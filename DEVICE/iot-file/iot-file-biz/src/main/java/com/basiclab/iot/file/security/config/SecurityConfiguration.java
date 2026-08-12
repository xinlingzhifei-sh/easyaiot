package com.basiclab.iot.file.security.config;

import com.basiclab.iot.common.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

/**
 * SecurityConfiguration
 *
 * @author reese
 * @email reese
 */

@Configuration(proxyBeanMethods = false, value = "fileSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("fileAuthorizeRequestsCustomizer")
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
            }

        };
    }

}
