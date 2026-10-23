package com.basiclab.iot.system.controller.admin.supervision.vo.task;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskDetailResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Admin - supervision task detail response")
@Data
public class TaskDetailRespVO {

    @Schema(description = "Task ID", example = "2001")
    private Long taskId;

    @Schema(description = "Event ID", example = "1001")
    private Long eventId;

    @Schema(description = "Task status", example = "submitted")
    private String taskStatus;

    @Schema(description = "Accepted user ID", example = "3001")
    private Long acceptedUserId;

    @Schema(description = "Accepted time", example = "2026-06-11T09:35:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime acceptedAt;

    @Schema(description = "Submitted time", example = "2026-06-11T09:50:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime submittedAt;

    @Schema(description = "Handling result category", example = "confirmed_violation")
    private String resultCategory;

    @Schema(description = "Handling note", example = "Handled according to SOP")
    private String handlingNote;

    @Schema(description = "Rework count", example = "1")
    private Integer reworkCount;

    public static TaskDetailRespVO from(TaskDetailResponse response) {
        if (response == null) {
            return null;
        }
        TaskDetailRespVO respVO = new TaskDetailRespVO();
        respVO.setTaskId(response.taskId());
        respVO.setEventId(response.eventId());
        respVO.setTaskStatus(response.taskStatus());
        respVO.setAcceptedUserId(response.acceptedUserId());
        respVO.setAcceptedAt(response.acceptedAt());
        respVO.setSubmittedAt(response.submittedAt());
        respVO.setResultCategory(response.resultCategory());
        respVO.setHandlingNote(response.handlingNote());
        respVO.setReworkCount(response.reworkCount());
        return respVO;
    }

}
