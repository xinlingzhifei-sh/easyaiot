"""命令方式下发 task.cmd：发布到 MQTT，或本机直接拉起（--local）。"""
from __future__ import annotations

import json
import logging
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

from edge import workload_runner
from edge.config import load_env, load_state, srs_env_from_local

logger = logging.getLogger('edge.task')

CMD_TOPIC = 'mqtt/iot-algo-task-cmd'


def _parse_brokers(urls: Any) -> List[tuple]:
    if not urls:
        return []
    if isinstance(urls, str):
        parts = [p.strip() for p in urls.split(',') if p.strip()]
    else:
        parts = [str(x).strip() for x in urls if str(x).strip()]
    out: List[tuple] = []
    for p in parts:
        p = p.replace('tcp://', '').replace('mqtt://', '')
        if ':' in p:
            host, _, port_s = p.rpartition(':')
            out.append((host, int(port_s)))
        else:
            out.append((p, 1883))
    return out


def _envelope(msg_type: str, tenant: str, payload: Dict[str, Any], correlation_id: Optional[str] = None) -> str:
    body: Dict[str, Any] = {
        'version': '1.0',
        'msgId': str(uuid.uuid4()),
        'msgType': msg_type,
        'tenant': tenant,
        'ts': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%f')[:-3] + 'Z',
        'payload': payload,
    }
    if correlation_id:
        body['correlationId'] = correlation_id
    return json.dumps(body, ensure_ascii=False)


def resolve_local_node_id() -> int:
    state = load_state()
    env = load_env()
    raw = state.get('nodeId') or env.get('EDGE_NODE_ID') or 0
    return int(raw or 0)


def build_runtime_env(node_id: Optional[int] = None) -> Dict[str, str]:
    """与 EdgeMqttRuntime._runtime_env 对齐，供 --local 直拉进程。"""
    state = load_state()
    rt = state.get('runtimeConfig') or {}
    nid = int(node_id or resolve_local_node_id() or 0)
    brokers = rt.get('mqttBrokerUrls') or []
    if isinstance(brokers, list):
        broker_s = ','.join(str(x) for x in brokers)
    else:
        broker_s = str(brokers or load_env().get('MQTT_BROKER_URLS') or '')
    env = {
        'MQTT_BROKER_URLS': broker_s,
        'MQTT_ALGO_TENANT': str(rt.get('mqttAlgoTenant') or load_env().get('MQTT_ALGO_TENANT') or 'default'),
        'MQTT_ALGO_USERNAME': str(rt.get('mqttUsername') or load_env().get('MQTT_ALGO_USERNAME') or ''),
        'MQTT_ALGO_PASSWORD': str(rt.get('mqttPassword') or load_env().get('MQTT_ALGO_PASSWORD') or ''),
        'MQTT_ALGO_CLIENT_ID': str(
            rt.get('mqttClientId') or load_env().get('MQTT_ALGO_CLIENT_ID') or f'algo-edge-{nid}'
        ),
        'ALERT_IMAGES_DIR': str(rt.get('alertImagesDir') or load_env().get('ALERT_IMAGES_DIR') or ''),
        'MEDIA_HOST_DATA_ROOT': str(rt.get('mediaHostDataRoot') or load_env().get('MEDIA_HOST_DATA_ROOT') or ''),
        'MEDIA_SNAP_DIR': str(rt.get('mediaSnapDir') or load_env().get('MEDIA_SNAP_DIR') or ''),
        'EDGE_NODE_ID': str(nid),
        'EDGE_EDGE_NODE_ID': str(
            rt.get('edgeNodeId') or state.get('edgeNodeId') or load_env().get('EDGE_EDGE_NODE_ID') or ''
        ),
        'EDGE_NODE_NAME': str(rt.get('edgeNodeName') or ''),
        'EDGE_NODE_HOST': str(rt.get('edgeNodeHost') or ''),
        'ALGO_MEDIA_REF_MODE': 'shared_fs',
        'ALGO_UPLOAD_MINIO_SYNC': 'false',
        'ALGO_BUS_TRANSPORT': 'mqtt',
    }
    env.update(srs_env_from_local())
    return env


