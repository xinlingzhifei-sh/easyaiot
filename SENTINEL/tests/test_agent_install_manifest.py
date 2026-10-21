import ast
import re
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
CONTROL_PLANE_DEPLOY_UTIL = (
    PROJECT_ROOT / "DEVICE" / "iot-node" / "iot-node-biz" / "src" / "main" / "java"
    / "com" / "basiclab" / "iot" / "node" / "util" / "AgentDeployUtil.java"
)
# 节点 launcher 优先执行 run_sentinel.py，缺失时回退 run_agent.py
ENTRY_POINTS = ("run_sentinel.py", "run_agent.py")
# iot-node 按目录整体上传的包
SYNCED_DIRECTORIES = ("sentinel", "registry")


def _imported_top_level_modules(path: Path):
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                yield alias.name.split(".")[0]
        elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            yield node.module.split(".")[0]


def _local_import_closure():
    """从入口脚本出发，收集 SENTINEL 根目录下被（直接或间接）导入的本地模块文件，含函数内的延迟导入。"""
    required = set(ENTRY_POINTS)
    scanned_packages = set()
    pending = [ROOT / name for name in ENTRY_POINTS]
    while pending:
        for module in _imported_top_level_modules(pending.pop()):
            module_file = ROOT / f"{module}.py"
            if module_file.is_file() and module_file.name not in required:
                required.add(module_file.name)
                pending.append(module_file)
            elif module in SYNCED_DIRECTORIES and (ROOT / module).is_dir() and module not in scanned_packages:
                scanned_packages.add(module)
                pending.extend((ROOT / module).rglob("*.py"))
    return required


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

    def test_control_plane_ssh_deploy_uploads_every_imported_module(self):
        # iot-node 经 SSH 只上传 SYNC_RELATIVE_FILES 与 sentinel/、registry/，并在安装目录原地执行 install.sh，
        # install.sh 自身的源码同步会被跳过，所以漏传的模块会让 import 校验或运行时失败。
        if not CONTROL_PLANE_DEPLOY_UTIL.is_file():
            self.skipTest("未找到 DEVICE 源码（SENTINEL 单独抽取）")
        java_source = CONTROL_PLANE_DEPLOY_UTIL.read_text(encoding="utf-8")
        block = re.search(r"SYNC_RELATIVE_FILES\s*=\s*\{(.*?)\};", java_source, re.S)
        self.assertIsNotNone(block, "AgentDeployUtil.java 中未找到 SYNC_RELATIVE_FILES")
        synced = set(re.findall(r'"([^"]+)"', block.group(1)))

        required = _local_import_closure()

        self.assertIn("agent_server.py", required)
        self.assertEqual(set(), required - synced)

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
