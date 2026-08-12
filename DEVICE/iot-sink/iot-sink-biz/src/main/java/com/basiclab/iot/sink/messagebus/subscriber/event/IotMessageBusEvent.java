package com.basiclab.iot.sink.messagebus.subscriber.event;

import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * IotMessageBusEvent
 *
 * @author reese
 * @email reese
 */

@Getter
public class IotMessageBusEvent extends ApplicationEvent {

    /**
     * 设备消息
     */
    private final IotDeviceMessage message;

    /**
     * Topic 枚举
     */
    private final IotDeviceTopicEnum topicEnum;

    public IotMessageBusEvent(Object source, IotDeviceMessage message, IotDeviceTopicEnum topicEnum) {
        super(source);
        this.message = message;
        this.topicEnum = topicEnum;
    }
}

