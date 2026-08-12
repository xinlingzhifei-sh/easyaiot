import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

sys.modules.setdefault("tzlocal", types.SimpleNamespace(get_localzone_name=Mock(return_value="UTC")))
sys.modules.setdefault(
    "apscheduler.schedulers.background",
    types.SimpleNamespace(BackgroundScheduler=Mock(return_value=types.SimpleNamespace(add_job=Mock(), start=Mock()))),
)
sys.modules.setdefault("onvif", types.SimpleNamespace(ONVIFCamera=object))
sys.modules.setdefault("sqlalchemy", types.SimpleNamespace(or_=Mock()))
sys.modules.setdefault("wsdiscovery", types.SimpleNamespace(WSDiscovery=object, Scope=object))
sys.modules.setdefault(
    "app.services.nvr_service",
    types.SimpleNamespace(
        infer_nvr_link_from_source=Mock(return_value=None),
        is_nvr_channel_device=Mock(return_value=False),
        nvr_fields_for_device=Mock(return_value={}),
        repair_nvr_channel_links=Mock(),
        resolve_nvr_link=Mock(return_value=(None, 0)),
    ),
)
sys.modules.setdefault("app.services.onvif_service", types.SimpleNamespace(OnvifCamera=object))
sys.modules.setdefault("app.utils.gb28181_source", types.SimpleNamespace(GB28181_SOURCE_PREFIX="gb28181://"))
sys.modules.setdefault(
    "app.utils.ip_utils",
    types.SimpleNamespace(
        IpReachabilityMonitor=Mock(return_value=types.SimpleNamespace(is_online=Mock(return_value=True))),
        resolve_ipv4_for_stream_urls=Mock(return_value=None),
    ),
)
sys.modules["models"] = types.SimpleNamespace(
    Device=object,
    DeviceDetectionRegion=object,
    DeviceDirectory=object,
    DeviceTrackSession=object,
    DeviceTrackPoint=object,
    StreamForwardTask=object,
    db=types.SimpleNamespace(session=Mock()),
)

from app.services import camera_service


class CameraAccessStateSummaryTest(unittest.TestCase):

    def test_camera_dict_includes_unified_access_state(self):
        camera = types.SimpleNamespace(
            id="cam-001",
            name="Gate Camera",
            source="rtsp://10.0.0.8/live",
            rtmp_stream="",
            http_stream="",
            ai_rtmp_stream="",
            ai_http_stream="",
            enable_forward=True,
            stream=None,
            ip=None,
            port=None,
            username=None,
            mac=None,
            manufacturer="yFeiEye",
            model="Camera-yFeiEye",
            firmware_version=None,
            serial_number=None,
            hardware_id=None,
            support_move=None,
            support_zoom=None,
            directory_id=None,
            rtsp_direct=None,
            channel_online=None,
            connection_status=None,
            longitude=None,
            latitude=None,
            altitude=None,
            address=None,
            location_source=None,
            location_updated_at=None,
            heading=None,
        )
        summary = {
            "state": "play_ready",
            "reason_code": "webrtc_probe_ok",
            "reason_message": "WebRTC playback probe passed",
            "play_ready": True,
            "ai_ready": False,
        }

        with patch("app.services.camera_service.get_device_access_summary", return_value=summary):
            payload = camera_service._to_dict(camera)

        self.assertEqual(summary, payload["access_state"])


if __name__ == "__main__":
    unittest.main()
