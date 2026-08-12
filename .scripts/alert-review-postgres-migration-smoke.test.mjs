import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

import * as postgresSmoke from './alert-review-postgres-migration-smoke.mjs';

import {
  MIGRATION_FILES,
  buildBootstrapSql,
  buildLegacyReviewFixtureSql,
  buildConcurrentDuplicateIdentityInsertSql,
  buildConcurrentReviewStatusBootstrapSql,
  buildConcurrentReviewStatusUpdateSql,
  buildConcurrentReviewSegmentBootstrapSql,
  buildConcurrentReviewSegmentInsertSql,
  buildConcurrentOperationsReportInsertSql,
  buildConcurrentReportAcknowledgementInsertSql,
  buildReportAcknowledgementLifecycleSmokeSql,
  buildPsqlInvocation,
  databaseUrlForDatabase,
  buildPostMigrationAssertionSql,
  parseArgs,
  summarizeConcurrentDuplicateResults,
  summarizeConcurrentReviewStatusResults,
  summarizeConcurrentReviewSegmentResults,
  summarizeConcurrentNoopResults,
} from './alert-review-postgres-migration-smoke.mjs';
import {
  evaluateStatus,
  releaseEntriesForTrackedPaths,
} from './verify-alert-review-release-package.mjs';

const tenantScopedBaseDoTables = [
  'system_supervision_event',
  'system_supervision_task',
  'system_supervision_alert_review_item',
  'system_supervision_alert_review_ingest_identity',
  'system_supervision_alert_review_segment',
  'system_supervision_alert_review_user_status',
  'system_supervision_alert_review_evidence',
  'system_supervision_alert_review_rule',
  'system_supervision_alert_review_case',
  'system_supervision_alert_review_case_item',
  'system_supervision_alert_review_case_audit',
  'system_supervision_alert_review_semantic_index',
  'system_supervision_alert_review_export_job',
  'system_supervision_alert_review_runtime_lock',
  'system_supervision_alert_review_runtime_run',
  'system_supervision_alert_review_runtime_outbox',
  'system_supervision_alert_review_runtime_outbox_delivery',
  'system_supervision_alert_review_report_ack',
];
const baselineSql = readFileSync(MIGRATION_FILES[0], 'utf8');
for (const tableName of tenantScopedBaseDoTables) {
  const tableBody = baselineSql.match(
    new RegExp(`CREATE TABLE IF NOT EXISTS ${tableName} \\(([\\s\\S]*?)\\n\\);`),
  )?.[1];
  assert.ok(tableBody, `${tableName} must be declared by the baseline migration`);
  assert.match(
    tableBody,
    /tenant_id BIGINT NOT NULL DEFAULT 0/,
    `${tableName} must expose the tenant_id column injected by TenantLineInterceptor`,
  );
}

const currentMigrationBundleSql = MIGRATION_FILES
  .map((file) => readFileSync(file, 'utf8'))
  .join('\n');
assert.doesNotMatch(currentMigrationBundleSql, /\bdeleted\s+BOOLEAN\b/i);
assert.doesNotMatch(currentMigrationBundleSql, /\bdeleted\s*=\s*(?:FALSE|TRUE)\b/i);

assert.deepEqual(MIGRATION_FILES, [
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
]);

assert.equal(
  typeof postgresSmoke.buildLegacyBooleanDeletedFixtureSql,
  'function',
  'the real PostgreSQL smoke must exercise the historical BOOLEAN deleted upgrade path',
);
const legacyBooleanDeletedFixtureSql = postgresSmoke.buildLegacyBooleanDeletedFixtureSql();
assert.match(legacyBooleanDeletedFixtureSql, /DROP COLUMN tenant_id/);
assert.match(legacyBooleanDeletedFixtureSql, /ALTER COLUMN deleted TYPE BOOLEAN/);
assert.match(legacyBooleanDeletedFixtureSql, /WHERE deleted = FALSE/);
assert.match(legacyBooleanDeletedFixtureSql, /'pending', TRUE/);

const legacyOperationsReportDuplicateFixtureSql =
  postgresSmoke.buildLegacyOperationsReportDuplicateFixtureSql();
