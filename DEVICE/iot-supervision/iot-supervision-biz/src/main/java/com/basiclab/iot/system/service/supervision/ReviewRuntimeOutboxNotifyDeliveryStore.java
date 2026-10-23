package com.basiclab.iot.system.service.supervision;

import java.time.LocalDateTime;

public interface ReviewRuntimeOutboxNotifyDeliveryStore {

    String CHANNEL_SYSTEM_NOTIFY_ADMIN = "system_notify_admin";

    boolean isDelivered(Long outboxId, String channel, Long recipientUserId, String templateCode);

    void markDelivered(Long outboxId, String eventType, String alertKey, String channel,
                       Long recipientUserId, String templateCode, Long notifyMessageId,
                       LocalDateTime deliveredAt);

    void markFailed(Long outboxId, String eventType, String alertKey, String channel,
                    Long recipientUserId, String templateCode, String lastError,
                    LocalDateTime attemptedAt);

    static ReviewRuntimeOutboxNotifyDeliveryStore noop() {
        return new ReviewRuntimeOutboxNotifyDeliveryStore() {
            @Override
            public boolean isDelivered(Long outboxId, String channel, Long recipientUserId, String templateCode) {
                return false;
            }

            @Override
            public void markDelivered(Long outboxId, String eventType, String alertKey, String channel,
                                      Long recipientUserId, String templateCode, Long notifyMessageId,
                                      LocalDateTime deliveredAt) {
            }

            @Override
            public void markFailed(Long outboxId, String eventType, String alertKey, String channel,
                                   Long recipientUserId, String templateCode, String lastError,
                                   LocalDateTime attemptedAt) {
            }
        };
    }
}
