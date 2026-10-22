package com.basiclab.iot.system.service.supervision.alertreview;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.roundRate;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Hex;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewAiSummaryService.buildStructuredAiSummaryData;

/**
 * 运营报表：班次/日报生成、投递计划入队、回执确认。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewOperationsReporting {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;
    private final ReviewSemanticIntelligence reviewSemanticIntelligence;

    ReviewOperationsReporting(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery, ReviewSemanticIntelligence reviewSemanticIntelligence) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
        this.reviewSemanticIntelligence = reviewSemanticIntelligence;
    }

    public ReviewOperationsReport generateReviewReport(ReviewReportCommand command) {
        Objects.requireNonNull(command, "command");
        String reportType = firstText(command.reportType(), "shift");
        ReviewQuery query = reportQuery(command);
        List<ReviewItemAggregate> reviewItems = reviewItemQuery.listWorkbench(query);
        List<Long> reviewItemIds = reviewItems.stream().map(ReviewItemAggregate::id).toList();
        List<String> evidenceGaps = new ArrayList<>();
        List<String> recommendedActions = new ArrayList<>();
        int missingRecordCount = 0;
        int falsePositiveCount = 0;
        int convertedEventCount = 0;
        int unreviewedBacklogCount = 0;
        for (ReviewItemAggregate item : reviewItems) {
            if (reviewItemQuery.hasRecordEvidenceGap(item)) {
                missingRecordCount++;
                evidenceGaps.add("review item " + item.reviewItemNo()
                        + " record evidence " + item.recordEvidenceStatus());
                recommendedActions.add("backfill record evidence for " + item.reviewItemNo());
            }
            if (STATUS_FALSE_POSITIVE.equals(item.reviewStatus())) {
                falsePositiveCount++;
                recommendedActions.add("review rule suggestion for " + item.reviewItemNo());
            }
            if (item.eventId() != null) {
                convertedEventCount++;
                recommendedActions.add("track supervision event " + item.eventId());
            }
            if (isUnreviewedBacklog(item)) {
                unreviewedBacklogCount++;
            }
        }
        ReviewSemanticIndexEvaluation semantic = reviewSemanticIntelligence.evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(query, command.operatorUserId()));
        Set<Long> reportReviewItemIds = new LinkedHashSet<>(reviewItemIds);
        List<ReviewEvidenceExportJob> exportJobs = deps.reviewItemStore.listAllExportJobs().stream()
                .filter(job -> exportJobContainsAnyReviewItem(job, reportReviewItemIds))
                .toList();
        int exportFailureCount = (int) exportJobs.stream()
                .filter(job -> EXPORT_JOB_FAILED.equals(job.status()))
                .count();
        LocalDateTime generatedAt = LocalDateTime.now();
        String title = reportType + " review report"
                + (command.periodStart() == null ? "" : " " + command.periodStart().toLocalDate());
        String summary = reviewItems.size() + " review item(s), "
                + evidenceGaps.size() + " evidence gap(s), "
                + convertedEventCount + " converted event(s).";
        List<String> distinctActions = List.copyOf(new LinkedHashSet<>(recommendedActions));
        Map<String, Object> structuredData = new LinkedHashMap<>(buildStructuredAiSummaryData(
                null,
                title,
                summary,
                reviewItems,
                evidenceGaps,
                distinctActions
        ));
        structuredData.put("reportType", reportType);
        structuredData.put("periodStart", command.periodStart() == null ? null : command.periodStart().toString());
        structuredData.put("periodEnd", command.periodEnd() == null ? null : command.periodEnd().toString());
        structuredData.put("reviewItemCount", reviewItems.size());
        structuredData.put("evidenceGapCount", evidenceGaps.size());
        structuredData.put("missingRecordCount", missingRecordCount);
        structuredData.put("missingRecordRate", roundRate(missingRecordCount, reviewItems.size()));
        structuredData.put("unreviewedBacklogCount", unreviewedBacklogCount);
        structuredData.put("unreviewedBacklogRate", roundRate(unreviewedBacklogCount, reviewItems.size()));
        structuredData.put("falsePositiveCount", falsePositiveCount);
        structuredData.put("falsePositiveRate", roundRate(falsePositiveCount, reviewItems.size()));
        structuredData.put("convertedEventCount", convertedEventCount);
        structuredData.put("semanticBacklogCount", semantic.staleReviewItemIds().size());
        structuredData.put("semanticBacklogActions", semantic.recommendedActions());
        structuredData.put("exportJobCount", exportJobs.size());
        structuredData.put("exportFailureCount", exportFailureCount);
        structuredData.put("exportFailureRate", roundRate(exportFailureCount, exportJobs.size()));
        structuredData.put("responsibilityUnitDimensions", buildReportDimensions(
                reviewItems,
                ReviewOperationsReporting::reportResponsibilityUnit
        ));
        structuredData.put("areaDimensions", buildReportDimensions(reviewItems, ReviewItemAggregate::zoneCode));
        structuredData.put("cameraDimensions", buildReportDimensions(reviewItems, ReviewItemAggregate::cameraId));
        structuredData.put("ruleDimensions", buildReportDimensions(reviewItems, ReviewItemAggregate::ruleCode));
        structuredData.put("operatorUserId", command.operatorUserId());
        structuredData.put("generatedAt", generatedAt.toString());
        String reportKey = buildReportKey(reportType, query);
        Map<String, Object> deliveryPlan = buildReportDeliveryPlan(command, reportType, reviewItemIds, generatedAt, reportKey);
        Map<String, Object> acknowledgement = buildReportAcknowledgement(
                command,
                reportType,
                reportKey,
                deps.reviewItemStore.findReportAcknowledgement(reportKey).orElse(null)
        );
        structuredData.put("deliveryPlan", deliveryPlan);
        structuredData.put("acknowledgement", acknowledgement);
        return new ReviewOperationsReport(
                reportType,
                reviewItemIds,
                title,
                summary,
                List.copyOf(evidenceGaps),
                distinctActions,
                generatedAt,
                command.operatorUserId(),
                immutableNonNullMap(structuredData),
                deliveryPlan,
                acknowledgement
        );
    }

    public ReviewOperationsReportDelivery scheduleReviewReportDelivery(ReviewReportCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewOperationsReport report = generateReviewReport(command);
        LocalDateTime queuedAt = LocalDateTime.now();
        int outboxEventCount = deps.reviewItemStore.enqueueOperationsReportDelivery(report, true, queuedAt);
        return new ReviewOperationsReportDelivery(report, outboxEventCount, queuedAt);
    }

    public ReviewReportAcknowledgement acknowledgeReviewReport(ReviewReportAcknowledgementCommand command) {
        Objects.requireNonNull(command, "command");
        String reportType = firstText(command.reportType(), "shift");
        ReviewQuery query = reportQuery(new ReviewReportCommand(
                reportType,
                command.query(),
                command.periodStart(),
                command.periodEnd(),
                command.operatorUserId()
        ));
        List<Long> reviewItemIds = reviewItemQuery.listWorkbench(query).stream().map(ReviewItemAggregate::id).toList();
        String reportKey = buildReportKey(reportType, query);
        Optional<ReviewReportAcknowledgement> existing = deps.reviewItemStore.findReportAcknowledgement(reportKey);
        if (existing.isPresent()) {
            ReviewReportAcknowledgement acknowledgement = existing.get();
            return new ReviewReportAcknowledgement(
                    acknowledgement.reportKey(),
                    acknowledgement.reportType(),
                    acknowledgement.status(),
                    acknowledgement.acknowledgedBy(),
                    acknowledgement.acknowledgedAt(),
                    acknowledgement.note(),
                    true,
                    acknowledgement.metadata()
            );
        }
        LocalDateTime acknowledgedAt = LocalDateTime.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("periodStart", query.beginTime() == null ? null : query.beginTime().toString());
        metadata.put("periodEnd", query.endTime() == null ? null : query.endTime().toString());
        metadata.put("reviewItemIds", reviewItemIds);
        metadata.put("requestedBy", command.operatorUserId());
        return deps.reviewItemStore.saveReportAcknowledgement(new ReviewReportAcknowledgement(
                reportKey,
                reportType,
                "acknowledged",
                command.operatorUserId(),
                acknowledgedAt,
                command.note(),
                false,
                immutableNonNullMap(metadata)
        ));
    }

    private Map<String, Object> buildReportDimensions(List<ReviewItemAggregate> reviewItems,
                                                      Function<ReviewItemAggregate, String> classifier) {
        Map<String, List<ReviewItemAggregate>> grouped = new TreeMap<>();
        for (ReviewItemAggregate item : reviewItems == null ? List.<ReviewItemAggregate>of() : reviewItems) {
            String key = firstText(classifier.apply(item), "unknown");
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        Map<String, Object> dimensions = new LinkedHashMap<>();
        for (Map.Entry<String, List<ReviewItemAggregate>> entry : grouped.entrySet()) {
            dimensions.put(entry.getKey(), reportDimensionSummary(entry.getValue()));
        }
        return immutableNonNullMap(dimensions);
    }

    private Map<String, Object> reportDimensionSummary(List<ReviewItemAggregate> reviewItems) {
        int totalCount = reviewItems == null ? 0 : reviewItems.size();
        int missingRecordCount = 0;
        int falsePositiveCount = 0;
        int convertedEventCount = 0;
        int unreviewedBacklogCount = 0;
        for (ReviewItemAggregate item : reviewItems == null ? List.<ReviewItemAggregate>of() : reviewItems) {
            if (reviewItemQuery.hasRecordEvidenceGap(item)) {
                missingRecordCount++;
            }
            if (STATUS_FALSE_POSITIVE.equals(item.reviewStatus())) {
                falsePositiveCount++;
            }
            if (item.eventId() != null) {
                convertedEventCount++;
            }
            if (isUnreviewedBacklog(item)) {
                unreviewedBacklogCount++;
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reviewItemCount", totalCount);
        summary.put("missingRecordCount", missingRecordCount);
        summary.put("missingRecordRate", roundRate(missingRecordCount, totalCount));
        summary.put("unreviewedBacklogCount", unreviewedBacklogCount);
        summary.put("unreviewedBacklogRate", roundRate(unreviewedBacklogCount, totalCount));
        summary.put("falsePositiveCount", falsePositiveCount);
        summary.put("falsePositiveRate", roundRate(falsePositiveCount, totalCount));
        summary.put("convertedEventCount", convertedEventCount);
        return immutableNonNullMap(summary);
    }

    private static Map<String, Object> buildReportDeliveryPlan(ReviewReportCommand command,
                                                               String reportType,
                                                               List<Long> reviewItemIds,
                                                               LocalDateTime generatedAt,
                                                               String reportKey) {
        Map<String, Object> deliveryPlan = new LinkedHashMap<>();
        deliveryPlan.put("deliveryStatus", "pending");
        deliveryPlan.put("channels", List.of("dashboard", "supervision_console"));
        deliveryPlan.put("reportKey", reportKey);
        deliveryPlan.put("reportType", reportType);
        deliveryPlan.put("periodStart", command.periodStart() == null ? null : command.periodStart().toString());
        deliveryPlan.put("periodEnd", command.periodEnd() == null ? null : command.periodEnd().toString());
        deliveryPlan.put("reviewItemCount", reviewItemIds == null ? 0 : reviewItemIds.size());
        deliveryPlan.put("deliverAfter", generatedAt == null ? null : generatedAt.toString());
        deliveryPlan.put("requiresOperatorAcknowledgement", true);
        deliveryPlan.put("requestedBy", command.operatorUserId());
        return immutableNonNullMap(deliveryPlan);
    }

    private static Map<String, Object> buildReportAcknowledgement(ReviewReportCommand command,
                                                                  String reportType,
                                                                  String reportKey,
                                                                  ReviewReportAcknowledgement persisted) {
        Map<String, Object> acknowledgement = new LinkedHashMap<>();
        acknowledgement.put("required", true);
        acknowledgement.put("status", persisted == null ? "pending" : persisted.status());
        acknowledgement.put("reportKey", reportKey);
        acknowledgement.put("reportType", reportType);
        acknowledgement.put("requestedBy", command.operatorUserId());
        if (persisted != null) {
            acknowledgement.put("acknowledgedBy", persisted.acknowledgedBy());
            acknowledgement.put("acknowledgedAt", persisted.acknowledgedAt() == null ? null : persisted.acknowledgedAt().toString());
            acknowledgement.put("note", persisted.note());
            acknowledgement.put("metadata", persisted.metadata());
        }
        return immutableNonNullMap(acknowledgement);
    }

    private static String buildReportKey(String reportType, ReviewQuery query) {
        ReviewQuery normalizedQuery = query == null
                ? new ReviewQuery(null, null, null, null, null, null, null, null, null, null)
                : query;
        return "report-" + sha256Hex(reportScopeValue(reportType).toLowerCase(Locale.ROOT)
                + "|" + reportScopeValue(normalizedQuery.reviewStatus())
                + "|" + reportScopeValue(normalizedQuery.cameraId())
                + "|" + reportScopeValue(normalizedQuery.zoneCode())
                + "|" + reportScopeValue(normalizedQuery.objectLabel())
                + "|" + reportScopeValue(normalizedQuery.recordEvidenceStatus())
                + "|" + reportScopeValue(normalizedQuery.converted())
                + "|" + reportScopeValue(normalizedQuery.inReviewCase())
                + "|" + reportScopeValue(normalizedQuery.reviewerUserId())
                + "|" + reportScopeValue(normalizedQuery.beginTime())
                + "|" + reportScopeValue(normalizedQuery.endTime()));
    }

    private static String reportScopeValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static boolean isUnreviewedBacklog(ReviewItemAggregate item) {
        return item != null && (item.reviewStatus() == null || STATUS_PENDING_REVIEW.equals(item.reviewStatus()));
    }

    private static String reportResponsibilityUnit(ReviewItemAggregate item) {
        if (item == null) {
            return "unknown";
        }
        Object configuredUnit = item.reviewData() == null ? null : item.reviewData().get("responsibilityUnit");
        return firstText(toText(configuredUnit), firstText(item.cameraId(), "unknown"));
    }

    private static boolean exportJobContainsAnyReviewItem(ReviewEvidenceExportJob job, Set<Long> reviewItemIds) {
        if (job == null || job.exportPackage() == null || reviewItemIds == null || reviewItemIds.isEmpty()) {
            return false;
        }
        List<Long> exportedIds = job.exportPackage().reviewItemIds();
        return exportedIds != null && exportedIds.stream().anyMatch(reviewItemIds::contains);
    }

    private static ReviewQuery reportQuery(ReviewReportCommand command) {
        ReviewQuery query = command.query();
        if (query == null) {
            return new ReviewQuery(null, null, null, null, null, null, null, null,
                    command.periodStart(), command.periodEnd());
        }
        return new ReviewQuery(
                query.reviewStatus(),
                query.cameraId(),
                query.zoneCode(),
                query.objectLabel(),
                query.recordEvidenceStatus(),
                query.converted(),
                query.inReviewCase(),
                query.reviewerUserId(),
                query.beginTime() == null ? command.periodStart() : query.beginTime(),
                query.endTime() == null ? command.periodEnd() : query.endTime()
        );
    }
}