assert.match(legacyOperationsReportDuplicateFixtureSql, /report-duplicate-first/);
assert.match(legacyOperationsReportDuplicateFixtureSql, /report-duplicate-second/);
assert.match(legacyOperationsReportDuplicateFixtureSql, /'runtime_alert'/);
assert.match(legacyOperationsReportDuplicateFixtureSql, /, 1\);/);
assert.match(legacyOperationsReportDuplicateFixtureSql, /99611/);
assert.match(legacyOperationsReportDuplicateFixtureSql, /99612/);

const platformCompatibilityMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('deleted_smallint')),
  'utf8',
);
for (const tableName of tenantScopedBaseDoTables) {
  assert.match(platformCompatibilityMigrationSql, new RegExp(`'${tableName}'`));
}
assert.match(platformCompatibilityMigrationSql, /SET tenant_id = 0/);

const schedulerJobMigrationSql = readFileSync(MIGRATION_FILES.find((file) => file.includes('scheduler_jobs')), 'utf8');
assert.doesNotMatch(schedulerJobMigrationSql, /existing\.id\s*=\s*seed\.id/);
assert.match(schedulerJobMigrationSql, /WHERE existing\.handler_name = seed\.handler_name/);
assert.match(schedulerJobMigrationSql, /existing\.handler_param IS NOT DISTINCT FROM seed\.handler_param/);
assert.match(schedulerJobMigrationSql, /supervisionAlertReviewEvidenceExportWorkerJob/);
assert.match(schedulerJobMigrationSql, /supervisionAlertReviewOperationsReportJob/);
assert.match(schedulerJobMigrationSql, /'shift'/);
assert.match(schedulerJobMigrationSql, /'daily'/);
const schedulerJobNames = [...schedulerJobMigrationSql.matchAll(/\('([^']+)', 'supervisionAlertReview/g)]
  .map((match) => match[1]);
assert.ok(schedulerJobNames.every((name) => name.length <= 32), 'infra_job.name seeds must fit VARCHAR(32)');

const schedulerActivationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('scheduler_activation')),
  'utf8',
);
assert.match(schedulerActivationSql, /UPDATE infra_job/);
assert.match(schedulerActivationSql, /SET status = 1/);
assert.match(schedulerActivationSql, /supervisionAlertReviewRuntimePatrolJob/);
assert.match(schedulerActivationSql, /supervisionAlertReviewRuntimeOutboxJob/);
assert.match(schedulerActivationSql, /supervisionAlertReviewEventReconcileJob/);
assert.match(schedulerActivationSql, /supervisionAlertReviewEvidenceExportWorkerJob/);
assert.match(schedulerActivationSql, /supervisionAlertReviewSemanticIndexJob/);
assert.match(schedulerActivationSql, /supervisionAlertReviewOperationsReportJob/);

const localSchedulerOwnershipSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('local_scheduler_ownership')),
  'utf8',
);
assert.match(localSchedulerOwnershipSql, /SET status = 2/);
assert.match(localSchedulerOwnershipSql, /LOCK TABLE infra_job/);
assert.match(localSchedulerOwnershipSql, /system_supervision_alert_review_runtime_outbox_delivery/);
assert.match(localSchedulerOwnershipSql, /uk_supervision_alert_review_runtime_outbox_report/);
assert.match(localSchedulerOwnershipSql, /tenant_id, event_type, alert_key/);
assert.match(localSchedulerOwnershipSql, /review_operations_report/);

const exportQueueMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('export_queue')),
  'utf8',
);
assert.match(exportQueueMigrationSql, /request_key VARCHAR\(128\)/);
assert.match(exportQueueMigrationSql, /claim_token VARCHAR\(128\)/);
assert.match(exportQueueMigrationSql, /next_retry_at TIMESTAMP/);
assert.match(exportQueueMigrationSql, /uk_supervision_alert_review_export_request/);
assert.match(exportQueueMigrationSql, /idx_supervision_alert_review_export_claim/);

const mediaManageMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('media_manage_permission')),
  'utf8',
);
assert.match(mediaManageMigrationSql, /system_menu/);
assert.match(mediaManageMigrationSql, /system:supervision-alert-review:media:manage/);
assert.doesNotMatch(
  readFileSync(MIGRATION_FILES.find((file) => file.includes('media_permissions')), 'utf8'),
  /system:supervision-alert-review:media:manage/,
);

const semanticTriggerConfirmationMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('semantic_trigger_confirmation')),
  'utf8',
);
assert.match(semanticTriggerConfirmationMigrationSql, /uk_alert_review_semantic_trigger_evaluation/);
assert.match(semanticTriggerConfirmationMigrationSql, /uk_alert_review_semantic_trigger_terminal/);
assert.match(semanticTriggerConfirmationMigrationSql, /'语义触发评估'/);
assert.match(semanticTriggerConfirmationMigrationSql, /'语义触发确认'/);
assert.doesNotMatch(semanticTriggerConfirmationMigrationSql, /璇|纭/);
assert.match(semanticTriggerConfirmationMigrationSql, /tenant_id/);
assert.match(semanticTriggerConfirmationMigrationSql, /semantic_trigger_evaluated/);
assert.match(semanticTriggerConfirmationMigrationSql, /semantic_trigger_confirmed/);
assert.match(semanticTriggerConfirmationMigrationSql, /semantic_trigger_rejected/);
assert.match(semanticTriggerConfirmationMigrationSql, /semantic-trigger-evaluation-v1/);
assert.match(
  semanticTriggerConfirmationMigrationSql,
  /system:supervision-alert-review:semantic-trigger:evaluate/,
);
assert.match(
  semanticTriggerConfirmationMigrationSql,
  /system:supervision-alert-review:semantic-trigger:confirm/,
);

const semanticIndexClaimMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('semantic_index_claim')),
  'utf8',
);
assert.match(semanticIndexClaimMigrationSql, /index_generation_id VARCHAR\(128\)/);

const recordStartMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('evidence_record_start')),
  'utf8',
);
assert.match(recordStartMigrationSql, /record_start_time TIMESTAMP/);

const eventCreatePermissionMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('supervision_event_create_permission')),
  'utf8',
);
assert.match(eventCreatePermissionMigrationSql, /system_menu/);
assert.match(eventCreatePermissionMigrationSql, /supervision:event:create/);
assert.match(eventCreatePermissionMigrationSql, /监管事件创建/);
assert.match(semanticIndexClaimMigrationSql, /claim_token VARCHAR\(128\)/);
assert.match(semanticIndexClaimMigrationSql, /claim_expires_at TIMESTAMP/);
assert.match(semanticIndexClaimMigrationSql, /next_retry_at TIMESTAMP/);
assert.match(semanticIndexClaimMigrationSql, /idx_alert_review_semantic_claim/);

assert.equal(
  typeof postgresSmoke.buildSemanticIndexQueueCasSmokeSql,
  'function',
  'the real PostgreSQL smoke must verify semantic reindex CAS claim preservation',
);
const semanticIndexQueueCasSmokeSql = postgresSmoke.buildSemanticIndexQueueCasSmokeSql();
assert.match(semanticIndexQueueCasSmokeSql, /ON CONFLICT DO NOTHING/);
assert.match(semanticIndexQueueCasSmokeSql, /index_status <> 'processing'/);
assert.match(semanticIndexQueueCasSmokeSql, /claim_expires_at <= TIMESTAMP/);
assert.match(semanticIndexQueueCasSmokeSql, /expected active semantic index claim to survive reindex queue/);
assert.match(semanticIndexQueueCasSmokeSql, /expected expired semantic index claim to be requeued/);
assert.doesNotMatch(semanticIndexQueueCasSmokeSql, /&lt;/);

const reportAckMigrationSql = readFileSync(MIGRATION_FILES.find((file) => file.includes('report_ack')), 'utf8');
assert.match(reportAckMigrationSql, /system_supervision_alert_review_report_ack/);
assert.match(reportAckMigrationSql, /report_key VARCHAR\(128\) NOT NULL/);
assert.match(reportAckMigrationSql, /acknowledgement_status VARCHAR\(32\) NOT NULL/);
assert.match(reportAckMigrationSql, /uk_supervision_alert_review_report_ack_key/);

const runtimeOutboxNotifyTemplateMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('runtime_outbox_notify_templates')),
  'utf8',
);
assert.match(runtimeOutboxNotifyTemplateMigrationSql, /system_notify_template/);
assert.match(runtimeOutboxNotifyTemplateMigrationSql, /YFEIEYE_REVIEW_RUNTIME_ALERT/);
assert.match(runtimeOutboxNotifyTemplateMigrationSql, /YFEIEYE_REVIEW_OPERATIONS_REPORT/);
assert.match(runtimeOutboxNotifyTemplateMigrationSql, /existing\.code = seed\.code/);

const runtimeOutboxNotifyParamsMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('notify_message_params_text')),
  'utf8',
);
assert.match(runtimeOutboxNotifyParamsMigrationSql, /ALTER TABLE system_notify_message/);
assert.match(runtimeOutboxNotifyParamsMigrationSql, /ALTER COLUMN template_params TYPE TEXT/);

const runtimeOutboxDeliveryMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('runtime_outbox_delivery')),
  'utf8',
);
assert.match(runtimeOutboxDeliveryMigrationSql, /system_supervision_alert_review_runtime_outbox_delivery/);
assert.match(runtimeOutboxDeliveryMigrationSql, /recipient_user_id BIGINT NOT NULL/);
assert.match(runtimeOutboxDeliveryMigrationSql, /notify_message_id BIGINT/);
assert.match(runtimeOutboxDeliveryMigrationSql, /uk_supervision_alert_review_runtime_outbox_delivery_recipient/);
assert.match(runtimeOutboxDeliveryMigrationSql, /FOREIGN KEY \(outbox_id\)/);

const runtimeOutboxClaimMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('runtime_outbox_claim')),
  'utf8',
);
assert.match(runtimeOutboxClaimMigrationSql, /claim_token VARCHAR\(128\)/);
assert.match(runtimeOutboxClaimMigrationSql, /claimed_by BIGINT/);
assert.match(runtimeOutboxClaimMigrationSql, /claimed_at TIMESTAMP/);
assert.match(runtimeOutboxClaimMigrationSql, /idx_supervision_alert_review_runtime_outbox_claim/);

const segmentEndTimeGuardMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('segment_end_time_guard')),
  'utf8',
);
assert.match(segmentEndTimeGuardMigrationSql, /ck_supervision_alert_review_segment_ended_time/);
assert.match(segmentEndTimeGuardMigrationSql, /segment_status <> 'ended' OR end_time IS NOT NULL/);
assert.match(segmentEndTimeGuardMigrationSql, /SET end_time = start_time/);

const segmentAlertSeverityGuardMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('segment_alert_severity_guard')),
  'utf8',
);
assert.match(segmentAlertSeverityGuardMigrationSql, /ck_supervision_alert_review_segment_alert_severity/);
assert.match(segmentAlertSeverityGuardMigrationSql, /segment_status <> 'alert' OR severity = 'alert'/);
assert.match(segmentAlertSeverityGuardMigrationSql, /SET severity = 'alert'/);

const mergeIndexSameCameraMigrationSql = readFileSync(
  MIGRATION_FILES.find((file) => file.includes('merge_index_same_camera')),
  'utf8',
);
assert.match(mergeIndexSameCameraMigrationSql, /DROP INDEX IF EXISTS idx_supervision_alert_review_merge/);
assert.match(mergeIndexSameCameraMigrationSql, /tenant_id, source_system, camera_id, review_status, last_alert_time/);
assert.doesNotMatch(mergeIndexSameCameraMigrationSql, /zone_code, rule_code/);

const bootstrapSql = buildBootstrapSql();
assert.match(bootstrapSql, /CREATE SEQUENCE system_menu_seq/);
assert.match(bootstrapSql, /CREATE TABLE system_menu/);
assert.match(bootstrapSql, /CREATE TABLE infra_job/);
assert.match(bootstrapSql, /CREATE TABLE system_notify_template/);
assert.doesNotMatch(bootstrapSql, /CREATE TABLE system_supervision_alert_review_runtime_outbox/);
assert.doesNotMatch(bootstrapSql, /CREATE TABLE system_supervision_alert_review_item/);
assert.doesNotMatch(bootstrapSql, /CREATE TABLE system_supervision_alert_review_segment/);

function assertReviewItemFixtureNumbers(sql, label) {
  const inserts = [...sql.matchAll(
    /INSERT INTO system_supervision_alert_review_item\s*\(\s*([\s\S]*?)\s*\)\s*VALUES/g,
  )];
  assert.ok(inserts.length > 0, `${label} must contain a review item fixture`);
  for (const insert of inserts) {
    for (const requiredColumn of [
      'review_item_no',
      'source_system',
      'rule_code',
      'first_alert_time',
      'last_alert_time',
      'source_alert_ids',
    ]) {
      assert.match(
        insert[1],
        new RegExp(`\\b${requiredColumn}\\b`),
        `${label} review item fixtures must set ${requiredColumn}`,
      );
    }
  }
}

