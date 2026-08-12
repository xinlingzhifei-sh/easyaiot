package com.basiclab.iot.message;

import com.basiclab.iot.common.constant.ServiceNameConstants;
import com.basiclab.iot.common.domain.AjaxResult;
import com.basiclab.iot.common.domain.TableDataInfo;
import com.basiclab.iot.message.factory.RemoteMessageNotifyQueryFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 消息中心：模板 / 用户分组查询（告警通知链路内网调用）
 */
@FeignClient(
        contextId = "RemoteMessageNotifyQueryService",
        value = ServiceNameConstants.IOT_MESSAGE,
        fallbackFactory = RemoteMessageNotifyQueryFactory.class)
public interface RemoteMessageNotifyQueryService {

    String INTERNAL_TOKEN_HEADER = "X-Iot-Message-Token";

    @GetMapping("/message/template/get")
    AjaxResult getTemplate(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @RequestParam("id") String id,
            @RequestParam("msgType") Integer msgType);

    @GetMapping("/message/preview/user/group/query")
    TableDataInfo queryUserGroup(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @RequestParam("id") String id);

    @GetMapping("/message/preview/user/query")
    TableDataInfo queryPreviewUser(
            @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
            @RequestParam("id") String id,
            @RequestParam(value = "msgType", required = false) Integer msgType);
}
