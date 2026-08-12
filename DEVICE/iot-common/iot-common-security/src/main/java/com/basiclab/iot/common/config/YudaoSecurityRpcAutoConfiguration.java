package com.basiclab.iot.common.config;

import com.basiclab.iot.common.feign.RpcInternalTokenRequestInterceptor;
import com.basiclab.iot.common.rpc.LoginUserRequestInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Security 使用到 Feign 的配置项
 *
 * @author reese
 * @email reese
 */
@AutoConfiguration
public class YudaoSecurityRpcAutoConfiguration {

    @Bean
    public LoginUserRequestInterceptor loginUserRequestInterceptor() {
        return new LoginUserRequestInterceptor();
    }

    @Bean
    public RpcInternalTokenRequestInterceptor rpcInternalTokenRequestInterceptor(
            RpcInternalTokenProperties properties) {
        return new RpcInternalTokenRequestInterceptor(properties);
    }

}
