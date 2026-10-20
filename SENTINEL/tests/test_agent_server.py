import importlib.util
import json
import sys
import threading
import unittest
import urllib.error
import urllib.request
from unittest.mock import Mock, patch

import agent_server


class ListenHostParsingTest(unittest.TestCase):

    def test_default_and_empty_fall_back_to_all_interfaces(self):
        self.assertEqual(agent_server.parse_listen_hosts('0.0.0.0'), ['0.0.0.0'])
        self.assertEqual(agent_server.parse_listen_hosts(''), ['0.0.0.0'])
        self.assertEqual(agent_server.parse_listen_hosts(' , '), ['0.0.0.0'])

    def test_multiple_hosts_separated_by_comma_or_space(self):
        self.assertEqual(
            agent_server.parse_listen_hosts('10.0.0.5, 127.0.0.1'),
            ['10.0.0.5', '127.0.0.1'],
        )
        self.assertEqual(
            agent_server.parse_listen_hosts('10.0.0.5 127.0.0.1'),
            ['10.0.0.5', '127.0.0.1'],
        )

    def test_wildcard_absorbs_same_family_addresses_to_avoid_bind_conflict(self):
        self.assertEqual(agent_server.parse_listen_hosts('0.0.0.0,127.0.0.1'), ['0.0.0.0'])
        self.assertEqual(agent_server.parse_listen_hosts('0.0.0.0,::1'), ['0.0.0.0', '::1'])
        self.assertEqual(agent_server.parse_listen_hosts(':: ::1 127.0.0.1'), ['::', '127.0.0.1'])
        self.assertEqual(agent_server.parse_listen_hosts('127.0.0.1,127.0.0.1'), ['127.0.0.1'])

    def test_ipv6_hosts_are_bracketed_in_listen_endpoint(self):
        hosts = agent_server.parse_listen_hosts('[::1],127.0.0.1')
        self.assertEqual(hosts, ['::1', '127.0.0.1'])
        self.assertEqual(agent_server._listen_endpoint('::1', 9100), '[::1]:9100')
        self.assertEqual(agent_server._listen_endpoint('127.0.0.1', 9100), '127.0.0.1:9100')


class RunServerTest(unittest.TestCase):

    def test_uses_waitress_with_all_listen_hosts_and_thread_count(self):
        fake_waitress = Mock()
        with (
            patch.dict(sys.modules, {'waitress': fake_waitress}),
            patch.object(agent_server, 'AGENT_LISTEN_HOST', '10.0.0.5,127.0.0.1'),
            patch.object(agent_server, 'AGENT_LISTEN_PORT', 9100),
            patch.object(agent_server, 'AGENT_HTTP_THREADS', 16),
            patch.object(agent_server.app, 'run') as flask_run,
        ):
            agent_server.run_server()

        fake_waitress.serve.assert_called_once_with(
            agent_server.app,
            listen='10.0.0.5:9100 127.0.0.1:9100',
            threads=16,
        )
        flask_run.assert_not_called()

    def test_falls_back_to_flask_server_when_waitress_missing(self):
        # sys.modules 中置 None 会让 import 抛 ImportError，模拟旧离线包未带 waitress
        with (
            patch.dict(sys.modules, {'waitress': None}),
            patch.object(agent_server, 'AGENT_LISTEN_HOST', '0.0.0.0'),
            patch.object(agent_server, 'AGENT_LISTEN_PORT', 9100),
            patch.object(agent_server.app, 'run') as flask_run,
            self.assertLogs('easyaiot-sentinel.server', level='WARNING') as logs,
        ):
            agent_server.run_server()

        flask_run.assert_called_once_with(host='0.0.0.0', port=9100, threaded=True, use_reloader=False)
        self.assertTrue(any('waitress' in line for line in logs.output))