def parse_env_pairs(pairs: Optional[List[str]]) -> Dict[str, str]:
    out: Dict[str, str] = {}
    for raw in pairs or []:
        if '=' not in raw:
            raise ValueError(f'--env 需要 KEY=VALUE，收到: {raw}')
        k, _, v = raw.partition('=')
        key = k.strip()
        if not key:
            raise ValueError(f'--env 键为空: {raw}')
        out[key] = v
    return out


def build_cmd_payload(
    *,
    action: str,
    task_id: int,
    task_type: str = 'realtime',
    target_node_id: Optional[int] = None,
    deploy_env: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    nid = int(target_node_id) if target_node_id is not None else resolve_local_node_id()
    if not nid:
        raise ValueError('无法确定 targetNodeId：请先 enroll，或传入 --target-node-id')
    payload: Dict[str, Any] = {
        'action': (action or '').strip().lower(),
        'taskId': int(task_id),
        'targetNodeId': nid,
    }
    if payload['action'] in ('start', 'restart'):
        payload['taskType'] = (task_type or 'realtime').strip() or 'realtime'
        payload['deploy'] = {'env': dict(deploy_env or {})}
    return payload


def publish_task_cmd(
    payload: Dict[str, Any],
    *,
    wait_sec: float = 2.0,
) -> Dict[str, Any]:
    import paho.mqtt.client as mqtt  # 延迟导入

    state = load_state()
    env = load_env()
    rt = state.get('runtimeConfig') or {}
    tenant = str(rt.get('mqttAlgoTenant') or env.get('MQTT_ALGO_TENANT') or 'default')
    brokers = _parse_brokers(rt.get('mqttBrokerUrls') or env.get('MQTT_BROKER_URLS'))
    if not brokers:
        raise RuntimeError('mqttBrokerUrls 为空：请先 python -m edge enroll / pull-config')

    username = str(rt.get('mqttUsername') or env.get('MQTT_ALGO_USERNAME') or '')
    password = str(rt.get('mqttPassword') or env.get('MQTT_ALGO_PASSWORD') or '')
    client_id = f"algo-edge-cmd-{resolve_local_node_id() or 'x'}-{uuid.uuid4().hex[:8]}"
    body = _envelope('task.cmd', tenant, payload, correlation_id=str(uuid.uuid4()))

    last_err: Optional[Exception] = None
    for host, port in brokers:
        client = mqtt.Client(client_id=client_id, clean_session=True)
        if username:
            client.username_pw_set(username, password)
        try:
            client.connect(host, port, keepalive=30)
            client.loop_start()
            info = client.publish(CMD_TOPIC, body, qos=1)
            info.wait_for_publish(timeout=max(1.0, wait_sec))
            time.sleep(0.15)
            client.loop_stop()
            client.disconnect()
            return {
                'success': True,
                'broker': f'{host}:{port}',
                'topic': CMD_TOPIC,
                'payload': payload,
                'published': True,
            }
        except Exception as exc:
            last_err = exc
            logger.warning('发布失败 %s:%s — %s', host, port, exc)
            try:
                client.loop_stop()
                client.disconnect()
            except Exception:
                pass
    raise RuntimeError(f'全部 MQTT broker 发布失败: {last_err}')


def execute_local(payload: Dict[str, Any]) -> Dict[str, Any]:
    action = (payload.get('action') or '').strip().lower()
    task_id = int(payload.get('taskId') or 0)
    env = build_runtime_env(payload.get('targetNodeId'))
    if action == 'start':
        return workload_runner.start_task(payload, env)
    if action == 'stop':
        return workload_runner.stop_task(task_id)
    if action == 'restart':
        return workload_runner.restart_task(payload, env)
    raise ValueError(f'unknown_action:{action}')
