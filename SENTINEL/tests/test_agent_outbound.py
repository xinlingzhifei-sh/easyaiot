import threading
import time
import unittest
from unittest.mock import Mock, patch

import agent_server
import run_sentinel
from agent_commands import AgentCommandRunner, TOKEN_ROTATE_COMMAND


class RouteExecutorTest(unittest.TestCase):

    def setUp(self):
        agent_server.auth_limiter._failures.clear()
        agent_server.auth_limiter._blocked_until.clear()

    def test_every_inbound_route_used_by_control_plane_has_a_command(self):
        # iot-node NodeAgentGateway 把 /a/b 映射为 a.b；这里锁住控制面会调用的全部入站接口
        used_by_control_plane = {
            '/workload/deploy', '/workload/stop', '/media/deploy', '/mqtt/deploy',
            '/sentinel/probe', '/sentinel/validate',
            '/camera/discover', '/camera/scan-segment', '/camera/probe-onvif',
            '/camera/probe-stream', '/camera/nvr-channels',
        }
        for path in used_by_control_plane:
            command_type = path.lstrip('/').replace('/', '.', 1)
            self.assertIn(command_type, agent_server.COMMAND_ROUTES, path)
            self.assertEqual(path, agent_server.COMMAND_ROUTES[command_type][1])

    def test_routes_exist_in_flask_app(self):
        rules = {(rule.rule, method) for rule in agent_server.app.url_map.iter_rules() for method in rule.methods}
        for method, path in agent_server.COMMAND_ROUTES.values():
            self.assertIn((path, method), rules, path)

    def test_invoke_goes_through_token_check(self):
        with patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'):
            result = agent_server.invoke_route('workload.list', {})
        self.assertIn('workloads', result['data'])

        # 配置令牌为空时 _check_token 拒绝一切请求：进程内调用同样被拒，不存在旁路
        with patch.object(agent_server, 'AGENT_TOKEN', ''):
            with self.assertRaises(RuntimeError) as ctx:
                agent_server.invoke_route('workload.list', {})
        self.assertIn('认证失败', str(ctx.exception))

    def test_business_errors_become_command_failures(self):
        with patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'):
            with self.assertRaises(RuntimeError) as ctx:
                agent_server.invoke_route('workload.deploy', {'workloadType': 'x'})
        self.assertIn('workloadType 和 workloadId 必填', str(ctx.exception))

    def test_route_executors_cover_the_whitelist_only(self):
        executors = agent_server.build_route_executors()
        self.assertEqual(set(agent_server.COMMAND_ROUTES), set(executors))


class ConcurrentRunnerTest(unittest.TestCase):

    def make_client(self, commands):
        client = Mock()
        client.max_commands = 5
        batches = [commands]
        client.poll.side_effect = lambda max_commands=None: batches.pop(0) if batches else []
        return client

    def test_polls_only_free_slots_and_runs_in_parallel(self):
        release = threading.Event()
        started = []

        def slow(payload):
            started.append(payload['n'])
            release.wait(5)
            return {'n': payload['n']}

        commands = [{'id': i, 'commandType': 'camera.scan-segment', 'payload': {'n': i}} for i in (1, 2)]
        client = self.make_client(commands)
        runner = AgentCommandRunner(client, {'camera.scan-segment': slow}, max_workers=2)

        self.assertEqual(2, runner.run_once())
        client.poll.assert_called_with(max_commands=2)
        deadline = time.time() + 5
        while len(started) < 2 and time.time() < deadline:
            time.sleep(0.01)
        self.assertEqual({1, 2}, set(started), "两条指令同时在执行")
        self.assertEqual(0, runner.run_once(), "槽位占满时不再拉取")
        self.assertEqual(1, client.poll.call_count)

        release.set()
        self.assertTrue(runner.wait_idle(5))
        statuses = sorted(call.args[1] for call in client.report_result.call_args_list)
        self.assertEqual(['succeeded', 'succeeded'], statuses)

    def test_long_running_command_renews_its_lease(self):
        client = self.make_client([{'id': 7, 'commandType': 'workload.deploy', 'payload': {}}])
        runner = AgentCommandRunner(client, {'workload.deploy': lambda payload: time.sleep(0.35) or {}},
                                    lease_renew_seconds=0.1)

        runner.run_once()

        self.assertGreaterEqual(client.heartbeat.call_count, 2)
        client.heartbeat.assert_called_with(7)
        client.report_result.assert_called_once_with(7, 'succeeded', {}, None)

    def test_report_failure_does_not_kill_the_loop(self):
        client = self.make_client([{'id': 8, 'commandType': 'workload.stop', 'payload': {}}])
        client.report_result.side_effect = ConnectionError('reset')
        runner = AgentCommandRunner(client, {'workload.stop': lambda payload: {}})

        self.assertEqual(1, runner.run_once())


class InboundSwitchTest(unittest.TestCase):

    def test_capabilities_declare_inbound_flag_rotation_and_routes(self):
        with patch.object(run_sentinel, 'AGENT_INBOUND_ENABLED', False):
            caps = run_sentinel.command_capabilities(run_sentinel.build_command_executors())

        self.assertIs(False, caps['inbound'])
        self.assertTrue(caps[TOKEN_ROTATE_COMMAND])
        for command_type in agent_server.COMMAND_ROUTES:
            self.assertTrue(caps[command_type], command_type)
        self.assertTrue(caps['stream_forward.deploy'])

    def run_main(self, inbound, poll):
        command_thread = Mock()
        with patch.object(run_sentinel, 'AGENT_INBOUND_ENABLED', inbound), \
                patch.object(run_sentinel, 'COMMAND_POLL_ENABLED', poll), \
                patch.object(run_sentinel, 'CONTROL_PLANE_URL', 'https://cp.example/admin-api/node/agent'), \
                patch.object(run_sentinel, 'NODE_ID', 3), \
                patch.object(run_sentinel, 'AGENT_TOKEN', 'node-secret'), \
                patch.object(run_sentinel, 'PLATFORM_AGENT', False), \
                patch.object(run_sentinel, 'heartbeat_loop', lambda: None), \
                patch.object(run_sentinel, 'start_command_loop', return_value=command_thread) as start, \
                patch.object(agent_server, 'run_server') as run_server:
            run_sentinel.main()
        return start, run_server, command_thread

    def test_outbound_only_mode_does_not_listen(self):
        start, run_server, command_thread = self.run_main(inbound=False, poll=True)

        start.assert_called_once()
        run_server.assert_not_called()
        command_thread.join.assert_called_once()

    def test_default_mode_still_listens(self):
        start, run_server, _ = self.run_main(inbound=True, poll=True)

        start.assert_called_once()
        run_server.assert_called_once()

    def test_outbound_only_requires_command_polling(self):
        with self.assertRaises(SystemExit) as ctx:
            self.run_main(inbound=False, poll=False)
        self.assertEqual(1, ctx.exception.code)


if __name__ == '__main__':
    unittest.main()
