import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCHEMA = ROOT / "sql" / "rtmp_ingest_auth_v1.sql"


class RtmpIngestAuthSchemaTest(unittest.TestCase):

    def test_schema_creates_secret_and_audit_tables(self):
        sql = SCHEMA.read_text(encoding="utf-8")

        self.assertIn("CREATE TABLE IF NOT EXISTS device_rtmp_ingest_secret", sql)
        self.assertIn("CREATE TABLE IF NOT EXISTS device_rtmp_publish_audit", sql)
        self.assertIn("device_id VARCHAR(100) NOT NULL", sql)
        self.assertIn("tenant_id VARCHAR(100) NOT NULL", sql)
        self.assertIn("token_version INT NOT NULL DEFAULT 1", sql)
        self.assertIn("secret VARCHAR(128) NOT NULL", sql)
        self.assertIn("node_id INT", sql)
        self.assertIn("accepted BOOLEAN NOT NULL DEFAULT FALSE", sql)
        self.assertIn("reason_code VARCHAR(100) NOT NULL", sql)
        self.assertIn("idx_device_rtmp_publish_audit_device_time", sql)
        self.assertIn("idx_device_rtmp_publish_audit_node_time", sql)
        self.assertIn("uk_device_rtmp_ingest_secret_device_tenant", sql)

    def test_schema_is_additive_only(self):
        sql = SCHEMA.read_text(encoding="utf-8").lower()

        self.assertNotIn("drop table", sql)
        self.assertNotIn("truncate table", sql)
        self.assertNotIn("delete from", sql)
        self.assertNotIn("update ", sql)


if __name__ == "__main__":
    unittest.main()
