package com.basiclab.iot.sink.messagebus.subscriber;

import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.messagebus.core.IotMessageSubscriber;
import com.basiclab.iot.sink.messagebus.subscriber.handler.IotUpstreamMessageHandler;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * IotUpstreamMessageSubscriber
 *
 * 使用 SmartInitializingSingleton 延迟注册，避免与 IotMessageBus 循环依赖，
 * 并确保在所有单例 Bean 创建完成后再挂载 Kafka 消费。
 *
 * @author reese
 * @email reese
 */
@Slf4j
@Component
public class IotUpstreamMessageSubscriber
        implements IotMessageSubscriber<IotDeviceMessage>, SmartInitializingSingleton {

    @Resource
    @Lazy
    private IotMessageBus messageBus;

    @Autowired(required = false)
    private List<IotUpstreamMessageHandler> upstreamMessageHandlers;

    @Override
    public void afterSingletonsInstantiated() {
        messageBus.register(this);
        log.info("[afterSingletonsInstantiated][IoT 网关上行消息订阅成功，主题：{}]", getTopic());
    }

    @Override
    public String getTopic() {
        return IotDeviceMessage.MESSAGE_BUS_DEVICE_MESSAGE_TOPIC;
    }

    @Override
    public String getGroup() {
        return "iot-gateway-upstream-subscriber";
    }

    @Override
    public void onMessage(IotDeviceMessage message) {
        if (message == null) {
            log.warn("[onMessage][接收到空的上行消息]");
            return;
        }

        log.debug("[onMessage][接收到上行消息, messageId: {}, method: {}, deviceId: {}, serverId: {}]",
                message.getId(), message.getMethod(), message.getDeviceId(), message.getServerId());

        try {
            if (message.getMethod() == null) {
                log.warn("[onMessage][消息或方法为空, messageId: {}, deviceId: {}]",
                        message.getId(), message.getDeviceId());
                return;
            }

            if (upstreamMessageHandlers != null && !upstreamMessageHandlers.isEmpty()) {
                boolean handled = false;
                for (IotUpstreamMessageHandler handler : upstreamMessageHandlers) {
                    try {
                        boolean success = handler.handleUpstreamMessage(message);
                        if (success) {
                            handled = true;
                            log.debug("[onMessage][上行消息处理成功, messageId: {}, method: {}, deviceId: {}, handler: {}]",
                                    message.getId(), message.getMethod(), message.getDeviceId(),
                                    handler.getClass().getSimpleName());
                            break;
                        }
                        log.debug("[onMessage][处理器未匹配上行消息, messageId: {}, method: {}, deviceId: {}, handler: {}]",
                                message.getId(), message.getMethod(), message.getDeviceId(),
                                handler.getClass().getSimpleName());
                    } catch (Exception e) {
                        log.error("[onMessage][上行消息处理器执行异常, messageId: {}, method: {}, deviceId: {}, handler: {}]",
                                message.getId(), message.getMethod(), message.getDeviceId(),
                                handler.getClass().getSimpleName(), e);
                    }
                }
                if (!handled) {
                    log.warn("[onMessage][没有处理器接收上行消息, messageId: {}, method: {}, deviceId: {}, topic: {}]",
                            message.getId(), message.getMethod(), message.getDeviceId(), message.getTopic());
                }
            } else {
                log.debug("[onMessage][未配置上行消息处理器，跳过处理, messageId: {}]", message.getId());
            }
        } catch (Exception e) {
            log.error("[onMessage][处理上行消息失败, messageId: {}, method: {}, deviceId: {}]",
                    message.getId(), message.getMethod(), message.getDeviceId(), e);
        }
    }
}
