package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEventMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionCloseResultEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionEventMapperTest {

    @Test
    void selectOpenBySourceAlertFindsUnclosedEventBySourceSystemAndAlertId() {
        initTableInfo();
        SupervisionEventDO existingEvent = new SupervisionEventDO().setId(1001L);
        CapturingMapperHandler handler = new CapturingMapperHandler(existingEvent);
        SupervisionEventMapper mapper = handler.createProxy();

        SupervisionEventDO result = mapper.selectOpenBySourceAlert("video", "alert-001");

        assertSame(existingEvent, result);
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("source_system"));
        assertTrue(sqlSegment.contains("#{ew.paramNameValuePairs.MPGENVAL1}"));
        assertTrue(sqlSegment.contains("source_alert_id"));
        assertTrue(sqlSegment.contains("#{ew.paramNameValuePairs.MPGENVAL2}"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(sqlSegment.contains("<>"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("video"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue("alert-001"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.CLOSED.getCode()));
    }

    @Test
    void updateStatusToDispatchedUpdatesCreatedEventById() {
        initTableInfo();
        LocalDateTime dispatchedAt = LocalDateTime.of(2026, 6, 10, 11, 20);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToDispatched(1001L, dispatchedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), handler.updateObject().getEventStatus());
        assertEquals(dispatchedAt, handler.updateObject().getDispatchedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.CREATED.getCode()));
    }

    @Test
    void updateStatusToAcceptedUpdatesDispatchedEventById() {
        initTableInfo();
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 6, 10, 12, 10);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToAccepted(1001L, acceptedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.ACCEPTED.getCode(), handler.updateObject().getEventStatus());
        assertEquals(acceptedAt, handler.updateObject().getAcceptedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.DISPATCHED.getCode()));
    }

    @Test
    void updateStatusToPendingRecheckUpdatesAcceptedEventById() {
        initTableInfo();
        LocalDateTime handledAt = LocalDateTime.of(2026, 6, 10, 14, 5);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToPendingRecheck(1001L, handledAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.PENDING_RECHECK.getCode(), handler.updateObject().getEventStatus());
        assertEquals(handledAt, handler.updateObject().getHandledAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.ACCEPTED.getCode()));
    }

    @Test
    void updateStatusToPendingCloseCheckUpdatesPendingRecheckEventById() {
        initTableInfo();
        LocalDateTime recheckedAt = LocalDateTime.of(2026, 6, 10, 15, 20);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToPendingCloseCheck(1001L, recheckedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode(), handler.updateObject().getEventStatus());
        assertEquals(recheckedAt, handler.updateObject().getRecheckedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.PENDING_RECHECK.getCode()));
    }

    @Test
    void updateStatusToReworkRequiredUpdatesPendingRecheckEventById() {
        initTableInfo();
        LocalDateTime recheckedAt = LocalDateTime.of(2026, 6, 10, 15, 45);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToReworkRequired(1001L, recheckedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode(), handler.updateObject().getEventStatus());
        assertEquals(recheckedAt, handler.updateObject().getRecheckedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.PENDING_RECHECK.getCode()));
    }

    @Test
    void updateStatusToClosedUpdatesPendingCloseCheckEventById() {
        initTableInfo();
        LocalDateTime closedAt = LocalDateTime.of(2026, 6, 10, 16, 30);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToClosed(
                1001L,
                SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode(),
                closedAt
        );

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.CLOSED.getCode(), handler.updateObject().getEventStatus());
        assertEquals(SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode(), handler.updateObject().getCloseResult());
        assertEquals(closedAt, handler.updateObject().getClosedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode()));
    }

    @Test
    void updateStatusToCloseCheckReworkRequiredUpdatesPendingCloseCheckEventById() {
        initTableInfo();
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToCloseCheckReworkRequired(1001L);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode(), handler.updateObject().getEventStatus());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode()));
    }

    @Test
    void updateStatusToAcceptedFromReworkUpdatesOnlyReworkRequiredEventById() {
        initTableInfo();
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 6, 10, 17, 40);
        CapturingMapperHandler handler = new CapturingMapperHandler(null);
        SupervisionEventMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToAcceptedFromRework(1001L, acceptedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionEventStatusEnum.ACCEPTED.getCode(), handler.updateObject().getEventStatus());
        assertEquals(acceptedAt, handler.updateObject().getAcceptedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionEventDO> queryWrapper = (LambdaQueryWrapperX<SupervisionEventDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("event_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(1001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode()));
    }

    private static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SupervisionEventDO.class) != null) {
            return;
        }
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, SupervisionEventDO.class);
    }

    private static final class CapturingMapperHandler implements InvocationHandler {

        private final SupervisionEventDO result;
        private Wrapper<SupervisionEventDO> queryWrapper;
        private SupervisionEventDO updateObject;

        private CapturingMapperHandler(SupervisionEventDO result) {
            this.result = result;
        }

        private SupervisionEventMapper createProxy() {
            return (SupervisionEventMapper) Proxy.newProxyInstance(
                    SupervisionEventMapper.class.getClassLoader(),
                    new Class[]{SupervisionEventMapper.class},
                    this
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("selectOne".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Wrapper) {
                queryWrapper = (Wrapper<SupervisionEventDO>) args[0];
                return result;
            }
            if ("update".equals(method.getName()) && args != null && args.length == 2
                    && args[0] instanceof SupervisionEventDO eventDO && args[1] instanceof Wrapper) {
                updateObject = eventDO;
                queryWrapper = (Wrapper<SupervisionEventDO>) args[1];
                return 1;
            }
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private Wrapper<SupervisionEventDO> queryWrapper() {
            return queryWrapper;
        }

        private SupervisionEventDO updateObject() {
            return updateObject;
        }

    }

}
