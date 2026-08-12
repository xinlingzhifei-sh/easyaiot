"""Atomic archive contract tests for retained recordings and snapshots."""
import importlib
import sys
import types
import unittest
from unittest import mock

from flask import Flask


class _Column:
    def __eq__(self, _other):
        return self

    def __lt__(self, _other):
        return self


class _Query:
    def __init__(self, space, rows):
        self.space = space
        self.rows = list(rows)

    def get_or_404(self, _identity):
        return self.space

    def filter(self, *_args):
        return self

    def all(self):
        return list(self.rows)


class _ObjectResponse:
    def __init__(self, content):
        self.content = content
        self.offset = 0

    def read(self, amount=-1):
        if amount is None or amount < 0:
            amount = len(self.content) - self.offset
        start = self.offset
        self.offset = min(len(self.content), self.offset + amount)
        return self.content[start:self.offset]

    def close(self):
        return None

    def release_conn(self):
        return None


class _ArchiveMinio:
    def __init__(self, events, failure=None):
        self.events = events
        self.failure = failure
        self.archives = {}
        self.archive_metadata = {}

    def bucket_exists(self, _bucket):
        return True

    def make_bucket(self, bucket):
        self.events.append(('make_bucket', bucket))

    def get_object(self, bucket, object_name):
        self.events.append(('get', bucket, object_name))
        archive = self.archives.get((bucket, object_name))
        if archive is not None:
            if self.failure == 'content' and archive:
                archive = bytes([archive[0] ^ 0xFF]) + archive[1:]
            return _ObjectResponse(archive)
        return _ObjectResponse(b'archive-source-content')

    def put_object(self, bucket, object_name, stream, length,
                   content_type=None, metadata=None):
        self.events.append(('put', bucket, object_name, length))
        if self.failure == 'put':
            raise OSError('archive upload failed')
        content = stream.read(length)
        self.archives[(bucket, object_name)] = content
        self.archive_metadata[(bucket, object_name)] = dict(metadata or {})
        return types.SimpleNamespace(object_name=object_name)

    def stat_object(self, bucket, object_name):
        self.events.append(('stat', bucket, object_name))
        content = self.archives[(bucket, object_name)]
        metadata = self.archive_metadata[(bucket, object_name)]
        size = len(content) + (1 if self.failure == 'size' else 0)
        digest = metadata.get('sha256', '')
        if self.failure == 'hash':
            digest = '0' * 64
        return types.SimpleNamespace(
            size=size,
            metadata={'x-amz-meta-sha256': digest},
        )

    def remove_object(self, bucket, object_name):
        self.events.append(('remove', bucket, object_name))


def _load_archive_services():
    model = type('Model', (), {
        'query': None,
        'tenant_id': _Column(),
        'space_id': _Column(),
        'device_id': _Column(),
        'event_time': _Column(),
        'captured_at': _Column(),
    })
    models = types.ModuleType('models')
    models.db = types.SimpleNamespace(session=types.SimpleNamespace(rollback=lambda: None))
    models.RecordSpace = model
    models.RecordFile = model
    models.Alert = model
    models.SnapSpace = model
    models.SnapImage = model

    minio = types.ModuleType('minio')
    minio_error = types.ModuleType('minio.error')
    minio_error.S3Error = type('S3Error', (Exception,), {})
    minio.error = minio_error

    alert_service = types.ModuleType('app.services.alert_service')
    alert_service._alert_to_dict = lambda value: value
    record_space_service = types.ModuleType('app.services.record_space_service')
    record_space_service.get_minio_client = lambda: None
    snap_space_service = types.ModuleType('app.services.snap_space_service')
    snap_space_service.get_minio_client = lambda: None
    metadata_service = types.ModuleType('app.services.space_file_metadata_service')
    for name in (
        'delete_record_files_metadata',
        'sync_record_files_from_minio',
        'delete_snap_images_metadata',
        'sync_snap_images_from_minio',
        'require_mutable_tenant_object',
    ):
        setattr(metadata_service, name, lambda *_args, **_kwargs: None)
    metadata_service.extract_prefix_from_url = lambda value: value
    service_urls = types.ModuleType('app.utils.service_urls')
    service_urls.build_record_video_api_url = lambda *_args, **_kwargs: ''
    service_urls.minio_storage_enabled = lambda: True
    disk_guard = types.ModuleType('app.services.playback_disk_guard_service')
    disk_guard.get_snap_staging_dir = lambda: ''
    bucket_policy = types.ModuleType('app.utils.minio_bucket_policy')
    bucket_policy.ensure_bucket_private = (
        lambda client, bucket: client.events.append(('private', bucket)))

    replacements = {
        'models': models,
        'minio': minio,
        'minio.error': minio_error,
        'app.services.alert_service': alert_service,
        'app.services.record_space_service': record_space_service,
        'app.services.snap_space_service': snap_space_service,
        'app.services.space_file_metadata_service': metadata_service,
        'app.services.playback_disk_guard_service': disk_guard,
        'app.utils.minio_bucket_policy': bucket_policy,
        'app.utils.service_urls': service_urls,
    }
    targets = (
        'app.services.record_video_service',
        'app.services.snap_image_service',
    )
    missing = object()
    names = tuple(replacements) + targets
    previous = {name: sys.modules.get(name, missing) for name in names}
    parent_attributes = []
    for name in names:
        if '.' not in name:
            continue
        parent_name, attribute = name.rsplit('.', 1)
        parent = sys.modules.get(parent_name)
        if parent is not None:
            parent_attributes.append(
                (parent, attribute, getattr(parent, attribute, missing)))
    try:
        sys.modules.update(replacements)
        for target in targets:
            sys.modules.pop(target, None)
        return tuple(importlib.import_module(target) for target in targets)
    finally:
        for name, module in previous.items():
            if module is missing:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = module
        for parent, attribute, value in parent_attributes:
            if value is missing:
                try:
                    delattr(parent, attribute)
                except AttributeError:
                    pass
            else:
                setattr(parent, attribute, value)


class ArchiveAtomicityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.record_service, cls.snap_service = _load_archive_services()

    def test_record_archive_is_verified_before_source_and_metadata_delete(self):
        space = types.SimpleNamespace(
            id=7, bucket_name='record-space', save_mode=1,
            device_id='camera-01', tenant_id=7)
        record = types.SimpleNamespace(
            object_name='tenants/7/camera-01/2026/clip.flv',
            filename='clip.flv', device_id='camera-01', thumbnail_url=None)

        result, client, events = self._run_record_archive(space, record)

        put_event = next(event for event in events if event[0] == 'put')
        archive_name = put_event[2]
        source_remove = ('remove', 'record-space', record.object_name)
        self.assertGreater(put_event[3], 0)
        self.assertEqual(put_event[3], len(client.archives[('record-archive', archive_name)]))
        self.assertRegex(
            client.archive_metadata[('record-archive', archive_name)]['sha256'],
            r'^[0-9a-f]{64}$',
        )
        self.assertTrue(archive_name.startswith(
            'tenants/7/camera-01/archives/'))
        self.assertIn(('private', 'record-archive'), events)
        self.assertLess(events.index(('stat', 'record-archive', archive_name)),
                        events.index(source_remove))
        metadata_delete = (
            'db', 'record-space', (record.object_name,),
            {'tenant_id': 7, 'space_id': 7})
        self.assertLess(events.index(source_remove), events.index(metadata_delete))
        self.assertEqual({
            'processed_count': 1,
            'deleted_count': 1,
            'archived_count': 1,
            'error_count': 0,
        }, result)

    def test_record_archive_failures_never_delete_source_or_metadata(self):
        for failure in ('put', 'size', 'hash', 'content'):
            with self.subTest(failure=failure):
                space = types.SimpleNamespace(
                    id=7, bucket_name='record-space', save_mode=1,
                    device_id='camera-01', tenant_id=7)
                record = types.SimpleNamespace(
                    object_name='tenants/7/camera-01/2026/clip.flv',
                    filename='clip.flv', device_id='camera-01', thumbnail_url=None)

                result, _client, events = self._run_record_archive(
                    space, record, failure=failure)

                self.assertNotIn(
                    ('remove', 'record-space', record.object_name), events)
                self.assertFalse(any(event[0] == 'db' for event in events))
                self.assertEqual(0, result['deleted_count'])
                self.assertEqual(0, result['archived_count'])
                self.assertGreater(result['error_count'], 0)

    def test_snapshot_archive_is_verified_before_source_and_metadata_delete(self):
        space = types.SimpleNamespace(
            id=8, bucket_name='snap-space', save_mode=1,
            device_id='camera-02', tenant_id=8)
        image = types.SimpleNamespace(
            object_name='tenants/8/cameras/camera-02/2026/frame.jpg',
            filename='frame.jpg', device_id='camera-02')

        result, client, events = self._run_snapshot_archive(space, image)

        put_event = next(event for event in events if event[0] == 'put')
        archive_name = put_event[2]
        source_remove = ('remove', 'snap-space', image.object_name)
        self.assertGreater(put_event[3], 0)
        self.assertEqual(put_event[3], len(client.archives[('snap-archive', archive_name)]))
        self.assertRegex(
            client.archive_metadata[('snap-archive', archive_name)]['sha256'],
            r'^[0-9a-f]{64}$',
        )
        self.assertTrue(archive_name.startswith(
            'tenants/8/cameras/camera-02/archives/'))
        self.assertIn(('private', 'snap-archive'), events)
        self.assertLess(events.index(('stat', 'snap-archive', archive_name)),
                        events.index(source_remove))
        metadata_delete = (
            'db', 'snap-space', (image.object_name,),
            {'tenant_id': 8, 'space_id': 8})
        self.assertLess(events.index(source_remove), events.index(metadata_delete))
        self.assertEqual({
            'processed_count': 1,
            'deleted_count': 1,
            'archived_count': 1,
            'error_count': 0,
        }, result)

    def test_snapshot_archive_failures_never_delete_source_or_metadata(self):
        for failure in ('put', 'size', 'hash', 'content'):
            with self.subTest(failure=failure):
                space = types.SimpleNamespace(
                    id=8, bucket_name='snap-space', save_mode=1,
                    device_id='camera-02', tenant_id=8)
                image = types.SimpleNamespace(
                    object_name='tenants/8/cameras/camera-02/2026/frame.jpg',
                    filename='frame.jpg', device_id='camera-02')

                result, _client, events = self._run_snapshot_archive(
                    space, image, failure=failure)

                self.assertNotIn(
                    ('remove', 'snap-space', image.object_name), events)
                self.assertFalse(any(event[0] == 'db' for event in events))
                self.assertEqual(0, result['deleted_count'])
                self.assertEqual(0, result['archived_count'])
                self.assertGreater(result['error_count'], 0)

    def _run_record_archive(self, space, record, failure=None):
        events = []
        client = _ArchiveMinio(events, failure=failure)
        space_model = types.SimpleNamespace(query=_Query(space, []))
        file_model = types.SimpleNamespace(
            query=_Query(space, [record]),
            tenant_id=_Column(), space_id=_Column(),
            event_time=_Column(), device_id=_Column())
        app = Flask(__name__)
        app.config['MINIO_ARCHIVE_BUCKET'] = 'record-archive'
        with mock.patch.object(self.record_service, 'RecordSpace', space_model), \
                mock.patch.object(self.record_service, 'RecordFile', file_model), \
                mock.patch.object(self.record_service, 'get_minio_client', return_value=client), \
                mock.patch.object(self.record_service.logger, 'error'), \
                mock.patch.object(
                    self.record_service,
                    'delete_record_files_metadata',
                    side_effect=lambda bucket, names, **scope: events.append(
                        ('db', bucket, tuple(names), scope))):
            with app.app_context():
                result = self.record_service.cleanup_old_videos_by_save_time(7, 1)
        return result, client, events

    def _run_snapshot_archive(self, space, image, failure=None):
        events = []
        client = _ArchiveMinio(events, failure=failure)
        space_model = types.SimpleNamespace(query=_Query(space, []))
        image_model = types.SimpleNamespace(
            query=_Query(space, [image]),
            tenant_id=_Column(), space_id=_Column(),
            device_id=_Column(), captured_at=_Column())
        app = Flask(__name__)
        app.config['MINIO_ARCHIVE_BUCKET'] = 'snap-archive'
        with mock.patch.object(self.snap_service, 'SnapSpace', space_model), \
                mock.patch.object(self.snap_service, 'SnapImage', image_model), \
                mock.patch.object(self.snap_service, 'get_minio_client', return_value=client), \
                mock.patch.object(self.snap_service.logger, 'error'), \
                mock.patch.object(
                    self.snap_service,
                    'delete_snap_images_metadata',
                    side_effect=lambda bucket, names, **scope: events.append(
                        ('db', bucket, tuple(names), scope))):
            with app.app_context():
                result = self.snap_service.cleanup_old_images_by_save_time(8, 1)
        return result, client, events


if __name__ == '__main__':
    unittest.main()
