import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from edge import config, workload_runner


class EdgeSecurityTest(unittest.TestCase):

    def test_edge_node_id_is_persisted_before_state_write(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            state_file = root / 'state' / 'edge.state.json'
            env_file = root / 'edge.env'
            with (
                patch.object(config, 'STATE_DIR', state_file.parent),
                patch.object(config, 'STATE_FILE', state_file),
                patch.object(config, 'ENV_FILE', env_file),
            ):
                config.merge_runtime_into_state({'edgeNodeId': 42})

            persisted = json.loads(state_file.read_text(encoding='utf-8'))
            self.assertEqual(42, persisted['edgeNodeId'])

    def test_missing_runtime_never_falls_back_to_mqtt_command(self):
        payload = {
            'taskId': 7,
            'taskType': 'realtime',
            'deploy': {
                'command': ['/bin/sh', '-c', 'touch /tmp/owned'],
                'workDir': '/tmp',
            },
        }
        with tempfile.TemporaryDirectory() as temp_dir:
            with (
                patch.object(workload_runner, 'RUNTIME_ROOT', Path(temp_dir)),
                patch.object(workload_runner, '_load_proc_map', return_value={}),
                patch.object(workload_runner.subprocess, 'Popen') as popen,
            ):
                with self.assertRaises(FileNotFoundError):
                    workload_runner.start_task(payload, {})

            popen.assert_not_called()

    def test_unknown_task_type_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'taskType'):
            workload_runner._deploy_script('unknown')

    def test_dangerous_python_environment_keys_are_blocked(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            work_dir = Path(temp_dir)
            fake_process = unittest.mock.Mock(pid=1234)
            with (
                patch.object(workload_runner.subprocess, 'Popen', return_value=fake_process) as popen,
                patch.object(workload_runner, '_remember_pid'),
            ):
                workload_runner._spawn(
                    9,
                    ['python', 'run_deploy.py'],
                    str(work_dir),
                    {},
                    {
                        'PYTHONPATH': '/tmp/owned',
                        'LD_AUDIT': '/tmp/owned.so',
                        'DYLD_INSERT_LIBRARIES': '/tmp/owned.dylib',
                        'CAMERA_ID': 'camera-1',
                    },
                )

            child_env = popen.call_args.kwargs['env']
            self.assertNotEqual('/tmp/owned', child_env.get('PYTHONPATH'))
            self.assertNotEqual('/tmp/owned.so', child_env.get('LD_AUDIT'))
            self.assertNotEqual(
                '/tmp/owned.dylib',
                child_env.get('DYLD_INSERT_LIBRARIES'),
            )
            self.assertEqual('camera-1', child_env['CAMERA_ID'])
            workload_runner._procs.pop(9, None)


if __name__ == '__main__':
    unittest.main()
