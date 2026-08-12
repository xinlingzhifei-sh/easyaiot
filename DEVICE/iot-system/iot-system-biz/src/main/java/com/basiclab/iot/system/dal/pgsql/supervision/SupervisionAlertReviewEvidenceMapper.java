package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewEvidenceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SupervisionAlertReviewEvidenceMapper extends BaseMapperX<SupervisionAlertReviewEvidenceDO> {

    default List<SupervisionAlertReviewEvidenceDO> selectByReviewItemId(Long reviewItemId) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewEvidenceDO>()
                .eq(SupervisionAlertReviewEvidenceDO::getReviewItemId, reviewItemId)
                .orderByAsc(SupervisionAlertReviewEvidenceDO::getHappenedAt));
    }

    default SupervisionAlertReviewEvidenceDO selectExisting(Long reviewItemId,
                                                            String sourceAlertId,
                                                            String materialType,
                                                            String materialUri) {
        LambdaQueryWrapperX<SupervisionAlertReviewEvidenceDO> query = new LambdaQueryWrapperX<SupervisionAlertReviewEvidenceDO>()
                .eq(SupervisionAlertReviewEvidenceDO::getReviewItemId, reviewItemId)
                .eq(SupervisionAlertReviewEvidenceDO::getSourceAlertId, sourceAlertId)
                .eq(SupervisionAlertReviewEvidenceDO::getMaterialType, materialType)
                .last("LIMIT 1");
        if (materialUri == null) {
            query.isNull(SupervisionAlertReviewEvidenceDO::getMaterialUri);
        } else {
            query.eq(SupervisionAlertReviewEvidenceDO::getMaterialUri, materialUri);
        }
        return selectOne(query);
    }

}
