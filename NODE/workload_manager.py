"""
工作负载管理：在节点上启动/停止 run_deploy 等子进程。
"""
import logging
import os
import socket
import subprocess
import sys
import threading
import urllib.parse
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional

import psutil

_repo_root = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
_lib_root = os.path.join(_repo_root, '.scripts', 'lib')
for _p in (_lib_root,):
    if _p not in sys.path:
        sys.path.insert(0, _p)

logger = logging.getLogger('easyaiot-node-agent.workload')

_WORKLOAD_POLICIES = {
    'ai_service': (
        'AI_ROOT',
        {'services/ai_service/run_deploy.py'},
        {'ai_service'},
    ),
    'llm_service': (
        'AI_ROOT',
        {'services/llm_service/run_deploy.py'},
        {'llm_service'},
    ),
    'auto_label': (
        'AI_ROOT',
        {'services/auto_label_worker/run_worker.py'},
        {'auto_label'},
    ),
    'model_train': (
        'AI_ROOT',
        {'services/train_worker/run_worker.py'},
        {'model_train'},
    ),
    'algorithm_task': (
        'VIDEO_ROOT',
        {
            'services/realtime_algorithm_service/run_deploy.py',
            'services/snapshot_algorithm_service/run_deploy.py',
            'services/patrol_algorithm_service/run_deploy.py',
        },
        {'algorithm_realtime', 'algorithm_snap', 'algorithm_patrol'},
    ),
    'stream_forward': (
        'VIDEO_ROOT',
        {'services/stream_forward_service/run_deploy.py'},
        {'stream_forward'},
    ),
    'post_process': (
        'VIDEO_ROOT',
        {'services/post_process_worker/run_worker.py'},
        {'post_process'},
    ),
}

_BLOCKED_ENV_KEYS = {
    'BASH_ENV',
    'ENV',
    'GCONV_PATH',
    'IFS',
    'LOCPATH',
    'NODE_OPTIONS',
    'PATH',
    'RUBYOPT',
    'SHELLOPTS',
    'SSLKEYLOGFILE',
    'VIRTUAL_ENV',
}


@dataclass
class WorkloadRecord:
    workload_type: str
    workload_id: str
    process: Optional[subprocess.Popen]
    pid: int
    log_dir: Optional[str] = None
    command: List[str] = field(default_factory=list)
    runtime: str = 'process'  # process | docker
    container_name: Optional[str] = None


