package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("system_supervision_alert_review_ingest_identity")
@KeySequence("system_supervision_alert_review_ingest_identity_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewIngestIdentityDO extends BaseDO {

    @TableId
    private Long id;
    private Long tenantId;
    private Long reviewItemId;
    private String sourceSystem;
    private String identityKey;
    private String sourceAlertId;
    private String sourcePayloadHash;

}
