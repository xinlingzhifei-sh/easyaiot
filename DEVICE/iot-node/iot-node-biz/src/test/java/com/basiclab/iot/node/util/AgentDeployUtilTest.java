package com.basiclab.iot.node.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * iot-node 经 SSH 部署 Agent 时只上传 {@link AgentDeployUtil#SYNC_RELATIVE_FILES} 与 sentinel/、registry/ 目录，
 * 并直接在安装目录执行 install.sh（此时 install.sh 自身的源码同步会被跳过）。
 * 这里从 SENTINEL 源码推导出必须上传的文件，锁住两边一致。
 */
class AgentDeployUtilTest {

    /** 节点 launcher 优先执行 run_sentinel.py，缺失时回退 run_agent.py。 */
    private static final List<String> ENTRY_POINTS = List.of("run_sentinel.py", "run_agent.py");

    /** iot-node 按目录整体上传的包，不需要出现在文件清单里。 */
    private static final Set<String> SYNCED_DIRECTORIES = Set.of("sentinel", "registry");

    private static final Pattern FROM_IMPORT = Pattern.compile("^\\s*from\\s+([A-Za-z_][\\w.]*)\\s+import\\b");
    private static final Pattern PLAIN_IMPORT = Pattern.compile("^\\s*import\\s+([A-Za-z_][\\w.]*(?:\\s*,\\s*[A-Za-z_][\\w.]*)*)");
    /** install.sh 顶部的同步清单数组：AGENT_SOURCE_FILES=( ... )。 */
    private static final Pattern INSTALL_SH_SOURCE_FILES = Pattern.compile("(?m)^AGENT_SOURCE_FILES=\\(([^)]*)\\)");

    @Test
    void syncedFilesCoverEveryLocalModuleTheAgentImports() throws IOException {
        Path sentinel = locateSentinelSource();

        Set<String> required = localImportClosure(sentinel);
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(Set.of(AgentDeployUtil.SYNC_RELATIVE_FILES));

        assertTrue(required.contains("agent_server.py"), "导入关系解析异常: " + required);
        assertTrue(missing.isEmpty(),
                "AgentDeployUtil.SYNC_RELATIVE_FILES 缺少 Agent 运行时会导入的模块: " + missing);
    }

    @Test
    void syncedFilesCoverEverythingInstallShCopies() throws IOException {
        Path sentinel = locateSentinelSource();
        String installSh = Files.readString(sentinel.resolve("install.sh"), StandardCharsets.UTF_8);
        // sync_agent_sources() 遍历 AGENT_SOURCE_FILES 逐个拷贝（目录另由 AGENT_SOURCE_DIRS 整体同步）
        Matcher matcher = INSTALL_SH_SOURCE_FILES.matcher(installSh);
        assertTrue(matcher.find(), "install.sh 中未找到 AGENT_SOURCE_FILES 数组");

        Set<String> copied = new TreeSet<>();
        for (String line : matcher.group(1).split("\\R")) {
            String entry = line.replaceAll("#.*", "").trim();
            if (!entry.isEmpty()) {
                copied.add(entry);
            }
        }
        assertFalse(copied.isEmpty(), "install.sh AGENT_SOURCE_FILES 文件清单解析为空");

        Set<String> missing = new TreeSet<>(copied);
        missing.removeAll(Set.of(AgentDeployUtil.SYNC_RELATIVE_FILES));
        assertTrue(missing.isEmpty(),
                "install.sh 会同步但 AgentDeployUtil.SYNC_RELATIVE_FILES 没有上传的文件: " + missing);
    }

    @Test
    void everySyncedFileExistsInSentinelSourceWithoutDuplicates() {
        Path sentinel = locateSentinelSource();
        List<String> synced = List.of(AgentDeployUtil.SYNC_RELATIVE_FILES);

        assertEquals(synced.size(), new LinkedHashSet<>(synced).size(), "SYNC_RELATIVE_FILES 有重复项: " + synced);
        for (String relative : synced) {
            // 缺任何一个文件，syncAgentFiles 都会以 AGENT_SOURCE_NOT_FOUND 中止部署
            assertTrue(Files.isRegularFile(sentinel.resolve(relative)), "SENTINEL 源码中不存在: " + relative);
        }
    }

    @Test
    void completeOfflineBundleHasNoMissingWheels() {
        assertEquals(List.of(), AgentDeployUtil.findMissingAgentWheels(completeBundle("3.9"), "3.9"));
        assertEquals(List.of(), AgentDeployUtil.findMissingAgentWheels(completeBundle("3.12"), "3.12"));
    }

    @Test
    void offlineBundleWithoutWaitressIsIncomplete() {
        for (String python : List.of("3.9", "3.10", "3.12")) {
            List<String> bundle = new ArrayList<>(completeBundle(python));
            bundle.removeIf(name -> name.startsWith("waitress"));

            assertEquals(List.of("waitress"), AgentDeployUtil.findMissingAgentWheels(bundle, python),
                    "target python " + python);
        }
    }

    @Test
    void onlyMissingWaitressIsDegradable() {
        assertTrue(AgentDeployUtil.isDegradableAgentWheelGap(List.of("waitress")));
        assertFalse(AgentDeployUtil.isDegradableAgentWheelGap(List.of()));
        assertFalse(AgentDeployUtil.isDegradableAgentWheelGap(List.of("waitress", "flask")));
        assertFalse(AgentDeployUtil.isDegradableAgentWheelGap(List.of("pip")));
    }

    @Test
    void wheelCheckCoversExportScriptCompletenessCheck() throws IOException {
        Path sentinel = locateSentinelSource();
        String exportScript = Files.readString(sentinel.resolve("export_pip_wheels.sh"), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("for pat in ([\\w\\s-]+); do").matcher(exportScript);
        assertTrue(matcher.find(), "export_pip_wheels.sh 中未找到按文件名校验的 wheel 清单");

        for (String python : List.of("3.9", "3.12")) {
            for (String pattern : matcher.group(1).trim().split("\\s+")) {
                List<String> bundle = new ArrayList<>(completeBundle(python));
                bundle.removeIf(name -> name.toLowerCase(Locale.ROOT)
                        .startsWith(pattern.toLowerCase(Locale.ROOT)));

                assertFalse(AgentDeployUtil.findMissingAgentWheels(bundle, python).isEmpty(),
                        "export_pip_wheels.sh 要求 " + pattern + "，控制面却认为缺少它的离线包已就绪（Python "
                                + python + "）");
            }
        }
    }

    private static List<String> completeBundle(String python) {
        List<String> names = new ArrayList<>(List.of(
                "requests-2.32.3-py3-none-any.whl",
                "psutil-6.0.0-cp36-abi3-manylinux_2_17_x86_64.whl",
                "Flask-3.0.3-py3-none-any.whl",
                "minio-7.2.7-py3-none-any.whl",
                "waitress-3.0.2-py3-none-any.whl",
                "pip-24.2-py3-none-any.whl",
                "setuptools-72.1.0-py3-none-any.whl",
                "wheel-0.44.0-py3-none-any.whl"));
        if ("3.9".equals(python)) {
            names.add("importlib_metadata-8.2.0-py3-none-any.whl");
            names.add("zipp-3.20.0-py3-none-any.whl");
        }
        return names;
    }

    /**
     * 从入口脚本出发，收集 SENTINEL 根目录下被（直接或间接）导入的本地模块文件。
     * 函数体内的延迟导入同样计入：它们在运行时执行到时一样会因文件缺失而失败。
     */
    private static Set<String> localImportClosure(Path sentinel) throws IOException {
        Set<String> required = new TreeSet<>();
        Set<String> scannedPackages = new TreeSet<>();
        Deque<Path> pending = new ArrayDeque<>();
        for (String entry : ENTRY_POINTS) {
            required.add(entry);
            pending.add(sentinel.resolve(entry));
        }
        while (!pending.isEmpty()) {
            for (String module : importedTopLevelModules(pending.poll())) {
                Path moduleFile = sentinel.resolve(module + ".py");
                Path packageDir = sentinel.resolve(module);
                if (Files.isRegularFile(moduleFile) && required.add(module + ".py")) {
                    pending.add(moduleFile);
                } else if (SYNCED_DIRECTORIES.contains(module) && Files.isDirectory(packageDir)
                        && scannedPackages.add(module)) {
                    try (Stream<Path> files = Files.walk(packageDir)) {
                        files.filter(p -> p.toString().endsWith(".py")).forEach(pending::add);
                    }
                }
            }
        }
        return required;
    }

    private static Set<String> importedTopLevelModules(Path pythonFile) throws IOException {
        Set<String> modules = new LinkedHashSet<>();
        for (String line : Files.readAllLines(pythonFile, StandardCharsets.UTF_8)) {
            Matcher from = FROM_IMPORT.matcher(line);
            if (from.find()) {
                modules.add(topLevel(from.group(1)));
                continue;
            }
            Matcher plain = PLAIN_IMPORT.matcher(line);
            if (plain.find()) {
                for (String name : plain.group(1).split(",")) {
                    modules.add(topLevel(name.trim()));
                }
            }
        }
        return modules;
    }

    private static String topLevel(String dotted) {
        int dot = dotted.indexOf('.');
        return dot < 0 ? dotted : dotted.substring(0, dot);
    }

    /** DEVICE 单独抽取部署时没有 SENTINEL 源码，此时跳过而不是失败。 */
    private static Path locateSentinelSource() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("SENTINEL");
            if (Files.isRegularFile(candidate.resolve("run_sentinel.py"))) {
                return candidate;
            }
            dir = dir.getParent();
        }
        assumeTrue(false, "未找到 SENTINEL 源码目录，跳过 Agent 同步清单校验");
        return null;
    }
}
