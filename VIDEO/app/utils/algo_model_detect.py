"""
算法任务统一检测入口：Ultralytics(.pt) 与 ONNXInference(.onnx)
"""
import json
import threading
from collections.abc import Iterable as IterableABC
from typing import Any, Callable, Dict, Iterable, List, Optional, Set

from app.utils.onnx_inference import ONNXInference


# Ultralytics YOLO is not thread-safe during first-time predictor/fuse setup.
_MODEL_INFER_LOCKS: Dict[int, threading.Lock] = {}
_MODEL_INFER_LOCKS_GUARD = threading.Lock()


def _get_model_infer_lock(model: Any) -> threading.Lock:
    key = id(model)
    with _MODEL_INFER_LOCKS_GUARD:
        lock = _MODEL_INFER_LOCKS.get(key)
        if lock is None:
            lock = threading.Lock()
            _MODEL_INFER_LOCKS[key] = lock
        return lock


MODEL_NAME_CLASS_FALLBACKS = [
    (("人脸", "face"), ("face", "person_face", "human_face", "人脸", "脸")),
    (
        ("安全帽", "helmet", "hardhat"),
        (
            "helmet",
            "hardhat",
            "hard_hat",
            "safety_helmet",
            "head",
            "no_helmet",
            "without_helmet",
            "no_hardhat",
            "安全帽",
            "未戴安全帽",
            "头盔",
        ),
    ),
    (
        ("反光衣", "反光背心", "vest"),
        (
            "vest",
            "reflective_vest",
            "safety_vest",
            "no_vest",
            "反光衣",
            "反光背心",
        ),
    ),
    (("睡岗", "sleep"), ("sleep", "sleeping", "drowsy", "睡岗", "睡觉")),
    (("火焰", "fire", "flame"), ("fire", "flame", "火焰", "明火")),
    (("吸烟", "抽烟", "smoking"), ("smoke", "smoking", "cigarette", "吸烟", "抽烟")),
    (
        ("打电话", "phone", "call"),
        ("phone", "cell_phone", "mobile_phone", "calling", "call", "打电话", "手机"),
    ),
    (("口罩", "mask"), ("mask", "face_mask", "no_mask", "口罩", "未戴口罩")),
    (("跌倒", "摔倒", "fall"), ("fall", "fallen", "fall_down", "person_fall", "跌倒", "摔倒")),
    (("车牌", "plate"), ("plate", "license_plate", "car_plate", "车牌")),
    (("人模型", "行人", "人形", "人体", "person", "pedestrian"), ("person", "human", "pedestrian", "人", "行人", "人形", "人体")),
]
PERSON_CLASS_ALIASES = ("person", "human", "pedestrian", "人", "行人", "人形", "人体")


def is_onnx_detector(model: Any) -> bool:
    return isinstance(model, ONNXInference)


def is_yolo26_model(
    model: Any,
    *,
    model_path: str = '',
    model_id: Optional[int] = None,
) -> bool:
    """识别 YOLO26 模型，兼容旧版 ultralytics 未暴露 end2end 属性的情况。"""
    if model_id == -3:
        return True
    path_lower = str(model_path or '').lower()
    if 'yolo26' in path_lower:
        return True
    if is_onnx_detector(model):
        return False
    overrides = getattr(model, 'overrides', None) or {}
    if 'yolo26' in str(overrides.get('model', '')).lower():
        return True
    inner = getattr(model, 'model', None)
    if inner is not None:
        if bool(getattr(inner, 'end2end', False)):
            return True
        yaml_cfg = getattr(inner, 'yaml', None)
        if isinstance(yaml_cfg, dict):
            yaml_file = str(yaml_cfg.get('yaml_file', '')).lower()
            if 'yolo26' in yaml_file or yaml_cfg.get('end2end'):
                return True
    return False


def is_end2end_ultralytics_model(model: Any) -> bool:
    """YOLO26 等 end2end 模型内置 NMS，推理参数需与普通 YOLO 区分。"""
    if is_yolo26_model(model):
        return True
    if is_onnx_detector(model):
        return False
    inner = getattr(model, 'model', None)
    if inner is not None and bool(getattr(inner, 'end2end', False)):
        return True
    yaml_cfg = getattr(inner, 'yaml', None) if inner is not None else None
    return bool(isinstance(yaml_cfg, dict) and yaml_cfg.get('end2end'))


