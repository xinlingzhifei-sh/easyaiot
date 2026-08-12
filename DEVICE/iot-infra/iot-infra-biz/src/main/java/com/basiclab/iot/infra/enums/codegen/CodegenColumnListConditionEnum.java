package com.basiclab.iot.infra.enums.codegen;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CodegenColumnListConditionEnum
 *
 * @author reese
 * @email reese
 */
@AllArgsConstructor
@Getter
public enum CodegenColumnListConditionEnum {

    EQ("="),
    NE("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    LIKE("LIKE"),
    BETWEEN("BETWEEN");

    /**
     * 条件
     */
    private final String condition;

}
