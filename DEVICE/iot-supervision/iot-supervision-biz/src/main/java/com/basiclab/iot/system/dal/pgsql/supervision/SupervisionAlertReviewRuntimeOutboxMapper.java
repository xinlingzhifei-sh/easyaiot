package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupervisionAlertReviewRuntimeOutboxMapper extends BaseMapperX<SupervisionAlertReviewRuntimeOutboxDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO system_supervision_alert_review_runtime_outbox(
                tenant_id, run_id, event_type, alert_key, payload, outbox_status,
                operator_user_id, created_at, retry_count, version
            ) VALUES (
                #{tenantId,jdbcType=BIGINT},
                #{entry.runId,jdbcType=VARCHAR},
                #{entry.eventType,jdbcType=VARCHAR},
                #{entry.alertKey,jdbcType=VARCHAR},
                #{entry.payload,jdbcType=VARCHAR},
                #{entry.outboxStatus,jdbcType=VARCHAR},
                #{entry.operatorUserId,jdbcType=BIGINT},
                #{entry.createdAt,jdbcType=TIMESTAMP},
                #{entry.retryCount,jdbcType=INTEGER},
                #{entry.version,jdbcType=INTEGER}
            )
            ON CONFLICT (tenant_id, event_type, alert_key)
            WHERE deleted = 0
              AND event_type = 'review_operations_report'
            DO NOTHING
            """)
    int insertOperationsReportIfAbsent(@Param("tenantId") Long tenantId,
                                       @Param("entry") SupervisionAlertReviewRuntimeOutboxDO entry);

    default List<SupervisionAlertReviewRuntimeOutboxDO> selectPending(Integer limit) {
        int normalizedLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewRuntimeOutboxDO>()
                .eq(SupervisionAlertReviewRuntimeOutboxDO::getOutboxStatus, "pending")
                .orderByAsc(SupervisionAlertReviewRuntimeOutboxDO::getCreatedAt)
                .orderByAsc(SupervisionAlertReviewRuntimeOutboxDO::getId)
                .last("LIMIT " + normalizedLimit));
    }

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE system_supervision_alert_review_runtime_outbox AS target
            SET outbox_status = 'processing',
                claim_token = #{claimToken,jdbcType=VARCHAR},
                claimed_by = #{claimedBy,jdbcType=BIGINT},
                claimed_at = #{claimedAt,jdbcType=TIMESTAMP},
                update_time = CURRENT_TIMESTAMP
            WHERE target.tenant_id = #{tenantId,jdbcType=BIGINT}
              AND target.id IN (
                SELECT candidate.id
                FROM system_supervision_alert_review_runtime_outbox AS candidate
                WHERE candidate.tenant_id = #{tenantId,jdbcType=BIGINT}
                  AND (
                    candidate.outbox_status = 'pending'
                    OR (
                        candidate.outbox_status = 'failed'
                        AND COALESCE(candidate.retry_count, 0) < 10
                        AND (
                            candidate.published_at IS NULL
                            OR candidate.published_at <= #{claimedAt,jdbcType=TIMESTAMP}
                                - (INTERVAL '1 second' * LEAST(
                                    3600,
                                    30 * POWER(2, LEAST(COALESCE(candidate.retry_count, 0), 7))
                                ))
                        )
                    )
                    OR (
                        candidate.outbox_status = 'processing'
                        AND #{reclaimBefore,jdbcType=TIMESTAMP} IS NOT NULL
                        AND (
                            candidate.claimed_at IS NULL
                            OR candidate.claimed_at < #{reclaimBefore,jdbcType=TIMESTAMP}
                        )
                    )
                )
                  AND candidate.deleted = 0
                ORDER BY candidate.created_at ASC, candidate.id ASC
                LIMIT #{limit,jdbcType=INTEGER}
                FOR UPDATE SKIP LOCKED
            )
            """)
    int claimPending(@Param("tenantId") Long tenantId,
                     @Param("limit") Integer limit,
                     @Param("claimToken") String claimToken,
                     @Param("claimedBy") Long claimedBy,
                     @Param("claimedAt") LocalDateTime claimedAt,
                     @Param("reclaimBefore") LocalDateTime reclaimBefore);

    default List<SupervisionAlertReviewRuntimeOutboxDO> selectClaimed(String claimToken, Integer limit) {
        int normalizedLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewRuntimeOutboxDO>()
                .eq(SupervisionAlertReviewRuntimeOutboxDO::getOutboxStatus, "processing")
                .eq(SupervisionAlertReviewRuntimeOutboxDO::getClaimToken, claimToken)
                .orderByAsc(SupervisionAlertReviewRuntimeOutboxDO::getCreatedAt)
                .orderByAsc(SupervisionAlertReviewRuntimeOutboxDO::getId)
                .last("LIMIT " + normalizedLimit));
    }

    default boolean existsActive(String eventType, String alertKey) {
        return selectCount(new LambdaQueryWrapperX<SupervisionAlertReviewRuntimeOutboxDO>()
                .eq(SupervisionAlertReviewRuntimeOutboxDO::getEventType, eventType)
                .eq(SupervisionAlertReviewRuntimeOutboxDO::getAlertKey, alertKey)
                .in(SupervisionAlertReviewRuntimeOutboxDO::getOutboxStatus,
                        List.of("pending", "processing", "published", "failed"))) > 0;
    }

}
