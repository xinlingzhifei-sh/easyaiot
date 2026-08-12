"""
抓拍空间和任务管理路由
@author reese
@email reese
"""
import logging
from flask import Blueprint, g, request, jsonify

from models import db, DetectionRegion
from app.services.snap_space_service import (
    create_snap_space, update_snap_space, delete_snap_space,
    get_snap_space, list_snap_spaces, get_snap_space_by_device_id,
    sync_spaces_to_minio,
)
from app.services.snap_task_service import (
    create_snap_task, update_snap_task, delete_snap_task,
    get_snap_task, list_snap_tasks, start_task, stop_task, restart_task, get_task_logs
)
from app.services.algorithm_service import (
    create_task_algorithm_service, update_task_algorithm_service, delete_task_algorithm_service,
    get_task_algorithm_services, create_region_algorithm_service, update_region_algorithm_service,
    delete_region_algorithm_service, get_region_algorithm_services
)
from app.services.storage_service import (
    get_or_create_device_storage_config, update_device_storage_config,
    get_device_storage_info, check_and_cleanup_storage
)
from app.services.snap_image_service import (
    list_snap_images, delete_snap_images, get_snap_image, cleanup_old_images_by_save_time,
    sync_snap_images_metadata,
)
from app.services.media_authorization_service import (
    MediaAuthorizationDecision,
    append_media_access_audit,
    audit_media_response,
    authorization_error,
    authorize_media_request,
)

snap_bp = Blueprint('snap', __name__)
logger = logging.getLogger(__name__)

_MULTI_SCOPE_ENDPOINTS = {
    'snap.list_spaces',
    'snap.sync_spaces_minio',
    'snap.list_tasks',
    'snap.update_group_policy',
}


def _authorization_denied_response(decision):
    payload, status = authorization_error(decision)
    return jsonify(payload), status


def _scope_mismatch_decision(decision, reason, status_code=403, audit=True):
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
    if audit:
        append_media_access_audit(denied, resource=request.path)
    return denied


def _request_data():
    if request.method not in {'POST', 'PUT', 'PATCH', 'DELETE'}:
        return {}
    data = request.get_json(silent=True)
    return data if isinstance(data, dict) else {}


def _requested_camera_id(data=None):
    data = data or {}
    return str(
        request.args.get('camera_id')
        or request.args.get('cameraId')
        or request.args.get('device_id')
        or request.args.get('deviceId')
        or data.get('camera_id')
        or data.get('cameraId')
        or data.get('device_id')
        or data.get('deviceId')
        or ''
    ).strip() or None


def _space_scope(space):
    if space is None:
        return None, None, 'snap_space_scope_missing'
    camera_id = str(getattr(space, 'device_id', '') or '').strip() or None
    tenant_id = getattr(space, 'tenant_id', None)
    tenant_text = str(tenant_id or '').strip()
    if not camera_id:
        return None, tenant_id, 'snap_camera_scope_missing'
    if not tenant_text.isdigit() or int(tenant_text) <= 0:
        return camera_id, None, 'snap_tenant_scope_missing'
    return camera_id, int(tenant_text), None


def _task_scope(task):
    if task is None:
        return None, None, None, 'snap_task_scope_missing'
    space = get_snap_space(int(getattr(task, 'space_id', 0) or 0))
    camera_id, tenant_id, error = _space_scope(space)
    if error:
        return space, camera_id, tenant_id, error
    task_camera = str(getattr(task, 'device_id', '') or '').strip()
    if not task_camera or task_camera != camera_id:
        return space, camera_id, tenant_id, 'snap_task_camera_scope_mismatch'
    return space, camera_id, tenant_id, None


def _algorithm_task_scope(task):
    if task is None or str(getattr(task, 'task_type', '') or '') != 'snap':
        return None, None, None, 'snap_algorithm_task_scope_missing'
    space_id = getattr(task, 'space_id', None)
    if space_id is None:
        return None, None, None, 'snap_algorithm_task_space_scope_missing'
    space = get_snap_space(int(space_id))
    camera_id, tenant_id, error = _space_scope(space)
    if error:
        return space, camera_id, tenant_id, error
    task_cameras = {
        str(getattr(device, 'id', '') or '').strip()
        for device in (getattr(task, 'devices', None) or [])
        if str(getattr(device, 'id', '') or '').strip()
    }
    if camera_id not in task_cameras:
        return space, camera_id, tenant_id, 'snap_algorithm_task_camera_scope_mismatch'
    return space, camera_id, tenant_id, None


def _region_task_scope(task_id):
    from models import AlgorithmTask, SnapTask

    candidates = []
    snap_task = SnapTask.query.get(int(task_id))
    if snap_task is not None:
        candidates.append(_task_scope(snap_task))
    algorithm_task = AlgorithmTask.query.get(int(task_id))
    if algorithm_task is not None:
        candidates.append(_algorithm_task_scope(algorithm_task))
    valid = [candidate for candidate in candidates if candidate[3] is None]
    if not valid:
        return None, None, None, 'snap_region_task_scope_missing'
    owners = {(candidate[1], candidate[2]) for candidate in valid}
    if len(owners) != 1:
        return None, None, None, 'snap_region_task_scope_ambiguous'
    return valid[0]


