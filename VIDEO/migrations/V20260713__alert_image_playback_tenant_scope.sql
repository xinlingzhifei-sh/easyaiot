BEGIN;

DO $$
DECLARE
  legacy_tenant_text TEXT := current_setting('yfeieye.video_legacy_tenant_id', true);
BEGIN
  IF legacy_tenant_text IS NULL
     OR legacy_tenant_text !~ '^[1-9][0-9]*$'
     OR legacy_tenant_text::NUMERIC <> 1 THEN
    RAISE EXCEPTION
      'yfeieye.video_legacy_tenant_id must be explicitly configured as 1';
  END IF;

  IF to_regclass('alert') IS NULL
     OR to_regclass('image') IS NULL
     OR to_regclass('playback') IS NULL THEN
    RAISE EXCEPTION
      'required VIDEO alert/image/playback tables do not exist';
  END IF;
EXCEPTION
  WHEN numeric_value_out_of_range THEN
    RAISE EXCEPTION
      'yfeieye.video_legacy_tenant_id must be explicitly configured as 1';
END
$$;

ALTER TABLE alert ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE image ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE playback ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE alert
SET tenant_id = current_setting(
  'yfeieye.video_legacy_tenant_id', true)::BIGINT
WHERE tenant_id IS NULL;

UPDATE image
SET tenant_id = current_setting(
  'yfeieye.video_legacy_tenant_id', true)::BIGINT
WHERE tenant_id IS NULL;

UPDATE playback
SET tenant_id = current_setting(
  'yfeieye.video_legacy_tenant_id', true)::BIGINT
WHERE tenant_id IS NULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM alert WHERE tenant_id IS NULL)
     OR EXISTS (SELECT 1 FROM image WHERE tenant_id IS NULL)
     OR EXISTS (SELECT 1 FROM playback WHERE tenant_id IS NULL) THEN
    RAISE EXCEPTION
      'VIDEO alert/image/playback tenant backfill left unresolved rows';
  END IF;
END
$$;

ALTER TABLE alert ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE image ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE playback ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE alert DROP CONSTRAINT IF EXISTS ck_alert_tenant_positive;
ALTER TABLE image DROP CONSTRAINT IF EXISTS ck_image_tenant_positive;
ALTER TABLE playback DROP CONSTRAINT IF EXISTS ck_playback_tenant_positive;

ALTER TABLE alert
  ADD CONSTRAINT ck_alert_tenant_positive CHECK (tenant_id > 0);
ALTER TABLE image
  ADD CONSTRAINT ck_image_tenant_positive CHECK (tenant_id > 0);
ALTER TABLE playback
  ADD CONSTRAINT ck_playback_tenant_positive CHECK (tenant_id > 0);

CREATE INDEX IF NOT EXISTS ix_alert_tenant_id
  ON alert (tenant_id);
CREATE INDEX IF NOT EXISTS ix_image_tenant_id
  ON image (tenant_id);
CREATE INDEX IF NOT EXISTS ix_playback_tenant_id
  ON playback (tenant_id);
CREATE INDEX IF NOT EXISTS ix_alert_tenant_device_time
  ON alert (tenant_id, device_id, time);
CREATE INDEX IF NOT EXISTS ix_image_tenant_device_created_at
  ON image (tenant_id, device_id, created_at);
CREATE INDEX IF NOT EXISTS ix_playback_tenant_device_event_time
  ON playback (tenant_id, device_id, event_time);

COMMENT ON COLUMN alert.tenant_id IS
  'Owning tenant; legacy rows are assigned only by explicit tenant-1 migration configuration.';
COMMENT ON COLUMN image.tenant_id IS
  'Owning tenant; new camera snapshots inherit the authenticated tenant.';
COMMENT ON COLUMN playback.tenant_id IS
  'Owning tenant; new playback rows inherit authenticated or record-space tenant ownership.';

COMMIT;
