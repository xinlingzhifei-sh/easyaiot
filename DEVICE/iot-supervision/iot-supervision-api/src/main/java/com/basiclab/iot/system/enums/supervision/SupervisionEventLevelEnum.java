package com.basiclab.iot.system.enums.supervision;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SupervisionEventLevelEnum {

    L1(1, "L1"),
    L2(2, "L2"),
    L3(3, "L3"),
    L4(4, "L4");

    private final int level;
    private final String code;

}
