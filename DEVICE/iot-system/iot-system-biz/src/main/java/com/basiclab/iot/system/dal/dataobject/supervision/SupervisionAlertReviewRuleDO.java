package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_rule")
@KeySequence("system_supervision_alert_review_rule_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewRuleDO extends BaseDO {

    @TableId
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String sourceSystem;
    private String cameraId;
    private String zoneCode;
    private String objectLabel;
    private Integer minStaySeconds;
    private Integer inertiaFrames;
    private Integer loiteringSeconds;
    private LocalDateTime activeStart;
    private LocalDateTime activeEnd;
    private Boolean enabled;
    private Integer version;

}
