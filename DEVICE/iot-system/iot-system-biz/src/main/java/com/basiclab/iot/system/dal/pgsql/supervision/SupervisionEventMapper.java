package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface SupervisionEventMapper extends BaseMapperX<SupervisionEventDO> {

    default SupervisionEventDO selectOpenBySourceAlert(String sourceSystem, String sourceAlertId) {
        return selectOne(new LambdaQueryWrapperX<SupervisionEventDO>()
                .eq(SupervisionEventDO::getSourceSystem, sourceSystem)
                .eq(SupervisionEventDO::getSourceAlertId, sourceAlertId)
                .ne(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.CLOSED.getCode()));
    }

    default int updateStatusToDispatched(Long eventId, LocalDateTime dispatchedAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.DISPATCHED.getCode())
                        .setDispatchedAt(dispatchedAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.CREATED.getCode()));
    }

    default int updateStatusToAccepted(Long eventId, LocalDateTime acceptedAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.ACCEPTED.getCode())
                        .setAcceptedAt(acceptedAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.DISPATCHED.getCode()));
    }

    default int updateStatusToPendingRecheck(Long eventId, LocalDateTime handledAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.PENDING_RECHECK.getCode())
                        .setHandledAt(handledAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.ACCEPTED.getCode()));
    }

    default int updateStatusToPendingCloseCheck(Long eventId, LocalDateTime recheckedAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode())
                        .setRecheckedAt(recheckedAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.PENDING_RECHECK.getCode()));
    }

    default int updateStatusToReworkRequired(Long eventId, LocalDateTime recheckedAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode())
                        .setRecheckedAt(recheckedAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.PENDING_RECHECK.getCode()));
    }

    default int updateStatusToClosed(Long eventId, String closeResult, LocalDateTime closedAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.CLOSED.getCode())
                        .setCloseResult(closeResult)
                        .setClosedAt(closedAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode()));
    }

    default int updateStatusToCloseCheckReworkRequired(Long eventId) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode()),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode()));
    }

    default int updateStatusToAcceptedFromRework(Long eventId, LocalDateTime acceptedAt) {
        return update(new SupervisionEventDO()
                        .setEventStatus(SupervisionEventStatusEnum.ACCEPTED.getCode())
                        .setAcceptedAt(acceptedAt),
                new LambdaQueryWrapperX<SupervisionEventDO>()
                        .eq(SupervisionEventDO::getId, eventId)
                        .eq(SupervisionEventDO::getEventStatus, SupervisionEventStatusEnum.REWORK_REQUIRED.getCode()));
    }

}
