package com.basiclab.iot.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档地址
 *
 * @author liufei
 */
@Getter
@AllArgsConstructor
public enum DocumentEnum {

    REDIS_INSTALL("https://redis.io/docs/latest/operate/oss_and_stack/install/", "Redis 安装文档");
    
    private final String url;
    private final String memo;

}
