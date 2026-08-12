"""Least-privilege environment tests for local VIDEO child processes."""
import importlib
import os
import sys
import tempfile
import types
import unittest
from unittest.mock import patch


_MEDIA_AUTH_ENV = {
    'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS': '{"iot-system":"shared-keyring-secret"}',
    'YFEIEYE_MEDIA_SERVICE_POLICIES': '{"iot-system":{"actions":["download"]}}',
    'YFEIEYE_MEDIA_SERVICE_IDS': 'iot-system,video-algorithm',
    'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET': 'legacy-shared-secret',
    'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS': 'download,alert_ingest',
    'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS': 'camera-01',
}


class _FakeProcess:
    pid = 3210

    def poll(self):
        return None

    def terminate(self):
        return None


class TestSubprocessEnvironment(unittest.TestCase):
    def setUp(self):
        self._previous_models = sys.modules.get('models')
        sys.modules['models'] = types.SimpleNamespace(
            AlgorithmTask=object,
            StreamForwardTask=object,
            db=types.SimpleNamespace(),
        )

    def tearDown(self):
        for name in (
            'app.services.post_process_launcher_service',
            'app.services.stream_forward_launcher_service',
            'app.services.stream_forward_daemon',
        ):
            sys.modules.pop(name, None)
        if self._previous_models is None:
            sys.modules.pop('models', None)
        else:
            sys.modules['models'] = self._previous_models

    def test_unprivileged_child_environment_removes_media_service_authority(self):
        from app.utils.video_env import (
            build_unprivileged_process_env,
        )

        with patch.dict(os.environ, {
            **_MEDIA_AUTH_ENV,
            'PATH': os.environ.get('PATH', ''),
            'VIDEO_ENV': 'production',
        }, clear=False):
            child_env = build_unprivileged_process_env({'CHILD_ROLE': 'worker'})

        self.assertEqual('worker', child_env['CHILD_ROLE'])
        self.assertEqual('production', child_env['VIDEO_ENV'])
        self.assertEqual(
            '1', child_env['YFEIEYE_UNPRIVILEGED_CHILD_PROCESS'])
        for name, value in _MEDIA_AUTH_ENV.items():
            self.assertNotIn(name, child_env)
            self.assertNotIn(value, child_env.values())

    def test_unprivileged_child_cannot_reload_media_authority_from_dotenv(self):
        from app.utils import video_env

        with tempfile.TemporaryDirectory() as video_root:
            env_path = os.path.join(video_root, '.env')
            with open(env_path, 'w', encoding='utf-8') as env_file:
                for name, value in _MEDIA_AUTH_ENV.items():
                    env_file.write(f'{name}={value}\n')
            with patch.object(
                    video_env, 'video_root_dir', return_value=video_root), patch.dict(
                    os.environ, {
                        'YFEIEYE_UNPRIVILEGED_CHILD_PROCESS': '1',
                    }, clear=False):
                for name in _MEDIA_AUTH_ENV:
                    os.environ.pop(name, None)
                video_env.load_video_env(override=True)
                for name in _MEDIA_AUTH_ENV:
                    self.assertNotIn(name, os.environ)

    def test_post_process_local_worker_has_video_root_and_no_media_service_authority(self):
        sys.modules.pop('app.services.post_process_launcher_service', None)
        launcher = importlib.import_module(
            'app.services.post_process_launcher_service')

        expected_root = os.path.abspath(os.path.join(
            os.path.dirname(launcher.__file__), '..', '..'))
        self.assertEqual(expected_root, launcher._get_video_root())

        with tempfile.TemporaryDirectory() as video_root, patch.dict(
                os.environ, _MEDIA_AUTH_ENV, clear=False), patch.object(
                launcher, '_get_video_root', return_value=video_root), patch.object(
                launcher.subprocess, 'Popen', return_value=_FakeProcess()) as popen:
            launcher._local_workers.clear()
            launcher._deploy_worker_local(types.SimpleNamespace(id=41), 0)

        child_env = popen.call_args.kwargs['env']
        self.assertEqual(video_root, child_env['VIDEO_ROOT'])
        for name in _MEDIA_AUTH_ENV:
            self.assertNotIn(name, child_env)

    def test_stream_forward_local_worker_does_not_inherit_media_service_authority(self):
        daemon_module = types.ModuleType('app.services.stream_forward_daemon')
        daemon_module.StreamForwardDaemon = object
        sys.modules['app.services.stream_forward_daemon'] = daemon_module
        sys.modules.pop('app.services.stream_forward_launcher_service', None)
        launcher = importlib.import_module(
            'app.services.stream_forward_launcher_service')

        camera_service = types.ModuleType('app.services.camera_service')
        camera_service._get_host_ip_for_stream_urls = lambda: '127.0.0.1'
        sync_service = types.ModuleType('app.services.stream_url_sync_service')
        sync_service.sync_devices_for_deployment = lambda *_args, **_kwargs: None

        with tempfile.TemporaryDirectory() as video_root, patch.dict(
                os.environ, _MEDIA_AUTH_ENV, clear=False), patch.dict(
                sys.modules, {
                    'app.services.camera_service': camera_service,
                    'app.services.stream_url_sync_service': sync_service,
                }), patch.object(
                launcher, '_get_video_root', return_value=video_root), patch.object(
                launcher, '_stop_local_shard'), patch.object(
                launcher, '_build_stream_forward_deploy_env',
                return_value={'TASK_ID': '51'}), patch.object(
                launcher.subprocess, 'Popen', return_value=_FakeProcess()) as popen:
            launcher._local_shard_processes.clear()
            launcher._deploy_shard_locally(
                51, types.SimpleNamespace(id=51), 0, ['camera-01'])

        child_env = popen.call_args.kwargs['env']
        self.assertEqual(video_root, child_env['VIDEO_ROOT'])
        for name in _MEDIA_AUTH_ENV:
            self.assertNotIn(name, child_env)


if __name__ == '__main__':
    unittest.main()
