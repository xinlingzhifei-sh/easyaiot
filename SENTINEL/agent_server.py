"""
Agent HTTP 服务：接收控制面下发的部署/停止指令。
"""
import hmac
import logging
import os
import re
from typing import List

from flask import Flask, jsonify, request

from workload_manager import WorkloadManager, find_available_port
from media_manager import MediaStackManager
from mqtt_manager import MqttStackManager
from camera_access import discover as camera_discover
from camera_access import nvr_channels as camera_nvr_channels
from camera_access import probe_onvif as camera_probe_onvif
from camera_access import probe_stream as camera_probe_stream
from camera_access import scan_segment as camera_scan_segment

logger = logging.getLogger('easyaiot-sentinel.server')

AGENT_TOKEN = os.environ.get('AGENT_TOKEN', '')
# 可写多个地址（逗号或空格分隔），如 "10.0.0.5,127.0.0.1"；iot-node 经 SSH 探测 127.0.0.1 健康检查，收紧时须保留回环地址
AGENT_LISTEN_HOST = os.environ.get('AGENT_LISTEN_HOST', '0.0.0.0')
AGENT_LISTEN_PORT = int(os.environ.get('AGENT_LISTEN_PORT', '9100'))
# 控制面最长请求约 330s（摄像头网段扫描），线程过少会让并发扫描挤占部署与健康探测
AGENT_HTTP_THREADS = int(os.environ.get('AGENT_HTTP_THREADS', '16'))

app = Flask(__name__)
manager = WorkloadManager()
media_manager = MediaStackManager()
mqtt_manager = MqttStackManager()


def _ok(data=None):
    return jsonify({'code': 0, 'msg': 'success', 'data': data if data is not None else {}})


def _err(msg: str, code: int = 1):
    return jsonify({'code': code, 'msg': msg, 'data': None}), 400


def _check_token():
    token = request.headers.get('X-Agent-Token', '')
    if not AGENT_TOKEN or not token or not hmac.compare_digest(token, AGENT_TOKEN):
        return False
    return True


@app.before_request
def auth_middleware():
    if request.path in ('/health',):
        return None
    if not _check_token():
        return _err('Agent 认证失败', 401)


@app.get('/health')
def health():
    return _ok({'status': 'ok', 'cameraAccess': True})


def _camera_call(handler):
    try:
        return _ok(handler(request.get_json(force=True) or {}))
    except ValueError as e:
        return _err(str(e))
    except Exception as e:
        logger.exception('摄像头接入操作失败')
        return _err(str(e))


@app.post('/camera/discover')
def camera_discover_route():
    return _camera_call(lambda _payload: camera_discover())


@app.post('/camera/scan-segment')
def camera_scan_segment_route():
    return _camera_call(camera_scan_segment)


@app.post('/camera/probe-onvif')
def camera_probe_onvif_route():
    return _camera_call(camera_probe_onvif)


@app.post('/camera/probe-stream')
def camera_probe_stream_route():
    return _camera_call(camera_probe_stream)


@app.post('/camera/nvr-channels')
def camera_nvr_channels_route():
    return _camera_call(camera_nvr_channels)


@app.post('/workload/deploy')
def deploy_workload():
    try:
        spec = request.get_json(force=True) or {}
        if not spec.get('workloadType') or not spec.get('workloadId'):
            return _err('workloadType 和 workloadId 必填')
        env = spec.get('env') or {}
        runtime = (spec.get('runtime') or env.get('RUNTIME') or 'process').lower()
        # TRANSFORM 镜像默认从 48096 起找空闲口，同节点可多容器
        if not env.get('PORT'):
            start = int(env.get('START_PORT') or (48096 if runtime == 'docker' or spec.get('workloadType') == 'transform_runtime' else 8000))
            port = find_available_port(start)
            if port:
                env['PORT'] = str(port)
                spec['env'] = env
        # docker 模式允许 command 为空（由 Agent 组装 docker run）
        if runtime != 'docker' and not (spec.get('command') or []):
            return _err('command 不能为空')
        data = manager.deploy(spec)
        return _ok(data)
    except Exception as e:
        logger.exception('部署失败')
        return _err(str(e))


@app.post('/workload/stop')
def stop_workload():
    try:
        body = request.get_json(force=True) or {}
        workload_type = body.get('workloadType')
        workload_id = body.get('workloadId')
        if not workload_type or not workload_id:
            return _err('workloadType 和 workloadId 必填')
        removed = manager.stop(workload_type, workload_id)
        return _ok({'stopped': True, 'removed': bool(removed)})
    except Exception as e:
        logger.exception('停止失败')
        return _err(str(e))


@app.get('/workload/list')
def list_workloads():
    return _ok({'workloads': manager.list_workloads(), 'activeTasks': manager.active_count()})


@app.post('/media/deploy')
def deploy_media_stack():
    try:
        spec = request.get_json(force=True) or {}
        if not spec.get('stackType') and not spec.get('mediaType'):
            return _err('stackType 必填')
        data = media_manager.deploy(spec)
        return _ok(data)
    except Exception as e:
        logger.exception('媒体栈部署失败')
        return _err(str(e))


@app.post('/media/stop')
def stop_media_stack():
    try:
        body = request.get_json(force=True) or {}
        stack_type = body.get('stackType') or body.get('mediaType')
        if not stack_type:
            return _err('stackType 必填')
        media_manager.stop(stack_type)
        return _ok({'stopped': True})
    except Exception as e:
        logger.exception('媒体栈停止失败')
        return _err(str(e))


