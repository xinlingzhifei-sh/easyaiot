package com.basiclab.iot.system.job.supervision;

import com.basiclab.iot.common.core.handler.JobHandler;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "yfeieye.review.local-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@Slf4j
public class SupervisionAlertReviewLocalScheduler implements InitializingBean {

    private static final String LOCK_PREFIX = "yfeieye:review:local-scheduler:";
    private static final List<String> OWNED_HANDLER_NAMES = List.of(
            "supervisionAlertReviewRuntimePatrolJob",
            "supervisionAlertReviewRuntimeOutboxJob",
            "supervisionAlertReviewEventReconcileJob",
            "supervisionAlertReviewEvidenceExportWorkerJob",
            "supervisionAlertReviewSemanticIndexJob",
            "supervisionAlertReviewOperationsReportJob"
    );

    private final JobHandler runtimePatrolJob;
    private final JobHandler runtimeOutboxJob;
    private final JobHandler eventReconcileJob;
    private final JobHandler evidenceExportWorkerJob;
    private final JobHandler semanticIndexJob;
    private final JobHandler operationsReportJob;
    private final RedissonClient redissonClient;
    private final Optional<Scheduler> quartzScheduler;

    public SupervisionAlertReviewLocalScheduler(
            @Qualifier("supervisionAlertReviewRuntimePatrolJob") JobHandler runtimePatrolJob,
            @Qualifier("supervisionAlertReviewRuntimeOutboxJob") JobHandler runtimeOutboxJob,
            @Qualifier("supervisionAlertReviewEventReconcileJob") JobHandler eventReconcileJob,
            @Qualifier("supervisionAlertReviewEvidenceExportWorkerJob") JobHandler evidenceExportWorkerJob,
            @Qualifier("supervisionAlertReviewSemanticIndexJob") JobHandler semanticIndexJob,
            @Qualifier("supervisionAlertReviewOperationsReportJob") JobHandler operationsReportJob,
            RedissonClient redissonClient,
            Optional<Scheduler> quartzScheduler) {
        this.runtimePatrolJob = runtimePatrolJob;
        this.runtimeOutboxJob = runtimeOutboxJob;
        this.eventReconcileJob = eventReconcileJob;
        this.evidenceExportWorkerJob = evidenceExportWorkerJob;
        this.semanticIndexJob = semanticIndexJob;
        this.operationsReportJob = operationsReportJob;
        this.redissonClient = redissonClient;
        this.quartzScheduler = quartzScheduler;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (quartzScheduler.isEmpty()) {
            return;
        }
        for (String handlerName : OWNED_HANDLER_NAMES) {
            JobKey jobKey = new JobKey(handlerName);
            if (quartzScheduler.get().checkExists(jobKey)) {
                quartzScheduler.get().pauseJob(jobKey);
                log.info("[afterPropertiesSet][paused Quartz handler({}) for local scheduler ownership]",
                        handlerName);
            }
        }
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.runtime-patrol-cron:0 */5 * * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void runRuntimePatrol() {
        runOnce("supervisionAlertReviewRuntimePatrolJob", runtimePatrolJob, "");
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.runtime-outbox-cron:0 * * * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void publishRuntimeOutbox() {
        runOnce("supervisionAlertReviewRuntimeOutboxJob", runtimeOutboxJob, "100");
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.event-reconcile-cron:0 */5 * * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void reconcileEvents() {
        runOnce("supervisionAlertReviewEventReconcileJob", eventReconcileJob, "");
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.evidence-export-cron:0 */2 * * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void processEvidenceExports() {
        runOnce("supervisionAlertReviewEvidenceExportWorkerJob", evidenceExportWorkerJob, "20");
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.semantic-index-cron:0 */10 * * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void processSemanticIndex() {
        runOnce("supervisionAlertReviewSemanticIndexJob", semanticIndexJob, "50");
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.shift-report-cron:0 0 */8 * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void deliverShiftReport() {
        runOnce("supervisionAlertReviewOperationsReportJob:shift", operationsReportJob, "shift");
    }

    @Scheduled(
            cron = "${yfeieye.review.local-scheduler.daily-report-cron:0 10 0 * * *}",
            zone = "${yfeieye.review.local-scheduler.zone:Asia/Shanghai}")
    public void deliverDailyReport() {
        runOnce("supervisionAlertReviewOperationsReportJob:daily", operationsReportJob, "daily");
    }

    private void runOnce(String handlerName, JobHandler handler, String param) {
        RLock lock = null;
        boolean acquired = false;
        long startedAt = System.nanoTime();
        try {
            lock = redissonClient.getLock(LOCK_PREFIX + handlerName);
            acquired = lock.tryLock();
            if (!acquired) {
                log.info("[runOnce][handler({}) skipped because another scheduler owns the lock]", handlerName);
                return;
            }
            String result = handler.execute(param);
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            log.info("[runOnce][handler({}) durationMs({}) result({})]", handlerName, durationMillis, result);
        } catch (Exception ex) {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000L;
            log.error("[runOnce][handler({}) durationMs({}) failed]", handlerName, durationMillis, ex);
            throw new IllegalStateException("local review scheduler handler failed: " + handlerName, ex);
        } finally {
            if (acquired && lock != null) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception unlockError) {
                    log.error("[runOnce][handler({}) lock release failed]", handlerName, unlockError);
                }
            }
        }
    }
}
