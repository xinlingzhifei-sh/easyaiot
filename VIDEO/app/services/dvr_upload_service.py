"""
DVR 段处理：

- standard/full/mini（默认）：转发 iot-sink（NFS 读盘 → MinIO → Playback/告警回填）
- edge / 本地存储：VIDEO 本机落 Playback + 回写告警 record_path（不经 MinIO/sink）
"""
import logging
import os
import re
import tempfile
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

import cv2
from minio.error import S3Error

from app.services.dvr_device_resolver import resolve_device_from_hook
from app.services.media_dvr_utils import (
    extract_thumbnail_from_video,
    ffprobe_video_duration_seconds,
    parse_srs_dvr_path_date,
    resolve_playback_absolute_path,
    srs_dvr_min_file_bytes,
    wait_dvr_file_stable,
)
from app.services.media_kafka_service import publish_dvr_dlq
from app.services.record_cache_flush_event_service import (
    record_cache_flush_failure,
    resolve_record_cache_flush_failure,
)
from app.utils.minio_bucket_policy import ensure_bucket_private
from app.utils.service_urls import (
    build_record_video_api_url,
    ensure_shanghai_aware,
    epoch_to_shanghai_datetime,
    minio_storage_enabled,
)
from models import Device, Playback, db

logger = logging.getLogger(__name__)

_TENANT_ID_PATTERN = re.compile(r'^[1-9][0-9]*$')


def _tenant_value(value) -> str:
    tenant_id = str(value or '').strip()
    if tenant_id and not _TENANT_ID_PATTERN.fullmatch(tenant_id):
        raise ValueError('DVR tenant id must be a positive integer')
    return tenant_id


def _resolve_dvr_tenant_id(event: Dict[str, Any], device=None, record_space=None) -> str:
    """Resolve a tenant without falling back to an unscoped system tenant."""
    candidates = (
        (event or {}).get('tenant_id'),
        (event or {}).get('tenantId'),
        getattr(device, 'tenant_id', None),
        getattr(device, 'tenantId', None),
        getattr(record_space, 'tenant_id', None),
        getattr(record_space, 'tenantId', None),
        os.environ.get('YFEIEYE_DVR_TENANT_ID'),
    )
    for candidate in candidates:
        tenant_id = _tenant_value(candidate)
        if tenant_id:
            return tenant_id
    raise ValueError('DVR tenant id is required')


def _build_dvr_object_name(tenant_id: str, device_id: str, date_dir: str,
                           filename: str) -> str:
    tenant_id = _tenant_value(tenant_id)
    if not tenant_id:
        raise ValueError('DVR tenant id is required')
    return f'tenants/{tenant_id}/{device_id}/{date_dir}/{filename}'


def _protected_record_url(space_id, object_name: str) -> str:
    return build_record_video_api_url(space_id, object_name)


