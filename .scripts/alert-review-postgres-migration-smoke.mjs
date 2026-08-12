import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const MIGRATION_FILES = [
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260702__alert_review_frigate_hardening.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260704__alert_review_segment_tenant_scope.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260705__alert_review_review_data_backfill.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260707__alert_review_item_media_audit.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708__alert_review_segment_status_transition.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_2__alert_review_scheduler_jobs.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_3__alert_review_report_ack.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_4__alert_review_runtime_outbox_notify_templates.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_5__alert_review_runtime_outbox_delivery.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_6__alert_review_runtime_outbox_claim.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_7__alert_review_segment_end_time_guard.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_8__alert_review_segment_alert_severity_guard.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_9__alert_review_merge_index_same_camera.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_10__alert_review_deleted_smallint.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260709__alert_review_scheduler_activation.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260710__alert_review_export_queue.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260712__alert_review_semantic_trigger_confirmation.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713__alert_review_semantic_index_claim.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_2__alert_review_evidence_record_start.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_3__supervision_event_create_permission.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_5__alert_review_notify_message_params_text.sql',
];

export function parseArgs(args, cwd = process.cwd()) {
  const parsed = {
    container: null,
    databaseUrl: null,
    database: `yfeieye_alert_review_migration_smoke_${Date.now()}`,
    repoRoot: cwd,
    keepDatabase: false,
    help: false,
  };

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg === '--keep-database') {
      parsed.keepDatabase = true;
    } else if (arg.startsWith('--container=')) {
      parsed.container = arg.slice('--container='.length);
    } else if (arg.startsWith('--database-url=')) {
      parsed.databaseUrl = arg.slice('--database-url='.length);
    } else if (arg.startsWith('--database=')) {
      parsed.database = arg.slice('--database='.length);
    } else if (arg.startsWith('--repo-root=')) {
      parsed.repoRoot = resolve(arg.slice('--repo-root='.length));
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  return parsed;
}

export function buildBootstrapSql() {
  return `
CREATE SEQUENCE system_menu_seq START WITH 10000;

CREATE TABLE system_menu (
  id BIGINT PRIMARY KEY,
  name VARCHAR(50) NOT NULL,
  permission VARCHAR(100) NOT NULL DEFAULT '',
  type SMALLINT NOT NULL,
  sort INTEGER NOT NULL DEFAULT 0,
  parent_id BIGINT NOT NULL DEFAULT 0,
  path VARCHAR(200),
  icon VARCHAR(100),
  component VARCHAR(255),
  component_name VARCHAR(255),
  status SMALLINT NOT NULL DEFAULT 0,
  visible BOOLEAN NOT NULL DEFAULT TRUE,
  keep_alive BOOLEAN NOT NULL DEFAULT TRUE,
  always_show BOOLEAN NOT NULL DEFAULT TRUE,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE infra_job (
  id BIGINT PRIMARY KEY,
  name VARCHAR(32) NOT NULL,
  status SMALLINT NOT NULL,
  handler_name VARCHAR(64) NOT NULL,
  handler_param VARCHAR(255),
  cron_expression VARCHAR(32) NOT NULL,
  retry_count INTEGER NOT NULL DEFAULT 0,
  retry_interval INTEGER NOT NULL DEFAULT 0,
  monitor_timeout INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE system_notify_template (
  id BIGINT PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  code VARCHAR(128) NOT NULL,
  type SMALLINT NOT NULL,
  nickname VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  params TEXT,
  status SMALLINT NOT NULL DEFAULT 0,
  remark VARCHAR(512),
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE TABLE system_notify_message (
  id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
  user_id BIGINT NOT NULL,
  user_type SMALLINT NOT NULL,
  template_id BIGINT NOT NULL,
  template_code VARCHAR(64) NOT NULL,
  template_nickname VARCHAR(63) NOT NULL,
  template_content VARCHAR(1024) NOT NULL,
  template_type INTEGER NOT NULL,
  template_params VARCHAR(255) NOT NULL,
  read_status BOOLEAN NOT NULL,
  read_time TIMESTAMP,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted SMALLINT NOT NULL DEFAULT 0,
  tenant_id BIGINT NOT NULL DEFAULT 0
);

INSERT INTO system_menu(
  id, name, permission, type, sort, parent_id, path, icon, component, component_name,
  status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
VALUES (
  9001, '旧复核录像播放', 'system:supervision-alert-review:media:playback', 3, 10, 0, '', '#', NULL, NULL,
  0, TRUE, TRUE, TRUE, 'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, 1
);

`;
}

export function buildLegacyReviewFixtureSql() {
  return `
INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES
  (1, 1001, 'fixture-review-1', 'video', 'motion', E'a-shared\\na-unique-1\\na-shared', 'person', '2026-07-05 10:00',
   'pending_review', 'camera-01', 'zone-a', 'rule-a', '2026-07-05 10:00',
   '{"correlationId":"legacy-correlation","confidence":0.87,"bbox":[1,2,3,4]}', 0),
  (2, 2002, 'fixture-review-2', 'video', 'alert', E'a-shared\\na-unique-2', 'car', '2026-07-05 10:00',
   'pending_review', 'camera-01', 'zone-a', 'rule-a', '2026-07-05 10:00', NULL, 0),
  (3, 1001, 'fixture-review-3', 'video', 'alert', 'a-shared', 'dog', '2026-07-05 10:10',
   'pending_review', 'camera-02', 'zone-b', 'rule-b', '2026-07-05 10:10',
   '{"reviewDataVersion":1,"labels":["dog"],"zones":["zone-b"],"objectIds":[],"objects":[{"label":"dog"}],"detections":[{"sourceAlertId":"a-shared","alertTime":"2026-07-05 10:10:00","cameraId":"camera-02"}],"reviewSegment":{"segmentId":"legacy-seg","cameraId":"camera-02","severity":"alert","status":"active","startTime":"2026-07-05 10:10:00","endTime":"2026-07-05 10:10:00","sourceAlertIds":["a-shared"]}}', 0);

INSERT INTO system_supervision_alert_review_segment(
  review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
)
VALUES
  (1, 'seg-tenant-1001', 1001, 'camera-01', 'alert', 'active', '2026-07-05 10:00', '2026-07-05 10:05', 0),
  (2, 'seg-tenant-2002', 2002, 'camera-01', 'alert', 'active', '2026-07-05 10:01', '2026-07-05 10:04', 0);
`;
}

