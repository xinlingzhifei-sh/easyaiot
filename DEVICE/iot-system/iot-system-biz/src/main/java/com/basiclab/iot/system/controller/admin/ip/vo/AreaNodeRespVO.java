package com.basiclab.iot.system.controller.admin.ip.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * AreaNodeRespVO
 *
 * @author reese
 * @email reese
 */
@Schema(description = "管理后台 - 地区节点 Response VO")
@Data
public class AreaNodeRespVO {

    @Schema(description = "编号", example = "110000")
    private Integer id;

    @Schema(description = "名字", example = "北京")
    private String name;

    private List<AreaNodeRespVO> children;

}
