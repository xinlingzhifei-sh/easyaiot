package com.basiclab.iot.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "VIDEO media permission check request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaPermissionCheckReqVO {

    @Schema(description = "Canonical media action", example = "coverage")
    private String action;

    @Schema(description = "Requested camera id", example = "camera-01")
    private String cameraId;

    @Schema(description = "Audit resource path")
    private String resource;

    @Schema(description = "Export id when checking an existing export")
    private String exportId;
}
