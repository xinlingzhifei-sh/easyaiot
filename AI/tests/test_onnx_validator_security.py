import importlib.util
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.utils import onnx_validator


AI_ROOT = Path(__file__).resolve().parents[1]


def _load_ai_service_validator():
    path = AI_ROOT / 'services' / 'ai_service' / 'app' / 'utils' / 'yolo_validator.py'
    spec = importlib.util.spec_from_file_location('ai_service_yolo_validator_test', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def test_filename_alone_cannot_validate_onnx_model(tmp_path, monkeypatch):
    model_path = tmp_path / 'yolo11-unverified.onnx'
    model_path.write_bytes(b'not-a-validated-model')

    def fail_ultralytics_load(*_args, **_kwargs):
        raise RuntimeError('model metadata unavailable')

    monkeypatch.setattr(onnx_validator, 'YOLO', fail_ultralytics_load)
    monkeypatch.setattr(
        onnx_validator,
        'onnx',
        SimpleNamespace(load=lambda _path: object()),
    )

    with pytest.raises(Exception, match='无法.*判断ONNX模型版本'):
        onnx_validator.validate_onnx_model(str(model_path))


def test_successful_load_without_metadata_does_not_guess_model_version(
    tmp_path,
    monkeypatch,
):
    model_path = tmp_path / 'unverified.onnx'
    model_path.write_bytes(b'placeholder')

    class NeutralModel:
        model = object()

        @staticmethod
        def info():
            return 'model loaded without version metadata'

    monkeypatch.setattr(
        onnx_validator,
        'YOLO',
        lambda _path, **_kwargs: NeutralModel(),
    )

    version, method = onnx_validator.validate_onnx_model(str(model_path))

    assert version is None
    assert '版本无法确定' in method


def test_ai_service_filename_alone_cannot_determine_model_version(
    tmp_path,
    monkeypatch,
):
    validator = _load_ai_service_validator()
    model_path = tmp_path / 'yolo11-unverified.onnx'
    model_path.write_bytes(b'placeholder')

    class NeutralModel:
        model = object()
        overrides = {}
        task = None

        @staticmethod
        def info():
            return 'model loaded without version metadata'

    monkeypatch.setattr(validator, 'YOLO', lambda _path: NeutralModel())

    version, method = validator.validate_yolo_model(str(model_path))

    assert version is None
    assert '版本无法确定' in method
