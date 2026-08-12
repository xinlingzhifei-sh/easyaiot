"""Evidence export helpers for record review workflows."""
import hashlib
import hmac
import json
import logging
import os
import re
import shutil
import subprocess
import tempfile
import threading
import time
import uuid
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from urllib.parse import parse_qs, quote, unquote, urlparse

from app.services.local_media_path_service import (
    LocalMediaPathError,
    resolve_allowed_local_media_file,
)
from app.services.media_resource_guard import (
    ffmpeg_output_thread_options,
    ffmpeg_resource_options,
    run_ffmpeg_guarded,
)
from app.utils.minio_bucket_policy import ensure_bucket_private


_EXPORT_JOBS = {}
_EXPORT_CONTENT = {}
_EXPORT_AUDIT = {}
_STORE_ENV = 'YFEIEYE_RECORD_EXPORT_STORE_DIR'
_SIGNING_SECRET_ENV = 'YFEIEYE_RECORD_EXPORT_HMAC_SECRET'
_SIGNING_KEY_ID_ENV = 'YFEIEYE_RECORD_EXPORT_KEY_ID'
_SIGNING_KEYS_ENV = 'YFEIEYE_RECORD_EXPORT_HMAC_KEYS'
_SIGNING_ACTIVE_KEY_ID_ENV = 'YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID'
_ALLOW_SHA256_FALLBACK_ENV = 'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK'
_STORE_TYPE_ENV = 'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE'
_STORE_URI_ENV = 'YFEIEYE_RECORD_EXPORT_STORAGE_URI'
_S3_ENDPOINT_ENV = 'YFEIEYE_RECORD_EXPORT_S3_ENDPOINT'
_S3_ACCESS_KEY_ENV = 'YFEIEYE_RECORD_EXPORT_S3_ACCESS_KEY'
_S3_SECRET_KEY_ENV = 'YFEIEYE_RECORD_EXPORT_S3_SECRET_KEY'
_S3_SECURE_ENV = 'YFEIEYE_RECORD_EXPORT_S3_SECURE'
_S3_BUCKET_ENV = 'YFEIEYE_RECORD_EXPORT_S3_BUCKET'
_S3_PREFIX_ENV = 'YFEIEYE_RECORD_EXPORT_S3_PREFIX'
_RETENTION_DAYS_ENV = 'YFEIEYE_RECORD_EXPORT_RETENTION_DAYS'
_DEFAULT_RETENTION_DAYS = '7'
_RETRY_BACKOFF_SECONDS_ENV = 'YFEIEYE_RECORD_EXPORT_RETRY_BACKOFF_SECONDS'
_CLAIM_STALE_SECONDS_ENV = 'YFEIEYE_RECORD_EXPORT_CLAIM_STALE_SECONDS'
_CLAIM_HEARTBEAT_SECONDS_ENV = 'YFEIEYE_RECORD_EXPORT_CLAIM_HEARTBEAT_SECONDS'
_DEFAULT_RETRY_BACKOFF_SECONDS = 5
_DEFAULT_CLAIM_STALE_SECONDS = 300
_DEFAULT_STORE_ROOT = '/data/yfeieye-record-exports'
_MAX_SEGMENTS_ENV = 'YFEIEYE_RECORD_EXPORT_MAX_SEGMENTS'
_MAX_TOTAL_DURATION_SECONDS_ENV = 'YFEIEYE_RECORD_EXPORT_MAX_TOTAL_DURATION_SECONDS'
_MAX_INPUT_BYTES_ENV = 'YFEIEYE_RECORD_EXPORT_MAX_INPUT_BYTES'
_MAX_OUTPUT_BYTES_ENV = 'YFEIEYE_RECORD_EXPORT_MAX_OUTPUT_BYTES'
_STORE_MAX_BYTES_ENV = 'YFEIEYE_RECORD_EXPORT_STORE_MAX_BYTES'
_TEMP_DIR_ENV = 'YFEIEYE_RECORD_EXPORT_TEMP_DIR'
_TEMP_MAX_BYTES_ENV = 'YFEIEYE_RECORD_EXPORT_TEMP_MAX_BYTES'
_ORPHAN_TTL_SECONDS_ENV = 'YFEIEYE_RECORD_EXPORT_ORPHAN_TTL_SECONDS'
_MIN_FREE_BYTES_ENV = 'YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES'
_DEFAULT_MAX_SEGMENTS = 16
_DEFAULT_MAX_TOTAL_DURATION_SECONDS = 3600
_DEFAULT_MAX_INPUT_BYTES = 2 * 1024 * 1024 * 1024
_DEFAULT_MAX_OUTPUT_BYTES = 2 * 1024 * 1024 * 1024
_DEFAULT_STORE_MAX_BYTES = 20 * 1024 * 1024 * 1024
_DEFAULT_TEMP_MAX_BYTES = 4 * 1024 * 1024 * 1024
_DEFAULT_ORPHAN_TTL_SECONDS = 60 * 60
_DEFAULT_MIN_FREE_BYTES = 512 * 1024 * 1024
_MIN_HMAC_SECRET_BYTES = 32
_ALLOWED_EXPORT_FORMATS = {'mp4', 'mkv', 'mov', 'avi', 'webm', 'ts'}
_WORKER_POLL_SECONDS_ENV = 'YFEIEYE_RECORD_EXPORT_WORKER_POLL_SECONDS'
_WORKER_MAX_ATTEMPTS_ENV = 'YFEIEYE_RECORD_EXPORT_WORKER_MAX_ATTEMPTS'
_WORKER_BATCH_SIZE_ENV = 'YFEIEYE_RECORD_EXPORT_WORKER_BATCH_SIZE'
_WORKER_WAKE = threading.Event()
_WORKER_START_LOCK = threading.Lock()
_WORKER_THREAD = None
_STORAGE_ADAPTER_FACTORY = None
_STORAGE_ADAPTER_CACHE = {}
_FFMPEG_MODE_ENV = 'YFEIEYE_RECORD_EXPORT_FFMPEG_MODE'
_AUDIT_LOCK_TIMEOUT_SECONDS = 10
_AUDIT_LOCK_STALE_SECONDS = 60
_LOGGER = logging.getLogger(__name__)
_ACTIVE_TEMP_PATHS = set()
_ACTIVE_TEMP_PATHS_LOCK = threading.Lock()


class RecordExportExpiredError(ValueError):
    """Raised when an export exists but its download lifetime has elapsed."""


class RecordExportIntegrityError(RuntimeError):
    """Raised when persisted export bytes no longer match their manifest."""


class RecordExportClaimLostError(RuntimeError):
    """Raised when a reclaimed worker fences an older worker from publishing."""


class RecordExportAccessDecisionConflictError(RuntimeError):
    """Raised when an access decision id is reused with different semantics."""


class _MinioObjectStorageAdapter:
    """Small S3-compatible adapter backed by the already bundled MinIO SDK."""

    def __init__(self, client, bucket: str, prefix: str = '', scheme: str = 's3'):
        self.client = client
        self.bucket = bucket
        self.prefix = _text(prefix).strip('/').replace('\\', '/')
        self.scheme = scheme
        if not self.client.bucket_exists(self.bucket):
            self.client.make_bucket(self.bucket)
        ensure_bucket_private(self.client, self.bucket)

    def _key(self, object_key: str) -> str:
        object_key = _text(object_key).lstrip('/').replace('\\', '/')
        return f'{self.prefix}/{object_key}' if self.prefix else object_key

    def put_file(self, object_key: str, path: str, content_type=None):
        self.client.fput_object(
            self.bucket,
            self._key(object_key),
            path,
            content_type=content_type or 'application/octet-stream',
            metadata={'sha256': _sha256_file(path).split(':', 1)[1]},
        )

    def stat(self, object_key: str):
        stat = self.client.stat_object(self.bucket, self._key(object_key))
        return {'size': int(getattr(stat, 'size', 0) or 0)}

    def open(self, object_key: str):
        return self.client.get_object(self.bucket, self._key(object_key))

    def delete(self, object_key: str):
        self.client.remove_object(self.bucket, self._key(object_key))

    def list(self, prefix: str):
        storage_prefix = self._key(prefix)
        for item in self.client.list_objects(
                self.bucket, prefix=storage_prefix, recursive=True):
            name = _text(getattr(item, 'object_name', None))
            if self.prefix and name.startswith(self.prefix + '/'):
                name = name[len(self.prefix) + 1:]
            if name:
                yield name

    def uri(self, object_key: str) -> str:
        return f'{self.scheme}://{self.bucket}/{self._key(object_key)}'


def configure_record_export_storage_adapter(factory=None):
    """Inject an object-storage adapter factory; primarily used by isolated tests."""
    global _STORAGE_ADAPTER_FACTORY
    _STORAGE_ADAPTER_FACTORY = factory
    _STORAGE_ADAPTER_CACHE.clear()


def create_record_export(payload: dict, record_resolver=None, async_worker=False, worker_runner=None) -> dict:
    """Create a persisted export task; only a verified worker may mark it ready."""
    payload = payload or {}
    async_worker = async_worker or _as_bool(payload.get('async_worker') or payload.get('asyncWorker'))
    result = _build_record_export(payload, record_resolver)
    export_id = result['export_id']
    claim_token = _acquire_export_claim(export_id)
    if not claim_token:
        existing = _reload_export_job(export_id)
        if existing:
            return _public_job(existing)
        raise RuntimeError('record export creation is already in progress')
    try:
        existing = _reload_export_job(export_id)
        if existing and existing.get('status') != 'expired':
            job = existing
        else:
            if existing:
                _EXPORT_CONTENT.pop(export_id, None)
                _delete_content(export_id)
                _delete_source_artifacts(export_id)
                _delete_transient_artifacts(export_id)
            job = dict(result)
            job.update({
                'status': 'pending',
                'status_url': f'/video/record/export/{export_id}',
                'download_url': None,
                'file_hash': None,
                'message': 'record evidence export queued',
                'retry_count': 0,
                'last_error': None,
                'created_at': datetime.now(timezone.utc).isoformat(),
                '_worker_runner': worker_runner,
            })
            _EXPORT_JOBS[export_id] = job
            _persist_job(job)
            _append_export_audit(export_id, 'created', None, None, {'status': 'pending'})
            _persist_manifest(export_id)
    finally:
        _release_export_claim(export_id, claim_token)
    wake_record_export_worker()
    if async_worker or job.get('status') in ('ready', 'failed', 'expired'):
        return _public_job(job)
    return poll_record_export(export_id)


def validate_record_export_request(payload: dict, camera_id=None, record_resolver=None) -> dict:
    """Fail closed unless every requested source belongs to the authorized camera."""
    payload = payload or {}
    camera_id = _text(camera_id or payload.get('camera_id') or payload.get('cameraId'))
    device_id = _text(payload.get('device_id') or payload.get('deviceId'))
    if camera_id and device_id and camera_id != device_id:
        raise ValueError('camera_id and device_id must identify the same camera')
    scoped_camera_id = camera_id or device_id
    if not scoped_camera_id:
        raise ValueError('camera_id or device_id must not be blank')
    tenant_id = _tenant_id_from_payload(payload)

    if any(key in payload for key in ('original_record_uri', 'originalRecordUri')):
        raise ValueError('original_record_uri is server-managed and must not be supplied')
    _validate_segment_identity(payload)

    raw_segments = payload.get('record_segments') or payload.get('recordSegments') or []
    if raw_segments and not isinstance(raw_segments, (list, tuple)):
        raise ValueError('record_segments must be a list')
    for segment in raw_segments:
        if not isinstance(segment, dict):
            raise ValueError('record_segments must contain objects')
        if any(key in segment for key in ('original_record_uri', 'originalRecordUri')):
            raise ValueError('original_record_uri is server-managed and must not be supplied')
        _validate_segment_identity(segment)

    requested_sources = _requested_record_sources(payload)
    max_segments = _positive_int(os.environ.get(_MAX_SEGMENTS_ENV)) or _DEFAULT_MAX_SEGMENTS
    if len(requested_sources) > max_segments:
        raise ValueError(f'record segment count exceeds limit {max_segments}')
    requested_uris = list(dict.fromkeys(
        source['record_uri'] for source in requested_sources if source.get('record_uri')
    ))
    if not requested_uris:
        return payload

    total_duration = _requested_total_duration(payload, requested_sources)
    max_duration = (
        _positive_int(os.environ.get(_MAX_TOTAL_DURATION_SECONDS_ENV))
        or _DEFAULT_MAX_TOTAL_DURATION_SECONDS
    )
    if total_duration and total_duration > max_duration:
        raise ValueError(f'record export total duration exceeds limit {max_duration} seconds')
    local_size = sum(
        os.path.getsize(path)
        for path in (_local_file_path(uri) for uri in requested_uris)
        if path and os.path.isfile(path)
    )
    if local_size > _max_input_bytes():
        raise ValueError('record export input size exceeds configured limit')

    resolver = record_resolver or resolve_record_uri_from_window
    resolved = resolver({
        **payload,
        'camera_id': scoped_camera_id,
        'device_id': scoped_camera_id,
    }) or {}
    resolved_uri = _text(
        resolved.get('record_uri')
        or resolved.get('recordUri')
        or resolved.get('download_url')
        or resolved.get('downloadUrl')
        or resolved.get('url')
    )
    resolved_uris = set(_requested_record_uris(resolved))
    if resolved_uri:
        resolved_uris.add(resolved_uri)
    resolved_camera = _text(
        resolved.get('camera_id') or resolved.get('cameraId')
        or resolved.get('device_id') or resolved.get('deviceId')
    )
    resolved_tenant = _tenant_id_from_payload(resolved)
    if resolved_uris and resolved_camera and resolved_camera != scoped_camera_id:
        raise ValueError('resolved record sources belong to a different camera')
    if (resolved_uris and tenant_id is not None
            and resolved_tenant is not None and resolved_tenant != tenant_id):
        raise ValueError('resolved record sources belong to a different tenant')
    for record_uri in requested_uris:
        if (record_uri in resolved_uris
                and resolved_camera == scoped_camera_id
                and (tenant_id is None or resolved_tenant in (None, tenant_id))):
            continue
        if _record_uri_owned_by_camera(record_uri, scoped_camera_id, payload):
            continue
        raise ValueError('record_uri is not bound to authorized camera metadata')
    return payload


def _requested_record_sources(payload: dict) -> list:
    raw_segments = payload.get('record_segments') or payload.get('recordSegments')
    if isinstance(raw_segments, (list, tuple)) and raw_segments:
        return [{
            'record_uri': _text(
                segment.get('record_uri') or segment.get('recordUri') or segment.get('uri')),
            'segment_start_time': _text(
                segment.get('segment_start_time') or segment.get('segmentStartTime')),
            'segment_end_time': _text(
                segment.get('segment_end_time') or segment.get('segmentEndTime')),
            'clip_start_time': _text(
                segment.get('clip_start_time') or segment.get('clipStartTime')),
            'clip_end_time': _text(
                segment.get('clip_end_time') or segment.get('clipEndTime')),
        } for segment in raw_segments if isinstance(segment, dict)]
    raw_uris = payload.get('record_uris') or payload.get('recordUris')
    if isinstance(raw_uris, (list, tuple)) and raw_uris:
        return [{'record_uri': _text(uri)} for uri in raw_uris if _text(uri)]
    primary = _text(payload.get('record_uri') or payload.get('recordUri'))
    return [{'record_uri': primary}] if primary else []


def _validate_segment_identity(segment: dict):
    uri = _text(segment.get('record_uri') or segment.get('recordUri') or segment.get('uri'))
    parsed_space, parsed_object = _canonical_record_identity(uri)
    explicit_space = segment.get('space_id') or segment.get('spaceId')
    explicit_object = _text(segment.get('object_name') or segment.get('objectName'))
    if explicit_space is not None or explicit_object:
        try:
            explicit_space = int(explicit_space) if explicit_space is not None else None
        except (TypeError, ValueError) as exc:
            raise ValueError('record segment identity is invalid') from exc
        if not parsed_space or not parsed_object:
            raise ValueError('record segment identity must come from a canonical record URI')
        if (explicit_space is not None and explicit_space != parsed_space) \
                or (explicit_object and explicit_object != parsed_object):
            raise ValueError('record segment identity does not match record URI')


def _canonical_record_identity(uri: str):
    path = unquote(urlparse(_text(uri)).path)
    matched = re.search(r'/video/record/space/(\d+)/video/(.+)$', path)
    if not matched:
        return None, ''
    return int(matched.group(1)), matched.group(2)


def _requested_total_duration(payload: dict, sources: list):
    durations = []
    for source in sources:
        start = _parse_time(source.get('clip_start_time') or source.get('segment_start_time'))
        end = _parse_time(source.get('clip_end_time') or source.get('segment_end_time'))
        if start and end and end > start:
            durations.append((end - start).total_seconds())
    if durations:
        return sum(durations)
    start = _parse_time(payload.get('start_time') or payload.get('startTime'))
    end = _parse_time(payload.get('end_time') or payload.get('endTime'))
    return (end - start).total_seconds() if start and end and end > start else None


