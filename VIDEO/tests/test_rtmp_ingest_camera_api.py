import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAMERA_BLUEPRINT = ROOT / "app" / "blueprints" / "camera.py"


class RtmpIngestCameraApiTest(unittest.TestCase):

    def test_camera_blueprint_exposes_signed_ingest_url_and_rotation_routes(self):
        source = CAMERA_BLUEPRINT.read_text(encoding="utf-8")

        self.assertIn("/device/<string:device_id>/rtmp-ingest-url", source)
        self.assertIn("/device/<string:device_id>/rtmp-ingest-token/rotate", source)
        self.assertIn("issue_rtmp_ingest_url", source)
        self.assertIn("rotate_rtmp_ingest_token", source)
        self.assertIn("_tenant_id_from_request", source)
        self.assertIn("_check_login(request)", source)


if __name__ == "__main__":
    unittest.main()
