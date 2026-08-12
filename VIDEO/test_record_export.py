"""Record evidence export contract tests."""
import hashlib
import inspect
import io
import importlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import time
import types
import unittest
from datetime import datetime, timedelta, timezone
from unittest import mock

from flask import Flask


def _trusted_record_resolver(payload):
    """Test-only metadata resolver for export-service contract fixtures."""
    record_uri = payload.get('record_uri') or payload.get('recordUri')
    return {
        'record_uri': record_uri,
        'record_uris': payload.get('record_uris') or payload.get('recordUris') or [],
        'record_segments': payload.get('record_segments') or payload.get('recordSegments') or [],
        'camera_id': payload.get('camera_id') or payload.get('cameraId')
                     or payload.get('device_id') or payload.get('deviceId'),
        'device_id': payload.get('device_id') or payload.get('deviceId')
                     or payload.get('camera_id') or payload.get('cameraId'),
        'source': 'test_metadata',
    } if record_uri else {}


def _provenance_worker_result(job, content=b'test-video-bytes', message='ffmpeg test worker'):
    command_hash = 'sha256:' + hashlib.sha256(
        f"ffmpeg:{job.get('export_id')}".encode('utf-8')
    ).hexdigest()
    record_uris = job.get('record_uris') or [job.get('record_uri')]
    raw_segments = job.get('record_segments') or [
        {'recordUri': uri}
        for uri in record_uris
        if uri
    ]
    segments = []
    for index, raw in enumerate(raw_segments):
        raw = dict(raw)
        record_uri = raw.get('recordUri') or raw.get('record_uri') or raw.get('uri')
        source_path = str(record_uri or '')
        if source_path.startswith('file://'):
            source_path = source_path[7:]
        if not os.path.isfile(source_path):
            source_dir = os.path.join(
                os.environ.get('YFEIEYE_RECORD_EXPORT_STORE_DIR') or tempfile.gettempdir(),
                str(job.get('export_id')),
            )
            os.makedirs(source_dir, exist_ok=True)
            source_path = os.path.join(source_dir, f'source-{index:03d}.fixture')
            with open(source_path, 'wb') as source_file:
                source_file.write(f'test-source:{record_uri}'.encode('utf-8'))
        with open(source_path, 'rb') as source_file:
            actual_source_hash = 'sha256:' + hashlib.sha256(source_file.read()).hexdigest()
        source_hash = raw.get('sourceHash') or raw.get('source_hash') or actual_source_hash
        clip_start = raw.get('clipStartTime') or raw.get('clip_start_time') or job.get('start_time')
        clip_end = raw.get('clipEndTime') or raw.get('clip_end_time') or job.get('end_time')
        segments.append({
            **raw,
            'recordUri': source_path,
            'originalRecordUri': record_uri,
            'sourceHash': source_hash,
            'ffmpegCommandHash': raw.get('ffmpegCommandHash') or command_hash,
            'stitchOrder': index,
            'clipParameters': {
                'clipStartTime': clip_start,
                'clipEndTime': clip_end,
                'offsetSeconds': 0.0,
                'durationSeconds': 1.0,
            },
        })
    return {
        'content': content,
        'message': message,
        'download_url': f'/video/record/export/{job["export_id"]}/download',
        'ffmpeg_command_hash': command_hash,
        'record_segments': segments,
    }


def _read_download_result(result):
    path = result.get('path')
    if path:
        with open(path, 'rb') as file_obj:
            return file_obj.read()
    stream = result.get('stream')
    if stream is None:
        return None
    try:
        return stream.read()
    finally:
        close = getattr(stream, 'close', None)
        if callable(close):
            close()


