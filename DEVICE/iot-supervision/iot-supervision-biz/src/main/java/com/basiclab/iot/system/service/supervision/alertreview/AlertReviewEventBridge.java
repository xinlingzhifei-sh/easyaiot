package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewItemQuery.mapEventReviewStatus;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewItemQuery.matchesQuery;

/**
 * 复核转事件中心的唯一入口：告警只作为输入，经 SupervisionEventService.createFromAlert 建立监管事件；以及事件投影对账。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class AlertReviewEventBridge {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;

    AlertReviewEventBridge(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
    }

    private static final String REVIEW_SOURCE_SYSTEM = "alert_review";

    public ReviewEventReconciliationResult reconcileEventProjections(ReviewEventReconciliationCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewQuery query = command.query();
        LocalDateTime reconciledAt = LocalDateTime.now();
        List<ReviewItemAggregate> items = deps.reviewItemStore.listWorkbench(query)
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> query == null || matchesQuery(item, query))
                .filter(item -> query == null || reviewItemQuery.matchesReviewerStatus(query.reviewerUserId(), item.id()))
                .toList();
        List<String> findings = new ArrayList<>();
        int reconciledCount = 0;
        int missingProjectionCount = 0;
        int conflictCount = 0;
        for (ReviewItemAggregate item : items) {
            if (item.eventId() == null) {
                continue;
            }
            Optional<EventProjection> projection = deps.eventProjectionStore.findByEventId(item.eventId());
            if (projection.isEmpty()) {
                missingProjectionCount++;
                findings.add("event_projection_missing:" + item.id() + ":" + item.eventId());
                continue;
            }
            EventProjection eventProjection = projection.get();
            String eventReviewStatus = mapEventReviewStatus(eventProjection);
            Map<String, Object> projectionData = new LinkedHashMap<>();
            projectionData.put("eventId", eventProjection.eventId());
            projectionData.put("eventStatus", eventProjection.eventStatus());
            projectionData.put("closeCheckStatus", eventProjection.closeCheckStatus());
            projectionData.put("evidenceStatus", eventProjection.evidenceStatus());
            projectionData.put("eventReviewStatus", eventReviewStatus);
            projectionData.put("reconciledAt", reconciledAt.toString());
            projectionData.put("operatorUserId", command.operatorUserId());
            Map<String, Object> conflictPolicy = eventProjectionConflictPolicy(item, eventProjection, eventReviewStatus);
            projectionData.putAll(conflictPolicy);
            Map<String, Object> reviewData = new LinkedHashMap<>(item.reviewData() == null ? Map.of() : item.reviewData());
            reviewData.put("eventProjection", immutableNonNullMap(projectionData));
            deps.reviewItemStore.updateEventProjection(
                    item.id(),
                    immutableNonNullMap(reviewData),
                    eventProjection,
                    eventReviewStatus,
                    reconciledAt
            );
            reconciledCount++;
            findings.add("event_projection_reconciled:" + item.id() + ":" + item.eventId() + ":" + eventReviewStatus);
            if (!conflictPolicy.isEmpty()) {
                conflictCount++;
                findings.add("event_projection_conflict:" + item.id() + ":" + item.eventId() + ":"
                        + conflictPolicy.get("conflictStatus"));
            }
        }
        return new ReviewEventReconciliationResult(
                items.size(),
                reconciledCount,
                missingProjectionCount,
                conflictCount,
                List.copyOf(findings),
                reconciledAt,
                command.operatorUserId()
        );
    }

    private static Map<String, Object> eventProjectionConflictPolicy(ReviewItemAggregate item,
                                                                     EventProjection projection,
                                                                     String eventReviewStatus) {
        if (item == null || projection == null || !STATUS_CONVERTED.equals(item.reviewStatus())) {
            return Map.of();
        }
        String conflictStatus = eventProjectionConflictStatus(projection);
        if (!hasText(conflictStatus)) {
            return Map.of();
        }
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("conflictPolicy", "keep_converted_review_item");
        policy.put("conflictStatus", conflictStatus);
        policy.put("reviewItemStatusPolicy", "converted_is_terminal");
        policy.put("eventReviewStatusAtConflict", eventReviewStatus);
        return immutableNonNullMap(policy);
    }

    private static String eventProjectionConflictStatus(EventProjection projection) {
        if (projection == null) {
            return null;
        }
        if (List.of("returned", "rejected").contains(projection.eventStatus())) {
            return "event_returned_after_conversion";
        }
        if (List.of("rework_required", "pending_recheck", "exception_review").contains(projection.eventStatus())
                || List.of("recheck_required", "rechecking").contains(projection.closeCheckStatus())) {
            return "event_rework_after_conversion";
        }
        return null;
    }

    public ReviewToEventResult convertToEvent(ReviewToEventCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = reviewItemQuery.withEventProjection(deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId())));
        if (item.eventId() != null) {
            return new ReviewToEventResult(item.id(), item.reviewStatus(), item.eventId(), true);
        }

        AlertToEventResult eventResult = deps.supervisionEventService.createFromAlert(new AlertToEventCommand(
                REVIEW_SOURCE_SYSTEM,
                item.reviewItemNo(),
                item.ruleCode(),
                item.sourceAlertType(),
                item.firstAlertTime(),
                null,
                command.dispatchTasks()
        ));
        LocalDateTime convertedAt = LocalDateTime.now();
        reviewItemQuery.markUserReviewed(item.id(), command.reviewerUserId(), convertedAt);
        ReviewItemAggregate converted = reviewItemQuery.withEventProjection(deps.reviewItemStore.markConverted(
                item.id(),
                command.reviewerUserId(),
                eventResult.eventId(),
                convertedAt
        ));
        return new ReviewToEventResult(converted.id(), converted.reviewStatus(), eventResult.eventId(), eventResult.reused());
    }
}
