package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewReportAckDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SupervisionAlertReviewReportAckMapper extends BaseMapperX<SupervisionAlertReviewReportAckDO> {

    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT INTO system_supervision_alert_review_report_ack(
                tenant_id, report_key, report_type, period_start, period_end,
                review_item_ids, acknowledgement_status, acknowledged_by,
                acknowledged_at, acknowledgement_note, metadata, version
            ) VALUES (
                #{tenantId,jdbcType=BIGINT},
                #{ack.reportKey,jdbcType=VARCHAR},
                #{ack.reportType,jdbcType=VARCHAR},
                #{ack.periodStart,jdbcType=TIMESTAMP},
                #{ack.periodEnd,jdbcType=TIMESTAMP},
                #{ack.reviewItemIds,jdbcType=VARCHAR},
                #{ack.acknowledgementStatus,jdbcType=VARCHAR},
                #{ack.acknowledgedBy,jdbcType=BIGINT},
                #{ack.acknowledgedAt,jdbcType=TIMESTAMP},
                #{ack.acknowledgementNote,jdbcType=VARCHAR},
                #{ack.metadata,jdbcType=VARCHAR},
                #{ack.version,jdbcType=INTEGER}
            )
            ON CONFLICT (tenant_id, report_key) WHERE deleted = 0
            DO NOTHING
            """)
    int insertIfAbsent(@Param("tenantId") Long tenantId,
                       @Param("ack") SupervisionAlertReviewReportAckDO ack);

    default SupervisionAlertReviewReportAckDO selectByTenantAndReportKey(Long tenantId, String reportKey) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewReportAckDO>()
                .eq(SupervisionAlertReviewReportAckDO::getTenantId, tenantId)
                .eq(SupervisionAlertReviewReportAckDO::getReportKey, reportKey)
                .last("LIMIT 1"));
    }

}
