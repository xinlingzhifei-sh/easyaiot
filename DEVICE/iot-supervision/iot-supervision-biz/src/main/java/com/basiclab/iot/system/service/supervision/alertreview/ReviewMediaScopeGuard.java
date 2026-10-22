package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.common.core.context.TenantContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.isEvidenceMedia;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.normalizeCameraScope;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;

/**
 * 媒体访问范围校验：按摄像头授权范围校验条目/案件/证据媒体，并记录访问审计。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewMediaScopeGuard {

    private final AlertReviewDependencies deps;
    private ReviewMediaAccess reviewMediaAccess;

    ReviewMediaScopeGuard(AlertReviewDependencies deps) {
        this.deps = deps;
    }

    void bindReviewMediaAccess(ReviewMediaAccess reviewMediaAccess) {
        this.reviewMediaAccess = reviewMediaAccess;
    }

    void enforceItemMediaReadScope(Long reviewCaseId,
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
        ReviewItemAggregate item = deps.reviewItemStore.findById(reviewItemId)
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

    private void auditAndEnforceMediaAccess(Long reviewCaseId,
                                            ReviewItemAggregate item,
                                            Long operatorUserId,
                                            String actionType,
                                            List<String> allowedCameraIds,
                                            String reason,
                                            String materialUri) {
        ReviewMediaAccessAuditEntry audit = reviewMediaAccess.auditMediaAccess(new ReviewMediaAccessCommand(
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

    record MediaAccessRef(String materialType, String materialUri) {
    }

    void enforceMediaAccessScope(Long reviewCaseId,
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
        deps.reviewItemStore.recordMediaAccessAudit(
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

    List<String> resolveEffectiveAllowedCameraIds(Long reviewCaseId,
                                                          Long operatorUserId,
                                                          String actionType,
                                                          List<String> requestedCameraIds) {
        List<String> normalizedRequested = normalizeCameraScope(requestedCameraIds);
        List<String> serviceAllowed = deps.cameraPermissionResolver.resolveAllowedCameraIds(new ReviewCameraPermissionRequest(
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
}
