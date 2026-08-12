package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService.EventAcceptanceStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionTaskAcceptanceServiceTest {

    @Test
    void acceptTaskMarksSentTaskAcknowledgedAndEventAcceptedWithAcceptedUser() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        CapturingEventAcceptanceStore eventAcceptanceStore = new CapturingEventAcceptanceStore();
        SupervisionTaskAcceptanceService service = new SupervisionTaskAcceptanceService(
                mapperHandler.createProxy(),
                eventAcceptanceStore
        );

        boolean accepted = service.acceptTask(2001L, 3001L);

        assertTrue(accepted);
        assertEquals(List.of(2001L), mapperHandler.selectedTaskIds());
        assertEquals(1, mapperHandler.acceptCommands().size());
        AcceptCommand command = mapperHandler.acceptCommands().get(0);
        assertEquals(2001L, command.taskId());
        assertEquals(3001L, command.acceptedUserId());
        assertNotNull(command.acceptedAt());
        assertEquals(List.of(1001L), eventAcceptanceStore.acceptedEventIds());
    }

    @Test
    void acceptTaskReturnsFalseWhenTaskIsNotSent() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(0);
        CapturingEventAcceptanceStore eventAcceptanceStore = new CapturingEventAcceptanceStore();
        SupervisionTaskAcceptanceService service = new SupervisionTaskAcceptanceService(
                mapperHandler.createProxy(),
                eventAcceptanceStore
        );

        boolean accepted = service.acceptTask(2001L, 3001L);

        assertFalse(accepted);
        assertEquals(1, mapperHandler.acceptCommands().size());
        assertEquals(List.of(), eventAcceptanceStore.acceptedEventIds());
    }

    @Test
    void acceptTaskReturnsFalseWhenTaskDoesNotExist() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1, false);
        CapturingEventAcceptanceStore eventAcceptanceStore = new CapturingEventAcceptanceStore();
        SupervisionTaskAcceptanceService service = new SupervisionTaskAcceptanceService(
                mapperHandler.createProxy(),
                eventAcceptanceStore
        );

        boolean accepted = service.acceptTask(2001L, 3001L);

        assertFalse(accepted);
        assertEquals(List.of(2001L), mapperHandler.selectedTaskIds());
        assertEquals(List.of(), mapperHandler.acceptCommands());
        assertEquals(List.of(), eventAcceptanceStore.acceptedEventIds());
    }

    @Test
    void acceptTaskRequiresTaskIdAndAcceptedUser() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        SupervisionTaskAcceptanceService service = new SupervisionTaskAcceptanceService(
                mapperHandler.createProxy(),
                new CapturingEventAcceptanceStore()
        );

        assertThrows(NullPointerException.class, () -> service.acceptTask(null, 3001L));
        assertThrows(NullPointerException.class, () -> service.acceptTask(2001L, null));
    }

    private record AcceptCommand(Long taskId, Long acceptedUserId, LocalDateTime acceptedAt) {
    }

    private static final class CapturingTaskMapperHandler implements InvocationHandler {

        private final int updateResult;
        private final boolean taskExists;
        private final List<AcceptCommand> acceptCommands = new ArrayList<>();
        private final List<Long> selectedTaskIds = new ArrayList<>();

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
            if ("updateStatusToAcknowledged".equals(method.getName()) && args != null && args.length == 3) {
                acceptCommands.add(new AcceptCommand((Long) args[0], (Long) args[1], (LocalDateTime) args[2]));
                return updateResult;
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private List<AcceptCommand> acceptCommands() {
            return acceptCommands;
        }

        private List<Long> selectedTaskIds() {
            return selectedTaskIds;
        }

    }

    private static final class CapturingEventAcceptanceStore implements EventAcceptanceStore {

        private final List<Long> acceptedEventIds = new ArrayList<>();

        @Override
        public void markAccepted(Long eventId) {
            acceptedEventIds.add(eventId);
        }

        private List<Long> acceptedEventIds() {
            return acceptedEventIds;
        }

    }

}
