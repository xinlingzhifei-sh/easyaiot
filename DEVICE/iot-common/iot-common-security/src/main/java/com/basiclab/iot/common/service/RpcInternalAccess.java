package com.basiclab.iot.common.service;

import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.enums.RpcConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 内部 RPC 服务身份访问校验
 */
public class RpcInternalAccess {

    private static final Logger LOG = LoggerFactory.getLogger(RpcInternalAccess.class);
    private static final long DIAGNOSTIC_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final RpcInternalTokenProperties properties;
    private final AtomicLong nextDiagnosticNanos = new AtomicLong();

    public RpcInternalAccess(RpcInternalTokenProperties properties) {
        this.properties = properties;
        if (!properties.isConfigured()) {
            LOG.error("未配置有效的内部 RPC 服务令牌，所有 RPC 请求将被拒绝");
        }
    }

    public boolean isAllowed(HttpServletRequest request) {
        boolean allowed = request != null && properties.matches(
                request.getHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
        if (!allowed) {
            logRejectedRequest();
        }
        return allowed;
    }

    private void logRejectedRequest() {
        long now = System.nanoTime();
        long next = nextDiagnosticNanos.get();
        if (now < next || !nextDiagnosticNanos.compareAndSet(next, now + DIAGNOSTIC_INTERVAL_NANOS)) {
            return;
        }
        LOG.warn("内部 RPC 服务令牌校验失败，已拒绝请求");
    }

}
