import importlib
import sys
import types
import unittest
from datetime import datetime, timedelta
from pathlib import Path
from unittest.mock import Mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

event_model = Mock()
current_model = Mock()
db = types.SimpleNamespace(session=Mock())
sys.modules["models"] = types.SimpleNamespace(
    Device=object,
    DeviceAccessStateEvent=event_model,
    DeviceAccessStateCurrent=current_model,
    StreamForwardTask=object,
    db=db,
)
sys.modules.pop("app.services.device_access_state_service", None)

state_service = importlib.import_module("app.services.device_access_state_service")
state_service = importlib.reload(state_service)
record_device_access_event = state_service.record_device_access_event
record_srs_publish_online = state_service.record_srs_publish_online
record_media_stream_offline = state_service.record_media_stream_offline
record_device_play_error = state_service.record_device_play_error
record_device_play_ready = state_service.record_device_play_ready
get_device_access_summary = state_service.get_device_access_summary
get_device_access_health_snapshot = state_service.get_device_access_health_snapshot
list_device_access_events = state_service.list_device_access_events


class FakeEventQuery:

    def __init__(self, rows):
        self.rows = rows
        self.filters = []
        self.limit_value = None

    def filter_by(self, **criteria):
        self.filters.append(criteria)
        return self

    def order_by(self, *_args, **_kwargs):
        return self

    def limit(self, value):
        self.limit_value = value
        return self

    def all(self):
        rows = self.rows
        for criteria in self.filters:
            rows = [
                row for row in rows
                if all(getattr(row, key, None) == value for key, value in criteria.items())
            ]
        return rows[:self.limit_value]


