import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAMERA_BLUEPRINT = ROOT / "app" / "blueprints" / "camera.py"


class CameraAccessStateEventsApiTest(unittest.TestCase):

    def test_camera_blueprint_exposes_access_state_events_route(self):
        source = CAMERA_BLUEPRINT.read_text(encoding="utf-8")

        self.assertIn("/device/<string:device_id>/access-state/events", source)
        self.assertIn("get_device_access_events", source)
        self.assertIn("list_device_access_events", source)
        self.assertIn("_check_login(request)", source)
        self.assertIn("request.args.get('limit'", source)
        self.assertIn("request.args.get('protocol'", source)
        self.assertIn("_tenant_id_from_request(request)", source)


if __name__ == "__main__":
    unittest.main()
