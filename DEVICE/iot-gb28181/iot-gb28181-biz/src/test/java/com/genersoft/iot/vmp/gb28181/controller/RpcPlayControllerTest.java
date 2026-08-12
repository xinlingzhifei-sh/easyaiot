package com.genersoft.iot.vmp.gb28181.controller;

import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.DeferredResult;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RpcPlayControllerTest {

    @Test
    void delegatesToTheExistingPlayFlow() {
        PlayController playController = mock(PlayController.class);
        RpcPlayController controller = new RpcPlayController(playController);
        MockHttpServletRequest request = new MockHttpServletRequest();
        DeferredResult<WVPResult<StreamContent>> expected = new DeferredResult<>();
        String deviceId = "44010200493432381460";
        String channelId = "34020000001320000001";
        when(playController.play(request, deviceId, channelId)).thenReturn(expected);

        DeferredResult<WVPResult<StreamContent>> actual =
                controller.play(request, deviceId, channelId);

        assertSame(expected, actual);
        verify(playController).play(request, deviceId, channelId);
    }
}
