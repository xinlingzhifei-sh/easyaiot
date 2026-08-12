package com.basiclab.iot.dataset.domain.dataset.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AutoLabelModelReqVO
 *
 * @author reese
 * @email reese
 */
@Data
public class AutoLabelModelReqVO {
    @Schema(description = "AI 模型 ID（直连推理，推荐）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long modelId;

    @Schema(description = "模型服务 ID（已废弃，兼容旧版）")
    @Deprecated
    private Long modelServiceId;
}
