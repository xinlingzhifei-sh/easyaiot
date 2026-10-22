package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.enums.supervision.SupervisionCloseResultEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventLevelEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionEventStatusEnum;
import com.basiclab.iot.system.enums.supervision.SupervisionTaskStatusEnum;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.stream;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SupervisionEnumsTest {

    @Test
    void eventStatusCodesMatchClosureContract() {
        assertArrayEquals(new String[]{
                "created",
                "dispatched",
                "accepted",
                "handling",
                "pending_recheck",
                "rework_required",
                "pending_close_check",
                "exception_review",
                "transferred_major",
                "closed"
        }, stream(SupervisionEventStatusEnum.values()).map(SupervisionEventStatusEnum::getCode).toArray(String[]::new));

        assertArrayEquals(new int[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100},
                stream(SupervisionEventStatusEnum.values()).mapToInt(SupervisionEventStatusEnum::getStatus).toArray());
    }

    @Test
    void eventLevelCodesMatchClosureContract() {
        assertArrayEquals(new String[]{"L1", "L2", "L3", "L4"},
                stream(SupervisionEventLevelEnum.values()).map(SupervisionEventLevelEnum::getCode).toArray(String[]::new));

        assertArrayEquals(new int[]{1, 2, 3, 4},
                stream(SupervisionEventLevelEnum.values()).mapToInt(SupervisionEventLevelEnum::getLevel).toArray());
    }

    @Test
    void taskStatusCodesMatchClosureContract() {
        assertArrayEquals(new String[]{
                "pending",
                "sent",
                "acknowledged",
                "handling",
                "submitted",
                "approved",
                "rejected",
                "timeout",
                "closed",
                "cancelled"
        }, stream(SupervisionTaskStatusEnum.values()).map(SupervisionTaskStatusEnum::getCode).toArray(String[]::new));

        assertArrayEquals(new int[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100},
                stream(SupervisionTaskStatusEnum.values()).mapToInt(SupervisionTaskStatusEnum::getStatus).toArray());
    }

    @Test
    void closeResultCodesMatchClosureContract() {
        assertArrayEquals(new String[]{
                "confirmed_handled",
                "false_alarm",
                "unable_to_confirm",
                "transferred_major",
                "duplicate_merged"
        }, stream(SupervisionCloseResultEnum.values()).map(SupervisionCloseResultEnum::getCode).toArray(String[]::new));

        assertArrayEquals(new int[]{10, 20, 30, 40, 50},
                stream(SupervisionCloseResultEnum.values()).mapToInt(SupervisionCloseResultEnum::getResult).toArray());
    }

}
