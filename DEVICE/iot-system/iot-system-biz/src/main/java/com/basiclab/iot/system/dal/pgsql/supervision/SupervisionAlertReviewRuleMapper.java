package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuleDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SupervisionAlertReviewRuleMapper extends BaseMapperX<SupervisionAlertReviewRuleDO> {

    default List<SupervisionAlertReviewRuleDO> selectEnabled() {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewRuleDO>()
                .eq(SupervisionAlertReviewRuleDO::getEnabled, true)
                .orderByDesc(SupervisionAlertReviewRuleDO::getId));
    }

    default List<SupervisionAlertReviewRuleDO> selectAllOrdered() {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewRuleDO>()
                .orderByDesc(SupervisionAlertReviewRuleDO::getId));
    }

}
