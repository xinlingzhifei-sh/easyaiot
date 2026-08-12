package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.core.aop.DataPermissionContextHolder;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.enums.CommonStatusEnum;
import com.basiclab.iot.system.dal.dataobject.user.AdminUserDO;
import com.basiclab.iot.system.service.notify.NotifySendService;
import com.basiclab.iot.system.service.supervision.NotifyReviewRuntimeOutboxPublisher;
import com.basiclab.iot.system.service.supervision.ReviewRuntimeOutboxNotifyDeliveryStore;
import com.basiclab.iot.system.service.supervision.ReviewRuntimeOutboxNotifyProperties;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxDeliveryResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxMessage;
import com.basiclab.iot.system.service.user.AdminUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyReviewRuntimeOutboxPublisherTest {

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    void publisherDispatchesOnlyToTheCurrentTenantsConfiguredAdmins() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserService adminUserService = adminUsers(Map.of(
                1001L, enabledUser(1001L, 1L),
                1002L, enabledUser(1002L, 1L),
                2001L, enabledUser(2001L, 121L)
        ));
        ReviewRuntimeOutboxNotifyProperties properties = new ReviewRuntimeOutboxNotifyProperties();
        properties.setTenantAdminUserRoutes("1=1001,1002;121=2001");
        properties.setRuntimeAlertTemplateCode("YFEIEYE_REVIEW_RUNTIME_ALERT");
        properties.setOperationsReportTemplateCode("YFEIEYE_REVIEW_OPERATIONS_REPORT");
        NotifyReviewRuntimeOutboxPublisher publisher = new NotifyReviewRuntimeOutboxPublisher(
                notifySendService, adminUserService, properties, ReviewRuntimeOutboxNotifyDeliveryStore.noop());

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult alertResult = publisher.publish(new ReviewRuntimeOutboxMessage(
                1L,
                "run-1",
                "review_runtime_alert",
                "record_storage_drift:file_missing",
                """
                        {"alert":"record_storage_drift:file_missing","action":"inspect_record_storage","recommendedActions":["inspect storage"],"metadata":{"finalRepairableCount":2}}
                        """,
                0,
                LocalDateTime.of(2026, 7, 8, 15, 0)
        ));
        TenantContextHolder.setTenantId(121L);
        ReviewRuntimeOutboxDeliveryResult reportResult = publisher.publish(new ReviewRuntimeOutboxMessage(
                2L,
                "report-daily",
                "review_operations_report",
                "daily:2026-07-08",
                """
                        {"reportKey":"daily:2026-07-08","reportType":"daily","action":"deliver_operations_report","reviewItemIds":[11,12],"evidenceGaps":["missing record"],"generatedAt":"2026-07-08T00:10:00"}
                        """,
                0,
                LocalDateTime.of(2026, 7, 8, 15, 1)
        ));

        assertTrue(alertResult.success());
        assertNull(alertResult.errorCode());
        assertTrue(reportResult.success());
        assertEquals(3, notifySendService.calls.size());
        assertEquals(List.of(1001L, 1002L, 2001L),
                notifySendService.calls.stream().map(SendCall::userId).toList());
        assertEquals("YFEIEYE_REVIEW_RUNTIME_ALERT", notifySendService.calls.get(0).templateCode());
        assertEquals("record_storage_drift:file_missing",
                notifySendService.calls.get(0).templateParams().get("alertKey"));
        assertEquals("inspect_record_storage",
                notifySendService.calls.get(0).templateParams().get("action"));
        assertEquals("2",
                notifySendService.calls.get(0).templateParams().get("finalRepairableCount"));
        assertEquals("YFEIEYE_REVIEW_OPERATIONS_REPORT", notifySendService.calls.get(2).templateCode());
        assertEquals("daily:2026-07-08",
                notifySendService.calls.get(2).templateParams().get("reportKey"));
        assertEquals("daily",
                notifySendService.calls.get(2).templateParams().get("reportType"));
    }

    @Test
    void publisherSuppliesRequiredActionWhenRuntimePatrolOnlyHasRecommendedActions() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        NotifyReviewRuntimeOutboxPublisher publisher = publisher(
                notifySendService,
                adminUsers(Map.of(1001L, enabledUser(1001L, 1L))),
                "1=1001");

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult result = publisher.publish(new ReviewRuntimeOutboxMessage(
                3L,
                "run-recommended-action",
                "review_runtime_alert",
                "record_storage_drift:file_missing",
                "{\"alert\":\"record_storage_drift:file_missing\","
                        + "\"recommendedActions\":[\"inspect_record_storage\",\"rerun_runtime_reconciliation\"]}",
                0,
                LocalDateTime.of(2026, 7, 13, 22, 30)
        ));

        assertTrue(result.success());
        assertEquals("inspect_record_storage",
                notifySendService.calls.get(0).templateParams().get("action"));
    }

    @Test
    void publisherSkipsAlreadyDeliveredRecipientsWhenRetryingAfterPartialFailure() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        notifySendService.failOnceForUserId = 1002L;
        InMemoryNotifyDeliveryStore deliveryStore = new InMemoryNotifyDeliveryStore();
        AdminUserService adminUserService = adminUsers(Map.of(
                1001L, enabledUser(1001L, 1L),
                1002L, enabledUser(1002L, 1L)
        ));
        ReviewRuntimeOutboxNotifyProperties properties = new ReviewRuntimeOutboxNotifyProperties();
        properties.setTenantAdminUserRoutes("1=1001,1002");
        properties.setRuntimeAlertTemplateCode("YFEIEYE_REVIEW_RUNTIME_ALERT");
        NotifyReviewRuntimeOutboxPublisher publisher = new NotifyReviewRuntimeOutboxPublisher(
                notifySendService, adminUserService, properties, deliveryStore);
        ReviewRuntimeOutboxMessage message = new ReviewRuntimeOutboxMessage(
                10L,
                "run-partial",
                "review_runtime_alert",
                "record_storage_drift:file_missing",
                "{\"alert\":\"record_storage_drift:file_missing\",\"action\":\"inspect_record_storage\"}",
                1,
                LocalDateTime.of(2026, 7, 8, 16, 0)
        );

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult firstAttempt = publisher.publish(message);
        ReviewRuntimeOutboxDeliveryResult retryAttempt = publisher.publish(message);

        assertEquals(false, firstAttempt.success());
        assertEquals("runtime_outbox_notify_send_failed:IllegalStateException", firstAttempt.errorCode());
        assertTrue(retryAttempt.success());
        assertEquals(List.of(1001L, 1002L, 1002L),
                notifySendService.calls.stream().map(SendCall::userId).toList());
        assertTrue(deliveryStore.isDelivered(10L, "system_notify_admin", 1001L,
                "YFEIEYE_REVIEW_RUNTIME_ALERT"));
        assertTrue(deliveryStore.isDelivered(10L, "system_notify_admin", 1002L,
                "YFEIEYE_REVIEW_RUNTIME_ALERT"));
        assertEquals("runtime_outbox_notify_send_failed:IllegalStateException",
                deliveryStore.lastError.get(new DeliveryKey(10L, "system_notify_admin", 1002L,
                        "YFEIEYE_REVIEW_RUNTIME_ALERT")));
    }

    @Test
    void publisherReturnsFailureWhenNotifyRoutingIsNotConfigured() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserService adminUserService = adminUsers(Map.of());
        ReviewRuntimeOutboxNotifyProperties properties = new ReviewRuntimeOutboxNotifyProperties();
        properties.setRuntimeAlertTemplateCode("YFEIEYE_REVIEW_RUNTIME_ALERT");
        NotifyReviewRuntimeOutboxPublisher publisher = new NotifyReviewRuntimeOutboxPublisher(
                notifySendService, adminUserService, properties, ReviewRuntimeOutboxNotifyDeliveryStore.noop());

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult result = publisher.publish(new ReviewRuntimeOutboxMessage(
                3L,
                "run-2",
                "review_runtime_alert",
                "review_data_schema_drift",
                "{\"alert\":\"review_data_schema_drift\"}",
                0,
                LocalDateTime.of(2026, 7, 8, 15, 2)
        ));

        assertEquals(false, result.success());
        assertEquals("runtime_outbox_notify_recipients_not_configured", result.errorCode());
        assertTrue(notifySendService.calls.isEmpty());
    }

    @Test
    void publisherRejectsMalformedTenantRoutesWithoutSending() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserService adminUserService = adminUsers(Map.of(1001L, enabledUser(1001L, 1L)));
        ReviewRuntimeOutboxNotifyProperties properties = new ReviewRuntimeOutboxNotifyProperties();
        properties.setTenantAdminUserRoutes("1=1001;121=not-a-user");
        NotifyReviewRuntimeOutboxPublisher publisher = new NotifyReviewRuntimeOutboxPublisher(
                notifySendService, adminUserService, properties, ReviewRuntimeOutboxNotifyDeliveryStore.noop());

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult result = publisher.publish(runtimeAlert(4L));

        assertEquals(false, result.success());
        assertEquals("runtime_outbox_notify_recipient_routes_invalid", result.errorCode());
        assertTrue(notifySendService.calls.isEmpty());
    }

    @Test
    void publisherRejectsDisabledRecipientWithoutSending() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserDO disabledUser = enabledUser(1001L, 1L);
        disabledUser.setStatus(CommonStatusEnum.DISABLE.getStatus());
        AdminUserService adminUserService = adminUsers(Map.of(1001L, disabledUser));
        NotifyReviewRuntimeOutboxPublisher publisher = publisher(
                notifySendService, adminUserService, "1=1001");

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult result = publisher.publish(runtimeAlert(5L));

        assertEquals(false, result.success());
        assertEquals("runtime_outbox_notify_recipient_disabled", result.errorCode());
        assertTrue(notifySendService.calls.isEmpty());
    }

    @Test
    void publisherRejectsCrossTenantRecipientWithoutSending() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserService adminUserService = adminUsers(Map.of(1001L, enabledUser(1001L, 121L)));
        NotifyReviewRuntimeOutboxPublisher publisher = publisher(
                notifySendService, adminUserService, "1=1001");

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult result = publisher.publish(runtimeAlert(6L));

        assertEquals(false, result.success());
        assertEquals("runtime_outbox_notify_recipient_tenant_mismatch", result.errorCode());
        assertTrue(notifySendService.calls.isEmpty());
    }

    @Test
    void publisherRejectsDeletedOrMissingRecipientWithoutSending() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserDO deletedUser = enabledUser(1001L, 1L);
        deletedUser.setDeleted(true);
        Map<Long, AdminUserDO> users = new HashMap<>();
        users.put(1001L, deletedUser);
        AdminUserService adminUserService = adminUsers(users);
        NotifyReviewRuntimeOutboxPublisher publisher = publisher(
                notifySendService, adminUserService, "1=1001");

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult deletedResult = publisher.publish(runtimeAlert(7L));
        users.remove(1001L);
        ReviewRuntimeOutboxDeliveryResult missingResult = publisher.publish(runtimeAlert(8L));

        assertEquals("runtime_outbox_notify_recipient_deleted", deletedResult.errorCode());
        assertEquals("runtime_outbox_notify_recipient_not_found", missingResult.errorCode());
        assertTrue(notifySendService.calls.isEmpty());
    }

    @Test
    void publisherRejectsMissingTenantContextAndUnmappedTenantWithoutSending() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AdminUserService adminUserService = adminUsers(Map.of(1001L, enabledUser(1001L, 1L)));
        NotifyReviewRuntimeOutboxPublisher publisher = publisher(
                notifySendService, adminUserService, "1=1001");

        ReviewRuntimeOutboxDeliveryResult missingTenantResult = publisher.publish(runtimeAlert(9L));
        TenantContextHolder.setTenantId(121L);
        ReviewRuntimeOutboxDeliveryResult unmappedTenantResult = publisher.publish(runtimeAlert(10L));

        assertEquals("runtime_outbox_notify_tenant_missing", missingTenantResult.errorCode());
        assertEquals("runtime_outbox_notify_recipients_not_configured", unmappedTenantResult.errorCode());
        assertTrue(notifySendService.calls.isEmpty());
    }

    @Test
    void publisherIgnoresDataPermissionOnlyDuringRecipientLookupAndRestoresContext() {
        CapturingNotifySendService notifySendService = new CapturingNotifySendService();
        AtomicBoolean dataPermissionIgnoredDuringLookup = new AtomicBoolean();
        AdminUserService adminUserService = adminUserService(userId -> {
            dataPermissionIgnoredDuringLookup.set(
                    DataPermissionContextHolder.get() != null
                            && !DataPermissionContextHolder.get().enable());
            return enabledUser(userId, 1L);
        });
        NotifyReviewRuntimeOutboxPublisher publisher = publisher(
                notifySendService, adminUserService, "1=1001");

        TenantContextHolder.setTenantId(1L);
        ReviewRuntimeOutboxDeliveryResult result = publisher.publish(runtimeAlert(11L));

        assertTrue(result.success());
        assertTrue(dataPermissionIgnoredDuringLookup.get());
        assertNull(DataPermissionContextHolder.get());
        assertEquals(1L, TenantContextHolder.getTenantId());
        assertEquals(false, TenantContextHolder.isIgnore());
    }

    private static NotifyReviewRuntimeOutboxPublisher publisher(CapturingNotifySendService notifySendService,
                                                                 AdminUserService adminUserService,
                                                                 String tenantAdminUserRoutes) {
        ReviewRuntimeOutboxNotifyProperties properties = new ReviewRuntimeOutboxNotifyProperties();
        properties.setTenantAdminUserRoutes(tenantAdminUserRoutes);
        return new NotifyReviewRuntimeOutboxPublisher(
                notifySendService, adminUserService, properties, ReviewRuntimeOutboxNotifyDeliveryStore.noop());
    }

    private static AdminUserDO enabledUser(Long userId, Long tenantId) {
        AdminUserDO user = new AdminUserDO();
        user.setId(userId);
        user.setTenantId(tenantId);
        user.setStatus(CommonStatusEnum.ENABLE.getStatus());
        user.setDeleted(false);
        return user;
    }

    private static ReviewRuntimeOutboxMessage runtimeAlert(Long id) {
        return new ReviewRuntimeOutboxMessage(
                id,
                "run-" + id,
                "review_runtime_alert",
                "review_data_schema_drift",
                "{\"alert\":\"review_data_schema_drift\"}",
                0,
                LocalDateTime.of(2026, 7, 8, 15, 2)
        );
    }

    private static AdminUserService adminUsers(Map<Long, AdminUserDO> users) {
        return adminUserService(users::get);
    }

    private static AdminUserService adminUserService(Function<Long, AdminUserDO> lookup) {
        return (AdminUserService) Proxy.newProxyInstance(
                AdminUserService.class.getClassLoader(),
                new Class<?>[]{AdminUserService.class},
                (proxy, method, args) -> {
                    if ("getUser".equals(method.getName()) && args != null && args.length == 1) {
                        return lookup.apply((Long) args[0]);
                    }
                    if ("toString".equals(method.getName())) {
                        return "TestAdminUserService";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static class CapturingNotifySendService implements NotifySendService {

        private final List<SendCall> calls = new ArrayList<>();
        private Long failOnceForUserId;

        @Override
        public Long sendSingleNotifyToAdmin(Long userId, String templateCode, Map<String, Object> templateParams) {
            calls.add(new SendCall(userId, templateCode, Map.copyOf(templateParams)));
            if (userId != null && userId.equals(failOnceForUserId)) {
                failOnceForUserId = null;
                throw new IllegalStateException("notify unavailable");
            }
            return 9000L + calls.size();
        }

        @Override
        public Long sendSingleNotifyToMember(Long userId, String templateCode, Map<String, Object> templateParams) {
            throw new UnsupportedOperationException("member notify is not used by alert review outbox");
        }

        @Override
        public Long sendSingleNotify(Long userId, Integer userType, String templateCode, Map<String, Object> templateParams) {
            throw new UnsupportedOperationException("generic notify is not used by alert review outbox");
        }
    }

    private record SendCall(Long userId,
                            String templateCode,
                            Map<String, Object> templateParams) {
    }

    private static class InMemoryNotifyDeliveryStore implements ReviewRuntimeOutboxNotifyDeliveryStore {

        private final Map<DeliveryKey, Long> delivered = new HashMap<>();
        private final Map<DeliveryKey, String> lastError = new HashMap<>();

        @Override
        public boolean isDelivered(Long outboxId, String channel, Long recipientUserId, String templateCode) {
            return delivered.containsKey(new DeliveryKey(outboxId, channel, recipientUserId, templateCode));
        }

        @Override
        public void markDelivered(Long outboxId, String eventType, String alertKey, String channel,
                                  Long recipientUserId, String templateCode, Long notifyMessageId,
                                  LocalDateTime deliveredAt) {
            delivered.put(new DeliveryKey(outboxId, channel, recipientUserId, templateCode), notifyMessageId);
        }

        @Override
        public void markFailed(Long outboxId, String eventType, String alertKey, String channel,
                               Long recipientUserId, String templateCode, String lastError,
                               LocalDateTime attemptedAt) {
            this.lastError.put(new DeliveryKey(outboxId, channel, recipientUserId, templateCode), lastError);
        }
    }

    private record DeliveryKey(Long outboxId,
                               String channel,
                               Long recipientUserId,
                               String templateCode) {
    }
}
