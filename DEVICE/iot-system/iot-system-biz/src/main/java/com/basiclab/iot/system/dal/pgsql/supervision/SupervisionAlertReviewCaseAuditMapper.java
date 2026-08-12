package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseAuditDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupervisionAlertReviewCaseAuditMapper extends BaseMapperX<SupervisionAlertReviewCaseAuditDO> {

    @Select("""
            <script>
            SELECT audit.*
            FROM system_supervision_alert_review_case_audit audit
            WHERE audit.tenant_id = #{tenantId,jdbcType=BIGINT}
              AND audit.deleted = 0
              AND audit.action_type IN ('export_downloaded', 'media_access_granted', 'media_access_denied')
            <if test="reviewCaseId != null">
              AND audit.review_case_id = #{reviewCaseId,jdbcType=BIGINT}
            </if>
            <if test="reviewItemId != null">
              AND (
                audit.review_item_id = #{reviewItemId,jdbcType=BIGINT}
                OR (
                  audit.action_type = 'export_downloaded'
                  AND EXISTS (
                    SELECT 1
                    FROM system_supervision_alert_review_export_job item_job
                    WHERE item_job.tenant_id = #{tenantId,jdbcType=BIGINT}
                      AND item_job.deleted = 0
                      AND item_job.review_case_id = audit.review_case_id
                      AND EXISTS (
                        SELECT 1
                        FROM unnest(string_to_array(COALESCE(item_job.review_item_ids, ''), ',')) AS item_job_token(value)
                        WHERE CASE WHEN btrim(item_job_token.value) ~ '^[0-9]{1,30}$'
                                   THEN btrim(item_job_token.value)::NUMERIC END = #{reviewItemId,jdbcType=BIGINT}
                        )
                      AND item_job.job_no = substring(audit.action_note FROM 'jobNo=([^;]+)')
                  )
                )
              )
            </if>
            <if test="eventId != null">
              AND (
                EXISTS (
                  SELECT 1
                  FROM system_supervision_alert_review_item event_item
                  WHERE event_item.tenant_id = #{tenantId,jdbcType=BIGINT}
                    AND event_item.deleted = 0
                    AND event_item.id = audit.review_item_id
                    AND event_item.event_id = #{eventId,jdbcType=BIGINT}
                )
                OR EXISTS (
                  SELECT 1
                  FROM system_supervision_alert_review_export_job event_job
                  WHERE event_job.tenant_id = #{tenantId,jdbcType=BIGINT}
                    AND event_job.deleted = 0
                    AND (
                      EXISTS (
                        SELECT 1
                        FROM unnest(string_to_array(COALESCE(event_job.bound_event_ids, ''), ',')) AS event_job_event_token(value)
                        WHERE CASE WHEN btrim(event_job_event_token.value) ~ '^[0-9]{1,30}$'
                                   THEN btrim(event_job_event_token.value)::NUMERIC END = #{eventId,jdbcType=BIGINT}
                        )
                      OR EXISTS (
                        SELECT 1
                        FROM system_supervision_alert_review_item exported_event_item
                        WHERE exported_event_item.tenant_id = #{tenantId,jdbcType=BIGINT}
                          AND exported_event_item.deleted = 0
                          AND exported_event_item.event_id = #{eventId,jdbcType=BIGINT}
                          AND EXISTS (
                            SELECT 1
                            FROM unnest(string_to_array(COALESCE(event_job.review_item_ids, ''), ',')) AS exported_event_item_token(value)
                            WHERE CASE WHEN btrim(exported_event_item_token.value) ~ '^[0-9]{1,30}$'
                                       THEN btrim(exported_event_item_token.value)::NUMERIC END = exported_event_item.id
                            )
                      )
                    )
                    AND (
                      EXISTS (
                        SELECT 1
                        FROM unnest(string_to_array(COALESCE(event_job.review_item_ids, ''), ',')) AS event_job_item_token(value)
                        WHERE CASE WHEN btrim(event_job_item_token.value) ~ '^[0-9]{1,30}$'
                                   THEN btrim(event_job_item_token.value)::NUMERIC END = audit.review_item_id
                        )
                      OR (
                        audit.action_type = 'export_downloaded'
                        AND event_job.job_no = substring(audit.action_note FROM 'jobNo=([^;]+)')
                      )
                    )
                )
              )
            </if>
            <if test="exportJobNo != null and exportJobNo != ''">
              AND EXISTS (
                SELECT 1
                FROM system_supervision_alert_review_export_job requested_job
                WHERE requested_job.tenant_id = #{tenantId,jdbcType=BIGINT}
                  AND requested_job.deleted = 0
                  AND requested_job.job_no = #{exportJobNo,jdbcType=VARCHAR}
                  AND (
                    EXISTS (
                      SELECT 1
                      FROM unnest(string_to_array(COALESCE(requested_job.review_item_ids, ''), ',')) AS requested_job_item_token(value)
                      WHERE CASE WHEN btrim(requested_job_item_token.value) ~ '^[0-9]{1,30}$'
                                 THEN btrim(requested_job_item_token.value)::NUMERIC END = audit.review_item_id
                      )
                    OR (
                      audit.action_type = 'export_downloaded'
                      AND requested_job.job_no = substring(audit.action_note FROM 'jobNo=([^;]+)')
                    )
                  )
              )
            </if>
            ORDER BY audit.happened_at ASC, audit.id ASC
            LIMIT 500
            </script>
            """)
    List<SupervisionAlertReviewCaseAuditDO> selectEvidenceAuditLookup(@Param("tenantId") Long tenantId,
                                                                      @Param("eventId") Long eventId,
                                                                      @Param("reviewCaseId") Long reviewCaseId,
                                                                      @Param("reviewItemId") Long reviewItemId,
                                                                      @Param("exportJobNo") String exportJobNo);

    @Select("""
            SELECT *
            FROM system_supervision_alert_review_case_audit
            WHERE tenant_id = #{tenantId}
              AND action_type = #{actionType}
              AND metadata IS NOT NULL
              AND position(
                    '"operationId":"' || #{operationId,jdbcType=VARCHAR} || '"'
                    IN metadata
                  ) > 0
              AND deleted = 0
            ORDER BY id ASC
            """)
    List<SupervisionAlertReviewCaseAuditDO> selectByOperationId(@Param("tenantId") Long tenantId,
                                                               @Param("actionType") String actionType,
                                                               @Param("operationId") String operationId);

    @Select("""
            SELECT *
            FROM system_supervision_alert_review_case_audit
            WHERE tenant_id = #{tenantId,jdbcType=BIGINT}
              AND action_type IN (
                'semantic_trigger_evaluated',
                'semantic_trigger_confirmed',
                'semantic_trigger_rejected'
              )
              AND substring(metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"')
                    = #{evaluationId,jdbcType=VARCHAR}
              AND position('"schemaVersion":"semantic-trigger-evaluation-v1"' IN metadata) > 0
              AND (
                action_type = 'semantic_trigger_evaluated'
                OR (
                  action_type = 'semantic_trigger_confirmed'
                  AND position('"humanConfirmationStatus":"confirmed"' IN metadata) > 0
                )
                OR (
                  action_type = 'semantic_trigger_rejected'
                  AND position('"humanConfirmationStatus":"rejected"' IN metadata) > 0
                )
              )
              AND deleted = 0
            ORDER BY id ASC
            LIMIT 3
            """)
    List<SupervisionAlertReviewCaseAuditDO> selectSemanticTriggerAudits(
            @Param("tenantId") Long tenantId,
            @Param("evaluationId") String evaluationId);

    @Insert("""
            INSERT INTO system_supervision_alert_review_case_audit(
              tenant_id,
              review_case_id,
              review_item_id,
              action_type,
              action_note,
              metadata,
              operator_user_id,
              happened_at,
              version
            )
            SELECT
              #{tenantId,jdbcType=BIGINT},
              NULL,
              pending.review_item_id,
              #{actionType,jdbcType=VARCHAR},
              #{actionNote,jdbcType=VARCHAR},
              #{metadata,jdbcType=VARCHAR},
              #{operatorUserId,jdbcType=BIGINT},
              #{happenedAt,jdbcType=TIMESTAMP},
              0
            FROM system_supervision_alert_review_case_audit pending
            WHERE pending.tenant_id = #{tenantId,jdbcType=BIGINT}
              AND pending.action_type = 'semantic_trigger_evaluated'
              AND substring(pending.metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"')
                    = #{evaluationId,jdbcType=VARCHAR}
              AND position('"schemaVersion":"semantic-trigger-evaluation-v1"' IN pending.metadata) > 0
              AND pending.deleted = 0
              AND NOT EXISTS (
                SELECT 1
                FROM system_supervision_alert_review_case_audit terminal
                WHERE terminal.tenant_id = #{tenantId,jdbcType=BIGINT}
                  AND terminal.action_type IN (
                    'semantic_trigger_confirmed',
                    'semantic_trigger_rejected'
                  )
                  AND substring(terminal.metadata FROM '"evaluationId":"(sem-[0-9a-f-]{36})"')
                        = #{evaluationId,jdbcType=VARCHAR}
                  AND position('"schemaVersion":"semantic-trigger-evaluation-v1"' IN terminal.metadata) > 0
                  AND (
                    (
                      terminal.action_type = 'semantic_trigger_confirmed'
                      AND position('"humanConfirmationStatus":"confirmed"' IN terminal.metadata) > 0
                    )
                    OR (
                      terminal.action_type = 'semantic_trigger_rejected'
                      AND position('"humanConfirmationStatus":"rejected"' IN terminal.metadata) > 0
                    )
                  )
                  AND terminal.deleted = 0
              )
            ORDER BY pending.id ASC
            LIMIT 1
            ON CONFLICT DO NOTHING
            """)
    int insertSemanticTriggerDecision(@Param("tenantId") Long tenantId,
                                      @Param("evaluationId") String evaluationId,
                                      @Param("actionType") String actionType,
                                      @Param("actionNote") String actionNote,
                                      @Param("metadata") String metadata,
                                      @Param("operatorUserId") Long operatorUserId,
                                      @Param("happenedAt") LocalDateTime happenedAt);

    default List<SupervisionAlertReviewCaseAuditDO> selectByCaseId(Long reviewCaseId) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewCaseAuditDO>()
                .eq(SupervisionAlertReviewCaseAuditDO::getReviewCaseId, reviewCaseId)
                .orderByAsc(SupervisionAlertReviewCaseAuditDO::getHappenedAt)
                .orderByAsc(SupervisionAlertReviewCaseAuditDO::getId));
    }

    default List<SupervisionAlertReviewCaseAuditDO> selectByReviewItemId(Long reviewItemId) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewCaseAuditDO>()
                .eq(SupervisionAlertReviewCaseAuditDO::getReviewItemId, reviewItemId)
                .orderByAsc(SupervisionAlertReviewCaseAuditDO::getHappenedAt)
                .orderByAsc(SupervisionAlertReviewCaseAuditDO::getId));
    }

}
