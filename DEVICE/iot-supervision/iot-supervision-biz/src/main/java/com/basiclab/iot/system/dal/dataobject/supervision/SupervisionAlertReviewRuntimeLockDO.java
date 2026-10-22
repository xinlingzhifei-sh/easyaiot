package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_runtime_lock")
@KeySequence("system_supervision_alert_review_runtime_lock_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewRuntimeLockDO extends BaseDO {

    @TableId
    private Long id;
    private String lockName;
    private Long ownerUserId;
    private LocalDateTime lockedUntil;
    private LocalDateTime lastLockedAt;
    private Integer version;

}
