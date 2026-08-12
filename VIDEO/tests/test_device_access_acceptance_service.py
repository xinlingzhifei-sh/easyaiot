import sys
import tempfile
import unittest
import json
import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.services.device_access_acceptance_service import run_device_access_acceptance


class DeviceAccessAcceptanceServiceTest(unittest.TestCase):

    def test_empty_environment_is_blocked_with_full_acceptance_matrix(self):
        result = run_device_access_acceptance({})

        self.assertEqual("blocked", result["status"])
        self.assertEqual(
            [
                "gb28181_public",
                "rtsp_same_lan",
                "rtsp_edge_outbound",
                "rtmp_public_push",
                "http_flv_public_playback",
                "webrtc_public_playback",
            ],
            [scenario["id"] for scenario in result["scenarios"]],
        )
        by_id = {scenario["id"]: scenario for scenario in result["scenarios"]}
        self.assertIn("ptz", by_id["gb28181_public"]["missing_probes"])
        self.assertIn("ice_connection_state", by_id["webrtc_public_playback"]["missing_evidence"])
        self.assertIn("turn_relay_verified", by_id["webrtc_public_playback"]["missing_evidence"])

    def test_webrtc_public_playback_requires_real_nat_network_evidence(self):
        class FakeResponse:
            status_code = 200

        class FakeHttpClient:
            def request(self, *_args, **_kwargs):
                return FakeResponse()

        result = run_device_access_acceptance(
            {
                "scenarios": {
                    "webrtc_public_playback": {
                        "probes": {
                            "https_page": {"method": "GET", "url": "https://eye.example.com/#/camera"},
                            "wss_signaling": {
                                "method": "GET",
                                "url": "https://eye.example.com/video/camera/webrtc/nat-config",
                            },
                            "access_state": {
                                "method": "GET",
                                "url": "https://eye.example.com/video/camera/access-state/health",
                            },
                        },
                        "evidence": {
                            "device_id": "cam-webrtc-001",
                            "network_name": "mobile-hotspot",
                            "ice_connection_state": "connected",
                            "selected_candidate_pair": "relay udp 203.0.113.10:3478 -> srflx 198.51.100.20:62000",
                            "public_candidate_observed": "true",
                            "turn_relay_verified": "true",
                        },
                    },
                },
            },
            http_client=FakeHttpClient(),
        )

        scenario = {
            item["id"]: item for item in result["scenarios"]
        }["webrtc_public_playback"]
        self.assertEqual("blocked", scenario["status"])
        for key in [
            "https_origin_verified",
            "wss_signaling_verified",
            "cross_carrier_verified",
            "mobile_hotspot_verified",
            "weak_network_profile",
            "weak_network_result",
        ]:
            self.assertIn(key, scenario["missing_evidence"])

    def test_configured_http_flv_scenario_runs_http_probes(self):
        calls = []

        class FakeResponse:
            status_code = 200

        class FakeHttpClient:
            def request(self, method, url, **kwargs):
                calls.append((method, url, kwargs))
                return FakeResponse()

        result = run_device_access_acceptance(
            {
                "scenarios": {
                    "http_flv_public_playback": {
                        "probes": {
                            "public_flv_playback": {"method": "GET", "url": "https://eye.example.com/live/cam-001.flv"},
                            "access_state": {"method": "GET", "url": "https://eye.example.com/video/camera/access-state/health"},
                        },
                        "evidence": {
                            "device_id": "cam-001",
                            "public_network": "cross-carrier",
                            "play_url": "https://eye.example.com/live/cam-001.flv",
                        },
                    },
                },
            },
            http_client=FakeHttpClient(),
        )

        scenario = {
            item["id"]: item for item in result["scenarios"]
        }["http_flv_public_playback"]
        self.assertEqual("passed", scenario["status"])
        self.assertEqual(["public_flv_playback", "access_state"], [probe["name"] for probe in scenario["probes"]])
        self.assertEqual(["passed", "passed"], [probe["status"] for probe in scenario["probes"]])
        self.assertEqual(
            [
                ("GET", "https://eye.example.com/live/cam-001.flv"),
                ("GET", "https://eye.example.com/video/camera/access-state/health"),
            ],
            [(method, url) for method, url, _ in calls],
        )

    def test_probe_without_url_is_blocked_before_http_execution(self):
        class FakeHttpClient:
            def request(self, *_args, **_kwargs):
                raise AssertionError("HTTP should not run for an incomplete probe")

        result = run_device_access_acceptance(
            {
                "scenarios": {
                    "http_flv_public_playback": {
                        "probes": {
                            "public_flv_playback": {"method": "GET"},
                            "access_state": {"method": "GET", "url": "https://eye.example.com/video/camera/access-state/health"},
                        },
                        "evidence": {
                            "device_id": "cam-001",
                            "public_network": "cross-carrier",
                            "play_url": "https://eye.example.com/live/cam-001.flv",
                        },
                    },
                },
            },
            http_client=FakeHttpClient(),
        )

        scenario = {
            item["id"]: item for item in result["scenarios"]
        }["http_flv_public_playback"]
        self.assertEqual("blocked", scenario["status"])
        self.assertIn("public_flv_playback.url", scenario["missing_probes"])

    def test_acceptance_cli_plan_only_writes_blocked_report(self):
        script_path = ROOT / "scripts" / "device_access_e2e_acceptance.py"
        spec = importlib.util.spec_from_file_location("device_access_e2e_acceptance", script_path)
        cli = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cli)

        with tempfile.TemporaryDirectory() as tmp:
            config_path = Path(tmp) / "acceptance.json"
            output_path = Path(tmp) / "report.json"
            config_path.write_text(json.dumps({"environment": "staging"}), encoding="utf-8")

            code = cli.main(["--config", str(config_path), "--output", str(output_path), "--plan-only"])

            self.assertEqual(2, code)
            report = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual("blocked", report["status"])
            self.assertEqual("staging", report["environment"])

    def test_example_config_covers_every_required_scenario(self):
        class FakeResponse:
            status_code = 200

        class FakeHttpClient:
            def request(self, *_args, **_kwargs):
                return FakeResponse()

        example = json.loads(
            (ROOT / "config" / "device_access_e2e_acceptance.example.json").read_text(encoding="utf-8")
        )

        result = run_device_access_acceptance(example, http_client=FakeHttpClient())

        self.assertEqual("passed", result["status"])
        self.assertEqual(
            [
                "gb28181_public",
                "rtsp_same_lan",
                "rtsp_edge_outbound",
                "rtmp_public_push",
                "http_flv_public_playback",
                "webrtc_public_playback",
            ],
            [scenario["id"] for scenario in result["scenarios"]],
        )
        self.assertTrue(all(scenario["status"] == "passed" for scenario in result["scenarios"]))

    def test_acceptance_runbook_documents_cli_and_matrix(self):
        runbook = (
            ROOT.parent
            / "docs"
            / "superpowers"
            / "specs"
            / "2026-06-13-device-access-e2e-acceptance-runbook.md"
        ).read_text(encoding="utf-8")

        self.assertIn("VIDEO/scripts/device_access_e2e_acceptance.py", runbook)
        for scenario_id in [
            "gb28181_public",
            "rtsp_same_lan",
            "rtsp_edge_outbound",
            "rtmp_public_push",
            "http_flv_public_playback",
            "webrtc_public_playback",
        ]:
            self.assertIn(scenario_id, runbook)
        self.assertIn("不能把 plan-only 或 blocked 报告当成验收通过", runbook)


if __name__ == "__main__":
    unittest.main()