class AgentTokenCheckTest(unittest.TestCase):

    def setUp(self):
        self.client = agent_server.app.test_client()

    def test_health_does_not_require_token(self):
        with patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'):
            response = self.client.get('/health')
        self.assertEqual(response.status_code, 200)

    # 现有契约：鉴权失败回 HTTP 400，body.code=401（_err 固定用 400），控制面按非 2xx 处理
    def assert_auth_rejected(self, response):
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.get_json()['code'], 401)
        self.assertEqual(response.get_json()['msg'], 'Agent 认证失败')

    def test_missing_or_wrong_token_is_rejected(self):
        with patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'):
            missing = self.client.get('/workload/list')
            wrong = self.client.get('/workload/list', headers={'X-Agent-Token': 'other'})
        self.assert_auth_rejected(missing)
        self.assert_auth_rejected(wrong)

    def test_empty_configured_token_rejects_empty_header(self):
        with patch.object(agent_server, 'AGENT_TOKEN', ''):
            response = self.client.get('/workload/list', headers={'X-Agent-Token': ''})
        self.assert_auth_rejected(response)

    def test_matching_token_is_accepted(self):
        with patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'):
            response = self.client.get('/workload/list', headers={'X-Agent-Token': 'node-secret'})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()['code'], 0)


class FakeClock:

    def __init__(self, now=1000.0):
        self.now = now

    def __call__(self):
        return self.now


class ClientAddressTest(unittest.TestCase):

    def test_parses_ipv4_ipv6_and_unwraps_ipv4_mapped(self):
        self.assertEqual(str(agent_server.client_address('10.0.0.5')), '10.0.0.5')
        self.assertEqual(str(agent_server.client_address('::ffff:10.0.0.5')), '10.0.0.5')
        self.assertEqual(str(agent_server.client_address('fe80::1%eth0')), 'fe80::1')

    def test_missing_or_garbage_address_is_none(self):
        self.assertIsNone(agent_server.client_address(None))
        self.assertIsNone(agent_server.client_address(''))
        self.assertIsNone(agent_server.client_address('not-an-ip'))


class SourcePolicyTest(unittest.TestCase):

    def addr(self, raw):
        return agent_server.client_address(raw)

    def test_unconfigured_allows_everyone(self):
        policy = agent_server.SourcePolicy('')
        self.assertTrue(policy.allows(self.addr('203.0.113.7')))
        self.assertTrue(policy.allows(None))

    def test_configured_allows_listed_networks_and_loopback_only(self):
        policy = agent_server.SourcePolicy('10.0.0.0/24, 192.168.1.20 2001:db8::/32')
        self.assertTrue(policy.allows(self.addr('10.0.0.9')))
        self.assertTrue(policy.allows(self.addr('::ffff:10.0.0.9')))
        self.assertTrue(policy.allows(self.addr('192.168.1.20')))
        self.assertTrue(policy.allows(self.addr('2001:db8::5')))
        self.assertTrue(policy.allows(self.addr('127.0.0.1')))
        self.assertTrue(policy.allows(self.addr('::1')))
        self.assertFalse(policy.allows(self.addr('10.0.1.9')))
        self.assertFalse(policy.allows(self.addr('192.168.1.21')))
        self.assertFalse(policy.allows(None))

    def test_invalid_entries_are_reported_and_fail_closed(self):
        policy = agent_server.SourcePolicy('10.0.0.0/33, bogus')
        self.assertEqual(policy.invalid, ['10.0.0.0/33', 'bogus'])
        self.assertEqual(policy.networks, [])
        self.assertFalse(policy.allows(self.addr('10.0.0.9')))
        self.assertTrue(policy.allows(self.addr('127.0.0.1')))


