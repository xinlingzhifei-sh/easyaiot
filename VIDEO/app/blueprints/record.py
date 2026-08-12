"""
监控录像空间管理路由
@author reese
@email reese
"""
import hashlib
import json
import logging
import os
import re
import uuid
from datetime import datetime, timezone
from flask import Blueprint, g, request, jsonify, send_file, Response, stream_with_context
from io import BytesIO
from urllib.parse import unquote, urlparse

from models import db
from app.services.record_space_service import (
    create_record_space, update_record_space, delete_record_space,
    get_record_space, list_record_spaces, list_record_space_authorization_scopes,
    get_record_space_by_device_id, sync_spaces_to_minio
)
from app.services.record_video_service import (
    list_record_videos, delete_record_videos, get_record_video, cleanup_old_videos_by_save_time,
    sync_record_videos_metadata, list_record_video_dates, list_record_videos_day_detail,
    find_segment_for_alert, query_recording_availability, inspect_recording_storage_drift,
    materialize_record_video,
)
from app.services.record_export_service import (
    RecordExportAccessDecisionConflictError,
    RecordExportExpiredError,
    RecordExportIntegrityError,
    append_record_export_access_audit,
    create_record_export,
    get_record_export_status,
    poll_record_export,
    retry_record_export,
    get_record_export_audit,
    get_record_export_manifest,
    download_record_export,
    validate_record_export_request,
)
from app.services.media_authorization_service import (
    MediaAccessAuditConflictError,
    MediaAuthorizationDecision,
    append_media_access_audit,
    audit_media_response,
    authorization_error,
    authorize_media_request,
)
from app.services.seekable_playback_service import (
    prepare_seekable_mp4_path,
    release_seekable_playback_lease,
)
from app.services.record_cache_flush_event_service import (
    list_record_cache_flush_failures,
)

record_bp = Blueprint('record', __name__)
logger = logging.getLogger(__name__)


class _ExportAccessAuditUnavailable(RuntimeError):
    pass


class _ExportAccessDecisionConflict(RuntimeError):
    pass

_MEDIA_AUTHORIZED_ENDPOINTS = {
    'record.list_spaces',
    'record.update_group_policy',
    'record.sync_spaces_minio',
    'record.record_availability',
    'record.resolve_alert_segment',
    'record.get_space_by_device',
    'record.list_videos_by_day',
    'record.export_record',
    'record.get_record_export',
    'record.retry_record_export_job',
    'record.get_record_export_audit_entries',
    'record.get_record_export_manifest_file',
    'record.download_record_export_file',
    'record.get_video',
    'record.inspect_videos_storage_drift',
}

_EXPORT_IDENTITY_KEYS = {
    'operator_user_id', 'operatorUserId', 'generated_by', 'generatedBy',
    'approved_by', 'approvedBy', 'approver_user_id', 'approverUserId',
    'approved_at', 'approvedAt', 'tenant_id', 'tenantId',
}
_EXPORT_POLICY_KEYS = {
    'storage_type', 'storageType', 'storage_root', 'storageRoot',
    'storage_uri', 'storageUri', 'retention_days', 'retentionDays',
    'expires_at', 'expiresAt',
}
_EXPORT_SERVICE_ONLY_KEYS = {
    'review_case_id', 'reviewCaseId', 'review_item_id', 'reviewItemId',
    'review_item_ids', 'reviewItemIds', 'event_ids', 'eventIds',
    'bound_event_ids', 'boundEventIds', 'snapshot_uris', 'snapshotUris',
    'snapshots', 'source_alert_id', 'sourceAlertId', 'approval_note', 'approvalNote',
}


@record_bp.before_request
def require_record_login():
    """At minimum, every record-management endpoint requires a real login."""
    if request.endpoint in _MEDIA_AUTHORIZED_ENDPOINTS:
        return None
    camera_id, owner_tenant_id, scope_error = _resolve_record_manage_camera()
    decision = authorize_media_request(
        request,
        action='record_manage',
        camera_id=camera_id,
        resource=request.path,
        owner_tenant_id=owner_tenant_id,
    )
    if not decision.allowed:
        return _authorization_denied_response(decision)
    if scope_error or not camera_id:
        return _authorization_denied_response(_scope_mismatch_decision(
            decision,
            scope_error or 'record_manage_camera_scope_missing',
        ))
    return None


