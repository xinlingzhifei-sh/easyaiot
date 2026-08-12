"""Fail-closed authorization and persistent audit for VIDEO media access."""
from __future__ import annotations

import hashlib
import hmac
import json
import os
import threading
import time
import uuid
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Optional
from urllib.parse import unquote_plus, urlsplit

import requests


_AUTHORIZATION_URL_ENV = 'YFEIEYE_MEDIA_AUTHORIZATION_URL'
_SERVICE_SECRET_ENV = 'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET'
_SERVICE_KEYS_ENV = 'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS'
_SERVICE_POLICIES_ENV = 'YFEIEYE_MEDIA_SERVICE_POLICIES'
_SERVICE_IDS_ENV = 'YFEIEYE_MEDIA_SERVICE_IDS'
_SERVICE_ACTIONS_ENV = 'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS'
_SERVICE_CAMERAS_ENV = 'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS'
_MAX_SKEW_ENV = 'YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS'
_AUDIT_DIR_ENV = 'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR'
_AUDIT_MAX_BYTES_ENV = 'YFEIEYE_MEDIA_ACCESS_AUDIT_MAX_BYTES'
_AUDIT_BACKUP_COUNT_ENV = 'YFEIEYE_MEDIA_ACCESS_AUDIT_BACKUP_COUNT'
_DEFAULT_SERVICE_IDS = 'iot-system,video-algorithm'
_DEFAULT_SERVICE_ACTIONS = 'coverage,export,download,alert_ingest'
_DEFAULT_AUDIT_MAX_BYTES = 64 * 1024 * 1024
_DEFAULT_AUDIT_BACKUP_COUNT = 5
_AUDIT_LOCK = threading.Lock()
_NONCE_LOCK = threading.Lock()
_SEEN_NONCES: dict[str, float] = {}
_MEDIA_TICKET_FIELDS = {
    'yf_ticket',
    'yf_service_id',
    'yf_user_id',
    'yf_tenant_id',
    'yf_camera_id',
    'yf_action',
    'yf_timestamp',
    'yf_nonce',
    'yf_signature',
}
_ALERT_INGEST_SERVICE_ID_ENV = 'YFEIEYE_ALERT_INGEST_SERVICE_ID'
_ALERT_INGEST_TENANT_ID_ENV = 'YFEIEYE_ALERT_INGEST_TENANT_ID'
_ALERT_INGEST_UNSIGNED_ENV = 'YFEIEYE_ALERT_INGEST_ALLOW_UNSIGNED'
_MIN_SERVICE_SECRET_BYTES = 32


class MediaAccessAuditConflictError(RuntimeError):
    """Raised when an idempotency key is reused for a different decision."""


@dataclass(frozen=True)
class MediaAuthorizationDecision:
    allowed: bool
    user_id: Optional[str]
    tenant_id: Optional[str]
    camera_id: Optional[str]
    action: str
    reason: str
    status_code: int
    auth_type: Optional[str] = None
    service_id: Optional[str] = None


def authorize_media_request(req,
                            action: str,
                            camera_id=None,
                            resource=None,
                            export_id=None,
                            owner_tenant_id=None,
                            defer_audit=False) -> MediaAuthorizationDecision:
    """Authorize a user token or a signed DEVICE service context.

    Caller-supplied operator and tenant values are deliberately ignored. The
    subject always comes from DEVICE's authenticated response or from a
    verified HMAC service context.
    """
    action = _text(action).lower()
    camera_id = _optional_text(camera_id)
    resource = _optional_text(resource) or getattr(req, 'path', None)
    export_id = _optional_text(export_id)
    owner_tenant_id = _optional_text(owner_tenant_id)

    if _has_service_context(req):
        decision = _authorize_service_context(req, action, camera_id)
    else:
        decision = _authorize_user_context(req, action, camera_id, resource, export_id)

    if decision.allowed and owner_tenant_id and decision.tenant_id != owner_tenant_id:
        decision = MediaAuthorizationDecision(
            False,
            decision.user_id,
            decision.tenant_id,
            camera_id,
            action,
            'tenant_scope_denied',
            403,
            decision.auth_type,
            decision.service_id,
        )
    if not defer_audit:
        append_media_access_audit(decision, resource=resource, export_id=export_id)
    return decision


