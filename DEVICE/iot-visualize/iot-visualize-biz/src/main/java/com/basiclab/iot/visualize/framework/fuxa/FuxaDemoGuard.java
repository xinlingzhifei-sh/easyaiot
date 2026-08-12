package com.basiclab.iot.visualize.framework.fuxa;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * yFeiEye 内置 FUXA 演示画面保护：平台侧识别后强制预览，避免经 SSO/编辑器改删工艺图。
 * 与 .scripts/fuxa/seed_fuxa_demo.sh / visualize_demo_seed.sql 保持一致。
 */
public final class FuxaDemoGuard {

    /** 平台种子项目 ID（visualize_demo_seed.sql） */
    public static final Set<Long> DEMO_PROJECT_IDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            9311L, 9312L, 9313L, 9314L
    )));

    /** FUXA 演示画面名（与 .fuxap / editor_ref 一致） */
    public static final Set<String> DEMO_VIEW_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "水厂工艺总貌",
            "产线运行看板",
            "厂区管网组态",
            "配电室电力监视"
    )));

    private FuxaDemoGuard() {
    }

    public static boolean isDemoProjectId(Long id) {
        return id != null && DEMO_PROJECT_IDS.contains(id);
    }

    public static boolean isDemoView(String viewOrName) {
        if (!StringUtils.hasText(viewOrName)) {
            return false;
        }
        String trimmed = viewOrName.trim();
        if (trimmed.startsWith("/")) {
            return false;
        }
        return DEMO_VIEW_NAMES.contains(trimmed);
    }

    /**
     * 是否应按只读演示处理（项目 ID 或画面名命中内置演示集）。
     */
    public static boolean isProtectedDemo(Long projectId, String projectName, String editorRef) {
        if (isDemoProjectId(projectId)) {
            return true;
        }
        return isDemoView(projectName) || isDemoView(editorRef);
    }

}