def poll_record_export(export_id: str) -> dict:
    """Run or inspect an async record export job."""
    export_id = _text(export_id)
    job = _reload_export_job(export_id) or _get_export_job(export_id)
    if not job:
        raise ValueError(f'export job not found: {export_id}')
    if job.get('status') in ('failed', 'expired'):
        return _public_job(job)
    if job.get('status') == 'ready' and _ready_job_is_committed(job):
        return _public_job(job)

    next_attempt_at = _parse_time(job.get('next_attempt_at'))
    if next_attempt_at and next_attempt_at > _current_local_time():
        return _public_job(job)

    claim_token = _acquire_export_claim(export_id)
    if not claim_token:
        latest = _reload_export_job(export_id) or job
        return _public_job(latest)

    heartbeat = _ExportClaimHeartbeat(export_id, claim_token).start()
    try:
        job = _reload_export_job(export_id) or job
        if job.get('status') in ('failed', 'expired'):
            return _public_job(job)
        if job.get('status') == 'ready' and _ready_job_is_committed(job):
            return _public_job(job)
        job['status'] = 'running'
        job['started_at'] = datetime.now(timezone.utc).isoformat()
        job['retry_count'] = int(job.get('retry_count') or 0) + 1
        job['claim_epoch'] = int(job.get('claim_epoch') or 0) + 1
        job['last_error'] = None
        job['next_attempt_at'] = None
        _persist_job(job)
        _append_export_audit(export_id, 'running', None, None, {'attempt': job['retry_count']})
        runner = job.get('_worker_runner') or _default_export_worker
        worker_job = _public_job(job)
        worker_job['_claim_token'] = claim_token
        result = runner(worker_job) or {}
        heartbeat.assert_owned()
        content_source, provenance = _validated_worker_result(job, result)
        computed_file_hash = content_source['hash']
        supplied_file_hash = _text(result.get('file_hash') or result.get('fileHash'))
        if supplied_file_hash and supplied_file_hash != computed_file_hash:
            raise RuntimeError('export worker file hash does not match generated media')
        _persist_content_source(export_id, content_source, claim_token=claim_token)
        heartbeat.assert_owned()
        persisted_path = _content_path(export_id)
        if not os.path.isfile(persisted_path) or _sha256_file(persisted_path) != computed_file_hash:
            raise RuntimeError('export media persistence verification failed')
        job['record_segments'] = provenance
        job['ffmpeg_command_hash'] = _text(
            result.get('ffmpeg_command_hash') or result.get('ffmpegCommandHash'))
        media_probe = result.get('media_probe') or result.get('mediaProbe')
        if isinstance(media_probe, dict):
            job['media_probe'] = dict(media_probe)
        job['download_url'] = _text(result.get('download_url')) or f'/video/record/export/{export_id}/download'
        job['file_hash'] = computed_file_hash
        job['output_size_bytes'] = content_source['size']
        job['message'] = _text(result.get('message')) or 'record evidence export ready'
        job['finished_at'] = datetime.now(timezone.utc).isoformat()
        _validate_ready_export(job)
        if _uses_object_storage(job):
            job['status'] = 'verifying'
            job['storage_verification'] = {
                'status': 'verifying',
                'verified_at': None,
                'artifact_count': 0,
            }
            _persist_job(job)
            _persist_manifest(export_id, job)
            audit_lock_token = _acquire_audit_lock(export_id)
            try:
                verified = _sync_object_storage_artifacts(job, claim_token=claim_token)
            finally:
                _release_named_claim(_audit_lock_path(export_id), audit_lock_token)
            heartbeat.assert_owned()
            job['storage_verification'] = {
                'status': 'verified',
                'verified_at': datetime.now(timezone.utc).isoformat(),
                'artifact_count': len(verified),
                'artifacts': verified,
            }
        heartbeat.assert_owned()
        job['status'] = 'ready'
        _persist_job(job)
        manifest = _persist_manifest(export_id, job)
        _validate_ready_manifest(job, manifest)
        _append_export_audit(export_id, 'ready', None, None, {
            'attempt': job['retry_count'],
            'file_hash': job['file_hash'],
        }, claim_token=claim_token)
        heartbeat.assert_owned()
    except RecordExportClaimLostError:
        return _public_job(_reload_export_job(export_id) or job)
    except Exception as exc:
        try:
            heartbeat.assert_owned()
        except RecordExportClaimLostError:
            return _public_job(_reload_export_job(export_id) or job)
        job['status'] = 'failed'
        job['message'] = str(exc)
        job['last_error'] = str(exc)
        job['download_url'] = None
        job['file_hash'] = None
        job['ffmpeg_command_hash'] = None
        job['output_size_bytes'] = None
        job['next_attempt_at'] = _retry_at(job['retry_count']).isoformat()
        job['finished_at'] = datetime.now(timezone.utc).isoformat()
        _EXPORT_CONTENT.pop(export_id, None)
        if _uses_object_storage(job):
            try:
                _delete_object_storage_artifacts(job)
            except Exception:
                _LOGGER.exception('failed to remove incomplete record export objects')
        _delete_content(export_id)
        _delete_source_artifacts(export_id)
        _delete_transient_artifacts(export_id)
        _persist_job(job)
        _append_export_audit(export_id, 'failed', None, None, {
            'attempt': job['retry_count'],
            'last_error': job['last_error'],
            'next_attempt_at': job['next_attempt_at'],
        })
    finally:
        heartbeat.stop()
        _release_export_claim(export_id, claim_token)
    return _public_job(job)


def retry_record_export(export_id: str) -> dict:
    """Re-queue a failed async export job with the original worker."""
    export_id = _text(export_id)
    job = _reload_export_job(export_id) or _get_export_job(export_id)
    if not job:
        raise ValueError(f'export job not found: {export_id}')
    if job.get('status') != 'failed':
        return _public_job(job)
    claim_token = _acquire_export_claim(export_id)
    if not claim_token:
        return _public_job(_reload_export_job(export_id) or job)
    try:
        job = _reload_export_job(export_id) or job
        if job.get('status') != 'failed':
            return _public_job(job)
        job['status'] = 'pending'
        job['message'] = 'retry queued'
        job['last_error'] = None
        job['next_attempt_at'] = None
        job['download_url'] = None
        job['file_hash'] = None
        job['finished_at'] = None
        _EXPORT_CONTENT.pop(export_id, None)
        _delete_content(export_id)
        _delete_source_artifacts(export_id)
        _delete_transient_artifacts(export_id)
        _persist_job(job)
        _append_export_audit(export_id, 'retry_queued', None, None, {
            'retry_count': job.get('retry_count'),
        })
    finally:
        _release_export_claim(export_id, claim_token)
    wake_record_export_worker()
    return _public_job(job)


def cleanup_record_export_resources(now=None) -> dict:
    """Remove stale worker/download artifacts without touching retained evidence."""
    now_value = time.time() if now is None else float(now)
    ttl_seconds = (
        _positive_int(os.environ.get(_ORPHAN_TTL_SECONDS_ENV))
        or _DEFAULT_ORPHAN_TTL_SECONDS
    )
    removed_paths = 0
    failures = 0
    temp_root = _export_temp_root()
    os.makedirs(temp_root, mode=0o750, exist_ok=True)
    with _ACTIVE_TEMP_PATHS_LOCK:
        active_temp_paths = set(_ACTIVE_TEMP_PATHS)
    try:
        temp_entries = list(os.scandir(temp_root))
    except OSError:
        temp_entries = []
        failures += 1
    for entry in temp_entries:
        if os.path.abspath(entry.path) in active_temp_paths:
            continue
        if _temp_workdir_has_active_claim(entry.path):
            continue
        if not _path_is_older_than(entry.path, now_value - ttl_seconds):
            continue
        try:
            _remove_resource_path(entry.path)
            removed_paths += 1
        except OSError:
            failures += 1

    root = _store_root()
    if not os.path.isdir(root):
        return {
            'status': 'completed' if failures == 0 else 'partial',
            'removed_paths': removed_paths,
            'cleanup_failures': failures,
        }
    for entry in os.scandir(root):
        if not entry.is_dir(follow_symlinks=False):
            continue
        if os.path.abspath(entry.path) == os.path.abspath(temp_root):
            continue
        job = _read_json(os.path.join(entry.path, 'job.json'), None)
        status = _text(job.get('status')) if isinstance(job, dict) else ''
        claim_path = os.path.join(entry.path, 'worker.claim')
        if os.path.exists(claim_path) and not _claim_is_stale(claim_path):
            continue
        for child in list(os.scandir(entry.path)):
            name = child.name
            transient = (
                re.fullmatch(r'clip-\d{3}\.[A-Za-z0-9]{1,10}', name)
                or name.startswith('worker-output.')
                or (name.startswith('download-') and name.endswith('.tmp'))
                or name == 'concat.txt'
                or name == '.staging'
                or name.endswith('.tmp')
            )
            orphan_source = (
                re.fullmatch(r'source-\d{3}\.[A-Za-z0-9]{1,10}', name)
                and status in ('', 'failed', 'expired')
            )
            if not (transient or orphan_source):
                continue
            if not _path_is_older_than(child.path, now_value - ttl_seconds):
                continue
            try:
                _remove_resource_path(child.path)
                removed_paths += 1
            except OSError:
                failures += 1
        if isinstance(job, dict) and status in (
                '', 'pending', 'running', 'verifying', 'failed', 'expired'):
            try:
                removed_paths += _cleanup_object_storage_staging(job)
            except Exception:
                failures += 1
                _LOGGER.warning(
                    'failed to clean stale record export object staging for %s',
                    job.get('export_id'),
                    exc_info=True,
                )
    return {
        'status': 'completed' if failures == 0 else 'partial',
        'removed_paths': removed_paths,
        'cleanup_failures': failures,
    }


def _cleanup_object_storage_staging(job: dict) -> int:
    if not _uses_object_storage(job):
        return 0
    adapter = _object_storage_adapter(job)
    list_objects = getattr(adapter, 'list', None)
    if not callable(list_objects):
        return 0
    staging_prefix = _storage_object_key(job, '.staging/')
    object_keys = list(list_objects(staging_prefix))
    if any(not isinstance(object_key, str) or not object_key.startswith(staging_prefix)
           for object_key in object_keys):
        raise RuntimeError(
            'record export cleanup received an object key outside the authorized staging prefix')
    removed = 0
    for object_key in object_keys:
        adapter.delete(object_key)
        removed += 1
    return removed


def _temp_workdir_has_active_claim(path: str) -> bool:
    marker = _read_json(os.path.join(path, '.active.json'), None)
    export_id = _text(marker.get('export_id')) if isinstance(marker, dict) else ''
    if not export_id:
        return False
    claim_path = _claim_path(export_id)
    return os.path.exists(claim_path) and not _claim_is_stale(claim_path)


def _path_is_older_than(path: str, cutoff: float) -> bool:
    try:
        if os.path.isdir(path):
            timestamps = [os.path.getmtime(path)]
            for root, directories, files in os.walk(path):
                for name in directories + files:
                    try:
                        timestamps.append(os.path.getmtime(os.path.join(root, name)))
                    except OSError:
                        return False
            return max(timestamps) < cutoff
        return os.path.getmtime(path) < cutoff
    except OSError:
        return False


def _remove_resource_path(path: str):
    if os.path.isdir(path) and not os.path.islink(path):
        shutil.rmtree(path)
    else:
        os.remove(path)


def _ensure_export_store_quota(additional_bytes=0):
    limit = (
        _positive_int(os.environ.get(_STORE_MAX_BYTES_ENV))
        or _DEFAULT_STORE_MAX_BYTES
    )
    usage = _tree_size_excluding(_store_root(), {_export_temp_root()})
    if usage + max(0, int(additional_bytes or 0)) > limit:
        raise RuntimeError('record export store quota exceeded')
    _ensure_media_disk_free(_store_root(), additional_bytes)
    return {'usage_bytes': usage, 'max_bytes': limit}


def _ensure_export_temp_quota(additional_bytes=0):
    limit = _export_temp_max_bytes()
    usage = _tree_size_excluding(_export_temp_root(), set())
    if usage + max(0, int(additional_bytes or 0)) > limit:
        raise RuntimeError('record export temporary storage quota exceeded')
    _ensure_media_disk_free(_export_temp_root(), additional_bytes)
    return {'usage_bytes': usage, 'max_bytes': limit}


def _remaining_export_temp_bytes() -> int:
    quota = _ensure_export_temp_quota(0)
    disk = _disk_usage_for_path(_export_temp_root())
    reserve = (
        _positive_int(os.environ.get(_MIN_FREE_BYTES_ENV))
        or _DEFAULT_MIN_FREE_BYTES
    )
    return max(0, min(
        quota['max_bytes'] - quota['usage_bytes'],
        int(disk.free) - reserve,
    ))


def _ensure_media_disk_free(path: str, additional_bytes=0):
    reserve = (
        _positive_int(os.environ.get(_MIN_FREE_BYTES_ENV))
        or _DEFAULT_MIN_FREE_BYTES
    )
    disk = _disk_usage_for_path(path)
    required = reserve + max(0, int(additional_bytes or 0))
    if int(disk.free) < required:
        raise RuntimeError('media storage free space reserve would be exhausted')
    return disk


def _disk_usage_for_path(path: str):
    candidate = os.path.abspath(path)
    while not os.path.exists(candidate):
        parent = os.path.dirname(candidate)
        if parent == candidate:
            break
        candidate = parent
    return shutil.disk_usage(candidate)


def _tree_size_excluding(path: str, excluded_paths: set) -> int:
    path = os.path.abspath(path)
    excluded = {os.path.abspath(item) for item in excluded_paths}
    if not os.path.exists(path) or path in excluded:
        return 0
    total = 0
    for root, directories, files in os.walk(path):
        directories[:] = [
            name for name in directories
            if os.path.abspath(os.path.join(root, name)) not in excluded
        ]
        for name in files:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                continue
    return total


def _path_is_within(path: str, root: str) -> bool:
    try:
        return os.path.commonpath((os.path.abspath(path), os.path.abspath(root))) \
            == os.path.abspath(root)
    except (OSError, ValueError):
        return False


def cleanup_expired_record_exports(now=None) -> list:
    """Expire persisted jobs and remove media/source artifacts after retention."""
    cleanup_record_export_resources()
    cutoff = _normalize_datetime(now) if isinstance(now, datetime) else _current_local_time()
    root = _store_root()
    if not os.path.isdir(root):
        return []
    expired_ids = []
    for name in os.listdir(root):
        job_path = os.path.join(root, name, 'job.json')
        job = _read_json(job_path, None)
        if not isinstance(job, dict):
            continue
        export_id = _text(job.get('export_id'))
        if not export_id:
            continue
        claim_token = _acquire_export_claim(export_id)
        if not claim_token:
            continue
        heartbeat = _ExportClaimHeartbeat(export_id, claim_token).start()
        try:
            job = _read_json(job_path, None)
            if not isinstance(job, dict) or job.get('status') == 'expired':
                continue
            expires_at = _parse_time(_manifest_expires_at(job))
            if not expires_at or expires_at > cutoff:
                continue
            _EXPORT_JOBS[export_id] = job
            _EXPORT_CONTENT.pop(export_id, None)
            if _uses_object_storage(job):
                try:
                    heartbeat.assert_owned()
                    _delete_object_storage_artifacts(job)
                except Exception as exc:
                    job['cleanup_last_error'] = str(exc)
                    job['cleanup_retry_at'] = _retry_at(1).isoformat()
                    _persist_job(job)
                    _append_export_audit(export_id, 'cleanup_failed', None, None, {
                        'last_error': str(exc),
                    })
                    continue
            heartbeat.assert_owned()
            _delete_content(export_id)
            _delete_source_artifacts(export_id)
            _delete_transient_artifacts(export_id)
            job['status'] = 'expired'
            job['download_url'] = None
            job['file_hash'] = None
            job['output_size_bytes'] = None
            job['finished_at'] = cutoff.isoformat()
            job['cleanup_last_error'] = None
            job['cleanup_retry_at'] = None
            _persist_job(job)
            _persist_manifest(export_id)
            expired_ids.append(export_id)
        finally:
            heartbeat.stop()
            _release_export_claim(export_id, claim_token)
    return expired_ids


def get_record_export_status(export_id: str) -> dict:
    """Read persisted status without executing ffmpeg in the HTTP request thread."""
    export_id = _text(export_id)
    job = _reload_export_job(export_id) or _get_export_job(export_id)
    if not job:
        raise ValueError(f'export job not found: {export_id}')
    if job.get('status') == 'ready' and not _ready_job_is_committed(job):
        job = dict(job)
        job['status'] = 'verifying'
        job['message'] = 'record export commit recovery pending'
        job['download_url'] = None
    return _public_job(job)


def process_record_export_queue(limit=None) -> list:
    """Claim and process a bounded batch of persisted export jobs."""
    normalized_limit = _positive_int(limit)
    if normalized_limit is None:
        normalized_limit = _positive_int(os.environ.get(_WORKER_BATCH_SIZE_ENV)) or 10
    max_attempts = _positive_int(os.environ.get(_WORKER_MAX_ATTEMPTS_ENV)) or 3
    now = _current_local_time()
    candidates = []
    root = _store_root()
    if not os.path.isdir(root):
        return []
    for name in os.listdir(root):
        job = _read_json(os.path.join(root, name, 'job.json'), None)
        if not isinstance(job, dict):
            continue
        status = _text(job.get('status'))
        retry_count = int(job.get('retry_count') or 0)
        next_attempt_at = _parse_time(job.get('next_attempt_at'))
        eligible = status in ('pending', 'running', 'verifying')
        if status == 'ready' and _uses_object_storage(job):
            eligible = not _ready_job_is_committed(job)
        if status == 'failed' and retry_count < max_attempts:
            eligible = next_attempt_at is None or next_attempt_at <= now
        if eligible:
            candidates.append(job)
    candidates.sort(key=lambda job: (_text(job.get('created_at')), _text(job.get('export_id'))))
    processed = []
    for candidate in candidates[:normalized_limit]:
        export_id = _text(candidate.get('export_id'))
        if candidate.get('status') == 'failed':
            retry_record_export(export_id)
        processed.append(poll_record_export(export_id))
    return processed


