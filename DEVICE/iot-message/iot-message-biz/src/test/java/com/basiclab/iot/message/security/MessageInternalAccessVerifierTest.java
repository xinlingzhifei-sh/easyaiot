package com.basiclab.iot.message.security;

import com.basiclab.iot.common.service.SecurityFrameworkService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageInternalAccessVerifierTest {

    private static final String TOKEN = "message-internal-test-token-32-bytes";

    @Test
    void allowsAdminWithoutServiceToken() {
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        when(security.isAdminUser()).thenReturn(true);
        MessageInternalAccessVerifier verifier = new MessageInternalAccessVerifier(security, "");

        assertDoesNotThrow(() -> verifier.verify(null));
    }

    @Test
    void allowsValidServiceTokenWithoutLogin() {
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        MessageInternalAccessVerifier verifier = new MessageInternalAccessVerifier(security, TOKEN);

        assertDoesNotThrow(() -> verifier.verify(TOKEN));
    }

    @Test
    void rejectsMemberOrAnonymousRequestWithoutValidServiceToken() {
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        MessageInternalAccessVerifier verifier = new MessageInternalAccessVerifier(security, TOKEN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify("wrong-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void failsClosedForInternalCallsWhenTokenIsNotConfigured() {
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        MessageInternalAccessVerifier verifier = new MessageInternalAccessVerifier(security, "");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> verifier.verify(null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }
}
