"""MQTT 运行时：有序 broker 列表，每次探测均从第一个开始。"""
from __future__ import annotations

import json
import logging
import threading
import time
import uuid
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Tuple

import paho.mqtt.client as mqtt

from edge import workload_runner
from edge.config import srs_env_from_local

logger = logging.getLogger('edge.mqtt')


def _parse_brokers(urls: Any) -> List[Tuple[str, int]]:
    if not urls:
        return []
    if isinstance(urls, str):
        parts = [p.strip() for p in urls.split(',') if p.strip()]
    else:
        parts = [str(x).strip() for x in urls if str(x).strip()]
    out: List[Tuple[str, int]] = []
    for p in parts:
        p = p.replace('tcp://', '').replace('mqtt://', '')
        if ':' in p:
            host, _, port_s = p.rpartition(':')
            out.append((host, int(port_s)))
        else:
            out.append((p, 1883))
    return out


def _envelope(msg_type: str, tenant: str, payload: Dict[str, Any], correlation_id: Optional[str] = None) -> str:
    body = {
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


class EdgeMqttRuntime:
    """订阅 task.cmd；按序连接 MQTT（每轮从列表头开始）。"""

    def __init__(self, runtime: Dict[str, Any], node_id: int):
        self.runtime = runtime
        self.node_id = int(node_id)
        self.tenant = runtime.get('mqttAlgoTenant') or 'default'
        self.brokers = _parse_brokers(runtime.get('mqttBrokerUrls'))
        self.username = runtime.get('mqttUsername') or ''
        self.password = runtime.get('mqttPassword') or ''
        self.client_id = runtime.get('mqttClientId') or f'algo-edge-{node_id}'
        self.cmd_topic = f'mqtt/iot-algo-task-cmd'
        self.ack_topic = f'mqtt/iot-algo-task-ack'
        self.status_topic = f'mqtt/iot-algo-task-status'
        self._client: Optional[mqtt.Client] = None
        self._stop = threading.Event()
        self._connected = threading.Event()
        self._thread: Optional[threading.Thread] = None

    def _runtime_env(self) -> Dict[str, str]:
        rt = self.runtime
        brokers = rt.get('mqttBrokerUrls') or []
        if isinstance(brokers, list):
            broker_s = ','.join(str(x) for x in brokers)
        else:
            broker_s = str(brokers)
        env = {
            'MQTT_BROKER_URLS': broker_s,
            'MQTT_ALGO_TENANT': str(rt.get('mqttAlgoTenant') or 'default'),
            'MQTT_ALGO_USERNAME': str(rt.get('mqttUsername') or ''),
            'MQTT_ALGO_PASSWORD': str(rt.get('mqttPassword') or ''),
            'MQTT_ALGO_CLIENT_ID': str(rt.get('mqttClientId') or self.client_id),
            'ALERT_IMAGES_DIR': str(rt.get('alertImagesDir') or ''),
            'MEDIA_HOST_DATA_ROOT': str(rt.get('mediaHostDataRoot') or ''),
            'MEDIA_SNAP_DIR': str(rt.get('mediaSnapDir') or ''),
            'EDGE_NODE_ID': str(self.node_id),
            'EDGE_EDGE_NODE_ID': str(self.runtime.get('edgeNodeId') or ''),
            'EDGE_NODE_NAME': str(self.runtime.get('edgeNodeName') or ''),
            'EDGE_NODE_HOST': str(self.runtime.get('edgeNodeHost') or ''),
            'ALGO_MEDIA_REF_MODE': 'shared_fs',
            'ALGO_UPLOAD_MINIO_SYNC': 'false',
            'ALGO_BUS_TRANSPORT': 'mqtt',
        }
        # 现场 set-srs 选定的推流目标（多 SRS 时手动指定，不依赖 POD_IP 本机约定）
        env.update(srs_env_from_local())
        return env

    def _try_connect_ordered(self) -> bool:
        """每一轮探测都从 brokers[0] 开始。"""
        if not self.brokers:
            logger.error('mqttBrokerUrls 为空，无法连接')
            return False
        delay = 1.0
        while not self._stop.is_set():
            for host, port in self.brokers:
                if self._stop.is_set():
                    return False
                client = mqtt.Client(client_id=self.client_id, clean_session=False)
                if self.username:
                    client.username_pw_set(self.username, self.password)
                client.on_connect = self._on_connect
                client.on_message = self._on_message
                client.on_disconnect = self._on_disconnect
                try:
                    logger.info('尝试连接 MQTT %s:%s （有序探测，列表头优先）', host, port)
                    client.connect(host, port, keepalive=60)
                    client.loop_start()
                    if self._connected.wait(timeout=8):
                        self._client = client
                        logger.info('已连接 MQTT %s:%s', host, port)
                        return True
                    client.loop_stop()
                    client.disconnect()
                except Exception as exc:
                    logger.warning('连接失败 %s:%s — %s', host, port, exc)
                    try:
                        client.loop_stop()
                    except Exception:
                        pass
                self._connected.clear()
            logger.warning('本轮全部 broker 不可用，%.1fs 后从列表头重试', delay)
            self._stop.wait(delay)
            delay = min(30.0, delay * 2)
        return False

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            client.subscribe(self.cmd_topic, qos=1)
            self._connected.set()
            self._publish_status('running' if workload_runner._procs else 'stopped')
        else:
            logger.error('MQTT on_connect rc=%s', rc)

    def _on_disconnect(self, client, userdata, rc):
        self._connected.clear()
        if not self._stop.is_set():
            logger.warning('MQTT 断开 rc=%s，将重新从列表头探测', rc)

    def _on_message(self, client, userdata, msg):
        try:
            data = json.loads(msg.payload.decode('utf-8'))
        except Exception:
            logger.warning('非法 MQTT JSON topic=%s', msg.topic)
            return
        payload = data.get('payload') if isinstance(data.get('payload'), dict) else data
        target = payload.get('targetNodeId')
        if target is not None and int(target) != self.node_id:
            return
        action = (payload.get('action') or '').strip().lower()
        corr = data.get('correlationId')
        task_id = payload.get('taskId')
        logger.info('收到 cmd action=%s taskId=%s', action, task_id)
        try:
            if action == 'start':
                result = workload_runner.start_task(payload, self._runtime_env())
            elif action == 'stop':
                result = workload_runner.stop_task(int(task_id))
            elif action == 'restart':
                result = workload_runner.restart_task(payload, self._runtime_env())
            else:
                result = {'success': False, 'reason': f'unknown_action:{action}'}
        except Exception as exc:
            logger.exception('执行 cmd 失败')
            result = {'success': False, 'reason': str(exc)}
        self._publish_ack(action, task_id, result, corr)
        self._publish_status('running' if result.get('success') and action != 'stop' else 'stopped', reason=result.get('reason'))

    def _publish(self, topic: str, body: str, qos: int = 1, retain: bool = False):
        if not self._client:
            return
        self._client.publish(topic, body, qos=qos, retain=retain)

    def _publish_ack(self, action: str, task_id: Any, result: Dict[str, Any], corr: Optional[str]):
        payload = {
            'action': action,
            'taskId': task_id,
            'success': bool(result.get('success')),
            'nodeId': self.node_id,
            'processId': result.get('processId'),
            'reason': result.get('reason'),
        }
        self._publish(self.ack_topic, _envelope('task.ack', self.tenant, payload, corr), qos=1)

    def _publish_status(self, run_status: str, reason: Optional[str] = None):
        payload = {
            'taskId': None,
            'runStatus': run_status,
            'nodeId': self.node_id,
            'reason': reason,
        }
        self._publish(self.status_topic, _envelope('task.status', self.tenant, payload), qos=1, retain=True)

    def run_forever(self):
        self._stop.clear()
        while not self._stop.is_set():
            self._connected.clear()
            if not self._try_connect_ordered():
                break
            # 已连接：等待断开或 stop
            while not self._stop.is_set() and self._connected.is_set():
                self._stop.wait(1.0)
            if self._client:
                try:
                    self._client.loop_stop()
                    self._client.disconnect()
                except Exception:
                    pass
                self._client = None
            if not self._stop.is_set():
                # 断线后下一轮依旧从列表头开始
                time.sleep(1.0)

    def stop(self):
        self._stop.set()
        self._connected.clear()
