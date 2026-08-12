package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class SupervisionTaskSubmissionService {

    private final SupervisionTaskMapper supervisionTaskMapper;
    private final EventHandlingStore eventHandlingStore;

    public SupervisionTaskSubmissionService(SupervisionTaskMapper supervisionTaskMapper,
                                            EventHandlingStore eventHandlingStore) {
        this.supervisionTaskMapper = Objects.requireNonNull(supervisionTaskMapper, "supervisionTaskMapper");
        this.eventHandlingStore = Objects.requireNonNull(eventHandlingStore, "eventHandlingStore");
    }

    public boolean submitTask(Long taskId, String resultCategory, String handlingNote) {
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(resultCategory, "resultCategory");
        Objects.requireNonNull(handlingNote, "handlingNote");
        SupervisionTaskDO task = supervisionTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        Long eventId = Objects.requireNonNull(task.getEventId(), "eventId");
        boolean submitted = supervisionTaskMapper.updateStatusToSubmitted(
                taskId,
                resultCategory,
                handlingNote,
                LocalDateTime.now()
        ) == 1;
        if (submitted) {
            eventHandlingStore.markHandled(eventId);
        }
        return submitted;
    }

    public interface EventHandlingStore {

        void markHandled(Long eventId);

    }

}
