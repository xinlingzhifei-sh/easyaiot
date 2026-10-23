package com.basiclab.iot.node.security;

import cn.hutool.core.util.StrUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Agent 令牌密文：控制面调用 Agent 9100、写 agent.env 等场景仍需取回明文。
 * AES-256-GCM，密钥由配置口令派生（不在数据库里），存储格式 {@code gcm1$<base64(IV||密文||标签)>}。
 */
public final class AgentTokenCipher {

    private static final String PREFIX = "gcm1$";
    private static final String KEY_CONTEXT = "yfeieye:node-agent-token:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    private AgentTokenCipher(byte[] keyBytes) {
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * 由配置口令派生密钥；口令为空时返回 null，调用方据此退回"保留明文列"的兼容模式。
     */
    public static AgentTokenCipher fromSecret(String secret) {
        if (StrUtil.isBlank(secret)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return new AgentTokenCipher(digest.digest((KEY_CONTEXT + secret).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String encrypt(String token) {
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("agent token must not be blank");
        }
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("agent token encryption failed", e);
        }
    }

    /**
     * 解密失败（格式不对、密钥已更换、密文被篡改）时抛 IllegalStateException，不返回错误明文。
     */
    public String decrypt(String stored) {
        if (StrUtil.isBlank(stored) || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("unsupported agent token cipher format");
        }
        try {
            byte[] raw = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (raw.length <= IV_BYTES) {
                throw new IllegalStateException("agent token cipher too short");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES));
            return new String(cipher.doFinal(raw, IV_BYTES, raw.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("agent token decryption failed (encryption key changed?)", e);
        }
    }
}
