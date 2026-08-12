"""
监控录像管理服务
@author reese
@email reese
"""
import io
import base64
import hashlib
import json
import logging
import os
import shutil
import zipfile
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any

from flask import current_app
from minio.error import S3Error
from sqlalchemy import and_, func, or_

from models import db, RecordSpace, RecordFile, Alert
from app.services.alert_service import _alert_to_dict
from app.services.record_space_service import get_minio_client
from app.utils.service_urls import build_record_video_api_url, minio_storage_enabled
from app.services.space_file_metadata_service import (
    delete_record_files_metadata,
    sync_record_files_from_minio,
    extract_prefix_from_url,
    require_mutable_tenant_object,
)
from app.services.local_media_path_service import (
    LocalMediaPathError,
    resolve_allowed_local_media_file,
)
from app.utils.minio_bucket_policy import ensure_bucket_private

logger = logging.getLogger(__name__)

STANDARD_RECORD_GAP_REASON_KEYS = [
    'video_url_not_configured',
    'record_space_not_found',
    'file_missing',
    'probe_failed',
    'permission_denied',
    'retention_expired',
    'disk_full',
    'cache_flush_failed',
]


def _space_tenant_id(space, tenant_id=None) -> int:
    try:
        owner_tenant_id = int(getattr(space, 'tenant_id', None))
    except (TypeError, ValueError) as exc:
        raise ValueError('record space tenant owner is missing') from exc
    if owner_tenant_id <= 0:
        raise ValueError('record space tenant owner is invalid')
    if tenant_id is not None:
        try:
            requested_tenant_id = int(tenant_id)
        except (TypeError, ValueError) as exc:
            raise ValueError('record tenant scope is invalid') from exc
        if requested_tenant_id != owner_tenant_id:
            raise ValueError('record tenant does not match space owner')
    return owner_tenant_id


def _upload_verified_archive(minio_client, bucket_name: str,
                             object_name: str, zip_buffer: io.BytesIO) -> int:
    archive_size = zip_buffer.tell()
    if archive_size <= 0:
        raise RuntimeError('record archive is empty')
    archive_hash = hashlib.sha256(zip_buffer.getbuffer()).hexdigest()
    zip_buffer.seek(0)
    try:
        minio_client.put_object(
            bucket_name,
            object_name,
            zip_buffer,
            length=archive_size,
            content_type='application/zip',
            metadata={'sha256': archive_hash},
        )
        archive_stat = minio_client.stat_object(bucket_name, object_name)
        if int(getattr(archive_stat, 'size', -1)) != archive_size:
            raise RuntimeError('record archive size verification failed')
        metadata = {
            str(key).lower(): str(value).strip().lower()
            for key, value in (getattr(archive_stat, 'metadata', {}) or {}).items()
        }
        persisted_hash = metadata.get('x-amz-meta-sha256') or metadata.get('sha256')
        if persisted_hash != archive_hash:
            raise RuntimeError('record archive hash verification failed')
        archive_response = minio_client.get_object(bucket_name, object_name)
        readback_hash = hashlib.sha256()
        try:
            while True:
                chunk = archive_response.read(1024 * 1024)
                if not chunk:
                    break
                readback_hash.update(chunk)
        finally:
            archive_response.close()
            archive_response.release_conn()
        if readback_hash.hexdigest() != archive_hash:
            raise RuntimeError('record archive content verification failed')
    except Exception:
        try:
            minio_client.remove_object(bucket_name, object_name)
        except Exception:
            pass
        raise
    return archive_size


def list_record_videos(
    space_id: int,
    device_id: Optional[str] = None,
    page_no: int = 1,
    page_size: int = 20,
    search: Optional[str] = None,
    start_time: Optional[datetime] = None,
    end_time: Optional[datetime] = None,
    tenant_id=None,
) -> Dict:
    """获取监控录像列表（数据库分页）"""
    try:
        record_space = RecordSpace.query.get_or_404(space_id)
        tenant_id = _space_tenant_id(record_space, tenant_id)
        query = RecordFile.query.filter_by(
            tenant_id=tenant_id, space_id=space_id)

        effective_device_id = device_id or record_space.device_id
        if effective_device_id:
            query = query.filter(RecordFile.device_id == effective_device_id)

        if search:
            query = query.filter(RecordFile.filename.ilike(f'%{search}%'))
        if start_time:
            query = query.filter(RecordFile.event_time >= start_time)
        if end_time:
            query = query.filter(RecordFile.event_time <= end_time)

        query = query.order_by(RecordFile.event_time.desc())
        pagination = query.paginate(page=page_no, per_page=page_size, error_out=False)

        return {
            'items': [item.to_list_item() for item in pagination.items],
            'total': pagination.total,
            'page_no': page_no,
            'page_size': page_size,
        }
    except Exception as e:
        logger.error(f"获取监控录像列表失败: {str(e)}", exc_info=True)
        raise RuntimeError(f"获取监控录像列表失败: {str(e)}")


def delete_record_videos(space_id: int, object_names: List[str], tenant_id=None) -> Dict:
    """批量删除监控录像（MinIO/本地 + 数据库）"""
    try:
        record_space = RecordSpace.query.get_or_404(space_id)
        tenant_id = _space_tenant_id(record_space, tenant_id)
        bucket_name = record_space.bucket_name
        for object_name in object_names:
            require_mutable_tenant_object(
                tenant_id,
                object_name,
                camera_id=record_space.device_id,
            )

        deleted_count = 0
        failed_count = 0
        failed_objects = []

        if not minio_storage_enabled():
            for object_name in object_names:
                record = RecordFile.query.filter_by(
                    tenant_id=tenant_id,
                    space_id=space_id,
                    object_name=object_name,
                ).first()
                local_path = _resolve_local_record_file(record, object_name)
                try:
                    if local_path and os.path.isfile(local_path):
                        os.remove(local_path)
                    deleted_count += 1
                    logger.info('mini 形态删除监控录像: %s', local_path)
                except OSError as e:
                    failed_count += 1
                    failed_objects.append(object_name)
                    logger.warning('mini 形态删除监控录像失败: %s error=%s', local_path, e)
        else:
            minio_client = get_minio_client()
            if not minio_client.bucket_exists(bucket_name):
                raise ValueError(f"监控录像空间的MinIO bucket不存在: {bucket_name}")

            for object_name in object_names:
                try:
                    minio_client.remove_object(bucket_name, object_name)
                    thumb_name = object_name.rsplit('.', 1)[0] + '.jpg'
                    try:
                        minio_client.remove_object(bucket_name, thumb_name)
                    except Exception:
                        pass
                    deleted_count += 1
                    logger.info(f"删除监控录像成功: {bucket_name}/{object_name}")
                except Exception as e:
                    failed_count += 1
                    failed_objects.append(object_name)
                    logger.warning(f"删除监控录像失败: {bucket_name}/{object_name}, error={str(e)}")

        success_objects = [n for n in object_names if n not in failed_objects]
        delete_record_files_metadata(
            bucket_name,
            success_objects,
            tenant_id=tenant_id,
            space_id=space_id,
        )

        return {
            'deleted_count': deleted_count,
            'failed_count': failed_count,
            'failed_objects': failed_objects,
        }
    except Exception as e:
        logger.error(f"批量删除监控录像失败: {str(e)}", exc_info=True)
        raise RuntimeError(f"批量删除监控录像失败: {str(e)}")


