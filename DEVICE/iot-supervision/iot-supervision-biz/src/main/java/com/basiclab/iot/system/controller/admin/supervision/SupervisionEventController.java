package com.basiclab.iot.system.controller.admin.supervision;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.AlertEventCreateReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.AlertEventRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.CloseCheckReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.ClosureSummaryRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.EventDetailReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.EventDetailRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.EventEvidenceRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.EventOperationRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.event.EventTimelineRespVO;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.AlertEventResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.CloseCheckRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.ClosureSummaryRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.ClosureSummaryResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventDetailRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventDetailResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventEvidenceRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventTimelineRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.OperationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

import static com.basiclab.iot.common.domain.CommonResult.success;

@Tag(name = "管理后台 - 监管事件")
@RestController
@RequestMapping("/system/supervision/events")
@Validated
public class SupervisionEventController {

    private final SupervisionWorkflowApplicationService supervisionWorkflowApplicationService;

    public SupervisionEventController(SupervisionWorkflowApplicationService supervisionWorkflowApplicationService) {
        this.supervisionWorkflowApplicationService = supervisionWorkflowApplicationService;
    }

    @GetMapping("/get")
    @Operation(summary = "Get supervision event detail")
    @Parameter(name = "id", description = "Event ID", required = true, example = "1001")
    public CommonResult<EventDetailRespVO> getEventDetail(@Valid EventDetailReqVO reqVO) {
        EventDetailResponse response = supervisionWorkflowApplicationService.getEventDetail(new EventDetailRequest(reqVO.getId()));
        return success(EventDetailRespVO.from(response));
    }

    @GetMapping("/closure-summary")
    @Operation(summary = "Get supervision event closure summary")
    public CommonResult<ClosureSummaryRespVO> getClosureSummary(@Valid EventDetailReqVO reqVO) {
        ClosureSummaryResponse response = supervisionWorkflowApplicationService.getClosureSummary(
                new ClosureSummaryRequest(reqVO.getId())
        );
        return success(ClosureSummaryRespVO.from(response));
    }

    @GetMapping("/evidence")
    @Operation(summary = "Get supervision event evidence chain")
    public CommonResult<List<EventEvidenceRespVO>> getEventEvidence(@Valid EventDetailReqVO reqVO) {
        return success(supervisionWorkflowApplicationService.getEventEvidence(new EventEvidenceRequest(reqVO.getId()))
                .stream()
                .map(EventEvidenceRespVO::from)
                .toList());
    }

    @GetMapping("/timeline")
    @Operation(summary = "Get supervision event timeline")
    public CommonResult<List<EventTimelineRespVO>> getEventTimeline(@Valid EventDetailReqVO reqVO) {
        return success(supervisionWorkflowApplicationService.getEventTimeline(new EventTimelineRequest(reqVO.getId()))
                .stream()
                .map(EventTimelineRespVO::from)
                .toList());
    }

    @PostMapping("/from-alert")
    @PreAuthorize("@ss.hasPermission('supervision:event:create')")
    @Operation(summary = "告警转监管事件")
    public CommonResult<AlertEventRespVO> createEventFromAlert(@Valid @RequestBody AlertEventCreateReqVO reqVO) {
        AlertEventResponse response = supervisionWorkflowApplicationService.createEventFromAlert(new AlertEventRequest(
                reqVO.getSourceSystem(),
                reqVO.getSourceAlertId(),
                reqVO.getRuleCode(),
                reqVO.getSourceAlertType(),
                reqVO.getSourceAlertTime(),
                reqVO.getSourcePayloadHash()
        ));
        return success(AlertEventRespVO.from(response));
    }

    @PostMapping("/close-check/approve")
    @Operation(summary = "Approve supervision event close check")
    public CommonResult<EventOperationRespVO> approveCloseCheck(@Valid @RequestBody CloseCheckReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.approveCloseCheck(
                new CloseCheckRequest(reqVO.getEventId())
        );
        return success(EventOperationRespVO.from(response));
    }

    @PostMapping("/close-check/reject")
    @Operation(summary = "Reject supervision event close check")
    public CommonResult<EventOperationRespVO> rejectCloseCheck(@Valid @RequestBody CloseCheckReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.rejectCloseCheck(
                new CloseCheckRequest(reqVO.getEventId())
        );
        return success(EventOperationRespVO.from(response));
    }

}
