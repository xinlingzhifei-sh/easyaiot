package com.basiclab.iot.message.service;

import com.basiclab.iot.message.domain.model.AlertNotificationMessage;

/**
 * 告警通知服务接口
 *
 * @author reese
 * @email reese
 */
public interface AlertNotificationService {
    
    /**
     * 处理告警通知
     *
     * @param notificationMessage 告警通知消息
     */
    void processAlertNotification(AlertNotificationMessage notificationMessage);

    /**
     * 判断告警消息是否具备可发送的通知配置
     */
    boolean hasNotificationConfig(
            java.util.List<java.util.Map<String, Object>> channels,
            java.util.List<java.util.Map<String, Object>> notifyUsers);
}

