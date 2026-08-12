package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDeliveryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SupervisionAlertReviewRuntimeOutboxDeliveryMapper
        extends BaseMapperX<SupervisionAlertReviewRuntimeOutboxDeliveryDO> {

    default SupervisionAlertReviewRuntimeOutboxDeliveryDO selectByDeliveryKey(Long outboxId,
                                                                              String channel,
                                                                              Long recipientUserId,
                                                                              String templateCode) {
        if (outboxId == null || channel == null || recipientUserId == null || templateCode == null) {
            return null;
        }
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewRuntimeOutboxDeliveryDO>()
                .eq(SupervisionAlertReviewRuntimeOutboxDeliveryDO::getOutboxId, outboxId)
                .eq(SupervisionAlertReviewRuntimeOutboxDeliveryDO::getChannel, channel)
                .eq(SupervisionAlertReviewRuntimeOutboxDeliveryDO::getRecipientUserId, recipientUserId)
                .eq(SupervisionAlertReviewRuntimeOutboxDeliveryDO::getTemplateCode, templateCode)
                .last("LIMIT 1"));
    }
}