def _space_for_device(device_id):
    from app.services.snap_space_service import list_snap_space_authorization_scopes

    scopes = list_snap_space_authorization_scopes(device_id)
    if len(scopes) != 1:
        return None
    return get_snap_space(int(scopes[0]['space_id']))


def _resolve_snap_scope():
    endpoint = request.endpoint or ''
    view_args = request.view_args or {}
    data = _request_data()
    requested_camera = _requested_camera_id(data)
    space = None
    camera_id = None
    tenant_id = None
    error = None
    region_task_scope = False
    algorithm_task_scope = endpoint in {
        'snap.list_task_services',
        'snap.create_task_service',
        'snap.update_task_service',
        'snap.delete_task_service',
    }

    service_id = view_args.get('service_id')
    region_id = view_args.get('region_id')
    task_id = view_args.get('task_id')
    space_id = view_args.get('space_id')
    route_device_id = str(view_args.get('device_id') or '').strip() or None

    if service_id is not None and endpoint in {
            'snap.update_task_service', 'snap.delete_task_service'}:
        from models import AlgorithmModelService

        service = AlgorithmModelService.query.get(int(service_id))
        task_id = getattr(service, 'task_id', None) if service else None
        if task_id is None:
            error = 'snap_task_service_scope_missing'
    elif service_id is not None and endpoint in {
            'snap.update_region_service', 'snap.delete_region_service'}:
        from models import RegionModelService

        service = RegionModelService.query.get(int(service_id))
        region_id = getattr(service, 'region_id', None) if service else None
        if region_id is None:
            error = 'snap_region_service_scope_missing'

    if not error and region_id is not None:
        region = DetectionRegion.query.get(int(region_id))
        task_id = getattr(region, 'task_id', None) if region else None
        region_task_scope = task_id is not None
        if task_id is None:
            error = 'snap_region_scope_missing'

    if not error and task_id is None and endpoint == 'snap.create_region':
        task_id = data.get('task_id')
        region_task_scope = task_id is not None
        if task_id is None:
            error = 'snap_task_scope_missing'

    if not error and task_id is not None:
        if region_task_scope:
            space, camera_id, tenant_id, error = _region_task_scope(task_id)
        elif algorithm_task_scope:
            from models import AlgorithmTask

            task = AlgorithmTask.query.get(int(task_id))
            space, camera_id, tenant_id, error = _algorithm_task_scope(task)
        else:
            from models import SnapTask

            task = SnapTask.query.get(int(task_id))
            space, camera_id, tenant_id, error = _task_scope(task)
        requested_space_id = data.get('space_id')
        if not error and requested_space_id is not None \
                and int(requested_space_id) != int(getattr(space, 'id')):
            error = 'snap_space_scope_mismatch'
    elif not error and space_id is None and endpoint == 'snap.create_task':
        space_id = data.get('space_id')
        if space_id is None:
            error = 'snap_space_scope_missing'

    if not error and camera_id is None and space_id is not None:
        space = get_snap_space(int(space_id))
        camera_id, tenant_id, error = _space_scope(space)

    if not error and camera_id is None and route_device_id:
        space = _space_for_device(route_device_id)
        camera_id, tenant_id, error = _space_scope(space)
        if not error and route_device_id != camera_id:
            error = 'snap_camera_scope_mismatch'

    if not error and requested_camera and requested_camera != camera_id:
        error = (
            'snapshot_camera_scope_denied'
            if endpoint == 'snap.list_space_images'
            else 'snap_camera_scope_mismatch'
        )

    object_names = []
    route_object = str(view_args.get('object_name') or '').strip()
    if route_object:
        object_names.append(route_object)
    body_objects = data.get('object_names') or data.get('objectNames') or []
    if isinstance(body_objects, (list, tuple)):
        object_names.extend(str(value or '').strip() for value in body_objects)
    if not error and object_names:
        if space is None or tenant_id is None or not camera_id:
            error = 'snap_object_scope_missing'
        else:
            from models import SnapImage

            for object_name in (value for value in object_names if value):
                image = SnapImage.query.filter_by(
                    tenant_id=int(tenant_id),
                    space_id=int(getattr(space, 'id')),
                    device_id=camera_id,
                    object_name=object_name,
                ).first()
                if image is None:
                    error = 'snapshot_object_scope_denied'
                    break

    return space, camera_id, tenant_id, error


