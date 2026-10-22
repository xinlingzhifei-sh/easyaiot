package com.basiclab.iot.system.service.supervision.alertreview;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.*;

/**
 * 复核组件共用的静态工具：类型转换、集合/映射合并、哈希与常量。
 *
 * <p>由 SupervisionAlertReviewServiceImpl 按职责拆出，方法体保持原样；事务边界仍在门面类上。</p>
 */
final class AlertReviewValues {

    private AlertReviewValues() {
    }

    static final String MATERIAL_SNAPSHOT = "snapshot";
    static final String MATERIAL_RECORD = "record";
    static final String MATERIAL_RECORD_COVERAGE = "record_coverage";
    static final String MATERIAL_REVIEW_ACTION = "review_action";

    static List<String> normalizeCameraScope(List<String> cameraIds) {
        if (cameraIds == null) {
            return null;
        }
        return cameraIds.stream()
                .filter(AlertReviewValues::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    static boolean isEvidenceMedia(String materialType) {
        return MATERIAL_SNAPSHOT.equals(materialType) || MATERIAL_RECORD.equals(materialType);
    }

    static List<Long> reviewItemIdsFromTimeline(List<ReviewCaseTimelineItem> timeline) {
        Set<Long> ids = new LinkedHashSet<>();
        for (ReviewCaseTimelineItem item : timeline) {
            if (item.reviewItemId() != null) {
                ids.add(item.reviewItemId());
            }
        }
        return List.copyOf(ids);
    }

    static String sha256Token(Object... values) {
        StringBuilder payload = new StringBuilder();
        if (values != null) {
            for (Object value : values) {
                payload.append(value == null ? "<null>" : value).append('\u001f');
            }
        }
        return "sha256:" + sha256Hex(payload.toString());
    }

    static String sha256Hex(String payload) {
        return sha256Hex(payload.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    static boolean matchesText(String configured, String actual) {
        if (!hasText(configured)) {
            return true;
        }
        return Objects.equals(configured, actual);
    }

    static double roundRate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0D;
        }
        return Math.round((numerator * 100D) / denominator) / 100D;
    }

    static int boundedPositive(Integer value, int fallback, int max) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(value, max);
    }

    static Map<String, Object> toStringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return normalizeStringKeyMap(map);
    }

    static Map<String, Object> immutableNonNullMap(Map<String, Object> values) {
        values.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        return Map.copyOf(values);
    }

    static int materialOrder(String materialType) {
        if (MATERIAL_SNAPSHOT.equals(materialType)) {
            return 10;
        }
        if (MATERIAL_RECORD.equals(materialType)) {
            return 20;
        }
        if (MATERIAL_RECORD_COVERAGE.equals(materialType)) {
            return 30;
        }
        if (MATERIAL_REVIEW_ACTION.equals(materialType)) {
            return 40;
        }
        return 90;
    }

    static Set<String> objectIds(ReviewItemAggregate item) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, item.reviewData().get("objectIds"));
        return values;
    }

    static Set<String> zones(ReviewItemAggregate item) {
        Set<String> values = new LinkedHashSet<>();
        collectStringValues(values, item.zoneCode());
        collectStringValues(values, item.reviewData().get("zones"));
        return values;
    }

    static boolean intersects(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return !intersection.isEmpty();
    }

    static Set<String> intersection(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return Set.of();
        }
        Set<String> intersection = new LinkedHashSet<>(left);
        intersection.retainAll(right);
        return intersection;
    }

    @SafeVarargs
    static <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    static List<Map<String, Object>> toMapList(Object value) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof Map<?, ?> map) {
                    values.add(normalizeStringKeyMap(map));
                }
            }
            return List.copyOf(values);
        }
        if (value instanceof Map<?, ?> map) {
            values.add(normalizeStringKeyMap(map));
        }
        return List.copyOf(values);
    }

    static List<String> toStringList(Object value, String fallback) {
        List<String> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = toText(item);
                if (hasText(text)) {
                    values.add(text);
                }
            }
        } else {
            String text = toText(value);
            if (hasText(text)) {
                values.add(text);
            }
        }
        if (values.isEmpty() && hasText(fallback)) {
            values.add(fallback);
        }
        return List.copyOf(values);
    }

    static List<Double> toDoubleList(Object value) {
        List<Double> values = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Double number = toDouble(item);
                if (number != null) {
                    values.add(number);
                }
            }
        } else {
            Double number = toDouble(value);
            if (number != null) {
                values.add(number);
            }
        }
        return List.copyOf(values);
    }

    static Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Map<String, Object> normalizeStringKeyMap(Map<?, ?> value) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : value.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                normalized.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return Map.copyOf(normalized);
    }

    static void collectStringValues(Set<String> values, Object value) {
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && hasText(String.valueOf(item))) {
                    values.add(String.valueOf(item));
                }
            }
            return;
        }
        if (value != null && hasText(String.valueOf(value))) {
            values.add(String.valueOf(value));
        }
    }

    static String firstSourceAlertId(ReviewItemAggregate item) {
        if (item.sourceAlertIds() == null || item.sourceAlertIds().isEmpty()) {
            return null;
        }
        return item.sourceAlertIds().get(0);
    }

    static String toText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    static String firstText(Object value, String fallback) {
        String text = toText(value);
        return hasText(text) ? text : fallback;
    }

    static Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = toText(value);
        if (!hasText(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isBefore(second) ? first : second;
    }

    static LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }

    static void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }

    static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
