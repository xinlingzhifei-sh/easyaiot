import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.utils import media_client


def _response(body):
    resp = Mock()
    resp.json.return_value = body
    resp.raise_for_status.return_value = None
    return resp


class GetAgentCommandByKeyTest(unittest.TestCase):

    @patch.object(media_client.requests, "get")
    def test_queries_iot_node_by_key_endpoint_with_node_id(self, http_get):
        http_get.return_value = _response({"code": 0, "data": {"id": 101, "status": "running"}})

        command = media_client.get_agent_command_by_key("stream_forward:cam-001", node_id=7)

        self.assertEqual({"id": 101, "status": "running"}, command)
        args, kwargs = http_get.call_args
        self.assertEqual(f"{media_client.AGENT_COMMAND_API_BASE}/by-key", args[0])
        self.assertTrue(args[0].endswith("/admin-api/node/agent/commands/by-key"))
        self.assertEqual({"commandKey": "stream_forward:cam-001", "nodeId": 7}, kwargs["params"])

    @patch.object(media_client.requests, "get")
    def test_omits_node_id_when_not_given(self, http_get):
        http_get.return_value = _response({"code": 0, "data": {"id": 101}})

        media_client.get_agent_command_by_key("stream_forward:cam-001")

        self.assertEqual({"commandKey": "stream_forward:cam-001"}, http_get.call_args.kwargs["params"])

    @patch.object(media_client.requests, "get")
    def test_returns_empty_dict_when_command_key_is_unknown(self, http_get):
        http_get.return_value = _response({"code": 0, "data": None})

        self.assertEqual({}, media_client.get_agent_command_by_key("stream_forward:missing", node_id=7))

    @patch.object(media_client.requests, "get")
    def test_raises_on_business_error(self, http_get):
        http_get.return_value = _response({"code": 401, "msg": "账号未登录"})

        with self.assertRaises(RuntimeError):
            media_client.get_agent_command_by_key("stream_forward:cam-001", node_id=7)


if __name__ == "__main__":
    unittest.main()
