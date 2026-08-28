import unittest
import tempfile
from unittest.mock import Mock, patch

from stream_forward_executor import StreamForwardExecutor


class StreamForwardExecutorTest(unittest.TestCase):

    @patch("stream_forward_executor.subprocess.Popen")
    def test_deploy_starts_ffmpeg_rtsp_to_rtmp_process(self, popen):
        process = Mock()
        process.pid = 4321
        popen.return_value = process
        executor = StreamForwardExecutor()

        with tempfile.TemporaryDirectory() as log_dir:
            result = executor.deploy({
                "deviceId": "cam-001",
                "rtspUrl": "rtsp://user:pass@10.0.0.8/live",
                "rtmpPushUrl": "rtmp://media.example.com/live/cam-001",
                "transport": "tcp",
                "logDir": log_dir,
            })

        self.assertEqual(4321, result["pid"])
        cmd = popen.call_args.args[0]
        self.assertIn("-rtsp_transport", cmd)
        self.assertIn("tcp", cmd)
        self.assertIn("rtmp://media.example.com/live/cam-001", cmd)

    def test_deploy_requires_rtsp_and_rtmp_urls(self):
        executor = StreamForwardExecutor()

        with self.assertRaises(ValueError):
            executor.deploy({"deviceId": "cam-001", "rtspUrl": "", "rtmpPushUrl": ""})


if __name__ == "__main__":
    unittest.main()
