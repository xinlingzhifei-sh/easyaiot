"""Security contract tests for recording bucket policies."""
import importlib
import re
import sys
import types
import unittest
from pathlib import Path

import app.utils as app_utils

_missing = object()
_previous_minio = sys.modules.get('minio')
_previous_minio_error = sys.modules.get('minio.error')
_previous_policy = sys.modules.pop('app.utils.minio_bucket_policy', None)
_previous_policy_attribute = getattr(app_utils, 'minio_bucket_policy', _missing)
if _previous_minio_error is None:
    minio = types.ModuleType('minio')
    minio_error = types.ModuleType('minio.error')
    minio_error.S3Error = type('S3Error', (Exception,), {})
    minio.error = minio_error
    sys.modules['minio'] = minio
    sys.modules['minio.error'] = minio_error

minio_bucket_policy = importlib.import_module('app.utils.minio_bucket_policy')

if _previous_minio is None:
    sys.modules.pop('minio', None)
else:
    sys.modules['minio'] = _previous_minio
if _previous_minio_error is None:
    sys.modules.pop('minio.error', None)
else:
    sys.modules['minio.error'] = _previous_minio_error
if _previous_policy is None:
    sys.modules.pop('app.utils.minio_bucket_policy', None)
else:
    sys.modules['app.utils.minio_bucket_policy'] = _previous_policy
if _previous_policy_attribute is _missing:
    try:
        delattr(app_utils, 'minio_bucket_policy')
    except AttributeError:
        pass
else:
    app_utils.minio_bucket_policy = _previous_policy_attribute


