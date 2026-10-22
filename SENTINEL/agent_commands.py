import logging
import os
import re
import tempfile
import threading
import time
from typing import Any, Callable, Dict, List, Optional

import requests

logger = logging.getLogger("easyaiot-node-agent.commands")

TOKEN_ROTATE_COMMAND = "agent.token.rotate"
# iot-node ErrorCodeConstants.AGENT_TOKEN_INVALID
AGENT_TOKEN_INVALID_CODE = 1005002000
_TOKEN_PATTERN = re.compile(r"^[A-Za-z0-9._~+/-]{16,256}$")


def _env_node_id() -> int:
    try:
        return int(os.environ.get("NODE_ID", "0") or 0)
    except ValueError:
        return 0


class AgentCredentials:
    """进程内唯一的 Agent 凭据：心跳、指令通道、自愈上报与 9100 鉴权都从这里取当前令牌。

    轮换时同步 os.environ['AGENT_TOKEN']（sentinel/remediator.py 按调用时读环境变量），
    并通知各使用方（agent_server 的 X-Agent-Token 校验、run_sentinel 的心跳）。
    """

    def __init__(self, node_id: int, token: str):
        self._lock = threading.Lock()
        self.node_id = node_id
        self._token = token or ""
        self._listeners: List[Callable[[str], None]] = []

    @property
    def token(self) -> str:
        with self._lock:
            return self._token

    def add_listener(self, listener: Callable[[str], None]) -> None:
        with self._lock:
            self._listeners.append(listener)

    def replace_token(self, new_token: str) -> str:
        with self._lock:
            old = self._token
            self._token = new_token
            listeners = list(self._listeners)
        os.environ["AGENT_TOKEN"] = new_token
        for listener in listeners:
            try:
                listener(new_token)
            except Exception:
                logger.exception("credential listener failed")
        return old


credentials = AgentCredentials(_env_node_id(), os.environ.get("AGENT_TOKEN", ""))


def resolve_agent_env_file() -> Optional[str]:
    """agent.env 位置：AGENT_ENV_FILE 优先，其次是标准安装目录与源码目录。"""
    explicit = os.environ.get("AGENT_ENV_FILE", "").strip()
    if explicit:
        return explicit if os.path.isfile(explicit) else None
    for candidate in (
        "/opt/easyaiot/sentinel-agent/agent.env",
        "/opt/easyaiot/node-agent/agent.env",
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "agent.env"),
    ):
        if os.path.isfile(candidate):
            return candidate
    return None


def _write_atomically(path: str, content: str) -> None:
    """同目录临时文件 + fsync + os.replace：任何一步失败原文件都保持原样。"""
    directory = os.path.dirname(os.path.abspath(path))
    st = os.stat(path)
    fd, tmp = tempfile.mkstemp(dir=directory, prefix=".agent.env.")
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as f:
            f.write(content)
            f.flush()
            os.fsync(f.fileno())
        os.chmod(tmp, st.st_mode & 0o7777)
        if hasattr(os, "chown"):
            try:
                os.chown(tmp, st.st_uid, st.st_gid)
            except PermissionError:
                pass
        os.replace(tmp, path)
    except BaseException:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise
    try:
        dir_fd = os.open(directory, os.O_RDONLY)
    except (OSError, AttributeError):
        return
    try:
        os.fsync(dir_fd)
    except OSError:
        pass
    finally:
        os.close(dir_fd)


def update_env_file(path: str, updates: Dict[str, str]) -> str:
    """原子地替换 env 文件中的若干键（保留注释、顺序与其他键，缺的键追加到末尾），返回原内容用于回滚。"""
    for key, value in updates.items():
        if not re.fullmatch(r"[A-Z_][A-Z0-9_]*", key) or any(c in str(value) for c in ("\0", "\r", "\n")):
            raise ValueError(f"invalid env entry: {key}")
    with open(path, encoding="utf-8") as f:
        original = f.read()
    pending = dict(updates)
    lines = []
    for line in original.splitlines():
        key = line.split("=", 1)[0].strip() if "=" in line and not line.lstrip().startswith("#") else None
        if key in pending:
            lines.append(f"{key}={pending.pop(key)}")
        else:
            lines.append(line)
    for key, value in pending.items():
        lines.append(f"{key}={value}")
    _write_atomically(path, "\n".join(lines) + "\n")
    return original


