package com.basiclab.iot.device.messagebus;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.basiclab.iot.device.domain.device.vo.DeviceServiceInvokeResponse;
import com.basiclab.iot.device.service.device.DeviceService;
import com.basiclab.iot.device.service.device.DeviceServiceInvokeResponseService;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ServiceInvokeResponseHandler
 *
 * @author reese
 * @email reese
 */
@Slf4j
@Component
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
public class ServiceInvokeResponseHandler {

    /** 下发时写入的待响应状态码 */
    public static final int PENDING_CODE = -1;

    private final DeviceServiceInvokeResponseService responseService;
    private final DeviceService deviceService;

    /**
     * 处理服务调用响应消息
     *
     * @param message 设备消息
     */
    public void handle(IotDeviceMessage message) {
        try {
            if (message == null || message.getDeviceId() == null) {
                log.warn("[handle][消息或设备ID为空，跳过处理]");
                return;
            }

            String topic = message.getTopic();
            String[] topicParts = topic != null ? topic.split("/") : new String[0];
            if (topicParts.length < 6) {
                log.warn("[handle][Topic格式不正确，无法提取服务标识，topic: {}]", topic);
                return;
            }

            // Topic格式：/iot/{productIdentification}/{deviceIdentification}/service/upstream/invoke/{identifier}/response
            String productIdentification = topicParts.length >= 3 ? topicParts[2] : null;
            String deviceIdentification = topicParts.length >= 4 ? topicParts[3] : null;
            String serviceIdentifier = topicParts.length >= 7 ? topicParts[6] : null;

            if (StrUtil.isBlank(deviceIdentification) && message.getDeviceId() != null) {
                Long numericId = IotDeviceMessageUtils.parseLongDeviceIdOrNull(message.getDeviceId());
                com.basiclab.iot.device.domain.device.vo.Device device =
                        numericId != null ? deviceService.findOneById(numericId) : null;
                if (device != null) {
                    deviceIdentification = device.getDeviceIdentification();
                    if (StrUtil.isBlank(productIdentification)) {
                        productIdentification = device.getProductIdentification();
                    }
                }
            }

            String outputJson = message.getData() != null ? JSONUtil.toJsonStr(message.getData())
                    : (message.getParams() != null ? JSONUtil.toJsonStr(message.getParams()) : null);
            Integer responseCode = message.getCode() != null ? message.getCode() : 0;
            String responseMsg = StrUtil.blankToDefault(message.getMsg(), "ok");
            LocalDateTime reportTime = message.getReportTime() != null
                    ? message.getReportTime() : LocalDateTime.now();

            // 优先按 requestId 更新下发时写入的 PENDING 记录
            DeviceServiceInvokeResponse pending = null;
            if (StrUtil.isNotBlank(message.getRequestId())) {
                pending = responseService.getByRequestId(message.getRequestId());
            }
            if (pending != null && pending.getId() != null
                    && (pending.getResponseCode() == null || pending.getResponseCode() == PENDING_CODE)) {
                String wrapped = wrapInputOutput(pending.getResponseData(), outputJson);
                pending.setMessageId(message.getId());
                pending.setResponseData(wrapped);
                pending.setResponseCode(responseCode);
                pending.setResponseMsg(responseMsg);
                pending.setTopic(topic);
                pending.setReportTime(reportTime);
                if (StrUtil.isNotBlank(serviceIdentifier)) {
                    pending.setServiceIdentifier(serviceIdentifier);
                }
                responseService.updateResponse(pending);
                log.info("[handle][更新 PENDING 服务调用响应成功，requestId: {}, deviceId: {}, serviceIdentifier: {}]",
                        message.getRequestId(), message.getDeviceId(), serviceIdentifier);
                return;
            }

            DeviceServiceInvokeResponse response = DeviceServiceInvokeResponse.builder()
                    .messageId(message.getId())
                    .deviceId(IotDeviceMessageUtils.parseLongDeviceIdOrNull(message.getDeviceId()))
                    .deviceIdentification(deviceIdentification)
                    .productIdentification(productIdentification)
                    .serviceIdentifier(serviceIdentifier)
                    .requestId(message.getRequestId())
                    .method(message.getMethod())
                    .responseData(outputJson)
                    .responseCode(responseCode)
                    .responseMsg(responseMsg)
                    .topic(topic)
                    .reportTime(reportTime)
                    .tenantId(message.getTenantId() != null ? message.getTenantId() : 0L)
                    .createTime(LocalDateTime.now())
                    .build();
            responseService.save(response);

            log.info("[handle][处理服务调用响应消息成功，messageId: {}, deviceId: {}, serviceIdentifier: {}]",
                    message.getId(), message.getDeviceId(), serviceIdentifier);

        } catch (Exception e) {
            log.error("[handle][处理服务调用响应消息失败，messageId: {}, topic: {}]",
                    message != null ? message.getId() : "unknown",
                    message != null ? message.getTopic() : "unknown", e);
        }
    }