class _FakeS3Error(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class _MinioClient:
    def __init__(self, *, exists=True, delete_error=None, buckets=None):
        self.exists = exists
        self.delete_error = delete_error
        self.deleted = []
        self.buckets = list(buckets or [])

    def bucket_exists(self, bucket_name):
        return self.exists

    def delete_bucket_policy(self, bucket_name):
        self.deleted.append(bucket_name)
        if self.delete_error:
            raise self.delete_error

    def list_buckets(self):
        return [types.SimpleNamespace(name=name) for name in self.buckets]


class TestPrivateBucketPolicy(unittest.TestCase):
    def test_production_code_cannot_restore_anonymous_bucket_policy(self):
        app_root = Path(__file__).resolve().parent / 'app'
        offenders = []
        for path in app_root.rglob('*.py'):
            source = path.read_text(encoding='utf-8')
            if 'set_bucket_policy(' in source or '_public_read_policy(' in source:
                offenders.append(str(path.relative_to(app_root.parent)).replace('\\', '/'))
        self.assertEqual([], offenders)

    def test_minio_bootstrap_scripts_keep_all_buckets_private(self):
        repo_root = Path(__file__).resolve().parent.parent
        upload_script = (repo_root / '.scripts' / 'docker' / 'upload_minio_data.sh').read_text(
            encoding='utf-8')

        self.assertNotIn('anonymous set public', upload_script)
        self.assertNotIn('client.set_bucket_policy(', upload_script)
        self.assertIn('anonymous set none', upload_script)
        self.assertIn('client.delete_bucket_policy(bucket_name)', upload_script)
        self.assertIsNone(
            re.search(r'^MINIO_(?:ACCESS_KEY|SECRET_KEY)="[^"$]+"$', upload_script, re.MULTILINE),
            'MinIO bootstrap credentials must never have literal fallbacks',
        )
        self.assertIn('MINIO_ROOT_USER', upload_script)
        self.assertIn('MINIO_ROOT_PASSWORD', upload_script)
        self.assertIn('os.environ.get("MINIO_ACCESS_KEY")', upload_script)
        self.assertIn('os.environ.get("MINIO_SECRET_KEY")', upload_script)

        middleware_env = (repo_root / '.scripts' / 'docker' / 'env.example').read_text(
            encoding='utf-8')
        self.assertRegex(middleware_env, r'(?m)^MINIO_ACCESS_KEY=\s*$')
        self.assertRegex(middleware_env, r'(?m)^MINIO_SECRET_KEY=\s*$')

    def test_srs_video_callback_host_is_configurable_for_private_bridge_binding(self):
        repo_root = Path(__file__).resolve().parent.parent
        installer = (repo_root / '.scripts' / 'docker' / 'install_middleware_linux.sh').read_text(
            encoding='utf-8')
        middleware_env = (repo_root / '.scripts' / 'docker' / 'env.example').read_text(
            encoding='utf-8')
        middleware_compose = (repo_root / '.scripts' / 'docker' / 'docker-compose.yml').read_text(
            encoding='utf-8')

        self.assertIn('resolve_video_callback_host', installer)
        self.assertGreaterEqual(installer.count('video_callback_host=$(resolve_video_callback_host)'), 2)
        self.assertNotIn('http://localhost:${video_port}/video/camera/callback', installer)
        self.assertIn('VIDEO_CALLBACK_HOST=172.17.0.1', middleware_env)
        self.assertRegex(middleware_env, r'(?m)^YFEIEYE_SRS_HOOK_TOKEN=\s*$')
        self.assertIn('?hook_token=${srs_hook_token}', installer)
        self.assertIn('chmod 600 "$srs_config_file"', installer)
        self.assertIn('resolve_middleware_data_root', installer)
        self.assertIn('local srs_config_target="${middleware_data_root}/srs_data/conf"', installer)
        self.assertIn(
            '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/srs_data/conf:/usr/local/srs/conf:rw',
            middleware_compose,
        )
        self.assertIn('- "/data:/data:rw"', middleware_compose)

    def test_public_bucket_policy_helpers_are_not_exposed(self):
        self.assertFalse(hasattr(
            minio_bucket_policy, 'build_public_read_write_policy'))
        self.assertFalse(hasattr(
            minio_bucket_policy, 'ensure_bucket_public_read_write_policy'))

    def test_existing_record_bucket_has_anonymous_policy_removed(self):
        client = _MinioClient()

        minio_bucket_policy.ensure_bucket_private(client, 'record-space')

        self.assertEqual(['record-space'], client.deleted)

    def test_missing_policy_is_already_private(self):
        previous = minio_bucket_policy.S3Error
        minio_bucket_policy.S3Error = _FakeS3Error
        try:
            client = _MinioClient(delete_error=_FakeS3Error('NoSuchBucketPolicy'))
            minio_bucket_policy.ensure_bucket_private(client, 'record-space')
        finally:
            minio_bucket_policy.S3Error = previous

        self.assertEqual(['record-space'], client.deleted)

    def test_policy_removal_failure_is_not_silently_ignored(self):
        previous = minio_bucket_policy.S3Error
        minio_bucket_policy.S3Error = _FakeS3Error
        try:
            client = _MinioClient(delete_error=_FakeS3Error('AccessDenied'))
            with self.assertRaisesRegex(_FakeS3Error, 'AccessDenied'):
                minio_bucket_policy.ensure_bucket_private(client, 'record-space')
        finally:
            minio_bucket_policy.S3Error = previous

    def test_startup_privacy_sweep_covers_existing_media_buckets_only(self):
        client = _MinioClient(buckets=[
            'record-space',
            'RECORD_ABC123',
            'SPACE_DEF456',
            'review-evidence',
            'model-space',
        ])

        secured = minio_bucket_policy.ensure_media_buckets_private(client)

        self.assertEqual(
            ['RECORD_ABC123', 'SPACE_DEF456', 'record-space', 'review-evidence'],
            secured,
        )
        self.assertNotIn('model-space', client.deleted)

    def test_video_compose_runs_privacy_sweep_before_service_start(self):
        compose = (Path(__file__).resolve().parent / 'docker-compose.yaml').read_text(
            encoding='utf-8')

        self.assertIn('python /app/enforce_private_media_buckets.py &&', compose)
        self.assertLess(
            compose.index('python /app/enforce_private_media_buckets.py &&'),
            compose.index('exec python /app/run.py'),
        )


if __name__ == '__main__':
    unittest.main()