def get_record_video(space_id: int, object_name: str, tenant_id=None):
    """获取监控录像内容"""
    import mimetypes
    import os

    try:
        record_space = RecordSpace.query.get_or_404(space_id)
        tenant_id = _space_tenant_id(record_space, tenant_id)
        bucket_name = record_space.bucket_name

        if not minio_storage_enabled():
            record = RecordFile.query.filter_by(
                tenant_id=tenant_id,
                space_id=space_id,
                object_name=object_name,
            ).first()
            local_path = _resolve_local_record_file(record, object_name)
            with open(local_path, 'rb') as handle:
                content = handle.read()
            filename = object_name.split('/')[-1]
            ext = os.path.splitext(filename)[1].lower()
            content_type_map = {
                '.mp4': 'video/mp4', '.flv': 'video/x-flv', '.avi': 'video/x-msvideo',
                '.mov': 'video/quicktime', '.mkv': 'video/x-matroska', '.ts': 'video/mp2t',
            }
            guessed, _ = mimetypes.guess_type(filename)
            return content, content_type_map.get(ext) or guessed or 'video/mp4', filename

        minio_client = get_minio_client()
        if not minio_client.bucket_exists(bucket_name):
            raise ValueError(f"监控录像空间的MinIO bucket不存在: {bucket_name}")

        try:
            stat = minio_client.stat_object(bucket_name, object_name)
            data = minio_client.get_object(bucket_name, object_name)
            content = data.read()
            data.close()
            data.release_conn()
            return content, stat.content_type or 'video/mp4', object_name.split('/')[-1]
        except S3Error as e:
            if e.code == 'NoSuchKey':
                raise ValueError(f"录像不存在: {object_name}")
            raise
    except Exception as e:
        logger.error(f"获取监控录像失败: {str(e)}", exc_info=True)
        raise RuntimeError(f"获取监控录像失败: {str(e)}")


def cleanup_orphan_minio_record_objects(space_id: int, save_time_hours: int) -> Dict:
    """清理 MinIO 中无 record_file 元数据且已过期的录像/缩略图（孤儿对象）。"""
    from app.services.media_dvr_utils import parse_record_minio_object_event_time
    from app.services.space_save_time_service import save_time_cutoff_naive

    if not minio_storage_enabled():
        return {'scanned_count': 0, 'deleted_count': 0, 'error_count': 0, 'skipped': 1}

    cutoff_time = save_time_cutoff_naive(save_time_hours)
    if cutoff_time is None:
        return {'scanned_count': 0, 'deleted_count': 0, 'error_count': 0, 'skipped': 1}

    record_space = RecordSpace.query.get_or_404(space_id)
    bucket_name = record_space.bucket_name
    device_id = record_space.device_id

    minio_client = get_minio_client()
    if not minio_client.bucket_exists(bucket_name):
        return {'scanned_count': 0, 'deleted_count': 0, 'error_count': 0}

    prefix = f'{device_id}/' if device_id else ''

    db_query = RecordFile.query.filter_by(space_id=space_id, bucket_name=bucket_name)
    if device_id:
        db_query = db_query.filter_by(device_id=device_id)
    known_objects = set()
    for record in db_query.all():
        known_objects.add(record.object_name)
        if record.thumbnail_url:
            thumb = extract_prefix_from_url(record.thumbnail_url)
            if thumb:
                known_objects.add(thumb)

    scanned_count = deleted_count = error_count = 0
    for obj in minio_client.list_objects(bucket_name, prefix=prefix, recursive=True):
        object_name = obj.object_name
        if not object_name or object_name.endswith('/'):
            continue
        scanned_count += 1
        if object_name in known_objects:
            continue

        event_time = parse_record_minio_object_event_time(object_name, obj.last_modified)
        if event_time is None or event_time >= cutoff_time:
            continue

        try:
            minio_client.remove_object(bucket_name, object_name)
            deleted_count += 1
            logger.info(
                '删除 MinIO 孤儿录像对象: bucket=%s object=%s event_time=%s cutoff=%s',
                bucket_name, object_name, event_time, cutoff_time,
            )
        except Exception as e:
            error_count += 1
            logger.warning(
                '删除 MinIO 孤儿录像对象失败: bucket=%s object=%s error=%s',
                bucket_name, object_name, e,
            )

    if deleted_count > 0:
        logger.info(
            '录像空间 %s 孤儿对象清理: 扫描=%s, 删除=%s, 错误=%s',
            record_space.space_name, scanned_count, deleted_count, error_count,
        )

    return {
        'scanned_count': scanned_count,
        'deleted_count': deleted_count,
        'error_count': error_count,
    }


def materialize_record_video(space_id: int, object_name: str, destination: str,
                             max_bytes: Optional[int] = None,
                             tenant_id=None) -> Dict:
    """Stream one verified record object to a local file without whole-file buffering."""
    import mimetypes

    record_space = RecordSpace.query.get_or_404(space_id)
    tenant_id = _space_tenant_id(record_space, tenant_id)
    record = RecordFile.query.filter_by(
        tenant_id=tenant_id,
        space_id=space_id,
        device_id=record_space.device_id,
        object_name=object_name,
    ).first()
    if record is None:
        raise ValueError(f"recording metadata not found: {object_name}")
    limit = int(max_bytes) if max_bytes else None
    os.makedirs(os.path.dirname(os.path.abspath(destination)), exist_ok=True)
    source = None
    content_type = 'application/octet-stream'
    try:
        if not minio_storage_enabled():
            local_path = _resolve_local_record_file(record, object_name)
            size = os.path.getsize(local_path)
            if limit and size > limit:
                raise ValueError('recording exceeds configured export input size limit')
            source = open(local_path, 'rb')
            content_type = mimetypes.guess_type(object_name)[0] or 'application/octet-stream'
        else:
            client = get_minio_client()
            stat = client.stat_object(record_space.bucket_name, object_name)
            size = int(getattr(stat, 'size', 0) or 0)
            if limit and size > limit:
                raise ValueError('recording exceeds configured export input size limit')
            source = client.get_object(record_space.bucket_name, object_name)
            content_type = stat.content_type or 'application/octet-stream'

        written = 0
        with open(destination, 'xb') as output:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if limit and written > limit:
                    raise ValueError('recording exceeds configured export input size limit')
                output.write(chunk)
            output.flush()
            os.fsync(output.fileno())
        if written <= 0:
            raise ValueError(f"recording is empty: {object_name}")
        return {
            'path': destination,
            'content_type': content_type,
            'filename': object_name.split('/')[-1],
            'size_bytes': written,
        }
    except Exception:
        try:
            os.remove(destination)
        except FileNotFoundError:
            pass
        raise
    finally:
        if source is not None:
            close = getattr(source, 'close', None)
            if callable(close):
                close()
            release = getattr(source, 'release_conn', None)
            if callable(release):
                release()


