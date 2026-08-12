import importlib.util
import sys
import types
from pathlib import Path

from app.utils.model_class_utils import extract_class_names_from_model


def test_web_class_extraction_refuses_pt_without_importing_ultralytics(
    tmp_path: Path,
    monkeypatch,
):
    pt_path = tmp_path / 'untrusted.pt'
    pt_path.write_bytes(b'not-a-checkpoint')

    def fail_import(*_args, **_kwargs):
        raise AssertionError('ultralytics must not be imported for .pt input')

    monkeypatch.setattr('builtins.__import__', fail_import)

    assert extract_class_names_from_model(str(pt_path)) == []


class _EmptyMetadataSession:
    def get_modelmeta(self):
        return types.SimpleNamespace(custom_metadata_map={})


def _assert_onnx_class_lookup_never_loads_adjacent_pt(
    module,
    tmp_path: Path,
    monkeypatch,
):
    onnx_path = tmp_path / 'model.onnx'
    onnx_path.write_bytes(b'not-a-real-onnx')
    (tmp_path / 'model.pt').write_bytes(b'untrusted-checkpoint')

    loaded_paths = []
    fake_ultralytics = types.ModuleType('ultralytics')
    fake_ultralytics.YOLO = lambda path: (
        loaded_paths.append(path) or types.SimpleNamespace(names={0: 'unsafe'})
    )
    monkeypatch.setitem(sys.modules, 'ultralytics', fake_ultralytics)
    monkeypatch.setattr(
        module,
        'ort',
        types.SimpleNamespace(
            InferenceSession=lambda *_args, **_kwargs: _EmptyMetadataSession()
        ),
    )

    assert module.get_classes_from_onnx_model(str(onnx_path)) is None
    assert loaded_paths == []


def test_primary_onnx_class_lookup_never_loads_adjacent_pt(tmp_path, monkeypatch):
    from app.utils import onnx_inference

    _assert_onnx_class_lookup_never_loads_adjacent_pt(
        onnx_inference,
        tmp_path,
        monkeypatch,
    )


def test_ai_service_copy_never_loads_adjacent_pt(tmp_path, monkeypatch):
    module_path = (
        Path(__file__).resolve().parents[1]
        / 'services'
        / 'ai_service'
        / 'app'
        / 'utils'
        / 'onnx_inference.py'
    )
    spec = importlib.util.spec_from_file_location(
        'ai_service_onnx_inference_security_test',
        module_path,
    )
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    _assert_onnx_class_lookup_never_loads_adjacent_pt(
        module,
        tmp_path,
        monkeypatch,
    )
