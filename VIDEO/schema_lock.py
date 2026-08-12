"""Bounded PostgreSQL advisory lock for VIDEO schema preparation."""
from __future__ import annotations

import os
import time
from contextlib import contextmanager


ADVISORY_LOCK_NAME = 'yfeieye:video:production-migrations:v1'
_WAIT_SECONDS_ENV = 'YFEIEYE_VIDEO_SCHEMA_LOCK_WAIT_SECONDS'
_STATEMENT_TIMEOUT_MS_ENV = 'YFEIEYE_VIDEO_SCHEMA_STATEMENT_TIMEOUT_MS'
_LOCK_TIMEOUT_MS_ENV = 'YFEIEYE_VIDEO_SCHEMA_DB_LOCK_TIMEOUT_MS'


def _positive_number(value, fallback, minimum, maximum):
    try:
        return max(minimum, min(float(value), maximum))
    except (TypeError, ValueError):
        return fallback


def schema_timeout_values() -> tuple[int, int]:
    statement_timeout_ms = int(_positive_number(
        os.environ.get(_STATEMENT_TIMEOUT_MS_ENV, '300000'), 300000, 1000, 1800000))
    lock_timeout_ms = int(_positive_number(
        os.environ.get(_LOCK_TIMEOUT_MS_ENV, '10000'), 10000, 100, 300000))
    return statement_timeout_ms, lock_timeout_ms


def configure_schema_timeouts(connection) -> None:
    statement_timeout_ms, lock_timeout_ms = schema_timeout_values()
    cursor = connection.cursor()
    try:
        cursor.execute(f'SET statement_timeout = {statement_timeout_ms}')
        cursor.execute(f'SET lock_timeout = {lock_timeout_ms}')
    finally:
        cursor.close()


def sqlalchemy_schema_connect_options() -> str:
    statement_timeout_ms, lock_timeout_ms = schema_timeout_values()
    return (
        f'-c statement_timeout={statement_timeout_ms} '
        f'-c lock_timeout={lock_timeout_ms}'
    )


@contextmanager
def acquire_schema_lock(connection, wait_seconds=None, poll_seconds=0.25):
    wait_seconds = _positive_number(
        wait_seconds if wait_seconds is not None else os.environ.get(_WAIT_SECONDS_ENV, '60'),
        60,
        0.001,
        600,
    )
    poll_seconds = _positive_number(poll_seconds, 0.25, 0.001, 5)
    configure_schema_timeouts(connection)
    cursor = connection.cursor()
    acquired = False
    try:
        deadline = time.monotonic() + wait_seconds
        while True:
            cursor.execute(
                'SELECT pg_try_advisory_lock(hashtextextended(%s, 0))',
                (ADVISORY_LOCK_NAME,),
            )
            acquired = bool(cursor.fetchone()[0])
            if acquired:
                break
            if time.monotonic() >= deadline:
                raise TimeoutError(
                    f'VIDEO schema migration lock was not acquired within {wait_seconds:g}s')
            time.sleep(min(poll_seconds, max(0, deadline - time.monotonic())))
        yield connection
    finally:
        if acquired:
            cursor.execute(
                'SELECT pg_advisory_unlock(hashtextextended(%s, 0))',
                (ADVISORY_LOCK_NAME,),
            )
        cursor.close()


@contextmanager
def production_schema_lock(database_url: str, connect=None, wait_seconds=None):
    if not str(database_url or '').strip():
        raise ValueError('DATABASE_URL is required for VIDEO schema preparation')
    if connect is None:
        import psycopg2
        connect = psycopg2.connect
    connection = connect(database_url)
    try:
        connection.autocommit = True
        with acquire_schema_lock(connection, wait_seconds=wait_seconds):
            yield connection
    finally:
        connection.close()
