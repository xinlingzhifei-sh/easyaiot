package com.basiclab.iot.node.agent;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.basiclab.iot.common.exception.ServiceException;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import com.basiclab.iot.node.dal.pgsql.NodeAgentCommandMapper;
import com.basiclab.iot.node.service.NodeAgentCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_COMMAND_FAILED;

/**
 * 控制面调用节点 Agent 的唯一出口：部署、停止、媒体/MQTT 栈、Sentinel 探测与摄像头接入都经这里。
 * <p>
 * 传输方式：Agent 声明 inbound=false（不监听 9100）时经指令队列下发并等待结果；其余情况照旧直连 9100。
 * 直连时令牌取 {@link NodeAgentCredentialService#dispatchToken}，轮换后 Agent 尚未切换时仍用上一令牌。
 */
@Slf4j
@Component
public class NodeAgentGateway {

    static final int DEFAULT_AGENT_PORT = 9100;
    static final String FEATURE_INBOUND = "inbound";
    static final String RPC_KEY_PREFIX = "rpc:";
    /** Agent 空闲时每 3s 轮询一次，排队等待比 HTTP 超时多留出这段余量 */
    static final long QUEUE_PICKUP_GRACE_MS = 15_000;

    /** HTTP 交换，单测替换 */
    @FunctionalInterface
    interface HttpTransport {
        HttpReply post(String url, String agentToken, String jsonBody, int timeoutMs);
    }

    record HttpReply(int status, String body) {
    }

    /** 每一步数据库操作都在独立事务里提交：调用方（如 deployWorkload）常处于事务中，不提交 Agent 就看不到指令 */
    @FunctionalInterface
    interface TxRunner {
        <T> T inNewTransaction(Supplier<T> work);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    @Resource
    private NodeAgentCredentialService nodeAgentCredentialService;
    @Resource
    private NodeAgentCommandMapper nodeAgentCommandMapper;
    @Resource
    private PlatformTransactionManager transactionManager;

    /** true 时只要 Agent 声明支持就走队列（即使 9100 仍开放），用于逐步验证只出站 */
    @Value("${easyaiot.node.agent.prefer-queue:false}")
    private boolean preferQueue;
    @Value("${easyaiot.node.agent.queue-poll-interval-ms:500}")
    private long queuePollIntervalMs = 500;

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

    TxRunner txRunner;
    Sleeper sleeper = Thread::sleep;

    @PostConstruct
    void initTransactions() {
        if (txRunner == null && transactionManager != null) {
            TransactionTemplate template = new TransactionTemplate(transactionManager);
            template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            txRunner = new TxRunner() {
                @Override
                public <T> T inNewTransaction(Supplier<T> work) {
                    return template.execute(status -> work.get());
                }
            };
        }
    }

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
        Map<String, ?> safeBody = body != null ? body : Collections.emptyMap();
        String commandType = commandTypeFor(path);
        if (useQueue(node, commandType)) {
            return callViaQueue(node, commandType, safeBody, timeoutMs);
        }
        return callViaHttp(node, path, safeBody, timeoutMs);
    }

    /** /camera/scan-segment → camera.scan-segment，与 SENTINEL agent_server.COMMAND_ROUTES 一致 */
    static String commandTypeFor(String path) {
        return StrUtil.removePrefix(path, "/").replaceFirst("/", ".");
    }

    boolean useQueue(ComputeNodeDO node, String commandType) {
        Map<String, Boolean> features = node.getAgentFeatures();
        boolean supported = features != null && Boolean.TRUE.equals(features.get(commandType));
        boolean inboundDisabled = features != null && Boolean.FALSE.equals(features.get(FEATURE_INBOUND));
        if (inboundDisabled && !supported) {
            // 节点没开 9100，回退 HTTP 必然失败
            throw exception(AGENT_COMMAND_FAILED, "节点 " + node.getId() + " 的 Agent 未监听入站端口，且不支持指令 "
                    + commandType + "，请升级 Agent 或设置 AGENT_INBOUND_ENABLED=true");
        }
        return inboundDisabled || (preferQueue && supported);
    }

    Object callViaHttp(ComputeNodeDO node, String path, Map<String, ?> body, int timeoutMs) {
        int port = node.getAgentPort() != null && node.getAgentPort() > 0 ? node.getAgentPort() : DEFAULT_AGENT_PORT;
        String url = String.format("http://%s:%d%s", node.getHost(), port, path);
        String token = nodeAgentCredentialService.dispatchToken(node);
        if (StrUtil.isBlank(token)) {
            throw exception(AGENT_COMMAND_FAILED, "节点 " + node.getId()
                    + " 没有可用的 Agent 令牌（加密密钥是否更换过？请重置令牌并重新部署 Agent）");
        }
        try {
            HttpReply reply = httpTransport.post(url, token, JSONUtil.toJsonStr(body), timeoutMs);
            return parseReply(reply);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Agent 请求异常 nodeId={} url={}: {}", node.getId(), url, e.getMessage());
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw exception(AGENT_COMMAND_FAILED, node.getHost() + ":" + port + " — " + detail);
        }
    }

    Object callViaQueue(ComputeNodeDO node, String commandType, Map<String, ?> body, int timeoutMs) {
        NodeAgentCommandDO command = NodeAgentCommandDO.builder()
                .nodeId(node.getId())
                .commandType(commandType)
                .commandKey(RPC_KEY_PREFIX + commandType + ":" + IdUtil.fastSimpleUUID())
                .payloadJson(JSONUtil.toJsonStr(body))
                .status(NodeAgentCommandMapper.STATUS_PENDING)
                .attemptCount(0)
                .build();
        txRunner.inNewTransaction(() -> nodeAgentCommandMapper.insert(command));
        Long commandId = command.getId();
        long deadline = System.currentTimeMillis() + timeoutMs + QUEUE_PICKUP_GRACE_MS;
        try {
            while (true) {
                NodeAgentCommandDO current = txRunner.inNewTransaction(() -> nodeAgentCommandMapper.selectById(commandId));
                String status = current != null ? current.getStatus() : null;
                if (NodeAgentCommandMapper.STATUS_SUCCEEDED.equals(status)) {
                    return parseQueuedResult(current);
                }
                if (status == null || NodeAgentCommandMapper.FINISHED_STATUSES.contains(status)) {
                    throw exception(AGENT_COMMAND_FAILED, StrUtil.blankToDefault(
                            current != null ? current.getLastError() : null, "Agent 指令执行失败: " + status));
                }
                if (System.currentTimeMillis() >= deadline) {
                    boolean canceled = txRunner.inNewTransaction(() -> nodeAgentCommandMapper.cancelIfPending(commandId)) > 0;
                    throw exception(AGENT_COMMAND_FAILED, canceled
                            ? "Agent 未在超时前领取指令（节点离线或轮询未开启？）"
                            : "等待 Agent 执行结果超时");
                }
                sleeper.sleep(queuePollIntervalMs);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(AGENT_COMMAND_FAILED, "等待 Agent 执行结果被中断");
        } finally {
            // 部署参数里有中间件凭据：HTTP 方式从不落库，队列方式在拿到结果后立即清掉
            try {
                txRunner.inNewTransaction(() -> nodeAgentCommandMapper.clearPayload(commandId));
            } catch (RuntimeException e) {
                log.warn("清理 Agent 指令参数失败 commandId={}: {}", commandId, e.getMessage());
            }
        }
    }

    static Object parseQueuedResult(NodeAgentCommandDO command) {
        if (StrUtil.isBlank(command.getResultJson())) {
            return null;
        }
        return JSONUtil.parseObj(command.getResultJson()).get("data");
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
