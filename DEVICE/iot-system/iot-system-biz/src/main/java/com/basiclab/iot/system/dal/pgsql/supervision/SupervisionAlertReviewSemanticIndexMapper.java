package com.basiclab.iot.system.dal.pgsql.supervision;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSemanticIndexDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupervisionAlertReviewSemanticIndexMapper extends BaseMapperX<SupervisionAlertReviewSemanticIndexDO> {

    default SupervisionAlertReviewSemanticIndexDO selectByReviewItemId(Long reviewItemId) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewSemanticIndexDO>()
                .eq(SupervisionAlertReviewSemanticIndexDO::getReviewItemId, reviewItemId));
    }

    default List<SupervisionAlertReviewSemanticIndexDO> selectByReviewItemIds(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewSemanticIndexDO>()
                .in(SupervisionAlertReviewSemanticIndexDO::getReviewItemId, reviewItemIds));
    }

    @Insert("""
            INSERT INTO system_supervision_alert_review_semantic_index (
                review_item_id,
                camera_id,
                first_alert_time,
                last_alert_time,
                index_status,
                document,
                embedding_key,
                embedding_model,
                embedding_vector_hash,
                retry_count,
                last_error,
                indexed_at,
                index_generation_id,
                next_retry_at,
                claim_token,
                claimed_at,
                claim_expires_at,
                version,
                deleted
            ) VALUES (
                #{index.reviewItemId,jdbcType=BIGINT},
                #{index.cameraId,jdbcType=VARCHAR},
                #{index.firstAlertTime,jdbcType=TIMESTAMP},
                #{index.lastAlertTime,jdbcType=TIMESTAMP},
                'pending',
                #{index.document,jdbcType=LONGVARCHAR},
                #{index.embeddingKey,jdbcType=VARCHAR},
                #{index.embeddingModel,jdbcType=VARCHAR},
                NULL,
                0,
                NULL,
                NULL,
                #{index.indexGenerationId,jdbcType=VARCHAR},
                NULL,
                NULL,
                NULL,
                NULL,
                0,
                0
            )
            ON CONFLICT DO NOTHING
            """)
    int insertPendingIfAbsent(@Param("index") SupervisionAlertReviewSemanticIndexDO index);

    @Update("""
            UPDATE system_supervision_alert_review_semantic_index
            SET camera_id = #{index.cameraId,jdbcType=VARCHAR},
                first_alert_time = #{index.firstAlertTime,jdbcType=TIMESTAMP},
                last_alert_time = #{index.lastAlertTime,jdbcType=TIMESTAMP},
                index_status = 'pending',
                document = #{index.document,jdbcType=LONGVARCHAR},
                embedding_key = #{index.embeddingKey,jdbcType=VARCHAR},
                embedding_model = #{index.embeddingModel,jdbcType=VARCHAR},
                embedding_vector_hash = NULL,
                retry_count = 0,
                last_error = NULL,
                indexed_at = NULL,
                index_generation_id = #{index.indexGenerationId,jdbcType=VARCHAR},
                next_retry_at = NULL,
                claim_token = NULL,
                claimed_at = NULL,
                claim_expires_at = NULL,
                version = version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE review_item_id = #{index.reviewItemId,jdbcType=BIGINT}
              AND deleted = 0
              AND (
                index_status <> 'processing'
                OR claim_token IS NULL
                OR claim_expires_at IS NULL
                OR claim_expires_at <= #{queuedAt,jdbcType=TIMESTAMP}
              )
            """)
    int queueReindexUnlessActivelyClaimed(
            @Param("index") SupervisionAlertReviewSemanticIndexDO index,
            @Param("queuedAt") LocalDateTime queuedAt);

    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            <script>
            UPDATE system_supervision_alert_review_semantic_index AS target
            SET index_status = 'processing',
                claim_token = #{claimToken,jdbcType=VARCHAR},
                claimed_at = #{claimedAt,jdbcType=TIMESTAMP},
                claim_expires_at = #{claimExpiresAt,jdbcType=TIMESTAMP},
                update_time = CURRENT_TIMESTAMP
            WHERE target.tenant_id = #{tenantId,jdbcType=BIGINT}
              AND target.id IN (
                SELECT candidate.id
                FROM system_supervision_alert_review_semantic_index AS candidate
                WHERE candidate.tenant_id = #{tenantId,jdbcType=BIGINT}
                  AND candidate.deleted = 0
                  AND candidate.review_item_id IN
                  <foreach collection="reviewItemIds" item="reviewItemId" open="(" separator="," close=")">
                    #{reviewItemId,jdbcType=BIGINT}
                  </foreach>
                  AND (
                    index_status = 'pending'
                    OR (
                      index_status = 'failed'
                      AND (next_retry_at IS NULL OR next_retry_at &lt;= #{claimedAt,jdbcType=TIMESTAMP})
                    )
                    OR (
                      index_status = 'processing'
                      AND (claim_expires_at IS NULL OR claim_expires_at &lt;= #{claimedAt,jdbcType=TIMESTAMP})
                    )
                  )
                ORDER BY candidate.update_time ASC, candidate.id ASC
                LIMIT #{limit,jdbcType=INTEGER}
                FOR UPDATE SKIP LOCKED
            )
            </script>
            """)
    int claimProcessable(@Param("tenantId") Long tenantId,
                         @Param("reviewItemIds") List<Long> reviewItemIds,
                         @Param("limit") Integer limit,
                         @Param("claimToken") String claimToken,
                         @Param("claimedAt") LocalDateTime claimedAt,
                         @Param("claimExpiresAt") LocalDateTime claimExpiresAt);

    default List<SupervisionAlertReviewSemanticIndexDO> selectClaimed(String claimToken, Integer limit) {
        int normalizedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewSemanticIndexDO>()
                .eq(SupervisionAlertReviewSemanticIndexDO::getIndexStatus, "processing")
                .eq(SupervisionAlertReviewSemanticIndexDO::getClaimToken, claimToken)
                .orderByAsc(SupervisionAlertReviewSemanticIndexDO::getClaimedAt)
                .orderByAsc(SupervisionAlertReviewSemanticIndexDO::getId)
                .last("LIMIT " + normalizedLimit));
    }

    @Update("""
            UPDATE system_supervision_alert_review_semantic_index
            SET camera_id = #{index.cameraId,jdbcType=VARCHAR},
                first_alert_time = #{index.firstAlertTime,jdbcType=TIMESTAMP},
                last_alert_time = #{index.lastAlertTime,jdbcType=TIMESTAMP},
                index_status = #{index.indexStatus,jdbcType=VARCHAR},
                document = #{index.document,jdbcType=LONGVARCHAR},
                embedding_key = #{index.embeddingKey,jdbcType=VARCHAR},
                embedding_model = #{index.embeddingModel,jdbcType=VARCHAR},
                embedding_vector_hash = #{index.embeddingVectorHash,jdbcType=VARCHAR},
                retry_count = #{index.retryCount,jdbcType=INTEGER},
                last_error = #{index.lastError,jdbcType=LONGVARCHAR},
                indexed_at = #{index.indexedAt,jdbcType=TIMESTAMP},
                index_generation_id = #{index.indexGenerationId,jdbcType=VARCHAR},
                next_retry_at = #{index.nextRetryAt,jdbcType=TIMESTAMP},
                claim_token = NULL,
                claimed_at = NULL,
                claim_expires_at = NULL,
                version = version + 1,
                update_time = CURRENT_TIMESTAMP
            WHERE review_item_id = #{index.reviewItemId,jdbcType=BIGINT}
              AND index_status = 'processing'
              AND claim_token = #{claimToken,jdbcType=VARCHAR}
              AND deleted = 0
            """)
    int completeClaim(@Param("index") SupervisionAlertReviewSemanticIndexDO index,
                      @Param("claimToken") String claimToken);

}
