"""
@author reese
@email reese
"""
from flask import Blueprint, request, jsonify, send_file
from pathlib import Path
import logging
import os
import re
import time
from datetime import datetime, timedelta, timezone
from threading import Lock
from urllib.parse import unquote, parse_qs, urlparse
from app.services.alert_service import (
    get_alert_list,
    get_alert_count,
    create_alert,
    get_dashboard_statistics,
    clear_all_alerts,
    clear_alerts_by_task_name,
    get_correlation_events,
)
from app.utils.service_urls import parse_alert_time_str, normalize_to_shanghai_naive
from app.services.alert_hook_service import process_alert_hook
from app.services.media_authorization_service import (
    MediaAuthorizationDecision,
    audit_media_response,
    authorization_error,
    authorize_media_request,
)
from app.services.local_media_path_service import (
    LocalMediaPathError,
    resolve_allowed_local_media_file,
)

# 创建Alert蓝图
alert_bp = Blueprint('alert', __name__)
logger = logging.getLogger(__name__)

# 请求去重缓存：避免短时间内重复查询
_query_cache = {}
_cache_lock = Lock()
_cache_ttl = 5  # 缓存有效期5秒

# 仪表板统计缓存（大屏多组件轮询）
_stats_cache = {'data': None, 'ts': 0.0}
_stats_cache_lock = Lock()
_stats_cache_ttl = 5


def _parse_alert_time_str(alert_time_str: str):
    return parse_alert_time_str(alert_time_str)


def _to_shanghai_naive(value):
    return normalize_to_shanghai_naive(value)


def api_response(code=200, message="success", data=None):
    """统一 API 响应格式（与改造后的前端 axios 解析一致：业务 code 成功为 0，HTTP 统一 200）"""
    business_code = 0 if code == 200 else code
    response = {
        "code": business_code,
        "msg": message,
        "message": message,
        "data": data
    }
    return jsonify(response), 200


def _authorization_denied_response(decision):
    payload, status = authorization_error(decision)
    return jsonify(payload), status


def _request_camera_hint():
    return (
        request.args.get('camera_id')
        or request.args.get('cameraId')
        or request.args.get('device_id')
        or request.args.get('deviceId')
        or request.headers.get('X-YFeiEye-Service-Camera-Id')
    )


def _denied_media_decision(decision, reason, status_code=403):
    denied = MediaAuthorizationDecision(
        False,
        decision.user_id,
        decision.tenant_id,
        decision.camera_id,
        decision.action,
        reason,
        status_code,
        decision.auth_type,
        decision.service_id,
    )
    return denied


def _defer_alert_audit(decision):
    return audit_media_response(decision, resource=request.path)


def _authorize_alert_collection(action):
    decision = authorize_media_request(
        request,
        action=action,
        camera_id=_request_camera_hint(),
        resource=request.path,
        defer_audit=True,
    )
    _defer_alert_audit(decision)
    if not decision.allowed:
        return None, _authorization_denied_response(decision)
    tenant_id = str(decision.tenant_id or '').strip()
    camera_id = str(decision.camera_id or '').strip()
    if not tenant_id.isdigit() or int(tenant_id) <= 0:
        denied = _denied_media_decision(
            decision, 'alert_tenant_scope_missing', 403)
        return None, _authorization_denied_response(denied)
    if not camera_id:
        denied = _denied_media_decision(
            decision, 'alert_camera_scope_missing', 403)
        return None, _authorization_denied_response(denied)
    return {
        'tenant_id': int(tenant_id),
        'camera_ids': [camera_id],
    }, None


