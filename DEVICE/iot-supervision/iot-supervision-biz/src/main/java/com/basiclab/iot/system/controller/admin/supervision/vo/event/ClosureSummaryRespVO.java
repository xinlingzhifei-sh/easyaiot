package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.ClosureSummaryResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Admin - supervision event closure summary response")
@Data
public class ClosureSummaryRespVO {

    @Schema(description = "Event ID", example = "1001")
    private Long eventId;

    @Schema(description = "Event status", example = "pending_close_check")
    private String eventStatus;

    @Schema(description = "Current task ID", example = "2002")
    private Long taskId;

    @Schema(description = "Current task status", example = "approved")
    private String taskStatus;

    @Schema(description = "Rework count", example = "2")
    private Integer reworkCount;

    @Schema(description = "Close result", example = "normal_closed")
    private String closeResult;

    @Schema(description = "Accepted time", example = "2026-06-11T09:35:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime acceptedAt;

    @Schema(description = "Handled time", example = "2026-06-11T09:50:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime handledAt;

    @Schema(description = "Closed time", example = "2026-06-11T10:10:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime closedAt;

    public static ClosureSummaryRespVO from(ClosureSummaryResponse response) {
        if (response == null) {
            return null;
        }
        ClosureSummaryRespVO respVO = new ClosureSummaryRespVO();
        respVO.setEventId(response.eventId());
        respVO.setEventStatus(response.eventStatus());
        respVO.setTaskId(response.taskId());
        respVO.setTaskStatus(response.taskStatus());
        respVO.setReworkCount(response.reworkCount());
        respVO.setCloseResult(response.closeResult());
        respVO.setAcceptedAt(response.acceptedAt());
        respVO.setHandledAt(response.handledAt());
        respVO.setClosedAt(response.closedAt());
        return respVO;
    }

}
