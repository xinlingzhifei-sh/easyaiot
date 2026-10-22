package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewIngestIdentityDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupervisionAlertReviewIngestIdentityMapper extends BaseMapperX<SupervisionAlertReviewIngestIdentityDO> {

    default SupervisionAlertReviewIngestIdentityDO selectByIdentity(Long tenantId,
                                                                    String sourceSystem,
                                                                    String identityKey) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewIngestIdentityDO>()
                .eq(SupervisionAlertReviewIngestIdentityDO::getTenantId, tenantId)
                .eq(SupervisionAlertReviewIngestIdentityDO::getSourceSystem, sourceSystem)
                .eq(SupervisionAlertReviewIngestIdentityDO::getIdentityKey, identityKey)
                .last("LIMIT 1"));
    }

}
