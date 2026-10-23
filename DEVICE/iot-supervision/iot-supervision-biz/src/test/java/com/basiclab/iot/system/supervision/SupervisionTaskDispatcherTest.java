package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatchCommand;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import com.basiclab.iot.system.service.supervision.SupervisionTaskDispatcher;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupervisionTaskDispatcherTest {

    @Test
    void dispatchForNewEventCreatesSentHandleTasksForResponsibilityChain() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler();
        SupervisionTaskDispatcher dispatcher = new SupervisionTaskDispatcher(mapperHandler.createProxy());
        TaskDispatchCommand command = new TaskDispatchCommand(
                1001L,
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                "health",
                SupervisionEventLevelEnum.L4,
                List.of("area_police", "medical", "duty_leader")
        );

        dispatcher.dispatchForNewEvent(command);

        assertEquals(3, mapperHandler.insertedTasks().size());
        assertInsertedTask(mapperHandler.insertedTasks().get(0), "area_police");
        assertInsertedTask(mapperHandler.insertedTasks().get(1), "medical");
        assertInsertedTask(mapperHandler.insertedTasks().get(2), "duty_leader");
    }

    private static void assertInsertedTask(SupervisionTaskDO task, String assignedRole) {
        assertNotNull(task.getTaskNo());
        assertEquals(1001L, task.getEventId());
        assertEquals(SupervisionTaskDispatcher.TASK_TYPE_HANDLE, task.getTaskType());
        assertEquals(SupervisionTaskStatusEnum.SENT.getCode(), task.getTaskStatus());
        assertEquals(assignedRole, task.getAssignedRole());
        assertEquals(0, task.getReworkCount());
    }

    private static final class CapturingTaskMapperHandler implements InvocationHandler {

        private long nextTaskId = 2000L;
        private final List<SupervisionTaskDO> insertedTasks = new ArrayList<>();

        private SupervisionTaskMapper createProxy() {
            return (SupervisionTaskMapper) Proxy.newProxyInstance(
                    SupervisionTaskMapper.class.getClassLoader(),
                    new Class[]{SupervisionTaskMapper.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("insert".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof SupervisionTaskDO taskDO) {
                taskDO.setId(++nextTaskId);
                insertedTasks.add(taskDO);
                return 1;
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private List<SupervisionTaskDO> insertedTasks() {
            return insertedTasks;
        }

    }

}