class DeviceAccessStateServiceTest(unittest.TestCase):

    def setUp(self):
        event_model.reset_mock()
        current_model.reset_mock()
        db.session.reset_mock()
        event_model.query = Mock()
        current_model.query = Mock()
        current_model.query.filter_by.return_value.one_or_none.return_value = None

    def test_records_transition_and_materializes_current_state(self):
        event = Mock()
        current = Mock()
        event_model.return_value = event
        current_model.return_value = current

        result = record_device_access_event(
            device_id="cam-001",
            protocol="edge_agent",
            state="registering",
            reason_code="edge_command_queued",
            reason_message="Edge Agent command queued",
            source_event="stream_forward.deploy.enqueued",
            stream_id="live/cam-001",
            node_id=7,
            tenant_id="tenant-a",
        )

        event_model.assert_called_once()
        current_model.assert_called_once_with(device_id="cam-001", protocol="edge_agent")
        self.assertEqual(event, result["event"])
        self.assertEqual(current, result["current"])
        self.assertEqual("registering", current.state)
        self.assertEqual("edge_command_queued", current.reason_code)
        self.assertEqual("Edge Agent command queued", current.reason_message)
        self.assertEqual("stream_forward.deploy.enqueued", current.source_event)
        self.assertEqual("live/cam-001", current.stream_id)
        self.assertEqual(7, current.node_id)
        self.assertEqual("tenant-a", current.tenant_id)
        db.session.add.assert_any_call(event)
        db.session.add.assert_any_call(current)
        db.session.commit.assert_called_once()

    def test_srs_publish_advances_edge_agent_stream_online_when_edge_is_registering(self):
        current = Mock()
        current.state = "registering"
        current_model.query.filter_by.return_value.one_or_none.return_value = current

        result = record_srs_publish_online(
            {"app": "live", "stream": "cam-001"},
            node_id=7,
            tenant_id="tenant-a",
        )

        self.assertEqual(current, result["current"])
        self.assertEqual("stream_online", current.state)
        self.assertEqual("srs_publish_online", current.reason_code)
        self.assertEqual("SRS publish hook confirmed live stream", current.reason_message)
        self.assertEqual("srs.on_publish", current.source_event)
        self.assertEqual("live/cam-001", current.stream_id)
        self.assertEqual(7, current.node_id)
        self.assertEqual("tenant-a", current.tenant_id)

    def test_zlm_publish_records_zlm_source_event_and_reason(self):
        current = Mock()
        current.state = "registering"
        current_model.query.filter_by.return_value.one_or_none.return_value = current

        record_srs_publish_online(
            {"app": "live", "stream": "cam-001"},
            tenant_id="tenant-a",
            source_event="zlm.on_publish",
        )

        self.assertEqual("stream_online", current.state)
        self.assertEqual("zlm_publish_online", current.reason_code)
        self.assertEqual("ZLM publish hook confirmed live stream", current.reason_message)
        self.assertEqual("zlm.on_publish", current.source_event)

    def test_zlm_stream_unregister_marks_edge_stream_error(self):
        current = Mock()
        current.state = "stream_online"
        current_model.query.filter_by.return_value.one_or_none.return_value = current

        result = record_media_stream_offline(
            {"app": "live", "stream": "cam-001", "regist": False},
            node_id=7,
            tenant_id="tenant-a",
            source_event="zlm.on_stream_changed",
        )

        self.assertEqual(current, result["current"])
        self.assertEqual("error", current.state)
        self.assertEqual("zlm_stream_offline", current.reason_code)
        self.assertEqual("ZLM stream changed hook reported stream offline", current.reason_message)
        self.assertEqual("zlm.on_stream_changed", current.source_event)
        self.assertEqual("live/cam-001", current.stream_id)
        self.assertEqual(7, current.node_id)
        self.assertEqual("tenant-a", current.tenant_id)

    def test_device_access_summary_collapses_protocol_rows_for_ui(self):
        edge = Mock()
        edge.protocol = "edge_agent"
        edge.state = "stream_online"
        edge.reason_code = "srs_publish_online"
        edge.reason_message = "SRS publish hook confirmed live stream"
        edge.last_transition_time = None
        webrtc = Mock()
        webrtc.protocol = "webrtc"
        webrtc.state = "play_ready"
        webrtc.reason_code = "webrtc_probe_ok"
        webrtc.reason_message = "WebRTC playback probe passed"
        webrtc.last_transition_time = None
        current_model.query.filter_by.return_value.all.return_value = [edge, webrtc]

        summary = get_device_access_summary("cam-001")

        self.assertEqual("play_ready", summary["state"])
        self.assertTrue(summary["play_ready"])
        self.assertFalse(summary["ai_ready"])
        self.assertEqual("webrtc_probe_ok", summary["reason_code"])
        self.assertEqual("WebRTC playback probe passed", summary["reason_message"])
        self.assertEqual(["edge_agent", "webrtc"], summary["protocols"])

    def test_records_http_flv_play_ready_from_play_url(self):
        event = Mock()
        current = Mock()
        event_model.return_value = event
        current_model.return_value = current

        result = record_device_play_ready(
            device_id="cam-001",
            play_url="https://eye.example.com/live/cam-001.flv",
            tenant_id="tenant-a",
        )

        current_model.assert_called_once_with(device_id="cam-001", protocol="http_flv")
        self.assertEqual(event, result["event"])
        self.assertEqual(current, result["current"])
        self.assertEqual("play_ready", current.state)
        self.assertEqual("http_flv_play_ready", current.reason_code)
        self.assertEqual("HTTP-FLV playback URL is ready", current.reason_message)
        self.assertEqual("play.probe.ready", current.source_event)
        self.assertEqual("live/cam-001", current.stream_id)
        self.assertEqual("tenant-a", current.tenant_id)
        db.session.commit.assert_called_once()

    def test_records_webrtc_ai_ready_when_ai_probe_passes(self):
        event = Mock()
        current = Mock()
        event_model.return_value = event
        current_model.return_value = current

        record_device_play_ready(
            device_id="cam-001",
            protocol="webrtc",
            play_url="webrtc://media.example.com/live/cam-001",
            ai=True,
        )

        current_model.assert_called_once_with(device_id="cam-001", protocol="webrtc")
        self.assertEqual("ai_ready", current.state)
        self.assertEqual("webrtc_ai_ready", current.reason_code)
        self.assertEqual("WebRTC AI playback URL is ready", current.reason_message)

    def test_records_play_ready_with_custom_reason_and_source_event(self):
        current = Mock()
        current_model.return_value = current

        record_device_play_ready(
            device_id="cam-001",
            protocol="webrtc",
            play_url="webrtc://media.example.com/live/cam-001",
            reason_code="webrtc_remote_stream_ready",
            reason_message="WebRTC remote stream is playing",
            source_event="webrtc.remote.stream",
        )

        self.assertEqual("play_ready", current.state)
        self.assertEqual("webrtc_remote_stream_ready", current.reason_code)
        self.assertEqual("WebRTC remote stream is playing", current.reason_message)
        self.assertEqual("webrtc.remote.stream", current.source_event)

    def test_records_play_error_with_normalized_reason(self):
        event = Mock()
        current = Mock()
        event_model.return_value = event
        current_model.return_value = current

        record_device_play_error(
            device_id="gb28181_34020000001320000001_34020000001320000002",
            protocol="gb28181",
            reason_code="gb28181_play_url_unavailable",
            reason_message="WVP play returned no playable stream",
            source_event="gb28181.play.start",
        )

        current_model.assert_called_once_with(
            device_id="gb28181_34020000001320000001_34020000001320000002",
            protocol="gb28181",
        )
        self.assertEqual("error", current.state)
        self.assertEqual("gb28181_play_url_unavailable", current.reason_code)
        self.assertEqual("WVP play returned no playable stream", current.reason_message)
        self.assertEqual("gb28181.play.start", current.source_event)

    def test_health_snapshot_flags_error_and_stale_access_states(self):
        now = datetime(2026, 6, 14, 10, 0, 0)
        rtmp_error = types.SimpleNamespace(
            device_id="cam-rtmp",
            protocol="rtmp",
            state="error",
            reason_code="rtmp_bad_sig",
            reason_message="RTMP ingest signature is invalid",
            source_event="srs.on_publish",
            last_transition_time=now - timedelta(seconds=30),
            stream_id="live/cam-rtmp",
            node_id=3,
            tenant_id="tenant-a",
        )
        stale_edge = types.SimpleNamespace(
            device_id="cam-edge",
            protocol="edge_agent",
            state="registering",
            reason_code="edge_command_queued",
            reason_message="Edge Agent command queued",
            source_event="stream_forward.deploy.enqueued",
            last_transition_time=now - timedelta(seconds=300),
            stream_id="live/cam-edge",
            node_id=7,
            tenant_id="tenant-a",
        )
        healthy_webrtc = types.SimpleNamespace(
            device_id="cam-ready",
            protocol="webrtc",
            state="play_ready",
            reason_code="webrtc_play_ready",
            reason_message="WebRTC playback URL is ready",
            source_event="play.probe.ready",
            last_transition_time=now - timedelta(seconds=10),
            stream_id="live/cam-ready",
            node_id=8,
            tenant_id="tenant-a",
        )
        current_model.query.all.return_value = [rtmp_error, stale_edge, healthy_webrtc]

        snapshot = get_device_access_health_snapshot(
            now=now,
            stale_after_seconds=120,
        )

        self.assertEqual(3, snapshot["total"])
        self.assertEqual({"error": 1, "registering": 1, "play_ready": 1}, snapshot["state_counts"])
        self.assertEqual(2, snapshot["alert_count"])
        self.assertEqual(
            ["rtmp_bad_sig", "registering_stale"],
            [alert["reason_code"] for alert in snapshot["alerts"]],
        )
        self.assertEqual(["critical", "warning"], [alert["severity"] for alert in snapshot["alerts"]])
        self.assertEqual([30, 300], [alert["age_seconds"] for alert in snapshot["alerts"]])
        self.assertEqual("cam-edge", snapshot["alerts"][1]["device_id"])
        self.assertIn("registering", snapshot["alerts"][1]["reason_message"])

    def test_lists_recent_events_for_device_with_normalized_filters(self):
        event_time = datetime(2026, 6, 14, 10, 1, 2)
        rows = [
            types.SimpleNamespace(
                id=1,
                device_id="cam-001",
                protocol="webrtc",
                state="error",
                reason_code="webrtc_ice_candidate_error",
                reason_message="ICE candidate gathering failed",
                source_event="webrtc.pc.icecandidateerror",
                event_time=event_time,
                stream_id="live/cam-001",
                node_id=8,
                tenant_id="tenant-a",
                created_at=event_time,
                to_dict=lambda: {
                    "id": 1,
                    "device_id": "cam-001",
                    "protocol": "webrtc",
                    "state": "error",
                    "reason_code": "webrtc_ice_candidate_error",
                    "reason_message": "ICE candidate gathering failed",
                    "source_event": "webrtc.pc.icecandidateerror",
                    "event_time": "2026-06-14T10:01:02",
                    "stream_id": "live/cam-001",
                    "node_id": 8,
                    "tenant_id": "tenant-a",
                    "created_at": "2026-06-14T10:01:02",
                },
            ),
            types.SimpleNamespace(device_id="cam-001", protocol="rtmp", tenant_id="tenant-a"),
            types.SimpleNamespace(device_id="cam-002", protocol="webrtc", tenant_id="tenant-a"),
        ]
        event_model.query = FakeEventQuery(rows)

        result = list_device_access_events(
            " cam-001 ",
            limit=250,
            protocol=" WebRTC ",
            tenant_id=" tenant-a ",
        )

        self.assertEqual("cam-001", result["device_id"])
        self.assertEqual(100, result["limit"])
        self.assertEqual(1, len(result["events"]))
        self.assertEqual("webrtc", result["events"][0]["protocol"])
        self.assertEqual("error", result["events"][0]["state"])
        self.assertEqual("webrtc_ice_candidate_error", result["events"][0]["reason_code"])
        self.assertEqual("ICE candidate gathering failed", result["events"][0]["reason_message"])
        self.assertEqual("webrtc.pc.icecandidateerror", result["events"][0]["source_event"])


if __name__ == "__main__":
    unittest.main()
