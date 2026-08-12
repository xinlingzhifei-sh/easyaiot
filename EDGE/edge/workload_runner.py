"""本节点工作负载：按 MQTT cmd 拉起/停止算法进程（runtime 目录）。"""
from __future__ import annotations

import json
import logging
import os
import signal
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Dict, Optional

from edge.config import EDGE_ROOT, STATE_DIR

logger = logging.getLogger('edge.workload')

RUNTIME_ROOT = Path(os.environ.get('EDGE_RUNTIME_ROOT') or EDGE_ROOT / 'runtime')
PROCS_FILE = Path(os.environ.get('EDGE_WORKLOAD_PROCS_FILE') or STATE_DIR / 'workloads.json')

_SERVICES = {
    'realtime': 'realtime_algorithm_service',
    'snap': 'snapshot_algorithm_service',
    'patrol': 'patrol_algorithm_service',
}

_BLOCKED_ENV_KEYS = {
    'BASH_ENV',
    'ENV',
    'GCONV_PATH',
    'LD_LIBRARY_PATH',
    'LD_PRELOAD',
    'PATH',
    'PYTHONEXECUTABLE',
    'PYTHONHOME',
    'PYTHONINSPECT',
    'PYTHONPATH',
    'PYTHONSTARTUP',
    'VIRTUAL_ENV',
}
_BLOCKED_ENV_PREFIXES = ('DYLD_', 'LD_', 'PYTHON')

_procs: Dict[int, subprocess.Popen] = {}


def _load_proc_map() -> Dict[str, int]:
    if not PROCS_FILE.is_file():
        return {}
    try:
        data = json.loads(PROCS_FILE.read_text(encoding='utf-8'))
        if isinstance(data, dict):
            return {str(k): int(v) for k, v in data.items() if str(k).isdigit()}
    except Exception:
        pass
    return {}


