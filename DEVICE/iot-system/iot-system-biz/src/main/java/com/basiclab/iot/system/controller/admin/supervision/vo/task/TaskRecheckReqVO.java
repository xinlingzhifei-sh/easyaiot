package com.basiclab.iot.system.controller.admin.supervision.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Admin - supervision task recheck request")
@Data
public class TaskRecheckReqVO {

    @Schema(description = "Task ID", example = "2001")
    private Long taskId;

}
