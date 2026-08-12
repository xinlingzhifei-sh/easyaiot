"""
YOLO模型版本验证工具
使用ultralytics库判断YOLO模型是版本8还是版本11

@author reese
@email reese
"""
import os
from typing import Optional, Tuple

try:
    from ultralytics import YOLO
except ImportError:
    YOLO = None

_YOLOV5_INCOMPATIBLE_MSG = (
    "检测到 YOLOv5 或基于 YOLOv5 训练框架（models.yolo）导出的权重，"
    "与平台要求的 YOLOv8/YOLOv11/YOLOv26 不兼容。\n"
    "请使用 ultralytics 重新训练/导出 .pt，或先转为 ONNX 后再上传。"
)

_UNTRUSTED_PT_REJECTION_MSG = (
    "Web 进程禁止加载 .pt/.pth 模型，因为该格式可能触发 Python pickle 反序列化。"
    "请在无网络、低权限、资源受限的隔离环境转换为 ONNX 后再导入。"
)


def _is_yolov5_style_checkpoint(text: str) -> bool:
    lower = str(text).lower()
    return (
        'models.yolo' in lower
        or 'yolov5' in lower
        or 'yolo v5' in lower
        or 'yolo5' in lower
    )


def _infer_version_from_checkpoint_blob(text: str) -> Optional[str]:
    lower = str(text).lower()
    if 'yolo26' in lower or 'yolo 26' in lower:
        return 'yolov26'
    if 'yolo11' in lower or 'yolo 11' in lower:
        return 'yolov11'
    if 'yolo8' in lower or 'yolo 8' in lower or 'yolov8' in lower:
        return 'yolov8'
    return None


def _load_torch_checkpoint(model_path: str):
    del model_path
    raise ValueError(_UNTRUSTED_PT_REJECTION_MSG)


def _inspect_checkpoint(checkpoint) -> Tuple[Optional[str], Optional[str], Optional[str]]:
    """返回 (version, method, reject_reason)。"""
    if not isinstance(checkpoint, dict):
        blob = str(checkpoint)
        if _is_yolov5_style_checkpoint(blob):
            return None, None, 'yolov5'
        version = _infer_version_from_checkpoint_blob(blob)
        if version:
            return version, 'torch模型元数据', None
        return None, None, None

    blob = str(checkpoint)
    if _is_yolov5_style_checkpoint(blob):
        return None, None, 'yolov5'

    version = _infer_version_from_checkpoint_blob(blob)
    if version:
        return version, 'torch模型元数据', None

    model_data = checkpoint.get('model')
    if model_data is not None:
        model_blob = str(model_data)
        if _is_yolov5_style_checkpoint(model_blob):
            return None, None, 'yolov5'
        version = _infer_version_from_checkpoint_blob(model_blob)
        if version:
            return version, 'torch模型元数据', None

    return None, None, None


def _raise_yolov5_incompatible() -> None:
    raise Exception(_YOLOV5_INCOMPATIBLE_MSG)


def _raise_from_yolo_load_error(exc: Exception) -> None:
    error_str = str(exc).lower()

    if _is_yolov5_style_checkpoint(error_str) or "no module named 'models" in error_str:
        _raise_yolov5_incompatible()

    if 'yolov5' in error_str or 'yolo v5' in error_str or 'yolo5' in error_str:
        _raise_yolov5_incompatible()

    if 'not forwards compatible' in error_str or 'not compatible' in error_str:
        detected_version = None
        if 'yolov3' in error_str or 'yolo v3' in error_str or 'yolo3' in error_str:
            detected_version = 'YOLOv3'
        elif 'yolov4' in error_str or 'yolo v4' in error_str or 'yolo4' in error_str:
            detected_version = 'YOLOv4'
        elif 'yolov6' in error_str or 'yolo v6' in error_str or 'yolo6' in error_str:
            detected_version = 'YOLOv6'
        elif 'yolov7' in error_str or 'yolo v7' in error_str or 'yolo7' in error_str:
            detected_version = 'YOLOv7'

        if detected_version:
            raise Exception(
                f"检测到{detected_version}模型。该模型与 YOLOv8/YOLOv11/YOLOv26 不兼容。\n"
                "请使用 YOLOv8、YOLOv11 或 YOLOv26 模型，或使用最新版本的 ultralytics 包重新训练模型。"
            )

    raise Exception(f"无法通过ultralytics库判断版本: {exc}")


