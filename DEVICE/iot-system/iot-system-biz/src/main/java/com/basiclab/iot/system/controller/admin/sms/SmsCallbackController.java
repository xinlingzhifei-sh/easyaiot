package com.basiclab.iot.system.controller.admin.sms;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.utils.servlet.ServletUtils;
import com.basiclab.iot.system.framework.sms.core.enums.SmsChannelEnum;
import com.basiclab.iot.system.framework.sms.core.security.SmsCallbackAccessVerifier;
import com.basiclab.iot.system.service.sms.SmsSendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;

import static com.basiclab.iot.common.domain.CommonResult.success;

/**
 * SmsCallbackController
 *
 * @author reese
 * @email reese
 */
@Tag(name = "管理后台 - 短信回调")
@RestController
@RequestMapping("/system/sms/callback")
public class SmsCallbackController {

    @Resource
    private SmsSendService smsSendService;

    @Resource
    private SmsCallbackAccessVerifier smsCallbackAccessVerifier;

    @PostMapping("/aliyun")
    @PermitAll
    @Operation(summary = "阿里云短信的回调", description = "参见 https://help.aliyun.com/zh/sms/developer-reference/configure-delivery-receipts-1 文档")
    public CommonResult<Boolean> receiveAliyunSmsStatus(
            HttpServletRequest request,
            @RequestHeader(value = SmsCallbackAccessVerifier.TOKEN_HEADER, required = false)
            String headerToken,
            @RequestParam(value = SmsCallbackAccessVerifier.TOKEN_PARAMETER, required = false)
            String queryToken) throws Throwable {
        smsCallbackAccessVerifier.verify(headerToken, queryToken);
        String text = ServletUtils.getBody(request);
        smsSendService.receiveSmsStatus(SmsChannelEnum.ALIYUN.getCode(), text);
        return success(true);
    }

    @PostMapping("/tencent")
    @PermitAll
    @Operation(summary = "腾讯云短信的回调", description = "参见 https://cloud.tencent.com/document/product/382/59178 文档")
    public CommonResult<Boolean> receiveTencentSmsStatus(
            HttpServletRequest request,
            @RequestHeader(value = SmsCallbackAccessVerifier.TOKEN_HEADER, required = false)
            String headerToken,
            @RequestParam(value = SmsCallbackAccessVerifier.TOKEN_PARAMETER, required = false)
            String queryToken) throws Throwable {
        smsCallbackAccessVerifier.verify(headerToken, queryToken);
        String text = ServletUtils.getBody(request);
        smsSendService.receiveSmsStatus(SmsChannelEnum.TENCENT.getCode(), text);
        return success(true);
    }

}