def start_record_export_worker(app=None):
    """Start one daemon consumer per process; file claims serialize processes."""
    global _WORKER_THREAD
    with _WORKER_START_LOCK:
        if _WORKER_THREAD is not None and _WORKER_THREAD.is_alive():
            _WORKER_WAKE.set()
            return _WORKER_THREAD
        _WORKER_THREAD = threading.Thread(
            target=_record_export_worker_loop,
            args=(app,),
            name='record-export-worker',
            daemon=True,
        )
        _WORKER_THREAD.start()
    _WORKER_WAKE.set()
    return _WORKER_THREAD


def wake_record_export_worker():
    _WORKER_WAKE.set()


def _record_export_worker_loop(app=None):
    while True:
        try:
            if app is None:
                cleanup_expired_record_exports()
                process_record_export_queue()
            else:
                with app.app_context():
                    cleanup_expired_record_exports()
                    process_record_export_queue()
        except Exception:
            _LOGGER.exception('record export worker cycle failed')
        configured = os.environ.get(_WORKER_POLL_SECONDS_ENV)
        try:
            poll_seconds = max(0.1, float(configured)) if configured is not None else 2.0
        except (TypeError, ValueError):
            poll_seconds = 2.0
        _WORKER_WAKE.wait(timeout=poll_seconds)
        _WORKER_WAKE.clear()


def get_record_export_audit(export_id: str) -> list:
    """Return the local audit trail for an export job."""
    export_id = _text(export_id)
    if not _get_export_job(export_id):
        raise ValueError(f'export job not found: {export_id}')
    lock_token = _acquire_audit_lock(export_id)
    try:
        job = _get_export_job(export_id)
        audit = list(_get_export_audit(export_id))
        if _uses_object_storage(job) and job.get('status') == 'ready':
            _verify_object_file_copy(job, 'audit.json', _audit_path(export_id))
            manifest = _read_json_strict(
                _manifest_path(export_id), 'record export manifest')
            _verify_object_commit_marker(job, manifest)
        return audit
    finally:
        _release_named_claim(_audit_lock_path(export_id), lock_token)


def get_record_export_manifest(export_id: str) -> dict:
    """Return the persistent evidence manifest for an export job."""
    export_id = _text(export_id)
    if not _get_export_job(export_id):
        raise ValueError(f'export job not found: {export_id}')
    lock_token = _acquire_audit_lock(export_id)
    try:
        job = _get_export_job(export_id)
        manifest_path = _manifest_path(export_id)
        manifest = _read_json_strict(manifest_path, 'record export manifest') \
            if os.path.exists(manifest_path) else None
        if isinstance(manifest, dict):
            _validate_manifest_integrity(manifest)
            object_storage_ready = (
                _uses_object_storage(job) and job.get('status') == 'ready')
            if object_storage_ready:
                _verify_object_file_copy(job, 'manifest.json', manifest_path)
                _verify_object_commit_marker(job, manifest)
            if _manifest_references_mutable_audit_file(manifest):
                legacy_manifest = manifest
                try:
                    manifest = _persist_manifest(export_id)
                    _validate_manifest_integrity(manifest)
                    if object_storage_ready:
                        _sync_object_storage_artifacts(job, metadata_only=True)
                        _publish_object_commit_marker(job)
                except Exception:
                    _write_json(manifest_path, legacy_manifest)
                    if object_storage_ready:
                        try:
                            _sync_object_storage_artifacts(job, metadata_only=True)
                            _publish_object_commit_marker(job)
                        except Exception:
                            _LOGGER.exception(
                                'failed to restore legacy record export manifest')
                    raise
            return manifest
        return _persist_manifest(export_id)
    finally:
        _release_named_claim(_audit_lock_path(export_id), lock_token)


def download_record_export(export_id: str, operator_user_id=None, reason=None) -> dict:
    """Return a local path or object stream without buffering the export in memory."""
    export_id = _text(export_id)
    job = _get_export_job(export_id)
    if not job:
        raise ValueError(f'export job not found: {export_id}')
    if _is_export_expired(job):
        raise RecordExportExpiredError(f'export expired: {export_id}')
    if job.get('status') != 'ready':
        raise ValueError(f'export is not ready: {export_id}')
    manifest = get_record_export_manifest(export_id)
    _validate_manifest_integrity(manifest)
    file_format = _text(job.get('format')) or 'mp4'
    expected_hash = _text(manifest.get('fileHash'))
    package_file = next((
        item for item in manifest.get('files') or []
        if isinstance(item, dict) and item.get('role') == 'export_package'
    ), {})
    expected_size = int(package_file.get('sizeBytes') or job.get('output_size_bytes') or 0)
    source = {}
    adapter = None
    object_key = None
    temporary_download_path = None
    if _uses_object_storage(job):
        adapter = _object_storage_adapter(job)
        object_key = _storage_object_key(job, 'content.bin')
        stat = adapter.stat(object_key) or {}
        actual_size = _storage_stat_size(stat)
        if actual_size != expected_size:
            raise RecordExportIntegrityError(
                f'export object size mismatch: {export_id}')
        os.makedirs(_export_temp_root(), mode=0o750, exist_ok=True)
        _ensure_export_temp_quota(expected_size)
        verified_path = os.path.join(
            _export_temp_root(), f'download-{export_id}-{uuid.uuid4().hex}.tmp')
        _copy_object_to_verified_path(
            adapter, object_key, verified_path, expected_size, expected_hash)
        temporary_download_path = verified_path
        source = {
            'path': verified_path,
            'temporary_path': True,
            'content_length': actual_size,
            'object_uri': adapter.uri(object_key),
        }
    else:
        path = _content_path(export_id)
        if not os.path.isfile(path):
            raise ValueError(f'export content not found: {export_id}')
        if os.path.getsize(path) != expected_size or _sha256_file(path) != expected_hash:
            raise RecordExportIntegrityError(
                f'export content verification failed: {export_id}')
        source = {
            'path': path,
            'content_length': expected_size,
        }
    try:
        _append_export_audit(export_id, 'downloaded', operator_user_id, reason, {
            'file_hash': job.get('file_hash'),
        })
        return {
            'export_id': export_id,
            'filename': f'{export_id}.{file_format}',
            'mimetype': 'video/mp4' if file_format == 'mp4' else 'application/octet-stream',
            'file_hash': job.get('file_hash'),
            **source,
        }
    except Exception:
        if temporary_download_path:
            try:
                os.remove(temporary_download_path)
            except FileNotFoundError:
                pass
        raise


