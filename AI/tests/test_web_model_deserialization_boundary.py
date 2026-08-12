import ast
from pathlib import Path


AI_ROOT = Path(__file__).resolve().parents[1]


def _source(relative_path: str) -> str:
    return (AI_ROOT / relative_path).read_text(encoding='utf-8')


def _called_names(relative_path: str) -> list[str]:
    tree = ast.parse(_source(relative_path))
    names = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        if isinstance(node.func, ast.Name):
            names.append(node.func.id)
        elif isinstance(node.func, ast.Attribute):
            names.append(node.func.attr)
    return names


def test_export_worker_never_deserializes_model_weights():
    source = _source('app/blueprints/export.py')

    assert 'YOLO' not in _called_names('app/blueprints/export.py')
    assert 'TrainTask.minio_model_path' not in source
    assert 'require_web_safe_model_reference' in source
    assert 'model_record.onnx_model_path' in source


def test_onnx_metadata_readers_never_call_yolo():
    for relative_path in (
        'app/utils/onnx_inference.py',
        'services/ai_service/app/utils/onnx_inference.py',
    ):
        assert 'YOLO' not in _called_names(relative_path)


def test_training_entrypoints_enforce_official_base_model_allowlist():
    for relative_path in (
        'app/blueprints/train.py',
        'app/blueprints/plate.py',
    ):
        source = _source(relative_path)
        assert 'require_official_pretrained_model' in source


def test_model_crud_enforces_web_safe_references():
    source = _source('app/blueprints/model.py')

    assert source.count('require_web_safe_model_reference') >= 3


def test_user_inference_only_resolves_onnx_models():
    source = _source('app/services/inference_service.py')

    assert 'allow_official_pretrained: bool = False' in source
    assert 'require_official_pretrained_model(os.path.basename(model_path))' in source
    assert "model_exts = ('.onnx',)" in source
    assert 'TrainTask.minio_model_path' not in source
    assert source.count('require_web_safe_model_reference') >= 3


def test_pose_loader_allows_only_onnx_or_builtin_pose_weights():
    inference_source = _source('app/utils/pose_inference.py')
    service_source = _source('app/services/pose_service.py')
    blueprint_source = _source('app/blueprints/pose.py')

    assert 'require_web_safe_model_reference' in inference_source
    assert 'require_official_pretrained_model' in inference_source
    assert '_official_pose_model_path' in service_source
    assert "m.onnx_model_path or ''" in blueprint_source
    assert "'model_file_path': m.onnx_model_path" in blueprint_source


def test_web_deploy_service_only_selects_onnx_artifacts():
    source = _source('app/services/deploy_service.py')

    assert 'def _resolve_deployable_model_path' in source
    assert source.count('_resolve_deployable_model_path(model)') >= 2
    assert 'require_web_safe_model_reference' in source
    assert 'model.model_path or model.onnx_model_path' not in source


def test_auto_label_only_reuses_completed_server_training_checkpoints():
    strategy_source = _source('app/services/auto_label_strategy.py')
    blueprint_source = _source('app/blueprints/auto_label.py')

    assert 'TrainTask.minio_model_path == model.model_path' in strategy_source
    assert "TrainTask.status == 'completed'" in strategy_source
    assert 'return bool(model.onnx_model_path)' in blueprint_source