def _authorize_alert_ingest(data):
    camera_id = str(
        (data or {}).get('device_id')
        or (data or {}).get('deviceId')
        or (data or {}).get('camera_id')
        or (data or {}).get('cameraId')
        or ''
    ).strip() or None
    if not camera_id:
        if request.headers.get('X-YFeiEye-Service-Id'):
            decision = authorize_media_request(
                request,
                action='alert_ingest',
                camera_id=request.headers.get('X-YFeiEye-Service-Camera-Id'),
                resource=request.path,
                defer_audit=True,
            )
            _defer_alert_audit(decision)
            if not decision.allowed:
                return decision
            return _denied_media_decision(
                decision,
                'alert_ingest_camera_required',
                403,
            )
        denied = MediaAuthorizationDecision(
            False,
            None,
            None,
            None,
            'alert_ingest',
            'alert_ingest_camera_required',
            403,
        )
        _defer_alert_audit(denied)
        return denied
    environment = str(os.environ.get('VIDEO_ENV') or '').strip().lower()
    allow_unsigned = str(
        os.environ.get('YFEIEYE_ALERT_INGEST_ALLOW_UNSIGNED') or ''
    ).strip().lower() in {'1', 'true', 'yes', 'on'}
    if environment in {'development', 'dev', 'test', 'local'} and allow_unsigned:
        tenant_id = str(
            os.environ.get('YFEIEYE_ALERT_INGEST_TENANT_ID') or '').strip()
        if not tenant_id.isdigit() or int(tenant_id) <= 0:
            denied = MediaAuthorizationDecision(
                False,
                'service:development',
                tenant_id or None,
                camera_id,
                'alert_ingest',
                'alert_ingest_tenant_required',
                403,
                'development_unsigned',
                'development',
            )
            _defer_alert_audit(denied)
            return denied
        allowed = MediaAuthorizationDecision(
            True,
            'service:development',
            tenant_id,
            camera_id,
            'alert_ingest',
            'development_unsigned_alert_ingest',
            200,
            'development_unsigned',
            'development',
        )
        _defer_alert_audit(allowed)
        return allowed
    if not request.headers.get('X-YFeiEye-Service-Id'):
        denied = MediaAuthorizationDecision(
            False,
            None,
            None,
            camera_id,
            'alert_ingest',
            'alert_ingest_service_hmac_required',
            401,
        )
        _defer_alert_audit(denied)
        return denied
    decision = authorize_media_request(
        request,
        action='alert_ingest',
        camera_id=camera_id,
        resource=request.path,
        defer_audit=True,
    )
    _defer_alert_audit(decision)
    return decision


def _validate_alert_ingest_media_paths(data, decision):
    for field in ('image_path', 'record_path'):
        value = str((data or {}).get(field) or '').strip()
        if not value:
            continue
        if field == 'record_path' and (
            value.startswith(('/api/', '/video/'))
            or urlparse(value).scheme.lower() in {'http', 'https'}
        ):
            continue
        try:
            data[field] = resolve_allowed_local_media_file(value)
        except LocalMediaPathError as exc:
            denied = _denied_media_decision(decision, exc.reason, 403)
            return jsonify({
                'code': 403,
                'msg': 'local media path denied',
                'reason': denied.reason,
            }), 403
    return None


def _authorize_alert_path(path, media_type, action):
    alert_id = request.args.get('alert_id') or request.args.get('alertId')
    owner_camera_id = _resolve_alert_media_camera(path, media_type, alert_id=alert_id)
    camera_id = owner_camera_id or _request_camera_hint()
    decision = authorize_media_request(
        request,
        action=action,
        camera_id=camera_id,
        resource=request.path,
        owner_tenant_id=_resolve_alert_media_tenant(
            path, media_type, alert_id=alert_id),
        defer_audit=True,
    )
    _defer_alert_audit(decision)
    if not decision.allowed:
        return None, _authorization_denied_response(decision)
    if path and not owner_camera_id:
        denied = _denied_media_decision(decision, 'media_metadata_not_found', 404)
        return None, (
            jsonify({'code': 404, 'msg': 'media not found', 'reason': denied.reason}),
            404,
        )
    return decision, None


