"""EDGE 纯命令行入口。"""
from __future__ import annotations

import argparse
import json
import logging
import os
import signal
import sys
import time
from pathlib import Path

from edge import __version__
from edge.config import (
    STATE_DIR,
    load_env,
    load_state,
    merge_runtime_into_state,
    normalize_node_url,
    save_env_value,
    save_srs_config,
    srs_env_from_local,
)
from edge import node_client

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
)
logger = logging.getLogger('edge')

RUN_PID_FILE = Path(os.environ.get('EDGE_RUN_PID_FILE') or STATE_DIR / 'edge.run.pid')


def _write_run_pid() -> None:
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    RUN_PID_FILE.write_text(str(os.getpid()) + '\n', encoding='utf-8')


def _clear_run_pid() -> None:
    try:
        if RUN_PID_FILE.is_file():
            RUN_PID_FILE.unlink()
    except Exception:
        pass


def cmd_config(args: argparse.Namespace) -> int:
    if args.action == 'set-node':
        url = normalize_node_url(args.value)
        save_env_value('EDGE_NODE_URL', url)
        print(f'已写入 EDGE_NODE_URL={url}')
        return 0
    if args.action == 'set-join-token':
        save_env_value('EDGE_JOIN_TOKEN', args.value)
        print('已写入 EDGE_JOIN_TOKEN')
        return 0
    if args.action == 'set-srs':
        host = (args.host or args.value or '').strip()
        if not host:
            print('请提供 SRS 主机：python -m edge config set-srs --host <IP>', file=sys.stderr)
            return 2
        try:
            saved = save_srs_config(
                host,
                rtmp_port=int(args.rtmp_port) if args.rtmp_port is not None else 1935,
                http_port=int(args.http_port) if args.http_port is not None else 8080,
                api_port=int(args.api_port) if args.api_port is not None else 1985,
            )
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
        print('已写入 SRS 目标：')
        print(json.dumps(saved, ensure_ascii=False, indent=2))
        print(
            f"推流基址 rtmp://{saved['EDGE_SRS_HOST']}:{saved['EDGE_SRS_RTMP_PORT']}/ai/"
            f"<deviceId>"
        )
        return 0
    if args.action == 'show':
        env = load_env()
        state = load_state()
        safe = {k: ('***' if 'TOKEN' in k or 'PASSWORD' in k else v) for k, v in env.items()}
        print(json.dumps({
            'env': safe,
            'srs': srs_env_from_local(),
            'stateKeys': list(state.keys()),
            'nodeId': state.get('nodeId'),
        }, ensure_ascii=False, indent=2))
        return 0
    print('未知 config 动作', file=sys.stderr)
    return 2


def cmd_enroll(args: argparse.Namespace) -> int:
    data = node_client.enroll(
        node_role=args.role,
        max_task_count=args.max_tasks,
        join_token=args.join_token,
    )
    node_id = data.get('nodeId')
    token = data.get('agentToken')
    runtime = data.get('runtimeConfig') or {}
    merge_runtime_into_state(runtime, node_id=int(node_id), agent_token=token)
    print(json.dumps({
        'nodeId': node_id,
        'mqttBrokerUrls': runtime.get('mqttBrokerUrls'),
        'alertImagesDir': runtime.get('alertImagesDir'),
        'mqttUsername': runtime.get('mqttUsername'),
    }, ensure_ascii=False, indent=2))
    print('enroll 成功：运行时配置已写入 state/ 与 edge.env')
    return 0


def cmd_pull_config(_: argparse.Namespace) -> int:
    runtime = node_client.pull_runtime_config()
    state = load_state()
    merge_runtime_into_state(
        runtime,
        node_id=int(state.get('nodeId') or load_env().get('EDGE_NODE_ID') or 0) or None,
        agent_token=state.get('agentToken') or load_env().get('EDGE_AGENT_TOKEN'),
    )
    print(json.dumps({
        'mqttBrokerUrls': runtime.get('mqttBrokerUrls'),
        'mediaHostDataRoot': runtime.get('mediaHostDataRoot'),
        'mqttClientId': runtime.get('mqttClientId'),
    }, ensure_ascii=False, indent=2))
    return 0


