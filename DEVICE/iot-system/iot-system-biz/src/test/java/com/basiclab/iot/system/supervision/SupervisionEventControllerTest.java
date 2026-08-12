package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.web.core.handler.GlobalExceptionHandler;
import com.basiclab.iot.system.controller.admin.supervision.SupervisionEventController;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.AlertEventCreateReqVO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEvidenceItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEvidenceQueryService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService;
import com.basiclab.iot.system.service.supervision.SupervisionTaskQueryService;
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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupervisionEventControllerTest {

    @Test
    void createEventFromAlertRequiresSeededCreatePermission() throws Exception {
        PreAuthorize preAuthorize = SupervisionEventController.class
                .getMethod("createEventFromAlert", AlertEventCreateReqVO.class)
                .getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("@ss.hasPermission('supervision:event:create')", preAuthorize.value());
    }

    @Test
    void getEventDetailMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/events/get")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventId").value(1001))
                .andExpect(jsonPath("$.data.sourceSystem").value("video"))
                .andExpect(jsonPath("$.data.sourceAlertId").value("alert-001"))
                .andExpect(jsonPath("$.data.ruleCode").value("RULE_ABNORMAL_GATHERING"))
                .andExpect(jsonPath("$.data.eventType").value("crowd_gathering"))
                .andExpect(jsonPath("$.data.eventLevel").value("L3"))
                .andExpect(jsonPath("$.data.eventStatus").value(SupervisionEventStatusEnum.CLOSED.getCode()))
                .andExpect(jsonPath("$.data.closeResult").value("normal_closed"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-06-11T09:30:00"))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-06-11T09:35:00"))
                .andExpect(jsonPath("$.data.handledAt").value("2026-06-11T09:50:00"))
                .andExpect(jsonPath("$.data.closedAt").value("2026-06-11T10:10:00"));

        assertEquals(new EventDetailRequest(1001L), applicationService.detailRequest());
    }

    @Test
    void getEventDetailRejectsInvalidEventIdBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/events/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("eventId must not be null")));

        assertNull(applicationService.detailRequest());

        assertInvalidEventIdRejected(mockMvc, applicationService, "0", "eventId must be positive");
        assertInvalidEventIdRejected(mockMvc, applicationService, "-1", "eventId must be positive");
    }

    private static void assertInvalidEventIdRejected(MockMvc mockMvc,
                                                     CapturingWorkflowApplicationService applicationService,
                                                     String eventId,
                                                     String expectedMessage) throws Exception {
        mockMvc.perform(get("/system/supervision/events/get")
                        .param("id", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString(expectedMessage)));

        assertNull(applicationService.detailRequest());
    }

    @Test
    void getClosureSummaryMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/events/closure-summary")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.eventId").value(1001))
                .andExpect(jsonPath("$.data.eventStatus").value(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode()))
                .andExpect(jsonPath("$.data.taskId").value(2002))
                .andExpect(jsonPath("$.data.taskStatus").value(SupervisionTaskStatusEnum.APPROVED.getCode()))
                .andExpect(jsonPath("$.data.reworkCount").value(2))
                .andExpect(jsonPath("$.data.closeResult").value("normal_closed"))
                .andExpect(jsonPath("$.data.acceptedAt").value("2026-06-11T09:35:00"))
                .andExpect(jsonPath("$.data.handledAt").value("2026-06-11T09:50:00"))
                .andExpect(jsonPath("$.data.closedAt").value("2026-06-11T10:10:00"));

        assertEquals(new ClosureSummaryRequest(1001L), applicationService.closureSummaryRequest());
    }

    @Test
    void getClosureSummaryRejectsInvalidEventIdBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/events/closure-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString("eventId must not be null")));

        assertNull(applicationService.closureSummaryRequest());

        assertInvalidClosureSummaryEventIdRejected(mockMvc, applicationService, "0", "eventId must be positive");
        assertInvalidClosureSummaryEventIdRejected(mockMvc, applicationService, "-1", "eventId must be positive");
    }

    private static void assertInvalidClosureSummaryEventIdRejected(MockMvc mockMvc,
                                                                   CapturingWorkflowApplicationService applicationService,
                                                                   String eventId,
                                                                   String expectedMessage) throws Exception {
        mockMvc.perform(get("/system/supervision/events/closure-summary")
                        .param("id", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(containsString(expectedMessage)));

        assertNull(applicationService.closureSummaryRequest());
    }

    @Test
    void getEventEvidenceMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/events/evidence")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].evidenceId").value(3001))
                .andExpect(jsonPath("$.data[0].eventId").value(1001))
                .andExpect(jsonPath("$.data[0].sourceType").value("video"))
                .andExpect(jsonPath("$.data[0].materialType").value("snapshot"))
                .andExpect(jsonPath("$.data[0].materialUri").value("/media/alarm/snapshot-001.jpg"))
                .andExpect(jsonPath("$.data[0].relatedRecordId").value("alarm-image-001"))
                .andExpect(jsonPath("$.data[0].isRequired").value(true))
                .andExpect(jsonPath("$.data[0].requiredForLevel").value("L3"))
                .andExpect(jsonPath("$.data[0].collectStatus").value("collected"))
                .andExpect(jsonPath("$.data[0].sensitivityLevel").value("normal"))
                .andExpect(jsonPath("$.data[0].createdAt").value("2026-06-11T09:31:00"));

        assertEquals(new EventEvidenceRequest(1001L), applicationService.evidenceRequest());
    }

    @Test
    void getEventTimelineMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        mockMvc.perform(get("/system/supervision/events/timeline")
                        .param("id", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].eventId").value(1001))
                .andExpect(jsonPath("$.data[0].timelineType").value("event_created"))
                .andExpect(jsonPath("$.data[0].timelineStatus").value(SupervisionEventStatusEnum.CREATED.getCode()))
                .andExpect(jsonPath("$.data[0].relatedRecordId").value("1001"))
                .andExpect(jsonPath("$.data[0].occurredAt").value("2026-06-11T09:30:00"))
                .andExpect(jsonPath("$.data[1].timelineType").value("evidence_collected"))
                .andExpect(jsonPath("$.data[1].timelineStatus").value("collected"))
                .andExpect(jsonPath("$.data[1].relatedRecordId").value("alarm-image-001"))
                .andExpect(jsonPath("$.data[1].occurredAt").value("2026-06-11T09:31:00"));

        assertEquals(new EventTimelineRequest(1001L), applicationService.timelineRequest());
    }

    @Test
    void createEventFromAlertMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
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
                .andExpect(jsonPath("$.data.eventId").value(1001))
                .andExpect(jsonPath("$.data.sourceSystem").value("video"))
                .andExpect(jsonPath("$.data.sourceAlertId").value("alert-001"))
                .andExpect(jsonPath("$.data.ruleCode").value("abnormal_gathering"))
                .andExpect(jsonPath("$.data.eventType").value("crowd_gathering"))
                .andExpect(jsonPath("$.data.eventLevel").value("L3"))
                .andExpect(jsonPath("$.data.eventStatus").value(SupervisionEventStatusEnum.DISPATCHED.getCode()))
                .andExpect(jsonPath("$.data.reused").value(false));

        assertEquals(new AlertEventRequest(
                "video",
                "alert-001",
                "abnormal_gathering",
                "abnormal_gathering",
                LocalDateTime.of(2026, 6, 11, 9, 30),
                "payload-hash-001"
        ), applicationService.request());
    }

    @Test
    void approveCloseCheckMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new CloseCheckRequest(1001L), applicationService.approveCloseCheckRequest());
    }

    @Test
    void rejectCloseCheckMapsHttpRequestToApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

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

        assertEquals(new CloseCheckRequest(1001L), applicationService.rejectCloseCheckRequest());
    }

    @Test
    void createEventFromAlertRejectsBlankRequiredFieldsBeforeApplicationFacade() throws Exception {
        CapturingWorkflowApplicationService applicationService = new CapturingWorkflowApplicationService();
        MockMvc mockMvc = mockMvc(applicationService);

        assertBlankRequiredFieldRejected(
                mockMvc,
                applicationService,
                " ",
                "alert-001",
                "abnormal_gathering",
                "请求参数不正确:sourceSystem must not be blank"
        );
        assertBlankRequiredFieldRejected(
                mockMvc,
                applicationService,
                "video",
                " ",
                "abnormal_gathering",
                "请求参数不正确:sourceAlertId must not be blank"
        );
        assertBlankRequiredFieldRejected(
                mockMvc,
                applicationService,
                "video",
                "alert-001",
                " ",
                "请求参数不正确:ruleCode must not be blank"
        );
    }

    private static void assertBlankRequiredFieldRejected(MockMvc mockMvc,
                                                         CapturingWorkflowApplicationService applicationService,
                                                         String sourceSystem,
                                                         String sourceAlertId,
                                                         String ruleCode,
                                                         String expectedMessage) throws Exception {
        mockMvc.perform(post("/system/supervision/events/from-alert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceSystem": "%s",
                                  "sourceAlertId": "%s",
                                  "ruleCode": "%s",
                                  "sourceAlertType": "abnormal_gathering",
                                  "sourceAlertTime": "2026-06-11T09:30:00",
                                  "sourcePayloadHash": "payload-hash-001"
                                }
                                """.formatted(sourceSystem, sourceAlertId, ruleCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value(expectedMessage));

        assertNull(applicationService.request());
    }

    private static MockMvc mockMvc(SupervisionWorkflowApplicationService applicationService) {
        return MockMvcBuilders
                .standaloneSetup(new SupervisionEventController(applicationService))
                .setControllerAdvice(new GlobalExceptionHandler("iot-system-biz"))
                .build();
    }

    private static final class CapturingWorkflowApplicationService extends SupervisionWorkflowApplicationService {

        private AlertEventRequest request;
        private EventDetailRequest detailRequest;
        private ClosureSummaryRequest closureSummaryRequest;
        private EventEvidenceRequest evidenceRequest;
        private EventTimelineRequest timelineRequest;
        private CloseCheckRequest approveCloseCheckRequest;
        private CloseCheckRequest rejectCloseCheckRequest;

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
        public ClosureSummaryResponse getClosureSummary(ClosureSummaryRequest request) {
            this.closureSummaryRequest = request;
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
        public EventDetailResponse getEventDetail(EventDetailRequest request) {
            this.detailRequest = request;
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
        public List<EventEvidenceItemResponse> getEventEvidence(EventEvidenceRequest request) {
            this.evidenceRequest = request;
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
            this.timelineRequest = request;
            return List.of(
                    new EventTimelineItemResponse(
                            1001L,
                            "event_created",
                            SupervisionEventStatusEnum.CREATED.getCode(),
                            "1001",
                            LocalDateTime.of(2026, 6, 11, 9, 30)
                    ),
                    new EventTimelineItemResponse(
                            1001L,
                            "evidence_collected",
                            "collected",
                            "alarm-image-001",
                            LocalDateTime.of(2026, 6, 11, 9, 31)
                    )
            );
        }

        @Override
        public AlertEventResponse createEventFromAlert(AlertEventRequest request) {
            this.request = request;
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
        public OperationResponse approveCloseCheck(CloseCheckRequest request) {
            this.approveCloseCheckRequest = request;
            return new OperationResponse(true);
        }

        @Override
        public OperationResponse rejectCloseCheck(CloseCheckRequest request) {
            this.rejectCloseCheckRequest = request;
            return new OperationResponse(true);
        }

        private AlertEventRequest request() {
            return request;
        }

        private EventDetailRequest detailRequest() {
            return detailRequest;
        }

        private ClosureSummaryRequest closureSummaryRequest() {
            return closureSummaryRequest;
        }

        private EventEvidenceRequest evidenceRequest() {
            return evidenceRequest;
        }

        private EventTimelineRequest timelineRequest() {
            return timelineRequest;
        }

        private CloseCheckRequest approveCloseCheckRequest() {
            return approveCloseCheckRequest;
        }

        private CloseCheckRequest rejectCloseCheckRequest() {
            return rejectCloseCheckRequest;
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
