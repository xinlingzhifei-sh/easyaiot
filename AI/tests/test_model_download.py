import importlib
import sys
import types
from pathlib import Path
from types import SimpleNamespace

from flask import Flask


def import_model_blueprint(monkeypatch, downloaded_content: bytes):
    class FakeModelService:
        calls = []

        @staticmethod
        def download_from_minio(bucket_name, object_key, file_path):
            FakeModelService.calls.append((bucket_name, object_key))
            Path(file_path).write_bytes(downloaded_content)
            return True, None

    minio_service = types.ModuleType('app.services.minio_service')
    minio_service.ModelService = FakeModelService
    monkeypatch.setitem(sys.modules, 'app.services.minio_service', minio_service)

    yolo_validator = types.ModuleType('app.utils.yolo_validator')
    yolo_validator.validate_yolo_model = lambda *_args, **_kwargs: (None, '')
    monkeypatch.setitem(sys.modules, 'app.utils.yolo_validator', yolo_validator)

    image_utils = types.ModuleType('app.utils.image_utils')
    image_utils.download_default_model_image = lambda *_args, **_kwargs: True
    monkeypatch.setitem(sys.modules, 'app.utils.image_utils', image_utils)

    model_class_utils = types.ModuleType('app.utils.model_class_utils')
    model_class_utils.dump_class_names_json = lambda value: value
    model_class_utils.extract_class_names_from_model = lambda *_args, **_kwargs: []
    model_class_utils.parse_class_names_json = lambda value: value or []
    monkeypatch.setitem(sys.modules, 'app.utils.model_class_utils', model_class_utils)

    class FakeQuery:
        def get_or_404(self, model_id):
            assert model_id == 42
            return SimpleNamespace(
                id=42,
                name='demo',
                version='2.0.0',
                model_path='/api/v1/buckets/models/objects/download?prefix=models/demo.pt',
                onnx_model_path=None,
            )

    class FakeModel:
        query = FakeQuery()

    db_models = types.ModuleType('db_models')
    db_models.db = SimpleNamespace(session=SimpleNamespace(add=lambda *_args: None, commit=lambda: None))
    db_models.Model = FakeModel
    db_models.InferenceTask = object
    monkeypatch.setitem(sys.modules, 'db_models', db_models)

    monkeypatch.delitem(sys.modules, 'app.blueprints.model', raising=False)
    module = importlib.import_module('app.blueprints.model')
    return module, FakeModelService


def test_model_download_returns_attachment_from_minio(monkeypatch):
    downloaded_content = b'model-bytes'
    model_module, fake_service = import_model_blueprint(monkeypatch, downloaded_content)

    app = Flask(__name__)
    app.register_blueprint(model_module.model_bp, url_prefix='/model')

    response = app.test_client().get('/model/42/download')

    assert response.status_code == 200
    assert response.data == downloaded_content
    assert fake_service.calls == [('models', 'models/demo.pt')]
    assert 'attachment' in response.headers['Content-Disposition']
    assert 'demo_2.0.0.pt' in response.headers['Content-Disposition']