def _resolve_record_manage_camera():
    view_args = request.view_args or {}
    route_device_id = str(view_args.get('device_id') or '').strip() or None
    requested_camera = (
        request.args.get('camera_id')
        or request.args.get('cameraId')
        or request.args.get('device_id')
        or request.args.get('deviceId')
    )
    if route_device_id:
        if requested_camera and requested_camera != route_device_id:
            return route_device_id, None, 'camera_device_scope_mismatch'
        return route_device_id, None, None

    space_id = view_args.get('space_id')
    if space_id is not None:
        try:
            space = get_record_space(int(space_id))
        except Exception:
            return None, None, 'record_space_camera_scope_missing'
        camera_id = (
            getattr(space, 'device_id', None)
            if space is not None and not isinstance(space, dict)
            else (space or {}).get('device_id')
        )
        camera_id = str(camera_id or '').strip() or None
        owner_tenant_id = str(
            getattr(space, 'tenant_id', None)
            if space is not None and not isinstance(space, dict)
            else (space or {}).get('tenant_id')
        ).strip() or None
        if not camera_id:
            return None, owner_tenant_id, 'record_space_camera_scope_missing'
        if not owner_tenant_id:
            return camera_id, None, 'record_space_tenant_scope_missing'
        if requested_camera and requested_camera != camera_id:
            return camera_id, owner_tenant_id, 'camera_device_scope_mismatch'
        data = request.get_json(silent=True) \
            if request.method in {'POST', 'PUT', 'PATCH', 'DELETE'} else {}
        data = data if isinstance(data, dict) else {}
        object_names = []
        route_object_name = str(view_args.get('object_name') or '').strip()
        if route_object_name:
            object_names.append(route_object_name)
        body_object_names = data.get('object_names') or data.get('objectNames') or []
        if isinstance(body_object_names, (list, tuple)):
            object_names.extend(str(value or '').strip() for value in body_object_names)
        object_names = [value for value in object_names if value]
        if object_names:
            try:
                from models import RecordFile

                for object_name in object_names:
                    record_file = RecordFile.query.filter_by(
                        tenant_id=int(owner_tenant_id),
                        space_id=int(space_id),
                        object_name=object_name,
                    ).first()
                    if record_file is None:
                        return camera_id, owner_tenant_id, 'record_object_camera_scope_missing'
                    record_camera = str(
                        getattr(record_file, 'device_id', '') or ''
                    ).strip()
                    if not record_camera:
                        return camera_id, owner_tenant_id, 'record_object_camera_scope_missing'
                    if record_camera != camera_id:
                        return camera_id, owner_tenant_id, 'record_object_camera_scope_mismatch'
            except (ImportError, AttributeError, TypeError, ValueError):
                return camera_id, owner_tenant_id, 'record_object_camera_scope_missing'
        return camera_id, owner_tenant_id, None

    data = request.get_json(silent=True) if request.method in {'POST', 'PUT', 'PATCH', 'DELETE'} else {}
    data = data if isinstance(data, dict) else {}
    body_camera = (
        data.get('camera_id')
        or data.get('cameraId')
        or data.get('device_id')
        or data.get('deviceId')
    )
    camera_id = str(requested_camera or body_camera or '').strip() or None
    return (
        camera_id,
        None,
        None if camera_id else 'record_manage_camera_scope_missing',
    )


def _authorization_denied_response(decision):
    payload, status = authorization_error(decision)
    return jsonify(payload), status


def _scope_mismatch_decision(decision, reason='camera_device_scope_mismatch', export_id=None,
                             defer_audit=False):
    denied = MediaAuthorizationDecision(
        False,
        decision.user_id,
        decision.tenant_id,
        decision.camera_id,
        decision.action,
        reason,
        403,
        decision.auth_type,
        decision.service_id,
    )
    if not defer_audit:
        append_media_access_audit(denied, resource=request.path, export_id=export_id)
    return denied


def _record_space_list_camera_hint():
    return str(
        request.args.get('camera_id')
        or request.args.get('cameraId')
        or request.args.get('device_id')
        or request.args.get('deviceId')
        or request.headers.get('X-YFeiEye-Service-Camera-Id')
        or request.args.get('yf_camera_id')
        or ''
    ).strip() or None


def _authorize_record_space_list():
    """Resolve a trusted tenant plus the cameras individually allowed to list."""
    camera_hint = _record_space_list_camera_hint()
    scopes = list_record_space_authorization_scopes(camera_hint)
    if not scopes:
        decision = authorize_media_request(
            request,
            action='record_manage',
            camera_id=camera_hint,
            resource=request.path,
        )
        if not decision.allowed:
            return decision, None, []
        if not str(decision.tenant_id or '').isdigit() \
                or int(decision.tenant_id) <= 0:
            return _scope_mismatch_decision(
                decision, 'record_space_tenant_scope_missing'), None, []
        return None, int(decision.tenant_id), [camera_hint] if camera_hint else []

    first_denied = None
    trusted_tenant_id = None
    allowed_camera_ids = []
    for scope in scopes:
        camera_id = str((scope or {}).get('camera_id') or '').strip()
        owner_tenant_id = str((scope or {}).get('tenant_id') or '').strip()
        if not camera_id or not owner_tenant_id:
            continue
        decision = authorize_media_request(
            request,
            action='record_manage',
            camera_id=camera_id,
            resource=request.path,
            owner_tenant_id=owner_tenant_id,
        )
        if not decision.allowed:
            if first_denied is None:
                first_denied = decision
            if decision.status_code != 403:
                return decision, None, []
            continue
        if trusted_tenant_id is None:
            trusted_tenant_id = decision.tenant_id
        elif decision.tenant_id != trusted_tenant_id:
            return _scope_mismatch_decision(
                decision, 'record_space_tenant_scope_ambiguous'), None, []
        allowed_camera_ids.append(camera_id)

    if not allowed_camera_ids:
        if first_denied is not None:
            return first_denied, None, []
        denied = MediaAuthorizationDecision(
            False,
            None,
            None,
            camera_hint,
            'record_manage',
            'record_space_authorization_scope_empty',
            403,
        )
        append_media_access_audit(denied, resource=request.path)
        return denied, None, []
    return None, int(trusted_tenant_id), list(dict.fromkeys(allowed_camera_ids))


def _authorize_record_group_policy(group_type, group_key):
    from app.services.space_group_save_time_service import (
        list_group_record_space_authorization_scopes,
    )

    scopes = list_group_record_space_authorization_scopes(group_type, group_key)
    if not scopes:
        decision = authorize_media_request(
            request,
            action='record_manage',
            camera_id=None,
            resource=request.path,
        )
        if not decision.allowed:
            return decision, None, []
        return _scope_mismatch_decision(
            decision, 'record_group_authorization_scope_empty'), None, []

    trusted_tenant_id = None
    allowed_camera_ids = []
    for scope in scopes:
        camera_id = str((scope or {}).get('camera_id') or '').strip()
        owner_tenant_id = str((scope or {}).get('tenant_id') or '').strip()
        if not camera_id or not owner_tenant_id.isdigit() \
                or int(owner_tenant_id) <= 0:
            decision = MediaAuthorizationDecision(
                False,
                None,
                owner_tenant_id or None,
                camera_id or None,
                'record_manage',
                'record_group_authorization_scope_invalid',
                403,
            )
            append_media_access_audit(decision, resource=request.path)
            return decision, None, []

        decision = authorize_media_request(
            request,
            action='record_manage',
            camera_id=camera_id,
            resource=request.path,
            owner_tenant_id=owner_tenant_id,
        )
        if not decision.allowed:
            return decision, None, []
        if trusted_tenant_id is None:
            trusted_tenant_id = int(decision.tenant_id)
        elif int(decision.tenant_id) != trusted_tenant_id:
            return _scope_mismatch_decision(
                decision, 'record_group_tenant_scope_ambiguous'), None, []
        allowed_camera_ids.append(camera_id)

    return None, trusted_tenant_id, list(dict.fromkeys(allowed_camera_ids))


