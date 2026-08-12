package com.basiclab.iot.device.controller.device;

import com.basiclab.iot.device.constant.DeviceStatusConstant;
import com.basiclab.iot.device.service.device.DeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeviceWebhookSecurityTest {

    private static final String TOKEN = "emqx-webhook-test-token-32-bytes!";

    private DeviceController controller;
    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        controller = new DeviceController();
        deviceService = mock(DeviceService.class);
        ReflectionTestUtils.setField(controller, "deviceService", deviceService);
    }

    @Test
    void refusesRequestsWhenServerTokenIsNotSafelyConfigured() {
        ReflectionTestUtils.setField(controller, "emqxWebhookToken", "short");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.webHook(TOKEN, Map.of()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void rejectsMissingOrInvalidToken() {
        ReflectionTestUtils.setField(controller, "emqxWebhookToken", TOKEN);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.webHook(null, Map.of()));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void acceptsConfiguredTokenAndDispatchesEvent() {
        ReflectionTestUtils.setField(controller, "emqxWebhookToken", TOKEN);
        Map<String, Object> payload = Map.of(
                "event",
                DeviceStatusConstant.CLIENT_CONNECTED);

        controller.webHook(TOKEN, payload);

        verify(deviceService).handleConnected(payload);
    }
}
