package com.basiclab.iot.system.controller.admin.permission.vo.role;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * RoleSimpleRespVO
 *
 * @author reese
 * @email reese
 */
@Schema(description = "管理后台 - 角色精简信息 Response VO")
@Data
public class RoleSimpleRespVO {

    @Schema(description = "角色编号", example = "1024")
    private Long id;

    @Schema(description = "角色名称", example = "yFeiEye")
    private String name;

}