    /**
     * 处理属性期望设置 ACK：按 requestId 更新 PENDING（serviceIdentifier=$property.set）
     */
    public void handlePropertySetAck(IotDeviceMessage message) {
        try {
            if (message == null) {
                return;
            }
            String topic = message.getTopic();
            String outputJson = message.getData() != null ? JSONUtil.toJsonStr(message.getData())
                    : (message.getParams() != null ? JSONUtil.toJsonStr(message.getParams()) : null);
            Integer responseCode = message.getCode() != null ? message.getCode() : 0;
            String responseMsg = StrUtil.blankToDefault(message.getMsg(), "ok");
            LocalDateTime reportTime = message.getReportTime() != null
                    ? message.getReportTime() : LocalDateTime.now();

            DeviceServiceInvokeResponse pending = null;
            if (StrUtil.isNotBlank(message.getRequestId())) {
                pending = responseService.getByRequestId(message.getRequestId());
            }
            if (pending != null && pending.getId() != null
                    && (pending.getResponseCode() == null || pending.getResponseCode() == PENDING_CODE)) {
                String wrapped = wrapInputOutput(pending.getResponseData(), outputJson);
                pending.setMessageId(message.getId());
                pending.setResponseData(wrapped);
                pending.setResponseCode(responseCode);
                pending.setResponseMsg(responseMsg);
                pending.setTopic(topic);
                pending.setReportTime(reportTime);
                if (StrUtil.isBlank(pending.getServiceIdentifier())) {
                    pending.setServiceIdentifier("$property.set");
                }
                responseService.updateResponse(pending);
                log.info("[handlePropertySetAck][更新属性设置 PENDING 成功，requestId: {}, deviceId: {}]",
                        message.getRequestId(), message.getDeviceId());
                return;
            }

            // 无 PENDING 时补记一条 ACK，避免指令日志空白
            String[] topicParts = topic != null ? topic.split("/") : new String[0];
            String productIdentification = topicParts.length >= 3 ? topicParts[2] : null;
            String deviceIdentification = topicParts.length >= 4 ? topicParts[3] : null;
            DeviceServiceInvokeResponse response = DeviceServiceInvokeResponse.builder()
                    .messageId(message.getId())
                    .deviceId(IotDeviceMessageUtils.parseLongDeviceIdOrNull(message.getDeviceId()))
                    .deviceIdentification(deviceIdentification)
                    .productIdentification(productIdentification)
                    .serviceIdentifier("$property.set")
                    .requestId(message.getRequestId())
                    .method(StrUtil.blankToDefault(message.getMethod(), "thing.property.set"))
                    .responseData(outputJson)
                    .responseCode(responseCode)
                    .responseMsg(responseMsg)
                    .topic(topic)
                    .reportTime(reportTime)
                    .tenantId(message.getTenantId() != null ? message.getTenantId() : 0L)
                    .createTime(LocalDateTime.now())
                    .build();
            responseService.save(response);
            log.info("[handlePropertySetAck][补记属性设置 ACK，requestId: {}, deviceId: {}]",
                    message.getRequestId(), message.getDeviceId());
        } catch (Exception e) {
            log.error("[handlePropertySetAck][处理属性设置 ACK 失败，messageId: {}]",
                    message != null ? message.getId() : "unknown", e);
        }
    }

    private static String wrapInputOutput(String inputJson, String outputJson) {
        JSONObject wrapped = new JSONObject();
        if (StrUtil.isNotBlank(inputJson)) {
            try {
                wrapped.set("input", JSONUtil.parse(inputJson));
            } catch (Exception ignored) {
                wrapped.set("input", inputJson);
            }
        }
        if (StrUtil.isNotBlank(outputJson)) {
            try {
                wrapped.set("output", JSONUtil.parse(outputJson));
            } catch (Exception ignored) {
                wrapped.set("output", outputJson);
            }
        }
        return wrapped.toString();
    }
}
