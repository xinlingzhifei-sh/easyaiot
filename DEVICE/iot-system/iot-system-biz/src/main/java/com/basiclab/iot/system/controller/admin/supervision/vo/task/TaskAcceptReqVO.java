package com.basiclab.iot.system.controller.admin.supervision.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Schema(description = "Admin - supervision task accept request")
@Data
public class TaskAcceptReqVO {

    @Schema(description = "Task ID", example = "2001")
    @NotNull(message = "taskId must not be null")
    @Positive(message = "taskId must be positive")
    private Long taskId;

    @Schema(description = "Accepted user ID", example = "3001")
    @NotNull(message = "acceptedUserId must not be null")
    @Positive(message = "acceptedUserId must be positive")
    private Long acceptedUserId;

}
