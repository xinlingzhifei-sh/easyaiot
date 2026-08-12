"""Process-wide resource controls for ffmpeg work."""
from __future__ import annotations

import os
import shutil
import subprocess
import threading
import time
from contextlib import contextmanager


_MAX_CONCURRENT_ENV = 'YFEIEYE_FFMPEG_MAX_CONCURRENT'
_SLOT_WAIT_SECONDS_ENV = 'YFEIEYE_FFMPEG_SLOT_WAIT_SECONDS'
_THREADS_ENV = 'YFEIEYE_FFMPEG_THREADS'
_FILTER_THREADS_ENV = 'YFEIEYE_FFMPEG_FILTER_THREADS'
_TIMEOUT_BASE_SECONDS_ENV = 'YFEIEYE_FFMPEG_TIMEOUT_BASE_SECONDS'
_TIMEOUT_PER_MEDIA_SECOND_ENV = 'YFEIEYE_FFMPEG_TIMEOUT_PER_MEDIA_SECOND'
_TIMEOUT_MAX_SECONDS_ENV = 'YFEIEYE_FFMPEG_TIMEOUT_MAX_SECONDS'
_OUTPUT_POLL_SECONDS_ENV = 'YFEIEYE_FFMPEG_OUTPUT_POLL_SECONDS'
_MIN_FREE_BYTES_ENV = 'YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES'

_DEFAULT_MAX_CONCURRENT = 2
_DEFAULT_SLOT_WAIT_SECONDS = 30.0
_DEFAULT_THREADS = 2
_DEFAULT_FILTER_THREADS = 1
_DEFAULT_TIMEOUT_BASE_SECONDS = 30.0
_DEFAULT_TIMEOUT_PER_MEDIA_SECOND = 4.0
_DEFAULT_TIMEOUT_MAX_SECONDS = 600.0
_DEFAULT_OUTPUT_POLL_SECONDS = 0.1
_DEFAULT_MIN_FREE_BYTES = 512 * 1024 * 1024


