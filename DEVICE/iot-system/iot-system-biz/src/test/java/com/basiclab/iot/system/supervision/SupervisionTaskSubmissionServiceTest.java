package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService.EventHandlingStore;
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

class SupervisionTaskSubmissionServiceTest {

    @Test
    void submitTaskMarksAcknowledgedTaskSubmittedWithResultAndNoteAndEventHandled() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        CapturingEventHandlingStore eventHandlingStore = new CapturingEventHandlingStore();
        SupervisionTaskSubmissionService service = new SupervisionTaskSubmissionService(
                mapperHandler.createProxy(),
                eventHandlingStore
        );

        boolean submitted = service.submitTask(2001L, "normal", "handled on site");

        assertTrue(submitted);
        assertEquals(List.of(2001L), mapperHandler.selectedTaskIds());
        assertEquals(1, mapperHandler.submitCommands().size());
        SubmitCommand command = mapperHandler.submitCommands().get(0);
        assertEquals(2001L, command.taskId());
        assertEquals("normal", command.resultCategory());
        assertEquals("handled on site", command.handlingNote());
        assertNotNull(command.submittedAt());
        assertEquals(List.of(1001L), eventHandlingStore.handledEventIds());
    }

    @Test
    void submitTaskReturnsFalseWhenTaskIsNotAcknowledged() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(0);
        CapturingEventHandlingStore eventHandlingStore = new CapturingEventHandlingStore();
        SupervisionTaskSubmissionService service = new SupervisionTaskSubmissionService(
                mapperHandler.createProxy(),
                eventHandlingStore
        );

        boolean submitted = service.submitTask(2001L, "normal", "handled on site");

        assertFalse(submitted);
        assertEquals(1, mapperHandler.submitCommands().size());
        assertEquals(List.of(), eventHandlingStore.handledEventIds());
    }

    @Test
    void submitTaskReturnsFalseWhenTaskDoesNotExist() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1, false);
        CapturingEventHandlingStore eventHandlingStore = new CapturingEventHandlingStore();
        SupervisionTaskSubmissionService service = new SupervisionTaskSubmissionService(
                mapperHandler.createProxy(),
                eventHandlingStore
        );

        boolean submitted = service.submitTask(2001L, "normal", "handled on site");

        assertFalse(submitted);
        assertEquals(List.of(2001L), mapperHandler.selectedTaskIds());
        assertEquals(List.of(), mapperHandler.submitCommands());
        assertEquals(List.of(), eventHandlingStore.handledEventIds());
    }

    @Test
    void submitTaskRequiresTaskIdResultCategoryAndHandlingNote() {
        CapturingTaskMapperHandler mapperHandler = new CapturingTaskMapperHandler(1);
        SupervisionTaskSubmissionService service = new SupervisionTaskSubmissionService(
                mapperHandler.createProxy(),
                new CapturingEventHandlingStore()
        );

        assertThrows(NullPointerException.class, () -> service.submitTask(null, "normal", "handled on site"));
        assertThrows(NullPointerException.class, () -> service.submitTask(2001L, null, "handled on site"));
        assertThrows(NullPointerException.class, () -> service.submitTask(2001L, "normal", null));
    }

    private record SubmitCommand(Long taskId, String resultCategory, String handlingNote, LocalDateTime submittedAt) {
    }

    private static final class CapturingTaskMapperHandler implements InvocationHandler {

        private final int updateResult;
        private final boolean taskExists;
        private final List<SubmitCommand> submitCommands = new ArrayList<>();
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
            if ("updateStatusToSubmitted".equals(method.getName()) && args != null && args.length == 4) {
                submitCommands.add(new SubmitCommand(
                        (Long) args[0],
                        (String) args[1],
                        (String) args[2],
                        (LocalDateTime) args[3]
                ));
                return updateResult;
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private List<SubmitCommand> submitCommands() {
            return submitCommands;
        }

        private List<Long> selectedTaskIds() {
            return selectedTaskIds;
        }

    }

    private static final class CapturingEventHandlingStore implements EventHandlingStore {

        private final List<Long> handledEventIds = new ArrayList<>();

        @Override
        public void markHandled(Long eventId) {
            handledEventIds.add(eventId);
        }

        private List<Long> handledEventIds() {
            return handledEventIds;
        }

    }

}
