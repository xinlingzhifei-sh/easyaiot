"""Real PostgreSQL gate for VIDEO record/snapshot tenant migration."""
from __future__ import annotations

import dataclasses
import os
import unittest
import uuid
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

import psycopg2
from psycopg2 import sql

from apply_migrations import apply_migrations, build_migration_plan
from prepare_database import prepare_database


ADMIN_DATABASE_URL = os.environ.get('YFEIEYE_VIDEO_TEST_DATABASE_ADMIN_URL', '')
VIDEO_DIR = Path(__file__).resolve().parent


def _database_url(admin_url: str, database_name: str) -> str:
    parsed = urlsplit(admin_url)
    return urlunsplit((
        parsed.scheme,
        parsed.netloc,
        f'/{database_name}',
        parsed.query,
        parsed.fragment,
    ))


@unittest.skipUnless(
    ADMIN_DATABASE_URL,
    'YFEIEYE_VIDEO_TEST_DATABASE_ADMIN_URL is required for PostgreSQL gate',
)
class VideoTenantMigrationPostgresGateTest(unittest.TestCase):

    def setUp(self):
        self.database_names = []
        self.admin = psycopg2.connect(ADMIN_DATABASE_URL)
        self.admin.autocommit = True

    def tearDown(self):
        for database_name in self.database_names:
            with self.admin.cursor() as cursor:
                cursor.execute(
                    'SELECT pg_terminate_backend(pid) FROM pg_stat_activity '
                    'WHERE datname = %s AND pid <> pg_backend_pid()',
                    (database_name,),
                )
                cursor.execute(sql.SQL('DROP DATABASE IF EXISTS {}').format(
                    sql.Identifier(database_name)))
        self.admin.close()

    def _create_database(self, suffix: str) -> tuple[str, str]:
        database_name = f'yfeieye_video_tenant_{suffix}_{uuid.uuid4().hex[:8]}'
        with self.admin.cursor() as cursor:
            cursor.execute(sql.SQL('CREATE DATABASE {}').format(
                sql.Identifier(database_name)))
        self.database_names.append(database_name)
        return database_name, _database_url(ADMIN_DATABASE_URL, database_name)

    def test_empty_legacy_repeat_and_checksum_gates(self):
        plan = build_migration_plan(VIDEO_DIR)

        _, empty_url = self._create_database('empty')
        prepared = prepare_database(empty_url, VIDEO_DIR)
        self.assertEqual(
            ['applied', 'applied', 'applied'],
            [row['status'] for row in prepared],
        )
        repeated = apply_migrations(empty_url, plan)
        self.assertEqual(
            ['already_applied', 'already_applied', 'already_applied'],
            [row['status'] for row in repeated],
        )
        with psycopg2.connect(empty_url) as connection, connection.cursor() as cursor:
            cursor.execute("""
                SELECT table_name, is_nullable, data_type
                FROM information_schema.columns
                WHERE table_name IN (
                  'record_space', 'record_file', 'snap_space', 'snap_image',
                  'alert', 'image', 'playback')
                  AND column_name = 'tenant_id'
                ORDER BY table_name
            """)
            self.assertEqual(7, len(cursor.fetchall()))
            cursor.execute("""
                SELECT conname, pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname IN (
                  'fk_record_file_tenant_space',
                  'fk_snap_image_tenant_space',
                  'uq_record_file_tenant_bucket_object',
                  'uq_snap_image_tenant_bucket_object',
                  'ck_record_space_tenant_positive',
                  'ck_record_file_tenant_positive',
                  'ck_snap_space_tenant_positive',
                  'ck_snap_image_tenant_positive',
                  'ck_alert_tenant_positive',
                  'ck_image_tenant_positive',
                  'ck_playback_tenant_positive')
                ORDER BY conname
            """)
            definitions = dict(cursor.fetchall())
            self.assertEqual(11, len(definitions))
            for name in (
                    'ck_record_space_tenant_positive',
                    'ck_record_file_tenant_positive',
                    'ck_snap_space_tenant_positive',
                    'ck_snap_image_tenant_positive',
                    'ck_alert_tenant_positive',
                    'ck_image_tenant_positive',
                    'ck_playback_tenant_positive'):
                self.assertIn('tenant_id > 0', definitions[name])

            cursor.execute(
                "SELECT set_config('yfeieye.video_legacy_tenant_id', '1', false)")
            for version in (
                    'V20260712__record_snapshot_tenant_scope.sql',
                    'V20260713__alert_image_playback_tenant_scope.sql'):
                tenant_migration = next(
                    migration for migration in plan
                    if migration.version == version)
                cursor.execute(tenant_migration.body_sql)
                cursor.execute(tenant_migration.body_sql)

        bad_plan = [dataclasses.replace(plan[0], checksum='0' * 64), *plan[1:]]
        with self.assertRaisesRegex(RuntimeError, 'checksum mismatch'):
            apply_migrations(empty_url, bad_plan)

        _, legacy_url = self._create_database('legacy')
        with psycopg2.connect(legacy_url) as connection, connection.cursor() as cursor:
            cursor.execute("""
                CREATE TABLE device_detection_region (
                  id SERIAL PRIMARY KEY
                );
                CREATE TABLE record_space (
                  id SERIAL PRIMARY KEY,
                  space_code VARCHAR(255) NOT NULL UNIQUE,
                  device_id VARCHAR(100) UNIQUE,
                  CONSTRAINT ck_record_space_tenant_positive CHECK (id > 0)
                );
                CREATE TABLE snap_space (
                  id SERIAL PRIMARY KEY,
                  space_code VARCHAR(255) NOT NULL UNIQUE,
                  device_id VARCHAR(100) UNIQUE,
                  CONSTRAINT ck_snap_space_tenant_positive CHECK (id > 0)
                );
                CREATE TABLE record_file (
                  id SERIAL PRIMARY KEY,
                  space_id INTEGER NOT NULL REFERENCES record_space(id)
                    ON DELETE CASCADE,
                  device_id VARCHAR(100) NOT NULL,
                  bucket_name VARCHAR(255) NOT NULL,
                  object_name VARCHAR(500) NOT NULL,
                  event_time TIMESTAMP NOT NULL,
                  CONSTRAINT uq_record_file_bucket_object
                    UNIQUE (bucket_name, object_name),
                  CONSTRAINT uq_record_file_tenant_bucket_object
                    UNIQUE (device_id)
                );
                CREATE TABLE snap_image (
                  id SERIAL PRIMARY KEY,
                  space_id INTEGER NOT NULL REFERENCES snap_space(id)
                    ON DELETE CASCADE,
                  device_id VARCHAR(100) NOT NULL,
                  bucket_name VARCHAR(255) NOT NULL,
                  object_name VARCHAR(500) NOT NULL,
                  captured_at TIMESTAMP NOT NULL,
                  CONSTRAINT uq_snap_image_bucket_object
                    UNIQUE (bucket_name, object_name),
                  CONSTRAINT uq_snap_image_tenant_bucket_object
                    UNIQUE (device_id)
                );
                CREATE TABLE alert (
                  id SERIAL PRIMARY KEY,
                  device_id VARCHAR(100) NOT NULL,
                  time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE image (
                  id SERIAL PRIMARY KEY,
                  device_id VARCHAR(100) NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                CREATE TABLE playback (
                  id SERIAL PRIMARY KEY,
                  device_id VARCHAR(100) NOT NULL,
                  event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                );
                INSERT INTO record_space(id, space_code, device_id)
                VALUES (1, 'legacy-record', 'camera-01');
                INSERT INTO snap_space(id, space_code, device_id)
                VALUES (1, 'legacy-snap', 'camera-01');
                INSERT INTO record_file(
                  id, space_id, device_id, bucket_name, object_name, event_time)
                VALUES (
                  1, 1, 'camera-01', 'record-space',
                  'camera-01/2026/07/11/clip.flv', CURRENT_TIMESTAMP);
                INSERT INTO snap_image(
                  id, space_id, device_id, bucket_name, object_name, captured_at)
                VALUES (
                  1, 1, 'camera-01', 'snap-space',
                  'camera-01/2026/07/11/frame.jpg', CURRENT_TIMESTAMP);
                INSERT INTO alert(id, device_id) VALUES (1, 'camera-01');
                INSERT INTO image(id, device_id) VALUES (1, 'camera-01');
                INSERT INTO playback(id, device_id) VALUES (1, 'camera-01');
            """)
            connection.commit()

        applied = apply_migrations(legacy_url, plan)
        self.assertEqual(
            ['applied', 'applied', 'applied'],
            [row['status'] for row in applied],
        )
        repeated = apply_migrations(legacy_url, plan)
        self.assertEqual(
            ['already_applied', 'already_applied', 'already_applied'],
            [row['status'] for row in repeated],
        )
        with psycopg2.connect(legacy_url) as connection, connection.cursor() as cursor:
            cursor.execute("""
                SELECT tenant_id, object_name FROM record_file WHERE id = 1
            """)
            self.assertEqual(
                (1, 'camera-01/2026/07/11/clip.flv'), cursor.fetchone())
            cursor.execute("""
                SELECT tenant_id, object_name FROM snap_image WHERE id = 1
            """)
            self.assertEqual(
                (1, 'camera-01/2026/07/11/frame.jpg'), cursor.fetchone())
            for table_name in ('alert', 'image', 'playback'):
                cursor.execute(sql.SQL(
                    'SELECT tenant_id FROM {} WHERE id = 1').format(
                        sql.Identifier(table_name)))
                self.assertEqual((1,), cursor.fetchone())

            cursor.execute("""
                SELECT conname, pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conname IN (
                  'uq_record_file_tenant_bucket_object',
                  'uq_snap_image_tenant_bucket_object',
                  'ck_record_space_tenant_positive',
                  'ck_snap_space_tenant_positive',
                  'ck_alert_tenant_positive',
                  'ck_image_tenant_positive',
                  'ck_playback_tenant_positive')
            """)
            definitions = dict(cursor.fetchall())
            self.assertIn(
                'UNIQUE (tenant_id, bucket_name, object_name)',
                definitions['uq_record_file_tenant_bucket_object'])
            self.assertIn(
                'UNIQUE (tenant_id, bucket_name, object_name)',
                definitions['uq_snap_image_tenant_bucket_object'])
            self.assertIn(
                'tenant_id > 0', definitions['ck_record_space_tenant_positive'])
            self.assertIn(
                'tenant_id > 0', definitions['ck_snap_space_tenant_positive'])
            for constraint_name in (
                    'ck_alert_tenant_positive',
                    'ck_image_tenant_positive',
                    'ck_playback_tenant_positive'):
                self.assertIn('tenant_id > 0', definitions[constraint_name])

            cursor.execute("""
                INSERT INTO record_space(
                  id, tenant_id, space_code, device_id)
                VALUES (2, 2, 'legacy-record', 'camera-01')
                RETURNING id
            """)
            tenant_two_space_id = cursor.fetchone()[0]
            cursor.execute("""
                INSERT INTO record_file(
                  id, tenant_id, space_id, device_id, bucket_name,
                  object_name, event_time)
                VALUES (
                  2, 2, %s, 'camera-01', 'record-space',
                  'tenants/2/camera-01/clip.flv', CURRENT_TIMESTAMP)
            """, (tenant_two_space_id,))
            connection.commit()

            with self.assertRaises(psycopg2.Error):
                cursor.execute("""
                    INSERT INTO record_file(
                      id, tenant_id, space_id, device_id, bucket_name,
                      object_name, event_time)
                    VALUES (
                      3, 2, %s, 'camera-01', 'record-space',
                      'camera-01/legacy-write.flv', CURRENT_TIMESTAMP)
                """, (tenant_two_space_id,))
            connection.rollback()

            for table_name in (
                    'record_space', 'record_file', 'snap_space', 'snap_image',
                    'alert', 'image', 'playback'):
                with self.subTest(table=table_name), self.assertRaises(psycopg2.Error):
                    cursor.execute(sql.SQL(
                        'INSERT INTO {} (tenant_id) VALUES (0)').format(
                            sql.Identifier(table_name)))
                connection.rollback()


if __name__ == '__main__':
    unittest.main()
