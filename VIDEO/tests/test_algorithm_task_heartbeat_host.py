import ast
import os
from pathlib import Path
import unittest
from unittest.mock import patch


DAEMON_PATH = (
    Path(__file__).resolve().parents[1]
    / "app"
    / "services"
    / "algorithm_task_daemon.py"
)


def load_resolve_video_heartbeat_host():
    module = ast.parse(DAEMON_PATH.read_text(encoding="utf-8"))
    function = next(
        node
        for node in module.body
        if isinstance(node, ast.FunctionDef)
        and node.name == "_resolve_video_heartbeat_host"
    )
    namespace = {"os": os}
    exec(
        compile(
            ast.Module(body=[function], type_ignores=[]),
            filename=str(DAEMON_PATH),
            mode="exec",
        ),
        namespace,
    )
    return namespace["_resolve_video_heartbeat_host"]


class AlgorithmTaskHeartbeatHostTest(unittest.TestCase):
    def test_flask_bind_host_is_used_for_local_heartbeat(self):
        resolve = load_resolve_video_heartbeat_host()

        with patch.dict(
            os.environ,
            {"FLASK_RUN_HOST": "172.17.0.1"},
            clear=True,
        ):
            self.assertEqual(resolve(), "172.17.0.1")

    def test_explicit_video_service_host_takes_precedence(self):
        resolve = load_resolve_video_heartbeat_host()

        with patch.dict(
            os.environ,
            {
                "VIDEO_SERVICE_HOST": "10.0.0.7",
                "FLASK_RUN_HOST": "172.17.0.1",
            },
            clear=True,
        ):
            self.assertEqual(resolve(), "10.0.0.7")

    def test_wildcard_bind_uses_pod_ip(self):
        resolve = load_resolve_video_heartbeat_host()

        with patch.dict(
            os.environ,
            {"FLASK_RUN_HOST": "0.0.0.0", "POD_IP": "10.0.0.8"},
            clear=True,
        ):
            self.assertEqual(resolve(), "10.0.0.8")

    def test_loopback_is_the_final_fallback(self):
        resolve = load_resolve_video_heartbeat_host()

        with patch.dict(os.environ, {}, clear=True):
            self.assertEqual(resolve(), "127.0.0.1")


if __name__ == "__main__":
    unittest.main()
