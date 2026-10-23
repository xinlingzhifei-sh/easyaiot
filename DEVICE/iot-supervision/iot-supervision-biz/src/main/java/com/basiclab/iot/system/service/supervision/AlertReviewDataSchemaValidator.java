package com.basiclab.iot.system.service.supervision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AlertReviewDataSchemaValidator {

    private static final String SCHEMA_RESOURCE = "schemas/alert-review-review-data-v1.schema.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final int version;
    private final Set<String> requiredProperties;
    private final double confidenceMinimum;
    private final double confidenceMaximum;
    private final int bboxMinItems;
    private final int bboxMaxItems;
    private final Set<String> segmentStatuses;
    private final Set<String> segmentSeverities;

    private AlertReviewDataSchemaValidator(JsonNode schema) {
        JsonNode properties = schema.path("properties");
        this.version = properties.path("reviewDataVersion").path("const").asInt();
        this.requiredProperties = stringSet(schema.path("required"));
        this.confidenceMinimum = properties.path("confidence").path("minimum").asDouble();
        this.confidenceMaximum = properties.path("confidence").path("maximum").asDouble();
        this.bboxMinItems = properties.path("bbox").path("minItems").asInt();
        this.bboxMaxItems = properties.path("bbox").path("maxItems").asInt();
        JsonNode segmentProperties = properties.path("reviewSegment").path("properties");
        this.segmentStatuses = stringSet(segmentProperties.path("status").path("enum"));
        this.segmentSeverities = stringSet(segmentProperties.path("severity").path("enum"));
    }

    public static AlertReviewDataSchemaValidator loadV1() {
        ClassLoader loader = AlertReviewDataSchemaValidator.class.getClassLoader();
        try (InputStream input = loader.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing reviewData schema resource: " + SCHEMA_RESOURCE);
            }
            return new AlertReviewDataSchemaValidator(OBJECT_MAPPER.readTree(input));
        } catch (IOException ex) {
            throw new IllegalStateException("invalid reviewData schema resource: " + SCHEMA_RESOURCE, ex);
        }
    }

    public ValidationResult validate(Map<String, Object> reviewData) {
        List<String> violations = new ArrayList<>();
        if (reviewData == null) {
            return new ValidationResult(false, List.of("reviewData:required"));
        }
        for (String required : requiredProperties) {
            if (!reviewData.containsKey(required) || reviewData.get(required) == null) {
                violations.add(required + ":required");
            }
        }
        if (!(reviewData.get("reviewDataVersion") instanceof Number number)
                || number.intValue() != version) {
            violations.add("reviewDataVersion:const=" + version);
        }
        validateStringList(reviewData.get("labels"), "labels", violations);
        validateStringList(reviewData.get("zones"), "zones", violations);
        validateStringList(reviewData.get("objectIds"), "objectIds", violations);
        validateConfidence(reviewData.get("confidence"), "confidence", violations);
        validateBbox(reviewData.get("bbox"), "bbox", violations);
        validateOptionalString(reviewData.get("correlationId"), "correlationId", violations);
        validateObjects(reviewData.get("objects"), violations);
        validateDetections(reviewData.get("detections"), violations);
        validateSegment(reviewData.get("reviewSegment"), violations);
        return new ValidationResult(violations.isEmpty(), List.copyOf(violations));
    }

    private void validateObjects(Object value, List<String> violations) {
        if (!(value instanceof List<?> objects)) {
            violations.add("objects:type=array");
            return;
        }
        for (int index = 0; index < objects.size(); index++) {
            if (!(objects.get(index) instanceof Map<?, ?> object)) {
                violations.add("objects[" + index + "]:type=object");
                continue;
            }
            String path = "objects[" + index + "]";
            validateOptionalString(object.get("id"), path + ".id", violations);
            validateOptionalString(object.get("label"), path + ".label", violations);
            validateConfidence(object.get("confidence"), path + ".confidence", violations);
            validateBbox(object.get("bbox"), path + ".bbox", violations);
        }
    }

    private void validateDetections(Object value, List<String> violations) {
        if (!(value instanceof List<?> detections)) {
            violations.add("detections:type=array");
            return;
        }
        for (int index = 0; index < detections.size(); index++) {
            if (!(detections.get(index) instanceof Map<?, ?> detection)) {
                violations.add("detections[" + index + "]:type=object");
                continue;
            }
            String path = "detections[" + index + "]";
            validateRequiredString(detection.get("sourceAlertId"), path + ".sourceAlertId", violations);
            validateRequiredString(detection.get("alertTime"), path + ".alertTime", violations);
            validateRequiredString(detection.get("cameraId"), path + ".cameraId", violations);
            validateStringList(detection.get("labels"), path + ".labels", violations);
            validateStringList(detection.get("zones"), path + ".zones", violations);
            validateStringList(detection.get("objectIds"), path + ".objectIds", violations);
            validateConfidence(detection.get("confidence"), path + ".confidence", violations);
            validateBbox(detection.get("bbox"), path + ".bbox", violations);
            validateOptionalString(detection.get("correlationId"), path + ".correlationId", violations);
        }
    }

    private void validateSegment(Object value, List<String> violations) {
        if (!(value instanceof Map<?, ?> segment)) {
            violations.add("reviewSegment:type=object");
            return;
        }
        validateRequiredString(segment.get("segmentId"), "reviewSegment.segmentId", violations);
        validateRequiredString(segment.get("cameraId"), "reviewSegment.cameraId", violations);
        validateRequiredString(segment.get("startTime"), "reviewSegment.startTime", violations);
        validateRequiredString(segment.get("endTime"), "reviewSegment.endTime", violations);
        Object status = segment.get("status");
        if (!(status instanceof String text) || !segmentStatuses.contains(text)) {
            violations.add("reviewSegment.status:enum");
        }
        Object severity = segment.get("severity");
        if (severity != null && (!(severity instanceof String text) || !segmentSeverities.contains(text))) {
            violations.add("reviewSegment.severity:enum");
        }
        validateStringList(segment.get("sourceAlertIds"), "reviewSegment.sourceAlertIds", violations);
        validateStringList(segment.get("objectIds"), "reviewSegment.objectIds", violations);
        validateStringList(segment.get("zones"), "reviewSegment.zones", violations);
    }

    private void validateConfidence(Object value, String path, List<String> violations) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number)
                || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < confidenceMinimum
                || number.doubleValue() > confidenceMaximum) {
            violations.add(path + ":range=" + confidenceMinimum + ".." + confidenceMaximum);
        }
    }

    private void validateBbox(Object value, String path, List<String> violations) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> bbox)
                || bbox.size() < bboxMinItems
                || bbox.size() > bboxMaxItems
                || bbox.stream().anyMatch(item -> !(item instanceof Number number)
                || !Double.isFinite(number.doubleValue()))) {
            violations.add(path + ":items=" + bboxMinItems + ".." + bboxMaxItems);
        }
    }

    private static void validateStringList(Object value, String path, List<String> violations) {
        if (!(value instanceof List<?> list) || list.stream().anyMatch(item -> !(item instanceof String))) {
            violations.add(path + ":type=string[]");
        }
    }

    private static void validateRequiredString(Object value, String path, List<String> violations) {
        if (!(value instanceof String text) || text.isBlank()) {
            violations.add(path + ":required-string");
        }
    }

    private static void validateOptionalString(Object value, String path, List<String> violations) {
        if (value != null && !(value instanceof String)) {
            violations.add(path + ":type=string");
        }
    }

    private static Set<String> stringSet(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return Set.copyOf(result);
    }

    public record ValidationResult(boolean valid, List<String> violations) {
    }

}
