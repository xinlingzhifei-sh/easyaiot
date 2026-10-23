package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.RecordEvidenceAttempt;
import com.basiclab.iot.system.service.supervision.alertreview.ReviewMediaScopeGuard.MediaAccessRef;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.DEFAULT_MERGE_WINDOW_SECONDS;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.buildRecoveryReviewSegmentId;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.mergeReviewSegmentStatus;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.mergeSeverity;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.mergeStringValues;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.reviewSegmentSeverity;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertClueIngestion.withReviewWindow;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_SNAPSHOT;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstNonNull;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstSourceAlertId;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.materialOrder;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.objectIds;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toDoubleList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLocalDateTime;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.zones;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewRuleGovernance.DEFAULT_RULE_CANDIDATE_ACTIONS;

/**
 * 复核工作台：时间线、详情流、复核片段视图、生命周期流转与人工复核/忽略/误报标记、工作台汇总。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewWorkbench {

    private final AlertReviewDependencies deps;
    private final AlertClueIngestion alertClueIngestion;
    private final ReviewItemQuery reviewItemQuery;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;
    private final ReviewRuleGovernance reviewRuleGovernance;

    ReviewWorkbench(AlertReviewDependencies deps, AlertClueIngestion alertClueIngestion, ReviewItemQuery reviewItemQuery, ReviewMediaScopeGuard reviewMediaScopeGuard, ReviewRuleGovernance reviewRuleGovernance) {
        this.deps = deps;
        this.alertClueIngestion = alertClueIngestion;
        this.reviewItemQuery = reviewItemQuery;
        this.reviewMediaScopeGuard = reviewMediaScopeGuard;
        this.reviewRuleGovernance = reviewRuleGovernance;
    }

    public List<ReviewEvidenceItem> getTimeline(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        return deps.reviewItemStore.listTimeline(reviewItemId);
    }

    public List<ReviewEvidenceItem> getTimeline(Long reviewItemId,
                                                Long reviewCaseId,
                                                Long operatorUserId,
                                                List<String> allowedCameraIds) {
        List<ReviewEvidenceItem> timeline = getTimeline(reviewItemId);
        enforceItemEvidenceMediaReadScope(
                reviewCaseId,
                reviewItemId,
                operatorUserId,
                allowedCameraIds,
                "timeline media read",
                timeline.stream()
                        .map(item -> new MediaAccessRef(item.materialType(), item.materialUri()))
                        .toList()
        );
        return timeline;
    }

    public List<ReviewDetailStreamItem> getReviewDetailStream(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        List<ReviewDetailStreamItem> stream = new ArrayList<>();
        stream.addAll(buildDetectionDetailStream(item));
        stream.addAll(buildMotionDetailStream(item));
        stream.addAll(buildLifecycleDetailStream(item));
        for (ReviewEvidenceItem evidenceItem : deps.reviewItemStore.listTimeline(reviewItemId)) {
            LocalDateTime seekTime = evidenceItem.happenedAt() == null
                    ? item.firstAlertTime()
                    : evidenceItem.happenedAt();
            LocalDateTime recordStartTime = MATERIAL_RECORD.equals(evidenceItem.materialType())
                    ? evidenceItem.recordStartTime()
                    : null;
            Integer playbackOffsetSeconds = null;
            if (recordStartTime != null && seekTime != null && !seekTime.isBefore(recordStartTime)) {
                long offsetSeconds = Duration.between(recordStartTime, seekTime).getSeconds();
                playbackOffsetSeconds = (int) Math.min(offsetSeconds, Integer.MAX_VALUE);
            }
            stream.add(new ReviewDetailStreamItem(
                    item.id(),
                    evidenceItem.sourceAlertId(),
                    item.cameraId(),
                    item.zoneCode(),
                    null,
                    item.objectLabel(),
                    evidenceItem.materialType(),
                    evidenceItem.happenedAt(),
                    seekTime,
                    toDoubleList(item.reviewData() == null ? null : item.reviewData().get("bbox")),
                    List.of(),
                    evidenceItem.materialType(),
                    evidenceItem.materialUri(),
                    Map.of("source", "timeline"),
                    recordStartTime,
                    playbackOffsetSeconds
            ));
        }
        return stream.stream()
                .sorted(Comparator
                        .comparing(ReviewDetailStreamItem::seekTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(itemRow -> materialOrder(itemRow.materialType()))
                        .thenComparing(ReviewDetailStreamItem::lifecycleEvent, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<ReviewDetailStreamItem> getReviewDetailStream(Long reviewItemId,
                                                              Long reviewCaseId,
                                                              Long operatorUserId,
                                                              List<String> allowedCameraIds) {
        List<ReviewDetailStreamItem> stream = getReviewDetailStream(reviewItemId);
        enforceItemEvidenceMediaReadScope(
                reviewCaseId,
                reviewItemId,
                operatorUserId,
                allowedCameraIds,
                "detail stream media read",
                stream.stream()
                        .map(item -> new MediaAccessRef(item.materialType(), item.materialUri()))
                        .toList()
        );
        return stream;
    }

    public ReviewSegmentView getReviewSegment(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        Optional<ReviewSegmentView> persistedSegment = deps.reviewItemStore.findPersistedReviewSegment(reviewItemId);
        if (persistedSegment.isPresent()) {
            return persistedSegment.get();
        }
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        return toReviewSegmentView(item);
    }

    public ReviewItemAggregate updateReviewLifecycle(ReviewLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate initialItem = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        deps.reviewItemStore.acquireReviewSegmentTransactionLocks(
                initialItem.cameraId(),
                initialItem.sourceSystem(),
                List.of()
        );
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        LocalDateTime happenedAt = command.happenedAt() == null ? LocalDateTime.now() : command.happenedAt();
        String state = normalizeReviewSegmentState(command.lifecycleState());
        Optional<ReviewSegmentView> persistedSegment = deps.reviewItemStore.findPersistedReviewSegment(item.id());
        assertReviewSegmentTransitionAllowed(item, persistedSegment, state, happenedAt);
        Map<String, Object> reviewData = new LinkedHashMap<>(item.reviewData() == null ? Map.of() : item.reviewData());
        Map<String, Object> lifecycle = new LinkedHashMap<>(toStringObjectMap(reviewData.get("lifecycle")));
        List<Map<String, Object>> events = new ArrayList<>(toMapList(lifecycle.get("events")));
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("state", state);
        event.put("event", state);
        event.put("happenedAt", happenedAt.toString());
        event.put("objectIds", command.objectIds() == null ? List.of() : List.copyOf(command.objectIds()));
        event.put("labels", command.labels() == null ? List.of() : List.copyOf(command.labels()));
        event.put("zones", command.zones() == null ? List.of() : List.copyOf(command.zones()));
        event.put("bbox", command.bbox() == null ? List.of() : List.copyOf(command.bbox()));
        event.put("motion", command.motionMetadata() == null ? Map.of() : Map.copyOf(command.motionMetadata()));
        if (hasText(command.recordUri())) {
            event.put("recordUri", command.recordUri());
        }
        events.add(immutableNonNullMap(event));

        String startedAt = firstText(lifecycle.get("startedAt"), firstText(reviewData.get("startTime"),
                item.firstAlertTime() == null ? null : item.firstAlertTime().toString()));
        lifecycle.put("state", state);
        lifecycle.put("startedAt", startedAt);
        lifecycle.put("lastSeenAt", happenedAt.toString());
        lifecycle.put("activeObjectIds", command.objectIds() == null ? List.of() : List.copyOf(command.objectIds()));
        lifecycle.put("labels", mergeStringValues(lifecycle.get("labels"), command.labels()));
        lifecycle.put("zones", mergeStringValues(lifecycle.get("zones"), command.zones()));
        lifecycle.put("cutoffWindowSeconds", DEFAULT_MERGE_WINDOW_SECONDS);
        lifecycle.put("events", List.copyOf(events));
        if ("ended".equals(state)) {
            lifecycle.put("endedAt", happenedAt.toString());
        }
        reviewData.put("lifecycle", immutableNonNullMap(lifecycle));
        reviewData.put("labels", mergeStringValues(reviewData.get("labels"), command.labels()));
        reviewData.put("zones", mergeStringValues(reviewData.get("zones"), command.zones()));
        reviewData.put("objectIds", mergeStringValues(reviewData.get("objectIds"), command.objectIds()));
        if (command.bbox() != null && !command.bbox().isEmpty()) {
            reviewData.put("bbox", List.copyOf(command.bbox()));
        }
        if (command.motionMetadata() != null && !command.motionMetadata().isEmpty()) {
            reviewData.put("motion", mergeMotionMetadata(reviewData.get("motion"), command.motionMetadata(), event));
        }
        String persistedSegmentId = persistedSegment
                .map(ReviewSegmentView::segmentId)
                .orElse(null);
        reviewData.put("reviewSegment", updateReviewSegmentLifecycle(
                item,
                reviewData,
                event,
                state,
                happenedAt,
                persistedSegmentId
        ));
        assertReviewSegmentDoesNotOverlapOtherItems(item.id(), toStringObjectMap(reviewData.get("reviewSegment")));

        List<ReviewEvidenceItem> evidenceItems = new ArrayList<>();
        String recordEvidenceStatus = item.recordEvidenceStatus();
        LocalDateTime checkedAt = item.recordEvidenceCheckedAt();
        String message = item.recordEvidenceMessage();
        if (hasText(command.recordUri())) {
            evidenceItems.add(new ReviewEvidenceItem(item.id(), firstSourceAlertId(item), MATERIAL_RECORD,
                    command.recordUri(), happenedAt));
            recordEvidenceStatus = RECORD_EVIDENCE_FOUND;
            checkedAt = LocalDateTime.now();
            message = "lifecycle_record_uri";
        }
        Map<String, Object> persistedReviewData = new LinkedHashMap<>(withReviewWindow(
                immutableNonNullMap(reviewData),
                item.firstAlertTime(),
                max(item.lastAlertTime(), happenedAt)
        ));
        if ("ended".equals(state)) {
            persistedReviewData.put("reviewSegment", reviewData.get("reviewSegment"));
        }
        ReviewItemAggregate updated = deps.reviewItemStore.updateReviewLifecycle(
                item.id(),
                immutableNonNullMap(persistedReviewData),
                item.firstAlertTime(),
                max(item.lastAlertTime(), happenedAt),
                evidenceItems,
                recordEvidenceStatus,
                checkedAt,
                message
        );
        return reviewItemQuery.withEventProjection(updated);
    }

    public ReviewItemAggregate retryRecordEvidence(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        RecordEvidenceAttempt attempt = alertClueIngestion.resolveRecordEvidence(
                firstSourceAlertId(item),
                item.deviceId(),
                item.cameraId(),
                item.firstAlertTime()
        );
        if (attempt.evidenceItem().isPresent()) {
            deps.reviewItemStore.appendEvidence(item.id(), List.of(attempt.evidenceItem().get()));
        }
        return reviewItemQuery.withEventProjection(deps.reviewItemStore.updateRecordEvidenceStatus(
                item.id(),
                attempt.recordEvidenceStatus(),
                attempt.recordEvidenceCheckedAt(),
                attempt.recordEvidenceMessage()
        ));
    }

    public ReviewItemAggregate markReviewed(ReviewOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate existing = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        if (isSameReviewStatus(existing, STATUS_REVIEWED)) {
            reviewItemQuery.markUserReviewed(command.reviewItemId(), command.reviewerUserId(), existing.reviewedAt());
            return reviewItemQuery.withEventProjection(existing);
        }
        assertReviewStatusTransitionAllowed(existing, STATUS_REVIEWED);
        LocalDateTime reviewedAt = LocalDateTime.now();
        reviewItemQuery.markUserReviewed(command.reviewItemId(), command.reviewerUserId(), reviewedAt);
        return reviewItemQuery.withEventProjection(deps.reviewItemStore.updateReviewStatus(
                command.reviewItemId(),
                STATUS_REVIEWED,
                command.reviewerUserId(),
                null,
                reviewedAt
        ));
    }

    public ReviewUserStatusView markUserReviewStatus(ReviewUserStatusCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        requirePositive(command.userId(), "userId");
        deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        boolean hasBeenReviewed = Boolean.TRUE.equals(command.hasBeenReviewed());
        return deps.reviewItemStore.upsertUserReviewStatus(
                command.reviewItemId(),
                command.userId(),
                hasBeenReviewed,
                hasBeenReviewed ? LocalDateTime.now() : null
        );
    }

    public ReviewItemAggregate ignore(ReviewOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate existing = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        if (isSameReviewStatus(existing, STATUS_IGNORED)) {
            reviewItemQuery.markUserReviewed(command.reviewItemId(), command.reviewerUserId(), existing.reviewedAt());
            return reviewItemQuery.withEventProjection(existing);
        }
        assertReviewStatusTransitionAllowed(existing, STATUS_IGNORED);
        LocalDateTime reviewedAt = LocalDateTime.now();
        reviewItemQuery.markUserReviewed(command.reviewItemId(), command.reviewerUserId(), reviewedAt);
        return reviewItemQuery.withEventProjection(deps.reviewItemStore.updateReviewStatus(
                command.reviewItemId(),
                STATUS_IGNORED,
                command.reviewerUserId(),
                command.reason(),
                reviewedAt
        ));
    }

    public ReviewItemAggregate markFalsePositive(ReviewOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        if (item.eventId() != null || STATUS_CONVERTED.equals(item.reviewStatus())) {
            throw new IllegalStateException("converted_review_item_cannot_be_marked_false_positive");
        }
        if (isSameReviewStatus(item, STATUS_FALSE_POSITIVE)) {
            reviewItemQuery.markUserReviewed(command.reviewItemId(), command.reviewerUserId(), item.reviewedAt());
            return reviewItemQuery.withEventProjection(item);
        }
        assertReviewStatusTransitionAllowed(item, STATUS_FALSE_POSITIVE);
        LocalDateTime reviewedAt = LocalDateTime.now();
        reviewItemQuery.markUserReviewed(command.reviewItemId(), command.reviewerUserId(), reviewedAt);
        return reviewItemQuery.withEventProjection(deps.reviewItemStore.updateFalsePositive(
                command.reviewItemId(),
                command.reviewerUserId(),
                command.reason(),
                buildRuleSuggestion(item, command.reason()),
                reviewedAt
        ));
    }

    public ReviewWorkbenchSummary getWorkbenchSummary(ReviewQuery query) {
        List<ReviewItemAggregate> items = reviewItemQuery.listWorkbench(query == null
                ? new ReviewQuery(null, null, null, null)
                : new ReviewQuery(null, null, query.zoneCode(), query.objectLabel(), null, null, null,
                        null, query.beginTime(), query.endTime()));
        long pendingReview = items.stream().filter(item -> STATUS_PENDING_REVIEW.equals(item.reviewStatus())).count();
        long reviewedByMe = 0L;
        if (query != null && query.reviewerUserId() != null) {
            reviewedByMe = deps.reviewItemStore.countReviewedByUser(
                    items.stream().map(ReviewItemAggregate::id).toList(),
                    query.reviewerUserId()
            );
        }
        long missingRecord = items.stream()
                .filter(item -> RECORD_EVIDENCE_MISSING.equals(item.recordEvidenceStatus())
                        || RECORD_EVIDENCE_FAILED.equals(item.recordEvidenceStatus()))
                .count();
        long converted = items.stream().filter(item -> item.eventId() != null).count();
        long inReviewCase = items.stream().filter(item -> Boolean.TRUE.equals(item.inReviewCase())).count();
        return new ReviewWorkbenchSummary(items.size(), pendingReview, reviewedByMe, missingRecord, converted, inReviewCase);
    }

    private void enforceItemEvidenceMediaReadScope(Long reviewCaseId,
                                                   Long reviewItemId,
                                                   Long operatorUserId,
                                                   List<String> allowedCameraIds,
                                                   String reason,
                                                   List<MediaAccessRef> mediaRefs) {
        List<MediaAccessRef> recordRefs = mediaRefsByType(mediaRefs, MATERIAL_RECORD);
        if (!recordRefs.isEmpty()) {
            reviewMediaScopeGuard.enforceItemMediaReadScope(reviewCaseId, reviewItemId, operatorUserId, "playback",
                    allowedCameraIds, reason, recordRefs);
        }
        List<MediaAccessRef> snapshotRefs = mediaRefsByType(mediaRefs, MATERIAL_SNAPSHOT);
        if (!snapshotRefs.isEmpty()) {
            reviewMediaScopeGuard.enforceItemMediaReadScope(reviewCaseId, reviewItemId, operatorUserId, "snapshot",
                    allowedCameraIds, reason, snapshotRefs);
        }
    }

    private static List<MediaAccessRef> mediaRefsByType(List<MediaAccessRef> mediaRefs, String materialType) {
        return (mediaRefs == null ? List.<MediaAccessRef>of() : mediaRefs).stream()
                .filter(Objects::nonNull)
                .filter(ref -> materialType.equals(ref.materialType()))
                .filter(ref -> hasText(ref.materialUri()))
                .toList();
    }

    private List<ReviewDetailStreamItem> buildDetectionDetailStream(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        List<Map<String, Object>> detections = toMapList(reviewData.get("detections"));
        List<ReviewDetailStreamItem> stream = new ArrayList<>();
        for (Map<String, Object> detection : detections) {
            LocalDateTime happenedAt = firstNonNull(toLocalDateTime(detection.get("alertTime")), item.firstAlertTime());
            List<String> labels = toStringList(detection.get("labels"), firstText(detection.get("objectLabel"), item.objectLabel()));
            List<String> objectIds = toStringList(detection.get("objectIds"), null);
            List<Double> bbox = toDoubleList(detection.get("bbox"));
            if (bbox.isEmpty()) {
                bbox = toDoubleList(reviewData.get("bbox"));
            }
            int count = Math.max(Math.max(labels.size(), objectIds.size()), 1);
            for (int index = 0; index < count; index++) {
                String label = index < labels.size() ? labels.get(index) : firstText(detection.get("objectLabel"), item.objectLabel());
                String objectId = index < objectIds.size() ? objectIds.get(index) : null;
                stream.add(new ReviewDetailStreamItem(
                        item.id(),
                        firstText(detection.get("sourceAlertId"), firstSourceAlertId(item)),
                        firstText(detection.get("cameraId"), item.cameraId()),
                        firstText(detection.get("zoneCode"), item.zoneCode()),
                        objectId,
                        label,
                        "detected",
                        happenedAt,
                        happenedAt,
                        bbox,
                        List.of(),
                        hasText(toText(detection.get("recordUri"))) ? MATERIAL_RECORD : null,
                        firstText(detection.get("recordUri"), null),
                        detection
                ));
            }
        }
        return List.copyOf(stream);
    }

    private List<ReviewDetailStreamItem> buildMotionDetailStream(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        Map<String, Object> motion = toStringObjectMap(reviewData.get("motion"));
        List<ReviewDetailStreamItem> stream = new ArrayList<>();
        for (Map<String, Object> point : toMapList(motion.get("path"))) {
            LocalDateTime happenedAt = firstNonNull(
                    toLocalDateTime(firstNonNull(point.get("timestamp"), point.get("time"), point.get("happenedAt"))),
                    item.firstAlertTime()
            );
            List<Double> bbox = toDoubleList(point.get("bbox"));
            if (bbox.isEmpty()) {
                bbox = toDoubleList(reviewData.get("bbox"));
            }
            stream.add(new ReviewDetailStreamItem(
                    item.id(),
                    firstSourceAlertId(item),
                    item.cameraId(),
                    item.zoneCode(),
                    firstText(firstNonNull(point.get("objectId"), point.get("object_id")), null),
                    firstText(point.get("label"), item.objectLabel()),
                    firstText(point.get("event"), "motion"),
                    happenedAt,
                    happenedAt,
                    bbox,
                    List.of(point),
                    null,
                    null,
                    point
            ));
        }
        return List.copyOf(stream);
    }

    private List<ReviewDetailStreamItem> buildLifecycleDetailStream(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        Map<String, Object> lifecycle = toStringObjectMap(reviewData.get("lifecycle"));
        List<ReviewDetailStreamItem> stream = new ArrayList<>();
        for (Map<String, Object> event : toMapList(lifecycle.get("events"))) {
            LocalDateTime happenedAt = firstNonNull(
                    toLocalDateTime(event.get("happenedAt")),
                    item.firstAlertTime()
            );
            List<String> labels = toStringList(event.get("labels"), item.objectLabel());
            List<String> objectIds = toStringList(event.get("objectIds"), null);
            List<Double> bbox = toDoubleList(event.get("bbox"));
            if (bbox.isEmpty()) {
                bbox = toDoubleList(reviewData.get("bbox"));
            }
            int count = Math.max(Math.max(labels.size(), objectIds.size()), 1);
            for (int index = 0; index < count; index++) {
                stream.add(new ReviewDetailStreamItem(
                        item.id(),
                        firstSourceAlertId(item),
                        item.cameraId(),
                        item.zoneCode(),
                        index < objectIds.size() ? objectIds.get(index) : null,
                        index < labels.size() ? labels.get(index) : item.objectLabel(),
                        firstText(event.get("state"), firstText(event.get("event"), "active")),
                        happenedAt,
                        happenedAt,
                        bbox,
                        List.of(event),
                        hasText(toText(event.get("recordUri"))) ? MATERIAL_RECORD : null,
                        toText(event.get("recordUri")),
                        event
                ));
            }
        }
        return List.copyOf(stream);
    }

    private static boolean isSameReviewStatus(ReviewItemAggregate item, String targetStatus) {
        return Objects.equals(targetStatus, item.reviewStatus());
    }

    private static void assertReviewStatusTransitionAllowed(ReviewItemAggregate item, String targetStatus) {
        String currentStatus = item.reviewStatus();
        if (currentStatus == null || STATUS_PENDING_REVIEW.equals(currentStatus)) {
            return;
        }
        if (Objects.equals(currentStatus, targetStatus)) {
            return;
        }
        throw new IllegalStateException("review_item_status_conflict: " + currentStatus + " -> " + targetStatus);
    }

    private static String normalizeReviewSegmentState(String lifecycleState) {
        String state = hasText(lifecycleState) ? lifecycleState.trim().toLowerCase() : "active";
        if (List.of("active", "detection", "alert", "ended").contains(state)) {
            return state;
        }
        throw new IllegalArgumentException("review segment state must be active, detection, alert, or ended: " + lifecycleState);
    }

    private static void assertReviewSegmentTransitionAllowed(ReviewItemAggregate item,
                                                             Optional<ReviewSegmentView> persistedSegment,
                                                             String nextState,
                                                             LocalDateTime happenedAt) {
        Map<String, Object> segment = toStringObjectMap(item.reviewData() == null
                ? null
                : item.reviewData().get("reviewSegment"));
        String persistedState = persistedSegment.map(ReviewSegmentView::status).orElse(null);
        String currentState = firstText(segment.get("status"), firstText(persistedState, "active"));
        if (("ended".equals(currentState) || "ended".equals(persistedState)) && !"ended".equals(nextState)) {
            throw new IllegalStateException("ended review segment cannot be reopened: " + item.id());
        }
        LocalDateTime startTime = toLocalDateTime(firstText(segment.get("startTime"),
                persistedSegment.map(ReviewSegmentView::startTime)
                        .map(LocalDateTime::toString)
                        .orElse(item.firstAlertTime() == null ? null : item.firstAlertTime().toString())));
        if (happenedAt != null && startTime != null && happenedAt.isBefore(startTime)) {
            throw new IllegalArgumentException("review segment lifecycle time cannot be before review segment start: " + item.id());
        }
    }

    private void assertReviewSegmentDoesNotOverlapOtherItems(Long reviewItemId, Map<String, Object> segment) {
        String cameraId = firstText(segment.get("cameraId"), null);
        LocalDateTime startTime = toLocalDateTime(segment.get("startTime"));
        LocalDateTime endTime = reviewSegmentEffectiveEnd(segment);
        if (!hasText(cameraId) || startTime == null || endTime == null) {
            return;
        }
        for (ReviewItemAggregate other : reviewItemQuery.listWorkbench(new ReviewQuery(null, cameraId, null, null))) {
            if (Objects.equals(reviewItemId, other.id())) {
                continue;
            }
            Map<String, Object> otherSegment = toStringObjectMap(other.reviewData() == null
                    ? null
                    : other.reviewData().get("reviewSegment"));
            if (!Objects.equals(cameraId, firstText(otherSegment.get("cameraId"), other.cameraId()))) {
                continue;
            }
            LocalDateTime otherStartTime = toLocalDateTime(firstText(otherSegment.get("startTime"),
                    other.firstAlertTime() == null ? null : other.firstAlertTime().toString()));
            LocalDateTime otherEndTime = reviewSegmentEffectiveEnd(otherSegment);
            if (otherStartTime != null && otherEndTime != null
                    && reviewSegmentsOverlap(startTime, endTime, otherStartTime, otherEndTime)) {
                throw new IllegalStateException("overlapping review segment for camera " + cameraId + ": " + other.id());
            }
        }
    }

    private static LocalDateTime reviewSegmentEffectiveEnd(Map<String, Object> segment) {
        String status = firstText(segment.get("status"), "active");
        if (!"ended".equals(status)) {
            return LocalDateTime.MAX;
        }
        LocalDateTime endTime = toLocalDateTime(segment.get("endTime"));
        return endTime == null ? toLocalDateTime(segment.get("startTime")) : endTime;
    }

    private static boolean reviewSegmentsOverlap(LocalDateTime startTime,
                                                 LocalDateTime endTime,
                                                 LocalDateTime otherStartTime,
                                                 LocalDateTime otherEndTime) {
        return startTime.isBefore(otherEndTime) && otherStartTime.isBefore(endTime);
    }

    private static Map<String, Object> updateReviewSegmentLifecycle(ReviewItemAggregate item,
                                                                    Map<String, Object> reviewData,
                                                                    Map<String, Object> lifecycleEvent,
                                                                    String state,
                                                                    LocalDateTime happenedAt,
                                                                    String persistedSegmentId) {
        Map<String, Object> segment = new LinkedHashMap<>(toStringObjectMap(reviewData.get("reviewSegment")));
        if (segment.isEmpty()) {
            segment.put("segmentId", firstText(persistedSegmentId, buildRecoveryReviewSegmentId(item)));
            segment.put("cameraId", item.cameraId());
            segment.put("severity", reviewSegmentSeverity(item.sourceAlertType()));
            segment.put("startTime", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
            segment.put("sourceAlertIds", item.sourceAlertIds() == null ? List.of() : List.copyOf(item.sourceAlertIds()));
        }
        List<Map<String, Object>> events = new ArrayList<>(toMapList(segment.get("events")));
        Map<String, Object> event = new LinkedHashMap<>(lifecycleEvent);
        event.put("event", state);
        events.add(immutableNonNullMap(event));
        segment.put("status", mergeReviewSegmentStatus(toText(segment.get("status")), state));
        segment.put("severity", mergeSeverity(toText(segment.get("severity")), lifecycleSeverity(state)));
        segment.put("endTime", happenedAt.toString());
        segment.put("objectIds", mergeStringValues(segment.get("objectIds"), toStringList(lifecycleEvent.get("objectIds"), null)));
        segment.put("zones", mergeStringValues(segment.get("zones"), toStringList(lifecycleEvent.get("zones"), null)));
        segment.put("events", List.copyOf(events));
        return immutableNonNullMap(segment);
    }

    private static ReviewSegmentView toReviewSegmentView(ReviewItemAggregate item) {
        Map<String, Object> segment = toStringObjectMap(item.reviewData() == null ? null : item.reviewData().get("reviewSegment"));
        String segmentId = firstText(segment.get("segmentId"), buildRecoveryReviewSegmentId(item));
        LocalDateTime startTime = toLocalDateTime(firstText(segment.get("startTime"),
                item.firstAlertTime() == null ? null : item.firstAlertTime().toString()));
        LocalDateTime endTime = toLocalDateTime(firstText(segment.get("endTime"),
                item.lastAlertTime() == null ? null : item.lastAlertTime().toString()));
        String status = firstText(segment.get("status"), "active");
        return new ReviewSegmentView(
                item.id(),
                segmentId,
                firstText(segment.get("cameraId"), item.cameraId()),
                firstText(segment.get("severity"), reviewSegmentSeverity(item.sourceAlertType())),
                status,
                startTime,
                endTime,
                toStringList(segment.containsKey("objectIds") ? segment.get("objectIds") : segment.get("objects"), null),
                toStringList(segment.get("zones"), null),
                toStringList(segment.containsKey("sourceAlertIds") ? segment.get("sourceAlertIds") : segment.get("alerts"), null),
                toMapList(segment.get("events")),
                immutableNonNullMap(segment)
        );
    }

    private static String lifecycleSeverity(String state) {
        if ("alert".equals(state)) {
            return "alert";
        }
        if ("detection".equals(state)) {
            return "detection";
        }
        return null;
    }

    private Map<String, Object> buildRuleSuggestion(ReviewItemAggregate item, String reason) {
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("action", "suppress_label_zone");
        suggestion.put("candidateActions", DEFAULT_RULE_CANDIDATE_ACTIONS);
        suggestion.put("lifecycleStatus", RULE_SUGGESTION_PENDING);
        suggestion.put("sourceSystem", item.sourceSystem());
        suggestion.put("ruleCode", item.ruleCode());
        suggestion.put("cameraId", item.cameraId());
        suggestion.put("zoneCode", item.zoneCode());
        suggestion.put("objectLabel", item.objectLabel());
        if (hasText(reason)) {
            suggestion.put("reason", reason);
        }
        suggestion.putAll(reviewRuleGovernance.buildRuleSuggestionSafetySummary(item));
        suggestion.put("suggestedRuleName", "false_positive_" + item.cameraId() + "_" + item.zoneCode());
        return suggestion;
    }

    private static Map<String, Object> mergeMotionMetadata(Object current,
                                                           Map<String, Object> incoming,
                                                           Map<String, Object> lifecycleEvent) {
        Map<String, Object> motion = new LinkedHashMap<>(toStringObjectMap(current));
        motion.putAll(incoming == null ? Map.of() : incoming);
        List<Map<String, Object>> path = new ArrayList<>(toMapList(motion.get("path")));
        path.add(lifecycleEvent);
        motion.put("path", List.copyOf(path));
        return immutableNonNullMap(motion);
    }
}
