package com.basiclab.iot.node.agent;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeAgentCommandMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;
import com.basiclab.iot.node.service.impl.NodeAgentCommandServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeAgentCommandServiceImplTest {

    private static final long NODE_ID = 42L;
    private static final String AGENT_TOKEN = "agent-token";

    private ComputeNodeMapper computeNodeMapper;
    private NodeAgentCommandMapper nodeAgentCommandMapper;
    private NodeAgentCommandServiceImpl service;
    private FakeNodeAgentCommandMapper fakeCommandMapper;

    @BeforeEach
    void setUp() throws Exception {
        ComputeNodeDO node = new ComputeNodeDO();
        node.setId(NODE_ID);
        node.setAgentToken(AGENT_TOKEN);
        computeNodeMapper = proxy(ComputeNodeMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) {
                return NODE_ID == (Long) args[0] ? node : null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        fakeCommandMapper = new FakeNodeAgentCommandMapper();
        nodeAgentCommandMapper = proxy(NodeAgentCommandMapper.class, fakeCommandMapper);
        service = new NodeAgentCommandServiceImpl();
        setField(service, "computeNodeMapper", computeNodeMapper);
        setField(service, "nodeAgentCommandMapper", nodeAgentCommandMapper);
    }

    @Test
    void enqueueCreatesPendingCommandWhenCommandKeyIsNew() {
        NodeAgentCommandEnqueueReqVO reqVO = new NodeAgentCommandEnqueueReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setCommandType("stream_forward.deploy");
        reqVO.setCommandKey("video-100");
        reqVO.setPayload(Map.of("sourceUrl", "rtsp://192.168.1.10/live"));

        NodeAgentCommandRespVO respVO = service.enqueue(reqVO);

        NodeAgentCommandDO command = fakeCommandMapper.inserted.get(0);
        assertEquals(NODE_ID, command.getNodeId());
        assertEquals("stream_forward.deploy", command.getCommandType());
        assertEquals("video-100", command.getCommandKey());
        assertEquals("pending", command.getStatus());
        assertEquals(0, command.getAttemptCount());
        assertEquals("{\"sourceUrl\":\"rtsp://192.168.1.10/live\"}", command.getPayloadJson());
        assertEquals("pending", respVO.getStatus());
    }

    @Test
    void pollLeasesPendingCommandsForValidatedAgent() {
        NodeAgentCommandDO command = command(100L, "pending");
        fakeCommandMapper.pollable = List.of(command);

        NodeAgentCommandPollReqVO reqVO = new NodeAgentCommandPollReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(AGENT_TOKEN);
        reqVO.setMaxCommands(2);

        List<NodeAgentCommandRespVO> commands = service.poll(reqVO);

        assertEquals(1, commands.size());
        assertEquals(100L, commands.get(0).getId());
        assertEquals("leased", command.getStatus());
        assertEquals(1, command.getAttemptCount());
        assertNotNull(command.getLeaseUntil());
        assertEquals(command, fakeCommandMapper.updated.get(0));
    }

    @Test
    void pollFailsExpiredCommandWhenRetryBudgetIsExhausted() {
        NodeAgentCommandDO command = command(100L, "leased");
        command.setAttemptCount(3);
        command.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        fakeCommandMapper.pollable = List.of(command);

        NodeAgentCommandPollReqVO reqVO = new NodeAgentCommandPollReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(AGENT_TOKEN);

        List<NodeAgentCommandRespVO> commands = service.poll(reqVO);

        assertEquals(0, commands.size());
        assertEquals("failed", command.getStatus());
        assertEquals("agent_command_retry_exhausted", command.getLastError());
        assertNotNull(command.getFinishedAt());
        assertEquals(command, fakeCommandMapper.updated.get(0));
    }

    @Test
    void reclaimTimedOutCommandsFailsRunningCommandsPastLease() {
        NodeAgentCommandDO command = command(100L, "running");
        command.setLeaseUntil(LocalDateTime.now().minusMinutes(1));
        fakeCommandMapper.timedOutRunning = List.of(command);

        int reclaimed = service.reclaimTimedOutCommands();

        assertEquals(1, reclaimed);
        assertEquals("failed", command.getStatus());
        assertEquals("agent_command_running_timeout", command.getLastError());
        assertNotNull(command.getFinishedAt());
        assertEquals(command, fakeCommandMapper.updated.get(0));
    }

    @Test
    void heartbeatExtendsRunningCommandLease() {
        NodeAgentCommandDO command = command(100L, "running");
        LocalDateTime previousLease = LocalDateTime.now().minusSeconds(5);
        command.setLeaseUntil(previousLease);
        fakeCommandMapper.byId.put(100L, command);

        NodeAgentCommandAckReqVO reqVO = new NodeAgentCommandAckReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(AGENT_TOKEN);

        service.heartbeat(100L, reqVO);

        assertEquals("running", command.getStatus());
        assertNotNull(command.getLeaseUntil());
        assertTrue(command.getLeaseUntil().isAfter(previousLease));
        assertEquals(command, fakeCommandMapper.updated.get(0));
    }

    @Test
    void ackMarksCommandRunning() {
        NodeAgentCommandDO command = command(100L, "leased");
        fakeCommandMapper.byId.put(100L, command);

        NodeAgentCommandAckReqVO reqVO = new NodeAgentCommandAckReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(AGENT_TOKEN);

        service.ack(100L, reqVO);

        assertEquals("running", command.getStatus());
        assertNotNull(command.getAckedAt());
        assertEquals(command, fakeCommandMapper.updated.get(0));
    }

    @Test
    void reportResultStoresSucceededResult() {
        NodeAgentCommandDO command = command(100L, "running");
        fakeCommandMapper.byId.put(100L, command);

        NodeAgentCommandResultReqVO reqVO = new NodeAgentCommandResultReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(AGENT_TOKEN);
        reqVO.setStatus("succeeded");
        reqVO.setResult(Map.of("pid", 1234));

        service.reportResult(100L, reqVO);

        assertEquals("succeeded", command.getStatus());
        assertEquals("{\"pid\":1234}", command.getResultJson());
        assertNotNull(command.getFinishedAt());
        assertEquals(command, fakeCommandMapper.updated.get(0));
    }

    private NodeAgentCommandDO command(Long id, String status) {
        return NodeAgentCommandDO.builder()
                .id(id)
                .nodeId(NODE_ID)
                .commandType("stream_forward.deploy")
                .commandKey("video-100")
                .payloadJson("{\"sourceUrl\":\"rtsp://192.168.1.10/live\"}")
                .status(status)
                .attemptCount(0)
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class FakeNodeAgentCommandMapper implements java.lang.reflect.InvocationHandler {

        private final Map<Long, NodeAgentCommandDO> byId = new HashMap<>();
        private final List<NodeAgentCommandDO> inserted = new ArrayList<>();
        private final List<NodeAgentCommandDO> updated = new ArrayList<>();
        private List<NodeAgentCommandDO> pollable = List.of();
        private List<NodeAgentCommandDO> timedOutRunning = List.of();

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            switch (method.getName()) {
                case "selectActiveByCommandKey":
                    return inserted.stream()
                            .filter(command -> args[0].equals(command.getNodeId())
                                    && args[1].equals(command.getCommandKey()))
                            .findFirst()
                            .orElse(null);
                case "selectPollable":
                    return pollable;
                case "selectTimedOutRunning":
                    return timedOutRunning;
                case "selectById":
                    return byId.get(args[0]);
                case "insert":
                    inserted.add((NodeAgentCommandDO) args[0]);
                    return 1;
                case "updateById":
                    updated.add((NodeAgentCommandDO) args[0]);
                    return 1;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        }
    }
}
