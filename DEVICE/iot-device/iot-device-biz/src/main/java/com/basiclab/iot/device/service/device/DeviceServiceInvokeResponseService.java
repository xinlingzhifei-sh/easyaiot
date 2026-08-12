package com.basiclab.iot.device.service.device;

import com.basiclab.iot.device.domain.device.vo.DeviceServiceInvokeResponse;

/**
 * DeviceServiceInvokeResponseService
 *
 * @author reese
 * @email reese
 */
public interface DeviceServiceInvokeResponseService {

    /**
     * 保存服务调用响应
     *
     * @param response 响应数据
     * @return 保存的记录
     */
    DeviceServiceInvokeResponse save(DeviceServiceInvokeResponse response);

    /**
     * 根据消息ID查询
     *
     * @param messageId 消息ID
     * @return 响应数据
     */
    DeviceServiceInvokeResponse getByMessageId(String messageId);

    /**
     * 根据请求编号查询
     */
    DeviceServiceInvokeResponse getByRequestId(String requestId);

    /**
     * 更新响应（PENDING → 完成）
     */
    int updateResponse(DeviceServiceInvokeResponse response);
}

