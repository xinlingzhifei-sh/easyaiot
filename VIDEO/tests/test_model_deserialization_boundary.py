import importlib.util
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest.mock import Mock, patch


VIDEO_ROOT = Path(__file__).resolve().parents[1]


def _load_frame_validator(torch_module, yolo_factory):
    path = (
        VIDEO_ROOT
        / 'services'
        / 'frame_extractor_service'
        / 'app'
        / 'utils'
        / 'yolo_validator.py'
    )
    spec = importlib.util.spec_from_file_location('frame_extractor_yolo_validator_test', path)
    module = importlib.util.module_from_spec(spec)
    with patch.dict(
        sys.modules,
        {
            'torch': torch_module,
            'ultralytics': types.SimpleNamespace(YOLO=yolo_factory),
        },
    ):
        spec.loader.exec_module(module)
    return module


def _load_frame_onnx_inference():
    path = (
        VIDEO_ROOT
        / 'services'
        / 'frame_extractor_service'
        / 'app'
        / 'utils'
        / 'onnx_inference.py'
    )
    spec = importlib.util.spec_from_file_location('frame_extractor_onnx_inference_test', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class ModelDeserializationBoundaryTest(unittest.TestCase):

    def test_frame_extractor_rejects_pickle_models_before_loader_calls(self):
        torch_load = Mock()
        yolo = Mock()
        validator = _load_frame_validator(
            types.SimpleNamespace(load=torch_load),
            yolo,
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = Path(temp_dir) / 'untrusted.pt'
            model_path.write_bytes(b'not-a-model')

            with self.assertRaisesRegex(ValueError, 'ONNX'):
                validator.validate_yolo_model(str(model_path))

        torch_load.assert_not_called()
        yolo.assert_not_called()

    def test_frame_extractor_filename_alone_cannot_determine_model_version(self):
        class NeutralModel:
            model = object()
            overrides = {}
            task = None

            @staticmethod
            def info():
                return 'model loaded without version metadata'

        validator = _load_frame_validator(
            types.SimpleNamespace(load=Mock()),
            Mock(return_value=NeutralModel()),
        )
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = Path(temp_dir) / 'yolo11-unverified.onnx'
            model_path.write_bytes(b'placeholder')

            version, method = validator.validate_yolo_model(str(model_path))

        self.assertIsNone(version)
        self.assertIn('版本无法确定', method)

    def test_frame_extractor_onnx_metadata_never_loads_sibling_pt(self):
        onnx_inference = _load_frame_onnx_inference()
        yolo = Mock(side_effect=AssertionError('must not load sibling .pt'))
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = Path(temp_dir) / 'detector.onnx'
            model_path.write_bytes(b'onnx')
            model_path.with_suffix('.pt').write_bytes(b'untrusted')

            with (
                patch.object(onnx_inference, 'ort', None),
                patch.dict(sys.modules, {'ultralytics': types.SimpleNamespace(YOLO=yolo)}),
            ):
                self.assertIsNone(
                    onnx_inference.get_classes_from_onnx_model(str(model_path))
                )

        yolo.assert_not_called()

    def test_video_onnx_metadata_never_loads_sibling_pt(self):
        from app.utils import onnx_inference

        yolo = Mock(side_effect=AssertionError('must not load sibling .pt'))
        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = Path(temp_dir) / 'detector.onnx'
            model_path.write_bytes(b'onnx')
            model_path.with_suffix('.pt').write_bytes(b'untrusted')

            with (
                patch.object(onnx_inference, 'ort', None),
                patch.dict(sys.modules, {'ultralytics': types.SimpleNamespace(YOLO=yolo)}),
            ):
                self.assertIsNone(
                    onnx_inference.get_classes_from_onnx_model(str(model_path))
                )

        yolo.assert_not_called()


if __name__ == '__main__':
    unittest.main()