def _copy_object_to_verified_path(adapter, object_key: str, destination: str,
                                  expected_size: int, expected_hash: str):
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    stream = adapter.open(object_key)
    digest = hashlib.sha256()
    size = 0
    try:
        with open(destination, 'xb') as output:
            while True:
                chunk = stream.read(1024 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > _max_output_bytes():
                    raise RecordExportIntegrityError(
                        'export object exceeds configured output size limit')
                output.write(chunk)
                digest.update(chunk)
            output.flush()
            os.fsync(output.fileno())
    except Exception:
        try:
            os.remove(destination)
        except FileNotFoundError:
            pass
        raise
    finally:
        _close_object_stream(stream)
    actual_hash = 'sha256:' + digest.hexdigest()
    if size != expected_size:
        os.remove(destination)
        raise RecordExportIntegrityError('export object readback size mismatch')
    if actual_hash != expected_hash:
        os.remove(destination)
        raise RecordExportIntegrityError('export object readback hash mismatch')


def _validate_manifest_integrity(manifest: dict):
    if not isinstance(manifest, dict):
        raise RuntimeError('record export manifest is missing')
    expected_hash = _expected_manifest_hash(manifest)
    if manifest.get('manifestHash') != expected_hash:
        raise RuntimeError('record export manifest hash mismatch')
    if manifest.get('status') == 'ready' and not _is_sha256_hash(manifest.get('fileHash')):
        raise RuntimeError('record export manifest file hash is invalid')
    audit = _ensure_audit_hash_chain(manifest.get('audit') or [])
    immutable = manifest.get('immutableAudit') or {}
    expected_head = audit[-1].get('entryHash') if audit else None
    if immutable.get('headHash') != expected_head:
        raise RuntimeError('record export audit hash chain head mismatch')
    signature = manifest.get('signature') or {}
    required_signature_fields = (
        'algorithm', 'algorithmVersion', 'signatureVersion', 'keyId',
        'signer', 'signedAt', 'value',
    )
    if any(not _text(signature.get(field)) for field in required_signature_fields):
        raise RuntimeError('record export manifest signature metadata is incomplete')
    algorithm = _text(signature.get('algorithm')).lower()
    key_id = _text(signature.get('keyId'))
    signing_configuration = validate_record_export_signing_configuration()
    if algorithm != _text(signing_configuration.get('algorithm')).lower():
        raise RuntimeError('record export manifest signature algorithm is not allowed')
    if algorithm == 'hmac-sha256':
        keyring, _active = _hmac_keyring_config()
        if keyring:
            secret = keyring.get(key_id)
        else:
            configured_id = _text(os.environ.get(_SIGNING_KEY_ID_ENV)) or 'local-hmac'
            secret = _text(os.environ.get(_SIGNING_SECRET_ENV)) if key_id == configured_id else ''
        if not secret:
            raise RuntimeError('record export manifest HMAC key is unavailable')
        expected_signature = _expected_manifest_signature(manifest, expected_hash, secret)
    elif algorithm == 'sha256':
        expected_signature = _expected_manifest_signature(manifest, expected_hash, '')
    else:
        raise RuntimeError('record export manifest signature algorithm is invalid')
    if signature.get('value') != expected_signature:
        raise RuntimeError('record export manifest signature mismatch')


def append_record_export_access_audit(export_id: str,
                                      decision: str,
                                      user_id=None,
                                      tenant_id=None,
                                      camera_id=None,
                                      action=None,
                                      reason=None,
                                      decision_id=None,
                                      auth_type=None,
                                      service_id=None):
    """Persist an allow/deny media decision in the export's hash-chained audit."""
    export_id = _text(export_id)
    if not _get_export_job(export_id):
        raise ValueError(f'export job not found: {export_id}')
    normalized_decision = _text(decision).lower()
    if normalized_decision not in ('allowed', 'denied'):
        raise ValueError('decision must be allowed or denied')
    decision_id = _text(decision_id) or None
    return _append_export_audit(
        export_id,
        f'access_{normalized_decision}',
        user_id,
        reason,
        {
            'tenant_id': _text(tenant_id) or None,
            'camera_id': _text(camera_id) or None,
            'media_action': _text(action) or None,
            'decision': normalized_decision,
            'decision_id': decision_id,
            'auth_type': _text(auth_type) or None,
            'service_id': _text(service_id) or None,
        },
        idempotency_key=decision_id,
    )


def _build_record_export(payload: dict, record_resolver=None) -> dict:
    payload = payload or {}
    camera_id = _text(payload.get('camera_id') or payload.get('cameraId'))
    device_id = _text(payload.get('device_id') or payload.get('deviceId'))
    validate_record_export_request(payload, camera_id or device_id, record_resolver)
    record_uri = _text(payload.get('record_uri') or payload.get('recordUri'))
    source = 'existing_record_uri'
    resolved = {}
    if not record_uri:
        resolver = record_resolver or resolve_record_uri_from_window
        resolved = resolver(payload) or {}
        record_uri = _text(
            resolved.get('record_uri')
            or resolved.get('recordUri')
            or resolved.get('download_url')
            or resolved.get('downloadUrl')
            or resolved.get('url')
        )
        source = _text(resolved.get('source')) or 'record_window'
    if not record_uri:
        raise ValueError('record_uri must not be blank or resolvable from device time window')

    review_case_id = _text(payload.get('review_case_id') or payload.get('reviewCaseId'))
    review_item_id = _text(payload.get('review_item_id') or payload.get('reviewItemId'))
    review_item_ids = _text_list(payload.get('review_item_ids') or payload.get('reviewItemIds'))
    if not review_item_ids and review_item_id:
        review_item_ids = [review_item_id]
    event_ids = _text_list(payload.get('event_ids') or payload.get('eventIds') or payload.get('bound_event_ids') or payload.get('boundEventIds'))
    snapshot_uris = _text_list(payload.get('snapshot_uris') or payload.get('snapshotUris') or payload.get('snapshots'))
    file_format = _text(payload.get('format')).lower() or 'mp4'
    if file_format not in _ALLOWED_EXPORT_FORMATS:
        raise ValueError(f'unsupported record export format: {file_format}')
    storage_type, storage_root = _configured_storage_policy()
    retention_days = _configured_retention_days()
    result = {
        'download_url': _download_url(record_uri),
        'status': 'ready',
        'message': 'using existing record evidence' if source == 'existing_record_uri' else 'resolved record evidence from time window',
        'record_uri': record_uri,
        'source': source,
        'review_case_id': review_case_id,
        'review_item_id': review_item_id,
        'review_item_ids': review_item_ids,
        'event_ids': event_ids,
        'snapshot_uris': snapshot_uris,
        'device_id': _text(payload.get('device_id') or payload.get('deviceId')),
        'camera_id': _text(payload.get('camera_id') or payload.get('cameraId')),
        'tenant_id': _text(payload.get('tenant_id') or payload.get('tenantId')),
        'source_alert_id': _text(payload.get('source_alert_id') or payload.get('sourceAlertId')),
        'start_time': _text(payload.get('start_time') or payload.get('startTime')),
        'end_time': _text(payload.get('end_time') or payload.get('endTime')),
        'format': file_format,
        'operator_user_id': _text(payload.get('operator_user_id') or payload.get('operatorUserId') or payload.get('generated_by') or payload.get('generatedBy')),
        'approved_by': _text(payload.get('approved_by') or payload.get('approvedBy') or payload.get('approver_user_id') or payload.get('approverUserId')),
        'approved_at': _text(payload.get('approved_at') or payload.get('approvedAt')),
        'approval_note': _text(payload.get('approval_note') or payload.get('approvalNote')),
        'expires_at': None,
        'retention_days': str(retention_days),
        'storage_type': storage_type,
        'storage_root': storage_root,
    }
    record_uris = payload.get('record_uris') or payload.get('recordUris')
    if isinstance(record_uris, (list, tuple)) and record_uris:
        result['record_uris'] = [_text(uri) for uri in record_uris if _text(uri)]
    record_segments = payload.get('record_segments') or payload.get('recordSegments')
    if isinstance(record_segments, (list, tuple)) and record_segments:
        result['record_segments'] = [dict(segment) for segment in record_segments if isinstance(segment, dict)]
    for key in ('space_id', 'object_name', 'segment_start_time', 'segment_end_time', 'duration'):
        value = resolved.get(key)
        if value is not None and value != '':
            result[key] = value
    result['export_id'] = _build_export_id(result)
    return result


def _configured_retention_days() -> int:
    raw = os.environ.get(_RETENTION_DAYS_ENV, _DEFAULT_RETENTION_DAYS)
    value = _positive_int(raw)
    if value is None or value > 3650:
        raise RuntimeError('record export retention policy must be between 1 and 3650 days')
    return value


def _configured_storage_policy() -> tuple:
    storage_type = _text(os.environ.get(_STORE_TYPE_ENV)).lower() or 'local_filesystem'
    if storage_type not in ('local_filesystem', 'minio', 's3'):
        raise RuntimeError(f'unsupported record export storage policy: {storage_type}')
    storage_root = _text(os.environ.get(_STORE_URI_ENV)) or _store_root()
    if storage_type in ('minio', 's3'):
        parsed = urlparse(storage_root)
        if parsed.scheme not in ('s3', 'minio') or not parsed.netloc:
            raise RuntimeError('object storage policy requires an s3://bucket/prefix URI')
    return storage_type, storage_root


def _requested_record_uris(payload: dict) -> list:
    values = []
    primary = _text(payload.get('record_uri') or payload.get('recordUri'))
    if primary:
        values.append(primary)
    raw_uris = payload.get('record_uris') or payload.get('recordUris')
    if isinstance(raw_uris, (list, tuple)):
        values.extend(_text(uri) for uri in raw_uris if _text(uri))
    raw_segments = payload.get('record_segments') or payload.get('recordSegments')
    if isinstance(raw_segments, (list, tuple)):
        for segment in raw_segments:
            if not isinstance(segment, dict):
                continue
            uri = _text(segment.get('record_uri') or segment.get('recordUri') or segment.get('uri'))
            if uri:
                values.append(uri)
    return list(dict.fromkeys(values))


def _record_uri_owned_by_camera(record_uri: str, camera_id: str, payload: dict, _depth=0) -> bool:
    """Resolve ownership from persisted alert/playback/record metadata only."""
    if _depth > 1:
        return False
    try:
        from models import RecordFile, RecordSpace
    except (ImportError, AttributeError):
        return False
    tenant_id = _tenant_id_from_payload(payload)
    record_filters = {
        'device_id': camera_id,
        'url': record_uri,
    }
    if tenant_id is not None:
        record_filters['tenant_id'] = tenant_id
    if _metadata_first(RecordFile, **record_filters) is not None:
        return True

    parsed_uri = urlparse(record_uri)
    path = unquote(parsed_uri.path)
    if path.endswith('/video/alert/record'):
        nested_path = (parse_qs(parsed_uri.query).get('path') or [''])[0]
        if nested_path and nested_path != record_uri:
            return _record_uri_owned_by_camera(
                nested_path,
                camera_id,
                payload,
                _depth=_depth + 1,
            )
    space_id, object_name = _canonical_record_identity(record_uri)
    if not space_id or not object_name:
        return False
    space = _metadata_get(RecordSpace, space_id)
    if not space or _text(getattr(space, 'device_id', None)) != camera_id:
        return False
    if tenant_id is not None and getattr(space, 'tenant_id', None) != tenant_id:
        return False
    file_filters = {
        'space_id': space_id,
        'device_id': camera_id,
        'object_name': object_name,
    }
    if tenant_id is not None:
        file_filters['tenant_id'] = tenant_id
    return _metadata_first(
        RecordFile,
        **file_filters,
    ) is not None


def _metadata_first(model, **filters):
    try:
        query = getattr(model, 'query', None)
        if query is None or not hasattr(query, 'filter_by'):
            return None
        return query.filter_by(**filters).first()
    except Exception:
        return None


def _metadata_get(model, identity):
    try:
        query = getattr(model, 'query', None)
        if query is None or not hasattr(query, 'get'):
            return None
        return query.get(identity)
    except Exception:
        return None


def _default_export_worker(job: dict) -> dict:
    return _run_ffmpeg_export(job)


def _run_ffmpeg_export(job: dict):
    ffmpeg = shutil.which('ffmpeg')
    if not ffmpeg:
        raise RuntimeError('ffmpeg executable is unavailable')
    ffprobe = shutil.which('ffprobe')
    if not ffprobe:
        raise RuntimeError('ffprobe executable is unavailable')

    file_format = _text(job.get('format')) or 'mp4'
    with _managed_export_workdir(job['export_id']) as workdir:
        sources = _materialize_record_sources(job, workdir)
        source_paths = [source['path'] for source in sources]
        if not source_paths:
            raise RuntimeError('record source materialization produced no media')
        source_durations = [
            _source_processing_duration(ffprobe, source, job)
            for source in sources
        ]
        expected_duration = _expected_export_duration(job, sources)
        if not expected_duration and all(source_durations):
            expected_duration = sum(source_durations)
        output_path = os.path.join(workdir, f'{job["export_id"]}.{file_format}')
        commands = []
        segment_command_hashes = []
        if len(source_paths) == 1:
            command = _single_clip_command(ffmpeg, source_paths[0], output_path, job, sources[0])
            _run_ffmpeg_command(
                command,
                output_path,
                expected_duration=(
                    source_durations[0] or expected_duration
                ),
            )
            commands.append(command)
            segment_command_hashes.append(_sha256_text(_canonical_json(command)))
        else:
            clipped_paths = []
            for index, source in enumerate(sources):
                clipped_path = os.path.join(workdir, f'clip-{index:03d}.{file_format}')
                clip_command = _single_clip_command(ffmpeg, source['path'], clipped_path, job, source)
                _run_ffmpeg_command(
                    clip_command,
                    clipped_path,
                    expected_duration=_clip_parameters(
                        source, job).get('durationSeconds') or source_durations[index],
                )
                commands.append(clip_command)
                segment_command_hashes.append(_sha256_text(_canonical_json(clip_command)))
                clipped_paths.append(clipped_path)
            concat_path = os.path.join(workdir, 'concat.txt')
            with open(concat_path, 'w', encoding='utf-8') as concat_file:
                for clipped_path in clipped_paths:
                    concat_file.write(f"file '{_ffmpeg_concat_path(clipped_path)}'\n")
            concat_command = [
                ffmpeg, *ffmpeg_resource_options(), '-y',
                '-f', 'concat', '-safe', '0', '-i', concat_path,
                '-c', 'copy',
            ]
            if file_format in ('mp4', 'mov'):
                concat_command.extend(['-movflags', '+faststart'])
            concat_command.extend(ffmpeg_output_thread_options())
            concat_command.append(output_path)
            _run_ffmpeg_command(
                concat_command,
                output_path,
                expected_duration=expected_duration,
            )
            commands.append(concat_command)
        media_probe = _validate_media_artifact(
            ffmpeg,
            ffprobe,
            output_path,
            expected_duration,
        )
        command_hash = _sha256_text(_canonical_json(commands))
        claim_token = _text(job.get('_claim_token')) or None
        persisted_sources = _persist_materialized_sources(
            job['export_id'], sources, claim_token=claim_token)
        if len(persisted_sources) != len(sources):
            raise RuntimeError('record source persistence failed')
        record_segments = _materialized_manifest_segments(
            job, persisted_sources, segment_command_hashes, command_hash)
        if not os.path.isfile(output_path) or os.path.getsize(output_path) <= 0:
            raise RuntimeError('ffmpeg generated an empty media artifact')
        persisted_output = os.path.join(
            _export_dir(job['export_id']), f'worker-output.{file_format}')
        os.makedirs(os.path.dirname(persisted_output), exist_ok=True)
        _copy_file_limited(
            output_path,
            persisted_output,
            _max_output_bytes(),
            export_id=job['export_id'],
            claim_token=claim_token,
        )
    return {
        'content_path': persisted_output,
        'download_url': f'/video/record/export/{job["export_id"]}/download',
        'message': 'ffmpeg clipped and stitched evidence',
        'record_segments': record_segments,
        'ffmpeg_command_hash': command_hash,
        'media_probe': media_probe,
    }


def _run_ffmpeg_command(command: list, output_path: str, expected_duration=None):
    completed = run_ffmpeg_guarded(
        command,
        output_path=output_path,
        expected_duration=expected_duration,
        max_output_bytes=_max_output_bytes(),
        quota_path=_export_temp_root(),
        max_total_bytes=_export_temp_max_bytes(),
    )
    if completed.returncode != 0:
        detail = completed.stderr.decode('utf-8', errors='replace')[-1000:]
        raise RuntimeError(f'ffmpeg export failed: {detail}'.strip())
    if not os.path.isfile(output_path) or os.path.getsize(output_path) <= 0:
        raise RuntimeError('ffmpeg did not produce a non-empty media artifact')
    if os.path.getsize(output_path) > _max_output_bytes():
        raise RuntimeError('ffmpeg output size exceeds configured limit')


def _validated_worker_result(job: dict, result: dict) -> tuple:
    content_path = _text(result.get('content_path') or result.get('contentPath'))
    if content_path:
        if not os.path.isfile(content_path) or os.path.getsize(content_path) <= 0:
            raise RuntimeError('export worker did not produce non-empty media file')
        size = os.path.getsize(content_path)
        if size > _max_output_bytes():
            raise RuntimeError('export worker output size exceeds configured limit')
        content_source = {
            'path': content_path,
            'size': size,
            'hash': _sha256_file(content_path),
        }
    else:
        content = _content_bytes(result.get('content'))
        if not content:
            raise RuntimeError('export worker did not produce non-empty media')
        if len(content) > _max_output_bytes():
            raise RuntimeError('export worker output size exceeds configured limit')
        content_source = {
            'content': content,
            'size': len(content),
            'hash': _sha256_bytes(content),
        }
    export_command_hash = _text(
        result.get('ffmpeg_command_hash') or result.get('ffmpegCommandHash'))
    if not _is_sha256_hash(export_command_hash):
        raise RuntimeError('export worker provenance missing ffmpeg command hash')
    raw_segments = result.get('record_segments') or result.get('recordSegments')
    if not isinstance(raw_segments, list) or not raw_segments:
        raise RuntimeError('export worker provenance missing record_segments')
    normalized = []
    for index, raw in enumerate(raw_segments):
        if not isinstance(raw, dict):
            raise RuntimeError('export worker provenance contains invalid record segment')
        source_hash = _text(raw.get('sourceHash') or raw.get('source_hash'))
        command_hash = _text(raw.get('ffmpegCommandHash') or raw.get('ffmpeg_command_hash'))
        clip_parameters = raw.get('clipParameters') or raw.get('clip_parameters')
        if not _is_sha256_hash(source_hash):
            raise RuntimeError('export worker provenance missing input segment hash')
        if not _is_sha256_hash(command_hash):
            raise RuntimeError('export worker provenance missing segment ffmpeg command hash')
        if not isinstance(clip_parameters, dict):
            raise RuntimeError('export worker provenance missing clip parameters')
        if 'offsetSeconds' not in clip_parameters or 'durationSeconds' not in clip_parameters:
            raise RuntimeError('export worker provenance has incomplete clip parameters')
        stitch_order = raw.get('stitchOrder', raw.get('stitch_order', index))
        if isinstance(stitch_order, bool) or not isinstance(stitch_order, int):
            raise RuntimeError('export worker provenance has invalid stitch order')
        normalized.append({
            **raw,
            'index': raw.get('index', index),
            'sourceHash': source_hash,
            'ffmpegCommandHash': command_hash,
            'exportFfmpegCommandHash': export_command_hash,
            'stitchOrder': stitch_order,
            'clipParameters': dict(clip_parameters),
        })
    if sorted(segment['stitchOrder'] for segment in normalized) != list(range(len(normalized))):
        raise RuntimeError('export worker provenance stitch order is not contiguous')
    return content_source, normalized


def _copy_file_limited(source_path: str, destination: str, limit: int,
                       export_id=None, claim_token=None):
    temporary = destination + '.' + uuid.uuid4().hex + '.tmp'
    written = 0
    try:
        if _path_is_within(destination, _store_root()) \
                and not _path_is_within(destination, _export_temp_root()):
            _ensure_export_store_quota(min(os.path.getsize(source_path), int(limit)))
        with open(source_path, 'rb') as source, open(temporary, 'xb') as output:
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if written > limit:
                    raise RuntimeError('record export output size exceeds configured limit')
                output.write(chunk)
            output.flush()
            os.fsync(output.fileno())
        if written <= 0:
            raise RuntimeError('record export output is empty')
        if claim_token:
            _assert_export_claim(export_id, claim_token)
        os.replace(temporary, destination)
    finally:
        try:
            os.remove(temporary)
        except FileNotFoundError:
            pass


def _validate_ready_export(job: dict):
    if not _text(job.get('download_url')):
        raise RuntimeError('ready export is missing download URL')
    if not _is_sha256_hash(job.get('file_hash')):
        raise RuntimeError('ready export is missing media hash')
    if not _is_sha256_hash(job.get('ffmpeg_command_hash')):
        raise RuntimeError('ready export is missing ffmpeg command hash')
    if int(job.get('output_size_bytes') or 0) <= 0:
        raise RuntimeError('ready export media is empty')
    if not _manifest_record_segments(job):
        raise RuntimeError('ready export is missing source provenance')


def _validate_ready_manifest(job: dict, manifest: dict):
    if not isinstance(manifest, dict):
        raise RuntimeError('ready export manifest was not persisted')
    if manifest.get('status') != 'ready':
        raise RuntimeError('ready export manifest has invalid status')
    if manifest.get('fileHash') != job.get('file_hash'):
        raise RuntimeError('ready export manifest media hash mismatch')
    if manifest.get('ffmpegCommandHash') != job.get('ffmpeg_command_hash'):
        raise RuntimeError('ready export manifest command hash mismatch')
    if not manifest.get('recordSegments'):
        raise RuntimeError('ready export manifest is missing record segments')


def _single_clip_command(ffmpeg: str, source_path: str, output_path: str, job: dict,
                         source: dict = None) -> list:
    command = [ffmpeg, *ffmpeg_resource_options(), '-y']
    parameters = _clip_parameters(source or {}, job)
    offset = parameters['offsetSeconds']
    duration = parameters['durationSeconds']
    mode = _text(os.environ.get(_FFMPEG_MODE_ENV)).lower() or 'browser_compatible'
    if mode not in ('browser_compatible', 'frame_accurate', 'stream_copy'):
        raise RuntimeError(f'unsupported record export ffmpeg mode: {mode}')
    if mode == 'stream_copy' and offset is not None and offset > 0:
        command.extend(['-ss', f'{offset:.3f}'])
    command.extend(['-i', source_path])
    if mode != 'stream_copy' and offset is not None and offset > 0:
        command.extend(['-ss', f'{offset:.3f}'])
    if duration is not None and duration > 0:
        command.extend(['-t', f'{duration:.3f}'])
    if mode == 'stream_copy':
        command.extend(['-c', 'copy'])
    else:
        command.extend(_browser_transcode_options(_text(job.get('format')) or 'mp4'))
    command.extend(ffmpeg_output_thread_options())
    command.append(output_path)
    return command


def _browser_transcode_options(file_format: str) -> list:
    file_format = _text(file_format).lower() or 'mp4'
    if file_format in ('mp4', 'mov'):
        return [
            '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '23',
            '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-movflags', '+faststart',
        ]
    if file_format == 'webm':
        return ['-c:v', 'libvpx-vp9', '-crf', '32', '-b:v', '0', '-c:a', 'libopus']
    if file_format == 'avi':
        return ['-c:v', 'mpeg4', '-q:v', '5', '-c:a', 'libmp3lame']
    if file_format == 'ts':
        return ['-c:v', 'libx264', '-preset', 'veryfast', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-f', 'mpegts']
    return ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '23', '-pix_fmt', 'yuv420p', '-c:a', 'aac']


def _expected_export_duration(job: dict, sources: list):
    durations = []
    for source in sources or []:
        duration = _clip_parameters(source, job).get('durationSeconds')
        if duration is not None and duration > 0:
            durations.append(float(duration))
    return sum(durations) if durations else _clip_duration_seconds(job)


def _source_processing_duration(ffprobe: str, source: dict, job: dict):
    clipped = _clip_parameters(source, job).get('durationSeconds')
    if clipped is not None and clipped > 0:
        return float(clipped)
    cached = source.get('input_duration_seconds')
    try:
        cached = float(cached)
    except (TypeError, ValueError):
        cached = 0.0
    if cached > 0:
        return cached
    duration = _probe_media_duration_seconds(ffprobe, _text(source.get('path')))
    if duration is not None:
        source['input_duration_seconds'] = duration
    return duration


def _probe_media_duration_seconds(ffprobe: str, source_path: str):
    try:
        result = subprocess.run([
            ffprobe,
            '-v', 'error',
            '-show_entries', 'format=duration',
            '-of', 'default=noprint_wrappers=1:nokey=1',
            source_path,
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
    except (OSError, subprocess.TimeoutExpired):
        return None
    if result.returncode != 0:
        return None
    try:
        duration = float(result.stdout.decode('utf-8').strip())
    except (AttributeError, TypeError, ValueError, UnicodeDecodeError):
        return None
    return duration if duration > 0 else None


def _validate_media_artifact(ffmpeg: str, ffprobe: str, output_path: str,
                             expected_duration=None) -> dict:
    probe = subprocess.run([
        ffprobe,
        '-v', 'error',
        '-show_entries', 'format=duration:stream=codec_type,codec_name',
        '-of', 'json',
        output_path,
    ], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=120)
    if probe.returncode != 0:
        detail = probe.stderr.decode('utf-8', errors='replace')[-1000:]
        raise RuntimeError(f'ffprobe validation failed: {detail}'.strip())
    try:
        metadata = json.loads(probe.stdout.decode('utf-8'))
        duration = float((metadata.get('format') or {}).get('duration') or 0)
        streams = metadata.get('streams') or []
    except (TypeError, ValueError, json.JSONDecodeError) as exc:
        raise RuntimeError('ffprobe returned invalid media metadata') from exc
    video_stream = next(
        (stream for stream in streams if stream.get('codec_type') == 'video'), None)
    if duration <= 0 or not video_stream or not _text(video_stream.get('codec_name')):
        raise RuntimeError('ffprobe found no decodable video stream or positive duration')
    _validate_probed_duration(duration, expected_duration)
    decoded = run_ffmpeg_guarded([
        ffmpeg, *ffmpeg_resource_options(),
        '-v', 'error', '-i', output_path, '-map', '0:v:0', '-f', 'null', '-'
    ], expected_duration=duration)
    if decoded.returncode != 0:
        detail = decoded.stderr.decode('utf-8', errors='replace')[-1000:]
        raise RuntimeError(f'ffmpeg decode verification failed: {detail}'.strip())
    return {
        'durationSeconds': duration,
        'videoCodec': _text(video_stream.get('codec_name')),
        'decodable': True,
        'ffprobeVerified': True,
    }


def _validate_probed_duration(actual_duration: float, expected_duration=None):
    configured_max = (
        _positive_int(os.environ.get(_MAX_TOTAL_DURATION_SECONDS_ENV))
        or _DEFAULT_MAX_TOTAL_DURATION_SECONDS
    )
    actual = float(actual_duration)
    if actual > configured_max:
        raise RuntimeError(
            'ffprobe duration exceeds the configured maximum export duration')
    if not expected_duration:
        return
    expected = float(expected_duration)
    tolerance = max(0.5, min(1.0, expected * 0.1))
    if actual < expected - tolerance:
        raise RuntimeError('ffprobe duration is shorter than the requested clip window')
    if actual > expected + tolerance:
        raise RuntimeError('ffprobe duration exceeds the requested clip window')


def _record_uris(job: dict) -> list:
    values = job.get('record_uris') or job.get('recordUris')
    if isinstance(values, (list, tuple)):
        return [_text(value) for value in values if _text(value)]
    record_uri = _text(job.get('record_uri') or job.get('recordUri'))
    return [record_uri] if record_uri else []


def _materialize_record_sources(job: dict, workdir: str) -> list:
    sources = []
    total_input_bytes = 0
    source_specs = _record_source_specs(job)
    max_segments = _positive_int(os.environ.get(_MAX_SEGMENTS_ENV)) or _DEFAULT_MAX_SEGMENTS
    if len(source_specs) > max_segments:
        raise RuntimeError(f'record segment count exceeds limit {max_segments}')
    for index, spec in enumerate(source_specs):
        uri = _text(spec.get('record_uri'))
        local_path = _local_file_path(uri)
        if local_path and os.path.exists(local_path):
            if not os.path.isfile(local_path) or os.path.getsize(local_path) <= 0:
                raise RuntimeError(f'record source is empty: {uri}')
            source_size = os.path.getsize(local_path)
            total_input_bytes += source_size
            if total_input_bytes > _max_input_bytes():
                raise RuntimeError('record export input size exceeds configured limit')
            _ensure_export_temp_quota(source_size)
            extension = os.path.splitext(local_path)[1] or '.bin'
            snapshot_path = os.path.join(workdir, f'source-{index:03d}{extension}')
            _snapshot_local_source(local_path, snapshot_path, max_bytes=source_size)
            sources.append({
                **spec,
                'path': snapshot_path,
                'source_hash': _sha256_file(snapshot_path),
            })
            continue

        space_id, object_name = _record_object_identity(spec, uri)
        if not space_id or not object_name:
            raise RuntimeError(f'record source is not resolvable: {uri}')
        from app.services.record_video_service import materialize_record_video
        extension = os.path.splitext(object_name)[1] or '.bin'
        materialized_path = os.path.join(workdir, f'source-{index:03d}{extension}')
        remaining = min(
            _max_input_bytes() - total_input_bytes,
            _remaining_export_temp_bytes(),
        )
        if remaining <= 0:
            raise RuntimeError('record export temporary storage quota exceeded')
        materialized = materialize_record_video(
            int(space_id), object_name, materialized_path,
            max_bytes=remaining,
            tenant_id=_tenant_id_from_payload(job),
        )
        materialized_size = int(materialized.get('size_bytes') or os.path.getsize(materialized_path))
        total_input_bytes += materialized_size
        if total_input_bytes > _max_input_bytes():
            raise RuntimeError('record export input size exceeds configured limit')
        _ensure_export_temp_quota(0)
        sources.append({
            **spec,
            'space_id': int(space_id),
            'object_name': object_name,
            'path': materialized_path,
            'source_hash': _sha256_file(materialized_path),
        })
    return sources


def _snapshot_local_source(source_path: str, snapshot_path: str, max_bytes=None):
    written = 0
    try:
        with open(source_path, 'rb') as source_file, open(snapshot_path, 'xb') as snapshot_file:
            while True:
                chunk = source_file.read(1024 * 1024)
                if not chunk:
                    break
                written += len(chunk)
                if max_bytes is not None and written > int(max_bytes):
                    raise RuntimeError('record export temporary storage quota exceeded')
                snapshot_file.write(chunk)
            snapshot_file.flush()
            os.fsync(snapshot_file.fileno())
    except Exception:
        try:
            os.remove(snapshot_path)
        except FileNotFoundError:
            pass
        raise
    if os.path.getsize(snapshot_path) <= 0:
        raise RuntimeError(f'record source snapshot is empty: {source_path}')


def _record_source_specs(job: dict) -> list:
    explicit = job.get('record_segments') or job.get('recordSegments')
    if isinstance(explicit, (list, tuple)) and explicit:
        specs = []
        for index, raw in enumerate(explicit):
            if not isinstance(raw, dict):
                continue
            specs.append({
                'index': raw.get('index', index),
                'record_uri': _text(
                    raw.get('original_record_uri')
                    or raw.get('originalRecordUri')
                    or raw.get('record_uri')
                    or raw.get('recordUri')
                    or raw.get('uri')
                ),
                'space_id': raw.get('space_id') or raw.get('spaceId') or job.get('space_id'),
                'object_name': _text(raw.get('object_name') or raw.get('objectName') or job.get('object_name')),
                'segment_start_time': _text(raw.get('segment_start_time') or raw.get('segmentStartTime') or job.get('segment_start_time')),
                'segment_end_time': _text(raw.get('segment_end_time') or raw.get('segmentEndTime') or job.get('segment_end_time')),
                'clip_start_time': _text(raw.get('clip_start_time') or raw.get('clipStartTime') or job.get('start_time')),
                'clip_end_time': _text(raw.get('clip_end_time') or raw.get('clipEndTime') or job.get('end_time')),
            })
        return [spec for spec in specs if spec.get('record_uri')]
    return [{
        'index': index,
        'record_uri': uri,
        'space_id': job.get('space_id'),
        'object_name': _text(job.get('object_name')),
        'segment_start_time': _text(job.get('segment_start_time')),
        'segment_end_time': _text(job.get('segment_end_time')),
        'clip_start_time': _text(job.get('start_time')),
        'clip_end_time': _text(job.get('end_time')),
    } for index, uri in enumerate(_record_uris(job))]


def _record_object_identity(spec: dict, uri: str):
    space_id = spec.get('space_id')
    object_name = _text(spec.get('object_name'))
    if space_id and object_name:
        return space_id, object_name
    path = unquote(urlparse(uri).path)
    matched = re.search(r'/video/record/space/(\d+)/video/(.+)$', path)
    if not matched:
        return space_id, object_name
    return space_id or int(matched.group(1)), object_name or matched.group(2)


def _materialized_manifest_segments(job: dict, sources: list, command_hashes: list,
                                    export_command_hash: str) -> list:
    return [{
        'index': source.get('index', index),
        'recordUri': source.get('record_uri'),
        'originalRecordUri': source.get('original_record_uri'),
        'artifactName': source.get('artifact_name'),
        'sourceHash': source.get('source_hash'),
        'segmentStartTime': source.get('segment_start_time') or _text(job.get('segment_start_time')),
        'segmentEndTime': source.get('segment_end_time') or _text(job.get('segment_end_time')),
        'clipStartTime': source.get('clip_start_time') or _text(job.get('start_time')),
        'clipEndTime': source.get('clip_end_time') or _text(job.get('end_time')),
        'ffmpegCommandHash': command_hashes[index],
        'exportFfmpegCommandHash': export_command_hash,
        'stitchOrder': index,
        'clipParameters': _clip_parameters(source, job),
        'objectName': source.get('object_name') or _text(job.get('object_name')),
        'spaceId': _text(source.get('space_id') or job.get('space_id')),
    } for index, source in enumerate(sources)]


def _persist_materialized_sources(export_id: str, sources: list, claim_token=None) -> list:
    if claim_token:
        _assert_export_claim(export_id, claim_token)
    export_dir = _export_dir(export_id)
    os.makedirs(export_dir, exist_ok=True)
    persisted = []
    artifact_names = set()
    for index, source in enumerate(sources):
        source_path = _text(source.get('path'))
        if not source_path or not os.path.isfile(source_path):
            continue
        extension = os.path.splitext(source_path)[1]
        if not re.fullmatch(r'\.[A-Za-z0-9]{1,10}', extension or ''):
            extension = '.bin'
        artifact_name = f'source-{index:03d}{extension.lower()}'
        artifact_path = os.path.join(export_dir, artifact_name)
        _copy_file_limited(
            source_path,
            artifact_path,
            _max_input_bytes(),
            export_id=export_id,
            claim_token=claim_token,
        )
        artifact_names.add(artifact_name)
        persisted.append({
            **source,
            'original_record_uri': source.get('record_uri'),
            'record_uri': artifact_path,
            'path': artifact_path,
            'artifact_name': artifact_name,
            'source_hash': _sha256_file(artifact_path),
        })
    if claim_token:
        _assert_export_claim(export_id, claim_token)
    for name in os.listdir(export_dir):
        if re.fullmatch(r'source-\d{3}\.[A-Za-z0-9]{1,10}', name) \
                and name not in artifact_names:
            os.remove(os.path.join(export_dir, name))
    return persisted


def _local_file_path(uri: str) -> str:
    uri = _text(uri)
    parsed = urlparse(uri)
    if uri.startswith(('/video/', '/api/')) or parsed.scheme.lower() in {'http', 'https'}:
        return ''
    if uri.lower().startswith('file:'):
        raise ValueError('record source file URI is not allowed')
    if os.path.isabs(uri):
        try:
            return resolve_allowed_local_media_file(uri)
        except LocalMediaPathError as exc:
            raise ValueError(f'record source local path denied: {exc.reason}') from exc
    return ''


def _existing_local_file_path(uri: str) -> str:
    try:
        return _local_file_path(uri)
    except ValueError:
        return ''


def _ffmpeg_concat_path(path: str) -> str:
    return path.replace('\\', '/').replace("'", "'\\''")


def _clip_offset_seconds(job: dict):
    start_time = _parse_time(job.get('start_time') or job.get('startTime'))
    segment_start = _parse_time(job.get('segment_start_time') or job.get('segmentStartTime'))
    if not start_time or not segment_start:
        return None
    return max(0, (start_time - segment_start).total_seconds())


def _clip_duration_seconds(job: dict):
    start_time = _parse_time(job.get('start_time') or job.get('startTime'))
    end_time = _parse_time(job.get('end_time') or job.get('endTime'))
    if not start_time or not end_time or end_time <= start_time:
        return None
    return (end_time - start_time).total_seconds()


def _clip_parameters(source: dict, job: dict) -> dict:
    clip_start_text = _text(source.get('clip_start_time') or source.get('clipStartTime')
                            or job.get('start_time') or job.get('startTime'))
    clip_end_text = _text(source.get('clip_end_time') or source.get('clipEndTime')
                          or job.get('end_time') or job.get('endTime'))
    segment_start = _parse_time(source.get('segment_start_time') or source.get('segmentStartTime')
                                or job.get('segment_start_time') or job.get('segmentStartTime'))
    clip_start = _parse_time(clip_start_text)
    clip_end = _parse_time(clip_end_text)
    offset = max(0.0, (clip_start - segment_start).total_seconds()) \
        if clip_start and segment_start else 0.0
    duration = (clip_end - clip_start).total_seconds() \
        if clip_start and clip_end and clip_end > clip_start else None
    return {
        'clipStartTime': clip_start_text or None,
        'clipEndTime': clip_end_text or None,
        'offsetSeconds': float(offset),
        'durationSeconds': float(duration) if duration is not None else None,
    }


def _content_bytes(content):
    if content is None:
        return None
    if isinstance(content, bytes):
        return content
    if isinstance(content, bytearray):
        return bytes(content)
    return str(content).encode('utf-8')


def _public_job(job: dict) -> dict:
    public = {key: value for key, value in job.items() if not str(key).startswith('_')}
    export_id = _text(public.get('export_id'))
    if export_id:
        public.setdefault('manifest_url', f'/video/record/export/{export_id}/manifest')
    return public


def _append_export_audit(export_id: str, action: str, operator_user_id=None,
                         reason=None, extra=None, claim_token=None,
                         idempotency_key=None):
    lock_token = _acquire_audit_lock(export_id)
    job = None
    try:
        audit_path = _audit_path(export_id)
        stored = _read_json_strict(audit_path, 'record export audit') \
            if os.path.exists(audit_path) else []
        if not isinstance(stored, list):
            raise RuntimeError('record export audit must be a JSON array')
        existing = _ensure_audit_hash_chain(stored)
        if idempotency_key:
            for current in existing:
                if current.get('decision_id') == idempotency_key:
                    comparable = {
                        'export_id': export_id,
                        'action': action,
                        'operator_user_id': _text(operator_user_id) or None,
                        'reason': _text(reason) or None,
                    }
                    if extra:
                        comparable.update(extra)
                    if any(current.get(key) != value
                           for key, value in comparable.items()):
                        raise RecordExportAccessDecisionConflictError(
                            f'record export access decision conflict: {idempotency_key}')
                    return current
        previous_audit = list(existing)
        previous_hash = existing[-1].get('entryHash') if existing else 'GENESIS'
        entry = {
            'export_id': export_id,
            'action': action,
            'operator_user_id': _text(operator_user_id) or None,
            'reason': _text(reason) or None,
            'happened_at': datetime.now(timezone.utc).isoformat(),
            'previousHash': previous_hash,
        }
        if extra:
            entry.update({key: value for key, value in extra.items() if value is not None})
        entry['entryHash'] = _audit_entry_hash(entry)
        existing.append(entry)
        _EXPORT_AUDIT[export_id] = list(existing)
        _write_json(audit_path, existing)
        job = _read_json(_job_path(export_id), None) \
            or _EXPORT_JOBS.get(export_id) or {}
        _persist_manifest(export_id, job)
        if isinstance(job, dict) and job.get('status') == 'ready' and _uses_object_storage(job):
            try:
                _sync_object_storage_artifacts(
                    job, metadata_only=True, claim_token=claim_token)
                _publish_object_commit_marker(job, claim_token=claim_token)
            except Exception:
                # Keep the local authoritative metadata aligned with the last committed
                # object generation so a concurrent GET cannot observe a mixed audit.
                _EXPORT_AUDIT[export_id] = list(previous_audit)
                _write_json(audit_path, previous_audit)
                _persist_manifest(export_id, job)
                try:
                    _sync_object_storage_artifacts(
                        job, metadata_only=True, claim_token=claim_token)
                    _publish_object_commit_marker(job, claim_token=claim_token)
                except Exception:
                    _LOGGER.exception(
                        'failed to restore committed record export metadata after audit append')
                raise
    finally:
        _release_named_claim(_audit_lock_path(export_id), lock_token)
    return entry


def _get_export_job(export_id: str):
    stored = _read_json(_job_path(export_id), None)
    if isinstance(stored, dict):
        existing = _EXPORT_JOBS.get(export_id) or {}
        if existing.get('_worker_runner') is not None:
            stored['_worker_runner'] = existing['_worker_runner']
        _EXPORT_JOBS[export_id] = stored
        return stored
    return _EXPORT_JOBS.get(export_id)


def _reload_export_job(export_id: str):
    stored = _read_json(_job_path(export_id), None)
    if not isinstance(stored, dict):
        return None
    existing = _EXPORT_JOBS.get(export_id) or {}
    if existing.get('_worker_runner') is not None:
        stored['_worker_runner'] = existing['_worker_runner']
    _EXPORT_JOBS[export_id] = stored
    return stored


def _acquire_export_claim(export_id: str):
    path = _claim_path(export_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    token = uuid.uuid4().hex
    for _attempt in range(2):
        try:
            descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        except FileExistsError:
            if not _claim_is_stale(path):
                return None
            if not _remove_stale_claim(path):
                return None
            continue
        with os.fdopen(descriptor, 'w', encoding='utf-8') as claim_file:
            json.dump({
                'token': token,
                'pid': os.getpid(),
                'claimedAt': datetime.now(timezone.utc).isoformat(),
            }, claim_file, sort_keys=True)
            claim_file.flush()
            os.fsync(claim_file.fileno())
        return token
    return None


def _claim_is_stale(path: str) -> bool:
    try:
        age_seconds = datetime.now().timestamp() - os.path.getmtime(path)
    except OSError:
        return True
    configured = _nonnegative_int(os.environ.get(_CLAIM_STALE_SECONDS_ENV))
    stale_seconds = _DEFAULT_CLAIM_STALE_SECONDS if configured is None else configured
    return age_seconds >= stale_seconds


def _remove_stale_claim(path: str) -> bool:
    """Remove only the inode observed as stale; never unlink a replacement claim."""
    quarantine = path + '.' + uuid.uuid4().hex + '.stale'
    try:
        os.link(path, quarantine)
        current = os.stat(path)
        observed = os.stat(quarantine)
        if (current.st_dev, current.st_ino) != (observed.st_dev, observed.st_ino):
            return False
        if not _claim_is_stale(quarantine):
            return False
        os.remove(path)
        return True
    except (FileExistsError, FileNotFoundError, OSError):
        return False
    finally:
        try:
            os.remove(quarantine)
        except FileNotFoundError:
            pass


def _release_export_claim(export_id: str, token: str):
    _release_named_claim(_claim_path(export_id), token)


def _heartbeat_export_claim(export_id: str, token: str) -> bool:
    path = _claim_path(export_id)
    try:
        with open(path, 'r+', encoding='utf-8') as claim_file:
            persisted = _claim_token_from_text(claim_file.read())
            if persisted != token:
                return False
            opened = os.fstat(claim_file.fileno())
            current = os.stat(path)
            if (opened.st_dev, opened.st_ino) != (current.st_dev, current.st_ino):
                return False
            os.utime(path, None)
        with open(path, 'r', encoding='utf-8') as claim_file:
            return _claim_token_from_text(claim_file.read()) == token
    except (FileNotFoundError, OSError, ValueError):
        return False


def _assert_export_claim(export_id: str, token: str):
    try:
        with open(_claim_path(export_id), 'r', encoding='utf-8') as claim_file:
            persisted = _claim_token_from_text(claim_file.read())
    except (FileNotFoundError, OSError):
        persisted = None
    if persisted != token:
        raise RecordExportClaimLostError(
            f'record export worker claim was lost: {export_id}')


def _claim_token_from_text(value: str):
    value = _text(value)
    if not value:
        return None
    try:
        parsed = json.loads(value)
    except (TypeError, ValueError):
        return value
    return _text(parsed.get('token')) if isinstance(parsed, dict) else None


class _ExportClaimHeartbeat:
    def __init__(self, export_id: str, token: str):
        self.export_id = export_id
        self.token = token
        stale = _nonnegative_int(os.environ.get(_CLAIM_STALE_SECONDS_ENV))
        stale = _DEFAULT_CLAIM_STALE_SECONDS if stale is None else max(stale, 1)
        configured = _positive_int(os.environ.get(_CLAIM_HEARTBEAT_SECONDS_ENV))
        self.interval = configured or max(1, min(30, stale // 3))
        self.stop_event = threading.Event()
        self.lost = threading.Event()
        self.thread = threading.Thread(
            target=self._run,
            name=f'record-export-heartbeat-{export_id}',
            daemon=True,
        )

    def start(self):
        self.thread.start()
        return self

    def stop(self):
        self.stop_event.set()
        self.thread.join(timeout=max(1, self.interval + 1))

    def assert_owned(self):
        if self.lost.is_set():
            raise RecordExportClaimLostError(
                f'record export worker heartbeat lost claim: {self.export_id}')
        _assert_export_claim(self.export_id, self.token)

    def _run(self):
        while not self.stop_event.wait(self.interval):
            if not _heartbeat_export_claim(self.export_id, self.token):
                self.lost.set()
                return


def _acquire_audit_lock(export_id: str):
    path = _audit_lock_path(export_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    deadline = time.monotonic() + _AUDIT_LOCK_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        token = uuid.uuid4().hex
        try:
            descriptor = os.open(path, os.O_CREAT | os.O_EXCL | os.O_WRONLY)
        except (FileExistsError, PermissionError):
            _remove_stale_named_claim(path, _AUDIT_LOCK_STALE_SECONDS)
            time.sleep(0.01)
            continue
        with os.fdopen(descriptor, 'w', encoding='utf-8') as lock_file:
            lock_file.write(token)
            lock_file.flush()
            os.fsync(lock_file.fileno())
        return token
    raise RuntimeError(f'timed out acquiring record export audit lock: {export_id}')


def _remove_stale_named_claim(path: str, stale_seconds: int) -> bool:
    quarantine = path + '.' + uuid.uuid4().hex + '.stale'
    try:
        os.link(path, quarantine)
        current = os.stat(path)
        observed = os.stat(quarantine)
        if (current.st_dev, current.st_ino) != (observed.st_dev, observed.st_ino):
            return False
        age_seconds = datetime.now().timestamp() - observed.st_mtime
        if age_seconds < stale_seconds:
            return False
        os.remove(path)
        return True
    except (FileExistsError, FileNotFoundError, PermissionError, OSError):
        return False
    finally:
        try:
            os.remove(quarantine)
        except FileNotFoundError:
            pass


def _release_named_claim(path: str, token: str):
    try:
        with open(path, 'r', encoding='utf-8') as claim_file:
            persisted_token = _claim_token_from_text(claim_file.read())
        if persisted_token == token:
            os.remove(path)
    except FileNotFoundError:
        return


def _retry_at(attempt_count: int) -> datetime:
    configured = _nonnegative_int(os.environ.get(_RETRY_BACKOFF_SECONDS_ENV))
    base_seconds = _DEFAULT_RETRY_BACKOFF_SECONDS if configured is None else configured
    multiplier = 1 << min(max(int(attempt_count or 1) - 1, 0), 4)
    return datetime.now(timezone.utc) + timedelta(seconds=base_seconds * multiplier)


def _persist_job(job: dict):
    if not job:
        return
    export_id = _text(job.get('export_id'))
    if not export_id:
        return
    _write_json(_job_path(export_id), _public_job(job))


def _get_export_content(export_id: str):
    content = _EXPORT_CONTENT.get(export_id)
    if content is not None:
        return content
    path = _content_path(export_id)
    if not os.path.exists(path):
        return None
    with open(path, 'rb') as content_file:
        content = content_file.read()
    _EXPORT_CONTENT[export_id] = content
    return content


def _persist_content(export_id: str, content: bytes, claim_token=None):
    if len(content) > _max_output_bytes():
        raise RuntimeError('record export output size exceeds configured limit')
    path = _content_path(export_id)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    temporary_path = path + '.' + uuid.uuid4().hex + '.tmp'
    try:
        _ensure_export_store_quota(len(content))
        with open(temporary_path, 'wb') as content_file:
            content_file.write(content)
            content_file.flush()
            os.fsync(content_file.fileno())
        if claim_token:
            _assert_export_claim(export_id, claim_token)
        os.replace(temporary_path, path)
    finally:
        if os.path.exists(temporary_path):
            os.remove(temporary_path)


def _persist_content_source(export_id: str, source: dict, claim_token=None):
    if claim_token:
        _assert_export_claim(export_id, claim_token)
    content = source.get('content')
    if content is not None:
        _persist_content(export_id, content, claim_token=claim_token)
        return
    source_path = _text(source.get('path'))
    if not source_path or not os.path.isfile(source_path):
        raise RuntimeError('export worker content path is missing')
    destination = _content_path(export_id)
    _copy_file_limited(
        source_path,
        destination,
        _max_output_bytes(),
        export_id=export_id,
        claim_token=claim_token,
    )
    if os.path.abspath(source_path) != os.path.abspath(destination) \
            and os.path.basename(source_path).startswith('worker-output.'):
        try:
            os.remove(source_path)
        except FileNotFoundError:
            pass


def _delete_content(export_id: str):
    path = _content_path(export_id)
    if os.path.exists(path):
        os.remove(path)


def _delete_source_artifacts(export_id: str):
    export_dir = _export_dir(export_id)
    if not os.path.isdir(export_dir):
        return
    for name in os.listdir(export_dir):
        if re.fullmatch(r'source-\d{3}\.[A-Za-z0-9]{1,10}', name):
            os.remove(os.path.join(export_dir, name))


def _delete_transient_artifacts(export_id: str):
    export_dir = _export_dir(export_id)
    if not os.path.isdir(export_dir):
        return
    for name in os.listdir(export_dir):
        if name.startswith('worker-output.') \
                or re.fullmatch(r'clip-\d{3}\.[A-Za-z0-9]{1,10}', name) \
                or (name.startswith('download-') and name.endswith('.tmp')) \
                or name == 'concat.txt' \
                or name == '.staging':
            _remove_resource_path(os.path.join(export_dir, name))


def _get_export_audit(export_id: str) -> list:
    path = _audit_path(export_id)
    stored = _read_json_strict(path, 'record export audit') \
        if os.path.exists(path) else []
    if not isinstance(stored, list):
        raise RuntimeError('record export audit must be a JSON array')
    stored = _ensure_audit_hash_chain(stored)
    _EXPORT_AUDIT[export_id] = list(stored)
    return list(stored)


def _persist_manifest(export_id: str, job: dict = None) -> dict:
    job = job or _read_json(_job_path(export_id), None) \
        or _EXPORT_JOBS.get(export_id) or {}
    if not isinstance(job, dict) or not job:
        return {}
    job = _public_job(job)
    audit = _get_export_audit(export_id)
    manifest = _build_manifest(job, audit)
    _write_json(_manifest_path(export_id), manifest)
    return manifest


def _build_manifest(job: dict, audit: list) -> dict:
    export_id = _text(job.get('export_id'))
    audit = _ensure_audit_hash_chain(audit)
    download_records = []
    for entry in audit:
        if entry.get('action') != 'downloaded':
            continue
        download_records.append({
            'operatorUserId': _text(entry.get('operator_user_id')) or None,
            'reason': _text(entry.get('reason')) or None,
            'downloadedAt': entry.get('happened_at'),
            'fileHash': entry.get('file_hash') or job.get('file_hash'),
        })
    expires_at = _manifest_expires_at(job)
    manifest = {
        'manifestVersion': 2,
        'schema': 'yfeieye.record-export.manifest.v2',
        'exportId': export_id,
        'reviewCaseId': _text(job.get('review_case_id')),
        'reviewItemIds': _text_list(job.get('review_item_ids') or job.get('review_item_id')),
        'eventIds': _text_list(job.get('event_ids')),
        'eventReferences': _manifest_event_references(job),
        'deviceId': _text(job.get('device_id')),
        'cameraId': _text(job.get('camera_id')),
        'tenantId': _text(job.get('tenant_id')) or None,
        'sourceAlertId': _text(job.get('source_alert_id')),
        'timeWindow': {
            'startTime': _text(job.get('start_time')),
            'endTime': _text(job.get('end_time')),
        },
        'recordSegments': _manifest_record_segments(job),
        'ffmpegCommandHash': _text(job.get('ffmpeg_command_hash')) or None,
        'snapshots': _text_list(job.get('snapshot_uris')),
        'fileHash': _text(job.get('file_hash')),
        'packageChecksum': _text(job.get('file_hash')),
        'files': _manifest_files(export_id, job),
        'status': _text(job.get('status')),
        'generatedBy': _text(job.get('operator_user_id')) or None,
        'generatedAt': job.get('created_at') or job.get('finished_at'),
        'approval': _manifest_approval(job),
        'expiresAt': expires_at,
        'retentionPolicy': {
            'retentionDays': _text(job.get('retention_days')) or None,
            'expiresAt': expires_at,
        },
        'storageLifecycle': _manifest_storage_lifecycle(export_id, job, expires_at),
        'storageVerification': job.get('storage_verification'),
        'mediaProbe': job.get('media_probe'),
        'finishedAt': job.get('finished_at'),
        'downloadRecords': download_records,
        'immutableAudit': {
            'algorithm': 'sha256(previousHash + canonicalEntry)',
            'entryCount': len(audit),
            'headHash': audit[-1].get('entryHash') if audit else None,
        },
        'audit': audit,
    }
    signing_key = validate_record_export_signing_configuration()
    manifest['signature'] = _manifest_signature_metadata(signing_key)
    manifest_hash = _expected_manifest_hash(manifest)
    manifest['manifestHash'] = manifest_hash
    manifest['signature']['value'] = _expected_manifest_signature(
        manifest, manifest_hash, signing_key.get('secret') or '')
    return manifest


def _canonical_manifest_signed_payload(manifest: dict) -> dict:
    payload = dict(manifest or {})
    payload.pop('manifestHash', None)
    signature = payload.get('signature')
    if isinstance(signature, dict):
        signature = dict(signature)
        signature.pop('value', None)
        payload['signature'] = signature
    return payload


def _expected_manifest_hash(manifest: dict) -> str:
    return _sha256_text(_canonical_json(
        _canonical_manifest_signed_payload(manifest)))


def _manifest_signature_metadata(signing_key: dict) -> dict:
    secret = _text(signing_key.get('secret'))
    signature = {
        'algorithm': 'hmac-sha256' if secret else 'sha256',
        'algorithmVersion': 'v2' if signing_key.get('keyring') else 'v1',
        'signatureVersion': 'v2' if signing_key.get('keyring') else 'v1',
        'keyId': signing_key.get('keyId') or ('local-hmac' if secret else 'local-sha256'),
        'signer': 'yFeiEye-video-evidence',
        'signedAt': datetime.now(timezone.utc).isoformat(),
    }
    if signing_key.get('keyring'):
        signature['keyRotation'] = {
            'activeKeyId': signing_key.get('keyId'),
            'acceptedPreviousKeyIds': signing_key.get('previousKeyIds') or [],
        }
    return signature


def _manifest_signature(manifest: dict, manifest_hash: str = '') -> dict:
    signing_key = validate_record_export_signing_configuration()
    signature = _manifest_signature_metadata(signing_key)
    signed_manifest = dict(manifest or {})
    signed_manifest['signature'] = signature
    signature['value'] = _expected_manifest_signature(
        signed_manifest, manifest_hash, signing_key.get('secret') or '')
    return signature


def _expected_manifest_signature(manifest: dict, manifest_hash: str, secret=None) -> str:
    payload = _canonical_manifest_signed_payload(manifest)
    signing_secret = _text(os.environ.get(_SIGNING_SECRET_ENV)) if secret is None else _text(secret)
    if signing_secret:
        digest = hmac.new(signing_secret.encode('utf-8'), _canonical_json(payload).encode('utf-8'), hashlib.sha256).hexdigest()
        return 'hmac-sha256:' + digest
    return _sha256_text(_canonical_json(payload))


def _select_manifest_signing_key() -> dict:
    keyring, configured_active_key_id = _hmac_keyring_config()
    if keyring:
        active_key_id = (
            _text(os.environ.get(_SIGNING_ACTIVE_KEY_ID_ENV))
            or _text(os.environ.get(_SIGNING_KEY_ID_ENV))
            or configured_active_key_id
        )
        if active_key_id and active_key_id not in keyring:
            raise RuntimeError(
                f'active record export HMAC key is not present in keyring: {active_key_id}')
        if not active_key_id:
            if len(keyring) != 1:
                raise RuntimeError(
                    'record export HMAC keyring requires an explicit active key id')
            active_key_id = next(iter(keyring))
        return {
            'keyId': active_key_id,
            'secret': keyring[active_key_id],
            'keyring': True,
            'previousKeyIds': sorted(key_id for key_id in keyring.keys() if key_id != active_key_id),
        }
    secret = _text(os.environ.get(_SIGNING_SECRET_ENV))
    key_id = _text(os.environ.get(_SIGNING_KEY_ID_ENV))
    return {
        'keyId': key_id or ('local-hmac' if secret else 'development-sha256'),
        'secret': secret,
        'keyring': False,
        'previousKeyIds': [],
        'explicitKeyId': bool(key_id),
    }


def validate_record_export_signing_configuration() -> dict:
    """Return the active signer or fail closed for unsafe runtime configuration."""
    signing_key = _select_manifest_signing_key()
    profile = _record_export_runtime_profile()
    production = profile in ('prod', 'production', 'release', 'staging')
    secret = _text(signing_key.get('secret'))
    if secret:
        keyring, _active = _hmac_keyring_config()
        secrets = list(keyring.values()) if keyring else [secret]
        if any(len(value.encode('utf-8')) < _MIN_HMAC_SECRET_BYTES for value in secrets):
            raise RuntimeError(
                f'record export HMAC keys must be at least {_MIN_HMAC_SECRET_BYTES} bytes')
        if production and not signing_key.get('keyring') and not signing_key.get('explicitKeyId'):
            raise RuntimeError(
                'production record export HMAC signing requires an explicit active key id')
        return {
            **signing_key,
            'algorithm': 'hmac-sha256',
        }
    if production:
        raise RuntimeError(
            'production record export manifests require an active HMAC key')
    fallback_allowed = _as_bool(os.environ.get(_ALLOW_SHA256_FALLBACK_ENV))
    if profile not in ('dev', 'development', 'test', 'testing', 'local') or not fallback_allowed:
        raise RuntimeError(
            'unsigned SHA256 manifest fallback requires explicit development/test opt-in')
    return {
        **signing_key,
        'keyId': 'development-sha256',
        'algorithm': 'sha256',
    }


def _record_export_runtime_profile() -> str:
    return _text(
        os.environ.get('VIDEO_ENV')
        or os.environ.get('FLASK_ENV')
        or os.environ.get('APP_ENV')
        or os.environ.get('EASYAIOT_DEPLOY_PROFILE')
    ).lower()


def _hmac_keyring_config() -> tuple:
    raw = _text(os.environ.get(_SIGNING_KEYS_ENV))
    if not raw:
        return {}, ''
    try:
        parsed = json.loads(raw)
    except (TypeError, ValueError) as exc:
        raise RuntimeError('record export HMAC keyring is malformed') from exc
    if not isinstance(parsed, dict):
        raise RuntimeError('record export HMAC keyring must be a JSON object')
    configured_active_key_id = _text(parsed.get('activeKeyId') or parsed.get('active_key_id'))
    reserved = {'activeKeyId', 'active_key_id', 'keys'}
    if 'keys' in parsed:
        if not isinstance(parsed.get('keys'), dict):
            raise RuntimeError('record export HMAC keyring keys must be an object')
        raw_keys = parsed['keys']
    else:
        raw_keys = {
            key: value for key, value in parsed.items() if key not in reserved}
    if not raw_keys:
        raise RuntimeError('record export HMAC keyring must contain at least one key')
    keyring = {}
    for raw_key_id, raw_secret in raw_keys.items():
        key_id = _text(raw_key_id)
        secret = _text(raw_secret)
        if not key_id or key_id in reserved or not secret:
            raise RuntimeError('record export HMAC keyring contains an invalid key')
        keyring[key_id] = secret
    return keyring, configured_active_key_id


def _manifest_event_references(job: dict) -> list:
    return [{
        'eventId': event_id,
        'relation': 'evidence_for_event',
    } for event_id in _text_list(job.get('event_ids'))]


def _manifest_approval(job: dict) -> dict:
    return {
        'approvedBy': _text(job.get('approved_by')) or None,
        'approvedAt': _text(job.get('approved_at')) or None,
        'approvalNote': _text(job.get('approval_note')) or None,
    }


def _manifest_expires_at(job: dict):
    explicit = _text(job.get('expires_at'))
    if explicit:
        return explicit
    retention_days = _positive_int(job.get('retention_days'))
    if not retention_days:
        return None
    basis = _parse_time(job.get('created_at') or job.get('finished_at')) or _current_local_time()
    return (basis + timedelta(days=retention_days)).isoformat()


def _is_export_expired(job: dict) -> bool:
    raw_expires_at = _text(_manifest_expires_at(job))
    if not raw_expires_at:
        return False
    normalized = raw_expires_at[:-1] + '+00:00' if raw_expires_at.endswith('Z') else raw_expires_at
    try:
        expires_at = datetime.fromisoformat(normalized)
    except ValueError:
        return True
    if expires_at.tzinfo is None:
        expires_at = expires_at.replace(tzinfo=timezone.utc)
    return datetime.now(timezone.utc) >= expires_at.astimezone(timezone.utc)


def _manifest_files(export_id: str, job: dict) -> list:
    files = []
    expires_at = _manifest_expires_at(job)
    path = _content_path(export_id)
    if os.path.isfile(path):
        files.append({
            'name': 'content.bin',
            'role': 'export_package',
            'hash': _sha256_file(path),
            'sizeBytes': os.path.getsize(path),
            'format': _text(job.get('format')) or 'mp4',
            'path': path,
            'storage': _artifact_storage_reference(job, export_id, 'content.bin', 'export_package', path, expires_at),
        })
    # audit.json is append-only mutable metadata. The complete audit chain is
    # already embedded in and signed by each manifest snapshot, so hashing the
    # live ledger here would invalidate a fetched manifest as soon as its own
    # access decision is appended.
    for name, path, role in (
            ('job.json', _job_path(export_id), 'export_job'),):
        if not os.path.exists(path):
            continue
        files.append({
            'name': name,
            'role': role,
            'hash': _sha256_file(path),
            'sizeBytes': os.path.getsize(path),
            'path': path,
            'storage': _artifact_storage_reference(job, export_id, name, role, path, expires_at),
        })
    for index, segment in enumerate(_manifest_record_segments(job)):
        path = _existing_local_file_path(segment.get('recordUri'))
        if not path or not os.path.isfile(path):
            continue
        name = _text(segment.get('artifactName')) or f'source-{index:03d}{os.path.splitext(path)[1] or ".bin"}'
        files.append({
            'name': name,
            'role': 'source_record_segment',
            'hash': _text(segment.get('sourceHash')) or _sha256_file(path),
            'sizeBytes': os.path.getsize(path),
            'path': path,
            'storage': _artifact_storage_reference(job, export_id, name, 'source_record_segment', path, expires_at),
        })
    return files


def _manifest_references_mutable_audit_file(manifest: dict) -> bool:
    return any(
        isinstance(item, dict)
        and (_text(item.get('name')).lower() == 'audit.json'
             or _text(item.get('role')).lower() == 'audit_log')
        for item in manifest.get('files') or []
    )


def _manifest_storage_lifecycle(export_id: str, job: dict, expires_at: str) -> dict:
    return {
        'storageType': _storage_type(job),
        'storeRoot': _storage_root(job),
        'status': _storage_lifecycle_status(expires_at),
        'expiresAt': expires_at,
        'retentionDays': _text(job.get('retention_days')) or None,
        'cleanupPolicy': {
            'scope': 'record_export_artifacts',
            'deleteAfter': expires_at,
        },
        'artifactKeys': {
            'exportPackage': _storage_object_key(job, 'content.bin'),
            'job': _storage_object_key(job, 'job.json'),
            'audit': _storage_object_key(job, 'audit.json'),
            'manifest': _storage_object_key(job, 'manifest.json'),
            'commit': _storage_object_key(job, 'commit.json'),
        },
    }


def _artifact_storage_reference(job: dict, export_id: str, name: str, role: str, path: str, expires_at: str) -> dict:
    object_key = _storage_object_key(job, name)
    reference = {
        'storageType': _storage_type(job),
        'artifactRole': role,
        'objectKey': object_key,
        'expiresAt': expires_at,
        'lifecycleStatus': _storage_lifecycle_status(expires_at),
    }
    root = _storage_root(job)
    if _uses_object_storage(job):
        reference['uri'] = _object_storage_adapter(job).uri(object_key)
    elif root:
        reference['uri'] = _join_storage_uri(root, object_key)
    if path:
        reference['path'] = path
    return reference


def _storage_type(job: dict) -> str:
    job = job or {}
    storage_type = _text(
        job.get('storage_type') or job.get('storageType')
    ).lower()
    if storage_type:
        return storage_type
    tenant_id = _text(job.get('tenant_id') or job.get('tenantId'))
    if not tenant_id:
        return 'local_filesystem'
    return _text(os.environ.get(_STORE_TYPE_ENV)).lower() or 'local_filesystem'


def _storage_root(job: dict) -> str:
    job = job or {}
    storage_root = _text(job.get('storage_root') or job.get('storageRoot'))
    if storage_root:
        return storage_root
    if _storage_type(job) not in ('minio', 's3'):
        return _store_root()
    return _text(os.environ.get(_STORE_URI_ENV)) or _store_root()


def _storage_object_key(job: dict, name: str) -> str:
    export_id = _text((job or {}).get('export_id'))
    if not export_id:
        raise RuntimeError('record export object key requires an export id')
    if not _uses_object_storage(job):
        return f'{export_id}/{name}'
    tenant_id = _text((job or {}).get('tenant_id') or (job or {}).get('tenantId'))
    if not tenant_id:
        raise RuntimeError('record export object storage requires an authorized tenant id')
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}', tenant_id):
        raise RuntimeError('record export tenant id contains unsafe characters')
    return f'tenants/{tenant_id}/exports/{export_id}/{name}'


def _storage_lifecycle_status(expires_at: str) -> str:
    expires = _parse_time(expires_at)
    if expires and expires <= _current_local_time():
        return 'expired'
    return 'retained'


def _join_storage_uri(root: str, object_key: str) -> str:
    root = _text(root).rstrip('/\\')
    if not root:
        return object_key
    return root.replace('\\', '/') + '/' + object_key


def _uses_object_storage(job: dict) -> bool:
    return _storage_type(job) in ('minio', 's3')


def _object_storage_adapter(job: dict):
    if not _uses_object_storage(job):
        raise RuntimeError('record export does not use object storage')
    if _STORAGE_ADAPTER_FACTORY is not None:
        adapter = (
            _STORAGE_ADAPTER_FACTORY(job)
            if callable(_STORAGE_ADAPTER_FACTORY)
            else _STORAGE_ADAPTER_FACTORY
        )
        _validate_storage_adapter(adapter)
        return adapter

    storage_root = _storage_root(job)
    parsed = urlparse(storage_root)
    bucket = (
        (_text(parsed.netloc) if parsed.scheme in ('s3', 'minio') else '')
        or _text(os.environ.get(_S3_BUCKET_ENV))
    )
    prefix = (
        (_text(parsed.path).strip('/') if parsed.scheme in ('s3', 'minio') else '')
        or _text(os.environ.get(_S3_PREFIX_ENV)).strip('/')
    )
    endpoint = _text(os.environ.get(_S3_ENDPOINT_ENV) or os.environ.get('MINIO_ENDPOINT'))
    access_key = _text(
        os.environ.get(_S3_ACCESS_KEY_ENV) or os.environ.get('MINIO_ACCESS_KEY'))
    secret_key = _text(
        os.environ.get(_S3_SECRET_KEY_ENV) or os.environ.get('MINIO_SECRET_KEY'))
    if not endpoint or not bucket or not access_key or not secret_key:
        raise RuntimeError(
            'object storage requires endpoint, bucket, access key, and secret key')
    secure = _as_bool(os.environ.get(_S3_SECURE_ENV) or os.environ.get('MINIO_SECURE'))
    if '://' in endpoint:
        endpoint_url = urlparse(endpoint)
        secure = endpoint_url.scheme.lower() == 'https'
        endpoint = endpoint_url.netloc or endpoint_url.path
    cache_key = (endpoint, access_key, secret_key, secure, bucket, prefix, _storage_type(job))
    adapter = _STORAGE_ADAPTER_CACHE.get(cache_key)
    if adapter is None:
        from minio import Minio
        adapter = _MinioObjectStorageAdapter(
            Minio(endpoint, access_key=access_key, secret_key=secret_key, secure=secure),
            bucket,
            prefix,
            scheme='s3',
        )
        _STORAGE_ADAPTER_CACHE[cache_key] = adapter
    return adapter


def _validate_storage_adapter(adapter):
    missing = [
        name for name in ('put_file', 'stat', 'open', 'delete', 'uri')
        if not callable(getattr(adapter, name, None))
    ]
    if missing:
        raise RuntimeError(
            'record export storage adapter is missing: ' + ', '.join(missing))


def _sync_object_storage_artifacts(job: dict, metadata_only=False, claim_token=None) -> list:
    adapter = _object_storage_adapter(job)
    verified = []
    for name, path in _storage_artifact_paths(job, metadata_only=metadata_only):
        object_key = _storage_object_key(job, name)
        expected_size = os.path.getsize(path)
        expected_hash = _sha256_file(path)
        staging_key = None
        try:
            if claim_token:
                _assert_export_claim(job['export_id'], claim_token)
                staging_key = _staging_object_key(job, claim_token, name)
                _put_and_verify_object(
                    adapter, staging_key, path, expected_size, expected_hash,
                    _artifact_content_type(name, job))
                _assert_export_claim(job['export_id'], claim_token)
            _put_and_verify_object(
                adapter, object_key, path, expected_size, expected_hash,
                _artifact_content_type(name, job))
            if claim_token:
                _assert_export_claim(job['export_id'], claim_token)
        finally:
            if staging_key:
                adapter.delete(staging_key)
        verified.append({
            'objectKey': object_key,
            'uri': adapter.uri(object_key),
            'sizeBytes': expected_size,
            'sha256': expected_hash,
        })
    return verified


def _put_and_verify_object(adapter, object_key: str, path: str, expected_size: int,
                           expected_hash: str, content_type: str):
    adapter.put_file(object_key, path, content_type)
    actual_size = _storage_stat_size(adapter.stat(object_key) or {})
    if actual_size != expected_size:
        raise RuntimeError(
            f'object storage stat size mismatch for {object_key}: '
            f'expected {expected_size}, got {actual_size}')
    stream = adapter.open(object_key)
    try:
        actual_hash, read_size = _hash_stream(stream)
    finally:
        _close_object_stream(stream)
    if read_size != expected_size:
        raise RuntimeError(f'object storage readback size mismatch for {object_key}')
    if actual_hash != expected_hash:
        raise RuntimeError(f'object storage readback hash mismatch for {object_key}')


def _staging_object_key(job: dict, claim_token: str, name: str) -> str:
    return _storage_object_key(job, f'.staging/{claim_token}/{name}')


def _publish_object_commit_marker(job: dict, claim_token=None):
    if claim_token:
        _assert_export_claim(job['export_id'], claim_token)
    manifest = _read_json(_manifest_path(job['export_id']), {})
    audit = _get_export_audit(job['export_id'])
    marker = {
        'version': 1,
        'exportId': job['export_id'],
        'fileHash': manifest.get('fileHash'),
        'manifestHash': manifest.get('manifestHash'),
        'auditHeadHash': audit[-1].get('entryHash') if audit else None,
        'claimEpoch': int(job.get('claim_epoch') or 0),
        'committedAt': datetime.now(timezone.utc).isoformat(),
    }
    path = _commit_marker_path(job['export_id'])
    _write_json(path, marker)
    adapter = _object_storage_adapter(job)
    object_key = _storage_object_key(job, 'commit.json')
    _put_and_verify_object(
        adapter, object_key, path, os.path.getsize(path), _sha256_file(path),
        'application/json')
    if claim_token:
        _assert_export_claim(job['export_id'], claim_token)
    return marker


def _verify_object_commit_marker(job: dict, manifest: dict):
    adapter = _object_storage_adapter(job)
    object_key = _storage_object_key(job, 'commit.json')
    stream = adapter.open(object_key)
    try:
        raw = b''.join(iter(lambda: stream.read(64 * 1024), b''))
    finally:
        _close_object_stream(stream)
    try:
        marker = json.loads(raw.decode('utf-8'))
    except (UnicodeDecodeError, ValueError, TypeError) as exc:
        raise RuntimeError('record export object commit marker is invalid') from exc
    audit = _get_export_audit(job['export_id'])
    expected_head = audit[-1].get('entryHash') if audit else None
    if marker.get('exportId') != job['export_id'] \
            or marker.get('fileHash') != manifest.get('fileHash') \
            or marker.get('manifestHash') != manifest.get('manifestHash') \
            or marker.get('auditHeadHash') != expected_head \
            or int(marker.get('claimEpoch') or 0) != int(job.get('claim_epoch') or 0):
        raise RuntimeError('record export object commit marker is stale or mismatched')


def _ready_job_is_committed(job: dict) -> bool:
    if not _uses_object_storage(job):
        return True
    export_id = job['export_id']
    lock_token = None
    try:
        lock_token = _acquire_audit_lock(export_id)
        manifest = _read_json(_manifest_path(export_id), {})
        _validate_manifest_integrity(manifest)
        _verify_object_commit_marker(job, manifest)
        return True
    except Exception:
        return False
    finally:
        if lock_token:
            _release_named_claim(_audit_lock_path(export_id), lock_token)


def _verify_object_file_copy(job: dict, name: str, local_path: str):
    if not os.path.isfile(local_path):
        raise RuntimeError(f'local record export metadata is missing: {name}')
    adapter = _object_storage_adapter(job)
    object_key = _storage_object_key(job, name)
    expected_size = os.path.getsize(local_path)
    expected_hash = _sha256_file(local_path)
    stat_size = _storage_stat_size(adapter.stat(object_key) or {})
    if stat_size != expected_size:
        raise RuntimeError(f'object metadata size mismatch: {name}')
    stream = adapter.open(object_key)
    try:
        actual_hash, actual_size = _hash_stream(stream)
    finally:
        _close_object_stream(stream)
    if actual_size != expected_size or actual_hash != expected_hash:
        raise RuntimeError(f'object metadata hash mismatch: {name}')


def _delete_object_storage_artifacts(job: dict):
    adapter = _object_storage_adapter(job)
    names = {name for name, _path in _storage_artifact_paths(job)}
    names.update({'content.bin', 'job.json', 'audit.json', 'manifest.json', 'commit.json'})
    keys = {_storage_object_key(job, name) for name in names}
    list_objects = getattr(adapter, 'list', None)
    if callable(list_objects):
        keys.update(list_objects(_storage_object_key(job, '.staging/')))
    failures = []
    for object_key in keys:
        try:
            adapter.delete(object_key)
        except Exception as exc:
            failures.append(f'{object_key}: {exc}')
            _LOGGER.warning('failed to delete record export object %s', object_key, exc_info=True)
    if failures:
        raise RuntimeError('record export object cleanup failed: ' + '; '.join(failures))


def _storage_artifact_paths(job: dict, metadata_only=False) -> list:
    export_id = _text(job.get('export_id'))
    paths = [
        ('job.json', _job_path(export_id)),
        ('audit.json', _audit_path(export_id)),
        ('manifest.json', _manifest_path(export_id)),
    ]
    if not metadata_only:
        paths.insert(0, ('content.bin', _content_path(export_id)))
        export_dir = _export_dir(export_id)
        if os.path.isdir(export_dir):
            paths.extend(
                (name, os.path.join(export_dir, name))
                for name in sorted(os.listdir(export_dir))
                if re.fullmatch(r'source-\d{3}\.[A-Za-z0-9]{1,10}', name)
            )
    return [(name, path) for name, path in paths if os.path.isfile(path)]


def _artifact_content_type(name: str, job: dict) -> str:
    if name == 'content.bin':
        return 'video/mp4' if _text(job.get('format')).lower() == 'mp4' \
            else 'application/octet-stream'
    if name.endswith('.json'):
        return 'application/json'
    return 'application/octet-stream'


def _storage_stat_size(stat) -> int:
    if isinstance(stat, dict):
        value = stat.get('size') or stat.get('sizeBytes') or stat.get('content_length')
    else:
        value = getattr(stat, 'size', None)
    try:
        return int(value)
    except (TypeError, ValueError):
        return -1


def _hash_stream(stream) -> tuple:
    digest = hashlib.sha256()
    size = 0
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            break
        digest.update(chunk)
        size += len(chunk)
    return 'sha256:' + digest.hexdigest(), size


def _close_object_stream(stream):
    close = getattr(stream, 'close', None)
    if callable(close):
        close()
    release = getattr(stream, 'release_conn', None)
    if callable(release):
        release()


def _ensure_audit_hash_chain(entries: list) -> list:
    chained = []
    previous_hash = 'GENESIS'
    for entry in entries or []:
        if not isinstance(entry, dict):
            raise RuntimeError('record export audit entry must be an object')
        current = dict(entry)
        supplied_previous = _text(current.get('previousHash'))
        if not supplied_previous:
            raise RuntimeError('record export audit previous hash is missing')
        if supplied_previous != previous_hash:
            raise RuntimeError('record export audit hash chain previous hash mismatch')
        supplied_hash = _text(current.get('entryHash'))
        if not supplied_hash:
            raise RuntimeError('record export audit entry hash is missing')
        expected_hash = _audit_entry_hash(current)
        if supplied_hash != expected_hash:
            raise RuntimeError('record export audit hash chain entry hash mismatch')
        previous_hash = supplied_hash
        chained.append(current)
    return chained


def _audit_entry_hash(entry: dict) -> str:
    payload = {key: value for key, value in entry.items() if key != 'entryHash'}
    return _sha256_text(_canonical_json(payload))


def _canonical_json(value) -> str:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def _sha256_text(value: str) -> str:
    return 'sha256:' + hashlib.sha256(value.encode('utf-8')).hexdigest()


def _sha256_bytes(content: bytes) -> str:
    return 'sha256:' + hashlib.sha256(content).hexdigest()


def _sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, 'rb') as file_obj:
        for chunk in iter(lambda: file_obj.read(1024 * 1024), b''):
            digest.update(chunk)
    return 'sha256:' + digest.hexdigest()


def _is_sha256_hash(value) -> bool:
    return re.fullmatch(r'sha256:[0-9a-f]{64}', _text(value).lower()) is not None


def _manifest_record_segments(job: dict) -> list:
    explicit_segments = job.get('record_segments') or job.get('recordSegments')
    if isinstance(explicit_segments, (list, tuple)) and explicit_segments:
        segments = []
        for index, raw_segment in enumerate(explicit_segments):
            if not isinstance(raw_segment, dict):
                continue
            uri = _text(raw_segment.get('recordUri') or raw_segment.get('record_uri') or raw_segment.get('uri'))
            segments.append({
                'index': index,
                'recordUri': uri,
                'originalRecordUri': _text(raw_segment.get('originalRecordUri') or raw_segment.get('original_record_uri')) or None,
                'artifactName': _text(raw_segment.get('artifactName') or raw_segment.get('artifact_name')) or None,
                'sourceHash': _text(raw_segment.get('sourceHash') or raw_segment.get('source_hash')) or _source_file_hash(uri),
                'segmentStartTime': _text(raw_segment.get('segmentStartTime') or raw_segment.get('segment_start_time') or job.get('segment_start_time')),
                'segmentEndTime': _text(raw_segment.get('segmentEndTime') or raw_segment.get('segment_end_time') or job.get('segment_end_time')),
                'clipStartTime': _text(raw_segment.get('clipStartTime') or raw_segment.get('clip_start_time') or job.get('start_time')),
                'clipEndTime': _text(raw_segment.get('clipEndTime') or raw_segment.get('clip_end_time') or job.get('end_time')),
                'ffmpegCommandHash': _text(raw_segment.get('ffmpegCommandHash') or raw_segment.get('ffmpeg_command_hash')),
                'exportFfmpegCommandHash': _text(raw_segment.get('exportFfmpegCommandHash')
                                                 or raw_segment.get('export_ffmpeg_command_hash')
                                                 or job.get('ffmpeg_command_hash')),
                'stitchOrder': raw_segment.get('stitchOrder', raw_segment.get('stitch_order', index)),
                'clipParameters': raw_segment.get('clipParameters')
                                  or raw_segment.get('clip_parameters')
                                  or _clip_parameters(raw_segment, job),
                'objectName': _text(raw_segment.get('objectName') or raw_segment.get('object_name') or job.get('object_name')),
                'spaceId': _text(raw_segment.get('spaceId') or raw_segment.get('space_id') or job.get('space_id')),
            })
        return [segment for segment in segments if segment.get('recordUri')]
    segments = []
    record_uris = _record_uris(job)
    for index, uri in enumerate(record_uris):
        segments.append({
            'index': index,
            'recordUri': uri,
            'sourceHash': _source_file_hash(uri),
            'segmentStartTime': _text(job.get('segment_start_time')),
            'segmentEndTime': _text(job.get('segment_end_time')),
            'clipStartTime': _text(job.get('start_time')),
            'clipEndTime': _text(job.get('end_time')),
            'ffmpegCommandHash': _ffmpeg_command_hash(job),
            'exportFfmpegCommandHash': _text(job.get('ffmpeg_command_hash')) or _ffmpeg_command_hash(job),
            'stitchOrder': index,
            'clipParameters': _clip_parameters({}, job),
            'objectName': _text(job.get('object_name')),
            'spaceId': _text(job.get('space_id')),
        })
    return segments


def _source_file_hash(uri: str) -> str:
    path = _existing_local_file_path(uri)
    if path and os.path.exists(path):
        return _sha256_file(path)
    return ''


def _ffmpeg_command_hash(job: dict) -> str:
    payload = {
        'recordUris': _record_uris(job),
        'startTime': _text(job.get('start_time')),
        'endTime': _text(job.get('end_time')),
        'format': _text(job.get('format')) or 'mp4',
    }
    return _sha256_text(_canonical_json(payload))


def _store_root() -> str:
    configured = _text(os.environ.get(_STORE_ENV))
    return configured or _DEFAULT_STORE_ROOT


def _export_temp_root() -> str:
    configured = _text(os.environ.get(_TEMP_DIR_ENV))
    return os.path.abspath(configured or os.path.join(_store_root(), '.tmp'))


def _export_temp_max_bytes() -> int:
    return (
        _positive_int(os.environ.get(_TEMP_MAX_BYTES_ENV))
        or _DEFAULT_TEMP_MAX_BYTES
    )


@contextmanager
def _managed_export_workdir(export_id: str):
    temp_root = _export_temp_root()
    os.makedirs(temp_root, mode=0o750, exist_ok=True)
    cleanup_record_export_resources()
    _ensure_export_temp_quota(0)
    temporary = tempfile.TemporaryDirectory(
        prefix=f'yfeieye-record-export-{_text(export_id)}-',
        dir=temp_root,
    )
    workdir = os.path.abspath(temporary.name)
    with _ACTIVE_TEMP_PATHS_LOCK:
        _ACTIVE_TEMP_PATHS.add(workdir)
    try:
        _write_json(os.path.join(workdir, '.active.json'), {
            'export_id': _text(export_id),
            'pid': os.getpid(),
            'created_at': datetime.now(timezone.utc).isoformat(),
        })
        yield workdir
    finally:
        try:
            temporary.cleanup()
        finally:
            with _ACTIVE_TEMP_PATHS_LOCK:
                _ACTIVE_TEMP_PATHS.discard(workdir)


def _export_dir(export_id: str) -> str:
    safe_id = ''.join(ch if ch.isalnum() or ch in ('-', '_') else '_' for ch in _text(export_id))
    return os.path.join(_store_root(), safe_id)


def _job_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'job.json')


def _audit_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'audit.json')


def _manifest_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'manifest.json')


def _content_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'content.bin')


def _commit_marker_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'commit.json')


