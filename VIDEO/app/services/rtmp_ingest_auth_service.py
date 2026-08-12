import hashlib
import hmac
import os
import secrets
import time
from datetime import datetime
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, urlencode, urlparse

from models import DeviceRtmpIngestSecret, DeviceRtmpPublishAudit, db

from app.services.device_access_state_service import record_device_access_event


DEFAULT_RTMP_INGEST_BASE_URL = "rtmp://localhost/live"
DEFAULT_RTMP_INGEST_BYPASS_APPS = {"rtp", "ai"}


def _canonical_string(
    *,
    tenant_id: str,
    device_id: str,
    app: str,
    stream: str,
    exp: int,
    token_version: int,
) -> str:
    return (
        f"tenant={tenant_id}&device={device_id}&app={app}&stream={stream}"
        f"&exp={exp}&ver={token_version}"
    )


def _sign(secret: str, canonical: str) -> str:
    return hmac.new(secret.encode("utf-8"), canonical.encode("utf-8"), hashlib.sha256).hexdigest()


def _ingest_bypass_apps():
    raw = os.getenv("RTMP_INGEST_BYPASS_APPS", "rtp,ai")
    apps = {item.strip().strip("/") for item in raw.split(",") if item.strip().strip("/")}
    return apps or DEFAULT_RTMP_INGEST_BYPASS_APPS


def _allow_non_ingest_publish(
    *,
    app: str,
    stream: str,
    device_id: Optional[str],
    node_id: Optional[int],
    remote_ip: Optional[str],
    raw_params: Optional[str],
    commit: bool,
) -> Dict[str, Any]:
    reason_code = "rtmp_non_ingest_app_allowed"
    reason_message = "Non-ingest media app bypassed RTMP ingest signature enforcement"
    _audit(
        device_id=device_id,
        tenant_id=None,
        node_id=node_id,
        app=app,
        stream=stream or None,
        token_version=None,
        accepted=True,
        reason_code=reason_code,
        reason_message=reason_message,
        remote_ip=remote_ip,
        raw_params=raw_params,
    )
    if commit:
        db.session.commit()
    return {
        "accepted": True,
        "reason_code": reason_code,
        "reason_message": reason_message,
        "device_id": device_id,
        "tenant_id": None,
        "stream_id": f"{app}/{stream}" if stream else app,
    }


def _find_secret(device_id: str, tenant_id: str):
    return DeviceRtmpIngestSecret.query.filter_by(
        device_id=device_id,
        tenant_id=tenant_id,
    ).one_or_none()


def _get_or_create_secret(device_id: str, tenant_id: str):
    row = _find_secret(device_id, tenant_id)
    if row is not None:
        return row
    row = DeviceRtmpIngestSecret(
        device_id=device_id,
        tenant_id=tenant_id,
        token_version=1,
        secret=secrets.token_urlsafe(32),
    )
    db.session.add(row)
    return row


def issue_rtmp_ingest_url(
    device_id: str,
    *,
    tenant_id: str,
    ttl_seconds: int = 3600,
    base_url: Optional[str] = None,
    now: Optional[int] = None,
    commit: bool = True,
) -> Dict[str, Any]:
    clean_device_id = (device_id or "").strip()
    clean_tenant_id = (tenant_id or "").strip()
    if not clean_device_id:
        raise ValueError("device_id is required")
    if not clean_tenant_id:
        raise ValueError("tenant_id is required")

    ttl = max(60, min(int(ttl_seconds or 3600), 86400))
    current = int(now if now is not None else time.time())
    exp = current + ttl
    app = "live"
    stream = clean_device_id
    secret_row = _get_or_create_secret(clean_device_id, clean_tenant_id)
    token_version = int(secret_row.token_version or 1)
    canonical = _canonical_string(
        tenant_id=clean_tenant_id,
        device_id=clean_device_id,
        app=app,
        stream=stream,
        exp=exp,
        token_version=token_version,
    )
    sig = _sign(secret_row.secret, canonical)
    root = (base_url or os.getenv("RTMP_INGEST_BASE_URL") or DEFAULT_RTMP_INGEST_BASE_URL).rstrip("/")
    query = urlencode({"tenant": clean_tenant_id, "exp": exp, "ver": token_version, "sig": sig})
    if commit:
        db.session.commit()
    return {
        "push_url": f"{root}/{clean_device_id}?{query}",
        "device_id": clean_device_id,
        "tenant_id": clean_tenant_id,
        "app": app,
        "stream": stream,
        "expires_at": exp,
        "token_version": token_version,
    }


