package com.genersoft.iot.vmp.framework.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.FilterChain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MediaHookTokenFilterTest {

    private static final String TOKEN = "gb28181-media-hook-test-token-32-bytes";

    @Test
    void acceptsTokenGeneratedForMediaServerHookUrl() throws Exception {
        String hookUrl = MediaHookTokenSupport.appendToUrl(
                "http://127.0.0.1:48088/index/hook/on_publish",
                TOKEN);
        assertTrue(hookUrl.endsWith("hookToken=" + TOKEN));

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/index/hook/on_publish");
        request.setParameter(MediaHookTokenSupport.TOKEN_PARAMETER, TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new MediaHookTokenFilter(TOKEN).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void rejectsInvalidHookToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/index/hook/on_stream_none_reader");
        request.setParameter(MediaHookTokenSupport.TOKEN_PARAMETER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new MediaHookTokenFilter(TOKEN).doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void failsClosedWhenHookTokenIsNotConfigured() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/index/hook/on_server_started");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new MediaHookTokenFilter("").doFilter(request, response, chain);

        assertEquals(503, response.getStatus());
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void doesNotInterceptNonHookRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/device/query/devices");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        new MediaHookTokenFilter("").doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