export function buildLegacyBooleanDeletedFixtureSql() {
  return `
DROP INDEX IF EXISTS uk_supervision_alert_review_runtime_outbox_delivery_recipient;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_delivery_status;
DROP INDEX IF EXISTS idx_supervision_alert_review_runtime_outbox_delivery_alert;

ALTER TABLE system_supervision_alert_review_runtime_outbox_delivery
  DROP COLUMN tenant_id;
ALTER TABLE system_supervision_alert_review_runtime_outbox_delivery
  ALTER COLUMN deleted DROP DEFAULT;
ALTER TABLE system_supervision_alert_review_runtime_outbox_delivery
  ALTER COLUMN deleted TYPE BOOLEAN USING (deleted <> 0);
ALTER TABLE system_supervision_alert_review_runtime_outbox_delivery
  ALTER COLUMN deleted SET DEFAULT FALSE;

INSERT INTO system_supervision_alert_review_runtime_outbox(
  id, tenant_id, run_id, event_type, alert_key, outbox_status, deleted
)
VALUES (99001, 1001, 'legacy-boolean-deleted', 'runtime_alert', 'legacy-boolean-deleted', 'pending', 0);

INSERT INTO system_supervision_alert_review_runtime_outbox_delivery(
  id, outbox_id, event_type, alert_key, channel, recipient_user_id, template_code, delivery_status, deleted
)
VALUES (99002, 99001, 'runtime_alert', 'legacy-boolean-deleted', 'notify', 1,
        'YFEIEYE_REVIEW_RUNTIME_ALERT', 'pending', TRUE);

CREATE UNIQUE INDEX uk_supervision_alert_review_runtime_outbox_delivery_recipient
ON system_supervision_alert_review_runtime_outbox_delivery(outbox_id, channel, recipient_user_id, template_code)
WHERE deleted = FALSE;

CREATE INDEX idx_supervision_alert_review_runtime_outbox_delivery_status
ON system_supervision_alert_review_runtime_outbox_delivery(delivery_status, last_attempt_at);

CREATE INDEX idx_supervision_alert_review_runtime_outbox_delivery_alert
ON system_supervision_alert_review_runtime_outbox_delivery(event_type, alert_key);
`;
}

export function buildLegacyOperationsReportDuplicateFixtureSql() {
  return `
INSERT INTO system_supervision_alert_review_runtime_outbox(
  id, tenant_id, run_id, event_type, alert_key, payload, outbox_status, deleted
)
VALUES
  (99601, 7007, 'report-duplicate-first', 'review_operations_report', 'report-duplicate', '{}', 'pending', 0),
  (99602, 7007, 'report-duplicate-second', 'review_operations_report', 'report-duplicate', '{}', 'failed', 0),
  (99603, 7008, 'report-other-tenant', 'review_operations_report', 'report-duplicate', '{}', 'pending', 0),
  (99604, 7007, 'report-other-event', 'runtime_alert', 'report-duplicate', '{}', 'pending', 0),
  (99605, 7007, 'report-already-deleted', 'review_operations_report', 'report-duplicate', '{}', 'pending', 1);

INSERT INTO system_supervision_alert_review_runtime_outbox_delivery(
  id, tenant_id, outbox_id, event_type, alert_key, channel, recipient_user_id,
  template_code, delivery_status, deleted
)
VALUES
  (99611, 7007, 99601, 'review_operations_report', 'report-duplicate', 'notify', 9001,
   'YFEIEYE_REVIEW_OPERATIONS_REPORT', 'pending', 0),
  (99612, 7007, 99602, 'review_operations_report', 'report-duplicate', 'notify', 9001,
   'YFEIEYE_REVIEW_OPERATIONS_REPORT', 'pending', 0);
`;
}

