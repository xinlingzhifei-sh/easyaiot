"""Tenant/camera authorization regression tests for record space listings."""
from __future__ import annotations

import os
import sys
import tempfile
import types
import unittest
from unittest import mock

import test_media_authorization as media_tests


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


class RecordSpaceTenantListingBlueprintTest(media_tests._ModuleIsolationTestCase):

    def setUp(self):
        super().setUp()
        self.audit_dir = tempfile.TemporaryDirectory()
        self.previous_env = {
            name: os.environ.get(name)
            for name in (
                'YFEIEYE_MEDIA_AUTHORIZATION_URL',
                'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR',
            )
        }
        os.environ['YFEIEYE_MEDIA_AUTHORIZATION_URL'] = (
            'http://device.local/admin-api/system/auth/media-permission-check')
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'] = self.audit_dir.name

    def tearDown(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        self.audit_dir.cleanup()

    @staticmethod
    def _authorization_response(camera_id):
        allowed = camera_id == 'camera-01'
        return media_tests._JsonResponse({
            'code': 0,
            'data': {
                'allowed': allowed,
                'userId': 42,
                'tenantId': 7,
                'cameraId': camera_id,
                'action': 'record_manage',
                'reason': 'granted' if allowed else 'camera_scope_denied',
            },
        })

    def test_list_without_camera_hint_returns_only_individually_authorized_spaces(self):
        record_module = media_tests.TestRecordMediaAuthorization._record_blueprint()
        record_module.list_record_space_authorization_scopes = lambda camera_id=None: [
            {'tenant_id': 7, 'camera_id': 'camera-01', 'space_id': 1},
            {'tenant_id': 7, 'camera_id': 'camera-02', 'space_id': 2},
            {'tenant_id': 8, 'camera_id': 'camera-03', 'space_id': 3},
        ]
        captured = []
        record_module.list_record_spaces = lambda *args, **kwargs: (
            captured.append((args, kwargs)) or {
                'items': [{
                    'id': 1,
                    'tenant_id': 7,
                    'device_id': 'camera-01',
                }],
                'total': 1,
                'parent_key': 'root',
            })

        def authorize(_url, **kwargs):
            return self._authorization_response(kwargs['json'].get('cameraId'))

        with mock.patch('requests.post', side_effect=authorize) as requests_post:
            response = media_tests.TestRecordMediaAuthorization._app(
                record_module).test_client().get(
                    '/video/record/space/list',
                    headers={'Authorization': 'Bearer scoped-user'},
                )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, response.get_json()['total'])
        self.assertEqual(1, len(captured))
        self.assertEqual(7, captured[0][1]['tenant_id'])
        self.assertEqual(['camera-01'], captured[0][1]['camera_ids'])
        self.assertEqual(
            {'camera-01', 'camera-02', 'camera-03'},
            {call.kwargs['json'].get('cameraId') for call in requests_post.call_args_list},
        )

    def test_authorized_camera_hint_cannot_expand_to_other_cameras(self):
        record_module = media_tests.TestRecordMediaAuthorization._record_blueprint()
        requested_scopes = []

        def scopes(camera_id=None):
            requested_scopes.append(camera_id)
            return [
                {'tenant_id': 7, 'camera_id': 'camera-01', 'space_id': 1},
            ] if camera_id == 'camera-01' else []

        record_module.list_record_space_authorization_scopes = scopes
        captured = []
        record_module.list_record_spaces = lambda *args, **kwargs: (
            captured.append(kwargs) or {
                'items': [{'id': 1, 'device_id': 'camera-01'}],
                'total': 1,
                'parent_key': 'root',
            })

        with mock.patch(
                'requests.post',
                return_value=self._authorization_response('camera-01')):
            response = media_tests.TestRecordMediaAuthorization._app(
                record_module).test_client().get(
                    '/video/record/space/list?camera_id=camera-01',
                    headers={'Authorization': 'Bearer scoped-user'},
                )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(['camera-01'], requested_scopes)
        self.assertEqual(['camera-01'], captured[0]['camera_ids'])
        self.assertEqual(7, captured[0]['tenant_id'])

    def test_list_fails_closed_when_no_candidate_camera_is_authorized(self):
        record_module = media_tests.TestRecordMediaAuthorization._record_blueprint()
        record_module.list_record_space_authorization_scopes = lambda camera_id=None: [
            {'tenant_id': 7, 'camera_id': 'camera-02', 'space_id': 2},
        ]
        called = []
        record_module.list_record_spaces = lambda *args, **kwargs: called.append(
            kwargs) or {'items': [], 'total': 0}

        with mock.patch(
                'requests.post',
                return_value=self._authorization_response('camera-02')):
            response = media_tests.TestRecordMediaAuthorization._app(
                record_module).test_client().get(
                    '/video/record/space/list',
                    headers={'Authorization': 'Bearer scoped-user'},
                )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual([], called)

    def test_storage_sync_only_receives_individually_authorized_space_scope(self):
        record_module = media_tests.TestRecordMediaAuthorization._record_blueprint()
        record_module.list_record_space_authorization_scopes = lambda camera_id=None: [
            {'tenant_id': 7, 'camera_id': 'camera-01', 'space_id': 1},
            {'tenant_id': 7, 'camera_id': 'camera-02', 'space_id': 2},
            {'tenant_id': 8, 'camera_id': 'camera-03', 'space_id': 3},
        ]
        captured = []
        record_module.sync_spaces_to_minio = lambda **kwargs: (
            captured.append(kwargs) or {
                'total_spaces': 1,
                'created_count': 1,
                'skipped_count': 0,
                'error_count': 0,
            })

        def authorize(_url, **kwargs):
            return self._authorization_response(kwargs['json'].get('cameraId'))

        with mock.patch('requests.post', side_effect=authorize):
            response = media_tests.TestRecordMediaAuthorization._app(
                record_module).test_client().post(
                    '/video/record/space/sync/minio',
                    headers={'Authorization': 'Bearer scoped-user'},
                )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual([{
            'tenant_id': 7,
            'camera_ids': ['camera-01'],
        }], captured)


