package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewSemanticSearchCandidate;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewSemanticSearchRequest;

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
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.boundedPositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstNonNull;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.matchesText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.roundRate;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Hex;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Token;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toInteger;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLocalDateTime;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLong;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;

/**
 * 语义检索：语义检索与索引队列、索引评估、语义触发评估与人工确认。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewSemanticIntelligence {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;

    ReviewSemanticIntelligence(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
    }

    private static final int DEFAULT_SEMANTIC_WORKER_LIMIT = 50;
    private static final int MAX_SEMANTIC_WORKER_LIMIT = 200;
    private static final String LOCAL_EMBEDDING_MODEL = "yfeieye-review-local-v1";
    private static final String SEMANTIC_TRIGGER_EVALUATED_ACTION = "semantic_trigger_evaluated";
    private static final String SEMANTIC_TRIGGER_CONFIRMED_ACTION = "semantic_trigger_confirmed";
    private static final String SEMANTIC_TRIGGER_REJECTED_ACTION = "semantic_trigger_rejected";
    private static final String SEMANTIC_TRIGGER_SCHEMA_VERSION = "semantic-trigger-evaluation-v1";
    private static final String SEMANTIC_TRIGGER_INPUT_VERSION = "semantic-trigger-input-v1";
    private static final long SEMANTIC_INDEX_CLAIM_LEASE_MINUTES = 5L;
    private static final long SEMANTIC_INDEX_RETRY_BASE_SECONDS = 30L;
    private static final long SEMANTIC_INDEX_RETRY_MAX_SECONDS = 3600L;
    private static final Set<String> SEMANTIC_TRIGGER_ACTIONS = Set.of(
            "notification", "sub_label", "attribute"
    );
    private static final Pattern SEMANTIC_TRIGGER_EVALUATION_ID = Pattern.compile(
            "sem-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    );

    public List<ReviewSemanticHit> semanticSearch(ReviewSemanticSearchCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.query(), "query");
        return semanticSearch(command, semanticSearchSnapshot(command.filters()).candidates());
    }

    private List<ReviewSemanticHit> semanticSearch(ReviewSemanticSearchCommand command,
                                                   List<ReviewSemanticSearchCandidate> candidates) {
        List<String> terms = tokenize(command.query());
        int limit = command.limit() == null || command.limit() <= 0 ? 20 : command.limit();
        Optional<List<ReviewSemanticHit>> providerHits = deps.reviewIntelligenceProvider.semanticSearch(
                new ReviewSemanticSearchRequest(command.query(), command.filters(), limit, candidates)
        );
        if (providerHits.isPresent()) {
            return providerHits.get().stream().limit(limit).toList();
        }
        return candidates.stream()
                .map(candidate -> toSemanticHit(candidate.item(), candidate.document(), terms))
                .filter(hit -> hit.score() > 0)
                .sorted(Comparator
                        .comparing(ReviewSemanticHit::score, Comparator.reverseOrder())
                        .thenComparing(hit -> hit.item().lastAlertTime(), Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    public List<ReviewSemanticIndexEntry> reindexSemanticIndex(ReviewQuery query) {
        LocalDateTime indexedAt = LocalDateTime.now();
        String indexGenerationId = newSemanticIndexGenerationId();
        return reviewItemQuery.listWorkbench(query).stream()
                .map(item -> reindexSemanticItem(item, indexedAt, indexGenerationId))
                .toList();
    }

    private ReviewSemanticIndexEntry reindexSemanticItem(ReviewItemAggregate item,
                                                          LocalDateTime indexedAt,
                                                          String indexGenerationId) {
        String document = buildSearchDocument(item);
        ReviewSemanticIndexEntry queued = deps.reviewItemStore.queueSemanticIndex(
                item,
                document,
                semanticEmbeddingKey(item),
                LOCAL_EMBEDDING_MODEL,
                indexGenerationId,
                indexedAt
        );
        if (!SEMANTIC_INDEX_PENDING.equals(queued.indexStatus())
                || !Objects.equals(indexGenerationId, queued.indexGenerationId())) {
            return queued;
        }
        String claimToken = UUID.randomUUID().toString();
        List<ReviewSemanticIndexEntry> claimed = deps.reviewItemStore.claimSemanticIndex(
                List.of(item.id()),
                1,
                claimToken,
                indexedAt,
                indexedAt.plusMinutes(SEMANTIC_INDEX_CLAIM_LEASE_MINUTES)
        );
        if (claimed.isEmpty()) {
            return queued;
        }
        try {
            return deps.reviewItemStore.completeSemanticIndexClaim(
                    item,
                    document,
                    semanticEmbeddingKey(item),
                    LOCAL_EMBEDDING_MODEL,
                    semanticEmbeddingVectorHash(document),
                    SEMANTIC_INDEX_INDEXED,
                    0,
                    null,
                    indexedAt,
                    indexGenerationId,
                    null,
                    claimToken
            );
        } catch (RuntimeException ex) {
            if (isSemanticIndexClaimConflict(ex)) {
                return queued;
            }
            throw ex;
        }
    }

    public ReviewSemanticReindexJob queueSemanticReindex(ReviewSemanticReindexCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDateTime queuedAt = LocalDateTime.now();
        String indexGenerationId = newSemanticIndexGenerationId();
        List<ReviewItemAggregate> items = reviewItemQuery.listWorkbench(command.query());
        List<Long> queuedIds = new ArrayList<>();
        for (ReviewItemAggregate item : items) {
            ReviewSemanticIndexEntry queued = deps.reviewItemStore.queueSemanticIndex(
                    item,
                    buildSearchDocument(item),
                    semanticEmbeddingKey(item),
                    LOCAL_EMBEDDING_MODEL,
                    indexGenerationId,
                    queuedAt
            );
            if (SEMANTIC_INDEX_PENDING.equals(queued.indexStatus())
                    && Objects.equals(indexGenerationId, queued.indexGenerationId())) {
                queuedIds.add(item.id());
            }
        }
        String status = queuedIds.size() == items.size()
                ? "queued"
                : queuedIds.isEmpty() ? "deferred" : "partially_queued";
        return new ReviewSemanticReindexJob(
                "RSJ-" + UUID.randomUUID(),
                status,
                List.copyOf(queuedIds),
                queuedAt,
                command.operatorUserId()
        );
    }

    public ReviewSemanticWorkerRun processSemanticIndexQueue(ReviewSemanticWorkerCommand command) {
        Objects.requireNonNull(command, "command");
        int limit = boundedPositive(command.maxItems(), DEFAULT_SEMANTIC_WORKER_LIMIT, MAX_SEMANTIC_WORKER_LIMIT);
        List<ReviewItemAggregate> items = reviewItemQuery.listWorkbench(command.query());
        LocalDateTime processedAt = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        List<ReviewSemanticIndexEntry> claimedEntries = deps.reviewItemStore.claimSemanticIndex(
                items.stream().map(ReviewItemAggregate::id).toList(),
                limit,
                claimToken,
                processedAt,
                processedAt.plusMinutes(SEMANTIC_INDEX_CLAIM_LEASE_MINUTES)
        );
        Map<Long, ReviewItemAggregate> itemById = items.stream()
                .collect(Collectors.toMap(ReviewItemAggregate::id, Function.identity()));
        List<Long> processedIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();
        List<Long> deferredIds = new ArrayList<>();
        for (ReviewSemanticIndexEntry current : claimedEntries) {
            ReviewItemAggregate item = itemById.get(current.reviewItemId());
            if (item == null) {
                continue;
            }
            String generationId = firstText(current.indexGenerationId(), newSemanticIndexGenerationId());
            try {
                String document = buildSearchDocument(item);
                deps.reviewItemStore.completeSemanticIndexClaim(
                        item,
                        document,
                        semanticEmbeddingKey(item),
                        LOCAL_EMBEDDING_MODEL,
                        semanticEmbeddingVectorHash(document),
                        SEMANTIC_INDEX_INDEXED,
                        0,
                        null,
                        processedAt,
                        generationId,
                        null,
                        claimToken
                );
                processedIds.add(item.id());
            } catch (RuntimeException ex) {
                if (isSemanticIndexClaimConflict(ex)) {
                    deferredIds.add(item.id());
                    continue;
                }
                int retryCount = (current == null || current.retryCount() == null ? 0 : current.retryCount()) + 1;
                try {
                    deps.reviewItemStore.completeSemanticIndexClaim(
                            item,
                            current.document(),
                            semanticEmbeddingKey(item),
                            LOCAL_EMBEDDING_MODEL,
                            current.embeddingVectorHash(),
                            SEMANTIC_INDEX_FAILED,
                            retryCount,
                            ex.getMessage(),
                            null,
                            generationId,
                            semanticIndexNextRetryAt(processedAt, retryCount),
                            claimToken
                    );
                } catch (RuntimeException completionEx) {
                    if (isSemanticIndexClaimConflict(completionEx)) {
                        deferredIds.add(item.id());
                        continue;
                    }
                    throw completionEx;
                }
                failedIds.add(item.id());
            }
        }
        ReviewSemanticIndexEvaluation evaluation = evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(command.query(), command.operatorUserId()));
        String status = semanticWorkerStatus(
                processedIds.size(),
                failedIds.size(),
                deferredIds.size(),
                evaluation.staleReviewItemIds().size());
        return new ReviewSemanticWorkerRun(
                status,
                claimedEntries.size(),
                processedIds.size(),
                failedIds.size(),
                evaluation.staleReviewItemIds().size(),
                evaluation.rebuildProgressRate(),
                List.copyOf(processedIds),
                List.copyOf(failedIds),
                processedAt,
                command.operatorUserId()
        );
    }

    public ReviewSemanticIndexEvaluation evaluateSemanticIndex(ReviewSemanticIndexEvaluationCommand command) {
        Objects.requireNonNull(command, "command");
        List<ReviewItemAggregate> items = reviewItemQuery.listWorkbench(command.query());
        Map<Long, ReviewSemanticIndexEntry> indexByItemId = semanticIndexByItemId(command.query());
        int pending = 0;
        int indexed = 0;
        int failed = 0;
        int latestIndexVersion = 0;
        List<Long> staleIds = new ArrayList<>();
        for (ReviewItemAggregate item : items) {
            ReviewSemanticIndexEntry entry = indexByItemId.get(item.id());
            if (entry == null) {
                staleIds.add(item.id());
                continue;
            }
            if (SEMANTIC_INDEX_PENDING.equals(entry.indexStatus())
                    || SEMANTIC_INDEX_PROCESSING.equals(entry.indexStatus())) {
                pending++;
                staleIds.add(item.id());
            } else if (SEMANTIC_INDEX_FAILED.equals(entry.indexStatus())) {
                failed++;
                staleIds.add(item.id());
            } else if (SEMANTIC_INDEX_INDEXED.equals(entry.indexStatus())) {
                indexed++;
                if (!Objects.equals(item.lastAlertTime(), entry.lastAlertTime())) {
                    staleIds.add(item.id());
                }
            } else {
                staleIds.add(item.id());
            }
            if (entry != null && entry.indexVersion() != null) {
                latestIndexVersion = Math.max(latestIndexVersion, entry.indexVersion());
            }
        }
        List<String> actions = new ArrayList<>();
        if (pending > 0) {
            actions.add("process_pending_semantic_index");
        }
        if (failed > 0) {
            actions.add("retry_failed_semantic_index");
        }
        if (!staleIds.isEmpty() && pending == 0 && failed == 0) {
            actions.add("reindex_stale_semantic_index");
        }
        String backlogAlarmLevel = semanticBacklogAlarmLevel(pending, failed, staleIds.size());
        if (!"none".equals(backlogAlarmLevel)) {
            actions.add("inspect_semantic_index_backlog_alarm");
        }
        return new ReviewSemanticIndexEvaluation(
                items.size(),
                pending,
                indexed,
                failed,
                roundRate(indexed, items.size()),
                List.copyOf(staleIds),
                List.copyOf(actions),
                roundRate(indexed, items.size()),
                backlogAlarmLevel,
                latestIndexVersion,
                LocalDateTime.now(),
                command.operatorUserId()
        );
    }

    private Map<Long, ReviewSemanticIndexEntry> semanticIndexByItemId(ReviewQuery query) {
        Map<Long, ReviewSemanticIndexEntry> indexByItemId = new LinkedHashMap<>();
        for (ReviewSemanticIndexEntry entry : deps.reviewItemStore.listSemanticIndex(query)) {
            indexByItemId.put(entry.reviewItemId(), entry);
        }
        return indexByItemId;
    }

    private static String newSemanticIndexGenerationId() {
        return "sig-" + UUID.randomUUID();
    }

    private static LocalDateTime semanticIndexNextRetryAt(LocalDateTime failedAt, int retryCount) {
        int exponent = Math.max(0, Math.min(retryCount - 1, 7));
        long delaySeconds = Math.min(
                SEMANTIC_INDEX_RETRY_MAX_SECONDS,
                SEMANTIC_INDEX_RETRY_BASE_SECONDS * (1L << exponent)
        );
        return failedAt.plusSeconds(delaySeconds);
    }

    private static boolean isSemanticIndexClaimConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.startsWith("semantic_index_claim_conflict:")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String semanticWorkerStatus(int processedCount,
                                               int failedCount,
                                               int deferredCount,
                                               int remainingBacklogCount) {
        if (failedCount > 0 && processedCount > 0) {
            return "partial_failed";
        }
        if (failedCount > 0) {
            return "failed";
        }
        if (deferredCount > 0 && processedCount > 0) {
            return "partial_deferred";
        }
        if (deferredCount > 0) {
            return "deferred";
        }
        if (remainingBacklogCount > 0) {
            return "partial";
        }
        return "completed";
    }

    private static String semanticBacklogAlarmLevel(int pendingCount, int failedCount, int staleCount) {
        if (failedCount > 0) {
            return "critical";
        }
        if (pendingCount > 0 || staleCount > 0) {
            return "warning";
        }
        return "none";
    }

    public ReviewSemanticTriggerResult evaluateSemanticTrigger(ReviewSemanticTriggerCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.triggerName(), "triggerName");
        requireText(command.data(), "data");
        if (command.triggerName().length() > 128) {
            throw new IllegalArgumentException("triggerName must not exceed 128 characters");
        }
        if (command.data().length() > 2000) {
            throw new IllegalArgumentException("data must not exceed 2000 characters");
        }
        if (command.threshold() != null
                && (!Double.isFinite(command.threshold())
                || command.threshold() < 0D
                || command.threshold() > 1D)) {
            throw new IllegalArgumentException("threshold must be between 0 and 1");
        }
        ReviewQuery filters = semanticTriggerFilters(command);
        int limit = command.actions() == null || command.actions().isEmpty() ? 20 : 50;
        SemanticSearchSnapshot semanticSnapshot = semanticSearchSnapshot(filters);
        List<ReviewSemanticHit> hits = semanticSearch(
                new ReviewSemanticSearchCommand(command.data(), filters, limit),
                semanticSnapshot.candidates())
                .stream()
                .filter(hit -> matchesSemanticTriggerThreshold(hit, command.threshold()))
                .toList();
        List<String> actions = command.actions() == null || command.actions().isEmpty()
                ? List.of("notification")
                : command.actions().stream()
                .filter(AlertReviewValues::hasText)
                .map(String::trim)
                .distinct()
                .limit(11)
                .toList();
        if (actions.isEmpty() || actions.size() > 10) {
            throw new IllegalArgumentException("actions must contain between 1 and 10 non-blank values");
        }
        if (actions.stream().anyMatch(action -> action.length() > 64 || !SEMANTIC_TRIGGER_ACTIONS.contains(action))) {
            throw new IllegalArgumentException(
                    "actions must contain only notification, sub_label, or attribute");
        }
        String inputHash = semanticTriggerInputHash(command, filters, actions, semanticSnapshot);
        LocalDateTime evaluatedAt = LocalDateTime.now();
        String evaluationId = "sem-" + UUID.randomUUID();
        List<Map<String, Object>> actionPayloads = new ArrayList<>();
        List<Map<String, Object>> actionPreviews = new ArrayList<>();
        for (ReviewSemanticHit hit : hits) {
            for (String action : actions) {
                Map<String, Object> actionPayload = buildSemanticTriggerAction(command, hit, action, evaluatedAt);
                actionPayloads.add(actionPayload);
                actionPreviews.add(buildSemanticTriggerActionPreview(actionPayload));
            }
        }
        ReviewSemanticTriggerResult result = new ReviewSemanticTriggerResult(
                command.triggerName(),
                command.triggerType(),
                command.data(),
                hits.stream().map(hit -> hit.item().id()).toList(),
                List.copyOf(actionPayloads),
                evaluatedAt,
                SEMANTIC_TRIGGER_INPUT_VERSION,
                inputHash,
                semanticSnapshot.indexGenerationId(),
                semanticSnapshot.latestIndexVersion(),
                hits.stream().map(hit -> buildSemanticTriggerHitExplanation(
                        command,
                        hit,
                        semanticSnapshot.indexProvenanceByItemId().get(hit.item().id()),
                        semanticSnapshot.indexGenerationId())).toList(),
                List.copyOf(actionPreviews),
                "pending",
                evaluationId,
                null,
                null,
                false
        );
        Long reviewItemId = result.matchedReviewItemIds().stream().findFirst().orElse(null);
        deps.reviewItemStore.recordSemanticTriggerEvaluation(
                reviewItemId,
                "evaluationId=" + evaluationId + "; humanConfirmationStatus=pending; previewOnly=true",
                command.operatorUserId(),
                evaluatedAt,
                semanticTriggerEvaluationMetadata(result, command.operatorUserId())
        );
        return result;
    }

    public ReviewSemanticTriggerResult getSemanticTrigger(String evaluationId) {
        String normalizedEvaluationId = normalizeSemanticTriggerEvaluationId(evaluationId);
        List<ReviewSemanticTriggerAuditRecord> audits = deps.reviewItemStore.listSemanticTriggerAudits(
                normalizedEvaluationId);
        ReviewSemanticTriggerAuditRecord pendingAudit = semanticTriggerPendingAudit(
                audits,
                normalizedEvaluationId);
        return semanticTriggerDecisionAudit(audits, normalizedEvaluationId)
                .map(decision -> semanticTriggerResultFromAudits(
                        normalizedEvaluationId,
                        pendingAudit,
                        decision,
                        false
                ))
                .orElseGet(() -> semanticTriggerPendingResult(normalizedEvaluationId, pendingAudit));
    }

    public ReviewSemanticTriggerResult confirmSemanticTrigger(ReviewSemanticTriggerConfirmationCommand command) {
        Objects.requireNonNull(command, "command");
        String evaluationId = normalizeSemanticTriggerEvaluationId(command.evaluationId());
        String confirmationStatus = normalizeSemanticTriggerConfirmationStatus(command.confirmationStatus());
        requirePositive(command.operatorUserId(), "operatorUserId");

        List<ReviewSemanticTriggerAuditRecord> existingAudits = deps.reviewItemStore.listSemanticTriggerAudits(evaluationId);
        ReviewSemanticTriggerAuditRecord pendingAudit = semanticTriggerPendingAudit(existingAudits, evaluationId);
        Optional<ReviewSemanticTriggerAuditRecord> existingDecision = semanticTriggerDecisionAudit(
                existingAudits,
                evaluationId);
        if (existingDecision.isPresent()) {
            return resolveExistingSemanticTriggerDecision(
                    evaluationId,
                    confirmationStatus,
                    pendingAudit,
                    existingDecision.get()
            );
        }

        LocalDateTime confirmedAt = LocalDateTime.now();
        String actionType = "confirmed".equals(confirmationStatus)
                ? SEMANTIC_TRIGGER_CONFIRMED_ACTION
                : SEMANTIC_TRIGGER_REJECTED_ACTION;
        Map<String, Object> decisionMetadata = semanticTriggerDecisionMetadata(
                evaluationId,
                confirmationStatus,
                command.operatorUserId(),
                confirmedAt,
                command.notes(),
                pendingAudit.metadata()
        );
        boolean inserted = deps.reviewItemStore.recordSemanticTriggerDecision(
                evaluationId,
                actionType,
                semanticTriggerDecisionAuditNote(decisionMetadata),
                command.operatorUserId(),
                confirmedAt,
                decisionMetadata
        );
        List<ReviewSemanticTriggerAuditRecord> persistedAudits = deps.reviewItemStore.listSemanticTriggerAudits(evaluationId);
        ReviewSemanticTriggerAuditRecord persistedDecision = semanticTriggerDecisionAudit(
                persistedAudits,
                evaluationId)
                .orElseThrow(() -> new IllegalStateException("semantic_trigger_decision_not_persisted"));
        ReviewSemanticTriggerResult result = semanticTriggerResultFromAudits(
                evaluationId,
                pendingAudit,
                persistedDecision,
                !inserted
        );
        if (!confirmationStatus.equals(result.humanConfirmationStatus())) {
            throw new IllegalStateException("semantic_trigger_already_decided: "
                    + result.humanConfirmationStatus());
        }
        return result;
    }

    private ReviewQuery semanticTriggerFilters(ReviewSemanticTriggerCommand command) {
        ReviewQuery filters = command.filters();
        if (filters == null) {
            return new ReviewQuery(null, command.cameraId(), null, null);
        }
        if (!hasText(command.cameraId()) || matchesText(command.cameraId(), filters.cameraId())) {
            return filters;
        }
        return new ReviewQuery(
                filters.reviewStatus(),
                command.cameraId(),
                filters.zoneCode(),
                filters.objectLabel(),
                filters.recordEvidenceStatus(),
                filters.converted(),
                filters.inReviewCase(),
                filters.reviewerUserId(),
                filters.beginTime(),
                filters.endTime()
        );
    }

    private static boolean matchesSemanticTriggerThreshold(ReviewSemanticHit hit, Double threshold) {
        if (threshold == null || threshold <= 0D) {
            return hit.score() > 0D;
        }
        return hit.score() >= threshold;
    }

    private static Map<String, Object> buildSemanticTriggerAction(ReviewSemanticTriggerCommand command,
                                                                  ReviewSemanticHit hit,
                                                                  String action,
                                                                  LocalDateTime evaluatedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", action);
        payload.put("triggerName", command.triggerName());
        payload.put("triggerType", command.triggerType());
        payload.put("data", command.data());
        payload.put("reviewItemId", hit.item().id());
        payload.put("cameraId", hit.item().cameraId());
        payload.put("score", hit.score());
        payload.put("matchedTerms", hit.matchedTerms());
        payload.put("evaluatedAt", evaluatedAt.toString());
        payload.put("requiresHumanConfirmation", true);
        payload.put("humanConfirmationStatus", "pending");
        if ("sub_label".equals(action)) {
            payload.put("value", command.triggerName());
        }
        if ("attribute".equals(action)) {
            payload.put("key", command.triggerName());
            payload.put("value", command.data());
        }
        return immutableNonNullMap(payload);
    }

    private static Map<String, Object> buildSemanticTriggerHitExplanation(ReviewSemanticTriggerCommand command,
                                                                          ReviewSemanticHit hit,
                                                                          ReviewSemanticIndexEntry indexEntry,
                                                                          String indexGenerationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("triggerName", command.triggerName());
        payload.put("triggerType", command.triggerType());
        payload.put("data", command.data());
        payload.put("threshold", command.threshold());
        payload.put("reviewItemId", hit.item().id());
        payload.put("cameraId", hit.item().cameraId());
        payload.put("zoneCode", hit.item().zoneCode());
        payload.put("objectLabel", hit.item().objectLabel());
        payload.put("score", hit.score());
        payload.put("matchedTerms", hit.matchedTerms());
        payload.put("snippet", hit.snippet());
        payload.put("sourceAlertIds", hit.item().sourceAlertIds());
        payload.put("indexVersion", indexEntry == null || indexEntry.indexVersion() == null
                ? 0
                : indexEntry.indexVersion());
        payload.put("indexGenerationId", indexGenerationId);
        payload.put("indexStatus", indexEntry == null ? "live_fallback" : indexEntry.indexStatus());
        payload.put("indexedAt", indexEntry == null || indexEntry.indexedAt() == null
                ? null
                : indexEntry.indexedAt().toString());
        payload.put("embeddingModel", indexEntry == null ? null : indexEntry.embeddingModel());
        payload.put("embeddingVectorHash", indexEntry == null ? null : indexEntry.embeddingVectorHash());
        payload.put("correlationId", hit.item().reviewData() == null
                ? null
                : hit.item().reviewData().get("correlationId"));
        return immutableNonNullMap(payload);
    }

    private static Map<String, Object> buildSemanticTriggerActionPreview(Map<String, Object> actionPayload) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("action", actionPayload.get("action"));
        preview.put("triggerName", actionPayload.get("triggerName"));
        preview.put("reviewItemId", actionPayload.get("reviewItemId"));
        preview.put("cameraId", actionPayload.get("cameraId"));
        preview.put("value", actionPayload.get("value"));
        preview.put("key", actionPayload.get("key"));
        preview.put("previewOnly", true);
        preview.put("requiresHumanConfirmation", actionPayload.get("requiresHumanConfirmation"));
        preview.put("humanConfirmationStatus", actionPayload.get("humanConfirmationStatus"));
        return immutableNonNullMap(preview);
    }

    private static Map<String, Object> semanticTriggerEvaluationMetadata(ReviewSemanticTriggerResult result,
                                                                          Long operatorUserId) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", SEMANTIC_TRIGGER_SCHEMA_VERSION);
        metadata.put("evaluationId", result.evaluationId());
        metadata.put("triggerName", result.triggerName());
        metadata.put("triggerType", result.triggerType());
        metadata.put("data", result.data());
        metadata.put("matchedReviewItemIds", result.matchedReviewItemIds());
        metadata.put("actionPayloads", result.actionPayloads());
        metadata.put("hitExplanations", result.hitExplanations());
        metadata.put("actionPreviews", result.actionPreviews());
        metadata.put("evaluatedAt", result.evaluatedAt().toString());
        metadata.put("inputVersion", result.inputVersion());
        metadata.put("inputHash", result.inputHash());
        metadata.put("indexGenerationId", result.indexGenerationId());
        metadata.put("latestIndexVersion", result.latestIndexVersion());
        metadata.put("evaluatedBy", operatorUserId);
        metadata.put("humanConfirmationStatus", "pending");
        metadata.put("previewOnly", true);
        return immutableNonNullMap(metadata);
    }

    private static Map<String, Object> semanticTriggerDecisionMetadata(String evaluationId,
                                                                        String confirmationStatus,
                                                                        Long operatorUserId,
                                                                        LocalDateTime confirmedAt,
                                                                        String notes,
                                                                        Map<String, Object> evaluationMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("schemaVersion", SEMANTIC_TRIGGER_SCHEMA_VERSION);
        metadata.put("evaluationId", evaluationId);
        metadata.put("inputVersion", firstText(
                toText(evaluationMetadata.get("inputVersion")),
                SEMANTIC_TRIGGER_INPUT_VERSION));
        metadata.put("inputHash", toText(evaluationMetadata.get("inputHash")));
        metadata.put("indexGenerationId", toText(evaluationMetadata.get("indexGenerationId")));
        metadata.put("latestIndexVersion", firstNonNull(
                toInteger(evaluationMetadata.get("latestIndexVersion")),
                0));
        metadata.put("humanConfirmationStatus", confirmationStatus);
        metadata.put("confirmedBy", operatorUserId);
        metadata.put("confirmedAt", confirmedAt.toString());
        metadata.put("notes", hasText(notes) ? notes.trim() : null);
        metadata.put("previewOnly", true);
        metadata.put("actionsExecuted", false);
        return immutableNonNullMap(metadata);
    }

    private static String semanticTriggerDecisionAuditNote(Map<String, Object> metadata) {
        List<String> values = new ArrayList<>();
        values.add("evaluationId=" + metadata.get("evaluationId"));
        values.add("humanConfirmationStatus=" + metadata.get("humanConfirmationStatus"));
        values.add("confirmedBy=" + metadata.get("confirmedBy"));
        values.add("confirmedAt=" + metadata.get("confirmedAt"));
        values.add("previewOnly=true");
        values.add("actionsExecuted=false");
        if (metadata.get("notes") != null) {
            values.add("notes=" + metadata.get("notes"));
        }
        return String.join("; ", values);
    }

    private static String normalizeSemanticTriggerEvaluationId(String evaluationId) {
        requireText(evaluationId, "evaluationId");
        String normalized = evaluationId.trim().toLowerCase(Locale.ROOT);
        if (!SEMANTIC_TRIGGER_EVALUATION_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("evaluationId is invalid");
        }
        return normalized;
    }

    private static String normalizeSemanticTriggerConfirmationStatus(String confirmationStatus) {
        requireText(confirmationStatus, "confirmationStatus");
        String normalized = confirmationStatus.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("confirmed", "rejected").contains(normalized)) {
            throw new IllegalArgumentException("confirmationStatus must be confirmed or rejected");
        }
        return normalized;
    }

    private static ReviewSemanticTriggerAuditRecord semanticTriggerPendingAudit(
            List<ReviewSemanticTriggerAuditRecord> audits,
            String evaluationId) {
        return audits.stream()
                .filter(audit -> SEMANTIC_TRIGGER_EVALUATED_ACTION.equals(audit.actionType()))
                .filter(audit -> SEMANTIC_TRIGGER_SCHEMA_VERSION.equals(audit.metadata().get("schemaVersion")))
                .filter(audit -> evaluationId.equals(audit.metadata().get("evaluationId")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("evaluationId not found"));
    }

    private static Optional<ReviewSemanticTriggerAuditRecord> semanticTriggerDecisionAudit(
            List<ReviewSemanticTriggerAuditRecord> audits,
            String evaluationId) {
        return audits.stream()
                .filter(audit -> SEMANTIC_TRIGGER_CONFIRMED_ACTION.equals(audit.actionType())
                        || SEMANTIC_TRIGGER_REJECTED_ACTION.equals(audit.actionType()))
                .filter(audit -> SEMANTIC_TRIGGER_SCHEMA_VERSION.equals(audit.metadata().get("schemaVersion")))
                .filter(audit -> evaluationId.equals(audit.metadata().get("evaluationId")))
                .filter(audit -> semanticTriggerDecisionStatus(audit.actionType())
                        .equals(audit.metadata().get("humanConfirmationStatus")))
                .findFirst();
    }

    private static String semanticTriggerDecisionStatus(String actionType) {
        return SEMANTIC_TRIGGER_CONFIRMED_ACTION.equals(actionType) ? "confirmed" : "rejected";
    }

    private static ReviewSemanticTriggerResult resolveExistingSemanticTriggerDecision(
            String evaluationId,
            String requestedStatus,
            ReviewSemanticTriggerAuditRecord pendingAudit,
            ReviewSemanticTriggerAuditRecord decisionAudit) {
        ReviewSemanticTriggerResult result = semanticTriggerResultFromAudits(
                evaluationId,
                pendingAudit,
                decisionAudit,
                true
        );
        if (!requestedStatus.equals(result.humanConfirmationStatus())) {
            throw new IllegalStateException("semantic_trigger_already_decided: "
                    + result.humanConfirmationStatus());
        }
        return result;
    }

    private static ReviewSemanticTriggerResult semanticTriggerResultFromAudits(
            String evaluationId,
            ReviewSemanticTriggerAuditRecord pendingAudit,
            ReviewSemanticTriggerAuditRecord decisionAudit,
            boolean duplicate) {
        Map<String, Object> pending = pendingAudit.metadata();
        String triggerName = toText(pending.get("triggerName"));
        LocalDateTime evaluatedAt = toLocalDateTime(pending.get("evaluatedAt"));
        if (!hasText(triggerName) || evaluatedAt == null) {
            throw new IllegalArgumentException("evaluationId not found");
        }
        String status = semanticTriggerDecisionStatus(decisionAudit.actionType());
        return new ReviewSemanticTriggerResult(
                triggerName,
                toText(pending.get("triggerType")),
                toText(pending.get("data")),
                toLongList(pending.get("matchedReviewItemIds")),
                toMapList(pending.get("actionPayloads")),
                evaluatedAt,
                firstText(toText(pending.get("inputVersion")), SEMANTIC_TRIGGER_INPUT_VERSION),
                toText(pending.get("inputHash")),
                toText(pending.get("indexGenerationId")),
                firstNonNull(toInteger(pending.get("latestIndexVersion")), 0),
                toMapList(pending.get("hitExplanations")),
                toMapList(pending.get("actionPreviews")),
                status,
                evaluationId,
                decisionAudit.operatorUserId(),
                decisionAudit.happenedAt(),
                duplicate
        );
    }

    private static ReviewSemanticTriggerResult semanticTriggerPendingResult(
            String evaluationId,
            ReviewSemanticTriggerAuditRecord pendingAudit) {
        Map<String, Object> pending = pendingAudit.metadata();
        String triggerName = toText(pending.get("triggerName"));
        LocalDateTime evaluatedAt = toLocalDateTime(pending.get("evaluatedAt"));
        if (!hasText(triggerName) || evaluatedAt == null) {
            throw new IllegalArgumentException("evaluationId not found");
        }
        return new ReviewSemanticTriggerResult(
                triggerName,
                toText(pending.get("triggerType")),
                toText(pending.get("data")),
                toLongList(pending.get("matchedReviewItemIds")),
                toMapList(pending.get("actionPayloads")),
                evaluatedAt,
                firstText(toText(pending.get("inputVersion")), SEMANTIC_TRIGGER_INPUT_VERSION),
                toText(pending.get("inputHash")),
                toText(pending.get("indexGenerationId")),
                firstNonNull(toInteger(pending.get("latestIndexVersion")), 0),
                toMapList(pending.get("hitExplanations")),
                toMapList(pending.get("actionPreviews")),
                "pending",
                evaluationId,
                null,
                null,
                false
        );
    }

    private ReviewSemanticHit toSemanticHit(ReviewItemAggregate item, String document, List<String> terms) {
        String lowerDocument = document.toLowerCase();
        Set<String> matchedTerms = new LinkedHashSet<>();
        double score = 0D;
        for (String term : terms) {
            if (lowerDocument.contains(term)) {
                matchedTerms.add(term);
                score += Math.max(1, term.length());
            }
        }
        if (matchedTerms.isEmpty()) {
            return new ReviewSemanticHit(item, 0D, List.of(), "");
        }
        return new ReviewSemanticHit(item, score, List.copyOf(matchedTerms), snippet(document, matchedTerms));
    }

    private SemanticSearchSnapshot semanticSearchSnapshot(ReviewQuery filters) {
        List<ReviewSemanticIndexEntry> indexEntries = deps.reviewItemStore.listSemanticIndex(filters);
        List<ReviewSemanticIndexEntry> indexedEntries = indexEntries.stream()
                .filter(entry -> SEMANTIC_INDEX_INDEXED.equals(entry.indexStatus()))
                .filter(entry -> hasText(entry.document()))
                .toList();
        String indexGenerationId = indexedEntries.stream()
                .filter(entry -> hasText(entry.indexGenerationId()))
                .max(Comparator
                        .comparing(ReviewSemanticIndexEntry::indexedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(ReviewSemanticIndexEntry::indexVersion,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(ReviewSemanticIndexEntry::indexGenerationId)
                .orElse(null);
        String selectedGenerationId = indexGenerationId;
        List<ReviewSemanticIndexEntry> selectedEntries = hasText(indexGenerationId)
                ? indexedEntries.stream()
                        .filter(entry -> selectedGenerationId.equals(entry.indexGenerationId()))
                        .toList()
                : indexedEntries;
        if (!hasText(indexGenerationId) && !selectedEntries.isEmpty()) {
            indexGenerationId = "legacy-" + sha256Hex(selectedEntries.stream()
                    .map(entry -> entry.reviewItemId() + ":" + entry.indexVersion())
                    .sorted()
                    .collect(Collectors.joining("|")));
        }
        Map<Long, ReviewSemanticIndexEntry> indexProvenanceByItemId = new LinkedHashMap<>();
        List<ReviewSemanticSearchCandidate> candidates = new ArrayList<>();
        for (ReviewSemanticIndexEntry entry : selectedEntries) {
            deps.reviewItemStore.findById(entry.reviewItemId()).ifPresent(item -> {
                candidates.add(new ReviewSemanticSearchCandidate(item, entry.document()));
                indexProvenanceByItemId.put(entry.reviewItemId(), entry);
            });
        }
        if (candidates.isEmpty()) {
            candidates.addAll(reviewItemQuery.listWorkbench(filters).stream()
                    .map(item -> new ReviewSemanticSearchCandidate(item, buildSearchDocument(item)))
                    .toList());
            indexProvenanceByItemId.clear();
            indexGenerationId = "live-" + sha256Hex(candidates.stream()
                    .map(candidate -> candidate.item().id() + ":"
                            + candidate.item().firstAlertTime() + ":"
                            + candidate.item().lastAlertTime())
                    .sorted()
                    .collect(Collectors.joining("|")));
        }
        int latestIndexVersion = selectedEntries.stream()
                .map(ReviewSemanticIndexEntry::indexVersion)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        return new SemanticSearchSnapshot(
                List.copyOf(candidates),
                Map.copyOf(indexProvenanceByItemId),
                indexGenerationId,
                latestIndexVersion
        );
    }

    private record SemanticSearchSnapshot(List<ReviewSemanticSearchCandidate> candidates,
                                          Map<Long, ReviewSemanticIndexEntry> indexProvenanceByItemId,
                                          String indexGenerationId,
                                          int latestIndexVersion) {
    }

    private static String semanticTriggerInputHash(ReviewSemanticTriggerCommand command,
                                                   ReviewQuery filters,
                                                   List<String> actions,
                                                   SemanticSearchSnapshot snapshot) {
        List<String> candidateWindow = snapshot.candidates().stream()
                .map(candidate -> candidate.item().id()
                        + "|" + candidate.item().firstAlertTime()
                        + "|" + candidate.item().lastAlertTime()
                        + "|" + candidate.item().sourceAlertIds())
                .sorted()
                .toList();
        List<String> normalizedActions = actions == null
                ? List.of()
                : actions.stream().map(String::trim).sorted().toList();
        return sha256Token(
                SEMANTIC_TRIGGER_INPUT_VERSION,
                normalizeSemanticInputText(command.triggerName()),
                normalizeSemanticInputText(command.cameraId()),
                normalizeSemanticInputText(command.triggerType()),
                normalizeSemanticInputText(command.data()),
                command.threshold(),
                normalizedActions,
                filters == null ? null : filters.reviewStatus(),
                filters == null ? null : filters.cameraId(),
                filters == null ? null : filters.zoneCode(),
                filters == null ? null : filters.objectLabel(),
                filters == null ? null : filters.recordEvidenceStatus(),
                filters == null ? null : filters.converted(),
                filters == null ? null : filters.inReviewCase(),
                filters == null ? null : filters.reviewerUserId(),
                filters == null ? null : filters.beginTime(),
                filters == null ? null : filters.endTime(),
                snapshot.indexGenerationId(),
                candidateWindow
        );
    }

    private static String normalizeSemanticInputText(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String buildSearchDocument(ReviewItemAggregate item) {
        StringBuilder document = new StringBuilder();
        appendSearchValue(document, item.reviewItemNo());
        appendSearchValue(document, item.sourceSystem());
        appendSearchValue(document, item.ruleCode());
        appendSearchValue(document, item.sourceAlertType());
        appendSearchValue(document, item.deviceId());
        appendSearchValue(document, item.cameraId());
        appendSearchValue(document, item.zoneCode());
        appendSearchValue(document, item.objectLabel());
        appendSearchValue(document, item.reviewStatus());
        appendSearchValue(document, item.sourceAlertIds());
        appendSearchValue(document, item.reviewData());
        for (ReviewEvidenceItem evidenceItem : deps.reviewItemStore.listTimeline(item.id())) {
            appendSearchValue(document, evidenceItem.sourceAlertId());
            appendSearchValue(document, evidenceItem.materialType());
            appendSearchValue(document, evidenceItem.materialUri());
        }
        return document.toString().trim();
    }

    private static String semanticEmbeddingKey(ReviewItemAggregate item) {
        return "review-item:" + item.id();
    }

    private static String semanticEmbeddingVectorHash(String document) {
        return "sha256:" + sha256Hex(document == null ? "" : document);
    }

    private static void appendSearchValue(StringBuilder document, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                appendSearchValue(document, entry.getKey());
                appendSearchValue(document, entry.getValue());
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                appendSearchValue(document, item);
            }
            return;
        }
        if (hasText(String.valueOf(value))) {
            document.append(value).append(' ');
        }
    }

    private static List<String> tokenize(String query) {
        String[] rawTerms = query.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+");
        Set<String> terms = new LinkedHashSet<>();
        for (String term : rawTerms) {
            if (hasText(term)) {
                terms.add(term);
            }
        }
        return List.copyOf(terms);
    }

    private static String snippet(String document, Set<String> matchedTerms) {
        String lowerDocument = document.toLowerCase();
        int firstMatch = -1;
        for (String term : matchedTerms) {
            int index = lowerDocument.indexOf(term);
            if (index >= 0 && (firstMatch < 0 || index < firstMatch)) {
                firstMatch = index;
            }
        }
        int start = firstMatch < 0 ? 0 : Math.max(0, firstMatch - 40);
        int end = Math.min(document.length(), start + 180);
        return document.substring(start, end);
    }

    private static List<Long> toLongList(Object value) {
        List<Long> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Long parsed = toLong(item);
                if (parsed != null) {
                    values.add(parsed);
                }
            }
        } else {
            Long parsed = toLong(value);
            if (parsed != null) {
                values.add(parsed);
            }
        }
        return List.copyOf(values);
    }
}
