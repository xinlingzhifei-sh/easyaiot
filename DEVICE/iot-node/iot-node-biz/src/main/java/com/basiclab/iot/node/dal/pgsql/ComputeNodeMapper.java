package com.basiclab.iot.node.dal.pgsql;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.common.domain.PageResult;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.domain.vo.ComputeNodePageReqVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface ComputeNodeMapper extends BaseMapperX<ComputeNodeDO> {

    default PageResult<ComputeNodeDO> selectPage(ComputeNodePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ComputeNodeDO>()
                .likeIfPresent(ComputeNodeDO::getName, reqVO.getName())
                .likeIfPresent(ComputeNodeDO::getHost, reqVO.getHost())
                .eqIfPresent(ComputeNodeDO::getStatus, reqVO.getStatus())
                .likeIfPresent(ComputeNodeDO::getNodeRole, reqVO.getFunction())
                .eqIfPresent(ComputeNodeDO::getRegion, reqVO.getRegion())
                .eqIfPresent(ComputeNodeDO::getControlPlaneId, reqVO.getControlPlaneId())
                .orderByDesc(ComputeNodeDO::getUpdateTime));
    }

    default ComputeNodeDO selectByHost(String host) {
        return selectOne(ComputeNodeDO::getHost, host);
    }

    default ComputeNodeDO selectPlatformNode() {
        return selectList().stream()
                .filter(node -> node.getCapabilities() != null
                        && Boolean.TRUE.equals(node.getCapabilities().get("platform")))
                .findFirst()
                .orElse(null);
    }

    /**
     * 写入 Agent 令牌状态（当前/上一令牌、pending、代次）。仅当库中当前哈希仍等于 expectedHash
     * （为 null 时要求库中也为 NULL）才更新，返回影响行数；为 0 表示期间被并发轮换或迁移过。
     */
    default int updateAgentCredential(Long id, ComputeNodeDO state, String expectedHash) {
        LambdaUpdateWrapper<ComputeNodeDO> wrapper = new LambdaUpdateWrapper<ComputeNodeDO>()
                .eq(ComputeNodeDO::getId, id)
                .set(ComputeNodeDO::getAgentToken, state.getAgentToken())
                .set(ComputeNodeDO::getAgentTokenHash, state.getAgentTokenHash())
                .set(ComputeNodeDO::getAgentTokenCipher, state.getAgentTokenCipher())
                .set(ComputeNodeDO::getAgentTokenPrevHash, state.getAgentTokenPrevHash())
                .set(ComputeNodeDO::getAgentTokenPrevCipher, state.getAgentTokenPrevCipher())
                .set(ComputeNodeDO::getAgentTokenPrevExpiresAt, state.getAgentTokenPrevExpiresAt())
                .set(ComputeNodeDO::getAgentTokenPending, Boolean.TRUE.equals(state.getAgentTokenPending()))
                .set(ComputeNodeDO::getAgentTokenGeneration,
                        state.getAgentTokenGeneration() != null ? state.getAgentTokenGeneration() : 0);
        if (expectedHash == null) {
            wrapper.isNull(ComputeNodeDO::getAgentTokenHash);
        } else {
            wrapper.eq(ComputeNodeDO::getAgentTokenHash, expectedHash);
        }
        return update(null, wrapper);
    }

    /**
     * 记录 Agent 在指令轮询中声明的能力（库连接串带 stringtype=unspecified，JSON 字符串可直接写入 jsonb）。
     */
    default int updateAgentFeatures(Long id, Map<String, Boolean> features) {
        return update(null, new LambdaUpdateWrapper<ComputeNodeDO>()
                .eq(ComputeNodeDO::getId, id)
                .set(ComputeNodeDO::getAgentFeatures, JSONUtil.toJsonStr(features)));
    }

}
