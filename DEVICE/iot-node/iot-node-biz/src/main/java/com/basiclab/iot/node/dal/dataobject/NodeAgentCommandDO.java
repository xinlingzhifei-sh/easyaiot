package com.basiclab.iot.node.dal.dataobject;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.*;

import java.time.LocalDateTime;

@TableName("node_agent_command")
@KeySequence("node_agent_command_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeAgentCommandDO extends BaseDO {

    @TableId
    private Long id;

    private Long nodeId;

    private String commandType;

    private String commandKey;

    private String payloadJson;

    private String status;

    private Integer attemptCount;

    private LocalDateTime leaseUntil;

    private String lastError;

    private String resultJson;

    private LocalDateTime ackedAt;

    private LocalDateTime finishedAt;

}
