package com.basiclab.iot.node.controller;

import com.basiclab.iot.common.core.aop.TenantIgnore;
import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandAckReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandEnqueueReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandPollReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandRespVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCommandResultReqVO;
import com.basiclab.iot.node.service.NodeAgentCommandService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.List;

import static com.basiclab.iot.common.domain.CommonResult.success;

@Tag(name = "Agent - outbound command queue")
@RestController
@RequestMapping("/node/agent/commands")
@Validated
@Slf4j
public class NodeAgentCommandController {

    @Resource
    private NodeAgentCommandService nodeAgentCommandService;

    @PostMapping("/enqueue")
    @Operation(summary = "Enqueue an Agent command")
    public CommonResult<NodeAgentCommandRespVO> enqueue(@Valid @RequestBody NodeAgentCommandEnqueueReqVO reqVO) {
        return success(nodeAgentCommandService.enqueue(reqVO));
    }

    @PostMapping("/poll")
    @Operation(summary = "Poll Agent commands")
    @TenantIgnore
    public CommonResult<List<NodeAgentCommandRespVO>> poll(@Valid @RequestBody NodeAgentCommandPollReqVO reqVO) {
        return success(nodeAgentCommandService.poll(reqVO));
    }

    @PostMapping("/{commandId}/ack")
    @Operation(summary = "Ack command execution")
    @TenantIgnore
    public CommonResult<Boolean> ack(@PathVariable("commandId") Long commandId,
                                     @Valid @RequestBody NodeAgentCommandAckReqVO reqVO) {
        nodeAgentCommandService.ack(commandId, reqVO);
        return success(true);
    }

    @PostMapping("/{commandId}/heartbeat")
    @Operation(summary = "Extend command execution lease")
    @TenantIgnore
    public CommonResult<Boolean> heartbeat(@PathVariable("commandId") Long commandId,
                                           @Valid @RequestBody NodeAgentCommandAckReqVO reqVO) {
        nodeAgentCommandService.heartbeat(commandId, reqVO);
        return success(true);
    }

    @PostMapping("/{commandId}/result")
    @Operation(summary = "Report command result")
    @TenantIgnore
    public CommonResult<Boolean> reportResult(@PathVariable("commandId") Long commandId,
                                              @Valid @RequestBody NodeAgentCommandResultReqVO reqVO) {
        nodeAgentCommandService.reportResult(commandId, reqVO);
        return success(true);
    }
}