export function buildPostMigrationAssertionSql() {
  return `
DO $$
BEGIN
  IF (
    WITH expected(table_name) AS (
      VALUES
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
    )
    SELECT count(*)
    FROM expected
    JOIN information_schema.columns AS tenant_column
      ON tenant_column.table_schema = current_schema()
     AND tenant_column.table_name = expected.table_name
     AND tenant_column.column_name = 'tenant_id'
     AND tenant_column.data_type = 'bigint'
     AND tenant_column.is_nullable = 'NO'
     AND tenant_column.column_default LIKE '0%'
    JOIN information_schema.columns AS deleted_column
      ON deleted_column.table_schema = current_schema()
     AND deleted_column.table_name = expected.table_name
     AND deleted_column.column_name = 'deleted'
     AND deleted_column.data_type = 'smallint'
     AND deleted_column.is_nullable = 'NO'
     AND deleted_column.column_default LIKE '0%'
  ) <> 18 THEN
    RAISE EXCEPTION 'expected all BaseDO review tables to use tenant BIGINT and deleted SMALLINT platform columns';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_runtime_outbox_delivery
    WHERE id = 99002
      AND tenant_id = 0
      AND deleted = 1
  ) THEN
    RAISE EXCEPTION 'expected historical BOOLEAN deleted and missing tenant column to migrate to SMALLINT 1 and tenant 0';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE schemaname = current_schema()
      AND indexname = 'uk_supervision_alert_review_runtime_outbox_delivery_recipient'
      AND indexdef LIKE '%(tenant_id, outbox_id, channel, recipient_user_id, template_code)%'
  ) THEN
    RAISE EXCEPTION 'expected runtime outbox delivery identity to be tenant scoped';
  END IF;

  IF (
    SELECT count(*)
    FROM system_menu
    WHERE permission IN (
      'system:supervision-alert-review:media:playback',
      'system:supervision-alert-review:media:snapshot',
      'system:supervision-alert-review:media:export',
      'system:supervision-alert-review:media:download',
      'system:supervision-alert-review:media:manifest',
      'system:supervision-alert-review:media:manage'
    )
      AND type = 3
      AND status = 0
      AND deleted = 0
  ) <> 6 THEN
    RAISE EXCEPTION 'expected review media permission seeds to be present';
  END IF;

  IF (
    SELECT count(*)
    FROM system_menu
    WHERE permission = 'system:supervision-alert-review:media:playback'
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected review media permission migration to restore existing playback seed without duplicates';
  END IF;

  IF (
    SELECT count(*)
    FROM system_menu
    WHERE permission IN (
      'system:supervision-alert-review:semantic-trigger:evaluate',
      'system:supervision-alert-review:semantic-trigger:confirm'
    )
      AND type = 3
      AND status = 0
      AND deleted = 0
  ) <> 2 THEN
    RAISE EXCEPTION 'expected semantic trigger permission seeds to be present';
  END IF;

  IF (
    SELECT count(*)
    FROM system_menu
    WHERE permission = 'supervision:event:create'
      AND type = 3
      AND status = 0
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected supervision event create permission seed to be present';
  END IF;

  IF (
    SELECT count(*)
    FROM pg_indexes
    WHERE schemaname = current_schema()
      AND tablename = 'system_supervision_alert_review_case_audit'
      AND indexname IN (
        'uk_alert_review_semantic_trigger_evaluation',
        'uk_alert_review_semantic_trigger_terminal'
      )
      AND indexdef LIKE '%UNIQUE INDEX%'
      AND indexdef LIKE '%tenant_id%'
  ) <> 2 THEN
    RAISE EXCEPTION 'expected semantic trigger confirmation indexes to be tenant scoped';
  END IF;

  IF (
    SELECT count(*)
    FROM information_schema.columns
    WHERE table_name = 'system_supervision_alert_review_semantic_index'
      AND column_name IN (
        'index_generation_id', 'claim_token', 'claimed_at', 'claim_expires_at', 'next_retry_at'
      )
  ) <> 5 THEN
    RAISE EXCEPTION 'expected semantic index generation and worker claim columns to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'system_supervision_alert_review_evidence'
      AND column_name = 'record_start_time'
      AND data_type = 'timestamp without time zone'
  ) THEN
    RAISE EXCEPTION 'expected review evidence recording start column to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_semantic_index'
      AND indexname = 'idx_alert_review_semantic_claim'
  ) THEN
    RAISE EXCEPTION 'expected semantic index worker claim index to exist';
  END IF;

  IF (
    SELECT count(*)
    FROM infra_job
    WHERE handler_name IN (
      'supervisionAlertReviewRuntimePatrolJob',
      'supervisionAlertReviewRuntimeOutboxJob',
      'supervisionAlertReviewEventReconcileJob',
      'supervisionAlertReviewEvidenceExportWorkerJob',
      'supervisionAlertReviewSemanticIndexJob',
      'supervisionAlertReviewOperationsReportJob'
    )
      AND status = 2
      AND deleted = 0
  ) <> 7 THEN
    RAISE EXCEPTION 'expected local-owned alert review Quartz jobs to be paused';
  END IF;

  IF (
    SELECT count(*)
    FROM infra_job
    WHERE handler_name = 'supervisionAlertReviewEventReconcileJob'
      AND cron_expression = '0 0/5 * * * ?'
      AND status = 2
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected event reconcile Quartz seed to be paused and deduplicated';
  END IF;

  IF (
    SELECT count(*)
    FROM infra_job
    WHERE handler_name = 'supervisionAlertReviewEvidenceExportWorkerJob'
      AND handler_param = '20'
      AND cron_expression = '0 0/2 * * * ?'
      AND status = 2
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected evidence export Quartz seed to be paused and deduplicated';
  END IF;

  IF (
    SELECT count(*)
    FROM infra_job
    WHERE handler_name = 'supervisionAlertReviewOperationsReportJob'
      AND handler_param IN ('shift', 'daily')
      AND status = 2
      AND deleted = 0
  ) <> 2 THEN
    RAISE EXCEPTION 'expected paused operations report seeds to cover shift and daily reports';
  END IF;

  IF (
    SELECT count(*)
    FROM system_notify_template
    WHERE code IN (
      'YFEIEYE_REVIEW_RUNTIME_ALERT',
      'YFEIEYE_REVIEW_OPERATIONS_REPORT'
    )
      AND status = 0
      AND deleted = 0
  ) <> 2 THEN
    RAISE EXCEPTION 'expected runtime outbox notify templates to be seeded';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'system_notify_message'
      AND column_name = 'template_params'
      AND data_type = 'text'
      AND character_maximum_length IS NULL
  ) THEN
    RAISE EXCEPTION 'expected runtime notify template_params to remove the legacy 255-character limit';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.tables
    WHERE table_name = 'system_supervision_alert_review_runtime_outbox_delivery'
  ) THEN
    RAISE EXCEPTION 'expected runtime outbox delivery table to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_runtime_outbox_delivery'
      AND indexname = 'uk_supervision_alert_review_runtime_outbox_delivery_recipient'
  ) THEN
    RAISE EXCEPTION 'expected runtime outbox delivery recipient idempotency index to exist';
  END IF;

  IF (
    SELECT count(*)
    FROM information_schema.columns
    WHERE table_name = 'system_supervision_alert_review_runtime_outbox'
      AND column_name IN ('claim_token', 'claimed_by', 'claimed_at')
  ) <> 3 THEN
    RAISE EXCEPTION 'expected runtime outbox claim columns to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_runtime_outbox'
      AND indexname = 'idx_supervision_alert_review_runtime_outbox_claim'
  ) THEN
    RAISE EXCEPTION 'expected runtime outbox claim index to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_index index_info
    JOIN pg_class index_class ON index_class.oid = index_info.indexrelid
    JOIN pg_class table_class ON table_class.oid = index_info.indrelid
    WHERE table_class.relname = 'system_supervision_alert_review_runtime_outbox'
      AND index_class.relname = 'uk_supervision_alert_review_runtime_outbox_report'
      AND index_info.indisunique
      AND index_info.indnkeyatts = 3
      AND pg_get_indexdef(index_info.indexrelid, 1, TRUE) = 'tenant_id'
      AND pg_get_indexdef(index_info.indexrelid, 2, TRUE) = 'event_type'
      AND pg_get_indexdef(index_info.indexrelid, 3, TRUE) = 'alert_key'
      AND pg_get_expr(index_info.indpred, index_info.indrelid) LIKE '%deleted = 0%'
      AND pg_get_expr(index_info.indpred, index_info.indrelid) LIKE '%review_operations_report%'
  ) THEN
    RAISE EXCEPTION 'expected operations report outbox idempotency index to exist';
  END IF;

  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_runtime_outbox
    WHERE tenant_id = 7007
      AND event_type = 'review_operations_report'
      AND alert_key = 'report-duplicate'
      AND deleted = 0
  ) <> 1 OR NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_runtime_outbox
    WHERE id = 99601 AND deleted = 0
  ) OR NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_runtime_outbox
    WHERE id = 99602 AND deleted = 1
  ) THEN
    RAISE EXCEPTION 'expected operations report migration to preserve the earliest active duplicate';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM system_supervision_alert_review_runtime_outbox_delivery
    WHERE id = 99611 AND deleted = 0
  ) OR NOT EXISTS (
    SELECT 1 FROM system_supervision_alert_review_runtime_outbox_delivery
    WHERE id = 99612 AND deleted = 1
  ) THEN
    RAISE EXCEPTION 'expected operations report dedupe to retire duplicate recipient deliveries';
  END IF;

  IF (
    SELECT count(*) FROM system_supervision_alert_review_runtime_outbox
    WHERE id IN (99603, 99604, 99605)
  ) <> 3 OR (
    SELECT count(*) FROM system_supervision_alert_review_runtime_outbox
    WHERE id IN (99603, 99604) AND deleted = 0
  ) <> 2 OR (
    SELECT count(*) FROM system_supervision_alert_review_runtime_outbox
    WHERE id = 99605 AND deleted = 1
  ) <> 1 THEN
    RAISE EXCEPTION 'expected operations report dedupe to preserve other tenants, event types, and deleted history';
  END IF;

  IF (
    SELECT count(*)
    FROM information_schema.columns
    WHERE table_name = 'system_supervision_alert_review_export_job'
      AND column_name IN (
        'request_key', 'claim_token', 'claimed_by', 'claimed_at', 'next_retry_at', 'last_error'
      )
  ) <> 6 THEN
    RAISE EXCEPTION 'expected persistent evidence export queue columns to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_export_job'
      AND indexname = 'uk_supervision_alert_review_export_request'
      AND indexdef LIKE '%tenant_id, request_key%'
  ) THEN
    RAISE EXCEPTION 'expected tenant-scoped evidence export request idempotency index to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_export_job'
      AND indexname = 'idx_supervision_alert_review_export_claim'
  ) THEN
    RAISE EXCEPTION 'expected evidence export worker claim index to exist';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_item'
      AND indexname = 'idx_supervision_alert_review_merge'
      AND indexdef LIKE '%tenant_id, source_system, camera_id, review_status, last_alert_time%'
  ) THEN
    RAISE EXCEPTION 'expected alert review merge index to use same-camera window columns';
  END IF;

  IF EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_item'
      AND indexname = 'idx_supervision_alert_review_merge'
      AND (indexdef LIKE '%zone_code%' OR indexdef LIKE '%rule_code%')
  ) THEN
    RAISE EXCEPTION 'expected alert review merge index to ignore zone/rule for same-camera segment merging';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_name = 'system_supervision_alert_review_case_audit'
      AND column_name = 'review_case_id'
      AND is_nullable = 'YES'
  ) THEN
    RAISE EXCEPTION 'expected review case audit to allow pre-case media audit rows';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_indexes
    WHERE tablename = 'system_supervision_alert_review_case_audit'
      AND indexname = 'idx_supervision_alert_review_case_audit_item'
  ) THEN
    RAISE EXCEPTION 'expected review item media audit lookup index to exist';
  END IF;

  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_ingest_identity
    WHERE deleted = 0
  ) <> 4 THEN
    RAISE EXCEPTION 'expected tenant-scoped ingest identity backfill to deduplicate historical source alerts into 4 rows';
  END IF;

  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_ingest_identity
    WHERE tenant_id = 1001
      AND source_system = 'video'
      AND identity_key = 'video:alert:a-shared'
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected tenant-scoped ingest identity backfill for tenant 1001 shared alert';
  END IF;

  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_ingest_identity
    WHERE tenant_id = 2002
      AND source_system = 'video'
      AND identity_key = 'video:alert:a-shared'
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected tenant-scoped ingest identity backfill for tenant 2002 shared alert';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_item
    WHERE id = 1
      AND (review_data::jsonb ->> 'reviewDataVersion') = '1'
      AND (review_data::jsonb -> 'labels') @> '["person"]'::jsonb
      AND (review_data::jsonb -> 'zones') @> '["zone-a"]'::jsonb
      AND jsonb_array_length(review_data::jsonb -> 'objectIds') = 0
      AND jsonb_array_length(review_data::jsonb -> 'objects') = 1
      AND jsonb_array_length(review_data::jsonb -> 'detections') = 1
      AND (review_data::jsonb #>> '{detections,0,source}') = 'migration_backfill'
      AND (review_data::jsonb #>> '{detections,0,sourceAlertId}') = 'a-shared'
      AND (review_data::jsonb #>> '{reviewSegment,cameraId}') = 'camera-01'
      AND (review_data::jsonb #>> '{reviewSegment,severity}') = 'detection'
      AND (review_data::jsonb #>> '{reviewSegment,status}') = 'active'
      AND (review_data::jsonb -> 'reviewSegment' -> 'sourceAlertIds') @> '["a-shared","a-unique-1"]'::jsonb
      AND (review_data::jsonb ->> 'correlationId') = 'legacy-correlation'
  ) THEN
    RAISE EXCEPTION 'expected ReviewData backfill to normalize legacy rows';
  END IF;

  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_item
    WHERE deleted = 0
      AND (
        review_data IS NULL
        OR btrim(review_data) = ''
        OR (review_data::jsonb ->> 'reviewDataVersion') IS DISTINCT FROM '1'
        OR jsonb_typeof(review_data::jsonb -> 'labels') IS DISTINCT FROM 'array'
        OR jsonb_typeof(review_data::jsonb -> 'zones') IS DISTINCT FROM 'array'
        OR jsonb_typeof(review_data::jsonb -> 'objectIds') IS DISTINCT FROM 'array'
        OR jsonb_typeof(review_data::jsonb -> 'objects') IS DISTINCT FROM 'array'
        OR jsonb_typeof(review_data::jsonb -> 'detections') IS DISTINCT FROM 'array'
        OR jsonb_typeof(review_data::jsonb -> 'reviewSegment') IS DISTINCT FROM 'object'
      )
  ) <> 0 THEN
    RAISE EXCEPTION 'expected ReviewData backfill to leave no legacy schema drift rows';
  END IF;
END $$;

DO $$
DECLARE
  affected_rows INTEGER;
BEGIN
  UPDATE system_supervision_alert_review_item
  SET review_status = 'reviewed',
      version = version + 1
  WHERE id = 1
    AND review_status = 'pending_review'
    AND version = 0;
  GET DIAGNOSTICS affected_rows = ROW_COUNT;
  IF affected_rows <> 1 THEN
    RAISE EXCEPTION 'expected first review status version update to affect one row';
  END IF;

  UPDATE system_supervision_alert_review_item
  SET review_status = 'ignored',
      version = version + 1
  WHERE id = 1
    AND version = 0;
  GET DIAGNOSTICS affected_rows = ROW_COUNT;
  IF affected_rows <> 0 THEN
    RAISE EXCEPTION 'expected stale review status version update to affect no rows';
  END IF;

  UPDATE system_supervision_alert_review_item
  SET review_status = 'reviewed'
  WHERE id = 1
    AND review_status = 'reviewed'
    AND version = 1;
  GET DIAGNOSTICS affected_rows = ROW_COUNT;
  IF affected_rows <> 1 THEN
    RAISE EXCEPTION 'expected repeated same-status reviewer update to be idempotent';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_item
    WHERE id = 1
      AND review_status = 'reviewed'
      AND version = 1
  ) THEN
    RAISE EXCEPTION 'expected repeated same-status reviewer update to keep version stable';
  END IF;
END $$;

DO $$
BEGIN
  BEGIN
    INSERT INTO system_supervision_alert_review_ingest_identity(
      tenant_id, review_item_id, source_system, identity_key, source_alert_id, deleted
    )
    VALUES (1001, 999, 'video', 'video:alert:a-shared', 'a-shared', 0);
    RAISE EXCEPTION 'expected duplicate tenant/source identity to be rejected';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;
END $$;

INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES (4, 1001, 'fixture-review-4', 'video', 'alert', 'a-overlap', 'person', '2026-07-05 10:02',
  'pending_review', 'camera-01', 'zone-a', 'rule-a', '2026-07-05 10:02', NULL, 0);

DO $$
BEGIN
  BEGIN
    INSERT INTO system_supervision_alert_review_segment(
      review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
    )
    VALUES (4, 'seg-overlap-tenant-1001', 1001, 'camera-01', 'alert', 'active', '2026-07-05 10:02', '2026-07-05 10:03', 0);
    RAISE EXCEPTION 'expected same-tenant camera/time overlap to be rejected';
  EXCEPTION WHEN exclusion_violation THEN
    NULL;
  END;
END $$;

INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES (9, 1001, 'fixture-review-9', 'video', 'motion', 'a-adjacent-boundary', 'person', '2026-07-05 10:05',
  'pending_review', 'camera-01', 'zone-a', 'rule-a', '2026-07-05 10:05', NULL, 0);

INSERT INTO system_supervision_alert_review_segment(
  review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
)
VALUES (9, 'seg-adjacent-boundary-tenant-1001', 1001, 'camera-01', 'detection', 'detection', '2026-07-05 10:05', '2026-07-05 10:06', 0);

DO $$
BEGIN
  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_segment
    WHERE segment_no = 'seg-adjacent-boundary-tenant-1001'
      AND camera_id = 'camera-01'
      AND start_time = '2026-07-05 10:05'
      AND end_time = '2026-07-05 10:06'
      AND deleted = 0
  ) <> 1 THEN
    RAISE EXCEPTION 'expected adjacent same-camera ReviewSegment boundary to be allowed';
  END IF;
END $$;

DO $$
BEGIN
  BEGIN
    INSERT INTO system_supervision_alert_review_segment(
      review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
    )
    VALUES (1, 'seg-duplicate-review-item-active', 1001, 'camera-review-item-unique-01', 'alert', 'active', '2026-07-05 12:30', '2026-07-05 12:31', 0);
    RAISE EXCEPTION 'expected duplicate active ReviewSegment review_item_id to be rejected';
  EXCEPTION WHEN unique_violation THEN
    NULL;
  END;

  INSERT INTO system_supervision_alert_review_segment(
    review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
  )
  VALUES (1, 'seg-duplicate-review-item-deleted', 1001, 'camera-review-item-unique-02', 'alert', 'active', '2026-07-05 12:32', '2026-07-05 12:33', 1);

  IF (
    SELECT count(*)
    FROM system_supervision_alert_review_segment
    WHERE review_item_id = 1
      AND segment_no = 'seg-duplicate-review-item-deleted'
      AND deleted = 1
  ) <> 1 THEN
    RAISE EXCEPTION 'expected deleted duplicate ReviewSegment review_item_id to be allowed';
  END IF;
END $$;

INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES
  (5, 1001, 'fixture-review-5', 'video', 'motion', 'a-open-active-1', 'person', '2026-07-05 11:00',
   'pending_review', 'camera-open-01', 'zone-a', 'rule-a', '2026-07-05 11:00', NULL, 0),
  (6, 1001, 'fixture-review-6', 'video', 'motion', 'a-open-active-2', 'person', '2026-07-05 11:01',
   'pending_review', 'camera-open-01', 'zone-a', 'rule-a', '2026-07-05 11:01', NULL, 0);

DO $$
BEGIN
  INSERT INTO system_supervision_alert_review_segment(
    review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
  )
  VALUES (5, 'seg-open-active-tenant-1001', 1001, 'camera-open-01', 'detection', 'active', '2026-07-05 11:00', NULL, 0);
  BEGIN
    INSERT INTO system_supervision_alert_review_segment(
      review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
    )
    VALUES (6, 'seg-open-active-overlap-tenant-1001', 1001, 'camera-open-01', 'detection', 'active', '2026-07-05 11:01', NULL, 0);
    RAISE EXCEPTION 'expected open active ReviewSegment to block later same-camera segment';
  EXCEPTION WHEN exclusion_violation THEN
    NULL;
  END;
END $$;

INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES
  (7, 1001, 'fixture-review-7', 'video', 'motion', 'a-transition-1', 'person', '2026-07-05 11:10',
   'pending_review', 'camera-transition-01', 'zone-a', 'rule-a', '2026-07-05 11:10', NULL, 0),
  (8, 1001, 'fixture-review-8', 'video', 'motion', 'a-transition-2', 'person', '2026-07-05 11:20',
   'pending_review', 'camera-transition-ended-01', 'zone-a', 'rule-a', '2026-07-05 11:20', NULL, 0);

INSERT INTO system_supervision_alert_review_segment(
  review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
)
VALUES
  (7, 'seg-transition-active', 1001, 'camera-transition-01', 'detection', 'active', '2026-07-05 11:10', NULL, 0),
  (8, 'seg-transition-ended', 1001, 'camera-transition-ended-01', 'alert', 'ended', '2026-07-05 11:20', '2026-07-05 11:21', 0);

INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES (10, 1001, 'fixture-review-10', 'video', 'motion', 'a-ended-null-end-time', 'person', '2026-07-05 11:30',
  'pending_review', 'camera-transition-02', 'zone-a', 'rule-a', '2026-07-05 11:30', NULL, 0);

INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_type, source_alert_ids, object_label, first_alert_time,
  review_status, camera_id, zone_code, rule_code, last_alert_time, review_data, deleted
)
VALUES (11, 1001, 'fixture-review-11', 'video', 'motion', 'a-alert-detection-severity', 'person', '2026-07-05 11:40',
  'pending_review', 'camera-transition-03', 'zone-a', 'rule-a', '2026-07-05 11:40', NULL, 0);

UPDATE system_supervision_alert_review_segment
SET segment_status = 'alert',
    severity = 'alert'
WHERE segment_no = 'seg-transition-active';

DO $$
BEGIN
  BEGIN
    UPDATE system_supervision_alert_review_segment
    SET segment_status = 'detection'
    WHERE segment_no = 'seg-transition-active';
    RAISE EXCEPTION 'expected alert ReviewSegment downgrade to detection to be rejected';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END $$;

DO $$
BEGIN
  BEGIN
    UPDATE system_supervision_alert_review_segment
    SET segment_status = 'active'
    WHERE segment_no = 'seg-transition-ended';
    RAISE EXCEPTION 'expected ended ReviewSegment reopen to be rejected';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_trigger
    WHERE tgname = 'tr_supervision_alert_review_segment_status_transition'
  ) THEN
    RAISE EXCEPTION 'expected ReviewSegment status transition trigger to exist';
  END IF;
END $$;

DO $$
BEGIN
  BEGIN
    INSERT INTO system_supervision_alert_review_segment(
      review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
    )
    VALUES (10, 'seg-ended-null-end-time', 1001, 'camera-transition-02', 'alert', 'ended', '2026-07-05 11:30', NULL, 0);
    RAISE EXCEPTION 'expected ended ReviewSegment without end_time to be rejected';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END $$;

DO $$
BEGIN
  BEGIN
    INSERT INTO system_supervision_alert_review_segment(
      review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
    )
    VALUES (11, 'seg-alert-detection-severity', 1001, 'camera-transition-03', 'detection', 'alert', '2026-07-05 11:40', '2026-07-05 11:41', 0);
    RAISE EXCEPTION 'expected alert ReviewSegment with detection severity to be rejected';
  EXCEPTION WHEN check_violation THEN
    NULL;
  END;
END $$;

SELECT 'alert review postgres migration smoke passed' AS result;
`;
}

