package com.basiclab.iot.system.enums.supervision;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SupervisionEventStatusEnum {

    CREATED(10, "created"),
    DISPATCHED(20, "dispatched"),
    ACCEPTED(30, "accepted"),
    HANDLING(40, "handling"),
    PENDING_RECHECK(50, "pending_recheck"),
    REWORK_REQUIRED(60, "rework_required"),
    PENDING_CLOSE_CHECK(70, "pending_close_check"),
    EXCEPTION_REVIEW(80, "exception_review"),
    TRANSFERRED_MAJOR(90, "transferred_major"),
    CLOSED(100, "closed");

    private final int status;
    private final String code;

}
