"""create_app() 后台任务的进程级"只启动一次"守卫。

run.py 的 create_app() 会启动 Nacos 注册心跳、定时任务、守护线程等后台任务，这些任务只应属于
对外提供 HTTP 的那个进程，且只启动一次。但 create_app() 还会在别处被再次调用：
- 同进程内 `from run import create_app; create_app()` 取应用上下文（AI 的训练/导出线程即如此）。
  服务进程的主模块是 __main__，`import run` 会再加载一份 run.py，模块级变量不共享，
  所以守卫状态放在本模块（sys.modules 里只有一份）；
- multiprocessing 子进程（spawn 方式会重新导入主模块）里调用 create_app()。
这些调用只需要数据库与蓝图，不应再起一份后台任务。

本文件在 AI/app/utils/ 与 VIDEO/app/utils/ 各有一份，修改时请两边同步。
"""
import multiprocessing
import threading

_lock = threading.Lock()
_owner = None


def claim_background_tasks(owner):
    """当前进程首次认领且不是 multiprocessing 子进程时返回 True；否则返回 False。"""
    global _owner
    if multiprocessing.parent_process() is not None:
        return False
    with _lock:
        if _owner is not None:
            return False
        _owner = owner
        return True


def background_tasks_owner():
    """已认领后台任务的调用方标识；尚未认领时为 None。"""
    return _owner