def _append_export_access_decision(export_id, decision, action, camera_id=None,
                                   reason=None, decision_override=None):
    final_decision = decision_override or ('allowed' if decision.allowed else 'denied')
    final_reason = reason or decision.reason
    decision_id = _export_access_decision_id(export_id, decision, action)
    try:
        stored = append_record_export_access_audit(
            export_id,
            decision=final_decision,
            user_id=decision.user_id,
            tenant_id=decision.tenant_id,
            camera_id=camera_id or decision.camera_id,
            action=action,
            reason=final_reason,
            decision_id=decision_id,
            auth_type=decision.auth_type,
            service_id=decision.service_id,
        )
    except RecordExportAccessDecisionConflictError as exc:
        raise _ExportAccessDecisionConflict(str(exc)) from exc
    except ValueError:
        logger.warning('export access audit could not resolve job %s', export_id)
        try:
            append_media_access_audit(
                decision,
                resource=request.path,
                export_id=export_id,
                reason=final_reason,
                decision_override=final_decision,
                decision_id=decision_id,
            )
        except MediaAccessAuditConflictError as exc:
            raise _ExportAccessDecisionConflict(str(exc)) from exc
        except Exception as exc:
            raise _ExportAccessAuditUnavailable(
                f'export access audit unavailable: {decision_id}') from exc
        return None
    except Exception as exc:
        raise _ExportAccessAuditUnavailable(
            f'export access audit unavailable: {decision_id}') from exc

    stored_decision = stored.get('decision') or final_decision
    stored_reason = stored.get('reason') or final_reason
    stored_authorization = MediaAuthorizationDecision(
        stored_decision == 'allowed',
        stored.get('operator_user_id'),
        stored.get('tenant_id'),
        stored.get('camera_id'),
        stored.get('media_action') or action,
        stored_reason,
        200 if stored_decision == 'allowed' else 403,
        stored.get('auth_type') or decision.auth_type,
        stored.get('service_id') or decision.service_id,
    )
    try:
        append_media_access_audit(
            stored_authorization,
            resource=request.path,
            export_id=export_id,
            reason=stored_reason,
            decision_override=stored_decision,
            decision_id=decision_id,
        )
    except MediaAccessAuditConflictError as exc:
        raise _ExportAccessDecisionConflict(str(exc)) from exc
    except Exception as exc:
        raise _ExportAccessAuditUnavailable(
            f'export access audit unavailable: {decision_id}') from exc
    return stored


def _append_global_export_access_decision(decision, reason=None,
                                          decision_override=None,
                                          export_id=None,
                                          decision_id=None):
    final_decision = decision_override or ('allowed' if decision.allowed else 'denied')
    final_reason = reason or decision.reason
    decision_id = decision_id or _export_access_decision_id(
        'create', decision, 'export')
    try:
        append_media_access_audit(
            decision,
            resource=request.path,
            export_id=export_id,
            reason=final_reason,
            decision_override=final_decision,
            decision_id=decision_id,
        )
    except MediaAccessAuditConflictError as exc:
        raise _ExportAccessDecisionConflict(str(exc)) from exc
    except Exception as exc:
        raise _ExportAccessAuditUnavailable(
            f'export access audit unavailable: {decision_id}') from exc
    return decision_id


def _append_created_export_access_decision(export_id, decision, camera_id=None):
    decision_id = _export_access_decision_id('create', decision, 'export')
    try:
        stored = append_record_export_access_audit(
            export_id,
            decision='allowed',
            user_id=decision.user_id,
            tenant_id=decision.tenant_id,
            camera_id=camera_id or decision.camera_id,
            action='export',
            reason=decision.reason,
            decision_id=decision_id,
            auth_type=decision.auth_type,
            service_id=decision.service_id,
        )
    except RecordExportAccessDecisionConflictError as exc:
        raise _ExportAccessDecisionConflict(str(exc)) from exc
    except Exception as exc:
        raise _ExportAccessAuditUnavailable(
            f'export access audit unavailable: {decision_id}') from exc
    _append_global_export_access_decision(
        decision, export_id=export_id, decision_id=decision_id)
    return stored


def _export_access_audit_error_response(error):
    if isinstance(error, _ExportAccessDecisionConflict):
        return jsonify({
            'code': 409,
            'msg': 'export access decision conflicts with idempotency key',
            'reason': 'export_access_decision_conflict',
        }), 409
    return jsonify({
        'code': 503,
        'msg': 'export access audit unavailable',
        'reason': 'export_audit_unavailable',
    }), 503


def _append_export_access_failure_response(export_id, decision, action, reason):
    try:
        _append_export_access_failure(export_id, decision, action, reason)
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    return None


def _append_global_export_access_failure_response(decision, reason):
    if decision is None:
        return None
    try:
        _append_global_export_access_decision(
            decision,
            reason=reason,
            decision_override='denied',
        )
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    return None


def _export_access_decision_id(export_id, decision, action):
    operation_ids = getattr(g, '_export_access_operation_ids', None)
    if operation_ids is None:
        operation_ids = {}
        g._export_access_operation_ids = operation_ids
    operation_key = f'{export_id}:{action}'
    if operation_key not in operation_ids:
        supplied = (
            request.headers.get('Idempotency-Key')
            or request.headers.get('X-Request-Id')
        )
        operation_ids[operation_key] = str(supplied).strip() if supplied else uuid.uuid4().hex
    identity = {
        'operationId': operation_ids[operation_key],
        'method': request.method,
        'resource': request.path,
        'exportId': str(export_id),
        'action': str(action),
        'userId': str(decision.user_id or ''),
        'tenantId': str(decision.tenant_id or ''),
        'cameraId': str(decision.camera_id or ''),
        'authType': str(decision.auth_type or ''),
        'serviceId': str(decision.service_id or ''),
    }
    digest = hashlib.sha256(json.dumps(
        identity, ensure_ascii=True, separators=(',', ':'), sort_keys=True,
    ).encode('utf-8')).hexdigest()
    return f'export-access-{digest}'


