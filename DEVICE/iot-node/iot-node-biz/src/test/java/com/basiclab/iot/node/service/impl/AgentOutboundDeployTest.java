package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.util.AgentDeployUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentOutboundDeployTest {

    @Test
    void envContentCarriesInboundSwitchAndDefaultsToListening() {
        String outbound = AgentDeployUtil.buildEnvContent(5L, "t", 9100, "https://cp/admin-api/node/agent",
                "algorithm", false, false);
        String legacy = AgentDeployUtil.buildEnvContent(5L, "t", 9100, "https://cp/admin-api/node/agent", "algorithm");

        assertTrue(outbound.contains("AGENT_INBOUND_ENABLED=false\n"));
        assertTrue(legacy.contains("AGENT_INBOUND_ENABLED=true\n"));
        assertTrue(outbound.contains("PLATFORM_AGENT=0\n"));
    }

    @Test
    void outboundOnlyVerificationWaitsForFreshHeartbeat() throws Exception {
        LocalDateTime since = LocalDateTime.now();
        ComputeNodeDO node = new ComputeNodeDO();
        node.setId(5L);
        ComputeNodeMapper mapper = mock(ComputeNodeMapper.class);
        when(mapper.selectById(5L)).thenReturn(node);
        ComputeNodeServiceImpl service = new ComputeNodeServiceImpl();
        ReflectionTestUtils.setField(service, "computeNodeMapper", mapper);
        service.heartbeatVerifyTimeoutMs = 50;

        node.setLastHeartbeatAt(since.minusMinutes(5));
        assertFalse(service.waitForHeartbeat(5L, since), "部署前的旧心跳不算");

        node.setLastHeartbeatAt(since.plusSeconds(1));
        assertTrue(service.waitForHeartbeat(5L, since));
    }
}
