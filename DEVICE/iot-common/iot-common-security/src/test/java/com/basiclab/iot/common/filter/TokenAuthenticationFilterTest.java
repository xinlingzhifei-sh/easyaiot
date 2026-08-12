package com.basiclab.iot.common.filter;

import com.basiclab.iot.common.config.SecurityProperties;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAuthenticationFilterTest {

    private final TestableTokenAuthenticationFilter filter = new TestableTokenAuthenticationFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldSkipTokenCheckRpcToPreventRecursiveAuthentication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/rpc-api/system/oauth2/token/check");
        request.addHeader("Authorization", "Bearer propagated-token");

        assertTrue(filter.shouldSkip(request));
    }

    @Test
    void shouldAuthenticateNormalApiRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/admin-api/system/auth/media-permission-check");

        assertFalse(filter.shouldSkip(request));
    }

    @Test
    void forgedLoginUserHeaderWithoutTokenDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/product-script/simulate");
        request.addHeader(SecurityFrameworkUtils.LOGIN_USER_HEADER, URLEncoder.encode(
                "{\"id\":1,\"userType\":2,\"tenantId\":1}",
                StandardCharsets.UTF_8.name()
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                assertNull(SecurityFrameworkUtils.getLoginUser()));
    }

    private static final class TestableTokenAuthenticationFilter extends TokenAuthenticationFilter {

        private TestableTokenAuthenticationFilter() {
            super(new SecurityProperties(), null, null);
        }

        private boolean shouldSkip(MockHttpServletRequest request) throws Exception {
            return shouldNotFilter(request);
        }
    }
}
