import importlib
import os
import sys
import types
import unittest
from pathlib import Path

import app.services as app_services


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

_previous_models = sys.modules.get("models")
_previous_service = sys.modules.get("app.services.stream_url_sync_service")
_missing = object()
_previous_service_attribute = getattr(
    app_services, "stream_url_sync_service", _missing)
sys.modules["models"] = types.SimpleNamespace(
    Device=object, StreamForwardTask=object, db=object())
sys.modules.pop("app.services.stream_url_sync_service", None)
stream_url_sync_service = importlib.import_module("app.services.stream_url_sync_service")
if _previous_models is None:
    sys.modules.pop("models", None)
else:
    sys.modules["models"] = _previous_models
if _previous_service is None:
    sys.modules.pop("app.services.stream_url_sync_service", None)
else:
    sys.modules["app.services.stream_url_sync_service"] = _previous_service
if _previous_service_attribute is _missing:
    try:
        delattr(app_services, "stream_url_sync_service")
    except AttributeError:
        pass
else:
    app_services.stream_url_sync_service = _previous_service_attribute

GB_DEVICE_ID = "gb28181_44010200493432381460_34020000001320000001"
GB_RAW_HTTP = (
    "https://eye.yfeiai.com/rtp/"
    "44010200493432381460_34020000001320000001.live.flv"
    "?originTypeStr=rtp_push&videoCodec=H265"
)
GB_AI_HTTP = f"https://eye.yfeiai.com/ai/{GB_DEVICE_ID}.flv"


class StreamUrlSyncServiceTest(unittest.TestCase):
    def tearDown(self):
        os.environ.pop("MEDIA_HTTP_PLAY_HOST", None)

    def test_build_stream_urls_uses_public_origin_without_srs_port(self):
        _, http_stream, _, ai_http_stream = stream_url_sync_service.build_stream_urls_for_host(
            "192.168.0.88",
            "gb28181_demo",
            http_play_host="https://eye.yfeiai.com",
        )

        self.assertEqual(http_stream, "https://eye.yfeiai.com/live/gb28181_demo.flv")
        self.assertEqual(ai_http_stream, "https://eye.yfeiai.com/ai/gb28181_demo.flv")

    def test_build_stream_urls_keeps_host_only_play_host_compatible(self):
        _, http_stream, _, ai_http_stream = stream_url_sync_service.build_stream_urls_for_host(
            "192.168.0.88",
            "gb28181_demo",
            tags={"srs_http_port": 18080},
            http_play_host="media.example.com",
        )

        self.assertEqual(http_stream, "http://media.example.com:18080/live/gb28181_demo.flv")
        self.assertEqual(ai_http_stream, "http://media.example.com:18080/ai/gb28181_demo.flv")

    def test_build_stream_urls_reads_public_origin_from_environment(self):
        os.environ["MEDIA_HTTP_PLAY_HOST"] = "https://eye.yfeiai.com/"

        _, http_stream, _, ai_http_stream = stream_url_sync_service.build_stream_urls_for_host(
            "192.168.0.88",
            "gb28181_demo",
        )

        self.assertEqual(http_stream, "https://eye.yfeiai.com/live/gb28181_demo.flv")
        self.assertEqual(ai_http_stream, "https://eye.yfeiai.com/ai/gb28181_demo.flv")

    def test_build_gb28181_zlm_http_flv_url_uses_public_rtp_route(self):
        os.environ["MEDIA_HTTP_PLAY_HOST"] = "https://eye.yfeiai.com/"

        url = stream_url_sync_service.build_gb28181_zlm_http_flv_url(
            GB_DEVICE_ID,
        )

        self.assertEqual(url, GB_RAW_HTTP)

    def test_resolve_gb_device_keeps_raw_and_ai_streams_separate(self):
        os.environ["MEDIA_HTTP_PLAY_HOST"] = "https://eye.yfeiai.com/"
        device = types.SimpleNamespace(
            id=GB_DEVICE_ID,
            source="gb28181://44010200493432381460/34020000001320000001",
            rtmp_stream="",
            http_stream="",
            ai_rtmp_stream=f"rtmp://192.168.0.88:1935/ai/{GB_DEVICE_ID}",
            ai_http_stream=GB_AI_HTTP,
        )

        rtmp_stream, http_stream, ai_rtmp_stream, ai_http_stream = (
            stream_url_sync_service.resolve_device_stream_urls(device)
        )

        self.assertEqual(rtmp_stream, "")
        self.assertEqual(http_stream, GB_RAW_HTTP)
        self.assertEqual(ai_rtmp_stream, f"rtmp://192.168.0.88:1935/ai/{GB_DEVICE_ID}")
        self.assertEqual(ai_http_stream, GB_AI_HTTP)


if __name__ == "__main__":
    unittest.main()
