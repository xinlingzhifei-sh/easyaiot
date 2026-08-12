package com.basiclab.iot.system.dal.pgsql.supervision;

import com.basiclab.iot.common.core.mapper.BaseMapperX;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SupervisionAlertReviewCaseMapper extends BaseMapperX<SupervisionAlertReviewCaseDO> {

    @Select("""
            SELECT *
            FROM system_supervision_alert_review_case
            WHERE tenant_id = #{tenantId}
              AND id = #{reviewCaseId}
              AND deleted = 0
            FOR UPDATE
            """)
    SupervisionAlertReviewCaseDO selectByIdForUpdate(@Param("tenantId") Long tenantId,
                                                     @Param("reviewCaseId") Long reviewCaseId);

    @Update("""
            UPDATE system_supervision_alert_review_case
            SET title = #{caseDO.title,jdbcType=VARCHAR},
                status = #{caseDO.status,jdbcType=VARCHAR},
                primary_review_item_id = #{caseDO.primaryReviewItemId,jdbcType=BIGINT},
                owner_user_id = #{caseDO.ownerUserId,jdbcType=BIGINT},
                notes = #{caseDO.notes,jdbcType=LONGVARCHAR},
                camera_ids = #{caseDO.cameraIds,jdbcType=LONGVARCHAR},
                start_time = #{caseDO.startTime,jdbcType=TIMESTAMP},
                end_time = #{caseDO.endTime,jdbcType=TIMESTAMP},
                version = #{caseDO.version,jdbcType=INTEGER},
                update_time = CURRENT_TIMESTAMP
            WHERE tenant_id = #{tenantId}
              AND id = #{caseDO.id}
              AND version = #{expectedVersion}
              AND deleted = 0
            """)
    int updateIfVersion(@Param("tenantId") Long tenantId,
                        @Param("caseDO") SupervisionAlertReviewCaseDO caseDO,
                        @Param("expectedVersion") Integer expectedVersion);

    default List<SupervisionAlertReviewCaseDO> selectLatest() {
        return selectList(new LambdaQueryWrapperX<SupervisionAlertReviewCaseDO>()
                .orderByDesc(SupervisionAlertReviewCaseDO::getId));
    }

}
