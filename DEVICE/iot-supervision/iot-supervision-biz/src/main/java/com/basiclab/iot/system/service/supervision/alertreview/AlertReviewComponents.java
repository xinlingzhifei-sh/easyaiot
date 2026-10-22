package com.basiclab.iot.system.service.supervision.alertreview;

import java.util.Objects;

/**
 * 按依赖顺序创建复核组件。组件之间有两条回边（媒体范围校验 -> 媒体访问审计，录像覆盖 -> 案件时间线），
 * 在全部组件创建完成后绑定；其余依赖均经构造器注入。
 */
public final class AlertReviewComponents {

    private final ReviewItemQuery reviewItemQuery;
    private final AlertClueIngestion alertClueIngestion;
    private final AlertReviewEventBridge alertReviewEventBridge;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;
    private final ReviewRecordCoverage reviewRecordCoverage;
    private final ReviewCaseManagement reviewCaseManagement;
    private final ReviewAiSummaryService reviewAiSummaryService;
    private final ReviewEvidenceAudit reviewEvidenceAudit;
    private final ReviewEvidenceManifests reviewEvidenceManifests;
    private final ReviewVideoExportPlanner reviewVideoExportPlanner;
    private final ReviewEvidenceExport reviewEvidenceExport;
    private final ReviewMediaAccess reviewMediaAccess;
    private final ReviewSemanticIntelligence reviewSemanticIntelligence;
    private final ReviewOperationsReporting reviewOperationsReporting;
    private final ReviewRuleGovernance reviewRuleGovernance;
    private final ReviewRuntimeOperations reviewRuntimeOperations;
    private final ReviewWorkbench reviewWorkbench;

    public AlertReviewComponents(AlertReviewDependencies deps) {
        Objects.requireNonNull(deps, "deps");
        this.reviewItemQuery = new ReviewItemQuery(deps);
        this.alertClueIngestion = new AlertClueIngestion(deps, this.reviewItemQuery);
        this.alertReviewEventBridge = new AlertReviewEventBridge(deps, this.reviewItemQuery);
        this.reviewMediaScopeGuard = new ReviewMediaScopeGuard(deps);
        this.reviewRecordCoverage = new ReviewRecordCoverage(deps, this.reviewMediaScopeGuard);
        this.reviewCaseManagement = new ReviewCaseManagement(deps, this.reviewItemQuery, this.reviewMediaScopeGuard, this.reviewRecordCoverage);
        this.reviewAiSummaryService = new ReviewAiSummaryService(deps, this.reviewCaseManagement, this.reviewItemQuery);
        this.reviewEvidenceAudit = new ReviewEvidenceAudit(deps, this.reviewItemQuery);
        this.reviewEvidenceManifests = new ReviewEvidenceManifests(deps, this.reviewEvidenceAudit, this.reviewItemQuery, this.reviewMediaScopeGuard);
        this.reviewVideoExportPlanner = new ReviewVideoExportPlanner(deps, this.reviewRecordCoverage);
        this.reviewEvidenceExport = new ReviewEvidenceExport(deps, this.reviewCaseManagement, this.reviewEvidenceAudit, this.reviewEvidenceManifests, this.reviewItemQuery, this.reviewMediaScopeGuard, this.reviewVideoExportPlanner);
        this.reviewMediaAccess = new ReviewMediaAccess(deps, this.reviewCaseManagement, this.reviewMediaScopeGuard);
        this.reviewSemanticIntelligence = new ReviewSemanticIntelligence(deps, this.reviewItemQuery);
        this.reviewOperationsReporting = new ReviewOperationsReporting(deps, this.reviewItemQuery, this.reviewSemanticIntelligence);
        this.reviewRuleGovernance = new ReviewRuleGovernance(deps, this.reviewItemQuery);
        this.reviewRuntimeOperations = new ReviewRuntimeOperations(deps, this.alertClueIngestion, this.alertReviewEventBridge, this.reviewCaseManagement, this.reviewEvidenceExport, this.reviewEvidenceManifests, this.reviewItemQuery, this.reviewRecordCoverage, this.reviewRuleGovernance, this.reviewSemanticIntelligence);
        this.reviewWorkbench = new ReviewWorkbench(deps, this.alertClueIngestion, this.reviewItemQuery, this.reviewMediaScopeGuard, this.reviewRuleGovernance);
        this.reviewMediaScopeGuard.bindReviewMediaAccess(this.reviewMediaAccess);
        this.reviewRecordCoverage.bindReviewCaseManagement(this.reviewCaseManagement);
    }

    public AlertClueIngestion alertClueIngestion() {
        return alertClueIngestion;
    }

    public AlertReviewEventBridge alertReviewEventBridge() {
        return alertReviewEventBridge;
    }

    public ReviewAiSummaryService reviewAiSummaryService() {
        return reviewAiSummaryService;
    }

    public ReviewCaseManagement reviewCaseManagement() {
        return reviewCaseManagement;
    }

    public ReviewEvidenceAudit reviewEvidenceAudit() {
        return reviewEvidenceAudit;
    }

    public ReviewEvidenceExport reviewEvidenceExport() {
        return reviewEvidenceExport;
    }

    public ReviewEvidenceManifests reviewEvidenceManifests() {
        return reviewEvidenceManifests;
    }

    public ReviewItemQuery reviewItemQuery() {
        return reviewItemQuery;
    }

    public ReviewMediaAccess reviewMediaAccess() {
        return reviewMediaAccess;
    }

    public ReviewOperationsReporting reviewOperationsReporting() {
        return reviewOperationsReporting;
    }

    public ReviewRecordCoverage reviewRecordCoverage() {
        return reviewRecordCoverage;
    }

    public ReviewRuleGovernance reviewRuleGovernance() {
        return reviewRuleGovernance;
    }

    public ReviewRuntimeOperations reviewRuntimeOperations() {
        return reviewRuntimeOperations;
    }

    public ReviewSemanticIntelligence reviewSemanticIntelligence() {
        return reviewSemanticIntelligence;
    }

    public ReviewWorkbench reviewWorkbench() {
        return reviewWorkbench;
    }
}
