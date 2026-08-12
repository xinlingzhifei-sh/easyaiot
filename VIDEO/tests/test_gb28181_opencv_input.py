import os
import sys
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.utils.gb28181_source import (
    _extract_stream_url_and_meta,
    prefer_h264_http_flv_for_opencv,
    prefer_hevc_http_ts_for_ffmpeg,
)
from app.utils.rtsp_stream_utils import use_ffmpeg_raw_capture_for_url


class Gb28181OpenCvInputTest(unittest.TestCase):
    def tearDown(self):
        os.environ.pop("AI_HTTP_FLV_FFMPEG_CAPTURE", None)
        os.environ.pop("GB28181_PLAY_PROTOCOL", None)

    def test_gb28181_http_flv_codec_metadata_is_not_rewritten(self):
        url = (
            "http://127.0.0.1:80/rtp/"
            "44010200493432381460_34020000001320000001.live.flv"
            "?originTypeStr=rtp_push&videoCodec=H265"
        )

        self.assertEqual(prefer_h264_http_flv_for_opencv(url), url)

    def test_gb28181_hevc_http_flv_uses_http_ts_for_ffmpeg(self):
        url = (
            "http://127.0.0.1:80/rtp/"
            "44010200493432381460_34020000001320000001.live.flv"
            "?originTypeStr=rtp_push&videoCodec=H265"
        )

        self.assertEqual(
            prefer_hevc_http_ts_for_ffmpeg(url),
            (
                "http://127.0.0.1:80/rtp/"
                "44010200493432381460_34020000001320000001.live.ts"
                "?originTypeStr=rtp_push&videoCodec=H265"
            ),
        )

    def test_gb28181_h264_http_flv_is_preserved(self):
        url = "http://127.0.0.1:80/rtp/camera.live.flv?videoCodec=H264"

        self.assertEqual(prefer_hevc_http_ts_for_ffmpeg(url), url)

    def test_http_flv_uses_ffmpeg_raw_capture_by_default(self):
        self.assertTrue(use_ffmpeg_raw_capture_for_url("http://127.0.0.1/live/test.flv"))
        self.assertTrue(use_ffmpeg_raw_capture_for_url("http://127.0.0.1/live/test.live.ts"))
        self.assertFalse(use_ffmpeg_raw_capture_for_url("rtsp://127.0.0.1/live/test"))

        os.environ["AI_HTTP_FLV_FFMPEG_CAPTURE"] = "false"
        self.assertFalse(use_ffmpeg_raw_capture_for_url("http://127.0.0.1/live/test.flv"))

    def test_ts_first_chooses_local_http_ts_for_h265(self):
        os.environ["GB28181_PLAY_PROTOCOL"] = "ts_first"
        stream_id = "44010200493432381460_34020000001320000001"
        payload = {
            "code": 0,
            "data": {
                "flv": (
                    f"http://eye.yfeiai.com:6080/rtp/{stream_id}.live.flv"
                    "?originTypeStr=rtp_push&videoCodec=H265"
                ),
                "ts": (
                    f"http://eye.yfeiai.com:6080/rtp/{stream_id}.live.ts"
                    "?originTypeStr=rtp_push&videoCodec=H265"
                ),
                "rtmp": (
                    f"rtmp://eye.yfeiai.com:1935/rtp/{stream_id}"
                    "?originTypeStr=rtp_push&videoCodec=H265"
                ),
            },
        }

        url, meta = _extract_stream_url_and_meta(payload)

        self.assertEqual(meta["branch"], "ts_first")
        self.assertEqual(
            url,
            (
                f"http://127.0.0.1:6080/rtp/{stream_id}.live.ts"
                "?originTypeStr=rtp_push&videoCodec=H265"
            ),
        )

    def test_fmp4_first_chooses_local_http_fmp4_for_h265(self):
        os.environ.pop("GB28181_PLAY_PROTOCOL", None)
        stream_id = "44010200493432381460_34020000001320000001"
        payload = {
            "code": 0,
            "data": {
                "fmp4": (
                    f"http://eye.yfeiai.com:6080/rtp/{stream_id}.live.mp4"
                    "?originTypeStr=rtp_push&videoCodec=H265"
                ),
                "ts": (
                    f"http://eye.yfeiai.com:6080/rtp/{stream_id}.live.ts"
                    "?originTypeStr=rtp_push&videoCodec=H265"
                ),
                "rtsp": (
                    f"rtsp://eye.yfeiai.com:554/rtp/{stream_id}"
                    "?originTypeStr=rtp_push&videoCodec=H265"
                ),
            },
        }

        url, meta = _extract_stream_url_and_meta(payload)

        self.assertEqual(meta["play_protocol"], "fmp4_first")
        self.assertEqual(meta["branch"], "fmp4_first")
        self.assertEqual(
            url,
            (
                f"http://127.0.0.1:6080/rtp/{stream_id}.live.mp4"
                "?originTypeStr=rtp_push&videoCodec=H265"
            ),
        )


if __name__ == "__main__":
    unittest.main()
