package com.basiclab.iot.node.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeTokenVerifierTest {

    @Test
    void acceptsOnlyExactNonBlankToken() {
        assertTrue(NodeTokenVerifier.matches("bootstrap-secret", "bootstrap-secret"));
        assertFalse(NodeTokenVerifier.matches("bootstrap-secret", "wrong"));
        assertFalse(NodeTokenVerifier.matches("bootstrap-secret", ""));
        assertFalse(NodeTokenVerifier.matches("", "bootstrap-secret"));
        assertFalse(NodeTokenVerifier.matches(null, "bootstrap-secret"));
    }
}
