package com.basiclab.iot.sink.javascript;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class JsEngineSecurityTest {

    private static final String REQUIRED_FUNCTIONS = """
            function rawDataToProtocol(topic, bytes) { return bytes; }
            function protocolToRawData(topic, message) { return jsUtil.utf8Bytes('ok'); }
            """;

    private final JsScriptManager manager = new JsScriptManager();

    @Test
    void standardCodecFunctionsRemainAvailable() throws Exception {
        manager.addScript("safe", REQUIRED_FUNCTIONS);

        assertArrayEquals(new byte[]{1, 2, 3},
                manager.invokeRawDataToProtocol("safe", "/topic", new byte[]{1, 2, 3}));
        assertArrayEquals("ok".getBytes(StandardCharsets.UTF_8),
                manager.invokeProtocolToRawData("safe", "/topic", Map.of()));
    }

    @Test
    void scriptCannotReflectFromInjectedUtilityObject() {
        JsScriptManager.CheckResult result = manager.checkScript("""
                var leakedClass = jsUtil.getClass();
                function rawDataToProtocol(topic, bytes) { return bytes; }
                function protocolToRawData(topic, message) { return jsUtil.utf8Bytes('ok'); }
                """);

        assertFalse(result.isSuccess());
    }

    @Test
    void scriptCannotReachHostOrPolyglotCapabilities() {
        for (String probe : List.of(
                "Java.type('java.lang.Runtime');",
                "Polyglot.eval('python', '1 + 1');",
                "load('/etc/passwd');")) {
            JsScriptManager.CheckResult result = manager.checkScript(probe + REQUIRED_FUNCTIONS);

            assertFalse(result.isSuccess(), probe);
        }
    }

    @Test
    void unboundedTopLevelLoopIsStopped() {
        JsScriptManager.CheckResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(3),
                () -> manager.checkScript("while (true) {}" + REQUIRED_FUNCTIONS)
        );

        assertFalse(result.isSuccess());
    }

    @Test
    void statementBudgetStopsLargeFinitePrograms() {
        JsScriptManager.CheckResult result = assertTimeoutPreemptively(
                Duration.ofSeconds(3),
                () -> manager.checkScript(
                        "var n = 0; for (var i = 0; i < 2000000; i++) { n += i; }"
                                + REQUIRED_FUNCTIONS)
        );

        assertFalse(result.isSuccess());
    }

    @Test
    void runtimeLoopIsStoppedAndClosedRuntimeIsEvicted() throws Exception {
        String script = """
                function rawDataToProtocol(topic, bytes) { while (true) {} }
                function protocolToRawData(topic, message) { return jsUtil.utf8Bytes('ok'); }
                """;
        assertFalse(manager.checkScript(script).isSuccess());
        manager.addScript("runtime-loop", script);

        byte[] output = assertTimeoutPreemptively(
                Duration.ofSeconds(3),
                () -> manager.invokeRawDataToProtocol("runtime-loop", "/topic", new byte[0])
        );

        assertArrayEquals(new byte[0], output);
        assertFalse(manager.hasScript("runtime-loop"));
    }

    @Test
    void hostBuffersCannotAllocateBeyondOutputLimit() {
        byte[] oversized = new byte[JsScriptManager.MAX_OUTPUT_BYTES + 1];

        assertThrows(IllegalArgumentException.class, () -> new WriteBuffer().writeBytes(oversized));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadBuffer(new byte[1]).readBytes(JsScriptManager.MAX_OUTPUT_BYTES + 1));
    }

    @Test
    void builtInTemplateStillPassesSecurityChecks() {
        JsScriptManager.CheckResult result = manager.checkScript(ProductScriptTemplates.COMPACT_TEXT);

        assertTrue(result.isSuccess(), result.getMessage());
    }
}