def validate_yolo_model(
    model_path: str,
    original_filename: Optional[str] = None,
) -> Tuple[Optional[str], str]:
    """
    验证YOLO模型版本，接受 yolov8、yolov11 或 yolov26

    Args:
        model_path: 模型文件路径
        original_filename: 上传时的原始文件名（仅用于拒绝不可信扩展名）

    Returns:
        (版本字符串, 检测方法) - 如果版本为 yolov8、yolov11 或 yolov26，返回版本字符串；否则返回 None

    Raises:
        FileNotFoundError: 模型文件不存在
        ImportError: 未安装ultralytics库
        Exception: 无法判断版本或其他错误
    """
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"模型文件不存在: {model_path}")

    model_extension = os.path.splitext(model_path)[1].lower()
    original_extension = os.path.splitext(original_filename or '')[1].lower()
    if model_extension in {'.pt', '.pth'} or original_extension in {'.pt', '.pth'}:
        raise ValueError(_UNTRUSTED_PT_REJECTION_MSG)

    if YOLO is None:
        raise ImportError("未安装ultralytics库，请先安装: pip install ultralytics")

    try:
        model = YOLO(model_path)

        try:
            model_info = str(model.info()).lower()
            if 'yolo26' in model_info or 'yolo 26' in model_info:
                return 'yolov26', 'ultralytics库'
            if 'yolo11' in model_info or 'yolo 11' in model_info:
                return 'yolov11', 'ultralytics库'
            if 'yolo8' in model_info or 'yolo 8' in model_info or 'yolov8' in model_info:
                return 'yolov8', 'ultralytics库'
        except Exception:
            pass

        try:
            model_type = str(type(model.model)).lower()
            if 'yolo26' in model_type:
                return 'yolov26', 'ultralytics库（类名）'
            if 'yolo11' in model_type:
                return 'yolov11', 'ultralytics库（类名）'
            if 'yolo8' in model_type or 'yolov8' in model_type:
                return 'yolov8', 'ultralytics库（类名）'
        except Exception:
            pass

        try:
            if hasattr(model.model, 'yaml') and model.model.yaml:
                yaml_str = str(model.model.yaml).lower()
                if 'yolo26' in yaml_str:
                    return 'yolov26', 'ultralytics库（yaml）'
                if 'yolo11' in yaml_str:
                    return 'yolov11', 'ultralytics库（yaml）'
                if 'yolo8' in yaml_str or 'yolov8' in yaml_str:
                    return 'yolov8', 'ultralytics库（yaml）'
        except Exception:
            pass

        try:
            if hasattr(model, 'overrides') and model.overrides:
                overrides_str = str(model.overrides).lower()
                if 'yolo26' in overrides_str:
                    return 'yolov26', 'ultralytics库（metadata）'
                if 'yolo11' in overrides_str:
                    return 'yolov11', 'ultralytics库（metadata）'
                if 'yolo8' in overrides_str or 'yolov8' in overrides_str:
                    return 'yolov8', 'ultralytics库（metadata）'
        except Exception:
            pass

        try:
            if hasattr(model.model, 'names'):
                model_str = str(model.model).lower()
                if 'yolo26' in model_str:
                    return 'yolov26', 'ultralytics库（架构）'
                if 'yolo11' in model_str:
                    return 'yolov11', 'ultralytics库（架构）'
                if 'yolo8' in model_str or 'yolov8' in model_str:
                    return 'yolov8', 'ultralytics库（架构）'
        except Exception:
            pass

        try:
            task = getattr(model, 'task', None)
            if task:
                task_str = str(task).lower()
                if 'yolo26' in task_str:
                    return 'yolov26', 'ultralytics库（任务类型）'
                if 'yolo11' in task_str:
                    return 'yolov11', 'ultralytics库（任务类型）'
                if 'yolo8' in task_str or 'yolov8' in task_str:
                    return 'yolov8', 'ultralytics库（任务类型）'

            if hasattr(model.model, 'model'):
                inner_model = model.model.model
                if hasattr(inner_model, '__class__'):
                    class_name = str(inner_model.__class__).lower()
                    if 'yolo26' in class_name or 'yolo 26' in class_name:
                        return 'yolov26', 'ultralytics库（内部模型类）'
                    if 'yolo11' in class_name or 'yolo 11' in class_name:
                        return 'yolov11', 'ultralytics库（内部模型类）'
                    if 'yolo8' in class_name or 'yolov8' in class_name or 'yolo 8' in class_name:
                        return 'yolov8', 'ultralytics库（内部模型类）'
        except Exception:
            pass

        return None, 'ultralytics库（模型成功加载但版本无法确定）'

    except Exception as e:
        _raise_from_yolo_load_error(e)
