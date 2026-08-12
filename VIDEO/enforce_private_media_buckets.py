"""Fail-closed startup sweep for stale anonymous media bucket policies."""
import os

from minio import Minio

from app.utils.minio_bucket_policy import ensure_media_buckets_private
from app.utils.video_env import load_video_env


def main() -> int:
    load_video_env(override=True)
    endpoint = str(os.environ.get('MINIO_ENDPOINT') or '').strip()
    access_key = str(os.environ.get('MINIO_ACCESS_KEY') or '').strip()
    secret_key = str(os.environ.get('MINIO_SECRET_KEY') or '').strip()
    if not endpoint or not access_key or not secret_key:
        raise RuntimeError('MinIO credentials are required for the media bucket privacy sweep')
    client = Minio(
        endpoint,
        access_key=access_key,
        secret_key=secret_key,
        secure=str(os.environ.get('MINIO_SECURE') or 'false').lower() == 'true',
    )
    secured = ensure_media_buckets_private(client)
    print(f'Media bucket privacy sweep complete: {len(secured)} bucket(s)')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
