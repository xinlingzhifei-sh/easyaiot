import threading
import unittest
from unittest.mock import patch

from app.utils import plate_model_download


class PlateModelDownloadTest(unittest.TestCase):
    def setUp(self):
        plate_model_download._state.update(
            status="idle",
            stage="idle",
            progress=0,
            error=None,
        )

    def test_existing_model_download_status_does_not_deadlock(self):
        result = {}

        def start_download():
            result.update(plate_model_download.start_plate_model_download())

        with patch.object(plate_model_download, "_model_ready", return_value=True):
            thread = threading.Thread(target=start_download, daemon=True)
            thread.start()
            thread.join(timeout=0.5)

        self.assertFalse(thread.is_alive(), "模型已存在时查询下载状态发生死锁")
        self.assertFalse(result["started"])
        self.assertTrue(result["exists"])
        self.assertEqual(result["stage"], "done")

    def test_new_model_download_status_does_not_deadlock(self):
        with (
            patch.object(plate_model_download, "_model_ready", return_value=False),
            patch.object(plate_model_download.threading, "Thread") as thread_class,
        ):
            result = plate_model_download.start_plate_model_download()

        self.assertTrue(result["started"])
        self.assertFalse(result["exists"])
        self.assertTrue(result["downloading"])
        self.assertEqual(result["stage"], "downloading")
        thread_class.return_value.start.assert_called_once_with()


if __name__ == "__main__":
    unittest.main()
