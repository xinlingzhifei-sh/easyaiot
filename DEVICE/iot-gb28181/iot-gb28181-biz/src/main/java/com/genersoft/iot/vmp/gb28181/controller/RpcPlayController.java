package com.genersoft.iot.vmp.gb28181.controller;

import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import javax.servlet.http.HttpServletRequest;

/**
 * 供平台内部服务使用的国标点播入口。
 */
@RestController
@RequestMapping("/rpc-api/gb28181/play")
public class RpcPlayController {

    private final PlayController playController;

    public RpcPlayController(PlayController playController) {
        this.playController = playController;
    }

    @GetMapping("/start/{deviceId}/{channelId}")
    public DeferredResult<WVPResult<StreamContent>> play(
            HttpServletRequest request,
            @PathVariable String deviceId,
            @PathVariable String channelId) {
        return playController.play(request, deviceId, channelId);
    }
}
