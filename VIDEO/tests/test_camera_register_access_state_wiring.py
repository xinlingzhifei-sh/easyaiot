import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAMERA_SERVICE = ROOT / "app" / "services" / "camera_service.py"


class CameraRegisterAccessStateWiringTest(unittest.TestCase):

    def test_direct_device_register_records_unified_registered_state(self):
        source = CAMERA_SERVICE.read_text(encoding="utf-8")

        self.assertIn("_record_registered_access_state_for_camera", source)
        self.assertIn("_access_protocol_for_source", source)
        self.assertIn("record_device_access_event", source)
        self.assertIn("source_event=\"device.register\"", source)
        self.assertIn("state=\"registered\"", source)


if __name__ == "__main__":
    unittest.main()
