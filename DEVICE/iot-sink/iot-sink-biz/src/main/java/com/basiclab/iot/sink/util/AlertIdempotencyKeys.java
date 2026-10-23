package com.basiclab.iot.sink.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 告警入站契约 v1 的幂等键规则，与 VIDEO app/utils/alert_ingress_contract.py 一致。
 * <p>
 * 按 correlation_id → correlationId → event_id → eventId 取第一个非空值（去首尾空白）；
 * alert 表以 uq_alert_correlation_id 唯一约束去重。共享样例见 testdata/contracts/alert_*.json。
 */
public final class AlertIdempotencyKeys {

    private static final String[] KEY_FIELDS = {"correlation_id", "correlationId", "event_id", "eventId"};

    private AlertIdempotencyKeys() {
    }

    /**
     * 解析统一幂等键；全部缺失时返回 null。
     */
    public static String resolve(JsonNode payload) {
        return firstText(payload, KEY_FIELDS);
    }

    /**
     * 扁平告警归一时写入 correlation_id（统一幂等键）与 event_id（生产者原值）。
     */
    public static void copyInto(JsonNode src, ObjectNode dst) {
        String key = resolve(src);
        if (key != null) {
            dst.put("correlation_id", key);
        }
        String eventId = firstText(src, "event_id", "eventId");
        if (eventId != null) {
            dst.put("event_id", eventId);
        }
    }

    private static String firstText(JsonNode payload, String... fields) {
        if (payload == null || !payload.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = payload.get(field);
            if (value == null || value.isNull() || value.isContainerNode()) {
                continue;
            }
            String text = value.asText().trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }
}
