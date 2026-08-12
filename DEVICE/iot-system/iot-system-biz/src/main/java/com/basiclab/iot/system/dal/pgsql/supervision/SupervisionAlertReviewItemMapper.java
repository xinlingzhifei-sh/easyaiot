package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewItemDO;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SupervisionAlertReviewItemMapper extends BaseMapperX<SupervisionAlertReviewItemDO> {

    @Select("""
            SELECT *
            FROM system_supervision_alert_review_item
            WHERE tenant_id = #{tenantId}
              AND id = #{reviewItemId}
              AND deleted = 0
            FOR UPDATE
            """)
    SupervisionAlertReviewItemDO selectByIdForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("reviewItemId") Long reviewItemId);

    default int updateReviewStatusIfCurrent(Long reviewItemId,
                                            String expectedReviewStatus,
                                            Integer expectedVersion,
                                            SupervisionAlertReviewItemDO updateObj) {
        LambdaQueryWrapperX<SupervisionAlertReviewItemDO> query =
                new LambdaQueryWrapperX<SupervisionAlertReviewItemDO>()
                        .eq(SupervisionAlertReviewItemDO::getId, reviewItemId);
        if (expectedReviewStatus == null) {
            query.isNull(SupervisionAlertReviewItemDO::getReviewStatus);
        } else {
            query.eq(SupervisionAlertReviewItemDO::getReviewStatus, expectedReviewStatus);
        }
        if (expectedVersion == null) {
            query.isNull(SupervisionAlertReviewItemDO::getVersion);
        } else {
            query.eq(SupervisionAlertReviewItemDO::getVersion, expectedVersion);
        }
        return update(updateObj, query);
    }

    default SupervisionAlertReviewItemDO selectMergeCandidate(Long tenantId,
                                                             String sourceSystem,
                                                             String cameraId,
                                                             String zoneCode,
                                                             String ruleCode,
                                                             LocalDateTime windowStart,
                                                             LocalDateTime windowEnd) {
        LambdaQueryWrapperX<SupervisionAlertReviewItemDO> query = new LambdaQueryWrapperX<SupervisionAlertReviewItemDO>()
                .eq(SupervisionAlertReviewItemDO::getReviewStatus, SupervisionAlertReviewService.STATUS_PENDING_REVIEW)
                .eqIfPresent(SupervisionAlertReviewItemDO::getTenantId, tenantId)
                .eq(SupervisionAlertReviewItemDO::getSourceSystem, sourceSystem)
                .eq(SupervisionAlertReviewItemDO::getCameraId, cameraId)
                .geIfPresent(SupervisionAlertReviewItemDO::getLastAlertTime, windowStart)
                .leIfPresent(SupervisionAlertReviewItemDO::getFirstAlertTime, windowEnd)
                .orderByDesc(SupervisionAlertReviewItemDO::getLastAlertTime)
                .last("LIMIT 1");
        return selectOne(query);
    }

    default List<SupervisionAlertReviewItemDO> selectWorkbench(Long tenantId,
                                                              String reviewStatus,
                                                              String cameraId,
                                                              LocalDateTime beginTime,
                                                              LocalDateTime endTime) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewItemDO>()
                .eqIfPresent(SupervisionAlertReviewItemDO::getTenantId, tenantId)
                .eqIfPresent(SupervisionAlertReviewItemDO::getReviewStatus, reviewStatus)
                .eqIfPresent(SupervisionAlertReviewItemDO::getCameraId, cameraId)
                .geIfPresent(SupervisionAlertReviewItemDO::getLastAlertTime, beginTime)
                .ltIfPresent(SupervisionAlertReviewItemDO::getFirstAlertTime, endTime)
                .orderByDesc(SupervisionAlertReviewItemDO::getLastAlertTime));
    }

    default List<String> selectExistingCameraIds(Long tenantId, List<String> requestedCameraIds) {
        if (tenantId == null || requestedCameraIds == null || requestedCameraIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewItemDO>()
                .select(SupervisionAlertReviewItemDO::getCameraId)
                .eq(SupervisionAlertReviewItemDO::getTenantId, tenantId)
                .in(SupervisionAlertReviewItemDO::getCameraId, requestedCameraIds)
                .groupBy(SupervisionAlertReviewItemDO::getCameraId))
                .stream()
                .map(SupervisionAlertReviewItemDO::getCameraId)
                .filter(cameraId -> cameraId != null && !cameraId.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .toList();
    }

}