def warmup_model_detection(
    model: Any,
    *,
    infer_device: str = 'cpu',
    imgsz: int = 640,
    conf: float = 0.25,
    iou: float = 0.45,
) -> None:
    """单线程预热 predictor，避免多 worker 首次推理时出现竞态。"""
    import numpy as np

    size = max(32, int(imgsz))
    dummy = np.zeros((size, size, 3), dtype=np.uint8)
    run_model_detection(
        model,
        dummy,
        conf=conf,
        iou=iou,
        imgsz=imgsz,
        infer_device=infer_device,
    )


def normalize_class_name(class_name: Any) -> str:
    return str(class_name or "").strip().lower().replace("-", "_").replace(" ", "_")


def _iter_class_names(raw: Any) -> Iterable[str]:
    if not raw:
        return []
    if isinstance(raw, str):
        stripped = raw.strip()
        if not stripped:
            return []
        try:
            return _iter_class_names(json.loads(stripped))
        except Exception:
            return [part.strip() for part in stripped.split(",") if part.strip()]
    if isinstance(raw, dict):
        return [str(value) for value in raw.values() if value]
    if isinstance(raw, (list, tuple, set)) or isinstance(raw, IterableABC):
        names = []
        for item in raw:
            if isinstance(item, dict):
                for key in ("name", "label", "class_name", "className"):
                    if item.get(key):
                        names.append(str(item[key]))
                        break
            elif item:
                names.append(str(item))
        return names
    return []


def _normalize_class_names(class_names: Iterable[str]) -> Set[str]:
    return {normalize_class_name(name) for name in class_names if normalize_class_name(name)}


def resolve_model_allowed_class_names(model_info: Dict[str, Any]) -> Optional[Set[str]]:
    for key in (
        "selectedClassNames",
        "selected_class_names",
        "classNames",
        "class_names",
        "labels",
        "classes",
    ):
        allowed = _normalize_class_names(_iter_class_names(model_info.get(key)))
        if allowed:
            return allowed

    model_name = str(model_info.get("name") or "")
    normalized_model_name = normalize_class_name(model_name)
    for keywords, class_names in MODEL_NAME_CLASS_FALLBACKS:
        if any(normalize_class_name(keyword) in normalized_model_name for keyword in keywords):
            return _normalize_class_names(class_names)
    return None


def class_name_allowed(class_name: str, allowed_class_names: Optional[Iterable[str]]) -> bool:
    if not allowed_class_names:
        return True
    allowed = set(allowed_class_names)
    return normalize_class_name(class_name) in allowed


def filter_detections_by_allowed_classes(
    detections: List[Dict[str, Any]],
    allowed_class_names: Optional[Iterable[str]],
) -> List[Dict[str, Any]]:
    if not allowed_class_names:
        return detections
    allowed = set(allowed_class_names)
    return [
        detection
        for detection in detections
        if class_name_allowed(detection.get("class_name", ""), allowed)
    ]


def prefer_loaded_person_classes(
    configured_allowed_class_names: Optional[Iterable[str]],
    loaded_class_names: Optional[Iterable[str]],
) -> Optional[Set[str]]:
    loaded_allowed = _normalize_class_names(_iter_class_names(loaded_class_names))
    person_aliases = _normalize_class_names(PERSON_CLASS_ALIASES)
    if configured_allowed_class_names:
        configured_allowed = set(configured_allowed_class_names)
        if loaded_allowed and loaded_allowed.issubset(person_aliases) and configured_allowed & person_aliases:
            return loaded_allowed
        return configured_allowed
    if loaded_allowed and loaded_allowed.issubset(person_aliases):
        return loaded_allowed
    return None


def allowed_classes_include_person(allowed_class_names: Optional[Iterable[str]]) -> bool:
    if not allowed_class_names:
        return False
    allowed = set(allowed_class_names)
    return bool(allowed & _normalize_class_names(PERSON_CLASS_ALIASES))


def is_person_class_name(class_name: Any) -> bool:
    return normalize_class_name(class_name) in _normalize_class_names(PERSON_CLASS_ALIASES)


