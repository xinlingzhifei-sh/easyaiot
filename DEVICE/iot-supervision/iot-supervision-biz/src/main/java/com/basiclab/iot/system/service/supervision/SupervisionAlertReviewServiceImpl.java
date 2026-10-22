package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.alertreview.AlertReviewComponents;
import com.basiclab.iot.system.service.supervision.alertreview.AlertReviewDependencies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SupervisionAlertReviewServiceImpl implements SupervisionAlertReviewService {

    private final AlertReviewDependencies deps;
    private final AlertReviewComponents components;

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
        this.deps = new AlertReviewDependencies(
                reviewItemStore,
                reviewRuleStore,
                supervisionEventService,
                recordEvidenceResolver,
                eventProjectionStore,
                recordCoverageResolver,
                reviewIntelligenceProvider,
                videoEvidenceExportProvider,
                cameraPermissionResolver,
                aiSummaryRedactionPolicy,
                cameraTopologyResolver,
                evidenceManifestSigner);
        this.components = new AlertReviewComponents(deps);
    }

    @Autowired(required = false)
    public void setRuntimeOutboxPublisher(ReviewRuntimeOutboxPublisher runtimeOutboxPublisher) {
        deps.setRuntimeOutboxPublisher(runtimeOutboxPublisher == null
                ? ReviewRuntimeOutboxPublisher.noop()
                : runtimeOutboxPublisher);
    }

    @Autowired(required = false)
    public void setReviewPlaybackUrlSigner(ReviewPlaybackUrlSigner reviewPlaybackUrlSigner) {
        deps.setReviewPlaybackUrlSigner(reviewPlaybackUrlSigner == null
                ? ReviewPlaybackUrlSigner.passthrough()
                : reviewPlaybackUrlSigner);
    }

    @Autowired(required = false)
    public void setRecordStorageDriftResolver(ReviewRecordStorageDriftResolver recordStorageDriftResolver) {
        deps.setRecordStorageDriftResolver(recordStorageDriftResolver == null
                ? ReviewRecordStorageDriftResolver.unavailable()
                : recordStorageDriftResolver);
    }

    @Value("${yfeieye.video.record-drift-retention-hours:24}")
    public void setRecordStorageDriftRetentionHours(int recordStorageDriftRetentionHours) {
        deps.setRecordStorageDriftRetentionHours(recordStorageDriftRetentionHours);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewItemAggregate ingestClue(AlertClueCommand command) {
        return components.alertClueIngestion().ingestClue(command);
    }

    @Override
    public List<ReviewItemAggregate> listWorkbench(ReviewQuery query) {
        return components.reviewItemQuery().listWorkbench(query);
    }

    @Override
    public List<ReviewEvidenceItem> getTimeline(Long reviewItemId) {
        return components.reviewWorkbench().getTimeline(reviewItemId);
    }

    @Override
    public List<ReviewEvidenceItem> getTimeline(Long reviewItemId,
                                                Long reviewCaseId,
                                                Long operatorUserId,
                                                List<String> allowedCameraIds) {
        return components.reviewWorkbench().getTimeline(reviewItemId, reviewCaseId, operatorUserId, allowedCameraIds);
    }

    @Override
    public List<ReviewDetailStreamItem> getReviewDetailStream(Long reviewItemId) {
        return components.reviewWorkbench().getReviewDetailStream(reviewItemId);
    }

    @Override
    public List<ReviewDetailStreamItem> getReviewDetailStream(Long reviewItemId,
                                                              Long reviewCaseId,
                                                              Long operatorUserId,
                                                              List<String> allowedCameraIds) {
        return components.reviewWorkbench().getReviewDetailStream(reviewItemId, reviewCaseId, operatorUserId, allowedCameraIds);
    }

    @Override
    public ReviewSegmentView getReviewSegment(Long reviewItemId) {
        return components.reviewWorkbench().getReviewSegment(reviewItemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReviewItemAggregate updateReviewLifecycle(ReviewLifecycleCommand command) {
        return components.reviewWorkbench().updateReviewLifecycle(command);
    }

    @Override
    public ReviewItemAggregate retryRecordEvidence(Long reviewItemId) {
        return components.reviewWorkbench().retryRecordEvidence(reviewItemId);
    }

    @Override
    public ReviewItemAggregate markReviewed(ReviewOperationCommand command) {
        return components.reviewWorkbench().markReviewed(command);
    }

    @Override
    public ReviewUserStatusView markUserReviewStatus(ReviewUserStatusCommand command) {
        return components.reviewWorkbench().markUserReviewStatus(command);
    }

    @Override
    public ReviewItemAggregate ignore(ReviewOperationCommand command) {
        return components.reviewWorkbench().ignore(command);
    }

    @Override
    public ReviewItemAggregate markFalsePositive(ReviewOperationCommand command) {
        return components.reviewWorkbench().markFalsePositive(command);
    }

    @Override
    public ReviewItemAggregate updateRuleSuggestionStatus(RuleSuggestionOperationCommand command) {
        return components.reviewRuleGovernance().updateRuleSuggestionStatus(command);
    }

    @Override
    public RuleSuggestionPreview previewRuleSuggestion(Long reviewItemId) {
        return components.reviewRuleGovernance().previewRuleSuggestion(reviewItemId);
    }

    @Override
    public ReviewItemAggregate revertRuleSuggestion(RuleSuggestionOperationCommand command) {
        return components.reviewRuleGovernance().revertRuleSuggestion(command);
    }

    @Override
    public List<RuleSuggestionStat> listRuleSuggestionStats(ReviewQuery query) {
        return components.reviewRuleGovernance().listRuleSuggestionStats(query);
    }

    @Override
    public List<RecordCoverageSegment> getRecordCoverage(Long reviewItemId) {
        return components.reviewRecordCoverage().getRecordCoverage(reviewItemId);
    }

    @Override
    public List<RecordCoverageSegment> getRecordCoverage(Long reviewItemId,
                                                         Long reviewCaseId,
                                                         Long operatorUserId,
                                                         List<String> allowedCameraIds) {
        return components.reviewRecordCoverage().getRecordCoverage(reviewItemId, reviewCaseId, operatorUserId, allowedCameraIds);
    }

    @Override
    public ReviewRecordStorageSyncResult syncRecordStorage(ReviewRecordStorageSyncCommand command) {
        return components.reviewRecordCoverage().syncRecordStorage(command);
    }

    @Override
    public ReviewCaseView createReviewCase(ReviewCaseCommand command) {
        return components.reviewCaseManagement().createReviewCase(command);
    }

    @Override
    public Optional<ReviewCaseView> findReviewCaseByItem(Long reviewItemId) {
        return components.reviewCaseManagement().findReviewCaseByItem(reviewItemId);
    }

    @Override
    public ReviewCaseView addToReviewCase(Long reviewCaseId, Long reviewItemId) {
        return components.reviewCaseManagement().addToReviewCase(reviewCaseId, reviewItemId);
    }

    @Override
    public ReviewCaseView assignReviewCaseOwner(ReviewCaseOwnerCommand command) {
        return components.reviewCaseManagement().assignReviewCaseOwner(command);
    }

    @Override
    public ReviewCaseView closeReviewCase(ReviewCaseOperationCommand command) {
        return components.reviewCaseManagement().closeReviewCase(command);
    }

    @Override
    public ReviewCaseMergeResult mergeReviewCases(ReviewCaseMergeCommand command) {
        return components.reviewCaseManagement().mergeReviewCases(command);
    }

    @Override
    public ReviewCaseSplitResult splitReviewCase(ReviewCaseSplitCommand command) {
        return components.reviewCaseManagement().splitReviewCase(command);
    }

    @Override
    public List<ReviewCaseTimelineItem> getReviewCaseTimeline(Long reviewCaseId) {
        return components.reviewCaseManagement().getReviewCaseTimeline(reviewCaseId);
    }

    @Override
    public List<ReviewCaseTimelineItem> getReviewCaseTimeline(Long reviewCaseId,
                                                              Long operatorUserId,
                                                              List<String> allowedCameraIds) {
        return components.reviewCaseManagement().getReviewCaseTimeline(reviewCaseId, operatorUserId, allowedCameraIds);
    }

    @Override
    public List<ReviewItemAggregate> suggestReviewCaseCandidates(Long reviewItemId) {
        return components.reviewCaseManagement().suggestReviewCaseCandidates(reviewItemId);
    }

    @Override
    public ReviewWorkbenchSummary getWorkbenchSummary(ReviewQuery query) {
        return components.reviewWorkbench().getWorkbenchSummary(query);
    }

    @Override
    public List<ReviewSemanticHit> semanticSearch(ReviewSemanticSearchCommand command) {
        return components.reviewSemanticIntelligence().semanticSearch(command);
    }

    @Override
    public List<ReviewSemanticIndexEntry> reindexSemanticIndex(ReviewQuery query) {
        return components.reviewSemanticIntelligence().reindexSemanticIndex(query);
    }

    @Override
    public ReviewSemanticReindexJob queueSemanticReindex(ReviewSemanticReindexCommand command) {
        return components.reviewSemanticIntelligence().queueSemanticReindex(command);
    }

    @Override
    public ReviewSemanticWorkerRun processSemanticIndexQueue(ReviewSemanticWorkerCommand command) {
        return components.reviewSemanticIntelligence().processSemanticIndexQueue(command);
    }

    @Override
    public ReviewSemanticIndexEvaluation evaluateSemanticIndex(ReviewSemanticIndexEvaluationCommand command) {
        return components.reviewSemanticIntelligence().evaluateSemanticIndex(command);
    }

    @Override
    public ReviewRuntimeHealthReport getReviewRuntimeHealth(ReviewRuntimeHealthCommand command) {
        return components.reviewRuntimeOperations().getReviewRuntimeHealth(command);
    }

    @Override
    public ReviewRuntimePatrolResult runRuntimePatrol(ReviewRuntimePatrolCommand command) {
        return components.reviewRuntimeOperations().runRuntimePatrol(command);
    }

    @Override
    public ReviewRuntimeOutboxPublishResult publishRuntimeOutbox(ReviewRuntimeOutboxPublishCommand command) {
        return components.reviewRuntimeOperations().publishRuntimeOutbox(command);
    }

    @Override
    public ReviewEventReconciliationResult reconcileEventProjections(ReviewEventReconciliationCommand command) {
        return components.alertReviewEventBridge().reconcileEventProjections(command);
    }

    @Override
    public ReviewReconciliationResult reconcileReviewRuntime(ReviewReconciliationCommand command) {
        return components.reviewRuntimeOperations().reconcileReviewRuntime(command);
    }

    @Override
    public ReviewSemanticTriggerResult evaluateSemanticTrigger(ReviewSemanticTriggerCommand command) {
        return components.reviewSemanticIntelligence().evaluateSemanticTrigger(command);
    }

    @Override
    public ReviewSemanticTriggerResult getSemanticTrigger(String evaluationId) {
        return components.reviewSemanticIntelligence().getSemanticTrigger(evaluationId);
    }

    @Override
    public ReviewSemanticTriggerResult confirmSemanticTrigger(ReviewSemanticTriggerConfirmationCommand command) {
        return components.reviewSemanticIntelligence().confirmSemanticTrigger(command);
    }

    @Override
    public ReviewAiSummary summarizeReviewCase(Long reviewCaseId, Long operatorUserId) {
        return components.reviewAiSummaryService().summarizeReviewCase(reviewCaseId, operatorUserId);
    }

    @Override
    public ReviewAiSummaryConfirmation confirmReviewCaseAiSummary(ReviewAiSummaryConfirmationCommand command) {
        return components.reviewAiSummaryService().confirmReviewCaseAiSummary(command);
    }

    @Override
    public ReviewOperationsReport generateReviewReport(ReviewReportCommand command) {
        return components.reviewOperationsReporting().generateReviewReport(command);
    }

    @Override
    public ReviewOperationsReportDelivery scheduleReviewReportDelivery(ReviewReportCommand command) {
        return components.reviewOperationsReporting().scheduleReviewReportDelivery(command);
    }

    @Override
    public ReviewReportAcknowledgement acknowledgeReviewReport(ReviewReportAcknowledgementCommand command) {
        return components.reviewOperationsReporting().acknowledgeReviewReport(command);
    }

    @Override
    public ReviewEvidenceExportPackage exportReviewEvidence(ReviewEvidenceExportCommand command) {
        return components.reviewEvidenceExport().exportReviewEvidence(command);
    }

    @Override
    public ReviewEvidenceExportJob createReviewEvidenceExportJob(ReviewEvidenceExportCommand command) {
        return components.reviewEvidenceExport().createReviewEvidenceExportJob(command);
    }

    @Override
    public ReviewEvidenceExportWorkerRun processEvidenceExportQueue(ReviewEvidenceExportWorkerCommand command) {
        return components.reviewEvidenceExport().processEvidenceExportQueue(command);
    }

    @Override
    public ReviewManifestVerification verifyEvidenceExportManifest(String jobNo) {
        return components.reviewEvidenceManifests().verifyEvidenceExportManifest(jobNo);
    }

    @Override
    public ReviewManifestVerification verifyEvidenceExportManifest(String jobNo,
                                                                   Long operatorUserId,
                                                                   List<String> allowedCameraIds) {
        return components.reviewEvidenceManifests().verifyEvidenceExportManifest(jobNo, operatorUserId, allowedCameraIds);
    }

    @Override
    public ReviewEvidenceVerificationReport verifyEvidencePackage(ReviewEvidenceVerificationCommand command) {
        return components.reviewEvidenceManifests().verifyEvidencePackage(command);
    }

    @Override
    public ReviewIntegrationSmokeResult runIntegrationSmoke(ReviewIntegrationSmokeCommand command) {
        return components.reviewRuntimeOperations().runIntegrationSmoke(command);
    }

    @Override
    public List<ReviewEvidenceAuditEntry> getEvidenceAuditTrail(Long reviewCaseId) {
        return components.reviewEvidenceAudit().getEvidenceAuditTrail(reviewCaseId);
    }

    @Override
    public List<ReviewEvidenceAuditEntry> getReviewItemEvidenceAuditTrail(Long reviewItemId) {
        return components.reviewEvidenceAudit().getReviewItemEvidenceAuditTrail(reviewItemId);
    }

    @Override
    public List<ReviewEvidenceAuditEntry> queryEvidenceAuditTrail(ReviewEvidenceAuditQuery query) {
        return components.reviewEvidenceAudit().queryEvidenceAuditTrail(query);
    }

    @Override
    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo, Long operatorUserId, String reason) {
        return components.reviewEvidenceAudit().recordEvidenceDownload(jobNo, operatorUserId, reason);
    }

    @Override
    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                           Long operatorUserId,
                                                           String reason,
                                                           List<String> allowedCameraIds) {
        return components.reviewEvidenceAudit().recordEvidenceDownload(jobNo, operatorUserId, reason, allowedCameraIds);
    }

    @Override
    public ReviewEvidenceDownloadArtifact downloadEvidencePackage(ReviewEvidenceDownloadCommand command) {
        return components.reviewEvidenceExport().downloadEvidencePackage(command);
    }

    @Override
    public ReviewMediaAccessAuditEntry auditMediaAccess(ReviewMediaAccessCommand command) {
        return components.reviewMediaAccess().auditMediaAccess(command);
    }

    @Override
    public ReviewPlaybackAccess prepareReviewPlayback(ReviewPlaybackCommand command) {
        return components.reviewMediaAccess().prepareReviewPlayback(command);
    }

    @Override
    public ReviewToEventResult convertToEvent(ReviewToEventCommand command) {
        return components.alertReviewEventBridge().convertToEvent(command);
    }

    @Override
    public ReviewRuleReplayResult replayRule(ReviewRuleReplayCommand command) {
        return components.reviewRuleGovernance().replayRule(command);
    }

    @Override
    public ReviewRuleGeometryEvaluation evaluateRuleGeometry(ReviewRuleGeometryCommand command) {
        return components.reviewRuleGovernance().evaluateRuleGeometry(command);
    }

    @Override
    public ReviewRuleView saveRule(ReviewRuleCommand command) {
        return components.reviewRuleGovernance().saveRule(command);
    }

    @Override
    public List<ReviewRuleView> listRules() {
        return components.reviewRuleGovernance().listRules();
    }
}
