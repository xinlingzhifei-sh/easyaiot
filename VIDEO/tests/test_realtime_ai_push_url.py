import ast
import os
from pathlib import Path
from typing import Optional
import unittest
from unittest.mock import Mock, patch


RUN_DEPLOY_PATH = (
    Path(__file__).resolve().parents[1]
    / "services"
    / "realtime_algorithm_service"
    / "run_deploy.py"
)


def load_resolve_ai_rtmp_push_url():
    source = RUN_DEPLOY_PATH.read_text(encoding="utf-8")
    module = ast.parse(source)
    function = next(
        node
        for node in module.body
        if isinstance(node, ast.FunctionDef)
        and node.name == "_resolve_ai_rtmp_push_url"
    )
    namespace = {
        "os": os,
        "Optional": Optional,
        "_srs_api_port": Mock(return_value=1985),
        "_srs_rtmp_port": Mock(return_value=1935),
        "_check_srs_api_ready": Mock(),
    }
    exec(
        compile(
            ast.Module(body=[function], type_ignores=[]),
            filename=str(RUN_DEPLOY_PATH),
            mode="exec",
        ),
        namespace,
    )
    return namespace


class RealtimeAiPushUrlTest(unittest.TestCase):
    def test_local_srs_is_preferred_over_persisted_external_url(self):
        namespace = load_resolve_ai_rtmp_push_url()
        namespace["_check_srs_api_ready"].return_value = True

        with patch.dict(os.environ, {}, clear=True):
            result = namespace["_resolve_ai_rtmp_push_url"](
                "camera-01",
                "rtmp://192.168.0.88:1935/ai/camera-01",
            )

        self.assertEqual(result, "rtmp://127.0.0.1:1935/ai/camera-01")
        namespace["_check_srs_api_ready"].assert_called_once_with(
            "127.0.0.1", 1985, timeout=1.0
        )

    def test_persisted_url_is_used_when_local_srs_is_unavailable(self):
        namespace = load_resolve_ai_rtmp_push_url()
        namespace["_check_srs_api_ready"].return_value = False

        with patch.dict(os.environ, {}, clear=True):
            result = namespace["_resolve_ai_rtmp_push_url"](
                "camera-01",
                "rtmp://media.example.test:1935/ai/camera-01",
            )

        self.assertEqual(
            result,
            "rtmp://media.example.test:1935/ai/camera-01",
        )

    def test_remote_slice_uses_loopback_without_readiness_probe(self):
        namespace = load_resolve_ai_rtmp_push_url()

        with patch.dict(os.environ, {"POD_IP": "10.0.0.8"}, clear=True):
            result = namespace["_resolve_ai_rtmp_push_url"]("camera-01")

        self.assertEqual(result, "rtmp://127.0.0.1:1935/ai/camera-01")
        namespace["_check_srs_api_ready"].assert_not_called()


if __name__ == "__main__":
    unittest.main()
