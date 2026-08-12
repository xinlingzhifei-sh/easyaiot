package com.basiclab.iot.common.enums;

/**
 * RPC 相关的枚举
 *
 * 虽然放在 iot-spring-boot-starter-rpc 会相对合适，但是每个 API 模块需要使用到，所以暂时只好放在此处
 *
 * @author reese
 * @email reese
 */
public class RpcConstants {

    /**
     * RPC API 的前缀
     */
    public static final String RPC_API_PREFIX = "/rpc-api";

    /**
     * 内部 RPC 服务身份请求头
     */
    public static final String RPC_INTERNAL_TOKEN_HEADER = "X-Iot-Rpc-Token";

    /**
     * 内部 RPC 服务令牌最小长度（至少 32 个随机字节的 Base64URL 编码长度）
     */
    public static final int RPC_INTERNAL_TOKEN_MIN_LENGTH = 43;

}
