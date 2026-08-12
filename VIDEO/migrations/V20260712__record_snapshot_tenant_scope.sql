BEGIN;

DO $$
DECLARE
  legacy_tenant_text TEXT := current_setting(
    'yfeieye.video_legacy_tenant_id', true);
BEGIN
  IF legacy_tenant_text IS NULL
     OR legacy_tenant_text !~ '^[1-9][0-9]*$'
     OR legacy_tenant_text::NUMERIC <> 1 THEN
    RAISE EXCEPTION
      'yfeieye.video_legacy_tenant_id must be explicitly configured as 1';
  END IF;

  IF to_regclass('record_space') IS NULL
     OR to_regclass('record_file') IS NULL
     OR to_regclass('snap_space') IS NULL
     OR to_regclass('snap_image') IS NULL THEN
    RAISE EXCEPTION
      'required VIDEO record/snapshot tables do not exist';
  END IF;
EXCEPTION
  WHEN numeric_value_out_of_range THEN
    RAISE EXCEPTION
      'yfeieye.video_legacy_tenant_id must be explicitly configured as 1';
END
$$;

ALTER TABLE record_space ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE record_file ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE snap_space ADD COLUMN IF NOT EXISTS tenant_id BIGINT;
ALTER TABLE snap_image ADD COLUMN IF NOT EXISTS tenant_id BIGINT;

UPDATE record_space
SET tenant_id = current_setting('yfeieye.video_legacy_tenant_id', true)::BIGINT
WHERE tenant_id IS NULL;

UPDATE snap_space
SET tenant_id = current_setting('yfeieye.video_legacy_tenant_id', true)::BIGINT
WHERE tenant_id IS NULL;

UPDATE record_file AS file
SET tenant_id = space.tenant_id
FROM record_space AS space
WHERE file.space_id = space.id
  AND file.tenant_id IS NULL;

UPDATE snap_image AS image
SET tenant_id = space.tenant_id
FROM snap_space AS space
WHERE image.space_id = space.id
  AND image.tenant_id IS NULL;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM record_space WHERE tenant_id IS NULL)
     OR EXISTS (SELECT 1 FROM record_file WHERE tenant_id IS NULL)
     OR EXISTS (SELECT 1 FROM snap_space WHERE tenant_id IS NULL)
     OR EXISTS (SELECT 1 FROM snap_image WHERE tenant_id IS NULL) THEN
    RAISE EXCEPTION 'VIDEO tenant backfill left unresolved rows';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM record_file AS file
    LEFT JOIN record_space AS space
      ON space.id = file.space_id
     AND space.tenant_id = file.tenant_id
    WHERE space.id IS NULL
  ) THEN
    RAISE EXCEPTION 'record_file tenant does not match record_space owner';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM snap_image AS image
    LEFT JOIN snap_space AS space
      ON space.id = image.space_id
     AND space.tenant_id = image.tenant_id
    WHERE space.id IS NULL
  ) THEN
    RAISE EXCEPTION 'snap_image tenant does not match snap_space owner';
  END IF;
END
$$;

ALTER TABLE record_space ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE record_file ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE snap_space ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE snap_image ALTER COLUMN tenant_id SET NOT NULL;

DO $$
DECLARE
  constraint_row RECORD;
BEGIN
  FOR constraint_row IN
    SELECT
      table_class.relname AS table_name,
      constraint_def.conname AS constraint_name,
      ARRAY_AGG(attribute.attname ORDER BY key_column.ordinality) AS columns
    FROM pg_constraint AS constraint_def
    JOIN pg_class AS table_class
      ON table_class.oid = constraint_def.conrelid
    JOIN LATERAL UNNEST(constraint_def.conkey)
      WITH ORDINALITY AS key_column(attnum, ordinality) ON TRUE
    JOIN pg_attribute AS attribute
      ON attribute.attrelid = constraint_def.conrelid
     AND attribute.attnum = key_column.attnum
    WHERE table_class.relname IN (
      'record_space', 'record_file', 'snap_space', 'snap_image')
      AND constraint_def.contype = 'u'
    GROUP BY table_class.relname, constraint_def.conname
  LOOP
    IF (constraint_row.table_name IN ('record_space', 'snap_space')
        AND constraint_row.columns IN (
          ARRAY['space_code']::NAME[], ARRAY['device_id']::NAME[]))
       OR (constraint_row.table_name IN ('record_file', 'snap_image')
           AND constraint_row.columns =
             ARRAY['bucket_name', 'object_name']::NAME[]) THEN
      EXECUTE FORMAT(
        'ALTER TABLE %I DROP CONSTRAINT %I',
        constraint_row.table_name,
        constraint_row.constraint_name);
    END IF;
  END LOOP;
END
$$;

DO $$
DECLARE
  constraint_row RECORD;
