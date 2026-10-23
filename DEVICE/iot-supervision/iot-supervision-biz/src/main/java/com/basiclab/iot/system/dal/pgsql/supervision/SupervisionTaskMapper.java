package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupervisionTaskMapper extends BaseMapperX<SupervisionTaskDO> {

    default int updateStatusToAcknowledged(Long taskId, Long acceptedUserId, LocalDateTime acceptedAt) {
        return update(new SupervisionTaskDO()
                        .setTaskStatus(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode())
                        .setAssignedUserId(acceptedUserId)
                        .setAcceptedAt(acceptedAt),
                new LambdaQueryWrapperX<SupervisionTaskDO>()
                        .eq(SupervisionTaskDO::getId, taskId)
                        .eq(SupervisionTaskDO::getTaskStatus, SupervisionTaskStatusEnum.SENT.getCode()));
    }

    default int updateStatusToSubmitted(Long taskId, String resultCategory, String handlingNote, LocalDateTime submittedAt) {
        return update(new SupervisionTaskDO()
                        .setTaskStatus(SupervisionTaskStatusEnum.SUBMITTED.getCode())
                        .setResultCategory(resultCategory)
                        .setHandlingNote(handlingNote)
                        .setSubmittedAt(submittedAt),
                new LambdaQueryWrapperX<SupervisionTaskDO>()
                        .eq(SupervisionTaskDO::getId, taskId)
                        .eq(SupervisionTaskDO::getTaskStatus, SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode()));
    }

    default int updateStatusToApproved(Long taskId) {
        return update(new SupervisionTaskDO()
                        .setTaskStatus(SupervisionTaskStatusEnum.APPROVED.getCode()),
                new LambdaQueryWrapperX<SupervisionTaskDO>()
                        .eq(SupervisionTaskDO::getId, taskId)
                        .eq(SupervisionTaskDO::getTaskStatus, SupervisionTaskStatusEnum.SUBMITTED.getCode()));
    }

    default int updateStatusToRejected(Long taskId) {
        return update(new SupervisionTaskDO()
                        .setTaskStatus(SupervisionTaskStatusEnum.REJECTED.getCode()),
                new LambdaQueryWrapperX<SupervisionTaskDO>()
                        .eq(SupervisionTaskDO::getId, taskId)
                        .eq(SupervisionTaskDO::getTaskStatus, SupervisionTaskStatusEnum.SUBMITTED.getCode()));
    }

    default int updateStatusToAcknowledgedForRework(Long taskId, Long acceptedUserId, LocalDateTime acceptedAt, Integer reworkCount) {
        return update(new SupervisionTaskDO()
                        .setTaskStatus(SupervisionTaskStatusEnum.ACKNOWLEDGED.getCode())
                        .setAssignedUserId(acceptedUserId)
                        .setAcceptedAt(acceptedAt)
                        .setReworkCount(reworkCount),
                new LambdaQueryWrapperX<SupervisionTaskDO>()
                        .eq(SupervisionTaskDO::getId, taskId)
                        .in(SupervisionTaskDO::getTaskStatus, List.of(
                                SupervisionTaskStatusEnum.REJECTED.getCode(),
                                SupervisionTaskStatusEnum.APPROVED.getCode()
                        )));
    }

}
