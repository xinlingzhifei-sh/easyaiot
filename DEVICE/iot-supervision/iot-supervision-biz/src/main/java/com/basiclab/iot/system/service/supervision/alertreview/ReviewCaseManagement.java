package com.basiclab.iot.system.service.supervision.alertreview;

import java.time.Duration;
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

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD_COVERAGE;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_REVIEW_ACTION;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_SNAPSHOT;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.collectStringValues;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstSourceAlertId;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.intersection;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.intersects;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.materialOrder;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.objectIds;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.reviewItemIdsFromTimeline;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.zones;

/**
 * 复核案件：案件创建/合并/拆分/关闭/指派、案件时间线、关联候选推荐。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewCaseManagement {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;
    private final ReviewRecordCoverage reviewRecordCoverage;

    ReviewCaseManagement(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery, ReviewMediaScopeGuard reviewMediaScopeGuard, ReviewRecordCoverage reviewRecordCoverage) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
        this.reviewMediaScopeGuard = reviewMediaScopeGuard;
        this.reviewRecordCoverage = reviewRecordCoverage;
    }

    private static final int DEFAULT_CASE_CANDIDATE_WINDOW_SECONDS = 600;

    public ReviewCaseView createReviewCase(ReviewCaseCommand command) {
        Objects.requireNonNull(command, "command");
        List<Long> reviewItemIds = normalizeReviewCaseItems(command.primaryReviewItemId(), command.reviewItemIds());
        Long primaryReviewItemId = command.primaryReviewItemId() == null ? reviewItemIds.get(0) : command.primaryReviewItemId();
        return deps.reviewItemStore.createCase(
                new ReviewCaseDraft(command.title(), primaryReviewItemId, command.ownerUserId(), command.notes()),
                reviewItemIds
        );
    }

    public Optional<ReviewCaseView> findReviewCaseByItem(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        if (deps.reviewItemStore.findById(reviewItemId).isEmpty()) {
            return Optional.empty();
        }
        return deps.reviewItemStore.findCaseByReviewItemId(reviewItemId);
    }

    public ReviewCaseView addToReviewCase(Long reviewCaseId, Long reviewItemId) {
        requirePositive(reviewCaseId, "reviewCaseId");
        requirePositive(reviewItemId, "reviewItemId");
        return deps.reviewItemStore.addCaseItem(reviewCaseId, reviewItemId);
    }

    public ReviewCaseView assignReviewCaseOwner(ReviewCaseOwnerCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        requirePositive(command.ownerUserId(), "ownerUserId");
        return deps.reviewItemStore.updateCaseOwner(
                command.reviewCaseId(),
                command.ownerUserId(),
                command.notes(),
                command.operatorUserId(),
                command.expectedVersion(),
                command.operationId()
        );
    }

    public ReviewCaseView closeReviewCase(ReviewCaseOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        return deps.reviewItemStore.closeCase(
                command.reviewCaseId(),
                command.notes(),
                command.operatorUserId(),
                LocalDateTime.now(),
                command.expectedVersion(),
                command.operationId()
        );
    }

    public ReviewCaseMergeResult mergeReviewCases(ReviewCaseMergeCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.targetReviewCaseId(), "targetReviewCaseId");
        requirePositive(command.sourceReviewCaseId(), "sourceReviewCaseId");
        if (Objects.equals(command.targetReviewCaseId(), command.sourceReviewCaseId())) {
            throw new IllegalArgumentException("sourceReviewCaseId must differ from targetReviewCaseId");
        }
        return deps.reviewItemStore.mergeCases(
                command.targetReviewCaseId(),
                command.sourceReviewCaseId(),
                command.operatorUserId(),
                command.notes(),
                command.targetExpectedVersion(),
                command.sourceExpectedVersion(),
                command.operationId()
        );
    }

    public ReviewCaseSplitResult splitReviewCase(ReviewCaseSplitCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.sourceReviewCaseId(), "sourceReviewCaseId");
        List<Long> reviewItemIds = normalizeReviewCaseItems(null, command.reviewItemIds());
        return deps.reviewItemStore.splitCase(
                command.sourceReviewCaseId(),
                new ReviewCaseDraft(command.title(), reviewItemIds.get(0), command.ownerUserId(), command.notes()),
                reviewItemIds,
                command.operatorUserId(),
                command.sourceExpectedVersion(),
                command.operationId()
        );
    }

    public List<ReviewCaseTimelineItem> getReviewCaseTimeline(Long reviewCaseId) {
        return getReviewCaseTimeline(reviewCaseId, new LinkedHashMap<>(), true);
    }

    List<ReviewCaseTimelineItem> getReviewCaseTimeline(
            Long reviewCaseId,
            Map<Long, List<RecordCoverageSegment>> coverageByReviewItemId,
            boolean includeRecordCoverage) {
        requirePositive(reviewCaseId, "reviewCaseId");
        List<ReviewCaseTimelineItem> timeline = new ArrayList<>(deps.reviewItemStore.listCaseTimeline(reviewCaseId));
        timeline.addAll(buildCaseDerivedTimeline(
                reviewCaseId,
                timeline,
                coverageByReviewItemId,
                includeRecordCoverage
        ));
        return timeline.stream()
                .map(this::withCaseTimelinePlaybackContext)
                .sorted(Comparator
                        .comparing(ReviewCaseTimelineItem::happenedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparingInt(item -> materialOrder(item.materialType())))
                .toList();
    }

    private ReviewCaseTimelineItem withCaseTimelinePlaybackContext(ReviewCaseTimelineItem item) {
        if (!MATERIAL_RECORD.equals(item.materialType()) || item.reviewItemId() == null) {
            return item;
        }
        LocalDateTime recordStartTime = item.recordStartTime();
        Integer playbackOffsetSeconds = null;
        if (recordStartTime != null && item.happenedAt() != null && !item.happenedAt().isBefore(recordStartTime)) {
            long offsetSeconds = Duration.between(recordStartTime, item.happenedAt()).getSeconds();
            playbackOffsetSeconds = (int) Math.min(offsetSeconds, Integer.MAX_VALUE);
        }
        return new ReviewCaseTimelineItem(
                item.reviewCaseId(),
                item.reviewItemId(),
                item.cameraId(),
                item.sourceAlertId(),
                item.materialType(),
                item.materialUri(),
                item.happenedAt(),
                item.actionNote(),
                recordStartTime,
                playbackOffsetSeconds
        );
    }

    public List<ReviewCaseTimelineItem> getReviewCaseTimeline(Long reviewCaseId,
                                                              Long operatorUserId,
                                                              List<String> allowedCameraIds) {
        List<ReviewCaseTimelineItem> timeline = getReviewCaseTimeline(reviewCaseId);
        enforceCaseEvidenceMediaReadScope(
                reviewCaseId,
                timeline,
                reviewItemQuery.loadReviewItems(reviewItemIdsFromTimeline(timeline)),
                operatorUserId,
                allowedCameraIds,
                "case timeline media read"
        );
        return timeline;
    }

    public List<ReviewItemAggregate> suggestReviewCaseCandidates(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate base = deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        LocalDateTime beginTime = base.firstAlertTime().minusSeconds(DEFAULT_CASE_CANDIDATE_WINDOW_SECONDS);
        LocalDateTime endTime = base.lastAlertTime().plusSeconds(DEFAULT_CASE_CANDIDATE_WINDOW_SECONDS);
        ReviewQuery query = new ReviewQuery(null, null, null, null, null, null, null, null, beginTime, endTime);
        return reviewItemQuery.listWorkbench(query).stream()
                .filter(candidate -> !Objects.equals(candidate.id(), base.id()))
                .filter(candidate -> correlates(base, candidate))
                .sorted(Comparator
                        .comparingInt((ReviewItemAggregate candidate) -> correlationScore(base, candidate))
                        .reversed()
                        .thenComparing(ReviewItemAggregate::firstAlertTime,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .map(candidate -> withCaseCandidateMatch(base, candidate))
                .toList();
    }

    private void enforceCaseEvidenceMediaReadScope(Long reviewCaseId,
                                                   List<ReviewCaseTimelineItem> timeline,
                                                   List<ReviewItemAggregate> reviewItems,
                                                   Long operatorUserId,
                                                   List<String> allowedCameraIds,
                                                   String reason) {
        List<ReviewCaseTimelineItem> recordItems = caseTimelineItemsByType(timeline, MATERIAL_RECORD);
        if (!recordItems.isEmpty()) {
            reviewMediaScopeGuard.enforceMediaAccessScope(reviewCaseId, recordItems, reviewItems, operatorUserId, "playback",
                    allowedCameraIds, reason);
        }
        List<ReviewCaseTimelineItem> snapshotItems = caseTimelineItemsByType(timeline, MATERIAL_SNAPSHOT);
        if (!snapshotItems.isEmpty()) {
            reviewMediaScopeGuard.enforceMediaAccessScope(reviewCaseId, snapshotItems, reviewItems, operatorUserId, "snapshot",
                    allowedCameraIds, reason);
        }
    }

    private static List<ReviewCaseTimelineItem> caseTimelineItemsByType(List<ReviewCaseTimelineItem> timeline,
                                                                        String materialType) {
        return (timeline == null ? List.<ReviewCaseTimelineItem>of() : timeline).stream()
                .filter(Objects::nonNull)
                .filter(item -> materialType.equals(item.materialType()))
                .filter(item -> hasText(item.materialUri()))
                .toList();
    }

    private List<ReviewCaseTimelineItem> buildCaseDerivedTimeline(
            Long reviewCaseId,
            List<ReviewCaseTimelineItem> storedTimeline,
            Map<Long, List<RecordCoverageSegment>> coverageByReviewItemId,
            boolean includeRecordCoverage) {
        Set<Long> reviewItemIds = new LinkedHashSet<>();
        for (ReviewCaseTimelineItem item : storedTimeline) {
            if (item.reviewItemId() != null) {
                reviewItemIds.add(item.reviewItemId());
            }
        }
        List<ReviewCaseTimelineItem> derived = new ArrayList<>();
        for (Long reviewItemId : reviewItemIds) {
            ReviewItemAggregate item = reviewItemQuery.withEventProjection(deps.reviewItemStore.findById(reviewItemId).orElse(null));
            if (item == null) {
                continue;
            }
            if (includeRecordCoverage) {
                List<RecordCoverageSegment> coverage = coverageByReviewItemId.computeIfAbsent(
                        reviewItemId,
                        reviewRecordCoverage::getRecordCoverage
                );
                for (RecordCoverageSegment segment : coverage) {
                    derived.add(new ReviewCaseTimelineItem(
                            reviewCaseId,
                            item.id(),
                            item.cameraId(),
                            firstSourceAlertId(item),
                            MATERIAL_RECORD_COVERAGE,
                            segment.recordUri(),
                            segment.startTime(),
                            segment.status(),
                            segment.startTime(),
                            0
                    ));
                }
            }
            if (STATUS_FALSE_POSITIVE.equals(item.reviewStatus())) {
                derived.add(new ReviewCaseTimelineItem(
                        reviewCaseId,
                        item.id(),
                        item.cameraId(),
                        firstSourceAlertId(item),
                        MATERIAL_REVIEW_ACTION,
                        "false_positive",
                        item.reviewedAt() == null ? item.lastAlertTime() : item.reviewedAt(),
                        item.ignoreReason()
                ));
            }
            if (item.eventId() != null) {
                derived.add(new ReviewCaseTimelineItem(
                        reviewCaseId,
                        item.id(),
                        item.cameraId(),
                        firstSourceAlertId(item),
                        MATERIAL_REVIEW_ACTION,
                        "converted_to_event:" + item.eventId(),
                        item.convertedAt() == null ? item.lastAlertTime() : item.convertedAt(),
                        item.eventReviewStatus()
                ));
            }
            if (RECORD_EVIDENCE_MISSING.equals(item.recordEvidenceStatus())
                    || RECORD_EVIDENCE_FAILED.equals(item.recordEvidenceStatus())) {
                derived.add(new ReviewCaseTimelineItem(
                        reviewCaseId,
                        item.id(),
                        item.cameraId(),
                        firstSourceAlertId(item),
                        MATERIAL_REVIEW_ACTION,
                        "record_evidence:" + item.recordEvidenceStatus(),
                        item.recordEvidenceCheckedAt() == null ? item.firstAlertTime() : item.recordEvidenceCheckedAt(),
                        item.recordEvidenceMessage()
                ));
            }
        }
        return derived;
    }

    private boolean correlates(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        return correlationScore(base, candidate) > 0;
    }

    private ReviewItemAggregate withCaseCandidateMatch(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        Map<String, Object> match = new LinkedHashMap<>();
        boolean configuredTopologyMatch = false;
        String baseCorrelationId = toText(reviewDataValue(base, "correlationId", "correlation_id"));
        String candidateCorrelationId = toText(reviewDataValue(candidate, "correlationId", "correlation_id"));
        if (hasText(baseCorrelationId) && Objects.equals(baseCorrelationId, candidateCorrelationId)) {
            match.put("correlationId", candidateCorrelationId);
        }

        Set<String> sharedObjectIds = intersection(objectIds(base), objectIds(candidate));
        if (!sharedObjectIds.isEmpty()) {
            match.put("objectIds", List.copyOf(sharedObjectIds));
        }

        String baseArea = regulatoryArea(base);
        String candidateArea = regulatoryArea(candidate);
        if (hasText(baseArea) && Objects.equals(baseArea, candidateArea)) {
            match.put("regulatoryArea", candidateArea);
            configuredTopologyMatch = hasConfiguredRegulatoryAreaMatch(base, candidate, baseArea, candidateArea);
        }

        Set<String> matchedAdjacentCameras = matchedAdjacentCameras(base, candidate);
        if (!matchedAdjacentCameras.isEmpty()) {
            match.put("adjacentCameras", List.copyOf(matchedAdjacentCameras));
            configuredTopologyMatch = configuredTopologyMatch || hasConfiguredAdjacentCameraMatch(base, candidate);
        }

        if (match.isEmpty()) {
            return candidate;
        }
        match.put("source", configuredTopologyMatch ? "configured_camera_topology" : "review_data");
        Map<String, Object> reviewData = new LinkedHashMap<>(
                candidate.reviewData() == null ? Map.of() : candidate.reviewData());
        reviewData.put("caseCandidateMatch", Map.copyOf(match));
        return withReviewData(candidate, Map.copyOf(reviewData));
    }

    private static ReviewItemAggregate withReviewData(ReviewItemAggregate item, Map<String, Object> reviewData) {
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
                item.eventStatus(),
                item.closeCheckStatus(),
                item.evidenceStatus(),
                item.eventReviewStatus(),
                item.inReviewCase(),
                item.ruleSuggestionStatus(),
                item.ruleSuggestionUpdatedAt()
        );
    }

    private boolean hasConfiguredRegulatoryAreaMatch(ReviewItemAggregate base,
                                                    ReviewItemAggregate candidate,
                                                    String baseArea,
                                                    String candidateArea) {
        ReviewCameraTopology baseTopology = cameraTopology(base.cameraId());
        ReviewCameraTopology candidateTopology = cameraTopology(candidate.cameraId());
        return Objects.equals(baseTopology.regulatoryArea(), baseArea)
                || Objects.equals(candidateTopology.regulatoryArea(), candidateArea);
    }

    private boolean hasConfiguredAdjacentCameraMatch(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        ReviewCameraTopology baseTopology = cameraTopology(base.cameraId());
        ReviewCameraTopology candidateTopology = cameraTopology(candidate.cameraId());
        return baseTopology.adjacentCameraIds().contains(candidate.cameraId())
                || candidateTopology.adjacentCameraIds().contains(base.cameraId());
    }

    private Set<String> matchedAdjacentCameras(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        Set<String> values = new LinkedHashSet<>();
        if (hasText(base.cameraId()) && hasAdjacentCamera(base, candidate)) {
            values.add(base.cameraId());
        }
        return values;
    }

    private int correlationScore(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        int score = 0;
        String baseCorrelationId = toText(base.reviewData().get("correlationId"));
        String candidateCorrelationId = toText(candidate.reviewData().get("correlationId"));
        if (hasText(baseCorrelationId) && Objects.equals(baseCorrelationId, candidateCorrelationId)) {
            score += 100;
        }
        if (intersects(objectIds(base), objectIds(candidate))) {
            score += 80;
        }
        if (hasSameRegulatoryArea(base, candidate)) {
            score += 40;
        }
        if (hasAdjacentCamera(base, candidate)) {
            score += 35;
        }
        if (intersects(zones(base), zones(candidate))) {
            score += 30;
        }
        return score;
    }

    private boolean hasSameRegulatoryArea(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        String baseArea = regulatoryArea(base);
        String candidateArea = regulatoryArea(candidate);
        return hasText(baseArea) && Objects.equals(baseArea, candidateArea);
    }

    private String regulatoryArea(ReviewItemAggregate item) {
        String reviewDataArea = toText(reviewDataValue(item,
                "regulatoryArea", "regulatory_area", "regulatoryAreaCode", "regulatory_area_code",
                "supervisionArea", "supervision_area"));
        if (hasText(reviewDataArea)) {
            return reviewDataArea;
        }
        return cameraTopology(item.cameraId()).regulatoryArea();
    }

    private boolean hasAdjacentCamera(ReviewItemAggregate base, ReviewItemAggregate candidate) {
        Set<String> baseAdjacentCameras = adjacentCameras(base);
        Set<String> candidateAdjacentCameras = adjacentCameras(candidate);
        return baseAdjacentCameras.contains(candidate.cameraId()) || candidateAdjacentCameras.contains(base.cameraId());
    }

    private Set<String> adjacentCameras(ReviewItemAggregate item) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, reviewDataValue(item, "adjacentCameras", "adjacent_cameras"));
        collectStringValues(values, cameraTopology(item.cameraId()).adjacentCameraIds());
        return values;
    }

    private ReviewCameraTopology cameraTopology(String cameraId) {
        if (!hasText(cameraId)) {
            return ReviewCameraTopology.empty();
        }
        ReviewCameraTopology topology = deps.cameraTopologyResolver.resolveCameraTopology(cameraId);
        return topology == null ? ReviewCameraTopology.empty() : topology;
    }

    private static Object reviewDataValue(ReviewItemAggregate item, String... keys) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        for (String key : keys) {
            if (reviewData.containsKey(key)) {
                return reviewData.get(key);
            }
        }
        Object motion = reviewData.get("motion");
        if (motion instanceof Map<?, ?> motionMap) {
            for (String key : keys) {
                if (motionMap.containsKey(key)) {
                    return motionMap.get(key);
                }
            }
        }
        return null;
    }

    private static List<Long> normalizeReviewCaseItems(Long primaryReviewItemId, List<Long> reviewItemIds) {
        Set<Long> ids = new LinkedHashSet<>();
        if (primaryReviewItemId != null) {
            requirePositive(primaryReviewItemId, "primaryReviewItemId");
            ids.add(primaryReviewItemId);
        }
        if (reviewItemIds != null) {
            for (Long reviewItemId : reviewItemIds) {
                requirePositive(reviewItemId, "reviewItemId");
                ids.add(reviewItemId);
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("reviewItemIds must not be empty");
        }
        return List.copyOf(ids);
    }
}
