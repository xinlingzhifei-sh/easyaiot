import logging
import mimetypes
import os
import posixpath
import re
import tempfile
from urllib.parse import quote, unquote

from flask import Blueprint, Response, after_this_request, jsonify, request, send_file
from minio.error import S3Error
from werkzeug.utils import secure_filename

from app.services.minio_service import ModelService, parse_minio_download_url

minio_proxy_bp = Blueprint('minio_proxy', __name__)
logger = logging.getLogger(__name__)

_PROTECTED_MEDIA_BUCKETS = frozenset({
    'record-space',
    'snap-space',
    'camera-screenshots',
    'alert-images',
    'record-archive',
    'snap-archive',
    'review-evidence',
})


def _build_content_disposition(disposition: str, filename: str) -> str:
    fallback = secure_filename(filename)
    extension = os.path.splitext(filename)[1]
    if not (
        extension.startswith('.')
        and len(extension) <= 16
        and extension[1:].isascii()
        and extension[1:].isalnum()
    ):
        extension = ''
    if not fallback or fallback == extension.lstrip('.'):
        fallback = f'download{extension}'

    if filename.isascii() and fallback == filename:
        return f'{disposition}; filename="{fallback}"'
    encoded_filename = quote(filename, safe='')
    return (
        f'{disposition}; filename="{fallback}"; '
        f"filename*=UTF-8''{encoded_filename}"
    )


def _download_from_minio(bucket_name, object_name, destination_path):
    return ModelService.download_from_minio(bucket_name, object_name, destination_path)


def _valid_object_name(value):
    if not value or value.startswith(('/', '\\')) or '\x00' in value:
        return False
    normalized = value.replace('\\', '/')
    return all(part not in ('', '.', '..') for part in normalized.split('/'))


@minio_proxy_bp.route('/api/v1/buckets/<bucket_name>/objects/download', methods=['GET', 'HEAD'])
def download_bucket_object(bucket_name):
    normalized_bucket = (bucket_name or '').strip().lower()
    if normalized_bucket in _PROTECTED_MEDIA_BUCKETS:
        return jsonify({
            'code': 403,
            'msg': 'protected media must use an authorized VIDEO endpoint',
            'reason': 'protected_media_bucket',
        }), 403
    if not re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,62}', normalized_bucket):
        return jsonify({
            'code': 400,
            'msg': 'invalid MinIO bucket name',
            'reason': 'invalid_bucket_name',
        }), 400

    prefix = request.args.get('prefix', '').strip()
    if not prefix:
        return jsonify({'code': 400, 'msg': 'missing MinIO object path'}), 400

    object_name = unquote(prefix)
    _, parsed_object_name = parse_minio_download_url(
        f'/api/v1/buckets/{normalized_bucket}/objects/download?prefix={prefix}'
    )
    if parsed_object_name:
        object_name = parsed_object_name
    if not _valid_object_name(object_name):
        return jsonify({
            'code': 400,
            'msg': 'invalid MinIO object path',
            'reason': 'invalid_object_path',
        }), 400

    from app.utils.service_urls import minio_storage_enabled
    from app.services.local_storage_service import read_local_object

    if not minio_storage_enabled():
        content, content_type, err = read_local_object(
            normalized_bucket, object_name)
        if err or content is None:
            logger.warning(
                'Local object not found: %s/%s', normalized_bucket, object_name)
            return Response(
                err or 'Local object not found', status=404, mimetype='text/plain')
        filename = os.path.basename(object_name) or 'download'
        response = Response(
            content, mimetype=content_type or 'application/octet-stream')
        response.headers['Content-Disposition'] = _build_content_disposition('inline', filename)
        response.headers['Content-Length'] = str(len(content))
        return response

    suffix = os.path.splitext(object_name)[1]
    fd, temp_path = tempfile.mkstemp(suffix=suffix)
    os.close(fd)
    try:
        success, error_message = _download_from_minio(
            normalized_bucket, object_name, temp_path)
        if not success:
            try:
                os.remove(temp_path)
            except OSError:
                pass
            return jsonify({
                'code': 404,
                'msg': error_message or 'MinIO object not found',
            }), 404

        @after_this_request
        def cleanup_temp_file(response):
            try:
                os.remove(temp_path)
            except OSError:
                pass
            return response

        mimetype = mimetypes.guess_type(object_name)[0] or 'application/octet-stream'
        filename = posixpath.basename(object_name) or 'download'
        response = send_file(
            temp_path,
            mimetype=mimetype,
            download_name=filename,
            as_attachment=True,
            conditional=True,
        )
        response.headers['Content-Disposition'] = _build_content_disposition(
            'attachment', filename)
        return response
    except S3Error as exc:
        logger.error(
            'MinIO download failed: %s/%s, %s',
            normalized_bucket, object_name, exc)
        try:
            os.remove(temp_path)
        except OSError:
            pass
        return Response(
            f'MinIO download failed: {exc}', status=500, mimetype='text/plain')
    except Exception as exc:
        logger.error(
            'Proxy download failed: %s/%s, %s',
            normalized_bucket, object_name, exc, exc_info=True)
        try:
            os.remove(temp_path)
        except OSError:
            pass
        return Response(
            f'Download failed: {exc}', status=500, mimetype='text/plain')
