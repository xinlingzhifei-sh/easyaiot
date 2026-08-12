package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewExportJobDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewExportJobMapper;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemStore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewExportQueuePersistenceTest {

    @Test
    void exportQueueCreationUsesPostgresConflictSafeInsert() throws Exception {
        Method insert = SupervisionAlertReviewExportJobMapper.class.getMethod(
                "insertIfAbsent",
                SupervisionAlertReviewExportJobDO.class
        );
        String insertSql = String.join("\n", insert.getAnnotation(Insert.class).value()).toLowerCase();

        assertTrue(insertSql.contains("insert into system_supervision_alert_review_export_job"));
        assertTrue(insertSql.contains("request_key"));
        assertTrue(insertSql.contains("on conflict do nothing"));
    }

    @Test
    void exportQueueStoreExposesOnlyClaimOwnedStateTransitions() throws Exception {
        assertFalse(Arrays.stream(ReviewItemStore.class.getMethods())
                .anyMatch(method -> "updateExportJob".equals(method.getName())));

        Method complete = ReviewItemStore.class.getMethod(
                "completeExportJobClaim",
                ReviewEvidenceExportJob.class,
                String.class
        );
        assertFalse(complete.isDefault());
    }

    @Test
    void exportQueueClaimUsesPostgresSkipLockedAndVersionedOwnership() throws Exception {
        Method claim = SupervisionAlertReviewExportJobMapper.class.getMethod(
                "claimProcessable",
                Long.class,
                Integer.class,
                String.class,
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class
        );
        String claimSql = String.join("\n", claim.getAnnotation(Update.class).value()).toLowerCase();

        assertTrue(claimSql.contains("for update skip locked"));
        assertTrue(claimSql.contains("status = 'running'"));
        assertTrue(claimSql.contains("claim_token"));
        assertTrue(claimSql.contains("claimed_at"));
        assertTrue(claimSql.contains("version = version + 1"));
        assertTrue(claimSql.contains("reclaimbefore"));
        assertTrue(claimSql.contains("target.tenant_id = #{tenantid"));
        assertTrue(claimSql.contains("candidate.tenant_id = #{tenantid"));
        InterceptorIgnore interceptorIgnore = claim.getAnnotation(InterceptorIgnore.class);
        assertNotNull(interceptorIgnore);
        assertTrue(Boolean.parseBoolean(interceptorIgnore.tenantLine()));

        Method complete = SupervisionAlertReviewExportJobMapper.class.getMethod(
                "completeClaim",
                SupervisionAlertReviewExportJobDO.class,
                String.class,
                Integer.class
        );
        String completeSql = String.join("\n", complete.getAnnotation(Update.class).value()).toLowerCase();
        assertTrue(completeSql.contains("version = #{expectedversion"));
        assertTrue(completeSql.contains("claim_token = #{claimtoken"));
        assertTrue(completeSql.contains("claim_token = null"));
    }

    @Test
    void exportQueueMigrationAddsIdempotencyAndClaimIndexes() throws Exception {
        InputStream migration = getClass().getClassLoader().getResourceAsStream(
                "sql/migrations/V20260710__alert_review_export_queue.sql");
        assertNotNull(migration);
        String sql;
        try (migration) {
            sql = new String(migration.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        assertTrue(sql.contains("request_key"));
        assertTrue(sql.contains("claim_token"));
        assertTrue(sql.contains("claimed_by"));
        assertTrue(sql.contains("claimed_at"));
        assertTrue(sql.contains("next_retry_at"));
        assertTrue(sql.contains("unique index"));
        assertTrue(sql.contains("status <> 'expired'"));
    }
}
