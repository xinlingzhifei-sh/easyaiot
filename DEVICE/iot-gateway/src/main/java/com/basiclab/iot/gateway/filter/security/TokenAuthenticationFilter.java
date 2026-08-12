package com.basiclab.iot.gateway.filter.security;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.basiclab.iot.common.config.RpcInternalTokenProperties;
import com.basiclab.iot.common.core.KeyValue;
import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.enums.RpcConstants;
import com.basiclab.iot.common.exception.GlobalErrorStatus;
import com.basiclab.iot.common.utils.json.JsonUtils;
import com.basiclab.iot.gateway.util.SecurityFrameworkUtils;
import com.basiclab.iot.gateway.util.WebFrameworkUtils;
import com.basiclab.iot.system.api.oauth2.OAuth2TokenApi;
import com.basiclab.iot.system.api.oauth2.dto.OAuth2AccessTokenCheckRespDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.loadbalancer.reactive.ReactorLoadBalancerExchangeFilterFunction;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.basiclab.iot.common.utils.cache.CacheUtils.buildAsyncReloadingCache;

/**
 * Token 过滤器，验证 token 的有效性
 * 1. 验证通过时，将 userId、userType、tenantId 通过 Header 转发给服务
 * 2. 验证不通过，还是会转发给服务。因为，接口是否需要登录的校验，还是交给服务自身处理
 *
 * @author reese
 * @email reese
 */
