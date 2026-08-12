import logging
import time
from typing import Any, Callable, Dict, List, Optional

import requests

logger = logging.getLogger("easyaiot-node-agent.commands")


class AgentCommandClient:
    def __init__(
        self,
        base_url: str,
        node_id: int,
        agent_token: str,
        session=None,
        capabilities: Optional[Dict[str, bool]] = None,
        max_commands: int = 5,
    ):
        self.base_url = base_url.rstrip("/")
        self.node_id = node_id
        self.agent_token = agent_token
        self.session = session or requests.Session()
        self.capabilities = capabilities or {}
        self.max_commands = max_commands

    def poll(self) -> List[Dict[str, Any]]:
        response = self.session.post(
            f"{self.base_url}/commands/poll",
            json={
                "nodeId": self.node_id,
                "agentToken": self.agent_token,
                "capabilities": self.capabilities,
                "maxCommands": self.max_commands,
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
            raise RuntimeError(data.get("msg") or "agent command request failed")
        return data.get("data")


class AgentCommandRunner:
    def __init__(
        self,
        client: AgentCommandClient,
        executors: Dict[str, Callable[[Dict[str, Any]], Dict[str, Any]]],
    ):
        self.client = client
        self.executors = executors

    def run_once(self) -> int:
        commands = self.client.poll()
        for command in commands:
            command_id = int(command["id"])
            command_type = command["commandType"]
            payload = command.get("payload") or {}
            self.client.ack(command_id)

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

    def run_forever(self, interval_seconds: float) -> None:
        while True:
            try:
                handled = self.run_once()
                time.sleep(0 if handled else interval_seconds)
            except Exception as exc:
                logger.warning("command poll failed: %s", exc)
                time.sleep(interval_seconds)
