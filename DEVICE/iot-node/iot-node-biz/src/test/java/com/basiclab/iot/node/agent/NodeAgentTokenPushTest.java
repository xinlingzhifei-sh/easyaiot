package com.basiclab.iot.node.agent;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeAgentCommandMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentTokenRotateRespVO;
import com.basiclab.iot.node.service.impl.AgentCredentialTestSupport;
import com.basiclab.iot.node.service.impl.ComputeNodeServiceImpl;
import com.basiclab.iot.node.service.impl.NodeAgentCommandServiceImpl;
import com.basiclab.iot.node.service.impl.NodeAgentCredentialServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 令牌轮换经指令轮询通道推送：控制面状态机 + 指令队列端到端（Mapper 用内存假实现）。
 */
class NodeAgentTokenPushTest {

    private static final long NODE_ID = 21L;
    private static final String OLD_TOKEN = "33333333333333333333333333333333";

    private ComputeNodeDO node;
    private final List<NodeAgentCommandDO> commands = new ArrayList<>();
    private NodeAgentCredentialServiceImpl credentials;
    private NodeAgentCommandServiceImpl commandService;
    private ComputeNodeServiceImpl computeNodeService;
    private Map<String, Boolean> recordedFeatures;

    @BeforeEach
    void setUp() throws Exception {
        node = new ComputeNodeDO();
        node.setId(NODE_ID);
        node.setHost("10.0.0.21");
        ComputeNodeMapper nodeMapper = proxy(ComputeNodeMapper.class, (p, m, a) -> switch (m.getName()) {
            case "selectById" -> NODE_ID == (Long) a[0] ? node : null;
            case "updateAgentCredential" -> {
                ComputeNodeDO state = (ComputeNodeDO) a[1];
                String expected = (String) a[2];
                if (expected == null ? node.getAgentTokenHash() != null : !expected.equals(node.getAgentTokenHash())) {
                    yield 0;
                }
                copy(state, node);
                yield 1;
            }
            case "updateAgentFeatures" -> {
                @SuppressWarnings("unchecked")
                Map<String, Boolean> features = (Map<String, Boolean>) a[1];
                recordedFeatures = features;
                yield 1;
            }
            default -> throw new UnsupportedOperationException(m.getName());
        });
        NodeAgentCommandMapper commandMapper = proxy(NodeAgentCommandMapper.class, (p, m, a) -> switch (m.getName()) {
            case "insert" -> {
                NodeAgentCommandDO command = (NodeAgentCommandDO) a[0];
                command.setId((long) commands.size() + 1);
                commands.add(command);
                yield 1;
            }
            case "updateById" -> 1;
            case "selectById" -> commands.stream().filter(c -> c.getId().equals(a[0])).findFirst().orElse(null);
            case "selectPendingByType" -> commands.stream()
                    .filter(c -> c.getCommandType().equals(a[1]) && "pending".equals(c.getStatus()))
                    .collect(Collectors.toList());
            case "selectPollable" -> commands.stream()
                    .filter(c -> "pending".equals(c.getStatus()))
                    .collect(Collectors.toList());
            default -> throw new UnsupportedOperationException(m.getName());
        });
        credentials = AgentCredentialTestSupport.credentialService(nodeMapper);
        AgentCredentialTestSupport.storeToken(credentials, node, OLD_TOKEN);

        commandService = new NodeAgentCommandServiceImpl();
        setField(commandService, "computeNodeMapper", nodeMapper);
        setField(commandService, "nodeAgentCommandMapper", commandMapper);
        setField(commandService, "nodeAgentCredentialService", credentials);

        computeNodeService = new ComputeNodeServiceImpl();
        setField(computeNodeService, "computeNodeMapper", nodeMapper);
        setField(computeNodeService, "nodeAgentCredentialService", credentials);
        setField(computeNodeService, "nodeAgentCommandService", commandService);
    }

    @Test
    void pollRecordsDeclaredAgentFeatures() {
        poll(OLD_TOKEN, Map.of("agent.token.rotate", true, "inbound", false));

        assertEquals(Map.of("agent.token.rotate", true, "inbound", false), recordedFeatures);
        recordedFeatures = null;
        node.setAgentFeatures(Map.of("agent.token.rotate", true, "inbound", false));
        poll(OLD_TOKEN, Map.of("agent.token.rotate", true, "inbound", false));
        assertNull(recordedFeatures, "能力未变化时不写库");
    }

    @Test
    void legacyAgentWithoutFeatureIsNotPushed() {
        NodeAgentTokenRotateRespVO resp = computeNodeService.rotateAgentToken(NODE_ID, false);

        assertFalse(resp.getPushed());
        assertTrue(commands.isEmpty());
        credentials.authenticate(NODE_ID, OLD_TOKEN);
    }

