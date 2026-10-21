import sys
import types
import unittest
from datetime import datetime
from pathlib import Path
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

models_stub = sys.modules.setdefault("models", types.SimpleNamespace())
models_stub.Device = object
models_stub.DeviceAccessStateCurrent = object
models_stub.DeviceAccessStateEvent = object
models_stub.StreamForwardTask = getattr(models_stub, "StreamForwardTask", object)
models_stub.db = getattr(models_stub, "db", types.SimpleNamespace(session=Mock()))

from app.services.edge_stream_forward_service import (
    ensure_edge_rtsp_forward,
    reconcile_edge_rtsp_forward_command,
)


class EdgeStreamForwardServiceTest(unittest.TestCase):

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.allocate_device_media")
    @patch("app.services.edge_stream_forward_service.db")
    @patch("app.services.edge_stream_forward_service.Device")
    def test_ensure_edge_rtsp_forward_allocates_media_and_enqueues_command(
        self,
        device_model,
        db,
        allocate_media,
        enqueue,
        record_state,
    ):
        device = Mock()
        device.id = "cam-001"
        device.name = "Gate Camera"
        device.source = "rtsp://user:pass@10.0.0.8/live"
        device.rtmp_stream = ""
        device.http_stream = ""
        device.ai_rtmp_stream = ""
        device.ai_http_stream = ""
        device_model.query.get.return_value = device
        allocate_media.return_value = {
            "rtmpStream": "rtmp://media.example.com/live/cam-001",
            "httpStream": "https://eye.example.com/live/cam-001.flv",
        }
        enqueue.return_value = {"id": 101, "status": "pending"}

        result = ensure_edge_rtsp_forward("cam-001", edge_node_id=7)

        self.assertEqual(101, result["command"]["id"])
        enqueue.assert_called_once()
        payload = enqueue.call_args.kwargs["payload"]
        self.assertEqual("cam-001", payload["deviceId"])
        self.assertEqual("rtsp://user:pass@10.0.0.8/live", payload["rtspUrl"])
        self.assertEqual("rtmp://media.example.com/live/cam-001", payload["rtmpPushUrl"])
        record_state.assert_called_once_with(
            device_id="cam-001",
            protocol="edge_agent",
            state="registering",
            reason_code="edge_command_queued",
            reason_message="Edge Agent stream-forward command queued",
            source_event="stream_forward.deploy.enqueued",
            stream_id="live/cam-001",
            node_id=7,
            tenant_id=None,
            commit=False,
        )
        db.session.commit.assert_called_once()

    @patch("app.services.edge_stream_forward_service.Device")
    def test_ensure_edge_rtsp_forward_rejects_non_rtsp_source(self, device_model):
        device = Mock()
        device.source = "gb28181://34020000001320000001"
        device_model.query.get.return_value = device

        with self.assertRaises(ValueError):
            ensure_edge_rtsp_forward("cam-001", edge_node_id=7)

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.allocate_device_media")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    @patch("app.services.edge_stream_forward_service.db")
    @patch("app.services.edge_stream_forward_service.Device")
    def test_reconcile_stale_edge_command_requeues_before_max_attempts(
        self,
        device_model,
        db,
        get_command,
        allocate_media,
        enqueue,
        record_state,
    ):
        device = Mock()
        device.id = "cam-001"
        device.source = "rtsp://user:pass@10.0.0.8/live"
        device.rtmp_stream = "rtmp://media.example.com/live/cam-001"
        device.http_stream = "https://eye.example.com/live/cam-001.flv"
        device.ai_rtmp_stream = ""
        device.ai_http_stream = ""
        device_model.query.get.return_value = device
        get_command.return_value = {
            "id": 101,
            "status": "leased",
            "attempt": 1,
            "leaseExpiresAt": "2026-06-14T09:58:00Z",
        }
        allocate_media.return_value = {
            "rtmpStream": "rtmp://media.example.com/live/cam-001",
            "httpStream": "https://eye.example.com/live/cam-001.flv",
        }
        enqueue.return_value = {"id": 102, "status": "pending"}

        result = reconcile_edge_rtsp_forward_command(
            "cam-001",
            edge_node_id=7,
            now="2026-06-14T10:00:00Z",
            timeout_seconds=60,
            max_attempts=3,
        )

        self.assertEqual("retry_queued", result["action"])
        self.assertEqual(101, result["previousCommand"]["id"])
        self.assertEqual(102, result["command"]["id"])
        enqueue.assert_called_once()
        record_state.assert_called_once_with(
            device_id="cam-001",
            protocol="edge_agent",
            state="registering",
            reason_code="edge_command_retry_queued",
            reason_message="Edge Agent command lease expired; retry queued",
            source_event="stream_forward.deploy.retry",
            stream_id="live/cam-001",
            node_id=7,
            tenant_id=None,
            commit=False,
        )
        db.session.commit.assert_called_once()

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    def test_reconcile_stale_edge_command_marks_error_after_max_attempts(
        self,
        get_command,
        enqueue,
        record_state,
    ):
        get_command.return_value = {
            "id": 103,
            "status": "leased",
            "attempt": 3,
            "leaseExpiresAt": "2026-06-14T09:58:00Z",
        }

        result = reconcile_edge_rtsp_forward_command(
            "cam-001",
            edge_node_id=7,
            now="2026-06-14T10:00:00Z",
            timeout_seconds=60,
            max_attempts=3,
        )

        self.assertEqual("timeout", result["action"])
        enqueue.assert_not_called()
        record_state.assert_called_once_with(
            device_id="cam-001",
            protocol="edge_agent",
            state="error",
            reason_code="edge_command_timeout",
            reason_message="Edge Agent command lease expired and retry limit was reached",
            source_event="stream_forward.deploy.timeout",
            stream_id="live/cam-001",
            node_id=7,
        )

    # ---- 以下用例按 iot-node GET /node/agent/commands/by-key 的真实返回结构构造 ----

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    def test_reconcile_reads_iot_node_attempt_count_and_epoch_millis_lease(
        self,
        get_command,
        enqueue,
        record_state,
    ):
        get_command.return_value = {
            "id": 104,
            "nodeId": 7,
            "commandKey": "stream_forward:cam-001",
            "status": "leased",
            "attemptCount": 3,
            "leaseUntil": _epoch_millis("2026-06-14T09:58:00+00:00"),
            "updateTime": _epoch_millis("2026-06-14T09:57:30+00:00"),
        }

        result = reconcile_edge_rtsp_forward_command(
            "cam-001",
            edge_node_id=7,
            now="2026-06-14T10:00:00Z",
            timeout_seconds=60,
            max_attempts=3,
        )

        self.assertEqual("timeout", result["action"])
        get_command.assert_called_once_with("stream_forward:cam-001", node_id=7)
        enqueue.assert_not_called()
        self.assertEqual("edge_command_timeout", record_state.call_args.kwargs["reason_code"])

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    def test_reconcile_keeps_waiting_while_running_lease_is_fresh(
        self,
        get_command,
        enqueue,
        record_state,
    ):
        get_command.return_value = {
            "id": 105,
            "status": "running",
            "attemptCount": 1,
            "leaseUntil": _epoch_millis("2026-06-14T10:00:20+00:00"),
            "updateTime": _epoch_millis("2026-06-14T09:59:50+00:00"),
        }

        result = reconcile_edge_rtsp_forward_command(
            "cam-001",
            edge_node_id=7,
            now="2026-06-14T10:00:00Z",
            timeout_seconds=60,
        )

        self.assertEqual("waiting", result["action"])
        enqueue.assert_not_called()
        record_state.assert_not_called()

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    def test_reconcile_marks_registered_when_finished_command_succeeded(
        self,
        get_command,
        enqueue,
        record_state,
    ):
        get_command.return_value = {
            "id": 106,
            "status": "succeeded",
            "attemptCount": 1,
            "result": {"pid": 4321},
            "finishedAt": _epoch_millis("2026-06-14T09:59:00+00:00"),
        }

        result = reconcile_edge_rtsp_forward_command("cam-001", edge_node_id=7)

        self.assertEqual("registered", result["action"])
        enqueue.assert_not_called()
        record_state.assert_called_once_with(
            device_id="cam-001",
            protocol="edge_agent",
            state="registered",
            reason_code="edge_command_succeeded",
            reason_message="Edge Agent command succeeded; waiting for media publish hook",
            source_event="stream_forward.deploy.result",
            stream_id="live/cam-001",
            node_id=7,
        )

    @patch("app.services.edge_stream_forward_service.record_device_access_event")
    @patch("app.services.edge_stream_forward_service.enqueue_agent_command")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    def test_reconcile_marks_error_with_iot_node_last_error(
        self,
        get_command,
        enqueue,
        record_state,
    ):
        get_command.return_value = {
            "id": 107,
            "status": "failed",
            "attemptCount": 2,
            "lastError": "agent_command_running_timeout",
        }

        result = reconcile_edge_rtsp_forward_command("cam-001", edge_node_id=7)

        self.assertEqual("failed", result["action"])
        enqueue.assert_not_called()
        self.assertEqual("edge_command_failed", record_state.call_args.kwargs["reason_code"])
        self.assertEqual("agent_command_running_timeout", record_state.call_args.kwargs["reason_message"])

    @patch("app.services.edge_stream_forward_service.ensure_edge_rtsp_forward")
    @patch("app.services.edge_stream_forward_service.get_agent_command_by_key")
    def test_reconcile_queues_command_when_iot_node_has_none(self, get_command, ensure):
        get_command.return_value = {}
        ensure.return_value = {"deviceId": "cam-001", "command": {"id": 108, "status": "pending"}}

        result = reconcile_edge_rtsp_forward_command("cam-001", edge_node_id=7, transport="udp")

        self.assertEqual("queued", result["action"])
        self.assertEqual(108, result["command"]["id"])
        ensure.assert_called_once_with("cam-001", edge_node_id=7, transport="udp")


def _epoch_millis(iso_text):
    return int(datetime.fromisoformat(iso_text).timestamp() * 1000)


if __name__ == "__main__":
    unittest.main()
