package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.core.job.TenantJob;
import com.basiclab.iot.common.core.job.TenantJobAspect;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewReportAckDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewReportAckMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeOutboxMapper;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewEventReconcileJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewEvidenceExportWorkerJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewLocalScheduler;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewOperationsReportJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimeOutboxJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimePatrolJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimeJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimeJobLockAspect;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewSemanticIndexJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxDeliveryResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxMessage;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxPublisher;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportCommand;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewRuntimeSchedulingTest {

    private static final String SCHEDULER_MIGRATION =
            "/sql/migrations/V20260709__alert_review_scheduler_activation.sql";
    private static final String LOCAL_SCHEDULER_OWNERSHIP_MIGRATION =
            "/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql";
    private static final String APPLICATION_YAML = "/application.yaml";

    @Test
    void schedulerSeedsRequiredRuntimeJobsEnabledByDefault() throws IOException {
        String sql = resourceText(SCHEDULER_MIGRATION);

        assertTrue(sql.contains("supervisionAlertReviewRuntimePatrolJob"));
        assertTrue(sql.contains("supervisionAlertReviewRuntimeOutboxJob"));
        assertTrue(sql.contains("supervisionAlertReviewEventReconcileJob"));
        assertTrue(sql.contains("supervisionAlertReviewEvidenceExportWorkerJob"));
        assertTrue(sql.contains("supervisionAlertReviewSemanticIndexJob"));
        assertTrue(sql.contains("supervisionAlertReviewOperationsReportJob"));
        assertTrue(sql.contains("SET status = 1"));
        assertFalse(sql.contains("SET status = 2"));
    }

    @Test
    void runtimeJobsAreTenantScopedSchedulerHandlers() throws NoSuchMethodException {
        List<Class<?>> jobTypes = List.of(
                SupervisionAlertReviewRuntimePatrolJob.class,
                SupervisionAlertReviewRuntimeOutboxJob.class,
                SupervisionAlertReviewEventReconcileJob.class,
                SupervisionAlertReviewEvidenceExportWorkerJob.class,
                SupervisionAlertReviewSemanticIndexJob.class,
                SupervisionAlertReviewOperationsReportJob.class
        );

        for (Class<?> jobType : jobTypes) {
            Component component = jobType.getAnnotation(Component.class);
            assertNotNull(component, jobType.getSimpleName());
            assertFalse(component.value().isBlank(), jobType.getSimpleName());
            assertTrue(jobType.getMethod("execute", String.class).isAnnotationPresent(TenantJob.class),
                    jobType.getSimpleName());
        }
    }

    @Test
    void localSchedulerInvokesEveryRuntimeHandlerWithProductionParameters() throws Exception {
        List<String> calls = new ArrayList<>();
        JobHandler patrol = recordingHandler(calls, "patrol");
        JobHandler outbox = recordingHandler(calls, "outbox");
        JobHandler reconcile = recordingHandler(calls, "reconcile");
        JobHandler export = recordingHandler(calls, "export");
        JobHandler semantic = recordingHandler(calls, "semantic");
        JobHandler report = recordingHandler(calls, "report");
        AtomicInteger unlockCount = new AtomicInteger();
        RLock lock = interfaceProxy(RLock.class, (proxy, method, args) -> switch (method.getName()) {
            case "tryLock", "isHeldByCurrentThread" -> true;
            case "unlock" -> {
                unlockCount.incrementAndGet();
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        RedissonClient redissonClient = interfaceProxy(RedissonClient.class,
                (proxy, method, args) -> "getLock".equals(method.getName())
                        ? lock : defaultValue(method.getReturnType()));
        SupervisionAlertReviewLocalScheduler scheduler = new SupervisionAlertReviewLocalScheduler(
                patrol, outbox, reconcile, export, semantic, report, redissonClient, Optional.empty()
        );

        scheduler.runRuntimePatrol();
        scheduler.publishRuntimeOutbox();
        scheduler.reconcileEvents();
        scheduler.processEvidenceExports();
        scheduler.processSemanticIndex();
        scheduler.deliverShiftReport();
        scheduler.deliverDailyReport();

        assertEquals(List.of(
                "patrol:",
                "outbox:100",
                "reconcile:",
                "export:20",
                "semantic:50",
                "report:shift",
                "report:daily"
        ), calls);
        List<Method> scheduledMethods = Arrays.stream(
                        SupervisionAlertReviewLocalScheduler.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Scheduled.class))
                .toList();
        assertEquals(7, scheduledMethods.size());
        assertEquals(0L, Arrays.stream(SupervisionAlertReviewLocalScheduler.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Async.class))
                .count());
        assertTrue(scheduledMethods.stream()
                .allMatch(method -> "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}"
                        .equals(method.getAnnotation(Scheduled.class).zone())));
        assertEquals(7, unlockCount.get());
        ConditionalOnProperty conditional = SupervisionAlertReviewLocalScheduler.class.getAnnotation(
                ConditionalOnProperty.class);
        assertNotNull(conditional);
        assertFalse(conditional.matchIfMissing());
        assertEquals("true", conditional.havingValue());
        assertEquals("yfeieye.review.local-scheduler", conditional.prefix());
        assertTrue(List.of(conditional.name()).contains("enabled"));
    }

    @Test
    void localSchedulerUsesDistributedNonReentrantLockAndPausesQuartzDoubleSource() throws Exception {
        List<String> calls = new ArrayList<>();
        RLock lock = interfaceProxy(RLock.class,
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        RedissonClient redissonClient = interfaceProxy(RedissonClient.class,
                (proxy, method, args) -> "getLock".equals(method.getName())
                        ? lock : defaultValue(method.getReturnType()));
        JobHandler handler = recordingHandler(calls, "handler");
        SupervisionAlertReviewLocalScheduler scheduler = new SupervisionAlertReviewLocalScheduler(
                handler, handler, handler, handler, handler, handler, redissonClient, Optional.empty()
        );

        scheduler.runRuntimePatrol();

        assertTrue(calls.isEmpty());

        AtomicInteger pauseCount = new AtomicInteger();
        Scheduler quartz = interfaceProxy(Scheduler.class, (proxy, method, args) -> switch (method.getName()) {
            case "checkExists" -> true;
            case "pauseJob" -> {
                pauseCount.incrementAndGet();
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewLocalScheduler conflicting = new SupervisionAlertReviewLocalScheduler(
                handler, handler, handler, handler, handler, handler, redissonClient, Optional.of(quartz)
        );

        conflicting.afterPropertiesSet();
        assertEquals(6, pauseCount.get());
    }

    @Test
    void operationsReportJobUsesPreviousCompleteShanghaiShiftAndDayWindows() throws Exception {
        AtomicReference<ReviewReportCommand> shiftCommand = new AtomicReference<>();
        AtomicReference<ReviewReportCommand> dailyCommand = new AtomicReference<>();
        SupervisionAlertReviewService service = interfaceProxy(SupervisionAlertReviewService.class,
                (proxy, method, args) -> {
            if (!"scheduleReviewReportDelivery".equals(method.getName())) {
                return defaultValue(method.getReturnType());
            }
            ReviewReportCommand command = (ReviewReportCommand) args[0];
            if ("daily".equals(command.reportType())) {
                dailyCommand.set(command);
            } else {
                shiftCommand.set(command);
            }
            return new SupervisionAlertReviewService.ReviewOperationsReportDelivery(
                    new SupervisionAlertReviewService.ReviewOperationsReport(
                            command.reportType(), List.of(), "title", "summary", List.of(), List.of(),
                            LocalDateTime.now(), null, java.util.Map.of()
                    ),
                    0,
                    LocalDateTime.now()
            );
        });
        SupervisionAlertReviewOperationsReportJob job = new SupervisionAlertReviewOperationsReportJob(service);

        job.execute("shift");
        job.execute("daily");

        assertCompleteWindow(shiftCommand.get(), Duration.ofHours(8));
        assertEquals(0, shiftCommand.get().periodEnd().getHour() % 8);
        assertCompleteWindow(dailyCommand.get(), Duration.ofHours(24));
        assertEquals(0, dailyCommand.get().periodEnd().getHour());
    }

    @Test
    void localSchedulerIsExplicitlyEnabledByProductionEnvironmentContract() throws IOException {
        String yaml = resourceText(APPLICATION_YAML);

        assertTrue(yaml.contains("local-scheduler:"));
        assertTrue(yaml.contains("enabled: ${YFEIEYE_REVIEW_LOCAL_SCHEDULER_ENABLED:false}"));
        assertTrue(yaml.contains(
                "spring.task.scheduling.pool.size: ${YFEIEYE_REVIEW_LOCAL_SCHEDULER_POOL_SIZE:4}"));
    }

    @Test
    void unconfiguredOutboxPublisherFailsClosedWithoutDiscardingPayload() {
        String payload = "{\"alert\":\"record_storage_drift:file_missing\"}";
        ReviewRuntimeOutboxMessage message = new ReviewRuntimeOutboxMessage(
                91L,
                "run-91",
                "review_runtime_alert",
                "record_storage_drift:file_missing",
                payload,
                2,
                LocalDateTime.of(2026, 7, 10, 9, 0)
        );

        ReviewRuntimeOutboxDeliveryResult result = ReviewRuntimeOutboxPublisher.noop().publish(message);

        assertFalse(result.success());
        assertEquals("runtime_outbox_sink_not_configured", result.errorCode());
        assertEquals(payload, message.payload());
    }

    @Test
    void outboxClaimAtomicallyReclaimsFailedRowsOnlyAfterBackoff() throws NoSuchMethodException {
        Method claimMethod = SupervisionAlertReviewRuntimeOutboxMapper.class.getMethod(
                "claimPending",
                Long.class,
                Integer.class,
                String.class,
                Long.class,
                LocalDateTime.class,
                LocalDateTime.class
        );
        String sql = String.join("\n", claimMethod.getAnnotation(Update.class).value()).toLowerCase();

        assertTrue(sql.contains("outbox_status = 'failed'"));
        assertTrue(sql.contains("retry_count"));
        assertTrue(sql.contains("published_at"));
        assertTrue(sql.contains("interval '1 second'"));
        assertTrue(sql.contains("for update skip locked"));
        assertTrue(sql.contains("target.tenant_id = #{tenantid"));
        assertTrue(sql.contains("candidate.tenant_id = #{tenantid"));
        assertFalse(sql.contains("payload ="));
        InterceptorIgnore interceptorIgnore = claimMethod.getAnnotation(InterceptorIgnore.class);
        assertNotNull(interceptorIgnore);
        assertEquals("true", interceptorIgnore.tenantLine());
    }

    @Test
    void localSchedulerOwnershipMigrationPausesQuartzAndMakesReportEnqueueAtomic() throws Exception {
        String migration = resourceText(LOCAL_SCHEDULER_OWNERSHIP_MIGRATION).toLowerCase();
        assertTrue(migration.contains("set status = 2"));
        assertTrue(migration.contains("review_operations_report"));
        assertTrue(migration.contains("create unique index"));
        assertTrue(migration.contains("tenant_id, event_type, alert_key"));

        Method insertMethod = SupervisionAlertReviewRuntimeOutboxMapper.class.getMethod(
                "insertOperationsReportIfAbsent", Long.class, SupervisionAlertReviewRuntimeOutboxDO.class);
        String insertSql = String.join("\n", insertMethod.getAnnotation(Insert.class).value()).toLowerCase();
        assertTrue(insertSql.contains("on conflict"));
        assertTrue(insertSql.contains("do nothing"));
        InterceptorIgnore interceptorIgnore = insertMethod.getAnnotation(InterceptorIgnore.class);
        assertNotNull(interceptorIgnore);
        assertEquals("true", interceptorIgnore.tenantLine());
    }

    @Test
    void reportAcknowledgementInsertIsTenantScopedAndAtomic() throws Exception {
        Method insertMethod = SupervisionAlertReviewReportAckMapper.class.getMethod(
                "insertIfAbsent", Long.class, SupervisionAlertReviewReportAckDO.class);
        String sql = String.join("\n", insertMethod.getAnnotation(Insert.class).value()).toLowerCase();

        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("on conflict (tenant_id, report_key) where deleted = 0"));
        assertTrue(sql.contains("do nothing"));
        assertFalse(sql.contains("${"));
        InterceptorIgnore interceptorIgnore = insertMethod.getAnnotation(InterceptorIgnore.class);
        assertNotNull(interceptorIgnore);
        assertEquals("true", interceptorIgnore.tenantLine());
    }

    @Test
    void everyRuntimeExecutionPathUsesTheSameHighestPrecedenceDistributedLock() throws Exception {
        Order order = SupervisionAlertReviewRuntimeJobLockAspect.class.getAnnotation(Order.class);
        assertNotNull(order);
        assertEquals(Ordered.HIGHEST_PRECEDENCE, order.value());

        List<Class<?>> jobTypes = List.of(
                SupervisionAlertReviewRuntimePatrolJob.class,
                SupervisionAlertReviewRuntimeOutboxJob.class,
                SupervisionAlertReviewEventReconcileJob.class,
                SupervisionAlertReviewEvidenceExportWorkerJob.class,
                SupervisionAlertReviewSemanticIndexJob.class,
                SupervisionAlertReviewOperationsReportJob.class);
        List<String> handlerNames = List.of(
                "supervisionAlertReviewRuntimePatrolJob",
                "supervisionAlertReviewRuntimeOutboxJob",
                "supervisionAlertReviewEventReconcileJob",
                "supervisionAlertReviewEvidenceExportWorkerJob",
                "supervisionAlertReviewSemanticIndexJob",
                "supervisionAlertReviewOperationsReportJob");
        for (int index = 0; index < jobTypes.size(); index++) {
            Method execute = jobTypes.get(index).getMethod("execute", String.class);
            SupervisionAlertReviewRuntimeJob runtimeJob = execute.getAnnotation(
                    SupervisionAlertReviewRuntimeJob.class);
            assertNotNull(runtimeJob, jobTypes.get(index).getSimpleName());
            assertEquals(handlerNames.get(index), runtimeJob.value());
        }
    }

    @Test
    void runtimeJobLockSkipsCompetingSourceAndUnlocksTheWinner() {
        AtomicBoolean lockAvailable = new AtomicBoolean(false);
        AtomicInteger unlockCount = new AtomicInteger();
        RLock lock = interfaceProxy(RLock.class, (proxy, method, args) -> switch (method.getName()) {
            case "tryLock" -> lockAvailable.get();
            case "isHeldByCurrentThread" -> true;
            case "unlock" -> {
                unlockCount.incrementAndGet();
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        RedissonClient redissonClient = interfaceProxy(RedissonClient.class,
                (proxy, method, args) -> "getLock".equals(method.getName())
                        ? lock : defaultValue(method.getReturnType()));
        RuntimeJobProbe target = new RuntimeJobProbe();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new SupervisionAlertReviewRuntimeJobLockAspect(redissonClient));
        RuntimeJobProbe proxy = proxyFactory.getProxy();

        assertEquals("skipped=distributed_lock_held,handler=runtimeJobProbe", proxy.execute("first"));
        assertEquals(0, target.calls.get());

        lockAvailable.set(true);
        assertEquals("executed:second", proxy.execute("second"));
        assertEquals(1, target.calls.get());
        assertEquals(1, unlockCount.get());
    }

    @Test
    void runtimeLockWrapsTheWholeMultiTenantExecutionOnce() {
        AtomicInteger lockAcquireCount = new AtomicInteger();
        AtomicInteger unlockCount = new AtomicInteger();
        RLock lock = interfaceProxy(RLock.class, (proxy, method, args) -> switch (method.getName()) {
            case "tryLock" -> {
                lockAcquireCount.incrementAndGet();
                yield true;
            }
            case "isHeldByCurrentThread" -> true;
            case "unlock" -> {
                unlockCount.incrementAndGet();
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        RedissonClient redissonClient = interfaceProxy(RedissonClient.class,
                (proxy, method, args) -> "getLock".equals(method.getName())
                        ? lock : defaultValue(method.getReturnType()));
        TenantFrameworkService tenantFrameworkService = new TenantFrameworkService() {
            @Override
            public List<Long> getTenantIds() {
                return List.of(101L, 202L);
            }

            @Override
            public void validTenant(Long id) {
            }
        };
        MultiTenantRuntimeJobProbe target = new MultiTenantRuntimeJobProbe();
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(new SupervisionAlertReviewRuntimeJobLockAspect(redissonClient));
        proxyFactory.addAspect(new TenantJobAspect(tenantFrameworkService));
        MultiTenantRuntimeJobProbe proxy = proxyFactory.getProxy();

        proxy.execute("");

        assertEquals(1, lockAcquireCount.get());
        assertEquals(1, unlockCount.get());
        assertEquals(List.of(101L, 202L), target.tenantIds.stream().sorted().toList());
        assertEquals(null, TenantContextHolder.getTenantId());
    }

    private static String resourceText(String path) throws IOException {
        try (InputStream input = SupervisionAlertReviewRuntimeSchedulingTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JobHandler recordingHandler(List<String> calls, String name) {
        return param -> {
            calls.add(name + ":" + (param == null ? "" : param));
            return "ok";
        };
    }

    private static <T> T interfaceProxy(Class<T> type, InvocationHandler delegate) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> null;
                        };
                    }
                    return delegate.invoke(proxy, method, args);
                }));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException(type.getName());
    }

    private static void assertCompleteWindow(ReviewReportCommand command, Duration expectedDuration) {
        assertNotNull(command);
        assertNotNull(command.periodStart());
        assertNotNull(command.periodEnd());
        assertEquals(expectedDuration, Duration.between(command.periodStart(), command.periodEnd()));
        assertEquals(0, command.periodEnd().getMinute());
        assertEquals(0, command.periodEnd().getSecond());
        assertEquals(0, command.periodEnd().getNano());
        assertTrue(command.periodEnd().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    static class RuntimeJobProbe {
        private final AtomicInteger calls = new AtomicInteger();

        @SupervisionAlertReviewRuntimeJob("runtimeJobProbe")
        public String execute(String param) {
            calls.incrementAndGet();
            return "executed:" + param;
        }
    }

    static class MultiTenantRuntimeJobProbe {
        private final Queue<Long> tenantIds = new ConcurrentLinkedQueue<>();

        @SupervisionAlertReviewRuntimeJob("multiTenantRuntimeJobProbe")
        @TenantJob
        public String execute(String param) {
            tenantIds.add(TenantContextHolder.getRequiredTenantId());
            return "executed:" + param;
        }
    }
}