BEGIN
  FOR constraint_row IN
    SELECT
      table_class.relname AS table_name,
      constraint_def.conname AS constraint_name,
      referenced_class.relname AS referenced_table,
      ARRAY_AGG(attribute.attname ORDER BY key_column.ordinality) AS columns
    FROM pg_constraint AS constraint_def
    JOIN pg_class AS table_class
      ON table_class.oid = constraint_def.conrelid
    JOIN pg_class AS referenced_class
      ON referenced_class.oid = constraint_def.confrelid
    JOIN LATERAL UNNEST(constraint_def.conkey)
      WITH ORDINALITY AS key_column(attnum, ordinality) ON TRUE
    JOIN pg_attribute AS attribute
      ON attribute.attrelid = constraint_def.conrelid
     AND attribute.attnum = key_column.attnum
    WHERE table_class.relname IN ('record_file', 'snap_image')
      AND constraint_def.contype = 'f'
    GROUP BY
      table_class.relname,
      constraint_def.conname,
      referenced_class.relname
  LOOP
    IF constraint_row.columns IN (
         ARRAY['space_id']::NAME[],
         ARRAY['tenant_id', 'space_id']::NAME[])
       AND ((constraint_row.table_name = 'record_file'
             AND constraint_row.referenced_table = 'record_space')
            OR (constraint_row.table_name = 'snap_image'
                AND constraint_row.referenced_table = 'snap_space')) THEN
      EXECUTE FORMAT(
        'ALTER TABLE %I DROP CONSTRAINT %I',
        constraint_row.table_name,
        constraint_row.constraint_name);
    END IF;
  END LOOP;
END
$$;

-- Rebuild every named tenant constraint so a legacy/manual constraint with the
-- expected name but the wrong definition cannot silently bypass this migration.
-- All parent foreign keys were removed above before their supporting composite
-- unique constraints are replaced.
ALTER TABLE record_space
  DROP CONSTRAINT IF EXISTS ck_record_space_tenant_positive;
ALTER TABLE record_file
  DROP CONSTRAINT IF EXISTS ck_record_file_tenant_positive;
ALTER TABLE snap_space
  DROP CONSTRAINT IF EXISTS ck_snap_space_tenant_positive;
ALTER TABLE snap_image
  DROP CONSTRAINT IF EXISTS ck_snap_image_tenant_positive;
ALTER TABLE record_file
  DROP CONSTRAINT IF EXISTS ck_record_file_object_tenant_scope;
ALTER TABLE snap_image
  DROP CONSTRAINT IF EXISTS ck_snap_image_object_tenant_scope;

ALTER TABLE record_file
  DROP CONSTRAINT IF EXISTS uq_record_file_tenant_bucket_object;
ALTER TABLE snap_image
  DROP CONSTRAINT IF EXISTS uq_snap_image_tenant_bucket_object;
ALTER TABLE record_space
  DROP CONSTRAINT IF EXISTS uq_record_space_tenant_space_code;
ALTER TABLE record_space
  DROP CONSTRAINT IF EXISTS uq_record_space_tenant_device;
ALTER TABLE record_space
  DROP CONSTRAINT IF EXISTS uq_record_space_tenant_id;
ALTER TABLE snap_space
  DROP CONSTRAINT IF EXISTS uq_snap_space_tenant_space_code;
ALTER TABLE snap_space
  DROP CONSTRAINT IF EXISTS uq_snap_space_tenant_device;
