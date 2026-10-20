"""app/utils/http_server.py 与 background_tasks_guard.py 的单测（AI/tests/test_ai_http_server.py 为同内容副本）。"""
import argparse
import http.client
import io
import sys
import threading
from pathlib import Path

import pytest
from flask import Flask, Response, send_file, stream_with_context

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from app.utils import background_tasks_guard, http_server


def _parse(argv):
    parser = argparse.ArgumentParser()
    http_server.add_server_arguments(parser)
    return parser.parse_args(argv).server


def test_server_arguments_default_dev_and_last_one_wins():
    assert _parse([]) is None
    assert _parse(['--dev']) == http_server.SERVER_DEV
    assert _parse(['--server=waitress']) == http_server.SERVER_WAITRESS
    # start_prod.sh 固定 --server=waitress，临时排障时在其后追加 --dev 仍可切到开发服务器
    assert _parse(['--server=waitress', '--dev']) == http_server.SERVER_DEV


def test_resolve_server_kind_prefers_cli_then_env_then_waitress(monkeypatch):
    monkeypatch.delenv('TEST_HTTP_SERVER', raising=False)
    assert http_server.resolve_server_kind(None, 'TEST_HTTP_SERVER') == http_server.SERVER_WAITRESS

    monkeypatch.setenv('TEST_HTTP_SERVER', 'dev')
    assert http_server.resolve_server_kind(None, 'TEST_HTTP_SERVER') == http_server.SERVER_DEV
    # 生产命令显式 --server=waitress，.env 里残留的 dev 不能把它切回开发服务器
    assert http_server.resolve_server_kind('waitress', 'TEST_HTTP_SERVER') == http_server.SERVER_WAITRESS

    monkeypatch.setenv('TEST_HTTP_SERVER', 'gunicorn')
    with pytest.raises(ValueError):
        http_server.resolve_server_kind(None, 'TEST_HTTP_SERVER')


def test_waitress_options_follow_app_upload_limit_and_env(monkeypatch):
    for name in ('THREADS', 'CONNECTION_LIMIT', 'CHANNEL_TIMEOUT', 'MAX_REQUEST_BODY_MB'):
        monkeypatch.delenv(f'TEST_HTTP_{name}', raising=False)
    app = Flask(__name__)

    options = http_server.build_waitress_options(app, 'TEST', default_threads=8)
    assert options['threads'] == 8
    assert options['max_request_body_size'] == 5120 * 1024 * 1024
    assert options['clear_untrusted_proxy_headers'] is False

    # 应用自身有上传上限时，waitress 不能比它更严（waitress 默认 1 GiB）
    app.config['MAX_CONTENT_LENGTH'] = 6 * 1024 * 1024 * 1024
    monkeypatch.setenv('TEST_HTTP_THREADS', '3')
    options = http_server.build_waitress_options(app, 'TEST', default_threads=8)
    assert options['threads'] == 3
    assert options['max_request_body_size'] == 6 * 1024 * 1024 * 1024

    monkeypatch.setenv('TEST_HTTP_THREADS', '0')
    with pytest.raises(ValueError):
        http_server.build_waitress_options(app, 'TEST', default_threads=8)


def test_dev_server_never_enables_reloader(monkeypatch):
    app = Flask(__name__)
    calls = []
    monkeypatch.setattr(app, 'run', lambda **kwargs: calls.append(kwargs))

    http_server.run_dev_server(app, '127.0.0.1', 6000)

    assert calls == [{'host': '127.0.0.1', 'port': 6000, 'threaded': True, 'use_reloader': False}]


def test_waitress_serves_json_sse_and_ranges_on_reserved_socket_and_blocks_second_instance():
    app = Flask(__name__)

    @app.route('/ping')
    def ping():
        return {'ok': True}

    @app.route('/events')
    def events():
        def generate():
            yield 'event: progress\ndata: {}\n\n'
            yield ': keepalive\n\n'

        # 与 VIDEO 巡检 SSE 相同的响应头。不能带 Connection 等逐跳头，waitress 会按 PEP 3333 直接返回 500
        return Response(
            stream_with_context(generate()),
            mimetype='text/event-stream',
            headers={'Cache-Control': 'no-cache', 'X-Accel-Buffering': 'no'},
        )

    @app.route('/file')
    def file():
        # 录像回放/下载走 send_file + Range；waitress 提供 wsgi.file_wrapper，与开发服务器走的路径不同
        return send_file(io.BytesIO(b'0123456789'), mimetype='video/mp4', conditional=True)

    sock = http_server.reserve_listen_socket('127.0.0.1', 0)
    port = sock.getsockname()[1]
    server = http_server.create_waitress_server(app, sock, env_prefix='TEST', default_threads=2)
    loop = threading.Thread(target=server.run, daemon=True)
    loop.start()
    try:
        conn = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
        conn.request('GET', '/ping')
        resp = conn.getresponse()
        assert resp.status == 200
        assert resp.read() == b'{"ok":true}\n'

        conn.request('GET', '/events')
        resp = conn.getresponse()
        assert resp.status == 200
        assert resp.getheader('Content-Type').startswith('text/event-stream')
        assert resp.read() == b'event: progress\ndata: {}\n\n: keepalive\n\n'

        conn.request('GET', '/file', headers={'Range': 'bytes=2-5'})
        resp = conn.getresponse()
        assert resp.status == 206
        assert resp.getheader('Content-Range') == 'bytes 2-5/10'
        assert resp.read() == b'2345'
        conn.close()

        # 端口已被在跑的实例占用时，第二个实例在 create_app() 之前就拿不到端口
        with pytest.raises(OSError):
            http_server.reserve_listen_socket('127.0.0.1', port)
    finally:
        server.close()
        loop.join(timeout=5)


def test_background_tasks_claimed_once_per_process(monkeypatch):
    monkeypatch.setattr(background_tasks_guard, '_owner', None)
    monkeypatch.setattr(background_tasks_guard.multiprocessing, 'parent_process', lambda: None)

    assert background_tasks_guard.claim_background_tasks('first') is True
    # 训练/导出线程里 `from run import create_app` 再调一次时不得再启动后台任务
    assert background_tasks_guard.claim_background_tasks('second') is False
    assert background_tasks_guard.background_tasks_owner() == 'first'


def test_background_tasks_never_claimed_in_multiprocessing_child(monkeypatch):
    monkeypatch.setattr(background_tasks_guard, '_owner', None)
    monkeypatch.setattr(background_tasks_guard.multiprocessing, 'parent_process', lambda: object())

    assert background_tasks_guard.claim_background_tasks('spawn child') is False
    assert background_tasks_guard.background_tasks_owner() is None
