package com.basiclab.iot.system.dal.pgsql.supervision;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewExportJobDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupervisionAlertReviewExportJobMapper extends BaseMapperX<SupervisionAlertReviewExportJobDO> {

    @Select("""
            <script>
            SELECT job.*
            FROM system_supervision_alert_review_export_job job
            WHERE job.tenant_id = #{tenantId,jdbcType=BIGINT}
              AND job.deleted = 0
            <if test="reviewCaseId != null">
              AND job.review_case_id = #{reviewCaseId,jdbcType=BIGINT}
            </if>
            <if test="reviewItemId != null">
              AND EXISTS (
                SELECT 1
                FROM unnest(string_to_array(COALESCE(job.review_item_ids, ''), ',')) AS review_item_token(value)
                WHERE CASE WHEN btrim(review_item_token.value) ~ '^[0-9]{1,30}$'
                           THEN btrim(review_item_token.value)::NUMERIC END = #{reviewItemId,jdbcType=BIGINT}
              )
            </if>
            <if test="eventId != null">
              AND (
                EXISTS (
                  SELECT 1
                  FROM unnest(string_to_array(COALESCE(job.bound_event_ids, ''), ',')) AS bound_event_token(value)
                  WHERE CASE WHEN btrim(bound_event_token.value) ~ '^[0-9]{1,30}$'
                             THEN btrim(bound_event_token.value)::NUMERIC END = #{eventId,jdbcType=BIGINT}
                )
                OR EXISTS (
                  SELECT 1
                  FROM system_supervision_alert_review_item item
                  WHERE item.tenant_id = #{tenantId,jdbcType=BIGINT}
                    AND item.deleted = 0
                    AND item.event_id = #{eventId,jdbcType=BIGINT}
                    AND EXISTS (
                      SELECT 1
                      FROM unnest(string_to_array(COALESCE(job.review_item_ids, ''), ',')) AS event_item_token(value)
                      WHERE CASE WHEN btrim(event_item_token.value) ~ '^[0-9]{1,30}$'
                                 THEN btrim(event_item_token.value)::NUMERIC END = item.id
                      )
                )
              )
            </if>
            <if test="exportJobNo != null and exportJobNo != ''">
              AND job.job_no = #{exportJobNo,jdbcType=VARCHAR}
            </if>
            ORDER BY job.generated_at ASC, job.id ASC
            LIMIT 500
            </script>
            """)
    List<SupervisionAlertReviewExportJobDO> selectEvidenceAuditLookup(@Param("tenantId") Long tenantId,
                                                                      @Param("eventId") Long eventId,
                                                                      @Param("reviewCaseId") Long reviewCaseId,
                                                                      @Param("reviewItemId") Long reviewItemId,
                                                                      @Param("exportJobNo") String exportJobNo);

    default SupervisionAlertReviewExportJobDO selectByJobNo(String jobNo) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewExportJobDO>()
                .eq(SupervisionAlertReviewExportJobDO::getJobNo, jobNo));
    }

    default SupervisionAlertReviewExportJobDO selectActiveByRequestKey(String requestKey) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewExportJobDO>()
                .eq(SupervisionAlertReviewExportJobDO::getRequestKey, requestKey)
                .ne(SupervisionAlertReviewExportJobDO::getStatus, "expired"));
    }

    default List<SupervisionAlertReviewExportJobDO> selectByReviewCaseId(Long reviewCaseId) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewExportJobDO>()
                .eq(SupervisionAlertReviewExportJobDO::getReviewCaseId, reviewCaseId)
                .orderByAsc(SupervisionAlertReviewExportJobDO::getGeneratedAt));
    }

    default List<SupervisionAlertReviewExportJobDO> selectAll() {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewExportJobDO>()
                .orderByAsc(SupervisionAlertReviewExportJobDO::getGeneratedAt));
    }

    @Insert("""
            INSERT INTO system_supervision_alert_review_export_job (
                job_no,
                request_key,
                status,
                package_no,
                review_case_id,
                review_item_ids,
                evidence_uris,
                manifest,
                file_hash,
                expires_at,
                operator_user_id,
                export_reason,
                bound_event_ids,
                generated_at,
                version,
                deleted
            ) VALUES (
                #{job.jobNo,jdbcType=VARCHAR},
                #{job.requestKey,jdbcType=VARCHAR},
                #{job.status,jdbcType=VARCHAR},
                #{job.packageNo,jdbcType=VARCHAR},
                #{job.reviewCaseId,jdbcType=BIGINT},
                #{job.reviewItemIds,jdbcType=VARCHAR},
                #{job.evidenceUris,jdbcType=VARCHAR},
                #{job.manifest,jdbcType=VARCHAR},
                #{job.fileHash,jdbcType=VARCHAR},
                #{job.expiresAt,jdbcType=TIMESTAMP},
                #{job.operatorUserId,jdbcType=BIGINT},
                #{job.exportReason,jdbcType=VARCHAR},
                #{job.boundEventIds,jdbcType=VARCHAR},
                #{job.generatedAt,jdbcType=TIMESTAMP},
                #{job.version,jdbcType=INTEGER},
                0
            )
            ON CONFLICT DO NOTHING
            """)
    int insertIfAbsent(@Param("job") SupervisionAlertReviewExportJobDO job);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE system_supervision_alert_review_export_job AS target
            SET status = 'running',
                claim_token = #{claimToken,jdbcType=VARCHAR},
                claimed_by = #{claimedBy,jdbcType=BIGINT},
                claimed_at = #{claimedAt,jdbcType=TIMESTAMP},
                version = version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE target.tenant_id = #{tenantId,jdbcType=BIGINT}
              AND target.id IN (
                SELECT candidate.id
                FROM system_supervision_alert_review_export_job AS candidate
                WHERE candidate.tenant_id = #{tenantId,jdbcType=BIGINT}
                  AND candidate.deleted = 0
                  AND (
                    (
                      candidate.status IN ('pending', 'failed', 'ready')
                      AND candidate.expires_at IS NOT NULL
                      AND candidate.expires_at <= #{claimedAt,jdbcType=TIMESTAMP}
                    )
                    OR candidate.status = 'pending'
                    OR (
                      candidate.status = 'failed'
                      AND (candidate.next_retry_at IS NULL
                           OR candidate.next_retry_at <= #{claimedAt,jdbcType=TIMESTAMP})
                    )
                    OR (
                      candidate.status = 'running'
                      AND #{reclaimBefore,jdbcType=TIMESTAMP} IS NOT NULL
                      AND (candidate.claimed_at IS NULL
                           OR candidate.claimed_at < #{reclaimBefore,jdbcType=TIMESTAMP})
                    )
                  )
                ORDER BY candidate.generated_at ASC, candidate.id ASC
                LIMIT #{limit,jdbcType=INTEGER}
                FOR UPDATE SKIP LOCKED
            )
            """)
    int claimProcessable(@Param("tenantId") Long tenantId,
                         @Param("limit") Integer limit,
                         @Param("claimToken") String claimToken,
                         @Param("claimedBy") Long claimedBy,
                         @Param("claimedAt") LocalDateTime claimedAt,
                         @Param("reclaimBefore") LocalDateTime reclaimBefore);

    default List<SupervisionAlertReviewExportJobDO> selectClaimed(String claimToken, Integer limit) {
        int normalizedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewExportJobDO>()
                .eq(SupervisionAlertReviewExportJobDO::getStatus, "running")
                .eq(SupervisionAlertReviewExportJobDO::getClaimToken, claimToken)
                .orderByAsc(SupervisionAlertReviewExportJobDO::getGeneratedAt)
                .orderByAsc(SupervisionAlertReviewExportJobDO::getId)
                .last("LIMIT " + normalizedLimit));
    }

    @Update("""
            UPDATE system_supervision_alert_review_export_job
            SET status = #{job.status,jdbcType=VARCHAR},
                package_no = #{job.packageNo,jdbcType=VARCHAR},
                review_case_id = #{job.reviewCaseId,jdbcType=BIGINT},
                review_item_ids = #{job.reviewItemIds,jdbcType=VARCHAR},
                evidence_uris = #{job.evidenceUris,jdbcType=VARCHAR},
                manifest = #{job.manifest,jdbcType=VARCHAR},
                file_hash = #{job.fileHash,jdbcType=VARCHAR},
                expires_at = #{job.expiresAt,jdbcType=TIMESTAMP},
                operator_user_id = #{job.operatorUserId,jdbcType=BIGINT},
                export_reason = #{job.exportReason,jdbcType=VARCHAR},
                bound_event_ids = #{job.boundEventIds,jdbcType=VARCHAR},
                generated_at = #{job.generatedAt,jdbcType=TIMESTAMP},
                next_retry_at = #{job.nextRetryAt,jdbcType=TIMESTAMP},
                last_error = #{job.lastError,jdbcType=VARCHAR},
                claim_token = NULL,
                claimed_by = NULL,
                claimed_at = NULL,
                version = version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{job.id,jdbcType=BIGINT}
              AND status = 'running'
              AND claim_token = #{claimToken,jdbcType=VARCHAR}
              AND version = #{expectedVersion,jdbcType=INTEGER}
              AND deleted = 0
            """)
    int completeClaim(@Param("job") SupervisionAlertReviewExportJobDO job,
                      @Param("claimToken") String claimToken,
                      @Param("expectedVersion") Integer expectedVersion);

}
