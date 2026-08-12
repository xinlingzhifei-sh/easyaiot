package com.basiclab.iot.gateway.filter.security;

import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gateway 内部 RPC 服务身份配置
 */
@Configuration(proxyBeanMethods = false)
public class RpcSecurityConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "iot.rpc")
    public RpcInternalTokenProperties rpcInternalTokenProperties() {
        return new RpcInternalTokenProperties();
    }

}
