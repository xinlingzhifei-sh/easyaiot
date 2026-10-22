-- Align alert-review persistence with the platform's MyBatis-Plus contracts:
-- logical deletion is SMALLINT 0/1 and every BaseDO table is tenant scoped.
-- The migration is safe for both fresh installs (already SMALLINT) and the
-- short-lived historical/shadow schema that used PostgreSQL BOOLEAN columns.

DROP TRIGGER IF EXISTS tr_supervision_alert_review_segment_status_transition
ON system_supervision_alert_review_segment;

ALTER TABLE system_supervision_alert_review_segment
  DROP CONSTRAINT IF EXISTS ex_supervision_alert_review_segment_camera_time;

DROP INDEX IF EXISTS uk_supervision_event_open_alert;
DROP INDEX IF EXISTS idx_supervision_task_event_id;
DROP INDEX IF EXISTS uk_supervision_alert_review_item_no;
DROP INDEX IF EXISTS idx_supervision_alert_review_workbench;
DROP INDEX IF EXISTS idx_supervision_alert_review_merge;
DROP INDEX IF EXISTS idx_supervision_alert_review_event;
DROP INDEX IF EXISTS idx_supervision_alert_review_rule_suggestion;
DROP INDEX IF EXISTS uk_supervision_alert_review_ingest_identity;
DROP INDEX IF EXISTS idx_supervision_alert_review_ingest_identity_item;
DROP INDEX IF EXISTS uk_supervision_alert_review_segment_no;
DROP INDEX IF EXISTS uk_supervision_alert_review_segment_item;
DROP INDEX IF EXISTS idx_supervision_alert_review_segment_camera_time;
DROP INDEX IF EXISTS idx_supervision_alert_review_segment_status;
DROP INDEX IF EXISTS uk_supervision_alert_review_user_status;
DROP INDEX IF EXISTS idx_supervision_alert_review_user_reviewed;
DROP INDEX IF EXISTS idx_supervision_alert_review_evidence_item_time;
DROP INDEX IF EXISTS idx_supervision_alert_review_rule_enabled;
DROP INDEX IF EXISTS uk_supervision_alert_review_case_no;
DROP INDEX IF EXISTS idx_supervision_alert_review_case_time;
DROP INDEX IF EXISTS uk_supervision_alert_review_case_item;
DROP INDEX IF EXISTS idx_supervision_alert_review_case_item_case;
DROP INDEX IF EXISTS idx_supervision_alert_review_case_audit_case;
DROP INDEX IF EXISTS idx_supervision_alert_review_case_audit_item;
DROP INDEX IF EXISTS uk_supervision_alert_review_semantic_item;
DROP INDEX IF EXISTS idx_supervision_alert_review_semantic_filter;
DROP INDEX IF EXISTS uk_supervision_alert_review_export_job_no;
DROP INDEX IF EXISTS idx_supervision_alert_review_export_job_case;
DROP INDEX IF EXISTS uk_supervision_alert_review_runtime_lock;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_lock_until;
DROP INDEX IF EXISTS uk_supervision_alert_review_runtime_run;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_run_status;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_status;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_claim;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_claimed_at;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_run;
DROP INDEX IF EXISTS uk_supervision_alert_review_runtime_outbox_delivery_recipient;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_delivery_status;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_delivery_alert;
DROP INDEX IF EXISTS uk_supervision_alert_review_report_ack_key;
DROP INDEX IF EXISTS idx_supervision_alert_review_report_ack_scope;

DO $$
DECLARE
  target_table TEXT;
  deleted_type TEXT;
