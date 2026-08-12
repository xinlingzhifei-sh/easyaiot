from datetime import datetime
from typing import Any, Dict, Optional
from urllib.parse import urlparse

from models import DeviceAccessStateCurrent, DeviceAccessStateEvent, db


ACCESS_STATES = {
    "pending_config",
    "registering",
    "registered",
    "stream_online",
    "play_ready",
    "ai_ready",
    "error",
}

ACCESS_PROTOCOLS = {
    "gb28181",
    "rtsp",
    "rtmp",
    "http_flv",
    "webrtc",
    "edge_agent",
}

STATE_PRIORITY = {
    "pending_config": 0,
    "registering": 10,
    "registered": 20,
    "stream_online": 30,
    "play_ready": 40,
    "ai_ready": 50,
    "error": 100,
}

STUCK_STATES = {
    "registering",
    "registered",
}

PROTOCOL_LABEL = {
    "gb28181": "GB28181",
    "rtsp": "RTSP",
    "rtmp": "RTMP",
    "http_flv": "HTTP-FLV",
    "webrtc": "WebRTC",
    "edge_agent": "Edge Agent",
}


def _format_time(value: Any) -> Optional[str]:
    return value.isoformat() if value is not None and hasattr(value, "isoformat") else None


def record_device_access_event(
    *,
    device_id: str,
    protocol: str,
    state: str,
    reason_code: Optional[str] = None,
    reason_message: Optional[str] = None,
    source_event: Optional[str] = None,
    event_time: Optional[datetime] = None,
    stream_id: Optional[str] = None,
    node_id: Optional[int] = None,
    tenant_id: Optional[str] = None,
    commit: bool = True,
) -> Dict[str, Any]:
    clean_device_id = (device_id or "").strip()
    clean_protocol = (protocol or "").strip().lower()
    clean_state = (state or "").strip().lower()
    if not clean_device_id:
        raise ValueError("device_id is required")
    if clean_protocol not in ACCESS_PROTOCOLS:
        raise ValueError(f"unsupported access protocol: {protocol}")
    if clean_state not in ACCESS_STATES:
        raise ValueError(f"unsupported access state: {state}")

    transition_time = event_time or datetime.utcnow()
    event = DeviceAccessStateEvent(
        device_id=clean_device_id,
        protocol=clean_protocol,
        state=clean_state,
        reason_code=reason_code,
        reason_message=reason_message,
        source_event=source_event,
        event_time=transition_time,
        stream_id=stream_id,
        node_id=node_id,
        tenant_id=tenant_id,
    )
    db.session.add(event)

    current = DeviceAccessStateCurrent.query.filter_by(
        device_id=clean_device_id,
        protocol=clean_protocol,
    ).one_or_none()
    if current is None:
        current = DeviceAccessStateCurrent(device_id=clean_device_id, protocol=clean_protocol)
        db.session.add(current)

    current.state = clean_state
    current.reason_code = reason_code
    current.reason_message = reason_message
    current.source_event = source_event
    current.last_transition_time = transition_time
    current.stream_id = stream_id
    current.node_id = node_id
    current.tenant_id = tenant_id

    if commit:
        db.session.commit()
    return {"event": event, "current": current}


