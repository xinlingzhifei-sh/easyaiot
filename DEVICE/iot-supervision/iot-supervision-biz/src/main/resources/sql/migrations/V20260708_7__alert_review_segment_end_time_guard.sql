UPDATE system_supervision_alert_review_segment
SET end_time = start_time
WHERE segment_status = 'ended'
  AND end_time IS NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_supervision_alert_review_segment_ended_time'
  ) THEN
    ALTER TABLE system_supervision_alert_review_segment
      ADD CONSTRAINT ck_supervision_alert_review_segment_ended_time
      CHECK (segment_status <> 'ended' OR end_time IS NOT NULL);
  END IF;
END $$;
