package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.AlertReviewDataSchemaValidator;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_SNAPSHOT;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.collectStringValues;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstNonNull;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.matchesText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.normalizeStringKeyMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.objectIds;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Hex;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toInteger;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLocalDateTime;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.zones;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRuleGovernance.normalizeZoneInertiaFrames;

/**
 * 告警线索归并：线索去重与身份键、复核片段的创建/合并/切分、同摄像头并发锁、证据收集与录像证据解析。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class AlertClueIngestion {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;

    AlertClueIngestion(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
    }

    static final int DEFAULT_MERGE_WINDOW_SECONDS = 300;
    private static final int MAX_SEGMENT_CONFLICT_ATTEMPTS = 3;
    static final int REVIEW_DATA_VERSION = 1;
    static final AlertReviewDataSchemaValidator REVIEW_DATA_SCHEMA_VALIDATOR =
            AlertReviewDataSchemaValidator.loadV1();
    private static final String FALLBACK_RULE_CODE = SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING;
    private final ConcurrentMap<String, Object> reviewSegmentIngestLocks = new ConcurrentHashMap<>();

    public ReviewItemAggregate ingestClue(AlertClueCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.sourceSystem(), "sourceSystem");
        requireText(command.sourceAlertId(), "sourceAlertId");
        LocalDateTime alertTime = Objects.requireNonNull(command.alertTime(), "alertTime");
        String cameraId = normalizeCameraId(command);
        List<String> identityKeys = ingestIdentityKeys(command);
        List<String> transactionIdentityKeys = new ArrayList<>(identityKeys);
        transactionIdentityKeys.add(command.sourceSystem() + ":alert:" + command.sourceAlertId());
        synchronized (reviewSegmentIngestLock(cameraId)) {
            deps.reviewItemStore.acquireReviewSegmentTransactionLocks(
                    cameraId,
                    command.sourceSystem(),
                    List.copyOf(new LinkedHashSet<>(transactionIdentityKeys))
            );
            return ingestClueLocked(command, alertTime, cameraId, identityKeys);
        }
    }

    private ReviewItemAggregate ingestClueLocked(AlertClueCommand command,
                                                 LocalDateTime alertTime,
                                                 String cameraId,
                                                 List<String> identityKeys) {
        String ruleCode = resolveRuleCode(command);
        Optional<ReviewItemAggregate> existingIdentity = findExistingIngestIdentity(command, identityKeys);
        if (existingIdentity.isPresent()) {
            return reviewItemQuery.withEventProjection(existingIdentity.get());
        }
        EvidenceBuildResult evidenceResult = buildEvidenceItems(command);
        Map<String, Object> reviewData = withReviewWindow(buildReviewData(command, ruleCode), alertTime, alertTime);
        LocalDateTime windowStart = alertTime.minusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);
        LocalDateTime windowEnd = alertTime.plusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);

        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_SEGMENT_CONFLICT_ATTEMPTS; attempt++) {
            Optional<ReviewItemAggregate> retryIdentity = findExistingIngestIdentity(command, identityKeys);
            if (retryIdentity.isPresent()) {
                return reviewItemQuery.withEventProjection(retryIdentity.get());
            }
            try {
                Optional<ReviewItemAggregate> candidate = deps.reviewItemStore.findMergeCandidate(
                                command.sourceSystem(),
                                cameraId,
                                command.zoneCode(),
                                ruleCode,
                                windowStart,
                                windowEnd
                        )
                        .filter(AlertClueIngestion::canMergeReviewSegment);
                if (candidate.isPresent()) {
                    ReviewItemAggregate current = candidate.get();
                    String recordEvidenceStatus = mergeRecordEvidenceStatus(
                            current.recordEvidenceStatus(),
                            evidenceResult.recordEvidenceStatus()
                    );
                    return deps.reviewItemStore.appendClue(current.id(),
                            command.sourceAlertId(),
                            alertTime,
                            evidenceResult.evidenceItems(),
                            withReviewWindow(
                                    mergeReviewData(current.reviewData(), reviewData),
                                    min(current.firstAlertTime(), alertTime),
                                    max(current.lastAlertTime(), alertTime)
                            ),
                            recordEvidenceStatus,
                            evidenceResult.recordEvidenceCheckedAt() == null
                                    ? current.recordEvidenceCheckedAt()
                                    : evidenceResult.recordEvidenceCheckedAt(),
                            hasText(evidenceResult.recordEvidenceMessage())
                                    ? evidenceResult.recordEvidenceMessage()
                                    : current.recordEvidenceMessage());
                }

                endPreviousOpenSegmentBeforeSplit(cameraId, alertTime);
                return deps.reviewItemStore.create(new ReviewItemDraft(
                        command.sourceSystem(),
                        command.sourceAlertId(),
                        ruleCode,
                        command.sourceAlertType(),
                        alertTime,
                        command.deviceId(),
                        cameraId,
                        command.zoneCode(),
                        command.objectLabel(),
                        command.sourcePayloadHash(),
                        reviewData,
                        evidenceResult.recordEvidenceStatus(),
                        evidenceResult.recordEvidenceCheckedAt(),
                        evidenceResult.recordEvidenceMessage()
                ), evidenceResult.evidenceItems());
            } catch (RuntimeException ex) {
                if (!isRetriableReviewSegmentConflict(ex)) {
                    throw ex;
                }
                Optional<ReviewItemAggregate> concurrentWinner = findExistingIngestIdentity(command, identityKeys);
                if (concurrentWinner.isPresent()) {
                    return reviewItemQuery.withEventProjection(concurrentWinner.get());
                }
                lastConflict = ex;
                if (attempt == MAX_SEGMENT_CONFLICT_ATTEMPTS) {
                    throw ex;
                }
            }
        }
        throw lastConflict == null
                ? new IllegalStateException("review segment ingest retry exhausted")
                : lastConflict;
    }

    private Object reviewSegmentIngestLock(String cameraId) {
        return reviewSegmentIngestLocks.computeIfAbsent(firstText(cameraId, "__missing_camera__"), key -> new Object());
    }

    private void endPreviousOpenSegmentBeforeSplit(String cameraId, LocalDateTime newSegmentStart) {
        Optional<ReviewItemAggregate> openSegment = deps.reviewItemStore.findLatestOpenReviewSegment(cameraId)
                .or(() -> deps.reviewItemStore.listWorkbench(null).stream()
                        .filter(item -> Objects.equals(cameraId, item.cameraId()))
                        .filter(AlertClueIngestion::canMergeReviewSegment)
                        .max(Comparator.comparing(
                                item -> firstNonNull(item.lastAlertTime(), item.firstAlertTime()),
                                Comparator.nullsFirst(Comparator.naturalOrder())
                        )));
        if (openSegment.isEmpty()) {
            return;
        }

        ReviewItemAggregate item = openSegment.get();
        Map<String, Object> reviewData = new LinkedHashMap<>(item.reviewData() == null ? Map.of() : item.reviewData());
        Map<String, Object> segment = new LinkedHashMap<>(toStringObjectMap(reviewData.get("reviewSegment")));
        LocalDateTime segmentStart = toLocalDateTime(firstText(
                segment.get("startTime"),
                item.firstAlertTime() == null ? null : item.firstAlertTime().toString()
        ));
        if (segmentStart != null && newSegmentStart.isBefore(segmentStart)) {
            throw new IllegalStateException("out-of-order clue overlaps open review segment for camera " + cameraId);
        }
        LocalDateTime lastSeen = firstNonNull(
                item.lastAlertTime(),
                toLocalDateTime(segment.get("endTime")),
                segmentStart
        );
        LocalDateTime cutoff = lastSeen == null
                ? newSegmentStart
                : min(newSegmentStart, lastSeen.plusSeconds(DEFAULT_MERGE_WINDOW_SECONDS));
        if (segmentStart != null && cutoff.isBefore(segmentStart)) {
            throw new IllegalStateException("review segment cutoff is before segment start for camera " + cameraId);
        }

        List<Map<String, Object>> events = new ArrayList<>(toMapList(segment.get("events")));
        events.add(Map.of(
                "event", "ended",
                "happenedAt", cutoff.toString(),
                "reason", "merge_window_cutoff"
        ));
        segment.put("status", "ended");
        segment.put("endTime", cutoff.toString());
        segment.put("events", List.copyOf(events));
        reviewData.put("reviewSegment", immutableNonNullMap(segment));

        Map<String, Object> lifecycle = new LinkedHashMap<>(toStringObjectMap(reviewData.get("lifecycle")));
        lifecycle.put("state", "ended");
        lifecycle.put("lastSeenAt", cutoff.toString());
        lifecycle.put("endedAt", cutoff.toString());
        lifecycle.put("cutoffWindowSeconds", DEFAULT_MERGE_WINDOW_SECONDS);
        reviewData.put("lifecycle", immutableNonNullMap(lifecycle));
        deps.reviewItemStore.updateReviewLifecycle(
                item.id(),
                immutableNonNullMap(reviewData),
                item.firstAlertTime(),
                item.lastAlertTime(),
                List.of(),
                item.recordEvidenceStatus(),
                item.recordEvidenceCheckedAt(),
                item.recordEvidenceMessage()
        );
    }

    private static boolean isRetriableReviewSegmentConflict(RuntimeException exception) {
        if (exception instanceof DuplicateKeyException) {
            return true;
        }
        if (!(exception instanceof DataIntegrityViolationException)) {
            return false;
        }
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23P01".equalsIgnoreCase(sqlException.getSQLState())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private String resolveRuleCode(AlertClueCommand command) {
        if (hasText(command.ruleCode())) {
            return command.ruleCode();
        }
        return deps.reviewRuleStore.listEnabled().stream()
                .filter(rule -> matchesRule(rule, command))
                .map(ReviewRuleView::ruleCode)
                .findFirst()
                .orElse(FALLBACK_RULE_CODE);
    }

    private static boolean matchesRule(ReviewRuleView rule, AlertClueCommand command) {
        return matchesText(rule.sourceSystem(), command.sourceSystem())
                && matchesText(rule.cameraId(), normalizeCameraId(command))
                && matchesText(rule.zoneCode(), command.zoneCode())
                && matchesText(rule.objectLabel(), command.objectLabel())
                && matchesStaySeconds(rule.minStaySeconds(), command.staySeconds())
                && matchesZoneInertia(rule.inertiaFrames(), command.motionMetadata())
                && matchesActiveWindow(rule, command.alertTime());
    }

    private static boolean matchesStaySeconds(Integer minStaySeconds, Integer actualStaySeconds) {
        if (minStaySeconds == null || minStaySeconds <= 0) {
            return true;
        }
        return actualStaySeconds != null && actualStaySeconds >= minStaySeconds;
    }

    private static boolean matchesZoneInertia(Integer inertiaFrames, Map<String, Object> motionMetadata) {
        int requiredFrames = normalizeZoneInertiaFrames(inertiaFrames);
        if (requiredFrames <= 1) {
            return true;
        }
        Integer observedFrames = toInteger(firstNonNull(
                motionMetadata == null ? null : motionMetadata.get("consecutiveZoneFrames"),
                motionMetadata == null ? null : motionMetadata.get("consecutive_zone_frames")
        ));
        return observedFrames != null && observedFrames >= requiredFrames;
    }

    private static boolean matchesActiveWindow(ReviewRuleView rule, LocalDateTime alertTime) {
        if (alertTime == null) {
            return false;
        }
        if (rule.activeStart() != null && alertTime.isBefore(rule.activeStart())) {
            return false;
        }
        return rule.activeEnd() == null || !alertTime.isAfter(rule.activeEnd());
    }

    private EvidenceBuildResult buildEvidenceItems(AlertClueCommand command) {
        List<ReviewEvidenceItem> evidenceItems = new ArrayList<>(2);
        if (hasText(command.snapshotUri())) {
            evidenceItems.add(new ReviewEvidenceItem(
                    null,
                    command.sourceAlertId(),
                    MATERIAL_SNAPSHOT,
                    command.snapshotUri(),
                    command.alertTime()
            ));
        }
        if (hasText(command.recordUri())) {
            evidenceItems.add(new ReviewEvidenceItem(
                    null,
                    command.sourceAlertId(),
                    MATERIAL_RECORD,
                    command.recordUri(),
                    command.alertTime()
            ));
            return new EvidenceBuildResult(
                    List.copyOf(evidenceItems),
                    RECORD_EVIDENCE_FOUND,
                    LocalDateTime.now(),
                    "source_record_uri"
            );
        }
        RecordEvidenceAttempt attempt = resolveRecordEvidence(
                command.sourceAlertId(),
                command.deviceId(),
                normalizeCameraId(command),
                command.alertTime()
        );
        attempt.evidenceItem().ifPresent(evidenceItems::add);
        return new EvidenceBuildResult(
                List.copyOf(evidenceItems),
                attempt.recordEvidenceStatus(),
                attempt.recordEvidenceCheckedAt(),
                attempt.recordEvidenceMessage()
        );
    }

    RecordEvidenceAttempt resolveRecordEvidence(String sourceAlertId,
                                                        String deviceId,
                                                        String cameraId,
                                                        LocalDateTime alertTime) {
        LocalDateTime checkedAt = LocalDateTime.now();
        if (!hasText(sourceAlertId) || (!hasText(deviceId) && !hasText(cameraId)) || alertTime == null) {
            return new RecordEvidenceAttempt(Optional.empty(), RECORD_EVIDENCE_FAILED, checkedAt, "missing_lookup_fields");
        }
        try {
            Optional<RecordEvidenceResult> resolved = deps.recordEvidenceResolver.resolve(new RecordEvidenceRequest(
                    sourceAlertId,
                    deviceId,
                    cameraId,
                    alertTime
            ));
            if (resolved.isPresent() && hasText(resolved.get().recordUri())) {
                ReviewEvidenceItem evidenceItem = new ReviewEvidenceItem(
                        null,
                        sourceAlertId,
                        MATERIAL_RECORD,
                        resolved.get().recordUri(),
                        alertTime,
                        resolved.get().recordStartTime()
                );
                return new RecordEvidenceAttempt(
                        Optional.of(evidenceItem),
                        RECORD_EVIDENCE_FOUND,
                        checkedAt,
                        resolved.get().message()
                );
            }
            return new RecordEvidenceAttempt(
                    Optional.empty(),
                    RECORD_EVIDENCE_MISSING,
                    checkedAt,
                    deps.recordEvidenceResolver.unavailableReason().orElse(RECORD_GAP_RECORD_NOT_FOUND)
            );
        } catch (RuntimeException ex) {
            return new RecordEvidenceAttempt(Optional.empty(), RECORD_EVIDENCE_FAILED, checkedAt, ex.getMessage());
        }
    }

    private Optional<ReviewItemAggregate> findExistingIngestIdentity(AlertClueCommand command,
                                                                    List<String> identityKeys) {
        if ((identityKeys == null || identityKeys.isEmpty()) && !hasText(command.sourceAlertId())) {
            return Optional.empty();
        }
        return deps.reviewItemStore.findByIngestIdentity(command.sourceSystem(), command.sourceAlertId(), identityKeys);
    }

    private static Map<String, Object> buildReviewData(AlertClueCommand command, String ruleCode) {
        Map<String, Object> reviewData = new LinkedHashMap<>();
        List<String> labels = nonEmpty(command.labels(), command.objectLabel());
        List<String> zones = nonEmpty(command.zones(), command.zoneCode());
        List<String> objectIds = command.objectIds() == null ? List.of() : List.copyOf(command.objectIds());
        reviewData.put("reviewDataVersion", REVIEW_DATA_VERSION);
        reviewData.put("ingestIdentityKeys", ingestIdentityKeys(command));
        reviewData.put("aggregation", buildAggregationPolicy(command));
        reviewData.put("labels", labels);
        reviewData.put("zones", zones);
        reviewData.put("objectIds", objectIds);
        reviewData.put("objects", buildReviewObjects(labels, objectIds, command.confidence(), command.bbox()));
        reviewData.put("detections", List.of(buildReviewDetection(command, ruleCode, labels, zones, objectIds)));
        reviewData.put("reviewSegment", buildInitialReviewSegment(command, ruleCode, labels, zones, objectIds));
        reviewData.put("verifiedObjects", command.verifiedObjects() == null ? List.of() : List.copyOf(command.verifiedObjects()));
        reviewData.put("audio", Map.of("labels", command.audioLabels() == null ? List.of() : List.copyOf(command.audioLabels())));
        reviewData.put("motion", command.motionMetadata() == null ? Map.of() : Map.copyOf(command.motionMetadata()));
        if (command.confidence() != null) {
            reviewData.put("confidence", command.confidence());
        }
        if (command.bbox() != null && !command.bbox().isEmpty()) {
            reviewData.put("bbox", List.copyOf(command.bbox()));
        }
        if (command.staySeconds() != null) {
            reviewData.put("staySeconds", command.staySeconds());
        }
        if (hasText(command.correlationId())) {
            reviewData.put("correlationId", command.correlationId());
        }
        if (hasText(command.sourcePayloadHash())) {
            reviewData.put("sourcePayloadHash", command.sourcePayloadHash());
        }
        if (command.thumbTime() != null) {
            reviewData.put("thumbTime", command.thumbTime().toString());
        }
        Map<String, Object> result = Map.copyOf(reviewData);
        AlertReviewDataSchemaValidator.ValidationResult validation = REVIEW_DATA_SCHEMA_VALIDATOR.validate(result);
        if (!validation.valid()) {
            throw new IllegalArgumentException("reviewData schema validation failed: "
                    + String.join(",", validation.violations()));
        }
        return result;
    }

    private static List<String> ingestIdentityKeys(AlertClueCommand command) {
        Set<String> keys = new LinkedHashSet<>();
        if (hasText(command.sourcePayloadHash())) {
            keys.add(command.sourceSystem() + ":payload:" + command.sourcePayloadHash());
        } else if (hasText(command.sourceAlertId())) {
            keys.add(command.sourceSystem() + ":alert:" + command.sourceAlertId());
        }
        return List.copyOf(keys);
    }

    private static Map<String, Object> buildAggregationPolicy(AlertClueCommand command) {
        Map<String, Object> aggregation = new LinkedHashMap<>();
        aggregation.put("aggregationKey", command.sourceSystem() + "|" + normalizeCameraId(command));
        aggregation.put("mergePolicy", "same_camera_sliding_window");
        aggregation.put("mergeWindowSeconds", DEFAULT_MERGE_WINDOW_SECONDS);
        aggregation.put("splitPolicy", "new_item_when_gap_exceeds_merge_window_or_existing_not_pending");
        return Map.copyOf(aggregation);
    }

    private static Map<String, Object> buildInitialReviewSegment(AlertClueCommand command,
                                                                 String ruleCode,
                                                                 List<String> labels,
                                                                 List<String> zones,
                                                                 List<String> objectIds) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", "start");
        event.put("happenedAt", command.alertTime() == null ? null : command.alertTime().toString());
        event.put("sourceAlertId", command.sourceAlertId());
        event.put("ruleCode", ruleCode);
        event.put("objectIds", objectIds);
        event.put("labels", labels);
        event.put("zones", zones);
        if (command.bbox() != null && !command.bbox().isEmpty()) {
            event.put("bbox", List.copyOf(command.bbox()));
        }
        if (command.confidence() != null) {
            event.put("confidence", command.confidence());
        }
        Map<String, Object> segment = new LinkedHashMap<>();
        String severity = reviewSegmentSeverity(command.sourceAlertType());
        segment.put("segmentId", buildReviewSegmentId(command));
        segment.put("cameraId", normalizeCameraId(command));
        segment.put("severity", severity);
        segment.put("status", "alert".equals(severity) ? "alert" : "active");
        segment.put("startTime", command.alertTime() == null ? null : command.alertTime().toString());
        segment.put("endTime", command.alertTime() == null ? null : command.alertTime().toString());
        segment.put("objectIds", objectIds);
        segment.put("zones", zones);
        segment.put("sourceAlertIds", hasText(command.sourceAlertId()) ? List.of(command.sourceAlertId()) : List.of());
        segment.put("cutoffWindowSeconds", DEFAULT_MERGE_WINDOW_SECONDS);
        segment.put("events", List.of(immutableNonNullMap(event)));
        return immutableNonNullMap(segment);
    }

    private static Map<String, Object> mergeReviewSegment(Map<String, Object> current, Map<String, Object> incoming) {
        Map<String, Object> currentSegment = toStringObjectMap(current == null ? null : current.get("reviewSegment"));
        Map<String, Object> incomingSegment = toStringObjectMap(incoming == null ? null : incoming.get("reviewSegment"));
        if (currentSegment.isEmpty()) {
            return immutableNonNullMap(incomingSegment);
        }
        if (incomingSegment.isEmpty()) {
            return immutableNonNullMap(currentSegment);
        }
        List<Map<String, Object>> events = new ArrayList<>(toMapList(currentSegment.get("events")));
        for (Map<String, Object> incomingEvent : toMapList(incomingSegment.get("events"))) {
            Map<String, Object> event = new LinkedHashMap<>(incomingEvent);
            event.put("event", "update");
            events.add(immutableNonNullMap(event));
        }
        Map<String, Object> merged = new LinkedHashMap<>(currentSegment);
        merged.put("severity", mergeSeverity(toText(currentSegment.get("severity")), toText(incomingSegment.get("severity"))));
        merged.put("status", mergeReviewSegmentStatus(toText(currentSegment.get("status")), toText(incomingSegment.get("status"))));
        merged.put("startTime", minTextTime(currentSegment.get("startTime"), incomingSegment.get("startTime")));
        merged.put("endTime", maxTextTime(currentSegment.get("endTime"), incomingSegment.get("endTime")));
        merged.put("objectIds", mergeStringValues(currentSegment.get("objectIds"), toStringList(incomingSegment.get("objectIds"), null)));
        merged.put("zones", mergeStringValues(currentSegment.get("zones"), toStringList(incomingSegment.get("zones"), null)));
        merged.put("sourceAlertIds", mergeSourceAlertIdsByEventTime(currentSegment, incomingSegment, events));
        merged.put("events", List.copyOf(events));
        return immutableNonNullMap(merged);
    }

    private static List<String> mergeSourceAlertIdsByEventTime(Map<String, Object> currentSegment,
                                                               Map<String, Object> incomingSegment,
                                                               List<Map<String, Object>> events) {
        List<String> mergedIds = mergeStringValues(
                currentSegment.get("sourceAlertIds"),
                toStringList(incomingSegment.get("sourceAlertIds"), null)
        );
        Map<String, Integer> originalOrder = new LinkedHashMap<>();
        for (int i = 0; i < mergedIds.size(); i++) {
            originalOrder.putIfAbsent(mergedIds.get(i), i);
        }
        Map<String, LocalDateTime> happenedAtById = new LinkedHashMap<>();
        for (Map<String, Object> event : events) {
            String sourceAlertId = firstText(event.get("sourceAlertId"), null);
            LocalDateTime happenedAt = toLocalDateTime(event.get("happenedAt"));
            if (hasText(sourceAlertId) && happenedAt != null) {
                happenedAtById.merge(sourceAlertId, happenedAt, AlertReviewValues::min);
            }
        }
        return mergedIds.stream()
                .sorted(Comparator
                        .comparing((String id) -> happenedAtById.get(id), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(id -> originalOrder.getOrDefault(id, Integer.MAX_VALUE)))
                .toList();
    }

    private static boolean canMergeReviewSegment(ReviewItemAggregate candidate) {
        Map<String, Object> segment = toStringObjectMap(candidate.reviewData() == null
                ? null
                : candidate.reviewData().get("reviewSegment"));
        return !"ended".equals(firstText(segment.get("status"), "active"));
    }

    private static String buildReviewSegmentId(AlertClueCommand command) {
        return buildReviewSegmentId(
                normalizeCameraId(command),
                command.alertTime(),
                command.sourceSystem(),
                command.sourceAlertId()
        );
    }

    static String buildRecoveryReviewSegmentId(ReviewItemAggregate item) {
        String identity = firstText(
                item.reviewItemNo(),
                firstText(item.id(), "unknown-review-item")
        );
        return "seg-" + sha256Hex(identity.length() + ":" + identity);
    }

    private static String buildReviewSegmentId(String cameraId,
                                               LocalDateTime startTime,
                                               String sourceSystem,
                                               String sourceAlertId) {
        List<String> identityParts = List.of(
                firstText(cameraId, "unknown-camera"),
                startTime == null ? "unknown-time" : startTime.toString(),
                firstText(sourceSystem, "unknown-source"),
                firstText(sourceAlertId, "unknown-alert")
        );
        StringBuilder identity = new StringBuilder();
        for (String part : identityParts) {
            identity.append(part.length()).append(':').append(part);
        }
        return "seg-" + sha256Hex(identity.toString());
    }

    static String reviewSegmentSeverity(String sourceAlertType) {
        String type = toText(sourceAlertType).toLowerCase();
        if (type.contains("detect") || type.contains("motion")) {
            return "detection";
        }
        return "alert";
    }

    static String mergeSeverity(String current, String incoming) {
        if ("alert".equals(current) || "alert".equals(incoming)) {
            return "alert";
        }
        return hasText(incoming) ? incoming : firstText(current, "detection");
    }

    private static String minTextTime(Object left, Object right) {
        LocalDateTime leftTime = toLocalDateTime(left);
        LocalDateTime rightTime = toLocalDateTime(right);
        if (leftTime == null) {
            return rightTime == null ? null : rightTime.toString();
        }
        if (rightTime == null) {
            return leftTime.toString();
        }
        return min(leftTime, rightTime).toString();
    }

    private static String maxTextTime(Object left, Object right) {
        LocalDateTime leftTime = toLocalDateTime(left);
        LocalDateTime rightTime = toLocalDateTime(right);
        if (leftTime == null) {
            return rightTime == null ? null : rightTime.toString();
        }
        if (rightTime == null) {
            return leftTime.toString();
        }
        return max(leftTime, rightTime).toString();
    }

    private static Map<String, Object> mergeReviewData(Map<String, Object> current, Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.putAll(current == null ? Map.of() : current);
        merged.put("reviewDataVersion", REVIEW_DATA_VERSION);
        merged.put("ingestIdentityKeys", mergeStringLists(current, incoming, "ingestIdentityKeys"));
        merged.put("labels", mergeStringLists(current, incoming, "labels"));
        merged.put("zones", mergeStringLists(current, incoming, "zones"));
        merged.put("objectIds", mergeStringLists(current, incoming, "objectIds"));
        merged.put("verifiedObjects", mergeStringLists(current, incoming, "verifiedObjects"));
        merged.put("objects", mergeMapLists(current, incoming, "objects"));
        merged.put("detections", mergeMapLists(current, incoming, "detections"));
        merged.put("reviewSegment", mergeReviewSegment(current, incoming));
        copyIfPresent(merged, current, incoming, "aggregation");
        copyIfPresent(merged, current, incoming, "audio");
        copyIfPresent(merged, current, incoming, "motion");
        copyIfPresent(merged, current, incoming, "thumbTime");
        copyIfPresent(merged, current, incoming, "confidence");
        copyIfPresent(merged, current, incoming, "bbox");
        copyIfPresent(merged, current, incoming, "staySeconds");
        copyIfPresent(merged, current, incoming, "correlationId");
        copyIfPresent(merged, current, incoming, "sourcePayloadHash");
        return Map.copyOf(merged);
    }

    static String mergeReviewSegmentStatus(String current, String incoming) {
        List<String> priority = List.of("active", "detection", "alert", "ended");
        String currentState = firstText(current, "active");
        String incomingState = firstText(incoming, "active");
        int currentPriority = priority.indexOf(currentState);
        int incomingPriority = priority.indexOf(incomingState);
        if (currentPriority < 0) {
            currentPriority = 0;
        }
        if (incomingPriority < 0) {
            incomingPriority = 0;
        }
        return incomingPriority >= currentPriority ? incomingState : currentState;
    }

    static Map<String, Object> withReviewWindow(Map<String, Object> reviewData,
                                                        LocalDateTime startTime,
                                                        LocalDateTime endTime) {
        Map<String, Object> enriched = new LinkedHashMap<>(reviewData == null ? Map.of() : reviewData);
        if (startTime != null) {
            enriched.put("startTime", startTime.toString());
        }
        if (endTime != null) {
            enriched.put("endTime", endTime.toString());
        }
        Map<String, Object> reviewSegment = toStringObjectMap(enriched.get("reviewSegment"));
        if (!reviewSegment.isEmpty()) {
            Map<String, Object> updatedSegment = new LinkedHashMap<>(reviewSegment);
            if (startTime != null) {
                updatedSegment.put("startTime", startTime.toString());
            }
            if (endTime != null) {
                updatedSegment.put("endTime", endTime.toString());
            }
            enriched.put("reviewSegment", immutableNonNullMap(updatedSegment));
        }
        return Map.copyOf(enriched);
    }

    private static String mergeRecordEvidenceStatus(String currentStatus, String incomingStatus) {
        if (RECORD_EVIDENCE_MISSING.equals(currentStatus) || RECORD_EVIDENCE_MISSING.equals(incomingStatus)) {
            return RECORD_EVIDENCE_MISSING;
        }
        if (RECORD_EVIDENCE_FAILED.equals(currentStatus) || RECORD_EVIDENCE_FAILED.equals(incomingStatus)) {
            return RECORD_EVIDENCE_FAILED;
        }
        if (RECORD_EVIDENCE_FOUND.equals(currentStatus) || RECORD_EVIDENCE_FOUND.equals(incomingStatus)) {
            return RECORD_EVIDENCE_FOUND;
        }
        if (RECORD_EVIDENCE_NOT_REQUIRED.equals(currentStatus) || RECORD_EVIDENCE_NOT_REQUIRED.equals(incomingStatus)) {
            return RECORD_EVIDENCE_NOT_REQUIRED;
        }
        return hasText(incomingStatus) ? incomingStatus : currentStatus;
    }

    static List<Map<String, Object>> buildReviewObjects(List<String> labels,
                                                                List<String> objectIds,
                                                                Double confidence,
                                                                List<Double> bbox) {
        int count = Math.max(labels.size(), objectIds.size());
        if (count == 0) {
            return List.of();
        }
        List<Map<String, Object>> objects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, Object> object = new LinkedHashMap<>();
            if (i < objectIds.size()) {
                object.put("id", objectIds.get(i));
            }
            if (i < labels.size()) {
                object.put("label", labels.get(i));
            }
            if (confidence != null) {
                object.put("confidence", confidence);
            }
            if (bbox != null && !bbox.isEmpty()) {
                object.put("bbox", List.copyOf(bbox));
            }
            objects.add(Map.copyOf(object));
        }
        return List.copyOf(objects);
    }

    private static Map<String, Object> buildReviewDetection(AlertClueCommand command,
                                                            String ruleCode,
                                                            List<String> labels,
                                                            List<String> zones,
                                                            List<String> objectIds) {
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("sourceAlertId", command.sourceAlertId());
        detection.put("ruleCode", ruleCode);
        detection.put("alertTime", command.alertTime().toString());
        detection.put("cameraId", normalizeCameraId(command));
        detection.put("labels", labels);
        detection.put("zones", zones);
        detection.put("objectIds", objectIds);
        putIfPresent(detection, "sourceAlertType", command.sourceAlertType());
        putIfPresent(detection, "deviceId", command.deviceId());
        putIfPresent(detection, "zoneCode", command.zoneCode());
        putIfPresent(detection, "objectLabel", command.objectLabel());
        if (command.confidence() != null) {
            detection.put("confidence", command.confidence());
        }
        if (command.staySeconds() != null) {
            detection.put("staySeconds", command.staySeconds());
        }
        if (command.bbox() != null && !command.bbox().isEmpty()) {
            detection.put("bbox", List.copyOf(command.bbox()));
        }
        if (hasText(command.snapshotUri())) {
            detection.put("snapshotUri", command.snapshotUri());
        }
        if (hasText(command.recordUri())) {
            detection.put("recordUri", command.recordUri());
        }
        return Map.copyOf(detection);
    }

    private static List<String> nonEmpty(List<String> configured, String fallback) {
        if (configured != null && !configured.isEmpty()) {
            return List.copyOf(configured);
        }
        if (hasText(fallback)) {
            return List.of(fallback);
        }
        return List.of();
    }

    private static List<String> mergeStringLists(Map<String, Object> current,
                                                 Map<String, Object> incoming,
                                                 String key) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, current == null ? null : current.get(key));
        collectStringValues(values, incoming == null ? null : incoming.get(key));
        return List.copyOf(values);
    }

    static List<String> mergeStringValues(Object current, List<String> incoming) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, current);
        collectStringValues(values, incoming);
        return List.copyOf(values);
    }

    private static List<Map<String, Object>> mergeMapLists(Map<String, Object> current,
                                                           Map<String, Object> incoming,
                                                           String key) {
        Map<String, Map<String, Object>> values = new LinkedHashMap<>();
        collectMapValues(values, current == null ? null : current.get(key));
        collectMapValues(values, incoming == null ? null : incoming.get(key));
        return List.copyOf(values.values());
    }

    private static void collectMapValues(Map<String, Map<String, Object>> values, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> normalized = normalizeStringKeyMap(map);
                    values.putIfAbsent(mapIdentity(normalized), normalized);
                }
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = normalizeStringKeyMap(map);
            values.putIfAbsent(mapIdentity(normalized), normalized);
        }
    }

    private static void putIfPresent(Map<String, Object> values, String key, Object value) {
        if (value != null) {
            values.put(key, value);
        }
    }

    private static String mapIdentity(Map<String, Object> value) {
        Object sourceAlertId = value.get("sourceAlertId");
        if (sourceAlertId != null && hasText(String.valueOf(sourceAlertId))) {
            return "alert:" + sourceAlertId;
        }
        Object id = value.get("id");
        if (id != null && hasText(String.valueOf(id))) {
            return "object:" + id;
        }
        return "value:" + value;
    }

    private static void copyIfPresent(Map<String, Object> merged,
                                      Map<String, Object> current,
                                      Map<String, Object> incoming,
                                      String key) {
        Object incomingValue = incoming == null ? null : incoming.get(key);
        if (incomingValue != null) {
            merged.put(key, incomingValue);
            return;
        }
        Object currentValue = current == null ? null : current.get(key);
        if (currentValue != null) {
            merged.put(key, currentValue);
        }
    }

    private static String normalizeCameraId(AlertClueCommand command) {
        if (hasText(command.cameraId())) {
            return command.cameraId();
        }
        return command.deviceId();
    }

    private record EvidenceBuildResult(List<ReviewEvidenceItem> evidenceItems,
                                       String recordEvidenceStatus,
                                       LocalDateTime recordEvidenceCheckedAt,
                                       String recordEvidenceMessage) {
    }

    record RecordEvidenceAttempt(Optional<ReviewEvidenceItem> evidenceItem,
                                         String recordEvidenceStatus,
                                         LocalDateTime recordEvidenceCheckedAt,
                                         String recordEvidenceMessage) {
    }
}
