package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEvidenceItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEvidenceQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionEvidenceQueryService.EvidenceItem;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventDetail;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskReworkService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.CloseCheckRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventEvidenceItemResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventEvidenceRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventTimelineItemResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventTimelineRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskAcceptRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskRecheckRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskSubmitRequest;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionWorkflowApplicationServiceTest {

    @Test
    void createEventFromAlertMapsRequestAndReturnsControllerReadyResponse() {
        List<AlertToEventCommand> commands = new ArrayList<>();
        SupervisionEventService eventService = command -> {
            commands.add(command);
            return new AlertToEventResult(
                    1001L,
                    "video",
                    "alert-001",
                    "abnormal_gathering",
                    "crowd_gathering",
                    SupervisionEventLevelEnum.L3,
                    SupervisionEventStatusEnum.DISPATCHED.getCode(),
                    false
            );
        };
        SupervisionWorkflowApplicationService service = newApplicationService(eventService);

        AlertEventResponse response = service.createEventFromAlert(new AlertEventRequest(
                "video",
                "alert-001",
                "abnormal_gathering",
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 30),
                "payload-hash-001"
        ));

        assertEquals(List.of(new AlertToEventCommand(
                "video",
                "alert-001",
                "abnormal_gathering",
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 30),
                "payload-hash-001"
        )), commands);
        assertEquals(1001L, response.eventId());
        assertEquals("video", response.sourceSystem());
        assertEquals("alert-001", response.sourceAlertId());
        assertEquals("abnormal_gathering", response.ruleCode());
        assertEquals("crowd_gathering", response.eventType());
        assertEquals("L3", response.eventLevel());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), response.eventStatus());
        assertFalse(response.reused());
    }

    @Test
    void taskActionsMapRequestsToDomainServicesAndReturnControllerReadyResponses() {
        List<String> calls = new ArrayList<>();
        SupervisionWorkflowApplicationService service = new SupervisionWorkflowApplicationService(
                command -> {
                    throw new AssertionError("unused event service");
                },
                acceptanceService(calls, true),
                submissionService(calls, false),
                recheckService(calls, true, false),
                closeCheckService(calls, true, false),
                reworkService(calls, true),
                unusedTaskQueryService(),
                unusedEvidenceQueryService()
        );

        assertTrue(service.acceptTask(new TaskAcceptRequest(2001L, 3001L)).success());
        assertFalse(service.submitTask(new TaskSubmitRequest(2002L, "normal", "handled on site")).success());
        assertTrue(service.approveRecheck(new TaskRecheckRequest(2003L)).success());
        assertFalse(service.rejectRecheck(new TaskRecheckRequest(2004L)).success());
        assertTrue(service.approveCloseCheck(new CloseCheckRequest(1001L)).success());
        assertFalse(service.rejectCloseCheck(new CloseCheckRequest(1002L)).success());
        assertTrue(service.restartRework(new TaskAcceptRequest(2005L, 3005L)).success());

        assertEquals(List.of(
                "accept:2001:3001",
                "submit:2002:normal:handled on site",
                "approveRecheck:2003",
                "rejectRecheck:2004",
                "approveCloseCheck:1001",
                "rejectCloseCheck:1002",
                "restartRework:2005:3005"
        ), calls);
    }

    @Test
    void eventEvidenceAndTimelineQueriesMapReadModels() {
        SupervisionWorkflowApplicationService service = newApplicationService(
                eventServiceWithDetail(new EventDetail(
                        1001L,
                        "video",
                        "alert-001",
                        "RULE_ABNORMAL_GATHERING",
                        "crowd_gathering",
                        "L3",
                        SupervisionEventStatusEnum.CLOSED.getCode(),
                        "normal_closed",
                        LocalDateTime.of(2026, 6, 11, 9, 30),
                        LocalDateTime.of(2026, 6, 11, 9, 35),
                        LocalDateTime.of(2026, 6, 11, 9, 50),
                        LocalDateTime.of(2026, 6, 11, 10, 10)
                )),
                evidenceQueryService(List.of(new EvidenceItem(
                        3001L,
                        1001L,
                        "video",
                        "snapshot",
                        "/media/alarm/snapshot-001.jpg",
                        "alarm-image-001",
                        true,
                        "L3",
                        "collected",
                        null,
                        "normal",
                        LocalDateTime.of(2026, 6, 11, 9, 31)
                )))
        );

        List<EventEvidenceItemResponse> evidence = service.getEventEvidence(new EventEvidenceRequest(1001L));
        List<EventTimelineItemResponse> timeline = service.getEventTimeline(new EventTimelineRequest(1001L));

        assertEquals(1, evidence.size());
        assertEquals(3001L, evidence.get(0).evidenceId());
        assertEquals("snapshot", evidence.get(0).materialType());
        assertEquals("alarm-image-001", evidence.get(0).relatedRecordId());
        assertEquals(5, timeline.size());
        assertEquals("event_created", timeline.get(0).timelineType());
        assertEquals(LocalDateTime.of(2026, 6, 11, 9, 30), timeline.get(0).occurredAt());
        assertEquals("evidence_collected", timeline.get(1).timelineType());
        assertEquals("event_accepted", timeline.get(2).timelineType());
        assertEquals("event_handled", timeline.get(3).timelineType());
        assertEquals("event_closed", timeline.get(4).timelineType());
    }

    @Test
    void createEventFromAlertRejectsBlankRequiredFieldsBeforeDomainCall() {
        SupervisionWorkflowApplicationService service = newApplicationService(command -> {
            throw new AssertionError("event service should not be called for invalid request");
        });

        assertInvalidAlertRequest(service, new AlertEventRequest(
                " ",
                "alert-001",
                "abnormal_gathering",
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 30),
                "payload-hash-001"
        ));
        assertInvalidAlertRequest(service, new AlertEventRequest(
                "video",
                "",
                "abnormal_gathering",
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 30),
                "payload-hash-001"
        ));
        assertInvalidAlertRequest(service, new AlertEventRequest(
                "video",
                "alert-001",
                " ",
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 30),
                "payload-hash-001"
        ));
    }

    @Test
    void taskActionsRejectInvalidRequiredFieldsBeforeDomainCall() {
        SupervisionWorkflowApplicationService service = applicationServiceThatFailsIfCalled();

        assertThrows(IllegalArgumentException.class,
                () -> service.acceptTask(new TaskAcceptRequest(null, 3001L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.acceptTask(new TaskAcceptRequest(0L, 3001L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.acceptTask(new TaskAcceptRequest(2001L, null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(new TaskSubmitRequest(null, "normal", "handled on site")));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(new TaskSubmitRequest(0L, "normal", "handled on site")));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(new TaskSubmitRequest(2002L, " ", "handled on site")));
        assertThrows(IllegalArgumentException.class,
                () -> service.submitTask(new TaskSubmitRequest(2002L, "normal", " ")));
        assertThrows(IllegalArgumentException.class,
                () -> service.approveRecheck(new TaskRecheckRequest(null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.approveRecheck(new TaskRecheckRequest(0L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.rejectRecheck(new TaskRecheckRequest(null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.approveCloseCheck(new CloseCheckRequest(null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.approveCloseCheck(new CloseCheckRequest(0L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.rejectCloseCheck(new CloseCheckRequest(null)));
        assertThrows(IllegalArgumentException.class,
                () -> service.restartRework(new TaskAcceptRequest(null, 3005L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.restartRework(new TaskAcceptRequest(0L, 3005L)));
        assertThrows(IllegalArgumentException.class,
                () -> service.restartRework(new TaskAcceptRequest(2005L, null)));
    }

    private static SupervisionWorkflowApplicationService newApplicationService(SupervisionEventService eventService) {
        return newApplicationService(eventService, unusedEvidenceQueryService());
    }

    private static SupervisionWorkflowApplicationService newApplicationService(SupervisionEventService eventService,
                                                                              SupervisionEvidenceQueryService evidenceQueryService) {
        List<String> calls = new ArrayList<>();
        return new SupervisionWorkflowApplicationService(
                eventService,
                acceptanceService(calls, true),
                submissionService(calls, true),
                recheckService(calls, true, true),
                closeCheckService(calls, true, true),
                reworkService(calls, true),
                unusedTaskQueryService(),
                evidenceQueryService
        );
    }

    private static SupervisionWorkflowApplicationService applicationServiceThatFailsIfCalled() {
        List<String> calls = new ArrayList<>();
        return new SupervisionWorkflowApplicationService(
                command -> {
                    throw new AssertionError("event service should not be called for invalid request");
                },
                acceptanceService(calls, true),
                submissionService(calls, true),
                recheckService(calls, true, true),
                closeCheckService(calls, true, true),
                reworkService(calls, true),
                unusedTaskQueryService(),
                unusedEvidenceQueryService()
        );
    }

    private static void assertInvalidAlertRequest(SupervisionWorkflowApplicationService service,
                                                  AlertEventRequest request) {
        assertThrows(IllegalArgumentException.class, () -> service.createEventFromAlert(request));
    }

    private static SupervisionTaskAcceptanceService acceptanceService(List<String> calls, boolean result) {
        return new SupervisionTaskAcceptanceService(unusedTaskMapper(), eventId -> {
            throw new AssertionError("unused event acceptance store");
        }) {
            @Override
            public boolean acceptTask(Long taskId, Long acceptedUserId) {
                calls.add("accept:" + taskId + ":" + acceptedUserId);
                return result;
            }
        };
    }

    private static SupervisionTaskSubmissionService submissionService(List<String> calls, boolean result) {
        return new SupervisionTaskSubmissionService(unusedTaskMapper(), eventId -> {
            throw new AssertionError("unused event handling store");
        }) {
            @Override
            public boolean submitTask(Long taskId, String resultCategory, String handlingNote) {
                calls.add("submit:" + taskId + ":" + resultCategory + ":" + handlingNote);
                return result;
            }
        };
    }

    private static SupervisionTaskRecheckService recheckService(List<String> calls,
                                                                boolean approveResult,
                                                                boolean rejectResult) {
        return new SupervisionTaskRecheckService(unusedTaskMapper(), new SupervisionTaskRecheckService.EventRecheckStore() {
            @Override
            public void markRechecked(Long eventId) {
                throw new AssertionError("unused event recheck store");
            }

            @Override
            public void markReworkRequired(Long eventId) {
                throw new AssertionError("unused event recheck store");
            }
        }) {
            @Override
            public boolean approveSubmittedTask(Long taskId) {
                calls.add("approveRecheck:" + taskId);
                return approveResult;
            }

            @Override
            public boolean rejectSubmittedTask(Long taskId) {
                calls.add("rejectRecheck:" + taskId);
                return rejectResult;
            }
        };
    }

    private static SupervisionEventCloseCheckService closeCheckService(List<String> calls,
                                                                       boolean approveResult,
                                                                       boolean rejectResult) {
        return new SupervisionEventCloseCheckService(new SupervisionEventCloseCheckService.EventCloseStore() {
            @Override
            public boolean markClosed(Long eventId, String closeResult) {
                throw new AssertionError("unused event close store");
            }

            @Override
            public boolean markCloseCheckReworkRequired(Long eventId) {
                throw new AssertionError("unused event close store");
            }
        }) {
            @Override
            public boolean approveCloseCheck(Long eventId) {
                calls.add("approveCloseCheck:" + eventId);
                return approveResult;
            }

            @Override
            public boolean rejectCloseCheck(Long eventId) {
                calls.add("rejectCloseCheck:" + eventId);
                return rejectResult;
            }
        };
    }

    private static SupervisionTaskReworkService reworkService(List<String> calls, boolean result) {
        return new SupervisionTaskReworkService(unusedTaskMapper(), eventId -> {
            throw new AssertionError("unused event rework store");
        }) {
            @Override
            public boolean restartReworkTask(Long taskId, Long acceptedUserId) {
                calls.add("restartRework:" + taskId + ":" + acceptedUserId);
                return result;
            }
        };
    }

    private static SupervisionTaskQueryService unusedTaskQueryService() {
        return new SupervisionTaskQueryService(unusedTaskMapper()) {
            @Override
            public java.util.Optional<SupervisionTaskQueryService.TaskDetail> getTaskDetail(Long taskId) {
                throw new AssertionError("unused task query service");
            }
        };
    }

    private static SupervisionEvidenceQueryService evidenceQueryService(List<EvidenceItem> evidenceItems) {
        return new SupervisionEvidenceQueryService(unusedEvidenceMapper()) {
            @Override
            public List<EvidenceItem> listByEventId(Long eventId) {
                return evidenceItems;
            }
        };
    }

    private static SupervisionEvidenceQueryService unusedEvidenceQueryService() {
        return evidenceQueryService(List.of());
    }

    private static SupervisionEventService eventServiceWithDetail(EventDetail detail) {
        return new SupervisionEventService() {
            @Override
            public AlertToEventResult createFromAlert(AlertToEventCommand command) {
                throw new AssertionError("unused event creation");
            }

            @Override
            public Optional<EventDetail> getEventDetail(Long eventId) {
                return Optional.of(detail);
            }
        };
    }

    private static SupervisionTaskMapper unusedTaskMapper() {
        Object target = new Object();
        return (SupervisionTaskMapper) Proxy.newProxyInstance(
                SupervisionTaskMapper.class.getClassLoader(),
                new Class<?>[]{SupervisionTaskMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(target, args);
                    }
                    throw new AssertionError("unused task mapper");
                }
        );
    }

    private static SupervisionEvidenceItemMapper unusedEvidenceMapper() {
        Object target = new Object();
        return (SupervisionEvidenceItemMapper) Proxy.newProxyInstance(
                SupervisionEvidenceItemMapper.class.getClassLoader(),
                new Class<?>[]{SupervisionEvidenceItemMapper.class},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return method.invoke(target, args);
                    }
                    throw new AssertionError("unused evidence mapper");
                }
        );
    }

}
