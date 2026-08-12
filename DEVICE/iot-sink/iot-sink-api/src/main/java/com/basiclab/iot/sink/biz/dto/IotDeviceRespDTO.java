package com.basiclab.iot.sink.biz.dto;

import lombok.Data;

/**
 * IotDeviceRespDTO
 *
 * @author reese
 * @email reese
 */

@Data
public class IotDeviceRespDTO {

    /**
     * 设备编号
     */
    private Long id;
    /**
     * 产品唯一标识
     */
    private String productIdentification;
    /**
     * 设备唯一标识
     */
    private String deviceIdentification;
    /**
     * 协议类型（如 MQTT）
     */
    private String protocolType;
    /**
     * 设备 IP 地址
     */
    private String ipAddress;
    /**
     * 设备扩展配置 JSON
     */
    private String extension;
    /**
     * 租户编号
     */
    private Long tenantId;

    /**
     * 设备类型：COMMON / GATEWAY / SUBSET / VIDEO_COMMON
     */
    private String deviceType;

    /**
     * 所属网关设备标识（子设备时有值）
     */
    private String parentIdentification;

}