def _claim_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'worker.claim')


def _audit_lock_path(export_id: str) -> str:
    return os.path.join(_export_dir(export_id), 'audit.lock')


def _write_json(path: str, value):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    serialized = json.dumps(
        value, ensure_ascii=False, indent=2, sort_keys=True)
    if _path_is_within(path, _store_root()) \
            and not _path_is_within(path, _export_temp_root()):
        try:
            current_size = os.path.getsize(path)
        except OSError:
            current_size = 0
        final_size = len(serialized.encode('utf-8'))
        _ensure_export_store_quota(max(0, final_size - current_size))
    temporary_path = path + '.' + uuid.uuid4().hex + '.tmp'
    try:
        with open(temporary_path, 'w', encoding='utf-8') as json_file:
            json_file.write(serialized)
            json_file.flush()
            os.fsync(json_file.fileno())
        os.replace(temporary_path, path)
        _fsync_parent_directory(path)
    finally:
        if os.path.exists(temporary_path):
            os.remove(temporary_path)


def _fsync_parent_directory(path: str):
    if os.name == 'nt':
        return
    descriptor = None
    try:
        descriptor = os.open(os.path.dirname(path), os.O_RDONLY)
        os.fsync(descriptor)
    except OSError:
        return
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _read_json(path: str, default):
    if not os.path.exists(path):
        return default
    try:
        with open(path, 'r', encoding='utf-8') as json_file:
            return json.load(json_file)
    except (OSError, ValueError, TypeError):
        return default


