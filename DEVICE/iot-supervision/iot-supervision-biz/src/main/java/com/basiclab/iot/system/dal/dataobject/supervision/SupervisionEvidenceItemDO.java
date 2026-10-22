package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_supervision_evidence_item")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionEvidenceItemDO extends BaseDO {

    private Long id;
    private Long eventId;
    private String sourceType;
    private String materialType;
    private String materialUri;
    private String relatedRecordId;
    private Boolean isRequired;
    private String requiredForLevel;
    private String collectStatus;
    private String missingReason;
    private String sensitivityLevel;

}