def _persist_dvr_locally(event: Dict[str, Any]) -> bool:
    """edge/本地存储：登记 Playback 并回写告警 record_path（保留本地文件）。"""
    from models import Device, Playback, db
    from app.services.alert_service import patch_alerts_record
    from app.services.media_dvr_utils import (
        ffprobe_video_duration_seconds,
        parse_srs_dvr_segment_start_from_filename,
        resolve_playback_absolute_path,
        wait_dvr_file_stable,
    )
    from app.utils.service_urls import epoch_to_shanghai_datetime

    stream = (event.get('stream') or '').strip()
    file_path = (event.get('file_path') or event.get('file') or '').strip()
    cwd = (event.get('cwd') or '').strip()
    device_id = (event.get('device_id') or stream or '').strip()
    task_id = event.get('task_id')
    if not file_path:
        logger.warning('本地 DVR：缺少 file_path event=%s', event)
        return False

    absolute = resolve_playback_absolute_path(file_path, cwd)
    file_size = wait_dvr_file_stable(absolute)
    if file_size <= 0:
        logger.warning('本地 DVR：文件未就绪 path=%s', absolute)
        return False

    device = None
    if device_id:
        device = Device.query.filter_by(id=device_id).first()
    if device is None and stream:
        device = Device.query.filter_by(id=stream).first()
    if device is None and stream:
        device = Device.query.filter(Device.rtmp_stream.contains(f'/{stream}')).first()
    if device is None and stream:
        from app.services.dvr_device_resolver import resolve_stream_identity_from_hook

        resolved_task_id, resolved_device_id, resolved_device = resolve_stream_identity_from_hook(
            stream, absolute
        )
        if resolved_device is not None:
            device = resolved_device
            device_id = resolved_device_id or device_id
            task_id = task_id if task_id is not None else resolved_task_id
    if device is None:
        logger.info('本地 DVR：设备不存在，丢弃 stream=%s file=%s', stream, absolute)
        return True

    resolved_id = device.id
    from app.services.media_asset_service import get_or_default_policy
    recording_policy = get_or_default_policy(resolved_id)
    recording_mode = recording_policy.recording_mode or 'continuous'
    if recording_mode == 'off':
        try:
            from app.utils.media_path_security import resolve_allowed_media_file
            recording_root = os.path.realpath(os.path.expanduser(
                os.getenv('EDGE_RECORDING_ROOT') or os.getenv('MEDIA_HOST_DATA_ROOT') or '/data/local-storage'
            ))
            disposable = resolve_allowed_media_file(absolute, extra_roots=[recording_root])
            if disposable:
                os.unlink(disposable)
        except OSError as exc:
            logger.warning('已关闭录像的分片清理失败 device=%s path=%s: %s', resolved_id, absolute, exc)
        return True
    device_name = device.name or resolved_id
    shanghai = timezone(timedelta(hours=8))
    segment_start = parse_srs_dvr_segment_start_from_filename(absolute)
    if segment_start is not None:
        event_time = segment_start
    else:
        try:
            event_time = epoch_to_shanghai_datetime(os.path.getmtime(absolute))
        except OSError:
            event_time = datetime.now(shanghai)

    probed_duration = float(ffprobe_video_duration_seconds(absolute) or 0)
    hook_duration = event.get('duration') or event.get('dvr_duration')
    try:
        duration_seconds = probed_duration if probed_duration > 0 else max(0.0, float(hook_duration or 0))
    except (TypeError, ValueError):
        duration_seconds = probed_duration
    duration = max(1, int(round(duration_seconds))) if duration_seconds > 0 else 0

    # 库中存宿主机绝对路径；前端经 resolve_playback_display_url 转 VIDEO API
    store_path = absolute
    try:
        existing = Playback.query.filter(
            Playback.device_id == resolved_id,
            Playback.file_path == store_path,
        ).first()
        now = datetime.now(shanghai)
        if existing:
            existing.task_id = task_id
            existing.event_time = event_time
            existing.duration = duration
            existing.file_size = file_size
            existing.device_name = device_name
            existing.updated_at = now
        else:
            db.session.add(
                Playback(
                    file_path=store_path,
                    event_time=event_time,
                    device_id=resolved_id,
                    task_id=task_id,
                    device_name=device_name,
                    duration=duration,
                    file_size=file_size,
                    created_at=now,
                    updated_at=now,
                )
            )
        db.session.commit()
    except Exception as e:
        logger.error('本地 DVR：写入 Playback 失败 device=%s error=%s', resolved_id, e, exc_info=True)
        db.session.rollback()
        return False

    asset_id = None
    if (os.getenv('RECORDING_STORAGE_MODE') or '').strip().lower() == 'edge_local':
        try:
            from app.services.media_asset_service import upsert_media_asset
            from app.services.edge_media_spool_service import enqueue_asset_report, flush_pending_reports_async
            from app.utils.media_path_security import resolve_allowed_media_file

            recording_root = os.path.realpath(os.path.expanduser(
                os.getenv('EDGE_RECORDING_ROOT') or os.getenv('MEDIA_HOST_DATA_ROOT') or '/data/local-storage'
            ))
            allowed = resolve_allowed_media_file(absolute, extra_roots=[recording_root])
            if allowed is None:
                raise ValueError(f'DVR 文件不在边缘录像根目录内: {absolute}')
            object_key = os.path.relpath(str(allowed), recording_root).replace(os.sep, '/')
            asset_id = str(uuid.uuid4())
            duration_ms = int(round(duration_seconds * 1000)) if duration_seconds > 0 else None
            end_time = event_time + timedelta(milliseconds=duration_ms) if duration_ms else None
            report_payload = {
                'asset_id': asset_id,
                'asset_type': 'recording_segment',
                'device_id': resolved_id,
                'task_id': task_id,
                'source_node_id': int(os.getenv('COMPUTE_NODE_ID') or os.getenv('NODE_ID') or 0) or None,
                'storage_node_id': int(os.getenv('COMPUTE_NODE_ID') or os.getenv('NODE_ID') or 0) or None,
                'storage_generation': int(os.getenv('RECORDING_STORAGE_GENERATION') or 1),
                'storage_scope': 'edge',
                'storage_backend': 'local',
                'object_key': object_key,
                'status': 'ready',
                'start_time': event_time.isoformat(),
                'end_time': end_time.isoformat() if end_time else None,
                'duration_ms': duration_ms,
                'file_size': file_size,
                'content_type': 'video/x-flv' if absolute.lower().endswith('.flv') else 'video/mp4',
            }
            if recording_mode == 'event_only':
                ring_seconds = max(
                    300,
                    int(recording_policy.event_pre_seconds or 0)
                    + int(recording_policy.event_post_seconds or 0)
                    + int(os.getenv('EDGE_EVENT_CLIP_SETTLE_SECONDS') or 70) + 120,
                )
                report_payload['expires_at'] = (datetime.now(timezone.utc) + timedelta(seconds=ring_seconds)).isoformat()
            if report_payload['source_node_id'] is None:
                raise ValueError('edge_local 未配置 COMPUTE_NODE_ID/NODE_ID')
            upsert_media_asset(report_payload, authenticated_node_id=report_payload['source_node_id'])
            # event_only 分片只是边缘环形缓存，不进入中心连续录像时间轴。
            if recording_mode == 'continuous':
                enqueue_asset_report(report_payload)
                flush_pending_reports_async()
        except Exception as e:
            logger.error('边缘 DVR：登记统一资产失败 device=%s error=%s', resolved_id, e, exc_info=True)
            return False
    else:
        # 旧本地/mini 模式仍写入 record_file；该函数会登记中心兼容资产。
        try:
            from models import RecordSpace
            from app.services.space_file_metadata_service import upsert_record_file
            record_space = RecordSpace.query.filter_by(device_id=resolved_id).first()
            if record_space:
                filename = os.path.basename(absolute)
                task_prefix = f'task_{int(task_id)}/' if task_id is not None else ''
                object_name = f'{task_prefix}{filename}'
                upsert_record_file(
                    space_id=record_space.id,
                    device_id=resolved_id,
                    task_id=task_id,
                    object_name=object_name,
                    bucket_name=record_space.bucket_name or 'record-space',
                    filename=object_name,
                    file_size=file_size,
                    url=store_path,
                    duration=duration,
                    event_time=event_time.replace(tzinfo=None) if event_time.tzinfo else event_time,
                    source='dvr',
                )
        except Exception as e:
            logger.warning('本地 DVR：写入 record_file 失败 device=%s error=%s', resolved_id, e)

    try:
        if recording_mode == 'event_only':
            raise LookupError('event_only 等待事件片段生成，不回写环形缓存分片')
        patch_alerts_record(
            {
                'device_id': resolved_id,
                'task_id': task_id,
                'event_time': event_time.astimezone(shanghai).strftime('%Y-%m-%d %H:%M:%S'),
                'duration': duration,
                'file_path': f'/video/media/assets/{asset_id}/content' if asset_id else store_path,
            }
        )
    except LookupError:
        pass
    except Exception as e:
        logger.warning('本地 DVR：回写告警 record_path 失败 device=%s error=%s', resolved_id, e)

    logger.info(
        '本地 DVR 已落盘 task=%s device=%s path=%s size=%s duration=%s asset=%s',
        task_id,
        resolved_id,
        store_path,
        file_size,
        duration,
        asset_id,
    )
    return True


