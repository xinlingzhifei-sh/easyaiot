"""Persistence contract tests for device detection rule thresholds."""
import importlib
import os
import sys
import tempfile
import types
import unittest
from pathlib import Path

from flask import Flask
from sqlalchemy import create_engine, inspect, text


VIDEO_DIR = os.path.dirname(os.path.abspath(__file__))
if VIDEO_DIR not in sys.path:
    sys.path.insert(0, VIDEO_DIR)

import models
from models import Device, DeviceDetectionRegion, Image, db


class _ExpressionField:
    def contains(self, _value):
        return True

    def __eq__(self, _value):
        return True


class _EmptyQuery:
    def filter(self, *_expressions):
        return self

    def all(self):
        return []


class _AlgorithmTaskWithoutAssignments:
    devices = _ExpressionField()
    task_type = _ExpressionField()
    query = _EmptyQuery()


class TestDeviceDetectionRegionPersistence(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Other VIDEO unit modules temporarily replace ``sys.modules['models']``
        # with narrow stubs. Rebind the real ORM module so this persistence suite
        # remains order-independent when the release gate runs all tests together.
        sys.modules['models'] = models
        camera_stub = types.ModuleType('app.blueprints.camera')
        camera_stub.upload_screenshot_to_minio = lambda *_args, **_kwargs: None
        camera_stub.grab_frame_for_snapshot = lambda *_args, **_kwargs: (None, 'not used')

        previous_camera_module = sys.modules.get('app.blueprints.camera')
        sys.modules['app.blueprints.camera'] = camera_stub
        try:
            cls.region_blueprint_module = importlib.import_module(
                'app.blueprints.device_detection_region'
            )
        finally:
            if previous_camera_module is None:
                sys.modules.pop('app.blueprints.camera', None)
            else:
                sys.modules['app.blueprints.camera'] = previous_camera_module

        cls.region_blueprint_module.AlgorithmTask = _AlgorithmTaskWithoutAssignments

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        database_path = os.path.join(self.temp_dir.name, 'region-rules.sqlite3')
        self.app = Flask(__name__)
        self.app.config.update(
            TESTING=True,
            SQLALCHEMY_DATABASE_URI=f'sqlite:///{database_path}',
            SQLALCHEMY_TRACK_MODIFICATIONS=False,
        )
        db.init_app(self.app)
        self.app.register_blueprint(
            self.region_blueprint_module.device_detection_region_bp,
            url_prefix='/video/device-detection',
        )

        with self.app.app_context():
            Device.__table__.create(db.engine)
            Image.__table__.create(db.engine)
            DeviceDetectionRegion.__table__.create(db.engine)
            db.session.add(Device(
                id='camera-01',
                name='Gate camera',
                source='rtsp://camera-01/live',
                rtmp_stream='rtmp://camera-01/live',
                http_stream='http://camera-01/live.flv',
                manufacturer='test',
                model='test-camera',
            ))
            db.session.commit()

        self.client = self.app.test_client()

    def tearDown(self):
        with self.app.app_context():
            db.session.remove()
            db.engine.dispose()
        self.temp_dir.cleanup()

    def _create_region(self, **thresholds):
        payload = {
            'region_name': 'Gate zone',
            'region_type': 'polygon',
            'points': [
                {'x': 0.1, 'y': 0.1},
                {'x': 0.9, 'y': 0.1},
                {'x': 0.9, 'y': 0.9},
            ],
            **thresholds,
        }
        return self.client.post(
            '/video/device-detection/device/camera-01/regions',
            json=payload,
        )

    def test_create_with_camel_case_persists_and_list_returns_both_key_styles(self):
        response = self._create_region(inertiaFrames=3, loiteringSeconds=20)

        self.assertEqual(200, response.status_code, response.get_json())
        created = response.get_json()['data']
        self.assertEqual(3, created['inertia_frames'])
        self.assertEqual(3, created['inertiaFrames'])
        self.assertEqual(20, created['loitering_seconds'])
        self.assertEqual(20, created['loiteringSeconds'])

        listed_response = self.client.get(
            '/video/device-detection/device/camera-01/regions'
        )
        self.assertEqual(200, listed_response.status_code, listed_response.get_json())
        listed = listed_response.get_json()['data'][0]
        self.assertEqual(3, listed['inertia_frames'])
        self.assertEqual(3, listed['inertiaFrames'])
        self.assertEqual(20, listed['loitering_seconds'])
        self.assertEqual(20, listed['loiteringSeconds'])

        with self.app.app_context():
            stored = db.session.get(DeviceDetectionRegion, created['id'])
            self.assertEqual(3, stored.inertia_frames)
            self.assertEqual(20, stored.loitering_seconds)

    def test_create_defaults_then_snake_update_are_persisted_on_readback(self):
        response = self._create_region()
        self.assertEqual(200, response.status_code, response.get_json())
        created = response.get_json()['data']
        self.assertEqual(1, created['inertia_frames'])
        self.assertEqual(1, created['inertiaFrames'])
        self.assertEqual(5, created['loitering_seconds'])
        self.assertEqual(5, created['loiteringSeconds'])

        updated_response = self.client.put(
            f"/video/device-detection/region/{created['id']}",
            json={'inertia_frames': 7, 'loitering_seconds': 45},
        )
        self.assertEqual(200, updated_response.status_code, updated_response.get_json())
        updated = updated_response.get_json()['data']
        self.assertEqual(7, updated['inertia_frames'])
        self.assertEqual(7, updated['inertiaFrames'])
        self.assertEqual(45, updated['loitering_seconds'])
        self.assertEqual(45, updated['loiteringSeconds'])

        listed = self.client.get(
            '/video/device-detection/device/camera-01/regions'
        ).get_json()['data'][0]
        self.assertEqual(7, listed['inertia_frames'])
        self.assertEqual(45, listed['loitering_seconds'])

        with self.app.app_context():
            db.session.expire_all()
            stored = db.session.get(DeviceDetectionRegion, created['id'])
            self.assertEqual(7, stored.inertia_frames)
            self.assertEqual(45, stored.loitering_seconds)

    def test_update_accepts_camel_case_aliases(self):
        created = self._create_region(
            inertia_frames=2,
            loitering_seconds=10,
        ).get_json()['data']

        response = self.client.put(
            f"/video/device-detection/region/{created['id']}",
            json={'inertiaFrames': 4, 'loiteringSeconds': 30},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        updated = response.get_json()['data']
        self.assertEqual(4, updated['inertia_frames'])
        self.assertEqual(30, updated['loitering_seconds'])

    def test_rejects_out_of_range_and_non_integer_thresholds(self):
        created = self._create_region().get_json()['data']
        invalid_values = (
            ('inertia_frames', -1),
            ('inertia_frames', 10001),
            ('inertia_frames', True),
            ('inertia_frames', 1.5),
            ('inertia_frames', '3'),
            ('loitering_seconds', -1),
            ('loitering_seconds', 86401),
            ('loitering_seconds', False),
            ('loitering_seconds', 5.5),
            ('loitering_seconds', '5'),
        )

        for field, value in invalid_values:
            with self.subTest(field=field, value=value):
                response = self.client.put(
                    f"/video/device-detection/region/{created['id']}",
                    json={field: value},
                )
                self.assertEqual(400, response.status_code, response.get_json())

        with self.app.app_context():
            db.session.expire_all()
            stored = db.session.get(DeviceDetectionRegion, created['id'])
            self.assertEqual(1, stored.inertia_frames)
            self.assertEqual(5, stored.loitering_seconds)


class TestDeviceDetectionRegionLegacyMigration(unittest.TestCase):
    def test_production_sql_migration_versions_region_rule_columns(self):
        migration = Path(VIDEO_DIR) / 'migrations' / (
            'V20260711__device_detection_region_rule_fields.sql'
        )
        self.assertTrue(migration.is_file())
        sql = migration.read_text(encoding='utf-8')
        self.assertIn('ALTER TABLE device_detection_region', sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS inertia_frames', sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS loitering_seconds', sql)
        self.assertIn('UPDATE device_detection_region', sql)
        self.assertIn('ALTER COLUMN inertia_frames SET NOT NULL', sql)
        self.assertIn('ALTER COLUMN loitering_seconds SET NOT NULL', sql)
        run_source = (Path(VIDEO_DIR) / 'run.py').read_text(encoding='utf-8')
        self.assertNotIn('ensure_device_detection_region_rule_columns(db.engine)', run_source)

    def test_existing_rows_receive_defaults_and_migration_is_idempotent(self):
        ensure_columns = getattr(
            models,
            'ensure_device_detection_region_rule_columns',
            None,
        )
        self.assertTrue(callable(ensure_columns))

        engine = create_engine('sqlite:///:memory:')
        with engine.begin() as connection:
            connection.execute(text(
                'CREATE TABLE device_detection_region '
                '(id INTEGER PRIMARY KEY, region_name VARCHAR(255) NOT NULL)'
            ))
            connection.execute(text(
                "INSERT INTO device_detection_region (id, region_name) VALUES (1, 'legacy')"
            ))

        ensure_columns(engine)
        ensure_columns(engine)

        column_names = [
            column['name']
            for column in inspect(engine).get_columns('device_detection_region')
        ]
        self.assertEqual(1, column_names.count('inertia_frames'))
        self.assertEqual(1, column_names.count('loitering_seconds'))
        with engine.connect() as connection:
            row = connection.execute(text(
                'SELECT inertia_frames, loitering_seconds '
                'FROM device_detection_region WHERE id = 1'
            )).one()
        self.assertEqual((1, 5), tuple(row))

    def test_existing_nullable_columns_are_backfilled_idempotently(self):
        engine = create_engine('sqlite:///:memory:')
        with engine.begin() as connection:
            connection.execute(text(
                'CREATE TABLE device_detection_region '
                '(id INTEGER PRIMARY KEY, region_name VARCHAR(255) NOT NULL, '
                'inertia_frames INTEGER, loitering_seconds INTEGER)'
            ))
            connection.execute(text(
                "INSERT INTO device_detection_region "
                "(id, region_name, inertia_frames, loitering_seconds) "
                "VALUES (1, 'partial-legacy', NULL, NULL)"
            ))

        models.ensure_device_detection_region_rule_columns(engine)
        models.ensure_device_detection_region_rule_columns(engine)

        with engine.connect() as connection:
            row = connection.execute(text(
                'SELECT inertia_frames, loitering_seconds '
                'FROM device_detection_region WHERE id = 1'
            )).one()
        self.assertEqual((1, 5), tuple(row))


if __name__ == '__main__':
    unittest.main()
