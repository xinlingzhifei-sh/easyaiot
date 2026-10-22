package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewAiSummaryRequest;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewItemSummaryContext;

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

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.reviewItemIdsFromTimeline;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Token;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.zones;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewEvidenceAudit.parseAuditNote;

/**
 * AI 摘要：案件摘要生成、输入脱敏、溯源信息、人工确认/驳回审计。摘要只是辅助材料，不构成处置结论。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewAiSummaryService {

    private final AlertReviewDependencies deps;
    private final ReviewCaseManagement reviewCaseManagement;
    private final ReviewItemQuery reviewItemQuery;

    ReviewAiSummaryService(AlertReviewDependencies deps, ReviewCaseManagement reviewCaseManagement, ReviewItemQuery reviewItemQuery) {
        this.deps = deps;
        this.reviewCaseManagement = reviewCaseManagement;
        this.reviewItemQuery = reviewItemQuery;
    }

    private static final String LOCAL_RULE_SUMMARY_MODEL = "local-rule-summary";
    private static final String REVIEW_AI_SUMMARY_PROVIDER_VERSION = "review-ai-provider-v1";
    private static final String REVIEW_AI_SUMMARY_PROMPT_VERSION = "review-ai-summary-prompt-v1";
    private static final String AI_SUMMARY_GENERATED_ACTION = "ai_summary_generated";
    private static final String AI_SUMMARY_CONFIRMED_ACTION = "ai_summary_confirmed";
    private static final String AI_SUMMARY_REJECTED_ACTION = "ai_summary_rejected";
    private static final String AI_SUMMARY_CONFIRMATION_CONFIRMED = "confirmed";
    private static final String AI_SUMMARY_CONFIRMATION_REJECTED = "rejected";

    public ReviewAiSummary summarizeReviewCase(Long reviewCaseId, Long operatorUserId) {
        requirePositive(reviewCaseId, "reviewCaseId");
        List<ReviewCaseTimelineItem> timeline = reviewCaseManagement.getReviewCaseTimeline(reviewCaseId);
        List<Long> reviewItemIds = reviewItemIdsFromTimeline(timeline);
        List<ReviewItemAggregate> reviewItems = reviewItemIds.stream()
                .map(id -> reviewItemQuery.withEventProjection(deps.reviewItemStore.findById(id).orElse(null)))
                .filter(Objects::nonNull)
                .toList();
        ReviewAiSummaryRequest rawSummaryRequest = new ReviewAiSummaryRequest(
                reviewCaseId,
                operatorUserId,
                reviewItemIds,
                timeline,
                reviewItems.stream().map(this::toSummaryContext).toList()
        );
        AiSummaryRedactionResult redaction = redactAiSummaryRequest(rawSummaryRequest);
        ReviewAiSummaryRequest summaryRequest = redaction.request();
        List<String> redactedFields = redaction.redactedFields();
        Optional<ReviewAiSummary> providerSummary = deps.reviewIntelligenceProvider.summarize(summaryRequest);
        if (providerSummary.isPresent()) {
            ReviewAiSummary enrichedSummary = withStructuredAiSummaryData(
                    providerSummary.get(),
                    reviewItems,
                    summaryRequest,
                    redactedFields
            );
            persistAiSummaryAudit(enrichedSummary, operatorUserId);
            return enrichedSummary;
        }
        List<String> keyFacts = new ArrayList<>();
        List<String> evidenceGaps = new ArrayList<>();
        List<String> recommendedActions = new ArrayList<>();
        for (ReviewItemAggregate item : reviewItems) {
            keyFacts.add("camera " + item.cameraId() + " zone " + item.zoneCode()
                    + " object " + item.objectLabel() + " status " + item.reviewStatus());
            if (RECORD_EVIDENCE_MISSING.equals(item.recordEvidenceStatus())
                    || RECORD_EVIDENCE_FAILED.equals(item.recordEvidenceStatus())) {
                evidenceGaps.add("review item " + item.reviewItemNo()
                        + " record evidence " + item.recordEvidenceStatus());
                recommendedActions.add("backfill record evidence for " + item.reviewItemNo());
            }
            if (STATUS_FALSE_POSITIVE.equals(item.reviewStatus())) {
                recommendedActions.add("review rule suggestion for " + item.reviewItemNo());
            }
            if (item.eventId() != null) {
                recommendedActions.add("track supervision event " + item.eventId());
            }
        }
        String summary = keyFacts.isEmpty()
                ? "No review items are available in this case."
                : "Case includes " + reviewItemIds.size() + " review item(s) across "
                        + keyFacts.stream().map(fact -> fact.split(" zone ")[0]).distinct().count()
                        + " camera view(s).";
        String title = "review case " + reviewCaseId;
        Map<String, Object> structuredData = new LinkedHashMap<>(buildStructuredAiSummaryData(
                reviewCaseId,
                title,
                summary,
                reviewItems,
                evidenceGaps,
                recommendedActions
        ));
        LocalDateTime generatedAt = LocalDateTime.now();
        String generatedBy = operatorUserId == null
                ? LOCAL_RULE_SUMMARY_MODEL
                : LOCAL_RULE_SUMMARY_MODEL + ":" + operatorUserId;
        structuredData.put("aiProvenance", buildAiSummaryProvenance(
                summaryRequest,
                generatedBy,
                generatedAt,
                LOCAL_RULE_SUMMARY_MODEL,
                redactedFields,
                List.of()
        ));
        ReviewAiSummary generatedSummary = new ReviewAiSummary(
                reviewCaseId,
                reviewItemIds,
                title,
                summary,
                List.copyOf(keyFacts),
                List.copyOf(evidenceGaps),
                List.copyOf(new LinkedHashSet<>(recommendedActions)),
                generatedAt,
                generatedBy,
                immutableNonNullMap(structuredData)
        );
        persistAiSummaryAudit(generatedSummary, operatorUserId);
        return generatedSummary;
    }

    public ReviewAiSummaryConfirmation confirmReviewCaseAiSummary(ReviewAiSummaryConfirmationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        String confirmationStatus = normalizeAiSummaryConfirmationStatus(command.confirmationStatus());
        List<ReviewCaseTimelineItem> timeline = reviewCaseManagement.getReviewCaseTimeline(command.reviewCaseId());
        ReviewCaseTimelineItem generatedAudit = latestCaseAudit(timeline, AI_SUMMARY_GENERATED_ACTION)
                .orElseThrow(() -> new IllegalStateException(
                        "AI summary generation audit is required before confirmation: " + command.reviewCaseId()));
        Map<String, Object> generatedNote = parseAuditNote(generatedAudit.actionNote());
        String promptHash = toText(generatedNote.get("promptHash"));
        String promptVersion = toText(generatedNote.get("promptVersion"));
        String summaryHash = toText(generatedNote.get("summaryHash"));
        Optional<ReviewCaseTimelineItem> latestConfirmation = latestAiSummaryConfirmation(
                timeline,
                generatedAudit.happenedAt(),
                promptHash
        );
        String previousStatus = latestConfirmation
                .map(ReviewCaseTimelineItem::actionNote)
                .map(ReviewEvidenceAudit::parseAuditNote)
                .map(note -> toText(note.get("humanConfirmationStatus")))
                .filter(AlertReviewValues::hasText)
                .orElse(null);
        if (latestConfirmation.isPresent() && confirmationStatus.equals(previousStatus)) {
            return buildAiSummaryConfirmation(
                    command,
                    confirmationStatus,
                    previousStatus,
                    promptHash,
                    promptVersion,
                    summaryHash,
                    latestConfirmation.get().happenedAt(),
                    true,
                    parseAuditNote(latestConfirmation.get().actionNote())
            );
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        Map<String, Object> metadata = aiSummaryConfirmationMetadata(
                command,
                confirmationStatus,
                previousStatus,
                generatedAudit,
                generatedNote,
                confirmedAt
        );
        deps.reviewItemStore.recordCaseAudit(
                command.reviewCaseId(),
                null,
                AI_SUMMARY_CONFIRMATION_CONFIRMED.equals(confirmationStatus)
                        ? AI_SUMMARY_CONFIRMED_ACTION
                        : AI_SUMMARY_REJECTED_ACTION,
                aiSummaryConfirmationAuditNote(metadata),
                command.operatorUserId(),
                confirmedAt,
                metadata
        );
        return buildAiSummaryConfirmation(
                command,
                confirmationStatus,
                previousStatus,
                promptHash,
                promptVersion,
                summaryHash,
                confirmedAt,
                false,
                metadata
        );
    }

    private ReviewItemSummaryContext toSummaryContext(ReviewItemAggregate item) {
        return new ReviewItemSummaryContext(
                item.id(),
                item.reviewItemNo(),
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                item.reviewStatus(),
                item.recordEvidenceStatus(),
                item.eventId(),
                item.reviewData()
        );
    }

    private record AiSummaryRedactionResult(ReviewAiSummaryRequest request,
                                            List<String> redactedFields) {
    }

    private AiSummaryRedactionResult redactAiSummaryRequest(ReviewAiSummaryRequest request) {
        if (request == null) {
            return new AiSummaryRedactionResult(null, List.of());
        }
        List<String> redactedFields = new ArrayList<>();
        List<ReviewCaseTimelineItem> redactedTimeline = new ArrayList<>();
        List<ReviewCaseTimelineItem> timeline = request.timeline() == null ? List.of() : request.timeline();
        for (int index = 0; index < timeline.size(); index++) {
            ReviewCaseTimelineItem item = timeline.get(index);
            if (item != null) {
                redactedTimeline.add(new ReviewCaseTimelineItem(
                        item.reviewCaseId(),
                        item.reviewItemId(),
                        item.cameraId(),
                        item.sourceAlertId(),
                        item.materialType(),
                        redactAiSummaryTimelineText(
                                item.materialUri(),
                                "timeline[" + index + "].materialUri",
                                redactedFields
                        ),
                        item.happenedAt(),
                        redactAiSummaryTimelineText(
                                item.actionNote(),
                                "timeline[" + index + "].actionNote",
                                redactedFields
                        )
                ));
            }
        }
        List<ReviewItemSummaryContext> redactedItems = new ArrayList<>();
        List<ReviewItemSummaryContext> items = request.items() == null ? List.of() : request.items();
        for (int index = 0; index < items.size(); index++) {
            ReviewItemSummaryContext item = items.get(index);
            redactedItems.add(new ReviewItemSummaryContext(
                    item.reviewItemId(),
                    item.reviewItemNo(),
                    item.cameraId(),
                    item.zoneCode(),
                    item.objectLabel(),
                    item.reviewStatus(),
                    item.recordEvidenceStatus(),
                    item.eventId(),
                    redactAiSummaryReviewData(
                            item.reviewData(),
                            "items[" + index + "].reviewData",
                            redactedFields
                    )
            ));
        }
        return new AiSummaryRedactionResult(
                new ReviewAiSummaryRequest(
                        request.reviewCaseId(),
                        request.operatorUserId(),
                        request.reviewItemIds(),
                        List.copyOf(redactedTimeline),
                        List.copyOf(redactedItems)
                ),
                List.copyOf(new LinkedHashSet<>(redactedFields))
        );
    }

    private String redactAiSummaryTimelineText(String value,
                                               String path,
                                               List<String> redactedFields) {
        if (shouldRedactAiSummaryValue(path, value)) {
            redactedFields.add(path);
            return deps.aiSummaryRedactionPolicy.getRedactedValue();
        }
        return value;
    }

    private Map<String, Object> redactAiSummaryReviewData(Map<String, Object> reviewData,
                                                         String path,
                                                         List<String> redactedFields) {
        if (reviewData == null || reviewData.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> redacted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : reviewData.entrySet()) {
            if (entry.getKey() != null) {
                redacted.put(entry.getKey(), redactAiSummaryValue(
                        entry.getValue(),
                        path + "." + entry.getKey(),
                        entry.getKey(),
                        redactedFields
                ));
            }
        }
        return immutableNonNullMap(redacted);
    }

    private Object redactAiSummaryValue(Object value,
                                        String path,
                                        String key,
                                        List<String> redactedFields) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    String childKey = String.valueOf(entry.getKey());
                    redacted.put(childKey, redactAiSummaryValue(
                            entry.getValue(),
                            path + "." + childKey,
                            childKey,
                            redactedFields
                    ));
                }
            }
            return immutableNonNullMap(redacted);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> redacted = new ArrayList<>();
            int index = 0;
            for (Object item : iterable) {
                redacted.add(redactAiSummaryValue(
                        item,
                        path + "[" + index + "]",
                        key,
                        redactedFields
                ));
                index++;
            }
            return List.copyOf(redacted);
        }
        if (shouldRedactAiSummaryValue(key, value)) {
            redactedFields.add(path);
            return deps.aiSummaryRedactionPolicy.getRedactedValue();
        }
        return value;
    }

    private boolean shouldRedactAiSummaryValue(String key, Object value) {
        return deps.aiSummaryRedactionPolicy.shouldRedact(key, value);
    }

    private ReviewAiSummary withStructuredAiSummaryData(ReviewAiSummary summary,
                                                        List<ReviewItemAggregate> reviewItems,
                                                        ReviewAiSummaryRequest request,
                                                        List<String> redactedFields) {
        List<String> outputRedactedFields = new ArrayList<>();
        String title = (String) redactAiSummaryValue(
                summary.title(), "title", "title", outputRedactedFields);
        String summaryText = (String) redactAiSummaryValue(
                summary.summary(), "summary", "summary", outputRedactedFields);
        List<String> keyFacts = redactAiSummaryOutputList(
                summary.keyFacts(), "keyFacts", outputRedactedFields);
        List<String> evidenceGaps = redactAiSummaryOutputList(
                summary.evidenceGaps(), "evidenceGaps", outputRedactedFields);
        List<String> recommendedActions = redactAiSummaryOutputList(
                summary.recommendedActions(), "recommendedActions", outputRedactedFields);
        Map<String, Object> providerStructuredData = redactAiSummaryReviewData(
                summary.structuredData(), "structuredData", outputRedactedFields);
        Map<String, Object> defaults = buildStructuredAiSummaryData(
                summary.reviewCaseId(),
                title,
                summaryText,
                reviewItems,
                evidenceGaps,
                recommendedActions
        );
        Map<String, Object> merged = new LinkedHashMap<>(defaults);
        if (!providerStructuredData.isEmpty()) {
            merged.putAll(providerStructuredData);
        }
        merged.put("aiProvenance", buildAiSummaryProvenance(
                request,
                summary.generatedBy(),
                summary.generatedAt(),
                firstText(summary.generatedBy(), "external-provider"),
                redactedFields,
                outputRedactedFields
        ));
        return new ReviewAiSummary(
                summary.reviewCaseId(),
                summary.reviewItemIds(),
                title,
                summaryText,
                keyFacts,
                evidenceGaps,
                recommendedActions,
                summary.generatedAt(),
                summary.generatedBy(),
                immutableNonNullMap(merged)
        );
    }

    private List<String> redactAiSummaryOutputList(List<String> values,
                                                   String path,
                                                   List<String> redactedFields) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> redacted = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            redacted.add((String) redactAiSummaryValue(
                    values.get(index),
                    path + "[" + index + "]",
                    path,
                    redactedFields
            ));
        }
        return List.copyOf(redacted);
    }

    static Map<String, Object> buildStructuredAiSummaryData(Long reviewCaseId,
                                                                    String title,
                                                                    String summary,
                                                                    List<ReviewItemAggregate> reviewItems,
                                                                    List<String> evidenceGaps,
                                                                    List<String> recommendedActions) {
        List<String> cameras = reviewItems.stream()
                .map(ReviewItemAggregate::cameraId)
                .filter(AlertReviewValues::hasText)
                .distinct()
                .toList();
        List<String> zones = reviewItems.stream()
                .map(ReviewItemAggregate::zoneCode)
                .filter(AlertReviewValues::hasText)
                .distinct()
                .toList();
        boolean hasEvidenceGap = evidenceGaps != null && !evidenceGaps.isEmpty();
        boolean hasEvent = reviewItems.stream().anyMatch(item -> item.eventId() != null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", hasText(title) ? title : "review case " + reviewCaseId);
        data.put("scene", String.join(" / ", cameras));
        data.put("zones", zones);
        data.put("shortSummary", summary);
        data.put("confidence", hasEvidenceGap ? 0.72D : 0.86D);
        data.put("concerns", evidenceGaps == null ? List.of() : List.copyOf(evidenceGaps));
        data.put("threatLevel", hasEvidenceGap ? "medium" : "low");
        data.put("responsibilityUnit", cameras.isEmpty() ? "unassigned" : cameras.get(0));
        data.put("handlingSuggestion", recommendedActions == null ? List.of() : List.copyOf(recommendedActions));
        data.put("disposalSuggestion", recommendedActions == null ? List.of() : List.copyOf(recommendedActions));
        data.put("evidenceGaps", evidenceGaps == null ? List.of() : List.copyOf(evidenceGaps));
        data.put("evidenceCompleteness", hasEvidenceGap ? "incomplete" : "complete");
        data.put("convertibleToEvent", !hasEvidenceGap || hasEvent);
        return Map.copyOf(data);
    }

    private Map<String, Object> buildAiSummaryProvenance(ReviewAiSummaryRequest request,
                                                         String generatedBy,
                                                         LocalDateTime generatedAt,
                                                         String model,
                                                         List<String> redactedFields,
                                                         List<String> outputRedactedFields) {
        Map<String, Object> provenance = new LinkedHashMap<>();
        provenance.put("provider", firstText(generatedBy, firstText(model, "unknown")));
        provenance.put("model", firstText(model, firstText(generatedBy, "unknown")));
        provenance.put("providerVersion", REVIEW_AI_SUMMARY_PROVIDER_VERSION);
        provenance.put("promptVersion", REVIEW_AI_SUMMARY_PROMPT_VERSION);
        provenance.put("redactionPolicyVersion", deps.aiSummaryRedactionPolicy.getPolicyVersion());
        provenance.put("promptHash", aiSummaryPromptHash(request));
        List<String> redactions = redactedFields == null ? List.of() : List.copyOf(redactedFields);
        provenance.put("redactionStatus", redactions.isEmpty() ? "not_required" : "applied");
        provenance.put("redactedFields", redactions);
        List<String> outputRedactions = outputRedactedFields == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(outputRedactedFields));
        provenance.put("outputRedacted", !outputRedactions.isEmpty());
        provenance.put("outputRedactedFields", outputRedactions);
        provenance.put("humanConfirmationStatus", "pending");
        provenance.put("requestedBy", request == null ? null : request.operatorUserId());
        provenance.put("generatedAt", generatedAt == null ? null : generatedAt.toString());
        provenance.put("generatedBy", generatedBy);
        provenance.put("reviewItemCount", request == null || request.reviewItemIds() == null
                ? 0
                : request.reviewItemIds().size());
        return immutableNonNullMap(provenance);
    }

    private static String aiSummaryPromptHash(ReviewAiSummaryRequest request) {
        return sha256Token(
                REVIEW_AI_SUMMARY_PROMPT_VERSION,
                request == null ? null : request.reviewCaseId(),
                request == null ? null : request.operatorUserId(),
                request == null ? List.of() : request.reviewItemIds(),
                request == null || request.timeline() == null ? 0 : request.timeline().size(),
                request == null || request.items() == null ? 0 : request.items().size()
        );
    }

    private void persistAiSummaryAudit(ReviewAiSummary summary, Long operatorUserId) {
        Map<String, Object> provenance = toStringObjectMap(summary == null || summary.structuredData() == null
                ? null
                : summary.structuredData().get("aiProvenance"));
        deps.reviewItemStore.recordCaseAudit(
                summary.reviewCaseId(),
                null,
                AI_SUMMARY_GENERATED_ACTION,
                aiSummaryAuditNote(summary, provenance),
                operatorUserId,
                summary.generatedAt(),
                aiSummaryAuditMetadata(summary, provenance)
        );
    }

    private static Map<String, Object> aiSummaryAuditMetadata(ReviewAiSummary summary,
                                                              Map<String, Object> provenance) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", 1);
        metadata.put("reviewCaseId", summary.reviewCaseId());
        metadata.put("reviewItemIds", summary.reviewItemIds());
        metadata.put("title", summary.title());
        metadata.put("summaryHash", sha256Token(summary.summary()));
        metadata.put("keyFactCount", summary.keyFacts() == null ? 0 : summary.keyFacts().size());
        metadata.put("evidenceGapCount", summary.evidenceGaps() == null ? 0 : summary.evidenceGaps().size());
        metadata.put("recommendedActionCount", summary.recommendedActions() == null ? 0 : summary.recommendedActions().size());
        metadata.put("generatedAt", summary.generatedAt() == null ? null : summary.generatedAt().toString());
        metadata.put("generatedBy", summary.generatedBy());
        metadata.put("provider", provenance.get("provider"));
        metadata.put("model", provenance.get("model"));
        metadata.put("providerVersion", provenance.get("providerVersion"));
        metadata.put("promptVersion", provenance.get("promptVersion"));
        metadata.put("redactionPolicyVersion", provenance.get("redactionPolicyVersion"));
        metadata.put("promptHash", provenance.get("promptHash"));
        metadata.put("redactionStatus", provenance.get("redactionStatus"));
        metadata.put("redactedFields", provenance.get("redactedFields"));
        metadata.put("humanConfirmationStatus", provenance.get("humanConfirmationStatus"));
        metadata.put("aiProvenance", provenance);
        return immutableNonNullMap(metadata);
    }

    private static String aiSummaryAuditNote(ReviewAiSummary summary, Map<String, Object> provenance) {
        List<String> values = new ArrayList<>();
        appendAuditNoteValue(values, "promptHash", provenance.get("promptHash"));
        appendAuditNoteValue(values, "promptVersion", provenance.get("promptVersion"));
        appendAuditNoteValue(values, "summaryHash", sha256Token(summary.summary()));
        appendAuditNoteValue(values, "provider", provenance.get("provider"));
        appendAuditNoteValue(values, "model", provenance.get("model"));
        appendAuditNoteValue(values, "providerVersion", provenance.get("providerVersion"));
        appendAuditNoteValue(values, "redactionPolicyVersion", provenance.get("redactionPolicyVersion"));
        appendAuditNoteValue(values, "humanConfirmationStatus", provenance.get("humanConfirmationStatus"));
        appendAuditNoteValue(values, "redactionStatus", provenance.get("redactionStatus"));
        return String.join("; ", values);
    }

    private static void appendAuditNoteValue(List<String> values, String key, Object value) {
        if (value != null) {
            values.add(key + "=" + value);
        }
    }

    private static String normalizeAiSummaryConfirmationStatus(String rawStatus) {
        requireText(rawStatus, "confirmationStatus");
        String normalized = rawStatus.trim().toLowerCase(Locale.ROOT);
        if ("confirm".equals(normalized)
                || "confirmed".equals(normalized)
                || "accept".equals(normalized)
                || "accepted".equals(normalized)) {
            return AI_SUMMARY_CONFIRMATION_CONFIRMED;
        }
        if ("reject".equals(normalized) || "rejected".equals(normalized)) {
            return AI_SUMMARY_CONFIRMATION_REJECTED;
        }
        throw new IllegalArgumentException("confirmationStatus must be confirmed or rejected");
    }

    private static Optional<ReviewCaseTimelineItem> latestCaseAudit(List<ReviewCaseTimelineItem> timeline,
                                                                    String actionType) {
        return timeline == null
                ? Optional.empty()
                : timeline.stream()
                        .filter(item -> "case_audit".equals(item.materialType()))
                        .filter(item -> Objects.equals(actionType, item.materialUri()))
                        .max(Comparator.comparing(
                                ReviewCaseTimelineItem::happenedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())
                        ));
    }

    private static Optional<ReviewCaseTimelineItem> latestAiSummaryConfirmation(List<ReviewCaseTimelineItem> timeline,
                                                                                LocalDateTime generatedAt,
                                                                                String promptHash) {
        if (timeline == null) {
            return Optional.empty();
        }
        return timeline.stream()
                .filter(item -> "case_audit".equals(item.materialType()))
                .filter(item -> AI_SUMMARY_CONFIRMED_ACTION.equals(item.materialUri())
                        || AI_SUMMARY_REJECTED_ACTION.equals(item.materialUri()))
                .filter(item -> generatedAt == null
                        || item.happenedAt() == null
                        || !item.happenedAt().isBefore(generatedAt))
                .filter(item -> {
                    if (!hasText(promptHash)) {
                        return true;
                    }
                    return Objects.equals(promptHash, toText(parseAuditNote(item.actionNote()).get("promptHash")));
                })
                .max(Comparator.comparing(
                        ReviewCaseTimelineItem::happenedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())
                ));
    }

    private static Map<String, Object> aiSummaryConfirmationMetadata(ReviewAiSummaryConfirmationCommand command,
                                                                     String confirmationStatus,
                                                                     String previousStatus,
                                                                     ReviewCaseTimelineItem generatedAudit,
                                                                     Map<String, Object> generatedNote,
                                                                     LocalDateTime confirmedAt) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", 1);
        metadata.put("reviewCaseId", command.reviewCaseId());
        metadata.put("humanConfirmationStatus", confirmationStatus);
        metadata.put("previousHumanConfirmationStatus", previousStatus);
        metadata.put("promptHash", generatedNote.get("promptHash"));
        metadata.put("promptVersion", generatedNote.get("promptVersion"));
        metadata.put("summaryHash", generatedNote.get("summaryHash"));
        metadata.put("provider", generatedNote.get("provider"));
        metadata.put("model", generatedNote.get("model"));
        metadata.put("providerVersion", generatedNote.get("providerVersion"));
        metadata.put("redactionStatus", generatedNote.get("redactionStatus"));
        metadata.put("sourceActionType", generatedAudit.materialUri());
        metadata.put("sourceGeneratedAt", generatedAudit.happenedAt() == null
                ? null
                : generatedAudit.happenedAt().toString());
        metadata.put("operatorUserId", command.operatorUserId());
        metadata.put("notes", command.notes());
        metadata.put("confirmedAt", confirmedAt == null ? null : confirmedAt.toString());
        return immutableNonNullMap(metadata);
    }

    private static String aiSummaryConfirmationAuditNote(Map<String, Object> metadata) {
        List<String> values = new ArrayList<>();
        appendAuditNoteValue(values, "humanConfirmationStatus", metadata.get("humanConfirmationStatus"));
        appendAuditNoteValue(values, "previousHumanConfirmationStatus", metadata.get("previousHumanConfirmationStatus"));
        appendAuditNoteValue(values, "promptHash", metadata.get("promptHash"));
        appendAuditNoteValue(values, "promptVersion", metadata.get("promptVersion"));
        appendAuditNoteValue(values, "summaryHash", metadata.get("summaryHash"));
        appendAuditNoteValue(values, "operatorUserId", metadata.get("operatorUserId"));
        appendAuditNoteValue(values, "redactionStatus", metadata.get("redactionStatus"));
        return String.join("; ", values);
    }

    private static ReviewAiSummaryConfirmation buildAiSummaryConfirmation(ReviewAiSummaryConfirmationCommand command,
                                                                          String confirmationStatus,
                                                                          String previousStatus,
                                                                          String promptHash,
                                                                          String promptVersion,
                                                                          String summaryHash,
                                                                          LocalDateTime confirmedAt,
                                                                          boolean duplicate,
                                                                          Map<String, Object> metadata) {
        return new ReviewAiSummaryConfirmation(
                command.reviewCaseId(),
                confirmationStatus,
                previousStatus,
                promptHash,
                promptVersion,
                summaryHash,
                command.operatorUserId(),
                command.notes(),
                confirmedAt,
                duplicate,
                immutableNonNullMap(metadata)
        );
    }
}
