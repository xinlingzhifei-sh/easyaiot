# Alert Review FR-13 to FR-16 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the yFeiEye alert review workbench with Frigate-inspired detail streams, asynchronous evidence export, safe rule application, semantic triggers, and shift/daily AI reports.

**Architecture:** Keep `SupervisionAlertReviewService` as the DEVICE-side review coordinator and keep VIDEO as the media/export worker. DEVICE computes review detail streams, rule shadow evaluation, semantic trigger matches, and review reports from existing review state. VIDEO owns asynchronous clipping/export job lifecycle, checksum, and download readiness.

**Tech Stack:** Java 21 Spring service tests, Python Flask service tests, PostgreSQL schema follow-up when persistence is added.

---

### Task 1: DEVICE Review Detail Stream

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionAlertReviewServiceTest.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewService.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewServiceImpl.java`

- [ ] **Step 1: Write failing test**

Add `reviewDetailStreamExpandsObjectsDetectionsAndSeekTimes()` that ingests a merged review item with two object IDs, labels, bbox data, detections, and motion path metadata. Assert `getReviewDetailStream(reviewItemId)` returns ordered lifecycle rows with `objectId`, `label`, `lifecycleEvent`, `happenedAt`, `seekTime`, `bbox`, `path`, `cameraId`, and `zoneCode`.

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewDetailStreamExpandsObjectsDetectionsAndSeekTimes" -DfailIfNoTests=false test
```

Expected: compile failure because `getReviewDetailStream` and `ReviewDetailStreamItem` do not exist.

- [ ] **Step 3: Implement minimal service contract**

Add `getReviewDetailStream(Long reviewItemId)` and `ReviewDetailStreamItem` to the service interface. In the service implementation, build stream items from `reviewData.detections`, `reviewData.objects`, timeline evidence, and `reviewData.motion.path`. Use `happenedAt` as `seekTime` so the UI can jump the video to the exact event.

- [ ] **Step 4: Run test to verify it passes**

Run the same Maven command. Expected: pass.

### Task 2: VIDEO Asynchronous Export Worker

**Files:**
- Modify: `VIDEO/test_record_export.py`
- Modify: `VIDEO/app/services/record_export_service.py`
- Modify: `VIDEO/app/blueprints/record.py`

- [ ] **Step 1: Write failing tests**

Add tests that prove `create_record_export(..., async_worker=True)` returns `pending` with an `export_id`, `poll_record_export(export_id)` advances through a worker runner to `ready`, returns a SHA-256 hash, and exposes a stable `download_url`. Add a route test for `GET /video/record/export/<export_id>`.

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
python -m pytest test_record_export.py -q
```

Expected: failures for missing worker methods and route.

- [ ] **Step 3: Implement minimal worker contract**

Add an in-memory export job registry with `pending`, `running`, `ready`, and `failed`. The default runner resolves an existing record URI; a supplied test runner can simulate ffmpeg clipping and return bytes. Store SHA-256 and expose the download URL only after the job is ready.

- [ ] **Step 4: Run tests to verify GREEN**

Run the same pytest command. Expected: pass.

### Task 3: DEVICE Rule Change Safe Apply

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionAlertReviewServiceTest.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewService.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewServiceImpl.java`

- [ ] **Step 1: Write failing test**

Add `ruleSuggestionSafeApplyRequiresApprovalAndStoresShadowEvaluation()` that marks two similar clues as false positives, verifies direct `applied` is rejected before `accepted`, then accepts/applies and asserts the suggestion contains `shadowEvaluation`, `estimatedSuppressedCount`, `beforeFalsePositiveRate`, `afterFalsePositiveRate`, `configVersion`, and rollback restores a new `rollbackVersion`.

- [ ] **Step 2: Run test to verify RED**

Run:

```powershell
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#ruleSuggestionSafeApplyRequiresApprovalAndStoresShadowEvaluation" -DfailIfNoTests=false test
```

Expected: direct apply is currently accepted or shadow fields are missing.

- [ ] **Step 3: Implement safe apply**

Require `accepted` before `applied`. Build shadow evaluation from recent workbench items in the same camera/zone/label scope. Store evaluation fields inside `ruleSuggestion` before saving the rule. Keep existing rollback behavior.

- [ ] **Step 4: Run test to verify GREEN**

Run the same Maven command. Expected: pass.

### Task 4: DEVICE Semantic Trigger and Report

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionAlertReviewServiceTest.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewService.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewServiceImpl.java`

- [ ] **Step 1: Write failing tests**

Add `semanticTriggerMatchesIndexedItemsAndReturnsActions()` and `shiftReportSummarizesReviewItemsAndEvidenceGaps()`. The trigger test should define a text trigger with actions `notification`, `sub_label`, and `attribute`, then assert matched review item IDs and action payloads. The report test should query a time range and assert `reportType`, `reviewItemIds`, `title`, `summary`, `structuredData.periodStart`, `structuredData.periodEnd`, and evidence gap count.

- [ ] **Step 2: Run tests to verify RED**

Run:

```powershell
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#semanticTriggerMatchesIndexedItemsAndReturnsActions,SupervisionAlertReviewServiceTest#shiftReportSummarizesReviewItemsAndEvidenceGaps" -DfailIfNoTests=false test
```

Expected: compile failure for missing trigger/report records and methods.

- [ ] **Step 3: Implement semantic trigger and report**

Use existing semantic search documents and filters for trigger matching. Build report summaries from `listWorkbench(query)` and reuse the existing structured AI summary vocabulary: evidence gaps, responsibility unit, threat level, and convertible state.

- [ ] **Step 4: Run tests to verify GREEN**

Run the same Maven command. Expected: pass.

### Task 5: Targeted Regression

- [ ] Run Java regression:

```powershell
mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest,HttpVideoResolverTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test
```

- [ ] Run VIDEO regression:

```powershell
python -m pytest test_record_export.py test_record_availability.py -q
```

- [ ] Run whitespace check:

```powershell
git diff --check
```