ALTER TABLE snap_space
  DROP CONSTRAINT IF EXISTS uq_snap_space_tenant_id;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_space'::REGCLASS
      AND conname = 'ck_record_space_tenant_positive'
  ) THEN
    ALTER TABLE record_space
      ADD CONSTRAINT ck_record_space_tenant_positive
      CHECK (tenant_id > 0);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_file'::REGCLASS
      AND conname = 'ck_record_file_tenant_positive'
  ) THEN
    ALTER TABLE record_file
      ADD CONSTRAINT ck_record_file_tenant_positive
      CHECK (tenant_id > 0);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_space'::REGCLASS
      AND conname = 'ck_snap_space_tenant_positive'
  ) THEN
    ALTER TABLE snap_space
      ADD CONSTRAINT ck_snap_space_tenant_positive
      CHECK (tenant_id > 0);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_image'::REGCLASS
      AND conname = 'ck_snap_image_tenant_positive'
  ) THEN
    ALTER TABLE snap_image
      ADD CONSTRAINT ck_snap_image_tenant_positive
      CHECK (tenant_id > 0);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_space'::REGCLASS
      AND conname = 'uq_record_space_tenant_space_code'
  ) THEN
    ALTER TABLE record_space
      ADD CONSTRAINT uq_record_space_tenant_space_code
      UNIQUE (tenant_id, space_code);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_space'::REGCLASS
      AND conname = 'uq_record_space_tenant_device'
  ) THEN
    ALTER TABLE record_space
      ADD CONSTRAINT uq_record_space_tenant_device
      UNIQUE (tenant_id, device_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_space'::REGCLASS
      AND conname = 'uq_record_space_tenant_id'
  ) THEN
    ALTER TABLE record_space
      ADD CONSTRAINT uq_record_space_tenant_id
      UNIQUE (tenant_id, id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_space'::REGCLASS
      AND conname = 'uq_snap_space_tenant_space_code'
  ) THEN
    ALTER TABLE snap_space
      ADD CONSTRAINT uq_snap_space_tenant_space_code
      UNIQUE (tenant_id, space_code);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_space'::REGCLASS
      AND conname = 'uq_snap_space_tenant_device'
  ) THEN
    ALTER TABLE snap_space
      ADD CONSTRAINT uq_snap_space_tenant_device
      UNIQUE (tenant_id, device_id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_space'::REGCLASS
      AND conname = 'uq_snap_space_tenant_id'
  ) THEN
    ALTER TABLE snap_space
      ADD CONSTRAINT uq_snap_space_tenant_id
      UNIQUE (tenant_id, id);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_file'::REGCLASS
      AND conname = 'uq_record_file_tenant_bucket_object'
  ) THEN
    ALTER TABLE record_file
      ADD CONSTRAINT uq_record_file_tenant_bucket_object
      UNIQUE (tenant_id, bucket_name, object_name);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_image'::REGCLASS
      AND conname = 'uq_snap_image_tenant_bucket_object'
  ) THEN
    ALTER TABLE snap_image
      ADD CONSTRAINT uq_snap_image_tenant_bucket_object
      UNIQUE (tenant_id, bucket_name, object_name);
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_file'::REGCLASS
      AND conname = 'fk_record_file_tenant_space'
  ) THEN
    ALTER TABLE record_file
      ADD CONSTRAINT fk_record_file_tenant_space
      FOREIGN KEY (tenant_id, space_id)
      REFERENCES record_space (tenant_id, id)
      ON DELETE CASCADE;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_image'::REGCLASS
      AND conname = 'fk_snap_image_tenant_space'
  ) THEN
    ALTER TABLE snap_image
      ADD CONSTRAINT fk_snap_image_tenant_space
      FOREIGN KEY (tenant_id, space_id)
      REFERENCES snap_space (tenant_id, id)
      ON DELETE CASCADE;
  END IF;
END
$$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'record_file'::REGCLASS
      AND conname = 'ck_record_file_object_tenant_scope'
  ) THEN
    ALTER TABLE record_file
      ADD CONSTRAINT ck_record_file_object_tenant_scope CHECK (
        object_name LIKE 'tenants/' || tenant_id || '/%'
        OR (tenant_id = 1 AND object_name NOT LIKE 'tenants/%')
      );
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
    WHERE conrelid = 'snap_image'::REGCLASS
      AND conname = 'ck_snap_image_object_tenant_scope'
  ) THEN
    ALTER TABLE snap_image
      ADD CONSTRAINT ck_snap_image_object_tenant_scope CHECK (
        object_name LIKE 'tenants/' || tenant_id || '/%'
        OR (tenant_id = 1 AND object_name NOT LIKE 'tenants/%')
      );
  END IF;
END
$$;

CREATE INDEX IF NOT EXISTS ix_record_space_tenant_id
  ON record_space (tenant_id);
CREATE INDEX IF NOT EXISTS ix_snap_space_tenant_id
  ON snap_space (tenant_id);
CREATE INDEX IF NOT EXISTS ix_record_file_tenant_id
  ON record_file (tenant_id);
CREATE INDEX IF NOT EXISTS ix_snap_image_tenant_id
  ON snap_image (tenant_id);
CREATE INDEX IF NOT EXISTS ix_record_file_tenant_space_event_time
  ON record_file (tenant_id, space_id, event_time);
CREATE INDEX IF NOT EXISTS ix_snap_image_tenant_space_captured_at
  ON snap_image (tenant_id, space_id, captured_at);

COMMENT ON COLUMN record_space.tenant_id IS
  'Owning tenant; production values are positive platform tenant IDs.';
COMMENT ON COLUMN snap_space.tenant_id IS
  'Owning tenant; production values are positive platform tenant IDs.';
COMMENT ON COLUMN record_file.tenant_id IS
  'Inherited from record_space and protected by a composite foreign key.';
COMMENT ON COLUMN snap_image.tenant_id IS
  'Inherited from snap_space and protected by a composite foreign key.';
COMMENT ON COLUMN record_file.object_name IS
  'New writes require tenants/{tenantId}/...; tenant-1 legacy object keys remain unchanged and are read-only compatible.';
COMMENT ON COLUMN snap_image.object_name IS
  'New writes require tenants/{tenantId}/...; tenant-1 legacy object keys remain unchanged and are read-only compatible.';

COMMIT;