def is_person_like_detection(
    detection: Dict[str, Any],
    frame_shape=None,
    *,
    min_confidence: float = 0.18,
    min_height_ratio: float = 0.04,
    min_area_ratio: float = 0.00005,
    min_width_height_ratio: float = 0.10,
    max_width_height_ratio: float = 0.95,
) -> bool:
    if not is_person_class_name(detection.get("class_name", "")):
        return True

    if float(detection.get("confidence", 0.0)) < float(min_confidence):
        return False

    bbox = detection.get("bbox") or []
    if len(bbox) != 4:
        return False
    x1, y1, x2, y2 = [float(value) for value in bbox]
    box_w = max(0.0, x2 - x1)
    box_h = max(0.0, y2 - y1)
    if box_w <= 0 or box_h <= 0:
        return False

    ratio = box_w / box_h
    if ratio < float(min_width_height_ratio) or ratio > float(max_width_height_ratio):
        return False

    if frame_shape is not None and len(frame_shape) >= 2:
        frame_h = max(1.0, float(frame_shape[0]))
        frame_w = max(1.0, float(frame_shape[1]))
        if box_h < frame_h * float(min_height_ratio):
            return False
        if (box_w * box_h) < frame_w * frame_h * float(min_area_ratio):
            return False

    return True


def filter_person_like_detections(
    detections: List[Dict[str, Any]],
    frame_shape=None,
    *,
    min_confidence: float = 0.18,
    min_height_ratio: float = 0.04,
    min_area_ratio: float = 0.00005,
    min_width_height_ratio: float = 0.10,
    max_width_height_ratio: float = 0.95,
) -> List[Dict[str, Any]]:
    return [
        detection
        for detection in detections
        if is_person_like_detection(
            detection,
            frame_shape,
            min_confidence=min_confidence,
            min_height_ratio=min_height_ratio,
            min_area_ratio=min_area_ratio,
            min_width_height_ratio=min_width_height_ratio,
            max_width_height_ratio=max_width_height_ratio,
        )
    ]


def iter_tiled_regions(frame_shape, columns: int, rows: int, overlap_ratio: float):
    height, width = frame_shape[:2]
    columns = max(1, int(columns))
    rows = max(1, int(rows))
    overlap_ratio = max(0.0, min(float(overlap_ratio), 0.45))
    if columns == 1 and rows == 1:
        return []

    regions = []
    for row in range(rows):
        base_y1 = int(row * height / rows)
        base_y2 = int((row + 1) * height / rows)
        pad_y = int((base_y2 - base_y1) * overlap_ratio / 2)
        y1 = max(0, base_y1 - pad_y)
        y2 = min(height, base_y2 + pad_y)
        for col in range(columns):
            base_x1 = int(col * width / columns)
            base_x2 = int((col + 1) * width / columns)
            pad_x = int((base_x2 - base_x1) * overlap_ratio / 2)
            x1 = max(0, base_x1 - pad_x)
            x2 = min(width, base_x2 + pad_x)
            if x2 > x1 and y2 > y1:
                regions.append((x1, y1, x2, y2))
    return regions


def _offset_detection(detection: Dict[str, Any], x_offset: int, y_offset: int) -> Dict[str, Any]:
    x1, y1, x2, y2 = detection["bbox"]
    adjusted = dict(detection)
    adjusted["bbox"] = [
        int(x1 + x_offset),
        int(y1 + y_offset),
        int(x2 + x_offset),
        int(y2 + y_offset),
    ]
    return adjusted


def _bbox_iou(first, second) -> float:
    ax1, ay1, ax2, ay2 = first
    bx1, by1, bx2, by2 = second
    inter_x1 = max(ax1, bx1)
    inter_y1 = max(ay1, by1)
    inter_x2 = min(ax2, bx2)
    inter_y2 = min(ay2, by2)
    inter_w = max(0, inter_x2 - inter_x1)
    inter_h = max(0, inter_y2 - inter_y1)
    inter_area = inter_w * inter_h
    first_area = max(0, ax2 - ax1) * max(0, ay2 - ay1)
    second_area = max(0, bx2 - bx1) * max(0, by2 - by1)
    union = first_area + second_area - inter_area
    return inter_area / union if union else 0.0


