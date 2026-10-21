import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
EXPORT_SCRIPT = ROOT / "export_pip_wheels.sh"
# 不存在的解释器版本：脚本找不到 python3.99，只能走按文件名校验离线包的分支
TARGET_PYTHON = "3.99"
FULL_BUNDLE = (
    "requests-2.32.3", "psutil-6.0.0", "Flask-3.0.3", "minio-7.2.7", "waitress-3.0.2",
    "pip-24.2", "setuptools-72.1.0", "wheel-0.44.0", "PyYAML-6.0.2", "httpx-0.27.0", "zeep-4.2.1",
)
FAKE_PYTHON = """#!/usr/bin/env bash
if [[ "$1" == "--version" ]]; then echo "Python 3.12.0"; exit 0; fi
if [[ "$1 $2 $3" == "-m pip --version" ]]; then echo "pip 24.2 (fake)"; exit 0; fi
if [[ "$1 $2 $3" == "-m pip download" ]]; then
  if [[ "${FAKE_PIP_MODE}" == "fail" ]]; then echo "network unreachable" >&2; exit 1; fi
  dest=""; prev=""
  for arg in "$@"; do
    if [[ "${prev}" == "-d" ]]; then dest="${arg}"; fi
    prev="${arg}"
  done
  for name in ${FAKE_PIP_WHEELS}; do : > "${dest}/${name}-py3-none-any.whl"; done
  exit 0
fi
exit 2
"""


def _find_bash():
    configured = os.environ.get("SENTINEL_TEST_BASH")
    if configured:
        return configured
    if os.name == "nt":
        # Windows 上 PATH 里的 bash 往往是 WSL 启动器，改用 Git 自带的 bash
        git = shutil.which("git")
        candidate = Path(git).resolve().parents[1] / "bin" / "bash.exe" if git else None
        return str(candidate) if candidate and candidate.is_file() else None
    return shutil.which("bash")


def _posix(path: Path) -> str:
    return str(path).replace("\\", "/")


@unittest.skipUnless(_find_bash(), "未找到 bash")
class ExportPipWheelsRefreshTest(unittest.TestCase):

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.wheels = self.tmp / "pip-wheels"
        self.wheels.mkdir()
        self.fake_python = self.tmp / "fake-python"
        self.fake_python.write_bytes(FAKE_PYTHON.encode("utf-8"))
        self.fake_python.chmod(0o755)

    def _stale_bundle(self, marker=TARGET_PYTHON, extra=()):
        names = [n for n in FULL_BUNDLE if not n.startswith("waitress")] + list(extra)
        for name in names:
            (self.wheels / f"{name}-py3-none-any.whl").write_bytes(b"")
        (self.wheels / ".target-python").write_text(marker + "\n", encoding="utf-8")
        return sorted(p.name for p in self.wheels.glob("*.whl"))

    def _run(self, mode):
        env = dict(os.environ)
        env.update({
            "PYTHON": _posix(self.fake_python),
            "AGENT_TARGET_PYTHON": TARGET_PYTHON,
            "AGENT_PIP_WHEELS_DIR": _posix(self.wheels),
            "FAKE_PIP_MODE": mode,
            "FAKE_PIP_WHEELS": " ".join(FULL_BUNDLE),
        })
        return subprocess.run(
            [_find_bash(), _posix(EXPORT_SCRIPT)],
            env=env, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=120,
        )

    def _leftover_staging(self):
        return [p.name for p in self.tmp.iterdir() if ".staging." in p.name]

    def test_failed_refresh_keeps_existing_wheels(self):
        before = self._stale_bundle()

        result = self._run("fail")

        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(before, sorted(p.name for p in self.wheels.glob("*.whl")))
        self.assertEqual(TARGET_PYTHON, (self.wheels / ".target-python").read_text(encoding="utf-8").strip())
        self.assertEqual([], self._leftover_staging())

    def test_failed_refresh_after_target_change_keeps_existing_wheels(self):
        before = self._stale_bundle(marker="3.98")

        result = self._run("fail")

        self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(before, sorted(p.name for p in self.wheels.glob("*.whl")))
        self.assertEqual([], self._leftover_staging())

    def test_successful_refresh_replaces_bundle(self):
        self._stale_bundle(extra=("obsolete-1.0",))

        result = self._run("ok")

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        names = sorted(p.name for p in self.wheels.glob("*.whl"))
        self.assertEqual(sorted(f"{n}-py3-none-any.whl" for n in FULL_BUNDLE), names)
        self.assertEqual(TARGET_PYTHON, (self.wheels / ".target-python").read_text(encoding="utf-8").strip())
        self.assertEqual([], self._leftover_staging())


if __name__ == "__main__":
    unittest.main()
