import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAMERA_BLUEPRINT = ROOT / "app" / "blueprints" / "camera.py"


class CameraWebrtcNatConfigApiTest(unittest.TestCase):

    def test_camera_blueprint_exposes_webrtc_nat_config_route(self):
        source = CAMERA_BLUEPRINT.read_text(encoding="utf-8")

        self.assertIn("/webrtc/nat-config", source)
        self.assertIn("get_webrtc_nat_config", source)
        self.assertIn("build_webrtc_nat_config", source)
        self.assertIn("_check_login(request)", source)


if __name__ == "__main__":
    unittest.main()
