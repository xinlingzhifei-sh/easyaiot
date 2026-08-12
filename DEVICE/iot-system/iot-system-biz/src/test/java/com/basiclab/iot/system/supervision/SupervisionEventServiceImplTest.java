package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventCreateDraft;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.EventStore;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatchCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.TaskDispatcher;
import com.basiclab.iot.system.service.supervision.SupervisionEventServiceImpl;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds.RuleSeed;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionEventServiceImplTest {

    @Test
    void createFromAlertReusesOpenEventForSameSourceAlert() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(eventStore);
        AlertToEventCommand command = new AlertToEventCommand(
                "video",
                "alert-001",
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                "fall_down",
                LocalDateTime.of(2026, 6, 10, 10, 30),
                "payload-hash-001"
        );
        RuleSeed ruleSeed = SupervisionRuleSeeds.findByCode(command.ruleCode()).orElseThrow();

        AlertToEventResult first = eventService.createFromAlert(command);
        AlertToEventResult second = eventService.createFromAlert(command);

        assertEquals(first.eventId(), second.eventId());
        assertFalse(first.reused());
        assertTrue(second.reused());
        assertEquals(1, eventStore.createdCount());
        assertEquals(List.of("video:alert-001", "video:alert-001"), eventStore.lookupKeys());
        assertEquals(ruleSeed.getEventType(), first.eventType());
        assertEquals(SupervisionEventLevelEnum.L4, first.eventLevel());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), first.eventStatus());
    }

    @Test
    void createFromAlertMarksEventDispatchedOnlyAfterTasksAreDispatched() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        CapturingTaskDispatcher taskDispatcher = new CapturingTaskDispatcher();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(eventStore, taskDispatcher);
        AlertToEventCommand command = new AlertToEventCommand(
                "video",
                "alert-001",
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                "fall_down",
                LocalDateTime.of(2026, 6, 10, 10, 30),
                "payload-hash-001"
        );
        RuleSeed ruleSeed = SupervisionRuleSeeds.findByCode(command.ruleCode()).orElseThrow();

        AlertToEventResult first = eventService.createFromAlert(command);
        AlertToEventResult second = eventService.createFromAlert(command);

        assertFalse(first.reused());
        assertTrue(second.reused());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), first.eventStatus());
        assertEquals(List.of(first.eventId()), eventStore.dispatchedEventIds());
        assertEquals(1, taskDispatcher.commands().size());
        TaskDispatchCommand dispatchCommand = taskDispatcher.commands().get(0);
        assertEquals(first.eventId(), dispatchCommand.eventId());
        assertEquals(command.ruleCode(), dispatchCommand.ruleCode());
        assertEquals(ruleSeed.getEventType(), dispatchCommand.eventType());
        assertEquals(ruleSeed.getDefaultLevel(), dispatchCommand.eventLevel());
        assertEquals(ruleSeed.getDefaultResponsibilityChain(), dispatchCommand.responsibilityChain());
    }

    @Test
    void createFromAlertKeepsEventCreatedWhenTaskDispatchFails() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                eventStore,
                command -> {
                    throw new IllegalStateException("dispatch failed");
                }
        );
        AlertToEventCommand command = new AlertToEventCommand(
                "video",
                "alert-001",
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                "fall_down",
                LocalDateTime.of(2026, 6, 10, 10, 30),
                "payload-hash-001"
        );

        assertThrows(IllegalStateException.class, () -> eventService.createFromAlert(command));

        assertEquals(1, eventStore.createdCount());
        assertEquals(List.of(), eventStore.dispatchedEventIds());
    }

    @Test
    void createFromAlertCanPersistSyntheticEventWithoutDispatchingTasks() {
        InMemoryEventStore eventStore = new InMemoryEventStore();
        CapturingTaskDispatcher taskDispatcher = new CapturingTaskDispatcher();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(eventStore, taskDispatcher);
        AlertToEventCommand command = new AlertToEventCommand(
                "alert-review",
                "integration-smoke-001",
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                "fall_down",
                LocalDateTime.of(2026, 7, 13, 16, 50),
                "payload-hash-smoke",
                false
        );

        AlertToEventResult first = eventService.createFromAlert(command);
        AlertToEventResult second = eventService.createFromAlert(command);

        assertEquals(SupervisionEventStatusEnum.CREATED.getCode(), first.eventStatus());
        assertFalse(first.reused());
        assertTrue(second.reused());
        assertEquals(List.of(), taskDispatcher.commands());
        assertEquals(List.of(), eventStore.dispatchedEventIds());
    }

    private static final class InMemoryEventStore implements EventStore {

        private long nextEventId = 1000L;
        private final Map<String, AlertToEventResult> openEvents = new LinkedHashMap<>();
        private final List<String> lookupKeys = new ArrayList<>();
        private final List<Long> dispatchedEventIds = new ArrayList<>();
        private int createdCount;

        @Override
        public Optional<AlertToEventResult> findOpenBySourceAlert(String sourceSystem, String sourceAlertId) {
            lookupKeys.add(key(sourceSystem, sourceAlertId));
            return Optional.ofNullable(openEvents.get(key(sourceSystem, sourceAlertId)));
        }

        @Override
        public AlertToEventResult create(EventCreateDraft draft) {
            createdCount++;
            AlertToEventResult result = new AlertToEventResult(
                    ++nextEventId,
                    draft.sourceSystem(),
                    draft.sourceAlertId(),
                    draft.ruleCode(),
                    draft.eventType(),
                    draft.eventLevel(),
                    draft.eventStatus(),
                    false
            );
            openEvents.put(key(draft.sourceSystem(), draft.sourceAlertId()), result);
            return result;
        }

        @Override
        public void markDispatched(Long eventId) {
            dispatchedEventIds.add(eventId);
            openEvents.replaceAll((key, event) -> event.eventId().equals(eventId) ? event.asDispatched() : event);
        }

        int createdCount() {
            return createdCount;
        }

        List<String> lookupKeys() {
            return lookupKeys;
        }

        List<Long> dispatchedEventIds() {
            return dispatchedEventIds;
        }

        private String key(String sourceSystem, String sourceAlertId) {
            return sourceSystem + ":" + sourceAlertId;
        }

    }

    private static final class CapturingTaskDispatcher implements TaskDispatcher {

        private final List<TaskDispatchCommand> commands = new ArrayList<>();

        @Override
        public void dispatchForNewEvent(TaskDispatchCommand command) {
            commands.add(command);
        }

        List<TaskDispatchCommand> commands() {
            return commands;
        }

    }

}
