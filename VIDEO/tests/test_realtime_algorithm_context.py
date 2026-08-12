import ast
from pathlib import Path
import threading
from types import SimpleNamespace
import typing
import unittest


VIDEO_ROOT = Path(__file__).resolve().parents[1]


class RealtimeAlgorithmContextTest(unittest.TestCase):
    def test_face_matching_threshold_uses_script_db_session(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertNotIn("FaceLibrary.query.get", source)
        self.assertIn("db_session.get(FaceLibrary", source)

    def test_face_capture_queue_accepts_realtime_source_event(self):
        queue_source = (
            VIDEO_ROOT / "app" / "utils" / "face_capture_queue_service.py"
        ).read_text(encoding="utf-8")
        realtime_source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertIn("source_event: Optional[str] = None", queue_source)
        self.assertIn("task['source_event'] = source_event", queue_source)
        self.assertIn("source_event=source_event", realtime_source)

    def test_gb28181_realtime_uses_cached_input_stream_when_play_fails(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertIn("resolve_gb28181_source(device.source", source)
        self.assertIn("gb28181_device_stream_urls(device.id)", source)
        self.assertIn("using generated GB28181 HTTP stream", source)
        self.assertIn("for cached_attr in ('http_stream', 'rtmp_stream')", source)
        self.assertIn("using cached {cached_attr}", source)
        self.assertIn("prefer_hevc_http_ts_for_ffmpeg", source)
        self.assertIn("rtsp_url = _normalize_gb28181_opencv_input_url(rtsp_url)", source)

    def test_realtime_quality_params_do_not_reference_undefined_source_fps(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")
        loaded_names = {
            node.id
            for node in ast.walk(ast.parse(source))
            if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load)
        }

        self.assertNotIn("SOURCE_FPS", loaded_names)
        self.assertIn("AI_OUTPUT_FPS", loaded_names)

    def test_overlay_sampling_defaults_to_task_extract_interval(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertIn(
            "OVERLAY_EXTRACT_INTERVAL = int(os.getenv('OVERLAY_EXTRACT_INTERVAL', str(EXTRACT_INTERVAL)))",
            source,
        )
        self.assertIn(
            "else _runtime_extract_interval",
            source,
        )

    def test_periodic_config_reload_reuses_unchanged_models(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertIn("def _reuse_loaded_yolo_models(", source)
        self.assertIn("reused_models = _reuse_loaded_yolo_models(model_ids)", source)
        self.assertIn("模型配置未变化，复用已加载的", source)

        helper_node = next(
            node
            for node in ast.parse(source).body
            if isinstance(node, ast.FunctionDef)
            and node.name == "_reuse_loaded_yolo_models"
        )
        helper_module = ast.fix_missing_locations(
            ast.Module(body=[helper_node], type_ignores=[])
        )
        model_1 = object()
        model_6 = object()
        namespace = {
            "Any": typing.Any,
            "Dict": typing.Dict,
            "List": typing.List,
            "Optional": typing.Optional,
            "yolo_models": {6: model_6, 1: model_1},
        }
        exec(compile(helper_module, "<model-reuse-helper>", "exec"), namespace)
        reuse = namespace["_reuse_loaded_yolo_models"]

        reordered = reuse([1, 6])
        self.assertEqual(list(reordered), [1, 6])
        self.assertIs(reordered[1], model_1)
        self.assertIs(reordered[6], model_6)
        self.assertIsNone(reuse([1, 5]))

    def test_detection_worker_idle_backoff_is_bounded(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertNotIn("2 ** idle_count", source)
        self.assertEqual(source.count("2 ** min(idle_count, 5)"), 2)

    def test_realtime_detection_uses_model_allowed_class_filter(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertIn("resolve_model_allowed_class_names", source)
        self.assertIn("yolo_model_allowed_classes[model_id]", source)
        self.assertIn("allowed_class_names = yolo_model_allowed_classes.get(model_id)", source)
        self.assertIn("allowed_class_names=allowed_class_names", source)
        self.assertIn("allowed_classes_include_person", source)
        self.assertIn("run_tiled_model_detection", source)

    def test_realtime_ai_pusher_rejects_stale_output_frames(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")

        self.assertIn("AI_OUTPUT_FRAME_STALE_SEC", source)
        self.assertIn("'updated_at': current_timestamp", source)
        self.assertIn("_is_output_frame_info_fresh", source)
        self.assertIn("AI输出帧过期", source)

    def test_stopped_realtime_tasks_cleanup_orphan_algorithm_processes(self):
        source = (
            VIDEO_ROOT / "app" / "services" / "algorithm_task_launcher_service.py"
        ).read_text(encoding="utf-8")

        self.assertIn("AlgorithmTask.run_status == 'stopped'", source)
        self.assertIn("cleanup_orphaned_processes(task_id)", source)
        self.assertIn("active_task_ids", source)
        self.assertIn("starting_task_ids", source)

    def test_algorithm_processes_receive_narrowed_alert_ingest_credentials(self):
        source = (
            VIDEO_ROOT / "app" / "services" / "algorithm_task_launcher_service.py"
        ).read_text(encoding="utf-8")

        self.assertIn("build_alert_ingest_process_env", source)
        self.assertIn("env.update(build_alert_ingest_process_env())", source)

    def test_realtime_heartbeat_statistics_keep_persisted_baselines(self):
        source = (
            VIDEO_ROOT / "services" / "realtime_algorithm_service" / "run_deploy.py"
        ).read_text(encoding="utf-8")
        helper_names = {
            "_initialize_heartbeat_statistics",
            "_record_realtime_detections",
            "_heartbeat_statistics",
        }
        helper_nodes = [
            node
            for node in ast.parse(source).body
            if isinstance(node, ast.FunctionDef) and node.name in helper_names
        ]
        self.assertEqual({node.name for node in helper_nodes}, helper_names)

        namespace = {
            "_MAX_HEARTBEAT_COUNTER": 2_147_483_647,
            "_heartbeat_stats_lock": threading.Lock(),
            "_heartbeat_stats_initialized": False,
            "_heartbeat_total_frames_base": 0,
            "_heartbeat_total_detections_base": 0,
            "_heartbeat_session_detections": 0,
            "frame_counts": {"camera-a": 12, "camera-b": 8},
        }
        helper_module = ast.fix_missing_locations(
            ast.Module(body=helper_nodes, type_ignores=[])
        )
        exec(compile(helper_module, "<heartbeat-statistics>", "exec"), namespace)

        initialize = namespace["_initialize_heartbeat_statistics"]
        initialize(SimpleNamespace(total_frames=100, total_detections=7))
        namespace["_record_realtime_detections"]([
            {"is_cached": False},
            {"is_cached": True},
            {"is_cached": False},
        ])
        self.assertEqual(
            namespace["_heartbeat_statistics"](),
            {"total_frames": 120, "total_detections": 9},
        )

        initialize(SimpleNamespace(total_frames=999, total_detections=999))
        self.assertEqual(
            namespace["_heartbeat_statistics"](),
            {"total_frames": 120, "total_detections": 9},
        )

    def test_local_algorithm_process_does_not_inherit_full_service_keyring(self):
        source = (
            VIDEO_ROOT / "app" / "services" / "algorithm_task_daemon.py"
        ).read_text(encoding="utf-8")

        self.assertIn("build_alert_ingest_process_env", source)
        self.assertIn("'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS'", source)
        self.assertIn("env.pop(key, None)", source)
        self.assertIn("env.update(build_alert_ingest_process_env())", source)


if __name__ == "__main__":
    unittest.main()
