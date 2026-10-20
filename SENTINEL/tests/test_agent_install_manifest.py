import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = ROOT.parent
INSTALL_SCRIPT = ROOT / "install.sh"
ENV_EXAMPLE = ROOT / "agent.env.example"
PLATFORM_AGENT_SCRIPTS = (
    PROJECT_ROOT / ".scripts" / "node" / "ensure_platform_agent.sh",
    PROJECT_ROOT / ".scripts" / "node" / "ensure_platform_agent_centos7.sh",
)


class AgentInstallManifestTest(unittest.TestCase):

    def test_install_script_ships_command_polling_and_stream_forward_executor(self):
        source = INSTALL_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("agent_commands.py", source)
        self.assertIn("stream_forward_executor.py", source)
        self.assertIn("run_agent.py", source)
        self.assertIn("agent.env.example", source)

    def test_agent_env_template_exposes_webrtc_nat_deploy_knobs(self):
        source = ENV_EXAMPLE.read_text(encoding="utf-8")

        self.assertIn("WEBRTC_PUBLIC_IP", source)
        self.assertIn("WEBRTC_STUN_URLS", source)
        self.assertIn("WEBRTC_TURN_URLS", source)
        self.assertIn("ZLM_RTC_EXTERN_IP", source)

    def test_install_script_validates_agent_env_before_starting_service(self):
        source = INSTALL_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("validate_agent_env()", source)
        self.assertIn("your-agent-token-here", source)
        self.assertIn('validate_agent_env "$resolved_install_dir/agent.env"', source)
        self.assertLess(source.index('validate_agent_env "$resolved_install_dir/agent.env"'), source.index("cat <<UNIT"))

    def test_platform_agent_refresh_syncs_complete_runtime_manifest(self):
        required_runtime_files = (
            "run_agent.py",
            "agent_server.py",
            "env_security.py",
            "media_manager.py",
            "mqtt_manager.py",
            "workload_manager.py",
            "agent_commands.py",
            "stream_forward_executor.py",
        )

        for script in PLATFORM_AGENT_SCRIPTS:
            with self.subTest(script=script.name):
                source = script.read_text(encoding="utf-8")
                for runtime_file in required_runtime_files:
                    self.assertIn(runtime_file, source)
                self.assertIn('for f in "${AGENT_RUNTIME_FILES[@]}"', source)

    def test_waitress_is_pinned_and_checked_in_offline_bundle(self):
        requirements = (ROOT / "requirements.txt").read_text(encoding="utf-8").splitlines()
        export_script = (ROOT / "export_pip_wheels.sh").read_text(encoding="utf-8")

        self.assertIn("waitress==3.0.2", [line.strip() for line in requirements])
        self.assertIn("minio waitress pip setuptools wheel", export_script)

    def test_install_tolerates_stale_offline_bundle_without_waitress(self):
        source = INSTALL_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("! has_bootstrap_wheel waitress", source)
        self.assertEqual(source.count('-r "$OFFLINE_REQ"'), 2)
        self.assertLess(source.index('local OFFLINE_REQ='), source.index("setup_agent_site_packages; then"))


if __name__ == "__main__":
    unittest.main()
