"""EDGE runtime：当 ALGO_BUS_TRANSPORT=mqtt 时，将告警发到算法总线。"""
from __future__ import annotations

import json
import logging
import os
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional, Tuple

logger = logging.getLogger('edge.algo_mqtt_bus')


def _brokers() -> list[Tuple[str, int]]:
    raw = os.getenv('MQTT_BROKER_URLS') or ''
    parts = [p.strip() for p in raw.split(',') if p.strip()]
    out: list[Tuple[str, int]] = []
    for p in parts:
        p = p.replace('tcp://', '').replace('mqtt://', '')
        if ':' in p:
            h, _, s = p.rpartition(':')
            try:
                out.append((h, int(s)))
            except ValueError:
                out.append((p, 1883))
        else:
            out.append((p, 1883))
    return out


def bus_enabled() -> bool:
    return (os.getenv('ALGO_BUS_TRANSPORT') or '').strip().lower() == 'mqtt'


def publish_alert(alert_data: Dict[str, Any], *, snapshot: bool = False) -> bool:
    if not bus_enabled():
        return False
    try:
        import paho.mqtt.client as mqtt
    except ImportError:
        logger.warning('paho-mqtt 未安装，无法 MQTT 发告警')
        return False

    brokers = _brokers()
    if not brokers:
        logger.warning('MQTT_BROKER_URLS 为空，跳过 MQTT 告警')
        return False

    topic = 'mqtt/iot-snapshot-alert' if snapshot else 'mqtt/iot-alert-notification'
    tenant = os.getenv('MQTT_ALGO_TENANT') or 'default'
    payload = dict(alert_data or {})
    payload.setdefault('task_type', 'snap' if snapshot else 'realtime')
    # sink 维度字段
    if os.getenv('EDGE_EDGE_NODE_ID'):
        payload.setdefault('edge_node_id', int(os.environ['EDGE_EDGE_NODE_ID']))
        payload.setdefault('edgeNodeId', int(os.environ['EDGE_EDGE_NODE_ID']))
    if os.getenv('EDGE_NODE_NAME'):
        payload.setdefault('edge_node_name', os.environ['EDGE_NODE_NAME'])
    if os.getenv('EDGE_NODE_HOST'):
        payload.setdefault('edge_node_host', os.environ['EDGE_NODE_HOST'])

    envelope = {
        'version': '1.0',
        'msgId': str(uuid.uuid4()),
        'msgType': 'alert.snapshot' if snapshot else 'alert.notification',
        'tenant': tenant,
        'ts': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z',
        'payload': payload,
    }
    body = json.dumps(envelope, ensure_ascii=False)
    username = os.getenv('MQTT_ALGO_USERNAME') or ''
    password = os.getenv('MQTT_ALGO_PASSWORD') or ''
    client_id = (os.getenv('MQTT_ALGO_CLIENT_ID') or 'algo-edge-alert') + '-pub-' + uuid.uuid4().hex[:8]

    last_err: Optional[Exception] = None
    for host, port in brokers:
        try:
            client = mqtt.Client(client_id=client_id, clean_session=True)
            if username:
                client.username_pw_set(username, password)
            client.connect(host, port, keepalive=30)
            info = client.publish(topic, body, qos=1)
            info.wait_for_publish(timeout=5)
            client.disconnect()
            logger.info('MQTT 告警已发布 topic=%s broker=%s:%s device=%s', topic, host, port, payload.get('device_id'))
            return True
        except Exception as exc:
            last_err = exc
            logger.warning('MQTT 告警发布失败 %s:%s — %s', host, port, exc)
    if last_err:
        logger.error('MQTT 告警全部 broker 失败: %s', last_err)
    return False
