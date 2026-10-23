"""
Agent HTTP 服务：接收控制面下发的部署/停止指令。
"""
import functools
import hmac
import ipaddress
import logging
import math
import os
import re
import threading
import time
from collections import deque
from typing import Callable, Deque, Dict, List, Optional, Tuple, Union

from flask import Flask, jsonify, request

from agent_commands import credentials as agent_credentials
from workload_manager import WorkloadManager, find_available_port
from media_manager import MediaStackManager
from mqtt_manager import MqttStackManager
from camera_access import discover as camera_discover
from camera_access import nvr_channels as camera_nvr_channels
from camera_access import probe_onvif as camera_probe_onvif
from camera_access import probe_stream as camera_probe_stream
from camera_access import scan_segment as camera_scan_segment

logger = logging.getLogger('easyaiot-sentinel.server')


def _env_int(name: str, default: int) -> int:
    """读取可选的整数调优项；写错时告警并用默认值，不让 Agent 因此起不来。"""
    raw = os.environ.get(name, '').strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        logger.warning('%s=%r 不是整数，使用默认值 %s', name, raw, default)
        return default
    if value < 0:
        logger.warning('%s=%s 不能为负数，使用默认值 %s', name, value, default)
        return default
    return value


AGENT_TOKEN = os.environ.get('AGENT_TOKEN', '')


def _on_agent_token_rotated(new_token: str) -> None:
    """控制面经指令通道推送新令牌后，9100 的 X-Agent-Token 校验随之切换（比较逻辑不变）。"""
    global AGENT_TOKEN
    AGENT_TOKEN = new_token


agent_credentials.add_listener(_on_agent_token_rotated)

# 可写多个地址（逗号或空格分隔），如 "10.0.0.5,127.0.0.1"；iot-node 经 SSH 探测 127.0.0.1 健康检查，收紧时须保留回环地址
AGENT_LISTEN_HOST = os.environ.get('AGENT_LISTEN_HOST', '0.0.0.0')
AGENT_LISTEN_PORT = int(os.environ.get('AGENT_LISTEN_PORT', '9100'))
# 控制面最长请求约 330s（摄像头网段扫描），线程过少会让并发扫描挤占部署与健康探测
AGENT_HTTP_THREADS = int(os.environ.get('AGENT_HTTP_THREADS', '16'))
# 鉴权失败限速：同一来源在窗口内失败达到阈值后，封禁期内一律返回 429。阈值为 0 时关闭
AGENT_AUTH_FAIL_LIMIT = _env_int('AGENT_AUTH_FAIL_LIMIT', 20)
AGENT_AUTH_FAIL_WINDOW = _env_int('AGENT_AUTH_FAIL_WINDOW', 60)
AGENT_AUTH_BLOCK_SECONDS = _env_int('AGENT_AUTH_BLOCK_SECONDS', 300)
# 来源白名单：IP 或 CIDR，逗号/空格分隔；为空不限制。回环地址始终放行（iot-node 经 SSH 在本机探测 /health）
AGENT_ALLOWED_SOURCES = os.environ.get('AGENT_ALLOWED_SOURCES', '')
# 摄像头网段扫描单次可达 330s，限制并发以免占满工作线程、挤掉部署与健康探测。0 表示不限
AGENT_CAMERA_SCAN_CONCURRENCY = _env_int('AGENT_CAMERA_SCAN_CONCURRENCY', 2)

IpAddress = Union[ipaddress.IPv4Address, ipaddress.IPv6Address]
IpNetwork = Union[ipaddress.IPv4Network, ipaddress.IPv6Network]


def client_address(remote_addr: Optional[str]) -> Optional[IpAddress]:
    """解析对端地址；IPv4 映射的 IPv6 地址（双栈监听时出现）还原为 IPv4，去掉 IPv6 zone 后缀。

    只信任套接字对端地址，不读 X-Forwarded-For：Agent 前面没有代理，该头可被任意伪造。
    """
    if not remote_addr:
        return None
    try:
        addr = ipaddress.ip_address(remote_addr.split('%', 1)[0])
    except ValueError:
        return None
    if addr.version == 6 and addr.ipv4_mapped:
        return addr.ipv4_mapped
    return addr


def parse_allowed_sources(raw: str) -> Tuple[List[IpNetwork], List[str]]:
    """解析来源白名单，返回 (合法网段, 无法解析的条目)。"""
    networks: List[IpNetwork] = []
    invalid: List[str] = []
    for item in re.split(r'[,\s]+', raw or ''):
        if not item:
            continue
        try:
            networks.append(ipaddress.ip_network(item, strict=False))
        except ValueError:
            invalid.append(item)
    return networks, invalid


