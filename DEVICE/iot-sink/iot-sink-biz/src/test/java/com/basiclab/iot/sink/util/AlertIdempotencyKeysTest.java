package com.basiclab.iot.sink.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 与 VIDEO tests/test_alert_ingress_contract.py 读同一批 testdata/contracts/alert_*.json 样例。
 */
class AlertIdempotencyKeysTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldResolveSameKeyAsSharedFixtures() throws IOException {
        List<Path> fixtures = alertFixtures();
        assertEquals(4, fixtures.size());
        for (Path fixture : fixtures) {
            JsonNode data = MAPPER.readTree(fixture.toFile());
            JsonNode expected = data.get("_expect").get("idempotency_key");
            String actual = AlertIdempotencyKeys.resolve(data);
            if (expected.isNull()) {
                assertNull(actual, fixture.getFileName().toString());
            } else {
                assertEquals(expected.asText(), actual, fixture.getFileName().toString());
            }
        }
    }

    @Test
    void shouldFallBackToEventIdWhenNormalizingFlatPayload() throws IOException {
        JsonNode eventOnly = MAPPER.readTree(contractRoot().resolve("alert_event_id_only.json").toFile());
        ObjectNode out = MAPPER.createObjectNode();

        AlertIdempotencyKeys.copyInto(eventOnly, out);

        assertEquals("fixture-event-only", out.get("correlation_id").asText());
        assertEquals("fixture-event-only", out.get("event_id").asText());
    }

    @Test
    void shouldPreferCorrelationIdAndKeepProducerEventId() throws IOException {
        JsonNode flat = MAPPER.readTree(contractRoot().resolve("alert_flat_hook.json").toFile());
        ObjectNode out = MAPPER.createObjectNode();

        AlertIdempotencyKeys.copyInto(flat, out);

        assertEquals("fixture-flat-hook", out.get("correlation_id").asText());
        assertEquals("fixture-event-ignored", out.get("event_id").asText());
    }

    @Test
    void shouldLeaveKeyAbsentWhenProducerSentBlankKey() throws IOException {
        JsonNode blank = MAPPER.readTree(contractRoot().resolve("alert_without_key.json").toFile());
        ObjectNode out = MAPPER.createObjectNode();

        AlertIdempotencyKeys.copyInto(blank, out);

        assertFalse(out.has("correlation_id"));
        assertFalse(out.has("event_id"));
        assertNull(AlertIdempotencyKeys.resolve(null));
    }

    private static List<Path> alertFixtures() throws IOException {
        try (Stream<Path> files = Files.list(contractRoot())) {
            return files.filter(path -> path.getFileName().toString().startsWith("alert_"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static Path contractRoot() {
        String override = System.getenv("EASYAIOT_CONTRACT_ROOT");
        Path root = override != null && !override.isBlank()
                ? Paths.get(override)
                : Paths.get("..", "..", "..");
        Path contracts = root.resolve("testdata").resolve("contracts").normalize();
        assertTrue(Files.isDirectory(contracts), "缺少共享契约样例目录: " + contracts.toAbsolutePath());
        return contracts;
    }
}
