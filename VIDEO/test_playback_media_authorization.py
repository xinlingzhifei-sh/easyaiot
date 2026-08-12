"""Playback metadata authorization and tenant-scope regression tests."""
import importlib
import os
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

from flask import Flask


class _JsonResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self.status_code = status_code

    def json(self):
        return self._payload


class _Column:
    def __init__(self, name):
        self.name = name

    def __eq__(self, value):
        return ('eq', self.name, value)

    def __ge__(self, value):
        return ('ge', self.name, value)

    def __le__(self, value):
        return ('le', self.name, value)

    def ilike(self, value):
        return ('ilike', self.name, value)

    def desc(self):
        return ('desc', self.name)


class PlaybackMediaAuthorizationTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_env = {
            name: os.environ.get(name)
            for name in (
                'YFEIEYE_MEDIA_AUTHORIZATION_URL',
                'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR',
            )
        }
        os.environ['YFEIEYE_MEDIA_AUTHORIZATION_URL'] = (
            'http://device.local/admin-api/system/auth/media-permission-check')
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'] = self.temp_dir.name
        self.previous_models = sys.modules.get('models')
        self.previous_blueprint = sys.modules.get('app.blueprints.playback')

    def tearDown(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        if self.previous_models is None:
            sys.modules.pop('models', None)
        else:
            sys.modules['models'] = self.previous_models
        if self.previous_blueprint is None:
            sys.modules.pop('app.blueprints.playback', None)
        else:
            sys.modules['app.blueprints.playback'] = self.previous_blueprint
        self.temp_dir.cleanup()

    def test_anonymous_playback_list_is_denied_before_metadata_query(self):
        playback_module, query, _created = self._playback_blueprint()

        response = self._app(playback_module).test_client().get(
            '/video/playback/list?device_id=camera-01')

        self.assertEqual(401, response.status_code, response.get_json())
        self.assertEqual('authentication_required', response.get_json()['reason'])
        self.assertEqual(0, query.paginate_calls)

    def test_playback_list_filters_authenticated_tenant_camera_and_redacts_paths(self):
        playback_module, query, _created = self._playback_blueprint()
        authorization = self._authorization(
            tenant_id=7, camera_id='camera-01', action='playback')

        with patch('requests.post', return_value=authorization):
            response = self._app(playback_module).test_client().get(
                '/video/playback/list?device_id=camera-01',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertIn({'tenant_id': 7, 'device_id': 'camera-01'}, query.filter_by_calls)
        item = response.get_json()['data'][0]
        self.assertNotIn('file_path', item)
        self.assertNotIn('thumbnail_path', item)
        self.assertNotIn('/private/', str(item))

    def test_playback_create_uses_authenticated_tenant_and_camera_not_payload_scope(self):
        playback_module, _query, created = self._playback_blueprint()
        authorization = self._authorization(
            tenant_id=7, camera_id='camera-01', action='record_manage')

        with patch('requests.post', return_value=authorization):
            response = self._app(playback_module).test_client().post(
                '/video/playback/',
                json={
                    'tenant_id': 99,
                    'file_path': '/private/tenant-99.mp4',
                    'event_time': '2026-07-13T09:00:00+08:00',
                    'device_id': 'camera-01',
                    'device_name': 'camera',
                    'duration': 10,
                },
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, len(created))
        self.assertIn('tenant_id', created[0])
        self.assertEqual(7, created[0]['tenant_id'])
        self.assertEqual('camera-01', created[0]['device_id'])

    @staticmethod
    def _authorization(tenant_id, camera_id, action):
        return _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': tenant_id,
                'cameraId': camera_id,
                'action': action,
                'reason': 'granted',
            },
        })

    @staticmethod
    def _app(playback_module):
        app = Flask(__name__)
        app.register_blueprint(
            playback_module.playback_bp, url_prefix='/video/playback')
        return app

    @staticmethod
    def _playback_blueprint():
        created = []

        class Row:
            id = 1
            tenant_id = 7
            device_id = 'camera-01'
            device_name = 'camera'
            file_path = '/private/tenant-7-camera-01.mp4'
            thumbnail_path = '/private/tenant-7-camera-01.jpg'
            event_time = None
            duration = 10
            file_size = 100
            created_at = None
            updated_at = None

            def to_dict(self):
                return {
                    'id': self.id,
                    'tenant_id': self.tenant_id,
                    'device_id': self.device_id,
                    'device_name': self.device_name,
                    'file_path': self.file_path,
                    'thumbnail_path': self.thumbnail_path,
                }

        class Query:
            def __init__(self):
                self.filter_by_calls = []
                self.paginate_calls = 0

            def filter_by(self, **filters):
                self.filter_by_calls.append(filters)
                return self

            def filter(self, *criteria):
                return self

            def order_by(self, *criteria):
                return self

            def paginate(self, **kwargs):
                self.paginate_calls += 1
                return types.SimpleNamespace(items=[Row()], total=1)

            def get(self, playback_id):
                return Row() if int(playback_id) == 1 else None

        query = Query()

        class Playback:
            tenant_id = _Column('tenant_id')
            device_id = _Column('device_id')
            device_name = _Column('device_name')
            file_path = _Column('file_path')
            event_time = _Column('event_time')
            def __init__(self, **values):
                created.append(dict(values))
                for key, value in values.items():
                    setattr(self, key, value)
                self.id = 2

            def to_dict(self):
                return dict(created[-1])

        Playback.query = query

        session = types.SimpleNamespace(
            add=lambda value: None,
            commit=lambda: None,
            rollback=lambda: None,
            delete=lambda value: None,
        )
        models = types.ModuleType('models')
        models.Playback = Playback
        models.db = types.SimpleNamespace(session=session)
        sys.modules['models'] = models
        sys.modules.pop('app.blueprints.playback', None)
        return importlib.import_module('app.blueprints.playback'), query, created


if __name__ == '__main__':
    unittest.main()
