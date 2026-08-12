"""Materialize verified browser-seekable MP4 files from recorded media."""
from __future__ import annotations

import hashlib
import json
import os
import shutil
import subprocess
import threading
import time
import uuid
from contextlib import contextmanager

from app.services.media_resource_guard import (
    ffmpeg_output_thread_options,
    ffmpeg_resource_options,
    media_storage_slot,
    run_ffmpeg_guarded,
)


_CACHE_DIR_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_DIR'
_LOCK_WAIT_SECONDS_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_LOCK_WAIT_SECONDS'
_CACHE_TTL_SECONDS_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS'
_CACHE_MAX_BYTES_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES'
_LOCK_STALE_SECONDS_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_LOCK_STALE_SECONDS'
_LOCK_HEARTBEAT_SECONDS_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_LOCK_HEARTBEAT_SECONDS'
_READ_LEASE_STALE_SECONDS_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_READ_LEASE_STALE_SECONDS'
_MAX_OUTPUT_BYTES_ENV = 'YFEIEYE_SEEKABLE_PLAYBACK_MAX_OUTPUT_BYTES'
_DEFAULT_CACHE_TTL_SECONDS = 24 * 60 * 60
_DEFAULT_CACHE_MAX_BYTES = 2 * 1024 * 1024 * 1024
_DEFAULT_LOCK_STALE_SECONDS = 60
_DEFAULT_LOCK_HEARTBEAT_SECONDS = 5
_DEFAULT_READ_LEASE_STALE_SECONDS = 24 * 60 * 60
_DEFAULT_MAX_OUTPUT_BYTES = 2 * 1024 * 1024 * 1024


def prepare_seekable_mp4_path(source_path: str | None = None,
                              acquire_lease: bool = False, *,
                              source_identity=None, source_size_bytes=None,
                              materialize_source=None) -> dict:
    cache_dir = _cache_directory()
    os.makedirs(cache_dir, mode=0o750, exist_ok=True)
    cleanup_seekable_playback_cache(cache_dir=cache_dir)
    source_path, source_identity, source_size_bytes = _normalize_source(
        source_path,
        source_identity=source_identity,
        source_size_bytes=source_size_bytes,
        materialize_source=materialize_source,
    )
    identity_key = _sha256_text(source_identity).removeprefix('sha256:')
    identity_metadata_path = os.path.join(
        cache_dir, f'.identity-{identity_key}.json')
    identity_lock_path = os.path.join(
        cache_dir, f'.identity-{identity_key}.lock')

    cached = _read_identity_cache(identity_metadata_path, cache_dir)
    if cached:
        cached = _prepare_result_with_optional_lease(
            cached, cached['path'], acquire_lease)
        if cached:
            cached['cache_hit'] = True
            return cached

    identity_token = _claim_lock(identity_lock_path)
    if not identity_token:
        cached = _wait_for_identity_cache(
            identity_metadata_path, cache_dir, identity_lock_path)
        if cached:
            cached = _prepare_result_with_optional_lease(
                cached, cached['path'], acquire_lease)
            if cached:
                cached['cache_hit'] = True
                return cached
        identity_token = _claim_lock(identity_lock_path, remove_stale=True)
    if not identity_token:
        raise RuntimeError('seekable playback source is busy')

    snapshot_path = os.path.join(cache_dir, f'.source-{uuid.uuid4().hex}.snapshot')
    try:
        with _lock_heartbeat(identity_lock_path, identity_token):
            cached = _read_identity_cache(identity_metadata_path, cache_dir)
            if cached:
                cached = _prepare_result_with_optional_lease(
                    cached, cached['path'], acquire_lease)
                if cached:
                    cached['cache_hit'] = True
                    return cached

            with media_storage_slot(
                cache_dir,
                incoming_bytes=source_size_bytes,
                max_total_bytes=_configured_positive_int(
                    _CACHE_MAX_BYTES_ENV, _DEFAULT_CACHE_MAX_BYTES),
            ):
                if materialize_source is None:
                    _copy_stable_source(source_path, snapshot_path)
                else:
                    materialize_source(snapshot_path)
                    _validate_materialized_source(snapshot_path)
            prepared = _prepare_seekable_snapshot(
                snapshot_path, cache_dir, acquire_lease)
            _atomic_write_json(identity_metadata_path, {
                'source_sha256': prepared['source_sha256'],
                'updated_at': time.time(),
            })
            return prepared
    finally:
        try:
            os.unlink(snapshot_path)
        except FileNotFoundError:
            pass
        _release_owned_lock(identity_lock_path, identity_token)