def record_srs_publish_online(
    hook_payload: Dict[str, Any],
    *,
    node_id: Optional[int] = None,
    tenant_id: Optional[str] = None,
    source_event: str = "srs.on_publish",
    commit: bool = True,
) -> Dict[str, Any]:
    app = str(hook_payload.get("app") or "").strip().strip("/")
    stream = str(hook_payload.get("stream") or "").strip().strip("/")
    stream_url = str(hook_payload.get("stream_url") or "").strip().strip("/")
    if not app or not stream:
        parts = [part for part in stream_url.split("/") if part]
        if len(parts) >= 2:
            app = app or parts[-2]
            stream = stream or parts[-1]
    if not stream:
        raise ValueError("SRS publish hook stream is required")

    device_id = stream.rsplit("/", 1)[-1]
    stream_id = f"{app}/{stream}" if app else stream
    protocol = "rtmp"
    edge_current = DeviceAccessStateCurrent.query.filter_by(
        device_id=device_id,
        protocol="edge_agent",
    ).one_or_none()
    if edge_current is not None and edge_current.state in {"registering", "registered"}:
        protocol = "edge_agent"

    source_prefix = "zlm" if source_event.startswith("zlm.") else "srs"
    source_label = source_prefix.upper()
    return record_device_access_event(
        device_id=device_id,
        protocol=protocol,
        state="stream_online",
        reason_code=f"{source_prefix}_publish_online",
        reason_message=f"{source_label} publish hook confirmed live stream",
        source_event=source_event,
        stream_id=stream_id,
        node_id=node_id,
        tenant_id=tenant_id,
        commit=commit,
    )


def record_media_stream_offline(
    hook_payload: Dict[str, Any],
    *,
    node_id: Optional[int] = None,
    tenant_id: Optional[str] = None,
    source_event: str = "srs.on_unpublish",
    commit: bool = True,
) -> Dict[str, Any]:
    app = str(hook_payload.get("app") or "").strip().strip("/")
    stream = str(hook_payload.get("stream") or "").strip().strip("/")
    stream_url = str(hook_payload.get("stream_url") or "").strip().strip("/")
    if not app or not stream:
        parts = [part for part in stream_url.split("/") if part]
        if len(parts) >= 2:
            app = app or parts[-2]
            stream = stream or parts[-1]
    if not stream:
        raise ValueError("media stream offline hook stream is required")

    device_id = stream.rsplit("/", 1)[-1]
    stream_id = f"{app}/{stream}" if app else stream
    protocol = "rtmp"
    edge_current = DeviceAccessStateCurrent.query.filter_by(
        device_id=device_id,
        protocol="edge_agent",
    ).one_or_none()
    if edge_current is not None and edge_current.state in {"registering", "registered", "stream_online", "play_ready", "ai_ready"}:
        protocol = "edge_agent"

    source_prefix = "zlm" if source_event.startswith("zlm.") else "srs"
    source_label = source_prefix.upper()
    hook_label = "stream changed" if source_event == "zlm.on_stream_changed" else "unpublish"
    return record_device_access_event(
        device_id=device_id,
        protocol=protocol,
        state="error",
        reason_code=f"{source_prefix}_stream_offline",
        reason_message=f"{source_label} {hook_label} hook reported stream offline",
        source_event=source_event,
        stream_id=stream_id,
        node_id=node_id,
        tenant_id=tenant_id,
        commit=commit,
    )


def _stream_id_from_play_url(play_url: Optional[str]) -> Optional[str]:
    parsed = urlparse((play_url or "").strip())
    path = parsed.path.strip("/")
    if not path:
        return None
    for suffix in (".flv", ".m3u8"):
        if path.lower().endswith(suffix):
            path = path[:-len(suffix)]
            break
    return path or None


def _infer_play_protocol(play_url: Optional[str], protocol: Optional[str]) -> str:
    explicit = (protocol or "").strip().lower()
    if explicit:
        return explicit

    parsed = urlparse((play_url or "").strip())
    scheme = parsed.scheme.lower()
    path = parsed.path.lower()
    query = parsed.query.lower()
    if scheme == "rtmp":
        return "rtmp"
    if scheme == "rtsp":
        return "rtsp"
    if scheme in {"webrtc", "rtc"} or "webrtc" in path or "rtc" in query:
        return "webrtc"
    if scheme in {"http", "https", "ws", "wss"}:
        return "http_flv"
    return "http_flv"


