package com.basiclab.iot.sink.javascript;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 产品编解码脚本管理：按产品隔离引擎，线程安全调用。
 *
 * @author reese
 * @email reese
 */
@Slf4j
@Component
public class JsScriptManager {

    static final int MAX_SCRIPT_BYTES = 256 * 1024;
    static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;
    static final Duration EXECUTION_TIMEOUT = Duration.ofSeconds(2);

    private static final int EXECUTOR_THREADS =
            Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors()));
    private static final AtomicInteger SCRIPT_THREAD_SEQUENCE = new AtomicInteger();
    private static final ThreadPoolExecutor SCRIPT_EXECUTOR = new ThreadPoolExecutor(
            EXECUTOR_THREADS,
            EXECUTOR_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(32),
            runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "iot-sink-js-" + SCRIPT_THREAD_SEQUENCE.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());

    private final ConcurrentMap<String, ProductScriptRuntime> runtimeCache = new ConcurrentHashMap<>();

    /**
     * 编译并校验脚本（使用临时引擎，不污染运行时缓存）。
     */
    public CheckResult checkScript(String jsText) {
        if (jsText == null || jsText.isBlank()) {
            return new CheckResult(false, "脚本内容不能为空");
        }
        try (ProductScriptRuntime runtime = ProductScriptRuntime.compile(jsText)) {
            try {
                runtime.invokeRawDataToProtocol("", new byte[0]);
            } catch (NoSuchMethodException e) {
                return new CheckResult(false, "缺少函数 rawDataToProtocol(topic, bytes)");
            } catch (ScriptException e) {
                return new CheckResult(false, "rawDataToProtocol 执行检查失败: " + e.getMessage());
            } catch (Exception ignored) {
                // 参数/业务异常忽略，只要函数存在
            }
            try {
                runtime.invokeProtocolToRawData("", new HashMap<>());
            } catch (NoSuchMethodException e) {
                return new CheckResult(false, "缺少函数 protocolToRawData(topic, message)");
            } catch (ScriptException e) {
                return new CheckResult(false, "protocolToRawData 执行检查失败: " + e.getMessage());
            } catch (Exception ignored) {
                // ignore
            }
            return new CheckResult(true, "脚本检查通过（引擎: " + runtime.engineClassName() + "）");
        } catch (ScriptException e) {
            log.warn("[checkScript][编译失败] {}", e.getMessage());
            return new CheckResult(false, "脚本编译失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[checkScript][异常]", e);
            return new CheckResult(false, "脚本检查异常: " + e.getMessage());
        }
    }

    public void addScript(String productIdentification, String jsText) throws ScriptException {
        ProductScriptRuntime runtime = ProductScriptRuntime.compile(jsText);
        ProductScriptRuntime previous = runtimeCache.put(productIdentification, runtime);
        closeQuietly(previous);
        log.info("[addScript][加载产品脚本成功 product={} engine={}]",
                productIdentification, runtime.engineClassName());
    }

    public void removeScript(String productIdentification) {
        ProductScriptRuntime removed = runtimeCache.remove(productIdentification);
        if (removed != null) {
            closeQuietly(removed);
            log.info("[removeScript][卸载产品脚本 product={}]", productIdentification);
        }
    }

    public boolean hasScript(String productIdentification) {
        return runtimeCache.containsKey(productIdentification);
    }

    public Set<String> getAllLoadedProductIdentifications() {
        return Set.copyOf(runtimeCache.keySet());
    }

    public void clearAll() {
        runtimeCache.values().forEach(JsScriptManager::closeQuietly);
        runtimeCache.clear();
        log.info("[clearAll][清空所有产品脚本缓存]");
    }

    @PreDestroy
    public void destroy() {
        clearAll();
    }

    /**
     * 上行：原始 → 平台标准 JSON 字节。无脚本或返回空时由调用方走 Codec。
     */
    public byte[] invokeRawDataToProtocol(String productIdentification, String topic, byte[] rawData) {
        ProductScriptRuntime runtime = runtimeCache.get(productIdentification);
        if (runtime == null) {
            return new byte[0];
        }
        try {
            return runtime.invokeRawDataToProtocol(topic, rawData == null ? new byte[0] : rawData);
        } catch (Exception e) {
            evictFailedRuntime(productIdentification, runtime);
            log.error("[invokeRawDataToProtocol][执行失败 product={} topic={}]", productIdentification, topic, e);
            return new byte[0];
        }
    }

    /**
     * 下行：平台消息 Map → 设备原始字节。
     */
    public byte[] invokeProtocolToRawData(String productIdentification, String topic, Map<String, Object> jsonData) {
        ProductScriptRuntime runtime = runtimeCache.get(productIdentification);
        if (runtime == null) {
            return new byte[0];
        }
        try {
            return runtime.invokeProtocolToRawData(topic, jsonData == null ? new HashMap<>() : jsonData);
        } catch (Exception e) {
            evictFailedRuntime(productIdentification, runtime);
            log.error("[invokeProtocolToRawData][执行失败 product={} topic={}]", productIdentification, topic, e);
            return new byte[0];
        }
    }

    /**
     * 使用指定脚本文本试跑（不写入缓存），供管理端「模拟调试」。
     */
    public SimulateResult simulate(String scriptContent, String direction, String topic,
                                   byte[] rawPayload, Map<String, Object> message) {
        long start = System.currentTimeMillis();
        try {
            CheckResult check = checkScript(scriptContent);
            if (!check.isSuccess()) {
                return SimulateResult.fail(check.getMessage(), start);
            }
            byte[] output;
            try (ProductScriptRuntime runtime = ProductScriptRuntime.compile(scriptContent)) {
                if ("downlink".equalsIgnoreCase(direction) || "encode".equalsIgnoreCase(direction)) {
                    output = runtime.invokeProtocolToRawData(topic, message == null ? new HashMap<>() : message);
                } else {
                    output = runtime.invokeRawDataToProtocol(topic, rawPayload == null ? new byte[0] : rawPayload);
                }
            }
            return SimulateResult.ok(output, start);
        } catch (Exception e) {
            log.warn("[simulate][失败] {}", e.getMessage());
            return SimulateResult.fail(e.getMessage(), start);
        }
    }

    private void evictFailedRuntime(String productIdentification, ProductScriptRuntime runtime) {
        if (runtimeCache.remove(productIdentification, runtime)) {
            closeQuietly(runtime);
            log.warn("[evictFailedRuntime][脚本执行失败，移除并关闭缓存 product={}]", productIdentification);
        }
    }

    private static void closeQuietly(ProductScriptRuntime runtime) {
        if (runtime == null) {
            return;
        }
        try {
            runtime.close();
        } catch (Exception e) {
            log.warn("[closeQuietly][关闭脚本引擎失败] {}", e.getMessage());
        }
    }

    public static class CheckResult {
        private final boolean success;
        private final String message;

        public CheckResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class SimulateResult {
        private final boolean success;
        private final String message;
        private final byte[] output;
        private final long elapsedMs;

        private SimulateResult(boolean success, String message, byte[] output, long elapsedMs) {
            this.success = success;
            this.message = message;
            this.output = output;
            this.elapsedMs = elapsedMs;
        }

        public static SimulateResult ok(byte[] output, long start) {
            return new SimulateResult(true, "ok", output == null ? new byte[0] : output,
                    System.currentTimeMillis() - start);
        }

        public static SimulateResult fail(String message, long start) {
            return new SimulateResult(false, message, new byte[0], System.currentTimeMillis() - start);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public byte[] getOutput() {
            return output;
        }

        public long getElapsedMs() {
            return elapsedMs;
        }
    }

    /**
     * 单产品运行时：独立引擎 + 同步调用。
     */
    static final class ProductScriptRuntime implements AutoCloseable {
        private final ScriptEngine engine;
        private final Invocable invocable;
        private final Object lock = new Object();
        private final AtomicBoolean closed = new AtomicBoolean();

        private ProductScriptRuntime(ScriptEngine engine) {
            this.engine = engine;
            this.invocable = (Invocable) engine;
        }

        static ProductScriptRuntime compile(String jsText) throws ScriptException {
            validateScriptSize(jsText);
            ScriptEngine engine = JsEngine.createEngine();
            ProductScriptRuntime runtime = new ProductScriptRuntime(engine);
            try {
                String full = JsEngine.getJsGlobalImport() + jsText;
                CompiledScript compiled = ((Compilable) engine).compile(full);
                runtime.executeWithTimeout(() -> {
                    compiled.eval();
                    return null;
                });
                return runtime;
            } catch (ScriptException e) {
                runtime.close();
                throw e;
            } catch (Exception e) {
                runtime.close();
                throw new ScriptException(e);
            }
        }

        String engineClassName() {
            return engine.getClass().getName();
        }

        byte[] invokeRawDataToProtocol(String topic, byte[] rawData) throws Exception {
            return executeWithTimeout(() -> {
                synchronized (lock) {
                    Object result = invocable.invokeFunction("rawDataToProtocol", topic, rawData);
                    return convertToBytes(result);
                }
            });
        }

        byte[] invokeProtocolToRawData(String topic, Map<String, Object> message) throws Exception {
            return executeWithTimeout(() -> {
                synchronized (lock) {
                    Object result = invocable.invokeFunction("protocolToRawData", topic, message);
                    return convertToBytes(result);
                }
            });
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (engine instanceof GraalJSScriptEngine) {
                try {
                    ((GraalJSScriptEngine) engine).getPolyglotContext().close(true);
                } catch (Exception ignored) {
                    // 引擎可能已由超时取消关闭
                }
            }
            if (engine instanceof AutoCloseable) {
                try {
                    ((AutoCloseable) engine).close();
                } catch (Exception ignored) {
                    // close 为尽力清理，不覆盖原始执行异常
                }
            }
        }

        private <T> T executeWithTimeout(Callable<T> task) throws Exception {
            if (closed.get()) {
                throw new IllegalStateException("脚本运行时已关闭");
            }
            Future<T> future;
            try {
                future = SCRIPT_EXECUTOR.submit(task);
            } catch (RejectedExecutionException e) {
                throw new ScriptException("脚本执行队列已满，请稍后重试");
            }
            try {
                return future.get(EXECUTION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                close();
                throw new ScriptException("脚本执行超过 "
                        + EXECUTION_TIMEOUT.toMillis() + "ms，已强制终止");
            } catch (InterruptedException e) {
                future.cancel(true);
                Thread.currentThread().interrupt();
                throw new ScriptException("脚本执行被中断");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                }
                throw new ScriptException("脚本执行失败: " + cause.getMessage());
            }
        }
    }

    /**
     * 将脚本返回值规范为 byte[]：支持 byte[]、UTF-8 字符串、数字数组、Graal Value。
     */
    static byte[] convertToBytes(Object result) {
        if (result == null) {
            return new byte[0];
        }
        // GraalJS 可能包装为 Value
        if ("org.graalvm.polyglot.Value".equals(result.getClass().getName())) {
            try {
                Object host = result.getClass().getMethod("isHostObject").invoke(result);
                if (Boolean.TRUE.equals(host)) {
                    Object unwrapped = result.getClass().getMethod("asHostObject").invoke(result);
                    return convertToBytes(unwrapped);
                }
                Object isString = result.getClass().getMethod("isString").invoke(result);
                if (Boolean.TRUE.equals(isString)) {
                    return String.valueOf(result.getClass().getMethod("asString").invoke(result))
                            .getBytes(StandardCharsets.UTF_8);
                }
                if (Boolean.TRUE.equals(result.getClass().getMethod("hasArrayElements").invoke(result))) {
                    long size = ((Number) result.getClass().getMethod("getArraySize").invoke(result)).longValue();
                    requireOutputSize(size);
                    byte[] out = new byte[(int) size];
                    for (int i = 0; i < size; i++) {
                        Object el = result.getClass().getMethod("getArrayElement", long.class)
                                .invoke(result, (long) i);
                        out[i] = toByte(el);
                    }
                    return out;
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("无法转换 Graal Value: " + e.getMessage(), e);
            }
        }
        if (result instanceof byte[]) {
            byte[] bytes = (byte[]) result;
            requireOutputSize(bytes.length);
            return bytes;
        }
        if (result instanceof Byte[]) {
            Byte[] arr = (Byte[]) result;
            requireOutputSize(arr.length);
            byte[] out = new byte[arr.length];
            for (int i = 0; i < arr.length; i++) {
                out[i] = arr[i] == null ? 0 : arr[i];
            }
            return out;
        }
        if (result instanceof CharSequence) {
            CharSequence chars = (CharSequence) result;
            requireOutputSize(chars.length());
            byte[] bytes = chars.toString().getBytes(StandardCharsets.UTF_8);
            requireOutputSize(bytes.length);
            return bytes;
        }
        if (result instanceof Collection) {
            Collection<?> col = (Collection<?>) result;
            requireOutputSize(col.size());
            byte[] out = new byte[col.size()];
            int i = 0;
            for (Object o : col) {
                out[i++] = toByte(o);
            }
            return out;
        }
        if (result.getClass().isArray()) {
            int len = Array.getLength(result);
            requireOutputSize(len);
            byte[] out = new byte[len];
            for (int i = 0; i < len; i++) {
                out[i] = toByte(Array.get(result, i));
            }
            return out;
        }
        throw new IllegalArgumentException(
                "脚本返回类型不支持: " + result.getClass().getName()
                        + "（请返回 byte[] 或 UTF-8 字符串，推荐 jsUtil.utf8Bytes(...)）");
    }

    private static void validateScriptSize(String jsText) throws ScriptException {
        if (jsText == null) {
            throw new ScriptException("脚本内容不能为空");
        }
        int bytes = jsText.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_SCRIPT_BYTES) {
            throw new ScriptException("脚本大小超过 " + MAX_SCRIPT_BYTES + " 字节限制");
        }
    }

    private static void requireOutputSize(long size) {
        if (size < 0 || size > MAX_OUTPUT_BYTES) {
            throw new IllegalArgumentException("脚本输出超过 " + MAX_OUTPUT_BYTES + " 字节限制");
        }
    }

    private static byte toByte(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Number) {
            return ((Number) o).byteValue();
        }
        return (byte) Integer.parseInt(String.valueOf(o));
    }
}
