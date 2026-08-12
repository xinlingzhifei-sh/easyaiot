"""Durable unresolved DVR cache-to-storage failure events for drift patrols."""
import base64
import hashlib
import json
import os
import posixpath
import uuid
from datetime import datetime, timedelta, timezone


_EVENT_DIR_ENV = 'YFEIEYE_RECORD_CACHE_EVENT_DIR'
_EVENT_RETENTION_HOURS_ENV = 'YFEIEYE_RECORD_CACHE_EVENT_RETENTION_HOURS'
_EVENT_MAX_FILES_ENV = 'YFEIEYE_RECORD_CACHE_EVENT_MAX_FILES'
_DEFAULT_EVENT_DIR = '/data/yfeieye-record-exports/record-cache-events'
_DEFAULT_RETENTION_HOURS = 24 * 7
_DEFAULT_MAX_FILES = 10000


def record_cache_flush_failure(event: dict, error) -> dict:
    event = dict(event or {})
    tenant_id = _tenant_id(event)
    identity = _event_identity(event)
    payload = {
        'tenant_id': tenant_id,
        'event_id': _text(event.get('event_id') or event.get('eventId')) or None,
        'device_id': _text(event.get('device_id') or event.get('deviceId')) or None,
        'space_id': event.get('space_id') or event.get('spaceId'),
        'cache_path': _cache_path(event) or None,
        'source': _text(event.get('source')) or 'dvr',
        'error': _text(error) or 'dvr_cache_flush_failed',
        'happened_at': datetime.now(timezone.utc).isoformat(),
        'status': 'failed',
        'identity': identity,
    }
    _write_json(_event_path(identity), payload)
    _prune_event_files()
    return payload


def resolve_record_cache_flush_failure(event: dict) -> bool:
    path = _event_path(_event_identity(event or {}))
    try:
        os.remove(path)
        _fsync_parent(path)
        return True
    except FileNotFoundError:
        return False


def list_record_cache_flush_failures(
        space_id=None,
        device_id=None,
        tenant_id=None,
        limit=1000,
        cursor=None,
        retention_hours=None,
        now=None,
        return_page=False):
    root = _event_dir()
    if not os.path.isdir(root):
        empty_page = {'items': [], 'next_cursor': None, 'has_more': False, 'limit': _limit(limit)}
        return empty_page if return_page else []
    normalized_tenant = str(_tenant_id({'tenant_id': tenant_id}))
    normalized_space = _text(space_id)
    normalized_device = _text(device_id)
    now = _utc_datetime(now) or datetime.now(timezone.utc)
    retention = _retention_hours(retention_hours)
    cutoff = now - timedelta(hours=retention) if retention else None
    cursor_value = _decode_cursor(cursor)
    events = []
    for entry in os.scandir(root):
        if not entry.is_file(follow_symlinks=False) or not entry.name.endswith('.json'):
            continue
        path = entry.path
        try:
            with open(path, 'r', encoding='utf-8') as handle:
                event = json.load(handle)
        except (OSError, ValueError, TypeError):
            continue
        if not isinstance(event, dict) or event.get('status') != 'failed':
            continue
        if _text(event.get('tenant_id')) != normalized_tenant:
            continue
        happened_at = _utc_datetime(event.get('happened_at'))
        if cutoff and happened_at and happened_at < cutoff:
            _remove_event_file(path)
            continue
        event_space = _text(event.get('space_id'))
        if normalized_space and event_space and event_space != normalized_space:
            continue
        if normalized_space and not event_space and not normalized_device:
            continue
        if normalized_device and _text(event.get('device_id')) != normalized_device:
            continue
        key = (
            happened_at.isoformat() if happened_at else '',
            _text(event.get('identity')),
        )
        if cursor_value and key <= cursor_value:
            continue
        events.append((key, event))
    events.sort(key=lambda item: item[0])
    page_limit = _limit(limit)
    page = events[:page_limit + 1]
    has_more = len(page) > page_limit
    page = page[:page_limit]
    items = [event for _, event in page]
    next_cursor = _encode_cursor(page[-1][0]) if has_more and page else None
    if return_page:
        return {
            'items': items,
            'next_cursor': next_cursor,
            'has_more': has_more,
            'limit': page_limit,
        }
    return items


def _event_identity(event: dict) -> str:
    tenant_id = _tenant_id(event)
    device_id = _text(event.get('device_id') or event.get('deviceId'))
    cache_path = _cache_path(event)
    event_id = _text(event.get('event_id') or event.get('eventId'))
    raw = f'{tenant_id}\n{device_id}\n{cache_path or event_id}'
    if not raw.strip():
        raise ValueError('cache flush event requires device/path or event_id identity')
    return hashlib.sha256(raw.encode('utf-8')).hexdigest()


