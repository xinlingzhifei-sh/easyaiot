import os
from datetime import datetime, timedelta, timezone
from typing import Any, Dict

from models import Device, db
from app.services.device_access_state_service import record_device_access_event
from app.utils.media_client import allocate_device_media, enqueue_agent_command, get_agent_command_by_key


ACTIVE_COMMAND_STATUSES = {"pending", "queued", "leased", "acked", "running", "processing"}
SUCCESS_COMMAND_STATUSES = {"succeeded", "success", "completed", "done"}
FAILED_COMMAND_STATUSES = {"failed", "error", "timeout", "cancelled", "canceled"}


def _parse_command_time(value: Any) -> datetime | None:
    if isinstance(value, datetime):
        return value if value.tzinfo else value.replace(tzinfo=timezone.utc)
    text = str(value or "").strip()
    if not text:
        return None
    if text.endswith("Z"):
        text = f"{text[:-1]}+00:00"
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError:
        return None
    return parsed if parsed.tzinfo else parsed.replace(tzinfo=timezone.utc)


def _coerce_now(value: Any) -> datetime:
    parsed = _parse_command_time(value)
    return parsed or datetime.now(timezone.utc)


def _command_attempt(command: Dict[str, Any]) -> int:
    for key in ("attempt", "attempts", "retryCount"):
        try:
            value = command.get(key)
            if value not in (None, ""):
                return max(1, int(value))
        except (TypeError, ValueError):
            continue
    return 1


def _is_command_stale(command: Dict[str, Any], *, now: datetime, timeout_seconds: int) -> bool:
    lease_expires_at = _parse_command_time(command.get("leaseExpiresAt") or command.get("lease_expires_at"))
    if lease_expires_at is not None and lease_expires_at <= now:
        return True

    updated_at = _parse_command_time(
        command.get("updatedAt")
        or command.get("updated_at")
        or command.get("createdAt")
        or command.get("created_at")
    )
    return updated_at is not None and updated_at + timedelta(seconds=timeout_seconds) <= now


def ensure_edge_rtsp_forward(
    device_id: str,
    *,
    edge_node_id: int,
    transport: str = "tcp",
    state_reason_code: str = "edge_command_queued",
    state_reason_message: str = "Edge Agent stream-forward command queued",
    state_source_event: str = "stream_forward.deploy.enqueued",
) -> Dict[str, Any]:
    device = Device.query.get(device_id)
    if not device:
        raise ValueError(f"device not found: {device_id}")

    source = (device.source or "").strip()
    if not source.lower().startswith("rtsp://"):
        raise ValueError("edge rtsp forwarding requires a rtsp:// device source")
    if not edge_node_id:
        raise ValueError("edge_node_id is required")

    binding = allocate_device_media(device_id, need_srs_live=True, need_srs_ai=True, need_zlm=False)
    rtmp_stream = binding.get("rtmpStream") or device.rtmp_stream
    http_stream = binding.get("httpStream") or device.http_stream
    if not rtmp_stream:
        raise ValueError("media allocation did not return rtmpStream")

    normalized_transport = transport if transport in ("tcp", "udp") else "tcp"
    payload = {
        "deviceId": device_id,
        "rtspUrl": source,
        "rtmpPushUrl": rtmp_stream,
        "streamName": f"live/{device_id}",
        "transport": normalized_transport,
        "heartbeatUrl": os.getenv("VIDEO_EDGE_HEARTBEAT_URL", ""),
        "logDir": os.path.join(
            os.getenv("EDGE_STREAM_LOG_ROOT", "/opt/easyaiot/logs/edge-stream"),
            device_id,
        ),
    }
    command = enqueue_agent_command(
        node_id=int(edge_node_id),
        command_type="stream_forward.deploy",
        command_key=f"stream_forward:{device_id}",
        payload=payload,
    )
    record_device_access_event(
        device_id=device_id,
        protocol="edge_agent",
        state="registering",
        reason_code=state_reason_code,
        reason_message=state_reason_message,
        source_event=state_source_event,
        stream_id=f"live/{device_id}",
        node_id=int(edge_node_id),
        tenant_id=None,
        commit=False,
    )

    device.rtmp_stream = rtmp_stream
    device.http_stream = http_stream or device.http_stream
    device.ai_rtmp_stream = binding.get("aiRtmpStream") or device.ai_rtmp_stream
    device.ai_http_stream = binding.get("aiHttpStream") or device.ai_http_stream
    device.enable_forward = True
    db.session.commit()

    return {
        "deviceId": device_id,
        "edgeNodeId": edge_node_id,
        "payload": payload,
        "command": command,
    }


def reconcile_edge_rtsp_forward_command(
    device_id: str,
    *,
    edge_node_id: int,
    transport: str = "tcp",
    now: Any = None,
    timeout_seconds: int = 120,
    max_attempts: int = 3,
) -> Dict[str, Any]:
    command_key = f"stream_forward:{device_id}"
    command = get_agent_command_by_key(command_key)
    if not command:
        result = ensure_edge_rtsp_forward(device_id, edge_node_id=edge_node_id, transport=transport)
        return {"action": "queued", **result}

    status = str(command.get("status") or "").strip().lower()
    normalized_now = _coerce_now(now)
    timeout = max(10, int(timeout_seconds or 120))
    attempts = _command_attempt(command)

    if status in SUCCESS_COMMAND_STATUSES:
        record_device_access_event(
            device_id=device_id,
            protocol="edge_agent",
            state="registered",
            reason_code="edge_command_succeeded",
            reason_message="Edge Agent command succeeded; waiting for media publish hook",
            source_event="stream_forward.deploy.result",
            stream_id=f"live/{device_id}",
            node_id=int(edge_node_id),
        )
        return {"action": "registered", "command": command}

    if status in FAILED_COMMAND_STATUSES:
        record_device_access_event(
            device_id=device_id,
            protocol="edge_agent",
            state="error",
            reason_code="edge_command_failed",
            reason_message=str(command.get("error") or command.get("message") or "Edge Agent command failed"),
            source_event="stream_forward.deploy.result",
            stream_id=f"live/{device_id}",
            node_id=int(edge_node_id),
        )
        return {"action": "failed", "command": command}

    if status in ACTIVE_COMMAND_STATUSES and _is_command_stale(
        command,
        now=normalized_now,
        timeout_seconds=timeout,
    ):
        if attempts >= max(1, int(max_attempts or 1)):
            record_device_access_event(
                device_id=device_id,
                protocol="edge_agent",
                state="error",
                reason_code="edge_command_timeout",
                reason_message="Edge Agent command lease expired and retry limit was reached",
                source_event="stream_forward.deploy.timeout",
                stream_id=f"live/{device_id}",
                node_id=int(edge_node_id),
            )
            return {"action": "timeout", "command": command}

        retry = ensure_edge_rtsp_forward(
            device_id,
            edge_node_id=edge_node_id,
            transport=transport,
            state_reason_code="edge_command_retry_queued",
            state_reason_message="Edge Agent command lease expired; retry queued",
            state_source_event="stream_forward.deploy.retry",
        )
        return {
            "action": "retry_queued",
            "previousCommand": command,
            **retry,
        }

    return {"action": "waiting", "command": command}
