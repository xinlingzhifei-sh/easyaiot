package com.basiclab.iot.system.framework.sms.core.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmsCallbackAccessVerifierTest {

    private static final String TOKEN = "sms-callback-test-token-at-least-32-bytes";

    @Test
    void acceptsValidHeaderToken() {
        SmsCallbackAccessVerifier verifier = new SmsCallbackAccessVerifier(TOKEN);

        assertDoesNotThrow(() -> verifier.verify(TOKEN, null));
    }

    @Test
    void acceptsValidQueryTokenForProvidersWithoutCustomHeaderSupport() {
        SmsCallbackAccessVerifier verifier = new SmsCallbackAccessVerifier(TOKEN);

        assertDoesNotThrow(() -> verifier.verify(null, TOKEN));
    }

    @Test
    void rejectsInvalidToken() {
        SmsCallbackAccessVerifier verifier = new SmsCallbackAccessVerifier(TOKEN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify("wrong-token", null));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void failsClosedWhenTokenIsNotConfigured() {
        SmsCallbackAccessVerifier verifier = new SmsCallbackAccessVerifier("");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify(null, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }
}