def _positive_int(value, fallback: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return int(fallback)


def _positive_float(value, fallback: float, allow_zero=False) -> float:
    try:
        normalized = float(value)
    except (TypeError, ValueError):
        normalized = float(fallback)
    minimum = 0.0 if allow_zero else 0.001
    return max(minimum, normalized)


_FFMPEG_LIMIT = None
_FFMPEG_SEMAPHORE = None
_FFMPEG_CONFIG_LOCK = threading.Lock()
_STORAGE_RESERVATIONS = {}
_STORAGE_RESERVATION_LOCK = threading.Lock()


def _ffmpeg_semaphore():
    """Resolve capacity lazily after the service has loaded its dotenv file."""
    global _FFMPEG_LIMIT, _FFMPEG_SEMAPHORE
    if _FFMPEG_SEMAPHORE is not None:
        return _FFMPEG_SEMAPHORE
    with _FFMPEG_CONFIG_LOCK:
        if _FFMPEG_SEMAPHORE is None:
            _FFMPEG_LIMIT = max(1, _positive_int(
                os.environ.get(_MAX_CONCURRENT_ENV),
                _DEFAULT_MAX_CONCURRENT,
            ))
            _FFMPEG_SEMAPHORE = threading.BoundedSemaphore(_FFMPEG_LIMIT)
    return _FFMPEG_SEMAPHORE


@contextmanager
def ffmpeg_slot(wait_seconds=None):
    """Acquire one bounded process-wide ffmpeg execution slot."""
    timeout = _positive_float(
        wait_seconds if wait_seconds is not None else os.environ.get(_SLOT_WAIT_SECONDS_ENV),
        _DEFAULT_SLOT_WAIT_SECONDS,
    )
    semaphore = _ffmpeg_semaphore()
    acquired = semaphore.acquire(timeout=timeout)
    if not acquired:
        raise RuntimeError('ffmpeg capacity is exhausted')
    try:
        yield
    finally:
        semaphore.release()


@contextmanager
def media_storage_slot(path: str, *, incoming_bytes=0,
                       max_total_bytes=None, wait_seconds=None):
    """Serialize source I/O with ffmpeg and check storage before writing."""
    with ffmpeg_slot(wait_seconds=wait_seconds):
        with _storage_reservation(
                path,
                incoming_bytes=incoming_bytes,
                max_total_bytes=max_total_bytes):
            yield


@contextmanager
def _storage_reservation(path: str, *, incoming_bytes=0,
                         max_total_bytes=None):
    try:
        incoming = max(0, int(incoming_bytes or 0))
    except (TypeError, ValueError) as exc:
        raise ValueError('incoming storage size must be an integer') from exc
    reservation_key = os.path.normcase(os.path.abspath(path))
    with _STORAGE_RESERVATION_LOCK:
        already_reserved = _STORAGE_RESERVATIONS.get(reservation_key, 0)
        ensure_storage_capacity(
            path,
            incoming_bytes=already_reserved + incoming,
            max_total_bytes=max_total_bytes,
        )
        _STORAGE_RESERVATIONS[reservation_key] = already_reserved + incoming
    try:
        yield
    finally:
        with _STORAGE_RESERVATION_LOCK:
            remaining = _STORAGE_RESERVATIONS.get(reservation_key, 0) - incoming
            if remaining > 0:
                _STORAGE_RESERVATIONS[reservation_key] = remaining
            else:
                _STORAGE_RESERVATIONS.pop(reservation_key, None)


def ffmpeg_resource_options() -> list[str]:
    """Return deterministic stdin and CPU bounds shared by all ffmpeg commands."""
    threads = max(1, _positive_int(os.environ.get(_THREADS_ENV), _DEFAULT_THREADS))
    filter_threads = max(
        1, _positive_int(os.environ.get(_FILTER_THREADS_ENV), _DEFAULT_FILTER_THREADS))
    return [
        '-nostdin',
        '-threads', str(threads),
        '-filter_threads', str(filter_threads),
    ]


def ffmpeg_output_thread_options() -> list[str]:
    """Bound encoder/output threads in addition to decoder threads."""
    threads = max(1, _positive_int(os.environ.get(_THREADS_ENV), _DEFAULT_THREADS))
    return ['-threads', str(threads)]


def ffmpeg_timeout_seconds(media_duration_seconds=None) -> float:
    """Scale execution time with input duration without exceeding a hard cap."""
    base = _positive_float(
        os.environ.get(_TIMEOUT_BASE_SECONDS_ENV), _DEFAULT_TIMEOUT_BASE_SECONDS)
    per_media_second = _positive_float(
        os.environ.get(_TIMEOUT_PER_MEDIA_SECOND_ENV),
        _DEFAULT_TIMEOUT_PER_MEDIA_SECOND,
        allow_zero=True,
    )
    hard_cap = _positive_float(
        os.environ.get(_TIMEOUT_MAX_SECONDS_ENV), _DEFAULT_TIMEOUT_MAX_SECONDS)
    if media_duration_seconds in (None, ''):
        return hard_cap
    try:
        duration = max(0.0, float(media_duration_seconds))
    except (TypeError, ValueError):
        duration = 0.0
    return min(hard_cap, base + duration * per_media_second)


def run_ffmpeg_guarded(command: list[str], *, output_path=None,
                       expected_duration=None, max_output_bytes=None,
                       poll_seconds=None, quota_path=None, max_total_bytes=None):
    """Run a child process with shared capacity, deadline, and live disk bounds."""
    timeout_seconds = ffmpeg_timeout_seconds(expected_duration)
    poll_interval = _positive_float(
        poll_seconds if poll_seconds is not None else os.environ.get(_OUTPUT_POLL_SECONDS_ENV),
        _DEFAULT_OUTPUT_POLL_SECONDS,
    )
    output_limit = _optional_positive_int(max_output_bytes)
    total_limit = _optional_positive_int(max_total_bytes)
    if quota_path:
        ensure_storage_capacity(
            quota_path,
            incoming_bytes=0,
            max_total_bytes=total_limit,
        )
    with ffmpeg_slot():
        started_at = time.monotonic()
        try:
            process = subprocess.Popen(
                command,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
        except OSError as exc:
            raise RuntimeError(f'ffmpeg process could not start: {exc}') from exc

        try:
            stdout = b''
            stderr = b''
            while True:
                _enforce_disk_limits(
                    output_path=output_path,
                    max_output_bytes=output_limit,
                    quota_path=quota_path,
                    max_total_bytes=total_limit,
                    process=process,
                )
                elapsed = time.monotonic() - started_at
                remaining = timeout_seconds - elapsed
                if remaining <= 0:
                    raise RuntimeError(
                        f'ffmpeg timed out after {timeout_seconds:.3f} seconds')
                try:
                    stdout, stderr = process.communicate(
                        timeout=min(poll_interval, remaining))
                    break
                except subprocess.TimeoutExpired:
                    continue

            _enforce_disk_limits(
                output_path=output_path,
                max_output_bytes=output_limit,
                quota_path=quota_path,
                max_total_bytes=total_limit,
                process=None,
            )
            return subprocess.CompletedProcess(
                command, process.returncode, stdout, stderr)
        finally:
            if process.poll() is None:
                _terminate_process(process)


def _enforce_disk_limits(*, output_path, max_output_bytes, quota_path,
                         max_total_bytes, process):
    if output_path and max_output_bytes is not None:
        try:
            output_bytes = os.path.getsize(output_path)
        except OSError:
            output_bytes = 0
        if output_bytes > max_output_bytes:
            if process is not None:
                _terminate_process(process)
            raise RuntimeError('ffmpeg output size limit exceeded')
    if quota_path and max_total_bytes is not None:
        total_bytes = _tree_size(quota_path)
        if total_bytes > max_total_bytes:
            if process is not None:
                _terminate_process(process)
            raise RuntimeError('ffmpeg temporary storage quota exceeded')
    if quota_path:
        reserve = max(0, _positive_int(
            os.environ.get(_MIN_FREE_BYTES_ENV), _DEFAULT_MIN_FREE_BYTES))
        if _disk_usage_for_path(quota_path).free < reserve:
            if process is not None:
                _terminate_process(process)
            raise RuntimeError('ffmpeg free space reserve would be exhausted')


def ensure_storage_capacity(path: str, *, incoming_bytes=0,
                            max_total_bytes=None) -> None:
    """Reject a materialization before it consumes cache quota or disk reserve."""
    try:
        incoming = max(0, int(incoming_bytes or 0))
    except (TypeError, ValueError) as exc:
        raise ValueError('incoming storage size must be an integer') from exc
    total_limit = _optional_positive_int(max_total_bytes)
    if total_limit is not None and _tree_size(path) + incoming > total_limit:
        raise RuntimeError('media storage quota would be exceeded')
    reserve = max(0, _positive_int(
        os.environ.get(_MIN_FREE_BYTES_ENV), _DEFAULT_MIN_FREE_BYTES))
    if _disk_usage_for_path(path).free - incoming < reserve:
        raise RuntimeError('media free space reserve would be exhausted')


def _terminate_process(process) -> None:
    if process.poll() is not None:
        try:
            process.communicate(timeout=0.1)
        except Exception:
            pass
        return
    try:
        process.terminate()
        process.communicate(timeout=2)
        return
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        process.kill()
    except OSError:
        pass
    try:
        process.communicate(timeout=2)
    except Exception:
        pass


def _tree_size(path: str) -> int:
    if not path or not os.path.exists(path):
        return 0
    if os.path.isfile(path):
        try:
            return os.path.getsize(path)
        except OSError:
            return 0
    total = 0
    for root, _directories, files in os.walk(path):
        for name in files:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                continue
    return total


def _disk_usage_for_path(path: str):
    candidate = os.path.abspath(path)
    while not os.path.exists(candidate):
        parent = os.path.dirname(candidate)
        if parent == candidate:
            break
        candidate = parent
    return shutil.disk_usage(candidate)


def _optional_positive_int(value):
    if value is None:
        return None
    return max(1, _positive_int(value, 1))