def append_media_access_audit(decision: MediaAuthorizationDecision,
                              resource=None,
                              export_id=None,
                              reason=None,
                              decision_override=None,
                              decision_id=None) -> dict[str, Any]:
    """Append a durable JSONL decision record and return the stored entry."""
    decision_id = _optional_text(decision_id)
    entry = {
        'auditId': f'media-{uuid.uuid4().hex}',
        'userId': _optional_text(decision.user_id),
        'tenantId': _optional_text(decision.tenant_id),
        'cameraId': _optional_text(decision.camera_id),
        'action': _text(decision.action),
        'resource': _optional_text(resource),
        'exportId': _optional_text(export_id),
        'decision': _optional_text(decision_override) or ('allowed' if decision.allowed else 'denied'),
        'reason': _optional_text(reason) or _text(decision.reason),
        'authType': _optional_text(decision.auth_type),
        'serviceId': _optional_text(decision.service_id),
        'timestamp': datetime.now(timezone.utc).isoformat(),
    }
    if decision_id:
        entry['decisionId'] = decision_id
    path = _audit_path()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    line = json.dumps(entry, ensure_ascii=False, separators=(',', ':')) + '\n'
    with _AUDIT_LOCK:
        existing = _find_media_audit_decision(path, decision_id)
        if existing is not None:
            comparable = {
                key: value
                for key, value in entry.items()
                if key not in ('auditId', 'timestamp')
            }
            if any(existing.get(key) != value for key, value in comparable.items()):
                raise MediaAccessAuditConflictError(
                    f'media access audit idempotency conflict: {decision_id}')
            return existing
        _rotate_audit_if_needed(path, len(line.encode('utf-8')))
        with open(path, 'a', encoding='utf-8', newline='') as audit_file:
            audit_file.write(line)
            audit_file.flush()
            os.fsync(audit_file.fileno())
    return entry


def _find_media_audit_decision(path: str, decision_id):
    if not decision_id:
        return None
    for index in range(_audit_backup_count() + 1):
        candidate = path if index == 0 else f'{path}.{index}'
        try:
            with open(candidate, 'r', encoding='utf-8') as audit_file:
                for line in audit_file:
                    if not line.strip():
                        continue
                    try:
                        entry = json.loads(line)
                    except (TypeError, ValueError):
                        continue
                    if isinstance(entry, dict) \
                            and entry.get('decisionId') == decision_id:
                        return entry
        except FileNotFoundError:
            continue
    return None


def audit_media_response(decision: MediaAuthorizationDecision,
                         resource=None,
                         export_id=None) -> MediaAuthorizationDecision:
    """Write exactly one final authorization audit after response validation."""
    from flask import after_this_request

    @after_this_request
    def append_final_decision(response):
        payload = response.get_json(silent=True) if response.is_json else None
        business_code = payload.get('code') if isinstance(payload, dict) else None
        response_allowed = 200 <= response.status_code < 400
        if isinstance(business_code, int) and business_code >= 400:
            response_allowed = False
        allowed = bool(decision.allowed and response_allowed)
        reason = (
            _optional_text(payload.get('reason'))
            if isinstance(payload, dict)
            else None
        )
        if not reason:
            reason = decision.reason if allowed or not decision.allowed else (
                f'http_{response.status_code}')
        final_decision = MediaAuthorizationDecision(
            allowed,
            decision.user_id,
            decision.tenant_id,
            decision.camera_id,
            decision.action,
            reason,
            response.status_code if not allowed else 200,
            decision.auth_type,
            decision.service_id,
        )
        append_media_access_audit(
            final_decision,
            resource=resource,
            export_id=export_id,
        )
        return response

    return decision


def authorization_error(decision: MediaAuthorizationDecision):
    """Return the stable JSON payload/status used by protected blueprints."""
    return {
        'code': decision.status_code,
        'msg': 'unauthorized' if decision.status_code == 401 else 'forbidden',
        'reason': decision.reason,
    }, decision.status_code


