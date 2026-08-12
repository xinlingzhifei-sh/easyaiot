package com.basiclab.iot.node.security;

import cn.hutool.core.util.StrUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 节点控制面共享令牌校验。
 */
public final class NodeTokenVerifier {

    private NodeTokenVerifier() {
    }

    public static boolean matches(String expected, String actual) {
        if (StrUtil.isBlank(expected) || StrUtil.isBlank(actual)) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
