package com.basiclab.iot.node.agent;

import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import com.basiclab.iot.node.dal.pgsql.NodeAgentCommandMapper;
import com.basiclab.iot.node.service.NodeAgentCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_COMMAND_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeAgentGatewayQueueTest {

    private NodeAgentGateway gateway;
    private ComputeNodeDO node;
    private final List<NodeAgentCommandDO> inserted = new ArrayList<>();
    private final List<String> operations = new ArrayList<>();
    private int transactions;
    private int httpCalls;
    /** 每次轮询状态前执行，模拟 Agent 的处理进度 */
    private Consumer<NodeAgentCommandDO> agent = command -> { };

    @BeforeEach
    void setUp() {
        gateway = new NodeAgentGateway();
        NodeAgentCredentialService credentials = mock(NodeAgentCredentialService.class);
        when(credentials.dispatchToken(any())).thenReturn("node-token");
        ReflectionTestUtils.setField(gateway, "nodeAgentCredentialService", credentials);
        ReflectionTestUtils.setField(gateway, "nodeAgentCommandMapper", mapper());
        ReflectionTestUtils.setField(gateway, "queuePollIntervalMs", 1L);
        gateway.httpTransport = (url, token, body, timeout) -> {
            httpCalls++;
            return new NodeAgentGateway.HttpReply(200, "{\"code\":0,\"data\":{}}");
        };
        gateway.txRunner = new NodeAgentGateway.TxRunner() {
            @Override
            public <T> T inNewTransaction(Supplier<T> work) {
                transactions++;
                return work.get();
            }
        };
        gateway.sleeper = millis -> { };
        node = new ComputeNodeDO();
        node.setId(4L);
        node.setHost("10.0.0.4");
    }

    private NodeAgentCommandMapper mapper() {
        return (NodeAgentCommandMapper) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{NodeAgentCommandMapper.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "insert" -> {
                            NodeAgentCommandDO command = (NodeAgentCommandDO) args[0];
                            command.setId(100L + inserted.size());
                            inserted.add(command);
                            operations.add("insert");
                            return 1;
                        }
                        case "selectById" -> {
                            NodeAgentCommandDO command = inserted.get(0);
                            agent.accept(command);
                            return command;
                        }
                        case "cancelIfPending" -> {
                            operations.add("cancel");
                            NodeAgentCommandDO command = inserted.get(0);
                            if (!"pending".equals(command.getStatus())) {
                                return 0;
                            }
                            command.setStatus("canceled");
                            return 1;
                        }
                        case "clearPayload" -> {
                            operations.add("clear");
                            inserted.get(0).setPayloadJson("{}");
                            return 1;
                        }
                        case "toString" -> {
                            return "FakeNodeAgentCommandMapper";
                        }
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private void outboundOnly(String... commandTypes) {
        Map<String, Boolean> features = new java.util.HashMap<>();
        features.put("inbound", false);
        for (String type : commandTypes) {
            features.put(type, true);
        }
        node.setAgentFeatures(features);
    }

    @Test
    void mapsPathsToCommandTypesUsedByAgent() {
        assertEquals("workload.deploy", NodeAgentGateway.commandTypeFor("/workload/deploy"));
        assertEquals("camera.scan-segment", NodeAgentGateway.commandTypeFor("/camera/scan-segment"));
        assertEquals("sentinel.validate", NodeAgentGateway.commandTypeFor("/sentinel/validate"));
    }

    @Test
    void outboundOnlyNodeIsCalledThroughQueueAndPayloadIsCleared() {
        outboundOnly("media.deploy");
        agent = command -> {
            command.setStatus("succeeded");
            command.setResultJson("{\"data\":{\"status\":\"running\"}}");
        };

        Object data = gateway.call(node, "/media/deploy", Map.of("env", Map.of("MINIO_SECRET_KEY", "s3cr3t")), 120_000)
                .get("status");

        assertEquals("running", data);
        assertEquals(0, httpCalls, "不再直连 9100");
        NodeAgentCommandDO command = inserted.get(0);
        assertEquals("media.deploy", command.getCommandType());
        assertTrue(command.getCommandKey().startsWith("rpc:media.deploy:"));
        assertEquals("{}", command.getPayloadJson(), "结束后不留存部署参数");
        assertEquals(List.of("insert", "clear"), operations);
        assertTrue(transactions >= 3, "入队、读状态、清理各自在独立事务中");
    }

    @Test
    void queuedFailureSurfacesAgentError() {
        outboundOnly("workload.deploy");
        agent = command -> {
            command.setStatus("failed");
            command.setLastError("command 不能为空");
        };

        ServiceException ex = assertThrows(ServiceException.class,
                () -> gateway.call(node, "/workload/deploy", Map.of(), 60_000));
        assertEquals(AGENT_COMMAND_FAILED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("command 不能为空"));
    }

    @Test
    void unclaimedCommandIsCanceledOnTimeout() {
        outboundOnly("camera.discover");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> gateway.callViaQueue(node, "camera.discover", Map.of(), (int) -NodeAgentGateway.QUEUE_PICKUP_GRACE_MS));
        assertTrue(ex.getMessage().contains("未在超时前领取"));
        assertEquals("canceled", inserted.get(0).getStatus());
        assertEquals(List.of("insert", "cancel", "clear"), operations);
    }

    @Test
    void outboundOnlyAgentWithoutCommandSupportFailsFast() {
        outboundOnly();

        ServiceException ex = assertThrows(ServiceException.class,
                () -> gateway.call(node, "/sentinel/probe", Map.of(), 1000));
        assertTrue(ex.getMessage().contains("sentinel.probe"));
        assertTrue(inserted.isEmpty());
        assertEquals(0, httpCalls);
    }

    @Test
    void agentsWithInboundStillUseHttpUnlessPreferQueue() {
        node.setAgentFeatures(Map.of("inbound", true, "workload.stop", true));
        gateway.call(node, "/workload/stop", Map.of(), 1000);
        assertEquals(1, httpCalls);
        assertTrue(inserted.isEmpty());

        ReflectionTestUtils.setField(gateway, "preferQueue", true);
        agent = command -> {
            command.setStatus("succeeded");
            command.setResultJson("{\"data\":{\"removed\":true}}");
        };
        assertEquals(true, gateway.call(node, "/workload/stop", Map.of(), 1000).get("removed"));
        assertEquals(1, httpCalls);

        node.setAgentFeatures(null);
        assertFalse(gateway.useQueue(node, "workload.stop"), "旧 Agent 未声明能力时走 HTTP");
    }
}