def canonical_service_signature(method: str,
                                path: str,
                                timestamp: str,
                                nonce: str,
                                service_id: str,
                                user_id: str,
                                tenant_id: str,
                                camera_id: str,
                                action: str,
                                body: bytes | str | None,
                                secret: str) -> str:
    """Build the HMAC value shared with DEVICE's HTTP providers."""
    if isinstance(body, str):
        body = body.encode('utf-8')
    body_hash = hashlib.sha256(body or b'').hexdigest()
    canonical = '\n'.join((
        'v1',
        _text(timestamp),
        _text(nonce),
        _text(method).upper(),
        _text(path),
        _text(service_id),
        _text(user_id),
        _text(tenant_id),
        _text(camera_id),
        _text(action).lower(),
        body_hash,
    ))
    return 'sha256=' + hmac.new(
        _text(secret).encode('utf-8'),
        canonical.encode('utf-8'),
        hashlib.sha256,
    ).hexdigest()


def build_alert_ingest_request(target: str,
                               payload: dict,
                               nonce: Optional[str] = None,
                               timestamp: Optional[str] = None) -> tuple[bytes, dict[str, str]]:
    """Serialize and sign one algorithm-service alert hook request."""
    if not isinstance(payload, dict):
        raise ValueError('alert ingest payload must be an object')
    camera_id = _optional_text(
        payload.get('device_id')
        or payload.get('deviceId')
        or payload.get('camera_id')
        or payload.get('cameraId')
    )
    if not camera_id:
        raise ValueError('alert ingest camera id is required')

    body = json.dumps(
        payload,
        ensure_ascii=False,
        separators=(',', ':'),
    ).encode('utf-8')
    headers = {'Content-Type': 'application/json'}
    service_id = _optional_text(os.environ.get(_ALERT_INGEST_SERVICE_ID_ENV)) \
        or 'video-algorithm'
    secret, secret_error = _service_secret(service_id)
    if not secret:
        if (secret_error == 'service_signature_not_configured'
                and _development_unsigned_alert_ingest_enabled()):
            return body, headers
        raise RuntimeError(
            f'alert ingest service HMAC key is unavailable: {secret_error}')

    tenant_id = _optional_text(
        payload.get('tenant_id')
        or payload.get('tenantId')
        or os.environ.get(_ALERT_INGEST_TENANT_ID_ENV)
    )
    if not tenant_id or not tenant_id.isdigit() or int(tenant_id) <= 0:
        raise ValueError('alert ingest tenant id is required and must be a positive integer')
    user_id = f'service:{service_id}'
    timestamp = _text(timestamp) or str(time.time())
    nonce = _text(nonce) or uuid.uuid4().hex
    parsed = urlsplit(_text(target))
    request_target = parsed.path or '/'
    if parsed.query:
        request_target += '?' + parsed.query
    signature = canonical_service_signature(
        'POST',
        request_target,
        timestamp,
        nonce,
        service_id,
        user_id,
        tenant_id,
        camera_id,
        'alert_ingest',
        body,
        secret,
    )
    headers.update({
        'X-YFeiEye-Service-Id': service_id,
        'X-YFeiEye-Service-User-Id': user_id,
        'X-YFeiEye-Service-Tenant-Id': tenant_id,
        'X-YFeiEye-Service-Camera-Id': camera_id,
        'X-YFeiEye-Service-Action': 'alert_ingest',
        'X-YFeiEye-Service-Timestamp': timestamp,
        'X-YFeiEye-Service-Nonce': nonce,
        'X-YFeiEye-Service-Signature': signature,
    })
    return body, headers


def build_alert_ingest_process_env() -> dict[str, str]:
    """Return least-privilege credentials for an algorithm child process."""
    service_id = _optional_text(os.environ.get(_ALERT_INGEST_SERVICE_ID_ENV)) \
        or 'video-algorithm'
    secret, secret_error = _service_secret(service_id)
    if not secret:
        if _production_environment():
            raise RuntimeError(
                f'alert ingest service HMAC key is unavailable: {secret_error}')
        return {}
    tenant_id = _optional_text(os.environ.get(_ALERT_INGEST_TENANT_ID_ENV))
    if not tenant_id or not tenant_id.isdigit() or int(tenant_id) <= 0:
        if _production_environment():
            raise RuntimeError('alert ingest tenant id is not configured')
        return {}
    return {
        _SERVICE_SECRET_ENV: secret,
        _SERVICE_IDS_ENV: service_id,
        _ALERT_INGEST_SERVICE_ID_ENV: service_id,
        _ALERT_INGEST_TENANT_ID_ENV: tenant_id,
    }


def post_alert_ingest(target: str, payload: dict, timeout=5):
    """POST an alert hook using the exact bytes covered by its service HMAC."""
    body, headers = build_alert_ingest_request(target, payload)
    return requests.post(
        target,
        data=body,
        headers=headers,
        timeout=timeout,
    )


