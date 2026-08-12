package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEventMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventCreateDraft;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventDetail;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventStore;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService.EventCloseStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskAcceptanceService.EventAcceptanceStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskRecheckService.EventRecheckStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskReworkService.EventReworkStore;
import com.basiclab.iot.system.service.supervision.SupervisionTaskSubmissionService.EventHandlingStore;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupervisionEventMapperEventStore implements EventStore, EventAcceptanceStore, EventHandlingStore, EventRecheckStore, EventCloseStore, EventReworkStore {

    private static final String EVENT_NO_PREFIX = "SE-";

    private final SupervisionEventMapper supervisionEventMapper;

    public SupervisionEventMapperEventStore(SupervisionEventMapper supervisionEventMapper) {
        this.supervisionEventMapper = Objects.requireNonNull(supervisionEventMapper, "supervisionEventMapper");
    }

    @Override
    public Optional<AlertToEventResult> findOpenBySourceAlert(String sourceSystem, String sourceAlertId) {
        return Optional.ofNullable(supervisionEventMapper.selectOpenBySourceAlert(sourceSystem, sourceAlertId))
                .map(this::toResult);
    }

    @Override
    public Optional<EventDetail> findById(Long eventId) {
        return Optional.ofNullable(supervisionEventMapper.selectById(eventId))
                .map(this::toDetail);
    }

    @Override
    public AlertToEventResult create(EventCreateDraft draft) {
        Objects.requireNonNull(draft, "draft");
        SupervisionEventDO eventDO = new SupervisionEventDO()
                .setEventNo(newEventNo())
                .setSourceSystem(draft.sourceSystem())
                .setSourceAlertId(draft.sourceAlertId())
                .setSourceAlertType(draft.sourceAlertType())
                .setSourceAlertTime(draft.sourceAlertTime())
                .setSourcePayloadHash(draft.sourcePayloadHash())
                .setEventType(draft.eventType())
                .setEventLevel(draft.eventLevel().getCode())
                .setEventStatus(draft.eventStatus());
        supervisionEventMapper.insert(eventDO);
        return new AlertToEventResult(
                eventDO.getId(),
                draft.sourceSystem(),
                draft.sourceAlertId(),
                draft.ruleCode(),
                draft.eventType(),
                draft.eventLevel(),
                draft.eventStatus(),
                false
        );
    }

    @Override
    public void markDispatched(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        supervisionEventMapper.updateStatusToDispatched(eventId, LocalDateTime.now());
    }

    @Override
    public void markAccepted(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        supervisionEventMapper.updateStatusToAccepted(eventId, LocalDateTime.now());
    }

    @Override
    public void markHandled(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        supervisionEventMapper.updateStatusToPendingRecheck(eventId, LocalDateTime.now());
    }

    @Override
    public void markRechecked(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        supervisionEventMapper.updateStatusToPendingCloseCheck(eventId, LocalDateTime.now());
    }

    @Override
    public void markReworkRequired(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        supervisionEventMapper.updateStatusToReworkRequired(eventId, LocalDateTime.now());
    }

    @Override
    public boolean markClosed(Long eventId, String closeResult) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(closeResult, "closeResult");
        return supervisionEventMapper.updateStatusToClosed(eventId, closeResult, LocalDateTime.now()) == 1;
    }

    @Override
    public boolean markCloseCheckReworkRequired(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return supervisionEventMapper.updateStatusToCloseCheckReworkRequired(eventId) == 1;
    }

    @Override
    public void markReworkAccepted(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        supervisionEventMapper.updateStatusToAcceptedFromRework(eventId, LocalDateTime.now());
    }

    private AlertToEventResult toResult(SupervisionEventDO eventDO) {
        return new AlertToEventResult(
                eventDO.getId(),
                eventDO.getSourceSystem(),
                eventDO.getSourceAlertId(),
                null,
                eventDO.getEventType(),
                SupervisionEventLevelEnum.valueOf(eventDO.getEventLevel()),
                eventDO.getEventStatus(),
                false
        );
    }

    private EventDetail toDetail(SupervisionEventDO eventDO) {
        return new EventDetail(
                eventDO.getId(),
                eventDO.getSourceSystem(),
                eventDO.getSourceAlertId(),
                resolveRuleCode(eventDO),
                eventDO.getEventType(),
                eventDO.getEventLevel(),
                eventDO.getEventStatus(),
                eventDO.getCloseResult(),
                eventDO.getCreateTime(),
                eventDO.getAcceptedAt(),
                eventDO.getHandledAt(),
                eventDO.getClosedAt()
        );
    }

    private String resolveRuleCode(SupervisionEventDO eventDO) {
        return SupervisionRuleSeeds.listP0Rules().stream()
                .filter(seed -> seed.getEventType().equals(eventDO.getEventType()))
                .filter(seed -> eventDO.getSourceAlertType() == null
                        || eventDO.getSourceAlertType().isBlank()
                        || seed.getAlertType().equals(eventDO.getSourceAlertType()))
                .map(SupervisionRuleSeeds.RuleSeed::getRuleCode)
                .findFirst()
                .orElse(null);
    }

    private String newEventNo() {
        return EVENT_NO_PREFIX + UUID.randomUUID();
    }

}
