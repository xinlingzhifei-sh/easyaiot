package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewEventReconcileJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewEvidenceExportWorkerJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewOperationsReportJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimeOutboxJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewRuntimePatrolJob;
import com.basiclab.iot.system.job.supervision.SupervisionAlertReviewSemanticIndexJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.AlertClueCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageSegment;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseDraft;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseOperationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseOwnerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseTimelineItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemAggregate;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemDraft;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewWorkbenchSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RuleSuggestionStat;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RuleSuggestionOperationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RuleSuggestionPreview;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewUserStatusCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewUserStatusView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticSearchCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticHit;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummaryConfirmation;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummaryConfirmationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportPackage;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportWorkerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportWorkerRun;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceDownloadArtifact;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceDownloadCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoDownloadRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVerificationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVerificationReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoSegmentRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewDetailStreamItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewIntegrationSmokeCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewIntegrationSmokeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationsReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraPermissionResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraTopology;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCameraTopologyResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgement;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgementCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReconciliationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReconciliationResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeHealthCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeHealthReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeLockAcquisition;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxDeliveryResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxMessage;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxPublishCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxPublishResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimePatrolCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimePatrolResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSegmentView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleGeometryCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleGeometryEvaluation;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleReplayCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleReplayResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewLifecycleCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewManifestVerification;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewMediaAccessAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewMediaAccessCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewPlaybackAccess;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewPlaybackCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRecordStorageSyncCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRecordStorageSyncResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEvaluation;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEvaluationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticReindexCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticReindexJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticTriggerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticTriggerResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticWorkerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticWorkerRun;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.VideoEvidenceExportProvider;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.EventProjection;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.EventProjectionStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceResult;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider;
import com.basiclab.iot.system.service.supervision.ReviewAiSummaryRedactionPolicy;
import com.basiclab.iot.system.service.supervision.ReviewEvidenceManifestSigner;
import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver.RecordStorageDriftReport;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewAiSummaryRequest;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewSemanticSearchRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewServiceImpl;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewServiceTest {

    @Test
    void reviewSegmentMutationsDeclareOneTransactionBoundary() throws Exception {
        Transactional ingestTransaction = SupervisionAlertReviewServiceImpl.class
                .getMethod("ingestClue", AlertClueCommand.class)
                .getAnnotation(Transactional.class);
        Transactional lifecycleTransaction = SupervisionAlertReviewServiceImpl.class
                .getMethod("updateReviewLifecycle", ReviewLifecycleCommand.class)
                .getAnnotation(Transactional.class);

        assertTrue(ingestTransaction != null, "ingest must keep identity, candidate, item and segment writes atomic");
        assertTrue(lifecycleTransaction != null, "segment lifecycle changes must use the same camera transaction lock");
    }

    @Test
    void ingestCluesMergesNearbyAlertSnapshotsAndRecordsIntoOneReviewItem() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 6, 30, 10, 0);

        ReviewItemAggregate first = service.ingestClue(newClue("alert-001", firstTime, "snap-001.jpg", "record-001.mp4"));
        ReviewItemAggregate second = service.ingestClue(newClue("alert-002", firstTime.plusSeconds(35), "snap-002.jpg", "record-002.mp4"));

        assertEquals(first.id(), second.id());
        assertEquals("pending_review", second.reviewStatus());
        assertEquals(2, second.alertCount());
        assertEquals(List.of("alert-001", "alert-002"), second.sourceAlertIds());
        assertEquals(firstTime, second.firstAlertTime());
        assertEquals(firstTime.plusSeconds(35), second.lastAlertTime());

        List<ReviewEvidenceItem> timeline = service.getTimeline(second.id());
        assertEquals(List.of("snapshot", "record", "snapshot", "record"),
                timeline.stream().map(ReviewEvidenceItem::materialType).toList());
        assertEquals(List.of("snap-001.jpg", "record-001.mp4", "snap-002.jpg", "record-002.mp4"),
                timeline.stream().map(ReviewEvidenceItem::materialUri).toList());
    }

    @Test
    void concurrentIngestKeepsSingleActiveReviewSegmentForSameCameraWindow() throws Exception {
        SlowCreateReviewItemStore itemStore = new SlowCreateReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 3, 21, 0);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ReviewItemAggregate> first = new AtomicReference<>();
        AtomicReference<ReviewItemAggregate> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread firstThread = new Thread(() -> ingestAfterStart(
                service,
                newClue("alert-race-001", firstTime, "race-1.jpg", "race-1.mp4"),
                ready,
                start,
                first,
                failure
        ), "review-segment-race-1");
        Thread secondThread = new Thread(() -> ingestAfterStart(
                service,
                newClue("alert-race-002", firstTime.plusSeconds(30), "race-2.jpg", "race-2.mp4"),
                ready,
                start,
                second,
                failure
        ), "review-segment-race-2");

        firstThread.start();
        secondThread.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS), "both ingest threads should be ready");
        start.countDown();
        firstThread.join(5_000);
        secondThread.join(5_000);

        assertFalse(firstThread.isAlive(), "first ingest thread should finish");
        assertFalse(secondThread.isAlive(), "second ingest thread should finish");
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        assertEquals(first.get().id(), second.get().id());
        assertEquals(1, itemStore.listWorkbench(null).size());
        ReviewSegmentView segment = service.getReviewSegment(first.get().id());
        assertEquals("alert", segment.status());
        assertEquals(firstTime, segment.startTime());
        assertEquals(firstTime.plusSeconds(30), segment.endTime());
        assertEquals(List.of("alert-race-001", "alert-race-002"), segment.sourceAlertIds());
    }

    @Test
    void ingestCluesMergesSameCameraTimeWindowAcrossRulesZonesAndObjects() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 6, 30, 10, 0);

        ReviewItemAggregate first = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-zone-a-person",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                firstTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                "snap-person.jpg",
                "record-person.mp4",
                "hash-person",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-person"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-window"
        ));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-zone-b-vehicle",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                firstTime.plusSeconds(120),
                "device-01",
                "camera-01",
                "zone-b",
                "vehicle",
                6,
                "snap-vehicle.jpg",
                "record-vehicle.mp4",
                "hash-vehicle",
                List.of("vehicle"),
                List.of("zone-b"),
                List.of("obj-vehicle"),
                0.82D,
                List.of(0.5D, 0.6D, 0.7D, 0.8D),
                "corr-window"
        ));

        assertEquals(first.id(), second.id());
        assertEquals(firstTime, second.firstAlertTime());
        assertEquals(firstTime.plusSeconds(120), second.lastAlertTime());
        assertEquals(2, second.alertCount());
        assertEquals(List.of("alert-zone-a-person", "alert-zone-b-vehicle"), second.sourceAlertIds());
        assertEquals(List.of("person", "vehicle"), second.reviewData().get("labels"));
        assertEquals(List.of("zone-a", "zone-b"), second.reviewData().get("zones"));
        assertEquals(List.of("obj-person", "obj-vehicle"), second.reviewData().get("objectIds"));
        assertEquals(firstTime.toString(), second.reviewData().get("startTime"));
        assertEquals(firstTime.plusSeconds(120).toString(), second.reviewData().get("endTime"));
        assertEquals(2, ((List<?>) second.reviewData().get("objects")).size());
        List<?> detections = (List<?>) second.reviewData().get("detections");
        assertEquals(2, detections.size());
        assertTrue(detections.stream().map(Map.class::cast)
                .anyMatch(detection -> "alert-zone-a-person".equals(detection.get("sourceAlertId"))
                        && SupervisionRuleSeeds.RULE_RESTRICTED_AREA.equals(detection.get("ruleCode"))));
        assertTrue(detections.stream().map(Map.class::cast)
                .anyMatch(detection -> "alert-zone-b-vehicle".equals(detection.get("sourceAlertId"))
                        && SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING.equals(detection.get("ruleCode"))));
    }

    @Test
    void ingestDuplicatePayloadHashKeepsReviewItemIdempotentWithoutExtraEvidence() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 9, 0);

        ReviewItemAggregate first = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-idempotent-a",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "idempotent-a.jpg",
                "idempotent-a.mp4",
                "payload-hash-idempotent",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-001"),
                0.92D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-idempotent"
        ));
        ReviewItemAggregate duplicate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-idempotent-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime.plusSeconds(10),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "idempotent-b.jpg",
                "idempotent-b.mp4",
                "payload-hash-idempotent",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-001"),
                0.92D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-idempotent"
        ));

        assertEquals(first.id(), duplicate.id());
        assertEquals(1, duplicate.alertCount());
        assertEquals(List.of("alert-idempotent-a"), duplicate.sourceAlertIds());
        assertEquals(List.of("video:payload:payload-hash-idempotent"),
                duplicate.reviewData().get("ingestIdentityKeys"));
        assertEquals(List.of("snapshot", "record"),
                service.getTimeline(duplicate.id()).stream().map(ReviewEvidenceItem::materialType).toList());
    }

    @Test
    void ingestRechecksIdentityAfterPostgresSegmentExclusionConflict() {
        SegmentConflictWinnerStore itemStore = new SegmentConflictWinnerStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 10, 9, 0);

        ReviewItemAggregate recovered = service.ingestClue(newClue(
                "alert-23p01-recheck",
                alertTime,
                "23p01.jpg",
                "23p01.mp4"
        ));

        assertEquals(1, itemStore.createAttempts());
        assertEquals(List.of("alert-23p01-recheck"), recovered.sourceAlertIds());
        assertEquals(1, itemStore.listWorkbench(null).size());
    }

    @Test
    void ingestSplitsOutsideMergeWindowAndRecordsAggregationPolicy() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 2, 10, 0);

        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-window-a",
                firstTime,
                "window-a.jpg",
                "window-a.mp4"
        ));
        ReviewItemAggregate split = service.ingestClue(newClue(
                "alert-window-b",
                firstTime.plusSeconds(301),
                "window-b.jpg",
                "window-b.mp4"
        ));

        assertFalse(Objects.equals(first.id(), split.id()));
        Map<?, ?> firstAggregation = (Map<?, ?>) first.reviewData().get("aggregation");
        Map<?, ?> splitAggregation = (Map<?, ?>) split.reviewData().get("aggregation");
        assertEquals("same_camera_sliding_window", firstAggregation.get("mergePolicy"));
        assertEquals(300, firstAggregation.get("mergeWindowSeconds"));
        assertEquals("new_item_when_gap_exceeds_merge_window_or_existing_not_pending",
                splitAggregation.get("splitPolicy"));
        assertEquals("video|camera-01", splitAggregation.get("aggregationKey"));
    }

    @Test
    void endedSegmentsRestartingAtSameInstantUseDistinctStableSegmentIds() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 13, 13, 45, 16);

        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-same-instant-a",
                startTime,
                "same-instant-a.jpg",
                "same-instant-a.mp4"
        ));
        service.updateReviewLifecycle(new ReviewLifecycleCommand(
                first.id(),
                "ended",
                startTime,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                null
        ));

        AlertClueCommand secondClue = newClue(
                "alert-same-instant-b",
                startTime,
                "same-instant-b.jpg",
                "same-instant-b.mp4"
        );
        ReviewItemAggregate second = service.ingestClue(secondClue);
        ReviewItemAggregate retry = service.ingestClue(secondClue);

        assertNotEquals(first.id(), second.id());
        assertNotEquals(service.getReviewSegment(first.id()).segmentId(),
                service.getReviewSegment(second.id()).segmentId());
        assertEquals(second.id(), retry.id());
        assertEquals(service.getReviewSegment(second.id()).segmentId(),
                service.getReviewSegment(retry.id()).segmentId());
    }

    @Test
    void segmentIdsStayWithinDatabaseLimitForMaximumCameraIdLength() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        String cameraId = "c".repeat(128);

        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-maximum-camera-id",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 7, 13, 14, 0),
                cameraId,
                cameraId,
                "zone-a",
                "person",
                15,
                "maximum-camera-id.jpg",
                "maximum-camera-id.mp4",
                null
        ));

        String segmentId = service.getReviewSegment(item.id()).segmentId();
        assertEquals(68, segmentId.length());
        assertTrue(segmentId.matches("seg-[0-9a-f]{64}"));
    }

    @Test
    void detectionOutsideMergeWindowEndsPreviousOpenSegmentAtCutoffBeforeSplit() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 10, 9, 20);
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-window-close-a",
                firstTime,
                "window-close-a.jpg",
                null
        ));

        ReviewItemAggregate detection = service.ingestClue(new AlertClueCommand(
                "video",
                "detection-window-close-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "motion_detection",
                firstTime.plusSeconds(360),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                3,
                "window-close-b.jpg",
                null,
                "hash-window-close-b",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-window-close-b"),
                0.73D,
                List.of(2D, 3D, 9D, 19D),
                "corr-window-close"
        ));

        ReviewSegmentView ended = service.getReviewSegment(first.id());
        ReviewSegmentView split = service.getReviewSegment(detection.id());
        assertNotEquals(first.id(), detection.id());
        assertEquals("ended", ended.status());
        assertEquals(firstTime.plusSeconds(300), ended.endTime());
        assertFalse(ended.endTime().isAfter(split.startTime()));
        assertEquals("detection", split.severity());
    }

    @Test
    void alertClueStartsAlertSegmentWithoutWaitingForLifecycleCallback() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 10, 9, 30);

        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-immediate-escalation",
                alertTime,
                "immediate-escalation.jpg",
                null
        ));

        ReviewSegmentView segment = service.getReviewSegment(item.id());
        assertEquals("alert", segment.status());
        assertEquals("alert", segment.severity());
    }

    @Test
    void reviewStatusCanBeConfirmedOrIgnoredBeforeConversion() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());

        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-001",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                "snap-001.jpg",
                "record-001.mp4"
        ));

        ReviewItemAggregate reviewed = service.markReviewed(new ReviewOperationCommand(item.id(), 9001L, null));
        assertEquals("reviewed", reviewed.reviewStatus());
        assertEquals(9001L, reviewed.reviewerUserId());
        assertTrue(reviewed.reviewedAt() != null);

        ReviewItemAggregate ignoredItem = service.ingestClue(newClue(
                "alert-002",
                LocalDateTime.of(2026, 6, 30, 10, 10),
                "snap-002.jpg",
                "record-002.mp4"
        ));
        ReviewItemAggregate ignored = service.ignore(new ReviewOperationCommand(ignoredItem.id(), 9002L, "duplicate"));
        assertEquals("ignored", ignored.reviewStatus());
        assertEquals(9002L, ignored.reviewerUserId());
        assertEquals("duplicate", ignored.ignoreReason());
    }

    @Test
    void reviewStatusActionsAreIdempotentAndRejectConflictingReviewerActions() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-review-conflict",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                "snap-review-conflict.jpg",
                "record-review-conflict.mp4"
        ));

        ReviewItemAggregate firstReviewed = service.markReviewed(new ReviewOperationCommand(item.id(), 9001L, null));
        ReviewItemAggregate repeatedReviewed = service.markReviewed(new ReviewOperationCommand(item.id(), 9001L, null));

        assertEquals("reviewed", repeatedReviewed.reviewStatus());
        assertEquals(firstReviewed.reviewerUserId(), repeatedReviewed.reviewerUserId());
        assertEquals(firstReviewed.reviewedAt(), repeatedReviewed.reviewedAt());
        IllegalStateException rejectedIgnore = assertThrows(IllegalStateException.class,
                () -> service.ignore(new ReviewOperationCommand(item.id(), 9002L, "duplicate")));
        assertEquals("review_item_status_conflict: reviewed -> ignored", rejectedIgnore.getMessage());
        IllegalStateException rejectedFalsePositive = assertThrows(IllegalStateException.class,
                () -> service.markFalsePositive(new ReviewOperationCommand(item.id(), 9003L, "zone too wide")));
        assertEquals("review_item_status_conflict: reviewed -> false_positive", rejectedFalsePositive.getMessage());
        assertEquals("reviewed", itemStore.findById(item.id()).orElseThrow().reviewStatus());
        assertEquals(9001L, itemStore.findById(item.id()).orElseThrow().reviewerUserId());
    }

    @Test
    void convertReviewItemToSupervisionEventUsesReviewItemAsIdempotentSource() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        List<AlertToEventCommand> eventCommands = new ArrayList<>();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), command -> {
            eventCommands.add(command);
            return new AlertToEventResult(
                    7001L,
                    command.sourceSystem(),
                    command.sourceAlertId(),
                    command.ruleCode(),
                    "supervision_order",
                    SupervisionEventLevelEnum.L2,
                    SupervisionEventStatusEnum.DISPATCHED.getCode(),
                    false
            );
        });
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-001",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                "snap-001.jpg",
                "record-001.mp4"
        ));

        var result = service.convertToEvent(new ReviewToEventCommand(item.id(), 9001L));

        assertEquals(7001L, result.eventId());
        assertEquals("converted", result.reviewStatus());
        assertEquals(7001L, itemStore.findById(item.id()).orElseThrow().eventId());
        assertEquals(List.of(new AlertToEventCommand(
                "alert_review",
                item.reviewItemNo(),
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                null
        )), eventCommands);
    }

    @Test
    void convertedReviewItemAllowsEvidenceHardeningButRejectsFalsePositiveRollback() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7002L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                )
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-converted-policy",
                LocalDateTime.of(2026, 7, 4, 11, 0),
                "converted-policy.jpg",
                "converted-policy.mp4"
        ));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9001L));

        ReviewRecordStorageSyncResult syncResult = service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9002L,
                List.of(new RecordCoverageSegment(
                        "available",
                        LocalDateTime.of(2026, 7, 4, 10, 59),
                        LocalDateTime.of(2026, 7, 4, 11, 1),
                        1,
                        "converted-policy.mp4"
                ))
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "converted policy case",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob exportJob = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9003L,
                "manifest",
                "post event evidence hardening"
        ));
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> service.markFalsePositive(new ReviewOperationCommand(item.id(), 9004L, "late false positive")));

        assertEquals("complete", syncResult.syncStatus());
        assertEquals(1, syncResult.availableSegmentCount());
        assertTrue(exportJob.boundEventIds().contains(7002L));
        assertEquals("converted", itemStore.findById(item.id()).orElseThrow().reviewStatus());
        assertEquals("converted_review_item_cannot_be_marked_false_positive", rejected.getMessage());
    }

    @Test
    void regionRuleSuppliesRuleCodeOnlyWhenZoneObjectAndStayTimeMatch() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(new InMemoryReviewItemStore(), ruleStore, unusedEventService());
        service.saveRule(new ReviewRuleCommand(
                null,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "A\u533A\u7981\u5165\u590D\u6838",
                "video",
                "camera-01",
                "zone-a",
                "person",
                10,
                null,
                null,
                true
        ));

        ReviewItemAggregate matched = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-001",
                null,
                "restricted_area",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "snap-001.jpg",
                "record-001.mp4",
                null
        ));
        ReviewItemAggregate notMatched = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-002",
                null,
                "restricted_area",
                LocalDateTime.of(2026, 6, 30, 10, 10),
                "device-01",
                "camera-01",
                "zone-b",
                "person",
                12,
                "snap-002.jpg",
                "record-002.mp4",
                null
        ));

        assertEquals(SupervisionRuleSeeds.RULE_RESTRICTED_AREA, matched.ruleCode());
        assertEquals(SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING, notMatched.ruleCode());
    }

    @Test
    void ingestWithoutRecordUriBackfillsRecordEvidenceWhenResolverFindsRecord() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 11, 0);
        LocalDateTime recordStartTime = alertTime.minusSeconds(15);
        CapturingRecordEvidenceResolver resolver = new CapturingRecordEvidenceResolver(
                Optional.of(new RecordEvidenceResult(
                        "record-from-video.mp4",
                        "playback_match",
                        recordStartTime
                ))
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                resolver,
                noEventProjectionStore()
        );
        ReviewItemAggregate item = service.ingestClue(newClue("alert-003", alertTime, "snap-003.jpg", null));

        assertEquals("found", item.recordEvidenceStatus());
        assertEquals("playback_match", item.recordEvidenceMessage());
        assertTrue(item.recordEvidenceCheckedAt() != null);
        assertEquals(List.of(new RecordEvidenceRequest("alert-003", "device-01", "camera-01", alertTime)),
                resolver.requests());
        assertEquals(List.of("snapshot", "record"),
                service.getTimeline(item.id()).stream().map(ReviewEvidenceItem::materialType).toList());
        assertEquals(List.of("snap-003.jpg", "record-from-video.mp4"),
                service.getTimeline(item.id()).stream().map(ReviewEvidenceItem::materialUri).toList());
        ReviewEvidenceItem recordEvidence = service.getTimeline(item.id()).stream()
                .filter(evidence -> "record".equals(evidence.materialType()))
                .findFirst()
                .orElseThrow();
        assertEquals(recordStartTime, recordEvidence.recordStartTime());

        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "resolver record playback",
                item.id(),
                List.of(item.id())
        ));
        ReviewCaseTimelineItem caseRecord = service.getReviewCaseTimeline(reviewCase.id()).stream()
                .filter(evidence -> "record".equals(evidence.materialType()))
                .findFirst()
                .orElseThrow();
        assertEquals(recordStartTime, caseRecord.recordStartTime());
        assertEquals(15, caseRecord.playbackOffsetSeconds());
    }

    @Test
    void ingestWithoutRecordUriMarksMissingWhenResolverHasNoRecord() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                new CapturingRecordEvidenceResolver(Optional.empty()),
                noEventProjectionStore()
        );

        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-004",
                LocalDateTime.of(2026, 6, 30, 11, 5),
                "snap-004.jpg",
                null
        ));

        assertEquals("missing", item.recordEvidenceStatus());
        assertTrue(item.recordEvidenceCheckedAt() != null);
        assertEquals(List.of("snapshot"),
                service.getTimeline(item.id()).stream().map(ReviewEvidenceItem::materialType).toList());
    }

    @Test
    void retryRecordEvidenceDoesNotDuplicateExistingRecordEvidence() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                new CapturingRecordEvidenceResolver(Optional.of(new RecordEvidenceResult("record-retry.mp4", "retry"))),
                noEventProjectionStore()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-005",
                LocalDateTime.of(2026, 6, 30, 11, 10),
                "snap-005.jpg",
                null
        ));

        service.retryRecordEvidence(item.id());
        ReviewItemAggregate retried = service.retryRecordEvidence(item.id());

        assertEquals("found", retried.recordEvidenceStatus());
        assertEquals(1, service.getTimeline(item.id()).stream()
                .filter(evidence -> "record".equals(evidence.materialType()))
                .count());
    }

    @Test
    void convertedReviewItemCarriesLinkedEventProjection() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7002L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.DISPATCHED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                eventId -> Optional.of(new EventProjection(eventId, "pending_close_check", "passed", "complete"))
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-006",
                LocalDateTime.of(2026, 6, 30, 11, 20),
                "snap-006.jpg",
                "record-006.mp4"
        ));

        service.convertToEvent(new ReviewToEventCommand(item.id(), 9001L));
        ReviewItemAggregate linked = service.listWorkbench(null).get(0);

        assertEquals(7002L, linked.eventId());
        assertEquals("pending_close_check", linked.eventStatus());
        assertEquals("passed", linked.closeCheckStatus());
        assertEquals("complete", linked.evidenceStatus());
    }

    @Test
    void eventReconcileJobPersistsReverseEventProjectionOutsideListQuery() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        Map<Long, EventProjection> projections = new LinkedHashMap<>();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7003L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                eventId -> Optional.ofNullable(projections.get(eventId))
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-event-reconcile-job",
                LocalDateTime.of(2026, 7, 3, 16, 5),
                "event-reconcile.jpg",
                "event-reconcile.mp4"
        ));

        service.convertToEvent(new ReviewToEventCommand(item.id(), 9008L));
        assertEquals("converted", itemStore.findById(item.id()).orElseThrow().reviewStatus());
        assertEquals(null, itemStore.findById(item.id()).orElseThrow().eventStatus());

        projections.put(7003L, new EventProjection(7003L, "closed", "passed", "complete"));
        String summary = new SupervisionAlertReviewEventReconcileJob(service).execute("");

        ReviewItemAggregate reconciled = itemStore.findById(item.id()).orElseThrow();
        assertTrue(summary.contains("reconciled=1"));
        assertEquals("converted", reconciled.reviewStatus());
        assertEquals("closed", reconciled.eventStatus());
        assertEquals("passed", reconciled.closeCheckStatus());
        assertEquals("complete", reconciled.evidenceStatus());
        assertEquals("closed", reconciled.eventReviewStatus());
        Map<String, Object> eventProjection = toStringObjectMap(reconciled.reviewData().get("eventProjection"));
        assertEquals("closed", eventProjection.get("eventStatus"));
        assertEquals("passed", eventProjection.get("closeCheckStatus"));
        assertEquals("complete", eventProjection.get("evidenceStatus"));
        assertEquals("closed", eventProjection.get("eventReviewStatus"));
    }

    @Test
    void eventReconcileKeepsConvertedItemWhenEventRollbackRequiresRework() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        Map<Long, EventProjection> projections = new LinkedHashMap<>();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7004L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                eventId -> Optional.ofNullable(projections.get(eventId))
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-event-rework-conflict",
                LocalDateTime.of(2026, 7, 4, 9, 30),
                "event-rework.jpg",
                "event-rework.mp4"
        ));

        service.convertToEvent(new ReviewToEventCommand(item.id(), 9008L));
        projections.put(7004L, new EventProjection(7004L, "rework_required", "recheck_required", "complete"));
        String summary = new SupervisionAlertReviewEventReconcileJob(service).execute("");

        ReviewItemAggregate reconciled = itemStore.findById(item.id()).orElseThrow();
        assertTrue(summary.contains("conflict=1"));
        assertEquals("converted", reconciled.reviewStatus());
        assertEquals("rechecking", reconciled.eventReviewStatus());
        Map<String, Object> eventProjection = toStringObjectMap(reconciled.reviewData().get("eventProjection"));
        assertEquals("keep_converted_review_item", eventProjection.get("conflictPolicy"));
        assertEquals("event_rework_after_conversion", eventProjection.get("conflictStatus"));
        assertEquals("converted_is_terminal", eventProjection.get("reviewItemStatusPolicy"));
        assertEquals("rechecking", eventProjection.get("eventReviewStatusAtConflict"));
    }

    @Test
    void reviewItemKeepsFrigateLikeReviewDataFromDetectionContext() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );

        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-rich-001",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 6, 30, 12, 0),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                "snap-rich.jpg",
                "record-rich.mp4",
                "payload-hash-001",
                List.of("person", "helmet"),
                List.of("zone-a", "doorway"),
                List.of("obj-001", "obj-002"),
                0.92D,
                List.of(0.11D, 0.22D, 0.33D, 0.44D),
                "corr-001"
        ));

        assertEquals(List.of("person", "helmet"), item.reviewData().get("labels"));
        assertEquals(List.of("zone-a", "doorway"), item.reviewData().get("zones"));
        assertEquals(List.of("obj-001", "obj-002"), item.reviewData().get("objectIds"));
        assertEquals(0.92D, item.reviewData().get("confidence"));
        assertEquals(List.of(0.11D, 0.22D, 0.33D, 0.44D), item.reviewData().get("bbox"));
        assertEquals("corr-001", item.reviewData().get("correlationId"));
    }

    @Test
    void ingestRejectsReviewDataThatViolatesVersionedConfidenceAndBboxSchema() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        AlertClueCommand invalid = new AlertClueCommand(
                "video",
                "alert-invalid-review-data",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 7, 10, 8, 0),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                10,
                "invalid.jpg",
                null,
                "invalid-review-data-hash",
                List.of("person"),
                List.of("zone-a"),
                List.of("object-01"),
                1.2D,
                List.of(1D, 2D, 3D),
                "corr-invalid"
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.ingestClue(invalid));

        assertTrue(error.getMessage().contains("reviewData schema validation failed"));
        assertTrue(error.getMessage().contains("confidence:range="));
        assertTrue(error.getMessage().contains("bbox:items="));
    }

    @Test
    void recordCoverageReturnsAvailableOrMissingWindowSegments() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 12, 10);

        ReviewItemAggregate withRecord = service.ingestClue(newClue("alert-record", alertTime, "snap.jpg", "record.mp4"));
        ReviewItemAggregate withoutRecord = service.ingestClue(newClue("alert-missing", alertTime.plusMinutes(10), "snap-2.jpg", null));

        List<RecordCoverageSegment> availableCoverage = service.getRecordCoverage(withRecord.id());
        assertEquals(1, availableCoverage.size());
        assertEquals("available", availableCoverage.get(0).status());
        assertEquals(alertTime.minusSeconds(300), availableCoverage.get(0).startTime());
        assertEquals(alertTime.plusSeconds(300), availableCoverage.get(0).endTime());
        assertEquals("record.mp4", availableCoverage.get(0).recordUri());

        List<RecordCoverageSegment> missingCoverage = service.getRecordCoverage(withoutRecord.id());
        assertEquals(1, missingCoverage.size());
        assertEquals("missing", missingCoverage.get(0).status());
        assertEquals(alertTime.plusMinutes(10).minusSeconds(300), missingCoverage.get(0).startTime());
        assertEquals(alertTime.plusMinutes(10).plusSeconds(300), missingCoverage.get(0).endTime());
    }

    @Test
    void recordCoverageFallsBackToMissingWindowWhenVideoResolverFails() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> {
                    throw new IllegalStateException("video unavailable");
                }
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 12, 15);
        ReviewItemAggregate item = service.ingestClue(newClue("alert-video-down", alertTime, "snap-down.jpg", null));

        List<RecordCoverageSegment> coverage = service.getRecordCoverage(item.id());

        assertEquals(1, coverage.size());
        assertEquals("missing", coverage.get(0).status());
        assertEquals(alertTime.minusSeconds(300), coverage.get(0).startTime());
        assertEquals(alertTime.plusSeconds(300), coverage.get(0).endTime());
    }

    @Test
    void falsePositiveActionMarksStatusAndCreatesRuleSuggestion() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-fp",
                LocalDateTime.of(2026, 6, 30, 12, 20),
                "snap-fp.jpg",
                "record-fp.mp4"
        ));

        ReviewItemAggregate falsePositive = service.markFalsePositive(
                new ReviewOperationCommand(item.id(), 9003L, "zone_too_wide")
        );

        assertEquals("false_positive", falsePositive.reviewStatus());
        assertEquals(9003L, falsePositive.reviewerUserId());
        assertEquals("zone_too_wide", falsePositive.ignoreReason());
        assertEquals("suppress_label_zone", falsePositive.ruleSuggestion().get("action"));
        assertEquals("camera-01", falsePositive.ruleSuggestion().get("cameraId"));
        assertEquals("zone-a", falsePositive.ruleSuggestion().get("zoneCode"));
        assertEquals("person", falsePositive.ruleSuggestion().get("objectLabel"));
        assertEquals(3, falsePositive.ruleSuggestion().get("minimumSampleCount"));
        assertEquals(1, falsePositive.ruleSuggestion().get("currentSampleCount"));
        assertEquals(false, falsePositive.ruleSuggestion().get("sampleRequirementMet"));
        assertEquals("low_sample_requires_more_review", falsePositive.ruleSuggestion().get("riskNote"));
        Map<?, ?> impactScope = (Map<?, ?>) falsePositive.ruleSuggestion().get("impactScope");
        assertEquals(List.of("camera-01"), impactScope.get("cameraIds"));
        assertEquals(List.of("zone-a"), impactScope.get("zoneCodes"));
        assertEquals(List.of("person"), impactScope.get("objectLabels"));
        Map<?, ?> beforeAfter = (Map<?, ?>) falsePositive.ruleSuggestion().get("beforeAfterComparison");
        assertEquals(1, beforeAfter.get("beforeHitCount"));
        assertEquals(0, beforeAfter.get("afterEstimatedHitCount"));
        assertEquals(1, beforeAfter.get("falsePositiveBeforeCount"));
        assertEquals(0, beforeAfter.get("falsePositiveAfterCount"));
        assertEquals(0, beforeAfter.get("possibleMissedCount"));
    }

    @Test
    void reviewCaseCollectsMultipleCameraCluesIntoOneTimeline() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 6, 30, 12, 30);
        ReviewItemAggregate corridor = service.ingestClue(newClue(
                "alert-case-001",
                firstTime,
                "corridor.jpg",
                "corridor.mp4"
        ));
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-case-002",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                firstTime.plusSeconds(40),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                15,
                "gate.jpg",
                "gate.mp4",
                null
        ));

        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "跨摄像头走廊复盘",
                corridor.id(),
                List.of(corridor.id())
        ));
        ReviewCaseView updated = service.addToReviewCase(reviewCase.id(), gate.id());
        List<ReviewCaseTimelineItem> timeline = service.getReviewCaseTimeline(updated.id());
        List<ReviewCaseTimelineItem> evidenceTimeline = timeline.stream()
                .filter(item -> List.of("snapshot", "record").contains(item.materialType()))
                .toList();

        assertEquals(List.of(corridor.id(), gate.id()), updated.reviewItemIds());
        assertEquals(List.of("camera-01", "camera-02"), updated.cameraIds());
        assertEquals(firstTime, updated.startTime());
        assertEquals(firstTime.plusSeconds(40), updated.endTime());
        assertEquals(List.of("corridor.jpg", "corridor.mp4", "gate.jpg", "gate.mp4"),
                evidenceTimeline.stream().map(ReviewCaseTimelineItem::materialUri).toList());
        assertEquals(List.of("camera-01", "camera-01", "camera-02", "camera-02"),
                evidenceTimeline.stream().map(ReviewCaseTimelineItem::cameraId).toList());
    }

    @Test
    void reviewCaseLifecycleKeepsOwnerDedupCloseAndAuditTrail() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 3, 17, 0);
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-case-life-001",
                firstTime,
                "case-life-1.jpg",
                "case-life-1.mp4"
        ));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-case-life-002",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                firstTime.plusSeconds(45),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                15,
                "case-life-2.jpg",
                "case-life-2.mp4",
                null
        ));
        ReviewItemAggregate late = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-case-life-003",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                firstTime.plusMinutes(20),
                "device-03",
                "camera-03",
                "zone-c",
                "person",
                15,
                "case-life-3.jpg",
                "case-life-3.mp4",
                null
        ));

        ReviewCaseView created = service.createReviewCase(new ReviewCaseCommand(
                "case lifecycle",
                first.id(),
                List.of(first.id(), second.id(), first.id()),
                9101L,
                "initial owner"
        ));
        ReviewCaseView deduped = service.addToReviewCase(created.id(), second.id());
        ReviewCaseView reassigned = service.assignReviewCaseOwner(new ReviewCaseOwnerCommand(
                created.id(),
                9102L,
                9103L,
                "handoff to duty lead"
        ));
        ReviewCaseView closed = service.closeReviewCase(new ReviewCaseOperationCommand(
                created.id(),
                9102L,
                "case resolved"
        ));

        IllegalStateException rejectedAdd = assertThrows(IllegalStateException.class,
                () -> service.addToReviewCase(created.id(), late.id()));
        List<ReviewCaseTimelineItem> auditTimeline = service.getReviewCaseTimeline(created.id()).stream()
                .filter(item -> "case_audit".equals(item.materialType()))
                .toList();

        assertEquals(List.of(first.id(), second.id()), created.reviewItemIds());
        assertEquals(List.of(first.id(), second.id()), deduped.reviewItemIds());
        assertEquals(9101L, created.ownerUserId());
        assertEquals("initial owner", created.notes());
        assertEquals(9102L, reassigned.ownerUserId());
        assertEquals("closed", closed.status());
        assertTrue(rejectedAdd.getMessage().contains("closed"));
        assertTrue(auditTimeline.stream().anyMatch(item -> "assign_owner".equals(item.materialUri())
                && item.actionNote().contains("ownerUserId=9102")
                && item.actionNote().contains("handoff to duty lead")));
        assertTrue(auditTimeline.stream().anyMatch(item -> "close_case".equals(item.materialUri())
                && item.actionNote().contains("case resolved")));
    }

    @Test
    void reviewCaseMergeAndSplitMoveCluesWithAuditTrail() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 3, 18, 0);
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-case-merge-001",
                baseTime,
                "case-merge-1.jpg",
                "case-merge-1.mp4"
        ));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-case-merge-002",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime.plusSeconds(30),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                15,
                "case-merge-2.jpg",
                "case-merge-2.mp4",
                null
        ));
        ReviewItemAggregate third = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-case-merge-003",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime.plusSeconds(90),
                "device-03",
                "camera-03",
                "zone-c",
                "person",
                15,
                "case-merge-3.jpg",
                "case-merge-3.mp4",
                null
        ));

        ReviewCaseView target = service.createReviewCase(new ReviewCaseCommand(
                "target case",
                first.id(),
                List.of(first.id(), second.id()),
                9201L,
                "target owner"
        ));
        ReviewCaseView source = service.createReviewCase(new ReviewCaseCommand(
                "source case",
                second.id(),
                List.of(second.id(), third.id()),
                9202L,
                "source owner"
        ));

        ReviewCaseMergeResult merged = service.mergeReviewCases(new ReviewCaseMergeCommand(
                target.id(),
                source.id(),
                9203L,
                "same person moved from gate to corridor"
        ));
        ReviewCaseSplitResult split = service.splitReviewCase(new ReviewCaseSplitCommand(
                target.id(),
                List.of(third.id()),
                "camera-03 follow-up",
                9204L,
                9205L,
                "separate camera-03 lead"
        ));
        List<ReviewCaseTimelineItem> targetAudit = service.getReviewCaseTimeline(target.id()).stream()
                .filter(item -> "case_audit".equals(item.materialType()))
                .toList();
        List<ReviewCaseTimelineItem> sourceAudit = service.getReviewCaseTimeline(source.id()).stream()
                .filter(item -> "case_audit".equals(item.materialType()))
                .toList();
        List<ReviewCaseTimelineItem> splitAudit = service.getReviewCaseTimeline(split.newCase().id()).stream()
                .filter(item -> "case_audit".equals(item.materialType()))
                .toList();

        assertEquals(List.of(first.id(), second.id(), third.id()), merged.targetCase().reviewItemIds());
        assertEquals("merged", merged.sourceCase().status());
        assertEquals(List.of(), merged.sourceCase().reviewItemIds());
        assertEquals(List.of(first.id(), second.id()), split.sourceCase().reviewItemIds());
        assertEquals(List.of(third.id()), split.newCase().reviewItemIds());
        assertEquals(9204L, split.newCase().ownerUserId());
        assertTrue(targetAudit.stream().anyMatch(item -> "merge_case".equals(item.materialUri())
                && item.actionNote().contains("sourceReviewCaseId=" + source.id())
                && item.actionNote().contains("same person moved")));
        assertTrue(sourceAudit.stream().anyMatch(item -> "merge_case".equals(item.materialUri())
                && item.actionNote().contains("targetReviewCaseId=" + target.id())));
        assertTrue(targetAudit.stream().anyMatch(item -> "split_case".equals(item.materialUri())
                && item.actionNote().contains("newReviewCaseId=" + split.newCase().id())));
        assertTrue(splitAudit.stream().anyMatch(item -> "split_case".equals(item.materialUri())
                && item.actionNote().contains("sourceReviewCaseId=" + target.id())));
    }
    @Test
    void reviewDataIsVersionedAndKeepsObjectsVerifiedThumbAudioAndMotionMetadata() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 13, 0);
        LocalDateTime thumbTime = alertTime.plusSeconds(2);

        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-structured",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                "snap-structured.jpg",
                "record-structured.mp4",
                "payload-hash-structured",
                List.of("person", "helmet"),
                List.of("zone-a"),
                List.of("obj-001", "obj-002"),
                0.93D,
                List.of(0.10D, 0.20D, 0.30D, 0.40D),
                "corr-structured",
                List.of("obj-001"),
                thumbTime,
                List.of("shout"),
                Map.of("score", 83, "area", "doorway")
        ));

        assertEquals(1, item.reviewData().get("reviewDataVersion"));
        assertEquals("payload-hash-structured", item.reviewData().get("sourcePayloadHash"));
        assertEquals("corr-structured", item.reviewData().get("correlationId"));
        assertEquals(List.of("obj-001"), item.reviewData().get("verifiedObjects"));
        assertEquals(thumbTime.toString(), item.reviewData().get("thumbTime"));
        List<?> objects = (List<?>) item.reviewData().get("objects");
        assertEquals(2, objects.size());
        Map<?, ?> firstObject = (Map<?, ?>) objects.get(0);
        assertEquals("obj-001", firstObject.get("id"));
        assertEquals("person", firstObject.get("label"));
        assertEquals(0.93D, firstObject.get("confidence"));
        assertEquals(List.of(0.10D, 0.20D, 0.30D, 0.40D), firstObject.get("bbox"));
        Map<?, ?> audio = (Map<?, ?>) item.reviewData().get("audio");
        assertEquals(List.of("shout"), audio.get("labels"));
        Map<?, ?> motion = (Map<?, ?>) item.reviewData().get("motion");
        assertEquals(83, motion.get("score"));
        assertEquals("doorway", motion.get("area"));
    }

    @Test
    void reviewDetailStreamExpandsObjectsDetectionsAndSeekTimes() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 1, 8, 30);
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-detail-001",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "gate-zone",
                "person",
                18,
                "detail-snap.jpg",
                "detail-record.mp4",
                "detail-hash",
                List.of("person", "helmet"),
                List.of("gate-zone"),
                List.of("obj-person", "obj-helmet"),
                0.93D,
                List.of(0.11D, 0.22D, 0.33D, 0.44D),
                "corr-detail",
                List.of("obj-person"),
                alertTime.minusSeconds(2),
                List.of("speech"),
                Map.of("path", List.of(
                        Map.of(
                                "objectId", "obj-person",
                                "label", "person",
                                "event", "entered_zone",
                                "timestamp", alertTime.plusSeconds(4).toString(),
                                "x", 0.42D,
                                "y", 0.58D
                        )
                ))
        ));

        List<ReviewDetailStreamItem> stream = service.getReviewDetailStream(item.id());

        assertTrue(stream.stream().anyMatch(row -> "detected".equals(row.lifecycleEvent())
                && "obj-person".equals(row.objectId())
                && "person".equals(row.label())
                && alertTime.equals(row.seekTime())
                && List.of(0.11D, 0.22D, 0.33D, 0.44D).equals(row.bbox())));
        assertTrue(stream.stream().anyMatch(row -> "entered_zone".equals(row.lifecycleEvent())
                && "obj-person".equals(row.objectId())
                && alertTime.plusSeconds(4).equals(row.seekTime())
                && row.path().stream().anyMatch(point -> Objects.equals(0.42D, point.get("x")))));
        assertTrue(stream.stream().anyMatch(row -> "record".equals(row.lifecycleEvent())
                && "detail-record.mp4".equals(row.materialUri())
                && alertTime.equals(row.seekTime())));
    }

    @Test
    void reviewDetailRecordUsesItsPersistedEvidenceStartAndOffset() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 10, 0, 20);
        LocalDateTime recordStartTime = alertTime.minusSeconds(20);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-detail-record-start", alertTime, "detail-start.jpg", null));
        itemStore.appendEvidence(item.id(), List.of(new ReviewEvidenceItem(
                item.id(),
                "alert-detail-record-start",
                "record",
                "detail-prerecord.mp4",
                alertTime,
                recordStartTime
        )));

        ReviewDetailStreamItem record = service.getReviewDetailStream(item.id()).stream()
                .filter(row -> "detail-prerecord.mp4".equals(row.materialUri()))
                .findFirst()
                .orElseThrow();

        assertEquals(recordStartTime, record.recordStartTime());
        assertEquals(20, record.playbackOffsetSeconds());
    }

    @Test
    void reviewDetailRecordKeepsPlaybackContextNullWhenEvidenceStartIsMissing() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 10, 0, 20);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-detail-record-unknown", alertTime, "detail-unknown.jpg", null));
        itemStore.appendEvidence(item.id(), List.of(new ReviewEvidenceItem(
                item.id(),
                "alert-detail-record-unknown",
                "record",
                "detail-unknown-start.mp4",
                alertTime
        )));

        assertNotNull(service.getReviewSegment(item.id()).startTime());
        ReviewDetailStreamItem record = service.getReviewDetailStream(item.id()).stream()
                .filter(row -> "detail-unknown-start.mp4".equals(row.materialUri()))
                .findFirst()
                .orElseThrow();

        assertNull(record.recordStartTime());
        assertNull(record.playbackOffsetSeconds());
    }

    @Test
    void reviewLifecycleStateMachineKeepsActiveWindowObjectEventsAndSeekTimes() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 9, 30);
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-lifecycle",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "gate-zone",
                "person",
                20,
                "life-start.jpg",
                "life-start.mp4",
                "life-hash",
                List.of("person"),
                List.of("gate-zone"),
                List.of("obj-life"),
                0.94D,
                List.of(0.12D, 0.22D, 0.32D, 0.42D),
                "corr-life"
        ));

        ReviewItemAggregate active = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "active",
                alertTime.plusSeconds(40),
                List.of("obj-life"),
                List.of("person"),
                List.of("gate-zone", "inner-yard"),
                List.of(0.13D, 0.23D, 0.33D, 0.43D),
                Map.of("event", "entered_zone", "x", 0.42D, "y", 0.58D),
                "life-active.mp4"
        ));
        ReviewItemAggregate ended = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "ended",
                alertTime.plusSeconds(140),
                List.of("obj-life"),
                List.of("person"),
                List.of("inner-yard"),
                List.of(0.14D, 0.24D, 0.34D, 0.44D),
                Map.of("event", "left_zone"),
                null
        ));

        Map<?, ?> lifecycle = (Map<?, ?>) ended.reviewData().get("lifecycle");
        assertEquals("ended", lifecycle.get("state"));
        assertEquals(alertTime.toString(), lifecycle.get("startedAt"));
        assertEquals(alertTime.plusSeconds(140).toString(), lifecycle.get("endedAt"));
        assertEquals(List.of("obj-life"), lifecycle.get("activeObjectIds"));
        assertEquals(300, lifecycle.get("cutoffWindowSeconds"));
        assertEquals(alertTime.plusSeconds(140), ended.lastAlertTime());
        assertEquals("found", active.recordEvidenceStatus());
        assertTrue(service.getTimeline(item.id()).stream()
                .anyMatch(evidence -> "record".equals(evidence.materialType())
                        && "life-active.mp4".equals(evidence.materialUri())));
        List<ReviewDetailStreamItem> stream = service.getReviewDetailStream(item.id());
        assertTrue(stream.stream().anyMatch(row -> "active".equals(row.lifecycleEvent())
                && "obj-life".equals(row.objectId())
                && alertTime.plusSeconds(40).equals(row.seekTime())
                && List.of(0.13D, 0.23D, 0.33D, 0.43D).equals(row.bbox())));
        assertTrue(stream.stream().anyMatch(row -> "ended".equals(row.lifecycleEvent())
                && alertTime.plusSeconds(140).equals(row.seekTime())));
    }

    @Test
    void recordStorageSyncPersistsCoverageSummaryAndBackfilledRecordEvidence() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 10, 0);
        ReviewItemAggregate item = service.ingestClue(newClue("alert-storage-sync", alertTime, "storage-sync.jpg", null));

        ReviewRecordStorageSyncResult result = service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9001L,
                List.of(
                        new RecordCoverageSegment("available", alertTime.minusSeconds(300), alertTime.minusSeconds(120),
                                0, "storage-a.mp4", 0, Map.of("source", "video-index")),
                        new RecordCoverageSegment("missing", alertTime.minusSeconds(120), alertTime.plusSeconds(60),
                                0, null, 0, Map.of("source", "video-index")),
                        new RecordCoverageSegment("motion", alertTime.plusSeconds(60), alertTime.plusSeconds(300),
                                55, "storage-b.mp4", 2, Map.of("source", "video-index"))
                )
        ));
        ReviewItemAggregate synced = service.listWorkbench(null).get(0);

        assertEquals("partial", result.syncStatus());
        assertEquals(2, result.availableSegmentCount());
        assertEquals(1, result.missingSegmentCount());
        assertEquals(1, result.motionSegmentCount());
        assertEquals("missing", synced.recordEvidenceStatus());
        Map<?, ?> storage = (Map<?, ?>) synced.reviewData().get("recordStorage");
        assertEquals("partial", storage.get("syncStatus"));
        assertEquals(420, storage.get("availableSeconds"));
        assertEquals(180, storage.get("missingSeconds"));
        assertEquals(240, storage.get("motionSeconds"));
        assertTrue(service.getTimeline(item.id()).stream()
                .anyMatch(evidence -> "record".equals(evidence.materialType())
                        && "storage-b.mp4".equals(evidence.materialUri())));
    }

    @Test
    void runtimePatrolSurfacesRecordStorageDriftReasonsFromStorageSync() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 10, 30);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-storage-drift-runtime",
                alertTime,
                "storage-drift.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9003L,
                List.of(
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(5), alertTime.minusMinutes(3),
                                0, null, 0, Map.of("gapReason", "file_missing", "source", "record_file")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(3), alertTime.minusMinutes(1),
                                0, null, 0, Map.of("gapReason", "disk_full", "source", "recording_storage")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(1), alertTime.plusMinutes(1),
                                0, null, 0, Map.of("gapReason", "cache_flush_failed", "source", "record_cache"))
                )
        ));

        ReviewRuntimeHealthReport health = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9006L
        ));
        ReviewRuntimePatrolResult patrol = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9007L,
                false,
                1,
                true
        ));

        assertEquals(1, health.recordGapReasons().get("file_missing"));
        assertEquals(1, health.recordGapReasons().get("disk_full"));
        assertEquals(1, health.recordGapReasons().get("cache_flush_failed"));
        assertTrue(health.alerts().contains("record_storage_drift"));
        assertTrue(health.alerts().contains("record_storage_drift:file_missing"));
        assertTrue(health.alerts().contains("record_storage_drift:disk_full"));
        assertTrue(health.alerts().contains("record_storage_drift:cache_flush_failed"));
        assertTrue(patrol.notifications().contains("review_runtime_alert:record_storage_drift:file_missing"));
        assertTrue(patrol.recommendedActions().contains("inspect_record_storage_drift"));
        assertTrue(patrol.recommendedActions().contains("review_missing_record_file"));
        assertTrue(patrol.recommendedActions().contains("free_or_expand_recording_disk"));
        assertTrue(patrol.recommendedActions().contains("inspect_record_cache_flush"));
        RuntimeOutboxEntry fileMissingOutbox = itemStore.runtimeOutbox().stream()
                .filter(entry -> "record_storage_drift:file_missing".equals(entry.alert()))
                .findFirst()
                .orElseThrow();
        assertEquals("review_missing_record_file", fileMissingOutbox.action());
        assertTrue(fileMissingOutbox.recommendedActions().contains("inspect_record_storage_drift"));
        assertTrue(fileMissingOutbox.recommendedActions().contains("review_missing_record_file"));
        assertEquals(patrol.metadata().get("historyRunId"), fileMissingOutbox.runId());
        assertEquals("pending", fileMissingOutbox.status());
    }

    @Test
    void scheduledRuntimePatrolConsumesLiveVideoStorageDriftWithoutListProjectionSideEffects() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewServiceImpl service = (SupervisionAlertReviewServiceImpl) newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        service.ingestClue(newClue(
                "alert-live-video-drift-runtime",
                LocalDateTime.of(2026, 7, 2, 11, 30),
                "live-drift.jpg",
                "live-drift.flv"
        ));
        service.setRecordStorageDriftResolver(request -> new RecordStorageDriftReport(
                request.deviceId(),
                request.cameraId(),
                9L,
                false,
                12,
                3,
                Map.of("file_missing", 2, "cache_flush_failed", 1),
                List.of("file_missing", "retention_expired", "disk_full", "cache_flush_failed"),
                "file_missing",
                LocalDateTime.of(2026, 7, 2, 11, 31)
        ));

        ReviewRuntimePatrolResult patrol = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9008L,
                false,
                1,
                true
        ));

        assertEquals(2, patrol.healthReport().recordGapReasons().get("file_missing"));
        assertEquals(1, patrol.healthReport().recordGapReasons().get("cache_flush_failed"));
        assertTrue(patrol.alerts().contains("record_storage_drift:file_missing"));
        assertTrue(patrol.alerts().contains("record_storage_drift:cache_flush_failed"));
        assertTrue(patrol.notifications().contains("review_runtime_alert:record_storage_drift:file_missing"));
        assertTrue(itemStore.runtimeOutbox().stream()
                .anyMatch(entry -> "record_storage_drift:cache_flush_failed".equals(entry.alert())));
    }

    @Test
    void runtimeHealthExposesStandardRecordGapReasonCatalogAndNormalizesAliases() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 11, 30);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-standard-record-gap-reasons",
                alertTime,
                "standard-gap-reasons.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9005L,
                List.of(
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(8), alertTime.minusMinutes(7),
                                0, null, 0, Map.of("gapReason", "file-expired")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(7), alertTime.minusMinutes(6),
                                0, null, 0, Map.of("gapReason", "VIDEO URL NOT CONFIGURED")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(6), alertTime.minusMinutes(5),
                                0, null, 0, Map.of("gapReason", "file_expired")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(5), alertTime.minusMinutes(4),
                                0, null, 0, Map.of("gapReason", "permission_denied")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(4), alertTime.minusMinutes(3),
                                0, null, 0, Map.of("gapReason", "record_space_not_found")),
                        new RecordCoverageSegment("missing", alertTime.minusMinutes(3), alertTime.minusMinutes(2),
                                0, null, 0, Map.of("gapReason", "probe_failed"))
                )
        ));

        ReviewRuntimeHealthReport health = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9006L
        ));

        assertEquals(1, health.recordGapReasons().get("retention_expired"));
        assertFalse(health.recordGapReasons().containsKey("file_expired"));
        assertFalse(health.recordGapReasons().containsKey("file-expired"));
        assertFalse(health.recordGapReasons().containsKey("record_not_found"));
        assertEquals(1, health.recordGapReasons().get("video_url_not_configured"));
        assertEquals(1, health.recordGapReasons().get("permission_denied"));
        assertEquals(1, health.recordGapReasons().get("record_space_not_found"));
        assertEquals(1, health.recordGapReasons().get("probe_failed"));
        Map<String, Map<String, Object>> catalog = health.recordGapReasonCatalog();
        assertTrue(catalog.keySet().containsAll(List.of(
                "video_url_not_configured",
                "record_space_not_found",
                "file_missing",
                "probe_failed",
                "permission_denied",
                "retention_expired"
        )));
        assertEquals("\u8FC7\u671F", catalog.get("retention_expired").get("labelZh"));
        assertEquals(List.of("file_expired"), catalog.get("retention_expired").get("aliases"));
        assertEquals("permission", catalog.get("permission_denied").get("category"));
        assertEquals(false, catalog.get("permission_denied").get("retryable"));
    }

    @Test
    void runtimePatrolAndOutboxJobsCloseScheduledAlertDispatchLoop() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewServiceImpl service = (SupervisionAlertReviewServiceImpl) newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        service.setRuntimeOutboxPublisher(message -> ReviewRuntimeOutboxDeliveryResult.delivered());
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 11, 20);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-runtime-job-loop",
                alertTime,
                "runtime-job.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9011L,
                List.of(new RecordCoverageSegment("missing", alertTime.minusMinutes(2), alertTime.plusMinutes(2),
                        0, null, 0, Map.of("gapReason", "file_missing")))
        ));

        String patrolSummary = new SupervisionAlertReviewRuntimePatrolJob(service).execute("");

        assertTrue(patrolSummary.contains("scheduled=true"));
        assertFalse(itemStore.runtimeOutbox().isEmpty());
        assertTrue(itemStore.runtimeOutbox().stream()
                .allMatch(entry -> "pending".equals(entry.status())));
        assertTrue(itemStore.runtimeOutbox().stream()
                .allMatch(entry -> Boolean.TRUE.equals(entry.metadata().get("scheduled"))));

        String outboxSummary = new SupervisionAlertReviewRuntimeOutboxJob(service).execute("10");

        assertTrue(outboxSummary.contains("published="));
        assertTrue(itemStore.runtimeOutbox().stream()
                .allMatch(entry -> "published".equals(entry.status())));
    }

    @Test
    void runtimeOutboxPublisherFailureMarksMessageFailedForRetry() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewServiceImpl service = new SupervisionAlertReviewServiceImpl(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                VideoEvidenceExportProvider.unavailable()
        );
        service.setRuntimeOutboxPublisher(
                message -> new ReviewRuntimeOutboxDeliveryResult(false, "notification_sink_unavailable")
        );
        itemStore.enqueueRuntimePatrolAlerts(
                "run-runtime-publish-fail",
                List.of("record_storage_drift:file_missing"),
                List.of("inspect runtime outbox sink"),
                9013L,
                LocalDateTime.of(2026, 7, 2, 12, 0),
                Map.of()
        );

        ReviewRuntimeOutboxPublishResult result = service.publishRuntimeOutbox(
                new ReviewRuntimeOutboxPublishCommand(10, 9013L)
        );

        assertEquals(1, result.scannedCount());
        assertEquals(0, result.publishedCount());
        assertEquals(1, result.failedCount());
        assertEquals(List.of("record_storage_drift:file_missing"), result.failedAlerts());
        RuntimeOutboxEntry outbox = itemStore.runtimeOutbox().get(0);
        assertEquals("failed", outbox.status());
        assertEquals(1, outbox.retryCount());
        assertEquals("notification_sink_unavailable", outbox.lastError());
    }

    @Test
    void runtimeOutboxPublishingClaimsPendingMessagesBeforeDelivery() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        AtomicInteger publishCalls = new AtomicInteger();
        AtomicReference<ReviewRuntimeOutboxPublishResult> nestedResult = new AtomicReference<>();
        AtomicReference<SupervisionAlertReviewServiceImpl> serviceRef = new AtomicReference<>();
        SupervisionAlertReviewServiceImpl service = new SupervisionAlertReviewServiceImpl(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                VideoEvidenceExportProvider.unavailable()
        );
        serviceRef.set(service);
        service.setRuntimeOutboxPublisher(message -> {
            if (publishCalls.incrementAndGet() == 1) {
                nestedResult.set(serviceRef.get().publishRuntimeOutbox(
                        new ReviewRuntimeOutboxPublishCommand(10, 9015L)
                ));
            }
            return ReviewRuntimeOutboxDeliveryResult.delivered();
        });
        itemStore.enqueueRuntimePatrolAlerts(
                "run-runtime-publish-claim",
                List.of("record_storage_drift:file_missing"),
                List.of("inspect runtime outbox claim"),
                9014L,
                LocalDateTime.of(2026, 7, 2, 12, 30),
                Map.of()
        );

        ReviewRuntimeOutboxPublishResult result = service.publishRuntimeOutbox(
                new ReviewRuntimeOutboxPublishCommand(10, 9014L)
        );

        assertEquals(1, result.scannedCount());
        assertEquals(1, result.publishedCount());
        assertEquals(0, result.failedCount());
        assertEquals(0, nestedResult.get().scannedCount());
        assertEquals(1, publishCalls.get());
        assertEquals("published", itemStore.runtimeOutbox().get(0).status());
    }

    @Test
    void runtimeOutboxPublishingReclaimsOnlyStaleProcessingMessages() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        AtomicInteger publishCalls = new AtomicInteger();
        SupervisionAlertReviewServiceImpl service = new SupervisionAlertReviewServiceImpl(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                VideoEvidenceExportProvider.unavailable()
        );
        service.setRuntimeOutboxPublisher(message -> {
            publishCalls.incrementAndGet();
            return ReviewRuntimeOutboxDeliveryResult.delivered();
        });
        itemStore.enqueueRuntimePatrolAlerts(
                "run-runtime-publish-stale-claim",
                List.of("record_storage_drift:file_missing", "record_storage_drift:probe_failed"),
                List.of("inspect runtime outbox claim"),
                9016L,
                LocalDateTime.of(2026, 7, 2, 12, 45),
                Map.of()
        );
        itemStore.claimPendingRuntimeOutbox(
                1,
                "stale-claim",
                9016L,
                LocalDateTime.now().minusMinutes(15)
        );
        itemStore.claimPendingRuntimeOutbox(
                1,
                "fresh-claim",
                9017L,
                LocalDateTime.now()
        );

        ReviewRuntimeOutboxPublishResult result = service.publishRuntimeOutbox(
                new ReviewRuntimeOutboxPublishCommand(10, 9018L)
        );

        assertEquals(1, result.scannedCount());
        assertEquals(1, result.publishedCount());
        assertEquals(0, result.failedCount());
        assertEquals(1, publishCalls.get());
        assertEquals("published", itemStore.runtimeOutbox().get(0).status());
        assertEquals("processing", itemStore.runtimeOutbox().get(1).status());
    }

    @Test
    void runtimePatrolRecoversExpiredClusterLockAndReportsPreviousOwner() {
        RecoverableRuntimeLockStore itemStore = new RecoverableRuntimeLockStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        String lockName = "alert-review-runtime-patrol";
        LocalDateTime futureUntil = LocalDateTime.now().plusMinutes(5);
        itemStore.seedRuntimeLock(lockName, 42L, futureUntil);

        ReviewRuntimePatrolResult locked = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9012L,
                true,
                1,
                true
        ));

        assertEquals("locked", locked.status());
        assertFalse(locked.lockAcquired());
        assertEquals("active_lock", locked.metadata().get("lockReason"));
        assertEquals(42L, locked.metadata().get("lockOwnerUserId"));
        assertEquals(futureUntil, locked.metadata().get("lockedUntil"));

        LocalDateTime staleUntil = LocalDateTime.now().minusMinutes(5);
        itemStore.seedRuntimeLock(lockName, 42L, staleUntil);
        ReviewRuntimePatrolResult recovered = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9013L,
                true,
                1,
                true
        ));

        assertTrue(recovered.lockAcquired());
        assertEquals("healthy", recovered.status());
        assertEquals(true, recovered.metadata().get("lockRecovered"));
        assertEquals("stale_lock_recovered", recovered.metadata().get("lockReason"));
        assertEquals(42L, recovered.metadata().get("previousLockOwnerUserId"));
        assertEquals(staleUntil, recovered.metadata().get("previousLockedUntil"));
        assertEquals(9013L, itemStore.currentLockOwner(lockName));
    }

    @Test
    void evidenceManifestVerificationRequiresHashAndSignature() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7600L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                noEventProjectionStore()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-manifest-verify",
                LocalDateTime.of(2026, 7, 2, 10, 30),
                "manifest.jpg",
                "manifest.mp4"
        ));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9002L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "manifest verification",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9003L,
                "manifest",
                "regulator package",
                9005L,
                "approved"
        ));

        ReviewManifestVerification verification = service.verifyEvidenceExportManifest(job.jobNo());

        assertTrue(verification.valid());
        assertEquals(job.jobNo(), verification.jobNo());
        assertEquals(job.exportPackage().manifest().get("manifestHash"), verification.actualManifestHash());
        assertEquals(verification.expectedManifestHash(), verification.actualManifestHash());
        assertEquals(job.exportPackage().manifest().get("packageChecksum"), verification.packageChecksum());
        assertEquals(List.of(), verification.violations());
        assertTrue(job.exportPackage().manifest().get("signature") instanceof Map<?, ?>);
    }

    @Test
    void mediaAccessAuditRecordsGrantedAndDeniedCameraScopedDecisions() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 0);
        ReviewItemAggregate corridor = service.ingestClue(newClue("alert-media-a", baseTime, "media-a.jpg", "media-a.mp4"));
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-media-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime.plusMinutes(10),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "media-b.jpg",
                "media-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "media access case",
                corridor.id(),
                List.of(corridor.id(), gate.id())
        ));

        ReviewMediaAccessAuditEntry granted = service.auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCase.id(),
                corridor.id(),
                9001L,
                "camera-01",
                "media-a.mp4",
                "playback",
                List.of("camera-01"),
                "review playback"
        ));
        ReviewMediaAccessAuditEntry denied = service.auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCase.id(),
                gate.id(),
                9001L,
                "camera-02",
                "media-b.mp4",
                "download",
                List.of("camera-01"),
                "not assigned camera"
        ));

        assertEquals("granted", granted.decision());
        assertEquals("denied", denied.decision());
        assertEquals(List.of("camera_not_allowed"), denied.deniedReasons());
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void evidenceAuditTrailIncludesMediaAccessReadsWithOperatorAndReverseLookup() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7600L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.DISPATCHED.getCode(),
                        false
                )
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 6, 12, 10);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-media-trail",
                baseTime,
                "media-trail.jpg",
                "media-trail.mp4"
        ));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9100L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "media access evidence trail",
                item.id(),
                List.of(item.id())
        ));

        service.auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCase.id(),
                item.id(),
                9101L,
                "camera-01",
                "media-trail.mp4",
                "playback",
                List.of("camera-01"),
                "operator reviewed playable evidence"
        ));
        service.auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCase.id(),
                item.id(),
                9102L,
                "camera-01",
                "media-trail.mp4",
                "download",
                List.of("camera-02"),
                "outside assigned scope"
        ));

        List<ReviewEvidenceAuditEntry> auditTrail = service.getEvidenceAuditTrail(reviewCase.id());
        ReviewEvidenceAuditEntry granted = auditTrail.stream()
                .filter(entry -> "media_access_granted".equals(entry.actionType()))
                .findFirst()
                .orElseThrow();
        ReviewEvidenceAuditEntry denied = auditTrail.stream()
                .filter(entry -> "media_access_denied".equals(entry.actionType()))
                .findFirst()
                .orElseThrow();

        assertEquals(9101L, granted.operatorUserId());
        assertEquals(List.of("media-trail.mp4"), granted.evidenceUris());
        assertEquals(List.of(7600L), granted.boundEventIds());
        assertEquals(reviewCase.id(), granted.metadata().get("reviewCaseId"));
        assertEquals(List.of(item.id()), granted.metadata().get("reviewItemIds"));
        assertEquals(List.of(7600L), granted.metadata().get("eventIds"));
        assertEquals("granted", granted.metadata().get("decision"));
        assertEquals("playback", granted.metadata().get("mediaAction"));
        assertEquals("camera-01", granted.metadata().get("cameraId"));
        assertEquals("media-trail.mp4", granted.metadata().get("materialUri"));

        assertEquals(9102L, denied.operatorUserId());
        assertEquals(List.of("media-trail.mp4"), denied.evidenceUris());
        assertEquals(List.of(7600L), denied.boundEventIds());
        assertEquals("denied", denied.metadata().get("decision"));
        assertEquals("download", denied.metadata().get("mediaAction"));
        assertEquals(List.of("camera_not_allowed"), denied.metadata().get("deniedReasons"));
        assertEquals("outside assigned scope", denied.actionNote());
    }

    @Test
    void preCaseMediaAccessAuditRecordsAllowDenyAndCanBeListedByReviewItem() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 7, 9, 30);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-pre-case-media",
                baseTime,
                "pre-case.jpg",
                "pre-case.mp4"
        ));

        ReviewMediaAccessAuditEntry granted = service.auditMediaAccess(new ReviewMediaAccessCommand(
                null,
                item.id(),
                9201L,
                "camera-01",
                "pre-case.mp4",
                "playback",
                List.of("camera-01"),
                "operator preview before case"
        ));
        ReviewMediaAccessAuditEntry denied = service.auditMediaAccess(new ReviewMediaAccessCommand(
                null,
                item.id(),
                9202L,
                "camera-01",
                "pre-case.mp4",
                "playback",
                List.of("camera-02"),
                "outside camera scope before case"
        ));

        assertNull(granted.reviewCaseId());
        assertEquals("granted", granted.decision());
        assertEquals("denied", denied.decision());
        assertEquals(List.of("camera_not_allowed"), denied.deniedReasons());

        List<ReviewEvidenceAuditEntry> auditTrail = service.getReviewItemEvidenceAuditTrail(item.id());
        assertEquals(
                List.of("media_access_granted", "media_access_denied"),
                auditTrail.stream().map(ReviewEvidenceAuditEntry::actionType).toList()
        );
        assertTrue(auditTrail.stream().allMatch(entry -> entry.metadata().get("reviewCaseId") == null));
        assertTrue(auditTrail.stream().allMatch(entry -> entry.metadata().get("reviewItemIds").equals(List.of(item.id()))));
        assertTrue(auditTrail.stream().allMatch(entry -> entry.evidenceUris().equals(List.of("pre-case.mp4"))));
    }

    @Test
    void preCaseMediaReadEndpointsAllowAndAuditWithoutAttachingCoverageEvidence() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 7, 9, 45);
        String coverageUri = "https://eye.yfeiai.com/records/pre-case-coverage.mp4";
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "available",
                        request.beginTime(),
                        request.endTime(),
                        0,
                        coverageUri,
                        0,
                        Map.of("source", "video-index")
                ))
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-pre-case-media-read",
                baseTime,
                "pre-case-read.jpg",
                "pre-case-read.mp4"
        ));

        List<ReviewEvidenceItem> timeline = service.getTimeline(
                item.id(), null, 9211L, List.of("camera-01"));
        List<ReviewDetailStreamItem> detail = service.getReviewDetailStream(
                item.id(), null, 9211L, List.of("camera-01"));
        List<RecordCoverageSegment> coverage = service.getRecordCoverage(
                item.id(), null, 9211L, List.of("camera-01"));

        assertEquals(List.of("snapshot", "record"),
                timeline.stream().map(ReviewEvidenceItem::materialType).toList());
        assertTrue(detail.stream().anyMatch(row -> "pre-case-read.mp4".equals(row.materialUri())));
        assertTrue(coverage.stream().anyMatch(segment -> coverageUri.equals(segment.recordUri())));
        assertFalse(service.getTimeline(item.id()).stream()
                .anyMatch(evidence -> coverageUri.equals(evidence.materialUri())),
                "pre-case coverage must not be attached to a case timeline");

        List<ReviewEvidenceAuditEntry> auditTrail = service.getReviewItemEvidenceAuditTrail(item.id());
        assertTrue(auditTrail.stream().allMatch(entry -> entry.metadata().get("reviewCaseId") == null));
        assertTrue(auditTrail.stream().allMatch(entry -> "media_access_granted".equals(entry.actionType())));
        assertTrue(auditTrail.stream().anyMatch(entry -> "playback".equals(entry.metadata().get("mediaAction"))));
        assertTrue(auditTrail.stream().anyMatch(entry -> "snapshot".equals(entry.metadata().get("mediaAction"))));
        assertTrue(auditTrail.stream().anyMatch(entry -> "coverage".equals(entry.metadata().get("mediaAction"))
                && entry.evidenceUris().equals(List.of(coverageUri))));
    }

    @Test
    void preCaseMediaReadEndpointsDenyUnauthorizedCameraAndAuditEachAttempt() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 7, 9, 50);
        String coverageUri = "https://eye.yfeiai.com/records/pre-case-denied-coverage.mp4";
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "available",
                        request.beginTime(),
                        request.endTime(),
                        0,
                        coverageUri,
                        0,
                        Map.of("source", "video-index")
                ))
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-pre-case-media-denied",
                baseTime,
                "pre-case-denied.jpg",
                "pre-case-denied.mp4"
        ));

        SecurityException timelineDenied = assertThrows(SecurityException.class,
                () -> service.getTimeline(item.id(), null, 9212L, List.of("camera-02")));
        SecurityException detailDenied = assertThrows(SecurityException.class,
                () -> service.getReviewDetailStream(item.id(), null, 9212L, List.of("camera-02")));
        SecurityException coverageDenied = assertThrows(SecurityException.class,
                () -> service.getRecordCoverage(item.id(), null, 9212L, List.of("camera-02")));

        assertAll(
                () -> assertTrue(timelineDenied.getMessage().contains("camera_not_allowed")),
                () -> assertTrue(detailDenied.getMessage().contains("camera_not_allowed")),
                () -> assertTrue(coverageDenied.getMessage().contains("camera_not_allowed"))
        );
        List<ReviewEvidenceAuditEntry> deniedAudits = service.getReviewItemEvidenceAuditTrail(item.id()).stream()
                .filter(entry -> "media_access_denied".equals(entry.actionType()))
                .toList();
        assertEquals(3, deniedAudits.size());
        assertTrue(deniedAudits.stream().allMatch(entry -> entry.metadata().get("reviewCaseId") == null));
        assertEquals(List.of("playback", "playback", "coverage"),
                deniedAudits.stream().map(entry -> String.valueOf(entry.metadata().get("mediaAction"))).toList());
        assertTrue(deniedAudits.stream().allMatch(entry ->
                ((List<?>) entry.metadata().get("deniedReasons")).contains("camera_not_allowed")));
    }

    @Test
    void detectionOnlyRecordUriIsDeniedAndAuditedAsPlaybackMedia() {
        DetectionOnlyReviewItemStore itemStore = new DetectionOnlyReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-detection-only-record",
                LocalDateTime.of(2026, 7, 7, 9, 55),
                null,
                "detection-only.mp4"
        ));

        assertEquals(List.of(), service.getTimeline(item.id()));
        ReviewDetailStreamItem detection = service.getReviewDetailStream(item.id()).stream()
                .filter(row -> "detected".equals(row.lifecycleEvent()))
                .findFirst()
                .orElseThrow();
        assertEquals("record", detection.materialType());
        assertEquals("detection-only.mp4", detection.materialUri());

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.getReviewDetailStream(item.id(), null, 9213L, List.of("camera-02")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        List<ReviewEvidenceAuditEntry> auditTrail = service.getReviewItemEvidenceAuditTrail(item.id());
        assertEquals(1, auditTrail.size());
        assertEquals("media_access_denied", auditTrail.get(0).actionType());
        assertEquals(List.of("detection-only.mp4"), auditTrail.get(0).evidenceUris());
        assertEquals("playback", auditTrail.get(0).metadata().get("mediaAction"));
        assertNull(auditTrail.get(0).metadata().get("reviewCaseId"));
    }

    @Test
    void playbackUrlPreparationEnforcesCameraScopeAndAuditsAllowDeny() {
        SupervisionAlertReviewServiceImpl service = (SupervisionAlertReviewServiceImpl) newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        service.setReviewPlaybackUrlSigner((rawUrl, cameraId) ->
                rawUrl + "?playback_format=mp4&camera=" + cameraId);
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 7, 10, 20);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-playback-url",
                baseTime,
                "playback-url.jpg",
                "playback-url.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "playback url case",
                item.id(),
                List.of(item.id())
        ));

        ReviewPlaybackAccess allowed = service.prepareReviewPlayback(new ReviewPlaybackCommand(
                reviewCase.id(),
                item.id(),
                9301L,
                "playback-url.mp4",
                List.of("camera-01"),
                "operator opened server playback url"
        ));
        ReviewPlaybackAccess denied = service.prepareReviewPlayback(new ReviewPlaybackCommand(
                reviewCase.id(),
                item.id(),
                9302L,
                "playback-url.mp4",
                List.of("camera-02"),
                "outside playback scope"
        ));

        assertEquals("granted", allowed.decision());
        assertEquals("playback-url.mp4?playback_format=mp4&camera=camera-01", allowed.playbackUrl());
        assertEquals(List.of(), allowed.deniedReasons());
        assertEquals("denied", denied.decision());
        assertNull(denied.playbackUrl());
        assertEquals(List.of("camera_not_allowed"), denied.deniedReasons());
        assertTrue(service.getEvidenceAuditTrail(reviewCase.id()).stream()
                .anyMatch(entry -> "media_access_granted".equals(entry.actionType())
                        && Objects.equals(9301L, entry.operatorUserId())
                        && entry.metadata().get("materialUri").equals("playback-url.mp4")));
        assertTrue(service.getEvidenceAuditTrail(reviewCase.id()).stream()
                .anyMatch(entry -> "media_access_denied".equals(entry.actionType())
                        && Objects.equals(9302L, entry.operatorUserId())
                        && entry.metadata().get("deniedReasons").equals(List.of("camera_not_allowed"))));
    }

    @Test
    void evidenceExportRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 10);
        ReviewItemAggregate corridor = service.ingestClue(newClue("alert-export-scope-a", baseTime, "scope-a.jpg", "scope-a.mp4"));
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-export-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime.plusMinutes(10),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "scope-b.jpg",
                "scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "export scope case",
                corridor.id(),
                List.of(corridor.id(), gate.id())
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.exportReviewEvidence(new ReviewEvidenceExportCommand(
                        reviewCase.id(),
                        List.of(gate.id()),
                        9002L,
                        "manifest",
                        "restricted camera export",
                        null,
                        null,
                        List.of("camera-01")
                )));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=export")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void evidenceDownloadRejectsUnauthorizedCameraMediaAndAuditsDenial() throws Exception {
        CapturingVideoEvidenceExportProvider videoExportProvider = singleCameraDownloadableProvider(
                "camera-02",
                "camera-02-protected-video".getBytes(StandardCharsets.UTF_8)
        );
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 20);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-download-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "download-scope-b.jpg",
                "download-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "download scope case",
                gate.id(),
                List.of(gate.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(gate.id()),
                9003L,
                "mp4",
                "prepare restricted download"
        ));
        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9003L));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.downloadEvidencePackage(new ReviewEvidenceDownloadCommand(
                        job.jobNo(), 9003L, List.of("camera-01"), "restricted download")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=download")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void itemTimelineRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 25);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-timeline-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "timeline-scope-b.jpg",
                "timeline-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "timeline scope case",
                gate.id(),
                List.of(gate.id())
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.getTimeline(gate.id(), reviewCase.id(), 9005L, List.of("camera-01")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=playback")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void detailStreamRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 28);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-detail-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "detail-scope-b.jpg",
                "detail-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "detail scope case",
                gate.id(),
                List.of(gate.id())
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.getReviewDetailStream(gate.id(), reviewCase.id(), 9006L, List.of("camera-01")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=playback")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void recordCoverageRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 32);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-coverage-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "coverage-scope-b.jpg",
                "coverage-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "coverage scope case",
                gate.id(),
                List.of(gate.id())
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.getRecordCoverage(gate.id(), reviewCase.id(), 9007L, List.of("camera-01")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=coverage")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void caseTimelineRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 36);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-case-timeline-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "case-timeline-scope-b.jpg",
                "case-timeline-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "case timeline scope case",
                gate.id(),
                List.of(gate.id())
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.getReviewCaseTimeline(reviewCase.id(), 9008L, List.of("camera-01")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=playback")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void mediaReadEndpointsUseConfiguredPlaybackAndSnapshotActions() {
        List<String> requestedActions = new ArrayList<>();
        ReviewCameraPermissionResolver failClosedResolver = request -> {
            requestedActions.add(request.actionType());
            return List.of("playback", "snapshot").contains(request.actionType())
                    ? List.of("camera-01")
                    : List.of();
        };
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                failClosedResolver
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-media-read-actions",
                LocalDateTime.of(2026, 7, 13, 15, 10),
                "media-read-actions.jpg",
                "media-read-actions.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "media read actions",
                item.id(),
                List.of(item.id())
        ));

        service.getTimeline(item.id(), reviewCase.id(), 1L, List.of("camera-01"));
        service.getReviewDetailStream(item.id(), reviewCase.id(), 1L, List.of("camera-01"));
        service.getReviewCaseTimeline(reviewCase.id(), 1L, List.of("camera-01"));

        assertTrue(requestedActions.contains("playback"));
        assertTrue(requestedActions.contains("snapshot"));
        assertFalse(requestedActions.contains("timeline"));
        assertFalse(requestedActions.contains("detail_stream"));
        assertFalse(requestedActions.contains("case_timeline"));
    }

    @Test
    void manifestVerificationRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 40);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-manifest-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "manifest-scope-b.jpg",
                "manifest-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "manifest scope case",
                gate.id(),
                List.of(gate.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(gate.id()),
                9009L,
                "manifest",
                "prepare manifest verify"
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.verifyEvidenceExportManifest(job.jobNo(), 9009L, List.of("camera-01")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=manifest_verify")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void evidencePackageVerificationRejectsUnauthorizedCameraMediaAndAuditsDenial() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 45);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-package-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "package-scope-b.jpg",
                "package-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "package scope case",
                gate.id(),
                List.of(gate.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(gate.id()),
                9010L,
                "manifest",
                "prepare package verify"
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.verifyEvidencePackage(new ReviewEvidenceVerificationCommand(
                        job.jobNo(),
                        9010L,
                        List.of("camera-01")
                )));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=manifest_verify")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void requestedCameraScopeCannotExpandServiceSideCameraPermission() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of("camera-01")
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 3, 9, 10);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-server-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "server-scope-b.jpg",
                "server-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "server scope case",
                gate.id(),
                List.of(gate.id())
        ));

        SecurityException denied = assertThrows(SecurityException.class,
                () -> service.getTimeline(gate.id(), reviewCase.id(), 9010L, List.of("camera-02")));

        assertTrue(denied.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=playback")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void mediaAccessAuditCannotExpandServiceSideCameraPermission() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of("camera-01")
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 3, 9, 20);
        ReviewItemAggregate gate = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-audit-server-scope-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                20,
                "audit-server-scope-b.jpg",
                "audit-server-scope-b.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "audit server scope case",
                gate.id(),
                List.of(gate.id())
        ));

        ReviewMediaAccessAuditEntry audit = service.auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCase.id(),
                gate.id(),
                9011L,
                "camera-02",
                "audit-server-scope-b.mp4",
                "playback",
                List.of("camera-02"),
                "tampered request scope"
        ));

        assertEquals("denied", audit.decision());
        assertTrue(audit.deniedReasons().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(item -> "case_audit".equals(item.materialType())
                        && "media_access_denied".equals(item.materialUri())
                        && item.actionNote().contains("action=playback")
                        && item.actionNote().contains("camera_not_allowed")));
    }

    @Test
    void semanticIndexQueueSupportsAsyncBacklogEvaluation() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 30);
        ReviewItemAggregate first = service.ingestClue(newClue("alert-semantic-queue-1", baseTime, "queue-1.jpg", "queue-1.mp4"));
        ReviewItemAggregate second = service.ingestClue(newClue("alert-semantic-queue-2", baseTime.plusMinutes(10), "queue-2.jpg", "queue-2.mp4"));

        ReviewSemanticReindexJob job = service.queueSemanticReindex(new ReviewSemanticReindexCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9001L
        ));
        ReviewSemanticIndexEvaluation pending = service.evaluateSemanticIndex(new ReviewSemanticIndexEvaluationCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9001L
        ));
        service.reindexSemanticIndex(new ReviewQuery(null, "camera-01", null, null));
        ReviewSemanticIndexEvaluation indexed = service.evaluateSemanticIndex(new ReviewSemanticIndexEvaluationCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9001L
        ));

        assertEquals("queued", job.status());
        assertEquals(List.of(first.id(), second.id()), job.queuedReviewItemIds());
        assertEquals(2, pending.pendingCount());
        assertEquals(0.0D, pending.coverageRate());
        assertTrue(pending.recommendedActions().contains("process_pending_semantic_index"));
        assertEquals(2, indexed.indexedCount());
        assertEquals(1.0D, indexed.coverageRate());
        assertEquals(List.of(), indexed.staleReviewItemIds());
    }

    @Test
    void semanticIndexWorkerRetriesFailuresAndReportsBacklogProgress() {
        FailingSemanticIndexStore itemStore = new FailingSemanticIndexStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 6, 9, 30);
        ReviewItemAggregate first = service.ingestClue(newClue("alert-semantic-worker-1", baseTime, "worker-1.jpg", "worker-1.mp4"));
        ReviewItemAggregate second = service.ingestClue(newClue("alert-semantic-worker-2", baseTime.plusMinutes(10), "worker-2.jpg", "worker-2.mp4"));

        service.queueSemanticReindex(new ReviewSemanticReindexCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9101L
        ));
        itemStore.failNextIndexedUpsertFor(second.id(), "embedding provider timeout");

        ReviewSemanticWorkerRun firstRun = service.processSemanticIndexQueue(new ReviewSemanticWorkerCommand(
                new ReviewQuery(null, "camera-01", null, null),
                10,
                9101L
        ));
        ReviewSemanticIndexEvaluation partial = service.evaluateSemanticIndex(new ReviewSemanticIndexEvaluationCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9101L
        ));

        assertEquals("partial_failed", firstRun.status());
        assertEquals(2, firstRun.scannedCount());
        assertEquals(1, firstRun.processedCount());
        assertEquals(1, firstRun.failedCount());
        assertEquals(1, firstRun.remainingBacklogCount());
        assertEquals(0.5D, firstRun.progressRate());
        assertEquals(List.of(first.id()), firstRun.processedReviewItemIds());
        assertEquals(List.of(second.id()), firstRun.failedReviewItemIds());
        assertEquals(1, partial.indexedCount());
        assertEquals(1, partial.failedCount());
        assertEquals(0.5D, partial.rebuildProgressRate());
        assertEquals("critical", partial.backlogAlarmLevel());
        assertTrue(partial.latestIndexVersion() >= 2);
        ReviewSemanticIndexEntry failed = itemStore.listSemanticIndex(new ReviewQuery(null, "camera-01", null, null)).stream()
                .filter(entry -> entry.reviewItemId().equals(second.id()))
                .findFirst()
                .orElseThrow();
        assertEquals("failed", failed.indexStatus());
        assertEquals(1, failed.retryCount());
        assertEquals("embedding provider timeout", failed.lastError());

        itemStore.makeSemanticRetryDue(second.id());
        ReviewSemanticWorkerRun retryRun = service.processSemanticIndexQueue(new ReviewSemanticWorkerCommand(
                new ReviewQuery(null, "camera-01", null, null),
                10,
                9101L
        ));
        ReviewSemanticIndexEvaluation complete = service.evaluateSemanticIndex(new ReviewSemanticIndexEvaluationCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9101L
        ));

        assertEquals("completed", retryRun.status());
        assertEquals(1, retryRun.processedCount());
        assertEquals(0, retryRun.failedCount());
        assertEquals(0, retryRun.remainingBacklogCount());
        assertEquals(1.0D, complete.rebuildProgressRate());
        assertEquals("none", complete.backlogAlarmLevel());
        assertEquals(2, complete.indexedCount());

        ReviewItemAggregate third = service.ingestClue(newClue("alert-semantic-worker-3", baseTime.plusMinutes(20), "worker-3.jpg", "worker-3.mp4"));
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9101L
        ));
        String jobSummary = new SupervisionAlertReviewSemanticIndexJob(service).execute("10");

        assertTrue(jobSummary.contains("status=completed"));
        assertTrue(jobSummary.contains("processed=3"));
        assertTrue(jobSummary.contains("remaining=0"));
        assertTrue(service.evaluateSemanticIndex(new ReviewSemanticIndexEvaluationCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9101L
        )).staleReviewItemIds().isEmpty());
        assertTrue(third.id() != null);
    }

    @Test
    void concurrentSemanticWorkersClaimEachReviewItemAtMostOnce() throws Exception {
        BlockingSemanticIndexStore itemStore = new BlockingSemanticIndexStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        service.ingestClue(newClue(
                "alert-semantic-worker-claim",
                LocalDateTime.of(2026, 7, 13, 8, 0),
                "semantic-worker-claim.jpg",
                "semantic-worker-claim.mp4"
        ));
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9201L
        ));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<ReviewSemanticWorkerRun> first = new AtomicReference<>();
        AtomicReference<ReviewSemanticWorkerRun> second = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread firstWorker = new Thread(
                () -> processSemanticWorkerAfterStart(service, ready, start, first, failure),
                "semantic-worker-claim-1"
        );
        Thread secondWorker = new Thread(
                () -> processSemanticWorkerAfterStart(service, ready, start, second, failure),
                "semantic-worker-claim-2"
        );

        firstWorker.start();
        secondWorker.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS));
        start.countDown();
        firstWorker.join(3000);
        secondWorker.join(3000);

        assertNull(failure.get());
        assertFalse(firstWorker.isAlive());
        assertFalse(secondWorker.isAlive());
        assertEquals(1, itemStore.indexedUpsertAttempts());
        assertEquals(1, first.get().processedCount() + second.get().processedCount());
    }

    @Test
    void semanticReindexDoesNotOverwriteAnActiveProcessingClaim() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-semantic-reindex-active-claim",
                LocalDateTime.of(2026, 7, 13, 9, 30),
                "semantic-reindex-active-claim.jpg",
                "semantic-reindex-active-claim.mp4"
        ));
        ReviewQuery query = new ReviewQuery(null, "camera-01", null, null);
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(query, 9203L));
        LocalDateTime claimedAt = LocalDateTime.now();
        ReviewSemanticIndexEntry activeClaim = itemStore.claimSemanticIndex(
                List.of(item.id()),
                1,
                "active-semantic-claim",
                claimedAt,
                claimedAt.plusMinutes(5)
        ).get(0);

        ReviewSemanticReindexJob queued = service.queueSemanticReindex(
                new ReviewSemanticReindexCommand(query, 9204L));
        ReviewSemanticIndexEntry legacyReindex = service.reindexSemanticIndex(query).get(0);
        ReviewSemanticIndexEntry afterQueue = itemStore.listSemanticIndex(query).get(0);

        assertEquals("processing", afterQueue.indexStatus());
        assertEquals(activeClaim.claimToken(), afterQueue.claimToken());
        assertEquals(activeClaim.indexGenerationId(), afterQueue.indexGenerationId());
        assertEquals("processing", legacyReindex.indexStatus());
        assertEquals(activeClaim.claimToken(), legacyReindex.claimToken());
        assertEquals("deferred", queued.status());
        assertEquals(List.of(), queued.queuedReviewItemIds());
    }

    @Test
    void reviewReconciliationDoesNotClearAnActiveSemanticProcessingClaim() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-reconcile-active-semantic-claim",
                LocalDateTime.of(2026, 7, 13, 9, 45),
                "reconcile-active-semantic-claim.jpg",
                "reconcile-active-semantic-claim.mp4"
        ));
        ReviewQuery query = new ReviewQuery(null, "camera-01", null, null);
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(query, 9205L));
        LocalDateTime claimedAt = LocalDateTime.now();
        ReviewSemanticIndexEntry activeClaim = itemStore.claimSemanticIndex(
                List.of(item.id()),
                1,
                "reconcile-active-claim",
                claimedAt,
                claimedAt.plusMinutes(5)
        ).get(0);

        ReviewReconciliationResult reconciliation = service.reconcileReviewRuntime(
                new ReviewReconciliationCommand(query, 9206L, true));
        ReviewSemanticIndexEntry persisted = itemStore.listSemanticIndex(query).get(0);

        assertEquals("processing", persisted.indexStatus());
        assertEquals(activeClaim.claimToken(), persisted.claimToken());
        assertEquals(activeClaim.indexGenerationId(), persisted.indexGenerationId());
        assertEquals(0, reconciliation.repairedSemanticIndexCount());
        assertFalse(reconciliation.findings().contains("semantic_reindexed:" + item.id()));
    }

    @Test
    void semanticIndexWorkerDefersCompletionClaimConflictAndProcessesLaterClaim() {
        ClaimConflictSemanticIndexStore itemStore = new ClaimConflictSemanticIndexStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 13, 9, 40);
        ReviewItemAggregate conflicted = service.ingestClue(newClue(
                "alert-semantic-worker-completion-conflict",
                baseTime,
                "semantic-worker-completion-conflict.jpg",
                "semantic-worker-completion-conflict.mp4"
        ));
        ReviewItemAggregate later = service.ingestClue(newClue(
                "alert-semantic-worker-after-completion-conflict",
                baseTime.plusMinutes(10),
                "semantic-worker-after-completion-conflict.jpg",
                "semantic-worker-after-completion-conflict.mp4"
        ));
        ReviewQuery query = new ReviewQuery(null, "camera-01", null, null);
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(query, 9205L));
        itemStore.conflictOnCompletion(conflicted.id());

        ReviewSemanticWorkerRun run = service.processSemanticIndexQueue(
                new ReviewSemanticWorkerCommand(query, 10, 9205L));

        assertEquals("partial_deferred", run.status());
        assertEquals(2, run.scannedCount());
        assertEquals(1, run.processedCount());
        assertEquals(0, run.failedCount());
        assertEquals(List.of(later.id()), run.processedReviewItemIds());
        assertEquals(List.of(), run.failedReviewItemIds());
    }

    @Test
    void semanticIndexWorkerDefersFailureCompletionClaimConflictAndProcessesLaterClaim() {
        ClaimConflictSemanticIndexStore itemStore = new ClaimConflictSemanticIndexStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 13, 9, 50);
        ReviewItemAggregate conflicted = service.ingestClue(newClue(
                "alert-semantic-worker-failure-conflict",
                baseTime,
                "semantic-worker-failure-conflict.jpg",
                "semantic-worker-failure-conflict.mp4"
        ));
        ReviewItemAggregate later = service.ingestClue(newClue(
                "alert-semantic-worker-after-failure-conflict",
                baseTime.plusMinutes(10),
                "semantic-worker-after-failure-conflict.jpg",
                "semantic-worker-after-failure-conflict.mp4"
        ));
        ReviewQuery query = new ReviewQuery(null, "camera-01", null, null);
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(query, 9206L));
        itemStore.failThenConflictOnCompletion(conflicted.id());

        ReviewSemanticWorkerRun run = service.processSemanticIndexQueue(
                new ReviewSemanticWorkerCommand(query, 10, 9206L));

        assertEquals("partial_deferred", run.status());
        assertEquals(2, run.scannedCount());
        assertEquals(1, run.processedCount());
        assertEquals(0, run.failedCount());
        assertEquals(List.of(later.id()), run.processedReviewItemIds());
        assertEquals(List.of(), run.failedReviewItemIds());
    }

    @Test
    void semanticIndexFailureSchedulesRetryAndSkipsWorkerBeforeNextRetryAt() {
        FailingSemanticIndexStore itemStore = new FailingSemanticIndexStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-semantic-worker-backoff",
                LocalDateTime.of(2026, 7, 13, 8, 10),
                "semantic-worker-backoff.jpg",
                "semantic-worker-backoff.mp4"
        ));
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(
                new ReviewQuery(null, "camera-01", null, null),
                9202L
        ));
        itemStore.failNextIndexedUpsertFor(item.id(), "embedding provider timeout");

        ReviewSemanticWorkerRun failedRun = service.processSemanticIndexQueue(new ReviewSemanticWorkerCommand(
                new ReviewQuery(null, "camera-01", null, null),
                10,
                9202L
        ));
        ReviewSemanticIndexEntry failed = itemStore.listSemanticIndex(
                        new ReviewQuery(null, "camera-01", null, null)).get(0);
        LocalDateTime nextRetryAt = (LocalDateTime) recordTraceValue(failed, "nextRetryAt");
        ReviewSemanticWorkerRun prematureRetry = service.processSemanticIndexQueue(new ReviewSemanticWorkerCommand(
                new ReviewQuery(null, "camera-01", null, null),
                10,
                9202L
        ));

        assertEquals(1, failedRun.failedCount());
        assertNotNull(nextRetryAt);
        assertTrue(nextRetryAt.isAfter(failedRun.processedAt()));
        assertEquals(0, prematureRetry.scannedCount());
        assertEquals(0, prematureRetry.processedCount());
        assertEquals(0, prematureRetry.failedCount());
        ReviewSemanticIndexEntry stillFailed = itemStore.listSemanticIndex(
                        new ReviewQuery(null, "camera-01", null, null)).get(0);
        assertEquals("failed", stillFailed.indexStatus());
        assertEquals(1, stillFailed.retryCount());
        assertEquals(nextRetryAt, recordTraceValue(stillFailed, "nextRetryAt"));
    }

    @Test
    void recordCoverageMergesVideoIndexIntervalsAndMissingGaps() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 13, 10);
        CapturingRecordCoverageResolver coverageResolver = new CapturingRecordCoverageResolver(List.of(
                new RecordCoverageSegment("available", alertTime.minusSeconds(240), alertTime.minusSeconds(60),
                        0, "https://eye.yfeiai.com/records/a.mp4", 0, Map.of("source", "video-index")),
                new RecordCoverageSegment("motion", alertTime.plusSeconds(60), alertTime.plusSeconds(180),
                        42, "https://eye.yfeiai.com/records/b.mp4", 3, Map.of("source", "video-index"))
        ));
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                coverageResolver
        );
        ReviewItemAggregate item = service.ingestClue(newClue("alert-coverage", alertTime, "snap.jpg", null));

        List<RecordCoverageSegment> coverage = service.getRecordCoverage(item.id());

        assertEquals(List.of(new RecordCoverageRequest("device-01", "camera-01",
                        alertTime.minusSeconds(300), alertTime.plusSeconds(300))),
                coverageResolver.requests());
        assertEquals(List.of("missing", "available", "missing", "motion", "missing"),
                coverage.stream().map(RecordCoverageSegment::status).toList());
        assertEquals(alertTime.minusSeconds(300), coverage.get(0).startTime());
        assertEquals(alertTime.minusSeconds(240), coverage.get(0).endTime());
        assertEquals(alertTime.minusSeconds(60), coverage.get(2).startTime());
        assertEquals(alertTime.plusSeconds(60), coverage.get(2).endTime());
        assertEquals(42, coverage.get(3).motion());
        assertEquals(3, coverage.get(3).objects());
    }

    @Test
    void allowedRecordCoverageReadAuditsReturnedRecordUrisInEvidenceTrail() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 6, 12, 40);
        CapturingRecordCoverageResolver coverageResolver = new CapturingRecordCoverageResolver(List.of(
                new RecordCoverageSegment("available", alertTime.minusSeconds(240), alertTime.minusSeconds(60),
                        0, "https://eye.yfeiai.com/records/coverage-a.mp4", 0, Map.of("source", "video-index")),
                new RecordCoverageSegment("motion", alertTime.plusSeconds(60), alertTime.plusSeconds(180),
                        37, "https://eye.yfeiai.com/records/coverage-b.mp4", 2, Map.of("source", "video-index"))
        ));
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7610L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.DISPATCHED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                coverageResolver
        );
        ReviewItemAggregate item = service.ingestClue(newClue("alert-coverage-audit", alertTime, "coverage.jpg", null));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9103L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "coverage audit case",
                item.id(),
                List.of(item.id())
        ));

        List<RecordCoverageSegment> coverage = service.getRecordCoverage(
                item.id(),
                reviewCase.id(),
                9104L,
                List.of("camera-01")
        );

        assertEquals(List.of("missing", "available", "missing", "motion", "missing"),
                coverage.stream().map(RecordCoverageSegment::status).toList());
        List<ReviewEvidenceAuditEntry> coverageAudits = service.getEvidenceAuditTrail(reviewCase.id()).stream()
                .filter(entry -> "media_access_granted".equals(entry.actionType()))
                .filter(entry -> "coverage".equals(entry.metadata().get("mediaAction")))
                .toList();
        assertEquals(List.of(
                        List.of("https://eye.yfeiai.com/records/coverage-a.mp4"),
                        List.of("https://eye.yfeiai.com/records/coverage-b.mp4")
                ),
                coverageAudits.stream().map(ReviewEvidenceAuditEntry::evidenceUris).toList());
        assertTrue(coverageAudits.stream().allMatch(entry -> Objects.equals(9104L, entry.operatorUserId())));
        assertTrue(coverageAudits.stream().allMatch(entry -> entry.boundEventIds().contains(7610L)));
        assertTrue(coverageAudits.stream().allMatch(entry -> Objects.equals("camera-01", entry.metadata().get("cameraId"))));
    }

    @Test
    void lowSampleRuleSuggestionCannotBeShadowEvaluatedBeforeMoreReviewSamples() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                ruleStore,
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-low-sample-1",
                LocalDateTime.of(2026, 7, 1, 8, 30),
                "low-sample-1.jpg",
                "low-sample-1.mp4"
        ));

        ReviewItemAggregate falsePositive = service.markFalsePositive(
                new ReviewOperationCommand(item.id(), 9001L, "zone too wide")
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () ->
                service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                        item.id(),
                        9002L,
                        "shadow_evaluated",
                        "sample is too small"
                )));
        assertTrue(rejected.getMessage().contains("minimum sample"));
        assertEquals("pending", falsePositive.ruleSuggestionStatus());
        assertEquals(false, falsePositive.ruleSuggestion().get("sampleRequirementMet"));
        assertEquals(List.of(), ruleStore.listAll());
    }

    @Test
    void falsePositiveRuleSuggestionAppliesRuleConfigOnlyAfterApprovalAndCanRollback() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                ruleStore,
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-rule-lifecycle",
                LocalDateTime.of(2026, 6, 30, 13, 20),
                "snap-rule.jpg",
                "record-rule.mp4"
        ));
        ReviewItemAggregate second = service.ingestClue(newClue(
                "alert-rule-lifecycle-2",
                LocalDateTime.of(2026, 6, 30, 13, 40),
                "snap-rule-2.jpg",
                "record-rule-2.mp4"
        ));
        ReviewItemAggregate third = service.ingestClue(newClue(
                "alert-rule-lifecycle-3",
                LocalDateTime.of(2026, 6, 30, 14, 0),
                "snap-rule-3.jpg",
                "record-rule-3.mp4"
        ));

        ReviewItemAggregate falsePositive = service.markFalsePositive(
                new ReviewOperationCommand(item.id(), 9003L, "zone_too_wide")
        );
        service.markFalsePositive(new ReviewOperationCommand(second.id(), 9006L, "zone_too_wide"));
        service.markFalsePositive(new ReviewOperationCommand(third.id(), 9007L, "zone_too_wide"));
        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                item.id(),
                9004L,
                "accepted",
                "approval must not skip shadow evaluation"
        )));
        ReviewItemAggregate shadowEvaluated = service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                item.id(),
                9004L,
                "shadow_evaluated",
                "independent shadow evaluation completed"
        ));
        assertEquals("shadow_evaluated", shadowEvaluated.ruleSuggestionStatus());
        assertTrue(shadowEvaluated.ruleSuggestion().get("shadowEvaluation") instanceof Map<?, ?>);
        assertTrue(shadowEvaluated.ruleSuggestion().get("replayReport") instanceof Map<?, ?>);
        assertEquals(List.of(), ruleStore.listAll());
        ReviewItemAggregate accepted = service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                item.id(),
                9004L,
                "accepted",
                "confirmed by supervisor"
        ));
        assertEquals(List.of(), ruleStore.listAll());

        ReviewItemAggregate applied = service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                item.id(),
                9004L,
                "applied",
                "manual rule updated"
        ));

        assertEquals("pending", falsePositive.ruleSuggestionStatus());
        assertEquals("pending", falsePositive.ruleSuggestion().get("lifecycleStatus"));
        assertEquals(List.of("narrow_zone", "raise_confidence", "increase_min_stay", "require_zone", "suppress_label_zone"),
                falsePositive.ruleSuggestion().get("candidateActions"));
        assertEquals("accepted", accepted.ruleSuggestionStatus());
        assertEquals("applied", applied.ruleSuggestionStatus());
        assertEquals(1, ruleStore.listAll().size());
        ReviewRuleView appliedRule = ruleStore.listAll().get(0);
        assertEquals(SupervisionRuleSeeds.RULE_RESTRICTED_AREA, appliedRule.ruleCode());
        assertEquals("false_positive_camera-01_zone-a", appliedRule.ruleName());
        assertEquals("video", appliedRule.sourceSystem());
        assertEquals("camera-01", appliedRule.cameraId());
        assertEquals("zone-a", appliedRule.zoneCode());
        assertEquals("person", appliedRule.objectLabel());
        assertEquals(Boolean.TRUE, appliedRule.enabled());
        assertEquals(appliedRule.id(), applied.ruleSuggestion().get("appliedRuleId"));
        assertEquals("rule-" + appliedRule.id() + "-v1", applied.ruleSuggestion().get("configVersion"));

        ReviewItemAggregate reverted = service.revertRuleSuggestion(new RuleSuggestionOperationCommand(
                item.id(),
                9005L,
                "reverted",
                "rollback after supervisor check"
        ));
        assertEquals("reverted", reverted.ruleSuggestionStatus());
        assertEquals(Boolean.FALSE, ruleStore.listAll().get(0).enabled());
        assertEquals("rule-" + appliedRule.id() + "-v2", reverted.ruleSuggestion().get("rollbackVersion"));
    }

    @Test
    void ruleSuggestionCanBeRejectedBeforeApprovalWithoutChangingLiveRule() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                ruleStore,
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 1, 8, 40);
        ReviewItemAggregate pendingCandidate = service.ingestClue(
                newClue("alert-reject-pending", baseTime, "reject-pending.jpg", "reject-pending.mp4")
        );
        ReviewItemAggregate shadowCandidate = service.ingestClue(
                newClue("alert-reject-shadow", baseTime.plusMinutes(10), "reject-shadow.jpg", "reject-shadow.mp4")
        );
        ReviewItemAggregate sample = service.ingestClue(
                newClue("alert-reject-sample", baseTime.plusMinutes(20), "reject-sample.jpg", "reject-sample.mp4")
        );
        service.markFalsePositive(new ReviewOperationCommand(pendingCandidate.id(), 9011L, "zone too wide"));
        service.markFalsePositive(new ReviewOperationCommand(shadowCandidate.id(), 9012L, "zone too wide"));
        service.markFalsePositive(new ReviewOperationCommand(sample.id(), 9013L, "zone too wide"));

        ReviewItemAggregate rejectedFromPending = service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(pendingCandidate.id(), 9014L, "rejected", "not suitable for governance")
        );
        assertEquals("rejected", rejectedFromPending.ruleSuggestionStatus());
        assertEquals(List.of(), ruleStore.listAll());

        ReviewItemAggregate shadowEvaluated = service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(shadowCandidate.id(), 9014L, "shadow_evaluated", "shadow evidence ready")
        );
        assertEquals("shadow_evaluated", shadowEvaluated.ruleSuggestionStatus());
        ReviewItemAggregate rejectedFromShadow = service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(shadowCandidate.id(), 9014L, "rejected", "shadow risk too high")
        );
        assertEquals("rejected", rejectedFromShadow.ruleSuggestionStatus());
        assertEquals(List.of(), ruleStore.listAll());

        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(shadowCandidate.id(), 9014L, "accepted", "rejected is terminal")
        ));
        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(shadowCandidate.id(), 9014L, "applied", "rejected cannot apply")
        ));
        assertEquals(List.of(), ruleStore.listAll());
    }

    @Test
    void ruleSuggestionSafeApplyRequiresApprovalAndStoresShadowEvaluation() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                ruleStore,
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 1, 9, 0);
        ReviewItemAggregate first = service.ingestClue(newClue("alert-safe-apply-1", baseTime, "safe-1.jpg", "safe-1.mp4"));
        ReviewItemAggregate second = service.ingestClue(newClue("alert-safe-apply-2", baseTime.plusMinutes(20), "safe-2.jpg", "safe-2.mp4"));
        ReviewItemAggregate third = service.ingestClue(newClue("alert-safe-apply-3", baseTime.plusMinutes(40), "safe-3.jpg", "safe-3.mp4"));
        service.markFalsePositive(new ReviewOperationCommand(first.id(), 9001L, "zone too wide"));
        service.markFalsePositive(new ReviewOperationCommand(second.id(), 9002L, "zone too wide"));
        service.markFalsePositive(new ReviewOperationCommand(third.id(), 9004L, "zone too wide"));

        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                first.id(),
                9003L,
                "applied",
                "unsafe direct apply"
        )));

        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                first.id(),
                9003L,
                "accepted",
                "unsafe approval without shadow evaluation"
        )));

        ReviewItemAggregate shadowEvaluated = service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                first.id(),
                9003L,
                "shadow_evaluated",
                "independent shadow evaluation completed"
        ));
        assertEquals("shadow_evaluated", shadowEvaluated.ruleSuggestionStatus());
        assertEquals(List.of(), ruleStore.listAll());
        Map<?, ?> shadowReplayReport = (Map<?, ?>) shadowEvaluated.ruleSuggestion().get("replayReport");

        Map<String, Object> missingReplayEvidence = new LinkedHashMap<>(shadowEvaluated.ruleSuggestion());
        missingReplayEvidence.remove("replayReport");
        itemStore.updateRuleSuggestionStatus(
                first.id(),
                9003L,
                "shadow_evaluated",
                Map.copyOf(missingReplayEvidence),
                LocalDateTime.now()
        );
        IllegalStateException missingReplayRejected = assertThrows(IllegalStateException.class, () ->
                service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                        first.id(),
                        9003L,
                        "accepted",
                        "approval cannot create missing replay evidence"
                )));
        assertTrue(missingReplayRejected.getMessage().contains("shadowEvaluation and replayReport"));
        assertEquals(List.of(), ruleStore.listAll());
        itemStore.updateRuleSuggestionStatus(
                first.id(),
                9003L,
                "shadow_evaluated",
                shadowEvaluated.ruleSuggestion(),
                LocalDateTime.now()
        );

        ReviewItemAggregate accepted = service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                first.id(),
                9003L,
                "accepted",
                "supervisor approved"
        ));
        assertEquals("accepted", accepted.ruleSuggestionStatus());
        assertEquals(List.of(), ruleStore.listAll());
        assertTrue(accepted.ruleSuggestion().get("shadowEvaluation") != null);
        Map<?, ?> acceptedReplayReport = (Map<?, ?>) accepted.ruleSuggestion().get("replayReport");
        assertEquals(shadowReplayReport, acceptedReplayReport);
        assertEquals("review_before_apply", acceptedReplayReport.get("decision"));
        assertEquals(3, acceptedReplayReport.get("evaluatedCount"));
        assertEquals(3, acceptedReplayReport.get("falsePositiveReduction"));
        Map<?, ?> replayRuleVersion = (Map<?, ?>) acceptedReplayReport.get("ruleVersion");
        Map<?, ?> sampleWindow = (Map<?, ?>) acceptedReplayReport.get("sampleWindow");
        Map<?, ?> hitComparison = (Map<?, ?>) acceptedReplayReport.get("hitComparison");
        Map<?, ?> falseNegativeEstimate = (Map<?, ?>) acceptedReplayReport.get("falseNegativeEstimate");
        assertEquals(SupervisionRuleSeeds.RULE_RESTRICTED_AREA, replayRuleVersion.get("ruleCode"));
        assertEquals("camera-01", replayRuleVersion.get("cameraId"));
        assertEquals(baseTime.toString(), sampleWindow.get("startTime"));
        assertEquals(baseTime.plusMinutes(40).toString(), sampleWindow.get("endTime"));
        assertEquals(3, sampleWindow.get("sampleCount"));
        assertEquals(3, hitComparison.get("beforeCount"));
        assertEquals(0, hitComparison.get("afterCount"));
        assertEquals(3, hitComparison.get("difference"));
        assertEquals(3, falseNegativeEstimate.get("possibleMissedCount"));
        assertEquals("review_required", falseNegativeEstimate.get("riskLevel"));

        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(first.id(), 9003L, "rejected", "approved suggestion cannot be rejected")
        ));
        assertEquals(List.of(), ruleStore.listAll());

        assertThrows(IllegalStateException.class, () -> service.revertRuleSuggestion(new RuleSuggestionOperationCommand(
                first.id(),
                9003L,
                "reverted",
                "cannot rollback before application"
        )));
        assertEquals(List.of(), ruleStore.listAll());

        Map<String, Object> missingShadowEvidence = new LinkedHashMap<>(accepted.ruleSuggestion());
        missingShadowEvidence.remove("shadowEvaluation");
        itemStore.updateRuleSuggestionStatus(
                first.id(),
                9003L,
                "accepted",
                Map.copyOf(missingShadowEvidence),
                LocalDateTime.now()
        );
        IllegalStateException missingShadowRejected = assertThrows(IllegalStateException.class, () ->
                service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                        first.id(),
                        9003L,
                        "applied",
                        "application cannot recreate missing shadow evidence"
                )));
        assertTrue(missingShadowRejected.getMessage().contains("shadowEvaluation and replayReport"));
        assertEquals(List.of(), ruleStore.listAll());
        itemStore.updateRuleSuggestionStatus(
                first.id(),
                9003L,
                "accepted",
                accepted.ruleSuggestion(),
                LocalDateTime.now()
        );

        ReviewItemAggregate applied = service.updateRuleSuggestionStatus(new RuleSuggestionOperationCommand(
                first.id(),
                9003L,
                "applied",
                "safe apply after shadow evaluation"
        ));

        Map<?, ?> shadowEvaluation = (Map<?, ?>) applied.ruleSuggestion().get("shadowEvaluation");
        assertEquals(3, shadowEvaluation.get("estimatedSuppressedCount"));
        assertEquals(3, shadowEvaluation.get("evaluatedReviewItemCount"));
        assertEquals(1.0D, shadowEvaluation.get("beforeFalsePositiveRate"));
        assertEquals(0.0D, shadowEvaluation.get("afterFalsePositiveRate"));
        assertEquals("supervisor approved", applied.ruleSuggestion().get("approvalNote"));
        assertEquals(acceptedReplayReport, applied.ruleSuggestion().get("replayReport"));
        assertTrue(String.valueOf(applied.ruleSuggestion().get("configVersion")).startsWith("rule-"));
        assertThrows(IllegalStateException.class, () -> service.updateRuleSuggestionStatus(
                new RuleSuggestionOperationCommand(first.id(), 9003L, "rejected", "applied suggestion cannot be rejected")
        ));
        assertEquals(1, ruleStore.listAll().size());
    }

    @Test
    void ruleSuggestionStatsAggregateFalsePositiveRateByCameraZoneLabelAndWindow() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 30, 13, 25);
        ReviewItemAggregate first = service.ingestClue(newClue("alert-stat-001", baseTime, "stat-1.jpg", "stat-1.mp4"));
        ReviewItemAggregate second = service.ingestClue(newClue("alert-stat-002", baseTime.plusMinutes(10), "stat-2.jpg", "stat-2.mp4"));
        service.ingestClue(newClue("alert-stat-003", baseTime.plusMinutes(20), "stat-3.jpg", "stat-3.mp4"));
        service.markFalsePositive(new ReviewOperationCommand(first.id(), 9001L, "zone_too_wide"));
        service.markFalsePositive(new ReviewOperationCommand(second.id(), 9002L, "zone_too_wide"));

        List<RuleSuggestionStat> stats = service.listRuleSuggestionStats(new ReviewQuery(
                null,
                "camera-01",
                "zone-a",
                "person",
                null,
                null,
                null,
                null,
                baseTime.minusMinutes(1),
                baseTime.plusMinutes(30)
        ));

        assertEquals(1, stats.size());
        assertEquals("camera-01", stats.get(0).cameraId());
        assertEquals("zone-a", stats.get(0).zoneCode());
        assertEquals("person", stats.get(0).objectLabel());
        assertEquals(2, stats.get(0).falsePositiveCount());
        assertEquals(3, stats.get(0).totalCount());
        assertEquals(0.67D, stats.get(0).falsePositiveRate());
        assertEquals(List.of("narrow_zone", "raise_confidence", "increase_min_stay", "require_zone", "suppress_label_zone"),
                stats.get(0).candidateActions());
    }

    @Test
    void ruleReplayEvaluatesHistoricalItemsBeforeApplyingRuleChange() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 1, 9, 30);
        ReviewItemAggregate first = service.ingestClue(newClue("alert-replay-001", baseTime, "replay-1.jpg", "replay-1.mp4"));
        ReviewItemAggregate second = service.ingestClue(newClue("alert-replay-002", baseTime.plusMinutes(10), "replay-2.jpg", "replay-2.mp4"));
        service.markFalsePositive(new ReviewOperationCommand(first.id(), 9001L, "zone too wide"));

        ReviewRuleReplayResult result = service.replayRule(new ReviewRuleReplayCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "video",
                "camera-01",
                "zone-a",
                "person",
                20,
                baseTime.minusMinutes(1),
                baseTime.plusMinutes(20),
                9003L
        ));

        assertEquals(SupervisionRuleSeeds.RULE_RESTRICTED_AREA, result.ruleCode());
        assertEquals(List.of(first.id(), second.id()), result.evaluatedReviewItemIds());
        assertEquals(2, result.evaluatedCount());
        assertEquals(0, result.matchBeforeCount());
        assertEquals(0, result.matchAfterCount());
        assertEquals(1, result.falsePositiveBeforeCount());
        assertEquals(0.5D, result.falsePositiveBeforeRate());
        assertEquals(0.0D, result.falsePositiveAfterRate());
        assertTrue(result.recommendedActions().contains("safe_to_apply"));
        assertEquals("camera-01", result.scope().get("cameraId"));
        assertEquals(false, result.report().get("shouldApply"));
        assertEquals("review_before_apply", result.report().get("decision"));
        assertEquals(1, result.report().get("falsePositiveReduction"));
        assertEquals(2, result.report().get("possibleMissedCount"));
        Map<?, ?> impactScope = (Map<?, ?>) result.report().get("impactScope");
        assertEquals(List.of("camera-01"), impactScope.get("cameraIds"));
        assertEquals(List.of("zone-a"), impactScope.get("zoneCodes"));
    }

    @Test
    void reviewCaseTimelineIncludesCoverageAndReviewActions() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 30, 13, 28);
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7200L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                new CapturingRecordEvidenceResolver(Optional.empty()),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "available",
                        request.beginTime(),
                        request.endTime(),
                        0,
                        "timeline-segment.mp4",
                        0,
                        Map.of()
                ))
        );
        ReviewItemAggregate falsePositive = service.ingestClue(newClue("alert-timeline-fp", baseTime, "fp.jpg", null));
        ReviewItemAggregate converted = service.ingestClue(newClue("alert-timeline-event", baseTime.plusMinutes(8), "event.jpg", "event.mp4"));
        service.markFalsePositive(new ReviewOperationCommand(falsePositive.id(), 9001L, "manual false positive"));
        service.convertToEvent(new ReviewToEventCommand(converted.id(), 9002L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "action timeline",
                falsePositive.id(),
                List.of(falsePositive.id(), converted.id())
        ));

        List<ReviewCaseTimelineItem> timeline = service.getReviewCaseTimeline(reviewCase.id());

        ReviewCaseTimelineItem coverageItem = timeline.stream()
                .filter(item -> falsePositive.id().equals(item.reviewItemId()))
                .filter(item -> "record_coverage".equals(item.materialType()))
                .findFirst()
                .orElseThrow();
        assertEquals("timeline-segment.mp4", coverageItem.materialUri());
        assertEquals(baseTime.minusMinutes(5), coverageItem.happenedAt());
        assertEquals("available", coverageItem.actionNote());
        assertTrue(timeline.stream().anyMatch(item -> "review_action".equals(item.materialType())
                && "false_positive".equals(item.materialUri())));
        assertTrue(timeline.stream().anyMatch(item -> "review_action".equals(item.materialType())
                && "converted_to_event:7200".equals(item.materialUri())));
    }

    @Test
    void reviewCaseRecordTimelineUsesEachRecordFileStartForPrerollOffsets() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime firstRecordStart = LocalDateTime.of(2026, 7, 2, 8, 0);
        LocalDateTime secondRecordStart = LocalDateTime.of(2026, 7, 2, 8, 1);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-case-record-playback",
                firstRecordStart.plusSeconds(10),
                "case-record-playback.jpg",
                "case-record-playback.mp4"
        ));
        itemStore.evidenceByItemId.put(item.id(), List.of(
                new ReviewEvidenceItem(
                        item.id(),
                        "alert-case-record-playback",
                        "record",
                        "case-record-playback.mp4",
                        firstRecordStart.plusSeconds(10),
                        firstRecordStart
                ),
                new ReviewEvidenceItem(
                        item.id(),
                        "alert-case-record-playback-2",
                        "record",
                        "case-record-playback-2.mp4",
                        secondRecordStart.plusSeconds(5),
                        secondRecordStart
                )
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "case record playback",
                item.id(),
                List.of(item.id())
        ));

        List<ReviewCaseTimelineItem> records = service.getReviewCaseTimeline(reviewCase.id()).stream()
                .filter(entry -> "record".equals(entry.materialType()))
                .toList();

        assertEquals(2, records.size());
        assertEquals(firstRecordStart, records.get(0).recordStartTime());
        assertEquals(10, records.get(0).playbackOffsetSeconds());
        assertEquals(secondRecordStart, records.get(1).recordStartTime());
        assertEquals(5, records.get(1).playbackOffsetSeconds());
    }

    @Test
    void reviewCaseRecordTimelineKeepsPlaybackContextEmptyWhenRecordStartIsUnknown() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 8, 30, 10);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-case-record-no-start",
                alertTime,
                "case-record-no-start.jpg",
                "case-record-no-start.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "case record without start",
                item.id(),
                List.of(item.id())
        ));

        ReviewCaseTimelineItem record = service.getReviewCaseTimeline(reviewCase.id()).stream()
                .filter(entry -> "record".equals(entry.materialType()))
                .findFirst()
                .orElseThrow();

        assertNull(record.recordStartTime());
        assertNull(record.playbackOffsetSeconds());
    }

    @Test
    void reviewCaseCandidatesUseCorrelationObjectIdsAndTimeWindow() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 6, 30, 13, 30);
        ReviewItemAggregate first = service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-001", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime, "device-01", "camera-01", "zone-a", "person", 15,
                "candidate-1.jpg", "candidate-1.mp4", null, List.of("person"), List.of("zone-a"),
                List.of("obj-shared"), 0.88D, List.of(0.1D, 0.2D, 0.3D, 0.4D), "corr-shared"
        ));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-002", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(70), "device-02", "camera-02", "zone-b", "person", 15,
                "candidate-2.jpg", "candidate-2.mp4", null, List.of("person"), List.of("zone-b"),
                List.of("obj-shared"), 0.86D, List.of(0.2D, 0.3D, 0.4D, 0.5D), "corr-shared"
        ));
        service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-003", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusMinutes(30), "device-03", "camera-03", "zone-c", "person", 15,
                "candidate-3.jpg", "candidate-3.mp4", null, List.of("person"), List.of("zone-c"),
                List.of("obj-other"), 0.80D, List.of(0.3D, 0.4D, 0.5D, 0.6D), "corr-other"
        ));

        List<ReviewItemAggregate> candidates = service.suggestReviewCaseCandidates(first.id());

        assertEquals(List.of(second.id()), candidates.stream().map(ReviewItemAggregate::id).toList());
    }

    @Test
    void reviewCaseCandidatesUseAdjacentCameraZoneAndRegulatoryArea() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(itemStore, new InMemoryRuleStore(), unusedEventService());
        LocalDateTime firstTime = LocalDateTime.of(2026, 6, 30, 13, 45);
        ReviewItemAggregate base = service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-area-001", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime, "device-01", "camera-01", "zone-a", "person", 15,
                "candidate-area-1.jpg", "candidate-area-1.mp4", null, List.of("person"), List.of("zone-a"),
                List.of("obj-base"), 0.88D, List.of(0.1D, 0.2D, 0.3D, 0.4D), null,
                null, null, null, Map.of(
                        "regulatoryArea", "yard-east",
                        "adjacentCameras", List.of("camera-02", "camera-03")
                )
        ));
        ReviewItemAggregate adjacentSameArea = service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-area-002", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(30), "device-02", "camera-02", "zone-b", "vehicle", 15,
                "candidate-area-2.jpg", "candidate-area-2.mp4", null, List.of("vehicle"), List.of("zone-b"),
                List.of("obj-other-1"), 0.84D, List.of(0.2D, 0.3D, 0.4D, 0.5D), null,
                null, null, null, Map.of("regulatoryArea", "yard-east")
        ));
        ReviewItemAggregate sameZone = service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-area-003", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(40), "device-04", "camera-04", "zone-a", "helmet", 15,
                "candidate-area-3.jpg", "candidate-area-3.mp4", null, List.of("helmet"), List.of("zone-a"),
                List.of("obj-other-2"), 0.81D, List.of(0.3D, 0.4D, 0.5D, 0.6D), null,
                null, null, null, Map.of("regulatoryArea", "yard-west")
        ));
        ReviewItemAggregate sharedObject = service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-area-004", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(90), "device-03", "camera-03", "zone-c", "person", 15,
                "candidate-area-4.jpg", "candidate-area-4.mp4", null, List.of("person"), List.of("zone-c"),
                List.of("obj-base"), 0.87D, List.of(0.4D, 0.5D, 0.6D, 0.7D), null,
                null, null, null, Map.of("regulatoryArea", "yard-east")
        ));
        service.ingestClue(new AlertClueCommand(
                "video", "alert-candidate-area-005", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(50), "device-05", "camera-05", "zone-d", "smoke", 15,
                "candidate-area-5.jpg", "candidate-area-5.mp4", null, List.of("smoke"), List.of("zone-d"),
                List.of("obj-other-3"), 0.75D, List.of(0.5D, 0.6D, 0.7D, 0.8D), null,
                null, null, null, Map.of("regulatoryArea", "yard-west")
        ));

        List<ReviewItemAggregate> candidates = service.suggestReviewCaseCandidates(base.id());

        assertEquals(List.of(sharedObject.id(), adjacentSameArea.id(), sameZone.id()),
                candidates.stream().map(ReviewItemAggregate::id).toList());
    }

    @Test
    void reviewCaseCandidatesUseConfiguredCameraTopologyWhenReviewDataHasNoTopology() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        ReviewCameraTopologyResolver topologyResolver = cameraId -> switch (cameraId) {
            case "camera-01" -> new ReviewCameraTopology("yard-east", List.of("camera-02"));
            case "camera-02" -> new ReviewCameraTopology("yard-east", List.of("camera-01"));
            case "camera-04" -> new ReviewCameraTopology("yard-east", List.of());
            default -> ReviewCameraTopology.empty();
        };
        SupervisionAlertReviewService service = newServiceWithCameraTopology(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                topologyResolver
        );
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 7, 9, 0);
        ReviewItemAggregate base = service.ingestClue(new AlertClueCommand(
                "video", "alert-topology-config-001", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime, "device-01", "camera-01", "zone-a", "person", 15,
                "topology-config-1.jpg", "topology-config-1.mp4", null, List.of("person"), List.of("zone-a"),
                List.of("obj-base"), 0.88D, List.of(0.1D, 0.2D, 0.3D, 0.4D), null
        ));
        ReviewItemAggregate adjacentFromConfig = service.ingestClue(new AlertClueCommand(
                "video", "alert-topology-config-002", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(30), "device-02", "camera-02", "zone-b", "vehicle", 15,
                "topology-config-2.jpg", "topology-config-2.mp4", null, List.of("vehicle"), List.of("zone-b"),
                List.of("obj-other-1"), 0.84D, List.of(0.2D, 0.3D, 0.4D, 0.5D), null
        ));
        ReviewItemAggregate sameAreaFromConfig = service.ingestClue(new AlertClueCommand(
                "video", "alert-topology-config-003", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(40), "device-04", "camera-04", "zone-c", "helmet", 15,
                "topology-config-3.jpg", "topology-config-3.mp4", null, List.of("helmet"), List.of("zone-c"),
                List.of("obj-other-2"), 0.81D, List.of(0.3D, 0.4D, 0.5D, 0.6D), null
        ));
        service.ingestClue(new AlertClueCommand(
                "video", "alert-topology-config-004", SupervisionRuleSeeds.RULE_RESTRICTED_AREA, "restricted_area",
                firstTime.plusSeconds(50), "device-03", "camera-03", "zone-d", "smoke", 15,
                "topology-config-4.jpg", "topology-config-4.mp4", null, List.of("smoke"), List.of("zone-d"),
                List.of("obj-other-3"), 0.75D, List.of(0.5D, 0.6D, 0.7D, 0.8D), null
        ));

        List<ReviewItemAggregate> candidates = service.suggestReviewCaseCandidates(base.id());

        assertEquals(List.of(adjacentFromConfig.id(), sameAreaFromConfig.id()),
                candidates.stream().map(ReviewItemAggregate::id).toList());
        Map<?, ?> adjacentMatch = (Map<?, ?>) candidates.get(0).reviewData().get("caseCandidateMatch");
        assertEquals("configured_camera_topology", adjacentMatch.get("source"));
        assertEquals("yard-east", adjacentMatch.get("regulatoryArea"));
        assertEquals(List.of("camera-01"), adjacentMatch.get("adjacentCameras"));
    }

    @Test
    void workbenchQueryAndSummarySupportEvidenceEventCaseAndReviewerPerspective() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7100L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                new CapturingRecordEvidenceResolver(Optional.empty()),
                eventId -> Optional.of(new EventProjection(eventId, SupervisionEventStatusEnum.ACCEPTED.getCode(), "not_checked", "missing_soft"))
        );
        LocalDateTime now = LocalDateTime.of(2026, 6, 30, 13, 40);
        ReviewItemAggregate missing = service.ingestClue(newClue("alert-summary-001", now, "summary-1.jpg", null));
        ReviewItemAggregate converted = service.ingestClue(newClue("alert-summary-002", now.plusMinutes(10), "summary-2.jpg", "summary-2.mp4"));
        service.markReviewed(new ReviewOperationCommand(missing.id(), 9001L, null));
        service.convertToEvent(new ReviewToEventCommand(converted.id(), 9002L));
        service.createReviewCase(new ReviewCaseCommand("investigation", missing.id(), List.of(missing.id())));

        List<ReviewItemAggregate> filtered = service.listWorkbench(new ReviewQuery(
                null,
                "camera-01",
                "zone-a",
                "person",
                "missing",
                false,
                true,
                9001L,
                now.minusMinutes(1),
                now.plusMinutes(1)
        ));
        ReviewWorkbenchSummary summary = service.getWorkbenchSummary(new ReviewQuery(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                9001L,
                null,
                null
        ));

        assertEquals(List.of(missing.id()), filtered.stream().map(ReviewItemAggregate::id).toList());
        assertEquals(2, summary.total());
        assertEquals(1, summary.reviewedByMe());
        assertEquals(1, summary.missingRecord());
        assertEquals(1, summary.converted());
        assertEquals(1, summary.inReviewCase());
        assertEquals("pending_evidence", service.listWorkbench(null).stream()
                .filter(item -> Objects.equals(converted.id(), item.id()))
                .findFirst()
                .orElseThrow()
                .eventReviewStatus());
    }

    @Test
    void userReviewStatusTracksMultipleReviewersIndependently() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-user-status",
                LocalDateTime.of(2026, 6, 30, 14, 0),
                "status.jpg",
                "status.mp4"
        ));

        ReviewUserStatusView reviewerOne = service.markUserReviewStatus(new ReviewUserStatusCommand(
                item.id(),
                9001L,
                true
        ));
        ReviewUserStatusView reviewerTwo = service.markUserReviewStatus(new ReviewUserStatusCommand(
                item.id(),
                9002L,
                false
        ));
        ReviewWorkbenchSummary reviewerOneSummary = service.getWorkbenchSummary(new ReviewQuery(
                null, null, null, null, null, null, null, 9001L, null, null
        ));
        ReviewWorkbenchSummary reviewerTwoSummary = service.getWorkbenchSummary(new ReviewQuery(
                null, null, null, null, null, null, null, 9002L, null, null
        ));

        assertTrue(reviewerOne.hasBeenReviewed());
        assertFalse(reviewerTwo.hasBeenReviewed());
        assertEquals(1, reviewerOneSummary.reviewedByMe());
        assertEquals(0, reviewerTwoSummary.reviewedByMe());
        assertEquals("pending_review", service.listWorkbench(null).get(0).reviewStatus());
    }

    @Test
    void ruleSuggestionCanBePreviewedButCannotRollbackBeforeApplication() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                ruleStore,
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-preview",
                LocalDateTime.of(2026, 6, 30, 14, 5),
                "preview.jpg",
                "preview.mp4"
        ));
        service.markFalsePositive(new ReviewOperationCommand(item.id(), 9001L, "zone too wide"));

        RuleSuggestionPreview preview = service.previewRuleSuggestion(item.id());
        IllegalStateException rejectedRollback = assertThrows(IllegalStateException.class, () ->
                service.revertRuleSuggestion(new RuleSuggestionOperationCommand(
                        item.id(),
                        9002L,
                        "reverted",
                        "rollback before application"
                )));

        assertEquals(item.id(), preview.reviewItemId());
        assertEquals("suppress_label_zone", preview.proposedRule().get("action"));
        assertTrue(preview.diff().stream().anyMatch(line -> line.contains("zone-a")));
        assertTrue(rejectedRollback.getMessage().contains("applied"));
        assertEquals(List.of(), ruleStore.listAll());
    }

    @Test
    void semanticSearchRanksReviewItemsByDetectionEvidenceContext() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 14, 10);
        ReviewItemAggregate helmet = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-semantic-helmet",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "doorway",
                "person",
                15,
                "helmet.jpg",
                "helmet.mp4",
                null,
                List.of("person", "helmet"),
                List.of("doorway"),
                List.of("obj-helmet"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-semantic"
        ));
        service.ingestClue(new AlertClueCommand(
                "video",
                "alert-semantic-vehicle",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "vehicle",
                alertTime.plusMinutes(10),
                "device-02",
                "camera-02",
                "parking",
                "car",
                5,
                "car.jpg",
                "car.mp4",
                null
        ));

        List<ReviewSemanticHit> hits = service.semanticSearch(new ReviewSemanticSearchCommand(
                "helmet doorway person",
                new ReviewQuery(null, null, null, null),
                5
        ));

        assertEquals(helmet.id(), hits.get(0).item().id());
        assertTrue(hits.get(0).score() > 0);
        assertTrue(hits.get(0).matchedTerms().contains("helmet"));
        assertTrue(hits.get(0).snippet().contains("doorway"));
    }

    @Test
    void semanticSearchCanUseExternalProviderBeforeLocalKeywordFallback() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> {
                            ReviewItemAggregate vehicle = request.candidates().stream()
                                    .map(ReviewIntelligenceProvider.ReviewSemanticSearchCandidate::item)
                                    .filter(item -> Objects.equals("car", item.objectLabel()))
                                    .findFirst()
                                    .orElseThrow();
                            assertTrue(request.candidates().stream()
                                    .anyMatch(candidate -> candidate.document().contains("corr-provider")));
                            return Optional.of(List.of(new ReviewSemanticHit(
                                    vehicle,
                                    99D,
                                    List.of("provider"),
                                    "provider semantic hit"
                            )));
                        },
                        request -> Optional.empty()
                )
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 14, 30);
        service.ingestClue(new AlertClueCommand(
                "video",
                "alert-provider-helmet",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "doorway",
                "person",
                15,
                "helmet.jpg",
                "helmet.mp4",
                null,
                List.of("person", "helmet"),
                List.of("doorway"),
                List.of("obj-provider"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-provider"
        ));
        ReviewItemAggregate vehicle = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-provider-vehicle",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "vehicle",
                alertTime.plusMinutes(10),
                "device-02",
                "camera-02",
                "parking",
                "car",
                5,
                "car.jpg",
                "car.mp4",
                null
        ));

        List<ReviewSemanticHit> hits = service.semanticSearch(new ReviewSemanticSearchCommand(
                "helmet doorway person",
                new ReviewQuery(null, null, null, null),
                5
        ));

        assertEquals(vehicle.id(), hits.get(0).item().id());
        assertEquals("provider semantic hit", hits.get(0).snippet());
    }

    @Test
    void semanticReindexPersistsLifecycleAndSearchCanUseIndexedDocument() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-semantic-index",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 6, 30, 15, 10),
                "device-01",
                "camera-01",
                "doorway",
                "person",
                15,
                "semantic-index.jpg",
                "semantic-index.mp4",
                null,
                List.of("person", "helmet"),
                List.of("doorway"),
                List.of("obj-semantic"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-semantic-index"
        ));

        List<ReviewSemanticIndexEntry> indexed = service.reindexSemanticIndex(new ReviewQuery(
                null,
                "camera-01",
                null,
                null
        ));
        List<ReviewSemanticHit> hits = service.semanticSearch(new ReviewSemanticSearchCommand(
                "helmet doorway",
                new ReviewQuery(null, "camera-01", null, null),
                5
        ));

        assertEquals(1, indexed.size());
        assertEquals(item.id(), indexed.get(0).reviewItemId());
        assertEquals("indexed", indexed.get(0).indexStatus());
        assertEquals("camera-01", indexed.get(0).cameraId());
        assertTrue(indexed.get(0).document().contains("corr-semantic-index"));
        assertEquals("review-item:" + item.id(), indexed.get(0).embeddingKey());
        assertEquals("yfeieye-review-local-v1", indexed.get(0).embeddingModel());
        assertTrue(indexed.get(0).embeddingVectorHash().startsWith("sha256:"));
        assertEquals(item.id(), hits.get(0).item().id());
    }

    @Test
    void semanticTriggerMatchesIndexedItemsAndReturnsActions() {
        InMemoryReviewItemStore store = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                store,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-trigger-helmet",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 7, 1, 10, 10),
                "device-01",
                "camera-01",
                "doorway",
                "person",
                15,
                "trigger.jpg",
                "trigger.mp4",
                null,
                List.of("person", "helmet"),
                List.of("doorway"),
                List.of("obj-trigger"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-trigger"
        ));
        ReviewSemanticIndexEntry semanticIndex = service.reindexSemanticIndex(
                new ReviewQuery(null, "camera-01", null, null)).get(0);

        ReviewSemanticTriggerResult result = service.evaluateSemanticTrigger(new ReviewSemanticTriggerCommand(
                "helmet-doorway",
                "camera-01",
                "description",
                "helmet doorway",
                0.5D,
                List.of("notification", "sub_label", "attribute"),
                new ReviewQuery(null, "camera-01", null, null)
        ));

        assertEquals("helmet-doorway", result.triggerName());
        assertEquals(List.of(item.id()), result.matchedReviewItemIds());
        assertTrue(result.actionPayloads().stream()
                .anyMatch(action -> Objects.equals("notification", action.get("action"))));
        assertTrue(result.actionPayloads().stream()
                .anyMatch(action -> Objects.equals("sub_label", action.get("action"))
                        && Objects.equals("helmet-doorway", action.get("value"))));
        assertTrue(result.actionPayloads().stream()
                .anyMatch(action -> Objects.equals("attribute", action.get("action"))
                        && Objects.equals(item.id(), action.get("reviewItemId"))));
        assertEquals("pending", result.humanConfirmationStatus());
        assertEquals(1, result.hitExplanations().size());
        Map<String, Object> explanation = result.hitExplanations().get(0);
        assertEquals(item.id(), explanation.get("reviewItemId"));
        assertEquals("camera-01", explanation.get("cameraId"));
        assertEquals(List.of("helmet", "doorway"), explanation.get("matchedTerms"));
        assertEquals("person", explanation.get("objectLabel"));
        assertEquals("doorway", explanation.get("zoneCode"));
        assertTrue(String.valueOf(explanation.get("snippet")).contains("helmet"));
        assertEquals(semanticIndex.indexVersion(), explanation.get("indexVersion"));
        assertEquals(semanticIndex.indexStatus(), explanation.get("indexStatus"));
        assertEquals(semanticIndex.indexedAt().toString(), explanation.get("indexedAt"));
        assertEquals(semanticIndex.embeddingModel(), explanation.get("embeddingModel"));
        assertEquals(semanticIndex.embeddingVectorHash(), explanation.get("embeddingVectorHash"));
        assertEquals("semantic-trigger-input-v1", semanticTriggerTraceValue(result, "inputVersion"));
        assertEquals(semanticIndex.indexVersion(), semanticTriggerTraceValue(result, "latestIndexVersion"));
        Map<String, Object> persistedEvaluation = store.semanticTriggerAudits
                .get(result.evaluationId()).get(0).metadata();
        assertEquals("semantic-trigger-input-v1", persistedEvaluation.get("inputVersion"));
        assertEquals(semanticIndex.indexVersion(), persistedEvaluation.get("latestIndexVersion"));
        assertEquals(result.actionPayloads().size(), result.actionPreviews().size());
        assertTrue(result.actionPreviews().stream()
                .anyMatch(preview -> Objects.equals("sub_label", preview.get("action"))
                        && Objects.equals(item.id(), preview.get("reviewItemId"))
                        && Boolean.TRUE.equals(preview.get("previewOnly"))
                        && Boolean.TRUE.equals(preview.get("requiresHumanConfirmation"))
                        && Objects.equals("pending", preview.get("humanConfirmationStatus"))));
        assertTrue(result.actionPayloads().stream()
                .allMatch(action -> Boolean.TRUE.equals(action.get("requiresHumanConfirmation"))
                        && Objects.equals("pending", action.get("humanConfirmationStatus"))));
    }

    @Test
    void semanticTriggerInputHashIsCanonicalAndChangesWithCandidateWindow() {
        InMemoryReviewItemStore store = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                store,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 13, 9, 0);
        service.ingestClue(newClue(
                "alert-semantic-input-hash-1",
                baseTime,
                "semantic-input-hash-1.jpg",
                "semantic-input-hash-1.mp4"
        ));
        ReviewSemanticTriggerCommand command = new ReviewSemanticTriggerCommand(
                "canonical-person-trigger",
                "camera-01",
                "description",
                "person camera-01",
                0.1D,
                List.of("notification", "attribute"),
                new ReviewQuery(null, "camera-01", null, "person", null,
                        null, null, null, baseTime.minusMinutes(5), baseTime.plusMinutes(30)),
                9301L
        );

        ReviewSemanticTriggerResult first = service.evaluateSemanticTrigger(command);
        ReviewSemanticTriggerResult equivalent = service.evaluateSemanticTrigger(command);
        String firstHash = (String) recordTraceValue(first, "inputHash");
        String equivalentHash = (String) recordTraceValue(equivalent, "inputHash");
        service.ingestClue(newClue(
                "alert-semantic-input-hash-2",
                baseTime.plusMinutes(10),
                "semantic-input-hash-2.jpg",
                "semantic-input-hash-2.mp4"
        ));
        ReviewSemanticTriggerResult expandedWindow = service.evaluateSemanticTrigger(command);
        String expandedHash = (String) recordTraceValue(expandedWindow, "inputHash");

        assertNotNull(firstHash);
        assertTrue(firstHash.matches("sha256:[0-9a-f]{64}"));
        assertEquals(firstHash, equivalentHash);
        assertNotEquals(firstHash, expandedHash);
        assertEquals(firstHash, store.semanticTriggerAudits.get(first.evaluationId()).get(0)
                .metadata().get("inputHash"));
    }

    @Test
    void semanticTriggerIndexGenerationRoundTripsWithoutMixingGenerations() {
        InMemoryReviewItemStore store = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                store,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        service.ingestClue(newClue(
                "alert-semantic-generation",
                LocalDateTime.of(2026, 7, 13, 9, 30),
                "semantic-generation.jpg",
                "semantic-generation.mp4"
        ));
        ReviewSemanticIndexEntry indexed = service.reindexSemanticIndex(
                new ReviewQuery(null, "camera-01", null, null)).get(0);
        String generationId = (String) recordTraceValue(indexed, "indexGenerationId");

        ReviewSemanticTriggerResult evaluated = service.evaluateSemanticTrigger(
                new ReviewSemanticTriggerCommand(
                        "generation-person-trigger",
                        "camera-01",
                        "description",
                        "person camera-01",
                        0.1D,
                        List.of("notification"),
                        new ReviewQuery(null, "camera-01", null, null),
                        9302L
                ));
        ReviewSemanticTriggerResult restored = service.getSemanticTrigger(evaluated.evaluationId());
        ReviewSemanticTriggerResult confirmed = service.confirmSemanticTrigger(
                new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                        evaluated.evaluationId(),
                        "confirmed",
                        "generation verified",
                        9303L
                ));
        Map<String, Object> persistedEvaluation = store.semanticTriggerAudits
                .get(evaluated.evaluationId()).get(0).metadata();
        Map<String, Object> persistedDecision = store.semanticTriggerAudits
                .get(evaluated.evaluationId()).get(1).metadata();

        assertNotNull(generationId);
        assertEquals(generationId, recordTraceValue(evaluated, "indexGenerationId"));
        assertEquals(generationId, evaluated.hitExplanations().get(0).get("indexGenerationId"));
        assertTrue(evaluated.hitExplanations().stream()
                .allMatch(hit -> generationId.equals(hit.get("indexGenerationId"))));
        assertEquals(generationId, persistedEvaluation.get("indexGenerationId"));
        assertEquals(generationId, persistedDecision.get("indexGenerationId"));
        assertEquals(generationId, recordTraceValue(restored, "indexGenerationId"));
        assertEquals(generationId, recordTraceValue(confirmed, "indexGenerationId"));
        assertEquals(recordTraceValue(evaluated, "inputHash"), recordTraceValue(restored, "inputHash"));
        assertEquals(recordTraceValue(evaluated, "inputHash"), recordTraceValue(confirmed, "inputHash"));
    }

    @Test
    void semanticTriggerConfirmationRestoresPersistedPreviewAndIsIdempotentWithoutExecutingActions() {
        InMemoryReviewItemStore store = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                store,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-semantic-confirm",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                LocalDateTime.of(2026, 7, 11, 10, 10),
                "device-01",
                "camera-01",
                "doorway",
                "person",
                15,
                "semantic-confirm.jpg",
                "semantic-confirm.mp4",
                null,
                List.of("person", "helmet"),
                List.of("doorway"),
                List.of("obj-semantic-confirm"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-semantic-confirm"
        ));
        ReviewSemanticIndexEntry semanticIndex = service.reindexSemanticIndex(
                new ReviewQuery(null, "camera-01", null, null)).get(0);

        ReviewSemanticTriggerResult evaluated = service.evaluateSemanticTrigger(new ReviewSemanticTriggerCommand(
                "helmet-doorway",
                "camera-01",
                "description",
                "helmet doorway",
                0.5D,
                List.of("notification", "sub_label"),
                new ReviewQuery(null, "camera-01", null, null),
                9001L
        ));

        assertTrue(evaluated.evaluationId().matches(
                "sem-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        assertEquals("pending", evaluated.humanConfirmationStatus());
        assertEquals(List.of(item.id()), evaluated.matchedReviewItemIds());
        assertEquals(2, evaluated.actionPreviews().size());
        assertNull(evaluated.confirmedBy());
        assertNull(evaluated.confirmedAt());
        assertFalse(evaluated.duplicate());

        ReviewSemanticTriggerResult restored = service.getSemanticTrigger(evaluated.evaluationId());

        ReviewSemanticTriggerResult confirmed = service.confirmSemanticTrigger(
                new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                        evaluated.evaluationId(),
                        "confirmed",
                        "operator approved preview only",
                        9100L
                ));
        ReviewSemanticTriggerResult duplicate = service.confirmSemanticTrigger(
                new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                        evaluated.evaluationId(),
                        "confirmed",
                        "client tried to replace persisted note",
                        9999L
                ));
        List<SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord> persistedAudits =
                store.semanticTriggerAudits.get(evaluated.evaluationId());
        Map<String, Object> terminalAudit = persistedAudits.get(persistedAudits.size() - 1).metadata();

        assertEquals(evaluated.matchedReviewItemIds(), confirmed.matchedReviewItemIds());
        assertEquals(evaluated.hitExplanations(), confirmed.hitExplanations());
        assertEquals(evaluated.actionPreviews(), confirmed.actionPreviews());
        assertEquals("semantic-trigger-input-v1", semanticTriggerTraceValue(evaluated, "inputVersion"));
        assertEquals(semanticIndex.indexVersion(), semanticTriggerTraceValue(evaluated, "latestIndexVersion"));
        assertEquals(semanticTriggerTraceValue(evaluated, "inputVersion"),
                semanticTriggerTraceValue(restored, "inputVersion"));
        assertEquals(semanticTriggerTraceValue(evaluated, "latestIndexVersion"),
                semanticTriggerTraceValue(restored, "latestIndexVersion"));
        assertEquals(semanticTriggerTraceValue(evaluated, "inputVersion"),
                semanticTriggerTraceValue(confirmed, "inputVersion"));
        assertEquals(semanticTriggerTraceValue(evaluated, "latestIndexVersion"),
                semanticTriggerTraceValue(confirmed, "latestIndexVersion"));
        assertEquals(semanticTriggerTraceValue(confirmed, "inputVersion"),
                semanticTriggerTraceValue(duplicate, "inputVersion"));
        assertEquals(semanticTriggerTraceValue(confirmed, "latestIndexVersion"),
                semanticTriggerTraceValue(duplicate, "latestIndexVersion"));
        assertEquals("semantic-trigger-input-v1", terminalAudit.get("inputVersion"));
        assertEquals(semanticIndex.indexVersion(), terminalAudit.get("latestIndexVersion"));
        assertEquals("confirmed", confirmed.humanConfirmationStatus());
        assertEquals(9100L, confirmed.confirmedBy());
        assertNotNull(confirmed.confirmedAt());
        assertFalse(confirmed.duplicate());
        assertTrue(duplicate.duplicate());
        assertEquals(9100L, duplicate.confirmedBy());
        assertEquals(confirmed.confirmedAt(), duplicate.confirmedAt());
        assertEquals(item.reviewData(), store.findById(item.id()).orElseThrow().reviewData(),
                "confirmation records approval only and must not execute the previewed action");

        IllegalStateException conflictingDecision = assertThrows(IllegalStateException.class,
                () -> service.confirmSemanticTrigger(
                        new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                                evaluated.evaluationId(),
                                "rejected",
                                "cannot overwrite terminal decision",
                                9200L
                        )));
        assertTrue(conflictingDecision.getMessage().contains("semantic_trigger_already_decided"));

        ReviewSemanticTriggerResult secondEvaluation = service.evaluateSemanticTrigger(
                new ReviewSemanticTriggerCommand(
                        "helmet-doorway-reject",
                        "camera-01",
                        "description",
                        "helmet doorway",
                        0.5D,
                        List.of("notification"),
                        new ReviewQuery(null, "camera-01", null, null),
                        9001L
                ));
        ReviewSemanticTriggerResult rejected = service.confirmSemanticTrigger(
                new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                        secondEvaluation.evaluationId(),
                        "rejected",
                        "preview would create noise",
                        9300L
                ));
        ReviewSemanticTriggerResult duplicateRejection = service.confirmSemanticTrigger(
                new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                        secondEvaluation.evaluationId(),
                        "rejected",
                        "duplicate",
                        9400L
                ));
        assertEquals("rejected", rejected.humanConfirmationStatus());
        assertEquals(9300L, rejected.confirmedBy());
        assertFalse(rejected.duplicate());
        assertTrue(duplicateRejection.duplicate());
        assertEquals(9300L, duplicateRejection.confirmedBy());
    }

    @Test
    void semanticTriggerConfirmationRejectsUnknownEvaluationAndInvalidDecision() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );

        assertThrows(IllegalArgumentException.class,
                () -> service.confirmSemanticTrigger(
                        new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                                "guessable-id", "confirmed", null, 9100L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.confirmSemanticTrigger(
                        new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                                "sem-123e4567-e89b-42d3-a456-426614174000", "executed", null, 9100L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.confirmSemanticTrigger(
                        new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                                "sem-123e4567-e89b-42d3-a456-426614174000", "confirmed", null, 9100L)));
    }

    @Test
    void semanticTriggerAppliesNormalizedThresholdAndRejectsUnknownActions() {
        InMemoryReviewItemStore store = new InMemoryReviewItemStore();
        ReviewIntelligenceProvider provider = new StubReviewIntelligenceProvider(
                request -> Optional.of(request.candidates().stream()
                        .findFirst()
                        .map(candidate -> List.of(new ReviewSemanticHit(
                                candidate.item(),
                                0.2D,
                                List.of("helmet"),
                                "helmet detected"
                        )))
                        .orElse(List.of())),
                request -> Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                store,
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                provider
        );
        service.ingestClue(newClue(
                "alert-semantic-threshold",
                LocalDateTime.of(2026, 7, 11, 11, 0),
                "semantic-threshold.jpg",
                "semantic-threshold.mp4"
        ));

        ReviewSemanticTriggerResult belowThreshold = service.evaluateSemanticTrigger(
                new ReviewSemanticTriggerCommand(
                        "strict-threshold",
                        "camera-01",
                        "description",
                        "helmet",
                        0.95D,
                        List.of("notification"),
                        new ReviewQuery(null, "camera-01", null, null),
                        9001L
                ));
        ReviewSemanticTriggerResult aboveThreshold = service.evaluateSemanticTrigger(
                new ReviewSemanticTriggerCommand(
                        "relaxed-threshold",
                        "camera-01",
                        "description",
                        "helmet",
                        0.1D,
                        List.of("notification"),
                        new ReviewQuery(null, "camera-01", null, null),
                        9001L
                ));

        assertTrue(belowThreshold.matchedReviewItemIds().isEmpty());
        assertTrue(belowThreshold.actionPreviews().isEmpty());
        assertEquals(1, aboveThreshold.matchedReviewItemIds().size());
        assertEquals(0, semanticTriggerTraceValue(aboveThreshold, "latestIndexVersion"));
        assertEquals(0, aboveThreshold.hitExplanations().get(0).get("indexVersion"));
        assertEquals("live_fallback", aboveThreshold.hitExplanations().get(0).get("indexStatus"));
        assertThrows(IllegalArgumentException.class,
                () -> service.evaluateSemanticTrigger(new ReviewSemanticTriggerCommand(
                        "unknown-action",
                        "camera-01",
                        "description",
                        "helmet",
                        0.1D,
                        List.of("execute_arbitrary_plugin"),
                        new ReviewQuery(null, "camera-01", null, null),
                        9001L
                )));
    }

    @Test
    void semanticTriggerCanBeRestoredAfterRefreshAndIgnoresCorruptTerminalAudit() {
        InMemoryReviewItemStore store = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                store,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        service.ingestClue(newClue(
                "alert-semantic-restore",
                LocalDateTime.of(2026, 7, 11, 11, 30),
                "semantic-restore.jpg",
                "semantic-restore.mp4"
        ));
        service.reindexSemanticIndex(new ReviewQuery(null, "camera-01", null, null));
        ReviewSemanticTriggerResult evaluated = service.evaluateSemanticTrigger(
                new ReviewSemanticTriggerCommand(
                        "restore-after-refresh",
                        "camera-01",
                        "description",
                        "person camera",
                        0.1D,
                        List.of("notification"),
                        new ReviewQuery(null, "camera-01", null, null),
                        9001L
                ));
        store.semanticTriggerAudits.get(evaluated.evaluationId()).add(
                new SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord(
                        evaluated.matchedReviewItemIds().stream().findFirst().orElse(null),
                        "semantic_trigger_confirmed",
                        6666L,
                        LocalDateTime.of(2026, 7, 11, 11, 31),
                        Map.of()
                ));

        ReviewSemanticTriggerResult restored = service.getSemanticTrigger(evaluated.evaluationId());
        ReviewSemanticTriggerResult confirmed = service.confirmSemanticTrigger(
                new SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand(
                        evaluated.evaluationId(),
                        "confirmed",
                        "valid approval",
                        9100L
                ));

        assertEquals("pending", restored.humanConfirmationStatus());
        assertEquals(evaluated.actionPreviews(), restored.actionPreviews());
        assertEquals("confirmed", confirmed.humanConfirmationStatus());
        assertEquals(9100L, confirmed.confirmedBy());
        assertFalse(confirmed.duplicate());
    }

    @Test
    void shiftReportSummarizesReviewItemsAndEvidenceGaps() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> Optional.empty(),
                noEventProjectionStore()
        );
        LocalDateTime shiftStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        ReviewItemAggregate missing = service.ingestClue(newClue("alert-report-missing", shiftStart.plusMinutes(15), "report-missing.jpg", null));
        ReviewItemAggregate found = service.ingestClue(newClue("alert-report-found", shiftStart.plusMinutes(45), "report-found.jpg", "report-found.mp4"));

        ReviewOperationsReport report = service.generateReviewReport(new ReviewReportCommand(
                "shift",
                new ReviewQuery(null, "camera-01", null, null, null, null, null, null, shiftStart, shiftStart.plusHours(1)),
                shiftStart,
                shiftStart.plusHours(1),
                9001L
        ));

        assertEquals("shift", report.reportType());
        assertEquals(List.of(missing.id(), found.id()), report.reviewItemIds());
        assertTrue(report.title().contains("shift"));
        assertTrue(report.summary().contains("2 review item"));
        assertEquals(shiftStart.toString(), report.structuredData().get("periodStart"));
        assertEquals(shiftStart.plusHours(1).toString(), report.structuredData().get("periodEnd"));
        assertEquals(1, report.structuredData().get("evidenceGapCount"));
        assertEquals("camera-01", report.structuredData().get("responsibilityUnit"));
        assertEquals("pending", report.deliveryPlan().get("deliveryStatus"));
        assertEquals(List.of("dashboard", "supervision_console"), report.deliveryPlan().get("channels"));
        assertEquals(true, report.deliveryPlan().get("requiresOperatorAcknowledgement"));
        assertEquals("pending", report.acknowledgement().get("status"));
        assertEquals(true, report.acknowledgement().get("required"));
        assertEquals(9001L, report.acknowledgement().get("requestedBy"));
        assertEquals(report.deliveryPlan(), report.structuredData().get("deliveryPlan"));
        assertEquals(report.acknowledgement(), report.structuredData().get("acknowledgement"));
    }

    @Test
    void scheduledReportKeyIsStableForLateDataAndUsesHalfOpenWindows() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> Optional.empty(),
                noEventProjectionStore()
        );
        LocalDateTime periodStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime periodEnd = periodStart.plusHours(8);
        service.ingestClue(newClue(
                "alert-report-first", periodStart.plusHours(1), "report-first.jpg", "report-first.mp4"));

        ReviewOperationsReport initial = service.generateReviewReport(new ReviewReportCommand(
                "shift", null, periodStart, periodEnd, null));
        String initialKey = String.valueOf(initial.deliveryPlan().get("reportKey"));

        service.ingestClue(newClue(
                "alert-report-late", periodStart.plusHours(2), "report-late.jpg", "report-late.mp4"));
        ReviewItemAggregate boundary = service.ingestClue(newClue(
                "alert-report-boundary", periodEnd, "report-boundary.jpg", "report-boundary.mp4"));
        ReviewOperationsReport updated = service.generateReviewReport(new ReviewReportCommand(
                "shift", null, periodStart, periodEnd, null));
        ReviewOperationsReport next = service.generateReviewReport(new ReviewReportCommand(
                "shift", null, periodEnd, periodEnd.plusHours(8), null));
        ReviewOperationsReport exactCamera = service.generateReviewReport(new ReviewReportCommand(
                "shift",
                new ReviewQuery(null, "camera-01", null, null, null, null, null, null,
                        periodStart, periodEnd),
                periodStart,
                periodEnd,
                null));
        ReviewOperationsReport whitespaceCamera = service.generateReviewReport(new ReviewReportCommand(
                "shift",
                new ReviewQuery(null, " camera-01 ", null, null, null, null, null, null,
                        periodStart, periodEnd),
                periodStart,
                periodEnd,
                null));

        assertEquals(initialKey, updated.deliveryPlan().get("reportKey"));
        assertFalse(updated.reviewItemIds().contains(boundary.id()));
        assertTrue(next.reviewItemIds().contains(boundary.id()));
        assertNotEquals(exactCamera.deliveryPlan().get("reportKey"),
                whitespaceCamera.deliveryPlan().get("reportKey"));
    }

    @Test
    void operationsReportAcknowledgementPersistsForSameReportScopeAndIsIdempotent() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> Optional.empty(),
                noEventProjectionStore()
        );
        LocalDateTime shiftStart = LocalDateTime.of(2026, 7, 1, 8, 0);
        LocalDateTime shiftEnd = shiftStart.plusHours(1);
        ReviewQuery query = new ReviewQuery(null, "camera-01", null, null, null, null, null, null, shiftStart, shiftEnd);
        service.ingestClue(newClue("alert-report-ack", shiftStart.plusMinutes(10), "report-ack.jpg", null));

        ReviewOperationsReport pending = service.generateReviewReport(new ReviewReportCommand(
                "shift",
                query,
                shiftStart,
                shiftEnd,
                9001L
        ));

        String reportKey = String.valueOf(pending.acknowledgement().get("reportKey"));
        assertEquals("pending", pending.acknowledgement().get("status"));
        assertTrue(reportKey.startsWith("report-"));

        ReviewReportAcknowledgement acknowledged = service.acknowledgeReviewReport(new ReviewReportAcknowledgementCommand(
                "shift",
                query,
                null,
                null,
                9002L,
                "shift leader reviewed"
        ));

        assertEquals(reportKey, acknowledged.reportKey());
        assertEquals("shift", acknowledged.reportType());
        assertEquals("acknowledged", acknowledged.status());
        assertEquals(9002L, acknowledged.acknowledgedBy());
        assertEquals("shift leader reviewed", acknowledged.note());
        assertFalse(acknowledged.duplicate());
        assertEquals(shiftStart.toString(), acknowledged.metadata().get("periodStart"));
        assertEquals(shiftEnd.toString(), acknowledged.metadata().get("periodEnd"));

        ReviewOperationsReport reloaded = service.generateReviewReport(new ReviewReportCommand(
                "shift",
                query,
                shiftStart,
                shiftEnd,
                9001L
        ));

        assertEquals("acknowledged", reloaded.acknowledgement().get("status"));
        assertEquals(reportKey, reloaded.acknowledgement().get("reportKey"));
        assertEquals(9002L, reloaded.acknowledgement().get("acknowledgedBy"));
        assertEquals("shift leader reviewed", reloaded.acknowledgement().get("note"));
        assertEquals(reloaded.acknowledgement(), reloaded.structuredData().get("acknowledgement"));

        ReviewReportAcknowledgement duplicate = service.acknowledgeReviewReport(new ReviewReportAcknowledgementCommand(
                "shift",
                query,
                shiftStart,
                shiftEnd,
                9003L,
                "second click"
        ));
        assertTrue(duplicate.duplicate());
        assertEquals(9002L, duplicate.acknowledgedBy());
        assertEquals("shift leader reviewed", duplicate.note());
    }

    @Test
    void dailyReportAggregatesOperationalMetricsByUnitAreaCameraAndRule() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> Optional.empty(),
                noEventProjectionStore()
        );
        LocalDateTime dayStart = LocalDateTime.of(2026, 7, 1, 0, 0);
        ReviewItemAggregate missing = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-report-daily-missing",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                dayStart.plusHours(1),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                "daily-missing.jpg",
                null,
                "payload-report-daily-missing"
        ));
        ReviewItemAggregate falsePositive = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-report-daily-fp",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                dayStart.plusHours(2),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                12,
                "daily-fp.jpg",
                "daily-fp.mp4",
                "payload-report-daily-fp"
        ));
        service.markFalsePositive(new ReviewOperationCommand(falsePositive.id(), 9002L, "known staff"));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "daily report case",
                missing.id(),
                List.of(missing.id(), falsePositive.id())
        ));
        ReviewEvidenceExportJob exportJob = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(missing.id(), falsePositive.id()),
                9001L,
                "manifest"
        ));
        itemStore.replaceExportJobStatus(exportJob.jobNo(), SupervisionAlertReviewService.EXPORT_JOB_FAILED);

        ReviewOperationsReport report = service.generateReviewReport(new ReviewReportCommand(
                "daily",
                new ReviewQuery(null, null, null, null, null, null, null, null, dayStart, dayStart.plusDays(1)),
                dayStart,
                dayStart.plusDays(1),
                9001L
        ));

        Map<String, Object> data = report.structuredData();
        assertEquals("daily", report.reportType());
        assertEquals(2, data.get("reviewItemCount"));
        assertEquals(1, data.get("missingRecordCount"));
        assertEquals(0.5D, data.get("missingRecordRate"));
        assertEquals(1, data.get("unreviewedBacklogCount"));
        assertEquals(0.5D, data.get("unreviewedBacklogRate"));
        assertEquals(1, data.get("falsePositiveCount"));
        assertEquals(0.5D, data.get("falsePositiveRate"));
        assertEquals(2, data.get("semanticBacklogCount"));
        assertEquals(1, data.get("exportJobCount"));
        assertEquals(1, data.get("exportFailureCount"));
        assertEquals(1.0D, data.get("exportFailureRate"));
        Map<?, ?> responsibilityDimensions = (Map<?, ?>) data.get("responsibilityUnitDimensions");
        Map<?, ?> cameraDimensions = (Map<?, ?>) data.get("cameraDimensions");
        Map<?, ?> areaDimensions = (Map<?, ?>) data.get("areaDimensions");
        Map<?, ?> ruleDimensions = (Map<?, ?>) data.get("ruleDimensions");
        assertEquals(1, ((Map<?, ?>) responsibilityDimensions.get("camera-01")).get("missingRecordCount"));
        assertEquals(1, ((Map<?, ?>) responsibilityDimensions.get("camera-01")).get("unreviewedBacklogCount"));
        assertEquals(0.0D, ((Map<?, ?>) cameraDimensions.get("camera-02")).get("unreviewedBacklogRate"));
        assertEquals(1, ((Map<?, ?>) cameraDimensions.get("camera-02")).get("falsePositiveCount"));
        assertEquals(1, ((Map<?, ?>) areaDimensions.get("zone-a")).get("missingRecordCount"));
        assertEquals(1, ((Map<?, ?>) ruleDimensions.get(SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING)).get("falsePositiveCount"));
    }

    @Test
    void operationsReportJobGeneratesScheduledShiftAndDailyReports() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewServiceImpl service = (SupervisionAlertReviewServiceImpl) newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> Optional.empty(),
                noEventProjectionStore()
        );
        service.setRuntimeOutboxPublisher(message -> ReviewRuntimeOutboxDeliveryResult.delivered());
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        LocalDateTime shiftEnd = now.toLocalDate().atTime((now.getHour() / 8) * 8, 0);
        LocalDateTime alertTime = shiftEnd.minusHours(1);
        service.ingestClue(newClue("alert-report-job", alertTime, "report-job.jpg", null));

        SupervisionAlertReviewOperationsReportJob job = new SupervisionAlertReviewOperationsReportJob(service);
        String shiftSummary = job.execute("");

        assertTrue(shiftSummary.contains("reportType=shift"));
        assertTrue(shiftSummary.contains("scheduled=true"));
        assertTrue(shiftSummary.contains("items=1"));
        assertTrue(shiftSummary.contains("deliveryStatus=pending"));
        assertTrue(shiftSummary.contains("acknowledgement=pending"));
        assertTrue(shiftSummary.contains("deliveryOutbox=1"));
        List<RuntimeOutboxEntry> reportDeliveries = itemStore.runtimeOutbox().stream()
                .filter(entry -> "deliver_operations_report".equals(entry.action()))
                .toList();
        assertEquals(1, reportDeliveries.size());
        RuntimeOutboxEntry reportDelivery = reportDeliveries.get(0);
        assertEquals("pending", reportDelivery.status());
        assertEquals("shift", reportDelivery.metadata().get("reportType"));
        assertEquals("pending", reportDelivery.metadata().get("acknowledgementStatus"));
        assertEquals(Boolean.TRUE, reportDelivery.metadata().get("scheduled"));
        assertTrue(String.valueOf(reportDelivery.metadata().get("reportKey")).startsWith("report-"));
        String outboxSummary = new SupervisionAlertReviewRuntimeOutboxJob(service).execute("10");
        assertTrue(outboxSummary.contains("published=1"));
        assertTrue(itemStore.runtimeOutbox().stream()
                .allMatch(entry -> "published".equals(entry.status())));
        String duplicateShiftSummary = job.execute("");
        assertTrue(duplicateShiftSummary.contains("deliveryOutbox=0"));
        assertEquals(1, itemStore.runtimeOutbox().stream()
                .filter(entry -> "shift".equals(entry.metadata().get("reportType")))
                .filter(entry -> "deliver_operations_report".equals(entry.action()))
                .count());
        String dailySummary = job.execute("daily");
        assertTrue(dailySummary.contains("reportType=daily"));
        assertTrue(dailySummary.contains("scheduled=true"));
    }

    @Test
    void aiSummaryAndEvidenceExportUseCaseTimelineEvidenceCoverageAndActions() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 30, 14, 20);
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7300L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                new CapturingRecordEvidenceResolver(Optional.empty()),
                noEventProjectionStore()
        );
        ReviewItemAggregate missing = service.ingestClue(newClue("alert-ai-missing", baseTime, "ai-missing.jpg", null));
        ReviewItemAggregate converted = service.ingestClue(newClue("alert-ai-event", baseTime.plusMinutes(4), "ai-event.jpg", "ai-event.mp4"));
        assertEquals(missing.id(), converted.id());
        service.markFalsePositive(new ReviewOperationCommand(missing.id(), 9001L, "shadow"));
        service.convertToEvent(new ReviewToEventCommand(converted.id(), 9002L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI summary case",
                missing.id(),
                List.of(missing.id(), converted.id())
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9003L);
        ReviewEvidenceExportPackage exportPackage = service.exportReviewEvidence(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                null,
                9003L,
                "manifest"
        ));

        assertEquals(reviewCase.id(), summary.reviewCaseId());
        assertTrue(summary.keyFacts().stream().anyMatch(fact -> fact.contains("camera-01")));
        assertTrue(summary.evidenceGaps().stream().anyMatch(gap -> gap.contains("missing")));
        assertEquals("medium", summary.structuredData().get("threatLevel"));
        assertEquals("camera-01", summary.structuredData().get("responsibilityUnit"));
        assertEquals(true, summary.structuredData().get("convertibleToEvent"));
        assertEquals("camera-01", summary.structuredData().get("scene"));
        assertEquals(summary.summary(), summary.structuredData().get("shortSummary"));
        assertEquals("incomplete", summary.structuredData().get("evidenceCompleteness"));
        assertTrue(((List<?>) summary.structuredData().get("concerns")).stream()
                .anyMatch(concern -> String.valueOf(concern).contains("missing")));
        assertTrue(((List<?>) summary.structuredData().get("disposalSuggestion")).stream()
                .anyMatch(action -> String.valueOf(action).contains("backfill")));
        assertEquals("manifest", exportPackage.format());
        assertEquals(List.of(missing.id()), exportPackage.reviewItemIds());
        assertTrue(exportPackage.evidenceUris().contains("ai-missing.jpg"));
        assertTrue(exportPackage.timeline().stream().anyMatch(item -> "converted_to_event:7300".equals(item.materialUri())));
        assertTrue(exportPackage.manifest().containsKey("checksum"));
    }

    @Test
    void evidenceExportCreatesPendingJobWithIntegrityAuditAndEventBinding() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7400L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                noEventProjectionStore()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-job",
                LocalDateTime.of(2026, 6, 30, 15, 20),
                "export-job.jpg",
                "export-job.mp4"
        ));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9002L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "export job case",
                item.id(),
                List.of(item.id())
        ));

        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9003L,
                "manifest",
                "regulator handoff",
                9005L,
                "approved by duty supervisor"
        ));

        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_PENDING, job.status());
        assertEquals(reviewCase.id(), job.exportPackage().reviewCaseId());
        assertEquals(List.of(item.id()), job.exportPackage().reviewItemIds());
        assertEquals(9003L, job.operatorUserId());
        assertEquals("regulator handoff", job.reason());
        assertEquals(List.of(7400L), job.boundEventIds());
        assertTrue(job.fileHash().startsWith("sha256:"));
        assertTrue(job.expiresAt().isAfter(job.createdAt()));
        Map<String, Object> manifest = job.exportPackage().manifest();
        assertTrue(String.valueOf(manifest.get("packageChecksum")).startsWith("sha256:"));
        assertTrue(String.valueOf(manifest.get("manifestHash")).startsWith("sha256:"));
        assertEquals(9003L, manifest.get("generatedBy"));
        assertEquals(job.expiresAt().toString(), manifest.get("expiresAt"));
        assertEquals(List.of(7400L), ((List<?>) manifest.get("eventReferences")).stream()
                .map(reference -> ((Map<?, ?>) reference).get("eventId"))
                .toList());
        assertTrue(((List<?>) manifest.get("files")).stream()
                .anyMatch(file -> "export-job.mp4".equals(((Map<?, ?>) file).get("uri"))
                        && String.valueOf(((Map<?, ?>) file).get("hash")).startsWith("sha256:")));
        assertEquals(9003L, ((Map<?, ?>) manifest.get("approval")).get("approvedBy"));
        assertEquals("approved by duty supervisor", ((Map<?, ?>) manifest.get("approval")).get("approvalNote"));
        assertTrue(String.valueOf(((Map<?, ?>) manifest.get("immutableAudit")).get("headHash"))
                .startsWith("sha256:"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(timeline -> "case_audit".equals(timeline.materialType())
                        && "export_evidence_job".equals(timeline.materialUri())
                        && timeline.actionNote().contains(job.jobNo())));
    }

    @Test
    void evidenceExportJobCreationPersistsPendingDraftWithoutCallingVideo() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "must-not-run-during-create",
                        "https://eye.yfeiai.com/exports/must-not-run-during-create.mp4",
                        "ready",
                        "unexpected synchronous export"
                ))
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-draft",
                LocalDateTime.of(2026, 7, 10, 9, 0),
                "export-draft.jpg",
                "export-draft.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "pending export draft",
                item.id(),
                List.of(item.id())
        ));

        ReviewEvidenceExportJob created = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9200L,
                "mp4",
                "double-click safe export"
        ));

        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_PENDING, created.status());
        assertTrue(videoExportProvider.requests().isEmpty());
        assertEquals(List.of(), created.exportPackage().manifest().get("videoExports"));
    }

    @Test
    void evidenceExportJobCreationIsIdempotentForRepeatedRequest() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-idempotent",
                LocalDateTime.of(2026, 7, 10, 9, 10),
                "export-idempotent.jpg",
                "export-idempotent.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "idempotent export",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportCommand command = new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9201L,
                "mp4",
                "same double click"
        );

        ReviewEvidenceExportJob first = service.createReviewEvidenceExportJob(command);
        ReviewEvidenceExportJob duplicate = service.createReviewEvidenceExportJob(command);

        assertEquals(first.jobNo(), duplicate.jobNo());
        assertEquals(1, itemStore.listExportJobs(reviewCase.id()).size());
        assertEquals(first.exportPackage().manifest().get("requestKey"),
                duplicate.exportPackage().manifest().get("requestKey"));
    }

    @Test
    void evidenceExportRejectsReviewItemsOutsideTheRequestedCase() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 10, 9, 15);
        ReviewItemAggregate caseItem = service.ingestClue(newClue(
                "alert-export-case-member",
                alertTime,
                "case-member.jpg",
                "case-member.mp4"
        ));
        ReviewItemAggregate externalItem = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-export-case-external",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                alertTime.plusMinutes(1),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                3,
                "case-external.jpg",
                "case-external.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "case membership export",
                caseItem.id(),
                List.of(caseItem.id())
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                        reviewCase.id(),
                        List.of(caseItem.id(), externalItem.id()),
                        9201L,
                        "manifest",
                        "must stay inside case"
                )));

        assertTrue(error.getMessage().contains("review_item_not_in_case"));
        assertTrue(itemStore.listExportJobs(reviewCase.id()).isEmpty());
    }

    @Test
    void evidenceExportBindsApproverToAuthenticatedOperator() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-approver",
                LocalDateTime.of(2026, 7, 10, 9, 16),
                "approver.jpg",
                "approver.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "approver binding",
                item.id(),
                List.of(item.id())
        ));

        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9202L,
                "manifest",
                "approval must be authenticated",
                9999L,
                "spoofed approver"
        ));

        assertEquals(9202L, ((Map<?, ?>) job.exportPackage().manifest().get("approval")).get("approvedBy"));
    }

    @Test
    void evidenceExportRejectsUnsupportedFormatBeforeCallingVideo() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-format",
                LocalDateTime.of(2026, 7, 10, 9, 17),
                "format.jpg",
                "format.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "format allowlist",
                item.id(),
                List.of(item.id())
        ));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.exportReviewEvidence(new ReviewEvidenceExportCommand(
                        reviewCase.id(),
                        List.of(item.id()),
                        9203L,
                        "../../executable",
                        "invalid format"
                )));

        assertTrue(error.getMessage().contains("unsupported_export_format"));
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportRejectsUnauthorizedCaseLevelMediaWithoutReviewItemId() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of("camera-01")
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-case-media",
                LocalDateTime.of(2026, 7, 10, 9, 18),
                "case-media.jpg",
                "case-media.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "case level media permission",
                item.id(),
                List.of(item.id())
        ));
        itemStore.addCaseLevelEvidence(
                reviewCase.id(),
                "camera-02",
                "record",
                "forbidden-case-media.mp4",
                LocalDateTime.of(2026, 7, 10, 9, 19)
        );

        SecurityException error = assertThrows(SecurityException.class,
                () -> service.exportReviewEvidence(new ReviewEvidenceExportCommand(
                        reviewCase.id(),
                        List.of(item.id()),
                        9204L,
                        "manifest",
                        "case-level media must be scoped",
                        null,
                        null,
                        List.of("camera-01", "camera-02")
                )));

        assertTrue(error.getMessage().contains("camera_not_allowed"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .anyMatch(row -> "media_access_denied".equals(row.materialUri())
                        && row.actionNote().contains("forbidden-case-media.mp4")));
    }

    @Test
    void evidenceExportWorkerAggregatesOrderedCaseCameraRecordSegmentsIntoOneVideoRequest() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "video-export-stitched",
                        "https://eye.yfeiai.com/exports/video-export-stitched.mp4",
                        "ready",
                        "two segments stitched",
                        "https://eye.yfeiai.com/exports/video-export-stitched/manifest",
                        validSha256('d'),
                        List.of(
                                Map.of(
                                        "originalRecordUri", "segment-a.mp4",
                                        "sourceHash", validSha256('e'),
                                        "ffmpegCommandHash", validSha256('f'),
                                        "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                        "stitchOrder", 0
                                ),
                                Map.of(
                                        "originalRecordUri", "segment-b.mp4",
                                        "sourceHash", validSha256('1'),
                                        "ffmpegCommandHash", validSha256('2'),
                                        "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                        "stitchOrder", 1
                                )
                        ),
                        validSha256('3')
                ))
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime firstAlertTime = LocalDateTime.of(2026, 7, 10, 9, 20);
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-export-segment-a",
                firstAlertTime,
                "segment-a.jpg",
                "segment-a.mp4"
        ));
        ReviewItemAggregate second = service.ingestClue(newClue(
                "alert-export-segment-b",
                firstAlertTime.plusHours(2),
                "segment-b.jpg",
                "segment-b.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "stitched camera timeline",
                first.id(),
                List.of(first.id(), second.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(first.id(), second.id()),
                9202L,
                "mp4",
                "ordered case export"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9203L)
        );

        assertEquals(1, run.processedCount());
        assertEquals(1, videoExportProvider.requests().size());
        ReviewEvidenceVideoExportRequest request = videoExportProvider.requests().get(0);
        assertEquals("camera-01", request.cameraId());
        assertEquals(job.expiresAt(), request.expiresAt());
        assertEquals(List.of("segment-a.mp4", "segment-b.mp4"), request.recordSegments().stream()
                .map(segment -> segment.recordUri())
                .toList());
        assertEquals(List.of(0, 1), request.recordSegments().stream()
                .map(segment -> segment.stitchOrder())
                .toList());
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_READY,
                itemStore.findExportJobByNo(job.jobNo()).orElseThrow().status());
    }

    @Test
    void evidenceExportWorkerClipsRecordSegmentsToTheirCoverageWindows() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 13, 45, 16);
        List<RecordCoverageSegment> coverage = List.of(
                new RecordCoverageSegment(
                        "available",
                        alertTime.minusSeconds(15),
                        alertTime.plusSeconds(14),
                        0,
                        "segment-before.flv"
                ),
                new RecordCoverageSegment(
                        "available",
                        alertTime.plusSeconds(14),
                        alertTime.plusSeconds(44),
                        0,
                        "segment-middle.flv"
                ),
                new RecordCoverageSegment(
                        "available",
                        alertTime.plusSeconds(44),
                        alertTime.plusSeconds(74),
                        0,
                        "segment-after.flv"
                ),
                new RecordCoverageSegment(
                        "available",
                        alertTime.plusSeconds(74),
                        alertTime.plusSeconds(104),
                        0,
                        "segment-outside.flv"
                )
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> coverage,
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-coverage-window",
                alertTime,
                "coverage-window.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(item.id(), 9202L, coverage));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "coverage bounded video export",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9203L,
                "mp4",
                "clip each physical recording only inside the review window"
        ));

        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9204L));

        assertEquals(1, videoExportProvider.requests().size());
        List<ReviewEvidenceVideoSegmentRequest> segments = videoExportProvider.requests().get(0).recordSegments();
        assertEquals(List.of("segment-before.flv", "segment-middle.flv", "segment-after.flv"), segments.stream()
                .map(ReviewEvidenceVideoSegmentRequest::recordUri)
                .toList());
        assertEquals(List.of(
                        alertTime.minusSeconds(15),
                        alertTime.plusSeconds(14),
                        alertTime.plusSeconds(44)
                ), segments.stream().map(ReviewEvidenceVideoSegmentRequest::segmentStartTime).toList());
        assertEquals(List.of(
                        alertTime.plusSeconds(14),
                        alertTime.plusSeconds(44),
                        alertTime.plusSeconds(74)
                ), segments.stream().map(ReviewEvidenceVideoSegmentRequest::segmentEndTime).toList());
        assertEquals(List.of(alertTime, alertTime.plusSeconds(14), alertTime.plusSeconds(44)), segments.stream()
                .map(ReviewEvidenceVideoSegmentRequest::clipStartTime)
                .toList());
        assertEquals(List.of(alertTime.plusSeconds(14), alertTime.plusSeconds(44), alertTime.plusSeconds(60)),
                segments.stream().map(ReviewEvidenceVideoSegmentRequest::clipEndTime).toList());
        assertEquals(List.of(0, 1, 2), segments.stream()
                .map(ReviewEvidenceVideoSegmentRequest::stitchOrder)
                .toList());
    }

    @Test
    void evidenceExportWorkerUsesOneCoverageSnapshotPerReviewItem() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        AtomicInteger coverageCalls = new AtomicInteger();
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 14, 0);
        RecordCoverageSegment firstSnapshot = new RecordCoverageSegment(
                "available",
                alertTime.minusSeconds(10),
                alertTime.plusSeconds(20),
                0,
                "snapshot-record.flv"
        );
        RecordCoverageSegment driftingSecondSnapshot = new RecordCoverageSegment(
                "available",
                alertTime.minusSeconds(5),
                alertTime.plusSeconds(40),
                0,
                "snapshot-record.flv"
        );
        RecordCoverageResolver coverageResolver = request -> coverageCalls.incrementAndGet() == 1
                ? List.of(firstSnapshot)
                : List.of(driftingSecondSnapshot);
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                coverageResolver,
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-single-coverage-snapshot",
                alertTime,
                "single-coverage-snapshot.jpg",
                "snapshot-record.flv"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "single coverage snapshot",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9205L,
                "mp4",
                "reuse one coverage snapshot throughout export"
        ));

        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9206L));

        assertEquals(1, videoExportProvider.requests().size());
        ReviewEvidenceVideoSegmentRequest segment = videoExportProvider.requests().get(0).recordSegments().get(0);
        assertAll(
                () -> assertEquals(1, coverageCalls.get()),
                () -> assertEquals(firstSnapshot.startTime(), segment.segmentStartTime()),
                () -> assertEquals(firstSnapshot.endTime(), segment.segmentEndTime()),
                () -> assertEquals(alertTime, segment.clipStartTime()),
                () -> assertEquals(firstSnapshot.endTime(), segment.clipEndTime())
        );
    }

    @Test
    void evidenceExportWorkerFallsBackToManualRecordWhenCoverageDoesNotMatch() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 14, 10);
        List<RecordCoverageSegment> unrelatedCoverage = List.of(
                new RecordCoverageSegment(
                        "missing",
                        alertTime.minusSeconds(30),
                        alertTime,
                        0,
                        null
                ),
                new RecordCoverageSegment(
                        "available",
                        alertTime,
                        alertTime.plusSeconds(30),
                        0,
                        "other-record.flv"
                )
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> unrelatedCoverage,
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-manual-record",
                alertTime,
                "manual-record.jpg",
                "manual-record.flv"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "manual record fallback",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9205L,
                "mp4",
                "retain explicitly attached recording"
        ));

        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9206L));

        assertEquals(1, videoExportProvider.requests().size());
        List<ReviewEvidenceVideoSegmentRequest> segments = videoExportProvider.requests().get(0).recordSegments();
        assertEquals(1, segments.size());
        assertEquals("manual-record.flv", segments.get(0).recordUri());
        assertNull(segments.get(0).segmentStartTime());
        assertNull(segments.get(0).segmentEndTime());
        assertEquals(alertTime, segments.get(0).clipStartTime());
        assertEquals(alertTime.plusSeconds(60), segments.get(0).clipEndTime());
        assertEquals(0, segments.get(0).stitchOrder());
    }

    @Test
    void evidenceExportWorkerFailsBeforeVideoWhenAnyReviewItemHasNoSegment() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime firstAlertTime = LocalDateTime.of(2026, 7, 13, 15, 0);
        LocalDateTime secondAlertTime = firstAlertTime.plusHours(2);
        RecordCoverageSegment firstCoverage = new RecordCoverageSegment(
                "available",
                firstAlertTime,
                firstAlertTime.plusSeconds(30),
                0,
                "segment-a.flv"
        );
        RecordCoverageSegment secondStoredCoverage = new RecordCoverageSegment(
                "available",
                secondAlertTime,
                secondAlertTime.plusSeconds(30),
                0,
                "segment-b.flv"
        );
        RecordCoverageSegment secondUnrelatedCoverage = new RecordCoverageSegment(
                "available",
                secondAlertTime,
                secondAlertTime.plusSeconds(30),
                0,
                "other-segment.flv"
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> request.beginTime().isBefore(firstAlertTime.plusHours(1))
                        ? List.of(firstCoverage)
                        : List.of(secondUnrelatedCoverage),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-export-complete-a",
                firstAlertTime,
                "complete-a.jpg",
                null
        ));
        ReviewItemAggregate second = service.ingestClue(newClue(
                "alert-export-complete-b",
                secondAlertTime,
                "complete-b.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(first.id(), 9207L, List.of(firstCoverage)));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(second.id(), 9207L,
                List.of(secondStoredCoverage)));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "complete multi-item export",
                first.id(),
                List.of(first.id(), second.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(first.id(), second.id()),
                9208L,
                "mp4",
                "every review item must have video evidence"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9209L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_FAILED,
                itemStore.findExportJobByNo(job.jobNo()).orElseThrow().status());
    }

    @Test
    void evidenceExportWorkerRejectsPartiallyOverlappingSegmentsForOneReviewItem() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 16, 0);
        List<RecordCoverageSegment> overlappingCoverage = List.of(
                new RecordCoverageSegment(
                        "available",
                        alertTime.minusSeconds(10),
                        alertTime.plusSeconds(35),
                        0,
                        "overlap-a.flv"
                ),
                new RecordCoverageSegment(
                        "available",
                        alertTime.plusSeconds(25),
                        alertTime.plusSeconds(55),
                        0,
                        "overlap-b.flv"
                )
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> overlappingCoverage,
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-overlap",
                alertTime,
                "overlap.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(item.id(), 9210L, overlappingCoverage));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "overlapping record segments",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9211L,
                "mp4",
                "overlap must fail closed"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9212L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportWorkerRejectsDifferentPhysicalWindowsForSameRecordUri() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 16, 30);
        List<RecordCoverageSegment> conflictingCoverage = List.of(
                new RecordCoverageSegment(
                        "available",
                        alertTime.minusSeconds(10),
                        alertTime.plusSeconds(30),
                        0,
                        "same-record.flv"
                ),
                new RecordCoverageSegment(
                        "available",
                        alertTime.plusSeconds(30),
                        alertTime.plusSeconds(60),
                        0,
                        "same-record.flv"
                )
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> conflictingCoverage,
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-same-record-bounds",
                alertTime,
                "same-record.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(item.id(), 9213L, conflictingCoverage));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "same record conflicting windows",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9214L,
                "mp4",
                "same record must have one physical window"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9215L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportWorkerSortsSegmentsAndAlignsTopLevelIdentityToFirstSegment() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime laterAlertTime = LocalDateTime.of(2026, 7, 13, 10, 20);
        LocalDateTime earlierAlertTime = LocalDateTime.of(2026, 7, 13, 10, 9);
        RecordCoverageSegment laterCoverage = new RecordCoverageSegment(
                "available",
                LocalDateTime.of(2026, 7, 13, 10, 0),
                LocalDateTime.of(2026, 7, 13, 10, 21),
                0,
                "sort-later.flv"
        );
        RecordCoverageSegment earlierCoverage = new RecordCoverageSegment(
                "available",
                LocalDateTime.of(2026, 7, 13, 10, 5),
                LocalDateTime.of(2026, 7, 13, 10, 10),
                0,
                "sort-earlier.flv"
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> "device-01".equals(request.deviceId())
                        ? List.of(laterCoverage)
                        : List.of(earlierCoverage),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate earlier = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-export-sort-earlier",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                earlierAlertTime,
                "device-02",
                "camera-01",
                "zone-a",
                "person",
                15,
                "sort-earlier.jpg",
                null,
                null
        ));
        ReviewItemAggregate later = service.ingestClue(newClue(
                "alert-export-sort-later",
                laterAlertTime,
                "sort-later.jpg",
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(later.id(), 9216L, List.of(laterCoverage)));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(earlier.id(), 9216L, List.of(earlierCoverage)));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "sorted cross-item video export",
                later.id(),
                List.of(later.id(), earlier.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(later.id(), earlier.id()),
                9217L,
                "mp4",
                "stable chronological stitch"
        ));

        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9218L));

        assertEquals(1, videoExportProvider.requests().size());
        ReviewEvidenceVideoExportRequest request = videoExportProvider.requests().get(0);
        assertEquals(List.of("sort-earlier.flv", "sort-later.flv"), request.recordSegments().stream()
                .map(ReviewEvidenceVideoSegmentRequest::recordUri)
                .toList());
        assertEquals(List.of(0, 1), request.recordSegments().stream()
                .map(ReviewEvidenceVideoSegmentRequest::stitchOrder)
                .toList());
        assertEquals(earlier.id(), request.reviewItemId());
        assertEquals("device-02", request.deviceId());
        assertEquals("alert-export-sort-earlier", request.sourceAlertId());
        assertEquals("sort-earlier.flv", request.recordUri());
    }

    @Test
    void evidenceExportWorkerRejectsConflictingPhysicalWindowsForSameUriAcrossItems() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        LocalDateTime firstAlertTime = LocalDateTime.of(2026, 7, 13, 17, 0);
        LocalDateTime secondAlertTime = firstAlertTime.plusHours(2);
        RecordCoverageSegment firstCoverage = new RecordCoverageSegment(
                "available",
                firstAlertTime,
                firstAlertTime.plusSeconds(30),
                0,
                "shared-record.flv"
        );
        RecordCoverageSegment secondCoverage = new RecordCoverageSegment(
                "available",
                secondAlertTime,
                secondAlertTime.plusSeconds(30),
                0,
                "shared-record.flv"
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> "device-01".equals(request.deviceId())
                        ? List.of(firstCoverage)
                        : List.of(secondCoverage),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-export-shared-uri-a",
                firstAlertTime,
                "shared-a.jpg",
                null
        ));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-export-shared-uri-b",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                secondAlertTime,
                "device-02",
                "camera-01",
                "zone-a",
                "person",
                15,
                "shared-b.jpg",
                null,
                null
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(first.id(), 9219L, List.of(firstCoverage)));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(second.id(), 9219L, List.of(secondCoverage)));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "shared URI conflicting physical windows",
                first.id(),
                List.of(first.id(), second.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(first.id(), second.id()),
                9220L,
                "mp4",
                "shared URI must identify one physical file window"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9221L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportWorkerNormalizesMinioRecordUriToUniqueCanonicalCameraRecord() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 13, 45, 16);
        String objectName = "camera-01/2026/07/13/record-001.flv";
        String minioUri = "https://eye.yfeiai.com/api/v1/buckets/record-space/objects/download"
                + "?prefix=camera-01%2F2026%2F07%2F13%2Frecord-001.flv";
        String canonicalUri = "/video/record/space/7/video/" + objectName;
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-minio-alias",
                alertTime,
                "record-001.jpg",
                minioUri
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9202L,
                List.of(new RecordCoverageSegment(
                        "available",
                        alertTime.plusSeconds(1),
                        alertTime.plusSeconds(61),
                        7,
                        canonicalUri
                ))
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "canonical record URI",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9203L,
                "mp4",
                "normalize MinIO alias"
        ));

        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9204L));

        assertEquals(1, videoExportProvider.requests().size());
        ReviewEvidenceVideoExportRequest request = videoExportProvider.requests().get(0);
        assertEquals(canonicalUri, request.recordUri());
        assertEquals(List.of(canonicalUri), request.recordSegments().stream()
                .map(segment -> segment.recordUri())
                .toList());
        assertEquals(List.of(0), request.recordSegments().stream()
                .map(segment -> segment.stitchOrder())
                .toList());
    }

    @Test
    void evidenceExportWorkerRejectsUnboundMinioRecordUriForCamera() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 14, 0);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-unbound-minio",
                alertTime,
                "unbound-minio.jpg",
                "https://eye.yfeiai.com/api/v1/buckets/record-space/objects/download"
                        + "?prefix=camera-01%2F2026%2F07%2F13%2Funbound.flv"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "unbound MinIO record URI",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9205L,
                "mp4",
                "reject unbound MinIO alias"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9206L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportWorkerRejectsAmbiguousCanonicalRecordsForMinioObject() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 14, 10);
        String objectName = "camera-01/2026/07/13/ambiguous.flv";
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-ambiguous-minio",
                alertTime,
                "ambiguous-minio.jpg",
                "https://eye.yfeiai.com/api/v1/buckets/record-space/objects/download"
                        + "?prefix=camera-01%2F2026%2F07%2F13%2Fambiguous.flv"
        ));
        service.syncRecordStorage(new ReviewRecordStorageSyncCommand(
                item.id(),
                9207L,
                List.of(
                        new RecordCoverageSegment(
                                "available",
                                alertTime.plusSeconds(1),
                                alertTime.plusSeconds(61),
                                7,
                                "/video/record/space/7/video/" + objectName
                        ),
                        new RecordCoverageSegment(
                                "available",
                                alertTime.plusSeconds(62),
                                alertTime.plusSeconds(122),
                                8,
                                "/video/record/space/8/video/" + objectName
                        )
                )
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "ambiguous canonical records",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9208L,
                "mp4",
                "reject ambiguous canonical records"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9209L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportWorkerDoesNotBindMinioAliasToAnotherCameraCanonicalRecord() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 14, 20);
        String objectName = "shared/2026/07/13/cross-camera.flv";
        ReviewItemAggregate aliasItem = service.ingestClue(newClue(
                "alert-export-cross-camera-alias",
                alertTime,
                "cross-camera-alias.jpg",
                "https://eye.yfeiai.com/api/v1/buckets/record-space/objects/download"
                        + "?prefix=shared%2F2026%2F07%2F13%2Fcross-camera.flv"
        ));
        ReviewItemAggregate canonicalItem = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-export-cross-camera-canonical",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime.plusMinutes(1),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                15,
                "cross-camera-canonical.jpg",
                "/video/record/space/7/video/" + objectName,
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "cross-camera canonical isolation",
                aliasItem.id(),
                List.of(aliasItem.id(), canonicalItem.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(aliasItem.id(), canonicalItem.id()),
                9210L,
                "mp4",
                "reject cross-camera canonical binding"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9211L)
        );

        assertEquals(1, run.failedCount());
        assertTrue(videoExportProvider.requests().isEmpty());
    }

    @Test
    void evidenceExportWorkerClaimsOnlyOneJobBeforeEachLongRunningExport() {
        CountingExportClaimStore itemStore = new CountingExportClaimStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-small-claim",
                LocalDateTime.of(2026, 7, 10, 9, 30),
                "small-claim.jpg",
                "small-claim.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "small export claims",
                item.id(),
                List.of(item.id())
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(), List.of(item.id()), 9210L, "manifest", "first claim"
        ));
        service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(), List.of(item.id()), 9210L, "manifest", "second claim"
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9211L)
        );

        assertEquals(2, run.processedCount());
        assertTrue(itemStore.claimLimits().size() >= 2);
        assertTrue(itemStore.claimLimits().stream().allMatch(limit -> limit == 1),
                String.valueOf(itemStore.claimLimits()));
    }

    @Test
    void staleExportClaimConflictDoesNotSecondCompleteOrAbortLaterJobs() {
        ConflictingExportCompletionStore itemStore = new ConflictingExportCompletionStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-cas-conflict",
                LocalDateTime.of(2026, 7, 10, 9, 31),
                "cas-conflict.jpg",
                "cas-conflict.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "stale claim conflict",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob stale = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(), List.of(item.id()), 9212L, "manifest", "stale claim"
        ));
        ReviewEvidenceExportJob later = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(), List.of(item.id()), 9212L, "manifest", "later claim"
        ));
        itemStore.conflictOn(stale.jobNo());

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9213L)
        );

        assertEquals(1, run.processedCount());
        assertEquals(0, run.failedCount());
        assertTrue(run.deferredJobNos().contains(stale.jobNo()), String.valueOf(run.deferredJobNos()));
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_READY,
                itemStore.findExportJobByNo(later.jobNo()).orElseThrow().status());
        assertEquals(1, itemStore.completionAttempts(stale.jobNo()));
    }

    @Test
    void evidenceExportWorkerRebuildsFailedJobsAndLeavesReplayableManifest() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-worker",
                LocalDateTime.of(2026, 7, 8, 9, 20),
                "export-worker.jpg",
                "export-worker.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "export worker case",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9100L,
                "manifest",
                "worker retry"
        ));
        itemStore.replaceExportJobStatus(job.jobNo(), SupervisionAlertReviewService.EXPORT_JOB_FAILED);

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9101L)
        );

        ReviewEvidenceExportJob recovered = itemStore.findExportJobByNo(job.jobNo()).orElseThrow();
        Map<?, ?> worker = (Map<?, ?>) recovered.exportPackage().manifest().get("worker");
        assertEquals("completed", run.status());
        assertEquals(1, run.processedCount());
        assertEquals(0, run.failedCount());
        assertTrue(run.processedJobNos().contains(job.jobNo()));
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_READY, recovered.status());
        assertEquals("ready", worker.get("status"));
        assertEquals(1, worker.get("attemptCount"));
        assertEquals(9101L, worker.get("operatorUserId"));
        assertTrue(service.verifyEvidenceExportManifest(job.jobNo()).valid());

        itemStore.replaceExportJobStatus(job.jobNo(), SupervisionAlertReviewService.EXPORT_JOB_FAILED);
        String summary = new SupervisionAlertReviewEvidenceExportWorkerJob(service).execute("10");
        assertTrue(summary.contains("processed=1"));
    }

    @Test
    void evidenceExportCleanupDoesNotExpireAnActivelyRunningClaim() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-active-claim",
                LocalDateTime.of(2026, 7, 10, 10, 0),
                "active-claim.jpg",
                "active-claim.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "active export claim",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9204L,
                "manifest",
                "active claim must survive cleanup"
        ));
        Map<String, Object> manifest = new LinkedHashMap<>(job.exportPackage().manifest());
        manifest.put("worker", Map.of(
                "status", "running",
                "attemptCount", 1,
                "processedAt", LocalDateTime.now().toString()
        ));
        ReviewEvidenceExportPackage runningPackage = new ReviewEvidenceExportPackage(
                job.exportPackage().packageNo(),
                job.exportPackage().format(),
                job.exportPackage().reviewCaseId(),
                job.exportPackage().reviewItemIds(),
                job.exportPackage().evidenceUris(),
                job.exportPackage().timeline(),
                Map.copyOf(manifest),
                job.exportPackage().generatedAt()
        );
        itemStore.replaceExportJobForTest(new ReviewEvidenceExportJob(
                job.jobNo(),
                SupervisionAlertReviewService.EXPORT_JOB_RUNNING,
                runningPackage,
                job.fileHash(),
                LocalDateTime.now().minusMinutes(1),
                job.operatorUserId(),
                job.reason(),
                job.boundEventIds(),
                job.createdAt()
        ));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9205L)
        );

        assertEquals(0, run.scannedCount());
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_RUNNING,
                itemStore.findExportJobByNo(job.jobNo()).orElseThrow().status());
    }

    @Test
    void failedEvidenceExportUsesPersistentBackoffBeforeRetryingVideo() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.empty()
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-backoff",
                LocalDateTime.of(2026, 7, 10, 10, 10),
                "export-backoff.jpg",
                "export-backoff.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "failed export backoff",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9206L,
                "mp4",
                "retry with backoff"
        ));

        ReviewEvidenceExportWorkerRun failed = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9207L)
        );
        ReviewEvidenceExportWorkerRun deferred = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9208L)
        );

        ReviewEvidenceExportJob stored = itemStore.findExportJobByNo(job.jobNo()).orElseThrow();
        Map<?, ?> worker = (Map<?, ?>) stored.exportPackage().manifest().get("worker");
        assertEquals(1, failed.failedCount());
        assertEquals(0, deferred.scannedCount());
        assertEquals(1, deferred.deferredCount());
        assertEquals(1, videoExportProvider.requests().size());
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_FAILED, stored.status());
        assertTrue(LocalDateTime.parse(String.valueOf(worker.get("nextRetryAt"))).isAfter(LocalDateTime.now()));
    }

    @Test
    void evidenceExportDownloadExpiresAndWorkerCleansExpiredJobs() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-export-expired",
                LocalDateTime.of(2026, 7, 8, 10, 20),
                "export-expired.jpg",
                "export-expired.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "export expired case",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob job = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9102L,
                "manifest",
                "expiry guard"
        ));
        itemStore.replaceExportJobStatus(job.jobNo(), SupervisionAlertReviewService.EXPORT_JOB_FAILED);

        IllegalStateException failed = assertThrows(IllegalStateException.class,
                () -> service.downloadEvidencePackage(new ReviewEvidenceDownloadCommand(
                        job.jobNo(), 9103L, null, "download failed job")));
        assertTrue(failed.getMessage().contains("not ready"));

        ReviewEvidenceExportJob expired = new ReviewEvidenceExportJob(
                job.jobNo(),
                SupervisionAlertReviewService.EXPORT_JOB_READY,
                job.exportPackage(),
                job.fileHash(),
                LocalDateTime.of(2026, 7, 1, 0, 0),
                job.operatorUserId(),
                job.reason(),
                job.boundEventIds(),
                job.createdAt()
        );
        itemStore.replaceExportJobForTest(expired);

        IllegalStateException expiredDownload = assertThrows(IllegalStateException.class,
                () -> service.downloadEvidencePackage(new ReviewEvidenceDownloadCommand(
                        job.jobNo(), 9104L, null, "download expired job")));
        assertTrue(expiredDownload.getMessage().contains("expired"));

        ReviewEvidenceExportWorkerRun run = service.processEvidenceExportQueue(
                new ReviewEvidenceExportWorkerCommand(10, 9105L)
        );

        ReviewEvidenceExportJob cleaned = itemStore.findExportJobByNo(job.jobNo()).orElseThrow();
        Map<?, ?> worker = (Map<?, ?>) cleaned.exportPackage().manifest().get("worker");
        assertEquals("completed", run.status());
        assertEquals(1, run.processedCount());
        assertTrue(run.processedJobNos().contains(job.jobNo()));
        assertEquals(SupervisionAlertReviewService.EXPORT_JOB_EXPIRED, cleaned.status());
        assertEquals("expired", worker.get("status"));
        assertEquals(9105L, worker.get("operatorUserId"));
        assertTrue(service.verifyEvidenceExportManifest(job.jobNo()).valid());
    }

    @Test
    void evidenceDownloadPackagesEveryCameraArtifactAndProvenanceIntoVerifiedZip() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        Map<String, byte[]> artifactBytes = Map.of(
                "camera-01", "camera-one-real-video".getBytes(StandardCharsets.UTF_8),
                "camera-02", "camera-two-real-video".getBytes(StandardCharsets.UTF_8)
        );
        Map<String, String> artifactHashes = Map.of(
                "camera-01", sha256Bytes(artifactBytes.get("camera-01")),
                "camera-02", sha256Bytes(artifactBytes.get("camera-02"))
        );
        List<ReviewEvidenceVideoDownloadRequest> downloadRequests = new ArrayList<>();
        VideoEvidenceExportProvider videoExportProvider = new VideoEvidenceExportProvider() {
            @Override
            public Optional<ReviewEvidenceVideoExportResult> export(ReviewEvidenceVideoExportRequest request) {
                List<Map<String, Object>> segments = request.recordSegments().stream()
                        .map(segment -> Map.<String, Object>of(
                                "cameraId", request.cameraId(),
                                "originalRecordUri", segment.recordUri(),
                                "sourceHash", validSha256(request.cameraId().endsWith("01") ? '1' : '2'),
                                "ffmpegCommandHash", validSha256(request.cameraId().endsWith("01") ? '3' : '4'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", segment.stitchOrder()
                        ))
                        .toList();
                return Optional.of(new ReviewEvidenceVideoExportResult(
                        "export-" + request.cameraId(),
                        "https://eye.yfeiai.com/video/record/export/export-" + request.cameraId() + "/download",
                        "ready",
                        "camera artifact ready",
                        "https://eye.yfeiai.com/video/record/export/export-" + request.cameraId() + "/manifest",
                        artifactHashes.get(request.cameraId()),
                        segments,
                        validSha256(request.cameraId().endsWith("01") ? '5' : '6')
                ));
            }

            @Override
            public Optional<ReviewEvidenceDownloadArtifact> download(ReviewEvidenceVideoDownloadRequest request) {
                downloadRequests.add(request);
                byte[] bytes = artifactBytes.get(request.cameraId());
                return Optional.of(temporaryArtifact(
                        null,
                        request.cameraId() + ".mp4",
                        "video/mp4",
                        bytes,
                        artifactHashes.get(request.cameraId())
                ));
            }
        };
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 11, 13, 0);
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-package-camera-01", baseTime, "camera-01.jpg", "camera-01.mp4"));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-package-camera-02",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime.plusMinutes(2),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                15,
                "camera-02.jpg",
                "camera-02.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "cross-camera evidence package", first.id(), List.of(first.id())));
        service.addToReviewCase(reviewCase.id(), second.id());
        ReviewEvidenceExportJob queued = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(), List.of(first.id(), second.id()), 9500L, "mp4", "regulator package"));
        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(1, 9500L));
        ReviewEvidenceExportJob ready = itemStore.findExportJobByNo(queued.jobNo()).orElseThrow();

        ReviewEvidenceDownloadArtifact artifact = service.downloadEvidencePackage(new ReviewEvidenceDownloadCommand(
                queued.jobNo(), 9501L, List.of("camera-01", "camera-02"), "download full case"));
        byte[] packageBytes = Files.readAllBytes(artifact.temporaryFile());
        Map<String, byte[]> zipEntries = unzipEntries(packageBytes);

        assertEquals("application/zip", artifact.contentType());
        assertTrue(artifact.fileName().endsWith(".zip"));
        assertEquals(packageBytes.length, artifact.contentLength());
        assertEquals(sha256Bytes(packageBytes), artifact.fileHash());
        assertEquals(artifact.fileHash(), artifact.auditEntry().fileHash());
        assertEquals(artifact.fileHash(), artifact.auditEntry().metadata().get("downloadFileHash"));
        assertEquals(ready.fileHash(), artifact.auditEntry().metadata().get("logicalPackageHash"));
        assertArrayEquals(artifactBytes.get("camera-01"), zipEntries.get("artifacts/000-camera-01.mp4"));
        assertArrayEquals(artifactBytes.get("camera-02"), zipEntries.get("artifacts/001-camera-02.mp4"));
        String manifest = new String(zipEntries.get("manifest.json"), StandardCharsets.UTF_8);
        String audit = new String(zipEntries.get("audit.json"), StandardCharsets.UTF_8);
        String metadata = new String(zipEntries.get("metadata.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("videoExports"));
        assertTrue(manifest.contains("sourceHash"));
        assertTrue(manifest.contains("ffmpegCommandHash"));
        assertTrue(manifest.contains("clipParameters"));
        assertTrue(manifest.contains("stitchOrder"));
        assertTrue(audit.contains("export_created"));
        assertTrue(metadata.contains("packageHash"));
        assertTrue(metadata.contains(artifactHashes.get("camera-01")));
        assertTrue(metadata.contains(artifactHashes.get("camera-02")));
        assertEquals(List.of("camera-01", "camera-02"), downloadRequests.stream()
                .map(ReviewEvidenceVideoDownloadRequest::cameraId)
                .toList());
        assertTrue(service.getEvidenceAuditTrail(reviewCase.id()).stream()
                .anyMatch(entry -> "export_downloaded".equals(entry.actionType())
                        && artifact.fileHash().equals(entry.fileHash())
                        && artifact.fileHash().equals(entry.metadata().get("downloadFileHash"))));
        artifact.close();
        assertFalse(Files.exists(artifact.temporaryFile()));
    }

    @Test
    void evidenceDownloadAcceptsUppercaseExpectedHashAndPersistsRealByteHash() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        byte[] videoBytes = "real-video-package-bytes".getBytes(StandardCharsets.UTF_8);
        String realHash = sha256Bytes(videoBytes);
        String providerHash = "sha256:" + realHash.substring("sha256:".length())
                .toUpperCase(java.util.Locale.ROOT);
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "real-export-001",
                        "https://eye.yfeiai.com/video/record/export/real-export-001/download",
                        "ready",
                        "real VIDEO package",
                        "https://eye.yfeiai.com/video/record/export/real-export-001/manifest",
                        providerHash,
                        List.of(Map.of(
                                "cameraId", "camera-01",
                                "originalRecordUri", "download-real.mp4",
                                "sourceHash", validSha256('a'),
                                "ffmpegCommandHash", validSha256('b'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", 0
                        )),
                        validSha256('c')
                )),
                Optional.of(temporaryArtifact(
                        "REJ-pending",
                        "real-export-001.mp4",
                        "video/mp4",
                        videoBytes,
                        realHash
                ))
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-download-real",
                LocalDateTime.of(2026, 7, 11, 9, 0),
                "download-real.jpg",
                "download-real.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "real VIDEO download",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob queued = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(), List.of(item.id()), 9300L, "mp4", "download real package"
        ));
        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(1, 9300L));
        ReviewEvidenceExportJob ready = itemStore.findExportJobByNo(queued.jobNo()).orElseThrow();

        ReviewEvidenceDownloadArtifact artifact = service.downloadEvidencePackage(new ReviewEvidenceDownloadCommand(
                ready.jobNo(), 9301L, List.of("camera-01"), "operator download"
        ));

        assertEquals(providerHash, ready.fileHash());
        assertEquals(realHash, artifact.fileHash());
        assertArrayEquals(videoBytes, Files.readAllBytes(artifact.temporaryFile()));
        assertEquals(1, videoExportProvider.downloadRequests().size());
        ReviewEvidenceVideoDownloadRequest request = videoExportProvider.downloadRequests().get(0);
        assertEquals("camera-01", request.cameraId());
        assertEquals(providerHash, request.expectedFileHash());
        assertTrue(service.getEvidenceAuditTrail(reviewCase.id()).stream()
                .anyMatch(entry -> "export_downloaded".equals(entry.actionType())
                        && realHash.equals(entry.fileHash())
                        && providerHash.equals(entry.metadata().get("logicalPackageHash"))
                        && Objects.equals(9301L, entry.operatorUserId())));
        artifact.close();
        assertFalse(Files.exists(artifact.temporaryFile()));
    }

    @Test
    void evidenceAuditTrailListsHashesExporterDownloadsAndBoundEvents() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = singleCameraDownloadableProvider(
                "camera-01",
                "audit-chain-real-video".getBytes(StandardCharsets.UTF_8)
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7500L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-audit-chain",
                LocalDateTime.of(2026, 7, 1, 10, 20),
                "audit-chain.jpg",
                "audit-chain.mp4"
        ));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9002L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "audit chain case",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob queuedJob = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9003L,
                "mp4",
                "regulator package"
        ));
        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9003L));
        ReviewEvidenceExportJob job = itemStore.findExportJobByNo(queuedJob.jobNo()).orElseThrow();

        try (ReviewEvidenceDownloadArtifact ignored = service.downloadEvidencePackage(
                new ReviewEvidenceDownloadCommand(
                        job.jobNo(), 9004L, List.of("camera-01"), "case handoff download"))) {
            assertTrue(ignored.contentLength() > 0);
        }
        List<ReviewEvidenceAuditEntry> auditTrail = service.getEvidenceAuditTrail(reviewCase.id());

        ReviewEvidenceAuditEntry createdAudit = auditTrail.stream()
                .filter(entry -> "export_created".equals(entry.actionType()))
                .findFirst()
                .orElseThrow();
        ReviewEvidenceAuditEntry downloadedAudit = auditTrail.stream()
                .filter(entry -> "export_downloaded".equals(entry.actionType()))
                .findFirst()
                .orElseThrow();
        assertTrue(auditTrail.stream().anyMatch(entry -> "export_created".equals(entry.actionType())
                && Objects.equals(job.jobNo(), entry.jobNo())
                && Objects.equals(job.fileHash(), entry.fileHash())
                && Objects.equals(9003L, entry.operatorUserId())
                && entry.boundEventIds().contains(7500L)
                && entry.evidenceUris().contains("audit-chain.mp4")));
        assertTrue(auditTrail.stream().anyMatch(entry -> "export_downloaded".equals(entry.actionType())
                && Objects.equals(job.jobNo(), entry.jobNo())
                && Objects.equals(9004L, entry.operatorUserId())
                && "case handoff download".equals(entry.actionNote())));
        assertEquals(reviewCase.id(), createdAudit.metadata().get("reviewCaseId"));
        assertEquals(List.of(item.id()), createdAudit.metadata().get("reviewItemIds"));
        assertEquals(List.of(7500L), createdAudit.metadata().get("eventIds"));
        assertEquals(job.jobNo(), createdAudit.metadata().get("exportJobNo"));
        assertEquals(reviewCase.id(), downloadedAudit.metadata().get("reviewCaseId"));
        assertEquals(List.of(item.id()), downloadedAudit.metadata().get("reviewItemIds"));
        assertEquals(List.of(7500L), downloadedAudit.metadata().get("eventIds"));
        assertEquals(job.jobNo(), downloadedAudit.metadata().get("exportJobNo"));
        assertTrue(downloadedAudit.boundEventIds().contains(7500L));
        assertTrue(downloadedAudit.evidenceUris().contains("audit-chain.mp4"));
        assertTrue(auditTrail.stream().allMatch(entry -> String.valueOf(entry.metadata().get("entryHash"))
                .startsWith("sha256:")));
        assertEquals("GENESIS", auditTrail.get(0).metadata().get("previousHash"));
        assertEquals(auditTrail.get(0).metadata().get("entryHash"), auditTrail.get(1).metadata().get("previousHash"));
    }

    @Test
    void unifiedEvidenceAuditLookupFindsPersistedMediaExportAndDownloadByEventOrJobAndIntersectsKeys() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = singleCameraDownloadableProvider(
                "camera-01",
                "reverse-lookup-real-video".getBytes(StandardCharsets.UTF_8)
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                command -> new AlertToEventResult(
                        7510L,
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        command.ruleCode(),
                        "supervision_order",
                        SupervisionEventLevelEnum.L2,
                        SupervisionEventStatusEnum.ACCEPTED.getCode(),
                        false
                ),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-audit-reverse-lookup",
                LocalDateTime.of(2026, 7, 11, 10, 30),
                "audit-reverse.jpg",
                "audit-reverse.mp4"
        ));
        service.convertToEvent(new ReviewToEventCommand(item.id(), 9010L));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "reverse lookup case",
                item.id(),
                List.of(item.id())
        ));
        service.auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCase.id(),
                item.id(),
                9011L,
                "camera-01",
                "audit-reverse.mp4",
                "playback",
                List.of("camera-01"),
                "reverse lookup playback"
        ));
        ReviewEvidenceExportJob queuedJob = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9012L,
                "mp4",
                "reverse lookup export"
        ));
        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9012L));
        ReviewEvidenceExportJob job = itemStore.findExportJobByNo(queuedJob.jobNo()).orElseThrow();
        try (ReviewEvidenceDownloadArtifact ignored = service.downloadEvidencePackage(
                new ReviewEvidenceDownloadCommand(
                        job.jobNo(), 9013L, List.of("camera-01"), "reverse lookup download"))) {
            assertTrue(ignored.contentLength() > 0);
        }

        List<ReviewEvidenceAuditEntry> byEvent = service.queryEvidenceAuditTrail(
                new ReviewEvidenceAuditQuery(7510L, null, null, null)
        );
        List<ReviewEvidenceAuditEntry> byJob = service.queryEvidenceAuditTrail(
                new ReviewEvidenceAuditQuery(null, null, null, job.jobNo())
        );
        List<ReviewEvidenceAuditEntry> intersectedMiss = service.queryEvidenceAuditTrail(
                new ReviewEvidenceAuditQuery(7510L, reviewCase.id(), item.id() + 1, job.jobNo())
        );

        List<String> actions = byEvent.stream().map(ReviewEvidenceAuditEntry::actionType).toList();
        assertTrue(actions.contains("export_created"));
        assertTrue(actions.contains("export_downloaded"));
        assertTrue(actions.contains("media_access_granted"));
        assertTrue(byEvent.stream().anyMatch(entry -> "playback".equals(entry.metadata().get("mediaAction"))));
        assertTrue(byEvent.stream().anyMatch(entry -> "download".equals(entry.metadata().get("mediaAction"))));
        assertTrue(byJob.size() >= 3);
        assertTrue(byJob.stream().allMatch(entry -> entry.metadata().containsKey("entryHash")));
        assertEquals("GENESIS", byJob.get(0).metadata().get("previousHash"));
        assertTrue(intersectedMiss.isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.queryEvidenceAuditTrail(new ReviewEvidenceAuditQuery(null, null, null, "  ")));
    }

    @Test
    void aiSummaryCanUseExternalProviderWithCaseTimelineContext() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> {
                            assertEquals(2, request.items().size());
                            assertTrue(request.timeline().stream()
                                    .anyMatch(item -> "snapshot".equals(item.materialType())));
                            return Optional.of(new ReviewAiSummary(
                                    request.reviewCaseId(),
                                    request.reviewItemIds(),
                                    "provider case",
                                    "external provider summary",
                                    List.of("provider fact"),
                                    List.of("provider gap"),
                                    List.of("provider action"),
                                    LocalDateTime.of(2026, 6, 30, 15, 0),
                                    "external-review-provider"
                            ));
                        }
                )
        );
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 30, 14, 40);
        ReviewItemAggregate first = service.ingestClue(newClue("alert-provider-ai-1", baseTime, "provider-1.jpg", "provider-1.mp4"));
        ReviewItemAggregate second = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-provider-ai-2",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                baseTime.plusMinutes(2),
                "device-02",
                "camera-02",
                "zone-b",
                "person",
                15,
                "provider-2.jpg",
                "provider-2.mp4",
                null
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "Provider summary case",
                first.id(),
                List.of(first.id(), second.id())
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);

        assertEquals("external provider summary", summary.summary());
        assertEquals("external-review-provider", summary.generatedBy());
        assertEquals(List.of("provider action"), summary.recommendedActions());
        assertEquals("provider case", summary.structuredData().get("title"));
        assertEquals("camera-01 / camera-02", summary.structuredData().get("scene"));
        assertEquals("medium", summary.structuredData().get("threatLevel"));
        assertEquals(List.of("provider gap"), summary.structuredData().get("evidenceGaps"));
        assertEquals(List.of("provider action"), summary.structuredData().get("disposalSuggestion"));
        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");
        assertTrue(provenance != null);
        assertEquals("external-review-provider", provenance.get("provider"));
        assertEquals("external-review-provider", provenance.get("model"));
        assertEquals("review-ai-provider-v1", provenance.get("providerVersion"));
        assertEquals("review-ai-summary-prompt-v1", provenance.get("promptVersion"));
        assertTrue(String.valueOf(provenance.get("promptHash")).startsWith("sha256:"));
        assertEquals("not_required", provenance.get("redactionStatus"));
        assertEquals(List.of(), provenance.get("redactedFields"));
        assertEquals("pending", provenance.get("humanConfirmationStatus"));
        assertEquals(9004L, provenance.get("requestedBy"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .filter(item -> "case_audit".equals(item.materialType()))
                .filter(item -> "ai_summary_generated".equals(item.materialUri()))
                .anyMatch(item -> item.actionNote().contains("promptHash=sha256:")
                        && item.actionNote().contains("promptVersion=review-ai-summary-prompt-v1")
                        && item.actionNote().contains("provider=external-review-provider")
                        && item.actionNote().contains("humanConfirmationStatus=pending")
                        && item.actionNote().contains("redactionStatus=not_required")));
    }

    @Test
    void aiSummaryOverridesProviderSuppliedProvenanceWithServerOwnedValues() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> Optional.of(new ReviewAiSummary(
                                request.reviewCaseId(),
                                request.reviewItemIds(),
                                "provider title",
                                "provider summary",
                                List.of("provider fact"),
                                List.of(),
                                List.of("provider action"),
                                LocalDateTime.of(2026, 6, 30, 15, 10),
                                "external-review-provider",
                                Map.of("aiProvenance", Map.of(
                                        "provider", "forged-provider",
                                        "model", "forged-model",
                                        "providerVersion", "forged-provider-version",
                                        "promptVersion", "forged-prompt-version",
                                        "requestedBy", -1L
                                ))
                        ))
                )
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-ai-forged-provenance",
                LocalDateTime.of(2026, 6, 30, 15, 5),
                "ai-forged-provenance.jpg",
                "ai-forged-provenance.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI forged provenance case",
                item.id(),
                List.of(item.id())
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);

        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");
        assertEquals("external-review-provider", provenance.get("provider"));
        assertEquals("external-review-provider", provenance.get("model"));
        assertEquals("review-ai-provider-v1", provenance.get("providerVersion"));
        assertEquals("review-ai-summary-prompt-v1", provenance.get("promptVersion"));
        assertEquals(9004L, provenance.get("requestedBy"));
    }

    @Test
    void aiSummaryRedactsSensitiveProviderOutputAndRecordsOutputProvenance() {
        String phone = "13812345678";
        String identityNumber = "110101199001011234";
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> Optional.of(new ReviewAiSummary(
                                request.reviewCaseId(),
                                request.reviewItemIds(),
                                "call " + phone,
                                "identity " + identityNumber,
                                List.of("fact " + phone),
                                List.of("gap " + identityNumber),
                                List.of("notify " + phone),
                                LocalDateTime.of(2026, 6, 30, 15, 20),
                                "external-review-provider",
                                Map.of(
                                        "phoneNumber", phone,
                                        "nested", Map.of(
                                                "idCard", identityNumber,
                                                "notes", List.of("safe", "contact " + phone)
                                        )
                                )
                        ))
                )
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-ai-provider-output-redaction",
                LocalDateTime.of(2026, 6, 30, 15, 15),
                "ai-provider-output-redaction.jpg",
                "ai-provider-output-redaction.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI provider output redaction case",
                item.id(),
                List.of(item.id())
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);

        assertEquals("[REDACTED]", summary.title());
        assertEquals("[REDACTED]", summary.summary());
        assertEquals(List.of("[REDACTED]"), summary.keyFacts());
        assertEquals(List.of("[REDACTED]"), summary.evidenceGaps());
        assertEquals(List.of("[REDACTED]"), summary.recommendedActions());
        assertFalse(String.valueOf(summary).contains(phone));
        assertFalse(String.valueOf(summary).contains(identityNumber));
        Map<?, ?> nested = (Map<?, ?>) summary.structuredData().get("nested");
        assertEquals("[REDACTED]", summary.structuredData().get("phoneNumber"));
        assertEquals("[REDACTED]", nested.get("idCard"));
        assertEquals(List.of("safe", "[REDACTED]"), nested.get("notes"));
        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");
        assertEquals(true, provenance.get("outputRedacted"));
        List<?> outputRedactedFields = (List<?>) provenance.get("outputRedactedFields");
        assertTrue(outputRedactedFields.contains("title"));
        assertTrue(outputRedactedFields.contains("summary"));
        assertTrue(outputRedactedFields.contains("keyFacts[0]"));
        assertTrue(outputRedactedFields.contains("evidenceGaps[0]"));
        assertTrue(outputRedactedFields.contains("recommendedActions[0]"));
        assertTrue(outputRedactedFields.contains("structuredData.phoneNumber"));
        assertTrue(outputRedactedFields.contains("structuredData.nested.idCard"));
        assertTrue(outputRedactedFields.contains("structuredData.nested.notes[1]"));
        assertEquals("review-ai-provider-v1", provenance.get("providerVersion"));
        assertEquals("review-ai-summary-prompt-v1", provenance.get("promptVersion"));
    }

    @Test
    void aiSummaryRedactsSensitiveReviewDataBeforeProviderAndAuditProvenance() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> {
                            Map<?, ?> reviewData = request.items().get(0).reviewData();
                            Map<?, ?> motion = (Map<?, ?>) reviewData.get("motion");
                            assertEquals("[REDACTED]", motion.get("personName"));
                            assertEquals("[REDACTED]", motion.get("phoneNumber"));
                            assertEquals("[REDACTED]", motion.get("idCard"));
                            assertEquals(7, motion.get("consecutiveZoneFrames"));
                            assertFalse(String.valueOf(request.items()).contains("Resident Alice"));
                            assertFalse(String.valueOf(request.items()).contains("13812345678"));
                            assertFalse(String.valueOf(request.items()).contains("110101199001011234"));
                            return Optional.of(new ReviewAiSummary(
                                    request.reviewCaseId(),
                                    request.reviewItemIds(),
                                    "redacted provider case",
                                    "provider summary after redaction",
                                    List.of("redacted fact"),
                                    List.of(),
                                    List.of("confirm redacted context"),
                                    LocalDateTime.of(2026, 6, 30, 15, 20),
                                    "external-review-provider"
                            ));
                        }
                )
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-ai-redaction",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 30, 15, 15),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "ai-redaction.jpg",
                "ai-redaction.mp4",
                "payload-redaction",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-redaction"),
                0.91D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-redaction",
                List.of("obj-redaction"),
                LocalDateTime.of(2026, 6, 30, 15, 15, 5),
                List.of("voice"),
                Map.of(
                        "personName", "Resident Alice",
                        "phoneNumber", "13812345678",
                        "idCard", "110101199001011234",
                        "consecutiveZoneFrames", 7
                )
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI redaction case",
                item.id(),
                List.of(item.id())
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);

        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");
        assertEquals("applied", provenance.get("redactionStatus"));
        List<?> redactedFields = (List<?>) provenance.get("redactedFields");
        assertTrue(redactedFields.contains("items[0].reviewData.motion.personName"));
        assertTrue(redactedFields.contains("items[0].reviewData.motion.phoneNumber"));
        assertTrue(redactedFields.contains("items[0].reviewData.motion.idCard"));
        assertTrue(service.getReviewCaseTimeline(reviewCase.id()).stream()
                .filter(timelineItem -> "case_audit".equals(timelineItem.materialType()))
                .filter(timelineItem -> "ai_summary_generated".equals(timelineItem.materialUri()))
                .anyMatch(timelineItem -> timelineItem.actionNote().contains("redactionStatus=applied")));
    }

    @Test
    void aiSummaryRedactsTimelineNotesAndMaterialUrisBeforeProvider() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> {
                            String timelineText = String.valueOf(request.timeline());
                            assertFalse(timelineText.contains("Resident Alice"));
                            assertFalse(timelineText.contains("13812345678"));
                            assertFalse(timelineText.contains("110101199001011234"));
                            assertTrue(request.timeline().stream()
                                    .filter(timelineItem -> "record".equals(timelineItem.materialType()))
                                    .anyMatch(timelineItem -> "[REDACTED]".equals(timelineItem.materialUri())));
                            assertTrue(request.timeline().stream()
                                    .filter(timelineItem -> "case_audit".equals(timelineItem.materialType()))
                                    .anyMatch(timelineItem -> "[REDACTED]".equals(timelineItem.actionNote())));
                            return Optional.of(new ReviewAiSummary(
                                    request.reviewCaseId(),
                                    request.reviewItemIds(),
                                    "timeline redaction case",
                                    "provider summary after timeline redaction",
                                    List.of("timeline redacted fact"),
                                    List.of(),
                                    List.of("confirm sanitized timeline"),
                                    LocalDateTime.of(2026, 6, 30, 15, 40),
                                    "external-review-provider"
                            ));
                        }
                )
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-ai-timeline-redaction",
                LocalDateTime.of(2026, 6, 30, 15, 35),
                "https://snapshot.example/Resident-Alice/110101199001011234.jpg",
                "https://video.example/records/13812345678/clip-110101199001011234.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI timeline redaction case",
                item.id(),
                List.of(item.id())
        ));
        service.assignReviewCaseOwner(new ReviewCaseOwnerCommand(
                reviewCase.id(),
                9100L,
                9004L,
                "handoff Resident Alice phone 13812345678 id 110101199001011234"
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);

        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");
        assertEquals("applied", provenance.get("redactionStatus"));
        List<?> redactedFields = (List<?>) provenance.get("redactedFields");
        assertTrue(redactedFields.stream()
                .map(String::valueOf)
                .anyMatch(path -> path.startsWith("timeline[") && path.endsWith(".materialUri")));
        assertTrue(redactedFields.stream()
                .map(String::valueOf)
                .anyMatch(path -> path.startsWith("timeline[") && path.endsWith(".actionNote")));
        List<ReviewCaseTimelineItem> persistedTimeline = service.getReviewCaseTimeline(reviewCase.id());
        assertTrue(persistedTimeline.stream()
                .anyMatch(timelineItem -> String.valueOf(timelineItem.materialUri()).contains("13812345678")));
        assertTrue(persistedTimeline.stream()
                .anyMatch(timelineItem -> String.valueOf(timelineItem.actionNote()).contains("Resident Alice")));
    }

    @Test
    void aiSummaryRedactionPolicyCanBeOverriddenAndRecordedInProvenance() {
        ReviewAiSummaryRedactionPolicy policy = new ReviewAiSummaryRedactionPolicy();
        policy.setPolicyVersion("custom-redaction-v2");
        policy.setSensitiveKeys(List.of("residentAlias"));
        policy.setSensitiveValuePatterns(List.of("(?s).*custom-secret-[0-9]+.*"));
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> {
                            Map<?, ?> reviewData = request.items().get(0).reviewData();
                            Map<?, ?> motion = (Map<?, ?>) reviewData.get("motion");
                            assertEquals("[REDACTED]", motion.get("residentAlias"));
                            assertFalse(String.valueOf(request).contains("Dormitory Lead"));
                            assertFalse(String.valueOf(request).contains("custom-secret-42"));
                            return Optional.of(new ReviewAiSummary(
                                    request.reviewCaseId(),
                                    request.reviewItemIds(),
                                    "custom policy case",
                                    "provider summary after custom policy",
                                    List.of("custom policy fact"),
                                    List.of(),
                                    List.of("confirm policy version"),
                                    LocalDateTime.of(2026, 6, 30, 16, 0),
                                    "external-review-provider"
                            ));
                        }
                ),
                policy
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-ai-policy-redaction",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 30, 15, 55),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "custom-secret-42-snapshot.jpg",
                "custom-secret-42-record.mp4",
                "payload-policy-redaction",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-policy"),
                0.92D,
                List.of(0.1D, 0.2D, 0.3D, 0.4D),
                "corr-policy",
                List.of("obj-policy"),
                LocalDateTime.of(2026, 6, 30, 15, 55, 5),
                List.of("voice"),
                Map.of(
                        "residentAlias", "Dormitory Lead",
                        "consecutiveZoneFrames", 9
                )
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI custom policy case",
                item.id(),
                List.of(item.id())
        ));

        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);

        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");
        assertEquals("custom-redaction-v2", provenance.get("redactionPolicyVersion"));
        assertEquals("applied", provenance.get("redactionStatus"));
        List<?> redactedFields = (List<?>) provenance.get("redactedFields");
        assertTrue(redactedFields.contains("items[0].reviewData.motion.residentAlias"));
        assertTrue(redactedFields.stream()
                .map(String::valueOf)
                .anyMatch(path -> path.startsWith("timeline[") && path.endsWith(".materialUri")));
    }
    @Test
    void aiSummaryConfirmationKeepsGeneratedProvenanceAndIsIdempotentForSameStatus() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                request -> List.of(),
                new StubReviewIntelligenceProvider(
                        request -> Optional.empty(),
                        request -> Optional.of(new ReviewAiSummary(
                                request.reviewCaseId(),
                                request.reviewItemIds(),
                                "confirmation case",
                                "summary needing human confirmation",
                                List.of("person stayed in zone"),
                                List.of(),
                                List.of("confirm with recording"),
                                LocalDateTime.of(2026, 6, 30, 15, 30),
                                "external-review-provider"
                        ))
                )
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-ai-confirmation",
                LocalDateTime.of(2026, 6, 30, 15, 25),
                "ai-confirmation.jpg",
                "ai-confirmation.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI confirmation case",
                item.id(),
                List.of(item.id())
        ));
        ReviewAiSummary summary = service.summarizeReviewCase(reviewCase.id(), 9004L);
        Map<?, ?> provenance = (Map<?, ?>) summary.structuredData().get("aiProvenance");

        ReviewAiSummaryConfirmation confirmed = service.confirmReviewCaseAiSummary(
                new ReviewAiSummaryConfirmationCommand(
                        reviewCase.id(),
                        "confirmed",
                        "checked recording and snapshot",
                        9100L
                ));
        ReviewAiSummaryConfirmation duplicate = service.confirmReviewCaseAiSummary(
                new ReviewAiSummaryConfirmationCommand(
                        reviewCase.id(),
                        "confirmed",
                        "checked recording and snapshot",
                        9100L
                ));
        ReviewAiSummaryConfirmation rejected = service.confirmReviewCaseAiSummary(
                new ReviewAiSummaryConfirmationCommand(
                        reviewCase.id(),
                        "rejected",
                        "summary missed the timeline gap",
                        9101L
                ));

        assertEquals("confirmed", confirmed.confirmationStatus());
        assertFalse(confirmed.duplicate());
        assertEquals(provenance.get("promptHash"), confirmed.promptHash());
        assertEquals("review-ai-summary-prompt-v1", confirmed.promptVersion());
        assertTrue(confirmed.summaryHash().startsWith("sha256:"));
        assertEquals(9100L, confirmed.operatorUserId());
        assertEquals("checked recording and snapshot", confirmed.notes());
        assertEquals("confirmed", duplicate.confirmationStatus());
        assertTrue(duplicate.duplicate());
        assertEquals("rejected", rejected.confirmationStatus());
        assertFalse(rejected.duplicate());
        assertEquals("confirmed", rejected.previousConfirmationStatus());

        List<ReviewCaseTimelineItem> aiConfirmationAudits = service.getReviewCaseTimeline(reviewCase.id()).stream()
                .filter(timelineItem -> "case_audit".equals(timelineItem.materialType()))
                .filter(timelineItem -> timelineItem.materialUri().startsWith("ai_summary_"))
                .filter(timelineItem -> !"ai_summary_generated".equals(timelineItem.materialUri()))
                .toList();
        assertEquals(2, aiConfirmationAudits.size());
        assertTrue(aiConfirmationAudits.stream().anyMatch(timelineItem -> "ai_summary_confirmed".equals(timelineItem.materialUri())
                && timelineItem.actionNote().contains("humanConfirmationStatus=confirmed")
                && timelineItem.actionNote().contains("promptHash=" + provenance.get("promptHash"))
                && timelineItem.actionNote().contains("summaryHash=sha256:")
                && timelineItem.actionNote().contains("operatorUserId=9100")));
        assertTrue(aiConfirmationAudits.stream().anyMatch(timelineItem -> "ai_summary_rejected".equals(timelineItem.materialUri())
                && timelineItem.actionNote().contains("humanConfirmationStatus=rejected")
                && timelineItem.actionNote().contains("previousHumanConfirmationStatus=confirmed")
                && timelineItem.actionNote().contains("operatorUserId=9101")));
    }

    @Test
    void aiSummaryConfirmationRequiresGeneratedSummaryAudit() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-ai-confirmation-missing",
                LocalDateTime.of(2026, 6, 30, 16, 5),
                "ai-confirmation-missing.jpg",
                "ai-confirmation-missing.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "AI confirmation missing case",
                item.id(),
                List.of(item.id())
        ));

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> service.confirmReviewCaseAiSummary(new ReviewAiSummaryConfirmationCommand(
                        reviewCase.id(),
                        "confirmed",
                        "no generated summary",
                        9100L
                )));

        assertTrue(rejected.getMessage().contains("AI summary generation audit is required"));
    }

    @Test
    void evidenceExportDefaultsAndNormalizesVideoFormatsToRealMp4() {
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "video-export-default-mp4",
                        "https://eye.yfeiai.com/exports/video-export-default-mp4.mp4",
                        "ready",
                        "VIDEO export ready",
                        "https://eye.yfeiai.com/exports/video-export-default-mp4/manifest",
                        validSha256('5'),
                        List.of(Map.of(
                                "sourceHash", validSha256('6'),
                                "ffmpegCommandHash", validSha256('7'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", 0
                        )),
                        validSha256('8')
                ))
        );
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-video-export-default",
                LocalDateTime.of(2026, 7, 11, 12, 0),
                "video-export-default.jpg",
                "video-export-default.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "default real video export",
                item.id(),
                List.of(item.id())
        ));

        ReviewEvidenceExportPackage defaultExport = service.exportReviewEvidence(
                new ReviewEvidenceExportCommand(reviewCase.id(), List.of(item.id()), 9400L, null));
        ReviewEvidenceExportPackage videoAliasExport = service.exportReviewEvidence(
                new ReviewEvidenceExportCommand(reviewCase.id(), List.of(item.id()), 9400L, "video"));

        assertEquals("mp4", defaultExport.format());
        assertEquals("mp4", videoAliasExport.format());
        assertEquals(List.of("mp4", "mp4"), videoExportProvider.requests().stream()
                .map(ReviewEvidenceVideoExportRequest::format)
                .toList());
    }

    @Test
    void evidenceExportRequestsVideoProviderAndKeepsExportTaskInManifest() {
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "video-export-001",
                        "https://eye.yfeiai.com/exports/video-export-001.mp4",
                        "ready",
                        "VIDEO export ready",
                        "https://eye.yfeiai.com/exports/video-export-001/manifest",
                        validSha256('1'),
                        List.of(Map.of(
                                "sourceHash", validSha256('2'),
                                "ffmpegCommandHash", validSha256('3'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", 0
                        )),
                        validSha256('4')
                ))
        );
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 6, 30, 16, 0);
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-video-export",
                alertTime,
                "video-export.jpg",
                "video-export.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "video export case",
                item.id(),
                List.of(item.id())
        ));

        ReviewEvidenceExportPackage exportPackage = service.exportReviewEvidence(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                null,
                9003L,
                "mp4"
        ));

        assertEquals(1, videoExportProvider.requests().size());
        ReviewEvidenceVideoExportRequest request = videoExportProvider.requests().get(0);
        assertEquals(reviewCase.id(), request.reviewCaseId());
        assertEquals(item.id(), request.reviewItemId());
        assertEquals("camera-01", request.cameraId());
        assertEquals("alert-video-export", request.sourceAlertId());
        assertEquals("video-export.mp4", request.recordUri());
        assertEquals("mp4", request.format());
        Object videoExports = exportPackage.manifest().get("videoExports");
        assertTrue(videoExports instanceof List<?>);
        assertEquals("video-export-001", ((Map<?, ?>) ((List<?>) videoExports).get(0)).get("exportId"));
        assertTrue(exportPackage.evidenceUris().contains("https://eye.yfeiai.com/exports/video-export-001.mp4"));
    }

    @Test
    void evidenceExportRejectsProviderReadyResultWithoutVerifiableProvenance() {
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "video-export-fake-hash",
                        "https://eye.yfeiai.com/exports/video-export-fake-hash.mp4",
                        "ready",
                        "not actually verified",
                        "https://eye.yfeiai.com/exports/video-export-fake-hash/manifest",
                        "sha256:media",
                        List.of(Map.of(
                                "sourceHash", "sha256:source",
                                "ffmpegCommandHash", "sha256:command",
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", 0
                        )),
                        "sha256:export-command"
                ))
        );
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-video-export-fake-hash",
                LocalDateTime.of(2026, 6, 30, 16, 10),
                "video-export-fake-hash.jpg",
                "video-export-fake-hash.mp4"
        ));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "invalid video export case",
                item.id(),
                List.of(item.id())
        ));

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> service.exportReviewEvidence(new ReviewEvidenceExportCommand(
                        reviewCase.id(),
                        null,
                        9003L,
                        "mp4"
                )));

        assertTrue(rejected.getMessage().contains("evidence job cannot be ready"));
        assertEquals(1, videoExportProvider.requests().size());
    }

    @Test
    void reviewReconciliationRepairsRecordAndSemanticDriftAndReportsHealthMetrics() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 9, 30);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "available",
                        alertTime.minusMinutes(5),
                        alertTime.plusMinutes(5),
                        null,
                        "reconciled-record.mp4"
                ))
        );
        ReviewItemAggregate missingRecord = service.ingestClue(newClue(
                "alert-reconcile",
                alertTime,
                "reconcile.jpg",
                null
        ));
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(new ReviewQuery(null, null, null, null), 9100L));

        ReviewRuntimeHealthReport before = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9101L
        ));
        ReviewReconciliationResult result = service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9102L,
                true
        ));
        ReviewItemAggregate repaired = service.listWorkbench(new ReviewQuery(null, null, null, null)).get(0);
        ReviewSemanticIndexEvaluation semanticHealth = service.evaluateSemanticIndex(new ReviewSemanticIndexEvaluationCommand(
                new ReviewQuery(null, null, null, null),
                9103L
        ));

        assertEquals(1, before.missingRecordCount());
        assertEquals(1, before.semanticBacklogCount());
        assertTrue(before.alerts().contains("record_evidence_gap"));
        assertEquals(1, result.scannedCount());
        assertEquals(1, result.repairedRecordCount());
        assertEquals(1, result.repairedSemanticIndexCount());
        assertTrue(result.findings().contains("record_repaired:" + missingRecord.id()));
        assertTrue(result.findings().contains("semantic_reindexed:" + missingRecord.id()));
        assertEquals("found", repaired.recordEvidenceStatus());
        assertTrue(service.getTimeline(repaired.id()).stream()
                .anyMatch(evidence -> "record".equals(evidence.materialType())
                        && "reconciled-record.mp4".equals(evidence.materialUri())));
        assertTrue(semanticHealth.staleReviewItemIds().isEmpty());
        assertEquals(0, result.healthReport().missingRecordCount());
        assertEquals(0, result.healthReport().semanticBacklogCount());
    }

    @Test
    void reviewReconciliationRepairsReviewDataSchemaAndSegmentDoubleWriteDrift() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 3, 11, 30);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-reviewdata-repair",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                20,
                "reviewdata-repair.jpg",
                "reviewdata-repair.mp4",
                "payload-reviewdata-repair",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-reviewdata"),
                0.91D,
                List.of(0.11D, 0.21D, 0.31D, 0.41D),
                "corr-reviewdata-repair"
        ));
        String originalSegmentId = service.getReviewSegment(item.id()).segmentId();
        Map<String, Object> legacyReviewData = new LinkedHashMap<>(item.reviewData());
        legacyReviewData.remove("reviewDataVersion");
        legacyReviewData.remove("reviewSegment");
        itemStore.updateReviewLifecycle(
                item.id(),
                Map.copyOf(legacyReviewData),
                item.firstAlertTime(),
                item.lastAlertTime(),
                List.of(),
                item.recordEvidenceStatus(),
                item.recordEvidenceCheckedAt(),
                item.recordEvidenceMessage()
        );

        ReviewRuntimeHealthReport before = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9301L
        ));
        ReviewReconciliationResult result = service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9302L,
                true
        ));
        ReviewItemAggregate repaired = service.listWorkbench(new ReviewQuery(null, null, null, null)).get(0);
        Map<?, ?> segment = (Map<?, ?>) repaired.reviewData().get("reviewSegment");

        assertTrue(before.alerts().contains("review_data_schema_drift"));
        assertTrue(before.alerts().contains("review_segment_double_write_drift"));
        assertEquals(3, before.repairableCount());
        assertTrue(result.findings().contains("review_data_repaired:" + item.id()));
        assertTrue(result.findings().contains("review_segment_repaired:" + item.id()));
        assertEquals(1, repaired.reviewData().get("reviewDataVersion"));
        assertEquals("corr-reviewdata-repair", repaired.reviewData().get("correlationId"));
        assertEquals("camera-01", segment.get("cameraId"));
        assertEquals(originalSegmentId, segment.get("segmentId"));
        assertEquals(alertTime.toString(), segment.get("startTime"));
        assertEquals(alertTime.toString(), segment.get("endTime"));
        assertEquals(List.of("obj-reviewdata"), segment.get("objectIds"));
        assertEquals(List.of("zone-a"), segment.get("zones"));
        assertFalse(result.healthReport().alerts().contains("review_data_schema_drift"));
        assertFalse(result.healthReport().alerts().contains("review_segment_double_write_drift"));
    }

    @Test
    void reviewReconciliationKeepsPersistedSegmentIdAfterOutOfOrderMerge() {
        LocalDateTime originalTime = LocalDateTime.of(2026, 7, 13, 10, 0);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate original = service.ingestClue(newClue(
                "alert-original",
                originalTime,
                "original.jpg",
                "original.mp4"
        ));
        String originalSegmentId = service.getReviewSegment(original.id()).segmentId();
        ReviewItemAggregate merged = service.ingestClue(newClue(
                "alert-earlier",
                originalTime.minusSeconds(10),
                "earlier.jpg",
                "earlier.mp4"
        ));
        Map<String, Object> legacyReviewData = new LinkedHashMap<>(merged.reviewData());
        legacyReviewData.remove("reviewSegment");
        itemStore.updateReviewLifecycle(
                merged.id(),
                Map.copyOf(legacyReviewData),
                merged.firstAlertTime(),
                merged.lastAlertTime(),
                List.of(),
                merged.recordEvidenceStatus(),
                merged.recordEvidenceCheckedAt(),
                merged.recordEvidenceMessage()
        );

        service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9303L,
                true
        ));

        ReviewItemAggregate repaired = service.listWorkbench(new ReviewQuery(null, null, null, null)).get(0);
        Map<?, ?> repairedSegment = (Map<?, ?>) repaired.reviewData().get("reviewSegment");
        assertEquals(originalSegmentId, repairedSegment.get("segmentId"));
        assertEquals(originalTime.minusSeconds(10), repaired.firstAlertTime());
    }

    @Test
    void reviewReconciliationKeepsPersistedSegmentIdAfterTimestampPrecisionRoundTrip() {
        LocalDateTime originalTime = LocalDateTime.of(2026, 7, 13, 10, 30, 0, 123_456_789);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate original = service.ingestClue(newClue(
                "alert-nanosecond",
                originalTime,
                "nanosecond.jpg",
                "nanosecond.mp4"
        ));
        String originalSegmentId = service.getReviewSegment(original.id()).segmentId();
        Map<String, Object> legacyReviewData = new LinkedHashMap<>(original.reviewData());
        legacyReviewData.remove("reviewSegment");
        LocalDateTime databaseTime = originalTime.withNano(123_456_000);
        itemStore.updateReviewLifecycle(
                original.id(),
                Map.copyOf(legacyReviewData),
                databaseTime,
                databaseTime,
                List.of(),
                original.recordEvidenceStatus(),
                original.recordEvidenceCheckedAt(),
                original.recordEvidenceMessage()
        );

        service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9304L,
                true
        ));

        ReviewItemAggregate repaired = service.listWorkbench(new ReviewQuery(null, null, null, null)).get(0);
        Map<?, ?> repairedSegment = (Map<?, ?>) repaired.reviewData().get("reviewSegment");
        assertEquals(originalSegmentId, repairedSegment.get("segmentId"));
        assertEquals(databaseTime, repaired.firstAlertTime());
    }

    @Test
    void reviewReconciliationDetectsMissingPersistedSegmentAndRepairsAfterReread() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 9, 10);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-persisted-segment-missing",
                alertTime,
                "persisted-segment-missing.jpg",
                "persisted-segment-missing.mp4"
        ));
        itemStore.removePersistedReviewSegmentForTest(item.id());

        ReviewRuntimeHealthReport before = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9320L
        ));
        ReviewReconciliationResult repaired = service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9321L,
                true
        ));
        ReviewRuntimeHealthReport reread = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9322L
        ));

        assertTrue(before.alerts().contains("review_segment_double_write_drift"));
        assertTrue(repaired.findings().contains("review_segment_repaired:" + item.id()));
        assertTrue(itemStore.findPersistedReviewSegment(item.id()).isPresent());
        assertFalse(reread.alerts().contains("review_segment_double_write_drift"));
    }

    @Test
    void reviewReconciliationDetectsTamperedPersistedSegmentAndRepairsAfterReread() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 9, 20);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-persisted-segment-tampered",
                alertTime,
                "persisted-segment-tampered.jpg",
                "persisted-segment-tampered.mp4"
        ));
        itemStore.tamperPersistedReviewSegmentCameraForTest(item.id(), "camera-other-tenant");

        ReviewRuntimeHealthReport before = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9330L
        ));
        ReviewReconciliationResult repaired = service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9331L,
                true
        ));
        ReviewSegmentView persisted = itemStore.findPersistedReviewSegment(item.id()).orElseThrow();

        assertTrue(before.alerts().contains("review_segment_double_write_drift"));
        assertTrue(repaired.findings().contains("review_segment_repaired:" + item.id()));
        assertEquals("camera-01", persisted.cameraId());
        assertFalse(repaired.healthReport().alerts().contains("review_segment_double_write_drift"));
    }

    @Test
    void reviewReconciliationRemovesInvalidOptionalReviewDataFields() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 10, 8, 30);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-invalid-legacy-reviewdata",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                10,
                "legacy-invalid.jpg",
                null,
                "legacy-invalid-hash",
                List.of("person"),
                List.of("zone-a"),
                List.of("object-01"),
                0.8D,
                List.of(1D, 2D, 3D, 4D),
                "corr-valid"
        ));
        Map<String, Object> invalidReviewData = new LinkedHashMap<>(item.reviewData());
        invalidReviewData.put("confidence", 1.7D);
        invalidReviewData.put("bbox", List.of(1D, 2D, 3D));
        invalidReviewData.put("correlationId", 123L);
        itemStore.updateReviewLifecycle(
                item.id(),
                Map.copyOf(invalidReviewData),
                item.firstAlertTime(),
                item.lastAlertTime(),
                List.of(),
                item.recordEvidenceStatus(),
                item.recordEvidenceCheckedAt(),
                item.recordEvidenceMessage()
        );

        ReviewRuntimeHealthReport before = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9310L
        ));
        ReviewReconciliationResult repaired = service.reconcileReviewRuntime(new ReviewReconciliationCommand(
                new ReviewQuery(null, null, null, null),
                9311L,
                true
        ));
        ReviewItemAggregate normalized = service.listWorkbench(new ReviewQuery(null, null, null, null)).get(0);

        assertTrue(before.alerts().contains("review_data_schema_drift"));
        assertTrue(repaired.findings().contains("review_data_repaired:" + item.id()));
        assertFalse(normalized.reviewData().containsKey("confidence"));
        assertFalse(normalized.reviewData().containsKey("bbox"));
        assertFalse(normalized.reviewData().containsKey("correlationId"));
        assertFalse(repaired.healthReport().alerts().contains("review_data_schema_drift"));
    }

    @Test
    void integrationSmokeCoversReviewRecordCaseExportAndManifestVerification() throws Exception {
        LocalDateTime smokeTime = LocalDateTime.of(2026, 7, 2, 10, 0);
        byte[] smokeVideoBytes = "integration-smoke-real-video-bytes".getBytes(StandardCharsets.UTF_8);
        String smokeVideoHash = sha256Bytes(smokeVideoBytes);
        CapturingVideoEvidenceExportProvider videoExportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "smoke-video-export",
                        "https://eye.yfeiai.com/exports/smoke-video-export.mp4",
                        "ready",
                        "smoke export ready",
                        "https://eye.yfeiai.com/exports/smoke-video-export/manifest",
                        smokeVideoHash,
                        List.of(Map.of(
                                "sourceHash", validSha256('6'),
                                "ffmpegCommandHash", validSha256('7'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", 0
                        )),
                        validSha256('8')
                )),
                Optional.of(temporaryArtifact(
                        null,
                        "smoke-video-export.mp4",
                        "video/mp4",
                        smokeVideoBytes,
                        smokeVideoHash
                ))
        );
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        AtomicReference<AlertToEventCommand> smokeEventCommand = new AtomicReference<>();
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                ruleStore,
                command -> {
                    smokeEventCommand.set(command);
                    return new AlertToEventResult(
                            7600L,
                            command.sourceSystem(),
                            command.sourceAlertId(),
                            command.ruleCode(),
                            "supervision_order",
                            SupervisionEventLevelEnum.L2,
                            SupervisionEventStatusEnum.ACCEPTED.getCode(),
                            false
                    );
                },
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "motion",
                        smokeTime.minusSeconds(30),
                        smokeTime.plusSeconds(90),
                        3,
                        "smoke-record.mp4"
                )),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );

        ReviewIntegrationSmokeResult smoke = service.runIntegrationSmoke(new ReviewIntegrationSmokeCommand(
                9200L,
                true,
                smokeTime
        ));

        assertEquals("passed", smoke.status());
        assertTrue(smoke.reviewItemId() > 0);
        assertEquals(7600L, smoke.eventId());
        assertTrue(smoke.reviewCaseId() > 0);
        assertTrue(smoke.exportJobNo().startsWith("REJ-"));
        assertTrue(smoke.manifestValid());
        assertTrue(smoke.videoExportRequested());
        assertEquals(1, videoExportProvider.requests().size());
        assertTrue(smoke.checkpoints().contains("ingest_review_item"));
        assertTrue(smoke.checkpoints().contains("review_event_bound"));
        assertTrue(smoke.checkpoints().contains("review_event_bound_without_task_dispatch"));
        assertFalse(smokeEventCommand.get().dispatchTasks());
        assertTrue(smoke.checkpoints().contains("record_coverage_synced"));
        assertTrue(smoke.checkpoints().contains("review_case_created"));
        assertTrue(smoke.checkpoints().contains("evidence_export_ready"));
        assertTrue(smoke.checkpoints().contains("evidence_export_event_bound"));
        assertTrue(smoke.checkpoints().contains("manifest_verified"));
        assertTrue(smoke.checkpoints().contains("evidence_download_bytes_verified"));
        assertTrue(smoke.checkpoints().contains("evidence_download_audited"));
        assertTrue(smoke.checkpoints().contains("review_rule_saved"));
        assertEquals(1, ruleStore.listAll().size());
        ReviewRuleView smokeRule = ruleStore.listAll().get(0);
        assertTrue(smoke.smokeRule() != null);
        assertEquals("camera-smoke", smoke.smokeRule().cameraId());
        assertEquals("zone-smoke", smoke.smokeRule().zoneCode());
        assertEquals("person", smoke.smokeRule().objectLabel());
        assertEquals(3, smoke.smokeRule().inertiaFrames());
        assertEquals(20, smoke.smokeRule().loiteringSeconds());
        assertEquals("camera-smoke", smokeRule.cameraId());
        assertEquals("zone-smoke", smokeRule.zoneCode());
        assertEquals("person", smokeRule.objectLabel());
        assertEquals(3, smokeRule.inertiaFrames());
        assertEquals(20, smokeRule.loiteringSeconds());
        assertEquals(1, videoExportProvider.downloadRequests().size());
        assertTrue(service.getEvidenceAuditTrail(smoke.reviewCaseId()).stream()
                .anyMatch(entry -> "export_downloaded".equals(entry.actionType())
                        && Objects.equals(9200L, entry.operatorUserId())
                        && entry.boundEventIds().contains(7600L)
                        && "integration smoke download audit".equals(entry.actionNote())));
    }

    @Test
    void realIntegrationSmokeUsesRequestedCameraAndRequiresRealVideoEvidence() throws Exception {
        LocalDateTime smokeTime = LocalDateTime.of(2026, 7, 10, 6, 11, 30);
        String cameraId = "gb28181-real-camera";
        byte[] realVideoBytes = "real-camera-export-video-bytes".getBytes(StandardCharsets.UTF_8);
        String realVideoHash = sha256Bytes(realVideoBytes);
        CapturingRecordEvidenceResolver recordResolver = new CapturingRecordEvidenceResolver(
                Optional.of(new RecordEvidenceResult("/video/record/real.flv", "alert_record_match"))
        );
        CapturingRecordCoverageResolver coverageResolver = new CapturingRecordCoverageResolver(List.of(
                new RecordCoverageSegment(
                        "motion",
                        smokeTime.minusSeconds(25),
                        smokeTime.plusSeconds(5),
                        2,
                        "/video/record/real.flv"
                )
        ));
        CapturingVideoEvidenceExportProvider exportProvider = new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "real-export-001",
                        "/video/record/export/real-export-001/download",
                        "ready",
                        "real ffmpeg export ready",
                        "/video/record/export/real-export-001/manifest",
                        realVideoHash,
                        List.of(Map.of(
                                "sourceHash", validSha256('a'),
                                "ffmpegCommandHash", validSha256('b'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 30D),
                                "stitchOrder", 0
                        )),
                        validSha256('c')
                )),
                Optional.of(temporaryArtifact(
                        null,
                        "real-export-001.mp4",
                        "video/mp4",
                        realVideoBytes,
                        realVideoHash
                ))
        );
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                eventServiceReturning(7601L),
                recordResolver,
                noEventProjectionStore(),
                coverageResolver,
                ReviewIntelligenceProvider.unavailable(),
                exportProvider
        );

        ReviewIntegrationSmokeResult smoke = service.runIntegrationSmoke(new ReviewIntegrationSmokeCommand(
                9201L,
                true,
                smokeTime,
                "device-video-web",
                cameraId,
                cameraId,
                "zone-real",
                "alert-real-001",
                List.of(cameraId)
        ));

        assertEquals("passed", smoke.status());
        assertTrue(smoke.videoExportConfirmed());
        assertTrue(smoke.checkpoints().contains("review_event_bound"));
        assertTrue(smoke.checkpoints().contains("evidence_export_event_bound"));
        assertTrue(smoke.checkpoints().contains("video_record_query_checked"));
        assertTrue(smoke.checkpoints().contains("real_record_coverage_checked"));
        assertTrue(smoke.checkpoints().contains("video_export_confirmed"));
        assertTrue(smoke.checkpoints().contains("evidence_download_bytes_verified"));
        assertEquals(cameraId, smoke.smokeRule().cameraId());
        assertEquals("zone-real", smoke.smokeRule().zoneCode());
        assertEquals(List.of(new RecordEvidenceRequest(
                "alert-real-001",
                cameraId,
                cameraId,
                smokeTime
        )), recordResolver.requests());
        assertEquals(cameraId, coverageResolver.requests().get(0).cameraId());
        assertEquals(cameraId, exportProvider.requests().get(0).cameraId());
        assertEquals(cameraId, exportProvider.downloadRequests().get(0).cameraId());
    }

    @Test
    void realIntegrationSmokeRejectsDeviceCameraScopeMismatchBeforeIngest() {
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                eventServiceReturning(7602L)
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.runIntegrationSmoke(new ReviewIntegrationSmokeCommand(
                        9202L,
                        true,
                        LocalDateTime.of(2026, 7, 10, 6, 12),
                        "release",
                        "device-real",
                        "camera-real",
                        "zone-real",
                        "alert-scope-mismatch",
                        List.of("camera-real")
                )));

        assertTrue(error.getMessage().contains("same VIDEO camera"));
    }

    @Test
    void realIntegrationSmokeFailsWhenVideoExportDoesNotReturnAnArtifact() {
        LocalDateTime smokeTime = LocalDateTime.of(2026, 7, 10, 6, 12);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                eventServiceReturning(7602L),
                request -> Optional.of(new RecordEvidenceResult("/video/record/real.flv", "alert_record_match")),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "motion",
                        smokeTime.minusSeconds(20),
                        smokeTime.plusSeconds(10),
                        1,
                        "/video/record/real.flv"
                )),
                ReviewIntelligenceProvider.unavailable(),
                VideoEvidenceExportProvider.unavailable()
        );
        ReviewIntegrationSmokeCommand command = new ReviewIntegrationSmokeCommand(
                9202L,
                true,
                smokeTime,
                "release",
                "camera-real",
                "camera-real",
                "zone-real",
                "alert-real-002",
                List.of("camera-real")
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.runIntegrationSmoke(command));

        assertTrue(error.getMessage().contains("VIDEO export failed"));
    }

    @Test
    void integrationSmokeRejectsNonPositiveConvertedEventId() {
        LocalDateTime smokeTime = LocalDateTime.of(2026, 7, 10, 6, 13);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                eventServiceReturning(0L)
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.runIntegrationSmoke(new ReviewIntegrationSmokeCommand(
                        9203L,
                        false,
                        smokeTime
                )));

        assertTrue(error.getMessage().contains("positive eventId"));
    }

    @Test
    void integrationSmokeRejectsReadyExportThatLostEventBinding() {
        LocalDateTime smokeTime = LocalDateTime.of(2026, 7, 10, 6, 14);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore() {
            @Override
            public ReviewEvidenceExportJob completeExportJobClaim(ReviewEvidenceExportJob job, String claimToken) {
                return super.completeExportJobClaim(new ReviewEvidenceExportJob(
                        job.jobNo(),
                        job.status(),
                        job.exportPackage(),
                        job.fileHash(),
                        job.expiresAt(),
                        job.operatorUserId(),
                        job.reason(),
                        List.of(),
                        job.createdAt(),
                        job.version()
                ), claimToken);
            }
        };
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                eventServiceReturning(7603L)
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.runIntegrationSmoke(new ReviewIntegrationSmokeCommand(
                        9204L,
                        false,
                        smokeTime
                )));

        assertTrue(error.getMessage().contains("event binding"));
    }

    @Test
    void ruleGeometryUsesBottomCenterSemanticsAndReplayRequiresSafeVersionLifecycle() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(new InMemoryReviewItemStore(), ruleStore, unusedEventService());
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 2, 11, 0);
        service.saveRule(new ReviewRuleCommand(
                null,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted area v1",
                "video",
                "camera-01",
                "zone-a",
                "person",
                5,
                baseTime.minusDays(1),
                null,
                true
        ));
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-geometry",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                "geometry.jpg",
                "geometry.mp4",
                null,
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-geometry"),
                0.92D,
                List.of(5D, -10D, 15D, 5D),
                "corr-geometry"
        ));

        ReviewRuleGeometryEvaluation geometry = service.evaluateRuleGeometry(new ReviewRuleGeometryCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "camera-01",
                "zone-a",
                List.of(
                        List.of(0D, 0D),
                        List.of(20D, 0D),
                        List.of(20D, 20D),
                        List.of(0D, 20D)
                ),
                List.of(5D, -10D, 15D, 5D),
                "person",
                new ReviewQuery(null, "camera-01", null, null),
                9300L
        ));
        ReviewRuleReplayResult replay = service.replayRule(new ReviewRuleReplayCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "video",
                "camera-01",
                "zone-a",
                "person",
                20,
                baseTime.minusMinutes(5),
                baseTime.plusMinutes(5),
                9301L
        ));
        Map<?, ?> lifecyclePolicy = (Map<?, ?>) replay.report().get("ruleLifecyclePolicy");
        Map<?, ?> geometrySemantics = (Map<?, ?>) replay.report().get("geometrySemantics");

        assertEquals("bottom_center", geometry.geometryType());
        assertEquals(List.of(10D, 5D), geometry.evaluatedPoint());
        assertTrue(geometry.inside());
        assertTrue(geometry.replayedReviewItemIds().contains(item.id()));
        assertEquals("shadow", geometry.ruleVersion().get("applicationMode"));
        assertTrue(geometry.consistencyChecks().contains("front_back_replay_use_bottom_center"));
        assertEquals(false, lifecyclePolicy.get("directApplyAllowed"));
        assertEquals(true, lifecyclePolicy.get("shadowEvaluationRequired"));
        assertEquals(true, lifecyclePolicy.get("approvalRequired"));
        assertEquals(true, lifecyclePolicy.get("rollbackSupported"));
        assertEquals("bottom_center", geometrySemantics.get("objectPoint"));
    }

    @Test
    void evidenceManifestV2AndVerifierReconstructDecisionTrailAndAuditChain() throws Exception {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        CapturingVideoEvidenceExportProvider videoExportProvider = singleCameraDownloadableProvider(
                "camera-01",
                "manifest-v2-real-video".getBytes(StandardCharsets.UTF_8)
        );
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                videoExportProvider
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 2, 12, 0);
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-manifest-v2",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                "manifest-v2.jpg",
                "manifest-v2.mp4",
                "payload-manifest-v2",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-manifest-v2"),
                0.96D,
                List.of(1D, 2D, 9D, 18D),
                "corr-manifest-v2"
        ));
        service.markFalsePositive(new ReviewOperationCommand(item.id(), 9400L, "known staff"));
        ReviewCaseView reviewCase = service.createReviewCase(new ReviewCaseCommand(
                "manifest v2 case",
                item.id(),
                List.of(item.id())
        ));
        ReviewEvidenceExportJob queuedJob = service.createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                9401L,
                "mp4",
                "evidence reproducibility",
                9402L,
                "approved for replay"
        ));
        service.processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(10, 9401L));
        ReviewEvidenceExportJob job = itemStore.findExportJobByNo(queuedJob.jobNo()).orElseThrow();
        try (ReviewEvidenceDownloadArtifact ignored = service.downloadEvidencePackage(
                new ReviewEvidenceDownloadCommand(
                        job.jobNo(), 9403L, List.of("camera-01"), "verifier download"))) {
            assertTrue(ignored.contentLength() > 0);
        }

        ReviewEvidenceVerificationReport verification = service.verifyEvidencePackage(new ReviewEvidenceVerificationCommand(
                job.jobNo(),
                9404L
        ));
        Map<String, Object> manifest = job.exportPackage().manifest();
        Map<?, ?> operator = (Map<?, ?>) manifest.get("operator");
        List<?> ruleVersions = (List<?>) manifest.get("ruleVersions");
        Map<?, ?> reviewDataByItem = (Map<?, ?>) manifest.get("reviewData");
        List<?> coverageSummary = (List<?>) manifest.get("coverageSummary");

        assertEquals("2", manifest.get("manifestVersion"));
        assertEquals("review-ai-summary-v1", manifest.get("aiSummaryVersion"));
        assertEquals(9401L, operator.get("exportedBy"));
        assertFalse(ruleVersions.isEmpty());
        assertEquals(SupervisionRuleSeeds.RULE_RESTRICTED_AREA, ((Map<?, ?>) ruleVersions.get(0)).get("ruleCode"));
        assertTrue(reviewDataByItem.containsKey(String.valueOf(item.id())));
        assertFalse(coverageSummary.isEmpty());
        assertTrue(((Map<?, ?>) reviewDataByItem.get(String.valueOf(item.id()))).containsKey("correlationId"));
        assertTrue(verification.valid());
        assertEquals(job.jobNo(), verification.jobNo());
        assertTrue(verification.replayableReasons().contains("manifest_hash_valid"));
        assertTrue(verification.replayableReasons().contains("decision_trail_reconstructed"));
        assertTrue(verification.decisionTrail().stream()
                .anyMatch(decision -> "false_positive".equals(decision.get("reviewStatus"))
                        && "known staff".equals(decision.get("reason"))));
        assertTrue(verification.auditTrail().stream()
                .anyMatch(entry -> "export_downloaded".equals(entry.actionType())
                        && Objects.equals(9403L, entry.operatorUserId())));
    }

    @Test
    void runtimePatrolProfileTraceAndGapReasonsHardenReviewOperations() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 3, 9, 0);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "missing",
                        request.beginTime(),
                        request.endTime(),
                        0,
                        null,
                        0,
                        Map.of(
                                "gapReason", "service_unavailable",
                                "reasonCode", "video_service_unavailable",
                                "retryable", true
                        )
                ))
        );
        ReviewItemAggregate missingRecord = service.ingestClue(newClue(
                "alert-runtime-patrol",
                alertTime,
                "patrol.jpg",
                null
        ));
        service.queueSemanticReindex(new ReviewSemanticReindexCommand(new ReviewQuery(null, null, null, null), 9500L));

        ReviewRuntimePatrolResult patrol = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9501L,
                true,
                2,
                true
        ));
        List<RecordCoverageSegment> coverage = service.getRecordCoverage(missingRecord.id());
        ReviewRuleGeometryEvaluation geometry = service.evaluateRuleGeometry(new ReviewRuleGeometryCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "camera-01",
                "zone-a",
                List.of(List.of(0D, 0D), List.of(20D, 0D), List.of(20D, 20D), List.of(0D, 20D)),
                List.of(2D, 4D, 8D, 16D),
                "person",
                new ReviewQuery(null, null, null, null),
                9503L
        ));
        Map<?, ?> firstTrace = (Map<?, ?>) geometry.matchTraces().get(0);

        assertEquals("alerted", patrol.status());
        assertTrue(patrol.lockAcquired());
        assertEquals(2, patrol.maxAttempts());
        assertTrue(patrol.reconciliationResult().scannedCount() >= 1);
        assertTrue(patrol.alerts().contains("record_evidence_gap"));
        assertTrue(patrol.notifications().contains("review_runtime_alert:record_evidence_gap"));
        assertTrue(patrol.recommendedActions().contains("backfill_record_evidence"));
        assertEquals("service_unavailable", coverage.get(0).metadata().get("gapReason"));
        assertEquals(List.of(5D, 16D), firstTrace.get("bottomCenter"));
        assertEquals("zone-a", firstTrace.get("zoneCode"));
        assertEquals(15, firstTrace.get("minStaySeconds"));
        assertEquals("bottom_center", firstTrace.get("geometryType"));
        assertEquals("shadow", firstTrace.get("ruleVersion"));
    }

    @Test
    void runtimeHealthReportsVideoUrlNotConfiguredReasonWhenResolverIsUnconfigured() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 3, 11, 0);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService(),
                new ConfigMissingRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of()
        );

        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-video-url-missing",
                alertTime,
                "video-url-missing.jpg",
                null
        ));
        ReviewRuntimeHealthReport health = service.getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                new ReviewQuery(null, null, null, null),
                9601L
        ));
        ReviewRuntimePatrolResult patrol = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9602L,
                false,
                1,
                true
        ));

        assertEquals("missing", item.recordEvidenceStatus());
        assertEquals("video_url_not_configured", item.recordEvidenceMessage());
        assertEquals(1, health.recordGapReasons().get("video_url_not_configured"));
        assertTrue(health.alerts().contains("record_evidence_gap:video_url_not_configured"));
        assertTrue(patrol.recommendedActions().contains("configure_video_record_query_url"));
        assertEquals(Map.of("video_url_not_configured", 1), patrol.metadata().get("recordGapReasons"));
        Map<?, ?> reasonDetails = (Map<?, ?>) patrol.metadata().get("recordGapReasonDetails");
        Map<?, ?> configReason = (Map<?, ?>) reasonDetails.get("video_url_not_configured");
        assertEquals("video_url_not_configured", configReason.get("code"));
        assertEquals("configuration", configReason.get("category"));
        assertEquals("未配置", configReason.get("labelZh"));
        assertEquals(false, configReason.get("retryable"));
    }

    @Test
    void reviewSegmentsRuntimePatrolAndRuleSemanticsArePersistentAndSingleSourced() {
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 4, 9, 0);
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService(),
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(new RecordCoverageSegment(
                        "missing",
                        request.beginTime(),
                        request.endTime(),
                        0,
                        null,
                        0,
                        Map.of("gapReason", "stream_interrupted", "retryable", true)
                ))
        );

        ReviewItemAggregate first = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-segment-start",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                firstTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "segment-start.jpg",
                null,
                "hash-segment-start",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-1"),
                0.91D,
                List.of(2D, 4D, 8D, 16D),
                "corr-segment"
        ));
        ReviewItemAggregate updated = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-segment-update",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                firstTime.plusSeconds(45),
                "device-01",
                "camera-01",
                "zone-b",
                "vehicle",
                20,
                "segment-update.jpg",
                null,
                "hash-segment-update",
                List.of("vehicle"),
                List.of("zone-b"),
                List.of("obj-2"),
                0.88D,
                List.of(10D, 6D, 16D, 18D),
                "corr-segment"
        ));
        ReviewItemAggregate ended = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                updated.id(),
                "ended",
                firstTime.plusSeconds(90),
                List.of("obj-1", "obj-2"),
                List.of("person", "vehicle"),
                List.of("zone-a", "zone-b"),
                List.of(10D, 6D, 16D, 18D),
                Map.of("motionCount", 4),
                null
        ));

        ReviewSegmentView segment = service.getReviewSegment(ended.id());
        ReviewRuntimePatrolResult patrol = service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9601L,
                true,
                3,
                true
        ));
        ReviewRuleGeometryEvaluation geometry = service.evaluateRuleGeometry(new ReviewRuleGeometryCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "camera-01",
                "zone-a",
                List.of(List.of(0D, 0D), List.of(20D, 0D), List.of(20D, 20D), List.of(0D, 20D)),
                List.of(2D, 4D, 8D, 16D),
                "person",
                new ReviewQuery(null, null, null, null),
                9602L
        ));
        Map<?, ?> firstTrace = (Map<?, ?>) geometry.matchTraces().get(0);

        assertEquals(first.id(), updated.id());
        assertEquals(ended.id(), segment.reviewItemId());
        assertEquals("camera-01", segment.cameraId());
        assertEquals("ended", segment.status());
        assertEquals("alert", segment.severity());
        assertEquals(firstTime, segment.startTime());
        assertEquals(firstTime.plusSeconds(90), segment.endTime());
        assertEquals(List.of("obj-1", "obj-2"), segment.objectIds());
        assertEquals(List.of("zone-a", "zone-b"), segment.zones());
        assertEquals(List.of("start", "update", "ended"), segment.events().stream()
                .map(event -> String.valueOf(event.get("event")))
                .toList());
        assertEquals(segment.segmentId(), ((Map<?, ?>) ended.reviewData().get("reviewSegment")).get("segmentId"));
        assertEquals("review_item_store", patrol.metadata().get("lockBackend"));
        assertTrue(String.valueOf(patrol.metadata().get("historyRunId")).startsWith("RPR-"));
        assertTrue(((Number) patrol.metadata().get("outboxEventCount")).intValue() >= 1);
        assertTrue(patrol.metadata().containsKey("nextRetryAt"));
        assertEquals("yfeieye-rule-geometry-v1", geometry.ruleVersion().get("semanticEngine"));
        assertEquals("yfeieye-rule-geometry-v1", firstTrace.get("semanticEngine"));
        assertEquals("bottom_center", firstTrace.get("pointStrategy"));
    }

    @Test
    void endedAlertReviewSegmentSplitsLateDetectionIntoNewSegment() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 9, 30);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate alert = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-split-start",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                8,
                "alert-split-start.jpg",
                null,
                "hash-alert-split-start",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-alert"),
                0.92D,
                List.of(1D, 2D, 8D, 18D),
                "corr-split"
        ));
        ReviewItemAggregate endedAlert = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                alert.id(),
                "ended",
                alertTime.plusSeconds(40),
                List.of("obj-alert"),
                List.of("person"),
                List.of("zone-a"),
                List.of(1D, 2D, 8D, 18D),
                Map.of("motionCount", 1),
                null
        ));

        ReviewItemAggregate detection = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-split-detection",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "motion_detection",
                alertTime.plusSeconds(80),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                3,
                "alert-split-detection.jpg",
                null,
                "hash-alert-split-detection",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-detection"),
                0.72D,
                List.of(2D, 3D, 9D, 19D),
                "corr-split"
        ));

        ReviewSegmentView alertSegment = service.getReviewSegment(endedAlert.id());
        ReviewSegmentView detectionSegment = service.getReviewSegment(detection.id());

        assertNotEquals(alert.id(), detection.id());
        assertEquals("ended", alertSegment.status());
        assertEquals(alertTime.plusSeconds(40), alertSegment.endTime());
        assertEquals("detection", detectionSegment.severity());
        assertEquals("active", detectionSegment.status());
        assertEquals(alertTime.plusSeconds(80), detectionSegment.startTime());
        assertEquals(List.of("start"), detectionSegment.events().stream()
                .map(event -> String.valueOf(event.get("event")))
                .toList());
    }

    @Test
    void endingMergedSegmentTruncatesBeforeLateDetectionSplitsNewActiveSegment() {
        LocalDateTime baseTime = LocalDateTime.of(2026, 7, 4, 9, 40);
        LocalDateTime cutoffTime = baseTime.plusMinutes(2);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate started = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-truncate-start",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                8,
                "alert-truncate-start.jpg",
                null,
                "hash-alert-truncate-start",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-truncate-alert"),
                0.92D,
                List.of(1D, 2D, 8D, 18D),
                "corr-truncate"
        ));
        ReviewItemAggregate merged = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-truncate-update",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                baseTime.plusMinutes(4),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                10,
                "alert-truncate-update.jpg",
                null,
                "hash-alert-truncate-update",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-truncate-update"),
                0.9D,
                List.of(2D, 3D, 9D, 19D),
                "corr-truncate"
        ));
        ReviewItemAggregate ended = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                merged.id(),
                "ended",
                cutoffTime,
                List.of("obj-truncate-alert", "obj-truncate-update"),
                List.of("person"),
                List.of("zone-a"),
                List.of(2D, 3D, 9D, 19D),
                Map.of("motionCount", 2),
                null
        ));

        ReviewItemAggregate detection = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-truncate-detection",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "motion_detection",
                baseTime.plusMinutes(3),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                3,
                "alert-truncate-detection.jpg",
                null,
                "hash-alert-truncate-detection",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-truncate-detection"),
                0.72D,
                List.of(3D, 4D, 10D, 20D),
                "corr-truncate"
        ));

        service.runRuntimePatrol(new ReviewRuntimePatrolCommand(
                new ReviewQuery(null, null, null, null),
                9801L,
                true,
                3,
                true
        ));

        ReviewSegmentView endedSegment = service.getReviewSegment(ended.id());
        ReviewSegmentView detectionSegment = service.getReviewSegment(detection.id());

        assertEquals(started.id(), merged.id());
        assertNotEquals(ended.id(), detection.id());
        assertEquals("ended", endedSegment.status());
        assertEquals(cutoffTime, endedSegment.endTime());
        assertEquals("active", detectionSegment.status());
        assertEquals("detection", detectionSegment.severity());
        assertEquals(baseTime.plusMinutes(3), detectionSegment.startTime());
        assertFalse(detectionSegment.startTime().isBefore(endedSegment.endTime()));
    }

    @Test
    void reviewSegmentLifecycleRejectsEndBeforeSegmentStart() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 9, 43);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-segment-invalid-end",
                alertTime,
                "segment-invalid-end.jpg",
                null
        ));

        IllegalArgumentException invalidEnd = assertThrows(IllegalArgumentException.class,
                () -> service.updateReviewLifecycle(new ReviewLifecycleCommand(
                        item.id(),
                        "ended",
                        alertTime.minusSeconds(1),
                        List.of("obj-invalid-end"),
                        List.of("person"),
                        List.of("zone-a"),
                        List.of(),
                        Map.of(),
                        null
                )));
        ReviewSegmentView segment = service.getReviewSegment(item.id());

        assertTrue(invalidEnd.getMessage().contains("before review segment start"));
        assertEquals("alert", segment.status());
        assertEquals(alertTime, segment.startTime());
        assertEquals(alertTime, segment.endTime());
    }

    @Test
    void reviewSegmentLifecycleRejectsInvalidStateAndReopenAfterEnded() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 9, 45);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-segment-transition",
                alertTime,
                "segment-transition.jpg",
                null
        ));

        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> service.updateReviewLifecycle(new ReviewLifecycleCommand(
                        item.id(),
                        "paused",
                        alertTime.plusSeconds(10),
                        List.of("obj-transition"),
                        List.of("person"),
                        List.of("zone-a"),
                        List.of(),
                        Map.of(),
                        null
                )));
        ReviewItemAggregate ended = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "ended",
                alertTime.plusSeconds(30),
                List.of("obj-transition"),
                List.of("person"),
                List.of("zone-a"),
                List.of(),
                Map.of(),
                null
        ));
        IllegalStateException reopen = assertThrows(IllegalStateException.class,
                () -> service.updateReviewLifecycle(new ReviewLifecycleCommand(
                        ended.id(),
                        "active",
                        alertTime.plusSeconds(40),
                        List.of("obj-transition"),
                        List.of("person"),
                        List.of("zone-a"),
                        List.of(),
                        Map.of(),
                        null
                )));

        assertTrue(invalid.getMessage().contains("review segment state"));
        assertTrue(reopen.getMessage().contains("ended review segment"));
    }

    @Test
    void reviewSegmentLifecycleRejectsReopenWhenJsonSegmentIsMissingButPersistedSegmentEnded() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 13, 10, 45);
        InMemoryReviewItemStore itemStore = new InMemoryReviewItemStore();
        SupervisionAlertReviewService service = newService(
                itemStore,
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(newClue(
                "alert-segment-persisted-ended",
                alertTime,
                "segment-persisted-ended.jpg",
                null
        ));
        ReviewItemAggregate ended = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "ended",
                alertTime.plusSeconds(30),
                List.of("obj-persisted-ended"),
                List.of("person"),
                List.of("zone-a"),
                List.of(),
                Map.of(),
                null
        ));
        Map<String, Object> damagedReviewData = new LinkedHashMap<>(ended.reviewData());
        damagedReviewData.remove("reviewSegment");
        itemStore.updateReviewLifecycle(
                ended.id(),
                Map.copyOf(damagedReviewData),
                ended.firstAlertTime(),
                ended.lastAlertTime(),
                List.of(),
                ended.recordEvidenceStatus(),
                ended.recordEvidenceCheckedAt(),
                ended.recordEvidenceMessage()
        );

        IllegalStateException reopen = assertThrows(IllegalStateException.class,
                () -> service.updateReviewLifecycle(new ReviewLifecycleCommand(
                        ended.id(),
                        "active",
                        alertTime.plusSeconds(40),
                        List.of("obj-persisted-ended"),
                        List.of("person"),
                        List.of("zone-a"),
                        List.of(),
                        Map.of(),
                        null
                )));

        assertTrue(reopen.getMessage().contains("ended review segment"));
        assertEquals("ended", itemStore.findPersistedReviewSegment(ended.id()).orElseThrow().status());
    }

    @Test
    void reviewSegmentLifecycleRejectsEndedExtensionOverlappingLaterActiveSegment() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 9, 46);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate first = service.ingestClue(newClue(
                "alert-segment-overlap-old",
                alertTime,
                "segment-overlap-old.jpg",
                null
        ));
        ReviewItemAggregate ended = service.updateReviewLifecycle(new ReviewLifecycleCommand(
                first.id(),
                "ended",
                alertTime.plusSeconds(30),
                List.of("obj-overlap-old"),
                List.of("person"),
                List.of("zone-a"),
                List.of(),
                Map.of(),
                null
        ));
        ReviewItemAggregate laterActive = service.ingestClue(newClue(
                "alert-segment-overlap-new",
                alertTime.plusSeconds(90),
                "segment-overlap-new.jpg",
                null
        ));

        IllegalStateException overlap = assertThrows(IllegalStateException.class,
                () -> service.updateReviewLifecycle(new ReviewLifecycleCommand(
                        ended.id(),
                        "ended",
                        alertTime.plusSeconds(120),
                        List.of("obj-overlap-old"),
                        List.of("person"),
                        List.of("zone-a"),
                        List.of(),
                        Map.of(),
                        null
                )));
        ReviewSegmentView oldSegment = service.getReviewSegment(ended.id());
        ReviewSegmentView newSegment = service.getReviewSegment(laterActive.id());

        assertTrue(overlap.getMessage().contains("overlapping review segment"));
        assertEquals(alertTime.plusSeconds(30), oldSegment.endTime());
        assertEquals("alert", newSegment.status());
        assertEquals(alertTime.plusSeconds(90), newSegment.startTime());
    }

    @Test
    void reviewSegmentAlertStateDoesNotDowngradeWhenLaterDetectionHeartbeatArrives() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 9, 50);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-segment-escalation",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "motion_detection",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                3,
                "segment-escalation.jpg",
                null,
                "hash-segment-escalation",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-escalation"),
                0.72D,
                List.of(1D, 2D, 8D, 18D),
                "corr-escalation"
        ));
        service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "alert",
                alertTime.plusSeconds(20),
                List.of("obj-escalation"),
                List.of("person"),
                List.of("zone-a"),
                List.of(2D, 3D, 9D, 19D),
                Map.of("event", "restricted_area_alert"),
                null
        ));
        service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "detection",
                alertTime.plusSeconds(30),
                List.of("obj-escalation"),
                List.of("person"),
                List.of("zone-a"),
                List.of(3D, 4D, 10D, 20D),
                Map.of("event", "detection_heartbeat"),
                null
        ));

        ReviewSegmentView segment = service.getReviewSegment(item.id());

        assertEquals("alert", segment.status());
        assertEquals("alert", segment.severity());
        assertEquals(List.of("start", "alert", "detection"), segment.events().stream()
                .map(event -> String.valueOf(event.get("event")))
                .toList());
        assertEquals(alertTime.plusSeconds(30), segment.endTime());
    }

    @Test
    void reviewSegmentAlertStateDoesNotDowngradeWhenLaterDetectionClueIsMerged() {
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 9, 55);
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                new InMemoryRuleStore(),
                unusedEventService()
        );
        ReviewItemAggregate item = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-segment-ingest-escalation",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "motion_detection",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                3,
                "segment-ingest-escalation.jpg",
                null,
                "hash-segment-ingest-escalation",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-ingest-escalation"),
                0.72D,
                List.of(1D, 2D, 8D, 18D),
                "corr-ingest-escalation"
        ));
        service.updateReviewLifecycle(new ReviewLifecycleCommand(
                item.id(),
                "alert",
                alertTime.plusSeconds(20),
                List.of("obj-ingest-escalation"),
                List.of("person"),
                List.of("zone-a"),
                List.of(2D, 3D, 9D, 19D),
                Map.of("event", "restricted_area_alert"),
                null
        ));

        ReviewItemAggregate mergedDetection = service.ingestClue(new AlertClueCommand(
                "video",
                "alert-segment-ingest-detection",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "motion_detection",
                alertTime.plusSeconds(30),
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                4,
                "segment-ingest-detection.jpg",
                null,
                "hash-segment-ingest-detection",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-ingest-detection"),
                0.74D,
                List.of(3D, 4D, 10D, 20D),
                "corr-ingest-escalation"
        ));

        ReviewSegmentView segment = service.getReviewSegment(item.id());

        assertEquals(item.id(), mergedDetection.id());
        assertEquals("alert", segment.status());
        assertEquals("alert", segment.severity());
        assertEquals(List.of("start", "alert", "update"), segment.events().stream()
                .map(event -> String.valueOf(event.get("event")))
                .toList());
        assertEquals(alertTime.plusSeconds(30), segment.endTime());
    }

    @Test
    void ruleGeometryAndReplayUseZoneInertiaAndLoiteringSemanticsFromSavedRule() {
        InMemoryRuleStore ruleStore = new InMemoryRuleStore();
        SupervisionAlertReviewService service = newService(
                new InMemoryReviewItemStore(),
                ruleStore,
                unusedEventService()
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 10, 0);
        service.saveRule(new ReviewRuleCommand(
                null,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "Restricted area with dwell guard",
                "video",
                "camera-01",
                "zone-a",
                "person",
                30,
                null,
                null,
                true,
                3,
                30
        ));
        service.ingestClue(new AlertClueCommand(
                "video",
                "alert-zone-inertia",
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                12,
                "zone-inertia.jpg",
                null,
                "hash-zone-inertia",
                List.of("person"),
                List.of("zone-a"),
                List.of("obj-zone-1"),
                0.91D,
                List.of(2D, 4D, 8D, 16D),
                "corr-zone-inertia",
                List.of("obj-zone-1"),
                alertTime,
                List.of(),
                Map.of("consecutiveZoneFrames", 2)
        ));

        ReviewRuleReplayResult replay = service.replayRule(new ReviewRuleReplayCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "video",
                "camera-01",
                "zone-a",
                "person",
                30,
                alertTime.minusMinutes(5),
                alertTime.plusMinutes(5),
                9701L
        ));
        ReviewRuleGeometryEvaluation geometry = service.evaluateRuleGeometry(new ReviewRuleGeometryCommand(
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "camera-01",
                "zone-a",
                List.of(List.of(0D, 0D), List.of(20D, 0D), List.of(20D, 20D), List.of(0D, 20D)),
                List.of(2D, 4D, 8D, 16D),
                "person",
                new ReviewQuery(null, null, null, null),
                9702L
        ));

        Map<?, ?> replayGeometrySemantics = (Map<?, ?>) replay.report().get("geometrySemantics");
        Map<?, ?> replayRuleVersion = (Map<?, ?>) replay.report().get("ruleVersion");
        Map<?, ?> trace = geometry.matchTraces().get(0);

        assertEquals(3, replayGeometrySemantics.get("zoneInertiaFrames"));
        assertEquals(30, replayGeometrySemantics.get("loiteringSeconds"));
        assertEquals(Boolean.TRUE, replayGeometrySemantics.get("singleSourceRuleVersion"));
        assertEquals(3, replayRuleVersion.get("inertiaFrames"));
        assertEquals(30, replayRuleVersion.get("loiteringSeconds"));
        assertTrue(geometry.consistencyChecks().contains("zone_inertia_applied"));
        assertTrue(geometry.consistencyChecks().contains("loitering_threshold_applied"));
        assertEquals(3, geometry.ruleVersion().get("zoneInertiaFrames"));
        assertEquals(30, geometry.ruleVersion().get("loiteringSeconds"));
        assertEquals(2, trace.get("observedConsecutiveFrames"));
        assertEquals(12, trace.get("observedStaySeconds"));
        assertEquals(Boolean.FALSE, trace.get("inertiaSatisfied"));
        assertEquals(Boolean.FALSE, trace.get("loiteringSatisfied"));
        assertEquals(Boolean.FALSE, trace.get("qualifiedInside"));
    }

    private static Object semanticTriggerTraceValue(ReviewSemanticTriggerResult result, String accessor) {
        return recordTraceValue(result, accessor);
    }

    private static Object recordTraceValue(Object record, String accessor) {
        try {
            return record.getClass().getMethod(accessor).invoke(record);
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void processSemanticWorkerAfterStart(SupervisionAlertReviewService service,
                                                        CountDownLatch ready,
                                                        CountDownLatch start,
                                                        AtomicReference<ReviewSemanticWorkerRun> result,
                                                        AtomicReference<Throwable> failure) {
        ready.countDown();
        try {
            start.await(2, TimeUnit.SECONDS);
            result.set(service.processSemanticIndexQueue(new ReviewSemanticWorkerCommand(
                    new ReviewQuery(null, "camera-01", null, null),
                    10,
                    9201L
            )));
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService) {
        return newService(itemStore, ruleStore, eventService, noRecordEvidenceResolver(), noEventProjectionStore());
    }

    private static SupervisionAlertReviewService newServiceWithCameraTopology(InMemoryReviewItemStore itemStore,
                                                                              InMemoryRuleStore ruleStore,
                                                                              SupervisionEventService eventService,
                                                                              ReviewCameraTopologyResolver cameraTopologyResolver) {
        return new SupervisionAlertReviewServiceImpl(
                itemStore,
                ruleStore,
                eventService,
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                request -> List.of(),
                ReviewIntelligenceProvider.unavailable(),
                VideoEvidenceExportProvider.unavailable(),
                ReviewCameraPermissionResolver.unrestricted(),
                new ReviewAiSummaryRedactionPolicy(),
                cameraTopologyResolver,
                testManifestSigner()
        );
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            ReviewCameraPermissionResolver cameraPermissionResolver) {
        return newService(itemStore, ruleStore, eventService, noRecordEvidenceResolver(), noEventProjectionStore(),
                request -> List.of(), ReviewIntelligenceProvider.unavailable(), VideoEvidenceExportProvider.unavailable(),
                cameraPermissionResolver);
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordEvidenceResolver recordEvidenceResolver,
                                                            EventProjectionStore eventProjectionStore) {
        return newService(itemStore, ruleStore, eventService, recordEvidenceResolver, eventProjectionStore,
                request -> List.of());
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordEvidenceResolver recordEvidenceResolver,
                                                            EventProjectionStore eventProjectionStore,
                                                            RecordCoverageResolver recordCoverageResolver) {
        return newService(itemStore, ruleStore, eventService, recordEvidenceResolver, eventProjectionStore,
                recordCoverageResolver, ReviewIntelligenceProvider.unavailable());
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordCoverageResolver recordCoverageResolver,
                                                            ReviewIntelligenceProvider reviewIntelligenceProvider) {
        return newService(itemStore, ruleStore, eventService, noRecordEvidenceResolver(), noEventProjectionStore(),
                recordCoverageResolver, reviewIntelligenceProvider);
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordEvidenceResolver recordEvidenceResolver,
                                                            EventProjectionStore eventProjectionStore,
                                                            RecordCoverageResolver recordCoverageResolver,
                                                            ReviewIntelligenceProvider reviewIntelligenceProvider) {
        return newService(itemStore, ruleStore, eventService, recordEvidenceResolver, eventProjectionStore,
                recordCoverageResolver, reviewIntelligenceProvider, VideoEvidenceExportProvider.unavailable());
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordEvidenceResolver recordEvidenceResolver,
                                                            EventProjectionStore eventProjectionStore,
                                                            RecordCoverageResolver recordCoverageResolver,
                                                            ReviewIntelligenceProvider reviewIntelligenceProvider,
                                                            VideoEvidenceExportProvider videoEvidenceExportProvider) {
        return newService(itemStore, ruleStore, eventService, recordEvidenceResolver, eventProjectionStore,
                recordCoverageResolver, reviewIntelligenceProvider, videoEvidenceExportProvider,
                ReviewCameraPermissionResolver.unrestricted());
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordEvidenceResolver recordEvidenceResolver,
                                                            EventProjectionStore eventProjectionStore,
                                                            RecordCoverageResolver recordCoverageResolver,
                                                            ReviewIntelligenceProvider reviewIntelligenceProvider,
                                                            VideoEvidenceExportProvider videoEvidenceExportProvider,
                                                            ReviewCameraPermissionResolver cameraPermissionResolver) {
        return new SupervisionAlertReviewServiceImpl(
                itemStore,
                ruleStore,
                eventService,
                recordEvidenceResolver,
                eventProjectionStore,
                recordCoverageResolver,
                reviewIntelligenceProvider,
                videoEvidenceExportProvider,
                cameraPermissionResolver,
                new ReviewAiSummaryRedactionPolicy(),
                ReviewCameraTopologyResolver.empty(),
                testManifestSigner()
        );
    }

    private static SupervisionAlertReviewService newService(InMemoryReviewItemStore itemStore,
                                                            InMemoryRuleStore ruleStore,
                                                            SupervisionEventService eventService,
                                                            RecordCoverageResolver recordCoverageResolver,
                                                            ReviewIntelligenceProvider reviewIntelligenceProvider,
                                                            ReviewAiSummaryRedactionPolicy aiSummaryRedactionPolicy) {
        return new SupervisionAlertReviewServiceImpl(
                itemStore,
                ruleStore,
                eventService,
                noRecordEvidenceResolver(),
                noEventProjectionStore(),
                recordCoverageResolver,
                reviewIntelligenceProvider,
                VideoEvidenceExportProvider.unavailable(),
                ReviewCameraPermissionResolver.unrestricted(),
                aiSummaryRedactionPolicy,
                ReviewCameraTopologyResolver.empty(),
                testManifestSigner()
        );
    }

    private static ReviewEvidenceManifestSigner testManifestSigner() {
        String secret = java.util.Base64.getEncoder().encodeToString(
                "yfeieye-review-manifest-test-key-0001".getBytes(StandardCharsets.UTF_8));
        return new ReviewEvidenceManifestSigner("test-key", "test-key=" + secret, "hmac-sha256-v1");
    }
    private static AlertClueCommand newClue(String sourceAlertId,
                                            LocalDateTime alertTime,
                                            String snapshotUri,
                                            String recordUri) {
        return new AlertClueCommand(
                "video",
                sourceAlertId,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                15,
                snapshotUri,
                recordUri,
                null
        );
    }

    private static void ingestAfterStart(SupervisionAlertReviewService service,
                                         AlertClueCommand command,
                                         CountDownLatch ready,
                                         CountDownLatch start,
                                         AtomicReference<ReviewItemAggregate> result,
                                         AtomicReference<Throwable> failure) {
        ready.countDown();
        try {
            if (!start.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("ingest start latch timed out");
            }
            result.set(service.ingestClue(command));
        } catch (Throwable throwable) {
            failure.compareAndSet(null, throwable);
        }
    }

    private static SupervisionEventService unusedEventService() {
        return command -> {
            throw new AssertionError("event service should not be called");
        };
    }

    private static SupervisionEventService eventServiceReturning(Long eventId) {
        return command -> new AlertToEventResult(
                eventId,
                command.sourceSystem(),
                command.sourceAlertId(),
                command.ruleCode(),
                "supervision_order",
                SupervisionEventLevelEnum.L2,
                SupervisionEventStatusEnum.ACCEPTED.getCode(),
                false
        );
    }

    private static RecordEvidenceResolver noRecordEvidenceResolver() {
        return request -> Optional.empty();
    }

    private static final class ConfigMissingRecordEvidenceResolver implements RecordEvidenceResolver {

        @Override
        public Optional<RecordEvidenceResult> resolve(RecordEvidenceRequest request) {
            return Optional.empty();
        }

        @Override
        public Optional<String> unavailableReason() {
            return Optional.of("video_url_not_configured");
        }

    }

    private static Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> reviewSegmentSourceAlertIds(Map<String, Object> reviewData) {
        Map<String, Object> segment = toStringObjectMap(reviewData == null ? null : reviewData.get("reviewSegment"));
        Object value = segment.get("sourceAlertIds");
        if (value instanceof List<?> raw) {
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    private static EventProjectionStore noEventProjectionStore() {
        return eventId -> Optional.empty();
    }

    private static String validSha256(char hexDigit) {
        if (Character.digit(hexDigit, 16) < 0) {
            throw new IllegalArgumentException("hexDigit must be hexadecimal");
        }
        return "sha256:" + String.valueOf(hexDigit).repeat(64);
    }

    private static String sha256Bytes(byte[] bytes) throws Exception {
        return "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static CapturingVideoEvidenceExportProvider singleCameraDownloadableProvider(
            String cameraId,
            byte[] bytes) throws Exception {
        String fileHash = sha256Bytes(bytes);
        return new CapturingVideoEvidenceExportProvider(
                Optional.of(new ReviewEvidenceVideoExportResult(
                        "export-" + cameraId,
                        "https://eye.yfeiai.com/video/record/export/export-" + cameraId + "/download",
                        "ready",
                        "real VIDEO package ready",
                        "https://eye.yfeiai.com/video/record/export/export-" + cameraId + "/manifest",
                        fileHash,
                        List.of(Map.of(
                                "cameraId", cameraId,
                                "originalRecordUri", cameraId + ".mp4",
                                "sourceHash", validSha256('a'),
                                "ffmpegCommandHash", validSha256('b'),
                                "clipParameters", Map.of("offsetSeconds", 0D, "durationSeconds", 60D),
                                "stitchOrder", 0
                        )),
                        validSha256('c')
                )),
                Optional.of(temporaryArtifact(
                        null,
                        "export-" + cameraId + ".mp4",
                        "video/mp4",
                        bytes,
                        fileHash
                ))
        );
    }

    private static ReviewEvidenceDownloadArtifact temporaryArtifact(String jobNo,
                                                                     String fileName,
                                                                     String contentType,
                                                                     byte[] bytes,
                                                                     String fileHash) {
        try {
            Path file = Files.createTempFile("alert-review-test-artifact-", ".tmp");
            Files.write(file, bytes);
            return new ReviewEvidenceDownloadArtifact(
                    jobNo,
                    fileName,
                    contentType,
                    file,
                    bytes.length,
                    fileHash,
                    null
            );
        } catch (Exception exception) {
            throw new IllegalStateException("unable to create test artifact", exception);
        }
    }

    private static Map<String, byte[]> unzipEntries(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static final class StubReviewIntelligenceProvider implements ReviewIntelligenceProvider {

        private final Function<ReviewSemanticSearchRequest, Optional<List<ReviewSemanticHit>>> semanticSearch;
        private final Function<ReviewAiSummaryRequest, Optional<ReviewAiSummary>> summarize;

        private StubReviewIntelligenceProvider(
                Function<ReviewSemanticSearchRequest, Optional<List<ReviewSemanticHit>>> semanticSearch,
                Function<ReviewAiSummaryRequest, Optional<ReviewAiSummary>> summarize) {
            this.semanticSearch = semanticSearch;
            this.summarize = summarize;
        }

        @Override
        public Optional<List<ReviewSemanticHit>> semanticSearch(ReviewSemanticSearchRequest request) {
            return semanticSearch.apply(request);
        }

        @Override
        public Optional<ReviewAiSummary> summarize(ReviewAiSummaryRequest request) {
            return summarize.apply(request);
        }
    }

    private static final class CapturingRecordEvidenceResolver implements RecordEvidenceResolver {

        private final Optional<RecordEvidenceResult> result;
        private final List<RecordEvidenceRequest> requests = new ArrayList<>();

        private CapturingRecordEvidenceResolver(Optional<RecordEvidenceResult> result) {
            this.result = result;
        }

        @Override
        public Optional<RecordEvidenceResult> resolve(RecordEvidenceRequest request) {
            requests.add(request);
            return result;
        }

        List<RecordEvidenceRequest> requests() {
            return requests;
        }

    }

    private static final class CapturingRecordCoverageResolver implements RecordCoverageResolver {

        private final List<RecordCoverageSegment> result;
        private final List<RecordCoverageRequest> requests = new ArrayList<>();

        private CapturingRecordCoverageResolver(List<RecordCoverageSegment> result) {
            this.result = result;
        }

        @Override
        public List<RecordCoverageSegment> resolve(RecordCoverageRequest request) {
            requests.add(request);
            return result;
        }

        List<RecordCoverageRequest> requests() {
            return requests;
        }

    }

    private static final class CapturingVideoEvidenceExportProvider implements VideoEvidenceExportProvider {

        private final Optional<ReviewEvidenceVideoExportResult> result;
        private final Optional<ReviewEvidenceDownloadArtifact> downloadResult;
        private final List<ReviewEvidenceVideoExportRequest> requests = new ArrayList<>();
        private final List<ReviewEvidenceVideoDownloadRequest> downloadRequests = new ArrayList<>();

        private CapturingVideoEvidenceExportProvider(Optional<ReviewEvidenceVideoExportResult> result) {
            this(result, Optional.empty());
        }

        private CapturingVideoEvidenceExportProvider(Optional<ReviewEvidenceVideoExportResult> result,
                                                     Optional<ReviewEvidenceDownloadArtifact> downloadResult) {
            this.result = result;
            this.downloadResult = downloadResult;
        }

        @Override
        public Optional<ReviewEvidenceVideoExportResult> export(ReviewEvidenceVideoExportRequest request) {
            requests.add(request);
            return result;
        }

        @Override
        public Optional<ReviewEvidenceDownloadArtifact> download(ReviewEvidenceVideoDownloadRequest request) {
            downloadRequests.add(request);
            return downloadResult;
        }

        List<ReviewEvidenceVideoExportRequest> requests() {
            return requests;
        }

        List<ReviewEvidenceVideoDownloadRequest> downloadRequests() {
            return downloadRequests;
        }

    }

    private record RuntimeOutboxEntry(String runId,
                                      String alert,
                                      String action,
                                      List<String> recommendedActions,
                                      Long operatorUserId,
                                      String status,
                                      String claimToken,
                                      Long claimedBy,
                                      LocalDateTime claimedAt,
                                      Map<String, Object> metadata,
                                      Long id,
                                      String payload,
                                      Integer retryCount,
                                      LocalDateTime publishedAt,
                                      String lastError) {
    }

    private static final class SlowCreateReviewItemStore extends InMemoryReviewItemStore {

        private final CountDownLatch concurrentCreateWindow = new CountDownLatch(2);

        @Override
        public ReviewItemAggregate create(ReviewItemDraft draft, List<ReviewEvidenceItem> evidenceItems) {
            concurrentCreateWindow.countDown();
            try {
                concurrentCreateWindow.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            synchronized (this) {
                return super.create(draft, evidenceItems);
            }
        }
    }

    private static final class DetectionOnlyReviewItemStore extends InMemoryReviewItemStore {

        @Override
        public List<ReviewEvidenceItem> listTimeline(Long reviewItemId) {
            return super.listTimeline(reviewItemId).stream()
                    .filter(evidence -> !"record".equals(evidence.materialType()))
                    .toList();
        }
    }

    private static final class SegmentConflictWinnerStore extends InMemoryReviewItemStore {

        private int createAttempts;

        @Override
        public ReviewItemAggregate create(ReviewItemDraft draft, List<ReviewEvidenceItem> evidenceItems) {
            createAttempts++;
            super.create(draft, evidenceItems);
            throw new DataIntegrityViolationException(
                    "concurrent review segment overlap",
                    new SQLException("exclusion constraint conflict", "23P01")
            );
        }

        int createAttempts() {
            return createAttempts;
        }
    }

    private static final class CountingExportClaimStore extends InMemoryReviewItemStore {

        private final List<Integer> claimLimits = new ArrayList<>();

        @Override
        public List<ReviewEvidenceExportJob> claimProcessableExportJobs(Integer limit,
                                                                        String claimToken,
                                                                        Long claimedBy,
                                                                        LocalDateTime claimedAt,
                                                                        LocalDateTime reclaimBefore) {
            claimLimits.add(limit);
            return super.claimProcessableExportJobs(limit, claimToken, claimedBy, claimedAt, reclaimBefore);
        }

        List<Integer> claimLimits() {
            return List.copyOf(claimLimits);
        }
    }

    private static final class ConflictingExportCompletionStore extends InMemoryReviewItemStore {

        private final Map<String, Integer> completionAttempts = new LinkedHashMap<>();
        private String conflictingJobNo;

        void conflictOn(String jobNo) {
            conflictingJobNo = jobNo;
        }

        int completionAttempts(String jobNo) {
            return completionAttempts.getOrDefault(jobNo, 0);
        }

        @Override
        public ReviewEvidenceExportJob completeExportJobClaim(ReviewEvidenceExportJob job, String claimToken) {
            completionAttempts.merge(job.jobNo(), 1, Integer::sum);
            if (Objects.equals(conflictingJobNo, job.jobNo())) {
                throw new IllegalStateException("export_job_claim_conflict: " + job.jobNo());
            }
            return super.completeExportJobClaim(job, claimToken);
        }
    }

    private static final class RecoverableRuntimeLockStore extends InMemoryReviewItemStore {

        private final Map<String, RuntimeLockState> runtimeLocks = new LinkedHashMap<>();

        void seedRuntimeLock(String lockName, Long ownerUserId, LocalDateTime lockedUntil) {
            runtimeLocks.put(lockName, new RuntimeLockState(ownerUserId, lockedUntil, LocalDateTime.now()));
        }

        Long currentLockOwner(String lockName) {
            RuntimeLockState state = runtimeLocks.get(lockName);
            return state == null ? null : state.ownerUserId();
        }

        @Override
        public ReviewRuntimeLockAcquisition acquireRuntimePatrolLock(String lockName,
                                                                     LocalDateTime expiresAt,
                                                                     Long operatorUserId) {
            LocalDateTime now = LocalDateTime.now();
            RuntimeLockState state = runtimeLocks.get(lockName);
            if (state != null && state.lockedUntil() != null && state.lockedUntil().isAfter(now)) {
                return new ReviewRuntimeLockAcquisition(
                        lockName,
                        false,
                        false,
                        state.ownerUserId(),
                        state.lockedUntil(),
                        state.lockedUntil(),
                        now,
                        "active_lock"
                );
            }
            boolean recovered = state != null;
            runtimeLocks.put(lockName, new RuntimeLockState(operatorUserId, expiresAt, now));
            return new ReviewRuntimeLockAcquisition(
                    lockName,
                    true,
                    recovered,
                    state == null ? null : state.ownerUserId(),
                    state == null ? null : state.lockedUntil(),
                    expiresAt,
                    now,
                    recovered ? "stale_lock_recovered" : "created"
            );
        }

        @Override
        public void releaseRuntimePatrolLock(String lockName, Long operatorUserId) {
            RuntimeLockState state = runtimeLocks.get(lockName);
            if (state == null) {
                return;
            }
            if (operatorUserId != null && state.ownerUserId() != null
                    && !Objects.equals(operatorUserId, state.ownerUserId())) {
                return;
            }
            runtimeLocks.put(lockName, new RuntimeLockState(state.ownerUserId(), LocalDateTime.now(), state.lastLockedAt()));
        }
    }

    private record RuntimeLockState(Long ownerUserId,
                                    LocalDateTime lockedUntil,
                                    LocalDateTime lastLockedAt) {
    }
    private static final class BlockingSemanticIndexStore extends InMemoryReviewItemStore {

        private final AtomicInteger indexedUpsertAttempts = new AtomicInteger();
        private final CountDownLatch concurrentIndexedUpserts = new CountDownLatch(2);

        @Override
        public ReviewSemanticIndexEntry upsertSemanticIndex(ReviewItemAggregate item,
                                                           String document,
                                                           String embeddingKey,
                                                           String embeddingModel,
                                                           String embeddingVectorHash,
                                                           String indexStatus,
                                                           Integer retryCount,
                                                           String lastError,
                                                           LocalDateTime indexedAt) {
            if (SupervisionAlertReviewService.SEMANTIC_INDEX_INDEXED.equals(indexStatus)) {
                indexedUpsertAttempts.incrementAndGet();
                concurrentIndexedUpserts.countDown();
                try {
                    concurrentIndexedUpserts.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }
            return super.upsertSemanticIndex(
                    item,
                    document,
                    embeddingKey,
                    embeddingModel,
                    embeddingVectorHash,
                    indexStatus,
                    retryCount,
                    lastError,
                    indexedAt
            );
        }

        int indexedUpsertAttempts() {
            return indexedUpsertAttempts.get();
        }
    }

    private static class FailingSemanticIndexStore extends InMemoryReviewItemStore {

        private final Map<Long, String> indexedFailures = new LinkedHashMap<>();

        void failNextIndexedUpsertFor(Long reviewItemId, String message) {
            indexedFailures.put(reviewItemId, message);
        }

        @Override
        public ReviewSemanticIndexEntry upsertSemanticIndex(ReviewItemAggregate item,
                                                           String document,
                                                           String embeddingKey,
                                                           String embeddingModel,
                                                           String embeddingVectorHash,
                                                           String indexStatus,
                                                           Integer retryCount,
                                                           String lastError,
                                                           LocalDateTime indexedAt) {
            if (item != null
                    && SupervisionAlertReviewService.SEMANTIC_INDEX_INDEXED.equals(indexStatus)
                    && indexedFailures.containsKey(item.id())) {
                String message = indexedFailures.remove(item.id());
                throw new IllegalStateException(message);
            }
            return super.upsertSemanticIndex(
                    item,
                    document,
                    embeddingKey,
                    embeddingModel,
                    embeddingVectorHash,
                    indexStatus,
                    retryCount,
                    lastError,
                    indexedAt
            );
        }
    }

    private static final class ClaimConflictSemanticIndexStore extends InMemoryReviewItemStore {

        private Long conflictReviewItemId;
        private boolean failBeforeConflict;

        void conflictOnCompletion(Long reviewItemId) {
            conflictReviewItemId = reviewItemId;
            failBeforeConflict = false;
        }

        void failThenConflictOnCompletion(Long reviewItemId) {
            conflictReviewItemId = reviewItemId;
            failBeforeConflict = true;
        }

        @Override
        public ReviewSemanticIndexEntry completeSemanticIndexClaim(ReviewItemAggregate item,
                                                                    String document,
                                                                    String embeddingKey,
                                                                    String embeddingModel,
                                                                    String embeddingVectorHash,
                                                                    String indexStatus,
                                                                    Integer retryCount,
                                                                    String lastError,
                                                                    LocalDateTime indexedAt,
                                                                    String indexGenerationId,
                                                                    LocalDateTime nextRetryAt,
                                                                    String claimToken) {
            if (Objects.equals(conflictReviewItemId, item.id())) {
                if (failBeforeConflict
                        && SupervisionAlertReviewService.SEMANTIC_INDEX_INDEXED.equals(indexStatus)) {
                    throw new IllegalStateException("embedding provider timeout");
                }
                throw new IllegalStateException("semantic_index_claim_conflict: " + item.id());
            }
            return super.completeSemanticIndexClaim(
                    item,
                    document,
                    embeddingKey,
                    embeddingModel,
                    embeddingVectorHash,
                    indexStatus,
                    retryCount,
                    lastError,
                    indexedAt,
                    indexGenerationId,
                    nextRetryAt,
                    claimToken
            );
        }
    }

    private static class InMemoryReviewItemStore implements ReviewItemStore {

        private final Map<Long, ReviewItemAggregate> items = new LinkedHashMap<>();
        private final Map<Long, ReviewSegmentView> persistedReviewSegments = new LinkedHashMap<>();
        private final Map<Long, List<ReviewEvidenceItem>> evidenceByItemId = new LinkedHashMap<>();
        private final Map<Long, ReviewCaseView> cases = new LinkedHashMap<>();
        private final Map<Long, List<Long>> caseItemIds = new LinkedHashMap<>();
        private final Map<Long, List<ReviewCaseTimelineItem>> caseAudits = new LinkedHashMap<>();
        private final Map<Long, List<ReviewCaseTimelineItem>> caseLevelEvidence = new LinkedHashMap<>();
        private final Map<Long, List<ReviewCaseTimelineItem>> itemMediaAudits = new LinkedHashMap<>();
        private final Map<String, ReviewEvidenceExportJob> exportJobs = new LinkedHashMap<>();
        private final Map<String, String> exportJobClaims = new LinkedHashMap<>();
        private final Map<String, LocalDateTime> exportJobClaimedAt = new LinkedHashMap<>();
        private final Map<Long, ReviewSemanticIndexEntry> semanticIndex = new LinkedHashMap<>();
        private final Map<String, ReviewUserStatusView> userStatuses = new LinkedHashMap<>();
        private final Map<String, ReviewReportAcknowledgement> reportAcknowledgements = new LinkedHashMap<>();
        private final Map<String, List<SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord>> semanticTriggerAudits = new LinkedHashMap<>();
        private final List<RuntimeOutboxEntry> runtimeOutbox = new ArrayList<>();
        private long nextId = 1000L;
        private long nextCaseId = 3000L;
        private long nextExportJobId = 5000L;
        private long nextRuntimeOutboxId = 7000L;

        void removePersistedReviewSegmentForTest(Long reviewItemId) {
            persistedReviewSegments.remove(reviewItemId);
        }

        void tamperPersistedReviewSegmentCameraForTest(Long reviewItemId, String cameraId) {
            ReviewSegmentView current = persistedReviewSegments.get(reviewItemId);
            persistedReviewSegments.put(reviewItemId, new ReviewSegmentView(
                    current.reviewItemId(),
                    current.segmentId(),
                    cameraId,
                    current.severity(),
                    current.status(),
                    current.startTime(),
                    current.endTime(),
                    current.objectIds(),
                    current.zones(),
                    current.sourceAlertIds(),
                    current.events(),
                    current.metadata()
            ));
        }

        @Override
        public Optional<ReviewSegmentView> findPersistedReviewSegment(Long reviewItemId) {
            return Optional.ofNullable(persistedReviewSegments.get(reviewItemId));
        }

        void addCaseLevelEvidence(Long reviewCaseId,
                                  String cameraId,
                                  String materialType,
                                  String materialUri,
                                  LocalDateTime happenedAt) {
            caseLevelEvidence.computeIfAbsent(reviewCaseId, ignored -> new ArrayList<>())
                    .add(new ReviewCaseTimelineItem(
                            reviewCaseId,
                            null,
                            cameraId,
                            null,
                            materialType,
                            materialUri,
                            happenedAt
                    ));
        }

        @Override
        public Optional<ReviewItemAggregate> findMergeCandidate(String sourceSystem,
                                                                String cameraId,
                                                                String zoneCode,
                                                                String ruleCode,
                                                                LocalDateTime windowStart,
                                                                LocalDateTime windowEnd) {
            return items.values().stream()
                    .filter(item -> "pending_review".equals(item.reviewStatus()))
                    .filter(item -> Objects.equals(sourceSystem, item.sourceSystem()))
                    .filter(item -> Objects.equals(cameraId, item.cameraId()))
                    .filter(item -> !item.lastAlertTime().isBefore(windowStart))
                    .filter(item -> !item.firstAlertTime().isAfter(windowEnd))
                    .findFirst();
        }

        @Override
        public ReviewItemAggregate create(ReviewItemDraft draft, List<ReviewEvidenceItem> evidenceItems) {
            long id = nextId++;
            ReviewItemAggregate item = new ReviewItemAggregate(
                    id,
                    "RI-" + id,
                    draft.sourceSystem(),
                    draft.ruleCode(),
                    draft.sourceAlertType(),
                    draft.deviceId(),
                    draft.cameraId(),
                    draft.zoneCode(),
                    draft.objectLabel(),
                    draft.alertTime(),
                    draft.alertTime(),
                    1,
                    List.of(draft.sourceAlertId()),
                    draft.reviewData(),
                    "pending_review",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    draft.recordEvidenceStatus(),
                    draft.recordEvidenceCheckedAt(),
                    draft.recordEvidenceMessage(),
                    null,
                    null,
                    null
            );
            items.put(id, item);
            persistReviewSegment(item);
            evidenceByItemId.put(id, new ArrayList<>(evidenceItems));
            return item;
        }

        @Override
        public ReviewItemAggregate appendClue(Long reviewItemId,
                                              String sourceAlertId,
                                              LocalDateTime alertTime,
                                              List<ReviewEvidenceItem> evidenceItems,
                                              Map<String, Object> reviewData,
                                              String recordEvidenceStatus,
                                              LocalDateTime recordEvidenceCheckedAt,
                                              String recordEvidenceMessage) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            List<String> sourceAlertIds = new ArrayList<>(item.sourceAlertIds());
            if (!sourceAlertIds.contains(sourceAlertId)) {
                sourceAlertIds.add(sourceAlertId);
            }
            List<String> orderedSourceAlertIds = reviewSegmentSourceAlertIds(reviewData);
            if (!orderedSourceAlertIds.isEmpty()) {
                sourceAlertIds = orderedSourceAlertIds;
            }
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    min(item.firstAlertTime(), alertTime),
                    max(item.lastAlertTime(), alertTime),
                    sourceAlertIds.size(),
                    List.copyOf(sourceAlertIds),
                    reviewData,
                    item.reviewStatus(),
                    item.reviewerUserId(),
                    item.reviewedAt(),
                    item.ignoreReason(),
                    item.ruleSuggestion(),
                    item.eventId(),
                    item.convertedAt(),
                    recordEvidenceStatus,
                    recordEvidenceCheckedAt,
                    recordEvidenceMessage,
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus()
            );
            items.put(reviewItemId, updated);
            persistReviewSegment(updated);
            appendEvidence(reviewItemId, evidenceItems);
            return updated;
        }

        @Override
        public ReviewItemAggregate appendEvidence(Long reviewItemId, List<ReviewEvidenceItem> evidenceItems) {
            List<ReviewEvidenceItem> timeline = evidenceByItemId.computeIfAbsent(reviewItemId, key -> new ArrayList<>());
            for (ReviewEvidenceItem evidenceItem : evidenceItems) {
                boolean exists = timeline.stream().anyMatch(existing -> sameEvidence(existing, evidenceItem));
                if (!exists) {
                    timeline.add(evidenceItem);
                }
            }
            return findById(reviewItemId).orElseThrow();
        }

        @Override
        public ReviewItemAggregate updateRecordEvidenceStatus(Long reviewItemId,
                                                              String recordEvidenceStatus,
                                                              LocalDateTime recordEvidenceCheckedAt,
                                                              String recordEvidenceMessage) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    item.reviewData(),
                    item.reviewStatus(),
                    item.reviewerUserId(),
                    item.reviewedAt(),
                    item.ignoreReason(),
                    item.ruleSuggestion(),
                    item.eventId(),
                    item.convertedAt(),
                    recordEvidenceStatus,
                    recordEvidenceCheckedAt,
                    recordEvidenceMessage,
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus()
            );
            items.put(reviewItemId, updated);
            return updated;
        }

        @Override
        public ReviewItemAggregate updateReviewLifecycle(Long reviewItemId,
                                                         Map<String, Object> reviewData,
                                                         LocalDateTime firstAlertTime,
                                                         LocalDateTime lastAlertTime,
                                                         List<ReviewEvidenceItem> evidenceItems,
                                                         String recordEvidenceStatus,
                                                         LocalDateTime recordEvidenceCheckedAt,
                                                         String recordEvidenceMessage) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    firstAlertTime,
                    lastAlertTime,
                    item.alertCount(),
                    item.sourceAlertIds(),
                    reviewData,
                    item.reviewStatus(),
                    item.reviewerUserId(),
                    item.reviewedAt(),
                    item.ignoreReason(),
                    item.ruleSuggestion(),
                    item.eventId(),
                    item.convertedAt(),
                    recordEvidenceStatus,
                    recordEvidenceCheckedAt,
                    recordEvidenceMessage,
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus(),
                    item.eventReviewStatus(),
                    item.inReviewCase(),
                    item.ruleSuggestionStatus(),
                    item.ruleSuggestionUpdatedAt()
            );
            items.put(reviewItemId, updated);
            persistReviewSegment(updated);
            appendEvidence(reviewItemId, evidenceItems);
            return updated;
        }

        private void persistReviewSegment(ReviewItemAggregate item) {
            Map<String, Object> segment = toStringObjectMap(
                    item.reviewData() == null ? null : item.reviewData().get("reviewSegment"));
            if (segment.isEmpty()) {
                return;
            }
            persistedReviewSegments.put(item.id(), new ReviewSegmentView(
                    item.id(),
                    String.valueOf(segment.get("segmentId")),
                    String.valueOf(segment.get("cameraId")),
                    String.valueOf(segment.get("severity")),
                    String.valueOf(segment.get("status")),
                    LocalDateTime.parse(String.valueOf(segment.get("startTime"))),
                    LocalDateTime.parse(String.valueOf(segment.get("endTime"))),
                    reviewSegmentStringList(segment.get("objectIds")),
                    reviewSegmentStringList(segment.get("zones")),
                    reviewSegmentStringList(segment.get("sourceAlertIds")),
                    reviewSegmentEvents(segment.get("events")),
                    segment
            ));
        }

        private static List<String> reviewSegmentStringList(Object value) {
            if (!(value instanceof List<?> values)) {
                return List.of();
            }
            return values.stream().map(String::valueOf).toList();
        }

        private static List<Map<String, Object>> reviewSegmentEvents(Object value) {
            if (!(value instanceof List<?> values)) {
                return List.of();
            }
            return values.stream()
                    .map(SupervisionAlertReviewServiceTest::toStringObjectMap)
                    .toList();
        }

        @Override
        public Optional<ReviewItemAggregate> findById(Long reviewItemId) {
            return Optional.ofNullable(items.get(reviewItemId)).map(this::withCaseFlag);
        }

        @Override
        public List<ReviewItemAggregate> listWorkbench(ReviewQuery query) {
            return items.values().stream().map(this::withCaseFlag).toList();
        }

        @Override
        public List<ReviewEvidenceItem> listTimeline(Long reviewItemId) {
            return List.copyOf(evidenceByItemId.getOrDefault(reviewItemId, List.of()));
        }

        @Override
        public Optional<ReviewReportAcknowledgement> findReportAcknowledgement(String reportKey) {
            return Optional.ofNullable(reportAcknowledgements.get(reportKey));
        }

        @Override
        public ReviewReportAcknowledgement saveReportAcknowledgement(ReviewReportAcknowledgement acknowledgement) {
            return reportAcknowledgements.computeIfAbsent(acknowledgement.reportKey(), ignored -> acknowledgement);
        }

        @Override
        public ReviewItemAggregate updateReviewStatus(Long reviewItemId,
                                                      String reviewStatus,
                                                      Long reviewerUserId,
                                                      String ignoreReason,
                                                      LocalDateTime reviewedAt) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    item.reviewData(),
                    reviewStatus,
                    reviewerUserId,
                    reviewedAt,
                    ignoreReason,
                    item.ruleSuggestion(),
                    item.eventId(),
                    item.convertedAt(),
                    item.recordEvidenceStatus(),
                    item.recordEvidenceCheckedAt(),
                    item.recordEvidenceMessage(),
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus(),
                    item.eventReviewStatus(),
                    item.inReviewCase(),
                    item.ruleSuggestionStatus(),
                    item.ruleSuggestionUpdatedAt()
            );
            items.put(reviewItemId, updated);
            return updated;
        }

        @Override
        public ReviewUserStatusView upsertUserReviewStatus(Long reviewItemId,
                                                           Long userId,
                                                           boolean hasBeenReviewed,
                                                           LocalDateTime reviewedAt) {
            findById(reviewItemId).orElseThrow();
            ReviewUserStatusView view = new ReviewUserStatusView(reviewItemId, userId, hasBeenReviewed, reviewedAt);
            userStatuses.put(userStatusKey(reviewItemId, userId), view);
            return view;
        }

        @Override
        public Optional<ReviewUserStatusView> findUserReviewStatus(Long reviewItemId, Long userId) {
            return Optional.ofNullable(userStatuses.get(userStatusKey(reviewItemId, userId)));
        }

        @Override
        public long countReviewedByUser(List<Long> reviewItemIds, Long userId) {
            if (reviewItemIds == null || reviewItemIds.isEmpty() || userId == null) {
                return 0L;
            }
            return userStatuses.values().stream()
                    .filter(status -> Objects.equals(userId, status.userId()))
                    .filter(status -> Boolean.TRUE.equals(status.hasBeenReviewed()))
                    .filter(status -> reviewItemIds.contains(status.reviewItemId()))
                    .count();
        }

        @Override
        public ReviewItemAggregate updateRuleSuggestionStatus(Long reviewItemId,
                                                              Long reviewerUserId,
                                                              String status,
                                                              Map<String, Object> ruleSuggestion,
                                                              LocalDateTime updatedAt) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    item.reviewData(),
                    item.reviewStatus(),
                    reviewerUserId,
                    item.reviewedAt(),
                    item.ignoreReason(),
                    ruleSuggestion,
                    item.eventId(),
                    item.convertedAt(),
                    item.recordEvidenceStatus(),
                    item.recordEvidenceCheckedAt(),
                    item.recordEvidenceMessage(),
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus(),
                    item.eventReviewStatus(),
                    item.inReviewCase(),
                    status,
                    updatedAt
            );
            items.put(reviewItemId, updated);
            return updated;
        }

        @Override
        public ReviewItemAggregate updateFalsePositive(Long reviewItemId,
                                                       Long reviewerUserId,
                                                       String reason,
                                                       Map<String, Object> ruleSuggestion,
                                                       LocalDateTime reviewedAt) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    item.reviewData(),
                    "false_positive",
                    reviewerUserId,
                    reviewedAt,
                    reason,
                    ruleSuggestion,
                    item.eventId(),
                    item.convertedAt(),
                    item.recordEvidenceStatus(),
                    item.recordEvidenceCheckedAt(),
                    item.recordEvidenceMessage(),
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus(),
                    item.eventReviewStatus(),
                    item.inReviewCase(),
                    "pending",
                    reviewedAt
            );
            items.put(reviewItemId, updated);
            return updated;
        }

        @Override
        public ReviewItemAggregate markConverted(Long reviewItemId,
                                                 Long reviewerUserId,
                                                 Long eventId,
                                                 LocalDateTime convertedAt) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    item.reviewData(),
                    "converted",
                    reviewerUserId,
                    convertedAt,
                    item.ignoreReason(),
                    item.ruleSuggestion(),
                    eventId,
                    convertedAt,
                    item.recordEvidenceStatus(),
                    item.recordEvidenceCheckedAt(),
                    item.recordEvidenceMessage(),
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus()
            );
            items.put(reviewItemId, updated);
            return updated;
        }

        @Override
        public ReviewItemAggregate updateEventProjection(Long reviewItemId,
                                                         Map<String, Object> reviewData,
                                                         EventProjection projection,
                                                         String eventReviewStatus,
                                                         LocalDateTime reconciledAt) {
            ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
            ReviewItemAggregate updated = new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    reviewData,
                    item.reviewStatus(),
                    item.reviewerUserId(),
                    item.reviewedAt(),
                    item.ignoreReason(),
                    item.ruleSuggestion(),
                    item.eventId(),
                    item.convertedAt(),
                    item.recordEvidenceStatus(),
                    item.recordEvidenceCheckedAt(),
                    item.recordEvidenceMessage(),
                    projection.eventStatus(),
                    projection.closeCheckStatus(),
                    projection.evidenceStatus(),
                    eventReviewStatus,
                    item.inReviewCase(),
                    item.ruleSuggestionStatus(),
                    item.ruleSuggestionUpdatedAt()
            );
            items.put(reviewItemId, updated);
            return updated;
        }

        @Override
        public ReviewCaseView createCase(ReviewCaseDraft draft, List<Long> reviewItemIds) {
            long id = nextCaseId++;
            for (Long reviewItemId : reviewItemIds) {
                findById(reviewItemId).orElseThrow();
            }
            caseItemIds.put(id, new ArrayList<>(reviewItemIds));
            ReviewCaseView view = buildCaseView(
                    id,
                    "RC-" + id,
                    draft.title(),
                    SupervisionAlertReviewService.REVIEW_CASE_OPEN,
                    draft.primaryReviewItemId(),
                    draft.ownerUserId(),
                    draft.notes()
            );
            cases.put(id, view);
            addCaseAudit(id, draft.primaryReviewItemId(), "create_case", draft.notes(), draft.ownerUserId(), LocalDateTime.now());
            return view;
        }

        @Override
        public ReviewCaseView addCaseItem(Long reviewCaseId, Long reviewItemId) {
            findById(reviewItemId).orElseThrow();
            ReviewCaseView current = cases.get(reviewCaseId);
            if (current == null) {
                throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
            }
            if (SupervisionAlertReviewService.REVIEW_CASE_CLOSED.equals(current.status())) {
                throw new IllegalStateException("review case is closed: " + reviewCaseId);
            }
            List<Long> reviewItemIds = caseItemIds.computeIfAbsent(reviewCaseId, key -> new ArrayList<>());
            if (!reviewItemIds.contains(reviewItemId)) {
                reviewItemIds.add(reviewItemId);
                addCaseAudit(reviewCaseId, reviewItemId, "add_item", null, null, LocalDateTime.now());
            }
            ReviewCaseView updated = buildCaseView(
                    reviewCaseId,
                    current.caseNo(),
                    current.title(),
                    current.status(),
                    current.primaryReviewItemId(),
                    current.ownerUserId(),
                    current.notes()
            );
            cases.put(reviewCaseId, updated);
            return updated;
        }

        @Override
        public ReviewCaseView updateCaseOwner(Long reviewCaseId,
                                              Long ownerUserId,
                                              String notes,
                                              Long operatorUserId) {
            ReviewCaseView current = cases.get(reviewCaseId);
            if (current == null) {
                throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
            }
            if (SupervisionAlertReviewService.REVIEW_CASE_CLOSED.equals(current.status())) {
                throw new IllegalStateException("review case is closed: " + reviewCaseId);
            }
            ReviewCaseView updated = buildCaseView(
                    reviewCaseId,
                    current.caseNo(),
                    current.title(),
                    current.status(),
                    current.primaryReviewItemId(),
                    ownerUserId,
                    notes == null || notes.isBlank() ? current.notes() : notes
            );
            cases.put(reviewCaseId, updated);
            addCaseAudit(reviewCaseId, null, "assign_owner", caseOwnerAuditNote(ownerUserId, notes), operatorUserId, LocalDateTime.now());
            return updated;
        }

        @Override
        public ReviewCaseView closeCase(Long reviewCaseId,
                                        String notes,
                                        Long operatorUserId,
                                        LocalDateTime closedAt) {
            ReviewCaseView current = cases.get(reviewCaseId);
            if (current == null) {
                throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
            }
            if (SupervisionAlertReviewService.REVIEW_CASE_CLOSED.equals(current.status())) {
                return current;
            }
            ReviewCaseView updated = buildCaseView(
                    reviewCaseId,
                    current.caseNo(),
                    current.title(),
                    SupervisionAlertReviewService.REVIEW_CASE_CLOSED,
                    current.primaryReviewItemId(),
                    current.ownerUserId(),
                    notes == null || notes.isBlank() ? current.notes() : notes
            );
            cases.put(reviewCaseId, updated);
            addCaseAudit(reviewCaseId, null, "close_case", caseNotesAuditNote(notes), operatorUserId,
                    closedAt == null ? LocalDateTime.now() : closedAt);
            return updated;
        }

        @Override
        public ReviewCaseMergeResult mergeCases(Long targetReviewCaseId,
                                                Long sourceReviewCaseId,
                                                Long operatorUserId,
                                                String notes) {
            if (Objects.equals(targetReviewCaseId, sourceReviewCaseId)) {
                throw new IllegalArgumentException("sourceReviewCaseId must differ from targetReviewCaseId");
            }
            ReviewCaseView target = requireOpenCase(targetReviewCaseId);
            ReviewCaseView source = requireOpenCase(sourceReviewCaseId);
            List<Long> targetReviewItemIds = caseItemIds.computeIfAbsent(targetReviewCaseId, key -> new ArrayList<>());
            List<Long> sourceReviewItemIds = caseItemIds.computeIfAbsent(sourceReviewCaseId, key -> new ArrayList<>());
            if (sourceReviewItemIds.isEmpty()) {
                throw new IllegalStateException("source review case has no clues: " + sourceReviewCaseId);
            }
            for (Long reviewItemId : sourceReviewItemIds) {
                if (!targetReviewItemIds.contains(reviewItemId)) {
                    targetReviewItemIds.add(reviewItemId);
                }
            }
            sourceReviewItemIds.clear();
            ReviewCaseView updatedTarget = buildCaseView(
                    targetReviewCaseId,
                    target.caseNo(),
                    target.title(),
                    target.status(),
                    target.primaryReviewItemId(),
                    target.ownerUserId(),
                    target.notes()
            );
            ReviewCaseView updatedSource = buildCaseView(
                    sourceReviewCaseId,
                    source.caseNo(),
                    source.title(),
                    SupervisionAlertReviewService.REVIEW_CASE_MERGED,
                    source.primaryReviewItemId(),
                    source.ownerUserId(),
                    notes == null || notes.isBlank() ? source.notes() : notes
            );
            cases.put(targetReviewCaseId, updatedTarget);
            cases.put(sourceReviewCaseId, updatedSource);
            addCaseAudit(targetReviewCaseId, null, "merge_case", caseRelatedAuditNote("sourceReviewCaseId", sourceReviewCaseId, null, notes), operatorUserId, LocalDateTime.now());
            addCaseAudit(sourceReviewCaseId, null, "merge_case", caseRelatedAuditNote("targetReviewCaseId", targetReviewCaseId, null, notes), operatorUserId, LocalDateTime.now());
            return new ReviewCaseMergeResult(updatedTarget, updatedSource);
        }

        @Override
        public ReviewCaseSplitResult splitCase(Long sourceReviewCaseId,
                                               ReviewCaseDraft draft,
                                               List<Long> reviewItemIds,
                                               Long operatorUserId) {
            Objects.requireNonNull(draft, "draft");
            ReviewCaseView source = requireOpenCase(sourceReviewCaseId);
            LinkedHashSet<Long> splitReviewItemIds = new LinkedHashSet<>(reviewItemIds == null ? List.of() : reviewItemIds);
            if (splitReviewItemIds.isEmpty()) {
                throw new IllegalArgumentException("reviewItemIds must not be empty");
            }
            for (Long reviewItemId : splitReviewItemIds) {
                findById(reviewItemId).orElseThrow();
            }
            List<Long> sourceReviewItemIds = caseItemIds.computeIfAbsent(sourceReviewCaseId, key -> new ArrayList<>());
            if (!sourceReviewItemIds.containsAll(splitReviewItemIds)) {
                throw new IllegalArgumentException("reviewItemIds must belong to source review case");
            }
            if (sourceReviewItemIds.size() == splitReviewItemIds.size()) {
                throw new IllegalArgumentException("split must leave at least one clue in source review case");
            }
            sourceReviewItemIds.removeIf(splitReviewItemIds::contains);
            Long sourcePrimaryReviewItemId = splitReviewItemIds.contains(source.primaryReviewItemId())
                    ? sourceReviewItemIds.get(0)
                    : source.primaryReviewItemId();
            long newCaseId = nextCaseId++;
            caseItemIds.put(newCaseId, new ArrayList<>(splitReviewItemIds));
            ReviewCaseView updatedSource = buildCaseView(
                    sourceReviewCaseId,
                    source.caseNo(),
                    source.title(),
                    source.status(),
                    sourcePrimaryReviewItemId,
                    source.ownerUserId(),
                    source.notes()
            );
            ReviewCaseView newCase = buildCaseView(
                    newCaseId,
                    "RC-" + newCaseId,
                    draft.title() == null || draft.title().isBlank() ? "review-case" : draft.title(),
                    SupervisionAlertReviewService.REVIEW_CASE_OPEN,
                    draft.primaryReviewItemId(),
                    draft.ownerUserId(),
                    draft.notes()
            );
            cases.put(sourceReviewCaseId, updatedSource);
            cases.put(newCaseId, newCase);
            addCaseAudit(newCaseId, draft.primaryReviewItemId(), "create_case", draft.notes(), draft.ownerUserId(), LocalDateTime.now());
            addCaseAudit(sourceReviewCaseId, null, "split_case", caseRelatedAuditNote("newReviewCaseId", newCaseId, splitReviewItemIds, draft.notes()), operatorUserId, LocalDateTime.now());
            addCaseAudit(newCaseId, null, "split_case", caseRelatedAuditNote("sourceReviewCaseId", sourceReviewCaseId, splitReviewItemIds, draft.notes()), operatorUserId, LocalDateTime.now());
            return new ReviewCaseSplitResult(updatedSource, newCase);
        }

        @Override
        public List<ReviewCaseTimelineItem> listCaseTimeline(Long reviewCaseId) {
            if (!cases.containsKey(reviewCaseId)) {
                throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
            }
            List<ReviewCaseTimelineItem> timeline = new ArrayList<>();
            for (Long reviewItemId : caseItemIds.getOrDefault(reviewCaseId, List.of())) {
                ReviewItemAggregate item = findById(reviewItemId).orElseThrow();
                for (ReviewEvidenceItem evidenceItem : evidenceByItemId.getOrDefault(reviewItemId, List.of())) {
                    timeline.add(new ReviewCaseTimelineItem(
                            reviewCaseId,
                            reviewItemId,
                            item.cameraId(),
                            evidenceItem.sourceAlertId(),
                            evidenceItem.materialType(),
                            evidenceItem.materialUri(),
                            evidenceItem.happenedAt(),
                            null,
                            evidenceItem.recordStartTime(),
                            null
                    ));
                }
            }
            timeline.addAll(caseLevelEvidence.getOrDefault(reviewCaseId, List.of()));
            timeline.addAll(caseAudits.getOrDefault(reviewCaseId, List.of()));
            return timeline;
        }

        @Override
        public ReviewSemanticIndexEntry upsertSemanticIndex(ReviewItemAggregate item,
                                                           String document,
                                                           String embeddingKey,
                                                           String embeddingModel,
                                                           String embeddingVectorHash,
                                                           String indexStatus,
                                                           Integer retryCount,
                                                           String lastError,
                                                           LocalDateTime indexedAt) {
            ReviewSemanticIndexEntry current = semanticIndex.get(item.id());
            return upsertSemanticIndex(
                    item,
                    document,
                    embeddingKey,
                    embeddingModel,
                    embeddingVectorHash,
                    indexStatus,
                    retryCount,
                    lastError,
                    indexedAt,
                    current == null ? null : current.indexGenerationId(),
                    null
            );
        }

        @Override
        public synchronized ReviewSemanticIndexEntry upsertSemanticIndex(ReviewItemAggregate item,
                                                                        String document,
                                                                        String embeddingKey,
                                                                        String embeddingModel,
                                                                        String embeddingVectorHash,
                                                                        String indexStatus,
                                                                        Integer retryCount,
                                                                        String lastError,
                                                                        LocalDateTime indexedAt,
                                                                        String indexGenerationId,
                                                                        LocalDateTime nextRetryAt) {
            ReviewSemanticIndexEntry current = semanticIndex.get(item.id());
            int indexVersion = (current == null || current.indexVersion() == null ? 0 : current.indexVersion()) + 1;
            ReviewSemanticIndexEntry entry = new ReviewSemanticIndexEntry(
                    item.id(),
                    item.cameraId(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    indexStatus,
                    document,
                    embeddingKey,
                    embeddingModel,
                    embeddingVectorHash,
                    retryCount == null ? 0 : retryCount,
                    lastError,
                    indexedAt,
                    indexGenerationId,
                    null,
                    null,
                    null,
                    nextRetryAt,
                    indexVersion
            );
            semanticIndex.put(item.id(), entry);
            return entry;
        }

        @Override
        public synchronized List<ReviewSemanticIndexEntry> claimSemanticIndex(List<Long> reviewItemIds,
                                                                              Integer limit,
                                                                              String claimToken,
                                                                              LocalDateTime claimedAt,
                                                                              LocalDateTime claimExpiresAt) {
            int normalizedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
            List<ReviewSemanticIndexEntry> claimed = new ArrayList<>();
            for (Long reviewItemId : reviewItemIds == null ? List.<Long>of() : reviewItemIds) {
                ReviewSemanticIndexEntry current = semanticIndex.get(reviewItemId);
                if (current == null || !semanticIndexClaimable(current, claimedAt)) {
                    continue;
                }
                ReviewSemanticIndexEntry processing = new ReviewSemanticIndexEntry(
                        current.reviewItemId(),
                        current.cameraId(),
                        current.firstAlertTime(),
                        current.lastAlertTime(),
                        SupervisionAlertReviewService.SEMANTIC_INDEX_PROCESSING,
                        current.document(),
                        current.embeddingKey(),
                        current.embeddingModel(),
                        current.embeddingVectorHash(),
                        current.retryCount(),
                        current.lastError(),
                        current.indexedAt(),
                        current.indexGenerationId(),
                        claimToken,
                        claimedAt,
                        claimExpiresAt,
                        current.nextRetryAt(),
                        current.indexVersion()
                );
                semanticIndex.put(reviewItemId, processing);
                claimed.add(processing);
                if (claimed.size() >= normalizedLimit) {
                    break;
                }
            }
            return List.copyOf(claimed);
        }

        @Override
        public synchronized ReviewSemanticIndexEntry queueSemanticIndex(ReviewItemAggregate item,
                                                                         String document,
                                                                         String embeddingKey,
                                                                         String embeddingModel,
                                                                         String indexGenerationId,
                                                                         LocalDateTime queuedAt) {
            ReviewSemanticIndexEntry current = semanticIndex.get(item.id());
            if (current != null
                    && SupervisionAlertReviewService.SEMANTIC_INDEX_PROCESSING.equals(current.indexStatus())
                    && current.claimToken() != null
                    && current.claimExpiresAt() != null
                    && current.claimExpiresAt().isAfter(queuedAt)) {
                return current;
            }
            return upsertSemanticIndex(
                    item,
                    document,
                    embeddingKey,
                    embeddingModel,
                    null,
                    SupervisionAlertReviewService.SEMANTIC_INDEX_PENDING,
                    0,
                    null,
                    null,
                    indexGenerationId,
                    null
            );
        }

        @Override
        public synchronized ReviewSemanticIndexEntry completeSemanticIndexClaim(ReviewItemAggregate item,
                                                                                String document,
                                                                                String embeddingKey,
                                                                                String embeddingModel,
                                                                                String embeddingVectorHash,
                                                                                String indexStatus,
                                                                                Integer retryCount,
                                                                                String lastError,
                                                                                LocalDateTime indexedAt,
                                                                                String indexGenerationId,
                                                                                LocalDateTime nextRetryAt,
                                                                                String claimToken) {
            ReviewSemanticIndexEntry current = semanticIndex.get(item.id());
            if (current == null || !Objects.equals(claimToken, current.claimToken())) {
                throw new IllegalStateException("semantic_index_claim_conflict: " + item.id());
            }
            ReviewSemanticIndexEntry updated = upsertSemanticIndex(
                    item,
                    document,
                    embeddingKey,
                    embeddingModel,
                    embeddingVectorHash,
                    indexStatus,
                    retryCount,
                    lastError,
                    indexedAt
            );
            ReviewSemanticIndexEntry completed = new ReviewSemanticIndexEntry(
                    updated.reviewItemId(),
                    updated.cameraId(),
                    updated.firstAlertTime(),
                    updated.lastAlertTime(),
                    updated.indexStatus(),
                    updated.document(),
                    updated.embeddingKey(),
                    updated.embeddingModel(),
                    updated.embeddingVectorHash(),
                    updated.retryCount(),
                    updated.lastError(),
                    updated.indexedAt(),
                    indexGenerationId,
                    null,
                    null,
                    null,
                    nextRetryAt,
                    updated.indexVersion()
            );
            semanticIndex.put(item.id(), completed);
            return completed;
        }

        private static boolean semanticIndexClaimable(ReviewSemanticIndexEntry entry, LocalDateTime claimedAt) {
            if (SupervisionAlertReviewService.SEMANTIC_INDEX_PENDING.equals(entry.indexStatus())) {
                return true;
            }
            if (SupervisionAlertReviewService.SEMANTIC_INDEX_FAILED.equals(entry.indexStatus())) {
                return entry.nextRetryAt() == null || !entry.nextRetryAt().isAfter(claimedAt);
            }
            return SupervisionAlertReviewService.SEMANTIC_INDEX_PROCESSING.equals(entry.indexStatus())
                    && (entry.claimExpiresAt() == null || !entry.claimExpiresAt().isAfter(claimedAt));
        }

        synchronized void makeSemanticRetryDue(Long reviewItemId) {
            ReviewSemanticIndexEntry current = semanticIndex.get(reviewItemId);
            semanticIndex.put(reviewItemId, new ReviewSemanticIndexEntry(
                    current.reviewItemId(),
                    current.cameraId(),
                    current.firstAlertTime(),
                    current.lastAlertTime(),
                    current.indexStatus(),
                    current.document(),
                    current.embeddingKey(),
                    current.embeddingModel(),
                    current.embeddingVectorHash(),
                    current.retryCount(),
                    current.lastError(),
                    current.indexedAt(),
                    current.indexGenerationId(),
                    current.claimToken(),
                    current.claimedAt(),
                    current.claimExpiresAt(),
                    LocalDateTime.now().minusSeconds(1),
                    current.indexVersion()
            ));
        }

        @Override
        public List<ReviewSemanticIndexEntry> listSemanticIndex(ReviewQuery query) {
            List<Long> reviewItemIds = listWorkbench(query).stream()
                    .map(ReviewItemAggregate::id)
                    .toList();
            return reviewItemIds.stream()
                    .map(semanticIndex::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        @Override
        public ReviewEvidenceExportJob createExportJob(ReviewEvidenceExportPackage exportPackage,
                                                       Long operatorUserId,
                                                       String reason,
                                                       List<Long> boundEventIds,
                                                       String fileHash,
                                                       LocalDateTime expiresAt,
                                                       LocalDateTime createdAt) {
            if (!cases.containsKey(exportPackage.reviewCaseId())) {
                throw new IllegalArgumentException("reviewCaseId not found: " + exportPackage.reviewCaseId());
            }
            Object requestKey = exportPackage.manifest().get("requestKey");
            Optional<ReviewEvidenceExportJob> duplicate = exportJobs.values().stream()
                    .filter(existing -> !SupervisionAlertReviewService.EXPORT_JOB_EXPIRED.equals(existing.status()))
                    .filter(existing -> Objects.equals(
                            requestKey,
                            existing.exportPackage().manifest().get("requestKey")
                    ))
                    .findFirst();
            if (duplicate.isPresent()) {
                return duplicate.get();
            }
            String jobNo = "REJ-" + nextExportJobId++;
            ReviewEvidenceExportJob job = new ReviewEvidenceExportJob(
                    jobNo,
                    SupervisionAlertReviewService.EXPORT_JOB_PENDING,
                    exportPackage,
                    fileHash,
                    expiresAt,
                    operatorUserId,
                    reason,
                    boundEventIds == null ? List.of() : List.copyOf(boundEventIds),
                    createdAt,
                    0
            );
            exportJobs.put(jobNo, job);
            String auditNote = "jobNo=" + jobNo + "; fileHash=" + fileHash
                    + (reason == null || reason.isBlank() ? "" : "; reason=" + reason);
            caseAudits.computeIfAbsent(exportPackage.reviewCaseId(), key -> new ArrayList<>())
                    .add(new ReviewCaseTimelineItem(
                            exportPackage.reviewCaseId(),
                            null,
                            null,
                            null,
                            "case_audit",
                            "export_evidence_job",
                            createdAt,
                            auditNote
                    ));
            return job;
        }

        private ReviewEvidenceExportJob replaceExportJobForTest(ReviewEvidenceExportJob job) {
            ReviewEvidenceExportJob current = exportJobs.get(job.jobNo());
            if (current == null) {
                throw new IllegalArgumentException("export job not found: " + job.jobNo());
            }
            ReviewEvidenceExportJob updated = exportJobWithVersion(job, current.version() + 1);
            exportJobs.put(job.jobNo(), updated);
            return updated;
        }

        @Override
        public List<ReviewEvidenceExportJob> listExportJobs(Long reviewCaseId) {
            return exportJobs.values().stream()
                    .filter(job -> Objects.equals(reviewCaseId, job.exportPackage().reviewCaseId()))
                    .toList();
        }

        @Override
        public List<ReviewEvidenceExportJob> listEvidenceAuditExportJobs(ReviewEvidenceAuditQuery query) {
            return exportJobs.values().stream()
                    .filter(job -> query.reviewCaseId() == null
                            || Objects.equals(query.reviewCaseId(), job.exportPackage().reviewCaseId()))
                    .filter(job -> query.reviewItemId() == null
                            || job.exportPackage().reviewItemIds().contains(query.reviewItemId()))
                    .filter(job -> query.eventId() == null || exportJobMatchesEvent(job, query.eventId()))
                    .filter(job -> query.exportJobNo() == null || Objects.equals(query.exportJobNo(), job.jobNo()))
                    .toList();
        }

        @Override
        public List<ReviewEvidenceExportJob> listAllExportJobs() {
            return List.copyOf(exportJobs.values());
        }

        @Override
        public List<ReviewEvidenceExportJob> claimProcessableExportJobs(Integer limit,
                                                                        String claimToken,
                                                                        Long claimedBy,
                                                                        LocalDateTime claimedAt,
                                                                        LocalDateTime reclaimBefore) {
            int normalizedLimit = limit == null || limit <= 0 ? 20 : limit;
            List<ReviewEvidenceExportJob> claimed = new ArrayList<>();
            for (ReviewEvidenceExportJob current : List.copyOf(exportJobs.values())) {
                if (claimed.size() >= normalizedLimit || !inMemoryExportJobClaimable(current, claimedAt, reclaimBefore)) {
                    continue;
                }
                ReviewEvidenceExportJob running = new ReviewEvidenceExportJob(
                        current.jobNo(),
                        SupervisionAlertReviewService.EXPORT_JOB_RUNNING,
                        current.exportPackage(),
                        current.fileHash(),
                        current.expiresAt(),
                        current.operatorUserId(),
                        current.reason(),
                        current.boundEventIds(),
                        current.createdAt(),
                        current.version() + 1
                );
                exportJobs.put(running.jobNo(), running);
                exportJobClaims.put(running.jobNo(), claimToken);
                exportJobClaimedAt.put(running.jobNo(), claimedAt);
                claimed.add(running);
            }
            return List.copyOf(claimed);
        }

        @Override
        public ReviewEvidenceExportJob completeExportJobClaim(ReviewEvidenceExportJob job, String claimToken) {
            ReviewEvidenceExportJob current = exportJobs.get(job.jobNo());
            if (current == null) {
                throw new IllegalArgumentException("export job not found: " + job.jobNo());
            }
            if (!Objects.equals(claimToken, exportJobClaims.get(job.jobNo()))
                    || !Objects.equals(current.version(), job.version())) {
                throw new IllegalStateException("export_job_claim_conflict: " + job.jobNo());
            }
            ReviewEvidenceExportJob updated = exportJobWithVersion(job, current.version() + 1);
            exportJobs.put(job.jobNo(), updated);
            exportJobClaims.remove(job.jobNo());
            exportJobClaimedAt.remove(job.jobNo());
            return updated;
        }

        private boolean inMemoryExportJobClaimable(ReviewEvidenceExportJob job,
                                                   LocalDateTime claimedAt,
                                                   LocalDateTime reclaimBefore) {
            boolean expired = job.expiresAt() != null && claimedAt != null && !job.expiresAt().isAfter(claimedAt);
            if (SupervisionAlertReviewService.EXPORT_JOB_PENDING.equals(job.status())) {
                return true;
            }
            if (SupervisionAlertReviewService.EXPORT_JOB_READY.equals(job.status())) {
                return expired;
            }
            if (SupervisionAlertReviewService.EXPORT_JOB_FAILED.equals(job.status())) {
                LocalDateTime nextRetryAt = inMemoryExportWorkerTime(job, "nextRetryAt");
                return expired || nextRetryAt == null || claimedAt == null || !nextRetryAt.isAfter(claimedAt);
            }
            if (SupervisionAlertReviewService.EXPORT_JOB_RUNNING.equals(job.status())) {
                LocalDateTime processedAt = exportJobClaimedAt.get(job.jobNo());
                if (processedAt == null) {
                    processedAt = inMemoryExportWorkerTime(job, "processedAt");
                }
                return reclaimBefore != null && (processedAt == null || processedAt.isBefore(reclaimBefore));
            }
            return false;
        }

        private static LocalDateTime inMemoryExportWorkerTime(ReviewEvidenceExportJob job, String field) {
            Object worker = job.exportPackage().manifest().get("worker");
            if (!(worker instanceof Map<?, ?> values) || values.get(field) == null) {
                return null;
            }
            try {
                return LocalDateTime.parse(String.valueOf(values.get(field)));
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        private static ReviewEvidenceExportJob exportJobWithVersion(ReviewEvidenceExportJob job, int version) {
            return new ReviewEvidenceExportJob(
                    job.jobNo(),
                    job.status(),
                    job.exportPackage(),
                    job.fileHash(),
                    job.expiresAt(),
                    job.operatorUserId(),
                    job.reason(),
                    job.boundEventIds(),
                    job.createdAt(),
                    version
            );
        }

        void replaceExportJobStatus(String jobNo, String status) {
            ReviewEvidenceExportJob job = exportJobs.get(jobNo);
            if (job == null) {
                throw new IllegalArgumentException("export job not found: " + jobNo);
            }
            exportJobs.put(jobNo, new ReviewEvidenceExportJob(
                    job.jobNo(),
                    status,
                    job.exportPackage(),
                    job.fileHash(),
                    job.expiresAt(),
                    job.operatorUserId(),
                    job.reason(),
                    job.boundEventIds(),
                    job.createdAt(),
                    job.version()
            ));
        }

        @Override
        public int enqueueRuntimePatrolAlerts(String runId,
                                              List<String> alerts,
                                              List<String> recommendedActions,
                                              Long operatorUserId,
                                              LocalDateTime executedAt,
                                              Map<String, Object> metadata) {
            if (alerts == null || alerts.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (String alert : alerts) {
                if (alert == null || alert.isBlank()) {
                    continue;
                }
                runtimeOutbox.add(new RuntimeOutboxEntry(
                        runId,
                        alert,
                        runtimeOutboxAction(alert, metadata),
                        recommendedActions == null ? List.of() : List.copyOf(recommendedActions),
                        operatorUserId,
                        "pending",
                        null,
                        null,
                        null,
                        metadata == null ? Map.of() : Map.copyOf(metadata),
                        nextRuntimeOutboxId++,
                        "alert=" + alert,
                        0,
                        null,
                        null
                ));
                count++;
            }
            return count;
        }

        @Override
        public int enqueueOperationsReportDelivery(ReviewOperationsReport report,
                                                   boolean scheduled,
                                                   LocalDateTime queuedAt) {
            if (report == null) {
                return 0;
            }
            Map<String, Object> deliveryPlan = report.deliveryPlan() == null ? Map.of() : report.deliveryPlan();
            Map<String, Object> acknowledgement = report.acknowledgement() == null ? Map.of() : report.acknowledgement();
            Object rawReportKey = deliveryPlan.get("reportKey") == null
                    ? acknowledgement.get("reportKey")
                    : deliveryPlan.get("reportKey");
            if (rawReportKey == null || String.valueOf(rawReportKey).isBlank()) {
                return 0;
            }
            String reportKey = String.valueOf(rawReportKey);
            boolean existingActive = runtimeOutbox.stream()
                    .anyMatch(entry -> reportKey.equals(entry.alert())
                            && "deliver_operations_report".equals(entry.action())
                            && List.of("pending", "processing", "published").contains(entry.status()));
            if (existingActive) {
                return 0;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("reportKey", reportKey);
            metadata.put("reportType", report.reportType());
            metadata.put("scheduled", scheduled);
            metadata.put("channels", deliveryPlan.get("channels"));
            metadata.put("deliveryStatus", deliveryPlan.get("deliveryStatus"));
            metadata.put("acknowledgementStatus", acknowledgement.get("status"));
            metadata.put("reviewItemIds", report.reviewItemIds());
            metadata.put("generatedAt", report.generatedAt() == null ? null : report.generatedAt().toString());
            metadata.entrySet().removeIf(entry -> entry.getValue() == null);
            runtimeOutbox.add(new RuntimeOutboxEntry(
                    reportKey,
                    reportKey,
                    "deliver_operations_report",
                    report.recommendedActions() == null ? List.of() : List.copyOf(report.recommendedActions()),
                    report.operatorUserId(),
                    "pending",
                    null,
                    null,
                    null,
                    Map.copyOf(metadata),
                    nextRuntimeOutboxId++,
                    "report=" + reportKey,
                    0,
                    null,
                    null
            ));
            return 1;
        }

        @Override
        public List<ReviewRuntimeOutboxMessage> listPendingRuntimeOutbox(Integer limit) {
            int normalizedLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
            return runtimeOutbox.stream()
                    .filter(entry -> "pending".equals(entry.status()))
                    .limit(normalizedLimit)
                    .map(InMemoryReviewItemStore::toRuntimeOutboxMessage)
                    .toList();
        }

        @Override
        public List<ReviewRuntimeOutboxMessage> claimPendingRuntimeOutbox(Integer limit,
                                                                          String claimToken,
                                                                          Long operatorUserId,
                                                                          LocalDateTime claimedAt) {
            return claimPendingRuntimeOutbox(limit, claimToken, operatorUserId, claimedAt, null);
        }

        @Override
        public List<ReviewRuntimeOutboxMessage> claimPendingRuntimeOutbox(Integer limit,
                                                                          String claimToken,
                                                                          Long operatorUserId,
                                                                          LocalDateTime claimedAt,
                                                                          LocalDateTime reclaimBefore) {
            int normalizedLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
            List<ReviewRuntimeOutboxMessage> claimed = new ArrayList<>();
            for (int index = 0; index < runtimeOutbox.size() && claimed.size() < normalizedLimit; index++) {
                RuntimeOutboxEntry entry = runtimeOutbox.get(index);
                if (!runtimeOutboxClaimable(entry, reclaimBefore)) {
                    continue;
                }
                RuntimeOutboxEntry claimedEntry = new RuntimeOutboxEntry(
                        entry.runId(),
                        entry.alert(),
                        entry.action(),
                        entry.recommendedActions(),
                        entry.operatorUserId(),
                        "processing",
                        claimToken,
                        operatorUserId,
                        claimedAt,
                        entry.metadata(),
                        entry.id(),
                        entry.payload(),
                        entry.retryCount(),
                        entry.publishedAt(),
                        entry.lastError()
                );
                runtimeOutbox.set(index, claimedEntry);
                claimed.add(toRuntimeOutboxMessage(claimedEntry));
            }
            return List.copyOf(claimed);
        }

        private static boolean runtimeOutboxClaimable(RuntimeOutboxEntry entry, LocalDateTime reclaimBefore) {
            if ("pending".equals(entry.status())) {
                return true;
            }
            if (!"processing".equals(entry.status())) {
                return false;
            }
            return reclaimBefore != null
                    && (entry.claimedAt() == null || entry.claimedAt().isBefore(reclaimBefore));
        }

        private static ReviewRuntimeOutboxMessage toRuntimeOutboxMessage(RuntimeOutboxEntry entry) {
            return new ReviewRuntimeOutboxMessage(
                    entry.id(),
                    entry.runId(),
                    "deliver_operations_report".equals(entry.action())
                            ? "review_operations_report"
                            : "review_runtime_alert",
                    entry.alert(),
                    entry.payload(),
                    entry.retryCount(),
                    null
            );
        }

        @Override
        public void markRuntimeOutboxPublished(Long outboxId, LocalDateTime publishedAt) {
            replaceRuntimeOutboxEntry(outboxId, entry -> new RuntimeOutboxEntry(
                    entry.runId(),
                    entry.alert(),
                    entry.action(),
                    entry.recommendedActions(),
                    entry.operatorUserId(),
                    "published",
                    entry.claimToken(),
                    entry.claimedBy(),
                    entry.claimedAt(),
                    entry.metadata(),
                    entry.id(),
                    entry.payload(),
                    entry.retryCount(),
                    publishedAt,
                    null
            ));
        }

        @Override
        public void markRuntimeOutboxFailed(Long outboxId, String lastError, LocalDateTime failedAt) {
            replaceRuntimeOutboxEntry(outboxId, entry -> new RuntimeOutboxEntry(
                    entry.runId(),
                    entry.alert(),
                    entry.action(),
                    entry.recommendedActions(),
                    entry.operatorUserId(),
                    "failed",
                    entry.claimToken(),
                    entry.claimedBy(),
                    entry.claimedAt(),
                    entry.metadata(),
                    entry.id(),
                    entry.payload(),
                    (entry.retryCount() == null ? 0 : entry.retryCount()) + 1,
                    failedAt,
                    lastError
            ));
        }

        private void replaceRuntimeOutboxEntry(Long outboxId, Function<RuntimeOutboxEntry, RuntimeOutboxEntry> updater) {
            for (int index = 0; index < runtimeOutbox.size(); index++) {
                RuntimeOutboxEntry entry = runtimeOutbox.get(index);
                if (Objects.equals(outboxId, entry.id())) {
                    runtimeOutbox.set(index, updater.apply(entry));
                    return;
                }
            }
        }

        List<RuntimeOutboxEntry> runtimeOutbox() {
            return List.copyOf(runtimeOutbox);
        }

        private static String runtimeOutboxAction(String alert, Map<String, Object> metadata) {
            Object rawAlertActions = metadata == null ? null : metadata.get("alertActions");
            if (!(rawAlertActions instanceof Map<?, ?> alertActions)) {
                return null;
            }
            Object action = alertActions.get(alert);
            return action == null ? null : String.valueOf(action);
        }

        @Override
        public Optional<ReviewEvidenceExportJob> findExportJobByNo(String jobNo) {
            return Optional.ofNullable(exportJobs.get(jobNo));
        }

        @Override
        public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                               Long operatorUserId,
                                                               String reason,
                                                               LocalDateTime happenedAt,
                                                               String downloadFileHash,
                                                               Map<String, Object> downloadMetadata) {
            ReviewEvidenceExportJob job = exportJobs.get(jobNo);
            if (job == null) {
                throw new IllegalArgumentException("export job not found: " + jobNo);
            }
            LocalDateTime auditTime = happenedAt == null ? LocalDateTime.now() : happenedAt;
            if (downloadFileHash == null || downloadFileHash.isBlank()) {
                throw new IllegalArgumentException("downloadFileHash is required from downloaded bytes");
            }
            String auditedFileHash = downloadFileHash;
            Map<String, Object> metadata = new LinkedHashMap<>(
                    downloadMetadata == null ? Map.of() : downloadMetadata);
            metadata.put("status", job.status());
            metadata.put("downloadFileHash", auditedFileHash);
            metadata.put("logicalPackageHash", job.fileHash());
            metadata.put("hashSource", "downloaded_bytes");
            String auditNote = "jobNo=" + jobNo + "; fileHash=" + auditedFileHash
                    + "; downloadFileHash=" + auditedFileHash
                    + "; logicalPackageHash=" + job.fileHash()
                    + (operatorUserId == null ? "" : "; operatorUserId=" + operatorUserId)
                    + (reason == null || reason.isBlank() ? "" : "; reason=" + reason);
            caseAudits.computeIfAbsent(job.exportPackage().reviewCaseId(), key -> new ArrayList<>())
                    .add(new ReviewCaseTimelineItem(
                            job.exportPackage().reviewCaseId(),
                            null,
                            null,
                            null,
                            "case_audit",
                            "export_downloaded",
                            auditTime,
                            auditNote
                    ));
            return new ReviewEvidenceAuditEntry(
                    job.exportPackage().reviewCaseId(),
                    null,
                    "export_downloaded",
                    jobNo,
                    auditedFileHash,
                    operatorUserId,
                    reason,
                    job.exportPackage().evidenceUris(),
                    job.boundEventIds(),
                    auditTime,
                    Map.copyOf(metadata)
            );
        }

        @Override
        public void recordCaseAudit(Long reviewCaseId,
                                    Long reviewItemId,
                                    String actionType,
                                    String actionNote,
                                    Long operatorUserId,
                                    LocalDateTime happenedAt,
                                    Map<String, Object> metadata) {
            if (!cases.containsKey(reviewCaseId)) {
                throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
            }
            if (reviewItemId != null) {
                findById(reviewItemId).orElseThrow();
            }
            caseAudits.computeIfAbsent(reviewCaseId, key -> new ArrayList<>())
                    .add(new ReviewCaseTimelineItem(
                            reviewCaseId,
                            reviewItemId,
                            null,
                            null,
                            "case_audit",
                            actionType,
                            happenedAt == null ? LocalDateTime.now() : happenedAt,
                            actionNote
                    ));
        }

        @Override
        public void recordMediaAccessAudit(Long reviewCaseId,
                                           Long reviewItemId,
                                           String actionType,
                                           String actionNote,
                                           Long operatorUserId,
                                           LocalDateTime happenedAt,
                                           Map<String, Object> metadata) {
            if (reviewCaseId != null) {
                recordCaseAudit(reviewCaseId, reviewItemId, actionType, actionNote, operatorUserId, happenedAt, metadata);
                return;
            }
            findById(reviewItemId).orElseThrow();
            itemMediaAudits.computeIfAbsent(reviewItemId, key -> new ArrayList<>())
                    .add(new ReviewCaseTimelineItem(
                            null,
                            reviewItemId,
                            null,
                            null,
                            "case_audit",
                            actionType,
                            happenedAt == null ? LocalDateTime.now() : happenedAt,
                            actionNote
                    ));
        }

        @Override
        public List<ReviewCaseTimelineItem> listMediaAccessAuditsByReviewItem(Long reviewItemId) {
            findById(reviewItemId).orElseThrow();
            return List.copyOf(itemMediaAudits.getOrDefault(reviewItemId, List.of()));
        }

        @Override
        public List<ReviewCaseTimelineItem> listEvidenceAuditRecords(ReviewEvidenceAuditQuery query) {
            List<ReviewCaseTimelineItem> audits = new ArrayList<>();
            caseAudits.values().forEach(audits::addAll);
            itemMediaAudits.values().forEach(audits::addAll);
            return audits.stream()
                    .filter(audit -> "export_downloaded".equals(audit.materialUri())
                            || "media_access_granted".equals(audit.materialUri())
                            || "media_access_denied".equals(audit.materialUri()))
                    .filter(audit -> query.reviewCaseId() == null
                            || Objects.equals(query.reviewCaseId(), audit.reviewCaseId()))
                    .filter(audit -> query.reviewItemId() == null
                            || Objects.equals(query.reviewItemId(), audit.reviewItemId())
                            || auditExportJob(audit)
                            .map(job -> job.exportPackage().reviewItemIds().contains(query.reviewItemId()))
                            .orElse(false))
                    .filter(audit -> query.eventId() == null
                            || Optional.ofNullable(audit.reviewItemId())
                            .flatMap(this::findById)
                            .map(ReviewItemAggregate::eventId)
                            .filter(query.eventId()::equals)
                            .isPresent()
                            || auditExportJob(audit)
                            .map(job -> exportJobMatchesEvent(job, query.eventId()))
                            .orElse(false))
                    .filter(audit -> query.exportJobNo() == null
                            || auditExportJob(audit)
                            .map(ReviewEvidenceExportJob::jobNo)
                            .filter(query.exportJobNo()::equals)
                            .isPresent()
                            || Optional.ofNullable(audit.reviewItemId())
                            .map(reviewItemId -> exportJobs.get(query.exportJobNo()))
                            .filter(Objects::nonNull)
                            .map(job -> job.exportPackage().reviewItemIds().contains(audit.reviewItemId()))
                            .orElse(false))
                    .toList();
        }

        @Override
        public void recordSemanticTriggerEvaluation(Long reviewItemId,
                                                    String actionNote,
                                                    Long operatorUserId,
                                                    LocalDateTime happenedAt,
                                                    Map<String, Object> metadata) {
            String evaluationId = String.valueOf(metadata.get("evaluationId"));
            semanticTriggerAudits.computeIfAbsent(evaluationId, ignored -> new ArrayList<>())
                    .add(new SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord(
                            reviewItemId,
                            "semantic_trigger_evaluated",
                            operatorUserId,
                            happenedAt,
                            metadata
                    ));
        }

        @Override
        public boolean recordSemanticTriggerDecision(String evaluationId,
                                                      String actionType,
                                                      String actionNote,
                                                      Long operatorUserId,
                                                      LocalDateTime happenedAt,
                                                      Map<String, Object> metadata) {
            List<SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord> audits =
                    semanticTriggerAudits.getOrDefault(evaluationId, List.of());
            if (audits.stream().anyMatch(audit ->
                    ("semantic_trigger_confirmed".equals(audit.actionType())
                            || "semantic_trigger_rejected".equals(audit.actionType()))
                            && "semantic-trigger-evaluation-v1".equals(audit.metadata().get("schemaVersion"))
                            && evaluationId.equals(audit.metadata().get("evaluationId")))) {
                return false;
            }
            Long reviewItemId = audits.stream()
                    .filter(audit -> "semantic_trigger_evaluated".equals(audit.actionType()))
                    .map(SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord::reviewItemId)
                    .findFirst()
                    .orElse(null);
            semanticTriggerAudits.computeIfAbsent(evaluationId, ignored -> new ArrayList<>())
                    .add(new SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord(
                            reviewItemId,
                            actionType,
                            operatorUserId,
                            happenedAt,
                            metadata
                    ));
            return true;
        }

        @Override
        public List<SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord> listSemanticTriggerAudits(
                String evaluationId) {
            return List.copyOf(semanticTriggerAudits.getOrDefault(evaluationId, List.of()));
        }

        private boolean exportJobMatchesEvent(ReviewEvidenceExportJob job, Long eventId) {
            return job.boundEventIds().contains(eventId)
                    || job.exportPackage().reviewItemIds().stream()
                    .map(items::get)
                    .filter(Objects::nonNull)
                    .map(ReviewItemAggregate::eventId)
                    .anyMatch(eventId::equals);
        }

        private Optional<ReviewEvidenceExportJob> auditExportJob(ReviewCaseTimelineItem audit) {
            if (!"export_downloaded".equals(audit.materialUri()) || audit.actionNote() == null) {
                return Optional.empty();
            }
            return java.util.Arrays.stream(audit.actionNote().split(";"))
                    .map(String::trim)
                    .filter(part -> part.startsWith("jobNo="))
                    .map(part -> part.substring("jobNo=".length()))
                    .map(exportJobs::get)
                    .filter(Objects::nonNull)
                    .findFirst();
        }

        private void addCaseAudit(Long reviewCaseId,
                                  Long reviewItemId,
                                  String actionType,
                                  String actionNote,
                                  Long operatorUserId,
                                  LocalDateTime happenedAt) {
            caseAudits.computeIfAbsent(reviewCaseId, key -> new ArrayList<>())
                    .add(new ReviewCaseTimelineItem(
                            reviewCaseId,
                            reviewItemId,
                            null,
                            null,
                            "case_audit",
                            actionType,
                            happenedAt == null ? LocalDateTime.now() : happenedAt,
                            actionNote == null || actionNote.isBlank()
                                    ? (operatorUserId == null ? "" : "operatorUserId=" + operatorUserId)
                                    : actionNote + (operatorUserId == null ? "" : "; operatorUserId=" + operatorUserId)
                    ));
        }

        private ReviewCaseView requireOpenCase(Long reviewCaseId) {
            ReviewCaseView current = cases.get(reviewCaseId);
            if (current == null) {
                throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
            }
            if (!SupervisionAlertReviewService.REVIEW_CASE_OPEN.equals(current.status())) {
                throw new IllegalStateException("review case is not open: " + reviewCaseId + " status=" + current.status());
            }
            return current;
        }

        private static String caseRelatedAuditNote(String relatedKey,
                                                   Long relatedCaseId,
                                                   Iterable<Long> reviewItemIds,
                                                   String notes) {
            List<String> values = new ArrayList<>();
            values.add(relatedKey + "=" + relatedCaseId);
            if (reviewItemIds != null) {
                values.add("reviewItemIds=" + joinLongCsv(reviewItemIds));
            }
            if (notes != null && !notes.isBlank()) {
                values.add("notes=" + notes);
            }
            return String.join("; ", values);
        }

        private static String caseOwnerAuditNote(Long ownerUserId, String notes) {
            String owner = "ownerUserId=" + ownerUserId;
            return notes == null || notes.isBlank() ? owner : owner + "; notes=" + notes;
        }

        private static String joinLongCsv(Iterable<Long> values) {
            if (values == null) {
                return "";
            }
            List<String> normalized = new ArrayList<>();
            for (Long value : values) {
                if (value != null) {
                    normalized.add(String.valueOf(value));
                }
            }
            return String.join(",", normalized);
        }

        private static String caseNotesAuditNote(String notes) {
            return notes == null || notes.isBlank() ? null : "notes=" + notes;
        }

        private ReviewCaseView buildCaseView(Long reviewCaseId,
                                             String caseNo,
                                             String title,
                                             String status,
                                             Long primaryReviewItemId,
                                             Long ownerUserId,
                                             String notes) {
            List<ReviewItemAggregate> reviewItems = caseItemIds.getOrDefault(reviewCaseId, List.of()).stream()
                    .map(reviewItemId -> findById(reviewItemId).orElseThrow())
                    .toList();
            List<String> cameraIds = reviewItems.stream()
                    .map(ReviewItemAggregate::cameraId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            LocalDateTime startTime = reviewItems.stream()
                    .map(ReviewItemAggregate::firstAlertTime)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime endTime = reviewItems.stream()
                    .map(ReviewItemAggregate::lastAlertTime)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            return new ReviewCaseView(
                    reviewCaseId,
                    caseNo,
                    title,
                    status,
                    primaryReviewItemId,
                    List.copyOf(caseItemIds.getOrDefault(reviewCaseId, List.of())),
                    cameraIds,
                    startTime,
                    endTime,
                    ownerUserId,
                    notes
            );
        }

        private ReviewItemAggregate withCaseFlag(ReviewItemAggregate item) {
            boolean inReviewCase = caseItemIds.values().stream().anyMatch(ids -> ids.contains(item.id()));
            return new ReviewItemAggregate(
                    item.id(),
                    item.reviewItemNo(),
                    item.sourceSystem(),
                    item.ruleCode(),
                    item.sourceAlertType(),
                    item.deviceId(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.firstAlertTime(),
                    item.lastAlertTime(),
                    item.alertCount(),
                    item.sourceAlertIds(),
                    item.reviewData(),
                    item.reviewStatus(),
                    item.reviewerUserId(),
                    item.reviewedAt(),
                    item.ignoreReason(),
                    item.ruleSuggestion(),
                    item.eventId(),
                    item.convertedAt(),
                    item.recordEvidenceStatus(),
                    item.recordEvidenceCheckedAt(),
                    item.recordEvidenceMessage(),
                    item.eventStatus(),
                    item.closeCheckStatus(),
                    item.evidenceStatus(),
                    item.eventReviewStatus(),
                    inReviewCase,
                    item.ruleSuggestionStatus(),
                    item.ruleSuggestionUpdatedAt()
            );
        }

        private static boolean sameEvidence(ReviewEvidenceItem first, ReviewEvidenceItem second) {
            return Objects.equals(first.sourceAlertId(), second.sourceAlertId())
                    && Objects.equals(first.materialType(), second.materialType())
                    && Objects.equals(first.materialUri(), second.materialUri());
        }

        private static String userStatusKey(Long reviewItemId, Long userId) {
            return reviewItemId + ":" + userId;
        }

        private static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
            return first.isBefore(second) ? first : second;
        }

        private static LocalDateTime max(LocalDateTime first, LocalDateTime second) {
            return first.isAfter(second) ? first : second;
        }

    }

    private static final class InMemoryRuleStore implements ReviewRuleStore {

        private final List<ReviewRuleView> rules = new ArrayList<>();
        private long nextId = 2000L;

        @Override
        public ReviewRuleView save(ReviewRuleCommand command) {
            ReviewRuleView view = new ReviewRuleView(
                    command.id() == null ? nextId++ : command.id(),
                    command.ruleCode(),
                    command.ruleName(),
                    command.sourceSystem(),
                    command.cameraId(),
                    command.zoneCode(),
                    command.objectLabel(),
                    command.minStaySeconds(),
                    command.activeStart(),
                    command.activeEnd(),
                    command.enabled(),
                    command.inertiaFrames(),
                    command.loiteringSeconds()
            );
            for (int i = 0; i < rules.size(); i++) {
                if (Objects.equals(rules.get(i).id(), view.id())) {
                    rules.set(i, view);
                    return view;
                }
            }
            rules.add(view);
            return view;
        }

        @Override
        public List<ReviewRuleView> listEnabled() {
            return rules.stream().filter(ReviewRuleView::enabled).toList();
        }

        @Override
        public List<ReviewRuleView> listAll() {
            return List.copyOf(rules);
        }

    }

}
