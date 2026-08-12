package com.basiclab.iot.system.supervision;

import com.basiclab.iot.common.core.aop.DataPermissionContextHolder;
import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewReportAckDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseAuditDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewExportJobDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewRuntimeRunDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSemanticIndexDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewSegmentDO;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseAuditMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewCaseMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewEvidenceMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewExportJobMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewIngestIdentityMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewItemMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewReportAckMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuleMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeLockMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeOutboxMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewRuntimeRunMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSegmentMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewSemanticIndexMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionAlertReviewUserStatusMapper;
import com.basiclab.iot.system.dal.pgsql.supervision.SupervisionEventMapper;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewMapperStore;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewEvidenceAuditQuery;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseView;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemDraft;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewItemAggregate;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewReportAcknowledgement;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewSemanticIndexEntry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewMapperStoreTest {

    @Test
    void runtimePatrolMetadataSerializesJavaTimeValues() {
        AtomicReference<SupervisionAlertReviewRuntimeRunDO> inserted = new AtomicReference<>();
        SupervisionAlertReviewRuntimeRunMapper runMapper = mapper(
                SupervisionAlertReviewRuntimeRunMapper.class,
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        inserted.set((SupervisionAlertReviewRuntimeRunDO) args[0]);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(
                runMapper, noopMapper(SupervisionAlertReviewRuntimeOutboxMapper.class));
        LocalDateTime lockedUntil = LocalDateTime.of(2026, 7, 13, 20, 30);

        store.recordRuntimePatrolRun(
                "healthy", 1, List.of(), List.of(), null, lockedUntil.minusMinutes(1),
                Map.of("lockedUntil", lockedUntil));

        assertTrue(inserted.get().getMetadata().contains("\"lockedUntil\":\"2026-07-13T20:30:00\""));
    }

    @Test
    void runtimeOutboxClaimRequiresAndPassesCurrentTenant() {
        AtomicReference<Long> claimedTenantId = new AtomicReference<>();
        AtomicBoolean dataPermissionIgnored = new AtomicBoolean();
        SupervisionAlertReviewRuntimeOutboxMapper outboxMapper = mapper(
                SupervisionAlertReviewRuntimeOutboxMapper.class,
                (proxy, method, args) -> {
                    if ("claimPending".equals(method.getName())) {
                        claimedTenantId.set((Long) args[0]);
                        dataPermissionIgnored.set(
                                DataPermissionContextHolder.get() != null
                                        && !DataPermissionContextHolder.get().enable());
                        return 0;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(
                noopMapper(SupervisionAlertReviewRuntimeRunMapper.class), outboxMapper);
        TenantContextHolder.clear();

        assertThrows(NullPointerException.class, () -> store.claimPendingRuntimeOutbox(
                10, "claim-no-tenant", null, LocalDateTime.now(), LocalDateTime.now().minusMinutes(10)));

        TenantContextHolder.setTenantId(42L);
        try {
            store.claimPendingRuntimeOutbox(
                    10, "claim-tenant-42", null, LocalDateTime.now(), LocalDateTime.now().minusMinutes(10));
            assertEquals(42L, claimedTenantId.get());
            assertTrue(dataPermissionIgnored.get());
            assertFalse(TenantContextHolder.isIgnore());
            assertNull(DataPermissionContextHolder.get());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void semanticIndexClaimRequiresAndPassesCurrentTenant() {
        AtomicReference<Object[]> claimArguments = new AtomicReference<>();
        AtomicBoolean dataPermissionIgnored = new AtomicBoolean();
        SupervisionAlertReviewSemanticIndexMapper semanticIndexMapper = mapper(
                SupervisionAlertReviewSemanticIndexMapper.class,
                (proxy, method, args) -> {
                    if ("claimProcessable".equals(method.getName())) {
                        claimArguments.set(args.clone());
                        dataPermissionIgnored.set(
                                DataPermissionContextHolder.get() != null
                                        && !DataPermissionContextHolder.get().enable());
                        return 0;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(semanticIndexMapper);
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 13, 20, 50);
        TenantContextHolder.clear();

        assertThrows(NullPointerException.class, () -> store.claimSemanticIndex(
                List.of(101L), 10, "semantic-no-tenant", claimedAt, claimedAt.plusMinutes(5)));

        TenantContextHolder.setTenantId(42L);
        try {
            store.claimSemanticIndex(
                    List.of(101L), 10, "semantic-tenant-42", claimedAt, claimedAt.plusMinutes(5));
            assertEquals(42L, claimArguments.get()[0]);
            assertEquals(List.of(101L), claimArguments.get()[1]);
            assertTrue(dataPermissionIgnored.get());
            assertFalse(TenantContextHolder.isIgnore());
            assertNull(DataPermissionContextHolder.get());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    void reportAcknowledgementInsertIsFirstWriterWinsAndReturnsDuplicateWinner() {
        AtomicReference<SupervisionAlertReviewReportAckDO> stored = new AtomicReference<>();
        AtomicReference<Long> insertedTenantId = new AtomicReference<>();
        AtomicInteger ordinaryInsertCount = new AtomicInteger();
        SupervisionAlertReviewReportAckMapper ackMapper = mapper(
                SupervisionAlertReviewReportAckMapper.class,
                (proxy, method, args) -> {
                    if ("insertIfAbsent".equals(method.getName())) {
                        insertedTenantId.set((Long) args[0]);
                        return stored.compareAndSet(null, (SupervisionAlertReviewReportAckDO) args[1]) ? 1 : 0;
                    }
                    if ("selectByTenantAndReportKey".equals(method.getName())) {
                        return stored.get();
                    }
                    if ("insert".equals(method.getName())) {
                        ordinaryInsertCount.incrementAndGet();
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(ackMapper);
        LocalDateTime firstAt = LocalDateTime.of(2026, 7, 13, 18, 0);
        ReviewReportAcknowledgement first = new ReviewReportAcknowledgement(
                "report-atomic", "shift", "acknowledged", 9001L, firstAt,
                "first", false, Map.of("periodStart", "2026-07-13T08:00:00"));
        ReviewReportAcknowledgement second = new ReviewReportAcknowledgement(
                "report-atomic", "shift", "acknowledged", 9002L, firstAt.plusSeconds(1),
                "second", false, Map.of("periodStart", "2026-07-13T08:00:00"));

        TenantContextHolder.setTenantId(42L);
        try {
            ReviewReportAcknowledgement inserted = store.saveReportAcknowledgement(first);
            ReviewReportAcknowledgement duplicate = store.saveReportAcknowledgement(second);

            assertFalse(inserted.duplicate());
            assertTrue(duplicate.duplicate());
            assertEquals(9001L, duplicate.acknowledgedBy());
            assertEquals("first", duplicate.note());
            assertEquals(firstAt, duplicate.acknowledgedAt());
            assertEquals(42L, insertedTenantId.get());
            assertEquals(0, ordinaryInsertCount.get());
        } finally {
            TenantContextHolder.clear();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsCurrentReviewCaseByItemAndReturnsEmptyWhenNoMembershipExists() throws Exception {
        SupervisionAlertReviewCaseItemDO membership = new SupervisionAlertReviewCaseItemDO()
                .setId(7001L)
                .setReviewCaseId(501L)
                .setReviewItemId(101L)
                .setSortOrder(1);
        SupervisionAlertReviewCaseItemMapper caseItemMapper = mapper(
                SupervisionAlertReviewCaseItemMapper.class,
                (proxy, method, args) -> {
                    if ("selectByReviewItemId".equals(method.getName())) {
                        return Long.valueOf(101L).equals(args[0]) ? List.of(membership) : List.of();
                    }
                    if ("selectByCaseId".equals(method.getName())) {
                        return List.of(membership);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewCaseMapper caseMapper = mapper(
                SupervisionAlertReviewCaseMapper.class,
                (proxy, method, args) -> {
                    if ("selectById".equals(method.getName())) {
                        return new SupervisionAlertReviewCaseDO()
                                .setId(501L)
                                .setCaseNo("RC-501")
                                .setTitle("existing case")
                                .setStatus("open")
                                .setPrimaryReviewItemId(101L)
                                .setCameraIds("camera-01")
                                .setOwnerUserId(9001L)
                                .setVersion(3);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(caseMapper, caseItemMapper);
        java.lang.reflect.Method lookup = SupervisionAlertReviewMapperStore.class
                .getMethod("findCaseByReviewItemId", Long.class);

        Optional<ReviewCaseView> found = (Optional<ReviewCaseView>) lookup.invoke(store, 101L);
        Optional<ReviewCaseView> missing = (Optional<ReviewCaseView>) lookup.invoke(store, 999L);

        assertEquals(501L, found.orElseThrow().id());
        assertEquals(List.of(101L), found.orElseThrow().reviewItemIds());
        assertTrue(missing.isEmpty());
    }

    @Test
    void evidenceDownloadAuditPersistsActualArchiveHashAndKeepsLogicalPackageHash() {
        String logicalPackageHash = "sha256:" + "1".repeat(64);
        String archiveHash = "sha256:" + "2".repeat(64);
        AtomicReference<SupervisionAlertReviewCaseAuditDO> insertedAudit = new AtomicReference<>();
        SupervisionAlertReviewExportJobMapper exportMapper = mapper(
                SupervisionAlertReviewExportJobMapper.class,
                (proxy, method, args) -> {
                    if ("selectByJobNo".equals(method.getName())) {
                        return new SupervisionAlertReviewExportJobDO()
                                .setJobNo("REJ-archive")
                                .setReviewCaseId(3001L)
                                .setStatus(SupervisionAlertReviewService.EXPORT_JOB_READY)
                                .setEvidenceUris("camera-01.mp4,camera-02.mp4")
                                .setBoundEventIds("7001")
                                .setFileHash(logicalPackageHash);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewCaseAuditMapper auditMapper = mapper(
                SupervisionAlertReviewCaseAuditMapper.class,
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        insertedAudit.set((SupervisionAlertReviewCaseAuditDO) args[0]);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(auditMapper, exportMapper);

        SupervisionAlertReviewService.ReviewEvidenceAuditEntry audit = store.recordEvidenceDownload(
                "REJ-archive",
                9001L,
                "regulator download",
                LocalDateTime.of(2026, 7, 11, 17, 0),
                archiveHash,
                Map.of("contentType", "application/zip", "contentLength", 4096L)
        );

        assertEquals(archiveHash, audit.fileHash());
        assertEquals(archiveHash, audit.metadata().get("downloadFileHash"));
        assertEquals(logicalPackageHash, audit.metadata().get("logicalPackageHash"));
        assertTrue(insertedAudit.get().getActionNote().contains("fileHash=" + archiveHash));
        assertTrue(insertedAudit.get().getMetadata().contains("\"downloadFileHash\":\"" + archiveHash + "\""));
        assertTrue(insertedAudit.get().getMetadata().contains("\"logicalPackageHash\":\"" + logicalPackageHash + "\""));
    }

    @Test
    void evidenceDownloadAuditRejectsMissingRealByteHashWithoutWritingAudit() {
        AtomicInteger auditInsertCount = new AtomicInteger();
        SupervisionAlertReviewExportJobMapper exportMapper = mapper(
                SupervisionAlertReviewExportJobMapper.class,
                (proxy, method, args) -> {
                    if ("selectByJobNo".equals(method.getName())) {
                        return new SupervisionAlertReviewExportJobDO()
                                .setJobNo("REJ-audit-only")
                                .setReviewCaseId(3002L)
                                .setStatus(SupervisionAlertReviewService.EXPORT_JOB_READY)
                                .setFileHash("sha256:" + "1".repeat(64));
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewCaseAuditMapper auditMapper = mapper(
                SupervisionAlertReviewCaseAuditMapper.class,
                (proxy, method, args) -> {
                    if ("insert".equals(method.getName())) {
                        auditInsertCount.incrementAndGet();
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(auditMapper, exportMapper);

        IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
                () -> store.recordEvidenceDownload(
                        "REJ-audit-only",
                        9001L,
                        "audit-only request",
                        LocalDateTime.of(2026, 7, 11, 17, 10),
                        null,
                        Map.of()
                ));

        assertTrue(rejected.getMessage().contains("downloadFileHash"));
        assertEquals(0, auditInsertCount.get());
    }

    @Test
    void semanticTriggerAuditUsesCurrentTenantAndToleratesMalformedLegacyMetadata() {
        AtomicReference<Object[]> lookupArgs = new AtomicReference<>();
        AtomicReference<Object[]> insertArgs = new AtomicReference<>();
        SupervisionAlertReviewCaseAuditMapper auditMapper = mapper(
                SupervisionAlertReviewCaseAuditMapper.class,
                (proxy, method, args) -> {
                    if ("selectSemanticTriggerAudits".equals(method.getName())) {
                        lookupArgs.set(args);
                        return List.of(
                                new SupervisionAlertReviewCaseAuditDO()
                                        .setReviewItemId(1001L)
                                        .setActionType("semantic_trigger_evaluated")
                                        .setMetadata("not-json")
                                        .setOperatorUserId(9001L)
                                        .setHappenedAt(LocalDateTime.of(2026, 7, 11, 10, 0)),
                                new SupervisionAlertReviewCaseAuditDO()
                                        .setReviewItemId(1001L)
                                        .setActionType("semantic_trigger_evaluated")
                                        .setMetadata("{\"schemaVersion\":\"semantic-trigger-evaluation-v1\",\"evaluationId\":\"sem-123e4567-e89b-42d3-a456-426614174000\",\"legacyNullable\":null}")
                                        .setOperatorUserId(9001L)
                                        .setHappenedAt(LocalDateTime.of(2026, 7, 11, 10, 1))
                        );
                    }
                    if ("insertSemanticTriggerDecision".equals(method.getName())) {
                        insertArgs.set(args);
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(
                auditMapper,
                noopMapper(SupervisionAlertReviewExportJobMapper.class)
        );
        String evaluationId = "sem-123e4567-e89b-42d3-a456-426614174000";

        try {
            TenantContextHolder.setTenantId(42L);
            List<SupervisionAlertReviewService.ReviewSemanticTriggerAuditRecord> audits =
                    store.listSemanticTriggerAudits(evaluationId);
            assertEquals(2, audits.size());
            assertTrue(audits.get(0).metadata().isEmpty());
            assertEquals(evaluationId, audits.get(1).metadata().get("evaluationId"));
            assertTrue(audits.get(1).metadata().containsKey("legacyNullable"));
            assertTrue(store.recordSemanticTriggerDecision(
                    evaluationId,
                    "semantic_trigger_confirmed",
                    "confirmed",
                    9100L,
                    LocalDateTime.of(2026, 7, 11, 10, 2),
                    Map.of("evaluationId", evaluationId)
            ));
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(List.of(42L, evaluationId), List.of(lookupArgs.get()));
        assertEquals(42L, insertArgs.get()[0]);
        assertEquals(evaluationId, insertArgs.get()[1]);
        assertEquals("semantic_trigger_confirmed", insertArgs.get()[2]);
        assertEquals(9100L, insertArgs.get()[5]);
    }

    @Test
    void evidenceAuditLookupPassesAllKeysAndCurrentTenantToBoundedMapperQueries() {
        AtomicReference<Object[]> exportArgs = new AtomicReference<>();
        AtomicReference<Object[]> auditArgs = new AtomicReference<>();
        SupervisionAlertReviewExportJobMapper exportMapper = mapper(
                SupervisionAlertReviewExportJobMapper.class,
                (proxy, method, args) -> {
                    if ("selectEvidenceAuditLookup".equals(method.getName())) {
                        exportArgs.set(args);
                        return List.of();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewCaseAuditMapper auditMapper = mapper(
                SupervisionAlertReviewCaseAuditMapper.class,
                (proxy, method, args) -> {
                    if ("selectEvidenceAuditLookup".equals(method.getName())) {
                        auditArgs.set(args);
                        return List.of();
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(auditMapper, exportMapper);
        ReviewEvidenceAuditQuery query = new ReviewEvidenceAuditQuery(7001L, 3001L, 1001L, "REJ-7001");

        try {
            TenantContextHolder.setTenantId(42L);
            store.listEvidenceAuditExportJobs(query);
            store.listEvidenceAuditRecords(query);
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(List.of(42L, 7001L, 3001L, 1001L, "REJ-7001"), List.of(exportArgs.get()));
        assertEquals(List.of(42L, 7001L, 3001L, 1001L, "REJ-7001"), List.of(auditArgs.get()));
    }

    @Test
    void evidenceAuditLookupFailsClosedWithoutTenantContext() {
        SupervisionAlertReviewMapperStore store = newStore(
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                noopMapper(SupervisionAlertReviewExportJobMapper.class)
        );

        SecurityException error = assertThrows(
                SecurityException.class,
                () -> store.listEvidenceAuditRecords(new ReviewEvidenceAuditQuery(7001L, null, null, null))
        );

        assertEquals("tenant context is required for evidence audit lookup", error.getMessage());
    }

    @Test
    void exportQueueClaimPassesCurrentTenantAndFailsClosedWithoutIt() {
        AtomicReference<Object[]> claimArgs = new AtomicReference<>();
        AtomicBoolean dataPermissionIgnored = new AtomicBoolean();
        SupervisionAlertReviewExportJobMapper exportMapper = mapper(
                SupervisionAlertReviewExportJobMapper.class,
                (proxy, method, args) -> {
                    if ("claimProcessable".equals(method.getName())) {
                        claimArgs.set(args);
                        dataPermissionIgnored.set(
                                DataPermissionContextHolder.get() != null
                                        && !DataPermissionContextHolder.get().enable());
                        return 0;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                exportMapper
        );
        LocalDateTime claimedAt = LocalDateTime.of(2026, 7, 13, 18, 36);
        LocalDateTime reclaimBefore = claimedAt.minusMinutes(10);

        try {
            TenantContextHolder.setTenantId(42L);
            assertTrue(store.claimProcessableExportJobs(
                    1, "claim-tenant-42", 9001L, claimedAt, reclaimBefore).isEmpty());
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(
                List.of(42L, 1, "claim-tenant-42", 9001L, claimedAt, reclaimBefore),
                List.of(claimArgs.get())
        );
        assertTrue(dataPermissionIgnored.get());
        assertFalse(TenantContextHolder.isIgnore());
        assertNull(DataPermissionContextHolder.get());
        SecurityException error = assertThrows(
                SecurityException.class,
                () -> store.claimProcessableExportJobs(
                        1, "claim-no-tenant", 9001L, claimedAt, reclaimBefore)
        );
        assertEquals("tenant context is required for export queue claim", error.getMessage());
    }

    @Test
    void appendClueLocksReviewItemRowBeforeMergingConcurrentSourceIds() {
        AtomicInteger lockedReads = new AtomicInteger();
        AtomicInteger unlockedReads = new AtomicInteger();
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 10, 8, 0);
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(
                SupervisionAlertReviewItemMapper.class,
                (proxy, method, args) -> {
                    if ("selectByIdForUpdate".equals(method.getName())) {
                        lockedReads.incrementAndGet();
                        return reviewItem(101L, SupervisionAlertReviewService.STATUS_PENDING_REVIEW, null, null);
                    }
                    if ("selectById".equals(method.getName())) {
                        unlockedReads.incrementAndGet();
                        return reviewItem(101L, SupervisionAlertReviewService.STATUS_PENDING_REVIEW, null, null);
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        store.appendClue(
                101L,
                "alert-row-lock",
                alertTime.plusSeconds(10),
                List.of(),
                Map.of(),
                "not_required",
                alertTime,
                null
        );

        assertEquals(1, lockedReads.get());
        assertEquals(0, unlockedReads.get());
    }

    @Test
    void createBindsTenantIdFromCurrentTenantContext() {
        AtomicReference<SupervisionAlertReviewItemDO> insertedItem = new AtomicReference<>();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(101L);
                insertedItem.set(itemDO);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(
                SupervisionAlertReviewSegmentMapper.class,
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        SupervisionAlertReviewCaseItemMapper reviewCaseItemMapper = mapper(
                SupervisionAlertReviewCaseItemMapper.class,
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                reviewCaseItemMapper
        );
        LocalDateTime alertTime = LocalDateTime.of(2026, 7, 4, 10, 15);

        try {
            TenantContextHolder.setTenantId(1001L);
            store.create(new ReviewItemDraft(
                    "video",
                    "alert-tenant-store",
                    "restricted_area",
                    "restricted_area",
                    alertTime,
                    "device-01",
                    "camera-01",
                    "zone-a",
                    "person",
                    "hash-tenant-store",
                    Map.of(),
                    "not_required",
                    alertTime,
                    null
            ), List.of());
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(1001L, insertedItem.get().getTenantId());
    }

    @Test
    void createRejectsOverlappingReviewSegmentBeforeSegmentInsert() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 4, 10, 15);
        LocalDateTime endTime = startTime.plusSeconds(30);
        AtomicInteger segmentInserts = new AtomicInteger();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(100L);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(SupervisionAlertReviewSegmentMapper.class, (proxy, method, args) -> {
            if ("selectOverlapping".equals(method.getName())) {
                return List.of(new SupervisionAlertReviewSegmentDO()
                        .setTenantId(0L)
                        .setReviewItemId(200L)
                        .setCameraId("camera-01")
                        .setStartTime(startTime.minusSeconds(10))
                        .setEndTime(startTime.plusSeconds(10)));
            }
            if ("insert".equals(method.getName())) {
                segmentInserts.incrementAndGet();
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewCaseItemMapper reviewCaseItemMapper = mapper(
                SupervisionAlertReviewCaseItemMapper.class,
                (proxy, method, args) -> defaultValue(method.getReturnType())
        );

        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                reviewCaseItemMapper
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.create(new ReviewItemDraft(
                        "video",
                        "alert-overlap-store",
                        "restricted_area",
                        "restricted_area",
                        startTime,
                        "device-01",
                        "camera-01",
                        "zone-a",
                        "person",
                        "hash-overlap-store",
                        Map.of("reviewSegment", Map.of(
                                "segmentId", "seg-overlap-store",
                                "cameraId", "camera-01",
                                "status", "alert",
                                "severity", "alert",
                                "startTime", startTime.toString(),
                                "endTime", endTime.toString(),
                                "objectIds", List.of("obj-overlap-store"),
                                "zones", List.of("zone-a"),
                                "sourceAlertIds", List.of("alert-overlap-store")
                        )),
                        "not_required",
                        startTime,
                        null
                ), List.of()));

        assertTrue(error.getMessage().contains("overlapping review segment"));
        assertEquals(0, segmentInserts.get());
    }

    @Test
    void createIgnoresDeletedOverlappingReviewSegmentBeforeSegmentInsert() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 4, 10, 20);
        LocalDateTime endTime = startTime.plusSeconds(30);
        AtomicInteger segmentInserts = new AtomicInteger();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(105L);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(SupervisionAlertReviewSegmentMapper.class, (proxy, method, args) -> {
            if ("selectOverlapping".equals(method.getName())) {
                SupervisionAlertReviewSegmentDO deletedOverlap = new SupervisionAlertReviewSegmentDO()
                        .setTenantId(0L)
                        .setReviewItemId(205L)
                        .setCameraId("camera-01")
                        .setStartTime(startTime.minusSeconds(10))
                        .setEndTime(startTime.plusSeconds(10));
                deletedOverlap.setDeleted(true);
                return List.of(deletedOverlap);
            }
            if ("insert".equals(method.getName())) {
                segmentInserts.incrementAndGet();
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        store.create(new ReviewItemDraft(
                "video",
                "alert-deleted-overlap-store",
                "restricted_area",
                "restricted_area",
                startTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                "hash-deleted-overlap-store",
                Map.of("reviewSegment", Map.of(
                        "segmentId", "seg-deleted-overlap-store",
                        "cameraId", "camera-01",
                        "status", "alert",
                        "severity", "alert",
                        "startTime", startTime.toString(),
                        "endTime", endTime.toString(),
                        "objectIds", List.of("obj-deleted-overlap-store"),
                        "zones", List.of("zone-a"),
                        "sourceAlertIds", List.of("alert-deleted-overlap-store")
                )),
                "not_required",
                startTime,
                null
        ), List.of());

        assertEquals(1, segmentInserts.get());
    }

    @Test
    void createRejectsReviewSegmentWithoutCameraBeforeSegmentInsert() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 4, 10, 25);
        AtomicInteger segmentInserts = new AtomicInteger();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(102L);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(SupervisionAlertReviewSegmentMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                segmentInserts.incrementAndGet();
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.create(new ReviewItemDraft(
                        "video",
                        "alert-missing-segment-camera",
                        "restricted_area",
                        "restricted_area",
                        startTime,
                        "device-01",
                        null,
                        "zone-a",
                        "person",
                        "hash-missing-segment-camera",
                        Map.of("reviewSegment", Map.of(
                                "segmentId", "seg-missing-camera",
                                "status", "alert",
                                "severity", "alert",
                                "startTime", startTime.toString(),
                                "endTime", startTime.plusSeconds(20).toString(),
                                "sourceAlertIds", List.of("alert-missing-segment-camera")
                        )),
                        "not_required",
                        startTime,
                        null
                ), List.of()));

        assertTrue(error.getMessage().contains("review segment cameraId is required"));
        assertEquals(0, segmentInserts.get());
    }

    @Test
    void createRejectsInvalidReviewSegmentStatusBeforeSegmentInsert() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 4, 10, 35);
        AtomicInteger segmentInserts = new AtomicInteger();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(103L);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(SupervisionAlertReviewSegmentMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                segmentInserts.incrementAndGet();
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> store.create(new ReviewItemDraft(
                        "video",
                        "alert-invalid-segment-status",
                        "restricted_area",
                        "restricted_area",
                        startTime,
                        "device-01",
                        "camera-01",
                        "zone-a",
                        "person",
                        "hash-invalid-segment-status",
                        Map.of("reviewSegment", Map.of(
                                "segmentId", "seg-invalid-status",
                                "cameraId", "camera-01",
                                "status", "paused",
                                "severity", "alert",
                                "startTime", startTime.toString(),
                                "endTime", startTime.plusSeconds(20).toString(),
                                "sourceAlertIds", List.of("alert-invalid-segment-status")
                        )),
                        "not_required",
                        startTime,
                        null
                ), List.of()));

        assertTrue(error.getMessage().contains("review segment status must be active, detection, alert, or ended"));
        assertEquals(0, segmentInserts.get());
    }

    @Test
    void createScopesReviewSegmentOverlapProbeByTenant() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 4, 10, 45);
        AtomicInteger overlapArgCount = new AtomicInteger();
        AtomicReference<Object[]> overlapArgs = new AtomicReference<>();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(101L);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(SupervisionAlertReviewSegmentMapper.class, (proxy, method, args) -> {
            if ("selectOverlapping".equals(method.getName())) {
                overlapArgCount.set(args == null ? 0 : args.length);
                overlapArgs.set(args);
                return List.of();
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        try {
            TenantContextHolder.setTenantId(1001L);
            store.create(new ReviewItemDraft(
                    "video",
                    "alert-tenant-segment-store",
                    "restricted_area",
                    "restricted_area",
                    startTime,
                    "device-01",
                    "camera-01",
                    "zone-a",
                    "person",
                    "hash-tenant-segment-store",
                    Map.of("reviewSegment", Map.of(
                            "segmentId", "seg-tenant-store",
                            "cameraId", "camera-01",
                            "status", "alert",
                            "severity", "alert",
                            "startTime", startTime.toString(),
                            "endTime", startTime.plusSeconds(20).toString(),
                            "objectIds", List.of("obj-tenant-store"),
                            "zones", List.of("zone-a"),
                            "sourceAlertIds", List.of("alert-tenant-segment-store")
                    )),
                    "not_required",
                    startTime,
                    null
            ), List.of());
        } finally {
            TenantContextHolder.clear();
        }

        assertEquals(4, overlapArgCount.get());
        assertEquals(1001L, overlapArgs.get()[0]);
        assertEquals("camera-01", overlapArgs.get()[1]);
    }

    @Test
    void createPersistsNonEndedReviewSegmentAsOpenInterval() {
        LocalDateTime startTime = LocalDateTime.of(2026, 7, 4, 10, 50);
        AtomicReference<Object[]> overlapArgs = new AtomicReference<>();
        AtomicReference<SupervisionAlertReviewSegmentDO> insertedSegment = new AtomicReference<>();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("insert".equals(method.getName())) {
                SupervisionAlertReviewItemDO itemDO = (SupervisionAlertReviewItemDO) args[0];
                itemDO.setId(104L);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewSegmentMapper reviewSegmentMapper = mapper(SupervisionAlertReviewSegmentMapper.class, (proxy, method, args) -> {
            if ("selectOverlapping".equals(method.getName())) {
                overlapArgs.set(args);
                return List.of();
            }
            if ("insert".equals(method.getName())) {
                insertedSegment.set((SupervisionAlertReviewSegmentDO) args[0]);
                return 1;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                reviewSegmentMapper,
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        store.create(new ReviewItemDraft(
                "video",
                "alert-open-segment-store",
                "restricted_area",
                "motion_detection",
                startTime,
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                "hash-open-segment-store",
                Map.of("reviewSegment", Map.of(
                        "segmentId", "seg-open-store",
                        "cameraId", "camera-01",
                        "status", "active",
                        "severity", "detection",
                        "startTime", startTime.toString(),
                        "endTime", startTime.toString(),
                        "objectIds", List.of("obj-open-store"),
                        "zones", List.of("zone-a"),
                        "sourceAlertIds", List.of("alert-open-segment-store")
                )),
                "not_required",
                startTime,
                null
        ), List.of());

        assertNull(overlapArgs.get()[3]);
        assertNull(insertedSegment.get().getEndTime());
        assertEquals("active", insertedSegment.get().getSegmentStatus());
    }

    @Test
    void updateReviewStatusRejectsConcurrentStatusConflict() {
        LocalDateTime firstReviewedAt = LocalDateTime.of(2026, 7, 4, 11, 0);
        LocalDateTime ignoredAt = LocalDateTime.of(2026, 7, 4, 11, 1);
        AtomicInteger selectCount = new AtomicInteger();
        AtomicInteger conditionalUpdateCount = new AtomicInteger();
        SupervisionAlertReviewItemMapper reviewItemMapper = mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
            if ("selectById".equals(method.getName())) {
                if (selectCount.incrementAndGet() == 1) {
                    return reviewItem(101L, SupervisionAlertReviewService.STATUS_PENDING_REVIEW, null, null);
                }
                return reviewItem(101L, SupervisionAlertReviewService.STATUS_REVIEWED, 9001L, firstReviewedAt);
            }
            if ("updateReviewStatusIfCurrent".equals(method.getName())) {
                conditionalUpdateCount.incrementAndGet();
                return 0;
            }
            return defaultValue(method.getReturnType());
        });
        SupervisionAlertReviewMapperStore store = newStore(
                reviewItemMapper,
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                noopMapper(SupervisionAlertReviewCaseItemMapper.class)
        );

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> store.updateReviewStatus(
                        101L,
                        SupervisionAlertReviewService.STATUS_IGNORED,
                        9002L,
                        "duplicate",
                        ignoredAt
                ));

        assertEquals("review_item_status_conflict: reviewed -> ignored", error.getMessage());
        assertEquals(1, conditionalUpdateCount.get());
    }

    @Test
    void queueSemanticIndexPreservesClaimAcquiredBetweenInsertAndCas() throws Exception {
        LocalDateTime queuedAt = LocalDateTime.of(2026, 7, 13, 10, 30);
        ReviewItemAggregate item = semanticReviewItem(9001L, queuedAt.minusMinutes(5));
        AtomicReference<SupervisionAlertReviewSemanticIndexDO> persisted = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        AtomicInteger selectCount = new AtomicInteger();
        List<String> mutations = new CopyOnWriteArrayList<>();
        CountDownLatch rowReady = new CountDownLatch(1);
        CountDownLatch workerClaimed = new CountDownLatch(1);
        SupervisionAlertReviewSemanticIndexMapper semanticIndexMapper = mapper(
                SupervisionAlertReviewSemanticIndexMapper.class,
                (proxy, method, args) -> {
                    if ("selectByReviewItemId".equals(method.getName())) {
                        int selection = selectCount.incrementAndGet();
                        SupervisionAlertReviewSemanticIndexDO current = persisted.get();
                        if (current != null) {
                            return copySemanticIndexRow(current);
                        }
                        if (selection == 1) {
                            return null;
                        }
                        SupervisionAlertReviewSemanticIndexDO pending = semanticIndexRow(
                                item, "sig-queue-cas", "pending", null, null);
                        persisted.set(copySemanticIndexRow(pending));
                        rowReady.countDown();
                        assertTrue(workerClaimed.await(5, TimeUnit.SECONDS));
                        return pending;
                    }
                    if ("insertPendingIfAbsent".equals(method.getName())) {
                        mutations.add("insert-if-absent");
                        persisted.compareAndSet(null, semanticIndexRow(
                                item, "sig-queue-cas", "pending", null, null));
                        rowReady.countDown();
                        assertTrue(workerClaimed.await(5, TimeUnit.SECONDS));
                        return 0;
                    }
                    if ("queueReindexUnlessActivelyClaimed".equals(method.getName())) {
                        mutations.add("conditional-cas");
                        SupervisionAlertReviewSemanticIndexDO current = persisted.get();
                        if ("processing".equals(current.getIndexStatus())
                                && current.getClaimToken() != null
                                && current.getClaimExpiresAt().isAfter((LocalDateTime) args[1])) {
                            return 0;
                        }
                        persisted.set(semanticIndexRow(item, "sig-queue-cas", "pending", null, null));
                        return 1;
                    }
                    if ("insert".equals(method.getName()) || "updateById".equals(method.getName())) {
                        mutations.add(method.getName());
                        persisted.set(copySemanticIndexRow((SupervisionAlertReviewSemanticIndexDO) args[0]));
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        SupervisionAlertReviewMapperStore store = newStore(semanticIndexMapper);
        Thread worker = new Thread(() -> {
            try {
                if (!rowReady.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("semantic index row was not initialized");
                }
                persisted.set(semanticIndexRow(
                        item,
                        "sig-queue-cas",
                        "processing",
                        "active-worker-claim",
                        queuedAt.plusMinutes(5)
                ));
            } catch (Throwable error) {
                workerFailure.set(error);
            } finally {
                workerClaimed.countDown();
            }
        }, "semantic-index-claim-race");
        worker.start();

        ReviewSemanticIndexEntry queued = store.queueSemanticIndex(
                item,
                "camera-01 person",
                "camera-01:9001",
                "local-hash-v1",
                "sig-queue-cas",
                queuedAt
        );
        worker.join(6000);

        assertNull(workerFailure.get());
        assertFalse(worker.isAlive());
        assertEquals(List.of("insert-if-absent", "conditional-cas"), mutations);
        assertEquals("processing", queued.indexStatus());
        assertEquals("active-worker-claim", queued.claimToken());
        assertEquals(queuedAt.plusMinutes(5), queued.claimExpiresAt());
    }

    private static SupervisionAlertReviewItemDO reviewItem(Long id,
                                                           String reviewStatus,
                                                           Long reviewerUserId,
                                                           LocalDateTime reviewedAt) {
        return new SupervisionAlertReviewItemDO()
                .setId(id)
                .setTenantId(0L)
                .setReviewItemNo("ARI-" + id)
                .setSourceSystem("video")
                .setRuleCode("restricted_area")
                .setSourceAlertType("restricted_area")
                .setDeviceId("device-01")
                .setCameraId("camera-01")
                .setZoneCode("zone-a")
                .setObjectLabel("person")
                .setFirstAlertTime(LocalDateTime.of(2026, 7, 4, 10, 55))
                .setLastAlertTime(LocalDateTime.of(2026, 7, 4, 10, 55))
                .setAlertCount(1)
                .setSourceAlertIds("alert-101")
                .setReviewData("{}")
                .setReviewStatus(reviewStatus)
                .setReviewerUserId(reviewerUserId)
                .setReviewedAt(reviewedAt)
                .setRecordEvidenceStatus("not_required")
                .setVersion(0);
    }

    private static ReviewItemAggregate semanticReviewItem(Long id, LocalDateTime alertTime) {
        return new ReviewItemAggregate(
                id,
                "ARI-" + id,
                "video",
                "restricted_area",
                "motion",
                "device-01",
                "camera-01",
                "zone-a",
                "person",
                alertTime,
                alertTime,
                1,
                List.of("alert-" + id),
                Map.of(),
                SupervisionAlertReviewService.STATUS_PENDING_REVIEW,
                null,
                null,
                null,
                Map.of(),
                null,
                null,
                "available",
                alertTime,
                null,
                null,
                null,
                null
        );
    }

    private static SupervisionAlertReviewSemanticIndexDO semanticIndexRow(ReviewItemAggregate item,
                                                                           String generationId,
                                                                           String status,
                                                                           String claimToken,
                                                                           LocalDateTime claimExpiresAt) {
        return new SupervisionAlertReviewSemanticIndexDO()
                .setId(7001L)
                .setReviewItemId(item.id())
                .setCameraId(item.cameraId())
                .setFirstAlertTime(item.firstAlertTime())
                .setLastAlertTime(item.lastAlertTime())
                .setIndexStatus(status)
                .setDocument("camera-01 person")
                .setEmbeddingKey("camera-01:9001")
                .setEmbeddingModel("local-hash-v1")
                .setRetryCount(0)
                .setIndexGenerationId(generationId)
                .setClaimToken(claimToken)
                .setClaimedAt(claimToken == null ? null : item.lastAlertTime())
                .setClaimExpiresAt(claimExpiresAt)
                .setVersion(1);
    }

    private static SupervisionAlertReviewSemanticIndexDO copySemanticIndexRow(
            SupervisionAlertReviewSemanticIndexDO source) {
        return new SupervisionAlertReviewSemanticIndexDO()
                .setId(source.getId())
                .setReviewItemId(source.getReviewItemId())
                .setCameraId(source.getCameraId())
                .setFirstAlertTime(source.getFirstAlertTime())
                .setLastAlertTime(source.getLastAlertTime())
                .setIndexStatus(source.getIndexStatus())
                .setDocument(source.getDocument())
                .setEmbeddingKey(source.getEmbeddingKey())
                .setEmbeddingModel(source.getEmbeddingModel())
                .setEmbeddingVectorHash(source.getEmbeddingVectorHash())
                .setRetryCount(source.getRetryCount())
                .setLastError(source.getLastError())
                .setIndexedAt(source.getIndexedAt())
                .setIndexGenerationId(source.getIndexGenerationId())
                .setClaimToken(source.getClaimToken())
                .setClaimedAt(source.getClaimedAt())
                .setClaimExpiresAt(source.getClaimExpiresAt())
                .setNextRetryAt(source.getNextRetryAt())
                .setVersion(source.getVersion());
    }

    private static SupervisionAlertReviewMapperStore newStore(SupervisionAlertReviewItemMapper reviewItemMapper,
                                                              SupervisionAlertReviewSegmentMapper reviewSegmentMapper,
                                                              SupervisionAlertReviewCaseItemMapper reviewCaseItemMapper) {
        return new SupervisionAlertReviewMapperStore(
                reviewItemMapper,
                noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                noopMapper(SupervisionAlertReviewRuleMapper.class),
                noopMapper(SupervisionAlertReviewCaseMapper.class),
                reviewCaseItemMapper,
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                noopMapper(SupervisionAlertReviewExportJobMapper.class),
                noopMapper(SupervisionAlertReviewSemanticIndexMapper.class),
                noopMapper(SupervisionAlertReviewUserStatusMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeLockMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeRunMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeOutboxMapper.class),
                reviewSegmentMapper,
                noopMapper(SupervisionAlertReviewReportAckMapper.class),
                noopMapper(SupervisionEventMapper.class)
        );
    }

    private static SupervisionAlertReviewMapperStore newStore(SupervisionAlertReviewCaseAuditMapper auditMapper,
                                                              SupervisionAlertReviewExportJobMapper exportMapper) {
        return new SupervisionAlertReviewMapperStore(
                noopMapper(SupervisionAlertReviewItemMapper.class),
                noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                noopMapper(SupervisionAlertReviewRuleMapper.class),
                noopMapper(SupervisionAlertReviewCaseMapper.class),
                noopMapper(SupervisionAlertReviewCaseItemMapper.class),
                auditMapper,
                exportMapper,
                noopMapper(SupervisionAlertReviewSemanticIndexMapper.class),
                noopMapper(SupervisionAlertReviewUserStatusMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeLockMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeRunMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeOutboxMapper.class),
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                noopMapper(SupervisionAlertReviewReportAckMapper.class),
                noopMapper(SupervisionEventMapper.class)
        );
    }

    private static SupervisionAlertReviewMapperStore newStore(SupervisionAlertReviewCaseMapper caseMapper,
                                                              SupervisionAlertReviewCaseItemMapper caseItemMapper) {
        return new SupervisionAlertReviewMapperStore(
                noopMapper(SupervisionAlertReviewItemMapper.class),
                noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                noopMapper(SupervisionAlertReviewRuleMapper.class),
                caseMapper,
                caseItemMapper,
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                noopMapper(SupervisionAlertReviewExportJobMapper.class),
                noopMapper(SupervisionAlertReviewSemanticIndexMapper.class),
                noopMapper(SupervisionAlertReviewUserStatusMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeLockMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeRunMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeOutboxMapper.class),
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                noopMapper(SupervisionAlertReviewReportAckMapper.class),
                noopMapper(SupervisionEventMapper.class)
        );
    }

    private static SupervisionAlertReviewMapperStore newStore(
            SupervisionAlertReviewSemanticIndexMapper semanticIndexMapper) {
        return new SupervisionAlertReviewMapperStore(
                noopMapper(SupervisionAlertReviewItemMapper.class),
                noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                noopMapper(SupervisionAlertReviewRuleMapper.class),
                noopMapper(SupervisionAlertReviewCaseMapper.class),
                noopMapper(SupervisionAlertReviewCaseItemMapper.class),
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                noopMapper(SupervisionAlertReviewExportJobMapper.class),
                semanticIndexMapper,
                noopMapper(SupervisionAlertReviewUserStatusMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeLockMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeRunMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeOutboxMapper.class),
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                noopMapper(SupervisionAlertReviewReportAckMapper.class),
                noopMapper(SupervisionEventMapper.class)
        );
    }

    private static SupervisionAlertReviewMapperStore newStore(
            SupervisionAlertReviewReportAckMapper reportAckMapper) {
        return new SupervisionAlertReviewMapperStore(
                noopMapper(SupervisionAlertReviewItemMapper.class),
                noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                noopMapper(SupervisionAlertReviewRuleMapper.class),
                noopMapper(SupervisionAlertReviewCaseMapper.class),
                noopMapper(SupervisionAlertReviewCaseItemMapper.class),
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                noopMapper(SupervisionAlertReviewExportJobMapper.class),
                noopMapper(SupervisionAlertReviewSemanticIndexMapper.class),
                noopMapper(SupervisionAlertReviewUserStatusMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeLockMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeRunMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeOutboxMapper.class),
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                reportAckMapper,
                noopMapper(SupervisionEventMapper.class)
        );
    }

    private static SupervisionAlertReviewMapperStore newStore(
            SupervisionAlertReviewRuntimeRunMapper runtimeRunMapper,
            SupervisionAlertReviewRuntimeOutboxMapper runtimeOutboxMapper) {
        return new SupervisionAlertReviewMapperStore(
                noopMapper(SupervisionAlertReviewItemMapper.class),
                noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                noopMapper(SupervisionAlertReviewRuleMapper.class),
                noopMapper(SupervisionAlertReviewCaseMapper.class),
                noopMapper(SupervisionAlertReviewCaseItemMapper.class),
                noopMapper(SupervisionAlertReviewCaseAuditMapper.class),
                noopMapper(SupervisionAlertReviewExportJobMapper.class),
                noopMapper(SupervisionAlertReviewSemanticIndexMapper.class),
                noopMapper(SupervisionAlertReviewUserStatusMapper.class),
                noopMapper(SupervisionAlertReviewRuntimeLockMapper.class),
                runtimeRunMapper,
                runtimeOutboxMapper,
                noopMapper(SupervisionAlertReviewSegmentMapper.class),
                noopMapper(SupervisionAlertReviewReportAckMapper.class),
                noopMapper(SupervisionEventMapper.class)
        );
    }

    private static <T> T noopMapper(Class<T> mapperType) {
        return mapper(mapperType, (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static <T> T mapper(Class<T> mapperType, InvocationHandler handler) {
        Object proxy = Proxy.newProxyInstance(
                mapperType.getClassLoader(),
                new Class<?>[]{mapperType},
                (proxyInstance, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> mapperType.getSimpleName() + "Proxy";
                            case "hashCode" -> System.identityHashCode(proxyInstance);
                            case "equals" -> proxyInstance == args[0];
                            default -> null;
                        };
                    }
                    return handler.invoke(proxyInstance, method, args);
                });
        return mapperType.cast(proxy);
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == Void.TYPE) {
            return null;
        }
        if (returnType == Boolean.TYPE) {
            return false;
        }
        if (returnType == Integer.TYPE) {
            return 0;
        }
        if (returnType == Long.TYPE) {
            return 0L;
        }
        if (List.class.isAssignableFrom(returnType)) {
            return List.of();
        }
        if (Map.class.isAssignableFrom(returnType)) {
            return Map.of();
        }
        if (Optional.class.isAssignableFrom(returnType)) {
            return Optional.empty();
        }
        return null;
    }

}
