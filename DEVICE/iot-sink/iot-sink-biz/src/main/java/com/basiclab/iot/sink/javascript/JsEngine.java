package com.basiclab.iot.sink.javascript;

import com.oracle.truffle.js.scriptengine.GraalJSScriptEngine;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.io.IOAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

/**
 * JavaScript 引擎工厂。
 * <p>
 * 每个产品脚本使用独立 {@link ScriptEngine}，避免多产品函数互相覆盖。
 * Java 21 默认无 Nashorn，仅允许使用项目锁定版本的 GraalJS。
 * <p>
 * 脚本只能调用显式加入白名单的编解码方法，禁止宿主类查找、IO、进程、
 * 线程、环境变量和 Polyglot 跨语言访问。
 *
 * @author reese
 * @email reese
 */
public final class JsEngine {

    private static final Logger log = LoggerFactory.getLogger(JsEngine.class);

    private static final JsUtilFunction JS_UTIL = new JsUtilFunction();

    private static final String ENGINE_IMPORT =
            "function ReadBuffer(data) { return jsUtil.readBuffer(data); }\n"
                    + "function WriteBuffer() { return jsUtil.writeBuffer(); }\n";

    private static final String ENGINE_WARMUP =
            "jsUtil.utf8Bytes(jsUtil.toJsonString(jsUtil.newMap()));";

    private static final HostAccess SCRIPT_HOST_ACCESS = buildHostAccess();
    private static final ResourceLimits SCRIPT_RESOURCE_LIMITS = ResourceLimits.newBuilder()
            .statementLimit(1_000_000, source -> true)
            .build();

    static {
        // 降低解释器模式告警噪音（非 GraalVM JDK 时正常）
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    }

    private JsEngine() {
    }

    /**
     * 创建隔离的 ScriptEngine。
     */
    public static ScriptEngine createEngine() {
        Context.Builder contextBuilder = Context.newBuilder("js")
                .allowHostAccess(SCRIPT_HOST_ACCESS)
                .allowHostClassLookup(className -> false)
                .allowHostClassLoading(false)
                .allowIO(IOAccess.NONE)
                .allowNativeAccess(false)
                .allowCreateProcess(false)
                .allowCreateThread(false)
                .allowEnvironmentAccess(EnvironmentAccess.NONE)
                .allowPolyglotAccess(PolyglotAccess.NONE)
                .resourceLimits(SCRIPT_RESOURCE_LIMITS);
        ScriptEngine engine = GraalJSScriptEngine.create(null, contextBuilder);
        engine.put("jsUtil", JS_UTIL);

        // 固定可信表达式不接触用户输入；将 Graal HostAccess 与 JSON 冷启动移出用户脚本的 2 秒预算。
        try {
            engine.eval(ENGINE_WARMUP);
        } catch (ScriptException e) {
            try {
                ((GraalJSScriptEngine) engine).close();
            } catch (Exception closeException) {
                e.addSuppressed(closeException);
            }
            throw new IllegalStateException("无法预热 JavaScript 引擎", e);
        }

        log.debug("[createEngine][创建 JS 引擎: {}]", engine.getClass().getName());
        return engine;
    }

    public static String getJsGlobalImport() {
        return ENGINE_IMPORT;
    }

    public static String engineName() {
        try (GraalJSScriptEngine engine = (GraalJSScriptEngine) createEngine()) {
            return engine.getClass().getName();
        } catch (Exception e) {
            return "unavailable: " + e.getMessage();
        }
    }

    private static HostAccess buildHostAccess() {
        HostAccess.Builder builder = HostAccess.newBuilder(HostAccess.NONE)
                .allowArrayAccess(true)
                .allowListAccess(true)
                .allowMapAccess(true);
        allowPublicDeclaredMethods(builder, JsUtilFunction.class);
        allowPublicDeclaredMethods(builder, ReadBuffer.class);
        allowPublicDeclaredMethods(builder, WriteBuffer.class);
        allowMethod(builder, Map.class, "get", Object.class);
        allowMethod(builder, Map.class, "put", Object.class, Object.class);
        allowMethod(builder, Map.class, "containsKey", Object.class);
        allowMethod(builder, Map.class, "size");
        allowMethod(builder, Map.class, "isEmpty");
        allowMethod(builder, List.class, "get", int.class);
        allowMethod(builder, List.class, "size");
        return builder.build();
    }

    private static void allowPublicDeclaredMethods(HostAccess.Builder builder, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers())) {
                builder.allowAccess(method);
            }
        }
    }

    private static void allowMethod(HostAccess.Builder builder, Class<?> type,
                                    String name, Class<?>... parameterTypes) {
        try {
            builder.allowAccess(type.getMethod(name, parameterTypes));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("无法注册脚本白名单方法: " + type.getName() + "." + name, e);
        }
    }
}
