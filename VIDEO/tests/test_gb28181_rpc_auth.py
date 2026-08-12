import os
import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.utils.gb28181_source import resolve_gb28181_source


class Gb28181RpcAuthTest(unittest.TestCase):
    SOURCE = "gb28181://44010200493432381460/34020000001320000001"
    RPC_TOKEN = "0123456789abcdef0123456789abcdef0123456789abc"

    @patch("app.utils.gb28181_source.requests.get")
    def test_internal_play_prefers_rpc_service_token(self, mock_get: Mock):
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {
            "code": 0,
            "data": {"fmp4": "http://127.0.0.1:6080/rtp/camera.live.mp4"},
        }
        mock_get.return_value = response

        with patch.dict(
            os.environ,
            {
                "IOT_RPC_INTERNAL_TOKEN": self.RPC_TOKEN,
                "GB28181_RPC_SERVICE_URL": "http://127.0.0.1:48088/rpc-api/gb28181",
            },
            clear=True,
        ):
            resolved = resolve_gb28181_source(self.SOURCE)

        self.assertEqual(resolved, "http://127.0.0.1:6080/rtp/camera.live.mp4")
        request_url = mock_get.call_args.args[0]
        request_headers = mock_get.call_args.kwargs["headers"]
        self.assertEqual(
            request_url,
            "http://127.0.0.1:48088/rpc-api/gb28181/play/start/"
            "44010200493432381460/34020000001320000001",
        )
        self.assertEqual(request_headers, {"X-Iot-Rpc-Token": self.RPC_TOKEN})

    @patch("app.utils.gb28181_source.requests.get")
    def test_legacy_play_keeps_user_jwt_header(self, mock_get: Mock):
        response = Mock()
        response.raise_for_status.return_value = None
        response.json.return_value = {
            "code": 0,
            "data": {"fmp4": "http://127.0.0.1:6080/rtp/camera.live.mp4"},
        }
        mock_get.return_value = response

        with patch.dict(
            os.environ,
            {
                "GB28181_SERVICE_URL": "http://127.0.0.1:48088/api",
                "JWT_TOKEN": "user-jwt",
            },
            clear=True,
        ):
            resolve_gb28181_source(self.SOURCE)

        self.assertEqual(
            mock_get.call_args.kwargs["headers"],
            {"X-Authorization": "Bearer user-jwt"},
        )


if __name__ == "__main__":
    unittest.main()
