package com.genersoft.iot.vmp.framework.security;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * ZLM 与 ABL 媒体回调共用的服务令牌校验。
 */
public final class MediaHookTokenSupport {

    public static final String TOKEN_HEADER = "X-Gb28181-Media-Hook-Token";
    public static final String TOKEN_PARAMETER = "hookToken";
    public static final int MIN_TOKEN_LENGTH = 32;

    private MediaHookTokenSupport() {
    }

    public static boolean isConfigured(String configuredToken) {
        return configuredToken != null && configuredToken.length() >= MIN_TOKEN_LENGTH;
    }

    public static boolean matches(String configuredToken, String suppliedToken) {
        return isConfigured(configuredToken)
                && suppliedToken != null
                && MessageDigest.isEqual(
                configuredToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8));
    }

    public static String appendToUrl(String hookUrl, String configuredToken) {
        if (!isConfigured(configuredToken)) {
            throw new IllegalStateException("GB28181_MEDIA_HOOK_TOKEN is not configured");
        }
        String separator = hookUrl.contains("?") ? "&" : "?";
        return hookUrl + separator + TOKEN_PARAMETER + "="
                + URLEncoder.encode(configuredToken, StandardCharsets.UTF_8);
    }
}