def _normalize_source(source_path, *, source_identity, source_size_bytes,
                      materialize_source):
    if materialize_source is None:
        source_path = os.path.abspath(
            os.path.expanduser(str(source_path or '').strip()))
        if not source_path or not os.path.isfile(source_path):
            raise RuntimeError('recording source file is missing')
        source_stat = os.stat(source_path)
        source_identity = str(source_identity or (
            f'local:{source_path}:{source_stat.st_size}:{source_stat.st_mtime_ns}'
        ))
        source_size_bytes = source_stat.st_size
        return source_path, source_identity, source_size_bytes
    if not callable(materialize_source):
        raise RuntimeError('recording source materializer is invalid')
    source_identity = str(source_identity or '').strip()
    if not source_identity:
        raise RuntimeError('recording source identity is required')
    try:
        source_size_bytes = int(source_size_bytes)
    except (TypeError, ValueError) as exc:
        raise RuntimeError('recording source size is required') from exc
    if source_size_bytes <= 0:
        raise RuntimeError('recording source size is required')
    return None, source_identity, source_size_bytes


def _validate_materialized_source(snapshot_path):
    if not os.path.isfile(snapshot_path) or os.path.getsize(snapshot_path) <= 0:
        raise RuntimeError('recording source is empty')


def _prepare_seekable_snapshot(snapshot_path, cache_dir, acquire_lease):
    source_hash = _sha256_file(snapshot_path)
    cache_key = source_hash.removeprefix('sha256:')
    output_path = os.path.join(cache_dir, f'{cache_key}.mp4')
    metadata_path = os.path.join(cache_dir, f'{cache_key}.json')
    lock_path = os.path.join(cache_dir, f'{cache_key}.lock')

    cached = _read_verified_cache(output_path, metadata_path, source_hash)
    if cached:
        cached = _prepare_result_with_optional_lease(
            cached, output_path, acquire_lease)
        if cached:
            cached['cache_hit'] = True
            return cached

    lock_token = _claim_lock(lock_path)
    if not lock_token:
        cached = _wait_for_cache(output_path, metadata_path, source_hash, lock_path)
        if cached:
            cached = _prepare_result_with_optional_lease(
                cached, output_path, acquire_lease)
            if cached:
                cached['cache_hit'] = True
                return cached
        lock_token = _claim_lock(lock_path, remove_stale=True)
    if not lock_token:
        raise RuntimeError('seekable playback cache is busy')

    temp_output = os.path.join(cache_dir, f'.output-{uuid.uuid4().hex}.mp4')
    try:
        source_probe = _probe(snapshot_path)
        source_duration = _probe_duration_seconds(source_probe)
        command = _build_ffmpeg_command(
            snapshot_path, temp_output, source_probe=source_probe)
        with _lock_heartbeat(lock_path, lock_token):
            result = _execute_ffmpeg(
                command,
                output_path=temp_output,
                expected_duration=source_duration,
            )
            if result.returncode != 0:
                error = result.stderr.decode('utf-8', errors='replace').strip()
                raise RuntimeError(f'ffmpeg seekable playback failed: {error[-1000:]}')
            verified = _verify_mp4(temp_output)
            output_hash = _sha256_file(temp_output)
            os.replace(temp_output, output_path)
            metadata = {
                'path': output_path,
                'content_type': 'video/mp4',
                'source_sha256': source_hash,
                'output_sha256': output_hash,
                'duration_seconds': verified['duration_seconds'],
                'video_codec': verified['video_codec'],
                'ffmpeg_command_sha256': _sha256_text(json.dumps(command, ensure_ascii=False)),
                'last_accessed_at': time.time(),
            }
            _atomic_write_json(metadata_path, metadata)
            metadata = _prepare_result_with_optional_lease(
                metadata, output_path, acquire_lease)
            if metadata is None:
                raise RuntimeError('seekable playback cache lease could not be acquired')
            metadata['cache_hit'] = False
            return metadata
    finally:
        if os.path.exists(temp_output):
            os.unlink(temp_output)
        _release_owned_lock(lock_path, lock_token)


