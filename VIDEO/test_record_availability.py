"""Recording availability contract tests."""
import importlib
import inspect
import json
import os
import sys
import tempfile
import types
import unittest
from datetime import datetime, timedelta, timezone

from flask import Flask


class _ModuleIsolationTestCase(unittest.TestCase):
    _ISOLATED_MODULE_NAMES = (
        'models',
        'minio',
        'minio.error',
        'cv2',
        'app.services.record_video_service',
        'app.services.alert_service',
        'app.services.record_space_service',
        'app.services.space_file_metadata_service',
        'app.services.dvr_device_resolver',
        'app.services.media_dvr_utils',
        'app.services.media_kafka_service',
        'app.services.playback_disk_guard_service',
        'app.services.dvr_upload_service',
        'app.services.snap_upload_service',
        'app.utils.minio_bucket_policy',
        'app.utils.service_urls',
        'app.blueprints.record',
    )

    def setUp(self):
        super().setUp()
        self._missing_module = object()
        self._previous_modules = {
            name: sys.modules.get(name, self._missing_module)
            for name in self._ISOLATED_MODULE_NAMES
        }
        self._previous_parent_attributes = []
        for name in self._ISOLATED_MODULE_NAMES:
            if '.' not in name:
                continue
            parent_name, attribute = name.rsplit('.', 1)
            parent = sys.modules.get(parent_name)
            if parent is not None:
                self._previous_parent_attributes.append((
                    parent,
                    attribute,
                    getattr(parent, attribute, self._missing_module),
                ))
        self.addCleanup(self._restore_isolated_modules)

    def _restore_isolated_modules(self):
        for name, previous in self._previous_modules.items():
            if previous is self._missing_module:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous
        for parent, attribute, previous in self._previous_parent_attributes:
            if previous is self._missing_module:
                try:
                    delattr(parent, attribute)
                except AttributeError:
                    pass
            else:
                setattr(parent, attribute, previous)


