package com.basiclab.iot.sink.messagebus.publisher;

import com.basiclab.iot.sink.biz.dto.IotDeviceRespDTO;

/**
 * IotDeviceService
 *
 * @author reese
 * @email reese
 */

public interface IotDeviceService {

    /**
     * 根据 productIdentification 和 deviceIdentification 获取设备信息
     *
     * @param productIdentification 产品唯一标识
     * @param deviceIdentification 设备唯一标识
     * @return 设备信息
     */
    IotDeviceRespDTO getDeviceFromCache(String productIdentification, String deviceIdentification);

    /**
     * 根据 id 获取设备信息
     *
     * @param id 设备编号
     * @return 设备信息
     */
    IotDeviceRespDTO getDeviceFromCache(Long id);

}