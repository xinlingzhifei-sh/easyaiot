package com.basiclab.iot.message.security;

import com.basiclab.iot.common.service.SecurityFrameworkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 消息模板和通知人查询既供后台管理员使用，也供受信服务调用。
 */
@Component
public class MessageInternalAccessVerifier {

    public static final String TOKEN_HEADER = "X-Iot-Message-Token";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final SecurityFrameworkService securityFrameworkService;
    private final String configuredToken;

    public MessageInternalAccessVerifier(
            SecurityFrameworkService securityFrameworkService,
            @Value("${IOT_MESSAGE_INTERNAL_TOKEN:}") String configuredToken) {
        this.securityFrameworkService = securityFrameworkService;
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    public void verify(String suppliedToken) {
        if (securityFrameworkService.isAdminUser()) {
            return;
        }
        if (configuredToken.length() < MIN_TOKEN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "IOT_MESSAGE_INTERNAL_TOKEN is not configured");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid service token");
        }
    }
}
