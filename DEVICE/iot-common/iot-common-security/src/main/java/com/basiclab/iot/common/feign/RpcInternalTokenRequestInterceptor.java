package com.basiclab.iot.common.feign;

import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.enums.RpcConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 为内部 RPC Feign 请求附加受管服务令牌
 */
public class RpcInternalTokenRequestInterceptor implements RequestInterceptor {

    private final RpcInternalTokenProperties properties;

    public RpcInternalTokenRequestInterceptor(RpcInternalTokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        String path = template.path();
        if (path == null || !(path.equals(RpcConstants.RPC_API_PREFIX)
                || path.startsWith(RpcConstants.RPC_API_PREFIX + "/"))) {
            return;
        }
        template.removeHeader(RpcConstants.RPC_INTERNAL_TOKEN_HEADER);
        if (properties.isConfigured()) {
            template.header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, properties.getInternalToken());
        }
    }

}
