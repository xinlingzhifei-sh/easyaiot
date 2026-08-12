"""Snap management authorization, tenant isolation, and bucket privacy tests."""
import importlib
import io
import sys
import types
import unittest
from unittest.mock import patch

from flask import Flask

from app.services.media_authorization_service import MediaAuthorizationDecision


class _Query:
    def __init__(self, items=()):
        self.items = list(items)

    def get(self, key):
        return next((item for item in self.items if item.id == key), None)

    def get_or_404(self, key):
        item = self.get(key)
        if item is None:
            raise ValueError(f'missing resource: {key}')
        return item

    def filter_by(self, **filters):
        matched = [
            item for item in self.items
            if all(getattr(item, key, None) == value for key, value in filters.items())
        ]
        return _Query(matched)

    def first(self):
        return self.items[0] if self.items else None

    def all(self):
        return list(self.items)

    def order_by(self, *args, **kwargs):
        return self


def _entity(entity_id, **fields):
    fields.setdefault('to_dict', lambda: {'id': entity_id})
    return types.SimpleNamespace(id=entity_id, **fields)


class SnapAuthorizationTestCase(unittest.TestCase):
    _MODULES = (
        'models',
        'app.services.snap_space_service',
        'app.services.snap_task_service',
        'app.services.algorithm_service',
        'app.services.storage_service',
        'app.services.snap_image_service',
        'app.services.space_group_save_time_service',
        'app.services.space_save_time_service',
        'app.blueprints.snap',
    )

    def setUp(self):
        self._missing = object()
        self._previous = {
            name: sys.modules.get(name, self._missing)
            for name in self._MODULES
        }

    def tearDown(self):
        for name, previous in self._previous.items():
            if previous is self._missing:
                sys.modules.pop(name, None)
            else:
                sys.modules[name] = previous

    def test_anonymous_space_read_is_denied_before_response_serialization(self):
        snap, calls = self._blueprint()

        response = self._client(snap).get('/video/snap/space/7')

        self.assertEqual(401, response.status_code, response.get_json())
        self.assertEqual([], calls['serialize_space'])

    def test_space_update_uses_record_manage_and_persisted_owner(self):
        snap, calls = self._blueprint()

        response = self._client(snap).put(
            '/video/snap/space/7',
            json={'space_name': 'renamed'},
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertTrue(calls['authorize'])
        self.assertEqual('record_manage', calls['authorize'][-1]['action'])
        self.assertEqual('camera-01', calls['authorize'][-1]['camera_id'])
        self.assertEqual(7, calls['authorize'][-1]['owner_tenant_id'])
        self.assertEqual(1, len(calls['update_space']))

    def test_image_delete_uses_record_manage_not_snapshot(self):
        image = _entity(
            11,
            tenant_id=7,
            space_id=7,
            device_id='camera-01',
            object_name='tenants/7/cameras/camera-01/private.jpg',
        )
        snap, calls = self._blueprint(images=[image])

        response = self._client(snap).delete(
            '/video/snap/space/7/images',
            json={'object_names': [image.object_name]},
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertTrue(calls['authorize'])
        self.assertEqual('record_manage', calls['authorize'][-1]['action'])
        self.assertEqual(1, len(calls['delete_images']))

    def test_task_update_rejects_body_camera_different_from_persisted_task(self):
        snap, calls = self._blueprint()

        response = self._client(snap).put(
            '/video/snap/task/9',
            json={'device_id': 'camera-02'},
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('snap_camera_scope_mismatch', response.get_json()['reason'])
        self.assertEqual([], calls['update_task'])
        self.assertTrue(any(
            audit['decision'].reason == 'snap_camera_scope_mismatch'
            for audit in calls['audit']
        ))

    def test_region_service_delete_resolves_region_task_space_owner(self):
        snap, calls = self._blueprint()

        response = self._client(snap).delete(
            '/video/snap/region-service/33',
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertTrue(calls['authorize'])
        self.assertEqual('record_manage', calls['authorize'][-1]['action'])
        self.assertEqual('camera-01', calls['authorize'][-1]['camera_id'])
        self.assertEqual(7, calls['authorize'][-1]['owner_tenant_id'])
        self.assertEqual([33], calls['delete_region_service'])

    def test_task_algorithm_service_resolves_algorithm_task_snap_space_owner(self):
        snap, calls = self._blueprint()

        response = self._client(snap).post(
            '/video/snap/task/19/service',
            json={'service_name': 'model', 'service_url': 'http://model'},
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual('record_manage', calls['authorize'][-1]['action'])
        self.assertEqual('camera-01', calls['authorize'][-1]['camera_id'])
        self.assertEqual(7, calls['authorize'][-1]['owner_tenant_id'])

    def test_region_service_resolves_unified_snap_algorithm_task_owner(self):
        snap, calls = self._blueprint()

        response = self._client(snap).delete(
            '/video/snap/region-service/44',
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual('camera-01', calls['authorize'][-1]['camera_id'])
        self.assertEqual(7, calls['authorize'][-1]['owner_tenant_id'])
        self.assertEqual([44], calls['delete_region_service'])

    def test_space_list_filters_before_pagination_by_allowed_tenant_cameras(self):
        snap, calls = self._blueprint(deny_cameras={'camera-02'})

        response = self._client(snap).get(
            '/video/snap/space/list?pageNo=2&pageSize=5',
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, len(calls['list_spaces']))
        self.assertEqual(7, calls['list_spaces'][0].get('tenant_id'))
        self.assertEqual(['camera-01'], calls['list_spaces'][0].get('camera_ids'))
        self.assertEqual(2, calls['list_spaces'][0]['page_no'])
        self.assertEqual(5, calls['list_spaces'][0]['page_size'])

    def test_task_list_filters_before_pagination_by_allowed_tenant_cameras(self):
        snap, calls = self._blueprint(deny_cameras={'camera-02'})

        response = self._client(snap).get(
            '/video/snap/task/list?pageNo=3&pageSize=4',
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, len(calls['list_tasks']))
        self.assertEqual(7, calls['list_tasks'][0].get('tenant_id'))
        self.assertEqual(['camera-01'], calls['list_tasks'][0].get('camera_ids'))
        self.assertEqual(3, calls['list_tasks'][0]['page_no'])
        self.assertEqual(4, calls['list_tasks'][0]['page_size'])

    def test_group_policy_rejects_entire_write_if_any_group_camera_is_denied(self):
        snap, calls = self._blueprint(deny_cameras={'camera-02'})

        response = self._client(snap).put(
            '/video/snap/space/group-policy',
            json={'group_type': 'nvr', 'group_key': '12', 'save_time': 24},
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual([], calls['update_group'])

    def test_group_policy_passes_authorized_tenant_and_camera_scope_to_service(self):
        snap, calls = self._blueprint()

        response = self._client(snap).put(
            '/video/snap/space/group-policy',
            json={'group_type': 'nvr', 'group_key': '12', 'save_time': 24},
            headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, len(calls['update_group']))
        self.assertEqual(7, calls['update_group'][0].get('tenant_id'))
        self.assertEqual(
            ['camera-01', 'camera-02'],
            calls['update_group'][0].get('camera_ids'),
        )

    def test_every_snap_mutation_requires_record_manage(self):
        object_name = 'tenants/7/cameras/camera-01/private.jpg'
        image = _entity(
            11,
            tenant_id=7,
            space_id=7,
            device_id='camera-01',
            object_name=object_name,
        )
        cases = (
            ('post', '/video/snap/space', {}),
            ('put', '/video/snap/space/7', {'space_name': 'renamed'}),
            ('delete', '/video/snap/space/7', None),
            ('post', '/video/snap/space/sync/minio', None),
            ('put', '/video/snap/space/group-policy', {
                'group_type': 'nvr', 'group_key': '12', 'save_time': 24}),
            ('post', '/video/snap/task', {
                'task_name': 'task', 'space_id': 7, 'device_id': 'camera-01'}),
            ('put', '/video/snap/task/9', {'task_name': 'renamed'}),
            ('delete', '/video/snap/task/9', None),
            ('post', '/video/snap/task/9/start', None),
            ('post', '/video/snap/task/9/stop', None),
            ('post', '/video/snap/task/9/restart', None),
            ('post', '/video/snap/region', {
                'task_id': 9,
                'region_name': 'zone',
                'points': [[0, 0], [1, 0], [1, 1]],
            }),
            ('put', '/video/snap/region/5', {'region_name': 'renamed'}),
            ('delete', '/video/snap/region/5', None),
            ('post', '/video/snap/task/19/service', {
                'service_name': 'model', 'service_url': 'http://model'}),
            ('put', '/video/snap/service/22', {'service_name': 'model'}),
            ('delete', '/video/snap/service/22', None),
            ('post', '/video/snap/region/5/service', {
                'service_name': 'model', 'service_url': 'http://model'}),
            ('put', '/video/snap/region-service/33', {'service_name': 'model'}),
            ('delete', '/video/snap/region-service/33', None),
            ('put', '/video/snap/device/camera-01/storage', {}),
            ('post', '/video/snap/device/camera-01/storage/cleanup', None),
            ('delete', '/video/snap/space/7/images', {
                'object_names': [object_name]}),
            ('post', '/video/snap/space/7/images/sync', None),
            ('post', '/video/snap/space/7/images/cleanup', {
                'save_time_hours': 24}),
        )

        for method, path, payload in cases:
            with self.subTest(method=method, path=path):
                snap, calls = self._blueprint(images=[image])
                response = getattr(self._client(snap), method)(path, json=payload)
                self.assertEqual(401, response.status_code, response.get_json())
                self.assertTrue(calls['authorize'])
                self.assertTrue(all(
                    call['action'] == 'record_manage'
                    for call in calls['authorize']
                ))

    def test_snap_management_reads_use_snapshot_action(self):
        object_name = 'tenants/7/cameras/camera-01/private.jpg'
        image = _entity(
            11,
            tenant_id=7,
            space_id=7,
            device_id='camera-01',
            object_name=object_name,
        )
        paths = (
            '/video/snap/space/7',
            '/video/snap/task/9',
            '/video/snap/task/9/logs',
            '/video/snap/task/9/regions',
            '/video/snap/region/5',
            '/video/snap/task/19/services',
            '/video/snap/region/5/services',
            '/video/snap/device/camera-01/storage',
            '/video/snap/space/7/images',
            f'/video/snap/space/7/image/{object_name}',
        )

        for path in paths:
            with self.subTest(path=path):
                snap, calls = self._blueprint(images=[image])
                response = self._client(snap).get(
                    path,
                    headers={
                        'Authorization': 'Bearer scoped-user',
                        'tenant-id': '7',
                    },
                )
                self.assertNotEqual(401, response.status_code, response.get_json())
                self.assertTrue(calls['authorize'])
                self.assertTrue(all(
                    call['action'] == 'snapshot'
                    for call in calls['authorize']
                ))

    @staticmethod
    def _client(snap):
        app = Flask(__name__)
        app.register_blueprint(snap.snap_bp, url_prefix='/video/snap')
        return app.test_client()

    @staticmethod
    def _blueprint(deny_cameras=None, images=()):
        deny_cameras = set(deny_cameras or ())
        calls = {
            'authorize': [],
            'audit': [],
            'serialize_space': [],
            'update_space': [],
            'delete_images': [],
            'update_task': [],
            'delete_region_service': [],
            'list_spaces': [],
            'list_tasks': [],
            'update_group': [],
        }
        spaces = [
            _entity(
                7,
                device_id='camera-01',
                tenant_id=7,
                to_dict=lambda: calls['serialize_space'].append(7) or {'id': 7},
            ),
            _entity(8, device_id='camera-02', tenant_id=7),
        ]
        tasks = [_entity(9, device_id='camera-01', space_id=7)]
        algorithm_tasks = [_entity(
            19,
            task_type='snap',
            space_id=7,
            devices=[types.SimpleNamespace(id='camera-01')],
        )]
        regions = [_entity(5, task_id=9), _entity(15, task_id=19)]
        task_services = [_entity(22, task_id=19)]
        region_services = [
            _entity(33, region_id=5),
            _entity(44, region_id=15),
        ]

        models = types.ModuleType('models')
        models.db = types.SimpleNamespace(session=types.SimpleNamespace(
            rollback=lambda: None,
            commit=lambda: None,
            delete=lambda value: None,
            add=lambda value: None,
        ))
        models.SnapSpace = types.SimpleNamespace(query=_Query(spaces))
        models.SnapTask = types.SimpleNamespace(query=_Query(tasks))
        models.AlgorithmTask = types.SimpleNamespace(query=_Query(algorithm_tasks))
        models.DetectionRegion = types.SimpleNamespace(
            query=_Query(regions), sort_order=object(), id=object())
        models.AlgorithmModelService = types.SimpleNamespace(
            query=_Query(task_services))
        models.RegionModelService = types.SimpleNamespace(
            query=_Query(region_services))
        models.SnapImage = types.SimpleNamespace(query=_Query(images))
        models.Device = types.SimpleNamespace(query=_Query())
        models.parse_shanghai_naive_to_utc_naive = lambda value: value
        sys.modules['models'] = models

        space_service = types.ModuleType('app.services.snap_space_service')
        space_service.get_snap_space = lambda space_id: _Query(spaces).get(space_id)
        space_service.get_snap_space_by_device_id = lambda device_id, **kwargs: next(
            (space for space in spaces if space.device_id == device_id), None)
        space_service.list_snap_space_authorization_scopes = lambda camera_id=None: [
            {'tenant_id': 7, 'camera_id': space.device_id, 'space_id': space.id}
            for space in spaces
            if not camera_id or space.device_id == camera_id
        ]
        space_service.list_snap_group_authorization_scopes = (
            lambda group_type, group_key: [
                {'tenant_id': 7, 'camera_id': space.device_id, 'space_id': space.id}
                for space in spaces
            ]
        )

        def list_spaces(page_no, page_size, search, parent_key, scope, **kwargs):
            calls['list_spaces'].append({
                'page_no': page_no,
                'page_size': page_size,
                **kwargs,
            })
            return {'items': [], 'total': 0}

        space_service.list_snap_spaces = list_spaces
        space_service.create_snap_space = lambda *args, **kwargs: None

        def update_space(*args, **kwargs):
            calls['update_space'].append((args, kwargs))
            return spaces[0]

        space_service.update_snap_space = update_space
        space_service.delete_snap_space = lambda *args, **kwargs: None
        space_service.sync_spaces_to_minio = lambda *args, **kwargs: {
            'total_spaces': 0,
            'created_count': 0,
            'skipped_count': 0,
            'error_count': 0,
        }
        sys.modules['app.services.snap_space_service'] = space_service

        task_service = types.ModuleType('app.services.snap_task_service')
        task_service.create_snap_task = lambda *args, **kwargs: tasks[0]

        def update_task(*args, **kwargs):
            calls['update_task'].append((args, kwargs))
            return tasks[0]

        task_service.update_snap_task = update_task
        task_service.delete_snap_task = lambda *args, **kwargs: True
        task_service.get_snap_task = lambda task_id: {'id': task_id}

        def list_tasks(page_no, page_size, space_id, device_id, search, status, **kwargs):
            calls['list_tasks'].append({
                'page_no': page_no,
                'page_size': page_size,
                **kwargs,
            })
            return {'items': [], 'total': 0}

        task_service.list_snap_tasks = list_tasks
        task_service.start_task = lambda task_id: tasks[0]
        task_service.stop_task = lambda task_id: tasks[0]
        task_service.restart_task = lambda task_id: tasks[0]
        task_service.get_task_logs = lambda *args, **kwargs: {
            'logs': [], 'total': 0}
        sys.modules['app.services.snap_task_service'] = task_service

        algorithm_service = types.ModuleType('app.services.algorithm_service')
        algorithm_service.create_task_algorithm_service = lambda **kwargs: task_services[0]
        algorithm_service.update_task_algorithm_service = lambda *args, **kwargs: task_services[0]
        algorithm_service.delete_task_algorithm_service = lambda service_id: True
        algorithm_service.get_task_algorithm_services = lambda task_id: []
        algorithm_service.create_region_algorithm_service = lambda **kwargs: region_services[0]
        algorithm_service.update_region_algorithm_service = lambda *args, **kwargs: region_services[0]

        def delete_region_service(service_id):
            calls['delete_region_service'].append(service_id)
            return True

        algorithm_service.delete_region_algorithm_service = delete_region_service
        algorithm_service.get_region_algorithm_services = lambda region_id: []
        sys.modules['app.services.algorithm_service'] = algorithm_service

        storage_service = types.ModuleType('app.services.storage_service')
        storage_value = _entity(1, device_id='camera-01')
        storage_service.get_or_create_device_storage_config = lambda device_id: storage_value
        storage_service.update_device_storage_config = lambda *args, **kwargs: storage_value
        storage_service.get_device_storage_info = lambda device_id: {}
        storage_service.check_and_cleanup_storage = lambda device_id: {}
        sys.modules['app.services.storage_service'] = storage_service

        image_service = types.ModuleType('app.services.snap_image_service')
        image_service.list_snap_images = lambda *args, **kwargs: {'items': [], 'total': 0}

        def delete_images(*args, **kwargs):
            calls['delete_images'].append((args, kwargs))
            return {'deleted_count': 1, 'failed_count': 0, 'failed_objects': []}

        image_service.delete_snap_images = delete_images
        image_service.get_snap_image = lambda *args, **kwargs: (
            b'image', 'image/jpeg', 'private.jpg')
        image_service.cleanup_old_images_by_save_time = lambda *args, **kwargs: {}
        image_service.sync_snap_images_metadata = lambda *args, **kwargs: {}
        sys.modules['app.services.snap_image_service'] = image_service

        group_service = types.ModuleType(
            'app.services.space_group_save_time_service')

        def update_group(group_type, group_key, space_kind, save_time, **kwargs):
            calls['update_group'].append(kwargs)
            policy = types.SimpleNamespace(
                group_type=group_type,
                group_key=group_key,
                snap_save_time=save_time,
            )
            return policy, len(kwargs.get('camera_ids') or [])

        group_service.update_group_save_time = update_group
        sys.modules['app.services.space_group_save_time_service'] = group_service
        save_time_service = types.ModuleType('app.services.space_save_time_service')
        save_time_service.SPACE_KIND_SNAP = 'snap'
        sys.modules['app.services.space_save_time_service'] = save_time_service

        sys.modules.pop('app.blueprints.snap', None)
        snap = importlib.import_module('app.blueprints.snap')

        def authorize(req, action, camera_id=None, resource=None,
                      owner_tenant_id=None, **kwargs):
            calls['authorize'].append({
                'action': action,
                'camera_id': camera_id,
                'owner_tenant_id': owner_tenant_id,
            })
            if not req.headers.get('Authorization'):
                return MediaAuthorizationDecision(
                    False, None, None, camera_id, action,
                    'authentication_required', 401,
                )
            if camera_id in deny_cameras:
                return MediaAuthorizationDecision(
                    False, '42', '7', camera_id, action,
                    'camera_not_allowed', 403, 'bearer',
                )
            return MediaAuthorizationDecision(
                True, '42', str(owner_tenant_id or 7), camera_id, action,
                'granted', 200, 'bearer',
            )

        snap.authorize_media_request = authorize
        snap.append_media_access_audit = lambda decision, **kwargs: (
            calls['audit'].append({'decision': decision, **kwargs}))

        def audit_response(decision, **kwargs):
            from flask import after_this_request

            @after_this_request
            def append_final(response):
                payload = response.get_json(silent=True) if response.is_json else {}
                allowed = decision.allowed and response.status_code < 400
                reason = payload.get('reason') or (
                    decision.reason if allowed or not decision.allowed
                    else f'http_{response.status_code}')
                final = MediaAuthorizationDecision(
                    allowed,
                    decision.user_id,
                    decision.tenant_id,
                    decision.camera_id,
                    decision.action,
                    reason,
                    response.status_code if not allowed else 200,
                    decision.auth_type,
                    decision.service_id,
                )
                calls['audit'].append({'decision': final, **kwargs})
                return response

            return decision

        snap.audit_media_response = audit_response

        return snap, calls


class SnapBucketPrivacyTestCase(unittest.TestCase):
    def test_snap_space_sync_filters_authorized_scope_and_enforces_private_bucket(self):
        service = importlib.import_module('app.services.snap_space_service')
        filters = []
        private_calls = []

        class Field:
            def __eq__(self, value):
                return ('eq', value)

            def in_(self, values):
                return ('in', tuple(values))

        class Query:
            def filter(self, *criteria):
                filters.append(criteria)
                return self

            @staticmethod
            def all():
                return []

        model = types.SimpleNamespace(
            tenant_id=Field(),
            device_id=Field(),
            query=Query(),
        )
        client = types.SimpleNamespace(
            bucket_exists=lambda bucket: True,
            delete_bucket_policy=lambda bucket: private_calls.append(bucket),
        )

        with patch.object(service, 'SnapSpace', model), \
                patch.object(service, 'minio_storage_enabled', return_value=True), \
                patch.object(service, 'get_minio_client', return_value=client):
            result = service.sync_spaces_to_minio(
                tenant_id=7, camera_ids=['camera-01'])

        self.assertEqual(0, result['total_spaces'])
        self.assertEqual([(('eq', 7), ('in', ('camera-01',)))], filters)
        self.assertEqual(['snap-space'], private_calls)

    def test_snap_image_read_enforces_private_bucket_before_object_read(self):
        service = importlib.import_module('app.services.snap_image_service')
        space = types.SimpleNamespace(
            id=7,
            tenant_id=7,
            device_id='camera-01',
            bucket_name='snap-space',
        )
        client = types.SimpleNamespace(
            bucket_exists=lambda bucket: True,
            stat_object=lambda bucket, object_name: types.SimpleNamespace(
                content_type='image/jpeg'),
            get_object=lambda bucket, object_name: _ObjectResponse(b'image'),
        )
        private_calls = []
        object_name = 'tenants/7/cameras/camera-01/private.jpg'

        snap_space_model = types.SimpleNamespace(query=_Query([space]))
        with patch.object(service, 'SnapSpace', snap_space_model), \
                patch.object(service, 'minio_storage_enabled', return_value=True), \
                patch.object(service, 'get_minio_client', return_value=client), \
                patch.object(
                    service,
                    'ensure_bucket_private',
                    lambda minio, bucket: private_calls.append((minio, bucket)),
                    create=True,
                ):
            content, content_type, filename = service.get_snap_image(
                7, object_name, tenant_id=7)

        self.assertEqual(b'image', content)
        self.assertEqual('image/jpeg', content_type)
        self.assertEqual('private.jpg', filename)
        self.assertEqual([(client, 'snap-space')], private_calls)


class _ObjectResponse(io.BytesIO):
    def release_conn(self):
        return None


if __name__ == '__main__':
    unittest.main()
