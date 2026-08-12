package com.basiclab.iot.common.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcInternalTokenPropertiesTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789a";

    @Test
    void requiresAtLeastFortyThreeCharacters() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();

        assertFalse(properties.isConfigured());
        properties.setInternalToken("x".repeat(42));
        assertFalse(properties.isConfigured());
        properties.setInternalToken(TOKEN);
        assertTrue(properties.isConfigured());
    }

    @Test
    void matchesOnlyTheConfiguredToken() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);

        assertTrue(properties.matches(TOKEN));
        assertFalse(properties.matches(TOKEN + "x"));
        assertFalse(properties.matches(null));
    }

}