def record_device_play_ready(
    *,
    device_id: str,
    play_url: Optional[str] = None,
    protocol: Optional[str] = None,
    stream_id: Optional[str] = None,
    node_id: Optional[int] = None,
    tenant_id: Optional[str] = None,
    ai: bool = False,
    reason_code: Optional[str] = None,
    reason_message: Optional[str] = None,
    source_event: Optional[str] = None,
    commit: bool = True,
) -> Dict[str, Any]:
    clean_protocol = _infer_play_protocol(play_url, protocol)
    label = PROTOCOL_LABEL.get(clean_protocol, clean_protocol.upper())
    state = "ai_ready" if ai else "play_ready"
    reason_suffix = "ai_ready" if ai else "play_ready"
    message_subject = f"{label} AI playback URL" if ai else f"{label} playback URL"
    return record_device_access_event(
        device_id=device_id,
        protocol=clean_protocol,
        state=state,
        reason_code=reason_code or f"{clean_protocol}_{reason_suffix}",
        reason_message=reason_message or f"{message_subject} is ready",
        source_event=source_event or "play.probe.ready",
        stream_id=stream_id or _stream_id_from_play_url(play_url),
        node_id=node_id,
        tenant_id=tenant_id,
        commit=commit,
    )


def record_device_play_error(
    *,
    device_id: str,
    protocol: str,
    reason_code: Optional[str] = None,
    reason_message: Optional[str] = None,
    source_event: Optional[str] = None,
    stream_id: Optional[str] = None,
    node_id: Optional[int] = None,
    tenant_id: Optional[str] = None,
    commit: bool = True,
) -> Dict[str, Any]:
    clean_protocol = (protocol or "").strip().lower()
    label = PROTOCOL_LABEL.get(clean_protocol, clean_protocol.upper() or "Playback")
    return record_device_access_event(
        device_id=device_id,
        protocol=clean_protocol,
        state="error",
        reason_code=reason_code or f"{clean_protocol}_play_error",
        reason_message=reason_message or f"{label} playback is not ready",
        source_event=source_event or "play.probe.error",
        stream_id=stream_id,
        node_id=node_id,
        tenant_id=tenant_id,
        commit=commit,
    )


def get_device_access_summary(device_id: str) -> Dict[str, Any]:
    clean_device_id = (device_id or "").strip()
    if not clean_device_id:
        raise ValueError("device_id is required")

    rows = DeviceAccessStateCurrent.query.filter_by(device_id=clean_device_id).all()
    if not rows:
        return {
            "state": "pending_config",
            "reason_code": "missing_access_state",
            "reason_message": "Device access has not reported state yet",
            "play_ready": False,
            "ai_ready": False,
            "last_transition_time": None,
            "protocols": [],
        }

    selected = max(rows, key=lambda row: STATE_PRIORITY.get(row.state, -1))
    ai_ready = any(row.state == "ai_ready" for row in rows)
    play_ready = ai_ready or any(row.state in {"play_ready", "ai_ready"} for row in rows)
    return {
        "state": selected.state,
        "reason_code": selected.reason_code,
        "reason_message": selected.reason_message,
        "play_ready": play_ready,
        "ai_ready": ai_ready,
        "last_transition_time": _format_time(selected.last_transition_time),
        "protocols": [row.protocol for row in rows],
    }


def _event_to_dict(row: Any) -> Dict[str, Any]:
    to_dict = getattr(row, "to_dict", None)
    if callable(to_dict):
        return to_dict()
    return {
        "id": getattr(row, "id", None),
        "device_id": getattr(row, "device_id", None),
        "protocol": getattr(row, "protocol", None),
        "state": getattr(row, "state", None),
        "reason_code": getattr(row, "reason_code", None),
        "reason_message": getattr(row, "reason_message", None),
        "source_event": getattr(row, "source_event", None),
        "event_time": _format_time(getattr(row, "event_time", None)),
        "stream_id": getattr(row, "stream_id", None),
        "node_id": getattr(row, "node_id", None),
        "tenant_id": getattr(row, "tenant_id", None),
        "created_at": _format_time(getattr(row, "created_at", None)),
    }


