package com.basiclab.iot.system.controller.admin.dict.vo.type;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * DictTypeSimpleRespVO
 *
 * @author reese
 * @email reese
 */
@Schema(description = "管理后台 - 字典类型精简信息 Response VO")
@Data
public class DictTypeSimpleRespVO {

    @Schema(description = "字典类型编号", example = "1024")
    private Long id;

    @Schema(description = "字典类型名称", example = "yFeiEye")
    private String name;

    @Schema(description = "字典类型", example = "sys_common_sex")
    private String type;

}
