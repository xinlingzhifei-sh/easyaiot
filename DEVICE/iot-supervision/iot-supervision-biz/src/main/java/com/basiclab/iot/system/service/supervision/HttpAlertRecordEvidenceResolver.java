package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceResolver;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Service
public class HttpAlertRecordEvidenceResolver implements RecordEvidenceResolver {

    private static final DateTimeFormatter ALERT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int DEFAULT_TIME_RANGE_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final String alertRecordQueryUrl;
    private final String publicPlayHost;
    private final VideoMediaServiceRequestSigner mediaRequestSigner;

    public HttpAlertRecordEvidenceResolver(RestTemplate restTemplate,
                                           @Value("${yfeieye.video.alert-record-query-url:}") String alertRecordQueryUrl,
                                           @Value("${yfeieye.video.public-play-host:${MEDIA_HTTP_PLAY_HOST:}}") String publicPlayHost,
                                           VideoMediaServiceRequestSigner mediaRequestSigner) {
        this.restTemplate = restTemplate;
        this.alertRecordQueryUrl = alertRecordQueryUrl;
        this.publicPlayHost = publicPlayHost;
        this.mediaRequestSigner = mediaRequestSigner;
    }

    @Override
    public Optional<RecordEvidenceResult> resolve(RecordEvidenceRequest request) {
        if (!hasText(alertRecordQueryUrl)) {
            return Optional.empty();
        }
        String deviceId = hasText(request.deviceId()) ? request.deviceId() : request.cameraId();
        if (hasText(request.deviceId()) && hasText(request.cameraId())
                && !request.deviceId().equals(request.cameraId())) {
            throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PERMISSION_DENIED);
        }
        String url = UriComponentsBuilder.fromHttpUrl(alertRecordQueryUrl)
                .queryParam("device_id", deviceId)
                .queryParam("alert_time", request.alertTime().format(ALERT_TIME_FORMATTER))
                .queryParam("time_range", DEFAULT_TIME_RANGE_SECONDS)
                .queryParam("alert_id", request.sourceAlertId())
                .build()
                .encode()
                .toUriString();

        try {
            URI requestUri = URI.create(url);
            HttpEntity<Void> entity = new HttpEntity<>(mediaRequestSigner.sign(
                    HttpMethod.GET,
                    requestUri,
                    "coverage",
                    deviceId,
                    ""
            ));
            Map<?, ?> response = restTemplate.exchange(
                    requestUri, HttpMethod.GET, entity, Map.class).getBody();
            Integer businessCode = responseCode(response);
            if (businessCode != null && (businessCode == 401 || businessCode == 403)) {
                throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PERMISSION_DENIED);
            }
            if (isExplicitRecordMiss(response, businessCode)) {
                return Optional.empty();
            }
            if ((response != null && response.containsKey("code") && businessCode == null)
                    || (businessCode != null && businessCode != 0 && businessCode != 200)) {
                throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PROBE_FAILED);
            }
            Map<?, ?> data = responseData(response);
            if (data == null) {
                throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PROBE_FAILED);
            }
            String recordUri = rewritePublicUri(firstText(data.get("video_url"), data.get("record_url"),
                    data.get("play_url"), data.get("url"), data.get("file_path")));
            if (!hasText(recordUri)) {
                throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PROBE_FAILED);
            }
            String message = firstText(data.get("source"), data.get("message"), data.get("msg"));
            LocalDateTime recordStartTime = parseRecordStartTime(firstText(
                    data.get("recordStartTime"),
                    data.get("record_start_time"),
                    data.get("event_time"),
                    data.get("eventTime")
            ));
            return Optional.of(new RecordEvidenceResult(recordUri, message, recordStartTime));
        } catch (HttpStatusCodeException exception) {
            int status = exception.getStatusCode().value();
            String reason = status == 401 || status == 403
                    ? SupervisionAlertReviewService.RECORD_GAP_PERMISSION_DENIED
                    : SupervisionAlertReviewService.RECORD_GAP_PROBE_FAILED;
            throw new IllegalStateException(reason, exception);
        } catch (SecurityException exception) {
            throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PERMISSION_DENIED, exception);
        } catch (RuntimeException exception) {
            if (SupervisionAlertReviewService.RECORD_GAP_PROBE_FAILED.equals(exception.getMessage())
                    || SupervisionAlertReviewService.RECORD_GAP_PERMISSION_DENIED.equals(exception.getMessage())) {
                throw exception;
            }
            throw new IllegalStateException(SupervisionAlertReviewService.RECORD_GAP_PROBE_FAILED, exception);
        }
    }

    @Override
    public Optional<String> unavailableReason() {
        return hasText(alertRecordQueryUrl)
                ? Optional.empty()
                : Optional.of(SupervisionAlertReviewService.RECORD_GAP_VIDEO_URL_NOT_CONFIGURED);
    }

    private static Map<?, ?> responseData(Map<?, ?> response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        if (response.containsKey("data")) {
            Object data = response.get("data");
            return data instanceof Map<?, ?> dataMap ? dataMap : null;
        }
        return response;
    }

    private static boolean isExplicitRecordMiss(Map<?, ?> response, Integer businessCode) {
        if (response == null || businessCode == null || businessCode != 400) {
            return false;
        }
        return response.get("data") == null
                && SupervisionAlertReviewService.RECORD_GAP_RECORD_NOT_FOUND.equals(
                firstText(response.get("reason")));
    }

    private static Integer responseCode(Map<?, ?> response) {
        if (response == null || !response.containsKey("code")) {
            return null;
        }
        Object value = response.get("code");
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
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

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = toText(value);
            if (hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static LocalDateTime parseRecordStartTime(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ignoredIso) {
                try {
                    return LocalDateTime.parse(value, ALERT_TIME_FORMATTER);
                } catch (DateTimeParseException ignoredLegacy) {
                    return null;
                }
            }
        }
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
