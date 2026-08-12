package com.basiclab.iot.node.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeAgentCommandMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;
import com.basiclab.iot.node.service.NodeAgentCommandService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_TOKEN_INVALID;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_NOT_EXISTS;

@Service
@Validated
public class NodeAgentCommandServiceImpl implements NodeAgentCommandService {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_LEASED = "leased";
    private static final String STATUS_RUNNING = "running";
    private static final String STATUS_FAILED = "failed";
    private static final String ERROR_RETRY_EXHAUSTED = "agent_command_retry_exhausted";
    private static final String ERROR_RUNNING_TIMEOUT = "agent_command_running_timeout";
    private static final int DEFAULT_MAX_COMMANDS = 5;
    private static final int LEASE_SECONDS = 30;
    private static final int MAX_COMMAND_ATTEMPTS = 3;
    private static final int TIMEOUT_SCAN_LIMIT = 100;

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private NodeAgentCommandMapper nodeAgentCommandMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeAgentCommandRespVO enqueue(NodeAgentCommandEnqueueReqVO reqVO) {
        validateNodeExists(reqVO.getNodeId());
        NodeAgentCommandDO command = nodeAgentCommandMapper.selectActiveByCommandKey(
                reqVO.getNodeId(), reqVO.getCommandKey());
        if (command == null) {
            command = NodeAgentCommandDO.builder()
                    .nodeId(reqVO.getNodeId())
                    .commandType(reqVO.getCommandType())
                    .commandKey(reqVO.getCommandKey())
                    .payloadJson(JSONUtil.toJsonStr(nullToEmpty(reqVO.getPayload())))
                    .status(STATUS_PENDING)
                    .attemptCount(0)
                    .build();
            nodeAgentCommandMapper.insert(command);
            return toRespVO(command);
        }
        if (STATUS_PENDING.equals(command.getStatus())) {
            command.setCommandType(reqVO.getCommandType());
            command.setPayloadJson(JSONUtil.toJsonStr(nullToEmpty(reqVO.getPayload())));
            nodeAgentCommandMapper.updateById(command);
        }
        return toRespVO(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<NodeAgentCommandRespVO> poll(NodeAgentCommandPollReqVO reqVO) {
        validateAgent(reqVO.getNodeId(), reqVO.getAgentToken());
        int maxCommands = reqVO.getMaxCommands() != null ? reqVO.getMaxCommands() : DEFAULT_MAX_COMMANDS;
        LocalDateTime now = LocalDateTime.now();
        List<NodeAgentCommandDO> commands = nodeAgentCommandMapper.selectPollable(reqVO.getNodeId(), now, maxCommands);
        List<NodeAgentCommandRespVO> leasedCommands = new ArrayList<>();
        for (NodeAgentCommandDO command : commands) {
            int attemptCount = command.getAttemptCount() != null ? command.getAttemptCount() : 0;
            if (attemptCount >= MAX_COMMAND_ATTEMPTS) {
                command.setStatus(STATUS_FAILED);
                command.setLastError(ERROR_RETRY_EXHAUSTED);
                command.setLeaseUntil(null);
                command.setFinishedAt(now);
                nodeAgentCommandMapper.updateById(command);
                continue;
            }
            command.setStatus(STATUS_LEASED);
            command.setAttemptCount(attemptCount + 1);
            command.setLeaseUntil(now.plusSeconds(LEASE_SECONDS));
            nodeAgentCommandMapper.updateById(command);
            leasedCommands.add(toRespVO(command));
        }
        return leasedCommands;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ack(Long commandId, NodeAgentCommandAckReqVO reqVO) {
        validateAgent(reqVO.getNodeId(), reqVO.getAgentToken());
        NodeAgentCommandDO command = validateCommand(commandId, reqVO.getNodeId());
        command.setStatus(STATUS_RUNNING);
        command.setAckedAt(LocalDateTime.now());
        nodeAgentCommandMapper.updateById(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(Long commandId, NodeAgentCommandAckReqVO reqVO) {
        validateAgent(reqVO.getNodeId(), reqVO.getAgentToken());
        NodeAgentCommandDO command = validateCommand(commandId, reqVO.getNodeId());
        if (!STATUS_RUNNING.equals(command.getStatus())) {
            return;
        }
        command.setLeaseUntil(LocalDateTime.now().plusSeconds(LEASE_SECONDS));
        nodeAgentCommandMapper.updateById(command);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reportResult(Long commandId, NodeAgentCommandResultReqVO reqVO) {
        validateAgent(reqVO.getNodeId(), reqVO.getAgentToken());
        NodeAgentCommandDO command = validateCommand(commandId, reqVO.getNodeId());
        command.setStatus(reqVO.getStatus());
        command.setResultJson(JSONUtil.toJsonStr(nullToEmpty(reqVO.getResult())));
        command.setLastError(reqVO.getError());
        command.setFinishedAt(LocalDateTime.now());
        nodeAgentCommandMapper.updateById(command);
    }

    @Override
    @Scheduled(fixedDelay = 30000)
    @Transactional(rollbackFor = Exception.class)
    public int reclaimTimedOutCommands() {
        LocalDateTime now = LocalDateTime.now();
        List<NodeAgentCommandDO> commands = nodeAgentCommandMapper.selectTimedOutRunning(now, TIMEOUT_SCAN_LIMIT);
        for (NodeAgentCommandDO command : commands) {
            command.setStatus(STATUS_FAILED);
            command.setLastError(ERROR_RUNNING_TIMEOUT);
            command.setLeaseUntil(null);
            command.setFinishedAt(now);
            nodeAgentCommandMapper.updateById(command);
        }
        return commands.size();
    }

    private void validateNodeExists(Long nodeId) {
        if (computeNodeMapper.selectById(nodeId) == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
    }

    private ComputeNodeDO validateAgent(Long nodeId, String agentToken) {
        ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        if (StrUtil.isBlank(agentToken) || !agentToken.equals(node.getAgentToken())) {
            throw exception(AGENT_TOKEN_INVALID);
        }
        return node;
    }

    private NodeAgentCommandDO validateCommand(Long commandId, Long nodeId) {
        NodeAgentCommandDO command = nodeAgentCommandMapper.selectById(commandId);
        if (command == null || !nodeId.equals(command.getNodeId())) {
            throw new IllegalArgumentException("Agent command not found: " + commandId);
        }
        return command;
    }

    private NodeAgentCommandRespVO toRespVO(NodeAgentCommandDO command) {
        NodeAgentCommandRespVO respVO = new NodeAgentCommandRespVO();
        respVO.setId(command.getId());
        respVO.setNodeId(command.getNodeId());
        respVO.setCommandType(command.getCommandType());
        respVO.setCommandKey(command.getCommandKey());
        respVO.setPayload(parsePayload(command.getPayloadJson()));
        respVO.setStatus(command.getStatus());
        respVO.setAttemptCount(command.getAttemptCount());
        return respVO;
    }

    private Map<String, Object> parsePayload(String payloadJson) {
        if (StrUtil.isBlank(payloadJson)) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(JSONUtil.parseObj(payloadJson));
    }

    private Map<String, Object> nullToEmpty(Map<String, Object> value) {
        return value != null ? value : Collections.emptyMap();
    }
}
