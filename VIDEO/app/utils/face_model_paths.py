"""External runtime paths for downloadable face models."""
import os

_VIDEO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_MODEL_ROOT = os.getenv('YFEIEYE_MODEL_ROOT', os.path.join(_VIDEO_ROOT, 'model'))

# 人脸/头部检测（算法抓脸队列）
FACE_CAPTURE_MODEL_PATH = os.getenv(
    'FACE_CAPTURE_MODEL_PATH',
    os.path.join(_MODEL_ROOT, 'face_det.onnx'),
)
FACE_CAPTURE_CLASS_NAMES = {0: 'face'}

# 人脸特征提取（1:N 匹配，InsightFace ArcFace ONNX）
FACE_MATCH_MODEL_PATH = os.getenv(
    'FACE_MATCH_MODEL_PATH',
    os.path.join(_MODEL_ROOT, 'face_rec.onnx'),
)
