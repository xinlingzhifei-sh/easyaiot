CREATE TABLE IF NOT EXISTS system_supervision_event (
  id BIGSERIAL PRIMARY KEY,
  event_no VARCHAR(64) NOT NULL,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  org_id BIGINT,
  site_type VARCHAR(32) NOT NULL DEFAULT 'prison',
  source_system VARCHAR(64) NOT NULL,
  source_alert_id VARCHAR(128),
  source_alert_type VARCHAR(128),
  source_alert_time TIMESTAMP,
  source_payload_hash VARCHAR(128),
  device_id VARCHAR(128),
  camera_id VARCHAR(128),
  location_id VARCHAR(128),
  person_id VARCHAR(128),
  person_confidence NUMERIC(8,4),
  event_type VARCHAR(64) NOT NULL,
  event_level VARCHAR(8) NOT NULL,
  event_status VARCHAR(64) NOT NULL,
  current_owner_dept_id BIGINT,
  current_owner_user_id BIGINT,
  close_result VARCHAR(64),
  close_reason TEXT,
  close_check_status VARCHAR(64) NOT NULL DEFAULT 'not_checked',
  evidence_status VARCHAR(64) NOT NULL DEFAULT 'missing_soft',
  sensitivity_level VARCHAR(64) NOT NULL DEFAULT 'normal',
  upgraded_from_level VARCHAR(8),
  upgrade_reason TEXT,
  merged_into_event_id BIGINT,
  dispatched_at TIMESTAMP,
  accepted_at TIMESTAMP,
  handled_at TIMESTAMP,
  rechecked_at TIMESTAMP,
  closed_at TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_event_open_alert
ON system_supervision_event(tenant_id, source_system, source_alert_id)
WHERE deleted = 0 AND event_status <> 'closed' AND source_alert_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS system_supervision_task (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  event_id BIGINT NOT NULL,
  task_no VARCHAR(64) NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  task_status VARCHAR(64) NOT NULL,
  assigned_dept_id BIGINT,
  assigned_role VARCHAR(64) NOT NULL,
  assigned_user_id BIGINT,
  due_at TIMESTAMP,
  accepted_at TIMESTAMP,
  arrived_at TIMESTAMP,
  submitted_at TIMESTAMP,
  result_category VARCHAR(64),
  handling_note TEXT,
  rework_count INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_supervision_task_event_id
ON system_supervision_task(tenant_id, event_id);

CREATE TABLE IF NOT EXISTS system_supervision_action (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  task_id BIGINT,
  action_type VARCHAR(64) NOT NULL,
  channel VARCHAR(64),
  action_status VARCHAR(64) NOT NULL,
  receiver_user_id BIGINT,
  result_payload TEXT,
  failure_reason TEXT,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_supervision_action_event_id
ON system_supervision_action(event_id);

CREATE TABLE IF NOT EXISTS system_supervision_evidence_item (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  material_type VARCHAR(64) NOT NULL,
  material_uri VARCHAR(512),
  related_record_id VARCHAR(128),
  is_required BOOLEAN NOT NULL DEFAULT FALSE,
  required_for_level VARCHAR(8),
  collect_status VARCHAR(64) NOT NULL,
  missing_reason TEXT,
  sensitivity_level VARCHAR(64) NOT NULL DEFAULT 'normal',
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_supervision_evidence_event_id
ON system_supervision_evidence_item(event_id);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_item (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_item_no VARCHAR(64) NOT NULL,
  source_system VARCHAR(64) NOT NULL,
  rule_code VARCHAR(128) NOT NULL,
  source_alert_type VARCHAR(128),
  device_id VARCHAR(128),
  camera_id VARCHAR(128),
  zone_code VARCHAR(128),
  object_label VARCHAR(128),
  first_alert_time TIMESTAMP NOT NULL,
  last_alert_time TIMESTAMP NOT NULL,
  alert_count INTEGER NOT NULL DEFAULT 1,
  source_alert_ids TEXT NOT NULL,
  review_data TEXT,
  review_status VARCHAR(64) NOT NULL DEFAULT 'pending_review',
  reviewer_user_id BIGINT,
  reviewed_at TIMESTAMP,
  ignore_reason TEXT,
  rule_suggestion TEXT,
  rule_suggestion_status VARCHAR(64),
  rule_suggestion_updated_at TIMESTAMP,
  event_id BIGINT,
  converted_at TIMESTAMP,
  record_evidence_status VARCHAR(64) NOT NULL DEFAULT 'missing',
  record_evidence_checked_at TIMESTAMP,
  record_evidence_message VARCHAR(256),
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

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

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_segment (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_item_id BIGINT NOT NULL,
  segment_no VARCHAR(128) NOT NULL,
  camera_id VARCHAR(128) NOT NULL,
  severity VARCHAR(64) NOT NULL,
  segment_status VARCHAR(64) NOT NULL DEFAULT 'active',
  start_time TIMESTAMP NOT NULL,
  end_time TIMESTAMP,
  object_ids TEXT,
  zone_codes TEXT,
  source_alert_ids TEXT,
  segment_events TEXT,
  segment_metadata TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_supervision_alert_review_segment_time CHECK (end_time IS NULL OR end_time >= start_time),
  CONSTRAINT ck_supervision_alert_review_segment_ended_time CHECK (segment_status <> 'ended' OR end_time IS NOT NULL),
  CONSTRAINT ck_supervision_alert_review_segment_status CHECK (segment_status IN ('active', 'detection', 'alert', 'ended')),
  CONSTRAINT ck_supervision_alert_review_segment_severity CHECK (severity IN ('detection', 'alert')),
  CONSTRAINT ck_supervision_alert_review_segment_alert_severity CHECK (segment_status <> 'alert' OR severity = 'alert'),
  CONSTRAINT ex_supervision_alert_review_segment_camera_time EXCLUDE USING gist (
    tenant_id WITH =,
    camera_id WITH =,
    tsrange(start_time, COALESCE(end_time, 'infinity'::timestamp), '[)') WITH &&
  ) WHERE (deleted = 0)
);

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

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_user_status (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_item_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  has_been_reviewed BOOLEAN NOT NULL DEFAULT FALSE,
  reviewed_at TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_user_status
ON system_supervision_alert_review_user_status(tenant_id, review_item_id, user_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_user_reviewed
ON system_supervision_alert_review_user_status(tenant_id, user_id, has_been_reviewed, review_item_id);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_evidence (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_item_id BIGINT NOT NULL,
  source_alert_id VARCHAR(128) NOT NULL,
  material_type VARCHAR(64) NOT NULL,
  material_uri VARCHAR(512),
  happened_at TIMESTAMP NOT NULL,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_evidence_item_time
ON system_supervision_alert_review_evidence(tenant_id, review_item_id, happened_at);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_rule (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  rule_code VARCHAR(128) NOT NULL,
  rule_name VARCHAR(128) NOT NULL,
  source_system VARCHAR(64),
  camera_id VARCHAR(128),
  zone_code VARCHAR(128),
  object_label VARCHAR(128),
  min_stay_seconds INTEGER,
  inertia_frames INTEGER,
  loitering_seconds INTEGER,
  active_start TIMESTAMP,
  active_end TIMESTAMP,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_rule_enabled
ON system_supervision_alert_review_rule(tenant_id, enabled, source_system, camera_id, zone_code);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_case (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  case_no VARCHAR(64) NOT NULL,
  title VARCHAR(128) NOT NULL,
  status VARCHAR(64) NOT NULL DEFAULT 'open',
  primary_review_item_id BIGINT,
  owner_user_id BIGINT,
  notes TEXT,
  camera_ids TEXT,
  start_time TIMESTAMP,
  end_time TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_case_no
ON system_supervision_alert_review_case(tenant_id, case_no)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_time
ON system_supervision_alert_review_case(tenant_id, status, start_time, end_time);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_case_item (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_case_id BIGINT NOT NULL,
  review_item_id BIGINT NOT NULL,
  sort_order INTEGER NOT NULL DEFAULT 0,
  added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_case_item
ON system_supervision_alert_review_case_item(tenant_id, review_case_id, review_item_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_item_case
ON system_supervision_alert_review_case_item(tenant_id, review_case_id, sort_order);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_case_audit (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_case_id BIGINT,
  review_item_id BIGINT,
  action_type VARCHAR(64) NOT NULL,
  action_note TEXT,
  metadata TEXT,
  operator_user_id BIGINT,
  happened_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_audit_case
ON system_supervision_alert_review_case_audit(tenant_id, review_case_id, happened_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_audit_item
ON system_supervision_alert_review_case_audit(tenant_id, review_item_id, happened_at);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_semantic_index (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  review_item_id BIGINT NOT NULL,
  camera_id VARCHAR(128),
  first_alert_time TIMESTAMP,
  last_alert_time TIMESTAMP,
  index_status VARCHAR(64) NOT NULL DEFAULT 'pending',
  document TEXT NOT NULL,
  embedding_key VARCHAR(128),
  embedding_model VARCHAR(128),
  embedding_vector_hash VARCHAR(128),
  retry_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  indexed_at TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_semantic_item
ON system_supervision_alert_review_semantic_index(tenant_id, review_item_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_semantic_filter
ON system_supervision_alert_review_semantic_index(tenant_id, index_status, camera_id, first_alert_time, last_alert_time);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_export_job (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  job_no VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL DEFAULT 'pending',
  package_no VARCHAR(64) NOT NULL,
  review_case_id BIGINT NOT NULL,
  review_item_ids TEXT NOT NULL,
  evidence_uris TEXT,
  manifest TEXT,
  file_hash VARCHAR(128) NOT NULL,
  expires_at TIMESTAMP,
  operator_user_id BIGINT,
  export_reason TEXT,
  bound_event_ids TEXT,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_export_job_no
ON system_supervision_alert_review_export_job(tenant_id, job_no)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_export_job_case
ON system_supervision_alert_review_export_job(tenant_id, review_case_id, status, expires_at);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_runtime_lock (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  lock_name VARCHAR(128) NOT NULL,
  owner_user_id BIGINT,
  locked_until TIMESTAMP,
  last_locked_at TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_lock
ON system_supervision_alert_review_runtime_lock(tenant_id, lock_name)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_lock_until
ON system_supervision_alert_review_runtime_lock(tenant_id, locked_until);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_runtime_run (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  run_id VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  alerts TEXT,
  recommended_actions TEXT,
  operator_user_id BIGINT,
  executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  metadata TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_run
ON system_supervision_alert_review_runtime_run(tenant_id, run_id)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_run_status
ON system_supervision_alert_review_runtime_run(tenant_id, status, executed_at);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_runtime_outbox (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  run_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  alert_key VARCHAR(128) NOT NULL,
  payload TEXT,
  outbox_status VARCHAR(64) NOT NULL DEFAULT 'pending',
  claim_token VARCHAR(128),
  claimed_by BIGINT,
  claimed_at TIMESTAMP,
  operator_user_id BIGINT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP,
  retry_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

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

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_runtime_outbox_delivery (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  outbox_id BIGINT NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  alert_key VARCHAR(128) NOT NULL,
  channel VARCHAR(64) NOT NULL,
  recipient_user_id BIGINT NOT NULL,
  template_code VARCHAR(128) NOT NULL,
  delivery_status VARCHAR(32) NOT NULL,
  notify_message_id BIGINT,
  attempt_count INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  last_attempt_at TIMESTAMP,
  delivered_at TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_supervision_alert_review_runtime_outbox_delivery_outbox
    FOREIGN KEY (outbox_id)
    REFERENCES system_supervision_alert_review_runtime_outbox(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_outbox_delivery_recipient
ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, outbox_id, channel, recipient_user_id, template_code)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_delivery_status
ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, delivery_status, last_attempt_at);

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_delivery_alert
ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, event_type, alert_key);

CREATE TABLE IF NOT EXISTS system_supervision_alert_review_report_ack (
  id BIGSERIAL PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  report_key VARCHAR(128) NOT NULL,
  report_type VARCHAR(32) NOT NULL,
  period_start TIMESTAMP,
  period_end TIMESTAMP,
  review_item_ids TEXT NOT NULL,
  acknowledgement_status VARCHAR(32) NOT NULL,
  acknowledged_by BIGINT,
  acknowledged_at TIMESTAMP,
  acknowledgement_note TEXT,
  metadata TEXT,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_report_ack_key
ON system_supervision_alert_review_report_ack(tenant_id, report_key)
WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_report_ack_scope
ON system_supervision_alert_review_report_ack(tenant_id, report_type, period_start, period_end);

CREATE TABLE IF NOT EXISTS system_supervision_close_check_result (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  check_result VARCHAR(64) NOT NULL,
  hard_block_items TEXT NOT NULL,
  soft_warning_items TEXT NOT NULL,
  exception_reason TEXT,
  checked_by BIGINT,
  checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);
