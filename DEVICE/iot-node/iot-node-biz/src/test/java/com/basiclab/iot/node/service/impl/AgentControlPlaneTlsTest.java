package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.node.util.AgentDeployUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentControlPlaneTlsTest {

    private static final String CA_PEM = "-----BEGIN CERTIFICATE-----\nMIIBszCCAVmgAwIBAgIUQ\n-----END CERTIFICATE-----\n";

    @Test
    void envContentPointsAgentAtDistributedCaOnlyWhenConfigured() {
        String withCa = AgentDeployUtil.buildEnvContent(3L, "t", 9100,
                "https://cp.example/admin-api/node/agent", "algorithm", true);
        String without = AgentDeployUtil.buildEnvContent(3L, "t", 9100,
                "https://cp.example/admin-api/node/agent", "algorithm");

        assertTrue(withCa.contains("CONTROL_PLANE_URL=https://cp.example/admin-api/node/agent\n"));
        assertTrue(withCa.contains("CONTROL_PLANE_CA_FILE=" + AgentDeployUtil.REMOTE_CONTROL_PLANE_CA_FILE + "\n"));
        assertFalse(without.contains("CONTROL_PLANE_CA_FILE"));
        assertTrue(without.contains("PLATFORM_AGENT=0\n"), "远程节点始终写 PLATFORM_AGENT=0");
        assertFalse(withCa.toLowerCase().contains("verify=false"));
    }

    @Test
    void installScriptWritesCaAndRestrictsAgentEnv() {
        String script = AgentDeployUtil.buildInstallScript("AGENT_TOKEN=t\n", CA_PEM);

        assertTrue(script.contains("sudo chmod 600 agent.env\n"));
        assertTrue(script.contains("sudo tee " + AgentDeployUtil.REMOTE_CONTROL_PLANE_CA_FILE + " > /dev/null <<'EOF_CA'\n"
                + CA_PEM + "EOF_CA\n"));
        assertTrue(script.indexOf("EOF_CA") < script.indexOf("install.sh install"), "CA 先落盘再启动 Agent");
        assertFalse(AgentDeployUtil.buildInstallScript("AGENT_TOKEN=t\n").contains("EOF_CA"));
    }

    @Test
    void rejectsNonCertificatesPrivateKeysAndHeredocBreakers() {
        assertThrows(IllegalArgumentException.class, () -> AgentDeployUtil.validateControlPlaneCaPem("hello"));
        assertThrows(IllegalArgumentException.class, () -> AgentDeployUtil.validateControlPlaneCaPem(
                CA_PEM + "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n"));
        assertThrows(IllegalArgumentException.class, () -> AgentDeployUtil.validateControlPlaneCaPem(
                CA_PEM + "EOF_CA\nrm -rf /\n"));
        assertEquals(CA_PEM, AgentDeployUtil.validateControlPlaneCaPem(CA_PEM.replace("\n", "\r\n")));
    }

    @Test
    void controlPlaneCaIsOptionalButMustBeReadableWhenConfigured(@TempDir Path dir) throws Exception {
        ComputeNodeServiceImpl service = new ComputeNodeServiceImpl();

        ReflectionTestUtils.setField(service, "controlPlaneCaFile", "");
        assertNull(service.loadControlPlaneCaPem());

        Path ca = Files.writeString(dir.resolve("ca.pem"), CA_PEM);
        ReflectionTestUtils.setField(service, "controlPlaneCaFile", ca.toString());
        assertEquals(CA_PEM, service.loadControlPlaneCaPem());

        ReflectionTestUtils.setField(service, "controlPlaneCaFile", dir.resolve("missing.pem").toString());
        assertThrows(IllegalStateException.class, service::loadControlPlaneCaPem);

        Path key = Files.writeString(dir.resolve("key.pem"), "-----BEGIN PRIVATE KEY-----\nabc\n");
        ReflectionTestUtils.setField(service, "controlPlaneCaFile", key.toString());
        assertThrows(IllegalStateException.class, service::loadControlPlaneCaPem);
    }
}
