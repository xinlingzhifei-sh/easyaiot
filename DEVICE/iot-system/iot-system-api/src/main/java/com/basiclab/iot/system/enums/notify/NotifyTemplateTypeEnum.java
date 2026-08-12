package com.basiclab.iot.system.enums.notify;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * NotifyTemplateTypeEnum
 *
 * @author reese
 * @email reese
 */
@Getter
@AllArgsConstructor
public enum NotifyTemplateTypeEnum {

    /**
     * 系统消息
     */
    SYSTEM_MESSAGE(2),
    /**
     * 通知消息
     */
    NOTIFICATION_MESSAGE(1);

    private final Integer type;

}
