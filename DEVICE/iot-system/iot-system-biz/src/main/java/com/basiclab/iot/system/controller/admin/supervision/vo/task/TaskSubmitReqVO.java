package com.basiclab.iot.system.controller.admin.supervision.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Schema(description = "Admin - supervision task submit request")
@Data
public class TaskSubmitReqVO {

    @Schema(description = "Task ID", example = "2001")
    @NotNull(message = "taskId must not be null")
    @Positive(message = "taskId must be positive")
    private Long taskId;

    @Schema(description = "Handling result category", example = "confirmed_violation")
    @NotBlank(message = "resultCategory must not be blank")
    private String resultCategory;

    @Schema(description = "Handling note", example = "Handled according to SOP")
    @NotBlank(message = "handlingNote must not be blank")
    private String handlingNote;

}
