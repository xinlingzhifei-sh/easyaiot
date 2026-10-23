package com.basiclab.iot.system.controller.admin.supervision.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Schema(description = "Management backend - Supervision task detail request VO")
@Data
public class TaskDetailReqVO {

    @Schema(description = "Task ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "2001")
    @NotNull(message = "taskId must not be null")
    @Positive(message = "taskId must be positive")
    private Long id;

}