class SourcePolicy:
    """来源白名单。未配置时全部放行；配置了却没有一条合法条目时只放行回环地址（写错时收紧而不是放开）。"""

    def __init__(self, raw: str):
        self.configured = bool((raw or '').strip())
        self.networks, self.invalid = parse_allowed_sources(raw)

    def allows(self, addr: Optional[IpAddress]) -> bool:
        if not self.configured:
            return True
        if addr is None:
            return False
        if addr.is_loopback:
            return True
        return any(addr in network for network in self.networks)


class AuthFailureLimiter:
    """按来源统计鉴权失败；窗口内失败达到阈值后封禁一段时间。

    封禁期内连携带正确令牌的请求也拒绝，否则"猜中返回 200、猜错返回 429"仍可用来试探令牌。
    成功请求不计数；状态只在内存里，Agent 重启（如 iot-node 重新部署、重写令牌）即清空。
    """

    MAX_TRACKED_SOURCES = 4096

    def __init__(self, limit: int, window_seconds: int, block_seconds: int, clock=time.monotonic):
        self.limit = limit
        self.window_seconds = window_seconds
        self.block_seconds = block_seconds
        self._clock = clock
        self._lock = threading.Lock()
        self._failures: Dict[str, Deque[float]] = {}
        self._blocked_until: Dict[str, float] = {}

    @property
    def enabled(self) -> bool:
        return self.limit > 0 and self.window_seconds > 0 and self.block_seconds > 0

    def retry_after(self, source: str) -> int:
        """仍在封禁期内时返回剩余秒数（向上取整，至少 1），否则返回 0。"""
        if not self.enabled:
            return 0
        with self._lock:
            until = self._blocked_until.get(source)
            if until is None:
                return 0
            remaining = until - self._clock()
            if remaining <= 0:
                del self._blocked_until[source]
                return 0
            return max(1, math.ceil(remaining))

    def record_failure(self, source: str) -> bool:
        """记录一次失败；本次失败触发封禁时返回 True。"""
        if not self.enabled:
            return False
        now = self._clock()
        with self._lock:
            failures = self._failures.get(source)
            if failures is None:
                self._prune(now)
                failures = self._failures[source] = deque()
            failures.append(now)
            cutoff = now - self.window_seconds
            while failures and failures[0] <= cutoff:
                failures.popleft()
            if len(failures) < self.limit:
                return False
            del self._failures[source]
            self._blocked_until[source] = now + self.block_seconds
            return True

    def _prune(self, now: float) -> None:
        """记录的来源数达到上限时清理过期条目，仍超限则丢弃最早开始计数的来源，保证内存有界。"""
        if len(self._blocked_until) >= self.MAX_TRACKED_SOURCES:
            for source in [s for s, until in self._blocked_until.items() if until <= now]:
                del self._blocked_until[source]
        if len(self._failures) < self.MAX_TRACKED_SOURCES:
            return
        cutoff = now - self.window_seconds
        for source in [s for s, failures in self._failures.items() if not failures or failures[-1] <= cutoff]:
            del self._failures[source]
        while len(self._failures) >= self.MAX_TRACKED_SOURCES:
            del self._failures[next(iter(self._failures))]


def _concurrency_slots(limit: int) -> Optional[threading.BoundedSemaphore]:
    return threading.BoundedSemaphore(limit) if limit > 0 else None


source_policy = SourcePolicy(AGENT_ALLOWED_SOURCES)
auth_limiter = AuthFailureLimiter(AGENT_AUTH_FAIL_LIMIT, AGENT_AUTH_FAIL_WINDOW, AGENT_AUTH_BLOCK_SECONDS)
camera_scan_slots = _concurrency_slots(AGENT_CAMERA_SCAN_CONCURRENCY)

app = Flask(__name__)
manager = WorkloadManager()
media_manager = MediaStackManager()
mqtt_manager = MqttStackManager()


def _ok(data=None):
    return jsonify({'code': 0, 'msg': 'success', 'data': data if data is not None else {}})


def _err(msg: str, code: int = 1):
    return jsonify({'code': code, 'msg': msg, 'data': None}), 400


def _reject(status: int, msg: str, retry_after: int = 0):
    """与 _err 同样的响应体，但用真实 HTTP 状态码（403 / 429）。"""
    response = jsonify({'code': status, 'msg': msg, 'data': None})
    response.status_code = status
    if retry_after:
        response.headers['Retry-After'] = str(retry_after)
    return response


def _check_token():
    token = request.headers.get('X-Agent-Token', '')
    if not AGENT_TOKEN or not token or not hmac.compare_digest(token, AGENT_TOKEN):
        return False
    return True


