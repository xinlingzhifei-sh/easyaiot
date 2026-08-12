"""Tenant isolation regression tests for VIDEO recording and snapshot metadata."""
from __future__ import annotations

import os
import importlib
import sys
import types
import unittest
from pathlib import Path
from unittest import mock


VIDEO_DIR = Path(__file__).resolve().parent


def _install_minio_stub_if_missing():
    try:
        import minio  # noqa: F401
        return
    except ModuleNotFoundError:
        pass

    minio = types.ModuleType('minio')
    minio.Minio = object
    minio_error = types.ModuleType('minio.error')
    minio_error.S3Error = type('S3Error', (Exception,), {})
    sys.modules['minio'] = minio
    sys.modules['minio.error'] = minio_error


_install_minio_stub_if_missing()

_REAL_MODULE_NAMES = (
    'models',
    'app.services.record_space_service',
    'app.services.snap_space_service',
    'app.services.space_file_metadata_service',
    'app.utils.service_urls',
)
_REAL_MODULES = {
    name: importlib.import_module(name)
    for name in _REAL_MODULE_NAMES
}


class _RealVideoModulesTestCase(unittest.TestCase):
    def setUp(self):
        for name, module in _REAL_MODULES.items():
            sys.modules[name] = module

    def tearDown(self):
        for name, module in _REAL_MODULES.items():
            sys.modules[name] = module


class VideoTenantModelContractTest(_RealVideoModulesTestCase):

    def test_historical_alert_image_and_playback_models_require_tenant_owner(self):
        from models import Alert, Image, Playback

        for model in (Alert, Image, Playback):
            with self.subTest(model=model.__name__):
                self.assertIn('tenant_id', model.__table__.c)
                column = model.__table__.c.tenant_id
                self.assertFalse(column.nullable)
                self.assertEqual('BIGINT', str(column.type))
                self.assertTrue(column.index)

    def test_record_and_snapshot_models_have_required_tenant_columns(self):
        from models import RecordFile, RecordSpace, SnapImage, SnapSpace

        for model in (RecordSpace, RecordFile, SnapSpace, SnapImage):
            with self.subTest(model=model.__name__):
                column = model.__table__.c.tenant_id
                self.assertFalse(column.nullable)
                self.assertEqual('BIGINT', str(column.type))

    def test_tenant_scoped_constraints_cover_spaces_files_and_parent_consistency(self):
        from models import RecordFile, RecordSpace, SnapImage, SnapSpace

        expected = {
            RecordSpace: {
                'uq_record_space_tenant_space_code': ('tenant_id', 'space_code'),
                'uq_record_space_tenant_device': ('tenant_id', 'device_id'),
                'uq_record_space_tenant_id': ('tenant_id', 'id'),
                'ck_record_space_tenant_positive': (),
            },
            SnapSpace: {
                'uq_snap_space_tenant_space_code': ('tenant_id', 'space_code'),
                'uq_snap_space_tenant_device': ('tenant_id', 'device_id'),
                'uq_snap_space_tenant_id': ('tenant_id', 'id'),
                'ck_snap_space_tenant_positive': (),
            },
            RecordFile: {
                'uq_record_file_tenant_bucket_object': (
                    'tenant_id', 'bucket_name', 'object_name'),
                'ck_record_file_tenant_positive': (),
                'ck_record_file_object_tenant_scope': (),
            },
            SnapImage: {
                'uq_snap_image_tenant_bucket_object': (
                    'tenant_id', 'bucket_name', 'object_name'),
                'ck_snap_image_tenant_positive': (),
                'ck_snap_image_object_tenant_scope': (),
            },
        }
        for model, constraints in expected.items():
            actual = {
                constraint.name: tuple(column.name for column in constraint.columns)
                for constraint in model.__table__.constraints
                if getattr(constraint, 'name', None)
            }
            for name, columns in constraints.items():
                with self.subTest(model=model.__name__, constraint=name):
                    self.assertEqual(columns, actual.get(name))

        record_fk = next(
            constraint for constraint in RecordFile.__table__.foreign_key_constraints
            if constraint.name == 'fk_record_file_tenant_space')
        snap_fk = next(
            constraint for constraint in SnapImage.__table__.foreign_key_constraints
            if constraint.name == 'fk_snap_image_tenant_space')
        self.assertEqual(
            ('tenant_id', 'space_id'),
            tuple(element.parent.name for element in record_fk.elements),
        )
        self.assertEqual(
            ('record_space.tenant_id', 'record_space.id'),
            tuple(element.target_fullname for element in record_fk.elements),
        )
        self.assertEqual(
            ('tenant_id', 'space_id'),
            tuple(element.parent.name for element in snap_fk.elements),
        )
        self.assertEqual(
            ('snap_space.tenant_id', 'snap_space.id'),
            tuple(element.target_fullname for element in snap_fk.elements),
        )

    def test_serialized_space_and_file_items_expose_tenant_id(self):
        from models import RecordFile, RecordSpace, SnapImage, SnapSpace

        record_space = RecordSpace(
            tenant_id=7,
            space_name='record',
            space_code='record-7',
            bucket_name='record-space',
            device_id=None,
        )
        snap_space = SnapSpace(
            tenant_id=7,
            space_name='snap',
            space_code='snap-7',
            bucket_name='snap-space',
            device_id=None,
        )
        record = RecordFile(
            tenant_id=7,
            space_id=1,
            device_id='camera-01',
            object_name='tenants/7/camera-01/clip.mp4',
            bucket_name='record-space',
            filename='clip.mp4',
            url=(
                '/api/v1/buckets/record-space/objects/download?'
                'prefix=tenants%2F7%2Fcamera-01%2Fclip.mp4'),
            thumbnail_url=(
                '/api/v1/buckets/record-space/objects/download?'
                'prefix=tenants%2F7%2Fcamera-01%2Fclip.jpg'),
        )
        image = SnapImage(
            tenant_id=7,
            space_id=1,
            device_id='camera-01',
            object_name='tenants/7/cameras/camera-01/frame.jpg',
            bucket_name='snap-space',
            filename='frame.jpg',
            url=(
                '/api/v1/buckets/snap-space/objects/download?'
                'prefix=tenants%2F7%2Fcameras%2Fcamera-01%2Fframe.jpg'),
        )

        with mock.patch(
                'app.services.space_save_time_service.enrich_record_space_dict',
                side_effect=lambda data, _space: data), mock.patch(
                'app.services.space_save_time_service.enrich_snap_space_dict',
                side_effect=lambda data, _space: data):
            self.assertEqual(7, record_space.to_dict()['tenant_id'])
            self.assertEqual(7, snap_space.to_dict()['tenant_id'])
        record_item = record.to_list_item()
        image_item = image.to_list_item()
        self.assertEqual(7, record_item['tenant_id'])
        self.assertEqual(
            '/video/record/space/1/video/tenants/7/camera-01/clip.mp4',
            record_item['url'],
        )
        self.assertIsNone(record_item['thumbnail_url'])
        self.assertEqual(7, image_item['tenant_id'])
        self.assertEqual(
            '/video/snap/space/1/image/tenants/7/cameras/camera-01/frame.jpg',
            image_item['url'],
        )


