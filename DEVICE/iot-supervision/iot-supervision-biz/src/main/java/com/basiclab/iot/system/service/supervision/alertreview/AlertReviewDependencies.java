package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.ReviewAiSummaryRedactionPolicy;
import com.basiclab.iot.system.service.supervision.ReviewEvidenceManifestSigner;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider;
import com.basiclab.iot.system.service.supervision.ReviewPlaybackUrlSigner;
import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;

import java.util.Objects;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;

/**
 * 复核组件共享的外部依赖。前 12 个在构造时校验非空；后 4 个由门面的可选 setter 与 @Value 注入，
 * 默认值与拆分前的 SupervisionAlertReviewServiceImpl 字段默认值一致。
 */
public final class AlertReviewDependencies {

    final ReviewItemStore reviewItemStore;
    final ReviewRuleStore reviewRuleStore;
    final SupervisionEventService supervisionEventService;
    final RecordEvidenceResolver recordEvidenceResolver;
    final EventProjectionStore eventProjectionStore;
    final RecordCoverageResolver recordCoverageResolver;
    final ReviewIntelligenceProvider reviewIntelligenceProvider;
    final VideoEvidenceExportProvider videoEvidenceExportProvider;
    final ReviewCameraPermissionResolver cameraPermissionResolver;
    final ReviewAiSummaryRedactionPolicy aiSummaryRedactionPolicy;
    final ReviewCameraTopologyResolver cameraTopologyResolver;
    final ReviewEvidenceManifestSigner evidenceManifestSigner;
    ReviewRuntimeOutboxPublisher runtimeOutboxPublisher = ReviewRuntimeOutboxPublisher.noop();
    ReviewPlaybackUrlSigner reviewPlaybackUrlSigner = ReviewPlaybackUrlSigner.passthrough();
    ReviewRecordStorageDriftResolver recordStorageDriftResolver = ReviewRecordStorageDriftResolver.unavailable();
    int recordStorageDriftRetentionHours = 24;

    public AlertReviewDependencies(ReviewItemStore reviewItemStore,
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

    public void setRuntimeOutboxPublisher(ReviewRuntimeOutboxPublisher runtimeOutboxPublisher) {
        this.runtimeOutboxPublisher = runtimeOutboxPublisher;
    }

    public void setReviewPlaybackUrlSigner(ReviewPlaybackUrlSigner reviewPlaybackUrlSigner) {
        this.reviewPlaybackUrlSigner = reviewPlaybackUrlSigner;
    }

    public void setRecordStorageDriftResolver(ReviewRecordStorageDriftResolver recordStorageDriftResolver) {
        this.recordStorageDriftResolver = recordStorageDriftResolver;
    }

    public void setRecordStorageDriftRetentionHours(int recordStorageDriftRetentionHours) {
        this.recordStorageDriftRetentionHours = recordStorageDriftRetentionHours;
    }
}
