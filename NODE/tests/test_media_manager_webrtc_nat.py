import types
import unittest
from unittest.mock import Mock, patch

from media_manager import MediaStackManager


class MediaManagerWebrtcNatTest(unittest.TestCase):

    @patch("media_manager.resolve_compose_cmd", return_value=["docker", "compose"])
    @patch("media_manager.os.path.isfile", return_value=True)
    @patch("media_manager.subprocess.run")
    def test_deploy_uses_webrtc_public_ip_for_media_candidate_rewrite(
        self,
        run,
        _isfile,
        _compose,
    ):
        calls = []

        def fake_run(cmd, cwd=None, env=None, capture_output=True, text=True):
            calls.append({"cmd": cmd, "cwd": cwd, "env": dict(env or {})})
            return types.SimpleNamespace(returncode=0, stdout="", stderr="")

        run.side_effect = fake_run

        with patch.dict("media_manager.os.environ", {
            "WEBRTC_PUBLIC_IP": "203.0.113.7",
            "POD_IP": "10.0.0.9",
        }, clear=False):
            result = MediaStackManager().deploy({"stackType": "srs_live", "nodeId": 7})

        self.assertEqual({"stackType": "srs_live", "nodeId": "7", "status": "running"}, result)
        self.assertGreaterEqual(len(calls), 2)
        render_env = calls[0]["env"]
        compose_env = calls[1]["env"]
        self.assertEqual("203.0.113.7", render_env["SRS_CANDIDATE_IP"])
        self.assertEqual("203.0.113.7", render_env["ZLM_RTC_EXTERN_IP"])
        self.assertEqual("203.0.113.7", compose_env["SRS_CANDIDATE_IP"])
        self.assertEqual("203.0.113.7", compose_env["ZLM_RTC_EXTERN_IP"])
        self.assertEqual("srs_live", compose_env["MEDIA_NODE_TYPE"])


if __name__ == "__main__":
    unittest.main()