def list_device_access_events(
    device_id: str,
    *,
    limit: int = 20,
    protocol: Optional[str] = None,
    tenant_id: Optional[str] = None,
) -> Dict[str, Any]:
    clean_device_id = (device_id or "").strip()
    if not clean_device_id:
        raise ValueError("device_id is required")

    try:
        clean_limit = int(limit)
    except (TypeError, ValueError):
        clean_limit = 20
    clean_limit = max(1, min(clean_limit, 100))

    query = DeviceAccessStateEvent.query.filter_by(device_id=clean_device_id)
    clean_protocol = (protocol or "").strip().lower()
    if clean_protocol:
        if clean_protocol not in ACCESS_PROTOCOLS:
            raise ValueError(f"unsupported access protocol: {protocol}")
        query = query.filter_by(protocol=clean_protocol)

    clean_tenant_id = (tenant_id or "").strip()
    if clean_tenant_id:
        query = query.filter_by(tenant_id=clean_tenant_id)

    rows = (
        query
        .order_by(DeviceAccessStateEvent.event_time.desc())
        .limit(clean_limit)
        .all()
    )
    return {
        "device_id": clean_device_id,
        "limit": clean_limit,
        "events": [_event_to_dict(row) for row in rows],
    }


def _age_seconds(now: datetime, transition_time: Any) -> Optional[int]:
    if transition_time is None:
        return None
    try:
        return max(0, int((now - transition_time).total_seconds()))
    except TypeError:
        return None


def _health_alert(
    row: Any,
    *,
    severity: str,
    reason_code: Optional[str],
    reason_message: Optional[str],
    age_seconds: Optional[int],
) -> Dict[str, Any]:
    return {
        "device_id": getattr(row, "device_id", None),
        "protocol": getattr(row, "protocol", None),
        "state": getattr(row, "state", None),
        "severity": severity,
        "reason_code": reason_code,
        "reason_message": reason_message,
        "source_event": getattr(row, "source_event", None),
        "last_transition_time": _format_time(getattr(row, "last_transition_time", None)),
        "stream_id": getattr(row, "stream_id", None),
        "node_id": getattr(row, "node_id", None),
        "tenant_id": getattr(row, "tenant_id", None),
        "age_seconds": age_seconds,
    }


def get_device_access_health_snapshot(
    *,
    now: Optional[datetime] = None,
    stale_after_seconds: int = 180,
    tenant_id: Optional[str] = None,
) -> Dict[str, Any]:
    snapshot_time = now or datetime.utcnow()
    query = DeviceAccessStateCurrent.query
    clean_tenant_id = (tenant_id or "").strip()
    rows = query.filter_by(tenant_id=clean_tenant_id).all() if clean_tenant_id else query.all()

    state_counts: Dict[str, int] = {}
    alerts = []
    for row in rows:
        state = getattr(row, "state", None)
        if state:
            state_counts[state] = state_counts.get(state, 0) + 1

        age = _age_seconds(snapshot_time, getattr(row, "last_transition_time", None))
        if state == "error":
            alerts.append(_health_alert(
                row,
                severity="critical",
                reason_code=getattr(row, "reason_code", None) or "device_access_error",
                reason_message=getattr(row, "reason_message", None) or "Device access entered error state",
                age_seconds=age,
            ))
        elif state in STUCK_STATES and age is not None and age >= stale_after_seconds:
            alerts.append(_health_alert(
                row,
                severity="warning",
                reason_code=f"{state}_stale",
                reason_message=(
                    f"Device access stayed in {state} for {age}s "
                    "without stream_online/play_ready"
                ),
                age_seconds=age,
            ))

    return {
        "total": len(rows),
        "state_counts": state_counts,
        "alert_count": len(alerts),
        "alerts": alerts,
        "stale_after_seconds": stale_after_seconds,
        "snapshot_time": _format_time(snapshot_time),
    }
