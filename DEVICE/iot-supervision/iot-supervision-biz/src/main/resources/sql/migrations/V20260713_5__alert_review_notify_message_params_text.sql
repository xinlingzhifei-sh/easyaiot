-- Runtime patrol and operations-report notifications retain structured audit parameters.
-- The platform bootstrap used VARCHAR(255), which is too small for the complete JSON map.
ALTER TABLE system_notify_message
  ALTER COLUMN template_params TYPE TEXT
  USING template_params::text;
