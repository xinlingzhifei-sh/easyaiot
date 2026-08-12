package com.basiclab.iot.sink.controller;

import com.basiclab.iot.common.service.SecurityFrameworkService;
import com.basiclab.iot.device.RemoteProductService;
import com.basiclab.iot.sink.dal.mapper.ProductScriptMapper;
import com.basiclab.iot.sink.javascript.JsScriptManager;
import com.basiclab.iot.sink.service.product.ProductScriptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ProductScriptMethodSecurityIntegrationTest.TestConfiguration.class)
class ProductScriptMethodSecurityIntegrationTest {

    @Autowired
    private ProductScriptController controller;

    @Autowired
    @Qualifier("ss")
    private SecurityFrameworkService securityFrameworkService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "test-user", "n/a", AuthorityUtils.NO_AUTHORITIES));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        reset(securityFrameworkService);
    }

    @Test
    void rejectsAnonymousCallerThroughMethodSecurityProxy() {
        SecurityContextHolder.clearContext();

        assertThrows(AuthenticationCredentialsNotFoundException.class, controller::templates);
    }

    @Test
    void rejectsAuthenticatedCallerWithoutSuperAdminRole() {
        when(securityFrameworkService.hasRole("super_admin")).thenReturn(false);

        assertThrows(AccessDeniedException.class, controller::templates);
    }

    @Test
    void allowsAuthenticatedSuperAdminThroughMethodSecurityProxy() {
        when(securityFrameworkService.hasRole("super_admin")).thenReturn(true);

        assertDoesNotThrow(controller::templates);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfiguration {

        @Bean("ss")
        SecurityFrameworkService securityFrameworkService() {
            return mock(SecurityFrameworkService.class);
        }

        @Bean
        ProductScriptController productScriptController() {
            return new ProductScriptController(
                    mock(ProductScriptService.class),
                    mock(ProductScriptMapper.class),
                    mock(JsScriptManager.class),
                    mock(RemoteProductService.class));
        }
    }
}
