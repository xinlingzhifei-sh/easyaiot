package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

public final class SupervisionRuleSeeds {

    public static final String RULE_DEVICE_OFFLINE_NORMAL = "RULE_DEVICE_OFFLINE_NORMAL";
    public static final String RULE_CAMERA_OFFLINE_KEY_AREA = "RULE_CAMERA_OFFLINE_KEY_AREA";
    public static final String RULE_FALL_DOWN = "RULE_FALL_DOWN";
    public static final String RULE_SUDDEN_ILLNESS = "RULE_SUDDEN_ILLNESS";
    public static final String RULE_FIGHT = "RULE_FIGHT";
    public static final String RULE_RESTRICTED_AREA = "RULE_RESTRICTED_AREA";
    public static final String RULE_ABNORMAL_GATHERING = "RULE_ABNORMAL_GATHERING";
    public static final String RULE_REHAB_WITHDRAWAL = "RULE_REHAB_WITHDRAWAL";
    public static final String RULE_RED_PHYSIOLOGY = "RULE_RED_PHYSIOLOGY";

    public static final String GENERATION_STRATEGY_MANUAL_CONFIRM = "manual_confirm";
    public static final String GENERATION_STRATEGY_AUTO_CREATE = "auto_create";

    private static final List<RuleSeed> P0_RULE_SEEDS = List.of(
            seed(RULE_DEVICE_OFFLINE_NORMAL, "普通设备离线", GENERATION_STRATEGY_MANUAL_CONFIRM,
                    "设备保障", SupervisionEventLevelEnum.L1, "运维", "指挥中心"),
            seed(RULE_CAMERA_OFFLINE_KEY_AREA, "重点区域摄像头离线", GENERATION_STRATEGY_AUTO_CREATE,
                    "设备保障", SupervisionEventLevelEnum.L2, "运维", "指挥中心"),
            seed(RULE_FALL_DOWN, "倒地", GENERATION_STRATEGY_AUTO_CREATE,
                    "生命健康", SupervisionEventLevelEnum.L4, "监区民警", "医务", "值班领导"),
            seed(RULE_SUDDEN_ILLNESS, "突发疾病", GENERATION_STRATEGY_AUTO_CREATE,
                    "生命健康", SupervisionEventLevelEnum.L4, "监区民警", "医务", "值班领导"),
            seed(RULE_FIGHT, "打架斗殴", GENERATION_STRATEGY_AUTO_CREATE,
                    "监管秩序", SupervisionEventLevelEnum.L3, "监区民警", "值班领导"),
            seed(RULE_RESTRICTED_AREA, "越界或重点区域入侵", GENERATION_STRATEGY_AUTO_CREATE,
                    "区域安全", SupervisionEventLevelEnum.L2, "指挥中心", "现场民警"),
            seed(RULE_ABNORMAL_GATHERING, "聚集或异常接触", GENERATION_STRATEGY_MANUAL_CONFIRM,
                    "监管秩序", SupervisionEventLevelEnum.L2, "监区民警"),
            seed(RULE_REHAB_WITHDRAWAL, "戒断或康复异常", GENERATION_STRATEGY_AUTO_CREATE,
                    "戒毒康复", SupervisionEventLevelEnum.L3, "大队民警", "医务/康复"),
            seed(RULE_RED_PHYSIOLOGY, "生理红色急症", GENERATION_STRATEGY_AUTO_CREATE,
                    "生命健康", SupervisionEventLevelEnum.L4, "医务", "监区民警", "值班领导")
    );

    private SupervisionRuleSeeds() {
    }

    public static List<RuleSeed> listP0Rules() {
        return P0_RULE_SEEDS;
    }

    public static Optional<RuleSeed> findByCode(String ruleCode) {
        if (ruleCode == null) {
            return Optional.empty();
        }
        return P0_RULE_SEEDS.stream()
                .filter(seed -> seed.getRuleCode().equals(ruleCode))
                .findFirst();
    }

    private static RuleSeed seed(String ruleCode, String alertType, String generationStrategy,
                                 String eventType, SupervisionEventLevelEnum defaultLevel,
                                 String... defaultResponsibilityChain) {
        return new RuleSeed(ruleCode, alertType, generationStrategy, eventType,
                defaultLevel, List.of(defaultResponsibilityChain));
    }

    @Getter
    @AllArgsConstructor
    public static final class RuleSeed {

        private final String ruleCode;
        private final String alertType;
        private final String generationStrategy;
        private final String eventType;
        private final SupervisionEventLevelEnum defaultLevel;
        private final List<String> defaultResponsibilityChain;

    }

}