@Component
public class TokenAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

    /**
     * CommonResult<OAuth2AccessTokenCheckRespDTO> 对应的 TypeReference 结果，用于解析 checkToken 的结果
     */
    private static final TypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>> CHECK_RESULT_TYPE_REFERENCE
            = new TypeReference<CommonResult<OAuth2AccessTokenCheckRespDTO>>() {};

    /**
     * 空的 LoginUser 的结果
     *
     * 用于解决如下问题：
     * 1. {@link #getLoginUser(ServerWebExchange, String)} 返回 Mono.empty() 时，会导致后续的 flatMap 无法进行处理的问题。
     * 2. {@link #buildUser(String)} 时，如果 Token 已经过期，返回 LOGIN_USER_EMPTY 对象，避免缓存无法刷新
     */
    private static final LoginUser LOGIN_USER_EMPTY = new LoginUser();

    /**
     * 内部 Token 校验依赖不可用时的哨兵值
     */
    private static final LoginUser LOGIN_USER_UNAVAILABLE = new LoginUser();

    private static final List<String> GATEWAY_TOKEN_REQUIRED_PATH_PREFIXES = List.of(
            "/admin-api/sink/product-script",
            "/admin-api/device/protocolCompileXcode"
    );

    private final WebClient webClient;

    private final RpcInternalTokenProperties rpcProperties;

    /**
     * 登录用户的本地缓存
     *
     * key1：多租户的编号
     * key2：访问令牌
     */
    private final LoadingCache<KeyValue<Long, String>, LoginUser> loginUserCache = buildAsyncReloadingCache(Duration.ofMinutes(1),
            new CacheLoader<KeyValue<Long, String>, LoginUser>() {

                @Override
                public LoginUser load(KeyValue<Long, String> token) {
                    String body = checkAccessToken(token.getKey(), token.getValue()).block();
                    return buildUser(body);
                }

            });

    @Autowired
    public TokenAuthenticationFilter(ReactorLoadBalancerExchangeFilterFunction lbFunction,
                                     RpcInternalTokenProperties rpcProperties) {
        // Q：为什么不使用 OAuth2TokenApi 进行调用？
        // A1：Spring Cloud OpenFeign 官方未内置 Reactive 的支持 https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/#reactive-support
        // A2：校验 Token 的 API 需要使用到 header[tenant-id] 传递租户编号，暂时不想编写 RequestInterceptor 实现
        // 因此，这里采用 WebClient，通过 lbFunction 实现负载均衡
        this(WebClient.builder().filter(lbFunction).build(), rpcProperties);
    }

    TokenAuthenticationFilter(WebClient webClient, RpcInternalTokenProperties rpcProperties) {
        this.webClient = webClient;
        this.rpcProperties = rpcProperties;
        if (!rpcProperties.isConfigured()) {
            LOG.error("Gateway 未配置有效的内部 RPC 服务令牌，访问令牌校验将返回 503");
        }
    }

    @Override
    public Mono<Void> filter(final ServerWebExchange exchange, GatewayFilterChain chain) {
        // 移除调用方可伪造的内部身份请求头
        ServerWebExchange sanitizedExchange = SecurityFrameworkUtils.removeUntrustedIdentityHeaders(exchange);
        ServerHttpResponse response = sanitizedExchange.getResponse();

        // 情况一，如果没有 Token 令牌，则直接继续 filter
        String token = SecurityFrameworkUtils.obtainAuthorization(sanitizedExchange);
        if (StrUtil.isEmpty(token)) {
            if (requiresGatewayToken(sanitizedExchange)) {
                return error(response, HttpStatus.UNAUTHORIZED,
                        JSON.toJSONString(GlobalErrorStatus.UNAUTHORIZED));
            }
            return chain.filter(sanitizedExchange);
        }

        if (!rpcProperties.isConfigured()) {
            return serviceUnavailable(response);
        }

        // 情况二，如果有 Token 令牌，则解析对应 userId、userType、tenantId 等字段，并通过 通过 Header 转发给服务
        // 重要说明：defaultIfEmpty 作用，保证 Mono.empty() 情况，可以继续执行 `flatMap 的 chain.filter(exchange)` 逻辑，避免返回给前端空的 Response！！
        return getLoginUser(sanitizedExchange, token)
                .defaultIfEmpty(LOGIN_USER_EMPTY)
                .onErrorReturn(LOGIN_USER_UNAVAILABLE)
                .flatMap(user -> {
            if (user == LOGIN_USER_UNAVAILABLE) {
                return serviceUnavailable(response);
            }
            // 1. 无用户，直接 filter 继续请求
            if (user == LOGIN_USER_EMPTY) {
                return error(response, HttpStatus.UNAUTHORIZED,
                        JSON.toJSONString(GlobalErrorStatus.UNAUTHORIZED));
//                return chain.filter(exchange);
            }

            // 2.1 有用户，则设置登录用户
            SecurityFrameworkUtils.setLoginUser(sanitizedExchange, user);
            // 2.2 将 user 并设置到 login-user 的请求头，使用 json 存储值
            ServerWebExchange newExchange = sanitizedExchange.mutate()
                    .request(builder -> SecurityFrameworkUtils.setLoginUserHeader(builder, user)).build();
            return chain.filter(newExchange);
        });
    }

    private Mono<LoginUser> getLoginUser(ServerWebExchange exchange, String token) {
        // 从缓存中，获取 LoginUser
        Long tenantId = WebFrameworkUtils.getTenantId(exchange);
        KeyValue<Long, String> cacheKey = new KeyValue<Long, String>().setKey(tenantId).setValue(token);
        LoginUser localUser = loginUserCache.getIfPresent(cacheKey);
        if (localUser != null) {
            return Mono.just(localUser);
        }

        // 缓存不存在，则请求远程服务
        return checkAccessToken(tenantId, token).flatMap((Function<String, Mono<LoginUser>>) body -> {
            LoginUser remoteUser = buildUser(body);
            if (remoteUser != null && remoteUser.getId() != null) {
                // 非空，则进行缓存
                loginUserCache.put(cacheKey, remoteUser);
                return Mono.just(remoteUser);
            }
            return Mono.empty();
        });
    }

    private Mono<String> checkAccessToken(Long tenantId, String token) {
        return webClient.get()
                .uri(OAuth2TokenApi.URL_CHECK, uriBuilder -> uriBuilder.queryParam("accessToken", token).build())
                .header(RpcConstants.RPC_INTERNAL_TOKEN_HEADER, rpcProperties.getInternalToken())
                .headers(httpHeaders -> WebFrameworkUtils.setTenantIdHeader(tenantId, httpHeaders)) // 设置租户的 Header
                .retrieve().bodyToMono(String.class);
    }

    private LoginUser buildUser(String body) {
        // 处理结果，结果不正确
        CommonResult<OAuth2AccessTokenCheckRespDTO> result = JsonUtils.parseObject(body, CHECK_RESULT_TYPE_REFERENCE);
        if (result == null) {
            throw new IllegalStateException("Token 校验服务返回空结果");
        }
        if (result.isError()) {
            // 特殊情况：令牌已经过期（code = 401），需要返回 LOGIN_USER_EMPTY，避免 Token 一直因为缓存，被误判为有效
            if (Objects.equals(result.getCode(), HttpStatus.UNAUTHORIZED.value())) {
                return LOGIN_USER_EMPTY;
            }
            throw new IllegalStateException("Token 校验服务返回非预期错误");
        }

        // 创建登录用户
        OAuth2AccessTokenCheckRespDTO tokenInfo = result.getData();
        return new LoginUser().setId(tokenInfo.getUserId()).setUserType(tokenInfo.getUserType())
                .setInfo(tokenInfo.getUserInfo()) // 额外的用户信息
                .setTenantId(tokenInfo.getTenantId()).setScopes(tokenInfo.getScopes());
    }

    @Override
    public int getOrder() {
        return -100; // 和 Spring Security Filter 的顺序对齐
    }

    private static boolean requiresGatewayToken(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return GATEWAY_TOKEN_REQUIRED_PATH_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private Mono<Void> serviceUnavailable(ServerHttpResponse response) {
        return error(response, HttpStatus.SERVICE_UNAVAILABLE, JsonUtils.toJsonString(
                CommonResult.error(HttpStatus.SERVICE_UNAVAILABLE.value(), "认证服务暂时不可用")));
    }

    private Mono<Void> error(ServerHttpResponse response, HttpStatus status, String json) {
        response.getHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
        response.setStatusCode(status);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
