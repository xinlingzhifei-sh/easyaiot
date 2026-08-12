package com.basiclab.iot.node.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "Agent command result request")
@Data
public class NodeAgentCommandResultReqVO {

    @Schema(description = "Node ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "nodeId is required")
    private Long nodeId;

    @Schema(description = "Agent token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "agentToken is required")
    private String agentToken;

    @Schema(description = "Execution status", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "status is required")
    private String status;

    @Schema(description = "Execution result")
    private Map<String, Object> result;

    @Schema(description = "Error message")
    private String error;

}
