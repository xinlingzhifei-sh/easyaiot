"""yolo_validator 单元测试。"""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from app.utils.yolo_validator import (
    _inspect_checkpoint,
    _is_yolov5_style_checkpoint,
    _load_torch_checkpoint,
    validate_yolo_model,
)


class TestYoloValidatorHelpers(unittest.TestCase):
    def test_detect_yolov5_checkpoint_blob(self):
        self.assertTrue(_is_yolov5_style_checkpoint("models.yolo.Model"))
        self.assertTrue(_is_yolov5_style_checkpoint("/home/ubuntu/算法模型 best.pt yolov5"))

    def test_inspect_checkpoint_rejects_yolov5(self):
        ckpt = {'model': 'models.yolo.Detect', 'train_args': '/home/ubuntu/算法模型'}
        version, method, reject = _inspect_checkpoint(ckpt)
        self.assertIsNone(version)
        self.assertEqual(reject, 'yolov5')

    def test_web_validator_never_deserializes_torch_checkpoint(self):
        with self.assertRaises(ValueError) as ctx:
            _load_torch_checkpoint('/fake/path.pt')
        self.assertIn('禁止加载', str(ctx.exception))

    def test_filename_alone_cannot_determine_model_version(self):
        class NeutralModel:
            model = object()
            overrides = {}
            task = None

            @staticmethod
            def info():
                return 'model loaded without version metadata'

        with tempfile.TemporaryDirectory() as temp_dir:
            model_path = Path(temp_dir) / 'yolo11-unverified.onnx'
            model_path.write_bytes(b'placeholder')
            with patch('app.utils.yolo_validator.YOLO', return_value=NeutralModel()):
                version, method = validate_yolo_model(str(model_path))

        self.assertIsNone(version)
        self.assertIn('版本无法确定', method)


if __name__ == '__main__':
    unittest.main()
