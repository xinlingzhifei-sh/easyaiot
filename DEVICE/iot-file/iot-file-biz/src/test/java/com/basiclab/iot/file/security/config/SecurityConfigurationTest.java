package com.basiclab.iot.file.security.config;

import com.basiclab.iot.common.config.AuthorizeRequestsCustomizer;
import com.basiclab.iot.file.enums.ApiConstants;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SecurityConfigurationTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void uploadEndpointsAreNotAnonymous() {
        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry =
                mock(ExpressionUrlAuthorizationConfigurer.ExpressionInterceptUrlRegistry.class, RETURNS_DEEP_STUBS);
        AuthorizeRequestsCustomizer customizer = new SecurityConfiguration().authorizeRequestsCustomizer();

        customizer.customize(registry);

        verify(registry, never()).antMatchers(ApiConstants.PREFIX_FILE1 + "/**");
        verify(registry, never()).antMatchers(ApiConstants.PREFIX_FILE2 + "/**");
    }

}
