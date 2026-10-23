package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SupervisionAlertReviewCaseItemMapper extends BaseMapperX<SupervisionAlertReviewCaseItemDO> {

    default List<SupervisionAlertReviewCaseItemDO> selectByCaseId(Long reviewCaseId) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewCaseItemDO>()
                .eq(SupervisionAlertReviewCaseItemDO::getReviewCaseId, reviewCaseId)
                .orderByAsc(SupervisionAlertReviewCaseItemDO::getSortOrder)
                .orderByAsc(SupervisionAlertReviewCaseItemDO::getId));
    }

    default SupervisionAlertReviewCaseItemDO selectExisting(Long reviewCaseId, Long reviewItemId) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewCaseItemDO>()
                .eq(SupervisionAlertReviewCaseItemDO::getReviewCaseId, reviewCaseId)
                .eq(SupervisionAlertReviewCaseItemDO::getReviewItemId, reviewItemId)
                .last("LIMIT 1"));
    }

    default List<SupervisionAlertReviewCaseItemDO> selectByReviewItemId(Long reviewItemId) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewCaseItemDO>()
                .eq(SupervisionAlertReviewCaseItemDO::getReviewItemId, reviewItemId)
                .orderByAsc(SupervisionAlertReviewCaseItemDO::getId));
    }

}
