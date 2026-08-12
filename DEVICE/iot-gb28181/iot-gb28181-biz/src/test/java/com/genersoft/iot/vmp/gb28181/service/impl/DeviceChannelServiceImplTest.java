package com.genersoft.iot.vmp.gb28181.service.impl;

import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.dao.DeviceChannelMapper;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceChannelServiceImplTest {

    @Test
    void updateChannelsPreservesConfiguredStreamIdentification() {
        DeviceChannelMapper mapper = mock(DeviceChannelMapper.class);
        IInviteStreamService inviteStreamService = mock(IInviteStreamService.class);
        DeviceChannelServiceImpl service = service(mapper, inviteStreamService);
        DeviceChannel stored = channel(1, "streamprofile:1");
        DeviceChannel refreshed = channel(0, null);
        Device device = new Device();
        device.setId(7);

        when(mapper.queryChannelsByDeviceDbId(7)).thenReturn(List.of(stored));
        when(mapper.batchUpdate(anyList())).thenReturn(1);

        service.updateChannels(device, List.of(refreshed));

        ArgumentCaptor<List<DeviceChannel>> captor = channelListCaptor();
        verify(mapper).batchUpdate(captor.capture());
        assertEquals("streamprofile:1", captor.getValue().get(0).getStreamIdentification());
    }

    @Test
    void resetChannelsPreservesConfiguredStreamIdentification() {
        DeviceChannelMapper mapper = mock(DeviceChannelMapper.class);
        DeviceChannelServiceImpl service = service(mapper, mock(IInviteStreamService.class));
        DeviceChannel stored = channel(1, "streamprofile:1");
        DeviceChannel refreshed = channel(0, null);

        when(mapper.queryAllChannelsForRefresh(7)).thenReturn(List.of(stored));

        service.resetChannels(7, List.of(refreshed));

        ArgumentCaptor<List<DeviceChannel>> captor = channelListCaptor();
        verify(mapper).batchUpdate(captor.capture());
        assertEquals("streamprofile:1", captor.getValue().get(0).getStreamIdentification());
    }

    private static DeviceChannelServiceImpl service(
            DeviceChannelMapper mapper,
            IInviteStreamService inviteStreamService) {
        DeviceChannelServiceImpl service = new DeviceChannelServiceImpl();
        ReflectionTestUtils.setField(service, "channelMapper", mapper);
        ReflectionTestUtils.setField(service, "inviteStreamService", inviteStreamService);
        return service;
    }

    private static DeviceChannel channel(int id, String streamIdentification) {
        DeviceChannel channel = new DeviceChannel();
        channel.setId(id);
        channel.setDataDeviceId(7);
        channel.setDeviceId("34020000001320000001");
        channel.setStatus("ON");
        channel.setStreamIdentification(streamIdentification);
        return channel;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<List<DeviceChannel>> channelListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }
}
