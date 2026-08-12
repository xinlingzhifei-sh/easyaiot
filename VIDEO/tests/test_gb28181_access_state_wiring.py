import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest.mock import Mock


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


class Gb28181AccessStateWiringTest(unittest.TestCase):

    def setUp(self):
        self._original_modules = {
            name: sys.modules.get(name)
            for name in (
                "models",
                "app.services.camera_service",
                "app.services.device_access_state_service",
                "app.utils.gb28181_source",
                "app.services.gb28181_sync_service",
            )
        }

        class FakeDevice:
            query = Mock()

            def __init__(self, **kwargs):
                self.__dict__.update(kwargs)

        FakeDevice.query.get.return_value = None
        self.fake_device = FakeDevice
        self.db = types.SimpleNamespace(session=Mock())
        self.record_state = Mock()

        sys.modules["models"] = types.SimpleNamespace(Device=FakeDevice, db=self.db)
        sys.modules["app.utils.gb28181_source"] = types.SimpleNamespace(
            GB28181_SOURCE_PREFIX="gb28181://",
            _candidate_bases=Mock(return_value=[]),
        )
        sys.modules["app.services.camera_service"] = types.SimpleNamespace(
            get_or_create_default_directory=Mock(return_value=types.SimpleNamespace(id=9)),
            gb28181_device_stream_urls=Mock(return_value=(
                "",
                "",
                "rtmp://media.example.com/ai/gb28181_sip-001_ch-001",
                "https://media.example.com/ai/gb28181_sip-001_ch-001.flv",
            )),
            sync_unassigned_devices_to_default_directory=Mock(),
        )
        sys.modules["app.services.device_access_state_service"] = types.SimpleNamespace(
            record_device_access_event=self.record_state,
        )
        sys.modules.pop("app.services.gb28181_sync_service", None)
        self.service = importlib.import_module("app.services.gb28181_sync_service")

    def tearDown(self):
        sys.modules.pop("app.services.gb28181_sync_service", None)
        for name, module in self._original_modules.items():
            if module is None:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = module

    def test_sync_payload_records_gb28181_registered_state(self):
        stats = self.service.sync_gb28181_channels_from_payload([
            {
                "sipDeviceId": "sip-001",
                "channelId": "ch-001",
                "name": "Gate camera",
            },
        ])

        self.assertEqual(1, stats["created"])
        self.record_state.assert_called_once_with(
            device_id="gb28181_sip-001_ch-001",
            protocol="gb28181",
            state="registered",
            reason_code="gb28181_channel_synced",
            reason_message="GB28181 channel synced from WVP",
            source_event="gb28181.channel.sync",
            stream_id="sip-001/ch-001",
        )


if __name__ == "__main__":
    unittest.main()
