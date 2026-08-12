from pathlib import Path

from sqlalchemy import text


VIDEO_SCHEMA_MIGRATIONS = (
    "device_access_state_v1.sql",
    "rtmp_ingest_auth_v1.sql",
)


def _default_sql_dir():
    return Path(__file__).resolve().parents[2] / "sql"


def _split_sql_statements(sql):
    return [statement.strip() for statement in sql.split(";") if statement.strip()]


def iter_video_schema_migrations(sql_dir=None):
    base_dir = Path(sql_dir) if sql_dir is not None else _default_sql_dir()
    for filename in VIDEO_SCHEMA_MIGRATIONS:
        path = base_dir / filename
        if not path.exists():
            raise FileNotFoundError(f"Video schema migration not found: {path}")
        yield filename, path.read_text(encoding="utf-8")


def apply_video_schema_migrations(session, sql_dir=None):
    applied = []

    for filename, sql in iter_video_schema_migrations(sql_dir):
        for statement in _split_sql_statements(sql):
            session.execute(text(statement))
        applied.append(filename)

    session.commit()
    return applied
