package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
public class SupervisionTaskQueryService {

    private final SupervisionTaskMapper supervisionTaskMapper;

    public SupervisionTaskQueryService(SupervisionTaskMapper supervisionTaskMapper) {
        this.supervisionTaskMapper = Objects.requireNonNull(supervisionTaskMapper, "supervisionTaskMapper");
    }

    public Optional<TaskDetail> getTaskDetail(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        return Optional.ofNullable(supervisionTaskMapper.selectById(taskId))
                .map(this::toDetail);
    }

    public Optional<TaskDetail> getCurrentTaskByEvent(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return supervisionTaskMapper.selectList(SupervisionTaskDO::getEventId, eventId).stream()
                .max(Comparator.comparing(SupervisionTaskDO::getId))
                .map(this::toDetail);
    }

    private TaskDetail toDetail(SupervisionTaskDO taskDO) {
        return new TaskDetail(
                taskDO.getId(),
                taskDO.getEventId(),
                taskDO.getTaskStatus(),
                taskDO.getAssignedUserId(),
                taskDO.getAcceptedAt(),
                taskDO.getSubmittedAt(),
                taskDO.getResultCategory(),
                taskDO.getHandlingNote(),
                taskDO.getReworkCount()
        );
    }

    public record TaskDetail(Long taskId,
                             Long eventId,
                             String taskStatus,
                             Long acceptedUserId,
                             LocalDateTime acceptedAt,
                             LocalDateTime submittedAt,
                             String resultCategory,
                             String handlingNote,
                             Integer reworkCount) {
    }

}
