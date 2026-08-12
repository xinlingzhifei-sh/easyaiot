"""MinIO download proxy response header tests."""
import unittest
from pathlib import Path
from unittest.mock import ANY, patch
from urllib.parse import unquote

from flask import Flask
from werkzeug.test import Client
from werkzeug.wrappers import Response

from app.blueprints.minio_proxy import minio_proxy_bp


class TestMinioProxy(unittest.TestCase):
    def setUp(self):
        self.app = Flask(__name__)
        self.app.register_blueprint(minio_proxy_bp)
        self.client = Client(self.app, Response)

    def assert_unicode_filename(self, response, disposition, filename):
        header = response.headers['Content-Disposition']
        header.encode('latin-1', 'strict')
        self.assertTrue(header.startswith(f'{disposition}; '), header)
        self.assertIn('filename="download.jpg"', header)

        marker = "filename*=UTF-8''"
        self.assertIn(marker, header)
        encoded_filename = header.split(marker, 1)[1]
        self.assertEqual(unquote(encoded_filename), filename)

    def test_downloads_minio_object_with_chinese_filename(self):
        content = b'jpeg-data'
        object_key = 'nested/path/中文封面.jpg'

        def download_to_file(bucket_name, object_name, destination_path):
            Path(destination_path).write_bytes(content)
            return True, None

        with patch(
            'app.utils.service_urls.minio_storage_enabled', return_value=True
        ), patch(
            'app.blueprints.minio_proxy._download_from_minio',
            side_effect=download_to_file,
        ) as download_mock:
            response = self.client.get(
                '/api/v1/buckets/models/objects/download',
                query_string={'prefix': object_key},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, content)
        self.assertEqual(response.content_type, 'image/jpeg')
        self.assert_unicode_filename(response, 'attachment', '中文封面.jpg')
        download_mock.assert_called_once_with('models', object_key, ANY)

    def test_downloads_local_object_with_chinese_filename(self):
        content = b'local-image'
        with patch(
            'app.utils.service_urls.minio_storage_enabled', return_value=False
        ), patch(
            'app.services.local_storage_service.read_local_object',
            return_value=(content, 'image/jpeg', None),
        ):
            response = self.client.get(
                '/api/v1/buckets/models/objects/download',
                query_string={'prefix': '中文封面.jpg'},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.data, content)
        self.assertEqual(response.content_type, 'image/jpeg')
        self.assert_unicode_filename(response, 'inline', '中文封面.jpg')

    def test_preserves_ascii_filename(self):
        with patch(
            'app.utils.service_urls.minio_storage_enabled', return_value=False
        ), patch(
            'app.services.local_storage_service.read_local_object',
            return_value=(b'image', 'image/jpeg', None),
        ):
            response = self.client.get(
                '/api/v1/buckets/models/objects/download',
                query_string={'prefix': 'cover.jpg'},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.headers['Content-Disposition'],
            'inline; filename="cover.jpg"',
        )

    def test_rejects_protected_media_bucket(self):
        response = self.client.get(
            '/api/v1/buckets/snap-space/objects/download',
            query_string={'prefix': '中文封面.jpg'},
        )

        self.assertEqual(response.status_code, 403)
        self.assertEqual(response.get_json()['reason'], 'protected_media_bucket')


if __name__ == '__main__':
    unittest.main()