class VideoTenantMigrationContractTest(_RealVideoModulesTestCase):

    def test_plan_tracks_tenant_scope_migration_after_region_rule_migration(self):
        from apply_migrations import build_migration_plan

        plan = build_migration_plan(VIDEO_DIR)
        migrations = {migration.version: migration for migration in plan}
        versions = [migration.version for migration in plan]
        region_version = 'V20260711__device_detection_region_rule_fields.sql'
        tenant_version = 'V20260712__record_snapshot_tenant_scope.sql'
        alert_version = 'V20260713__alert_image_playback_tenant_scope.sql'

        self.assertLess(versions.index(region_version), versions.index(tenant_version))
        self.assertLess(versions.index(tenant_version), versions.index(alert_version))
        self.assertRegex(migrations[alert_version].checksum, r'^[a-f0-9]{64}$')
        tenant_sql = migrations[tenant_version].sql
        for marker in (
            "current_setting('yfeieye.video_legacy_tenant_id', true)",
            'tenant_id BIGINT',
            'uq_record_space_tenant_device',
            'uq_snap_space_tenant_device',
            'uq_record_file_tenant_bucket_object',
            'uq_snap_image_tenant_bucket_object',
            'fk_record_file_tenant_space',
            'fk_snap_image_tenant_space',
            'ck_record_space_tenant_positive',
            'ck_record_file_tenant_positive',
            'ck_snap_space_tenant_positive',
            'ck_snap_image_tenant_positive',
            'legacy object keys remain unchanged',
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, tenant_sql)
        sql = migrations[alert_version].sql
        for marker in (
            "current_setting('yfeieye.video_legacy_tenant_id', true)",
            'ALTER TABLE alert ADD COLUMN IF NOT EXISTS tenant_id BIGINT',
            'ALTER TABLE image ADD COLUMN IF NOT EXISTS tenant_id BIGINT',
            'ALTER TABLE playback ADD COLUMN IF NOT EXISTS tenant_id BIGINT',
            'ck_alert_tenant_positive',
            'ck_image_tenant_positive',
            'ck_playback_tenant_positive',
            'ix_alert_tenant_device_time',
            'ix_image_tenant_device_created_at',
            'ix_playback_tenant_device_event_time',
        ):
            with self.subTest(marker=marker):
                self.assertIn(marker, sql)

    def test_legacy_tenant_configuration_fails_closed(self):
        from apply_migrations import resolve_legacy_tenant_id

        for value in (None, '', '0', '-1', 'tenant-one', '1.5'):
            with self.subTest(value=value):
                with mock.patch.dict(os.environ, {}, clear=False):
                    os.environ.pop('YFEIEYE_VIDEO_LEGACY_TENANT_ID', None)
                    if value is not None:
                        os.environ['YFEIEYE_VIDEO_LEGACY_TENANT_ID'] = value
                    with self.assertRaisesRegex(ValueError, 'legacy tenant'):
                        resolve_legacy_tenant_id()

        with mock.patch.dict(
                os.environ, {'YFEIEYE_VIDEO_LEGACY_TENANT_ID': '1'}, clear=False):
            self.assertEqual(1, resolve_legacy_tenant_id())

    def test_migration_connection_receives_legacy_tenant_guc(self):
        from apply_migrations import configure_legacy_tenant_guc

        statements = []

        class Cursor:
            def execute(self, statement, params=None):
                statements.append((statement, params))

            def close(self):
                pass

        class Connection:
            def cursor(self):
                return Cursor()

        configure_legacy_tenant_guc(Connection(), 1)

        self.assertEqual(1, len(statements))
        self.assertIn("set_config('yfeieye.video_legacy_tenant_id'", statements[0][0])
        self.assertEqual(('1',), statements[0][1])

    def test_compose_and_env_example_configure_legacy_tenant_one(self):
        compose = (VIDEO_DIR / 'docker-compose.yaml').read_text(encoding='utf-8')
        env_example = (VIDEO_DIR / 'env.example').read_text(encoding='utf-8')

        self.assertIn(
            'YFEIEYE_VIDEO_LEGACY_TENANT_ID=${YFEIEYE_VIDEO_LEGACY_TENANT_ID:-1}',
            compose,
        )
        self.assertIn('YFEIEYE_VIDEO_LEGACY_TENANT_ID=1', env_example)


