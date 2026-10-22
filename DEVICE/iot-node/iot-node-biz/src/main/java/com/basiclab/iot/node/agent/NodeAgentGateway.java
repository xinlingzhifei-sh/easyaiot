package com.basiclab.iot.node.agent;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.service.NodeAgentCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_COMMAND_FAILED;

/**
 * 控制面调用节点 Agent 的唯一出口：部署、停止、媒体/MQTT 栈、Sentinel 探测与摄像头接入都经这里。
 * 令牌取 {@link NodeAgentCredentialService#dispatchToken}，轮换后 Agent 尚未切换时仍用上一令牌。
 */
@Slf4j
@Component
public class NodeAgentGateway {

    static final int DEFAULT_AGENT_PORT = 9100;

    /** HTTP 交换，单测替换 */
    @FunctionalInterface
    interface HttpTransport {
        HttpReply post(String url, String agentToken, String jsonBody, int timeoutMs);
    }

    record HttpReply(int status, String body) {
    }

    @Resource
    private NodeAgentCredentialService nodeAgentCredentialService;

    HttpTransport httpTransport = (url, agentToken, jsonBody, timeoutMs) -> {
        try (HttpResponse response = HttpRequest.post(url)
                .header("X-Agent-Token", agentToken)
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .timeout(timeoutMs)
                .execute()) {
            return new HttpReply(response.getStatus(), response.body());
        }
    };

    /**
     * 调用 Agent 并返回响应 data（非对象时返回空对象）。
     */
    public JSONObject call(ComputeNodeDO node, String path, Map<String, ?> body, int timeoutMs) {
        Object data = callForData(node, path, body, timeoutMs);
        return data instanceof JSONObject json ? json : new JSONObject();
    }

    /**
     * 调用 Agent 并原样返回响应 data（摄像头发现等接口返回数组）。失败一律抛 AGENT_COMMAND_FAILED。
     */
    public Object callForData(ComputeNodeDO node, String path, Map<String, ?> body, int timeoutMs) {
        int port = node.getAgentPort() != null && node.getAgentPort() > 0 ? node.getAgentPort() : DEFAULT_AGENT_PORT;
        String url = String.format("http://%s:%d%s", node.getHost(), port, path);
        String token = nodeAgentCredentialService.dispatchToken(node);
        if (StrUtil.isBlank(token)) {
            throw exception(AGENT_COMMAND_FAILED, "节点 " + node.getId()
                    + " 没有可用的 Agent 令牌（加密密钥是否更换过？请重置令牌并重新部署 Agent）");
        }
        try {
            HttpReply reply = httpTransport.post(url, token,
                    JSONUtil.toJsonStr(body != null ? body : Collections.emptyMap()), timeoutMs);
            return parseReply(reply);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent 请求异常 nodeId={} url={}: {}", node.getId(), url, e.getMessage());
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw exception(AGENT_COMMAND_FAILED, node.getHost() + ":" + port + " — " + detail);
        }
    }

    static Object parseReply(HttpReply reply) {
        if (reply.status() < 200 || reply.status() >= 300) {
            throw exception(AGENT_COMMAND_FAILED, "HTTP " + reply.status() + " — " + reply.body());
        }
        JSONObject json = JSONUtil.parseObj(reply.body());
        if (json.getInt("code", -1) != 0) {
            throw exception(AGENT_COMMAND_FAILED, json.getStr("msg", reply.body()));
        }
        return json.get("data");
    }
}