def _tenant_id(event: dict) -> int:
    value = _text((event or {}).get('tenant_id') or (event or {}).get('tenantId'))
    if not value.isdigit() or int(value) <= 0:
        raise ValueError('cache flush event tenant id must be a positive integer')
    return int(value)


def _cache_path(event: dict) -> str:
    value = _text(
        event.get('cache_path') or event.get('cachePath')
        or event.get('file_path') or event.get('filePath') or event.get('file')
    )
    if not value:
        return ''
    cwd = _text(event.get('cwd'))
    if cwd and not os.path.isabs(value):
        value = os.path.join(cwd, value)
    if value.startswith('/'):
        return posixpath.normpath(value.replace('\\', '/'))
    return os.path.normpath(value)


def _event_dir() -> str:
    return _text(os.environ.get(_EVENT_DIR_ENV)) or _DEFAULT_EVENT_DIR


def _event_path(identity: str) -> str:
    return os.path.join(_event_dir(), identity + '.json')


def _write_json(path: str, value: dict):
    os.makedirs(os.path.dirname(path), mode=0o750, exist_ok=True)
    temporary = path + '.' + uuid.uuid4().hex + '.tmp'
    try:
        with open(temporary, 'x', encoding='utf-8') as handle:
            json.dump(value, handle, ensure_ascii=False, sort_keys=True)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        _fsync_parent(path)
    finally:
        try:
            os.remove(temporary)
        except FileNotFoundError:
            pass


def _fsync_parent(path: str):
    if os.name == 'nt':
        return
    descriptor = None
    try:
        descriptor = os.open(os.path.dirname(path), os.O_RDONLY)
        os.fsync(descriptor)
    except OSError:
        pass
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _prune_event_files():
    root = _event_dir()
    if not os.path.isdir(root):
        return
    now = datetime.now(timezone.utc)
    cutoff = now - timedelta(hours=_retention_hours(None))
    retained = []
    for entry in os.scandir(root):
        if not entry.is_file(follow_symlinks=False) or not entry.name.endswith('.json'):
            continue
        happened_at = None
        try:
            with open(entry.path, 'r', encoding='utf-8') as handle:
                value = json.load(handle)
            happened_at = _utc_datetime(value.get('happened_at')) if isinstance(value, dict) else None
        except (OSError, ValueError, TypeError):
            pass
        try:
            modified_at = entry.stat(follow_symlinks=False).st_mtime
        except OSError:
            continue
        if happened_at and happened_at < cutoff:
            _remove_event_file(entry.path)
            continue
        retained.append((modified_at, entry.path))
    max_files = _positive_int(os.environ.get(_EVENT_MAX_FILES_ENV), _DEFAULT_MAX_FILES, 100000)
    for _, path in sorted(retained)[:-max_files]:
        _remove_event_file(path)


def _remove_event_file(path: str):
    try:
        os.remove(path)
    except FileNotFoundError:
        return
    except OSError:
        return


def _retention_hours(explicit) -> int:
    value = explicit if explicit is not None else os.environ.get(
        _EVENT_RETENTION_HOURS_ENV, str(_DEFAULT_RETENTION_HOURS))
    return _positive_int(value, _DEFAULT_RETENTION_HOURS, 24 * 365)


def _limit(value) -> int:
    return _positive_int(value, 1000, 1000)


def _positive_int(value, fallback: int, maximum: int) -> int:
    try:
        return max(1, min(int(value), maximum))
    except (TypeError, ValueError):
        return fallback


def _utc_datetime(value):
    if isinstance(value, datetime):
        parsed = value
    else:
        text = _text(value)
        if not text:
            return None
        try:
            parsed = datetime.fromisoformat(text.replace('Z', '+00:00'))
        except ValueError:
            return None
    if parsed.tzinfo is None:
        return parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def _encode_cursor(key) -> str:
    payload = json.dumps({
        'happenedAt': key[0],
        'identity': key[1],
    }, sort_keys=True, separators=(',', ':')).encode('utf-8')
    return base64.urlsafe_b64encode(payload).decode('ascii').rstrip('=')


def _decode_cursor(cursor):
    cursor = _text(cursor)
    if not cursor:
        return None
    try:
        padding = '=' * (-len(cursor) % 4)
        value = json.loads(base64.urlsafe_b64decode(cursor + padding).decode('utf-8'))
        happened_at = _text(value['happenedAt'])
        identity = _text(value['identity'])
    except (KeyError, TypeError, ValueError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError('invalid cache flush event cursor') from exc
    if not happened_at or not identity:
        raise ValueError('invalid cache flush event cursor')
    return happened_at, identity


def _text(value) -> str:
    return '' if value is None else str(value).strip()
