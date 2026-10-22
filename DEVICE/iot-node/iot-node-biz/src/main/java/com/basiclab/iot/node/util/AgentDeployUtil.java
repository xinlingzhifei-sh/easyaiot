package com.basiclab.iot.node.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class AgentDeployUtil {

    public static final String REMOTE_INSTALL_DIR = "/opt/easyaiot/sentinel-agent";
    /** 兼容历史安装路径 */
    public static final String LEGACY_REMOTE_INSTALL_DIR = "/opt/easyaiot/node-agent";
    public static final String PIP_WHEELS_DIR = "pip-wheels";
    public static final String EXPORT_PIP_WHEELS_SCRIPT = "export_pip_wheels.sh";
    public static final String GET_PIP_SCRIPT = "get-pip.py";
    /** 控制面自定义 CA 在节点上的位置（easyaiot.agent.control-plane-ca-file 配置时下发） */
    public static final String REMOTE_CONTROL_PLANE_CA_FILE = REMOTE_INSTALL_DIR + "/control-plane-ca.pem";

    /**
     * SSH 部署时上传到安装目录的 Agent 源码（sentinel/、registry/ 按目录另行上传）。
     * install.sh 在安装目录原地执行时会跳过自身的源码同步，所以这里必须覆盖入口脚本导入的全部本地模块；
     * AgentDeployUtilTest 与 SENTINEL/tests/test_agent_install_manifest.py 从 SENTINEL 源码推导并锁住该清单。
     */
    public static final String[] SYNC_RELATIVE_FILES = {
            "run_sentinel.py",
            "run_agent.py",
            "agent_server.py",
            "agent_commands.py",
            "camera_access.py",
            "env_security.py",
            "media_manager.py",
            "mqtt_manager.py",
            "stream_forward_executor.py",
            "workload_manager.py",
            "requirements.txt",
            "agent.env.example",
            "install.sh",
    };

    /** 全量离线包可选文件：缺省不阻断部署 */
    public static final String[] SYNC_OPTIONAL_FILES = {
            "requirements-py39-extras.txt",
            "README.md",
    };

    /**
     * 离线 pip 包按文件名判定完整时必须存在的包，与 SENTINEL/export_pip_wheels.sh 的 verify_offline_install 一致；
     * 缺 waitress 的旧离线包判为不完整，部署前由 export_pip_wheels.sh 刷新。
     */
    private static final List<String> REQUIRED_AGENT_WHEELS = List.of(
            "requests", "psutil", "flask", "minio", "waitress", "pip", "setuptools", "wheel");
    /** Python 3.9 额外依赖（requirements-py39-extras.txt）。 */
    private static final List<String> PY39_EXTRA_AGENT_WHEELS = List.of("importlib_metadata", "zipp");
    /**
     * 离线包缺这些包时仍可部署：install.sh 会跳过它们安装其余依赖，Agent 回退到 Flask 开发服务器。
     * 仅在离线包刷新失败时作为降级使用。
     */
    private static final List<String> DEGRADABLE_AGENT_WHEELS = List.of("waitress");

    private AgentDeployUtil() {
    }

    /**
     * 按文件名检查离线 pip 包是否齐全，返回缺失的包名前缀（不区分大小写）。
     */
    public static List<String> findMissingAgentWheels(Collection<String> artifactNames, String targetPython) {
        List<String> lowerNames = new ArrayList<>();
        for (String name : artifactNames) {
            lowerNames.add(name.toLowerCase(Locale.ROOT));
        }
        List<String> required = new ArrayList<>(REQUIRED_AGENT_WHEELS);
        if (isPythonBelow310(targetPython)) {
            required.addAll(PY39_EXTRA_AGENT_WHEELS);
        }
        List<String> missing = new ArrayList<>();
        for (String prefix : required) {
            if (lowerNames.stream().noneMatch(name -> name.startsWith(prefix))) {
                missing.add(prefix);
            }
        }
        return missing;
    }

    /**
     * 缺失的包是否全部可降级（且确有缺失）：此时旧离线包仍能完成部署。
     */
    public static boolean isDegradableAgentWheelGap(List<String> missingWheels) {
        return !missingWheels.isEmpty() && DEGRADABLE_AGENT_WHEELS.containsAll(missingWheels);
    }

    private static boolean isPythonBelow310(String version) {
        if (version == null || !version.matches("3\\.\\d+")) {
            return false;
        }
        return Integer.parseInt(version.substring(2)) < 10;
    }

    public static String buildEnvContent(long nodeId, String agentToken, int agentPort,
                                         String controlPlaneUrl, String functionsCsv) {
        return buildEnvContent(nodeId, agentToken, agentPort, controlPlaneUrl, functionsCsv, false);
    }

    /**
     * @param controlPlaneCa 是否随安装脚本下发控制面 CA（{@link #buildInstallScript(String, String)}）
     */
    public static String buildEnvContent(long nodeId, String agentToken, int agentPort,
                                         String controlPlaneUrl, String functionsCsv, boolean controlPlaneCa) {
        return buildEnvContent(nodeId, agentToken, agentPort, controlPlaneUrl, functionsCsv, controlPlaneCa, true);
    }

    /**
     * @param inboundEnabled false 时写 AGENT_INBOUND_ENABLED=false：Agent 不监听 9100，全部指令经轮询通道下发
     */
    public static String buildEnvContent(long nodeId, String agentToken, int agentPort, String controlPlaneUrl,
                                         String functionsCsv, boolean controlPlaneCa, boolean inboundEnabled) {
        String functions = functionsCsv != null && !functionsCsv.isBlank()
                ? functionsCsv.trim().toLowerCase() : "";
        return "# yFeiEye Node Agent 配置\n"
                + "PLATFORM_AGENT=0\n"
                + "NODE_ID=" + nodeId + "\n"
                + "AGENT_TOKEN=" + agentToken + "\n"
                + "NODE_FUNCTIONS=" + functions + "\n"
                + "CONTROL_PLANE_URL=" + controlPlaneUrl + "\n"
                + (controlPlaneCa ? "CONTROL_PLANE_CA_FILE=" + REMOTE_CONTROL_PLANE_CA_FILE + "\n" : "")
                + "HEARTBEAT_INTERVAL=10\n"
                + "SENTINEL_L1_INTERVAL=300\n"
                + "SENTINEL_AUTO_REMEDIATE=true\n"
                + "AGENT_INBOUND_ENABLED=" + inboundEnabled + "\n"
                + "AGENT_LISTEN_HOST=0.0.0.0\n"
                + "AGENT_LISTEN_PORT=" + agentPort + "\n"
                + "AI_ROOT=/opt/easyaiot/AI\n"
                + "VIDEO_ROOT=/opt/easyaiot/VIDEO\n"
                + "MEDIA_CLUSTER_ROOT=/opt/easyaiot/media-cluster\n"
                + "MQTT_CLUSTER_ROOT=/opt/easyaiot/mqtt-cluster\n"
                + "MINIO_ENDPOINT=http://localhost:9000\n"
                + "MINIO_ACCESS_KEY=\n"
                + "MINIO_SECRET_KEY=\n";
    }

    public static String buildInstallScript(String envContent) {
        return buildInstallScript(envContent, null);
    }

    /**
     * @param controlPlaneCaPem 控制面 CA（PEM，可含多张证书）；为空时不下发
     */
    public static String buildInstallScript(String envContent, String controlPlaneCaPem) {
        StringBuilder script = new StringBuilder()
                .append("#!/usr/bin/env bash\n")
                .append("set -euo pipefail\n")
                .append("INSTALL_DIR=\"").append(REMOTE_INSTALL_DIR).append("\"\n")
                .append("cd \"$INSTALL_DIR\"\n")
                .append("sudo tee agent.env > /dev/null <<'EOF'\n")
                .append(envContent)
                .append("EOF\n")
                // 含 Agent 令牌，只给 root（systemd 服务以 root 读取）
                .append("sudo chmod 600 agent.env\n");
        if (controlPlaneCaPem != null && !controlPlaneCaPem.isBlank()) {
            String pem = validateControlPlaneCaPem(controlPlaneCaPem);
            script.append("sudo tee ").append(REMOTE_CONTROL_PLANE_CA_FILE).append(" > /dev/null <<'EOF_CA'\n")
                    .append(pem)
                    .append("EOF_CA\n")
                    .append("sudo chmod 644 ").append(REMOTE_CONTROL_PLANE_CA_FILE).append("\n");
        }
        return script.append("sudo chmod +x install.sh\n")
                .append("sudo bash install.sh install\n")
                .toString();
    }

    /**
     * 只接受证书：拒绝私钥（避免把网关私钥误配成 CA 发到每台节点）以及会提前结束 heredoc 的内容。
     */
    public static String validateControlPlaneCaPem(String pem) {
        String normalized = pem.replace("\r\n", "\n").trim();
        if (!normalized.contains("-----BEGIN CERTIFICATE-----")) {
            throw new IllegalArgumentException("控制面 CA 文件不是 PEM 证书");
        }
        if (normalized.contains("PRIVATE KEY")) {
            throw new IllegalArgumentException("控制面 CA 文件包含私钥，拒绝下发到节点");
        }
        for (String line : normalized.split("\n")) {
            if ("EOF_CA".equals(line.trim())) {
                throw new IllegalArgumentException("控制面 CA 文件内容非法");
            }
        }
        return normalized + "\n";
    }

}
