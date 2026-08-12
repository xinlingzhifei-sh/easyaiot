package com.basiclab.iot.tdengine.domain;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * SelectDto
 *
 * @author reese
 * @email reese
 */
@Data
public class SelectDto {

    @NotBlank(message = "invalid operation: dataBaseName can not be empty")
    private String dataBaseName;

    @NotBlank(message = "invalid operation: tableName can not be empty")
    private String tableName;

//    @NotBlank(message = "invalid operation: fieldName can not be empty")
    private String fieldName;

//    @NotNull(message = "invalid operation: startTime can not be null")
    private Long startTime;

//    @NotNull(message = "invalid operation: endTime can not be null")
    private Long endTime;
}
