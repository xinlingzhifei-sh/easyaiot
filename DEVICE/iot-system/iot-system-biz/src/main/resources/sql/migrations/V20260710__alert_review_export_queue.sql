-- Persistent, idempotent evidence-export queue ownership.
ALTER TABLE system_supervision_alert_review_export_job
  ADD COLUMN IF NOT EXISTS request_key VARCHAR(128);

ALTER TABLE system_supervision_alert_review_export_job
  ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128);

ALTER TABLE system_supervision_alert_review_export_job
  ADD COLUMN IF NOT EXISTS claimed_by BIGINT;

ALTER TABLE system_supervision_alert_review_export_job
  ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP;

ALTER TABLE system_supervision_alert_review_export_job
  ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

ALTER TABLE system_supervision_alert_review_export_job
  ADD COLUMN IF NOT EXISTS last_error TEXT;

UPDATE system_supervision_alert_review_export_job
SET request_key = 'legacy:' || job_no
WHERE request_key IS NULL OR BTRIM(request_key) = '';

ALTER TABLE system_supervision_alert_review_export_job
  ALTER COLUMN request_key SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_export_request
ON system_supervision_alert_review_export_job(tenant_id, request_key)
WHERE deleted = 0 AND status <> 'expired';

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_export_claim
ON system_supervision_alert_review_export_job(tenant_id, status, next_retry_at, claimed_at, generated_at)
WHERE deleted = 0;
