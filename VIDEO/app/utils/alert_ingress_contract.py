"""告警入站契约 v1：各告警通道（MQTT 算法总线、HTTP hook、Kafka、直连落库）共用的身份字段规则。

幂等键：按 correlation_id → correlationId → event_id → eventId 取第一个非空值（去首尾空白）。
alert 表以 uq_alert_correlation_id 唯一约束去重，因此同一条告警无论经哪条通道、重试几次，
都必须携带同一个 correlation_id。进入系统的第一个边界（总线发布、HTTP hook）负责在缺失时补键，
之后各跳只透传、不另造。共享样例见 testdata/contracts/alert_*.json。
"""
from __future__ import annotations

import uuid
from typing import Any, Dict, List, Mapping, Optional

ALERT_IDEMPOTENCY_KEY_FIELDS = ('correlation_id', 'correlationId', 'event_id', 'eventId')
# 与 alert.correlation_id varchar(36) 一致；超长的键在落库时必然失败
ALERT_IDEMPOTENCY_KEY_MAX_LEN = 36


def _clean(value: Any) -> Optional[str]:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def resolve_alert_idempotency_key(data: Optional[Mapping[str, Any]]) -> Optional[str]:
    """解析告警的统一幂等键；全部缺失时返回 None。"""
    if not isinstance(data, Mapping):
        return None
    for field in ALERT_IDEMPOTENCY_KEY_FIELDS:
        key = _clean(data.get(field))
        if key:
            return key
    return None


def resolve_alert_event_id(data: Optional[Mapping[str, Any]]) -> Optional[str]:
    """解析生产者显式给出的 event_id（不回落 correlation_id）。"""
    if not isinstance(data, Mapping):
        return None
    return _clean(data.get('event_id')) or _clean(data.get('eventId'))


def ensure_alert_idempotency_key(data: Dict[str, Any]) -> str:
    """保证告警带幂等键：已有则规范写回 correlation_id，缺失则补 UUID。会修改传入的字典。"""
    key = resolve_alert_idempotency_key(data)
    if not key:
        key = str(uuid.uuid4())
    data['correlation_id'] = key
    return key


def validate_alert_ingress(data: Optional[Mapping[str, Any]]) -> List[str]:
    """返回告警入站契约问题列表；空列表表示通过。只拦截落库时必然失败的输入。"""
    if not isinstance(data, Mapping):
        return ['payload_not_object']
    problems = []
    if not _clean(data.get('device_id') or data.get('deviceId')):
        problems.append('device_id_missing')
    key = resolve_alert_idempotency_key(data)
    if key and len(key) > ALERT_IDEMPOTENCY_KEY_MAX_LEN:
        problems.append('correlation_id_too_long')
    return problems


def apply_alert_identity_fields(message: Dict[str, Any], data: Mapping[str, Any]) -> Dict[str, Any]:
    """向 Kafka AlertNotificationMessage 写入身份字段（correlationId / eventId / schemaVersion）。"""
    key = resolve_alert_idempotency_key(data)
    if key:
        message['correlationId'] = key
    message['schemaVersion'] = data.get('schema_version') or data.get('schemaVersion') or 1
    message['eventId'] = resolve_alert_event_id(data) or key
    return message