@app.post('/mqtt/deploy')
def deploy_mqtt_stack():
    try:
        spec = request.get_json(force=True) or {}
        data = mqtt_manager.deploy(spec)
        return _ok(data)
    except Exception as e:
        logger.exception('MQTT 网关部署失败')
        return _err(str(e))


@app.post('/mqtt/stop')
def stop_mqtt_stack():
    try:
        body = request.get_json(force=True) or {}
        stack_type = body.get('stackType') or 'emqx'
        mqtt_manager.stop(stack_type)
        return _ok({'stopped': True})
    except Exception as e:
        logger.exception('MQTT 网关停止失败')
        return _err(str(e))


@app.post('/sentinel/validate')
def sentinel_validate():
    try:
        body = request.get_json(force=True) or {}
        from run_sentinel import NODE_ID, collect_metrics
        from sentinel.validate import validate_workload

        metrics = collect_metrics()
        result = validate_workload(
            node_id=NODE_ID,
            workload_type=str(body.get('workloadType') or ''),
            requirements=body.get('requirements') or {},
            gpu_info=metrics.get('gpuInfo') or [],
            cluster_mode=bool(metrics.get('clusterMode')),
            nfs_mount_ready=bool(metrics.get('nfsMountReady') or metrics.get('cephMountReady')),
        )
        if not result.get('valid'):
            return _err(result.get('reason') or 'Sentinel 校验未通过: ' + ','.join(result.get('missingCapabilities') or []))
        return _ok(result)
    except Exception as e:
        logger.exception('Sentinel validate 失败')
        return _err(str(e))


@app.post('/sentinel/probe')
def sentinel_probe():
    try:
        body = request.get_json(force=True) or {}
        level_raw = str(body.get('level') or 'L0').upper()
        from sentinel.base import ProbeLevel
        from sentinel.orchestrator import run_sentinel_probe
        from run_sentinel import NODE_ID, collect_metrics

        level = ProbeLevel.L1 if level_raw == 'L1' else ProbeLevel.L2 if level_raw == 'L2' else ProbeLevel.L0
        metrics = collect_metrics()
        # collect_metrics 已含 L0 sentinel；按需提升探测级别
        if level != ProbeLevel.L0:
            payload = run_sentinel_probe(
                node_id=NODE_ID,
                gpu_info=metrics.get('gpuInfo') or [],
                cluster_mode=bool(metrics.get('clusterMode')),
                nfs_mount_ready=bool(metrics.get('nfsMountReady') or metrics.get('cephMountReady')),
                level=level,
            )
        else:
            payload = metrics.get('sentinel') or run_sentinel_probe(
                node_id=NODE_ID,
                gpu_info=metrics.get('gpuInfo') or [],
                cluster_mode=bool(metrics.get('clusterMode')),
                nfs_mount_ready=bool(metrics.get('nfsMountReady') or metrics.get('cephMountReady')),
                level=level,
            )
        return _ok(payload)
    except Exception as e:
        logger.exception('Sentinel 探测失败')
        return _err(str(e))


@app.get('/sentinel/profile')
def sentinel_profile():
    try:
        from run_sentinel import collect_metrics
        metrics = collect_metrics()
        return _ok(metrics.get('sentinel') or {})
    except Exception as e:
        logger.exception('Sentinel profile 读取失败')
        return _err(str(e))


def create_app():
    return app


def parse_listen_hosts(raw: str) -> List[str]:
    hosts = list(dict.fromkeys(h.strip('[]') for h in re.split(r'[,\s]+', raw or '') if h.strip('[]')))
    # 通配地址已覆盖同族所有网卡，再绑具体地址会端口冲突（Linux 下 EADDRINUSE）导致 Agent 起不来
    if '0.0.0.0' in hosts:
        hosts = [h for h in hosts if h == '0.0.0.0' or ':' in h]
    if '::' in hosts:
        hosts = [h for h in hosts if h == '::' or ':' not in h]
    return hosts or ['0.0.0.0']


def _listen_endpoint(host: str, port: int) -> str:
    return f'[{host}]:{port}' if ':' in host else f'{host}:{port}'


def run_server():
    hosts = parse_listen_hosts(AGENT_LISTEN_HOST)
    if any(h in ('0.0.0.0', '::') for h in hosts):
        logger.warning('Agent HTTP 服务监听所有网卡；可在 agent.env 设置 AGENT_LISTEN_HOST=<内网IP>,127.0.0.1 收紧')
    try:
        from waitress import serve
    except ImportError:
        # 旧节点只同步了源码未重装依赖、或控制面离线包尚未刷新时，保证 Agent 照常启动
        logger.warning('未安装 waitress，回退到 Flask 内置开发服务器（不适合生产）；'
                       '请在平台重新执行 SENTINEL/export_pip_wheels.sh 后重新部署 Agent')
        if len(hosts) > 1:
            logger.warning('Flask 内置服务器只能监听一个地址，仅使用 %s，忽略 %s', hosts[0], ','.join(hosts[1:]))
        logger.info('Agent HTTP 服务启动 %s:%s', hosts[0], AGENT_LISTEN_PORT)
        app.run(host=hosts[0], port=AGENT_LISTEN_PORT, threaded=True, use_reloader=False)
        return
    listen = ' '.join(_listen_endpoint(h, AGENT_LISTEN_PORT) for h in hosts)
    logger.info('Agent HTTP 服务启动（waitress）%s，线程数 %s', listen, AGENT_HTTP_THREADS)
    serve(app, listen=listen, threads=AGENT_HTTP_THREADS)
