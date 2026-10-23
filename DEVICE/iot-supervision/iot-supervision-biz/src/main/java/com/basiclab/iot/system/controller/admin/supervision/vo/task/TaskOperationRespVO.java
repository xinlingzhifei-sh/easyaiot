package com.basiclab.iot.system.controller.admin.supervision.vo.task;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.OperationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Admin - supervision task operation response")
@Data
public class TaskOperationRespVO {

    @Schema(description = "Whether the operation succeeded", example = "true")
    private boolean success;

    public static TaskOperationRespVO from(OperationResponse response) {
        TaskOperationRespVO respVO = new TaskOperationRespVO();
        respVO.setSuccess(response.success());
        return respVO;
    }

}
