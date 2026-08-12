"""Apply immutable VIDEO PostgreSQL migrations before starting the service."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable, Optional

from schema_lock import acquire_schema_lock, configure_schema_timeouts


MIGRATION_FILES = (
    'migrations/V20260711__device_detection_region_rule_fields.sql',
    'migrations/V20260712__record_snapshot_tenant_scope.sql',
    'migrations/V20260713__alert_image_playback_tenant_scope.sql',
    'migrations/V20260722__nvr_rtsp_fields.sql',
)
HISTORY_TABLE = 'yfeieye_video_schema_history'
_MIGRATION_NAME = re.compile(r'^(V[0-9][A-Za-z0-9_]*)__([A-Za-z0-9_-]+)\.sql$')
_OUTER_TRANSACTION = re.compile(
    r'^\s*BEGIN\s*;\s*(?P<body>.*)\s*COMMIT\s*;\s*$',
    re.IGNORECASE | re.DOTALL,
)


@dataclass(frozen=True)
class Migration:
    version: str
    script_path: str
    checksum: str
    sql: str
    body_sql: str


def strip_outer_transaction(sql: str) -> str:
    match = _OUTER_TRANSACTION.match(str(sql or ''))
    if not match:
        raise ValueError('production migration must have one outer BEGIN/COMMIT transaction')
    body = match.group('body').strip()
    if not body:
        raise ValueError('production migration transaction body must not be empty')
    return body


def resolve_legacy_tenant_id(value=None) -> int:
    """Return the one production tenant allowed to own pre-tenant VIDEO rows."""
    raw_value = value
    if raw_value is None:
        raw_value = os.environ.get('YFEIEYE_VIDEO_LEGACY_TENANT_ID')
    text_value = str(raw_value or '').strip()
    if not re.fullmatch(r'[1-9][0-9]*', text_value):
        raise ValueError(
            'VIDEO legacy tenant must be explicitly configured as tenant 1')
    tenant_id = int(text_value)
    if tenant_id != 1:
        raise ValueError(
            'VIDEO legacy tenant must be explicitly configured as tenant 1')
    return tenant_id


def configure_legacy_tenant_guc(connection, legacy_tenant_id) -> None:
    """Bind the validated legacy owner to the exact migration connection."""
    tenant_id = resolve_legacy_tenant_id(legacy_tenant_id)
    cursor = connection.cursor()
    try:
        cursor.execute(
            "SELECT set_config('yfeieye.video_legacy_tenant_id', %s, false)",
            (str(tenant_id),),
        )
    finally:
        cursor.close()


def build_migration_plan(repo_root: Path | str,
                         migration_files: Optional[Iterable[str]] = None) -> list[Migration]:
    root = Path(repo_root).resolve()
    versions: set[str] = set()
    plan: list[Migration] = []
    for relative_path in migration_files or MIGRATION_FILES:
        normalized_path = str(relative_path).replace('\\', '/')
        path = (root / normalized_path).resolve()
        try:
            path.relative_to(root)
        except ValueError as exception:
            raise ValueError(f'migration path escapes repository root: {relative_path}') from exception
        match = _MIGRATION_NAME.match(path.name)
        if not match:
            raise ValueError(f'invalid production migration filename: {path.name}')
        version = match.group(1)
        if version in versions:
            raise ValueError(f'duplicate migration version: {version}')
        versions.add(version)
        sql = path.read_text(encoding='utf-8')
        plan.append(Migration(
            version=path.name,
            script_path=normalized_path,
            checksum=hashlib.sha256(sql.encode('utf-8')).hexdigest(),
            sql=sql,
            body_sql=strip_outer_transaction(sql),
        ))
    return plan


def apply_migrations(database_url: str,
                     plan: list[Migration],
                     verify_only: bool = False,
                     acquire_lock: bool = True,
                     legacy_tenant_id=None,
                     connect: Optional[Callable] = None) -> list[dict]:
    if not str(database_url or '').strip():
        raise ValueError('DATABASE_URL is required for VIDEO production migrations')
    legacy_tenant_id = resolve_legacy_tenant_id(legacy_tenant_id)
    if connect is None:
        import psycopg2
        connect = psycopg2.connect
    connection = connect(database_url)
    results: list[dict] = []
    cursor = None
    lock_context = None
    lock_entered = False
    try:
        connection.autocommit = True
        configure_schema_timeouts(connection)
        configure_legacy_tenant_guc(connection, legacy_tenant_id)
        if acquire_lock:
            lock_context = acquire_schema_lock(connection)
            lock_context.__enter__()
            lock_entered = True
        cursor = connection.cursor()
        if verify_only:
            cursor.execute('SELECT to_regclass(%s)', (HISTORY_TABLE,))
            if cursor.fetchone()[0] is None:
                raise RuntimeError(f'migration history missing: {HISTORY_TABLE}')
        else:
            cursor.execute(f'''
                CREATE TABLE IF NOT EXISTS {HISTORY_TABLE} (
                  installed_rank BIGSERIAL PRIMARY KEY,
                  version VARCHAR(255) NOT NULL UNIQUE,
                  checksum CHAR(64) NOT NULL,
                  script_path TEXT NOT NULL,
                  installed_by VARCHAR(128) NOT NULL DEFAULT CURRENT_USER,
                  installed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            ''')

        for migration in plan:
            cursor.execute(
                f'SELECT checksum FROM {HISTORY_TABLE} WHERE version = %s',
                (migration.version,),
            )
            row = cursor.fetchone()
            if row is not None:
                if row[0] != migration.checksum:
                    raise RuntimeError(
                        f'checksum mismatch for migration {migration.version}: '
                        f'expected {migration.checksum}, history has {row[0]}'
                    )
                results.append({'version': migration.version, 'status': 'verified' if verify_only else 'already_applied'})
                continue
            if verify_only:
                raise RuntimeError(f'migration history missing: {migration.version}')

            connection.autocommit = False
            try:
                cursor.execute(migration.body_sql)
                cursor.execute(
                    f'''INSERT INTO {HISTORY_TABLE}(version, checksum, script_path)
                        VALUES (%s, %s, %s)''',
                    (migration.version, migration.checksum, migration.script_path),
                )
                connection.commit()
            except Exception:
                connection.rollback()
                raise
            finally:
                connection.autocommit = True
            results.append({'version': migration.version, 'status': 'applied'})
        return results
    finally:
        try:
            if not connection.autocommit:
                connection.rollback()
                connection.autocommit = True
            if cursor is not None:
                cursor.close()
            if lock_entered:
                lock_context.__exit__(*sys.exc_info())
        finally:
            connection.close()


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description='Apply immutable VIDEO PostgreSQL migrations')
    parser.add_argument('--database-url', default=os.environ.get('DATABASE_URL', ''))
    parser.add_argument('--repo-root', default=str(Path(__file__).resolve().parent))
    parser.add_argument('--verify-only', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    return parser.parse_args(argv)


def main(argv=None) -> int:
    options = parse_args(argv)
    plan = build_migration_plan(options.repo_root)
    if options.dry_run:
        results = [{
            'version': migration.version,
            'scriptPath': migration.script_path,
            'checksum': migration.checksum,
            'status': 'planned',
        } for migration in plan]
    else:
        results = apply_migrations(
            options.database_url,
            plan,
            verify_only=options.verify_only,
        )
    print(json.dumps({
        'mode': 'verify' if options.verify_only else ('dry_run' if options.dry_run else 'apply'),
        'migrationCount': len(plan),
        'results': results,
    }, ensure_ascii=False))
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