def restore_env_file(path: str, content: str) -> None:
    _write_atomically(path, content)


class TokenRotation:
    """一次已经落盘、已经切换内存的令牌轮换，可整体回滚。"""

    def __init__(self, old_token: str, new_token: str, env_path: Optional[str], backup: Optional[str]):
        self.old_token = old_token
        self.new_token = new_token
        self.env_path = env_path
        self.backup = backup


class TokenRotator:
    """处理控制面推送的新令牌：先原子写 agent.env，再切换内存；失败时两者都回滚。"""

    def __init__(self, creds: AgentCredentials, env_file_resolver: Callable[[], Optional[str]] = resolve_agent_env_file):
        self.credentials = creds
        self.env_file_resolver = env_file_resolver

    def apply(self, payload: Dict[str, Any]) -> Optional[TokenRotation]:
        """返回 None 表示新令牌与当前相同（重复投递），无需改动。"""
        new_token = str(payload.get("agentToken") or "")
        if not _TOKEN_PATTERN.fullmatch(new_token):
            raise ValueError("agent token in rotate command is missing or malformed")
        if new_token == self.credentials.token:
            return None
        env_path = self.env_file_resolver()
        if not env_path:
            # 找不到 agent.env 时切换后重启会退回旧令牌，宽限期结束即掉线，宁可拒绝
            raise RuntimeError("agent.env not found; set AGENT_ENV_FILE so the new token survives restarts")
        backup = update_env_file(env_path, {"AGENT_TOKEN": new_token})
        old_token = self.credentials.replace_token(new_token)
        return TokenRotation(old_token, new_token, env_path, backup)

    def rollback(self, rotation: TokenRotation) -> None:
        if rotation.env_path and rotation.backup is not None:
            restore_env_file(rotation.env_path, rotation.backup)
        self.credentials.replace_token(rotation.old_token)


class AgentCommandRejected(RuntimeError):
    """控制面返回业务错误（code != 0）。"""

    def __init__(self, code: Any, message: str):
        super().__init__(message)
        self.code = code

    @property
    def credential_rejected(self) -> bool:
        try:
            return int(self.code) == AGENT_TOKEN_INVALID_CODE
        except (TypeError, ValueError):
            return False


class AgentCommandClient:
    def __init__(
        self,
        base_url: str,
        node_id: int,
        agent_token: str,
        session=None,
        capabilities: Optional[Dict[str, bool]] = None,
        max_commands: int = 5,
        credentials: Optional[AgentCredentials] = None,
    ):
        self.base_url = base_url.rstrip("/")
        self.node_id = node_id
        self._static_token = agent_token
        self.credentials = credentials
        self.session = session or requests.Session()
        self.capabilities = capabilities or {}
        self.max_commands = max_commands

    @property
    def agent_token(self) -> str:
        return self.credentials.token if self.credentials is not None else self._static_token

    def poll(self, max_commands: Optional[int] = None) -> List[Dict[str, Any]]:
        response = self.session.post(
            f"{self.base_url}/commands/poll",
            json={
                "nodeId": self.node_id,
                "agentToken": self.agent_token,
                "capabilities": self.capabilities,
                "maxCommands": self.max_commands if max_commands is None else max_commands,
            },
            timeout=15,
        )
        data = self._response_data(response)
        return data if isinstance(data, list) else []

    def ack(self, command_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/commands/{command_id}/ack",
            json={"nodeId": self.node_id, "agentToken": self.agent_token},
            timeout=10,
        )
        self._response_data(response)

    def heartbeat(self, command_id: int) -> None:
        response = self.session.post(
            f"{self.base_url}/commands/{command_id}/heartbeat",
            json={"nodeId": self.node_id, "agentToken": self.agent_token},
            timeout=10,
        )
        self._response_data(response)

    def report_result(
        self,
        command_id: int,
        status: str,
        result: Optional[Dict[str, Any]],
        error: Optional[str],
    ) -> None:
        response = self.session.post(
            f"{self.base_url}/commands/{command_id}/result",
            json={
                "nodeId": self.node_id,
                "agentToken": self.agent_token,
                "status": status,
                "result": result or {},
                "error": error,
            },
            timeout=15,
        )
        self._response_data(response)

    def _response_data(self, response):
        response.raise_for_status()
        data = response.json()
        if data.get("code") != 0:
            raise AgentCommandRejected(data.get("code"), data.get("msg") or "agent command request failed")
        return data.get("data")