export function buildConcurrentDuplicateIdentityInsertSql() {
  return `
INSERT INTO system_supervision_alert_review_ingest_identity(
  tenant_id, review_item_id, source_system, identity_key, source_alert_id, deleted
)
VALUES (3003, 3003, 'video', 'video:alert:a-race', 'a-race', 0);
`;
}

export function buildConcurrentReviewStatusBootstrapSql() {
  return `
INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_ids, review_status, camera_id, zone_code, rule_code,
  first_alert_time, last_alert_time, version, deleted
)
VALUES (8001, 5005, 'fixture-review-status-8001', 'video', 'review-status-race', 'pending_review', 'camera-review-status-race-01', 'zone-a', 'rule-a',
  '2026-07-05 13:00', '2026-07-05 13:00', 0, 0);
`;
}

export function buildConcurrentReviewStatusUpdateSql() {
  return `
UPDATE system_supervision_alert_review_item
SET review_status = 'reviewed',
    version = version + 1
WHERE id = 8001
  AND review_status = 'pending_review'
  AND version = 0;
`;
}

export function buildSemanticIndexQueueCasSmokeSql() {
  return `
DO $$
DECLARE
  active_update_count INTEGER;
  expired_update_count INTEGER;
BEGIN
  INSERT INTO system_supervision_alert_review_semantic_index(
    tenant_id, review_item_id, camera_id, first_alert_time, last_alert_time, index_status, document,
    embedding_key, embedding_model, retry_count, index_generation_id, claim_token, claimed_at,
    claim_expires_at, version, deleted
  ) VALUES (
    6006, 9001, 'camera-semantic-cas-01', TIMESTAMP '2026-07-13 10:00', TIMESTAMP '2026-07-13 10:05',
    'processing', 'active claim document', 'camera-semantic-cas-01:9001', 'local-hash-v1', 0,
    'sig-active-claim', 'active-worker-claim', TIMESTAMP '2026-07-13 10:20', TIMESTAMP '2026-07-13 10:40', 1, 0
  );

  INSERT INTO system_supervision_alert_review_semantic_index(
    tenant_id, review_item_id, camera_id, first_alert_time, last_alert_time, index_status, document,
    embedding_key, embedding_model, retry_count, index_generation_id, version, deleted
  ) VALUES (
    6006, 9001, 'camera-semantic-cas-01', TIMESTAMP '2026-07-13 10:00', TIMESTAMP '2026-07-13 10:05',
    'pending', 'queued document', 'camera-semantic-cas-01:9001', 'local-hash-v1', 0, 'sig-requeue', 0, 0
  )
  ON CONFLICT DO NOTHING;

  UPDATE system_supervision_alert_review_semantic_index
  SET camera_id = 'camera-semantic-cas-01',
      first_alert_time = TIMESTAMP '2026-07-13 10:00',
      last_alert_time = TIMESTAMP '2026-07-13 10:05',
      index_status = 'pending',
      document = 'queued document',
      embedding_key = 'camera-semantic-cas-01:9001',
      embedding_model = 'local-hash-v1',
      embedding_vector_hash = NULL,
      retry_count = 0,
      last_error = NULL,
      indexed_at = NULL,
      index_generation_id = 'sig-requeue',
      next_retry_at = NULL,
      claim_token = NULL,
      claimed_at = NULL,
      claim_expires_at = NULL,
      version = version + 1,
      update_time = CURRENT_TIMESTAMP
  WHERE tenant_id = 6006
    AND review_item_id = 9001
    AND deleted = 0
    AND (
      index_status <> 'processing'
      OR claim_token IS NULL
      OR claim_expires_at IS NULL
      OR claim_expires_at <= TIMESTAMP '2026-07-13 10:30'
    );
  GET DIAGNOSTICS active_update_count = ROW_COUNT;

  IF active_update_count <> 0 OR NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_semantic_index
    WHERE tenant_id = 6006
      AND review_item_id = 9001
      AND index_status = 'processing'
      AND claim_token = 'active-worker-claim'
      AND index_generation_id = 'sig-active-claim'
      AND deleted = 0
  ) THEN
    RAISE EXCEPTION 'expected active semantic index claim to survive reindex queue';
  END IF;

  UPDATE system_supervision_alert_review_semantic_index
  SET claim_expires_at = TIMESTAMP '2026-07-13 10:25'
  WHERE tenant_id = 6006 AND review_item_id = 9001 AND deleted = 0;

  INSERT INTO system_supervision_alert_review_semantic_index(
    tenant_id, review_item_id, camera_id, first_alert_time, last_alert_time, index_status, document,
    embedding_key, embedding_model, retry_count, index_generation_id, version, deleted
  ) VALUES (
    6006, 9001, 'camera-semantic-cas-01', TIMESTAMP '2026-07-13 10:00', TIMESTAMP '2026-07-13 10:05',
    'pending', 'queued after expiry', 'camera-semantic-cas-01:9001', 'local-hash-v1', 0,
    'sig-after-expiry', 0, 0
  )
  ON CONFLICT DO NOTHING;

  UPDATE system_supervision_alert_review_semantic_index
  SET index_status = 'pending',
      document = 'queued after expiry',
      index_generation_id = 'sig-after-expiry',
      claim_token = NULL,
      claimed_at = NULL,
      claim_expires_at = NULL,
      version = version + 1,
      update_time = CURRENT_TIMESTAMP
  WHERE tenant_id = 6006
    AND review_item_id = 9001
    AND deleted = 0
    AND (
      index_status <> 'processing'
      OR claim_token IS NULL
      OR claim_expires_at IS NULL
      OR claim_expires_at <= TIMESTAMP '2026-07-13 10:30'
    );
  GET DIAGNOSTICS expired_update_count = ROW_COUNT;

  IF expired_update_count <> 1 OR NOT EXISTS (
    SELECT 1
    FROM system_supervision_alert_review_semantic_index
    WHERE tenant_id = 6006
      AND review_item_id = 9001
      AND index_status = 'pending'
      AND claim_token IS NULL
      AND index_generation_id = 'sig-after-expiry'
      AND deleted = 0
  ) THEN
    RAISE EXCEPTION 'expected expired semantic index claim to be requeued';
  END IF;
END $$;

SELECT 'semantic index queue CAS smoke passed' AS result;
`;
}

