import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAMERA_BLUEPRINT = ROOT / "app" / "blueprints" / "camera.py"


class CameraAccessStateHealthApiTest(unittest.TestCase):

    def test_camera_blueprint_exposes_access_state_health_route(self):
        source = CAMERA_BLUEPRINT.read_text(encoding="utf-8")

        self.assertIn("/access-state/health", source)
        self.assertIn("get_device_access_health", source)
        self.assertIn("get_device_access_health_snapshot", source)
        self.assertIn("stale_after_seconds", source)
        self.assertIn("_check_login(request)", source)


if __name__ == "__main__":
    unittest.main()
