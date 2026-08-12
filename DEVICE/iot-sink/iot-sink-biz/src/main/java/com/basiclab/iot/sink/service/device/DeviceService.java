package com.basiclab.iot.sink.service.device;

import com.basiclab.iot.sink.biz.dto.IotDeviceRespDTO;
import com.basiclab.iot.sink.dal.dataobject.DeviceDO;

/**
 * DeviceService
 *
 * @author reese
 * @email reese
 */

public interface DeviceService {

    /**
     * 根据产品唯一标识和设备唯一标识获取设备信息
     *
     * @param productIdentification 产品唯一标识
     * @param deviceIdentification 设备唯一标识
     * @return 设备信息
     */
    IotDeviceRespDTO getDevice(String productIdentification, String deviceIdentification);

    /**
     * 按产品/设备标识查询（忽略租户上下文）。
     * <p>
     * 用于私有协议上行：payload 非 JSON，无法从消息体解析 tenantId 时，先定位设备再进入租户上下文。
     */
    IotDeviceRespDTO getDeviceIgnoreTenant(String productIdentification, String deviceIdentification);

    /**
     * 根据ID获取设备信息
     *
     * @param id 设备ID
     * @return 设备信息
     */
    IotDeviceRespDTO getDevice(Long id);

    /**
     * 根据客户端ID、用户名、密码、设备状态和协议类型查询设备（用于认证）
     *
     * @param clientId 客户端ID
     * @param userName 用户名
     * @param password 密码
     * @param deviceStatus 设备状态
     * @param protocolType 协议类型
     * @return 设备信息
     */
    DeviceDO getDeviceForAuth(String clientId, String userName, String password, String deviceStatus, String protocolType);

    DeviceDO getDeviceForProtocolAuth(String clientId, String productIdentification, String deviceIdentification,
                                      String deviceStatus, String protocolType);
}

