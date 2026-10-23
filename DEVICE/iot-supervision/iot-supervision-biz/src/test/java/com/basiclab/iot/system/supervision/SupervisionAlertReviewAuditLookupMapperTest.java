package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseAuditMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewExportJobMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewAuditLookupMapperTest {

    @Test
    void exportLookupIsTenantScopedParameterizedIntersectingAndBounded() throws Exception {
        String sql = selectSql(SupervisionAlertReviewExportJobMapper.class);

        assertLookupSql(sql);
        assertTrue(sql.contains("system_supervision_alert_review_export_job"));
        assertTrue(sql.contains("bound_event_ids"));
        assertTrue(sql.contains("review_item_ids"));
        assertTrue(sql.contains("job_no = #{exportJobNo"));
    }

    @Test
    void persistedAuditLookupUsesDatabaseRelationsForEventAndExportKeys() throws Exception {
        String sql = selectSql(SupervisionAlertReviewCaseAuditMapper.class);

        assertLookupSql(sql);
        assertTrue(sql.contains("system_supervision_alert_review_case_audit"));
        assertTrue(sql.contains("system_supervision_alert_review_item"));
        assertTrue(sql.contains("system_supervision_alert_review_export_job"));
        assertTrue(sql.contains("action_type IN ('export_downloaded', 'media_access_granted', 'media_access_denied')"));
        assertTrue(sql.contains("job_no = #{exportJobNo"));
        assertFalse(sql.contains("::JSONB"));
        assertFalse(sql.contains("::jsonb"));
    }

    @Test
    void caseOperationIdempotencyLookupSkipsMalformedLegacyMetadataWithoutJsonCast() throws Exception {
        Method method = SupervisionAlertReviewCaseAuditMapper.class.getMethod(
                "selectByOperationId",
                Long.class,
                String.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("tenant_id = #{tenantId"));
        assertTrue(sql.contains("action_type = #{actionType"));
        assertTrue(sql.contains("position("));
        assertTrue(sql.contains("#{operationId"));
        assertFalse(sql.contains("::jsonb"));
        assertFalse(sql.contains("::JSONB"));
    }

    @Test
    void semanticTriggerLookupIsTenantScopedParameterizedBoundedAndLegacySafe() throws Exception {
        Method method = SupervisionAlertReviewCaseAuditMapper.class.getMethod(
                "selectSemanticTriggerAudits",
                Long.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        String sql = String.join(" ", select.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("tenant_id = #{tenantId"));
        assertTrue(sql.contains("#{evaluationId"));
        assertTrue(sql.contains("substring(metadata"));
        assertTrue(sql.contains("semantic_trigger_evaluated"));
        assertTrue(sql.contains("semantic_trigger_confirmed"));
        assertTrue(sql.contains("semantic_trigger_rejected"));
        assertTrue(sql.contains("semantic-trigger-evaluation-v1"));
        assertTrue(sql.contains("humanConfirmationStatus"));
        assertTrue(sql.contains("LIMIT 3"));
        assertFalse(sql.contains("::jsonb"));
        assertFalse(sql.contains("${"));
    }

    @Test
    void semanticTriggerDecisionInsertUsesAtomicPendingToTerminalTransition() throws Exception {
        Method method = SupervisionAlertReviewCaseAuditMapper.class.getMethod(
                "insertSemanticTriggerDecision",
                Long.class,
                String.class,
                String.class,
                String.class,
                String.class,
                Long.class,
                java.time.LocalDateTime.class
        );
        Insert insert = method.getAnnotation(Insert.class);
        assertNotNull(insert);
        String sql = String.join(" ", insert.value()).replaceAll("\\s+", " ");

        assertTrue(sql.contains("INSERT INTO system_supervision_alert_review_case_audit"));
        assertTrue(sql.contains("pending.tenant_id = #{tenantId"));
        assertTrue(sql.contains("pending.action_type = 'semantic_trigger_evaluated'"));
        assertTrue(sql.contains("NOT EXISTS"));
        assertTrue(sql.contains("semantic_trigger_confirmed"));
        assertTrue(sql.contains("semantic_trigger_rejected"));
        assertTrue(sql.contains("semantic-trigger-evaluation-v1"));
        assertTrue(sql.contains("humanConfirmationStatus"));
        assertTrue(sql.contains("ON CONFLICT DO NOTHING"));
        assertFalse(sql.contains("::jsonb"));
        assertFalse(sql.contains("${"));
    }

    private static String selectSql(Class<?> mapperType) throws Exception {
        Method method = mapperType.getMethod(
                "selectEvidenceAuditLookup",
                Long.class,
                Long.class,
                Long.class,
                Long.class,
                String.class
        );
        Select select = method.getAnnotation(Select.class);
        assertNotNull(select);
        return String.join(" ", select.value()).replaceAll("\\s+", " ");
    }

    private static void assertLookupSql(String sql) {
        assertTrue(sql.contains("tenant_id = #{tenantId"));
        assertTrue(sql.contains("review_case_id = #{reviewCaseId"));
        assertTrue(sql.contains("#{reviewItemId"));
        assertTrue(sql.contains("#{eventId"));
        assertTrue(sql.contains("LIMIT 500"));
        assertTrue(sql.contains("CASE WHEN btrim("));
        assertTrue(sql.contains("~ '^[0-9]{1,30}$'"));
        assertTrue(sql.contains("::NUMERIC"));
        assertFalse(sql.contains("::BIGINT[]"));
        assertFalse(sql.contains("${"));
    }

}
