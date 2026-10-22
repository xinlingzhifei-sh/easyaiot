package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_runtime_run")
@KeySequence("system_supervision_alert_review_runtime_run_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewRuntimeRunDO extends BaseDO {

    @TableId
    private Long id;
    private String runId;
    private String status;
    private Integer attemptCount;
    private String alerts;
    private String recommendedActions;
    private Long operatorUserId;
    private LocalDateTime executedAt;
    private String metadata;
    private Integer version;

}
