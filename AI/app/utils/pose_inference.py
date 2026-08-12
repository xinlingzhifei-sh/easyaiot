"""
姿态估计推理工具（基于 ultralytics YOLO Pose）。
"""
from __future__ import annotations

import base64
import logging
import os
import platform
import subprocess
import threading
from typing import Callable, Optional

import cv2
import numpy as np

from app.utils.model_upload_security import (
    require_official_pretrained_model,
    require_web_safe_model_reference,
)

logger = logging.getLogger(__name__)

_lock = threading.Lock()
_model_cache: dict = {}

DEFAULT_POSE_MODELS = ('yolo26n-pose.pt',)


def _get_yolo_model(abs_path: str):
    """加载并缓存 YOLO 姿态模型。"""
    from ultralytics import YOLO

    path = os.path.abspath(abs_path)
    if path.lower().endswith('.onnx'):
        require_web_safe_model_reference(path)
    else:
        require_official_pretrained_model(os.path.basename(path))
    mtime = os.path.getmtime(path) if os.path.isfile(path) else 0
    cache_key = (path, mtime)
    with _lock:
        if cache_key in _model_cache:
            return _model_cache[cache_key]
        model = YOLO(path)
        _model_cache[cache_key] = model
        return model


def _parse_keypoints(result) -> list[dict]:
    persons = []
    if result.keypoints is None or result.keypoints.data is None:
        return persons
    for kp in result.keypoints.data.cpu().tolist():
        pts = [
            [round(float(x), 1), round(float(y), 1), round(float(c), 4)]
            for x, y, c in kp
        ]
        persons.append({'keypoints': pts})
    return persons


def estimate_pose(abs_path: str, image_bytes: bytes, conf: float = 0.25, draw: bool = True) -> dict:
    """图片姿态估计：返回关键点与骨架标注图。"""
    arr = np.frombuffer(image_bytes, np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError('无法解析图片')

    model = _get_yolo_model(abs_path)
    result = model.predict(img, conf=conf, verbose=False)[0]
    persons = _parse_keypoints(result)

    image_b64 = None
    if draw:
        plotted = result.plot()
        ok, buf = cv2.imencode('.jpg', plotted)
        image_b64 = base64.b64encode(buf.tobytes()).decode() if ok else None

    h, w = img.shape[:2]
    return {
        'count': len(persons),
        'persons': persons,
        'imageBase64': image_b64,
        'width': w,
        'height': h,
        'keypointCount': 17,
        'poseType': 'body17',
    }


def _convert_to_h264(src_path: str, dst_path: str) -> None:
    """将 OpenCV 输出的 mp4v 视频转为浏览器可播的 H.264。"""
    if platform.system() == 'Darwin':
        cmd = [
            'ffmpeg', '-y', '-i', src_path,
            '-c:v', 'h264_videotoolbox', '-profile:v', 'main', '-level', '4.0',
            '-pix_fmt', 'yuv420p', '-movflags', '+faststart', dst_path,
        ]
    else:
        cmd = [
            'ffmpeg', '-y', '-i', src_path,
            '-c:v', 'libx264', '-preset', 'medium',
            '-pix_fmt', 'yuv420p', '-movflags', '+faststart', dst_path,
        ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        logger.warning('FFmpeg H.264 转换失败，使用原始视频: %s', proc.stderr[:200])
        if os.path.isfile(src_path):
            import shutil
            shutil.copy2(src_path, dst_path)


def pose_video(
    abs_path: str,
    src_path: str,
    dst_path: str,
    conf: float = 0.25,
    progress_cb: Optional[Callable[[int, int], None]] = None,
) -> dict:
    """逐帧姿态估计视频，输出骨架视频。"""
    model = _get_yolo_model(abs_path)

    cap = None
    writer = None
    temp_path = f'{dst_path}.tmp.mp4'
    total_persons = 0
    frames = 0
    total = 0
    fps = 25.0
    ew = eh = 0

    try:
        cap = cv2.VideoCapture(src_path)
        if not cap.isOpened():
            raise ValueError('无法打开视频文件')

        fps = cap.get(cv2.CAP_PROP_FPS) or 25.0
        w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        total = int(cap.get(cv2.CAP_PROP_FRAME_COUNT)) or 0
        ew, eh = w - (w % 2), h - (h % 2)

        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        writer = cv2.VideoWriter(temp_path, fourcc, fps, (w, h))
        if not writer.isOpened():
            raise ValueError(f'无法创建视频输出: {temp_path}')

        while True:
            ok, frame = cap.read()
            if not ok:
                break
            result = model.predict(frame, conf=conf, verbose=False)[0]
            if result.keypoints is not None and result.keypoints.data is not None:
                total_persons += len(result.keypoints.data)
            plotted = result.plot()
            writer.write(plotted[:eh, :ew] if plotted.shape[1] != w else plotted)
            frames += 1
            if progress_cb:
                progress_cb(frames, total)
    finally:
        if cap is not None:
            cap.release()
        if writer is not None:
            writer.release()

    if os.path.isfile(temp_path):
        _convert_to_h264(temp_path, dst_path)
        try:
            os.remove(temp_path)
        except OSError:
            pass

    return {
        'frames': frames,
        'totalFrames': total,
        'totalPersons': total_persons,
        'fps': round(float(fps), 2),
        'width': ew,
        'height': eh,
        'keypointCount': 17,
        'poseType': 'body17',
    }


def predict_pose_frame(model, frame_bgr: np.ndarray, conf: float = 0.25) -> np.ndarray:
    """单帧姿态推理，返回带骨架标注的 BGR 帧。"""
    result = model.predict(frame_bgr, conf=conf, verbose=False)[0]
    return result.plot()
