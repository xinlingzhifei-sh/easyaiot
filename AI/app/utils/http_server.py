"""HTTP 服务承载：生产用 waitress（单进程多线程），本地调试可切回 Flask 开发服务器。

为什么必须是单进程：create_app() 会在进程内启动 Nacos 注册心跳、APScheduler 定时任务、
算法/推流守护（拉起 RUNTIME 子进程）等后台任务，任务注册表、SSE 订阅队列等状态也只存在于
本进程内存。多进程 worker（如 gunicorn -w N）会让每个 worker 各执行一次 create_app()，
后台任务随之重复 N 份，请求落到不同 worker 时还看不到彼此的任务状态。waitress 只有一个进程，
用线程池并发处理请求，与这种"进程内后台线程"模型兼容；Flask 开发服务器原本也是单进程多线程，
所以应用侧的线程安全要求不变。

本文件在 AI/app/utils/http_server.py 与 VIDEO/app/utils/http_server.py 各有一份
（两个模块可单独部署，容器内只挂载各自目录），修改时请两边同步。
"""
import logging
import os
import socket

logger = logging.getLogger(__name__)

SERVER_WAITRESS = 'waitress'
SERVER_DEV = 'dev'
SERVER_CHOICES = (SERVER_WAITRESS, SERVER_DEV)

# 未设置 MAX_CONTENT_LENGTH 时的请求体上限。waitress 默认 1 GiB，比原 Flask 开发服务器（不限）严，
# 会让大模型文件上传直接 413，故放宽到 5 GiB（与 AI 训练数据集上传默认上限一致）。
_DEFAULT_MAX_REQUEST_BODY_MB = 5120
# waitress 默认 100。超过后不再 accept，新连接要等空闲 keep-alive 连接超时（channel_timeout）才进得来；
# 原 Flask 开发服务器不限连接数，取一个足够宽的值。
_DEFAULT_CONNECTION_LIMIT = 1000


def add_server_arguments(parser):
    """给 run.py 的 argparse 加上服务器选择参数（--server / --dev 写同一个 dest，命令行后者覆盖前者）。"""
    parser.add_argument(
        '--server', choices=SERVER_CHOICES, default=None,
        help='HTTP 服务器：waitress（生产，默认）或 dev（Flask 开发服务器）；未指定时读 <模块>_HTTP_SERVER 环境变量',
    )
    parser.add_argument(
        '--dev', dest='server', action='store_const', const=SERVER_DEV,
        help='本地调试用 Flask 开发服务器，等价于 --server=dev',
    )


def resolve_server_kind(cli_value, env_name):
    """命令行优先，其次环境变量，缺省 waitress。"""
    value = (cli_value or os.getenv(env_name, '') or SERVER_WAITRESS).strip().lower()
    if value not in SERVER_CHOICES:
        raise ValueError(f'{env_name}={value!r} 无效，可选值: {", ".join(SERVER_CHOICES)}')
    return value


def require_waitress():
    """在 create_app() 之前确认 waitress 可用，缺依赖时不要先把后台任务拉起来再失败。"""
    try:
        import waitress  # noqa: F401
    except ImportError as exc:
        raise SystemExit(
            '❌ 未安装 waitress：Docker 部署请重建镜像（install_linux.sh build），'
            '本地请 pip install -r requirements.txt；临时调试可加 --dev 改用 Flask 开发服务器'
        ) from exc


def reserve_listen_socket(host, port):
    """在 create_app() 之前占住监听端口，端口被占用时抛 OSError。

    create_app() 会启动后台任务、拉起 RUNTIME 子进程；同一端口上已有实例在跑时必须在此之前失败，
    否则会短暂出现两份后台任务。返回已 bind、尚未 listen 的 socket，直接交给 waitress，
    避免"先检查端口、再绑定"之间被别人抢走。
    """
    infos = socket.getaddrinfo(host or None, port, type=socket.SOCK_STREAM, flags=socket.AI_PASSIVE)
    family, socktype, proto, _canonname, sockaddr = infos[0]
    sock = socket.socket(family, socktype, proto)
    try:
        if os.name != 'nt':
            # POSIX 上 SO_REUSEADDR 只放过 TIME_WAIT（重启后立即重绑），仍拒绝与在监听的实例共用端口；
            # Windows 上它会允许抢占已占用端口，故不设置。
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(sockaddr)
    except OSError:
        sock.close()
        raise
    return sock