class TestRecordAvailabilityService(_ModuleIsolationTestCase):
    def test_record_metadata_file_uri_is_not_treated_as_local_media(self):
        service = self._import_record_video_service_with_stubs()
        record = types.SimpleNamespace(
            url='file:///etc/passwd',
            object_name='live/device-01/clip.flv',
        )

        self.assertEqual('', service._record_local_path(record))

    def test_record_cache_flush_failure_journal_survives_reload_and_resolves(self):
        with tempfile.TemporaryDirectory() as event_dir:
            previous = os.environ.get('YFEIEYE_RECORD_CACHE_EVENT_DIR')
            os.environ['YFEIEYE_RECORD_CACHE_EVENT_DIR'] = event_dir
            try:
                self.assertIsNotNone(
                    importlib.util.find_spec(
                        'app.services.record_cache_flush_event_service'),
                    'persistent cache flush event service must exist',
                )
                import app.services.record_cache_flush_event_service as cache_events
                cache_events = importlib.reload(cache_events)
                event = {
                    'event_id': 'dvr-event-1',
                    'tenant_id': 7,
                    'device_id': 'device-01',
                    'space_id': 7,
                    'file_path': '/data/playbacks/live/device-01/cache.ts',
                    'source': 'srs',
                }
                cache_events.record_cache_flush_failure(event, 'copy timeout')

                cache_events = importlib.reload(cache_events)
                failures = cache_events.list_record_cache_flush_failures(
                    tenant_id=7, space_id=7, device_id='device-01')
                self.assertEqual(1, len(failures))
                self.assertEqual('dvr-event-1', failures[0]['event_id'])
                self.assertEqual('copy timeout', failures[0]['error'])
                self.assertEqual(
                    '/data/playbacks/live/device-01/cache.ts',
                    failures[0]['cache_path'],
                )
                self.assertEqual([], cache_events.list_record_cache_flush_failures(
                    tenant_id=7, space_id=7, device_id='device-02'))

                enqueue_failure = {
                    'event_id': 'dvr-event-before-space-resolution',
                    'tenant_id': 7,
                    'device_id': 'device-01',
                    'file_path': '/data/playbacks/live/device-01/cache-2.ts',
                    'source': 'srs',
                }
                cache_events.record_cache_flush_failure(
                    enqueue_failure, 'kafka enqueue failed')
                scoped_failures = cache_events.list_record_cache_flush_failures(
                    tenant_id=7, space_id=7, device_id='device-01')
                self.assertEqual(2, len(scoped_failures))

                cache_events.resolve_record_cache_flush_failure(event)
                cache_events.resolve_record_cache_flush_failure(enqueue_failure)
                self.assertEqual([], cache_events.list_record_cache_flush_failures(
                    tenant_id=7, space_id=7, device_id='device-01'))
            finally:
                if previous is None:
                    os.environ.pop('YFEIEYE_RECORD_CACHE_EVENT_DIR', None)
                else:
                    os.environ['YFEIEYE_RECORD_CACHE_EVENT_DIR'] = previous

    def test_build_recording_availability_returns_available_missing_motion_and_export(self):
        service = self._import_record_video_service_with_stubs()

        record = types.SimpleNamespace(
            id=11,
            space_id=7,
            object_name='live/device-01/clip.mp4',
            url='',
            event_time=datetime(2026, 6, 30, 10, 0, 0),
            duration=60,
            source='dvr',
        )
        alert = types.SimpleNamespace(
            id=21,
            object='person',
            event='intrusion',
            region='gate',
            information='',
            time=datetime(2026, 6, 30, 10, 0, 30),
            device_id='device-01',
            device_name='camera-01',
            image_path='',
            image_url='',
            record_path='',
            task_type='realtime',
            task_id=None,
            task_name='',
            notify_users='',
            channels='',
            notification_sent=False,
            notification_sent_time=None,
            business_tags='',
            correlation_id='corr-001',
        )

        result = service.build_recording_availability(
            records=[record],
            alerts=[alert],
            space_id=7,
            device_id='device-01',
            camera_id='camera-01',
            begin_time=datetime(2026, 6, 30, 10, 0, 0),
            end_time=datetime(2026, 6, 30, 10, 2, 0),
        )

        self.assertEqual('device-01', result['device_id'])
        self.assertEqual('camera-01', result['camera_id'])
        self.assertEqual(2, len(result['segments']))
        self.assertEqual('motion', result['segments'][0]['status'])
        self.assertEqual('/video/record/space/7/video/live/device-01/clip.mp4', result['segments'][0]['play_url'])
        self.assertEqual('/video/record/export', result['segments'][0]['export_url'])
        self.assertEqual('continuous', result['segments'][0]['retain_mode'])
        self.assertEqual('alert', result['segments'][0]['coverage_source'])
        self.assertTrue(result['segments'][0]['exportable'])
        self.assertIsNone(result['segments'][0]['non_exportable_reason'])
        self.assertEqual(7, result['segments'][0]['export_payload']['space_id'])
        self.assertEqual(
            'live/device-01/clip.mp4',
            result['segments'][0]['export_payload']['object_name'],
        )
        self.assertEqual(1, result['segments'][0]['motion'])
        self.assertEqual(1, result['segments'][0]['object_count'])
        self.assertEqual('missing', result['segments'][1]['status'])
        self.assertFalse(result['segments'][1]['exportable'])
        self.assertEqual(
            result['segments'][1]['gap_reason'],
            result['segments'][1]['non_exportable_reason'],
        )
        self.assertEqual('2026-06-30T10:01:00', result['segments'][1]['start_time'])
        self.assertEqual('2026-06-30T10:02:00', result['segments'][1]['end_time'])
        self.assertEqual(1, len(result['available']))
        self.assertEqual(1, len(result['motion']))
        self.assertEqual(1, len(result['missing']))
        self.assertEqual(60, result['summary']['available_seconds'])
        self.assertEqual(60, result['summary']['missing_seconds'])
        self.assertEqual(1, result['summary']['object_count'])

    def test_missing_segments_classify_operational_gap_reasons(self):
        service = self._import_record_video_service_with_stubs()
        expected_categories = {
            'retention_expired': 'retention',
            'stream_interrupted': 'stream',
            'recording_disabled': 'configuration',
            'video_url_not_configured': 'configuration',
            'record_space_not_found': 'configuration',
            'file_missing': 'filesystem',
            'probe_failed': 'probe',
            'permission_denied': 'permission',
            'service_unavailable': 'service',
            'disk_full': 'storage',
            'cache_flush_failed': 'cache',
        }

        for reason_code, category in expected_categories.items():
            with self.subTest(reason_code=reason_code):
                result = service.build_recording_availability(
                    records=[],
                    alerts=[],
                    space_id=7,
                    device_id='device-01',
                    camera_id='camera-01',
                    begin_time=datetime(2026, 6, 30, 10, 0, 0),
                    end_time=datetime(2026, 6, 30, 10, 1, 0),
                    missing_reason=reason_code,
                )

                missing = result['missing'][0]
                self.assertEqual(reason_code, missing['gap_reason'])
                self.assertEqual(category, missing['gap_reason_category'])
                self.assertIn('retryable', missing)
                self.assertEqual(60, result['summary']['gap_reasons'][reason_code])

    def test_missing_segments_normalize_legacy_gap_reason_aliases(self):
        service = self._import_record_video_service_with_stubs()
        aliases = {
            'file_expired': 'retention_expired',
            'file-expired': 'retention_expired',
            'FILE EXPIRED': 'retention_expired',
            'VIDEO URL NOT CONFIGURED': 'video_url_not_configured',
        }

        for alias, canonical in aliases.items():
            with self.subTest(alias=alias):
                result = service.build_recording_availability(
                    records=[],
                    alerts=[],
                    space_id=7,
                    device_id='device-01',
                    camera_id='camera-01',
                    begin_time=datetime(2026, 6, 30, 10, 0, 0),
                    end_time=datetime(2026, 6, 30, 10, 1, 0),
                    missing_reason=alias,
                )

                missing = result['missing'][0]
                self.assertEqual(canonical, missing['gap_reason'])
                self.assertNotIn(alias, result['summary']['gap_reasons'])
                self.assertEqual({canonical: 60}, result['summary']['gap_reasons'])

    def test_availability_uses_review_overlap_retention_capture_and_probe_facts(self):
        service = self._import_record_video_service_with_stubs()

        valid_record = types.SimpleNamespace(
            id=31,
            space_id=7,
            object_name='live/device-01/valid.mp4',
            url='',
            event_time=datetime(2026, 6, 30, 10, 0, 0),
            duration=60,
            retention_mode='motion',
            pre_capture_seconds=5,
            post_capture_seconds=10,
            probe_result={
                'has_valid_video': True,
                'duration': 60,
                'codec': 'h264',
            },
        )
        corrupt_record = types.SimpleNamespace(
            id=32,
            space_id=7,
            object_name='live/device-01/corrupt.mp4',
            url='',
            event_time=datetime(2026, 6, 30, 10, 1, 20),
            duration=30,
            retention_mode='alerts',
            pre_capture_seconds=5,
            post_capture_seconds=5,
            probe_result={
                'has_valid_video': False,
                'error': 'corrupt_segment',
            },
        )

        result = service.build_recording_availability(
            records=[valid_record, corrupt_record],
            alerts=[],
            space_id=7,
            device_id='device-01',
            camera_id='camera-01',
            begin_time=datetime(2026, 6, 30, 9, 59, 55),
            end_time=datetime(2026, 6, 30, 10, 2, 0),
        )

        self.assertEqual(1, len(result['available']))
        self.assertEqual('2026-06-30T09:59:55', result['available'][0]['segment_start_time'])
        self.assertEqual('2026-06-30T10:01:10', result['available'][0]['segment_end_time'])
        self.assertEqual('motion', result['available'][0]['retention_mode'])
        self.assertEqual(5, result['available'][0]['pre_capture_seconds'])
        self.assertEqual(10, result['available'][0]['post_capture_seconds'])
        self.assertTrue(result['available'][0]['review_overlap'])
        self.assertEqual('h264', result['available'][0]['probe']['codec'])
        self.assertTrue(any(segment['gap_reason'] == 'corrupt_segment'
                            and segment['source'] == 'file_probe_failed'
                            and segment['probe']['error'] == 'corrupt_segment'
                            for segment in result['missing']))
        self.assertEqual('motion', result['summary']['retention_modes'][0])
        self.assertEqual(1, result['summary']['probe_failed_count'])

    def test_recording_storage_drift_patrol_reports_missing_expired_disk_and_cache_failures(self):
        service = self._import_record_video_service_with_stubs()

        with tempfile.TemporaryDirectory() as record_dir:
            previous_roots = os.environ.get('YFEIEYE_LOCAL_MEDIA_ROOTS')
            os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = record_dir
            service.minio_storage_enabled = lambda: False
            existing_path = os.path.join(record_dir, 'existing.mp4')
            with open(existing_path, 'wb') as file_obj:
                file_obj.write(b'recording-bytes')
            missing_path = os.path.join(record_dir, 'missing.mp4')
            now = datetime(2026, 7, 2, 12, 0, 0)
            records = [
                types.SimpleNamespace(
                    id=41,
                    space_id=7,
                    device_id='device-01',
                    object_name='live/device-01/existing.mp4',
                    url=existing_path,
                    event_time=now - timedelta(hours=1),
                    duration=60,
                ),
                types.SimpleNamespace(
                    id=42,
                    space_id=7,
                    device_id='device-01',
                    object_name='live/device-01/missing.mp4',
                    url=missing_path,
                    event_time=now - timedelta(hours=2),
                    duration=60,
                ),
                types.SimpleNamespace(
                    id=43,
                    space_id=7,
                    device_id='device-01',
                    object_name='live/device-01/expired.mp4',
                    url=existing_path,
                    event_time=now - timedelta(days=9),
                    duration=60,
                ),
            ]

            previous_roots = os.environ.get('YFEIEYE_LOCAL_MEDIA_ROOTS')
            os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = json.dumps([record_dir])
            try:
                report = service.inspect_recording_storage_drift(
                    records=records,
                    space_id=7,
                    device_id='device-01',
                    now=now,
                    retention_hours=24,
                    disk_probe={
                        'path': record_dir,
                        'total_bytes': 1000,
                        'free_bytes': 20,
                    },
                    cache_flush_events=[{
                        'device_id': 'device-01',
                        'cache_path': os.path.join(record_dir, 'cache.ts'),
                        'error': 'copy timeout',
                        'happened_at': '2026-07-02T11:59:00',
                    }],
                )
            finally:
                if previous_roots is None:
                    os.environ.pop('YFEIEYE_LOCAL_MEDIA_ROOTS', None)
                else:
                    os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = previous_roots

            self.assertEqual('device-01', report['device_id'])
            self.assertEqual(7, report['space_id'])
            self.assertEqual(3, report['summary']['record_count'])
            self.assertEqual(4, report['summary']['issue_count'])
            self.assertEqual(1, report['summary']['issue_reasons']['file_missing'])
            self.assertEqual(1, report['summary']['issue_reasons']['retention_expired'])
            self.assertEqual(1, report['summary']['issue_reasons']['disk_full'])
            self.assertEqual(1, report['summary']['issue_reasons']['cache_flush_failed'])
            self.assertEqual(
                [
                    'video_url_not_configured',
                    'record_space_not_found',
                    'file_missing',
                    'probe_failed',
                    'permission_denied',
                    'retention_expired',
                    'disk_full',
                    'cache_flush_failed',
                ],
                report['summary']['standard_reason_keys'],
            )
            self.assertTrue(any(issue['record_id'] == 42
                                and issue['reason'] == 'file_missing'
                                and issue['suggested_action'] == 'delete_db_metadata_after_review'
                                for issue in report['issues']))
            self.assertTrue(any(issue['record_id'] == 43
                                and issue['reason'] == 'retention_expired'
                                and issue['category'] == 'retention'
                                for issue in report['issues']))
            self.assertTrue(any(issue['reason'] == 'disk_full'
                                and issue['category'] == 'storage'
                                and issue['retryable']
                                for issue in report['issues']))
            self.assertTrue(any(issue['reason'] == 'cache_flush_failed'
                                and issue['source'] == 'record_cache'
                                and issue['detail']['error'] == 'copy timeout'
                                for issue in report['issues']))

    def test_recording_storage_drift_prefers_minio_object_probe_over_unmounted_local_path(self):
        service = self._import_record_video_service_with_stubs()
        probes = []
        service.minio_storage_enabled = lambda: True
        service.get_minio_client = lambda: types.SimpleNamespace(
            stat_object=lambda bucket_name, object_name: probes.append((bucket_name, object_name)),
        )
        record = types.SimpleNamespace(
            id=44,
            space_id=7,
            device_id='device-01',
            bucket_name='record-space',
            object_name='live/device-01/minio-only.flv',
            url='/data/not-mounted-on-video/minio-only.flv',
            event_time=datetime(2026, 7, 2, 12, 0, 0),
            duration=60,
        )

        report = service.inspect_recording_storage_drift(
            records=[record],
            space_id=7,
            device_id='device-01',
            now=datetime(2026, 7, 2, 13, 0, 0),
            retention_hours=24,
            disk_probe={'total_bytes': 1000, 'free_bytes': 900},
        )

        self.assertEqual([('record-space', 'live/device-01/minio-only.flv')], probes)
        self.assertEqual(0, report['summary']['issue_count'])
        self.assertTrue(report['summary']['healthy'])

    def test_recording_storage_drift_uses_bounded_cursor_query(self):
        service = self._import_record_video_service_with_stubs()
        captured = {}
        now = datetime(2026, 7, 11, 12, 0, 0)
        record = types.SimpleNamespace(
            id=52,
            space_id=7,
            device_id='device-01',
            bucket_name='record-space',
            object_name='live/device-01/clip.flv',
            url='/video/record/space/7/video/live/device-01/clip.flv',
            event_time=now - timedelta(hours=1),
            duration=60,
        )

        def fake_query(**kwargs):
            captured.update(kwargs)
            return [record], 'next-page-token', True

        service._query_records_for_drift = fake_query
        service._record_storage_probe = lambda _record: {'exists': True}

        report = service.inspect_recording_storage_drift(
            space_id=7,
            device_id='device-01',
            now=now,
            retention_hours=24,
            cursor='current-page-token',
            limit=25,
            disk_probe={'total_bytes': 1000, 'free_bytes': 900},
        )

        self.assertEqual(25, captured['limit'])
        self.assertEqual('current-page-token', captured['cursor'])
        self.assertEqual(now, captured['now'])
        self.assertEqual(24, captured['retention_hours'])
        self.assertEqual('next-page-token', report['pagination']['next_cursor'])
        self.assertTrue(report['pagination']['has_more'])
        self.assertEqual(25, report['pagination']['limit'])

    def test_recording_storage_drift_includes_missing_permanent_record_older_than_one_year(self):
        from flask_sqlalchemy import SQLAlchemy

        service = self._import_record_video_service_with_stubs()
        app = Flask(__name__)
        app.config.update(
            SQLALCHEMY_DATABASE_URI='sqlite:///:memory:',
            SQLALCHEMY_TRACK_MODIFICATIONS=False,
        )
        test_db = SQLAlchemy(app)

        class TestRecordSpace(test_db.Model):
            __tablename__ = 'test_record_space_drift'
            id = test_db.Column(test_db.Integer, primary_key=True)
            tenant_id = test_db.Column(test_db.Integer, nullable=False)
            save_time = test_db.Column(test_db.Integer, nullable=False)

        class TestRecordFile(test_db.Model):
            __tablename__ = 'test_record_file_drift'
            id = test_db.Column(test_db.Integer, primary_key=True)
            tenant_id = test_db.Column(test_db.Integer, nullable=False)
            space_id = test_db.Column(test_db.Integer, nullable=False)
            device_id = test_db.Column(test_db.String(100), nullable=False)
            object_name = test_db.Column(test_db.String(500), nullable=False)
            bucket_name = test_db.Column(test_db.String(255), nullable=False)
            url = test_db.Column(test_db.String(500), nullable=False)
            duration = test_db.Column(test_db.Integer)
            event_time = test_db.Column(test_db.DateTime, nullable=False)

        now = datetime(2026, 7, 11, 12, 0, 0)
        with app.app_context():
            test_db.create_all()
            test_db.session.add_all([
                TestRecordSpace(id=7, tenant_id=1, save_time=0),
                TestRecordSpace(id=8, tenant_id=1, save_time=24),
                TestRecordFile(
                    id=61,
                    tenant_id=1,
                    space_id=7,
                    device_id='camera-permanent',
                    object_name='tenants/1/live/camera-permanent/missing.flv',
                    bucket_name='record-space',
                    url='/missing/permanent.flv',
                    duration=60,
                    event_time=now - timedelta(days=500),
                ),
                TestRecordFile(
                    id=62,
                    tenant_id=1,
                    space_id=8,
                    device_id='camera-expiring',
                    object_name='tenants/1/live/camera-expiring/expired.flv',
                    bucket_name='record-space',
                    url='/missing/expired.flv',
                    duration=60,
                    event_time=now - timedelta(days=500),
                ),
            ])
            test_db.session.commit()

            service.RecordSpace = TestRecordSpace
            service.RecordFile = TestRecordFile
            service._record_storage_probe = lambda _record: {
                'exists': False,
                'reason': 'file_missing',
            }
            report = service.inspect_recording_storage_drift(
                tenant_id=1,
                now=now,
                limit=10,
                disk_probe={'total_bytes': 1000, 'free_bytes': 900},
            )

        self.assertEqual([61], [record['record_id'] for record in report['records']])
        self.assertEqual({'file_missing': 1}, report['summary']['issue_reasons'])

    def test_recording_storage_drift_classifies_minio_permission_and_outage(self):
        service = self._import_record_video_service_with_stubs()
        service.minio_storage_enabled = lambda: True
        now = datetime(2026, 7, 11, 12, 0, 0)
        record = types.SimpleNamespace(
            id=53,
            space_id=7,
            device_id='device-01',
            bucket_name='record-space',
            object_name='live/device-01/clip.flv',
            url='/video/record/space/7/video/live/device-01/clip.flv',
            event_time=now,
            duration=60,
        )

        denied = service.S3Error('denied')
        denied.code = 'AccessDenied'
        service.get_minio_client = lambda: types.SimpleNamespace(
            stat_object=lambda *_args: (_ for _ in ()).throw(denied))
        denied_report = service.inspect_recording_storage_drift(
            records=[record],
            space_id=7,
            device_id='device-01',
            now=now,
            disk_probe={'total_bytes': 1000, 'free_bytes': 900},
        )

        service.get_minio_client = lambda: types.SimpleNamespace(
            stat_object=lambda *_args: (_ for _ in ()).throw(TimeoutError('timed out')))
        outage_report = service.inspect_recording_storage_drift(
            records=[record],
            space_id=7,
            device_id='device-01',
            now=now,
            disk_probe={'total_bytes': 1000, 'free_bytes': 900},
        )

        self.assertEqual('permission_denied', denied_report['issues'][0]['reason'])
        self.assertTrue(denied_report['issues'][0]['retryable'] is False)
        self.assertEqual('service_unavailable', outage_report['issues'][0]['reason'])
        self.assertTrue(outage_report['issues'][0]['retryable'])

    def test_cache_flush_failure_page_applies_retention_limit_and_cursor(self):
        with tempfile.TemporaryDirectory() as event_dir:
            previous = os.environ.get('YFEIEYE_RECORD_CACHE_EVENT_DIR')
            os.environ['YFEIEYE_RECORD_CACHE_EVENT_DIR'] = event_dir
            try:
                import app.services.record_cache_flush_event_service as cache_events
                cache_events = importlib.reload(cache_events)
                fixtures = [
                    ('expired', '2026-07-01T00:00:00+00:00'),
                    ('newer-1', '2026-07-11T10:00:00+00:00'),
                    ('newer-2', '2026-07-11T11:00:00+00:00'),
                ]
                for identity, happened_at in fixtures:
                    with open(os.path.join(event_dir, identity + '.json'), 'w', encoding='utf-8') as handle:
                        json.dump({
                            'identity': identity,
                            'status': 'failed',
                            'tenant_id': 7,
                            'space_id': 7,
                            'device_id': 'device-01',
                            'happened_at': happened_at,
                        }, handle)

                first = cache_events.list_record_cache_flush_failures(
                    tenant_id=7,
                    space_id=7,
                    device_id='device-01',
                    limit=1,
                    retention_hours=24,
                    now=datetime(2026, 7, 11, 12, 0, tzinfo=timezone.utc),
                    return_page=True,
                )
                second = cache_events.list_record_cache_flush_failures(
                    tenant_id=7,
                    space_id=7,
                    device_id='device-01',
                    limit=1,
                    cursor=first['next_cursor'],
                    retention_hours=24,
                    now=datetime(2026, 7, 11, 12, 0, tzinfo=timezone.utc),
                    return_page=True,
                )

                self.assertEqual(['newer-1'], [event['identity'] for event in first['items']])
                self.assertTrue(first['has_more'])
                self.assertEqual(['newer-2'], [event['identity'] for event in second['items']])
                self.assertFalse(second['has_more'])
                self.assertFalse(os.path.exists(os.path.join(event_dir, 'expired.json')))
            finally:
                if previous is None:
                    os.environ.pop('YFEIEYE_RECORD_CACHE_EVENT_DIR', None)
                else:
                    os.environ['YFEIEYE_RECORD_CACHE_EVENT_DIR'] = previous

    @staticmethod
    def _import_record_video_service_with_stubs():
        sys.modules.pop('app.services.record_video_service', None)
        minio_module = types.ModuleType('minio')
        minio_error_module = types.ModuleType('minio.error')

        class S3Error(Exception):
            pass

        minio_error_module.S3Error = S3Error
        sys.modules['minio'] = minio_module
        sys.modules['minio.error'] = minio_error_module

        sys.modules['models'] = types.SimpleNamespace(
            db=types.SimpleNamespace(session=types.SimpleNamespace(rollback=lambda: None)),
            RecordSpace=types.SimpleNamespace(),
            RecordFile=types.SimpleNamespace(),
            Alert=types.SimpleNamespace(),
        )

        alert_service = types.ModuleType('app.services.alert_service')
        alert_service._alert_to_dict = lambda alert: {
            'id': getattr(alert, 'id', None),
            'object': getattr(alert, 'object', None),
            'event': getattr(alert, 'event', None),
            'region': getattr(alert, 'region', None),
            'time': getattr(alert, 'time', None).isoformat() if getattr(alert, 'time', None) else None,
        }
        sys.modules['app.services.alert_service'] = alert_service

        record_space_service = types.ModuleType('app.services.record_space_service')
        record_space_service.get_minio_client = lambda: None
        sys.modules['app.services.record_space_service'] = record_space_service

        metadata_service = types.ModuleType('app.services.space_file_metadata_service')
        metadata_service.delete_record_files_metadata = lambda *args, **kwargs: None
        metadata_service.sync_record_files_from_minio = lambda *args, **kwargs: None
        metadata_service.extract_prefix_from_url = lambda url: url
        metadata_service.require_mutable_tenant_object = lambda *args, **kwargs: None
        sys.modules['app.services.space_file_metadata_service'] = metadata_service

        return importlib.import_module('app.services.record_video_service')


