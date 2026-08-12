package com.basiclab.iot.system.controller.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "Authoritative authenticated media permission decision")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MediaPermissionCheckRespVO {

    private Boolean allowed;
    private Long userId;
    private Long tenantId;
    private String cameraId;
    private String action;
    private String reason;
}