def rotate_rtmp_ingest_token(
    device_id: str,
    *,
    tenant_id: str,
    now: Optional[int] = None,
    commit: bool = True,
) -> Dict[str, Any]:
    clean_device_id = (device_id or "").strip()
    clean_tenant_id = (tenant_id or "").strip()
    if not clean_device_id:
        raise ValueError("device_id is required")
    if not clean_tenant_id:
        raise ValueError("tenant_id is required")

    row = _get_or_create_secret(clean_device_id, clean_tenant_id)
    row.token_version = int(row.token_version or 1) + 1
    row.secret = secrets.token_urlsafe(32)
    row.rotated_at = datetime.utcfromtimestamp(int(now)) if now is not None else datetime.utcnow()
    reason_message = "RTMP ingest token rotated; previous push URLs are revoked"
    _audit(
        device_id=clean_device_id,
        tenant_id=clean_tenant_id,
        node_id=None,
        app="live",
        stream=clean_device_id,
        token_version=row.token_version,
        accepted=True,
        reason_code="rtmp_token_rotated",
        reason_message=reason_message,
        remote_ip=None,
        raw_params=None,
    )
    record_device_access_event(
        device_id=clean_device_id,
        protocol="rtmp",
        state="registered",
        reason_code="rtmp_token_rotated",
        reason_message=reason_message,
        source_event="rtmp.token.rotate",
        stream_id=f"live/{clean_device_id}",
        node_id=None,
        tenant_id=clean_tenant_id,
        commit=False,
    )
    if commit:
        db.session.commit()
    return {
        "device_id": clean_device_id,
        "tenant_id": clean_tenant_id,
        "token_version": row.token_version,
        "rotated_at": row.rotated_at.isoformat() if row.rotated_at else None,
    }


def _parse_hook_params(hook_payload: Dict[str, Any]) -> Dict[str, str]:
    param = str(hook_payload.get("param") or hook_payload.get("params") or "").strip()
    if not param:
        stream_url = str(hook_payload.get("stream_url") or "").strip()
        parsed = urlparse(stream_url)
        param = parsed.query
    if param.startswith("?"):
        param = param[1:]
    parsed = parse_qs(param, keep_blank_values=True)
    return {key: values[0] for key, values in parsed.items() if values}


def _audit(
    *,
    device_id: Optional[str],
    tenant_id: Optional[str],
    node_id: Optional[int],
    app: Optional[str],
    stream: Optional[str],
    token_version: Optional[int],
    accepted: bool,
    reason_code: str,
    reason_message: str,
    remote_ip: Optional[str],
    raw_params: Optional[str],
):
    audit = DeviceRtmpPublishAudit(
        device_id=device_id,
        tenant_id=tenant_id,
        node_id=node_id,
        app=app,
        stream=stream,
        token_version=token_version,
        accepted=accepted,
        reason_code=reason_code,
        reason_message=reason_message,
        remote_ip=remote_ip,
        raw_params=raw_params,
        event_time=datetime.utcnow(),
    )
    db.session.add(audit)
    return audit


