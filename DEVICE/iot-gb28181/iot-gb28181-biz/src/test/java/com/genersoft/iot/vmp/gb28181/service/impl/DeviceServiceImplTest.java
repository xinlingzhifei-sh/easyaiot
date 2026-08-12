package com.genersoft.iot.vmp.gb28181.service.impl;

import com.genersoft.iot.vmp.common.enums.ChannelDataType;
import com.genersoft.iot.vmp.gb28181.dao.DeviceMapper;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DeviceServiceImplTest {

    @AfterEach
    void clearPageHelper() {
        PageHelper.clearPage();
    }

    @Test
    void getAllPassesEscapedLikePatternToDeviceMapper() {
        AtomicReference<Object[]> capturedArgs = new AtomicReference<>();
        DeviceMapper deviceMapper = (DeviceMapper) Proxy.newProxyInstance(
                DeviceMapper.class.getClassLoader(),
                new Class<?>[]{DeviceMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.getName();
                    }
                    if ("getDeviceList".equals(method.getName())) {
                        capturedArgs.set(args);
                        return Collections.emptyList();
                    }
                    throw new AssertionError("Unexpected mapper method: " + method.getName());
                });
        DeviceServiceImpl service = new DeviceServiceImpl();
        ReflectionTestUtils.setField(service, "deviceMapper", deviceMapper);

        service.getAll(1, 20, "abc/%_", null);

        assertArrayEquals(new Object[]{ChannelDataType.GB28181, "%abc///%/_%", null}, capturedArgs.get());
    }
}