def _read_json_strict(path: str, description: str):
    try:
        with open(path, 'r', encoding='utf-8') as json_file:
            return json.load(json_file)
    except (OSError, ValueError, TypeError) as exc:
        raise RuntimeError(f'{description} contains invalid JSON') from exc


def resolve_record_uri_from_window(payload: dict) -> dict:
    """Resolve a record URI from VIDEO metadata when DEVICE has only a time window."""
    payload = payload or {}
    resolved = _resolve_from_alert_segment(payload)
    if resolved:
        return resolved

    device_id = _text(payload.get('device_id') or payload.get('deviceId') or payload.get('camera_id') or payload.get('cameraId'))
    tenant_id = _tenant_id_from_payload(payload)
    window_start, window_end = _payload_window(payload)
    if not device_id or not window_start:
        return {}

    try:
        from models import RecordFile, RecordSpace

        space_filters = {'device_id': device_id}
        if tenant_id is not None:
            space_filters['tenant_id'] = tenant_id
        space = RecordSpace.query.filter_by(**space_filters).first()
        if not space:
            return {}
        window_end = window_end or window_start
        lookback_seconds = max(3600, int(abs((window_end - window_start).total_seconds())) + 300)
        records = (
            RecordFile.query.filter(
                *([RecordFile.tenant_id == tenant_id]
                  if tenant_id is not None else []),
                RecordFile.space_id == space.id,
                RecordFile.device_id == device_id,
                RecordFile.event_time >= window_start - timedelta(seconds=lookback_seconds),
                RecordFile.event_time <= window_end + timedelta(seconds=300),
            )
            .order_by(RecordFile.event_time.asc())
            .all()
        )
        record = _select_record_for_window(records, window_start, window_end)
        return _resolved_from_record(
            record, 'record_window', getattr(space, 'id', None))
    except Exception:
        return {}


