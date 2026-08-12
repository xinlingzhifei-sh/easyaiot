package com.basiclab.iot.common.aspect;

import com.basiclab.iot.common.annotations.InnerAuth;
import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.constant.SecurityConstants;
import com.basiclab.iot.common.enums.RpcConstants;
import com.basiclab.iot.common.exception.InnerAuthException;
import com.basiclab.iot.common.service.RpcInternalAccess;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InnerAuthAspectTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789a";

    @AfterEach
    void clearContexts() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void forgeableLegacyHeaderDoesNotAuthenticate() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(SecurityConstants.FROM_SOURCE, SecurityConstants.INNER);
        bindRequest(request);
        InnerAuthAspect aspect = new InnerAuthAspect(configuredAccess());

        assertThrows(InnerAuthException.class,
                () -> aspect.innerAround(pointReturning(null, new AtomicBoolean()), annotation(false)));
    }

    @Test
    void validRpcHeaderProceeds() throws Throwable {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN);
        bindRequest(request);
        AtomicBoolean proceeded = new AtomicBoolean();
        ProceedingJoinPoint point = pointReturning("ok", proceeded);
        InnerAuthAspect aspect = new InnerAuthAspect(configuredAccess());

        assertEquals("ok", aspect.innerAround(point, annotation(false)));
        assertTrue(proceeded.get());
    }

    @Test
    void userRequirementNeedsVerifiedLoginUser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, TOKEN);
        request.addHeader(SecurityConstants.DETAILS_USER_ID, "1");
        request.addHeader(SecurityConstants.DETAILS_USERNAME, "forged");
        bindRequest(request);
        InnerAuthAspect aspect = new InnerAuthAspect(configuredAccess());

        assertThrows(InnerAuthException.class,
                () -> aspect.innerAround(pointReturning(null, new AtomicBoolean()), annotation(true)));
    }

    private static RpcInternalAccess configuredAccess() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);
        return new RpcInternalAccess(properties);
    }

    private static InnerAuth annotation(boolean isUser) {
        return new InnerAuth() {
            @Override
            public boolean isUser() {
                return isUser;
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return InnerAuth.class;
            }
        };
    }

    private static ProceedingJoinPoint pointReturning(Object result, AtomicBoolean proceeded) {
        return (ProceedingJoinPoint) Proxy.newProxyInstance(
                InnerAuthAspectTest.class.getClassLoader(),
                new Class<?>[]{ProceedingJoinPoint.class},
                (proxy, method, args) -> {
                    if ("proceed".equals(method.getName())) {
                        proceeded.set(true);
                        return result;
                    }
                    if ("toString".equals(method.getName())) {
                        return "ProceedingJoinPointTestDouble";
                    }
                    return null;
                });
    }

    private static void bindRequest(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

}
