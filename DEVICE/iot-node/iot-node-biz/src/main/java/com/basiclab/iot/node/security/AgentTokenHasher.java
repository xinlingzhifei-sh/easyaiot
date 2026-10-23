package com.basiclab.iot.node.security;

import cn.hutool.core.util.StrUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Agent 令牌的加盐哈希：HMAC-SHA256(key = 每个令牌独立的 16 字节随机盐, msg = 令牌)。
 * 存储格式 {@code hs256$<盐>$<MAC>}（base64url 无填充）。校验只比较 MAC，常量时间。
 */
public final class AgentTokenHasher {

    private static final String PREFIX = "hs256";
    private static final String ALGORITHM = "HmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private AgentTokenHasher() {
    }

    public static String hash(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("agent token must not be blank");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return PREFIX + "$" + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(mac(salt, token));
    }

    /**
     * 令牌与哈希是否匹配。哈希为空、格式不对或令牌为空时返回 false，不抛异常。
     */
    public static boolean matches(String storedHash, String token) {
        if (StrUtil.isBlank(storedHash) || StrUtil.isBlank(token)) {
            return false;
        }
        String[] parts = storedHash.split("\\$");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            byte[] salt = DECODER.decode(parts[1]);
            byte[] expected = DECODER.decode(parts[2]);
            return MessageDigest.isEqual(expected, mac(salt, token));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] mac(byte[] salt, String token) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(salt, ALGORITHM));
            return mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
