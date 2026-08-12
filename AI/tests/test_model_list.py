import importlib
import sys
import types
from datetime import datetime
from pathlib import Path
from types import SimpleNamespace

from flask import Flask


AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))


def import_model_blueprint_for_list(monkeypatch, items):
    minio_service = types.ModuleType('app.services.minio_service')
    minio_service.ModelService = object
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

    class FakePagination:
        def __init__(self):
            self.items = items
            self.total = len(items)

    class FakeQuery:
        def order_by(self, *_args):
            return self

        def paginate(self, page, per_page, error_out=False):
            assert page == 1
            assert per_page == 18
            assert error_out is False
            return FakePagination()

    class FakeModel:
        query = FakeQuery()
        created_at = SimpleNamespace(desc=lambda: object())

    db_models = types.ModuleType('db_models')
    db_models.db = SimpleNamespace(session=SimpleNamespace(add=lambda *_args: None, commit=lambda: None))
    db_models.Model = FakeModel
    db_models.InferenceTask = object
    monkeypatch.setitem(sys.modules, 'db_models', db_models)

    monkeypatch.delitem(sys.modules, 'app.blueprints.model', raising=False)
    return importlib.import_module('app.blueprints.model')


def test_model_list_tolerates_models_without_class_name_fields(monkeypatch):
    legacy_model = SimpleNamespace(
        id=7,
        name='legacy-yolo',
        version='1.0.0',
        description='old model row',
        status=0,
        created_at=datetime(2026, 6, 9, 13, 30, 0),
        updated_at=None,
        image_url=None,
        model_path='/api/v1/buckets/models/objects/download?prefix=yolo/demo.pt',
        onnx_model_path=None,
        torchscript_model_path=None,
        tensorrt_model_path=None,
        openvino_model_path=None,
    )
    model_module = import_model_blueprint_for_list(monkeypatch, [legacy_model])

    app = Flask(__name__)
    app.register_blueprint(model_module.model_bp, url_prefix='/model')

    response = app.test_client().get('/model/list?pageNo=1&pageSize=18')

    assert response.status_code == 200
    payload = response.get_json()
    assert payload['code'] == 0
    assert payload['total'] == 1
    assert payload['data'][0]['class_names'] == []
    assert payload['data'][0]['selected_class_names'] == []
