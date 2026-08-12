"""算法任务子进程心跳地址回归测试。"""
import os
import unittest
from unittest.mock import patch

from app.services.algorithm_task_daemon import _resolve_algorithm_heartbeat_url


class TestAlgorithmTaskHeartbeatUrl(unittest.TestCase):
    def test_heartbeat_uses_the_video_service_bind_host(self):
        with patch.dict(os.environ, {
            'FLASK_RUN_HOST': '172.17.0.1',
            'FLASK_RUN_PORT': '6000',
        }, clear=True):
            self.assertEqual(
                _resolve_algorithm_heartbeat_url('realtime'),
                'http://172.17.0.1:6000/video/algorithm/heartbeat/realtime',
            )
            self.assertEqual(
                _resolve_algorithm_heartbeat_url('patrol'),
                'http://172.17.0.1:6000/video/algorithm/heartbeat/patrol',
            )


if __name__ == '__main__':
    unittest.main()
