import ast
import os
from pathlib import Path
from threading import Lock
import unittest
from unittest.mock import patch


RUN_DEPLOY_PATH = (
    Path(__file__).resolve().parents[1]
    / "services"
    / "realtime_algorithm_service"
    / "run_deploy.py"
)


def parse_run_deploy():
    return ast.parse(RUN_DEPLOY_PATH.read_text(encoding="utf-8"))


class RealtimeStreamParamsTest(unittest.TestCase):
    def test_manual_profile_uses_ai_output_fps(self):
        module = parse_run_deploy()
        function = next(
            node
            for node in module.body
            if isinstance(node, ast.FunctionDef)
            and node.name == "_get_effective_realtime_stream_params"
        )
        namespace = {
            "AUTO_QUALITY_ENABLED": False,
            "MANUAL_QUALITY_CONFIGURED": True,
            "AI_OUTPUT_FPS": 23,
            "TARGET_WIDTH": 1280,
            "TARGET_HEIGHT": 720,
            "FFMPEG_VIDEO_BITRATE": "3500k",
            "FFMPEG_GOP_SIZE": 46,
            "AUTO_QUALITY_LOCK_PROFILE": "",
            "QUALITY_PROFILE_PRESETS": {},
            "_quality_profile_lock": Lock(),
        }
        exec(
            compile(
                ast.Module(body=[function], type_ignores=[]),
                filename=str(RUN_DEPLOY_PATH),
                mode="exec",
            ),
            namespace,
        )

        result = namespace["_get_effective_realtime_stream_params"]()

        self.assertEqual(result, ("manual", 23, 1280, 720, "3500k", 46))

    def test_legacy_source_fps_configures_ai_output_fps(self):
        module = parse_run_deploy()
        assignment = next(
            node
            for node in module.body
            if isinstance(node, ast.Assign)
            and any(
                isinstance(target, ast.Name) and target.id == "AI_OUTPUT_FPS"
                for target in node.targets
            )
        )
        namespace = {"os": os}

        with patch.dict(os.environ, {"SOURCE_FPS": "17"}, clear=True):
            exec(
                compile(
                    ast.Module(body=[assignment], type_ignores=[]),
                    filename=str(RUN_DEPLOY_PATH),
                    mode="exec",
                ),
                namespace,
            )

        self.assertEqual(namespace["AI_OUTPUT_FPS"], 17)


if __name__ == "__main__":
    unittest.main()
