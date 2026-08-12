package com.basiclab.iot.infra.enums.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * ConfigTypeEnum
 *
 * @author reese
 * @email reese
 */
@Getter
@AllArgsConstructor
public enum ConfigTypeEnum {

    SYSTEM(1),
    /**
     * 自定义配置
     */
    CUSTOM(2);

    private final Integer type;

}
