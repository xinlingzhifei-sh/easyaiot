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

def validate_yolo_model(model_path: str) -> Tuple[Optional[str], str]:
    """
    验证YOLO模型版本，接受 yolov8、yolov11 或 yolov26
    
    Args:
        model_path: 模型文件路径
        
    Returns:
        (版本字符串, 检测方法) - 如果版本为 yolov8、yolov11 或 yolov26，返回版本字符串；否则返回 None
        
    Raises:
        FileNotFoundError: 模型文件不存在
        ImportError: 未安装ultralytics库
        Exception: 无法判断版本或其他错误
    """
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"模型文件不存在: {model_path}")
    if os.path.splitext(model_path)[1].lower() != '.onnx':
        raise ValueError(
            'Web/帧提取进程仅允许验证 ONNX 模型；.pt/.pth 必须在隔离环境转换后再导入'
        )
    
    if YOLO is None:
        raise ImportError("未安装ultralytics库，请先安装: pip install ultralytics")
    
    try:
        # 加载模型
        model = YOLO(model_path)
        
        # 方法1: 检查模型信息字符串（注意：model.info()可能会打印信息但不返回包含版本号的字符串）
        try:
            model_info = str(model.info()).lower()
            if 'yolo26' in model_info or 'yolo 26' in model_info:
                return 'yolov26', "ultralytics库"
            elif 'yolo11' in model_info or 'yolo 11' in model_info:
                return 'yolov11', "ultralytics库"
            elif 'yolo8' in model_info or 'yolo 8' in model_info or 'yolov8' in model_info:
                return 'yolov8', "ultralytics库"
        except Exception:
            pass
        
        # 方法2: 检查模型类名
        try:
            model_type = str(type(model.model)).lower()
            if 'yolo26' in model_type:
                return 'yolov26', "ultralytics库（类名）"
            elif 'yolo11' in model_type:
                return 'yolov11', "ultralytics库（类名）"
            elif 'yolo8' in model_type or 'yolov8' in model_type:
                return 'yolov8', "ultralytics库（类名）"
        except Exception:
            pass
        
        # 方法3: 检查模型架构名称
        try:
            if hasattr(model.model, 'yaml') and model.model.yaml:
                yaml_str = str(model.model.yaml).lower()
                if 'yolo26' in yaml_str:
                    return 'yolov26', "ultralytics库（yaml）"
                elif 'yolo11' in yaml_str:
                    return 'yolov11', "ultralytics库（yaml）"
                elif 'yolo8' in yaml_str or 'yolov8' in yaml_str:
                    return 'yolov8', "ultralytics库（yaml）"
        except Exception:
            pass
        
        # 方法4: 检查模型的metadata（如果存在）
        try:
            if hasattr(model, 'overrides') and model.overrides:
                overrides_str = str(model.overrides).lower()
                if 'yolo26' in overrides_str:
                    return 'yolov26', "ultralytics库（metadata）"
                elif 'yolo11' in overrides_str:
                    return 'yolov11', "ultralytics库（metadata）"
                elif 'yolo8' in overrides_str or 'yolov8' in overrides_str:
                    return 'yolov8', "ultralytics库（metadata）"
        except Exception:
            pass
        
        # 方法5: 检查模型的任务类型和架构
        try:
            if hasattr(model.model, 'names'):
                # 尝试通过模型结构判断
                model_str = str(model.model).lower()
                if 'yolo26' in model_str:
                    return 'yolov26', "ultralytics库（架构）"
                elif 'yolo11' in model_str:
                    return 'yolov11', "ultralytics库（架构）"
                elif 'yolo8' in model_str or 'yolov8' in model_str:
                    return 'yolov8', "ultralytics库（架构）"
        except Exception:
            pass
        
        # 方法6: 如果模型能成功加载且没有报错，尝试通过模型的实际结构判断
        try:
            # 尝试获取模型的任务类型
            task = getattr(model, 'task', None)
            if task:
                task_str = str(task).lower()
                if 'yolo26' in task_str:
                    return 'yolov26', "ultralytics库（任务类型）"
                elif 'yolo11' in task_str:
                    return 'yolov11', "ultralytics库（任务类型）"
                elif 'yolo8' in task_str or 'yolov8' in task_str:
                    return 'yolov8', "ultralytics库（任务类型）"
            
            # 尝试通过模型的实际层结构判断
            if hasattr(model.model, 'model'):
                inner_model = model.model.model
                if hasattr(inner_model, '__class__'):
                    class_name = str(inner_model.__class__).lower()
                    if 'yolo26' in class_name or 'yolo 26' in class_name:
                        return 'yolov26', "ultralytics库（内部模型类）"
                    elif 'yolo11' in class_name or 'yolo 11' in class_name:
                        return 'yolov11', "ultralytics库（内部模型类）"
                    elif 'yolo8' in class_name or 'yolov8' in class_name or 'yolo 8' in class_name:
                        return 'yolov8', "ultralytics库（内部模型类）"
        except Exception:
            pass
        
        return None, "ultralytics库（模型成功加载但版本无法确定）"
            
    except Exception as e:
        error_str = str(e).lower()
        
        # 检查是否是YOLOv5模型
        if 'yolov5' in error_str or 'yolo v5' in error_str or 'yolo5' in error_str:
            raise Exception(
                "检测到YOLOv5模型。该模型与 YOLOv8/YOLOv11/YOLOv26 不兼容。\n"
                "请使用 YOLOv8、YOLOv11 或 YOLOv26 模型，或使用最新版本的 ultralytics 包重新训练模型。"
            )
        
        # 检查是否是其他不支持的模型版本
        if 'not forwards compatible' in error_str or 'not compatible' in error_str:
            # 尝试提取模型版本信息
            detected_version = None
            if 'yolov3' in error_str or 'yolo v3' in error_str or 'yolo3' in error_str:
                detected_version = "YOLOv3"
            elif 'yolov4' in error_str or 'yolo v4' in error_str or 'yolo4' in error_str:
                detected_version = "YOLOv4"
            elif 'yolov6' in error_str or 'yolo v6' in error_str or 'yolo6' in error_str:
                detected_version = "YOLOv6"
            elif 'yolov7' in error_str or 'yolo v7' in error_str or 'yolo7' in error_str:
                detected_version = "YOLOv7"
            
            if detected_version:
                raise Exception(
                    f"检测到{detected_version}模型。该模型与 YOLOv8/YOLOv11/YOLOv26 不兼容。\n"
                    "请使用 YOLOv8、YOLOv11 或 YOLOv26 模型，或使用最新版本的 ultralytics 包重新训练模型。"
                )
        
        # 其他错误，抛出原始异常信息
        raise Exception(f"无法通过ultralytics库判断版本: {e}")