def _authorize_user_context(req,
                            action: str,
                            camera_id: Optional[str],
                            resource: Optional[str],
                            export_id: Optional[str]) -> MediaAuthorizationDecision:
    authorization = _authorization_header(req)
    if not authorization:
        return _denied(action, camera_id, 'authentication_required', 401)
    authorization_url = _optional_text(os.environ.get(_AUTHORIZATION_URL_ENV))
    if not authorization_url:
        return _denied(action, camera_id, 'authorization_service_not_configured', 503)
    headers = {'Authorization': authorization}
    tenant_header = (
        req.headers.get('tenant-id')
        or req.headers.get('Tenant-Id')
        or req.headers.get('X-Tenant-Id')
    )
    if tenant_header:
        headers['tenant-id'] = tenant_header
    try:
        response = requests.post(
            authorization_url,
            headers=headers,
            json={
                'action': action,
                'cameraId': camera_id,
                'resource': resource,
                'exportId': export_id,
            },
            timeout=5,
        )
        if response.status_code != 200:
            return _denied(action, camera_id, 'authorization_service_rejected', 401)
        payload = response.json()
    except Exception:
        return _denied(action, camera_id, 'authorization_service_unavailable', 503)
    if not isinstance(payload, dict) or payload.get('code') != 0:
        return _denied(action, camera_id, 'authorization_service_rejected', 401)
    data = payload.get('data')
    if not isinstance(data, dict):
        return _denied(action, camera_id, 'authentication_required', 401)

    user_id = _optional_text(data.get('userId') or data.get('user_id'))
    tenant_id = _optional_text(data.get('tenantId') or data.get('tenant_id'))
    authorized_camera = _optional_text(data.get('cameraId') or data.get('camera_id'))
    authorized_action = _text(data.get('action')).lower()
    auth_type = 'user_token'
    if not user_id:
        return _denied(action, camera_id, 'authentication_required', 401, auth_type=auth_type)
    if not tenant_id:
        return _denied(action, camera_id, 'tenant_required', 403, user_id=user_id, auth_type=auth_type)
    if authorized_action != action:
        return _denied(action, camera_id, 'action_permission_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type)
    if camera_id and authorized_camera != camera_id:
        return _denied(action, camera_id, 'camera_scope_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type)
    if data.get('allowed') is not True:
        return _denied(
            action,
            camera_id,
            _optional_text(data.get('reason')) or 'permission_denied',
            403,
            user_id=user_id,
            tenant_id=tenant_id,
            auth_type=auth_type,
        )
    return MediaAuthorizationDecision(
        True,
        user_id,
        tenant_id,
        camera_id or authorized_camera,
        action,
        _optional_text(data.get('reason')) or 'granted',
        200,
        auth_type,
    )


def _authorize_service_context(req,
                               action: str,
                               camera_id: Optional[str]) -> MediaAuthorizationDecision:
    header_context = bool(req.headers.get('X-YFeiEye-Service-Id'))
    ticket_context = _has_media_ticket(req)
    if header_context and ticket_context:
        return _denied(action, camera_id, 'service_context_ambiguous', 401)
    service_id = _service_context_value(
        req, 'X-YFeiEye-Service-Id', 'yf_service_id', ticket_context
    )
    user_id = _service_context_value(
        req, 'X-YFeiEye-Service-User-Id', 'yf_user_id', ticket_context
    )
    tenant_id = _service_context_value(
        req, 'X-YFeiEye-Service-Tenant-Id', 'yf_tenant_id', ticket_context
    )
    signed_camera = _service_context_value(
        req, 'X-YFeiEye-Service-Camera-Id', 'yf_camera_id', ticket_context
    )
    signed_action = _text(_service_context_value(
        req, 'X-YFeiEye-Service-Action', 'yf_action', ticket_context
    )).lower()
    timestamp = _service_context_value(
        req, 'X-YFeiEye-Service-Timestamp', 'yf_timestamp', ticket_context
    )
    nonce = _service_context_value(
        req, 'X-YFeiEye-Service-Nonce', 'yf_nonce', ticket_context
    )
    supplied_signature = _service_context_value(
        req, 'X-YFeiEye-Service-Signature', 'yf_signature', ticket_context
    )
    auth_type = 'signed_media_url' if ticket_context else 'service_hmac'
    if not service_id or service_id not in _allowed_service_ids():
        return _denied(action, camera_id, 'service_identity_denied', 401,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    if not user_id:
        return _denied(action, camera_id, 'authentication_required', 401,
                       tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    if not tenant_id:
        return _denied(action, camera_id, 'tenant_required', 403,
                       user_id=user_id, auth_type=auth_type, service_id=service_id)
    if signed_action != action:
        return _denied(action, camera_id, 'action_permission_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    if camera_id and signed_camera != camera_id:
        return _denied(action, camera_id, 'camera_scope_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    policy, policy_error = _service_policy(service_id)
    if policy_error:
        return _denied(action, camera_id, policy_error, 503,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    if action not in policy['actions']:
        return _denied(action, camera_id, 'service_action_scope_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    if signed_camera not in policy['camera_ids'] and '*' not in policy['camera_ids']:
        return _denied(action, camera_id, 'service_camera_scope_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    expected_service_subject = f'service:{service_id}'
    if user_id != expected_service_subject and not policy['allow_on_behalf']:
        return _denied(action, camera_id, 'service_subject_scope_denied', 403,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    secret, secret_error = _service_secret(service_id)
    if not secret:
        return _denied(action, camera_id, secret_error or 'service_signature_not_configured', 503,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    if not timestamp or not nonce or not supplied_signature:
        return _denied(action, camera_id, 'service_signature_invalid', 401,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    try:
        timestamp_value = float(timestamp)
    except ValueError:
        return _denied(action, camera_id, 'service_signature_invalid', 401,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    now = time.time()
    max_skew = _max_service_skew_seconds()
    if abs(now - timestamp_value) > max_skew:
        return _denied(action, camera_id, 'service_signature_expired', 401,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    body = req.get_data(cache=True) if hasattr(req, 'get_data') else b''
    expected = canonical_service_signature(
        getattr(req, 'method', ''),
        _canonical_request_target(req, strip_media_ticket=ticket_context),
        timestamp,
        nonce,
        service_id,
        user_id,
        tenant_id,
        signed_camera or '',
        signed_action,
        body,
        secret,
    )
    if not hmac.compare_digest(expected, supplied_signature):
        return _denied(action, camera_id, 'service_signature_invalid', 401,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    # Browser media elements can issue multiple Range requests for one URL.
    # Header-authenticated service calls stay single-use; a signed media URL is
    # intentionally reusable only inside the short timestamp window.
    if not ticket_context and not _consume_nonce(service_id, nonce, now, max_skew):
        return _denied(action, camera_id, 'service_signature_replayed', 401,
                       user_id=user_id, tenant_id=tenant_id, auth_type=auth_type, service_id=service_id)
    return MediaAuthorizationDecision(
        True,
        user_id,
        tenant_id,
        camera_id or signed_camera,
        action,
        'granted',
        200,
        auth_type,
        service_id,
    )


def _denied(action,
            camera_id,
            reason,
            status_code,
            user_id=None,
            tenant_id=None,
            auth_type=None,
            service_id=None):
    return MediaAuthorizationDecision(
        False,
        _optional_text(user_id),
        _optional_text(tenant_id),
        _optional_text(camera_id),
        _text(action),
        _text(reason),
        int(status_code),
        _optional_text(auth_type),
        _optional_text(service_id),
    )


def _consume_nonce(service_id: str, nonce: str, now: float, max_skew: int) -> bool:
    key = f'{service_id}:{nonce}'
    with _NONCE_LOCK:
        expired = [candidate for candidate, expires_at in _SEEN_NONCES.items() if expires_at < now]
        for candidate in expired:
            _SEEN_NONCES.pop(candidate, None)
        if key in _SEEN_NONCES:
            return False
        if not _claim_persistent_nonce(key, now, max_skew):
            return False
        _SEEN_NONCES[key] = now + max_skew
        return True


def _canonical_request_target(req, strip_media_ticket=False) -> str:
    path = _text(getattr(req, 'path', ''))
    raw_query = getattr(req, 'query_string', b'') or b''
    if isinstance(raw_query, bytes):
        raw_query = raw_query.decode('latin-1')
    else:
        raw_query = str(raw_query)
    if strip_media_ticket:
        raw_query = _strip_media_ticket_query(raw_query)
    return f'{path}?{raw_query}' if raw_query else path


def _strip_media_ticket_query(raw_query: str) -> str:
    retained = []
    for part in str(raw_query or '').split('&'):
        raw_key = part.split('=', 1)[0]
        try:
            key = unquote_plus(raw_key)
        except Exception:
            key = raw_key
        if key in _MEDIA_TICKET_FIELDS:
            continue
        if part:
            retained.append(part)
    return '&'.join(retained)


def _service_context_value(req, header_name, query_name, ticket_context):
    value = req.args.get(query_name) if ticket_context else req.headers.get(header_name)
    return _optional_text(value)


def _claim_persistent_nonce(key: str, now: float, max_skew: int) -> bool:
    nonce_dir = os.path.join(os.path.dirname(_audit_path()), '.media-nonces')
    try:
        os.makedirs(nonce_dir, mode=0o700, exist_ok=True)
        _cleanup_persistent_nonces(nonce_dir, now, max_skew)
    except OSError:
        return False

    marker_name = hashlib.sha256(key.encode('utf-8')).hexdigest() + '.nonce'
    marker_path = os.path.join(nonce_dir, marker_name)
    expires_at = now + max_skew
    for _attempt in range(2):
        try:
            descriptor = os.open(
                marker_path,
                os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                0o600,
            )
        except FileExistsError:
            if not _persistent_nonce_expired(marker_path, now, max_skew):
                return False
            try:
                os.unlink(marker_path)
            except FileNotFoundError:
                pass
            except OSError:
                return False
            continue
        except OSError:
            return False
        try:
            os.write(descriptor, f'{expires_at:.6f}\n'.encode('ascii'))
            os.fsync(descriptor)
        except OSError:
            try:
                os.unlink(marker_path)
            except OSError:
                pass
            return False
        finally:
            os.close(descriptor)
        return True
    return False


def _cleanup_persistent_nonces(nonce_dir: str, now: float, max_skew: int) -> None:
    for entry in os.scandir(nonce_dir):
        if not entry.is_file(follow_symlinks=False) or not entry.name.endswith('.nonce'):
            continue
        if not _persistent_nonce_expired(entry.path, now, max_skew):
            continue
        try:
            os.unlink(entry.path)
        except FileNotFoundError:
            pass


def _persistent_nonce_expired(marker_path: str, now: float, max_skew: int) -> bool:
    try:
        with open(marker_path, 'r', encoding='ascii') as marker_file:
            return float(marker_file.read().strip()) < now
    except (OSError, ValueError):
        try:
            return os.path.getmtime(marker_path) < now - max_skew
        except OSError:
            return False


def _has_service_context(req) -> bool:
    return bool(req.headers.get('X-YFeiEye-Service-Id')) or _has_media_ticket(req)


def _has_media_ticket(req) -> bool:
    return _text(req.args.get('yf_ticket')).lower() == 'v1'


def _authorization_header(req) -> str:
    return _text(req.headers.get('Authorization') or req.headers.get('X-Authorization')).strip()


def _allowed_service_ids() -> set[str]:
    raw = os.environ.get(_SERVICE_IDS_ENV, _DEFAULT_SERVICE_IDS)
    return {value.strip() for value in raw.split(',') if value.strip()}


def _service_secret(service_id: str) -> tuple[Optional[str], Optional[str]]:
    raw_keyring = _optional_text(os.environ.get(_SERVICE_KEYS_ENV))
    if raw_keyring:
        try:
            keyring = json.loads(raw_keyring)
        except (TypeError, ValueError):
            return None, 'service_keyring_invalid'
        if not isinstance(keyring, dict):
            return None, 'service_keyring_invalid'
        secret = _optional_text(keyring.get(service_id))
        if not secret or len(secret.encode('utf-8')) < _MIN_SERVICE_SECRET_BYTES:
            return None, 'service_key_unavailable'
        return secret, None
    if _production_environment() and len(_allowed_service_ids()) > 1:
        return None, 'service_keyring_required'
    secret = _optional_text(os.environ.get(_SERVICE_SECRET_ENV))
    if not secret:
        return None, 'service_signature_not_configured'
    if len(secret.encode('utf-8')) < _MIN_SERVICE_SECRET_BYTES:
        return None, 'service_signature_secret_invalid'
    return secret, None


def _service_policy(service_id: str) -> tuple[Optional[dict], Optional[str]]:
    raw_policies = _optional_text(os.environ.get(_SERVICE_POLICIES_ENV))
    if raw_policies:
        try:
            policies = json.loads(raw_policies)
        except (TypeError, ValueError):
            return None, 'service_policy_invalid'
        raw_policy = policies.get(service_id) if isinstance(policies, dict) else None
        if not isinstance(raw_policy, dict):
            return None, 'service_policy_unavailable'
        actions = {
            _text(value).lower()
            for value in raw_policy.get('actions', [])
            if _text(value)
        }
        cameras = {
            _text(value)
            for value in raw_policy.get('cameraIds', raw_policy.get('camera_ids', []))
            if _text(value)
        }
        allow_on_behalf = raw_policy.get(
            'allowOnBehalf', raw_policy.get('allow_on_behalf', False)) is True
        if not actions or not cameras:
            return None, 'service_policy_invalid'
        return {
            'actions': actions,
            'camera_ids': cameras,
            'allow_on_behalf': allow_on_behalf,
        }, None
    cameras = _csv_values(os.environ.get(_SERVICE_CAMERAS_ENV, ''))
    if service_id == 'video-algorithm':
        actions = {'alert_ingest'}
        allow_on_behalf = False
    else:
        actions = _csv_values(os.environ.get(
            _SERVICE_ACTIONS_ENV, _DEFAULT_SERVICE_ACTIONS))
        allow_on_behalf = service_id == 'iot-system'
    return {
        'actions': actions,
        'camera_ids': cameras,
        'allow_on_behalf': allow_on_behalf,
    }, None


def _production_environment() -> bool:
    return _text(os.environ.get('VIDEO_ENV')).lower() in {'production', 'prod'}


def _csv_values(raw: str) -> set[str]:
    return {value.strip() for value in (raw or '').split(',') if value.strip()}


def _max_service_skew_seconds() -> int:
    try:
        return max(10, min(int(os.environ.get(_MAX_SKEW_ENV, '60')), 300))
    except ValueError:
        return 60


def _development_unsigned_alert_ingest_enabled() -> bool:
    environment = _text(os.environ.get('VIDEO_ENV')).lower()
    allow_unsigned = _text(os.environ.get(_ALERT_INGEST_UNSIGNED_ENV)).lower()
    return (
        environment in {'development', 'dev', 'test', 'local'}
        and allow_unsigned in {'1', 'true', 'yes', 'on'}
    )


def _audit_path() -> str:
    root = (
        _optional_text(os.environ.get(_AUDIT_DIR_ENV))
        or _optional_text(os.environ.get('YFEIEYE_RECORD_EXPORT_STORE_DIR'))
        or os.path.join(os.getcwd(), 'data', 'media-access-audit')
    )
    return os.path.join(os.path.abspath(root), 'media-access-audit.jsonl')


def _rotate_audit_if_needed(path: str, incoming_bytes: int) -> None:
    if not os.path.isfile(path):
        return
    if os.path.getsize(path) + incoming_bytes <= _audit_max_bytes():
        return

    backup_count = _audit_backup_count()
    if backup_count == 0:
        os.remove(path)
        return

    oldest = f'{path}.{backup_count}'
    if os.path.exists(oldest):
        os.remove(oldest)
    for index in range(backup_count - 1, 0, -1):
        source = f'{path}.{index}'
        if os.path.exists(source):
            os.replace(source, f'{path}.{index + 1}')
    os.replace(path, f'{path}.1')


def _audit_max_bytes() -> int:
    try:
        value = int(os.environ.get(_AUDIT_MAX_BYTES_ENV, _DEFAULT_AUDIT_MAX_BYTES))
        return value if value > 0 else _DEFAULT_AUDIT_MAX_BYTES
    except ValueError:
        return _DEFAULT_AUDIT_MAX_BYTES


def _audit_backup_count() -> int:
    try:
        value = int(os.environ.get(_AUDIT_BACKUP_COUNT_ENV, _DEFAULT_AUDIT_BACKUP_COUNT))
        return max(0, min(value, 100))
    except ValueError:
        return _DEFAULT_AUDIT_BACKUP_COUNT


def _text(value) -> str:
    return '' if value is None else str(value).strip()


def _optional_text(value) -> Optional[str]:
    value = _text(value)
    return value or None
