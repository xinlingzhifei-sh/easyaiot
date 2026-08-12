package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService.EventRecheckStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionTaskRecheckServiceTest {

    @Test
    void approveSubmittedTaskMarksTaskApprovedAndEventRechecked() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        CapturingEventRecheckStore eventRecheckStore = new CapturingEventRecheckStore();
        SupervisionTaskRecheckService service = new SupervisionTaskRecheckService(
                mapperHandler.createProxy(),
                eventRecheckStore
        );

        boolean approved = service.approveSubmittedTask(2001L);

        assertTrue(approved);
        assertEquals(List.of(2001L), mapperHandler.selectedTaskIds());
        assertEquals(List.of(2001L), mapperHandler.approvedTaskIds());
        assertEquals(List.of(1001L), eventRecheckStore.recheckedEventIds());
    }

    @Test
    void approveSubmittedTaskReturnsFalseWhenTaskIsNotSubmitted() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(0);
        CapturingEventRecheckStore eventRecheckStore = new CapturingEventRecheckStore();
        SupervisionTaskRecheckService service = new SupervisionTaskRecheckService(
                mapperHandler.createProxy(),
                eventRecheckStore
        );

        boolean approved = service.approveSubmittedTask(2001L);

        assertFalse(approved);
        assertEquals(List.of(2001L), mapperHandler.approvedTaskIds());
        assertEquals(List.of(), eventRecheckStore.recheckedEventIds());
    }

    @Test
    void rejectSubmittedTaskReturnsFalseWhenTaskIsNotSubmitted() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(0);
        CapturingEventRecheckStore eventRecheckStore = new CapturingEventRecheckStore();
        SupervisionTaskRecheckService service = new SupervisionTaskRecheckService(
                mapperHandler.createProxy(),
                eventRecheckStore
        );

        boolean rejected = service.rejectSubmittedTask(2001L);

        assertFalse(rejected);
        assertEquals(List.of(2001L), mapperHandler.rejectedTaskIds());
        assertEquals(List.of(), eventRecheckStore.reworkEventIds());
    }

    @Test
    void rejectSubmittedTaskMarksTaskRejectedAndEventReworkRequired() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        CapturingEventRecheckStore eventRecheckStore = new CapturingEventRecheckStore();
        SupervisionTaskRecheckService service = new SupervisionTaskRecheckService(
                mapperHandler.createProxy(),
                eventRecheckStore
        );

        boolean rejected = service.rejectSubmittedTask(2001L);

        assertTrue(rejected);
        assertEquals(List.of(2001L), mapperHandler.selectedTaskIds());
        assertEquals(List.of(2001L), mapperHandler.rejectedTaskIds());
        assertEquals(List.of(1001L), eventRecheckStore.reworkEventIds());
    }

    @Test
    void approveAndRejectSubmittedTaskReturnFalseWhenTaskDoesNotExist() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1, false);
        CapturingEventRecheckStore eventRecheckStore = new CapturingEventRecheckStore();
        SupervisionTaskRecheckService service = new SupervisionTaskRecheckService(
                mapperHandler.createProxy(),
                eventRecheckStore
        );

        boolean approved = service.approveSubmittedTask(2001L);
        boolean rejected = service.rejectSubmittedTask(2002L);

        assertFalse(approved);
        assertFalse(rejected);
        assertEquals(List.of(2001L, 2002L), mapperHandler.selectedTaskIds());
        assertEquals(List.of(), mapperHandler.approvedTaskIds());
        assertEquals(List.of(), mapperHandler.rejectedTaskIds());
        assertEquals(List.of(), eventRecheckStore.recheckedEventIds());
        assertEquals(List.of(), eventRecheckStore.reworkEventIds());
    }

    @Test
    void recheckRequiresTaskId() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        SupervisionTaskRecheckService service = new SupervisionTaskRecheckService(
                mapperHandler.createProxy(),
                new CapturingEventRecheckStore()
        );

        assertThrows(NullPointerException.class, () -> service.approveSubmittedTask(null));
        assertThrows(NullPointerException.class, () -> service.rejectSubmittedTask(null));
    }

    private static final class CapturingTaskMapperHandler implements InvocationHandler {

        private final int updateResult;
        private final boolean taskExists;
        private final List<Long> selectedTaskIds = new ArrayList<>();
        private final List<Long> approvedTaskIds = new ArrayList<>();
        private final List<Long> rejectedTaskIds = new ArrayList<>();

        private CapturingTaskMapperHandler(int updateResult) {
            this(updateResult, true);
        }

        private CapturingTaskMapperHandler(int updateResult, boolean taskExists) {
            this.updateResult = updateResult;
            this.taskExists = taskExists;
        }

        private SupervisionTaskMapper createProxy() {
            return (SupervisionTaskMapper) Proxy.newProxyInstance(
                    SupervisionTaskMapper.class.getClassLoader(),
                    new Class[]{SupervisionTaskMapper.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("selectById".equals(method.getName()) && args != null && args.length == 1) {
                selectedTaskIds.add((Long) args[0]);
                if (!taskExists) {
                    return null;
                }
                return new SupervisionTaskDO()
                        .setId((Long) args[0])
                        .setEventId(1001L);
            }
            if ("updateStatusToApproved".equals(method.getName()) && args != null && args.length == 1) {
                approvedTaskIds.add((Long) args[0]);
                return updateResult;
            }
            if ("updateStatusToRejected".equals(method.getName()) && args != null && args.length == 1) {
                rejectedTaskIds.add((Long) args[0]);
                return updateResult;
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private List<Long> selectedTaskIds() {
            return selectedTaskIds;
        }

        private List<Long> approvedTaskIds() {
            return approvedTaskIds;
        }

        private List<Long> rejectedTaskIds() {
            return rejectedTaskIds;
        }

    }

    private static final class CapturingEventRecheckStore implements EventRecheckStore {

        private final List<Long> recheckedEventIds = new ArrayList<>();
        private final List<Long> reworkEventIds = new ArrayList<>();

        @Override
        public void markRechecked(Long eventId) {
            recheckedEventIds.add(eventId);
        }

        @Override
        public void markReworkRequired(Long eventId) {
            reworkEventIds.add(eventId);
        }

        private List<Long> recheckedEventIds() {
            return recheckedEventIds;
        }

        private List<Long> reworkEventIds() {
            return reworkEventIds;
        }

    }

}
