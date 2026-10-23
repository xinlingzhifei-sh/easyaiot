package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.web.core.handler.GlobalExceptionHandler;
import com.basiclab.iot.system.controller.admin.supervision.SupervisionEventController;
import com.basiclab.iot.system.controller.admin.supervision.SupervisionTaskController;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEvidenceItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEvidenceQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskReworkService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.CloseCheckRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.ClosureSummaryRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.ClosureSummaryResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventDetailRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventDetailResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventEvidenceItemResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventEvidenceRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventTimelineItemResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventTimelineRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.OperationResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskAcceptRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskByEventRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskDetailRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskDetailResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskRecheckRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskSubmitRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupervisionP0ControllerRegressionTest {

    @Test
    void p0ControllerEndpointsRemainMappedAndReturnSuccessfulContracts() throws Exception {
        SupervisionWorkflowApplicationService applicationService = new StubWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(post("/system/supervision/events/from-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceSystem": "video",
                                  "sourceAlertId": "alert-001",
                                  "ruleCode": "abnormal_gathering",
                                  "sourceAlertType": "abnormal_gathering",
                                  "sourceAlertTime": "2026-06-11T09:30:00",
                                  "sourcePayloadHash": "payload-hash-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventId").value(1001));

        mockMvc.perform(post("/system/supervision/tasks/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 2001,
                                  "acceptedUserId": 3001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/system/supervision/tasks/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 2001,
                                  "resultCategory": "confirmed_violation",
                                  "handlingNote": "Handled according to SOP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/system/supervision/tasks/recheck/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 2001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/system/supervision/tasks/recheck/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 2001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/system/supervision/events/close-check/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 1001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/system/supervision/events/close-check/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": 1001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(post("/system/supervision/tasks/rework/restart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": 2001,
                                  "acceptedUserId": 3001
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(true));

        mockMvc.perform(get("/system/supervision/events/get")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventStatus").value(SupervisionEventStatusEnum.CLOSED.getCode()));

        mockMvc.perform(get("/system/supervision/tasks/get")
                        .param("id", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskStatus").value(SupervisionTaskStatusEnum.SUBMITTED.getCode()));

        mockMvc.perform(get("/system/supervision/tasks/by-event")
                        .param("eventId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(2002));

        mockMvc.perform(get("/system/supervision/events/closure-summary")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventId").value(1001))
                .andExpect(jsonPath("$.data.taskId").value(2002));

        mockMvc.perform(get("/system/supervision/events/evidence")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].evidenceId").value(3001));

        mockMvc.perform(get("/system/supervision/events/timeline")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].timelineType").value("event_created"));
    }

    private static MockMvc mockMvc(SupervisionWorkflowApplicationService applicationService) {
        return MockMvcBuilders
                .standaloneSetup(
                        new SupervisionEventController(applicationService),
                        new SupervisionTaskController(applicationService)
                )
                .setControllerAdvice(new GlobalExceptionHandler("iot-system-biz"))
                .build();
    }

    private static final class StubWorkflowApplicationService extends SupervisionWorkflowApplicationService {

        private StubWorkflowApplicationService() {
            super(
                    command -> {
                        throw new AssertionError("unused event service");
                    },
                    unusedAcceptanceService(),
                    unusedSubmissionService(),
                    unusedRecheckService(),
                    unusedCloseCheckService(),
                    unusedReworkService(),
                    new SupervisionTaskQueryService(unusedTaskMapper()),
                    unusedEvidenceQueryService()
            );
        }

        @Override
        public AlertEventResponse createEventFromAlert(AlertEventRequest request) {
            return new AlertEventResponse(
                    1001L,
                    "video",
                    "alert-001",
                    "abnormal_gathering",
                    "crowd_gathering",
                    "L3",
                    SupervisionEventStatusEnum.DISPATCHED.getCode(),
                    false
            );
        }

        @Override
        public OperationResponse acceptTask(TaskAcceptRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse submitTask(TaskSubmitRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse approveRecheck(TaskRecheckRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse rejectRecheck(TaskRecheckRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse approveCloseCheck(CloseCheckRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse rejectCloseCheck(CloseCheckRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse restartRework(TaskAcceptRequest request) {
            return new OperationResponse(true);
        }

        @Override
        public EventDetailResponse getEventDetail(EventDetailRequest request) {
            return new EventDetailResponse(
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
            );
        }

        @Override
        public TaskDetailResponse getTaskDetail(TaskDetailRequest request) {
            return new TaskDetailResponse(
                    2001L,
                    1001L,
                    SupervisionTaskStatusEnum.SUBMITTED.getCode(),
                    3001L,
                    LocalDateTime.of(2026, 6, 11, 9, 35),
                    LocalDateTime.of(2026, 6, 11, 9, 50),
                    "confirmed_violation",
                    "Handled according to SOP",
                    1
            );
        }

        @Override
        public TaskDetailResponse getCurrentTaskByEvent(TaskByEventRequest request) {
            return new TaskDetailResponse(
                    2002L,
                    1001L,
                    SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode(),
                    3002L,
                    LocalDateTime.of(2026, 6, 11, 10, 20),
                    null,
                    null,
                    null,
                    2
            );
        }

        @Override
        public ClosureSummaryResponse getClosureSummary(ClosureSummaryRequest request) {
            return new ClosureSummaryResponse(
                    1001L,
                    SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode(),
                    2002L,
                    SupervisionTaskStatusEnum.APPROVED.getCode(),
                    2,
                    "normal_closed",
                    LocalDateTime.of(2026, 6, 11, 9, 35),
                    LocalDateTime.of(2026, 6, 11, 9, 50),
                    LocalDateTime.of(2026, 6, 11, 10, 10)
            );
        }

        @Override
        public List<EventEvidenceItemResponse> getEventEvidence(EventEvidenceRequest request) {
            return List.of(new EventEvidenceItemResponse(
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
            ));
        }

        @Override
        public List<EventTimelineItemResponse> getEventTimeline(EventTimelineRequest request) {
            return List.of(new EventTimelineItemResponse(
                    1001L,
                    "event_created",
                    SupervisionEventStatusEnum.CLOSED.getCode(),
                    "1001",
                    LocalDateTime.of(2026, 6, 11, 9, 30)
            ));
        }

    }

    private static SupervisionTaskAcceptanceService unusedAcceptanceService() {
        return new SupervisionTaskAcceptanceService(unusedTaskMapper(), eventId -> {
            throw new AssertionError("unused event acceptance store");
        }) {
        };
    }

    private static SupervisionTaskSubmissionService unusedSubmissionService() {
        return new SupervisionTaskSubmissionService(unusedTaskMapper(), eventId -> {
            throw new AssertionError("unused event handling store");
        }) {
        };
    }

    private static SupervisionTaskRecheckService unusedRecheckService() {
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
        };
    }

    private static SupervisionEventCloseCheckService unusedCloseCheckService() {
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
        };
    }

    private static SupervisionTaskReworkService unusedReworkService() {
        return new SupervisionTaskReworkService(unusedTaskMapper(), eventId -> {
            throw new AssertionError("unused event rework store");
        }) {
        };
    }

    private static SupervisionEvidenceQueryService unusedEvidenceQueryService() {
        return new SupervisionEvidenceQueryService(unusedEvidenceMapper()) {
            @Override
            public List<SupervisionEvidenceQueryService.EvidenceItem> listByEventId(Long eventId) {
                throw new AssertionError("unused evidence query service");
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
