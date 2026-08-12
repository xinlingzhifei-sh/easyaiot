package com.basiclab.iot.gateway.filter.security;

import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.enums.RpcConstants;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TokenAuthenticationFilterTest {

    private static final String RPC_TOKEN =
            "0123456789abcdef0123456789abcdef0123456789a";

    @Test
    void missingTokenStillRemovesSpoofedLoginUserHeaderBeforeForwarding() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                mock(ReactorLoadBalancerExchangeFilterFunction.class), configuredProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin-api/system/tenant/get-by-website")
                        .header("login-user", "forged-user")
                        .header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, "forged-rpc-token")
                        .build());
        AtomicReference<ServerWebExchange> forwardedExchange = new AtomicReference<>();
        GatewayFilterChain chain = forwarded -> {
            forwardedExchange.set(forwarded);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        ServerWebExchange forwarded = forwardedExchange.get();
        assertNotNull(forwarded);
        assertNotSame(exchange, forwarded);
        assertFalse(forwarded.getRequest().getHeaders().containsKey("login-user"));
        assertFalse(forwarded.getRequest().getHeaders()
                .containsKey(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
    }

    @Test
    void missingTokenCannotReachServerSideScriptManagementRoute() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                mock(ReactorLoadBalancerExchangeFilterFunction.class), configuredProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/admin-api/sink/product-script/simulate").build());
        AtomicBoolean forwarded = new AtomicBoolean();
        GatewayFilterChain chain = ignored -> {
            forwarded.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void missingTokenCannotReachDynamicJavaCompilationRoute() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                mock(ReactorLoadBalancerExchangeFilterFunction.class), configuredProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post(
                        "/admin-api/device/protocolCompileXcode/dynamicallyXcode").build());
        AtomicBoolean forwarded = new AtomicBoolean();
        GatewayFilterChain chain = ignored -> {
            forwarded.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(1));

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void configuredRpcTokenIsSentToSystemTokenCheck() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        WebClient webClient = webClientReturning(
                capturedRequest,
                "{\"code\":0,\"data\":{\"userId\":1,\"userType\":2,"
                        + "\"tenantId\":1,\"userInfo\":{},\"scopes\":[]}}"
        );
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                webClient, configuredProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin-api/system/tenant/get")
                        .header("Authorization", "Bearer user-token")
                        .header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, "forged-rpc-token")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertTrue(forwarded.get());
        assertNotNull(capturedRequest.get());
        assertEquals(RPC_TOKEN, capturedRequest.get().headers()
                .getFirst(RpcConstants.RPC_INTERNAL_TOKEN_HEADER));
    }

    @Test
    void unconfiguredRpcTokenFailsClosedWithServiceUnavailable() {
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                mock(ReactorLoadBalancerExchangeFilterFunction.class),
                new RpcInternalTokenProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin-api/system/tenant/get")
                        .header("Authorization", "Bearer user-token")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    @Test
    void invalidUserTokenRemainsUnauthorized() {
        WebClient webClient = webClientReturning(
                new AtomicReference<>(), "{\"code\":401,\"msg\":\"expired\"}");
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                webClient, configuredProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin-api/system/tenant/get")
                        .header("Authorization", "Bearer expired-token")
                        .build());
        AtomicBoolean forwarded = new AtomicBoolean();

        filter.filter(exchange, ignored -> {
            forwarded.set(true);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        assertFalse(forwarded.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    void tokenCheckDependencyFailureReturnsServiceUnavailable() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.error(
                        new IllegalStateException("dependency unavailable")))
                .build();
        TokenAuthenticationFilter filter = new TokenAuthenticationFilter(
                webClient, configuredProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/admin-api/system/tenant/get")
                        .header("Authorization", "Bearer user-token")
                        .build());

        filter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(1));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
    }

    private static RpcInternalTokenProperties configuredProperties() {
        RpcInternalTokenProperties properties = new RpcInternalTokenProperties();
        properties.setInternalToken(RPC_TOKEN);
        return properties;
    }

    private static WebClient webClientReturning(
            AtomicReference<ClientRequest> capturedRequest, String body) {
        return WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body(body).build());
                })
                .build();
    }
}
