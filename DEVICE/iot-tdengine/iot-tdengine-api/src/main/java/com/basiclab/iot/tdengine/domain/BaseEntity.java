package com.basiclab.iot.tdengine.domain;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * BaseEntity
 *
 * @author reese
 * @email reese
 */
@Data
public class BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 数据库名称
     */
    @NotBlank(message = "invalid operation: databaseName can not be empty")
    private String dataBaseName;

    /**
     * 超级表名称
     */
    @NotBlank(message = "invalid operation: superTableName can not be empty")
    private String superTableName;
}
