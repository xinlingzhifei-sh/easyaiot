import os
import stat
import tempfile
import unittest
from unittest.mock import patch

import agent_commands
import agent_server
from agent_commands import (
    AGENT_TOKEN_INVALID_CODE,
    AgentCommandClient,
    AgentCommandRunner,
    AgentCredentials,
    TOKEN_ROTATE_COMMAND,
    TokenRotator,
    update_env_file,
)

OLD_TOKEN = "a" * 32
NEW_TOKEN = "b" * 32

ENV_TEXT = (
    "# yFeiEye Node Agent 配置\n"
    "PLATFORM_AGENT=0\n"
    "NODE_ID=7\n"
    f"AGENT_TOKEN={OLD_TOKEN}\n"
    "# 注释保持原样\n"
    "CONTROL_PLANE_URL=https://cp.example/admin-api/node/agent\n"
)


class EnvFileTestCase(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.env_path = os.path.join(self.tmp.name, "agent.env")
        with open(self.env_path, "w", encoding="utf-8", newline="\n") as f:
            f.write(ENV_TEXT)
        if os.name == "posix":
            os.chmod(self.env_path, 0o600)

    def tearDown(self):
        self.tmp.cleanup()

    def read_env(self):
        with open(self.env_path, encoding="utf-8") as f:
            return f.read()

    def leftover_temp_files(self):
        return [name for name in os.listdir(self.tmp.name) if name.startswith(".agent.env.")]


class UpdateEnvFileTest(EnvFileTestCase):

    def test_replaces_only_target_keys_and_keeps_comments_and_order(self):
        original = update_env_file(self.env_path, {"AGENT_TOKEN": NEW_TOKEN, "EXTRA_KEY": "1"})

        self.assertEqual(ENV_TEXT, original)
        self.assertEqual(ENV_TEXT.replace(OLD_TOKEN, NEW_TOKEN) + "EXTRA_KEY=1\n", self.read_env())
        self.assertEqual([], self.leftover_temp_files())

    @unittest.skipUnless(os.name == "posix", "权限位只在 POSIX 上有意义")
    def test_preserves_file_mode(self):
        update_env_file(self.env_path, {"AGENT_TOKEN": NEW_TOKEN})

        self.assertEqual(0o600, stat.S_IMODE(os.stat(self.env_path).st_mode))

    def test_failed_replace_leaves_original_untouched(self):
        with patch("agent_commands.os.replace", side_effect=OSError("disk full")):
            with self.assertRaises(OSError):
                update_env_file(self.env_path, {"AGENT_TOKEN": NEW_TOKEN})

        self.assertEqual(ENV_TEXT, self.read_env())
        self.assertEqual([], self.leftover_temp_files())

    def test_rejects_values_that_would_inject_lines(self):
        with self.assertRaises(ValueError):
            update_env_file(self.env_path, {"AGENT_TOKEN": NEW_TOKEN + "\nPLATFORM_AGENT=1"})
        with self.assertRaises(ValueError):
            update_env_file(self.env_path, {"bad key": "x"})
        self.assertEqual(ENV_TEXT, self.read_env())


class RotationFixture(EnvFileTestCase):

    def setUp(self):
        super().setUp()
        self.env_backup = os.environ.get("AGENT_TOKEN")
        self.credentials = AgentCredentials(7, OLD_TOKEN)
        self.seen = []
        self.credentials.add_listener(self.seen.append)
        self.rotator = TokenRotator(self.credentials, lambda: self.env_path)

    def tearDown(self):
        if self.env_backup is None:
            os.environ.pop("AGENT_TOKEN", None)
        else:
            os.environ["AGENT_TOKEN"] = self.env_backup
        super().tearDown()


class TokenRotatorTest(RotationFixture):

    def test_apply_persists_then_switches_memory_and_environment(self):
        rotation = self.rotator.apply({"agentToken": NEW_TOKEN, "generation": 3})

        self.assertIn(f"AGENT_TOKEN={NEW_TOKEN}\n", self.read_env())
        self.assertEqual(NEW_TOKEN, self.credentials.token)
        self.assertEqual(NEW_TOKEN, os.environ["AGENT_TOKEN"])
        self.assertEqual([NEW_TOKEN], self.seen)
        self.assertEqual(OLD_TOKEN, rotation.old_token)

        self.rotator.rollback(rotation)
        self.assertEqual(ENV_TEXT, self.read_env())
        self.assertEqual(OLD_TOKEN, self.credentials.token)
        self.assertEqual(OLD_TOKEN, os.environ["AGENT_TOKEN"])

    def test_same_token_is_idempotent(self):
        self.assertIsNone(self.rotator.apply({"agentToken": OLD_TOKEN}))
        self.assertEqual(ENV_TEXT, self.read_env())
        self.assertEqual([], self.seen)

    def test_malformed_token_is_rejected_without_changes(self):
        for bad in ("", "short", "x" * 300, OLD_TOKEN + " y", None):
            with self.assertRaises(ValueError):
                self.rotator.apply({"agentToken": bad})
        self.assertEqual(ENV_TEXT, self.read_env())
        self.assertEqual(OLD_TOKEN, self.credentials.token)

    def test_missing_env_file_refuses_to_switch(self):
        rotator = TokenRotator(self.credentials, lambda: None)

        with self.assertRaises(RuntimeError):
            rotator.apply({"agentToken": NEW_TOKEN})
        self.assertEqual(OLD_TOKEN, self.credentials.token)

    def test_write_failure_keeps_old_token(self):
        with patch("agent_commands.os.replace", side_effect=OSError("read-only file system")):
            with self.assertRaises(OSError):
                self.rotator.apply({"agentToken": NEW_TOKEN})
        self.assertEqual(OLD_TOKEN, self.credentials.token)
        self.assertEqual(ENV_TEXT, self.read_env())


class FakeResponse:
    def __init__(self, payload):
        self.payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self.payload


class ControlPlaneSession:
    """模拟控制面：记录每次请求携带的令牌；可配置拒绝新令牌或模拟网络错误。"""

    def __init__(self, commands, reject_tokens=(), fail_ack_with=None):
        self.commands = commands
        self.reject_tokens = set(reject_tokens)
        self.fail_ack_with = fail_ack_with
        self.calls = []

    def post(self, url, json, timeout):
        self.calls.append((url.rsplit("/", 1)[-1], json.get("agentToken"), json))
        if json.get("agentToken") in self.reject_tokens:
            return FakeResponse({"code": AGENT_TOKEN_INVALID_CODE, "msg": "Agent 认证令牌无效"})
        if url.endswith("/poll"):
            commands, self.commands = self.commands, []
            return FakeResponse({"code": 0, "data": commands})
        if url.endswith("/ack") and self.fail_ack_with and json.get("agentToken") == NEW_TOKEN:
            raise self.fail_ack_with
        return FakeResponse({"code": 0, "data": True})


class RunnerTokenRotationTest(RotationFixture):

    def run_rotation(self, session):
        client = AgentCommandClient("https://cp/admin-api/node/agent", 7, "", session=session,
                                    credentials=self.credentials)
        AgentCommandRunner(client, {}, token_rotator=self.rotator).run_once()
        return [(name, token) for name, token, _ in session.calls]

    def rotate_command(self):
        return [{"id": 55, "commandType": TOKEN_ROTATE_COMMAND,
                 "payload": {"agentToken": NEW_TOKEN, "generation": 4}}]

    def test_success_confirms_with_new_token_then_reports(self):
        session = ControlPlaneSession(self.rotate_command())

        calls = self.run_rotation(session)

        self.assertEqual([("poll", OLD_TOKEN), ("ack", OLD_TOKEN), ("ack", NEW_TOKEN), ("result", NEW_TOKEN)], calls)
        result = session.calls[-1][2]
        self.assertEqual("succeeded", result["status"])
        self.assertEqual({"generation": 4, "applied": True}, result["result"])
        self.assertNotIn(NEW_TOKEN, str(result["result"]), "结果里不回传令牌")
        self.assertEqual(NEW_TOKEN, self.credentials.token)
        self.assertIn(f"AGENT_TOKEN={NEW_TOKEN}\n", self.read_env())

    def test_rejected_new_token_rolls_back_file_and_memory(self):
        session = ControlPlaneSession(self.rotate_command(), reject_tokens=[NEW_TOKEN])

        calls = self.run_rotation(session)

        self.assertEqual(("result", OLD_TOKEN), calls[-1], "回滚后用旧令牌上报失败")
        self.assertEqual("failed", session.calls[-1][2]["status"])
        self.assertTrue(session.calls[-1][2]["result"]["rolledBack"])
        self.assertEqual(OLD_TOKEN, self.credentials.token)
        self.assertEqual(ENV_TEXT, self.read_env())

    def test_network_error_during_confirmation_keeps_new_token(self):
        session = ControlPlaneSession(self.rotate_command(), fail_ack_with=ConnectionError("reset"))

        calls = self.run_rotation(session)

        self.assertEqual(("result", NEW_TOKEN), calls[-1])
        self.assertEqual("succeeded", session.calls[-1][2]["status"])
        self.assertEqual(NEW_TOKEN, self.credentials.token)

    def test_redelivered_command_is_idempotent(self):
        self.run_rotation(ControlPlaneSession(self.rotate_command()))
        session = ControlPlaneSession(self.rotate_command())

        calls = self.run_rotation(session)

        self.assertEqual([("poll", NEW_TOKEN), ("ack", NEW_TOKEN), ("result", NEW_TOKEN)], calls)
        self.assertEqual({"generation": 4, "applied": False}, session.calls[-1][2]["result"])

    def test_env_write_failure_reports_failed_with_old_token(self):
        session = ControlPlaneSession(self.rotate_command())
        with patch("agent_commands.os.replace", side_effect=OSError("read-only file system")):
            calls = self.run_rotation(session)

        self.assertEqual(("result", OLD_TOKEN), calls[-1])
        self.assertEqual("failed", session.calls[-1][2]["status"])
        self.assertIn("read-only", session.calls[-1][2]["error"])
        self.assertEqual(OLD_TOKEN, self.credentials.token)


class AgentServerFollowsRotationTest(unittest.TestCase):

    def test_inbound_token_check_switches_with_shared_credentials(self):
        creds = agent_commands.credentials
        original = creds.token
        env_backup = os.environ.get("AGENT_TOKEN")
        client = agent_server.app.test_client()
        try:
            creds.replace_token(OLD_TOKEN)
            self.assertEqual(200, client.get("/workload/list", headers={"X-Agent-Token": OLD_TOKEN}).status_code)

            creds.replace_token(NEW_TOKEN)

            self.assertEqual(NEW_TOKEN, agent_server.AGENT_TOKEN)
            self.assertEqual(200, client.get("/workload/list", headers={"X-Agent-Token": NEW_TOKEN}).status_code)
            rejected = client.get("/workload/list", headers={"X-Agent-Token": OLD_TOKEN})
            self.assertEqual(401, rejected.get_json()["code"])
        finally:
            creds.replace_token(original)
            if env_backup is None:
                os.environ.pop("AGENT_TOKEN", None)
            else:
                os.environ["AGENT_TOKEN"] = env_backup
            agent_server.auth_limiter._failures.clear()
            agent_server.auth_limiter._blocked_until.clear()


if __name__ == "__main__":
    unittest.main()
