import unittest

from agent_commands import AgentCommandClient, AgentCommandRunner


class FakeResponse:
    def __init__(self, payload, status_code=200):
        self.payload = payload
        self.status_code = status_code

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")

    def json(self):
        return self.payload


class FakeSession:
    def __init__(self):
        self.calls = []
        self.next_response = FakeResponse({"code": 0, "data": []})

    def post(self, url, json, timeout):
        self.calls.append({"url": url, "json": json, "timeout": timeout})
        return self.next_response


class AgentCommandClientTest(unittest.TestCase):

    def test_poll_posts_node_credentials_and_returns_command_data(self):
        session = FakeSession()
        session.next_response = FakeResponse({
            "code": 0,
            "data": [
                {
                    "id": 101,
                    "commandType": "stream_forward.deploy",
                    "payload": {"deviceId": "cam-001"},
                },
            ],
        })
        client = AgentCommandClient(
            "http://control/admin-api/node/agent",
            node_id=7,
            agent_token="secret",
            session=session,
            max_commands=3,
        )

        commands = client.poll()

        self.assertEqual(1, len(commands))
        self.assertEqual("http://control/admin-api/node/agent/commands/poll", session.calls[0]["url"])
        self.assertEqual({
            "nodeId": 7,
            "agentToken": "secret",
            "capabilities": {},
            "maxCommands": 3,
        }, session.calls[0]["json"])

    def test_ack_heartbeat_and_report_result_use_command_endpoints(self):
        session = FakeSession()
        client = AgentCommandClient(
            "http://control/admin-api/node/agent",
            node_id=7,
            agent_token="secret",
            session=session,
        )

        client.ack(101)
        client.heartbeat(101)
        client.report_result(101, "succeeded", {"pid": 4321}, None)

        self.assertEqual("http://control/admin-api/node/agent/commands/101/ack", session.calls[0]["url"])
        self.assertEqual("http://control/admin-api/node/agent/commands/101/heartbeat", session.calls[1]["url"])
        self.assertEqual("http://control/admin-api/node/agent/commands/101/result", session.calls[2]["url"])
        self.assertEqual("succeeded", session.calls[2]["json"]["status"])
        self.assertEqual({"pid": 4321}, session.calls[2]["json"]["result"])


class AgentCommandRunnerTest(unittest.TestCase):

    def test_run_once_acks_executes_and_reports_success(self):
        client = FakeCommandClient([
            {
                "id": 101,
                "commandType": "stream_forward.deploy",
                "payload": {"deviceId": "cam-001"},
            },
        ])
        runner = AgentCommandRunner(
            client,
            {"stream_forward.deploy": lambda payload: {"pid": 4321, "deviceId": payload["deviceId"]}},
        )

        handled = runner.run_once()

        self.assertEqual(1, handled)
        self.assertEqual([101], client.acked)
        self.assertEqual([(101, "succeeded", {"pid": 4321, "deviceId": "cam-001"}, None)], client.results)

    def test_run_once_reports_unsupported_command_type(self):
        client = FakeCommandClient([
            {"id": 102, "commandType": "unknown.command", "payload": {}},
        ])
        runner = AgentCommandRunner(client, {})

        handled = runner.run_once()

        self.assertEqual(1, handled)
        self.assertEqual([102], client.acked)
        self.assertEqual("failed", client.results[0][1])
        self.assertIn("unsupported command type", client.results[0][3])


class FakeCommandClient:
    def __init__(self, commands):
        self.commands = commands
        self.acked = []
        self.results = []

    def poll(self):
        return self.commands

    def ack(self, command_id):
        self.acked.append(command_id)

    def report_result(self, command_id, status, result, error):
        self.results.append((command_id, status, result, error))


if __name__ == "__main__":
    unittest.main()
