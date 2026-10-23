package com.basiclab.iot.system.service.supervision.alertreview;

import com.basiclab.iot.common.utils.json.JsonUtils;

import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_SNAPSHOT;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.boundedPositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.firstText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.immutableNonNullMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.isEvidenceMedia;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requirePositive;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.requireText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.reviewItemIdsFromTimeline;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.sha256Token;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLocalDateTime;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toLong;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toStringObjectMap;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewEvidenceManifests.expectedManifestHash;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewEvidenceManifests.exportFileHash;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.hasCompleteVideoExportProvenance;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.isSha256Hash;
import static com.basiclab.iot.system.service.supervision.alertreview.ReviewVideoExportPlanner.sha256HashesEqual;

/**
 * 证据导出：导出任务创建、工作队列认领与重试、导出包组装、证据包下载与多摄像头打包。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewEvidenceExport {

    private final AlertReviewDependencies deps;
    private final ReviewCaseManagement reviewCaseManagement;
    private final ReviewEvidenceAudit reviewEvidenceAudit;
    private final ReviewEvidenceManifests reviewEvidenceManifests;
    private final ReviewItemQuery reviewItemQuery;
    private final ReviewMediaScopeGuard reviewMediaScopeGuard;
    private final ReviewVideoExportPlanner reviewVideoExportPlanner;

    ReviewEvidenceExport(AlertReviewDependencies deps, ReviewCaseManagement reviewCaseManagement, ReviewEvidenceAudit reviewEvidenceAudit, ReviewEvidenceManifests reviewEvidenceManifests, ReviewItemQuery reviewItemQuery, ReviewMediaScopeGuard reviewMediaScopeGuard, ReviewVideoExportPlanner reviewVideoExportPlanner) {
        this.deps = deps;
        this.reviewCaseManagement = reviewCaseManagement;
        this.reviewEvidenceAudit = reviewEvidenceAudit;
        this.reviewEvidenceManifests = reviewEvidenceManifests;
        this.reviewItemQuery = reviewItemQuery;
        this.reviewMediaScopeGuard = reviewMediaScopeGuard;
        this.reviewVideoExportPlanner = reviewVideoExportPlanner;
    }

    private static final int DEFAULT_EXPORT_JOB_EXPIRES_DAYS = 7;
    private static final int DEFAULT_EXPORT_WORKER_LIMIT = 20;
    private static final int MAX_EXPORT_WORKER_LIMIT = 100;
    private static final int EXPORT_WORKER_RECLAIM_MINUTES = 30;
    private static final Set<String> EVIDENCE_EXPORT_FORMATS = Set.of(
            "manifest", "mp4", "mkv", "mov", "avi", "webm", "ts"
    );

    public ReviewEvidenceExportPackage exportReviewEvidence(ReviewEvidenceExportCommand command) {
        return buildReviewEvidenceExportPackage(command, true);
    }

    public ReviewEvidenceExportJob createReviewEvidenceExportJob(ReviewEvidenceExportCommand command) {
        ReviewEvidenceExportPackage exportPackage = buildReviewEvidenceExportPackage(command, false);
        LocalDateTime createdAt = exportPackage.generatedAt();
        return deps.reviewItemStore.createExportJob(
                exportPackage,
                command.operatorUserId(),
                command.reason(),
                reviewItemQuery.boundEventIds(exportPackage.reviewItemIds()),
                exportFileHash(exportPackage),
                createdAt.plusDays(DEFAULT_EXPORT_JOB_EXPIRES_DAYS),
                createdAt
        );
    }

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
            List<ReviewEvidenceExportJob> claimed = deps.reviewItemStore.claimProcessableExportJobs(
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
                deps.reviewItemStore.completeExportJobClaim(new ReviewEvidenceExportJob(
                        job.jobNo(),
                        EXPORT_JOB_READY,
                        readyPackage,
                        exportFileHash(readyPackage),
                        job.expiresAt(),
                        job.operatorUserId(),
                        job.reason(),
                        reviewItemQuery.boundEventIds(readyPackage.reviewItemIds()),
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
        List<ReviewEvidenceExportJob> jobsAfterRun = deps.reviewItemStore.listAllExportJobs();
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
        return deps.reviewItemStore.completeExportJobClaim(new ReviewEvidenceExportJob(
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
        manifest.put("signature", reviewEvidenceManifests.manifestSignature(manifest, manifestHash, processedAt));
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

    public ReviewEvidenceDownloadArtifact downloadEvidencePackage(ReviewEvidenceDownloadCommand command) {
        Objects.requireNonNull(command, "command");
        requireText(command.jobNo(), "jobNo");
        ReviewEvidenceExportJob job = deps.reviewItemStore.findExportJobByNo(command.jobNo())
                .orElseThrow(() -> new IllegalArgumentException("export job not found: " + command.jobNo()));
        assertExportJobDownloadable(job, LocalDateTime.now());
        reviewMediaScopeGuard.enforceMediaAccessScope(
                job.exportPackage().reviewCaseId(),
                job.exportPackage().timeline(),
                reviewItemQuery.loadReviewItems(job.exportPackage().reviewItemIds()),
                command.operatorUserId(),
                "download",
                command.allowedCameraIds(),
                command.reason()
        );
        ReviewManifestVerification manifestVerification = reviewEvidenceManifests.verifyEvidenceExportManifest(
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
                ReviewEvidenceAuditEntry auditEntry = reviewEvidenceAudit.recordEvidenceDownload(
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
            downloadedArtifacts.forEach(ReviewEvidenceExport::closeDownloadArtifactQuietly);
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
        ReviewEvidenceDownloadArtifact downloaded = deps.videoEvidenceExportProvider.download(
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
                        reviewEvidenceAudit.getEvidenceAuditTrail(job.exportPackage().reviewCaseId())));
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
            artifacts.forEach(ReviewEvidenceExport::closeDownloadArtifactQuietly);
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

    static String sha256File(Path file) {
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

    static void assertExportJobDownloadable(ReviewEvidenceExportJob job, LocalDateTime now) {
        if (!EXPORT_JOB_READY.equals(job.status())) {
            throw new IllegalStateException("export job is not ready for download: " + job.jobNo()
                    + " status=" + job.status());
        }
        if (isExportJobExpired(job, now)) {
            throw new IllegalStateException("export job expired: " + job.jobNo());
        }
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
        List<ReviewCaseTimelineItem> timeline = reviewCaseManagement.getReviewCaseTimeline(
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
                .map(id -> reviewItemQuery.withEventProjection(deps.reviewItemStore.findById(id).orElse(null)))
                .filter(Objects::nonNull)
                .toList();
        String format = normalizeEvidenceExportFormat(command.format());
        if (!EVIDENCE_EXPORT_FORMATS.contains(format)) {
            throw new IllegalArgumentException("unsupported_export_format: " + format);
        }
        reviewMediaScopeGuard.enforceMediaAccessScope(
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
                ? reviewVideoExportPlanner.requestVideoExports(command.reviewCaseId(), reviewItemIds, scopedTimeline, format, expiresAt,
                coverageByReviewItemId)
                : List.of();
        List<String> evidenceUris = scopedTimeline.stream()
                .filter(item -> MATERIAL_SNAPSHOT.equals(item.materialType()) || MATERIAL_RECORD.equals(item.materialType()))
                .map(ReviewCaseTimelineItem::materialUri)
                .filter(AlertReviewValues::hasText)
                .distinct()
                .toList();
        evidenceUris = mergeEvidenceUris(evidenceUris, videoExports);
        String requestKey = evidenceExportRequestKey(command, reviewItemIds, format, approverUserId);
        Map<String, Object> manifest = reviewEvidenceManifests.buildEvidenceManifest(
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

    private static String normalizeEvidenceExportFormat(String format) {
        String normalized = hasText(format)
                ? format.trim().toLowerCase(Locale.ROOT)
                : "mp4";
        return "video".equals(normalized) ? "mp4" : normalized;
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
}