class AgentCommandRunner:
    def __init__(
        self,
        client: AgentCommandClient,
        executors: Dict[str, Callable[[Dict[str, Any]], Dict[str, Any]]],
        token_rotator: Optional[TokenRotator] = None,
    ):
        self.client = client
        self.executors = executors
        self.token_rotator = token_rotator

    def run_once(self) -> int:
        commands = self.client.poll()
        for command in commands:
            command_id = int(command["id"])
            command_type = command["commandType"]
            payload = command.get("payload") or {}
            self.client.ack(command_id)

            if command_type == TOKEN_ROTATE_COMMAND and self.token_rotator is not None:
                self._rotate_token(command_id, payload)
                continue

            executor = self.executors.get(command_type)
            if executor is None:
                self.client.report_result(
                    command_id,
                    "failed",
                    {},
                    f"unsupported command type: {command_type}",
                )
                continue

            try:
                result = executor(payload)
                self.client.report_result(command_id, "succeeded", result, None)
            except Exception as exc:
                logger.exception("command failed id=%s type=%s", command_id, command_type)
                self.client.report_result(command_id, "failed", {}, str(exc))
        return len(commands)

    def _rotate_token(self, command_id: int, payload: Dict[str, Any]) -> None:
        generation = payload.get("generation")
        try:
            rotation = self.token_rotator.apply(payload)
        except Exception as exc:
            logger.error("agent token rotation not applied: %s", exc)
            self.client.report_result(command_id, "failed", {"generation": generation}, str(exc))
            return
        if rotation is None:
            # 重复投递：新令牌已在用
            self.client.report_result(command_id, "succeeded", {"generation": generation, "applied": False}, None)
            return
        try:
            # 用新令牌回连一次，确认控制面已接受；控制面据此把调用 9100 的令牌也切过去
            self.client.ack(command_id)
        except AgentCommandRejected as exc:
            if exc.credential_rejected:
                logger.error("control plane rejected the new agent token, rolling back")
                self.token_rotator.rollback(rotation)
                self.client.report_result(
                    command_id, "failed", {"generation": generation, "rolledBack": True},
                    "new agent token rejected by control plane")
                return
            logger.warning("agent token switched but confirmation failed: %s", exc)
        except Exception as exc:
            # 网络错误：控制面上新令牌已是当前值，保留；结果上报失败时指令会重新投递，apply 幂等
            logger.warning("agent token switched but confirmation failed: %s", exc)
        logger.info("agent token rotated (generation=%s), agent.env updated", generation)
        self.client.report_result(command_id, "succeeded", {"generation": generation, "applied": True}, None)

    def run_forever(self, interval_seconds: float) -> None:
        while True:
            try:
                handled = self.run_once()
                time.sleep(0 if handled else interval_seconds)
            except Exception as exc:
                logger.warning("command poll failed: %s", exc)
                time.sleep(interval_seconds)
