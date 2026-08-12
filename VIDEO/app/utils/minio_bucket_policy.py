"""MinIO bucket privacy helpers."""
import logging
import os
from urllib.parse import urlparse

from minio.error import S3Error

logger = logging.getLogger(__name__)

_MEDIA_BUCKET_NAMES = {
    'alert-images',
    'camera-screenshots',
    'record-archive',
    'record-space',
    'review-evidence',
    'snap-archive',
    'snap-space',
}
_MEDIA_BUCKET_PREFIXES = ('record-', 'record_', 'snap-', 'snap_', 'space-', 'space_')


def ensure_bucket_private(minio_client, bucket_name: str) -> None:
    """Remove any anonymous bucket policy; missing policy already means private."""
    if not minio_client.bucket_exists(bucket_name):
        return
    try:
        minio_client.delete_bucket_policy(bucket_name)
        logger.info('Removed anonymous MinIO bucket policy: %s', bucket_name)
    except S3Error as exc:
        if getattr(exc, 'code', '') == 'NoSuchBucketPolicy':
            return
        raise


def ensure_media_buckets_private(minio_client) -> list[str]:
    """Remove stale anonymous policies from every existing media bucket."""
    protected = {
        bucket.name
        for bucket in minio_client.list_buckets()
        if _is_media_bucket(bucket.name)
    }
    export_bucket = _configured_export_bucket()
    if export_bucket and minio_client.bucket_exists(export_bucket):
        protected.add(export_bucket)
    for bucket_name in sorted(protected):
        ensure_bucket_private(minio_client, bucket_name)
    return sorted(protected)


def _is_media_bucket(bucket_name: str) -> bool:
    normalized = str(bucket_name or '').strip().lower()
    return normalized in _MEDIA_BUCKET_NAMES or normalized.startswith(_MEDIA_BUCKET_PREFIXES)


def _configured_export_bucket() -> str | None:
    storage_uri = str(os.environ.get('YFEIEYE_RECORD_EXPORT_STORAGE_URI') or '').strip()
    if not storage_uri.lower().startswith('s3://'):
        return None
    return urlparse(storage_uri).netloc or None
