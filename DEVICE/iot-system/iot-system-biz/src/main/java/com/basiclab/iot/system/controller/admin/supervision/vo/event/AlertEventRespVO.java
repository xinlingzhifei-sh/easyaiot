package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 告警转监管事件 Response VO")
@Data
public class AlertEventRespVO {

    @Schema(description = "监管事件编号", example = "1001")
    private Long eventId;

    @Schema(description = "来源系统", example = "video")
    private String sourceSystem;

    @Schema(description = "来源告警编号", example = "alert-001")
    private String sourceAlertId;

    @Schema(description = "处置规则编码", example = "abnormal_gathering")
    private String ruleCode;

    @Schema(description = "事件类型", example = "crowd_gathering")
    private String eventType;

    @Schema(description = "事件等级", example = "L3")
    private String eventLevel;

    @Schema(description = "事件状态", example = "dispatched")
    private String eventStatus;

    @Schema(description = "是否复用未关闭事件", example = "false")
    private boolean reused;

    public static AlertEventRespVO from(AlertEventResponse response) {
        AlertEventRespVO respVO = new AlertEventRespVO();
        respVO.setEventId(response.eventId());
        respVO.setSourceSystem(response.sourceSystem());
        respVO.setSourceAlertId(response.sourceAlertId());
        respVO.setRuleCode(response.ruleCode());
        respVO.setEventType(response.eventType());
        respVO.setEventLevel(response.eventLevel());
        respVO.setEventStatus(response.eventStatus());
        respVO.setReused(response.reused());
        return respVO;
    }

}
