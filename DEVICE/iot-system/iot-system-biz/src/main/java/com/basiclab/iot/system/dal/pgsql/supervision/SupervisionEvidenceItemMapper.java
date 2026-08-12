package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEvidenceItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SupervisionEvidenceItemMapper extends BaseMapperX<SupervisionEvidenceItemDO> {

    default List<SupervisionEvidenceItemDO> selectByEventId(Long eventId) {
        return selectList(new LambdaQueryWrapperX<SupervisionEvidenceItemDO>()
                .eq(SupervisionEvidenceItemDO::getEventId, eventId)
                .orderByAsc(SupervisionEvidenceItemDO::getCreateTime)
                .orderByAsc(SupervisionEvidenceItemDO::getId));
    }

}
