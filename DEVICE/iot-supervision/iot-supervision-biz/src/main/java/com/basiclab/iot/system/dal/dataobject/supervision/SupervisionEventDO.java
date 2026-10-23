package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("system_supervision_event")
@KeySequence("system_supervision_event_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionEventDO extends BaseDO {

    @TableId
    private Long id;
    private String eventNo;
    private Long tenantId;
    private Long orgId;
    private String siteType;
    private String sourceSystem;
    private String sourceAlertId;
    private String sourceAlertType;
    private LocalDateTime sourceAlertTime;
    private String sourcePayloadHash;
    private String deviceId;
    private String cameraId;
    private String locationId;
    private String personId;
    private BigDecimal personConfidence;
    private String eventType;
    private String eventLevel;
    private String eventStatus;
    private Long currentOwnerDeptId;
    private Long currentOwnerUserId;
    private String closeResult;
    private String closeReason;
    private String closeCheckStatus;
    private String evidenceStatus;
    private String sensitivityLevel;
    private String upgradedFromLevel;
    private String upgradeReason;
    private Long mergedIntoEventId;
    private LocalDateTime dispatchedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime handledAt;
    private LocalDateTime recheckedAt;
    private LocalDateTime closedAt;
    private Integer version;

}
