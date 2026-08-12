package com.basiclab.iot.device.dal.pgsql.device;

import com.basiclab.iot.device.domain.device.vo.DeviceServiceInvokeResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * DeviceServiceInvokeResponseMapper
 *
 * @author reese
 * @email reese
 */
@Mapper
public interface DeviceServiceInvokeResponseMapper {

    /**
     * 插入记录
     *
     * @param record 记录
     * @return 插入数量
     */
    int insert(DeviceServiceInvokeResponse record);

    /**
     * 根据主键查询
     *
     * @param id 主键
     * @return 记录
     */
    DeviceServiceInvokeResponse selectByPrimaryKey(@Param("id") Long id);

    /**
     * 根据消息ID查询
     *
     * @param messageId 消息ID
     * @return 记录
     */
    DeviceServiceInvokeResponse selectByMessageId(@Param("messageId") String messageId);

    /**
     * 根据设备ID查询
     *
     * @param deviceId 设备ID
     * @return 记录列表
     */
    List<DeviceServiceInvokeResponse> selectByDeviceId(@Param("deviceId") Long deviceId);

    /**
     * 根据请求编号查询（取最新一条）
     */
    DeviceServiceInvokeResponse selectByRequestId(@Param("requestId") String requestId);

    /**
     * 更新响应结果（用于 PENDING → SUCCESS/FAILED）
     */
    int updateResponse(DeviceServiceInvokeResponse record);
}