def _env_int(name, default):
    raw = os.getenv(name, '').strip()
    if not raw:
        return default
    try:
        value = int(raw)
    except ValueError:
        raise ValueError(f'{name}={raw!r} 不是整数') from None
    if value <= 0:
        raise ValueError(f'{name} 必须大于 0，当前为 {value}')
    return value


def build_waitress_options(app, env_prefix, default_threads):
    """waitress 参数，均可用 <env_prefix>_HTTP_* 环境变量覆盖。"""
    max_body = app.config.get('MAX_CONTENT_LENGTH')
    if not max_body:
        max_body = _env_int(f'{env_prefix}_HTTP_MAX_REQUEST_BODY_MB', _DEFAULT_MAX_REQUEST_BODY_MB) * 1024 * 1024
    return {
        # SSE、流式下载/回放、LLM 流式对话会在整个响应期间占住一个线程，线程数要留足
        'threads': _env_int(f'{env_prefix}_HTTP_THREADS', default_threads),
        'connection_limit': _env_int(f'{env_prefix}_HTTP_CONNECTION_LIMIT', _DEFAULT_CONNECTION_LIMIT),
        # 空闲 keep-alive 连接的关闭时间；处理中的请求不受影响
        'channel_timeout': _env_int(f'{env_prefix}_HTTP_CHANNEL_TIMEOUT', 120),
        # waitress 会在把请求交给应用前先收完请求体，必须不比应用自身的上限更严
        'max_request_body_size': int(max_body),
        # Flask 开发服务器原样透传 X-Forwarded-*，waitress 默认会剥掉未受信代理头；保持原行为
        'clear_untrusted_proxy_headers': False,
        # select() 受 FD_SETSIZE 限制，进程持有大量子进程管道/数据库连接时 fd 编号容易超限；
        # 没有 poll 的平台（Windows）waitress 会自动回落到 select
        'asyncore_use_poll': True,
    }


def create_waitress_server(app, sock, env_prefix, default_threads):
    """在已占住的 sock 上创建 waitress 服务器（开始 listen，但尚未进入事件循环）。"""
    from waitress.server import create_server

    return create_server(app, sockets=[sock], **build_waitress_options(app, env_prefix, default_threads))


def serve_with_waitress(app, sock, env_prefix, default_threads):
    """用已占住的 sock 启动 waitress，阻塞到 SIGTERM/SIGINT（SystemExit/KeyboardInterrupt）后返回。"""
    server = create_waitress_server(app, sock, env_prefix, default_threads)
    adj = server.adj
    host, port = sock.getsockname()[:2]
    print(
        f"✅ waitress 已监听 {host}:{port}（threads={adj.threads}, connection_limit={adj.connection_limit}, "
        f"channel_timeout={adj.channel_timeout}s, max_request_body={adj.max_request_body_size // (1024 * 1024)}MB）"
    )
    # run() 捕获 SystemExit/KeyboardInterrupt 后关闭线程池并返回，之后由解释器正常退出并执行 atexit 钩子
    server.run()


def run_dev_server(app, host, port):
    """Flask 开发服务器，仅供本地调试。

    显式关闭 reloader：FLASK_DEBUG=1 时 Flask 默认启用 reloader，父进程在 app.run 之前已执行过
    create_app()，再派生的子进程会再执行一遍，后台任务随之启动两份。
    """
    app.run(host=host, port=port, threaded=True, use_reloader=False)
