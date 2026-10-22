package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEventMapper;
import com.basiclab.iot.system.enums.supervision.SupervisionCloseResultEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventMapperEventStore;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventCommand;
import com.basiclab.iot.system.service.supervision.SupervisionEventService.AlertToEventResult;
import com.basiclab.iot.system.service.supervision.SupervisionEventServiceImpl;
import com.basiclab.iot.system.service.supervision.SupervisionRuleSeeds;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionEventMapperEventStoreTest {

    @Test
    void serviceCreatesEventThroughMapperStoreOnlyWhenNoOpenEventExists() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventService eventService = new SupervisionEventServiceImpl(
                new SupervisionEventMapperEventStore(mapperHandler.createProxy())
        );
        AlertToEventCommand command = new AlertToEventCommand(
                "video",
                "alert-001",
                SupervisionRuleSeeds.RULE_FALL_DOWN,
                "fall_down",
                LocalDateTime.of(2026, 6, 10, 10, 30),
                "payload-hash-001"
        );

        AlertToEventResult first = eventService.createFromAlert(command);
        AlertToEventResult second = eventService.createFromAlert(command);

        assertEquals(List.of("video:alert-001", "video:alert-001"), mapperHandler.lookupKeys());
        assertEquals(1, mapperHandler.insertedEvents().size());
        assertFalse(first.reused());
        assertTrue(second.reused());
        assertEquals(first.eventId(), second.eventId());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), first.eventStatus());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), second.eventStatus());
        assertEquals(SupervisionEventLevelEnum.L4, first.eventLevel());

        SupervisionEventDO insertedEvent = mapperHandler.insertedEvents().get(0);
        assertNotNull(insertedEvent.getEventNo());
        assertEquals("video", insertedEvent.getSourceSystem());
        assertEquals("alert-001", insertedEvent.getSourceAlertId());
        assertEquals("fall_down", insertedEvent.getSourceAlertType());
        assertEquals(LocalDateTime.of(2026, 6, 10, 10, 30), insertedEvent.getSourceAlertTime());
        assertEquals("payload-hash-001", insertedEvent.getSourcePayloadHash());
        assertEquals("生命健康", insertedEvent.getEventType());
        assertEquals(SupervisionEventLevelEnum.L4.getCode(), insertedEvent.getEventLevel());
        assertEquals(SupervisionEventStatusEnum.CREATED.getCode(), insertedEvent.getEventStatus());

        assertEquals(1, mapperHandler.dispatchedUpdates().size());
        SupervisionEventDO dispatchedUpdate = mapperHandler.dispatchedUpdates().get(0);
        assertEquals(first.eventId(), dispatchedUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.DISPATCHED.getCode(), dispatchedUpdate.getEventStatus());
        assertNotNull(dispatchedUpdate.getDispatchedAt());
    }

    @Test
    void markAcceptedUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        eventStore.markAccepted(1001L);

        assertEquals(1, mapperHandler.acceptedUpdates().size());
        SupervisionEventDO acceptedUpdate = mapperHandler.acceptedUpdates().get(0);
        assertEquals(1001L, acceptedUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.ACCEPTED.getCode(), acceptedUpdate.getEventStatus());
        assertNotNull(acceptedUpdate.getAcceptedAt());
    }

    @Test
    void markHandledUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        eventStore.markHandled(1001L);

        assertEquals(1, mapperHandler.handledUpdates().size());
        SupervisionEventDO handledUpdate = mapperHandler.handledUpdates().get(0);
        assertEquals(1001L, handledUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.PENDING_RECHECK.getCode(), handledUpdate.getEventStatus());
        assertNotNull(handledUpdate.getHandledAt());
    }

    @Test
    void markRecheckedUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        eventStore.markRechecked(1001L);

        assertEquals(1, mapperHandler.recheckedUpdates().size());
        SupervisionEventDO recheckedUpdate = mapperHandler.recheckedUpdates().get(0);
        assertEquals(1001L, recheckedUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode(), recheckedUpdate.getEventStatus());
        assertNotNull(recheckedUpdate.getRecheckedAt());
    }

    @Test
    void markReworkRequiredUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        eventStore.markReworkRequired(1001L);

        assertEquals(1, mapperHandler.reworkUpdates().size());
        SupervisionEventDO reworkUpdate = mapperHandler.reworkUpdates().get(0);
        assertEquals(1001L, reworkUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode(), reworkUpdate.getEventStatus());
        assertNotNull(reworkUpdate.getRecheckedAt());
    }

    @Test
    void markClosedUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        boolean closed = eventStore.markClosed(1001L, SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode());

        assertTrue(closed);
        assertEquals(1, mapperHandler.closedUpdates().size());
        SupervisionEventDO closedUpdate = mapperHandler.closedUpdates().get(0);
        assertEquals(1001L, closedUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.CLOSED.getCode(), closedUpdate.getEventStatus());
        assertEquals(SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode(), closedUpdate.getCloseResult());
        assertNotNull(closedUpdate.getClosedAt());
    }

    @Test
    void markCloseCheckReworkRequiredUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        boolean reworkRequired = eventStore.markCloseCheckReworkRequired(1001L);

        assertTrue(reworkRequired);
        assertEquals(1, mapperHandler.closeCheckReworkUpdates().size());
        SupervisionEventDO reworkUpdate = mapperHandler.closeCheckReworkUpdates().get(0);
        assertEquals(1001L, reworkUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode(), reworkUpdate.getEventStatus());
    }

    @Test
    void closeCheckServiceReturnsFalseWhenMapperStoreUpdatesZeroRows() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler(0);
        SupervisionEventCloseCheckService closeCheckService = new SupervisionEventCloseCheckService(
                new SupervisionEventMapperEventStore(mapperHandler.createProxy())
        );

        boolean closed = closeCheckService.approveCloseCheck(1001L);
        boolean reworkRequired = closeCheckService.rejectCloseCheck(1002L);

        assertFalse(closed);
        assertFalse(reworkRequired);
        assertEquals(1, mapperHandler.closedUpdates().size());
        assertEquals(1001L, mapperHandler.closedUpdates().get(0).getId());
        assertEquals(1, mapperHandler.closeCheckReworkUpdates().size());
        assertEquals(1002L, mapperHandler.closeCheckReworkUpdates().get(0).getId());
    }

    @Test
    void markReworkAcceptedUpdatesEventThroughMapper() {
        CapturingMapperHandler mapperHandler = new CapturingMapperHandler();
        SupervisionEventMapperEventStore eventStore = new SupervisionEventMapperEventStore(mapperHandler.createProxy());

        eventStore.markReworkAccepted(1001L);

        assertEquals(1, mapperHandler.reworkAcceptedUpdates().size());
        SupervisionEventDO acceptedUpdate = mapperHandler.reworkAcceptedUpdates().get(0);
        assertEquals(1001L, acceptedUpdate.getId());
        assertEquals(SupervisionEventStatusEnum.ACCEPTED.getCode(), acceptedUpdate.getEventStatus());
        assertNotNull(acceptedUpdate.getAcceptedAt());
    }

    private static final class CapturingMapperHandler implements InvocationHandler {

        private final int updateResult;
        private long nextEventId = 1000L;
        private final Map<String, SupervisionEventDO> openEvents = new LinkedHashMap<>();
        private final List<String> lookupKeys = new ArrayList<>();
        private final List<SupervisionEventDO> insertedEvents = new ArrayList<>();
        private final List<SupervisionEventDO> dispatchedUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> acceptedUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> handledUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> recheckedUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> reworkUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> closedUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> closeCheckReworkUpdates = new ArrayList<>();
        private final List<SupervisionEventDO> reworkAcceptedUpdates = new ArrayList<>();

        private CapturingMapperHandler() {
            this(1);
        }

        private CapturingMapperHandler(int updateResult) {
            this.updateResult = updateResult;
        }

        private SupervisionEventMapper createProxy() {
            return (SupervisionEventMapper) Proxy.newProxyInstance(
                    SupervisionEventMapper.class.getClassLoader(),
                    new Class[]{SupervisionEventMapper.class},
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("selectOpenBySourceAlert".equals(method.getName()) && args != null && args.length == 2) {
                String key = key((String) args[0], (String) args[1]);
                lookupKeys.add(key);
                return openEvents.get(key);
            }
            if ("insert".equals(method.getName()) && args != null && args.length == 1
                    && args[0] instanceof SupervisionEventDO eventDO) {
                eventDO.setId(++nextEventId);
                insertedEvents.add(copy(eventDO));
                openEvents.put(key(eventDO.getSourceSystem(), eventDO.getSourceAlertId()), eventDO);
                return 1;
            }
            if ("updateStatusToDispatched".equals(method.getName()) && args != null && args.length == 2) {
                Long eventId = (Long) args[0];
                LocalDateTime dispatchedAt = (LocalDateTime) args[1];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.DISPATCHED.getCode())
                        .setDispatchedAt(dispatchedAt);
                dispatchedUpdates.add(update);
                openEvents.values().stream()
                        .filter(eventDO -> eventId.equals(eventDO.getId()))
                        .findFirst()
                        .ifPresent(eventDO -> eventDO
                                .setEventStatus(SupervisionEventStatusEnum.DISPATCHED.getCode())
                                .setDispatchedAt(dispatchedAt));
                return updateResult;
            }
            if ("updateStatusToAccepted".equals(method.getName()) && args != null && args.length == 2) {
                Long eventId = (Long) args[0];
                LocalDateTime acceptedAt = (LocalDateTime) args[1];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.ACCEPTED.getCode())
                        .setAcceptedAt(acceptedAt);
                acceptedUpdates.add(update);
                return updateResult;
            }
            if ("updateStatusToPendingRecheck".equals(method.getName()) && args != null && args.length == 2) {
                Long eventId = (Long) args[0];
                LocalDateTime handledAt = (LocalDateTime) args[1];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.PENDING_RECHECK.getCode())
                        .setHandledAt(handledAt);
                handledUpdates.add(update);
                return updateResult;
            }
            if ("updateStatusToPendingCloseCheck".equals(method.getName()) && args != null && args.length == 2) {
                Long eventId = (Long) args[0];
                LocalDateTime recheckedAt = (LocalDateTime) args[1];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.PENDING_CLOSE_CHECK.getCode())
                        .setRecheckedAt(recheckedAt);
                recheckedUpdates.add(update);
                return updateResult;
            }
            if ("updateStatusToReworkRequired".equals(method.getName()) && args != null && args.length == 2) {
                Long eventId = (Long) args[0];
                LocalDateTime recheckedAt = (LocalDateTime) args[1];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode())
                        .setRecheckedAt(recheckedAt);
                reworkUpdates.add(update);
                return updateResult;
            }
            if ("updateStatusToClosed".equals(method.getName()) && args != null && args.length == 3) {
                Long eventId = (Long) args[0];
                String closeResult = (String) args[1];
                LocalDateTime closedAt = (LocalDateTime) args[2];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.CLOSED.getCode())
                        .setCloseResult(closeResult)
                        .setClosedAt(closedAt);
                closedUpdates.add(update);
                return updateResult;
            }
            if ("updateStatusToCloseCheckReworkRequired".equals(method.getName()) && args != null && args.length == 1) {
                Long eventId = (Long) args[0];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.REWORK_REQUIRED.getCode());
                closeCheckReworkUpdates.add(update);
                return updateResult;
            }
            if ("updateStatusToAcceptedFromRework".equals(method.getName()) && args != null && args.length == 2) {
                Long eventId = (Long) args[0];
                LocalDateTime acceptedAt = (LocalDateTime) args[1];
                SupervisionEventDO update = new SupervisionEventDO()
                        .setId(eventId)
                        .setEventStatus(SupervisionEventStatusEnum.ACCEPTED.getCode())
                        .setAcceptedAt(acceptedAt);
                reworkAcceptedUpdates.add(update);
                return updateResult;
            }
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            throw new UnsupportedOperationException(method.toString());
        }

        private List<String> lookupKeys() {
            return lookupKeys;
        }

        private List<SupervisionEventDO> insertedEvents() {
            return insertedEvents;
        }

        private List<SupervisionEventDO> dispatchedUpdates() {
            return dispatchedUpdates;
        }

        private List<SupervisionEventDO> acceptedUpdates() {
            return acceptedUpdates;
        }

        private List<SupervisionEventDO> handledUpdates() {
            return handledUpdates;
        }

        private List<SupervisionEventDO> recheckedUpdates() {
            return recheckedUpdates;
        }

        private List<SupervisionEventDO> reworkUpdates() {
            return reworkUpdates;
        }

        private List<SupervisionEventDO> closedUpdates() {
            return closedUpdates;
        }

        private List<SupervisionEventDO> closeCheckReworkUpdates() {
            return closeCheckReworkUpdates;
        }

        private List<SupervisionEventDO> reworkAcceptedUpdates() {
            return reworkAcceptedUpdates;
        }

        private String key(String sourceSystem, String sourceAlertId) {
            return sourceSystem + ":" + sourceAlertId;
        }

        private SupervisionEventDO copy(SupervisionEventDO source) {
            return new SupervisionEventDO()
                    .setId(source.getId())
                    .setEventNo(source.getEventNo())
                    .setSourceSystem(source.getSourceSystem())
                    .setSourceAlertId(source.getSourceAlertId())
                    .setSourceAlertType(source.getSourceAlertType())
                    .setSourceAlertTime(source.getSourceAlertTime())
                    .setSourcePayloadHash(source.getSourcePayloadHash())
                    .setEventType(source.getEventType())
                    .setEventLevel(source.getEventLevel())
                    .setEventStatus(source.getEventStatus())
                    .setDispatchedAt(source.getDispatchedAt());
        }

    }

}
