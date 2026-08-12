package com.basiclab.iot.sink.biz.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 * IotDeviceAuthReqDTO
 *
 * @author reese
 * @email reese
 */

@Data
public class IotDeviceAuthReqDTO {

    /**
     * 客户端 ID
     */
    @NotEmpty(message = "客户端 ID 不能为空")
    private String clientId;

    /**
     * 用户名
     */
    @NotEmpty(message = "用户名不能为空")
    private String username;

    /**
     * 产品认证账号；username 保留用于携带“设备标识&产品标识”。
     */
    private String account;

    /**
     * 密码
     */
    private String password;

    /**
     * 接入协议；为空时兼容旧客户端并按 MQTT 认证。
     */
    private String protocolType;

    /**
     * RSA SHA-256 签名（Base64）
     */
    private String signature;

    /**
     * 签名时间戳（毫秒）
     */
    private Long timestamp;

}
