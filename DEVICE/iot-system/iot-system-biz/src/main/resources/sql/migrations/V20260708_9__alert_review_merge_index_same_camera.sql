DROP INDEX IF EXISTS idx_supervision_alert_review_merge;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_merge
ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, review_status, last_alert_time);