const legacyReviewFixtureSql = buildLegacyReviewFixtureSql();
assert.match(legacyReviewFixtureSql, /source_alert_ids/);
assert.match(legacyReviewFixtureSql, /a-shared/);
assert.match(legacyReviewFixtureSql, /legacy-correlation/);
assert.match(legacyReviewFixtureSql, /seg-tenant-1001/);
assertReviewItemFixtureNumbers(legacyReviewFixtureSql, 'legacy migration');

const assertionSql = buildPostMigrationAssertionSql();
assert.match(assertionSql, /system_supervision_alert_review_ingest_identity/);
assert.match(assertionSql, /expected tenant-scoped ingest identity backfill/);
assert.match(assertionSql, /unique_violation/);
assert.match(assertionSql, /exclusion_violation/);
assert.match(assertionSql, /expected open active ReviewSegment to block later same-camera segment/);
assert.match(assertionSql, /expected adjacent same-camera ReviewSegment boundary to be allowed/);
assert.match(assertionSql, /expected duplicate active ReviewSegment review_item_id to be rejected/);
assert.match(assertionSql, /'seg-transition-ended', 1001, 'camera-transition-ended-01'/);
assert.match(assertionSql, /expected deleted duplicate ReviewSegment review_item_id to be allowed/);
assert.match(assertionSql, /expected ReviewData backfill to normalize legacy rows/);
assert.match(assertionSql, /reviewDataVersion/);
assert.match(assertionSql, /reviewSegment/);
assert.match(assertionSql, /system:supervision-alert-review:media:playback/);
assert.match(assertionSql, /expected review media permission seeds to be present/);
assert.match(assertionSql, /system:supervision-alert-review:media:manage/);
assert.match(assertionSql, /<> 6/);
assert.match(assertionSql, /supervision:event:create/);
assert.match(assertionSql, /expected supervision event create permission seed to be present/);
assert.match(assertionSql, /system:supervision-alert-review:semantic-trigger:evaluate/);
assert.match(assertionSql, /system:supervision-alert-review:semantic-trigger:confirm/);
assert.match(assertionSql, /uk_alert_review_semantic_trigger_evaluation/);
assert.match(assertionSql, /uk_alert_review_semantic_trigger_terminal/);
assert.match(assertionSql, /expected semantic trigger permission seeds to be present/);
assert.match(assertionSql, /expected semantic trigger confirmation indexes to be tenant scoped/);
assert.match(assertionSql, /expected local-owned alert review Quartz jobs to be paused/);
assert.match(assertionSql, /supervisionAlertReviewEventReconcileJob/);
assert.match(assertionSql, /supervisionAlertReviewEvidenceExportWorkerJob/);
assert.match(assertionSql, /supervisionAlertReviewOperationsReportJob/);
assert.match(assertionSql, /YFEIEYE_REVIEW_RUNTIME_ALERT/);
assert.match(assertionSql, /YFEIEYE_REVIEW_OPERATIONS_REPORT/);
assert.match(assertionSql, /expected runtime outbox notify templates to be seeded/);
assert.match(assertionSql, /system_supervision_alert_review_runtime_outbox_delivery/);
assert.match(assertionSql, /expected runtime outbox delivery recipient idempotency index to exist/);
assert.match(assertionSql, /expected runtime outbox claim columns to exist/);
assert.match(assertionSql, /expected runtime outbox claim index to exist/);
assert.match(assertionSql, /expected operations report outbox idempotency index to exist/);
assert.match(assertionSql, /index_info\.indisunique/);
assert.match(assertionSql, /expected operations report migration to preserve the earliest active duplicate/);
assert.match(assertionSql, /expected operations report dedupe to retire duplicate recipient deliveries/);
assert.match(assertionSql, /expected operations report dedupe to preserve other tenants, event types, and deleted history/);
assert.match(assertionSql, /expected review case audit to allow pre-case media audit rows/);
assert.match(assertionSql, /idx_supervision_alert_review_case_audit_item/);
assert.match(assertionSql, /expected stale review status version update to affect no rows/);
assert.match(assertionSql, /expected repeated same-status reviewer update to be idempotent/);
assert.match(assertionSql, /expected ended ReviewSegment reopen to be rejected/);
assert.match(assertionSql, /expected alert ReviewSegment downgrade to detection to be rejected/);
assert.match(assertionSql, /expected ended ReviewSegment without end_time to be rejected/);
assert.match(assertionSql, /expected alert ReviewSegment with detection severity to be rejected/);
assert.match(assertionSql, /tr_supervision_alert_review_segment_status_transition/);
assertReviewItemFixtureNumbers(assertionSql, 'post-migration assertions');

