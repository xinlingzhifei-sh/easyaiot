package com.basiclab.iot.system.supervision;

import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseAuditDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewCaseItemDO;
import com.basiclab.iot.system.dal.dataobject.supervision.SupervisionAlertReviewItemDO;
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
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseDraft;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseMergeResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseSplitResult;
import com.basiclab.iot.system.service.supervision.SupervisionAlertReviewService.ReviewCaseView;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupervisionAlertReviewCaseConcurrencyTest {

    @Test
    void caseContractsExposeVersionAndIdempotencyKey() {
        assertRecordComponents(SupervisionAlertReviewService.ReviewCaseOwnerCommand.class,
                "expectedVersion", "operationId");
        assertRecordComponents(SupervisionAlertReviewService.ReviewCaseOperationCommand.class,
                "expectedVersion", "operationId");
        assertRecordComponents(SupervisionAlertReviewService.ReviewCaseMergeCommand.class,
                "targetExpectedVersion", "sourceExpectedVersion", "operationId");
        assertRecordComponents(SupervisionAlertReviewService.ReviewCaseSplitCommand.class,
                "sourceExpectedVersion", "operationId");
        assertRecordComponents(ReviewCaseView.class, "version");
    }

    @Test
    void caseMapperLocksByTenantAndUsesCompareAndSetVersion() {
        Method lock = requiredMethod(SupervisionAlertReviewCaseMapper.class,
                "selectByIdForUpdate", Long.class, Long.class);
        String lockSql = String.join("\n", lock.getAnnotation(Select.class).value()).toLowerCase();
        assertTrue(lockSql.contains("tenant_id = #{tenantid}"));
        assertTrue(lockSql.contains("deleted = 0"));
        assertTrue(lockSql.contains("for update"));

        Method cas = requiredMethod(SupervisionAlertReviewCaseMapper.class,
                "updateIfVersion", Long.class, SupervisionAlertReviewCaseDO.class, Integer.class);
        String casSql = String.join("\n", cas.getAnnotation(Update.class).value()).toLowerCase();
        assertTrue(casSql.contains("version = #{casedo.version"));
        assertTrue(casSql.contains("version = #{expectedversion"));
        assertTrue(casSql.contains("tenant_id = #{tenantid}"));
        assertTrue(casSql.contains("deleted = 0"));
    }

    @Test
    void ownerAndCloseRetriesAreIdempotentAndStaleOwnerWriteConflicts() {
        CaseStoreHarness harness = new CaseStoreHarness();
        harness.addCase(10L, "open", 1001L, 0, List.of());
        SupervisionAlertReviewMapperStore store = harness.store();

        Method owner = requiredMethod(SupervisionAlertReviewMapperStore.class,
                "updateCaseOwner", Long.class, Long.class, String.class, Long.class, Integer.class, String.class);
        ReviewCaseView firstOwner = invoke(owner, store, 10L, 1002L, "handoff", 9001L, 0, "owner-click-1");
        ReviewCaseView repeatedOwner = invoke(owner, store, 10L, 1002L, "handoff", 9001L, 0, "owner-click-1");

        assertEquals(1002L, firstOwner.ownerUserId());
        assertEquals(1, version(firstOwner));
        assertEquals(1, version(repeatedOwner));
        assertEquals(1, harness.auditCount("assign_owner"));
        assertEquals(1, harness.casUpdateCount);

        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> invoke(owner, store, 10L, 1003L, "stale handoff", 9002L, 0, "owner-click-2"));
        assertTrue(conflict.getMessage().contains("case_version_conflict"));
        assertEquals(1, harness.auditCount("assign_owner"));

        Method close = requiredMethod(SupervisionAlertReviewMapperStore.class,
                "closeCase", Long.class, String.class, Long.class, LocalDateTime.class, Integer.class, String.class);
        ReviewCaseView closed = invoke(close, store, 10L, "resolved", 9003L,
                LocalDateTime.of(2026, 7, 11, 1, 0), 1, "close-click-1");
        ReviewCaseView repeatedClose = invoke(close, store, 10L, "resolved", 9003L,
                LocalDateTime.of(2026, 7, 11, 1, 1), 1, "close-click-1");

        assertEquals("closed", closed.status());
        assertEquals(2, version(closed));
        assertEquals(2, version(repeatedClose));
        assertEquals(1, harness.auditCount("close_case"));
        assertEquals(2, harness.casUpdateCount);
    }

    @Test
    void repeatedAddItemClickDoesNotAdvanceCaseVersionTwice() {
        CaseStoreHarness harness = new CaseStoreHarness();
        harness.addItem(101L, "camera-01");
        harness.addCase(10L, "open", 1001L, 0, List.of());
        SupervisionAlertReviewMapperStore store = harness.store();

        ReviewCaseView first = store.addCaseItem(10L, 101L);
        ReviewCaseView repeated = store.addCaseItem(10L, 101L);

        assertEquals(1, version(first));
        assertEquals(1, version(repeated));
        assertEquals(1, harness.auditCount("add_item"));
        assertEquals(1, harness.casUpdateCount);
    }

    @Test
    void mergeLocksCasesInStableOrderAndRepeatedOperationDoesNotMoveTwice() {
        CaseStoreHarness harness = new CaseStoreHarness();
        harness.addItem(101L, "camera-01");
        harness.addItem(102L, "camera-02");
        harness.addCase(20L, "open", 2001L, 0, List.of(101L));
        harness.addCase(11L, "open", 2002L, 0, List.of(102L));
        SupervisionAlertReviewMapperStore store = harness.store();
        Method merge = requiredMethod(SupervisionAlertReviewMapperStore.class,
                "mergeCases", Long.class, Long.class, Long.class, String.class,
                Integer.class, Integer.class, String.class);

        ReviewCaseMergeResult first = invoke(merge, store,
                20L, 11L, 9001L, "same incident", 0, 0, "merge-click-1");
        ReviewCaseMergeResult repeated = invoke(merge, store,
                20L, 11L, 9001L, "same incident", 0, 0, "merge-click-1");

        assertEquals(List.of(101L, 102L), first.targetCase().reviewItemIds());
        assertEquals("merged", first.sourceCase().status());
        assertEquals(List.of(101L, 102L), repeated.targetCase().reviewItemIds());
        assertEquals(2, harness.auditCount("merge_case"));
        assertEquals(List.of(11L, 20L, 11L, 20L), harness.lockedCaseIds);
        assertEquals(2, harness.casUpdateCount);
    }

    @Test
    void splitRetryReturnsOriginalNewCaseAndLeavesSingleAuditPair() {
        CaseStoreHarness harness = new CaseStoreHarness();
        harness.addItem(101L, "camera-01");
        harness.addItem(102L, "camera-02");
        harness.addCase(30L, "open", 3001L, 0, List.of(101L, 102L));
        SupervisionAlertReviewMapperStore store = harness.store();
        Method split = requiredMethod(SupervisionAlertReviewMapperStore.class,
                "splitCase", Long.class, ReviewCaseDraft.class, List.class, Long.class, Integer.class, String.class);
        ReviewCaseDraft draft = new ReviewCaseDraft("follow-up", 102L, 3002L, "separate lead");

        ReviewCaseSplitResult first = invoke(split, store,
                30L, draft, List.of(102L), 9001L, 0, "split-click-1");
        ReviewCaseSplitResult repeated = invoke(split, store,
                30L, draft, List.of(102L), 9001L, 0, "split-click-1");

        assertEquals(first.newCase().id(), repeated.newCase().id());
        assertEquals(List.of(101L), repeated.sourceCase().reviewItemIds());
        assertEquals(List.of(102L), repeated.newCase().reviewItemIds());
        assertEquals(2, harness.auditCount("split_case"));
        assertEquals(2, harness.cases.size());
        assertEquals(1, harness.casUpdateCount);
    }

    private static void assertRecordComponents(Class<?> recordType, String... requiredNames) {
        List<String> actual = Arrays.stream(recordType.getRecordComponents()).map(component -> component.getName()).toList();
        for (String requiredName : requiredNames) {
            assertTrue(actual.contains(requiredName), recordType.getSimpleName() + " missing " + requiredName);
        }
    }

    private static Method requiredMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            throw new AssertionError(type.getSimpleName() + " missing " + name + Arrays.toString(parameterTypes), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object target, Object... args) {
        try {
            return (T) method.invoke(target, args);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (ex.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(ex.getCause());
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static int version(ReviewCaseView view) {
        try {
            return (Integer) requiredMethod(ReviewCaseView.class, "version").invoke(view);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static final class CaseStoreHarness {

        private final Map<Long, SupervisionAlertReviewCaseDO> cases = new LinkedHashMap<>();
        private final Map<Long, SupervisionAlertReviewItemDO> items = new LinkedHashMap<>();
        private final List<SupervisionAlertReviewCaseItemDO> caseItems = new ArrayList<>();
        private final List<SupervisionAlertReviewCaseAuditDO> audits = new ArrayList<>();
        private final List<Long> lockedCaseIds = new ArrayList<>();
        private final AtomicLong caseIdSequence = new AtomicLong(100L);
        private final AtomicLong caseItemIdSequence = new AtomicLong(1000L);
        private final AtomicLong auditIdSequence = new AtomicLong(2000L);
        private int casUpdateCount;

        private void addItem(Long id, String cameraId) {
            LocalDateTime happenedAt = LocalDateTime.of(2026, 7, 11, 0, 0).plusSeconds(id);
            items.put(id, new SupervisionAlertReviewItemDO()
                    .setId(id)
                    .setTenantId(0L)
                    .setReviewItemNo("RI-" + id)
                    .setSourceSystem("video")
                    .setCameraId(cameraId)
                    .setFirstAlertTime(happenedAt)
                    .setLastAlertTime(happenedAt.plusSeconds(10))
                    .setAlertCount(1)
                    .setSourceAlertIds("alert-" + id)
                    .setReviewData("{}")
                    .setReviewStatus(SupervisionAlertReviewService.STATUS_PENDING_REVIEW)
                    .setRecordEvidenceStatus("not_required")
                    .setVersion(0));
        }

        private void addCase(Long id, String status, Long ownerUserId, int version, List<Long> reviewItemIds) {
            cases.put(id, new SupervisionAlertReviewCaseDO()
                    .setId(id)
                    .setCaseNo("RC-" + id)
                    .setTitle("case-" + id)
                    .setStatus(status)
                    .setPrimaryReviewItemId(reviewItemIds.isEmpty() ? null : reviewItemIds.get(0))
                    .setOwnerUserId(ownerUserId)
                    .setVersion(version));
            int sortOrder = 1;
            for (Long reviewItemId : reviewItemIds) {
                caseItems.add(new SupervisionAlertReviewCaseItemDO()
                        .setId(caseItemIdSequence.incrementAndGet())
                        .setReviewCaseId(id)
                        .setReviewItemId(reviewItemId)
                        .setSortOrder(sortOrder++)
                        .setAddedAt(LocalDateTime.of(2026, 7, 11, 0, 0))
                        .setVersion(0));
            }
        }

        private int auditCount(String actionType) {
            return (int) audits.stream().filter(audit -> actionType.equals(audit.getActionType())).count();
        }

        private SupervisionAlertReviewMapperStore store() {
            return new SupervisionAlertReviewMapperStore(
                    itemMapper(),
                    noopMapper(SupervisionAlertReviewEvidenceMapper.class),
                    noopMapper(SupervisionAlertReviewIngestIdentityMapper.class),
                    noopMapper(SupervisionAlertReviewRuleMapper.class),
                    caseMapper(),
                    caseItemMapper(),
                    caseAuditMapper(),
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

        private SupervisionAlertReviewItemMapper itemMapper() {
            return mapper(SupervisionAlertReviewItemMapper.class, (proxy, method, args) -> {
                if ("selectById".equals(method.getName())) {
                    return items.get((Long) args[0]);
                }
                return defaultValue(method.getReturnType());
            });
        }

        private SupervisionAlertReviewCaseMapper caseMapper() {
            return mapper(SupervisionAlertReviewCaseMapper.class, (proxy, method, args) -> switch (method.getName()) {
                case "selectByIdForUpdate" -> {
                    Long id = (Long) args[1];
                    lockedCaseIds.add(id);
                    yield copy(cases.get(id));
                }
                case "selectById" -> copy(cases.get((Long) args[0]));
                case "insert" -> {
                    SupervisionAlertReviewCaseDO inserted = copy((SupervisionAlertReviewCaseDO) args[0]);
                    long id = caseIdSequence.incrementAndGet();
                    inserted.setId(id);
                    ((SupervisionAlertReviewCaseDO) args[0]).setId(id);
                    cases.put(id, inserted);
                    yield 1;
                }
                case "updateIfVersion" -> {
                    SupervisionAlertReviewCaseDO update = (SupervisionAlertReviewCaseDO) args[1];
                    Integer expectedVersion = (Integer) args[2];
                    SupervisionAlertReviewCaseDO current = cases.get(update.getId());
                    if (current == null || !Objects.equals(expectedVersion, current.getVersion())) {
                        yield 0;
                    }
                    cases.put(update.getId(), copy(update));
                    casUpdateCount++;
                    yield 1;
                }
                default -> defaultValue(method.getReturnType());
            });
        }

        private SupervisionAlertReviewCaseItemMapper caseItemMapper() {
            return mapper(SupervisionAlertReviewCaseItemMapper.class, (proxy, method, args) -> switch (method.getName()) {
                case "selectByCaseId" -> caseItems.stream()
                        .filter(item -> Objects.equals(item.getReviewCaseId(), args[0]))
                        .sorted(java.util.Comparator.comparing(SupervisionAlertReviewCaseItemDO::getSortOrder))
                        .toList();
                case "selectExisting" -> caseItems.stream()
                        .filter(item -> Objects.equals(item.getReviewCaseId(), args[0]))
                        .filter(item -> Objects.equals(item.getReviewItemId(), args[1]))
                        .findFirst().orElse(null);
                case "insert" -> {
                    SupervisionAlertReviewCaseItemDO item = (SupervisionAlertReviewCaseItemDO) args[0];
                    item.setId(caseItemIdSequence.incrementAndGet());
                    caseItems.add(item);
                    yield 1;
                }
                case "deleteById" -> {
                    Long id = args[0] instanceof SupervisionAlertReviewCaseItemDO item ? item.getId() : (Long) args[0];
                    boolean removed = caseItems.removeIf(item -> Objects.equals(item.getId(), id));
                    yield removed ? 1 : 0;
                }
                case "selectByReviewItemId" -> caseItems.stream()
                        .filter(item -> Objects.equals(item.getReviewItemId(), args[0]))
                        .toList();
                default -> defaultValue(method.getReturnType());
            });
        }

        private SupervisionAlertReviewCaseAuditMapper caseAuditMapper() {
            return mapper(SupervisionAlertReviewCaseAuditMapper.class, (proxy, method, args) -> switch (method.getName()) {
                case "insert" -> {
                    SupervisionAlertReviewCaseAuditDO audit = (SupervisionAlertReviewCaseAuditDO) args[0];
                    audit.setId(auditIdSequence.incrementAndGet());
                    audits.add(audit);
                    yield 1;
                }
                case "selectByOperationId" -> audits.stream()
                        .filter(audit -> Objects.equals(audit.getActionType(), args[1]))
                        .filter(audit -> audit.getMetadata() != null
                                && audit.getMetadata().contains("\"operationId\":\"" + args[2] + "\""))
                        .toList();
                case "selectByCaseId" -> audits.stream()
                        .filter(audit -> Objects.equals(audit.getReviewCaseId(), args[0]))
                        .toList();
                default -> defaultValue(method.getReturnType());
            });
        }

        private static SupervisionAlertReviewCaseDO copy(SupervisionAlertReviewCaseDO source) {
            if (source == null) {
                return null;
            }
            return new SupervisionAlertReviewCaseDO()
                    .setId(source.getId())
                    .setCaseNo(source.getCaseNo())
                    .setTitle(source.getTitle())
                    .setStatus(source.getStatus())
                    .setPrimaryReviewItemId(source.getPrimaryReviewItemId())
                    .setOwnerUserId(source.getOwnerUserId())
                    .setNotes(source.getNotes())
                    .setCameraIds(source.getCameraIds())
                    .setStartTime(source.getStartTime())
                    .setEndTime(source.getEndTime())
                    .setVersion(source.getVersion());
        }
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
