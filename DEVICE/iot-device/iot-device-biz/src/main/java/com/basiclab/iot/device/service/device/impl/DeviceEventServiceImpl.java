package com.basiclab.iot.device.service.device.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.basiclab.iot.device.dal.pgsql.device.DeviceEventMapper;
import com.basiclab.iot.device.domain.device.vo.DeviceEvent;
import com.basiclab.iot.device.service.device.DeviceEventService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * DeviceEventServiceImpl
 *
 * @author reese
 * @email reese
 */
@Service
public class DeviceEventServiceImpl extends ServiceImpl<DeviceEventMapper, DeviceEvent> implements DeviceEventService {

    @Override
    public List<DeviceEvent> selectDeviceEventList(DeviceEvent deviceEvent) {
        return baseMapper.selectDeviceEventList(deviceEvent);
    }

    @Override
    public DeviceEvent selectDeviceEventById(Long id) {
        return baseMapper.selectDeviceEventById(id);
    }

    @Override
    public int insertDeviceEvent(DeviceEvent deviceEvent) {
        return baseMapper.insertDeviceEvent(deviceEvent);
    }

    @Override
    public int updateDeviceEvent(DeviceEvent deviceEvent) {
        return baseMapper.updateDeviceEvent(deviceEvent);
    }

    @Override
    public int deleteDeviceEventByIds(String[] ids) {
        return baseMapper.deleteDeviceEventByIds(ids);
    }
}

