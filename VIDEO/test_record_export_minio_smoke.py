"""Opt-in real ffmpeg + MinIO smoke for review evidence exports."""
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
import time
import unittest


@unittest.skipUnless(
    os.environ.get('YFEIEYE_RECORD_EXPORT_REAL_MINIO_SMOKE') == 'true',
    'set YFEIEYE_RECORD_EXPORT_REAL_MINIO_SMOKE=true to use a real MinIO endpoint',
)
class TestRealRecordExportMinioSmoke(unittest.TestCase):
    def test_real_ffmpeg_export_is_readback_verified_from_minio(self):
        service = self._load_service()
        ffmpeg = shutil.which('ffmpeg')
        ffprobe = shutil.which('ffprobe')
        self.assertTrue(ffmpeg and ffprobe, 'real ffmpeg and ffprobe are required')
        self.assertTrue(os.environ.get('MINIO_ENDPOINT'))
        self.assertTrue(os.environ.get('MINIO_ACCESS_KEY'))
        self.assertTrue(os.environ.get('MINIO_SECRET_KEY'))

        bucket = f'yfeieye-export-smoke-{int(time.time())}'
        with tempfile.TemporaryDirectory(prefix='yfeieye-minio-smoke-') as work_dir:
            source_path = os.path.join(work_dir, 'source.mp4')
            subprocess.run([
                ffmpeg, '-hide_banner', '-loglevel', 'error', '-y',
                '-f', 'lavfi', '-i', 'testsrc=size=160x120:rate=10',
                '-t', '1', '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
                '-an', source_path,
            ], check=True, timeout=30)
            os.environ.update({
                'VIDEO_ENV': 'production',
                'YFEIEYE_LOCAL_MEDIA_ROOTS': work_dir,
                'YFEIEYE_RECORD_EXPORT_STORE_DIR': os.path.join(work_dir, 'local'),
                'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
                'YFEIEYE_RECORD_EXPORT_STORAGE_URI': f's3://{bucket}/evidence',
                'YFEIEYE_RECORD_EXPORT_HMAC_KEYS': json.dumps({
                    'activeKeyId': 'smoke-current',
                    'keys': {
                        'smoke-previous': 'previous-smoke-secret-at-least-32-bytes',
                        'smoke-current': 'current-smoke-secret-at-least-32-bytes',
                    },
                }),
                'YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID': 'smoke-current',
            })
            started = service.create_record_export({
                'review_case_id': 'real-minio-smoke',
                'review_item_id': 'real-minio-smoke-item',
                'tenant_id': '1',
                'camera_id': 'smoke-camera',
                'device_id': 'smoke-camera',
                'record_uri': source_path,
                'segment_start_time': '2026-07-10T10:00:00',
                'segment_end_time': '2026-07-10T10:00:01',
                'start_time': '2026-07-10T10:00:00',
                'end_time': '2026-07-10T10:00:01',
                'format': 'mp4',
            }, record_resolver=lambda payload: {
                'record_uri': payload['record_uri'],
                'camera_id': payload['camera_id'],
                'device_id': payload['device_id'],
                'source': 'real_minio_smoke',
            }, async_worker=True)
            ready = service.poll_record_export(started['export_id'])
            try:
                self.assertEqual('ready', ready['status'], ready.get('last_error'))
                self.assertEqual('verified', ready['storage_verification']['status'])
                self.assertGreaterEqual(ready['storage_verification']['artifact_count'], 5)
                self.assertTrue(ready['media_probe']['decodable'])
                manifest = service.get_record_export_manifest(started['export_id'])
                self.assertEqual('hmac-sha256', manifest['signature']['algorithm'])
                self.assertEqual('smoke-current', manifest['signature']['keyId'])
                self.assertIn('smoke-previous', (
                    manifest['signature'].get('keyRotation') or {}
                ).get('acceptedPreviousKeyIds', []))
                downloaded = service.download_record_export(started['export_id'])
                with open(downloaded['path'], 'rb') as handle:
                    content = handle.read()
                os.remove(downloaded['path'])
                self.assertEqual(ready['output_size_bytes'], len(content))
                self.assertEqual(
                    ready['file_hash'],
                    'sha256:' + hashlib.sha256(content).hexdigest(),
                )
                print(json.dumps({
                    'exportId': ready['export_id'],
                    'status': ready['status'],
                    'storageStatus': ready['storage_verification']['status'],
                    'artifactCount': ready['storage_verification']['artifact_count'],
                    'signatureKeyId': manifest['signature']['keyId'],
                    'fileHash': ready['file_hash'],
                    'outputSizeBytes': ready['output_size_bytes'],
                    'durationSeconds': ready['media_probe']['durationSeconds'],
                }, sort_keys=True))
            finally:
                job = service._get_export_job(started['export_id'])
                adapter = service._object_storage_adapter(job)
                service._delete_object_storage_artifacts(job)
                adapter.client.remove_bucket(adapter.bucket)

    @staticmethod
    def _load_service():
        path = os.environ.get('YFEIEYE_RECORD_EXPORT_SMOKE_SERVICE_PATH')
        if not path:
            from app.services import record_export_service
            return record_export_service
        spec = importlib.util.spec_from_file_location('record_export_service_smoke', path)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module


if __name__ == '__main__':
    unittest.main()
