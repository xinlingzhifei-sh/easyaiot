package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeMetricSnapshotMapper;
import com.basiclab.iot.node.dal.pgsql.NodeSshCredentialMapper;
import com.basiclab.iot.node.domain.vo.ComputeNodeRespVO;
import com.basiclab.iot.node.enums.NodeRoleEnum;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComputeNodeServiceImplTest {

    @Test
    void hidesAgentTokenUnlessResponseExplicitlyExposesIt() throws Exception {
        ComputeNodeServiceImpl service = new ComputeNodeServiceImpl();
        setField(service, "nodeSshCredentialMapper", nullReturningSshCredentialMapper());
        setField(service, "nodeMetricSnapshotMapper", nullReturningMetricSnapshotMapper());

        ComputeNodeDO node = new ComputeNodeDO();
        node.setId(7L);
        node.setName("worker-7");
        node.setAgentToken("secret-agent-token");

        ComputeNodeRespVO hidden = toRespVO(service, node, false);
        assertNull(hidden.getAgentToken());

        ComputeNodeRespVO exposed = toRespVO(service, node, true);
        assertEquals("secret-agent-token", exposed.getAgentToken());
    }

    @Test
    void keepsExistingPlatformHostWhenAutoDetectedHostDiffers() throws Exception {
        ComputeNodeServiceImpl service = new ComputeNodeServiceImpl();
        ComputeNodeDO platformNode = new ComputeNodeDO();
        platformNode.setId(4L);
        platformNode.setName("platform");
        platformNode.setHost("1.95.118.210");
        platformNode.setNodeRole(NodeRoleEnum.HYBRID.getRole());
        Map<String, Boolean> caps = new HashMap<>();
        caps.put("platform", true);
        caps.put("ai_inference", true);
        caps.put("algorithm_realtime", true);
        caps.put("algorithm_snap", true);
        caps.put("algorithm_patrol", true);
        caps.put("stream_forward", true);
        caps.put("auto_label", true);
        caps.put("model_train", true);
        caps.put("llm_inference", true);
        caps.put("srs_live", true);
        caps.put("srs_ai", true);
        caps.put("zlm", true);
        platformNode.setCapabilities(caps);
        setField(service, "computeNodeMapper", platformNodeMapper(platformNode));

        service.ensurePlatformNode();

        assertEquals("1.95.118.210", platformNode.getHost());
    }

    @Test
    void refreshesPlatformHostOnlyWhenConfiguredOrMissing() {
        assertTrue(ComputeNodeServiceImpl.shouldRefreshPlatformHost("172.18.0.12", "1.95.118.210", true));
        assertTrue(ComputeNodeServiceImpl.shouldRefreshPlatformHost("", "192.168.0.88", false));
        assertFalse(ComputeNodeServiceImpl.shouldRefreshPlatformHost("1.95.118.210", "172.18.0.12", false));
    }

    private static ComputeNodeRespVO toRespVO(ComputeNodeServiceImpl service,
                                             ComputeNodeDO node,
                                             boolean exposeToken) throws Exception {
        Method method = ComputeNodeServiceImpl.class.getDeclaredMethod("toRespVO", ComputeNodeDO.class, boolean.class);
        method.setAccessible(true);
        return (ComputeNodeRespVO) method.invoke(service, node, exposeToken);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static NodeSshCredentialMapper nullReturningSshCredentialMapper() {
        return (NodeSshCredentialMapper) Proxy.newProxyInstance(
                NodeSshCredentialMapper.class.getClassLoader(),
                new Class[]{NodeSshCredentialMapper.class},
                (proxy, method, args) -> null);
    }

    private static NodeMetricSnapshotMapper nullReturningMetricSnapshotMapper() {
        return (NodeMetricSnapshotMapper) Proxy.newProxyInstance(
                NodeMetricSnapshotMapper.class.getClassLoader(),
                new Class[]{NodeMetricSnapshotMapper.class},
                (proxy, method, args) -> null);
    }

    private static ComputeNodeMapper platformNodeMapper(ComputeNodeDO platformNode) {
        return (ComputeNodeMapper) Proxy.newProxyInstance(
                ComputeNodeMapper.class.getClassLoader(),
                new Class[]{ComputeNodeMapper.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("selectPlatformNode".equals(name)) {
                        return platformNode;
                    }
                    if ("selectByHost".equals(name)) {
                        return null;
                    }
                    if ("updateById".equals(name)) {
                        return 1;
                    }
                    if ("insert".equals(name)) {
                        platformNode.setId(4L);
                        return 1;
                    }
                    return null;
                });
    }
}
