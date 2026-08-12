package com.basiclab.iot.system.controller.admin.supervision.vo.event;

import com.basiclab.iot.system.service.supervision.SupervisionWorkflowApplicationService.EventEvidenceItemResponse;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "Admin - supervision event evidence item response")
@Data
public class EventEvidenceRespVO {

    @Schema(description = "Evidence ID", example = "3001")
    private Long evidenceId;

    @Schema(description = "Event ID", example = "1001")
    private Long eventId;

    @Schema(description = "Evidence source type", example = "video")
    private String sourceType;

    @Schema(description = "Evidence material type", example = "snapshot")
    private String materialType;

    @Schema(description = "Evidence material URI", example = "/media/alarm/snapshot-001.jpg")
    private String materialUri;

    @Schema(description = "Related source record ID", example = "alarm-image-001")
    private String relatedRecordId;

    @Schema(description = "Whether this evidence is required", example = "true")
    private Boolean isRequired;

    @Schema(description = "Required event level", example = "L3")
    private String requiredForLevel;

    @Schema(description = "Evidence collection status", example = "collected")
    private String collectStatus;

    @Schema(description = "Missing reason")
    private String missingReason;

    @Schema(description = "Sensitivity level", example = "normal")
    private String sensitivityLevel;

    @Schema(description = "Evidence created time", example = "2026-06-11T09:31:00")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    public static EventEvidenceRespVO from(EventEvidenceItemResponse response) {
        EventEvidenceRespVO respVO = new EventEvidenceRespVO();
        respVO.setEvidenceId(response.evidenceId());
        respVO.setEventId(response.eventId());
        respVO.setSourceType(response.sourceType());
        respVO.setMaterialType(response.materialType());
        respVO.setMaterialUri(response.materialUri());
        respVO.setRelatedRecordId(response.relatedRecordId());
        respVO.setIsRequired(response.isRequired());
        respVO.setRequiredForLevel(response.requiredForLevel());
        respVO.setCollectStatus(response.collectStatus());
        respVO.setMissingReason(response.missingReason());
        respVO.setSensitivityLevel(response.sensitivityLevel());
        respVO.setCreatedAt(response.createdAt());
        return respVO;
    }

}