def cmd_status(_: argparse.Namespace) -> int:
    env = load_env()
    state = load_state()
    rt = state.get('runtimeConfig') or {}
    print(json.dumps({
        'version': __version__,
        'nodeUrl': env.get('EDGE_NODE_URL'),
        'nodeId': state.get('nodeId') or env.get('EDGE_NODE_ID'),
        'mqttBrokerUrls': rt.get('mqttBrokerUrls') or env.get('MQTT_BROKER_URLS'),
        'srsHost': env.get('EDGE_SRS_HOST'),
        'srsRtmpPort': env.get('EDGE_SRS_RTMP_PORT') or env.get('SRS_RTMP_PORT'),
        'enrolled': bool(state.get('nodeId') and state.get('agentToken')),
    }, ensure_ascii=False, indent=2))
    return 0


def cmd_run(args: argparse.Namespace) -> int:
    env = load_env()
    if not env.get('EDGE_NODE_URL'):
        print('请先: python -m edge config set-node <NODE地址>', file=sys.stderr)
        return 2
    state = load_state()
    if not state.get('nodeId') or not state.get('agentToken'):
        logger.info('尚未 enroll，自动执行 enroll…')
        cmd_enroll(argparse.Namespace(role=args.role, max_tasks=args.max_tasks, join_token=None))
        state = load_state()
    else:
        # 每次启动刷新动态配置（MQTT 列表可能变更）
        try:
            cmd_pull_config(argparse.Namespace())
            state = load_state()
        except Exception as exc:
            logger.warning('pull-config 失败，使用本地缓存: %s', exc)

    runtime = state.get('runtimeConfig') or {}
    node_id = int(state['nodeId'])
    from edge.mqtt_runtime import EdgeMqttRuntime  # 延迟导入，避免 status 等命令强依赖 paho
    rt = EdgeMqttRuntime(runtime, node_id)

    def _stop(*_):
        logger.info('收到退出信号')
        rt.stop()

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)
    _write_run_pid()
    print(f'EDGE 运行中 nodeId={node_id} brokers={runtime.get("mqttBrokerUrls")} （Ctrl+C 退出）')
    try:
        rt.run_forever()
    finally:
        _clear_run_pid()
    return 0


def cmd_stop(_: argparse.Namespace) -> int:
    """优雅停止本机 python -m edge run（依据 state/edge.run.pid）。"""
    if not RUN_PID_FILE.is_file():
        print('未发现运行中的 edge run（无 pid 文件）', file=sys.stderr)
        return 1
    try:
        pid = int(RUN_PID_FILE.read_text(encoding='utf-8').strip())
    except Exception:
        print(f'无效 pid 文件: {RUN_PID_FILE}', file=sys.stderr)
        return 2
    try:
        os.kill(pid, 0)
    except OSError:
        _clear_run_pid()
        print(f'进程 {pid} 已不存在，已清理 pid 文件')
        return 0
    print(f'正在停止 edge run pid={pid} …')
    try:
        os.kill(pid, signal.SIGTERM)
    except OSError as exc:
        print(f'发送 SIGTERM 失败: {exc}', file=sys.stderr)
        return 1
    for _ in range(30):
        try:
            os.kill(pid, 0)
            time.sleep(0.2)
        except OSError:
            _clear_run_pid()
            print('已停止')
            return 0
    print('进程未在超时内退出，可手动 kill', file=sys.stderr)
    return 1


