# Alert Review Phase 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add backend-led record evidence backfill, visual region-rule configuration entry points, and reverse event status projection to the alert review workbench.

**Architecture:** Keep the review service as the coordinator. VIDEO record lookup is hidden behind a `RecordEvidenceResolver` interface and the default implementation calls the existing VIDEO `/video/alert/record/query` endpoint when configured. Region geometry remains in existing device-region APIs; event state is read as a projection from `system_supervision_event`.

**Tech Stack:** Java Spring service/controller/mapper tests, PostgreSQL schema SQL, Vue 3 + TypeScript frontend.

---

### Task 1: Backend Record Evidence and Event Projection

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionAlertReviewServiceTest.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewService.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewServiceImpl.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/HttpAlertRecordEvidenceResolver.java`

- [ ] **Step 1: Write failing tests**

Add tests that prove:

```java
@Test
void ingestWithoutRecordUriBackfillsRecordEvidenceWhenResolverFindsRecord() {
    // resolver returns "record-from-video.mp4"
    // ingest clue has snapshotUri but no recordUri
    // timeline contains snapshot and record
    // aggregate recordEvidenceStatus is "found"
}

@Test
void ingestWithoutRecordUriMarksMissingWhenResolverHasNoRecord() {
    // resolver returns Optional.empty()
    // timeline contains snapshot only
    // aggregate recordEvidenceStatus is "missing"
}

@Test
void retryRecordEvidenceDoesNotDuplicateExistingRecordEvidence() {
    // first retry appends one record
    // second retry keeps one record
}

@Test
void convertedReviewItemCarriesLinkedEventProjection() {
    // event projection store returns eventStatus/closeCheckStatus/evidenceStatus
    // listWorkbench and findById aggregate include those fields
}
```

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest" -DfailIfNoTests=false -DforkCount=0 test
```

Expected: compile or assertion failures for missing new service contracts.

- [ ] **Step 3: Implement minimal service contracts**

Add records/interfaces:

```java
record RecordEvidenceRequest(String sourceAlertId, String deviceId, String cameraId, LocalDateTime alertTime) {}
record RecordEvidenceResult(String recordUri, String message) {}
record EventProjection(Long eventId, String eventStatus, String closeCheckStatus, String evidenceStatus) {}

interface RecordEvidenceResolver {
    Optional<RecordEvidenceResult> resolve(RecordEvidenceRequest request);
}

interface EventProjectionStore {
    Optional<EventProjection> findByEventId(Long eventId);
}
```

Add `retryRecordEvidence(Long reviewItemId)` and evidence status fields to `ReviewItemAggregate`. Add `HttpAlertRecordEvidenceResolver` that reads `yfeieye.video.alert-record-query-url` and calls the existing VIDEO endpoint with `device_id`, `alert_time`, `time_range`, and `alert_id`.

- [ ] **Step 4: Implement backfill flow**

When building evidence:
- If `recordUri` exists, append `record` and mark `found`.
- If no `recordUri`, call resolver.
- If resolver returns a URI, append `record` and mark `found`.
- If resolver returns empty or VIDEO is not configured, mark `missing`.
- If required lookup fields are missing or resolver throws, mark `failed`.

- [ ] **Step 5: Run tests and verify GREEN**

Run the same Maven command. Expected: test class passes.

### Task 2: Persistence, SQL, and Controller Endpoint

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/resources/sql/supervision_event_closure_v1.sql`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionAlertReviewItemDO.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionAlertReviewEvidenceMapper.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionEventMapper.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewMapperStore.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionAlertReviewController.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/review/AlertReviewVO.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionSchemaSqlTest.java`

- [ ] **Step 1: Write failing schema/mapper tests**

Extend schema test to assert `record_evidence_status`, `record_evidence_checked_at`, `record_evidence_message`, and an event projection index or lookup path exist.

- [ ] **Step 2: Run tests and verify RED**

Run:

```bash
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest,SupervisionAlertReviewServiceTest" -DfailIfNoTests=false -DforkCount=0 test
```

Expected: schema assertion failures.

- [ ] **Step 3: Implement persistence**

Add SQL columns, DO fields, mapper methods for dedupe insert and status update, and event projection lookup.

- [ ] **Step 4: Add retry endpoint**

Add:

```text
POST /system/supervision/alert-review/items/{reviewItemId}/record-evidence/retry
```

Return the updated item response with evidence and event projection fields.

- [ ] **Step 5: Run tests and verify GREEN**

Run the same Maven command. Expected: targeted tests pass.

### Task 3: Frontend Workbench Integration

**Files:**
- Modify: `WEB/src/api/supervision/alertReview.ts`
- Modify: `WEB/src/views/alert/components/AlertReviewWorkbench.vue`

- [ ] **Step 1: Add API types and retry wrapper**

Add fields:

```ts
recordEvidenceStatus?: 'not_required' | 'pending' | 'found' | 'missing' | 'failed' | string;
recordEvidenceCheckedAt?: string;
recordEvidenceMessage?: string;
eventStatus?: string;
closeCheckStatus?: string;
evidenceStatus?: string;
```

Add:

```ts
export function retryAlertReviewRecordEvidence(reviewItemId: number) {
  return defHttp.post<AlertReviewItem>({
    url: `${Api.Items}/${reviewItemId}/record-evidence/retry`,
  });
}
```

- [ ] **Step 2: Show evidence and event status**

Render a compact record evidence badge, a retry action for `missing`/`failed`, and linked event status fields when `eventId` exists.

- [ ] **Step 3: Add visual rule configuration entry**

Add a drawer section that reuses `DeviceRegionDrawer` for the selected item device/camera. On save, call existing `saveAlertReviewRule` with selected region name/code mapped to `zoneCode`.

- [ ] **Step 4: Verify typecheck impact**

Run:

```bash
$env:NODE_OPTIONS='--max-old-space-size=8192'; pnpm exec vue-tsc --noEmit --skipLibCheck --pretty false
```

Expected: no new errors mentioning `AlertReviewWorkbench.vue`, `alertReview.ts`, or `api/supervision`.

### Task 4: Final Verification

- [ ] Run targeted backend tests:

```bash
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false -DforkCount=0 test
```

- [ ] Run frontend typecheck filter and confirm no new workbench/API errors.
- [ ] Review `git diff` for scope creep.