export function buildConcurrentReviewSegmentBootstrapSql() {
  return `
INSERT INTO system_supervision_alert_review_item(
  id, tenant_id, review_item_no, source_system, source_alert_ids, review_status, camera_id, zone_code, rule_code,
  first_alert_time, last_alert_time, deleted
)
VALUES
  (7001, 4004, 'fixture-review-segment-7001', 'video', 'a-segment-race-1', 'pending_review', 'camera-segment-race-01', 'zone-a', 'rule-a',
   '2026-07-05 12:00', '2026-07-05 12:00', 0),
  (7002, 4004, 'fixture-review-segment-7002', 'video', 'a-segment-race-2', 'pending_review', 'camera-segment-race-01', 'zone-a', 'rule-a',
   '2026-07-05 12:00', '2026-07-05 12:00', 0);
`;
}

export function buildConcurrentReviewSegmentInsertSql({ reviewItemId, segmentNo }) {
  return `
INSERT INTO system_supervision_alert_review_segment(
  review_item_id, segment_no, tenant_id, camera_id, severity, segment_status, start_time, end_time, deleted
)
VALUES (${reviewItemId}, '${segmentNo}', 4004, 'camera-segment-race-01', 'detection', 'active', '2026-07-05 12:00', NULL, 0);
`;
}