def _resolve_alert_id_path(alert_id, media_type):
    if not alert_id:
        return None
    try:
        from models import Alert
    except (ImportError, AttributeError):
        return None
    alert = _metadata_get(Alert, alert_id)
    if alert is None:
        return None
    if media_type == 'image':
        return next(iter(_alert_media_paths(alert, media_type)), None)
    return str(getattr(alert, 'record_path', '') or '').strip() or None


def _resolve_alert_media_tenant(path, media_type, alert_id=None):
    path = str(path or '').strip()
    try:
        from models import Alert, Image, Playback, RecordFile
    except (ImportError, AttributeError):
        return None
    candidates = []
    if alert_id:
        alert = _metadata_get(Alert, alert_id)
        if not path or (
                alert is not None
                and path in _alert_media_paths(alert, media_type)):
            candidates.append(alert)
    if path and media_type == 'image':
        candidates.extend([
            _metadata_first(Alert, image_path=path),
            _metadata_first(Alert, image_url=path),
            _metadata_first(Image, path=path),
        ])
    elif path:
        candidates.extend([
            _metadata_first(Alert, record_path=path),
            _metadata_first(Playback, file_path=path),
            _metadata_first(RecordFile, url=path),
        ])
    for candidate in candidates:
        tenant_id = str(getattr(candidate, 'tenant_id', '') or '').strip()
        if tenant_id:
            return tenant_id
    return None


def _resolve_alert_media_camera(path, media_type, alert_id=None):
    """Resolve ownership from persisted metadata, never from a local path string."""
    path = (path or '').strip()
    if not path:
        return None
    try:
        from models import Alert, Image, Playback, RecordFile, RecordSpace
    except (ImportError, AttributeError):
        return None

    if alert_id:
        alert = _metadata_get(Alert, alert_id)
        if alert and path in _alert_media_paths(alert, media_type):
            return _model_device_id(alert)

    if media_type == 'image':
        for field in ('image_path', 'image_url'):
            alert = _metadata_first(Alert, **{field: path})
            if alert:
                return _model_device_id(alert)
        return _model_device_id(_metadata_first(Image, path=path))

    alert = _metadata_first(Alert, record_path=path)
    if alert:
        return _model_device_id(alert)
    playback = _metadata_first(Playback, file_path=path)
    if playback:
        return _model_device_id(playback)
    record = _metadata_first(RecordFile, url=path)
    if record:
        return _model_device_id(record)

    parsed_path = unquote(urlparse(path).path)
    matched = re.search(r'/video/record/space/(\d+)/video/(.+)$', parsed_path)
    if not matched:
        return None
    space_id = int(matched.group(1))
    object_name = matched.group(2)
    space = _metadata_get(RecordSpace, space_id)
    camera_id = _model_device_id(space)
    if not camera_id:
        return None
    record = _metadata_first(
        RecordFile,
        space_id=space_id,
        device_id=camera_id,
        object_name=object_name,
    )
    return camera_id if record else None


def _resolve_alert_query_camera(device_id, alert_id):
    if not alert_id:
        return device_id
    try:
        from models import Alert
    except (ImportError, AttributeError):
        return device_id
    alert = _metadata_get(Alert, alert_id)
    return _model_device_id(alert) or device_id


def _alert_media_paths(alert, media_type):
    fields = ('image_path', 'image_url') if media_type == 'image' else ('record_path',)
    return {
        str(getattr(alert, field, '') or '').strip()
        for field in fields
        if str(getattr(alert, field, '') or '').strip()
    }


def _model_device_id(model):
    if model is None:
        return None
    return str(getattr(model, 'device_id', '') or '').strip() or None


def _metadata_first(model, **filters):
    query = getattr(model, 'query', None)
    if query is None or not hasattr(query, 'filter_by'):
        return None
    try:
        return query.filter_by(**filters).first()
    except Exception:
        return None


def _metadata_get(model, identity):
    query = getattr(model, 'query', None)
    if query is None or not hasattr(query, 'get'):
        return None
    try:
        return query.get(int(identity))
    except Exception:
        return None