def process_dvr_event(event: Dict[str, Any]) -> bool:
    """Process one DVR event and journal unresolved cache-to-storage failures."""
    try:
        success = _process_dvr_event_impl(event)
    except Exception as exc:
        _persist_cache_flush_failure_safely(event, f'{type(exc).__name__}: {exc}')
        raise
    if success:
        try:
            resolve_record_cache_flush_failure(event)
        except Exception:
            logger.exception('failed to resolve persisted DVR cache flush event')
    else:
        _persist_cache_flush_failure_safely(
            event, 'dvr_cache_flush_worker_returned_false')
    return success


def _persist_cache_flush_failure_safely(event: Dict[str, Any], error: str):
    try:
        record_cache_flush_failure(event, error)
    except Exception:
        logger.exception('failed to persist DVR cache flush failure event')


def _process_dvr_event_impl(event: Dict[str, Any]) -> bool:
    """处理单条 DVR Kafka 事件或 Hook 同步任务。成功返回 True。"""
    stream = event.get('stream', '') or ''
    file_path = event.get('file_path', '') or ''
    cwd = event.get('cwd', '') or ''
    device_id = event.get('device_id') or stream

    device = Device.query.get(device_id) if device_id else None
    if not device:
        resolved_id, device = resolve_device_from_hook(stream, file_path)
        if device:
            device_id = resolved_id

    if not device:
        absolute_file_path = resolve_playback_absolute_path(file_path, cwd) if file_path else ''
        if absolute_file_path:
            from app.services.playback_disk_guard_service import remove_playback_file
            remove_playback_file(absolute_file_path, reason='设备已删除')
        logger.info('DVR 上传：设备不存在，已丢弃本地回放 stream=%s file=%s', stream, file_path)
        return True

    from app.services.record_space_service import (
        create_record_space_for_device,
        get_minio_client,
        get_record_space_by_device_id,
    )

    try:
        tenant_id = _resolve_dvr_tenant_id(event, device=device)
    except ValueError as exc:
        logger.error('DVR tenant resolution failed device_id=%s error=%s', device_id, exc)
        return False

    record_space = get_record_space_by_device_id(device_id, tenant_id=tenant_id)
    if not record_space:
        try:
            record_space = create_record_space_for_device(
                device_id, device.name, tenant_id=tenant_id)
        except Exception as e:
            logger.error('创建设备录像空间失败 device_id=%s error=%s', device_id, e, exc_info=True)
            return False

    event.setdefault('space_id', getattr(record_space, 'id', None))
    if str(getattr(record_space, 'tenant_id', '') or '') != tenant_id:
        logger.error('DVR space tenant mismatch device_id=%s', device_id)
        return False
    event['tenant_id'] = tenant_id

    absolute_file_path = resolve_playback_absolute_path(file_path, cwd)
    file_size = wait_dvr_file_stable(absolute_file_path)
    if file_size <= 0:
        min_bytes = srs_dvr_min_file_bytes()
        try:
            if os.path.exists(absolute_file_path):
                sz = os.path.getsize(absolute_file_path)
                if 0 < sz < min_bytes:
                    logger.info(
                        'DVR 片段过小已丢弃 file=%s size=%s min=%s',
                        absolute_file_path, sz, min_bytes,
                    )
                    _cleanup_local_file(absolute_file_path, device_id)
                    return True
        except OSError:
            pass
        logger.warning('DVR 文件未就绪或过小 file=%s', absolute_file_path)
        return False

    try:
        from app.services.playback_disk_guard_service import ensure_playback_path_deletable
        ensure_playback_path_deletable(absolute_file_path)
    except Exception as perm_err:
        logger.warning('修复回放文件权限失败 file=%s error=%s', absolute_file_path, perm_err)

    parsed_date_dir, parsed_record_time = parse_srs_dvr_path_date(absolute_file_path)
    if parsed_date_dir and parsed_record_time:
        date_dir = parsed_date_dir
        record_time = parsed_record_time
    else:
        try:
            record_time = epoch_to_shanghai_datetime(os.path.getmtime(absolute_file_path))
            date_dir = record_time.strftime('%Y/%m/%d')
        except OSError:
            record_time = datetime.now(timezone(timedelta(hours=8)))
            date_dir = record_time.strftime('%Y/%m/%d')

    filename = os.path.basename(absolute_file_path)
    file_ext = os.path.splitext(filename)[1].lower()
    legacy_object_name = f'{device_id}/{date_dir}/{filename}'
    object_name = _build_dvr_object_name(
        tenant_id, device_id, date_dir, filename)

    from models import RecordFile
    existing_rf = RecordFile.query.filter_by(
        tenant_id=int(tenant_id),
        space_id=record_space.id,
        device_id=device_id,
        object_name=object_name,
    ).first()
    if existing_rf:
        file_path_url = _protected_record_url(record_space.id, object_name)
        if (existing_rf.url or '').strip() != file_path_url:
            existing_rf.url = file_path_url
            db.session.commit()
        if minio_storage_enabled():
            _cleanup_local_file(absolute_file_path, device_id)
        duration = int(existing_rf.duration or 0) or int(ffprobe_video_duration_seconds(absolute_file_path))
        _patch_alert_record(
            tenant_id, device_id, record_time, duration, file_path_url)
        logger.debug('DVR 已存在元数据，仍回写告警 record_path object=%s', object_name)
        return True
    legacy_rf = None
    if tenant_id == '1':
        legacy_rf = RecordFile.query.filter_by(
            tenant_id=1,
            space_id=record_space.id,
            device_id=device_id,
            object_name=legacy_object_name,
        ).first()

    if not minio_storage_enabled():
        content_type_map = {
            '.mp4': 'video/mp4', '.flv': 'video/x-flv', '.avi': 'video/x-msvideo',
            '.mov': 'video/quicktime', '.mkv': 'video/x-matroska', '.ts': 'video/mp2t',
        }
        local_content_type = content_type_map.get(file_ext, 'video/mp4')
        local_bucket_name = record_space.bucket_name
        file_path_url = absolute_file_path
        duration = int(ffprobe_video_duration_seconds(absolute_file_path))
        _upsert_playback(
            tenant_id, device, device_id, file_path_url, object_name,
            record_time, file_size, duration, None)
        _upsert_record_metadata(record_space, device_id, object_name, local_bucket_name, filename,
                                file_size, local_content_type, file_path_url, None, duration, record_time)
        _patch_alert_record(
            tenant_id, device_id, record_time, duration, file_path_url)
        logger.info('mini 形态 DVR 保留本地路径 device_id=%s path=%s size=%s', device_id, absolute_file_path, file_size)
        return True

    content_type_map = {
        '.mp4': 'video/mp4', '.flv': 'video/x-flv', '.avi': 'video/x-msvideo',
        '.mov': 'video/quicktime', '.mkv': 'video/x-matroska', '.ts': 'video/mp2t',
    }
    content_type = content_type_map.get(file_ext, 'video/mp4')
    bucket_name = record_space.bucket_name
    minio_client = get_minio_client()

    if not minio_client.bucket_exists(bucket_name):
        try:
            minio_client.make_bucket(bucket_name)
        except Exception as e:
            logger.error('创建 MinIO bucket 失败 bucket=%s error=%s', bucket_name, e, exc_info=True)
            return False
    ensure_bucket_private(minio_client, bucket_name)

    try:
        minio_client.fput_object(bucket_name, object_name, absolute_file_path, content_type=content_type)
    except S3Error as e:
        logger.error('MinIO 上传失败 device_id=%s object=%s error=%s', device_id, object_name, e, exc_info=True)
        publish_dvr_dlq(event, str(e))
        return False

    file_path_url = _protected_record_url(record_space.id, object_name)
    if legacy_rf is not None:
        logger.info(
            'tenant-1 legacy DVR metadata remains read-only object=%s',
            legacy_object_name,
        )
    thumbnail_path = _upload_thumbnail(
        minio_client, bucket_name, tenant_id, device_id, date_dir, filename,
        absolute_file_path)
    duration = int(ffprobe_video_duration_seconds(absolute_file_path))
    _upsert_playback(
        tenant_id, device, device_id, file_path_url, object_name,
        record_time, file_size, duration, thumbnail_path)
    _upsert_record_metadata(record_space, device_id, object_name, bucket_name, filename,
                            file_size, content_type, file_path_url, thumbnail_path, duration, record_time)
    _patch_alert_record(
        tenant_id, device_id, record_time, duration, file_path_url)
    _cleanup_local_file(absolute_file_path, device_id)
    logger.info('DVR 上传完成 device_id=%s object=%s size=%s', device_id, object_name, file_size)
    return True


