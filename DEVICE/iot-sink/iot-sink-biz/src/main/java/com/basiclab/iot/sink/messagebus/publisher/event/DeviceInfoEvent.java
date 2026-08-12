package com.basiclab.iot.sink.messagebus.publisher.event;

import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;

/**
 * DeviceInfoEvent
 *
 * @author reese
 * @email reese
 */

public class DeviceInfoEvent extends AbstractIotDeviceEvent {

    public DeviceInfoEvent(Object source, IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        super(source, message, topicEnum);
    }
}

