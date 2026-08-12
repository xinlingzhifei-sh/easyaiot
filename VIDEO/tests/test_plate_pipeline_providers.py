import os
from types import SimpleNamespace
import unittest
from unittest.mock import patch

from app.utils.plate_recognition import pipeline


class _FakeSession:
    calls = []

    def __init__(self, model_path, providers):
        self.calls.append((model_path, providers))

    def get_inputs(self):
        return [SimpleNamespace(name="images")]


class PlatePipelineProviderTest(unittest.TestCase):
    def setUp(self):
        _FakeSession.calls.clear()

    def test_cpu_deployment_does_not_request_cuda_provider(self):
        with (
            patch.dict(os.environ, {"USE_GPU": "False"}),
            patch.object(
                pipeline.ort,
                "get_available_providers",
                return_value=["CUDAExecutionProvider", "CPUExecutionProvider"],
            ),
            patch.object(pipeline.ort, "InferenceSession", _FakeSession),
        ):
            pipeline.PlatePipeline("detect.onnx", "rec.onnx")

        self.assertEqual(
            _FakeSession.calls,
            [
                ("detect.onnx", ["CPUExecutionProvider"]),
                ("rec.onnx", ["CPUExecutionProvider"]),
            ],
        )


if __name__ == "__main__":
    unittest.main()
