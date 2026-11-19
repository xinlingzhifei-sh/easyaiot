package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RecordCoverageRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoExportRequest;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVideoSegmentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VideoMediaTimeZoneTest {
    private final VideoMediaTimeZone clock = new VideoMediaTimeZone(ZoneOffset.UTC, "Asia/Shanghai");
    private final LocalDateTime start = LocalDateTime.parse("2026-11-19T16:00:00");
    private final LocalDateTime end = start.plusSeconds(20);

    @Test
    void preservesDefaultAndReadsTimeZoneOffsets() {
        assertEquals(start, new VideoMediaTimeZone(ZoneOffset.UTC, "").toVideo(start));
        assertEquals(start, clock.parseVideo("2026-11-20T00:00:00+08:00"));
        assertEquals(start, clock.parseVideo("2026-11-19T16:00:00Z"));
        assertEquals(start, clock.parseVideo("2026-11-20 00:00:00"));
        assertNull(clock.parseVideo("not-a-time"));
    }

    @Test
    void queriesVideoCalendarDayAndKeepsPhysicalAndClippedWindows() {
        RestTemplate client = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(client);
        server.expect(req -> {
            String query = req.getURI().getQuery();
            assertTrue(query.contains("begin_time=2026-11-20 00:00:00"), query);
            assertTrue(query.contains("date=2026-11-20"), query);
        }).andRespond(withSuccess("""
              {"code":0,"data":{"segments":[{"start_time":"2026-11-20T00:00:00","end_time":"2026-11-20T00:00:20",
                "segment_start_time":"2026-11-19T23:59:59.123","segment_end_time":"2026-11-20T00:00:59.123",
                "record_uri":"/video/record/cam.flv","status":"available"}]}}
              """, MediaType.APPLICATION_JSON));
        HttpVideoRecordCoverageResolver resolver = new HttpVideoRecordCoverageResolver(client,
                "http://video.test/video/record/availability", "", mock(VideoMediaServiceRequestSigner.class));
        ReflectionTestUtils.setField(resolver, "mediaTime", clock);
        var result = resolver.resolve(new RecordCoverageRequest("cam", "cam", start, end)).get(0);
        assertEquals(start, result.startTime());
        assertEquals(end, result.endTime());
        assertEquals("2026-11-19T15:59:59.123", result.metadata().get("segmentStartTime"));
        assertEquals("2026-11-19T16:00:59.123", result.metadata().get("segmentEndTime"));
        server.verify();
    }

    @Test
    void exportsLocalWindowsAndReconcilesReturnedMediaTimes() {
        String uri = "/video/record/cam.flv";
        var segment = new ReviewEvidenceVideoSegmentRequest(1L,"1",uri,start.minusSeconds(5),end.plusSeconds(9),start,end,0);
        var request = new ReviewEvidenceVideoExportRequest(1L,1L,"cam","cam","1",start,end,uri,"mp4",List.of(segment),end.plusDays(1));
        var client = new HttpVideoEvidenceExportProvider(new RestTemplate(), "http://video.test/video/record/export", "",
                mock(VideoMediaServiceRequestSigner.class));
        ReflectionTestUtils.setField(client, "mediaTime", clock);
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(client, "requestBody", request);
        assertEquals("2026-11-20T00:00",body.get("start_time"));
        assertEquals("2026-11-21T00:00:20",body.get("expires_at"));
        Map<?, ?> row = (Map<?, ?>) ((List<?>) body.get("record_segments")).get(0);
        assertEquals("2026-11-19T23:59:55",row.get("segment_start_time"));
        assertEquals("2026-11-20T00:00:20",row.get("clip_end_time"));
        var resolved = new java.util.HashMap<Object,Object>(row);
        resolved.put("offset_seconds",5.0);
        resolved.put("clip_parameters",Map.of("duration_seconds",20.0));
        assertDoesNotThrow(() -> {ReflectionTestUtils.invokeMethod(client,"reconcileRecordSegments",request,List.of(resolved));});
    }
}
