package com.basiclab.iot.node.service;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.domain.vo.NodeAgentTokenRotateRespVO;

/**
 * 节点 Agent 令牌：库中只存加盐哈希用于校验，另存应用层密钥加密的密文供控制面外发时取回明文。
 */
public interface NodeAgentCredentialService {

    /**
     * 为尚未入库的新节点签发令牌：把哈希/密文写到 node 上，返回明文（只在创建响应里展示）。
     */
    String issue(ComputeNodeDO node);

    /**
     * 校验 Agent → 控制面请求携带的令牌：当前令牌，或宽限期内的上一令牌。
     *
     * @throws com.basiclab.iot.common.exception.ServiceException 节点不存在或令牌无效
     */
    ComputeNodeDO authenticate(Long nodeId, String agentToken);

    /**
     * 当前令牌明文（写 agent.env、平台 bootstrap、管理端展示）；节点没有令牌时返回 null。
     */
    String currentToken(ComputeNodeDO node);

    /**
     * 宽限期内仍有效的上一令牌明文；没有时返回 null。
     */
    String previousToken(ComputeNodeDO node);

    /**
     * 控制面调用 Agent 9100 时放在 X-Agent-Token 的令牌：轮换后 Agent 尚未切换时用上一令牌。
     */
    String dispatchToken(ComputeNodeDO node);

    /**
     * 轮换令牌。immediate=false 时旧令牌（Agent 实际持有的那个）在宽限期内仍有效；
     * immediate=true 时旧令牌立即失效（疑似泄露时使用）。不负责推送，pushed 恒为 false。
     */
    NodeAgentTokenRotateRespVO rotate(ComputeNodeDO node, boolean immediate);

    /**
     * Agent 报告轮换失败（已回滚到旧令牌）时，把控制面恢复为旧令牌。
     * generation 已不是当前代次或已确认切换时不做任何事。
     *
     * @return 是否回滚
     */
    boolean revertRotation(Long nodeId, int generation);

    /**
     * 启动时把旧版明文令牌迁移为哈希 + 密文（含已逻辑删除的行）；可重入，多副本并发安全。
     *
     * @return 本次迁移的行数
     */
    int migrateLegacyTokens();
}
