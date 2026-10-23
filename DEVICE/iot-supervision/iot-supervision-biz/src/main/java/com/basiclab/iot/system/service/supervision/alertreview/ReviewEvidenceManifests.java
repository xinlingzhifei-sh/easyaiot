package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.system.service.supervision.ReviewEvidenceManifestSigner;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_SNAPSHOT;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Hex;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Token;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLong;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.SHA256_HASH;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.hasCompleteVideoExportProvenance;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.videoExportToMap;

/**
 * 证据清单：清单生成、签名、校验与证据包核验。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewEvidenceManifests {

    private final AlertReviewDependencies deps;
    private final ReviewEvidenceAudit reviewEvidenceAudit;
    private final ReviewItemQuery reviewItemQuery;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;

    ReviewEvidenceManifests(AlertReviewDependencies deps, ReviewEvidenceAudit reviewEvidenceAudit, ReviewItemQuery reviewItemQuery, ReviewMediaScopeGuard reviewMediaScopeGuard) {
        this.deps = deps;
        this.reviewEvidenceAudit = reviewEvidenceAudit;
        this.reviewItemQuery = reviewItemQuery;
        this.reviewMediaScopeGuard = reviewMediaScopeGuard;
    }

    public ReviewManifestVerification verifyEvidenceExportManifest(String jobNo) {
        return verifyEvidenceExportManifest(jobNo, null, null);
    }

    public ReviewManifestVerification verifyEvidenceExportManifest(String jobNo,
                                                                   Long operatorUserId,
                                                                   List<String> allowedCameraIds) {
        requireText(jobNo, "jobNo");
        ReviewEvidenceExportJob job = deps.reviewItemStore.findExportJobByNo(jobNo)
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + jobNo));
        reviewMediaScopeGuard.enforceMediaAccessScope(
                job.exportPackage().reviewCaseId(),
                job.exportPackage().timeline(),
                reviewItemQuery.loadReviewItems(job.exportPackage().reviewItemIds()),
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
        ReviewEvidenceManifestSigner.Verification signatureVerification = deps.evidenceManifestSigner.verify(
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

    public ReviewEvidenceVerificationReport verifyEvidencePackage(ReviewEvidenceVerificationCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.jobNo(), "jobNo");
        ReviewManifestVerification manifestVerification = verifyEvidenceExportManifest(
                command.jobNo(),
                command.operatorUserId(),
                command.allowedCameraIds()
        );
        ReviewEvidenceExportJob job = deps.reviewItemStore.findExportJobByNo(command.jobNo())
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + command.jobNo()));
        Map<String, Object> manifest = new LinkedHashMap<>(job.exportPackage().manifest());
        Long reviewCaseId = toLong(manifest.get("reviewCaseId"));
        List<ReviewEvidenceAuditEntry> auditTrail = reviewCaseId == null ? List.of() : reviewEvidenceAudit.getEvidenceAuditTrail(reviewCaseId);
        manifest.put("downloadRecords", auditTrail.stream()
                .filter(entry -> "export_downloaded".equals(entry.actionType()))
                .map(ReviewEvidenceManifests::auditEntryToManifestMap)
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

    Map<String, Object> buildEvidenceManifest(Long reviewCaseId,
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
                .map(ReviewVideoExportPlanner::videoExportToMap)
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
            deps.reviewItemStore.findById(reviewItemId)
                    .map(ReviewEvidenceManifests::decisionTrailRow)
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

    static String expectedManifestHash(Map<String, Object> manifest) {
        Map<String, Object> hashable = new LinkedHashMap<>(manifest == null ? Map.of() : manifest);
        hashable.remove("manifestHash");
        hashable.remove("signature");
        return sha256Token(canonicalValue(hashable));
    }

    Map<String, Object> manifestSignature(Map<String, Object> manifest,
                                                  String manifestHash,
                                                  LocalDateTime signedAt) {
        return deps.evidenceManifestSigner.sign(manifestHash, signedAt);
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

    static String exportFileHash(ReviewEvidenceExportPackage exportPackage) {
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
}