def _upload_thumbnail(minio_client, bucket_name, tenant_id, device_id, date_dir,
                      filename, absolute_file_path) -> Optional[str]:
    try:
        frame = extract_thumbnail_from_video(absolute_file_path, output_path=None, frame_position=0.1)
        if frame is None:
            return None
        thumbnail_filename = os.path.splitext(filename)[0] + '.jpg'
        thumbnail_object_name = _build_dvr_object_name(
            tenant_id, device_id, date_dir, thumbnail_filename)
        success, encoded_image = cv2.imencode('.jpg', frame, [cv2.IMWRITE_JPEG_QUALITY, 85])
        if not success:
            return None
        with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as tmp_file:
            tmp_thumbnail_path = tmp_file.name
            tmp_file.write(encoded_image.tobytes())
        try:
            minio_client.fput_object(
                bucket_name, thumbnail_object_name, tmp_thumbnail_path, content_type='image/jpeg',
            )
            # No protected thumbnail route exists yet, so do not expose a URL.
            return None
        finally:
            try:
                os.remove(tmp_thumbnail_path)
            except OSError:
                pass
    except Exception as e:
        logger.error('封面上传失败 device_id=%s error=%s', device_id, e, exc_info=True)
    return None


def _upsert_playback(tenant_id, device, device_id, file_path_url, object_name,
                     record_time, file_size, duration, thumbnail_path):
    try:
        shanghai_tz = timezone(timedelta(hours=8))
        record_time = ensure_shanghai_aware(record_time)
        tenant_id = int(tenant_id)
        existing = Playback.query.filter_by(
            tenant_id=tenant_id,
            file_path=file_path_url,
            device_id=device_id,
        ).first()
        if not existing:
            existing = Playback.query.filter_by(
                tenant_id=tenant_id,
                file_path=object_name,
                device_id=device_id,
            ).first()
        if existing:
            existing.file_path = file_path_url
            existing.thumbnail_path = thumbnail_path
            existing.file_size = file_size
            existing.event_time = record_time
            if duration > 0:
                existing.duration = duration
            existing.updated_at = datetime.now(shanghai_tz)
        else:
            current_time = datetime.now(shanghai_tz)
            playback = Playback(
                tenant_id=tenant_id,
                file_path=file_path_url,
                event_time=record_time,
                device_id=device_id,
                device_name=device.name if device else '',
                duration=duration if duration > 0 else 1,
                thumbnail_path=thumbnail_path,
                file_size=file_size,
                created_at=current_time,
                updated_at=current_time,
            )
            db.session.add(playback)
        db.session.commit()
    except Exception as e:
        logger.error('Playback 写入失败 device_id=%s error=%s', device_id, e, exc_info=True)
        db.session.rollback()


