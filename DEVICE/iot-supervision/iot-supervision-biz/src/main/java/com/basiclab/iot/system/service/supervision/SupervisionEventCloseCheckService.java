package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionCloseResultEnum;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SupervisionEventCloseCheckService {

    private final EventCloseStore eventCloseStore;

    public SupervisionEventCloseCheckService(EventCloseStore eventCloseStore) {
        this.eventCloseStore = Objects.requireNonNull(eventCloseStore, "eventCloseStore");
    }

    public boolean approveCloseCheck(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return eventCloseStore.markClosed(eventId, SupervisionCloseResultEnum.CONFIRMED_HANDLED.getCode());
    }

    public boolean rejectCloseCheck(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return eventCloseStore.markCloseCheckReworkRequired(eventId);
    }

    public interface EventCloseStore {

        boolean markClosed(Long eventId, String closeResult);

        boolean markCloseCheckReworkRequired(Long eventId);

    }

}
