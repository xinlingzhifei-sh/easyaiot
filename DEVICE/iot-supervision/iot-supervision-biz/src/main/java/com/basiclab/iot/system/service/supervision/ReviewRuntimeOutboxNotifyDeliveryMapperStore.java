package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDeliveryDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeOutboxDeliveryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ReviewRuntimeOutboxNotifyDeliveryMapperStore implements ReviewRuntimeOutboxNotifyDeliveryStore {

    private final SupervisionAlertReviewRuntimeOutboxDeliveryMapper deliveryMapper;

    public ReviewRuntimeOutboxNotifyDeliveryMapperStore(
            SupervisionAlertReviewRuntimeOutboxDeliveryMapper deliveryMapper) {
        this.deliveryMapper = Objects.requireNonNull(deliveryMapper, "deliveryMapper");
    }

    @Override
    public boolean isDelivered(Long outboxId, String channel, Long recipientUserId, String templateCode) {
        SupervisionAlertReviewRuntimeOutboxDeliveryDO delivery =
                deliveryMapper.selectByDeliveryKey(outboxId, channel, recipientUserId, templateCode);
        return delivery != null && "delivered".equals(delivery.getDeliveryStatus());
    }

    @Override
    public void markDelivered(Long outboxId, String eventType, String alertKey, String channel,
                              Long recipientUserId, String templateCode, Long notifyMessageId,
                              LocalDateTime deliveredAt) {
        if (!hasDeliveryKey(outboxId, channel, recipientUserId, templateCode)) {
            return;
        }
        LocalDateTime timestamp = deliveredAt == null ? LocalDateTime.now() : deliveredAt;
        SupervisionAlertReviewRuntimeOutboxDeliveryDO delivery =
                existingOrNew(outboxId, eventType, alertKey, channel, recipientUserId, templateCode);
        delivery.setDeliveryStatus("delivered")
                .setNotifyMessageId(notifyMessageId)
                .setAttemptCount(nextAttemptCount(delivery))
                .setLastError(null)
                .setLastAttemptAt(timestamp)
                .setDeliveredAt(timestamp);
        save(delivery);
    }

    @Override
    public void markFailed(Long outboxId, String eventType, String alertKey, String channel,
                           Long recipientUserId, String templateCode, String lastError,
                           LocalDateTime attemptedAt) {
        if (!hasDeliveryKey(outboxId, channel, recipientUserId, templateCode)) {
            return;
        }
        SupervisionAlertReviewRuntimeOutboxDeliveryDO delivery =
                existingOrNew(outboxId, eventType, alertKey, channel, recipientUserId, templateCode);
        delivery.setDeliveryStatus("failed")
                .setAttemptCount(nextAttemptCount(delivery))
                .setLastError(trimToLength(lastError, 500))
                .setLastAttemptAt(attemptedAt == null ? LocalDateTime.now() : attemptedAt);
        save(delivery);
    }

    private SupervisionAlertReviewRuntimeOutboxDeliveryDO existingOrNew(Long outboxId,
                                                                        String eventType,
                                                                        String alertKey,
                                                                        String channel,
                                                                        Long recipientUserId,
                                                                        String templateCode) {
        SupervisionAlertReviewRuntimeOutboxDeliveryDO delivery =
                deliveryMapper.selectByDeliveryKey(outboxId, channel, recipientUserId, templateCode);
        if (delivery != null) {
            delivery.setEventType(eventType)
                    .setAlertKey(alertKey);
            return delivery;
        }
        return new SupervisionAlertReviewRuntimeOutboxDeliveryDO()
                .setOutboxId(outboxId)
                .setEventType(eventType)
                .setAlertKey(alertKey)
                .setChannel(channel)
                .setRecipientUserId(recipientUserId)
                .setTemplateCode(templateCode)
                .setAttemptCount(0)
                .setVersion(0);
    }

    private void save(SupervisionAlertReviewRuntimeOutboxDeliveryDO delivery) {
        if (delivery.getId() == null) {
            deliveryMapper.insert(delivery);
            return;
        }
        deliveryMapper.updateById(delivery);
    }

    private static boolean hasDeliveryKey(Long outboxId, String channel, Long recipientUserId, String templateCode) {
        return outboxId != null
                && hasText(channel)
                && recipientUserId != null
                && hasText(templateCode);
    }

    private static int nextAttemptCount(SupervisionAlertReviewRuntimeOutboxDeliveryDO delivery) {
        return (delivery.getAttemptCount() == null ? 0 : delivery.getAttemptCount()) + 1;
    }

    private static String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