def cleanup_old_videos_by_save_time(space_id: int, save_time_hours: int) -> Dict:
    """根据保存时长（小时）清理旧的监控录像"""
    try:
        from app.services.space_save_time_service import save_time_cutoff_naive

        record_space = RecordSpace.query.get_or_404(space_id)
        tenant_id = _space_tenant_id(record_space)
        bucket_name = record_space.bucket_name
        save_mode = record_space.save_mode

        cutoff_time = save_time_cutoff_naive(save_time_hours)
        if cutoff_time is None:
            return {
                'processed_count': 0, 'deleted_count': 0, 'archived_count': 0,
                'error_count': 0, 'orphan_deleted_count': 0, 'orphan_scanned_count': 0,
                'orphan_error_count': 0,
            }
        query = RecordFile.query.filter(
            RecordFile.tenant_id == tenant_id,
            RecordFile.space_id == space_id,
            RecordFile.event_time < cutoff_time,
        )
        if record_space.device_id:
            query = query.filter(RecordFile.device_id == record_space.device_id)

        records = query.all()

        def _merge_orphan(result: Dict) -> Dict:
            orphan_result = cleanup_orphan_minio_record_objects(space_id, save_time_hours)
            result['orphan_deleted_count'] = orphan_result.get('deleted_count', 0)
            result['orphan_scanned_count'] = orphan_result.get('scanned_count', 0)
            result['orphan_error_count'] = orphan_result.get('error_count', 0)
            return result

        minio_client = get_minio_client()
        if not minio_storage_enabled() or not minio_client.bucket_exists(bucket_name):
            return _merge_orphan({
                'processed_count': 0, 'deleted_count': 0, 'archived_count': 0, 'error_count': 0,
            })

        if not records:
            return _merge_orphan({
                'processed_count': 0, 'deleted_count': 0, 'archived_count': 0, 'error_count': 0,
            })

        archive_bucket_name = current_app.config.get('MINIO_ARCHIVE_BUCKET', 'record-archive')
        if save_mode == 1 and not minio_client.bucket_exists(archive_bucket_name):
            minio_client.make_bucket(archive_bucket_name)
        if save_mode == 1:
            ensure_bucket_private(minio_client, archive_bucket_name)

        processed_count = deleted_count = archived_count = error_count = 0

        if save_mode == 0:
            object_names = []
            for record in records:
                try:
                    minio_client.remove_object(bucket_name, record.object_name)
                    if record.thumbnail_url:
                        thumb = extract_prefix_from_url(record.thumbnail_url)
                        if thumb:
                            try:
                                minio_client.remove_object(bucket_name, thumb)
                            except Exception:
                                pass
                    object_names.append(record.object_name)
                    deleted_count += 1
                    processed_count += 1
                except Exception as e:
                    error_count += 1
                    logger.error(f"删除录像失败: {record.object_name}, error={e}")
            delete_record_files_metadata(
                bucket_name,
                object_names,
                tenant_id=tenant_id,
                space_id=space_id,
            )
        else:
            device_groups: Dict[str, list] = {}
            for record in records:
                device_groups.setdefault(record.device_id, []).append(record)

            for device_id, record_list in device_groups.items():
                try:
                    zip_buffer = io.BytesIO()
                    archived_records = []
                    with zipfile.ZipFile(zip_buffer, 'w', zipfile.ZIP_DEFLATED) as zip_file:
                        for record in record_list:
                            try:
                                data = minio_client.get_object(bucket_name, record.object_name)
                                file_content = data.read()
                                data.close()
                                data.release_conn()
                                zip_file.writestr(record.filename, file_content)
                                archived_records.append(record)
                            except Exception as e:
                                error_count += 1
                                logger.error(f"处理录像失败: {record.object_name}, error={e}")

                    if zip_buffer.tell() > 0:
                        archive_object_name = (
                            f"tenants/{tenant_id}/{device_id}/archives/"
                            f"{datetime.utcnow().strftime('%Y%m%d_%H%M%S_%f')}.zip"
                        )
                        _upload_verified_archive(
                            minio_client, archive_bucket_name,
                            archive_object_name, zip_buffer)
                        archived_count += 1
                        deleted_names = []
                        for record in archived_records:
                            try:
                                minio_client.remove_object(
                                    bucket_name, record.object_name)
                                deleted_names.append(record.object_name)
                                deleted_count += 1
                            except Exception as e:
                                error_count += 1
                                logger.error(
                                    f"删除已归档录像失败: {record.object_name}, error={e}")
                        if deleted_names:
                            delete_record_files_metadata(
                                bucket_name,
                                deleted_names,
                                tenant_id=tenant_id,
                                space_id=space_id,
                            )
                            processed_count += len(deleted_names)
                except Exception as e:
                    error_count += len(record_list)
                    logger.error(f"归档设备录像失败: device_id={device_id}, error={e}", exc_info=True)

        return _merge_orphan({
            'processed_count': processed_count,
            'deleted_count': deleted_count,
            'archived_count': archived_count,
            'error_count': error_count,
        })
    except Exception as e:
        logger.error(f"清理过期录像失败: {str(e)}", exc_info=True)
        raise RuntimeError(f"清理过期录像失败: {str(e)}")


def sync_record_videos_metadata(space_id: int) -> Dict:
    """从 MinIO 同步录像元数据到数据库"""
    return sync_record_files_from_minio(space_id)


def inspect_recording_storage_drift(
    records=None,
    space_id: Optional[int] = None,
    device_id: Optional[str] = None,
    now: Optional[datetime] = None,
    retention_hours: Optional[int] = None,
    disk_probe: Optional[dict] = None,
    cache_flush_events: Optional[List[dict]] = None,
    free_ratio_threshold: float = 0.05,
    cursor: Optional[str] = None,
    limit: int = 200,
    tenant_id=None,
) -> Dict[str, Any]:
    """Inspect DB/disk recording drift without deleting evidence metadata."""
    now = _normalize_availability_time(now or datetime.utcnow())
    page_limit = _normalize_drift_limit(limit)
    next_cursor = None
    has_more = False
    if records is not None:
        resolved_records = list(records)[:page_limit]
    else:
        resolved_records, next_cursor, has_more = _query_records_for_drift(
            space_id=space_id,
            device_id=device_id,
            tenant_id=tenant_id,
            cursor=cursor,
            limit=page_limit,
            now=now,
            retention_hours=retention_hours,
        )
    issues: List[dict] = []
    checked_records = []
    retention_cutoff = None
    if retention_hours is not None:
        try:
            retention_cutoff = now - timedelta(hours=int(retention_hours))
        except (TypeError, ValueError):
            retention_cutoff = None

    for record in resolved_records:
        if tenant_id is not None and str(
                _record_text(record, 'tenant_id', 'tenantId')) != str(tenant_id):
            continue
        if device_id and _record_text(record, 'device_id', 'deviceId') != device_id:
            continue
        record_info = _record_drift_info(record, space_id)
        storage_probe = _record_storage_probe(record_info)
        record_info['storage_probe'] = storage_probe
        checked_records.append(record_info)
        if not storage_probe.get('exists'):
            reason = storage_probe.get('reason') or 'probe_failed'
            issue_policy = {
                'file_missing': (
                    'filesystem', 'error', False, 'delete_db_metadata_after_review'),
                'permission_denied': (
                    'permission', 'error', False, 'repair_storage_credentials_or_policy'),
                'service_unavailable': (
                    'service', 'error', True, 'retry_storage_probe_after_service_recovery'),
                'probe_failed': (
                    'probe', 'error', True, 'inspect_storage_probe_configuration'),
            }
            category, severity, retryable, suggested_action = issue_policy.get(
                reason, issue_policy['probe_failed'])
            issues.append(_recording_storage_issue(
                reason,
                category,
                record_info,
                severity=severity,
                retryable=retryable,
                suggested_action=suggested_action,
            ))
        event_time = _normalize_availability_time(getattr(record, 'event_time', None))
        if retention_cutoff and event_time and event_time < retention_cutoff:
            issues.append(_recording_storage_issue(
                'retention_expired',
                'retention',
                record_info,
                severity='warning',
                retryable=False,
                suggested_action='verify_retention_cleanup',
            ))

    disk_status = _resolve_recording_disk_probe(disk_probe, checked_records)
    if disk_status and _disk_probe_is_full(disk_status, free_ratio_threshold):
        issues.append({
            'reason': 'disk_full',
            'category': 'storage',
            'severity': 'critical',
            'retryable': True,
            'source': 'recording_storage',
            'suggested_action': 'free_space_or_expand_recording_disk',
            'detail': disk_status,
        })

    for event in cache_flush_events or []:
        if device_id and str(event.get('device_id') or event.get('deviceId') or '').strip() != device_id:
            continue
        issues.append({
            'reason': 'cache_flush_failed',
            'category': 'cache',
            'severity': 'error',
            'retryable': True,
            'source': 'record_cache',
            'suggested_action': 'inspect_cache_flush_worker_and_storage_io',
            'detail': dict(event),
        })

    issue_reasons = {}
    for issue in issues:
        reason = issue.get('reason') or 'unknown'
        issue_reasons[reason] = issue_reasons.get(reason, 0) + 1

    return {
        'space_id': space_id,
        'device_id': device_id,
        'checked_at': now.isoformat() if now else None,
        'records': checked_records,
        'issues': issues,
        'disk': disk_status,
        'pagination': {
            'cursor': cursor,
            'next_cursor': next_cursor,
            'has_more': has_more,
            'limit': page_limit,
        },
        'summary': {
            'record_count': len(checked_records),
            'issue_count': len(issues),
            'issue_reasons': issue_reasons,
            'standard_reason_keys': list(STANDARD_RECORD_GAP_REASON_KEYS),
            'healthy': len(issues) == 0,
        },
}