const concurrentInsertSql = buildConcurrentDuplicateIdentityInsertSql();
assert.match(concurrentInsertSql, /video:alert:a-race/);
assert.match(concurrentInsertSql, /system_supervision_alert_review_ingest_identity/);

const concurrentReviewStatusBootstrapSql = buildConcurrentReviewStatusBootstrapSql();
assert.match(concurrentReviewStatusBootstrapSql, /review-status-race/);
assert.match(concurrentReviewStatusBootstrapSql, /pending_review/);
assert.match(concurrentReviewStatusBootstrapSql, /8001/);
assertReviewItemFixtureNumbers(concurrentReviewStatusBootstrapSql, 'concurrent review status bootstrap');

const concurrentReviewStatusUpdateSql = buildConcurrentReviewStatusUpdateSql();
assert.match(concurrentReviewStatusUpdateSql, /UPDATE system_supervision_alert_review_item/);
assert.match(concurrentReviewStatusUpdateSql, /review_status = 'reviewed'/);
assert.match(concurrentReviewStatusUpdateSql, /version = version \+ 1/);
assert.match(concurrentReviewStatusUpdateSql, /id = 8001/);
assert.match(concurrentReviewStatusUpdateSql, /version = 0/);

const concurrentSegmentBootstrapSql = buildConcurrentReviewSegmentBootstrapSql();
assert.match(concurrentSegmentBootstrapSql, /a-segment-race-1/);
assert.match(concurrentSegmentBootstrapSql, /a-segment-race-2/);
assert.match(concurrentSegmentBootstrapSql, /camera-segment-race-01/);
assertReviewItemFixtureNumbers(concurrentSegmentBootstrapSql, 'concurrent segment bootstrap');

const concurrentSegmentInsertSql = buildConcurrentReviewSegmentInsertSql({
  reviewItemId: 7001,
  segmentNo: 'seg-race-1',
});
assert.match(concurrentSegmentInsertSql, /review_item_id, segment_no, tenant_id, camera_id/);
assert.match(concurrentSegmentInsertSql, /7001, 'seg-race-1'/);
assert.match(concurrentSegmentInsertSql, /camera-segment-race-01/);
assert.match(concurrentSegmentInsertSql, /NULL, 0/);

const concurrentOperationsReportInsertSql = buildConcurrentOperationsReportInsertSql('report-worker-a');
assert.match(concurrentOperationsReportInsertSql, /review_operations_report/);
assert.match(concurrentOperationsReportInsertSql, /ON CONFLICT \(tenant_id, event_type, alert_key\)/);
assert.match(concurrentOperationsReportInsertSql, /DO NOTHING/);

const concurrentReportAcknowledgementInsertSql = buildConcurrentReportAcknowledgementInsertSql({
  userId: 9101,
  note: 'first contender',
});
assert.match(concurrentReportAcknowledgementInsertSql, /report-ack-concurrent/);
assert.match(concurrentReportAcknowledgementInsertSql, /ON CONFLICT \(tenant_id, report_key\) WHERE deleted = 0/);
assert.match(concurrentReportAcknowledgementInsertSql, /DO NOTHING/);

const reportAcknowledgementLifecycleSmokeSql = buildReportAcknowledgementLifecycleSmokeSql();
assert.match(reportAcknowledgementLifecycleSmokeSql, /tenant_id = 8008/);
assert.match(reportAcknowledgementLifecycleSmokeSql, /tenant_id = 8009/);
assert.match(reportAcknowledgementLifecycleSmokeSql, /replacement after soft delete/);
assert.match(
  reportAcknowledgementLifecycleSmokeSql,
  /expected report acknowledgement uniqueness to be tenant scoped and soft-delete aware/,
);

assert.equal(
  summarizeConcurrentDuplicateResults([
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
    {
      status: 3,
      stdout: '',
      stderr: 'ERROR: duplicate key value violates unique constraint "uk_supervision_alert_review_ingest_identity"',
    },
  ]),
  'concurrent duplicate ingest identity smoke passed',
);
assert.throws(
  () => summarizeConcurrentDuplicateResults([
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
  ]),
  /expected exactly one concurrent duplicate identity insert to succeed/,
);

