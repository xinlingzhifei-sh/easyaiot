package com.basiclab.iot.node.dal.pgsql;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.node.dal.dataobject.NodeAgentCommandDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface NodeAgentCommandMapper extends BaseMapperX<NodeAgentCommandDO> {

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

    default List<NodeAgentCommandDO> selectTimedOutRunning(LocalDateTime now, int limit) {
        return selectList(new LambdaQueryWrapperX<NodeAgentCommandDO>()
                .eq(NodeAgentCommandDO::getStatus, "running")
                .lt(NodeAgentCommandDO::getLeaseUntil, now)
                .orderByAsc(NodeAgentCommandDO::getUpdateTime)
                .last("LIMIT " + Math.max(1, Math.min(limit, 200))));
    }
}
