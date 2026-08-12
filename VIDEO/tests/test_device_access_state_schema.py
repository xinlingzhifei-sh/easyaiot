import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "sql" / "device_access_state_v1.sql"


class DeviceAccessStateSchemaTest(unittest.TestCase):

    def test_schema_creates_event_and_current_tables(self):
        sql = SCHEMA.read_text(encoding="utf-8")

        self.assertIn("CREATE TABLE IF NOT EXISTS device_access_state_event", sql)
        self.assertIn("CREATE TABLE IF NOT EXISTS device_access_state_current", sql)
        self.assertIn("device_id VARCHAR(100) NOT NULL", sql)
        self.assertIn("protocol VARCHAR(32) NOT NULL", sql)
        self.assertIn("state VARCHAR(32) NOT NULL", sql)
        self.assertIn("reason_code VARCHAR(100)", sql)
        self.assertIn("source_event VARCHAR(100)", sql)
        self.assertIn("PRIMARY KEY (device_id, protocol)", sql)
        self.assertIn("idx_device_access_state_event_device_time", sql)
        self.assertIn("idx_device_access_state_current_state", sql)

    def test_schema_is_additive_only(self):
        sql = SCHEMA.read_text(encoding="utf-8").lower()

        self.assertNotIn("drop table", sql)
        self.assertNotIn("truncate table", sql)
        self.assertNotIn("delete from", sql)
        self.assertNotIn("update ", sql)


if __name__ == "__main__":
    unittest.main()
