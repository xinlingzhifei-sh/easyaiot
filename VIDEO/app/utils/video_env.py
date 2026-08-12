"""VIDEO 根目录环境变量加载：优先 .env.{VIDEO_ENV}，供 run.py 与各 algorithm 子进程共用。"""
from __future__ import annotations

import hmac
import os
import re
from typing import Mapping, Optional

from dotenv import load_dotenv


UNPRIVILEGED_CHILD_PROCESS_ENV = 'YFEIEYE_UNPRIVILEGED_CHILD_PROCESS'
_MEDIA_AUTHORITY_ENV_NAMES = {
    'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS',
    'YFEIEYE_MEDIA_SERVICE_POLICIES',
    'YFEIEYE_MEDIA_SERVICE_IDS',
    'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET',
}
_MEDIA_AUTHORITY_ENV_PREFIXES = (
    'YFEIEYE_MEDIA_SERVICE_ALLOWED_',
)
_PRODUCTION_ENV_NAMES = {'production', 'prod', 'release'}
_KNOWN_WEAK_APPLICATION_SECRETS = {
    'your-secret-key-please-change-this-to-a-random-string',
}
_SRS_HOOK_TOKEN_RE = re.compile(r'^[A-Za-z0-9._~-]+$')


def _remove_media_service_authority(env) -> None:
    for name in list(env):
        normalized = str(name).upper()
        if normalized in _MEDIA_AUTHORITY_ENV_NAMES or normalized.startswith(
                _MEDIA_AUTHORITY_ENV_PREFIXES):
            env.pop(name, None)


def build_unprivileged_process_env(
        overrides: Optional[Mapping[str, str]] = None) -> dict:
    """Copy runtime settings without delegating media service authority."""
    env = os.environ.copy()
    if overrides:
        env.update(overrides)
    env[UNPRIVILEGED_CHILD_PROCESS_ENV] = '1'
    _remove_media_service_authority(env)
    return env


def validate_production_runtime_secrets() -> None:
    """Fail closed when a release is started without external runtime secrets."""
    if str(os.getenv('VIDEO_ENV') or '').strip().lower() not in _PRODUCTION_ENV_NAMES:
        return
    application_secret = str(os.getenv('SECRET_KEY') or '')
    if (len(application_secret.encode('utf-8')) < 32
            or application_secret in _KNOWN_WEAK_APPLICATION_SECRETS):
        raise RuntimeError('SECRET_KEY must contain at least 32 bytes in production')
    minio_access_key = str(os.getenv('MINIO_ACCESS_KEY') or '').strip()
    minio_secret_key = str(os.getenv('MINIO_SECRET_KEY') or '')
    if not minio_access_key or minio_access_key.lower() == 'minioadmin':
        raise RuntimeError('MINIO_ACCESS_KEY must be configured in production')
    if len(minio_secret_key.encode('utf-8')) < 16 or minio_secret_key.lower() == 'minioadmin':
        raise RuntimeError('MINIO_SECRET_KEY must contain at least 16 bytes in production')
    srs_hook_token = str(os.getenv('YFEIEYE_SRS_HOOK_TOKEN') or '')
    if (len(srs_hook_token.encode('utf-8')) < 32
            or _SRS_HOOK_TOKEN_RE.fullmatch(srs_hook_token) is None):
        raise RuntimeError(
            'YFEIEYE_SRS_HOOK_TOKEN must contain at least 32 bytes in production')


def authorize_srs_hook_token(provided_token: Optional[str]) -> bool:
    """Validate the private SRS callback bearer token without timing leaks."""
    configured_token = str(os.getenv('YFEIEYE_SRS_HOOK_TOKEN') or '')
    if (len(configured_token.encode('utf-8')) < 32
            or _SRS_HOOK_TOKEN_RE.fullmatch(configured_token) is None
            or not provided_token):
        return False
    return hmac.compare_digest(
        configured_token.encode('utf-8'),
        str(provided_token).encode('utf-8'),
    )


def video_root_dir() -> str:
    return os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def load_video_env(*, override: bool = True) -> str | None:
    """加载 VIDEO/.env.{VIDEO_ENV} 或 VIDEO/.env；返回实际加载的文件路径。"""
    root = video_root_dir()
    candidates: list[str] = []
    env_name = os.getenv('VIDEO_ENV', '').strip()
    if env_name:
        candidates.append(os.path.join(root, f'.env.{env_name}'))
    candidates.append(os.path.join(root, '.env'))
    for path in candidates:
        if os.path.isfile(path):
            load_dotenv(path, override=override)
            if os.getenv(UNPRIVILEGED_CHILD_PROCESS_ENV) == '1':
                _remove_media_service_authority(os.environ)
            return path
    load_dotenv(override=override)
    if os.getenv(UNPRIVILEGED_CHILD_PROCESS_ENV) == '1':
        _remove_media_service_authority(os.environ)
    return None
