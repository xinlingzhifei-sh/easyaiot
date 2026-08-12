package com.genersoft.iot.vmp.framework.security.config;

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

@Configuration(proxyBeanMethods = false, value = "gb28181SecurityConfiguration")
public class SecurityConfiguration {

    @Bean("gb28181AuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry) {
                // TODO yFeiEye：这个每个项目都需要重复配置，得捉摸有没通用的方案
                // Swagger 接口文档
                registry.antMatchers("/v3/api-docs/**").permitAll() // 元数据
                        .antMatchers("/swagger-ui.html").permitAll() // Swagger UI
                        // 媒体回调不使用用户 JWT，由 MediaHookTokenFilter 独立鉴权。
                        .antMatchers("/index/hook/**").permitAll();
                // Druid 监控
                registry.antMatchers("/druid/**").access("@ss.isAdminUser()");
                // Spring Boot Actuator 的安全配置
                registry.antMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .antMatchers("/actuator", "/actuator/**").access("@ss.isAdminUser()");
                // Note: ApiConstants not available in gb28181 module, so RPC service config is omitted
                // If needed, create ApiConstants class in iot-gb28181-api module
            }

        };
    }

}
