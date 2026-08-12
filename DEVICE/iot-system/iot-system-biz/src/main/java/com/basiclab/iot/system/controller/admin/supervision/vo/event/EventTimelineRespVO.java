package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventTimelineItemResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Admin - supervision event timeline item response")
@Data
public class EventTimelineRespVO {

    @Schema(description = "Event ID", example = "1001")
    private Long eventId;

    @Schema(description = "Timeline item type", example = "event_created")
    private String timelineType;

    @Schema(description = "Timeline item status", example = "closed")
    private String timelineStatus;

    @Schema(description = "Related record ID", example = "1001")
    private String relatedRecordId;

    @Schema(description = "Timeline item occurred time", example = "2026-06-11T09:30:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    public static EventTimelineRespVO from(EventTimelineItemResponse response) {
        EventTimelineRespVO respVO = new EventTimelineRespVO();
        respVO.setEventId(response.eventId());
        respVO.setTimelineType(response.timelineType());
        respVO.setTimelineStatus(response.timelineStatus());
        respVO.setRelatedRecordId(response.relatedRecordId());
        respVO.setOccurredAt(response.occurredAt());
        return respVO;
    }

}
