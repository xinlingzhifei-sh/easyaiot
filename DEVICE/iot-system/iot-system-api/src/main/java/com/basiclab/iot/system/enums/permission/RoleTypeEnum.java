package com.basiclab.iot.system.enums.permission;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * RoleTypeEnum
 *
 * @author reese
 * @email reese
 */
@Getter
@AllArgsConstructor
public enum RoleTypeEnum {

    SYSTEM(1),
    /**
     * 自定义角色
     */
    CUSTOM(2);

    private final Integer type;

}
