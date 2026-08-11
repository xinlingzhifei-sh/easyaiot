"""Tenant isolation gates for VIDEO maintenance and drift paths."""
from __future__ import annotations

import ast
import importlib
import os
import re
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock


class _Column:
    def __init__(self, name):
        self.name = name

    def __eq__(self, value):
        return ('eq', self.name, value)

    def in_(self, values):
        return ('in', self.name, tuple(values))

    def isnot(self, value):
        return ('isnot', self.name, value)


class _DeleteQuery:
    def __init__(self):
        self.filters = []

    def filter(self, *expressions):
        self.filters.extend(expressions)
        return self

    def delete(self, synchronize_session=False):
        del synchronize_session
        return 1


class TenantMaintenanceTest(unittest.TestCase):

    @mock.patch(
        'app.services.runtime_config_service.resolve_gb28181_source',
        create=True,
    )
    def test_cpp_runtime_resolves_gb28181_virtual_source(self, resolver):
        from app.services import runtime_config_service as runtime_config

        resolver.return_value = 'rtsp://media.example/live/channel'
        device = types.SimpleNamespace(
            source='gb28181://device/channel',
            rtsp_direct='',
        )

        self.assertEqual(
            'rtsp://media.example/live/channel',
            runtime_config.resolve_algo_rtsp_url(device),
        )
        resolver.assert_called_once_with(device.source, logger=runtime_config.logger)

    def test_disk_guard_float_environment_values_fall_back_safely(self):
        from app.services import playback_disk_guard_service as guard

        with mock.patch.dict(os.environ, {
            'PLAYBACK_KEEP_RATIO': '0.25',
            'PLAYBACK_DISK_CRITICAL_PERCENT': 'invalid',
        }, clear=True):
            self.assertEqual(0.25, guard._env_float('PLAYBACK_KEEP_RATIO', 0.2))
            self.assertEqual(
                90.0,
                guard._env_float('PLAYBACK_DISK_CRITICAL_PERCENT', 90.0),
            )

    def test_production_runtime_rejects_missing_or_weak_application_secret(self):
        from app.utils.video_env import validate_production_runtime_secrets

        common = {
            'VIDEO_ENV': 'production',
            'MINIO_ACCESS_KEY': 'video-service-user',
            'MINIO_SECRET_KEY': 'm' * 32,
            'YFEIEYE_SRS_HOOK_TOKEN': 'h' * 32,
        }
        for secret in ('', 'your-secret-key-please-change-this-to-a-random-string', 'short'):
            with self.subTest(secret=secret), mock.patch.dict(
                    os.environ, {**common, 'SECRET_KEY': secret}, clear=True):
                with self.assertRaises(RuntimeError):
                    validate_production_runtime_secrets()
        for key, value in (
                ('MINIO_ACCESS_KEY', ''),
                ('MINIO_ACCESS_KEY', 'minioadmin'),
                ('MINIO_SECRET_KEY', ''),
                ('MINIO_SECRET_KEY', 'short'),
                ('YFEIEYE_SRS_HOOK_TOKEN', ''),
                ('YFEIEYE_SRS_HOOK_TOKEN', 'short'),
                ('YFEIEYE_SRS_HOOK_TOKEN', ('h' * 31) + '?')):
            with self.subTest(key=key, value=value), mock.patch.dict(
                    os.environ, {
                        **common,
                        'SECRET_KEY': 's' * 32,
                        key: value,
                    }, clear=True):
                with self.assertRaises(RuntimeError):
                    validate_production_runtime_secrets()

    def test_production_runtime_accepts_external_strong_secrets(self):
        from app.utils.video_env import validate_production_runtime_secrets

        with mock.patch.dict(os.environ, {
                'VIDEO_ENV': 'production',
                'SECRET_KEY': 's' * 32,
                'MINIO_ACCESS_KEY': 'video-service-user',
                'MINIO_SECRET_KEY': 'm' * 32,
                'YFEIEYE_SRS_HOOK_TOKEN': 'h' * 32,
        }, clear=True):
            validate_production_runtime_secrets()

    def test_srs_hook_token_authorization_is_fail_closed(self):
        from app.utils.video_env import authorize_srs_hook_token

        token = 'h' * 32
        for configured, provided, expected in (
                ('', '', False),
                ('short', 'short', False),
                (('h' * 31) + '?', ('h' * 31) + '?', False),
                (token, '', False),
                (token, 'wrong-' + token, False),
                (token, token, True)):
            with self.subTest(configured=configured, provided=provided), mock.patch.dict(
                    os.environ, {'YFEIEYE_SRS_HOOK_TOKEN': configured}, clear=True):
                self.assertEqual(expected, authorize_srs_hook_token(provided))

    def test_video_runtime_state_mounts_live_outside_versioned_release_tree(self):
        compose = (Path(__file__).resolve().parent / 'docker-compose.yaml').read_text(
            encoding='utf-8')
        device_compose = (
            Path(__file__).resolve().parents[1] / 'DEVICE' / 'docker-compose.yml'
        ).read_text(encoding='utf-8')

        for directory in ('data', 'static', 'temp_uploads', 'model', 'alert_images', 'logs'):
            self.assertIn(
                f'${{YFEIEYE_VIDEO_STATE_ROOT:-/data/yfeieye-video}}/{directory}',
                compose,
            )
            self.assertNotIn(f'\n      - ./{directory}:/app/{directory}', compose)
        self.assertIn(
            '${YFEIEYE_VIDEO_STATE_ROOT:-/data/yfeieye-video}/alert_images:/app/alert_images',
            device_compose,
        )
        self.assertNotIn('../VIDEO/alert_images:/app/alert_images', device_compose)

    def test_video_compose_keeps_host_service_private_and_secrets_out_of_source(self):
        compose = (Path(__file__).resolve().parent / 'docker-compose.yaml').read_text(
            encoding='utf-8')
        example = (Path(__file__).resolve().parent / 'env.example').read_text(
            encoding='utf-8')

        self.assertIn('FLASK_RUN_HOST=${FLASK_RUN_HOST:?', compose)
        self.assertIn('ALLOWED_HOSTS=${ALLOWED_HOSTS:?', compose)
        self.assertNotRegex(compose, r'FLASK_RUN_HOST=\$\{FLASK_RUN_HOST:-(?:127\.0\.0\.1|0\.0\.0\.0)\}')
        self.assertNotRegex(compose, r'ALLOWED_HOSTS=\$\{ALLOWED_HOSTS:-\[?\*\]?\}')
        self.assertNotIn('DATABASE_URL=postgresql://', compose)
        self.assertNotIn('SECRET_KEY=${SECRET_KEY:-', compose)
        self.assertNotRegex(compose, r'MINIO_ACCESS_KEY=\$\{MINIO_ACCESS_KEY:-[^}]+\}')
        self.assertNotRegex(compose, r'MINIO_SECRET_KEY=\$\{MINIO_SECRET_KEY:-[^}]+\}')
        self.assertIn(
            'env_file:\n'
            '      - ${YFEIEYE_VIDEO_COMPOSE_ENV_FILE:-.env.docker}',
            compose,
        )
        self.assertIn(
            'http://$${FLASK_RUN_HOST}:$${FLASK_RUN_PORT:-6000}/actuator/health',
            compose,
        )
        self.assertIn('FLASK_RUN_HOST=172.17.0.1', example)
        self.assertIn('ALLOWED_HOSTS=172.17.0.1', example)
        for unsafe in (
                'your-secret-key-please-change-this-to-a-random-string',
                'MINIO_ACCESS_KEY=minioadmin'):
            self.assertNotIn(unsafe, example)
        self.assertNotRegex(example, r'(?m)^DATABASE_URL=\S+')
        self.assertNotRegex(example, r'(?m)^MINIO_SECRET_KEY=\S+')

    def test_srs_hooks_require_shared_token_and_public_nginx_paths_are_denied(self):
        root = Path(__file__).resolve().parents[1]
        video_root = Path(__file__).resolve().parent
        camera_source = (video_root / 'app' / 'blueprints' / 'camera.py').read_text(
            encoding='utf-8')
        installer = (root / '.scripts' / 'docker' / 'install_middleware_linux.sh').read_text(
            encoding='utf-8')
        middleware_example = (root / '.scripts' / 'docker' / 'env.example').read_text(
            encoding='utf-8')

        self.assertGreaterEqual(camera_source.count('authorize_srs_hook_token('), 2)
        self.assertIn('resolve_srs_hook_token()', installer)
        self.assertIn('?hook_token=${srs_hook_token}', installer)
        self.assertIn('chmod 600 "$srs_config_file"', installer)
        self.assertNotIn('/admin-api/video/camera/callback/on_publish', installer)
        self.assertIn('VIDEO_CALLBACK_HOST=172.17.0.1', middleware_example)
        self.assertIn('YFEIEYE_SRS_HOOK_TOKEN=', middleware_example)

        expected_public_paths = {
            root / 'APP' / 'conf' / 'nginx.conf': (
                '/dev-api/video/camera/callback/on_publish',
                '/dev-api/video/camera/callback/on_dvr',
            ),
            root / 'WEB' / 'conf' / 'nginx.conf': (
                '/yfeieye/dev-api/video/camera/callback/on_publish',
                '/yfeieye/dev-api/video/camera/callback/on_dvr',
                '/dev-api/video/camera/callback/on_publish',
                '/dev-api/video/camera/callback/on_dvr',
            ),
            root / 'WEB' / 'conf' / 'nginx.mini.conf': (
                '/dev-api/video/camera/callback/on_publish',
                '/dev-api/video/camera/callback/on_dvr',
            ),
        }
        for path, callback_paths in expected_public_paths.items():
            source = path.read_text(encoding='utf-8')
            self.assertIn(
                'include /etc/nginx/yfeieye-secrets/yfeieye-stream-secret.runtime.conf;',
                source,
            )
            self.assertNotIn('set $stream_secret "";', source)
            self.assertNotIn('yfeieye-stream-secret*.conf', source)
            for callback_path in callback_paths:
                self.assertRegex(
                    source,
                    rf'location\s*=\s*{re.escape(callback_path)}\s*\{{\s*return\s+403;',
                )

    def test_alternate_media_hooks_require_token_and_are_not_publicly_proxied(self):
        root = Path(__file__).resolve().parents[1]
        media_hook_path = Path(__file__).resolve().parent / 'app' / 'blueprints' / 'media_hook.py'
        media_hook_source = media_hook_path.read_text(encoding='utf-8')
        tree = ast.parse(media_hook_source)
        protected_functions = {
            'srs_on_dvr',
            'srs_on_publish',
            'srs_on_unpublish',
            'snap_completed',
            'zlm_on_record',
        }
        functions = {
            node.name: node for node in tree.body if isinstance(node, ast.FunctionDef)
        }
        for name in protected_functions:
            calls = [
                node.func.id
                for node in ast.walk(functions[name])
                if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
            ]
            self.assertIn('_require_internal_hook_token', calls, name)
        self.assertIn("request.headers.get('X-YFeiEye-Hook-Token')", media_hook_source)

        public_prefixes = {
            root / 'APP' / 'conf' / 'nginx.conf': (
                '/dev-api/video/media/hook/',
            ),
            root / 'WEB' / 'conf' / 'nginx.conf': (
                '/yfeieye/dev-api/video/media/hook/',
                '/dev-api/video/media/hook/',
            ),
            root / 'WEB' / 'conf' / 'nginx.mini.conf': (
                '/dev-api/video/media/hook/',
            ),
        }
        for path, prefixes in public_prefixes.items():
            source = path.read_text(encoding='utf-8')
            for prefix in prefixes:
                self.assertRegex(
                    source,
                    rf'location\s+\^~\s+{re.escape(prefix)}\s*\{{\s*return\s+403;',
                )

        cluster_root = root / '.scripts' / 'media-cluster'
        srs_template = (cluster_root / 'srs' / 'cluster.conf.template').read_text(
            encoding='utf-8')
        zlm_template = (cluster_root / 'zlm' / 'config.ini.template').read_text(
            encoding='utf-8')
        cluster_installer = (cluster_root / 'install_media_stack.sh').read_text(
            encoding='utf-8')
        cluster_enable = (cluster_root / 'enable_cluster_mode.sh').read_text(
            encoding='utf-8')
        cluster_compose = (cluster_root / 'docker-compose.media-node.yml').read_text(
            encoding='utf-8')
        for template in (srs_template, zlm_template):
            self.assertIn('?hook_token=${YFEIEYE_SRS_HOOK_TOKEN}', template)
        self.assertIn('YFEIEYE_SRS_HOOK_TOKEN', cluster_installer)
        self.assertIn("envsubst '${MEDIA_NODE_ID} ${MEDIA_HOOK_HOST}", cluster_installer)
        self.assertIn('${YFEIEYE_SRS_HOOK_TOKEN}', cluster_installer)
        self.assertGreaterEqual(cluster_installer.count('chmod 600 "${out}"'), 2)
        self.assertIn('YFEIEYE_SRS_HOOK_TOKEN=', cluster_enable)
        self.assertIn('chmod 600 "${ENV_SNIPPET}"', cluster_enable)
        self.assertIn('YFEIEYE_SRS_HOOK_TOKEN', cluster_compose)
        self.assertIn('container_name: "${MEDIA_NODE_ID}-srs"', cluster_compose)
        self.assertIn('container_name: "${MEDIA_NODE_ID}-zlm"', cluster_compose)
        self.assertGreaterEqual(
            cluster_installer.count('export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"'),
            2,
        )
        self.assertNotIn('ZLM_SECRET="${ZLM_SECRET:-yFeiEye_Media_Secret}"', cluster_installer)
        self.assertIn('validate_zlm_secret()', cluster_installer)
        self.assertIn('ZLM_SECRET must contain at least 32 characters', cluster_installer)
        self.assertIn('validate_hook_token()', cluster_enable)
        self.assertIn(
            'YFEIEYE_SRS_HOOK_TOKEN must contain at least 32 characters',
            cluster_enable,
        )

    def test_direct_service_api_proxies_require_platform_authentication(self):
        root = Path(__file__).resolve().parents[1]

        def location_block(source, prefix):
            match = re.search(
                rf'location\s+\^~\s+{re.escape(prefix)}\s*\{{',
                source,
            )
            self.assertIsNotNone(match, prefix)
            start = match.end() - 1
            depth = 0
            for index in range(start, len(source)):
                if source[index] == '{':
                    depth += 1
                elif source[index] == '}':
                    depth -= 1
                    if depth == 0:
                        return source[start:index + 1]
            self.fail(f'location block is not closed: {prefix}')

        configs = {
            root / 'WEB' / 'conf' / 'nginx.conf': {
                'prefixes': (
                    '/yfeieye/dev-api/model/',
                    '/yfeieye/dev-api/ai/',
                    '/yfeieye/dev-api/video/',
                    '/yfeieye/dev-api/srs/',
                    '/dev-api/model/',
                    '/dev-api/ai/',
                    '/dev-api/video/',
                    '/dev-api/srs/',
                ),
                'auth_upstream': (
                    'http://gateway:48080/admin-api/system/auth/'
                    'check-session'
                ),
            },
            root / 'WEB' / 'conf' / 'nginx.mini.conf': {
                'prefixes': (
                    '/admin-api/model/',
                    '/dev-api/model/',
                    '/dev-api/ai/',
                    '/dev-api/video/',
                    '/dev-api/srs/',
                ),
                'auth_upstream': (
                    'http://system-host:48099/admin-api/system/auth/'
                    'check-session'
                ),
            },
            root / 'APP' / 'conf' / 'nginx.conf': {
                'prefixes': (
                    '/dev-api/model/',
                    '/dev-api/ai/',
                    '/dev-api/video/',
                    '/dev-api/srs/',
                    '/dev-api/nodeRed/',
                    '/nodeRed',
                ),
                'auth_upstream': (
                    'http://gateway:48080/admin-api/system/auth/'
                    'check-session'
                ),
            },
        }

        for path, expected in configs.items():
            source = path.read_text(encoding='utf-8')
            self.assertRegex(
                source,
                r'(?s)map\s+\$http_authorization\s+\$platform_api_auth_candidate'
                r'\s*\{.*?'
                r"''\s+\$http_x_authorization;",
            )
            self.assertRegex(
                source,
                r'(?s)map\s+\$platform_api_auth_candidate'
                r'\s+\$platform_api_authorization'
                r'\s*\{.*?'
                r'default\s+\$platform_api_auth_candidate;',
            )
            self.assertNotIn('missing-platform-api-token', source)
            self.assertIn('location = /_platform_api_auth {', source)
            self.assertIn(
                f"proxy_pass {expected['auth_upstream']};",
                source,
            )
            self.assertIn(
                'proxy_set_header Authorization $platform_api_authorization;',
                source,
            )
            self.assertEqual(
                len(expected['prefixes']),
                source.count('auth_request /_platform_api_auth;'),
            )
            for prefix in expected['prefixes']:
                block = location_block(source, prefix)
                self.assertIn(
                    'Authorization,X-Authorization,tenant-id',
                    block,
                    prefix,
                )
                self.assertIn('auth_request /_platform_api_auth;', block, prefix)
                self.assertRegex(block, r'proxy_pass\s+http://[^;]+;', prefix)

    def test_alternate_srs_publish_hook_calls_authorized_core_without_revalidation(self):
        video_root = Path(__file__).resolve().parent
        media_tree = ast.parse(
            (video_root / 'app' / 'blueprints' / 'media_hook.py').read_text(
                encoding='utf-8'))
        camera_tree = ast.parse(
            (video_root / 'app' / 'blueprints' / 'camera.py').read_text(
                encoding='utf-8'))
        media_functions = {
            node.name: node for node in media_tree.body if isinstance(node, ast.FunctionDef)
        }
        camera_functions = {
            node.name: node for node in camera_tree.body if isinstance(node, ast.FunctionDef)
        }
        self.assertIn('_handle_authorized_on_publish_callback', camera_functions)

        media_calls = [
            node.func.id
            for node in ast.walk(media_functions['srs_on_publish'])
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
        ]
        route_calls = [
            node.func.id
            for node in ast.walk(camera_functions['on_publish_callback'])
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
        ]
        core_calls = [
            node.func.id
            for node in ast.walk(camera_functions['_handle_authorized_on_publish_callback'])
            if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
        ]

        self.assertEqual(1, media_calls.count('_require_internal_hook_token'))
        self.assertEqual(1, media_calls.count('_handle_authorized_on_publish_callback'))
        self.assertNotIn('on_publish_callback', media_calls)
        self.assertEqual(1, route_calls.count('authorize_srs_hook_token'))
        self.assertEqual(1, route_calls.count('_handle_authorized_on_publish_callback'))
        self.assertNotIn('authorize_srs_hook_token', core_calls)

    def test_minio_compose_binds_loopback_and_requires_external_credentials(self):
        docker_root = Path(__file__).resolve().parents[1] / '.scripts' / 'docker'
        compose = (docker_root / 'docker-compose.yml').read_text(encoding='utf-8')
        example = (docker_root / 'env.example').read_text(encoding='utf-8')

        self.assertIn('127.0.0.1:9000:9000', compose)
        self.assertIn('127.0.0.1:9001:9001', compose)
        self.assertNotIn('0.0.0.0:9000:9000', compose)
        self.assertNotIn('0.0.0.0:9001:9001', compose)
        self.assertIn('MINIO_ROOT_USER=${MINIO_ROOT_USER:?', compose)
        self.assertIn('MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD:?', compose)
        self.assertNotIn('MINIO_ROOT_USER=minioadmin', compose)
        self.assertIn('MINIO_ROOT_USER=', example)
        self.assertIn('MINIO_ROOT_PASSWORD=', example)
        self.assertNotIn('MINIO_ROOT_USER=minioadmin', example)
        self.assertNotRegex(example, r'MINIO_ROOT_PASSWORD=\S+')

    def test_minio_clients_do_not_override_external_credentials_with_defaults(self):
        root = Path(__file__).resolve().parents[1]
        ai_compose = (root / 'AI' / 'docker-compose.yaml').read_text(encoding='utf-8')
        ai_example = (root / 'AI' / 'env.example').read_text(encoding='utf-8')
        device_compose = (root / 'DEVICE' / 'docker-compose.yml').read_text(
            encoding='utf-8')

        self.assertNotRegex(
            ai_compose, r'MINIO_ACCESS_KEY=\$\{MINIO_ACCESS_KEY:-[^}]+\}')
        self.assertNotRegex(
            ai_compose, r'MINIO_SECRET_KEY=\$\{MINIO_SECRET_KEY:-[^}]+\}')
        self.assertIn('env_file:\n      - .env.docker', ai_compose)
        self.assertIn('MINIO_ACCESS_KEY=', ai_example)
        self.assertIn('MINIO_SECRET_KEY=', ai_example)
        self.assertNotRegex(ai_example, r'MINIO_ACCESS_KEY=\S+')
        self.assertNotRegex(ai_example, r'MINIO_SECRET_KEY=\S+')
        self.assertIn('MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY:?', device_compose)
        self.assertIn('MINIO_SECRET_KEY=${MINIO_SECRET_KEY:?', device_compose)
        self.assertNotIn('MINIO_ACCESS_KEY=minioadmin', device_compose)

    def test_iot_sink_compose_defaults_postgres_user_and_requires_password(self):
        root = Path(__file__).resolve().parents[1]
        compose = (root / 'DEVICE' / 'docker-compose.yml').read_text(encoding='utf-8')
        iot_sink = compose.split('\n  iot-sink:\n', 1)[1].split('\n  iot-gb28181:\n', 1)[0]

        for datasource in ('MASTER', 'VIDEO', 'NODE'):
            self.assertIn(
                f'SPRING_DATASOURCE_DYNAMIC_DATASOURCE_{datasource}_USERNAME='
                '${POSTGRES_USER:-postgres}',
                iot_sink,
            )
            self.assertIn(
                f'SPRING_DATASOURCE_DYNAMIC_DATASOURCE_{datasource}_PASSWORD='
                '${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}',
                iot_sink,
            )

    def test_downloadable_models_default_to_external_model_mount(self):
        utils_root = Path(__file__).resolve().parent / 'app' / 'utils'
        for filename in ('face_model_paths.py', 'plate_model_paths.py'):
            source = (utils_root / filename).read_text(encoding='utf-8')
            self.assertIn("os.path.join(_VIDEO_ROOT, 'model'", source)
            self.assertNotRegex(source, r"os\.path\.join\(_VIDEO_ROOT, '(?:face|plate)_[^']+\.onnx'\)")

    def test_cache_flush_failures_are_isolated_by_tenant_identity_and_filter(self):
        from app.services import record_cache_flush_event_service as cache_events

        with tempfile.TemporaryDirectory() as event_dir, mock.patch.dict(
                os.environ, {'YFEIEYE_RECORD_CACHE_EVENT_DIR': event_dir}):
            cache_events = importlib.reload(cache_events)
            common = {
                'event_id': 'event-1',
                'device_id': 'camera-01',
                'space_id': 3,
                'file_path': '/data/playbacks/live/camera-01/clip.flv',
            }
            first = cache_events.record_cache_flush_failure(
                {**common, 'tenant_id': 7}, 'tenant seven failure')
            second = cache_events.record_cache_flush_failure(
                {**common, 'tenant_id': 8}, 'tenant eight failure')

            self.assertNotEqual(first['identity'], second['identity'])
            self.assertEqual(
                [7],
                [item['tenant_id'] for item in cache_events.list_record_cache_flush_failures(
                    tenant_id=7, space_id=3, device_id='camera-01')],
            )
            cache_events.resolve_record_cache_flush_failure(
                {**common, 'tenant_id': 7})
            self.assertEqual(
                [8],
                [item['tenant_id'] for item in cache_events.list_record_cache_flush_failures(
                    tenant_id=8, space_id=3, device_id='camera-01')],
            )
            with self.assertRaisesRegex(ValueError, 'tenant'):
                cache_events.record_cache_flush_failure(common, 'unscoped')

    def test_janitor_requeue_preserves_explicit_tenant_for_dvr_and_snapshot(self):
        from app.services import media_janitor_service as janitor

        published = []

        def build_dvr(data, device_id=None):
            return {**data, 'device_id': device_id}

        snap_upload = importlib.import_module('app.services.snap_upload_service')
        with mock.patch.object(janitor, 'is_kafka_upload_mode', return_value=True), \
                mock.patch.object(janitor, 'build_event_from_srs_hook', side_effect=build_dvr), \
                mock.patch.object(janitor, 'publish_dvr_event', side_effect=lambda event: published.append(event) or True), \
                mock.patch.object(snap_upload, 'build_snap_event', side_effect=lambda device, path, source, tenant_id: {
                    'device_id': device, 'file_path': path, 'source': source,
                    'tenant_id': tenant_id,
                }), \
                mock.patch.object(janitor, 'is_snap_kafka_mode', return_value=True), \
                mock.patch.object(janitor, 'publish_snap_event', side_effect=lambda event: published.append(event) or True):
            self.assertTrue(janitor.requeue_orphan_dvr({
                'tenant_id': 7,
                'device_id': 'camera-01',
                'file_path': '/data/playbacks/live/camera-01/clip.flv',
            }))
            self.assertTrue(janitor.requeue_orphan_snap({
                'tenant_id': 8,
                'device_id': 'camera-01',
                'file_path': '/data/snaps/camera-01/frame.jpg',
            }))

        self.assertEqual(['7', '8'], [str(event['tenant_id']) for event in published])

    def test_retention_policy_query_is_scoped_to_configured_dvr_tenant(self):
        from app.services import playback_disk_guard_service as guard
        import models
        from app.services import space_save_time_service

        class Query:
            def __init__(self):
                self.filter_by_calls = []

            def filter_by(self, **values):
                self.filter_by_calls.append(values)
                return self

            def filter(self, *_expressions):
                return self

            def all(self):
                return [types.SimpleNamespace(device_id='camera-01', save_time=24)]

        query = Query()
        record_space = types.SimpleNamespace(
            query=query,
            device_id=_Column('device_id'),
        )
        with mock.patch.object(models, 'RecordSpace', record_space), \
                mock.patch.object(
                    space_save_time_service,
                    'enrich_record_space_dict',
                    return_value={'effective_save_time': 24}):
            result = guard._resolve_device_playback_max_age_map(tenant_id=7)

        self.assertEqual([{'tenant_id': 7}], query.filter_by_calls)
        self.assertEqual({'camera-01': 24}, result)

    def test_metadata_deletes_require_tenant_and_space_scope(self):
        from app.services import space_file_metadata_service as metadata

        record_query = _DeleteQuery()
        snap_query = _DeleteQuery()
        playback_query = _DeleteQuery()
        record_model = types.SimpleNamespace(
            query=record_query,
            tenant_id=_Column('tenant_id'),
            space_id=_Column('space_id'),
            bucket_name=_Column('bucket_name'),
            object_name=_Column('object_name'),
        )
        snap_model = types.SimpleNamespace(
            query=snap_query,
            tenant_id=_Column('tenant_id'),
            space_id=_Column('space_id'),
            bucket_name=_Column('bucket_name'),
            object_name=_Column('object_name'),
        )
        playback_model = types.SimpleNamespace(
            query=playback_query,
            file_path=_Column('file_path'),
        )
        session = types.SimpleNamespace(commit=lambda: None)
        with mock.patch.object(metadata, 'RecordFile', record_model), \
                mock.patch.object(metadata, 'SnapImage', snap_model), \
                mock.patch.object(metadata, 'Playback', playback_model), \
                mock.patch.object(metadata.db, 'session', session):
            metadata.delete_record_files_metadata(
                'record-space', ['tenants/7/camera-01/clip.flv'],
                tenant_id=7, space_id=11)
            metadata.delete_snap_images_metadata(
                'snap-space', ['tenants/7/cameras/camera-01/frame.jpg'],
                tenant_id=7, space_id=12)

        self.assertIn(('eq', 'tenant_id', 7), record_query.filters)
        self.assertIn(('eq', 'space_id', 11), record_query.filters)
        self.assertIn(('eq', 'tenant_id', 7), snap_query.filters)
        self.assertIn(('eq', 'space_id', 12), snap_query.filters)


if __name__ == '__main__':
    unittest.main()