def _append_export_access_failure(export_id, decision, action, reason):
    if decision is None:
        return
    _append_export_access_decision(
        export_id,
        decision,
        action,
        reason=reason,
        decision_override='denied',
    )


def _authorize_export_access(export_id, action, defer_allowed_audit=False):
    camera_hint = (
        request.args.get('camera_id')
        or request.args.get('cameraId')
        or request.headers.get('X-YFeiEye-Service-Camera-Id')
    )
    decision = authorize_media_request(
        request,
        action=action,
        camera_id=camera_hint,
        resource=request.path,
        export_id=export_id,
        defer_audit=True,
    )
    if not decision.allowed:
        _append_export_access_decision(export_id, decision, action)
        return decision
    try:
        manifest = get_record_export_manifest(export_id)
    except ValueError:
        _append_export_access_decision(
            export_id,
            decision,
            action,
            reason='export_not_found',
            decision_override='denied',
        )
        raise
    except Exception:
        _append_export_access_decision(
            export_id,
            decision,
            action,
            reason='export_integrity_error',
            decision_override='denied',
        )
        raise
    camera_id = str(manifest.get('cameraId') or '').strip() or None
    tenant_id = str(manifest.get('tenantId') or '').strip() or None
    if not camera_id or camera_id != decision.camera_id:
        decision = _scope_mismatch_decision(
            decision, 'camera_scope_denied', export_id, defer_audit=True)
    elif not tenant_id or tenant_id != decision.tenant_id:
        decision = _scope_mismatch_decision(
            decision, 'tenant_scope_denied', export_id, defer_audit=True)
    if not decision.allowed or not defer_allowed_audit:
        _append_export_access_decision(
            export_id, decision, action, camera_id=camera_id)
    return decision


def _derive_export_camera(data):
    explicit = (
        data.get('camera_id')
        or data.get('cameraId')
        or data.get('device_id')
        or data.get('deviceId')
    )
    if explicit:
        return str(explicit).strip() or None

    uris = []
    primary = data.get('record_uri') or data.get('recordUri')
    if primary:
        uris.append(primary)
    raw_uris = data.get('record_uris') or data.get('recordUris') or []
    if isinstance(raw_uris, (list, tuple)):
        uris.extend(raw_uris)
    segments = data.get('record_segments') or data.get('recordSegments') or []
    if isinstance(segments, (list, tuple)):
        for segment in segments:
            if isinstance(segment, dict):
                uri = segment.get('record_uri') or segment.get('recordUri') or segment.get('uri')
                if uri:
                    uris.append(uri)
    uris = list(dict.fromkeys(str(value or '').strip() for value in uris if str(value or '').strip()))
    if not uris:
        return None

    try:
        from models import RecordFile, RecordSpace
    except (ImportError, AttributeError):
        return None
    cameras = set()
    for uri in uris:
        match = re.search(
            r'/video/record/space/(\d+)/video/(.+)$',
            unquote(urlparse(uri).path),
        )
        if not match:
            return None
        space_id = int(match.group(1))
        object_name = match.group(2)
        try:
            space = RecordSpace.query.get(space_id)
            camera_id = str(getattr(space, 'device_id', '') or '').strip()
            tenant_id = getattr(space, 'tenant_id', None)
            record_file = RecordFile.query.filter_by(
                tenant_id=tenant_id,
                space_id=space_id,
                device_id=camera_id,
                object_name=object_name,
            ).first()
            record_camera = str(getattr(record_file, 'device_id', '') or '').strip()
            record_tenant = getattr(record_file, 'tenant_id', None)
        except Exception:
            return None
        if (not camera_id or not record_camera or record_camera != camera_id
                or tenant_id is None or record_tenant != tenant_id):
            return None
        cameras.add(camera_id)
    return cameras.pop() if len(cameras) == 1 else None


# ====================== 监控录像空间管理接口 ======================
@record_bp.route('/space/list', methods=['GET'])
def list_spaces():
    """查询监控录像空间列表"""
    try:
        denied, tenant_id, camera_ids = _authorize_record_space_list()
        if denied is not None:
            return _authorization_denied_response(denied)
        page_no = int(request.args.get('pageNo', 1))
        page_size = int(request.args.get('pageSize', 10))
        search = request.args.get('search', '').strip() or None
        parent_key = request.args.get('parentKey', 'root').strip() or 'root'
        scope = request.args.get('scope', '').strip() or None

        if camera_ids:
            result = list_record_spaces(
                page_no,
                page_size,
                search,
                parent_key,
                scope,
                tenant_id=tenant_id,
                camera_ids=camera_ids,
            )
        else:
            result = {
                'items': [],
                'total': 0,
                'parent_key': 'root',
                'breadcrumbs': [{'key': 'root', 'name': '全部空间'}],
            }
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result['items'],
            'total': result['total'],
            'parent_key': result.get('parent_key', 'root'),
            'breadcrumbs': result.get('breadcrumbs', []),
            'is_search': result.get('is_search', False),
            'scope': result.get('scope'),
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'查询监控录像空间列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>', methods=['GET'])
def get_space(space_id):
    """获取监控录像空间详情"""
    try:
        space = get_record_space(space_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': space.to_dict()
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取监控录像空间失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/device/<device_id>', methods=['GET'])
def get_space_by_device(device_id):
    """根据设备ID获取监控录像空间"""
    try:
        decision = authorize_media_request(
            request,
            action='coverage',
            camera_id=device_id,
            resource=request.path,
        )
        if not decision.allowed:
            return _authorization_denied_response(decision)
        space = get_record_space_by_device_id(
            device_id, tenant_id=decision.tenant_id)
        if not space:
            return jsonify({
                'code': 400,
                'msg': f'设备 {device_id} 没有关联的监控录像空间'
            }), 400
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': space.to_dict()
        })
    except Exception as e:
        logger.error(f'根据设备ID获取监控录像空间失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space', methods=['POST'])
