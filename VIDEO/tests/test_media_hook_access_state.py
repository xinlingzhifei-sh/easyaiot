import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from flask import Flask, jsonify


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

sys.modules.setdefault(
    "app.services.dvr_device_resolver",
    types.SimpleNamespace(resolve_device_from_hook=Mock(return_value=("cam-001", None))),
)
sys.modules.setdefault(
    "app.services.dvr_upload_service",
    types.SimpleNamespace(process_dvr_event=Mock()),
)
sys.modules.setdefault(
    "app.services.media_kafka_service",
    types.SimpleNamespace(
        build_event_from_srs_hook=Mock(return_value={}),
        build_event_from_zlm_hook=Mock(return_value={}),
        enqueue_srs_dvr_hook=Mock(),
        enqueue_zlm_record_hook=Mock(),
        is_hybrid_upload_mode=Mock(return_value=False),
        is_kafka_upload_mode=Mock(return_value=False),
        is_snap_kafka_mode=Mock(return_value=False),
        publish_dvr_event=Mock(),
        publish_snap_event=Mock(),
    ),
)
sys.modules.setdefault(
    "app.services.snap_upload_service",
    types.SimpleNamespace(build_snap_event=Mock(return_value={}), process_snap_event=Mock()),
)
sys.modules.setdefault(
    "app.services.device_access_state_service",
    types.SimpleNamespace(record_srs_publish_online=Mock(), record_media_stream_offline=Mock()),
)
sys.modules.setdefault(
    "app.services.rtmp_ingest_auth_service",
    types.SimpleNamespace(verify_rtmp_publish_hook=Mock(return_value={"accepted": True})),
)

camera_module = types.SimpleNamespace()


def _ok_publish_callback():
    return jsonify({"code": 0, "msg": None})


camera_module._handle_authorized_on_publish_callback = _ok_publish_callback
sys.modules["app.blueprints.camera"] = camera_module

from app.blueprints import media_hook


class MediaHookAccessStateTest(unittest.TestCase):

    @patch("app.blueprints.media_hook._require_internal_hook_token", return_value=None)
    def test_srs_on_publish_verifies_signed_ingest_before_delegating(self, _authorize):
        app = Flask(__name__)
        payload = {
            "app": "live",
            "stream": "cam-001",
            "param": "?tenant=tenant-a&exp=1700000060&ver=1&sig=abc",
        }

        with app.test_request_context("/hook/srs/on_publish", method="POST", json=payload):
            with patch("app.blueprints.media_hook.verify_rtmp_publish_hook", return_value={"accepted": True}) as verify:
                with patch("app.blueprints.media_hook.record_srs_publish_online") as record_online:
                    response = media_hook.srs_on_publish()

        self.assertEqual(200, response.status_code)
        verify.assert_called_once_with(payload, remote_ip=None, source_event="srs.on_publish")
        record_online.assert_called_once_with(
            payload,
            node_id=None,
            tenant_id=None,
            source_event="srs.on_publish",
        )

    @patch("app.blueprints.media_hook._require_internal_hook_token", return_value=None)
    def test_srs_on_publish_rejects_unsigned_ingest_without_delegating(self, _authorize):
        app = Flask(__name__)
        payload = {"app": "live", "stream": "cam-001", "param": ""}
        camera_module._handle_authorized_on_publish_callback = Mock(side_effect=_ok_publish_callback)

        with app.test_request_context("/hook/srs/on_publish", method="POST", json=payload):
            with patch(
                "app.blueprints.media_hook.verify_rtmp_publish_hook",
                return_value={
                    "accepted": False,
                    "reason_code": "rtmp_missing_sig",
                    "reason_message": "RTMP ingest signature is required",
                },
            ) as verify:
                response = media_hook.srs_on_publish()

        self.assertEqual(403, response[1])
        self.assertEqual({"code": 403, "msg": "RTMP ingest signature is required"}, response[0].get_json())
        verify.assert_called_once_with(payload, remote_ip=None, source_event="srs.on_publish")
        camera_module._handle_authorized_on_publish_callback.assert_not_called()

    @patch("app.blueprints.media_hook._require_internal_hook_token", return_value=None)
    def test_zlm_on_publish_records_zlm_source_event(self, _authorize):
        app = Flask(__name__)
        payload = {
            "app": "live",
            "stream": "cam-001",
            "param": "?tenant=tenant-a&exp=1700000060&ver=1&sig=abc",
        }

        with app.test_request_context("/hook/zlm/on_publish", method="POST", json=payload):
            with patch(
                "app.blueprints.media_hook.verify_rtmp_publish_hook",
                return_value={"accepted": True, "tenant_id": "tenant-a"},
            ) as verify:
                with patch("app.blueprints.media_hook.record_srs_publish_online") as record_online:
                    response = media_hook.zlm_on_publish()

        self.assertEqual({"code": 0, "msg": None}, response.get_json())
        verify.assert_called_once_with(payload, remote_ip=None, source_event="zlm.on_publish")
        record_online.assert_called_once_with(
            payload,
            node_id=None,
            tenant_id="tenant-a",
            source_event="zlm.on_publish",
        )

    @patch("app.blueprints.media_hook._require_internal_hook_token", return_value=None)
    def test_zlm_on_stream_changed_route_accepts_notifications(self, _authorize):
        app = Flask(__name__)
        app.register_blueprint(media_hook.media_hook_bp)

        response = app.test_client().post(
            "/hook/zlm/on_stream_changed",
            json={"schema": "rtmp", "app": "live", "stream": "cam-001", "regist": False},
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual({"code": 0, "msg": None}, response.get_json())

    @patch("app.blueprints.media_hook._require_internal_hook_token", return_value=None)
    def test_zlm_on_stream_changed_records_offline_when_unregistered(self, _authorize):
        app = Flask(__name__)
        payload = {"schema": "rtmp", "app": "live", "stream": "cam-001", "regist": False, "node_id": 7}

        with app.test_request_context("/hook/zlm/on_stream_changed", method="POST", json=payload):
            with patch("app.blueprints.media_hook.record_media_stream_offline") as record_offline:
                response = media_hook.zlm_on_stream_changed()

        self.assertEqual({"code": 0, "msg": None}, response.get_json())
        record_offline.assert_called_once_with(
            payload,
            node_id=7,
            tenant_id=None,
            source_event="zlm.on_stream_changed",
        )

    @patch("app.blueprints.media_hook._require_internal_hook_token", return_value=None)
    def test_srs_on_unpublish_records_stream_offline(self, _authorize):
        app = Flask(__name__)
        payload = {"app": "live", "stream": "cam-001", "node_id": 7}

        with app.test_request_context("/hook/srs/on_unpublish", method="POST", json=payload):
            with patch("app.blueprints.media_hook.record_media_stream_offline") as record_offline:
                response = media_hook.srs_on_unpublish()

        self.assertEqual({"code": 0, "msg": None}, response.get_json())
        record_offline.assert_called_once_with(
            payload,
            node_id=7,
            tenant_id=None,
            source_event="srs.on_unpublish",
        )


if __name__ == "__main__":
    unittest.main()
