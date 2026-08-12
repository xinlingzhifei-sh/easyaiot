"""Canonical allow-root checks for local alert and recording media."""
from __future__ import annotations

import json
import os
import stat
from typing import Iterable


_ALLOWED_ROOTS_ENV = 'YFEIEYE_LOCAL_MEDIA_ROOTS'


class LocalMediaPathError(ValueError):
    def __init__(self, reason: str):
        super().__init__(reason)
        self.reason = reason


def resolve_allowed_local_media_file(path: str) -> str:
    """Return a canonical regular file path inside an explicitly allowed root."""
    raw = str(path or '').strip()
    if raw.lower().startswith('file:'):
        raise LocalMediaPathError('local_media_file_uri_not_allowed')
    if not raw or not os.path.isabs(raw):
        raise LocalMediaPathError('local_media_absolute_path_required')

    absolute_path = os.path.abspath(os.path.expandvars(os.path.expanduser(raw)))
    canonical_path = os.path.realpath(absolute_path)
    if os.path.normcase(absolute_path) != os.path.normcase(canonical_path):
        raise LocalMediaPathError('local_media_symlink_not_allowed')

    roots = allowed_local_media_roots()
    if not roots:
        raise LocalMediaPathError('local_media_allowed_roots_not_configured')
    if not any(_is_within(canonical_path, root) for root in roots):
        raise LocalMediaPathError('local_media_path_outside_allowed_roots')

    try:
        mode = os.lstat(canonical_path).st_mode
    except OSError as exc:
        raise LocalMediaPathError('local_media_file_not_found') from exc
    if stat.S_ISLNK(mode):
        raise LocalMediaPathError('local_media_symlink_not_allowed')
    if not stat.S_ISREG(mode):
        raise LocalMediaPathError('local_media_regular_file_required')
    return canonical_path


def allowed_local_media_roots() -> tuple[str, ...]:
    raw = str(os.environ.get(_ALLOWED_ROOTS_ENV) or '').strip()
    values: Iterable[object]
    if not raw:
        values = ()
    elif raw.startswith('['):
        try:
            parsed = json.loads(raw)
        except (TypeError, ValueError):
            parsed = ()
        values = parsed if isinstance(parsed, list) else ()
    else:
        values = raw.split(',')
    configured_export_store = str(
        os.environ.get('YFEIEYE_RECORD_EXPORT_STORE_DIR') or '').strip()
    if configured_export_store:
        values = tuple(values) + (configured_export_store,)
    roots = []
    for value in values:
        candidate = str(value or '').strip()
        if not candidate or not os.path.isabs(candidate):
            continue
        canonical = os.path.realpath(
            os.path.abspath(os.path.expandvars(os.path.expanduser(candidate))))
        if canonical not in roots:
            roots.append(canonical)
    return tuple(roots)


def _is_within(path: str, root: str) -> bool:
    try:
        return os.path.normcase(os.path.commonpath((path, root))) == os.path.normcase(root)
    except ValueError:
        return False