@snap_bp.before_request
def require_snap_authorization():
    if request.endpoint in _MULTI_SCOPE_ENDPOINTS:
        return None
    space, camera_id, tenant_id, scope_error = _resolve_snap_scope()
    action = 'snapshot' if request.method == 'GET' else 'record_manage'
    decision = authorize_media_request(
        request,
        action=action,
        camera_id=camera_id,
        resource=request.path,
        owner_tenant_id=tenant_id,
        defer_audit=True,
    )
    audit_media_response(decision, resource=request.path)
    if not decision.allowed:
        return _authorization_denied_response(decision)
    if scope_error or not camera_id or tenant_id is None:
        reason = scope_error or 'snap_camera_scope_missing'
        status_code = 404 if reason == 'snapshot_object_scope_denied' else 403
        return _authorization_denied_response(_scope_mismatch_decision(
            decision, reason, status_code=status_code, audit=False))
    g.snap_space = space
    g.snap_decision = decision
    return None


def _authorize_scopes(scopes, action):
    if not scopes:
        decision = authorize_media_request(
            request,
            action=action,
            camera_id=None,
            resource=request.path,
        )
        if not decision.allowed:
            return decision, None, []
        return _scope_mismatch_decision(
            decision, 'snap_authorization_scope_empty'), None, []

    trusted_tenant_id = None
    allowed_camera_ids = []
    for scope in scopes:
        camera_id = str((scope or {}).get('camera_id') or '').strip()
        tenant_id = str((scope or {}).get('tenant_id') or '').strip()
        if not camera_id or not tenant_id.isdigit() or int(tenant_id) <= 0:
            continue
        decision = authorize_media_request(
            request,
            action=action,
            camera_id=camera_id,
            resource=request.path,
            owner_tenant_id=int(tenant_id),
        )
        if not decision.allowed:
            if decision.status_code != 403:
                return decision, None, []
            continue
        if trusted_tenant_id is None:
            trusted_tenant_id = int(decision.tenant_id)
        elif int(decision.tenant_id) != trusted_tenant_id:
            return _scope_mismatch_decision(
                decision, 'snap_tenant_scope_ambiguous'), None, []
        allowed_camera_ids.append(camera_id)

    if not allowed_camera_ids:
        decision = MediaAuthorizationDecision(
            False, None, None, None, action,
            'snap_authorization_scope_empty', 403,
        )
        append_media_access_audit(decision, resource=request.path)
        return decision, None, []
    return None, trusted_tenant_id, list(dict.fromkeys(allowed_camera_ids))


def _authorize_list(action):
    from app.services.snap_space_service import list_snap_space_authorization_scopes

    camera_hint = _requested_camera_id()
    return _authorize_scopes(
        list_snap_space_authorization_scopes(camera_hint), action)


def _authorize_group_policy(group_type, group_key):
    from app.services.snap_space_service import list_snap_group_authorization_scopes

    scopes = list_snap_group_authorization_scopes(group_type, group_key)
    denied, tenant_id, camera_ids = _authorize_scopes(scopes, 'record_manage')
    if denied is not None:
        return denied, None, []
    if len(camera_ids) != len(scopes):
        decision = MediaAuthorizationDecision(
            False, None, str(tenant_id), None, 'record_manage',
            'snap_group_camera_scope_denied', 403,
        )
        append_media_access_audit(decision, resource=request.path)
        return decision, None, []
    return None, tenant_id, camera_ids


def _authorize_snap_space(space_id: int):
    space = getattr(g, 'snap_space', None)
    decision = getattr(g, 'snap_decision', None)
    if space is not None and int(getattr(space, 'id')) == int(space_id):
        return space, decision, None
    space = get_snap_space(space_id)
    camera_id = str(getattr(space, 'device_id', None) or '').strip()
    if not camera_id:
        return space, None, (
            jsonify({
                'code': 403,
                'msg': 'snapshot camera scope is missing',
                'reason': 'snapshot_camera_scope_missing',
            }),
            403,
        )
    decision = authorize_media_request(
        request,
        action='snapshot' if request.method == 'GET' else 'record_manage',
        camera_id=camera_id,
        resource=request.path,
        owner_tenant_id=getattr(space, 'tenant_id', None),
    )
    if decision.allowed:
        return space, decision, None
    payload, status = authorization_error(decision)
    return space, decision, (jsonify(payload), status)


# ====================== 抓拍空间管理接口 ======================
@snap_bp.route('/space/list', methods=['GET'])
def list_spaces():
    """查询抓拍空间列表"""
    try:
        denied, tenant_id, camera_ids = _authorize_list('snapshot')
        if denied is not None:
            return _authorization_denied_response(denied)
        page_no = int(request.args.get('pageNo', 1))
        page_size = int(request.args.get('pageSize', 10))
        search = request.args.get('search', '').strip() or None
        parent_key = request.args.get('parentKey', 'root').strip() or 'root'
        scope = request.args.get('scope', '').strip() or None

        result = list_snap_spaces(
            page_no,
            page_size,
            search,
            parent_key,
            scope,
            tenant_id=tenant_id,
            camera_ids=camera_ids,
        )
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
        logger.error(f'查询抓拍空间列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/<int:space_id>', methods=['GET'])
