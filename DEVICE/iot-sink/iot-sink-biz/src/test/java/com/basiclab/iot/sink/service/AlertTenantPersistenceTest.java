package com.basiclab.iot.sink.service;

import com.basiclab.iot.sink.dal.dataobject.AlertDO;
import com.basiclab.iot.sink.dal.mapper.AlertMapper;
import com.basiclab.iot.sink.domain.model.AlertNotificationMessage;
import com.basiclab.iot.sink.service.impl.AlertServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AlertTenantPersistenceTest {

    @Test
    void persistsOwningTenantFromMessage() throws Exception {
        AlertMapper mapper = mock(AlertMapper.class);
        doAnswer(invocation -> {
            AlertDO alert = invocation.getArgument(0);
            alert.setId(101);
            return 1;
        }).when(mapper).insert(any(AlertDO.class));
        AlertServiceImpl service = serviceWithMapper(mapper);

        Integer alertId = service.processAlert(message(7L));

        assertEquals(101, alertId);
        verify(mapper).insert(org.mockito.ArgumentMatchers.argThat(
                alert -> Long.valueOf(7L).equals(alert.getTenantId())));
    }

    @Test
    void missingTenantFailsClosedBeforeInsert() throws Exception {
        AlertMapper mapper = mock(AlertMapper.class);
        AlertServiceImpl service = serviceWithMapper(mapper);

        assertNull(service.processAlert(message(null)));

        verify(mapper, never()).insert(any(AlertDO.class));
    }

    private static AlertServiceImpl serviceWithMapper(AlertMapper mapper) throws Exception {
        AlertServiceImpl service = new AlertServiceImpl();
        Field field = AlertServiceImpl.class.getDeclaredField("alertMapper");
        field.setAccessible(true);
        field.set(service, mapper);
        return service;
    }

    private static AlertNotificationMessage message(Long tenantId) {
        AlertNotificationMessage message = new AlertNotificationMessage();
        message.setTenantId(tenantId);
        message.setDeviceId("camera-01");
        message.setDeviceName("Camera 01");
        AlertNotificationMessage.AlertInfo alert = new AlertNotificationMessage.AlertInfo();
        alert.setObject("person");
        alert.setEvent("detection");
        alert.setTaskType("realtime");
        message.setAlert(alert);
        return message;
    }
}