def cmd_task(args: argparse.Namespace) -> int:
    """命令下发启停：默认经 MQTT；加 --local 则本机直拉 EDGE/runtime（不经过总线）。"""
    from edge.task_publish import build_cmd_payload, execute_local, parse_env_pairs, publish_task_cmd

    try:
        deploy_env = parse_env_pairs(args.env)
        if args.env_json:
            extra = json.loads(args.env_json)
            if not isinstance(extra, dict):
                raise ValueError('--env-json 须为 JSON 对象')
            deploy_env.update({str(k): str(v) for k, v in extra.items()})
        payload = build_cmd_payload(
            action=args.action,
            task_id=int(args.task_id),
            task_type=args.type,
            target_node_id=args.target_node_id,
            deploy_env=deploy_env,
        )
        if args.local:
            result = execute_local(payload)
            print(json.dumps({'mode': 'local', 'payload': payload, 'result': result}, ensure_ascii=False, indent=2))
            return 0 if result.get('success') else 1
        result = publish_task_cmd(payload)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        print('已发布到 MQTT。请确认另一终端已执行: python -m edge run')
        return 0
    except Exception as exc:
        print(str(exc), file=sys.stderr)
        return 2


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog='edge', description='yFeiEye EDGE — 无界面边缘算法运行时')
    p.add_argument('--version', action='version', version=f'edge {__version__}')
    sub = p.add_subparsers(dest='command', required=True)

    c = sub.add_parser('config', help='本地配置（set-node 必配；多 SRS 时另设 set-srs）')
    c.add_argument('action', choices=['set-node', 'set-join-token', 'set-srs', 'show'])
    c.add_argument('value', nargs='?', default='', help='set-node URL / set-join-token / set-srs host')
    c.add_argument('--host', default='', help='set-srs：SRS 主机 IP 或域名')
    c.add_argument('--rtmp-port', type=int, default=None, help='set-srs：RTMP 端口，默认 1935')
    c.add_argument('--http-port', type=int, default=None, help='set-srs：HTTP-FLV 端口，默认 8080')
    c.add_argument('--api-port', type=int, default=None, help='set-srs：SRS API 端口，默认 1985')
    c.set_defaults(func=cmd_config)

    e = sub.add_parser('enroll', help='向 NODE 登记并领取 MQTT/路径等全部动态配置')
    e.add_argument('--role', default='compute', help='节点角色，默认 compute')
    e.add_argument('--max-tasks', type=int, default=1)
    e.add_argument('--join-token', default=None)
    e.set_defaults(func=cmd_enroll)

    sub.add_parser('pull-config', help='刷新运行时配置').set_defaults(func=cmd_pull_config)
    sub.add_parser('status', help='查看本地状态').set_defaults(func=cmd_status)
    sub.add_parser('stop', help='停止本机 edge run（读取 state/edge.run.pid）').set_defaults(func=cmd_stop)

    r = sub.add_parser('run', help='连接 MQTT 并接收算法任务指令')
    r.add_argument('--role', default='compute')
    r.add_argument('--max-tasks', type=int, default=1)
    r.set_defaults(func=cmd_run)

    t = sub.add_parser('task', help='命令下发算法启停（MQTT；可加 --local 本机直拉）')
    t.add_argument('action', choices=['start', 'stop', 'restart'])
    t.add_argument('--task-id', type=int, required=True, help='任务 ID（写入进程环境 TASK_ID）')
    t.add_argument('--type', default='realtime', choices=['realtime', 'snap', 'patrol'], help='任务类型')
    t.add_argument('--target-node-id', type=int, default=None, help='默认为本机 enroll 的 nodeId（compute_node）')
    t.add_argument('--env', action='append', default=[], help='额外环境变量 KEY=VALUE，可重复')
    t.add_argument('--env-json', default='', help='额外环境变量 JSON 对象')
    t.add_argument('--local', action='store_true', help='不经 MQTT，直接在本机拉起/停止 workload')
    t.set_defaults(func=cmd_task)

    return p


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if args.command == 'config' and args.action not in ('show', 'set-srs') and not args.value:
        parser.error(f'config {args.action} 需要 value')
    if args.command == 'config' and args.action == 'set-srs' and not (args.host or args.value):
        parser.error('config set-srs 需要 --host <IP> 或位置参数 host')
    return int(args.func(args))
