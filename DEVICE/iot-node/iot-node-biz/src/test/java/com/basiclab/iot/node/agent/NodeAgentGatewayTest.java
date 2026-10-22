package com.basiclab.iot.node.agent;

import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.service.NodeAgentCredentialService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_COMMAND_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NodeAgentGatewayTest {

    private NodeAgentCredentialService credentials;
    private NodeAgentGateway gateway;
    private ComputeNodeDO node;
    private final List<String[]> calls = new ArrayList<>();
    private NodeAgentGateway.HttpReply reply;

    @BeforeEach
    void setUp() {
        credentials = mock(NodeAgentCredentialService.class);
        gateway = new NodeAgentGateway();
        ReflectionTestUtils.setField(gateway, "nodeAgentCredentialService", credentials);
        gateway.httpTransport = (url, token, body, timeout) -> {
            calls.add(new String[]{url, token, body, String.valueOf(timeout)});
            return reply;
        };
        node = new ComputeNodeDO();
        node.setId(5L);
        node.setHost("10.0.0.5");
        node.setAgentPort(9100);
    }

    @Test
    void sendsDispatchTokenAndReturnsData() {
        when(credentials.dispatchToken(node)).thenReturn("token-the-agent-holds");
        reply = new NodeAgentGateway.HttpReply(200, "{\"code\":0,\"data\":{\"pid\":42}}");

        Object pid = gateway.call(node, "/workload/deploy", Map.of("workloadId", "w1"), 60_000).get("pid");

        assertEquals(42, pid);
        assertEquals("http://10.0.0.5:9100/workload/deploy", calls.get(0)[0]);
        assertEquals("token-the-agent-holds", calls.get(0)[1]);
        assertEquals("{\"workloadId\":\"w1\"}", calls.get(0)[2]);
        assertEquals("60000", calls.get(0)[3]);
    }

    @Test
    void returnsNonObjectDataAsIs() {
        when(credentials.dispatchToken(node)).thenReturn("t");
        reply = new NodeAgentGateway.HttpReply(200, "{\"code\":0,\"data\":[{\"ip\":\"10.0.0.9\"}]}");

        Object data = gateway.callForData(node, "/camera/discover", Map.of(), 90_000);

        assertTrue(data instanceof List<?>);
        assertTrue(gateway.call(node, "/camera/discover", Map.of(), 90_000).isEmpty());
    }

    @Test
    void mapsAgentErrorsToCommandFailed() {
        when(credentials.dispatchToken(node)).thenReturn("t");
        reply = new NodeAgentGateway.HttpReply(400, "{\"code\":401,\"msg\":\"Agent 认证失败\"}");
        ServiceException http = assertThrows(ServiceException.class,
                () -> gateway.call(node, "/media/deploy", Map.of(), 1000));
        assertEquals(AGENT_COMMAND_FAILED.getCode(), http.getCode());
        assertTrue(http.getMessage().contains("HTTP 400"));

        reply = new NodeAgentGateway.HttpReply(200, "{\"code\":1,\"msg\":\"stackType 必填\"}");
        ServiceException business = assertThrows(ServiceException.class,
                () -> gateway.call(node, "/media/deploy", Map.of(), 1000));
        assertTrue(business.getMessage().contains("stackType 必填"));
    }

    @Test
    void transportExceptionsBecomeCommandFailedWithEndpoint() {
        when(credentials.dispatchToken(node)).thenReturn("t");
        gateway.httpTransport = (url, token, body, timeout) -> {
            throw new IllegalStateException("connect timed out");
        };

        ServiceException ex = assertThrows(ServiceException.class,
                () -> gateway.call(node, "/sentinel/probe", Map.of(), 1000));
        assertTrue(ex.getMessage().contains("10.0.0.5:9100"));
        assertTrue(ex.getMessage().contains("connect timed out"));
    }

    @Test
    void refusesToCallWithoutUsableToken() {
        when(credentials.dispatchToken(node)).thenReturn(null);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> gateway.call(node, "/workload/stop", Map.of(), 1000));
        assertEquals(AGENT_COMMAND_FAILED.getCode(), ex.getCode());
        assertTrue(calls.isEmpty());
    }
}
