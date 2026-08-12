#!/usr/bin/env python3
"""在从 VIDEO 种子同步之后，将 EDGE 自有覆盖重新打入 runtime。

VIDEO 源码树不得包含 EDGE_* 推流/告警总线逻辑；边缘专用补丁只存在于本目录。
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

OVERLAY_DIR = Path(__file__).resolve().parent
RUNTIME_ROOT = OVERLAY_DIR.parent
REALTIME_DEPLOY = RUNTIME_ROOT / 'services' / 'realtime_algorithm_service' / 'run_deploy.py'
SNIPPET = OVERLAY_DIR / 'resolve_ai_rtmp_push_url.py.snippet'
ALERT_SNIPPET = OVERLAY_DIR / 'alert_mqtt_bus.py.snippet'

FUNC_RE = re.compile(
    r'^def _resolve_ai_rtmp_push_url\(.*?(?=^def )',
    re.MULTILINE | re.DOTALL,
)

# 在 send_alert_event_async 内、HTTP post 之前注入 MQTT 优先分支
ALERT_ANCHOR_RE = re.compile(
    r"(alert_data\['task_type'\] = 'realtime'\n)"
    r"(?P<body>(?:.*?\n)*?)"
    r"(?P<http>            # 通过 HTTP 发送告警事件到 sink hook 接口\n"
    r"            # sink 会负责将告警投入 Kafka\n"
    r"            try:\n"
    r"                response = requests\.post\()",
    re.MULTILINE,
)

# 兼容种子文件尚未抽公共赋值时的旧形态
ALERT_LEGACY_RE = re.compile(
    r"(            # 通过 HTTP 发送告警事件到 sink hook 接口\n"
    r"            # sink 会负责将告警投入 Kafka\n"
    r"            try:\n)"
    r"(?:                # 标记为实时算法任务\n"
    r"                alert_data\['task_type'\] = 'realtime'\n"
    r"(?:.*?\n)*?)?"
    r"(                response = requests\.post\()",
    re.MULTILINE,
)


def apply_resolve_ai_rtmp_overlay() -> None:
    if not REALTIME_DEPLOY.is_file():
        print(f'[skip] missing {REALTIME_DEPLOY}', file=sys.stderr)
        return
    if not SNIPPET.is_file():
        raise SystemExit(f'missing snippet: {SNIPPET}')

    src = REALTIME_DEPLOY.read_text(encoding='utf-8')
    snippet = SNIPPET.read_text(encoding='utf-8').rstrip() + '\n\n'
    new_src, n = FUNC_RE.subn(snippet, src, count=1)
    if n != 1:
        raise SystemExit(
            f'failed to locate exactly one _resolve_ai_rtmp_push_url in {REALTIME_DEPLOY} (matched={n})'
        )
    REALTIME_DEPLOY.write_text(new_src, encoding='utf-8')
    print(f'[ok] applied resolve_ai_rtmp_push_url overlay → {REALTIME_DEPLOY}')


def apply_alert_mqtt_overlay() -> None:
    if not REALTIME_DEPLOY.is_file() or not ALERT_SNIPPET.is_file():
        print('[skip] alert mqtt overlay missing files', file=sys.stderr)
        return
    src = REALTIME_DEPLOY.read_text(encoding='utf-8')
    # 先去掉旧 overlay 再注入，避免重复
    src = re.sub(
        r'\n?            # BEGIN EDGE_OVERLAY alert_mqtt_bus\n.*?# END EDGE_OVERLAY alert_mqtt_bus\n',
        '\n',
        src,
        count=1,
        flags=re.DOTALL,
    )
    block = ALERT_SNIPPET.read_text(encoding='utf-8').rstrip() + '\n'
    if 'BEGIN EDGE_OVERLAY alert_mqtt_bus' in src:
        REALTIME_DEPLOY.write_text(src, encoding='utf-8')
        print('[ok] alert overlay already present after strip/recheck')
        return

    m = ALERT_ANCHOR_RE.search(src)
    if m:
        new_src = src[: m.start('http')] + block + '\n' + m.group('http') + src[m.end('http') :]
        REALTIME_DEPLOY.write_text(new_src, encoding='utf-8')
        print(f'[ok] applied alert_mqtt_bus overlay → {REALTIME_DEPLOY}')
        return

    m2 = ALERT_LEGACY_RE.search(src)
    if m2:
        insert_at = m2.start(2) if m2.lastindex and m2.lastindex >= 2 else m2.start()
        # 在 response = requests.post 前插入；保留前置 HTTP 注释与 try
        prefix = src[: m2.start(2)]
        new_src = prefix + block + '\n' + src[m2.start(2) :]
        REALTIME_DEPLOY.write_text(new_src, encoding='utf-8')
        print(f'[ok] applied alert_mqtt_bus overlay (legacy) → {REALTIME_DEPLOY}')
        return

    print('[warn] alert_mqtt_bus anchor not found; keep manual patch if present', file=sys.stderr)


def main() -> int:
    apply_resolve_ai_rtmp_overlay()
    apply_alert_mqtt_overlay()
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