export function buildConcurrentOperationsReportInsertSql(runId) {
  return `
INSERT INTO system_supervision_alert_review_runtime_outbox(
  tenant_id, run_id, event_type, alert_key, payload, outbox_status, retry_count, version
)
VALUES (8008, '${runId}', 'review_operations_report', 'report-concurrent', '{}', 'pending', 0, 0)
ON CONFLICT (tenant_id, event_type, alert_key)
WHERE deleted = 0 AND event_type = 'review_operations_report'
DO NOTHING;
`;
}

export function buildConcurrentReportAcknowledgementInsertSql({ userId, note }) {
  return `
INSERT INTO system_supervision_alert_review_report_ack(
  tenant_id, report_key, report_type, period_start, period_end, review_item_ids,
  acknowledgement_status, acknowledged_by, acknowledged_at, acknowledgement_note,
  metadata, version
)
VALUES (
  8008, 'report-ack-concurrent', 'shift', TIMESTAMP '2026-07-13 08:00',
  TIMESTAMP '2026-07-13 16:00', '', 'acknowledged', ${userId}, CURRENT_TIMESTAMP,
  '${note}', '{}', 0
)
ON CONFLICT (tenant_id, report_key) WHERE deleted = 0
DO NOTHING;
`;
}

export function buildReportAcknowledgementLifecycleSmokeSql() {
  return `
INSERT INTO system_supervision_alert_review_report_ack(
  tenant_id, report_key, report_type, period_start, period_end, review_item_ids,
  acknowledgement_status, acknowledged_by, acknowledged_at, acknowledgement_note,
  metadata, version
)
VALUES (
  8009, 'report-ack-concurrent', 'shift', TIMESTAMP '2026-07-13 08:00',
  TIMESTAMP '2026-07-13 16:00', '', 'acknowledged', 9201, CURRENT_TIMESTAMP,
  'other tenant', '{}', 0
);

UPDATE system_supervision_alert_review_report_ack
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE tenant_id = 8008 AND report_key = 'report-ack-concurrent' AND deleted = 0;

INSERT INTO system_supervision_alert_review_report_ack(
  tenant_id, report_key, report_type, period_start, period_end, review_item_ids,
  acknowledgement_status, acknowledged_by, acknowledged_at, acknowledgement_note,
  metadata, version
)
VALUES (
  8008, 'report-ack-concurrent', 'shift', TIMESTAMP '2026-07-13 08:00',
  TIMESTAMP '2026-07-13 16:00', '', 'acknowledged', 9202, CURRENT_TIMESTAMP,
  'replacement after soft delete', '{}', 0
);

DO $$
BEGIN
  IF (
    SELECT count(*) FROM system_supervision_alert_review_report_ack
    WHERE tenant_id = 8008 AND report_key = 'report-ack-concurrent' AND deleted = 0
  ) <> 1 OR NOT EXISTS (
    SELECT 1 FROM system_supervision_alert_review_report_ack
    WHERE tenant_id = 8008 AND report_key = 'report-ack-concurrent'
      AND acknowledged_by = 9202 AND deleted = 0
  ) OR NOT EXISTS (
    SELECT 1 FROM system_supervision_alert_review_report_ack
    WHERE tenant_id = 8009 AND report_key = 'report-ack-concurrent'
      AND acknowledged_by = 9201 AND deleted = 0
  ) THEN
    RAISE EXCEPTION 'expected report acknowledgement uniqueness to be tenant scoped and soft-delete aware';
  END IF;
END $$;

SELECT 'report acknowledgement tenant and soft-delete lifecycle smoke passed' AS result;
`;
}

