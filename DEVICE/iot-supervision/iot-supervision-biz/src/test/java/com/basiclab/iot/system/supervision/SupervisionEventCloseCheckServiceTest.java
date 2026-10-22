package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionCloseResultEnum;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService;
import com.basiclab.iot.system.service.supervision.SupervisionEventCloseCheckService.EventCloseStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionEventCloseCheckServiceTest {

    @Test
    void approveCloseCheckClosesEventAsConfirmedHandled() {
        CapturingEventCloseStore eventCloseStore = new CapturingEventCloseStore();
        SupervisionEventCloseCheckService service = new SupervisionEventCloseCheckService(eventCloseStore);

        boolean closed = service.approveCloseCheck(1001L);

        assertTrue(closed);
        assertEquals(List.of(new CloseCommand(1001L, SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode())),
                eventCloseStore.closeCommands());
    }

    @Test
    void rejectCloseCheckMarksEventReworkRequired() {
        CapturingEventCloseStore eventCloseStore = new CapturingEventCloseStore();
        SupervisionEventCloseCheckService service = new SupervisionEventCloseCheckService(eventCloseStore);

        boolean rejected = service.rejectCloseCheck(1001L);

        assertTrue(rejected);
        assertEquals(List.of(1001L), eventCloseStore.closeCheckReworkEventIds());
    }

    @Test
    void closeCheckReturnsFalseWhenEventStoreRejectsMissingEvent() {
        CapturingEventCloseStore eventCloseStore = new CapturingEventCloseStore(false);
        SupervisionEventCloseCheckService service = new SupervisionEventCloseCheckService(eventCloseStore);

        boolean closed = service.approveCloseCheck(1001L);
        boolean rejected = service.rejectCloseCheck(1002L);

        assertFalse(closed);
        assertFalse(rejected);
        assertEquals(List.of(new CloseCommand(1001L, SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode())),
                eventCloseStore.closeCommands());
        assertEquals(List.of(1002L), eventCloseStore.closeCheckReworkEventIds());
    }

    @Test
    void closeCheckRequiresEventId() {
        SupervisionEventCloseCheckService service = new SupervisionEventCloseCheckService(new CapturingEventCloseStore());

        assertThrows(NullPointerException.class, () -> service.approveCloseCheck(null));
        assertThrows(NullPointerException.class, () -> service.rejectCloseCheck(null));
    }

    private record CloseCommand(Long eventId, String closeResult) {
    }

    private static final class CapturingEventCloseStore implements EventCloseStore {

        private final boolean updateResult;
        private final List<CloseCommand> closeCommands = new ArrayList<>();
        private final List<Long> closeCheckReworkEventIds = new ArrayList<>();

        private CapturingEventCloseStore() {
            this(true);
        }

        private CapturingEventCloseStore(boolean updateResult) {
            this.updateResult = updateResult;
        }

        @Override
        public boolean markClosed(Long eventId, String closeResult) {
            closeCommands.add(new CloseCommand(eventId, closeResult));
            return updateResult;
        }

        @Override
        public boolean markCloseCheckReworkRequired(Long eventId) {
            closeCheckReworkEventIds.add(eventId);
            return updateResult;
        }

        private List<CloseCommand> closeCommands() {
            return closeCommands;
        }

        private List<Long> closeCheckReworkEventIds() {
            return closeCheckReworkEventIds;
        }

    }

}
