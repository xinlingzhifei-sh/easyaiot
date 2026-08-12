package com.basiclab.iot.device.framework.security.config;

import com.basiclab.iot.common.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

/**
 * SecurityConfiguration
 *
 * @author reese
 * @email reese
 */
@Configuration(proxyBeanMethods = false, value = "deviceSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("deviceAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry) {
                // TODO yFeiEye：这个每个项目都需要重复配置，得捉摸有没通用的方案
                // Swagger 接口文档
                registry.antMatchers("/v3/api-docs/**").permitAll() // 元数据
                        .antMatchers("/swagger-ui.html").permitAll(); // Swagger UI
                // Druid 监控
                registry.antMatchers("/druid/**").access("@ss.isAdminUser()");
                // Spring Boot Actuator 的安全配置
                registry.antMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .antMatchers("/actuator", "/actuator/**").access("@ss.isAdminUser()");
                // 未显式公开的管理写接口只允许管理后台用户，阻断 App Token 越权。
                registry.antMatchers(HttpMethod.POST, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.PUT, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.PATCH, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.DELETE, "/**").access("@ss.isAdminUser()");
            }

        };
    }

}
