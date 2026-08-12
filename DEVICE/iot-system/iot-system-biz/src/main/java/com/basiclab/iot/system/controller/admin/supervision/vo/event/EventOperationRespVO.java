package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.OperationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Admin - supervision event operation response")
@Data
public class EventOperationRespVO {

    @Schema(description = "Whether the operation succeeded", example = "true")
    private boolean success;

    public static EventOperationRespVO from(OperationResponse response) {
        EventOperationRespVO respVO = new EventOperationRespVO();
        respVO.setSuccess(response.success());
        return respVO;
    }

}
