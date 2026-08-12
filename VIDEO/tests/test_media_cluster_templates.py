import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


class MediaClusterTemplatesTest(unittest.TestCase):

    def test_zlm_template_uses_zlm_hook_routes(self):
        template = (ROOT / ".scripts" / "media-cluster" / "zlm" / "config.ini.template").read_text(
            encoding="utf-8",
        )

        self.assertIn("/video/media/hook/zlm/on_publish", template)
        self.assertIn("/video/media/hook/zlm/on_stream_changed", template)
        self.assertNotIn("on_publish=http://${MEDIA_HOOK_HOST}:${MEDIA_HOOK_PORT}${MEDIA_HOOK_PATH_PREFIX}/video/media/hook/srs/on_publish", template)
        self.assertNotIn("on_stream_changed=http://${MEDIA_HOOK_HOST}:${MEDIA_HOOK_PORT}${MEDIA_HOOK_PATH_PREFIX}/video/media/hook/srs/on_unpublish", template)


if __name__ == "__main__":
    unittest.main()
