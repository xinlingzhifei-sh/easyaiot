package com.basiclab.iot.node.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Schema(description = "Agent command ack request")
@Data
public class NodeAgentCommandAckReqVO {

    @Schema(description = "Node ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "nodeId is required")
    private Long nodeId;

    @Schema(description = "Agent token", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "agentToken is required")
    private String agentToken;

}
