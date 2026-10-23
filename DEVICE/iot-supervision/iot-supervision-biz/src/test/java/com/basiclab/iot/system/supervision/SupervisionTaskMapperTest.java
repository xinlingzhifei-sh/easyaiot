package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionTaskMapperTest {

    @Test
    void updateStatusToAcknowledgedUpdatesOnlySentTaskById() {
        initTableInfo();
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 6, 10, 11, 45);
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionTaskMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToAcknowledged(2001L, 3001L, acceptedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode(), handler.updateObject().getTaskStatus());
        assertEquals(3001L, handler.updateObject().getAssignedUserId());
        assertEquals(acceptedAt, handler.updateObject().getAcceptedAt());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionTaskDO> queryWrapper = (LambdaQueryWrapperX<SupervisionTaskDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("task_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(2001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionTaskStatusEnum.SENT.getCode()));
    }

    @Test
    void updateStatusToSubmittedUpdatesOnlyAcknowledgedTaskById() {
        initTableInfo();
        LocalDateTime submittedAt = LocalDateTime.of(2026, 6, 10, 13, 30);
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionTaskMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToSubmitted(2001L, "normal", "现场处置完成", submittedAt);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionTaskStatusEnum.SUBMITTED.getCode(), handler.updateObject().getTaskStatus());
        assertEquals(submittedAt, handler.updateObject().getSubmittedAt());
        assertEquals("现场处置完成", handler.updateObject().getHandlingNote());
        assertEquals("normal", handler.updateObject().getResultCategory());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionTaskDO> queryWrapper = (LambdaQueryWrapperX<SupervisionTaskDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("task_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(2001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode()));
    }

    @Test
    void updateStatusToApprovedUpdatesOnlySubmittedTaskById() {
        initTableInfo();
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionTaskMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToApproved(2001L);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionTaskStatusEnum.APPROVED.getCode(), handler.updateObject().getTaskStatus());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionTaskDO> queryWrapper = (LambdaQueryWrapperX<SupervisionTaskDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("task_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(2001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionTaskStatusEnum.SUBMITTED.getCode()));
    }

    @Test
    void updateStatusToRejectedUpdatesOnlySubmittedTaskById() {
        initTableInfo();
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionTaskMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToRejected(2001L);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionTaskStatusEnum.REJECTED.getCode(), handler.updateObject().getTaskStatus());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionTaskDO> queryWrapper = (LambdaQueryWrapperX<SupervisionTaskDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("task_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(2001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionTaskStatusEnum.SUBMITTED.getCode()));
    }

    @Test
    void updateStatusToAcknowledgedForReworkUpdatesOnlyRejectedOrApprovedTaskById() {
        initTableInfo();
        LocalDateTime acceptedAt = LocalDateTime.of(2026, 6, 10, 17, 25);
        CapturingMapperHandler handler = new CapturingMapperHandler();
        SupervisionTaskMapper mapper = handler.createProxy();

        int updated = mapper.updateStatusToAcknowledgedForRework(2001L, 3001L, acceptedAt, 2);

        assertEquals(1, updated);
        assertNotNull(handler.updateObject());
        assertEquals(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode(), handler.updateObject().getTaskStatus());
        assertEquals(3001L, handler.updateObject().getAssignedUserId());
        assertEquals(acceptedAt, handler.updateObject().getAcceptedAt());
        assertEquals(2, handler.updateObject().getReworkCount());
        assertTrue(handler.queryWrapper() instanceof LambdaQueryWrapperX);
        LambdaQueryWrapperX<SupervisionTaskDO> queryWrapper = (LambdaQueryWrapperX<SupervisionTaskDO>) handler.queryWrapper();
        String sqlSegment = queryWrapper.getSqlSegment();
        assertTrue(sqlSegment.contains("id"));
        assertTrue(sqlSegment.contains("task_status"));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(2001L));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionTaskStatusEnum.REJECTED.getCode()));
        assertTrue(queryWrapper.getParamNameValuePairs().containsValue(SupervisionTaskStatusEnum.APPROVED.getCode()));
    }

    private static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(SupervisionTaskDO.class) != null) {
            return;
        }
        MapperBuilderAssistant builderAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(builderAssistant, SupervisionTaskDO.class);
    }

    private static final class CapturingMapperHandler implements InvocationHandler {

        private SupervisionTaskDO updateObject;
        private Wrapper<SupervisionTaskDO> queryWrapper;

        private SupervisionTaskMapper createProxy() {
            return (SupervisionTaskMapper) Proxy.newProxyInstance(
                    SupervisionTaskMapper.class.getClassLoader(),
                    new Class[]{SupervisionTaskMapper.class},
                    this
            );
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("update".equals(method.getName()) && args != null && args.length == 2
                    && args[0] instanceof SupervisionTaskDO taskDO && args[1] instanceof Wrapper) {
                updateObject = taskDO;
                queryWrapper = (Wrapper<SupervisionTaskDO>) args[1];
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

        private SupervisionTaskDO updateObject() {
            return updateObject;
        }

        private Wrapper<SupervisionTaskDO> queryWrapper() {
            return queryWrapper;
        }

    }

}
