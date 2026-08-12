from datetime import datetime
from types import SimpleNamespace
from unittest import TestCase

from app.blueprints.algorithm_task import _apply_realtime_heartbeat_statistics


class RealtimeHeartbeatStatisticsTest(TestCase):
    def test_advances_counters_and_processing_timestamps(self):
        task = SimpleNamespace(
            total_frames=10,
            total_detections=2,
            last_process_time=None,
            last_success_time=None,
        )
        processed_at = datetime(2026, 7, 24, 6, 0, 0)

        advanced = _apply_realtime_heartbeat_statistics(
            task,
            {"total_frames": 25, "total_detections": 4},
            now=processed_at,
        )

        self.assertTrue(advanced)
        self.assertEqual(task.total_frames, 25)
        self.assertEqual(task.total_detections, 4)
        self.assertEqual(task.last_process_time, processed_at)
        self.assertEqual(task.last_success_time, processed_at)

    def test_does_not_regress_counters_from_a_stale_process(self):
        original_time = datetime(2026, 7, 24, 5, 0, 0)
        task = SimpleNamespace(
            total_frames=100,
            total_detections=20,
            last_process_time=original_time,
            last_success_time=original_time,
        )

        advanced = _apply_realtime_heartbeat_statistics(
            task,
            {"total_frames": 90, "total_detections": 19},
        )

        self.assertFalse(advanced)
        self.assertEqual(task.total_frames, 100)
        self.assertEqual(task.total_detections, 20)
        self.assertEqual(task.last_process_time, original_time)

    def test_rejects_negative_counters(self):
        task = SimpleNamespace(
            total_frames=0,
            total_detections=0,
            last_process_time=None,
            last_success_time=None,
        )

        with self.assertRaisesRegex(ValueError, "total_frames"):
            _apply_realtime_heartbeat_statistics(task, {"total_frames": -1})
