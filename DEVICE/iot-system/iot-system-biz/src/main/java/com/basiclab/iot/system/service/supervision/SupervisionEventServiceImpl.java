package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventDetail;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventCreateDraft;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventStore;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatchCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatcher;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds.RuleSeed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class SupervisionEventServiceImpl implements SupervisionEventService {

    private final EventStore eventStore;
    private final TaskDispatcher taskDispatcher;

    public SupervisionEventServiceImpl(EventStore eventStore) {
        this(eventStore, new NoopTaskDispatcher());
    }

    @Autowired
    public SupervisionEventServiceImpl(EventStore eventStore, TaskDispatcher taskDispatcher) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore");
        this.taskDispatcher = Objects.requireNonNull(taskDispatcher, "taskDispatcher");
    }

    @Override
    public AlertToEventResult createFromAlert(AlertToEventCommand command) {
        Objects.requireNonNull(command, "command");
        String sourceSystem = requireText(command.sourceSystem(), "sourceSystem");
        String sourceAlertId = requireText(command.sourceAlertId(), "sourceAlertId");

        return eventStore.findOpenBySourceAlert(sourceSystem, sourceAlertId)
                .map(AlertToEventResult::asReused)
                .orElseGet(() -> createNewEvent(command, sourceSystem, sourceAlertId));
    }

    @Override
    public Optional<EventDetail> getEventDetail(Long eventId) {
        if (eventId == null || eventId <= 0) {
            throw new IllegalArgumentException("eventId must be positive");
        }
        return eventStore.findById(eventId);
    }

    private AlertToEventResult createNewEvent(AlertToEventCommand command,
                                             String sourceSystem,
                                             String sourceAlertId) {
        String ruleCode = requireText(command.ruleCode(), "ruleCode");
        RuleSeed ruleSeed = SupervisionRuleSeeds.findByCode(ruleCode)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported supervision rule code: " + ruleCode));

        EventCreateDraft draft = new EventCreateDraft(
                sourceSystem,
                sourceAlertId,
                resolveSourceAlertType(command.sourceAlertType(), ruleSeed),
                command.sourceAlertTime(),
                command.sourcePayloadHash(),
                ruleCode,
                ruleSeed.getEventType(),
                ruleSeed.getDefaultLevel(),
                SupervisionEventStatusEnum.CREATED.getCode()
        );
        AlertToEventResult result = eventStore.create(draft);
        if (!command.dispatchTasks()) {
            return result;
        }
        taskDispatcher.dispatchForNewEvent(new TaskDispatchCommand(
                result.eventId(),
                ruleCode,
                ruleSeed.getEventType(),
                ruleSeed.getDefaultLevel(),
                ruleSeed.getDefaultResponsibilityChain()
        ));
        eventStore.markDispatched(result.eventId());
        return result.asDispatched();
    }

    private static String resolveSourceAlertType(String sourceAlertType, RuleSeed ruleSeed) {
        if (sourceAlertType == null || sourceAlertType.isBlank()) {
            return ruleSeed.getAlertType();
        }
        return sourceAlertType;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static final class NoopTaskDispatcher implements TaskDispatcher {

        @Override
        public void dispatchForNewEvent(TaskDispatchCommand command) {
        }

    }

}
