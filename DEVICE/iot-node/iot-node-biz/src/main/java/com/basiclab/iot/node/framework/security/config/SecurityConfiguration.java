package com.basiclab.iot.node.framework.security.config;

import com.basiclab.iot.common.config.AuthorizeRequestsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;

@Configuration(proxyBeanMethods = false, value = "nodeSecurityConfiguration")
public class SecurityConfiguration {

    @Bean("nodeAuthorizeRequestsCustomizer")
    public AuthorizeRequestsCustomizer authorizeRequestsCustomizer() {
        return new AuthorizeRequestsCustomizer() {

            @Override
            public void customize(ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry registry) {
                // Swagger 接口文档
                registry.antMatchers("/v3/api-docs/**").permitAll()
                        .antMatchers("/swagger-ui.html").permitAll()
                        // 以下端点由业务层校验各自的 bootstrap/join/agent/peer token。
                        .antMatchers(HttpMethod.GET,
                                "/node/platform-agent-bootstrap",
                                "/node/control-plane/snapshot").permitAll()
                        .antMatchers(HttpMethod.POST,
                                "/node/edge/enroll",
                                "/node/edge/runtime-config",
                                "/node/agent/register",
                                "/node/agent/heartbeat",
                                "/node/control-plane/peer/register").permitAll()
                        // Agent 出站指令通道：业务层强制校验 nodeId + agentToken（enqueue 仍只对管理后台开放）。
                        .antMatchers(HttpMethod.POST,
                                "/node/agent/commands/poll",
                                "/node/agent/commands/*/ack",
                                "/node/agent/commands/*/heartbeat",
                                "/node/agent/commands/*/result").permitAll()
                        // Sentinel 自愈：未登录管理后台时 Controller 要求携带 Agent Token，业务层按节点校验。
                        .antMatchers(HttpMethod.POST,
                                "/node/sentinel/remediate",
                                "/node/sentinel/remediate-report").permitAll();
                // Druid 监控
                registry.antMatchers("/druid/**").access("@ss.isAdminUser()");
                // Spring Boot Actuator 的安全配置
                registry.antMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .antMatchers("/actuator", "/actuator/**").access("@ss.isAdminUser()");
                // 其余节点管理接口（包括读取 Agent 凭据）仅允许管理后台用户。
                registry.antMatchers("/node/**").access("@ss.isAdminUser()");
                // 未显式公开的其他管理写接口同样只允许管理后台用户，阻断 App Token 越权。
                registry.antMatchers(HttpMethod.POST, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.PUT, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.PATCH, "/**").access("@ss.isAdminUser()")
                        .antMatchers(HttpMethod.DELETE, "/**").access("@ss.isAdminUser()");
            }

        };
    }

}
