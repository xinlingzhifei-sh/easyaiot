package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventDetailResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Admin - supervision event detail response")
@Data
public class EventDetailRespVO {

    @Schema(description = "Event ID", example = "1001")
    private Long eventId;

    @Schema(description = "Source system", example = "video")
    private String sourceSystem;

    @Schema(description = "Source alert ID", example = "alert-001")
    private String sourceAlertId;

    @Schema(description = "Matched rule code", example = "RULE_ABNORMAL_GATHERING")
    private String ruleCode;

    @Schema(description = "Event type", example = "crowd_gathering")
    private String eventType;

    @Schema(description = "Event level", example = "L3")
    private String eventLevel;

    @Schema(description = "Event status", example = "closed")
    private String eventStatus;

    @Schema(description = "Close result", example = "normal_closed")
    private String closeResult;

    @Schema(description = "Created time", example = "2026-06-11T09:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Schema(description = "Accepted time", example = "2026-06-11T09:35:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime acceptedAt;

    @Schema(description = "Handled time", example = "2026-06-11T09:50:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime handledAt;

    @Schema(description = "Closed time", example = "2026-06-11T10:10:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime closedAt;

    public static EventDetailRespVO from(EventDetailResponse response) {
        if (response == null) {
            return null;
        }
        EventDetailRespVO respVO = new EventDetailRespVO();
        respVO.setEventId(response.eventId());
        respVO.setSourceSystem(response.sourceSystem());
        respVO.setSourceAlertId(response.sourceAlertId());
        respVO.setRuleCode(response.ruleCode());
        respVO.setEventType(response.eventType());
        respVO.setEventLevel(response.eventLevel());
        respVO.setEventStatus(response.eventStatus());
        respVO.setCloseResult(response.closeResult());
        respVO.setCreatedAt(response.createdAt());
        respVO.setAcceptedAt(response.acceptedAt());
        respVO.setHandledAt(response.handledAt());
        respVO.setClosedAt(response.closedAt());
        return respVO;
    }

}