class WorkloadManager:
    def __init__(self):
        self._lock = threading.Lock()
        self._workloads: Dict[str, WorkloadRecord] = {}

    def _key(self, workload_type: str, workload_id: str) -> str:
        return f'{workload_type}:{workload_id}'

    def list_workloads(self) -> List[Dict[str, Any]]:
        with self._lock:
            result = []
            for rec in self._workloads.values():
                if rec.runtime == 'docker' and rec.container_name:
                    running = _docker_running(rec.container_name)
                else:
                    running = rec.process is not None and rec.process.poll() is None
                result.append({
                    'workloadType': rec.workload_type,
                    'workloadId': rec.workload_id,
                    'pid': rec.pid,
                    'running': running,
                    'runtime': rec.runtime,
                    'containerName': rec.container_name,
                })
            return result

    def active_count(self) -> int:
        with self._lock:
            n = 0
            for rec in self._workloads.values():
                if rec.runtime == 'docker' and rec.container_name:
                    if _docker_running(rec.container_name):
                        n += 1
                elif rec.process is not None and rec.process.poll() is None:
                    n += 1
            return n

    def deploy(self, spec: Dict[str, Any]) -> Dict[str, Any]:
        workload_type, workload_id, command, work_dir, log_dir, env_extra = (
            _validate_workload_spec(spec)
        )
        gpu_ids = spec.get('gpuIds')
        runtime = (spec.get('runtime') or env_extra.get('RUNTIME') or 'process').lower()

        key = self._key(workload_type, workload_id)
        with self._lock:
            existing = self._workloads.get(key)
            if existing:
                if existing.runtime == 'docker' and existing.container_name and _docker_running(existing.container_name):
                    raise ValueError(f'工作负载已运行: {key}')
                if existing.process is not None and existing.process.poll() is None:
                    raise ValueError(f'工作负载已运行: {key}')

        env = os.environ.copy()
        env.update({k: str(v) for k, v in env_extra.items() if v is not None})
        env['PYTHONUNBUFFERED'] = '1'
        if gpu_ids:
            env['CUDA_VISIBLE_DEVICES'] = str(gpu_ids)
            env['GPU_IDS'] = str(gpu_ids)
        if log_dir:
            env['LOG_PATH'] = log_dir
            os.makedirs(log_dir, exist_ok=True)

        # Optional: materialize config files before process start (e.g. RUNTIME ini)
        files = spec.get('files') or []
        if files:
            _materialize_files(files)

        if runtime == 'docker':
            return self._deploy_docker(spec, key, env)

        if not command:
            raise ValueError('command 不能为空')

        model_path = env.get('MODEL_PATH', '')
        if model_path:
            local_path = _ensure_model_local(model_path, env.get('MODEL_ID', '0'))
            if local_path:
                if workload_type == 'ai_service':
                    _require_onnx_model_reference(local_path)
                env['MODEL_PATH'] = local_path

        log_file = None
        if log_dir:
            log_file = open(os.path.join(log_dir, 'workload.log'), 'a', encoding='utf-8')

        try:
            proc = subprocess.Popen(
                command,
                cwd=work_dir,
                env=env,
                stdout=log_file or subprocess.DEVNULL,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        finally:
            if log_file:
                log_file.close()
        record = WorkloadRecord(
            workload_type=workload_type,
            workload_id=workload_id,
            process=proc,
            pid=proc.pid,
            log_dir=log_dir,
            command=command,
            runtime='process',
        )
        with self._lock:
            self._workloads[key] = record
        logger.info('工作负载已启动 %s pid=%s', key, proc.pid)
        return {'pid': proc.pid, 'workloadType': workload_type, 'workloadId': workload_id, 'runtime': 'process'}

    def _deploy_docker(self, spec: Dict[str, Any], key: str, env: Dict[str, str]) -> Dict[str, Any]:
        workload_type = spec['workloadType']
        workload_id = spec['workloadId']
        image = spec.get('image') or env.get('IMAGE') or env.get('TRANSFORM_IMAGE')
        if not image:
            raise ValueError('docker 部署需要 image / env.IMAGE')

        # 容器名必须唯一：同节点可跑多副本
        safe_id = ''.join(c if c.isalnum() or c in '-_' else '-' for c in str(workload_id))[:40]
        container_name = (spec.get('containerName') or env.get('CONTAINER_NAME')
                          or f'{workload_type}-{safe_id}')[:63].strip('-')

        host_port = env.get('PORT') or env.get('SERVER_PORT') or '48096'
        container_port = env.get('CONTAINER_PORT') or '48096'
        network = env.get('DOCKER_NETWORK') or ''
        extra_args = []
        if network:
            extra_args.extend(['--network', network])

        # 先清理同名残留
        subprocess.run(['docker', 'rm', '-f', container_name], capture_output=True, text=True)

        # on-failure：优雅退出(exit 0)后不会被 Docker 自动拉起；unless-stopped 会把停机打成「假停」
        restart_policy = (env.get('DOCKER_RESTART') or 'on-failure:5').strip() or 'on-failure:5'
        cmd = [
            'docker', 'run', '-d',
            '--name', container_name,
            '--restart', restart_policy,
            '-p', f'{host_port}:{container_port}',
            '-e', f'SERVER_PORT={container_port}',
            '-e', f'PORT={container_port}',
        ]
        # 透传常见环境变量
        passthrough = [
            'TRANSFORM_INSTANCE_ID', 'TRANSFORM_NODE_ID', 'TRANSFORM_HOST', 'TRANSFORM_ROLE',
            'KAFKA_BOOTSTRAP', 'POSTGRES_URL', 'POSTGRES_USERNAME', 'POSTGRES_PASSWORD',
            'SPRING_PROFILES_ACTIVE', 'TRANSFORM_BACKUP_DIR', 'JAVA_OPTS', 'NACOS_ADDR',
        ]
        # 默认 instance id = workloadId，保证多副本身份不同
        if not env.get('TRANSFORM_INSTANCE_ID'):
            env['TRANSFORM_INSTANCE_ID'] = str(workload_id)
        for k in passthrough:
            if env.get(k):
                cmd.extend(['-e', f'{k}={env[k]}'])
        cmd.extend(extra_args)
        cmd.append(image)

        logger.info('docker run: %s', ' '.join(cmd))
        result = subprocess.run(cmd, capture_output=True, text=True)
        if result.returncode != 0:
            raise RuntimeError(f'docker run 失败: {result.stderr or result.stdout}')
        container_id = (result.stdout or '').strip()
        record = WorkloadRecord(
            workload_type=workload_type,
            workload_id=workload_id,
            process=None,
            pid=0,
            command=cmd,
            runtime='docker',
            container_name=container_name,
        )
        with self._lock:
            self._workloads[key] = record
        logger.info('Docker 工作负载已启动 %s container=%s id=%s port=%s',
                    key, container_name, container_id[:12], host_port)
        return {
            'workloadType': workload_type,
            'workloadId': workload_id,
            'runtime': 'docker',
            'containerName': container_name,
            'containerId': container_id,
            'port': int(host_port),
        }

    def _container_name_for(self, workload_type: str, workload_id: str) -> str:
        safe_id = ''.join(c if c.isalnum() or c in '-_' else '-' for c in str(workload_id))[:40]
        return f'{workload_type}-{safe_id}'[:63].strip('-')

    def stop(self, workload_type: str, workload_id: str) -> bool:
        """停止工作负载。Docker 场景即使 Agent 重启丢失内存，也按命名约定 / 实例环境变量硬删容器。"""
        key = self._key(workload_type, workload_id)
        with self._lock:
            record = self._workloads.get(key)

        stopped = False
        if record:
            if record.runtime == 'docker' and record.container_name:
                stopped = _docker_rm_force(record.container_name) or stopped
            elif record.process is not None:
                _terminate_process_tree(record.process.pid)
                stopped = True
            with self._lock:
                self._workloads.pop(key, None)

        # Agent 重启后内存无记录：按约定名 + TRANSFORM_INSTANCE_ID 环境变量兜底
        convention = self._container_name_for(workload_type, workload_id)
        if record is None or (record.runtime == 'docker' and record.container_name != convention):
            stopped = _docker_rm_force(convention) or stopped
        for cname in _docker_find_by_env('TRANSFORM_INSTANCE_ID', str(workload_id)):
            stopped = _docker_rm_force(cname) or stopped

        if stopped:
            logger.info('工作负载已停止 %s', key)
        else:
            logger.warning('未找到可停止的工作负载 %s（可能已停止）', key)
        return stopped


def _docker_running(container_name: str) -> bool:
    try:
        r = subprocess.run(
            ['docker', 'inspect', '-f', '{{.State.Running}}', container_name],
            capture_output=True, text=True, timeout=10,
        )
        return r.returncode == 0 and (r.stdout or '').strip().lower() == 'true'
    except Exception:
        return False


def _docker_rm_force(container_name: str) -> bool:
    if not container_name:
        return False
    try:
        r = subprocess.run(
            ['docker', 'rm', '-f', container_name],
            capture_output=True, text=True, timeout=30,
        )
        if r.returncode == 0:
            logger.info('docker rm -f %s', container_name)
            return True
        err = (r.stderr or r.stdout or '').strip()
        # 容器不存在不算失败
        if 'No such container' in err or 'No such object' in err:
            return False
        logger.warning('docker rm -f %s failed: %s', container_name, err)
        return False
    except Exception as e:
        logger.warning('docker rm -f %s error: %s', container_name, e)
        return False


def _docker_find_by_env(env_key: str, env_value: str) -> List[str]:
    """按容器环境变量查找名称（用于 Agent 重启后按 TRANSFORM_INSTANCE_ID 硬停）。"""
    if not env_key or env_value is None or env_value == '':
        return []
    try:
        listed = subprocess.run(
            ['docker', 'ps', '-a', '--format', '{{.Names}}'],
            capture_output=True, text=True, timeout=15,
        )
        if listed.returncode != 0:
            return []
        names = [n.strip() for n in (listed.stdout or '').splitlines() if n.strip()]
        matched: List[str] = []
        needle = f'{env_key}={env_value}'
        for name in names:
            insp = subprocess.run(
                ['docker', 'inspect', '-f', '{{range .Config.Env}}{{println .}}{{end}}', name],
                capture_output=True, text=True, timeout=10,
            )
            if insp.returncode != 0:
                continue
            envs = {(line or '').strip() for line in (insp.stdout or '').splitlines()}
            if needle in envs:
                matched.append(name)
        return matched
    except Exception as e:
        logger.warning('docker find by env %s=%s failed: %s', env_key, env_value, e)
        return []


def _validate_workload_spec(spec: Dict[str, Any]):
    workload_type = str(spec.get('workloadType') or '').strip().lower()
    workload_id = str(spec.get('workloadId') or '').strip()
    policy = _WORKLOAD_POLICIES.get(workload_type)
    if policy is None:
        raise ValueError(f'不允许的 workloadType: {workload_type or "<empty>"}')
    if not workload_id:
        raise ValueError('workloadId 不能为空')

    root_env, allowed_scripts, allowed_bundles = policy
    default_root = '/opt/easyaiot/AI' if root_env == 'AI_ROOT' else '/opt/easyaiot/VIDEO'
    root = os.path.realpath(os.environ.get(root_env, default_root))
    if not os.path.isdir(root):
        raise ValueError(f'{root_env} 不存在: {root}')

    command = spec.get('command') or []
    if not isinstance(command, (list, tuple)) or len(command) != 2:
        raise ValueError('启动命令必须是固定 Python 启动器与单个 worker 脚本')
    executable = os.path.realpath(str(command[0]))
    script = os.path.realpath(str(command[1]))

    relative_script = _relative_path_within(root, script, 'worker 脚本')
    if relative_script not in allowed_scripts:
        raise ValueError(f'workloadType={workload_type} 不允许脚本: {relative_script}')
    if not os.path.isfile(script):
        raise ValueError(f'worker 脚本不存在: {script}')

    allowed_executables = {os.path.realpath(sys.executable)}
    for bundle in allowed_bundles:
        allowed_executables.add(
            os.path.realpath(os.path.join(root, '.bundles', bundle, 'run-python.sh'))
        )
    configured_executables = os.environ.get('NODE_ALLOWED_PYTHON_EXECUTABLES', '')
    for candidate in configured_executables.split(os.pathsep):
        if candidate.strip():
            allowed_executables.add(os.path.realpath(candidate.strip()))
    if executable not in allowed_executables or not os.path.isfile(executable):
        raise ValueError(f'不允许的 Python 启动器: {executable}')

    work_dir = os.path.realpath(str(spec.get('workDir') or ''))
    if work_dir != os.path.dirname(script) or not os.path.isdir(work_dir):
        raise ValueError('workDir 必须是受信 worker 脚本所在目录')

    log_dir_value = str(spec.get('logDir') or '').strip()
    log_dir = None
    if log_dir_value:
        log_dir = os.path.realpath(log_dir_value)
        logs_root = os.path.realpath(os.path.join(root, 'logs'))
        _relative_path_within(logs_root, log_dir, 'logDir')
        os.makedirs(log_dir, exist_ok=True)

    env_extra = {}
    for raw_key, value in (spec.get('env') or {}).items():
        key = str(raw_key).strip().upper()
        if not key:
            raise ValueError('环境变量名不能为空')
        if (
            key in _BLOCKED_ENV_KEYS
            or key.startswith('LD_')
            or key.startswith('DYLD_')
            or key.startswith('PYTHON')
        ):
            raise ValueError(f'不允许覆盖进程启动环境变量: {key}')
        if key in {'AI_ROOT', 'VIDEO_ROOT'}:
            if os.path.realpath(str(value)) != root:
                raise ValueError(f'不允许覆盖 {key}')
        env_extra[key] = str(value)

    if workload_type == 'ai_service':
        model_path = env_extra.get('MODEL_PATH', '')
        if not model_path:
            raise ValueError('ai_service 必须提供 ONNX MODEL_PATH')
        _require_onnx_model_reference(model_path)
        env_extra['MODEL_ID'] = _normalize_model_id(env_extra.get('MODEL_ID', '0'))

    return (
        workload_type,
        workload_id,
        [executable, script],
        work_dir,
        log_dir,
        env_extra,
    )


def _relative_path_within(root: str, path: str, label: str) -> str:
    try:
        if os.path.commonpath((root, path)) != root:
            raise ValueError
    except ValueError:
        raise ValueError(f'{label} 必须位于 {root} 内')
    return os.path.relpath(path, root).replace(os.sep, '/')


def _normalize_model_id(value: Any) -> str:
    text = str(value if value is not None else '0').strip()
    if not text or not text.isascii() or not text.isdigit():
        raise ValueError('MODEL_ID 必须是非负十进制整数')
    number = int(text)
    if number > 9_223_372_036_854_775_807:
        raise ValueError('MODEL_ID 超出支持范围')
    return str(number)


def _require_onnx_model_reference(model_path: str) -> str:
    value = str(model_path or '').strip()
    parsed = urllib.parse.urlparse(value)
    object_path = urllib.parse.parse_qs(parsed.query).get('prefix', [parsed.path])[0]
    for _ in range(3):
        decoded = urllib.parse.unquote(object_path)
        if decoded == object_path:
            break
        object_path = decoded
    if os.path.splitext(object_path)[1].lower() != '.onnx':
        raise ValueError('ai_service 仅允许加载 ONNX 模型，不允许 pickle-backed 模型')
    return value


def _terminate_process_tree(pid: int):
    try:
        parent = psutil.Process(pid)
        children = parent.children(recursive=True)
        for child in children:
            try:
                child.terminate()
            except psutil.Error:
                pass
        parent.terminate()
        gone, alive = psutil.wait_procs(children + [parent], timeout=5)
        for p in alive:
            try:
                p.kill()
            except psutil.Error:
                pass
    except psutil.NoSuchProcess:
        pass


def _materialize_files(files) -> None:
    """Write deploy-time files onto the node (path + content)."""
    for item in files or []:
        if not isinstance(item, dict):
            continue
        path = (item.get('path') or '').strip()
        content = item.get('content')
        if not path or content is None:
            continue
        parent = os.path.dirname(path)
        if parent:
            os.makedirs(parent, exist_ok=True)
        mode = item.get('mode')
        with open(path, 'w', encoding='utf-8') as f:
            f.write(str(content))
        if mode:
            try:
                os.chmod(path, int(str(mode), 8) if isinstance(mode, str) else int(mode))
            except Exception:
                pass
        logger.info('已写入部署文件: %s (%s bytes)', path, len(str(content)))


def _ensure_model_local(model_path: str, model_id: str) -> Optional[str]:
    """若 MODEL_PATH 为 MinIO URL 则下载到本地；集群模式优先读 CephFS 共享缓存。"""
    model_id = _normalize_model_id(model_id)
    try:
        mid = int(model_id)
        if mid > 0:
            from model_resolver import try_resolve_cluster_model_path
            cluster_path = try_resolve_cluster_model_path(mid)
            if cluster_path:
                logger.info('使用集群共享模型 model_id=%s path=%s', model_id, cluster_path)
                return cluster_path
    except (ImportError, ValueError, TypeError):
        pass

    if not model_path.startswith('/api/v1/buckets/') and not model_path.startswith('http'):
        if os.path.isabs(model_path) and os.path.exists(model_path):
            return model_path
        ai_root = os.environ.get('AI_ROOT', '/opt/easyaiot/AI')
        local = os.path.join(ai_root, model_path) if not os.path.isabs(model_path) else model_path
        return local if os.path.exists(local) else model_path

    try:
        parsed = urllib.parse.urlparse(model_path)
        path_parts = parsed.path.split('/')
        if len(path_parts) < 5 or path_parts[3] != 'buckets':
            return model_path
        bucket_name = path_parts[4]
        query_params = urllib.parse.parse_qs(parsed.query)
        object_key = query_params.get('prefix', [None])[0]
        if not object_key:
            return model_path

        ai_root = os.environ.get('AI_ROOT', '/opt/easyaiot/AI')
        models_base = os.environ.get('AI_MODELS_DIR', '').strip()
        if not models_base:
            try:
                from cluster_storage import get_ai_models_dir
                models_base = get_ai_models_dir()
            except ImportError:
                models_base = os.path.join(ai_root, 'data', 'models')
        models_base = os.path.realpath(models_base)
        storage_dir = os.path.realpath(os.path.join(models_base, model_id))
        _relative_path_within(models_base, storage_dir, '模型缓存目录')
        os.makedirs(storage_dir, exist_ok=True)
        filename = os.path.basename(object_key)
        if filename in {'', '.', '..'}:
            raise ValueError('模型对象名无效')
        local_path = os.path.realpath(os.path.join(storage_dir, filename))
        _relative_path_within(storage_dir, local_path, '模型缓存文件')
        if os.path.exists(local_path):
            return local_path

        endpoint = os.environ.get('MINIO_ENDPOINT', 'http://localhost:9000').rstrip('/')
        access_key = os.environ.get('MINIO_ACCESS_KEY', '').strip()
        secret_key = os.environ.get('MINIO_SECRET_KEY', '')
        if not access_key or not secret_key:
            raise ValueError('MINIO_ACCESS_KEY / MINIO_SECRET_KEY 未配置')
        from minio import Minio
        secure = endpoint.startswith('https')
        host = endpoint.replace('https://', '').replace('http://', '')
        client = Minio(host, access_key=access_key, secret_key=secret_key, secure=secure)
        client.fget_object(bucket_name, object_key, local_path)
        logger.info('模型已下载到 %s', local_path)
        return local_path
    except Exception as e:
        raise ValueError(f'模型下载失败: {e}') from e


def find_available_port(start_port: int = 8000, max_attempts: int = 100) -> Optional[int]:
    for i in range(max_attempts):
        port = start_port + i
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.bind(('0.0.0.0', port))
            return port
        except OSError:
            continue
        finally:
            sock.close()
    return None