class VideoTenantSpaceServiceTest(_RealVideoModulesTestCase):

    def test_record_space_creation_and_lookup_are_tenant_scoped(self):
        from app.services import record_space_service as service

        captured_filters = []
        created = []

        class Query:
            @staticmethod
            def filter_by(**filters):
                captured_filters.append(filters)
                return types.SimpleNamespace(first=lambda: None)

        class RecordSpace:
            query = Query()

            def __init__(self, **values):
                self.__dict__.update(values)
                created.append(values)

        session = types.SimpleNamespace(
            expire_all=lambda: None,
            add=lambda value: None,
            commit=lambda: None,
            rollback=lambda: None,
        )
        with mock.patch.object(service, 'RecordSpace', RecordSpace), \
                mock.patch.object(service.db, 'session', session), \
                mock.patch.object(service, 'minio_storage_enabled', return_value=False):
            space = service.create_record_space(
                'camera one', device_id='camera-01', tenant_id='7')

        self.assertEqual(7, space.tenant_id)
        self.assertEqual(7, created[0]['tenant_id'])
        self.assertEqual(
            {'tenant_id': 7, 'device_id': 'camera-01'},
            captured_filters[0],
        )

    def test_snapshot_space_creation_and_lookup_are_tenant_scoped(self):
        from app.services import snap_space_service as service

        captured_filters = []
        created = []

        class Query:
            @staticmethod
            def filter_by(**filters):
                captured_filters.append(filters)
                return types.SimpleNamespace(first=lambda: None)

        class SnapSpace:
            query = Query()

            def __init__(self, **values):
                self.__dict__.update(values)
                created.append(values)

        session = types.SimpleNamespace(
            expire_all=lambda: None,
            add=lambda value: None,
            commit=lambda: None,
            rollback=lambda: None,
        )
        with mock.patch.object(service, 'SnapSpace', SnapSpace), \
                mock.patch.object(service.db, 'session', session), \
                mock.patch.object(service, 'minio_storage_enabled', return_value=False):
            space = service.create_snap_space(
                'camera one', device_id='camera-01', tenant_id='7')

        self.assertEqual(7, space.tenant_id)
        self.assertEqual(7, created[0]['tenant_id'])
        self.assertEqual(
            {'tenant_id': 7, 'device_id': 'camera-01'},
            captured_filters[0],
        )


