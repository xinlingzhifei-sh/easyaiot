package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.service.supervision.HttpAlertRecordEvidenceResolver;
import com.basiclab.iot.system.service.supervision.HttpReviewIntelligenceProvider;
import com.basiclab.iot.system.service.supervision.HttpVideoEvidenceExportProvider;
import com.basiclab.iot.system.service.supervision.HttpVideoRecordCoverageResolver;
import com.basiclab.iot.system.service.supervision.VideoMediaServiceRequestSigner;
import com.basiclab.iot.system.service.supervision.ReviewIntelligenceProvider.ReviewAiSummaryRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewAiSummary;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageSegment;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordEvidenceResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoSegmentRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceDownloadArtifact;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoDownloadRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpVideoResolverTest {

    @Test
    void dockerComposeWiresReviewVideoUrlsToRealVideoRecordEndpointsByDefault() throws Exception {
        String applicationYaml = Files.readString(modulePath("src/main/resources/application.yaml"), StandardCharsets.UTF_8);
        String dockerCompose = Files.readString(modulePath("../../docker-compose.yml"), StandardCharsets.UTF_8);
        String coverageResolver = Files.readString(modulePath("src/main/java/com/basiclab/iot/system/service/supervision/HttpVideoRecordCoverageResolver.java"), StandardCharsets.UTF_8);
        String exportProvider = Files.readString(modulePath("src/main/java/com/basiclab/iot/system/service/supervision/HttpVideoEvidenceExportProvider.java"), StandardCharsets.UTF_8);
        String requestSigner = Files.readString(modulePath("src/main/java/com/basiclab/iot/system/service/supervision/VideoMediaServiceRequestSigner.java"), StandardCharsets.UTF_8);

        assertTrue(applicationYaml.contains("alert-record-query-url: ${YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL:}"));
        assertTrue(applicationYaml.contains("record-coverage-query-url: ${YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL:}"));
        assertTrue(applicationYaml.contains("record-base-url: ${YFEIEYE_VIDEO_RECORD_BASE_URL:}"));
        assertTrue(applicationYaml.contains("record-export-url: ${YFEIEYE_VIDEO_RECORD_EXPORT_URL:}"));
        assertTrue(applicationYaml.contains("public-play-host: ${YFEIEYE_VIDEO_PUBLIC_PLAY_HOST:${MEDIA_HTTP_PLAY_HOST:}}"));
        assertTrue(applicationYaml.contains("record-export-connect-timeout-ms: ${YFEIEYE_VIDEO_RECORD_EXPORT_CONNECT_TIMEOUT_MS:5000}"));
        assertTrue(applicationYaml.contains("record-export-read-timeout-ms: ${YFEIEYE_VIDEO_RECORD_EXPORT_READ_TIMEOUT_MS:30000}"));
        assertTrue(applicationYaml.contains("record-export-poll-timeout-ms: ${YFEIEYE_VIDEO_RECORD_EXPORT_POLL_TIMEOUT_MS:300000}"));
        assertTrue(applicationYaml.contains("media-service-allowed-actions: ${YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS:coverage,export,download,playback,manifest_verify}"));
        assertTrue(applicationYaml.contains("users:"));
        assertTrue(applicationYaml.contains("1: ${YFEIEYE_REVIEW_CAMERA_PERMISSION_USERS_1:}"));
        assertTrue(applicationYaml.contains("record_manage:"));
        assertTrue(applicationYaml.contains("system:supervision-alert-review:media:manage"));
        assertTrue(applicationYaml.contains("record-drift-retention-hours: ${YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS:24}"));
        assertTrue(coverageResolver.contains("@Value(\"${yfeieye.video.record-coverage-query-url:}\")"));
        assertFalse(coverageResolver.contains("record-coverage-query-url:${yfeieye.video.alert-record-query-url"));
        assertTrue(exportProvider.contains(".setConnectTimeout("));
        assertTrue(exportProvider.contains(".setReadTimeout("));
        assertFalse(exportProvider.contains("byte[].class"),
                "VIDEO downloads must not buffer the full artifact in the JVM heap");
        assertTrue(exportProvider.contains("Files.createTempFile("),
                "VIDEO downloads must stream into a bounded temporary file");
        assertTrue(requestSigner.contains("media-service-allowed-actions:coverage,export,download,playback,manifest_verify"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL=${YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL:-http://host.docker.internal:6000/video/alert/record/query}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL=${YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL:-http://host.docker.internal:6000/video/record/availability}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_BASE_URL=${YFEIEYE_VIDEO_RECORD_BASE_URL:-http://host.docker.internal:6000/video/record}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_EXPORT_URL=${YFEIEYE_VIDEO_RECORD_EXPORT_URL:-http://host.docker.internal:6000/video/record/export}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_PUBLIC_PLAY_HOST=${YFEIEYE_VIDEO_PUBLIC_PLAY_HOST:-}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_EXPORT_CONNECT_TIMEOUT_MS=${YFEIEYE_VIDEO_RECORD_EXPORT_CONNECT_TIMEOUT_MS:-5000}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_EXPORT_READ_TIMEOUT_MS=${YFEIEYE_VIDEO_RECORD_EXPORT_READ_TIMEOUT_MS:-30000}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_EXPORT_POLL_TIMEOUT_MS=${YFEIEYE_VIDEO_RECORD_EXPORT_POLL_TIMEOUT_MS:-300000}"));
        assertTrue(dockerCompose.contains("YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS=${YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS:-coverage,export,download,playback,manifest_verify}"));
        assertTrue(dockerCompose.contains("YFEIEYE_REVIEW_CAMERA_PERMISSION_USERS_1=${YFEIEYE_REVIEW_CAMERA_PERMISSION_USERS_1:-}"));
        assertTrue(dockerCompose.contains("YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS=${YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS:-24}"));
    }

    private static Path modulePath(String relativePath) {
        try {
            Path testClasses = Path.of(HttpVideoResolverTest.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return testClasses.getParent().getParent().resolve(relativePath).normalize();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve iot-system-biz module path", exception);
        }
    }

    @Test
    void runtimeOutboxNotifyUsesTenantScopedAdminRoutesWithoutGlobalFallback() throws Exception {
        String applicationYaml = Files.readString(modulePath("src/main/resources/application.yaml"), StandardCharsets.UTF_8);
        String dockerCompose = Files.readString(modulePath("../../docker-compose.yml"), StandardCharsets.UTF_8);

        assertTrue(applicationYaml.contains(
                "tenant-admin-user-routes: ${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES:}"));
        assertFalse(applicationYaml.contains(
                "admin-user-ids: ${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ADMIN_USER_IDS:[]}"));
        assertTrue(dockerCompose.contains(
                "YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES=${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES:-}"));
        assertFalse(dockerCompose.contains("YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ADMIN_USER_IDS="));
    }

    @Test
    void reviewIntelligenceProviderKeepsStructuredSummaryData() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals("http://ai.local/review/summary", request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": {
                            "title": "provider title",
                            "summary": "provider structured summary",
                            "structuredData": {
                              "scene": "gate / yard",
                              "threatLevel": "high",
                              "responsibilityUnit": "camera-01"
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpReviewIntelligenceProvider provider = new HttpReviewIntelligenceProvider(
                restTemplate,
                "",
                "http://ai.local/review/summary"
        );

        Optional<ReviewAiSummary> summary = provider.summarize(new ReviewAiSummaryRequest(
                900L,
                901L,
                List.of(1001L),
                List.of(),
                List.of()
        ));

        assertTrue(summary.isPresent());
        assertEquals("provider title", summary.get().title());
        assertEquals("gate / yard", summary.get().structuredData().get("scene"));
        assertEquals("high", summary.get().structuredData().get("threatLevel"));
        assertEquals("camera-01", summary.get().structuredData().get("responsibilityUnit"));
        server.verify();
    }

    @Test
    void alertRecordResolverParsesVideoPayloadAndRewritesRelativePlaybackUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    String url = request.getURI().toString();
                    assertTrue(url.startsWith("http://video.local/video/alert/record/query?"));
                    assertTrue(url.contains("device_id=camera-01"));
                    assertTrue(url.contains("alert_time=2026-06-30%2010:15:00"));
                    assertTrue(url.contains("time_range=300"));
                    assertTrue(url.contains("alert_id=alert-001"));
                    assertEquals("iot-system", request.getHeaders().getFirst("X-YFeiEye-Service-Id"));
                    assertEquals("camera-01", request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                    assertEquals("coverage", request.getHeaders().getFirst("X-YFeiEye-Service-Action"));
                    assertTrue(request.getHeaders().getFirst("X-YFeiEye-Service-Signature").startsWith("sha256="));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "msg": "success",
                          "data": {
                            "video_url": "http://host.docker.internal:6000/video/alert/record?path=%2Fdata%2Fplaybacks%2Fclip.flv",
                            "file_path": "/data/playbacks/clip.flv",
                            "event_time": "2026-06-30T10:14:30",
                            "source": "alert_record_path"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com/yfeieye/dev-api",
                testSigner()
        );

        Optional<RecordEvidenceResult> result = resolver.resolve(new RecordEvidenceRequest(
                "alert-001",
                null,
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 15)
        ));

        assertTrue(result.isPresent());
        assertEquals("https://eye.yfeiai.com/yfeieye/dev-api/video/alert/record?path=%2Fdata%2Fplaybacks%2Fclip.flv",
                result.get().recordUri());
        assertEquals("alert_record_path", result.get().message());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 14, 30), result.get().recordStartTime());
        server.verify();
    }

    @Test
    void alertRecordResolverAcceptsRecordStartTimeAliasFromVideoContract() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    assertTrue(request.getURI().toString()
                            .startsWith("http://video.local/video/alert/record/query?"));
                    assertTrue(request.getURI().toString().contains("device_id=device-01"));
                    assertEquals("device-01",
                            request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "data": {
                            "video_url": "/video/alert/record?path=%2Fdata%2Fplaybacks%2Falias.flv",
                            "recordStartTime": "2026-06-30 10:13:45",
                            "source": "playback_match"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        RecordEvidenceResult result = resolver.resolve(new RecordEvidenceRequest(
                "alert-alias",
                "device-01",
                "device-01",
                LocalDateTime.of(2026, 6, 30, 10, 15)
        )).orElseThrow();

        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 13, 45), result.recordStartTime());
        server.verify();
    }

    @Test
    void alertRecordResolverRejectsDeviceCameraScopeMismatch() {
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                new RestTemplate(),
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(
                new RecordEvidenceRequest(
                        "alert-scope-mismatch",
                        "device-01",
                        "camera-01",
                        LocalDateTime.of(2026, 6, 30, 10, 15)
                )));

        assertEquals("permission_denied", failure.getMessage());
    }

    @Test
    void alertRecordResolverReturnsEmptyWhenVideoServiceFails() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString()
                        .startsWith("http://video.local/video/alert/record/query?")))
                .andRespond(withServerError());
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(
                new RecordEvidenceRequest(
                        "alert-500",
                        "camera-01",
                        "camera-01",
                        LocalDateTime.of(2026, 6, 30, 10, 18)
                )));

        assertEquals("probe_failed", failure.getMessage());
        server.verify();
    }

    @Test
    void alertRecordResolverClassifiesVideoPermissionDenial() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString()
                        .startsWith("http://video.local/video/alert/record/query?")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"code":403,"reason":"camera_scope_denied"}
                                """));
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(
                new RecordEvidenceRequest(
                        "alert-denied",
                        null,
                        "camera-01",
                        LocalDateTime.of(2026, 6, 30, 10, 15)
                )));

        assertEquals("permission_denied", failure.getMessage());
        server.verify();
    }

    @Test
    void alertRecordResolverKeepsExplicitVideoRecordMissAsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString()
                        .startsWith("http://video.local/video/alert/record/query?")))
                .andRespond(withSuccess("""
                        {"code":400,"reason":"record_not_found","message":"record not found","data":null}
                        """, MediaType.APPLICATION_JSON));
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        Optional<RecordEvidenceResult> result = resolver.resolve(new RecordEvidenceRequest(
                "alert-missing",
                null,
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 15)
        ));

        assertTrue(result.isEmpty());
        server.verify();
    }

    @Test
    void alertRecordResolverRejectsAmbiguousBusinessFailures() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString().contains("alert_id=alert-code-500")))
                .andRespond(withSuccess("""
                        {"code":500,"message":"query failed","data":null}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertTrue(request.getURI().toString().contains("alert_id=alert-invalid")))
                .andRespond(withSuccess("""
                        {"code":400,"reason":"invalid_request","message":"bad alert time","data":null}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertTrue(request.getURI().toString().contains("alert_id=alert-no-url")))
                .andRespond(withSuccess("""
                        {"code":0,"message":"success","data":{"source":"playback_match"}}
                        """, MediaType.APPLICATION_JSON));
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        for (String alertId : List.of("alert-code-500", "alert-invalid", "alert-no-url")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class, () -> resolver.resolve(
                    new RecordEvidenceRequest(
                            alertId,
                            null,
                            "camera-01",
                            LocalDateTime.of(2026, 6, 30, 10, 15)
                    )));
            assertEquals("probe_failed", failure.getMessage());
        }
        server.verify();
    }

    @Test
    void alertRecordResolverReportsVideoUrlNotConfiguredWhenUrlIsEmpty() {
        HttpAlertRecordEvidenceResolver resolver = new HttpAlertRecordEvidenceResolver(
                new RestTemplate(),
                "",
                "https://eye.yfeiai.com",
                testSigner()
        );

        Optional<RecordEvidenceResult> result = resolver.resolve(new RecordEvidenceRequest(
                "alert-no-config",
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 19)
        ));

        assertTrue(result.isEmpty());
        assertEquals("video_url_not_configured", resolver.unavailableReason().orElse(null));
    }

    @Test
    void coverageResolverParsesAlertRecordQuerySinglePayloadWhenConfiguredAsFallback() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    assertTrue(request.getURI().toString()
                            .startsWith("http://video.local/video/alert/record/query?"));
                    assertEquals("camera-01", request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                    assertEquals("coverage", request.getHeaders().getFirst("X-YFeiEye-Service-Action"));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "message": "success",
                          "data": {
                            "video_url": "/video/alert/record?path=%2Fdata%2Fplaybacks%2Fsingle.flv",
                            "event_time": "2026-06-30T10:14:30",
                            "duration": 45,
                            "source": "playback_match"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = new HttpVideoRecordCoverageResolver(
                restTemplate,
                "http://video.local/video/alert/record/query",
                "https://eye.yfeiai.com",
                testSigner()
        );

        List<RecordCoverageSegment> segments = resolver.resolve(new RecordCoverageRequest(
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 10),
                LocalDateTime.of(2026, 6, 30, 10, 20)
        ));

        assertEquals(1, segments.size());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 14, 30), segments.get(0).startTime());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 15, 15), segments.get(0).endTime());
        assertEquals("https://eye.yfeiai.com/video/alert/record?path=%2Fdata%2Fplaybacks%2Fsingle.flv",
                segments.get(0).recordUri());
        assertEquals("playback_match", segments.get(0).metadata().get("source"));
        server.verify();
    }

    @Test
    void coverageResolverParsesVideoDayTimelineOffsetsFromRecordBlueprint() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    String url = request.getURI().toString();
                    assertTrue(url.startsWith("http://video.local/video/record/space/7/videos/day?"));
                    assertTrue(url.contains("date=2026-06-30"));
                    assertTrue(url.contains("device_id=device-01"));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "success",
                          "data": {
                            "date": "2026-06-30",
                            "timeline_merged": [
                              {
                                "start_offset_sec": 36600,
                                "end_offset_sec": 36690,
                                "has_recording": true,
                                "has_alert": true,
                                "alert_count": 2,
                                "segment_ids": [11, 12]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = new HttpVideoRecordCoverageResolver(
                restTemplate,
                "http://video.local/video/record/space/7/videos/day",
                "",
                testSigner()
        );

        List<RecordCoverageSegment> segments = resolver.resolve(new RecordCoverageRequest(
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 8),
                LocalDateTime.of(2026, 6, 30, 10, 14)
        ));

        assertEquals(1, segments.size());
        assertEquals("motion", segments.get(0).status());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 10), segments.get(0).startTime());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 11, 30), segments.get(0).endTime());
        assertEquals(2, segments.get(0).objects());
        assertEquals(List.of(11, 12), segments.get(0).metadata().get("segmentIds"));
        server.verify();
    }

    @Test
    void coverageResolverParsesAvailabilitySegmentsWithMissingAndExportUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString()
                        .startsWith("http://video.local/video/record/availability?")))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "success",
                          "data": {
                            "date": "2026-06-30",
                            "segments": [
                              {
                                "status": "available",
                                "start_time": "2026-06-30T10:00:00",
                                "end_time": "2026-06-30T10:01:00",
                                "play_url": "/video/record/space/7/video/live/device-01/clip.mp4",
                                "export_url": "/video/record/export",
                                "object_count": 2
                              },
                              {
                                "status": "missing",
                                "start_time": "2026-06-30T10:01:00",
                                "end_time": "2026-06-30T10:02:00",
                                "source": "coverage_gap"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = new HttpVideoRecordCoverageResolver(
                restTemplate,
                "http://video.local/video/record/availability",
                "https://eye.yfeiai.com/yfeieye/dev-api",
                testSigner()
        );

        List<RecordCoverageSegment> segments = resolver.resolve(new RecordCoverageRequest(
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                LocalDateTime.of(2026, 6, 30, 10, 2)
        ));

        assertEquals(2, segments.size());
        assertEquals("available", segments.get(0).status());
        assertEquals(2, segments.get(0).objects());
        assertEquals("https://eye.yfeiai.com/yfeieye/dev-api/video/record/space/7/video/live/device-01/clip.mp4",
                segments.get(0).recordUri());
        assertEquals("https://eye.yfeiai.com/yfeieye/dev-api/video/record/export", segments.get(0).metadata().get("exportUrl"));
        assertEquals("missing", segments.get(1).status());
        assertEquals("coverage_gap", segments.get(1).metadata().get("source"));
        server.verify();
    }

    @Test
    void coverageResolverPreservesRetainModeSourceAndNonExportableReason() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString()
                        .startsWith("http://video.local/video/record/availability?")))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "segments": [
                              {
                                "status": "available",
                                "start_time": "2026-06-30T10:03:00",
                                "end_time": "2026-06-30T10:04:00",
                                "retain_mode": "motion",
                                "has_detection": true,
                                "exportable": false,
                                "non_exportable_reason": "source_file_missing",
                                "retain_until": "2026-07-07T10:04:00"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = new HttpVideoRecordCoverageResolver(
                restTemplate,
                "http://video.local/video/record/availability",
                "",
                testSigner()
        );

        List<RecordCoverageSegment> segments = resolver.resolve(new RecordCoverageRequest(
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 0),
                LocalDateTime.of(2026, 6, 30, 10, 5)
        ));

        assertEquals(1, segments.size());
        assertEquals("motion", segments.get(0).metadata().get("retainMode"));
        assertEquals("detection", segments.get(0).metadata().get("coverageSource"));
        assertEquals(false, segments.get(0).metadata().get("exportable"));
        assertEquals("source_file_missing", segments.get(0).metadata().get("nonExportableReason"));
        assertEquals("2026-07-07T10:04:00", segments.get(0).metadata().get("retainUntil"));
        server.verify();
    }

    @Test
    void coverageResolverDiscoversRecordSpaceByDeviceWhenConfiguredWithRecordBaseUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/space/device/device-01",
                        request.getURI().toString()))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "success",
                          "data": {
                            "id": 9,
                            "device_id": "device-01"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    String url = request.getURI().toString();
                    assertTrue(url.startsWith("http://video.local/video/record/space/9/videos/day?"));
                    assertTrue(url.contains("date=2026-06-30"));
                    assertTrue(url.contains("device_id=device-01"));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "success",
                          "data": {
                            "date": "2026-06-30",
                            "segments": [
                              {
                                "id": 31,
                                "url": "/video/record/space/9/video/device-01/clip.flv",
                                "start_time": "2026-06-30T10:10:30",
                                "end_time": "2026-06-30T10:11:15",
                                "has_alert": false,
                                "alert_count": 0
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = new HttpVideoRecordCoverageResolver(
                restTemplate,
                "http://video.local/video/record",
                "https://eye.yfeiai.com",
                testSigner()
        );

        List<RecordCoverageSegment> segments = resolver.resolve(new RecordCoverageRequest(
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 8),
                LocalDateTime.of(2026, 6, 30, 10, 14)
        ));

        assertEquals(1, segments.size());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 10, 30), segments.get(0).startTime());
        assertEquals(LocalDateTime.of(2026, 6, 30, 10, 11, 15), segments.get(0).endTime());
        assertEquals("https://eye.yfeiai.com/video/record/space/9/video/device-01/clip.flv",
                segments.get(0).recordUri());
        assertEquals(List.of(31), segments.get(0).metadata().get("segmentIds"));
        server.verify();
    }

    @Test
    void coverageResolverFallsBackToDedicatedRecordBaseUrlWhenAvailabilityHasNoSegments() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertTrue(request.getURI().toString()
                        .startsWith("http://video.local/video/record/availability?")))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "success",
                          "data": {
                            "segments": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/space/device/device-01",
                        request.getURI().toString()))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "id": 9,
                            "device_id": "device-01"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    String url = request.getURI().toString();
                    assertTrue(url.startsWith("http://video.local/video/record/space/9/videos/day?"));
                    assertTrue(url.contains("date=2026-06-30"));
                    assertTrue(url.contains("device_id=device-01"));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "segments": [
                              {
                                "id": 31,
                                "url": "/video/record/space/9/video/device-01/fallback.flv",
                                "start_time": "2026-06-30T10:10:30",
                                "end_time": "2026-06-30T10:11:15"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = newCoverageResolver(
                restTemplate,
                "http://video.local/video/record/availability",
                "http://video.local/video/record",
                "https://eye.yfeiai.com"
        );

        List<RecordCoverageSegment> segments = resolver.resolve(new RecordCoverageRequest(
                "device-01",
                "camera-01",
                LocalDateTime.of(2026, 6, 30, 10, 8),
                LocalDateTime.of(2026, 6, 30, 10, 14)
        ));

        assertEquals(1, segments.size());
        assertEquals("https://eye.yfeiai.com/video/record/space/9/video/device-01/fallback.flv",
                segments.get(0).recordUri());
        assertEquals(List.of(31), segments.get(0).metadata().get("segmentIds"));
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderPostsExportWindowAndRewritesRelativeExportUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    String body = request.getBody().toString();
                    assertTrue(body.contains("camera-01"));
                    assertTrue(body.contains("alert-export-001"));
                    assertTrue(body.contains("2026-06-30T10:10"));
                    assertTrue(body.contains("2026-06-30T10:12"));
                    assertTrue(body.contains("\"record_segments\""));
                    assertTrue(body.contains("\"review_item_ids\":[1000]"));
                    assertTrue(body.contains("\"segment_start_time\":\"2026-06-30T10:09:30\""));
                    assertTrue(body.contains("\"segment_end_time\":\"2026-06-30T10:12:30\""));
                    assertTrue(body.contains("\"stitch_order\":0"));
                    assertTrue(body.contains("\"async_worker\":true"));
                    assertEquals("iot-system", request.getHeaders().getFirst("X-YFeiEye-Service-Id"));
                    assertEquals("camera-01", request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                    assertEquals("export", request.getHeaders().getFirst("X-YFeiEye-Service-Action"));
                })
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-001",
                            "status_url": "/video/record/export/exp-001",
                            "status": "pending",
                            "message": "accepted"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export/exp-001",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-001",
                            "status": "running"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export/exp-001",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-001",
                            "download_url": "/video/record/export/exp-001/download",
                            "manifest_url": "/video/record/export/exp-001/manifest",
                            "file_hash": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                            "ffmpeg_command_hash": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                            "record_segments": [
                              {
                                "originalRecordUri": "record.mp4",
                                "sourceHash": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                                "ffmpegCommandHash": "sha256:4444444444444444444444444444444444444444444444444444444444444444",
                                "stitchOrder": 0,
                                "segmentStartTime": "2026-06-30T10:09:30",
                                "segmentEndTime": "2026-06-30T10:12:30",
                                "clipStartTime": "2026-06-30T10:10:00",
                                "clipEndTime": "2026-06-30T10:12:00",
                                "clipParameters": {
                                  "clipStartTime": "2026-06-30T10:10:00",
                                  "clipEndTime": "2026-06-30T10:12:00",
                                  "offsetSeconds": 30.0,
                                  "durationSeconds": 120.0
                                }
                              }
                            ],
                            "status": "ready",
                            "message": "ffmpeg clipped and stitched evidence"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com/yfeieye/dev-api",
                testSigner()
        );

        Optional<ReviewEvidenceVideoExportResult> result = provider.export(new ReviewEvidenceVideoExportRequest(
                3000L,
                1000L,
                "device-01",
                "camera-01",
                "alert-export-001",
                LocalDateTime.of(2026, 6, 30, 10, 10),
                LocalDateTime.of(2026, 6, 30, 10, 12),
                "record.mp4",
                "mp4",
                List.of(new ReviewEvidenceVideoSegmentRequest(
                        1000L,
                        "alert-export-001",
                        "record.mp4",
                        LocalDateTime.of(2026, 6, 30, 10, 9, 30),
                        LocalDateTime.of(2026, 6, 30, 10, 12, 30),
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 12),
                        0
                ))
        ));

        assertTrue(result.isPresent());
        assertEquals("exp-001", result.get().exportId());
        assertEquals("https://eye.yfeiai.com/yfeieye/dev-api/video/record/export/exp-001/download", result.get().exportUri());
        assertEquals("https://eye.yfeiai.com/yfeieye/dev-api/video/record/export/exp-001/manifest", result.get().manifestUri());
        assertEquals("sha256:1111111111111111111111111111111111111111111111111111111111111111",
                result.get().fileHash());
        assertEquals("sha256:2222222222222222222222222222222222222222222222222222222222222222",
                result.get().ffmpegCommandHash());
        assertEquals(1, result.get().recordSegments().size());
        assertEquals("ready", result.get().status());
        assertEquals("ffmpeg clipped and stitched evidence", result.get().message());
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderRejectsReadyResultThatDoesNotReconcileRequestedSegments() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-segment-mismatch",
                            "download_url": "/video/record/export/exp-segment-mismatch/download",
                            "manifest_url": "/video/record/export/exp-segment-mismatch/manifest",
                            "file_hash": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                            "ffmpeg_command_hash": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                            "record_segments": [{
                              "originalRecordUri": "segment-a.mp4",
                              "sourceHash": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                              "ffmpegCommandHash": "sha256:4444444444444444444444444444444444444444444444444444444444444444",
                              "stitchOrder": 0,
                              "clipParameters": {"offsetSeconds": 0.0, "durationSeconds": 60.0}
                            }],
                            "status": "ready"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "device-01",
                        "camera-01",
                        "alert-segment-a",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 12),
                        "segment-a.mp4",
                        "mp4",
                        List.of(
                                new ReviewEvidenceVideoSegmentRequest(
                                        1000L,
                                        "alert-segment-a",
                                        "segment-a.mp4",
                                        LocalDateTime.of(2026, 6, 30, 10, 10),
                                        LocalDateTime.of(2026, 6, 30, 10, 11),
                                        0
                                ),
                                new ReviewEvidenceVideoSegmentRequest(
                                        1001L,
                                        "alert-segment-b",
                                        "segment-b.mp4",
                                        LocalDateTime.of(2026, 6, 30, 10, 11),
                                        LocalDateTime.of(2026, 6, 30, 10, 12),
                                        1
                                )
                        )
                )
        ));

        assertTrue(rejected.getMessage().contains("reconcile"));
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderRejectsReadyResultWithDifferentClipWindow() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-clip-window-mismatch",
                            "download_url": "/video/record/export/exp-clip-window-mismatch/download",
                            "manifest_url": "/video/record/export/exp-clip-window-mismatch/manifest",
                            "file_hash": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                            "ffmpeg_command_hash": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                            "record_segments": [{
                              "originalRecordUri": "segment-a.mp4",
                              "sourceHash": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                              "ffmpegCommandHash": "sha256:4444444444444444444444444444444444444444444444444444444444444444",
                              "stitchOrder": 0,
                              "clipStartTime": "2026-06-30T10:11:00",
                              "clipEndTime": "2026-06-30T10:12:00",
                              "clipParameters": {
                                "clipStartTime": "2026-06-30T10:11:00",
                                "clipEndTime": "2026-06-30T10:12:00",
                                "offsetSeconds": 0.0,
                                "durationSeconds": 60.0
                              }
                            }],
                            "status": "ready"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "device-01",
                        "camera-01",
                        "alert-segment-a",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 11),
                        "segment-a.mp4",
                        "mp4"
                )
        ));

        assertTrue(rejected.getMessage().contains("clip window"), rejected.getMessage());
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderRejectsReadyResultWithDifferentPhysicalWindow() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-physical-window-mismatch",
                            "download_url": "/video/record/export/exp-physical-window-mismatch/download",
                            "manifest_url": "/video/record/export/exp-physical-window-mismatch/manifest",
                            "file_hash": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                            "ffmpeg_command_hash": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                            "record_segments": [{
                              "originalRecordUri": "segment-a.mp4",
                              "sourceHash": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                              "ffmpegCommandHash": "sha256:4444444444444444444444444444444444444444444444444444444444444444",
                              "stitchOrder": 0,
                              "segmentStartTime": "2026-06-30T10:09:31",
                              "segmentEndTime": "2026-06-30T10:12:30",
                              "clipStartTime": "2026-06-30T10:10:00",
                              "clipEndTime": "2026-06-30T10:11:00",
                              "clipParameters": {
                                "offsetSeconds": 29.0,
                                "durationSeconds": 60.0
                              }
                            }],
                            "status": "ready"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "device-01",
                        "camera-01",
                        "alert-segment-a",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 11),
                        "segment-a.mp4",
                        "mp4",
                        List.of(new ReviewEvidenceVideoSegmentRequest(
                                1000L,
                                "alert-segment-a",
                                "segment-a.mp4",
                                LocalDateTime.of(2026, 6, 30, 10, 9, 30),
                                LocalDateTime.of(2026, 6, 30, 10, 12, 30),
                                LocalDateTime.of(2026, 6, 30, 10, 10),
                                LocalDateTime.of(2026, 6, 30, 10, 11),
                                0
                        ))
                )
        ));

        assertTrue(rejected.getMessage().contains("physical window"), rejected.getMessage());
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderRejectsReadyResultWithDifferentClipOffset() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-clip-offset-mismatch",
                            "download_url": "/video/record/export/exp-clip-offset-mismatch/download",
                            "manifest_url": "/video/record/export/exp-clip-offset-mismatch/manifest",
                            "file_hash": "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                            "ffmpeg_command_hash": "sha256:2222222222222222222222222222222222222222222222222222222222222222",
                            "record_segments": [{
                              "originalRecordUri": "segment-a.mp4",
                              "sourceHash": "sha256:3333333333333333333333333333333333333333333333333333333333333333",
                              "ffmpegCommandHash": "sha256:4444444444444444444444444444444444444444444444444444444444444444",
                              "stitchOrder": 0,
                              "segmentStartTime": "2026-06-30T10:09:30",
                              "segmentEndTime": "2026-06-30T10:12:30",
                              "clipStartTime": "2026-06-30T10:10:00",
                              "clipEndTime": "2026-06-30T10:11:00",
                              "clipParameters": {
                                "offsetSeconds": 31.0,
                                "durationSeconds": 60.0
                              }
                            }],
                            "status": "ready"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "device-01",
                        "camera-01",
                        "alert-segment-a",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 11),
                        "segment-a.mp4",
                        "mp4",
                        List.of(new ReviewEvidenceVideoSegmentRequest(
                                1000L,
                                "alert-segment-a",
                                "segment-a.mp4",
                                LocalDateTime.of(2026, 6, 30, 10, 9, 30),
                                LocalDateTime.of(2026, 6, 30, 10, 12, 30),
                                LocalDateTime.of(2026, 6, 30, 10, 10),
                                LocalDateTime.of(2026, 6, 30, 10, 11),
                                0
                        ))
                )
        ));

        assertTrue(rejected.getMessage().contains("clip offset"), rejected.getMessage());
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderRejectsShortFakeHashes() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-fake-hash",
                            "download_url": "/video/record/export/exp-fake-hash/download",
                            "manifest_url": "/video/record/export/exp-fake-hash/manifest",
                            "file_hash": "sha256:media",
                            "ffmpeg_command_hash": "sha256:command",
                            "record_segments": [{
                              "sourceHash": "sha256:source",
                              "ffmpegCommandHash": "sha256:clip",
                              "stitchOrder": 0,
                              "clipParameters": {"offsetSeconds": 0.0, "durationSeconds": 1.0}
                            }],
                            "status": "ready"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "camera-01",
                        "camera-01",
                        "alert-fake-hash",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 11),
                        "record.mp4",
                        "mp4"
                )
        ));

        assertTrue(rejected.getMessage().contains("hash"));
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderRejectsOffOriginStatusUrl() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-off-origin",
                            "status_url": "http://attacker.invalid/export/exp-off-origin",
                            "status": "pending"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        IllegalStateException rejected = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "camera-01",
                        "camera-01",
                        "alert-off-origin",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 11),
                        "record.mp4",
                        "mp4"
                )
        ));

        assertTrue(rejected.getMessage().contains("status URL"));
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderDownloadsInternalPackageAndVerifiesRealBytes() throws Exception {
        byte[] packageBytes = "verified-real-video-bytes".getBytes(StandardCharsets.UTF_8);
        String expectedHash = "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(packageBytes));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    assertEquals("http://video.local/video/record/export/exp-download/download",
                            request.getURI().toString());
                    assertEquals("download", request.getHeaders().getFirst("X-YFeiEye-Service-Action"));
                    assertEquals("camera-01", request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(packageBytes, MediaType.valueOf("video/mp4")));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        ReviewEvidenceDownloadArtifact artifact = provider.download(new ReviewEvidenceVideoDownloadRequest(
                "https://eye.yfeiai.com/video/record/export/exp-download/download",
                "camera-01",
                "sha256:" + expectedHash.substring("sha256:".length()).toUpperCase(java.util.Locale.ROOT)
        )).orElseThrow();

        assertArrayEquals(packageBytes, Files.readAllBytes(artifact.temporaryFile()));
        assertEquals(packageBytes.length, artifact.contentLength());
        assertEquals(expectedHash, artifact.fileHash());
        assertEquals("video/mp4", artifact.contentType());
        Path temporaryFile = artifact.temporaryFile();
        assertTrue(Files.exists(temporaryFile));
        artifact.close();
        assertFalse(Files.exists(temporaryFile));
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderDownloadsPackageFromPrefixedPublicUrl() throws Exception {
        byte[] packageBytes = "prefixed-public-video-bytes".getBytes(StandardCharsets.UTF_8);
        String expectedHash = "sha256:" + java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(packageBytes));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export/exp-prefixed/download?yf_ticket=v1",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(packageBytes, MediaType.valueOf("video/mp4")));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com/yfeieye/dev-api",
                testSigner()
        );

        ReviewEvidenceDownloadArtifact artifact = provider.download(new ReviewEvidenceVideoDownloadRequest(
                "https://eye.yfeiai.com/yfeieye/dev-api/video/record/export/exp-prefixed/download?yf_ticket=v1",
                "camera-01",
                expectedHash
        )).orElseThrow();

        assertArrayEquals(packageBytes, Files.readAllBytes(artifact.temporaryFile()));
        artifact.close();
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderDeletesTemporaryFileAfterHashVerificationFailure() throws Exception {
        byte[] packageBytes = "tampered-video-bytes".getBytes(StandardCharsets.UTF_8);
        Set<Path> temporaryFilesBefore = videoExportTemporaryFiles();
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/export/exp-tampered/download",
                        request.getURI().toString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(packageBytes, MediaType.valueOf("video/mp4")));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );

        assertThrows(SecurityException.class, () -> provider.download(new ReviewEvidenceVideoDownloadRequest(
                "https://eye.yfeiai.com/video/record/export/exp-tampered/download",
                "camera-01",
                "sha256:0000000000000000000000000000000000000000000000000000000000000000"
        )));

        assertEquals(temporaryFilesBefore, videoExportTemporaryFiles());
        server.verify();
    }

    @Test
    void videoEvidenceExportProviderStopsPollingAtConfiguredHardDeadline() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    assertEquals("http://video.local/video/record/export", request.getURI().toString());
                    java.util.concurrent.locks.LockSupport.parkNanos(java.time.Duration.ofMillis(5).toNanos());
                })
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "export_id": "exp-timeout",
                            "status": "pending"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoEvidenceExportProvider provider = new HttpVideoEvidenceExportProvider(
                restTemplate,
                "http://video.local/video/record/export",
                "https://eye.yfeiai.com",
                testSigner()
        );
        ReflectionTestUtils.setField(provider, "pollTimeoutMillis", 1L);
        ReflectionTestUtils.setField(provider, "pollIntervalMillis", 10L);

        IllegalStateException timeout = assertThrows(IllegalStateException.class, () -> provider.export(
                new ReviewEvidenceVideoExportRequest(
                        3000L,
                        1000L,
                        "device-01",
                        "camera-01",
                        "alert-timeout",
                        LocalDateTime.of(2026, 6, 30, 10, 10),
                        LocalDateTime.of(2026, 6, 30, 10, 11),
                        "record.mp4",
                        "mp4"
                )
        ));

        assertTrue(timeout.getMessage().contains("hard timeout"));
        server.verify();
    }

    private static HttpVideoRecordCoverageResolver newCoverageResolver(RestTemplate restTemplate,
                                                                       String recordCoverageQueryUrl,
                                                                       String recordBaseUrl,
                                                                       String publicPlayHost) {
        try {
            Constructor<HttpVideoRecordCoverageResolver> constructor = HttpVideoRecordCoverageResolver.class
                    .getConstructor(RestTemplate.class, String.class, String.class, String.class,
                            VideoMediaServiceRequestSigner.class);
            return constructor.newInstance(restTemplate, recordCoverageQueryUrl, recordBaseUrl, publicPlayHost,
                    testSigner());
        } catch (NoSuchMethodException e) {
            fail("HttpVideoRecordCoverageResolver must accept a dedicated record base URL");
            return null;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static VideoMediaServiceRequestSigner testSigner() {
        return new VideoMediaServiceRequestSigner(
                "resolver-test-secret",
                "iot-system",
                () -> "42",
                () -> "7",
                () -> 1_720_580_000L,
                () -> "resolver-test-nonce",
                (action, cameraId) -> true
        );
    }

    private static Set<Path> videoExportTemporaryFiles() throws Exception {
        try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files
                    .filter(path -> path.getFileName().toString().startsWith("yfeieye-video-export-"))
                    .collect(Collectors.toSet());
        }
    }

}
