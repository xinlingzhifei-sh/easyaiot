package com.basiclab.iot.sink.framework.security.config;

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

@Configuration(proxyBeanMethods = false, value = "sinkSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("sinkAuthorizeRequestsCustomizer")
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
                // 媒体服务器回调不携带后台登录态，由 MediaHookController 校验独立服务令牌。
                registry.antMatchers(
                        HttpMethod.POST,
                        "/media/hook/srs/on_dvr",
                        "/media/hook/zlm/on_record_mp4",
                        "/media/hook/health").permitAll();
                // 未显式公开的管理写接口只允许管理后台用户，阻断 App Token 越权。
                registry.antMatchers(HttpMethod.POST, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.PUT, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.PATCH, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.DELETE, "/**").access("@ss.isAdminUser()");
            }

        };
    }

}
