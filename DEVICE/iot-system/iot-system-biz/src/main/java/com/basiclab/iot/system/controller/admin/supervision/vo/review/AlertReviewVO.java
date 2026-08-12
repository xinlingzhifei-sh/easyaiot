package com.basiclab.iot.system.controller.admin.supervision.vo.review;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageSegment;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummaryConfirmation;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseTimelineItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceExportPackage;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVerificationReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewDetailStreamItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewIntegrationSmokeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemAggregate;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewManifestVerification;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewMediaAccessAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewPlaybackAccess;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationsReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgement;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRecordStorageSyncResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReconciliationResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimeHealthReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuntimePatrolResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleGeometryEvaluation;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleReplayResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewRuleView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSegmentView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticHit;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEvaluation;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticTriggerResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticReindexJob;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewUserStatusView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewWorkbenchSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RuleSuggestionPreview;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RuleSuggestionStat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class AlertReviewVO {

    private AlertReviewVO() {
    }

    @Schema(description = "Alert review clue ingest request")
    @Data
    public static class ClueIngestReqVO {

        @NotBlank(message = "sourceSystem must not be blank")
        private String sourceSystem;

        @NotBlank(message = "sourceAlertId must not be blank")
        private String sourceAlertId;

        private String ruleCode;
        private String sourceAlertType;

        @NotNull(message = "alertTime must not be null")
        private LocalDateTime alertTime;

        private String deviceId;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private Integer staySeconds;
        private String snapshotUri;
        private String recordUri;
        private String sourcePayloadHash;
        private List<String> labels;
        private List<String> zones;
        private List<String> objectIds;
        private Double confidence;
        private List<Double> bbox;
        private String correlationId;
        private List<String> verifiedObjects;
        private LocalDateTime thumbTime;
        private List<String> audioLabels;
        private Map<String, Object> motionMetadata;

    }

    @Schema(description = "Alert review operation request")
    @Data
    public static class OperationReqVO {

        private Long reviewerUserId;
        private String reason;

    }

    @Schema(description = "Alert review user status request")
    @Data
    public static class UserStatusReqVO {

        private Long userId;
        private Boolean hasBeenReviewed;

    }

    @Schema(description = "Alert review lifecycle update request")
    @Data
    public static class LifecycleReqVO {

        private String lifecycleState;
        private LocalDateTime happenedAt;
        private List<String> objectIds;
        private List<String> labels;
        private List<String> zones;
        private List<Double> bbox;
        private Map<String, Object> motionMetadata;
        private String recordUri;

    }

    @Schema(description = "Alert review record coverage segment request")
    @Data
    public static class CoverageSegmentReqVO {

        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer motion;
        private String recordUri;
        private Integer objects;
        private Map<String, Object> metadata;

        public RecordCoverageSegment toSegment() {
            return new RecordCoverageSegment(status, startTime, endTime, motion, recordUri, objects, metadata);
        }

    }

    @Schema(description = "Alert review record storage sync request")
    @Data
    public static class RecordStorageSyncReqVO {

        private Long operatorUserId;
        private List<CoverageSegmentReqVO> coverageSegments;

    }

    @Schema(description = "Alert review user status response")
    @Data
    public static class UserStatusRespVO {

        private Long reviewItemId;
        private Long userId;
        private Boolean hasBeenReviewed;
        private LocalDateTime reviewedAt;

        public static UserStatusRespVO from(ReviewUserStatusView view) {
            UserStatusRespVO respVO = new UserStatusRespVO();
            respVO.setReviewItemId(view.reviewItemId());
            respVO.setUserId(view.userId());
            respVO.setHasBeenReviewed(view.hasBeenReviewed());
            respVO.setReviewedAt(view.reviewedAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review rule suggestion status request")
    @Data
    public static class RuleSuggestionStatusReqVO {

        private Long reviewerUserId;
        private String status;
        private String note;

    }

    @Schema(description = "Alert review rule suggestion preview response")
    @Data
    public static class RuleSuggestionPreviewRespVO {

        private Long reviewItemId;
        private Map<String, Object> currentRule;
        private Map<String, Object> proposedRule;
        private List<String> diff;
        private List<String> affectedReviewItemNos;

        public static RuleSuggestionPreviewRespVO from(RuleSuggestionPreview preview) {
            RuleSuggestionPreviewRespVO respVO = new RuleSuggestionPreviewRespVO();
            respVO.setReviewItemId(preview.reviewItemId());
            respVO.setCurrentRule(preview.currentRule());
            respVO.setProposedRule(preview.proposedRule());
            respVO.setDiff(preview.diff());
            respVO.setAffectedReviewItemNos(preview.affectedReviewItemNos());
            return respVO;
        }

    }

    @Schema(description = "Alert review rule save request")
    @Data
    public static class RuleSaveReqVO {

        private Long id;

        @NotBlank(message = "ruleCode must not be blank")
        private String ruleCode;

        @NotBlank(message = "ruleName must not be blank")
        private String ruleName;

        private String sourceSystem;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private Integer minStaySeconds;
        private Integer inertiaFrames;
        private Integer loiteringSeconds;
        private LocalDateTime activeStart;
        private LocalDateTime activeEnd;
        private Boolean enabled;

    }

    @Schema(description = "Alert review item response")
    @Data
    public static class ItemRespVO {

        private Long id;
        private String reviewItemNo;
        private String sourceSystem;
        private String ruleCode;
        private String sourceAlertType;
        private String deviceId;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private LocalDateTime firstAlertTime;
        private LocalDateTime lastAlertTime;
        private Integer alertCount;
        private List<String> sourceAlertIds;
        private String reviewStatus;
        private Long reviewerUserId;
        private LocalDateTime reviewedAt;
        private String ignoreReason;
        private Long eventId;
        private LocalDateTime convertedAt;
        private String recordEvidenceStatus;
        private LocalDateTime recordEvidenceCheckedAt;
        private String recordEvidenceMessage;
        private String eventStatus;
        private String closeCheckStatus;
        private String evidenceStatus;
        private String eventReviewStatus;
        private Boolean inReviewCase;
        private String ruleSuggestionStatus;
        private LocalDateTime ruleSuggestionUpdatedAt;
        private Map<String, Object> reviewData;
        private Map<String, Object> ruleSuggestion;

        public static ItemRespVO from(ReviewItemAggregate item) {
            ItemRespVO respVO = new ItemRespVO();
            respVO.setId(item.id());
            respVO.setReviewItemNo(item.reviewItemNo());
            respVO.setSourceSystem(item.sourceSystem());
            respVO.setRuleCode(item.ruleCode());
            respVO.setSourceAlertType(item.sourceAlertType());
            respVO.setDeviceId(item.deviceId());
            respVO.setCameraId(item.cameraId());
            respVO.setZoneCode(item.zoneCode());
            respVO.setObjectLabel(item.objectLabel());
            respVO.setFirstAlertTime(item.firstAlertTime());
            respVO.setLastAlertTime(item.lastAlertTime());
            respVO.setAlertCount(item.alertCount());
            respVO.setSourceAlertIds(item.sourceAlertIds());
            respVO.setReviewStatus(item.reviewStatus());
            respVO.setReviewerUserId(item.reviewerUserId());
            respVO.setReviewedAt(item.reviewedAt());
            respVO.setIgnoreReason(item.ignoreReason());
            respVO.setEventId(item.eventId());
            respVO.setConvertedAt(item.convertedAt());
            respVO.setRecordEvidenceStatus(item.recordEvidenceStatus());
            respVO.setRecordEvidenceCheckedAt(item.recordEvidenceCheckedAt());
            respVO.setRecordEvidenceMessage(item.recordEvidenceMessage());
            respVO.setEventStatus(item.eventStatus());
            respVO.setCloseCheckStatus(item.closeCheckStatus());
            respVO.setEvidenceStatus(item.evidenceStatus());
            respVO.setEventReviewStatus(item.eventReviewStatus());
            respVO.setInReviewCase(item.inReviewCase());
            respVO.setRuleSuggestionStatus(item.ruleSuggestionStatus());
            respVO.setRuleSuggestionUpdatedAt(item.ruleSuggestionUpdatedAt());
            respVO.setReviewData(item.reviewData());
            respVO.setRuleSuggestion(item.ruleSuggestion());
            return respVO;
        }

    }

    @Schema(description = "Alert review record coverage response")
    @Data
    public static class CoverageRespVO {

        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer motion;
        private String recordUri;
        private Integer objects;
        private Map<String, Object> metadata;

        public static CoverageRespVO from(RecordCoverageSegment segment) {
            CoverageRespVO respVO = new CoverageRespVO();
            respVO.setStatus(segment.status());
            respVO.setStartTime(segment.startTime());
            respVO.setEndTime(segment.endTime());
            respVO.setMotion(segment.motion());
            respVO.setRecordUri(segment.recordUri());
            respVO.setObjects(segment.objects());
            respVO.setMetadata(segment.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review record storage sync response")
    @Data
    public static class RecordStorageSyncRespVO {

        private Long reviewItemId;
        private String syncStatus;
        private Integer availableSegmentCount;
        private Integer missingSegmentCount;
        private Integer motionSegmentCount;
        private Integer availableSeconds;
        private Integer missingSeconds;
        private Integer motionSeconds;
        private List<CoverageRespVO> coverage;
        private LocalDateTime syncedAt;
        private Long operatorUserId;

        public static RecordStorageSyncRespVO from(ReviewRecordStorageSyncResult result) {
            RecordStorageSyncRespVO respVO = new RecordStorageSyncRespVO();
            respVO.setReviewItemId(result.reviewItemId());
            respVO.setSyncStatus(result.syncStatus());
            respVO.setAvailableSegmentCount(result.availableSegmentCount());
            respVO.setMissingSegmentCount(result.missingSegmentCount());
            respVO.setMotionSegmentCount(result.motionSegmentCount());
            respVO.setAvailableSeconds(result.availableSeconds());
            respVO.setMissingSeconds(result.missingSeconds());
            respVO.setMotionSeconds(result.motionSeconds());
            respVO.setCoverage(result.coverage().stream().map(CoverageRespVO::from).toList());
            respVO.setSyncedAt(result.syncedAt());
            respVO.setOperatorUserId(result.operatorUserId());
            return respVO;
        }

    }

    @Schema(description = "Alert review evidence response")
    @Data
    public static class EvidenceRespVO {

        private Long reviewItemId;
        private String sourceAlertId;
        private String materialType;
        private String materialUri;
        private LocalDateTime happenedAt;

        public static EvidenceRespVO from(ReviewEvidenceItem item) {
            EvidenceRespVO respVO = new EvidenceRespVO();
            respVO.setReviewItemId(item.reviewItemId());
            respVO.setSourceAlertId(item.sourceAlertId());
            respVO.setMaterialType(item.materialType());
            respVO.setMaterialUri(item.materialUri());
            respVO.setHappenedAt(item.happenedAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review detail stream response")
    @Data
    public static class DetailStreamRespVO {

        private Long reviewItemId;
        private String sourceAlertId;
        private String cameraId;
        private String zoneCode;
        private String objectId;
        private String label;
        private String lifecycleEvent;
        private LocalDateTime happenedAt;
        private LocalDateTime seekTime;
        private List<Double> bbox;
        private List<Map<String, Object>> path;
        private String materialType;
        private String materialUri;
        private Map<String, Object> metadata;
        private LocalDateTime recordStartTime;
        private Integer playbackOffsetSeconds;

        public static DetailStreamRespVO from(ReviewDetailStreamItem item) {
            DetailStreamRespVO respVO = new DetailStreamRespVO();
            respVO.setReviewItemId(item.reviewItemId());
            respVO.setSourceAlertId(item.sourceAlertId());
            respVO.setCameraId(item.cameraId());
            respVO.setZoneCode(item.zoneCode());
            respVO.setObjectId(item.objectId());
            respVO.setLabel(item.label());
            respVO.setLifecycleEvent(item.lifecycleEvent());
            respVO.setHappenedAt(item.happenedAt());
            respVO.setSeekTime(item.seekTime());
            respVO.setBbox(item.bbox());
            respVO.setPath(item.path());
            respVO.setMaterialType(item.materialType());
            respVO.setMaterialUri(item.materialUri());
            respVO.setMetadata(item.metadata());
            respVO.setRecordStartTime(item.recordStartTime());
            respVO.setPlaybackOffsetSeconds(item.playbackOffsetSeconds());
            return respVO;
        }

    }

    @Schema(description = "Alert review segment response")
    @Data
    public static class ReviewSegmentRespVO {

        private Long reviewItemId;
        private String segmentId;
        private String cameraId;
        private String severity;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<String> objectIds;
        private List<String> zones;
        private List<String> sourceAlertIds;
        private List<Map<String, Object>> events;
        private Map<String, Object> metadata;

        public static ReviewSegmentRespVO from(ReviewSegmentView view) {
            ReviewSegmentRespVO respVO = new ReviewSegmentRespVO();
            respVO.setReviewItemId(view.reviewItemId());
            respVO.setSegmentId(view.segmentId());
            respVO.setCameraId(view.cameraId());
            respVO.setSeverity(view.severity());
            respVO.setStatus(view.status());
            respVO.setStartTime(view.startTime());
            respVO.setEndTime(view.endTime());
            respVO.setObjectIds(view.objectIds());
            respVO.setZones(view.zones());
            respVO.setSourceAlertIds(view.sourceAlertIds());
            respVO.setEvents(view.events());
            respVO.setMetadata(view.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review to event response")
    @Data
    public static class ToEventRespVO {

        private Long reviewItemId;
        private String reviewStatus;
        private Long eventId;
        private boolean reused;

        public static ToEventRespVO from(ReviewToEventResult result) {
            ToEventRespVO respVO = new ToEventRespVO();
            respVO.setReviewItemId(result.reviewItemId());
            respVO.setReviewStatus(result.reviewStatus());
            respVO.setEventId(result.eventId());
            respVO.setReused(result.reused());
            return respVO;
        }

    }

    @Schema(description = "Alert review case create request")
    @Data
    public static class CaseCreateReqVO {

        private String title;
        private Long primaryReviewItemId;
        private List<Long> reviewItemIds;
        private Long ownerUserId;
        private String notes;

    }

    @Schema(description = "Alert review case owner request")
    @Data
    public static class CaseOwnerReqVO {

        private Long ownerUserId;
        private Long operatorUserId;
        private String notes;
        private Integer expectedVersion;
        private String operationId;

    }

    @Schema(description = "Alert review case close request")
    @Data
    public static class CaseCloseReqVO {

        private Long operatorUserId;
        private String notes;
        private Integer expectedVersion;
        private String operationId;

    }

    @Schema(description = "Alert review case merge request")
    @Data
    public static class CaseMergeReqVO {

        private Long sourceReviewCaseId;
        private Long operatorUserId;
        private String notes;
        private Integer targetExpectedVersion;
        private Integer sourceExpectedVersion;
        private String operationId;

    }

    @Schema(description = "Alert review case split request")
    @Data
    public static class CaseSplitReqVO {

        private List<Long> reviewItemIds;
        private String title;
        private Long ownerUserId;
        private Long operatorUserId;
        private String notes;
        private Integer sourceExpectedVersion;
        private String operationId;

    }

    @Schema(description = "Alert review case response")
    @Data
    public static class CaseRespVO {

        private Long id;
        private String caseNo;
        private String title;
        private String status;
        private Long primaryReviewItemId;
        private List<Long> reviewItemIds;
        private List<String> cameraIds;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Long ownerUserId;
        private String notes;
        private Integer version;

        public static CaseRespVO from(ReviewCaseView view) {
            CaseRespVO respVO = new CaseRespVO();
            respVO.setId(view.id());
            respVO.setCaseNo(view.caseNo());
            respVO.setTitle(view.title());
            respVO.setStatus(view.status());
            respVO.setPrimaryReviewItemId(view.primaryReviewItemId());
            respVO.setReviewItemIds(view.reviewItemIds());
            respVO.setCameraIds(view.cameraIds());
            respVO.setStartTime(view.startTime());
            respVO.setEndTime(view.endTime());
            respVO.setOwnerUserId(view.ownerUserId());
            respVO.setNotes(view.notes());
            respVO.setVersion(view.version());
            return respVO;
        }

    }

    @Schema(description = "Alert review case merge response")
    @Data
    public static class CaseMergeRespVO {

        private CaseRespVO targetCase;
        private CaseRespVO sourceCase;

        public static CaseMergeRespVO from(ReviewCaseMergeResult result) {
            CaseMergeRespVO respVO = new CaseMergeRespVO();
            respVO.setTargetCase(CaseRespVO.from(result.targetCase()));
            respVO.setSourceCase(CaseRespVO.from(result.sourceCase()));
            return respVO;
        }

    }

    @Schema(description = "Alert review case split response")
    @Data
    public static class CaseSplitRespVO {

        private CaseRespVO sourceCase;
        private CaseRespVO newCase;

        public static CaseSplitRespVO from(ReviewCaseSplitResult result) {
            CaseSplitRespVO respVO = new CaseSplitRespVO();
            respVO.setSourceCase(CaseRespVO.from(result.sourceCase()));
            respVO.setNewCase(CaseRespVO.from(result.newCase()));
            return respVO;
        }

    }

    @Schema(description = "Alert review case timeline response")
    @Data
    public static class CaseTimelineRespVO {

        private Long reviewCaseId;
        private Long reviewItemId;
        private String cameraId;
        private String sourceAlertId;
        private String materialType;
        private String materialUri;
        private LocalDateTime happenedAt;
        private LocalDateTime recordStartTime;
        private Integer playbackOffsetSeconds;
        private String actionNote;

        public static CaseTimelineRespVO from(ReviewCaseTimelineItem item) {
            CaseTimelineRespVO respVO = new CaseTimelineRespVO();
            respVO.setReviewCaseId(item.reviewCaseId());
            respVO.setReviewItemId(item.reviewItemId());
            respVO.setCameraId(item.cameraId());
            respVO.setSourceAlertId(item.sourceAlertId());
            respVO.setMaterialType(item.materialType());
            respVO.setMaterialUri(item.materialUri());
            respVO.setHappenedAt(item.happenedAt());
            respVO.setRecordStartTime(item.recordStartTime() != null
                    ? item.recordStartTime()
                    : "record_coverage".equals(item.materialType()) ? item.happenedAt() : null);
            respVO.setPlaybackOffsetSeconds(item.playbackOffsetSeconds());
            respVO.setActionNote(item.actionNote());
            return respVO;
        }

    }

    @Schema(description = "Alert review workbench summary response")
    @Data
    public static class SummaryRespVO {

        private long total;
        private long pendingReview;
        private long reviewedByMe;
        private long missingRecord;
        private long converted;
        private long inReviewCase;

        public static SummaryRespVO from(ReviewWorkbenchSummary summary) {
            SummaryRespVO respVO = new SummaryRespVO();
            respVO.setTotal(summary.total());
            respVO.setPendingReview(summary.pendingReview());
            respVO.setReviewedByMe(summary.reviewedByMe());
            respVO.setMissingRecord(summary.missingRecord());
            respVO.setConverted(summary.converted());
            respVO.setInReviewCase(summary.inReviewCase());
            return respVO;
        }

    }

    @Schema(description = "Alert review runtime health response")
    @Data
    public static class RuntimeHealthRespVO {

        private Integer totalCount;
        private Integer missingRecordCount;
        private Integer staleSemanticIndexCount;
        private Integer failedExportJobCount;
        private Double missingRecordRate;
        private Double exportFailureRate;
        private Integer semanticBacklogCount;
        private Integer repairableCount;
        private Map<String, Integer> recordGapReasons;
        private Map<String, Map<String, Object>> recordGapReasonCatalog;
        private List<String> alerts;
        private LocalDateTime measuredAt;
        private Long operatorUserId;

        public static RuntimeHealthRespVO from(ReviewRuntimeHealthReport report) {
            RuntimeHealthRespVO respVO = new RuntimeHealthRespVO();
            respVO.setTotalCount(report.totalCount());
            respVO.setMissingRecordCount(report.missingRecordCount());
            respVO.setStaleSemanticIndexCount(report.staleSemanticIndexCount());
            respVO.setFailedExportJobCount(report.failedExportJobCount());
            respVO.setMissingRecordRate(report.missingRecordRate());
            respVO.setExportFailureRate(report.exportFailureRate());
            respVO.setSemanticBacklogCount(report.semanticBacklogCount());
            respVO.setRepairableCount(report.repairableCount());
            respVO.setRecordGapReasons(report.recordGapReasons());
            respVO.setRecordGapReasonCatalog(report.recordGapReasonCatalog());
            respVO.setAlerts(report.alerts());
            respVO.setMeasuredAt(report.measuredAt());
            respVO.setOperatorUserId(report.operatorUserId());
            return respVO;
        }

    }

    @Schema(description = "Alert review operations report request")
    @Data
    public static class OperationsReportReqVO {

        private String reportType;
        private String reviewStatus;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private String recordEvidenceStatus;
        private Boolean converted;
        private Boolean inReviewCase;
        private Long reviewerUserId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private Long operatorUserId;

    }

    @Schema(description = "Alert review operations report acknowledgement request")
    @Data
    public static class ReportAcknowledgementReqVO {

        private String reportType;
        private String reviewStatus;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private String recordEvidenceStatus;
        private Boolean converted;
        private Boolean inReviewCase;
        private Long reviewerUserId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
        private Long operatorUserId;
        private String note;

    }

    @Schema(description = "Alert review operations report response")
    @Data
    public static class OperationsReportRespVO {

        private String reportType;
        private List<Long> reviewItemIds;
        private String title;
        private String summary;
        private List<String> evidenceGaps;
        private List<String> recommendedActions;
        private LocalDateTime generatedAt;
        private Long operatorUserId;
        private Map<String, Object> structuredData;
        private Map<String, Object> deliveryPlan;
        private Map<String, Object> acknowledgement;

        public static OperationsReportRespVO from(ReviewOperationsReport report) {
            OperationsReportRespVO respVO = new OperationsReportRespVO();
            respVO.setReportType(report.reportType());
            respVO.setReviewItemIds(report.reviewItemIds());
            respVO.setTitle(report.title());
            respVO.setSummary(report.summary());
            respVO.setEvidenceGaps(report.evidenceGaps());
            respVO.setRecommendedActions(report.recommendedActions());
            respVO.setGeneratedAt(report.generatedAt());
            respVO.setOperatorUserId(report.operatorUserId());
            respVO.setStructuredData(report.structuredData());
            respVO.setDeliveryPlan(report.deliveryPlan());
            respVO.setAcknowledgement(report.acknowledgement());
            return respVO;
        }

    }

    @Schema(description = "Alert review operations report acknowledgement response")
    @Data
    public static class ReportAcknowledgementRespVO {

        private String reportKey;
        private String reportType;
        private String status;
        private Long acknowledgedBy;
        private LocalDateTime acknowledgedAt;
        private String note;
        private boolean duplicate;
        private Map<String, Object> metadata;

        public static ReportAcknowledgementRespVO from(ReviewReportAcknowledgement acknowledgement) {
            ReportAcknowledgementRespVO respVO = new ReportAcknowledgementRespVO();
            respVO.setReportKey(acknowledgement.reportKey());
            respVO.setReportType(acknowledgement.reportType());
            respVO.setStatus(acknowledgement.status());
            respVO.setAcknowledgedBy(acknowledgement.acknowledgedBy());
            respVO.setAcknowledgedAt(acknowledgement.acknowledgedAt());
            respVO.setNote(acknowledgement.note());
            respVO.setDuplicate(acknowledgement.duplicate());
            respVO.setMetadata(acknowledgement.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review runtime reconciliation response")
    @Data
    public static class ReconciliationRespVO {

        private Integer scannedCount;
        private Integer repairedRecordCount;
        private Integer repairedSemanticIndexCount;
        private Integer failedExportJobCount;
        private List<String> findings;
        private RuntimeHealthRespVO healthReport;
        private LocalDateTime reconciledAt;
        private Long operatorUserId;

        public static ReconciliationRespVO from(ReviewReconciliationResult result) {
            ReconciliationRespVO respVO = new ReconciliationRespVO();
            respVO.setScannedCount(result.scannedCount());
            respVO.setRepairedRecordCount(result.repairedRecordCount());
            respVO.setRepairedSemanticIndexCount(result.repairedSemanticIndexCount());
            respVO.setFailedExportJobCount(result.failedExportJobCount());
            respVO.setFindings(result.findings());
            respVO.setHealthReport(RuntimeHealthRespVO.from(result.healthReport()));
            respVO.setReconciledAt(result.reconciledAt());
            respVO.setOperatorUserId(result.operatorUserId());
            return respVO;
        }

    }

    @Schema(description = "Alert review runtime patrol request")
    @Data
    public static class RuntimePatrolReqVO {

        private String reviewStatus;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private String recordEvidenceStatus;
        private Boolean converted;
        private Boolean inReviewCase;
        private Long reviewerUserId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private Long operatorUserId;
        private Boolean repair;
        private Integer maxAttempts;
        private Boolean scheduled;

    }

    @Schema(description = "Alert review runtime patrol response")
    @Data
    public static class RuntimePatrolRespVO {

        private String status;
        private boolean lockAcquired;
        private Integer maxAttempts;
        private Integer attemptCount;
        private RuntimeHealthRespVO healthReport;
        private ReconciliationRespVO reconciliationResult;
        private List<String> alerts;
        private List<String> notifications;
        private List<String> recommendedActions;
        private LocalDateTime executedAt;
        private Long operatorUserId;
        private Map<String, Object> metadata;

        public static RuntimePatrolRespVO from(ReviewRuntimePatrolResult result) {
            RuntimePatrolRespVO respVO = new RuntimePatrolRespVO();
            respVO.setStatus(result.status());
            respVO.setLockAcquired(result.lockAcquired());
            respVO.setMaxAttempts(result.maxAttempts());
            respVO.setAttemptCount(result.attemptCount());
            respVO.setHealthReport(RuntimeHealthRespVO.from(result.healthReport()));
            respVO.setReconciliationResult(result.reconciliationResult() == null
                    ? null
                    : ReconciliationRespVO.from(result.reconciliationResult()));
            respVO.setAlerts(result.alerts());
            respVO.setNotifications(result.notifications());
            respVO.setRecommendedActions(result.recommendedActions());
            respVO.setExecutedAt(result.executedAt());
            respVO.setOperatorUserId(result.operatorUserId());
            respVO.setMetadata(result.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review semantic search hit response")
    @Data
    public static class SemanticHitRespVO {

        private ItemRespVO item;
        private double score;
        private List<String> matchedTerms;
        private String snippet;

        public static SemanticHitRespVO from(ReviewSemanticHit hit) {
            SemanticHitRespVO respVO = new SemanticHitRespVO();
            respVO.setItem(ItemRespVO.from(hit.item()));
            respVO.setScore(hit.score());
            respVO.setMatchedTerms(hit.matchedTerms());
            respVO.setSnippet(hit.snippet());
            return respVO;
        }

    }

    @Schema(description = "Alert review semantic index response")
    @Data
    public static class SemanticIndexRespVO {

        private Long reviewItemId;
        private String cameraId;
        private LocalDateTime firstAlertTime;
        private LocalDateTime lastAlertTime;
        private String embeddingKey;
        private String embeddingModel;
        private String embeddingVectorHash;
        private String indexStatus;
        private Integer retryCount;
        private String lastError;
        private LocalDateTime indexedAt;
        private Integer indexVersion;

        public static SemanticIndexRespVO from(ReviewSemanticIndexEntry entry) {
            SemanticIndexRespVO respVO = new SemanticIndexRespVO();
            respVO.setReviewItemId(entry.reviewItemId());
            respVO.setCameraId(entry.cameraId());
            respVO.setFirstAlertTime(entry.firstAlertTime());
            respVO.setLastAlertTime(entry.lastAlertTime());
            respVO.setEmbeddingKey(entry.embeddingKey());
            respVO.setEmbeddingModel(entry.embeddingModel());
            respVO.setEmbeddingVectorHash(entry.embeddingVectorHash());
            respVO.setIndexStatus(entry.indexStatus());
            respVO.setRetryCount(entry.retryCount());
            respVO.setLastError(entry.lastError());
            respVO.setIndexedAt(entry.indexedAt());
            respVO.setIndexVersion(entry.indexVersion());
            return respVO;
        }

    }

    @Schema(description = "Alert review semantic reindex job response")
    @Data
    public static class SemanticReindexJobRespVO {

        private String jobNo;
        private String status;
        private List<Long> queuedReviewItemIds;
        private LocalDateTime queuedAt;
        private Long operatorUserId;

        public static SemanticReindexJobRespVO from(ReviewSemanticReindexJob job) {
            SemanticReindexJobRespVO respVO = new SemanticReindexJobRespVO();
            respVO.setJobNo(job.jobNo());
            respVO.setStatus(job.status());
            respVO.setQueuedReviewItemIds(job.queuedReviewItemIds());
            respVO.setQueuedAt(job.queuedAt());
            respVO.setOperatorUserId(job.operatorUserId());
            return respVO;
        }

    }

    @Schema(description = "Alert review semantic index evaluation response")
    @Data
    public static class SemanticIndexEvaluationRespVO {

        private Integer totalCount;
        private Integer pendingCount;
        private Integer indexedCount;
        private Integer failedCount;
        private Double coverageRate;
        private List<Long> staleReviewItemIds;
        private List<String> recommendedActions;
        private Double rebuildProgressRate;
        private String backlogAlarmLevel;
        private Integer latestIndexVersion;
        private LocalDateTime evaluatedAt;
        private Long operatorUserId;

        public static SemanticIndexEvaluationRespVO from(ReviewSemanticIndexEvaluation evaluation) {
            SemanticIndexEvaluationRespVO respVO = new SemanticIndexEvaluationRespVO();
            respVO.setTotalCount(evaluation.totalCount());
            respVO.setPendingCount(evaluation.pendingCount());
            respVO.setIndexedCount(evaluation.indexedCount());
            respVO.setFailedCount(evaluation.failedCount());
            respVO.setCoverageRate(evaluation.coverageRate());
            respVO.setStaleReviewItemIds(evaluation.staleReviewItemIds());
            respVO.setRecommendedActions(evaluation.recommendedActions());
            respVO.setRebuildProgressRate(evaluation.rebuildProgressRate());
            respVO.setBacklogAlarmLevel(evaluation.backlogAlarmLevel());
            respVO.setLatestIndexVersion(evaluation.latestIndexVersion());
            respVO.setEvaluatedAt(evaluation.evaluatedAt());
            respVO.setOperatorUserId(evaluation.operatorUserId());
            return respVO;
        }

    }

    @Schema(description = "Alert review semantic trigger evaluation request")
    @Data
    public static class SemanticTriggerEvaluationReqVO {

        @NotBlank(message = "triggerName must not be blank")
        private String triggerName;
        private String cameraId;
        private String triggerType;
        @NotBlank(message = "data must not be blank")
        private String data;
        @DecimalMin(value = "0.0", message = "threshold must be at least 0")
        @DecimalMax(value = "1.0", message = "threshold must not exceed 1")
        private Double threshold;
        private List<String> actions;
        private String reviewStatus;
        private String zoneCode;
        private String objectLabel;
        private String recordEvidenceStatus;
        private Boolean converted;
        private Boolean inReviewCase;
        private Long reviewerUserId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private Long operatorUserId;

    }

    @Schema(description = "Alert review semantic trigger confirmation request")
    @Data
    public static class SemanticTriggerConfirmationReqVO {

        @NotBlank(message = "confirmationStatus must not be blank")
        private String confirmationStatus;
        private String notes;
        private Long operatorUserId;

    }

    @Schema(description = "Alert review semantic trigger evaluation and confirmation response")
    @Data
    public static class SemanticTriggerRespVO {

        private String triggerName;
        private String triggerType;
        private String data;
        private List<Long> matchedReviewItemIds;
        private List<Map<String, Object>> actionPayloads;
        private LocalDateTime evaluatedAt;
        private String inputVersion;
        private String inputHash;
        private String indexGenerationId;
        private Integer latestIndexVersion;
        private List<Map<String, Object>> hitExplanations;
        private List<Map<String, Object>> actionPreviews;
        private String humanConfirmationStatus;
        private String evaluationId;
        private Long confirmedBy;
        private LocalDateTime confirmedAt;
        private Boolean duplicate;

        public static SemanticTriggerRespVO from(ReviewSemanticTriggerResult result) {
            SemanticTriggerRespVO respVO = new SemanticTriggerRespVO();
            respVO.setTriggerName(result.triggerName());
            respVO.setTriggerType(result.triggerType());
            respVO.setData(result.data());
            respVO.setMatchedReviewItemIds(result.matchedReviewItemIds());
            respVO.setActionPayloads(result.actionPayloads());
            respVO.setEvaluatedAt(result.evaluatedAt());
            respVO.setInputVersion(result.inputVersion());
            respVO.setInputHash(result.inputHash());
            respVO.setIndexGenerationId(result.indexGenerationId());
            respVO.setLatestIndexVersion(result.latestIndexVersion());
            respVO.setHitExplanations(result.hitExplanations());
            respVO.setActionPreviews(result.actionPreviews());
            respVO.setHumanConfirmationStatus(result.humanConfirmationStatus());
            respVO.setEvaluationId(result.evaluationId());
            respVO.setConfirmedBy(result.confirmedBy());
            respVO.setConfirmedAt(result.confirmedAt());
            respVO.setDuplicate(result.duplicate());
            return respVO;
        }

    }

    @Schema(description = "Alert review AI summary response")
    @Data
    public static class AiSummaryRespVO {

        private Long reviewCaseId;
        private List<Long> reviewItemIds;
        private String title;
        private String summary;
        private List<String> keyFacts;
        private List<String> evidenceGaps;
        private List<String> recommendedActions;
        private LocalDateTime generatedAt;
        private String generatedBy;
        private Map<String, Object> structuredData;

        public static AiSummaryRespVO from(ReviewAiSummary summary) {
            AiSummaryRespVO respVO = new AiSummaryRespVO();
            respVO.setReviewCaseId(summary.reviewCaseId());
            respVO.setReviewItemIds(summary.reviewItemIds());
            respVO.setTitle(summary.title());
            respVO.setSummary(summary.summary());
            respVO.setKeyFacts(summary.keyFacts());
            respVO.setEvidenceGaps(summary.evidenceGaps());
            respVO.setRecommendedActions(summary.recommendedActions());
            respVO.setGeneratedAt(summary.generatedAt());
            respVO.setGeneratedBy(summary.generatedBy());
            respVO.setStructuredData(summary.structuredData());
            return respVO;
        }

    }

    @Schema(description = "Alert review AI summary confirmation request")
    @Data
    public static class AiSummaryConfirmationReqVO {

        private String confirmationStatus;
        private String notes;
        private Long operatorUserId;

    }

    @Schema(description = "Alert review AI summary confirmation response")
    @Data
    public static class AiSummaryConfirmationRespVO {

        private Long reviewCaseId;
        private String confirmationStatus;
        private String previousConfirmationStatus;
        private String promptHash;
        private String promptVersion;
        private String summaryHash;
        private Long operatorUserId;
        private String notes;
        private LocalDateTime confirmedAt;
        private Boolean duplicate;
        private Map<String, Object> metadata;

        public static AiSummaryConfirmationRespVO from(ReviewAiSummaryConfirmation confirmation) {
            AiSummaryConfirmationRespVO respVO = new AiSummaryConfirmationRespVO();
            respVO.setReviewCaseId(confirmation.reviewCaseId());
            respVO.setConfirmationStatus(confirmation.confirmationStatus());
            respVO.setPreviousConfirmationStatus(confirmation.previousConfirmationStatus());
            respVO.setPromptHash(confirmation.promptHash());
            respVO.setPromptVersion(confirmation.promptVersion());
            respVO.setSummaryHash(confirmation.summaryHash());
            respVO.setOperatorUserId(confirmation.operatorUserId());
            respVO.setNotes(confirmation.notes());
            respVO.setConfirmedAt(confirmation.confirmedAt());
            respVO.setDuplicate(confirmation.duplicate());
            respVO.setMetadata(confirmation.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review evidence export request")
    @Data
    public static class EvidenceExportReqVO {

        private List<Long> reviewItemIds;
        private Long operatorUserId;
        private String format;
        private String reason;
        private Long approverUserId;
        private String approvalNote;
        private List<String> allowedCameraIds;

    }

    @Schema(description = "Alert review evidence export response")
    @Data
    public static class EvidenceExportRespVO {

        private String packageNo;
        private String format;
        private Long reviewCaseId;
        private List<Long> reviewItemIds;
        private List<String> evidenceUris;
        private List<CaseTimelineRespVO> timeline;
        private Map<String, Object> manifest;
        private LocalDateTime generatedAt;

        public static EvidenceExportRespVO from(ReviewEvidenceExportPackage exportPackage) {
            EvidenceExportRespVO respVO = new EvidenceExportRespVO();
            respVO.setPackageNo(exportPackage.packageNo());
            respVO.setFormat(exportPackage.format());
            respVO.setReviewCaseId(exportPackage.reviewCaseId());
            respVO.setReviewItemIds(exportPackage.reviewItemIds());
            respVO.setEvidenceUris(exportPackage.evidenceUris());
            respVO.setTimeline(exportPackage.timeline().stream().map(CaseTimelineRespVO::from).toList());
            respVO.setManifest(exportPackage.manifest());
            respVO.setGeneratedAt(exportPackage.generatedAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review evidence export job response")
    @Data
    public static class EvidenceExportJobRespVO {

        private String jobNo;
        private String status;
        private EvidenceExportRespVO exportPackage;
        private String fileHash;
        private LocalDateTime expiresAt;
        private Long operatorUserId;
        private String reason;
        private List<Long> boundEventIds;
        private LocalDateTime createdAt;

        public static EvidenceExportJobRespVO from(ReviewEvidenceExportJob job) {
            EvidenceExportJobRespVO respVO = new EvidenceExportJobRespVO();
            respVO.setJobNo(job.jobNo());
            respVO.setStatus(job.status());
            respVO.setExportPackage(EvidenceExportRespVO.from(job.exportPackage()));
            respVO.setFileHash(job.fileHash());
            respVO.setExpiresAt(job.expiresAt());
            respVO.setOperatorUserId(job.operatorUserId());
            respVO.setReason(job.reason());
            respVO.setBoundEventIds(job.boundEventIds());
            respVO.setCreatedAt(job.createdAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review evidence manifest verification response")
    @Data
    public static class ManifestVerificationRespVO {

        private String jobNo;
        private boolean valid;
        private String expectedManifestHash;
        private String actualManifestHash;
        private String packageChecksum;
        private List<String> violations;
        private LocalDateTime verifiedAt;

        public static ManifestVerificationRespVO from(ReviewManifestVerification verification) {
            ManifestVerificationRespVO respVO = new ManifestVerificationRespVO();
            respVO.setJobNo(verification.jobNo());
            respVO.setValid(verification.valid());
            respVO.setExpectedManifestHash(verification.expectedManifestHash());
            respVO.setActualManifestHash(verification.actualManifestHash());
            respVO.setPackageChecksum(verification.packageChecksum());
            respVO.setViolations(verification.violations());
            respVO.setVerifiedAt(verification.verifiedAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review evidence package verification response")
    @Data
    public static class EvidenceVerificationRespVO {

        private String jobNo;
        private boolean valid;
        private ManifestVerificationRespVO manifestVerification;
        private Map<String, Object> manifestV2;
        private List<Map<String, Object>> decisionTrail;
        private List<String> replayableReasons;
        private List<EvidenceAuditRespVO> auditTrail;
        private LocalDateTime verifiedAt;
        private Long operatorUserId;

        public static EvidenceVerificationRespVO from(ReviewEvidenceVerificationReport report) {
            EvidenceVerificationRespVO respVO = new EvidenceVerificationRespVO();
            respVO.setJobNo(report.jobNo());
            respVO.setValid(report.valid());
            respVO.setManifestVerification(ManifestVerificationRespVO.from(report.manifestVerification()));
            respVO.setManifestV2(report.manifestV2());
            respVO.setDecisionTrail(report.decisionTrail());
            respVO.setReplayableReasons(report.replayableReasons());
            respVO.setAuditTrail(report.auditTrail().stream().map(EvidenceAuditRespVO::from).toList());
            respVO.setVerifiedAt(report.verifiedAt());
            respVO.setOperatorUserId(report.operatorUserId());
            return respVO;
        }

    }

    @Schema(description = "Alert review integration smoke request")
    @Data
    public static class IntegrationSmokeReqVO {

        private Long operatorUserId;
        private Boolean includeVideoExport;
        private LocalDateTime alertTime;
        private String profile;
        private String deviceId;
        private String cameraId;
        private String zoneCode;
        private String sourceAlertId;
        private List<String> allowedCameraIds;

    }

    @Schema(description = "Alert review integration smoke response")
    @Data
    public static class IntegrationSmokeRespVO {

        private String status;
        private Long reviewItemId;
        private Long eventId;
        private Long reviewCaseId;
        private String exportJobNo;
        private boolean manifestValid;
        private boolean videoExportRequested;
        private boolean videoExportConfirmed;
        private List<String> checkpoints;
        private LocalDateTime executedAt;
        private Long operatorUserId;
        private String profile;
        private RuleRespVO ruleEvidence;

        public static IntegrationSmokeRespVO from(ReviewIntegrationSmokeResult result) {
            IntegrationSmokeRespVO respVO = new IntegrationSmokeRespVO();
            respVO.setStatus(result.status());
            respVO.setReviewItemId(result.reviewItemId());
            respVO.setEventId(result.eventId());
            respVO.setReviewCaseId(result.reviewCaseId());
            respVO.setExportJobNo(result.exportJobNo());
            respVO.setManifestValid(result.manifestValid());
            respVO.setVideoExportRequested(result.videoExportRequested());
            respVO.setVideoExportConfirmed(result.videoExportConfirmed());
            respVO.setCheckpoints(result.checkpoints());
            respVO.setExecutedAt(result.executedAt());
            respVO.setOperatorUserId(result.operatorUserId());
            respVO.setProfile(result.profile());
            if (result.smokeRule() != null) {
                respVO.setRuleEvidence(RuleRespVO.from(result.smokeRule()));
            }
            return respVO;
        }

    }

    @Schema(description = "Alert review evidence download audit request")
    @Data
    public static class EvidenceDownloadAuditReqVO {

        private Long operatorUserId;
        private String reason;
        private List<String> allowedCameraIds;

    }

    @Schema(description = "Alert review evidence audit response")
    @Data
    public static class EvidenceAuditRespVO {

        private Long reviewCaseId;
        private Long reviewItemId;
        private String actionType;
        private String jobNo;
        private String fileHash;
        private Long operatorUserId;
        private String actionNote;
        private List<String> evidenceUris;
        private List<Long> boundEventIds;
        private LocalDateTime happenedAt;
        private Map<String, Object> metadata;

        public static EvidenceAuditRespVO from(ReviewEvidenceAuditEntry entry) {
            EvidenceAuditRespVO respVO = new EvidenceAuditRespVO();
            respVO.setReviewCaseId(entry.reviewCaseId());
            respVO.setReviewItemId(entry.reviewItemId());
            respVO.setActionType(entry.actionType());
            respVO.setJobNo(entry.jobNo());
            respVO.setFileHash(entry.fileHash());
            respVO.setOperatorUserId(entry.operatorUserId());
            respVO.setActionNote(entry.actionNote());
            respVO.setEvidenceUris(entry.evidenceUris());
            respVO.setBoundEventIds(entry.boundEventIds());
            respVO.setHappenedAt(entry.happenedAt());
            respVO.setMetadata(entry.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review media access audit request")
    @Data
    public static class MediaAccessAuditReqVO {

        private Long reviewItemId;
        private Long operatorUserId;
        private String cameraId;
        private String materialUri;
        private String actionType;
        private List<String> allowedCameraIds;
        private String reason;

    }

    @Schema(description = "Alert review media access audit response")
    @Data
    public static class MediaAccessAuditRespVO {

        private Long reviewCaseId;
        private Long reviewItemId;
        private Long operatorUserId;
        private String cameraId;
        private String materialUri;
        private String actionType;
        private String decision;
        private List<String> deniedReasons;
        private LocalDateTime happenedAt;
        private Map<String, Object> metadata;

        public static MediaAccessAuditRespVO from(ReviewMediaAccessAuditEntry entry) {
            MediaAccessAuditRespVO respVO = new MediaAccessAuditRespVO();
            respVO.setReviewCaseId(entry.reviewCaseId());
            respVO.setReviewItemId(entry.reviewItemId());
            respVO.setOperatorUserId(entry.operatorUserId());
            respVO.setCameraId(entry.cameraId());
            respVO.setMaterialUri(entry.materialUri());
            respVO.setActionType(entry.actionType());
            respVO.setDecision(entry.decision());
            respVO.setDeniedReasons(entry.deniedReasons());
            respVO.setHappenedAt(entry.happenedAt());
            respVO.setMetadata(entry.metadata());
            return respVO;
        }

    }

    @Schema(description = "Alert review playback URL preparation response")
    @Data
    public static class PlaybackAccessRespVO {

        private Long reviewCaseId;
        private Long reviewItemId;
        private Long operatorUserId;
        private String cameraId;
        private String materialUri;
        private String playbackUrl;
        private String decision;
        private List<String> deniedReasons;
        private MediaAccessAuditRespVO audit;

        public static PlaybackAccessRespVO from(ReviewPlaybackAccess access) {
            PlaybackAccessRespVO respVO = new PlaybackAccessRespVO();
            respVO.setReviewCaseId(access.reviewCaseId());
            respVO.setReviewItemId(access.reviewItemId());
            respVO.setOperatorUserId(access.operatorUserId());
            respVO.setCameraId(access.cameraId());
            respVO.setMaterialUri(access.materialUri());
            respVO.setPlaybackUrl(access.playbackUrl());
            respVO.setDecision(access.decision());
            respVO.setDeniedReasons(access.deniedReasons());
            respVO.setAudit(access.audit() == null ? null : MediaAccessAuditRespVO.from(access.audit()));
            return respVO;
        }

    }

    @Schema(description = "Alert review rule replay request")
    @Data
    public static class RuleReplayReqVO {

        @NotBlank(message = "ruleCode must not be blank")
        private String ruleCode;
        private String sourceSystem;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private Integer minStaySeconds;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private Long operatorUserId;

    }

    @Schema(description = "Alert review rule replay response")
    @Data
    public static class RuleReplayRespVO {

        private String ruleCode;
        private List<Long> evaluatedReviewItemIds;
        private Integer evaluatedCount;
        private Integer matchBeforeCount;
        private Integer matchAfterCount;
        private Integer falsePositiveBeforeCount;
        private Double falsePositiveBeforeRate;
        private Double falsePositiveAfterRate;
        private List<String> recommendedActions;
        private Map<String, Object> scope;
        private Map<String, Object> report;
        private LocalDateTime replayedAt;

        public static RuleReplayRespVO from(ReviewRuleReplayResult result) {
            RuleReplayRespVO respVO = new RuleReplayRespVO();
            respVO.setRuleCode(result.ruleCode());
            respVO.setEvaluatedReviewItemIds(result.evaluatedReviewItemIds());
            respVO.setEvaluatedCount(result.evaluatedCount());
            respVO.setMatchBeforeCount(result.matchBeforeCount());
            respVO.setMatchAfterCount(result.matchAfterCount());
            respVO.setFalsePositiveBeforeCount(result.falsePositiveBeforeCount());
            respVO.setFalsePositiveBeforeRate(result.falsePositiveBeforeRate());
            respVO.setFalsePositiveAfterRate(result.falsePositiveAfterRate());
            respVO.setRecommendedActions(result.recommendedActions());
            respVO.setScope(result.scope());
            respVO.setReport(result.report());
            respVO.setReplayedAt(result.replayedAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review rule geometry evaluation request")
    @Data
    public static class RuleGeometryReqVO {

        @NotBlank(message = "ruleCode must not be blank")
        private String ruleCode;
        private String cameraId;
        private String zoneCode;
        private List<List<Double>> polygon;
        private List<Double> bbox;
        private String objectLabel;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private Long operatorUserId;

    }

    @Schema(description = "Alert review rule geometry evaluation response")
    @Data
    public static class RuleGeometryRespVO {

        private String geometryType;
        private boolean inside;
        private List<Double> evaluatedPoint;
        private String zoneCode;
        private List<Long> replayedReviewItemIds;
        private Map<String, Object> ruleVersion;
        private List<String> consistencyChecks;
        private LocalDateTime evaluatedAt;
        private List<Map<String, Object>> matchTraces;

        public static RuleGeometryRespVO from(ReviewRuleGeometryEvaluation evaluation) {
            RuleGeometryRespVO respVO = new RuleGeometryRespVO();
            respVO.setGeometryType(evaluation.geometryType());
            respVO.setInside(evaluation.inside());
            respVO.setEvaluatedPoint(evaluation.evaluatedPoint());
            respVO.setZoneCode(evaluation.zoneCode());
            respVO.setReplayedReviewItemIds(evaluation.replayedReviewItemIds());
            respVO.setRuleVersion(evaluation.ruleVersion());
            respVO.setConsistencyChecks(evaluation.consistencyChecks());
            respVO.setEvaluatedAt(evaluation.evaluatedAt());
            respVO.setMatchTraces(evaluation.matchTraces());
            return respVO;
        }

    }

    @Schema(description = "Alert review rule suggestion stat response")
    @Data
    public static class RuleSuggestionStatRespVO {

        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private String action;
        private long falsePositiveCount;
        private long totalCount;
        private Double falsePositiveRate;
        private List<String> candidateActions;
        private LocalDateTime lastSeenAt;

        public static RuleSuggestionStatRespVO from(RuleSuggestionStat stat) {
            RuleSuggestionStatRespVO respVO = new RuleSuggestionStatRespVO();
            respVO.setCameraId(stat.cameraId());
            respVO.setZoneCode(stat.zoneCode());
            respVO.setObjectLabel(stat.objectLabel());
            respVO.setAction(stat.action());
            respVO.setFalsePositiveCount(stat.falsePositiveCount());
            respVO.setTotalCount(stat.totalCount());
            respVO.setFalsePositiveRate(stat.falsePositiveRate());
            respVO.setCandidateActions(stat.candidateActions());
            respVO.setLastSeenAt(stat.lastSeenAt());
            return respVO;
        }

    }

    @Schema(description = "Alert review rule response")
    @Data
    public static class RuleRespVO {

        private Long id;
        private String ruleCode;
        private String ruleName;
        private String sourceSystem;
        private String cameraId;
        private String zoneCode;
        private String objectLabel;
        private Integer minStaySeconds;
        private Integer inertiaFrames;
        private Integer loiteringSeconds;
        private LocalDateTime activeStart;
        private LocalDateTime activeEnd;
        private Boolean enabled;

        public static RuleRespVO from(ReviewRuleView rule) {
            RuleRespVO respVO = new RuleRespVO();
            respVO.setId(rule.id());
            respVO.setRuleCode(rule.ruleCode());
            respVO.setRuleName(rule.ruleName());
            respVO.setSourceSystem(rule.sourceSystem());
            respVO.setCameraId(rule.cameraId());
            respVO.setZoneCode(rule.zoneCode());
            respVO.setObjectLabel(rule.objectLabel());
            respVO.setMinStaySeconds(rule.minStaySeconds());
            respVO.setInertiaFrames(rule.inertiaFrames());
            respVO.setLoiteringSeconds(rule.loiteringSeconds());
            respVO.setActiveStart(rule.activeStart());
            respVO.setActiveEnd(rule.activeEnd());
            respVO.setEnabled(rule.enabled());
            return respVO;
        }

    }

}
