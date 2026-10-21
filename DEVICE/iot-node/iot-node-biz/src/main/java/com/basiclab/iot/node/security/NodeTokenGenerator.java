package com.basiclab.iot.node.security;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 节点 Agent 令牌生成。
 * 128 位密码学安全随机数，输出 32 位小写十六进制串，长度与字符集同旧版 IdUtil.fastSimpleUUID 一致，已发放的令牌照常可用。
 */
public final class NodeTokenGenerator {

    private static final int TOKEN_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private NodeTokenGenerator() {
    }

    public static String newAgentToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
