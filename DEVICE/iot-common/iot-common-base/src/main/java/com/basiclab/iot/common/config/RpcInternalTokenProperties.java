package com.basiclab.iot.common.config;

import com.basiclab.iot.common.enums.RpcConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部 RPC 服务令牌配置
 */
public class RpcInternalTokenProperties {

    private String internalToken;

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public boolean isConfigured() {
        return internalToken != null
                && internalToken.length() >= RpcConstants.RPC_INTERNAL_TOKEN_MIN_LENGTH;
    }

    public boolean matches(String candidate) {
        if (!isConfigured() || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }

}
