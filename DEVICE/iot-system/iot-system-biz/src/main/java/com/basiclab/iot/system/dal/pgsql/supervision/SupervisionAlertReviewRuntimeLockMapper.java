package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeLockDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupervisionAlertReviewRuntimeLockMapper extends BaseMapperX<SupervisionAlertReviewRuntimeLockDO> {

    default SupervisionAlertReviewRuntimeLockDO selectByLockName(String lockName) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewRuntimeLockDO>()
                .eq(SupervisionAlertReviewRuntimeLockDO::getLockName, lockName)
                .last("LIMIT 1"));
    }

}
