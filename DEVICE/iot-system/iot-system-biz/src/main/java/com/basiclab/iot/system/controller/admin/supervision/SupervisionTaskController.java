package com.basiclab.iot.system.controller.admin.supervision;

import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskAcceptReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskByEventReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskDetailReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskDetailRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskOperationRespVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskRecheckReqVO;
import com.basiclab.iot.system.controller.admin.supervision.vo.task.TaskSubmitReqVO;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.OperationResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskAcceptRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskByEventRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskDetailRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskDetailResponse;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskRecheckRequest;
import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.TaskSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

import static com.basiclab.iot.common.domain.CommonResult.success;

@Tag(name = "Admin - Supervision Task")
@RestController
@RequestMapping("/system/supervision/tasks")
@Validated
public class SupervisionTaskController {

    private final SupervisionWorkflowApplicationService supervisionWorkflowApplicationService;

    public SupervisionTaskController(SupervisionWorkflowApplicationService supervisionWorkflowApplicationService) {
        this.supervisionWorkflowApplicationService = supervisionWorkflowApplicationService;
    }

    @GetMapping("/get")
    @Operation(summary = "Get supervision task detail")
    public CommonResult<TaskDetailRespVO> getTaskDetail(@Valid TaskDetailReqVO reqVO) {
        TaskDetailResponse response = supervisionWorkflowApplicationService.getTaskDetail(new TaskDetailRequest(reqVO.getId()));
        return success(TaskDetailRespVO.from(response));
    }

    @GetMapping("/by-event")
    @Operation(summary = "Get current supervision task by event")
    public CommonResult<TaskDetailRespVO> getCurrentTaskByEvent(@Valid TaskByEventReqVO reqVO) {
        TaskDetailResponse response = supervisionWorkflowApplicationService.getCurrentTaskByEvent(new TaskByEventRequest(reqVO.getEventId()));
        return success(TaskDetailRespVO.from(response));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept supervision task")
    public CommonResult<TaskOperationRespVO> acceptTask(@Valid @RequestBody TaskAcceptReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.acceptTask(new TaskAcceptRequest(
                reqVO.getTaskId(),
                reqVO.getAcceptedUserId()
        ));
        return success(TaskOperationRespVO.from(response));
    }

    @PostMapping("/rework/restart")
    @Operation(summary = "Restart supervision task rework")
    public CommonResult<TaskOperationRespVO> restartRework(@Valid @RequestBody TaskAcceptReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.restartRework(new TaskAcceptRequest(
                reqVO.getTaskId(),
                reqVO.getAcceptedUserId()
        ));
        return success(TaskOperationRespVO.from(response));
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit supervision task handling result")
    public CommonResult<TaskOperationRespVO> submitTask(@Valid @RequestBody TaskSubmitReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.submitTask(new TaskSubmitRequest(
                reqVO.getTaskId(),
                reqVO.getResultCategory(),
                reqVO.getHandlingNote()
        ));
        return success(TaskOperationRespVO.from(response));
    }

    @PostMapping("/recheck/approve")
    @Operation(summary = "Approve supervision task recheck")
    public CommonResult<TaskOperationRespVO> approveRecheck(@Valid @RequestBody TaskRecheckReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.approveRecheck(new TaskRecheckRequest(
                reqVO.getTaskId()
        ));
        return success(TaskOperationRespVO.from(response));
    }

    @PostMapping("/recheck/reject")
    @Operation(summary = "Reject supervision task recheck")
    public CommonResult<TaskOperationRespVO> rejectRecheck(@Valid @RequestBody TaskRecheckReqVO reqVO) {
        OperationResponse response = supervisionWorkflowApplicationService.rejectRecheck(new TaskRecheckRequest(
                reqVO.getTaskId()
        ));
        return success(TaskOperationRespVO.from(response));
    }

}
