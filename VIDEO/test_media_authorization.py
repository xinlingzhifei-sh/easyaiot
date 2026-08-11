"""Fail-closed media authorization regression tests."""
import importlib
import json
import os
import subprocess
import sys
import tempfile
import time
import types
import unittest
from datetime import datetime, timedelta, timezone
from io import BytesIO
from urllib.parse import urlencode
from unittest.mock import patch

from flask import Flask, Response


class _JsonResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self.status_code = status_code

    def json(self):
        return self._payload


def _trusted_record_resolver(payload):
    record_uri = payload.get('record_uri') or payload.get('recordUri')
    camera_id = payload.get('camera_id') or payload.get('cameraId') \
        or payload.get('device_id') or payload.get('deviceId')
    return {
        'record_uri': record_uri,
        'camera_id': camera_id,
        'device_id': camera_id,
        'source': 'test_metadata',
    } if record_uri else {}


class _ModuleIsolationTestCase(unittest.TestCase):
    _ISOLATED_MODULE_NAMES = (
        'models',
        'minio',
        'minio.error',
        'app.services.record_space_service',
        'app.services.record_video_service',
        'app.services.space_group_save_time_service',
        'app.services.space_save_time_service',
        'app.services.alert_service',
        'app.services.alert_hook_service',
        'app.services.camera_service',
        'app.services.snap_space_service',
        'app.services.snap_task_service',
        'app.services.algorithm_service',
        'app.services.storage_service',
        'app.services.snap_image_service',
        'app.utils.gb28181_source',
        'app.utils.node_client',
        'app.blueprints.record',
        'app.blueprints.alert',
        'app.blueprints.camera',
        'app.blueprints.snap',
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


class TestMediaAccessAuditRotation(unittest.TestCase):
    _ENV_NAMES = (
        'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR',
        'YFEIEYE_MEDIA_ACCESS_AUDIT_MAX_BYTES',
        'YFEIEYE_MEDIA_ACCESS_AUDIT_BACKUP_COUNT',
    )

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_env = {
            name: os.environ.get(name)
            for name in self._ENV_NAMES
        }
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'] = self.temp_dir.name
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_MAX_BYTES'] = '1'
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_BACKUP_COUNT'] = '2'
        self.addCleanup(self.temp_dir.cleanup)
        self.addCleanup(self._restore_environment)

    def test_audit_rotates_by_bytes_and_keeps_configured_backups(self):
        from app.services import media_authorization_service as service

        for allowed in (False, True, False, True):
            service.append_media_access_audit(self._decision(allowed))

        path = os.path.join(self.temp_dir.name, 'media-access-audit.jsonl')
        retained_paths = [path, f'{path}.1', f'{path}.2']
        self.assertTrue(all(os.path.isfile(item) for item in retained_paths))
        self.assertFalse(os.path.exists(f'{path}.3'))
        retained = [self._single_entry(item) for item in retained_paths]
        self.assertEqual({'allowed', 'denied'}, {
            entry['decision']
            for entry in retained
        })

    def test_each_rotated_append_is_flushed_with_fsync(self):
        from app.services import media_authorization_service as service

        original_fsync = os.fsync
        with patch.object(service.os, 'fsync', wraps=original_fsync) as fsync:
            for allowed in (True, False, True):
                service.append_media_access_audit(self._decision(allowed))

        self.assertEqual(3, fsync.call_count)

    @staticmethod
    def _decision(allowed):
        from app.services.media_authorization_service import MediaAuthorizationDecision

        return MediaAuthorizationDecision(
            allowed=allowed,
            user_id='rotation-user',
            tenant_id='rotation-tenant',
            camera_id='rotation-camera',
            action='download',
            reason='rotation-test',
            status_code=200 if allowed else 403,
        )

    @staticmethod
    def _single_entry(path):
        with open(path, 'r', encoding='utf-8') as audit_file:
            entries = [json.loads(line) for line in audit_file if line.strip()]
        if len(entries) != 1:
            raise AssertionError(f'expected one retained entry in {path}, got {len(entries)}')
        return entries[0]

    def _restore_environment(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value


class TestRecordMediaAuthorization(_ModuleIsolationTestCase):
    def setUp(self):
        super().setUp()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_env = {
            name: os.environ.get(name)
            for name in (
                'YFEIEYE_MEDIA_AUTHORIZATION_URL',
                'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR',
                'YFEIEYE_RECORD_EXPORT_STORE_DIR',
                'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET',
                'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS',
                'YFEIEYE_MEDIA_SERVICE_POLICIES',
                'YFEIEYE_MEDIA_SERVICE_IDS',
                'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS',
                'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS',
                'VIDEO_ENV',
                'YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK',
            )
        }
        os.environ['YFEIEYE_MEDIA_AUTHORIZATION_URL'] = (
            'http://device.local/admin-api/system/auth/media-permission-check'
        )
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'] = self.temp_dir.name
        os.environ['YFEIEYE_RECORD_EXPORT_STORE_DIR'] = self.temp_dir.name
        os.environ['VIDEO_ENV'] = 'test'
        os.environ.pop('YFEIEYE_MEDIA_SERVICE_HMAC_KEYS', None)
        os.environ.pop('YFEIEYE_MEDIA_SERVICE_POLICIES', None)
        os.environ['YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS'] = (
            'coverage,export,download,playback,manifest_verify')
        os.environ['YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS'] = 'camera-01'
        os.environ['YFEIEYE_RECORD_EXPORT_ALLOW_SHA256_FALLBACK'] = 'true'

    def tearDown(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        self.temp_dir.cleanup()

    def test_anonymous_record_availability_is_denied_before_query(self):
        record_module = self._record_blueprint()
        called = []
        record_module.query_recording_availability = lambda **kwargs: called.append(kwargs) or {}

        response = self._app(record_module).test_client().get(
            '/video/record/availability?camera_id=camera-01&device_id=device-01'
            '&begin_time=2026-07-10T10:00:00&end_time=2026-07-10T10:01:00'
        )

        self.assertEqual(401, response.status_code)
        self.assertEqual('authentication_required', response.get_json()['reason'])
        self.assertEqual([], called)

    def test_default_service_allowlist_accepts_iot_system_download(self):
        from flask import request as flask_request
        from app.services.media_authorization_service import (
            authorize_media_request,
            canonical_service_signature,
        )

        path = '/video/record/export/export-1/download'
        timestamp = str(time.time())
        nonce = 'default-download-allowlist-1'
        secret = 'device-download-unit-secret-at-least-32-bytes'
        signature = canonical_service_signature(
            'GET', path, timestamp, nonce, 'iot-system', 'service:iot-system',
            '7', 'camera-01', 'download', b'', secret,
        )
        headers = {
            'X-YFeiEye-Service-Id': 'iot-system',
            'X-YFeiEye-Service-User-Id': 'service:iot-system',
            'X-YFeiEye-Service-Tenant-Id': '7',
            'X-YFeiEye-Service-Camera-Id': 'camera-01',
            'X-YFeiEye-Service-Action': 'download',
            'X-YFeiEye-Service-Timestamp': timestamp,
            'X-YFeiEye-Service-Nonce': nonce,
            'X-YFeiEye-Service-Signature': signature,
        }
        with patch.dict(os.environ, {
            'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
        }, clear=False):
            os.environ.pop('YFEIEYE_MEDIA_SERVICE_IDS', None)
            os.environ.pop('YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS', None)
            with Flask(__name__).test_request_context(path, headers=headers):
                decision = authorize_media_request(
                    flask_request,
                    action='download',
                    camera_id='camera-01',
                    resource=path,
                )

        self.assertTrue(decision.allowed, decision.reason)
        self.assertEqual('service_hmac', decision.auth_type)

    def test_production_rejects_shared_hmac_secret_for_multiple_service_identities(self):
        from flask import request as flask_request
        from app.services.media_authorization_service import (
            authorize_media_request,
            canonical_service_signature,
        )

        path = '/video/record/export/export-1/download'
        timestamp = str(time.time())
        nonce = 'shared-secret-service-spoof-1'
        secret = 'shared-service-secret-at-least-32-bytes'
        signature = canonical_service_signature(
            'GET', path, timestamp, nonce, 'iot-system', '42',
            '7', 'camera-01', 'download', b'', secret,
        )
        headers = {
            'X-YFeiEye-Service-Id': 'iot-system',
            'X-YFeiEye-Service-User-Id': '42',
            'X-YFeiEye-Service-Tenant-Id': '7',
            'X-YFeiEye-Service-Camera-Id': 'camera-01',
            'X-YFeiEye-Service-Action': 'download',
            'X-YFeiEye-Service-Timestamp': timestamp,
            'X-YFeiEye-Service-Nonce': nonce,
            'X-YFeiEye-Service-Signature': signature,
        }
        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
            'YFEIEYE_MEDIA_SERVICE_IDS': 'iot-system,video-algorithm',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS': 'download,alert_ingest',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
        }, clear=False):
            os.environ.pop('YFEIEYE_MEDIA_SERVICE_HMAC_KEYS', None)
            with Flask(__name__).test_request_context(path, headers=headers):
                decision = authorize_media_request(
                    flask_request,
                    action='download',
                    camera_id='camera-01',
                    resource=path,
                )

        self.assertFalse(decision.allowed)
        self.assertEqual('service_keyring_required', decision.reason)

    def test_per_service_hmac_keyring_allows_iot_system_on_behalf_subject(self):
        from flask import request as flask_request
        from app.services.media_authorization_service import (
            authorize_media_request,
            canonical_service_signature,
        )

        path = '/video/record/export/export-1/download'
        timestamp = str(time.time())
        nonce = 'per-service-keyring-iot-1'
        secret = 'iot-system-specific-secret-at-least-32-bytes'
        signature = canonical_service_signature(
            'GET', path, timestamp, nonce, 'iot-system', '42',
            '7', 'camera-01', 'download', b'', secret,
        )
        headers = {
            'X-YFeiEye-Service-Id': 'iot-system',
            'X-YFeiEye-Service-User-Id': '42',
            'X-YFeiEye-Service-Tenant-Id': '7',
            'X-YFeiEye-Service-Camera-Id': 'camera-01',
            'X-YFeiEye-Service-Action': 'download',
            'X-YFeiEye-Service-Timestamp': timestamp,
            'X-YFeiEye-Service-Nonce': nonce,
            'X-YFeiEye-Service-Signature': signature,
        }
        policies = {
            'iot-system': {
                'actions': ['download'],
                'cameraIds': ['camera-01'],
                'allowOnBehalf': True,
            },
            'video-algorithm': {
                'actions': ['alert_ingest'],
                'cameraIds': ['camera-01'],
                'allowOnBehalf': False,
            },
        }
        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS': json.dumps({
                'iot-system': secret,
                'video-algorithm': 'algorithm-specific-secret-at-least-32-bytes',
            }),
            'YFEIEYE_MEDIA_SERVICE_POLICIES': json.dumps(policies),
            'YFEIEYE_MEDIA_SERVICE_IDS': 'iot-system,video-algorithm',
        }, clear=False):
            with Flask(__name__).test_request_context(path, headers=headers):
                decision = authorize_media_request(
                    flask_request,
                    action='download',
                    camera_id='camera-01',
                    resource=path,
                )

        self.assertTrue(decision.allowed, decision.reason)
        self.assertEqual('iot-system', decision.service_id)

    def test_video_algorithm_key_cannot_impersonate_an_on_behalf_user(self):
        from flask import request as flask_request
        from app.services.media_authorization_service import (
            authorize_media_request,
            canonical_service_signature,
        )

        path = '/video/alert/hook'
        timestamp = str(time.time())
        nonce = 'algorithm-on-behalf-spoof-1'
        secret = 'algorithm-specific-secret-at-least-32-bytes'
        body = b'{}'
        signature = canonical_service_signature(
            'POST', path, timestamp, nonce, 'video-algorithm', '42',
            '7', 'camera-01', 'alert_ingest', body, secret,
        )
        headers = {
            'X-YFeiEye-Service-Id': 'video-algorithm',
            'X-YFeiEye-Service-User-Id': '42',
            'X-YFeiEye-Service-Tenant-Id': '7',
            'X-YFeiEye-Service-Camera-Id': 'camera-01',
            'X-YFeiEye-Service-Action': 'alert_ingest',
            'X-YFeiEye-Service-Timestamp': timestamp,
            'X-YFeiEye-Service-Nonce': nonce,
            'X-YFeiEye-Service-Signature': signature,
        }
        policies = {
            'video-algorithm': {
                'actions': ['alert_ingest'],
                'cameraIds': ['camera-01'],
                'allowOnBehalf': False,
            },
        }
        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS': json.dumps({
                'video-algorithm': secret,
            }),
            'YFEIEYE_MEDIA_SERVICE_POLICIES': json.dumps(policies),
            'YFEIEYE_MEDIA_SERVICE_IDS': 'video-algorithm',
        }, clear=False):
            with Flask(__name__).test_request_context(
                    path, method='POST', data=body, headers=headers):
                decision = authorize_media_request(
                    flask_request,
                    action='alert_ingest',
                    camera_id='camera-01',
                    resource=path,
                )

        self.assertFalse(decision.allowed)
        self.assertEqual('service_subject_scope_denied', decision.reason)

    def test_record_availability_without_resolvable_camera_fails_closed(self):
        record_module = self._record_blueprint()
        called = []
        record_module.query_recording_availability = lambda **kwargs: called.append(kwargs) or {}
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'coverage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/availability?begin_time=2026-07-10T10:00:00'
                '&end_time=2026-07-10T10:01:00',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual(
            'record_camera_scope_missing',
            response.get_json()['reason'],
        )
        self.assertEqual([], called)

    def test_record_space_update_authorizes_the_persisted_camera_scope(self):
        record_module = self._record_blueprint()
        space = types.SimpleNamespace(
            id=7,
            device_id='camera-01',
            tenant_id=7,
            to_dict=lambda: {'id': 7, 'device_id': 'camera-01'},
        )
        record_module.get_record_space = lambda space_id: space
        record_module.update_record_space = lambda *args, **kwargs: space
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'record_manage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization) as authorize_request:
            response = self._app(record_module).test_client().put(
                '/video/record/space/7',
                json={'space_name': 'camera one'},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(
            'camera-01',
            authorize_request.call_args.kwargs['json']['cameraId'],
        )

    def test_group_policy_requires_record_manage_for_every_persisted_camera(self):
        record_module = self._record_blueprint()
        updates = []
        self._install_group_policy_service(
            scopes=[
                {'tenant_id': 7, 'camera_id': 'camera-01'},
                {'tenant_id': 7, 'camera_id': 'camera-02'},
            ],
            updates=updates,
        )

        def authorize(_url, **kwargs):
            camera_id = kwargs['json']['cameraId']
            return self._record_manage_response(
                camera_id,
                allowed=camera_id == 'camera-01',
                reason='granted' if camera_id == 'camera-01' else 'camera_scope_denied',
            )

        with patch('requests.post', side_effect=authorize) as authorize_request:
            response = self._app(record_module).test_client().put(
                '/video/record/space/group-policy?camera_id=camera-01',
                json={'group_type': 'nvr', 'group_key': '9', 'save_time': 168},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('camera_scope_denied', response.get_json()['reason'])
        self.assertEqual([], updates)
        self.assertEqual(
            ['camera-01', 'camera-02'],
            [call.kwargs['json']['cameraId'] for call in authorize_request.call_args_list],
        )
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['cameraId'] == 'camera-02'
            and entry['action'] == 'record_manage'
            and entry['reason'] == 'camera_scope_denied'
            for entry in self._audit_entries()
        ))

    def test_group_policy_rejects_a_persisted_camera_owned_by_another_tenant(self):
        record_module = self._record_blueprint()
        updates = []
        self._install_group_policy_service(
            scopes=[
                {'tenant_id': 7, 'camera_id': 'camera-01'},
                {'tenant_id': 8, 'camera_id': 'camera-02'},
            ],
            updates=updates,
        )

        with patch('requests.post', side_effect=lambda _url, **kwargs: (
                self._record_manage_response(kwargs['json']['cameraId']))):
            response = self._app(record_module).test_client().put(
                '/video/record/space/group-policy?camera_id=camera-01',
                json={'group_type': 'nvr', 'group_key': '9', 'save_time': 168},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('tenant_scope_denied', response.get_json()['reason'])
        self.assertEqual([], updates)
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['tenantId'] == '7'
            and entry['cameraId'] == 'camera-02'
            and entry['reason'] == 'tenant_scope_denied'
            for entry in self._audit_entries()
        ))

    def test_group_policy_passes_all_authorized_persisted_scopes_to_update(self):
        record_module = self._record_blueprint()
        updates = []
        self._install_group_policy_service(
            scopes=[
                {'tenant_id': 7, 'camera_id': 'camera-01'},
                {'tenant_id': 7, 'camera_id': 'camera-02'},
            ],
            updates=updates,
        )

        with patch('requests.post', side_effect=lambda _url, **kwargs: (
                self._record_manage_response(kwargs['json']['cameraId']))) as authorize_request:
            response = self._app(record_module).test_client().put(
                '/video/record/space/group-policy?camera_id=camera-01',
                json={'group_type': 'nvr', 'group_key': '9', 'save_time': 168},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, len(updates))
        self.assertEqual(7, updates[0]['tenant_id'])
        self.assertEqual(['camera-01', 'camera-02'], updates[0]['camera_ids'])
        self.assertEqual(
            ['camera-01', 'camera-02'],
            [call.kwargs['json']['cameraId'] for call in authorize_request.call_args_list],
        )

    def test_record_playback_denies_authenticated_tenant_different_from_space_owner(self):
        record_module = self._record_blueprint()
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id,
            device_id='camera-01',
            tenant_id=7,
        )
        reads = []
        record_module.get_record_video = lambda *args: (
            reads.append(args) or (b'private', 'video/mp4', 'private.mp4'))
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 8,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/space/7/video/tenants/7/camera-01/private.mp4',
                headers={'Authorization': 'Bearer other-tenant', 'tenant-id': '8'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('tenant_scope_denied', response.get_json()['reason'])
        self.assertEqual([], reads)

    def test_record_playback_object_scope_denial_writes_one_final_denied_audit(self):
        record_module = self._record_blueprint()
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id,
            device_id='camera-01',
            tenant_id=7,
        )

        class Query:
            @staticmethod
            def filter_by(**filters):
                return types.SimpleNamespace(first=lambda: None)

        sys.modules['models'].RecordFile = types.SimpleNamespace(query=Query())
        record_module.get_record_video = lambda *args, **kwargs: self.fail(
            'object scope denial must happen before storage access')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/space/7/video/tenants/7/camera-01/missing.mp4',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(404, response.status_code, response.get_json())
        self.assertEqual('record_object_metadata_mismatch', response.get_json()['reason'])
        entries = [
            entry for entry in self._audit_entries()
            if entry['resource'].endswith('/tenants/7/camera-01/missing.mp4')
        ]
        self.assertEqual(1, len(entries), entries)
        self.assertEqual('denied', entries[0]['decision'])
        self.assertEqual('record_object_metadata_mismatch', entries[0]['reason'])

    def test_record_playback_queries_tenant_camera_space_and_object_identity(self):
        record_module = self._record_blueprint()
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id,
            device_id='camera-01',
            tenant_id=7,
        )
        filters_seen = []

        class Query:
            @staticmethod
            def filter_by(**filters):
                filters_seen.append(filters)
                matched = filters == {
                    'tenant_id': 7,
                    'space_id': 7,
                    'device_id': 'camera-01',
                    'object_name': 'tenants/7/camera-01/private.mp4',
                }
                return types.SimpleNamespace(
                    first=lambda: types.SimpleNamespace(**filters) if matched else None)

        sys.modules['models'].RecordFile = types.SimpleNamespace(query=Query())
        record_module.get_record_video = lambda *args, **kwargs: (
            b'private', 'video/mp4', 'private.mp4')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/space/7/video/tenants/7/camera-01/private.mp4',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            {
                'tenant_id': 7,
                'space_id': 7,
                'device_id': 'camera-01',
                'object_name': 'tenants/7/camera-01/private.mp4',
            },
            filters_seen[-1],
        )

    def test_record_delete_rejects_object_metadata_from_another_camera(self):
        record_module = self._record_blueprint()
        space = types.SimpleNamespace(id=7, device_id='camera-01', tenant_id=7)
        record_module.get_record_space = lambda space_id: space
        deleted = []
        record_module.delete_record_videos = lambda *args, **kwargs: deleted.append(
            (args, kwargs)) or {}

        class _RecordQuery:
            @staticmethod
            def filter_by(**filters):
                return types.SimpleNamespace(first=lambda: types.SimpleNamespace(
                    space_id=filters.get('space_id'),
                    object_name=filters.get('object_name'),
                    device_id='camera-02',
                ))

        sys.modules['models'].RecordFile = types.SimpleNamespace(query=_RecordQuery())
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'record_manage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().delete(
                '/video/record/space/7/videos',
                json={'object_names': ['camera-02/secret.flv']},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual(
            'record_object_camera_scope_mismatch',
            response.get_json()['reason'],
        )
        self.assertEqual([], deleted)

    def test_record_availability_rejects_camera_device_mismatch_before_query(self):
        record_module = self._record_blueprint()
        called = []
        record_module.query_recording_availability = lambda **kwargs: called.append(kwargs) or {}
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'coverage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/availability?camera_id=camera-01&device_id=camera-02'
                '&begin_time=2026-07-10T10:00:00&end_time=2026-07-10T10:01:00',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('camera_device_scope_mismatch', response.get_json()['reason'])
        self.assertEqual([], called)
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['cameraId'] == 'camera-01'
            and entry['reason'] == 'camera_device_scope_mismatch'
            for entry in self._audit_entries()
        ))

    def test_non_boolean_allowed_value_is_denied(self):
        record_module = self._record_blueprint()
        called = []
        record_module.query_recording_availability = lambda **kwargs: called.append(kwargs) or {}
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': 'true',
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'coverage',
                'reason': 'invalid_boolean_claim',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/availability?camera_id=camera-01&device_id=camera-01'
                '&begin_time=2026-07-10T10:00:00&end_time=2026-07-10T10:01:00',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('invalid_boolean_claim', response.get_json()['reason'])
        self.assertEqual([], called)

    def test_spoofed_export_identity_is_replaced_by_authenticated_subject(self):
        record_module = self._record_blueprint()
        captured = {}
        record_module.validate_record_export_request = lambda payload, camera_id: payload
        record_module.create_record_export = lambda payload, async_worker=False: captured.update(payload) or {
            'export_id': 'exp-auth-1',
            'camera_id': payload['camera_id'],
            'status': 'ready',
        }
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: {}
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization) as request_authorization:
            response = self._app(record_module).test_client().post(
                '/video/record/export',
                headers={
                    'Authorization': 'Bearer real-user-token',
                    'tenant-id': '999',
                },
                json={
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': '/recording/source.mp4',
                    'operator_user_id': 999,
                    'tenant_id': 999,
                },
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual('42', str(captured['operator_user_id']))
        self.assertEqual('7', str(captured['tenant_id']))
        request_headers = request_authorization.call_args.kwargs['headers']
        self.assertEqual('Bearer real-user-token', request_headers['Authorization'])

    def test_export_derives_camera_from_canonical_space_object_metadata_before_auth(self):
        record_module = self._record_blueprint()
        captured = {}
        record_module.validate_record_export_request = (
            lambda payload, camera_id: captured.update({
                'payload': dict(payload),
                'camera_id': camera_id,
            }) or payload
        )
        record_module.create_record_export = lambda payload, async_worker=False: {
            'export_id': 'exp-derived-camera',
            'camera_id': payload['camera_id'],
            'status': 'pending',
        }
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: {}

        class _SpaceQuery:
            @staticmethod
            def get(space_id):
                return types.SimpleNamespace(
                    id=space_id, tenant_id=7, device_id='camera-01')

        class _RecordQuery:
            @staticmethod
            def filter_by(**filters):
                return types.SimpleNamespace(first=lambda: types.SimpleNamespace(
                    tenant_id=7,
                    device_id='camera-01',
                    space_id=filters.get('space_id'),
                    object_name=filters.get('object_name'),
                ))

        sys.modules['models'].RecordSpace = types.SimpleNamespace(query=_SpaceQuery())
        sys.modules['models'].RecordFile = types.SimpleNamespace(query=_RecordQuery())
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })
        record_uri = '/video/record/space/7/video/camera-01/clip.mp4'

        with patch('requests.post', return_value=authorization) as authorize_request:
            response = self._app(record_module).test_client().post(
                '/video/record/export',
                json={'record_uri': record_uri},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(
            'camera-01',
            authorize_request.call_args.kwargs['json']['cameraId'],
        )
        self.assertEqual('camera-01', captured['camera_id'])
        self.assertEqual('camera-01', captured['payload']['camera_id'])
        self.assertEqual('camera-01', captured['payload']['device_id'])

    def test_export_rejects_camera_device_mismatch_before_job_creation(self):
        record_module = self._record_blueprint()
        created = []
        record_module.create_record_export = lambda payload, async_worker=False: created.append(payload) or {
            'export_id': 'must-not-exist',
            'status': 'ready',
        }
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().post(
                '/video/record/export',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
                json={
                    'camera_id': 'camera-01',
                    'device_id': 'camera-02',
                    'record_uri': '/recording/source.mp4',
                },
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('camera_device_scope_mismatch', response.get_json()['reason'])
        self.assertEqual([], created)

    def test_export_rejects_unowned_absolute_record_path(self):
        record_module = self._record_blueprint()
        created = []
        record_module.create_record_export = lambda payload, async_worker=False: created.append(payload) or {
            'export_id': 'must-not-exist',
            'status': 'ready',
        }
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().post(
                '/video/record/export',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
                json={
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': os.path.abspath('private-source.mp4'),
                },
            )

        self.assertEqual(400, response.status_code, response.get_json())
        self.assertEqual([], created)

    def test_wrong_camera_is_denied_and_persistently_audited(self):
        record_module = self._record_blueprint()
        record_module.query_recording_availability = lambda **kwargs: self.fail(
            'record query must not run after camera-scope denial'
        )
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': False,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-02',
                'action': 'coverage',
                'reason': 'camera_scope_denied',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/availability?camera_id=camera-02&device_id=device-02'
                '&begin_time=2026-07-10T10:00:00&end_time=2026-07-10T10:01:00',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code)
        self.assertEqual('camera_scope_denied', response.get_json()['reason'])
        audit = self._audit_entries()
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['userId'] == '42'
            and entry['tenantId'] == '7'
            and entry['cameraId'] == 'camera-02'
            and entry['action'] == 'coverage'
            and entry['reason'] == 'camera_scope_denied'
            for entry in audit
        ))

    def test_allowed_record_access_is_persistently_audited(self):
        record_module = self._record_blueprint()
        record_module.query_recording_availability = lambda **kwargs: {'camera_id': kwargs['camera_id']}
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'coverage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                '/video/record/availability?camera_id=camera-01&device_id=camera-01'
                '&begin_time=2026-07-10T10:00:00&end_time=2026-07-10T10:01:00',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code)
        audit = self._audit_entries()
        self.assertTrue(any(
            entry['decision'] == 'allowed'
            and entry['userId'] == '42'
            and entry['tenantId'] == '7'
            and entry['cameraId'] == 'camera-01'
            and entry['action'] == 'coverage'
            for entry in audit
        ))

    def test_expired_export_download_is_denied_and_audited(self):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        record_module = self._record_blueprint()
        started = record_export_service.create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
            'operator_user_id': '42',
            'tenant_id': '7',
            'expires_at': (datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat(),
        }, record_resolver=_trusted_record_resolver)
        export_id = started['export_id']
        expired_job = record_export_service._get_export_job(export_id)
        expired_job['expires_at'] = (
            datetime.now(timezone.utc) - timedelta(minutes=1)).isoformat()
        record_export_service._persist_job(expired_job)
        record_export_service._persist_manifest(export_id)
        record_module.poll_record_export = record_export_service.poll_record_export
        record_module.download_record_export = record_export_service.download_record_export
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'download',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}/download?operator_user_id=999',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(410, response.status_code, response.get_json())
        self.assertEqual('export_expired', response.get_json()['reason'])
        media_audit = self._media_export_audit_entries(export_id, 'download')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('denied', media_audit[0]['decision'])
        self.assertEqual('export_expired', media_audit[0]['reason'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'download')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_denied', export_audit[0]['action'])
        self.assertEqual('export_expired', export_audit[0]['reason'])

    def test_export_authorization_denial_writes_one_final_denied_audit(self):
        record_export_service, record_module, export_id = self._create_export_job()
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': False,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'permission_denied',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        media_audit = self._media_export_audit_entries(export_id, 'export')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('denied', media_audit[0]['decision'])
        self.assertEqual('permission_denied', media_audit[0]['reason'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'export')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_denied', export_audit[0]['action'])

    def test_export_scope_mismatch_writes_one_final_denied_audit(self):
        record_export_service, record_module, export_id = self._create_export_job()
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-02',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('camera_scope_denied', response.get_json()['reason'])
        media_audit = self._media_export_audit_entries(export_id, 'export')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('denied', media_audit[0]['decision'])
        self.assertEqual('camera_scope_denied', media_audit[0]['reason'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'export')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_denied', export_audit[0]['action'])
        self.assertEqual('camera_scope_denied', export_audit[0]['reason'])

    def test_successful_export_download_writes_one_final_allowed_audit(self):
        record_export_service, record_module, export_id = self._create_export_job()
        record_module.download_record_export = lambda *args, **kwargs: {
            'stream': BytesIO(b'export-content'),
            'filename': f'{export_id}.mp4',
            'mimetype': 'video/mp4',
            'content_length': len(b'export-content'),
        }
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'download',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}/download',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual(b'export-content', response.data)
        media_audit = self._media_export_audit_entries(export_id, 'download')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('allowed', media_audit[0]['decision'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'download')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_allowed', export_audit[0]['action'])

    def test_successful_export_status_and_manifest_write_one_final_allowed_audit(self):
        record_export_service, record_module, export_id = self._create_export_job()
        routes = (
            (f'/video/record/export/{export_id}', 'export'),
            (f'/video/record/export/{export_id}/manifest', 'manifest_verify'),
        )

        client = self._app(record_module).test_client()
        for path, action in routes:
            authorization = _JsonResponse({
                'code': 0,
                'data': {
                    'allowed': True,
                    'userId': 42,
                    'tenantId': 7,
                    'cameraId': 'camera-01',
                    'action': action,
                    'reason': 'granted',
                },
            })
            with self.subTest(action=action), patch(
                    'requests.post', return_value=authorization):
                response = client.get(
                    path,
                    headers={
                        'Authorization': 'Bearer scoped-user',
                        'tenant-id': '7',
                    },
                )

                self.assertEqual(200, response.status_code, response.get_json())
                media_audit = self._media_export_audit_entries(export_id, action)
                self.assertEqual(1, len(media_audit), media_audit)
                self.assertEqual('allowed', media_audit[0]['decision'])
                export_audit = self._record_export_access_entries(
                    record_export_service, export_id, action)
                self.assertEqual(1, len(export_audit), export_audit)
                self.assertEqual('access_allowed', export_audit[0]['action'])
                if action == 'manifest_verify':
                    returned_manifest = response.get_json()['data']
                    from app.services.record_export_manifest_verifier import verify_manifest
                    verification = verify_manifest(returned_manifest)
                    self.assertNotIn(
                        'file_hash_mismatch', verification['violations'], verification)
                    self.assertTrue(verification['signatureValid'], verification)
                    self.assertEqual(
                        verification['canonicalHash'],
                        verification['actualManifestHash'],
                    )
                    self.assertNotIn(
                        'audit.json',
                        [item['name'] for item in returned_manifest['files']],
                    )

    def test_export_access_retry_completes_both_ledgers_without_mixed_decisions(self):
        record_export_service, record_module, export_id = self._create_export_job()
        append_media_audit = record_module.append_media_access_audit
        attempts = []

        def fail_second_ledger_once(*args, **kwargs):
            attempts.append(kwargs.get('decision_override'))
            if len(attempts) == 1:
                raise RuntimeError('media audit disk unavailable')
            return append_media_audit(*args, **kwargs)

        record_module.append_media_access_audit = fail_second_ledger_once
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })
        headers = {
            'Authorization': 'Bearer scoped-user',
            'tenant-id': '7',
            'Idempotency-Key': 'status-audit-retry-001',
        }
        client = self._app(record_module).test_client()

        with patch('requests.post', return_value=authorization):
            first_response = client.get(
                f'/video/record/export/{export_id}', headers=headers)

            self.assertEqual(503, first_response.status_code, first_response.get_json())
            self.assertEqual(
                'export_audit_unavailable', first_response.get_json()['reason'])
            first_media_audit = self._media_export_audit_entries(export_id, 'export')
            self.assertEqual([], first_media_audit)
            first_export_audit = self._record_export_access_entries(
                record_export_service, export_id, 'export')
            self.assertEqual(1, len(first_export_audit), first_export_audit)
            self.assertEqual({'access_allowed'}, {
                entry['action'] for entry in first_export_audit
            })

            retry_response = client.get(
                f'/video/record/export/{export_id}', headers=headers)

        self.assertEqual(200, retry_response.status_code, retry_response.get_json())
        media_audit = self._media_export_audit_entries(export_id, 'export')
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'export')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual({'allowed'}, {entry['decision'] for entry in media_audit})
        self.assertEqual({'access_allowed'}, {
            entry['action'] for entry in export_audit
        })
        self.assertEqual(
            media_audit[0]['decisionId'],
            export_audit[0]['decision_id'],
        )

    def test_export_access_idempotency_key_rejects_a_changed_terminal_decision(self):
        record_export_service, record_module, export_id = self._create_export_job()
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })
        headers = {
            'Authorization': 'Bearer scoped-user',
            'tenant-id': '7',
            'Idempotency-Key': 'status-terminal-conflict-001',
        }
        client = self._app(record_module).test_client()

        with patch('requests.post', return_value=authorization):
            first_response = client.get(
                f'/video/record/export/{export_id}', headers=headers)

            def fail_status(_export_id):
                raise RuntimeError('status store unavailable')

            record_module.get_record_export_status = fail_status
            conflict_response = client.get(
                f'/video/record/export/{export_id}', headers=headers)

        self.assertEqual(200, first_response.status_code, first_response.get_json())
        self.assertEqual(409, conflict_response.status_code, conflict_response.get_json())
        self.assertEqual(
            'export_access_decision_conflict',
            conflict_response.get_json()['reason'],
        )
        media_audit = self._media_export_audit_entries(export_id, 'export')
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'export')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual({'allowed'}, {entry['decision'] for entry in media_audit})
        self.assertEqual({'access_allowed'}, {
            entry['action'] for entry in export_audit
        })

    def test_export_failure_audit_unavailable_is_stable_json_for_all_routes(self):
        routes = ('create', 'status', 'retry', 'audit', 'manifest', 'download')

        for index, route in enumerate(routes):
            with self.subTest(route=route):
                record_export_service, record_module, export_id = self._create_export_job(
                    review_case_id=3050 + index,
                    review_item_id=1050 + index,
                )
                action = 'manifest_verify' if route in ('audit', 'manifest') else (
                    'download' if route == 'download' else 'export')
                authorization = _JsonResponse({
                    'code': 0,
                    'data': {
                        'allowed': True,
                        'userId': 42,
                        'tenantId': 7,
                        'cameraId': 'camera-01',
                        'action': action,
                        'reason': 'granted',
                    },
                })
                method = 'GET'
                payload = None
                if route == 'create':
                    record_module.validate_record_export_request = lambda *_args: (
                        (_ for _ in ()).throw(ValueError('invalid export')))
                    path = '/video/record/export'
                    method = 'POST'
                    payload = {
                        'camera_id': 'camera-01',
                        'device_id': 'camera-01',
                        'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
                    }
                elif route == 'status':
                    record_module.get_record_export_status = lambda *_args: (
                        (_ for _ in ()).throw(RuntimeError('status failed')))
                    path = f'/video/record/export/{export_id}'
                elif route == 'retry':
                    record_module.retry_record_export = lambda *_args: (
                        (_ for _ in ()).throw(RuntimeError('retry failed')))
                    path = f'/video/record/export/{export_id}/retry'
                    method = 'POST'
                elif route == 'audit':
                    record_module.get_record_export_audit = lambda *_args: (
                        (_ for _ in ()).throw(RuntimeError('audit read failed')))
                    path = f'/video/record/export/{export_id}/audit'
                elif route == 'manifest':
                    load_manifest = record_module.get_record_export_manifest
                    calls = 0

                    def fail_manifest_response(*args, **kwargs):
                        nonlocal calls
                        calls += 1
                        if calls == 1:
                            return load_manifest(*args, **kwargs)
                        raise RuntimeError('manifest failed')

                    record_module.get_record_export_manifest = fail_manifest_response
                    path = f'/video/record/export/{export_id}/manifest'
                else:
                    record_module.download_record_export = lambda *_args, **_kwargs: (
                        (_ for _ in ()).throw(RuntimeError('download failed')))
                    path = f'/video/record/export/{export_id}/download'

                record_module.append_media_access_audit = lambda *_args, **_kwargs: (
                    (_ for _ in ()).throw(RuntimeError('global audit disk unavailable')))
                with patch('requests.post', return_value=authorization):
                    response = self._app(record_module).test_client().open(
                        path,
                        method=method,
                        json=payload,
                        headers={
                            'Authorization': 'Bearer scoped-user',
                            'tenant-id': '7',
                            'Idempotency-Key': f'{route}-failure-audit-001',
                        },
                    )

                self.assertEqual(503, response.status_code, response.get_data(as_text=True))
                self.assertTrue(response.is_json, response.get_data(as_text=True))
                self.assertEqual(
                    'export_audit_unavailable', response.get_json()['reason'])

    def test_export_create_failures_write_only_the_final_denied_decision(self):
        failures = (
            (ValueError('invalid export request'), 400),
            (RuntimeError('export store unavailable'), 500),
        )
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        for index, (error, expected_status) in enumerate(failures):
            with self.subTest(error=type(error).__name__):
                record_module = self._record_blueprint()

                def fail_validation(*_args, **_kwargs):
                    raise error

                record_module.validate_record_export_request = fail_validation
                before = len(self._audit_entries())
                with patch('requests.post', return_value=authorization):
                    response = self._app(record_module).test_client().post(
                        '/video/record/export',
                        headers={
                            'Authorization': 'Bearer scoped-user',
                            'tenant-id': '7',
                            'Idempotency-Key': f'create-failure-{index}',
                        },
                        json={
                            'camera_id': 'camera-01',
                            'device_id': 'camera-01',
                            'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
                        },
                    )

                self.assertEqual(expected_status, response.status_code, response.get_json())
                entries = self._audit_entries()[before:]
                self.assertEqual(1, len(entries), entries)
                self.assertEqual('denied', entries[0]['decision'])
                self.assertEqual('export_create_failed', entries[0]['reason'])

    def test_export_create_export_audit_failure_does_not_write_global_allowed(self):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        record_module = self._record_blueprint()
        record_module.validate_record_export_request = lambda *_args: None
        created = []

        def create_export(payload, async_worker=False):
            result = record_export_service.create_record_export(
                payload,
                record_resolver=_trusted_record_resolver,
                async_worker=async_worker,
            )
            created.append(result)
            return result

        record_module.create_record_export = create_export
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: (
            (_ for _ in ()).throw(RuntimeError('export audit disk unavailable')))
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().post(
                '/video/record/export',
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                    'Idempotency-Key': 'create-export-audit-failure-001',
                },
                json={
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
                },
            )

        self.assertEqual(503, response.status_code, response.get_json())
        self.assertEqual('export_audit_unavailable', response.get_json()['reason'])
        export_id = created[0]['export_id']
        self.assertEqual([], self._media_export_audit_entries(export_id, 'export'))
        self.assertEqual([], self._record_export_access_entries(
            record_export_service, export_id, 'export'))

    def test_export_create_global_audit_retry_preserves_export_decision(self):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        record_module = self._record_blueprint()
        record_module.validate_record_export_request = lambda *_args: None
        record_module.create_record_export = lambda payload, async_worker=False: (
            record_export_service.create_record_export(
                payload,
                record_resolver=_trusted_record_resolver,
                async_worker=async_worker,
            )
        )
        append_media_audit = record_module.append_media_access_audit
        attempts = []

        def fail_global_once(*args, **kwargs):
            attempts.append(kwargs.get('decision_override'))
            if len(attempts) == 1:
                raise RuntimeError('media audit disk unavailable')
            return append_media_audit(*args, **kwargs)

        record_module.append_media_access_audit = fail_global_once
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })
        headers = {
            'Authorization': 'Bearer scoped-user',
            'tenant-id': '7',
            'Idempotency-Key': 'create-global-audit-retry-001',
        }
        payload = {
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
        }
        client = self._app(record_module).test_client()

        with patch('requests.post', return_value=authorization):
            first_response = client.post(
                '/video/record/export', headers=headers, json=payload)

            self.assertEqual(503, first_response.status_code, first_response.get_json())
            export_id = record_export_service._build_record_export({
                **payload,
                'operator_user_id': '42',
                'approved_by': '42',
                'tenant_id': '7',
            }, _trusted_record_resolver)['export_id']
            self.assertEqual([], self._media_export_audit_entries(export_id, 'export'))
            first_export_audit = self._record_export_access_entries(
                record_export_service, export_id, 'export')
            self.assertEqual(1, len(first_export_audit), first_export_audit)

            retry_response = client.post(
                '/video/record/export', headers=headers, json=payload)

        self.assertEqual(200, retry_response.status_code, retry_response.get_json())
        media_audit = self._media_export_audit_entries(export_id, 'export')
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'export')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual(
            media_audit[0]['decisionId'], export_audit[0]['decision_id'])

    def test_export_create_success_writes_matching_dual_ledgers(self):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        record_module = self._record_blueprint()
        record_module.validate_record_export_request = lambda *_args: None
        record_module.create_record_export = lambda payload, async_worker=False: (
            record_export_service.create_record_export(
                payload,
                record_resolver=_trusted_record_resolver,
                async_worker=async_worker,
            )
        )
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().post(
                '/video/record/export',
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                    'Idempotency-Key': 'create-success-001',
                },
                json={
                    'camera_id': 'camera-01',
                    'device_id': 'camera-01',
                    'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
                },
            )

        self.assertEqual(200, response.status_code, response.get_json())
        export_id = response.get_json()['data']['export_id']
        media_audit = self._media_export_audit_entries(export_id, 'export')
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'export')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('allowed', media_audit[0]['decision'])
        self.assertEqual('access_allowed', export_audit[0]['action'])
        self.assertEqual(
            media_audit[0]['decisionId'], export_audit[0]['decision_id'])

    def test_export_retry_and_audit_success_write_terminal_dual_ledgers(self):
        record_export_service, record_module, export_id = self._create_export_job()
        record_module.retry_record_export = lambda current_export_id: {
            'export_id': current_export_id,
            'status': 'pending',
        }
        record_module.get_record_export_audit = lambda _export_id: []
        routes = (
            (f'/video/record/export/{export_id}/retry', 'POST', 'export'),
            (f'/video/record/export/{export_id}/audit', 'GET', 'manifest_verify'),
        )

        for path, method, action in routes:
            authorization = _JsonResponse({
                'code': 0,
                'data': {
                    'allowed': True,
                    'userId': 42,
                    'tenantId': 7,
                    'cameraId': 'camera-01',
                    'action': action,
                    'reason': 'granted',
                },
            })
            with self.subTest(action=action), patch(
                    'requests.post', return_value=authorization):
                response = self._app(record_module).test_client().open(
                    path,
                    method=method,
                    headers={
                        'Authorization': 'Bearer scoped-user',
                        'tenant-id': '7',
                        'Idempotency-Key': f'{action}-success-001',
                    },
                )

            self.assertEqual(200, response.status_code, response.get_json())
            media_audit = self._media_export_audit_entries(export_id, action)
            export_audit = self._record_export_access_entries(
                record_export_service, export_id, action)
            self.assertEqual(1, len(media_audit), media_audit)
            self.assertEqual(1, len(export_audit), export_audit)
            self.assertEqual('allowed', media_audit[0]['decision'])
            self.assertEqual('access_allowed', export_audit[0]['action'])

    def test_export_routes_write_one_denied_audit_on_manifest_integrity_error(self):
        record_export_service, record_module, export_id = self._create_export_job()

        def corrupted_manifest(_export_id):
            raise RuntimeError('record export manifest hash mismatch')

        record_module.get_record_export_manifest = corrupted_manifest
        routes = (
            (f'/video/record/export/{export_id}', 'export'),
            (f'/video/record/export/{export_id}/manifest', 'manifest_verify'),
            (f'/video/record/export/{export_id}/download', 'download'),
        )

        client = self._app(record_module).test_client()
        for path, action in routes:
            authorization = _JsonResponse({
                'code': 0,
                'data': {
                    'allowed': True,
                    'userId': 42,
                    'tenantId': 7,
                    'cameraId': 'camera-01',
                    'action': action,
                    'reason': 'granted',
                },
            })
            with self.subTest(action=action), patch(
                    'requests.post', return_value=authorization):
                response = client.get(
                    path,
                    headers={
                        'Authorization': 'Bearer scoped-user',
                        'tenant-id': '7',
                    },
                )

                self.assertEqual(500, response.status_code, response.get_json())
                media_audit = self._media_export_audit_entries(export_id, action)
                self.assertEqual(1, len(media_audit), media_audit)
                self.assertEqual('denied', media_audit[0]['decision'])
                self.assertEqual('export_integrity_error', media_audit[0]['reason'])
                export_audit = self._record_export_access_entries(
                    record_export_service, export_id, action)
                self.assertEqual(1, len(export_audit), export_audit)
                self.assertEqual('access_denied', export_audit[0]['action'])
                self.assertEqual('export_integrity_error', export_audit[0]['reason'])

    def test_export_download_failure_writes_one_final_denied_audit(self):
        failures = (
            (ValueError('export is not ready'), 404),
            (RuntimeError('object storage unavailable'), 500),
        )

        for index, (error, expected_status) in enumerate(failures):
            with self.subTest(error=type(error).__name__):
                record_export_service, record_module, export_id = self._create_export_job(
                    review_case_id=3100 + index,
                    review_item_id=1100 + index,
                )

                def fail_download(*_args, **_kwargs):
                    raise error

                record_module.download_record_export = fail_download
                authorization = _JsonResponse({
                    'code': 0,
                    'data': {
                        'allowed': True,
                        'userId': 42,
                        'tenantId': 7,
                        'cameraId': 'camera-01',
                        'action': 'download',
                        'reason': 'granted',
                    },
                })
                with patch('requests.post', return_value=authorization):
                    response = self._app(record_module).test_client().get(
                        f'/video/record/export/{export_id}/download',
                        headers={
                            'Authorization': 'Bearer scoped-user',
                            'tenant-id': '7',
                        },
                    )

                self.assertEqual(expected_status, response.status_code, response.get_json())
                media_audit = self._media_export_audit_entries(export_id, 'download')
                self.assertEqual(1, len(media_audit), media_audit)
                self.assertEqual('denied', media_audit[0]['decision'])
                self.assertEqual('export_download_failed', media_audit[0]['reason'])
                export_audit = self._record_export_access_entries(
                    record_export_service, export_id, 'download')
                self.assertEqual(1, len(export_audit), export_audit)
                self.assertEqual('access_denied', export_audit[0]['action'])
                self.assertEqual('export_download_failed', export_audit[0]['reason'])

    def test_export_download_integrity_failure_writes_one_integrity_audit_chain(self):
        record_export_service, record_module, export_id = self._create_export_job()

        def fail_download(*_args, **_kwargs):
            raise record_export_service.RecordExportIntegrityError(
                'export package hash mismatch')

        record_module.download_record_export = fail_download
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'download',
                'reason': 'granted',
            },
        })
        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}/download',
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                },
            )

        self.assertEqual(500, response.status_code, response.get_json())
        self.assertEqual('export_integrity_error', response.get_json()['reason'])
        media_audit = self._media_export_audit_entries(export_id, 'download')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('denied', media_audit[0]['decision'])
        self.assertEqual('export_integrity_error', media_audit[0]['reason'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'download')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_denied', export_audit[0]['action'])
        self.assertEqual('export_integrity_error', export_audit[0]['reason'])

    def test_export_status_failure_writes_one_final_denied_audit(self):
        failures = (
            (ValueError('export job disappeared'), 404),
            (RuntimeError('status store unavailable'), 500),
        )

        for index, (error, expected_status) in enumerate(failures):
            with self.subTest(error=type(error).__name__):
                record_export_service, record_module, export_id = self._create_export_job(
                    review_case_id=3200 + index,
                    review_item_id=1200 + index,
                )

                def fail_status(*_args, **_kwargs):
                    raise error

                record_module.get_record_export_status = fail_status
                authorization = _JsonResponse({
                    'code': 0,
                    'data': {
                        'allowed': True,
                        'userId': 42,
                        'tenantId': 7,
                        'cameraId': 'camera-01',
                        'action': 'export',
                        'reason': 'granted',
                    },
                })
                with patch('requests.post', return_value=authorization):
                    response = self._app(record_module).test_client().get(
                        f'/video/record/export/{export_id}',
                        headers={
                            'Authorization': 'Bearer scoped-user',
                            'tenant-id': '7',
                        },
                    )

                self.assertEqual(expected_status, response.status_code, response.get_json())
                media_audit = self._media_export_audit_entries(export_id, 'export')
                self.assertEqual(1, len(media_audit), media_audit)
                self.assertEqual('denied', media_audit[0]['decision'])
                self.assertEqual('export_status_failed', media_audit[0]['reason'])
                export_audit = self._record_export_access_entries(
                    record_export_service, export_id, 'export')
                self.assertEqual(1, len(export_audit), export_audit)
                self.assertEqual('access_denied', export_audit[0]['action'])
                self.assertEqual('export_status_failed', export_audit[0]['reason'])

    def test_export_manifest_response_failure_writes_one_final_denied_audit(self):
        record_export_service, record_module, export_id = self._create_export_job()
        load_manifest = record_module.get_record_export_manifest
        calls = 0

        def fail_second_manifest_read(*args, **kwargs):
            nonlocal calls
            calls += 1
            if calls == 1:
                return load_manifest(*args, **kwargs)
            raise RuntimeError('manifest store unavailable')

        record_module.get_record_export_manifest = fail_second_manifest_read
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'manifest_verify',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}/manifest',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(500, response.status_code, response.get_json())
        media_audit = self._media_export_audit_entries(export_id, 'manifest_verify')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('denied', media_audit[0]['decision'])
        self.assertEqual('export_manifest_failed', media_audit[0]['reason'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'manifest_verify')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_denied', export_audit[0]['action'])
        self.assertEqual('export_manifest_failed', export_audit[0]['reason'])

    def test_export_download_response_failure_writes_one_final_denied_audit(self):
        record_export_service, record_module, export_id = self._create_export_job()
        record_module.download_record_export = lambda *_args, **_kwargs: {
            'path': 'unused-export-path',
            'filename': f'{export_id}.mp4',
            'mimetype': 'video/mp4',
        }
        record_module.send_file = lambda *_args, **_kwargs: (
            (_ for _ in ()).throw(RuntimeError('response construction failed')))
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'download',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f'/video/record/export/{export_id}/download',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(500, response.status_code, response.get_json())
        media_audit = self._media_export_audit_entries(export_id, 'download')
        self.assertEqual(1, len(media_audit), media_audit)
        self.assertEqual('denied', media_audit[0]['decision'])
        self.assertEqual('export_download_failed', media_audit[0]['reason'])
        export_audit = self._record_export_access_entries(
            record_export_service, export_id, 'download')
        self.assertEqual(1, len(export_audit), export_audit)
        self.assertEqual('access_denied', export_audit[0]['action'])
        self.assertEqual('export_download_failed', export_audit[0]['reason'])

    def test_export_access_decision_is_reversible_from_export_audit(self):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        started = record_export_service.create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
            'operator_user_id': '42',
            'tenant_id': '7',
        }, record_resolver=_trusted_record_resolver)
        self.assertTrue(
            hasattr(record_export_service, 'append_record_export_access_audit'),
            'record export service must persist allow/deny media decisions',
        )

        record_export_service.append_record_export_access_audit(
            started['export_id'],
            decision='denied',
            user_id='42',
            tenant_id='7',
            camera_id='camera-01',
            action='download',
            reason='export_expired',
        )

        audit = record_export_service.get_record_export_audit(started['export_id'])
        manifest = record_export_service.get_record_export_manifest(started['export_id'])
        self.assertTrue(any(
            entry['action'] == 'access_denied'
            and entry['export_id'] == started['export_id']
            and entry['operator_user_id'] == '42'
            and entry['tenant_id'] == '7'
            and entry['camera_id'] == 'camera-01'
            and entry['media_action'] == 'download'
            and entry['reason'] == 'export_expired'
            for entry in audit
        ))
        self.assertEqual('7', manifest['tenantId'])

    def test_legacy_export_without_tenant_binding_fails_closed(self):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        record_module = self._record_blueprint()
        started = record_export_service.create_record_export({
            'review_case_id': 3000,
            'review_item_id': 1000,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/camera-01/legacy-source.mp4',
            'operator_user_id': '42',
        }, record_resolver=_trusted_record_resolver)
        record_module.get_record_export_manifest = record_export_service.get_record_export_manifest
        record_module.poll_record_export = record_export_service.poll_record_export
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'export',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(record_module).test_client().get(
                f"/video/record/export/{started['export_id']}",
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('tenant_scope_denied', response.get_json()['reason'])

    def test_missing_export_is_authenticated_and_audited_before_lookup(self):
        record_module = self._record_blueprint()
        lookups = []

        def missing_manifest(export_id):
            lookups.append(export_id)
            raise ValueError(f'export job not found: {export_id}')

        record_module.get_record_export_manifest = missing_manifest

        response = self._app(record_module).test_client().get(
            '/video/record/export/nonexistent-export'
        )

        self.assertEqual(401, response.status_code, response.get_json())
        self.assertEqual('authentication_required', response.get_json()['reason'])
        self.assertEqual([], lookups)
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['exportId'] == 'nonexistent-export'
            and entry['reason'] == 'authentication_required'
            for entry in self._audit_entries()
        ))

    def test_authenticated_missing_export_routes_write_one_final_not_found_audit(self):
        record_module = self._record_blueprint()
        routes = (
            ('/video/record/export/nonexistent-status', 'nonexistent-status', 'export'),
            (
                '/video/record/export/nonexistent-manifest/manifest',
                'nonexistent-manifest',
                'manifest_verify',
            ),
            (
                '/video/record/export/nonexistent-download/download',
                'nonexistent-download',
                'download',
            ),
        )

        client = self._app(record_module).test_client()
        for path, export_id, action in routes:
            authorization = _JsonResponse({
                'code': 0,
                'data': {
                    'allowed': True,
                    'userId': 42,
                    'tenantId': 7,
                    'cameraId': 'camera-01',
                    'action': action,
                    'reason': 'granted',
                },
            })
            with self.subTest(action=action), patch(
                    'requests.post', return_value=authorization):
                response = client.get(
                    path,
                    headers={
                        'Authorization': 'Bearer scoped-user',
                        'tenant-id': '7',
                    },
                )

                self.assertEqual(404, response.status_code, response.get_json())
                entries = self._media_export_audit_entries(export_id, action)
                self.assertEqual(1, len(entries), entries)
                self.assertEqual('denied', entries[0]['decision'])
                self.assertEqual('export_not_found', entries[0]['reason'])

    def test_resolve_alert_uses_coverage_authorization_not_record_management(self):
        record_module = self._record_blueprint()
        record_module.find_segment_for_alert = lambda device_id, alert_id, **kwargs: {
            'device_id': device_id,
            'alert_id': alert_id,
            'segment': {'url': '/video/record/space/7/video/camera-01/clip.flv'},
        }
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'coverage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization) as request_authorization:
            response = self._app(record_module).test_client().get(
                '/video/record/space/device/camera-01/resolve-alert?alert_id=9',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(
            'coverage',
            request_authorization.call_args.kwargs['json']['action'],
        )

    def test_signed_device_context_is_accepted_once_and_body_identity_is_ignored(self):
        from app.services.media_authorization_service import canonical_service_signature

        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        record_module = self._record_blueprint()
        captured = []
        record_module.validate_record_export_request = lambda payload, camera_id: payload
        record_module.append_record_export_access_audit = lambda *_args, **_kwargs: {}
        record_module.create_record_export = lambda payload, async_worker=False: captured.append(payload) or {
            'export_id': 'exp-service-1',
            'camera_id': payload['camera_id'],
            'status': 'ready',
        }
        body = json.dumps({
            'camera_id': 'camera-01',
            'record_uri': '/recording/source.mp4',
            'operator_user_id': 'spoofed-user',
            'tenant_id': '999',
        }, separators=(',', ':')).encode('utf-8')
        timestamp = str(time.time())
        nonce = 'nonce-service-context-1'
        signature = canonical_service_signature(
            'POST',
            '/video/record/export',
            timestamp,
            nonce,
            'iot-system',
            '42',
            '7',
            'camera-01',
            'export',
            body,
            'unit-test-service-secret-at-least-32-bytes',
        )
        headers = {
            'Content-Type': 'application/json',
            'X-YFeiEye-Service-Id': 'iot-system',
            'X-YFeiEye-Service-User-Id': '42',
            'X-YFeiEye-Service-Tenant-Id': '7',
            'X-YFeiEye-Service-Camera-Id': 'camera-01',
            'X-YFeiEye-Service-Action': 'export',
            'X-YFeiEye-Service-Timestamp': timestamp,
            'X-YFeiEye-Service-Nonce': nonce,
            'X-YFeiEye-Service-Signature': signature,
        }
        client = self._app(record_module).test_client()

        granted = client.post('/video/record/export', data=body, headers=headers)
        from app.services import media_authorization_service
        media_authorization_service._SEEN_NONCES.clear()
        replayed = client.post('/video/record/export', data=body, headers=headers)

        self.assertEqual(200, granted.status_code)
        self.assertEqual('42', captured[0]['operator_user_id'])
        self.assertEqual('7', captured[0]['tenant_id'])
        self.assertEqual(401, replayed.status_code)
        self.assertEqual('service_signature_replayed', replayed.get_json()['reason'])
        self.assertEqual(1, len(captured))

    def test_canonical_service_signature_matches_device_vector(self):
        from app.services.media_authorization_service import canonical_service_signature

        self.assertEqual(
            'sha256=8ecffc612ecfacf6931d49d0d3bd8e4fa4d1b13ff572921046530a91ef535188',
            canonical_service_signature(
                'POST',
                '/video/record/export',
                '1720580000',
                'nonce-1',
                'iot-system',
                '42',
                '7',
                'camera-01',
                'export',
                b'{"camera_id":"camera-01"}',
                'secret-1',
            ),
        )

    def test_signed_coverage_context_can_discover_record_space(self):
        from app.services.media_authorization_service import canonical_service_signature

        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        record_module = self._record_blueprint()
        record_module.get_record_space_by_device_id = lambda device_id, **kwargs: types.SimpleNamespace(
            tenant_id=7,
            to_dict=lambda: {'id': 9, 'device_id': device_id, 'tenant_id': 7}
        )
        path = '/video/record/space/device/camera-01'
        timestamp = str(time.time())
        nonce = 'nonce-coverage-space-1'
        signature = canonical_service_signature(
            'GET', path, timestamp, nonce, 'iot-system', '42', '7',
            'camera-01', 'coverage', b'', 'unit-test-service-secret-at-least-32-bytes'
        )

        response = self._app(record_module).test_client().get(path, headers={
            'X-YFeiEye-Service-Id': 'iot-system',
            'X-YFeiEye-Service-User-Id': '42',
            'X-YFeiEye-Service-Tenant-Id': '7',
            'X-YFeiEye-Service-Camera-Id': 'camera-01',
            'X-YFeiEye-Service-Action': 'coverage',
            'X-YFeiEye-Service-Timestamp': timestamp,
            'X-YFeiEye-Service-Nonce': nonce,
            'X-YFeiEye-Service-Signature': signature,
        })

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(9, response.get_json()['data']['id'])

    def test_service_signature_covers_raw_query_order_and_encoding(self):
        from app.services.media_authorization_service import canonical_service_signature

        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        record_module = self._record_blueprint()
        record_module.query_recording_availability = lambda **kwargs: {
            'camera_id': kwargs.get('camera_id'),
        }
        path = '/video/record/availability'
        original_query = (
            'camera_id=camera-01&device_id=camera-01'
            '&begin_time=2026-07-10T10%3A00%3A00&end_time=2026-07-10T10%3A01%3A00'
        )

        def headers(nonce):
            timestamp = str(time.time())
            return {
                'X-YFeiEye-Service-Id': 'iot-system',
                'X-YFeiEye-Service-User-Id': '42',
                'X-YFeiEye-Service-Tenant-Id': '7',
                'X-YFeiEye-Service-Camera-Id': 'camera-01',
                'X-YFeiEye-Service-Action': 'coverage',
                'X-YFeiEye-Service-Timestamp': timestamp,
                'X-YFeiEye-Service-Nonce': nonce,
                'X-YFeiEye-Service-Signature': canonical_service_signature(
                    'GET', f'{path}?{original_query}', timestamp, nonce,
                    'iot-system', '42', '7', 'camera-01', 'coverage', b'',
                    'unit-test-service-secret-at-least-32-bytes',
                ),
            }

        client = self._app(record_module).test_client()
        granted = client.get(f'{path}?{original_query}', headers=headers('raw-query-original'))
        tampered = client.get(
            f'{path}?{original_query.replace("end_time=2026-07-10T10%3A01%3A00", "end_time=2026-07-10T10%3A02%3A00")}',
            headers=headers('raw-query-tampered'),
        )
        reordered = client.get(
            f'{path}?device_id=camera-01&camera_id=camera-01'
            '&begin_time=2026-07-10T10%3A00%3A00&end_time=2026-07-10T10%3A01%3A00',
            headers=headers('raw-query-reordered'),
        )
        encoding_changed = client.get(
            f'{path}?{original_query.replace("%3A", "%3a")}',
            headers=headers('raw-query-encoding'),
        )

        self.assertEqual(200, granted.status_code, granted.get_json())
        self.assertEqual(401, tampered.status_code, tampered.get_json())
        self.assertEqual('service_signature_invalid', tampered.get_json()['reason'])
        self.assertEqual(401, reordered.status_code, reordered.get_json())
        self.assertEqual('service_signature_invalid', reordered.get_json()['reason'])
        self.assertEqual(401, encoding_changed.status_code, encoding_changed.get_json())
        self.assertEqual('service_signature_invalid', encoding_changed.get_json()['reason'])

    def test_signed_coverage_context_can_query_record_day(self):
        from app.services.media_authorization_service import canonical_service_signature

        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        record_module = self._record_blueprint()
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id, device_id='camera-01', tenant_id=7)
        record_module.list_record_videos_day_detail = lambda space_id, date, device_id, **kwargs: {
            'space_id': space_id,
            'date': date,
            'device_id': device_id,
            'segments': [],
        }
        path = '/video/record/space/9/videos/day'
        timestamp = str(time.time())
        nonce = 'nonce-coverage-day-1'
        query = 'date=2026-07-10&device_id=camera-01'
        signature = canonical_service_signature(
            'GET', f'{path}?{query}', timestamp, nonce, 'iot-system', '42', '7',
            'camera-01', 'coverage', b'', 'unit-test-service-secret-at-least-32-bytes'
        )

        response = self._app(record_module).test_client().get(
            f'{path}?{query}',
            headers={
                'X-YFeiEye-Service-Id': 'iot-system',
                'X-YFeiEye-Service-User-Id': '42',
                'X-YFeiEye-Service-Tenant-Id': '7',
                'X-YFeiEye-Service-Camera-Id': 'camera-01',
                'X-YFeiEye-Service-Action': 'coverage',
                'X-YFeiEye-Service-Timestamp': timestamp,
                'X-YFeiEye-Service-Nonce': nonce,
                'X-YFeiEye-Service-Signature': signature,
            },
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual('camera-01', response.get_json()['data']['device_id'])

    def test_record_day_query_derives_camera_from_space_when_query_omits_device(self):
        from app.services.media_authorization_service import canonical_service_signature

        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        record_module = self._record_blueprint()
        record_module.get_record_space = lambda space_id: types.SimpleNamespace(
            id=space_id, device_id='camera-01', tenant_id=7)
        record_module.list_record_videos_day_detail = lambda space_id, date, device_id, **kwargs: {
            'space_id': space_id,
            'date': date,
            'device_id': device_id,
            'segments': [],
        }
        path = '/video/record/space/9/videos/day'
        query = 'date=2026-07-10'
        timestamp = str(time.time())
        nonce = 'nonce-coverage-day-space-camera-1'
        signature = canonical_service_signature(
            'GET', f'{path}?{query}', timestamp, nonce, 'iot-system', '42', '7',
            'camera-01', 'coverage', b'', 'unit-test-service-secret-at-least-32-bytes',
        )

        response = self._app(record_module).test_client().get(
            f'{path}?{query}',
            headers={
                'X-YFeiEye-Service-Id': 'iot-system',
                'X-YFeiEye-Service-User-Id': '42',
                'X-YFeiEye-Service-Tenant-Id': '7',
                'X-YFeiEye-Service-Camera-Id': 'camera-01',
                'X-YFeiEye-Service-Action': 'coverage',
                'X-YFeiEye-Service-Timestamp': timestamp,
                'X-YFeiEye-Service-Nonce': nonce,
                'X-YFeiEye-Service-Signature': signature,
            },
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual('camera-01', response.get_json()['data']['device_id'])

    def test_signed_service_context_still_obeys_explicit_camera_scope(self):
        from app.services.media_authorization_service import canonical_service_signature

        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        os.environ['YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS'] = 'coverage,export'
        os.environ['YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS'] = 'camera-01'
        record_module = self._record_blueprint()
        record_module.query_recording_availability = lambda **kwargs: {
            'camera_id': kwargs.get('camera_id')
        }
        path = '/video/record/availability'
        timestamp = str(time.time())
        nonce = 'nonce-denied-service-camera-1'
        query = (
            'device_id=camera-02&camera_id=camera-02&begin_time=2026-07-10T10:00:00'
            '&end_time=2026-07-10T10:01:00'
        )
        signature = canonical_service_signature(
            'GET', f'{path}?{query}', timestamp, nonce, 'iot-system', 'service:iot-system', '7',
            'camera-02', 'coverage', b'', 'unit-test-service-secret-at-least-32-bytes'
        )

        response = self._app(record_module).test_client().get(
            f'{path}?{query}',
            headers={
                'X-YFeiEye-Service-Id': 'iot-system',
                'X-YFeiEye-Service-User-Id': 'service:iot-system',
                'X-YFeiEye-Service-Tenant-Id': '7',
                'X-YFeiEye-Service-Camera-Id': 'camera-02',
                'X-YFeiEye-Service-Action': 'coverage',
                'X-YFeiEye-Service-Timestamp': timestamp,
                'X-YFeiEye-Service-Nonce': nonce,
                'X-YFeiEye-Service-Signature': signature,
            },
        )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('service_camera_scope_denied', response.get_json()['reason'])

    @staticmethod
    def _app(record_module):
        app = Flask(__name__)
        app.register_blueprint(record_module.record_bp, url_prefix='/video/record')
        return app

    def _audit_entries(self):
        path = os.path.join(self.temp_dir.name, 'media-access-audit.jsonl')
        if not os.path.isfile(path):
            return []
        with open(path, 'r', encoding='utf-8') as audit_file:
            return [json.loads(line) for line in audit_file if line.strip()]

    def _create_export_job(self, review_case_id=3000, review_item_id=1000):
        from app.services import record_export_service

        importlib.reload(record_export_service)
        started = record_export_service.create_record_export({
            'review_case_id': review_case_id,
            'review_item_id': review_item_id,
            'camera_id': 'camera-01',
            'device_id': 'camera-01',
            'record_uri': '/video/record/space/7/video/camera-01/source.mp4',
            'operator_user_id': '42',
            'tenant_id': '7',
        }, record_resolver=_trusted_record_resolver, async_worker=True)
        return record_export_service, self._record_blueprint(), started['export_id']

    def _media_export_audit_entries(self, export_id, action):
        return [
            entry for entry in self._audit_entries()
            if entry.get('exportId') == export_id and entry.get('action') == action
        ]

    @staticmethod
    def _record_export_access_entries(record_export_service, export_id, action):
        return [
            entry for entry in record_export_service.get_record_export_audit(export_id)
            if entry.get('action') in ('access_allowed', 'access_denied')
            and entry.get('media_action') == action
        ]

    @staticmethod
    def _install_group_policy_service(scopes, updates):
        group_service = types.ModuleType('app.services.space_group_save_time_service')
        group_service.list_group_record_space_authorization_scopes = (
            lambda group_type, group_key: list(scopes)
        )

        def update_group_save_time(group_type, group_key, space_kind, save_time,
                                   **authorization_scope):
            updates.append({
                'group_type': group_type,
                'group_key': group_key,
                'space_kind': space_kind,
                'save_time': save_time,
                **authorization_scope,
            })
            return types.SimpleNamespace(
                group_type=group_type,
                group_key=group_key,
                record_save_time=save_time,
            ), len(scopes)

        group_service.update_group_save_time = update_group_save_time
        sys.modules['app.services.space_group_save_time_service'] = group_service

        save_time_service = types.ModuleType('app.services.space_save_time_service')
        save_time_service.SPACE_KIND_RECORD = 'record'
        sys.modules['app.services.space_save_time_service'] = save_time_service

    @staticmethod
    def _record_manage_response(camera_id, allowed=True, reason='granted'):
        return _JsonResponse({
            'code': 0,
            'data': {
                'allowed': allowed,
                'userId': 42,
                'tenantId': 7,
                'cameraId': camera_id,
                'action': 'record_manage',
                'reason': reason,
            },
        })

    @staticmethod
    def _record_blueprint():
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
        return importlib.import_module('app.blueprints.record')


class TestAlertMediaAuthorization(_ModuleIsolationTestCase):
    def setUp(self):
        super().setUp()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_env = {
            name: os.environ.get(name)
            for name in (
                'YFEIEYE_MEDIA_AUTHORIZATION_URL',
                'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR',
                'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET',
                'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS',
                'YFEIEYE_MEDIA_SERVICE_POLICIES',
                'YFEIEYE_MEDIA_SERVICE_IDS',
                'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS',
                'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS',
                'YFEIEYE_ALERT_INGEST_TENANT_ID',
                'VIDEO_ENV',
                'YFEIEYE_LOCAL_MEDIA_ROOTS',
            )
        }
        os.environ['YFEIEYE_MEDIA_AUTHORIZATION_URL'] = (
            'http://device.local/admin-api/system/auth/media-permission-check'
        )
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'] = self.temp_dir.name
        os.environ['YFEIEYE_LOCAL_MEDIA_ROOTS'] = self.temp_dir.name
        os.environ['VIDEO_ENV'] = 'test'
        os.environ.pop('YFEIEYE_MEDIA_SERVICE_HMAC_KEYS', None)
        os.environ.pop('YFEIEYE_MEDIA_SERVICE_POLICIES', None)
        os.environ['YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS'] = (
            'coverage,export,download,playback,manifest_verify')
        os.environ['YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS'] = 'camera-01'
        os.environ['YFEIEYE_ALERT_INGEST_TENANT_ID'] = '7'

    def tearDown(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        self.temp_dir.cleanup()

    def test_alert_metadata_and_clear_routes_require_authorization_before_service_access(self):
        alert_module = self._alert_blueprint()
        service_calls = []
        for name in (
            'get_alert_list',
            'get_alert_count',
            'get_dashboard_statistics',
            'clear_all_alerts',
            'clear_alerts_by_task_name',
            'get_correlation_events',
        ):
            setattr(alert_module, name, lambda *args, _name=name, **kwargs: (
                service_calls.append((_name, args, kwargs)) or {}))
        client = self._app(alert_module).test_client()

        requests_to_check = (
            ('GET', '/video/alert/page?camera_id=camera-01'),
            ('GET', '/video/alert/correlation?correlation_id=correlation-1&camera_id=camera-01'),
            ('GET', '/video/alert/count?camera_id=camera-01'),
            ('GET', '/video/alert/statistics?camera_id=camera-01'),
            ('DELETE', '/video/alert/clear?task_name=task-1&camera_id=camera-01'),
            ('DELETE', '/video/alert/clear/all?camera_id=camera-01'),
        )
        for method, path in requests_to_check:
            with self.subTest(path=path):
                response = client.open(path, method=method)
                self.assertEqual(401, response.status_code, response.get_json())
                self.assertEqual('authentication_required', response.get_json()['reason'])

        self.assertEqual([], service_calls)

    def test_alert_page_passes_authenticated_tenant_and_camera_scope_to_service(self):
        alert_module = self._alert_blueprint()
        calls = []
        alert_module.get_alert_list = lambda args, **scope: (
            calls.append((dict(args), dict(scope)))
            or {'alert_list': [], 'total': 0})
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'alert_read',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(alert_module).test_client().get(
                '/video/alert/page?camera_id=camera-01',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(1, len(calls))
        self.assertIn('tenant_id', calls[0][1])
        self.assertEqual(7, calls[0][1]['tenant_id'])
        self.assertEqual(['camera-01'], calls[0][1]['camera_ids'])

    def test_alert_clear_all_passes_authenticated_tenant_and_camera_scope_to_service(self):
        alert_module = self._alert_blueprint()
        calls = []
        alert_module.clear_all_alerts = lambda **scope: (
            calls.append(dict(scope)) or {'deleted_count': 1})
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'alert_manage',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(alert_module).test_client().delete(
                '/video/alert/clear/all?camera_id=camera-01',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual([{'tenant_id': 7, 'camera_ids': ['camera-01']}], calls)

    def test_unsigned_production_alert_hook_is_denied_before_processing(self):
        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }

        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_ALERT_INGEST_ALLOW_UNSIGNED': 'true',
        }, clear=False):
            response = self._app(alert_module).test_client().post(
                '/video/alert/hook',
                json={
                    'device_id': 'camera-01',
                    'object': 'person',
                    'event': 'intrusion',
                },
            )

        self.assertEqual(401, response.status_code, response.get_json())
        self.assertEqual('alert_ingest_service_hmac_required', response.get_json()['reason'])
        self.assertEqual([], processed)
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['action'] == 'alert_ingest'
            and entry['cameraId'] == 'camera-01'
            and entry['reason'] == 'alert_ingest_service_hmac_required'
            for entry in self._audit_entries()
        ))

    def test_explicit_development_mode_may_accept_unsigned_alert_hook(self):
        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }
        payload = {
            'device_id': 'camera-01',
            'object': 'person',
            'event': 'intrusion',
        }

        with patch.dict(os.environ, {
            'VIDEO_ENV': 'development',
            'YFEIEYE_ALERT_INGEST_ALLOW_UNSIGNED': 'true',
        }, clear=False):
            response = self._app(alert_module).test_client().post(
                '/video/alert/hook',
                json=payload,
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual([{**payload, 'tenant_id': 7}], processed)
        self.assertTrue(any(
            entry['decision'] == 'allowed'
            and entry['action'] == 'alert_ingest'
            and entry['cameraId'] == 'camera-01'
            and entry['reason'] == 'development_unsigned_alert_ingest'
            for entry in self._audit_entries()
        ))

    def test_alert_ingest_request_builder_round_trips_through_service_hmac(self):
        from app.services import media_authorization_service as authorization_service

        builder = getattr(authorization_service, 'build_alert_ingest_request', None)
        self.assertTrue(callable(builder), 'alert ingest caller must sign the exact JSON body')
        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }
        payload = {
            'device_id': 'camera-01',
            'object': 'person',
            'event': 'intrusion',
        }
        secret = 'alert-ingest-unit-secret-at-least-32-bytes'

        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
            'YFEIEYE_MEDIA_SERVICE_IDS': 'video-algorithm',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS': 'alert_ingest',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
            'YFEIEYE_ALERT_INGEST_SERVICE_ID': 'video-algorithm',
            'YFEIEYE_ALERT_INGEST_TENANT_ID': '7',
        }, clear=False):
            body, headers = builder(
                'http://video.local/video/alert/hook',
                payload,
                nonce='alert-ingest-round-trip-1',
                timestamp=str(time.time()),
            )
            response = self._app(alert_module).test_client().post(
                '/video/alert/hook',
                data=body,
                headers=headers,
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual([{**payload, 'tenant_id': 7}], processed)
        self.assertTrue(any(
            entry['decision'] == 'allowed'
            and entry['action'] == 'alert_ingest'
            and entry['cameraId'] == 'camera-01'
            and entry['authType'] == 'service_hmac'
            for entry in self._audit_entries()
        ))

    def test_alert_ingest_sender_posts_the_signed_bytes_not_a_reserialized_json_body(self):
        from app.services import media_authorization_service as authorization_service

        sender = getattr(authorization_service, 'post_alert_ingest', None)
        self.assertTrue(callable(sender), 'algorithm senders need one signed-body helper')
        payload = {
            'device_id': 'camera-01',
            'object': 'person',
            'event': 'intrusion',
        }
        sentinel = object()
        secret = 'alert-ingest-unit-secret-at-least-32-bytes'
        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
            'YFEIEYE_MEDIA_SERVICE_IDS': 'video-algorithm',
            'YFEIEYE_ALERT_INGEST_SERVICE_ID': 'video-algorithm',
            'YFEIEYE_ALERT_INGEST_TENANT_ID': '7',
        }, clear=False):
            with patch('app.services.media_authorization_service.requests.post',
                       return_value=sentinel) as post_request:
                result = sender(
                    'http://video.local/video/alert/hook',
                    payload,
                    timeout=7,
                )

        self.assertIs(sentinel, result)
        self.assertNotIn('json', post_request.call_args.kwargs)
        self.assertIsInstance(post_request.call_args.kwargs['data'], bytes)
        self.assertEqual(7, post_request.call_args.kwargs['timeout'])
        self.assertEqual(
            'alert_ingest',
            post_request.call_args.kwargs['headers']['X-YFeiEye-Service-Action'],
        )

    def test_default_service_allowlists_accept_video_algorithm_alert_ingest(self):
        from app.services.media_authorization_service import build_alert_ingest_request

        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }
        payload = {'device_id': 'camera-01', 'object': 'person', 'event': 'intrusion'}
        algorithm_secret = 'alert-ingest-unit-secret-at-least-32-bytes'
        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS': json.dumps({
                'video-algorithm': algorithm_secret,
            }),
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
            'YFEIEYE_ALERT_INGEST_SERVICE_ID': 'video-algorithm',
            'YFEIEYE_ALERT_INGEST_TENANT_ID': '7',
        }, clear=False):
            os.environ.pop('YFEIEYE_MEDIA_SERVICE_IDS', None)
            os.environ.pop('YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS', None)
            body, headers = build_alert_ingest_request(
                'http://video.local/video/alert/hook',
                payload,
                nonce='default-alert-ingest-allowlist-1',
                timestamp=str(time.time()),
            )
            response = self._app(alert_module).test_client().post(
                '/video/alert/hook', data=body, headers=headers,
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual([{**payload, 'tenant_id': 7}], processed)

    def test_algorithm_process_env_receives_only_its_own_service_secret(self):
        from app.services import media_authorization_service as authorization_service

        builder = getattr(authorization_service, 'build_alert_ingest_process_env', None)
        self.assertTrue(callable(builder))
        algorithm_secret = 'algorithm-only-secret-at-least-32-bytes'
        iot_system_secret = 'iot-system-secret-at-least-32-bytes'
        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS': json.dumps({
                'iot-system': iot_system_secret,
                'video-algorithm': algorithm_secret,
            }),
            'YFEIEYE_MEDIA_SERVICE_IDS': 'iot-system,video-algorithm',
            'YFEIEYE_ALERT_INGEST_SERVICE_ID': 'video-algorithm',
            'YFEIEYE_ALERT_INGEST_TENANT_ID': '7',
        }, clear=False):
            process_env = builder()

        self.assertEqual(algorithm_secret, process_env['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'])
        self.assertEqual('video-algorithm', process_env['YFEIEYE_MEDIA_SERVICE_IDS'])
        self.assertEqual('video-algorithm', process_env['YFEIEYE_ALERT_INGEST_SERVICE_ID'])
        self.assertEqual('7', process_env['YFEIEYE_ALERT_INGEST_TENANT_ID'])
        self.assertNotIn('YFEIEYE_MEDIA_SERVICE_HMAC_KEYS', process_env)
        self.assertNotIn(iot_system_secret, json.dumps(process_env))

    def test_signed_alert_hook_without_payload_camera_fails_closed(self):
        from app.services.media_authorization_service import canonical_service_signature

        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }
        body = json.dumps({
            'object': 'person',
            'event': 'intrusion',
        }, separators=(',', ':')).encode('utf-8')
        secret = 'alert-ingest-unit-secret-at-least-32-bytes'
        timestamp = str(time.time())
        signature = canonical_service_signature(
            'POST',
            '/video/alert/hook',
            timestamp,
            'alert-ingest-missing-camera-1',
            'video-algorithm',
            'service:video-algorithm',
            '7',
            'camera-01',
            'alert_ingest',
            body,
            secret,
        )

        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
            'YFEIEYE_MEDIA_SERVICE_IDS': 'video-algorithm',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS': 'alert_ingest',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
        }, clear=False):
            response = self._app(alert_module).test_client().post(
                '/video/alert/hook',
                data=body,
                headers={
                    'Content-Type': 'application/json',
                    'X-YFeiEye-Service-Id': 'video-algorithm',
                    'X-YFeiEye-Service-User-Id': 'service:video-algorithm',
                    'X-YFeiEye-Service-Tenant-Id': '7',
                    'X-YFeiEye-Service-Camera-Id': 'camera-01',
                    'X-YFeiEye-Service-Action': 'alert_ingest',
                    'X-YFeiEye-Service-Timestamp': timestamp,
                    'X-YFeiEye-Service-Nonce': 'alert-ingest-missing-camera-1',
                    'X-YFeiEye-Service-Signature': signature,
                },
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('alert_ingest_camera_required', response.get_json()['reason'])
        self.assertEqual([], processed)

    def test_production_alert_hook_rejects_short_hmac_secret(self):
        from app.services.media_authorization_service import canonical_service_signature

        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }
        payload = {'device_id': 'camera-01', 'object': 'person', 'event': 'intrusion'}
        body = json.dumps(payload, separators=(',', ':')).encode('utf-8')
        timestamp = str(time.time())
        secret = 'short'
        signature = canonical_service_signature(
            'POST', '/video/alert/hook', timestamp, 'short-ingest-secret-1',
            'video-algorithm', 'service:video-algorithm', '7', 'camera-01',
            'alert_ingest', body, secret,
        )

        with patch.dict(os.environ, {
            'VIDEO_ENV': 'production',
            'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
            'YFEIEYE_MEDIA_SERVICE_IDS': 'video-algorithm',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS': 'alert_ingest',
            'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
        }, clear=False):
            response = self._app(alert_module).test_client().post(
                '/video/alert/hook', data=body, headers={
                    'Content-Type': 'application/json',
                    'X-YFeiEye-Service-Id': 'video-algorithm',
                    'X-YFeiEye-Service-User-Id': 'service:video-algorithm',
                    'X-YFeiEye-Service-Tenant-Id': '7',
                    'X-YFeiEye-Service-Camera-Id': 'camera-01',
                    'X-YFeiEye-Service-Action': 'alert_ingest',
                    'X-YFeiEye-Service-Timestamp': timestamp,
                    'X-YFeiEye-Service-Nonce': 'short-ingest-secret-1',
                    'X-YFeiEye-Service-Signature': signature,
                },
            )

        self.assertEqual(503, response.status_code, response.get_json())
        self.assertEqual('service_signature_secret_invalid', response.get_json()['reason'])
        self.assertEqual([], processed)

    def test_signed_alert_hook_rejects_outside_local_image_before_processing(self):
        from app.services.media_authorization_service import build_alert_ingest_request

        alert_module = self._alert_blueprint()
        processed = []
        alert_module.process_alert_hook = lambda payload: processed.append(payload) or {
            'status': 'success',
        }
        secret = 'alert-ingest-unit-secret-at-least-32-bytes'

        with tempfile.TemporaryDirectory() as outside_root:
            image_path = os.path.join(outside_root, 'injected-alert.jpg')
            with open(image_path, 'wb') as image_file:
                image_file.write(b'injected-local-image')
            payload = {
                'device_id': 'camera-01',
                'object': 'person',
                'event': 'intrusion',
                'image_path': image_path,
            }
            with patch.dict(os.environ, {
                'VIDEO_ENV': 'production',
                'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': secret,
                'YFEIEYE_MEDIA_SERVICE_IDS': 'video-algorithm',
                'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS': 'alert_ingest',
                'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
                'YFEIEYE_ALERT_INGEST_SERVICE_ID': 'video-algorithm',
                'YFEIEYE_ALERT_INGEST_TENANT_ID': '7',
            }, clear=False):
                body, headers = build_alert_ingest_request(
                    'http://video.local/video/alert/hook',
                    payload,
                    nonce='alert-ingest-outside-image-1',
                    timestamp=str(time.time()),
                )
                response = self._app(alert_module).test_client().post(
                    '/video/alert/hook', data=body, headers=headers,
                )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual(
            'local_media_path_outside_allowed_roots',
            response.get_json()['reason'],
        )
        self.assertEqual([], processed)

    def test_anonymous_alert_image_is_denied_before_local_file_read(self):
        alert_module = self._alert_blueprint()
        image_path = os.path.join(self.temp_dir.name, 'alert.jpg')
        with open(image_path, 'wb') as image_file:
            image_file.write(b'sensitive-alert-image')
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'

        response = self._app(alert_module).test_client().get(
            '/video/alert/image', query_string={'path': image_path}
        )

        self.assertEqual(401, response.status_code, response.get_json())
        self.assertEqual('authentication_required', response.get_json()['reason'])
        self.assertNotIn(b'sensitive-alert-image', response.data)

    def test_owned_alert_record_outside_allowed_roots_is_denied_and_audited(self):
        alert_module = self._alert_blueprint()
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with tempfile.TemporaryDirectory() as outside_root:
            record_path = os.path.join(outside_root, 'outside-alert.mp4')
            with open(record_path, 'wb') as record_file:
                record_file.write(b'outside-alert-record')
            with patch('requests.post', return_value=authorization):
                response = self._app(alert_module).test_client().get(
                    '/video/alert/record',
                    query_string={'path': record_path},
                    headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
                )
            try:
                self.assertEqual(403, response.status_code, response.get_json())
                self.assertEqual(
                    'local_media_path_outside_allowed_roots',
                    response.get_json()['reason'],
                )
                self.assertNotIn(b'outside-alert-record', response.data)
            finally:
                response.close()
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['action'] == 'playback'
            and entry['reason'] == 'local_media_path_outside_allowed_roots'
            for entry in self._audit_entries()
        ))

    def test_owned_alert_image_outside_allowed_roots_is_denied(self):
        alert_module = self._alert_blueprint()
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with tempfile.TemporaryDirectory() as outside_root:
            image_path = os.path.join(outside_root, 'outside-alert.jpg')
            with open(image_path, 'wb') as image_file:
                image_file.write(b'outside-alert-image')
            with patch('requests.post', return_value=authorization):
                response = self._app(alert_module).test_client().get(
                    '/video/alert/image',
                    query_string={'path': image_path},
                    headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
                )
            try:
                self.assertEqual(403, response.status_code, response.get_json())
                self.assertEqual(
                    'local_media_path_outside_allowed_roots',
                    response.get_json()['reason'],
                )
                self.assertNotIn(b'outside-alert-image', response.data)
            finally:
                response.close()

    def test_metadata_owned_local_alert_record_remains_playable_and_audited(self):
        alert_module = self._alert_blueprint()
        record_path = os.path.join(self.temp_dir.name, 'owned-alert.mp4')
        with open(record_path, 'wb') as record_file:
            record_file.write(b'owned-alert-record')
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(alert_module).test_client().get(
                '/video/alert/record',
                query_string={'path': record_path},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        try:
            self.assertEqual(200, response.status_code)
            self.assertEqual(b'owned-alert-record', response.data)
            self.assertTrue(any(
                entry['decision'] == 'allowed'
                and entry['action'] == 'playback'
                and entry['cameraId'] == 'camera-01'
                for entry in self._audit_entries()
            ))
        finally:
            response.close()

    def test_seekable_alert_record_holds_cache_lease_until_response_closes(self):
        alert_module = self._alert_blueprint()
        record_path = os.path.join(self.temp_dir.name, 'leased-alert.mp4')
        with open(record_path, 'wb') as record_file:
            record_file.write(b'leased-alert-record')
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })
        lease = {'path': record_path, 'token': 'lease-1'}
        close_callbacks = []

        from app.services import seekable_playback_service

        original_call_on_close = Response.call_on_close

        def capture_call_on_close(response, callback):
            close_callbacks.append(callback)
            return original_call_on_close(response, callback)

        with patch('requests.post', return_value=authorization), \
                patch.object(
                    seekable_playback_service,
                    'prepare_seekable_mp4_path',
                    return_value={
                        'path': record_path,
                        'source_sha256': 'sha256:source',
                        'output_sha256': 'sha256:output',
                        'lease': lease,
                    }) as prepare, \
                patch.object(
                    seekable_playback_service,
                    'release_seekable_playback_lease',
                    return_value=True) as release, \
                patch.object(Response, 'call_on_close', capture_call_on_close):
            response = self._app(alert_module).test_client().get(
                '/video/alert/record',
                query_string={
                    'path': record_path,
                    'playback_format': 'mp4',
                },
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                },
                buffered=False,
            )

            self.assertEqual(200, response.status_code)
            prepare.assert_called_once_with(record_path, acquire_lease=True)
            release.assert_not_called()
            self.assertEqual(1, len(close_callbacks))
            close_callbacks[0]()
            release.assert_called_once_with(lease)
            response.close()

    def test_seekable_alert_record_releases_cache_lease_when_send_file_fails(self):
        alert_module = self._alert_blueprint()
        record_path = os.path.join(self.temp_dir.name, 'failed-leased-alert.mp4')
        with open(record_path, 'wb') as record_file:
            record_file.write(b'failed-leased-alert-record')
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })
        lease = {'path': record_path, 'token': 'lease-failed-send'}

        from app.services import seekable_playback_service

        with patch('requests.post', return_value=authorization), \
                patch.object(
                    seekable_playback_service,
                    'prepare_seekable_mp4_path',
                    return_value={
                        'path': record_path,
                        'source_sha256': 'sha256:source',
                        'output_sha256': 'sha256:output',
                        'lease': lease,
                    }) as prepare, \
                patch.object(
                    seekable_playback_service,
                    'release_seekable_playback_lease',
                    return_value=True) as release, \
                patch.object(alert_module, 'send_file', side_effect=OSError('send failed')):
            response = self._app(alert_module).test_client().get(
                '/video/alert/record',
                query_string={
                    'path': record_path,
                    'playback_format': 'mp4',
                },
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                },
            )

            self.assertEqual(500, response.status_code)
            prepare.assert_called_once_with(record_path, acquire_lease=True)
            release.assert_called_once_with(lease)
        response.close()

    def test_short_lived_signed_media_url_is_reusable_and_query_tamper_proof(self):
        from app.services.media_authorization_service import canonical_service_signature

        alert_module = self._alert_blueprint()
        record_path = os.path.join(self.temp_dir.name, 'ticketed-alert.flv')
        generated = subprocess.run([
            'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
            '-f', 'lavfi', '-i', 'testsrc=size=160x120:rate=10',
            '-t', '1', '-c:v', 'libx264', '-pix_fmt', 'yuv420p',
            '-an', '-f', 'flv', record_path,
        ], capture_output=True, timeout=30)
        self.assertEqual(0, generated.returncode, generated.stderr.decode('utf-8', errors='replace'))
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'

        path = '/video/alert/record'
        base_query = urlencode({'path': record_path, 'playback_format': 'mp4'})
        timestamp = str(time.time())
        nonce = 'browser-playback-ticket-1'
        signature = canonical_service_signature(
            'GET', f'{path}?{base_query}', timestamp, nonce, 'iot-system', '42', '7',
            'camera-01', 'playback', b'', 'unit-test-service-secret-at-least-32-bytes',
        )
        ticket_query = urlencode({
            'yf_ticket': 'v1',
            'yf_service_id': 'iot-system',
            'yf_user_id': '42',
            'yf_tenant_id': '7',
            'yf_camera_id': 'camera-01',
            'yf_action': 'playback',
            'yf_timestamp': timestamp,
            'yf_nonce': nonce,
            'yf_signature': signature,
        })
        ticketed_url = f'{path}?{base_query}&{ticket_query}'
        client = self._app(alert_module).test_client()

        first = client.get(ticketed_url)
        second = client.get(ticketed_url, headers={'Range': 'bytes=0-7'})
        tampered = client.get(ticketed_url.replace('playback_format=mp4', 'playback_format=flv'))

        try:
            self.assertEqual(200, first.status_code, first.get_json())
            self.assertEqual('video/mp4', first.mimetype)
            self.assertIn(b'ftyp', first.data[:32])
            self.assertIn(second.status_code, (200, 206), second.get_data(as_text=True))
            self.assertEqual(401, tampered.status_code, tampered.get_json())
            self.assertEqual('service_signature_invalid', tampered.get_json()['reason'])
        finally:
            first.close()
            second.close()

    def test_invalid_device_hmac_cannot_read_alert_record_and_is_audited(self):
        alert_module = self._alert_blueprint()
        record_path = os.path.join(self.temp_dir.name, 'alert.mp4')
        with open(record_path, 'wb') as record_file:
            record_file.write(b'sensitive-alert-record')
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: 'camera-01'
        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'

        response = self._app(alert_module).test_client().get(
            '/video/alert/record',
            query_string={'path': record_path, 'camera_id': 'camera-01'},
            headers={
                'X-YFeiEye-Service-Id': 'iot-system',
                'X-YFeiEye-Service-User-Id': '42',
                'X-YFeiEye-Service-Tenant-Id': '7',
                'X-YFeiEye-Service-Camera-Id': 'camera-01',
                'X-YFeiEye-Service-Action': 'playback',
                'X-YFeiEye-Service-Timestamp': str(time.time()),
                'X-YFeiEye-Service-Nonce': 'invalid-alert-record-nonce',
                'X-YFeiEye-Service-Signature': 'sha256=invalid',
            },
        )

        self.assertEqual(401, response.status_code, response.get_json())
        self.assertEqual('service_signature_invalid', response.get_json()['reason'])
        self.assertNotIn(b'sensitive-alert-record', response.data)
        self.assertTrue(any(
            entry['decision'] == 'denied'
            and entry['action'] == 'playback'
            and entry['cameraId'] == 'camera-01'
            and entry['reason'] == 'service_signature_invalid'
            for entry in self._audit_entries()
        ))

    def test_signed_device_context_authorizes_alert_record_query_and_audits_allow(self):
        from app.services.media_authorization_service import canonical_service_signature

        alert_module = self._alert_blueprint()
        tenant_lookup = []
        alert_module._resolve_alert_media_tenant = lambda path, media_type, alert_id=None: (
            tenant_lookup.append((path, media_type, alert_id)) or '7'
        )
        alert_module._do_query_alert_record = lambda *args, **kwargs: (
            alert_module.jsonify({'code': 0, 'data': {'device_id': 'camera-01'}}),
            200,
        )
        os.environ['YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'] = 'unit-test-service-secret-at-least-32-bytes'
        os.environ['YFEIEYE_MEDIA_SERVICE_IDS'] = 'iot-system'
        path = '/video/alert/record/query'
        query = 'device_id=camera-01&alert_time=2026-07-10T10:00:00&alert_id=123'
        timestamp = str(time.time())
        nonce = 'alert-query-nonce-1'
        signature = canonical_service_signature(
            'GET', f'{path}?{query}', timestamp, nonce, 'iot-system', '42', '7',
            'camera-01', 'coverage', b'', 'unit-test-service-secret-at-least-32-bytes',
        )

        response = self._app(alert_module).test_client().get(
            f'{path}?{query}',
            headers={
                'X-YFeiEye-Service-Id': 'iot-system',
                'X-YFeiEye-Service-User-Id': '42',
                'X-YFeiEye-Service-Tenant-Id': '7',
                'X-YFeiEye-Service-Camera-Id': 'camera-01',
                'X-YFeiEye-Service-Action': 'coverage',
                'X-YFeiEye-Service-Timestamp': timestamp,
                'X-YFeiEye-Service-Nonce': nonce,
                'X-YFeiEye-Service-Signature': signature,
            },
        )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual([(None, 'record', '123')], tenant_lookup)
        self.assertTrue(any(
            entry['decision'] == 'allowed'
            and entry['action'] == 'coverage'
            and entry['cameraId'] == 'camera-01'
            for entry in self._audit_entries()
        ))

    def test_alert_record_miss_has_stable_reason(self):
        alert_module = self._alert_blueprint()
        app = self._app(alert_module)
        with patch(
                'app.services.alert_service.resolve_alert_record_video',
                return_value=None):
            with app.app_context():
                response, status = alert_module._do_query_alert_record(
                    'camera-01', '2026-07-10 10:00:00', 300,
                    tenant_id='7')

        self.assertEqual(200, status)
        self.assertEqual(400, response.get_json()['code'])
        self.assertEqual('record_not_found', response.get_json()['reason'])

    def test_alert_tenant_lookup_does_not_query_empty_media_paths(self):
        alert_module = self._alert_blueprint()
        path_lookups = []
        alert_module._metadata_first = lambda *args, **kwargs: path_lookups.append(
            (args, kwargs))
        alert_module._metadata_get = lambda model, identity: (
            types.SimpleNamespace(tenant_id=7) if identity == 'known-alert' else None
        )

        self.assertEqual(
            '7',
            alert_module._resolve_alert_media_tenant(
                None, 'record', alert_id='known-alert'),
        )
        self.assertIsNone(alert_module._resolve_alert_media_tenant(
            None, 'record', alert_id='external-alert'))
        self.assertEqual([], path_lookups)

    def test_alert_tenant_lookup_does_not_trust_mismatched_alert_path(self):
        alert_module = self._alert_blueprint()
        alert = types.SimpleNamespace(
            tenant_id=7,
            record_path='/records/owned.mp4',
        )
        foreign_record = types.SimpleNamespace(tenant_id=8)
        alert_module._metadata_get = lambda model, identity: (
            alert if identity == 'known-alert' else None
        )
        alert_module._metadata_first = lambda model, **filters: (
            foreign_record
            if filters == {'url': '/records/foreign.mp4'}
            else None
        )

        self.assertEqual(
            '8',
            alert_module._resolve_alert_media_tenant(
                '/records/foreign.mp4', 'record', alert_id='known-alert'),
        )
        self.assertEqual(
            '7',
            alert_module._resolve_alert_media_tenant(
                '/records/owned.mp4', 'record', alert_id='known-alert'),
        )

    def test_authorized_camera_cannot_read_unowned_absolute_alert_record(self):
        alert_module = self._alert_blueprint()
        record_path = os.path.join(self.temp_dir.name, 'unowned.mp4')
        with open(record_path, 'wb') as record_file:
            record_file.write(b'unowned-record')
        alert_module._resolve_alert_media_camera = lambda *args, **kwargs: None
        path = '/video/alert/record'
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = self._app(alert_module).test_client().get(
                path,
                query_string={'path': record_path, 'camera_id': 'camera-01'},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(404, response.status_code, response.get_json())
        self.assertNotIn(b'unowned-record', response.data)
        entries = [
            entry for entry in self._audit_entries()
            if entry['resource'] == '/video/alert/record'
        ]
        self.assertEqual(1, len(entries), entries)
        self.assertEqual('denied', entries[0]['decision'])
        self.assertEqual('media_metadata_not_found', entries[0]['reason'])

    @staticmethod
    def _app(alert_module):
        app = Flask(__name__)
        app.register_blueprint(alert_module.alert_bp, url_prefix='/video/alert')
        return app

    def _audit_entries(self):
        path = os.path.join(self.temp_dir.name, 'media-access-audit.jsonl')
        if not os.path.isfile(path):
            return []
        with open(path, 'r', encoding='utf-8') as audit_file:
            return [json.loads(line) for line in audit_file if line.strip()]

    @staticmethod
    def _alert_blueprint():
        alert_service = types.ModuleType('app.services.alert_service')
        for name in (
            'get_alert_list',
            'get_alert_count',
            'create_alert',
            'get_dashboard_statistics',
            'clear_all_alerts',
            'clear_alerts_by_task_name',
            'get_correlation_events',
        ):
            setattr(alert_service, name, lambda *args, **kwargs: {})
        alert_service.resolve_alert_record_video = lambda *args, **kwargs: {
            'device_id': args[0],
            'video_url': '/video/record/space/7/video/camera-01/clip.flv',
        }
        sys.modules['app.services.alert_service'] = alert_service

        alert_hook_service = types.ModuleType('app.services.alert_hook_service')
        alert_hook_service.process_alert_hook = lambda data: {'status': 'success'}
        sys.modules['app.services.alert_hook_service'] = alert_hook_service

        sys.modules.pop('app.blueprints.alert', None)
        return importlib.import_module('app.blueprints.alert')


class TestCameraMediaAuthorization(_ModuleIsolationTestCase):
    def setUp(self):
        super().setUp()
        self.temp_dir = tempfile.TemporaryDirectory()
        self.previous_env = {
            name: os.environ.get(name)
            for name in ('YFEIEYE_MEDIA_AUTHORIZATION_URL', 'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR')
        }
        os.environ['YFEIEYE_MEDIA_AUTHORIZATION_URL'] = (
            'http://device.local/admin-api/system/auth/media-permission-check'
        )
        os.environ['YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'] = self.temp_dir.name

    def tearDown(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        self.temp_dir.cleanup()

    def test_anonymous_snapshot_is_denied_before_camera_capture(self):
        camera_module = self._camera_blueprint()
        captured = []
        camera_module.grab_frame_for_snapshot = lambda device: captured.append(device) or (object(), None)
        camera_module.upload_screenshot_to_minio = lambda *args: '/snapshot.jpg'
        app = Flask(__name__)
        app.register_blueprint(camera_module.camera_bp, url_prefix='/video/camera')

        response = app.test_client().post('/video/camera/device/camera-01/snapshot')

        self.assertEqual(401, response.status_code)
        self.assertEqual('authentication_required', response.get_json()['reason'])
        self.assertEqual([], captured)

    def test_allowed_snapshot_uses_authenticated_camera_scope_and_is_audited(self):
        camera_module = self._camera_blueprint()
        captured = []
        camera_module.grab_frame_for_snapshot = lambda device: captured.append(device) or (object(), None)
        camera_module.upload_screenshot_to_minio = lambda *args: '/snapshot.jpg'
        app = Flask(__name__)
        app.register_blueprint(camera_module.camera_bp, url_prefix='/video/camera')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = app.test_client().post(
                '/video/camera/device/camera-01/snapshot',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual(0, response.get_json()['code'])
        self.assertEqual(1, len(captured))
        path = os.path.join(self.temp_dir.name, 'media-access-audit.jsonl')
        audit = []
        if os.path.isfile(path):
            with open(path, 'r', encoding='utf-8') as audit_file:
                audit = [json.loads(line) for line in audit_file if line.strip()]
        self.assertTrue(any(
            entry['decision'] == 'allowed'
            and entry['action'] == 'snapshot'
            and entry['cameraId'] == 'camera-01'
            and entry['userId'] == '42'
            and entry['tenantId'] == '7'
            for entry in audit
        ))

    def test_camera_screenshot_metadata_persists_authenticated_tenant(self):
        camera_module = self._camera_blueprint()
        created = []
        persisted = []

        class Image:
            def __init__(self, **values):
                created.append(dict(values))
                self.id = 1

        class Encoded:
            @staticmethod
            def tobytes():
                return b'jpeg'

        class Minio:
            @staticmethod
            def bucket_exists(bucket):
                return True

            @staticmethod
            def put_object(*args, **kwargs):
                return None

        camera_module.Image = Image
        camera_module.cv2 = types.SimpleNamespace(
            imencode=lambda *args, **kwargs: (True, Encoded()))
        camera_module.get_minio_client = lambda: Minio()
        camera_module.ensure_bucket_private = lambda *args, **kwargs: None
        camera_module.db = types.SimpleNamespace(session=types.SimpleNamespace(
            add=lambda value: persisted.append(value),
            commit=lambda: None,
            rollback=lambda: None,
        ))
        frame = types.SimpleNamespace(shape=(10, 20, 3))

        with self._app_for_camera(camera_module).app_context():
            result = camera_module.upload_screenshot_to_minio(
                'camera-01', frame, tenant_id='7')

        self.assertTrue(result)
        self.assertEqual(1, len(created))
        self.assertIn('tenant_id', created[0])
        self.assertEqual(7, created[0]['tenant_id'])
        self.assertEqual(1, len(persisted))

    def test_snapshot_image_rejects_object_without_persisted_camera_metadata(self):
        camera_module = self._camera_blueprint()
        camera_module.get_minio_client = lambda: self.fail(
            'unauthorized object metadata must not reach MinIO')
        app = Flask(__name__)
        app.register_blueprint(camera_module.camera_bp, url_prefix='/video/camera')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = app.test_client().get(
                '/video/camera/device/camera-01/snapshot-image/'
                'tenants/7/cameras/camera-02/private.jpg',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(404, response.status_code)
        self.assertEqual('snapshot_object_scope_denied', response.get_json()['reason'])
        entries = [
            entry for entry in self._audit_entries()
            if entry['resource'].endswith('/snapshot-image/'
                                          'tenants/7/cameras/camera-02/private.jpg')
        ]
        self.assertEqual(1, len(entries), entries)
        self.assertEqual('denied', entries[0]['decision'])
        self.assertEqual('snapshot_object_scope_denied', entries[0]['reason'])

    def test_stream_ticket_rejects_logged_in_user_without_camera_permission(self):
        camera_module = self._camera_blueprint()
        app = Flask(__name__)
        app.register_blueprint(camera_module.camera_bp, url_prefix='/video/camera')
        login = _JsonResponse({'code': 0, 'data': {'userId': 42}})
        denied = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': False,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-02',
                'action': 'playback',
                'reason': 'camera_not_allowed',
            },
        })

        with patch.dict(os.environ, {
            'STREAM_TICKET_SECRET': 'stream-ticket-secret-at-least-32-bytes',
        }, clear=False), patch('requests.get', return_value=login), patch(
            'requests.post', return_value=denied,
        ) as permission_check:
            response = app.test_client().post(
                '/video/camera/stream/ticket/sign',
                json={'path': '/live/camera-02.flv', 'ttl': 90},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('camera_not_allowed', response.get_json()['reason'])
        self.assertEqual(1, permission_check.call_count)

    def test_stream_ticket_authorizes_the_camera_owning_the_exact_stream_path(self):
        camera_module = self._camera_blueprint()
        app = Flask(__name__)
        app.register_blueprint(camera_module.camera_bp, url_prefix='/video/camera')
        login = _JsonResponse({'code': 0, 'data': {'userId': 42}})
        allowed = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'playback',
                'reason': 'granted',
            },
        })

        with patch.dict(os.environ, {
            'STREAM_TICKET_SECRET': 'stream-ticket-secret-at-least-32-bytes',
        }, clear=False), patch('requests.get', return_value=login), patch(
            'requests.post', return_value=allowed,
        ) as permission_check:
            response = app.test_client().post(
                '/video/camera/stream/ticket/sign',
                json={'path': '/live/camera-01.flv', 'ttl': 90},
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code, response.get_json())
        self.assertEqual(0, response.get_json()['code'])
        permission_payload = permission_check.call_args.kwargs['json']
        self.assertEqual('camera-01', permission_payload['cameraId'])
        self.assertEqual('playback', permission_payload['action'])

    def _audit_entries(self):
        path = os.path.join(self.temp_dir.name, 'media-access-audit.jsonl')
        if not os.path.isfile(path):
            return []
        with open(path, 'r', encoding='utf-8') as audit_file:
            return [json.loads(line) for line in audit_file if line.strip()]

    @staticmethod
    def _camera_blueprint():
        minio_module = types.ModuleType('minio')
        minio_module.Minio = object
        minio_error_module = types.ModuleType('minio.error')
        minio_error_module.S3Error = Exception
        sys.modules['minio'] = minio_module
        sys.modules['minio.error'] = minio_error_module

        devices = [
            types.SimpleNamespace(
                id=camera_id,
                source=f'rtsp://{camera_id}/source',
                rtmp_stream=f'rtmp://media/live/{camera_id}',
                http_stream=f'http://media/live/{camera_id}.flv',
                ai_rtmp_stream=f'rtmp://media/ai/{camera_id}',
                ai_http_stream=f'http://media/ai/{camera_id}.flv',
                ip='127.0.0.1',
                port=80,
                username='camera-user',
                password='camera-password',
            )
            for camera_id in ('camera-01', 'camera-02')
        ]

        class _Query:
            @staticmethod
            def get(device_id):
                return next(
                    (device for device in devices if device.id == str(device_id)),
                    None,
                )

            @staticmethod
            def all():
                return list(devices)

            @staticmethod
            def filter_by(**kwargs):
                return types.SimpleNamespace(
                    first=lambda: None,
                    order_by=lambda *args, **kw: types.SimpleNamespace(first=lambda: None)
                )

        class _Device:
            query = _Query()

        class _Image:
            query = _Query()
            created_at = types.SimpleNamespace(desc=lambda: None)

        models = types.ModuleType('models')
        models.Device = _Device
        models.Image = _Image
        models.DeviceDirectory = object
        models.DetectionRegion = object
        models.StreamForwardTask = object
        models.AlgorithmTask = object
        models.db = types.SimpleNamespace(session=types.SimpleNamespace(rollback=lambda: None))
        sys.modules['models'] = models

        camera_service = types.ModuleType('app.services.camera_service')
        for name in (
            'register_camera',
            'register_camera_by_onvif',
            'get_camera_info',
            'update_camera',
            'delete_camera',
            'batch_delete_cameras',
            'search_camera',
            'get_snapshot_uri',
            'refresh_camera',
            '_to_dict',
        ):
            setattr(camera_service, name, lambda *args, **kwargs: None)
        camera_service.OnvifCamera = object
        services_package = importlib.import_module('app.services')
        setattr(services_package, 'camera_service', camera_service)
        sys.modules['app.services.camera_service'] = camera_service

        gb28181_source = types.ModuleType('app.utils.gb28181_source')
        gb28181_source.resolve_gb28181_source = lambda source, **kwargs: source
        sys.modules['app.utils.gb28181_source'] = gb28181_source
        node_client = types.ModuleType('app.utils.node_client')
        node_client.resolve_java_backend_url = lambda: 'http://device.local'
        sys.modules['app.utils.node_client'] = node_client

        sys.modules.pop('app.blueprints.camera', None)
        return importlib.import_module('app.blueprints.camera')

    @staticmethod
    def _app_for_camera(camera_module):
        app = Flask(__name__)
        app.register_blueprint(camera_module.camera_bp, url_prefix='/video/camera')
        return app


