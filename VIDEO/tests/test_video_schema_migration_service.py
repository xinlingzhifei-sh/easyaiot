import unittest
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))


def _import_apply_video_schema_migrations():
    sys.modules.pop("app.services.video_schema_migration_service", None)
    sqlalchemy_module = sys.modules.get("sqlalchemy")
    if sqlalchemy_module is not None and not hasattr(sqlalchemy_module, "text"):
        sys.modules.pop("sqlalchemy", None)

    from app.services.video_schema_migration_service import apply_video_schema_migrations

    return apply_video_schema_migrations


class FakeSession:
    def __init__(self):
        self.executed = []
        self.committed = False

    def execute(self, statement):
        self.executed.append(str(statement).strip())

    def commit(self):
        self.committed = True


class VideoSchemaMigrationServiceTest(unittest.TestCase):

    def test_applies_registered_sql_files_in_order(self):
        apply_video_schema_migrations = _import_apply_video_schema_migrations()

        session = FakeSession()

        applied = apply_video_schema_migrations(session)

        self.assertEqual(
            ["device_access_state_v1.sql", "rtmp_ingest_auth_v1.sql"],
            applied,
        )
        self.assertTrue(session.committed)
        self.assertGreater(len(session.executed), 2)
        self.assertIn(
            "CREATE TABLE IF NOT EXISTS device_access_state_event",
            session.executed[0],
        )
        self.assertIn(
            "CREATE INDEX IF NOT EXISTS idx_device_rtmp_publish_audit_reason_time",
            session.executed[-1],
        )

    def test_app_startup_invokes_schema_migration_chain(self):
        run_py = (ROOT / "run.py").read_text(encoding="utf-8")

        self.assertIn(
            "from app.services.video_schema_migration_service import apply_video_schema_migrations",
            run_py,
        )
        self.assertIn("apply_video_schema_migrations(db.session)", run_py)


if __name__ == "__main__":
    unittest.main()