export function summarizeConcurrentDuplicateResults(results) {
  const successCount = results.filter((result) => result.status === 0).length;
  const duplicateCount = results.filter((result) =>
    `${result.stdout ?? ''}\n${result.stderr ?? ''}`.includes('duplicate key value violates unique constraint'),
  ).length;
  if (successCount !== 1 || duplicateCount !== 1) {
    throw new Error(
      [
        'expected exactly one concurrent duplicate identity insert to succeed and one to hit the unique constraint',
        ...results.map((result, index) =>
          `process ${index + 1}: status=${result.status} stdout=${JSON.stringify(result.stdout)} stderr=${JSON.stringify(result.stderr)}`,
        ),
      ].join('\n'),
    );
  }
  return 'concurrent duplicate ingest identity smoke passed';
}

export function summarizeConcurrentReviewStatusResults(results) {
  const updateOneCount = results.filter((result) =>
    result.status === 0 && /\bUPDATE 1\b/.test(`${result.stdout ?? ''}\n${result.stderr ?? ''}`),
  ).length;
  const updateZeroCount = results.filter((result) =>
    result.status === 0 && /\bUPDATE 0\b/.test(`${result.stdout ?? ''}\n${result.stderr ?? ''}`),
  ).length;
  if (updateOneCount !== 1 || updateZeroCount !== 1) {
    throw new Error(
      [
        'expected exactly one concurrent review status update to win the version race and one stale update to affect zero rows',
        ...results.map((result, index) =>
          `process ${index + 1}: status=${result.status} stdout=${JSON.stringify(result.stdout)} stderr=${JSON.stringify(result.stderr)}`,
        ),
      ].join('\n'),
    );
  }
  return 'concurrent review status version smoke passed';
}

export function summarizeConcurrentReviewSegmentResults(results) {
  const successCount = results.filter((result) => result.status === 0).length;
  const exclusionCount = results.filter((result) =>
    `${result.stdout ?? ''}\n${result.stderr ?? ''}`.includes('violates exclusion constraint'),
  ).length;
  if (successCount !== 1 || exclusionCount !== 1) {
    throw new Error(
      [
        'expected exactly one concurrent ReviewSegment insert to succeed and one to hit the exclusion constraint',
        ...results.map((result, index) =>
          `process ${index + 1}: status=${result.status} stdout=${JSON.stringify(result.stdout)} stderr=${JSON.stringify(result.stderr)}`,
        ),
      ].join('\n'),
    );
  }
  return 'concurrent ReviewSegment overlap smoke passed';
}

export function summarizeConcurrentNoopResults(results, label) {
  const insertedCount = results.filter((result) =>
    result.status === 0 && /\bINSERT 0 1\b/.test(`${result.stdout ?? ''}\n${result.stderr ?? ''}`),
  ).length;
  const noopCount = results.filter((result) =>
    result.status === 0 && /\bINSERT 0 0\b/.test(`${result.stdout ?? ''}\n${result.stderr ?? ''}`),
  ).length;
  if (insertedCount !== 1 || noopCount !== 1) {
    throw new Error(
      [
        `expected exactly one concurrent ${label} insert and one atomic no-op`,
        ...results.map((result, index) =>
          `process ${index + 1}: status=${result.status} stdout=${JSON.stringify(result.stdout)} stderr=${JSON.stringify(result.stderr)}`,
        ),
      ].join('\n'),
    );
  }
  return `concurrent ${label} first-writer-wins smoke passed`;
}

function printHelp() {
  console.log(`Usage: node .scripts/alert-review-postgres-migration-smoke.mjs (--container=NAME | --database-url=URL) [--database=NAME] [--repo-root=PATH] [--keep-database]

Runs FR-01/FR-20/FR-24/FR-30/FR-33/FR-35 alert review PostgreSQL migration smoke against an existing Docker PostgreSQL container or a direct PostgreSQL URL.
The target container must accept: docker exec -i NAME psql -U postgres -d DATABASE.
The direct URL must be a maintenance database URL accepted by local psql; the smoke creates --database on the same server.
The smoke creates a temporary database, applies V20260701 through V20260713_5 in MIGRATION_FILES order, and verifies platform SMALLINT deleted semantics, BaseDO tenant columns and indexes, the historical BOOLEAN upgrade path, ingest identity, ReviewSegment constraints, status transitions, ended segment end-time guard, alert segment severity guard, same-camera merge index shape, ReviewData backfill, immutable media permission seeds plus the forward media-manage permission, semantic-trigger evaluation/confirmation permission seeds and tenant-scoped terminal indexes, semantic-index generation/worker claims, paused Quartz seeds for local scheduler ownership, report acknowledgement DDL, atomic operations report outbox idempotency, runtime outbox notify templates and unbounded structured parameters, runtime outbox recipient delivery idempotency, runtime outbox claim columns, persistent evidence export queue claims/idempotency, item media audit lookup, and concurrent races.`);
}

function assertSafeDatabaseName(database) {
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(database)) {
    throw new Error(`Unsafe database name: ${database}`);
  }
}

export function databaseUrlForDatabase(databaseUrl, database) {
  const url = new URL(databaseUrl);
  url.pathname = `/${database}`;
  return url.toString();
}

function databaseNameFromUrl(databaseUrl) {
  const url = new URL(databaseUrl);
  const database = decodeURIComponent(url.pathname.replace(/^\/+/, ''));
  return database || 'postgres';
}

export function buildPsqlInvocation(options, database) {
  if (options.container) {
    return {
      command: 'docker',
      args: ['exec', '-i', options.container, 'psql', '-U', 'postgres', '-d', database, '-v', 'ON_ERROR_STOP=1'],
      label: `${options.container}/${database}`,
    };
  }
  if (options.databaseUrl) {
    const env = psqlEnvForDatabase(options.databaseUrl, database);
    return {
      command: 'psql',
      args: ['-v', 'ON_ERROR_STOP=1'],
      env,
      label: `psql/${database}`,
    };
  }
  throw new Error('Missing required --container=NAME or --database-url=URL');
}