def get_space(space_id):
    """获取抓拍空间详情"""
    try:
        space = get_snap_space(space_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': space.to_dict()
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取抓拍空间失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/device/<device_id>', methods=['GET'])
def get_space_by_device(device_id):
    """根据设备ID获取抓拍空间"""
    try:
        decision = g.snap_decision
        space = get_snap_space_by_device_id(
            device_id, tenant_id=decision.tenant_id)
        if not space:
            return jsonify({
                'code': 400,
                'msg': f'设备 {device_id} 没有关联的抓拍空间'
            }), 400
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': space.to_dict()
        })
    except Exception as e:
        logger.error(f'根据设备ID获取抓拍空间失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space', methods=['POST'])
def create_space():
    """创建抓拍空间（已禁用：抓拍空间现在跟随设备自动创建）"""
    return jsonify({
        'code': 403,
        'msg': '抓拍空间不能手动创建，系统会在创建设备时自动创建抓拍空间'
    }), 403


@snap_bp.route('/space/<int:space_id>', methods=['PUT'])
def update_space(space_id):
    """更新抓拍空间"""
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
            space = update_snap_space(
                space_id, space_name, save_mode, save_time, description, save_time_custom,
            )
        except ValueError as ve:
            return jsonify({'code': 400, 'msg': str(ve)}), 400
        return jsonify({
            'code': 0,
            'msg': '抓拍空间更新成功',
            'data': space.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'更新抓拍空间失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/group-policy', methods=['PUT'])
def update_group_policy():
    """更新 NVR / GB28181 分组默认抓拍保存时间，联动非自定义子设备。"""
    try:
        data = request.get_json() or {}
        group_type = (data.get('group_type') or '').strip().lower()
        group_key = str(data.get('group_key') or '').strip()
        save_time = data.get('save_time')
        if save_time is None:
            return jsonify({'code': 400, 'msg': 'save_time 不能为空'}), 400

        denied, tenant_id, camera_ids = _authorize_group_policy(
            group_type, group_key)
        if denied is not None:
            return _authorization_denied_response(denied)

        from app.services.space_group_save_time_service import update_group_save_time
        from app.services.space_save_time_service import SPACE_KIND_SNAP

        policy, updated = update_group_save_time(
            group_type,
            group_key,
            SPACE_KIND_SNAP,
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
                'save_time': policy.snap_save_time,
                'updated_count': updated,
            },
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'更新分组抓拍存储策略失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/<int:space_id>', methods=['DELETE'])
def delete_space(space_id):
    """删除抓拍空间"""
    try:
        delete_snap_space(space_id)
        return jsonify({
            'code': 0,
            'msg': '抓拍空间删除成功'
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'删除抓拍空间失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/sync/minio', methods=['POST'])
def sync_spaces_minio():
    """同步所有抓拍空间到Minio，创建不存在的目录"""
    try:
        denied, tenant_id, camera_ids = _authorize_list('record_manage')
        if denied is not None:
            return _authorization_denied_response(denied)
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
        logger.error(f'同步抓拍空间到Minio失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


# ====================== 抓拍任务管理接口 ======================
@snap_bp.route('/task/list', methods=['GET'])
def list_tasks():
    """查询抓拍任务列表"""
    try:
        denied, tenant_id, camera_ids = _authorize_list('snapshot')
        if denied is not None:
            return _authorization_denied_response(denied)
        page_no = int(request.args.get('pageNo', 1))
        page_size = int(request.args.get('pageSize', 10))
        space_id = request.args.get('space_id')
        device_id = request.args.get('device_id')
        search = request.args.get('search', '').strip() or None
        status = request.args.get('status')
        
        space_id_int = int(space_id) if space_id else None
        status_int = int(status) if status else None
        
        result = list_snap_tasks(
            page_no,
            page_size,
            space_id_int,
            device_id,
            search,
            status_int,
            tenant_id=tenant_id,
            camera_ids=camera_ids,
        )
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result['items'],
            'total': result['total']
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'查询抓拍任务列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>', methods=['GET'])
def get_task(task_id):
    """获取抓拍任务详情"""
    try:
        task = get_snap_task(task_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': task
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取抓拍任务失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task', methods=['POST'])
def create_task():
    """创建抓拍任务"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        task_name = data.get('task_name', '').strip()
        if not task_name:
            return jsonify({'code': 400, 'msg': '任务名称不能为空'}), 400
        
        space_id = data.get('space_id')
        if not space_id:
            return jsonify({'code': 400, 'msg': '抓拍空间ID不能为空'}), 400
        
        device_id = data.get('device_id', '').strip()
        if not device_id:
            return jsonify({'code': 400, 'msg': '设备ID不能为空'}), 400
        
        task = create_snap_task(
            task_name=task_name,
            space_id=int(space_id),
            device_id=device_id,
            capture_type=data.get('capture_type', 0),
            cron_expression=data.get('cron_expression', '0 */5 * * * *'),
            frame_skip=data.get('frame_skip', 1),
            algorithm_enabled=data.get('algorithm_enabled', False),
            algorithm_type=data.get('algorithm_type'),
            algorithm_model_id=data.get('algorithm_model_id'),
            algorithm_threshold=data.get('algorithm_threshold'),
            algorithm_night_mode=data.get('algorithm_night_mode', False),
            alarm_enabled=data.get('alarm_enabled', False),
            alarm_type=data.get('alarm_type', 0),
            phone_number=data.get('phone_number'),
            email=data.get('email'),
            notify_users=data.get('notify_users'),
            notify_methods=data.get('notify_methods'),
            alarm_suppress_time=data.get('alarm_suppress_time', 300),
            auto_filename=data.get('auto_filename', True),
            custom_filename_prefix=data.get('custom_filename_prefix')
        )
        
        task_dict = task.to_dict()
        from models import Device
        device = Device.query.get(device_id)
        if device:
            task_dict['device_name'] = device.name
        
        return jsonify({
            'code': 0,
            'msg': '抓拍任务创建成功',
            'data': task_dict
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'创建抓拍任务失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>', methods=['PUT'])
def update_task(task_id):
    """更新抓拍任务"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        update_data = {}
        if 'task_name' in data:
            update_data['task_name'] = data.get('task_name', '').strip()
        if 'space_id' in data:
            update_data['space_id'] = data.get('space_id')
        if 'device_id' in data:
            update_data['device_id'] = data.get('device_id', '').strip()
        if 'capture_type' in data:
            update_data['capture_type'] = data.get('capture_type')
        if 'cron_expression' in data:
            update_data['cron_expression'] = data.get('cron_expression')
        if 'frame_skip' in data:
            update_data['frame_skip'] = data.get('frame_skip')
        if 'algorithm_enabled' in data:
            update_data['algorithm_enabled'] = data.get('algorithm_enabled')
        if 'algorithm_type' in data:
            update_data['algorithm_type'] = data.get('algorithm_type')
        if 'algorithm_model_id' in data:
            update_data['algorithm_model_id'] = data.get('algorithm_model_id')
        if 'algorithm_threshold' in data:
            update_data['algorithm_threshold'] = data.get('algorithm_threshold')
        if 'algorithm_night_mode' in data:
            update_data['algorithm_night_mode'] = data.get('algorithm_night_mode')
        if 'alarm_enabled' in data:
            update_data['alarm_enabled'] = data.get('alarm_enabled')
        if 'alarm_type' in data:
            update_data['alarm_type'] = data.get('alarm_type')
        if 'phone_number' in data:
            update_data['phone_number'] = data.get('phone_number')
        if 'email' in data:
            update_data['email'] = data.get('email')
        if 'notify_users' in data:
            update_data['notify_users'] = data.get('notify_users')
        if 'notify_methods' in data:
            update_data['notify_methods'] = data.get('notify_methods')
        if 'alarm_suppress_time' in data:
            update_data['alarm_suppress_time'] = data.get('alarm_suppress_time')
        if 'auto_filename' in data:
            update_data['auto_filename'] = data.get('auto_filename')
        if 'custom_filename_prefix' in data:
            update_data['custom_filename_prefix'] = data.get('custom_filename_prefix')
        if 'is_enabled' in data:
            update_data['is_enabled'] = data.get('is_enabled')
        
        task = update_snap_task(task_id, **update_data)
        task_dict = task.to_dict()
        from models import Device
        device = Device.query.get(task.device_id)
        if device:
            task_dict['device_name'] = device.name
        
        return jsonify({
            'code': 0,
            'msg': '抓拍任务更新成功',
            'data': task_dict
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'更新抓拍任务失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>', methods=['DELETE'])
def delete_task(task_id):
    """删除抓拍任务"""
    try:
        delete_snap_task(task_id)
        return jsonify({
            'code': 0,
            'msg': '抓拍任务删除成功'
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'删除抓拍任务失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>/start', methods=['POST'])
def start_task_route(task_id):
    """启动抓拍任务"""
    try:
        task = start_task(task_id)
        return jsonify({
            'code': 0,
            'msg': '任务已启动',
            'data': task.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'启动任务失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>/stop', methods=['POST'])
def stop_task_route(task_id):
    """停止抓拍任务"""
    try:
        task = stop_task(task_id)
        return jsonify({
            'code': 0,
            'msg': '任务已停止',
            'data': task.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'停止任务失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>/restart', methods=['POST'])
def restart_task_route(task_id):
    """重启抓拍任务"""
    try:
        task = restart_task(task_id)
        return jsonify({
            'code': 0,
            'msg': '任务已重启',
            'data': task.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'重启任务失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>/logs', methods=['GET'])
def get_task_logs_route(task_id):
    """获取任务日志"""
    try:
        page_no = int(request.args.get('pageNo', 1))
        page_size = int(request.args.get('pageSize', 50))
        level = request.args.get('level', '').strip() or None
        
        result = get_task_logs(task_id, page_no, page_size, level)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result['logs'],
            'total': result['total']
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取任务日志失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


# ====================== 检测区域管理接口 ======================
@snap_bp.route('/task/<int:task_id>/regions', methods=['GET'])
def list_regions(task_id):
    """查询任务的检测区域列表"""
    try:
        regions = DetectionRegion.query.filter_by(task_id=task_id).order_by(DetectionRegion.sort_order, DetectionRegion.id).all()
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': [region.to_dict() for region in regions]
        })
    except Exception as e:
        logger.error(f'查询检测区域列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region/<int:region_id>', methods=['GET'])
def get_region(region_id):
    """获取检测区域详情"""
    try:
        region = DetectionRegion.query.get(region_id)
        if not region:
            return jsonify({'code': 400, 'msg': f'检测区域不存在: ID={region_id}'}), 400
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': region.to_dict()
        })
    except Exception as e:
        logger.error(f'获取检测区域失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region', methods=['POST'])
def create_region():
    """创建检测区域"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        task_id = data.get('task_id')
        if not task_id:
            return jsonify({'code': 400, 'msg': '任务ID不能为空'}), 400
        
        region_name = data.get('region_name', '').strip()
        if not region_name:
            return jsonify({'code': 400, 'msg': '区域名称不能为空'}), 400
        
        points = data.get('points')
        if not points or not isinstance(points, list) or len(points) < 3:
            return jsonify({'code': 400, 'msg': '区域坐标点不能为空，且至少需要3个点'}), 400
        
        import json
        region = DetectionRegion(
            task_id=task_id,
            region_name=region_name,
            region_type=data.get('region_type', 'polygon'),
            points=json.dumps(points),
            image_id=data.get('image_id'),
            algorithm_type=data.get('algorithm_type'),
            algorithm_model_id=data.get('algorithm_model_id'),
            algorithm_threshold=data.get('algorithm_threshold'),
            algorithm_enabled=data.get('algorithm_enabled', True),
            color=data.get('color', '#FF5252'),
            opacity=data.get('opacity', 0.3),
            is_enabled=data.get('is_enabled', True),
            sort_order=data.get('sort_order', 0)
        )
        
        db.session.add(region)
        db.session.commit()
        
        return jsonify({
            'code': 0,
            'msg': '检测区域创建成功',
            'data': region.to_dict()
        })
    except Exception as e:
        db.session.rollback()
        logger.error(f'创建检测区域失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region/<int:region_id>', methods=['PUT'])
def update_region(region_id):
    """更新检测区域"""
    try:
        region = DetectionRegion.query.get(region_id)
        if not region:
            return jsonify({'code': 400, 'msg': f'检测区域不存在: ID={region_id}'}), 400
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        if 'region_name' in data:
            region.region_name = data.get('region_name', '').strip()
        if 'region_type' in data:
            region.region_type = data.get('region_type', 'polygon')
        if 'points' in data:
            import json
            points = data.get('points')
            if not points or not isinstance(points, list) or len(points) < 3:
                return jsonify({'code': 400, 'msg': '区域坐标点不能为空，且至少需要3个点'}), 400
            region.points = json.dumps(points)
        if 'image_id' in data:
            region.image_id = data.get('image_id')
        if 'algorithm_type' in data:
            region.algorithm_type = data.get('algorithm_type')
        if 'algorithm_model_id' in data:
            region.algorithm_model_id = data.get('algorithm_model_id')
        if 'algorithm_threshold' in data:
            region.algorithm_threshold = data.get('algorithm_threshold')
        if 'algorithm_enabled' in data:
            region.algorithm_enabled = data.get('algorithm_enabled', True)
        if 'color' in data:
            region.color = data.get('color', '#FF5252')
        if 'opacity' in data:
            region.opacity = data.get('opacity', 0.3)
        if 'is_enabled' in data:
            region.is_enabled = data.get('is_enabled', True)
        if 'sort_order' in data:
            region.sort_order = data.get('sort_order', 0)
        
        db.session.commit()
        
        return jsonify({
            'code': 0,
            'msg': '检测区域更新成功',
            'data': region.to_dict()
        })
    except Exception as e:
        db.session.rollback()
        logger.error(f'更新检测区域失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region/<int:region_id>', methods=['DELETE'])
def delete_region(region_id):
    """删除检测区域"""
    try:
        region = DetectionRegion.query.get(region_id)
        if not region:
            return jsonify({'code': 400, 'msg': f'检测区域不存在: ID={region_id}'}), 400
        db.session.delete(region)
        db.session.commit()
        
        return jsonify({
            'code': 0,
            'msg': '检测区域删除成功'
        })
    except Exception as e:
        db.session.rollback()
        logger.error(f'删除检测区域失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


# ====================== 算法模型服务配置接口 ======================
@snap_bp.route('/task/<int:task_id>/services', methods=['GET'])
def list_task_services(task_id):
    """获取任务的算法模型服务配置列表"""
    try:
        services = get_task_algorithm_services(task_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': [s.to_dict() for s in services]
        })
    except Exception as e:
        logger.error(f'获取任务算法服务配置失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/task/<int:task_id>/service', methods=['POST'])
def create_task_service(task_id):
    """创建任务的算法模型服务配置"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        service_name = data.get('service_name', '').strip()
        if not service_name:
            return jsonify({'code': 400, 'msg': '服务名称不能为空'}), 400
        
        service_url = data.get('service_url', '').strip()
        if not service_url:
            return jsonify({'code': 400, 'msg': '服务URL不能为空'}), 400
        
        service = create_task_algorithm_service(
            task_id=task_id,
            service_name=service_name,
            service_url=service_url,
            service_type=data.get('service_type'),
            model_id=data.get('model_id'),
            threshold=data.get('threshold'),
            request_method=data.get('request_method', 'POST'),
            request_headers=data.get('request_headers'),
            request_body_template=data.get('request_body_template'),
            timeout=data.get('timeout', 30),
            is_enabled=data.get('is_enabled', True),
            sort_order=data.get('sort_order', 0)
        )
        
        return jsonify({
            'code': 0,
            'msg': '算法服务配置创建成功',
            'data': service.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'创建算法服务配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/service/<int:service_id>', methods=['PUT'])
def update_task_service(service_id):
    """更新任务的算法模型服务配置"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        service = update_task_algorithm_service(service_id, **data)
        return jsonify({
            'code': 0,
            'msg': '算法服务配置更新成功',
            'data': service.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'更新算法服务配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/service/<int:service_id>', methods=['DELETE'])
def delete_task_service(service_id):
    """删除任务的算法模型服务配置"""
    try:
        delete_task_algorithm_service(service_id)
        return jsonify({
            'code': 0,
            'msg': '算法服务配置删除成功'
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'删除算法服务配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region/<int:region_id>/services', methods=['GET'])
def list_region_services(region_id):
    """获取区域的算法模型服务配置列表"""
    try:
        services = get_region_algorithm_services(region_id)
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': [s.to_dict() for s in services]
        })
    except Exception as e:
        logger.error(f'获取区域算法服务配置失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region/<int:region_id>/service', methods=['POST'])
def create_region_service(region_id):
    """创建区域的算法模型服务配置"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        service_name = data.get('service_name', '').strip()
        if not service_name:
            return jsonify({'code': 400, 'msg': '服务名称不能为空'}), 400
        
        service_url = data.get('service_url', '').strip()
        if not service_url:
            return jsonify({'code': 400, 'msg': '服务URL不能为空'}), 400
        
        service = create_region_algorithm_service(
            region_id=region_id,
            service_name=service_name,
            service_url=service_url,
            service_type=data.get('service_type'),
            model_id=data.get('model_id'),
            threshold=data.get('threshold'),
            request_method=data.get('request_method', 'POST'),
            request_headers=data.get('request_headers'),
            request_body_template=data.get('request_body_template'),
            timeout=data.get('timeout', 30),
            is_enabled=data.get('is_enabled', True),
            sort_order=data.get('sort_order', 0)
        )
        
        return jsonify({
            'code': 0,
            'msg': '区域算法服务配置创建成功',
            'data': service.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'创建区域算法服务配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region-service/<int:service_id>', methods=['PUT'])
def update_region_service(service_id):
    """更新区域的算法模型服务配置"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        service = update_region_algorithm_service(service_id, **data)
        return jsonify({
            'code': 0,
            'msg': '区域算法服务配置更新成功',
            'data': service.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'更新区域算法服务配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/region-service/<int:service_id>', methods=['DELETE'])
def delete_region_service(service_id):
    """删除区域的算法模型服务配置"""
    try:
        delete_region_algorithm_service(service_id)
        return jsonify({
            'code': 0,
            'msg': '区域算法服务配置删除成功'
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'删除区域算法服务配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


# ====================== 设备存储配置接口 ======================
@snap_bp.route('/device/<device_id>/storage', methods=['GET'])
def get_device_storage(device_id):
    """获取设备存储配置和信息"""
    try:
        config = get_or_create_device_storage_config(device_id)
        storage_info = get_device_storage_info(device_id)
        
        result = config.to_dict()
        result.update(storage_info)
        
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result
        })
    except Exception as e:
        logger.error(f'获取设备存储配置失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/device/<device_id>/storage', methods=['PUT'])
def update_device_storage(device_id):
    """更新设备存储配置"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        config = update_device_storage_config(device_id, **data)
        return jsonify({
            'code': 0,
            'msg': '设备存储配置更新成功',
            'data': config.to_dict()
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'更新设备存储配置失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/device/<device_id>/storage/cleanup', methods=['POST'])
def cleanup_device_storage(device_id):
    """手动触发设备存储清理"""
    try:
        result = check_and_cleanup_storage(device_id)
        return jsonify({
            'code': 0,
            'msg': '存储清理完成',
            'data': result
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'清理设备存储失败: {str(e)}', exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


# ====================== 抓拍图片管理接口 ======================
@snap_bp.route('/space/<int:space_id>/images', methods=['GET'])
def list_space_images(space_id):
    """获取抓拍空间图片列表"""
    try:
        space, _decision, denied = _authorize_snap_space(space_id)
        if denied:
            return denied
        device_id = str(getattr(space, 'device_id', None) or '').strip()
        requested_device_id = request.args.get('device_id', '').strip()
        if requested_device_id and requested_device_id != device_id:
            return jsonify({
                'code': 403,
                'msg': 'snapshot camera scope denied',
                'reason': 'snapshot_camera_scope_denied',
            }), 403
        page_no = int(request.args.get('pageNo', 1))
        page_size = int(request.args.get('pageSize', 20))
        search = request.args.get('search', '').strip() or None
        source = request.args.get('source', '').strip() or None
        start_time = request.args.get('startTime')
        end_time = request.args.get('endTime')

        from datetime import datetime
        from app.utils.service_urls import normalize_to_shanghai_naive

        def _parse_dt(value):
            if not value:
                return None
            text = str(value).strip()
            if ' ' in text and 'T' not in text:
                text = text.replace(' ', 'T', 1)
            parsed = datetime.fromisoformat(text.replace('Z', '+00:00'))
            if parsed.tzinfo is not None:
                return normalize_to_shanghai_naive(parsed)
            return parsed

        start_dt = _parse_dt(start_time)
        end_dt = _parse_dt(end_time)

        result = list_snap_images(
            space_id, device_id, page_no, page_size, search,
            start_dt, end_dt, source,
            tenant_id=getattr(space, 'tenant_id', None),
        )
        return jsonify({
            'code': 0,
            'msg': 'success',
            'data': result['items'],
            'total': result['total']
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取抓拍图片列表失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/<int:space_id>/image/<path:object_name>', methods=['GET'])
def get_space_image(space_id, object_name):
    """获取抓拍图片内容"""
    try:
        from flask import Response
        from models import SnapImage
        space, _decision, denied = _authorize_snap_space(space_id)
        if denied:
            return denied
        device_id = str(getattr(space, 'device_id', None) or '').strip()
        image = SnapImage.query.filter_by(
            tenant_id=int(getattr(space, 'tenant_id')),
            space_id=space_id,
            device_id=device_id,
            object_name=object_name,
        ).first()
        if image is None:
            return jsonify({
                'code': 404,
                'msg': 'snapshot object is not owned by the authorized camera',
                'reason': 'snapshot_object_scope_denied',
            }), 404
        content, content_type, filename = get_snap_image(
            space_id,
            object_name,
            tenant_id=getattr(space, 'tenant_id', None),
        )
        return Response(
            content,
            mimetype=content_type,
            headers={'Content-Disposition': f'inline; filename="{filename}"'}
        )
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'获取抓拍图片失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/<int:space_id>/images', methods=['DELETE'])
def delete_space_images(space_id):
    """批量删除抓拍图片"""
    try:
        space, _decision, denied = _authorize_snap_space(space_id)
        if denied:
            return denied
        data = request.get_json()
        if not data:
            return jsonify({'code': 400, 'msg': '请求数据不能为空'}), 400
        
        object_names = data.get('object_names', [])
        if not object_names or not isinstance(object_names, list):
            return jsonify({'code': 400, 'msg': 'object_names必须是非空数组'}), 400
        
        result = delete_snap_images(
            space_id,
            object_names,
            tenant_id=getattr(space, 'tenant_id', None),
        )
        return jsonify({
            'code': 0,
            'msg': '删除完成',
            'data': result
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'批量删除抓拍图片失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/<int:space_id>/images/sync', methods=['POST'])
def sync_images_metadata(space_id):
    """从 MinIO 同步抓拍元数据到数据库（历史数据回填）"""
    try:
        _space, _decision, denied = _authorize_snap_space(space_id)
        if denied:
            return denied
        result = sync_snap_images_metadata(space_id)
        return jsonify({
            'code': 0,
            'msg': '同步完成',
            'data': result
        })
    except RuntimeError as e:
        return jsonify({'code': 500, 'msg': str(e)}), 500
    except Exception as e:
        logger.error(f'同步抓拍元数据失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500


@snap_bp.route('/space/<int:space_id>/images/cleanup', methods=['POST'])
def cleanup_space_images(space_id):
    """清理过期的抓拍图片"""
    try:
        _space, _decision, denied = _authorize_snap_space(space_id)
        if denied:
            return denied
        data = request.get_json() or {}
        if 'save_time_hours' not in data:
            return jsonify({'code': 400, 'msg': '需要提供 save_time_hours 参数'}), 400
        save_time_hours = int(data.get('save_time_hours', 0))

        if save_time_hours <= 0:
            return jsonify({'code': 400, 'msg': 'save_time_hours 必须大于 0'}), 400

        result = cleanup_old_images_by_save_time(space_id, save_time_hours)
        return jsonify({
            'code': 0,
            'msg': '清理完成',
            'data': result
        })
    except ValueError as e:
        return jsonify({'code': 400, 'msg': str(e)}), 400
    except Exception as e:
        logger.error(f'清理过期图片失败: {str(e)}', exc_info=True)
        return jsonify({'code': 500, 'msg': f'服务器内部错误: {str(e)}'}), 500