def create_space():
    """创建监控录像空间（已禁用：监控录像空间现在跟随设备自动创建）"""
    return jsonify({
        'code': 403,
        'msg': '监控录像空间不能手动创建，系统会在创建设备时自动创建监控录像空间'
    }), 403


@record_bp.route('/space/<int:space_id>', methods=['PUT'])
def update_space(space_id):
    """更新监控录像空间"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        space_name = data.get('space_name', '').strip() if 'space_name' in data else None
        save_mode = data.get('save_mode') if 'save_mode' in data else None
        save_time = data.get('save_time') if 'save_time' in data else None
        save_time_custom = data.get('save_time_custom') if 'save_time_custom' in data else None
        description = data.get('description', '').strip() if 'description' in data else None
        
        try:
            space = update_record_space(
                space_id, space_name, save_mode, save_time, description, save_time_custom,
            )
        except ValueError as ve:
            return jsonify({'code': 400, 'msg': str(ve)}), 400
        return jsonify({
            'code': 0,
            'msg': '监控录像空间更新成功',
            'data': space.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'更新监控录像空间失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/group-policy', methods=['PUT'])
def update_group_policy():
    """更新 NVR / GB28181 分组默认录像保存时间，联动非自定义子设备。"""
    try:
        data = request.get_json() or {}
        group_type = (data.get('group_type') or '').strip().lower()
        group_key = str(data.get('group_key') or '').strip()
        save_time = data.get('save_time')
        if save_time is None:
            return jsonify({'code': 400, 'msg': 'save_time 不能为空'}), 400

        denied, tenant_id, camera_ids = _authorize_record_group_policy(
            group_type, group_key)
        if denied is not None:
            return _authorization_denied_response(denied)

        from app.services.space_group_save_time_service import update_group_save_time
        from app.services.space_save_time_service import SPACE_KIND_RECORD

        policy, updated = update_group_save_time(
            group_type,
            group_key,
            SPACE_KIND_RECORD,
            save_time,
            tenant_id=tenant_id,
            camera_ids=camera_ids,
        )
        return jsonify({
            'code': 0,
            'msg': f'分组存储策略已更新，已同步 {updated} 个非自定义设备空间',
            'data': {
                'group_type': policy.group_type,
                'group_key': policy.group_key,
                'save_time': policy.record_save_time,
                'updated_count': updated,
            },
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'更新分组录像存储策略失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>', methods=['DELETE'])
def delete_space(space_id):
    """删除监控录像空间"""
    try:
        delete_record_space(space_id)
        return jsonify({
            'code': 0,
            'msg': '监控录像空间删除成功'
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'删除监控录像空间失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/sync/minio', methods=['POST'])
def sync_spaces_minio():
    """同步当前用户获准摄像头的录像空间到 MinIO。"""
    try:
        denied, tenant_id, camera_ids = _authorize_record_space_list()
        if denied is not None:
            return _authorization_denied_response(denied)
        if not camera_ids:
            result = {
                'total_spaces': 0,
                'created_count': 0,
                'skipped_count': 0,
                'error_count': 0,
            }
        else:
            result = sync_spaces_to_minio(
                tenant_id=tenant_id,
                camera_ids=camera_ids,
            )
        return jsonify({
            'code': 0,
            'msg': '同步完成',
            'data': result
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'同步监控录像空间到Minio失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


# ====================== 监控录像管理接口 ======================
@record_bp.route('/space/<int:space_id>/videos/dates', methods=['GET'])
def list_video_dates(space_id):
    """列出有录像的日期"""
    try:
        device_id = request.args.get('device_id')
        dates = list_record_video_dates(space_id, device_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': dates,
        })
    except Exception as e:
        logger.error(f'获取录像日期列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>/videos/day', methods=['GET'])
def list_videos_by_day(space_id):
    """获取指定日期的录像片段详情（含时间轴与告警关联）"""
    try:
        date_str = request.args.get('date', '').strip()
        if not date_str:
            return jsonify({'code': 400, 'msg': 'date 参数不能为空（格式 YYYY-MM-DD）'}), 400
        requested_device_id = request.args.get('device_id') or request.args.get('deviceId')
        space = get_record_space(space_id)
        space_device_id = (
            getattr(space, 'device_id', None)
            if space is not None and not isinstance(space, dict)
            else (space or {}).get('device_id')
        )
        device_id = str(space_device_id or '').strip() or None
        decision = authorize_media_request(
            request,
            action='coverage',
            camera_id=device_id,
            resource=request.path,
            owner_tenant_id=getattr(space, 'tenant_id', None),
        )
        if not decision.allowed:
            return _authorization_denied_response(decision)
        if not device_id:
            return _authorization_denied_response(_scope_mismatch_decision(
                decision, 'record_space_camera_scope_missing'))
        if requested_device_id and requested_device_id != device_id:
            return _authorization_denied_response(_scope_mismatch_decision(decision))
        result = list_record_videos_day_detail(
            space_id, date_str, device_id,
            tenant_id=getattr(space, 'tenant_id', None),
        )
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取日录像详情失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/device/<device_id>/resolve-alert', methods=['GET'])
def resolve_alert_segment(device_id):
    """根据告警 ID 定位录像片段（供告警页跳转回放）"""
    try:
        decision = authorize_media_request(
            request,
            action='coverage',
            camera_id=device_id,
            resource=request.path,
        )
        if not decision.allowed:
            return _authorization_denied_response(decision)
        alert_id = request.args.get('alert_id') or request.args.get('alertId')
        if not alert_id:
            return jsonify({'code': 400, 'msg': 'alert_id 参数不能为空'}), 400
        result = find_segment_for_alert(
            device_id, int(alert_id), tenant_id=decision.tenant_id)
        if not result:
            return jsonify({'code': 404, 'msg': '未找到告警或关联录像空间'}), 404
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'定位告警录像片段失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/availability', methods=['GET'])
def record_availability():
    """Query recording coverage for a review incident window."""
    try:
        device_id = request.args.get('device_id') or request.args.get('deviceId')
        camera_id = request.args.get('camera_id') or request.args.get('cameraId')
        scoped_camera_id = camera_id or device_id
        decision = authorize_media_request(
            request,
            action='coverage',
            camera_id=scoped_camera_id,
            resource=request.path,
        )
        if not decision.allowed:
            return _authorization_denied_response(decision)
        if not scoped_camera_id:
            return _authorization_denied_response(_scope_mismatch_decision(
                decision, 'record_camera_scope_missing'))
        if camera_id and device_id and camera_id != device_id:
            return _authorization_denied_response(_scope_mismatch_decision(decision))
        begin_time = (
            request.args.get('begin_time')
            or request.args.get('beginTime')
            or request.args.get('start_time')
            or request.args.get('startTime')
            or request.args.get('after')
        )
        end_time = (
            request.args.get('end_time')
            or request.args.get('endTime')
            or request.args.get('stop_time')
            or request.args.get('stopTime')
            or request.args.get('before')
        )
        result = query_recording_availability(
            device_id=device_id,
            camera_id=camera_id,
            tenant_id=decision.tenant_id,
            begin_time=begin_time,
            end_time=end_time,
            alert_time=request.args.get('alert_time') or request.args.get('alertTime'),
            time_range=request.args.get('time_range') or request.args.get('timeRange'),
        )
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'查询录像覆盖度失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/export', methods=['POST'])
def export_record():
    """Create a review evidence record export task."""
    decision = None
    try:
        data = request.get_json() or {}
        camera_id = _derive_export_camera(data)
        decision = authorize_media_request(
            request,
            action='export',
            camera_id=camera_id,
            resource=request.path,
            defer_audit=True,
        )
        if not decision.allowed:
            _append_global_export_access_decision(decision)
            return _authorization_denied_response(decision)
        if not camera_id:
            denied = _scope_mismatch_decision(
                decision, 'record_export_camera_scope_missing', defer_audit=True)
            _append_global_export_access_decision(denied)
            return _authorization_denied_response(denied)
        device_id = data.get('device_id') or data.get('deviceId')
        explicit_camera_id = data.get('camera_id') or data.get('cameraId')
        if explicit_camera_id and device_id and explicit_camera_id != device_id:
            denied = _scope_mismatch_decision(decision, defer_audit=True)
            _append_global_export_access_decision(denied)
            return _authorization_denied_response(denied)
        for key in _EXPORT_IDENTITY_KEYS | _EXPORT_POLICY_KEYS:
            data.pop(key, None)
        if decision.auth_type != 'service_hmac':
            for key in _EXPORT_SERVICE_ONLY_KEYS:
                data.pop(key, None)
        data['operator_user_id'] = decision.user_id
        data['approved_by'] = decision.user_id
        data['approved_at'] = datetime.now(timezone.utc).isoformat()
        data['tenant_id'] = decision.tenant_id
        if not data.get('camera_id') and not data.get('cameraId'):
            data['camera_id'] = camera_id
        if not data.get('device_id') and not data.get('deviceId'):
            data['device_id'] = camera_id
        validate_record_export_request(data, camera_id)
        result = create_record_export(data, async_worker=True)
        _append_created_export_access_decision(
            result.get('export_id'), decision, camera_id=camera_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    except ValueError as e:
        audit_response = _append_global_export_access_failure_response(
            decision, 'export_create_failed')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        audit_response = _append_global_export_access_failure_response(
            decision, 'export_create_failed')
        if audit_response is not None:
            return audit_response
        logger.error(f'\u521b\u5efa\u590d\u6838\u8bc1\u636e\u5f55\u50cf\u5bfc\u51fa\u4efb\u52a1\u5931\u8d25: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'\u670d\u52a1\u5668\u5185\u90e8\u9519\u8bef: {str(e)}'}), 500


@record_bp.route('/export/<export_id>', methods=['GET'])
def get_record_export(export_id):
    """Poll a review evidence record export task."""
    decision = None
    try:
        decision = _authorize_export_access(
            export_id, 'export', defer_allowed_audit=True)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        result = get_record_export_status(export_id)
        response = jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
        _append_export_access_decision(export_id, decision, 'export')
        return response
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    except ValueError as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'export', 'export_status_failed')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 404, 'msg': str(e)}), 404
    except Exception as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'export', 'export_status_failed')
        if audit_response is not None:
            return audit_response
        logger.error(f'查询复核证据录像导出任务失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/export/<export_id>/retry', methods=['POST'])
def retry_record_export_job(export_id):
    """Requeue a failed review evidence record export task."""
    decision = None
    try:
        decision = _authorize_export_access(
            export_id, 'export', defer_allowed_audit=True)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        result = retry_record_export(export_id)
        response = jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
        _append_export_access_decision(export_id, decision, 'export')
        return response
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    except ValueError as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'export', 'export_retry_failed')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 404, 'msg': str(e)}), 404
    except Exception as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'export', 'export_retry_failed')
        if audit_response is not None:
            return audit_response
        logger.error(f'retry review evidence record export failed: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'server error: {str(e)}'}), 500


@record_bp.route('/export/<export_id>/audit', methods=['GET'])
def get_record_export_audit_entries(export_id):
    """List review evidence record export audit entries."""
    decision = None
    try:
        decision = _authorize_export_access(
            export_id, 'manifest_verify', defer_allowed_audit=True)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        result = get_record_export_audit(export_id)
        response = jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
        _append_export_access_decision(
            export_id, decision, 'manifest_verify')
        return response
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    except ValueError as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'manifest_verify', 'export_audit_read_failed')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 404, 'msg': str(e)}), 404
    except Exception as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'manifest_verify', 'export_audit_read_failed')
        if audit_response is not None:
            return audit_response
        logger.error(f'list review evidence record export audit failed: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'server error: {str(e)}'}), 500


@record_bp.route('/export/<export_id>/manifest', methods=['GET'])
def get_record_export_manifest_file(export_id):
    """Return the persistent review evidence record export manifest."""
    decision = None
    try:
        decision = _authorize_export_access(
            export_id, 'manifest_verify', defer_allowed_audit=True)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        result = get_record_export_manifest(export_id)
        response = jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
        _append_export_access_decision(
            export_id, decision, 'manifest_verify')
        return response
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    except ValueError as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'manifest_verify', 'export_manifest_failed')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 404, 'msg': str(e)}), 404
    except Exception as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'manifest_verify', 'export_manifest_failed')
        if audit_response is not None:
            return audit_response
        logger.error(f'get review evidence record export manifest failed: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'server error: {str(e)}'}), 500


@record_bp.route('/export/<export_id>/download', methods=['GET'])
def download_record_export_file(export_id):
    """Download a generated review evidence record export."""
    decision = None
    try:
        decision = _authorize_export_access(
            export_id, 'download', defer_allowed_audit=True)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        result = download_record_export(
            export_id,
            operator_user_id=decision.user_id,
            reason=request.args.get('reason'),
        )
        if result.get('path'):
            response = send_file(
                result['path'],
                mimetype=result.get('mimetype') or 'application/octet-stream',
                as_attachment=True,
                download_name=result.get('filename') or f'{export_id}.mp4',
                conditional=True,
            )
            if result.get('temporary_path'):
                temporary_path = result['path']
                response.call_on_close(
                    lambda: _remove_temporary_download(temporary_path))
            _append_export_access_decision(export_id, decision, 'download')
            return response
        stream = result.get('stream')
        if stream is None:
            raise ValueError(f'export content not found: {export_id}')
        filename = result.get('filename') or f'{export_id}.mp4'
        response = Response(
            stream_with_context(_iter_object_stream(stream)),
            mimetype=result.get('mimetype') or 'application/octet-stream',
            direct_passthrough=True,
        )
        response.headers['Content-Disposition'] = f'attachment; filename="{filename}"'
        if result.get('content_length') is not None:
            response.content_length = int(result['content_length'])
        _append_export_access_decision(export_id, decision, 'download')
        return response
    except (_ExportAccessAuditUnavailable, _ExportAccessDecisionConflict) as error:
        return _export_access_audit_error_response(error)
    except RecordExportExpiredError:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'download', 'export_expired')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 410, 'msg': 'export expired', 'reason': 'export_expired'}), 410
    except RecordExportIntegrityError:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'download', 'export_integrity_error')
        if audit_response is not None:
            return audit_response
        return jsonify({
            'code': 500,
            'msg': 'export integrity verification failed',
            'reason': 'export_integrity_error',
        }), 500
    except ValueError as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'download', 'export_download_failed')
        if audit_response is not None:
            return audit_response
        return jsonify({'code': 404, 'msg': str(e)}), 404
    except Exception as e:
        audit_response = _append_export_access_failure_response(
            export_id, decision, 'download', 'export_download_failed')
        if audit_response is not None:
            return audit_response
        logger.error(f'下载复核证据录像导出任务失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


def _iter_object_stream(stream, chunk_size=1024 * 1024):
    try:
        while True:
            chunk = stream.read(chunk_size)
            if not chunk:
                break
            yield chunk
    finally:
        close = getattr(stream, 'close', None)
        if callable(close):
            close()
        release = getattr(stream, 'release_conn', None)
        if callable(release):
            release()


def _remove_temporary_download(path):
    try:
        os.remove(path)
    except FileNotFoundError:
        pass


@record_bp.route('/space/<int:space_id>/videos', methods=['GET'])
def list_videos(space_id):
    """获取监控录像列表"""
    try:
        device_id = request.args.get('device_id')
        page_no = int(request.args.get('pageNo', 1))
        page_size = int(request.args.get('pageSize', 20))
        search = request.args.get('search', '').strip() or None
        start_time = request.args.get('startTime')
        end_time = request.args.get('endTime')

        from datetime import datetime
        start_dt = datetime.fromisoformat(start_time) if start_time else None
        end_dt = datetime.fromisoformat(end_time) if end_time else None

        result = list_record_videos(space_id, device_id, page_no, page_size, search, start_dt, end_dt)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result['items'],
            'total': result['total']
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取监控录像列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>/video/<path:object_name>', methods=['GET'])
def get_video(space_id, object_name):
    """获取监控录像内容"""
    try:
        space = get_record_space(space_id)
        camera_id = (
            getattr(space, 'device_id', None)
            if space is not None and not isinstance(space, dict)
            else (space or {}).get('device_id')
        )
        decision = authorize_media_request(
            request,
            action='playback',
            camera_id=camera_id,
            resource=request.path,
            owner_tenant_id=(
                getattr(space, 'tenant_id', None)
                if space is not None and not isinstance(space, dict)
                else (space or {}).get('tenant_id')
            ),
            defer_audit=True,
        )
        audit_media_response(decision, resource=request.path)
        if not decision.allowed:
            return _authorization_denied_response(decision)
        from models import RecordFile
        record_file = RecordFile.query.filter_by(
            tenant_id=int(getattr(space, 'tenant_id')),
            space_id=space_id,
            device_id=camera_id,
            object_name=object_name,
        ).first()
        if record_file is None:
            return jsonify({
                'code': 404,
                'msg': 'record object not found for authorized camera',
                'reason': 'record_object_metadata_mismatch',
            }), 404
        if str(request.args.get('playback_format') or '').strip().lower() == 'mp4':
            max_input_bytes = int(os.environ.get(
                'YFEIEYE_RECORD_EXPORT_MAX_INPUT_BYTES', '2147483648'))
            source_size_bytes = int(record_file.file_size or max_input_bytes)
            tenant_id = int(getattr(space, 'tenant_id'))
            source_identity = (
                f'tenant:{tenant_id}:space:{space_id}:object:{object_name}:'
                f'etag:{getattr(record_file, "etag", "") or ""}:'
                f'size:{source_size_bytes}'
            )

            def materialize_source(destination):
                return materialize_record_video(
                    space_id,
                    object_name,
                    destination,
                    max_bytes=max_input_bytes,
                    tenant_id=tenant_id,
                )

            prepared = prepare_seekable_mp4_path(
                acquire_lease=True,
                source_identity=source_identity,
                source_size_bytes=source_size_bytes,
                materialize_source=materialize_source,
            )
            lease = prepared['lease']
            try:
                response = send_file(
                    prepared['path'],
                    mimetype='video/mp4',
                    as_attachment=False,
                    download_name=os.path.basename(prepared['path']),
                    conditional=True,
                )
            except Exception:
                release_seekable_playback_lease(lease)
                raise
            response.call_on_close(
                lambda lease=lease: release_seekable_playback_lease(lease))
            return response
        content, content_type, filename = get_record_video(
            space_id,
            object_name,
            tenant_id=getattr(space, 'tenant_id', None),
        )
        return send_file(
            BytesIO(content),
            mimetype=content_type,
            as_attachment=False,
            download_name=filename
        )
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取监控录像失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>/videos', methods=['DELETE'])
def delete_videos(space_id):
    """批量删除监控录像"""
    try:
        data = request.get_json()
        if not data or 'object_names' not in data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空，需要提供 object_names 数组'}), 400
        
        object_names = data.get('object_names', [])
        if not isinstance(object_names, list) or len(object_names) == 0:
            return jsonify({'code': 400, 'msg': 'object_names 必须是非空数组'}), 400
        
        result = delete_record_videos(space_id, object_names)
        return jsonify({
            'code': 0,
            'msg': '删除成功',
            'data': result
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'批量删除监控录像失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>/videos/sync', methods=['POST'])
def sync_videos_metadata(space_id):
    """从 MinIO 同步录像元数据到数据库（历史数据回填）"""
    try:
        result = sync_record_videos_metadata(space_id)
        return jsonify({
            'code': 0,
            'msg': '同步完成',
            'data': result
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'同步录像元数据失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>/videos/drift', methods=['GET'])
def inspect_videos_storage_drift(space_id):
    """Inspect recording DB/disk drift for review evidence reliability."""
    try:
        retention_hours = request.args.get('retention_hours') or request.args.get('retentionHours')
        cursor = request.args.get('cursor')
        limit = int(request.args.get('limit') or 200)
        cache_cursor = request.args.get('cache_cursor') or request.args.get('cacheCursor')
        cache_limit = int(request.args.get('cache_limit') or request.args.get('cacheLimit') or 200)
        requested_device_id = request.args.get('device_id') or request.args.get('deviceId')
        space = get_record_space(space_id)
        space_device_id = (
            getattr(space, 'device_id', None)
            if space is not None and not isinstance(space, dict)
            else (space or {}).get('device_id')
        )
        scoped_camera_id = space_device_id or requested_device_id
        decision = authorize_media_request(
            request,
            action='coverage',
            camera_id=scoped_camera_id,
            resource=request.path,
            owner_tenant_id=(
                getattr(space, 'tenant_id', None)
                if space is not None and not isinstance(space, dict)
                else (space or {}).get('tenant_id')
            ),
        )
        if not decision.allowed:
            return _authorization_denied_response(decision)
        if not space_device_id:
            return _authorization_denied_response(_scope_mismatch_decision(
                decision, 'record_space_camera_scope_missing'))
        if requested_device_id and requested_device_id != space_device_id:
            return _authorization_denied_response(_scope_mismatch_decision(decision))
        device_id = str(space_device_id)
        cache_flush_page = list_record_cache_flush_failures(
            space_id=space_id,
            device_id=device_id,
            tenant_id=decision.tenant_id,
            cursor=cache_cursor,
            limit=cache_limit,
            retention_hours=int(retention_hours) if retention_hours else None,
            return_page=True,
        )
        if isinstance(cache_flush_page, dict):
            cache_flush_events = cache_flush_page.get('items') or []
        else:
            cache_flush_events = cache_flush_page or []
            cache_flush_page = {
                'items': cache_flush_events,
                'next_cursor': None,
                'has_more': False,
                'limit': cache_limit,
            }
        result = inspect_recording_storage_drift(
            space_id=space_id,
            device_id=device_id,
            tenant_id=getattr(space, 'tenant_id', None),
            retention_hours=int(retention_hours) if retention_hours else None,
            cache_flush_events=cache_flush_events,
            cursor=cursor,
            limit=limit,
        )
        result['cache_flush_pagination'] = {
            key: cache_flush_page.get(key)
            for key in ('next_cursor', 'has_more', 'limit')
        }
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result,
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'inspect recording storage drift failed: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'server error: {str(e)}'}), 500


@record_bp.route('/space/<int:space_id>/videos/cleanup', methods=['POST'])
def cleanup_videos(space_id):
    """清理过期的监控录像"""
    try:
        data = request.get_json()
        if not data or 'save_time_hours' not in data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空，需要提供 save_time_hours 参数'}), 400

        save_time_hours = int(data.get('save_time_hours', 0))
        if save_time_hours <= 0:
            return jsonify({'code': 400, 'msg': 'save_time_hours 必须大于 0'}), 400

        result = cleanup_old_videos_by_save_time(space_id, save_time_hours)
        return jsonify({
            'code': 0,
            'msg': '清理完成',
            'data': result
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'清理过期监控录像失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500