function psqlEnvForDatabase(databaseUrl, database) {
  const url = new URL(databaseUrl);
  const env = {
    PGDATABASE: database,
  };
  if (url.hostname) {
    env.PGHOST = url.hostname;
  }
  if (url.password) {
    env.PGPASSWORD = decodeURIComponent(url.password);
  }
  if (url.port) {
    env.PGPORT = url.port;
  }
  if (url.searchParams.has('sslmode')) {
    env.PGSSLMODE = url.searchParams.get('sslmode');
  }
  if (url.username) {
    env.PGUSER = decodeURIComponent(url.username);
  }
  return env;
}

function runPsql(options, database, sql) {
  const invocation = buildPsqlInvocation(options, database);
  const result = spawnSync(invocation.command, invocation.args, {
    input: sql,
    encoding: 'utf8',
    env: invocation.env ? { ...process.env, ...invocation.env } : process.env,
    windowsHide: true,
    maxBuffer: 16 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(
      [
        `psql failed in ${invocation.label} with exit ${result.status}`,
        result.stdout,
        result.stderr,
      ]
        .filter(Boolean)
        .join('\n'),
    );
  }
  return result.stdout;
}

function runDockerCommand(args) {
  const result = spawnSync('docker', args, {
    encoding: 'utf8',
    windowsHide: true,
    maxBuffer: 16 * 1024 * 1024,
  });
  if (result.status !== 0) {
    throw new Error(
      [`docker ${args.join(' ')} failed with exit ${result.status}`, result.stdout, result.stderr]
        .filter(Boolean)
        .join('\n'),
    );
  }
  return result.stdout;
}

function runPsqlAsync(options, database, sql) {
  return new Promise((resolveResult) => {
    const invocation = buildPsqlInvocation(options, database);
    const child = spawn(invocation.command, invocation.args, {
      env: invocation.env ? { ...process.env, ...invocation.env } : process.env,
      windowsHide: true,
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => {
      stdout += chunk.toString();
    });
    child.stderr.on('data', (chunk) => {
      stderr += chunk.toString();
    });
    child.on('error', (error) => {
      resolveResult({ status: -1, stdout, stderr: `${stderr}\n${error.message}` });
    });
    child.on('close', (status) => {
      resolveResult({ status, stdout, stderr });
    });
    child.stdin.end(sql);
  });
}

function readMigrationSql(repoRoot, migrationFile) {
  const path = resolve(repoRoot, migrationFile);
  if (!existsSync(path)) {
    throw new Error(`Migration file not found: ${path}`);
  }
  return readFileSync(path, 'utf8');
}

export async function runSmoke(options) {
  if (Boolean(options.container) === Boolean(options.databaseUrl)) {
    throw new Error('Provide exactly one of --container=NAME or --database-url=URL');
  }
  assertSafeDatabaseName(options.database);
  const adminDatabase = options.container ? 'postgres' : databaseNameFromUrl(options.databaseUrl);

  if (options.container) {
    runDockerCommand(['exec', options.container, 'pg_isready', '-U', 'postgres']);
  }
  runPsql(
    options,
    adminDatabase,
    `DROP DATABASE IF EXISTS ${options.database};\nCREATE DATABASE ${options.database};\n`,
  );

  try {
    runPsql(options, options.database, buildBootstrapSql());
    for (const [index, migrationFile] of MIGRATION_FILES.entries()) {
      if (migrationFile.endsWith('V20260708_10__alert_review_deleted_smallint.sql')) {
        runPsql(options, options.database, buildLegacyBooleanDeletedFixtureSql());
      }
      if (migrationFile.endsWith('V20260713_4__alert_review_local_scheduler_ownership.sql')) {
        runPsql(options, options.database, buildLegacyOperationsReportDuplicateFixtureSql());
      }
      runPsql(
        options,
        options.database,
        `BEGIN;\n${readMigrationSql(options.repoRoot, migrationFile)}\nCOMMIT;\n`,
      );
      if (index === 0) {
        runPsql(options, options.database, buildLegacyReviewFixtureSql());
      }
    }
    const assertionOutput = runPsql(options, options.database, buildPostMigrationAssertionSql());
    const semanticIndexQueueCasOutput = runPsql(
      options,
      options.database,
      buildSemanticIndexQueueCasSmokeSql(),
    );
    const concurrentOutput = await runConcurrentDuplicateIdentitySmoke(options);
    runPsql(options, options.database, buildConcurrentReviewStatusBootstrapSql());
    const concurrentReviewStatusOutput = await runConcurrentReviewStatusSmoke(options);
    runPsql(options, options.database, buildConcurrentReviewSegmentBootstrapSql());
    const concurrentSegmentOutput = await runConcurrentReviewSegmentSmoke(options);
    const concurrentReportOutput = await runConcurrentOperationsReportSmoke(options);
    const concurrentReportAckOutput = await runConcurrentReportAcknowledgementSmoke(options);
    const reportAckLifecycleOutput = runPsql(
      options,
      options.database,
      buildReportAcknowledgementLifecycleSmokeSql(),
    );
    return `${assertionOutput}${semanticIndexQueueCasOutput}${concurrentOutput}\n${concurrentReviewStatusOutput}\n${concurrentSegmentOutput}\n${concurrentReportOutput}\n${concurrentReportAckOutput}\n${reportAckLifecycleOutput}\n`;
  } finally {
    if (!options.keepDatabase) {
      runPsql(options, adminDatabase, `DROP DATABASE IF EXISTS ${options.database};\n`);
    }
  }
}

async function runConcurrentDuplicateIdentitySmoke(options) {
  const results = await Promise.all([
    runPsqlAsync(options, options.database, buildConcurrentDuplicateIdentityInsertSql()),
    runPsqlAsync(options, options.database, buildConcurrentDuplicateIdentityInsertSql()),
  ]);
  return summarizeConcurrentDuplicateResults(results);
}

async function runConcurrentReviewStatusSmoke(options) {
  const results = await Promise.all([
    runPsqlAsync(options, options.database, buildConcurrentReviewStatusUpdateSql()),
    runPsqlAsync(options, options.database, buildConcurrentReviewStatusUpdateSql()),
  ]);
  return summarizeConcurrentReviewStatusResults(results);
}

async function runConcurrentReviewSegmentSmoke(options) {
  const results = await Promise.all([
    runPsqlAsync(
      options,
      options.database,
      buildConcurrentReviewSegmentInsertSql({ reviewItemId: 7001, segmentNo: 'seg-race-1' }),
    ),
    runPsqlAsync(
      options,
      options.database,
      buildConcurrentReviewSegmentInsertSql({ reviewItemId: 7002, segmentNo: 'seg-race-2' }),
    ),
  ]);
  return summarizeConcurrentReviewSegmentResults(results);
}

async function runConcurrentOperationsReportSmoke(options) {
  const results = await Promise.all([
    runPsqlAsync(options, options.database, buildConcurrentOperationsReportInsertSql('report-worker-a')),
    runPsqlAsync(options, options.database, buildConcurrentOperationsReportInsertSql('report-worker-b')),
  ]);
  return summarizeConcurrentNoopResults(results, 'operations report outbox');
}

async function runConcurrentReportAcknowledgementSmoke(options) {
  const results = await Promise.all([
    runPsqlAsync(
      options,
      options.database,
      buildConcurrentReportAcknowledgementInsertSql({ userId: 9101, note: 'first contender' }),
    ),
    runPsqlAsync(
      options,
      options.database,
      buildConcurrentReportAcknowledgementInsertSql({ userId: 9102, note: 'second contender' }),
    ),
  ]);
  return summarizeConcurrentNoopResults(results, 'report acknowledgement');
}

async function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const output = await runSmoke(options);
  process.stdout.write(output);
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
  });
}
