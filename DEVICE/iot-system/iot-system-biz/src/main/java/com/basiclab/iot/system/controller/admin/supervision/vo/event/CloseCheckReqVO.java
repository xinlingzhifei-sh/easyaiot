package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Admin - supervision event close check request")
@Data
public class CloseCheckReqVO {

    @Schema(description = "Event ID", example = "1001")
    private Long eventId;

}
