package com.basiclab.iot.common.feign;

import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.enums.RpcConstants;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RpcInternalTokenRequestInterceptorTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789a";

    @Test
    void injectsOnlyForRpcPathsAndOverwritesCallerValue() {
        RpcInternalTokenRequestInterceptor interceptor =
                new RpcInternalTokenRequestInterceptor(configuredProperties());
        RequestTemplate rpc = new RequestTemplate().uri(
                "/rpc-api/system/tenant/valid");
        rpc.header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, "forged");

        interceptor.apply(rpc);

        assertEquals(List.of(TOKEN), new ArrayList<>(rpc.headers()
                .get(RpcConstants.RPC_INTERNAL_TOKEN_HEADER)));

        RequestTemplate ordinary = new RequestTemplate().uri("/message/template/get");
        interceptor.apply(ordinary);
        assertFalse(ordinary.headers().containsKey(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
    }

    @Test
    void unconfiguredRpcCallRemovesCallerSuppliedHeader() {
        RpcInternalTokenRequestInterceptor interceptor =
                new RpcInternalTokenRequestInterceptor(new RpcInternalTokenProperties());
        RequestTemplate rpc = new RequestTemplate().uri("/rpc-api/infra/file/presigned-url");
        rpc.header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, "forged");

        interceptor.apply(rpc);

        assertFalse(rpc.headers().containsKey(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
    }

    private static RpcInternalTokenProperties configuredProperties() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(TOKEN);
        return properties;
    }

}