def _query_records_for_drift(
    space_id: Optional[int],
    device_id: Optional[str],
    tenant_id=None,
    cursor: Optional[str] = None,
    limit: int = 200,
    now: Optional[datetime] = None,
    retention_hours: Optional[int] = None,
):
    page_limit = _normalize_drift_limit(limit)
    query = RecordFile.query
    if tenant_id is not None:
        query = query.filter(RecordFile.tenant_id == int(tenant_id))
    if space_id is not None:
        query = query.filter(RecordFile.space_id == space_id)
    if device_id:
        query = query.filter(RecordFile.device_id == device_id)
    now = _normalize_availability_time(now or datetime.utcnow())
    lookback_hours = _configured_drift_lookback_hours(retention_hours)
    if lookback_hours:
        query = query.join(RecordSpace, and_(
            RecordSpace.tenant_id == RecordFile.tenant_id,
            RecordSpace.id == RecordFile.space_id,
        )).filter(or_(
            RecordFile.event_time >= now - timedelta(hours=lookback_hours),
            RecordSpace.save_time == 0,
        ))
    cursor_value = _decode_drift_cursor(cursor)
    if cursor_value:
        cursor_time, cursor_id = cursor_value
        query = query.filter(or_(
            RecordFile.event_time > cursor_time,
            and_(RecordFile.event_time == cursor_time, RecordFile.id > cursor_id),
        ))
    rows = query.order_by(RecordFile.event_time.asc(), RecordFile.id.asc()).limit(
        page_limit + 1).all()
    has_more = len(rows) > page_limit
    records = rows[:page_limit]
    next_cursor = _encode_drift_cursor(records[-1]) if has_more and records else None
    return records, next_cursor, has_more


def _normalize_drift_limit(value) -> int:
    try:
        return max(1, min(int(value), 500))
    except (TypeError, ValueError):
        return 200


def _configured_drift_lookback_hours(retention_hours) -> int:
    try:
        configured = max(1, min(int(os.environ.get(
            'YFEIEYE_RECORD_DRIFT_LOOKBACK_HOURS', '720')), 24 * 365))
    except (TypeError, ValueError):
        configured = 720
    try:
        retention = max(0, int(retention_hours)) if retention_hours is not None else 0
    except (TypeError, ValueError):
        retention = 0
    return max(configured, retention)


