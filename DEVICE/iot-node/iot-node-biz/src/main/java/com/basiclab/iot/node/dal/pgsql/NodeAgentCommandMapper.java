package com.basiclab.iot.node.dal.pgsql;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface NodeAgentCommandMapper extends BaseMapperX<NodeAgentCommandDO> {

    String STATUS_PENDING = "pending";
    String STATUS_SUCCEEDED = "succeeded";
    String STATUS_CANCELED = "canceled";
    /** 终态中除 succeeded 以外的状态 */
    Set<String> FINISHED_STATUSES = Set.of("failed", "superseded", STATUS_CANCELED);

    /**
     * 仍未被 Agent 领取时取消（控制面等待超时）；已领取的指令无法撤回，返回 0。
     */
    default int cancelIfPending(Long id) {
        return update(null, new LambdaUpdateWrapper<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getId, id)
                .eq(NodeAgentCommandDO::getStatus, STATUS_PENDING)
                .set(NodeAgentCommandDO::getStatus, STATUS_CANCELED)
                .set(NodeAgentCommandDO::getLastError, "agent_command_wait_timeout")
                .set(NodeAgentCommandDO::getFinishedAt, LocalDateTime.now()));
    }

    /**
     * 清空指令参数（部署参数含中间件凭据，同步调用结束后不留存）。
     */
    default int clearPayload(Long id) {
        return update(null, new LambdaUpdateWrapper<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getId, id)
                .set(NodeAgentCommandDO::getPayloadJson, "{}"));
    }

    default NodeAgentCommandDO selectActiveByCommandKey(Long nodeId, String commandKey) {
        return selectOne(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getNodeId, nodeId)
                .eq(NodeAgentCommandDO::getCommandKey, commandKey)
                .in(NodeAgentCommandDO::getStatus, List.of("pending", "leased", "running")));
    }

    /**
     * 按 commandKey 查最近一条指令（含已结束的），nodeId 为空时不按节点过滤。
     */
    default NodeAgentCommandDO selectLatestByCommandKey(String commandKey, Long nodeId) {
        return selectOne(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getCommandKey, commandKey)
                .eqIfPresent(NodeAgentCommandDO::getNodeId, nodeId)
                .orderByDesc(NodeAgentCommandDO::getId)
                .last("LIMIT 1"));
    }

    default List<NodeAgentCommandDO> selectPollable(Long nodeId, LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getNodeId, nodeId)
                .and(wrapper -> wrapper
                        .eq(NodeAgentCommandDO::getStatus, "pending")
                        .or()
                        .eq(NodeAgentCommandDO::getStatus, "leased")
                        .lt(NodeAgentCommandDO::getLeaseUntil, now))
                .orderByAsc(NodeAgentCommandDO::getCreateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 20))));
    }

    default List<NodeAgentCommandDO> selectPendingByType(Long nodeId, String commandType) {
        return selectList(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getNodeId, nodeId)
                .eq(NodeAgentCommandDO::getCommandType, commandType)
                .eq(NodeAgentCommandDO::getStatus, "pending"));
    }

    default List<NodeAgentCommandDO> selectTimedOutRunning(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getStatus, "running")
                .lt(NodeAgentCommandDO::getLeaseUntil, now)
                .orderByAsc(NodeAgentCommandDO::getUpdateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
