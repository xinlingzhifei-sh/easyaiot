package com.basiclab.iot.system.framework.security.config;

import com.basiclab.iot.common.config.AuthorizeRequestsCustomizer;
import com.basiclab.iot.common.web.config.WebProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SecurityConfigurationTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void sessionProbeBypassesTheAuthenticationEntryPoint() {
        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry =
                mock(ExpressionUrlAuthorizationConfigurer.ExpressionInterceptUrlRegistry.class, RETURNS_DEEP_STUBS);
        AuthorizeRequestsCustomizer customizer = new SecurityConfiguration().authorizeRequestsCustomizer();
        ReflectionTestUtils.setField(customizer, "webProperties", new WebProperties());

        customizer.customize(registry);

        verify(registry).antMatchers(HttpMethod.GET, "/admin-api/system/auth/check-session");
    }

    @Test
    void sessionProbeBypassesTenantHeaderRequirement() throws IOException {
        String applicationYaml = StreamUtils.copyToString(
                new ClassPathResource("application.yaml").getInputStream(),
                StandardCharsets.UTF_8
        );

        assertTrue(applicationYaml.contains(
                "- /admin-api/system/auth/check-session # Nginx auth_request"));
    }
}