def _encode_drift_cursor(record) -> str:
    event_time = _normalize_availability_time(getattr(record, 'event_time', None))
    record_id = getattr(record, 'id', None)
    if event_time is None or record_id is None:
        raise ValueError('recording drift cursor fields are missing')
    payload = json.dumps({
        'eventTime': event_time.isoformat(),
        'id': int(record_id),
    }, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return base64.urlsafe_b64encode(payload).decode('ascii').rstrip('=')


def _decode_drift_cursor(cursor):
    cursor = str(cursor or '').strip()
    if not cursor:
        return None
    try:
        padding = '=' * (-len(cursor) % 4)
        payload = json.loads(base64.urlsafe_b64decode(cursor + padding).decode('utf-8'))
        event_time = datetime.fromisoformat(str(payload['eventTime']).replace('Z', '+00:00'))
        event_time = _normalize_availability_time(event_time)
        record_id = int(payload['id'])
    except (KeyError, TypeError, ValueError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError('invalid recording drift cursor') from exc
    if event_time is None or record_id < 0:
        raise ValueError('invalid recording drift cursor')
    return event_time, record_id


def _record_drift_info(record, fallback_space_id=None) -> dict:
    event_time = _normalize_availability_time(getattr(record, 'event_time', None))
    return {
        'record_id': getattr(record, 'id', None),
        'space_id': getattr(record, 'space_id', None) or fallback_space_id,
        'device_id': _record_text(record, 'device_id', 'deviceId'),
        'bucket_name': _record_text(record, 'bucket_name', 'bucketName'),
        'object_name': _record_text(record, 'object_name', 'objectName'),
        'url': _record_text(record, 'url'),
        'local_path': _record_local_path(record),
        'event_time': event_time.isoformat() if event_time else None,
        'duration': getattr(record, 'duration', None),
    }


def _record_storage_probe(record_info: dict) -> dict:
    if minio_storage_enabled():
        bucket_name = record_info.get('bucket_name')
        object_name = record_info.get('object_name')
        if bucket_name and object_name:
            try:
                get_minio_client().stat_object(bucket_name, object_name)
                return {'exists': True, 'backend': 'minio'}
            except S3Error as exc:
                code = str(getattr(exc, 'code', '') or '').strip()
                if code in {'NoSuchKey', 'NoSuchObject', 'NoSuchBucket', 'NotFound'}:
                    reason = 'file_missing'
                elif code in {'AccessDenied', 'InvalidAccessKeyId', 'SignatureDoesNotMatch'}:
                    reason = 'permission_denied'
                elif code.startswith('5') or code in {
                        'InternalError', 'ServiceUnavailable', 'SlowDown', 'RequestTimeout'}:
                    reason = 'service_unavailable'
                else:
                    reason = 'probe_failed'
                return {
                    'exists': False,
                    'backend': 'minio',
                    'reason': reason,
                    'error_code': code or type(exc).__name__,
                }
            except (TimeoutError, ConnectionError) as exc:
                return {
                    'exists': False,
                    'backend': 'minio',
                    'reason': 'service_unavailable',
                    'error_code': type(exc).__name__,
                }
            except Exception as exc:
                return {
                    'exists': False,
                    'backend': 'minio',
                    'reason': 'probe_failed',
                    'error_code': type(exc).__name__,
                }

    local_path = record_info.get('local_path')
    if local_path:
        return {
            'exists': os.path.isfile(local_path),
            'backend': 'filesystem',
            'reason': None if os.path.isfile(local_path) else 'file_missing',
        }

    if minio_storage_enabled():
        return {
            'exists': False,
            'backend': 'minio',
            'reason': 'probe_failed',
            'error_code': 'storage_metadata_missing',
        }

    return {
        'exists': False,
        'backend': 'filesystem',
        'reason': 'file_missing',
    }


def _record_storage_exists(record_info: dict) -> bool:
    return bool(_record_storage_probe(record_info).get('exists'))


def _record_local_path(record) -> str:
    try:
        return _resolve_local_record_file(
            record,
            _record_text(record, 'object_name', 'objectName'),
        )
    except (LocalMediaPathError, ValueError):
        return ''


def _resolve_local_record_file(record, object_name: str) -> str:
    url = _record_text(record, 'url') if record is not None else ''
    if url.lower().startswith('file:'):
        raise ValueError('record metadata file URI is not allowed')
    if url and not url.startswith(('/video/', '/api/')):
        candidate = url
    else:
        if not object_name:
            raise ValueError('record object name is required')
        from app.services.media_dvr_utils import resolve_playback_absolute_path
        from app.services.playback_disk_guard_service import get_srs_record_dir

        candidate = resolve_playback_absolute_path(
            os.path.join(get_srs_record_dir(), object_name.replace('/', os.sep)),
        )
    try:
        return resolve_allowed_local_media_file(candidate)
    except LocalMediaPathError as exc:
        raise ValueError(f'record local media path denied: {exc.reason}') from exc


def _recording_storage_issue(reason: str, category: str, record_info: dict, **extra) -> dict:
    issue = {
        'reason': reason,
        'category': category,
        'record_id': record_info.get('record_id'),
        'space_id': record_info.get('space_id'),
        'device_id': record_info.get('device_id'),
        'object_name': record_info.get('object_name'),
        'local_path': record_info.get('local_path'),
        'source': 'record_file',
        'detail': record_info,
    }
    issue.update(extra)
    return issue


def _resolve_recording_disk_probe(disk_probe: Optional[dict], checked_records: List[dict]) -> Optional[dict]:
    if isinstance(disk_probe, dict):
        total = _safe_int(disk_probe.get('total_bytes') or disk_probe.get('totalBytes'))
        free = _safe_int(disk_probe.get('free_bytes') or disk_probe.get('freeBytes'))
        used = _safe_int(disk_probe.get('used_bytes') or disk_probe.get('usedBytes'))
        if total is not None and free is not None and used is None:
            used = max(0, total - free)
        result = dict(disk_probe)
        if total is not None:
            result['total_bytes'] = total
        if free is not None:
            result['free_bytes'] = free
        if used is not None:
            result['used_bytes'] = used
        if total and free is not None:
            result['free_ratio'] = free / total
        return result

    for record in checked_records:
        local_path = record.get('local_path')
        if not local_path:
            continue
        probe_path = local_path if os.path.isdir(local_path) else os.path.dirname(local_path)
        if not probe_path or not os.path.exists(probe_path):
            continue
        usage = shutil.disk_usage(probe_path)
        return {
            'path': probe_path,
            'total_bytes': usage.total,
            'used_bytes': usage.used,
            'free_bytes': usage.free,
            'free_ratio': usage.free / usage.total if usage.total else None,
        }
    return None


def _disk_probe_is_full(disk_status: dict, free_ratio_threshold: float) -> bool:
    free_ratio = disk_status.get('free_ratio')
    if free_ratio is None:
        total = _safe_int(disk_status.get('total_bytes'))
        free = _safe_int(disk_status.get('free_bytes'))
        free_ratio = (free / total) if total and free is not None else None
    return free_ratio is not None and free_ratio <= free_ratio_threshold


def _safe_int(value):
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _parse_day_range(date_str: str):
    """解析 YYYY-MM-DD 为当日起止时间（与 event_time 一致的 naive 本地时间）。"""
    day_start = datetime.strptime(date_str.strip(), '%Y-%m-%d')
    day_end = day_start + timedelta(days=1) - timedelta(microseconds=1)
    return day_start, day_end


def _alert_time_naive(alert_time: datetime) -> datetime:
    from app.utils.service_urls import normalize_to_shanghai_naive

    return normalize_to_shanghai_naive(alert_time)


def _match_alerts_to_segment(
    alerts: List[Alert],
    seg_start: datetime,
    seg_end: datetime,
    record_url: str,
) -> List[dict]:
    """将告警匹配到录像片段（时间窗重叠或 record_path 一致）。"""
    matched = []
    record_url = (record_url or '').strip()
    for alert in alerts:
        alert_naive = _alert_time_naive(alert.time)
        in_window = alert_naive is not None and seg_start <= alert_naive <= seg_end
        same_record = record_url and (alert.record_path or '').strip() == record_url
        if in_window or same_record:
            matched.append(_alert_to_dict(alert))
    return matched


# 相邻片段间隔 <= 该值（秒）则视为同一段连续录像
SESSION_MERGE_GAP_SEC = 2


def _merge_timeline_ranges(timeline: List[dict], gap_sec: int = SESSION_MERGE_GAP_SEC) -> List[dict]:
    """合并时间轴上相邻的录像覆盖区间。"""
    if not timeline:
        return []
    merged: List[dict] = []
    current = {
        'start_offset_sec': timeline[0]['start_offset_sec'],
        'end_offset_sec': timeline[0]['end_offset_sec'],
        'has_recording': True,
        'has_alert': timeline[0].get('has_alert', False),
        'alert_count': timeline[0].get('alert_count', 0),
        'segment_ids': [timeline[0].get('segment_id')],
    }
    for item in timeline[1:]:
        gap = item['start_offset_sec'] - current['end_offset_sec']
        if gap <= gap_sec:
            current['end_offset_sec'] = item['end_offset_sec']
            current['has_alert'] = current['has_alert'] or item.get('has_alert', False)
            current['alert_count'] = current.get('alert_count', 0) + item.get('alert_count', 0)
            current['segment_ids'].append(item.get('segment_id'))
        else:
            merged.append(current)
            current = {
                'start_offset_sec': item['start_offset_sec'],
                'end_offset_sec': item['end_offset_sec'],
                'has_recording': True,
                'has_alert': item.get('has_alert', False),
                'alert_count': item.get('alert_count', 0),
                'segment_ids': [item.get('segment_id')],
            }
    merged.append(current)
    return merged


def _build_session_groups(segments: List[dict], gap_sec: int = SESSION_MERGE_GAP_SEC) -> List[dict]:
    """将相邻片段合并为连续录像会话（供左侧树与会话播放使用）。"""
    if not segments:
        return []
    groups: List[dict] = []
    current: Optional[dict] = None
    for seg in segments:
        if current is None:
            current = {
                'group_id': len(groups),
                'start_time': seg.get('start_time'),
                'end_time': seg.get('end_time'),
                'start_offset_sec': seg.get('start_offset_sec', 0),
                'end_offset_sec': seg.get('end_offset_sec', 0),
                'segment_count': 1,
                'has_alert': seg.get('has_alert', False),
                'alert_count': seg.get('alert_count', 0),
                'segments': [seg],
            }
        else:
            gap = seg.get('start_offset_sec', 0) - current['end_offset_sec']
            if gap <= gap_sec:
                current['end_offset_sec'] = seg.get('end_offset_sec', 0)
                current['end_time'] = seg.get('end_time')
                current['segment_count'] += 1
                current['has_alert'] = current['has_alert'] or seg.get('has_alert', False)
                current['alert_count'] += seg.get('alert_count', 0)
                current['segments'].append(seg)
            else:
                groups.append(current)
                current = {
                    'group_id': len(groups),
                    'start_time': seg.get('start_time'),
                    'end_time': seg.get('end_time'),
                    'start_offset_sec': seg.get('start_offset_sec', 0),
                    'end_offset_sec': seg.get('end_offset_sec', 0),
                    'segment_count': 1,
                    'has_alert': seg.get('has_alert', False),
                    'alert_count': seg.get('alert_count', 0),
                    'segments': [seg],
                }
    if current is not None:
        groups.append(current)
    return groups


def find_segment_for_alert(device_id: str, alert_id: int,
                           tenant_id=None) -> Optional[dict]:
    """根据告警 ID 定位所属录像片段及空间。"""
    alert = Alert.query.get(alert_id)
    if not alert or alert.device_id != device_id:
        return None

    space = RecordSpace.query.filter_by(
        tenant_id=int(tenant_id), device_id=device_id).first()
    if not space:
        return None

    alert_naive = _alert_time_naive(alert.time)
    if not alert_naive:
        return None

    date_str = alert_naive.strftime('%Y-%m-%d')
    day_start, day_end = _parse_day_range(date_str)

    records = (
        RecordFile.query.filter(
            RecordFile.tenant_id == int(tenant_id),
            RecordFile.space_id == space.id,
            RecordFile.device_id == device_id,
            RecordFile.event_time >= day_start,
            RecordFile.event_time <= day_end,
        )
        .order_by(RecordFile.event_time.asc())
        .all()
    )

    record_url = (alert.record_path or '').strip()
    matched_seg = None
    for record in records:
        duration = int(record.duration or 30)
        seg_start = record.event_time
        seg_end = seg_start + timedelta(seconds=duration)
        same_record = record_url and (record.url or '').strip() == record_url
        in_window = seg_start <= alert_naive <= seg_end
        if same_record or in_window:
            matched_seg = record
            break

    if not matched_seg:
        return {
            'space_id': space.id,
            'device_id': device_id,
            'date': date_str,
            'alert_id': alert_id,
            'segment': None,
        }

    duration = int(matched_seg.duration or 30)
    seg_start = matched_seg.event_time
    seg_end = seg_start + timedelta(seconds=duration)
    start_offset = max(0, int((seg_start - day_start).total_seconds()))

    return {
        'space_id': space.id,
        'device_id': device_id,
        'date': date_str,
        'alert_id': alert_id,
        'segment': {
            **matched_seg.to_list_item(),
            'start_time': seg_start.isoformat() if seg_start else None,
            'end_time': seg_end.isoformat() if seg_end else None,
            'start_offset_sec': start_offset,
            'end_offset_sec': min(86400, start_offset + duration),
        },
    }


def query_recording_availability(
    device_id: str,
    begin_time=None,
    end_time=None,
    camera_id: Optional[str] = None,
    alert_time=None,
    time_range=None,
    tenant_id=None,
) -> Dict[str, Any]:
    """Return recording coverage for an incident window."""
    effective_device_id = (device_id or camera_id or '').strip()
    if not effective_device_id:
        raise ValueError('device_id 参数不能为空')

    window_start, window_end = _resolve_availability_window(begin_time, end_time, alert_time, time_range)
    try:
        tenant_id = int(tenant_id)
    except (TypeError, ValueError) as exc:
        raise ValueError('record tenant scope is required') from exc
    space = RecordSpace.query.filter_by(
        tenant_id=tenant_id, device_id=effective_device_id).first()
    if not space:
        return build_recording_availability(
            records=[],
            alerts=[],
            space_id=None,
            device_id=effective_device_id,
            camera_id=camera_id,
            begin_time=window_start,
            end_time=window_end,
            missing_reason='record_space_not_found',
        )

    lookback_seconds = max(3600, int((window_end - window_start).total_seconds()) + 300)
    records = (
        RecordFile.query.filter(
            RecordFile.tenant_id == tenant_id,
            RecordFile.space_id == space.id,
            RecordFile.device_id == effective_device_id,
            RecordFile.event_time >= window_start - timedelta(seconds=lookback_seconds),
            RecordFile.event_time <= window_end,
        )
        .order_by(RecordFile.event_time.asc())
        .all()
    )
    alerts = (
        Alert.query.filter(
            Alert.device_id == effective_device_id,
            Alert.time >= window_start,
            Alert.time <= window_end,
        )
        .order_by(Alert.time.asc())
        .all()
    )
    return build_recording_availability(
        records=records,
        alerts=alerts,
        space_id=space.id,
        device_id=effective_device_id,
        camera_id=camera_id,
        begin_time=window_start,
        end_time=window_end,
    )


def build_recording_availability(
    records,
    alerts,
    space_id,
    device_id: str,
    camera_id: Optional[str],
    begin_time: datetime,
    end_time: datetime,
    missing_reason: Optional[str] = None,
) -> Dict[str, Any]:
    """Build a Frigate-like review coverage window from local VIDEO metadata."""
    if end_time < begin_time:
        begin_time, end_time = end_time, begin_time

    available_segments: List[dict] = []
    probe_missing_segments: List[dict] = []
    for record in records or []:
        seg_start = _normalize_availability_time(getattr(record, 'event_time', None))
        if not seg_start:
            continue
        duration = max(1, int(getattr(record, 'duration', None) or 30))
        pre_capture_seconds = _record_positive_int(record, 'pre_capture_seconds', 'preCaptureSeconds', default_value=0)
        post_capture_seconds = _record_positive_int(record, 'post_capture_seconds', 'postCaptureSeconds', default_value=0)
        segment_start = seg_start - timedelta(seconds=pre_capture_seconds)
        segment_end = seg_start + timedelta(seconds=duration + post_capture_seconds)
        if segment_start >= end_time or segment_end <= begin_time:
            continue

        clipped_start = max(segment_start, begin_time)
        clipped_end = min(segment_end, end_time)
        play_url = _availability_record_url(record, space_id)
        probe = _availability_probe(record)
        if probe and probe.get('has_valid_video') is False:
            reason = probe.get('error') or 'probe_invalid'
            probe_missing_segments.append(_availability_missing_segment(
                clipped_start,
                clipped_end,
                {
                    'gap_reason': reason,
                    'reasonCode': reason,
                    'retryable': False,
                    'source': 'file_probe_failed',
                    'probe': probe,
                },
            ))
            continue
        matched_alerts = _match_alerts_to_segment(alerts or [], segment_start, segment_end, play_url)
        object_count = len(matched_alerts)
        motion = 1 if object_count > 0 else 0
        status = 'motion' if motion else 'available'
        retain_mode = _coverage_classification(
            _record_text(record, 'retention_mode', 'retentionMode', 'retain_mode', 'retainMode')
            or _record_text(record, 'source'),
            default_value='continuous',
        )
        coverage_source = 'alert' if matched_alerts else _coverage_classification(
            _record_text(record, 'coverage_source', 'coverageSource', 'record_source', 'recordSource')
            or _record_text(record, 'source'),
            default_value=retain_mode,
        )
        object_name = getattr(record, 'object_name', None)
        record_segment = {
            'index': 0,
            'record_uri': play_url,
            'space_id': space_id,
            'object_name': object_name,
            'segment_start_time': segment_start.isoformat(),
            'segment_end_time': segment_end.isoformat(),
            'clip_start_time': clipped_start.isoformat(),
            'clip_end_time': clipped_end.isoformat(),
        }
        available_segments.append({
            'id': getattr(record, 'id', None),
            'status': status,
            'start_time': clipped_start.isoformat(),
            'end_time': clipped_end.isoformat(),
            'segment_start_time': segment_start.isoformat(),
            'segment_end_time': segment_end.isoformat(),
            'duration': int((clipped_end - clipped_start).total_seconds()),
            'motion': motion,
            'object_count': object_count,
            'play_url': play_url,
            'record_uri': play_url,
            'retention_mode': _record_text(record, 'retention_mode', 'retentionMode') or 'unknown',
            'retain_mode': retain_mode,
            'coverage_source': coverage_source,
            'exportable': True,
            'non_exportable_reason': None,
            'pre_capture_seconds': pre_capture_seconds,
            'post_capture_seconds': post_capture_seconds,
            'review_overlap': clipped_start < end_time and clipped_end > begin_time,
            'probe': probe,
            'export_url': '/video/record/export',
            'export_payload': {
                'device_id': device_id,
                'camera_id': camera_id,
                'start_time': clipped_start.isoformat(),
                'end_time': clipped_end.isoformat(),
                'record_uri': play_url,
                'space_id': space_id,
                'object_name': object_name,
                'segment_start_time': segment_start.isoformat(),
                'segment_end_time': segment_end.isoformat(),
                'record_segments': [record_segment],
                'format': 'mp4',
            },
            'alerts': matched_alerts,
            'object_name': object_name,
            'space_id': space_id,
        })

    available_segments.sort(key=lambda item: item['start_time'])
    coverage_boundaries = sorted([*available_segments, *probe_missing_segments], key=lambda item: item['start_time'])
    missing_segments = [
        *probe_missing_segments,
        *_availability_missing_segments(begin_time, end_time, coverage_boundaries, missing_reason),
    ]
    segments = sorted(
        [*available_segments, *missing_segments],
        key=lambda item: item['start_time'],
    )
    motion_segments = [item for item in available_segments if item.get('status') == 'motion']
    available_seconds = sum(_segment_seconds(item) for item in available_segments)
    missing_seconds = sum(_segment_seconds(item) for item in missing_segments)
    object_count = sum(int(item.get('object_count') or 0) for item in available_segments)
    gap_reasons = {}
    for segment in missing_segments:
        reason = segment.get('gap_reason') or 'unknown'
        gap_reasons[reason] = gap_reasons.get(reason, 0) + _segment_seconds(segment)
    retention_modes = sorted({
        item.get('retention_mode') for item in available_segments if item.get('retention_mode')
    })

    return {
        'device_id': device_id,
        'camera_id': camera_id,
        'begin_time': begin_time.isoformat(),
        'end_time': end_time.isoformat(),
        'available': available_segments,
        'missing': missing_segments,
        'motion': motion_segments,
        'segments': segments,
        'summary': {
            'available': bool(available_segments),
            'available_seconds': available_seconds,
            'missing_seconds': missing_seconds,
            'motion_seconds': sum(_segment_seconds(item) for item in motion_segments),
            'object_count': object_count,
            'segment_count': len(available_segments),
            'missing_count': len(missing_segments),
            'gap_reasons': gap_reasons,
            'retention_modes': retention_modes,
            'probe_failed_count': len(probe_missing_segments),
        },
    }


def _resolve_availability_window(begin_time, end_time, alert_time=None, time_range=None):
    start = _parse_availability_time(begin_time)
    end = _parse_availability_time(end_time)
    if start and end:
        return (start, end) if end >= start else (end, start)

    alert_dt = _parse_availability_time(alert_time)
    if not alert_dt:
        raise ValueError('begin_time/end_time 或 alert_time 参数不能为空')
    seconds = _parse_positive_int(time_range, 300)
    return alert_dt - timedelta(seconds=seconds), alert_dt + timedelta(seconds=seconds)


def _parse_availability_time(value):
    if isinstance(value, datetime):
        return _normalize_availability_time(value)
    text = str(value or '').strip()
    if not text:
        return None
    normalized = text[:-1] + '+00:00' if text.endswith('Z') else text
    for parser in (
        lambda raw: datetime.fromisoformat(raw),
        lambda raw: datetime.strptime(raw, '%Y-%m-%d %H:%M:%S'),
        lambda raw: datetime.strptime(raw, '%Y-%m-%d %H:%M:%S.%f'),
    ):
        try:
            return _normalize_availability_time(parser(normalized))
        except ValueError:
            continue
    raise ValueError('时间格式错误，应为 YYYY-MM-DD HH:mm:ss 或 ISO-8601')


def _normalize_availability_time(value):
    if value is None:
        return None
    if value.tzinfo is None:
        return value
    return value.astimezone().replace(tzinfo=None)


def _parse_positive_int(value, default_value: int) -> int:
    try:
        parsed = int(value)
        return parsed if parsed > 0 else default_value
    except (TypeError, ValueError):
        return default_value


def _coverage_classification(value, default_value='continuous') -> str:
    normalized = _normalize_gap_reason_token(value)
    aliases = {
        'all': 'continuous',
        'record': 'continuous',
        'recording': 'continuous',
        'dvr': 'continuous',
        'alerts': 'alert',
        'detections': 'detection',
    }
    normalized = aliases.get(normalized, normalized)
    if normalized in {'continuous', 'motion', 'alert', 'detection'}:
        return normalized
    return default_value


def _availability_record_url(record, space_id) -> str:
    object_name = (getattr(record, 'object_name', None) or '').strip()
    resolved_space_id = space_id or getattr(record, 'space_id', None)
    if object_name and resolved_space_id:
        return build_record_video_api_url(int(resolved_space_id), object_name)
    return (getattr(record, 'url', None) or '').strip()


def _availability_missing_segments(begin_time: datetime, end_time: datetime, available_segments: List[dict],
                                   reason: Optional[str] = None) -> List[dict]:
    missing: List[dict] = []
    cursor = begin_time
    for segment in available_segments:
        start = _parse_availability_time(segment.get('start_time'))
        end = _parse_availability_time(segment.get('end_time'))
        if not start or not end:
            continue
        if start > cursor:
            missing.append(_availability_missing_segment(cursor, start, reason))
        if end > cursor:
            cursor = end
    if cursor < end_time:
        missing.append(_availability_missing_segment(cursor, end_time, reason))
    return missing


def _availability_missing_segment(start_time: datetime, end_time: datetime, reason: Optional[str] = None) -> dict:
    gap = _normalize_gap_reason(reason)
    result = {
        'status': 'missing',
        'exportable': False,
        'non_exportable_reason': gap['reason'],
        'start_time': start_time.isoformat(),
        'end_time': end_time.isoformat(),
        'duration': int((end_time - start_time).total_seconds()),
        'motion': 0,
        'object_count': 0,
        'gap_reason': gap['reason'],
        'gap_reason_category': gap['category'],
        'retryable': gap['retryable'],
    }
    if reason:
        result['source'] = gap.get('source') or gap['reason']
    if gap.get('probe'):
        result['probe'] = gap['probe']
    return result


def _normalize_gap_reason(reason) -> dict:
    if isinstance(reason, dict):
        raw_reason = str(reason.get('gap_reason') or reason.get('reason') or reason.get('reasonCode') or '').strip()
        retryable = bool(reason.get('retryable')) if 'retryable' in reason else None
        source = str(reason.get('source') or '').strip() or None
        probe = reason.get('probe') if isinstance(reason.get('probe'), dict) else None
    else:
        raw_reason = str(reason or '').strip()
        retryable = None
        source = None
        probe = None
    normalized = _normalize_gap_reason_token(raw_reason)
    alias_map = {
        'file_expired': 'retention_expired',
    }
    normalized = alias_map.get(normalized, normalized)
    category_map = {
        'retention_expired': 'retention',
        'stream_interrupted': 'stream',
        'recording_disabled': 'configuration',
        'video_url_not_configured': 'configuration',
        'record_space_not_found': 'configuration',
        'record_not_found': 'configuration',
        'file_missing': 'filesystem',
        'probe_failed': 'probe',
        'permission_denied': 'permission',
        'service_unavailable': 'service',
        'disk_full': 'storage',
        'cache_flush_failed': 'cache',
        'probe_invalid': 'stream',
        'corrupt_segment': 'stream',
    }
    retryable_defaults = {
        'stream_interrupted': True,
        'service_unavailable': True,
        'probe_failed': True,
        'permission_denied': False,
        'retention_expired': False,
        'recording_disabled': False,
        'video_url_not_configured': False,
        'record_space_not_found': False,
        'record_not_found': False,
        'file_missing': False,
        'disk_full': False,
        'cache_flush_failed': True,
        'probe_invalid': False,
        'corrupt_segment': False,
    }
    return {
        'reason': normalized,
        'category': category_map.get(normalized, 'unknown'),
        'retryable': retryable if retryable is not None else retryable_defaults.get(normalized, False),
        'source': source,
        'probe': probe,
    }


def _normalize_gap_reason_token(value) -> str:
    text = str(value or '').strip().lower()
    if not text:
        return 'unknown'
    normalized = ''.join(ch if ch.isalnum() else '_' for ch in text)
    while '__' in normalized:
        normalized = normalized.replace('__', '_')
    return normalized.strip('_') or 'unknown'


def _availability_probe(record) -> dict:
    value = (
        getattr(record, 'probe_result', None)
        or getattr(record, 'probeResult', None)
        or getattr(record, 'probe', None)
        or {}
    )
    return dict(value) if isinstance(value, dict) else {}


def _record_text(record, *names) -> str:
    for name in names:
        value = getattr(record, name, None)
        if value is not None:
            text = str(value).strip()
            if text:
                return text
    return ''


def _record_positive_int(record, *names, default_value: int = 0) -> int:
    for name in names:
        try:
            value = getattr(record, name, None)
            if value is None:
                continue
            parsed = int(value)
            return parsed if parsed >= 0 else default_value
        except (TypeError, ValueError):
            continue
    return default_value


def _segment_seconds(segment: dict) -> int:
    start = _parse_availability_time(segment.get('start_time'))
    end = _parse_availability_time(segment.get('end_time'))
    if not start or not end or end <= start:
        return 0
    return int((end - start).total_seconds())


def list_record_video_dates(space_id: int, device_id: Optional[str] = None,
                            tenant_id=None) -> List[dict]:
    """列出有录像的日期及片段数量。"""
    record_space = RecordSpace.query.get_or_404(space_id)
    tenant_id = _space_tenant_id(record_space, tenant_id)
    effective_device_id = device_id or record_space.device_id

    query = db.session.query(
        func.date(RecordFile.event_time).label('record_date'),
        func.count(RecordFile.id).label('segment_count'),
    ).filter(
        RecordFile.tenant_id == tenant_id,
        RecordFile.space_id == space_id,
    )

    if effective_device_id:
        query = query.filter(RecordFile.device_id == effective_device_id)

    rows = (
        query.group_by(func.date(RecordFile.event_time))
        .order_by(func.date(RecordFile.event_time).desc())
        .all()
    )
    return [
        {
            'date': row.record_date.strftime('%Y-%m-%d') if hasattr(row.record_date, 'strftime') else str(row.record_date),
            'segment_count': int(row.segment_count or 0),
        }
        for row in rows
    ]


def list_record_videos_day_detail(
    space_id: int,
    date_str: str,
    device_id: Optional[str] = None,
    tenant_id=None,
) -> Dict[str, Any]:
    """获取指定日期的全部录像片段、时间轴覆盖及告警关联。"""
    record_space = RecordSpace.query.get_or_404(space_id)
    tenant_id = _space_tenant_id(record_space, tenant_id)
    effective_device_id = device_id or record_space.device_id
    day_start, day_end = _parse_day_range(date_str)

    query = RecordFile.query.filter(
        RecordFile.tenant_id == tenant_id,
        RecordFile.space_id == space_id,
        RecordFile.event_time >= day_start,
        RecordFile.event_time <= day_end,
    )
    if effective_device_id:
        query = query.filter(RecordFile.device_id == effective_device_id)

    records = query.order_by(RecordFile.event_time.asc()).all()

    alert_query = Alert.query.filter(
        Alert.time >= day_start,
        Alert.time <= day_end,
    )
    if effective_device_id:
        alert_query = alert_query.filter(Alert.device_id == effective_device_id)
    day_alerts = alert_query.order_by(Alert.time.asc()).all()

    segments: List[dict] = []
    timeline: List[dict] = []
    total_duration = 0
    alert_segment_count = 0

    for record in records:
        duration = int(record.duration or 30)
        seg_start = record.event_time
        seg_end = seg_start + timedelta(seconds=duration)
        matched_alerts = _match_alerts_to_segment(day_alerts, seg_start, seg_end, record.url)
        has_alert = len(matched_alerts) > 0
        if has_alert:
            alert_segment_count += 1
        total_duration += duration

        start_offset = max(0, int((seg_start - day_start).total_seconds()))
        end_offset = min(86400, start_offset + duration)

        segments.append({
            **record.to_list_item(),
            'start_time': seg_start.isoformat() if seg_start else None,
            'end_time': seg_end.isoformat() if seg_end else None,
            'has_alert': has_alert,
            'alert_count': len(matched_alerts),
            'alerts': matched_alerts,
            'start_offset_sec': start_offset,
            'end_offset_sec': end_offset,
        })
        timeline.append({
            'start_offset_sec': start_offset,
            'end_offset_sec': end_offset,
            'has_recording': True,
            'has_alert': has_alert,
            'segment_id': record.id,
            'alert_count': len(matched_alerts),
        })

    timeline_merged = _merge_timeline_ranges(timeline)
    session_groups = _build_session_groups(segments)

    return {
        'date': date_str,
        'device_id': effective_device_id,
        'space_id': space_id,
        'segments': segments,
        'timeline': timeline,
        'timeline_merged': timeline_merged,
        'session_groups': session_groups,
        'total_segments': len(segments),
        'total_sessions': len(session_groups),
        'total_duration_sec': total_duration,
        'alert_segment_count': alert_segment_count,
        'total_alert_count': len(day_alerts),
        'alerts': [_alert_to_dict(a) for a in day_alerts],
    }
