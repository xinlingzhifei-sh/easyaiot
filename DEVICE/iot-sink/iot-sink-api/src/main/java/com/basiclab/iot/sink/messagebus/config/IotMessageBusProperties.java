package com.basiclab.iot.sink.messagebus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;

/**
 * IotMessageBusProperties
 *
 * @author reese
 * @email reese
 */
@ConfigurationProperties("basiclab.iot.message-bus")
@Data
@Validated
public class IotMessageBusProperties {

    /**
     * 消息总线类型
     *
     * 可选值：local、kafka
     */
    @NotNull(message = "IoT 消息总线类型不能为空")
    private String type = "local";

}