@alert_bp.route('/page')
def get_alert_list_route():
    """获取报警列表"""
    try:
        scope, denied = _authorize_alert_collection('alert_read')
        if denied:
            return denied
        args_dict = {}
        for key, value in request.args.items():
            if isinstance(value, list):
                args_dict[key] = value[0] if value else None
            else:
                args_dict[key] = value

        logger.debug(f'告警列表查询参数: {args_dict}')
        result = get_alert_list(args_dict, **scope)
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取报警列表失败: {str(e)}', exc_info=True)
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/correlation', methods=['GET'])
def get_correlation_events_route():
    """按 correlation_id 查询同一帧关联的告警、人脸匹配、车牌匹配记录"""
    try:
        scope, denied = _authorize_alert_collection('alert_read')
        if denied:
            return denied
        correlation_id = request.args.get('correlation_id') or request.args.get('correlationId')
        if not correlation_id:
            return api_response(400, 'correlation_id 不能为空')
        result = get_correlation_events(correlation_id, **scope)
        return api_response(data=result)
    except ValueError as e:
        return api_response(400, str(e))
    except Exception as e:
        logger.error(f'查询关联事件失败: {str(e)}', exc_info=True)
        return api_response(500, f'查询失败: {str(e)}')


@alert_bp.route('/count')
def get_alert_count_route():
    """获取报警统计"""
    try:
        scope, denied = _authorize_alert_collection('alert_read')
        if denied:
            return denied
        args_dict = {}
        for key, value in request.args.items():
            if isinstance(value, list):
                args_dict[key] = value[0] if value else None
            else:
                args_dict[key] = value
        result = get_alert_count(args_dict, **scope)
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取报警统计失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/statistics', methods=['GET'])
def get_dashboard_statistics_route():
    """获取仪表板统计信息（统一接口）"""
    try:
        scope, denied = _authorize_alert_collection('alert_read')
        if denied:
            return denied
        cache_key = (
            str(scope.get('tenant_id') or ''),
            tuple(sorted(str(value) for value in (scope.get('camera_ids') or ()))),
        )
        current_time = time.time()
        with _stats_cache_lock:
            cached = _stats_cache.get('data')
            cached_ts = _stats_cache.get('ts') or 0.0
            if (
                cached is not None
                and _stats_cache.get('key') == cache_key
                and current_time - cached_ts < _stats_cache_ttl
            ):
                return api_response(data=cached)

        result = get_dashboard_statistics(**scope)
        with _stats_cache_lock:
            _stats_cache['data'] = result
            _stats_cache['ts'] = current_time
            _stats_cache['key'] = cache_key
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取仪表板统计信息失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/image')
def get_alert_image():
    """获取报警图片（支持本地文件和MinIO存储）"""
    try:
        alert_id = request.args.get('alert_id') or request.args.get('alertId')
        path = request.args.get('path') or _resolve_alert_id_path(alert_id, 'image')
        decision, denied_response = _authorize_alert_path(path, 'image', 'snapshot')
        if denied_response:
            return denied_response
        if not path:
            return api_response(400, '路径参数不能为空')
        
        # 检查是否是MinIO下载URL格式（/api/v1/buckets/{bucket_name}/objects/download?prefix=...）
        if path.startswith('/api/v1/buckets/') and '/objects/download' in path:
            try:
                from app.services.minio_service import ModelService
                from minio.error import S3Error
                from io import BytesIO
                
                # 解析URL：/api/v1/buckets/{bucket_name}/objects/download?prefix={object_name}
                parsed = urlparse(path)
                query_params = parse_qs(parsed.query)
                
                # 提取bucket_name和object_name
                path_parts = parsed.path.split('/')
                if len(path_parts) < 5 or path_parts[1] != 'api' or path_parts[2] != 'v1' or path_parts[3] != 'buckets':
                    return api_response(400, f'MinIO URL格式错误: {path}')
                
                bucket_name = path_parts[4]
                prefix = query_params.get('prefix', [None])[0]
                
                if not prefix:
                    return api_response(400, f'MinIO URL缺少prefix参数: {path}')
                
                # URL解码prefix
                object_name = unquote(prefix)
                
                # 获取MinIO客户端
                minio_client = ModelService.get_minio_client()
                
                # 检查存储桶是否存在
                if not minio_client.bucket_exists(bucket_name):
                    return api_response(400, f'MinIO存储桶不存在: {bucket_name}')
                
                # 从MinIO获取对象
                try:
                    stat = minio_client.stat_object(bucket_name, object_name)
                    data = minio_client.get_object(bucket_name, object_name)
                    content = data.read()
                    data.close()
                    data.release_conn()
                    
                    # 返回文件内容
                    from flask import Response
                    return Response(
                        content,
                        mimetype=stat.content_type or 'image/jpeg',
                        headers={
                            'Content-Disposition': f'inline; filename={object_name.split("/")[-1]}'
                        }
                    )
                except S3Error as e:
                    if e.code == 'NoSuchKey':
                        return api_response(400, f'MinIO对象不存在: {object_name}')
                    raise
            except Exception as e:
                logger.error(f'从MinIO获取报警图片失败: {str(e)}', exc_info=True)
                return api_response(500, f'从MinIO获取失败: {str(e)}')
        else:
            # 本地文件路径
            try:
                file_path = Path(resolve_allowed_local_media_file(path))
            except LocalMediaPathError as exc:
                denied = _denied_media_decision(decision, exc.reason, 403)
                return jsonify({
                    'code': 403,
                    'msg': 'local media path denied',
                    'reason': denied.reason,
                }), 403
            if not file_path.exists():
                return api_response(400, f'文件不存在: {path}')
            
            return send_file(str(file_path))
    except Exception as e:
        logger.error(f'获取报警图片失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/record')
