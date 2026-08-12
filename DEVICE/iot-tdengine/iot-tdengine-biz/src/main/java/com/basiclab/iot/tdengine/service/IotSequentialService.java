package com.basiclab.iot.tdengine.service;

import com.basiclab.iot.tdengine.domain.IotSequential;

import java.util.List;

/**
 * @program: yFeiEye
 * @description:
 * @packagename: com.basiclab.iot.tdengine.service.impl
 * @author reese
 * @email reese
 * @date: 2025-11-04 10:50
 **/
public interface IotSequentialService {

    public IotSequential selectByTime(String startTime);

    public List<IotSequential> getList(IotSequential iotSequential);


    public int save(IotSequential iotSequential);
}