class AuthFailureLimiterTest(unittest.TestCase):

    def setUp(self):
        self.clock = FakeClock()
        self.limiter = agent_server.AuthFailureLimiter(3, 60, 300, clock=self.clock)

    def test_blocks_after_limit_within_window_and_reports_retry_after(self):
        self.assertFalse(self.limiter.record_failure('10.0.0.9'))
        self.assertFalse(self.limiter.record_failure('10.0.0.9'))
        self.assertEqual(self.limiter.retry_after('10.0.0.9'), 0)
        self.assertTrue(self.limiter.record_failure('10.0.0.9'))
        self.assertEqual(self.limiter.retry_after('10.0.0.9'), 300)
        self.clock.now += 299.5
        self.assertEqual(self.limiter.retry_after('10.0.0.9'), 1)
        self.clock.now += 0.5
        self.assertEqual(self.limiter.retry_after('10.0.0.9'), 0)
        # 封禁结束后重新计数
        self.assertFalse(self.limiter.record_failure('10.0.0.9'))

    def test_failures_outside_window_do_not_accumulate(self):
        self.limiter.record_failure('10.0.0.9')
        self.limiter.record_failure('10.0.0.9')
        self.clock.now += 61
        self.assertFalse(self.limiter.record_failure('10.0.0.9'))
        self.assertFalse(self.limiter.record_failure('10.0.0.9'))
        self.assertEqual(self.limiter.retry_after('10.0.0.9'), 0)

    def test_sources_are_counted_separately(self):
        for _ in range(3):
            self.limiter.record_failure('10.0.0.9')
        self.assertGreater(self.limiter.retry_after('10.0.0.9'), 0)
        self.assertEqual(self.limiter.retry_after('10.0.0.10'), 0)

    def test_zero_limit_disables_limiter(self):
        limiter = agent_server.AuthFailureLimiter(0, 60, 300, clock=self.clock)
        self.assertFalse(limiter.enabled)
        for _ in range(100):
            self.assertFalse(limiter.record_failure('10.0.0.9'))
        self.assertEqual(limiter.retry_after('10.0.0.9'), 0)

    def test_tracked_sources_are_bounded(self):
        with patch.object(agent_server.AuthFailureLimiter, 'MAX_TRACKED_SOURCES', 5):
            for i in range(50):
                self.limiter.record_failure(f'10.0.1.{i}')
            self.assertLessEqual(len(self.limiter._failures), 5)


