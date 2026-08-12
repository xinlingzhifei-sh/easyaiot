BEGIN;

DO $$
BEGIN
  IF to_regclass('device_detection_region') IS NULL THEN
    RAISE EXCEPTION 'required table device_detection_region does not exist';
  END IF;
END
$$;

ALTER TABLE device_detection_region
  ADD COLUMN IF NOT EXISTS inertia_frames INTEGER,
  ADD COLUMN IF NOT EXISTS loitering_seconds INTEGER;

UPDATE device_detection_region
SET
  inertia_frames = COALESCE(inertia_frames, 1),
  loitering_seconds = COALESCE(loitering_seconds, 5)
WHERE inertia_frames IS NULL OR loitering_seconds IS NULL;

ALTER TABLE device_detection_region
  ALTER COLUMN inertia_frames SET DEFAULT 1,
  ALTER COLUMN inertia_frames SET NOT NULL,
  ALTER COLUMN loitering_seconds SET DEFAULT 5,
  ALTER COLUMN loitering_seconds SET NOT NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'device_detection_region'::regclass
      AND conname = 'ck_device_detection_region_inertia_frames'
  ) THEN
    ALTER TABLE device_detection_region
      ADD CONSTRAINT ck_device_detection_region_inertia_frames
      CHECK (inertia_frames BETWEEN 0 AND 10000);
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'device_detection_region'::regclass
      AND conname = 'ck_device_detection_region_loitering_seconds'
  ) THEN
    ALTER TABLE device_detection_region
      ADD CONSTRAINT ck_device_detection_region_loitering_seconds
      CHECK (loitering_seconds BETWEEN 0 AND 86400);
  END IF;
END
$$;

COMMIT;
