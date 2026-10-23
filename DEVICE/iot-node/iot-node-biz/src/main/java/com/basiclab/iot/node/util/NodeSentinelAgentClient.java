package com.basiclab.iot.node.util;

import com.basiclab.iot.node.agent.NodeAgentGateway;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class NodeSentinelAgentClient {

    private static final int PROBE_TIMEOUT_MS = 120_000;
    private static final int VALIDATE_TIMEOUT_MS = 120_000;

    @Resource
    private NodeAgentGateway nodeAgentGateway;

    public Map<String, Object> probe(ComputeNodeDO node, String level) {
        Map<String, Object> body = new HashMap<>();
        body.put("level", level != null ? level : "L1");
        return postAgent(node, "/sentinel/probe", body, PROBE_TIMEOUT_MS);
    }

    public Map<String, Object> validate(ComputeNodeDO node, String workloadType, Map<String, Object> requirements) {
        Map<String, Object> body = new HashMap<>();
        body.put("workloadType", workloadType);
        body.put("requirements", requirements != null ? requirements : Map.of());
        return postAgent(node, "/sentinel/validate", body, VALIDATE_TIMEOUT_MS);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postAgent(ComputeNodeDO node, String path, Map<String, Object> body, int timeoutMs) {
        Object data = nodeAgentGateway.callForData(node, path, body, timeoutMs);
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
