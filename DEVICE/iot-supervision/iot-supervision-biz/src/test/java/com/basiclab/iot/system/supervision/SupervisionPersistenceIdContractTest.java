package com.basiclab.iot.system.supervision;

import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseAuditDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewEvidenceDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewExportJobDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewIngestIdentityDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewReportAckDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuleDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeLockDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeOutboxDeliveryDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeRunDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSegmentDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSemanticIndexDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewUserStatusDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionEventDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionTaskDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SupervisionPersistenceIdContractTest {

    private static final List<Class<?>> POSTGRES_SEQUENCE_ENTITIES = List.of(
            SupervisionEventDO.class,
            SupervisionTaskDO.class,
            SupervisionAlertReviewItemDO.class,
            SupervisionAlertReviewIngestIdentityDO.class,
            SupervisionAlertReviewSegmentDO.class,
            SupervisionAlertReviewUserStatusDO.class,
            SupervisionAlertReviewEvidenceDO.class,
            SupervisionAlertReviewRuleDO.class,
            SupervisionAlertReviewCaseDO.class,
            SupervisionAlertReviewCaseItemDO.class,
            SupervisionAlertReviewCaseAuditDO.class,
            SupervisionAlertReviewSemanticIndexDO.class,
            SupervisionAlertReviewExportJobDO.class,
            SupervisionAlertReviewRuntimeLockDO.class,
            SupervisionAlertReviewRuntimeRunDO.class,
            SupervisionAlertReviewRuntimeOutboxDO.class,
            SupervisionAlertReviewRuntimeOutboxDeliveryDO.class,
            SupervisionAlertReviewReportAckDO.class
    );

    @Test
    void supervisionEntitiesUseTheirPostgresSequencesForInputIdMode() throws Exception {
        for (Class<?> entityType : POSTGRES_SEQUENCE_ENTITIES) {
            TableName tableName = entityType.getAnnotation(TableName.class);
            assertNotNull(tableName, entityType.getSimpleName());

            KeySequence keySequence = entityType.getAnnotation(KeySequence.class);
            assertNotNull(keySequence, entityType.getSimpleName());
            assertEquals(tableName.value() + "_id_seq", keySequence.value(), entityType.getSimpleName());

            Field id = entityType.getDeclaredField("id");
            assertEquals(Long.class, id.getType(), entityType.getSimpleName());
            assertNotNull(id.getAnnotation(TableId.class), entityType.getSimpleName());
        }
    }
}
