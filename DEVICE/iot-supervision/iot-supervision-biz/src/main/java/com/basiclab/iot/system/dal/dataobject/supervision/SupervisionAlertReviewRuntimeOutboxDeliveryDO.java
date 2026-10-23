package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_runtime_outbox_delivery")
@KeySequence("system_supervision_alert_review_runtime_outbox_delivery_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewRuntimeOutboxDeliveryDO extends BaseDO {

    @TableId
    private Long id;
    private Long outboxId;
    private String eventType;
    private String alertKey;
    private String channel;
    private Long recipientUserId;
    private String templateCode;
    private String deliveryStatus;
    private Long notifyMessageId;
    private Integer attemptCount;
    private String lastError;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime deliveredAt;
    private Integer version;

}
