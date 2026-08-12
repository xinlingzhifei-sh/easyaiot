package com.basiclab.iot.sink.biz.dto;

import lombok.Data;

/**
 * IotDeviceGetReqDTO
 *
 * @author reese
 * @email reese
 */

@Data
public class IotDeviceGetReqDTO {

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

}