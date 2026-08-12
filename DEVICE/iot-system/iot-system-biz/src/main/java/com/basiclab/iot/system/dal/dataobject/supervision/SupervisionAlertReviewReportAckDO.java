package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_report_ack")
@KeySequence("system_supervision_alert_review_report_ack_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewReportAckDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    private String reportKey;
    private String reportType;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String reviewItemIds;
    private String acknowledgementStatus;
    private Long acknowledgedBy;
    private LocalDateTime acknowledgedAt;
    private String acknowledgementNote;
    private String metadata;
    private Integer version;

}
