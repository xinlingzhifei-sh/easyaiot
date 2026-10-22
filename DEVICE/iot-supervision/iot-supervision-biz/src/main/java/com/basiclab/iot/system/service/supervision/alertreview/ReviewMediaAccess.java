package com.basiclab.iot.system.service.supervision.alertreview;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;

/**
 * 媒体访问：媒体访问审计入口与回放地址签发。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewMediaAccess {

    private final AlertReviewDependencies deps;
    private final ReviewCaseManagement reviewCaseManagement;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;

    ReviewMediaAccess(AlertReviewDependencies deps, ReviewCaseManagement reviewCaseManagement, ReviewMediaScopeGuard reviewMediaScopeGuard) {
        this.deps = deps;
        this.reviewCaseManagement = reviewCaseManagement;
        this.reviewMediaScopeGuard = reviewMediaScopeGuard;
    }

    public ReviewMediaAccessAuditEntry auditMediaAccess(ReviewMediaAccessCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        List<ReviewCaseTimelineItem> timeline = command.reviewCaseId() == null
                ? List.of()
                : reviewCaseManagement.getReviewCaseTimeline(command.reviewCaseId());
        boolean itemInCase = command.reviewCaseId() == null
                || timeline.stream().anyMatch(row -> Objects.equals(command.reviewItemId(), row.reviewItemId()));
        boolean mediaInCase = command.reviewCaseId() == null
                || !hasText(command.materialUri())
                || timeline.stream()
                        .filter(row -> Objects.equals(command.reviewItemId(), row.reviewItemId()))
                        .anyMatch(row -> Objects.equals(command.materialUri(), row.materialUri()));
        String actionType = firstText(command.actionType(), "playback");
        List<String> effectiveAllowedCameraIds = reviewMediaScopeGuard.resolveEffectiveAllowedCameraIds(
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
        deps.reviewItemStore.recordMediaAccessAudit(
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

    public ReviewPlaybackAccess prepareReviewPlayback(ReviewPlaybackCommand command) {
        Objects.requireNonNull(command, "command");
        requirePositive(command.reviewItemId(), "reviewItemId");
        ReviewItemAggregate item = deps.reviewItemStore.findById(command.reviewItemId())
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + command.reviewItemId()));
        String materialUri = firstText(command.materialUri(), deps.reviewItemStore.listTimeline(command.reviewItemId()).stream()
                .filter(evidence -> MATERIAL_RECORD.equals(evidence.materialType()))
                .map(ReviewEvidenceItem::materialUri)
                .filter(AlertReviewValues::hasText)
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
                ? deps.reviewPlaybackUrlSigner.signPlaybackUrl(materialUri, item.cameraId())
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
}
