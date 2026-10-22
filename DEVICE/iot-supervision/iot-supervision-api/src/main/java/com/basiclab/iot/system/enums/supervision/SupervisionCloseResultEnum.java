package com.basiclab.iot.system.enums.supervision;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SupervisionCloseResultEnum {

    CONFIRMED_HANDLED(10, "confirmed_handled"),
    FALSE_ALARM(20, "false_alarm"),
    UNABLE_TO_CONFIRM(30, "unable_to_confirm"),
    TRANSFERRED_MAJOR(40, "transferred_major"),
    DUPLICATE_MERGED(50, "duplicate_merged");

    private final int result;
    private final String code;

}
