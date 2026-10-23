package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.web.core.handler.GlobalExceptionHandler;
import com.basiclab.iot.system.controller.admin.supervision.SupervisionTaskController;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEvidenceItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEvidenceQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskReworkService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService;
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

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupervisionTaskControllerTest {

    @Test
    void getTaskDetailMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/tasks/get")
                        .param("id", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(2001))
                .andExpect(jsonPath("$.data.eventId").value(1001))
                .andExpect(jsonPath("$.data.taskStatus").value(SupervisionTaskStatusEnum.SUBMITTED.getCode()))
                .andExpect(jsonPath("$.data.acceptedUserId").value(3001))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-06-11T09:35:00"))
                .andExpect(jsonPath("$.data.submittedAt").value("2026-06-11T09:50:00"))
                .andExpect(jsonPath("$.data.resultCategory").value("confirmed_violation"))
                .andExpect(jsonPath("$.data.handlingNote").value("Handled according to SOP"))
                .andExpect(jsonPath("$.data.reworkCount").value(1));

        assertEquals(new TaskDetailRequest(2001L), applicationService.detailRequest());
    }

    @Test
    void getTaskDetailRejectsInvalidTaskIdBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/tasks/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("taskId must not be null")));

        assertNull(applicationService.detailRequest());

        assertInvalidTaskDetailIdRejected(mockMvc, applicationService, "0", "taskId must be positive");
        assertInvalidTaskDetailIdRejected(mockMvc, applicationService, "-1", "taskId must be positive");
    }

    private static void assertInvalidTaskDetailIdRejected(MockMvc mockMvc,
                                                          CapturingWorkflowApplicationService applicationService,
                                                          String taskId,
                                                          String expectedMessage) throws Exception {
        mockMvc.perform(get("/system/supervision/tasks/get")
                        .param("id", taskId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString(expectedMessage)));

        assertNull(applicationService.detailRequest());
    }

    @Test
    void getCurrentTaskByEventMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/tasks/by-event")
                        .param("eventId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.taskId").value(2002))
                .andExpect(jsonPath("$.data.eventId").value(1001))
                .andExpect(jsonPath("$.data.taskStatus").value(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode()))
                .andExpect(jsonPath("$.data.acceptedUserId").value(3002))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-06-11T10:20:00"))
                .andExpect(jsonPath("$.data.reworkCount").value(2));

        assertEquals(new TaskByEventRequest(1001L), applicationService.taskByEventRequest());
    }

    @Test
    void getCurrentTaskByEventRejectsInvalidEventIdBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/tasks/by-event"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("eventId must not be null")));

        assertNull(applicationService.taskByEventRequest());

        assertInvalidTaskByEventIdRejected(mockMvc, applicationService, "0", "eventId must be positive");
        assertInvalidTaskByEventIdRejected(mockMvc, applicationService, "-1", "eventId must be positive");
    }

    private static void assertInvalidTaskByEventIdRejected(MockMvc mockMvc,
                                                           CapturingWorkflowApplicationService applicationService,
                                                           String eventId,
                                                           String expectedMessage) throws Exception {
        mockMvc.perform(get("/system/supervision/tasks/by-event")
                        .param("eventId", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString(expectedMessage)));

        assertNull(applicationService.taskByEventRequest());
    }

    @Test
    void acceptTaskMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new TaskAcceptRequest(2001L, 3001L), applicationService.request());
    }

    @Test
    void restartReworkMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new TaskAcceptRequest(2001L, 3001L), applicationService.restartReworkRequest());
    }

    @Test
    void submitTaskMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new TaskSubmitRequest(
                2001L,
                "confirmed_violation",
                "Handled according to SOP"
        ), applicationService.submitRequest());
    }

    @Test
    void approveRecheckMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new TaskRecheckRequest(2001L), applicationService.approveRecheckRequest());
    }

    @Test
    void rejectRecheckMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new TaskRecheckRequest(2001L), applicationService.rejectRecheckRequest());
    }

    @Test
    void submitTaskRejectsInvalidRequiredFieldsBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        assertInvalidSubmitFieldRejected(
                mockMvc,
                applicationService,
                0,
                "confirmed_violation",
                "Handled according to SOP",
                "taskId must be positive"
        );
        assertInvalidSubmitFieldRejected(
                mockMvc,
                applicationService,
                2001,
                " ",
                "Handled according to SOP",
                "resultCategory must not be blank"
        );
        assertInvalidSubmitFieldRejected(
                mockMvc,
                applicationService,
                2001,
                "confirmed_violation",
                " ",
                "handlingNote must not be blank"
        );
    }

    private static void assertInvalidSubmitFieldRejected(MockMvc mockMvc,
                                                         CapturingWorkflowApplicationService applicationService,
                                                         long taskId,
                                                         String resultCategory,
                                                         String handlingNote,
                                                         String expectedMessage) throws Exception {
        mockMvc.perform(post("/system/supervision/tasks/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": %d,
                                  "resultCategory": "%s",
                                  "handlingNote": "%s"
                                }
                                """.formatted(taskId, resultCategory, handlingNote)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString(expectedMessage)));

        assertNull(applicationService.submitRequest());
    }

    @Test
    void acceptTaskRejectsInvalidRequiredFieldsBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        assertInvalidAcceptFieldRejected(
                mockMvc,
                applicationService,
                0,
                3001,
                "taskId must be positive"
        );
        assertInvalidAcceptFieldRejected(
                mockMvc,
                applicationService,
                2001,
                0,
                "acceptedUserId must be positive"
        );
    }

    private static void assertInvalidAcceptFieldRejected(MockMvc mockMvc,
                                                         CapturingWorkflowApplicationService applicationService,
                                                         long taskId,
                                                         long acceptedUserId,
                                                         String expectedMessage) throws Exception {
        mockMvc.perform(post("/system/supervision/tasks/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "taskId": %d,
                                  "acceptedUserId": %d
                                }
                                """.formatted(taskId, acceptedUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString(expectedMessage)));

        assertNull(applicationService.request());
    }

    private static MockMvc mockMvc(SupervisionWorkflowApplicationService applicationService) {
        return MockMvcBuilders
                .standaloneSetup(new SupervisionTaskController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler("iot-system-biz"))
                .build();
    }

    private static final class CapturingWorkflowApplicationService extends SupervisionWorkflowApplicationService {

        private TaskAcceptRequest request;
        private TaskAcceptRequest restartReworkRequest;
        private TaskDetailRequest detailRequest;
        private TaskByEventRequest taskByEventRequest;
        private TaskSubmitRequest submitRequest;
        private TaskRecheckRequest approveRecheckRequest;
        private TaskRecheckRequest rejectRecheckRequest;

        private CapturingWorkflowApplicationService() {
            super(
                    command -> new AlertToEventResult(
                            1001L,
                            "video",
                            "alert-001",
                            "abnormal_gathering",
                            "crowd_gathering",
                            SupervisionEventLevelEnum.L3,
                            SupervisionEventStatusEnum.DISPATCHED.getCode(),
                            false
                    ),
                    unusedAcceptanceService(),
                    unusedSubmissionService(),
                    unusedRecheckService(),
                    unusedCloseCheckService(),
                    unusedReworkService(),
                    unusedTaskQueryService(),
                    unusedEvidenceQueryService()
            );
        }

        @Override
        public TaskDetailResponse getCurrentTaskByEvent(TaskByEventRequest request) {
            this.taskByEventRequest = request;
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
        public TaskDetailResponse getTaskDetail(TaskDetailRequest request) {
            this.detailRequest = request;
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
        public OperationResponse acceptTask(TaskAcceptRequest request) {
            this.request = request;
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse restartRework(TaskAcceptRequest request) {
            this.restartReworkRequest = request;
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse submitTask(TaskSubmitRequest request) {
            this.submitRequest = request;
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse approveRecheck(TaskRecheckRequest request) {
            this.approveRecheckRequest = request;
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse rejectRecheck(TaskRecheckRequest request) {
            this.rejectRecheckRequest = request;
            return new OperationResponse(true);
        }

        private TaskAcceptRequest request() {
            return request;
        }

        private TaskAcceptRequest restartReworkRequest() {
            return restartReworkRequest;
        }

        private TaskDetailRequest detailRequest() {
            return detailRequest;
        }

        private TaskByEventRequest taskByEventRequest() {
            return taskByEventRequest;
        }

        private TaskSubmitRequest submitRequest() {
            return submitRequest;
        }

        private TaskRecheckRequest approveRecheckRequest() {
            return approveRecheckRequest;
        }

        private TaskRecheckRequest rejectRecheckRequest() {
            return rejectRecheckRequest;
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

    private static SupervisionTaskQueryService unusedTaskQueryService() {
        return new SupervisionTaskQueryService(unusedTaskMapper()) {
            @Override
            public java.util.Optional<SupervisionTaskQueryService.TaskDetail> getTaskDetail(Long taskId) {
                throw new AssertionError("unused task query service");
            }
        };
    }

    private static SupervisionEvidenceQueryService unusedEvidenceQueryService() {
        return new SupervisionEvidenceQueryService(unusedEvidenceMapper()) {
            @Override
            public java.util.List<SupervisionEvidenceQueryService.EvidenceItem> listByEventId(Long eventId) {
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
