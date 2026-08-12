import importlib
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


class WebrtcNatConfigServiceTest(unittest.TestCase):

    def test_builds_ice_servers_and_candidate_rewrite_from_environment(self):
        with patch.dict(os.environ, {
            "WEBRTC_STUN_URLS": "stun:stun1.example.com:3478, stun:stun2.example.com:3478",
            "WEBRTC_TURN_URLS": "turn:turn.example.com:3478?transport=udp, turns:turn.example.com:5349",
            "WEBRTC_TURN_USERNAME": "turn-user",
            "WEBRTC_TURN_CREDENTIAL": "turn-pass",
            "WEBRTC_PUBLIC_IP": "203.0.113.10",
            "WEBRTC_PUBLIC_HOST": "stream.example.com",
            "WEBRTC_FORCE_SECURE": "1",
        }, clear=False):
            service = importlib.import_module("app.services.webrtc_nat_config_service")
            service = importlib.reload(service)

            config = service.build_webrtc_nat_config()

        self.assertEqual(
            {"urls": ["stun:stun1.example.com:3478", "stun:stun2.example.com:3478"]},
            config["iceServers"][0],
        )
        self.assertEqual(
            {
                "urls": ["turn:turn.example.com:3478?transport=udp", "turns:turn.example.com:5349"],
                "username": "turn-user",
                "credential": "turn-pass",
            },
            config["iceServers"][1],
        )
        self.assertEqual("203.0.113.10", config["candidate_ip"])
        self.assertEqual("stream.example.com", config["public_host"])
        self.assertTrue(config["require_secure_context"])
        self.assertTrue(config["prefer_wss"])
        self.assertTrue(config["turn_ready"])

    def test_omits_turn_server_when_credentials_are_incomplete(self):
        with patch.dict(os.environ, {
            "WEBRTC_STUN_URLS": "stun:stun.example.com:3478",
            "WEBRTC_TURN_URLS": "turn:turn.example.com:3478?transport=udp",
            "WEBRTC_TURN_USERNAME": "turn-user",
            "WEBRTC_TURN_CREDENTIAL": "",
        }, clear=False):
            service = importlib.import_module("app.services.webrtc_nat_config_service")
            service = importlib.reload(service)

            config = service.build_webrtc_nat_config()

        self.assertEqual([{"urls": ["stun:stun.example.com:3478"]}], config["iceServers"])
        self.assertFalse(config["turn_ready"])
        self.assertEqual("turn_incomplete", config["turn_status"])


if __name__ == "__main__":
    unittest.main()
