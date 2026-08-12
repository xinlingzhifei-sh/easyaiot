package com.basiclab.iot.sink.messagebus.subscriber;

import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.messagebus.core.IotMessageSubscriber;
import com.basiclab.iot.sink.messagebus.subscriber.handler.IotDownstreamMessageHandler;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * IotDownstreamMessageSubscriber
 *
 * @author reese
 * @email reese
 */
@Slf4j
@Component
public class IotDownstreamMessageSubscriber
        implements IotMessageSubscriber<IotDeviceMessage>, SmartInitializingSingleton {

    @Resource
    @Lazy
    private IotMessageBus messageBus;

    @Autowired(required = false)
    private List<IotDownstreamMessageHandler> downstreamMessageHandlers;

    @Override
    public void afterSingletonsInstantiated() {
        messageBus.register(this);
        log.info("[afterSingletonsInstantiated][IoT 网关下行消息订阅成功，主题：{}]", getTopic());
    }

    @Override
    public String getTopic() {
        return IotDeviceMessage.MESSAGE_BUS_DEVICE_MESSAGE_TOPIC;
    }

    @Override
    public String getGroup() {
        return "iot-gateway-downstream-subscriber";
    }

    @Override
    public void onMessage(IotDeviceMessage message) {
        if (message == null) {
            log.warn("[onMessage][接收到空的下行消息]");
            return;
        }
        log.debug("[onMessage][接收到下行消息, messageId: {}, method: {}, deviceId: {}, serverId: {}]",
                message.getId(), message.getMethod(), message.getDeviceId(), message.getServerId());

        try {
            if (message.getMethod() == null) {
                log.warn("[onMessage][消息或方法为空, messageId: {}, deviceId: {}]",
                        message.getId(), message.getDeviceId());
                return;
            }

            if (!isDownstreamMessage(message)) {
                log.debug("[onMessage][消息不是下行消息，跳过处理, messageId: {}, method: {}]",
                        message.getId(), message.getMethod());
                return;
            }

            if (downstreamMessageHandlers != null && !downstreamMessageHandlers.isEmpty()) {
                for (IotDownstreamMessageHandler handler : downstreamMessageHandlers) {
                    try {
                        boolean success = handler.handleDownstreamMessage(message);
                        if (success) {
                            log.debug("[onMessage][下行消息处理成功, messageId: {}, method: {}, deviceId: {}, handler: {}]",
                                    message.getId(), message.getMethod(), message.getDeviceId(),
                                    handler.getClass().getSimpleName());
                        } else {
                            log.warn("[onMessage][下行消息处理失败, messageId: {}, method: {}, deviceId: {}, handler: {}]",
                                    message.getId(), message.getMethod(), message.getDeviceId(),
                                    handler.getClass().getSimpleName());
                        }
                    } catch (Exception e) {
                        log.error("[onMessage][下行消息处理器执行异常, messageId: {}, method: {}, deviceId: {}, handler: {}]",
                                message.getId(), message.getMethod(), message.getDeviceId(),
                                handler.getClass().getSimpleName(), e);
                    }
                }
            } else {
                log.debug("[onMessage][未配置下行消息处理器，跳过处理, messageId: {}]", message.getId());
            }
        } catch (Exception e) {
            log.error("[onMessage][处理下行消息失败, messageId: {}, method: {}, deviceId: {}]",
                    message.getId(), message.getMethod(), message.getDeviceId(), e);
        }
    }

    private boolean isDownstreamMessage(IotDeviceMessage message) {
        return !IotDeviceMessageUtils.isUpstreamMessage(message);
    }
}