class VideoTenantMetadataServiceTest(_RealVideoModulesTestCase):

    def test_record_upsert_inherits_space_tenant_and_queries_tenant_identity(self):
        from app.services import space_file_metadata_service as service

        captured_filters = []
        created = []

        class RecordQuery:
            @staticmethod
            def filter_by(**filters):
                captured_filters.append(filters)
                return types.SimpleNamespace(first=lambda: None)

        class Record:
            query = RecordQuery()

            def __init__(self, **values):
                self.__dict__.update(values)
                created.append(values)

        class SpaceQuery:
            @staticmethod
            def get_or_404(space_id):
                return types.SimpleNamespace(id=space_id, tenant_id=7)

        session = types.SimpleNamespace(add=lambda value: None, commit=lambda: None)
        with mock.patch.object(service, 'RecordFile', Record), \
                mock.patch.object(
                    service, 'RecordSpace', types.SimpleNamespace(query=SpaceQuery())), \
                mock.patch.object(service.db, 'session', session):
            record = service.upsert_record_file(
                space_id=1,
                device_id='camera-01',
                object_name='tenants/7/camera-01/clip.mp4',
                bucket_name='record-space',
            )

        self.assertEqual(7, record.tenant_id)
        self.assertEqual(7, created[0]['tenant_id'])
        self.assertEqual(
            '/video/record/space/1/video/tenants/7/camera-01/clip.mp4',
            created[0]['url'],
        )
        self.assertEqual(
            {
                'tenant_id': 7,
                'bucket_name': 'record-space',
                'object_name': 'tenants/7/camera-01/clip.mp4',
            },
            captured_filters[0],
        )

    def test_snapshot_upsert_rejects_tenant_different_from_space_owner(self):
        from app.services import space_file_metadata_service as service

        class SpaceQuery:
            @staticmethod
            def get_or_404(space_id):
                return types.SimpleNamespace(id=space_id, tenant_id=7)

        with mock.patch.object(
                service, 'SnapSpace', types.SimpleNamespace(query=SpaceQuery())):
            with self.assertRaisesRegex(ValueError, 'tenant.*space'):
                service.upsert_snap_image(
                    tenant_id=8,
                    space_id=1,
                    device_id='camera-01',
                    object_name='tenants/8/cameras/camera-01/frame.jpg',
                    bucket_name='snap-space',
                )


class LegacyObjectCompatibilityTest(_RealVideoModulesTestCase):

    def test_legacy_objects_are_read_only_and_only_tenant_one_may_resolve_them(self):
        from app.services.space_file_metadata_service import validate_object_tenant_scope

        self.assertEqual(
            'legacy_read_only',
            validate_object_tenant_scope(
                1, 'camera-01/2026/07/11/clip.flv', camera_id='camera-01'),
        )
        with self.assertRaisesRegex(ValueError, 'legacy object'):
            validate_object_tenant_scope(
                2, 'camera-01/2026/07/11/clip.flv', camera_id='camera-01')
        with self.assertRaisesRegex(ValueError, 'tenant object'):
            validate_object_tenant_scope(
                7, 'tenants/8/camera-01/clip.flv', camera_id='camera-01')

    def test_sync_extracts_camera_from_new_and_tenant_one_legacy_keys(self):
        from app.services.space_file_metadata_service import object_camera_id

        self.assertEqual(
            'camera-01',
            object_camera_id(7, 'tenants/7/camera-01/2026/clip.flv'),
        )
        self.assertEqual(
            'camera-01',
            object_camera_id(
                7, 'tenants/7/cameras/camera-01/2026/frame.jpg'),
        )
        self.assertEqual(
            'camera-01',
            object_camera_id(1, 'camera-01/2026/clip.flv'),
        )

    def test_snapshot_producers_only_write_tenant_prefixed_object_keys(self):
        producer_paths = (
            'app/services/auto_frame_extraction_service.py',
            'app/utils/patrol_snap_upload.py',
            'app/services/snap_task_service.py',
            'services/snapshot_algorithm_service/run_deploy.py',
        )
        for relative_path in producer_paths:
            source = (VIDEO_DIR / relative_path).read_text(encoding='utf-8')
            with self.subTest(path=relative_path):
                self.assertIn('tenants/{tenant_id}/cameras/', source)
                self.assertIn('tenant_id=tenant_id', source)

    def test_record_and_snapshot_delete_paths_reject_legacy_objects(self):
        for relative_path in (
                'app/services/record_video_service.py',
                'app/services/snap_image_service.py'):
            source = (VIDEO_DIR / relative_path).read_text(encoding='utf-8')
            with self.subTest(path=relative_path):
                self.assertIn('require_mutable_tenant_object', source)


if __name__ == '__main__':
    unittest.main()
