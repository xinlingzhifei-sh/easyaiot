ALTER TABLE system_supervision_alert_review_case_audit
  ALTER COLUMN review_case_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_audit_item
ON system_supervision_alert_review_case_audit(tenant_id, review_item_id, happened_at);
