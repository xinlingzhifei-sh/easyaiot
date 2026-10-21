package com.basiclab.iot.node.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeTokenGeneratorTest {

    @Test
    void keepsLegacyTokenFormat() {
        // 与旧版 fastSimpleUUID 相同：32 位小写十六进制，agent.env / 数据库字段无需调整
        String token = NodeTokenGenerator.newAgentToken();

        assertTrue(token.matches("[0-9a-f]{32}"), token);
        assertTrue(NodeTokenVerifier.matches(token, token));
    }

    @Test
    void generatesDistinctTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(NodeTokenGenerator.newAgentToken());
        }

        assertEquals(1000, tokens.size());
    }
}
