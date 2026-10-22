package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SupervisionTaskReworkService {

    private final SupervisionTaskMapper supervisionTaskMapper;
    private final EventReworkStore eventReworkStore;

    public SupervisionTaskReworkService(SupervisionTaskMapper supervisionTaskMapper,
                                        EventReworkStore eventReworkStore) {
        this.supervisionTaskMapper = Objects.requireNonNull(supervisionTaskMapper, "supervisionTaskMapper");
        this.eventReworkStore = Objects.requireNonNull(eventReworkStore, "eventReworkStore");
    }

    public boolean restartReworkTask(Long taskId, Long acceptedUserId) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(acceptedUserId, "acceptedUserId");
        SupervisionTaskDO task = supervisionTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        Long eventId = Objects.requireNonNull(task.getEventId(), "eventId");
        int nextReworkCount = Objects.requireNonNullElse(task.getReworkCount(), 0) + 1;
        boolean restarted = supervisionTaskMapper.updateStatusToAcknowledgedForRework(
                taskId,
                acceptedUserId,
                LocalDateTime.now(),
                nextReworkCount
        ) == 1;
        if (restarted) {
            eventReworkStore.markReworkAccepted(eventId);
        }
        return restarted;
    }

    public interface EventReworkStore {

        void markReworkAccepted(Long eventId);

    }

}
