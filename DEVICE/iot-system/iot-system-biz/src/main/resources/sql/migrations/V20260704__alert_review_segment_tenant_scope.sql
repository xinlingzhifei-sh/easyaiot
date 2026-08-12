CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE system_supervision_alert_review_segment
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE system_supervision_alert_review_segment segment
SET tenant_id = COALESCE(item.tenant_id, 0)
FROM system_supervision_alert_review_item item
WHERE segment.review_item_id = item.id
  AND segment.tenant_id IS DISTINCT FROM COALESCE(item.tenant_id, 0);

UPDATE system_supervision_alert_review_segment
SET tenant_id = 0
WHERE tenant_id IS NULL;

ALTER TABLE system_supervision_alert_review_segment
  ALTER COLUMN tenant_id SET DEFAULT 0;

ALTER TABLE system_supervision_alert_review_segment
  ALTER COLUMN tenant_id SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_supervision_alert_review_segment_status'
  ) THEN
    ALTER TABLE system_supervision_alert_review_segment
      ADD CONSTRAINT ck_supervision_alert_review_segment_status
      CHECK (segment_status IN ('active', 'detection', 'alert', 'ended'));
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_supervision_alert_review_segment_severity'
  ) THEN
    ALTER TABLE system_supervision_alert_review_segment
      ADD CONSTRAINT ck_supervision_alert_review_segment_severity
      CHECK (severity IN ('detection', 'alert'));
  END IF;
END $$;

DROP INDEX IF EXISTS idx_supervision_alert_review_segment_camera_time;
CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_segment_camera_time
ON system_supervision_alert_review_segment(tenant_id, camera_id, start_time, end_time);

ALTER TABLE system_supervision_alert_review_segment
  DROP CONSTRAINT IF EXISTS ex_supervision_alert_review_segment_camera_time;

ALTER TABLE system_supervision_alert_review_segment
  ADD CONSTRAINT ex_supervision_alert_review_segment_camera_time
  EXCLUDE USING gist (
    tenant_id WITH =,
    camera_id WITH =,
    tsrange(start_time, COALESCE(end_time, 'infinity'::timestamp), '[)') WITH &&
  )
  WHERE (deleted = 0);