def _prepare_result_with_optional_lease(metadata, output_path, acquire_lease):
    result = dict(metadata or {})
    if not acquire_lease:
        return result
    try:
        result['lease'] = acquire_seekable_playback_lease(output_path)
    except RuntimeError:
        return None
    return result


def _copy_stable_source(source_path: str, snapshot_path: str) -> None:
    before = os.stat(source_path)
    with open(source_path, 'rb') as source, open(snapshot_path, 'xb') as snapshot:
        shutil.copyfileobj(source, snapshot, length=1024 * 1024)
        snapshot.flush()
        os.fsync(snapshot.fileno())
    after = os.stat(source_path)
    if before.st_size != after.st_size or before.st_mtime_ns != after.st_mtime_ns:
        try:
            os.unlink(snapshot_path)
        except FileNotFoundError:
            pass
        raise RuntimeError('recording source changed while preparing playback')
    if os.path.getsize(snapshot_path) <= 0:
        raise RuntimeError('recording source is empty')


def _build_ffmpeg_command(source_path: str, output_path: str,
                          source_probe=None) -> list[str]:
    source_probe = source_probe or _probe(source_path)
    video_streams = [stream for stream in source_probe.get('streams', [])
                     if stream.get('codec_type') == 'video']
    if not video_streams:
        raise RuntimeError('recording source has no video stream')
    video_codec = str(video_streams[0].get('codec_name') or '').lower()
    audio_streams = [stream for stream in source_probe.get('streams', [])
                     if stream.get('codec_type') == 'audio']
    command = [
        'ffmpeg', *ffmpeg_resource_options(),
        '-hide_banner', '-loglevel', 'error', '-y',
        '-fflags', '+genpts', '-i', source_path,
        '-map', '0:v:0', '-map', '0:a?',
    ]
    if video_codec == 'h264':
        command.extend(['-c:v', 'copy'])
    else:
        command.extend([
            '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20',
            '-pix_fmt', 'yuv420p',
        ])
    if audio_streams:
        command.extend(['-c:a', 'aac', '-b:a', '128k'])
    command.extend(ffmpeg_output_thread_options())
    command.extend([
        '-avoid_negative_ts', 'make_zero',
        '-movflags', '+faststart', '-f', 'mp4', output_path,
    ])
    return command


def _execute_ffmpeg(command: list[str], output_path=None, expected_duration=None):
    return run_ffmpeg_guarded(
        command,
        output_path=output_path,
        expected_duration=expected_duration,
        max_output_bytes=_configured_positive_int(
            _MAX_OUTPUT_BYTES_ENV, _DEFAULT_MAX_OUTPUT_BYTES),
        quota_path=os.path.dirname(os.path.abspath(output_path)) if output_path else None,
        max_total_bytes=_configured_positive_int(
            _CACHE_MAX_BYTES_ENV, _DEFAULT_CACHE_MAX_BYTES),
    )


def _probe_duration_seconds(probe: dict):
    try:
        duration = float((probe.get('format') or {}).get('duration') or 0)
    except (AttributeError, TypeError, ValueError):
        return None
    return duration if duration > 0 else None


def _verify_mp4(path: str) -> dict:
    if not os.path.isfile(path) or os.path.getsize(path) <= 1024:
        raise RuntimeError('seekable playback output is empty')
    probe = _probe(path)
    video_streams = [stream for stream in probe.get('streams', [])
                     if stream.get('codec_type') == 'video']
    if not video_streams or video_streams[0].get('codec_name') != 'h264':
        raise RuntimeError('seekable playback output is not browser-compatible H.264')
    try:
        duration = float((probe.get('format') or {}).get('duration') or 0)
    except (TypeError, ValueError):
        duration = 0
    if not duration > 0:
        raise RuntimeError('seekable playback output duration is invalid')
    format_name = str((probe.get('format') or {}).get('format_name') or '')
    if 'mp4' not in format_name:
        raise RuntimeError('seekable playback output is not MP4')
    return {'duration_seconds': duration, 'video_codec': 'h264'}


