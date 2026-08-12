package com.basiclab.iot.common.handler;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RpcSecurityStatusHandlerTest {

    @Test
    void anonymousRpcDenialUsesHttpForbidden() {
        MockHttpServletRequest request = rpcRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AuthenticationEntryPointImpl().commence(
                request, response, new InsufficientAuthenticationException("test"));

        assertEquals(403, response.getStatus());
    }

    @Test
    void authenticatedRpcDenialUsesHttpForbidden() throws Exception {
        MockHttpServletRequest request = rpcRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new AccessDeniedHandlerImpl().handle(
                request, response, new AccessDeniedException("test"));

        assertEquals(403, response.getStatus());
    }

    private static MockHttpServletRequest rpcRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/rpc-api/system/oauth2/token/check");
        return request;
    }

}
