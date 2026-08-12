package com.basiclab.iot.node.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

@Schema(description = "Admin agent command enqueue request")
@Data
public class NodeAgentCommandEnqueueReqVO {

    @Schema(description = "Node ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "nodeId is required")
    private Long nodeId;

    @Schema(description = "Command type", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "commandType is required")
    private String commandType;

    @Schema(description = "Idempotency command key", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "commandKey is required")
    private String commandKey;

    @Schema(description = "Command payload")
    private Map<String, Object> payload;

}
