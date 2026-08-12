package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoSegmentRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceDownloadArtifact;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoDownloadRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.VideoEvidenceExportProvider;
import com.basiclab.iot.common.utils.json.JsonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class HttpVideoEvidenceExportProvider implements VideoEvidenceExportProvider {

    private static final Pattern SHA256_HASH = Pattern.compile("sha256:[0-9a-fA-F]{64}");

    private final RestTemplate restTemplate;
    private final String recordExportUrl;
    private final String publicPlayHost;
    private final VideoMediaServiceRequestSigner mediaRequestSigner;
    private final long downloadMaxBytes;

    @Value("${yfeieye.video.record-export-poll-max-attempts:300}")
    private int pollMaxAttempts = 300;

    @Value("${yfeieye.video.record-export-poll-interval-ms:1000}")
    private long pollIntervalMillis = 0L;

    @Value("${yfeieye.video.record-export-poll-timeout-ms:300000}")
    private long pollTimeoutMillis = 300_000L;

    public HttpVideoEvidenceExportProvider(RestTemplate restTemplate,
                                           String recordExportUrl,
                                           String publicPlayHost,
                                           VideoMediaServiceRequestSigner mediaRequestSigner) {
        this(restTemplate, recordExportUrl, publicPlayHost, mediaRequestSigner, 536_870_912L);
    }

    private HttpVideoEvidenceExportProvider(RestTemplate restTemplate,
                                            String recordExportUrl,
                                            String publicPlayHost,
                                            VideoMediaServiceRequestSigner mediaRequestSigner,
                                            long downloadMaxBytes) {
        this.restTemplate = restTemplate;
        this.recordExportUrl = recordExportUrl;
        this.publicPlayHost = publicPlayHost;
        this.mediaRequestSigner = mediaRequestSigner;
        this.downloadMaxBytes = Math.max(1L, downloadMaxBytes);
    }

    @Autowired
    public HttpVideoEvidenceExportProvider(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${yfeieye.video.record-export-url:}") String recordExportUrl,
            @Value("${yfeieye.video.public-play-host:${MEDIA_HTTP_PLAY_HOST:}}") String publicPlayHost,
            VideoMediaServiceRequestSigner mediaRequestSigner,
            @Value("${yfeieye.video.record-export-connect-timeout-ms:5000}") long connectTimeoutMillis,
            @Value("${yfeieye.video.record-export-read-timeout-ms:30000}") long readTimeoutMillis,
            @Value("${yfeieye.video.record-export-download-max-bytes:536870912}") long downloadMaxBytes) {
        this(
                restTemplateBuilder
                        .setConnectTimeout(Duration.ofMillis(Math.max(1L, connectTimeoutMillis)))
                        .setReadTimeout(Duration.ofMillis(Math.max(1L, readTimeoutMillis)))
                        .build(),
                recordExportUrl,
                publicPlayHost,
                mediaRequestSigner,
                downloadMaxBytes
        );
    }

    @Override
    public Optional<ReviewEvidenceVideoExportResult> export(ReviewEvidenceVideoExportRequest request) {
        if (!hasText(recordExportUrl) || request == null) {
            return Optional.empty();
        }
        URI requestUri = URI.create(recordExportUrl);
        long pollDeadlineNanos = System.nanoTime()
                + Duration.ofMillis(Math.max(1L, pollTimeoutMillis)).toNanos();
        String requestBody = JsonUtils.toJsonString(requestBody(request));
        Map<?, ?> data = responseData(exchange(requestUri, HttpMethod.POST, request.cameraId(), requestBody));
        String exportId = exportId(data);
        if (!hasText(exportId)) {
            throw new IllegalStateException("VIDEO export did not return export_id");
        }
        URI statusUri = statusUri(requestUri, data, exportId);
        for (int attempt = 0; attempt <= Math.max(1, pollMaxAttempts); attempt++) {
            String status = normalizedStatus(data);
            if ("ready".equals(status)) {
                return Optional.of(readyResult(exportId, data, request));
            }
            if (List.of("failed", "rejected", "expired", "unavailable").contains(status)) {
                throw new IllegalStateException("VIDEO export failed: "
                        + firstText(data.get("last_error"), data.get("lastError"),
                        data.get("message"), data.get("msg"), status));
            }
            if (attempt >= Math.max(1, pollMaxAttempts) || System.nanoTime() >= pollDeadlineNanos) {
                break;
            }
            pauseBeforePoll(pollDeadlineNanos);
            if (System.nanoTime() >= pollDeadlineNanos) {
                break;
            }
            data = responseData(exchange(statusUri, HttpMethod.GET, request.cameraId(), ""));
        }
        throw new IllegalStateException("VIDEO export did not reach ready state within hard timeout");
    }

    @Override
    public Optional<ReviewEvidenceDownloadArtifact> download(ReviewEvidenceVideoDownloadRequest request) {
        if (!hasText(recordExportUrl) || request == null) {
            return Optional.empty();
        }
        if (!isSha256(request.expectedFileHash())) {
            throw new IllegalArgumentException("expectedFileHash must be a SHA-256 digest");
        }
        String expectedFileHash = request.expectedFileHash().toLowerCase(Locale.ROOT);
        URI downloadUri = internalDownloadUri(request.exportUri());
        HttpHeaders headers = mediaRequestSigner.sign(
                HttpMethod.GET,
                downloadUri,
                "download",
                request.cameraId(),
                ""
        );
        Path temporaryFile = createDownloadTemporaryFile();
        try {
            DownloadMetadata metadata = restTemplate.execute(
                    downloadUri,
                    HttpMethod.GET,
                    httpRequest -> httpRequest.getHeaders().putAll(headers),
                    response -> streamDownload(response.getHeaders(), response.getBody(), temporaryFile)
            );
            if (metadata == null || metadata.contentLength() <= 0L) {
                throw new IllegalStateException("VIDEO export download returned no bytes");
            }
            if (!MessageDigest.isEqual(
                    expectedFileHash.getBytes(StandardCharsets.US_ASCII),
                    metadata.fileHash().getBytes(StandardCharsets.US_ASCII))) {
                throw new SecurityException("VIDEO export download hash mismatch");
            }
            return Optional.of(new ReviewEvidenceDownloadArtifact(
                    null,
                    downloadFileName(metadata.headers(), downloadUri),
                    metadata.contentType(),
                    temporaryFile,
                    metadata.contentLength(),
                    metadata.fileHash(),
                    null
            ));
        } catch (RuntimeException exception) {
            deleteTemporaryFile(temporaryFile);
            throw exception;
        }
    }

    private DownloadMetadata streamDownload(HttpHeaders responseHeaders,
                                            InputStream input,
                                            Path temporaryFile) {
        long declaredLength = responseHeaders.getContentLength();
        if (declaredLength > downloadMaxBytes) {
            throw new IllegalStateException("VIDEO export download exceeds configured byte limit");
        }
        MessageDigest digest = sha256Digest();
        long byteCount = 0L;
        try (InputStream source = input;
             OutputStream target = Files.newOutputStream(
                     temporaryFile,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = source.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                if (read > downloadMaxBytes - byteCount) {
                    throw new IllegalStateException("VIDEO export download exceeds configured byte limit");
                }
                target.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                byteCount += read;
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("VIDEO export download stream failed", exception);
        }
        if (byteCount == 0L) {
            throw new IllegalStateException("VIDEO export download returned no bytes");
        }
        String contentType = responseHeaders.getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : responseHeaders.getContentType().toString();
        return new DownloadMetadata(
                responseHeaders,
                contentType,
                byteCount,
                "sha256:" + java.util.HexFormat.of().formatHex(digest.digest())
        );
    }

    private static Path createDownloadTemporaryFile() {
        try {
            return Files.createTempFile("yfeieye-video-export-", ".part");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create VIDEO export download temporary file", exception);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void deleteTemporaryFile(Path temporaryFile) {
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (Exception ignored) {
            temporaryFile.toFile().deleteOnExit();
        }
    }

    private record DownloadMetadata(HttpHeaders headers,
                                    String contentType,
                                    long contentLength,
                                    String fileHash) {
    }

    private URI internalDownloadUri(String exportedUri) {
        if (!hasText(exportedUri)) {
            throw new IllegalArgumentException("exportUri is required");
        }
        URI configured = URI.create(recordExportUrl);
        URI candidate = configured.resolve(exportedUri);
        if (candidate.getUserInfo() != null || candidate.getFragment() != null) {
            throw new SecurityException("VIDEO export download URL is invalid");
        }
        boolean configuredOrigin = sameOrigin(configured, candidate);
        boolean publicOrigin = hasText(publicPlayHost)
                && sameOrigin(URI.create(publicPlayHost), candidate);
        if (!configuredOrigin && !publicOrigin) {
            throw new SecurityException("VIDEO export download URL origin is not trusted");
        }
        String configuredPath = Optional.ofNullable(configured.getPath()).orElse("").replaceAll("/+$", "");
        String candidatePath = Optional.ofNullable(candidate.getPath()).orElse("");
        String normalizedPath = URI.create(candidatePath).normalize().getPath();
        if (!Objects.equals(candidatePath, normalizedPath)) {
            throw new SecurityException("VIDEO export download URL path is not trusted");
        }
        String internalPath = candidatePath;
        if (publicOrigin) {
            String publicBasePath = Optional.ofNullable(URI.create(publicPlayHost).getPath())
                    .orElse("")
                    .replaceAll("/+$", "");
            if (hasText(publicBasePath) && !"/".equals(publicBasePath)
                    && candidatePath.startsWith(publicBasePath + "/")) {
                internalPath = candidatePath.substring(publicBasePath.length());
            }
        }
        String normalizedInternalPath = URI.create(internalPath).normalize().getPath();
        if (!Objects.equals(internalPath, normalizedInternalPath)
                || !internalPath.startsWith(configuredPath + "/")
                || !internalPath.endsWith("/download")) {
            throw new SecurityException("VIDEO export download URL path is not trusted");
        }
        try {
            return new URI(
                    configured.getScheme(),
                    null,
                    configured.getHost(),
                    configured.getPort(),
                    internalPath,
                    candidate.getQuery(),
                    null
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("VIDEO export download URL is invalid", exception);
        }
    }

    private static String downloadFileName(HttpHeaders headers, URI downloadUri) {
        String fileName = headers.getContentDisposition().getFilename();
        if (hasText(fileName) && fileName.matches("[A-Za-z0-9._-]{1,180}")) {
            return fileName;
        }
        String[] segments = downloadUri.getPath().split("/");
        String exportId = segments.length >= 2 ? segments[segments.length - 2] : "evidence-export";
        exportId = exportId.matches("[A-Za-z0-9._-]{1,160}") ? exportId : "evidence-export";
        return exportId + ".bin";
    }

    private Map<?, ?> exchange(URI uri, HttpMethod method, String cameraId, String body) {
        HttpHeaders headers = mediaRequestSigner.sign(method, uri, "export", cameraId, body);
        if (method == HttpMethod.POST) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return restTemplate.exchange(
                uri,
                method,
                new HttpEntity<>(method == HttpMethod.POST ? body : null, headers),
                Map.class
        ).getBody();
    }

    private void pauseBeforePoll(long pollDeadlineNanos) {
        if (pollIntervalMillis <= 0) {
            return;
        }
        try {
            long remainingMillis = Math.max(0L,
                    Duration.ofNanos(Math.max(0L, pollDeadlineNanos - System.nanoTime())).toMillis());
            Thread.sleep(Math.min(pollIntervalMillis, remainingMillis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("VIDEO export polling was interrupted", exception);
        }
    }

    private ReviewEvidenceVideoExportResult readyResult(String exportId,
                                                        Map<?, ?> data,
                                                        ReviewEvidenceVideoExportRequest request) {
        String downloadUri = firstText(data.get("download_url"), data.get("downloadUrl"),
                data.get("export_uri"), data.get("exportUri"), data.get("url"));
        String manifestUri = firstText(data.get("manifest_url"), data.get("manifestUrl"));
        String fileHash = firstText(data.get("file_hash"), data.get("fileHash"));
        String commandHash = firstText(data.get("ffmpeg_command_hash"), data.get("ffmpegCommandHash"));
        List<Map<String, Object>> segments = recordSegments(data);
        for (int index = 0; index < segments.size(); index++) {
            Map<String, Object> segment = segments.get(index);
            segment.putIfAbsent("cameraId", request.cameraId());
            if (index < request.recordSegments().size()) {
                segment.putIfAbsent("reviewItemId", request.recordSegments().get(index).reviewItemId());
                segment.putIfAbsent("sourceAlertId", request.recordSegments().get(index).sourceAlertId());
            }
        }
        if (!hasText(downloadUri) || !hasText(manifestUri)) {
            throw new IllegalStateException("ready VIDEO export is missing download or manifest URL");
        }
        if (!isSha256(fileHash) || !isSha256(commandHash)) {
            throw new IllegalStateException("ready VIDEO export is missing media or ffmpeg command hash");
        }
        if (!hasCompleteSegmentProvenance(segments)) {
            throw new IllegalStateException("ready VIDEO export is missing input provenance");
        }
        reconcileRecordSegments(request, segments);
        return new ReviewEvidenceVideoExportResult(
                exportId,
                rewritePublicUri(downloadUri),
                "ready",
                firstText(data.get("message"), data.get("msg")),
                rewritePublicUri(manifestUri),
                fileHash,
                segments,
                commandHash
        );
    }

    private static void reconcileRecordSegments(ReviewEvidenceVideoExportRequest request,
                                                List<Map<String, Object>> returnedSegments) {
        List<?> requestedSegments = request == null ? List.of() : request.recordSegments();
        if (requestedSegments.size() != returnedSegments.size()) {
            throw new IllegalStateException("ready VIDEO export record segments do not reconcile: count mismatch");
        }
        for (int index = 0; index < request.recordSegments().size(); index++) {
            var requested = request.recordSegments().get(index);
            Map<String, Object> returned = returnedSegments.get(index);
            Integer stitchOrder = exactInteger(returned.get("stitchOrder") != null
                    ? returned.get("stitchOrder")
                    : returned.get("stitch_order"));
            String recordUri = firstText(
                    returned.get("originalRecordUri"),
                    returned.get("original_record_uri"),
                    returned.get("inputRecordUri"),
                    returned.get("input_record_uri"),
                    returned.get("recordUri"),
                    returned.get("record_uri")
            );
            Map<?, ?> clipParameters = returned.get("clipParameters") instanceof Map<?, ?> values
                    ? values
                    : returned.get("clip_parameters") instanceof Map<?, ?> values
                    ? values
                    : Map.of();
            LocalDateTime clipStartTime = parseClipTime(firstText(
                    returned.get("clipStartTime"),
                    returned.get("clip_start_time"),
                    clipParameters.get("clipStartTime"),
                    clipParameters.get("clip_start_time")
            ));
            LocalDateTime clipEndTime = parseClipTime(firstText(
                    returned.get("clipEndTime"),
                    returned.get("clip_end_time"),
                    clipParameters.get("clipEndTime"),
                    clipParameters.get("clip_end_time")
            ));
            LocalDateTime segmentStartTime = parseClipTime(firstText(
                    returned.get("segmentStartTime"),
                    returned.get("segment_start_time"),
                    clipParameters.get("segmentStartTime"),
                    clipParameters.get("segment_start_time")
            ));
            LocalDateTime segmentEndTime = parseClipTime(firstText(
                    returned.get("segmentEndTime"),
                    returned.get("segment_end_time"),
                    clipParameters.get("segmentEndTime"),
                    clipParameters.get("segment_end_time")
            ));
            if (!Objects.equals(requested.stitchOrder(), stitchOrder)
                    || !Objects.equals(requested.recordUri(), recordUri)) {
                throw new IllegalStateException(
                        "ready VIDEO export record segments do not reconcile: order or URI mismatch"
                );
            }
            if (!Objects.equals(requested.segmentStartTime(), segmentStartTime)
                    || !Objects.equals(requested.segmentEndTime(), segmentEndTime)) {
                throw new IllegalStateException(
                        "ready VIDEO export record segments do not reconcile: physical window mismatch"
                );
            }
            if (!Objects.equals(requested.clipStartTime(), clipStartTime)
                    || !Objects.equals(requested.clipEndTime(), clipEndTime)) {
                throw new IllegalStateException(
                        "ready VIDEO export record segments do not reconcile: clip window mismatch"
                );
            }
            if (requested.segmentStartTime() != null && requested.segmentEndTime() != null
                    && requested.clipStartTime() != null && requested.clipEndTime() != null) {
                double expectedOffset = Duration.between(
                        requested.segmentStartTime(),
                        requested.clipStartTime()
                ).toMillis() / 1000D;
                Object offsetValue = returned.get("offsetSeconds");
                if (offsetValue == null) {
                    offsetValue = returned.get("offset_seconds");
                }
                if (offsetValue == null) {
                    offsetValue = clipParameters.get("offsetSeconds");
                }
                if (offsetValue == null) {
                    offsetValue = clipParameters.get("offset_seconds");
                }
                if (!(offsetValue instanceof Number offset)
                        || !Double.isFinite(offset.doubleValue())
                        || Math.abs(offset.doubleValue() - expectedOffset) > 0.001D) {
                    throw new IllegalStateException(
                            "ready VIDEO export record segments do not reconcile: clip offset mismatch"
                    );
                }
            }
            if (requested.clipStartTime() != null && requested.clipEndTime() != null) {
                double expectedDuration = Duration.between(
                        requested.clipStartTime(),
                        requested.clipEndTime()
                ).toMillis() / 1000D;
                Object durationValue = clipParameters.get("durationSeconds") != null
                        ? clipParameters.get("durationSeconds")
                        : clipParameters.get("duration_seconds");
                if (!(durationValue instanceof Number duration)
                        || Math.abs(duration.doubleValue() - expectedDuration) > 0.001D) {
                    throw new IllegalStateException(
                            "ready VIDEO export record segments do not reconcile: clip duration mismatch"
                    );
                }
            }
        }
    }

    private static LocalDateTime parseClipTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value).toLocalDateTime();
            } catch (DateTimeParseException invalid) {
                throw new IllegalStateException("ready VIDEO export returned an invalid clip timestamp", invalid);
            }
        }
    }

    private static List<Map<String, Object>> recordSegments(Map<?, ?> data) {
        Object raw = data.get("record_segments") != null ? data.get("record_segments") : data.get("recordSegments");
        if (!(raw instanceof List<?> values)) {
            return List.of();
        }
        List<Map<String, Object>> segments = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> segment = new LinkedHashMap<>();
            map.forEach((key, item) -> segment.put(String.valueOf(key), item));
            segments.add(segment);
        }
        return List.copyOf(segments);
    }

    private static String exportId(Map<?, ?> data) {
        return firstText(data.get("export_id"), data.get("exportId"), data.get("id"),
                data.get("task_id"), data.get("taskId"));
    }

    private static String normalizedStatus(Map<?, ?> data) {
        String status = firstText(data.get("status"), data.get("state"));
        return status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
    }

    private static URI statusUri(URI requestUri, Map<?, ?> data, String exportId) {
        String value = firstText(data.get("status_url"), data.get("statusUrl"));
        if (hasText(value)) {
            URI resolved = requestUri.resolve(value);
            if (!sameOrigin(requestUri, resolved) || !isExportStatusPath(requestUri, resolved)) {
                throw new IllegalStateException("VIDEO export returned an invalid status URL");
            }
            return resolved;
        }
        String base = requestUri.toString().endsWith("/")
                ? requestUri.toString().substring(0, requestUri.toString().length() - 1)
                : requestUri.toString();
        return URI.create(base + "/" + exportId);
    }

    private static boolean isSha256(String value) {
        return hasText(value) && SHA256_HASH.matcher(value).matches();
    }

    private static boolean hasCompleteSegmentProvenance(List<Map<String, Object>> segments) {
        if (segments.isEmpty()) {
            return false;
        }
        Set<Integer> stitchOrders = new java.util.HashSet<>();
        for (Map<String, Object> segment : segments) {
            if (!isSha256(firstText(segment.get("sourceHash"), segment.get("source_hash")))
                    || !isSha256(firstText(segment.get("ffmpegCommandHash"), segment.get("ffmpeg_command_hash")))) {
                return false;
            }
            Object rawClipParameters = segment.get("clipParameters") != null
                    ? segment.get("clipParameters") : segment.get("clip_parameters");
            if (!(rawClipParameters instanceof Map<?, ?> clipParameters)
                    || !isFiniteNonNegative(clipParameters.get("offsetSeconds"))
                    || !isFinitePositive(clipParameters.get("durationSeconds"))) {
                return false;
            }
            Integer stitchOrder = exactInteger(segment.get("stitchOrder") != null
                    ? segment.get("stitchOrder") : segment.get("stitch_order"));
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

    private static Integer exactInteger(Object value) {
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

    private static boolean sameOrigin(URI configured, URI candidate) {
        return configured.getScheme() != null
                && candidate.getScheme() != null
                && configured.getScheme().equalsIgnoreCase(candidate.getScheme())
                && configured.getHost() != null
                && configured.getHost().equalsIgnoreCase(candidate.getHost())
                && effectivePort(configured) == effectivePort(candidate)
                && candidate.getUserInfo() == null;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isExportStatusPath(URI configured, URI candidate) {
        String basePath = Optional.ofNullable(configured.getPath()).orElse("").replaceAll("/+$", "");
        String candidatePath = Optional.ofNullable(candidate.getPath()).orElse("");
        return hasText(basePath) && candidatePath.startsWith(basePath + "/");
    }

    private static Map<String, Object> requestBody(ReviewEvidenceVideoExportRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("review_case_id", request.reviewCaseId());
        body.put("review_item_id", request.reviewItemId());
        body.put("device_id", request.deviceId());
        body.put("camera_id", request.cameraId());
        body.put("source_alert_id", request.sourceAlertId());
        body.put("start_time", request.startTime() == null ? null : request.startTime().toString());
        body.put("end_time", request.endTime() == null ? null : request.endTime().toString());
        body.put("record_uri", request.recordUri());
        body.put("format", request.format());
        body.put("expires_at", request.expiresAt() == null ? null : request.expiresAt().toString());
        if (!request.recordSegments().isEmpty()) {
            List<Long> reviewItemIds = request.recordSegments().stream()
                    .map(ReviewEvidenceVideoSegmentRequest::reviewItemId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            if (!reviewItemIds.isEmpty()) {
                body.put("review_item_ids", reviewItemIds);
            }
            body.put("record_segments", request.recordSegments().stream()
                    .map(segment -> {
                        Map<String, Object> value = new LinkedHashMap<>();
                        value.put("review_item_id", segment.reviewItemId());
                        value.put("source_alert_id", segment.sourceAlertId());
                        value.put("record_uri", segment.recordUri());
                        value.put("segment_start_time", segment.segmentStartTime() == null
                                ? null
                                : segment.segmentStartTime().toString());
                        value.put("segment_end_time", segment.segmentEndTime() == null
                                ? null
                                : segment.segmentEndTime().toString());
                        value.put("clip_start_time", segment.clipStartTime() == null
                                ? null
                                : segment.clipStartTime().toString());
                        value.put("clip_end_time", segment.clipEndTime() == null
                                ? null
                                : segment.clipEndTime().toString());
                        value.put("stitch_order", segment.stitchOrder());
                        value.entrySet().removeIf(entry -> entry.getValue() == null);
                        return value;
                    })
                    .toList());
        }
        body.put("async_worker", true);
        body.entrySet().removeIf(entry -> entry.getValue() == null);
        return body;
    }

    private static Map<?, ?> responseData(Map<?, ?> response) {
        if (response == null || response.isEmpty()) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return dataMap;
        }
        return response;
    }

    private String rewritePublicUri(String uri) {
        if (!hasText(uri) || !hasText(publicPlayHost)) {
            return uri;
        }
        try {
            URI source = URI.create(uri);
            URI publicHost = URI.create(publicPlayHost);
            if (!hasText(publicHost.getScheme()) || !hasText(publicHost.getHost())) {
                return uri;
            }
            if (source.getHost() == null && !uri.startsWith("/")) {
                return uri;
            }
            String targetPath = publicTargetPath(publicHost, source);
            StringBuilder target = new StringBuilder(new URI(
                    publicHost.getScheme(),
                    source.getUserInfo(),
                    publicHost.getHost(),
                    publicHost.getPort(),
                    targetPath,
                    null,
                    null
            ).toASCIIString());
            if (source.getRawQuery() != null) {
                target.append('?').append(source.getRawQuery());
            }
            if (source.getRawFragment() != null) {
                target.append('#').append(source.getRawFragment());
            }
            return target.toString();
        } catch (Exception ignored) {
            return uri;
        }
    }

    private static String publicTargetPath(URI publicHost, URI source) {
        String sourcePath = source.getPath();
        String basePath = publicHost.getPath();
        if (hasText(basePath)
                && !"/".equals(basePath)
                && ("/video".equals(sourcePath) || sourcePath.startsWith("/video/"))) {
            return basePath.replaceAll("/+$", "") + sourcePath;
        }
        return sourcePath;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = toText(value);
            if (hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static String toText(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
