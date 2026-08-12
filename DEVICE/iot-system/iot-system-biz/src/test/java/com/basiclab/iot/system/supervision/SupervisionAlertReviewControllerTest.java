package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.domain.LoginUser;
import com.basiclab.iot.system.controller.admin.supervision.SupervisionAlertReviewController;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.CaseTimelineRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.DetailStreamRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.OperationReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.EvidenceDownloadAuditReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.EvidenceExportReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.IntegrationSmokeReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.RuleReplayReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.RuleSuggestionStatusReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.UserStatusReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.SemanticTriggerEvaluationReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.review.AlertReviewVO.SemanticTriggerConfirmationReqVO;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseOperationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseOwnerCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseTimelineItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewDetailStreamItem;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVerificationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceVerificationReport;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceDownloadArtifact;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceDownloadCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewIntegrationSmokeCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewIntegrationSmokeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewManifestVerification;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewMediaAccessAuditEntry;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewPlaybackAccess;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewPlaybackCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewUserStatusCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.RuleSuggestionOperationCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgement;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgementCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportCommand;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewOperationsReport;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SupervisionAlertReviewControllerTest {

    @Test
    void detailStreamResponseCarriesOnlyExplicitRecordPlaybackContext() {
        LocalDateTime happenedAt = LocalDateTime.of(2026, 7, 13, 10, 0, 20);
        LocalDateTime recordStart = happenedAt.minusSeconds(20);

        DetailStreamRespVO response = DetailStreamRespVO.from(new ReviewDetailStreamItem(
                22L,
                "alert-22",
                "camera-02",
                "zone-a",
                null,
                "person",
                "record",
                happenedAt,
                happenedAt,
                List.of(),
                List.of(),
                "record",
                "prerecord.mp4",
                Map.of("source", "timeline"),
                recordStart,
                20
        ));

        assertEquals(recordStart, response.getRecordStartTime());
        assertEquals(20, response.getPlaybackOffsetSeconds());
    }

    @Test
    void recordCoverageTimelineResponseCarriesItsOwnRecordStartTime() {
        LocalDateTime segmentStart = LocalDateTime.of(2026, 7, 13, 9, 15, 30);

        CaseTimelineRespVO response = CaseTimelineRespVO.from(new ReviewCaseTimelineItem(
                11L,
                22L,
                "camera-02",
                "alert-02",
                "record_coverage",
                "segment-02.mp4",
                segmentStart,
                "available"
        ));

        assertEquals("segment-02.mp4", response.getMaterialUri());
        assertEquals(segmentStart, response.getRecordStartTime());
    }

    @Test
    void recordTimelineResponseCarriesExplicitPlaybackContext() {
        LocalDateTime recordStart = LocalDateTime.of(2026, 7, 2, 8, 0);

        CaseTimelineRespVO response = CaseTimelineRespVO.from(new ReviewCaseTimelineItem(
                501L,
                101L,
                "cam-east-gate",
                "frigate-event-1",
                "record",
                "/video/record/east-gate-080000.mp4",
                recordStart.plusSeconds(10),
                "primary evidence",
                recordStart,
                10
        ));

        assertEquals(recordStart, response.getRecordStartTime());
        assertEquals(10, responsePlaybackOffsetSeconds(response));
    }

    @Test
    void reviewItemCaseEndpointReturnsCurrentCaseAndKeepsEmptyResultNullable() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();

        mockMvc.perform(get("/system/supervision/alert-review/items/101/case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(501))
                .andExpect(jsonPath("$.data.reviewItemIds[0]").value(101));
        assertEquals(101L, reviewService.command("findReviewCaseByItem"));

        mockMvc.perform(get("/system/supervision/alert-review/items/999/case"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertEquals(999L, reviewService.command("findReviewCaseByItem"));
    }

    @Test
    void legacySemanticReindexEndpointReturnsDeferredActiveClaimWithoutForcingIndexedState() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();

        mockMvc.perform(post("/system/supervision/alert-review/semantic-index/reindex")
                        .param("cameraId", "camera-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].reviewItemId").value(1001))
                .andExpect(jsonPath("$.data[0].indexStatus").value("processing"));

        ReviewQuery query = (ReviewQuery) reviewService.command("reindexSemanticIndex");
        assertEquals("camera-01", query.cameraId());
    }

    @Test
    void semanticTriggerEndpointsUseLoginOperatorAndExposePreviewOnlyConfirmationContract() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(782L),
                "unused",
                List.of()
        ));

        try {
            mockMvc.perform(post("/system/supervision/alert-review/semantic-triggers/evaluate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "triggerName": "helmet-doorway",
                                      "cameraId": "camera-01",
                                      "triggerType": "description",
                                      "data": "helmet doorway",
                                      "threshold": 0.5,
                                      "actions": ["notification"],
                                      "operatorUserId": 999999
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.evaluationId")
                            .value("sem-123e4567-e89b-42d3-a456-426614174000"))
                    .andExpect(jsonPath("$.data.humanConfirmationStatus").value("pending"))
                    .andExpect(jsonPath("$.data.inputVersion").value("semantic-trigger-input-v1"))
                    .andExpect(jsonPath("$.data.latestIndexVersion").value(1))
                    .andExpect(jsonPath("$.data.hitExplanations[0].reviewItemId").value(1001))
                    .andExpect(jsonPath("$.data.hitExplanations[0].indexVersion").value(1))
                    .andExpect(jsonPath("$.data.actionPreviews[0].previewOnly").value(true));

            SupervisionAlertReviewService.ReviewSemanticTriggerCommand evaluationCommand =
                    (SupervisionAlertReviewService.ReviewSemanticTriggerCommand)
                            reviewService.command("evaluateSemanticTrigger");
            assertEquals(782L, evaluationCommand.operatorUserId());

            mockMvc.perform(get("/system/supervision/alert-review/semantic-triggers/sem-123e4567-e89b-42d3-a456-426614174000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.evaluationId")
                            .value("sem-123e4567-e89b-42d3-a456-426614174000"))
                    .andExpect(jsonPath("$.data.humanConfirmationStatus").value("pending"))
                    .andExpect(jsonPath("$.data.inputVersion").value("semantic-trigger-input-v1"))
                    .andExpect(jsonPath("$.data.latestIndexVersion").value(1));

            mockMvc.perform(post("/system/supervision/alert-review/semantic-triggers/sem-123e4567-e89b-42d3-a456-426614174000/confirmation")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "confirmationStatus": "confirmed",
                                      "notes": "preview approved",
                                      "operatorUserId": 999999,
                                      "actionPreviews": [{"action": "execute-client-payload"}]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.humanConfirmationStatus").value("confirmed"))
                    .andExpect(jsonPath("$.data.confirmedBy").value(782))
                    .andExpect(jsonPath("$.data.inputVersion").value("semantic-trigger-input-v1"))
                    .andExpect(jsonPath("$.data.latestIndexVersion").value(1))
                    .andExpect(jsonPath("$.data.actionPreviews[0].action").value("notification"));

            SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand confirmationCommand =
                    (SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand)
                            reviewService.command("confirmSemanticTrigger");
            assertEquals(782L, confirmationCommand.operatorUserId());
            assertEquals("preview approved", confirmationCommand.notes());
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertPreAuthorize(
                "evaluateSemanticTrigger",
                new Class<?>[]{SemanticTriggerEvaluationReqVO.class},
                "system:supervision-alert-review:semantic-trigger:evaluate"
        );
        assertPreAuthorize(
                "confirmSemanticTrigger",
                new Class<?>[]{String.class, SemanticTriggerConfirmationReqVO.class},
                "system:supervision-alert-review:semantic-trigger:confirm"
        );
        assertPreAuthorizeExpression(
                "getSemanticTrigger",
                new Class<?>[]{String.class},
                "@ss.hasAnyPermissions('system:supervision-alert-review:semantic-trigger:evaluate','system:supervision-alert-review:semantic-trigger:confirm')"
        );
    }

    @Test
    void unifiedEvidenceAuditLookupMapsFourIntersectingKeys() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();

        mockMvc.perform(get("/system/supervision/alert-review/evidence-audit")
                        .param("eventId", "7001")
                        .param("reviewCaseId", "3001")
                        .param("reviewItemId", "1001")
                        .param("exportJobNo", "REJ-7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].reviewCaseId").value(3001))
                .andExpect(jsonPath("$.data[0].reviewItemId").value(1001))
                .andExpect(jsonPath("$.data[0].jobNo").value("REJ-7001"))
                .andExpect(jsonPath("$.data[0].metadata.entryHash").value("sha256:lookup"));

        assertEquals(
                new ReviewEvidenceAuditQuery(7001L, 3001L, 1001L, "REJ-7001"),
                reviewService.command("queryEvidenceAuditTrail")
        );
    }

    @Test
    void caseLifecycleEndpointsMapHttpRequestsToServiceCommands() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();

        mockMvc.perform(post("/system/supervision/alert-review/cases/10/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ownerUserId": 2001,
                                  "operatorUserId": 9001,
                                  "notes": "handoff",
                                  "expectedVersion": 3,
                                  "operationId": "owner-http-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.ownerUserId").value(2001))
                .andExpect(jsonPath("$.data.version").value(7));

        mockMvc.perform(post("/system/supervision/alert-review/cases/10/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorUserId": 9002,
                                  "notes": "resolved",
                                  "expectedVersion": 4,
                                  "operationId": "close-http-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("closed"));

        mockMvc.perform(post("/system/supervision/alert-review/cases/10/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sourceReviewCaseId": 11,
                                  "operatorUserId": 9003,
                                  "notes": "same lead",
                                  "targetExpectedVersion": 5,
                                  "sourceExpectedVersion": 2,
                                  "operationId": "merge-http-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.targetCase.id").value(10))
                .andExpect(jsonPath("$.data.sourceCase.status").value("merged"));

        mockMvc.perform(post("/system/supervision/alert-review/cases/10/split")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reviewItemIds": [103],
                                  "title": "camera-03 follow-up",
                                  "ownerUserId": 2002,
                                  "operatorUserId": 9004,
                                  "notes": "separate lead",
                                  "sourceExpectedVersion": 6,
                                  "operationId": "split-http-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sourceCase.id").value(10))
                .andExpect(jsonPath("$.data.newCase.ownerUserId").value(2002));

        assertEquals(new ReviewCaseOwnerCommand(10L, 2001L, 9001L, "handoff", 3, "owner-http-1"),
                reviewService.command("assignReviewCaseOwner"));
        assertEquals(new ReviewCaseOperationCommand(10L, 9002L, "resolved", 4, "close-http-1"),
                reviewService.command("closeReviewCase"));
        assertEquals(new ReviewCaseMergeCommand(10L, 11L, 9003L, "same lead", 5, 2, "merge-http-1"),
                reviewService.command("mergeReviewCases"));
        assertEquals(new ReviewCaseSplitCommand(10L, List.of(103L), "camera-03 follow-up", 2002L, 9004L,
                        "separate lead", 6, "split-http-1"),
                reviewService.command("splitReviewCase"));
    }

    @Test
    void evidencePackageVerificationUsesLoginUserInsteadOfRequestOperator() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(777L),
                null,
                List.of()
        ));
        try {
            mockMvc.perform(get("/system/supervision/alert-review/evidence-export-jobs/JOB-1/verify")
                            .param("operatorUserId", "9999")
                            .param("allowedCameraIds", "camera-01", "camera-02"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.operatorUserId").value(777));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(new ReviewEvidenceVerificationCommand("JOB-1", 777L, List.of("camera-01", "camera-02")),
                reviewService.command("verifyEvidencePackage"));
    }

    @Test
    void evidenceDownloadProxiesVerifiedBytesAndUsesLoginUser() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(778L),
                null,
                List.of()
        ));
        byte[] responseBytes;
        try {
            MvcResult streaming = mockMvc.perform(get("/system/supervision/alert-review/evidence-export-jobs/JOB-2/download")
                            .param("operatorUserId", "9999")
                            .param("allowedCameraIds", "camera-01")
                            .param("reason", "download for regulator"))
                    .andExpect(request().asyncStarted())
                    .andReturn();
            responseBytes = mockMvc.perform(asyncDispatch(streaming))
                    .andExpect(status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .header().string("X-Content-SHA256", "sha256:" + "a".repeat(64)))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertArrayEquals("real-package".getBytes(java.nio.charset.StandardCharsets.UTF_8), responseBytes);
        assertNotNull(reviewService.lastDownloadFile());
        assertEquals(false, Files.exists(reviewService.lastDownloadFile()),
                "stream completion must delete the temporary evidence artifact");
        assertEquals(new ReviewEvidenceDownloadCommand(
                        "JOB-2", 778L, List.of("camera-01"), "download for regulator"),
                reviewService.command("downloadEvidencePackage"));
    }

    @Test
    void auditOnlyEvidenceDownloadEndpointIsGoneAndCannotReturnSuccessAudit() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();

        mockMvc.perform(post("/system/supervision/alert-review/evidence-export-jobs/JOB-audit-only/downloads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorUserId": 9001,
                                  "allowedCameraIds": ["camera-01"],
                                  "reason": "audit without bytes"
                                }
                                """))
                .andExpect(status().isGone());

        assertEquals("JOB-audit-only", reviewService.command("recordEvidenceDownload"));
    }

    @Test
    void integrationSmokeUsesLoginUserAndPassesRealCameraScope() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(780L),
                null,
                List.of()
        ));
        try {
            mockMvc.perform(post("/system/supervision/alert-review/integration-smoke")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "operatorUserId": 9999,
                                      "includeVideoExport": true,
                                      "alertTime": "2026-07-10T06:11:30",
                                      "profile": "device-video-web",
                                      "deviceId": "camera-real",
                                      "cameraId": "camera-real",
                                      "zoneCode": "zone-real",
                                      "sourceAlertId": "alert-real",
                                      "allowedCameraIds": ["camera-real"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.operatorUserId").value(780))
                    .andExpect(jsonPath("$.data.eventId").value(7604))
                    .andExpect(jsonPath("$.data.videoExportConfirmed").value(true));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(new ReviewIntegrationSmokeCommand(
                        780L,
                        true,
                        LocalDateTime.of(2026, 7, 10, 6, 11, 30),
                        "device-video-web",
                        "camera-real",
                        "camera-real",
                        "zone-real",
                        "alert-real",
                        List.of("camera-real")
                ),
                reviewService.command("runIntegrationSmoke"));
    }

    @Test
    void reviewMutationsUseLoginUserInsteadOfBodyIdentity() {
        CapturingReviewService reviewService = new CapturingReviewService();
        SupervisionAlertReviewController controller = new SupervisionAlertReviewController(reviewService.proxy());
        OperationReqVO operation = new OperationReqVO();
        operation.setReviewerUserId(9999L);
        operation.setReason("review reason");
        UserStatusReqVO userStatus = new UserStatusReqVO();
        userStatus.setUserId(9998L);
        userStatus.setHasBeenReviewed(true);
        RuleSuggestionStatusReqVO suggestion = new RuleSuggestionStatusReqVO();
        suggestion.setReviewerUserId(9997L);
        suggestion.setStatus("approved");
        suggestion.setNote("approval note");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(781L),
                null,
                List.of()
        ));
        try {
            assertThrows(AssertionError.class, () -> controller.markReviewed(100L, operation));
            assertThrows(AssertionError.class, () -> controller.markUserReviewStatus(100L, userStatus));
            assertThrows(AssertionError.class, () -> controller.ignore(100L, operation));
            assertThrows(AssertionError.class, () -> controller.markFalsePositive(100L, operation));
            assertThrows(AssertionError.class, () -> controller.updateRuleSuggestionStatus(100L, suggestion));
            assertThrows(AssertionError.class, () -> controller.revertRuleSuggestion(100L, suggestion));
            assertThrows(AssertionError.class, () -> controller.convertToEvent(100L, operation));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(new ReviewOperationCommand(100L, 781L, "review reason"),
                reviewService.command("markReviewed"));
        assertEquals(new ReviewUserStatusCommand(100L, 781L, true),
                reviewService.command("markUserReviewStatus"));
        assertEquals(new ReviewOperationCommand(100L, 781L, "review reason"),
                reviewService.command("ignore"));
        assertEquals(new ReviewOperationCommand(100L, 781L, "review reason"),
                reviewService.command("markFalsePositive"));
        assertEquals(new RuleSuggestionOperationCommand(100L, 781L, "approved", "approval note"),
                reviewService.command("updateRuleSuggestionStatus"));
        assertEquals(new RuleSuggestionOperationCommand(100L, 781L, "approved", "approval note"),
                reviewService.command("revertRuleSuggestion"));
        assertEquals(new ReviewToEventCommand(100L, 781L),
                reviewService.command("convertToEvent"));
    }

    @Test
    void playbackUrlEndpointUsesLoginUserAndPreparesAuditedPlayback() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(778L),
                null,
                List.of()
        ));
        try {
            mockMvc.perform(get("/system/supervision/alert-review/items/100/playback-url")
                            .param("reviewCaseId", "10")
                            .param("operatorUserId", "9999")
                            .param("materialUri", "clip.mp4")
                            .param("allowedCameraIds", "camera-01")
                            .param("reason", "open playback"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.operatorUserId").value(778))
                    .andExpect(jsonPath("$.data.playbackUrl").value("clip.mp4"))
                    .andExpect(jsonPath("$.data.decision").value("granted"));
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(new ReviewPlaybackCommand(10L, 100L, 778L, "clip.mp4", List.of("camera-01"), "open playback"),
                reviewService.command("prepareReviewPlayback"));
    }

    @Test
    void operationsReportEndpointsExposeAcknowledgementContractAndUseLoginUser() throws Exception {
        CapturingReviewService reviewService = new CapturingReviewService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new SupervisionAlertReviewController(reviewService.proxy()))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser().setId(779L),
                null,
                List.of()
        ));
        try {
            mockMvc.perform(post("/system/supervision/alert-review/operations-report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "reportType": "daily",
                                      "reviewStatus": "pending_review",
                                      "cameraId": "camera-01",
                                      "periodStart": "2026-07-08T00:00:00",
                                      "periodEnd": "2026-07-08T23:59:59",
                                      "operatorUserId": 9999
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.reportType").value("daily"))
                    .andExpect(jsonPath("$.data.deliveryPlan.reportKey").value("report-controller"))
                    .andExpect(jsonPath("$.data.acknowledgement.status").value("pending"));

            mockMvc.perform(post("/system/supervision/alert-review/operations-report/acknowledgement")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "reportType": "daily",
                                      "reviewStatus": "pending_review",
                                      "cameraId": "camera-01",
                                      "periodStart": "2026-07-08T00:00:00",
                                      "periodEnd": "2026-07-08T23:59:59",
                                      "operatorUserId": 9999,
                                      "note": "shift leader acknowledged"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.reportKey").value("report-controller"))
                    .andExpect(jsonPath("$.data.status").value("acknowledged"))
                    .andExpect(jsonPath("$.data.acknowledgedBy").value(779))
                    .andExpect(jsonPath("$.data.duplicate").value(false));
        } finally {
            SecurityContextHolder.clearContext();
        }

        ReviewQuery expectedQuery = new ReviewQuery(
                "pending_review",
                "camera-01",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        assertEquals(new ReviewReportCommand(
                        "daily",
                        expectedQuery,
                        LocalDateTime.of(2026, 7, 8, 0, 0),
                        LocalDateTime.of(2026, 7, 8, 23, 59, 59),
                        779L
                ),
                reviewService.command("generateReviewReport"));
        assertEquals(new ReviewReportAcknowledgementCommand(
                        "daily",
                        expectedQuery,
                        LocalDateTime.of(2026, 7, 8, 0, 0),
                        LocalDateTime.of(2026, 7, 8, 23, 59, 59),
                        779L,
                        "shift leader acknowledged"
                ),
                reviewService.command("acknowledgeReviewReport"));
    }

    @Test
    void ruleSuggestionGovernanceEndpointsDeclareApprovalPermissions() throws Exception {
        assertPreAuthorize(
                "updateRuleSuggestionStatus",
                new Class<?>[]{Long.class, RuleSuggestionStatusReqVO.class},
                "system:supervision-alert-review:rule-suggestion:update"
        );
        assertPreAuthorize(
                "revertRuleSuggestion",
                new Class<?>[]{Long.class, RuleSuggestionStatusReqVO.class},
                "system:supervision-alert-review:rule-suggestion:revert"
        );
        assertPreAuthorize(
                "replayRule",
                new Class<?>[]{RuleReplayReqVO.class},
                "system:supervision-alert-review:rules:replay"
        );
    }

    @Test
    void mediaEndpointsDeclareSeededPermissions() throws Exception {
        assertPreAuthorize(
                "convertToEvent",
                new Class<?>[]{Long.class, OperationReqVO.class},
                "supervision:event:create"
        );
        assertPreAuthorizeExpression(
                "findReviewCaseByItem",
                new Class<?>[]{Long.class},
                "@ss.hasAnyPermissions('system:supervision-alert-review:media:playback','system:supervision-alert-review:media:snapshot')"
        );
        assertPreAuthorize(
                "getRecordCoverage",
                new Class<?>[]{Long.class, Long.class, Long.class, List.class},
                "system:supervision-alert-review:media:playback"
        );
        assertPreAuthorize(
                "preparePlaybackUrl",
                new Class<?>[]{Long.class, Long.class, Long.class, String.class, List.class, String.class},
                "system:supervision-alert-review:media:playback"
        );
        assertPreAuthorize(
                "getDetailStream",
                new Class<?>[]{Long.class, Long.class, Long.class, List.class},
                "system:supervision-alert-review:media:playback"
        );
        assertPreAuthorizeExpression(
                "getTimeline",
                new Class<?>[]{Long.class, Long.class, Long.class, List.class},
                "@ss.hasAnyPermissions('system:supervision-alert-review:media:playback','system:supervision-alert-review:media:snapshot')"
        );
        assertPreAuthorizeExpression(
                "getReviewCaseTimeline",
                new Class<?>[]{Long.class, Long.class, List.class},
                "@ss.hasAnyPermissions('system:supervision-alert-review:media:playback','system:supervision-alert-review:media:snapshot')"
        );
        assertPreAuthorize(
                "exportReviewEvidence",
                new Class<?>[]{Long.class, EvidenceExportReqVO.class},
                "system:supervision-alert-review:media:export"
        );
        assertPreAuthorize(
                "createReviewEvidenceExportJob",
                new Class<?>[]{Long.class, EvidenceExportReqVO.class},
                "system:supervision-alert-review:media:export"
        );
        assertPreAuthorize(
                "verifyEvidenceExportManifest",
                new Class<?>[]{String.class, Long.class, List.class},
                "system:supervision-alert-review:media:manifest"
        );
        assertPreAuthorize(
                "verifyEvidencePackage",
                new Class<?>[]{String.class, Long.class, List.class},
                "system:supervision-alert-review:media:manifest"
        );
        assertPreAuthorize(
                "queryEvidenceAuditTrail",
                new Class<?>[]{Long.class, Long.class, Long.class, String.class},
                "system:supervision-alert-review:media:manifest"
        );
        assertPreAuthorize(
                "recordEvidenceDownload",
                new Class<?>[]{String.class, EvidenceDownloadAuditReqVO.class},
                "system:supervision-alert-review:media:download"
        );
        assertPreAuthorize(
                "downloadEvidenceExportPackage",
                new Class<?>[]{String.class, Long.class, List.class, String.class},
                "system:supervision-alert-review:media:download"
        );
        assertPreAuthorizeExpression(
                "runIntegrationSmoke",
                new Class<?>[]{IntegrationSmokeReqVO.class},
                "@ss.hasPermission('system:supervision-alert-review:media:playback')"
                        + " and @ss.hasPermission('system:supervision-alert-review:media:export')"
                        + " and @ss.hasPermission('system:supervision-alert-review:media:manifest')"
                        + " and @ss.hasPermission('system:supervision-alert-review:media:download')"
                        + " and @ss.hasPermission('supervision:event:create')"
        );
    }

    private static void assertPreAuthorize(String methodName,
                                           Class<?>[] parameterTypes,
                                           String permission) throws NoSuchMethodException {
        assertPreAuthorizeExpression(methodName, parameterTypes, "@ss.hasPermission('" + permission + "')");
    }

    private static void assertPreAuthorizeExpression(String methodName,
                                                     Class<?>[] parameterTypes,
                                                     String expression) throws NoSuchMethodException {
        Method method = SupervisionAlertReviewController.class.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize, methodName + " must declare @PreAuthorize");
        assertEquals(expression, preAuthorize.value());
    }

    private static ReviewCaseView caseView(Long id,
                                           String status,
                                           List<Long> reviewItemIds,
                                           Long ownerUserId,
                                           String notes) {
        return new ReviewCaseView(
                id,
                "RC-" + id,
                "case-" + id,
                status,
                reviewItemIds.isEmpty() ? null : reviewItemIds.get(0),
                reviewItemIds,
                List.of("camera-01"),
                LocalDateTime.of(2026, 7, 3, 18, 0),
                LocalDateTime.of(2026, 7, 3, 18, 5),
                ownerUserId,
                notes,
                7
        );
    }

    private static final class CapturingReviewService implements InvocationHandler {

        private final Map<String, Object> commands = new LinkedHashMap<>();
        private Path lastDownloadFile;

        private SupervisionAlertReviewService proxy() {
            return (SupervisionAlertReviewService) Proxy.newProxyInstance(
                    SupervisionAlertReviewService.class.getClassLoader(),
                    new Class<?>[]{SupervisionAlertReviewService.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> "CapturingReviewService";
                    case "hashCode" -> System.identityHashCode(this);
                    case "equals" -> proxy == args[0];
                    default -> null;
                };
            }
            Object command = args == null || args.length == 0 ? null : args[0];
            commands.put(method.getName(), command);
            return switch (method.getName()) {
                case "findReviewCaseByItem" -> Long.valueOf(101L).equals(command)
                        ? Optional.of(caseView(501L, "open", List.of(101L), 2001L, "existing"))
                        : Optional.empty();
                case "assignReviewCaseOwner" -> caseView(10L, "open", List.of(101L, 102L), 2001L, "handoff");
                case "closeReviewCase" -> caseView(10L, "closed", List.of(101L, 102L), 2001L, "resolved");
                case "mergeReviewCases" -> new ReviewCaseMergeResult(
                        caseView(10L, "open", List.of(101L, 102L, 103L), 2001L, "target"),
                        caseView(11L, "merged", List.of(), 2003L, "same lead")
                );
                case "splitReviewCase" -> new ReviewCaseSplitResult(
                        caseView(10L, "open", List.of(101L, 102L), 2001L, "source"),
                        caseView(12L, "open", List.of(103L), 2002L, "separate lead")
                );
                case "prepareReviewPlayback" -> playbackAccess((ReviewPlaybackCommand) command);
                case "downloadEvidencePackage" -> {
                    ReviewEvidenceDownloadArtifact artifact = downloadArtifact((ReviewEvidenceDownloadCommand) command);
                    lastDownloadFile = artifact.temporaryFile();
                    yield artifact;
                }
                case "recordEvidenceDownload" -> new ReviewEvidenceAuditEntry(
                        10L,
                        null,
                        "media_access_denied",
                        String.valueOf(command),
                        null,
                        9001L,
                        "audit without bytes",
                        List.of(),
                        List.of(),
                        LocalDateTime.of(2026, 7, 11, 18, 0),
                        Map.of("deniedReasons", List.of("audit_only_endpoint_disabled"))
                );
                case "verifyEvidencePackage" -> verificationReport((ReviewEvidenceVerificationCommand) command);
                case "queryEvidenceAuditTrail" -> evidenceAuditEntries((ReviewEvidenceAuditQuery) command);
                case "evaluateSemanticTrigger" -> semanticTriggerResult(
                        (SupervisionAlertReviewService.ReviewSemanticTriggerCommand) command,
                        "pending",
                        null,
                        null
                );
                case "getSemanticTrigger" -> semanticTriggerResult(null, "pending", null, null);
                case "confirmSemanticTrigger" -> semanticTriggerResult(
                        null,
                        "confirmed",
                        ((SupervisionAlertReviewService.ReviewSemanticTriggerConfirmationCommand) command).operatorUserId(),
                        LocalDateTime.of(2026, 7, 11, 12, 30)
                );
                case "reindexSemanticIndex" -> List.of(new SupervisionAlertReviewService.ReviewSemanticIndexEntry(
                        1001L,
                        "camera-01",
                        LocalDateTime.of(2026, 7, 13, 9, 30),
                        LocalDateTime.of(2026, 7, 13, 9, 30),
                        "processing",
                        "camera-01 person",
                        "camera-01:1001",
                        "local-hash-v1",
                        null,
                        0,
                        null,
                        null,
                        "sig-active",
                        "active-claim",
                        LocalDateTime.of(2026, 7, 13, 9, 31),
                        LocalDateTime.of(2026, 7, 13, 9, 36),
                        null,
                        1
                ));
                case "runIntegrationSmoke" -> integrationSmokeResult((ReviewIntegrationSmokeCommand) command);
                case "generateReviewReport" -> operationsReport((ReviewReportCommand) command);
                case "acknowledgeReviewReport" -> reportAcknowledgement((ReviewReportAcknowledgementCommand) command);
                default -> throw new AssertionError("unexpected service method: " + method.getName());
            };
        }

        private Object command(String methodName) {
            return commands.get(methodName);
        }

        private Path lastDownloadFile() {
            return lastDownloadFile;
        }

    }

    private static List<ReviewEvidenceAuditEntry> evidenceAuditEntries(ReviewEvidenceAuditQuery query) {
        return List.of(new ReviewEvidenceAuditEntry(
                query.reviewCaseId(),
                query.reviewItemId(),
                "export_downloaded",
                query.exportJobNo(),
                "sha256:" + "a".repeat(64),
                9001L,
                "reverse lookup",
                List.of("evidence.mp4"),
                List.of(query.eventId()),
                LocalDateTime.of(2026, 7, 11, 12, 0),
                Map.of("entryHash", "sha256:lookup", "previousHash", "GENESIS")
        ));
    }

    private static SupervisionAlertReviewService.ReviewSemanticTriggerResult semanticTriggerResult(
            SupervisionAlertReviewService.ReviewSemanticTriggerCommand command,
            String confirmationStatus,
            Long confirmedBy,
            LocalDateTime confirmedAt) {
        return new SupervisionAlertReviewService.ReviewSemanticTriggerResult(
                command == null ? "helmet-doorway" : command.triggerName(),
                command == null ? "description" : command.triggerType(),
                command == null ? "helmet doorway" : command.data(),
                List.of(1001L),
                List.of(Map.of("action", "notification", "reviewItemId", 1001L)),
                LocalDateTime.of(2026, 7, 11, 12, 0),
                "semantic-trigger-input-v1",
                1,
                List.of(Map.of(
                        "reviewItemId", 1001L,
                        "reason", "semantic match",
                        "indexVersion", 1
                )),
                List.of(Map.of(
                        "action", "notification",
                        "reviewItemId", 1001L,
                        "previewOnly", true,
                        "requiresHumanConfirmation", true
                )),
                confirmationStatus,
                "sem-123e4567-e89b-42d3-a456-426614174000",
                confirmedBy,
                confirmedAt,
                false
        );
    }

    private static ReviewPlaybackAccess playbackAccess(ReviewPlaybackCommand command) {
        ReviewMediaAccessAuditEntry audit = new ReviewMediaAccessAuditEntry(
                command.reviewCaseId(),
                command.reviewItemId(),
                command.operatorUserId(),
                "camera-01",
                command.materialUri(),
                "playback",
                "granted",
                List.of(),
                LocalDateTime.of(2026, 7, 6, 10, 1),
                Map.of("decision", "granted")
        );
        return new ReviewPlaybackAccess(
                command.reviewCaseId(),
                command.reviewItemId(),
                command.operatorUserId(),
                "camera-01",
                command.materialUri(),
                command.materialUri(),
                "granted",
                List.of(),
                audit
        );
    }

    private static ReviewEvidenceDownloadArtifact downloadArtifact(ReviewEvidenceDownloadCommand command) {
        try {
            byte[] bytes = "real-package".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            Path file = Files.createTempFile("alert-review-controller-test-", ".mp4");
            Files.write(file, bytes);
            return new ReviewEvidenceDownloadArtifact(
                    command.jobNo(),
                    command.jobNo() + ".mp4",
                    "video/mp4",
                    file,
                    bytes.length,
                    "sha256:" + "a".repeat(64),
                    null
            );
        } catch (Exception exception) {
            throw new IllegalStateException("unable to create controller test artifact", exception);
        }
    }

    private static ReviewEvidenceVerificationReport verificationReport(ReviewEvidenceVerificationCommand command) {
        ReviewManifestVerification manifestVerification = new ReviewManifestVerification(
                command.jobNo(),
                true,
                "sha256:expected",
                "sha256:expected",
                "sha256:package",
                List.of(),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        );
        return new ReviewEvidenceVerificationReport(
                command.jobNo(),
                true,
                manifestVerification,
                Map.of(),
                List.of(),
                List.of("manifest_hash_valid"),
                List.of(),
                LocalDateTime.of(2026, 7, 6, 10, 0),
                command.operatorUserId()
        );
    }

    private static ReviewIntegrationSmokeResult integrationSmokeResult(ReviewIntegrationSmokeCommand command) {
        return new ReviewIntegrationSmokeResult(
                "passed",
                100L,
                7604L,
                10L,
                "JOB-SMOKE",
                true,
                true,
                true,
                List.of("video_export_confirmed"),
                LocalDateTime.of(2026, 7, 10, 6, 12),
                command.operatorUserId(),
                command.profile(),
                null
        );
    }

    private static ReviewOperationsReport operationsReport(ReviewReportCommand command) {
        Map<String, Object> deliveryPlan = Map.of(
                "reportKey", "report-controller",
                "reportType", command.reportType(),
                "deliveryStatus", "pending"
        );
        Map<String, Object> acknowledgement = Map.of(
                "reportKey", "report-controller",
                "reportType", command.reportType(),
                "status", "pending",
                "required", true
        );
        return new ReviewOperationsReport(
                command.reportType(),
                List.of(100L),
                "daily review report",
                "1 pending clue",
                List.of("missing_record"),
                List.of("acknowledge"),
                LocalDateTime.of(2026, 7, 8, 12, 0),
                command.operatorUserId(),
                Map.of("acknowledgement", acknowledgement, "deliveryPlan", deliveryPlan),
                deliveryPlan,
                acknowledgement
        );
    }

    private static ReviewReportAcknowledgement reportAcknowledgement(ReviewReportAcknowledgementCommand command) {
        return new ReviewReportAcknowledgement(
                "report-controller",
                command.reportType(),
                "acknowledged",
                command.operatorUserId(),
                LocalDateTime.of(2026, 7, 8, 12, 5),
                command.note(),
                false,
                Map.of("periodStart", String.valueOf(command.periodStart()))
        );
    }

    private static Object responsePlaybackOffsetSeconds(CaseTimelineRespVO response) {
        try {
            return response.getClass().getMethod("getPlaybackOffsetSeconds").invoke(response);
        } catch (NoSuchMethodException exception) {
            return null;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

}
