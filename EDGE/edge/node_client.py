"""通过 NODE（iot-node）领取动态分配信息。"""
from __future__ import annotations

import hashlib
import logging
import platform
import socket
from typing import Any, Dict, Optional

import requests

from edge.config import admin_api_base, load_env, load_state, normalize_node_url

logger = logging.getLogger('edge.node')

REQUEST_TIMEOUT = 60


def _detect_host_ip() -> str:
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return socket.gethostbyname(socket.gethostname())


def machine_fingerprint() -> str:
    raw = '|'.join(
        [
            platform.node(),
            platform.system(),
            platform.machine(),
            _detect_host_ip(),
        ]
    )
    return 'sha256:' + hashlib.sha256(raw.encode('utf-8')).hexdigest()


def _post(path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    env = load_env()
    node_url = env.get('EDGE_NODE_URL') or ''
    if not node_url:
        raise RuntimeError('未配置 EDGE_NODE_URL，请先执行: python -m edge config set-node <url>')
    url = f'{admin_api_base(node_url)}{path}'
    resp = requests.post(url, json=payload, timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    if data.get('code') not in (0, None):
        raise RuntimeError(data.get('msg') or data.get('message') or f'NODE API 失败: {url}')
    return data.get('data') or {}


def enroll(
    *,
    node_role: str = 'compute',
    max_task_count: int = 1,
    join_token: Optional[str] = None,
) -> Dict[str, Any]:
    env = load_env()
    token = join_token if join_token is not None else env.get('EDGE_JOIN_TOKEN', '')
    payload = {
        'hostname': platform.node(),
        'fingerprint': machine_fingerprint(),
        'host': _detect_host_ip(),
        'joinToken': token or None,
        'osInfo': f'{platform.system()} {platform.release()}',
        'agentVersion': 'edge-0.1.0',
        'nodeRole': node_role,
        'maxTaskCount': max_task_count,
        'capabilities': {
            'algorithm_realtime': True,
            'algorithm_snap': True,
            'algorithm_patrol': True,
            'edge_runtime': True,
        },
    }
    logger.info('enroll → %s host=%s', normalize_node_url(env['EDGE_NODE_URL']), payload['host'])
    return _post('/node/edge/enroll', payload)


def pull_runtime_config(
    node_id: Optional[int] = None,
    agent_token: Optional[str] = None,
) -> Dict[str, Any]:
    env = load_env()
    state = load_state()
    nid = node_id if node_id is not None else state.get('nodeId') or env.get('EDGE_NODE_ID')
    token = agent_token or state.get('agentToken') or env.get('EDGE_AGENT_TOKEN')
    if not nid or not token:
        raise RuntimeError('缺少 nodeId/agentToken，请先 enroll')
    return _post(
        '/node/edge/runtime-config',
        {
            'nodeId': int(nid),
            'agentToken': str(token),
            'fingerprint': machine_fingerprint(),
        },
    )
