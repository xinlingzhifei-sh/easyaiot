package com.genersoft.iot.vmp.gb28181.controller;

import com.genersoft.iot.vmp.conf.SipConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeviceQueryTest {

    @Test
    void generateDeviceAccessInfoReturnsExistingParametersByDefault() {
        DeviceQuery controller = new DeviceQuery();
        ReflectionTestUtils.setField(controller, "sipConfig", sipConfig());

        String first = controller.generateDeviceAccessInfo(2).getData();
        String second = controller.generateDeviceAccessInfo(2).getData();

        assertEquals(first, second);
    }

    @Test
    void generateDeviceAccessInfoCanRefreshParametersWhenForced() {
        DeviceQuery controller = new DeviceQuery();
        ReflectionTestUtils.setField(controller, "sipConfig", sipConfig());

        String first = controller.generateDeviceAccessInfo(2).getData();
        String refreshed = controller.generateDeviceAccessInfo(2, true).getData();

        assertNotEquals(first, refreshed);
    }

    @Test
    void generateDeviceAccessInfoUsesConfiguredPasswordWhenRegisterPasswordAuthIsEnabled() {
        DeviceQuery controller = new DeviceQuery();
        SipConfig sipConfig = sipConfig();
        sipConfig.setRegisterPasswordAuth(true);
        sipConfig.setPassword("admin123");
        ReflectionTestUtils.setField(controller, "sipConfig", sipConfig);

        String accessInfo = controller.generateDeviceAccessInfo(1).getData();

        assertTrue(accessInfo.contains("SIP用户认证密码：admin123"));
    }

    private static SipConfig sipConfig() {
        SipConfig sipConfig = new SipConfig();
        sipConfig.setId("44010200492000000001");
        sipConfig.setDomain("4401020049");
        sipConfig.setPort(5060);
        sipConfig.setShowIp("1.95.118.210");
        return sipConfig;
    }
}
