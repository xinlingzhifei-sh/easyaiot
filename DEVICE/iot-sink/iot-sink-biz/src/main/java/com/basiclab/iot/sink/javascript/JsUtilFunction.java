package com.basiclab.iot.sink.javascript;

import com.basiclab.iot.common.utils.json.JsonUtils;
import com.basiclab.iot.sink.mq.message.IotDeviceMessage;
import com.basiclab.iot.sink.util.IotDeviceMessageUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 注入到产品编解码脚本的工具函数（全局名 jsUtil）。
 * <p>
 * 约定：脚本最终须返回 {@code byte[]}（也可用 UTF-8 字符串，引擎会自动转换）。
 *
 * @author reese
 * @email reese
 */
public class JsUtilFunction {

    /**
     * 构造平台标准 JSON 消息字节（供 Codec 二次解码）。
     */
    public byte[] toStandardMessage(Long tenantId, String requestId, String method, Object params) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        body.put("requestId", requestId != null ? requestId : IotDeviceMessageUtils.generateMessageId());
        body.put("method", method);
        if (params != null) {
            body.put("params", params);
        }
        return JsonUtils.toJsonByte(body);
    }

    /**
     * 带响应字段的标准消息。
     */
    public byte[] toStandardMessage(Long tenantId, String requestId, String method,
                                    Object params, Object data, Integer code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (tenantId != null) {
            body.put("tenantId", tenantId);
        }
        body.put("requestId", requestId != null ? requestId : IotDeviceMessageUtils.generateMessageId());
        body.put("method", method);
        if (params != null) {
            body.put("params", params);
        }
        if (data != null) {
            body.put("data", data);
        }
        if (code != null) {
            body.put("code", code);
        }
        if (msg != null) {
            body.put("msg", msg);
        }
        return JsonUtils.toJsonByte(body);
    }

    /**
     * @deprecated 使用 {@link #toStandardMessage}，method 请传 thing.property.post
     */
    @Deprecated
    public byte[] toDeviceReq(Map<String, Object> data) {
        return toDeviceReq(data, null);
    }

    @Deprecated
    public byte[] toDeviceReq(Map<String, Object> data, Long timestamp) {
        IotDeviceMessage message = IotDeviceMessage.requestOf("thing.property.post", data);
        if (timestamp != null) {
            message.setReportTime(java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(timestamp),
                    java.time.ZoneId.systemDefault()));
        }
        return JsonUtils.toJsonByte(message);
    }

    public Map<String, Object> toJsonObject(byte[] data) {
        if (data == null || data.length == 0) {
            return new HashMap<>();
        }
        return JsonUtils.parseObject(new String(data, StandardCharsets.UTF_8), Map.class);
    }

    public Map<String, Object> toJsonObject(String jsonText) {
        if (jsonText == null || jsonText.isEmpty()) {
            return new HashMap<>();
        }
        return JsonUtils.parseObject(jsonText, Map.class);
    }

    /**
     * 将任意对象（含 Java Map）序列化为 JSON 字符串。
     * <p>
     * 注意：脚本里对 Java Map 使用 JSON.stringify 会得到 "{}"，请改用本方法。
     */
    public String toJsonString(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence) {
            return value.toString();
        }
        return JsonUtils.toJsonString(value);
    }

    public String bytesToUtf8(byte[] data) {
        if (data == null) {
            return "";
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    public byte[] utf8Bytes(String text) {
        if (text == null) {
            return new byte[0];
        }
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public String toHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    public byte[] fromHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        String cleaned = hex.replaceAll("\\s+", "").replace("0x", "").replace("0X", "");
        if ((cleaned.length() & 1) != 0) {
            throw new IllegalArgumentException("hex 长度必须为偶数");
        }
        byte[] out = new byte[cleaned.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(cleaned.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public ReadBuffer readBuffer(byte[] data) {
        return new ReadBuffer(data == null ? new byte[0] : data);
    }

    public WriteBuffer writeBuffer() {
        return new WriteBuffer();
    }

    public Map<String, Object> newMap() {
        return new LinkedHashMap<>();
    }

    public int toInt(Number number) {
        return toInt(number, 0);
    }

    public int toInt(Number number, int defaultValue) {
        return number == null ? defaultValue : number.intValue();
    }

    public long toLong(Number number) {
        return toLong(number, 0L);
    }

    public long toLong(Number number, long defaultValue) {
        return number == null ? defaultValue : number.longValue();
    }

    public String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
