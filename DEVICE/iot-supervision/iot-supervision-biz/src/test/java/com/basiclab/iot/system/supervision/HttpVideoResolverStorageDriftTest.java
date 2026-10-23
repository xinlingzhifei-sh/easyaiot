package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.service.supervision.HttpVideoRecordStorageDriftResolver;
import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver.RecordStorageDriftReport;
import com.basiclab.iot.system.service.supervision.ReviewRecordStorageDriftResolver.RecordStorageDriftRequest;
import com.basiclab.iot.system.service.supervision.VideoMediaServiceRequestSigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpVideoResolverStorageDriftTest {

    @Test
    void resolvesSpaceThenConsumesAuthenticatedVideoStorageDriftReport() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> {
                    assertEquals("http://video.local/video/record/space/device/device-01",
                            request.getURI().toString());
                    assertEquals("device-01", request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                    assertEquals("coverage", request.getHeaders().getFirst("X-YFeiEye-Service-Action"));
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"code":0,"data":{"id":9,"device_id":"device-01"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(request -> {
                    assertEquals(
                            "http://video.local/video/record/space/9/videos/drift?retention_hours=24&device_id=device-01",
                            request.getURI().toString());
                    assertEquals("device-01", request.getHeaders().getFirst("X-YFeiEye-Service-Camera-Id"));
                    assertEquals("coverage", request.getHeaders().getFirst("X-YFeiEye-Service-Action"));
                })
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": {
                            "space_id": 9,
                            "device_id": "device-01",
                            "summary": {
                              "record_count": 8,
                              "issue_count": 3,
                              "issue_reasons": {"file_missing": 2, "cache_flush_failed": 1},
                              "standard_reason_keys": [
                                "file_missing", "retention_expired", "disk_full", "cache_flush_failed"
                              ],
                              "healthy": false
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        HttpVideoRecordStorageDriftResolver resolver = new HttpVideoRecordStorageDriftResolver(
                restTemplate,
                "http://video.local/video/record",
                testSigner()
        );

        RecordStorageDriftReport report = resolver.inspect(new RecordStorageDriftRequest(
                "device-01", "camera-01", 24
        ));

        assertFalse(report.healthy());
        assertEquals(9L, report.spaceId());
        assertEquals(8, report.recordCount());
        assertEquals(3, report.issueCount());
        assertEquals(2, report.issueReasons().get("file_missing"));
        assertEquals(1, report.issueReasons().get("cache_flush_failed"));
        assertTrue(report.standardReasonKeys().contains("disk_full"));
        server.verify();
    }

    @Test
    void missingVideoBaseUrlDegradesToStandardConfigurationReason() {
        HttpVideoRecordStorageDriftResolver resolver = new HttpVideoRecordStorageDriftResolver(
                new RestTemplate(), "", testSigner()
        );

        RecordStorageDriftReport report = resolver.inspect(new RecordStorageDriftRequest(
                "device-01", "camera-01", 24
        ));

        assertFalse(report.healthy());
        assertEquals(1, report.issueReasons().get("video_url_not_configured"));
        assertEquals("video_url_not_configured", report.message());
    }

    @Test
    void videoProbeFailureDegradesWithoutBreakingScheduledPatrol() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(request -> assertEquals(
                        "http://video.local/video/record/space/device/device-01",
                        request.getURI().toString()))
                .andRespond(withServerError());
        HttpVideoRecordStorageDriftResolver resolver = new HttpVideoRecordStorageDriftResolver(
                restTemplate, "http://video.local/video/record", testSigner()
        );

        RecordStorageDriftReport report = resolver.inspect(new RecordStorageDriftRequest(
                "device-01", "camera-01", 24
        ));

        assertFalse(report.healthy());
        assertEquals(1, report.issueReasons().get("probe_failed"));
        assertEquals("probe_failed", report.message());
        server.verify();
    }

    private static VideoMediaServiceRequestSigner testSigner() {
        return new VideoMediaServiceRequestSigner(
                "resolver-storage-drift-test-secret",
                "iot-system",
                () -> "42",
                () -> "7",
                () -> 1_720_580_000L,
                () -> "storage-drift-test-nonce",
                (action, cameraId) -> true
        );
    }
}
