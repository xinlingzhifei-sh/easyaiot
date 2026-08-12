package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionTaskMapper;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SupervisionTaskRecheckService {

    private final SupervisionTaskMapper supervisionTaskMapper;
    private final EventRecheckStore eventRecheckStore;

    public SupervisionTaskRecheckService(SupervisionTaskMapper supervisionTaskMapper,
                                         EventRecheckStore eventRecheckStore) {
        this.supervisionTaskMapper = Objects.requireNonNull(supervisionTaskMapper, "supervisionTaskMapper");
        this.eventRecheckStore = Objects.requireNonNull(eventRecheckStore, "eventRecheckStore");
    }

    public boolean approveSubmittedTask(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        SupervisionTaskDO task = supervisionTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        Long eventId = Objects.requireNonNull(task.getEventId(), "eventId");
        boolean approved = supervisionTaskMapper.updateStatusToApproved(taskId) == 1;
        if (approved) {
            eventRecheckStore.markRechecked(eventId);
        }
        return approved;
    }

    public boolean rejectSubmittedTask(Long taskId) {
        Objects.requireNonNull(taskId, "taskId");
        SupervisionTaskDO task = supervisionTaskMapper.selectById(taskId);
        if (task == null) {
            return false;
        }
        Long eventId = Objects.requireNonNull(task.getEventId(), "eventId");
        boolean rejected = supervisionTaskMapper.updateStatusToRejected(taskId) == 1;
        if (rejected) {
            eventRecheckStore.markReworkRequired(eventId);
        }
        return rejected;
    }

    public interface EventRecheckStore {

        void markRechecked(Long eventId);

        void markReworkRequired(Long eventId);

    }

}
