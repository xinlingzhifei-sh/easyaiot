"""
姿态分析 Flask 蓝图：图片 / 视频 / 摄像头(RTSP) 姿态估计。
"""
from __future__ import annotations

import logging
import os
import tempfile

from flask import Blueprint, jsonify, request, send_file
from werkzeug.utils import secure_filename

from app.services.pose_service import PoseService
from db_models import Model

logger = logging.getLogger(__name__)

pose_bp = Blueprint('pose', __name__)

VIDEO_ALLOWED_EXT = {'.mp4', '.avi', '.mov', '.mkv', '.webm', '.flv', '.wmv', '.m4v'}


def _ok(data=None, msg='success'):
    return jsonify({'code': 0, 'msg': msg, 'data': data or {}})


def _err(code, msg, http_status=400):
    return jsonify({'code': code, 'msg': msg}), http_status


def _resolve_model_id(raw) -> int:
    try:
        return max(0, int(raw))
    except (TypeError, ValueError):
        return 0


def _build_pose_service(model_id: int) -> PoseService:
    svc = PoseService(model_id if model_id > 0 else None)
    model_file_path = request.form.get('model_file_path') or (
        (request.json or {}).get('model_file_path') if request.is_json else None
    )
    if model_file_path:
        svc.set_model_file_path(model_file_path)
    return svc


@pose_bp.route('/<int:model_id>/predict', methods=['POST'])
def predict_image(model_id):
    """图片姿态估计（同步）。"""
    file = request.files.get('file')
    if file is None or not file.filename:
        return _err(400, '未接收到图片')

    try:
        conf = float(request.form.get('conf', 0.25))
    except (TypeError, ValueError):
        conf = 0.25
    draw = request.form.get('draw', '1') not in ('0', 'false', 'False')

    try:
        svc = _build_pose_service(model_id)
        result = svc.predict_image(file.read(), conf=conf, draw=draw)
        return _ok(result, msg='姿态估计完成')
    except FileNotFoundError as exc:
        return _err(400, str(exc))
    except Exception as exc:
        logger.exception('图片姿态估计失败')
        return _err(500, f'姿态估计失败: {exc}', 500)


@pose_bp.route('/<int:model_id>/predict-video', methods=['POST'])
def predict_video(model_id):
    """视频姿态估计（异步），返回 jobId。"""
    file = request.files.get('file')
    if file is None or not file.filename:
        return _err(400, '未接收到视频')

    ext = os.path.splitext(file.filename)[1].lower()
    if ext not in VIDEO_ALLOWED_EXT:
        return _err(400, '不支持的视频格式')

    try:
        conf = float(request.form.get('conf', 0.25))
    except (TypeError, ValueError):
        conf = 0.25

    temp_dir = tempfile.mkdtemp(prefix='pose_video_')
    base = secure_filename(os.path.splitext(file.filename)[0]) or 'video'
    src_path = os.path.join(temp_dir, f'{base}{ext}')
    file.save(src_path)

    try:
        svc = _build_pose_service(model_id)
        job_id = svc.start_video_job(src_path, conf=conf)
        return _ok({'jobId': job_id}, msg='任务已启动')
    except Exception as exc:
        logger.exception('视频姿态任务启动失败')
        return _err(500, f'任务启动失败: {exc}', 500)


@pose_bp.route('/progress/<job_id>', methods=['GET'])
def video_progress(job_id):
    """查询视频姿态任务进度。"""
    svc = PoseService()
    job = svc.get_video_progress(job_id)
    if not job:
        return _err(404, '任务不存在或已过期', 404)
    if job.get('status') == 'error':
        return _ok(job, msg=job.get('error') or '视频处理失败')
    return _ok(job)


@pose_bp.route('/output/<path:filename>', methods=['GET'])
def download_output(filename):
    """下载姿态视频输出文件。"""
    try:
        svc = PoseService()
        path = svc.get_output_path(filename)
        return send_file(path, mimetype='video/mp4', as_attachment=True, download_name=os.path.basename(filename))
    except FileNotFoundError:
        return _err(404, '文件不存在', 404)


@pose_bp.route('/<int:model_id>/rtsp/start', methods=['POST'])
def start_rtsp_pose(model_id):
    """启动摄像头 RTSP 姿态推流。"""
    if request.is_json:
        data = request.json or {}
    else:
        data = request.form.to_dict()
        if 'parameters' in data and isinstance(data['parameters'], str):
            import json
            try:
                data['parameters'] = json.loads(data['parameters'])
            except Exception:
                data['parameters'] = {}

    rtsp_url = (data.get('input_source') or '').strip()
    device_id = (data.get('device_id') or '').strip()
    parameters = data.get('parameters') or {}
    if isinstance(parameters, str):
        import json
        try:
            parameters = json.loads(parameters)
        except Exception:
            parameters = {}

    if device_id:
        parameters['device_id'] = device_id
    if not rtsp_url and not device_id:
        return _err(400, '请提供 device_id 或 input_source（rtsp:// / rtmp://）')

    try:
        svc = _build_pose_service(model_id)
        result = svc.start_rtsp(rtsp_url, parameters)
        return _ok({'result': result, 'record_id': result.get('record_id')}, msg='姿态推流已启动')
    except Exception as exc:
        logger.exception('RTSP 姿态推流启动失败')
        return _err(500, f'姿态推流启动失败: {exc}', 500)


@pose_bp.route('/rtsp/stop', methods=['POST'])
def stop_rtsp_pose():
    """停止姿态 RTSP 推流。"""
    data = request.json if request.is_json else request.form.to_dict()
    device_id = (data.get('device_id') or '').strip() or None
    record_id = data.get('record_id')
    stop_all = str(data.get('stop_all', '')).lower() in ('1', 'true', 'yes')

    try:
        if record_id is not None:
            record_id = int(record_id)
    except (TypeError, ValueError):
        record_id = None

    svc = PoseService()
    stopped = svc.stop_rtsp(device_id=device_id, record_id=record_id, stop_all=stop_all)
    return _ok({'stopped': stopped}, msg='已停止姿态推流')


@pose_bp.route('/models', methods=['GET'])
def list_pose_models():
    """返回可用于姿态分析的模型列表（内置 + 名称含 pose 的自定义模型）。"""
    from app.utils.pose_inference import DEFAULT_POSE_MODELS

    builtin = [
        {'id': 'yolo26n-pose', 'name': 'Yolo26n-Pose', 'model_file_path': 'yolo26n-pose.pt'},
    ]
    custom = []
    try:
        rows = Model.query.order_by(Model.id.desc()).limit(200).all()
        for m in rows:
            path = (m.onnx_model_path or '').lower()
            name = (m.name or '').lower()
            if 'pose' in path or 'pose' in name:
                custom.append({
                    'id': m.id,
                    'name': m.name,
                    'model_file_path': m.onnx_model_path,
                    'version': m.version,
                })
    except Exception as exc:
        logger.warning('查询姿态模型列表失败: %s', exc)

    return _ok({'builtin': builtin, 'custom': custom, 'defaults': list(DEFAULT_POSE_MODELS)})
