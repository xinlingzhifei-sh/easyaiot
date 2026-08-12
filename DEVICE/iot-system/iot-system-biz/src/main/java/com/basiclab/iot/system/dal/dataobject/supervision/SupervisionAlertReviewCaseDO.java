package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_case")
@KeySequence("system_supervision_alert_review_case_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewCaseDO extends BaseDO {

    @TableId
    private Long id;
    private String caseNo;
    private String title;
    private String status;
    private Long primaryReviewItemId;
    private Long ownerUserId;
    private String notes;
    private String cameraIds;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer version;

}
