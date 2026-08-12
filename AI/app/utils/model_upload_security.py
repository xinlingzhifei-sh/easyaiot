"""模型上传格式安全边界。"""

import os
import re
from urllib.parse import parse_qs, unquote, urlparse


ALLOWED_MODEL_UPLOAD_EXTENSIONS = frozenset({'.onnx'})
OFFICIAL_PRETRAINED_MODEL_PATTERN = re.compile(
    r'^yolo(?:v8|11|26)[nslmx](?:-pose)?\.pt$',
    re.IGNORECASE,
)


def require_safe_model_upload_extension(filename: str) -> str:
    """仅允许不会触发 Python pickle 反序列化的模型格式。"""
    extension = os.path.splitext(str(filename or ''))[1].lower()
    if extension not in ALLOWED_MODEL_UPLOAD_EXTENSIONS:
        raise ValueError(
            'Web 上传仅支持 .onnx；.pt/.pth 必须在无网络、低权限的隔离环境转换后再导入'
        )
    return extension


def require_web_safe_model_reference(reference: str) -> str:
    """Web 请求链只允许引用不会触发 Pickle 反序列化的 ONNX 模型。"""
    value = str(reference or '').strip()
    parsed = urlparse(value)
    query = parse_qs(parsed.query, keep_blank_values=True)
    prefix_values = query.get('prefix')
    if prefix_values and len(prefix_values) != 1:
        raise ValueError(
            'Web 进程仅允许 ONNX 模型；PyTorch 权重必须在隔离环境转换'
        )

    object_path = prefix_values[0] if prefix_values else parsed.path
    if (
        not object_path
        or parsed.fragment
        or unquote(object_path) != object_path
        or '?' in object_path
        or '#' in object_path
        or any(ord(char) < 32 or ord(char) == 127 for char in object_path)
    ):
        raise ValueError(
            'Web 进程仅允许 ONNX 模型；PyTorch 权重必须在隔离环境转换'
        )

    extension = os.path.splitext(object_path)[1].lower()
    if extension not in ALLOWED_MODEL_UPLOAD_EXTENSIONS:
        raise ValueError(
            'Web 进程仅允许 ONNX 模型；PyTorch 权重必须在隔离环境转换'
        )
    return value


def require_official_pretrained_model(model_name: str) -> str:
    """训练入口仅允许 Ultralytics 官方基础模型名称，不接受任意本地或远程权重。"""
    value = str(model_name or '').strip()
    if OFFICIAL_PRETRAINED_MODEL_PATTERN.fullmatch(value) is None:
        raise ValueError(
            '训练仅允许官方基础模型名称；自定义 .pt 必须在无网络、低权限的隔离环境转换'
        )
    return value
