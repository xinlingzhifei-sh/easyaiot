package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.service.supervision.AlertReviewDataSchemaValidator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertReviewDataSchemaValidatorTest {

    @Test
    void loadsVersionedSchemaAndValidatesReviewDataPayload() {
        AlertReviewDataSchemaValidator validator = AlertReviewDataSchemaValidator.loadV1();

        AlertReviewDataSchemaValidator.ValidationResult result = validator.validate(validReviewData());

        assertTrue(result.valid(), () -> String.join(",", result.violations()));
    }

    @Test
    void rejectsOutOfRangeConfidenceMalformedBboxAndNonStringCorrelation() {
        AlertReviewDataSchemaValidator validator = AlertReviewDataSchemaValidator.loadV1();
        Map<String, Object> invalid = new LinkedHashMap<>(validReviewData());
        invalid.put("confidence", 1.4D);
        invalid.put("bbox", List.of(1D, 2D, 3D));
        invalid.put("correlationId", 42L);

        AlertReviewDataSchemaValidator.ValidationResult result = validator.validate(Map.copyOf(invalid));

        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(value -> value.startsWith("confidence:range=")));
        assertTrue(result.violations().stream().anyMatch(value -> value.startsWith("bbox:items=")));
        assertTrue(result.violations().contains("correlationId:type=string"));
    }

    private static Map<String, Object> validReviewData() {
        Map<String, Object> detection = Map.of(
                "sourceAlertId", "alert-001",
                "alertTime", "2026-07-10T06:11:30",
                "cameraId", "camera-01",
                "labels", List.of("person"),
                "zones", List.of("zone-a"),
                "objectIds", List.of("object-01"),
                "confidence", 0.92D,
                "bbox", List.of(1D, 2D, 3D, 4D),
                "correlationId", "corr-001"
        );
        Map<String, Object> segment = Map.of(
                "segmentId", "segment-001",
                "cameraId", "camera-01",
                "severity", "alert",
                "status", "active",
                "startTime", "2026-07-10T06:11:30",
                "endTime", "2026-07-10T06:11:30",
                "sourceAlertIds", List.of("alert-001"),
                "objectIds", List.of("object-01"),
                "zones", List.of("zone-a")
        );
        return Map.of(
                "reviewDataVersion", 1,
                "labels", List.of("person"),
                "zones", List.of("zone-a"),
                "objectIds", List.of("object-01"),
                "objects", List.of(Map.of(
                        "id", "object-01",
                        "label", "person",
                        "confidence", 0.92D,
                        "bbox", List.of(1D, 2D, 3D, 4D)
                )),
                "detections", List.of(detection),
                "reviewSegment", segment,
                "confidence", 0.92D,
                "bbox", List.of(1D, 2D, 3D, 4D),
                "correlationId", "corr-001"
        );
    }

}