def _save_proc_map(mapping: Dict[str, int]) -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    PROCS_FILE.write_text(json.dumps(mapping, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def _remember_pid(task_id: int, pid: int) -> None:
    mapping = _load_proc_map()
    mapping[str(task_id)] = int(pid)
    _save_proc_map(mapping)


def _forget_pid(task_id: int) -> None:
    mapping = _load_proc_map()
    mapping.pop(str(task_id), None)
    _save_proc_map(mapping)


def _deploy_script(task_type: str) -> Path:
    normalized_type = str(task_type or '').strip().lower()
    if normalized_type not in _SERVICES:
        raise ValueError(f'不支持的 taskType: {task_type!r}')
    name = _SERVICES[normalized_type]
    script = RUNTIME_ROOT / 'services' / name / 'run_deploy.py'
    return script


def start_task(cmd_payload: Dict[str, Any], runtime_env: Dict[str, str]) -> Dict[str, Any]:
    task_id = int(cmd_payload.get('taskId') or 0)
    if not task_id:
        raise ValueError('cmd 缺少 taskId')
    if task_id in _procs and _procs[task_id].poll() is None:
        return {'success': True, 'processId': _procs[task_id].pid, 'reason': 'already_running'}

    # 跨进程：若 workloads.json 中仍存活则视为已运行
    existing = _load_proc_map().get(str(task_id))
    if existing:
        try:
            os.kill(existing, 0)
            return {'success': True, 'processId': existing, 'reason': 'already_running'}
        except OSError:
            _forget_pid(task_id)

    task_type = (cmd_payload.get('taskType') or 'realtime').strip()
    deploy = cmd_payload.get('deploy') or {}
    script = _deploy_script(task_type)
    # 边缘执行唯一入口：EDGE/runtime（与 VIDEO 源码树解耦）。
    # MQTT 只能选择固定 taskType，不能下发 command/workDir。
    if script.is_file():
        python_exec = sys.executable
        command = [python_exec, str(script)]
        work_dir = str(script.parent)
        return _spawn(task_id, command, work_dir, runtime_env, deploy.get('env') or {})

    raise FileNotFoundError(
        f'未找到算法入口 {script}，请执行 EDGE/scripts/sync_runtime_from_video.sh 种子化 runtime'
    )


def _spawn(
    task_id: int,
    command: list,
    work_dir: str,
    runtime_env: Dict[str, str],
    deploy_env: Dict[str, Any],
) -> Dict[str, Any]:
    env = os.environ.copy()
    env.update({k: str(v) for k, v in runtime_env.items() if v is not None})
    for key, value in deploy_env.items():
        normalized_key = str(key).strip().upper()
        if (
            not normalized_key
            or normalized_key in _BLOCKED_ENV_KEYS
            or normalized_key.startswith(_BLOCKED_ENV_PREFIXES)
        ):
            logger.warning('忽略不安全的任务环境变量: %s', normalized_key or '<empty>')
            continue
        env[normalized_key] = str(value)
    env.setdefault('TASK_ID', str(task_id))
    # 边缘不存储：强制 Ceph 路径语义；不做 MinIO 同步上传
    env.setdefault('ALGO_MEDIA_REF_MODE', 'shared_fs')
    env.setdefault('ALGO_UPLOAD_MINIO_SYNC', 'false')
    env.setdefault('ALGO_BUS_TRANSPORT', 'mqtt')

    log_dir = Path(work_dir) / 'logs' / f'task_{task_id}'
    log_dir.mkdir(parents=True, exist_ok=True)
    stdout = open(log_dir / 'edge_stdout.log', 'a', encoding='utf-8')
    try:
        proc = subprocess.Popen(
            command,
            cwd=work_dir,
            env=env,
            stdout=stdout,
            stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    finally:
        stdout.close()
    _procs[task_id] = proc
    _remember_pid(task_id, proc.pid)
    logger.info('started task_id=%s pid=%s cmd=%s', task_id, proc.pid, command)
    return {'success': True, 'processId': proc.pid, 'reason': None}


def stop_task(task_id: int) -> Dict[str, Any]:
    proc = _procs.get(task_id)
    pid: Optional[int] = None
    if proc and proc.poll() is None:
        pid = proc.pid
    else:
        remembered = _load_proc_map().get(str(task_id))
        if remembered:
            try:
                os.kill(remembered, 0)
                pid = remembered
            except OSError:
                _forget_pid(task_id)
                _procs.pop(task_id, None)
                return {'success': True, 'processId': None, 'reason': 'not_running'}

    if not pid:
        _procs.pop(task_id, None)
        _forget_pid(task_id)
        return {'success': True, 'processId': None, 'reason': 'not_running'}

    try:
        os.killpg(os.getpgid(pid), signal.SIGTERM)
    except Exception:
        try:
            os.kill(pid, signal.SIGTERM)
        except Exception:
            if proc:
                proc.terminate()
    if proc:
        try:
            proc.wait(timeout=15)
        except Exception:
            try:
                os.killpg(os.getpgid(pid), signal.SIGKILL)
            except Exception:
                try:
                    os.kill(pid, signal.SIGKILL)
                except Exception:
                    proc.kill()
    else:
        for _ in range(30):
            try:
                os.kill(pid, 0)
                time.sleep(0.5)
            except OSError:
                break
        else:
            try:
                os.killpg(os.getpgid(pid), signal.SIGKILL)
            except Exception:
                try:
                    os.kill(pid, signal.SIGKILL)
                except Exception:
                    pass
    _procs.pop(task_id, None)
    _forget_pid(task_id)
    logger.info('stopped task_id=%s', task_id)
    return {'success': True, 'processId': None, 'reason': None}


def restart_task(cmd_payload: Dict[str, Any], runtime_env: Dict[str, str]) -> Dict[str, Any]:
    task_id = int(cmd_payload.get('taskId') or 0)
    stop_task(task_id)
    return start_task(cmd_payload, runtime_env)
