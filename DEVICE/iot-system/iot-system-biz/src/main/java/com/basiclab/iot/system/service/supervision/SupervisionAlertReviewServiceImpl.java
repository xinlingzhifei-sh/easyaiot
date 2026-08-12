package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.utils.json.JsonUtils;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewAiSummaryRequest;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewItemSummaryContext;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewSemanticSearchCandidate;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewSemanticSearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.sql.SQLException;
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
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SupervisionAlertReviewServiceImpl implements SupervisionAlertReviewService {

    private static final String REVIEW_SOURCE_SYSTEM = "alert_review";
    private static final int DEFAULT_MERGE_WINDOW_SECONDS = 300;
    private static final int MAX_SEGMENT_CONFLICT_ATTEMPTS = 3;
    private static final int DEFAULT_CASE_CANDIDATE_WINDOW_SECONDS = 600;
    private static final int DEFAULT_EXPORT_JOB_EXPIRES_DAYS = 7;
    private static final int DEFAULT_EXPORT_WORKER_LIMIT = 20;
    private static final int MAX_EXPORT_WORKER_LIMIT = 100;
    private static final int EXPORT_WORKER_RECLAIM_MINUTES = 30;
    private static final Set<String> EVIDENCE_EXPORT_FORMATS = Set.of(
            "manifest", "mp4", "mkv", "mov", "avi", "webm", "ts"
    );
    private static final int DEFAULT_RUNTIME_OUTBOX_LIMIT = 50;
    private static final int MAX_RUNTIME_OUTBOX_LIMIT = 200;
    private static final int RUNTIME_OUTBOX_CLAIM_TIMEOUT_MINUTES = 10;
    private static final int DEFAULT_SEMANTIC_WORKER_LIMIT = 50;
    private static final int MAX_SEMANTIC_WORKER_LIMIT = 200;
    private static final int MIN_RULE_SUGGESTION_SAMPLE_COUNT = 3;
    private static final int REVIEW_DATA_VERSION = 1;
    private static final AlertReviewDataSchemaValidator REVIEW_DATA_SCHEMA_VALIDATOR =
            AlertReviewDataSchemaValidator.loadV1();
    private static final String LOCAL_EMBEDDING_MODEL = "yfeieye-review-local-v1";
    private static final String LOCAL_RULE_SUMMARY_MODEL = "local-rule-summary";
    private static final String REVIEW_AI_SUMMARY_PROVIDER_VERSION = "review-ai-provider-v1";
    private static final String REVIEW_AI_SUMMARY_PROMPT_VERSION = "review-ai-summary-prompt-v1";
    private static final Pattern SHA256_HASH = Pattern.compile("sha256:[0-9a-fA-F]{64}");
    private static final Pattern CANONICAL_RECORD_URI_PATH = Pattern.compile(
            "/video/record/space/[1-9][0-9]*/video/(.+)$");
    private static final Pattern MINIO_RECORD_DOWNLOAD_PATH = Pattern.compile(
            "/api/v1/buckets/[^/]+/objects/download/?$");
    private static final String AI_SUMMARY_GENERATED_ACTION = "ai_summary_generated";
    private static final String AI_SUMMARY_CONFIRMED_ACTION = "ai_summary_confirmed";
    private static final String AI_SUMMARY_REJECTED_ACTION = "ai_summary_rejected";
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
    private static final String AI_SUMMARY_CONFIRMATION_CONFIRMED = "confirmed";
    private static final String AI_SUMMARY_CONFIRMATION_REJECTED = "rejected";
    private static final String MATERIAL_SNAPSHOT = "snapshot";
    private static final String MATERIAL_RECORD = "record";
    private static final String MATERIAL_RECORD_COVERAGE = "record_coverage";
    private static final String MATERIAL_REVIEW_ACTION = "review_action";
    private static final String RECORD_GAP_FILE_EXPIRED_ALIAS = "file_expired";
    private static final String FALLBACK_RULE_CODE = SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING;
    private static final List<String> DEFAULT_RULE_CANDIDATE_ACTIONS = List.of(
            "narrow_zone",
            "raise_confidence",
            "increase_min_stay",
            "require_zone",
            "suppress_label_zone"
    );
    private static final List<RecordGapReasonDefinition> RECORD_GAP_REASON_CATALOG = List.of(
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

    private final ReviewItemStore reviewItemStore;
    private final ReviewRuleStore reviewRuleStore;
    private final SupervisionEventService supervisionEventService;
    private final RecordEvidenceResolver recordEvidenceResolver;
    private final EventProjectionStore eventProjectionStore;
    private final RecordCoverageResolver recordCoverageResolver;
    private final ReviewIntelligenceProvider reviewIntelligenceProvider;
    private final VideoEvidenceExportProvider videoEvidenceExportProvider;
    private final ReviewCameraPermissionResolver cameraPermissionResolver;
    private final ReviewAiSummaryRedactionPolicy aiSummaryRedactionPolicy;
    private final ReviewCameraTopologyResolver cameraTopologyResolver;
    private final ReviewEvidenceManifestSigner evidenceManifestSigner;
    private final ConcurrentMap<String, Object> reviewSegmentIngestLocks = new ConcurrentHashMap<>();
    private ReviewRuntimeOutboxPublisher runtimeOutboxPublisher = ReviewRuntimeOutboxPublisher.noop();
    private ReviewPlaybackUrlSigner reviewPlaybackUrlSigner = ReviewPlaybackUrlSigner.passthrough();
    private ReviewRecordStorageDriftResolver recordStorageDriftResolver = ReviewRecordStorageDriftResolver.unavailable();

    @Value("${yfeieye.video.record-drift-retention-hours:24}")
    private int recordStorageDriftRetentionHours = 24;

    private record RecordGapReasonDefinition(String code,
                                             String category,
                                             String labelZh,
                                             boolean retryable,
                                             List<String> aliases) {
    }

    private record ReviewDataConsistency(Map<String, Object> reviewData,
                                         boolean schemaDrift,
                                         boolean segmentDoubleWriteDrift) {
        boolean hasDrift() {
            return schemaDrift || segmentDoubleWriteDrift;
        }
    }

    public SupervisionAlertReviewServiceImpl(ReviewItemStore reviewItemStore,
                                             ReviewRuleStore reviewRuleStore,
                                             SupervisionEventService supervisionEventService,
                                             RecordEvidenceResolver recordEvidenceResolver,
                                             EventProjectionStore eventProjectionStore,
                                             RecordCoverageResolver recordCoverageResolver,
                                             ReviewIntelligenceProvider reviewIntelligenceProvider,
                                             VideoEvidenceExportProvider videoEvidenceExportProvider) {
        this(reviewItemStore, reviewRuleStore, supervisionEventService, recordEvidenceResolver, eventProjectionStore,
                recordCoverageResolver, reviewIntelligenceProvider, videoEvidenceExportProvider,
                ReviewCameraPermissionResolver.unrestricted(), new ReviewAiSummaryRedactionPolicy(),
                ReviewCameraTopologyResolver.empty());
    }

    public SupervisionAlertReviewServiceImpl(ReviewItemStore reviewItemStore,
                                             ReviewRuleStore reviewRuleStore,
                                             SupervisionEventService supervisionEventService,
                                             RecordEvidenceResolver recordEvidenceResolver,
                                             EventProjectionStore eventProjectionStore,
                                             RecordCoverageResolver recordCoverageResolver,
                                             ReviewIntelligenceProvider reviewIntelligenceProvider,
                                             VideoEvidenceExportProvider videoEvidenceExportProvider,
                                             ReviewCameraPermissionResolver cameraPermissionResolver) {
        this(reviewItemStore, reviewRuleStore, supervisionEventService, recordEvidenceResolver, eventProjectionStore,
                recordCoverageResolver, reviewIntelligenceProvider, videoEvidenceExportProvider,
                cameraPermissionResolver, new ReviewAiSummaryRedactionPolicy(), ReviewCameraTopologyResolver.empty());
    }

    public SupervisionAlertReviewServiceImpl(ReviewItemStore reviewItemStore,
                                             ReviewRuleStore reviewRuleStore,
                                             SupervisionEventService supervisionEventService,
                                             RecordEvidenceResolver recordEvidenceResolver,
                                             EventProjectionStore eventProjectionStore,
                                             RecordCoverageResolver recordCoverageResolver,
                                             ReviewIntelligenceProvider reviewIntelligenceProvider,
                                             VideoEvidenceExportProvider videoEvidenceExportProvider,
                                             ReviewCameraPermissionResolver cameraPermissionResolver,
                                             ReviewAiSummaryRedactionPolicy aiSummaryRedactionPolicy) {
        this(reviewItemStore, reviewRuleStore, supervisionEventService, recordEvidenceResolver, eventProjectionStore,
                recordCoverageResolver, reviewIntelligenceProvider, videoEvidenceExportProvider,
                cameraPermissionResolver, aiSummaryRedactionPolicy, ReviewCameraTopologyResolver.empty());
    }

    public SupervisionAlertReviewServiceImpl(ReviewItemStore reviewItemStore,
                                             ReviewRuleStore reviewRuleStore,
                                             SupervisionEventService supervisionEventService,
                                             RecordEvidenceResolver recordEvidenceResolver,
                                             EventProjectionStore eventProjectionStore,
                                             RecordCoverageResolver recordCoverageResolver,
                                             ReviewIntelligenceProvider reviewIntelligenceProvider,
                                             VideoEvidenceExportProvider videoEvidenceExportProvider,
                                             ReviewCameraPermissionResolver cameraPermissionResolver,
                                             ReviewAiSummaryRedactionPolicy aiSummaryRedactionPolicy,
                                             ReviewCameraTopologyResolver cameraTopologyResolver) {
        this(reviewItemStore, reviewRuleStore, supervisionEventService, recordEvidenceResolver,
                eventProjectionStore, recordCoverageResolver, reviewIntelligenceProvider,
                videoEvidenceExportProvider, cameraPermissionResolver, aiSummaryRedactionPolicy,
                cameraTopologyResolver, new ReviewEvidenceManifestSigner("", "", "hmac-sha256-v1"));
    }

    @Autowired
    public SupervisionAlertReviewServiceImpl(ReviewItemStore reviewItemStore,
                                             ReviewRuleStore reviewRuleStore,
                                             SupervisionEventService supervisionEventService,
                                             RecordEvidenceResolver recordEvidenceResolver,
                                             EventProjectionStore eventProjectionStore,
                                             RecordCoverageResolver recordCoverageResolver,
                                             ReviewIntelligenceProvider reviewIntelligenceProvider,
                                             VideoEvidenceExportProvider videoEvidenceExportProvider,
                                             ReviewCameraPermissionResolver cameraPermissionResolver,
                                             ReviewAiSummaryRedactionPolicy aiSummaryRedactionPolicy,
                                             ReviewCameraTopologyResolver cameraTopologyResolver,
                                             ReviewEvidenceManifestSigner evidenceManifestSigner) {
        this.reviewItemStore = Objects.requireNonNull(reviewItemStore, "reviewItemStore");
        this.reviewRuleStore = Objects.requireNonNull(reviewRuleStore, "reviewRuleStore");
        this.supervisionEventService = Objects.requireNonNull(supervisionEventService, "supervisionEventService");
        this.recordEvidenceResolver = Objects.requireNonNull(recordEvidenceResolver, "recordEvidenceResolver");
        this.eventProjectionStore = Objects.requireNonNull(eventProjectionStore, "eventProjectionStore");
        this.recordCoverageResolver = Objects.requireNonNull(recordCoverageResolver, "recordCoverageResolver");
        this.reviewIntelligenceProvider = Objects.requireNonNull(reviewIntelligenceProvider, "reviewIntelligenceProvider");
        this.videoEvidenceExportProvider = Objects.requireNonNull(videoEvidenceExportProvider, "videoEvidenceExportProvider");
        this.cameraPermissionResolver = Objects.requireNonNull(cameraPermissionResolver, "cameraPermissionResolver");
        this.aiSummaryRedactionPolicy = Objects.requireNonNull(aiSummaryRedactionPolicy, "aiSummaryRedactionPolicy");
        this.cameraTopologyResolver = Objects.requireNonNull(cameraTopologyResolver, "cameraTopologyResolver");
        this.evidenceManifestSigner = Objects.requireNonNull(evidenceManifestSigner, "evidenceManifestSigner");
    }

    @Autowired(required = false)
    public void setRuntimeOutboxPublisher(ReviewRuntimeOutboxPublisher runtimeOutboxPublisher) {
        this.runtimeOutboxPublisher = runtimeOutboxPublisher == null
                ? ReviewRuntimeOutboxPublisher.noop()
                : runtimeOutboxPublisher;
    }

    @Autowired(required = false)
    public void setReviewPlaybackUrlSigner(ReviewPlaybackUrlSigner reviewPlaybackUrlSigner) {
        this.reviewPlaybackUrlSigner = reviewPlaybackUrlSigner == null
                ? ReviewPlaybackUrlSigner.passthrough()
                : reviewPlaybackUrlSigner;
    }

    @Autowired(required = false)
    public void setRecordStorageDriftResolver(ReviewRecordStorageDriftResolver recordStorageDriftResolver) {
        this.recordStorageDriftResolver = recordStorageDriftResolver == null
                ? ReviewRecordStorageDriftResolver.unavailable()
                : recordStorageDriftResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
            reviewItemStore.acquireReviewSegmentTransactionLocks(
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
            return withEventProjection(existingIdentity.get());
        }
        EvidenceBuildResult evidenceResult = buildEvidenceItems(command);
        Map<String, Object> reviewData = withReviewWindow(buildReviewData(command, ruleCode), alertTime, alertTime);
        LocalDateTime windowStart = alertTime.minusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);
        LocalDateTime windowEnd = alertTime.plusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);

        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_SEGMENT_CONFLICT_ATTEMPTS; attempt++) {
            Optional<ReviewItemAggregate> retryIdentity = findExistingIngestIdentity(command, identityKeys);
            if (retryIdentity.isPresent()) {
                return withEventProjection(retryIdentity.get());
            }
            try {
                Optional<ReviewItemAggregate> candidate = reviewItemStore.findMergeCandidate(
                                command.sourceSystem(),
                                cameraId,
                                command.zoneCode(),
                                ruleCode,
                                windowStart,
                                windowEnd
                        )
                        .filter(SupervisionAlertReviewServiceImpl::canMergeReviewSegment);
                if (candidate.isPresent()) {
                    ReviewItemAggregate current = candidate.get();
                    String recordEvidenceStatus = mergeRecordEvidenceStatus(
                            current.recordEvidenceStatus(),
                            evidenceResult.recordEvidenceStatus()
                    );
                    return reviewItemStore.appendClue(current.id(),
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
                return reviewItemStore.create(new ReviewItemDraft(
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
                    return withEventProjection(concurrentWinner.get());
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
        Optional<ReviewItemAggregate> openSegment = reviewItemStore.findLatestOpenReviewSegment(cameraId)
                .or(() -> reviewItemStore.listWorkbench(null).stream()
                        .filter(item -> Objects.equals(cameraId, item.cameraId()))
                        .filter(SupervisionAlertReviewServiceImpl::canMergeReviewSegment)
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
        reviewItemStore.updateReviewLifecycle(
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

    @Override
    public List<ReviewItemAggregate> listWorkbench(ReviewQuery query) {
        if (query == null) {
            return reviewItemStore.listWorkbench(null).stream()
                    .map(this::withEventProjection)
                    .toList();
        }
        return reviewItemStore.listWorkbench(query)
                .stream()
                .map(this::withEventProjection)
                .filter(item -> matchesQuery(item, query))
                .filter(item -> matchesReviewerStatus(query.reviewerUserId(), item.id()))
                .toList();
    }

    @Override
    public List<ReviewEvidenceItem> getTimeline(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        return reviewItemStore.listTimeline(reviewItemId);
    }

    @Override
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

    @Override
    public List<ReviewDetailStreamItem> getReviewDetailStream(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        List<ReviewDetailStreamItem> stream = new ArrayList<>();
        stream.addAll(buildDetectionDetailStream(item));
        stream.addAll(buildMotionDetailStream(item));
        stream.addAll(buildLifecycleDetailStream(item));
        for (ReviewEvidenceItem evidenceItem : reviewItemStore.listTimeline(reviewItemId)) {
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

    @Override
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

    @Override
    public ReviewSegmentView getReviewSegment(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        Optional<ReviewSegmentView> persistedSegment = reviewItemStore.findPersistedReviewSegment(reviewItemId);
        if (persistedSegment.isPresent()) {
            return persistedSegment.get();
        }
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        return toReviewSegmentView(item);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewItemAggregate updateReviewLifecycle(ReviewLifecycleCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate initialItem = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        reviewItemStore.acquireReviewSegmentTransactionLocks(
                initialItem.cameraId(),
                initialItem.sourceSystem(),
                List.of()
        );
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        LocalDateTime happenedAt = command.happenedAt() == null ? LocalDateTime.now() : command.happenedAt();
        String state = normalizeReviewSegmentState(command.lifecycleState());
        Optional<ReviewSegmentView> persistedSegment = reviewItemStore.findPersistedReviewSegment(item.id());
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
        ReviewItemAggregate updated = reviewItemStore.updateReviewLifecycle(
                item.id(),
                immutableNonNullMap(persistedReviewData),
                item.firstAlertTime(),
                max(item.lastAlertTime(), happenedAt),
                evidenceItems,
                recordEvidenceStatus,
                checkedAt,
                message
        );
        return withEventProjection(updated);
    }

    @Override
    public ReviewItemAggregate retryRecordEvidence(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        RecordEvidenceAttempt attempt = resolveRecordEvidence(
                firstSourceAlertId(item),
                item.deviceId(),
                item.cameraId(),
                item.firstAlertTime()
        );
        if (attempt.evidenceItem().isPresent()) {
            reviewItemStore.appendEvidence(item.id(), List.of(attempt.evidenceItem().get()));
        }
        return withEventProjection(reviewItemStore.updateRecordEvidenceStatus(
                item.id(),
                attempt.recordEvidenceStatus(),
                attempt.recordEvidenceCheckedAt(),
                attempt.recordEvidenceMessage()
        ));
    }

    @Override
    public ReviewItemAggregate markReviewed(ReviewOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate existing = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        if (isSameReviewStatus(existing, STATUS_REVIEWED)) {
            markUserReviewed(command.reviewItemId(), command.reviewerUserId(), existing.reviewedAt());
            return withEventProjection(existing);
        }
        assertReviewStatusTransitionAllowed(existing, STATUS_REVIEWED);
        LocalDateTime reviewedAt = LocalDateTime.now();
        markUserReviewed(command.reviewItemId(), command.reviewerUserId(), reviewedAt);
        return withEventProjection(reviewItemStore.updateReviewStatus(
                command.reviewItemId(),
                STATUS_REVIEWED,
                command.reviewerUserId(),
                null,
                reviewedAt
        ));
    }

    @Override
    public ReviewUserStatusView markUserReviewStatus(ReviewUserStatusCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        requirePositive(command.userId(), "userId");
        reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        boolean hasBeenReviewed = Boolean.TRUE.equals(command.hasBeenReviewed());
        return reviewItemStore.upsertUserReviewStatus(
                command.reviewItemId(),
                command.userId(),
                hasBeenReviewed,
                hasBeenReviewed ? LocalDateTime.now() : null
        );
    }

    @Override
    public ReviewItemAggregate ignore(ReviewOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate existing = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        if (isSameReviewStatus(existing, STATUS_IGNORED)) {
            markUserReviewed(command.reviewItemId(), command.reviewerUserId(), existing.reviewedAt());
            return withEventProjection(existing);
        }
        assertReviewStatusTransitionAllowed(existing, STATUS_IGNORED);
        LocalDateTime reviewedAt = LocalDateTime.now();
        markUserReviewed(command.reviewItemId(), command.reviewerUserId(), reviewedAt);
        return withEventProjection(reviewItemStore.updateReviewStatus(
                command.reviewItemId(),
                STATUS_IGNORED,
                command.reviewerUserId(),
                command.reason(),
                reviewedAt
        ));
    }

    @Override
    public ReviewItemAggregate markFalsePositive(ReviewOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        if (item.eventId() != null || STATUS_CONVERTED.equals(item.reviewStatus())) {
            throw new IllegalStateException("converted_review_item_cannot_be_marked_false_positive");
        }
        if (isSameReviewStatus(item, STATUS_FALSE_POSITIVE)) {
            markUserReviewed(command.reviewItemId(), command.reviewerUserId(), item.reviewedAt());
            return withEventProjection(item);
        }
        assertReviewStatusTransitionAllowed(item, STATUS_FALSE_POSITIVE);
        LocalDateTime reviewedAt = LocalDateTime.now();
        markUserReviewed(command.reviewItemId(), command.reviewerUserId(), reviewedAt);
        return withEventProjection(reviewItemStore.updateFalsePositive(
                command.reviewItemId(),
                command.reviewerUserId(),
                command.reason(),
                buildRuleSuggestion(item, command.reason()),
                reviewedAt
        ));
    }

    @Override
    public ReviewItemAggregate updateRuleSuggestionStatus(RuleSuggestionOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        String status = requireRuleSuggestionStatus(command.status());
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        requireRuleSuggestionTransition(item, status);
        Map<String, Object> suggestion = updateRuleSuggestionLifecycle(item.ruleSuggestion(), status, command.note());
        if (RULE_SUGGESTION_SHADOW_EVALUATED.equals(status)) {
            suggestion = withCurrentRuleSuggestionSafety(item, suggestion);
            requireRuleSuggestionSampleReady(suggestion);
            suggestion = withRuleGovernanceEvidence(item, suggestion);
        }
        if (RULE_SUGGESTION_ACCEPTED.equals(status)) {
            requireRuleSuggestionSampleReady(suggestion);
            requireRuleGovernanceEvidence(suggestion);
        }
        if (RULE_SUGGESTION_APPLIED.equals(status)) {
            requireRuleSuggestionSampleReady(suggestion);
            requireRuleGovernanceEvidence(suggestion);
            suggestion = applyRuleSuggestion(item, suggestion);
        }
        return withEventProjection(reviewItemStore.updateRuleSuggestionStatus(
                command.reviewItemId(),
                command.reviewerUserId(),
                status,
                suggestion,
                LocalDateTime.now()
        ));
    }

    @Override
    public RuleSuggestionPreview previewRuleSuggestion(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        Map<String, Object> proposedRule = buildProposedRule(item);
        Map<String, Object> currentRule = reviewRuleStore.listAll().stream()
                .filter(rule -> sameRuleScope(rule, item))
                .findFirst()
                .map(SupervisionAlertReviewServiceImpl::ruleToMap)
                .orElse(Map.of());
        List<String> diff = buildRuleSuggestionDiff(currentRule, proposedRule);
        List<String> affectedReviewItemNos = listWorkbench(new ReviewQuery(null, item.cameraId(), null, null))
                .stream()
                .filter(candidate -> Objects.equals(candidate.zoneCode(), item.zoneCode()))
                .filter(candidate -> Objects.equals(candidate.objectLabel(), item.objectLabel()))
                .map(ReviewItemAggregate::reviewItemNo)
                .toList();
        return new RuleSuggestionPreview(item.id(), currentRule, proposedRule, diff, affectedReviewItemNos);
    }

    @Override
    public ReviewItemAggregate revertRuleSuggestion(RuleSuggestionOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        String status = requireRuleSuggestionStatus(command.status());
        if (!RULE_SUGGESTION_REVERTED.equals(status)) {
            throw new IllegalArgumentException("rule suggestion revert requires status: " + RULE_SUGGESTION_REVERTED);
        }
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        requireRuleSuggestionTransition(item, status);
        Map<String, Object> suggestion = updateRuleSuggestionLifecycle(item.ruleSuggestion(), status, command.note());
        suggestion = rollbackRuleSuggestion(item, suggestion);
        return withEventProjection(reviewItemStore.updateRuleSuggestionStatus(
                command.reviewItemId(),
                command.reviewerUserId(),
                status,
                suggestion,
                LocalDateTime.now()
        ));
    }

    @Override
    public List<RuleSuggestionStat> listRuleSuggestionStats(ReviewQuery query) {
        ReviewQuery scopedQuery = query == null
                ? new ReviewQuery(null, null, null, null)
                : new ReviewQuery(null, query.cameraId(), query.zoneCode(), query.objectLabel(), null,
                        null, null, null, query.beginTime(), query.endTime());
        Map<RuleSuggestionGroupKey, List<ReviewItemAggregate>> grouped = new LinkedHashMap<>();
        for (ReviewItemAggregate item : listWorkbench(scopedQuery)) {
            RuleSuggestionGroupKey key = new RuleSuggestionGroupKey(item.cameraId(), item.zoneCode(), item.objectLabel());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }

        List<RuleSuggestionStat> stats = new ArrayList<>();
        for (Map.Entry<RuleSuggestionGroupKey, List<ReviewItemAggregate>> entry : grouped.entrySet()) {
            List<ReviewItemAggregate> falsePositiveItems = entry.getValue().stream()
                    .filter(item -> STATUS_FALSE_POSITIVE.equals(item.reviewStatus()))
                    .toList();
            if (falsePositiveItems.isEmpty()) {
                continue;
            }
            ReviewItemAggregate sample = falsePositiveItems.get(0);
            Map<String, Object> suggestion = sample.ruleSuggestion() == null ? Map.of() : sample.ruleSuggestion();
            String action = toText(suggestion.get("action"));
            LocalDateTime lastSeenAt = falsePositiveItems.stream()
                    .map(ReviewItemAggregate::lastAlertTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            stats.add(new RuleSuggestionStat(
                    entry.getKey().cameraId(),
                    entry.getKey().zoneCode(),
                    entry.getKey().objectLabel(),
                    hasText(action) ? action : "suppress_label_zone",
                    falsePositiveItems.size(),
                    entry.getValue().size(),
                    roundRate(falsePositiveItems.size(), entry.getValue().size()),
                    ruleCandidateActions(suggestion),
                    lastSeenAt
            ));
        }
        return stats.stream()
                .sorted(Comparator
                        .comparing(RuleSuggestionStat::falsePositiveCount, Comparator.reverseOrder())
                        .thenComparing(RuleSuggestionStat::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    @Override
    public List<RecordCoverageSegment> getRecordCoverage(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        LocalDateTime windowStart = item.firstAlertTime().minusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);
        LocalDateTime windowEnd = item.lastAlertTime().plusSeconds(DEFAULT_MERGE_WINDOW_SECONDS);
        List<RecordCoverageSegment> resolved;
        try {
            resolved = recordCoverageResolver.resolve(new RecordCoverageRequest(
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
        Optional<ReviewEvidenceItem> recordEvidence = reviewItemStore.listTimeline(reviewItemId).stream()
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

    @Override
    public List<RecordCoverageSegment> getRecordCoverage(Long reviewItemId,
                                                         Long reviewCaseId,
                                                         Long operatorUserId,
                                                         List<String> allowedCameraIds) {
        List<RecordCoverageSegment> coverage = getRecordCoverage(reviewItemId);
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        if (canAttachRecordCoverageEvidence(reviewCaseId, item, operatorUserId, allowedCameraIds)) {
            ensureRecordCoverageEvidence(item, coverage);
        }
        enforceItemMediaReadScope(
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
        List<String> effectiveAllowedCameraIds = resolveEffectiveAllowedCameraIds(
                reviewCaseId,
                operatorUserId,
                "coverage",
                allowedCameraIds
        );
        if (effectiveAllowedCameraIds == null || !effectiveAllowedCameraIds.contains(item.cameraId())) {
            return false;
        }
        return getReviewCaseTimeline(reviewCaseId).stream()
                .anyMatch(row -> Objects.equals(item.id(), row.reviewItemId()));
    }

    private void ensureRecordCoverageEvidence(ReviewItemAggregate item, List<RecordCoverageSegment> coverage) {
        List<RecordCoverageSegment> segments = coverage == null ? List.of() : coverage;
        if (segments.stream().noneMatch(segment -> segment != null && hasText(segment.recordUri()))) {
            return;
        }
        Set<String> knownRecordUris = reviewItemStore.listTimeline(item.id()).stream()
                .filter(evidence -> MATERIAL_RECORD.equals(evidence.materialType()))
                .map(ReviewEvidenceItem::materialUri)
                .filter(SupervisionAlertReviewServiceImpl::hasText)
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
            reviewItemStore.appendEvidence(item.id(), evidenceItems);
        }
    }

    @Override
    public ReviewRecordStorageSyncResult syncRecordStorage(ReviewRecordStorageSyncCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
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
        reviewItemStore.updateReviewLifecycle(
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

    @Override
    public ReviewCaseView createReviewCase(ReviewCaseCommand command) {
        Objects.requireNonNull(command, "command");
        List<Long> reviewItemIds = normalizeReviewCaseItems(command.primaryReviewItemId(), command.reviewItemIds());
        Long primaryReviewItemId = command.primaryReviewItemId() == null ? reviewItemIds.get(0) : command.primaryReviewItemId();
        return reviewItemStore.createCase(
                new ReviewCaseDraft(command.title(), primaryReviewItemId, command.ownerUserId(), command.notes()),
                reviewItemIds
        );
    }

    @Override
    public Optional<ReviewCaseView> findReviewCaseByItem(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        if (reviewItemStore.findById(reviewItemId).isEmpty()) {
            return Optional.empty();
        }
        return reviewItemStore.findCaseByReviewItemId(reviewItemId);
    }

    @Override
    public ReviewCaseView addToReviewCase(Long reviewCaseId, Long reviewItemId) {
        requirePositive(reviewCaseId, "reviewCaseId");
        requirePositive(reviewItemId, "reviewItemId");
        return reviewItemStore.addCaseItem(reviewCaseId, reviewItemId);
    }

    @Override
    public ReviewCaseView assignReviewCaseOwner(ReviewCaseOwnerCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        requirePositive(command.ownerUserId(), "ownerUserId");
        return reviewItemStore.updateCaseOwner(
                command.reviewCaseId(),
                command.ownerUserId(),
                command.notes(),
                command.operatorUserId(),
                command.expectedVersion(),
                command.operationId()
        );
    }

    @Override
    public ReviewCaseView closeReviewCase(ReviewCaseOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        return reviewItemStore.closeCase(
                command.reviewCaseId(),
                command.notes(),
                command.operatorUserId(),
                LocalDateTime.now(),
                command.expectedVersion(),
                command.operationId()
        );
    }

    @Override
    public ReviewCaseMergeResult mergeReviewCases(ReviewCaseMergeCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.targetReviewCaseId(), "targetReviewCaseId");
        requirePositive(command.sourceReviewCaseId(), "sourceReviewCaseId");
        if (Objects.equals(command.targetReviewCaseId(), command.sourceReviewCaseId())) {
            throw new IllegalArgumentException("sourceReviewCaseId must differ from targetReviewCaseId");
        }
        return reviewItemStore.mergeCases(
                command.targetReviewCaseId(),
                command.sourceReviewCaseId(),
                command.operatorUserId(),
                command.notes(),
                command.targetExpectedVersion(),
                command.sourceExpectedVersion(),
                command.operationId()
        );
    }

    @Override
    public ReviewCaseSplitResult splitReviewCase(ReviewCaseSplitCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.sourceReviewCaseId(), "sourceReviewCaseId");
        List<Long> reviewItemIds = normalizeReviewCaseItems(null, command.reviewItemIds());
        return reviewItemStore.splitCase(
                command.sourceReviewCaseId(),
                new ReviewCaseDraft(command.title(), reviewItemIds.get(0), command.ownerUserId(), command.notes()),
                reviewItemIds,
                command.operatorUserId(),
                command.sourceExpectedVersion(),
                command.operationId()
        );
    }

    @Override
    public List<ReviewCaseTimelineItem> getReviewCaseTimeline(Long reviewCaseId) {
        return getReviewCaseTimeline(reviewCaseId, new LinkedHashMap<>(), true);
    }

    private List<ReviewCaseTimelineItem> getReviewCaseTimeline(
            Long reviewCaseId,
            Map<Long, List<RecordCoverageSegment>> coverageByReviewItemId,
            boolean includeRecordCoverage) {
        requirePositive(reviewCaseId, "reviewCaseId");
        List<ReviewCaseTimelineItem> timeline = new ArrayList<>(reviewItemStore.listCaseTimeline(reviewCaseId));
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

    @Override
    public List<ReviewCaseTimelineItem> getReviewCaseTimeline(Long reviewCaseId,
                                                              Long operatorUserId,
                                                              List<String> allowedCameraIds) {
        List<ReviewCaseTimelineItem> timeline = getReviewCaseTimeline(reviewCaseId);
        enforceCaseEvidenceMediaReadScope(
                reviewCaseId,
                timeline,
                loadReviewItems(reviewItemIdsFromTimeline(timeline)),
                operatorUserId,
                allowedCameraIds,
                "case timeline media read"
        );
        return timeline;
    }

    @Override
    public List<ReviewItemAggregate> suggestReviewCaseCandidates(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate base = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        LocalDateTime beginTime = base.firstAlertTime().minusSeconds(DEFAULT_CASE_CANDIDATE_WINDOW_SECONDS);
        LocalDateTime endTime = base.lastAlertTime().plusSeconds(DEFAULT_CASE_CANDIDATE_WINDOW_SECONDS);
        ReviewQuery query = new ReviewQuery(null, null, null, null, null, null, null, null, beginTime, endTime);
        return listWorkbench(query).stream()
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

    @Override
    public ReviewWorkbenchSummary getWorkbenchSummary(ReviewQuery query) {
        List<ReviewItemAggregate> items = listWorkbench(query == null
                ? new ReviewQuery(null, null, null, null)
                : new ReviewQuery(null, null, query.zoneCode(), query.objectLabel(), null, null, null,
                        null, query.beginTime(), query.endTime()));
        long pendingReview = items.stream().filter(item -> STATUS_PENDING_REVIEW.equals(item.reviewStatus())).count();
        long reviewedByMe = 0L;
        if (query != null && query.reviewerUserId() != null) {
            reviewedByMe = reviewItemStore.countReviewedByUser(
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

    @Override
    public List<ReviewSemanticHit> semanticSearch(ReviewSemanticSearchCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.query(), "query");
        return semanticSearch(command, semanticSearchSnapshot(command.filters()).candidates());
    }

    private List<ReviewSemanticHit> semanticSearch(ReviewSemanticSearchCommand command,
                                                   List<ReviewSemanticSearchCandidate> candidates) {
        List<String> terms = tokenize(command.query());
        int limit = command.limit() == null || command.limit() <= 0 ? 20 : command.limit();
        Optional<List<ReviewSemanticHit>> providerHits = reviewIntelligenceProvider.semanticSearch(
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

    @Override
    public List<ReviewSemanticIndexEntry> reindexSemanticIndex(ReviewQuery query) {
        LocalDateTime indexedAt = LocalDateTime.now();
        String indexGenerationId = newSemanticIndexGenerationId();
        return listWorkbench(query).stream()
                .map(item -> reindexSemanticItem(item, indexedAt, indexGenerationId))
                .toList();
    }

    private ReviewSemanticIndexEntry reindexSemanticItem(ReviewItemAggregate item,
                                                          LocalDateTime indexedAt,
                                                          String indexGenerationId) {
        String document = buildSearchDocument(item);
        ReviewSemanticIndexEntry queued = reviewItemStore.queueSemanticIndex(
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
        List<ReviewSemanticIndexEntry> claimed = reviewItemStore.claimSemanticIndex(
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
            return reviewItemStore.completeSemanticIndexClaim(
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

    @Override
    public ReviewSemanticReindexJob queueSemanticReindex(ReviewSemanticReindexCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDateTime queuedAt = LocalDateTime.now();
        String indexGenerationId = newSemanticIndexGenerationId();
        List<ReviewItemAggregate> items = listWorkbench(command.query());
        List<Long> queuedIds = new ArrayList<>();
        for (ReviewItemAggregate item : items) {
            ReviewSemanticIndexEntry queued = reviewItemStore.queueSemanticIndex(
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

    @Override
    public ReviewSemanticWorkerRun processSemanticIndexQueue(ReviewSemanticWorkerCommand command) {
        Objects.requireNonNull(command, "command");
        int limit = boundedPositive(command.maxItems(), DEFAULT_SEMANTIC_WORKER_LIMIT, MAX_SEMANTIC_WORKER_LIMIT);
        List<ReviewItemAggregate> items = listWorkbench(command.query());
        LocalDateTime processedAt = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        List<ReviewSemanticIndexEntry> claimedEntries = reviewItemStore.claimSemanticIndex(
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
                reviewItemStore.completeSemanticIndexClaim(
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
                    reviewItemStore.completeSemanticIndexClaim(
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

    @Override
    public ReviewSemanticIndexEvaluation evaluateSemanticIndex(ReviewSemanticIndexEvaluationCommand command) {
        Objects.requireNonNull(command, "command");
        List<ReviewItemAggregate> items = listWorkbench(command.query());
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
        for (ReviewSemanticIndexEntry entry : reviewItemStore.listSemanticIndex(query)) {
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

    @Override
    public ReviewRuntimeHealthReport getReviewRuntimeHealth(ReviewRuntimeHealthCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewQuery query = command.query();
        List<ReviewItemAggregate> items = listWorkbench(query);
        int missingRecordCount = (int) items.stream()
                .filter(this::hasRecordEvidenceGap)
                .count();
        ReviewSemanticIndexEvaluation semantic = evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(query, command.operatorUserId()));
        int semanticBacklogCount = semantic.staleReviewItemIds().size();
        List<ReviewEvidenceExportJob> exportJobs = reviewItemStore.listAllExportJobs();
        int failedExportJobCount = (int) exportJobs.stream()
                .filter(job -> EXPORT_JOB_FAILED.equals(job.status()))
                .count();
        List<ReviewDataConsistency> reviewDataConsistencies = items.stream()
                .map(this::reviewDataConsistency)
                .toList();
        int reviewDataSchemaDriftCount = (int) reviewDataConsistencies.stream()
                .filter(ReviewDataConsistency::schemaDrift)
                .count();
        int reviewSegmentDoubleWriteDriftCount = (int) reviewDataConsistencies.stream()
                .filter(ReviewDataConsistency::segmentDoubleWriteDrift)
                .count();
        List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> storageDriftReports =
                inspectRecordStorageDrift(items);
        Map<String, Integer> recordGapReasons = mergeRecordStorageDriftReasons(
                recordGapReasons(items), storageDriftReports);
        int storageDriftIssueCount = storageDriftReports.stream()
                .mapToInt(ReviewRecordStorageDriftResolver.RecordStorageDriftReport::issueCount)
                .sum();
        List<String> alerts = new ArrayList<>();
        if (missingRecordCount > 0 || !recordGapReasons.isEmpty()) {
            alerts.add("record_evidence_gap");
            for (String reason : recordGapReasons.keySet()) {
                alerts.add("record_evidence_gap:" + reason);
            }
        }
        List<String> storageDriftReasons = recordGapReasons.keySet().stream()
                .filter(SupervisionAlertReviewServiceImpl::isRecordStorageDriftReason)
                .toList();
        if (!storageDriftReasons.isEmpty()) {
            alerts.add("record_storage_drift");
            for (String reason : storageDriftReasons) {
                alerts.add("record_storage_drift:" + reason);
            }
        }
        if (semanticBacklogCount > 0) {
            alerts.add("semantic_index_backlog");
        }
        if (failedExportJobCount > 0) {
            alerts.add("evidence_export_failed");
        }
        if (reviewDataSchemaDriftCount > 0) {
            alerts.add("review_data_schema_drift");
        }
        if (reviewSegmentDoubleWriteDriftCount > 0) {
            alerts.add("review_segment_double_write_drift");
        }
        return new ReviewRuntimeHealthReport(
                items.size(),
                missingRecordCount,
                semantic.staleReviewItemIds().size(),
                failedExportJobCount,
                roundRate(missingRecordCount, items.size()),
                roundRate(failedExportJobCount, exportJobs.size()),
                semanticBacklogCount,
                missingRecordCount + semanticBacklogCount + failedExportJobCount
                        + reviewDataSchemaDriftCount + reviewSegmentDoubleWriteDriftCount
                        + storageDriftIssueCount,
                Map.copyOf(recordGapReasons),
                recordGapReasonCatalog(),
                List.copyOf(alerts),
                LocalDateTime.now(),
                command.operatorUserId()
        );
    }

    @Override
    public ReviewRuntimePatrolResult runRuntimePatrol(ReviewRuntimePatrolCommand command) {
        Objects.requireNonNull(command, "command");
        String lockName = "alert-review-runtime-patrol";
        ReviewRuntimeLockAcquisition lock = reviewItemStore.acquireRuntimePatrolLock(
                lockName,
                LocalDateTime.now().plusMinutes(10),
                command.operatorUserId());
        if (!Boolean.TRUE.equals(lock.acquired())) {
            return new ReviewRuntimePatrolResult(
                    "locked",
                    false,
                    normalizePatrolAttempts(command.maxAttempts()),
                    0,
                    getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(command.query(), command.operatorUserId())),
                    null,
                    List.of("runtime_patrol_locked"),
                    List.of(),
                    List.of("wait_for_current_runtime_patrol"),
                    LocalDateTime.now(),
                    command.operatorUserId(),
                    immutableNonNullMap(runtimeLockMetadata(command, lockName, lock))
            );
        }
        try {
            int maxAttempts = normalizePatrolAttempts(command.maxAttempts());
            ReviewRuntimeHealthReport before = getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                    command.query(), command.operatorUserId()));
            List<String> alerts = List.copyOf(before.alerts());
            List<String> notifications = alerts.stream()
                    .map(alert -> "review_runtime_alert:" + alert)
                    .toList();
            ReviewReconciliationResult reconciliation = null;
            int attemptCount = 0;
            List<String> attemptFindings = new ArrayList<>();
            RuntimeException lastFailure = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                attemptCount = attempt;
                try {
                    reconciliation = reconcileReviewRuntime(new ReviewReconciliationCommand(
                            command.query(),
                            command.operatorUserId(),
                            command.repair()
                    ));
                    break;
                } catch (RuntimeException ex) {
                    lastFailure = ex;
                    attemptFindings.add("attempt_failed:" + attempt + ":" + ex.getClass().getSimpleName());
                }
            }
            ReviewRuntimeHealthReport after = reconciliation == null
                    ? before
                    : reconciliation.healthReport();
            List<String> recommendedActions = runtimePatrolRecommendedActions(alerts, after);
            if (reconciliation == null && lastFailure != null) {
                recommendedActions = new ArrayList<>(recommendedActions);
                recommendedActions.add("inspect_runtime_patrol_failure");
            }
            LocalDateTime executedAt = LocalDateTime.now();
            Map<String, Object> metadata = runtimeLockMetadata(command, lockName, lock);
            metadata.put("repairRequested", !Boolean.FALSE.equals(command.repair()));
            metadata.put("attemptFindings", attemptFindings);
            metadata.put("initialRepairableCount", before.repairableCount());
            metadata.put("finalRepairableCount", after.repairableCount());
            metadata.put("recordGapReasons", after.recordGapReasons());
            metadata.put("recordGapReasonDetails", recordGapReasonDetails(after.recordGapReasons()));
            metadata.put("alertActions", runtimePatrolAlertActions(alerts));
            metadata.put("nextRetryAt", after.repairableCount() > 0
                    ? executedAt.plusMinutes(Math.max(1, attemptCount)).toString()
                    : null);
            String status = reconciliation == null ? "failed" : (alerts.isEmpty() ? "healthy" : "alerted");
            String runId = reviewItemStore.recordRuntimePatrolRun(
                    status,
                    attemptCount,
                    alerts,
                    recommendedActions,
                    command.operatorUserId(),
                    executedAt,
                    immutableNonNullMap(metadata)
            );
            int outboxEventCount = reviewItemStore.enqueueRuntimePatrolAlerts(
                    runId,
                    alerts,
                    recommendedActions,
                    command.operatorUserId(),
                    executedAt,
                    immutableNonNullMap(metadata)
            );
            metadata.put("historyRunId", runId);
            metadata.put("outboxEventCount", outboxEventCount);
            return new ReviewRuntimePatrolResult(
                    status,
                    true,
                    maxAttempts,
                    attemptCount,
                    after,
                    reconciliation,
                    alerts,
                    notifications,
                    List.copyOf(new LinkedHashSet<>(recommendedActions)),
                    executedAt,
                    command.operatorUserId(),
                    immutableNonNullMap(metadata)
            );
        } finally {
            reviewItemStore.releaseRuntimePatrolLock(lockName, command.operatorUserId());
        }
    }

    private static Map<String, Object> runtimeLockMetadata(ReviewRuntimePatrolCommand command,
                                                           String lockName,
                                                           ReviewRuntimeLockAcquisition lock) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scheduled", Boolean.TRUE.equals(command.scheduled()));
        metadata.put("lockName", lockName);
        metadata.put("lockBackend", "review_item_store");
        metadata.put("lockReason", lock.reason());
        metadata.put("lockRecovered", Boolean.TRUE.equals(lock.recoveredStaleLock()));
        metadata.put("lockedUntil", lock.lockedUntil());
        metadata.put("lockAcquiredAt", lock.acquiredAt());
        if (Boolean.TRUE.equals(lock.acquired())) {
            metadata.put("previousLockOwnerUserId", lock.previousOwnerUserId());
            metadata.put("previousLockedUntil", lock.previousLockedUntil());
        } else {
            metadata.put("lockOwnerUserId", lock.previousOwnerUserId());
        }
        return metadata;
    }

    @Override
    public ReviewRuntimeOutboxPublishResult publishRuntimeOutbox(ReviewRuntimeOutboxPublishCommand command) {
        Objects.requireNonNull(command, "command");
        int limit = normalizeRuntimeOutboxLimit(command.limit());
        LocalDateTime publishedAt = LocalDateTime.now();
        String claimToken = UUID.randomUUID().toString();
        List<String> publishedAlerts = new ArrayList<>();
        List<String> failedAlerts = new ArrayList<>();
        List<ReviewRuntimeOutboxMessage> messages = reviewItemStore.claimPendingRuntimeOutbox(
                limit,
                claimToken,
                command.operatorUserId(),
                publishedAt,
                publishedAt.minusMinutes(RUNTIME_OUTBOX_CLAIM_TIMEOUT_MINUTES)
        );
        for (ReviewRuntimeOutboxMessage message : messages) {
            if (message == null || message.id() == null) {
                continue;
            }
            if (!hasText(message.eventType()) || !hasText(message.alertKey()) || !hasText(message.payload())) {
                reviewItemStore.markRuntimeOutboxFailed(
                        message.id(),
                        "invalid_runtime_outbox_payload",
                        publishedAt
                );
                failedAlerts.add(String.valueOf(message.alertKey()));
                continue;
            }
            ReviewRuntimeOutboxDeliveryResult deliveryResult;
            try {
                deliveryResult = runtimeOutboxPublisher.publish(message);
            } catch (RuntimeException ex) {
                reviewItemStore.markRuntimeOutboxFailed(
                        message.id(),
                        "runtime_outbox_publish_exception:" + ex.getClass().getSimpleName(),
                        publishedAt
                );
                failedAlerts.add(message.alertKey());
                continue;
            }
            if (deliveryResult == null || !Boolean.TRUE.equals(deliveryResult.success())) {
                reviewItemStore.markRuntimeOutboxFailed(
                        message.id(),
                        runtimeOutboxDeliveryFailureReason(deliveryResult),
                        publishedAt
                );
                failedAlerts.add(message.alertKey());
                continue;
            }
            reviewItemStore.markRuntimeOutboxPublished(message.id(), publishedAt);
            publishedAlerts.add(message.alertKey());
        }
        return new ReviewRuntimeOutboxPublishResult(
                messages.size(),
                publishedAlerts.size(),
                failedAlerts.size(),
                List.copyOf(publishedAlerts),
                List.copyOf(failedAlerts),
                publishedAt,
                command.operatorUserId()
        );
    }

    private static String runtimeOutboxDeliveryFailureReason(ReviewRuntimeOutboxDeliveryResult deliveryResult) {
        if (deliveryResult == null) {
            return "runtime_outbox_publish_failed";
        }
        return firstText(deliveryResult.errorCode(), "runtime_outbox_publish_failed");
    }

    @Override
    public ReviewEventReconciliationResult reconcileEventProjections(ReviewEventReconciliationCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewQuery query = command.query();
        LocalDateTime reconciledAt = LocalDateTime.now();
        List<ReviewItemAggregate> items = reviewItemStore.listWorkbench(query)
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> query == null || matchesQuery(item, query))
                .filter(item -> query == null || matchesReviewerStatus(query.reviewerUserId(), item.id()))
                .toList();
        List<String> findings = new ArrayList<>();
        int reconciledCount = 0;
        int missingProjectionCount = 0;
        int conflictCount = 0;
        for (ReviewItemAggregate item : items) {
            if (item.eventId() == null) {
                continue;
            }
            Optional<EventProjection> projection = eventProjectionStore.findByEventId(item.eventId());
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
            reviewItemStore.updateEventProjection(
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

    @Override
    public ReviewReconciliationResult reconcileReviewRuntime(ReviewReconciliationCommand command) {
        Objects.requireNonNull(command, "command");
        boolean repair = !Boolean.FALSE.equals(command.repair());
        ReviewQuery query = command.query();
        List<ReviewItemAggregate> items = listWorkbench(query);
        List<String> findings = new ArrayList<>();
        int repairedRecordCount = 0;
        if (repair) {
            for (ReviewItemAggregate item : items) {
                if (!hasRecordEvidenceGap(item)) {
                    continue;
                }
                List<RecordCoverageSegment> coverage = getRecordCoverage(item.id());
                ReviewRecordStorageSyncResult syncResult = syncRecordStorage(new ReviewRecordStorageSyncCommand(
                        item.id(),
                        command.operatorUserId(),
                        coverage
                ));
                ReviewItemAggregate repairedItem = reviewItemStore.findById(item.id()).orElse(item);
                if (RECORD_EVIDENCE_FOUND.equals(repairedItem.recordEvidenceStatus())) {
                    repairedRecordCount++;
                    findings.add("record_repaired:" + item.id());
                } else {
                    findings.add("record_unresolved:" + item.id() + ":" + syncResult.syncStatus());
                }
            }
        }

        items = listWorkbench(query);
        if (repair) {
            for (ReviewItemAggregate item : items) {
                ReviewDataConsistency consistency = reviewDataConsistency(item);
                if (!consistency.hasDrift()) {
                    continue;
                }
                reviewItemStore.updateReviewLifecycle(
                        item.id(),
                        consistency.reviewData(),
                        item.firstAlertTime(),
                        item.lastAlertTime(),
                        List.of(),
                        item.recordEvidenceStatus(),
                        item.recordEvidenceCheckedAt(),
                        item.recordEvidenceMessage()
                );
                if (consistency.schemaDrift()) {
                    findings.add("review_data_repaired:" + item.id());
                }
                if (consistency.segmentDoubleWriteDrift()) {
                    findings.add("review_segment_repaired:" + item.id());
                }
            }
        }

        ReviewSemanticIndexEvaluation semanticBefore = evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(query, command.operatorUserId()));
        int repairedSemanticIndexCount = 0;
        if (repair && !semanticBefore.staleReviewItemIds().isEmpty()) {
            Set<Long> staleIds = new LinkedHashSet<>(semanticBefore.staleReviewItemIds());
            for (ReviewSemanticIndexEntry entry : reindexSemanticIndex(query)) {
                if (staleIds.contains(entry.reviewItemId())
                        && SEMANTIC_INDEX_INDEXED.equals(entry.indexStatus())) {
                    repairedSemanticIndexCount++;
                    findings.add("semantic_reindexed:" + entry.reviewItemId());
                }
            }
        }
        int failedExportJobCount = (int) reviewItemStore.listAllExportJobs().stream()
                .filter(job -> EXPORT_JOB_FAILED.equals(job.status()))
                .count();
        ReviewRuntimeHealthReport healthReport = getReviewRuntimeHealth(new ReviewRuntimeHealthCommand(
                query,
                command.operatorUserId()
        ));
        return new ReviewReconciliationResult(
                items.size(),
                repairedRecordCount,
                repairedSemanticIndexCount,
                failedExportJobCount,
                List.copyOf(findings),
                healthReport,
                LocalDateTime.now(),
                command.operatorUserId()
        );
    }

    @Override
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
                .filter(SupervisionAlertReviewServiceImpl::hasText)
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
        reviewItemStore.recordSemanticTriggerEvaluation(
                reviewItemId,
                "evaluationId=" + evaluationId + "; humanConfirmationStatus=pending; previewOnly=true",
                command.operatorUserId(),
                evaluatedAt,
                semanticTriggerEvaluationMetadata(result, command.operatorUserId())
        );
        return result;
    }

    @Override
    public ReviewSemanticTriggerResult getSemanticTrigger(String evaluationId) {
        String normalizedEvaluationId = normalizeSemanticTriggerEvaluationId(evaluationId);
        List<ReviewSemanticTriggerAuditRecord> audits = reviewItemStore.listSemanticTriggerAudits(
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

    @Override
    public ReviewSemanticTriggerResult confirmSemanticTrigger(ReviewSemanticTriggerConfirmationCommand command) {
        Objects.requireNonNull(command, "command");
        String evaluationId = normalizeSemanticTriggerEvaluationId(command.evaluationId());
        String confirmationStatus = normalizeSemanticTriggerConfirmationStatus(command.confirmationStatus());
        requirePositive(command.operatorUserId(), "operatorUserId");

        List<ReviewSemanticTriggerAuditRecord> existingAudits = reviewItemStore.listSemanticTriggerAudits(evaluationId);
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
        boolean inserted = reviewItemStore.recordSemanticTriggerDecision(
                evaluationId,
                actionType,
                semanticTriggerDecisionAuditNote(decisionMetadata),
                command.operatorUserId(),
                confirmedAt,
                decisionMetadata
        );
        List<ReviewSemanticTriggerAuditRecord> persistedAudits = reviewItemStore.listSemanticTriggerAudits(evaluationId);
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

    @Override
    public ReviewAiSummary summarizeReviewCase(Long reviewCaseId, Long operatorUserId) {
        requirePositive(reviewCaseId, "reviewCaseId");
        List<ReviewCaseTimelineItem> timeline = getReviewCaseTimeline(reviewCaseId);
        List<Long> reviewItemIds = reviewItemIdsFromTimeline(timeline);
        List<ReviewItemAggregate> reviewItems = reviewItemIds.stream()
                .map(id -> withEventProjection(reviewItemStore.findById(id).orElse(null)))
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
        Optional<ReviewAiSummary> providerSummary = reviewIntelligenceProvider.summarize(summaryRequest);
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

    @Override
    public ReviewAiSummaryConfirmation confirmReviewCaseAiSummary(ReviewAiSummaryConfirmationCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        String confirmationStatus = normalizeAiSummaryConfirmationStatus(command.confirmationStatus());
        List<ReviewCaseTimelineItem> timeline = getReviewCaseTimeline(command.reviewCaseId());
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
                .map(SupervisionAlertReviewServiceImpl::parseAuditNote)
                .map(note -> toText(note.get("humanConfirmationStatus")))
                .filter(SupervisionAlertReviewServiceImpl::hasText)
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
        reviewItemStore.recordCaseAudit(
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

    @Override
    public ReviewOperationsReport generateReviewReport(ReviewReportCommand command) {
        Objects.requireNonNull(command, "command");
        String reportType = firstText(command.reportType(), "shift");
        ReviewQuery query = reportQuery(command);
        List<ReviewItemAggregate> reviewItems = listWorkbench(query);
        List<Long> reviewItemIds = reviewItems.stream().map(ReviewItemAggregate::id).toList();
        List<String> evidenceGaps = new ArrayList<>();
        List<String> recommendedActions = new ArrayList<>();
        int missingRecordCount = 0;
        int falsePositiveCount = 0;
        int convertedEventCount = 0;
        int unreviewedBacklogCount = 0;
        for (ReviewItemAggregate item : reviewItems) {
            if (hasRecordEvidenceGap(item)) {
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
        ReviewSemanticIndexEvaluation semantic = evaluateSemanticIndex(
                new ReviewSemanticIndexEvaluationCommand(query, command.operatorUserId()));
        Set<Long> reportReviewItemIds = new LinkedHashSet<>(reviewItemIds);
        List<ReviewEvidenceExportJob> exportJobs = reviewItemStore.listAllExportJobs().stream()
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
                SupervisionAlertReviewServiceImpl::reportResponsibilityUnit
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
                reviewItemStore.findReportAcknowledgement(reportKey).orElse(null)
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

    @Override
    public ReviewOperationsReportDelivery scheduleReviewReportDelivery(ReviewReportCommand command) {
        Objects.requireNonNull(command, "command");
        ReviewOperationsReport report = generateReviewReport(command);
        LocalDateTime queuedAt = LocalDateTime.now();
        int outboxEventCount = reviewItemStore.enqueueOperationsReportDelivery(report, true, queuedAt);
        return new ReviewOperationsReportDelivery(report, outboxEventCount, queuedAt);
    }

    @Override
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
        List<Long> reviewItemIds = listWorkbench(query).stream().map(ReviewItemAggregate::id).toList();
        String reportKey = buildReportKey(reportType, query);
        Optional<ReviewReportAcknowledgement> existing = reviewItemStore.findReportAcknowledgement(reportKey);
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
        return reviewItemStore.saveReportAcknowledgement(new ReviewReportAcknowledgement(
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
            if (hasRecordEvidenceGap(item)) {
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

    @Override
    public ReviewEvidenceExportPackage exportReviewEvidence(ReviewEvidenceExportCommand command) {
        return buildReviewEvidenceExportPackage(command, true);
    }

    @Override
    public ReviewEvidenceExportJob createReviewEvidenceExportJob(ReviewEvidenceExportCommand command) {
        ReviewEvidenceExportPackage exportPackage = buildReviewEvidenceExportPackage(command, false);
        LocalDateTime createdAt = exportPackage.generatedAt();
        return reviewItemStore.createExportJob(
                exportPackage,
                command.operatorUserId(),
                command.reason(),
                boundEventIds(exportPackage.reviewItemIds()),
                exportFileHash(exportPackage),
                createdAt.plusDays(DEFAULT_EXPORT_JOB_EXPIRES_DAYS),
                createdAt
        );
    }

    @Override
    public ReviewEvidenceExportWorkerRun processEvidenceExportQueue(ReviewEvidenceExportWorkerCommand command) {
        Objects.requireNonNull(command, "command");
        int limit = boundedPositive(command.maxJobs(), DEFAULT_EXPORT_WORKER_LIMIT, MAX_EXPORT_WORKER_LIMIT);
        LocalDateTime processedAt = LocalDateTime.now();
        List<String> processedJobNos = new ArrayList<>();
        List<String> failedJobNos = new ArrayList<>();
        List<String> deferredJobNos = new ArrayList<>();
        int scannedCount = 0;
        int claimConflictCount = 0;
        for (int index = 0; index < limit; index++) {
            String claimToken = UUID.randomUUID().toString();
            List<ReviewEvidenceExportJob> claimed = reviewItemStore.claimProcessableExportJobs(
                    1,
                    claimToken,
                    command.operatorUserId(),
                    processedAt,
                    processedAt.minusMinutes(EXPORT_WORKER_RECLAIM_MINUTES)
            );
            if (claimed.isEmpty()) {
                break;
            }
            ReviewEvidenceExportJob job = claimed.get(0);
            scannedCount++;
            int attemptCount = exportWorkerAttemptCount(job) + 1;
            try {
                if (isExportJobExpired(job, processedAt)) {
                    ReviewEvidenceExportJob expired = completeExportJobWorkerState(
                            job,
                            claimToken,
                            EXPORT_JOB_EXPIRED,
                            attemptCount,
                            "expired",
                            command.operatorUserId(),
                            processedAt,
                            null,
                            "export package expired"
                    );
                    processedJobNos.add(expired.jobNo());
                    continue;
                }
                ReviewEvidenceExportPackage exportPackage = buildReviewEvidenceExportPackage(
                        exportCommandFromJob(job),
                        true,
                        job.expiresAt()
                );
                ReviewEvidenceExportPackage readyPackage = withExportWorkerManifest(
                        exportPackage,
                        attemptCount,
                        "ready",
                        command.operatorUserId(),
                        processedAt,
                        null,
                        null
                );
                reviewItemStore.completeExportJobClaim(new ReviewEvidenceExportJob(
                        job.jobNo(),
                        EXPORT_JOB_READY,
                        readyPackage,
                        exportFileHash(readyPackage),
                        job.expiresAt(),
                        job.operatorUserId(),
                        job.reason(),
                        boundEventIds(readyPackage.reviewItemIds()),
                        job.createdAt(),
                        job.version()
                ), claimToken);
                processedJobNos.add(job.jobNo());
            } catch (RuntimeException ex) {
                if (isExportJobClaimConflict(ex)) {
                    deferredJobNos.add(job.jobNo());
                    claimConflictCount++;
                    continue;
                }
                try {
                    ReviewEvidenceExportJob failed = completeExportJobWorkerState(
                            job,
                            claimToken,
                            EXPORT_JOB_FAILED,
                            attemptCount,
                            "failed",
                            command.operatorUserId(),
                            processedAt,
                            exportWorkerNextRetryAt(processedAt, attemptCount),
                            ex.getMessage()
                    );
                    failedJobNos.add(failed.jobNo());
                } catch (RuntimeException completionException) {
                    if (!isExportJobClaimConflict(completionException)) {
                        throw completionException;
                    }
                    deferredJobNos.add(job.jobNo());
                    claimConflictCount++;
                }
            }
        }
        List<ReviewEvidenceExportJob> jobsAfterRun = reviewItemStore.listAllExportJobs();
        int remainingBacklog = (int) jobsAfterRun.stream()
                .filter(job -> shouldProcessExportJob(job, processedAt))
                .count();
        jobsAfterRun.stream()
                .filter(job -> isExportWorkerDeferred(job, processedAt))
                .map(ReviewEvidenceExportJob::jobNo)
                .forEach(deferredJobNos::add);
        List<String> distinctDeferredJobNos = List.copyOf(new LinkedHashSet<>(deferredJobNos));
        int deferredCount = distinctDeferredJobNos.size();
        return new ReviewEvidenceExportWorkerRun(
                exportWorkerStatus(processedJobNos.size(), failedJobNos.size(), remainingBacklog + claimConflictCount),
                scannedCount,
                processedJobNos.size(),
                failedJobNos.size(),
                deferredCount,
                remainingBacklog,
                List.copyOf(processedJobNos),
                List.copyOf(failedJobNos),
                distinctDeferredJobNos,
                processedAt,
                command.operatorUserId()
        );
    }

    private static boolean isExportJobClaimConflict(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().startsWith("export_job_claim_conflict:")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private ReviewEvidenceExportCommand exportCommandFromJob(ReviewEvidenceExportJob job) {
        Map<String, Object> approval = toStringObjectMap(job.exportPackage().manifest().get("approval"));
        return new ReviewEvidenceExportCommand(
                job.exportPackage().reviewCaseId(),
                job.exportPackage().reviewItemIds(),
                job.operatorUserId(),
                job.exportPackage().format(),
                job.reason(),
                toLong(approval.get("approvedBy")),
                toText(approval.get("approvalNote"))
        );
    }

    private ReviewEvidenceExportJob completeExportJobWorkerState(ReviewEvidenceExportJob job,
                                                                 String claimToken,
                                                                 String status,
                                                                 int attemptCount,
                                                                 String workerStatus,
                                                                 Long operatorUserId,
                                                                 LocalDateTime processedAt,
                                                                 LocalDateTime nextRetryAt,
                                                                 String lastError) {
        ReviewEvidenceExportPackage exportPackage = withExportWorkerManifest(
                job.exportPackage(),
                attemptCount,
                workerStatus,
                operatorUserId,
                processedAt,
                nextRetryAt,
                lastError
        );
        return reviewItemStore.completeExportJobClaim(new ReviewEvidenceExportJob(
                job.jobNo(),
                status,
                exportPackage,
                exportFileHash(exportPackage),
                job.expiresAt(),
                job.operatorUserId(),
                job.reason(),
                job.boundEventIds(),
                job.createdAt(),
                job.version()
        ), claimToken);
    }

    private ReviewEvidenceExportPackage withExportWorkerManifest(ReviewEvidenceExportPackage exportPackage,
                                                                 int attemptCount,
                                                                 String workerStatus,
                                                                 Long operatorUserId,
                                                                 LocalDateTime processedAt,
                                                                 LocalDateTime nextRetryAt,
                                                                 String lastError) {
        Map<String, Object> manifest = new LinkedHashMap<>(exportPackage.manifest());
        Map<String, Object> worker = new LinkedHashMap<>();
        worker.put("status", workerStatus);
        worker.put("attemptCount", attemptCount);
        worker.put("operatorUserId", operatorUserId);
        worker.put("processedAt", processedAt == null ? null : processedAt.toString());
        worker.put("nextRetryAt", nextRetryAt == null ? null : nextRetryAt.toString());
        worker.put("lastError", lastError);
        manifest.put("worker", immutableNonNullMap(worker));
        manifest.remove("manifestHash");
        manifest.remove("signature");
        String manifestHash = expectedManifestHash(manifest);
        manifest.put("manifestHash", manifestHash);
        manifest.put("signature", manifestSignature(manifest, manifestHash, processedAt));
        return new ReviewEvidenceExportPackage(
                exportPackage.packageNo(),
                exportPackage.format(),
                exportPackage.reviewCaseId(),
                exportPackage.reviewItemIds(),
                exportPackage.evidenceUris(),
                exportPackage.timeline(),
                immutableNonNullMap(manifest),
                exportPackage.generatedAt()
        );
    }

    private static boolean shouldProcessExportJob(ReviewEvidenceExportJob job, LocalDateTime now) {
        if (job == null) {
            return false;
        }
        if (EXPORT_JOB_READY.equals(job.status())) {
            return isExportJobExpired(job, now);
        }
        if (EXPORT_JOB_RUNNING.equals(job.status())) {
            LocalDateTime processedAt = exportWorkerProcessedAt(job);
            return processedAt == null
                    || (now != null && processedAt.isBefore(now.minusMinutes(EXPORT_WORKER_RECLAIM_MINUTES)));
        }
        if (!EXPORT_JOB_PENDING.equals(job.status())
                && !EXPORT_JOB_FAILED.equals(job.status())) {
            return false;
        }
        return !isExportWorkerDeferred(job, now);
    }

    private static boolean isExportJobExpired(ReviewEvidenceExportJob job, LocalDateTime now) {
        return job != null
                && job.expiresAt() != null
                && now != null
                && !job.expiresAt().isAfter(now);
    }

    private static boolean isExportWorkerDeferred(ReviewEvidenceExportJob job, LocalDateTime now) {
        if (job == null || !EXPORT_JOB_FAILED.equals(job.status())) {
            return false;
        }
        LocalDateTime nextRetryAt = exportWorkerNextRetryAt(job);
        return nextRetryAt != null && now != null && nextRetryAt.isAfter(now);
    }

    private static LocalDateTime exportWorkerNextRetryAt(ReviewEvidenceExportJob job) {
        if (job == null || job.exportPackage() == null || job.exportPackage().manifest() == null) {
            return null;
        }
        return toLocalDateTime(toStringObjectMap(job.exportPackage().manifest().get("worker")).get("nextRetryAt"));
    }

    private static int exportWorkerAttemptCount(ReviewEvidenceExportJob job) {
        if (job == null || job.exportPackage() == null || job.exportPackage().manifest() == null) {
            return 0;
        }
        Long attemptCount = toLong(toStringObjectMap(job.exportPackage().manifest().get("worker")).get("attemptCount"));
        if (attemptCount == null || attemptCount < 0) {
            return 0;
        }
        return Math.toIntExact(Math.min(attemptCount, Integer.MAX_VALUE));
    }

    private static LocalDateTime exportWorkerProcessedAt(ReviewEvidenceExportJob job) {
        if (job == null || job.exportPackage() == null || job.exportPackage().manifest() == null) {
            return null;
        }
        return toLocalDateTime(toStringObjectMap(job.exportPackage().manifest().get("worker")).get("processedAt"));
    }

    private static LocalDateTime exportWorkerNextRetryAt(LocalDateTime processedAt, int attemptCount) {
        int backoffStep = Math.min(Math.max(attemptCount - 1, 0), 3);
        int minutes = Math.min(60, Math.max(5, 5 * (1 << backoffStep)));
        return processedAt.plusMinutes(minutes);
    }

    private static String exportWorkerStatus(int processedCount, int failedCount, int remainingBacklogCount) {
        if (failedCount > 0) {
            return "failed";
        }
        if (remainingBacklogCount > 0) {
            return "partial";
        }
        if (processedCount > 0) {
            return "completed";
        }
        return "idle";
    }

    @Override
    public ReviewManifestVerification verifyEvidenceExportManifest(String jobNo) {
        return verifyEvidenceExportManifest(jobNo, null, null);
    }

    @Override
    public ReviewManifestVerification verifyEvidenceExportManifest(String jobNo,
                                                                   Long operatorUserId,
                                                                   List<String> allowedCameraIds) {
        requireText(jobNo, "jobNo");
        ReviewEvidenceExportJob job = reviewItemStore.findExportJobByNo(jobNo)
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + jobNo));
        enforceMediaAccessScope(
                job.exportPackage().reviewCaseId(),
                job.exportPackage().timeline(),
                loadReviewItems(job.exportPackage().reviewItemIds()),
                operatorUserId,
                "manifest_verify",
                allowedCameraIds,
                "manifest verify"
        );
        Map<String, Object> manifest = job.exportPackage().manifest();
        String actualManifestHash = toText(manifest.get("manifestHash"));
        String expectedManifestHash = expectedManifestHash(manifest);
        String packageChecksum = toText(manifest.get("packageChecksum"));
        List<String> violations = new ArrayList<>();
        if (!hasText(actualManifestHash)) {
            violations.add("missing_manifest_hash");
        } else if (!Objects.equals(expectedManifestHash, actualManifestHash)) {
            violations.add("manifest_hash_mismatch");
        }
        ReviewEvidenceManifestSigner.Verification signatureVerification = evidenceManifestSigner.verify(
                toStringObjectMap(manifest.get("signature")),
                expectedManifestHash
        );
        if (!signatureVerification.valid()) {
            violations.add(signatureVerification.violation());
        }
        if (!hasText(packageChecksum)) {
            violations.add("missing_package_checksum");
        }
        return new ReviewManifestVerification(
                jobNo,
                violations.isEmpty(),
                expectedManifestHash,
                actualManifestHash,
                packageChecksum,
                List.copyOf(violations),
                LocalDateTime.now()
        );
    }

    @Override
    public ReviewEvidenceVerificationReport verifyEvidencePackage(ReviewEvidenceVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.jobNo(), "jobNo");
        ReviewManifestVerification manifestVerification = verifyEvidenceExportManifest(
                command.jobNo(),
                command.operatorUserId(),
                command.allowedCameraIds()
        );
        ReviewEvidenceExportJob job = reviewItemStore.findExportJobByNo(command.jobNo())
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + command.jobNo()));
        Map<String, Object> manifest = new LinkedHashMap<>(job.exportPackage().manifest());
        Long reviewCaseId = toLong(manifest.get("reviewCaseId"));
        List<ReviewEvidenceAuditEntry> auditTrail = reviewCaseId == null ? List.of() : getEvidenceAuditTrail(reviewCaseId);
        manifest.put("downloadRecords", auditTrail.stream()
                .filter(entry -> "export_downloaded".equals(entry.actionType()))
                .map(SupervisionAlertReviewServiceImpl::auditEntryToManifestMap)
                .toList());
        List<Map<String, Object>> decisionTrail = toMapList(manifest.get("decisionTrail"));
        if (decisionTrail.isEmpty()) {
            decisionTrail = reconstructDecisionTrail(job.exportPackage().reviewItemIds());
        }
        List<String> replayableReasons = new ArrayList<>();
        if (manifestVerification.valid()) {
            replayableReasons.add("manifest_hash_valid");
            replayableReasons.add("signature_valid");
        }
        if (!decisionTrail.isEmpty()) {
            replayableReasons.add("decision_trail_reconstructed");
        }
        if (!toMapList(manifest.get("coverageSummary")).isEmpty()) {
            replayableReasons.add("coverage_summary_present");
        }
        if (!toStringObjectMap(manifest.get("reviewData")).isEmpty()) {
            replayableReasons.add("review_data_present");
        }
        if (!auditTrail.isEmpty()) {
            replayableReasons.add("audit_trail_attached");
        }
        if (!toMapList(manifest.get("ruleVersions")).isEmpty()) {
            replayableReasons.add("rule_version_present");
        }
        return new ReviewEvidenceVerificationReport(
                command.jobNo(),
                manifestVerification.valid(),
                manifestVerification,
                immutableNonNullMap(manifest),
                decisionTrail,
                List.copyOf(replayableReasons),
                auditTrail,
                LocalDateTime.now(),
                command.operatorUserId()
        );
    }

    @Override
    public ReviewIntegrationSmokeResult runIntegrationSmoke(ReviewIntegrationSmokeCommand command) {
        Objects.requireNonNull(command, "command");
        LocalDateTime alertTime = command.alertTime() == null ? LocalDateTime.now() : command.alertTime();
        boolean includeVideoExport = Boolean.TRUE.equals(command.includeVideoExport());
        String profile = firstText(command.profile(), "service-synthetic");
        boolean realProfile = "device-video-web".equals(profile) || "release".equals(profile);
        if (realProfile) {
            requirePositive(command.operatorUserId(), "operatorUserId");
            requireText(command.deviceId(), "deviceId");
            requireText(command.cameraId(), "cameraId");
            requireText(command.zoneCode(), "zoneCode");
            if (!command.deviceId().equals(command.cameraId())) {
                throw new IllegalArgumentException(
                        "real integration smoke requires deviceId and cameraId to identify the same VIDEO camera");
            }
            if (!includeVideoExport) {
                throw new IllegalArgumentException("real integration smoke requires video export");
            }
            List<String> requestedCameraIds = normalizeCameraScope(command.allowedCameraIds());
            if (requestedCameraIds == null || !requestedCameraIds.contains(command.cameraId())) {
                throw new IllegalArgumentException("real integration smoke allowedCameraIds must contain cameraId");
            }
        }
        List<String> checkpoints = new ArrayList<>();
        if (realProfile) {
            checkpoints.add("device_api_reachable");
            checkpoints.add("web_contract_checked");
        }
        String sourceAlertId = firstText(command.sourceAlertId(), "review-smoke-" + UUID.randomUUID());
        String deviceId = realProfile ? command.deviceId() : "device-smoke";
        String cameraId = realProfile ? command.cameraId() : "camera-smoke";
        String zoneCode = realProfile ? command.zoneCode() : "zone-smoke";
        ReviewItemAggregate item = ingestClue(new AlertClueCommand(
                "video",
                sourceAlertId,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "restricted_area",
                alertTime,
                deviceId,
                cameraId,
                zoneCode,
                "person",
                15,
                realProfile ? null : "smoke-snapshot.jpg",
                realProfile ? null : "smoke-record.mp4",
                "smoke:" + sourceAlertId,
                List.of("person"),
                List.of(zoneCode),
                List.of("obj-smoke"),
                0.9D,
                List.of(0D, 0D, 10D, 20D),
                sourceAlertId
        ));
        checkpoints.add("ingest_review_item");
        if (realProfile) {
            boolean resolvedRecord = RECORD_EVIDENCE_FOUND.equals(item.recordEvidenceStatus())
                    && reviewItemStore.listTimeline(item.id()).stream()
                    .anyMatch(evidence -> MATERIAL_RECORD.equals(evidence.materialType())
                            && hasText(evidence.materialUri()));
            if (!resolvedRecord) {
                throw new IllegalStateException("real VIDEO alert record query did not resolve recording: "
                        + firstText(item.recordEvidenceMessage(), "unknown"));
            }
            checkpoints.add("video_record_query_checked");
            checkpoints.add("sample_alert_ingested");
        }
        ReviewRuleView smokeRule = saveRule(new ReviewRuleCommand(
                null,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                "integration smoke zone rule",
                "video",
                cameraId,
                zoneCode,
                "person",
                15,
                alertTime.minusMinutes(5),
                null,
                true,
                3,
                20
        ));
        checkpoints.add("review_rule_saved");
        List<RecordCoverageSegment> coverage = getRecordCoverage(item.id());
        if (realProfile && coverage.stream().noneMatch(segment -> segment != null
                && !RECORD_COVERAGE_MISSING.equals(normalizeCoverageStatus(segment))
                && hasText(segment.recordUri()))) {
            throw new IllegalStateException("real VIDEO coverage did not return an exportable recording segment");
        }
        syncRecordStorage(new ReviewRecordStorageSyncCommand(item.id(), command.operatorUserId(), coverage));
        checkpoints.add("record_coverage_synced");
        if (realProfile) {
            checkpoints.add("real_record_coverage_checked");
            checkpoints.add("sample_record_coverage_probed");
        }
        ReviewToEventResult eventResult = convertToEvent(new ReviewToEventCommand(
                item.id(),
                command.operatorUserId(),
                false
        ));
        Long eventId = eventResult.eventId();
        if (eventId == null || eventId <= 0L) {
            throw new IllegalStateException("integration smoke event conversion did not return a positive eventId");
        }
        checkpoints.add("review_event_bound");
        checkpoints.add("review_event_bound_without_task_dispatch");
        ReviewCaseView reviewCase = createReviewCase(new ReviewCaseCommand(
                "integration smoke " + sourceAlertId,
                item.id(),
                List.of(item.id()),
                command.operatorUserId(),
                "runtime smoke"
        ));
        checkpoints.add("review_case_created");
        ReviewEvidenceExportJob queuedJob = createReviewEvidenceExportJob(new ReviewEvidenceExportCommand(
                reviewCase.id(),
                List.of(item.id()),
                command.operatorUserId(),
                includeVideoExport ? "mp4" : "manifest",
                "integration smoke",
                null,
                null,
                command.allowedCameraIds()
        ));
        checkpoints.add("evidence_export_queued");
        processEvidenceExportQueue(new ReviewEvidenceExportWorkerCommand(1, command.operatorUserId()));
        ReviewEvidenceExportJob job = reviewItemStore.findExportJobByNo(queuedJob.jobNo())
                .orElseThrow(() -> new IllegalStateException("evidence export job disappeared"));
        if (!EXPORT_JOB_READY.equals(job.status())) {
            String workerError = toText(toStringObjectMap(
                    job.exportPackage().manifest().get("worker")
            ).get("lastError"));
            throw new IllegalStateException(firstText(
                    workerError,
                    "evidence export worker did not produce a ready package: " + job.status()
            ));
        }
        checkpoints.add("evidence_export_ready");
        if (job.boundEventIds() == null || !job.boundEventIds().contains(eventId)) {
            throw new IllegalStateException("integration smoke evidence export lost event binding: " + eventId);
        }
        checkpoints.add("evidence_export_event_bound");
        boolean videoExportConfirmed = hasConfirmedVideoExport(job);
        if (videoExportConfirmed) {
            checkpoints.add("video_export_confirmed");
        } else if (realProfile) {
            throw new IllegalStateException("VIDEO export did not return a real artifact");
        }
        ReviewManifestVerification verification = verifyEvidenceExportManifest(
                job.jobNo(),
                command.operatorUserId(),
                command.allowedCameraIds()
        );
        if (verification.valid()) {
            checkpoints.add("manifest_verified");
            if (includeVideoExport) {
                try (ReviewEvidenceDownloadArtifact artifact = downloadEvidencePackage(
                        new ReviewEvidenceDownloadCommand(
                                job.jobNo(),
                                command.operatorUserId(),
                                command.allowedCameraIds(),
                                "integration smoke download audit"
                        ))) {
                    long actualLength = Files.size(artifact.temporaryFile());
                    String actualHash = sha256File(artifact.temporaryFile());
                    if (actualLength <= 0 || actualLength != artifact.contentLength()) {
                        throw new IllegalStateException("integration smoke downloaded package length mismatch");
                    }
                    if (!sha256HashesEqual(actualHash, artifact.fileHash())) {
                        throw new IllegalStateException("integration smoke downloaded package hash mismatch");
                    }
                    if (artifact.auditEntry() == null
                            || !"export_downloaded".equals(artifact.auditEntry().actionType())
                            || !sha256HashesEqual(actualHash, artifact.auditEntry().fileHash())) {
                        throw new IllegalStateException("integration smoke download audit did not bind real bytes");
                    }
                    checkpoints.add("evidence_download_bytes_verified");
                    checkpoints.add("evidence_download_audited");
                } catch (IllegalStateException | SecurityException exception) {
                    throw exception;
                } catch (Exception exception) {
                    throw new IllegalStateException("integration smoke evidence download failed", exception);
                }
            }
        }
        if (realProfile) {
            checkpoints.add("sample_web_contract_renderable");
        }
        return new ReviewIntegrationSmokeResult(
                verification.valid() ? "passed" : "failed",
                item.id(),
                eventId,
                reviewCase.id(),
                job.jobNo(),
                verification.valid(),
                includeVideoExport,
                videoExportConfirmed,
                List.copyOf(checkpoints),
                LocalDateTime.now(),
                command.operatorUserId(),
                profile,
                smokeRule
        );
    }

    private static boolean hasConfirmedVideoExport(ReviewEvidenceExportJob job) {
        return toMapList(job.exportPackage().manifest().get("videoExports")).stream()
                .anyMatch(SupervisionAlertReviewServiceImpl::hasCompleteVideoExportProvenance);
    }

    @Override
    public List<ReviewEvidenceAuditEntry> getEvidenceAuditTrail(Long reviewCaseId) {
        requirePositive(reviewCaseId, "reviewCaseId");
        List<ReviewEvidenceAuditEntry> auditTrail = new ArrayList<>();
        for (ReviewEvidenceExportJob job : reviewItemStore.listExportJobs(reviewCaseId)) {
            auditTrail.add(exportJobAuditEntry(job));
        }
        for (ReviewCaseTimelineItem timelineItem : reviewItemStore.listCaseTimeline(reviewCaseId)) {
            if (!"case_audit".equals(timelineItem.materialType())
                    || (!"export_downloaded".equals(timelineItem.materialUri())
                    && !isMediaAccessAuditAction(timelineItem.materialUri()))) {
                continue;
            }
            if ("export_downloaded".equals(timelineItem.materialUri())) {
                auditTrail.add(downloadAuditEntry(reviewCaseId, timelineItem));
            } else {
                auditTrail.add(mediaAccessAuditEntry(reviewCaseId, timelineItem));
            }
        }
        List<ReviewEvidenceAuditEntry> sortedTrail = auditTrail.stream()
                .sorted(Comparator.comparing(
                        ReviewEvidenceAuditEntry::happenedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return withAuditHashChain(sortedTrail);
    }

    @Override
    public List<ReviewEvidenceAuditEntry> getReviewItemEvidenceAuditTrail(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        List<ReviewEvidenceAuditEntry> auditTrail = reviewItemStore.listMediaAccessAuditsByReviewItem(reviewItemId)
                .stream()
                .filter(timelineItem -> "case_audit".equals(timelineItem.materialType()))
                .filter(timelineItem -> isMediaAccessAuditAction(timelineItem.materialUri()))
                .map(timelineItem -> mediaAccessAuditEntry(timelineItem.reviewCaseId(), timelineItem))
                .sorted(Comparator.comparing(
                        ReviewEvidenceAuditEntry::happenedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return withAuditHashChain(auditTrail);
    }

    @Override
    public List<ReviewEvidenceAuditEntry> queryEvidenceAuditTrail(ReviewEvidenceAuditQuery query) {
        Objects.requireNonNull(query, "query");
        if (!query.hasCriteria()) {
            throw new IllegalArgumentException(
                    "at least one of eventId, reviewCaseId, reviewItemId or exportJobNo is required");
        }
        if (query.eventId() != null) {
            requirePositive(query.eventId(), "eventId");
        }
        if (query.reviewCaseId() != null) {
            requirePositive(query.reviewCaseId(), "reviewCaseId");
        }
        if (query.reviewItemId() != null) {
            requirePositive(query.reviewItemId(), "reviewItemId");
        }
        if (query.exportJobNo() != null) {
            requireText(query.exportJobNo(), "exportJobNo");
        }

        List<ReviewEvidenceAuditEntry> auditTrail = new ArrayList<>();
        reviewItemStore.listEvidenceAuditExportJobs(query).stream()
                .map(SupervisionAlertReviewServiceImpl::exportJobAuditEntry)
                .forEach(auditTrail::add);
        for (ReviewCaseTimelineItem timelineItem : reviewItemStore.listEvidenceAuditRecords(query)) {
            if (!"case_audit".equals(timelineItem.materialType())) {
                continue;
            }
            if ("export_downloaded".equals(timelineItem.materialUri())) {
                auditTrail.add(downloadAuditEntry(timelineItem.reviewCaseId(), timelineItem));
            } else if (isMediaAccessAuditAction(timelineItem.materialUri())) {
                auditTrail.add(mediaAccessAuditEntry(timelineItem.reviewCaseId(), timelineItem));
            }
        }
        List<ReviewEvidenceAuditEntry> sortedTrail = auditTrail.stream()
                .sorted(Comparator
                        .comparing(ReviewEvidenceAuditEntry::happenedAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceAuditEntry::actionType,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceAuditEntry::jobNo,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        return withAuditHashChain(sortedTrail);
    }

    @Override
    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo, Long operatorUserId, String reason) {
        return recordEvidenceDownload(jobNo, operatorUserId, reason, null);
    }

    @Override
    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                           Long operatorUserId,
                                                           String reason,
                                                           List<String> allowedCameraIds) {
        requireText(jobNo, "jobNo");
        ReviewEvidenceExportJob job = reviewItemStore.findExportJobByNo(jobNo)
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + jobNo));
        LocalDateTime happenedAt = LocalDateTime.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", "denied");
        metadata.put("deniedReasons", List.of("audit_only_endpoint_disabled"));
        metadata.put("jobNo", jobNo);
        metadata.put("logicalPackageHash", job.fileHash());
        metadata.put("reason", reason);
        metadata.put("allowedCameraIds", allowedCameraIds == null ? List.of() : List.copyOf(allowedCameraIds));
        String actionNote = "decision=denied; action=download; jobNo=" + jobNo
                + "; deniedReasons=audit_only_endpoint_disabled"
                + (hasText(reason) ? "; reason=" + reason : "");
        reviewItemStore.recordMediaAccessAudit(
                job.exportPackage().reviewCaseId(),
                null,
                "evidence_download_denied",
                actionNote,
                operatorUserId,
                happenedAt,
                immutableNonNullMap(metadata)
        );
        return new ReviewEvidenceAuditEntry(
                job.exportPackage().reviewCaseId(),
                null,
                "evidence_download_denied",
                jobNo,
                null,
                operatorUserId,
                actionNote,
                job.exportPackage().evidenceUris(),
                job.boundEventIds(),
                happenedAt,
                immutableNonNullMap(metadata)
        );
    }

    private ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                            Long operatorUserId,
                                                            String reason,
                                                            List<String> allowedCameraIds,
                                                            String downloadFileHash,
                                                            Map<String, Object> downloadMetadata) {
        requireText(jobNo, "jobNo");
        requireText(downloadFileHash, "downloadFileHash");
        ReviewEvidenceExportJob job = reviewItemStore.findExportJobByNo(jobNo)
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + jobNo));
        assertExportJobDownloadable(job, LocalDateTime.now());
        ReviewEvidenceAuditEntry auditEntry = reviewItemStore.recordEvidenceDownload(
                jobNo,
                operatorUserId,
                reason,
                LocalDateTime.now(),
                downloadFileHash,
                downloadMetadata
        );
        return enrichDownloadAuditEntry(auditEntry, job);
    }

    @Override
    public ReviewEvidenceDownloadArtifact downloadEvidencePackage(ReviewEvidenceDownloadCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.jobNo(), "jobNo");
        ReviewEvidenceExportJob job = reviewItemStore.findExportJobByNo(command.jobNo())
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + command.jobNo()));
        assertExportJobDownloadable(job, LocalDateTime.now());
        enforceMediaAccessScope(
                job.exportPackage().reviewCaseId(),
                job.exportPackage().timeline(),
                loadReviewItems(job.exportPackage().reviewItemIds()),
                command.operatorUserId(),
                "download",
                command.allowedCameraIds(),
                command.reason()
        );
        ReviewManifestVerification manifestVerification = verifyEvidenceExportManifest(
                job.jobNo(),
                command.operatorUserId(),
                command.allowedCameraIds()
        );
        if (!manifestVerification.valid()) {
            throw new SecurityException("evidence manifest trust verification failed: "
                    + String.join(",", manifestVerification.violations()));
        }
        List<Map<String, Object>> videoExports = videoExportsForDownload(job);
        List<ReviewEvidenceDownloadArtifact> downloadedArtifacts = new ArrayList<>();
        try {
            for (Map<String, Object> videoExport : videoExports) {
                downloadedArtifacts.add(downloadVideoExport(job, videoExport));
            }
            ReviewEvidenceDownloadArtifact packageArtifact;
            if (downloadedArtifacts.size() == 1) {
                Map<String, Object> videoExport = videoExports.get(0);
                String expectedFileHash = toText(videoExport.get("fileHash"));
                if (!sha256HashesEqual(expectedFileHash, job.fileHash())) {
                    throw new IllegalStateException("export job file hash does not reference the ready VIDEO package");
                }
                packageArtifact = downloadedArtifacts.remove(0);
            } else {
                packageArtifact = createMultiCameraEvidencePackage(job, videoExports, downloadedArtifacts);
                downloadedArtifacts.clear();
            }
            try {
                ReviewEvidenceAuditEntry auditEntry = recordEvidenceDownload(
                        job.jobNo(),
                        command.operatorUserId(),
                        command.reason(),
                        command.allowedCameraIds(),
                        packageArtifact.fileHash(),
                        evidenceDownloadMetadata(job, packageArtifact, videoExports.size())
                );
                return new ReviewEvidenceDownloadArtifact(
                        job.jobNo(),
                        firstText(packageArtifact.fileName(), job.jobNo() + ".bin"),
                        firstText(packageArtifact.contentType(), "application/octet-stream"),
                        packageArtifact.temporaryFile(),
                        packageArtifact.contentLength(),
                        packageArtifact.fileHash(),
                        auditEntry
                );
            } catch (RuntimeException exception) {
                closeDownloadArtifactQuietly(packageArtifact);
                throw exception;
            }
        } finally {
            downloadedArtifacts.forEach(SupervisionAlertReviewServiceImpl::closeDownloadArtifactQuietly);
        }
    }

    private static List<Map<String, Object>> videoExportsForDownload(ReviewEvidenceExportJob job) {
        List<Map<String, Object>> videoExports = toMapList(job.exportPackage().manifest().get("videoExports"));
        if (videoExports.isEmpty() || videoExports.stream()
                .anyMatch(videoExport -> !hasCompleteVideoExportProvenance(videoExport))) {
            throw new IllegalStateException("export job must reference ready VIDEO package artifacts");
        }
        return videoExports;
    }

    private static Map<String, Object> evidenceDownloadMetadata(ReviewEvidenceExportJob job,
                                                                ReviewEvidenceDownloadArtifact artifact,
                                                                int artifactCount) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("downloadFileHash", artifact.fileHash());
        metadata.put("logicalPackageHash", job.fileHash());
        metadata.put("manifestPackageChecksum", job.exportPackage().manifest().get("packageChecksum"));
        metadata.put("fileName", artifact.fileName());
        metadata.put("contentType", artifact.contentType());
        metadata.put("contentLength", artifact.contentLength());
        metadata.put("artifactCount", artifactCount);
        metadata.put("hashSource", "downloaded_bytes");
        return immutableNonNullMap(metadata);
    }

    private ReviewEvidenceDownloadArtifact downloadVideoExport(ReviewEvidenceExportJob job,
                                                                 Map<String, Object> videoExport) {
        String exportUri = toText(videoExport.get("exportUri"));
        String expectedFileHash = toText(videoExport.get("fileHash"));
        if (!hasText(exportUri) || !isSha256Hash(expectedFileHash)) {
            throw new IllegalStateException("ready export job has no verifiable VIDEO package");
        }
        String cameraId = videoExportCameraId(videoExport, job.exportPackage().timeline());
        ReviewEvidenceDownloadArtifact downloaded = videoEvidenceExportProvider.download(
                        new ReviewEvidenceVideoDownloadRequest(exportUri, cameraId, expectedFileHash))
                .orElseThrow(() -> new IllegalStateException("VIDEO package download is unavailable"));
        try {
            if (!Files.isRegularFile(downloaded.temporaryFile()) || downloaded.contentLength() <= 0L) {
                throw new IllegalStateException("VIDEO package download returned no bytes");
            }
            long actualLength = Files.size(downloaded.temporaryFile());
            if (actualLength != downloaded.contentLength()) {
                throw new SecurityException("VIDEO package byte length mismatch");
            }
            String actualFileHash = sha256File(downloaded.temporaryFile());
            if (!sha256HashesEqual(expectedFileHash, actualFileHash)
                    || !sha256HashesEqual(actualFileHash, downloaded.fileHash())) {
                throw new SecurityException("VIDEO package byte hash mismatch");
            }
            return downloaded;
        } catch (RuntimeException exception) {
            closeDownloadArtifactQuietly(downloaded);
            throw exception;
        } catch (Exception exception) {
            closeDownloadArtifactQuietly(downloaded);
            throw new IllegalStateException("VIDEO package download verification failed", exception);
        }
    }

    private ReviewEvidenceDownloadArtifact createMultiCameraEvidencePackage(
            ReviewEvidenceExportJob job,
            List<Map<String, Object>> videoExports,
            List<ReviewEvidenceDownloadArtifact> artifacts) {
        if (videoExports.size() != artifacts.size() || artifacts.size() < 2) {
            throw new IllegalStateException("multi-camera evidence package artifacts do not reconcile");
        }
        Path packageFile = createEvidencePackageTemporaryFile();
        try {
            List<Map<String, Object>> artifactMetadata = new ArrayList<>();
            for (int index = 0; index < artifacts.size(); index++) {
                ReviewEvidenceDownloadArtifact artifact = artifacts.get(index);
                Map<String, Object> videoExport = videoExports.get(index);
                String cameraId = videoExportCameraId(videoExport, job.exportPackage().timeline());
                String entryName = "artifacts/" + String.format(Locale.ROOT, "%03d", index)
                        + "-" + safeArchiveFileName(artifact.fileName(), cameraId + ".mp4");
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("packageOrder", index);
                metadata.put("entryName", entryName);
                metadata.put("cameraId", cameraId);
                metadata.put("exportId", videoExport.get("exportId"));
                metadata.put("fileName", artifact.fileName());
                metadata.put("contentType", artifact.contentType());
                metadata.put("contentLength", artifact.contentLength());
                metadata.put("fileHash", artifact.fileHash());
                metadata.put("ffmpegCommandHash", videoExport.get("ffmpegCommandHash"));
                metadata.put("recordSegments", videoExport.get("recordSegments"));
                artifactMetadata.add(immutableNonNullMap(metadata));
            }
            String logicalPackageHash = sha256Token(
                    "review-evidence-multi-camera-package-v1",
                    job.jobNo(),
                    job.exportPackage().manifest().get("manifestHash"),
                    artifactMetadata
            );
            Map<String, Object> packageMetadata = new LinkedHashMap<>();
            packageMetadata.put("schema", "yfeieye-evidence-package-v1");
            packageMetadata.put("packageType", "multi-camera-zip");
            packageMetadata.put("jobNo", job.jobNo());
            packageMetadata.put("packageNo", job.exportPackage().packageNo());
            packageMetadata.put("reviewCaseId", job.exportPackage().reviewCaseId());
            packageMetadata.put("reviewItemIds", job.exportPackage().reviewItemIds());
            packageMetadata.put("packageHash", logicalPackageHash);
            packageMetadata.put("packageChecksum", job.exportPackage().manifest().get("packageChecksum"));
            packageMetadata.put("manifestHash", job.exportPackage().manifest().get("manifestHash"));
            packageMetadata.put("artifactCount", artifacts.size());
            packageMetadata.put("artifactOrder", artifactMetadata.stream()
                    .map(metadata -> metadata.get("entryName"))
                    .toList());
            packageMetadata.put("artifacts", List.copyOf(artifactMetadata));

            MessageDigest archiveDigest = sha256Digest();
            try (OutputStream fileOutput = Files.newOutputStream(
                    packageFile,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
                 DigestOutputStream digestOutput = new DigestOutputStream(
                         new BufferedOutputStream(fileOutput), archiveDigest);
                 ZipOutputStream zip = new ZipOutputStream(digestOutput, StandardCharsets.UTF_8)) {
                writeZipEntry(zip, "manifest.json", JsonUtils.toJsonString(job.exportPackage().manifest()));
                writeZipEntry(zip, "audit.json", JsonUtils.toJsonString(
                        getEvidenceAuditTrail(job.exportPackage().reviewCaseId())));
                writeZipEntry(zip, "metadata.json", JsonUtils.toJsonString(packageMetadata));
                for (int index = 0; index < artifacts.size(); index++) {
                    String entryName = toText(artifactMetadata.get(index).get("entryName"));
                    writeZipFile(zip, entryName, artifacts.get(index).temporaryFile());
                }
            }
            long contentLength = Files.size(packageFile);
            if (contentLength <= 0L) {
                throw new IllegalStateException("multi-camera evidence ZIP is empty");
            }
            return new ReviewEvidenceDownloadArtifact(
                    job.jobNo(),
                    safeArchiveFileName(job.jobNo(), "evidence-package") + ".zip",
                    "application/zip",
                    packageFile,
                    contentLength,
                    "sha256:" + java.util.HexFormat.of().formatHex(archiveDigest.digest()),
                    null
            );
        } catch (RuntimeException exception) {
            deleteTemporaryFile(packageFile);
            throw exception;
        } catch (Exception exception) {
            deleteTemporaryFile(packageFile);
            throw new IllegalStateException("unable to create multi-camera evidence ZIP", exception);
        } finally {
            artifacts.forEach(SupervisionAlertReviewServiceImpl::closeDownloadArtifactQuietly);
        }
    }

    private static void writeZipEntry(ZipOutputStream zip, String entryName, String content) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void writeZipFile(ZipOutputStream zip, String entryName, Path file) throws Exception {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(0L);
        zip.putNextEntry(entry);
        try (InputStream input = Files.newInputStream(file)) {
            input.transferTo(zip);
        }
        zip.closeEntry();
    }

    private static Path createEvidencePackageTemporaryFile() {
        try {
            return Files.createTempFile("yfeieye-evidence-package-", ".zip");
        } catch (Exception exception) {
            throw new IllegalStateException("unable to create evidence package temporary file", exception);
        }
    }

    private static String safeArchiveFileName(String fileName, String fallback) {
        String candidate = hasText(fileName) ? fileName.trim() : fallback;
        String sanitized = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!hasText(sanitized) || ".".equals(sanitized) || "..".equals(sanitized)) {
            sanitized = fallback;
        }
        return sanitized.length() <= 180 ? sanitized : sanitized.substring(0, 180);
    }

    private static String sha256File(Path file) {
        MessageDigest digest = sha256Digest();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("unable to hash evidence artifact", exception);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void closeDownloadArtifactQuietly(ReviewEvidenceDownloadArtifact artifact) {
        if (artifact == null) {
            return;
        }
        try {
            artifact.close();
        } catch (Exception ignored) {
            artifact.temporaryFile().toFile().deleteOnExit();
        }
    }

    private static void deleteTemporaryFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
            file.toFile().deleteOnExit();
        }
    }

    private static String videoExportCameraId(Map<String, Object> videoExport,
                                              List<ReviewCaseTimelineItem> timeline) {
        LinkedHashSet<String> cameraIds = new LinkedHashSet<>();
        for (Map<String, Object> segment : toMapList(videoExport.get("recordSegments"))) {
            String cameraId = firstText(toText(segment.get("cameraId")), toText(segment.get("camera_id")));
            if (hasText(cameraId)) {
                cameraIds.add(cameraId);
            }
        }
        if (cameraIds.isEmpty()) {
            for (ReviewCaseTimelineItem item : timeline == null ? List.<ReviewCaseTimelineItem>of() : timeline) {
                if (isEvidenceMedia(item.materialType()) && hasText(item.cameraId())) {
                    cameraIds.add(item.cameraId());
                }
            }
        }
        if (cameraIds.size() != 1) {
            throw new IllegalStateException("unable to resolve one camera for VIDEO package download");
        }
        return cameraIds.iterator().next();
    }

    private static void assertExportJobDownloadable(ReviewEvidenceExportJob job, LocalDateTime now) {
        if (!EXPORT_JOB_READY.equals(job.status())) {
            throw new IllegalStateException("export job is not ready for download: " + job.jobNo()
                    + " status=" + job.status());
        }
        if (isExportJobExpired(job, now)) {
            throw new IllegalStateException("export job expired: " + job.jobNo());
        }
    }

    @Override
    public ReviewMediaAccessAuditEntry auditMediaAccess(ReviewMediaAccessCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        List<ReviewCaseTimelineItem> timeline = command.reviewCaseId() == null
                ? List.of()
                : getReviewCaseTimeline(command.reviewCaseId());
        boolean itemInCase = command.reviewCaseId() == null
                || timeline.stream().anyMatch(row -> Objects.equals(command.reviewItemId(), row.reviewItemId()));
        boolean mediaInCase = command.reviewCaseId() == null
                || !hasText(command.materialUri())
                || timeline.stream()
                        .filter(row -> Objects.equals(command.reviewItemId(), row.reviewItemId()))
                        .anyMatch(row -> Objects.equals(command.materialUri(), row.materialUri()));
        String actionType = firstText(command.actionType(), "playback");
        List<String> effectiveAllowedCameraIds = resolveEffectiveAllowedCameraIds(
                command.reviewCaseId(),
                command.operatorUserId(),
                actionType,
                command.allowedCameraIds()
        );
        Set<String> allowedCameras = new LinkedHashSet<>(effectiveAllowedCameraIds == null
                ? List.of()
                : effectiveAllowedCameraIds);
        List<String> deniedReasons = new ArrayList<>();
        if (!itemInCase) {
            deniedReasons.add("item_not_in_case");
        }
        if (!mediaInCase) {
            deniedReasons.add("media_not_in_case");
        }
        if (!Objects.equals(item.cameraId(), command.cameraId())) {
            deniedReasons.add("camera_mismatch");
        }
        if (!allowedCameras.contains(item.cameraId())) {
            deniedReasons.add("camera_not_allowed");
        }
        String decision = deniedReasons.isEmpty() ? "granted" : "denied";
        LocalDateTime happenedAt = LocalDateTime.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", decision);
        metadata.put("allowedCameraIds", List.copyOf(allowedCameras));
        metadata.put("deniedReasons", List.copyOf(deniedReasons));
        metadata.put("reason", command.reason());
        metadata.put("eventId", item.eventId());
        String auditNote = "decision=" + decision
                + "; action=" + actionType
                + "; cameraId=" + command.cameraId()
                + "; materialUri=" + command.materialUri()
                + (command.operatorUserId() == null ? "" : "; operatorUserId=" + command.operatorUserId())
                + (deniedReasons.isEmpty() ? "" : "; deniedReasons=" + String.join(",", deniedReasons))
                + (hasText(command.reason()) ? "; reason=" + command.reason() : "");
        reviewItemStore.recordMediaAccessAudit(
                command.reviewCaseId(),
                command.reviewItemId(),
                "media_access_" + decision,
                auditNote,
                command.operatorUserId(),
                happenedAt,
                metadata
        );
        return new ReviewMediaAccessAuditEntry(
                command.reviewCaseId(),
                command.reviewItemId(),
                command.operatorUserId(),
                command.cameraId(),
                command.materialUri(),
                actionType,
                decision,
                List.copyOf(deniedReasons),
                happenedAt,
                immutableNonNullMap(metadata)
        );
    }

    @Override
    public ReviewPlaybackAccess prepareReviewPlayback(ReviewPlaybackCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        String materialUri = firstText(command.materialUri(), reviewItemStore.listTimeline(command.reviewItemId()).stream()
                .filter(evidence -> MATERIAL_RECORD.equals(evidence.materialType()))
                .map(ReviewEvidenceItem::materialUri)
                .filter(SupervisionAlertReviewServiceImpl::hasText)
                .findFirst()
                .orElse(null));
        ReviewMediaAccessAuditEntry audit = auditMediaAccess(new ReviewMediaAccessCommand(
                command.reviewCaseId(),
                command.reviewItemId(),
                command.operatorUserId(),
                item.cameraId(),
                materialUri,
                "playback",
                command.allowedCameraIds(),
                command.reason()
        ));
        String playbackUrl = "granted".equals(audit.decision()) && hasText(materialUri)
                ? reviewPlaybackUrlSigner.signPlaybackUrl(materialUri, item.cameraId())
                : null;
        return new ReviewPlaybackAccess(
                command.reviewCaseId(),
                command.reviewItemId(),
                command.operatorUserId(),
                item.cameraId(),
                materialUri,
                playbackUrl,
                audit.decision(),
                audit.deniedReasons(),
                audit
        );
    }

    private ReviewEvidenceExportPackage buildReviewEvidenceExportPackage(ReviewEvidenceExportCommand command,
                                                                          boolean requestVideoExport) {
        return buildReviewEvidenceExportPackage(command, requestVideoExport, null);
    }

    private ReviewEvidenceExportPackage buildReviewEvidenceExportPackage(ReviewEvidenceExportCommand command,
                                                                          boolean requestVideoExport,
                                                                          LocalDateTime requestedExpiresAt) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewCaseId(), "reviewCaseId");
        Map<Long, List<RecordCoverageSegment>> coverageByReviewItemId = new LinkedHashMap<>();
        List<ReviewCaseTimelineItem> timeline = getReviewCaseTimeline(
                command.reviewCaseId(),
                coverageByReviewItemId,
                requestVideoExport
        );
        List<Long> caseReviewItemIds = reviewItemIdsFromTimeline(timeline);
        List<Long> reviewItemIds = command.reviewItemIds() == null || command.reviewItemIds().isEmpty()
                ? caseReviewItemIds
                : List.copyOf(new LinkedHashSet<>(command.reviewItemIds()));
        List<Long> caseExternalReviewItemIds = reviewItemIds.stream()
                .filter(id -> id == null || !caseReviewItemIds.contains(id))
                .toList();
        if (!caseExternalReviewItemIds.isEmpty()) {
            throw new IllegalArgumentException("review_item_not_in_case: " + caseExternalReviewItemIds);
        }
        Long approverUserId = authenticatedApproverUserId(command);
        List<ReviewCaseTimelineItem> scopedTimeline = timeline.stream()
                .filter(item -> item.reviewItemId() == null || reviewItemIds.contains(item.reviewItemId()))
                .toList();
        List<ReviewItemAggregate> reviewItems = reviewItemIds.stream()
                .map(id -> withEventProjection(reviewItemStore.findById(id).orElse(null)))
                .filter(Objects::nonNull)
                .toList();
        String format = normalizeEvidenceExportFormat(command.format());
        if (!EVIDENCE_EXPORT_FORMATS.contains(format)) {
            throw new IllegalArgumentException("unsupported_export_format: " + format);
        }
        enforceMediaAccessScope(
                command.reviewCaseId(),
                scopedTimeline,
                reviewItems,
                command.operatorUserId(),
                "export",
                command.allowedCameraIds(),
                command.reason()
        );
        LocalDateTime generatedAt = LocalDateTime.now();
        LocalDateTime expiresAt = requestedExpiresAt == null
                ? generatedAt.plusDays(DEFAULT_EXPORT_JOB_EXPIRES_DAYS)
                : requestedExpiresAt;
        List<ReviewEvidenceVideoExportResult> videoExports = requestVideoExport
                ? requestVideoExports(command.reviewCaseId(), reviewItemIds, scopedTimeline, format, expiresAt,
                coverageByReviewItemId)
                : List.of();
        List<String> evidenceUris = scopedTimeline.stream()
                .filter(item -> MATERIAL_SNAPSHOT.equals(item.materialType()) || MATERIAL_RECORD.equals(item.materialType()))
                .map(ReviewCaseTimelineItem::materialUri)
                .filter(SupervisionAlertReviewServiceImpl::hasText)
                .distinct()
                .toList();
        evidenceUris = mergeEvidenceUris(evidenceUris, videoExports);
        String requestKey = evidenceExportRequestKey(command, reviewItemIds, format, approverUserId);
        Map<String, Object> manifest = buildEvidenceManifest(
                command.reviewCaseId(),
                reviewItemIds,
                evidenceUris,
                scopedTimeline,
                reviewItems,
                videoExports,
                format,
                command.operatorUserId(),
                approverUserId,
                command.approvalNote(),
                requestKey,
                expiresAt,
                generatedAt
        );
        return new ReviewEvidenceExportPackage(
                "REP-" + UUID.randomUUID(),
                format,
                command.reviewCaseId(),
                reviewItemIds,
                evidenceUris,
                scopedTimeline,
                manifest,
                generatedAt
        );
    }

    private static String evidenceExportRequestKey(ReviewEvidenceExportCommand command,
                                                    List<Long> reviewItemIds,
                                                    String format,
                                                    Long approverUserId) {
        List<Long> normalizedReviewItemIds = reviewItemIds == null
                ? List.of()
                : reviewItemIds.stream().filter(Objects::nonNull).sorted().toList();
        return sha256Token(
                "review-evidence-export-request-v1",
                command.reviewCaseId(),
                normalizedReviewItemIds,
                command.operatorUserId(),
                format == null ? null : format.trim().toLowerCase(Locale.ROOT),
                command.reason(),
                approverUserId,
                command.approvalNote()
        );
    }

    private static Long authenticatedApproverUserId(ReviewEvidenceExportCommand command) {
        boolean approvalRequested = command.approverUserId() != null || hasText(command.approvalNote());
        if (!approvalRequested) {
            return null;
        }
        if (command.operatorUserId() == null || command.operatorUserId() <= 0) {
            throw new SecurityException("authenticated approver is required");
        }
        return command.operatorUserId();
    }

    private void enforceItemMediaReadScope(Long reviewCaseId,
                                           Long reviewItemId,
                                           Long operatorUserId,
                                           String actionType,
                                           List<String> allowedCameraIds,
                                           String reason,
                                           List<MediaAccessRef> mediaRefs) {
        List<String> effectiveAllowedCameraIds = resolveEffectiveAllowedCameraIds(
                reviewCaseId,
                operatorUserId,
                actionType,
                allowedCameraIds
        );
        if (effectiveAllowedCameraIds == null) {
            return;
        }
        requirePositive(reviewItemId, "reviewItemId");
        ReviewItemAggregate item = reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        Set<String> auditedKeys = new LinkedHashSet<>();
        boolean audited = false;
        for (MediaAccessRef ref : mediaRefs == null ? List.<MediaAccessRef>of() : mediaRefs) {
            if (ref == null || !isEvidenceMedia(ref.materialType()) || !hasText(ref.materialUri())) {
                continue;
            }
            String key = ref.materialType() + "\n" + ref.materialUri();
            if (!auditedKeys.add(key)) {
                continue;
            }
            auditAndEnforceMediaAccess(reviewCaseId, item, operatorUserId, actionType, effectiveAllowedCameraIds, reason,
                    ref.materialUri());
            audited = true;
        }
        if (!audited) {
            auditAndEnforceMediaAccess(reviewCaseId, item, operatorUserId, actionType, effectiveAllowedCameraIds, reason, null);
        }
    }

    private void enforceItemEvidenceMediaReadScope(Long reviewCaseId,
                                                   Long reviewItemId,
                                                   Long operatorUserId,
                                                   List<String> allowedCameraIds,
                                                   String reason,
                                                   List<MediaAccessRef> mediaRefs) {
        List<MediaAccessRef> recordRefs = mediaRefsByType(mediaRefs, MATERIAL_RECORD);
        if (!recordRefs.isEmpty()) {
            enforceItemMediaReadScope(reviewCaseId, reviewItemId, operatorUserId, "playback",
                    allowedCameraIds, reason, recordRefs);
        }
        List<MediaAccessRef> snapshotRefs = mediaRefsByType(mediaRefs, MATERIAL_SNAPSHOT);
        if (!snapshotRefs.isEmpty()) {
            enforceItemMediaReadScope(reviewCaseId, reviewItemId, operatorUserId, "snapshot",
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

    private void auditAndEnforceMediaAccess(Long reviewCaseId,
                                            ReviewItemAggregate item,
                                            Long operatorUserId,
                                            String actionType,
                                            List<String> allowedCameraIds,
                                            String reason,
                                            String materialUri) {
        ReviewMediaAccessAuditEntry audit = auditMediaAccess(new ReviewMediaAccessCommand(
                reviewCaseId,
                item.id(),
                operatorUserId,
                item.cameraId(),
                materialUri,
                actionType,
                allowedCameraIds,
                reason
        ));
        if ("denied".equals(audit.decision())) {
            throw new SecurityException("media access denied: " + String.join(",", audit.deniedReasons()));
        }
    }

    private record MediaAccessRef(String materialType, String materialUri) {
    }

    private void enforceMediaAccessScope(Long reviewCaseId,
                                         List<ReviewCaseTimelineItem> timeline,
                                         List<ReviewItemAggregate> reviewItems,
                                         Long operatorUserId,
                                         String actionType,
                                         List<String> allowedCameraIds,
                                         String reason) {
        List<String> effectiveAllowedCameraIds = resolveEffectiveAllowedCameraIds(
                reviewCaseId,
                operatorUserId,
                actionType,
                allowedCameraIds
        );
        if (effectiveAllowedCameraIds == null) {
            return;
        }
        Map<Long, ReviewItemAggregate> itemById = reviewItems == null
                ? Map.of()
                : reviewItems.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(
                                ReviewItemAggregate::id,
                                item -> item,
                                (left, right) -> left,
                                LinkedHashMap::new
                        ));
        for (ReviewCaseTimelineItem item : timeline == null ? List.<ReviewCaseTimelineItem>of() : timeline) {
            if (!isEvidenceMedia(item.materialType())) {
                continue;
            }
            if (item.reviewItemId() == null) {
                auditAndEnforceCaseLevelMediaAccess(
                        reviewCaseId,
                        item,
                        operatorUserId,
                        actionType,
                        effectiveAllowedCameraIds,
                        reason
                );
                continue;
            }
            ReviewItemAggregate reviewItem = itemById.get(item.reviewItemId());
            if (reviewItem == null) {
                throw new IllegalArgumentException("reviewItemId not found: " + item.reviewItemId());
            }
            auditAndEnforceMediaAccess(
                    reviewCaseId,
                    reviewItem,
                    operatorUserId,
                    actionType,
                    effectiveAllowedCameraIds,
                    reason,
                    item.materialUri()
            );
        }
    }

    private void enforceCaseEvidenceMediaReadScope(Long reviewCaseId,
                                                   List<ReviewCaseTimelineItem> timeline,
                                                   List<ReviewItemAggregate> reviewItems,
                                                   Long operatorUserId,
                                                   List<String> allowedCameraIds,
                                                   String reason) {
        List<ReviewCaseTimelineItem> recordItems = caseTimelineItemsByType(timeline, MATERIAL_RECORD);
        if (!recordItems.isEmpty()) {
            enforceMediaAccessScope(reviewCaseId, recordItems, reviewItems, operatorUserId, "playback",
                    allowedCameraIds, reason);
        }
        List<ReviewCaseTimelineItem> snapshotItems = caseTimelineItemsByType(timeline, MATERIAL_SNAPSHOT);
        if (!snapshotItems.isEmpty()) {
            enforceMediaAccessScope(reviewCaseId, snapshotItems, reviewItems, operatorUserId, "snapshot",
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

    private void auditAndEnforceCaseLevelMediaAccess(Long reviewCaseId,
                                                     ReviewCaseTimelineItem item,
                                                     Long operatorUserId,
                                                     String actionType,
                                                     List<String> allowedCameraIds,
                                                     String reason) {
        List<String> deniedReasons = new ArrayList<>();
        if (!hasText(item.cameraId())) {
            deniedReasons.add("camera_unresolved");
        } else if (!allowedCameraIds.contains(item.cameraId())) {
            deniedReasons.add("camera_not_allowed");
        }
        String decision = deniedReasons.isEmpty() ? "granted" : "denied";
        LocalDateTime happenedAt = LocalDateTime.now();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("decision", decision);
        metadata.put("cameraId", item.cameraId());
        metadata.put("materialUri", item.materialUri());
        metadata.put("allowedCameraIds", List.copyOf(allowedCameraIds));
        metadata.put("deniedReasons", List.copyOf(deniedReasons));
        metadata.put("reason", reason);
        String auditNote = "decision=" + decision
                + "; action=" + actionType
                + "; cameraId=" + item.cameraId()
                + "; materialUri=" + item.materialUri()
                + (operatorUserId == null ? "" : "; operatorUserId=" + operatorUserId)
                + (deniedReasons.isEmpty() ? "" : "; deniedReasons=" + String.join(",", deniedReasons))
                + (hasText(reason) ? "; reason=" + reason : "");
        reviewItemStore.recordMediaAccessAudit(
                reviewCaseId,
                null,
                "media_access_" + decision,
                auditNote,
                operatorUserId,
                happenedAt,
                immutableNonNullMap(metadata)
        );
        if (!deniedReasons.isEmpty()) {
            throw new SecurityException("media access denied: " + String.join(",", deniedReasons));
        }
    }

    private List<String> resolveEffectiveAllowedCameraIds(Long reviewCaseId,
                                                          Long operatorUserId,
                                                          String actionType,
                                                          List<String> requestedCameraIds) {
        List<String> normalizedRequested = normalizeCameraScope(requestedCameraIds);
        List<String> serviceAllowed = cameraPermissionResolver.resolveAllowedCameraIds(new ReviewCameraPermissionRequest(
                reviewCaseId,
                operatorUserId,
                TenantContextHolder.getTenantId(),
                actionType,
                normalizedRequested
        ));
        List<String> normalizedServiceAllowed = normalizeCameraScope(serviceAllowed);
        if (normalizedServiceAllowed == null) {
            return normalizedRequested;
        }
        if (normalizedRequested == null) {
            return normalizedServiceAllowed;
        }
        Set<String> requestedSet = new LinkedHashSet<>(normalizedRequested);
        return normalizedServiceAllowed.stream()
                .filter(requestedSet::contains)
                .toList();
    }

    private static List<String> normalizeCameraScope(List<String> cameraIds) {
        if (cameraIds == null) {
            return null;
        }
        return cameraIds.stream()
                .filter(SupervisionAlertReviewServiceImpl::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<ReviewItemAggregate> loadReviewItems(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        return reviewItemIds.stream()
                .map(id -> withEventProjection(reviewItemStore.findById(id).orElse(null)))
                .filter(Objects::nonNull)
                .toList();
    }

    private static boolean isEvidenceMedia(String materialType) {
        return MATERIAL_SNAPSHOT.equals(materialType) || MATERIAL_RECORD.equals(materialType);
    }

    @Override
    public ReviewToEventResult convertToEvent(ReviewToEventCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = withEventProjection(reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId())));
        if (item.eventId() != null) {
            return new ReviewToEventResult(item.id(), item.reviewStatus(), item.eventId(), true);
        }

        AlertToEventResult eventResult = supervisionEventService.createFromAlert(new AlertToEventCommand(
                REVIEW_SOURCE_SYSTEM,
                item.reviewItemNo(),
                item.ruleCode(),
                item.sourceAlertType(),
                item.firstAlertTime(),
                null,
                command.dispatchTasks()
        ));
        LocalDateTime convertedAt = LocalDateTime.now();
        markUserReviewed(item.id(), command.reviewerUserId(), convertedAt);
        ReviewItemAggregate converted = withEventProjection(reviewItemStore.markConverted(
                item.id(),
                command.reviewerUserId(),
                eventResult.eventId(),
                convertedAt
        ));
        return new ReviewToEventResult(converted.id(), converted.reviewStatus(), eventResult.eventId(), eventResult.reused());
    }

    @Override
    public ReviewRuleReplayResult replayRule(ReviewRuleReplayCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.ruleCode(), "ruleCode");
        ReviewQuery query = new ReviewQuery(
                null,
                command.cameraId(),
                command.zoneCode(),
                command.objectLabel(),
                null,
                null,
                null,
                null,
                command.beginTime(),
                command.endTime()
        );
        List<ReviewItemAggregate> evaluatedItems = listWorkbench(query).stream()
                .filter(item -> Objects.equals(command.ruleCode(), item.ruleCode()))
                .filter(item -> matchesText(command.sourceSystem(), item.sourceSystem()))
                .toList();
        Optional<ReviewRuleView> currentRule = reviewRuleStore.listAll().stream()
                .filter(rule -> Objects.equals(command.ruleCode(), rule.ruleCode()))
                .filter(rule -> matchesText(rule.sourceSystem(), command.sourceSystem()))
                .filter(rule -> matchesText(rule.cameraId(), command.cameraId()))
                .filter(rule -> matchesText(rule.zoneCode(), command.zoneCode()))
                .filter(rule -> matchesText(rule.objectLabel(), command.objectLabel()))
                .findFirst();
        Map<String, Object> ruleVersion = currentRule
                .map(SupervisionAlertReviewServiceImpl::ruleVersionFromRule)
                .orElseGet(() -> ruleVersionFromReplayCommand(command));
        int matchBeforeCount = currentRule
                .map(rule -> (int) evaluatedItems.stream().filter(item -> matchesReplayRule(item, rule)).count())
                .orElse(0);
        int matchAfterCount = (int) evaluatedItems.stream()
                .filter(item -> matchesReplayRule(item, command))
                .count();
        int falsePositiveBeforeCount = (int) evaluatedItems.stream()
                .filter(item -> STATUS_FALSE_POSITIVE.equals(item.reviewStatus()))
                .count();
        int matchedFalsePositiveAfterCount = (int) evaluatedItems.stream()
                .filter(item -> STATUS_FALSE_POSITIVE.equals(item.reviewStatus()))
                .filter(item -> matchesReplayRule(item, command))
                .count();
        List<String> actions = new ArrayList<>();
        double beforeRate = roundRate(falsePositiveBeforeCount, evaluatedItems.size());
        double afterRate = roundRate(matchedFalsePositiveAfterCount, matchAfterCount);
        if (afterRate <= beforeRate) {
            actions.add("safe_to_apply");
        } else {
            actions.add("manual_review_required");
        }
        if (matchAfterCount == 0) {
            actions.add("check_recall_risk");
        }
        Map<String, Object> report = buildReplayReport(
                evaluatedItems,
                matchBeforeCount,
                matchAfterCount,
                falsePositiveBeforeCount,
                matchedFalsePositiveAfterCount,
                actions,
                ruleVersion
        );
        return new ReviewRuleReplayResult(
                command.ruleCode(),
                evaluatedItems.stream().map(ReviewItemAggregate::id).toList(),
                evaluatedItems.size(),
                matchBeforeCount,
                matchAfterCount,
                falsePositiveBeforeCount,
                beforeRate,
                afterRate,
                List.copyOf(actions),
                replayScope(command),
                report,
                LocalDateTime.now()
        );
    }

    @Override
    public ReviewRuleGeometryEvaluation evaluateRuleGeometry(ReviewRuleGeometryCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.ruleCode(), "ruleCode");
        List<Double> evaluatedPoint = bottomCenterPoint(command.bbox());
        boolean inside = !evaluatedPoint.isEmpty() && pointInPolygon(evaluatedPoint, command.polygon());
        ReviewQuery query = command.query() == null
                ? new ReviewQuery(null, command.cameraId(), command.zoneCode(), command.objectLabel(),
                        null, null, null, null, null, null)
                : command.query();
        List<ReviewItemAggregate> replayedItems = listWorkbench(query).stream()
                .filter(item -> Objects.equals(command.ruleCode(), item.ruleCode()))
                .filter(item -> matchesText(command.cameraId(), item.cameraId()))
                .filter(item -> matchesText(command.zoneCode(), item.zoneCode()))
                .filter(item -> matchesText(command.objectLabel(), item.objectLabel()))
                .toList();
        List<Long> replayedReviewItemIds = replayedItems.stream()
                .map(ReviewItemAggregate::id)
                .toList();
        Integer minStaySeconds = replayedItems.stream()
                .map(SupervisionAlertReviewServiceImpl::inferredStaySeconds)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Optional<ReviewRuleView> matchedRule = matchingRuleForGeometry(command);
        Integer zoneInertiaFrames = matchedRule
                .map(ReviewRuleView::inertiaFrames)
                .map(SupervisionAlertReviewServiceImpl::normalizeZoneInertiaFrames)
                .orElse(1);
        Integer loiteringSeconds = matchedRule
                .map(ReviewRuleView::loiteringSeconds)
                .filter(value -> value != null && value > 0)
                .orElseGet(() -> matchedRule
                        .map(ReviewRuleView::minStaySeconds)
                        .filter(value -> value != null && value > 0)
                        .orElse(minStaySeconds));
        Integer observedConsecutiveFrames = replayedItems.stream()
                .map(SupervisionAlertReviewServiceImpl::inferredConsecutiveZoneFrames)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        Integer observedStaySeconds = replayedItems.stream()
                .map(SupervisionAlertReviewServiceImpl::inferredStaySeconds)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        boolean inertiaSatisfied = observedConsecutiveFrames == null
                ? zoneInertiaFrames <= 1
                : observedConsecutiveFrames >= zoneInertiaFrames;
        boolean loiteringSatisfied = loiteringSeconds == null || loiteringSeconds <= 0
                || (observedStaySeconds != null && observedStaySeconds >= loiteringSeconds);
        Map<String, Object> ruleVersion = new LinkedHashMap<>();
        ruleVersion.put("ruleCode", command.ruleCode());
        ruleVersion.put("cameraId", command.cameraId());
        ruleVersion.put("zoneCode", command.zoneCode());
        ruleVersion.put("objectLabel", command.objectLabel());
        ruleVersion.put("minStaySeconds", minStaySeconds);
        ruleVersion.put("zoneInertiaFrames", zoneInertiaFrames);
        ruleVersion.put("loiteringSeconds", loiteringSeconds);
        ruleVersion.put("geometryType", "bottom_center");
        ruleVersion.put("semanticEngine", "yfeieye-rule-geometry-v1");
        ruleVersion.put("pointStrategy", "bottom_center");
        ruleVersion.put("coordinateSpace", "image");
        ruleVersion.put("applicationMode", "shadow");
        ruleVersion.put("operatorUserId", command.operatorUserId());
        ruleVersion.put("evaluatedAt", LocalDateTime.now().toString());
        List<String> consistencyChecks = new ArrayList<>(List.of(
                "front_back_replay_use_bottom_center",
                "bbox_order_x1_y1_x2_y2"
        ));
        if (zoneInertiaFrames > 1) {
            consistencyChecks.add("zone_inertia_applied");
        }
        if (loiteringSeconds != null && loiteringSeconds > 0) {
            consistencyChecks.add("loitering_threshold_applied");
        }
        return new ReviewRuleGeometryEvaluation(
                "bottom_center",
                inside,
                evaluatedPoint,
                command.zoneCode(),
                replayedReviewItemIds,
                immutableNonNullMap(ruleVersion),
                List.copyOf(consistencyChecks),
                LocalDateTime.now(),
                List.of(ruleMatchTrace(command, evaluatedPoint, inside, minStaySeconds, zoneInertiaFrames,
                        observedConsecutiveFrames, inertiaSatisfied, loiteringSeconds, observedStaySeconds,
                        loiteringSatisfied))
        );
    }

    @Override
    public ReviewRuleView saveRule(ReviewRuleCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.ruleCode(), "ruleCode");
        return reviewRuleStore.save(command);
    }

    @Override
    public List<ReviewRuleView> listRules() {
        return reviewRuleStore.listAll();
    }

    private static ReviewEvidenceAuditEntry exportJobAuditEntry(ReviewEvidenceExportJob job) {
        ReviewEvidenceExportPackage exportPackage = job.exportPackage();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("packageNo", exportPackage.packageNo());
        metadata.put("format", exportPackage.format());
        metadata.put("status", job.status());
        metadata.put("expiresAt", job.expiresAt() == null ? null : job.expiresAt().toString());
        metadata.put("reason", job.reason());
        putAuditReverseLookupMetadata(
                metadata,
                exportPackage.reviewCaseId(),
                exportPackage.reviewItemIds(),
                job.boundEventIds(),
                job.jobNo()
        );
        return new ReviewEvidenceAuditEntry(
                exportPackage.reviewCaseId(),
                null,
                "export_created",
                job.jobNo(),
                job.fileHash(),
                job.operatorUserId(),
                job.reason(),
                exportPackage.evidenceUris(),
                job.boundEventIds(),
                job.createdAt(),
                immutableNonNullMap(metadata)
        );
    }

    private static List<ReviewEvidenceAuditEntry> withAuditHashChain(List<ReviewEvidenceAuditEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<ReviewEvidenceAuditEntry> chained = new ArrayList<>();
        String previousHash = "GENESIS";
        for (ReviewEvidenceAuditEntry entry : entries) {
            Map<String, Object> metadata = new LinkedHashMap<>(entry.metadata() == null ? Map.of() : entry.metadata());
            metadata.put("previousHash", previousHash);
            String entryHash = sha256Token(
                    entry.reviewCaseId(),
                    entry.reviewItemId(),
                    entry.actionType(),
                    entry.jobNo(),
                    entry.fileHash(),
                    entry.operatorUserId(),
                    entry.actionNote(),
                    entry.evidenceUris(),
                    entry.boundEventIds(),
                    entry.happenedAt(),
                    previousHash
            );
            metadata.put("entryHash", entryHash);
            previousHash = entryHash;
            chained.add(new ReviewEvidenceAuditEntry(
                    entry.reviewCaseId(),
                    entry.reviewItemId(),
                    entry.actionType(),
                    entry.jobNo(),
                    entry.fileHash(),
                    entry.operatorUserId(),
                    entry.actionNote(),
                    entry.evidenceUris(),
                    entry.boundEventIds(),
                    entry.happenedAt(),
                    immutableNonNullMap(metadata)
            ));
        }
        return List.copyOf(chained);
    }

    private static ReviewEvidenceAuditEntry enrichDownloadAuditEntry(ReviewEvidenceAuditEntry entry,
                                                                     ReviewEvidenceExportJob job) {
        if (entry == null || job == null) {
            return entry;
        }
        ReviewEvidenceExportPackage exportPackage = job.exportPackage();
        Map<String, Object> metadata = new LinkedHashMap<>(entry.metadata() == null ? Map.of() : entry.metadata());
        putAuditReverseLookupMetadata(
                metadata,
                exportPackage.reviewCaseId(),
                exportPackage.reviewItemIds(),
                job.boundEventIds(),
                job.jobNo()
        );
        return new ReviewEvidenceAuditEntry(
                entry.reviewCaseId() == null ? exportPackage.reviewCaseId() : entry.reviewCaseId(),
                entry.reviewItemId(),
                entry.actionType(),
                firstText(entry.jobNo(), job.jobNo()),
                firstText(entry.fileHash(), job.fileHash()),
                entry.operatorUserId(),
                entry.actionNote(),
                entry.evidenceUris() == null || entry.evidenceUris().isEmpty()
                        ? exportPackage.evidenceUris()
                        : entry.evidenceUris(),
                entry.boundEventIds() == null || entry.boundEventIds().isEmpty()
                        ? job.boundEventIds()
                        : entry.boundEventIds(),
                entry.happenedAt(),
                immutableNonNullMap(metadata)
        );
    }

    private ReviewEvidenceAuditEntry downloadAuditEntry(Long reviewCaseId, ReviewCaseTimelineItem timelineItem) {
        Map<String, Object> note = parseAuditNote(timelineItem.actionNote());
        String jobNo = toText(note.get("jobNo"));
        Optional<ReviewEvidenceExportJob> exportJob = hasText(jobNo)
                ? reviewItemStore.findExportJobByNo(jobNo)
                : Optional.empty();
        Map<String, Object> metadata = new LinkedHashMap<>(note);
        exportJob.ifPresent(job -> putAuditReverseLookupMetadata(
                metadata,
                job.exportPackage().reviewCaseId(),
                job.exportPackage().reviewItemIds(),
                job.boundEventIds(),
                job.jobNo()
        ));
        return new ReviewEvidenceAuditEntry(
                exportJob.map(job -> job.exportPackage().reviewCaseId()).orElse(reviewCaseId),
                timelineItem.reviewItemId(),
                "export_downloaded",
                jobNo,
                firstText(note.get("fileHash"), exportJob.map(ReviewEvidenceExportJob::fileHash).orElse(null)),
                toLong(note.get("operatorUserId")),
                toText(note.get("reason")),
                exportJob
                        .map(job -> job.exportPackage().evidenceUris())
                        .orElse(List.of()),
                exportJob
                        .map(ReviewEvidenceExportJob::boundEventIds)
                        .orElse(List.of()),
                timelineItem.happenedAt(),
                immutableNonNullMap(metadata)
        );
    }

    private ReviewEvidenceAuditEntry mediaAccessAuditEntry(Long reviewCaseId, ReviewCaseTimelineItem timelineItem) {
        Map<String, Object> note = parseAuditNote(timelineItem.actionNote());
        Optional<ReviewItemAggregate> reviewItem = timelineItem.reviewItemId() == null
                ? Optional.empty()
                : reviewItemStore.findById(timelineItem.reviewItemId());
        List<Long> reviewItemIds = timelineItem.reviewItemId() == null
                ? List.of()
                : List.of(timelineItem.reviewItemId());
        List<Long> eventIds = reviewItem
                .map(ReviewItemAggregate::eventId)
                .map(List::of)
                .orElse(List.of());
        String materialUri = toText(note.get("materialUri"));
        Map<String, Object> metadata = new LinkedHashMap<>(note);
        metadata.put("decision", firstText(note.get("decision"), mediaAccessDecision(timelineItem.materialUri())));
        metadata.put("mediaAction", note.get("action"));
        metadata.put("deniedReasons", splitAuditCsv(note.get("deniedReasons")));
        putAuditReverseLookupMetadata(metadata, reviewCaseId, reviewItemIds, eventIds, null);
        return new ReviewEvidenceAuditEntry(
                reviewCaseId,
                timelineItem.reviewItemId(),
                timelineItem.materialUri(),
                null,
                null,
                toLong(note.get("operatorUserId")),
                toText(note.get("reason")),
                hasText(materialUri) ? List.of(materialUri) : List.of(),
                eventIds,
                timelineItem.happenedAt(),
                immutableNonNullMap(metadata)
        );
    }

    private static boolean isMediaAccessAuditAction(String actionType) {
        return "media_access_granted".equals(actionType) || "media_access_denied".equals(actionType);
    }

    private static String mediaAccessDecision(String actionType) {
        if ("media_access_granted".equals(actionType)) {
            return "granted";
        }
        if ("media_access_denied".equals(actionType)) {
            return "denied";
        }
        return null;
    }

    private static List<String> splitAuditCsv(Object value) {
        String text = toText(value);
        if (!hasText(text)) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : text.split(",")) {
            if (hasText(part)) {
                values.add(part.trim());
            }
        }
        return List.copyOf(values);
    }

    private static void putAuditReverseLookupMetadata(Map<String, Object> metadata,
                                                      Long reviewCaseId,
                                                      List<Long> reviewItemIds,
                                                      List<Long> eventIds,
                                                      String exportJobNo) {
        metadata.put("reviewCaseId", reviewCaseId);
        metadata.put("reviewItemIds", reviewItemIds == null ? List.of() : List.copyOf(reviewItemIds));
        metadata.put("eventIds", eventIds == null ? List.of() : List.copyOf(eventIds));
        metadata.put("exportJobNo", exportJobNo);
    }

    private static Map<String, Object> parseAuditNote(String note) {
        if (!hasText(note)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String part : note.split(";")) {
            String[] pair = part.trim().split("=", 2);
            if (pair.length == 2 && hasText(pair[0])) {
                values.put(pair[0].trim(), pair[1].trim());
            }
        }
        return immutableNonNullMap(values);
    }

    private static int normalizePatrolAttempts(Integer maxAttempts) {
        if (maxAttempts == null || maxAttempts <= 0) {
            return 1;
        }
        return Math.min(maxAttempts, 5);
    }

    private static int normalizeRuntimeOutboxLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_RUNTIME_OUTBOX_LIMIT;
        }
        return Math.min(limit, MAX_RUNTIME_OUTBOX_LIMIT);
    }

    private static List<String> runtimePatrolRecommendedActions(List<String> alerts,
                                                                ReviewRuntimeHealthReport healthReport) {
        List<String> actions = new ArrayList<>();
        for (String alert : alerts == null ? List.<String>of() : alerts) {
            String action = runtimePatrolActionForAlert(alert);
            if (hasText(action)) {
                actions.add(action);
            }
        }
        if (healthReport != null && healthReport.repairableCount() != null && healthReport.repairableCount() > 0) {
            actions.add("rerun_runtime_reconciliation");
        }
        return actions;
    }

    private static Map<String, Object> runtimePatrolAlertActions(List<String> alerts) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String alert : alerts == null ? List.<String>of() : alerts) {
            String action = runtimePatrolActionForAlert(alert);
            if (hasText(action)) {
                values.put(alert, action);
            }
        }
        return values;
    }

    private static String runtimePatrolActionForAlert(String alert) {
        if ("record_evidence_gap".equals(alert)) {
            return "backfill_record_evidence";
        }
        if (("record_evidence_gap:" + RECORD_GAP_VIDEO_URL_NOT_CONFIGURED).equals(alert)) {
            return "configure_video_record_query_url";
        }
        if ("record_storage_drift".equals(alert)) {
            return "inspect_record_storage_drift";
        }
        if (("record_storage_drift:" + RECORD_GAP_FILE_MISSING).equals(alert)) {
            return "review_missing_record_file";
        }
        if (("record_storage_drift:" + RECORD_GAP_RETENTION_EXPIRED).equals(alert)) {
            return "verify_record_retention_cleanup";
        }
        if (("record_storage_drift:" + RECORD_GAP_DISK_FULL).equals(alert)) {
            return "free_or_expand_recording_disk";
        }
        if (("record_storage_drift:" + RECORD_GAP_CACHE_FLUSH_FAILED).equals(alert)) {
            return "inspect_record_cache_flush";
        }
        if ("semantic_index_backlog".equals(alert)) {
            return "process_pending_semantic_index";
        }
        if ("evidence_export_failed".equals(alert)) {
            return "retry_evidence_export";
        }
        return null;
    }

    private boolean hasRecordEvidenceGap(ReviewItemAggregate item) {
        return item != null
                && (RECORD_EVIDENCE_MISSING.equals(item.recordEvidenceStatus())
                || RECORD_EVIDENCE_FAILED.equals(item.recordEvidenceStatus()));
    }

    private List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> inspectRecordStorageDrift(
            List<ReviewItemAggregate> items) {
        Map<String, ReviewItemAggregate> devices = new LinkedHashMap<>();
        for (ReviewItemAggregate item : items == null ? List.<ReviewItemAggregate>of() : items) {
            if (item == null) {
                continue;
            }
            String deviceId = hasText(item.deviceId()) ? item.deviceId() : item.cameraId();
            if (hasText(deviceId)) {
                devices.putIfAbsent(deviceId, item);
            }
        }
        List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> reports = new ArrayList<>();
        for (Map.Entry<String, ReviewItemAggregate> entry : devices.entrySet()) {
            ReviewItemAggregate item = entry.getValue();
            try {
                ReviewRecordStorageDriftResolver.RecordStorageDriftReport report =
                        recordStorageDriftResolver.inspect(
                                new ReviewRecordStorageDriftResolver.RecordStorageDriftRequest(
                                        entry.getKey(),
                                        item.cameraId(),
                                        Math.max(1, recordStorageDriftRetentionHours)
                                ));
                if (report != null) {
                    reports.add(report);
                }
            } catch (RuntimeException ignored) {
                reports.add(new ReviewRecordStorageDriftResolver.RecordStorageDriftReport(
                        entry.getKey(),
                        item.cameraId(),
                        null,
                        false,
                        0,
                        1,
                        Map.of(RECORD_GAP_PROBE_FAILED, 1),
                        List.of(),
                        RECORD_GAP_PROBE_FAILED,
                        LocalDateTime.now()
                ));
            }
        }
        return List.copyOf(reports);
    }

    private static Map<String, Integer> mergeRecordStorageDriftReasons(
            Map<String, Integer> itemReasons,
            List<ReviewRecordStorageDriftResolver.RecordStorageDriftReport> reports) {
        Map<String, Integer> merged = new LinkedHashMap<>(itemReasons == null ? Map.of() : itemReasons);
        for (ReviewRecordStorageDriftResolver.RecordStorageDriftReport report
                : reports == null ? List.<ReviewRecordStorageDriftResolver.RecordStorageDriftReport>of() : reports) {
            if (report == null || report.issueReasons() == null) {
                continue;
            }
            for (Map.Entry<String, Integer> entry : report.issueReasons().entrySet()) {
                String reason = normalizeRecordGapReason(entry.getKey());
                Integer count = entry.getValue();
                if (hasText(reason) && count != null && count > 0) {
                    merged.merge(reason, count, Integer::sum);
                }
            }
        }
        return Map.copyOf(merged);
    }

    private Map<String, Integer> recordGapReasons(List<ReviewItemAggregate> items) {
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (ReviewItemAggregate item : items == null ? List.<ReviewItemAggregate>of() : items) {
            if (!hasRecordEvidenceGap(item)) {
                continue;
            }
            for (String reason : recordGapReasons(item)) {
                reasons.merge(reason, 1, Integer::sum);
            }
        }
        return reasons;
    }

    private static Set<String> recordGapReasons(ReviewItemAggregate item) {
        Set<String> reasons = new LinkedHashSet<>();
        reasons.add(recordGapReason(item));
        reasons.addAll(recordStorageGapReasons(item));
        return reasons;
    }

    private static String recordGapReason(ReviewItemAggregate item) {
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

    private static String normalizeRecordGapReason(String value) {
        String normalized = normalizeRecordGapReasonToken(value);
        if (!hasText(normalized)) {
            return null;
        }
        return recordGapReasonDefinition(normalized)
                .map(RecordGapReasonDefinition::code)
                .orElse(RECORD_GAP_RECORD_NOT_FOUND);
    }

    private static Optional<RecordGapReasonDefinition> recordGapReasonDefinition(String value) {
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

    private static Map<String, Map<String, Object>> recordGapReasonCatalog() {
        Map<String, Map<String, Object>> catalog = new LinkedHashMap<>();
        for (RecordGapReasonDefinition definition : RECORD_GAP_REASON_CATALOG) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("code", definition.code());
            values.put("category", definition.category());
            values.put("labelZh", definition.labelZh());
            values.put("retryable", definition.retryable());
            values.put("aliases", List.copyOf(definition.aliases()));
            catalog.put(definition.code(), Map.copyOf(values));
        }
        return Map.copyOf(catalog);
    }

    private static Map<String, Map<String, Object>> recordGapReasonDetails(Map<String, Integer> reasonCounts) {
        if (reasonCounts == null || reasonCounts.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> details = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : reasonCounts.entrySet()) {
            recordGapReasonDefinition(entry.getKey()).ifPresent(definition -> {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("code", definition.code());
                values.put("category", definition.category());
                values.put("labelZh", definition.labelZh());
                values.put("retryable", definition.retryable());
                values.put("count", entry.getValue());
                details.put(definition.code(), Map.copyOf(values));
            });
        }
        return Map.copyOf(details);
    }

    private static Set<String> recordStorageGapReasons(ReviewItemAggregate item) {
        if (item == null || item.reviewData() == null) {
            return Set.of();
        }
        Map<String, Object> storage = toStringObjectMap(item.reviewData().get("recordStorage"));
        Map<String, Object> gapReasons = toStringObjectMap(storage.get("gapReasons"));
        if (gapReasons.isEmpty()) {
            return Set.of();
        }
        Set<String> reasons = new LinkedHashSet<>();
        for (String reason : gapReasons.keySet()) {
            String normalized = normalizeRecordGapReason(reason);
            if (hasText(normalized)) {
                reasons.add(normalized);
            }
        }
        return reasons;
    }

    private static boolean isRecordStorageDriftReason(String reason) {
        return switch (reason) {
            case RECORD_GAP_FILE_MISSING,
                 RECORD_GAP_RETENTION_EXPIRED,
                 RECORD_GAP_DISK_FULL,
                 RECORD_GAP_CACHE_FLUSH_FAILED -> true;
            default -> false;
        };
    }

    private ReviewDataConsistency reviewDataConsistency(ReviewItemAggregate item) {
        if (item == null) {
            return new ReviewDataConsistency(Map.of(), true, true);
        }
        Map<String, Object> current = item.reviewData() == null
                ? Map.of()
                : item.reviewData();
        Map<String, Object> normalized = new LinkedHashMap<>(current);
        AlertReviewDataSchemaValidator.ValidationResult schemaValidation =
                REVIEW_DATA_SCHEMA_VALIDATOR.validate(current);

        List<String> labels = toStringList(normalized.get("labels"), item.objectLabel());
        List<String> zones = toStringList(normalized.get("zones"), item.zoneCode());
        List<String> objectIds = toStringList(normalized.get("objectIds"), null);
        List<Double> bbox = toDoubleList(normalized.get("bbox"));
        Double confidence = toDouble(normalized.get("confidence"));
        if (!validReviewConfidence(confidence)) {
            confidence = null;
            normalized.remove("confidence");
        }
        if (!validReviewBbox(bbox)) {
            bbox = List.of();
            normalized.remove("bbox");
        }
        if (normalized.get("correlationId") != null && !(normalized.get("correlationId") instanceof String)) {
            normalized.remove("correlationId");
        }

        boolean schemaDrift = !schemaValidation.valid()
                || !Objects.equals(toInteger(normalized.get("reviewDataVersion")), REVIEW_DATA_VERSION)
                || !normalized.containsKey("labels")
                || !normalized.containsKey("zones")
                || !normalized.containsKey("objectIds")
                || toMapList(normalized.get("objects")).isEmpty()
                || toMapList(normalized.get("detections")).isEmpty();
        normalized.put("reviewDataVersion", REVIEW_DATA_VERSION);
        normalized.put("labels", labels);
        normalized.put("zones", zones);
        normalized.put("objectIds", objectIds);
        if (toMapList(normalized.get("objects")).isEmpty()
                || hasSchemaViolationPrefix(schemaValidation, "objects[")) {
            normalized.put("objects", buildReviewObjects(labels, objectIds, confidence, bbox));
        }
        if (toMapList(normalized.get("detections")).isEmpty()
                || hasSchemaViolationPrefix(schemaValidation, "detections[")) {
            normalized.put("detections", List.of(buildRuntimeRepairDetection(item, labels, zones, objectIds, confidence, bbox)));
        }

        Optional<ReviewSegmentView> persistedSegment = reviewItemStore.findPersistedReviewSegment(item.id());
        Map<String, Object> segment = normalizeReviewSegmentDoubleWrite(
                item,
                normalized.get("reviewSegment"),
                objectIds,
                zones,
                persistedSegment.map(ReviewSegmentView::segmentId).orElse(null)
        );
        boolean segmentDoubleWriteDrift = reviewSegmentDoubleWriteDrift(
                item,
                toStringObjectMap(normalized.get("reviewSegment")),
                segment
        ) || persistedReviewSegmentDrift(item, segment, persistedSegment);
        normalized.put("reviewSegment", segment);
        return new ReviewDataConsistency(Map.copyOf(normalized), schemaDrift, segmentDoubleWriteDrift);
    }

    private boolean persistedReviewSegmentDrift(ReviewItemAggregate item,
                                                Map<String, Object> expectedSegment,
                                                Optional<ReviewSegmentView> persisted) {
        if (persisted.isEmpty()) {
            return true;
        }
        ReviewSegmentView actual = persisted.get();
        return !Objects.equals(item.id(), actual.reviewItemId())
                || !Objects.equals(firstText(expectedSegment.get("segmentId"), null), actual.segmentId())
                || !Objects.equals(firstText(expectedSegment.get("cameraId"), null), actual.cameraId())
                || !Objects.equals(firstText(expectedSegment.get("severity"), null), actual.severity())
                || !Objects.equals(firstText(expectedSegment.get("status"), null), actual.status())
                || !Objects.equals(toLocalDateTime(expectedSegment.get("startTime")), actual.startTime())
                || !Objects.equals(toLocalDateTime(expectedSegment.get("endTime")), actual.endTime())
                || !Objects.equals(toStringList(expectedSegment.get("objectIds"), null), actual.objectIds())
                || !Objects.equals(toStringList(expectedSegment.get("zones"), null), actual.zones())
                || !Objects.equals(toStringList(expectedSegment.get("sourceAlertIds"), null), actual.sourceAlertIds())
                || !Objects.equals(toMapList(expectedSegment.get("events")), actual.events())
                || !Objects.equals(expectedSegment, actual.metadata());
    }

    private static boolean hasSchemaViolationPrefix(AlertReviewDataSchemaValidator.ValidationResult validation,
                                                    String prefix) {
        return validation.violations().stream().anyMatch(violation -> violation.startsWith(prefix));
    }

    private static boolean validReviewConfidence(Double confidence) {
        return confidence == null || (Double.isFinite(confidence) && confidence >= 0D && confidence <= 1D);
    }

    private static boolean validReviewBbox(List<Double> bbox) {
        return bbox == null || bbox.isEmpty()
                || (bbox.size() == 4 && bbox.stream().allMatch(value -> value != null && Double.isFinite(value)));
    }

    private static Map<String, Object> buildRuntimeRepairDetection(ReviewItemAggregate item,
                                                                   List<String> labels,
                                                                   List<String> zones,
                                                                   List<String> objectIds,
                                                                   Double confidence,
                                                                   List<Double> bbox) {
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("sourceAlertId", firstSourceAlertId(item));
        detection.put("alertTime", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
        detection.put("cameraId", item.cameraId());
        detection.put("zoneCode", item.zoneCode());
        detection.put("ruleCode", item.ruleCode());
        detection.put("objectLabel", item.objectLabel());
        detection.put("labels", labels);
        detection.put("zones", zones);
        detection.put("objectIds", objectIds);
        detection.put("confidence", confidence);
        detection.put("bbox", bbox);
        detection.put("source", "runtime_repair");
        return immutableNonNullMap(detection);
    }

    private static Map<String, Object> normalizeReviewSegmentDoubleWrite(ReviewItemAggregate item,
                                                                         Object currentSegment,
                                                                         List<String> objectIds,
                                                                         List<String> zones,
                                                                         String persistedSegmentId) {
        Map<String, Object> segment = new LinkedHashMap<>(toStringObjectMap(currentSegment));
        segment.put("segmentId", firstText(
                segment.get("segmentId"),
                firstText(persistedSegmentId, buildRecoveryReviewSegmentId(item))
        ));
        segment.put("cameraId", item.cameraId());
        String severity = firstText(segment.get("severity"), reviewSegmentSeverity(item.sourceAlertType()));
        segment.put("severity", Set.of("detection", "alert").contains(severity)
                ? severity
                : reviewSegmentSeverity(item.sourceAlertType()));
        String status = firstText(segment.get("status"), "active");
        String normalizedStatus = Set.of("active", "detection", "alert", "ended").contains(status)
                ? status
                : "active";
        segment.put("status", normalizedStatus);
        segment.put("startTime", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
        LocalDateTime endedAt = "ended".equals(normalizedStatus)
                ? toLocalDateTime(segment.get("endTime"))
                : null;
        LocalDateTime normalizedEndTime = endedAt == null ? item.lastAlertTime() : endedAt;
        segment.put("endTime", normalizedEndTime == null ? null : normalizedEndTime.toString());
        segment.put("objectIds", objectIds.isEmpty()
                ? toStringList(segment.get("objectIds"), null)
                : objectIds);
        segment.put("zones", zones.isEmpty()
                ? toStringList(segment.get("zones"), null)
                : zones);
        segment.put("sourceAlertIds", item.sourceAlertIds() == null
                ? List.of()
                : List.copyOf(item.sourceAlertIds()));
        if (toMapList(segment.get("events")).isEmpty()) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event", "runtime_repair");
            event.put("happenedAt", item.firstAlertTime() == null ? null : item.firstAlertTime().toString());
            event.put("sourceAlertId", firstSourceAlertId(item));
            event.put("ruleCode", item.ruleCode());
            event.put("objectIds", segment.get("objectIds"));
            event.put("labels", toStringList(null, item.objectLabel()));
            event.put("zones", segment.get("zones"));
            segment.put("events", List.of(immutableNonNullMap(event)));
        }
        return immutableNonNullMap(segment);
    }

    private static boolean reviewSegmentDoubleWriteDrift(ReviewItemAggregate item,
                                                         Map<String, Object> current,
                                                         Map<String, Object> normalized) {
        if (current == null || current.isEmpty()) {
            return true;
        }
        return !Objects.equals(firstText(current.get("cameraId"), null), normalized.get("cameraId"))
                || !Objects.equals(firstText(current.get("startTime"), null), normalized.get("startTime"))
                || !Objects.equals(firstText(current.get("endTime"), null), normalized.get("endTime"))
                || !Objects.equals(toStringList(current.get("objectIds"), null), normalized.get("objectIds"))
                || !Objects.equals(toStringList(current.get("zones"), null), normalized.get("zones"))
                || !Objects.equals(toStringList(current.get("sourceAlertIds"), null), normalized.get("sourceAlertIds"))
                || !Objects.equals(firstText(current.get("status"), null), normalized.get("status"))
                || !Objects.equals(firstText(current.get("severity"), null), normalized.get("severity"))
                || !Objects.equals(firstText(current.get("segmentId"), null), normalized.get("segmentId"))
                || toMapList(current.get("events")).isEmpty()
                || item.firstAlertTime() == null;
    }

    private static boolean matchesReplayRule(ReviewItemAggregate item, ReviewRuleView rule) {
        return matchesText(rule.sourceSystem(), item.sourceSystem())
                && matchesText(rule.cameraId(), item.cameraId())
                && matchesText(rule.zoneCode(), item.zoneCode())
                && matchesText(rule.objectLabel(), item.objectLabel())
                && matchesReplayStay(rule.minStaySeconds(), item)
                && matchesReplayInertia(rule.inertiaFrames(), item);
    }

    private static boolean matchesReplayRule(ReviewItemAggregate item, ReviewRuleReplayCommand command) {
        return matchesText(command.sourceSystem(), item.sourceSystem())
                && matchesText(command.cameraId(), item.cameraId())
                && matchesText(command.zoneCode(), item.zoneCode())
                && matchesText(command.objectLabel(), item.objectLabel())
                && matchesReplayStay(command.minStaySeconds(), item);
    }

    private static boolean matchesReplayStay(Integer minStaySeconds, ReviewItemAggregate item) {
        if (minStaySeconds == null || minStaySeconds <= 0) {
            return true;
        }
        Integer staySeconds = inferredStaySeconds(item);
        return staySeconds != null && staySeconds >= minStaySeconds;
    }

    private static boolean matchesReplayInertia(Integer inertiaFrames, ReviewItemAggregate item) {
        int requiredFrames = normalizeZoneInertiaFrames(inertiaFrames);
        if (requiredFrames <= 1) {
            return true;
        }
        Integer observedFrames = inferredConsecutiveZoneFrames(item);
        return observedFrames != null && observedFrames >= requiredFrames;
    }

    private static List<Double> bottomCenterPoint(List<Double> bbox) {
        if (bbox == null || bbox.size() < 4) {
            return List.of();
        }
        Double x1 = bbox.get(0);
        Double y2 = bbox.get(3);
        Double x2 = bbox.get(2);
        if (x1 == null || x2 == null || y2 == null) {
            return List.of();
        }
        return List.of((x1 + x2) / 2D, y2);
    }

    private static boolean pointInPolygon(List<Double> point, List<List<Double>> polygon) {
        if (point == null || point.size() < 2 || polygon == null || polygon.size() < 3) {
            return false;
        }
        double x = point.get(0);
        double y = point.get(1);
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            List<Double> current = polygon.get(i);
            List<Double> previous = polygon.get(j);
            if (current == null || previous == null || current.size() < 2 || previous.size() < 2) {
                continue;
            }
            double xi = current.get(0);
            double yi = current.get(1);
            double xj = previous.get(0);
            double yj = previous.get(1);
            boolean intersects = ((yi > y) != (yj > y))
                    && x < (xj - xi) * (y - yi) / (yj - yi) + xi;
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static Integer inferredStaySeconds(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        Integer staySeconds = toInteger(reviewData.get("staySeconds"));
        if (staySeconds != null) {
            return staySeconds;
        }
        for (Map<String, Object> detection : toMapList(reviewData.get("detections"))) {
            staySeconds = toInteger(detection.get("staySeconds"));
            if (staySeconds != null) {
                return staySeconds;
            }
        }
        return null;
    }

    private Optional<ReviewRuleView> matchingRuleForGeometry(ReviewRuleGeometryCommand command) {
        return reviewRuleStore.listAll().stream()
                .filter(rule -> Objects.equals(command.ruleCode(), rule.ruleCode()))
                .filter(rule -> matchesText(rule.cameraId(), command.cameraId()))
                .filter(rule -> matchesText(rule.zoneCode(), command.zoneCode()))
                .filter(rule -> matchesText(rule.objectLabel(), command.objectLabel()))
                .findFirst();
    }

    private static Integer inferredConsecutiveZoneFrames(ReviewItemAggregate item) {
        Map<String, Object> reviewData = item.reviewData() == null ? Map.of() : item.reviewData();
        Integer frames = toInteger(reviewData.get("consecutiveZoneFrames"));
        if (frames != null) {
            return frames;
        }
        Map<String, Object> motion = toStringObjectMap(reviewData.get("motion"));
        frames = toInteger(firstNonNull(motion.get("consecutiveZoneFrames"), motion.get("consecutive_zone_frames")));
        if (frames != null) {
            return frames;
        }
        for (Map<String, Object> detection : toMapList(reviewData.get("detections"))) {
            frames = toInteger(firstNonNull(detection.get("consecutiveZoneFrames"), detection.get("consecutive_zone_frames")));
            if (frames != null) {
                return frames;
            }
        }
        return null;
    }

    private static Map<String, Object> ruleMatchTrace(ReviewRuleGeometryCommand command,
                                                      List<Double> bottomCenter,
                                                      boolean inside,
                                                      Integer minStaySeconds,
                                                      Integer zoneInertiaFrames,
                                                      Integer observedConsecutiveFrames,
                                                      boolean inertiaSatisfied,
                                                      Integer loiteringSeconds,
                                                      Integer observedStaySeconds,
                                                      boolean loiteringSatisfied) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("ruleCode", command.ruleCode());
        trace.put("cameraId", command.cameraId());
        trace.put("zoneCode", command.zoneCode());
        trace.put("objectLabel", command.objectLabel());
        trace.put("bbox", command.bbox());
        trace.put("bottomCenter", bottomCenter);
        trace.put("inside", inside);
        trace.put("minStaySeconds", minStaySeconds);
        trace.put("threshold", minStaySeconds);
        trace.put("zoneInertiaFrames", zoneInertiaFrames);
        trace.put("observedConsecutiveFrames", observedConsecutiveFrames);
        trace.put("inertiaSatisfied", inertiaSatisfied);
        trace.put("loiteringSeconds", loiteringSeconds);
        trace.put("observedStaySeconds", observedStaySeconds);
        trace.put("loiteringSatisfied", loiteringSatisfied);
        trace.put("qualifiedInside", inside && inertiaSatisfied && loiteringSatisfied);
        trace.put("ruleVersion", "shadow");
        trace.put("geometryType", "bottom_center");
        trace.put("semanticEngine", "yfeieye-rule-geometry-v1");
        trace.put("pointStrategy", "bottom_center");
        trace.put("coordinateSpace", "image");
        trace.put("operatorUserId", command.operatorUserId());
        return immutableNonNullMap(trace);
    }

    private static Map<String, Object> replayScope(ReviewRuleReplayCommand command) {
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("ruleCode", command.ruleCode());
        scope.put("sourceSystem", command.sourceSystem());
        scope.put("cameraId", command.cameraId());
        scope.put("zoneCode", command.zoneCode());
        scope.put("objectLabel", command.objectLabel());
        scope.put("minStaySeconds", command.minStaySeconds());
        scope.put("beginTime", command.beginTime() == null ? null : command.beginTime().toString());
        scope.put("endTime", command.endTime() == null ? null : command.endTime().toString());
        scope.put("operatorUserId", command.operatorUserId());
        return immutableNonNullMap(scope);
    }

    private static Map<String, Object> ruleVersionFromRule(ReviewRuleView rule) {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("ruleId", rule.id());
        version.put("ruleCode", rule.ruleCode());
        version.put("sourceSystem", rule.sourceSystem());
        version.put("cameraId", rule.cameraId());
        version.put("zoneCode", rule.zoneCode());
        version.put("objectLabel", rule.objectLabel());
        version.put("minStaySeconds", rule.minStaySeconds());
        version.put("inertiaFrames", normalizeZoneInertiaFrames(rule.inertiaFrames()));
        version.put("loiteringSeconds", firstNonNull(rule.loiteringSeconds(), rule.minStaySeconds()));
        version.put("semanticEngine", "yfeieye-rule-geometry-v1");
        version.put("source", "saved_rule");
        return immutableNonNullMap(version);
    }

    private static Map<String, Object> ruleVersionFromReplayCommand(ReviewRuleReplayCommand command) {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("ruleCode", command.ruleCode());
        version.put("sourceSystem", command.sourceSystem());
        version.put("cameraId", command.cameraId());
        version.put("zoneCode", command.zoneCode());
        version.put("objectLabel", command.objectLabel());
        version.put("minStaySeconds", command.minStaySeconds());
        version.put("inertiaFrames", 1);
        version.put("loiteringSeconds", command.minStaySeconds());
        version.put("semanticEngine", "yfeieye-rule-geometry-v1");
        version.put("source", "replay_command");
        return immutableNonNullMap(version);
    }

    private static Integer normalizeZoneInertiaFrames(Integer inertiaFrames) {
        if (inertiaFrames == null || inertiaFrames <= 0) {
            return 1;
        }
        return inertiaFrames;
    }

    private static Map<String, Object> buildReplayReport(List<ReviewItemAggregate> evaluatedItems,
                                                         int matchBeforeCount,
                                                         int matchAfterCount,
                                                         int falsePositiveBeforeCount,
                                                         int matchedFalsePositiveAfterCount,
                                                         List<String> recommendedActions) {
        return buildReplayReport(evaluatedItems, matchBeforeCount, matchAfterCount, falsePositiveBeforeCount,
                matchedFalsePositiveAfterCount, recommendedActions, Map.of());
    }

    private static Map<String, Object> buildReplayReport(List<ReviewItemAggregate> evaluatedItems,
                                                         int matchBeforeCount,
                                                         int matchAfterCount,
                                                         int falsePositiveBeforeCount,
                                                         int matchedFalsePositiveAfterCount,
                                                         List<String> recommendedActions,
                                                         Map<String, Object> ruleVersion) {
        int hitReduction = Math.max(0, matchBeforeCount - matchAfterCount);
        int falsePositiveReduction = Math.max(0, falsePositiveBeforeCount - matchedFalsePositiveAfterCount);
        int possibleMissedCount = matchAfterCount == 0
                ? evaluatedItems.size()
                : Math.max(0, matchBeforeCount - matchAfterCount);
        boolean recallRisk = possibleMissedCount > 0 && recommendedActions.contains("check_recall_risk");
        boolean shouldApply = recommendedActions.contains("safe_to_apply") && !recallRisk;

        Map<String, Object> impactScope = new LinkedHashMap<>();
        impactScope.put("cameraIds", distinctValues(evaluatedItems.stream().map(ReviewItemAggregate::cameraId).toList()));
        impactScope.put("zoneCodes", distinctValues(evaluatedItems.stream().map(ReviewItemAggregate::zoneCode).toList()));
        impactScope.put("objectLabels", distinctValues(evaluatedItems.stream().map(ReviewItemAggregate::objectLabel).toList()));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("shouldApply", shouldApply);
        report.put("decision", shouldApply ? "apply" : "review_before_apply");
        report.put("hitReduction", hitReduction);
        report.put("matchBeforeCount", matchBeforeCount);
        report.put("matchAfterCount", matchAfterCount);
        report.put("falsePositiveReduction", falsePositiveReduction);
        report.put("possibleMissedCount", possibleMissedCount);
        report.put("impactScope", impactScope);
        report.put("ruleVersion", immutableNonNullMap(ruleVersion));
        Map<String, Object> geometrySemantics = new LinkedHashMap<>();
        geometrySemantics.put("objectPoint", "bottom_center");
        geometrySemantics.put("bboxOrder", "x1_y1_x2_y2");
        geometrySemantics.put("zoneInertiaFrames", ruleVersion.getOrDefault("inertiaFrames", 1));
        geometrySemantics.put("loiteringSeconds", ruleVersion.get("loiteringSeconds"));
        geometrySemantics.put("singleSourceRuleVersion", !ruleVersion.isEmpty());
        geometrySemantics.put("sharedBy", List.of("rule_editor", "backend_replay", "evidence_verifier"));
        report.put("geometrySemantics", immutableNonNullMap(geometrySemantics));
        report.put("ruleLifecyclePolicy", Map.of(
                "directApplyAllowed", false,
                "shadowEvaluationRequired", true,
                "approvalRequired", true,
                "versionedRollbackRequired", true,
                "rollbackSupported", true
        ));
        return immutableNonNullMap(report);
    }

    private static List<String> distinctValues(List<String> values) {
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (hasText(value)) {
                distinct.add(value);
            }
        }
        return List.copyOf(distinct);
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

    private Map<String, Object> withRuleGovernanceEvidence(ReviewItemAggregate item,
                                                           Map<String, Object> suggestion) {
        Map<String, Object> updated = new LinkedHashMap<>(suggestion == null ? Map.of() : suggestion);
        Map<String, Object> shadowEvaluation = toStringObjectMap(updated.get("shadowEvaluation"));
        if (shadowEvaluation.isEmpty()) {
            shadowEvaluation = buildRuleShadowEvaluation(item);
            updated.put("shadowEvaluation", shadowEvaluation);
        }
        if (toStringObjectMap(updated.get("replayReport")).isEmpty()) {
            updated.put("replayReport", buildRuleSuggestionReplayReport(item, shadowEvaluation));
        }
        return immutableNonNullMap(updated);
    }

    private static void requireRuleGovernanceEvidence(Map<String, Object> suggestion) {
        if (toStringObjectMap(suggestion == null ? null : suggestion.get("shadowEvaluation")).isEmpty()
                || toStringObjectMap(suggestion == null ? null : suggestion.get("replayReport")).isEmpty()) {
            throw new IllegalStateException(
                    "rule suggestion requires existing shadowEvaluation and replayReport before approval or apply"
            );
        }
    }

    private Map<String, Object> buildRuleShadowEvaluation(ReviewItemAggregate item) {
        List<ReviewItemAggregate> scopedItems = listWorkbench(new ReviewQuery(
                null,
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                null,
                null,
                null,
                null,
                null,
                null
        )).stream()
                .filter(candidate -> Objects.equals(candidate.ruleCode(), item.ruleCode()))
                .filter(candidate -> sameRuleScope(candidate, item))
                .toList();
        long totalCount = scopedItems.size();
        long falsePositiveCount = scopedItems.stream()
                .filter(candidate -> STATUS_FALSE_POSITIVE.equals(candidate.reviewStatus()))
                .count();
        long estimatedSuppressedCount = falsePositiveCount;
        long afterFalsePositiveCount = Math.max(0L, falsePositiveCount - estimatedSuppressedCount);
        Map<String, Object> shadow = new LinkedHashMap<>();
        shadow.put("evaluatedReviewItemCount", (int) totalCount);
        shadow.put("estimatedSuppressedCount", (int) estimatedSuppressedCount);
        shadow.put("beforeFalsePositiveRate", roundRate(falsePositiveCount, totalCount));
        shadow.put("afterFalsePositiveRate", roundRate(afterFalsePositiveCount, totalCount));
        shadow.put("scopeCameraId", item.cameraId());
        shadow.put("scopeZoneCode", item.zoneCode());
        shadow.put("scopeObjectLabel", item.objectLabel());
        shadow.put("evaluatedAt", LocalDateTime.now().toString());
        return immutableNonNullMap(shadow);
    }

    private Map<String, Object> buildRuleSuggestionReplayReport(ReviewItemAggregate item,
                                                                Map<String, Object> shadowEvaluation) {
        List<ReviewItemAggregate> scopedItems = listWorkbench(new ReviewQuery(
                null,
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                null,
                null,
                null,
                null,
                null,
                null
        )).stream()
                .filter(candidate -> Objects.equals(candidate.ruleCode(), item.ruleCode()))
                .filter(candidate -> sameRuleScope(candidate, item))
                .toList();
        int falsePositiveCount = (int) scopedItems.stream()
                .filter(candidate -> STATUS_FALSE_POSITIVE.equals(candidate.reviewStatus()))
                .count();
        List<String> recommendedActions = List.of("safe_to_apply", "check_recall_risk");
        Map<String, Object> ruleVersion = ruleVersionFromRuleSuggestionItem(item);
        Map<String, Object> report = new LinkedHashMap<>(buildReplayReport(
                scopedItems,
                scopedItems.size(),
                0,
                falsePositiveCount,
                0,
                recommendedActions,
                ruleVersion
        ));
        report.put("evaluatedReviewItemIds", scopedItems.stream().map(ReviewItemAggregate::id).toList());
        report.put("evaluatedCount", scopedItems.size());
        report.put("sampleWindow", replaySampleWindow(scopedItems, item));
        report.put("hitComparison", replayHitComparison(scopedItems.size(), 0, falsePositiveCount, 0));
        report.put("falseNegativeEstimate", replayFalseNegativeEstimate(toInteger(report.get("possibleMissedCount"))));
        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("sourceSystem", item.sourceSystem());
        scope.put("ruleCode", item.ruleCode());
        scope.put("cameraId", item.cameraId());
        scope.put("zoneCode", item.zoneCode());
        scope.put("objectLabel", item.objectLabel());
        report.put("scope", immutableNonNullMap(scope));
        report.put("recommendedActions", recommendedActions);
        report.put("shadowEvaluation", shadowEvaluation);
        report.put("replayedAt", LocalDateTime.now().toString());
        return immutableNonNullMap(report);
    }

    private static Map<String, Object> ruleVersionFromRuleSuggestionItem(ReviewItemAggregate item) {
        Map<String, Object> suggestion = item.ruleSuggestion() == null ? Map.of() : item.ruleSuggestion();
        Integer minStaySeconds = firstNonNull(toInteger(suggestion.get("minStaySeconds")), 15);
        Integer loiteringSeconds = firstNonNull(toInteger(suggestion.get("loiteringSeconds")), minStaySeconds);
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("ruleCode", item.ruleCode());
        version.put("sourceSystem", item.sourceSystem());
        version.put("cameraId", item.cameraId());
        version.put("zoneCode", item.zoneCode());
        version.put("objectLabel", item.objectLabel());
        version.put("minStaySeconds", minStaySeconds);
        version.put("inertiaFrames", normalizeZoneInertiaFrames(toInteger(suggestion.get("inertiaFrames"))));
        version.put("loiteringSeconds", loiteringSeconds);
        version.put("semanticEngine", "yfeieye-rule-geometry-v1");
        version.put("source", "rule_suggestion");
        version.put("suggestionAction", suggestion.get("action"));
        version.put("lifecycleStatus", firstText(suggestion.get("lifecycleStatus"), item.ruleSuggestionStatus()));
        return immutableNonNullMap(version);
    }

    private static Map<String, Object> replaySampleWindow(List<ReviewItemAggregate> scopedItems,
                                                          ReviewItemAggregate fallbackItem) {
        LocalDateTime startTime = scopedItems.stream()
                .map(candidate -> firstNonNull(candidate.firstAlertTime(), candidate.lastAlertTime()))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(firstNonNull(fallbackItem.firstAlertTime(), fallbackItem.lastAlertTime()));
        LocalDateTime endTime = scopedItems.stream()
                .map(candidate -> firstNonNull(candidate.lastAlertTime(), candidate.firstAlertTime()))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(firstNonNull(fallbackItem.lastAlertTime(), fallbackItem.firstAlertTime()));
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("startTime", startTime == null ? null : startTime.toString());
        window.put("endTime", endTime == null ? null : endTime.toString());
        window.put("sampleCount", scopedItems.size());
        window.put("reviewItemIds", scopedItems.stream().map(ReviewItemAggregate::id).toList());
        return immutableNonNullMap(window);
    }

    private static Map<String, Object> replayHitComparison(int beforeCount,
                                                           int afterCount,
                                                           int falsePositiveBeforeCount,
                                                           int falsePositiveAfterCount) {
        Map<String, Object> comparison = new LinkedHashMap<>();
        comparison.put("beforeCount", beforeCount);
        comparison.put("afterCount", afterCount);
        comparison.put("difference", Math.max(0, beforeCount - afterCount));
        comparison.put("falsePositiveBeforeCount", falsePositiveBeforeCount);
        comparison.put("falsePositiveAfterCount", falsePositiveAfterCount);
        comparison.put("falsePositiveReduction", Math.max(0, falsePositiveBeforeCount - falsePositiveAfterCount));
        return immutableNonNullMap(comparison);
    }

    private static Map<String, Object> replayFalseNegativeEstimate(Integer possibleMissedCount) {
        int missedCount = possibleMissedCount == null ? 0 : Math.max(0, possibleMissedCount);
        Map<String, Object> estimate = new LinkedHashMap<>();
        estimate.put("possibleMissedCount", missedCount);
        estimate.put("riskLevel", missedCount > 0 ? "review_required" : "low");
        estimate.put("recommendedAction", missedCount > 0 ? "check_recall_risk" : "none");
        return immutableNonNullMap(estimate);
    }

    private static boolean sameRuleScope(ReviewItemAggregate left, ReviewItemAggregate right) {
        return Objects.equals(left.sourceSystem(), right.sourceSystem())
                && Objects.equals(left.ruleCode(), right.ruleCode())
                && Objects.equals(left.cameraId(), right.cameraId())
                && Objects.equals(left.zoneCode(), right.zoneCode())
                && Objects.equals(left.objectLabel(), right.objectLabel());
    }

    private void markUserReviewed(Long reviewItemId, Long reviewerUserId, LocalDateTime reviewedAt) {
        if (reviewerUserId == null) {
            return;
        }
        reviewItemStore.upsertUserReviewStatus(reviewItemId, reviewerUserId, true, reviewedAt);
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

    private boolean matchesReviewerStatus(Long reviewerUserId, Long reviewItemId) {
        return reviewerUserId == null || reviewItemStore.findUserReviewStatus(reviewItemId, reviewerUserId)
                .map(ReviewUserStatusView::hasBeenReviewed)
                .orElse(false);
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
            return aiSummaryRedactionPolicy.getRedactedValue();
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
            return aiSummaryRedactionPolicy.getRedactedValue();
        }
        return value;
    }

    private boolean shouldRedactAiSummaryValue(String key, Object value) {
        return aiSummaryRedactionPolicy.shouldRedact(key, value);
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

    private static Map<String, Object> buildStructuredAiSummaryData(Long reviewCaseId,
                                                                    String title,
                                                                    String summary,
                                                                    List<ReviewItemAggregate> reviewItems,
                                                                    List<String> evidenceGaps,
                                                                    List<String> recommendedActions) {
        List<String> cameras = reviewItems.stream()
                .map(ReviewItemAggregate::cameraId)
                .filter(SupervisionAlertReviewServiceImpl::hasText)
                .distinct()
                .toList();
        List<String> zones = reviewItems.stream()
                .map(ReviewItemAggregate::zoneCode)
                .filter(SupervisionAlertReviewServiceImpl::hasText)
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
        provenance.put("redactionPolicyVersion", aiSummaryRedactionPolicy.getPolicyVersion());
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
        reviewItemStore.recordCaseAudit(
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

    private SemanticSearchSnapshot semanticSearchSnapshot(ReviewQuery filters) {
        List<ReviewSemanticIndexEntry> indexEntries = reviewItemStore.listSemanticIndex(filters);
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
            reviewItemStore.findById(entry.reviewItemId()).ifPresent(item -> {
                candidates.add(new ReviewSemanticSearchCandidate(item, entry.document()));
                indexProvenanceByItemId.put(entry.reviewItemId(), entry);
            });
        }
        if (candidates.isEmpty()) {
            candidates.addAll(listWorkbench(filters).stream()
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
        for (ReviewEvidenceItem evidenceItem : reviewItemStore.listTimeline(item.id())) {
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

    private static List<Long> reviewItemIdsFromTimeline(List<ReviewCaseTimelineItem> timeline) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ReviewCaseTimelineItem item : timeline) {
            if (item.reviewItemId() != null) {
                ids.add(item.reviewItemId());
            }
        }
        return List.copyOf(ids);
    }

    private Map<String, Object> buildEvidenceManifest(Long reviewCaseId,
                                                             List<Long> reviewItemIds,
                                                             List<String> evidenceUris,
                                                             List<ReviewCaseTimelineItem> timeline,
                                                             List<ReviewItemAggregate> reviewItems,
                                                             List<ReviewEvidenceVideoExportResult> videoExports,
                                                             String format,
                                                             Long operatorUserId,
                                                             Long approverUserId,
                                                             String approvalNote,
                                                             String requestKey,
                                                             LocalDateTime expiresAt,
                                                             LocalDateTime generatedAt) {
        List<Long> eventIds = eventIdsFromTimeline(timeline);
        List<Map<String, Object>> files = evidenceFileHashes(evidenceUris);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("manifestVersion", "2");
        manifest.put("schema", "yfeieye-evidence-manifest-v2");
        manifest.put("reviewCaseId", reviewCaseId);
        manifest.put("reviewItemIds", reviewItemIds);
        manifest.put("evidenceUris", evidenceUris);
        manifest.put("files", files);
        manifest.put("timelineSize", timeline.size());
        manifest.put("format", format);
        manifest.put("operatorUserId", operatorUserId);
        manifest.put("generatedBy", operatorUserId);
        manifest.put("requestKey", requestKey);
        manifest.put("generatedAt", generatedAt.toString());
        manifest.put("expiresAt", expiresAt.toString());
        Map<String, Object> operator = new LinkedHashMap<>();
        operator.put("exportedBy", operatorUserId);
        operator.put("generatedBy", operatorUserId);
        operator.put("approvedBy", approverUserId);
        manifest.put("operator", immutableNonNullMap(operator));
        manifest.put("ruleVersions", manifestRuleVersions(reviewItems));
        manifest.put("reviewData", manifestReviewData(reviewItems));
        manifest.put("coverageSummary", manifestCoverageSummary(reviewItems));
        manifest.put("decisionTrail", manifestDecisionTrail(reviewItems));
        manifest.put("aiSummaryVersion", "review-ai-summary-v1");
        manifest.put("downloadRecords", List.of());
        manifest.put("approval", approvalManifest(approverUserId, approvalNote, generatedAt));
        manifest.put("eventReferences", eventIds.stream()
                .map(eventId -> Map.<String, Object>of(
                        "eventId", eventId,
                        "relation", "evidence_for_event"))
                .toList());
        manifest.put("videoExports", videoExports.stream()
                .map(SupervisionAlertReviewServiceImpl::videoExportToMap)
                .toList());
        String packageChecksum = sha256Token(
                reviewCaseId,
                reviewItemIds,
                evidenceUris,
                files,
                videoExports,
                timeline.stream().map(ReviewCaseTimelineItem::toString).toList(),
                format
        );
        manifest.put("packageChecksum", packageChecksum);
        manifest.put("checksum", packageChecksum);
        manifest.put("immutableAudit", Map.of(
                "algorithm", "sha256(previousHash + canonicalEntry)",
                "entryCount", 1,
                "headHash", sha256Token(packageChecksum, generatedAt)
        ));
        String manifestHash = expectedManifestHash(manifest);
        manifest.put("manifestHash", manifestHash);
        manifest.put("signature", manifestSignature(manifest, manifestHash, generatedAt));
        return Map.copyOf(manifest);
    }

    private static List<Map<String, Object>> manifestRuleVersions(List<ReviewItemAggregate> reviewItems) {
        if (reviewItems == null || reviewItems.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> versions = new ArrayList<>();
        for (ReviewItemAggregate item : reviewItems) {
            Map<String, Object> suggestion = item.ruleSuggestion() == null ? Map.of() : item.ruleSuggestion();
            Map<String, Object> version = new LinkedHashMap<>();
            version.put("reviewItemId", item.id());
            version.put("ruleCode", item.ruleCode());
            version.put("sourceSystem", item.sourceSystem());
            version.put("cameraId", item.cameraId());
            version.put("zoneCode", item.zoneCode());
            version.put("objectLabel", item.objectLabel());
            version.put("ruleSuggestionStatus", item.ruleSuggestionStatus());
            version.put("ruleSuggestionUpdatedAt", item.ruleSuggestionUpdatedAt() == null
                    ? null
                    : item.ruleSuggestionUpdatedAt().toString());
            version.put("applicationMode", RULE_SUGGESTION_APPLIED.equals(item.ruleSuggestionStatus()) ? "applied" : "shadow");
            version.put("shadowEvaluation", toStringObjectMap(suggestion.get("shadowEvaluation")));
            version.put("replayReport", toStringObjectMap(suggestion.get("replayReport")));
            version.put("versionHash", sha256Token(
                    item.ruleCode(),
                    item.ruleSuggestionStatus(),
                    item.ruleSuggestionUpdatedAt(),
                    suggestion
            ));
            versions.add(immutableNonNullMap(version));
        }
        return List.copyOf(versions);
    }

    private static Map<String, Object> manifestReviewData(List<ReviewItemAggregate> reviewItems) {
        Map<String, Object> reviewData = new LinkedHashMap<>();
        if (reviewItems == null) {
            return Map.of();
        }
        for (ReviewItemAggregate item : reviewItems) {
            reviewData.put(String.valueOf(item.id()), item.reviewData() == null ? Map.of() : item.reviewData());
        }
        return immutableNonNullMap(reviewData);
    }

    private static List<Map<String, Object>> manifestCoverageSummary(List<ReviewItemAggregate> reviewItems) {
        if (reviewItems == null || reviewItems.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> coverage = new ArrayList<>();
        for (ReviewItemAggregate item : reviewItems) {
            Map<String, Object> storage = toStringObjectMap(item.reviewData() == null
                    ? null
                    : item.reviewData().get("recordStorage"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("reviewItemId", item.id());
            row.put("recordEvidenceStatus", item.recordEvidenceStatus());
            row.put("recordEvidenceCheckedAt", item.recordEvidenceCheckedAt() == null
                    ? null
                    : item.recordEvidenceCheckedAt().toString());
            row.put("recordEvidenceMessage", item.recordEvidenceMessage());
            row.put("syncStatus", storage.get("syncStatus"));
            row.put("availableSeconds", storage.get("availableSeconds"));
            row.put("missingSeconds", storage.get("missingSeconds"));
            row.put("motionSeconds", storage.get("motionSeconds"));
            coverage.add(immutableNonNullMap(row));
        }
        return List.copyOf(coverage);
    }

    private static List<Map<String, Object>> manifestDecisionTrail(List<ReviewItemAggregate> reviewItems) {
        if (reviewItems == null || reviewItems.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> decisionTrail = new ArrayList<>();
        for (ReviewItemAggregate item : reviewItems) {
            decisionTrail.add(decisionTrailRow(item));
        }
        return List.copyOf(decisionTrail);
    }

    private static Map<String, Object> decisionTrailRow(ReviewItemAggregate item) {
        Map<String, Object> suggestion = item.ruleSuggestion() == null ? Map.of() : item.ruleSuggestion();
        String reason = hasText(item.ignoreReason()) ? item.ignoreReason() : toText(suggestion.get("reason"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("reviewItemId", item.id());
        row.put("reviewItemNo", item.reviewItemNo());
        row.put("reviewStatus", item.reviewStatus());
        row.put("reason", reason);
        row.put("reviewerUserId", item.reviewerUserId());
        row.put("reviewedAt", item.reviewedAt() == null ? null : item.reviewedAt().toString());
        row.put("eventId", item.eventId());
        row.put("convertedAt", item.convertedAt() == null ? null : item.convertedAt().toString());
        row.put("eventStatus", item.eventStatus());
        row.put("closeCheckStatus", item.closeCheckStatus());
        row.put("evidenceStatus", item.evidenceStatus());
        row.put("ruleSuggestionStatus", item.ruleSuggestionStatus());
        row.put("recordEvidenceStatus", item.recordEvidenceStatus());
        row.put("correlationId", item.reviewData() == null ? null : item.reviewData().get("correlationId"));
        return immutableNonNullMap(row);
    }

    private List<Map<String, Object>> reconstructDecisionTrail(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long reviewItemId : reviewItemIds) {
            reviewItemStore.findById(reviewItemId)
                    .map(SupervisionAlertReviewServiceImpl::decisionTrailRow)
                    .ifPresent(rows::add);
        }
        return List.copyOf(rows);
    }

    private static Map<String, Object> auditEntryToManifestMap(ReviewEvidenceAuditEntry entry) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("actionType", entry.actionType());
        values.put("jobNo", entry.jobNo());
        values.put("fileHash", entry.fileHash());
        values.put("operatorUserId", entry.operatorUserId());
        values.put("actionNote", entry.actionNote());
        values.put("happenedAt", entry.happenedAt() == null ? null : entry.happenedAt().toString());
        return immutableNonNullMap(values);
    }

    private static String expectedManifestHash(Map<String, Object> manifest) {
        Map<String, Object> hashable = new LinkedHashMap<>(manifest == null ? Map.of() : manifest);
        hashable.remove("manifestHash");
        hashable.remove("signature");
        return sha256Token(canonicalValue(hashable));
    }

    private Map<String, Object> manifestSignature(Map<String, Object> manifest,
                                                  String manifestHash,
                                                  LocalDateTime signedAt) {
        return evidenceManifestSigner.sign(manifestHash, signedAt);
    }

    private static String canonicalValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    sorted.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(entry.getKey()).append('=').append(canonicalValue(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                builder.append(canonicalValue(item));
            }
            return builder.append(']').toString();
        }
        return value == null ? "<null>" : String.valueOf(value);
    }

    private static Map<String, Object> approvalManifest(Long approverUserId,
                                                        String approvalNote,
                                                        LocalDateTime generatedAt) {
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("approvedBy", approverUserId);
        approval.put("approvedAt", approverUserId == null ? null : generatedAt.toString());
        approval.put("approvalNote", hasText(approvalNote) ? approvalNote : null);
        return immutableNonNullMap(approval);
    }

    private static List<Map<String, Object>> evidenceFileHashes(List<String> evidenceUris) {
        if (evidenceUris == null || evidenceUris.isEmpty()) {
            return List.of();
        }
        return evidenceUris.stream()
                .map(uri -> {
                    Map<String, Object> file = new LinkedHashMap<>();
                    file.put("uri", uri);
                    file.put("role", uri.endsWith(".mp4") || uri.endsWith(".flv") ? MATERIAL_RECORD : MATERIAL_SNAPSHOT);
                    file.put("hash", sha256Token(uri));
                    return Map.copyOf(file);
                })
                .toList();
    }

    private static List<Long> eventIdsFromTimeline(List<ReviewCaseTimelineItem> timeline) {
        if (timeline == null || timeline.isEmpty()) {
            return List.of();
        }
        Set<Long> eventIds = new LinkedHashSet<>();
        for (ReviewCaseTimelineItem item : timeline) {
            if (item == null || !hasText(item.materialUri())) {
                continue;
            }
            String prefix = "converted_to_event:";
            if (!item.materialUri().startsWith(prefix)) {
                continue;
            }
            Long eventId = toLong(item.materialUri().substring(prefix.length()));
            if (eventId != null) {
                eventIds.add(eventId);
            }
        }
        return List.copyOf(eventIds);
    }

    private List<Long> boundEventIds(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        Set<Long> eventIds = new LinkedHashSet<>();
        for (Long reviewItemId : reviewItemIds) {
            reviewItemStore.findById(reviewItemId)
                    .map(ReviewItemAggregate::eventId)
                    .filter(Objects::nonNull)
                    .ifPresent(eventIds::add);
        }
        return List.copyOf(eventIds);
    }

    private static String exportFileHash(ReviewEvidenceExportPackage exportPackage) {
        List<Map<String, Object>> videoExports = toMapList(exportPackage.manifest().get("videoExports"));
        if (videoExports.size() == 1 && hasCompleteVideoExportProvenance(videoExports.get(0))) {
            String realVideoHash = toText(videoExports.get(0).get("fileHash"));
            if (hasText(realVideoHash) && SHA256_HASH.matcher(realVideoHash).matches()) {
                return realVideoHash;
            }
        }
        String payload = exportPackage.packageNo()
                + "|" + exportPackage.reviewCaseId()
                + "|" + exportPackage.reviewItemIds()
                + "|" + exportPackage.evidenceUris()
                + "|" + exportPackage.manifest().get("checksum");
        return "sha256:" + sha256Hex(payload);
    }

    private static String sha256Token(Object... values) {
        StringBuilder payload = new StringBuilder();
        if (values != null) {
            for (Object value : values) {
                payload.append(value == null ? "<null>" : value).append('\u001f');
            }
        }
        return "sha256:" + sha256Hex(payload.toString());
    }

    private static String sha256Hex(String payload) {
        return sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private List<ReviewEvidenceVideoExportResult> requestVideoExports(Long reviewCaseId,
                                                                      List<Long> reviewItemIds,
                                                                      List<ReviewCaseTimelineItem> timeline,
                                                                      String format,
                                                                      LocalDateTime expiresAt,
                                                                      Map<Long, List<RecordCoverageSegment>>
                                                                              coverageByReviewItemId) {
        if (timeline.isEmpty()) {
            if (requiresVideoArtifact(format)) {
                throw new IllegalStateException("video export requested but case timeline is empty");
            }
            return List.of();
        }
        List<ReviewEvidenceVideoExportResult> results = new ArrayList<>();
        List<VideoExportCameraRequestPlan> requestPlans = new ArrayList<>();
        Set<String> requestedSegments = new LinkedHashSet<>();
        Map<String, List<VideoExportSegmentContext>> segmentsByCamera = new LinkedHashMap<>();
        boolean videoArtifactRequired = requiresVideoArtifact(format);
        List<ReviewCaseTimelineItem> orderedTimeline = timeline.stream()
                .sorted(Comparator
                        .comparing(ReviewCaseTimelineItem::happenedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewCaseTimelineItem::reviewItemId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewCaseTimelineItem::materialUri, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (ReviewCaseTimelineItem timelineItem : orderedTimeline) {
            if (!MATERIAL_RECORD.equals(timelineItem.materialType())
                    || timelineItem.reviewItemId() == null
                    || !reviewItemIds.contains(timelineItem.reviewItemId())
                    || !hasText(timelineItem.materialUri())) {
                continue;
            }
            Optional<ReviewItemAggregate> aggregate = reviewItemStore.findById(timelineItem.reviewItemId());
            if (aggregate.isEmpty()) {
                continue;
            }
            ReviewItemAggregate item = aggregate.get();
            if (!hasText(item.cameraId())) {
                continue;
            }
            segmentsByCamera.computeIfAbsent(item.cameraId(), ignored -> new ArrayList<>())
                    .add(new VideoExportSegmentContext(item, timelineItem));
        }
        for (Map.Entry<String, List<VideoExportSegmentContext>> entry : segmentsByCamera.entrySet()) {
            List<VideoExportSegmentContext> contexts = entry.getValue();
            if (contexts.isEmpty()) {
                continue;
            }
            Map<String, Set<String>> canonicalUrisByObjectName = canonicalRecordUrisByObjectName(contexts);
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests = new ArrayList<>(contexts.size());
            for (VideoExportSegmentContext context : contexts) {
                String normalizedRecordUri = normalizeVideoExportRecordUri(
                        context.timelineItem().materialUri(),
                        canonicalUrisByObjectName
                );
                LocalDateTime itemStartTime = exportStartTime(context.item(), context.timelineItem());
                LocalDateTime itemEndTime = exportEndTime(context.item(), context.timelineItem());
                if (itemStartTime == null || itemEndTime == null || !itemEndTime.isAfter(itemStartTime)) {
                    continue;
                }
                int segmentCountBeforeCoverage = segmentRequests.size();
                boolean hasPhysicalCoverageMatch = false;
                List<RecordCoverageSegment> coverage = coverageByReviewItemId.computeIfAbsent(
                        context.item().id(),
                        ignored -> getRecordCoverage(context.item().id())
                );
                for (RecordCoverageSegment coverageSegment : coverage) {
                    if (coverageSegment == null
                            || !hasText(coverageSegment.recordUri())) {
                        continue;
                    }
                    String normalizedCoverageRecordUri = normalizeVideoExportRecordUri(
                            coverageSegment.recordUri(),
                            canonicalUrisByObjectName
                    );
                    if (!normalizedRecordUri.equals(normalizedCoverageRecordUri)) {
                        continue;
                    }
                    if (RECORD_COVERAGE_MISSING.equals(coverageSegment.status())) {
                        continue;
                    }
                    hasPhysicalCoverageMatch = true;
                    if (coverageSegment.startTime() == null
                            || coverageSegment.endTime() == null
                            || !coverageSegment.endTime().isAfter(coverageSegment.startTime())) {
                        continue;
                    }
                    LocalDateTime clipStartTime = max(itemStartTime, coverageSegment.startTime());
                    LocalDateTime clipEndTime = min(itemEndTime, coverageSegment.endTime());
                    if (!clipEndTime.isAfter(clipStartTime)) {
                        continue;
                    }
                    String requestKey = entry.getKey()
                            + "|" + context.item().id()
                            + "|" + normalizedRecordUri
                            + "|" + coverageSegment.startTime()
                            + "|" + coverageSegment.endTime()
                            + "|" + clipStartTime
                            + "|" + clipEndTime;
                    if (!requestedSegments.add(requestKey)) {
                        continue;
                    }
                    segmentRequests.add(new ReviewEvidenceVideoSegmentRequest(
                            context.item().id(),
                            context.timelineItem().sourceAlertId(),
                            normalizedRecordUri,
                            coverageSegment.startTime(),
                            coverageSegment.endTime(),
                            clipStartTime,
                            clipEndTime,
                            segmentRequests.size()
                    ));
                }
                if (segmentRequests.size() == segmentCountBeforeCoverage
                        && !hasPhysicalCoverageMatch
                        && context.timelineItem().recordStartTime() == null
                        && minioRecordObjectName(context.timelineItem().materialUri()).isEmpty()) {
                    String requestKey = entry.getKey()
                            + "|" + context.item().id()
                            + "|" + normalizedRecordUri
                            + "|<manual>|<manual>"
                            + "|" + itemStartTime
                            + "|" + itemEndTime;
                    if (requestedSegments.add(requestKey)) {
                        segmentRequests.add(new ReviewEvidenceVideoSegmentRequest(
                                context.item().id(),
                                context.timelineItem().sourceAlertId(),
                                normalizedRecordUri,
                                itemStartTime,
                                itemEndTime,
                                segmentRequests.size()
                        ));
                    }
                }
            }
            if (segmentRequests.isEmpty()) {
                continue;
            }
            List<ReviewEvidenceVideoSegmentRequest> orderedSegmentRequests = orderAndNumberVideoSegments(
                    segmentRequests);
            assertReviewItemVideoSegmentsDoNotOverlap(orderedSegmentRequests);
            requestPlans.add(new VideoExportCameraRequestPlan(
                    entry.getKey(),
                    orderedSegmentRequests
            ));
        }
        if (videoArtifactRequired) {
            Set<Long> expectedReviewItemIds = reviewItemIds.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> segmentedReviewItemIds = requestPlans.stream()
                    .flatMap(plan -> plan.segmentRequests().stream())
                    .map(ReviewEvidenceVideoSegmentRequest::reviewItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            expectedReviewItemIds.removeAll(segmentedReviewItemIds);
            if (!expectedReviewItemIds.isEmpty()) {
                throw new IllegalStateException(
                        "video export requested but review items have no exportable recording segment: "
                                + expectedReviewItemIds);
            }
            if (requestedSegments.isEmpty()) {
                throw new IllegalStateException("video export requested but case has no recording evidence");
            }
        }
        for (VideoExportCameraRequestPlan plan : requestPlans) {
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests = plan.segmentRequests();
            ReviewEvidenceVideoSegmentRequest firstSegment = segmentRequests.get(0);
            ReviewItemAggregate firstItem = reviewItemStore.findById(firstSegment.reviewItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "review item missing for VIDEO export: " + firstSegment.reviewItemId()));
            LocalDateTime startTime = segmentRequests.stream()
                    .map(ReviewEvidenceVideoSegmentRequest::clipStartTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime endTime = segmentRequests.stream()
                    .map(ReviewEvidenceVideoSegmentRequest::clipEndTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            try {
                Optional<ReviewEvidenceVideoExportResult> exportResult = videoEvidenceExportProvider.export(
                        new ReviewEvidenceVideoExportRequest(
                                reviewCaseId,
                                firstSegment.reviewItemId(),
                                firstItem.deviceId(),
                                plan.cameraId(),
                                firstSegment.sourceAlertId(),
                                startTime,
                                endTime,
                                firstSegment.recordUri(),
                                format,
                                segmentRequests,
                                expiresAt
                        ));
                if (exportResult.isPresent()) {
                    if (videoArtifactRequired && !hasCompleteVideoExportProvenance(exportResult.get())) {
                        throw new IllegalStateException("VIDEO export returned incomplete media provenance");
                    }
                    results.add(exportResult.get());
                } else if (videoArtifactRequired) {
                    throw new IllegalStateException("VIDEO export did not return a ready media artifact");
                }
            } catch (RuntimeException exception) {
                if (videoArtifactRequired) {
                    throw new IllegalStateException("VIDEO export failed; evidence job cannot be ready", exception);
                }
            }
        }
        if (videoArtifactRequired && results.size() != requestPlans.size()) {
            throw new IllegalStateException("VIDEO export did not complete every requested camera timeline");
        }
        return List.copyOf(results);
    }

    private record VideoExportSegmentContext(ReviewItemAggregate item,
                                             ReviewCaseTimelineItem timelineItem) {
    }

    private record VideoExportCameraRequestPlan(String cameraId,
                                                List<ReviewEvidenceVideoSegmentRequest> segmentRequests) {
    }

    private static List<ReviewEvidenceVideoSegmentRequest> orderAndNumberVideoSegments(
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests) {
        List<ReviewEvidenceVideoSegmentRequest> ordered = segmentRequests.stream()
                .sorted(Comparator
                        .comparing(ReviewEvidenceVideoSegmentRequest::clipStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceVideoSegmentRequest::segmentStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceVideoSegmentRequest::reviewItemId,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceVideoSegmentRequest::recordUri,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ReviewEvidenceVideoSegmentRequest> numbered = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ReviewEvidenceVideoSegmentRequest segment = ordered.get(index);
            numbered.add(new ReviewEvidenceVideoSegmentRequest(
                    segment.reviewItemId(),
                    segment.sourceAlertId(),
                    segment.recordUri(),
                    segment.segmentStartTime(),
                    segment.segmentEndTime(),
                    segment.clipStartTime(),
                    segment.clipEndTime(),
                    index
            ));
        }
        return List.copyOf(numbered);
    }

    private static void assertReviewItemVideoSegmentsDoNotOverlap(
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests) {
        for (int leftIndex = 0; leftIndex < segmentRequests.size(); leftIndex++) {
            ReviewEvidenceVideoSegmentRequest left = segmentRequests.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < segmentRequests.size(); rightIndex++) {
                ReviewEvidenceVideoSegmentRequest right = segmentRequests.get(rightIndex);
                if (Objects.equals(left.recordUri(), right.recordUri())
                        && (!Objects.equals(left.segmentStartTime(), right.segmentStartTime())
                        || !Objects.equals(left.segmentEndTime(), right.segmentEndTime()))) {
                    throw new IllegalStateException(
                            "record URI has conflicting physical windows: " + left.recordUri());
                }
                if (!Objects.equals(left.reviewItemId(), right.reviewItemId())
                        || left.clipStartTime() == null
                        || left.clipEndTime() == null
                        || right.clipStartTime() == null
                        || right.clipEndTime() == null) {
                    continue;
                }
                if (left.clipStartTime().isBefore(right.clipEndTime())
                        && right.clipStartTime().isBefore(left.clipEndTime())) {
                    throw new IllegalStateException(
                            "review item video segments overlap: " + left.reviewItemId());
                }
            }
        }
    }

    private static Map<String, Set<String>> canonicalRecordUrisByObjectName(
            List<VideoExportSegmentContext> contexts) {
        Map<String, Set<String>> canonicalUrisByObjectName = new LinkedHashMap<>();
        for (VideoExportSegmentContext context : contexts) {
            String recordUri = context.timelineItem().materialUri();
            canonicalRecordObjectName(recordUri).ifPresent(objectName ->
                    canonicalUrisByObjectName.computeIfAbsent(objectName, ignored -> new LinkedHashSet<>())
                            .add(recordUri));
        }
        return canonicalUrisByObjectName;
    }

    private static String normalizeVideoExportRecordUri(String recordUri,
                                                        Map<String, Set<String>> canonicalUrisByObjectName) {
        if (canonicalRecordObjectName(recordUri).isPresent()) {
            return recordUri;
        }
        Optional<String> minioObjectName = minioRecordObjectName(recordUri);
        if (minioObjectName.isEmpty()) {
            return recordUri;
        }
        Set<String> candidates = canonicalUrisByObjectName.get(minioObjectName.get());
        if (candidates == null || candidates.size() != 1) {
            throw new IllegalStateException(
                    "VIDEO export MinIO record URI is not uniquely bound to a canonical camera record");
        }
        return candidates.iterator().next();
    }

    private static Optional<String> canonicalRecordObjectName(String recordUri) {
        try {
            URI uri = URI.create(recordUri);
            java.util.regex.Matcher matcher = CANONICAL_RECORD_URI_PATH.matcher(uri.getPath());
            if (!matcher.find()) {
                return Optional.empty();
            }
            return normalizedRecordObjectName(matcher.group(1));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> minioRecordObjectName(String recordUri) {
        URI uri;
        try {
            uri = URI.create(recordUri);
        } catch (RuntimeException exception) {
            if (recordUri.contains("/api/v1/buckets/") && recordUri.contains("/objects/download")) {
                throw new IllegalStateException(
                        "VIDEO export MinIO record URI is not uniquely bound to a canonical camera record",
                        exception);
            }
            return Optional.empty();
        }
        if (!MINIO_RECORD_DOWNLOAD_PATH.matcher(uri.getPath()).find()) {
            return Optional.empty();
        }
        Set<String> prefixes = new LinkedHashSet<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null) {
            for (String parameter : rawQuery.split("&")) {
                int separator = parameter.indexOf('=');
                String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
                if (!"prefix".equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) {
                    continue;
                }
                String rawValue = separator < 0 ? "" : parameter.substring(separator + 1);
                normalizedRecordObjectName(URLDecoder.decode(rawValue, StandardCharsets.UTF_8))
                        .ifPresent(prefixes::add);
            }
        }
        if (prefixes.size() != 1) {
            throw new IllegalStateException(
                    "VIDEO export MinIO record URI is not uniquely bound to a canonical camera record");
        }
        return Optional.of(prefixes.iterator().next());
    }

    private static Optional<String> normalizedRecordObjectName(String objectName) {
        if (!hasText(objectName)) {
            return Optional.empty();
        }
        String normalized = objectName.replace('\\', '/').replaceFirst("^/+", "");
        return hasText(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    private static boolean hasCompleteVideoExportProvenance(ReviewEvidenceVideoExportResult result) {
        return result != null
                && "ready".equals(normalizeVideoExportStatus(result.status()))
                && hasText(result.exportId())
                && hasText(result.exportUri())
                && hasText(result.manifestUri())
                && isSha256Hash(result.fileHash())
                && isSha256Hash(result.ffmpegCommandHash())
                && hasCompleteVideoSegmentProvenance(result.recordSegments());
    }

    private static boolean hasCompleteVideoExportProvenance(Map<String, Object> result) {
        return result != null
                && "ready".equals(normalizeVideoExportStatus(toText(result.get("status"))))
                && hasText(toText(result.get("exportId")))
                && hasText(toText(result.get("exportUri")))
                && hasText(toText(result.get("manifestUri")))
                && isSha256Hash(toText(result.get("fileHash")))
                && isSha256Hash(toText(result.get("ffmpegCommandHash")))
                && hasCompleteVideoSegmentProvenance(toMapList(result.get("recordSegments")));
    }

    private static boolean hasCompleteVideoSegmentProvenance(List<Map<String, Object>> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        Set<Integer> stitchOrders = new LinkedHashSet<>();
        for (Map<String, Object> segment : segments) {
            if (!isSha256Hash(toText(segment.get("sourceHash")))
                    || !isSha256Hash(toText(segment.get("ffmpegCommandHash")))) {
                return false;
            }
            if (!(segment.get("clipParameters") instanceof Map<?, ?> clipParameters)
                    || !isFiniteNonNegative(clipParameters.get("offsetSeconds"))
                    || !isFinitePositive(clipParameters.get("durationSeconds"))) {
                return false;
            }
            Integer stitchOrder = exactVideoExportInteger(segment.get("stitchOrder"));
            if (stitchOrder == null || stitchOrder < 0 || !stitchOrders.add(stitchOrder)) {
                return false;
            }
        }
        for (int expected = 0; expected < segments.size(); expected++) {
            if (!stitchOrders.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeVideoExportStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isSha256Hash(String value) {
        return hasText(value) && SHA256_HASH.matcher(value).matches();
    }

    private static boolean sha256HashesEqual(String left, String right) {
        return isSha256Hash(left) && isSha256Hash(right) && left.equalsIgnoreCase(right);
    }

    private static Integer exactVideoExportInteger(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double candidate = number.doubleValue();
        if (!Double.isFinite(candidate) || candidate != Math.rint(candidate)
                || candidate < Integer.MIN_VALUE || candidate > Integer.MAX_VALUE) {
            return null;
        }
        return (int) candidate;
    }

    private static boolean isFiniteNonNegative(Object value) {
        return value instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= 0D;
    }

    private static boolean isFinitePositive(Object value) {
        return value instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() > 0D;
    }

    private static boolean requiresVideoArtifact(String format) {
        if (!hasText(format)) {
            return false;
        }
        return Set.of("mp4", "mkv", "mov", "avi", "webm", "ts")
                .contains(format.trim().toLowerCase(Locale.ROOT));
    }

    private static String normalizeEvidenceExportFormat(String format) {
        String normalized = hasText(format)
                ? format.trim().toLowerCase(Locale.ROOT)
                : "mp4";
        return "video".equals(normalized) ? "mp4" : normalized;
    }

    private static LocalDateTime exportStartTime(ReviewItemAggregate item, ReviewCaseTimelineItem timelineItem) {
        if (item.firstAlertTime() != null) {
            return item.firstAlertTime();
        }
        return timelineItem.happenedAt();
    }

    private static LocalDateTime exportEndTime(ReviewItemAggregate item, ReviewCaseTimelineItem timelineItem) {
        LocalDateTime startTime = exportStartTime(item, timelineItem);
        LocalDateTime endTime = item.lastAlertTime();
        if (endTime == null) {
            endTime = timelineItem.happenedAt();
        }
        if (startTime != null && (endTime == null || !endTime.isAfter(startTime))) {
            return startTime.plusSeconds(60);
        }
        return endTime;
    }

    private static List<String> mergeEvidenceUris(List<String> evidenceUris,
                                                  List<ReviewEvidenceVideoExportResult> videoExports) {
        Set<String> merged = new LinkedHashSet<>(evidenceUris);
        for (ReviewEvidenceVideoExportResult videoExport : videoExports) {
            if (hasText(videoExport.exportUri())) {
                merged.add(videoExport.exportUri());
            }
        }
        return List.copyOf(merged);
    }

    private static Map<String, Object> videoExportToMap(ReviewEvidenceVideoExportResult videoExport) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("exportId", videoExport.exportId());
        values.put("exportUri", videoExport.exportUri());
        values.put("status", videoExport.status());
        values.put("message", videoExport.message());
        values.put("manifestUri", videoExport.manifestUri());
        values.put("fileHash", videoExport.fileHash());
        values.put("recordSegments", videoExport.recordSegments());
        values.put("ffmpegCommandHash", videoExport.ffmpegCommandHash());
        values.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(values);
    }

    private static Map<String, Object> buildProposedRule(ReviewItemAggregate item) {
        Map<String, Object> suggestion = item.ruleSuggestion() == null ? Map.of() : item.ruleSuggestion();
        Map<String, Object> proposedRule = new LinkedHashMap<>();
        proposedRule.put("action", hasText(toText(suggestion.get("action")))
                ? suggestion.get("action")
                : "suppress_label_zone");
        proposedRule.put("sourceSystem", item.sourceSystem());
        proposedRule.put("ruleCode", item.ruleCode());
        proposedRule.put("cameraId", item.cameraId());
        proposedRule.put("zoneCode", item.zoneCode());
        proposedRule.put("objectLabel", item.objectLabel());
        if (hasText(toText(suggestion.get("reason")))) {
            proposedRule.put("reason", suggestion.get("reason"));
        }
        return Map.copyOf(proposedRule);
    }

    private Map<String, Object> applyRuleSuggestion(ReviewItemAggregate item, Map<String, Object> suggestion) {
        Map<String, Object> previousRule = reviewRuleStore.listAll().stream()
                .filter(rule -> sameRuleScope(rule, item))
                .findFirst()
                .map(SupervisionAlertReviewServiceImpl::ruleToMap)
                .orElse(Map.of());
        ReviewRuleView appliedRule = reviewRuleStore.save(buildAppliedRuleCommand(item, suggestion, previousRule));
        Map<String, Object> updated = new LinkedHashMap<>(suggestion);
        if (!previousRule.isEmpty()) {
            updated.put("previousRule", previousRule);
        }
        updated.put("appliedRuleId", appliedRule.id());
        updated.put("appliedRule", ruleToMap(appliedRule));
        updated.put("configVersion", ruleConfigVersion(appliedRule.id(), suggestion, "configVersion"));
        return immutableNonNullMap(updated);
    }

    private Map<String, Object> rollbackRuleSuggestion(ReviewItemAggregate item, Map<String, Object> suggestion) {
        Long appliedRuleId = toLong(suggestion.get("appliedRuleId"));
        if (appliedRuleId == null) {
            return suggestion;
        }
        Map<String, Object> previousRule = toStringObjectMap(suggestion.get("previousRule"));
        ReviewRuleCommand rollbackCommand = previousRule.isEmpty()
                ? buildDisabledAppliedRuleCommand(item, suggestion, appliedRuleId)
                : ruleCommandFromMap(previousRule, appliedRuleId);
        ReviewRuleView rollbackRule = reviewRuleStore.save(rollbackCommand);
        Map<String, Object> updated = new LinkedHashMap<>(suggestion);
        updated.put("rollbackRule", ruleToMap(rollbackRule));
        updated.put("rollbackVersion", ruleConfigVersion(appliedRuleId, suggestion, "configVersion"));
        return immutableNonNullMap(updated);
    }

    private static ReviewRuleCommand buildAppliedRuleCommand(ReviewItemAggregate item,
                                                             Map<String, Object> suggestion,
                                                             Map<String, Object> previousRule) {
        Long ruleId = toLong(suggestion.get("appliedRuleId"));
        if (ruleId == null) {
            ruleId = toLong(previousRule.get("id"));
        }
        return new ReviewRuleCommand(
                ruleId,
                item.ruleCode(),
                firstText(suggestion.get("suggestedRuleName"), "false_positive_" + item.cameraId() + "_" + item.zoneCode()),
                item.sourceSystem(),
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                toInteger(suggestion.get("minStaySeconds")),
                null,
                null,
                true,
                toInteger(firstNonNull(suggestion.get("inertiaFrames"), previousRule.get("inertiaFrames"))),
                toInteger(firstNonNull(suggestion.get("loiteringSeconds"), previousRule.get("loiteringSeconds")))
        );
    }

    private static ReviewRuleCommand buildDisabledAppliedRuleCommand(ReviewItemAggregate item,
                                                                     Map<String, Object> suggestion,
                                                                     Long appliedRuleId) {
        Map<String, Object> appliedRule = toStringObjectMap(suggestion.get("appliedRule"));
        return new ReviewRuleCommand(
                appliedRuleId,
                firstText(appliedRule.get("ruleCode"), item.ruleCode()),
                firstText(appliedRule.get("ruleName"), "false_positive_" + item.cameraId() + "_" + item.zoneCode()),
                firstText(appliedRule.get("sourceSystem"), item.sourceSystem()),
                firstText(appliedRule.get("cameraId"), item.cameraId()),
                firstText(appliedRule.get("zoneCode"), item.zoneCode()),
                firstText(appliedRule.get("objectLabel"), item.objectLabel()),
                toInteger(appliedRule.get("minStaySeconds")),
                toLocalDateTime(appliedRule.get("activeStart")),
                toLocalDateTime(appliedRule.get("activeEnd")),
                false,
                toInteger(appliedRule.get("inertiaFrames")),
                toInteger(appliedRule.get("loiteringSeconds"))
        );
    }

    private static ReviewRuleCommand ruleCommandFromMap(Map<String, Object> rule, Long fallbackRuleId) {
        return new ReviewRuleCommand(
                toLong(rule.get("id")) == null ? fallbackRuleId : toLong(rule.get("id")),
                toText(rule.get("ruleCode")),
                toText(rule.get("ruleName")),
                toText(rule.get("sourceSystem")),
                toText(rule.get("cameraId")),
                toText(rule.get("zoneCode")),
                toText(rule.get("objectLabel")),
                toInteger(rule.get("minStaySeconds")),
                toLocalDateTime(rule.get("activeStart")),
                toLocalDateTime(rule.get("activeEnd")),
                toBoolean(rule.get("enabled")),
                toInteger(rule.get("inertiaFrames")),
                toInteger(rule.get("loiteringSeconds"))
        );
    }

    private static boolean sameRuleScope(ReviewRuleView rule, ReviewItemAggregate item) {
        return Objects.equals(rule.ruleCode(), item.ruleCode())
                && matchesText(rule.sourceSystem(), item.sourceSystem())
                && matchesText(rule.cameraId(), item.cameraId())
                && matchesText(rule.zoneCode(), item.zoneCode())
                && matchesText(rule.objectLabel(), item.objectLabel());
    }

    private static Map<String, Object> ruleToMap(ReviewRuleView rule) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", rule.id());
        values.put("ruleCode", rule.ruleCode());
        values.put("ruleName", rule.ruleName());
        values.put("sourceSystem", rule.sourceSystem());
        values.put("cameraId", rule.cameraId());
        values.put("zoneCode", rule.zoneCode());
        values.put("objectLabel", rule.objectLabel());
        values.put("minStaySeconds", rule.minStaySeconds());
        values.put("inertiaFrames", rule.inertiaFrames());
        values.put("loiteringSeconds", rule.loiteringSeconds());
        values.put("activeStart", rule.activeStart());
        values.put("activeEnd", rule.activeEnd());
        values.put("enabled", rule.enabled());
        values.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(values);
    }

    private static List<String> buildRuleSuggestionDiff(Map<String, Object> currentRule,
                                                        Map<String, Object> proposedRule) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(currentRule.keySet());
        keys.addAll(proposedRule.keySet());
        List<String> diff = new ArrayList<>();
        for (String key : keys) {
            Object currentValue = currentRule.get(key);
            Object proposedValue = proposedRule.get(key);
            if (!Objects.equals(currentValue, proposedValue)) {
                diff.add(key + ": " + currentValue + " -> " + proposedValue);
            }
        }
        return List.copyOf(diff);
    }

    private String resolveRuleCode(AlertClueCommand command) {
        if (hasText(command.ruleCode())) {
            return command.ruleCode();
        }
        return reviewRuleStore.listEnabled().stream()
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

    private static boolean matchesText(String configured, String actual) {
        if (!hasText(configured)) {
            return true;
        }
        return Objects.equals(configured, actual);
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

    private RecordEvidenceAttempt resolveRecordEvidence(String sourceAlertId,
                                                        String deviceId,
                                                        String cameraId,
                                                        LocalDateTime alertTime) {
        LocalDateTime checkedAt = LocalDateTime.now();
        if (!hasText(sourceAlertId) || (!hasText(deviceId) && !hasText(cameraId)) || alertTime == null) {
            return new RecordEvidenceAttempt(Optional.empty(), RECORD_EVIDENCE_FAILED, checkedAt, "missing_lookup_fields");
        }
        try {
            Optional<RecordEvidenceResult> resolved = recordEvidenceResolver.resolve(new RecordEvidenceRequest(
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
                    recordEvidenceResolver.unavailableReason().orElse(RECORD_GAP_RECORD_NOT_FOUND)
            );
        } catch (RuntimeException ex) {
            return new RecordEvidenceAttempt(Optional.empty(), RECORD_EVIDENCE_FAILED, checkedAt, ex.getMessage());
        }
    }

    private ReviewItemAggregate withEventProjection(ReviewItemAggregate item) {
        if (item == null || item.eventId() == null) {
            return item;
        }
        return eventProjectionStore.findByEventId(item.eventId())
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

    private Optional<ReviewItemAggregate> findExistingIngestIdentity(AlertClueCommand command,
                                                                    List<String> identityKeys) {
        if ((identityKeys == null || identityKeys.isEmpty()) && !hasText(command.sourceAlertId())) {
            return Optional.empty();
        }
        return reviewItemStore.findByIngestIdentity(command.sourceSystem(), command.sourceAlertId(), identityKeys);
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
                happenedAtById.merge(sourceAlertId, happenedAt, SupervisionAlertReviewServiceImpl::min);
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
        for (ReviewItemAggregate other : listWorkbench(new ReviewQuery(null, cameraId, null, null))) {
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

    private static String buildReviewSegmentId(AlertClueCommand command) {
        return buildReviewSegmentId(
                normalizeCameraId(command),
                command.alertTime(),
                command.sourceSystem(),
                command.sourceAlertId()
        );
    }

    private static String buildRecoveryReviewSegmentId(ReviewItemAggregate item) {
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

    private static String reviewSegmentSeverity(String sourceAlertType) {
        String type = toText(sourceAlertType).toLowerCase();
        if (type.contains("detect") || type.contains("motion")) {
            return "detection";
        }
        return "alert";
    }

    private static String mergeSeverity(String current, String incoming) {
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

    private static String mergeReviewSegmentStatus(String current, String incoming) {
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

    private static String lifecycleSeverity(String state) {
        if ("alert".equals(state)) {
            return "alert";
        }
        if ("detection".equals(state)) {
            return "detection";
        }
        return null;
    }

    private static Map<String, Object> withReviewWindow(Map<String, Object> reviewData,
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
        suggestion.putAll(buildRuleSuggestionSafetySummary(item));
        suggestion.put("suggestedRuleName", "false_positive_" + item.cameraId() + "_" + item.zoneCode());
        return suggestion;
    }

    private Map<String, Object> buildRuleSuggestionSafetySummary(ReviewItemAggregate item) {
        List<ReviewItemAggregate> scopedItems = listWorkbench(new ReviewQuery(
                null,
                item.cameraId(),
                item.zoneCode(),
                item.objectLabel(),
                null,
                null,
                null,
                null,
                null,
                null
        )).stream()
                .filter(candidate -> Objects.equals(candidate.ruleCode(), item.ruleCode()))
                .filter(candidate -> sameRuleScope(candidate, item))
                .toList();
        int beforeHitCount = Math.max(1, scopedItems.size());
        int existingFalsePositiveCount = (int) scopedItems.stream()
                .filter(candidate -> !Objects.equals(candidate.id(), item.id()))
                .filter(candidate -> STATUS_FALSE_POSITIVE.equals(candidate.reviewStatus()))
                .count();
        int currentSampleCount = existingFalsePositiveCount + 1;
        int afterEstimatedHitCount = 0;
        int possibleMissedCount = Math.max(0, beforeHitCount - currentSampleCount);

        Map<String, Object> impactScope = new LinkedHashMap<>();
        impactScope.put("cameraIds", distinctValues(withScopeValue(scopedItems.stream()
                .map(ReviewItemAggregate::cameraId)
                .toList(), item.cameraId())));
        impactScope.put("zoneCodes", distinctValues(withScopeValue(scopedItems.stream()
                .map(ReviewItemAggregate::zoneCode)
                .toList(), item.zoneCode())));
        impactScope.put("objectLabels", distinctValues(withScopeValue(scopedItems.stream()
                .map(ReviewItemAggregate::objectLabel)
                .toList(), item.objectLabel())));

        Map<String, Object> beforeAfter = new LinkedHashMap<>();
        beforeAfter.put("beforeHitCount", beforeHitCount);
        beforeAfter.put("afterEstimatedHitCount", afterEstimatedHitCount);
        beforeAfter.put("falsePositiveBeforeCount", currentSampleCount);
        beforeAfter.put("falsePositiveAfterCount", 0);
        beforeAfter.put("possibleMissedCount", possibleMissedCount);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("minimumSampleCount", MIN_RULE_SUGGESTION_SAMPLE_COUNT);
        summary.put("currentSampleCount", currentSampleCount);
        summary.put("sampleRequirementMet", currentSampleCount >= MIN_RULE_SUGGESTION_SAMPLE_COUNT);
        summary.put("riskNote", currentSampleCount < MIN_RULE_SUGGESTION_SAMPLE_COUNT
                ? "low_sample_requires_more_review"
                : possibleMissedCount > 0
                ? "possible_recall_loss_requires_replay"
                : "ready_for_shadow_evaluation");
        summary.put("impactScope", immutableNonNullMap(impactScope));
        summary.put("beforeAfterComparison", immutableNonNullMap(beforeAfter));
        return immutableNonNullMap(summary);
    }

    private Map<String, Object> withCurrentRuleSuggestionSafety(ReviewItemAggregate item,
                                                                Map<String, Object> suggestion) {
        Map<String, Object> refreshed = new LinkedHashMap<>(suggestion == null ? Map.of() : suggestion);
        refreshed.putAll(buildRuleSuggestionSafetySummary(item));
        return immutableNonNullMap(refreshed);
    }

    private static void requireRuleSuggestionSampleReady(Map<String, Object> suggestion) {
        if (Boolean.TRUE.equals(suggestion.get("sampleRequirementMet"))) {
            return;
        }
        Integer currentSampleCount = toInteger(suggestion.get("currentSampleCount"));
        Integer minimumSampleCount = toInteger(suggestion.get("minimumSampleCount"));
        throw new IllegalStateException("rule suggestion minimum sample requirement not met: "
                + (currentSampleCount == null ? "-" : currentSampleCount)
                + "/"
                + (minimumSampleCount == null ? "-" : minimumSampleCount));
    }

    private static List<String> withScopeValue(List<String> values, String value) {
        List<String> merged = new ArrayList<>(values == null ? List.of() : values);
        if (hasText(value)) {
            merged.add(value);
        }
        return merged;
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
            ReviewItemAggregate item = withEventProjection(reviewItemStore.findById(reviewItemId).orElse(null));
            if (item == null) {
                continue;
            }
            if (includeRecordCoverage) {
                List<RecordCoverageSegment> coverage = coverageByReviewItemId.computeIfAbsent(
                        reviewItemId,
                        this::getRecordCoverage
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

    private static List<String> ruleCandidateActions(Map<String, Object> suggestion) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, suggestion == null ? null : suggestion.get("candidateActions"));
        if (values.isEmpty()) {
            return DEFAULT_RULE_CANDIDATE_ACTIONS;
        }
        return List.copyOf(values);
    }

    private static double roundRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 100D) / denominator) / 100D;
    }

    private static int boundedPositive(Integer value, int fallback, int max) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }

    private static Map<String, Object> updateRuleSuggestionLifecycle(Map<String, Object> current,
                                                                     String status,
                                                                     String note) {
        Map<String, Object> suggestion = new LinkedHashMap<>(current == null ? Map.of() : current);
        suggestion.put("lifecycleStatus", status);
        if (hasText(note)) {
            suggestion.put("lifecycleNote", note);
        }
        if (RULE_SUGGESTION_ACCEPTED.equals(status) && hasText(note)) {
            suggestion.put("approvalNote", note);
        }
        if (RULE_SUGGESTION_APPLIED.equals(status) && hasText(note)) {
            suggestion.put("applyNote", note);
        }
        suggestion.put("lifecycleUpdatedAt", LocalDateTime.now().toString());
        return Map.copyOf(suggestion);
    }

    private static void requireRuleSuggestionTransition(ReviewItemAggregate item, String nextStatus) {
        String lifecycleStatus = item.ruleSuggestion() == null
                ? null
                : toText(item.ruleSuggestion().get("lifecycleStatus"));
        String currentStatus = hasText(item.ruleSuggestionStatus())
                ? item.ruleSuggestionStatus()
                : lifecycleStatus;
        Set<String> requiredCurrentStatuses = switch (nextStatus) {
            case RULE_SUGGESTION_SHADOW_EVALUATED -> Set.of(RULE_SUGGESTION_PENDING);
            case RULE_SUGGESTION_ACCEPTED -> Set.of(RULE_SUGGESTION_SHADOW_EVALUATED);
            case RULE_SUGGESTION_REJECTED -> Set.of(
                    RULE_SUGGESTION_PENDING,
                    RULE_SUGGESTION_SHADOW_EVALUATED
            );
            case RULE_SUGGESTION_APPLIED -> Set.of(RULE_SUGGESTION_ACCEPTED);
            case RULE_SUGGESTION_REVERTED -> Set.of(RULE_SUGGESTION_APPLIED);
            default -> Set.of();
        };
        if (requiredCurrentStatuses.contains(currentStatus)) {
            return;
        }
        throw new IllegalStateException("rule suggestion transition requires "
                + requiredCurrentStatuses + " -> " + nextStatus + ", current: " + currentStatus);
    }

    private static String ruleConfigVersion(Long ruleId, Map<String, Object> suggestion, String versionKey) {
        int version = toInteger(suggestion.get(versionKey)) == null ? 1 : toInteger(suggestion.get(versionKey)) + 1;
        Object current = suggestion.get(versionKey);
        if (current instanceof String value && value.startsWith("rule-" + ruleId + "-v")) {
            String raw = value.substring(("rule-" + ruleId + "-v").length());
            try {
                version = Integer.parseInt(raw) + 1;
            } catch (NumberFormatException ignored) {
                version = 1;
            }
        }
        return "rule-" + ruleId + "-v" + version;
    }

    private static Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return normalizeStringKeyMap(map);
    }

    private static Map<String, Object> immutableNonNullMap(Map<String, Object> values) {
        values.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        return Map.copyOf(values);
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

    private static String normalizeCoverageStatus(RecordCoverageSegment segment) {
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

    private static int materialOrder(String materialType) {
        if (MATERIAL_SNAPSHOT.equals(materialType)) {
            return 10;
        }
        if (MATERIAL_RECORD.equals(materialType)) {
            return 20;
        }
        if (MATERIAL_RECORD_COVERAGE.equals(materialType)) {
            return 30;
        }
        if (MATERIAL_REVIEW_ACTION.equals(materialType)) {
            return 40;
        }
        return 90;
    }

    private static List<Map<String, Object>> buildReviewObjects(List<String> labels,
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

    private static boolean matchesQuery(ReviewItemAggregate item, ReviewQuery query) {
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

    private static Set<String> objectIds(ReviewItemAggregate item) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, item.reviewData().get("objectIds"));
        return values;
    }

    private static Set<String> zones(ReviewItemAggregate item) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, item.zoneCode());
        collectStringValues(values, item.reviewData().get("zones"));
        return values;
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
        ReviewCameraTopology topology = cameraTopologyResolver.resolveCameraTopology(cameraId);
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

    private static boolean intersects(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return !intersection.isEmpty();
    }

    private static Set<String> intersection(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return Set.of();
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return intersection;
    }

    private static String requireRuleSuggestionStatus(String status) {
        requireText(status, "status");
        if (List.of(RULE_SUGGESTION_PENDING, RULE_SUGGESTION_SHADOW_EVALUATED,
                RULE_SUGGESTION_ACCEPTED, RULE_SUGGESTION_REJECTED,
                RULE_SUGGESTION_APPLIED, RULE_SUGGESTION_REVERTED).contains(status)) {
            return status;
        }
        throw new IllegalArgumentException("unsupported rule suggestion status: " + status);
    }

    private static String mapEventReviewStatus(EventProjection projection) {
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

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
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

    private static List<Map<String, Object>> toMapList(Object value) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    values.add(normalizeStringKeyMap(map));
                }
            }
            return List.copyOf(values);
        }
        if (value instanceof Map<?, ?> map) {
            values.add(normalizeStringKeyMap(map));
        }
        return List.copyOf(values);
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

    private static List<String> toStringList(Object value, String fallback) {
        List<String> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = toText(item);
                if (hasText(text)) {
                    values.add(text);
                }
            }
        } else {
            String text = toText(value);
            if (hasText(text)) {
                values.add(text);
            }
        }
        if (values.isEmpty() && hasText(fallback)) {
            values.add(fallback);
        }
        return List.copyOf(values);
    }

    private static List<Double> toDoubleList(Object value) {
        List<Double> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Double number = toDouble(item);
                if (number != null) {
                    values.add(number);
                }
            }
        } else {
            Double number = toDouble(value);
            if (number != null) {
                values.add(number);
            }
        }
        return List.copyOf(values);
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private static List<String> mergeStringValues(Object current, List<String> incoming) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, current);
        collectStringValues(values, incoming);
        return List.copyOf(values);
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

    private static Map<String, Object> normalizeStringKeyMap(Map<?, ?> value) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(normalized);
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

    private static void collectStringValues(Set<String> values, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item));
                }
            }
            return;
        }
        if (value != null && hasText(String.valueOf(value))) {
            values.add(String.valueOf(value));
        }
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

    private static String firstSourceAlertId(ReviewItemAggregate item) {
        if (item.sourceAlertIds() == null || item.sourceAlertIds().isEmpty()) {
            return null;
        }
        return item.sourceAlertIds().get(0);
    }

    private static String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstText(Object value, String fallback) {
        String text = toText(value);
        return hasText(text) ? text : fallback;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = toText(value);
        return hasText(text) ? Boolean.parseBoolean(text) : null;
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    private static LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    private static String normalizeCameraId(AlertClueCommand command) {
        if (hasText(command.cameraId())) {
            return command.cameraId();
        }
        return command.deviceId();
    }

    private static void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record EvidenceBuildResult(List<ReviewEvidenceItem> evidenceItems,
                                       String recordEvidenceStatus,
                                       LocalDateTime recordEvidenceCheckedAt,
                                       String recordEvidenceMessage) {
    }

    private record RecordEvidenceAttempt(Optional<ReviewEvidenceItem> evidenceItem,
                                         String recordEvidenceStatus,
                                         LocalDateTime recordEvidenceCheckedAt,
                                         String recordEvidenceMessage) {
    }

    private record RuleSuggestionGroupKey(String cameraId,
                                          String zoneCode,
                                          String objectLabel) {
    }

}
