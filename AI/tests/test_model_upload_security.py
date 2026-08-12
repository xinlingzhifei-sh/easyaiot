import pytest

from app.utils import model_upload_security
from app.utils.model_upload_security import require_safe_model_upload_extension


@pytest.mark.parametrize('filename', ['model.pt', 'model.PTH', 'model.pkl', 'model'])
def test_pickle_backed_model_uploads_are_rejected(filename):
    with pytest.raises(ValueError, match='仅支持 \\.onnx'):
        require_safe_model_upload_extension(filename)


def test_onnx_model_upload_is_allowed():
    assert require_safe_model_upload_extension('detector.ONNX') == '.onnx'


@pytest.mark.parametrize(
    'reference',
    [
        '/api/v1/buckets/models/objects/download?prefix=models/evil.pt',
        'models/evil.PTH',
        'models/evil.pkl',
        'models/evil.pickle',
    ],
)
def test_web_model_references_reject_pickle_backed_artifacts(reference):
    guard = getattr(model_upload_security, 'require_web_safe_model_reference', None)
    assert callable(guard), '缺少 Web 模型引用安全边界'

    with pytest.raises(ValueError, match='Web 进程仅允许 ONNX'):
        guard(reference)


def test_web_model_reference_allows_onnx_object_url():
    guard = getattr(model_upload_security, 'require_web_safe_model_reference', None)
    assert callable(guard), '缺少 Web 模型引用安全边界'

    reference = '/api/v1/buckets/models/objects/download?prefix=models/safe.onnx'
    assert guard(reference) == reference


@pytest.mark.parametrize(
    'reference',
    [
        '/api/v1/buckets/models/objects/download?prefix=models/evil.pt%3Fsafe.onnx',
        '/api/v1/buckets/models/objects/download?prefix=models/evil.pt%23safe.onnx',
        '/api/v1/buckets/models/objects/download?prefix=models%252Fevil%252Ept%253Fsafe.onnx',
        '/api/v1/buckets/models/objects/download?prefix=models/evil.pt%00.onnx',
    ],
)
def test_web_model_references_reject_ambiguous_encoded_paths(reference):
    with pytest.raises(ValueError, match='Web 进程仅允许 ONNX'):
        model_upload_security.require_web_safe_model_reference(reference)


def test_web_model_reference_allows_single_encoded_object_separator():
    reference = '/api/v1/buckets/models/objects/download?prefix=models%2Fsafe.onnx'
    assert model_upload_security.require_web_safe_model_reference(reference) == reference


@pytest.mark.parametrize(
    'model_name',
    [
        '/api/v1/buckets/models/objects/download?prefix=evil.pt',
        'C:/models/evil.pt',
        '../evil.pt',
        'custom.pt',
        'yolov8s-best.pt',
    ],
)
def test_training_rejects_untrusted_pretrained_weight_references(model_name):
    guard = getattr(model_upload_security, 'require_official_pretrained_model', None)
    assert callable(guard), '缺少训练基础模型白名单'

    with pytest.raises(ValueError, match='仅允许官方基础模型名称'):
        guard(model_name)


@pytest.mark.parametrize(
    'model_name',
    ['yolov8n.pt', 'yolo11l.pt', 'yolo26n.pt', 'yolo26n-pose.pt'],
)
def test_training_allows_known_official_pretrained_model_names(model_name):
    guard = getattr(model_upload_security, 'require_official_pretrained_model', None)
    assert callable(guard), '缺少训练基础模型白名单'

    assert guard(model_name) == model_name
