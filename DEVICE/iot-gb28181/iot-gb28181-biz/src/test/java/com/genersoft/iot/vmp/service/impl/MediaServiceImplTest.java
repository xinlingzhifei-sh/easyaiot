package com.genersoft.iot.vmp.service.impl;

import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelService;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.bean.ResultForOnPublish;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.streamProxy.service.IStreamProxyService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaServiceImplTest {

    private static final String DEVICE_ID = "44010200493432381460";
    private static final String CHANNEL_ID = "34020000001320000001";
    private static final String STREAM = DEVICE_ID + "_" + CHANNEL_ID;

    @Test
    void enabledBroadcastConsumesOneTimeAuthorityAndAllowsAudio() {
        IRedisCatchStorage redisCatchStorage = mock(IRedisCatchStorage.class);
        IDeviceChannelService deviceChannelService = mock(IDeviceChannelService.class);
        IGbChannelService channelService = mock(IGbChannelService.class);
        DeviceChannel deviceChannel = mock(DeviceChannel.class);
        CommonGBChannel commonChannel = mock(CommonGBChannel.class);

        when(redisCatchStorage.consumeAudioBroadcastAuthority("broadcast", STREAM, "token")).thenReturn(true);
        when(deviceChannelService.getOne(DEVICE_ID, CHANNEL_ID)).thenReturn(deviceChannel);
        when(channelService.queryCommonChannelByDeviceChannel(deviceChannel)).thenReturn(commonChannel);
        when(commonChannel.getEnableBroadcast()).thenReturn(1);

        MediaServiceImpl service = createService(redisCatchStorage, deviceChannelService, channelService);
        ResultForOnPublish result = service.authenticatePublish(
                mock(MediaServer.class),
                "broadcast",
                STREAM,
                "app=broadcast&stream=" + STREAM + "&callId=token&type=push"
        );

        assertTrue(result.isEnable_audio());
        assertFalse(result.isEnable_mp4());
        verify(redisCatchStorage).consumeAudioBroadcastAuthority("broadcast", STREAM, "token");
    }

    @Test
    void invalidBroadcastAuthorityIsRejectedBeforeChannelLookup() {
        IRedisCatchStorage redisCatchStorage = mock(IRedisCatchStorage.class);
        IDeviceChannelService deviceChannelService = mock(IDeviceChannelService.class);
        IGbChannelService channelService = mock(IGbChannelService.class);

        MediaServiceImpl service = createService(redisCatchStorage, deviceChannelService, channelService);

        assertThrows(
                ControllerException.class,
                () -> service.authenticatePublish(
                        mock(MediaServer.class),
                        "broadcast",
                        STREAM,
                        "app=broadcast&stream=" + STREAM + "&callId=wrong&type=push"
                )
        );

        verify(deviceChannelService, never()).getOne(DEVICE_ID, CHANNEL_ID);
    }

    @Test
    void disabledBroadcastChannelIsRejectedAfterAuthorityValidation() {
        IRedisCatchStorage redisCatchStorage = mock(IRedisCatchStorage.class);
        IDeviceChannelService deviceChannelService = mock(IDeviceChannelService.class);
        IGbChannelService channelService = mock(IGbChannelService.class);
        DeviceChannel deviceChannel = mock(DeviceChannel.class);
        CommonGBChannel commonChannel = mock(CommonGBChannel.class);

        when(redisCatchStorage.consumeAudioBroadcastAuthority("broadcast", STREAM, "token")).thenReturn(true);
        when(deviceChannelService.getOne(DEVICE_ID, CHANNEL_ID)).thenReturn(deviceChannel);
        when(channelService.queryCommonChannelByDeviceChannel(deviceChannel)).thenReturn(commonChannel);
        when(commonChannel.getEnableBroadcast()).thenReturn(0);

        MediaServiceImpl service = createService(redisCatchStorage, deviceChannelService, channelService);

        assertThrows(
                ControllerException.class,
                () -> service.authenticatePublish(
                        mock(MediaServer.class),
                        "broadcast",
                        STREAM,
                        "app=broadcast&stream=" + STREAM + "&callId=token&type=push"
                )
        );
    }

    private MediaServiceImpl createService(
            IRedisCatchStorage redisCatchStorage,
            IDeviceChannelService deviceChannelService,
            IGbChannelService channelService
    ) {
        UserSetting userSetting = mock(UserSetting.class);
        IStreamProxyService streamProxyService = mock(IStreamProxyService.class);
        when(userSetting.getPushAuthority()).thenReturn(true);

        MediaServiceImpl service = new MediaServiceImpl();
        ReflectionTestUtils.setField(service, "redisCatchStorage", redisCatchStorage);
        ReflectionTestUtils.setField(service, "deviceChannelService", deviceChannelService);
        ReflectionTestUtils.setField(service, "channelService", channelService);
        ReflectionTestUtils.setField(service, "userSetting", userSetting);
        ReflectionTestUtils.setField(service, "streamProxyService", streamProxyService);
        return service;
    }
}
