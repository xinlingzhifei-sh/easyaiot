package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeMetricSnapshotMapper;
import com.basiclab.iot.node.dal.pgsql.NodeSshCredentialMapper;
import com.basiclab.iot.node.domain.vo.ComputeNodeRespVO;
import com.basiclab.iot.node.domain.vo.NodeMediaRemoteDeployRespVO;
import com.basiclab.iot.node.util.NodeFunctions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
        platformNode.setNodeRole(NodeFunctions.toCsv(NodeFunctions.PLATFORM_DEFAULT));
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

    @Test
    void deployFallsBackToStaleBundleMissingOnlyWaitressWhenRefreshFails(@TempDir Path sentinel) throws Exception {
        Path wheels = staleAgentBundle(sentinel, "3.10", List.of("waitress"));
        ComputeNodeServiceImpl service = serviceWithFailingWheelExport(sentinel);

        Object bundle = ReflectionTestUtils.invokeMethod(service, "resolveAgentPipBundle", "3.10");
        NodeMediaRemoteDeployRespVO.DeployStep step =
                ReflectionTestUtils.invokeMethod(service, "ensureLocalAgentPipWheels", bundle, "3.10");

        assertEquals("success", step.getStatus(), step.getOutput());
        assertTrue(step.getOutput().contains("waitress"), step.getOutput());
        assertTrue(step.getOutput().contains("network unreachable"), step.getOutput());
        assertEquals(wheels.toFile().getAbsoluteFile(),
                ((File) ReflectionTestUtils.getField(bundle, "wheelsDir")).getAbsoluteFile());
        assertTrue(((File) ReflectionTestUtils.getField(bundle, "getPipScript")).isFile());
    }

    @Test
    void deployStillFailsWhenRefreshFailsAndStaleBundleMissesCoreWheels(@TempDir Path sentinel) throws Exception {
        staleAgentBundle(sentinel, "3.10", List.of("waitress", "flask"));
        ComputeNodeServiceImpl service = serviceWithFailingWheelExport(sentinel);

        Object bundle = ReflectionTestUtils.invokeMethod(service, "resolveAgentPipBundle", "3.10");
        NodeMediaRemoteDeployRespVO.DeployStep step =
                ReflectionTestUtils.invokeMethod(service, "ensureLocalAgentPipWheels", bundle, "3.10");

        assertEquals("failed", step.getStatus(), step.getOutput());
    }

    /** 模拟控制面已有的旧离线包：export_pip_wheels.sh、get-pip.py、.target-python 齐全，缺 {@code missing} 中的 wheel。 */
    private static Path staleAgentBundle(Path sentinel, String targetPython, List<String> missing) throws Exception {
        Files.writeString(sentinel.resolve("export_pip_wheels.sh"), "exit 1\n");
        Files.writeString(sentinel.resolve("get-pip.py"), "# stub\n");
        Path wheels = Files.createDirectories(sentinel.resolve("pip-wheels"));
        Files.writeString(wheels.resolve(".target-python"), targetPython + "\n");
        for (String name : List.of("requests-2.32.3", "psutil-6.0.0", "Flask-3.0.3", "minio-7.2.7",
                "waitress-3.0.2", "pip-24.2", "setuptools-72.1.0", "wheel-0.44.0")) {
            String project = name.substring(0, name.indexOf('-')).toLowerCase(Locale.ROOT);
            if (!missing.contains(project)) {
                Files.createFile(wheels.resolve(name + "-py3-none-any.whl"));
            }
        }
        return wheels;
    }

    /** 刷新离线包时脚本失败（例如控制面无法联网）。 */
    private static ComputeNodeServiceImpl serviceWithFailingWheelExport(Path sentinel) {
        ComputeNodeServiceImpl service = new ComputeNodeServiceImpl() {
            @Override
            String runExportPipWheelsScript(File exportScript, String targetPython, File wheelsDir) {
                throw new IllegalStateException("network unreachable");
            }
        };
        ReflectionTestUtils.setField(service, "agentSourcePath", sentinel.toString());
        return service;
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
