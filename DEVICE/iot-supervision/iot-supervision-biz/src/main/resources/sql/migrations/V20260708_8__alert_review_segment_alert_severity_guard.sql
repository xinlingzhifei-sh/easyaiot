UPDATE system_supervision_alert_review_segment
SET severity = 'alert'
WHERE segment_status = 'alert'
  AND severity <> 'alert';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_supervision_alert_review_segment_alert_severity'
  ) THEN
    ALTER TABLE system_supervision_alert_review_segment
      ADD CONSTRAINT ck_supervision_alert_review_segment_alert_severity
      CHECK (segment_status <> 'alert' OR severity = 'alert');
  END IF;
END $$;
