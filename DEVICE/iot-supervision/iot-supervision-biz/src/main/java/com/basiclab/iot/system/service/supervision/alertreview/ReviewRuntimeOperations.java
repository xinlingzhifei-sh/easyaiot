package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.AlertReviewDataSchemaValidator;
import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import com.basiclab.iot.system.service.supervision.alertreview.ReviewRecordCoverage.RecordGapReasonDefinition;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.REVIEW_DATA_SCHEMA_VALIDATOR;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.REVIEW_DATA_VERSION;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.buildRecoveryReviewSegmentId;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.buildReviewObjects;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.reviewSegmentSeverity;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstSourceAlertId;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.normalizeCameraScope;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.objectIds;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.roundRate;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toDouble;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toDoubleList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toInteger;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLocalDateTime;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.zones;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewEvidenceExport.sha256File;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRecordCoverage.RECORD_GAP_REASON_CATALOG;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRecordCoverage.normalizeCoverageStatus;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRecordCoverage.normalizeRecordGapReason;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRecordCoverage.recordGapReason;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRecordCoverage.recordGapReasonDefinition;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.hasCompleteVideoExportProvenance;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.sha256HashesEqual;

/**
 * 运行时运维：健康报告、巡检与分布式锁、外发队列投递、运行时对账、集成冒烟。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewRuntimeOperations {

    private final AlertReviewDependencies deps;
    private final AlertClueIngestion alertClueIngestion;
    private final AlertReviewEventBridge alertReviewEventBridge;
    private final ReviewCaseManagement reviewCaseManagement;
    private final ReviewEvidenceExport reviewEvidenceExport;
    private final ReviewEvidenceManifests reviewEvidenceManifests;
    private final ReviewItemQuery reviewItemQuery;
    private final ReviewRecordCoverage reviewRecordCoverage;
    private final ReviewRuleGovernance reviewRuleGovernance;
    private final ReviewSemanticIntelligence reviewSemanticIntelligence;

    ReviewRuntimeOperations(AlertReviewDependencies deps, AlertClueIngestion alertClueIngestion, AlertReviewEventBridge alertReviewEventBridge, ReviewCaseManagement reviewCaseManagement, ReviewEvidenceExport reviewEvidenceExport, ReviewEvidenceManifests reviewEvidenceManifests, ReviewItemQuery reviewItemQuery, ReviewRecordCoverage reviewRecordCoverage, ReviewRuleGovernance reviewRuleGovernance, ReviewSemanticIntelligence reviewSemanticIntelligence) {
        this.deps = deps;
        this.alertClueIngestion = alertClueIngestion;
        this.alertReviewEventBridge = alertReviewEventBridge;
        this.reviewCaseManagement = reviewCaseManagement;
        this.reviewEvidenceExport = reviewEvidenceExport;
        this.reviewEvidenceManifests = reviewEvidenceManifests;
        this.reviewItemQuery = reviewItemQuery;
        this.reviewRecordCoverage = reviewRecordCoverage;
        this.reviewRuleGovernance = reviewRuleGovernance;
        this.reviewSemanticIntelligence = reviewSemanticIntelligence;
    }

    private static final int DEFAULT_RUNTIME_OUTBOX_LIMIT = 50;
    private static final int MAX_RUNTIME_OUTBOX_LIMIT = 200;
    private static final int RUNTIME_OUTBOX_CLAIM_TIMEOUT_MINUTES = 10;

    private record ReviewDataConsistency(Map<String, Object> reviewData,
                                         boolean schemaDrift,
                                         boolean segmentDoubleWriteDrift) {
        boolean hasDrift() {
            return schemaDrift || segmentDoubleWriteDrift;
        }
    }

    public ReviewRuntimeHealthReport getReviewRuntimeHealth(ReviewRuntimeHealthCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewQuery query = command.query();
        List<ReviewItemAggregate> items = reviewItemQuery.listWorkbench(query);
        int missingRecordCount = (int) items.stream()
                .filter(reviewItemQuery::hasRecordEvidenceGap)
                .count();
        ReviewSemanticIndexEvaluation semantic = reviewSemanticIntelligence.evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(query, command.operatorUserId()));
        int semanticBacklogCount = semantic.staleReviewItemIds().size();
        List<ReviewEvidenceExportJob> exportJobs = deps.reviewItemStore.listAllExportJobs();
        int failedExportJobCount = (int) exportJobs.stream()
                .filter(job -> EXPORT_JOB_FAILED.equals(job.status()))
                .count();
        List<ReviewDataConsistency> reviewDataConsistencies = items.stream()
                .map(this::reviewDataConsistency)
                .toList();
        int reviewDataSchemaDriftCount = (int) reviewDataConsistencies.stream()
                .filter(ReviewDataConsistency::schemaDrift)
                .count();
        int reviewSegmentDoubleWriteDriftCount = (int) reviewDataConsistencies.stream()
                .filter(ReviewDataConsistency::segmentDoubleWriteDrift)
                .count();
        List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> storageDriftReports =
                inspectRecordStorageDrift(items);
        Map<String, Integer> recordGapReasons = mergeRecordStorageDriftReasons(
                recordGapReasons(items), storageDriftReports);
        int storageDriftIssueCount = storageDriftReports.stream()
                .mapToInt(ReviewRecordStorageDriftResolver.RecordStorageDriftReport::issueCount)
                .sum();
        List<String> alerts = new ArrayList<>();
        if (missingRecordCount > 0 || !recordGapReasons.isEmpty()) {
            alerts.add("record_evidence_gap");
            for (String reason : recordGapReasons.keySet()) {
                alerts.add("record_evidence_gap:" + reason);
            }
        }
        List<String> storageDriftReasons = recordGapReasons.keySet().stream()
                .filter(ReviewRuntimeOperations::isRecordStorageDriftReason)
                .toList();
        if (!storageDriftReasons.isEmpty()) {
            alerts.add("record_storage_drift");
            for (String reason : storageDriftReasons) {
                alerts.add("record_storage_drift:" + reason);
            }
        }
        if (semanticBacklogCount > 0) {
            alerts.add("semantic_index_backlog");
        }
        if (failedExportJobCount > 0) {
            alerts.add("evidence_export_failed");
        }
        if (reviewDataSchemaDriftCount > 0) {
            alerts.add("review_data_schema_drift");
        }
        if (reviewSegmentDoubleWriteDriftCount > 0) {
            alerts.add("review_segment_double_write_drift");
        }
        return new ReviewRuntimeHealthReport(
                items.size(),
                missingRecordCount,
                semantic.staleReviewItemIds().size(),
                failedExportJobCount,
                roundRate(missingRecordCount, items.size()),
                roundRate(failedExportJobCount, exportJobs.size()),
                semanticBacklogCount,
                missingRecordCount + semanticBacklogCount + failedExportJobCount
                        + reviewDataSchemaDriftCount + reviewSegmentDoubleWriteDriftCount
                        + storageDriftIssueCount,
                Map.copyOf(recordGapReasons),
                recordGapReasonCatalog(),
                List.copyOf(alerts),
                LocalDateTime.now(),
                command.operatorUserId()
        );
    }

    public ReviewRuntimePatrolResult runRuntimePatrol(ReviewRuntimePatrolCommand command) {
        Objects.requireNonNull(command, "command");
        String lockName = "alert-review-runtime-patrol";
        ReviewRuntimeLockAcquisition lock = deps.reviewItemStore.acquireRuntimePatrolLock(
                lockName,
                LocalDateTime.now().plusMinutes(10),
                command.operatorUserId());
        if (!Boolean.TRUE.equals(lock.acquired())) {
            return new ReviewRuntimePatrolResult(
                    "locked",
                    false,
                    normalizePatrolAttempts(command.maxAttempts()),
                    0,
                    getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(command.query(), command.operatorUserId())),
                    null,
                    List.of("runtime_patrol_locked"),
                    List.of(),
                    List.of("wait_for_current_runtime_patrol"),
                    LocalDateTime.now(),
                    command.operatorUserId(),
                    immutableNonNullMap(runtimeLockMetadata(command, lockName, lock))
            );
        }
        try {
            int maxAttempts = normalizePatrolAttempts(command.maxAttempts());
            ReviewRuntimeHealthReport before = getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                    command.query(), command.operatorUserId()));
            List<String> alerts = List.copyOf(before.alerts());
            List<String> notifications = alerts.stream()
                    .map(alert -> "review_runtime_alert:" + alert)
                    .toList();
            ReviewReconciliationResult reconciliation = null;
            int attemptCount = 0;
            List<String> attemptFindings = new ArrayList<>();
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                attemptCount = attempt;
                try {
                    reconciliation = reconcileReviewRuntime(new ReviewReconciliationCommand(
                            command.query(),
                            command.operatorUserId(),
                            command.repair()
                    ));
                    break;
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    attemptFindings.add("attempt_failed:" + attempt + ":" + ex.getClass().getSimpleName());
                }
            }
            ReviewRuntimeHealthReport after = reconciliation == null
                    ? before
                    : reconciliation.healthReport();
            List<String> recommendedActions = runtimePatrolRecommendedActions(alerts, after);
            if (reconciliation == null && lastFailure != null) {
                recommendedActions = new ArrayList<>(recommendedActions);
                recommendedActions.add("inspect_runtime_patrol_failure");
            }
            LocalDateTime executedAt = LocalDateTime.now();
            Map<String, Object> metadata = runtimeLockMetadata(command, lockName, lock);
            metadata.put("repairRequested", !Boolean.FALSE.equals(command.repair()));
            metadata.put("attemptFindings", attemptFindings);
            metadata.put("initialRepairableCount", before.repairableCount());
            metadata.put("finalRepairableCount", after.repairableCount());
            metadata.put("recordGapReasons", after.recordGapReasons());
            metadata.put("recordGapReasonDetails", recordGapReasonDetails(after.recordGapReasons()));
            metadata.put("alertActions", runtimePatrolAlertActions(alerts));
            metadata.put("nextRetryAt", after.repairableCount() > 0
                    ? executedAt.plusMinutes(Math.max(1, attemptCount)).toString()
                    : null);
            String status = reconciliation == null ? "failed" : (alerts.isEmpty() ? "healthy" : "alerted");
            String runId = deps.reviewItemStore.recordRuntimePatrolRun(
                    status,
                    attemptCount,
                    alerts,
                    recommendedActions,
                    command.operatorUserId(),
                    executedAt,
                    immutableNonNullMap(metadata)
            );
            int outboxEventCount = deps.reviewItemStore.enqueueRuntimePatrolAlerts(
                    runId,
                    alerts,
                    recommendedActions,
                    command.operatorUserId(),
                    executedAt,
                    immutableNonNullMap(metadata)
            );
            metadata.put("historyRunId", runId);
            metadata.put("outboxEventCount", outboxEventCount);
            return new ReviewRuntimePatrolResult(
                    status,
                    true,
                    maxAttempts,
                    attemptCount,
                    after,
                    reconciliation,
                    alerts,
                    notifications,
                    List.copyOf(new LinkedHashSet<>(recommendedActions)),
                    executedAt,
                    command.operatorUserId(),
                    immutableNonNullMap(metadata)
            );
        } finally {
            deps.reviewItemStore.releaseRuntimePatrolLock(lockName, command.operatorUserId());
        }
    }

    private static Map<String, Object> runtimeLockMetadata(ReviewRuntimePatrolCommand command,
                                                           String lockName,
                                                           ReviewRuntimeLockAcquisition lock) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scheduled", Boolean.TRUE.equals(command.scheduled()));
        metadata.put("lockName", lockName);
        metadata.put("lockBackend", "review_item_store");
        metadata.put("lockReason", lock.reason());
        metadata.put("lockRecovered", Boolean.TRUE.equals(lock.recoveredStaleLock()));
        metadata.put("lockedUntil", lock.lockedUntil());
        metadata.put("lockAcquiredAt", lock.acquiredAt());
        if (Boolean.TRUE.equals(lock.acquired())) {
            metadata.put("previousLockOwnerUserId", lock.previousOwnerUserId());
            metadata.put("previousLockedUntil", lock.previousLockedUntil());
        } else {
            metadata.put("lockOwnerUserId", lock.previousOwnerUserId());
        }
        return metadata;
    }

    public ReviewRuntimeOutboxPublishResult publishRuntimeOutbox(ReviewRuntimeOutboxPublishCommand command) {
        Objects.requireNonNull(command, "command");
        int limit = normalizeRuntimeOutboxLimit(command.limit());
        LocalDateTime publishedAt = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        List<String> publishedAlerts = new ArrayList<>();
        List<String> failedAlerts = new ArrayList<>();
        List<ReviewRuntimeOutboxMessage> messages = deps.reviewItemStore.claimPendingRuntimeOutbox(
                limit,
                claimToken,
                command.operatorUserId(),
                publishedAt,
                publishedAt.minusMinutes(RUNTIME_OUTBOX_CLAIM_TIMEOUT_MINUTES)
        );
        for (ReviewRuntimeOutboxMessage message : messages) {
            if (message == null || message.id() == null) {
                continue;
            }
            if (!hasText(message.eventType()) || !hasText(message.alertKey()) || !hasText(message.payload())) {
                deps.reviewItemStore.markRuntimeOutboxFailed(
                        message.id(),
                        "invalid_runtime_outbox_payload",
                        publishedAt
                );
                failedAlerts.add(String.valueOf(message.alertKey()));
                continue;
            }
            ReviewRuntimeOutboxDeliveryResult deliveryResult;
            try {
                deliveryResult = deps.runtimeOutboxPublisher.publish(message);
            } catch (RuntimeException ex) {
                deps.reviewItemStore.markRuntimeOutboxFailed(
                        message.id(),
                        "runtime_outbox_publish_exception:" + ex.getClass().getSimpleName(),
                        publishedAt
                );
                failedAlerts.add(message.alertKey());
                continue;
            }
            if (deliveryResult == null || !Boolean.TRUE.equals(deliveryResult.success())) {
                deps.reviewItemStore.markRuntimeOutboxFailed(
                        message.id(),
                        runtimeOutboxDeliveryFailureReason(deliveryResult),
                        publishedAt
                );
                failedAlerts.add(message.alertKey());
                continue;
            }
            deps.reviewItemStore.markRuntimeOutboxPublished(message.id(), publishedAt);
            publishedAlerts.add(message.alertKey());
        }
        return new ReviewRuntimeOutboxPublishResult(
                messages.size(),
                publishedAlerts.size(),
                failedAlerts.size(),
                List.copyOf(publishedAlerts),
                List.copyOf(failedAlerts),
                publishedAt,
                command.operatorUserId()
        );
    }

    private static String runtimeOutboxDeliveryFailureReason(ReviewRuntimeOutboxDeliveryResult deliveryResult) {
        if (deliveryResult == null) {
            return "runtime_outbox_publish_failed";
        }
        return firstText(deliveryResult.errorCode(), "runtime_outbox_publish_failed");
    }

    public ReviewReconciliationResult reconcileReviewRuntime(ReviewReconciliationCommand command) {
        Objects.requireNonNull(command, "command");
        boolean repair = !Boolean.FALSE.equals(command.repair());
        ReviewQuery query = command.query();
        List<ReviewItemAggregate> items = reviewItemQuery.listWorkbench(query);
        List<String> findings = new ArrayList<>();
        int repairedRecordCount = 0;
        if (repair) {
            for (ReviewItemAggregate item : items) {
                if (!reviewItemQuery.hasRecordEvidenceGap(item)) {
                    continue;
                }
                List<RecordCoverageSegment> coverage = reviewRecordCoverage.getRecordCoverage(item.id());
                ReviewRecordStorageSyncResult syncResult = reviewRecordCoverage.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                        item.id(),
                        command.operatorUserId(),
                        coverage
                ));
                ReviewItemAggregate repairedItem = deps.reviewItemStore.findById(item.id()).orElse(item);
                if (RECORD_EVIDENCE_FOUND.equals(repairedItem.recordEvidenceStatus())) {
                    repairedRecordCount++;
                    findings.add("record_repaired:" + item.id());
                } else {
                    findings.add("record_unresolved:" + item.id() + ":" + syncResult.syncStatus());
                }
            }
        }

        items = reviewItemQuery.listWorkbench(query);
        if (repair) {
            for (ReviewItemAggregate item : items) {
                ReviewDataConsistency consistency = reviewDataConsistency(item);
                if (!consistency.hasDrift()) {
                    continue;
                }
                deps.reviewItemStore.updateReviewLifecycle(
                        item.id(),
                        consistency.reviewData(),
                        item.firstAlertTime(),
                        item.lastAlertTime(),
                        List.of(),
                        item.recordEvidenceStatus(),
                        item.recordEvidenceCheckedAt(),
                        item.recordEvidenceMessage()
                );
                if (consistency.schemaDrift()) {
                    findings.add("review_data_repaired:" + item.id());
                }
                if (consistency.segmentDoubleWriteDrift()) {
                    findings.add("review_segment_repaired:" + item.id());
                }
            }
        }

        ReviewSemanticIndexEvaluation semanticBefore = reviewSemanticIntelligence.evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(query, command.operatorUserId()));
        int repairedSemanticIndexCount = 0;
        if (repair && !semanticBefore.staleReviewItemIds().isEmpty()) {
            Set<Long> staleIds = new LinkedHashSet<>(semanticBefore.staleReviewItemIds());
            for (ReviewSemanticIndexEntry entry : reviewSemanticIntelligence.reindexSemanticIndex(query)) {
                if (staleIds.contains(entry.reviewItemId())
                        && SEMANTIC_INDEX_INDEXED.equals(entry.indexStatus())) {
                    repairedSemanticIndexCount++;
                    findings.add("semantic_reindexed:" + entry.reviewItemId());
                }
            }
        }
        int failedExportJobCount = (int) deps.reviewItemStore.listAllExportJobs().stream()
                .filter(job -> EXPORT_JOB_FAILED.equals(job.status()))
                .count();
        ReviewRuntimeHealthReport healthReport = getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                query,
                command.operatorUserId()
        ));
        return new ReviewReconciliationResult(
                items.size(),
                repairedRecordCount,
                repairedSemanticIndexCount,
                failedExportJobCount,
                List.copyOf(findings),
                healthReport,
                LocalDateTime.now(),
                command.operatorUserId()
        );
    }

    public ReviewIntegrationSmokeResult runIntegrationSmoke(ReviewIntegrationSmokeCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDateTime alertTime = command.alertTime() == null ? LocalDateTime.now() : command.alertTime();
        boolean includeVideoExport = Boolean.TRUE.equals(command.includeVideoExport());
        String profile = firstText(command.profile(), "service-synthetic");
        boolean realProfile = "device-video-web".equals(profile) || "release".equals(profile);
        if (realProfile) {
            requirePositive(command.operatorUserId(), "operatorUserId");
            requireText(command.deviceId(), "deviceId");
            requireText(command.cameraId(), "cameraId");
            requireText(command.zoneCode(), "zoneCode");
            if (!command.deviceId().equals(command.cameraId())) {
                throw new IllegalArgumentException(
                        "real integration smoke requires deviceId and cameraId to identify the same VIDEO camera");
            }
            if (!includeVideoExport) {
                throw new IllegalArgumentException("real integration smoke requires video export");
            }
            List<String> requestedCameraIds = normalizeCameraScope(command.allowedCameraIds());
            if (requestedCameraIds == null || !requestedCameraIds.contains(command.cameraId())) {
                throw new IllegalArgumentException("real integration smoke allowedCameraIds must contain cameraId");
            }
        }
        List<String> checkpoints = new ArrayList<>();
        if (realProfile) {
            checkpoints.add("device_api_reachable");
            checkpoints.add("web_contract_checked");
        }
        String sourceAlertId = firstText(command.sourceAlertId(), "review-smoke-" + UUID.randomUUID());
        String deviceId = realProfile ? command.deviceId() : "device-smoke";
        String cameraId = realProfile ? command.cameraId() : "camera-smoke";
        String zoneCode = realProfile ? command.zoneCode() : "zone-smoke";
        ReviewItemAggregate item = alertClueIngestion.ingestClue(new AlertClueCommand(
                "video",
                sourceAlertId,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                deviceId,
                cameraId,
                zoneCode,
                "person",
                15,
                realProfile ? null : "smoke-snapshot.jpg",
                realProfile ? null : "smoke-record.mp4",
                "smoke:" + sourceAlertId,
                List.of("person"),
                List.of(zoneCode),
                List.of("obj-smoke"),
                0.9D,
                List.of(0D, 0D, 10D, 20D),
                sourceAlertId
        ));
        checkpoints.add("ingest_review_item");
        if (realProfile) {
            boolean resolvedRecord = RECORD_EVIDENCE_FOUND.equals(item.recordEvidenceStatus())
                    && deps.reviewItemStore.listTimeline(item.id()).stream()
                    .anyMatch(evidence -> MATERIAL_RECORD.equals(evidence.materialType())
                            && hasText(evidence.materialUri()));
            if (!resolvedRecord) {
                throw new IllegalStateException("real VIDEO alert record query did not resolve recording: "
                        + firstText(item.recordEvidenceMessage(), "unknown"));
            }
            checkpoints.add("video_record_query_checked");
            checkpoints.add("sample_alert_ingested");
        }
        ReviewRuleView smokeRule = reviewRuleGovernance.saveRule(new ReviewRuleCommand(
                null,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "integration smoke zone rule",
                "video",
                cameraId,
                zoneCode,
                "person",
                15,
                alertTime.minusMinutes(5),
                null,
                true,
                3,
                20
        ));
        checkpoints.add("review_rule_saved");
        List<RecordCoverageSegment> coverage = reviewRecordCoverage.getRecordCoverage(item.id());
        if (realProfile && coverage.stream().noneMatch(segment -> segment != null
                && !RECORD_COVERAGE_MISSING.equals(normalizeCoverageStatus(segment))
                && hasText(segment.recordUri()))) {
            throw new IllegalStateException("real VIDEO coverage did not return an exportable recording segment");
        }
        reviewRecordCoverage.syncRecordStorage(new ReviewRecordStorageSyncCommand(item.id(), command.operatorUserId(), coverage));
        checkpoints.add("record_coverage_synced");
        if (realProfile) {
            checkpoints.add("real_record_coverage_checked");
            checkpoints.add("sample_record_coverage_probed");
        }
        ReviewToEventResult eventResult = alertReviewEventBridge.convertToEvent(new ReviewToEventCommand(
                item.id(),
                command.operatorUserId(),
                false
        ));
        Long eventId = eventResult.eventId();
        if (eventId == null || eventId <= 0L) {
            throw new IllegalStateException("integration smoke event conversion did not return a positive eventId");
        }
        checkpoints.add("review_event_bound");
        checkpoints.add("review_event_bound_without_task_dispatch");
        ReviewCaseView reviewCase = reviewCaseManagement.createReviewCase(new ReviewCaseCommand(
                "integration smoke " + sourceAlertId,
                item.id(),
                List.of(item.id()),
                command.operatorUserId(),
                "runtime smoke"
        ));
        checkpoints.add("review_case_created");
        ReviewEvidenceExportJob queuedJob = reviewEvidenceExport.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                command.operatorUserId(),
                includeVideoExport ? "mp4" : "manifest",
                "integration smoke",
                null,
                null,
                command.allowedCameraIds()
        ));
        checkpoints.add("evidence_export_queued");
        reviewEvidenceExport.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(1, command.operatorUserId()));
        ReviewEvidenceExportJob job = deps.reviewItemStore.findExportJobByNo(queuedJob.jobNo())
                .orElseThrow(() -> new IllegalStateException("evidence export job disappeared"));
        if (!EXPORT_JOB_READY.equals(job.status())) {
            String workerError = toText(toStringObjectMap(
                    job.exportPackage().manifest().get("worker")
            ).get("lastError"));
            throw new IllegalStateException(firstText(
                    workerError,
                    "evidence export worker did not produce a ready package: " + job.status()
            ));
        }
        checkpoints.add("evidence_export_ready");
        if (job.boundEventIds() == null || !job.boundEventIds().contains(eventId)) {
            throw new IllegalStateException("integration smoke evidence export lost event binding: " + eventId);
        }
        checkpoints.add("evidence_export_event_bound");
        boolean videoExportConfirmed = hasConfirmedVideoExport(job);
        if (videoExportConfirmed) {
            checkpoints.add("video_export_confirmed");
        } else if (realProfile) {
            throw new IllegalStateException("VIDEO export did not return a real artifact");
        }
        ReviewManifestVerification verification = reviewEvidenceManifests.verifyEvidenceExportManifest(
                job.jobNo(),
                command.operatorUserId(),
                command.allowedCameraIds()
        );
        if (verification.valid()) {
            checkpoints.add("manifest_verified");
            if (includeVideoExport) {
                try (ReviewEvidenceDownloadArtifact artifact = reviewEvidenceExport.downloadEvidencePackage(
                        new ReviewEvidenceDownloadCommand(
                                job.jobNo(),
                                command.operatorUserId(),
                                command.allowedCameraIds(),
                                "integration smoke download audit"
                        ))) {
                    long actualLength = Files.size(artifact.temporaryFile());
                    String actualHash = sha256File(artifact.temporaryFile());
                    if (actualLength <= 0 || actualLength != artifact.contentLength()) {
                        throw new IllegalStateException("integration smoke downloaded package length mismatch");
                    }
                    if (!sha256HashesEqual(actualHash, artifact.fileHash())) {
                        throw new IllegalStateException("integration smoke downloaded package hash mismatch");
                    }
                    if (artifact.auditEntry() == null
                            || !"export_downloaded".equals(artifact.auditEntry().actionType())
                            || !sha256HashesEqual(actualHash, artifact.auditEntry().fileHash())) {
                        throw new IllegalStateException("integration smoke download audit did not bind real bytes");
                    }
                    checkpoints.add("evidence_download_bytes_verified");
                    checkpoints.add("evidence_download_audited");
                } catch (IllegalStateException | SecurityException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("integration smoke evidence download failed", exception);
                }
            }
        }
        if (realProfile) {
            checkpoints.add("sample_web_contract_renderable");
        }
        return new ReviewIntegrationSmokeResult(
                verification.valid() ? "passed" : "failed",
                item.id(),
                eventId,
                reviewCase.id(),
                job.jobNo(),
                verification.valid(),
                includeVideoExport,
                videoExportConfirmed,
                List.copyOf(checkpoints),
                LocalDateTime.now(),
                command.operatorUserId(),
                profile,
                smokeRule
        );
    }

    private static boolean hasConfirmedVideoExport(ReviewEvidenceExportJob job) {
        return toMapList(job.exportPackage().manifest().get("videoExports")).stream()
                .anyMatch(ReviewVideoExportPlanner::hasCompleteVideoExportProvenance);
    }

    private static int normalizePatrolAttempts(Integer maxAttempts) {
        if (maxAttempts == null || maxAttempts <= 0) {
            return 1;
        }
        return Math.min(maxAttempts, 5);
    }

    private static int normalizeRuntimeOutboxLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RUNTIME_OUTBOX_LIMIT;
        }
        return Math.min(limit, MAX_RUNTIME_OUTBOX_LIMIT);
    }

    private static List<String> runtimePatrolRecommendedActions(List<String> alerts,
                                                                ReviewRuntimeHealthReport healthReport) {
        List<String> actions = new ArrayList<>();
        for (String alert : alerts == null ? List.<String>of() : alerts) {
            String action = runtimePatrolActionForAlert(alert);
            if (hasText(action)) {
                actions.add(action);
            }
        }
        if (healthReport != null && healthReport.repairableCount() != null && healthReport.repairableCount() > 0) {
            actions.add("rerun_runtime_reconciliation");
        }
        return actions;
    }

    private static Map<String, Object> runtimePatrolAlertActions(List<String> alerts) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String alert : alerts == null ? List.<String>of() : alerts) {
            String action = runtimePatrolActionForAlert(alert);
            if (hasText(action)) {
                values.put(alert, action);
            }
        }
        return values;
    }

    private static String runtimePatrolActionForAlert(String alert) {
        if ("record_evidence_gap".equals(alert)) {
            return "backfill_record_evidence";
        }
        if (("record_evidence_gap:" + RECORD_GAP_VIDEO_URL_NOT_CONFIGURED).equals(alert)) {
            return "configure_video_record_query_url";
        }
        if ("record_storage_drift".equals(alert)) {
            return "inspect_record_storage_drift";
        }
        if (("record_storage_drift:" + RECORD_GAP_FILE_MISSING).equals(alert)) {
            return "review_missing_record_file";
        }
        if (("record_storage_drift:" + RECORD_GAP_RETENTION_EXPIRED).equals(alert)) {
            return "verify_record_retention_cleanup";
        }
        if (("record_storage_drift:" + RECORD_GAP_DISK_FULL).equals(alert)) {
            return "free_or_expand_recording_disk";
        }
        if (("record_storage_drift:" + RECORD_GAP_CACHE_FLUSH_FAILED).equals(alert)) {
            return "inspect_record_cache_flush";
        }
        if ("semantic_index_backlog".equals(alert)) {
            return "process_pending_semantic_index";
        }
        if ("evidence_export_failed".equals(alert)) {
            return "retry_evidence_export";
        }
        return null;
    }

    private List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> inspectRecordStorageDrift(
            List<ReviewItemAggregate> items) {
        Map<String, ReviewItemAggregate> devices = new LinkedHashMap<>();
        for (ReviewItemAggregate item : items == null ? List.<ReviewItemAggregate>of() : items) {
            if (item == null) {
                continue;
            }
            String deviceId = hasText(item.deviceId()) ? item.deviceId() : item.cameraId();
            if (hasText(deviceId)) {
                devices.putIfAbsent(deviceId, item);
            }
        }
        List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> reports = new ArrayList<>();
        for (Map.Entry<String, ReviewItemAggregate> entry : devices.entrySet()) {
            ReviewItemAggregate item = entry.getValue();
            try {
                ReviewRecordStorageDriftResolver.RecordStorageDriftReport report =
                        deps.recordStorageDriftResolver.inspect(
                                new ReviewRecordStorageDriftResolver.RecordStorageDriftRequest(
                                        entry.getKey(),
                                        item.cameraId(),
                                        Math.max(1, deps.recordStorageDriftRetentionHours)
                                ));
                if (report != null) {
                    reports.add(report);
                }
            } catch (RuntimeException ignored) {
                reports.add(new ReviewRecordStorageDriftResolver.RecordStorageDriftReport(
                        entry.getKey(),
                        item.cameraId(),
                        null,
                        false,
                        0,
                        1,
                        Map.of(RECORD_GAP_PROBE_FAILED, 1),
                        List.of(),
                        RECORD_GAP_PROBE_FAILED,
                        LocalDateTime.now()
                ));
            }
        }
        return List.copyOf(reports);
    }

    private static Map<String, Integer> mergeRecordStorageDriftReasons(
            Map<String, Integer> itemReasons,
            List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> reports) {
        Map<String, Integer> merged = new LinkedHashMap<>(itemReasons == null ? Map.of() : itemReasons);
        for (ReviewRecordStorageDriftResolver.RecordStorageDriftReport report
                : reports == null ? List.<ReviewRecordStorageDriftResolver.RecordStorageDriftReport>of() : reports) {
            if (report == null || report.issueReasons() == null) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : report.issueReasons().entrySet()) {
                String reason = normalizeRecordGapReason(entry.getKey());
                Integer count = entry.getValue();
                if (hasText(reason) && count != null && count > 0) {
                    merged.merge(reason, count, Integer::sum);
                }
            }
        }
        return Map.copyOf(merged);
    }

    private Map<String, Integer> recordGapReasons(List<ReviewItemAggregate> items) {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (ReviewItemAggregate item : items == null ? List.<ReviewItemAggregate>of() : items) {
            if (!reviewItemQuery.hasRecordEvidenceGap(item)) {
                continue;
            }
            for (String reason : recordGapReasons(item)) {
                reasons.merge(reason, 1, Integer::sum);
            }
        }
        return reasons;
    }

    private static Set<String> recordGapReasons(ReviewItemAggregate item) {
        Set<String> reasons = new LinkedHashSet<>();
        reasons.add(recordGapReason(item));
        reasons.addAll(recordStorageGapReasons(item));
        return reasons;
    }

    private static Map<String, Map<String, Object>> recordGapReasonCatalog() {
        Map<String, Map<String, Object>> catalog = new LinkedHashMap<>();
        for (RecordGapReasonDefinition definition : RECORD_GAP_REASON_CATALOG) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("code", definition.code());
            values.put("category", definition.category());
            values.put("labelZh", definition.labelZh());
            values.put("retryable", definition.retryable());
            values.put("aliases", List.copyOf(definition.aliases()));
            catalog.put(definition.code(), Map.copyOf(values));
        }
        return Map.copyOf(catalog);
    }

    private static Map<String, Map<String, Object>> recordGapReasonDetails(Map<String, Integer> reasonCounts) {
        if (reasonCounts == null || reasonCounts.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> details = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : reasonCounts.entrySet()) {
            recordGapReasonDefinition(entry.getKey()).ifPresent(definition -> {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("code", definition.code());
                values.put("category", definition.category());
                values.put("labelZh", definition.labelZh());
                values.put("retryable", definition.retryable());
                values.put("count", entry.getValue());
                details.put(definition.code(), Map.copyOf(values));
            });
        }
        return Map.copyOf(details);
    }

    private static Set<String> recordStorageGapReasons(ReviewItemAggregate item) {
        if (item == null || item.reviewData() == null) {
            return Set.of();
        }
        Map<String, Object> storage = toStringObjectMap(item.reviewData().get("recordStorage"));
        Map<String, Object> gapReasons = toStringObjectMap(storage.get("gapReasons"));
        if (gapReasons.isEmpty()) {
            return Set.of();
        }
        Set<String> reasons = new LinkedHashSet<>();
        for (String reason : gapReasons.keySet()) {
            String normalized = normalizeRecordGapReason(reason);
            if (hasText(normalized)) {
                reasons.add(normalized);
            }
        }
        return reasons;
    }

    private static boolean isRecordStorageDriftReason(String reason) {
        return switch (reason) {
            case RECORD_GAP_FILE_MISSING,
                 RECORD_GAP_RETENTION_EXPIRED,
                 RECORD_GAP_DISK_FULL,
                 RECORD_GAP_CACHE_FLUSH_FAILED -> true;
            default -> false;
        };
    }

    private ReviewDataConsistency reviewDataConsistency(ReviewItemAggregate item) {
        if (item == null) {
            return new ReviewDataConsistency(Map.of(), true, true);
        }
        Map<String, Object> current = item.reviewData() == null
                ? Map.of()
                : item.reviewData();
        Map<String, Object> normalized = new LinkedHashMap<>(current);
        AlertReviewDataSchemaValidator.ValidationResult schemaValidation =
                REVIEW_DATA_SCHEMA_VALIDATOR.validate(current);

        List<String> labels = toStringList(normalized.get("labels"), item.objectLabel());
        List<String> zones = toStringList(normalized.get("zones"), item.zoneCode());
        List<String> objectIds = toStringList(normalized.get("objectIds"), null);
        List<Double> bbox = toDoubleList(normalized.get("bbox"));
        Double confidence = toDouble(normalized.get("confidence"));
        if (!validReviewConfidence(confidence)) {
            confidence = null;
            normalized.remove("confidence");
        }
        if (!validReviewBbox(bbox)) {
            bbox = List.of();
            normalized.remove("bbox");
        }
        if (normalized.get("correlationId") != null && !(normalized.get("correlationId") instanceof String)) {
            normalized.remove("correlationId");
        }

        boolean schemaDrift = !schemaValidation.valid()
                || !Objects.equals(toInteger(normalized.get("reviewDataVersion")), REVIEW_DATA_VERSION)
                || !normalized.containsKey("labels")
                || !normalized.containsKey("zones")
                || !normalized.containsKey("objectIds")
                || toMapList(normalized.get("objects")).isEmpty()
                || toMapList(normalized.get("detections")).isEmpty();
        normalized.put("reviewDataVersion", REVIEW_DATA_VERSION);
        normalized.put("labels", labels);
        normalized.put("zones", zones);
        normalized.put("objectIds", objectIds);
        if (toMapList(normalized.get("objects")).isEmpty()
                || hasSchemaViolationPrefix(schemaValidation, "objects[")) {
            normalized.put("objects", buildReviewObjects(labels, objectIds, confidence, bbox));
        }
        if (toMapList(normalized.get("detections")).isEmpty()
                || hasSchemaViolationPrefix(schemaValidation, "detections[")) {
            normalized.put("detections", List.of(buildRuntimeRepairDetection(item, labels, zones, objectIds, confidence, bbox)));
        }

        Optional<ReviewSegmentView> persistedSegment = deps.reviewItemStore.findPersistedReviewSegment(item.id());
        Map<String, Object> segment = normalizeReviewSegmentDoubleWrite(
                item,
                normalized.get("reviewSegment"),
                objectIds,
                zones,
                persistedSegment.map(ReviewSegmentView::segmentId).orElse(null)
        );
        boolean segmentDoubleWriteDrift = reviewSegmentDoubleWriteDrift(
                item,
                toStringObjectMap(normalized.get("reviewSegment")),
                segment
        ) || persistedReviewSegmentDrift(item, segment, persistedSegment);
        normalized.put("reviewSegment", segment);
        return new ReviewDataConsistency(Map.copyOf(normalized), schemaDrift, segmentDoubleWriteDrift);
    }

    private boolean persistedReviewSegmentDrift(ReviewItemAggregate item,
                                                Map<String, Object> expectedSegment,
                                                Optional<ReviewSegmentView> persisted) {
        if (persisted.isEmpty()) {
            return true;
        }
        ReviewSegmentView actual = persisted.get();
        return !Objects.equals(item.id(), actual.reviewItemId())
                || !Objects.equals(firstText(expectedSegment.get("segmentId"), null), actual.segmentId())
                || !Objects.equals(firstText(expectedSegment.get("cameraId"), null), actual.cameraId())
                || !Objects.equals(firstText(expectedSegment.get("severity"), null), actual.severity())
                || !Objects.equals(firstText(expectedSegment.get("status"), null), actual.status())
                || !Objects.equals(toLocalDateTime(expectedSegment.get("startTime")), actual.startTime())
                || !Objects.equals(toLocalDateTime(expectedSegment.get("endTime")), actual.endTime())
                || !Objects.equals(toStringList(expectedSegment.get("objectIds"), null), actual.objectIds())
                || !Objects.equals(toStringList(expectedSegment.get("zones"), null), actual.zones())
                || !Objects.equals(toStringList(expectedSegment.get("sourceAlertIds"), null), actual.sourceAlertIds())
                || !Objects.equals(toMapList(expectedSegment.get("events")), actual.events())
                || !Objects.equals(expectedSegment, actual.metadata());
    }

    private static boolean hasSchemaViolationPrefix(AlertReviewDataSchemaValidator.ValidationResult validation,
                                                    String prefix) {
        return validation.violations().stream().anyMatch(violation -> violation.startsWith(prefix));
    }

    private static boolean validReviewConfidence(Double confidence) {
        return confidence == null || (Double.isFinite(confidence) && confidence >= 0D && confidence <= 1D);
    }

    private static boolean validReviewBbox(List<Double> bbox) {
        return bbox == null || bbox.isEmpty()
                || (bbox.size() == 4 && bbox.stream().allMatch(value -> value != null && Double.isFinite(value)));
    }

    private static Map<String, Object> buildRuntimeRepairDetection(ReviewItemAggregate item,
                                                                   List<String> labels,
                                                                   List<String> zones,
                                                                   List<String> objectIds,
                                                                   Double confidence,
                                                                   List<Double> bbox) {
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("sourceAlertId", firstSourceAlertId(item));
        detection.put("alertTime", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
        detection.put("cameraId", item.cameraId());
        detection.put("zoneCode", item.zoneCode());
        detection.put("ruleCode", item.ruleCode());
        detection.put("objectLabel", item.objectLabel());
        detection.put("labels", labels);
        detection.put("zones", zones);
        detection.put("objectIds", objectIds);
        detection.put("confidence", confidence);
        detection.put("bbox", bbox);
        detection.put("source", "runtime_repair");
        return immutableNonNullMap(detection);
    }

    private static Map<String, Object> normalizeReviewSegmentDoubleWrite(ReviewItemAggregate item,
                                                                         Object currentSegment,
                                                                         List<String> objectIds,
                                                                         List<String> zones,
                                                                         String persistedSegmentId) {
        Map<String, Object> segment = new LinkedHashMap<>(toStringObjectMap(currentSegment));
        segment.put("segmentId", firstText(
                segment.get("segmentId"),
                firstText(persistedSegmentId, buildRecoveryReviewSegmentId(item))
        ));
        segment.put("cameraId", item.cameraId());
        String severity = firstText(segment.get("severity"), reviewSegmentSeverity(item.sourceAlertType()));
        segment.put("severity", Set.of("detection", "alert").contains(severity)
                ? severity
                : reviewSegmentSeverity(item.sourceAlertType()));
        String status = firstText(segment.get("status"), "active");
        String normalizedStatus = Set.of("active", "detection", "alert", "ended").contains(status)
                ? status
                : "active";
        segment.put("status", normalizedStatus);
        segment.put("startTime", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
        LocalDateTime endedAt = "ended".equals(normalizedStatus)
                ? toLocalDateTime(segment.get("endTime"))
                : null;
        LocalDateTime normalizedEndTime = endedAt == null ? item.lastAlertTime() : endedAt;
        segment.put("endTime", normalizedEndTime == null ? null : normalizedEndTime.toString());
        segment.put("objectIds", objectIds.isEmpty()
                ? toStringList(segment.get("objectIds"), null)
                : objectIds);
        segment.put("zones", zones.isEmpty()
                ? toStringList(segment.get("zones"), null)
                : zones);
        segment.put("sourceAlertIds", item.sourceAlertIds() == null
                ? List.of()
                : List.copyOf(item.sourceAlertIds()));
        if (toMapList(segment.get("events")).isEmpty()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "runtime_repair");
            event.put("happenedAt", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
            event.put("sourceAlertId", firstSourceAlertId(item));
            event.put("ruleCode", item.ruleCode());
            event.put("objectIds", segment.get("objectIds"));
            event.put("labels", toStringList(null, item.objectLabel()));
            event.put("zones", segment.get("zones"));
            segment.put("events", List.of(immutableNonNullMap(event)));
        }
        return immutableNonNullMap(segment);
    }

    private static boolean reviewSegmentDoubleWriteDrift(ReviewItemAggregate item,
                                                         Map<String, Object> current,
                                                         Map<String, Object> normalized) {
        if (current == null || current.isEmpty()) {
            return true;
        }
        return !Objects.equals(firstText(current.get("cameraId"), null), normalized.get("cameraId"))
                || !Objects.equals(firstText(current.get("startTime"), null), normalized.get("startTime"))
                || !Objects.equals(firstText(current.get("endTime"), null), normalized.get("endTime"))
                || !Objects.equals(toStringList(current.get("objectIds"), null), normalized.get("objectIds"))
                || !Objects.equals(toStringList(current.get("zones"), null), normalized.get("zones"))
                || !Objects.equals(toStringList(current.get("sourceAlertIds"), null), normalized.get("sourceAlertIds"))
                || !Objects.equals(firstText(current.get("status"), null), normalized.get("status"))
                || !Objects.equals(firstText(current.get("severity"), null), normalized.get("severity"))
                || !Objects.equals(firstText(current.get("segmentId"), null), normalized.get("segmentId"))
                || toMapList(current.get("events")).isEmpty()
                || item.firstAlertTime() == null;
    }
}
