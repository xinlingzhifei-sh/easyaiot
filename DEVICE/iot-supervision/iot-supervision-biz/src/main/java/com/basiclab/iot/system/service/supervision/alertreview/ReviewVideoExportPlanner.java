package com.basiclab.iot.system.service.supervision.alertreview;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.MATERIAL_RECORD;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.hasText;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.max;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.min;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toMapList;
import static com.basiclab.iot.system.service.supervision.alertreview.AlertReviewValues.toText;

/**
 * 视频导出规划：按摄像头规划导出分段、录像 URI 归一化、导出结果的溯源完整性校验。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
public final class ReviewVideoExportPlanner {

    private final AlertReviewDependencies deps;
    private final ReviewRecordCoverage reviewRecordCoverage;

    ReviewVideoExportPlanner(AlertReviewDependencies deps, ReviewRecordCoverage reviewRecordCoverage) {
        this.deps = deps;
        this.reviewRecordCoverage = reviewRecordCoverage;
    }

    static final Pattern SHA256_HASH = Pattern.compile("sha256:[0-9a-fA-F]{64}");
    private static final Pattern CANONICAL_RECORD_URI_PATH = Pattern.compile(
            "/video/record/space/[1-9][0-9]*/video/(.+)$");
    private static final Pattern MINIO_RECORD_DOWNLOAD_PATH = Pattern.compile(
            "/api/v1/buckets/[^/]+/objects/download/?$");

    List<ReviewEvidenceVideoExportResult> requestVideoExports(Long reviewCaseId,
                                                                      List<Long> reviewItemIds,
                                                                      List<ReviewCaseTimelineItem> timeline,
                                                                      String format,
                                                                      LocalDateTime expiresAt,
                                                                      Map<Long, List<RecordCoverageSegment>>
                                                                              coverageByReviewItemId) {
        if (timeline.isEmpty()) {
            if (requiresVideoArtifact(format)) {
                throw new IllegalStateException("video export requested but case timeline is empty");
            }
            return List.of();
        }
        List<ReviewEvidenceVideoExportResult> results = new ArrayList<>();
        List<VideoExportCameraRequestPlan> requestPlans = new ArrayList<>();
        Set<String> requestedSegments = new LinkedHashSet<>();
        Map<String, List<VideoExportSegmentContext>> segmentsByCamera = new LinkedHashMap<>();
        boolean videoArtifactRequired = requiresVideoArtifact(format);
        List<ReviewCaseTimelineItem> orderedTimeline = timeline.stream()
                .sorted(Comparator
                        .comparing(ReviewCaseTimelineItem::happenedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewCaseTimelineItem::reviewItemId, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewCaseTimelineItem::materialUri, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (ReviewCaseTimelineItem timelineItem : orderedTimeline) {
            if (!MATERIAL_RECORD.equals(timelineItem.materialType())
                    || timelineItem.reviewItemId() == null
                    || !reviewItemIds.contains(timelineItem.reviewItemId())
                    || !hasText(timelineItem.materialUri())) {
                continue;
            }
            Optional<ReviewItemAggregate> aggregate = deps.reviewItemStore.findById(timelineItem.reviewItemId());
            if (aggregate.isEmpty()) {
                continue;
            }
            ReviewItemAggregate item = aggregate.get();
            if (!hasText(item.cameraId())) {
                continue;
            }
            segmentsByCamera.computeIfAbsent(item.cameraId(), ignored -> new ArrayList<>())
                    .add(new VideoExportSegmentContext(item, timelineItem));
        }
        for (Map.Entry<String, List<VideoExportSegmentContext>> entry : segmentsByCamera.entrySet()) {
            List<VideoExportSegmentContext> contexts = entry.getValue();
            if (contexts.isEmpty()) {
                continue;
            }
            Map<String, Set<String>> canonicalUrisByObjectName = canonicalRecordUrisByObjectName(contexts);
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests = new ArrayList<>(contexts.size());
            for (VideoExportSegmentContext context : contexts) {
                String normalizedRecordUri = normalizeVideoExportRecordUri(
                        context.timelineItem().materialUri(),
                        canonicalUrisByObjectName
                );
                LocalDateTime itemStartTime = exportStartTime(context.item(), context.timelineItem());
                LocalDateTime itemEndTime = exportEndTime(context.item(), context.timelineItem());
                if (itemStartTime == null || itemEndTime == null || !itemEndTime.isAfter(itemStartTime)) {
                    continue;
                }
                int segmentCountBeforeCoverage = segmentRequests.size();
                boolean hasPhysicalCoverageMatch = false;
                List<RecordCoverageSegment> coverage = coverageByReviewItemId.computeIfAbsent(
                        context.item().id(),
                        ignored -> reviewRecordCoverage.getRecordCoverage(context.item().id())
                );
                for (RecordCoverageSegment coverageSegment : coverage) {
                    if (coverageSegment == null
                            || !hasText(coverageSegment.recordUri())) {
                        continue;
                    }
                    String normalizedCoverageRecordUri = normalizeVideoExportRecordUri(
                            coverageSegment.recordUri(),
                            canonicalUrisByObjectName
                    );
                    if (!normalizedRecordUri.equals(normalizedCoverageRecordUri)) {
                        continue;
                    }
                    if (RECORD_COVERAGE_MISSING.equals(coverageSegment.status())) {
                        continue;
                    }
                    hasPhysicalCoverageMatch = true;
                    if (coverageSegment.startTime() == null
                            || coverageSegment.endTime() == null
                            || !coverageSegment.endTime().isAfter(coverageSegment.startTime())) {
                        continue;
                    }
                    LocalDateTime clipStartTime = max(itemStartTime, coverageSegment.startTime());
                    LocalDateTime clipEndTime = min(itemEndTime, coverageSegment.endTime());
                    if (!clipEndTime.isAfter(clipStartTime)) {
                        continue;
                    }
                    String requestKey = entry.getKey()
                            + "|" + context.item().id()
                            + "|" + normalizedRecordUri
                            + "|" + coverageSegment.startTime()
                            + "|" + coverageSegment.endTime()
                            + "|" + clipStartTime
                            + "|" + clipEndTime;
                    if (!requestedSegments.add(requestKey)) {
                        continue;
                    }
                    segmentRequests.add(new ReviewEvidenceVideoSegmentRequest(
                            context.item().id(),
                            context.timelineItem().sourceAlertId(),
                            normalizedRecordUri,
                            coverageSegment.startTime(),
                            coverageSegment.endTime(),
                            clipStartTime,
                            clipEndTime,
                            segmentRequests.size()
                    ));
                }
                if (segmentRequests.size() == segmentCountBeforeCoverage
                        && !hasPhysicalCoverageMatch
                        && context.timelineItem().recordStartTime() == null
                        && minioRecordObjectName(context.timelineItem().materialUri()).isEmpty()) {
                    String requestKey = entry.getKey()
                            + "|" + context.item().id()
                            + "|" + normalizedRecordUri
                            + "|<manual>|<manual>"
                            + "|" + itemStartTime
                            + "|" + itemEndTime;
                    if (requestedSegments.add(requestKey)) {
                        segmentRequests.add(new ReviewEvidenceVideoSegmentRequest(
                                context.item().id(),
                                context.timelineItem().sourceAlertId(),
                                normalizedRecordUri,
                                itemStartTime,
                                itemEndTime,
                                segmentRequests.size()
                        ));
                    }
                }
            }
            if (segmentRequests.isEmpty()) {
                continue;
            }
            List<ReviewEvidenceVideoSegmentRequest> orderedSegmentRequests = orderAndNumberVideoSegments(
                    segmentRequests);
            assertReviewItemVideoSegmentsDoNotOverlap(orderedSegmentRequests);
            requestPlans.add(new VideoExportCameraRequestPlan(
                    entry.getKey(),
                    orderedSegmentRequests
            ));
        }
        if (videoArtifactRequired) {
            Set<Long> expectedReviewItemIds = reviewItemIds.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<Long> segmentedReviewItemIds = requestPlans.stream()
                    .flatMap(plan -> plan.segmentRequests().stream())
                    .map(ReviewEvidenceVideoSegmentRequest::reviewItemId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            expectedReviewItemIds.removeAll(segmentedReviewItemIds);
            if (!expectedReviewItemIds.isEmpty()) {
                throw new IllegalStateException(
                        "video export requested but review items have no exportable recording segment: "
                                + expectedReviewItemIds);
            }
            if (requestedSegments.isEmpty()) {
                throw new IllegalStateException("video export requested but case has no recording evidence");
            }
        }
        for (VideoExportCameraRequestPlan plan : requestPlans) {
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests = plan.segmentRequests();
            ReviewEvidenceVideoSegmentRequest firstSegment = segmentRequests.get(0);
            ReviewItemAggregate firstItem = deps.reviewItemStore.findById(firstSegment.reviewItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "review item missing for VIDEO export: " + firstSegment.reviewItemId()));
            LocalDateTime startTime = segmentRequests.stream()
                    .map(ReviewEvidenceVideoSegmentRequest::clipStartTime)
                    .filter(Objects::nonNull)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);
            LocalDateTime endTime = segmentRequests.stream()
                    .map(ReviewEvidenceVideoSegmentRequest::clipEndTime)
                    .filter(Objects::nonNull)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
            try {
                Optional<ReviewEvidenceVideoExportResult> exportResult = deps.videoEvidenceExportProvider.export(
                        new ReviewEvidenceVideoExportRequest(
                                reviewCaseId,
                                firstSegment.reviewItemId(),
                                firstItem.deviceId(),
                                plan.cameraId(),
                                firstSegment.sourceAlertId(),
                                startTime,
                                endTime,
                                firstSegment.recordUri(),
                                format,
                                segmentRequests,
                                expiresAt
                        ));
                if (exportResult.isPresent()) {
                    if (videoArtifactRequired && !hasCompleteVideoExportProvenance(exportResult.get())) {
                        throw new IllegalStateException("VIDEO export returned incomplete media provenance");
                    }
                    results.add(exportResult.get());
                } else if (videoArtifactRequired) {
                    throw new IllegalStateException("VIDEO export did not return a ready media artifact");
                }
            } catch (RuntimeException exception) {
                if (videoArtifactRequired) {
                    throw new IllegalStateException("VIDEO export failed; evidence job cannot be ready", exception);
                }
            }
        }
        if (videoArtifactRequired && results.size() != requestPlans.size()) {
            throw new IllegalStateException("VIDEO export did not complete every requested camera timeline");
        }
        return List.copyOf(results);
    }

    private record VideoExportSegmentContext(ReviewItemAggregate item,
                                             ReviewCaseTimelineItem timelineItem) {
    }

    private record VideoExportCameraRequestPlan(String cameraId,
                                                List<ReviewEvidenceVideoSegmentRequest> segmentRequests) {
    }

    private static List<ReviewEvidenceVideoSegmentRequest> orderAndNumberVideoSegments(
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests) {
        List<ReviewEvidenceVideoSegmentRequest> ordered = segmentRequests.stream()
                .sorted(Comparator
                        .comparing(ReviewEvidenceVideoSegmentRequest::clipStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceVideoSegmentRequest::segmentStartTime,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceVideoSegmentRequest::reviewItemId,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewEvidenceVideoSegmentRequest::recordUri,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        List<ReviewEvidenceVideoSegmentRequest> numbered = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ReviewEvidenceVideoSegmentRequest segment = ordered.get(index);
            numbered.add(new ReviewEvidenceVideoSegmentRequest(
                    segment.reviewItemId(),
                    segment.sourceAlertId(),
                    segment.recordUri(),
                    segment.segmentStartTime(),
                    segment.segmentEndTime(),
                    segment.clipStartTime(),
                    segment.clipEndTime(),
                    index
            ));
        }
        return List.copyOf(numbered);
    }

    private static void assertReviewItemVideoSegmentsDoNotOverlap(
            List<ReviewEvidenceVideoSegmentRequest> segmentRequests) {
        for (int leftIndex = 0; leftIndex < segmentRequests.size(); leftIndex++) {
            ReviewEvidenceVideoSegmentRequest left = segmentRequests.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < segmentRequests.size(); rightIndex++) {
                ReviewEvidenceVideoSegmentRequest right = segmentRequests.get(rightIndex);
                if (Objects.equals(left.recordUri(), right.recordUri())
                        && (!Objects.equals(left.segmentStartTime(), right.segmentStartTime())
                        || !Objects.equals(left.segmentEndTime(), right.segmentEndTime()))) {
                    throw new IllegalStateException(
                            "record URI has conflicting physical windows: " + left.recordUri());
                }
                if (!Objects.equals(left.reviewItemId(), right.reviewItemId())
                        || left.clipStartTime() == null
                        || left.clipEndTime() == null
                        || right.clipStartTime() == null
                        || right.clipEndTime() == null) {
                    continue;
                }
                if (left.clipStartTime().isBefore(right.clipEndTime())
                        && right.clipStartTime().isBefore(left.clipEndTime())) {
                    throw new IllegalStateException(
                            "review item video segments overlap: " + left.reviewItemId());
                }
            }
        }
    }

    private static Map<String, Set<String>> canonicalRecordUrisByObjectName(
            List<VideoExportSegmentContext> contexts) {
        Map<String, Set<String>> canonicalUrisByObjectName = new LinkedHashMap<>();
        for (VideoExportSegmentContext context : contexts) {
            String recordUri = context.timelineItem().materialUri();
            canonicalRecordObjectName(recordUri).ifPresent(objectName ->
                    canonicalUrisByObjectName.computeIfAbsent(objectName, ignored -> new LinkedHashSet<>())
                            .add(recordUri));
        }
        return canonicalUrisByObjectName;
    }

    private static String normalizeVideoExportRecordUri(String recordUri,
                                                        Map<String, Set<String>> canonicalUrisByObjectName) {
        if (canonicalRecordObjectName(recordUri).isPresent()) {
            return recordUri;
        }
        Optional<String> minioObjectName = minioRecordObjectName(recordUri);
        if (minioObjectName.isEmpty()) {
            return recordUri;
        }
        Set<String> candidates = canonicalUrisByObjectName.get(minioObjectName.get());
        if (candidates == null || candidates.size() != 1) {
            throw new IllegalStateException(
                    "VIDEO export MinIO record URI is not uniquely bound to a canonical camera record");
        }
        return candidates.iterator().next();
    }

    private static Optional<String> canonicalRecordObjectName(String recordUri) {
        try {
            URI uri = URI.create(recordUri);
            java.util.regex.Matcher matcher = CANONICAL_RECORD_URI_PATH.matcher(uri.getPath());
            if (!matcher.find()) {
                return Optional.empty();
            }
            return normalizedRecordObjectName(matcher.group(1));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> minioRecordObjectName(String recordUri) {
        URI uri;
        try {
            uri = URI.create(recordUri);
        } catch (RuntimeException exception) {
            if (recordUri.contains("/api/v1/buckets/") && recordUri.contains("/objects/download")) {
                throw new IllegalStateException(
                        "VIDEO export MinIO record URI is not uniquely bound to a canonical camera record",
                        exception);
            }
            return Optional.empty();
        }
        if (!MINIO_RECORD_DOWNLOAD_PATH.matcher(uri.getPath()).find()) {
            return Optional.empty();
        }
        Set<String> prefixes = new LinkedHashSet<>();
        String rawQuery = uri.getRawQuery();
        if (rawQuery != null) {
            for (String parameter : rawQuery.split("&")) {
                int separator = parameter.indexOf('=');
                String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
                if (!"prefix".equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) {
                    continue;
                }
                String rawValue = separator < 0 ? "" : parameter.substring(separator + 1);
                normalizedRecordObjectName(URLDecoder.decode(rawValue, StandardCharsets.UTF_8))
                        .ifPresent(prefixes::add);
            }
        }
        if (prefixes.size() != 1) {
            throw new IllegalStateException(
                    "VIDEO export MinIO record URI is not uniquely bound to a canonical camera record");
        }
        return Optional.of(prefixes.iterator().next());
    }

    private static Optional<String> normalizedRecordObjectName(String objectName) {
        if (!hasText(objectName)) {
            return Optional.empty();
        }
        String normalized = objectName.replace('\\', '/').replaceFirst("^/+", "");
        return hasText(normalized) ? Optional.of(normalized) : Optional.empty();
    }

    static boolean hasCompleteVideoExportProvenance(ReviewEvidenceVideoExportResult result) {
        return result != null
                && "ready".equals(normalizeVideoExportStatus(result.status()))
                && hasText(result.exportId())
                && hasText(result.exportUri())
                && hasText(result.manifestUri())
                && isSha256Hash(result.fileHash())
                && isSha256Hash(result.ffmpegCommandHash())
                && hasCompleteVideoSegmentProvenance(result.recordSegments());
    }

    static boolean hasCompleteVideoExportProvenance(Map<String, Object> result) {
        return result != null
                && "ready".equals(normalizeVideoExportStatus(toText(result.get("status"))))
                && hasText(toText(result.get("exportId")))
                && hasText(toText(result.get("exportUri")))
                && hasText(toText(result.get("manifestUri")))
                && isSha256Hash(toText(result.get("fileHash")))
                && isSha256Hash(toText(result.get("ffmpegCommandHash")))
                && hasCompleteVideoSegmentProvenance(toMapList(result.get("recordSegments")));
    }

    private static boolean hasCompleteVideoSegmentProvenance(List<Map<String, Object>> segments) {
        if (segments == null || segments.isEmpty()) {
            return false;
        }
        Set<Integer> stitchOrders = new LinkedHashSet<>();
        for (Map<String, Object> segment : segments) {
            if (!isSha256Hash(toText(segment.get("sourceHash")))
                    || !isSha256Hash(toText(segment.get("ffmpegCommandHash")))) {
                return false;
            }
            if (!(segment.get("clipParameters") instanceof Map<?, ?> clipParameters)
                    || !isFiniteNonNegative(clipParameters.get("offsetSeconds"))
                    || !isFinitePositive(clipParameters.get("durationSeconds"))) {
                return false;
            }
            Integer stitchOrder = exactVideoExportInteger(segment.get("stitchOrder"));
            if (stitchOrder == null || stitchOrder < 0 || !stitchOrders.add(stitchOrder)) {
                return false;
            }
        }
        for (int expected = 0; expected < segments.size(); expected++) {
            if (!stitchOrders.contains(expected)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeVideoExportStatus(String status) {
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    static boolean isSha256Hash(String value) {
        return hasText(value) && SHA256_HASH.matcher(value).matches();
    }

    static boolean sha256HashesEqual(String left, String right) {
        return isSha256Hash(left) && isSha256Hash(right) && left.equalsIgnoreCase(right);
    }

    private static Integer exactVideoExportInteger(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        double candidate = number.doubleValue();
        if (!Double.isFinite(candidate) || candidate != Math.rint(candidate)
                || candidate < Integer.MIN_VALUE || candidate > Integer.MAX_VALUE) {
            return null;
        }
        return (int) candidate;
    }

    private static boolean isFiniteNonNegative(Object value) {
        return value instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= 0D;
    }

    private static boolean isFinitePositive(Object value) {
        return value instanceof Number number
                && Double.isFinite(number.doubleValue())
                && number.doubleValue() > 0D;
    }

    private static boolean requiresVideoArtifact(String format) {
        if (!hasText(format)) {
            return false;
        }
        return Set.of("mp4", "mkv", "mov", "avi", "webm", "ts")
                .contains(format.trim().toLowerCase(Locale.ROOT));
    }

    private static LocalDateTime exportStartTime(ReviewItemAggregate item, ReviewCaseTimelineItem timelineItem) {
        if (item.firstAlertTime() != null) {
            return item.firstAlertTime();
        }
        return timelineItem.happenedAt();
    }

    private static LocalDateTime exportEndTime(ReviewItemAggregate item, ReviewCaseTimelineItem timelineItem) {
        LocalDateTime startTime = exportStartTime(item, timelineItem);
        LocalDateTime endTime = item.lastAlertTime();
        if (endTime == null) {
            endTime = timelineItem.happenedAt();
        }
        if (startTime != null && (endTime == null || !endTime.isAfter(startTime))) {
            return startTime.plusSeconds(60);
        }
        return endTime;
    }

    static Map<String, Object> videoExportToMap(ReviewEvidenceVideoExportResult videoExport) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("exportId", videoExport.exportId());
        values.put("exportUri", videoExport.exportUri());
        values.put("status", videoExport.status());
        values.put("message", videoExport.message());
        values.put("manifestUri", videoExport.manifestUri());
        values.put("fileHash", videoExport.fileHash());
        values.put("recordSegments", videoExport.recordSegments());
        values.put("ffmpegCommandHash", videoExport.ffmpegCommandHash());
        values.entrySet().removeIf(entry -> entry.getValue() == null);
        return Map.copyOf(values);
    }
}
