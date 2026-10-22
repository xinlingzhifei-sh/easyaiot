package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatchCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatcher;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class SupervisionTaskDispatcher implements TaskDispatcher {

    public static final String TASK_TYPE_HANDLE = "handle";

    private static final String TASK_NO_PREFIX = "ST-";

    private final SupervisionTaskMapper supervisionTaskMapper;

    public SupervisionTaskDispatcher(SupervisionTaskMapper supervisionTaskMapper) {
        this.supervisionTaskMapper = Objects.requireNonNull(supervisionTaskMapper, "supervisionTaskMapper");
    }

    @Override
    public void dispatchForNewEvent(TaskDispatchCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.eventId(), "eventId");

        command.responsibilityChain().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(role -> newTask(command.eventId(), role))
                .forEach(supervisionTaskMapper::insert);
    }

    private SupervisionTaskDO newTask(Long eventId, String assignedRole) {
        return new SupervisionTaskDO()
                .setEventId(eventId)
                .setTaskNo(newTaskNo())
                .setTaskType(TASK_TYPE_HANDLE)
                .setTaskStatus(SupervisionTaskStatusEnum.SENT.getCode())
                .setAssignedRole(assignedRole)
                .setReworkCount(0);
    }

    private String newTaskNo() {
        return TASK_NO_PREFIX + UUID.randomUUID();
    }

}