def dedupe_detections_by_iou(
    detections: List[Dict[str, Any]],
    iou_threshold: float = 0.5,
) -> List[Dict[str, Any]]:
    kept: List[Dict[str, Any]] = []
    for detection in sorted(detections, key=lambda det: float(det.get("confidence", 0.0)), reverse=True):
        class_name = normalize_class_name(detection.get("class_name", ""))
        if any(
            class_name == normalize_class_name(existing.get("class_name", ""))
            and _bbox_iou(detection.get("bbox", []), existing.get("bbox", [])) >= iou_threshold
            for existing in kept
        ):
            continue
        kept.append(detection)
    return kept


def run_tiled_model_detection(
    model: Any,
    frame,
    *,
    columns: int,
    rows: int,
    overlap_ratio: float,
    conf: float = 0.25,
    iou: float = 0.45,
    imgsz: int = 640,
    infer_device: str = 'cpu',
    should_keep: Optional[Callable[[str], bool]] = None,
    allowed_class_names: Optional[Iterable[str]] = None,
) -> List[Dict[str, Any]]:
    detections: List[Dict[str, Any]] = []
    for x1, y1, x2, y2 in iter_tiled_regions(frame.shape, columns, rows, overlap_ratio):
        crop = frame[y1:y2, x1:x2]
        crop_detections = run_model_detection(
            model,
            crop,
            conf=conf,
            iou=iou,
            imgsz=imgsz,
            infer_device=infer_device,
            should_keep=should_keep,
            allowed_class_names=allowed_class_names,
        )
        detections.extend(_offset_detection(detection, x1, y1) for detection in crop_detections)
    return dedupe_detections_by_iou(detections, iou_threshold=iou)


def run_model_detection(
    model: Any,
    frame,
    *,
    conf: float = 0.25,
    iou: float = 0.45,
    imgsz: int = 640,
    infer_device: str = 'cpu',
    should_keep: Optional[Callable[[str], bool]] = None,
    allowed_class_names: Optional[Iterable[str]] = None,
) -> List[Dict[str, Any]]:
    """对单帧执行检测，返回统一格式的检测列表。"""
    if is_onnx_detector(model):
        with _get_model_infer_lock(model):
            _, raw_detections = model.detect(frame, conf_threshold=conf, iou_threshold=iou, draw=False)
        detections = []
        for det in raw_detections:
            class_name = det['class_name']
            if not class_name_allowed(class_name, allowed_class_names):
                continue
            if should_keep and not should_keep(class_name):
                continue
            x1, y1, x2, y2 = det['bbox']
            detections.append({
                'class_id': int(det.get('class', 0)),
                'class_name': class_name,
                'confidence': float(det['confidence']),
                'bbox': [int(x1), int(y1), int(x2), int(y2)],
            })
        return detections

    predict_kwargs = dict(
        conf=conf,
        iou=iou,
        imgsz=imgsz,
        verbose=False,
        half=False,
        device=infer_device,
    )
    if is_end2end_ultralytics_model(model) or is_yolo26_model(model):
        predict_kwargs['max_det'] = 300
        predict_kwargs['iou'] = max(iou, 0.7)
    with _get_model_infer_lock(model):
        results = model(frame, **predict_kwargs)
    result = results[0]
    detections = []
    if result.boxes is None or len(result.boxes) == 0:
        return detections

    boxes = result.boxes.xyxy.cpu().numpy()
    confidences = result.boxes.conf.cpu().numpy()
    class_ids = result.boxes.cls.cpu().numpy().astype(int)
    names = getattr(model, 'names', {})

    for box, score, cls_id in zip(boxes, confidences, class_ids):
        x1, y1, x2, y2 = map(int, box)
        class_name = names[cls_id] if names else f'class_{cls_id}'
        if not class_name_allowed(class_name, allowed_class_names):
            continue
        if should_keep and not should_keep(class_name):
            continue
        detections.append({
            'class_id': int(cls_id),
            'class_name': class_name,
            'confidence': float(score),
            'bbox': [int(x1), int(y1), int(x2), int(y2)],
        })
    return detections
