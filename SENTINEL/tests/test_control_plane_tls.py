import os
import tempfile
import unittest
from unittest.mock import Mock, patch

import run_sentinel
from agent_commands import ControlPlaneTlsError, check_control_plane_transport, control_plane_tls_verify
from sentinel import remediator


class ControlPlaneTlsVerifyTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.ca_file = os.path.join(self.tmp.name, "control-plane-ca.pem")
        with open(self.ca_file, "w", encoding="utf-8") as f:
            f.write("-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n")

    def tearDown(self):
        self.tmp.cleanup()

    def test_defaults_to_system_ca_verification(self):
        self.assertIs(True, control_plane_tls_verify({}))
        self.assertIs(True, control_plane_tls_verify({"CONTROL_PLANE_CA_FILE": "  "}))

    def test_uses_configured_ca_file(self):
        self.assertEqual(self.ca_file, control_plane_tls_verify({"CONTROL_PLANE_CA_FILE": self.ca_file}))

    def test_missing_ca_file_fails_instead_of_falling_back(self):
        with self.assertRaises(ControlPlaneTlsError):
            control_plane_tls_verify({"CONTROL_PLANE_CA_FILE": os.path.join(self.tmp.name, "missing.pem")})
        with self.assertRaises(ControlPlaneTlsError):
            control_plane_tls_verify({"CONTROL_PLANE_CA_FILE": self.tmp.name})

    def test_there_is_no_switch_that_disables_verification(self):
        for name in ("CONTROL_PLANE_TLS_VERIFY", "CONTROL_PLANE_INSECURE", "CONTROL_PLANE_VERIFY", "PYTHONHTTPSVERIFY"):
            self.assertIs(True, control_plane_tls_verify({name: "0"}), name)
            self.assertIs(True, control_plane_tls_verify({name: "false"}), name)

    def test_transport_check(self):
        url = "https://cp.example/admin-api/node/agent"
        self.assertEqual(self.ca_file, check_control_plane_transport(url, {"CONTROL_PLANE_CA_FILE": self.ca_file}))
        with self.assertLogs("easyaiot-node-agent.commands", level="WARNING"):
            self.assertIs(True, check_control_plane_transport("http://10.0.0.1:48080/admin-api/node/agent", {}))
        with self.assertRaises(ControlPlaneTlsError):
            check_control_plane_transport("http://10.0.0.1:48080/admin-api/node/agent",
                                          {"CONTROL_PLANE_REQUIRE_HTTPS": "true"})
        with self.assertRaises(ControlPlaneTlsError):
            check_control_plane_transport("ftp://cp.example/agent", {})

    def test_heartbeat_requests_carry_verify(self):
        response = Mock(status_code=200)
        response.json.return_value = {"code": 0}
        with patch.dict(os.environ, {"CONTROL_PLANE_CA_FILE": self.ca_file}), \
                patch.object(run_sentinel.requests, "post", return_value=response) as post:
            ok, _ = run_sentinel.post_json("/heartbeat", {"nodeId": 1})

        self.assertTrue(ok)
        self.assertEqual(self.ca_file, post.call_args.kwargs["verify"])

    def test_heartbeat_fails_closed_when_ca_disappears(self):
        with patch.dict(os.environ, {"CONTROL_PLANE_CA_FILE": os.path.join(self.tmp.name, "gone.pem")}), \
                patch.object(run_sentinel.requests, "post") as post:
            ok, msg = run_sentinel.post_json("/heartbeat", {"nodeId": 1})

        self.assertFalse(ok)
        post.assert_not_called()
        self.assertIn("CONTROL_PLANE_CA_FILE", msg)

    def test_remediation_requests_use_the_same_ca(self):
        with patch.dict(os.environ, {"CONTROL_PLANE_CA_FILE": self.ca_file}):
            self.assertEqual(self.ca_file, remediator._tls_verify())

    def test_agent_refuses_to_start_with_unusable_tls_config(self):
        with patch.dict(os.environ, {"CONTROL_PLANE_REQUIRE_HTTPS": "true"}), \
                patch.object(run_sentinel, "CONTROL_PLANE_URL", "http://10.0.0.1:48080/admin-api/node/agent"), \
                patch.object(run_sentinel, "wait_for_platform_credentials") as wait:
            with self.assertRaises(SystemExit) as ctx:
                run_sentinel.main()

        self.assertEqual(1, ctx.exception.code)
        wait.assert_not_called()


if __name__ == "__main__":
    unittest.main()
