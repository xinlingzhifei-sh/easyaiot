package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_runtime_outbox")
@KeySequence("system_supervision_alert_review_runtime_outbox_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewRuntimeOutboxDO extends BaseDO {

    @TableId
    private Long id;
    private String runId;
    private String eventType;
    private String alertKey;
    private String payload;
    private String outboxStatus;
    private String claimToken;
    private Long claimedBy;
    private LocalDateTime claimedAt;
    private Long operatorUserId;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
    private Integer retryCount;
    private String lastError;
    private Integer version;

}
