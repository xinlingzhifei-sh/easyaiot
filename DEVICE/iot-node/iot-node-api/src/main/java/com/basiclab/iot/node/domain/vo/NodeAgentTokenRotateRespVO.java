package com.basiclab.iot.node.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Agent 令牌轮换结果")
@Data
public class NodeAgentTokenRotateRespVO {

    @Schema(description = "新令牌（只在此响应中返回一次明文）")
    private String agentToken;

    @Schema(description = "轮换代次")
    private Integer generation;

    @Schema(description = "是否已经经指令轮询通道推送到节点；false 时需在宽限期内重新部署或手工更新 agent.env")
    private Boolean pushed;

    @Schema(description = "旧令牌失效时间；为空表示旧令牌已立即失效")
    private LocalDateTime previousValidUntil;
}
