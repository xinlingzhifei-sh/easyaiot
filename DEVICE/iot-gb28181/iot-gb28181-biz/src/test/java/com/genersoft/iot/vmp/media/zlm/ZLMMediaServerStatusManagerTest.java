package com.genersoft.iot.vmp.media.zlm;

import com.genersoft.iot.vmp.conf.MediaConfig;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.zlm.dto.ZLMResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZLMMediaServerStatusManagerTest {

    @Test
    void setZLMConfigPinsZlmRuntimeIdToConfiguredMediaServerId() {
        ZLMRESTfulUtils zlmRestfulUtils = mock(ZLMRESTfulUtils.class);
        MediaConfig mediaConfig = mock(MediaConfig.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlmediakit-local");
        mediaServer.setIp("127.0.0.1");
        mediaServer.setHookIp("192.168.0.88");
        mediaServer.setHttpPort(6080);

        when(mediaConfig.getId()).thenReturn("another-media-server");
        when(zlmRestfulUtils.setServerConfig(eq(mediaServer), paramsCaptor.capture()))
                .thenReturn(ZLMResult.getMediaServer(0, "ok"));

        ZLMMediaServerStatusManager manager = new ZLMMediaServerStatusManager();
        ReflectionTestUtils.setField(manager, "zlmresTfulUtils", zlmRestfulUtils);
        ReflectionTestUtils.setField(manager, "mediaConfig", mediaConfig);
        ReflectionTestUtils.setField(manager, "sslEnabled", false);
        ReflectionTestUtils.setField(manager, "serverPort", 48088);
        ReflectionTestUtils.setField(manager, "serverServletContextPath", "");
        ReflectionTestUtils.setField(
                manager,
                "mediaHookToken",
                "gb28181-media-hook-test-token-32-bytes");

        manager.setZLMConfig(mediaServer, false);

        assertEquals("zlmediakit-local", paramsCaptor.getValue().get("general.mediaServerId"));
    }
}
