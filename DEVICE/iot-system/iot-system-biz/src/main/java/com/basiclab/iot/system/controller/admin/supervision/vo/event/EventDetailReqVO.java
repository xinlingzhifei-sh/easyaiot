package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;

@Schema(description = "Management backend - Supervision event detail request VO")
@Data
public class EventDetailReqVO {

    @Schema(description = "Event ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "1001")
    @NotNull(message = "eventId must not be null")
    @Positive(message = "eventId must be positive")
    private Long id;

}