def _probe(path: str) -> dict:
    try:
        result = subprocess.run([
            'ffprobe', '-v', 'error', '-show_streams', '-show_format',
            '-of', 'json', path,
        ], capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError(f'ffprobe is unavailable: {exc}') from exc
    if result.returncode != 0:
        raise RuntimeError(f'ffprobe failed: {result.stderr.strip()[-1000:]}')
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise RuntimeError('ffprobe returned invalid JSON') from exc


def _claim_lock(lock_path: str, remove_stale=False, stale_seconds=None):
    if remove_stale and _lock_is_stale(lock_path, stale_seconds=stale_seconds):
        _quarantine_stale_lock(lock_path, stale_seconds=stale_seconds)
    token = uuid.uuid4().hex
    try:
        descriptor = os.open(lock_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        return None
    try:
        payload = json.dumps({
            'token': token,
            'pid': os.getpid(),
            'claimedAt': time.time(),
        }, sort_keys=True, separators=(',', ':')) + '\n'
        os.write(descriptor, payload.encode('ascii'))
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
    return token


def _quarantine_stale_lock(lock_path: str, stale_seconds=None) -> bool:
    try:
        before = os.stat(lock_path, follow_symlinks=False)
    except OSError:
        return False
    threshold = _lock_stale_seconds(stale_seconds)
    if before.st_mtime >= time.time() - threshold:
        return False
    quarantine = f'{lock_path}.stale-{uuid.uuid4().hex}'
    try:
        os.replace(lock_path, quarantine)
    except OSError:
        return False
    try:
        try:
            after = os.stat(quarantine, follow_symlinks=False)
        except OSError:
            return False
        if after.st_mtime >= time.time() - threshold:
            try:
                descriptor = os.open(lock_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            except FileExistsError:
                return False
            else:
                os.close(descriptor)
                os.replace(quarantine, lock_path)
                return False
        return True
    finally:
        try:
            os.unlink(quarantine)
        except FileNotFoundError:
            pass


def _read_lock_token(lock_path: str):
    try:
        with open(lock_path, 'r', encoding='ascii') as handle:
            raw = handle.read(4096).strip()
    except (OSError, UnicodeError):
        return None
    try:
        value = json.loads(raw)
    except (TypeError, ValueError):
        return raw or None
    if not isinstance(value, dict):
        return None
    token = str(value.get('token') or '').strip()
    return token or None


def _release_owned_lock(lock_path: str, token) -> bool:
    if not token or _read_lock_token(lock_path) != token:
        return False
    try:
        os.unlink(lock_path)
        return True
    except FileNotFoundError:
        return False


def _touch_owned_lock(lock_path: str, token) -> bool:
    if not token or _read_lock_token(lock_path) != token:
        return False
    try:
        os.utime(lock_path, None)
        return _read_lock_token(lock_path) == token
    except OSError:
        return False


@contextmanager
def _lock_heartbeat(lock_path: str, token, interval_seconds=None):
    interval = _configured_positive_float(
        _LOCK_HEARTBEAT_SECONDS_ENV,
        _DEFAULT_LOCK_HEARTBEAT_SECONDS,
        explicit=interval_seconds,
    )
    stopped = threading.Event()

    def heartbeat():
        while not stopped.wait(interval):
            if not _touch_owned_lock(lock_path, token):
                return

    _touch_owned_lock(lock_path, token)
    worker = threading.Thread(target=heartbeat, name='seekable-lock-heartbeat', daemon=True)
    worker.start()
    try:
        yield
    finally:
        stopped.set()
        worker.join(timeout=max(1.0, interval * 2))


def _wait_for_cache(output_path, metadata_path, source_hash, lock_path):
    try:
        wait_seconds = max(1, min(int(os.environ.get(_LOCK_WAIT_SECONDS_ENV, '30')), 120))
    except ValueError:
        wait_seconds = 30
    deadline = time.monotonic() + wait_seconds
    while time.monotonic() < deadline:
        cached = _read_verified_cache(output_path, metadata_path, source_hash)
        if cached:
            return cached
        if not os.path.exists(lock_path):
            return None
        time.sleep(0.1)
    return None


def _read_identity_cache(identity_metadata_path, cache_dir):
    try:
        with open(identity_metadata_path, 'r', encoding='utf-8') as handle:
            identity_metadata = json.load(handle)
        source_hash = str(identity_metadata.get('source_sha256') or '')
    except (OSError, TypeError, ValueError, json.JSONDecodeError):
        return None
    if not source_hash.startswith('sha256:') or len(source_hash) != 71:
        return None
    cache_key = source_hash.removeprefix('sha256:')
    return _read_verified_cache(
        os.path.join(cache_dir, f'{cache_key}.mp4'),
        os.path.join(cache_dir, f'{cache_key}.json'),
        source_hash,
    )


def _wait_for_identity_cache(identity_metadata_path, cache_dir, lock_path):
    try:
        wait_seconds = max(1, min(int(os.environ.get(_LOCK_WAIT_SECONDS_ENV, '30')), 120))
    except ValueError:
        wait_seconds = 30
    deadline = time.monotonic() + wait_seconds
    while time.monotonic() < deadline:
        cached = _read_identity_cache(identity_metadata_path, cache_dir)
        if cached:
            return cached
        if not os.path.exists(lock_path):
            return None
        time.sleep(0.1)
    return None


def _lock_is_stale(lock_path: str, stale_seconds=None) -> bool:
    try:
        return os.path.getmtime(lock_path) < time.time() - _lock_stale_seconds(stale_seconds)
    except OSError:
        return False


def _lock_stale_seconds(explicit=None) -> float:
    return _configured_positive_float(
        _LOCK_STALE_SECONDS_ENV,
        _DEFAULT_LOCK_STALE_SECONDS,
        explicit=explicit,
    )


def _read_verified_cache(output_path, metadata_path, source_hash):
    if not os.path.isfile(output_path) or not os.path.isfile(metadata_path):
        return None
    try:
        with open(metadata_path, 'r', encoding='utf-8') as handle:
            metadata = json.load(handle)
        if metadata.get('source_sha256') != source_hash:
            return None
        if metadata.get('output_sha256') != _sha256_file(output_path):
            return None
        _verify_mp4(output_path)
        metadata['path'] = output_path
        metadata['content_type'] = 'video/mp4'
        metadata['last_accessed_at'] = time.time()
        _atomic_write_json(metadata_path, metadata)
        return metadata
    except (OSError, ValueError, RuntimeError, json.JSONDecodeError):
        return None


def acquire_seekable_playback_lease(media_path: str) -> dict:
    media_path = os.path.abspath(str(media_path or '').strip())
    if not media_path.endswith('.mp4') or not os.path.isfile(media_path):
        raise RuntimeError('seekable playback cache file is missing')
    key = os.path.basename(media_path)[:-4]
    cache_dir = os.path.dirname(media_path)
    eviction_lock = os.path.join(cache_dir, f'{key}.evict.lock')
    for _ in range(100):
        if os.path.exists(eviction_lock):
            time.sleep(0.01)
            continue
        token = uuid.uuid4().hex
        lease_path = os.path.join(cache_dir, f'{key}.lease.{token}')
        try:
            descriptor = os.open(lease_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        except FileExistsError:
            continue
        try:
            payload = json.dumps({
                'token': token,
                'pid': os.getpid(),
                'createdAt': time.time(),
            }, sort_keys=True, separators=(',', ':')) + '\n'
            os.write(descriptor, payload.encode('ascii'))
            os.fsync(descriptor)
        finally:
            os.close(descriptor)
        if not os.path.exists(eviction_lock) and os.path.isfile(media_path):
            return {'path': lease_path, 'token': token, 'media_path': media_path}
        release_seekable_playback_lease({'path': lease_path, 'token': token})
        time.sleep(0.01)
    raise RuntimeError('seekable playback cache eviction is busy')


def release_seekable_playback_lease(lease) -> bool:
    if not isinstance(lease, dict):
        return False
    lease_path = os.path.abspath(str(lease.get('path') or '').strip())
    token = str(lease.get('token') or '').strip()
    if not lease_path or not token or _read_lock_token(lease_path) != token:
        return False
    try:
        os.unlink(lease_path)
        return True
    except FileNotFoundError:
        return False


def cleanup_seekable_playback_cache(cache_dir=None, now=None) -> dict:
    cache_dir = os.path.abspath(cache_dir or _cache_directory())
    os.makedirs(cache_dir, mode=0o750, exist_ok=True)
    now = time.time() if now is None else float(now)
    cleanup_lock = os.path.join(cache_dir, '.cleanup.lock')
    cleanup_token = _claim_lock(cleanup_lock, remove_stale=True)
    if not cleanup_token:
        return {
            'status': 'busy',
            'expired_pairs_removed': 0,
            'capacity_pairs_removed': 0,
            'remaining_bytes': _cache_media_bytes(cache_dir),
        }
    expired_removed = 0
    capacity_removed = 0
    cleanup_failures = 0
    ttl_seconds = _configured_positive_int(
        _CACHE_TTL_SECONDS_ENV, _DEFAULT_CACHE_TTL_SECONDS)
    max_bytes = _configured_positive_int(
        _CACHE_MAX_BYTES_ENV, _DEFAULT_CACHE_MAX_BYTES)
    try:
        _remove_stale_temporary_files(cache_dir, now)
        pairs = _cache_pairs(cache_dir)
        for pair in pairs:
            if pair['locked'] or now - pair['last_accessed_at'] <= ttl_seconds:
                continue
            outcome = _remove_cache_pair(pair)
            if outcome == 'removed':
                expired_removed += 1
            elif outcome == 'failed':
                cleanup_failures += 1

        pairs = _cache_pairs(cache_dir)
        remaining_bytes = sum(pair['media_bytes'] for pair in pairs)
        for pair in sorted(pairs, key=lambda value: value['last_accessed_at']):
            if remaining_bytes <= max_bytes:
                break
            if pair['locked']:
                continue
            outcome = _remove_cache_pair(pair)
            if outcome == 'removed':
                capacity_removed += 1
                remaining_bytes -= pair['media_bytes']
            elif outcome == 'failed':
                cleanup_failures += 1
        _remove_orphan_identity_files(cache_dir)
        return {
            'status': 'completed' if cleanup_failures == 0 else 'partial',
            'expired_pairs_removed': expired_removed,
            'capacity_pairs_removed': capacity_removed,
            'cleanup_failures': cleanup_failures,
            'remaining_bytes': max(0, remaining_bytes),
            'ttl_seconds': ttl_seconds,
            'max_bytes': max_bytes,
        }
    finally:
        _release_owned_lock(cleanup_lock, cleanup_token)


def _cache_directory() -> str:
    return os.path.abspath(
        os.environ.get(_CACHE_DIR_ENV)
        or os.path.join(os.getcwd(), 'data', 'seekable-playback')
    )


def _cache_pairs(cache_dir: str) -> list[dict]:
    pairs = []
    for entry in os.scandir(cache_dir):
        if not entry.is_file(follow_symlinks=False) or not entry.name.endswith('.mp4'):
            continue
        if entry.name.startswith('.'):
            continue
        key = entry.name[:-4]
        metadata_path = os.path.join(cache_dir, f'{key}.json')
        lock_path = os.path.join(cache_dir, f'{key}.lock')
        if os.path.exists(lock_path) and _lock_is_stale(lock_path):
            stale_owner = _claim_lock(lock_path, remove_stale=True)
            if stale_owner:
                _release_owned_lock(lock_path, stale_owner)
        leases = _active_read_leases(cache_dir, key)
        try:
            metadata_mtime = os.path.getmtime(metadata_path)
        except OSError:
            metadata_mtime = entry.stat(follow_symlinks=False).st_mtime
        pairs.append({
            'output_path': entry.path,
            'key': key,
            'metadata_path': metadata_path,
            'lock_path': lock_path,
            'locked': os.path.exists(lock_path) or bool(leases),
            'leases': leases,
            'last_accessed_at': max(entry.stat(follow_symlinks=False).st_mtime, metadata_mtime),
            'media_bytes': entry.stat(follow_symlinks=False).st_size,
        })
    return pairs


def _remove_cache_pair(pair: dict) -> str:
    cache_dir = os.path.dirname(pair['output_path'])
    key = pair.get('key') or os.path.basename(pair['output_path'])[:-4]
    eviction_lock = os.path.join(cache_dir, f'{key}.evict.lock')
    eviction_token = _claim_lock(eviction_lock, remove_stale=True)
    if not eviction_token:
        return 'busy'
    try:
        producer_lock = os.path.join(cache_dir, f'{key}.lock')
        if os.path.exists(producer_lock) or _active_read_leases(cache_dir, key):
            return 'busy'
        try:
            os.unlink(pair['output_path'])
        except FileNotFoundError:
            pass
        except OSError:
            return 'failed'
        try:
            os.unlink(pair['metadata_path'])
        except FileNotFoundError:
            pass
        except OSError:
            return 'failed'
        return 'removed'
    finally:
        _release_owned_lock(eviction_lock, eviction_token)


def _active_read_leases(cache_dir: str, key: str, now=None) -> list[str]:
    now = time.time() if now is None else float(now)
    stale_seconds = _configured_positive_float(
        _READ_LEASE_STALE_SECONDS_ENV,
        _DEFAULT_READ_LEASE_STALE_SECONDS,
    )
    prefix = f'{key}.lease.'
    leases = []
    try:
        entries = list(os.scandir(cache_dir))
    except OSError:
        return leases
    for entry in entries:
        if not entry.name.startswith(prefix) or not entry.is_file(follow_symlinks=False):
            continue
        try:
            if entry.stat(follow_symlinks=False).st_mtime < now - stale_seconds:
                os.unlink(entry.path)
                continue
        except OSError:
            continue
        leases.append(entry.path)
    return leases


def _remove_stale_temporary_files(cache_dir: str, now: float) -> None:
    for entry in os.scandir(cache_dir):
        if not entry.is_file(follow_symlinks=False):
            continue
        if not entry.name.startswith(('.source-', '.output-')) and not entry.name.endswith('.tmp'):
            continue
        try:
            if entry.stat(follow_symlinks=False).st_mtime < now - 3600:
                os.unlink(entry.path)
        except OSError:
            pass


def _remove_orphan_identity_files(cache_dir: str) -> None:
    for entry in os.scandir(cache_dir):
        if (not entry.is_file(follow_symlinks=False)
                or not entry.name.startswith('.identity-')
                or not entry.name.endswith('.json')):
            continue
        lock_path = entry.path[:-5] + '.lock'
        if os.path.exists(lock_path):
            continue
        try:
            with open(entry.path, 'r', encoding='utf-8') as handle:
                source_hash = str(json.load(handle).get('source_sha256') or '')
            cache_key = source_hash.removeprefix('sha256:')
            valid = (
                source_hash.startswith('sha256:')
                and len(source_hash) == 71
                and os.path.isfile(os.path.join(cache_dir, f'{cache_key}.mp4'))
                and os.path.isfile(os.path.join(cache_dir, f'{cache_key}.json'))
            )
        except (OSError, TypeError, ValueError, json.JSONDecodeError):
            valid = False
        if not valid:
            try:
                os.unlink(entry.path)
            except OSError:
                pass


def _cache_media_bytes(cache_dir: str) -> int:
    return sum(pair['media_bytes'] for pair in _cache_pairs(cache_dir))


def _configured_positive_int(name: str, fallback: int) -> int:
    try:
        return max(1, int(os.environ.get(name, str(fallback))))
    except (TypeError, ValueError):
        return fallback


def _configured_positive_float(name: str, fallback: float, explicit=None) -> float:
    value = explicit if explicit is not None else os.environ.get(name, str(fallback))
    try:
        return max(0.001, float(value))
    except (TypeError, ValueError):
        return float(fallback)


def _atomic_write_json(path: str, value: dict) -> None:
    temp_path = f'{path}.{uuid.uuid4().hex}.tmp'
    try:
        with open(temp_path, 'x', encoding='utf-8', newline='') as handle:
            json.dump(value, handle, ensure_ascii=False, sort_keys=True, separators=(',', ':'))
            handle.write('\n')
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_path, path)
    finally:
        try:
            os.unlink(temp_path)
        except FileNotFoundError:
            pass


def _sha256_file(path: str) -> str:
    digest = hashlib.sha256()
    with open(path, 'rb') as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b''):
            digest.update(chunk)
    return 'sha256:' + digest.hexdigest()


def _sha256_text(value: str) -> str:
    return 'sha256:' + hashlib.sha256(value.encode('utf-8')).hexdigest()
