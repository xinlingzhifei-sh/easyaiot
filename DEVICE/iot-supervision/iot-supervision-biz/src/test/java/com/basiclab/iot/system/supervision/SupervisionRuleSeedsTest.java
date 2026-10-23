package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds.RuleSeed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionRuleSeedsTest {

    @Test
    void p0RuleCodesMatchClosureContract() {
        assertEquals(List.of(
                SupervisionRuleSeeds.RULE_DEVICE_OFFLINE_NORMAL,
                SupervisionRuleSeeds.RULE_CAMERA_OFFLINE_KEY_AREA,
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                SupervisionRuleSeeds.RULE_SUDDEN_ILLNESS,
                SupervisionRuleSeeds.RULE_FIGHT,
                SupervisionRuleSeeds.RULE_RESTRICTED_AREA,
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                SupervisionRuleSeeds.RULE_REHAB_WITHDRAWAL,
                SupervisionRuleSeeds.RULE_RED_PHYSIOLOGY
        ), SupervisionRuleSeeds.listP0Rules().stream().map(RuleSeed::getRuleCode).toList());
    }

    @Test
    void deviceOfflineNormalRequiresManualConfirmation() {
        RuleSeed seed = SupervisionRuleSeeds.findByCode(SupervisionRuleSeeds.RULE_DEVICE_OFFLINE_NORMAL).orElseThrow();

        assertEquals("普通设备离线", seed.getAlertType());
        assertEquals(SupervisionRuleSeeds.GENERATION_STRATEGY_MANUAL_CONFIRM, seed.getGenerationStrategy());
        assertEquals("设备保障", seed.getEventType());
        assertEquals(SupervisionEventLevelEnum.L1, seed.getDefaultLevel());
        assertEquals(List.of("运维", "指挥中心"), seed.getDefaultResponsibilityChain());
    }

    @Test
    void highRiskLifeHealthRulesAutoCreateL4Events() {
        List<String> highRiskCodes = List.of(
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                SupervisionRuleSeeds.RULE_SUDDEN_ILLNESS,
                SupervisionRuleSeeds.RULE_RED_PHYSIOLOGY
        );

        for (String ruleCode : highRiskCodes) {
            RuleSeed seed = SupervisionRuleSeeds.findByCode(ruleCode).orElseThrow();
            assertEquals(SupervisionRuleSeeds.GENERATION_STRATEGY_AUTO_CREATE, seed.getGenerationStrategy());
            assertEquals("生命健康", seed.getEventType());
            assertEquals(SupervisionEventLevelEnum.L4, seed.getDefaultLevel());
        }
    }

    @Test
    void unknownRuleCodeReturnsEmpty() {
        Optional<RuleSeed> seed = SupervisionRuleSeeds.findByCode("RULE_UNKNOWN");

        assertFalse(seed.isPresent());
    }

    @Test
    void seedListCannotBeMutatedByCallers() {
        List<RuleSeed> seeds = SupervisionRuleSeeds.listP0Rules();

        assertThrows(UnsupportedOperationException.class, () -> seeds.add(seeds.get(0)));
        assertTrue(SupervisionRuleSeeds.findByCode(null).isEmpty());
    }

}
