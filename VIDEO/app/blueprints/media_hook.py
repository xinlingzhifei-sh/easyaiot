"""
流媒体集群 Hook 入口（SRS / ZLM）→ Kafka 异步入队或同步上传。
"""
import logging

from flask import Blueprint, jsonify, request

from app.services.dvr_device_resolver import resolve_device_from_hook
from app.services.dvr_upload_service import process_dvr_event
from app.services.media_kafka_service import (
    build_event_from_srs_hook,
    build_event_from_zlm_hook,
    enqueue_srs_dvr_hook,
    enqueue_zlm_record_hook,
    is_hybrid_upload_mode,
    is_kafka_upload_mode,
    is_snap_kafka_mode,
    publish_dvr_event,
    publish_snap_event,
)
from app.services.device_access_state_service import record_media_stream_offline, record_srs_publish_online
from app.services.rtmp_ingest_auth_service import verify_rtmp_publish_hook
from app.services.snap_upload_service import build_snap_event, process_snap_event
from app.utils.video_env import authorize_srs_hook_token

logger = logging.getLogger(__name__)

media_hook_bp = Blueprint('media_hook', __name__)


def _hook_ok():
    return jsonify({'code': 0, 'msg': None})


def _require_internal_hook_token():
    provided_token = (
        request.args.get('hook_token')
        or request.headers.get('X-YFeiEye-Hook-Token')
    )
    if authorize_srs_hook_token(provided_token):
        return None
    logger.warning('internal media hook rejected: invalid hook token')
    return jsonify({'code': 403, 'msg': 'forbidden'}), 403


def _hook_node_id(data):
    value = data.get('node_id') if data.get('node_id') is not None else data.get('nodeId')
    if value in (None, ''):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _record_publish_online(data, result, *, source_event='srs.on_publish'):
    try:
        record_srs_publish_online(
            data,
            node_id=_hook_node_id(data),
            tenant_id=result.get('tenant_id'),
            source_event=source_event,
        )
    except Exception as e:
        logger.warning('record publish online access-state failed: %s', e, exc_info=True)


def _record_stream_offline(data, result=None, *, source_event='srs.on_unpublish'):
    try:
        record_media_stream_offline(
            data,
            node_id=_hook_node_id(data),
            tenant_id=(result or {}).get('tenant_id'),
            source_event=source_event,
        )
    except Exception as e:
        logger.warning('record stream offline access-state failed: %s', e, exc_info=True)


@media_hook_bp.route('/hook/srs/on_dvr', methods=['POST'])
def srs_on_dvr():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    data = request.get_json(silent=True) or {}
    if not data.get('stream') and not data.get('file'):
        return _hook_ok()
    device_id, _ = resolve_device_from_hook(data.get('stream', ''), data.get('file', ''))
    if is_kafka_upload_mode():
        enqueue_srs_dvr_hook(data, device_id=device_id)
        if not is_hybrid_upload_mode():
            return _hook_ok()
    event = build_event_from_srs_hook(data, device_id=device_id)
    if is_hybrid_upload_mode() or not is_kafka_upload_mode():
        process_dvr_event(event)
    return _hook_ok()


@media_hook_bp.route('/hook/srs/on_publish', methods=['POST'])
def srs_on_publish():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    """转发至现有 on_publish 逻辑（流冲突检测）。"""
    data = request.get_json(silent=True) or {}
    result = verify_rtmp_publish_hook(
        data,
        remote_ip=request.headers.get('X-Forwarded-For') or request.remote_addr,
        source_event='srs.on_publish',
    )
    if not result.get('accepted'):
        return jsonify({'code': 403, 'msg': result.get('reason_message') or result.get('reason_code')}), 403
    _record_publish_online(data, result, source_event='srs.on_publish')
    from app.blueprints.camera import _handle_authorized_on_publish_callback
    return _handle_authorized_on_publish_callback()


@media_hook_bp.route('/hook/srs/on_unpublish', methods=['POST'])
def srs_on_unpublish():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    data = request.get_json(silent=True) or {}
    _record_stream_offline(data, source_event='srs.on_unpublish')
    return _hook_ok()


@media_hook_bp.route('/hook/zlm/on_publish', methods=['POST'])
def zlm_on_publish():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    data = request.get_json(silent=True) or {}
    result = verify_rtmp_publish_hook(
        data,
        remote_ip=request.headers.get('X-Forwarded-For') or request.remote_addr,
        source_event='zlm.on_publish',
    )
    if not result.get('accepted'):
        return jsonify({'code': 403, 'msg': result.get('reason_message') or result.get('reason_code')}), 403
    _record_publish_online(data, result, source_event='zlm.on_publish')
    return _hook_ok()


@media_hook_bp.route('/hook/zlm/on_stream_changed', methods=['POST'])
def zlm_on_stream_changed():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    data = request.get_json(silent=True) or {}
    if data.get('regist') is False:
        _record_stream_offline(data, source_event='zlm.on_stream_changed')
    return _hook_ok()


@media_hook_bp.route('/hook/snap/completed', methods=['POST'])
def snap_completed():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    """抓拍完成通知：本地文件已落盘，请求上传 MinIO。"""
    data = request.get_json(silent=True) or {}
    device_id = (data.get('device_id') or '').strip()
    file_path = data.get('file_path') or data.get('file') or ''
    if not device_id or not file_path:
        return _hook_ok()
    event = build_snap_event(
        device_id,
        file_path,
        source=data.get('source') or 'algorithm',
        task_id=data.get('task_id'),
        space_id=data.get('space_id'),
    )
    if is_snap_kafka_mode():
        publish_snap_event(event)
        return _hook_ok()
    process_snap_event(event)
    return _hook_ok()


@media_hook_bp.route('/hook/zlm/on_record_mp4', methods=['POST'])
@media_hook_bp.route('/hook/zlm/on_record_ts', methods=['POST'])
def zlm_on_record():
    denied = _require_internal_hook_token()
    if denied:
        return denied
    data = request.get_json(silent=True) or {}
    file_path = data.get('file_path', '') or data.get('file_name', '')
    stream = data.get('stream', '') or ''
    if not file_path and not stream:
        return _hook_ok()
    device_id, _ = resolve_device_from_hook(stream, file_path)
    if is_kafka_upload_mode():
        enqueue_zlm_record_hook(data, device_id=device_id)
        if not is_hybrid_upload_mode():
            return _hook_ok()
    event = build_event_from_zlm_hook(data, device_id=device_id)
    if is_hybrid_upload_mode() or not is_kafka_upload_mode():
        process_dvr_event(event)
    return _hook_ok()