class GuardMiddlewareTest(unittest.TestCase):

    def setUp(self):
        self.client = agent_server.app.test_client()
        self.clock = FakeClock()
        patches = [
            patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'),
            patch.object(agent_server, 'source_policy', agent_server.SourcePolicy('')),
            patch.object(agent_server, 'auth_limiter', agent_server.AuthFailureLimiter(3, 60, 300, clock=self.clock)),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def get(self, path, ip, token=None):
        headers = {'X-Agent-Token': token} if token is not None else {}
        return self.client.get(path, headers=headers, environ_base={'REMOTE_ADDR': ip})

    def test_allowlist_rejects_unlisted_sources_including_health(self):
        with patch.object(agent_server, 'source_policy', agent_server.SourcePolicy('10.0.0.0/24')):
            outsider = self.get('/health', '198.51.100.4')
            listed = self.get('/workload/list', '10.0.0.9', token='node-secret')
            local_health = self.get('/health', '127.0.0.1')
        self.assertEqual(outsider.status_code, 403)
        self.assertEqual(outsider.get_json()['code'], 403)
        self.assertEqual(listed.status_code, 200)
        self.assertEqual(local_health.status_code, 200)

    def test_repeated_failures_block_source_even_with_correct_token(self):
        for _ in range(3):
            response = self.get('/workload/list', '10.0.0.9', token='wrong')
            self.assertEqual(response.status_code, 400)
            self.assertEqual(response.get_json()['code'], 401)

        blocked = self.get('/workload/list', '10.0.0.9', token='node-secret')
        self.assertEqual(blocked.status_code, 429)
        self.assertEqual(blocked.get_json()['code'], 429)
        self.assertEqual(blocked.headers['Retry-After'], '300')

        other_source = self.get('/workload/list', '10.0.0.10', token='node-secret')
        self.assertEqual(other_source.status_code, 200)

        self.clock.now += 300
        recovered = self.get('/workload/list', '10.0.0.9', token='node-secret')
        self.assertEqual(recovered.status_code, 200)

    def test_successful_requests_do_not_count_as_failures(self):
        for _ in range(10):
            self.assertEqual(self.get('/workload/list', '10.0.0.9', token='node-secret').status_code, 200)
        self.get('/workload/list', '10.0.0.9', token='wrong')
        self.get('/workload/list', '10.0.0.9', token='wrong')
        self.assertEqual(self.get('/workload/list', '10.0.0.9', token='node-secret').status_code, 200)

    def test_loopback_is_never_rate_limited(self):
        for _ in range(10):
            self.get('/workload/list', '127.0.0.1', token='wrong')
        self.assertEqual(self.get('/workload/list', '127.0.0.1', token='node-secret').status_code, 200)

    def test_health_failures_are_not_counted(self):
        for _ in range(10):
            self.assertEqual(self.get('/health', '10.0.0.9').status_code, 200)
        self.assertEqual(self.get('/workload/list', '10.0.0.9', token='node-secret').status_code, 200)


class CameraScanConcurrencyTest(unittest.TestCase):

    def setUp(self):
        self.client = agent_server.app.test_client()
        patches = [
            patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'),
            patch.object(agent_server, 'camera_scan_segment', lambda payload: {'devices': []}),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def scan(self):
        return self.client.post('/camera/scan-segment', json={}, headers={'X-Agent-Token': 'node-secret'})

    def test_rejects_when_all_slots_are_busy(self):
        slots = threading.BoundedSemaphore(1)
        slots.acquire()
        with patch.object(agent_server, 'camera_scan_slots', slots):
            response = self.scan()
        self.assertEqual(response.status_code, 429)
        self.assertEqual(response.get_json()['code'], 429)

    def test_releases_slot_after_scan(self):
        slots = threading.BoundedSemaphore(1)
        with patch.object(agent_server, 'camera_scan_slots', slots):
            first = self.scan()
            second = self.scan()
        self.assertEqual(first.status_code, 200)
        self.assertEqual(second.status_code, 200)
        self.assertTrue(slots.acquire(blocking=False))

    def test_unlimited_when_slots_disabled(self):
        with patch.object(agent_server, 'camera_scan_slots', None):
            self.assertEqual(self.scan().status_code, 200)


@unittest.skipUnless(importlib.util.find_spec('waitress'), 'waitress 未安装')
class WaitressServingTest(unittest.TestCase):
    """真实 socket 上验证 waitress 承载 Flask 应用时鉴权与响应格式不变。"""

    @classmethod
    def setUpClass(cls):
        from waitress import create_server

        cls.server = create_server(agent_server.app, host='127.0.0.1', port=0, threads=2)
        cls.base_url = f'http://127.0.0.1:{cls.server.effective_port}'
        threading.Thread(target=cls.server.run, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.server.close()

    def _get(self, path, token=None):
        request = urllib.request.Request(self.base_url + path)
        if token is not None:
            request.add_header('X-Agent-Token', token)
        try:
            with urllib.request.urlopen(request, timeout=5) as response:
                return response.status, json.loads(response.read())
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read())

    def test_health_and_token_check_over_http(self):
        with patch.object(agent_server, 'AGENT_TOKEN', 'node-secret'):
            health = self._get('/health')
            rejected = self._get('/workload/list', token='other')
            accepted = self._get('/workload/list', token='node-secret')

        self.assertEqual(health[0], 200)
        self.assertEqual(health[1]['data']['status'], 'ok')
        self.assertEqual(rejected[0], 400)
        self.assertEqual(rejected[1]['code'], 401)
        self.assertEqual(rejected[1]['msg'], 'Agent 认证失败')
        self.assertEqual(accepted[0], 200)
        self.assertEqual(accepted[1]['code'], 0)


if __name__ == '__main__':
    unittest.main()
