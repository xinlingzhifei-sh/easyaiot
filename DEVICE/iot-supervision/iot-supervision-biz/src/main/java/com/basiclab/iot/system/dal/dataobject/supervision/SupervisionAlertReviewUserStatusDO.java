package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_user_status")
@KeySequence("system_supervision_alert_review_user_status_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewUserStatusDO extends BaseDO {

    @TableId
    private Long id;
    private Long reviewItemId;
    private Long userId;
    private Boolean hasBeenReviewed;
    private LocalDateTime reviewedAt;
    private Integer version;

}