assert.equal(
  summarizeConcurrentReviewStatusResults([
    { status: 0, stdout: 'UPDATE 1', stderr: '' },
    { status: 0, stdout: 'UPDATE 0', stderr: '' },
  ]),
  'concurrent review status version smoke passed',
);
assert.throws(
  () => summarizeConcurrentReviewStatusResults([
    { status: 0, stdout: 'UPDATE 1', stderr: '' },
    { status: 0, stdout: 'UPDATE 1', stderr: '' },
  ]),
  /expected exactly one concurrent review status update to win the version race/,
);

assert.equal(
  summarizeConcurrentReviewSegmentResults([
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
    {
      status: 3,
      stdout: '',
      stderr: 'ERROR: conflicting key value violates exclusion constraint "ex_supervision_alert_review_segment_camera_time"',
    },
  ]),
  'concurrent ReviewSegment overlap smoke passed',
);
assert.throws(
  () => summarizeConcurrentReviewSegmentResults([
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
  ]),
  /expected exactly one concurrent ReviewSegment insert to succeed/,
);

assert.equal(
  summarizeConcurrentNoopResults([
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
    { status: 0, stdout: 'INSERT 0 0', stderr: '' },
  ], 'operations report outbox'),
  'concurrent operations report outbox first-writer-wins smoke passed',
);
assert.throws(
  () => summarizeConcurrentNoopResults([
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
    { status: 0, stdout: 'INSERT 0 1', stderr: '' },
  ], 'operations report outbox'),
  /expected exactly one concurrent operations report outbox insert and one atomic no-op/,
);

assert.deepEqual(parseArgs(['--container=pg-review', '--database=yfeieye_smoke']), {
  container: 'pg-review',
  databaseUrl: null,
  database: 'yfeieye_smoke',
  repoRoot: process.cwd(),
  keepDatabase: false,
  help: false,
});
assert.deepEqual(parseArgs([
  '--database-url=postgresql://ci:secret@db.example:5432/postgres?sslmode=require',
  '--database=yfeieye_smoke',
]), {
  container: null,
  databaseUrl: 'postgresql://ci:secret@db.example:5432/postgres?sslmode=require',
  database: 'yfeieye_smoke',
  repoRoot: process.cwd(),
  keepDatabase: false,
  help: false,
});
assert.equal(
  databaseUrlForDatabase(
    'postgresql://ci:secret@db.example:5432/postgres?sslmode=require',
    'yfeieye_alert_review_smoke',
  ),
  'postgresql://ci:secret@db.example:5432/yfeieye_alert_review_smoke?sslmode=require',
);
assert.deepEqual(
  buildPsqlInvocation({
    container: null,
    databaseUrl: 'postgresql://ci:secret@db.example:5432/postgres?sslmode=require',
  }, 'yfeieye_alert_review_smoke'),
  {
    command: 'psql',
    args: ['-v', 'ON_ERROR_STOP=1'],
    env: {
      PGDATABASE: 'yfeieye_alert_review_smoke',
      PGHOST: 'db.example',
      PGPASSWORD: 'secret',
      PGPORT: '5432',
      PGSSLMODE: 'require',
      PGUSER: 'ci',
    },
    label: 'psql/yfeieye_alert_review_smoke',
  },
);
assert.equal(
  JSON.stringify(buildPsqlInvocation({
    container: null,
    databaseUrl: 'postgresql://ci:secret@db.example:5432/postgres?sslmode=require',
  }, 'yfeieye_alert_review_smoke').args).includes('secret'),
  false,
);
assert.equal(parseArgs(['--help']).help, true);
assert.throws(() => parseArgs(['--bogus']), /Unknown argument/);

const untrackedSmoke = evaluateStatus(`
?? .scripts/alert-review-postgres-migration-smoke.mjs
?? .scripts/alert-review-postgres-migration-smoke.test.mjs
`);
assert.equal(untrackedSmoke.ok, false);
assert.equal(untrackedSmoke.blockers[0].group, 'FR release gate tooling');
assert.equal(untrackedSmoke.blockers[1].group, 'FR release gate tooling');

const trackedSmokeEntries = releaseEntriesForTrackedPaths([
  '.scripts/alert-review-postgres-migration-smoke.mjs',
  '.scripts/alert-review-postgres-migration-smoke.test.mjs',
]);
assert.equal(trackedSmokeEntries.length, 2);

console.log('alert review postgres migration smoke tests OK');
