import sys
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


class CoturnConfigServiceTest(unittest.TestCase):

    def test_builds_turn_urls_for_udp_and_tcp(self):
        from app.services.coturn_config_service import build_turn_urls

        self.assertEqual(
            [
                "turn:eye.yfeiai.com:3478?transport=udp",
                "turn:eye.yfeiai.com:3478?transport=tcp",
            ],
            build_turn_urls("eye.yfeiai.com", listen_port=3478),
        )

    def test_builds_coturn_config_with_public_ip_credentials_and_relay_ports(self):
        from app.services.coturn_config_service import build_coturn_config

        config = build_coturn_config(
            public_ip="1.95.118.210",
            realm="eye.yfeiai.com",
            username="yfeieye",
            credential="turn-secret",
            listen_port=3478,
            relay_min_port=49160,
            relay_max_port=49200,
        )

        self.assertIn("listening-port=3478", config)
        self.assertIn("external-ip=1.95.118.210", config)
        self.assertIn("realm=eye.yfeiai.com", config)
        self.assertIn("server-name=eye.yfeiai.com", config)
        self.assertIn("user=yfeieye:turn-secret", config)
        self.assertIn("min-port=49160", config)
        self.assertIn("max-port=49200", config)
        self.assertIn("lt-cred-mech", config)
        self.assertIn("fingerprint", config)
        self.assertIn("no-cli", config)
        self.assertTrue(config.endswith("\n"))

    def test_render_script_writes_turnserver_config_and_webrtc_env(self):
        script = ROOT / "scripts" / "render_coturn_config.py"
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            config_out = temp / "turnserver.conf"
            env_out = temp / "turn.env"

            result = subprocess.run(
                [
                    sys.executable,
                    str(script),
                    "--public-ip",
                    "1.95.118.210",
                    "--public-host",
                    "eye.yfeiai.com",
                    "--username",
                    "yfeieye",
                    "--credential",
                    "turn-secret",
                    "--config-out",
                    str(config_out),
                    "--env-out",
                    str(env_out),
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            config = config_out.read_text(encoding="utf-8")
            self.assertIn("listening-port=3478", config)
            self.assertIn("user=yfeieye:turn-secret", config)
            env = env_out.read_text(encoding="utf-8")
            self.assertIn(
                "WEBRTC_TURN_URLS=turn:eye.yfeiai.com:3478?transport=udp,turn:eye.yfeiai.com:3478?transport=tcp",
                env,
            )
            self.assertIn("WEBRTC_TURN_USERNAME=yfeieye", env)
            self.assertIn("WEBRTC_TURN_CREDENTIAL=turn-secret", env)

    def test_install_script_dry_run_writes_config_without_running_docker(self):
        script = ROOT / "scripts" / "install_coturn.py"
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)

            result = subprocess.run(
                [
                    sys.executable,
                    str(script),
                    "--dry-run",
                    "--work-dir",
                    str(temp),
                    "--public-ip",
                    "1.95.118.210",
                    "--public-host",
                    "eye.yfeiai.com",
                    "--username",
                    "yfeieye",
                    "--credential",
                    "turn-secret",
                ],
                check=False,
                capture_output=True,
                text=True,
            )

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("DRY_RUN: docker run", result.stdout)
            self.assertIn("docker.m.daocloud.io/instrumentisto/coturn:latest", result.stdout)
            self.assertTrue((temp / "turnserver.conf").exists())
            self.assertTrue((temp / "turn.env").exists())
            self.assertIn("external-ip=1.95.118.210", (temp / "turnserver.conf").read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
