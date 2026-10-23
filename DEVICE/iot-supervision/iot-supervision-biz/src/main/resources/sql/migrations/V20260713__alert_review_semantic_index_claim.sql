ALTER TABLE system_supervision_alert_review_semantic_index
  ADD COLUMN IF NOT EXISTS index_generation_id VARCHAR(128),
  ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128),
  ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMP,
  ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP;

UPDATE system_supervision_alert_review_semantic_index
SET index_generation_id = 'legacy-' || tenant_id::text
WHERE index_generation_id IS NULL
  AND deleted = 0;

CREATE INDEX IF NOT EXISTS idx_alert_review_semantic_claim
ON system_supervision_alert_review_semantic_index(
  tenant_id, index_status, next_retry_at, claim_expires_at
)
WHERE deleted = 0;
