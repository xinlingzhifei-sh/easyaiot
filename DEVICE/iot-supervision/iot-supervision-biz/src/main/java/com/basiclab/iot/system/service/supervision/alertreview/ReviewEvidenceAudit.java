package com.basiclab.iot.system.service.supervision.alertreview;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Token;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLong;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewEvidenceExport.assertExportJobDownloadable;

/**
 * 证据审计链：案件/条目/条件审计查询、审计哈希链、下载审计记录。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewEvidenceAudit {

    private final AlertReviewDependencies deps;
    private final ReviewItemQuery reviewItemQuery;

    ReviewEvidenceAudit(AlertReviewDependencies deps, ReviewItemQuery reviewItemQuery) {
        this.deps = deps;
        this.reviewItemQuery = reviewItemQuery;
    }

    public List<ReviewEvidenceAuditEntry> getEvidenceAuditTrail(Long reviewCaseId) {
        requirePositive(reviewCaseId, "reviewCaseId");
        List<ReviewEvidenceAuditEntry> auditTrail = new ArrayList<>();
        for (ReviewEvidenceExportJob job : deps.reviewItemStore.listExportJobs(reviewCaseId)) {
            auditTrail.add(exportJobAuditEntry(job));
        }
        for (ReviewCaseTimelineItem timelineItem : deps.reviewItemStore.listCaseTimeline(reviewCaseId)) {
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

    public List<ReviewEvidenceAuditEntry> getReviewItemEvidenceAuditTrail(Long reviewItemId) {
        requirePositive(reviewItemId, "reviewItemId");
        deps.reviewItemStore.findById(reviewItemId)
                .orElseThrow(() -> new IllegalArgumentException("reviewItemId not found: " + reviewItemId));
        List<ReviewEvidenceAuditEntry> auditTrail = deps.reviewItemStore.listMediaAccessAuditsByReviewItem(reviewItemId)
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
        deps.reviewItemStore.listEvidenceAuditExportJobs(query).stream()
                .map(ReviewEvidenceAudit::exportJobAuditEntry)
                .forEach(auditTrail::add);
        for (ReviewCaseTimelineItem timelineItem : deps.reviewItemStore.listEvidenceAuditRecords(query)) {
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

    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo, Long operatorUserId, String reason) {
        return recordEvidenceDownload(jobNo, operatorUserId, reason, null);
    }

    public ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                           Long operatorUserId,
                                                           String reason,
                                                           List<String> allowedCameraIds) {
        requireText(jobNo, "jobNo");
        ReviewEvidenceExportJob job = deps.reviewItemStore.findExportJobByNo(jobNo)
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
        deps.reviewItemStore.recordMediaAccessAudit(
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

    ReviewEvidenceAuditEntry recordEvidenceDownload(String jobNo,
                                                            Long operatorUserId,
                                                            String reason,
                                                            List<String> allowedCameraIds,
                                                            String downloadFileHash,
                                                            Map<String, Object> downloadMetadata) {
        requireText(jobNo, "jobNo");
        requireText(downloadFileHash, "downloadFileHash");
        ReviewEvidenceExportJob job = deps.reviewItemStore.findExportJobByNo(jobNo)
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + jobNo));
        assertExportJobDownloadable(job, LocalDateTime.now());
        ReviewEvidenceAuditEntry auditEntry = deps.reviewItemStore.recordEvidenceDownload(
                jobNo,
                operatorUserId,
                reason,
                LocalDateTime.now(),
                downloadFileHash,
                downloadMetadata
        );
        return enrichDownloadAuditEntry(auditEntry, job);
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
                ? deps.reviewItemStore.findExportJobByNo(jobNo)
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
                : deps.reviewItemStore.findById(timelineItem.reviewItemId());
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

    static Map<String, Object> parseAuditNote(String note) {
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
}
