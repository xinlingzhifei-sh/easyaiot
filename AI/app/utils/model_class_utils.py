"""
从 YOLO / ONNX 模型中提取类别标签，并在推理时解析所选标签。

@author reese
@email reese
"""
import json
import logging
import os
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)


def parse_class_names_json(value: Any) -> List[str]:
    """将数据库或请求中的类别字段解析为字符串列表。"""
    if not value:
        return []
    if isinstance(value, list):
        return [str(item) for item in value if str(item).strip()]
    if isinstance(value, str):
        try:
            parsed = json.loads(value)
            if isinstance(parsed, list):
                return [str(item) for item in parsed if str(item).strip()]
        except (json.JSONDecodeError, TypeError):
            pass
    return []


def dump_class_names_json(names: Optional[List[str]]) -> Optional[str]:
    if not names:
        return None
    cleaned = [str(name).strip() for name in names if str(name).strip()]
    return json.dumps(cleaned, ensure_ascii=False) if cleaned else None


def extract_class_names_from_model(model_path: str) -> List[str]:
    """
    从 ONNX 模型中提取类别名称列表（按 class id 顺序）。

    Web 请求链不得加载 .pt/.pth，避免触发 pickle 反序列化。
    """
    if not model_path or not os.path.exists(model_path):
        return []

    ext = os.path.splitext(model_path)[1].lower()
    if ext != '.onnx':
        logger.warning('拒绝在 Web 进程解析非 ONNX 模型类别: %s', model_path)
        return []

    try:
        from app.utils.onnx_inference import get_classes_from_onnx_model
        classes_dict = get_classes_from_onnx_model(model_path) or {}
        if not classes_dict:
            return []
        return [classes_dict[i] for i in sorted(classes_dict.keys())]
    except Exception as exc:
        logger.warning('提取模型类别失败: %s, error=%s', model_path, exc)
    return []


def resolve_class_ids_from_names(
    names_dict: Dict[int, str],
    selected_names: Optional[List[str]],
) -> Optional[List[int]]:
    """
    将所选类别名称转换为模型 class id 列表。
    未指定 selected_names 时返回 None，表示不过滤。
    """
    if not selected_names:
        return None

    normalized = [str(name).strip() for name in selected_names if str(name).strip()]
    if not normalized:
        return None

    name_to_id = {str(name): int(class_id) for class_id, name in names_dict.items()}
    class_ids: List[int] = []
    for name in normalized:
        if name in name_to_id:
            class_ids.append(name_to_id[name])
            continue
        for class_id, model_name in names_dict.items():
            if str(model_name) == name:
                class_ids.append(int(class_id))
                break

    return sorted(set(class_ids)) if class_ids else None
