package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSemanticIndexDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSemanticIndexMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionSchemaSqlTest {

    private static final String SCHEMA_RESOURCE = "sql/migrations/V20260701__supervision_event_closure_baseline.sql";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TENANT_SCOPED_BASE_DO_TABLES = Set.of(
            "system_supervision_event",
            "system_supervision_task",
            "system_supervision_alert_review_item",
            "system_supervision_alert_review_ingest_identity",
            "system_supervision_alert_review_segment",
            "system_supervision_alert_review_user_status",
            "system_supervision_alert_review_evidence",
            "system_supervision_alert_review_rule",
            "system_supervision_alert_review_case",
            "system_supervision_alert_review_case_item",
            "system_supervision_alert_review_case_audit",
            "system_supervision_alert_review_semantic_index",
            "system_supervision_alert_review_export_job",
            "system_supervision_alert_review_runtime_lock",
            "system_supervision_alert_review_runtime_run",
            "system_supervision_alert_review_runtime_outbox",
            "system_supervision_alert_review_runtime_outbox_delivery",
            "system_supervision_alert_review_report_ack"
    );

    @Test
    void semanticReindexCasRendersPostgresOperatorsThroughMybatis() {
        Configuration configuration = new Configuration();
        configuration.addMapper(SupervisionAlertReviewSemanticIndexMapper.class);
        SupervisionAlertReviewSemanticIndexDO desired = new SupervisionAlertReviewSemanticIndexDO()
                .setReviewItemId(9001L)
                .setCameraId("camera-01")
                .setIndexGenerationId("sig-9001");
        BoundSql boundSql = configuration.getMappedStatement(
                        SupervisionAlertReviewSemanticIndexMapper.class.getName()
                                + ".queueReindexUnlessActivelyClaimed")
                .getBoundSql(Map.of(
                        "index", desired,
                        "queuedAt", LocalDateTime.of(2026, 7, 13, 10, 0)
                ));
        String renderedSql = boundSql.getSql().replaceAll("\\s+", " ").trim();

        assertFalse(renderedSql.contains("&lt;"), renderedSql);
        assertTrue(renderedSql.contains("index_status <> 'processing'"), renderedSql);
        assertTrue(renderedSql.contains("claim_expires_at <= ?"), renderedSql);
    }

    @Test
    void schemaCreatesOnlySupervisionTables() throws IOException {
        String sql = readSchemaSql();

        assertEquals(Set.of(
                "system_supervision_event",
                "system_supervision_task",
                "system_supervision_action",
                "system_supervision_evidence_item",
                "system_supervision_alert_review_item",
                "system_supervision_alert_review_ingest_identity",
                "system_supervision_alert_review_segment",
                "system_supervision_alert_review_user_status",
                "system_supervision_alert_review_evidence",
                "system_supervision_alert_review_rule",
                "system_supervision_alert_review_case",
                "system_supervision_alert_review_case_item",
                "system_supervision_alert_review_case_audit",
                "system_supervision_alert_review_semantic_index",
                "system_supervision_alert_review_export_job",
                "system_supervision_alert_review_runtime_lock",
                "system_supervision_alert_review_runtime_run",
                "system_supervision_alert_review_runtime_outbox",
                "system_supervision_alert_review_runtime_outbox_delivery",
                "system_supervision_alert_review_report_ack",
                "system_supervision_close_check_result"
        ), extractCreatedTables(sql));
    }

    @Test
    void schemaIsAdditiveOnly() throws IOException {
        String lowerSql = readSchemaSql().toLowerCase();

        assertFalse(lowerSql.contains("alter table "));
        assertFalse(lowerSql.contains("drop table "));
        assertFalse(lowerSql.contains("truncate table "));
        assertFalse(lowerSql.contains("insert into "));
        assertFalse(lowerSql.contains("update "));
        assertFalse(lowerSql.contains("delete from "));
    }

    @Test
    void schemaDefinesIdempotencyAndLookupIndexes() throws IOException {
        String sql = readSchemaSql();

        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_event_open_alert"));
        assertTrue(sql.contains("ON system_supervision_event(tenant_id, source_system, source_alert_id)"));
        assertTrue(sql.contains("event_status <> 'closed'"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_task_event_id"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_action_event_id"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_evidence_event_id"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_item_no"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_ingest_identity"));
        assertTrue(sql.contains("ON system_supervision_alert_review_ingest_identity(tenant_id, source_system, identity_key)"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_segment_no"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_segment_item"));
        assertTrue(sql.contains("ON system_supervision_alert_review_segment(tenant_id, review_item_id)"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_segment_camera_time"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_segment_status"));
        assertTrue(sql.contains("CONSTRAINT ck_supervision_alert_review_segment_time"));
        assertTrue(sql.contains("CONSTRAINT ck_supervision_alert_review_segment_ended_time"));
        assertTrue(sql.contains("CONSTRAINT ck_supervision_alert_review_segment_alert_severity"));
        assertTrue(sql.contains("CONSTRAINT ck_supervision_alert_review_segment_status"));
        assertTrue(sql.contains("CONSTRAINT ck_supervision_alert_review_segment_severity"));
        assertTrue(sql.contains("CONSTRAINT ex_supervision_alert_review_segment_camera_time"));
        assertTrue(sql.contains("camera_id WITH ="));
        assertTrue(sql.contains("tsrange(start_time, COALESCE(end_time, 'infinity'::timestamp), '[)') WITH &&"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_workbench"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_merge"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_event"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_user_status"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_user_reviewed"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_evidence_item_time"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_rule_enabled"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_case_no"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_time"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_case_item"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_item_case"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_rule_suggestion"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_case_audit_case"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_semantic_item"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_semantic_filter"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_export_job_no"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_export_job_case"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_lock"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_lock_until"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_run"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_run_status"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_status"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_claim"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_claimed_at"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_runtime_outbox_run"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_report_ack_key"));
        assertTrue(sql.contains("ON system_supervision_alert_review_report_ack(tenant_id, report_key)"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_supervision_alert_review_report_ack_scope"));

        for (String tenantScopedIndex : Set.of(
                "ON system_supervision_task(tenant_id, event_id)",
                "ON system_supervision_alert_review_item(tenant_id, review_item_no)",
                "ON system_supervision_alert_review_item(tenant_id, event_id)",
                "ON system_supervision_alert_review_ingest_identity(tenant_id, review_item_id)",
                "ON system_supervision_alert_review_segment(tenant_id, segment_no)",
                "ON system_supervision_alert_review_user_status(tenant_id, review_item_id, user_id)",
                "ON system_supervision_alert_review_evidence(tenant_id, review_item_id, happened_at)",
                "ON system_supervision_alert_review_rule(tenant_id, enabled, source_system, camera_id, zone_code)",
                "ON system_supervision_alert_review_case(tenant_id, case_no)",
                "ON system_supervision_alert_review_case_item(tenant_id, review_case_id, review_item_id)",
                "ON system_supervision_alert_review_case_audit(tenant_id, review_case_id, happened_at)",
                "ON system_supervision_alert_review_semantic_index(tenant_id, review_item_id)",
                "ON system_supervision_alert_review_export_job(tenant_id, job_no)",
                "ON system_supervision_alert_review_runtime_lock(tenant_id, lock_name)",
                "ON system_supervision_alert_review_runtime_run(tenant_id, run_id)",
                "ON system_supervision_alert_review_runtime_outbox(tenant_id, outbox_status, created_at)",
                "ON system_supervision_alert_review_runtime_outbox_delivery(tenant_id, outbox_id, channel, recipient_user_id, template_code)"
        )) {
            assertTrue(sql.contains(tenantScopedIndex), tenantScopedIndex + " must be tenant scoped");
        }
    }

    @Test
    void reviewSegmentOverlapUsesHalfOpenIntervalsSoAdjacentSegmentsCanSplitCleanly() throws Exception {
        String sql = readSchemaSql();
        LocalDateTime start = LocalDateTime.of(2026, 7, 4, 10, 0);
        LocalDateTime end = start.plusMinutes(2);
        Method overlaps = Class
                .forName("com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSegmentMapper")
                .getDeclaredMethod("overlaps", LocalDateTime.class, LocalDateTime.class,
                        LocalDateTime.class, LocalDateTime.class);
        overlaps.setAccessible(true);

        assertTrue(sql.contains("tsrange(start_time, COALESCE(end_time, 'infinity'::timestamp), '[)')"));
        assertFalse((Boolean) overlaps.invoke(null, end, end.plusMinutes(1), start, end));
        assertTrue((Boolean) overlaps.invoke(null, end.minusSeconds(1), end.plusMinutes(1), start, end));
        assertTrue((Boolean) overlaps.invoke(null, end, end.plusMinutes(1), start, null));
    }

    @Test
    void tablesKeepLifecycleAuditAndSoftDeleteColumns() throws IOException {
        String sql = readSchemaSql();

        for (String tableName : extractCreatedTables(sql)) {
            String tableBody = extractTableBody(sql, tableName);
            assertTrue(tableBody.contains("creator VARCHAR(64)"));
            assertTrue(tableBody.contains("create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"));
            assertTrue(tableBody.contains("updater VARCHAR(64)"));
            assertTrue(tableBody.contains("update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"));
            assertTrue(tableBody.contains("deleted SMALLINT NOT NULL DEFAULT 0"));
        }

        for (String tableName : TENANT_SCOPED_BASE_DO_TABLES) {
            assertTrue(extractTableBody(sql, tableName).contains("tenant_id BIGINT NOT NULL DEFAULT 0"),
                    tableName + " must expose the tenant_id column injected by TenantLineInterceptor");
        }
    }

    @Test
    void alertReviewMigrationsUsePlatformSmallintSoftDeleteSemantics() throws IOException {
        Path migrationDirectory = modulePath("src/main/resources/sql/migrations");
        Path compatibilityMigration = migrationDirectory.resolve(
                "V20260708_10__alert_review_deleted_smallint.sql");

        assertTrue(Files.exists(compatibilityMigration),
                "a compatibility migration must convert historical BOOLEAN deleted columns");
        try (var paths = Files.list(migrationDirectory)) {
            for (Path migration : paths
                    .filter(path -> path.getFileName().toString().endsWith(".sql"))
                    .filter(path -> !path.equals(compatibilityMigration))
                    .toList()) {
                String sql = Files.readString(migration, StandardCharsets.UTF_8);
                assertFalse(Pattern.compile("(?i)\\bdeleted\\s+BOOLEAN\\b").matcher(sql).find(),
                        migration.getFileName() + " must declare deleted as SMALLINT");
                assertFalse(Pattern.compile("(?i)\\bdeleted\\s*=\\s*(?:FALSE|TRUE)\\b").matcher(sql).find(),
                        migration.getFileName() + " must compare deleted with 0/1");
            }
        }

        String compatibilitySql = Files.readString(compatibilityMigration, StandardCharsets.UTF_8);
        assertTrue(compatibilitySql.contains("deleted_type = 'boolean'"));
        assertTrue(compatibilitySql.contains("TYPE SMALLINT"));
        assertTrue(compatibilitySql.contains("CASE WHEN deleted THEN 1 ELSE 0 END"));
        assertTrue(compatibilitySql.contains("SET DEFAULT 0"));
        assertTrue(compatibilitySql.contains("SET NOT NULL"));
        assertTrue(compatibilitySql.contains("SET tenant_id = 0"));
        for (String tableName : TENANT_SCOPED_BASE_DO_TABLES) {
            assertTrue(compatibilitySql.contains("'" + tableName + "'"),
                    tableName + " must be covered by the platform compatibility migration");
        }
    }

    @Test
    void eventTableKeepsAlertSourceAndClosureFields() throws IOException {
        String eventTable = extractTableBody(readSchemaSql(), "system_supervision_event");

        assertTrue(eventTable.contains("source_system VARCHAR(64) NOT NULL"));
        assertTrue(eventTable.contains("source_alert_id VARCHAR(128)"));
        assertTrue(eventTable.contains("event_level VARCHAR(8) NOT NULL"));
        assertTrue(eventTable.contains("event_status VARCHAR(64) NOT NULL"));
        assertTrue(eventTable.contains("close_result VARCHAR(64)"));
        assertTrue(eventTable.contains("close_check_status VARCHAR(64) NOT NULL DEFAULT 'not_checked'"));
        assertTrue(eventTable.contains("evidence_status VARCHAR(64) NOT NULL DEFAULT 'missing_soft'"));
        assertTrue(eventTable.contains("closed_at TIMESTAMP"));
    }

    @Test
    void alertReviewTablesKeepClueEvidenceAndRegionRuleFields() throws IOException {
        String sql = readSchemaSql();
        String reviewItemTable = extractTableBody(sql, "system_supervision_alert_review_item");
        String ingestIdentityTable = extractTableBody(sql, "system_supervision_alert_review_ingest_identity");
        String reviewSegmentTable = extractTableBody(sql, "system_supervision_alert_review_segment");
        String evidenceTable = extractTableBody(sql, "system_supervision_alert_review_evidence");
        String userStatusTable = extractTableBody(sql, "system_supervision_alert_review_user_status");
        String ruleTable = extractTableBody(sql, "system_supervision_alert_review_rule");
        String caseTable = extractTableBody(sql, "system_supervision_alert_review_case");
        String caseItemTable = extractTableBody(sql, "system_supervision_alert_review_case_item");
        String caseAuditTable = extractTableBody(sql, "system_supervision_alert_review_case_audit");
        String semanticIndexTable = extractTableBody(sql, "system_supervision_alert_review_semantic_index");
        String exportJobTable = extractTableBody(sql, "system_supervision_alert_review_export_job");
        String runtimeLockTable = extractTableBody(sql, "system_supervision_alert_review_runtime_lock");
        String runtimeRunTable = extractTableBody(sql, "system_supervision_alert_review_runtime_run");
        String runtimeOutboxTable = extractTableBody(sql, "system_supervision_alert_review_runtime_outbox");

        assertTrue(reviewItemTable.contains("review_item_no VARCHAR(64) NOT NULL"));
        assertTrue(reviewItemTable.contains("tenant_id BIGINT"));
        assertTrue(reviewItemTable.contains("source_alert_ids TEXT NOT NULL"));
        assertTrue(reviewItemTable.contains("review_data TEXT"));
        assertTrue(reviewItemTable.contains("review_status VARCHAR(64) NOT NULL DEFAULT 'pending_review'"));
        assertTrue(reviewItemTable.contains("rule_suggestion TEXT"));
        assertTrue(reviewItemTable.contains("rule_suggestion_status VARCHAR(64)"));
        assertTrue(reviewItemTable.contains("rule_suggestion_updated_at TIMESTAMP"));
        assertTrue(reviewItemTable.contains("event_id BIGINT"));
        assertTrue(reviewItemTable.contains("record_evidence_status VARCHAR(64) NOT NULL DEFAULT 'missing'"));
        assertTrue(reviewItemTable.contains("record_evidence_checked_at TIMESTAMP"));
        assertTrue(reviewItemTable.contains("record_evidence_message VARCHAR(256)"));
        assertTrue(sql.contains("ON system_supervision_alert_review_item(tenant_id, review_status, camera_id, last_alert_time)"));
        assertTrue(sql.contains("ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, review_status, last_alert_time)"));
        assertFalse(sql.contains("ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, zone_code, rule_code"));
        assertTrue(ingestIdentityTable.contains("tenant_id BIGINT NOT NULL DEFAULT 0"));
        assertTrue(ingestIdentityTable.contains("review_item_id BIGINT NOT NULL"));
        assertTrue(ingestIdentityTable.contains("source_system VARCHAR(64) NOT NULL"));
        assertTrue(ingestIdentityTable.contains("identity_key VARCHAR(256) NOT NULL"));
        assertTrue(ingestIdentityTable.contains("source_alert_id VARCHAR(128)"));
        assertTrue(ingestIdentityTable.contains("source_payload_hash VARCHAR(128)"));
        assertTrue(reviewSegmentTable.contains("review_item_id BIGINT NOT NULL"));
        assertTrue(reviewSegmentTable.contains("segment_no VARCHAR(128) NOT NULL"));
        assertTrue(reviewSegmentTable.contains("camera_id VARCHAR(128) NOT NULL"));
        assertTrue(reviewSegmentTable.contains("severity VARCHAR(64) NOT NULL"));
        assertTrue(reviewSegmentTable.contains("segment_status VARCHAR(64) NOT NULL DEFAULT 'active'"));
        assertTrue(reviewSegmentTable.contains("CONSTRAINT ck_supervision_alert_review_segment_status"));
        assertTrue(reviewSegmentTable.contains("CONSTRAINT ck_supervision_alert_review_segment_ended_time"));
        assertTrue(reviewSegmentTable.contains("CONSTRAINT ck_supervision_alert_review_segment_alert_severity"));
        assertTrue(reviewSegmentTable.contains("CONSTRAINT ck_supervision_alert_review_segment_severity"));
        assertTrue(reviewSegmentTable.contains("start_time TIMESTAMP NOT NULL"));
        assertTrue(reviewSegmentTable.contains("end_time TIMESTAMP"));
        assertTrue(reviewSegmentTable.contains("object_ids TEXT"));
        assertTrue(reviewSegmentTable.contains("zone_codes TEXT"));
        assertTrue(reviewSegmentTable.contains("source_alert_ids TEXT"));
        assertTrue(reviewSegmentTable.contains("segment_events TEXT"));
        assertTrue(reviewSegmentTable.contains("segment_metadata TEXT"));
        assertTrue(userStatusTable.contains("review_item_id BIGINT NOT NULL"));
        assertTrue(userStatusTable.contains("user_id BIGINT NOT NULL"));
        assertTrue(userStatusTable.contains("has_been_reviewed BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(userStatusTable.contains("reviewed_at TIMESTAMP"));
        assertTrue(evidenceTable.contains("review_item_id BIGINT NOT NULL"));
        assertTrue(evidenceTable.contains("material_type VARCHAR(64) NOT NULL"));
        assertTrue(evidenceTable.contains("material_uri VARCHAR(512)"));
        assertTrue(evidenceTable.contains("happened_at TIMESTAMP NOT NULL"));
        assertTrue(ruleTable.contains("zone_code VARCHAR(128)"));
        assertTrue(ruleTable.contains("object_label VARCHAR(128)"));
        assertTrue(ruleTable.contains("min_stay_seconds INTEGER"));
        assertTrue(ruleTable.contains("inertia_frames INTEGER"));
        assertTrue(ruleTable.contains("loitering_seconds INTEGER"));
        assertTrue(ruleTable.contains("enabled BOOLEAN NOT NULL DEFAULT TRUE"));
        assertTrue(caseTable.contains("case_no VARCHAR(64) NOT NULL"));
        assertTrue(caseTable.contains("primary_review_item_id BIGINT"));
        assertTrue(caseTable.contains("owner_user_id BIGINT"));
        assertTrue(caseTable.contains("notes TEXT"));
        assertTrue(caseTable.contains("camera_ids TEXT"));
        assertTrue(caseTable.contains("start_time TIMESTAMP"));
        assertTrue(caseItemTable.contains("review_case_id BIGINT NOT NULL"));
        assertTrue(caseItemTable.contains("review_item_id BIGINT NOT NULL"));
        assertTrue(caseItemTable.contains("sort_order INTEGER NOT NULL DEFAULT 0"));
        assertTrue(caseAuditTable.contains("review_case_id BIGINT"));
        assertFalse(caseAuditTable.contains("review_case_id BIGINT NOT NULL"));
        assertTrue(caseAuditTable.contains("review_item_id BIGINT"));
        assertTrue(caseAuditTable.contains("action_type VARCHAR(64) NOT NULL"));
        assertTrue(caseAuditTable.contains("action_note TEXT"));
        assertTrue(caseAuditTable.contains("metadata TEXT"));
        assertTrue(sql.contains("ON system_supervision_alert_review_case_audit(tenant_id, review_item_id, happened_at)"));
        assertTrue(semanticIndexTable.contains("review_item_id BIGINT NOT NULL"));
        assertTrue(semanticIndexTable.contains("camera_id VARCHAR(128)"));
        assertTrue(semanticIndexTable.contains("first_alert_time TIMESTAMP"));
        assertTrue(semanticIndexTable.contains("last_alert_time TIMESTAMP"));
        assertTrue(semanticIndexTable.contains("index_status VARCHAR(64) NOT NULL DEFAULT 'pending'"));
        assertTrue(semanticIndexTable.contains("document TEXT NOT NULL"));
        assertTrue(semanticIndexTable.contains("embedding_key VARCHAR(128)"));
        assertTrue(semanticIndexTable.contains("embedding_model VARCHAR(128)"));
        assertTrue(semanticIndexTable.contains("embedding_vector_hash VARCHAR(128)"));
        assertTrue(semanticIndexTable.contains("retry_count INTEGER NOT NULL DEFAULT 0"));
        assertTrue(semanticIndexTable.contains("last_error TEXT"));
        assertTrue(semanticIndexTable.contains("indexed_at TIMESTAMP"));
        assertTrue(exportJobTable.contains("job_no VARCHAR(64) NOT NULL"));
        assertTrue(exportJobTable.contains("status VARCHAR(64) NOT NULL DEFAULT 'pending'"));
        assertTrue(exportJobTable.contains("package_no VARCHAR(64) NOT NULL"));
        assertTrue(exportJobTable.contains("review_case_id BIGINT NOT NULL"));
        assertTrue(exportJobTable.contains("review_item_ids TEXT NOT NULL"));
        assertTrue(exportJobTable.contains("manifest TEXT"));
        assertTrue(exportJobTable.contains("file_hash VARCHAR(128) NOT NULL"));
        assertTrue(exportJobTable.contains("expires_at TIMESTAMP"));
        assertTrue(exportJobTable.contains("operator_user_id BIGINT"));
        assertTrue(exportJobTable.contains("export_reason TEXT"));
        assertTrue(exportJobTable.contains("bound_event_ids TEXT"));
        assertTrue(runtimeLockTable.contains("lock_name VARCHAR(128) NOT NULL"));
        assertTrue(runtimeLockTable.contains("owner_user_id BIGINT"));
        assertTrue(runtimeLockTable.contains("locked_until TIMESTAMP"));
        assertTrue(runtimeLockTable.contains("last_locked_at TIMESTAMP"));
        assertTrue(runtimeRunTable.contains("run_id VARCHAR(64) NOT NULL"));
        assertTrue(runtimeRunTable.contains("status VARCHAR(64) NOT NULL"));
        assertTrue(runtimeRunTable.contains("attempt_count INTEGER NOT NULL DEFAULT 0"));
        assertTrue(runtimeRunTable.contains("alerts TEXT"));
        assertTrue(runtimeRunTable.contains("recommended_actions TEXT"));
        assertTrue(runtimeRunTable.contains("metadata TEXT"));
        assertTrue(runtimeOutboxTable.contains("run_id VARCHAR(64) NOT NULL"));
        assertTrue(runtimeOutboxTable.contains("event_type VARCHAR(64) NOT NULL"));
        assertTrue(runtimeOutboxTable.contains("alert_key VARCHAR(128) NOT NULL"));
        assertTrue(runtimeOutboxTable.contains("outbox_status VARCHAR(64) NOT NULL DEFAULT 'pending'"));
        assertTrue(runtimeOutboxTable.contains("claim_token VARCHAR(128)"));
        assertTrue(runtimeOutboxTable.contains("claimed_by BIGINT"));
        assertTrue(runtimeOutboxTable.contains("claimed_at TIMESTAMP"));
        assertTrue(runtimeOutboxTable.contains("retry_count INTEGER NOT NULL DEFAULT 0"));
    }

    @Test
    void alertReviewItemMediaAuditMigrationAllowsPreCaseAuditRows() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260707__alert_review_item_media_audit.sql");

        assertTrue(Files.exists(migration), "item media audit migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("ALTER COLUMN review_case_id DROP NOT NULL"));
        assertTrue(migrationSql.contains("idx_supervision_alert_review_case_audit_item"));
        assertTrue(migrationSql.contains("review_item_id, happened_at"));
    }

    @Test
    void semanticTriggerConfirmationMigrationIsIdempotentTenantScopedAndLegacyMetadataSafe() throws IOException {
        Path migration = modulePath(
                "src/main/resources/sql/migrations/V20260712__alert_review_semantic_trigger_confirmation.sql");

        assertTrue(Files.exists(migration), "semantic trigger confirmation migration should exist");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_review_semantic_trigger_evaluation"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_alert_review_semantic_trigger_terminal"));
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("substring(metadata"));
        assertTrue(sql.contains("semantic_trigger_evaluated"));
        assertTrue(sql.contains("semantic_trigger_confirmed"));
        assertTrue(sql.contains("semantic_trigger_rejected"));
        assertTrue(sql.contains("semantic-trigger-evaluation-v1"));
        assertTrue(sql.contains("humanConfirmationStatus"));
        assertTrue(sql.contains("system:supervision-alert-review:semantic-trigger:evaluate"));
        assertTrue(sql.contains("system:supervision-alert-review:semantic-trigger:confirm"));
        assertFalse(sql.contains("metadata::jsonb"));
        assertFalse(sql.contains("metadata::JSONB"));
    }

    @Test
    void semanticIndexClaimMigrationAddsGenerationLeaseAndRetryScheduling() throws IOException {
        Path migration = modulePath(
                "src/main/resources/sql/migrations/V20260713__alert_review_semantic_index_claim.sql");

        assertTrue(Files.exists(migration), "semantic index claim migration should exist");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS index_generation_id VARCHAR(128)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS claim_expires_at TIMESTAMP"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP"));
        assertTrue(sql.contains("CREATE INDEX IF NOT EXISTS idx_alert_review_semantic_claim"));
        assertTrue(sql.contains("tenant_id, index_status, next_retry_at, claim_expires_at"));
        assertTrue(sql.contains("WHERE deleted = 0"));
    }

    @Test
    void recordEvidenceMigrationPersistsTheRealRecordingStartTime() throws IOException {
        Path migration = modulePath(
                "src/main/resources/sql/migrations/V20260713_2__alert_review_evidence_record_start.sql");

        assertTrue(Files.exists(migration), "record evidence start migration should exist");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(sql.contains("ALTER TABLE system_supervision_alert_review_evidence"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS record_start_time TIMESTAMP"));
    }

    @Test
    void localSchedulerOwnershipMigrationPausesQuartzAndProtectsReportIdempotency() throws IOException {
        Path migration = modulePath(
                "src/main/resources/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql");

        assertTrue(Files.exists(migration), "local scheduler ownership migration should exist");
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(sql.contains("LOCK TABLE infra_job"));
        assertTrue(sql.contains("system_supervision_alert_review_runtime_outbox_delivery"));
        assertTrue(sql.contains("SET status = 2"));
        assertTrue(sql.contains("ROW_NUMBER() OVER"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_runtime_outbox_report"));
        assertTrue(sql.contains("tenant_id, event_type, alert_key"));
        assertTrue(sql.contains("event_type = 'review_operations_report'"));
    }

    @Test
    void alertReviewHardeningMigrationIsSplitForProductionRelease() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260702__alert_review_frigate_hardening.sql");

        assertTrue(Files.exists(migration), "production migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("uk_supervision_alert_review_segment_item"));
        assertTrue(migrationSql.contains("ADD COLUMN IF NOT EXISTS tenant_id BIGINT"));
        assertTrue(migrationSql.contains("ON system_supervision_alert_review_item(tenant_id, review_status, camera_id, last_alert_time)"));
        assertTrue(migrationSql.contains("ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, review_status, last_alert_time)"));
        assertFalse(migrationSql.contains("ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, zone_code, rule_code"));
        assertTrue(migrationSql.contains("CREATE TABLE IF NOT EXISTS system_supervision_alert_review_ingest_identity"));
        assertTrue(migrationSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_ingest_identity"));
        assertTrue(migrationSql.contains("ON system_supervision_alert_review_ingest_identity(tenant_id, source_system, identity_key)"));
        assertTrue(migrationSql.contains("ON system_supervision_alert_review_segment(tenant_id, review_item_id)"));
        assertTrue(migrationSql.contains("UPDATE system_supervision_alert_review_segment segment"));
        assertTrue(migrationSql.contains("ON system_supervision_alert_review_segment(tenant_id, camera_id, start_time, end_time)"));
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_time"));
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_status"));
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_severity"));
        assertTrue(migrationSql.contains("ex_supervision_alert_review_segment_camera_time"));
        assertTrue(migrationSql.contains("DROP CONSTRAINT IF EXISTS ex_supervision_alert_review_segment_camera_time"));
        assertTrue(migrationSql.contains("tenant_id WITH ="));
        assertTrue(migrationSql.contains("tsrange(start_time, COALESCE(end_time, 'infinity'::timestamp), '[)') WITH &&"));
        assertTrue(migrationSql.contains("system_supervision_alert_review_segment"));
    }

    @Test
    void alertReviewSegmentTenantScopeMigrationKeepsStatusAndSeverityConstraints() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260704__alert_review_segment_tenant_scope.sql");

        assertTrue(Files.exists(migration), "tenant-scoped segment migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_status"));
        assertTrue(migrationSql.contains("segment_status IN ('active', 'detection', 'alert', 'ended')"));
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_severity"));
        assertTrue(migrationSql.contains("severity IN ('detection', 'alert')"));
        assertTrue(migrationSql.contains("tenant_id WITH ="));
        assertTrue(migrationSql.contains("camera_id WITH ="));
    }

    @Test
    void alertReviewReviewDataBackfillMigrationNormalizesLegacyRows() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260705__alert_review_review_data_backfill.sql");

        assertTrue(Files.exists(migration), "reviewData backfill migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("system_supervision_alert_review_item"));
        assertTrue(migrationSql.contains("review_data::jsonb"));
        assertTrue(migrationSql.contains("reviewDataVersion"));
        assertTrue(migrationSql.contains("labels"));
        assertTrue(migrationSql.contains("zones"));
        assertTrue(migrationSql.contains("objectIds"));
        assertTrue(migrationSql.contains("objects"));
        assertTrue(migrationSql.contains("detections"));
        assertTrue(migrationSql.contains("reviewSegment"));
        assertTrue(migrationSql.contains("migration_backfill"));
        assertTrue(migrationSql.contains("FROM system_supervision_alert_review_item item_row"));
        assertTrue(migrationSql.contains("item_row.source_alert_ids"));
    }

    @Test
    void alertReviewReportAckMigrationPersistsOperatorAcknowledgement() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_3__alert_review_report_ack.sql");

        assertTrue(Files.exists(migration), "report acknowledgement migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("CREATE TABLE IF NOT EXISTS system_supervision_alert_review_report_ack"));
        assertTrue(migrationSql.contains("report_key VARCHAR(128) NOT NULL"));
        assertTrue(migrationSql.contains("acknowledgement_status VARCHAR(32) NOT NULL"));
        assertTrue(migrationSql.contains("acknowledged_by BIGINT"));
        assertTrue(migrationSql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_alert_review_report_ack_key"));
        assertTrue(migrationSql.contains("ON system_supervision_alert_review_report_ack(tenant_id, report_key)"));
        assertTrue(migrationSql.contains("WHERE deleted = 0"));
    }

    @Test
    void alertReviewRuntimeOutboxNotifyMigrationSeedsTemplates() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_4__alert_review_runtime_outbox_notify_templates.sql");

        assertTrue(Files.exists(migration), "runtime outbox notify template migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("system_notify_template"));
        assertTrue(migrationSql.contains("YFEIEYE_REVIEW_RUNTIME_ALERT"));
        assertTrue(migrationSql.contains("YFEIEYE_REVIEW_OPERATIONS_REPORT"));
        assertTrue(migrationSql.contains("\"alertKey\",\"action\",\"runId\",\"createdAt\""));
        assertTrue(migrationSql.contains("\"reportType\",\"reportKey\",\"generatedAt\",\"evidenceGaps\""));
        assertTrue(migrationSql.contains("existing.code = seed.code"));
    }

    @Test
    void alertReviewRuntimeOutboxNotifyParamsMigrationRemovesLegacyLengthLimit() throws IOException {
        Path migration = modulePath(
                "src/main/resources/sql/migrations/V20260713_5__alert_review_notify_message_params_text.sql");

        assertTrue(Files.exists(migration), "runtime outbox notify params migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("ALTER TABLE system_notify_message"));
        assertTrue(migrationSql.contains("ALTER COLUMN template_params TYPE TEXT"));
    }

    @Test
    void alertReviewRuntimeOutboxDeliveryMigrationTracksRecipientIdempotency() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_5__alert_review_runtime_outbox_delivery.sql");
        Path baseline = modulePath("src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql");

        assertTrue(Files.exists(migration), "runtime outbox delivery migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("CREATE TABLE IF NOT EXISTS system_supervision_alert_review_runtime_outbox_delivery"));
        assertTrue(migrationSql.contains("outbox_id BIGINT NOT NULL"));
        assertTrue(migrationSql.contains("recipient_user_id BIGINT NOT NULL"));
        assertTrue(migrationSql.contains("notify_message_id BIGINT"));
        assertTrue(migrationSql.contains("attempt_count INTEGER NOT NULL DEFAULT 0"));
        assertTrue(migrationSql.contains("uk_supervision_alert_review_runtime_outbox_delivery_recipient"));
        assertTrue(migrationSql.contains("FOREIGN KEY (outbox_id)"));

        String baselineSql = Files.readString(baseline, StandardCharsets.UTF_8);
        assertTrue(baselineSql.contains("system_supervision_alert_review_runtime_outbox_delivery"));
        assertTrue(baselineSql.contains("idx_supervision_alert_review_runtime_outbox_delivery_status"));
    }

    @Test
    void alertReviewRuntimeOutboxClaimMigrationTracksProcessingOwnership() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_6__alert_review_runtime_outbox_claim.sql");
        Path baseline = modulePath("src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql");

        assertTrue(Files.exists(migration), "runtime outbox claim migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("ADD COLUMN IF NOT EXISTS claim_token VARCHAR(128)"));
        assertTrue(migrationSql.contains("ADD COLUMN IF NOT EXISTS claimed_by BIGINT"));
        assertTrue(migrationSql.contains("ADD COLUMN IF NOT EXISTS claimed_at TIMESTAMP"));
        assertTrue(migrationSql.contains("idx_supervision_alert_review_runtime_outbox_claim"));
        assertTrue(migrationSql.contains("idx_supervision_alert_review_runtime_outbox_claimed_at"));

        String baselineSql = Files.readString(baseline, StandardCharsets.UTF_8);
        assertTrue(baselineSql.contains("claim_token VARCHAR(128)"));
        assertTrue(baselineSql.contains("idx_supervision_alert_review_runtime_outbox_claim"));
    }

    @Test
    void alertReviewSegmentEndTimeGuardMigrationRequiresEndedSegmentsToHaveEndTime() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_7__alert_review_segment_end_time_guard.sql");
        Path baseline = modulePath("src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql");

        assertTrue(Files.exists(migration), "segment end-time guard migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("SET end_time = start_time"));
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_ended_time"));
        assertTrue(migrationSql.contains("segment_status <> 'ended' OR end_time IS NOT NULL"));

        String baselineSql = Files.readString(baseline, StandardCharsets.UTF_8);
        assertTrue(baselineSql.contains("ck_supervision_alert_review_segment_ended_time"));
    }

    @Test
    void alertReviewSegmentAlertSeverityGuardMigrationRequiresAlertSegmentsToKeepAlertSeverity() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_8__alert_review_segment_alert_severity_guard.sql");
        Path baseline = modulePath("src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql");

        assertTrue(Files.exists(migration), "segment alert severity guard migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("SET severity = 'alert'"));
        assertTrue(migrationSql.contains("ck_supervision_alert_review_segment_alert_severity"));
        assertTrue(migrationSql.contains("segment_status <> 'alert' OR severity = 'alert'"));

        String baselineSql = Files.readString(baseline, StandardCharsets.UTF_8);
        assertTrue(baselineSql.contains("ck_supervision_alert_review_segment_alert_severity"));
    }

    @Test
    void alertReviewMergeIndexMigrationUsesSameCameraWindowSemantics() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260708_9__alert_review_merge_index_same_camera.sql");
        Path baseline = modulePath("src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql");
        String sameCameraIndex = "ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, review_status, last_alert_time)";
        String oldZoneRuleIndex = "ON system_supervision_alert_review_item(tenant_id, source_system, camera_id, zone_code, rule_code";

        assertTrue(Files.exists(migration), "same-camera merge index migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("DROP INDEX IF EXISTS idx_supervision_alert_review_merge"));
        assertTrue(migrationSql.contains(sameCameraIndex));
        assertFalse(migrationSql.contains(oldZoneRuleIndex));

        String baselineSql = Files.readString(baseline, StandardCharsets.UTF_8);
        assertTrue(baselineSql.contains(sameCameraIndex));
        assertFalse(baselineSql.contains(oldZoneRuleIndex));
    }

    @Test
    void runtimeOutboxClaimSqlReclaimsStaleProcessingRowsWithSkipLocked() throws Exception {
        Method claimPending = Class
                .forName("com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeOutboxMapper")
                .getDeclaredMethod("claimPending", Long.class, Integer.class, String.class, Long.class,
                        LocalDateTime.class, LocalDateTime.class);
        Update update = claimPending.getAnnotation(Update.class);
        assertNotNull(update);
        String sql = String.join("\n", update.value());

        assertTrue(sql.contains("outbox_status = 'pending'"));
        assertTrue(sql.contains("outbox_status = 'processing'"));
        assertTrue(sql.contains("#{reclaimBefore,jdbcType=TIMESTAMP} IS NOT NULL"));
        assertTrue(sql.contains("claimed_at < #{reclaimBefore,jdbcType=TIMESTAMP}"));
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"));
        assertTrue(sql.contains("target.tenant_id = #{tenantId,jdbcType=BIGINT}"));
        assertTrue(sql.contains("candidate.tenant_id = #{tenantId,jdbcType=BIGINT}"));
        assertEquals("true", claimPending.getAnnotation(InterceptorIgnore.class).tenantLine());
    }

    @Test
    void semanticIndexClaimSqlIsTenantScopedAndSkipsTenantParser() throws Exception {
        Method claimProcessable = SupervisionAlertReviewSemanticIndexMapper.class.getDeclaredMethod(
                "claimProcessable",
                Long.class,
                List.class,
                Integer.class,
                String.class,
                LocalDateTime.class,
                LocalDateTime.class
        );
        Update update = claimProcessable.getAnnotation(Update.class);
        assertNotNull(update);
        String sql = String.join("\n", update.value());

        assertTrue(sql.contains("target.tenant_id = #{tenantId,jdbcType=BIGINT}"));
        assertTrue(sql.contains("candidate.tenant_id = #{tenantId,jdbcType=BIGINT}"));
        assertTrue(sql.contains("FOR UPDATE SKIP LOCKED"));
        assertEquals("true", claimProcessable.getAnnotation(InterceptorIgnore.class).tenantLine());
    }

    @Test
    void alertReviewMediaPermissionMigrationSeedsMenuPermissions() throws IOException {
        Path migration = modulePath("src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql");

        assertTrue(Files.exists(migration), "review media permission migration should exist");
        String migrationSql = Files.readString(migration, StandardCharsets.UTF_8);
        assertTrue(migrationSql.contains("system_menu"));
        assertTrue(migrationSql.contains("system_menu_seq"));
        assertTrue(migrationSql.contains("type = 3"));
        assertTrue(migrationSql.contains("system:supervision-alert-review:media:playback"));
        assertTrue(migrationSql.contains("system:supervision-alert-review:media:snapshot"));
        assertTrue(migrationSql.contains("system:supervision-alert-review:media:export"));
        assertTrue(migrationSql.contains("system:supervision-alert-review:media:download"));
        assertTrue(migrationSql.contains("system:supervision-alert-review:media:manifest"));
        assertFalse(migrationSql.contains("system:supervision-alert-review:media:manage"));

        Path managementMigration = modulePath(
                "src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql");
        assertTrue(Files.exists(managementMigration), "review media management permission migration should exist");
        String managementSql = Files.readString(managementMigration, StandardCharsets.UTF_8);
        assertTrue(managementSql.contains("system_menu"));
        assertTrue(managementSql.contains("system_menu_seq"));
        assertTrue(managementSql.contains("system:supervision-alert-review:media:manage"));
    }

    @Test
    void reviewDataJsonSchemaArtifactDefinesVersionedFrigateReviewFields() throws IOException {
        Path schema = modulePath("src/main/resources/schemas/alert-review-review-data-v1.schema.json");

        assertTrue(Files.exists(schema), "reviewData JSON schema artifact should exist");
        JsonNode root = OBJECT_MAPPER.readTree(Files.readString(schema, StandardCharsets.UTF_8));
        JsonNode properties = root.path("properties");
        String required = root.path("required").toString();

        assertEquals("https://json-schema.org/draft/2020-12/schema", root.path("$schema").asText());
        assertEquals("yfeieye.alertReview.reviewData.v1", root.path("$id").asText());
        assertEquals(1, properties.path("reviewDataVersion").path("const").asInt());
        assertEquals("array", properties.path("labels").path("type").asText());
        assertEquals("array", properties.path("zones").path("type").asText());
        assertEquals("array", properties.path("objectIds").path("type").asText());
        assertEquals("number", properties.path("confidence").path("type").asText());
        assertEquals("array", properties.path("bbox").path("type").asText());
        assertEquals(4, properties.path("bbox").path("minItems").asInt());
        assertEquals(4, properties.path("bbox").path("maxItems").asInt());
        assertEquals("string", properties.path("correlationId").path("type").asText());
        assertTrue(properties.has("reviewSegment"));
        assertTrue(required.contains("\"reviewDataVersion\""));
        assertTrue(required.contains("\"labels\""));
        assertTrue(required.contains("\"zones\""));
        assertTrue(required.contains("\"objectIds\""));
        assertTrue(required.contains("\"reviewSegment\""));
    }

    private static String readSchemaSql() throws IOException {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(SCHEMA_RESOURCE);
        assertNotNull(inputStream, SCHEMA_RESOURCE + " should exist on the classpath");
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Path modulePath(String relativePath) {
        try {
            Path testClasses = Path.of(SupervisionSchemaSqlTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return testClasses.getParent().getParent().resolve(relativePath).normalize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve iot-system-biz module path", exception);
        }
    }

    private static Set<String> extractCreatedTables(String sql) {
        Matcher matcher = Pattern.compile("(?im)^\\s*CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+([a-zA-Z0-9_]+)\\s*\\(")
                .matcher(sql);
        Set<String> tableNames = new LinkedHashSet<>();
        while (matcher.find()) {
            tableNames.add(matcher.group(1));
        }
        return tableNames;
    }

    private static String extractTableBody(String sql, String tableName) {
        Matcher matcher = Pattern.compile("(?is)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+" + tableName + "\\s*\\((.*?)\\);")
                .matcher(sql);
        assertTrue(matcher.find(), tableName + " table should be defined");
        return matcher.group(1);
    }

}
