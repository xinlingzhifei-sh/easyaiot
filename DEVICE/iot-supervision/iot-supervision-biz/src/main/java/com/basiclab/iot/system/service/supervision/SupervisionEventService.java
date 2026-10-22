package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface SupervisionEventService {

    AlertToEventResult createFromAlert(AlertToEventCommand command);

    default Optional<EventDetail> getEventDetail(Long eventId) {
        return Optional.empty();
    }

    record AlertToEventCommand(String sourceSystem,
                               String sourceAlertId,
                               String ruleCode,
                               String sourceAlertType,
                               LocalDateTime sourceAlertTime,
                               String sourcePayloadHash,
                               boolean dispatchTasks) {

        public AlertToEventCommand(String sourceSystem,
                                   String sourceAlertId,
                                   String ruleCode,
                                   String sourceAlertType,
                                   LocalDateTime sourceAlertTime,
                                   String sourcePayloadHash) {
            this(sourceSystem, sourceAlertId, ruleCode, sourceAlertType, sourceAlertTime, sourcePayloadHash, true);
        }
    }

    record AlertToEventResult(Long eventId,
                              String sourceSystem,
                              String sourceAlertId,
                              String ruleCode,
                              String eventType,
                              SupervisionEventLevelEnum eventLevel,
                              String eventStatus,
                              boolean reused) {

        public AlertToEventResult asReused() {
            if (reused) {
                return this;
            }
            return new AlertToEventResult(eventId, sourceSystem, sourceAlertId, ruleCode,
                    eventType, eventLevel, eventStatus, true);
        }

        public AlertToEventResult asDispatched() {
            if (SupervisionEventStatusEnum.DISPATCHED.getCode().equals(eventStatus)) {
                return this;
            }
            return new AlertToEventResult(eventId, sourceSystem, sourceAlertId, ruleCode,
                    eventType, eventLevel, SupervisionEventStatusEnum.DISPATCHED.getCode(), reused);
        }

    }

    record EventDetail(Long eventId,
                       String sourceSystem,
                       String sourceAlertId,
                       String ruleCode,
                       String eventType,
                       String eventLevel,
                       String eventStatus,
                       String closeResult,
                       LocalDateTime createdAt,
                       LocalDateTime acceptedAt,
                       LocalDateTime handledAt,
                       LocalDateTime closedAt) {
    }

    record EventCreateDraft(String sourceSystem,
                            String sourceAlertId,
                            String sourceAlertType,
                            LocalDateTime sourceAlertTime,
                            String sourcePayloadHash,
                            String ruleCode,
                            String eventType,
                            SupervisionEventLevelEnum eventLevel,
                            String eventStatus) {
    }

    record TaskDispatchCommand(Long eventId,
                               String ruleCode,
                               String eventType,
                               SupervisionEventLevelEnum eventLevel,
                               List<String> responsibilityChain) {

        public TaskDispatchCommand {
            responsibilityChain = List.copyOf(Objects.requireNonNull(responsibilityChain, "responsibilityChain"));
        }

    }

    interface EventStore {

        Optional<AlertToEventResult> findOpenBySourceAlert(String sourceSystem, String sourceAlertId);

        AlertToEventResult create(EventCreateDraft draft);

        void markDispatched(Long eventId);

        default Optional<EventDetail> findById(Long eventId) {
            return Optional.empty();
        }

    }

    interface TaskDispatcher {

        void dispatchForNewEvent(TaskDispatchCommand command);

    }

}
