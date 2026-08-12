import importlib
import sys
import types
import unittest
from pathlib import Path

import app.services as app_services


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


class FakeColumn:
    def isnot(self, _value):
        return self


class FakeQuery:
    def __init__(self, rows):
        self.rows = rows

    def filter(self, *_args, **_kwargs):
        return self

    def all(self):
        return self.rows


class FakeDeviceModel:
    source = FakeColumn()
    query = FakeQuery([])


class FakeSession:
    def __init__(self):
        self.commit_count = 0

    def commit(self):
        self.commit_count += 1


class FakeDb:
    def __init__(self):
        self.session = FakeSession()


EXPECTED_AI_HTTP = (
    "https://eye.yfeiai.com/ai/"
    "gb28181_44010200493432381460_34020000001320000001.flv"
)
EXPECTED_AI_RTMP = "rtmp://127.0.0.1:1935/ai/gb28181_demo"


def fake_gb28181_device_stream_urls(_device_id):
    return (
        "",
        "https://eye.yfeiai.com/rtp/"
        "44010200493432381460_34020000001320000001.live.flv"
        "?originTypeStr=rtp_push&videoCodec=H265",
        EXPECTED_AI_RTMP,
        EXPECTED_AI_HTTP,
    )


_missing = object()
_previous_modules = {
    name: sys.modules.get(name)
    for name in (
        "models",
        "app.services.camera_service",
        "app.services.gb28181_sync_service",
    )
}
_previous_service_attributes = {
    name: getattr(app_services, name, _missing)
    for name in ('camera_service', 'gb28181_sync_service')
}
sys.modules["models"] = types.SimpleNamespace(Device=FakeDeviceModel, db=FakeDb())
sys.modules["app.services.camera_service"] = types.SimpleNamespace(
    get_or_create_default_directory=lambda: types.SimpleNamespace(id=1),
    gb28181_device_stream_urls=fake_gb28181_device_stream_urls,
    sync_unassigned_devices_to_default_directory=lambda: 0,
)
sys.modules.pop("app.services.gb28181_sync_service", None)
gb28181_sync_service = importlib.import_module("app.services.gb28181_sync_service")
for _module_name, _previous_module in _previous_modules.items():
    if _previous_module is None:
        sys.modules.pop(_module_name, None)
    else:
        sys.modules[_module_name] = _previous_module
for _attribute_name, _previous_attribute in _previous_service_attributes.items():
    if _previous_attribute is _missing:
        try:
            delattr(app_services, _attribute_name)
        except AttributeError:
            pass
    else:
        setattr(app_services, _attribute_name, _previous_attribute)


class Gb28181SyncServiceTest(unittest.TestCase):
    def setUp(self):
        gb28181_sync_service.db.session.commit_count = 0

    def test_backfill_rewrites_stale_ai_stream_urls(self):
        device = types.SimpleNamespace(
            id="gb28181_44010200493432381460_34020000001320000001",
            source="gb28181://44010200493432381460/34020000001320000001",
            ai_rtmp_stream="rtmp://192.168.0.88:1935/ai/gb28181_demo",
            ai_http_stream=(
                "https://eye.yfeiai.com/rtp/"
                "44010200493432381460_34020000001320000001.live.flv"
                "?originTypeStr=rtp_push&videoCodec=H265"
            ),
            http_stream="",
        )
        gb28181_sync_service.Device.query = FakeQuery([device])

        updated = gb28181_sync_service.backfill_gb28181_ai_stream_urls()

        self.assertEqual(updated, 1)
        self.assertIn("/rtp/", device.http_stream)
        self.assertEqual(device.ai_rtmp_stream, EXPECTED_AI_RTMP)
        self.assertEqual(device.ai_http_stream, EXPECTED_AI_HTTP)
        self.assertEqual(gb28181_sync_service.db.session.commit_count, 1)


if __name__ == "__main__":
    unittest.main()