BEGIN
  FOR target_table IN
    SELECT table_name
    FROM (VALUES
      ('system_supervision_event'),
      ('system_supervision_task'),
      ('system_supervision_alert_review_item'),
      ('system_supervision_alert_review_ingest_identity'),
      ('system_supervision_alert_review_segment'),
      ('system_supervision_alert_review_user_status'),
      ('system_supervision_alert_review_evidence'),
      ('system_supervision_alert_review_rule'),
      ('system_supervision_alert_review_case'),
      ('system_supervision_alert_review_case_item'),
      ('system_supervision_alert_review_case_audit'),
      ('system_supervision_alert_review_semantic_index'),
      ('system_supervision_alert_review_export_job'),
      ('system_supervision_alert_review_runtime_lock'),
      ('system_supervision_alert_review_runtime_run'),
      ('system_supervision_alert_review_runtime_outbox'),
      ('system_supervision_alert_review_runtime_outbox_delivery'),
      ('system_supervision_alert_review_report_ack')
    ) AS base_do_tables(table_name)
  LOOP
    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS tenant_id BIGINT', target_table);
    EXECUTE format('UPDATE %I SET tenant_id = 0 WHERE tenant_id IS NULL', target_table);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET DEFAULT 0', target_table);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN tenant_id SET NOT NULL', target_table);

    EXECUTE format('ALTER TABLE %I ADD COLUMN IF NOT EXISTS deleted SMALLINT', target_table);
    SELECT column_info.data_type
      INTO deleted_type
      FROM information_schema.columns AS column_info
     WHERE column_info.table_schema = current_schema()
       AND column_info.table_name = target_table
       AND column_info.column_name = 'deleted';

    IF deleted_type = 'boolean' THEN
      EXECUTE format('ALTER TABLE %I ALTER COLUMN deleted DROP DEFAULT', target_table);
      EXECUTE format(
        'ALTER TABLE %I ALTER COLUMN deleted TYPE SMALLINT USING (CASE WHEN deleted THEN 1 ELSE 0 END)::SMALLINT',
        target_table
      );
    END IF;

    EXECUTE format('UPDATE %I SET deleted = 0 WHERE deleted IS NULL', target_table);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN deleted SET DEFAULT 0', target_table);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN deleted SET NOT NULL', target_table);
  END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_event_open_alert
