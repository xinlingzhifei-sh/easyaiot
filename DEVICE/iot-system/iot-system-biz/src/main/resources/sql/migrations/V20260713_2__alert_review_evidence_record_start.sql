ALTER TABLE system_supervision_alert_review_evidence
  ADD COLUMN IF NOT EXISTS record_start_time TIMESTAMP;

COMMENT ON COLUMN system_supervision_alert_review_evidence.record_start_time
  IS 'Start time of the referenced recording file; null when VIDEO cannot provide it';
