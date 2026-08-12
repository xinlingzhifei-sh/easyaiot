package com.basiclab.iot.sink.controller;

import com.basiclab.iot.sink.domain.model.PostProcessRequestMessage;
import com.basiclab.iot.sink.service.PostProcessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PostProcessControllerSecurityTest {

    private static final String VALID_TOKEN = "post-process-token-at-least-32-bytes";

    @Test
    void missingConfigurationFailsClosed() {
        PostProcessService service = mock(PostProcessService.class);
        PostProcessController controller = new PostProcessController(service, "");
        PostProcessRequestMessage message = new PostProcessRequestMessage();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.enqueue(VALID_TOKEN, message));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
        verify(service, never()).enqueue(message);
    }

    @Test
    void invalidTokenIsRejected() {
        PostProcessService service = mock(PostProcessService.class);
        PostProcessController controller = new PostProcessController(service, VALID_TOKEN);
        PostProcessRequestMessage message = new PostProcessRequestMessage();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.enqueue("wrong-token", message));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        verify(service, never()).enqueue(message);
    }

    @Test
    void validTokenEnqueuesMessage() {
        PostProcessService service = mock(PostProcessService.class);
        PostProcessController controller = new PostProcessController(service, VALID_TOKEN);
        PostProcessRequestMessage message = new PostProcessRequestMessage();

        controller.enqueue(VALID_TOKEN, message);

        verify(service).enqueue(message);
    }
}