    @Test
    void rotationIsPushedThroughPollWithoutStoringPlaintext() {
        node.setAgentFeatures(Map.of("agent.token.rotate", true));

        NodeAgentTokenRotateRespVO resp = computeNodeService.rotateAgentToken(NODE_ID, false);

        assertTrue(resp.getPushed());
        assertEquals(1, commands.size());
        NodeAgentCommandDO command = commands.get(0);
        assertEquals("agent.token.rotate", command.getCommandType());
        assertEquals("agent.token.rotate:" + resp.getGeneration(), command.getCommandKey());
        assertFalse(command.getPayloadJson().contains(resp.getAgentToken()), "指令表不存令牌明文");

        // 旧令牌仍可 poll（宽限期内），响应里带新令牌
        List<NodeAgentCommandRespVO> leased = poll(OLD_TOKEN, null);
        assertEquals(1, leased.size());
        assertEquals(resp.getAgentToken(), leased.get(0).getPayload().get("agentToken"));
        assertEquals(resp.getGeneration(), leased.get(0).getPayload().get("generation"));
        assertFalse(command.getPayloadJson().contains(resp.getAgentToken()));

        // Agent 切换后用新令牌上报成功 → 控制面确认，9100 改用新令牌
        report(command.getId(), resp.getAgentToken(), "succeeded");
        assertFalse(node.getAgentTokenPending());
        assertEquals(resp.getAgentToken(), credentials.dispatchToken(node));
    }

    @Test
    void failedRotationRevertsControlPlaneToOldToken() {
        node.setAgentFeatures(Map.of("agent.token.rotate", true));
        NodeAgentTokenRotateRespVO resp = computeNodeService.rotateAgentToken(NODE_ID, false);
        Long commandId = commands.get(0).getId();
        poll(OLD_TOKEN, null);

        report(commandId, OLD_TOKEN, "failed");

        assertEquals(OLD_TOKEN, credentials.currentToken(node));
        assertFalse(node.getAgentTokenPending());
        assertThrows(RuntimeException.class, () -> credentials.authenticate(NODE_ID, resp.getAgentToken()));
        credentials.authenticate(NODE_ID, OLD_TOKEN);
    }

    @Test
    void newerRotationSupersedesPendingAndStaleCommands() {
        node.setAgentFeatures(Map.of("agent.token.rotate", true));
        computeNodeService.rotateAgentToken(NODE_ID, false);
        NodeAgentTokenRotateRespVO second = computeNodeService.rotateAgentToken(NODE_ID, false);

        assertEquals("superseded", commands.get(0).getStatus());
        List<NodeAgentCommandRespVO> leased = poll(OLD_TOKEN, null);
        assertEquals(1, leased.size());
        assertEquals(second.getAgentToken(), leased.get(0).getPayload().get("agentToken"));
    }

    @Test
    void stalePendingRotationIsNotDeliveredAfterImmediateRotation() {
        node.setAgentFeatures(Map.of("agent.token.rotate", true));
        computeNodeService.rotateAgentToken(NODE_ID, false);
        NodeAgentTokenRotateRespVO immediate = computeNodeService.rotateAgentToken(NODE_ID, true);

        assertFalse(immediate.getPushed());
        assertTrue(poll(immediate.getAgentToken(), null).isEmpty());
        assertEquals("superseded", commands.get(0).getStatus());
    }

    @Test
    void resultStatusIsRestrictedToTerminalValues() {
        node.setAgentFeatures(Map.of("agent.token.rotate", true));
        computeNodeService.rotateAgentToken(NODE_ID, false);

        assertThrows(IllegalArgumentException.class, () -> report(commands.get(0).getId(), OLD_TOKEN, "pending"));
    }

    private List<NodeAgentCommandRespVO> poll(String token, Map<String, Boolean> capabilities) {
        NodeAgentCommandPollReqVO reqVO = new NodeAgentCommandPollReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(token);
        reqVO.setCapabilities(capabilities);
        return commandService.poll(reqVO);
    }

    private void report(Long commandId, String token, String status) {
        NodeAgentCommandResultReqVO reqVO = new NodeAgentCommandResultReqVO();
        reqVO.setNodeId(NODE_ID);
        reqVO.setAgentToken(token);
        reqVO.setStatus(status);
        reqVO.setResult(new HashMap<>());
        commandService.reportResult(commandId, reqVO);
    }

    private static void copy(ComputeNodeDO from, ComputeNodeDO to) {
        to.setAgentToken(from.getAgentToken());
        to.setAgentTokenHash(from.getAgentTokenHash());
        to.setAgentTokenCipher(from.getAgentTokenCipher());
        to.setAgentTokenPrevHash(from.getAgentTokenPrevHash());
        to.setAgentTokenPrevCipher(from.getAgentTokenPrevCipher());
        to.setAgentTokenPrevExpiresAt(from.getAgentTokenPrevExpiresAt());
        to.setAgentTokenPending(from.getAgentTokenPending());
        to.setAgentTokenGeneration(from.getAgentTokenGeneration());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }
}