def _upsert_record_metadata(record_space, device_id, object_name, bucket_name, filename,
                            file_size, content_type, file_path_url, thumbnail_path, duration, record_time):
    try:
        from app.services.space_file_metadata_service import upsert_record_file
        event_time_naive = record_time.replace(tzinfo=None) if hasattr(record_time, 'tzinfo') and record_time.tzinfo else record_time
        upsert_record_file(
            space_id=record_space.id,
            device_id=device_id,
            object_name=object_name,
            bucket_name=bucket_name,
            filename=filename,
            file_size=file_size,
            content_type=content_type,
            url=file_path_url,
            thumbnail_url=thumbnail_path,
            duration=duration if duration > 0 else 1,
            event_time=event_time_naive,
            source='dvr',
        )
    except Exception as e:
        logger.error('录像元数据写入失败 device_id=%s error=%s', device_id, e, exc_info=True)
        db.session.rollback()


def _patch_alert_record(tenant_id, device_id, record_time, duration,
                        file_path_url):
    try:
        from app.services.alert_service import patch_alerts_record

        record_time = ensure_shanghai_aware(record_time)
        event_time_str = record_time.strftime('%Y-%m-%d %H:%M:%S')
        patch_alerts_record({
            'tenant_id': int(tenant_id),
            'event_time': event_time_str,
            'duration': duration if duration > 0 else 1,
            'device_id': device_id,
            'file_path': file_path_url,
        })
    except Exception as e:
        logger.error('关联告警 record_path 失败 device_id=%s error=%s', device_id, e, exc_info=True)


def _cleanup_local_file(absolute_file_path, device_id):
    try:
        from app.services.playback_disk_guard_service import (
            cleanup_device_recordings,
            remove_local_after_minio_upload,
        )
        remove_local_after_minio_upload(absolute_file_path)
        cleanup_device_recordings(device_id)
    except Exception as e:
        logger.error('本地回放清理失败 device_id=%s error=%s', device_id, e, exc_info=True)
