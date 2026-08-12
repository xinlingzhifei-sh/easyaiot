package com.basiclab.iot.system.dal.dataobject.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@TableName("system_supervision_alert_review_semantic_index")
@KeySequence("system_supervision_alert_review_semantic_index_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class SupervisionAlertReviewSemanticIndexDO extends BaseDO {

    @TableId
    private Long id;
    private Long reviewItemId;
    private String cameraId;
    private LocalDateTime firstAlertTime;
    private LocalDateTime lastAlertTime;
    private String indexStatus;
    private String document;
    private String embeddingKey;
    private String embeddingModel;
    private String embeddingVectorHash;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime indexedAt;
    private String indexGenerationId;
    private String claimToken;
    private LocalDateTime claimedAt;
    private LocalDateTime claimExpiresAt;
    private LocalDateTime nextRetryAt;
    private Integer version;

}
