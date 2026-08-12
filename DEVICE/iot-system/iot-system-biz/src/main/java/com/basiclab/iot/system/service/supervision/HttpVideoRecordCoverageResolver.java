package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HttpVideoRecordCoverageResolver implements RecordCoverageResolver {

    private static final DateTimeFormatter QUERY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final String recordCoverageQueryUrl;
    private final String recordBaseUrl;
    private final String publicPlayHost;
    private final VideoMediaServiceRequestSigner mediaRequestSigner;

    @Autowired
    public HttpVideoRecordCoverageResolver(RestTemplate restTemplate,
                                           @Value("${yfeieye.video.record-coverage-query-url:}") String recordCoverageQueryUrl,
                                           @Value("${yfeieye.video.record-base-url:}") String recordBaseUrl,
                                           @Value("${yfeieye.video.public-play-host:${MEDIA_HTTP_PLAY_HOST:}}") String publicPlayHost,
                                           VideoMediaServiceRequestSigner mediaRequestSigner) {
        this.restTemplate = restTemplate;
        this.recordCoverageQueryUrl = recordCoverageQueryUrl;
        this.recordBaseUrl = recordBaseUrl;
        this.publicPlayHost = publicPlayHost;
        this.mediaRequestSigner = mediaRequestSigner;
    }

    public HttpVideoRecordCoverageResolver(RestTemplate restTemplate,
                                           String recordCoverageQueryUrl,
                                           String publicPlayHost,
                                           VideoMediaServiceRequestSigner mediaRequestSigner) {
        this(restTemplate, recordCoverageQueryUrl, "", publicPlayHost, mediaRequestSigner);
    }

    @Override
    public List<RecordCoverageSegment> resolve(RecordCoverageRequest request) {
        if (request == null) {
            return List.of();
        }
        String deviceId = hasText(request.deviceId()) ? request.deviceId() : request.cameraId();
        if (!hasText(recordCoverageQueryUrl)) {
            return resolveByRecordBaseUrl(request, deviceId, recordBaseUrl);
        }
        if (isRecordBaseUrl(recordCoverageQueryUrl)) {
            return resolveByRecordBaseUrl(request, deviceId, recordCoverageQueryUrl);
        }
        long timeRangeSeconds = Math.max(1, java.time.Duration.between(request.beginTime(), request.endTime()).toSeconds() / 2);
        LocalDateTime alertTime = request.beginTime().plusSeconds(timeRangeSeconds);
        String url = UriComponentsBuilder.fromHttpUrl(recordCoverageQueryUrl)
                .queryParam("device_id", deviceId)
                .queryParam("camera_id", request.cameraId())
                .queryParam("date", request.beginTime().toLocalDate().toString())
                .queryParam("alert_time", alertTime.format(QUERY_TIME_FORMATTER))
                .queryParam("time_range", timeRangeSeconds)
                .queryParam("begin_time", request.beginTime().format(QUERY_TIME_FORMATTER))
                .queryParam("end_time", request.endTime().format(QUERY_TIME_FORMATTER))
                .queryParam("after", request.beginTime().format(QUERY_TIME_FORMATTER))
                .queryParam("before", request.endTime().format(QUERY_TIME_FORMATTER))
                .build()
                .encode()
                .toUriString();
        Map<?, ?> response = signedGet(url, request.cameraId());
        Map<?, ?> data = responseData(response);
        Object rows = firstPresent(data, "segments", "records", "items", "recordings");
        if (rows instanceof List<?> list) {
            return withRecordBaseFallback(toSegments(list, parseDate(data.get("date"), request.beginTime().toLocalDate())),
                    request,
                    deviceId);
        }
        Object timelineRows = firstPresent(data, "timeline_merged", "timelineMerged", "timeline");
        if (timelineRows instanceof List<?> list) {
            return withRecordBaseFallback(toSegments(list, parseDate(data.get("date"), request.beginTime().toLocalDate())),
                    request,
                    deviceId);
        }
        List<RecordCoverageSegment> segments = toSegment(data, request.beginTime().toLocalDate())
                .map(List::of)
                .orElse(List.of());
        return withRecordBaseFallback(segments, request, deviceId);
    }

    private List<RecordCoverageSegment> withRecordBaseFallback(List<RecordCoverageSegment> segments,
                                                               RecordCoverageRequest request,
                                                               String deviceId) {
        if (!segments.isEmpty() || !hasText(recordBaseUrl)) {
            return segments;
        }
        return resolveByRecordBaseUrl(request, deviceId, recordBaseUrl);
    }

    private List<RecordCoverageSegment> resolveByRecordBaseUrl(RecordCoverageRequest request, String deviceId, String baseUrlValue) {
        if (!hasText(deviceId) || !hasText(baseUrlValue)) {
            return List.of();
        }
        String baseUrl = stripTrailingSlash(baseUrlValue);
        String cameraId = hasText(request.cameraId()) ? request.cameraId() : deviceId;
        Long spaceId = resolveRecordSpaceId(baseUrl, deviceId, cameraId);
        if (spaceId == null) {
            return List.of();
        }
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment("space", String.valueOf(spaceId), "videos", "day")
                .queryParam("date", request.beginTime().toLocalDate().toString())
                .queryParam("device_id", deviceId)
                .build()
                .toUriString();
        Map<?, ?> response = signedGet(url, cameraId);
        Map<?, ?> data = responseData(response);
        Object rows = firstPresent(data, "segments", "records", "items", "recordings");
        if (rows instanceof List<?> list) {
            return toSegments(list, parseDate(data.get("date"), request.beginTime().toLocalDate()));
        }
        Object timelineRows = firstPresent(data, "timeline_merged", "timelineMerged", "timeline");
        if (timelineRows instanceof List<?> list) {
            return toSegments(list, parseDate(data.get("date"), request.beginTime().toLocalDate()));
        }
        return List.of();
    }

    private Long resolveRecordSpaceId(String baseUrl, String deviceId, String cameraId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment("space", "device", deviceId)
                .build()
                .toUriString();
        Map<?, ?> response = signedGet(url, cameraId);
        Map<?, ?> data = responseData(response);
        return parseLong(firstPresent(data, "id", "space_id", "spaceId", "record_space_id", "recordSpaceId"));
    }

    private Map<?, ?> signedGet(String url, String cameraId) {
        URI requestUri = URI.create(url);
        HttpEntity<Void> entity = new HttpEntity<>(mediaRequestSigner.sign(
                HttpMethod.GET,
                requestUri,
                "coverage",
                cameraId,
                ""
        ));
        return restTemplate.exchange(requestUri, HttpMethod.GET, entity, Map.class).getBody();
    }

    private List<RecordCoverageSegment> toSegments(List<?> list, LocalDate date) {
        List<RecordCoverageSegment> segments = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> map) {
                toSegment(map, date).ifPresent(segments::add);
            }
        }
        return List.copyOf(segments);
    }

    private java.util.Optional<RecordCoverageSegment> toSegment(Map<?, ?> map, LocalDate date) {
        LocalDateTime startTime = parseTime(firstPresent(map, "start_time", "startTime", "begin_time", "beginTime",
                "event_time", "eventTime", "last_modified", "lastModified", "start"));
        if (startTime == null) {
            startTime = parseOffsetTime(date, firstPresent(map, "start_offset_sec", "startOffsetSec"));
        }
        LocalDateTime endTime = parseTime(firstPresent(map, "end_time", "endTime", "stop_time", "stopTime", "end"));
        if (endTime == null) {
            endTime = parseOffsetTime(date, firstPresent(map, "end_offset_sec", "endOffsetSec"));
        }
        if (endTime == null && startTime != null) {
            Integer duration = parseInteger(firstPresent(map, "duration", "duration_sec", "durationSec"));
            if (duration != null && duration > 0) {
                endTime = startTime.plusSeconds(duration);
            }
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            return java.util.Optional.empty();
        }
        Integer motion = parseInteger(firstPresent(map, "motion", "motion_score", "motionScore"));
        if (motion == null && parseBoolean(firstPresent(map, "has_alert", "hasAlert"))) {
            motion = 1;
        }
        Integer objects = parseInteger(firstPresent(map, "objects", "object_count", "objectCount", "alert_count", "alertCount"));
        String recordUri = rewritePublicUri(toText(firstPresent(map, "record_uri", "recordUri", "play_url", "playUrl",
                "video_url", "videoUrl", "record_url", "recordUrl", "url", "file_path", "path")));
        Map<String, Object> metadata = new LinkedHashMap<>();
        copyIfPresent(metadata, "exportUrl", rewritePublicUri(toText(firstPresent(map, "export_url", "exportUrl"))));
        copyIfPresent(metadata, "motionHeatmap", firstPresent(map, "motion_heatmap", "motionHeatmap"));
        copyIfPresent(metadata, "source", firstPresent(map, "source", "message"));
        copyIfPresent(metadata, "segmentIds", segmentIds(map));
        String retainMode = toText(firstPresent(map, "retain_mode", "retainMode"));
        copyIfPresent(metadata, "retainMode", retainMode);
        copyIfPresent(metadata, "exportable", parseOptionalBoolean(firstPresent(map, "exportable", "can_export", "canExport")));
        copyIfPresent(metadata, "nonExportableReason", firstPresent(map, "non_exportable_reason", "nonExportableReason",
                "export_block_reason", "exportBlockReason"));
        copyIfPresent(metadata, "retainUntil", toText(firstPresent(map, "retain_until", "retainUntil",
                "expires_at", "expiresAt")));
        String status = normalizeCoverageStatus(toText(firstPresent(map, "status", "coverage_status", "coverageStatus")));
        if (!hasText(status)) {
            status = motion != null && motion > 0
                    ? SupervisionAlertReviewService.RECORD_COVERAGE_MOTION
                    : SupervisionAlertReviewService.RECORD_COVERAGE_AVAILABLE;
        }
        copyIfPresent(metadata, "coverageSource", coverageSource(map, retainMode, motion, status));
        return java.util.Optional.of(new RecordCoverageSegment(status, startTime, endTime, motion, recordUri, objects, metadata));
    }

    private static String coverageSource(Map<?, ?> map, String retainMode, Integer motion, String status) {
        String explicit = normalizeCoverageSource(toText(firstPresent(map, "coverage_source", "coverageSource",
                "source_type", "sourceType")));
        if (hasText(explicit)) {
            return explicit;
        }
        if (parseBoolean(firstPresent(map, "has_alert", "hasAlert"))) {
            return "alert";
        }
        if (parseBoolean(firstPresent(map, "has_detection", "hasDetection"))) {
            return "detection";
        }
        String retainSource = normalizeCoverageSource(retainMode);
        if (hasText(retainSource)) {
            return retainSource;
        }
        if (motion != null && motion > 0) {
            return "motion";
        }
        if (SupervisionAlertReviewService.RECORD_COVERAGE_MOTION.equals(status)) {
            return "motion";
        }
        if (SupervisionAlertReviewService.RECORD_COVERAGE_AVAILABLE.equals(status)) {
            return "continuous";
        }
        return null;
    }

    private static String normalizeCoverageSource(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "alert", "detection", "motion", "continuous" -> normalized;
            case "all", "record", "recording" -> "continuous";
            default -> null;
        };
    }

    private static String normalizeCoverageStatus(String value) {
        if (!hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        return switch (normalized) {
            case SupervisionAlertReviewService.RECORD_COVERAGE_AVAILABLE,
                 SupervisionAlertReviewService.RECORD_COVERAGE_MOTION,
                 SupervisionAlertReviewService.RECORD_COVERAGE_MISSING -> normalized;
            default -> null;
        };
    }

    private String rewritePublicUri(String uri) {
        if (!hasText(uri) || !hasText(publicPlayHost)) {
            return uri;
        }
        try {
            URI source = URI.create(uri);
            URI publicHost = URI.create(publicPlayHost);
            if (publicHost.getHost() == null) {
                return uri;
            }
            if (source.getHost() == null && !uri.startsWith("/video/") && !uri.startsWith("/api/")) {
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

    private static Object firstPresent(Map<?, ?> map, String... keys) {
        if (map == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void copyIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static Object segmentIds(Map<?, ?> map) {
        Object segmentIds = firstPresent(map, "segment_ids", "segmentIds");
        if (segmentIds != null) {
            return segmentIds;
        }
        Object segmentId = firstPresent(map, "id", "segment_id", "segmentId");
        return segmentId == null ? null : List.of(segmentId);
    }

    private static LocalDateTime parseTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            if (epoch > 10_000_000_000L) {
                epoch = epoch / 1000L;
            }
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
        }
        String text = String.valueOf(value);
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(text, QUERY_TIME_FORMATTER);
            } catch (DateTimeParseException ignoredAgain) {
                return null;
            }
        }
    }

    private static LocalDateTime parseOffsetTime(LocalDate date, Object value) {
        Integer offsetSeconds = parseInteger(value);
        if (date == null || offsetSeconds == null) {
            return null;
        }
        return date.atStartOfDay().plusSeconds(offsetSeconds);
    }

    private static LocalDate parseDate(Object value, LocalDate fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(String.valueOf(value));
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private static Boolean parseOptionalBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isRecordBaseUrl(String url) {
        if (!hasText(url)) {
            return false;
        }
        try {
            return isRecordBasePath(URI.create(url).getPath());
        } catch (Exception ignored) {
            return isRecordBasePath(url);
        }
    }

    private static boolean isRecordBasePath(String path) {
        String normalized = stripTrailingSlash(path);
        return normalized.endsWith("/video/record") || normalized.endsWith("/record");
    }

    private static String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value;
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
