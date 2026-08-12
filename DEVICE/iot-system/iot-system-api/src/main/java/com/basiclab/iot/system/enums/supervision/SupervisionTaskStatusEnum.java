package com.basiclab.iot.system.enums.supervision;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SupervisionTaskStatusEnum {

    PENDING(10, "pending"),
    SENT(20, "sent"),
    ACKNOWLEDGED(30, "acknowledged"),
    HANDLING(40, "handling"),
    SUBMITTED(50, "submitted"),
    APPROVED(60, "approved"),
    REJECTED(70, "rejected"),
    TIMEOUT(80, "timeout"),
    CLOSED(90, "closed"),
    CANCELLED(100, "cancelled");

    private final int status;
    private final String code;

}
