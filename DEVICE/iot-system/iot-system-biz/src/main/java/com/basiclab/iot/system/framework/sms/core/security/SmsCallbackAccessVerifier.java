package com.basiclab.iot.system.framework.sms.core.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 短信供应商状态回调使用独立令牌，不依赖用户登录态。
 */
@Component
public class SmsCallbackAccessVerifier {

    public static final String TOKEN_HEADER = "X-Sms-Callback-Token";
    public static final String TOKEN_PARAMETER = "callbackToken";
    private static final int MIN_TOKEN_LENGTH = 32;

    private final String configuredToken;

    public SmsCallbackAccessVerifier(
            @Value("${sms-callback.token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    public void verify(String headerToken, String queryToken) {
        if (configuredToken.length() < MIN_TOKEN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "SMS_CALLBACK_TOKEN is not configured");
        }
        String suppliedToken = StringUtils.hasText(headerToken) ? headerToken : queryToken;
        if (suppliedToken == null || !MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid SMS callback token");
        }
    }
}
