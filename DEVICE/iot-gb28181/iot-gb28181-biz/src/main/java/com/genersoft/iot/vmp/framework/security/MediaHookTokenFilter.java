package com.genersoft.iot.vmp.framework.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 媒体服务器回调不使用用户 JWT，但必须持有独立的服务令牌。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MediaHookTokenFilter extends OncePerRequestFilter {

    private final String configuredToken;

    public MediaHookTokenFilter(
            @Value("${media.hook-token:}") String configuredToken) {
        this.configuredToken = configuredToken == null ? "" : configuredToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return !path.equals("/index/hook") && !path.startsWith("/index/hook/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!MediaHookTokenSupport.isConfigured(configuredToken)) {
            response.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "GB28181 media hook token is not configured");
            return;
        }

        String suppliedToken = request.getHeader(MediaHookTokenSupport.TOKEN_HEADER);
        if (!StringUtils.hasText(suppliedToken)) {
            suppliedToken = request.getParameter(MediaHookTokenSupport.TOKEN_PARAMETER);
        }
        if (!MediaHookTokenSupport.matches(configuredToken, suppliedToken)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid media hook token");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
