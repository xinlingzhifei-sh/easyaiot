package com.basiclab.iot.node.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "Agent command response")
@Data
public class NodeAgentCommandRespVO {

    @Schema(description = "Command ID")
    private Long id;

    @Schema(description = "Node ID")
    private Long nodeId;

    @Schema(description = "Command type")
    private String commandType;

    @Schema(description = "Idempotency command key")
    private String commandKey;

    @Schema(description = "Command payload")
    private Map<String, Object> payload;

    @Schema(description = "Status")
    private String status;

    @Schema(description = "Attempt count")
    private Integer attemptCount;

    @Schema(description = "Lease expiry time")
    private LocalDateTime leaseUntil;

    @Schema(description = "Last error")
    private String lastError;

    @Schema(description = "Command result")
    private Map<String, Object> result;

    @Schema(description = "Acked time")
    private LocalDateTime ackedAt;

    @Schema(description = "Finished time")
    private LocalDateTime finishedAt;

    @Schema(description = "Created time")
    private LocalDateTime createTime;

    @Schema(description = "Updated time")
    private LocalDateTime updateTime;

}
