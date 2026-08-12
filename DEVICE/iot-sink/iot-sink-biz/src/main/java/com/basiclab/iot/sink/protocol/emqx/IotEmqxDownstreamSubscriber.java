package com.basiclab.iot.sink.protocol.emqx;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.common.core.util.TenantUtils;
import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.messagebus.core.IotMessageSubscriber;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.protocol.emqx.router.IotEmqxDownstreamHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * IotEmqxDownstreamSubscriber
 *
 * 延迟注册消息总线，避免与 IotKafkaMessageBus 循环依赖。
 *
 * @author reese
 * @email reese
 */
@Slf4j
public class IotEmqxDownstreamSubscriber
        implements IotMessageSubscriber<IotDeviceMessage>, SmartInitializingSingleton {

    private final IotEmqxDownstreamHandler downstreamHandler;

    private final IotMessageBus messageBus;

    private final IotEmqxUpstreamProtocol protocol;

    public IotEmqxDownstreamSubscriber(IotEmqxUpstreamProtocol protocol, IotMessageBus messageBus) {
        this.protocol = protocol;
        this.messageBus = messageBus;
        this.downstreamHandler = new IotEmqxDownstreamHandler(protocol);
    }

    @Override
    public void afterSingletonsInstantiated() {
        messageBus.register(this);
        log.info("[afterSingletonsInstantiated][EMQX 下行订阅器注册成功，主题：{}]", getTopic());
    }

    @Override
    public String getTopic() {
        // 订阅通用设备消息 Topic：iot-device 下行通常不带 serverId。
        // 仍兼容带 serverId 的网关专用 Topic（见 afterSingletonsInstantiated 双注册需求时由总线扩展）。
        // 此处以通用 Topic 为主，确保直连与「经网关代理子设备」下行都能到达 EMQX Publish。
        return IotDeviceMessage.MESSAGE_BUS_DEVICE_MESSAGE_TOPIC;
    }

    @Override
    public String getGroup() {
        return "iot-emqx-downstream-subscriber-" + protocol.getServerId();
    }

    @Override
    public void onMessage(IotDeviceMessage message) {
        if (message == null) {
            log.warn("[onMessage][接收到空的下行消息]");
            return;
        }
        log.debug("[onMessage][接收到下行消息, messageId: {}, method: {}, deviceId: {}]",
                message.getId(), message.getMethod(), message.getDeviceId());
        try {
            // 指定网关 Topic 只应承载下行消息，防止历史或异常消息形成 MQTT 回环。
            if (StrUtil.isNotBlank(message.getTopic())) {
                IotDeviceTopicEnum topicEnum = IotDeviceTopicEnum.matchTopic(message.getTopic());
                if (topicEnum != null && !topicEnum.isNeedReply()) {
                    log.debug("[onMessage][忽略上行消息, messageId: {}, topic: {}]",
                            message.getId(), message.getTopic());
                    return;
                }
            }

            String method = message.getMethod();
            if (method == null) {
                log.warn("[onMessage][消息方法为空, messageId: {}, deviceId: {}]",
                        message.getId(), message.getDeviceId());
                return;
            }

            if (message.getTenantId() == null) {
                log.warn("[onMessage][下行消息缺少tenantId, messageId: {}]", message.getId());
                return;
            }

            TenantUtils.execute(message.getTenantId(), () -> downstreamHandler.handle(message));
        } catch (Exception e) {
            log.error("[onMessage][处理下行消息失败, messageId: {}, method: {}, deviceId: {}]",
                    message.getId(), message.getMethod(), message.getDeviceId(), e);
        }
    }
}
