package com.basiclab.iot.node.controller;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.common.core.aop.TenantIgnore;
import com.basiclab.iot.common.domain.CommonResult;
import com.basiclab.iot.common.service.SecurityFrameworkService;
import com.basiclab.iot.node.domain.vo.NodeSentinelProbeReqVO;
import com.basiclab.iot.node.domain.vo.NodeSentinelRemediateReqVO;
import com.basiclab.iot.node.domain.vo.NodeSentinelRespVO;
import com.basiclab.iot.node.service.NodeSentinelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;

import static com.basiclab.iot.common.domain.CommonResult.success;
import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_TOKEN_INVALID;

@Tag(name = "管理后台 - 节点 Sentinel")
@RestController
@RequestMapping("/node/sentinel")
@Validated
public class NodeSentinelController {

    private static final String AGENT_TOKEN_HEADER = "X-Agent-Token";

    @Resource
    private NodeSentinelService nodeSentinelService;
    @Resource
    private SecurityFrameworkService securityFrameworkService;

    @GetMapping("/get")
    @Operation(summary = "获取节点 Sentinel 快照（组件 + 可调度能力）")
    @Parameter(name = "nodeId", description = "节点 ID", required = true)
    public CommonResult<NodeSentinelRespVO> getSnapshot(@RequestParam("nodeId") Long nodeId) {
        return success(nodeSentinelService.getSnapshot(nodeId));
    }

    @GetMapping("/registry")
    @Operation(summary = "获取 Sentinel 能力注册表（components + capabilities）")
    public CommonResult<Map<String, Object>> getRegistry() {
        return success(nodeSentinelService.getRegistry());
    }

    @PostMapping("/probe")
    @Operation(summary = "主动触发节点 Sentinel 探测（L0/L1/L2）")
    public CommonResult<NodeSentinelRespVO> probe(@Valid @RequestBody NodeSentinelProbeReqVO reqVO) {
        return success(nodeSentinelService.probe(reqVO));
    }

    @PostMapping("/resync")
    @Operation(summary = "重新同步节点 Sentinel 快照（等同 L1 探测）")
    public CommonResult<NodeSentinelRespVO> resync(@RequestParam("nodeId") Long nodeId) {
        return success(nodeSentinelService.resync(nodeId));
    }

    @GetMapping("/schedulable")
    @Operation(summary = "获取节点可调度能力布尔映射")
    @Parameter(name = "nodeId", description = "节点 ID", required = true)
    public CommonResult<Map<String, Boolean>> getSchedulable(@RequestParam("nodeId") Long nodeId) {
        return success(nodeSentinelService.getSchedulableMap(nodeId));
    }

    @PostMapping("/remediate")
    @Operation(summary = "触发组件自愈合（管理端或 Agent 调用）")
    @TenantIgnore
    public CommonResult<Map<String, Object>> remediate(
            @Valid @RequestBody NodeSentinelRemediateReqVO reqVO,
            HttpServletRequest request) {
        reqVO.setAgentToken(requireAgentTokenUnlessAdmin(reqVO.getAgentToken(), request));
        return success(nodeSentinelService.remediate(reqVO));
    }

    @PostMapping("/remediate-report")
    @Operation(summary = "Sentinel 汇报自愈结果与日志（Agent 限次失败后上报）")
    @TenantIgnore
    public CommonResult<Map<String, Object>> remediateReport(
            @Valid @RequestBody com.basiclab.iot.node.domain.vo.NodeSentinelRemediateReportReqVO reqVO,
            HttpServletRequest request) {
        reqVO.setAgentToken(requireAgentTokenUnlessAdmin(reqVO.getAgentToken(), request));
        return success(nodeSentinelService.reportRemediation(reqVO));
    }

    @GetMapping("/remediate-logs")
    @Operation(summary = "查询节点自愈日志")
    @Parameter(name = "nodeId", description = "节点 ID", required = true)
    public CommonResult<java.util.List<com.basiclab.iot.node.domain.vo.NodeSentinelRemediateLogRespVO>> remediateLogs(
            @RequestParam("nodeId") Long nodeId) {
        return success(nodeSentinelService.listRemediateLogs(nodeId));
    }

    /**
     * 自愈接口对 Agent 免登录放行：请求体未带 token 时取 X-Agent-Token 请求头；
     * 未登录管理后台的调用方必须带 token，token 值由业务层按节点校验。
     */
    private String requireAgentTokenUnlessAdmin(String bodyToken, HttpServletRequest request) {
        String token = StrUtil.isNotBlank(bodyToken) ? bodyToken : request.getHeader(AGENT_TOKEN_HEADER);
        if (StrUtil.isBlank(token)) {
            if (!securityFrameworkService.isAdminUser()) {
                throw exception(AGENT_TOKEN_INVALID);
            }
            return null;
        }
        return token;
    }
}
