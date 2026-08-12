import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
STREAM_FORWARD_BLUEPRINT = ROOT / "app" / "blueprints" / "stream_forward.py"


class StreamForwardEdgeReconcileApiTest(unittest.TestCase):

    def test_stream_forward_blueprint_exposes_edge_reconcile_route(self):
        source = STREAM_FORWARD_BLUEPRINT.read_text(encoding="utf-8")

        self.assertIn("/device/<string:device_id>/reconcile-edge-task", source)
        self.assertIn("reconcile_edge_task", source)
        self.assertIn("reconcile_edge_rtsp_forward_command", source)
        self.assertIn("edge_node_id", source)
        self.assertIn("timeout_seconds", source)
        self.assertIn("max_attempts", source)


if __name__ == "__main__":
    unittest.main()
