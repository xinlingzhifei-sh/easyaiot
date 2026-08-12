import os
from unittest import TestCase
from unittest.mock import Mock, patch

from app.utils import rtsp_stream_utils
from app.utils.rtsp_stream_utils import FfmpegRawVideoCapture, ffmpeg_raw_capture_dimensions


class FfmpegRawCaptureDimensionsTest(TestCase):
    @patch.dict(
        os.environ,
        {
            "AI_TARGET_WIDTH": "640",
            "AI_TARGET_HEIGHT": "360",
        },
        clear=True,
    )
    def test_uses_ai_output_dimensions_by_default(self):
        self.assertEqual(ffmpeg_raw_capture_dimensions(), (640, 360))

    @patch.dict(
        os.environ,
        {
            "AI_FFMPEG_INPUT_WIDTH": "960",
            "AI_FFMPEG_INPUT_HEIGHT": "540",
            "AI_TARGET_WIDTH": "640",
            "AI_TARGET_HEIGHT": "360",
        },
        clear=True,
    )
    def test_explicit_input_dimensions_take_precedence(self):
        self.assertEqual(ffmpeg_raw_capture_dimensions(), (960, 540))


class FfmpegRawCaptureReadTest(TestCase):
    def _capture(self):
        capture = FfmpegRawVideoCapture.__new__(FfmpegRawVideoCapture)
        capture.width = 2
        capture.height = 1
        capture.frame_size = 6
        capture.read_timeout_sec = 0.01
        stdout = Mock()
        stdout.fileno.return_value = 42
        process = Mock()
        process.stdout = stdout
        process.poll.return_value = None
        capture.process = process
        return capture, stdout

    def test_releases_capture_when_posix_pipe_produces_no_frame(self):
        capture, _ = self._capture()
        with patch.object(
            rtsp_stream_utils, "_POSIX_PIPE_SELECT_SUPPORTED", True
        ), patch.object(
            rtsp_stream_utils.select, "select", return_value=([], [], [])
        ), patch.object(capture, "release") as release:
            opened, frame = capture.read()

        self.assertFalse(opened)
        self.assertIsNone(frame)
        release.assert_called_once_with()

    def test_collects_partial_posix_pipe_reads_into_one_frame(self):
        capture, stdout = self._capture()
        with patch.object(
            rtsp_stream_utils, "_POSIX_PIPE_SELECT_SUPPORTED", True
        ), patch.object(
            rtsp_stream_utils.select,
            "select",
            side_effect=[
                ([stdout], [], []),
                ([stdout], [], []),
            ],
        ), patch.object(
            rtsp_stream_utils.os,
            "read",
            side_effect=[b"\x00\x01", b"\x02\x03\x04\x05"],
        ):
            opened, frame = capture.read()

        self.assertTrue(opened)
        self.assertEqual(frame.shape, (1, 2, 3))
        self.assertEqual(frame.tolist(), [[[0, 1, 2], [3, 4, 5]]])