def get_alert_record():
    """获取报警录像（支持本地文件 Range 请求）"""
    try:
        from urllib.parse import unquote

        from app.services.media_dvr_utils import resolve_playback_absolute_path

        alert_id = request.args.get('alert_id') or request.args.get('alertId')
        path = unquote((request.args.get('path') or '').strip()) \
            or _resolve_alert_id_path(alert_id, 'record')
        decision, denied_response = _authorize_alert_path(path, 'record', 'playback')
        if denied_response:
            return denied_response
        if not path:
            return jsonify({'code': 400, 'message': '路径参数不能为空', 'data': None}), 400

        file_path = resolve_playback_absolute_path(path)
        try:
            file_path = resolve_allowed_local_media_file(file_path)
        except LocalMediaPathError as exc:
            denied = _denied_media_decision(decision, exc.reason, 403)
            return jsonify({
                'code': 403,
                'msg': 'local media path denied',
                'reason': denied.reason,
            }), 403
        if not file_path or not Path(file_path).exists():
            logger.warning('告警录像不存在 path=%s resolved=%s', path, file_path)
            return jsonify({'code': 404, 'message': f'文件不存在: {path}', 'data': None}), 404

        playback_metadata = None
        playback_lease = None
        release_playback_lease = None
        if (request.args.get('playback_format') or '').strip().lower() == 'mp4':
            from app.services.seekable_playback_service import (
                prepare_seekable_mp4_path,
                release_seekable_playback_lease,
            )

            playback_metadata = prepare_seekable_mp4_path(file_path, acquire_lease=True)
            file_path = playback_metadata['path']
            playback_lease = playback_metadata['lease']
            release_playback_lease = release_seekable_playback_lease

        ext = Path(file_path).suffix.lower()
        mimetype_map = {
            '.flv': 'video/x-flv',
            '.mp4': 'video/mp4',
            '.ts': 'video/mp2t',
            '.mkv': 'video/x-matroska',
        }
        mimetype = mimetype_map.get(ext, 'application/octet-stream')
        try:
            response = send_file(
                str(file_path),
                mimetype=mimetype,
                conditional=True,
                as_attachment=False,
            )
            if playback_metadata:
                response.headers['X-YFeiEye-Seekable-Playback'] = 'mp4'
                response.headers['X-YFeiEye-Source-SHA256'] = playback_metadata['source_sha256']
                response.headers['X-YFeiEye-Output-SHA256'] = playback_metadata['output_sha256']
        except Exception:
            if playback_lease is not None and release_playback_lease is not None:
                release_playback_lease(playback_lease)
            raise
        if playback_lease is not None and release_playback_lease is not None:
            response.call_on_close(
                lambda lease=playback_lease, release=release_playback_lease:
                release(lease))
        return response
    except Exception as e:
        logger.error(f'获取报警录像失败: {str(e)}')
        return jsonify({'code': 500, 'message': f'获取失败: {str(e)}', 'data': None}), 500


