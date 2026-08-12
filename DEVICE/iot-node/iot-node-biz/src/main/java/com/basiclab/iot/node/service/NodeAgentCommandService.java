package com.basiclab.iot.node.service;

import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;

import java.util.List;

public interface NodeAgentCommandService {

    NodeAgentCommandRespVO enqueue(NodeAgentCommandEnqueueReqVO reqVO);

    List<NodeAgentCommandRespVO> poll(NodeAgentCommandPollReqVO reqVO);

    void ack(Long commandId, NodeAgentCommandAckReqVO reqVO);

    void heartbeat(Long commandId, NodeAgentCommandAckReqVO reqVO);

    void reportResult(Long commandId, NodeAgentCommandResultReqVO reqVO);

    int reclaimTimedOutCommands();

}