def _resolve_from_alert_segment(payload: dict) -> dict:
    device_id = _text(payload.get('device_id') or payload.get('deviceId') or payload.get('camera_id') or payload.get('cameraId'))
    source_alert_id = _text(payload.get('source_alert_id') or payload.get('sourceAlertId') or payload.get('alert_id') or payload.get('alertId'))
    tenant_id = _tenant_id_from_payload(payload)
    if not device_id or not source_alert_id.isdigit():
        return {}
    try:
        from app.services.record_video_service import find_segment_for_alert

        result = find_segment_for_alert(
            device_id, int(source_alert_id), tenant_id=tenant_id) or {}
        segment = result.get('segment') or {}
        uri = _text(segment.get('url') or segment.get('record_uri') or segment.get('download_url'))
        if not uri:
            return {}
        return {
            'record_uri': uri,
            'source': 'alert_segment',
            'camera_id': device_id,
            'device_id': device_id,
            'tenant_id': tenant_id,
            'space_id': result.get('space_id'),
            'object_name': segment.get('object_name'),
            'segment_start_time': segment.get('start_time'),
            'segment_end_time': segment.get('end_time'),
            'duration': segment.get('duration'),
        }
    except Exception:
        return {}


def _payload_window(payload: dict):
    start_time = _parse_time(payload.get('start_time') or payload.get('startTime'))
    end_time = _parse_time(payload.get('end_time') or payload.get('endTime'))
    if start_time and end_time and end_time < start_time:
        return end_time, start_time
    return start_time, end_time


