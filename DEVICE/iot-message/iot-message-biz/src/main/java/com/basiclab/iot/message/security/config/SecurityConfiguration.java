package com.basiclab.iot.message.security.config;

import com.basiclab.iot.common.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

/**
 * Device 模块的 Security 配置
 */
@Configuration(proxyBeanMethods = false, value = "messageSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("messageAuthorizeRequestsCustomizer")
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
                // VIDEO / iot-device 告警通知链路：控制器校验独立服务令牌或后台管理员身份。
                registry.antMatchers(HttpMethod.GET,
                        "/message/template/get",
                        "/message/preview/user/group/query",
                        "/message/preview/user/query"
                ).permitAll();
                // 其余消息管理、发送和用户数据接口仅允许管理后台用户。
                registry.antMatchers("/message/**").access("@ss.isAdminUser()");
            }

        };
    }

}