@app.before_request
def auth_middleware():
    addr = client_address(request.remote_addr)
    if not source_policy.allows(addr):
        return _reject(403, 'Agent 拒绝该来源地址访问')
    if request.path in ('/health',):
        return None
    # 回环地址不限速：本机运维脚本与 iot-node 的 SSH 探测都从这里来
    limited = auth_limiter.enabled and not (addr is not None and addr.is_loopback)
    source = str(addr) if addr is not None else (request.remote_addr or 'unknown')
    if limited:
        retry_after = auth_limiter.retry_after(source)
        if retry_after:
            return _reject(429, 'Agent 认证失败次数过多，请稍后重试', retry_after)
    if not _check_token():
        if limited and auth_limiter.record_failure(source):
            logger.warning('来源 %s 在 %ss 内鉴权失败 %s 次，封禁 %ss',
                           source, auth_limiter.window_seconds, auth_limiter.limit, auth_limiter.block_seconds)
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
    slots = camera_scan_slots
    if slots is None:
        return _camera_call(camera_scan_segment)
    if not slots.acquire(blocking=False):
        return _reject(429, '摄像头网段扫描并发已达上限，请等当前扫描结束后重试')
    try:
        return _camera_call(camera_scan_segment)
    finally:
        slots.release()


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


# 只出站模式下控制面经指令轮询通道调用的入站功能：指令类型 → (HTTP 方法, 路径)。
# 只认这张白名单，不接受控制面下发任意路径
COMMAND_ROUTES = {
    'workload.deploy': ('POST', '/workload/deploy'),
    'workload.stop': ('POST', '/workload/stop'),
    'workload.list': ('GET', '/workload/list'),
    'media.deploy': ('POST', '/media/deploy'),
    'media.stop': ('POST', '/media/stop'),
    'mqtt.deploy': ('POST', '/mqtt/deploy'),
    'mqtt.stop': ('POST', '/mqtt/stop'),
    'sentinel.probe': ('POST', '/sentinel/probe'),
    'sentinel.validate': ('POST', '/sentinel/validate'),
    'sentinel.profile': ('GET', '/sentinel/profile'),
    'camera.discover': ('POST', '/camera/discover'),
    'camera.scan-segment': ('POST', '/camera/scan-segment'),
    'camera.probe-onvif': ('POST', '/camera/probe-onvif'),
    'camera.probe-stream': ('POST', '/camera/probe-stream'),
    'camera.nvr-channels': ('POST', '/camera/nvr-channels'),
}


def invoke_route(command_type: str, payload: Optional[dict]) -> dict:
    """在进程内调用与 9100 相同的处理函数。

    请求带当前 X-Agent-Token 走完整的 before_request（令牌校验、来源白名单、并发上限），
    HTTP 与指令队列两条路径共用同一份代码，不存在绕过令牌校验的旁路。
    """
    method, path = COMMAND_ROUTES[command_type]
    with app.test_client() as client:
        response = client.open(
            path,
            method=method,
            json=(payload or {}) if method == 'POST' else None,
            headers={'X-Agent-Token': AGENT_TOKEN},
            environ_base={'REMOTE_ADDR': '127.0.0.1'},
        )
    body = response.get_json(silent=True) or {}
    if response.status_code != 200 or body.get('code') != 0:
        raise RuntimeError(body.get('msg') or f'HTTP {response.status_code}')
    return {'data': body.get('data')}


def build_route_executors() -> Dict[str, Callable[[dict], dict]]:
    return {command_type: functools.partial(invoke_route, command_type) for command_type in COMMAND_ROUTES}


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


def log_guard_settings():
    """启动时打印来源白名单、限速与并发设置，配置写错时给出明确告警。"""
    if source_policy.invalid:
        logger.error('AGENT_ALLOWED_SOURCES 中无法解析的条目已忽略：%s', ','.join(source_policy.invalid))
    if source_policy.configured and not source_policy.networks:
        logger.error('AGENT_ALLOWED_SOURCES 没有合法条目，只放行回环地址，控制面将无法访问 Agent')
    elif source_policy.configured:
        logger.info('来源白名单：%s（回环地址始终放行）', ','.join(str(n) for n in source_policy.networks))
    if auth_limiter.enabled:
        logger.info('鉴权失败限速：%ss 内失败 %s 次封禁 %ss（回环地址不限）',
                    auth_limiter.window_seconds, auth_limiter.limit, auth_limiter.block_seconds)
    else:
        logger.warning('鉴权失败限速已关闭')
    if camera_scan_slots is None:
        logger.info('摄像头网段扫描不限并发')


def run_server():
    hosts = parse_listen_hosts(AGENT_LISTEN_HOST)
    if any(h in ('0.0.0.0', '::') for h in hosts) and not source_policy.configured:
        logger.warning('Agent HTTP 服务监听所有网卡；可在 agent.env 设置 AGENT_LISTEN_HOST=<内网IP>,127.0.0.1 '
                       '或 AGENT_ALLOWED_SOURCES=<控制面出口IP> 收紧')
    log_guard_settings()
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