def _select_record_for_window(records, window_start: datetime, window_end: datetime):
    if not records or not window_start:
        return None
    window_end = window_end or window_start
    center = window_start + (window_end - window_start) / 2
    best = None
    for record in records:
        segment_start = _normalize_datetime(getattr(record, 'event_time', None))
        if not segment_start:
            continue
        duration = max(1, int(getattr(record, 'duration', None) or 30))
        segment_end = segment_start + timedelta(seconds=duration)
        if segment_start > window_end or segment_end < window_start:
            continue
        distance = _distance_to_segment(center, segment_start, segment_end)
        if best is None or distance < best[0]:
            best = (distance, record)
    return best[1] if best else None


def _resolved_from_record(record, source: str, space_id=None) -> dict:
    if not record:
        return {}
    record_uri = _record_uri(record, space_id)
    if not record_uri:
        return {}
    segment_start = _normalize_datetime(getattr(record, 'event_time', None))
    duration = max(1, int(getattr(record, 'duration', None) or 30))
    segment_end = segment_start + timedelta(seconds=duration) if segment_start else None
    return {
        'record_uri': record_uri,
        'source': source,
        'camera_id': _text(getattr(record, 'device_id', None)),
        'device_id': _text(getattr(record, 'device_id', None)),
        'tenant_id': getattr(record, 'tenant_id', None),
        'space_id': space_id or getattr(record, 'space_id', None),
        'object_name': _text(getattr(record, 'object_name', None)),
        'segment_start_time': segment_start.isoformat() if segment_start else None,
        'segment_end_time': segment_end.isoformat() if segment_end else None,
        'duration': duration,
    }


def _record_uri(record, space_id=None) -> str:
    object_name = _text(getattr(record, 'object_name', None))
    if not object_name:
        return ''
    resolved_space_id = space_id or getattr(record, 'space_id', None)
    if resolved_space_id:
        try:
            from app.utils.service_urls import build_record_video_api_url

            return build_record_video_api_url(int(resolved_space_id), object_name)
        except Exception:
            return object_name
    return object_name


def _tenant_id_from_payload(payload: dict):
    raw = (payload or {}).get('tenant_id')
    if raw is None:
        raw = (payload or {}).get('tenantId')
    if raw is None or _text(raw) == '':
        return None
    value = _positive_int(raw)
    if value is None:
        raise ValueError('record export tenant id must be a positive integer')
    return value


def _distance_to_segment(moment: datetime, segment_start: datetime, segment_end: datetime) -> float:
    if segment_start <= moment <= segment_end:
        return 0
    return min(abs((moment - segment_start).total_seconds()), abs((moment - segment_end).total_seconds()))


def _parse_time(value):
    text = _text(value)
    if not text:
        return None
    normalized = text[:-1] + '+00:00' if text.endswith('Z') else text
    for parser in (
        lambda raw: datetime.fromisoformat(raw),
        lambda raw: datetime.strptime(raw, '%Y-%m-%d %H:%M:%S'),
        lambda raw: datetime.strptime(raw, '%Y-%m-%d %H:%M:%S.%f'),
    ):
        try:
            return _normalize_datetime(parser(normalized))
        except ValueError:
            continue
    return None


def _normalize_datetime(value):
    if not value:
        return None
    if value.tzinfo is None:
        return value
    try:
        from app.utils.service_urls import SHANGHAI_TZ
    except Exception:
        SHANGHAI_TZ = timezone(timedelta(hours=8))
    return value.astimezone(SHANGHAI_TZ).replace(tzinfo=None)


def _current_local_time() -> datetime:
    return _normalize_datetime(datetime.now(timezone.utc))


def _build_export_id(job: dict) -> str:
    identity = {
        key: job.get(key)
        for key in (
            'tenant_id', 'review_case_id', 'review_item_id', 'review_item_ids',
            'event_ids', 'device_id', 'camera_id', 'source_alert_id',
            'record_uri', 'record_uris', 'record_segments', 'space_id', 'object_name',
            'segment_start_time', 'segment_end_time', 'start_time', 'end_time',
            'format', 'storage_type', 'storage_root', 'retention_days',
        )
    }
    digest = hashlib.sha256(_canonical_json(identity).encode('utf-8')).hexdigest()[:24]
    case_part = _text(job.get('review_case_id')) or 'case'
    item_ids = _text_list(job.get('review_item_ids') or job.get('review_item_id'))
    item_part = item_ids[0] if item_ids else 'item'
    return f'review-{case_part}-{item_part}-{digest}'


def _download_url(record_uri: str) -> str:
    if record_uri.startswith(('http://', 'https://', '/video/', '/api/')):
        return record_uri
    if record_uri.startswith('/'):
        return f'/video/alert/record?path={quote(record_uri, safe="")}'
    return record_uri


def _as_bool(value) -> bool:
    if isinstance(value, bool):
        return value
    text = _text(value).lower()
    return text in ('1', 'true', 'yes', 'y', 'on')


def _positive_int(value):
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return parsed if parsed > 0 else None


def _nonnegative_int(value):
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return None
    return parsed if parsed >= 0 else None


def _max_input_bytes() -> int:
    return _positive_int(os.environ.get(_MAX_INPUT_BYTES_ENV)) or _DEFAULT_MAX_INPUT_BYTES


def _max_output_bytes() -> int:
    return _positive_int(os.environ.get(_MAX_OUTPUT_BYTES_ENV)) or _DEFAULT_MAX_OUTPUT_BYTES


def _text(value) -> str:
    if value is None:
        return ''
    return str(value).strip()


def _text_list(value) -> list:
    if value is None:
        return []
    if isinstance(value, (list, tuple, set)):
        return [_text(item) for item in value if _text(item)]
    text = _text(value)
    return [text] if text else []
