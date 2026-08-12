import sys
from pathlib import Path

import pytest
from flask import Flask


AI_ROOT = Path(__file__).resolve().parents[1]
if str(AI_ROOT) not in sys.path:
    sys.path.insert(0, str(AI_ROOT))


def test_minio_proxy_streams_object_download(monkeypatch):
    from app.blueprints import minio_proxy

    calls = []

    def fake_download_from_minio(bucket_name, object_name, destination_path):
        calls.append((bucket_name, object_name))
        Path(destination_path).write_bytes(b'png-bytes')
        return True, None

    monkeypatch.setattr(minio_proxy, '_download_from_minio', fake_download_from_minio)

    app = Flask(__name__)
    app.register_blueprint(minio_proxy.minio_proxy_bp)

    response = app.test_client().get(
        '/api/v1/buckets/models/objects/download?prefix=images/demo.png'
    )

    assert response.status_code == 200
    assert response.data == b'png-bytes'
    assert response.mimetype == 'image/png'
    assert calls == [('models', 'images/demo.png')]


@pytest.mark.parametrize('bucket_name', [
    'record-space',
    'snap-space',
    'camera-screenshots',
    'alert-images',
    'record-archive',
    'snap-archive',
    'review-evidence',
])
def test_minio_proxy_rejects_protected_media_buckets(monkeypatch, bucket_name):
    from app.blueprints import minio_proxy

    calls = []
    monkeypatch.setattr(
        minio_proxy,
        '_download_from_minio',
        lambda *args: calls.append(args),
    )

    app = Flask(__name__)
    app.register_blueprint(minio_proxy.minio_proxy_bp)

    response = app.test_client().get(
        f'/api/v1/buckets/{bucket_name}/objects/download?prefix=secret.bin'
    )

    assert response.status_code == 403
    assert response.get_json()['reason'] == 'protected_media_bucket'
    assert calls == []


def test_minio_proxy_uses_parsed_object_name_in_local_mode(monkeypatch):
    from app.blueprints import minio_proxy
    from app.utils import service_urls
    from app.services import local_storage_service

    calls = []
    monkeypatch.setattr(service_urls, 'minio_storage_enabled', lambda: False)

    def fake_read_local_object(bucket_name, object_name):
        calls.append((bucket_name, object_name))
        return b'model-bytes', 'application/octet-stream', None

    monkeypatch.setattr(
        local_storage_service, 'read_local_object', fake_read_local_object)

    app = Flask(__name__)
    app.register_blueprint(minio_proxy.minio_proxy_bp)

    response = app.test_client().get(
        '/api/v1/buckets/models/objects/download?prefix=weights%2Fdemo.pt'
    )

    assert response.status_code == 200
    assert response.data == b'model-bytes'
    assert calls == [('models', 'weights/demo.pt')]


@pytest.mark.parametrize('prefix', ['../secret', 'a/../../secret', '%2Fabsolute'])
def test_minio_proxy_rejects_unsafe_object_paths(prefix):
    from app.blueprints import minio_proxy

    app = Flask(__name__)
    app.register_blueprint(minio_proxy.minio_proxy_bp)

    response = app.test_client().get(
        f'/api/v1/buckets/models/objects/download?prefix={prefix}'
    )

    assert response.status_code == 400
    assert response.get_json()['reason'] == 'invalid_object_path'
