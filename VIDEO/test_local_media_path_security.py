"""Local media canonical-path security regression tests."""
import os
import tempfile
import unittest
from unittest.mock import patch

from app.services.local_media_path_service import (
    LocalMediaPathError,
    resolve_allowed_local_media_file,
)


class TestLocalMediaPathSecurity(unittest.TestCase):
    def test_regular_file_inside_explicit_root_is_accepted(self):
        with tempfile.TemporaryDirectory() as root:
            path = os.path.join(root, 'clip.mp4')
            with open(path, 'wb') as media_file:
                media_file.write(b'video')
            with patch.dict(os.environ, {'YFEIEYE_LOCAL_MEDIA_ROOTS': root}, clear=False):
                self.assertEqual(os.path.realpath(path), resolve_allowed_local_media_file(path))

    def test_file_uri_outside_path_and_directory_are_rejected(self):
        with tempfile.TemporaryDirectory() as root, tempfile.TemporaryDirectory() as outside:
            outside_file = os.path.join(outside, 'outside.mp4')
            with open(outside_file, 'wb') as media_file:
                media_file.write(b'outside')
            with patch.dict(os.environ, {'YFEIEYE_LOCAL_MEDIA_ROOTS': root}, clear=False):
                expectations = (
                    ('file://' + outside_file, 'local_media_file_uri_not_allowed'),
                    (outside_file, 'local_media_path_outside_allowed_roots'),
                    (root, 'local_media_regular_file_required'),
                )
                for candidate, reason in expectations:
                    with self.subTest(candidate=candidate):
                        with self.assertRaises(LocalMediaPathError) as raised:
                            resolve_allowed_local_media_file(candidate)
                        self.assertEqual(reason, raised.exception.reason)

    def test_symlink_file_is_rejected_even_when_link_is_inside_root(self):
        with tempfile.TemporaryDirectory() as root:
            target = os.path.join(root, 'target.mp4')
            link = os.path.join(root, 'linked.mp4')
            with open(target, 'wb') as media_file:
                media_file.write(b'video')
            try:
                os.symlink(target, link)
            except (OSError, NotImplementedError):
                original_realpath = os.path.realpath

                def simulated_realpath(path):
                    if os.path.normcase(os.path.abspath(path)) == os.path.normcase(link):
                        return target
                    return original_realpath(path)

                with patch.dict(os.environ, {'YFEIEYE_LOCAL_MEDIA_ROOTS': root}, clear=False), \
                        patch(
                            'app.services.local_media_path_service.os.path.realpath',
                            side_effect=simulated_realpath,
                        ):
                    with self.assertRaises(LocalMediaPathError) as raised:
                        resolve_allowed_local_media_file(link)
                    self.assertEqual(
                        'local_media_symlink_not_allowed', raised.exception.reason)
                return
            with patch.dict(os.environ, {'YFEIEYE_LOCAL_MEDIA_ROOTS': root}, clear=False):
                with self.assertRaises(LocalMediaPathError) as raised:
                    resolve_allowed_local_media_file(link)
                self.assertEqual('local_media_symlink_not_allowed', raised.exception.reason)

    def test_missing_allowed_roots_fails_closed(self):
        with tempfile.NamedTemporaryFile() as media_file:
            with patch.dict(os.environ, {}, clear=False):
                os.environ.pop('YFEIEYE_LOCAL_MEDIA_ROOTS', None)
                os.environ.pop('YFEIEYE_RECORD_EXPORT_STORE_DIR', None)
                with self.assertRaises(LocalMediaPathError) as raised:
                    resolve_allowed_local_media_file(media_file.name)
                self.assertEqual(
                    'local_media_allowed_roots_not_configured',
                    raised.exception.reason,
                )


if __name__ == '__main__':
    unittest.main()
