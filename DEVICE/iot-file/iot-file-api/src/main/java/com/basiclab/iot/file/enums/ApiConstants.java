package com.basiclab.iot.file.enums;

/**
 * API 相关的枚举
 *
 * @author reese
 * @email reese
 */
public class ApiConstants {

    /**
     * 服务名
     * <p>
     * 注意，需要保证和 spring.application.name 保持一致
     */
    public static final String NAME = "file-server";

    public static final String PREFIX_FILE1 = "/sysFile/upload";
    public static final String PREFIX_FILE2 = "/sysFile/uploadByBucket";

    public static final String VERSION = "1.0.0";

}
