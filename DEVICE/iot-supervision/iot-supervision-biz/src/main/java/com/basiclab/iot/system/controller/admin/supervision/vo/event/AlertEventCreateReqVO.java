package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 告警转监管事件 Request VO")
@Data
public class AlertEventCreateReqVO {

    @Schema(description = "来源系统", example = "video")
    @NotBlank(message = "sourceSystem must not be blank")
    private String sourceSystem;

    @Schema(description = "来源告警编号", example = "alert-001")
    @NotBlank(message = "sourceAlertId must not be blank")
    private String sourceAlertId;

    @Schema(description = "处置规则编码", example = "abnormal_gathering")
    @NotBlank(message = "ruleCode must not be blank")
    private String ruleCode;

    @Schema(description = "来源告警类型", example = "abnormal_gathering")
    private String sourceAlertType;

    @Schema(description = "来源告警时间")
    private LocalDateTime sourceAlertTime;

    @Schema(description = "来源载荷哈希", example = "payload-hash-001")
    private String sourcePayloadHash;

}
