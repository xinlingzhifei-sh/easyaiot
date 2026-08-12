"""External runtime paths for downloadable plate models."""
import os

_VIDEO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
_MODEL_ROOT = os.getenv('YFEIEYE_MODEL_ROOT', os.path.join(_VIDEO_ROOT, 'model'))

PLATE_DETECT_MODEL_PATH = os.getenv(
    'PLATE_DETECT_MODEL_PATH',
    os.path.join(_MODEL_ROOT, 'plate_detect.onnx'),
)
PLATE_REC_MODEL_PATH = os.getenv(
    'PLATE_REC_MODEL_PATH',
    os.path.join(_MODEL_ROOT, 'plate_rec.onnx'),
)
