import os
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

from media_manager import MediaStackManager
from mqtt_manager import MqttStackManager
from workload_manager import WorkloadManager


class WorkloadSecurityTest(unittest.TestCase):

    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.ai_root = Path(self.temp_dir.name) / 'AI'
        self.script = self.ai_root / 'services' / 'ai_service' / 'run_deploy.py'
        self.script.parent.mkdir(parents=True)
        self.script.write_text('print("ok")\n', encoding='utf-8')
        self.logs = self.ai_root / 'logs' / 'service-1'

    def tearDown(self):
        self.temp_dir.cleanup()

    def _spec(self):
        return {
            'workloadType': 'ai_service',
            'workloadId': '1',
            'command': [sys.executable, str(self.script)],
            'workDir': str(self.script.parent),
            'logDir': str(self.logs),
            'env': {'MODEL_PATH': 'detector.onnx'},
        }

    def test_arbitrary_shell_command_is_rejected(self):
        spec = self._spec()
        spec['command'] = ['/bin/sh', '-c', 'touch /tmp/owned']

        with (
            patch.dict(os.environ, {'AI_ROOT': str(self.ai_root)}),
            patch('workload_manager.subprocess.Popen') as popen,
        ):
            with self.assertRaises(ValueError):
                WorkloadManager().deploy(spec)

        popen.assert_not_called()

    def test_dangerous_loader_environment_is_rejected(self):
        spec = self._spec()
        spec['env']['LD_PRELOAD'] = '/tmp/owned.so'

        with patch.dict(os.environ, {'AI_ROOT': str(self.ai_root)}):
            with self.assertRaisesRegex(ValueError, 'LD_PRELOAD'):
                WorkloadManager().deploy(spec)

    def test_encoded_pickle_model_is_rejected_after_resolution(self):
        spec = self._spec()
        spec['env'].update({
            'MODEL_ID': '1',
            'MODEL_PATH': '/api/v1/buckets/ai/models?prefix=attacker%2Ept',
        })
        fake_process = Mock(pid=4321)

        with (
            patch.dict(os.environ, {'AI_ROOT': str(self.ai_root)}),
            patch(
                'workload_manager._ensure_model_local',
                return_value=str(self.ai_root / 'data' / 'models' / '1' / 'attacker.pt'),
            ),
            patch('workload_manager.subprocess.Popen', return_value=fake_process) as popen,
        ):
            with self.assertRaisesRegex(ValueError, 'ONNX'):
                WorkloadManager().deploy(spec)

        popen.assert_not_called()

    def test_non_numeric_model_id_cannot_escape_model_cache(self):
        spec = self._spec()
        spec['env']['MODEL_ID'] = '../../services/ai_service'
        fake_process = Mock(pid=4321)

        with (
            patch.dict(os.environ, {'AI_ROOT': str(self.ai_root)}),
            patch(
                'workload_manager._ensure_model_local',
                return_value=str(self.ai_root / 'services' / 'ai_service' / 'run_deploy.py'),
            ) as resolver,
            patch('workload_manager.subprocess.Popen', return_value=fake_process) as popen,
        ):
            with self.assertRaisesRegex(ValueError, 'MODEL_ID'):
                WorkloadManager().deploy(spec)

        resolver.assert_not_called()
        popen.assert_not_called()

    def test_media_stack_rejects_process_loader_environment(self):
        with patch('media_manager.subprocess.run') as run:
            with self.assertRaisesRegex(ValueError, 'BASH_ENV'):
                MediaStackManager().deploy({
                    'stackType': 'zlm',
                    'nodeId': '1',
                    'env': {'BASH_ENV': '/tmp/attacker.sh'},
                })

        run.assert_not_called()

    def test_mqtt_stack_rejects_process_loader_environment(self):
        with patch('mqtt_manager.subprocess.run') as run:
            with self.assertRaisesRegex(ValueError, 'LD_PRELOAD'):
                MqttStackManager().deploy({
                    'stackType': 'emqx',
                    'nodeId': '1',
                    'env': {'LD_PRELOAD': '/tmp/attacker.so'},
                })

        run.assert_not_called()

    def test_stack_managers_accept_declared_environment_contract(self):
        media_root = Path(self.temp_dir.name) / 'media'
        media_root.mkdir()
        media_compose = media_root / 'docker-compose.media-node.yml'
        media_compose.write_text('services: {}\n', encoding='utf-8')
        (media_root / 'install_media_stack.sh').write_text('#!/bin/sh\n', encoding='utf-8')

        mqtt_root = Path(self.temp_dir.name) / 'mqtt'
        mqtt_root.mkdir()
        mqtt_compose = mqtt_root / 'docker-compose.mqtt-node.yml'
        mqtt_compose.write_text('services: {}\n', encoding='utf-8')
        (mqtt_root / 'install_mqtt_stack.sh').write_text('#!/bin/sh\n', encoding='utf-8')

        completed = Mock(returncode=0, stdout='', stderr='')
        with (
            patch('media_manager.MEDIA_CLUSTER_ROOT', str(media_root)),
            patch('media_manager.COMPOSE_FILE', str(media_compose)),
            patch('media_manager.subprocess.run', return_value=completed),
        ):
            media = MediaStackManager().deploy({
                'stackType': 'zlm',
                'nodeId': 'node-1',
                'env': {
                    'MEDIA_CLUSTER_ROOT': str(media_root),
                    'ZLM_HTTP_PORT': '6080',
                },
            })

        with (
            patch('mqtt_manager.MQTT_CLUSTER_ROOT', str(mqtt_root)),
            patch('mqtt_manager.COMPOSE_FILE', str(mqtt_compose)),
            patch('mqtt_manager.subprocess.run', return_value=completed),
        ):
            mqtt = MqttStackManager().deploy({
                'stackType': 'emqx',
                'nodeId': 'node-1',
                'env': {
                    'MQTT_CLUSTER_ROOT': str(mqtt_root),
                    'MQTT_TCP_PORT': '1883',
                },
            })

        self.assertEqual('running', media['status'])
        self.assertEqual('running', mqtt['status'])

    def test_fixed_worker_entrypoint_can_start(self):
        fake_process = Mock(pid=4321)
        spec = self._spec()

        with (
            patch.dict(os.environ, {'AI_ROOT': str(self.ai_root)}),
            patch('workload_manager.subprocess.Popen', return_value=fake_process) as popen,
        ):
            result = WorkloadManager().deploy(spec)

        self.assertEqual(4321, result['pid'])
        self.assertEqual(
            [os.path.realpath(sys.executable), os.path.realpath(self.script)],
            popen.call_args.args[0],
        )


if __name__ == '__main__':
    unittest.main()