@alert_bp.route('/hook', methods=['POST'])
def alert_hook():
    """告警Hook接口：接收告警事件并发送到Kafka"""
    try:
        data = request.get_json()
        if not data:
            return api_response(400, '请求数据不能为空')

        decision = _authorize_alert_ingest(data)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        tenant_id = str(decision.tenant_id or '').strip()
        if not tenant_id.isdigit() or int(tenant_id) <= 0:
            denied = _denied_media_decision(
                decision, 'alert_ingest_tenant_required', 403)
            return _authorization_denied_response(denied)
        data['tenant_id'] = int(tenant_id)
        media_path_denial = _validate_alert_ingest_media_paths(data, decision)
        if media_path_denial:
            return media_path_denial
        
        # 调用告警Hook服务处理
        result = process_alert_hook(data)
        
        if result.get('status') == 'success':
            return api_response(200, '告警事件已发送', result)
        elif result.get('status') in ('skipped', 'suppressed'):
            return api_response(200, '告警事件已跳过', result)
        else:
            return api_response(500, f"告警事件处理失败: {result.get('error', '未知错误')}", result)
    except Exception as e:
        logger.error(f'处理告警Hook失败: {str(e)}', exc_info=True)
        return api_response(500, f'处理失败: {str(e)}')


@alert_bp.route('/record/query', methods=['GET'])
def query_alert_record():
    """根据告警时间和设备ID查询对应的录像
    
    参数:
        device_id: 设备ID（必填）
        alert_time: 告警时间，格式：'YYYY-MM-DD HH:MM:SS'（必填）
        time_range: 时间范围（秒），默认300秒，用于查找告警时间前后范围内的录像
    """
    try:
        device_id = request.args.get('device_id')
        alert_time_str = request.args.get('alert_time')
        alert_id = request.args.get('alert_id')
        scoped_camera_id = _resolve_alert_query_camera(device_id, alert_id) or _request_camera_hint()
        owner_tenant_id = _resolve_alert_media_tenant(
            None, 'record', alert_id=alert_id)
        decision = authorize_media_request(
            request,
            action='coverage',
            camera_id=scoped_camera_id,
            resource=request.path,
            owner_tenant_id=owner_tenant_id,
            defer_audit=True,
        )
        _defer_alert_audit(decision)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        if device_id and scoped_camera_id and device_id != scoped_camera_id:
            return _authorization_denied_response(
                _denied_media_decision(decision, 'camera_alert_scope_mismatch')
            )
        time_range = int(request.args.get('time_range', 300))  # 默认前后300秒（5分钟）

        if not device_id and not alert_id:
            return api_response(400, '设备ID不能为空')
        if not alert_time_str and not alert_id:
            return api_response(400, '告警时间不能为空')
        
        # 请求去重：检查是否在短时间内有相同的请求
        cache_key = (
            f"{decision.tenant_id}:{device_id}:{alert_id or ''}:"
            f"{alert_time_str or ''}:{time_range}"
        )
        current_time = time.time()
        
        with _cache_lock:
            # 清理过期的缓存
            expired_keys = [k for k, (_, timestamp) in _query_cache.items() 
                          if current_time - timestamp > _cache_ttl]
            for key in expired_keys:
                _query_cache.pop(key, None)
            
            # 检查是否有相同的请求在缓存中
            if cache_key in _query_cache:
                cached_result, cached_timestamp = _query_cache[cache_key]
                if current_time - cached_timestamp < _cache_ttl:
                    logger.debug(f'使用缓存结果，避免重复查询 cache_key={cache_key}')
                    return cached_result
        
        # 执行查询
        try:
            result = _do_query_alert_record(
                device_id,
                alert_time_str,
                time_range,
                alert_id=alert_id,
                tenant_id=decision.tenant_id,
            )
            
            # 缓存结果（只缓存400错误，避免重复查询）
            if result[1] == 400:  # result是(Response, status_code)元组
                with _cache_lock:
                    _query_cache[cache_key] = (result, current_time)
            
            return result
        except Exception as e:
            logger.error(f'查询告警录像失败: {str(e)}', exc_info=True)
            return api_response(500, f'查询失败: {str(e)}')
    except Exception as e:
        logger.error(f'查询告警录像失败: {str(e)}', exc_info=True)
        return api_response(500, f'查询失败: {str(e)}')


