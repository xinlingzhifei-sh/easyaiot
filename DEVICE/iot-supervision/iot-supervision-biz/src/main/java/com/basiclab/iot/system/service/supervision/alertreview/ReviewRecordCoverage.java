package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.alertreview.ReviewMediaScopeGuard.MediaAccessRef;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.DEFAULT_MERGE_WINDOW_SECONDS;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstSourceAlertId;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;

/**
 * 录像覆盖：录像覆盖区间与缺口、缺口原因目录、录像存储同步与漂移巡检。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewRecordCoverage {

    private final AlertReviewDependencies deps;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;
    private ReviewCaseManagement reviewCaseManagement;

    ReviewRecordCoverage(AlertReviewDependencies deps, ReviewMediaScopeGuard reviewMediaScopeGuard) {
        this.deps = deps;
        this.reviewMediaScopeGuard = reviewMediaScopeGuard;
    }

    void bindReviewCaseManagement(ReviewCaseManagement reviewCaseManagement) {
        this.reviewCaseManagement = reviewCaseManagement;
    }

    private static final String RECORD_GAP_FILE_EXPIRED_ALIAS = "file_expired";
    static final List<RecordGapReasonDefinition> RECORD_GAP_REASON_CATALOG = List.of(
            new RecordGapReasonDefinition(RECORD_GAP_VIDEO_URL_NOT_CONFIGURED, "configuration",
                    "\u672A\u914D\u7F6E", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_RECORD_SPACE_NOT_FOUND, "configuration",
                    "\u65E0\u5F55\u50CF\u7A7A\u95F4", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_FILE_MISSING, "filesystem",
                    "\u6587\u4EF6\u7F3A\u5931", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_PROBE_FAILED, "probe",
                    "\u63A2\u6D4B\u5931\u8D25", true, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_PERMISSION_DENIED, "permission",
                    "\u6743\u9650\u62D2\u7EDD", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_RETENTION_EXPIRED, "retention",
                    "\u8FC7\u671F", false, List.of(RECORD_GAP_FILE_EXPIRED_ALIAS)),
            new RecordGapReasonDefinition(RECORD_GAP_RECORD_NOT_FOUND, "configuration",
                    "\u5F55\u50CF\u672A\u627E\u5230", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_MISSING_LOOKUP_FIELDS, "configuration",
                    "\u67E5\u8BE2\u5B57\u6BB5\u7F3A\u5931", false, List.of()),
            new RecordGapReasonDefinition("service_unavailable", "service",
                    "\u670D\u52A1\u4E0D\u53EF\u7528", true, List.of()),
            new RecordGapReasonDefinition("video_service_unavailable", "service",
                    "\u89C6\u9891\u670D\u52A1\u4E0D\u53EF\u7528", true, List.of()),
            new RecordGapReasonDefinition("stream_interrupted", "stream",
                    "\u7801\u6D41\u4E2D\u65AD", true, List.of()),
            new RecordGapReasonDefinition("recording_disabled", "configuration",
                    "\u5F55\u50CF\u672A\u542F\u7528", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_DISK_FULL, "storage",
                    "\u5F55\u50CF\u78C1\u76D8\u6EE1", false, List.of()),
            new RecordGapReasonDefinition(RECORD_GAP_CACHE_FLUSH_FAILED, "cache",
                    "\u7F13\u5B58\u843D\u76D8\u5931\u8D25", true, List.of())
    );

    record RecordGapReasonDefinition(String code,
                                             String category,
                                             String labelZh,
                                             boolean retryable,
                                             List<String> aliases) {
    }

    public List<RecordCoverageSegment> getRecordCoverage(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        LocalDateTime windowStart = item.firstAlertTime().minusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);
        LocalDateTime windowEnd = item.lastAlertTime().plusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);
        List<RecordCoverageSegment> resolved;
        try {
            resolved = deps.recordCoverageResolver.resolve(new RecordCoverageRequest(
                    item.deviceId(),
                    item.cameraId(),
                    windowStart,
                    windowEnd
            ));
        } catch (RuntimeException ex) {
            resolved = List.of(new RecordCoverageSegment(
                    RECORD_COVERAGE_MISSING,
                    windowStart,
                    windowEnd,
                    0,
                    null,
                    0,
                    recordGapMetadata("service_unavailable", "video_service_unavailable", true, ex.getMessage())
            ));
        }
        if (resolved != null && !resolved.isEmpty()) {
            return mergeCoverageWithMissingGaps(windowStart, windowEnd, resolved);
        }
        Optional<ReviewEvidenceItem> recordEvidence = deps.reviewItemStore.listTimeline(reviewItemId).stream()
                .filter(evidence -> MATERIAL_RECORD.equals(evidence.materialType()))
                .filter(evidence -> hasText(evidence.materialUri()))
                .findFirst();
        if (recordEvidence.isPresent()) {
            return List.of(new RecordCoverageSegment(RECORD_COVERAGE_AVAILABLE, windowStart, windowEnd, null,
                    recordEvidence.get().materialUri()));
        }
        String gapReason = recordGapReason(item);
        return List.of(new RecordCoverageSegment(
                RECORD_COVERAGE_MISSING,
                windowStart,
                windowEnd,
                0,
                null,
                0,
                recordGapMetadata(gapReason, gapReason, false, null)
        ));
    }

    public List<RecordCoverageSegment> getRecordCoverage(Long reviewItemId,
                                                         Long reviewCaseId,
                                                         Long operatorUserId,
                                                         List<String> allowedCameraIds) {
        List<RecordCoverageSegment> coverage = getRecordCoverage(reviewItemId);
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        if (canAttachRecordCoverageEvidence(reviewCaseId, item, operatorUserId, allowedCameraIds)) {
            ensureRecordCoverageEvidence(item, coverage);
        }
        reviewMediaScopeGuard.enforceItemMediaReadScope(
                reviewCaseId,
                reviewItemId,
                operatorUserId,
                "coverage",
                allowedCameraIds,
                "record coverage read",
                coverage.stream()
                        .map(segment -> new MediaAccessRef(MATERIAL_RECORD, segment.recordUri()))
                        .toList()
        );
        return coverage;
    }

    private boolean canAttachRecordCoverageEvidence(Long reviewCaseId,
                                                    ReviewItemAggregate item,
                                                    Long operatorUserId,
                                                    List<String> allowedCameraIds) {
        if (reviewCaseId == null || reviewCaseId <= 0) {
            return false;
        }
        List<String> effectiveAllowedCameraIds = reviewMediaScopeGuard.resolveEffectiveAllowedCameraIds(
                reviewCaseId,
                operatorUserId,
                "coverage",
                allowedCameraIds
        );
        if (effectiveAllowedCameraIds == null || !effectiveAllowedCameraIds.contains(item.cameraId())) {
            return false;
        }
        return reviewCaseManagement.getReviewCaseTimeline(reviewCaseId).stream()
                .anyMatch(row -> Objects.equals(item.id(), row.reviewItemId()));
    }

    private void ensureRecordCoverageEvidence(ReviewItemAggregate item, List<RecordCoverageSegment> coverage) {
        List<RecordCoverageSegment> segments = coverage == null ? List.of() : coverage;
        if (segments.stream().noneMatch(segment -> segment != null && hasText(segment.recordUri()))) {
            return;
        }
        Set<String> knownRecordUris = deps.reviewItemStore.listTimeline(item.id()).stream()
                .filter(evidence -> MATERIAL_RECORD.equals(evidence.materialType()))
                .map(ReviewEvidenceItem::materialUri)
                .filter(AlertReviewValues::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<ReviewEvidenceItem> evidenceItems = new ArrayList<>();
        for (RecordCoverageSegment segment : segments) {
            if (segment == null || !hasText(segment.recordUri()) || !knownRecordUris.add(segment.recordUri())) {
                continue;
            }
            evidenceItems.add(new ReviewEvidenceItem(
                    item.id(),
                    firstSourceAlertId(item),
                    MATERIAL_RECORD,
                    segment.recordUri(),
                    segment.startTime() == null ? item.firstAlertTime() : segment.startTime(),
                    segment.startTime()
            ));
        }
        if (!evidenceItems.isEmpty()) {
            deps.reviewItemStore.appendEvidence(item.id(), evidenceItems);
        }
    }

    public ReviewRecordStorageSyncResult syncRecordStorage(ReviewRecordStorageSyncCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        List<RecordCoverageSegment> coverage = command.coverageSegments() == null
                ? List.of()
                : command.coverageSegments().stream()
                        .filter(segment -> segment.startTime() != null && segment.endTime() != null)
                        .sorted(Comparator.comparing(RecordCoverageSegment::startTime))
                        .toList();
        int availableCount = 0;
        int missingCount = 0;
        int motionCount = 0;
        int availableSeconds = 0;
        int missingSeconds = 0;
        int motionSeconds = 0;
        Map<String, Integer> gapReasons = new LinkedHashMap<>();
        List<ReviewEvidenceItem> evidenceItems = new ArrayList<>();
        for (RecordCoverageSegment segment : coverage) {
            String status = normalizeCoverageStatus(segment);
            if (RECORD_COVERAGE_MISSING.equals(segment.status())) {
                status = RECORD_COVERAGE_MISSING;
            }
            int seconds = coverageSeconds(segment);
            if (RECORD_COVERAGE_MISSING.equals(status)) {
                missingCount++;
                missingSeconds += seconds;
                String gapReason = toText(segment.metadata() == null ? null : segment.metadata().get("gapReason"));
                if (hasText(gapReason)) {
                    gapReasons.merge(gapReason, seconds, Integer::sum);
                }
                continue;
            }
            availableCount++;
            availableSeconds += seconds;
            if (RECORD_COVERAGE_MOTION.equals(status)) {
                motionCount++;
                motionSeconds += seconds;
            }
            if (hasText(segment.recordUri())) {
                evidenceItems.add(new ReviewEvidenceItem(item.id(), firstSourceAlertId(item), MATERIAL_RECORD,
                        segment.recordUri(), segment.startTime(), segment.startTime()));
            }
        }
        String syncStatus = missingCount > 0 ? "partial" : (availableCount > 0 ? "complete" : "missing");
        String recordEvidenceStatus = availableCount > 0 && missingCount == 0
                ? RECORD_EVIDENCE_FOUND
                : RECORD_EVIDENCE_MISSING;
        String recordEvidenceMessage = firstRecordGapReason(gapReasons, syncStatus);
        LocalDateTime syncedAt = LocalDateTime.now();
        Map<String, Object> reviewData = new LinkedHashMap<>(item.reviewData() == null ? Map.of() : item.reviewData());
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("syncStatus", syncStatus);
        storage.put("availableSegmentCount", availableCount);
        storage.put("missingSegmentCount", missingCount);
        storage.put("motionSegmentCount", motionCount);
        storage.put("availableSeconds", availableSeconds);
        storage.put("missingSeconds", missingSeconds);
        storage.put("motionSeconds", motionSeconds);
        storage.put("gapReasons", gapReasons);
        storage.put("operatorUserId", command.operatorUserId());
        storage.put("syncedAt", syncedAt.toString());
        reviewData.put("recordStorage", immutableNonNullMap(storage));
        deps.reviewItemStore.updateReviewLifecycle(
                item.id(),
                immutableNonNullMap(reviewData),
                item.firstAlertTime(),
                item.lastAlertTime(),
                evidenceItems,
                recordEvidenceStatus,
                syncedAt,
                recordEvidenceMessage
        );
        return new ReviewRecordStorageSyncResult(
                item.id(),
                syncStatus,
                availableCount,
                missingCount,
                motionCount,
                availableSeconds,
                missingSeconds,
                motionSeconds,
                coverage,
                syncedAt,
                command.operatorUserId()
        );
    }

    static String recordGapReason(ReviewItemAggregate item) {
        if (item == null) {
            return RECORD_GAP_RECORD_NOT_FOUND;
        }
        String message = normalizeRecordGapReason(item.recordEvidenceMessage());
        if (hasText(message)) {
            return message;
        }
        if (RECORD_EVIDENCE_FAILED.equals(item.recordEvidenceStatus())) {
            return RECORD_GAP_MISSING_LOOKUP_FIELDS;
        }
        return RECORD_GAP_RECORD_NOT_FOUND;
    }

    static String normalizeRecordGapReason(String value) {
        String normalized = normalizeRecordGapReasonToken(value);
        if (!hasText(normalized)) {
            return null;
        }
        return recordGapReasonDefinition(normalized)
                .map(RecordGapReasonDefinition::code)
                .orElse(RECORD_GAP_RECORD_NOT_FOUND);
    }

    static Optional<RecordGapReasonDefinition> recordGapReasonDefinition(String value) {
        if (!hasText(value)) {
            return Optional.empty();
        }
        String normalized = normalizeRecordGapReasonToken(value);
        return RECORD_GAP_REASON_CATALOG.stream()
                .filter(definition -> definition.code().equals(normalized)
                        || definition.aliases().contains(normalized))
                .findFirst();
    }

    private static String normalizeRecordGapReasonToken(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        return hasText(normalized) ? normalized : null;
    }

    private static List<RecordCoverageSegment> mergeCoverageWithMissingGaps(LocalDateTime windowStart,
                                                                            LocalDateTime windowEnd,
                                                                            List<RecordCoverageSegment> rawSegments) {
        List<RecordCoverageSegment> normalized = rawSegments.stream()
                .filter(segment -> segment.startTime() != null && segment.endTime() != null)
                .filter(segment -> segment.endTime().isAfter(windowStart) && segment.startTime().isBefore(windowEnd))
                .map(segment -> new RecordCoverageSegment(
                        normalizeCoverageStatus(segment),
                        max(windowStart, segment.startTime()),
                        min(windowEnd, segment.endTime()),
                        segment.motion(),
                        segment.recordUri(),
                        segment.objects(),
                        segment.metadata() == null ? Map.of() : segment.metadata()
                ))
                .filter(segment -> segment.endTime().isAfter(segment.startTime()))
                .sorted(Comparator.comparing(RecordCoverageSegment::startTime))
                .toList();
        if (normalized.isEmpty()) {
            return List.of(recordMissingSegment(windowStart, windowEnd, "record_not_found", "record_window_empty", false));
        }
        List<RecordCoverageSegment> result = new ArrayList<>();
        LocalDateTime cursor = windowStart;
        for (RecordCoverageSegment segment : normalized) {
            if (segment.startTime().isAfter(cursor)) {
                result.add(recordMissingSegment(cursor, segment.startTime(), "stream_interrupted", "coverage_gap", true));
            }
            result.add(segment);
            if (segment.endTime().isAfter(cursor)) {
                cursor = segment.endTime();
            }
        }
        if (cursor.isBefore(windowEnd)) {
            result.add(recordMissingSegment(cursor, windowEnd, "stream_interrupted", "coverage_gap", true));
        }
        return List.copyOf(result);
    }

    static String normalizeCoverageStatus(RecordCoverageSegment segment) {
        if (RECORD_COVERAGE_MISSING.equals(segment.status())) {
            return RECORD_COVERAGE_MISSING;
        }
        if (RECORD_COVERAGE_MOTION.equals(segment.status())) {
            return RECORD_COVERAGE_MOTION;
        }
        if (segment.motion() != null && segment.motion() > 0) {
            return RECORD_COVERAGE_MOTION;
        }
        return RECORD_COVERAGE_AVAILABLE;
    }

    private static RecordCoverageSegment recordMissingSegment(LocalDateTime startTime,
                                                              LocalDateTime endTime,
                                                              String gapReason,
                                                              String reasonCode,
                                                              boolean retryable) {
        return new RecordCoverageSegment(
                RECORD_COVERAGE_MISSING,
                startTime,
                endTime,
                0,
                null,
                0,
                recordGapMetadata(gapReason, reasonCode, retryable, null)
        );
    }

    private static Map<String, Object> recordGapMetadata(String gapReason,
                                                         String reasonCode,
                                                         boolean retryable,
                                                         String detail) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("gapReason", gapReason);
        metadata.put("reasonCode", reasonCode);
        metadata.put("retryable", retryable);
        metadata.put("category", recordGapCategory(gapReason));
        metadata.put("detail", detail);
        return immutableNonNullMap(metadata);
    }

    private static String firstRecordGapReason(Map<String, Integer> gapReasons, String syncStatus) {
        if (gapReasons != null) {
            for (String reason : gapReasons.keySet()) {
                if (hasText(reason)) {
                    return normalizeRecordGapReason(reason);
                }
            }
        }
        return "record_storage_sync:" + syncStatus;
    }

    private static String recordGapCategory(String gapReason) {
        return recordGapReasonDefinition(gapReason)
                .map(RecordGapReasonDefinition::category)
                .orElse("unknown");
    }

    private static int coverageSeconds(RecordCoverageSegment segment) {
        if (segment == null || segment.startTime() == null || segment.endTime() == null
                || !segment.endTime().isAfter(segment.startTime())) {
            return 0;
        }
        long seconds = Duration.between(segment.startTime(), segment.endTime()).getSeconds();
        return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
    }
}