def _coerce_optional_int(value) -> Optional[int]:
    if value in (None, ""):
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def verify_rtmp_publish_hook(
    hook_payload: Dict[str, Any],
    *,
    remote_ip: Optional[str] = None,
    source_event: str = "srs.on_publish",
    now: Optional[int] = None,
    commit: bool = True,
) -> Dict[str, Any]:
    app = str(hook_payload.get("app") or "").strip().strip("/")
    stream = str(hook_payload.get("stream") or "").strip().strip("/")
    params = _parse_hook_params(hook_payload)
    tenant_id = (params.get("tenant") or "").strip()
    sig = (params.get("sig") or "").strip()
    raw_params = str(hook_payload.get("param") or hook_payload.get("params") or "")
    if not raw_params:
        raw_params = urlparse(str(hook_payload.get("stream_url") or "")).query
    device_id = stream.rsplit("/", 1)[-1] if stream else None
    token_version = int(params.get("ver") or "0") if str(params.get("ver") or "").isdigit() else None
    node_id = _coerce_optional_int(
        hook_payload.get("node_id") if hook_payload.get("node_id") is not None else hook_payload.get("nodeId")
    )
    current = int(now if now is not None else time.time())

    def reject(reason_code: str, reason_message: str) -> Dict[str, Any]:
        _audit(
            device_id=device_id,
            tenant_id=tenant_id or None,
            node_id=node_id,
            app=app or None,
            stream=stream or None,
            token_version=token_version,
            accepted=False,
            reason_code=reason_code,
            reason_message=reason_message,
            remote_ip=remote_ip,
            raw_params=raw_params,
        )
        if device_id:
            record_device_access_event(
                device_id=device_id,
                protocol="rtmp",
                state="error",
                reason_code=reason_code,
                reason_message=reason_message,
                source_event=source_event,
                stream_id=f"{app}/{stream}" if app and stream else stream,
                node_id=node_id,
                tenant_id=tenant_id or None,
                commit=False,
            )
        if commit:
            db.session.commit()
        return {
            "accepted": False,
            "reason_code": reason_code,
            "reason_message": reason_message,
            "device_id": device_id,
            "tenant_id": tenant_id or None,
        }

    if app != "live":
        if app and app in _ingest_bypass_apps():
            return _allow_non_ingest_publish(
                app=app,
                stream=stream,
                device_id=device_id,
                node_id=node_id,
                remote_ip=remote_ip,
                raw_params=raw_params,
                commit=commit,
            )
        return reject("rtmp_invalid_app", "RTMP ingest app must be live")
    if not device_id:
        return reject("rtmp_missing_device", "RTMP ingest stream is required")
    if not tenant_id:
        return reject("rtmp_missing_tenant", "RTMP ingest tenant is required")
    if not sig:
        return reject("rtmp_missing_sig", "RTMP ingest signature is required")
    try:
        exp = int(params.get("exp") or "0")
    except ValueError:
        return reject("rtmp_invalid_exp", "RTMP ingest expiry is invalid")
    if exp <= current:
        return reject("rtmp_expired", "RTMP ingest signature has expired")
    if token_version is None:
        return reject("rtmp_invalid_version", "RTMP ingest token version is invalid")

    secret_row = _find_secret(device_id, tenant_id)
    if secret_row is None:
        return reject("rtmp_unknown_token", "RTMP ingest token is not registered for this device and tenant")
    if int(secret_row.token_version or 0) != token_version:
        return reject("rtmp_token_version_revoked", "RTMP ingest token version has been rotated")

    canonical = _canonical_string(
        tenant_id=tenant_id,
        device_id=device_id,
        app=app,
        stream=stream,
        exp=exp,
        token_version=token_version,
    )
    expected = _sign(secret_row.secret, canonical)
    if not hmac.compare_digest(expected, sig):
        return reject("rtmp_bad_sig", "RTMP ingest signature is invalid")

    _audit(
        device_id=device_id,
        tenant_id=tenant_id,
        node_id=node_id,
        app=app,
        stream=stream,
        token_version=token_version,
        accepted=True,
        reason_code="rtmp_publish_accepted",
        reason_message="Signed RTMP publish accepted",
        remote_ip=remote_ip,
        raw_params=raw_params,
    )
    record_device_access_event(
        device_id=device_id,
        protocol="rtmp",
        state="stream_online",
        reason_code="rtmp_publish_accepted",
        reason_message="Signed RTMP publish accepted",
        source_event=source_event,
        stream_id=f"{app}/{stream}",
        node_id=node_id,
        tenant_id=tenant_id,
        commit=False,
    )
    if commit:
        db.session.commit()
    return {
        "accepted": True,
        "reason_code": "rtmp_publish_accepted",
        "reason_message": "Signed RTMP publish accepted",
        "device_id": device_id,
        "tenant_id": tenant_id,
        "stream_id": f"{app}/{stream}",
    }
