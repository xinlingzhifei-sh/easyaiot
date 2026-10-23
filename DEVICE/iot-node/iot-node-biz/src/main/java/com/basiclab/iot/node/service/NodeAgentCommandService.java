package com.basiclab.iot.node.service;

import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;

import java.util.List;

public interface NodeAgentCommandService {

    NodeAgentCommandRespVO enqueue(NodeAgentCommandEnqueueReqVO reqVO);

    /**
     * 查询 commandKey 最近一条指令（不限状态），没有则返回 null。
     */
    NodeAgentCommandRespVO getLatestByCommandKey(String commandKey, Long nodeId);

    List<NodeAgentCommandRespVO> poll(NodeAgentCommandPollReqVO reqVO);

    void ack(Long commandId, NodeAgentCommandAckReqVO reqVO);

    void heartbeat(Long commandId, NodeAgentCommandAckReqVO reqVO);

    void reportResult(Long commandId, NodeAgentCommandResultReqVO reqVO);

    int reclaimTimedOutCommands();

    /**
     * 入队一条令牌轮换推送指令（payload 只含代次，令牌明文在 poll 下发时才解密放入），
     * 并把同节点尚未被领取的旧轮换指令标记为 superseded。
     */
    NodeAgentCommandRespVO enqueueTokenRotation(Long nodeId, int generation);

}
