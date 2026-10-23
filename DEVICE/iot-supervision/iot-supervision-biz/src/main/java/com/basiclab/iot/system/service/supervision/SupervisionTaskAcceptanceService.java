package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SupervisionTaskAcceptanceService {

    private final SupervisionTaskMapper supervisionTaskMapper;
    private final EventAcceptanceStore eventAcceptanceStore;

    public SupervisionTaskAcceptanceService(SupervisionTaskMapper supervisionTaskMapper,
                                            EventAcceptanceStore eventAcceptanceStore) {
        this.supervisionTaskMapper = Objects.requireNonNull(supervisionTaskMapper, "supervisionTaskMapper");
        this.eventAcceptanceStore = Objects.requireNonNull(eventAcceptanceStore, "eventAcceptanceStore");
    }

    public boolean acceptTask(Long taskId, Long acceptedUserId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(acceptedUserId, "acceptedUserId");
        SupervisionTaskDO task = supervisionTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        Long eventId = Objects.requireNonNull(task.getEventId(), "eventId");
        boolean accepted = supervisionTaskMapper.updateStatusToAcknowledged(taskId, acceptedUserId, LocalDateTime.now()) == 1;
        if (accepted) {
            eventAcceptanceStore.markAccepted(eventId);
        }
        return accepted;
    }

    public interface EventAcceptanceStore {

        void markAccepted(Long eventId);

    }

}