class TestRecordCacheFlushIntegration(_ModuleIsolationTestCase):
    def test_legacy_dvr_metadata_remains_read_only_when_tenant_object_is_written(self):
        service = self._import_dvr_upload_service_with_stubs()
        source = inspect.getsource(service._process_dvr_event_impl)

        self.assertIn('legacy_rf = RecordFile.query.filter_by', source)
        self.assertLess(source.index('legacy_rf = RecordFile.query.filter_by'),
                        source.index('minio_client.fput_object'))
        self.assertNotIn('legacy_rf.object_name = object_name', source)
        self.assertNotIn('legacy_rf.url = file_path_url', source)
        self.assertNotIn("file_path_url = (existing_rf.url or '').strip()", source)

    def test_snap_upload_is_tenant_scoped_private_and_uses_protected_url(self):
        service = self._import_snap_upload_service_with_stubs()
        previous = os.environ.get('YFEIEYE_SNAPSHOT_TENANT_ID')
        os.environ['YFEIEYE_SNAPSHOT_TENANT_ID'] = '9'
        try:
            self.assertEqual(
                '6',
                service._resolve_snapshot_tenant_id(
                    {'tenant_id': '6'},
                    snap_space=types.SimpleNamespace(tenant_id='7'),
                ),
            )
            self.assertEqual(
                '7',
                service._resolve_snapshot_tenant_id(
                    {}, snap_space=types.SimpleNamespace(tenant_id='7')),
            )
            self.assertEqual(
                '9',
                service._resolve_snapshot_tenant_id(
                    {}, snap_space=types.SimpleNamespace()),
            )
            self.assertEqual(
                'tenants/1/cameras/device-01/20260711/frame.jpg',
                service._build_snapshot_object_name(
                    '1', 'device-01', '20260711/frame.jpg'),
            )
            self.assertEqual(
                '/video/snap/space/7/image/tenants/1/cameras/device-01/20260711/frame.jpg',
                service._protected_snapshot_url(
                    7, 'tenants/1/cameras/device-01/20260711/frame.jpg'),
            )
            self.assertIs(
                service.ensure_bucket_private,
                sys.modules['app.utils.minio_bucket_policy'].ensure_bucket_private,
            )
        finally:
            if previous is None:
                os.environ.pop('YFEIEYE_SNAPSHOT_TENANT_ID', None)
            else:
                os.environ['YFEIEYE_SNAPSHOT_TENANT_ID'] = previous

    def test_dvr_tenant_resolution_uses_event_model_then_environment(self):
        service = self._import_dvr_upload_service_with_stubs()
        previous = os.environ.get('YFEIEYE_DVR_TENANT_ID')
        os.environ['YFEIEYE_DVR_TENANT_ID'] = '9'
        try:
            device = types.SimpleNamespace(tenant_id='8')
            space = types.SimpleNamespace(tenant_id='7')
            self.assertEqual(
                '6',
                service._resolve_dvr_tenant_id(
                    {'tenant_id': '6'}, device=device, record_space=space),
            )
            self.assertEqual(
                '8',
                service._resolve_dvr_tenant_id({}, device=device, record_space=space),
            )
            self.assertEqual(
                '7',
                service._resolve_dvr_tenant_id(
                    {}, device=types.SimpleNamespace(), record_space=space),
            )
            self.assertEqual(
                '9',
                service._resolve_dvr_tenant_id(
                    {}, device=types.SimpleNamespace(), record_space=types.SimpleNamespace()),
            )
        finally:
            if previous is None:
                os.environ.pop('YFEIEYE_DVR_TENANT_ID', None)
            else:
                os.environ['YFEIEYE_DVR_TENANT_ID'] = previous

    def test_dvr_tenant_resolution_fails_closed_when_unconfigured(self):
        service = self._import_dvr_upload_service_with_stubs()
        previous = os.environ.pop('YFEIEYE_DVR_TENANT_ID', None)
        try:
            with self.assertRaisesRegex(ValueError, 'tenant'):
                service._resolve_dvr_tenant_id(
                    {}, device=types.SimpleNamespace(), record_space=types.SimpleNamespace())
        finally:
            if previous is not None:
                os.environ['YFEIEYE_DVR_TENANT_ID'] = previous

    def test_dvr_object_names_are_tenant_scoped_and_urls_are_protected(self):
        service = self._import_dvr_upload_service_with_stubs()

        object_name = service._build_dvr_object_name(
            '1', 'device-01', '2026/07/11', 'clip.mp4')

        self.assertEqual(
            'tenants/1/device-01/2026/07/11/clip.mp4', object_name)
        self.assertEqual(
            '/video/record/space/7/video/tenants/1/device-01/2026/07/11/clip.mp4',
            service._protected_record_url(7, object_name),
        )

    def test_dvr_enqueue_failure_is_persisted_before_worker_receives_event(self):
        import app.services.media_kafka_service as kafka_service

        failures = []
        kafka_service.record_cache_flush_failure = lambda event, error: failures.append(
            (dict(event), str(error)))
        kafka_service._get_producer = lambda: (_ for _ in ()).throw(
            RuntimeError('kafka unavailable'))
        event = kafka_service.build_event_from_srs_hook({
            'stream': 'device-01',
            'file': '/data/playbacks/live/device-01/cache.flv',
            'tenant_id': '7',
        }, device_id='device-01')

        self.assertFalse(kafka_service.publish_dvr_event(event))
        self.assertEqual(1, len(failures))
        self.assertEqual('device-01', failures[0][0]['device_id'])
        self.assertEqual('7', failures[0][0]['tenant_id'])
        self.assertIn('kafka unavailable', failures[0][1])

    def test_dvr_worker_persists_failed_flush_and_resolves_successful_retry(self):
        service = self._import_dvr_upload_service_with_stubs()
        self.assertTrue(hasattr(service, '_process_dvr_event_impl'))
        failures = []
        resolved = []
        service.record_cache_flush_failure = lambda event, error: failures.append(
            (dict(event), str(error)))
        service.resolve_record_cache_flush_failure = lambda event: resolved.append(
            dict(event))
        event = {
            'event_id': 'dvr-1',
            'device_id': 'device-01',
            'file_path': '/data/playbacks/live/device-01/cache.ts',
        }

        service._process_dvr_event_impl = lambda payload: False
        self.assertFalse(service.process_dvr_event(event))
        self.assertEqual('dvr_cache_flush_worker_returned_false', failures[-1][1])
        self.assertEqual([], resolved)

        service._process_dvr_event_impl = lambda payload: True
        self.assertTrue(service.process_dvr_event(event))
        self.assertEqual(event, resolved[-1])

        def crash(_payload):
            raise OSError('disk write failed')

        service._process_dvr_event_impl = crash
        with self.assertRaisesRegex(OSError, 'disk write failed'):
            service.process_dvr_event(event)
        self.assertIn('disk write failed', failures[-1][1])

    @staticmethod
    def _import_dvr_upload_service_with_stubs():
        cv2 = types.ModuleType('cv2')
        cv2.IMWRITE_JPEG_QUALITY = 1
        cv2.imencode = lambda *args, **kwargs: (False, b'')
        sys.modules['cv2'] = cv2

        minio = types.ModuleType('minio')
        minio_error = types.ModuleType('minio.error')
        minio_error.S3Error = type('S3Error', (Exception,), {})
        minio.error = minio_error
        sys.modules['minio'] = minio
        sys.modules['minio.error'] = minio_error

        resolver = types.ModuleType('app.services.dvr_device_resolver')
        resolver.resolve_device_from_hook = lambda *args, **kwargs: (None, None)
        sys.modules['app.services.dvr_device_resolver'] = resolver

        utils = types.ModuleType('app.services.media_dvr_utils')
        utils.extract_thumbnail_from_video = lambda *args, **kwargs: None
        utils.ffprobe_video_duration_seconds = lambda *args, **kwargs: 0
        utils.parse_srs_dvr_path_date = lambda *args, **kwargs: (None, None)
        utils.resolve_playback_absolute_path = lambda path, cwd='': path
        utils.srs_dvr_min_file_bytes = lambda: 1
        utils.wait_dvr_file_stable = lambda *args, **kwargs: 0
        sys.modules['app.services.media_dvr_utils'] = utils

        kafka = types.ModuleType('app.services.media_kafka_service')
        kafka.publish_dvr_dlq = lambda *args, **kwargs: None
        sys.modules['app.services.media_kafka_service'] = kafka

        policy = types.ModuleType('app.utils.minio_bucket_policy')
        policy.ensure_bucket_public_read_write_policy = lambda *args, **kwargs: None
        policy.ensure_bucket_private = lambda *args, **kwargs: None
        sys.modules['app.utils.minio_bucket_policy'] = policy

        urls = types.ModuleType('app.utils.service_urls')
        urls.ensure_shanghai_aware = lambda value: value
        urls.epoch_to_shanghai_datetime = lambda value: datetime.fromtimestamp(value)
        urls.minio_storage_enabled = lambda: False
        urls.build_record_video_api_url = (
            lambda space_id, object_name:
            f'/video/record/space/{space_id}/video/{object_name}')
        sys.modules['app.utils.service_urls'] = urls

        query = types.SimpleNamespace(get=lambda *_: None, filter_by=lambda **kwargs: None)
        sys.modules['models'] = types.SimpleNamespace(
            Device=types.SimpleNamespace(query=query),
            Playback=types.SimpleNamespace(query=query),
            db=types.SimpleNamespace(session=types.SimpleNamespace(
                add=lambda *_: None, commit=lambda: None, rollback=lambda: None)),
        )
        sys.modules.pop('app.services.dvr_upload_service', None)
        return importlib.import_module('app.services.dvr_upload_service')

    @staticmethod
    def _import_snap_upload_service_with_stubs():
        media_utils = types.ModuleType('app.services.media_dvr_utils')
        media_utils.resolve_playback_absolute_path = lambda value: value
        sys.modules['app.services.media_dvr_utils'] = media_utils

        media_kafka = types.ModuleType('app.services.media_kafka_service')
        media_kafka.publish_snap_dlq = lambda *_args, **_kwargs: None
        sys.modules['app.services.media_kafka_service'] = media_kafka

        disk_guard = types.ModuleType('app.services.playback_disk_guard_service')
        disk_guard.get_snap_staging_dir = lambda: '/tmp'
        disk_guard.remove_playback_file = lambda *_args, **_kwargs: None
        sys.modules['app.services.playback_disk_guard_service'] = disk_guard

        policy = types.ModuleType('app.utils.minio_bucket_policy')
        policy.ensure_bucket_private = lambda *_args, **_kwargs: None
        sys.modules['app.utils.minio_bucket_policy'] = policy

        urls = types.ModuleType('app.utils.service_urls')
        urls.minio_storage_enabled = lambda: True
        urls.build_snap_image_api_url = (
            lambda space_id, object_name:
            f'/video/snap/space/{space_id}/image/{object_name}')
        sys.modules['app.utils.service_urls'] = urls

        sys.modules['models'] = types.SimpleNamespace(
            SnapSpace=types.SimpleNamespace(),
            db=types.SimpleNamespace(session=types.SimpleNamespace(rollback=lambda: None)),
        )
        sys.modules.pop('app.services.snap_upload_service', None)
        return importlib.import_module('app.services.snap_upload_service')


