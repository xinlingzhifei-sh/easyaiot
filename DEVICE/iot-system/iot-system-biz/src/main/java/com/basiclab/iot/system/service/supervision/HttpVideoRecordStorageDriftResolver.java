package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver.RecordStorageDriftReport;
import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver.RecordStorageDriftRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class HttpVideoRecordStorageDriftResolver implements ReviewRecordStorageDriftResolver {

    private static final List<String> STANDARD_REASON_KEYS = List.of(
            "video_url_not_configured",
            "record_space_not_found",
            "file_missing",
            "probe_failed",
            "permission_denied",
            "retention_expired",
            "disk_full",
            "cache_flush_failed"
    );

    private final RestTemplate restTemplate;
    private final String recordBaseUrl;
    private final VideoMediaServiceRequestSigner mediaRequestSigner;

    @Autowired
    public HttpVideoRecordStorageDriftResolver(
            RestTemplate restTemplate,
            @Value("${yfeieye.video.record-base-url:}") String recordBaseUrl,
            VideoMediaServiceRequestSigner mediaRequestSigner) {
        this.restTemplate = restTemplate;
        this.recordBaseUrl = stripTrailingSlash(recordBaseUrl);
        this.mediaRequestSigner = mediaRequestSigner;
    }

    @Override
    public RecordStorageDriftReport inspect(RecordStorageDriftRequest request) {
        if (request == null || !hasText(request.deviceId())) {
            return failure(request, null, "record_space_not_found");
        }
        if (!hasText(recordBaseUrl)) {
            return failure(request, null, "video_url_not_configured");
        }
        Long spaceId;
        try {
            String spaceUrl = UriComponentsBuilder.fromHttpUrl(recordBaseUrl)
                    .pathSegment("space", "device", request.deviceId())
                    .build()
                    .encode()
                    .toUriString();
            Map<?, ?> spaceData = responseData(signedGet(spaceUrl, request.deviceId()));
            spaceId = parseLong(firstPresent(spaceData, "id", "space_id", "spaceId"));
            if (spaceId == null) {
                return failure(request, null, "record_space_not_found");
            }
        } catch (HttpClientErrorException.BadRequest exception) {
            return failure(request, null, "record_space_not_found");
        } catch (RestClientResponseException exception) {
            return failure(request, null, permissionReason(exception));
        } catch (SecurityException exception) {
            return failure(request, null, "permission_denied");
        } catch (RuntimeException exception) {
            return failure(request, null, "probe_failed");
        }

        try {
            int retentionHours = request.retentionHours() == null
                    ? 24
                    : Math.max(1, request.retentionHours());
            String driftUrl = UriComponentsBuilder.fromHttpUrl(recordBaseUrl)
                    .pathSegment("space", String.valueOf(spaceId), "videos", "drift")
                    .queryParam("retention_hours", retentionHours)
                    .queryParam("device_id", request.deviceId())
                    .build()
                    .encode()
                    .toUriString();
            Map<?, ?> data = responseData(signedGet(driftUrl, request.deviceId()));
            Map<?, ?> summary = data.get("summary") instanceof Map<?, ?> values ? values : Map.of();
            Map<String, Integer> issueReasons = parseReasonCounts(
                    firstPresent(summary, "issue_reasons", "issueReasons"));
            int issueCount = parseInteger(firstPresent(summary, "issue_count", "issueCount"),
                    issueReasons.values().stream().mapToInt(Integer::intValue).sum());
            int recordCount = parseInteger(firstPresent(summary, "record_count", "recordCount"), 0);
            boolean healthy = parseBoolean(summary.get("healthy"), issueCount == 0);
            List<String> standardReasonKeys = parseTextList(
                    firstPresent(summary, "standard_reason_keys", "standardReasonKeys"));
            if (standardReasonKeys.isEmpty()) {
                standardReasonKeys = STANDARD_REASON_KEYS;
            }
            return new RecordStorageDriftReport(
                    request.deviceId(),
                    request.cameraId(),
                    spaceId,
                    healthy,
                    recordCount,
                    issueCount,
                    Map.copyOf(issueReasons),
                    List.copyOf(standardReasonKeys),
                    healthy ? "healthy" : firstReason(issueReasons, "storage_drift_detected"),
                    LocalDateTime.now()
            );
        } catch (RestClientResponseException exception) {
            return failure(request, spaceId, permissionReason(exception));
        } catch (SecurityException exception) {
            return failure(request, spaceId, "permission_denied");
        } catch (RuntimeException exception) {
            return failure(request, spaceId, "probe_failed");
        }
    }

    private Map<?, ?> signedGet(String url, String scopedDeviceId) {
        URI requestUri = URI.create(url);
        HttpEntity<Void> entity = new HttpEntity<>(mediaRequestSigner.sign(
                HttpMethod.GET,
                requestUri,
                "coverage",
                scopedDeviceId,
                ""
        ));
        return restTemplate.exchange(requestUri, HttpMethod.GET, entity, Map.class).getBody();
    }

    private static Map<?, ?> responseData(Map<?, ?> response) {
        if (response == null || response.isEmpty()) {
            throw new IllegalStateException("empty VIDEO storage drift response");
        }
        Object code = response.get("code");
        if (code != null && parseInteger(code, -1) != 0) {
            throw new IllegalStateException("VIDEO storage drift response rejected");
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> values) {
            return values;
        }
        return response;
    }

    private static RecordStorageDriftReport failure(RecordStorageDriftRequest request,
                                                    Long spaceId,
                                                    String reason) {
        String deviceId = request == null ? null : request.deviceId();
        String cameraId = request == null ? null : request.cameraId();
        return new RecordStorageDriftReport(
                deviceId,
                cameraId,
                spaceId,
                false,
                0,
                1,
                Map.of(reason, 1),
                STANDARD_REASON_KEYS,
                reason,
                LocalDateTime.now()
        );
    }

    private static String permissionReason(RestClientResponseException exception) {
        int status = exception.getRawStatusCode();
        return status == 401 || status == 403 ? "permission_denied" : "probe_failed";
    }

    private static Map<String, Integer> parseReasonCounts(Object value) {
        if (!(value instanceof Map<?, ?> values)) {
            return Map.of();
        }
        Map<String, Integer> reasons = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String reason = normalizeReason(entry.getKey());
            int count = parseInteger(entry.getValue(), 0);
            if (hasText(reason) && count > 0) {
                reasons.merge(reason, count, Integer::sum);
            }
        }
        return reasons;
    }

    private static List<String> parseTextList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object entry : values) {
            String reason = normalizeReason(entry);
            if (hasText(reason) && !result.contains(reason)) {
                result.add(reason);
            }
        }
        return result;
    }

    private static String normalizeReason(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
        while (normalized.contains("__")) {
            normalized = normalized.replace("__", "_");
        }
        return normalized;
    }

    private static String firstReason(Map<String, Integer> reasons, String fallback) {
        return reasons.keySet().stream().findFirst().orElse(fallback);
    }

    private static Object firstPresent(Map<?, ?> values, String... keys) {
        if (values == null) {
            return null;
        }
        for (String key : keys) {
            if (values.get(key) != null) {
                return values.get(key);
            }
        }
        return null;
    }

    private static int parseInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