class TestSnapMediaAuthorization(_ModuleIsolationTestCase):
    def setUp(self):
        super().setUp()
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

    def tearDown(self):
        for name, value in self.previous_env.items():
            if value is None:
                os.environ.pop(name, None)
            else:
                os.environ[name] = value
        self.temp_dir.cleanup()

    def test_anonymous_snap_list_is_denied_before_metadata_query(self):
        snap_module = self._snap_blueprint()
        calls = []
        snap_module.list_snap_images = lambda *args, **kwargs: (
            calls.append((args, kwargs)) or {'items': [], 'total': 0})
        app = Flask(__name__)
        app.register_blueprint(snap_module.snap_bp, url_prefix='/video/snap')

        response = app.test_client().get('/video/snap/space/7/images')

        self.assertEqual(401, response.status_code)
        self.assertEqual('authentication_required', response.get_json()['reason'])
        self.assertEqual([], calls)

    def test_snap_list_rejects_device_query_outside_persisted_space_camera(self):
        snap_module = self._snap_blueprint()
        calls = []
        snap_module.list_snap_images = lambda *args, **kwargs: (
            calls.append((args, kwargs)) or {'items': [], 'total': 0})
        app = Flask(__name__)
        app.register_blueprint(snap_module.snap_bp, url_prefix='/video/snap')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = app.test_client().get(
                '/video/snap/space/7/images?device_id=camera-02',
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                },
            )

        self.assertEqual(403, response.status_code)
        self.assertEqual('snapshot_camera_scope_denied', response.get_json()['reason'])
        self.assertEqual([], calls)

    def test_snap_get_rejects_object_not_owned_by_persisted_space_camera(self):
        image = types.SimpleNamespace(
            space_id=7,
            device_id='camera-02',
            object_name='tenants/7/cameras/camera-02/private.jpg',
        )
        snap_module = self._snap_blueprint(image=image)
        reads = []
        snap_module.get_snap_image = lambda *args: (
            reads.append(args) or (b'private', 'image/jpeg', 'private.jpg'))
        app = Flask(__name__)
        app.register_blueprint(snap_module.snap_bp, url_prefix='/video/snap')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = app.test_client().get(
                '/video/snap/space/7/image/'
                'tenants/7/cameras/camera-02/private.jpg',
                headers={
                    'Authorization': 'Bearer scoped-user',
                    'tenant-id': '7',
                },
            )

        self.assertEqual(404, response.status_code)
        self.assertEqual('snapshot_object_scope_denied', response.get_json()['reason'])
        self.assertEqual([], reads)

    def test_snapshot_get_denies_authenticated_tenant_different_from_space_owner(self):
        snap_module = self._snap_blueprint()
        reads = []
        snap_module.get_snap_image = lambda *args: (
            reads.append(args) or (b'private', 'image/jpeg', 'private.jpg'))
        app = Flask(__name__)
        app.register_blueprint(snap_module.snap_bp, url_prefix='/video/snap')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 8,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = app.test_client().get(
                '/video/snap/space/7/image/tenants/7/cameras/camera-01/private.jpg',
                headers={'Authorization': 'Bearer other-tenant', 'tenant-id': '8'},
            )

        self.assertEqual(403, response.status_code, response.get_json())
        self.assertEqual('tenant_scope_denied', response.get_json()['reason'])
        self.assertEqual([], reads)

    def test_snapshot_get_queries_tenant_camera_space_and_object_identity(self):
        snap_module = self._snap_blueprint()
        filters_seen = []

        class Query:
            @staticmethod
            def filter_by(**filters):
                filters_seen.append(filters)
                matched = filters == {
                    'tenant_id': 7,
                    'space_id': 7,
                    'device_id': 'camera-01',
                    'object_name': 'tenants/7/cameras/camera-01/private.jpg',
                }
                return types.SimpleNamespace(
                    first=lambda: types.SimpleNamespace(**filters) if matched else None)

        sys.modules['models'].SnapImage = types.SimpleNamespace(query=Query())
        snap_module.get_snap_image = lambda *args, **kwargs: (
            b'private', 'image/jpeg', 'private.jpg')
        app = Flask(__name__)
        app.register_blueprint(snap_module.snap_bp, url_prefix='/video/snap')
        authorization = _JsonResponse({
            'code': 0,
            'data': {
                'allowed': True,
                'userId': 42,
                'tenantId': 7,
                'cameraId': 'camera-01',
                'action': 'snapshot',
                'reason': 'granted',
            },
        })

        with patch('requests.post', return_value=authorization):
            response = app.test_client().get(
                '/video/snap/space/7/image/tenants/7/cameras/camera-01/private.jpg',
                headers={'Authorization': 'Bearer scoped-user', 'tenant-id': '7'},
            )

        self.assertEqual(200, response.status_code)
        self.assertEqual(
            {
                'tenant_id': 7,
                'space_id': 7,
                'device_id': 'camera-01',
                'object_name': 'tenants/7/cameras/camera-01/private.jpg',
            },
            filters_seen[-1],
        )

    @staticmethod
    def _snap_blueprint(image=None):
        class Query:
            def filter_by(self, **filters):
                if image and all(getattr(image, key, None) == value
                                 for key, value in filters.items()):
                    return types.SimpleNamespace(first=lambda: image)
                return types.SimpleNamespace(first=lambda: None)

        models = types.ModuleType('models')
        models.db = types.SimpleNamespace(
            session=types.SimpleNamespace(rollback=lambda: None))
        models.DetectionRegion = object
        models.SnapImage = types.SimpleNamespace(query=Query())
        models.parse_shanghai_naive_to_utc_naive = lambda value: value
        sys.modules['models'] = models

        snap_space_service = types.ModuleType('app.services.snap_space_service')
        space = types.SimpleNamespace(id=7, device_id='camera-01', tenant_id=7)
        snap_space_service.get_snap_space = lambda space_id: space
        snap_space_service.get_snap_space_by_device_id = lambda device_id: space
        snap_space_service.list_snap_spaces = lambda *args, **kwargs: {
            'items': [], 'total': 0}
        for name in (
            'create_snap_space', 'update_snap_space', 'delete_snap_space',
            'sync_spaces_to_minio',
        ):
            setattr(snap_space_service, name, lambda *args, **kwargs: None)
        sys.modules['app.services.snap_space_service'] = snap_space_service

        for module_name, names in {
            'app.services.snap_task_service': (
                'create_snap_task', 'update_snap_task', 'delete_snap_task',
                'get_snap_task', 'list_snap_tasks', 'start_task', 'stop_task',
                'restart_task', 'get_task_logs',
            ),
            'app.services.algorithm_service': (
                'create_task_algorithm_service', 'update_task_algorithm_service',
                'delete_task_algorithm_service', 'get_task_algorithm_services',
                'create_region_algorithm_service', 'update_region_algorithm_service',
                'delete_region_algorithm_service', 'get_region_algorithm_services',
            ),
            'app.services.storage_service': (
                'get_or_create_device_storage_config', 'update_device_storage_config',
                'get_device_storage_info', 'check_and_cleanup_storage',
            ),
            'app.services.snap_image_service': (
                'list_snap_images', 'delete_snap_images', 'get_snap_image',
                'cleanup_old_images_by_save_time', 'sync_snap_images_metadata',
            ),
        }.items():
            module = types.ModuleType(module_name)
            for name in names:
                setattr(module, name, lambda *args, **kwargs: None)
            sys.modules[module_name] = module

        sys.modules.pop('app.blueprints.snap', None)
        return importlib.import_module('app.blueprints.snap')


if __name__ == '__main__':
    unittest.main()
