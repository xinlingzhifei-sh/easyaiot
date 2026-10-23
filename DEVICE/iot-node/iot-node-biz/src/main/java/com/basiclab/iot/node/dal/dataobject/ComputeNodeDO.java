package com.basiclab.iot.node.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.basiclab.iot.common.core.dataobject.BaseDO;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@TableName(value = "compute_node", autoResultMap = true)
@KeySequence("compute_node_id_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComputeNodeDO extends BaseDO {

    @TableId
    private Long id;

    private String name;

    private String host;

    private Integer sshPort;

    private Integer agentPort;

    private String status;

    private String nodeRole;

    private String region;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> tags;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Boolean> capabilities;

    private Integer maxGpuCount;

    private Integer maxTaskCount;

    private Integer weight;

    /**
     * 旧版明文令牌列。迁移后为 NULL；仅在未配置加密密钥或 retain-plaintext 时保留。
     * 令牌相关字段一律不经 updateById 写入（见 ComputeNodeMapper#updateAgentCredential），
     * 避免心跳等整行回写把刚轮换的令牌覆盖回旧值。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String agentToken;

    /** 当前令牌的加盐哈希（hs256$盐$MAC） */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String agentTokenHash;

    /** 当前令牌密文（gcm1$...），供调用 Agent 9100、写 agent.env 时取回明文 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String agentTokenCipher;

    /** 轮换前的令牌，宽限期内仍有效 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String agentTokenPrevHash;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String agentTokenPrevCipher;

    @TableField(updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime agentTokenPrevExpiresAt;

    /** 已轮换但尚未见到 Agent 用新令牌回连 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Boolean agentTokenPending;

    /** 轮换代次 */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private Integer agentTokenGeneration;

    /** Agent 在指令轮询中声明的能力：支持的指令类型、inbound 是否开放 */
    @TableField(typeHandler = JacksonTypeHandler.class, updateStrategy = FieldStrategy.NEVER)
    private Map<String, Boolean> agentFeatures;

    private String remark;

    private LocalDateTime lastHeartbeatAt;

    /** 所属中心节点（平台节点）ID */
    private Long controlPlaneId;

    /** 录像物理存储模式：central_shared / edge_local */
    private String recordingStorageMode;

    /** 存储模式应用状态：active / applying / error */
    private String recordingStorageState;

    /** 存储模式代次，切换时递增 */
    private Long recordingStorageGeneration;

    /** 客户端可安全直连的边缘媒体地址 */
    private String mediaPublicUrl;

    private LocalDateTime recordingStorageUpdatedAt;

    private String recordingStorageError;

}