class TestRecordExportService(unittest.TestCase):
    def setUp(self):
        self._previous_export_store = os.environ.get('YFEIEYE_RECORD_EXPORT_STORE_DIR')
        self._previous_video_env = os.environ.get('VIDEO_ENV')
        self._previous_sha_fallback = os.environ.get(
            'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK')
        self._previous_local_media_roots = os.environ.get('YFEIEYE_LOCAL_MEDIA_ROOTS')
        self._isolated_export_store = tempfile.TemporaryDirectory()
        os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = self._isolated_export_store.name
        os.environ['VIDEO_ENV'] = 'test'
        os.environ['YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK'] = 'true'
        import app.services.record_export_service as export_service
        importlib.reload(export_service)

    def tearDown(self):
        if self._previous_export_store is None:
            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
        else:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = self._previous_export_store
        if self._previous_video_env is None:
            os.environ.pop('VIDEO_ENV', None)
        else:
            os.environ['VIDEO_ENV'] = self._previous_video_env
        if self._previous_sha_fallback is None:
            os.environ.pop('YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK', None)
        else:
            os.environ[
                'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK'
            ] = self._previous_sha_fallback
        if self._previous_local_media_roots is None:
            os.environ.pop('YFEIEYE_LOCAL_MEDIA_ROOTS', None)
        else:
            os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = self._previous_local_media_roots
        import app.services.record_export_service as export_service
        importlib.reload(export_service)
        self._isolated_export_store.cleanup()

    def test_create_record_export_rejects_unowned_absolute_record_uri(self):
        from app.services.record_export_service import create_record_export

        with tempfile.TemporaryDirectory() as work_dir:
            source_path = os.path.join(work_dir, 'private.mp4')
            with open(source_path, 'wb') as source_file:
                source_file.write(b'private-record')

            with self.assertRaisesRegex(ValueError, 'record source|record_uri'):
                create_record_export({
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': source_path,
                })

    def test_metadata_owned_file_uri_is_rejected_before_export_read(self):
        from app.services.record_export_service import validate_record_export_request

        with tempfile.TemporaryDirectory() as work_dir:
            source_path = os.path.join(work_dir, 'owned.mp4')
            with open(source_path, 'wb') as source_file:
                source_file.write(b'metadata-owned-record')
            with mock.patch.dict(os.environ, {
                'YFEIEYE_LOCAL_MEDIA_ROOTS': work_dir,
            }, clear=False):
                with self.assertRaisesRegex(ValueError, 'file URI'):
                    validate_record_export_request({
                        'camera_id': 'camera-01',
                        'device_id': 'camera-01',
                        'record_uri': 'file://' + source_path,
                    }, 'camera-01', _trusted_record_resolver)

    def test_create_record_export_reuses_local_record_uri_as_download_url(self):
        from app.services.record_export_service import create_record_export, get_record_export_manifest

        with tempfile.TemporaryDirectory() as media_root:
            record_uri = os.path.join(media_root, 'device-01', 'clip.flv')
            os.makedirs(os.path.dirname(record_uri), exist_ok=True)
            with open(record_uri, 'wb') as record_file:
                record_file.write(b'local-record')
            with mock.patch.dict(os.environ, {
                'YFEIEYE_LOCAL_MEDIA_ROOTS': media_root,
            }, clear=False):
                result = create_record_export({
                    'review_case_id': 3000,
                    'review_item_id': 1000,
                    'device_id': 'device-01',
                    'camera_id': 'device-01',
                    'source_alert_id': 'alert-export-001',
                    'start_time': '2026-06-30T10:10',
                    'end_time': '2026-06-30T10:12',
                    'record_uri': record_uri,
                    'format': 'mp4',
                }, record_resolver=_trusted_record_resolver, async_worker=True)

                self.assertEqual('pending', result['status'])
                self.assertEqual('existing_record_uri', result['source'])
                self.assertTrue(result['export_id'].startswith('review-3000-1000-'))
                self.assertEqual(record_uri, result['record_uri'])
                self.assertEqual(
                    '/video/record/export/' + result['export_id'] + '/manifest',
                    result['manifest_url'],
                )
                manifest = get_record_export_manifest(result['export_id'])
                self.assertEqual(2, manifest['manifestVersion'])
                self.assertEqual(result['export_id'], manifest['exportId'])

    def test_canonical_record_uri_is_accepted_only_for_matching_space_and_file_metadata(self):
        from app.services.record_export_service import validate_record_export_request

        class Query:
            def __init__(self, rows):
                self.rows = list(rows)

            def filter_by(self, **filters):
                return Query([
                    row for row in self.rows
                    if all(getattr(row, key, None) == value for key, value in filters.items())
                ])

            def first(self):
                return self.rows[0] if self.rows else None

            def get(self, identity):
                return next((row for row in self.rows if getattr(row, 'id', None) == identity), None)

        space = types.SimpleNamespace(id=7, tenant_id=7, device_id='camera-01')
        record = types.SimpleNamespace(
            tenant_id=7,
            space_id=7,
            device_id='camera-01',
            object_name='live/camera-01/clip.flv',
            url='/minio/record/clip.flv',
        )
        local_path = '/data/playbacks/live/camera-01/clip.flv'
        playback = types.SimpleNamespace(device_id='camera-01', file_path=local_path)
        empty_model = types.SimpleNamespace(query=Query([]))
        models = types.SimpleNamespace(
            Alert=empty_model,
            Playback=types.SimpleNamespace(query=Query([playback])),
            RecordSpace=types.SimpleNamespace(query=Query([space])),
            RecordFile=types.SimpleNamespace(query=Query([record])),
        )
        original_models = sys.modules.get('models')
        sys.modules['models'] = models
        record_uri = '/video/record/space/7/video/live/camera-01/clip.flv'
        try:
            validated = validate_record_export_request({
                'tenant_id': '7',
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'record_uri': record_uri,
            })
            proxy_uri = '/video/alert/record?path=%2Fdata%2Fplaybacks%2Flive%2Fcamera-01%2Fclip.flv'
            with self.assertRaisesRegex(ValueError, 'record_uri'):
                validate_record_export_request({
                    'tenant_id': '7',
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': proxy_uri,
                })
            with self.assertRaisesRegex(ValueError, 'record_uri'):
                validate_record_export_request({
                    'tenant_id': '7',
                    'camera_id': 'camera-02',
                    'device_id': 'camera-02',
                    'record_uri': record_uri,
                })
        finally:
            if original_models is None:
                sys.modules.pop('models', None)
            else:
                sys.modules['models'] = original_models

        self.assertEqual(record_uri, validated['record_uri'])

    def test_canonical_record_uri_rejects_same_camera_from_other_tenant(self):
        from app.services.record_export_service import validate_record_export_request

        class Query:
            def __init__(self, rows):
                self.rows = list(rows)

            def filter_by(self, **filters):
                return Query([
                    row for row in self.rows
                    if all(getattr(row, key, None) == value
                           for key, value in filters.items())
                ])

            def first(self):
                return self.rows[0] if self.rows else None

            def get(self, identity):
                return next((
                    row for row in self.rows
                    if getattr(row, 'id', None) == identity
                ), None)

        space = types.SimpleNamespace(
            id=7, tenant_id=8, device_id='camera-01')
        record = types.SimpleNamespace(
            tenant_id=8,
            space_id=7,
            device_id='camera-01',
            object_name='live/camera-01/clip.flv',
            url='/minio/record/clip.flv',
        )
        empty_model = types.SimpleNamespace(query=Query([]))
        original_models = sys.modules.get('models')
        sys.modules['models'] = types.SimpleNamespace(
            Alert=empty_model,
            Playback=empty_model,
            RecordSpace=types.SimpleNamespace(query=Query([space])),
            RecordFile=types.SimpleNamespace(query=Query([record])),
        )
        try:
            with self.assertRaisesRegex(ValueError, 'record_uri'):
                validate_record_export_request({
                    'tenant_id': '7',
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': (
                        '/video/record/space/7/video/'
                        'live/camera-01/clip.flv'),
                })
        finally:
            if original_models is None:
                sys.modules.pop('models', None)
            else:
                sys.modules['models'] = original_models

    def test_create_record_export_resolves_record_uri_from_time_window(self):
        from app.services.record_export_service import create_record_export

        resolver_calls = []

        def fake_resolver(payload):
            resolver_calls.append(payload)
            return {
                'record_uri': '/video/record/space/7/video/live/device-01/clip.flv',
                'source': 'record_window',
                'space_id': 7,
                'object_name': 'live/device-01/clip.flv',
                'segment_start_time': '2026-06-30T10:10:10',
                'segment_end_time': '2026-06-30T10:11:10',
            }

        result = create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'device_id': 'device-01',
            'start_time': '2026-06-30T10:10:30',
            'end_time': '2026-06-30T10:11:30',
        }, record_resolver=fake_resolver, async_worker=True)

        self.assertEqual(1, len(resolver_calls))
        self.assertEqual('pending', result['status'])
        self.assertEqual('record_window', result['source'])
        self.assertEqual('/video/record/space/7/video/live/device-01/clip.flv', result['record_uri'])
        self.assertEqual(7, result['space_id'])
        self.assertEqual('live/device-01/clip.flv', result['object_name'])

    def test_select_record_for_window_prefers_nearest_overlapping_segment(self):
        from app.services.record_export_service import _select_record_for_window

        early = types.SimpleNamespace(
            url='/video/record/early.mp4',
            event_time=datetime(2026, 6, 30, 10, 0, 0),
            duration=60,
        )
        matched = types.SimpleNamespace(
            url='/video/record/matched.mp4',
            event_time=datetime(2026, 6, 30, 10, 4, 30),
            duration=180,
        )

        result = _select_record_for_window(
            [early, matched],
            datetime(2026, 6, 30, 10, 5, 0),
            datetime(2026, 6, 30, 10, 6, 0),
        )

        self.assertIs(matched, result)

    def test_async_record_export_worker_moves_job_to_ready_with_hash_and_download(self):
        from app.services.record_export_service import create_record_export, poll_record_export

        runner_calls = []

        def fake_worker(job):
            runner_calls.append(job)
            return _provenance_worker_result(
                job, b'clipped-video-bytes', 'ffmpeg clipped and stitched evidence')

        started = create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'source_alert_id': 'alert-export-async',
            'start_time': '2026-06-30T10:10',
            'end_time': '2026-06-30T10:12',
            'record_uri': '/video/record/space/7/video/live/device-01/clip.flv',
            'format': 'mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True, worker_runner=fake_worker)

        self.assertEqual('pending', started['status'])
        self.assertIn('export_id', started)
        self.assertEqual('/video/record/export/' + started['export_id'], started['status_url'])
        self.assertEqual('/video/record/export/' + started['export_id'] + '/manifest', started['manifest_url'])

        ready = poll_record_export(started['export_id'])

        self.assertEqual(1, len(runner_calls))
        self.assertEqual('ready', ready['status'])
        self.assertTrue(ready['file_hash'].startswith('sha256:'))
        self.assertEqual('/video/record/export/' + started['export_id'] + '/download', ready['download_url'])
        self.assertEqual('/video/record/export/' + started['export_id'] + '/manifest', ready['manifest_url'])
        self.assertEqual('ffmpeg clipped and stitched evidence', ready['message'])

    def test_persistent_queue_processes_pending_job_after_service_restart(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'source_alert_id': 'alert-export-restart-queue',
            'start_time': '2026-06-30T10:10:00',
            'end_time': '2026-06-30T10:11:00',
            'record_uri': '/video/record/space/7/video/live/device-01/restart-queue.mp4',
            'format': 'mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        self.assertEqual('pending', started['status'])

        export_service = importlib.reload(export_service)
        original_worker = export_service._default_export_worker
        export_service._default_export_worker = lambda job: _provenance_worker_result(
            job, b'restarted-persistent-queue-video')
        try:
            processed = export_service.process_record_export_queue(limit=1)
        finally:
            export_service._default_export_worker = original_worker

        self.assertEqual(1, len(processed))
        self.assertEqual(started['export_id'], processed[0]['export_id'])
        self.assertEqual('ready', processed[0]['status'])
        self.assertEqual(
            b'restarted-persistent-queue-video',
            _read_download_result(
                export_service.download_record_export(started['export_id'])),
        )

    def test_async_worker_without_real_media_provenance_never_marks_job_ready(self):
        from app.services.record_export_service import create_record_export, poll_record_export

        started = create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'source_alert_id': 'alert-export-no-provenance',
            'start_time': '2026-06-30T10:10:00',
            'end_time': '2026-06-30T10:11:00',
            'record_uri': '/video/record/space/7/video/live/device-01/clip.flv',
            'format': 'mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True,
           worker_runner=lambda job: {'content': b'not-a-proven-real-export'})

        failed = poll_record_export(started['export_id'])

        self.assertEqual('failed', failed['status'])
        self.assertIn('provenance', failed['last_error'])
        self.assertIsNone(failed['download_url'])
        self.assertIsNone(failed['file_hash'])

    def test_missing_ffmpeg_never_falls_back_to_text_content(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            original_which = export_service.shutil.which
            export_service.shutil.which = lambda executable: None
            try:
                started = export_service.create_record_export({
                    'review_case_id': 3000,
                    'review_item_id': 1000,
                    'device_id': 'device-01',
                    'camera_id': 'device-01',
                    'record_uri': '/video/record/space/7/video/live/device-01/missing.mp4',
                    'start_time': '2026-06-30T10:10:00',
                    'end_time': '2026-06-30T10:11:00',
                    'format': 'mp4',
                }, record_resolver=_trusted_record_resolver, async_worker=True)

                failed = export_service.poll_record_export(started['export_id'])

                self.assertEqual('failed', failed['status'])
                self.assertIn('ffmpeg', failed['last_error'])
                self.assertFalse(os.path.exists(os.path.join(
                    store_dir, started['export_id'], 'content.bin')))
            finally:
                export_service.shutil.which = original_which
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                importlib.reload(export_service)

    def test_persistent_claim_prevents_duplicate_worker_execution(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            calls = []

            def worker(job):
                calls.append(job['export_id'])
                return _provenance_worker_result(job, b'claimed-video')

            try:
                started = export_service.create_record_export({
                    'review_case_id': 3000,
                    'review_item_id': 1000,
                    'device_id': 'device-01',
                    'camera_id': 'device-01',
                    'record_uri': '/video/record/space/7/video/live/device-01/claim.mp4',
                    'start_time': '2026-06-30T10:10:00',
                    'end_time': '2026-06-30T10:11:00',
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=worker)
                token = export_service._acquire_export_claim(started['export_id'])

                blocked = export_service.poll_record_export(started['export_id'])

                self.assertEqual('pending', blocked['status'])
                self.assertEqual([], calls)
                export_service._release_export_claim(started['export_id'], token)
                ready = export_service.poll_record_export(started['export_id'])
                self.assertEqual('ready', ready['status'])
                self.assertEqual([started['export_id']], calls)
            finally:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                importlib.reload(export_service)

    def test_duplicate_create_is_idempotent_and_does_not_replace_pending_worker(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            calls = []
            payload = {
                'review_case_id': 3000,
                'review_item_id': 1000,
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'record_uri': '/video/record/space/7/video/live/device-01/idempotent.mp4',
                'start_time': '2026-06-30T10:10:00',
                'end_time': '2026-06-30T10:11:00',
            }

            def first_worker(job):
                calls.append('first')
                return _provenance_worker_result(job, b'idempotent-video')

            def replacement_worker(job):
                calls.append('replacement')
                return _provenance_worker_result(job, b'wrong-video')

            try:
                first = export_service.create_record_export(
                    payload, record_resolver=_trusted_record_resolver,
                    async_worker=True, worker_runner=first_worker)
                duplicate = export_service.create_record_export(
                    payload, record_resolver=_trusted_record_resolver,
                    async_worker=True, worker_runner=replacement_worker)
                ready = export_service.poll_record_export(first['export_id'])

                self.assertEqual(first['export_id'], duplicate['export_id'])
                self.assertEqual('pending', duplicate['status'])
                self.assertEqual('ready', ready['status'])
                self.assertEqual(['first'], calls)
            finally:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                importlib.reload(export_service)

    def test_export_identity_includes_tenant_window_format_and_all_segments(self):
        from app.services.record_export_service import create_record_export

        base = {
            'review_case_id': 3000,
            'review_item_id': 1000,
            'tenant_id': 7,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'record_uri': '/video/record/space/7/video/live/device-01/identity.mp4',
            'start_time': '2026-06-30T10:10:00',
            'end_time': '2026-06-30T10:11:00',
            'format': 'mp4',
        }

        first = create_record_export(
            base, record_resolver=_trusted_record_resolver, async_worker=True)
        changed_window = create_record_export(
            {**base, 'end_time': '2026-06-30T10:12:00'},
            record_resolver=_trusted_record_resolver, async_worker=True)
        changed_tenant = create_record_export(
            {**base, 'tenant_id': 8},
            record_resolver=_trusted_record_resolver, async_worker=True)

        self.assertEqual(3, len({
            first['export_id'], changed_window['export_id'], changed_tenant['export_id'],
        }))

    def test_expired_cleanup_removes_media_and_marks_persisted_job_expired(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            try:
                started = export_service.create_record_export({
                    'review_case_id': 3000,
                    'review_item_id': 1000,
                    'device_id': 'device-01',
                    'camera_id': 'device-01',
                    'record_uri': '/video/record/space/7/video/live/device-01/expired.mp4',
                    'start_time': '2026-06-30T10:10:00',
                    'end_time': '2026-06-30T10:11:00',
                    'expires_at': '2026-07-01T00:00:00Z',
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(job, b'expired-video'))
                expiring_job = export_service._get_export_job(started['export_id'])
                expiring_job['expires_at'] = '2026-07-01T00:00:00Z'
                export_service._persist_job(expiring_job)
                claim = export_service._acquire_export_claim(started['export_id'])
                blocked_cleanup = export_service.cleanup_expired_record_exports(
                    datetime(2026, 7, 2, 0, 0, 0))
                self.assertEqual([], blocked_cleanup)
                self.assertEqual(
                    'pending',
                    export_service.poll_record_export(started['export_id'])['status'],
                )
                export_service._release_export_claim(started['export_id'], claim)
                ready = export_service.poll_record_export(started['export_id'])
                self.assertEqual('ready', ready['status'])

                expired = export_service.cleanup_expired_record_exports(
                    datetime(2026, 7, 2, 0, 0, 0))
                reloaded = export_service.poll_record_export(started['export_id'])

                self.assertEqual([started['export_id']], expired)
                self.assertEqual('expired', reloaded['status'])
                self.assertIsNone(reloaded['download_url'])
                self.assertFalse(os.path.exists(os.path.join(
                    store_dir, started['export_id'], 'content.bin')))
            finally:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                importlib.reload(export_service)

    def test_failed_async_record_export_can_retry_and_records_download_audit(self):
        from app.services.record_export_service import (
            create_record_export,
            download_record_export,
            get_record_export_audit,
            poll_record_export,
            retry_record_export,
        )

        previous_backoff = os.environ.get('YFEIEYE_RECORD_EXPORT_RETRY_BACKOFF_SECONDS')
        os.environ['YFEIEYE_RECORD_EXPORT_RETRY_BACKOFF_SECONDS'] = '0'

        def restore_backoff():
            if previous_backoff is None:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_RETRY_BACKOFF_SECONDS', None)
            else:
                os.environ['YFEIEYE_RECORD_EXPORT_RETRY_BACKOFF_SECONDS'] = previous_backoff

        self.addCleanup(restore_backoff)

        calls = []

        def flaky_worker(job):
            calls.append(job)
            if len(calls) == 1:
                raise RuntimeError('ffmpeg segment missing')
            return _provenance_worker_result(job, b'recovered-video-bytes', 'retry clipped evidence')

        started = create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'source_alert_id': 'alert-export-retry',
            'start_time': '2026-06-30T10:10',
            'end_time': '2026-06-30T10:12',
            'record_uri': '/video/record/space/7/video/live/device-01/retry.flv',
            'format': 'mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True, worker_runner=flaky_worker)

        failed = poll_record_export(started['export_id'])
        self.assertEqual('failed', failed['status'])
        self.assertEqual(1, failed['retry_count'])
        self.assertEqual('ffmpeg segment missing', failed['last_error'])

        retrying = retry_record_export(started['export_id'])
        ready = poll_record_export(retrying['export_id'])
        downloaded = download_record_export(started['export_id'], operator_user_id='9004', reason='handoff')
        audit = get_record_export_audit(started['export_id'])

        self.assertEqual('pending', retrying['status'])
        self.assertIsNone(retrying['last_error'])
        self.assertIsNone(retrying['next_attempt_at'])
        self.assertEqual('ready', ready['status'])
        self.assertEqual(2, ready['retry_count'])
        self.assertTrue(ready['file_hash'].startswith('sha256:'))
        self.assertEqual(b'recovered-video-bytes', _read_download_result(downloaded))
        self.assertTrue(any(entry['action'] == 'downloaded'
                            and entry['operator_user_id'] == '9004'
                            and entry['reason'] == 'handoff'
                            for entry in audit))

    def test_pending_retry_time_uses_shanghai_clock_in_utc_container(self):
        import app.services.record_export_service as export_service

        class FixedDatetime(datetime):
            current = datetime(2026, 7, 15, 11, 10, 0, tzinfo=timezone.utc)

            @classmethod
            def now(cls, tz=None):
                if tz is None:
                    return cls.current.replace(tzinfo=None)
                return cls.current.astimezone(tz)

        started = export_service.create_record_export({
            'review_case_id': 3001,
            'review_item_id': 1001,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'record_uri': '/video/record/space/7/video/live/device-01/timezone.flv',
        }, record_resolver=_trusted_record_resolver, async_worker=True,
           worker_runner=lambda job: _provenance_worker_result(job, b'timezone-retry'))
        pending = export_service._get_export_job(started['export_id'])
        pending['next_attempt_at'] = (
            FixedDatetime.current - timedelta(seconds=1)).isoformat()
        export_service._persist_job(pending)

        with mock.patch.object(export_service, 'datetime', FixedDatetime):
            ready = export_service.poll_record_export(started['export_id'])

        self.assertEqual('ready', ready['status'])

    def test_retry_source_specs_restore_server_managed_original_record_uri(self):
        import app.services.record_export_service as export_service

        original_uri = (
            '/video/record/space/7/video/tenants/7/camera-01/2026/07/15/input.flv')
        specs = export_service._record_source_specs({
            'record_segments': [{
                'recordUri': '/data/yfeieye-record-exports/job/source-000.flv',
                'originalRecordUri': original_uri,
                'spaceId': '7',
                'objectName': 'tenants/7/camera-01/2026/07/15/input.flv',
                'segmentStartTime': '2026-07-15T18:43:00',
                'segmentEndTime': '2026-07-15T18:43:31',
                'clipStartTime': '2026-07-15T18:43:03',
                'clipEndTime': '2026-07-15T18:43:31',
            }],
        })

        self.assertEqual(original_uri, specs[0]['record_uri'])
        self.assertEqual('7', specs[0]['space_id'])
        self.assertEqual(
            'tenants/7/camera-01/2026/07/15/input.flv', specs[0]['object_name'])

    def test_expiration_scheduler_uses_shanghai_clock_in_utc_container(self):
        import app.services.record_export_service as export_service

        class FixedDatetime(datetime):
            current = datetime(2026, 7, 15, 11, 10, 0, tzinfo=timezone.utc)

            @classmethod
            def now(cls, tz=None):
                if tz is None:
                    return cls.current.replace(tzinfo=None)
                return cls.current.astimezone(tz)

        started = export_service.create_record_export({
            'review_case_id': 3002,
            'review_item_id': 1002,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'record_uri': '/video/record/space/7/video/live/device-01/expiry-timezone.flv',
            'expires_at': (FixedDatetime.current - timedelta(seconds=1)).isoformat(),
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        expiring = export_service._get_export_job(started['export_id'])
        expiring['expires_at'] = (
            FixedDatetime.current - timedelta(seconds=1)).isoformat()
        export_service._persist_job(expiring)

        with mock.patch.object(export_service, 'datetime', FixedDatetime):
            expired = export_service.cleanup_expired_record_exports()

        self.assertEqual([started['export_id']], expired)
        self.assertEqual('expired', export_service._get_export_job(started['export_id'])['status'])

    def test_async_record_export_persists_manifest_content_and_audit_across_restart(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            def fake_worker(job):
                return _provenance_worker_result(
                    job, b'persistent-video-bytes', 'persisted evidence package')

            started = export_service.create_record_export({
                'review_case_id': 3000,
                'review_item_ids': [1000, 1001],
                'event_ids': [7500],
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'source_alert_id': 'alert-export-persist',
                'start_time': '2026-06-30T10:10',
                'end_time': '2026-06-30T10:12',
                'record_uris': [
                    '/video/record/space/7/video/live/device-01/a.mp4',
                    '/video/record/space/7/video/live/device-01/b.mp4',
                ],
                'record_uri': '/video/record/space/7/video/live/device-01/a.mp4',
                'snapshot_uris': ['snap-a.jpg', 'snap-b.jpg'],
                'operator_user_id': 9004,
                'format': 'mp4',
            }, record_resolver=_trusted_record_resolver, async_worker=True, worker_runner=fake_worker)
            ready = export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])

            self.assertEqual('ready', ready['status'])
            self.assertEqual('3000', manifest['reviewCaseId'])
            self.assertEqual(['1000', '1001'], manifest['reviewItemIds'])
            self.assertEqual(['7500'], manifest['eventIds'])
            self.assertEqual(['snap-a.jpg', 'snap-b.jpg'], manifest['snapshots'])
            self.assertEqual(2, len(manifest['recordSegments']))
            self.assertTrue(manifest['fileHash'].startswith('sha256:'))

            export_service = importlib.reload(export_service)
            reloaded = export_service.poll_record_export(started['export_id'])
            downloaded = export_service.download_record_export(
                started['export_id'],
                operator_user_id='9005',
                reason='restart verification',
            )
            audit = export_service.get_record_export_audit(started['export_id'])
            manifest_after_download = export_service.get_record_export_manifest(started['export_id'])

            self.assertEqual('ready', reloaded['status'])
            self.assertEqual(b'persistent-video-bytes', _read_download_result(downloaded))
            self.assertTrue(any(entry['action'] == 'downloaded'
                                and entry['operator_user_id'] == '9005'
                                for entry in audit))
            self.assertTrue(any(record['operatorUserId'] == '9005'
                                and record['reason'] == 'restart verification'
                                for record in manifest_after_download['downloadRecords']))

            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            importlib.reload(export_service)

    def test_record_export_manifest_is_tamper_evident_and_links_approval_downloads(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            def fake_worker(job):
                return _provenance_worker_result(
                    job, b'audit-chain-video', 'immutable evidence package')

            started = export_service.create_record_export({
                'review_case_id': 3000,
                'review_item_ids': [1000, 1001],
                'event_ids': [7500, 7501],
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'source_alert_id': 'alert-export-chain',
                'start_time': '2026-06-30T10:10',
                'end_time': '2026-06-30T10:12',
                'record_uri': '/video/record/space/7/video/live/device-01/a.mp4',
                'snapshot_uris': ['snap-a.jpg'],
                'operator_user_id': 9004,
                'approved_by': 9008,
                'approval_note': 'supervisor approved',
                'expires_at': '2099-07-07T10:12:00Z',
            }, record_resolver=_trusted_record_resolver, async_worker=True, worker_runner=fake_worker)
            ready = export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])

            self.assertEqual(ready['file_hash'], manifest['packageChecksum'])
            self.assertTrue(manifest['manifestHash'].startswith('sha256:'))
            self.assertEqual('9004', manifest['generatedBy'])
            self.assertEqual('9008', manifest['approval']['approvedBy'])
            self.assertEqual('supervisor approved', manifest['approval']['approvalNote'])
            created_at = export_service._parse_time(ready['created_at'])
            expires_at = export_service._parse_time(manifest['expiresAt'])
            self.assertEqual(7, (expires_at - created_at).days)
            self.assertEqual(['7500', '7501'], [ref['eventId'] for ref in manifest['eventReferences']])
            self.assertTrue(any(file['name'] == 'content.bin'
                                and file['hash'] == ready['file_hash']
                                and file['sizeBytes'] == len(b'audit-chain-video')
                                for file in manifest['files']))
            self.assertTrue(all(entry['entryHash'].startswith('sha256:')
                                and entry['previousHash']
                                for entry in manifest['audit']))
            self.assertEqual('GENESIS', manifest['audit'][0]['previousHash'])
            self.assertEqual(manifest['audit'][-1]['entryHash'], manifest['immutableAudit']['headHash'])

            export_service.download_record_export(
                started['export_id'],
                operator_user_id='9010',
                reason='court package download',
            )
            manifest_after_download = export_service.get_record_export_manifest(started['export_id'])

            self.assertNotEqual(
                manifest['immutableAudit']['headHash'],
                manifest_after_download['immutableAudit']['headHash'],
            )
            self.assertEqual(
                manifest['immutableAudit']['headHash'],
                manifest_after_download['audit'][-1]['previousHash'],
            )
            self.assertTrue(any(record['operatorUserId'] == '9010'
                                and record['reason'] == 'court package download'
                                for record in manifest_after_download['downloadRecords']))

            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            importlib.reload(export_service)

    def test_record_export_manifest_tracks_storage_lifecycle_for_persisted_artifacts(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            os.environ['YFEIEYE_RECORD_EXPORT_RETENTION_DAYS'] = '12'
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            def fake_worker(job):
                return _provenance_worker_result(
                    job, b'lifecycle-video-bytes', 'persisted lifecycle evidence package')

            started = export_service.create_record_export({
                'review_case_id': 3001,
                'review_item_id': 1002,
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'source_alert_id': 'alert-export-lifecycle',
                'start_time': '2026-07-08T10:10',
                'end_time': '2026-07-08T10:12',
                'record_uri': '/video/record/space/7/video/live/device-01/lifecycle.mp4',
                'expires_at': '2026-07-20T00:00:00Z',
                'retention_days': 12,
            }, record_resolver=_trusted_record_resolver, async_worker=True, worker_runner=fake_worker)
            ready = export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])

            lifecycle = manifest['storageLifecycle']
            self.assertEqual('local_filesystem', lifecycle['storageType'])
            self.assertEqual(store_dir, lifecycle['storeRoot'])
            created_at = export_service._parse_time(ready['created_at'])
            expires_at = export_service._parse_time(lifecycle['expiresAt'])
            self.assertEqual(12, (expires_at - created_at).days)
            self.assertEqual('retained', lifecycle['status'])
            self.assertEqual(
                started['export_id'] + '/content.bin',
                lifecycle['artifactKeys']['exportPackage'],
            )
            content_file = next(file for file in manifest['files'] if file['role'] == 'export_package')
            self.assertEqual(ready['file_hash'], content_file['hash'])
            self.assertTrue(os.path.exists(content_file['path']))
            self.assertEqual('export_package', content_file['storage']['artifactRole'])
            self.assertEqual('local_filesystem', content_file['storage']['storageType'])
            self.assertEqual(started['export_id'] + '/content.bin', content_file['storage']['objectKey'])
            self.assertEqual(lifecycle['expiresAt'], content_file['storage']['expiresAt'])
            self.assertEqual('retained', content_file['storage']['lifecycleStatus'])

            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            os.environ.pop('YFEIEYE_RECORD_EXPORT_RETENTION_DAYS', None)
            importlib.reload(export_service)

    def test_record_export_defaults_to_configured_retention_policy(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            os.environ['YFEIEYE_RECORD_EXPORT_RETENTION_DAYS'] = '30'
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            try:
                started = export_service.create_record_export({
                    'review_case_id': 3002,
                    'review_item_id': 1003,
                    'device_id': 'device-01',
                    'record_uri': '/video/record/space/7/video/live/device-01/default-retention.mp4',
                }, record_resolver=_trusted_record_resolver,
                   async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(
                       job, b'default-retention-video'))
                export_service.poll_record_export(started['export_id'])
                manifest = export_service.get_record_export_manifest(started['export_id'])

                created_at = export_service._parse_time(manifest['generatedAt'])
                expires_at = export_service._parse_time(manifest['storageLifecycle']['expiresAt'])
                self.assertEqual('30', manifest['storageLifecycle']['retentionDays'])
                self.assertEqual(30, (expires_at - created_at).days)
                self.assertEqual(expires_at.isoformat(), manifest['storageLifecycle']['cleanupPolicy']['deleteAfter'])
            finally:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                os.environ.pop('YFEIEYE_RECORD_EXPORT_RETENTION_DAYS', None)
                importlib.reload(export_service)

    def test_manifest_verifier_cli_validates_canonical_hash_signature_and_tampering(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            started = export_service.create_record_export({
                'review_case_id': 3000,
                'review_item_id': 1000,
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'source_alert_id': 'alert-export-verifier',
                'start_time': '2026-06-30T10:10',
                'end_time': '2026-06-30T10:12',
                'record_uri': '/video/record/space/7/video/live/device-01/clip.flv',
                'format': 'mp4',
                'operator_user_id': '9004',
                'approved_by': '9008',
            }, record_resolver=_trusted_record_resolver,
               async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(job, b'verifier-content'))
            export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])
            manifest_path = os.path.join(store_dir, 'manifest.json')
            with open(manifest_path, 'w', encoding='utf-8') as file_obj:
                json.dump(manifest, file_obj, ensure_ascii=False, sort_keys=True)

            from app.services.record_export_manifest_verifier import main, verify_manifest_file

            report = verify_manifest_file(manifest_path)
            self.assertEqual(2, manifest['manifestVersion'])
            self.assertEqual('yfeieye.record-export.manifest.v2', manifest['schema'])
            self.assertTrue(report['valid'])
            self.assertEqual('yfeieye.record-export.manifest.v2', report['manifestSchema'])
            self.assertTrue(report['canonicalHash'].startswith('sha256:'))
            self.assertEqual(manifest['manifestHash'], report['canonicalHash'])
            self.assertTrue(report['signatureValid'])
            self.assertEqual(0, main(['--manifest', manifest_path]))

            tampered = dict(manifest)
            tampered['reviewCaseId'] = 'tampered-case'
            with open(manifest_path, 'w', encoding='utf-8') as file_obj:
                json.dump(tampered, file_obj, ensure_ascii=False, sort_keys=True)
            tampered_report = verify_manifest_file(manifest_path)
            self.assertFalse(tampered_report['valid'])
            self.assertIn('manifest_hash_mismatch', tampered_report['violations'])

            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            importlib.reload(export_service)

    def test_manifest_read_upgrades_legacy_mutable_audit_file_reference(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3010,
            'review_item_id': 1010,
            'device_id': 'device-01',
            'camera_id': 'device-01',
            'record_uri': '/video/record/space/7/video/live/device-01/legacy.flv',
        }, record_resolver=_trusted_record_resolver,
           async_worker=True,
           worker_runner=lambda job: _provenance_worker_result(
               job, b'legacy-manifest-content'))
        export_service.poll_record_export(started['export_id'])
        manifest = export_service.get_record_export_manifest(started['export_id'])
        audit_path = export_service._audit_path(started['export_id'])
        manifest['files'].append({
            'name': 'audit.json',
            'role': 'audit_log',
            'hash': export_service._sha256_file(audit_path),
            'sizeBytes': os.path.getsize(audit_path),
            'path': audit_path,
        })
        signing_key = export_service.validate_record_export_signing_configuration()
        manifest['manifestHash'] = export_service._expected_manifest_hash(manifest)
        manifest['signature']['value'] = export_service._expected_manifest_signature(
            manifest, manifest['manifestHash'], signing_key.get('secret') or '')
        export_service._write_json(
            export_service._manifest_path(started['export_id']), manifest)

        upgraded = export_service.get_record_export_manifest(started['export_id'])

        self.assertNotIn('audit.json', [item['name'] for item in upgraded['files']])
        self.assertNotEqual(manifest['manifestHash'], upgraded['manifestHash'])
        export_service._validate_manifest_integrity(upgraded)

    def _assert_legacy_object_manifest_upgrade_recovers(self, failed_name):
        import app.services.record_export_service as export_service

        class FaultInjectingObjectStorage:
            def __init__(self):
                self.objects = {}
                self.fail_next_key = None
                self.failed_keys = []

            def put_file(self, object_key, path, content_type=None):
                with open(path, 'rb') as file_obj:
                    self.objects[object_key] = file_obj.read()
                if object_key == self.fail_next_key:
                    self.fail_next_key = None
                    self.failed_keys.append(object_key)
                    raise RuntimeError(f'injected object write failure: {object_key}')

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                return io.BytesIO(self.objects[object_key])

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://review-evidence/' + object_key

        adapter = FaultInjectingObjectStorage()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            with mock.patch.dict(os.environ, {
                'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 's3',
                'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/cases',
            }, clear=False):
                started = export_service.create_record_export({
                    'review_case_id': 'legacy-object-upgrade',
                    'review_item_id': 'legacy-item',
                    'device_id': 'camera-01',
                    'camera_id': 'camera-01',
                    'tenant_id': '7',
                    'record_uri': (
                        '/video/record/space/7/video/live/camera-01/legacy.mp4'),
                    'storage_type': 's3',
                    'storage_root': 's3://review-evidence/cases',
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(
                       job, b'legacy-object-manifest'))
                ready = export_service.poll_record_export(started['export_id'])
                self.assertEqual('ready', ready['status'])

                export_id = started['export_id']
                job = export_service._get_export_job(export_id)
                manifest_path = export_service._manifest_path(export_id)
                audit_path = export_service._audit_path(export_id)
                legacy_manifest = export_service.get_record_export_manifest(export_id)
                legacy_manifest['files'].append({
                    'name': 'audit.json',
                    'role': 'audit_log',
                    'hash': export_service._sha256_file(audit_path),
                    'sizeBytes': os.path.getsize(audit_path),
                    'path': audit_path,
                })
                signing_key = export_service.validate_record_export_signing_configuration()
                legacy_manifest['manifestHash'] = (
                    export_service._expected_manifest_hash(legacy_manifest))
                legacy_manifest['signature']['value'] = (
                    export_service._expected_manifest_signature(
                        legacy_manifest,
                        legacy_manifest['manifestHash'],
                        signing_key.get('secret') or ''))
                export_service._write_json(manifest_path, legacy_manifest)
                export_service._sync_object_storage_artifacts(
                    job, metadata_only=True)
                legacy_marker = export_service._publish_object_commit_marker(job)

                manifest_key = export_service._storage_object_key(job, 'manifest.json')
                commit_key = export_service._storage_object_key(job, 'commit.json')
                failed_key = export_service._storage_object_key(job, failed_name)
                adapter.fail_next_key = failed_key

                with self.assertRaisesRegex(RuntimeError, 'injected object write failure'):
                    export_service.get_record_export_manifest(export_id)

                self.assertEqual([failed_key], adapter.failed_keys)
                restored_local = export_service._read_json_strict(
                    manifest_path, 'record export manifest')
                restored_remote = json.loads(
                    adapter.objects[manifest_key].decode('utf-8'))
                restored_marker = json.loads(
                    adapter.objects[commit_key].decode('utf-8'))
                marker_fields = (
                    'version', 'exportId', 'fileHash', 'manifestHash',
                    'auditHeadHash', 'claimEpoch')
                self.assertEqual(legacy_manifest, restored_local)
                self.assertEqual(legacy_manifest, restored_remote)
                self.assertEqual(
                    {name: legacy_marker.get(name) for name in marker_fields},
                    {name: restored_marker.get(name) for name in marker_fields})
                export_service._verify_object_commit_marker(job, restored_local)

                upgraded = export_service.get_record_export_manifest(export_id)

                self.assertNotIn(
                    'audit.json', [item['name'] for item in upgraded['files']])
                self.assertNotEqual(
                    legacy_manifest['manifestHash'], upgraded['manifestHash'])
                self.assertEqual(
                    upgraded,
                    export_service._read_json_strict(
                        manifest_path, 'record export manifest'))
                self.assertEqual(
                    upgraded,
                    json.loads(adapter.objects[manifest_key].decode('utf-8')))
                export_service._verify_object_commit_marker(job, upgraded)
        finally:
            export_service.configure_record_export_storage_adapter(None)

    def test_legacy_object_manifest_upgrade_recovers_after_manifest_sync_failure(self):
        self._assert_legacy_object_manifest_upgrade_recovers('manifest.json')

    def test_legacy_object_manifest_upgrade_recovers_after_commit_publish_failure(self):
        self._assert_legacy_object_manifest_upgrade_recovers('commit.json')

    def test_manifest_hmac_signature_verifier_checks_files_source_segments_and_clip_params(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            os.environ['YFEIEYE_RECORD_EXPORT_HMAC_SECRET'] = (
                'unit-test-manifest-secret-at-least-32-bytes')
            os.environ['YFEIEYE_RECORD_EXPORT_KEY_ID'] = 'unit-test-key'
            source_path = os.path.join(store_dir, 'source.mp4')
            with open(source_path, 'wb') as source_file:
                source_file.write(b'source-video-content')

            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            started = export_service.create_record_export({
                'review_case_id': 3001,
                'review_item_id': 1001,
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'source_alert_id': 'alert-export-hmac',
                'start_time': '2026-06-30T10:10:10',
                'end_time': '2026-06-30T10:10:40',
                'record_uri': source_path,
                'format': 'mp4',
                'operator_user_id': '9004',
                'approved_by': '9008',
                'record_segments': [{
                    'recordUri': source_path,
                    'sourceHash': 'sha256:' + __import__('hashlib').sha256(b'source-video-content').hexdigest(),
                    'clipStartTime': '2026-06-30T10:10:10',
                    'clipEndTime': '2026-06-30T10:10:40',
                    'ffmpegCommandHash': 'sha256:' + hashlib.sha256(b'clip-command').hexdigest(),
                }],
            }, record_resolver=_trusted_record_resolver,
               async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(job, b'hmac-export-content'))
            export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])
            manifest_path = os.path.join(store_dir, 'manifest.json')
            with open(manifest_path, 'w', encoding='utf-8') as file_obj:
                json.dump(manifest, file_obj, ensure_ascii=False, sort_keys=True)

            from app.services.record_export_manifest_verifier import verify_manifest_file

            report = verify_manifest_file(manifest_path)
            self.assertTrue(report['valid'])
            self.assertEqual('hmac-sha256', manifest['signature']['algorithm'])
            self.assertEqual('unit-test-key', manifest['signature']['keyId'])
            self.assertEqual('v1', manifest['signature']['algorithmVersion'])
            self.assertTrue(report['fileChecks'][0]['valid'])
            self.assertTrue(report['recordSegmentChecks'][0]['valid'])
            self.assertEqual(
                'sha256:' + hashlib.sha256(b'clip-command').hexdigest(),
                manifest['recordSegments'][0]['ffmpegCommandHash'],
            )

            tampered = dict(manifest)
            tampered['files'] = [dict(manifest['files'][0], hash='sha256:tampered')]
            with open(manifest_path, 'w', encoding='utf-8') as file_obj:
                json.dump(tampered, file_obj, ensure_ascii=False, sort_keys=True)
            tampered_report = verify_manifest_file(manifest_path)
            self.assertFalse(tampered_report['valid'])
            self.assertIn('file_hash_mismatch', tampered_report['violations'])

            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            os.environ.pop('YFEIEYE_RECORD_EXPORT_HMAC_SECRET', None)
            os.environ.pop('YFEIEYE_RECORD_EXPORT_KEY_ID', None)
            importlib.reload(export_service)

    def test_manifest_hmac_keyring_verifier_uses_manifest_key_id_after_rotation(self):
        with tempfile.TemporaryDirectory() as store_dir:
            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            os.environ.pop('YFEIEYE_RECORD_EXPORT_HMAC_SECRET', None)
            os.environ.pop('YFEIEYE_RECORD_EXPORT_KEY_ID', None)
            os.environ['YFEIEYE_RECORD_EXPORT_HMAC_KEYS'] = json.dumps({
                '2026-q2': 'old-manifest-secret-at-least-32-bytes',
                '2026-q3': 'new-manifest-secret-at-least-32-bytes',
            }, sort_keys=True)
            os.environ['YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID'] = '2026-q2'

            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            try:
                started = export_service.create_record_export({
                    'review_case_id': 3002,
                    'review_item_id': 1002,
                    'device_id': 'device-01',
                    'camera_id': 'device-01',
                    'source_alert_id': 'alert-export-keyring',
                    'start_time': '2026-06-30T10:10:10',
                    'end_time': '2026-06-30T10:10:40',
                    'record_uri': '/video/record/space/7/video/live/device-01/clip.flv',
                    'format': 'mp4',
                    'operator_user_id': '9004',
                    'approved_by': '9008',
                }, record_resolver=_trusted_record_resolver,
                   async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(job, b'keyring-export-content'))
                export_service.poll_record_export(started['export_id'])
                manifest = export_service.get_record_export_manifest(started['export_id'])
                self.assertEqual('hmac-sha256', manifest['signature']['algorithm'])
                self.assertEqual('2026-q2', manifest['signature']['keyId'])
                self.assertEqual('v2', manifest['signature']['algorithmVersion'])
                self.assertEqual('v2', manifest['signature']['signatureVersion'])

                manifest_path = os.path.join(store_dir, 'manifest-keyring.json')
                with open(manifest_path, 'w', encoding='utf-8') as file_obj:
                    json.dump(manifest, file_obj, ensure_ascii=False, sort_keys=True)

                os.environ['YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID'] = '2026-q3'
                from app.services.record_export_manifest_verifier import verify_manifest_file

                rotated_report = verify_manifest_file(manifest_path)
                self.assertTrue(rotated_report['valid'])
                self.assertTrue(rotated_report['signatureValid'])
                self.assertTrue(rotated_report['signatureKeyAvailable'])
                self.assertEqual('keyring', rotated_report['signatureKeySource'])
                self.assertEqual('2026-q2', rotated_report['keyId'])

                os.environ['YFEIEYE_RECORD_EXPORT_HMAC_KEYS'] = json.dumps({
                    '2026-q3': 'new-manifest-secret-at-least-32-bytes',
                }, sort_keys=True)
                missing_key_report = verify_manifest_file(manifest_path)
                self.assertFalse(missing_key_report['valid'])
                self.assertFalse(missing_key_report['signatureKeyAvailable'])
                self.assertIn('missing_hmac_key', missing_key_report['violations'])
            finally:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                os.environ.pop('YFEIEYE_RECORD_EXPORT_HMAC_KEYS', None)
                os.environ.pop('YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID', None)
                importlib.reload(export_service)

    def test_nonempty_malformed_hmac_keyring_never_falls_back_to_legacy_secret(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_RECORD_EXPORT_HMAC_SECRET': (
                'legacy-secret-that-must-not-mask-keyring-errors'),
            'YFEIEYE_RECORD_EXPORT_KEY_ID': 'legacy-key',
        }, clear=False):
            for malformed in ('{not-json', '[]', '{"keys":"not-an-object"}'):
                with self.subTest(keyring=malformed):
                    os.environ['YFEIEYE_RECORD_EXPORT_HMAC_KEYS'] = malformed
                    export_service = importlib.reload(export_service)
                    with self.assertRaisesRegex(RuntimeError, 'keyring'):
                        export_service.validate_record_export_signing_configuration()

    def test_manifest_verifier_rejects_algorithm_downgrade_and_signature_metadata_tamper(self):
        import copy
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_HMAC_SECRET': (
                'tamper-test-signing-secret-at-least-32-bytes'),
            'YFEIEYE_RECORD_EXPORT_KEY_ID': 'tamper-test-key',
        }, clear=False):
            os.environ.pop('YFEIEYE_RECORD_EXPORT_HMAC_KEYS', None)
            export_service = importlib.reload(export_service)
            started = export_service.create_record_export({
                'review_case_id': 3003,
                'review_item_id': 1003,
                'device_id': 'camera-01',
                'camera_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/tamper.mp4',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(
                   job, b'manifest-tamper-content'))
            export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])

            from app.services import record_export_manifest_verifier as verifier

            with mock.patch.dict(os.environ, {
                'VIDEO_ENV': 'production',
                'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK': 'false',
            }, clear=False):
                self.assertTrue(verifier.verify_manifest(manifest)['valid'])

                downgraded = copy.deepcopy(manifest)
                downgraded['signature'].update({
                    'algorithm': 'sha256',
                    'keyId': 'development-sha256',
                    'value': '',
                })
                downgraded['manifestHash'] = verifier._expected_manifest_hash(downgraded)
                downgraded['signature']['value'] = verifier._expected_manifest_signature(
                    downgraded, downgraded['manifestHash'], '')
                downgrade_report = verifier.verify_manifest(downgraded)
                self.assertFalse(downgrade_report['valid'])
                self.assertIn(
                    'signature_algorithm_not_allowed', downgrade_report['violations'])
                with self.assertRaisesRegex(RuntimeError, 'algorithm'):
                    export_service._validate_manifest_integrity(downgraded)

                metadata_tampered = copy.deepcopy(manifest)
                metadata_tampered['signature']['signer'] = 'attacker'
                metadata_tampered['signature']['signedAt'] = '2000-01-01T00:00:00+00:00'
                metadata_report = verifier.verify_manifest(metadata_tampered)
                self.assertFalse(metadata_report['valid'])
                self.assertTrue({
                    'manifest_hash_mismatch', 'signature_mismatch',
                }.intersection(metadata_report['violations']))

    def test_manifest_resigning_after_audit_uses_current_utc_signed_at(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3004,
            'review_item_id': 1004,
            'device_id': 'camera-01',
            'camera_id': 'camera-01',
            'tenant_id': '7',
            'record_uri': '/video/record/space/7/video/live/camera-01/audit-sign.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True,
           worker_runner=lambda job: _provenance_worker_result(
               job, b'audit-sign-content'))
        export_service.poll_record_export(started['export_id'])
        before = export_service.get_record_export_manifest(started['export_id'])
        time.sleep(0.01)

        entry = export_service.append_record_export_access_audit(
            started['export_id'], 'allowed', user_id='9004',
            tenant_id='7', camera_id='camera-01', action='download')
        after = export_service.get_record_export_manifest(started['export_id'])

        self.assertNotEqual(
            before['signature']['signedAt'], after['signature']['signedAt'])
        signed_at = datetime.fromisoformat(after['signature']['signedAt'])
        happened_at = datetime.fromisoformat(entry['happened_at'])
        self.assertIsNotNone(signed_at.tzinfo)
        self.assertGreaterEqual(signed_at, happened_at)

    def test_production_signing_fails_closed_without_an_active_hmac_key(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK': 'false',
        }, clear=False):
            for name in (
                    'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
                    'YFEIEYE_RECORD_EXPORT_KEY_ID',
                    'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
                    'YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID'):
                os.environ.pop(name, None)
            export_service = importlib.reload(export_service)

            with self.assertRaisesRegex(RuntimeError, 'HMAC'):
                export_service.validate_record_export_signing_configuration()

    def test_sha256_manifest_fallback_requires_explicit_development_or_test_opt_in(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {'VIDEO_ENV': 'development'}, clear=False):
            for name in (
                    'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
                    'YFEIEYE_RECORD_EXPORT_KEY_ID',
                    'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
                    'YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID',
                    'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK'):
                os.environ.pop(name, None)
            export_service = importlib.reload(export_service)
            with self.assertRaisesRegex(RuntimeError, 'fallback'):
                export_service.validate_record_export_signing_configuration()

            os.environ['YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK'] = 'true'
            selected = export_service.validate_record_export_signing_configuration()
            self.assertEqual('sha256', selected['algorithm'])
            self.assertEqual('development-sha256', selected['keyId'])

        with mock.patch.dict(os.environ, {
            'VIDEO_ENV': 'release',
            'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK': 'true',
        }, clear=False):
            export_service = importlib.reload(export_service)
            with self.assertRaisesRegex(RuntimeError, 'HMAC'):
                export_service.validate_record_export_signing_configuration()

    def test_object_storage_uploads_and_readback_verifies_every_evidence_artifact(self):
        import app.services.record_export_service as export_service

        class MemoryObjectStorage:
            def __init__(self):
                self.objects = {}
                self.readbacks = []

            def put_file(self, object_key, path, content_type=None):
                with open(path, 'rb') as file_obj:
                    self.objects[object_key] = file_obj.read()

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                self.readbacks.append(object_key)
                return io.BytesIO(self.objects[object_key])

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://review-evidence/' + object_key

        adapter = MemoryObjectStorage()
        storage_env = mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/cases',
        }, clear=False)
        storage_env.start()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            started = export_service.create_record_export({
                'review_case_id': 3010,
                'review_item_id': 1010,
                'device_id': 'camera-01',
                'camera_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/object.mp4',
                'storage_type': 'minio',
                'storage_root': 's3://review-evidence/cases',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(
                   job, b'object-storage-export'))

            ready = export_service.poll_record_export(started['export_id'])
            prefix = f'tenants/7/exports/{started["export_id"]}/'

            self.assertEqual('ready', ready['status'])
            self.assertEqual('verified', ready['storage_verification']['status'])
            self.assertTrue({
                prefix + 'content.bin',
                prefix + 'job.json',
                prefix + 'audit.json',
                prefix + 'manifest.json',
                prefix + 'commit.json',
                prefix + 'source-000.fixture',
            }.issubset(adapter.objects))
            self.assertFalse(any('/.staging/' in key for key in adapter.objects))
            self.assertTrue(set(adapter.objects).issubset(set(adapter.readbacks)))
            manifest = export_service.get_record_export_manifest(started['export_id'])
            package = next(file for file in manifest['files']
                           if file['role'] == 'export_package')
            self.assertEqual('minio', package['storage']['storageType'])
            self.assertEqual(
                's3://review-evidence/' + prefix + 'content.bin',
                package['storage']['uri'],
            )

            metadata_sync_lock_states = []
            original_sync = export_service._sync_object_storage_artifacts

            def track_metadata_sync(job, *args, **kwargs):
                if kwargs.get('metadata_only'):
                    metadata_sync_lock_states.append(os.path.exists(
                        export_service._audit_lock_path(job['export_id'])))
                return original_sync(job, *args, **kwargs)

            with mock.patch.object(
                    export_service, '_sync_object_storage_artifacts',
                    side_effect=track_metadata_sync):
                downloaded = export_service.download_record_export(started['export_id'])
            self.assertNotIn('content', downloaded)
            self.assertTrue(downloaded['temporary_path'])
            self.assertTrue(metadata_sync_lock_states)
            self.assertTrue(all(metadata_sync_lock_states))
            with open(downloaded['path'], 'rb') as handle:
                self.assertEqual(b'object-storage-export', handle.read())
            manifest_key = prefix + 'manifest.json'
            remote_manifest = adapter.objects[manifest_key]
            adapter.objects[manifest_key] = (
                bytes([remote_manifest[0] ^ 1]) + remote_manifest[1:])
            with self.assertRaisesRegex(RuntimeError, 'metadata hash mismatch'):
                export_service.get_record_export_manifest(started['export_id'])
        finally:
            export_service.configure_record_export_storage_adapter(None)
            storage_env.stop()

    def test_initial_object_storage_sync_serializes_concurrent_access_audit(self):
        import app.services.record_export_service as export_service

        audit_upload_started = threading.Event()
        release_audit_upload = threading.Event()

        class RacingObjectStorage:
            def __init__(self):
                self.objects = {}
                self.blocked_once = False

            def put_file(self, object_key, path, content_type=None):
                if object_key.endswith('/audit.json') and not self.blocked_once:
                    self.blocked_once = True
                    audit_upload_started.set()
                    if not release_audit_upload.wait(2):
                        raise RuntimeError('test timed out releasing audit upload')
                with open(path, 'rb') as file_obj:
                    self.objects[object_key] = file_obj.read()

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                return io.BytesIO(self.objects[object_key])

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://review-evidence/' + object_key

        adapter = RacingObjectStorage()
        export_errors = []
        export_results = []
        audit_errors = []
        storage_env = mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/cases',
        }, clear=False)
        storage_env.start()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            started = export_service.create_record_export({
                'review_case_id': 3011,
                'review_item_id': 1011,
                'device_id': 'camera-01',
                'camera_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/race.mp4',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(
                   job, b'concurrent-audit-export'))

            def run_export():
                try:
                    export_results.append(export_service.poll_record_export(started['export_id']))
                except Exception as exc:  # pragma: no cover - asserted below
                    export_errors.append(exc)

            def append_access_audit():
                try:
                    export_service.append_record_export_access_audit(
                        started['export_id'], 'allowed', user_id='operator',
                        tenant_id='7', camera_id='camera-01', action='export')
                except Exception as exc:  # pragma: no cover - asserted below
                    audit_errors.append(exc)

            export_thread = threading.Thread(target=run_export, name='record-export')
            export_thread.start()
            self.assertTrue(audit_upload_started.wait(2))
            audit_thread = threading.Thread(target=append_access_audit, name='access-audit')
            audit_thread.start()
            audit_thread.join(timeout=0.2)
            audit_waited_for_initial_sync = audit_thread.is_alive()
            release_audit_upload.set()
            export_thread.join(timeout=3)
            audit_thread.join(timeout=3)

            self.assertTrue(audit_waited_for_initial_sync)
            self.assertFalse(export_thread.is_alive())
            self.assertFalse(audit_thread.is_alive())
            self.assertEqual([], export_errors)
            self.assertEqual([], audit_errors)
            self.assertEqual('ready', export_results[0]['status'])
        finally:
            release_audit_upload.set()
            export_service.configure_record_export_storage_adapter(None)
            storage_env.stop()

    def test_object_storage_ready_manifest_ignores_stale_status_cache(self):
        import app.services.record_export_service as export_service

        class CacheReplacingStorage:
            def __init__(self):
                self.objects = {}
                self.replaced = False

            def put_file(self, object_key, path, content_type=None):
                del content_type
                with open(path, 'rb') as file_obj:
                    self.objects[object_key] = file_obj.read()
                if self.replaced or not object_key.endswith('/content.bin'):
                    return
                export_id = object_key.split('/exports/', 1)[1].split('/', 1)[0]
                stale = dict(export_service._EXPORT_JOBS[export_id])
                stale['status'] = 'verifying'
                export_service._EXPORT_JOBS[export_id] = stale
                self.replaced = True

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                return io.BytesIO(self.objects[object_key])

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://review-evidence/' + object_key

        adapter = CacheReplacingStorage()
        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/cases',
        }, clear=False):
            export_service.configure_record_export_storage_adapter(lambda _job: adapter)
            try:
                started = export_service.create_record_export({
                    'review_case_id': 3011,
                    'review_item_id': 1011,
                    'device_id': 'camera-01',
                    'camera_id': 'camera-01',
                    'tenant_id': '7',
                    'record_uri': '/video/record/space/7/video/live/camera-01/race.mp4',
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(
                       job, b'cache-race-export'))

                ready = export_service.poll_record_export(started['export_id'])
                manifest = export_service.get_record_export_manifest(
                    started['export_id'])

                self.assertTrue(adapter.replaced)
                self.assertEqual('ready', ready['status'])
                self.assertEqual('ready', manifest['status'])
            finally:
                export_service.configure_record_export_storage_adapter(None)

    def test_object_storage_export_without_authorized_tenant_fails_closed(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/cases',
        }, clear=False):
            with self.assertRaisesRegex(RuntimeError, 'tenant id'):
                export_service.create_record_export({
                    'review_case_id': 3010,
                    'review_item_id': 1010,
                    'device_id': 'camera-01',
                    'camera_id': 'camera-01',
                    'record_uri': (
                        '/video/record/space/7/video/live/camera-01/no-tenant.mp4'),
                }, record_resolver=_trusted_record_resolver, async_worker=True)

    def test_object_storage_readback_hash_mismatch_never_marks_export_ready(self):
        import app.services.record_export_service as export_service

        class CorruptReadbackStorage:
            def __init__(self):
                self.objects = {}

            def put_file(self, object_key, path, content_type=None):
                with open(path, 'rb') as file_obj:
                    self.objects[object_key] = file_obj.read()

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                content = self.objects[object_key]
                if object_key.endswith('/content.bin'):
                    content = content[:-1] + bytes([content[-1] ^ 1])
                return io.BytesIO(content)

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://review-evidence/' + object_key

        adapter = CorruptReadbackStorage()
        storage_env = mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 's3',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/cases',
        }, clear=False)
        storage_env.start()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            started = export_service.create_record_export({
                'review_case_id': 3011,
                'review_item_id': 1011,
                'device_id': 'camera-01',
                'camera_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/corrupt.mp4',
                'storage_type': 's3',
                'storage_root': 's3://review-evidence/cases',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(
                   job, b'object-storage-export'))

            failed = export_service.poll_record_export(started['export_id'])

            self.assertEqual('failed', failed['status'])
            self.assertIn('readback hash mismatch', failed['last_error'])
            self.assertIsNone(failed['download_url'])
        finally:
            export_service.configure_record_export_storage_adapter(None)
            storage_env.stop()

    def test_local_export_download_returns_streamable_path_without_loading_content(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3012,
            'review_item_id': 1012,
            'device_id': 'camera-01',
            'camera_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/download.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True,
           worker_runner=lambda job: _provenance_worker_result(job, b'streamed-export'))
        ready = export_service.poll_record_export(started['export_id'])

        downloaded = export_service.download_record_export(ready['export_id'])

        self.assertNotIn('content', downloaded)
        self.assertTrue(os.path.isfile(downloaded['path']))
        with open(downloaded['path'], 'rb') as file_obj:
            self.assertEqual(b'streamed-export', file_obj.read())

    def test_local_export_download_rejects_package_size_and_hash_mismatch_as_integrity_error(self):
        import app.services.record_export_service as export_service

        for mutation in ('size', 'hash'):
            with self.subTest(mutation=mutation):
                started = export_service.create_record_export({
                    'review_case_id': 4012,
                    'review_item_id': 2012,
                    'device_id': 'camera-01',
                    'camera_id': 'camera-01',
                    'record_uri': (
                        f'/video/record/space/7/video/live/camera-01/{mutation}.mp4'),
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(
                       job, b'integrity-protected-export'))
                ready = export_service.poll_record_export(started['export_id'])
                content_path = export_service._content_path(ready['export_id'])
                with open(content_path, 'rb') as content_file:
                    content = content_file.read()
                if mutation == 'size':
                    corrupted = content + b'!'
                else:
                    corrupted = bytes([content[0] ^ 1]) + content[1:]
                with open(content_path, 'wb') as content_file:
                    content_file.write(corrupted)

                with self.assertRaises(export_service.RecordExportIntegrityError):
                    export_service.download_record_export(ready['export_id'])

    def test_concurrent_process_audit_appends_preserve_one_atomic_hash_chain(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3013,
            'review_item_id': 1013,
            'device_id': 'camera-01',
            'camera_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/audit.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        barrier_path = os.path.join(self._isolated_export_store.name, 'audit.start')
        code = (
            "import os,time; "
            "from app.services.record_export_service import append_record_export_access_audit; "
            "barrier=os.environ['AUDIT_BARRIER']; "
            "\nwhile not os.path.exists(barrier): time.sleep(0.01)\n"
            "append_record_export_access_audit(os.environ['EXPORT_ID'],'allowed',"
            "user_id=os.environ['AUDIT_USER'],camera_id='camera-01',action='download')"
        )
        processes = []
        for index in range(8):
            env = dict(os.environ)
            env.update({
                'EXPORT_ID': started['export_id'],
                'AUDIT_BARRIER': barrier_path,
                'AUDIT_USER': str(index),
            })
            processes.append(subprocess.Popen(
                [sys.executable, '-c', code], cwd=os.path.dirname(__file__), env=env,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            ))
        with open(barrier_path, 'w', encoding='utf-8') as barrier:
            barrier.write('go')
        failures = []
        for process in processes:
            stdout, stderr = process.communicate(timeout=30)
            if process.returncode:
                failures.append((stdout, stderr))
        self.assertEqual([], failures)

        audit = export_service.get_record_export_audit(started['export_id'])
        access_entries = [entry for entry in audit if entry['action'] == 'access_allowed']
        self.assertEqual(8, len(access_entries))
        previous = 'GENESIS'
        for entry in audit:
            self.assertEqual(previous, entry['previousHash'])
            previous = entry['entryHash']

    def test_audit_lock_retries_windows_sharing_violation(self):
        import app.services.record_export_service as export_service

        original_open = os.open
        attempts = []

        def flaky_open(path, flags, mode=0o777):
            attempts.append(path)
            if len(attempts) == 1:
                raise PermissionError('simulated Windows sharing violation')
            return original_open(path, flags, mode)

        with mock.patch.object(export_service.os, 'open', side_effect=flaky_open):
            token = export_service._acquire_audit_lock('sharing-violation')
        try:
            self.assertEqual(2, len(attempts))
        finally:
            export_service._release_named_claim(
                export_service._audit_lock_path('sharing-violation'), token)

    def test_browser_compatible_ffmpeg_command_uses_accurate_seek_and_h264_faststart(self):
        import app.services.record_export_service as export_service

        command = export_service._single_clip_command(
            'ffmpeg', 'source.flv', 'output.mp4',
            {'format': 'mp4'},
            {
                'segment_start_time': '2026-07-10T10:00:00',
                'clip_start_time': '2026-07-10T10:00:02',
                'clip_end_time': '2026-07-10T10:00:05',
            },
        )

        self.assertGreater(command.index('-ss'), command.index('-i'))
        self.assertEqual('libx264', command[command.index('-c:v') + 1])
        self.assertEqual('yuv420p', command[command.index('-pix_fmt') + 1])
        self.assertEqual('+faststart', command[command.index('-movflags') + 1])
        self.assertIn('-nostdin', command)
        self.assertEqual('2', command[command.index('-threads') + 1])
        self.assertEqual('1', command[command.index('-filter_threads') + 1])
        thread_positions = [
            index for index, value in enumerate(command) if value == '-threads'
        ]
        self.assertGreater(max(thread_positions), command.index('-c:v'))

    def test_ffmpeg_timeout_scales_with_media_duration_and_has_hard_cap(self):
        from app.services import media_resource_guard as resource_guard

        with mock.patch.dict(os.environ, {
            'YFEIEYE_FFMPEG_TIMEOUT_BASE_SECONDS': '10',
            'YFEIEYE_FFMPEG_TIMEOUT_PER_MEDIA_SECOND': '2',
            'YFEIEYE_FFMPEG_TIMEOUT_MAX_SECONDS': '30',
        }, clear=False):
            self.assertEqual(20.0, resource_guard.ffmpeg_timeout_seconds(5))
            self.assertEqual(30.0, resource_guard.ffmpeg_timeout_seconds(100))
            self.assertEqual(30.0, resource_guard.ffmpeg_timeout_seconds(None))

    def test_ffmpeg_timeout_uses_probed_input_duration_without_clip_window(self):
        import app.services.record_export_service as export_service

        source = {'path': 'full-recording.flv'}
        with mock.patch.object(
            export_service, '_probe_media_duration_seconds', return_value=87.5
        ) as probe:
            duration = export_service._source_processing_duration(
                'ffprobe', source, {})

        self.assertEqual(87.5, duration)
        self.assertEqual(87.5, source['input_duration_seconds'])
        probe.assert_called_once_with('ffprobe', 'full-recording.flv')

    def test_global_ffmpeg_capacity_blocks_second_process_until_slot_released(self):
        import threading
        from app.services import media_resource_guard as resource_guard

        previous = os.environ.get('YFEIEYE_FFMPEG_MAX_CONCURRENT')
        os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = '1'
        resource_guard = importlib.reload(resource_guard)
        entered = threading.Event()

        def contender():
            with resource_guard.ffmpeg_slot():
                entered.set()

        try:
            with resource_guard.ffmpeg_slot():
                thread = threading.Thread(target=contender)
                thread.start()
                self.assertFalse(entered.wait(0.1))
            thread.join(timeout=2)
            self.assertFalse(thread.is_alive())
            self.assertTrue(entered.is_set())
        finally:
            if previous is None:
                os.environ.pop('YFEIEYE_FFMPEG_MAX_CONCURRENT', None)
            else:
                os.environ['YFEIEYE_FFMPEG_MAX_CONCURRENT'] = previous
            importlib.reload(resource_guard)

    def test_guard_terminates_process_while_output_is_growing_past_limit(self):
        from app.services.media_resource_guard import run_ffmpeg_guarded

        output_path = os.path.join(self._isolated_export_store.name, 'growing-output.bin')
        writer = (
            "import os,sys,time; p=sys.argv[1]; f=open(p,'wb'); "
            "[(f.write(b'x'*4096),f.flush(),os.fsync(f.fileno()),time.sleep(.02)) "
            "for _ in range(50)]; f.close()"
        )
        started = time.monotonic()
        with self.assertRaisesRegex(RuntimeError, 'output size limit'):
            run_ffmpeg_guarded(
                [sys.executable, '-c', writer, output_path],
                output_path=output_path,
                expected_duration=30,
                max_output_bytes=1024,
                poll_seconds=0.01,
            )
        self.assertLess(time.monotonic() - started, 1.0)

    def test_export_store_quota_and_janitor_remove_only_orphan_resources(self):
        import app.services.record_export_service as export_service

        store = self._isolated_export_store.name
        temp_root = os.path.join(store, '.tmp')
        os.makedirs(temp_root, exist_ok=True)
        stale = time.time() - 7200

        orphan_temp = os.path.join(temp_root, 'yfeieye-record-export-orphan')
        os.makedirs(orphan_temp)
        for name in ('source-000.mp4', 'clip-000.mp4', 'output.mp4'):
            path = os.path.join(orphan_temp, name)
            with open(path, 'wb') as handle:
                handle.write(b'orphan')
            os.utime(path, (stale, stale))
        os.utime(orphan_temp, (stale, stale))

        failed_dir = os.path.join(store, 'failed-export')
        os.makedirs(failed_dir)
        with open(os.path.join(failed_dir, 'job.json'), 'w', encoding='utf-8') as handle:
            json.dump({'export_id': 'failed-export', 'status': 'failed'}, handle)
        for name in ('source-000.mp4', 'clip-000.mp4', 'worker-output.mp4',
                     'download-deadbeef.tmp'):
            path = os.path.join(failed_dir, name)
            with open(path, 'wb') as handle:
                handle.write(b'orphan')
            os.utime(path, (stale, stale))

        ready_dir = os.path.join(store, 'ready-export')
        os.makedirs(ready_dir)
        with open(os.path.join(ready_dir, 'job.json'), 'w', encoding='utf-8') as handle:
            json.dump({'export_id': 'ready-export', 'status': 'ready'}, handle)
        ready_source = os.path.join(ready_dir, 'source-000.mp4')
        with open(ready_source, 'wb') as handle:
            handle.write(b'evidence-source')
        os.utime(ready_source, (stale, stale))

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_TEMP_DIR': temp_root,
            'YFEIEYE_RECORD_EXPORT_ORPHAN_TTL_SECONDS': '60',
        }, clear=False):
            result = export_service.cleanup_record_export_resources(now=time.time())

        self.assertGreaterEqual(result['removed_paths'], 5)
        self.assertFalse(os.path.exists(orphan_temp))
        self.assertFalse(os.path.exists(os.path.join(failed_dir, 'source-000.mp4')))
        self.assertFalse(os.path.exists(os.path.join(failed_dir, 'worker-output.mp4')))
        self.assertFalse(os.path.exists(os.path.join(failed_dir, 'download-deadbeef.tmp')))
        self.assertTrue(os.path.isfile(ready_source))

        filler = os.path.join(store, 'quota-filler.bin')
        with open(filler, 'wb') as handle:
            handle.write(b'x' * 64)
        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORE_MAX_BYTES': '64',
        }, clear=False), self.assertRaisesRegex(RuntimeError, 'store quota'):
            export_service._ensure_export_store_quota(1)

    def test_cleanup_keeps_unscoped_legacy_jobs_on_local_storage(self):
        import app.services.record_export_service as export_service

        legacy_job = {
            'export_id': 'legacy-local-export',
            'status': 'failed',
            'created_at': '2026-07-09T00:00:00+00:00',
        }
        explicit_local_job = {
            **legacy_job,
            'storage_type': 'local_filesystem',
        }
        legacy_dir = os.path.join(
            self._isolated_export_store.name, legacy_job['export_id'])
        os.makedirs(legacy_dir)
        with open(os.path.join(legacy_dir, 'job.json'), 'w', encoding='utf-8') as handle:
            json.dump(legacy_job, handle)

        adapter = mock.Mock()
        adapter_factory = mock.Mock(return_value=adapter)
        export_service.configure_record_export_storage_adapter(adapter_factory)
        try:
            with mock.patch.dict(os.environ, {
                'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
                'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://review-evidence/exports',
            }, clear=False), self.assertNoLogs(export_service._LOGGER.name, level='WARNING'):
                self.assertEqual('local_filesystem', export_service._storage_type(legacy_job))
                self.assertEqual(
                    self._isolated_export_store.name,
                    export_service._storage_root(legacy_job),
                )
                self.assertEqual(
                    self._isolated_export_store.name,
                    export_service._storage_root(explicit_local_job),
                )
                lifecycle = export_service._manifest_storage_lifecycle(
                    legacy_job['export_id'],
                    explicit_local_job,
                    '2026-07-20T00:00:00Z',
                )
                reference = export_service._artifact_storage_reference(
                    explicit_local_job,
                    legacy_job['export_id'],
                    'content.bin',
                    'export_package',
                    '',
                    '2026-07-20T00:00:00Z',
                )
                self.assertEqual('local_filesystem', lifecycle['storageType'])
                self.assertEqual(self._isolated_export_store.name, lifecycle['storeRoot'])
                self.assertEqual('local_filesystem', reference['storageType'])
                self.assertEqual(
                    self._isolated_export_store.name.replace('\\', '/').rstrip('/')
                    + '/legacy-local-export/content.bin',
                    reference['uri'],
                )
                self.assertEqual(
                    's3://review-evidence/exports',
                    export_service._storage_root({
                        'tenant_id': '7',
                        'storage_type': 'minio',
                    }),
                )
                first = export_service.cleanup_record_export_resources(now=time.time())
                second = export_service.cleanup_record_export_resources(now=time.time())
        finally:
            export_service.configure_record_export_storage_adapter(None)

        self.assertEqual('completed', first['status'])
        self.assertEqual('completed', second['status'])
        self.assertEqual(0, first['cleanup_failures'])
        self.assertEqual(0, second['cleanup_failures'])
        adapter_factory.assert_not_called()
        adapter.list.assert_not_called()
        adapter.delete.assert_not_called()

    def test_cleanup_reports_tenant_scoped_object_storage_failures(self):
        import app.services.record_export_service as export_service

        job = {
            'export_id': 'tenant-minio-export',
            'status': 'failed',
            'tenant_id': '7',
            'storage_type': 'minio',
            'storage_root': 's3://review-evidence/exports',
        }
        export_dir = os.path.join(self._isolated_export_store.name, job['export_id'])
        os.makedirs(export_dir)
        with open(os.path.join(export_dir, 'job.json'), 'w', encoding='utf-8') as handle:
            json.dump(job, handle)

        adapter = mock.Mock()
        adapter.list.side_effect = RuntimeError('object storage unavailable')
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            with self.assertLogs(export_service._LOGGER.name, level='WARNING'):
                result = export_service.cleanup_record_export_resources(now=time.time())
        finally:
            export_service.configure_record_export_storage_adapter(None)

        self.assertEqual('partial', result['status'])
        self.assertEqual(1, result['cleanup_failures'])
        adapter.list.assert_called_once_with(
            'tenants/7/exports/tenant-minio-export/.staging/')
        adapter.delete.assert_not_called()

    def test_cleanup_rejects_object_keys_outside_tenant_staging_prefix(self):
        import app.services.record_export_service as export_service

        job = {
            'export_id': 'tenant-minio-export',
            'status': 'failed',
            'tenant_id': '7',
            'storage_type': 'minio',
            'storage_root': 's3://review-evidence/exports',
        }
        export_dir = os.path.join(self._isolated_export_store.name, job['export_id'])
        os.makedirs(export_dir)
        with open(os.path.join(export_dir, 'job.json'), 'w', encoding='utf-8') as handle:
            json.dump(job, handle)

        adapter = mock.Mock()
        adapter.list.return_value = [
            'tenants/7/exports/tenant-minio-export/.staging/claim/content.bin',
            'tenants/8/exports/other-export/.staging/claim/content.bin',
        ]
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            with self.assertLogs(export_service._LOGGER.name, level='WARNING'):
                result = export_service.cleanup_record_export_resources(now=time.time())
        finally:
            export_service.configure_record_export_storage_adapter(None)

        self.assertEqual('partial', result['status'])
        self.assertEqual(1, result['cleanup_failures'])
        adapter.list.assert_called_once_with(
            'tenants/7/exports/tenant-minio-export/.staging/')
        adapter.delete.assert_not_called()

    def test_ffmpeg_worker_removes_configured_temp_workdir_after_materialize_failure(self):
        import app.services.record_export_service as export_service

        temp_root = os.path.join(self._isolated_export_store.name, 'worker-temp')
        os.makedirs(temp_root)
        observed = {}

        def fail_materialize(_job, workdir):
            observed['workdir'] = workdir
            with open(os.path.join(workdir, 'source-000.mp4'), 'wb') as handle:
                handle.write(b'partial-minio-download')
            raise RuntimeError('minio download interrupted')

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_TEMP_DIR': temp_root,
        }, clear=False), mock.patch.object(
            export_service.shutil, 'which', side_effect=lambda name: name
        ), mock.patch.object(
            export_service, '_materialize_record_sources', side_effect=fail_materialize
        ), self.assertRaisesRegex(RuntimeError, 'minio download interrupted'):
            export_service._run_ffmpeg_export({
                'export_id': 'temp-cleanup',
                'format': 'mp4',
            })

        self.assertFalse(os.path.exists(observed['workdir']))
        self.assertEqual(os.path.abspath(temp_root),
                         os.path.abspath(os.path.dirname(observed['workdir'])))
        self.assertEqual([], os.listdir(temp_root))

    def test_object_storage_staging_is_deleted_when_final_publish_fails(self):
        import app.services.record_export_service as export_service

        class FailingPromotionStorage:
            def __init__(self):
                self.objects = {}

            def put_file(self, object_key, path, content_type=None):
                if '/.staging/' not in object_key:
                    raise RuntimeError('final promotion failed')
                with open(path, 'rb') as handle:
                    self.objects[object_key] = handle.read()

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                return io.BytesIO(self.objects[object_key])

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://evidence/' + object_key

        adapter = FailingPromotionStorage()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            with mock.patch.dict(os.environ, {
                'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
                'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://evidence/exports',
            }, clear=False):
                started = export_service.create_record_export({
                    'review_case_id': 4100,
                    'review_item_id': 1100,
                    'device_id': 'camera-01',
                    'camera_id': 'camera-01',
                    'tenant_id': '7',
                    'record_uri': '/video/record/space/7/video/live/camera-01/fail.mp4',
                    'storage_type': 'minio',
                    'storage_root': 's3://evidence/exports',
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(job, b'payload'))
                failed = export_service.poll_record_export(started['export_id'])
        finally:
            export_service.configure_record_export_storage_adapter(None)

        self.assertEqual('failed', failed['status'])
        self.assertFalse(any('/.staging/' in key for key in adapter.objects))

    def test_failed_worker_removes_transient_output_immediately(self):
        import app.services.record_export_service as export_service

        observed = {}

        def invalid_worker(job):
            path = os.path.join(
                export_service._export_dir(job['export_id']), 'worker-output.mp4')
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, 'wb') as handle:
                handle.write(b'uncommitted-output')
            observed['path'] = path
            return {
                'content_path': path,
                'ffmpeg_command_hash': 'invalid',
                'record_segments': [],
            }

        started = export_service.create_record_export({
            'review_case_id': 4200,
            'review_item_id': 1200,
            'device_id': 'camera-01',
            'camera_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/transient.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True,
           worker_runner=invalid_worker)

        failed = export_service.poll_record_export(started['export_id'])

        self.assertEqual('failed', failed['status'])
        self.assertFalse(os.path.exists(observed['path']))

    def test_minio_download_temp_is_removed_when_audit_persistence_fails(self):
        import app.services.record_export_service as export_service

        class MemoryStorage:
            def __init__(self):
                self.objects = {}

            def put_file(self, object_key, path, content_type=None):
                with open(path, 'rb') as handle:
                    self.objects[object_key] = handle.read()

            def stat(self, object_key):
                return {'size': len(self.objects[object_key])}

            def open(self, object_key):
                return io.BytesIO(self.objects[object_key])

            def delete(self, object_key):
                self.objects.pop(object_key, None)

            def uri(self, object_key):
                return 's3://evidence/' + object_key

        adapter = MemoryStorage()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            with mock.patch.dict(os.environ, {
                'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
                'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://evidence/exports',
            }, clear=False):
                started = export_service.create_record_export({
                    'review_case_id': 4300,
                    'review_item_id': 1300,
                    'device_id': 'camera-01',
                    'camera_id': 'camera-01',
                    'tenant_id': '7',
                    'record_uri': '/video/record/space/7/video/live/camera-01/download.mp4',
                    'storage_type': 'minio',
                    'storage_root': 's3://evidence/exports',
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(
                       job, b'download-payload'))
                ready = export_service.poll_record_export(started['export_id'])
                self.assertEqual('ready', ready['status'])

                original_append = export_service._append_export_audit

                def fail_download_audit(export_id, action, *args, **kwargs):
                    if action == 'downloaded':
                        raise OSError('audit disk unavailable')
                    return original_append(export_id, action, *args, **kwargs)

                with mock.patch.object(
                    export_service, '_append_export_audit', side_effect=fail_download_audit
                ), self.assertRaisesRegex(OSError, 'audit disk unavailable'):
                    export_service.download_record_export(started['export_id'])

                leftovers = [
                    name for name in os.listdir(export_service._export_temp_root())
                    if name.startswith(f'download-{started["export_id"]}-')
                ]
        finally:
            export_service.configure_record_export_storage_adapter(None)

        self.assertEqual([], leftovers)

    def test_atomic_json_replacement_uses_final_store_footprint_for_quota(self):
        import app.services.record_export_service as export_service

        path = os.path.join(self._isolated_export_store.name, 'atomic', 'job.json')
        value = {'export_id': 'atomic', 'status': 'pending'}
        export_service._write_json(path, value)
        current_usage = export_service._tree_size_excluding(
            self._isolated_export_store.name,
            {export_service._export_temp_root()},
        )

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORE_MAX_BYTES': str(current_usage + 1),
        }, clear=False):
            export_service._write_json(path, value)

        with open(path, encoding='utf-8') as handle:
            self.assertEqual(value, json.load(handle))

    def test_janitor_preserves_stale_temp_directory_while_export_claim_is_alive(self):
        import app.services.record_export_service as export_service

        export_id = 'active-cross-process'
        export_dir = export_service._export_dir(export_id)
        os.makedirs(export_dir, exist_ok=True)
        claim_path = export_service._claim_path(export_id)
        with open(claim_path, 'w', encoding='utf-8') as handle:
            handle.write('active-token')

        temp_root = export_service._export_temp_root()
        os.makedirs(temp_root, exist_ok=True)
        workdir = os.path.join(temp_root, f'yfeieye-record-export-{export_id}-worker')
        os.makedirs(workdir)
        marker = os.path.join(workdir, '.active.json')
        with open(marker, 'w', encoding='utf-8') as handle:
            json.dump({'export_id': export_id}, handle)
        stale = time.time() - 7200
        os.utime(marker, (stale, stale))
        os.utime(workdir, (stale, stale))

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_ORPHAN_TTL_SECONDS': '60',
        }, clear=False):
            export_service.cleanup_record_export_resources(now=time.time())

        self.assertTrue(os.path.isdir(workdir))

    def test_store_and_temp_quota_fail_before_disk_free_reserve_is_consumed(self):
        import app.services.record_export_service as export_service

        usage = types.SimpleNamespace(total=1024, used=1024, free=0)
        with mock.patch.object(
            export_service.shutil, 'disk_usage', return_value=usage
        ), mock.patch.dict(os.environ, {
            'YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES': '128',
        }, clear=False):
            with self.assertRaisesRegex(RuntimeError, 'free space'):
                export_service._ensure_export_store_quota(1)
            with self.assertRaisesRegex(RuntimeError, 'free space'):
                export_service._ensure_export_temp_quota(1)

    def test_record_segments_reject_original_uri_and_space_object_identity_bypasses(self):
        import app.services.record_export_service as export_service

        trusted_uri = '/video/record/space/7/video/live/camera-01/trusted.mp4'
        base = {
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': trusted_uri,
            'record_segments': [{
                'recordUri': trusted_uri,
                'segmentStartTime': '2026-07-10T10:00:00',
                'segmentEndTime': '2026-07-10T10:00:01',
            }],
        }
        with self.assertRaisesRegex(ValueError, 'original_record_uri'):
            export_service.validate_record_export_request({
                **base,
                'record_segments': [{
                    **base['record_segments'][0],
                    'originalRecordUri': os.path.abspath('private-source.mp4'),
                }],
            }, 'camera-01', _trusted_record_resolver)

        with self.assertRaisesRegex(ValueError, 'identity'):
            export_service.validate_record_export_request({
                **base,
                'record_segments': [{
                    **base['record_segments'][0],
                    'spaceId': 8,
                    'objectName': 'live/camera-02/other.mp4',
                }],
            }, 'camera-01', _trusted_record_resolver)

        with self.assertRaisesRegex(ValueError, 'identity'):
            export_service.validate_record_export_request({
                **base,
                'space_id': 8,
                'object_name': 'live/camera-02/other.mp4',
            }, 'camera-01', _trusted_record_resolver)

        with self.assertRaisesRegex(ValueError, 'identity'):
            export_service.validate_record_export_request({
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'record_uri': os.path.abspath('metadata-owned-source.mp4'),
                'space_id': 8,
                'object_name': 'live/camera-02/other.mp4',
            }, 'camera-01', _trusted_record_resolver)

    def test_default_record_resolver_must_return_explicit_matching_camera_scope(self):
        import app.services.record_export_service as export_service

        uri = '/video/record/space/7/video/live/camera-01/resolved.mp4'
        with mock.patch.object(
                export_service,
                'resolve_record_uri_from_window',
                return_value={'record_uri': uri, 'source': 'alert_segment'}):
            with self.assertRaisesRegex(ValueError, 'authorized camera'):
                export_service.validate_record_export_request({
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': uri,
                }, 'camera-01')

    def test_default_record_resolver_rejects_other_tenant(self):
        import app.services.record_export_service as export_service

        uri = '/video/record/space/7/video/live/camera-01/resolved.mp4'
        with mock.patch.object(
                export_service,
                'resolve_record_uri_from_window',
                return_value={
                    'record_uri': uri,
                    'source': 'record_window',
                    'camera_id': 'camera-01',
                    'tenant_id': 8,
                }):
            with self.assertRaisesRegex(ValueError, 'tenant'):
                export_service.validate_record_export_request({
                    'tenant_id': '7',
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': uri,
                }, 'camera-01')

    def test_server_policy_overrides_caller_storage_retention_and_expiry(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'local_filesystem',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': '/data/yfeieye-record-exports',
            'YFEIEYE_RECORD_EXPORT_RETENTION_DAYS': '7',
        }, clear=False):
            built = export_service._build_record_export({
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'record_uri': '/video/record/space/7/video/live/camera-01/policy.mp4',
                'storage_type': 's3',
                'storage_root': 's3://attacker/private',
                'retention_days': 3650,
                'expires_at': '2099-01-01T00:00:00Z',
            }, _trusted_record_resolver)

        self.assertEqual('local_filesystem', built['storage_type'])
        self.assertEqual('/data/yfeieye-record-exports', built['storage_root'])
        self.assertEqual('7', built['retention_days'])
        self.assertIsNone(built['expires_at'])

    def test_claim_heartbeat_renews_lease_and_lost_worker_cannot_publish(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3020,
            'review_item_id': 1020,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/lease.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True,
           worker_runner=lambda job: self.fail('replacement claim must fence worker'))
        claim = export_service._acquire_export_claim(started['export_id'])
        claim_path = export_service._claim_path(started['export_id'])
        before = os.path.getmtime(claim_path)
        time.sleep(0.02)
        self.assertTrue(export_service._heartbeat_export_claim(started['export_id'], claim))
        self.assertGreater(os.path.getmtime(claim_path), before)
        export_service._release_export_claim(started['export_id'], claim)

        def loses_claim(job):
            with open(export_service._claim_path(job['export_id']), 'w', encoding='utf-8') as handle:
                json.dump({'token': 'replacement-worker'}, handle)
            return _provenance_worker_result(job, b'must-not-publish')

        export_service._EXPORT_JOBS[started['export_id']]['_worker_runner'] = loses_claim
        result = export_service.poll_record_export(started['export_id'])

        self.assertNotEqual('ready', result['status'])
        self.assertNotEqual('failed', result['status'])
        self.assertFalse(os.path.exists(export_service._content_path(started['export_id'])))

    def test_content_publish_requires_current_claim_and_commit_epoch(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3022,
            'review_item_id': 1022,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/fenced.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        claim = export_service._acquire_export_claim(started['export_id'])
        source_path = os.path.join(self._isolated_export_store.name, 'worker-result.mp4')
        with open(source_path, 'wb') as handle:
            handle.write(b'fenced-worker-result')
        with open(export_service._claim_path(started['export_id']), 'w', encoding='utf-8') as handle:
            json.dump({'token': 'replacement-worker'}, handle)

        self.assertIn(
            'claim_token',
            inspect.signature(export_service._persist_content_source).parameters,
        )
        with self.assertRaises(export_service.RecordExportClaimLostError):
            export_service._persist_content_source(
                started['export_id'], {'path': source_path}, claim_token=claim)
        self.assertFalse(os.path.exists(
            export_service._content_path(started['export_id'])))

        marker = {
            'exportId': started['export_id'],
            'fileHash': 'sha256:' + 'a' * 64,
            'manifestHash': 'sha256:' + 'b' * 64,
            'auditHeadHash': 'sha256:' + 'c' * 64,
            'claimEpoch': 2,
        }
        job = {
            'export_id': started['export_id'],
            'claim_epoch': 3,
            'tenant_id': '7',
            'storage_type': 's3',
            'storage_root': 's3://evidence/exports',
        }

        class MarkerAdapter:
            def open(self, _key):
                return io.BytesIO(json.dumps(marker).encode('utf-8'))

        with mock.patch.object(
                export_service, '_object_storage_adapter',
                return_value=MarkerAdapter()), mock.patch.object(
                export_service, '_get_export_audit',
                return_value=[{'entryHash': marker['auditHeadHash']}]):
            with self.assertRaisesRegex(RuntimeError, 'stale or mismatched'):
                export_service._verify_object_commit_marker(job, {
                    'fileHash': marker['fileHash'],
                    'manifestHash': marker['manifestHash'],
                })

    def test_queue_reclaims_stale_verifying_job(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 3021,
            'review_item_id': 1021,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/verifying.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        job = export_service._get_export_job(started['export_id'])
        job['status'] = 'verifying'
        export_service._persist_job(job)
        claim_path = export_service._claim_path(started['export_id'])
        os.makedirs(os.path.dirname(claim_path), exist_ok=True)
        with open(claim_path, 'w', encoding='utf-8') as handle:
            json.dump({'token': 'crashed-worker'}, handle)
        old = time.time() - 120
        os.utime(claim_path, (old, old))
        original_worker = export_service._default_export_worker
        export_service._default_export_worker = lambda queued: _provenance_worker_result(
            queued, b'reclaimed-verifying')
        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_CLAIM_STALE_SECONDS': '1',
        }, clear=False):
            try:
                processed = export_service.process_record_export_queue(limit=1)
            finally:
                export_service._default_export_worker = original_worker

        self.assertEqual('ready', processed[0]['status'])

    def test_object_download_verifies_manifest_hash_before_returning_bytes(self):
        import app.services.record_export_service as export_service

        class MemoryStorage:
            def __init__(self):
                self.objects = {}

            def put_file(self, key, path, content_type=None):
                with open(path, 'rb') as handle:
                    self.objects[key] = handle.read()

            def stat(self, key):
                return {'size': len(self.objects[key])}

            def open(self, key):
                return io.BytesIO(self.objects[key])

            def delete(self, key):
                self.objects.pop(key, None)

            def uri(self, key):
                return 's3://evidence/' + key

        adapter = MemoryStorage()
        storage_env = mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 's3',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://evidence/exports',
        }, clear=False)
        storage_env.start()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            started = export_service.create_record_export({
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/hash.mp4',
                'storage_type': 's3',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(job, b'original-media'))
            ready = export_service.poll_record_export(started['export_id'])
            content_key = (
                f'tenants/7/exports/{ready["export_id"]}/content.bin')
            original = adapter.objects[content_key]
            adapter.objects[content_key] = bytes([original[0] ^ 1]) + original[1:]

            with self.assertRaisesRegex(
                    export_service.RecordExportIntegrityError, 'hash mismatch'):
                export_service.download_record_export(ready['export_id'])
        finally:
            export_service.configure_record_export_storage_adapter(None)
            storage_env.stop()

    def test_object_download_rejects_stat_size_mismatch_as_integrity_error(self):
        import app.services.record_export_service as export_service

        class MemoryStorage:
            def __init__(self):
                self.objects = {}

            def put_file(self, key, path, content_type=None):
                with open(path, 'rb') as handle:
                    self.objects[key] = handle.read()

            def stat(self, key):
                return {'size': len(self.objects[key])}

            def open(self, key):
                return io.BytesIO(self.objects[key])

            def delete(self, key):
                self.objects.pop(key, None)

            def uri(self, key):
                return 's3://evidence/' + key

        adapter = MemoryStorage()
        storage_env = mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 'minio',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://evidence/exports',
        }, clear=False)
        storage_env.start()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            started = export_service.create_record_export({
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/size.mp4',
                'storage_type': 'minio',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(
                   job, b'original-media'))
            ready = export_service.poll_record_export(started['export_id'])
            content_key = f'tenants/7/exports/{ready["export_id"]}/content.bin'
            adapter.objects[content_key] += b'!'

            with self.assertRaisesRegex(
                    export_service.RecordExportIntegrityError, 'size mismatch'):
                export_service.download_record_export(ready['export_id'])
        finally:
            export_service.configure_record_export_storage_adapter(None)
            storage_env.stop()

    def test_cleanup_delete_failure_is_retryable_and_does_not_mark_expired(self):
        import app.services.record_export_service as export_service

        class DeletableStorage:
            def __init__(self):
                self.objects = {}
                self.fail_delete = True

            def put_file(self, key, path, content_type=None):
                with open(path, 'rb') as handle:
                    self.objects[key] = handle.read()

            def stat(self, key):
                return {'size': len(self.objects[key])}

            def open(self, key):
                return io.BytesIO(self.objects[key])

            def delete(self, key):
                if self.fail_delete and '/.staging/' not in key \
                        and key.endswith('/content.bin'):
                    raise RuntimeError('object store delete unavailable')
                self.objects.pop(key, None)

            def uri(self, key):
                return 's3://evidence/' + key

        adapter = DeletableStorage()
        storage_env = mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 's3',
            'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://evidence/exports',
        }, clear=False)
        storage_env.start()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        try:
            started = export_service.create_record_export({
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'tenant_id': '7',
                'record_uri': '/video/record/space/7/video/live/camera-01/cleanup.mp4',
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(job, b'expiring-media'))
            ready = export_service.poll_record_export(started['export_id'])
            job = export_service._get_export_job(ready['export_id'])
            job['expires_at'] = '2026-07-01T00:00:00Z'
            export_service._persist_job(job)

            first = export_service.cleanup_expired_record_exports(
                datetime(2026, 7, 2, 0, 0, 0))
            self.assertEqual([], first)
            self.assertNotEqual(
                'expired', export_service._reload_export_job(ready['export_id'])['status'])
            adapter.fail_delete = False
            second = export_service.cleanup_expired_record_exports(
                datetime(2026, 7, 2, 0, 0, 0))
            self.assertEqual([ready['export_id']], second)
            self.assertEqual(
                'expired', export_service._reload_export_job(ready['export_id'])['status'])
        finally:
            export_service.configure_record_export_storage_adapter(None)
            storage_env.stop()

    def test_export_limits_reject_segment_duration_input_and_output_abuse(self):
        import app.services.record_export_service as export_service

        uri = '/video/record/space/7/video/live/camera-01/limit.mp4'
        with mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_MAX_SEGMENTS': '1',
            'YFEIEYE_RECORD_EXPORT_MAX_TOTAL_DURATION_SECONDS': '5',
            'YFEIEYE_RECORD_EXPORT_MAX_OUTPUT_BYTES': '8',
        }, clear=False):
            with self.assertRaisesRegex(ValueError, 'segment count'):
                export_service.validate_record_export_request({
                    'camera_id': 'camera-01', 'device_id': 'camera-01',
                    'record_uri': uri, 'record_uris': [uri, uri + '.2'],
                }, 'camera-01', _trusted_record_resolver)
            with self.assertRaisesRegex(ValueError, 'total duration'):
                export_service.validate_record_export_request({
                    'camera_id': 'camera-01', 'device_id': 'camera-01',
                    'record_uri': uri,
                    'start_time': '2026-07-10T10:00:00',
                    'end_time': '2026-07-10T10:00:10',
                }, 'camera-01', _trusted_record_resolver)
            started = export_service.create_record_export({
                'camera_id': 'camera-01', 'device_id': 'camera-01', 'record_uri': uri,
            }, record_resolver=_trusted_record_resolver, async_worker=True,
               worker_runner=lambda job: _provenance_worker_result(job, b'0123456789'))
            failed = export_service.poll_record_export(started['export_id'])
            self.assertEqual('failed', failed['status'])
            self.assertIn('output size', failed['last_error'])

    def test_record_source_and_worker_output_use_streamed_files_instead_of_bytes_buffers(self):
        import app.services.record_export_service as export_service

        original_video_service = sys.modules.get('app.services.record_video_service')
        streamed_service = types.ModuleType('app.services.record_video_service')

        def materialize(space_id, object_name, destination, max_bytes=None,
                        tenant_id=None):
            self.assertEqual(7, space_id)
            self.assertEqual('live/camera-01/source.mp4', object_name)
            self.assertEqual(7, tenant_id)
            with open(destination, 'wb') as handle:
                handle.write(b'streamed-record-source')
            return {
                'path': destination,
                'content_type': 'video/mp4',
                'filename': 'source.mp4',
                'size_bytes': len(b'streamed-record-source'),
            }

        streamed_service.materialize_record_video = materialize
        streamed_service.get_record_video = lambda *args: self.fail(
            'export source must not use whole-file get_record_video')
        sys.modules['app.services.record_video_service'] = streamed_service
        try:
            with tempfile.TemporaryDirectory() as work_dir:
                sources = export_service._materialize_record_sources({
                    'record_uri': (
                        '/video/record/space/7/video/live/camera-01/source.mp4'),
                    'camera_id': 'camera-01',
                    'tenant_id': '7',
                }, work_dir)
                with open(sources[0]['path'], 'rb') as source_file:
                    self.assertEqual(b'streamed-record-source', source_file.read())

                worker_path = os.path.join(work_dir, 'worker-output.mp4')
                with open(worker_path, 'wb') as handle:
                    handle.write(b'streamed-worker-output')
                result = _provenance_worker_result(
                    {'export_id': 'stream-output', 'record_uri': sources[0]['path']},
                    b'placeholder')
                result.pop('content')
                result['content_path'] = worker_path
                content_source, _segments = export_service._validated_worker_result(
                    {'record_uri': sources[0]['path']}, result)
                self.assertEqual(worker_path, content_source['path'])
                self.assertNotIn('content', content_source)
        finally:
            if original_video_service is None:
                sys.modules.pop('app.services.record_video_service', None)
            else:
                sys.modules['app.services.record_video_service'] = original_video_service

    def test_worker_rechecks_segment_count_and_absolute_output_duration_limits(self):
        import app.services.record_export_service as export_service

        with tempfile.TemporaryDirectory() as work_dir, mock.patch.dict(os.environ, {
            'YFEIEYE_RECORD_EXPORT_MAX_SEGMENTS': '1',
            'YFEIEYE_RECORD_EXPORT_MAX_TOTAL_DURATION_SECONDS': '5',
        }, clear=False):
            first = os.path.join(work_dir, 'first.mp4')
            second = os.path.join(work_dir, 'second.mp4')
            for path in (first, second):
                with open(path, 'wb') as handle:
                    handle.write(b'video')
            materialized_dir = os.path.join(work_dir, 'materialized')
            os.makedirs(materialized_dir)
            with self.assertRaisesRegex(RuntimeError, 'segment count'):
                export_service._materialize_record_sources({
                    'record_uris': [first, second],
                }, materialized_dir)
            with self.assertRaisesRegex(RuntimeError, 'configured maximum'):
                export_service._validate_probed_duration(6.0, None)

    def test_duration_validation_rejects_truncated_and_overlong_output(self):
        import app.services.record_export_service as export_service

        with self.assertRaisesRegex(RuntimeError, 'shorter'):
            export_service._validate_probed_duration(1.0, 10.0)
        with self.assertRaisesRegex(RuntimeError, 'exceeds'):
            export_service._validate_probed_duration(12.0, 10.0)
        export_service._validate_probed_duration(9.5, 10.0)

    def test_short_hmac_keys_and_tampered_audit_chains_are_rejected(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_RECORD_EXPORT_HMAC_SECRET': 'short',
            'YFEIEYE_RECORD_EXPORT_KEY_ID': 'current',
        }, clear=False):
            os.environ.pop('YFEIEYE_RECORD_EXPORT_HMAC_KEYS', None)
            os.environ.pop('YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID', None)
            with self.assertRaisesRegex(RuntimeError, 'at least'):
                export_service.validate_record_export_signing_configuration()

        started = export_service.create_record_export({
            'camera_id': 'camera-01', 'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/audit-tamper.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        audit_path = export_service._audit_path(started['export_id'])
        with open(audit_path, 'r', encoding='utf-8') as handle:
            audit = json.load(handle)
        audit[0]['reason'] = 'tampered'
        with open(audit_path, 'w', encoding='utf-8') as handle:
            json.dump(audit, handle)
        with self.assertRaisesRegex(RuntimeError, 'audit hash chain'):
            export_service.get_record_export_audit(started['export_id'])

    def test_audit_chain_rejects_missing_hash_non_object_and_invalid_json(self):
        import app.services.record_export_service as export_service

        def create_job(item_id):
            return export_service.create_record_export({
                'review_case_id': 'audit-strict',
                'review_item_id': item_id,
                'camera_id': 'camera-01',
                'device_id': 'camera-01',
                'record_uri': (
                    f'/video/record/space/7/video/live/camera-01/{item_id}.mp4'),
            }, record_resolver=_trusted_record_resolver, async_worker=True)

        missing_hash = create_job('missing-hash')
        audit_path = export_service._audit_path(missing_hash['export_id'])
        with open(audit_path, 'r', encoding='utf-8') as handle:
            audit = json.load(handle)
        audit[0].pop('entryHash')
        with open(audit_path, 'w', encoding='utf-8') as handle:
            json.dump(audit, handle)
        with self.assertRaisesRegex(RuntimeError, 'entry hash'):
            export_service.get_record_export_audit(missing_hash['export_id'])

        non_object = create_job('non-object')
        audit_path = export_service._audit_path(non_object['export_id'])
        with open(audit_path, 'r', encoding='utf-8') as handle:
            audit = json.load(handle)
        audit.append('not-an-audit-entry')
        with open(audit_path, 'w', encoding='utf-8') as handle:
            json.dump(audit, handle)
        with self.assertRaisesRegex(RuntimeError, 'entry must be an object'):
            export_service.get_record_export_audit(non_object['export_id'])

        invalid_json = create_job('invalid-json')
        audit_path = export_service._audit_path(invalid_json['export_id'])
        with open(audit_path, 'w', encoding='utf-8') as handle:
            handle.write('{broken-json')
        with self.assertRaisesRegex(RuntimeError, 'invalid JSON'):
            export_service.get_record_export_audit(invalid_json['export_id'])

    def test_ready_object_metadata_sync_and_commit_remain_inside_audit_lock(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 'audit-object-lock',
            'review_item_id': 'item',
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'tenant_id': '7',
            'record_uri': '/video/record/space/7/video/live/camera-01/lock.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        job = export_service._get_export_job(started['export_id'])
        job['status'] = 'ready'
        job['storage_type'] = 's3'
        job['storage_root'] = 's3://evidence/exports'
        export_service._persist_job(job)
        observed = []

        def observe(label):
            observed.append((
                label,
                os.path.exists(export_service._audit_lock_path(started['export_id'])),
            ))
            return [] if label == 'sync' else {}

        storage_adapter = types.SimpleNamespace(
            uri=lambda key: 's3://evidence/' + key)
        with mock.patch.object(
                export_service, '_object_storage_adapter',
                return_value=storage_adapter), mock.patch.object(
                export_service, '_sync_object_storage_artifacts',
                side_effect=lambda *args, **kwargs: observe('sync')), mock.patch.object(
                export_service, '_publish_object_commit_marker',
                side_effect=lambda *args, **kwargs: observe('commit')):
            export_service.append_record_export_access_audit(
                started['export_id'], 'allowed', user_id='operator',
                camera_id='camera-01', action='manifest_verify')

        self.assertEqual([('sync', True), ('commit', True)], observed)

    def test_ready_commit_check_waits_for_access_audit_metadata_commit(self):
        import app.services.record_export_service as export_service

        class MemoryStorage:
            def __init__(self):
                self.objects = {}

            def put_file(self, key, path, content_type=None):
                with open(path, 'rb') as handle:
                    self.objects[key] = handle.read()

            def stat(self, key):
                return {'size': len(self.objects[key])}

            def open(self, key):
                return io.BytesIO(self.objects[key])

            def delete(self, key):
                self.objects.pop(key, None)

            def uri(self, key):
                return 's3://evidence/' + key

        adapter = MemoryStorage()
        export_service.configure_record_export_storage_adapter(lambda _job: adapter)
        audit_sync_started = threading.Event()
        release_audit_sync = threading.Event()
        audit_errors = []
        check_errors = []
        check_results = []
        check_started = threading.Event()
        try:
            with mock.patch.dict(os.environ, {
                'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE': 's3',
                'YFEIEYE_RECORD_EXPORT_STORAGE_URI': 's3://evidence/exports',
            }, clear=False):
                started = export_service.create_record_export({
                    'review_case_id': 'ready-audit-race',
                    'review_item_id': 'item',
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'tenant_id': '7',
                    'record_uri': (
                        '/video/record/space/7/video/live/camera-01/race.mp4'),
                }, record_resolver=_trusted_record_resolver, async_worker=True,
                   worker_runner=lambda job: _provenance_worker_result(
                       job, b'object-storage-export'))
                ready = export_service.poll_record_export(started['export_id'])
                self.assertEqual('ready', ready['status'])

                original_sync = export_service._sync_object_storage_artifacts

                def pause_access_audit_sync(job, *args, **kwargs):
                    if kwargs.get('metadata_only'):
                        audit_sync_started.set()
                        if not release_audit_sync.wait(2):
                            raise RuntimeError('test timed out releasing access audit sync')
                    return original_sync(job, *args, **kwargs)

                def append_access_audit():
                    try:
                        export_service.append_record_export_access_audit(
                            started['export_id'], 'allowed', user_id='operator',
                            tenant_id='7', camera_id='camera-01', action='export')
                    except Exception as exc:  # pragma: no cover - asserted below
                        audit_errors.append(exc)

                def check_ready_commit():
                    try:
                        check_started.set()
                        job = export_service._reload_export_job(started['export_id'])
                        check_results.append(export_service._ready_job_is_committed(job))
                    except Exception as exc:  # pragma: no cover - asserted below
                        check_errors.append(exc)

                with mock.patch.object(
                        export_service, '_sync_object_storage_artifacts',
                        side_effect=pause_access_audit_sync):
                    audit_thread = threading.Thread(
                        target=append_access_audit, name='access-audit')
                    audit_thread.start()
                    self.assertTrue(audit_sync_started.wait(2))

                    check_thread = threading.Thread(
                        target=check_ready_commit, name='ready-commit-check')
                    check_thread.start()
                    self.assertTrue(check_started.wait(2))
                    check_thread.join(timeout=0.2)
                    check_waited_for_commit = check_thread.is_alive()

                    release_audit_sync.set()
                    audit_thread.join(timeout=2)
                    check_thread.join(timeout=2)

                self.assertTrue(check_waited_for_commit)
                self.assertFalse(audit_thread.is_alive())
                self.assertFalse(check_thread.is_alive())
                self.assertEqual([], audit_errors)
                self.assertEqual([], check_errors)
                self.assertEqual([True], check_results)
        finally:
            release_audit_sync.set()
            export_service.configure_record_export_storage_adapter(None)

    def test_ready_commit_check_treats_unavailable_audit_lock_as_uncommitted(self):
        import app.services.record_export_service as export_service

        with mock.patch.object(
                export_service,
                '_acquire_audit_lock',
                side_effect=RuntimeError('audit lock unavailable')):
            self.assertFalse(export_service._ready_job_is_committed({
                'export_id': 'ready-lock-unavailable',
                'storage_type': 's3',
            }))

    def test_manifest_and_audit_gets_share_the_cross_process_metadata_lock(self):
        import app.services.record_export_service as export_service

        started = export_service.create_record_export({
            'review_case_id': 'metadata-reader-lock',
            'review_item_id': 'item',
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/read-lock.mp4',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        observed = []
        original_validate = export_service._validate_manifest_integrity
        original_get_audit = export_service._get_export_audit

        def validate_with_observation(manifest):
            observed.append((
                'manifest',
                os.path.exists(export_service._audit_lock_path(started['export_id'])),
            ))
            return original_validate(manifest)

        def audit_with_observation(export_id):
            observed.append((
                'audit',
                os.path.exists(export_service._audit_lock_path(export_id)),
            ))
            return original_get_audit(export_id)

        with mock.patch.object(
                export_service, '_validate_manifest_integrity',
                side_effect=validate_with_observation):
            export_service.get_record_export_manifest(started['export_id'])
        with mock.patch.object(
                export_service, '_get_export_audit',
                side_effect=audit_with_observation):
            export_service.get_record_export_audit(started['export_id'])

        self.assertEqual([('manifest', True), ('audit', True)], observed)

    def test_persisted_object_storage_location_is_not_redirected_by_config_drift(self):
        import app.services.record_export_service as export_service

        class FakeClient:
            def __init__(self):
                self.deleted_policies = []

            def bucket_exists(self, bucket):
                return True

            def delete_bucket_policy(self, bucket):
                self.deleted_policies.append(bucket)

        fake_minio = types.ModuleType('minio')
        fake_client = FakeClient()
        fake_minio.Minio = lambda *args, **kwargs: fake_client
        original_minio = sys.modules.get('minio')
        sys.modules['minio'] = fake_minio
        export_service._STORAGE_ADAPTER_CACHE.clear()
        try:
            with mock.patch.dict(os.environ, {
                'YFEIEYE_RECORD_EXPORT_S3_ENDPOINT': '127.0.0.1:9000',
                'YFEIEYE_RECORD_EXPORT_S3_ACCESS_KEY': 'access',
                'YFEIEYE_RECORD_EXPORT_S3_SECRET_KEY': 'secret',
                'YFEIEYE_RECORD_EXPORT_S3_BUCKET': 'changed-bucket',
                'YFEIEYE_RECORD_EXPORT_S3_PREFIX': 'changed-prefix',
            }, clear=False):
                adapter = export_service._object_storage_adapter({
                    'storage_type': 's3',
                    'storage_root': 's3://immutable-bucket/immutable-prefix',
                })
            self.assertEqual('immutable-bucket', adapter.bucket)
            self.assertEqual('immutable-prefix', adapter.prefix)
            self.assertEqual(['immutable-bucket'], fake_client.deleted_policies)
        finally:
            export_service._STORAGE_ADAPTER_CACHE.clear()
            if original_minio is None:
                sys.modules.pop('minio', None)
            else:
                sys.modules['minio'] = original_minio

    def test_default_store_is_persistent_data_directory(self):
        import app.services.record_export_service as export_service

        with mock.patch.dict(os.environ, {}, clear=False):
            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            self.assertEqual(
                os.path.normpath('/data/yfeieye-record-exports'),
                os.path.normpath(export_service._store_root()),
            )

    def test_real_ffmpeg_export_keeps_original_source_hash_after_download_audit(self):
        ffmpeg = shutil.which('ffmpeg')
        if not ffmpeg:
            self.skipTest('ffmpeg is required for real record export smoke')

        with tempfile.TemporaryDirectory() as work_dir:
            store_dir = os.path.join(work_dir, 'exports')
            source_path = os.path.join(work_dir, 'source.mp4')
            subprocess.run([
                ffmpeg,
                '-y',
                '-f',
                'lavfi',
                '-i',
                'testsrc=size=32x32:rate=1',
                '-t',
                '2',
                '-pix_fmt',
                'yuv420p',
                source_path,
            ], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            with open(source_path, 'rb') as source_file:
                original_source_hash = 'sha256:' + hashlib.sha256(source_file.read()).hexdigest()

            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = store_dir
            os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = work_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)

            started = export_service.create_record_export({
                'review_case_id': 3002,
                'review_item_id': 1002,
                'event_ids': [7502],
                'device_id': 'device-01',
                'camera_id': 'device-01',
                'source_alert_id': 'alert-export-real-ffmpeg',
                'segment_start_time': '2026-06-30T10:00:00',
                'segment_end_time': '2026-06-30T10:00:02',
                'start_time': '2026-06-30T10:00:00',
                'end_time': '2026-06-30T10:00:01',
                'record_uri': source_path,
                'format': 'mp4',
                'operator_user_id': '9004',
            }, record_resolver=_trusted_record_resolver, async_worker=True)
            ready = export_service.poll_record_export(started['export_id'])
            manifest = export_service.get_record_export_manifest(started['export_id'])

            self.assertEqual('ready', ready['status'])
            self.assertEqual('ffmpeg clipped and stitched evidence', ready['message'])
            self.assertTrue(ready['ffmpeg_command_hash'].startswith('sha256:'))
            self.assertTrue(ready['manifest_url'].endswith('/manifest'))
            self.assertTrue(ready['download_url'].endswith('/download'))
            self.assertGreater(os.path.getsize(manifest['files'][0]['path']), 0)
            self.assertEqual(original_source_hash, manifest['recordSegments'][0]['sourceHash'])
            self.assertTrue(manifest['recordSegments'][0]['ffmpegCommandHash'].startswith('sha256:'))
            self.assertEqual(0, manifest['recordSegments'][0]['stitchOrder'])
            self.assertEqual(0.0, manifest['recordSegments'][0]['clipParameters']['offsetSeconds'])
            self.assertEqual(1.0, manifest['recordSegments'][0]['clipParameters']['durationSeconds'])
            persisted_source_path = manifest['recordSegments'][0]['recordUri']
            self.assertTrue(os.path.isfile(persisted_source_path))
            self.assertTrue(any(file['role'] == 'source_record_segment'
                                and file['path'] == persisted_source_path
                                and file['hash'] == original_source_hash
                                for file in manifest['files']))

            with open(source_path, 'wb') as source_file:
                source_file.write(b'tampered-after-export')

            downloaded = export_service.download_record_export(
                started['export_id'],
                operator_user_id='9011',
                reason='source-retention-expired verification',
            )
            manifest_after_download = export_service.get_record_export_manifest(started['export_id'])

            self.assertEqual(ready['file_hash'], downloaded['file_hash'])
            self.assertEqual(
                original_source_hash,
                manifest_after_download['recordSegments'][0]['sourceHash'],
            )
            from app.services.record_export_manifest_verifier import verify_manifest
            self.assertTrue(verify_manifest(manifest_after_download)['valid'])
            self.assertTrue(any(record['operatorUserId'] == '9011'
                                for record in manifest_after_download['downloadRecords']))

            os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
            importlib.reload(export_service)

    def test_real_ffmpeg_export_materializes_minio_source_by_space_and_object(self):
        ffmpeg = shutil.which('ffmpeg')
        if not ffmpeg:
            self.skipTest('ffmpeg is required for real record export smoke')

        with tempfile.TemporaryDirectory() as work_dir:
            source_path = os.path.join(work_dir, 'source.mp4')
            subprocess.run([
                ffmpeg,
                '-y',
                '-f',
                'lavfi',
                '-i',
                'testsrc=size=32x32:rate=1',
                '-t',
                '2',
                '-pix_fmt',
                'yuv420p',
                source_path,
            ], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            with open(source_path, 'rb') as source_file:
                source_content = source_file.read()
            source_hash = 'sha256:' + hashlib.sha256(source_content).hexdigest()

            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = os.path.join(work_dir, 'exports')
            os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = work_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            loaded = []

            def load_record(space_id, object_name, destination, max_bytes=None,
                            tenant_id=None):
                self.assertEqual(7, tenant_id)
                loaded.append((space_id, object_name))
                with open(destination, 'xb') as handle:
                    handle.write(source_content)
                return {
                    'path': destination,
                    'content_type': 'video/mp4',
                    'filename': 'source.mp4',
                    'size_bytes': len(source_content),
                }

            module_name = 'app.services.record_video_service'
            original_module = sys.modules.get(module_name)
            video_service = types.ModuleType(module_name)
            video_service.materialize_record_video = load_record
            sys.modules[module_name] = video_service
            try:
                started = export_service.create_record_export({
                    'review_case_id': 3003,
                    'review_item_id': 1003,
                    'device_id': 'device-01',
                    'tenant_id': '7',
                    'space_id': 7,
                    'object_name': 'live/device-01/source.mp4',
                    'record_uri': '/video/record/space/7/video/live/device-01/source.mp4',
                    'segment_start_time': '2026-07-10T05:00:00',
                    'segment_end_time': '2026-07-10T05:00:02',
                    'start_time': '2026-07-10T05:00:00',
                    'end_time': '2026-07-10T05:00:01',
                    'format': 'mp4',
                }, record_resolver=_trusted_record_resolver, async_worker=True)

                ready = export_service.poll_record_export(started['export_id'])
                manifest = export_service.get_record_export_manifest(started['export_id'])

                self.assertEqual([(7, 'live/device-01/source.mp4')], loaded)
                self.assertEqual('ready', ready['status'])
                self.assertEqual('ffmpeg clipped and stitched evidence', ready['message'])
                self.assertEqual(source_hash, manifest['recordSegments'][0]['sourceHash'])
                self.assertTrue(manifest['recordSegments'][0]['ffmpegCommandHash'].startswith('sha256:'))
                self.assertTrue(os.path.isfile(manifest['recordSegments'][0]['recordUri']))
                self.assertEqual(
                    '/video/record/space/7/video/live/device-01/source.mp4',
                    manifest['recordSegments'][0]['originalRecordUri'],
                )
            finally:
                if original_module is None:
                    sys.modules.pop(module_name, None)
                else:
                    sys.modules[module_name] = original_module
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                importlib.reload(export_service)

    def test_real_ffmpeg_export_clips_and_stitches_segments_in_declared_order(self):
        ffmpeg = shutil.which('ffmpeg')
        if not ffmpeg:
            self.skipTest('ffmpeg is required for real record export smoke')

        with tempfile.TemporaryDirectory() as work_dir:
            source_paths = []
            for index, color in enumerate(('red', 'blue')):
                source_path = os.path.join(work_dir, f'source-{index}.mp4')
                subprocess.run([
                    ffmpeg,
                    '-y',
                    '-f',
                    'lavfi',
                    '-i',
                    f'color=c={color}:size=32x32:rate=2',
                    '-t',
                    '1',
                    '-pix_fmt',
                    'yuv420p',
                    source_path,
                ], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                source_paths.append(source_path)

            os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = os.path.join(work_dir, 'exports')
            os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = work_dir
            import app.services.record_export_service as export_service
            export_service = importlib.reload(export_service)
            try:
                started = export_service.create_record_export({
                    'review_case_id': 3004,
                    'review_item_id': 1004,
                    'device_id': 'device-01',
                    'camera_id': 'device-01',
                    'record_uri': source_paths[0],
                    'record_uris': source_paths,
                    'record_segments': [{
                        'recordUri': source_paths[0],
                        'segmentStartTime': '2026-07-10T05:00:00',
                        'segmentEndTime': '2026-07-10T05:00:01',
                        'clipStartTime': '2026-07-10T05:00:00',
                        'clipEndTime': '2026-07-10T05:00:01',
                    }, {
                        'recordUri': source_paths[1],
                        'segmentStartTime': '2026-07-10T05:00:01',
                        'segmentEndTime': '2026-07-10T05:00:02',
                        'clipStartTime': '2026-07-10T05:00:01',
                        'clipEndTime': '2026-07-10T05:00:02',
                    }],
                    'start_time': '2026-07-10T05:00:00',
                    'end_time': '2026-07-10T05:00:02',
                    'format': 'mp4',
                }, record_resolver=_trusted_record_resolver, async_worker=True)

                ready = export_service.poll_record_export(started['export_id'])
                manifest = export_service.get_record_export_manifest(started['export_id'])

                self.assertEqual('ready', ready['status'])
                self.assertGreater(ready['output_size_bytes'], 0)
                self.assertTrue(ready['ffmpeg_command_hash'].startswith('sha256:'))
                self.assertEqual([0, 1], [
                    segment['stitchOrder'] for segment in manifest['recordSegments']
                ])
                self.assertTrue(all(
                    segment['sourceHash'].startswith('sha256:')
                    and segment['ffmpegCommandHash'].startswith('sha256:')
                    and segment['clipParameters']['durationSeconds'] == 1.0
                    for segment in manifest['recordSegments']
                ))
            finally:
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                importlib.reload(export_service)


class TestRecordExportBlueprint(unittest.TestCase):
    def setUp(self):
        import app.blueprints as app_blueprints
        import app.services as app_services

        self._missing_module = object()
        self._isolated_module_names = (
            'models',
            'app.services.record_space_service',
            'app.services.record_video_service',
            'app.blueprints.record',
        )
        self._previous_modules = {
            name: sys.modules.get(name, self._missing_module)
            for name in self._isolated_module_names
        }
        self._previous_package_attributes = (
            (app_services, 'record_space_service',
             getattr(app_services, 'record_space_service', self._missing_module)),
            (app_services, 'record_video_service',
             getattr(app_services, 'record_video_service', self._missing_module)),
            (app_blueprints, 'record',
             getattr(app_blueprints, 'record', self._missing_module)),
        )
        self.addCleanup(self._restore_isolated_modules)

    def _restore_isolated_modules(self):
        for name, previous in self._previous_modules.items():
            if previous is self._missing_module:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
        for package, attribute, previous in self._previous_package_attributes:
            if previous is self._missing_module:
                try:
                    delattr(package, attribute)
                except AttributeError:
                    pass
            else:
                setattr(package, attribute, previous)

    def test_user_export_binds_identity_and_strips_sensitive_service_associations(self):
        record_module = self._import_record_blueprint_with_stubs()
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: {}
        from app.services.media_authorization_service import MediaAuthorizationDecision
        record_module.authorize_media_request = lambda *args, **kwargs: MediaAuthorizationDecision(
            True, 'trusted-user', 'trusted-tenant', 'camera-01', 'export',
            'granted', 200, 'user_token', None)
        captured = {}
        record_module.create_record_export = lambda payload, async_worker=False: (
            captured.update(payload) or {'export_id': 'safe-export', 'status': 'pending'})
        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        response = app.test_client().post('/video/record/export', json={
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/source.mp4',
            'operator_user_id': 'attacker',
            'approved_by': 'attacker-approver',
            'review_case_id': 'other-case',
            'review_item_ids': ['other-item'],
            'event_ids': ['other-event'],
            'snapshot_uris': ['/private/snapshot.jpg'],
            'source_alert_id': 'other-alert',
            'storage_type': 's3',
            'storage_root': 's3://attacker/private',
            'retention_days': 999,
            'expires_at': '2099-01-01T00:00:00Z',
        })

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual('trusted-user', captured['operator_user_id'])
        self.assertEqual('trusted-user', captured['approved_by'])
        self.assertEqual('trusted-tenant', captured['tenant_id'])
        for key in (
                'review_case_id', 'review_item_ids', 'event_ids', 'snapshot_uris',
                'source_alert_id', 'storage_type', 'storage_root', 'retention_days',
                'expires_at'):
            self.assertNotIn(key, captured)

    def test_service_hmac_export_may_supply_traceable_case_event_snapshot_associations(self):
        record_module = self._import_record_blueprint_with_stubs()
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: {}
        from app.services.media_authorization_service import MediaAuthorizationDecision
        record_module.authorize_media_request = lambda *args, **kwargs: MediaAuthorizationDecision(
            True, 'device-worker', 'tenant-1', 'camera-01', 'export',
            'service_granted', 200, 'service_hmac', 'iot-system')
        captured = {}
        record_module.create_record_export = lambda payload, async_worker=False: (
            captured.update(payload) or {'export_id': 'service-export', 'status': 'pending'})
        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        response = app.test_client().post('/video/record/export', json={
            'camera_id': 'camera-01', 'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/live/camera-01/source.mp4',
            'review_case_id': 'case-1', 'review_item_ids': ['item-1'],
            'event_ids': ['event-1'], 'snapshot_uris': ['snapshot-1.jpg'],
        })

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual('case-1', captured['review_case_id'])
        self.assertEqual(['event-1'], captured['event_ids'])
        self.assertEqual(['snapshot-1.jpg'], captured['snapshot_uris'])
        self.assertEqual('device-worker', captured['operator_user_id'])
        self.assertEqual('device-worker', captured['approved_by'])

    def test_record_export_route_posts_to_service(self):
        record_module = self._import_record_blueprint_with_stubs()
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: {}

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        captured = {}

        def fake_create_record_export(payload, async_worker=False):
            captured.update(payload)
            captured['_async_worker'] = async_worker
            return {
                'export_id': 'review-3000-1000-test',
                'download_url': '/video/alert/record?path=%2Fdata%2Fclip.flv',
                'status': 'ready',
                'message': 'using existing record',
            }

        original = getattr(record_module, 'create_record_export', None)
        record_module.create_record_export = fake_create_record_export
        try:
            response = app.test_client().post('/video/record/export', json={
                'review_case_id': 3000,
                'review_item_id': 1000,
                'device_id': 'camera-01',
                'camera_id': 'camera-01',
                'source_alert_id': 'alert-export-001',
                'start_time': '2026-06-30T10:10',
                'end_time': '2026-06-30T10:12',
                'record_uri': '/data/clip.flv',
                'format': 'mp4',
            })
        finally:
            if original is None:
                delattr(record_module, 'create_record_export')
            else:
                record_module.create_record_export = original

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual('review-3000-1000-test', body['data']['export_id'])
        self.assertEqual('camera-01', captured['device_id'])
        self.assertEqual('alert-export-001', captured['source_alert_id'])
        self.assertTrue(captured['_async_worker'])

    def test_record_export_status_route_polls_worker_job(self):
        record_module = self._import_record_blueprint_with_stubs()

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        def fake_get_record_export_status(export_id):
            return {
                'export_id': export_id,
                'status': 'ready',
                'download_url': f'/video/record/export/{export_id}/download',
                'file_hash': 'sha256:test',
            }

        original_status = getattr(record_module, 'get_record_export_status', None)
        original_poll = getattr(record_module, 'poll_record_export', None)
        record_module.get_record_export_status = fake_get_record_export_status
        record_module.poll_record_export = lambda export_id: (_ for _ in ()).throw(
            AssertionError('status GET must not execute ffmpeg worker'))
        try:
            response = app.test_client().get(
                '/video/record/export/review-3000-1000-test?camera_id=camera-01'
            )
        finally:
            if original_status is None:
                delattr(record_module, 'get_record_export_status')
            else:
                record_module.get_record_export_status = original_status
            if original_poll is None:
                delattr(record_module, 'poll_record_export')
            else:
                record_module.poll_record_export = original_poll

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual('ready', body['data']['status'])
        self.assertEqual('sha256:test', body['data']['file_hash'])

    def test_record_export_retry_route_requeues_failed_job(self):
        record_module = self._import_record_blueprint_with_stubs()

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        def fake_retry_record_export(export_id):
            return {
                'export_id': export_id,
                'status': 'pending',
                'retry_count': 1,
            }

        original = getattr(record_module, 'retry_record_export', None)
        record_module.retry_record_export = fake_retry_record_export
        try:
            response = app.test_client().post(
                '/video/record/export/review-3000-1000-test/retry?camera_id=camera-01'
            )
        finally:
            if original is None:
                delattr(record_module, 'retry_record_export')
            else:
                record_module.retry_record_export = original

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual('pending', body['data']['status'])
        self.assertEqual(1, body['data']['retry_count'])

    def test_record_export_audit_route_lists_job_audit_entries(self):
        record_module = self._import_record_blueprint_with_stubs()

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        def fake_get_record_export_audit(export_id):
            return [{
                'export_id': export_id,
                'action': 'downloaded',
                'operator_user_id': '9004',
                'reason': 'handoff',
            }]

        original = getattr(record_module, 'get_record_export_audit', None)
        record_module.get_record_export_audit = fake_get_record_export_audit
        try:
            response = app.test_client().get(
                '/video/record/export/review-3000-1000-test/audit?camera_id=camera-01'
            )
        finally:
            if original is None:
                delattr(record_module, 'get_record_export_audit')
            else:
                record_module.get_record_export_audit = original

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual('downloaded', body['data'][0]['action'])
        self.assertEqual('9004', body['data'][0]['operator_user_id'])

    def test_record_export_manifest_route_returns_persistent_evidence_manifest(self):
        record_module = self._import_record_blueprint_with_stubs()

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        def fake_get_record_export_manifest(export_id):
            return {
                'exportId': export_id,
                'reviewCaseId': '3000',
                'reviewItemIds': ['1000'],
                'eventIds': ['7500'],
                'fileHash': 'sha256:test',
                'cameraId': 'camera-01',
                'tenantId': '1',
            }

        original = getattr(record_module, 'get_record_export_manifest', None)
        record_module.get_record_export_manifest = fake_get_record_export_manifest
        try:
            response = app.test_client().get(
                '/video/record/export/review-3000-1000-test/manifest?camera_id=camera-01'
            )
        finally:
            if original is None:
                delattr(record_module, 'get_record_export_manifest')
            else:
                record_module.get_record_export_manifest = original

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual('3000', body['data']['reviewCaseId'])
        self.assertEqual(['7500'], body['data']['eventIds'])

    def test_record_export_download_streams_object_response_without_bytesio_buffer(self):
        record_module = self._import_record_blueprint_with_stubs()
        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        class ClosingStream(io.BytesIO):
            released = False

            def release_conn(self):
                self.released = True

        stream = ClosingStream(b'streamed-object-export')
        record_module.download_record_export = lambda *args, **kwargs: {
            'filename': 'evidence.mp4',
            'stream': stream,
            'content_length': len(b'streamed-object-export'),
            'mimetype': 'video/mp4',
        }

        response = app.test_client().get(
            '/video/record/export/review-3000-1000-test/download?camera_id=camera-01'
        )

        self.assertEqual(200, response.status_code)
        self.assertEqual(b'streamed-object-export', response.data)
        self.assertEqual('22', response.headers['Content-Length'])
        self.assertIn('evidence.mp4', response.headers['Content-Disposition'])
        self.assertTrue(stream.closed)
        self.assertTrue(stream.released)

    def test_record_video_seekable_mp4_requires_exact_camera_object_metadata_and_supports_range(self):
        record_module = self._import_record_blueprint_with_stubs()
        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        class Query:
            filters = None

            def filter_by(self, **filters):
                Query.filters = filters
                return self

            def first(self):
                return types.SimpleNamespace(
                    id=99, file_size=14, etag='source-etag')

        sys.modules['models'].RecordFile = types.SimpleNamespace(query=Query())
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id, device_id='camera-01', tenant_id=1)
        record_module.get_record_video = lambda *args: self.fail(
            'seekable playback must not buffer the whole record in memory')
        def fake_materialize(
                space_id, object_name, destination, max_bytes=None, tenant_id=None):
            self.assertEqual(1, tenant_id)
            with open(destination, 'xb') as source_file:
                source_file.write(b'raw-flv-source')
            return {'path': destination, 'content_type': 'video/x-flv'}
        record_module.materialize_record_video = fake_materialize
        with tempfile.TemporaryDirectory() as work_dir:
            output_path = os.path.join(work_dir, 'seekable.mp4')
            with open(output_path, 'wb') as output_file:
                output_file.write(b'0123456789')

            def fake_prepare(source_path=None, acquire_lease=False, **source):
                self.assertIsNone(source_path)
                self.assertTrue(acquire_lease)
                self.assertEqual(14, source['source_size_bytes'])
                self.assertEqual(
                    'tenant:1:space:7:object:live/camera-01/clip.flv:'
                    'etag:source-etag:size:14',
                    source['source_identity'],
                )
                materialized_path = os.path.join(work_dir, 'materialized.flv')
                source['materialize_source'](materialized_path)
                with open(materialized_path, 'rb') as source_file:
                    self.assertEqual(b'raw-flv-source', source_file.read())
                return {
                    'path': output_path,
                    'content_type': 'video/mp4',
                    'lease': {'path': output_path + '.lease.test', 'token': 'test'},
                }

            record_module.prepare_seekable_mp4_path = fake_prepare
            response = app.test_client().get(
                '/video/record/space/7/video/live/camera-01/clip.flv'
                '?playback_format=mp4',
                headers={'Range': 'bytes=2-5'},
            )
            status_code = response.status_code
            response_data = response.data
            response.close()

        self.assertEqual(206, status_code)
        self.assertEqual(b'2345', response_data)
        self.assertEqual({
            'tenant_id': 1,
            'space_id': 7,
            'device_id': 'camera-01',
            'object_name': 'live/camera-01/clip.flv',
        }, Query.filters)

    def test_record_video_rejects_object_not_owned_by_camera_metadata(self):
        record_module = self._import_record_blueprint_with_stubs()
        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        class Query:
            def filter_by(self, **filters):
                return self

            def first(self):
                return None

        sys.modules['models'].RecordFile = types.SimpleNamespace(query=Query())
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id, device_id='camera-01', tenant_id=1)
        record_module.get_record_video = lambda *args: (_ for _ in ()).throw(
            AssertionError('unowned object must not be read'))

        response = app.test_client().get(
            '/video/record/space/7/video/private/other-camera.flv'
            '?playback_format=mp4'
        )

        self.assertEqual(404, response.status_code)

    @staticmethod
    def _import_record_blueprint_with_stubs():
        db = types.SimpleNamespace(session=types.SimpleNamespace(rollback=lambda: None))
        sys.modules['models'] = types.SimpleNamespace(db=db)

        space_service = types.ModuleType('app.services.record_space_service')
        for name in (
            'create_record_space',
            'update_record_space',
            'delete_record_space',
            'get_record_space',
            'list_record_spaces',
            'list_record_space_authorization_scopes',
            'get_record_space_by_device_id',
            'sync_spaces_to_minio',
        ):
            setattr(space_service, name, lambda *args, **kwargs: None)
        sys.modules['app.services.record_space_service'] = space_service

        video_service = types.ModuleType('app.services.record_video_service')
        for name in (
            'list_record_videos',
            'delete_record_videos',
            'get_record_video',
            'materialize_record_video',
            'cleanup_old_videos_by_save_time',
            'sync_record_videos_metadata',
            'list_record_video_dates',
            'list_record_videos_day_detail',
            'find_segment_for_alert',
            'query_recording_availability',
            'inspect_recording_storage_drift',
        ):
            setattr(video_service, name, lambda *args, **kwargs: None)
        video_service.create_record_export = lambda payload: {
            'export_id': 'stub',
            'download_url': '/stub',
            'status': 'ready',
        }
        sys.modules['app.services.record_video_service'] = video_service

        sys.modules.pop('app.blueprints.record', None)
        record_module = importlib.import_module('app.blueprints.record')
        from app.services.media_authorization_service import MediaAuthorizationDecision
        record_module.authorize_media_request = lambda req, action, camera_id=None, **kwargs: (
            MediaAuthorizationDecision(
                True,
                '9004',
                '1',
                str(camera_id) if camera_id is not None else None,
                action,
                'test_granted',
                200,
                'service_hmac',
                'iot-system',
            )
        )
        record_module.validate_record_export_request = lambda payload, camera_id: payload
        record_module.get_record_export_manifest = lambda export_id: {
            'exportId': export_id,
            'cameraId': 'camera-01',
            'tenantId': '1',
        }
        return record_module


if __name__ == '__main__':
    unittest.main()
