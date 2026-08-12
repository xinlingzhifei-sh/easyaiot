CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE system_supervision_alert_review_item
  ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

ALTER TABLE system_supervision_alert_review_case_audit
  ADD COLUMN IF NOT EXISTS metadata TEXT;

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

DROP INDEX IF EXISTS idx_supervision_alert_review_workbench;
CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_workbench
ON system_supervision_alert_review_item(tenant_id, review_status, camera_id, last_alert_time);

DROP INDEX IF EXISTS idx_supervision_alert_review_merge;
CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_merge
ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, review_status, last_alert_time);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_segment_item
ON system_supervision_alert_review_segment(tenant_id, review_item_id)
WHERE deleted = 0;

DROP INDEX IF EXISTS idx_supervision_alert_review_segment_camera_time;
CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_segment_camera_time
ON system_supervision_alert_review_segment(tenant_id, camera_id, start_time, end_time);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_ingest_identity (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_item_id BIGINT NOT NULL,
  source_system VARCHAR(64) NOT NULL,
  identity_key VARCHAR(256) NOT NULL,
  source_alert_id VARCHAR(128),
  source_payload_hash VARCHAR(128),
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_ingest_identity
ON system_supervision_alert_review_ingest_identity(tenant_id, source_system, identity_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_ingest_identity_item
ON system_supervision_alert_review_ingest_identity(tenant_id, review_item_id);

INSERT INTO system_supervision_alert_review_ingest_identity (
  tenant_id,
  review_item_id,
  source_system,
  identity_key,
  source_alert_id
)
SELECT DISTINCT
  COALESCE(item.tenant_id, 0),
  item.id,
  item.source_system,
  item.source_system || ':alert:' || trim(source_alert_id),
  trim(source_alert_id)
FROM system_supervision_alert_review_item item
CROSS JOIN LATERAL regexp_split_to_table(item.source_alert_ids, E'\n') AS source_alert_id
WHERE item.deleted = 0
  AND item.source_alert_ids IS NOT NULL
  AND trim(source_alert_id) <> ''
ON CONFLICT DO NOTHING;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_supervision_alert_review_segment_time'
  ) THEN
    ALTER TABLE system_supervision_alert_review_segment
      ADD CONSTRAINT ck_supervision_alert_review_segment_time
      CHECK (end_time IS NULL OR end_time >= start_time);
  END IF;
END $$;

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
