package com.basiclab.iot.sink.controller;

import com.basiclab.iot.sink.service.media.DvrUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MediaHookControllerSecurityTest {

    private static final String VALID_TOKEN = "media-hook-token-at-least-32-bytes";

    @Test
    void missingConfigurationFailsClosed() {
        DvrUploadService service = mock(DvrUploadService.class);
        MediaHookController controller = new MediaHookController(service, "");
        Map<String, Object> body = dvrBody();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.srsOnDvr(VALID_TOKEN, body));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(service, never()).processDvrEvent(body);
    }

    @Test
    void invalidTokenIsRejected() {
        DvrUploadService service = mock(DvrUploadService.class);
        MediaHookController controller = new MediaHookController(service, VALID_TOKEN);
        Map<String, Object> body = dvrBody();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.srsOnDvr("wrong-token", body));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(service, never()).processDvrEvent(body);
    }

    @Test
    void validTokenProcessesDvrEvent() {
        DvrUploadService service = mock(DvrUploadService.class);
        MediaHookController controller = new MediaHookController(service, VALID_TOKEN);
        Map<String, Object> body = dvrBody();

        Map<String, Object> response = controller.srsOnDvr(VALID_TOKEN, body);

        assertEquals(0, response.get("code"));
        verify(service).processDvrEvent(body);
    }

    private static Map<String, Object> dvrBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("stream", "camera-01");
        body.put("file_path", "/data/playbacks/camera-01/test.flv");
        return body;
    }
}
