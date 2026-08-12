package com.basiclab.iot.system.service.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEvidenceItemDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEvidenceItemMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class SupervisionEvidenceQueryService {

    private final SupervisionEvidenceItemMapper supervisionEvidenceItemMapper;

    public SupervisionEvidenceQueryService(SupervisionEvidenceItemMapper supervisionEvidenceItemMapper) {
        this.supervisionEvidenceItemMapper = Objects.requireNonNull(supervisionEvidenceItemMapper, "supervisionEvidenceItemMapper");
    }

    public List<EvidenceItem> listByEventId(Long eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return supervisionEvidenceItemMapper.selectByEventId(eventId).stream()
                .map(this::toEvidenceItem)
                .toList();
    }

    private EvidenceItem toEvidenceItem(SupervisionEvidenceItemDO evidenceItemDO) {
        return new EvidenceItem(
                evidenceItemDO.getId(),
                evidenceItemDO.getEventId(),
                evidenceItemDO.getSourceType(),
                evidenceItemDO.getMaterialType(),
                evidenceItemDO.getMaterialUri(),
                evidenceItemDO.getRelatedRecordId(),
                evidenceItemDO.getIsRequired(),
                evidenceItemDO.getRequiredForLevel(),
                evidenceItemDO.getCollectStatus(),
                evidenceItemDO.getMissingReason(),
                evidenceItemDO.getSensitivityLevel(),
                evidenceItemDO.getCreateTime()
        );
    }

    public record EvidenceItem(Long evidenceId,
                               Long eventId,
                               String sourceType,
                               String materialType,
                               String materialUri,
                               String relatedRecordId,
                               Boolean isRequired,
                               String requiredForLevel,
                               String collectStatus,
                               String missingReason,
                               String sensitivityLevel,
                               LocalDateTime createdAt) {
    }

}
