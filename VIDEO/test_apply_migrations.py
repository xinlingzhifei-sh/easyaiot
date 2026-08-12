import tempfile
import unittest
import sys
import types
from contextlib import contextmanager
from pathlib import Path
from unittest import mock


class VideoProductionMigrationRunnerTest(unittest.TestCase):

    def test_plan_tracks_region_and_tenant_migrations_with_checksums(self):
        from apply_migrations import build_migration_plan

        plan = build_migration_plan(Path(__file__).resolve().parent)

        self.assertEqual(4, len(plan))
        self.assertEqual(
            'V20260711__device_detection_region_rule_fields.sql',
            plan[0].version,
        )
        self.assertRegex(plan[0].checksum, r'^[a-f0-9]{64}$')
        self.assertIn('ADD COLUMN IF NOT EXISTS inertia_frames', plan[0].sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS loitering_seconds', plan[0].sql)
        self.assertEqual(
            'V20260712__record_snapshot_tenant_scope.sql',
            plan[1].version,
        )
        self.assertRegex(plan[1].checksum, r'^[a-f0-9]{64}$')
        self.assertIn('fk_record_file_tenant_space', plan[1].sql)
        self.assertEqual(
            'V20260713__alert_image_playback_tenant_scope.sql',
            plan[2].version,
        )
        self.assertRegex(plan[2].checksum, r'^[a-f0-9]{64}$')
        self.assertIn('ck_playback_tenant_positive', plan[2].sql)
        self.assertEqual(
            'V20260722__nvr_rtsp_fields.sql',
            plan[3].version,
        )
        self.assertRegex(plan[3].checksum, r'^[a-f0-9]{64}$')
        self.assertIn('ADD COLUMN IF NOT EXISTS rtsp_template TEXT', plan[3].sql)
        self.assertIn('ADD COLUMN IF NOT EXISTS rtsp_port SMALLINT', plan[3].sql)

    def test_runner_strips_only_outer_transaction_for_atomic_history_insert(self):
        from apply_migrations import strip_outer_transaction

        body = strip_outer_transaction('BEGIN;\nSELECT 1;\nCOMMIT;\n')

        self.assertEqual('SELECT 1;', body)
        with self.assertRaisesRegex(ValueError, 'outer BEGIN/COMMIT'):
            strip_outer_transaction('SELECT 1;')

    def test_duplicate_migration_versions_are_rejected(self):
        from apply_migrations import build_migration_plan

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            migrations = root / 'migrations'
            migrations.mkdir()
            source = (
                Path(__file__).resolve().parent
                / 'migrations'
                / 'V20260711__device_detection_region_rule_fields.sql'
            ).read_text(encoding='utf-8')
            (migrations / 'V20260711__first.sql').write_text(source, encoding='utf-8')
            (migrations / 'V20260711__second.sql').write_text(source, encoding='utf-8')

            with self.assertRaisesRegex(ValueError, 'duplicate migration version'):
                build_migration_plan(root, [
                    'migrations/V20260711__first.sql',
                    'migrations/V20260711__second.sql',
                ])

    def test_compose_runs_migrations_before_application(self):
        compose = (Path(__file__).resolve().parent / 'docker-compose.yaml').read_text(encoding='utf-8')

        self.assertIn('python /app/prepare_database.py', compose)
        self.assertIn('python /app/apply_migrations.py --verify-only', compose)
        self.assertIn('exec python /app/run.py', compose)
        self.assertLess(
            compose.index('python /app/prepare_database.py'),
            compose.index('python /app/apply_migrations.py --verify-only'),
        )
        self.assertLess(
            compose.index('python /app/apply_migrations.py --verify-only'),
            compose.index('exec python /app/run.py'),
        )

    def test_schema_lock_times_out_instead_of_waiting_forever(self):
        from schema_lock import acquire_schema_lock

        class Cursor:
            statements = []

            def execute(self, statement, params=None):
                self.statements.append((statement, params))

            def fetchone(self):
                return (False,)

            def close(self):
                pass

        class Connection:
            def __init__(self):
                self.cursor_instance = Cursor()

            def cursor(self):
                return self.cursor_instance

        connection = Connection()

        with self.assertRaisesRegex(TimeoutError, 'schema migration lock'):
            with acquire_schema_lock(connection, wait_seconds=0.001, poll_seconds=0.001):
                self.fail('lock must not be yielded')

        sql = '\n'.join(statement for statement, _ in connection.cursor_instance.statements)
        self.assertIn('SET statement_timeout', sql)
        self.assertIn('SET lock_timeout', sql)
        self.assertIn('pg_try_advisory_lock', sql)

    def test_prepare_database_holds_one_lock_across_baseline_and_migrations(self):
        import prepare_database

        events = []

        @contextmanager
        def fake_lock(*_args, **_kwargs):
            events.append('lock_acquired')
            try:
                yield object()
            finally:
                events.append('lock_released')

        with mock.patch.object(prepare_database, 'production_schema_lock', fake_lock), \
                mock.patch.object(
                    prepare_database, 'bootstrap_schema',
                    side_effect=lambda *_args, **_kwargs: events.append('baseline')) as bootstrap, \
                mock.patch.object(
                    prepare_database, 'apply_migrations',
                    side_effect=lambda *_args, **_kwargs: events.append('migrations')) as apply, \
                mock.patch.object(prepare_database, 'build_migration_plan', return_value=['migration']):
            prepare_database.prepare_database('postgresql://example/video')

        self.assertEqual(
            ['lock_acquired', 'baseline', 'migrations', 'lock_released'],
            events,
        )
        bootstrap.assert_called_once_with('postgresql://example/video')
        apply.assert_called_once_with(
            'postgresql://example/video',
            ['migration'],
            acquire_lock=False,
        )

    def test_baseline_ddl_connections_receive_statement_and_lock_timeouts(self):
        import bootstrap_schema

        captured = {}

        class FakeDb:
            session = types.SimpleNamespace(remove=lambda: None)

            def init_app(self, app):
                captured.update(app.config['SQLALCHEMY_ENGINE_OPTIONS']['connect_args'])

            @staticmethod
            def create_all():
                captured['created'] = True

        models = types.ModuleType('models')
        models.db = FakeDb()
        with mock.patch.dict(sys.modules, {'models': models}):
            bootstrap_schema.bootstrap_schema('postgresql://example/video')

        self.assertTrue(captured['created'])
        self.assertEqual(10, captured['connect_timeout'])
        self.assertIn('statement_timeout=', captured['options'])
        self.assertIn('lock_timeout=', captured['options'])


if __name__ == '__main__':
    unittest.main()
