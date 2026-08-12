#!/usr/bin/env python3
"""yFeiEye 紧凑文本私有协议（EA|…）— 设备侧编解码。

与产品管理「协议脚本」模板 compact_text / ProductScriptTemplates.COMPACT_TEXT 对齐：

上行设备 → 平台:
  EA|UP|<tenantId>|<requestId>|<TYPE>|<k=v;k=v>
  TYPE: PROP | EVENT | LOG | SVC_ACK | DESIRED_ACK

下行平台 → 设备（由 iot-sink protocolToRawData 产出）:
  EA|DN|<requestId>|<TYPE>|<identifier>|<payload>
  TYPE: SVC | SET
"""

from __future__ import annotations

import json
import uuid
from typing import Any, Dict, Optional, Tuple


def new_request_id() -> str:
    return uuid.uuid4().hex[:16]


def encode_kv(params: Dict[str, Any]) -> str:
    parts = []
    for k, v in params.items():
        if v is None:
            continue
        parts.append(f"{k}={v}")
    return ";".join(parts)


def decode_kv(text: str) -> Dict[str, str]:
    out: Dict[str, str] = {}
    if not text:
        return out
    for pair in text.split(";"):
        if not pair or "=" not in pair:
            continue
        k, v = pair.split("=", 1)
        out[k] = v
    return out


def encode_uplink(
    *,
    tenant_id: int,
    msg_type: str,
    params: Dict[str, Any],
    request_id: Optional[str] = None,
) -> bytes:
    """编码上行私有帧（UTF-8 bytes）。"""
    rid = request_id or new_request_id()
    line = f"EA|UP|{tenant_id}|{rid}|{msg_type}|{encode_kv(params)}"
    return line.encode("utf-8")


def encode_property_report(
    *,
    tenant_id: int,
    params: Dict[str, Any],
    request_id: Optional[str] = None,
) -> bytes:
    return encode_uplink(
        tenant_id=tenant_id, msg_type="PROP", params=params, request_id=request_id
    )


def encode_service_ack(
    *,
    tenant_id: int,
    request_id: str,
    params: Optional[Dict[str, Any]] = None,
) -> bytes:
    body = dict(params or {})
    body.setdefault("result", "ok")
    body.setdefault("msg", "demo ok")
    return encode_uplink(
        tenant_id=tenant_id, msg_type="SVC_ACK", params=body, request_id=request_id
    )


def encode_desired_ack(
    *,
    tenant_id: int,
    request_id: str,
    params: Optional[Dict[str, Any]] = None,
) -> bytes:
    body = dict(params or {})
    body.setdefault("msg", "demo ok")
    return encode_uplink(
        tenant_id=tenant_id, msg_type="DESIRED_ACK", params=body, request_id=request_id
    )


def decode_downlink(payload: bytes) -> Optional[Dict[str, Any]]:
    """解析下行 EA|DN|…；非本协议返回 None。"""
    try:
        text = payload.decode("utf-8")
    except UnicodeDecodeError:
        return None
    if not text.startswith("EA|DN|"):
        return None
    parts = text.split("|", 5)
    # EA DN requestId TYPE identifier payload
    if len(parts) < 6:
        return None
    request_id, msg_type, identifier, raw_payload = parts[2], parts[3], parts[4], parts[5]
    parsed_payload: Any
    try:
        parsed_payload = json.loads(raw_payload)
    except Exception:
        parsed_payload = decode_kv(raw_payload) if "=" in raw_payload else raw_payload
    return {
        "raw": text,
        "requestId": request_id,
        "type": msg_type,
        "identifier": identifier,
        "payload": parsed_payload,
    }


def try_decode_any(payload: bytes) -> Tuple[str, Any]:
    """返回 (kind, data)。kind: ea_down | json | text | hex"""
    down = decode_downlink(payload)
    if down is not None:
        return "ea_down", down
    try:
        text = payload.decode("utf-8")
        if text.strip().startswith("{"):
            return "json", json.loads(text)
        return "text", text
    except Exception:
        return "hex", payload.hex()