ON system_supervision_event(tenant_id, source_system, source_alert_id)
WHERE deleted = 0 AND event_status <> 'closed' AND source_alert_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_supervision_task_event_id
ON system_supervision_task(tenant_id, event_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_item_no
ON system_supervision_alert_review_item(tenant_id, review_item_no)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_workbench
ON system_supervision_alert_review_item(tenant_id, review_status, camera_id, last_alert_time);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_merge
ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, review_status, last_alert_time);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_event
ON system_supervision_alert_review_item(tenant_id, event_id);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_rule_suggestion
ON system_supervision_alert_review_item(tenant_id, rule_suggestion_status, camera_id, zone_code, object_label);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_ingest_identity
ON system_supervision_alert_review_ingest_identity(tenant_id, source_system, identity_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_ingest_identity_item
ON system_supervision_alert_review_ingest_identity(tenant_id, review_item_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_segment_no
ON system_supervision_alert_review_segment(tenant_id, segment_no)
WHERE deleted = 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_segment_item
ON system_supervision_alert_review_segment(tenant_id, review_item_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_segment_camera_time
ON system_supervision_alert_review_segment(tenant_id, camera_id, start_time, end_time);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_segment_status
ON system_supervision_alert_review_segment(tenant_id, segment_status, severity, start_time);

ALTER TABLE system_supervision_alert_review_segment
  ADD CONSTRAINT ex_supervision_alert_review_segment_camera_time
  EXCLUDE USING gist (
    tenant_id WITH =,
    camera_id WITH =,
    tsrange(start_time, COALESCE(end_time, 'infinity'::timestamp), '[)') WITH &&
  ) WHERE (deleted = 0);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_user_status
ON system_supervision_alert_review_user_status(tenant_id, review_item_id, user_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_user_reviewed
ON system_supervision_alert_review_user_status(tenant_id, user_id, has_been_reviewed, review_item_id);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_evidence_item_time
ON system_supervision_alert_review_evidence(tenant_id, review_item_id, happened_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_rule_enabled
ON system_supervision_alert_review_rule(tenant_id, enabled, source_system, camera_id, zone_code);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_case_no
ON system_supervision_alert_review_case(tenant_id, case_no)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_time
ON system_supervision_alert_review_case(tenant_id, status, start_time, end_time);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_case_item
ON system_supervision_alert_review_case_item(tenant_id, review_case_id, review_item_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_item_case
ON system_supervision_alert_review_case_item(tenant_id, review_case_id, sort_order);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_audit_case
ON system_supervision_alert_review_case_audit(tenant_id, review_case_id, happened_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_audit_item
ON system_supervision_alert_review_case_audit(tenant_id, review_item_id, happened_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_semantic_item
ON system_supervision_alert_review_semantic_index(tenant_id, review_item_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_semantic_filter
ON system_supervision_alert_review_semantic_index(tenant_id, index_status, camera_id, first_alert_time, last_alert_time);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_export_job_no
ON system_supervision_alert_review_export_job(tenant_id, job_no)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_export_job_case
ON system_supervision_alert_review_export_job(tenant_id, review_case_id, status, expires_at);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_lock
ON system_supervision_alert_review_runtime_lock(tenant_id, lock_name)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_lock_until
ON system_supervision_alert_review_runtime_lock(tenant_id, locked_until);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_run
ON system_supervision_alert_review_runtime_run(tenant_id, run_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_run_status
ON system_supervision_alert_review_runtime_run(tenant_id, status, executed_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_status
ON system_supervision_alert_review_runtime_outbox(tenant_id, outbox_status, created_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_claim
ON system_supervision_alert_review_runtime_outbox(tenant_id, outbox_status, claim_token)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_claimed_at
ON system_supervision_alert_review_runtime_outbox(tenant_id, outbox_status, claimed_at)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_run
ON system_supervision_alert_review_runtime_outbox(tenant_id, run_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_outbox_delivery_recipient
ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, outbox_id, channel, recipient_user_id, template_code)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_delivery_status
ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, delivery_status, last_attempt_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_delivery_alert
ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, event_type, alert_key);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_report_ack_key
ON system_supervision_alert_review_report_ack(tenant_id, report_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_report_ack_scope
ON system_supervision_alert_review_report_ack(tenant_id, report_type, period_start, period_end);

CREATE OR REPLACE FUNCTION fn_supervision_alert_review_segment_status_transition()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
  old_rank INTEGER;
  new_rank INTEGER;
BEGIN
  IF OLD.segment_status IS NOT DISTINCT FROM NEW.segment_status THEN
    RETURN NEW;
  END IF;

  old_rank := CASE OLD.segment_status
    WHEN 'active' THEN 1
    WHEN 'detection' THEN 2
    WHEN 'alert' THEN 3
    WHEN 'ended' THEN 4
    ELSE NULL
  END;
  new_rank := CASE NEW.segment_status
    WHEN 'active' THEN 1
    WHEN 'detection' THEN 2
    WHEN 'alert' THEN 3
    WHEN 'ended' THEN 4
    ELSE NULL
  END;

  IF old_rank IS NULL OR new_rank IS NULL THEN
    RETURN NEW;
  END IF;
  IF new_rank < old_rank THEN
    RAISE EXCEPTION 'invalid review segment status transition: % -> %', OLD.segment_status, NEW.segment_status
      USING ERRCODE = 'check_violation';
  END IF;

  RETURN NEW;
END $$;

CREATE TRIGGER tr_supervision_alert_review_segment_status_transition
BEFORE UPDATE OF segment_status
ON system_supervision_alert_review_segment
FOR EACH ROW
WHEN (OLD.deleted = 0 AND NEW.deleted = 0)
EXECUTE FUNCTION fn_supervision_alert_review_segment_status_transition();