def _do_query_alert_record(device_id, alert_time_str, time_range, alert_id=None,
                           tenant_id=None):
    """执行实际的查询逻辑"""
    from app.services.alert_service import resolve_alert_record_video
    from app.utils.service_urls import ensure_shanghai_aware

    alert_time = None
    if alert_id:
        try:
            from models import Alert

            alert_query = Alert.query.filter(Alert.id == int(alert_id))
            if tenant_id is not None:
                alert_query = alert_query.filter(
                    Alert.tenant_id == int(tenant_id))
            alert_row = alert_query.first()
            if alert_row:
                device_id = device_id or alert_row.device_id
                if alert_row.time is not None:
                    alert_time = ensure_shanghai_aware(alert_row.time)
        except (TypeError, ValueError):
            pass

    if alert_time is None and alert_time_str:
        alert_time, err = _parse_alert_time_str(alert_time_str)
        if err:
            return api_response(400, err)

    if not device_id:
        return api_response(400, '设备ID不能为空')
    if alert_time is None:
        return api_response(400, '告警时间不能为空')

    resolved = resolve_alert_record_video(
        device_id,
        alert_time,
        time_range=time_range,
        alert_id=alert_id,
        tenant_id=tenant_id,
    )
    if not resolved:
        logger.debug(
            '未找到匹配的录像 device_id=%s, alert_time=%s, alert_id=%s, time_range=%s',
            device_id, alert_time_str, alert_id, time_range,
        )
        return jsonify({
            "code": 400,
            "reason": "record_not_found",
            "message": f'该设备在告警时间前后{time_range}秒内暂无录像记录，请稍后再试',
            "data": None
        }), 200

    return api_response(200, 'success', resolved)


@alert_bp.route('/clear', methods=['DELETE'])
def clear_alerts_by_task_name_route():
    """清空任务的所有告警记录（通过task_name）"""
    try:
        scope, denied = _authorize_alert_collection('alert_manage')
        if denied:
            return denied
        task_name = request.args.get('task_name')
        if not task_name:
            return api_response(400, 'task_name参数不能为空')

        result = clear_alerts_by_task_name(task_name, **scope)
        return api_response(200, 'success', result)
    except ValueError as e:
        return api_response(400, str(e))
    except Exception as e:
        logger.error(f'清空任务告警失败: {str(e)}', exc_info=True)
        return api_response(500, f'清空失败: {str(e)}')


@alert_bp.route('/clear/all', methods=['DELETE'])
def clear_all_alerts_route():
    """清空所有告警记录"""
    try:
        scope, denied = _authorize_alert_collection('alert_manage')
        if denied:
            return denied
        result = clear_all_alerts(**scope)
        return api_response(200, 'success', result)
    except Exception as e:
        logger.error(f'清空所有告警失败: {str(e)}', exc_info=True)
        return api_response(500, f'清空失败: {str(e)}')
