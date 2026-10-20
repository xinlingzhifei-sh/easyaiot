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
