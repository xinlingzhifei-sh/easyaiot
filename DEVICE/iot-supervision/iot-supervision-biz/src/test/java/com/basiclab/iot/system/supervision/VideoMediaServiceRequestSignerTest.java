package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.service.supervision.VideoMediaServiceRequestSigner;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoMediaServiceRequestSignerTest {

    @Test
    void signsTheSameCanonicalContextAsVideo() {
        VideoMediaServiceRequestSigner signer = new VideoMediaServiceRequestSigner(
                "secret-1",
                "iot-system",
                () -> "42",
                () -> "7",
                () -> 1_720_580_000L,
                () -> "nonce-1",
                (action, cameraId) -> true
        );

        HttpHeaders headers = signer.sign(
                HttpMethod.POST,
                URI.create("http://video.local/video/record/export?ignored=query"),
                "export",
                "camera-01",
                "{\"camera_id\":\"camera-01\"}"
        );

        assertEquals("iot-system", headers.getFirst("X-YFeiEye-Service-Id"));
        assertEquals("42", headers.getFirst("X-YFeiEye-Service-User-Id"));
        assertEquals("7", headers.getFirst("X-YFeiEye-Service-Tenant-Id"));
        assertEquals("camera-01", headers.getFirst("X-YFeiEye-Service-Camera-Id"));
        assertEquals("export", headers.getFirst("X-YFeiEye-Service-Action"));
        assertEquals("1720580000", headers.getFirst("X-YFeiEye-Service-Timestamp"));
        assertEquals("nonce-1", headers.getFirst("X-YFeiEye-Service-Nonce"));
        assertEquals(
                "sha256=06a883ecd564d2ca00a8f54b4ac1c5e4f4f96996cc0d6f4d3d347ac4fc82ae9b",
                headers.getFirst("X-YFeiEye-Service-Signature")
        );
    }

    @Test
    void queryStringIsCoveredByTheServiceSignature() {
        VideoMediaServiceRequestSigner signer = new VideoMediaServiceRequestSigner(
                "secret-1", "iot-system", () -> "42", () -> "7",
                () -> 1_720_580_000L, () -> "nonce-1", (action, cameraId) -> true
        );

        String first = signer.sign(
                HttpMethod.GET,
                URI.create("http://video.local/video/record/availability?camera_id=camera-01&time_range=60"),
                "coverage", "camera-01", ""
        ).getFirst("X-YFeiEye-Service-Signature");
        String tampered = signer.sign(
                HttpMethod.GET,
                URI.create("http://video.local/video/record/availability?camera_id=camera-01&time_range=3600"),
                "coverage", "camera-01", ""
        ).getFirst("X-YFeiEye-Service-Signature");

        assertNotEquals(first, tampered);
    }

    @Test
    void signsSeekableBrowserPlaybackUrlWithTheSamePythonVector() {
        VideoMediaServiceRequestSigner signer = new VideoMediaServiceRequestSigner(
                "secret-1", "iot-system", () -> "42", () -> "7",
                () -> 1_720_580_000L, () -> "nonce-1", (action, cameraId) -> true
        );

        String signed = signer.signPlaybackUrl(
                "/video/alert/record?path=%2Fdata%2Fclip.flv",
                "camera-01"
        );

        assertTrue(signed.startsWith(
                "/video/alert/record?path=%2Fdata%2Fclip.flv&playback_format=mp4&yf_ticket=v1"));
        assertTrue(signed.contains("yf_camera_id=camera-01"));
        assertTrue(signed.contains("yf_action=playback"));
        assertTrue(signed.contains("yf_timestamp=1720580000"));
        assertTrue(signed.contains("yf_nonce=nonce-1"));
        assertTrue(signed.contains(
                "yf_signature=sha256%3Dda4dd47bceb69b2ed9bbb8e5398a49dfc660bf497a5280ae02500f4c3be1e251"));
    }

    @Test
    void signsPublicProxyPlaybackUrlAgainstTheVideoRouteSeenAfterNginxRewrite() {
        VideoMediaServiceRequestSigner signer = new VideoMediaServiceRequestSigner(
                "secret-1", "iot-system", () -> "42", () -> "7",
                () -> 1_720_580_000L, () -> "nonce-1", (action, cameraId) -> true
        );

        String signed = signer.signPlaybackUrl(
                "https://eye.yfeiai.com/yfeieye/dev-api/video/alert/record?path=%2Fdata%2Fclip.flv",
                "camera-01"
        );

        assertTrue(signed.startsWith(
                "https://eye.yfeiai.com/yfeieye/dev-api/video/alert/record?path=%2Fdata%2Fclip.flv&playback_format=mp4&yf_ticket=v1"));
        assertTrue(signed.contains(
                "yf_signature=sha256%3Dda4dd47bceb69b2ed9bbb8e5398a49dfc660bf497a5280ae02500f4c3be1e251"));
    }

    @Test
    void refusesToLeakPlaybackTicketToNonVideoEndpoint() {
        VideoMediaServiceRequestSigner signer = new VideoMediaServiceRequestSigner(
                "secret-1", "iot-system", () -> "42", () -> "7",
                () -> 1L, () -> "nonce", (action, cameraId) -> true
        );

        assertThrows(IllegalArgumentException.class,
                () -> signer.signPlaybackUrl("https://untrusted.example/private.mp4", "camera-01"));
    }

    @Test
    void forcesMp4PlaybackAndRejectsUnsignedFragments() {
        VideoMediaServiceRequestSigner signer = new VideoMediaServiceRequestSigner(
                "secret-1", "iot-system", () -> "42", () -> "7",
                () -> 1_720_580_000L, () -> "nonce-1", (action, cameraId) -> true
        );

        String signed = signer.signPlaybackUrl(
                "/video/alert/record?path=%2Fdata%2Fclip.flv&playback_format=flv",
                "camera-01"
        );

        assertTrue(signed.contains("playback_format=mp4"));
        assertTrue(!signed.contains("playback_format=flv"));
        assertThrows(IllegalArgumentException.class, () -> signer.signPlaybackUrl(
                "/video/alert/record?path=%2Fdata%2Fclip.flv#unsigned-fragment",
                "camera-01"
        ));
    }

    @Test
    void missingSecretOrTenantFailsClosed() {
        VideoMediaServiceRequestSigner missingSecret = new VideoMediaServiceRequestSigner(
                "", "iot-system", () -> "42", () -> "7", () -> 1L, () -> "nonce",
                (action, cameraId) -> true
        );
        VideoMediaServiceRequestSigner missingTenant = new VideoMediaServiceRequestSigner(
                "secret", "iot-system", () -> "42", () -> null, () -> 1L, () -> "nonce",
                (action, cameraId) -> true
        );
        VideoMediaServiceRequestSigner deniedScope = new VideoMediaServiceRequestSigner(
                "secret", "iot-system", () -> "42", () -> "7", () -> 1L, () -> "nonce",
                (action, cameraId) -> false
        );

        assertThrows(IllegalStateException.class, () -> missingSecret.sign(
                HttpMethod.GET, URI.create("http://video.local/video/record/availability"),
                "coverage", "camera-01", ""));
        assertThrows(IllegalStateException.class, () -> missingTenant.sign(
                HttpMethod.GET, URI.create("http://video.local/video/record/availability"),
                "coverage", "camera-01", ""));
        assertThrows(SecurityException.class, () -> deniedScope.sign(
                HttpMethod.GET, URI.create("http://video.local/video/record/availability"),
                "coverage", "camera-01", ""));
    }
}
