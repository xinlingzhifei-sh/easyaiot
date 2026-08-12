package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewUserStatusDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SupervisionAlertReviewUserStatusMapper extends BaseMapperX<SupervisionAlertReviewUserStatusDO> {

    default SupervisionAlertReviewUserStatusDO selectByReviewItemAndUser(Long reviewItemId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewUserStatusDO>()
                .eq(SupervisionAlertReviewUserStatusDO::getReviewItemId, reviewItemId)
                .eq(SupervisionAlertReviewUserStatusDO::getUserId, userId)
                .last("LIMIT 1"));
    }

    default Long selectReviewedCountByUser(List<Long> reviewItemIds, Long userId) {
        return selectCount(new LambdaQueryWrapperX<SupervisionAlertReviewUserStatusDO>()
                .in(SupervisionAlertReviewUserStatusDO::getReviewItemId, reviewItemIds)
                .eq(SupervisionAlertReviewUserStatusDO::getUserId, userId)
                .eq(SupervisionAlertReviewUserStatusDO::getHasBeenReviewed, true));
    }

}
