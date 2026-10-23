package com.basiclab.iot.system.service.supervision;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.core.util.DataPermissionUtils;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewEvidenceDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseAuditDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewExportJobDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewIngestIdentityDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewReportAckDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuleDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeLockDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeRunDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSegmentDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSemanticIndexDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewUserStatusDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseAuditMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewEvidenceMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewExportJobMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewIngestIdentityMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewReportAckMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuleMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeLockMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeOutboxMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeRunMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSegmentMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSemanticIndexMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewUserStatusMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEventMapper;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.EventProjection;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.EventProjectionStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseDraft;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseTimelineItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportPackage;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemAggregate;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemDraft;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationsReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgement;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeLockAcquisition;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeOutboxMessage;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSegmentView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewUserStatusView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SupervisionAlertReviewMapperStore implements ReviewItemStore, ReviewRuleStore, EventProjectionStore {

    private static final String REVIEW_ITEM_NO_PREFIX = "RI-";
    private static final String REVIEW_CASE_NO_PREFIX = "RC-";
    private static final String SOURCE_ALERT_ID_SEPARATOR = "\n";
    private static final String CSV_SEPARATOR = ",";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final SupervisionAlertReviewItemMapper reviewItemMapper;
    private final SupervisionAlertReviewEvidenceMapper reviewEvidenceMapper;
    private final SupervisionAlertReviewIngestIdentityMapper reviewIngestIdentityMapper;
    private final SupervisionAlertReviewRuleMapper reviewRuleMapper;
    private final SupervisionAlertReviewCaseMapper reviewCaseMapper;
    private final SupervisionAlertReviewCaseItemMapper reviewCaseItemMapper;
    private final SupervisionAlertReviewCaseAuditMapper reviewCaseAuditMapper;
    private final SupervisionAlertReviewExportJobMapper reviewExportJobMapper;
    private final SupervisionAlertReviewSemanticIndexMapper reviewSemanticIndexMapper;
    private final SupervisionAlertReviewUserStatusMapper reviewUserStatusMapper;
    private final SupervisionAlertReviewRuntimeLockMapper reviewRuntimeLockMapper;
    private final SupervisionAlertReviewRuntimeRunMapper reviewRuntimeRunMapper;
    private final SupervisionAlertReviewRuntimeOutboxMapper reviewRuntimeOutboxMapper;
    private final SupervisionAlertReviewSegmentMapper reviewSegmentMapper;
    private final SupervisionAlertReviewReportAckMapper reviewReportAckMapper;
    private final SupervisionEventMapper supervisionEventMapper;

    public SupervisionAlertReviewMapperStore(SupervisionAlertReviewItemMapper reviewItemMapper,
                                             SupervisionAlertReviewEvidenceMapper reviewEvidenceMapper,
                                             SupervisionAlertReviewIngestIdentityMapper reviewIngestIdentityMapper,
                                             SupervisionAlertReviewRuleMapper reviewRuleMapper,
                                             SupervisionAlertReviewCaseMapper reviewCaseMapper,
                                             SupervisionAlertReviewCaseItemMapper reviewCaseItemMapper,
                                             SupervisionAlertReviewCaseAuditMapper reviewCaseAuditMapper,
                                             SupervisionAlertReviewExportJobMapper reviewExportJobMapper,
                                             SupervisionAlertReviewSemanticIndexMapper reviewSemanticIndexMapper,
                                             SupervisionAlertReviewUserStatusMapper reviewUserStatusMapper,
                                             SupervisionAlertReviewRuntimeLockMapper reviewRuntimeLockMapper,
                                             SupervisionAlertReviewRuntimeRunMapper reviewRuntimeRunMapper,
                                             SupervisionAlertReviewRuntimeOutboxMapper reviewRuntimeOutboxMapper,
                                             SupervisionAlertReviewSegmentMapper reviewSegmentMapper,
                                             SupervisionAlertReviewReportAckMapper reviewReportAckMapper,
                                             SupervisionEventMapper supervisionEventMapper) {
        this.reviewItemMapper = Objects.requireNonNull(reviewItemMapper, "reviewItemMapper");
        this.reviewEvidenceMapper = Objects.requireNonNull(reviewEvidenceMapper, "reviewEvidenceMapper");
        this.reviewIngestIdentityMapper = Objects.requireNonNull(reviewIngestIdentityMapper, "reviewIngestIdentityMapper");
        this.reviewRuleMapper = Objects.requireNonNull(reviewRuleMapper, "reviewRuleMapper");
        this.reviewCaseMapper = Objects.requireNonNull(reviewCaseMapper, "reviewCaseMapper");
        this.reviewCaseItemMapper = Objects.requireNonNull(reviewCaseItemMapper, "reviewCaseItemMapper");
        this.reviewCaseAuditMapper = Objects.requireNonNull(reviewCaseAuditMapper, "reviewCaseAuditMapper");
        this.reviewExportJobMapper = Objects.requireNonNull(reviewExportJobMapper, "reviewExportJobMapper");
        this.reviewSemanticIndexMapper = Objects.requireNonNull(reviewSemanticIndexMapper, "reviewSemanticIndexMapper");
        this.reviewUserStatusMapper = Objects.requireNonNull(reviewUserStatusMapper, "reviewUserStatusMapper");
        this.reviewRuntimeLockMapper = Objects.requireNonNull(reviewRuntimeLockMapper, "reviewRuntimeLockMapper");
        this.reviewRuntimeRunMapper = Objects.requireNonNull(reviewRuntimeRunMapper, "reviewRuntimeRunMapper");
        this.reviewRuntimeOutboxMapper = Objects.requireNonNull(reviewRuntimeOutboxMapper, "reviewRuntimeOutboxMapper");
        this.reviewSegmentMapper = Objects.requireNonNull(reviewSegmentMapper, "reviewSegmentMapper");
        this.reviewReportAckMapper = Objects.requireNonNull(reviewReportAckMapper, "reviewReportAckMapper");
        this.supervisionEventMapper = Objects.requireNonNull(supervisionEventMapper, "supervisionEventMapper");
    }

    @Override
    public void acquireReviewSegmentTransactionLocks(String cameraId,
                                                     String sourceSystem,
                                                     List<String> identityKeys) {
        Long tenantId = reviewSegmentTenantId(TenantContextHolder.getTenantId());
        reviewSegmentMapper.acquireTransactionLock(tenantId, "review-segment-camera", cameraId);
        if (identityKeys == null || identityKeys.isEmpty()) {
            return;
        }
        identityKeys.stream()
                .filter(SupervisionAlertReviewMapperStore::hasText)
                .distinct()
                .sorted()
                .forEach(identityKey -> reviewSegmentMapper.acquireTransactionLock(
                        tenantId,
                        "review-ingest-identity",
                        toText(sourceSystem, "unknown-source") + ":" + identityKey
                ));
    }

    @Override
    public Optional<ReviewItemAggregate> findLatestOpenReviewSegment(String cameraId) {
        SupervisionAlertReviewSegmentDO segmentDO = reviewSegmentMapper.selectLatestOpen(
                reviewSegmentTenantId(TenantContextHolder.getTenantId()),
                cameraId
        );
        if (segmentDO == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(reviewItemMapper.selectById(segmentDO.getReviewItemId()))
                .map(this::toAggregate);
    }

    @Override
    public Optional<ReviewSegmentView> findPersistedReviewSegment(Long reviewItemId) {
        SupervisionAlertReviewSegmentDO segmentDO = reviewSegmentMapper.selectByReviewItemId(reviewItemId);
        if (segmentDO == null) {
            return Optional.empty();
        }
        Map<String, Object> metadata = readJson(segmentDO.getSegmentMetadata());
        LocalDateTime viewEndTime = segmentDO.getEndTime() == null
                ? toLocalDateTime(metadata.get("endTime"), null)
                : segmentDO.getEndTime();
        return Optional.of(new ReviewSegmentView(
                segmentDO.getReviewItemId(),
                segmentDO.getSegmentNo(),
                segmentDO.getCameraId(),
                segmentDO.getSeverity(),
                segmentDO.getSegmentStatus(),
                segmentDO.getStartTime(),
                viewEndTime,
                splitCsv(segmentDO.getObjectIds()),
                splitCsv(segmentDO.getZoneCodes()),
                splitCsv(segmentDO.getSourceAlertIds()),
                readJsonList(segmentDO.getSegmentEvents()),
                metadata
        ));
    }

    @Override
    public Optional<ReviewItemAggregate> findMergeCandidate(String sourceSystem,
                                                            String cameraId,
                                                            String zoneCode,
                                                            String ruleCode,
                                                            LocalDateTime windowStart,
                                                            LocalDateTime windowEnd) {
        return Optional.ofNullable(reviewItemMapper.selectMergeCandidate(
                        TenantContextHolder.getTenantId(),
                        sourceSystem,
                        cameraId,
                        zoneCode,
                        ruleCode,
                        windowStart,
                        windowEnd
                ))
                .map(this::toAggregate);
    }

    @Override
    public Optional<ReviewItemAggregate> findByIngestIdentity(String sourceSystem,
                                                             String sourceAlertId,
                                                             List<String> identityKeys) {
        Long tenantId = reviewIdentityTenantId(TenantContextHolder.getTenantId());
        for (String identityKey : ingestIdentityLookupKeys(sourceSystem, sourceAlertId, identityKeys, null)) {
            SupervisionAlertReviewIngestIdentityDO identityDO =
                    reviewIngestIdentityMapper.selectByIdentity(tenantId, sourceSystem, identityKey);
            if (identityDO != null) {
                return findById(identityDO.getReviewItemId());
            }
        }
        return Optional.empty();
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public ReviewItemAggregate create(ReviewItemDraft draft, List<ReviewEvidenceItem> evidenceItems) {
        Objects.requireNonNull(draft, "draft");
        SupervisionAlertReviewItemDO itemDO = new SupervisionAlertReviewItemDO()
                .setTenantId(TenantContextHolder.getTenantId())
                .setReviewItemNo(newReviewItemNo())
                .setSourceSystem(draft.sourceSystem())
                .setRuleCode(draft.ruleCode())
                .setSourceAlertType(draft.sourceAlertType())
                .setDeviceId(draft.deviceId())
                .setCameraId(draft.cameraId())
                .setZoneCode(draft.zoneCode())
                .setObjectLabel(draft.objectLabel())
                .setFirstAlertTime(draft.alertTime())
                .setLastAlertTime(draft.alertTime())
                .setAlertCount(1)
                .setSourceAlertIds(joinSourceAlertIds(List.of(draft.sourceAlertId())))
                .setReviewData(writeJson(draft.reviewData()))
                .setReviewStatus(SupervisionAlertReviewService.STATUS_PENDING_REVIEW)
                .setRecordEvidenceStatus(draft.recordEvidenceStatus())
                .setRecordEvidenceCheckedAt(draft.recordEvidenceCheckedAt())
                .setRecordEvidenceMessage(draft.recordEvidenceMessage())
                .setVersion(0);
        reviewItemMapper.insert(itemDO);
        insertEvidence(itemDO.getId(), evidenceItems);
        insertIngestIdentities(itemDO, draft.sourceAlertId(), draft.sourcePayloadHash(), draft.reviewData());
        upsertReviewSegment(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    @Transactional(propagation = Propagation.NESTED, rollbackFor = Exception.class)
    public ReviewItemAggregate appendClue(Long reviewItemId,
                                          String sourceAlertId,
                                          LocalDateTime alertTime,
                                          List<ReviewEvidenceItem> evidenceItems,
                                          Map<String, Object> reviewData,
                                          String recordEvidenceStatus,
                                          LocalDateTime recordEvidenceCheckedAt,
                                          String recordEvidenceMessage) {
        SupervisionAlertReviewItemDO itemDO = requireItemForUpdate(reviewItemId);
        List<String> sourceAlertIds = new ArrayList<>(splitSourceAlertIds(itemDO.getSourceAlertIds()));
        if (!sourceAlertIds.contains(sourceAlertId)) {
            sourceAlertIds.add(sourceAlertId);
        }
        List<String> orderedSourceAlertIds = toStringList(toStringObjectMap(reviewData == null ? null : reviewData.get("reviewSegment")).get("sourceAlertIds"));
        if (!orderedSourceAlertIds.isEmpty()) {
            sourceAlertIds = orderedSourceAlertIds;
        }
        itemDO.setFirstAlertTime(min(itemDO.getFirstAlertTime(), alertTime))
                .setLastAlertTime(max(itemDO.getLastAlertTime(), alertTime))
                .setAlertCount(sourceAlertIds.size())
                .setSourceAlertIds(joinSourceAlertIds(sourceAlertIds))
                .setReviewData(writeJson(reviewData))
                .setRecordEvidenceStatus(recordEvidenceStatus)
                .setRecordEvidenceCheckedAt(recordEvidenceCheckedAt)
                .setRecordEvidenceMessage(recordEvidenceMessage);
        reviewItemMapper.updateById(itemDO);
        insertEvidence(reviewItemId, evidenceItems);
        insertIngestIdentities(itemDO, sourceAlertId, toText(reviewData == null ? null : reviewData.get("sourcePayloadHash"), null), reviewData);
        upsertReviewSegment(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewItemAggregate appendEvidence(Long reviewItemId, List<ReviewEvidenceItem> evidenceItems) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        insertEvidence(reviewItemId, evidenceItems);
        return toAggregate(itemDO);
    }

    @Override
    public ReviewItemAggregate updateRecordEvidenceStatus(Long reviewItemId,
                                                          String recordEvidenceStatus,
                                                          LocalDateTime recordEvidenceCheckedAt,
                                                          String recordEvidenceMessage) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        itemDO.setRecordEvidenceStatus(recordEvidenceStatus)
                .setRecordEvidenceCheckedAt(recordEvidenceCheckedAt)
                .setRecordEvidenceMessage(recordEvidenceMessage);
        reviewItemMapper.updateById(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewItemAggregate updateReviewLifecycle(Long reviewItemId,
                                                     Map<String, Object> reviewData,
                                                     LocalDateTime firstAlertTime,
                                                     LocalDateTime lastAlertTime,
                                                     List<ReviewEvidenceItem> evidenceItems,
                                                     String recordEvidenceStatus,
                                                     LocalDateTime recordEvidenceCheckedAt,
                                                     String recordEvidenceMessage) {
        SupervisionAlertReviewItemDO itemDO = requireItemForUpdate(reviewItemId);
        itemDO.setReviewData(writeJson(reviewData))
                .setFirstAlertTime(firstAlertTime)
                .setLastAlertTime(lastAlertTime)
                .setRecordEvidenceStatus(recordEvidenceStatus)
                .setRecordEvidenceCheckedAt(recordEvidenceCheckedAt)
                .setRecordEvidenceMessage(recordEvidenceMessage);
        reviewItemMapper.updateById(itemDO);
        insertEvidence(reviewItemId, evidenceItems);
        upsertReviewSegment(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    public Optional<ReviewItemAggregate> findById(Long reviewItemId) {
        return Optional.ofNullable(reviewItemMapper.selectById(reviewItemId)).map(this::toAggregate);
    }

    @Override
    public List<ReviewItemAggregate> listWorkbench(ReviewQuery query) {
        String reviewStatus = query == null ? null : query.reviewStatus();
        String cameraId = query == null ? null : query.cameraId();
        LocalDateTime beginTime = query == null ? null : query.beginTime();
        LocalDateTime endTime = query == null ? null : query.endTime();
        return reviewItemMapper.selectWorkbench(TenantContextHolder.getTenantId(), reviewStatus, cameraId, beginTime, endTime)
                .stream()
                .map(this::toAggregate)
                .toList();
    }

    @Override
    public List<ReviewEvidenceItem> listTimeline(Long reviewItemId) {
        return reviewEvidenceMapper.selectByReviewItemId(reviewItemId)
                .stream()
                .map(this::toEvidenceItem)
                .toList();
    }

    @Override
    public Optional<ReviewReportAcknowledgement> findReportAcknowledgement(String reportKey) {
        if (!hasText(reportKey)) {
            return Optional.empty();
        }
        return Optional.ofNullable(reviewReportAckMapper.selectByTenantAndReportKey(
                        TenantContextHolder.getRequiredTenantId(),
                        reportKey
                ))
                .map(SupervisionAlertReviewMapperStore::toReportAcknowledgement);
    }

    @Override
    public ReviewReportAcknowledgement saveReportAcknowledgement(ReviewReportAcknowledgement acknowledgement) {
        Objects.requireNonNull(acknowledgement, "acknowledgement");
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, Object> metadata = acknowledgement.metadata();
        int inserted = reviewReportAckMapper.insertIfAbsent(tenantId, new SupervisionAlertReviewReportAckDO()
                .setTenantId(tenantId)
                .setReportKey(acknowledgement.reportKey())
                .setReportType(acknowledgement.reportType())
                .setPeriodStart(toLocalDateTime(metadata.get("periodStart"), null))
                .setPeriodEnd(toLocalDateTime(metadata.get("periodEnd"), null))
                .setReviewItemIds(joinCsv(toStringList(metadata.get("reviewItemIds"))))
                .setAcknowledgementStatus(acknowledgement.status())
                .setAcknowledgedBy(acknowledgement.acknowledgedBy())
                .setAcknowledgedAt(acknowledgement.acknowledgedAt())
                .setAcknowledgementNote(acknowledgement.note())
                .setMetadata(writeJson(metadata))
                .setVersion(0));
        SupervisionAlertReviewReportAckDO persisted = reviewReportAckMapper.selectByTenantAndReportKey(
                tenantId, acknowledgement.reportKey());
        if (persisted == null) {
            throw new IllegalStateException(
                    "report acknowledgement insert completed without a readable row: "
                            + acknowledgement.reportKey());
        }
        ReviewReportAcknowledgement result = toReportAcknowledgement(persisted);
        if (inserted > 0) {
            return result;
        }
        return new ReviewReportAcknowledgement(
                result.reportKey(),
                result.reportType(),
                result.status(),
                result.acknowledgedBy(),
                result.acknowledgedAt(),
                result.note(),
                true,
                result.metadata()
        );
    }

    @Override
    public ReviewItemAggregate updateReviewStatus(Long reviewItemId,
                                                  String reviewStatus,
                                                  Long reviewerUserId,
                                                  String ignoreReason,
                                                  LocalDateTime reviewedAt) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        String expectedStatus = itemDO.getReviewStatus();
        Integer expectedVersion = itemDO.getVersion();
        Integer nextVersion = nextVersion(expectedVersion);
        itemDO.setReviewStatus(reviewStatus)
                .setReviewerUserId(reviewerUserId)
                .setReviewedAt(reviewedAt)
                .setIgnoreReason(ignoreReason)
                .setVersion(nextVersion);
        SupervisionAlertReviewItemDO updateDO = new SupervisionAlertReviewItemDO()
                .setReviewStatus(reviewStatus)
                .setReviewerUserId(reviewerUserId)
                .setReviewedAt(reviewedAt)
                .setIgnoreReason(ignoreReason)
                .setVersion(nextVersion);
        int updated = reviewItemMapper.updateReviewStatusIfCurrent(
                reviewItemId,
                expectedStatus,
                expectedVersion,
                updateDO
        );
        if (updated == 0) {
            return resolveConcurrentReviewStatusMiss(reviewItemId, reviewStatus);
        }
        return toAggregate(itemDO);
    }

    @Override
    public ReviewUserStatusView upsertUserReviewStatus(Long reviewItemId,
                                                       Long userId,
                                                       boolean hasBeenReviewed,
                                                       LocalDateTime reviewedAt) {
        requireItem(reviewItemId);
        SupervisionAlertReviewUserStatusDO statusDO =
                reviewUserStatusMapper.selectByReviewItemAndUser(reviewItemId, userId);
        if (statusDO == null) {
            statusDO = new SupervisionAlertReviewUserStatusDO()
                    .setReviewItemId(reviewItemId)
                    .setUserId(userId)
                    .setHasBeenReviewed(hasBeenReviewed)
                    .setReviewedAt(reviewedAt)
                    .setVersion(0);
            reviewUserStatusMapper.insert(statusDO);
            return toUserStatusView(statusDO);
        }
        statusDO.setHasBeenReviewed(hasBeenReviewed)
                .setReviewedAt(reviewedAt);
        reviewUserStatusMapper.updateById(statusDO);
        return toUserStatusView(statusDO);
    }

    @Override
    public Optional<ReviewUserStatusView> findUserReviewStatus(Long reviewItemId, Long userId) {
        if (reviewItemId == null || userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(reviewUserStatusMapper.selectByReviewItemAndUser(reviewItemId, userId))
                .map(this::toUserStatusView);
    }

    @Override
    public long countReviewedByUser(List<Long> reviewItemIds, Long userId) {
        if (reviewItemIds == null || reviewItemIds.isEmpty() || userId == null) {
            return 0L;
        }
        Long count = reviewUserStatusMapper.selectReviewedCountByUser(reviewItemIds, userId);
        return count == null ? 0L : count;
    }

    @Override
    public ReviewItemAggregate updateFalsePositive(Long reviewItemId,
                                                   Long reviewerUserId,
                                                   String reason,
                                                   Map<String, Object> ruleSuggestion,
                                                   LocalDateTime reviewedAt) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        String expectedStatus = itemDO.getReviewStatus();
        Integer expectedVersion = itemDO.getVersion();
        Integer nextVersion = nextVersion(expectedVersion);
        itemDO.setReviewStatus(SupervisionAlertReviewService.STATUS_FALSE_POSITIVE)
                .setReviewerUserId(reviewerUserId)
                .setReviewedAt(reviewedAt)
                .setIgnoreReason(reason)
                .setRuleSuggestion(writeJson(ruleSuggestion))
                .setRuleSuggestionStatus(SupervisionAlertReviewService.RULE_SUGGESTION_PENDING)
                .setRuleSuggestionUpdatedAt(reviewedAt)
                .setVersion(nextVersion);
        SupervisionAlertReviewItemDO updateDO = new SupervisionAlertReviewItemDO()
                .setReviewStatus(SupervisionAlertReviewService.STATUS_FALSE_POSITIVE)
                .setReviewerUserId(reviewerUserId)
                .setReviewedAt(reviewedAt)
                .setIgnoreReason(reason)
                .setRuleSuggestion(writeJson(ruleSuggestion))
                .setRuleSuggestionStatus(SupervisionAlertReviewService.RULE_SUGGESTION_PENDING)
                .setRuleSuggestionUpdatedAt(reviewedAt)
                .setVersion(nextVersion);
        int updated = reviewItemMapper.updateReviewStatusIfCurrent(
                reviewItemId,
                expectedStatus,
                expectedVersion,
                updateDO
        );
        if (updated == 0) {
            return resolveConcurrentReviewStatusMiss(reviewItemId, SupervisionAlertReviewService.STATUS_FALSE_POSITIVE);
        }
        return toAggregate(itemDO);
    }

    @Override
    public ReviewItemAggregate updateRuleSuggestionStatus(Long reviewItemId,
                                                          Long reviewerUserId,
                                                          String status,
                                                          Map<String, Object> ruleSuggestion,
                                                          LocalDateTime updatedAt) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        itemDO.setReviewerUserId(reviewerUserId)
                .setRuleSuggestion(writeJson(ruleSuggestion))
                .setRuleSuggestionStatus(status)
                .setRuleSuggestionUpdatedAt(updatedAt);
        reviewItemMapper.updateById(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    public ReviewItemAggregate markConverted(Long reviewItemId,
                                             Long reviewerUserId,
                                             Long eventId,
                                             LocalDateTime convertedAt) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        itemDO.setReviewStatus(SupervisionAlertReviewService.STATUS_CONVERTED)
                .setReviewerUserId(reviewerUserId)
                .setReviewedAt(convertedAt)
                .setEventId(eventId)
                .setConvertedAt(convertedAt);
        reviewItemMapper.updateById(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    public ReviewItemAggregate updateEventProjection(Long reviewItemId,
                                                     Map<String, Object> reviewData,
                                                     EventProjection projection,
                                                     String eventReviewStatus,
                                                     LocalDateTime reconciledAt) {
        SupervisionAlertReviewItemDO itemDO = requireItem(reviewItemId);
        itemDO.setReviewData(writeJson(reviewData));
        reviewItemMapper.updateById(itemDO);
        return toAggregate(itemDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseView createCase(ReviewCaseDraft draft, List<Long> reviewItemIds) {
        Objects.requireNonNull(draft, "draft");
        List<SupervisionAlertReviewItemDO> items = requireItems(reviewItemIds);
        SupervisionAlertReviewCaseDO caseDO = new SupervisionAlertReviewCaseDO()
                .setCaseNo(newReviewCaseNo())
                .setTitle(hasText(draft.title()) ? draft.title() : "review-case")
                .setStatus(SupervisionAlertReviewService.REVIEW_CASE_OPEN)
                .setPrimaryReviewItemId(draft.primaryReviewItemId())
                .setOwnerUserId(draft.ownerUserId())
                .setNotes(draft.notes())
                .setVersion(0);
        fillCaseSummary(caseDO, items);
        reviewCaseMapper.insert(caseDO);
        insertCaseItems(caseDO.getId(), reviewItemIds);
        insertCaseAudit(caseDO.getId(), draft.primaryReviewItemId(), "create_case", draft.notes(), draft.ownerUserId());
        return toCaseView(caseDO);
    }

    @Override
    public Optional<ReviewCaseView> findCaseByReviewItemId(Long reviewItemId) {
        return reviewCaseItemMapper.selectByReviewItemId(reviewItemId).stream()
                .findFirst()
                .map(caseItem -> toCaseView(requireCase(caseItem.getReviewCaseId())));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseView addCaseItem(Long reviewCaseId, Long reviewItemId) {
        SupervisionAlertReviewCaseDO caseDO = requireCaseForUpdate(reviewCaseId);
        ensureCaseOpen(caseDO);
        requireItem(reviewItemId);
        if (reviewCaseItemMapper.selectExisting(reviewCaseId, reviewItemId) != null) {
            return toCaseView(caseDO);
        }
        int nextSortOrder = reviewCaseItemMapper.selectByCaseId(reviewCaseId).size() + 1;
        reviewCaseItemMapper.insert(new SupervisionAlertReviewCaseItemDO()
                .setReviewCaseId(reviewCaseId)
                .setReviewItemId(reviewItemId)
                .setSortOrder(nextSortOrder)
                .setAddedAt(LocalDateTime.now())
                .setVersion(0));
        insertCaseAudit(reviewCaseId, reviewItemId, "add_item", null, null);
        List<Long> reviewItemIds = reviewCaseItemMapper.selectByCaseId(reviewCaseId)
                .stream()
                .map(SupervisionAlertReviewCaseItemDO::getReviewItemId)
                .toList();
        fillCaseSummary(caseDO, requireItems(reviewItemIds));
        updateCaseIfCurrent(caseDO, caseDO.getVersion());
        return toCaseView(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseView updateCaseOwner(Long reviewCaseId,
                                          Long ownerUserId,
                                          String notes,
                                          Long operatorUserId) {
        return updateCaseOwner(reviewCaseId, ownerUserId, notes, operatorUserId, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseView updateCaseOwner(Long reviewCaseId,
                                          Long ownerUserId,
                                          String notes,
                                          Long operatorUserId,
                                          Integer expectedVersion,
                                          String operationId) {
        String normalizedOperationId = normalizeCaseOperationId(
                operationId, "assign_owner", reviewCaseId, ownerUserId, notes);
        SupervisionAlertReviewCaseDO caseDO = requireCaseForUpdate(reviewCaseId);
        if (hasCaseOperationAudit(reviewCaseId, "assign_owner", normalizedOperationId)) {
            return toCaseView(caseDO);
        }
        ensureCaseOpen(caseDO);
        assertExpectedCaseVersion(caseDO, expectedVersion);
        Integer previousVersion = caseDO.getVersion();
        caseDO.setOwnerUserId(ownerUserId);
        if (hasText(notes)) {
            caseDO.setNotes(notes);
        }
        updateCaseIfCurrent(caseDO, previousVersion);
        Map<String, Object> ownerMetadata = new LinkedHashMap<>();
        ownerMetadata.put("ownerUserId", ownerUserId);
        insertCaseAudit(
                reviewCaseId,
                null,
                "assign_owner",
                caseOwnerAuditNote(ownerUserId, notes),
                operatorUserId,
                LocalDateTime.now(),
                caseOperationMetadata(normalizedOperationId, expectedVersion, previousVersion,
                        caseDO.getVersion(), ownerMetadata)
        );
        return toCaseView(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseView closeCase(Long reviewCaseId,
                                    String notes,
                                    Long operatorUserId,
                                    LocalDateTime closedAt) {
        return closeCase(reviewCaseId, notes, operatorUserId, closedAt, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseView closeCase(Long reviewCaseId,
                                    String notes,
                                    Long operatorUserId,
                                    LocalDateTime closedAt,
                                    Integer expectedVersion,
                                    String operationId) {
        String normalizedOperationId = normalizeCaseOperationId(
                operationId, "close_case", reviewCaseId, notes);
        SupervisionAlertReviewCaseDO caseDO = requireCaseForUpdate(reviewCaseId);
        if (hasCaseOperationAudit(reviewCaseId, "close_case", normalizedOperationId)
                || SupervisionAlertReviewService.REVIEW_CASE_CLOSED.equals(caseDO.getStatus())) {
            return toCaseView(caseDO);
        }
        ensureCaseOpen(caseDO);
        assertExpectedCaseVersion(caseDO, expectedVersion);
        Integer previousVersion = caseDO.getVersion();
        caseDO.setStatus(SupervisionAlertReviewService.REVIEW_CASE_CLOSED);
        if (hasText(notes)) {
            caseDO.setNotes(notes);
        }
        updateCaseIfCurrent(caseDO, previousVersion);
        insertCaseAudit(
                reviewCaseId,
                null,
                "close_case",
                caseNotesAuditNote(notes),
                operatorUserId,
                closedAt,
                caseOperationMetadata(normalizedOperationId, expectedVersion, previousVersion,
                        caseDO.getVersion(), Map.of("status", caseDO.getStatus()))
        );
        return toCaseView(caseDO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseMergeResult mergeCases(Long targetReviewCaseId,
                                            Long sourceReviewCaseId,
                                            Long operatorUserId,
                                            String notes) {
        return mergeCases(targetReviewCaseId, sourceReviewCaseId, operatorUserId, notes,
                null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseMergeResult mergeCases(Long targetReviewCaseId,
                                            Long sourceReviewCaseId,
                                            Long operatorUserId,
                                            String notes,
                                            Integer targetExpectedVersion,
                                            Integer sourceExpectedVersion,
                                            String operationId) {
        if (Objects.equals(targetReviewCaseId, sourceReviewCaseId)) {
            throw new IllegalArgumentException("sourceReviewCaseId must differ from targetReviewCaseId");
        }
        String normalizedOperationId = normalizeCaseOperationId(
                operationId, "merge_case", targetReviewCaseId, sourceReviewCaseId, notes);
        Map<Long, SupervisionAlertReviewCaseDO> lockedCases = lockCases(targetReviewCaseId, sourceReviewCaseId);
        SupervisionAlertReviewCaseDO targetCaseDO = lockedCases.get(targetReviewCaseId);
        SupervisionAlertReviewCaseDO sourceCaseDO = lockedCases.get(sourceReviewCaseId);
        if (hasCaseOperationAudit(targetReviewCaseId, "merge_case", normalizedOperationId)
                && hasCaseOperationAudit(sourceReviewCaseId, "merge_case", normalizedOperationId)) {
            return new ReviewCaseMergeResult(toCaseView(targetCaseDO), toCaseView(sourceCaseDO));
        }
        ensureCaseOpen(targetCaseDO);
        ensureCaseOpen(sourceCaseDO);
        assertExpectedCaseVersion(targetCaseDO, targetExpectedVersion);
        assertExpectedCaseVersion(sourceCaseDO, sourceExpectedVersion);
        Integer targetPreviousVersion = targetCaseDO.getVersion();
        Integer sourcePreviousVersion = sourceCaseDO.getVersion();
        List<SupervisionAlertReviewCaseItemDO> targetCaseItems = reviewCaseItemMapper.selectByCaseId(targetReviewCaseId);
        List<SupervisionAlertReviewCaseItemDO> sourceCaseItems = reviewCaseItemMapper.selectByCaseId(sourceReviewCaseId);
        if (sourceCaseItems.isEmpty()) {
            throw new IllegalStateException("source review case has no clues: " + sourceReviewCaseId);
        }
        LinkedHashSet<Long> targetReviewItemIds = new LinkedHashSet<>();
        for (SupervisionAlertReviewCaseItemDO targetCaseItem : targetCaseItems) {
            targetReviewItemIds.add(targetCaseItem.getReviewItemId());
        }
        int nextSortOrder = targetCaseItems.size() + 1;
        for (SupervisionAlertReviewCaseItemDO sourceCaseItem : sourceCaseItems) {
            Long reviewItemId = sourceCaseItem.getReviewItemId();
            if (targetReviewItemIds.add(reviewItemId)) {
                reviewCaseItemMapper.insert(new SupervisionAlertReviewCaseItemDO()
                        .setReviewCaseId(targetReviewCaseId)
                        .setReviewItemId(reviewItemId)
                        .setSortOrder(nextSortOrder++)
                        .setAddedAt(LocalDateTime.now())
                        .setVersion(0));
            }
        }
        for (SupervisionAlertReviewCaseItemDO sourceCaseItem : sourceCaseItems) {
            reviewCaseItemMapper.deleteById(sourceCaseItem.getId());
        }
        fillCaseSummary(targetCaseDO, requireItems(List.copyOf(targetReviewItemIds)));
        sourceCaseDO.setStatus(SupervisionAlertReviewService.REVIEW_CASE_MERGED)
                .setCameraIds(null)
                .setStartTime(null)
                .setEndTime(null);
        if (hasText(notes)) {
            sourceCaseDO.setNotes(notes);
        }
        updateCaseIfCurrent(targetCaseDO, targetPreviousVersion);
        updateCaseIfCurrent(sourceCaseDO, sourcePreviousVersion);
        Map<String, Object> targetMetadata = new LinkedHashMap<>();
        targetMetadata.put("role", "target");
        targetMetadata.put("sourceReviewCaseId", sourceReviewCaseId);
        targetMetadata.put("sourceVersionBefore", sourcePreviousVersion);
        targetMetadata.put("sourceVersionAfter", sourceCaseDO.getVersion());
        insertCaseAudit(
                targetReviewCaseId,
                null,
                "merge_case",
                caseRelatedAuditNote("sourceReviewCaseId", sourceReviewCaseId, null, notes),
                operatorUserId,
                LocalDateTime.now(),
                caseOperationMetadata(normalizedOperationId, targetExpectedVersion, targetPreviousVersion,
                        targetCaseDO.getVersion(), targetMetadata)
        );
        Map<String, Object> sourceMetadata = new LinkedHashMap<>();
        sourceMetadata.put("role", "source");
        sourceMetadata.put("targetReviewCaseId", targetReviewCaseId);
        sourceMetadata.put("targetVersionBefore", targetPreviousVersion);
        sourceMetadata.put("targetVersionAfter", targetCaseDO.getVersion());
        insertCaseAudit(
                sourceReviewCaseId,
                null,
                "merge_case",
                caseRelatedAuditNote("targetReviewCaseId", targetReviewCaseId, null, notes),
                operatorUserId,
                LocalDateTime.now(),
                caseOperationMetadata(normalizedOperationId, sourceExpectedVersion, sourcePreviousVersion,
                        sourceCaseDO.getVersion(), sourceMetadata)
        );
        return new ReviewCaseMergeResult(toCaseView(targetCaseDO), toCaseView(sourceCaseDO));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseSplitResult splitCase(Long sourceReviewCaseId,
                                           ReviewCaseDraft draft,
                                           List<Long> reviewItemIds,
                                           Long operatorUserId) {
        return splitCase(sourceReviewCaseId, draft, reviewItemIds, operatorUserId, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewCaseSplitResult splitCase(Long sourceReviewCaseId,
                                           ReviewCaseDraft draft,
                                           List<Long> reviewItemIds,
                                           Long operatorUserId,
                                           Integer sourceExpectedVersion,
                                           String operationId) {
        Objects.requireNonNull(draft, "draft");
        List<Long> normalizedReviewItemIds = reviewItemIds == null
                ? List.of()
                : reviewItemIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        String normalizedOperationId = normalizeCaseOperationId(
                operationId, "split_case", sourceReviewCaseId, normalizedReviewItemIds,
                draft.title(), draft.ownerUserId(), draft.notes());
        SupervisionAlertReviewCaseDO sourceCaseDO = requireCaseForUpdate(sourceReviewCaseId);
        Optional<Long> duplicateNewCaseId = splitResultCaseId(sourceReviewCaseId, normalizedOperationId);
        if (duplicateNewCaseId.isPresent()) {
            return new ReviewCaseSplitResult(toCaseView(sourceCaseDO), toCaseView(requireCase(duplicateNewCaseId.get())));
        }
        ensureCaseOpen(sourceCaseDO);
        assertExpectedCaseVersion(sourceCaseDO, sourceExpectedVersion);
        Integer sourcePreviousVersion = sourceCaseDO.getVersion();
        List<SupervisionAlertReviewCaseItemDO> sourceCaseItems = reviewCaseItemMapper.selectByCaseId(sourceReviewCaseId);
        LinkedHashSet<Long> sourceReviewItemIds = new LinkedHashSet<>();
        for (SupervisionAlertReviewCaseItemDO sourceCaseItem : sourceCaseItems) {
            sourceReviewItemIds.add(sourceCaseItem.getReviewItemId());
        }
        LinkedHashSet<Long> splitReviewItemIds = new LinkedHashSet<>(normalizedReviewItemIds);
        if (splitReviewItemIds.isEmpty()) {
            throw new IllegalArgumentException("reviewItemIds must not be empty");
        }
        if (!sourceReviewItemIds.containsAll(splitReviewItemIds)) {
            throw new IllegalArgumentException("reviewItemIds must belong to source review case");
        }
        if (sourceReviewItemIds.size() == splitReviewItemIds.size()) {
            throw new IllegalArgumentException("split must leave at least one clue in source review case");
        }
        ReviewCaseView newCase = createCase(draft, List.copyOf(splitReviewItemIds));
        for (SupervisionAlertReviewCaseItemDO sourceCaseItem : sourceCaseItems) {
            if (splitReviewItemIds.contains(sourceCaseItem.getReviewItemId())) {
                reviewCaseItemMapper.deleteById(sourceCaseItem.getId());
            }
        }
        List<Long> remainingReviewItemIds = sourceReviewItemIds.stream()
                .filter(reviewItemId -> !splitReviewItemIds.contains(reviewItemId))
                .toList();
        if (splitReviewItemIds.contains(sourceCaseDO.getPrimaryReviewItemId())) {
            sourceCaseDO.setPrimaryReviewItemId(remainingReviewItemIds.get(0));
        }
        fillCaseSummary(sourceCaseDO, requireItems(remainingReviewItemIds));
        updateCaseIfCurrent(sourceCaseDO, sourcePreviousVersion);
        Map<String, Object> sourceMetadata = new LinkedHashMap<>();
        sourceMetadata.put("role", "source");
        sourceMetadata.put("newReviewCaseId", newCase.id());
        sourceMetadata.put("reviewItemIds", List.copyOf(splitReviewItemIds));
        insertCaseAudit(
                sourceReviewCaseId,
                null,
                "split_case",
                caseRelatedAuditNote("newReviewCaseId", newCase.id(), splitReviewItemIds, draft.notes()),
                operatorUserId,
                LocalDateTime.now(),
                caseOperationMetadata(normalizedOperationId, sourceExpectedVersion, sourcePreviousVersion,
                        sourceCaseDO.getVersion(), sourceMetadata)
        );
        Map<String, Object> newCaseMetadata = new LinkedHashMap<>();
        newCaseMetadata.put("role", "new_case");
        newCaseMetadata.put("sourceReviewCaseId", sourceReviewCaseId);
        newCaseMetadata.put("reviewItemIds", List.copyOf(splitReviewItemIds));
        insertCaseAudit(
                newCase.id(),
                null,
                "split_case",
                caseRelatedAuditNote("sourceReviewCaseId", sourceReviewCaseId, splitReviewItemIds, draft.notes()),
                operatorUserId,
                LocalDateTime.now(),
                caseOperationMetadata(normalizedOperationId, null, null, newCase.version(), newCaseMetadata)
        );
        return new ReviewCaseSplitResult(toCaseView(sourceCaseDO), toCaseView(requireCase(newCase.id())));
    }

    @Override
    public List<ReviewCaseTimelineItem> listCaseTimeline(Long reviewCaseId) {
        requireCase(reviewCaseId);
        List<ReviewCaseTimelineItem> timeline = new ArrayList<>();
        for (SupervisionAlertReviewCaseItemDO caseItemDO : reviewCaseItemMapper.selectByCaseId(reviewCaseId)) {
            SupervisionAlertReviewItemDO itemDO = requireItem(caseItemDO.getReviewItemId());
            for (ReviewEvidenceItem evidenceItem : listTimeline(caseItemDO.getReviewItemId())) {
                timeline.add(new ReviewCaseTimelineItem(
                        reviewCaseId,
                        caseItemDO.getReviewItemId(),
                        itemDO.getCameraId(),
                        evidenceItem.sourceAlertId(),
                        evidenceItem.materialType(),
                        evidenceItem.materialUri(),
                        evidenceItem.happenedAt(),
                        null,
                        evidenceItem.recordStartTime(),
                        null
                ));
            }
        }
        for (SupervisionAlertReviewCaseAuditDO auditDO : reviewCaseAuditMapper.selectByCaseId(reviewCaseId)) {
            timeline.add(new ReviewCaseTimelineItem(
                    reviewCaseId,
                    auditDO.getReviewItemId(),
                    null,
                    null,
                    "case_audit",
                    auditDO.getActionType(),
                    auditDO.getHappenedAt(),
                    auditDO.getActionNote()
            ));
        }
        return timeline;
    }

    @Override
    public void recordCaseAudit(Long reviewCaseId,
                                Long reviewItemId,
                                String actionType,
                                String actionNote,
                                Long operatorUserId,
                                LocalDateTime happenedAt,
                                Map<String, Object> metadata) {
        requireCase(reviewCaseId);
        if (reviewItemId != null) {
            requireItem(reviewItemId);
        }
        insertCaseAudit(reviewCaseId, reviewItemId, actionType, actionNote, operatorUserId, happenedAt, metadata);
    }

    @Override
    public void recordMediaAccessAudit(Long reviewCaseId,
                                       Long reviewItemId,
                                       String actionType,
                                       String actionNote,
                                       Long operatorUserId,
                                       LocalDateTime happenedAt,
                                       Map<String, Object> metadata) {
        if (reviewCaseId != null) {
            recordCaseAudit(reviewCaseId, reviewItemId, actionType, actionNote, operatorUserId, happenedAt, metadata);
            return;
        }
        requireItem(reviewItemId);
        insertCaseAudit(null, reviewItemId, actionType, actionNote, operatorUserId, happenedAt, metadata);
    }

    @Override
    public List<ReviewCaseTimelineItem> listMediaAccessAuditsByReviewItem(Long reviewItemId) {
        requireItem(reviewItemId);
        return reviewCaseAuditMapper.selectByReviewItemId(reviewItemId)
                .stream()
                .filter(auditDO -> "media_access_granted".equals(auditDO.getActionType())
                        || "media_access_denied".equals(auditDO.getActionType()))
                .map(auditDO -> new ReviewCaseTimelineItem(
                        auditDO.getReviewCaseId(),
                        auditDO.getReviewItemId(),
                        null,
                        null,
                        "case_audit",
                        auditDO.getActionType(),
                        auditDO.getHappenedAt(),
                        auditDO.getActionNote()
                ))
                .toList();
    }

    @Override
    public List<ReviewCaseTimelineItem> listEvidenceAuditRecords(ReviewEvidenceAuditQuery query) {
        Objects.requireNonNull(query, "query");
        Long tenantId = requireEvidenceAuditTenantId();
        return reviewCaseAuditMapper.selectEvidenceAuditLookup(
                        tenantId,
                        query.eventId(),
                        query.reviewCaseId(),
                        query.reviewItemId(),
                        query.exportJobNo()
                )
                .stream()
                .map(auditDO -> new ReviewCaseTimelineItem(
                        auditDO.getReviewCaseId(),
                        auditDO.getReviewItemId(),
                        null,
                        null,
                        "case_audit",
                        auditDO.getActionType(),
                        auditDO.getHappenedAt(),
                        auditDO.getActionNote()
                ))
                .toList();
    }

    @Override
    public void recordSemanticTriggerEvaluation(Long reviewItemId,
                                                String actionNote,
                                                Long operatorUserId,
                                                LocalDateTime happenedAt,
                                                Map<String, Object> metadata) {
        Long tenantId = requireSemanticTriggerTenantId();
        if (reviewItemId != null) {
            requireItem(reviewItemId);
        }
        SupervisionAlertReviewCaseAuditDO auditDO = new SupervisionAlertReviewCaseAuditDO();
        auditDO.setReviewCaseId(null);
        auditDO.setReviewItemId(reviewItemId);
        auditDO.setActionType("semantic_trigger_evaluated");
        auditDO.setActionNote(actionNote);
        auditDO.setMetadata(writeJson(metadata));
        auditDO.setOperatorUserId(operatorUserId);
        auditDO.setHappenedAt(happenedAt == null ? LocalDateTime.now() : happenedAt);
        auditDO.setVersion(0);
        reviewCaseAuditMapper.insert(auditDO);
    }

    @Override
    public boolean recordSemanticTriggerDecision(String evaluationId,
                                                  String actionType,
                                                  String actionNote,
                                                  Long operatorUserId,
                                                  LocalDateTime happenedAt,
                                                  Map<String, Object> metadata) {
        Long tenantId = requireSemanticTriggerTenantId();
        return reviewCaseAuditMapper.insertSemanticTriggerDecision(
                tenantId,
                evaluationId,
                actionType,
                actionNote,
                writeJson(metadata),
                operatorUserId,
                happenedAt == null ? LocalDateTime.now() : happenedAt
        ) == 1;
    }

    @Override
    public List<ReviewSemanticTriggerAuditRecord> listSemanticTriggerAudits(String evaluationId) {
        Long tenantId = requireSemanticTriggerTenantId();
        return reviewCaseAuditMapper.selectSemanticTriggerAudits(tenantId, evaluationId)
                .stream()
                .map(auditDO -> new ReviewSemanticTriggerAuditRecord(
                        auditDO.getReviewItemId(),
                        auditDO.getActionType(),
                        auditDO.getOperatorUserId(),
                        auditDO.getHappenedAt(),
                        readJson(auditDO.getMetadata())
                ))
                .toList();
    }

    @Override
    public ReviewSemanticIndexEntry upsertSemanticIndex(ReviewItemAggregate item,
                                                       String document,
                                                       String embeddingKey,
                                                       String embeddingModel,
                                                       String embeddingVectorHash,
                                                       String indexStatus,
                                                       Integer retryCount,
                                                       String lastError,
                                                       LocalDateTime indexedAt) {
        return upsertSemanticIndex(item, document, embeddingKey, embeddingModel, embeddingVectorHash,
                indexStatus, retryCount, lastError, indexedAt, null, null);
    }

    @Override
    public ReviewSemanticIndexEntry upsertSemanticIndex(ReviewItemAggregate item,
                                                       String document,
                                                       String embeddingKey,
                                                       String embeddingModel,
                                                       String embeddingVectorHash,
                                                       String indexStatus,
                                                       Integer retryCount,
                                                       String lastError,
                                                       LocalDateTime indexedAt,
                                                       String indexGenerationId,
                                                       LocalDateTime nextRetryAt) {
        Objects.requireNonNull(item, "item");
        SupervisionAlertReviewSemanticIndexDO indexDO = reviewSemanticIndexMapper.selectByReviewItemId(item.id());
        if (indexDO == null) {
            indexDO = new SupervisionAlertReviewSemanticIndexDO()
                    .setReviewItemId(item.id())
                    .setVersion(0);
        }
        indexDO.setCameraId(item.cameraId())
                .setFirstAlertTime(item.firstAlertTime())
                .setLastAlertTime(item.lastAlertTime())
                .setIndexStatus(indexStatus)
                .setDocument(document)
                .setEmbeddingKey(embeddingKey)
                .setEmbeddingModel(embeddingModel)
                .setEmbeddingVectorHash(embeddingVectorHash)
                .setRetryCount(retryCount == null ? 0 : retryCount)
                .setLastError(lastError)
                .setIndexedAt(indexedAt)
                .setIndexGenerationId(hasText(indexGenerationId)
                        ? indexGenerationId
                        : indexDO.getIndexGenerationId())
                .setClaimToken(null)
                .setClaimedAt(null)
                .setClaimExpiresAt(null)
                .setNextRetryAt(nextRetryAt)
                .setVersion((indexDO.getVersion() == null ? 0 : indexDO.getVersion()) + 1);
        if (indexDO.getId() == null) {
            reviewSemanticIndexMapper.insert(indexDO);
        } else {
            reviewSemanticIndexMapper.updateById(indexDO);
        }
        return toSemanticIndexEntry(indexDO);
    }

    @Override
    public List<ReviewSemanticIndexEntry> claimSemanticIndex(List<Long> reviewItemIds,
                                                             Integer limit,
                                                             String claimToken,
                                                             LocalDateTime claimedAt,
                                                             LocalDateTime claimExpiresAt) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            return List.of();
        }
        int normalizedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        String normalizedToken = hasText(claimToken) ? claimToken : UUID.randomUUID().toString();
        LocalDateTime normalizedClaimedAt = claimedAt == null ? LocalDateTime.now() : claimedAt;
        LocalDateTime normalizedClaimExpiresAt = claimExpiresAt == null
                ? normalizedClaimedAt.plusMinutes(5)
                : claimExpiresAt;
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return DataPermissionUtils.executeIgnore(() -> {
            int claimed = reviewSemanticIndexMapper.claimProcessable(
                    tenantId,
                    reviewItemIds,
                    normalizedLimit,
                    normalizedToken,
                    normalizedClaimedAt,
                    normalizedClaimExpiresAt
            );
            if (claimed <= 0) {
                return List.of();
            }
            return reviewSemanticIndexMapper.selectClaimed(normalizedToken, normalizedLimit).stream()
                    .map(this::toSemanticIndexEntry)
                    .toList();
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewSemanticIndexEntry queueSemanticIndex(ReviewItemAggregate item,
                                                        String document,
                                                        String embeddingKey,
                                                        String embeddingModel,
                                                        String indexGenerationId,
                                                        LocalDateTime queuedAt) {
        Objects.requireNonNull(item, "item");
        SupervisionAlertReviewSemanticIndexDO desired = new SupervisionAlertReviewSemanticIndexDO()
                .setReviewItemId(item.id())
                .setCameraId(item.cameraId())
                .setFirstAlertTime(item.firstAlertTime())
                .setLastAlertTime(item.lastAlertTime())
                .setDocument(document)
                .setEmbeddingKey(embeddingKey)
                .setEmbeddingModel(embeddingModel)
                .setIndexGenerationId(indexGenerationId);
        reviewSemanticIndexMapper.insertPendingIfAbsent(desired);
        reviewSemanticIndexMapper.queueReindexUnlessActivelyClaimed(
                desired,
                queuedAt == null ? LocalDateTime.now() : queuedAt
        );
        return toSemanticIndexEntry(
                reviewSemanticIndexMapper.selectByReviewItemId(item.id()));
    }

    @Override
    public ReviewSemanticIndexEntry completeSemanticIndexClaim(ReviewItemAggregate item,
                                                               String document,
                                                               String embeddingKey,
                                                               String embeddingModel,
                                                               String embeddingVectorHash,
                                                               String indexStatus,
                                                               Integer retryCount,
                                                               String lastError,
                                                               LocalDateTime indexedAt,
                                                               String indexGenerationId,
                                                               LocalDateTime nextRetryAt,
                                                               String claimToken) {
        Objects.requireNonNull(item, "item");
        SupervisionAlertReviewSemanticIndexDO desired = new SupervisionAlertReviewSemanticIndexDO()
                .setReviewItemId(item.id())
                .setCameraId(item.cameraId())
                .setFirstAlertTime(item.firstAlertTime())
                .setLastAlertTime(item.lastAlertTime())
                .setIndexStatus(indexStatus)
                .setDocument(document)
                .setEmbeddingKey(embeddingKey)
                .setEmbeddingModel(embeddingModel)
                .setEmbeddingVectorHash(embeddingVectorHash)
                .setRetryCount(retryCount == null ? 0 : retryCount)
                .setLastError(lastError)
                .setIndexedAt(indexedAt)
                .setIndexGenerationId(indexGenerationId)
                .setNextRetryAt(nextRetryAt);
        int completed = reviewSemanticIndexMapper.completeClaim(desired, claimToken);
        if (completed != 1) {
            throw new IllegalStateException("semantic_index_claim_conflict: " + item.id());
        }
        SupervisionAlertReviewSemanticIndexDO persisted = reviewSemanticIndexMapper.selectByReviewItemId(item.id());
        if (persisted == null) {
            throw new IllegalStateException("semantic index not found: " + item.id());
        }
        return toSemanticIndexEntry(persisted);
    }

    @Override
    public List<ReviewSemanticIndexEntry> listSemanticIndex(ReviewQuery query) {
        List<Long> reviewItemIds = listWorkbench(query).stream()
                .map(ReviewItemAggregate::id)
                .toList();
        return reviewSemanticIndexMapper.selectByReviewItemIds(reviewItemIds)
                .stream()
                .map(this::toSemanticIndexEntry)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewEvidenceExportJob createExportJob(ReviewEvidenceExportPackage exportPackage,
                                                   Long operatorUserId,
                                                   String reason,
                                                   List<Long> boundEventIds,
                                                   String fileHash,
                                                   LocalDateTime expiresAt,
                                                   LocalDateTime createdAt) {
        Objects.requireNonNull(exportPackage, "exportPackage");
        requireCase(exportPackage.reviewCaseId());
        String requestKey = toText(exportPackage.manifest().get("requestKey"), exportPackage.packageNo());
        SupervisionAlertReviewExportJobDO existing = reviewExportJobMapper.selectActiveByRequestKey(requestKey);
        if (existing != null) {
            return toExportJob(existing);
        }
        String jobNo = "REJ-" + UUID.randomUUID();
        SupervisionAlertReviewExportJobDO jobDO = new SupervisionAlertReviewExportJobDO()
                .setJobNo(jobNo)
                .setRequestKey(requestKey)
                .setStatus(SupervisionAlertReviewService.EXPORT_JOB_PENDING)
                .setPackageNo(exportPackage.packageNo())
                .setReviewCaseId(exportPackage.reviewCaseId())
                .setReviewItemIds(joinLongCsv(exportPackage.reviewItemIds()))
                .setEvidenceUris(joinCsv(exportPackage.evidenceUris()))
                .setManifest(writeJson(exportPackage.manifest()))
                .setFileHash(fileHash)
                .setExpiresAt(expiresAt)
                .setOperatorUserId(operatorUserId)
                .setExportReason(reason)
                .setBoundEventIds(joinLongCsv(boundEventIds))
                .setGeneratedAt(createdAt)
                .setVersion(0);
        int inserted = reviewExportJobMapper.insertIfAbsent(jobDO);
        if (inserted == 0) {
            SupervisionAlertReviewExportJobDO duplicate = reviewExportJobMapper.selectActiveByRequestKey(requestKey);
            if (duplicate != null) {
                return toExportJob(duplicate);
            }
            throw new IllegalStateException("export job insert conflicted without an active request: " + requestKey);
        }
        insertCaseAudit(
                exportPackage.reviewCaseId(),
                null,
                "export_evidence_job",
                exportAuditNote(jobNo, fileHash, reason),
                operatorUserId
        );
        return new ReviewEvidenceExportJob(
                jobNo,
                SupervisionAlertReviewService.EXPORT_JOB_PENDING,
                exportPackage,
                fileHash,
                expiresAt,
                operatorUserId,
                reason,
                boundEventIds == null ? List.of() : List.copyOf(boundEventIds),
                createdAt,
                0
        );
    }

    @Override
    public List<ReviewEvidenceExportJob> listExportJobs(Long reviewCaseId) {
        requireCase(reviewCaseId);
        return reviewExportJobMapper.selectByReviewCaseId(reviewCaseId)
                .stream()
                .map(this::toExportJob)
                .toList();
    }

    @Override
    public List<ReviewEvidenceExportJob> listEvidenceAuditExportJobs(ReviewEvidenceAuditQuery query) {
        Objects.requireNonNull(query, "query");
        Long tenantId = requireEvidenceAuditTenantId();
        return reviewExportJobMapper.selectEvidenceAuditLookup(
                        tenantId,
                        query.eventId(),
                        query.reviewCaseId(),
                        query.reviewItemId(),
                        query.exportJobNo()
                )
                .stream()
                .map(this::toExportJob)
                .toList();
    }

    @Override
    public List<ReviewEvidenceExportJob> listAllExportJobs() {
        return reviewExportJobMapper.selectAll()
                .stream()
                .map(this::toExportJob)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReviewEvidenceExportJob> claimProcessableExportJobs(Integer limit,
                                                                    String claimToken,
                                                                    Long claimedBy,
                                                                    LocalDateTime claimedAt,
                                                                    LocalDateTime reclaimBefore) {
        int normalizedLimit = limit == null || limit <= 0 ? 20 : Math.min(limit, 100);
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new SecurityException("tenant context is required for export queue claim");
        }
        return DataPermissionUtils.executeIgnore(() -> {
            int claimedCount = reviewExportJobMapper.claimProcessable(
                    tenantId,
                    normalizedLimit,
                    claimToken,
                    claimedBy,
                    claimedAt,
                    reclaimBefore
            );
            if (claimedCount <= 0) {
                return List.of();
            }
            return reviewExportJobMapper.selectClaimed(claimToken, normalizedLimit).stream()
                    .map(this::toExportJob)
                    .toList();
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewEvidenceExportJob completeExportJobClaim(ReviewEvidenceExportJob job, String claimToken) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(job.exportPackage(), "exportPackage");
        if (job.version() == null) {
            throw new IllegalStateException("export_job_claim_conflict: missing version for " + job.jobNo());
        }
        SupervisionAlertReviewExportJobDO current = reviewExportJobMapper.selectByJobNo(job.jobNo());
        if (current == null) {
            throw new IllegalArgumentException("export job not found: " + job.jobNo());
        }
        Map<String, Object> worker = toStringObjectMap(job.exportPackage().manifest().get("worker"));
        SupervisionAlertReviewExportJobDO desired = new SupervisionAlertReviewExportJobDO()
                .setId(current.getId())
                .setJobNo(current.getJobNo())
                .setRequestKey(current.getRequestKey())
                .setStatus(job.status())
                .setPackageNo(job.exportPackage().packageNo())
                .setReviewCaseId(job.exportPackage().reviewCaseId())
                .setReviewItemIds(joinLongCsv(job.exportPackage().reviewItemIds()))
                .setEvidenceUris(joinCsv(job.exportPackage().evidenceUris()))
                .setManifest(writeJson(job.exportPackage().manifest()))
                .setFileHash(job.fileHash())
                .setExpiresAt(job.expiresAt())
                .setOperatorUserId(job.operatorUserId())
                .setExportReason(job.reason())
                .setBoundEventIds(joinLongCsv(job.boundEventIds()))
                .setGeneratedAt(job.exportPackage().generatedAt())
                .setNextRetryAt(toLocalDateTime(worker.get("nextRetryAt"), null))
                .setLastError(toText(worker.get("lastError"), null))
                .setVersion(job.version());
        int updated = reviewExportJobMapper.completeClaim(desired, claimToken, job.version());
        if (updated != 1) {
            throw new IllegalStateException("export_job_claim_conflict: " + job.jobNo());
        }
        desired.setVersion(job.version() + 1);
        return toExportJob(desired);
    }

    @Override
    public Optional<ReviewEvidenceExportJob> findExportJobByNo(String jobNo) {
        if (jobNo == null || jobNo.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(reviewExportJobMapper.selectByJobNo(jobNo))
                .map(this::toExportJob);
    }

    @Override
    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                           Long operatorUserId,
                                                           String reason,
                                                           LocalDateTime happenedAt,
                                                           String downloadFileHash,
                                                           Map<String, Object> downloadMetadata) {
        SupervisionAlertReviewExportJobDO jobDO = reviewExportJobMapper.selectByJobNo(jobNo);
        if (jobDO == null) {
            throw new IllegalArgumentException("export job not found: " + jobNo);
        }
        if (!hasText(downloadFileHash)) {
            throw new IllegalArgumentException("downloadFileHash is required from downloaded bytes");
        }
        String auditedFileHash = downloadFileHash;
        Map<String, Object> metadata = new LinkedHashMap<>(downloadMetadata == null ? Map.of() : downloadMetadata);
        metadata.put("status", jobDO.getStatus());
        metadata.put("downloadFileHash", auditedFileHash);
        metadata.put("logicalPackageHash", jobDO.getFileHash());
        metadata.put("hashSource", "downloaded_bytes");
        String note = exportDownloadAuditNote(jobNo, auditedFileHash, operatorUserId, reason)
                + "; downloadFileHash=" + auditedFileHash
                + "; logicalPackageHash=" + jobDO.getFileHash();
        insertCaseAudit(
                jobDO.getReviewCaseId(),
                null,
                "export_downloaded",
                note,
                operatorUserId,
                happenedAt,
                metadata
        );
        return new ReviewEvidenceAuditEntry(
                jobDO.getReviewCaseId(),
                null,
                "export_downloaded",
                jobNo,
                auditedFileHash,
                operatorUserId,
                reason,
                splitCsv(jobDO.getEvidenceUris()),
                splitLongCsv(jobDO.getBoundEventIds()),
                happenedAt == null ? LocalDateTime.now() : happenedAt,
                Map.copyOf(metadata)
        );
    }

    @Override
    public void recordCaseAudit(Long reviewCaseId,
                                Long reviewItemId,
                                String actionType,
                                String actionNote,
                                Long operatorUserId,
                                LocalDateTime happenedAt) {
        recordCaseAudit(reviewCaseId, reviewItemId, actionType, actionNote, operatorUserId, happenedAt, Map.of());
    }

    @Override
    public ReviewRuleView save(ReviewRuleCommand command) {
        SupervisionAlertReviewRuleDO ruleDO = new SupervisionAlertReviewRuleDO()
                .setId(command.id())
                .setRuleCode(command.ruleCode())
                .setRuleName(command.ruleName())
                .setSourceSystem(command.sourceSystem())
                .setCameraId(command.cameraId())
                .setZoneCode(command.zoneCode())
                .setObjectLabel(command.objectLabel())
                .setMinStaySeconds(command.minStaySeconds())
                .setInertiaFrames(command.inertiaFrames())
                .setLoiteringSeconds(command.loiteringSeconds())
                .setActiveStart(command.activeStart())
                .setActiveEnd(command.activeEnd())
                .setEnabled(command.enabled() == null || command.enabled())
                .setVersion(0);
        if (ruleDO.getId() == null) {
            reviewRuleMapper.insert(ruleDO);
        } else {
            reviewRuleMapper.updateById(ruleDO);
        }
        return toRuleView(ruleDO);
    }

    @Override
    public List<ReviewRuleView> listEnabled() {
        return reviewRuleMapper.selectEnabled().stream().map(this::toRuleView).toList();
    }

    @Override
    public List<ReviewRuleView> listAll() {
        return reviewRuleMapper.selectAllOrdered().stream().map(this::toRuleView).toList();
    }

    @Override
    public Optional<EventProjection> findByEventId(Long eventId) {
        if (eventId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(supervisionEventMapper.selectById(eventId))
                .map(this::toEventProjection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewRuntimeLockAcquisition acquireRuntimePatrolLock(String lockName,
                                                                 LocalDateTime expiresAt,
                                                                 Long operatorUserId) {
        LocalDateTime now = LocalDateTime.now();
        if (!hasText(lockName)) {
            return new ReviewRuntimeLockAcquisition(lockName, false, false, null, null, null, now, "missing_lock_name");
        }
        SupervisionAlertReviewRuntimeLockDO lockDO = reviewRuntimeLockMapper.selectByLockName(lockName);
        if (lockDO == null) {
            SupervisionAlertReviewRuntimeLockDO created = new SupervisionAlertReviewRuntimeLockDO()
                    .setLockName(lockName)
                    .setOwnerUserId(operatorUserId)
                    .setLockedUntil(expiresAt)
                    .setLastLockedAt(now)
                    .setVersion(0);
            reviewRuntimeLockMapper.insert(created);
            return new ReviewRuntimeLockAcquisition(lockName, true, false, null, null, expiresAt, now, "created");
        }
        LocalDateTime previousLockedUntil = lockDO.getLockedUntil();
        Long previousOwnerUserId = lockDO.getOwnerUserId();
        if (previousLockedUntil != null && previousLockedUntil.isAfter(now)) {
            return new ReviewRuntimeLockAcquisition(
                    lockName,
                    false,
                    false,
                    previousOwnerUserId,
                    previousLockedUntil,
                    previousLockedUntil,
                    now,
                    "active_lock"
            );
        }
        SupervisionAlertReviewRuntimeLockDO updateDO = new SupervisionAlertReviewRuntimeLockDO()
                .setOwnerUserId(operatorUserId)
                .setLockedUntil(expiresAt)
                .setLastLockedAt(now);
        int updated = reviewRuntimeLockMapper.update(updateDO,
                new LambdaUpdateWrapper<SupervisionAlertReviewRuntimeLockDO>()
                        .eq(SupervisionAlertReviewRuntimeLockDO::getId, lockDO.getId())
                        .and(wrapper -> wrapper
                                .isNull(SupervisionAlertReviewRuntimeLockDO::getLockedUntil)
                                .or()
                                .le(SupervisionAlertReviewRuntimeLockDO::getLockedUntil, now)));
        if (updated <= 0) {
            return new ReviewRuntimeLockAcquisition(
                    lockName,
                    false,
                    false,
                    previousOwnerUserId,
                    previousLockedUntil,
                    previousLockedUntil,
                    now,
                    "lock_race_lost"
            );
        }
        return new ReviewRuntimeLockAcquisition(
                lockName,
                true,
                true,
                previousOwnerUserId,
                previousLockedUntil,
                expiresAt,
                now,
                "stale_lock_recovered"
        );
    }

    @Override
    public boolean tryAcquireRuntimePatrolLock(String lockName, LocalDateTime expiresAt, Long operatorUserId) {
        return Boolean.TRUE.equals(acquireRuntimePatrolLock(lockName, expiresAt, operatorUserId).acquired());
    }

    @Override
    public void releaseRuntimePatrolLock(String lockName, Long operatorUserId) {
        if (!hasText(lockName)) {
            return;
        }
        SupervisionAlertReviewRuntimeLockDO lockDO = reviewRuntimeLockMapper.selectByLockName(lockName);
        if (lockDO == null) {
            return;
        }
        if (operatorUserId != null && lockDO.getOwnerUserId() != null
                && !Objects.equals(operatorUserId, lockDO.getOwnerUserId())) {
            return;
        }
        lockDO.setLockedUntil(LocalDateTime.now());
        reviewRuntimeLockMapper.updateById(lockDO);
    }

    @Override
    public String recordRuntimePatrolRun(String status,
                                         Integer attemptCount,
                                         List<String> alerts,
                                         List<String> recommendedActions,
                                         Long operatorUserId,
                                         LocalDateTime executedAt,
                                         Map<String, Object> metadata) {
        String runId = "RPR-" + UUID.randomUUID();
        reviewRuntimeRunMapper.insert(new SupervisionAlertReviewRuntimeRunDO()
                .setRunId(runId)
                .setStatus(status)
                .setAttemptCount(attemptCount == null ? 0 : attemptCount)
                .setAlerts(joinCsv(alerts))
                .setRecommendedActions(joinCsv(recommendedActions))
                .setOperatorUserId(operatorUserId)
                .setExecutedAt(executedAt == null ? LocalDateTime.now() : executedAt)
                .setMetadata(writeJson(metadata))
                .setVersion(0));
        return runId;
    }

    @Override
    public int enqueueRuntimePatrolAlerts(String runId,
                                          List<String> alerts,
                                          List<String> recommendedActions,
                                          Long operatorUserId,
                                          LocalDateTime executedAt,
                                          Map<String, Object> metadata) {
        if (alerts == null || alerts.isEmpty()) {
            return 0;
        }
        LocalDateTime createdAt = executedAt == null ? LocalDateTime.now() : executedAt;
        int count = 0;
        for (String alert : alerts) {
            if (!hasText(alert)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("runId", runId);
            payload.put("alert", alert);
            payload.put("action", runtimeOutboxAction(alert, metadata));
            payload.put("recommendedActions", recommendedActions == null ? List.of() : recommendedActions);
            payload.put("metadata", metadata == null ? Map.of() : metadata);
            payload.entrySet().removeIf(entry -> entry.getValue() == null);
            reviewRuntimeOutboxMapper.insert(new SupervisionAlertReviewRuntimeOutboxDO()
                    .setRunId(runId)
                    .setEventType("review_runtime_alert")
                    .setAlertKey(alert)
                    .setPayload(writeJson(payload))
                    .setOutboxStatus("pending")
                    .setOperatorUserId(operatorUserId)
                    .setCreatedAt(createdAt)
                    .setRetryCount(0)
                    .setVersion(0));
            count++;
        }
        return count;
    }

    @Override
    public int enqueueOperationsReportDelivery(ReviewOperationsReport report,
                                               boolean scheduled,
                                               LocalDateTime queuedAt) {
        if (report == null) {
            return 0;
        }
        Map<String, Object> deliveryPlan = report.deliveryPlan() == null ? Map.of() : report.deliveryPlan();
        Map<String, Object> acknowledgement = report.acknowledgement() == null ? Map.of() : report.acknowledgement();
        String reportKey = toText(deliveryPlan.get("reportKey"), toText(acknowledgement.get("reportKey"), null));
        if (!hasText(reportKey)) {
            return 0;
        }
        LocalDateTime createdAt = queuedAt == null ? LocalDateTime.now() : queuedAt;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportKey", reportKey);
        payload.put("reportType", report.reportType());
        payload.put("action", "deliver_operations_report");
        payload.put("scheduled", scheduled);
        payload.put("channels", deliveryPlan.get("channels"));
        payload.put("deliveryStatus", deliveryPlan.get("deliveryStatus"));
        payload.put("acknowledgementStatus", acknowledgement.get("status"));
        payload.put("reviewItemIds", report.reviewItemIds());
        payload.put("evidenceGaps", report.evidenceGaps());
        payload.put("recommendedActions", report.recommendedActions());
        payload.put("generatedAt", report.generatedAt() == null ? null : report.generatedAt().toString());
        payload.put("operatorUserId", report.operatorUserId());
        payload.entrySet().removeIf(entry -> entry.getValue() == null);
        SupervisionAlertReviewRuntimeOutboxDO entry = new SupervisionAlertReviewRuntimeOutboxDO()
                .setRunId(runtimeOutboxReportRunId(reportKey))
                .setEventType("review_operations_report")
                .setAlertKey(reportKey)
                .setPayload(writeJson(payload))
                .setOutboxStatus("pending")
                .setOperatorUserId(report.operatorUserId())
                .setCreatedAt(createdAt)
                .setRetryCount(0)
                .setVersion(0);
        return reviewRuntimeOutboxMapper.insertOperationsReportIfAbsent(
                TenantContextHolder.getRequiredTenantId(), entry);
    }

    private static String runtimeOutboxReportRunId(String reportKey) {
        if (!hasText(reportKey) || reportKey.length() <= 64) {
            return reportKey;
        }
        return reportKey.substring(reportKey.length() - 64);
    }

    @Override
    public List<ReviewRuntimeOutboxMessage> listPendingRuntimeOutbox(Integer limit) {
        return reviewRuntimeOutboxMapper.selectPending(limit)
                .stream()
                .map(SupervisionAlertReviewMapperStore::toRuntimeOutboxMessage)
                .toList();
    }

    @Override
    public List<ReviewRuntimeOutboxMessage> claimPendingRuntimeOutbox(Integer limit,
                                                                      String claimToken,
                                                                      Long operatorUserId,
                                                                      LocalDateTime claimedAt,
                                                                      LocalDateTime reclaimBefore) {
        int normalizedLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        String normalizedToken = hasText(claimToken) ? claimToken : UUID.randomUUID().toString();
        LocalDateTime normalizedClaimedAt = claimedAt == null ? LocalDateTime.now() : claimedAt;
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        return DataPermissionUtils.executeIgnore(() -> {
            int claimedCount = reviewRuntimeOutboxMapper.claimPending(
                    tenantId,
                    normalizedLimit,
                    normalizedToken,
                    operatorUserId,
                    normalizedClaimedAt,
                    reclaimBefore
            );
            if (claimedCount <= 0) {
                return List.of();
            }
            return reviewRuntimeOutboxMapper.selectClaimed(normalizedToken, normalizedLimit)
                    .stream()
                    .map(SupervisionAlertReviewMapperStore::toRuntimeOutboxMessage)
                    .toList();
        });
    }

    private static ReviewRuntimeOutboxMessage toRuntimeOutboxMessage(SupervisionAlertReviewRuntimeOutboxDO outboxDO) {
        return new ReviewRuntimeOutboxMessage(
                outboxDO.getId(),
                outboxDO.getRunId(),
                outboxDO.getEventType(),
                outboxDO.getAlertKey(),
                outboxDO.getPayload(),
                outboxDO.getRetryCount(),
                outboxDO.getCreatedAt()
        );
    }

    @Override
    public void markRuntimeOutboxPublished(Long outboxId, LocalDateTime publishedAt) {
        if (outboxId == null) {
            return;
        }
        SupervisionAlertReviewRuntimeOutboxDO outboxDO = reviewRuntimeOutboxMapper.selectById(outboxId);
        if (outboxDO == null) {
            return;
        }
        outboxDO.setOutboxStatus("published")
                .setPublishedAt(publishedAt == null ? LocalDateTime.now() : publishedAt)
                .setLastError(null);
        reviewRuntimeOutboxMapper.updateById(outboxDO);
    }

    @Override
    public void markRuntimeOutboxFailed(Long outboxId, String lastError, LocalDateTime failedAt) {
        if (outboxId == null) {
            return;
        }
        SupervisionAlertReviewRuntimeOutboxDO outboxDO = reviewRuntimeOutboxMapper.selectById(outboxId);
        if (outboxDO == null) {
            return;
        }
        outboxDO.setOutboxStatus("failed")
                .setPublishedAt(failedAt == null ? LocalDateTime.now() : failedAt)
                .setRetryCount((outboxDO.getRetryCount() == null ? 0 : outboxDO.getRetryCount()) + 1)
                .setLastError(trimToLength(lastError, 500));
        reviewRuntimeOutboxMapper.updateById(outboxDO);
    }

    private static String runtimeOutboxAction(String alert, Map<String, Object> metadata) {
        Map<String, Object> alertActions = toStringObjectMap(metadata == null ? null : metadata.get("alertActions"));
        String action = toText(alertActions.get(alert), null);
        return hasText(action) ? action : null;
    }

    private SupervisionAlertReviewItemDO requireItem(Long reviewItemId) {
        SupervisionAlertReviewItemDO itemDO = reviewItemMapper.selectById(reviewItemId);
        if (itemDO == null) {
            throw new IllegalArgumentException("reviewItemId not found: " + reviewItemId);
        }
        return itemDO;
    }

    private SupervisionAlertReviewItemDO requireItemForUpdate(Long reviewItemId) {
        SupervisionAlertReviewItemDO itemDO = reviewItemMapper.selectByIdForUpdate(
                reviewSegmentTenantId(TenantContextHolder.getTenantId()),
                reviewItemId
        );
        if (itemDO == null) {
            throw new IllegalArgumentException("reviewItemId not found: " + reviewItemId);
        }
        return itemDO;
    }

    private ReviewItemAggregate resolveConcurrentReviewStatusMiss(Long reviewItemId, String targetStatus) {
        SupervisionAlertReviewItemDO current = requireItem(reviewItemId);
        if (Objects.equals(targetStatus, current.getReviewStatus())) {
            return toAggregate(current);
        }
        throw new IllegalStateException("review_item_status_conflict: "
                + current.getReviewStatus() + " -> " + targetStatus);
    }

    private static Integer nextVersion(Integer currentVersion) {
        return currentVersion == null ? 1 : currentVersion + 1;
    }

    private void insertEvidence(Long reviewItemId, List<ReviewEvidenceItem> evidenceItems) {
        if (evidenceItems == null || evidenceItems.isEmpty()) {
            return;
        }
        for (ReviewEvidenceItem evidenceItem : evidenceItems) {
            if (reviewEvidenceMapper.selectExisting(
                    reviewItemId,
                    evidenceItem.sourceAlertId(),
                    evidenceItem.materialType(),
                    evidenceItem.materialUri()) == null) {
                reviewEvidenceMapper.insert(toEvidenceDO(reviewItemId, evidenceItem));
            }
        }
    }

    private void insertIngestIdentities(SupervisionAlertReviewItemDO itemDO,
                                        String sourceAlertId,
                                        String sourcePayloadHash,
                                        Map<String, Object> reviewData) {
        Set<String> identityKeys = ingestIdentityLookupKeys(
                itemDO.getSourceSystem(),
                sourceAlertId,
                toStringList(reviewData == null ? null : reviewData.get("ingestIdentityKeys")),
                sourcePayloadHash
        );
        Long tenantId = reviewIdentityTenantId(itemDO.getTenantId());
        for (String identityKey : identityKeys) {
            SupervisionAlertReviewIngestIdentityDO existing =
                    reviewIngestIdentityMapper.selectByIdentity(tenantId, itemDO.getSourceSystem(), identityKey);
            if (existing != null) {
                if (Objects.equals(existing.getReviewItemId(), itemDO.getId())) {
                    continue;
                }
                throw new DuplicateKeyException("duplicate alert review ingest identity: " + identityKey);
            }
            reviewIngestIdentityMapper.insert(new SupervisionAlertReviewIngestIdentityDO()
                    .setTenantId(tenantId)
                    .setReviewItemId(itemDO.getId())
                    .setSourceSystem(itemDO.getSourceSystem())
                    .setIdentityKey(identityKey)
                    .setSourceAlertId(sourceAlertIdFromIdentityKey(itemDO.getSourceSystem(), identityKey, sourceAlertId))
                    .setSourcePayloadHash(sourcePayloadHashFromIdentityKey(itemDO.getSourceSystem(), identityKey, sourcePayloadHash)));
        }
    }

    private void upsertReviewSegment(SupervisionAlertReviewItemDO itemDO) {
        Map<String, Object> reviewData = readJson(itemDO.getReviewData());
        Map<String, Object> segment = toStringObjectMap(reviewData.get("reviewSegment"));
        if (segment.isEmpty()) {
            return;
        }
        SupervisionAlertReviewSegmentDO segmentDO = reviewSegmentMapper.selectByReviewItemId(itemDO.getId());
        boolean insert = segmentDO == null;
        if (insert) {
            segmentDO = new SupervisionAlertReviewSegmentDO()
                    .setTenantId(reviewSegmentTenantId(itemDO.getTenantId()))
                    .setReviewItemId(itemDO.getId())
                    .setVersion(0);
        }
        String segmentStatus = toText(segment.get("status"), "active");
        LocalDateTime segmentStartTime = toLocalDateTime(segment.get("startTime"), itemDO.getFirstAlertTime());
        LocalDateTime segmentEndTime = reviewSegmentPersistenceEndTime(
                segmentStatus,
                segmentStartTime,
                toLocalDateTime(segment.get("endTime"), itemDO.getLastAlertTime())
        );
        segmentDO.setTenantId(reviewSegmentTenantId(itemDO.getTenantId()));
        segmentDO.setSegmentNo(toText(segment.get("segmentId"), itemDO.getReviewItemNo()))
                .setCameraId(toText(segment.get("cameraId"), itemDO.getCameraId()))
                .setSeverity(toText(segment.get("severity"), "alert"))
                .setSegmentStatus(segmentStatus)
                .setStartTime(segmentStartTime)
                .setEndTime(segmentEndTime)
                .setObjectIds(joinCsv(toStringList(segment.get("objectIds"))))
                .setZoneCodes(joinCsv(toStringList(segment.get("zones"))))
                .setSourceAlertIds(joinCsv(toStringList(segment.get("sourceAlertIds"))))
                .setSegmentEvents(writeJsonValue(segment.get("events")))
                .setSegmentMetadata(writeJsonValue(segment));
        assertNoOverlappingReviewSegment(segmentDO);
        if (insert) {
            reviewSegmentMapper.insert(segmentDO);
        } else {
            reviewSegmentMapper.updateById(segmentDO);
        }
    }

    private void assertNoOverlappingReviewSegment(SupervisionAlertReviewSegmentDO segmentDO) {
        if (!hasText(segmentDO.getCameraId())) {
            throw new IllegalArgumentException("review segment cameraId is required: " + segmentDO.getReviewItemId());
        }
        assertReviewSegmentStatusAllowed(segmentDO.getSegmentStatus(), segmentDO.getReviewItemId());
        if (segmentDO.getStartTime() == null) {
            throw new IllegalArgumentException("review segment startTime is required: " + segmentDO.getReviewItemId());
        }
        if (segmentDO.getEndTime() != null && segmentDO.getEndTime().isBefore(segmentDO.getStartTime())) {
            throw new IllegalArgumentException("review segment endTime cannot be before startTime: " + segmentDO.getReviewItemId());
        }
        for (SupervisionAlertReviewSegmentDO overlap : reviewSegmentMapper.selectOverlapping(
                segmentDO.getTenantId(),
                segmentDO.getCameraId(),
                segmentDO.getStartTime(),
                segmentDO.getEndTime())) {
            if (Boolean.TRUE.equals(overlap.getDeleted())) {
                continue;
            }
            if (!Objects.equals(overlap.getReviewItemId(), segmentDO.getReviewItemId())) {
                throw new IllegalStateException("overlapping review segment for camera "
                        + segmentDO.getCameraId() + ": " + overlap.getReviewItemId());
            }
        }
    }

    private static LocalDateTime reviewSegmentPersistenceEndTime(String status,
                                                                 LocalDateTime startTime,
                                                                 LocalDateTime endTime) {
        if (!"ended".equals(status)) {
            return null;
        }
        return endTime == null ? startTime : endTime;
    }

    private static void assertReviewSegmentStatusAllowed(String status, Long reviewItemId) {
        if (!List.of("active", "detection", "alert", "ended").contains(status)) {
            throw new IllegalArgumentException("review segment status must be active, detection, alert, or ended: " + reviewItemId);
        }
    }

    private static Long reviewSegmentTenantId(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }

    private static Long reviewIdentityTenantId(Long tenantId) {
        return tenantId == null ? 0L : tenantId;
    }

    private static Long requireEvidenceAuditTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new SecurityException("tenant context is required for evidence audit lookup");
        }
        return tenantId;
    }

    private static Long requireSemanticTriggerTenantId() {
        Long tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            throw new SecurityException("tenant context is required for semantic trigger audit");
        }
        return tenantId;
    }

    private static Set<String> ingestIdentityLookupKeys(String sourceSystem,
                                                        String sourceAlertId,
                                                        List<String> identityKeys,
                                                        String sourcePayloadHash) {
        Set<String> keys = new LinkedHashSet<>();
        if (identityKeys != null) {
            for (String identityKey : identityKeys) {
                if (hasText(identityKey)) {
                    keys.add(identityKey);
                }
            }
        }
        if (hasText(sourcePayloadHash)) {
            keys.add(sourceSystem + ":payload:" + sourcePayloadHash);
        }
        if (hasText(sourceAlertId)) {
            keys.add(sourceSystem + ":alert:" + sourceAlertId);
        }
        return keys;
    }

    private static String sourceAlertIdFromIdentityKey(String sourceSystem,
                                                       String identityKey,
                                                       String fallback) {
        String prefix = sourceSystem + ":alert:";
        if (hasText(identityKey) && identityKey.startsWith(prefix)) {
            return identityKey.substring(prefix.length());
        }
        return fallback;
    }

    private static String sourcePayloadHashFromIdentityKey(String sourceSystem,
                                                          String identityKey,
                                                          String fallback) {
        String prefix = sourceSystem + ":payload:";
        if (hasText(identityKey) && identityKey.startsWith(prefix)) {
            return identityKey.substring(prefix.length());
        }
        return fallback;
    }

    private ReviewItemAggregate toAggregate(SupervisionAlertReviewItemDO itemDO) {
        Map<String, Object> reviewData = readJson(itemDO.getReviewData());
        Map<String, Object> eventProjection = toStringObjectMap(reviewData.get("eventProjection"));
        return new ReviewItemAggregate(
                itemDO.getId(),
                itemDO.getReviewItemNo(),
                itemDO.getSourceSystem(),
                itemDO.getRuleCode(),
                itemDO.getSourceAlertType(),
                itemDO.getDeviceId(),
                itemDO.getCameraId(),
                itemDO.getZoneCode(),
                itemDO.getObjectLabel(),
                itemDO.getFirstAlertTime(),
                itemDO.getLastAlertTime(),
                itemDO.getAlertCount(),
                splitSourceAlertIds(itemDO.getSourceAlertIds()),
                reviewData,
                itemDO.getReviewStatus(),
                itemDO.getReviewerUserId(),
                itemDO.getReviewedAt(),
                itemDO.getIgnoreReason(),
                readJson(itemDO.getRuleSuggestion()),
                itemDO.getEventId(),
                itemDO.getConvertedAt(),
                itemDO.getRecordEvidenceStatus(),
                itemDO.getRecordEvidenceCheckedAt(),
                itemDO.getRecordEvidenceMessage(),
                toText(eventProjection.get("eventStatus"), null),
                toText(eventProjection.get("closeCheckStatus"), null),
                toText(eventProjection.get("evidenceStatus"), null),
                toText(eventProjection.get("eventReviewStatus"), null),
                !reviewCaseItemMapper.selectByReviewItemId(itemDO.getId()).isEmpty(),
                itemDO.getRuleSuggestionStatus(),
                itemDO.getRuleSuggestionUpdatedAt()
        );
    }

    private ReviewEvidenceItem toEvidenceItem(SupervisionAlertReviewEvidenceDO evidenceDO) {
        return new ReviewEvidenceItem(
                evidenceDO.getReviewItemId(),
                evidenceDO.getSourceAlertId(),
                evidenceDO.getMaterialType(),
                evidenceDO.getMaterialUri(),
                evidenceDO.getHappenedAt(),
                evidenceDO.getRecordStartTime()
        );
    }

    private ReviewUserStatusView toUserStatusView(SupervisionAlertReviewUserStatusDO statusDO) {
        return new ReviewUserStatusView(
                statusDO.getReviewItemId(),
                statusDO.getUserId(),
                statusDO.getHasBeenReviewed(),
                statusDO.getReviewedAt()
        );
    }

    private SupervisionAlertReviewEvidenceDO toEvidenceDO(Long reviewItemId, ReviewEvidenceItem evidenceItem) {
        return new SupervisionAlertReviewEvidenceDO()
                .setReviewItemId(reviewItemId)
                .setSourceAlertId(evidenceItem.sourceAlertId())
                .setMaterialType(evidenceItem.materialType())
                .setMaterialUri(evidenceItem.materialUri())
                .setHappenedAt(evidenceItem.happenedAt())
                .setRecordStartTime(evidenceItem.recordStartTime());
    }

    private EventProjection toEventProjection(SupervisionEventDO eventDO) {
        return new EventProjection(
                eventDO.getId(),
                eventDO.getEventStatus(),
                eventDO.getCloseCheckStatus(),
                eventDO.getEvidenceStatus()
        );
    }

    private ReviewRuleView toRuleView(SupervisionAlertReviewRuleDO ruleDO) {
        return new ReviewRuleView(
                ruleDO.getId(),
                ruleDO.getRuleCode(),
                ruleDO.getRuleName(),
                ruleDO.getSourceSystem(),
                ruleDO.getCameraId(),
                ruleDO.getZoneCode(),
                ruleDO.getObjectLabel(),
                ruleDO.getMinStaySeconds(),
                ruleDO.getActiveStart(),
                ruleDO.getActiveEnd(),
                ruleDO.getEnabled(),
                ruleDO.getInertiaFrames(),
                ruleDO.getLoiteringSeconds()
        );
    }

    private ReviewSemanticIndexEntry toSemanticIndexEntry(SupervisionAlertReviewSemanticIndexDO indexDO) {
        return new ReviewSemanticIndexEntry(
                indexDO.getReviewItemId(),
                indexDO.getCameraId(),
                indexDO.getFirstAlertTime(),
                indexDO.getLastAlertTime(),
                indexDO.getIndexStatus(),
                indexDO.getDocument(),
                indexDO.getEmbeddingKey(),
                indexDO.getEmbeddingModel(),
                indexDO.getEmbeddingVectorHash(),
                indexDO.getRetryCount(),
                indexDO.getLastError(),
                indexDO.getIndexedAt(),
                indexDO.getIndexGenerationId(),
                indexDO.getClaimToken(),
                indexDO.getClaimedAt(),
                indexDO.getClaimExpiresAt(),
                indexDO.getNextRetryAt(),
                indexDO.getVersion()
        );
    }

    private ReviewEvidenceExportJob toExportJob(SupervisionAlertReviewExportJobDO jobDO) {
        ReviewEvidenceExportPackage exportPackage = new ReviewEvidenceExportPackage(
                jobDO.getPackageNo(),
                toText(readJson(jobDO.getManifest()).get("format"), "manifest"),
                jobDO.getReviewCaseId(),
                splitLongCsv(jobDO.getReviewItemIds()),
                splitCsv(jobDO.getEvidenceUris()),
                listCaseTimeline(jobDO.getReviewCaseId()),
                readJson(jobDO.getManifest()),
                jobDO.getGeneratedAt()
        );
        return new ReviewEvidenceExportJob(
                jobDO.getJobNo(),
                jobDO.getStatus(),
                exportPackage,
                jobDO.getFileHash(),
                jobDO.getExpiresAt(),
                jobDO.getOperatorUserId(),
                jobDO.getExportReason(),
                splitLongCsv(jobDO.getBoundEventIds()),
                jobDO.getGeneratedAt(),
                jobDO.getVersion()
        );
    }

    private static ReviewReportAcknowledgement toReportAcknowledgement(SupervisionAlertReviewReportAckDO ackDO) {
        return new ReviewReportAcknowledgement(
                ackDO.getReportKey(),
                ackDO.getReportType(),
                ackDO.getAcknowledgementStatus(),
                ackDO.getAcknowledgedBy(),
                ackDO.getAcknowledgedAt(),
                ackDO.getAcknowledgementNote(),
                false,
                readJson(ackDO.getMetadata())
        );
    }

    private SupervisionAlertReviewCaseDO requireCase(Long reviewCaseId) {
        SupervisionAlertReviewCaseDO caseDO = reviewCaseMapper.selectById(reviewCaseId);
        if (caseDO == null) {
            throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
        }
        return caseDO;
    }

    private SupervisionAlertReviewCaseDO requireCaseForUpdate(Long reviewCaseId) {
        SupervisionAlertReviewCaseDO caseDO = reviewCaseMapper.selectByIdForUpdate(
                reviewSegmentTenantId(TenantContextHolder.getTenantId()),
                reviewCaseId
        );
        if (caseDO == null) {
            throw new IllegalArgumentException("reviewCaseId not found: " + reviewCaseId);
        }
        return caseDO;
    }

    private Map<Long, SupervisionAlertReviewCaseDO> lockCases(Long... reviewCaseIds) {
        Map<Long, SupervisionAlertReviewCaseDO> locked = new LinkedHashMap<>();
        Arrays.stream(reviewCaseIds)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(reviewCaseId -> locked.put(reviewCaseId, requireCaseForUpdate(reviewCaseId)));
        return locked;
    }

    private void updateCaseIfCurrent(SupervisionAlertReviewCaseDO caseDO, Integer expectedVersion) {
        Integer nextCaseVersion = nextVersion(expectedVersion);
        caseDO.setVersion(nextCaseVersion);
        int updated = reviewCaseMapper.updateIfVersion(
                reviewSegmentTenantId(TenantContextHolder.getTenantId()),
                caseDO,
                expectedVersion
        );
        if (updated == 0) {
            throw caseVersionConflict(caseDO.getId(), expectedVersion, null);
        }
    }

    private static void assertExpectedCaseVersion(SupervisionAlertReviewCaseDO caseDO, Integer expectedVersion) {
        if (expectedVersion != null && !Objects.equals(expectedVersion, caseDO.getVersion())) {
            throw caseVersionConflict(caseDO.getId(), expectedVersion, caseDO.getVersion());
        }
    }

    private static IllegalStateException caseVersionConflict(Long reviewCaseId,
                                                             Integer expectedVersion,
                                                             Integer actualVersion) {
        return new IllegalStateException("case_version_conflict: reviewCaseId=" + reviewCaseId
                + ", expectedVersion=" + expectedVersion
                + ", actualVersion=" + actualVersion);
    }

    private boolean hasCaseOperationAudit(Long reviewCaseId, String actionType, String operationId) {
        return caseOperationAudits(actionType, operationId).stream()
                .anyMatch(audit -> Objects.equals(reviewCaseId, audit.getReviewCaseId()));
    }

    private List<SupervisionAlertReviewCaseAuditDO> caseOperationAudits(String actionType, String operationId) {
        return reviewCaseAuditMapper.selectByOperationId(
                reviewSegmentTenantId(TenantContextHolder.getTenantId()),
                actionType,
                operationId
        );
    }

    private Optional<Long> splitResultCaseId(Long sourceReviewCaseId, String operationId) {
        return caseOperationAudits("split_case", operationId).stream()
                .filter(audit -> Objects.equals(sourceReviewCaseId, audit.getReviewCaseId()))
                .map(SupervisionAlertReviewCaseAuditDO::getMetadata)
                .map(SupervisionAlertReviewMapperStore::readJson)
                .map(metadata -> toLongValue(metadata.get("newReviewCaseId")))
                .filter(Objects::nonNull)
                .findFirst();
    }

    private static String normalizeCaseOperationId(String operationId, Object... fallbackParts) {
        if (hasText(operationId)) {
            String normalized = operationId.trim();
            if (normalized.length() > 128 || !normalized.matches("[A-Za-z0-9._:-]+")) {
                throw new IllegalArgumentException(
                        "operationId must contain 1-128 letters, digits, dot, underscore, colon, or hyphen");
            }
            return normalized;
        }
        List<String> canonicalParts = Arrays.stream(fallbackParts)
                .map(String::valueOf)
                .toList();
        UUID deterministicId = UUID.nameUUIDFromBytes(
                String.join("|", canonicalParts).getBytes(StandardCharsets.UTF_8));
        return "auto:" + deterministicId;
    }

    private static Map<String, Object> caseOperationMetadata(String operationId,
                                                             Integer requestedVersion,
                                                             Integer versionBefore,
                                                             Integer versionAfter,
                                                             Map<String, Object> details) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("operationId", operationId);
        metadata.put("requestedVersion", requestedVersion);
        metadata.put("versionBefore", versionBefore);
        metadata.put("versionAfter", versionAfter);
        if (details != null) {
            metadata.putAll(details);
        }
        return metadata;
    }

    private static Long toLongValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<SupervisionAlertReviewItemDO> requireItems(List<Long> reviewItemIds) {
        if (reviewItemIds == null || reviewItemIds.isEmpty()) {
            throw new IllegalArgumentException("reviewItemIds must not be empty");
        }
        List<SupervisionAlertReviewItemDO> items = new ArrayList<>(reviewItemIds.size());
        for (Long reviewItemId : reviewItemIds) {
            items.add(requireItem(reviewItemId));
        }
        return items;
    }

    private void insertCaseItems(Long reviewCaseId, List<Long> reviewItemIds) {
        int sortOrder = 1;
        for (Long reviewItemId : reviewItemIds) {
            if (reviewCaseItemMapper.selectExisting(reviewCaseId, reviewItemId) == null) {
                reviewCaseItemMapper.insert(new SupervisionAlertReviewCaseItemDO()
                        .setReviewCaseId(reviewCaseId)
                        .setReviewItemId(reviewItemId)
                        .setSortOrder(sortOrder)
                        .setAddedAt(LocalDateTime.now())
                        .setVersion(0));
            }
            sortOrder++;
        }
    }

    private static void ensureCaseOpen(SupervisionAlertReviewCaseDO caseDO) {
        if (!SupervisionAlertReviewService.REVIEW_CASE_OPEN.equals(caseDO.getStatus())) {
            throw new IllegalStateException("review case is not open: " + caseDO.getId() + " status=" + caseDO.getStatus());
        }
    }

    private static String caseRelatedAuditNote(String relatedKey,
                                               Long relatedCaseId,
                                               Iterable<Long> reviewItemIds,
                                               String notes) {
        List<String> values = new ArrayList<>();
        values.add(relatedKey + "=" + relatedCaseId);
        if (reviewItemIds != null) {
            values.add("reviewItemIds=" + joinLongCsv(reviewItemIds));
        }
        if (hasText(notes)) {
            values.add("notes=" + notes);
        }
        return String.join("; ", values);
    }

    private static String caseOwnerAuditNote(Long ownerUserId, String notes) {
        List<String> values = new ArrayList<>();
        values.add("ownerUserId=" + ownerUserId);
        if (hasText(notes)) {
            values.add("notes=" + notes);
        }
        return String.join("; ", values);
    }

    private static String caseNotesAuditNote(String notes) {
        return hasText(notes) ? "notes=" + notes : null;
    }

    private void insertCaseAudit(Long reviewCaseId,
                                 Long reviewItemId,
                                 String actionType,
                                 String actionNote,
                                 Long operatorUserId) {
        insertCaseAudit(reviewCaseId, reviewItemId, actionType, actionNote, operatorUserId, LocalDateTime.now(), null);
    }

    private void insertCaseAudit(Long reviewCaseId,
                                 Long reviewItemId,
                                 String actionType,
                                 String actionNote,
                                 Long operatorUserId,
                                 LocalDateTime happenedAt,
                                 Map<String, Object> metadata) {
        reviewCaseAuditMapper.insert(new SupervisionAlertReviewCaseAuditDO()
                .setReviewCaseId(reviewCaseId)
                .setReviewItemId(reviewItemId)
                .setActionType(actionType)
                .setActionNote(actionNote)
                .setMetadata(writeJson(metadata))
                .setOperatorUserId(operatorUserId)
                .setHappenedAt(happenedAt == null ? LocalDateTime.now() : happenedAt)
                .setVersion(0));
    }

    private void fillCaseSummary(SupervisionAlertReviewCaseDO caseDO, List<SupervisionAlertReviewItemDO> items) {
        List<String> cameraIds = items.stream()
                .map(SupervisionAlertReviewItemDO::getCameraId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        LocalDateTime startTime = items.stream()
                .map(SupervisionAlertReviewItemDO::getFirstAlertTime)
                .reduce(null, SupervisionAlertReviewMapperStore::min);
        LocalDateTime endTime = items.stream()
                .map(SupervisionAlertReviewItemDO::getLastAlertTime)
                .reduce(null, SupervisionAlertReviewMapperStore::max);
        caseDO.setCameraIds(joinCsv(cameraIds))
                .setStartTime(startTime)
                .setEndTime(endTime);
    }

    private ReviewCaseView toCaseView(SupervisionAlertReviewCaseDO caseDO) {
        List<Long> reviewItemIds = reviewCaseItemMapper.selectByCaseId(caseDO.getId()).stream()
                .map(SupervisionAlertReviewCaseItemDO::getReviewItemId)
                .toList();
        return new ReviewCaseView(
                caseDO.getId(),
                caseDO.getCaseNo(),
                caseDO.getTitle(),
                caseDO.getStatus(),
                caseDO.getPrimaryReviewItemId(),
                reviewItemIds,
                splitCsv(caseDO.getCameraIds()),
                caseDO.getStartTime(),
                caseDO.getEndTime(),
                caseDO.getOwnerUserId(),
                caseDO.getNotes(),
                caseDO.getVersion()
        );
    }

    private static String newReviewItemNo() {
        return REVIEW_ITEM_NO_PREFIX + UUID.randomUUID();
    }

    private static String newReviewCaseNo() {
        return REVIEW_CASE_NO_PREFIX + UUID.randomUUID();
    }

    private static String joinLongCsv(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return joinCsv(values.stream().map(String::valueOf).toList());
    }

    private static String joinLongCsv(Iterable<Long> values) {
        if (values == null) {
            return "";
        }
        List<String> normalized = new ArrayList<>();
        for (Long value : values) {
            if (value != null) {
                normalized.add(String.valueOf(value));
            }
        }
        return joinCsv(normalized);
    }

    private static String exportAuditNote(String jobNo, String fileHash, String reason) {
        List<String> values = new ArrayList<>();
        values.add("jobNo=" + jobNo);
        values.add("fileHash=" + fileHash);
        if (hasText(reason)) {
            values.add("reason=" + reason);
        }
        return String.join("; ", values);
    }

    private static String exportDownloadAuditNote(String jobNo, String fileHash, Long operatorUserId, String reason) {
        List<String> values = new ArrayList<>();
        values.add("jobNo=" + jobNo);
        values.add("fileHash=" + fileHash);
        if (operatorUserId != null) {
            values.add("operatorUserId=" + operatorUserId);
        }
        if (hasText(reason)) {
            values.add("reason=" + reason);
        }
        return String.join("; ", values);
    }

    private static String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("review json is invalid", ex);
        }
    }

    private static String writeJsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("review json is invalid", ex);
        }
    }

    private static Map<String, Object> readJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    private static List<Map<String, Object>> readJsonList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return List.copyOf(OBJECT_MAPPER.readValue(value, MAP_LIST_TYPE));
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private static String joinSourceAlertIds(List<String> sourceAlertIds) {
        return String.join(SOURCE_ALERT_ID_SEPARATOR, sourceAlertIds);
    }

    private static String joinCsv(List<String> values) {
        return String.join(CSV_SEPARATOR, values == null ? List.of() : values);
    }

    private static List<String> splitCsv(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>(Arrays.asList(values.split(CSV_SEPARATOR)));
        result.removeIf(String::isBlank);
        return List.copyOf(result);
    }

    private static Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    result.add(String.valueOf(item));
                }
            }
            return List.copyOf(result);
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }

    private static List<Long> splitLongCsv(String values) {
        List<Long> result = new ArrayList<>();
        for (String value : splitCsv(values)) {
            try {
                result.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // Ignore malformed historical values while preserving the rest of the audit trail.
            }
        }
        return List.copyOf(result);
    }

    private static List<String> splitSourceAlertIds(String sourceAlertIds) {
        if (sourceAlertIds == null || sourceAlertIds.isBlank()) {
            return List.of();
        }
        Set<String> result = new LinkedHashSet<>(Arrays.asList(sourceAlertIds.split(SOURCE_ALERT_ID_SEPARATOR)));
        result.removeIf(String::isBlank);
        return List.copyOf(result);
    }

    private static String toText(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Object value, LocalDateTime fallback) {
        if (value instanceof LocalDateTime time) {
            return time;
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToLength(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
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

}
