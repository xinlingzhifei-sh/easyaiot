package com.basiclab.iot.system.service.supervision.alertreview;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.matchesText;

/**
 * 复核条目查询：工作台列表、查询条件匹配、人工复核状态与事件投影（事件中心状态只读回填到复核条目）。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewItemQuery {

    private final AlertReviewDependencies deps;

    ReviewItemQuery(AlertReviewDependencies deps) {
        this.deps = deps;
    }

    public List<ReviewItemAggregate> listWorkbench(ReviewQuery query) {
        if (query == null) {
            return deps.reviewItemStore.listWorkbench(null).stream()
                    .map(this::withEventProjection)
                    .toList();
        }
        return deps.reviewItemStore.listWorkbench(query)
                .stream()
                .map(this::withEventProjection)
                .filter(item -> matchesQuery(item, query))
                .filter(item -> matchesReviewerStatus(query.reviewerUserId(), item.id()))
                .toList();
    }

    List<ReviewItemAggregate> loadReviewItems(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        return reviewItemIds.stream()
                .map(id -> withEventProjection(deps.reviewItemStore.findById(id).orElse(null)))
                .filter(Objects::nonNull)
                .toList();
    }

    boolean hasRecordEvidenceGap(ReviewItemAggregate item) {
        return item != null
                && (RECORD_EVIDENCE_MISSING.equals(item.recordEvidenceStatus())
                || RECORD_EVIDENCE_FAILED.equals(item.recordEvidenceStatus()));
    }

    void markUserReviewed(Long reviewItemId, Long reviewerUserId, LocalDateTime reviewedAt) {
        if (reviewerUserId == null) {
            return;
        }
        deps.reviewItemStore.upsertUserReviewStatus(reviewItemId, reviewerUserId, true, reviewedAt);
    }

    boolean matchesReviewerStatus(Long reviewerUserId, Long reviewItemId) {
        return reviewerUserId == null || deps.reviewItemStore.findUserReviewStatus(reviewItemId, reviewerUserId)
                .map(ReviewUserStatusView::hasBeenReviewed)
                .orElse(false);
    }

    List<Long> boundEventIds(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        Set<Long> eventIds = new LinkedHashSet<>();
        for (Long reviewItemId : reviewItemIds) {
            deps.reviewItemStore.findById(reviewItemId)
                    .map(ReviewItemAggregate::eventId)
                    .filter(Objects::nonNull)
                    .ifPresent(eventIds::add);
        }
        return List.copyOf(eventIds);
    }

    ReviewItemAggregate withEventProjection(ReviewItemAggregate item) {
        if (item == null || item.eventId() == null) {
            return item;
        }
        return deps.eventProjectionStore.findByEventId(item.eventId())
                .map(projection -> new ReviewItemAggregate(
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
                        projection.eventStatus(),
                        projection.closeCheckStatus(),
                        projection.evidenceStatus(),
                        mapEventReviewStatus(projection),
                        item.inReviewCase(),
                        item.ruleSuggestionStatus(),
                        item.ruleSuggestionUpdatedAt()
                ))
                .orElse(item);
    }

    static boolean matchesQuery(ReviewItemAggregate item, ReviewQuery query) {
        return matchesText(query.reviewStatus(), item.reviewStatus())
                && matchesText(query.cameraId(), item.cameraId())
                && matchesText(query.zoneCode(), item.zoneCode())
                && matchesText(query.objectLabel(), item.objectLabel())
                && matchesText(query.recordEvidenceStatus(), item.recordEvidenceStatus())
                && matchesConverted(query.converted(), item.eventId())
                && matchesInCase(query.inReviewCase(), item.inReviewCase())
                && matchesReviewer(query.reviewerUserId(), item.reviewerUserId())
                && matchesTimeRange(query.beginTime(), query.endTime(), item.firstAlertTime(), item.lastAlertTime());
    }

    private static boolean matchesConverted(Boolean converted, Long eventId) {
        return converted == null || converted == (eventId != null);
    }

    private static boolean matchesInCase(Boolean inReviewCase, Boolean actual) {
        return inReviewCase == null || inReviewCase.equals(Boolean.TRUE.equals(actual));
    }

    private static boolean matchesReviewer(Long reviewerUserId, Long actualReviewerUserId) {
        return reviewerUserId == null || Objects.equals(reviewerUserId, actualReviewerUserId);
    }

    private static boolean matchesTimeRange(LocalDateTime beginTime,
                                            LocalDateTime endTime,
                                            LocalDateTime firstAlertTime,
                                            LocalDateTime lastAlertTime) {
        if (beginTime != null && lastAlertTime != null && lastAlertTime.isBefore(beginTime)) {
            return false;
        }
        return endTime == null || firstAlertTime == null || firstAlertTime.isBefore(endTime);
    }

    static String mapEventReviewStatus(EventProjection projection) {
        if (projection == null) {
            return null;
        }
        if ("closed".equals(projection.eventStatus())) {
            return "closed";
        }
        if ("returned".equals(projection.eventStatus()) || "rejected".equals(projection.eventStatus())) {
            return "returned";
        }
        if (projection.evidenceStatus() != null && projection.evidenceStatus().startsWith("missing")) {
            return "pending_evidence";
        }
        if ("recheck_required".equals(projection.closeCheckStatus()) || "rechecking".equals(projection.closeCheckStatus())) {
            return "rechecking";
        }
        return projection.eventStatus() == null ? null : "accepted";
    }
}
