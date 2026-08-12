package com.basiclab.iot.common.service;

import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.enums.RpcConstants;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RpcInternalAccessTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789a";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void allowsOnlyConfiguredMatchingHeader() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);
        RpcInternalAccess access = new RpcInternalAccess(properties);

        MockHttpServletRequest valid = new MockHttpServletRequest(
                "GET", "/rpc-api/system/tenant/valid");
        valid.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN);
        assertTrue(access.isAllowed(valid));

        assertFalse(access.isAllowed(new MockHttpServletRequest(
                "GET", "/rpc-api/system/tenant/valid")));
        MockHttpServletRequest wrong = new MockHttpServletRequest(
                "GET", "/rpc-api/system/tenant/valid");
        wrong.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN + "x");
        assertFalse(access.isAllowed(wrong));
    }

    @Test
    void failsClosedWhenConfigurationOrRequestIsMissing() {
        RpcInternalAccess access = new RpcInternalAccess(new RpcInternalTokenProperties());

        assertFalse(access.isAllowed(new MockHttpServletRequest()));
        assertFalse(access.isAllowed(null));
    }

    @Test
    void validServiceIdentityDoesNotCreateLoginUser() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);
        RpcInternalAccess access = new RpcInternalAccess(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN);

        assertTrue(access.isAllowed(request));
        assertNull(SecurityFrameworkUtils.getLoginUser());
    }

}
