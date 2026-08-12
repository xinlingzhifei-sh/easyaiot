package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSegmentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Mapper
public interface SupervisionAlertReviewSegmentMapper extends BaseMapperX<SupervisionAlertReviewSegmentDO> {

    @Select("""
            SELECT pg_advisory_xact_lock(
                hashtextextended(
                    CONCAT(
                        CAST(#{tenantId} AS text),
                        ':',
                        CAST(#{namespace} AS text),
                        ':',
                        CAST(#{lockKey} AS text)
                    ),
                    0
                )
            )
            """)
    String acquireTransactionLock(@Param("tenantId") Long tenantId,
                                  @Param("namespace") String namespace,
                                  @Param("lockKey") String lockKey);

    default SupervisionAlertReviewSegmentDO selectByReviewItemId(Long reviewItemId) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO>()
                .eq(SupervisionAlertReviewSegmentDO::getReviewItemId, reviewItemId)
                .eq(SupervisionAlertReviewSegmentDO::getDeleted, 0));
    }

    default List<SupervisionAlertReviewSegmentDO> selectOverlapping(Long tenantId,
                                                                    String cameraId,
                                                                    LocalDateTime startTime,
                                                                    LocalDateTime endTime) {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO>()
                .eq(SupervisionAlertReviewSegmentDO::getCameraId, cameraId)
                .eq(SupervisionAlertReviewSegmentDO::getDeleted, 0)
                .orderByDesc(SupervisionAlertReviewSegmentDO::getStartTime))
                .stream()
                .filter(segment -> Objects.equals(tenantId, segment.getTenantId()))
                .filter(segment -> overlaps(startTime, endTime, segment.getStartTime(), segment.getEndTime()))
                .toList();
    }

    default SupervisionAlertReviewSegmentDO selectLatestOpen(Long tenantId, String cameraId) {
        return selectOne(new LambdaQueryWrapperX<SupervisionAlertReviewSegmentDO>()
                .eq(SupervisionAlertReviewSegmentDO::getTenantId, tenantId)
                .eq(SupervisionAlertReviewSegmentDO::getCameraId, cameraId)
                .ne(SupervisionAlertReviewSegmentDO::getSegmentStatus, "ended")
                .isNull(SupervisionAlertReviewSegmentDO::getEndTime)
                .eq(SupervisionAlertReviewSegmentDO::getDeleted, 0)
                .orderByDesc(SupervisionAlertReviewSegmentDO::getStartTime)
                .last("LIMIT 1"));
    }

    private static boolean overlaps(LocalDateTime startTime,
                                    LocalDateTime endTime,
                                    LocalDateTime existingStartTime,
                                    LocalDateTime existingEndTime) {
        LocalDateTime leftEnd = endTime == null ? LocalDateTime.MAX : endTime;
        LocalDateTime rightEnd = existingEndTime == null ? LocalDateTime.MAX : existingEndTime;
        return startTime.isBefore(rightEnd) && existingStartTime.isBefore(leftEnd);
    }

}
