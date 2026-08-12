package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionCloseResultEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService.EventCloseStore;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventCreateDraft;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventStore;
import com.basiclab.iot.system.service.supervision.SupervisionEventServiceImpl;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService.EventAcceptanceStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskDispatcher;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService.EventRecheckStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskReworkService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskReworkService.EventReworkStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService.EventHandlingStore;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionClosureHappyPathAcceptanceTest {

    @Test
    void alertEventTaskLifecycleClosesThroughHappyPath() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        InMemoryTaskMapper taskMapper = new InMemoryTaskMapper();
        SupervisionTaskMapper taskMapperProxy = taskMapper.createProxy();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                eventStore,
                new SupervisionTaskDispatcher(taskMapperProxy)
        );
        SupervisionTaskAcceptanceService acceptanceService = new SupervisionTaskAcceptanceService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskSubmissionService submissionService = new SupervisionTaskSubmissionService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskRecheckService recheckService = new SupervisionTaskRecheckService(
                taskMapperProxy,
                eventStore
        );
        SupervisionEventCloseCheckService closeCheckService = new SupervisionEventCloseCheckService(eventStore);

        AlertToEventResult result = eventService.createFromAlert(new AlertToEventCommand(
                "video",
                "alert-closure-001",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 10, 18, 30),
                "payload-hash-closure-001"
        ));
        Long taskId = taskMapper.onlyTaskId();

        boolean accepted = acceptanceService.acceptTask(taskId, 3001L);
        boolean submitted = submissionService.submitTask(taskId, "normal", "handled on site");
        boolean rechecked = recheckService.approveSubmittedTask(taskId);
        boolean closed = closeCheckService.approveCloseCheck(result.eventId());

        assertFalse(result.reused());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), result.eventStatus());
        assertTrue(accepted);
        assertTrue(submitted);
        assertTrue(rechecked);
        assertTrue(closed);

        SupervisionEventDO event = eventStore.event(result.eventId());
        assertEquals(SupervisionEventStatusEnum.CLOSED.getCode(), event.getEventStatus());
        assertEquals(SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode(), event.getCloseResult());
        assertNotNull(event.getDispatchedAt());
        assertNotNull(event.getAcceptedAt());
        assertNotNull(event.getHandledAt());
        assertNotNull(event.getRecheckedAt());
        assertNotNull(event.getClosedAt());

        SupervisionTaskDO task = taskMapper.task(taskId);
        assertEquals(result.eventId(), task.getEventId());
        assertEquals(SupervisionTaskStatusEnum.APPROVED.getCode(), task.getTaskStatus());
        assertEquals(3001L, task.getAssignedUserId());
        assertEquals("normal", task.getResultCategory());
        assertEquals("handled on site", task.getHandlingNote());
        assertNotNull(task.getAcceptedAt());
        assertNotNull(task.getSubmittedAt());
    }

    @Test
    void recheckReworkRestartsExistingTaskAndReturnsEventToPendingRecheck() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        InMemoryTaskMapper taskMapper = new InMemoryTaskMapper();
        SupervisionTaskMapper taskMapperProxy = taskMapper.createProxy();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                eventStore,
                new SupervisionTaskDispatcher(taskMapperProxy)
        );
        SupervisionTaskAcceptanceService acceptanceService = new SupervisionTaskAcceptanceService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskSubmissionService submissionService = new SupervisionTaskSubmissionService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskRecheckService recheckService = new SupervisionTaskRecheckService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskReworkService reworkService = new SupervisionTaskReworkService(
                taskMapperProxy,
                eventStore
        );

        AlertToEventResult result = eventService.createFromAlert(new AlertToEventCommand(
                "video",
                "alert-recheck-rework-001",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 10, 19, 10),
                "payload-hash-recheck-rework-001"
        ));
        Long taskId = taskMapper.onlyTaskId();

        assertTrue(acceptanceService.acceptTask(taskId, 3001L));
        assertTrue(submissionService.submitTask(taskId, "abnormal", "needs rework"));
        assertTrue(recheckService.rejectSubmittedTask(taskId));
        assertTrue(reworkService.restartReworkTask(taskId, 3002L));
        assertTrue(submissionService.submitTask(taskId, "normal", "rework handled"));

        SupervisionEventDO event = eventStore.event(result.eventId());
        assertEquals(SupervisionEventStatusEnum.PENDING_RECHECK.getCode(), event.getEventStatus());
        assertNotNull(event.getAcceptedAt());
        assertNotNull(event.getHandledAt());

        SupervisionTaskDO task = taskMapper.task(taskId);
        assertEquals(SupervisionTaskStatusEnum.SUBMITTED.getCode(), task.getTaskStatus());
        assertEquals(3002L, task.getAssignedUserId());
        assertEquals(1, task.getReworkCount());
        assertEquals("normal", task.getResultCategory());
        assertEquals("rework handled", task.getHandlingNote());
    }

    @Test
    void closeCheckReworkRestartsApprovedTaskAndReturnsEventToPendingRecheck() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        InMemoryTaskMapper taskMapper = new InMemoryTaskMapper();
        SupervisionTaskMapper taskMapperProxy = taskMapper.createProxy();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                eventStore,
                new SupervisionTaskDispatcher(taskMapperProxy)
        );
        SupervisionTaskAcceptanceService acceptanceService = new SupervisionTaskAcceptanceService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskSubmissionService submissionService = new SupervisionTaskSubmissionService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskRecheckService recheckService = new SupervisionTaskRecheckService(
                taskMapperProxy,
                eventStore
        );
        SupervisionEventCloseCheckService closeCheckService = new SupervisionEventCloseCheckService(eventStore);
        SupervisionTaskReworkService reworkService = new SupervisionTaskReworkService(
                taskMapperProxy,
                eventStore
        );

        AlertToEventResult result = eventService.createFromAlert(new AlertToEventCommand(
                "video",
                "alert-close-check-rework-001",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 10, 19, 45),
                "payload-hash-close-check-rework-001"
        ));
        Long taskId = taskMapper.onlyTaskId();

        assertTrue(acceptanceService.acceptTask(taskId, 3001L));
        assertTrue(submissionService.submitTask(taskId, "normal", "handled before close check"));
        assertTrue(recheckService.approveSubmittedTask(taskId));
        assertTrue(closeCheckService.rejectCloseCheck(result.eventId()));
        assertTrue(reworkService.restartReworkTask(taskId, 3003L));
        assertTrue(submissionService.submitTask(taskId, "normal", "close-check rework handled"));

        SupervisionEventDO event = eventStore.event(result.eventId());
        assertEquals(SupervisionEventStatusEnum.PENDING_RECHECK.getCode(), event.getEventStatus());
        assertNotNull(event.getAcceptedAt());
        assertNotNull(event.getHandledAt());

        SupervisionTaskDO task = taskMapper.task(taskId);
        assertEquals(SupervisionTaskStatusEnum.SUBMITTED.getCode(), task.getTaskStatus());
        assertEquals(3003L, task.getAssignedUserId());
        assertEquals(1, task.getReworkCount());
        assertEquals("normal", task.getResultCategory());
        assertEquals("close-check rework handled", task.getHandlingNote());
    }

    @Test
    void repeatedReworkIncrementsCountAndRequiresSubmitAndRecheckBeforeClose() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        InMemoryTaskMapper taskMapper = new InMemoryTaskMapper();
        SupervisionTaskMapper taskMapperProxy = taskMapper.createProxy();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                eventStore,
                new SupervisionTaskDispatcher(taskMapperProxy)
        );
        SupervisionTaskAcceptanceService acceptanceService = new SupervisionTaskAcceptanceService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskSubmissionService submissionService = new SupervisionTaskSubmissionService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskRecheckService recheckService = new SupervisionTaskRecheckService(
                taskMapperProxy,
                eventStore
        );
        SupervisionEventCloseCheckService closeCheckService = new SupervisionEventCloseCheckService(eventStore);
        SupervisionTaskReworkService reworkService = new SupervisionTaskReworkService(
                taskMapperProxy,
                eventStore
        );

        AlertToEventResult result = eventService.createFromAlert(new AlertToEventCommand(
                "video",
                "alert-rework-count-001",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 15),
                "payload-hash-rework-count-001"
        ));
        Long taskId = taskMapper.onlyTaskId();

        assertTrue(acceptanceService.acceptTask(taskId, 3001L));
        assertTrue(submissionService.submitTask(taskId, "abnormal", "first handling"));
        assertTrue(recheckService.rejectSubmittedTask(taskId));
        assertFalse(closeCheckService.approveCloseCheck(result.eventId()));

        assertTrue(reworkService.restartReworkTask(taskId, 3002L));
        assertEquals(1, taskMapper.task(taskId).getReworkCount());
        assertFalse(closeCheckService.approveCloseCheck(result.eventId()));
        assertTrue(submissionService.submitTask(taskId, "normal", "first rework handling"));
        assertFalse(closeCheckService.approveCloseCheck(result.eventId()));

        assertTrue(recheckService.approveSubmittedTask(taskId));
        assertTrue(closeCheckService.rejectCloseCheck(result.eventId()));
        assertTrue(reworkService.restartReworkTask(taskId, 3003L));
        assertEquals(2, taskMapper.task(taskId).getReworkCount());
        assertFalse(closeCheckService.approveCloseCheck(result.eventId()));
        assertTrue(submissionService.submitTask(taskId, "normal", "second rework handling"));
        assertFalse(closeCheckService.approveCloseCheck(result.eventId()));

        assertTrue(recheckService.approveSubmittedTask(taskId));
        assertTrue(closeCheckService.approveCloseCheck(result.eventId()));

        SupervisionEventDO event = eventStore.event(result.eventId());
        assertEquals(SupervisionEventStatusEnum.CLOSED.getCode(), event.getEventStatus());
        assertEquals(SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode(), event.getCloseResult());

        SupervisionTaskDO task = taskMapper.task(taskId);
        assertEquals(SupervisionTaskStatusEnum.APPROVED.getCode(), task.getTaskStatus());
        assertEquals(3003L, task.getAssignedUserId());
        assertEquals(2, task.getReworkCount());
        assertEquals("second rework handling", task.getHandlingNote());
    }

    @Test
    void invalidServiceTransitionsReturnFalseWithoutChangingWorkflowState() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        InMemoryTaskMapper taskMapper = new InMemoryTaskMapper();
        SupervisionTaskMapper taskMapperProxy = taskMapper.createProxy();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                eventStore,
                new SupervisionTaskDispatcher(taskMapperProxy)
        );
        SupervisionTaskAcceptanceService acceptanceService = new SupervisionTaskAcceptanceService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskSubmissionService submissionService = new SupervisionTaskSubmissionService(
                taskMapperProxy,
                eventStore
        );
        SupervisionTaskRecheckService recheckService = new SupervisionTaskRecheckService(
                taskMapperProxy,
                eventStore
        );
        SupervisionEventCloseCheckService closeCheckService = new SupervisionEventCloseCheckService(eventStore);
        SupervisionTaskReworkService reworkService = new SupervisionTaskReworkService(
                taskMapperProxy,
                eventStore
        );

        AlertToEventResult result = eventService.createFromAlert(new AlertToEventCommand(
                "video",
                "alert-invalid-transition-001",
                SupervisionRuleSeeds.RULE_ABNORMAL_GATHERING,
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 10, 30),
                "payload-hash-invalid-transition-001"
        ));
        Long taskId = taskMapper.onlyTaskId();

        assertFalse(submissionService.submitTask(taskId, "normal", "too early submit"));
        assertWorkflowState(
                eventStore,
                taskMapper,
                result.eventId(),
                taskId,
                SupervisionEventStatusEnum.DISPATCHED,
                SupervisionTaskStatusEnum.SENT
        );

        assertTrue(acceptanceService.acceptTask(taskId, 3001L));
        assertFalse(recheckService.approveSubmittedTask(taskId));
        assertFalse(recheckService.rejectSubmittedTask(taskId));
        assertFalse(reworkService.restartReworkTask(taskId, 3002L));
        assertWorkflowState(
                eventStore,
                taskMapper,
                result.eventId(),
                taskId,
                SupervisionEventStatusEnum.ACCEPTED,
                SupervisionTaskStatusEnum.ACKNOWLEDGED
        );

        assertTrue(submissionService.submitTask(taskId, "normal", "ready for recheck"));
        assertFalse(closeCheckService.approveCloseCheck(result.eventId()));
        assertFalse(closeCheckService.rejectCloseCheck(result.eventId()));
        assertWorkflowState(
                eventStore,
                taskMapper,
                result.eventId(),
                taskId,
                SupervisionEventStatusEnum.PENDING_RECHECK,
                SupervisionTaskStatusEnum.SUBMITTED
        );
        assertNull(eventStore.event(result.eventId()).getCloseResult());
    }

    private static final class InMemoryEventStore implements EventStore, EventAcceptanceStore,
            EventHandlingStore, EventRecheckStore, EventCloseStore, EventReworkStore {

        private long nextEventId = 1000L;
        private final Map<Long, SupervisionEventDO> eventsById = new LinkedHashMap<>();
        private final Map<String, Long> eventIdsBySourceAlert = new LinkedHashMap<>();

        @Override
        public Optional<AlertToEventResult> findOpenBySourceAlert(String sourceSystem, String sourceAlertId) {
            Long eventId = eventIdsBySourceAlert.get(key(sourceSystem, sourceAlertId));
            if (eventId == null) {
                return Optional.empty();
            }
            SupervisionEventDO event = eventsById.get(eventId);
            if (SupervisionEventStatusEnum.CLOSED.getCode().equals(event.getEventStatus())) {
                return Optional.empty();
            }
            return Optional.of(toResult(event, true));
        }

        @Override
        public AlertToEventResult create(EventCreateDraft draft) {
            SupervisionEventDO event = new SupervisionEventDO()
                    .setId(++nextEventId)
                    .setEventNo("SE-" + nextEventId)
                    .setSourceSystem(draft.sourceSystem())
                    .setSourceAlertId(draft.sourceAlertId())
                    .setSourceAlertType(draft.sourceAlertType())
                    .setSourceAlertTime(draft.sourceAlertTime())
                    .setSourcePayloadHash(draft.sourcePayloadHash())
                    .setEventType(draft.eventType())
                    .setEventLevel(draft.eventLevel().getCode())
                    .setEventStatus(draft.eventStatus());
            eventsById.put(event.getId(), event);
            eventIdsBySourceAlert.put(key(draft.sourceSystem(), draft.sourceAlertId()), event.getId());
            return toResult(event, false);
        }

        @Override
        public void markDispatched(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (SupervisionEventStatusEnum.CREATED.getCode().equals(event.getEventStatus())) {
                event.setEventStatus(SupervisionEventStatusEnum.DISPATCHED.getCode())
                        .setDispatchedAt(LocalDateTime.now());
            }
        }

        @Override
        public void markAccepted(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (SupervisionEventStatusEnum.DISPATCHED.getCode().equals(event.getEventStatus())) {
                event.setEventStatus(SupervisionEventStatusEnum.ACCEPTED.getCode())
                        .setAcceptedAt(LocalDateTime.now());
            }
        }

        @Override
        public void markHandled(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (SupervisionEventStatusEnum.ACCEPTED.getCode().equals(event.getEventStatus())) {
                event.setEventStatus(SupervisionEventStatusEnum.PENDING_RECHECK.getCode())
                        .setHandledAt(LocalDateTime.now());
            }
        }

        @Override
        public void markRechecked(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (SupervisionEventStatusEnum.PENDING_RECHECK.getCode().equals(event.getEventStatus())) {
                event.setEventStatus(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode())
                        .setRecheckedAt(LocalDateTime.now());
            }
        }

        @Override
        public void markReworkRequired(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (SupervisionEventStatusEnum.PENDING_RECHECK.getCode().equals(event.getEventStatus())) {
                event.setEventStatus(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode())
                        .setRecheckedAt(LocalDateTime.now());
            }
        }

        @Override
        public boolean markClosed(Long eventId, String closeResult) {
            SupervisionEventDO event = event(eventId);
            if (!SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode().equals(event.getEventStatus())) {
                return false;
            }
            event.setEventStatus(SupervisionEventStatusEnum.CLOSED.getCode())
                    .setCloseResult(closeResult)
                    .setClosedAt(LocalDateTime.now());
            return true;
        }

        @Override
        public boolean markCloseCheckReworkRequired(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (!SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode().equals(event.getEventStatus())) {
                return false;
            }
            event.setEventStatus(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode());
            return true;
        }

        @Override
        public void markReworkAccepted(Long eventId) {
            SupervisionEventDO event = event(eventId);
            if (SupervisionEventStatusEnum.REWORK_REQUIRED.getCode().equals(event.getEventStatus())) {
                event.setEventStatus(SupervisionEventStatusEnum.ACCEPTED.getCode())
                        .setAcceptedAt(LocalDateTime.now());
            }
        }

        private SupervisionEventDO event(Long eventId) {
            SupervisionEventDO event = eventsById.get(eventId);
            if (event == null) {
                throw new IllegalArgumentException("Unknown event: " + eventId);
            }
            return event;
        }

        private AlertToEventResult toResult(SupervisionEventDO event, boolean reused) {
            return new AlertToEventResult(
                    event.getId(),
                    event.getSourceSystem(),
                    event.getSourceAlertId(),
                    null,
                    event.getEventType(),
                    SupervisionEventLevelEnum.valueOf(event.getEventLevel()),
                    event.getEventStatus(),
                    reused
            );
        }

        private String key(String sourceSystem, String sourceAlertId) {
            return sourceSystem + ":" + sourceAlertId;
        }

    }

    private static final class InMemoryTaskMapper implements InvocationHandler {

        private long nextTaskId = 2000L;
        private final Map<Long, SupervisionTaskDO> tasksById = new LinkedHashMap<>();

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
                    && args[0] instanceof SupervisionTaskDO task) {
                task.setId(++nextTaskId);
                tasksById.put(task.getId(), task);
                return 1;
            }
            if ("selectById".equals(method.getName()) && args != null && args.length == 1) {
                return tasksById.get((Long) args[0]);
            }
            if ("updateStatusToAcknowledged".equals(method.getName()) && args != null && args.length == 3) {
                SupervisionTaskDO task = task((Long) args[0]);
                if (!SupervisionTaskStatusEnum.SENT.getCode().equals(task.getTaskStatus())) {
                    return 0;
                }
                task.setTaskStatus(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode())
                        .setAssignedUserId((Long) args[1])
                        .setAcceptedAt((LocalDateTime) args[2]);
                return 1;
            }
            if ("updateStatusToSubmitted".equals(method.getName()) && args != null && args.length == 4) {
                SupervisionTaskDO task = task((Long) args[0]);
                if (!SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode().equals(task.getTaskStatus())) {
                    return 0;
                }
                task.setTaskStatus(SupervisionTaskStatusEnum.SUBMITTED.getCode())
                        .setResultCategory((String) args[1])
                        .setHandlingNote((String) args[2])
                        .setSubmittedAt((LocalDateTime) args[3]);
                return 1;
            }
            if ("updateStatusToApproved".equals(method.getName()) && args != null && args.length == 1) {
                SupervisionTaskDO task = task((Long) args[0]);
                if (!SupervisionTaskStatusEnum.SUBMITTED.getCode().equals(task.getTaskStatus())) {
                    return 0;
                }
                task.setTaskStatus(SupervisionTaskStatusEnum.APPROVED.getCode());
                return 1;
            }
            if ("updateStatusToRejected".equals(method.getName()) && args != null && args.length == 1) {
                SupervisionTaskDO task = task((Long) args[0]);
                if (!SupervisionTaskStatusEnum.SUBMITTED.getCode().equals(task.getTaskStatus())) {
                    return 0;
                }
                task.setTaskStatus(SupervisionTaskStatusEnum.REJECTED.getCode());
                return 1;
            }
            if ("updateStatusToAcknowledgedForRework".equals(method.getName()) && args != null && args.length == 4) {
                SupervisionTaskDO task = task((Long) args[0]);
                if (!SupervisionTaskStatusEnum.REJECTED.getCode().equals(task.getTaskStatus())
                        && !SupervisionTaskStatusEnum.APPROVED.getCode().equals(task.getTaskStatus())) {
                    return 0;
                }
                task.setTaskStatus(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode())
                        .setAssignedUserId((Long) args[1])
                        .setAcceptedAt((LocalDateTime) args[2])
                        .setReworkCount((Integer) args[3]);
                return 1;
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private Long onlyTaskId() {
            assertEquals(1, tasksById.size());
            return new ArrayList<>(tasksById.keySet()).get(0);
        }

        private SupervisionTaskDO task(Long taskId) {
            SupervisionTaskDO task = tasksById.get(taskId);
            if (task == null) {
                throw new IllegalArgumentException("Unknown task: " + taskId);
            }
            return task;
        }

    }

    private static void assertWorkflowState(InMemoryEventStore eventStore, InMemoryTaskMapper taskMapper,
                                            Long eventId, Long taskId,
                                            SupervisionEventStatusEnum eventStatus,
                                            SupervisionTaskStatusEnum taskStatus) {
        assertEquals(eventStatus.getCode(), eventStore.event(eventId).getEventStatus());
        assertEquals(taskStatus.getCode(), taskMapper.task(taskId).getTaskStatus());
    }

}
