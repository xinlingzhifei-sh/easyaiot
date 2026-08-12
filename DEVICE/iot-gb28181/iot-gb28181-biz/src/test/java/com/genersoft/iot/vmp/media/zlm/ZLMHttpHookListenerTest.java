package com.genersoft.iot.vmp.media.zlm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.bean.ResultForOnPublish;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.zlm.dto.ZLMResult;
import com.genersoft.iot.vmp.media.zlm.dto.hook.HookResultForOnPublish;
import com.genersoft.iot.vmp.media.zlm.dto.hook.OnPublishHookParam;
import com.genersoft.iot.vmp.media.zlm.dto.hook.OnStreamNoneReaderHookParam;
import com.genersoft.iot.vmp.service.IMediaService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ZLMHttpHookListenerTest {

    @Test
    void onPublishFallsBackToDefaultMediaServerWhenHookDoesNotSendMediaServerId() {
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        IMediaService mediaService = mock(IMediaService.class);
        MediaServer defaultMediaServer = new MediaServer();
        defaultMediaServer.setId("zlmediakit-local");
        ResultForOnPublish publishResult = new ResultForOnPublish();
        publishResult.setEnable_audio(true);

        when(mediaServerService.getDefaultMediaServer()).thenReturn(defaultMediaServer);
        when(mediaService.authenticatePublish(defaultMediaServer, "rtp", "device_channel", ""))
                .thenReturn(publishResult);

        ZLMHttpHookListener listener = new ZLMHttpHookListener();
        ReflectionTestUtils.setField(listener, "mediaServerService", mediaServerService);
        ReflectionTestUtils.setField(listener, "mediaService", mediaService);

        OnPublishHookParam param = new OnPublishHookParam();
        param.setApp("rtp");
        param.setStream("device_channel");
        param.setParams("");

        HookResultForOnPublish result = listener.onPublish(param);

        assertEquals(0, result.getCode());
        assertEquals("zlmediakit-local", param.getMediaServerId());
        verify(mediaServerService).getDefaultMediaServer();
        verify(mediaService).authenticatePublish(defaultMediaServer, "rtp", "device_channel", "");
    }

    @Test
    void onPublishFallsBackToDefaultMediaServerWhenHookSendsUnknownMediaServerId() {
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        IMediaService mediaService = mock(IMediaService.class);
        MediaServer defaultMediaServer = new MediaServer();
        defaultMediaServer.setId("zlmediakit-local");
        ResultForOnPublish publishResult = new ResultForOnPublish();
        publishResult.setEnable_audio(true);

        when(mediaServerService.getOne("zlm-random-runtime-id")).thenReturn(null);
        when(mediaServerService.getDefaultMediaServer()).thenReturn(defaultMediaServer);
        when(mediaService.authenticatePublish(defaultMediaServer, "rtp", "device_channel", ""))
                .thenReturn(publishResult);

        ZLMHttpHookListener listener = new ZLMHttpHookListener();
        ReflectionTestUtils.setField(listener, "mediaServerService", mediaServerService);
        ReflectionTestUtils.setField(listener, "mediaService", mediaService);

        OnPublishHookParam param = new OnPublishHookParam();
        param.setMediaServerId("zlm-random-runtime-id");
        param.setApp("rtp");
        param.setStream("device_channel");
        param.setParams("");

        HookResultForOnPublish result = listener.onPublish(param);

        assertEquals(0, result.getCode());
        assertEquals("zlmediakit-local", param.getMediaServerId());
        verify(mediaServerService).getOne("zlm-random-runtime-id");
        verify(mediaServerService).getDefaultMediaServer();
        verify(mediaService).authenticatePublish(defaultMediaServer, "rtp", "device_channel", "");
    }

    @Test
    void onStreamNoneReaderKeepsSourceWhenAnotherProtocolStillHasReaders() {
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        IMediaService mediaService = mock(IMediaService.class);
        ZLMRESTfulUtils zlmRestfulUtils = mock(ZLMRESTfulUtils.class);
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlmediakit-local");

        JSONArray mediaList = new JSONArray();
        mediaList.add(mediaEntry("hls", 0));
        mediaList.add(mediaEntry("rtmp", 1));
        when(mediaServerService.getOne("zlmediakit-local")).thenReturn(mediaServer);
        when(zlmRestfulUtils.getMediaList(mediaServer, "rtp", "device_channel"))
                .thenReturn(ZLMResult.getMediaServer(0, "success", mediaList));

        ZLMHttpHookListener listener = listener(mediaServerService, mediaService, zlmRestfulUtils);
        OnStreamNoneReaderHookParam param = noneReaderParam();

        JSONObject result = listener.onStreamNoneReader(param);

        assertFalse(result.getBooleanValue("close"));
        verify(mediaService, never()).closeStreamOnNoneReader(
                "zlmediakit-local", "rtp", "device_channel", "hls");
    }

    @Test
    void onStreamNoneReaderDelegatesCloseWhenAllProtocolsHaveNoReaders() {
        IMediaServerService mediaServerService = mock(IMediaServerService.class);
        IMediaService mediaService = mock(IMediaService.class);
        ZLMRESTfulUtils zlmRestfulUtils = mock(ZLMRESTfulUtils.class);
        MediaServer mediaServer = new MediaServer();
        mediaServer.setId("zlmediakit-local");

        JSONArray mediaList = new JSONArray();
        mediaList.add(mediaEntry("hls", 0));
        mediaList.add(mediaEntry("rtmp", 0));
        when(mediaServerService.getOne("zlmediakit-local")).thenReturn(mediaServer);
        when(zlmRestfulUtils.getMediaList(mediaServer, "rtp", "device_channel"))
                .thenReturn(ZLMResult.getMediaServer(0, "success", mediaList));
        when(mediaService.closeStreamOnNoneReader(
                "zlmediakit-local", "rtp", "device_channel", "hls")).thenReturn(true);

        ZLMHttpHookListener listener = listener(mediaServerService, mediaService, zlmRestfulUtils);

        JSONObject result = listener.onStreamNoneReader(noneReaderParam());

        assertTrue(result.getBooleanValue("close"));
        verify(mediaService).closeStreamOnNoneReader(
                "zlmediakit-local", "rtp", "device_channel", "hls");
    }

    private static ZLMHttpHookListener listener(
            IMediaServerService mediaServerService,
            IMediaService mediaService,
            ZLMRESTfulUtils zlmRestfulUtils) {
        ZLMHttpHookListener listener = new ZLMHttpHookListener();
        ReflectionTestUtils.setField(listener, "mediaServerService", mediaServerService);
        ReflectionTestUtils.setField(listener, "mediaService", mediaService);
        ReflectionTestUtils.setField(listener, "zlmRestfulUtils", zlmRestfulUtils);
        return listener;
    }

    private static OnStreamNoneReaderHookParam noneReaderParam() {
        OnStreamNoneReaderHookParam param = new OnStreamNoneReaderHookParam();
        param.setMediaServerId("zlmediakit-local");
        param.setSchema("hls");
        param.setApp("rtp");
        param.setStream("device_channel");
        return param;
    }

    private static JSONObject mediaEntry(String schema, int readerCount) {
        JSONObject media = new JSONObject();
        media.put("schema", schema);
        media.put("readerCount", readerCount);
        return media;
    }
}