class TestRecordAvailabilityBlueprint(_ModuleIsolationTestCase):
    def test_record_storage_drift_route_passes_query_to_service(self):
        record_module = self._import_record_blueprint_with_stubs()
        from app.services.media_authorization_service import MediaAuthorizationDecision

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        captured = {}
        cache_query = {}
        auth_calls = []
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id, device_id='device-01')
        record_module.authorize_media_request = lambda req, action, camera_id=None, **kwargs: (
            auth_calls.append((action, camera_id)) or MediaAuthorizationDecision(
                True, '9004', '1', camera_id, action, 'test_granted', 200,
                'service_hmac', 'iot-system'))

        def fake_list_cache_flush_failures(**kwargs):
            cache_query.update(kwargs)
            return {
                'items': [{
                    'device_id': 'device-01',
                    'space_id': 7,
                    'cache_path': '/data/cache.ts',
                    'error': 'flush failed',
                }],
                'next_cursor': 'cache-next',
                'has_more': True,
                'limit': 10,
            }

        def fake_inspect_recording_storage_drift(**kwargs):
            captured.update(kwargs)
            return {
                'space_id': kwargs['space_id'],
                'device_id': kwargs.get('device_id'),
                'issues': [{
                    'reason': 'file_missing',
                    'category': 'filesystem',
                    'record_id': 42,
                }],
                'summary': {
                    'record_count': 3,
                    'issue_count': 1,
                    'issue_reasons': {'file_missing': 1},
                    'healthy': False,
                },
            }

        original = getattr(record_module, 'inspect_recording_storage_drift', None)
        original_cache_reader = getattr(
            record_module, 'list_record_cache_flush_failures', None)
        record_module.inspect_recording_storage_drift = fake_inspect_recording_storage_drift
        record_module.list_record_cache_flush_failures = fake_list_cache_flush_failures
        try:
            response = app.test_client().get(
                '/video/record/space/7/videos/drift'
                '?device_id=device-01'
                '&retention_hours=24'
                '&cursor=record-cursor'
                '&limit=25'
                '&cache_cursor=cache-cursor'
                '&cache_limit=10'
            )
        finally:
            if original is None:
                delattr(record_module, 'inspect_recording_storage_drift')
            else:
                record_module.inspect_recording_storage_drift = original
            if original_cache_reader is None:
                delattr(record_module, 'list_record_cache_flush_failures')
            else:
                record_module.list_record_cache_flush_failures = original_cache_reader

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual(7, body['data']['space_id'])
        self.assertEqual('device-01', body['data']['device_id'])
        self.assertEqual(7, captured['space_id'])
        self.assertEqual('device-01', captured['device_id'])
        self.assertEqual(24, captured['retention_hours'])
        self.assertEqual('record-cursor', captured['cursor'])
        self.assertEqual(25, captured['limit'])
        self.assertIn('cache_flush_events', captured)
        self.assertEqual([{
            'device_id': 'device-01',
            'space_id': 7,
            'cache_path': '/data/cache.ts',
            'error': 'flush failed',
        }], captured['cache_flush_events'])
        self.assertEqual({
            'space_id': 7,
            'device_id': 'device-01',
            'tenant_id': '1',
            'cursor': 'cache-cursor',
            'limit': 10,
            'retention_hours': 24,
            'return_page': True,
        }, cache_query)
        self.assertEqual('cache-next', body['data']['cache_flush_pagination']['next_cursor'])
        self.assertEqual([('coverage', 'device-01')], auth_calls)

    def test_record_storage_drift_rejects_camera_not_owned_by_space(self):
        record_module = self._import_record_blueprint_with_stubs()
        from app.services.media_authorization_service import MediaAuthorizationDecision
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id, device_id='camera-01')
        record_module.authorize_media_request = lambda req, action, camera_id=None, **kwargs: (
            MediaAuthorizationDecision(
                True, '9004', '1', camera_id, action, 'test_granted', 200,
                'service_hmac', 'iot-system'))
        record_module.inspect_recording_storage_drift = lambda **kwargs: self.fail(
            'camera scope mismatch must not inspect storage metadata')

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')
        response = app.test_client().get(
            '/video/record/space/7/videos/drift?device_id=camera-02')

        self.assertEqual(403, response.status_code)
        self.assertEqual('camera_device_scope_mismatch', response.get_json()['reason'])

    def test_record_availability_route_passes_window_to_service(self):
        record_module = self._import_record_blueprint_with_stubs()

        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')

        captured = {}

        def fake_query_recording_availability(**kwargs):
            captured.update(kwargs)
            return {
                'device_id': kwargs['device_id'],
                'camera_id': kwargs.get('camera_id'),
                'segments': [
                    {
                        'status': 'available',
                        'start_time': kwargs['begin_time'],
                        'end_time': kwargs['end_time'],
                        'play_url': '/video/record/space/7/video/live/device-01/clip.mp4',
                        'export_url': '/video/record/export',
                    }
                ],
            }

        original = getattr(record_module, 'query_recording_availability', None)
        record_module.query_recording_availability = fake_query_recording_availability
        try:
            response = app.test_client().get(
                '/video/record/availability'
                '?device_id=device-01'
                '&camera_id=device-01'
                '&begin_time=2026-06-30%2010:00:00'
                '&end_time=2026-06-30%2010:02:00'
            )
        finally:
            if original is None:
                delattr(record_module, 'query_recording_availability')
            else:
                record_module.query_recording_availability = original

        self.assertEqual(200, response.status_code)
        body = response.get_json()
        self.assertEqual(0, body['code'])
        self.assertEqual('device-01', body['data']['device_id'])
        self.assertEqual('device-01', captured['device_id'])
        self.assertEqual('device-01', captured['camera_id'])
        self.assertEqual('2026-06-30 10:00:00', captured['begin_time'])
        self.assertEqual('2026-06-30 10:02:00', captured['end_time'])

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
                'test',
            )
        )
        return record_module


if __name__ == '__main__':
    unittest.main()