class RecordSpaceFolderScopeQueryTest(unittest.TestCase):

    def test_space_loader_filters_tenant_and_allowed_camera_ids_before_grouping(self):
        from app.services import space_folder_tree_service as service

        class Column:
            def __init__(self, name):
                self.name = name

            def __eq__(self, value):
                return ('eq', self.name, value)

            def in_(self, values):
                return ('in', self.name, tuple(values))

            def isnot(self, value):
                return ('isnot', self.name, value)

        class Query:
            def __init__(self, rows):
                self.rows = rows
                self.filters = []

            def filter(self, *expressions):
                self.filters.extend(expressions)
                return self

            def all(self):
                return list(self.rows)

        space_query = Query([
            types.SimpleNamespace(
                id=1, tenant_id=7, device_id='camera-01')])
        device_query = Query([
            types.SimpleNamespace(id='camera-01')])
        SpaceModel = types.SimpleNamespace(
            query=space_query,
            tenant_id=Column('tenant_id'),
            device_id=Column('device_id'),
        )
        Device = types.SimpleNamespace(
            query=device_query,
            id=Column('id'),
        )

        with mock.patch.object(service, '_space_model', return_value=SpaceModel), \
                mock.patch.object(service, 'Device', Device):
            spaces, devices = service._load_spaces_with_devices(
                'record', tenant_id=7, camera_ids=['camera-01'])

        self.assertEqual(['camera-01'], [space.device_id for space in spaces])
        self.assertEqual(['camera-01'], list(devices))
        self.assertIn(('eq', 'tenant_id', 7), space_query.filters)
        self.assertIn(('in', 'device_id', ('camera-01',)), space_query.filters)
        self.assertIn(('isnot', 'device_id', None), space_query.filters)

    def test_storage_sync_filters_tenant_and_camera_before_touching_minio(self):
        from app.services import record_space_service as service

        class Column:
            def __init__(self, name):
                self.name = name

            def __eq__(self, value):
                return ('eq', self.name, value)

            def in_(self, values):
                return ('in', self.name, tuple(values))

        class Query:
            def __init__(self):
                self.filters = []

            def filter(self, *expressions):
                self.filters.extend(expressions)
                return self

            def all(self):
                return []

        query = Query()
        RecordSpace = types.SimpleNamespace(
            query=query,
            tenant_id=Column('tenant_id'),
            device_id=Column('device_id'),
        )
        with mock.patch.object(service, 'RecordSpace', RecordSpace), \
                mock.patch.object(service, 'minio_storage_enabled', return_value=False):
            result = service.sync_spaces_to_minio(
                tenant_id=7, camera_ids=['camera-01'])

        self.assertEqual(0, result['total_spaces'])
        self.assertIn(('eq', 'tenant_id', 7), query.filters)
        self.assertIn(('in', 'device_id', ('camera-01',)), query.filters)


if __name__ == '__main__':
    unittest.main()
