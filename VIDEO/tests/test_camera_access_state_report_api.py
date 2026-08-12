import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CAMERA_BLUEPRINT = ROOT / "app" / "blueprints" / "camera.py"


class CameraAccessStateReportApiTest(unittest.TestCase):

    def test_camera_blueprint_exposes_play_state_report_route(self):
        source = CAMERA_BLUEPRINT.read_text(encoding="utf-8")

        self.assertIn("/device/<string:device_id>/access-state/play", source)
        self.assertIn("report_device_play_state", source)
        self.assertIn("record_device_play_ready", source)
        self.assertIn("record_device_play_error", source)
        self.assertIn("_check_login(request)", source)
        ready_branch = source.split("if ready:", 1)[1].split("else:", 1)[0]
        self.assertIn("reason_code=data.get('reason_code') or data.get('reasonCode')", ready_branch)
        self.assertIn("reason_message=data.get('reason_message') or data.get('reasonMessage')", ready_branch)
        self.assertIn("source_event=data.get('source_event') or data.get('sourceEvent')", ready_branch)


if __name__ == "__main__":
    unittest.main()
