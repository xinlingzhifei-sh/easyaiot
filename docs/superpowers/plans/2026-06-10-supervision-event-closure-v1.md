# Supervision Event Closure V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first yFeiEye supervision-event closure loop without changing existing alert, image, video, notification, device, or playback behavior.

**Architecture:** Add a new supervision subdomain in `DEVICE/iot-system` for events, tasks, evidence, close checks, rules, and permissions. Keep `Alert` as a read-only source and evidence entry; expose additive `/system/supervision/*` APIs; add isolated `WEB` pages and only then add a permission-gated "监管事件" action to the existing alert page.

**Tech Stack:** Java Spring Boot, MyBatis Plus, PostgreSQL-compatible SQL, yFeiEye `CommonResult`/`PageResult`, Vue 3, Vite, Ant Design Vue, existing `BasicTable` and `defHttp` API patterns.

---

## Safety Rules

1. Do not change the existing alert query API, alert clear API, image modal, video modal, device location drawer, or playback navigation behavior.
2. Do not add lifecycle fields to the existing `Alert` record. Link by `source_system + source_alert_id`.
3. Do not change existing database tables in the first pass. Add new `system_supervision_*` tables only.
4. Do not expose the new UI to all users by default. Route and actions must be guarded by new permissions.
5. Do not delete or rewrite any existing docs or generated output. The repository already contains uncommitted documentation changes; keep implementation commits separated from those changes.
6. Every phase must be reversible by hiding the new menu/permission and not calling the new APIs from existing workflows.

## Micro-Feature Gate

Implementation must proceed one micro-feature at a time. A micro-feature is the smallest user-visible or service-visible behavior that can be tested and rolled back independently.

No micro-feature is considered complete until all five gates pass:

1. **Behavior gate:** the exact behavior in the micro-feature works in isolation.
2. **Regression gate:** existing alert list, image preview, video playback, clear alerts, map tab, and device location behavior are unchanged when the micro-feature touches `WEB/src/views/alert`.
3. **Permission gate:** unauthorized users cannot see or call the new action; authorized users can.
4. **Data gate:** no existing alert, device, video, user, role, or notice table is modified by the feature unless the task explicitly says so.
5. **Verification gate:** focused tests, compile/typecheck, and the feature smoke command pass before moving on.

Absolute "zero bugs" cannot be proven by assertion. The engineering rule for this plan is stronger and measurable: do not proceed to the next micro-feature while any known defect, failed test, failed typecheck, failed build, or unresolved regression remains.

## Micro-Feature Order

| Micro-feature | Scope | Must pass before next |
| --- | --- | --- |
| MF-00 Contract freeze | Confirm docs, boundaries, and no-regression baseline | Baseline commands recorded |
| MF-01 Backend enums | Add event/task/close enums only | Maven compile |
| MF-02 Additive schema | Add `system_supervision_*` tables only | SQL is additive and existing tables untouched |
| MF-03 Rule seeds | Add P0 rule seed constants only | Rule-code lookup test passes |
| MF-04 Event create from alert | Idempotent `Alert -> Event` backend service | Duplicate alert returns same open event |
| MF-05 Event query/detail | Event list/detail/evidence read APIs | Read APIs return created event and evidence |
| MF-06 Task dispatch | Create task from event | Event moves to `dispatched`, no close side effects |
| MF-07 Task accept | Assigned user accepts task | Event moves to `accepted` |
| MF-08 Task submit | Handler submits result | Event moves to `pending_close_check` or `pending_recheck` |
| MF-09 Event center UI | New isolated event list/detail pages | Existing alert page still unchanged |
| MF-10 My tasks UI | New isolated my-task page | Task accept/submit works from isolated page |
| MF-11 Alert page entry | Add permission-gated alert action | Image/video/list actions still work |
| MF-12 Recheck/rework | Approve/reject/transfer review | Rejected event requires supplement |
| MF-13 Close check/close | Hard blocks, warnings, close action | Missing evidence/permission blocks close |
| MF-14 Evidence timeline | Timeline and evidence read model | Lifecycle steps appear in order |
| MF-15 Permission seed | Menu/action/data permission boundaries | Unauthorized access blocked |
| MF-16 Acceptance runbook | P0 scripts and smoke checks | All scripts have input, steps, expected result |

Each micro-feature should be committed or at least reviewed as a separate diff before starting the next one. If the user asks to batch commits later, still keep the working diff inspectable by micro-feature.

## Per-Feature Execution Template

Use this template for every micro-feature from MF-01 onward:

1. **Scope lock**
   - Name the micro-feature.
   - List the exact files allowed to change.
   - List the existing behaviors that must not change.

2. **Red test**
   - Add the smallest failing unit, service, API, or UI test for the behavior.
   - Run the focused test and confirm it fails for the expected reason.

3. **Minimal implementation**
   - Implement only enough code to pass the failing test.
   - Do not add adjacent cleanup or speculative configuration.

4. **Green verification**
   - Re-run the focused test.
   - Run the compile/typecheck command for the touched layer.

5. **Regression verification**
   - For backend-only changes, run Maven compile for `iot-system-api` and `iot-system-biz`.
   - For frontend-only changes, run `pnpm type:check`.
   - For alert-page changes, additionally search for and manually smoke the existing image/video/list handlers.

6. **Review checkpoint**
   - Summarize changed files.
   - Summarize tests run and results.
   - State known residual risk.
   - Do not start the next micro-feature until defects are fixed or explicitly accepted.

## Baseline Commands

Run before the first code task and again after each milestone:

```powershell
git status --short
cd DEVICE
mvn -pl iot-system/iot-system-biz -am -DskipTests compile
cd ..\WEB
pnpm type:check
```

Expected:

1. `git status --short` shows only intentional work.
2. Maven compile succeeds or fails only for a pre-existing environment dependency that is documented in the task result.
3. `pnpm type:check` succeeds or reports a pre-existing type issue unrelated to changed files.

## File Map

### Backend

Create:

- `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionEventStatusEnum.java`
- `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionEventLevelEnum.java`
- `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionTaskStatusEnum.java`
- `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionCloseResultEnum.java`
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionEventController.java`
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionTaskController.java`
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/*.java`
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/*.java`
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/*.java`
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/*.java`
- `DEVICE/iot-system/iot-system-biz/src/main/resources/sql/supervision_event_closure_v1.sql`
- `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/*.java`

Modify:

- `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/ErrorCodeConstants.java`
- `DEVICE/iot-system/iot-system-biz/src/test/resources/sql/create_tables.sql`
- `DEVICE/iot-system/iot-system-biz/src/test/resources/sql/clean.sql`

### Frontend

Create:

- `WEB/src/api/system/supervision/event.ts`
- `WEB/src/views/supervision/event/index.vue`
- `WEB/src/views/supervision/event/detail.vue`
- `WEB/src/views/supervision/event/event.data.ts`
- `WEB/src/views/supervision/task/my.vue`
- `WEB/src/views/supervision/task/task.data.ts`

Modify:

- `WEB/src/views/alert/index.vue`
- `WEB/src/views/alert/components/AlertCards/index.vue`
- `WEB/src/router/routes/modules/supervision.ts`

### Docs And Acceptance

Create:

- `docs/prd/tasks/supervision-event-closure-v1/p0-acceptance-runbook.md`

Modify:

- `docs/prd/tasks/supervision-event-closure-v1/README.md`

---

## Milestone A: Tasks 01-04

This milestone proves the non-invasive loop:

`existing alert -> supervision event -> disposal task -> accept -> submit`

### Task 01: Freeze Contract And Establish Non-Regression Baseline

**Files:**

- Read: `docs/prd/supervision-event-closure-v1-engineering-contract.md`
- Read: `docs/prd/supervision-event-closure-v1-development-list.md`
- Read: `WEB/src/views/alert/index.vue`
- Read: `WEB/src/views/alert/components/AlertCards/index.vue`
- Read: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/notice/NoticeController.java`

- [ ] **Step 1: Confirm contract inputs**

Run:

```powershell
rg -n "Alert|Supervision Event|Disposal Task|Evidence Chain|P0 初始规则种子" docs/prd/supervision-event-closure-v1-engineering-contract.md
```

Expected: matches for all five contract terms.

- [ ] **Step 2: Confirm existing alert behavior touchpoints**

Run:

```powershell
rg -n "queryAlarmList|clearAllAlerts|handleViewImage|handleViewVideo|DialogPlayer|ImageModal" WEB/src/views/alert/index.vue WEB/src/views/alert/components/AlertCards/index.vue
```

Expected: existing alert list, clear, image, and video behavior is visible before implementation.

- [ ] **Step 3: Record baseline build**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -am -DskipTests compile
cd ..\WEB
pnpm type:check
```

Expected: compile/typecheck pass, or failures are recorded as pre-existing and unrelated to supervision files.

- [ ] **Step 4: Commit only if the tree is scoped**

Run:

```powershell
git status --short
```

Expected: no unrelated implementation files. If docs are still uncommitted, keep code commits separate from docs changes.

### Task 02: Add Backend Schema, Enums, And Rule Seeds

**Files:**

- Create: `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionEventStatusEnum.java`
- Create: `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionEventLevelEnum.java`
- Create: `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionTaskStatusEnum.java`
- Create: `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/supervision/SupervisionCloseResultEnum.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/resources/sql/supervision_event_closure_v1.sql`
- Modify: `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/ErrorCodeConstants.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/resources/sql/create_tables.sql`
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/resources/sql/clean.sql`

- [ ] **Step 1: Add enum classes**

Use numeric values in Java enums to match existing system enum style. Add these exact semantic values:

```java
CREATED, DISPATCHED, ACCEPTED, HANDLING, PENDING_RECHECK,
REWORK_REQUIRED, PENDING_CLOSE_CHECK, EXCEPTION_REVIEW,
TRANSFERRED_MAJOR, CLOSED
```

For event levels:

```java
L1, L2, L3, L4
```

For task status:

```java
PENDING, SENT, ACKNOWLEDGED, HANDLING, SUBMITTED, APPROVED, REJECTED, TIMEOUT, CLOSED, CANCELLED
```

For close result:

```java
CONFIRMED_HANDLED, FALSE_ALARM, UNABLE_TO_CONFIRM, TRANSFERRED_MAJOR, DUPLICATE_MERGED
```

- [ ] **Step 2: Add additive SQL tables**

Create these new tables in `supervision_event_closure_v1.sql`:

```sql
CREATE TABLE IF NOT EXISTS system_supervision_event (
  id BIGSERIAL PRIMARY KEY,
  event_no VARCHAR(64) NOT NULL,
  tenant_id BIGINT,
  org_id BIGINT,
  site_type VARCHAR(32) NOT NULL DEFAULT 'prison',
  source_system VARCHAR(64) NOT NULL,
  source_alert_id VARCHAR(128),
  source_alert_type VARCHAR(128),
  source_alert_time TIMESTAMP,
  source_payload_hash VARCHAR(128),
  device_id VARCHAR(128),
  camera_id VARCHAR(128),
  location_id VARCHAR(128),
  person_id VARCHAR(128),
  person_confidence NUMERIC(8,4),
  event_type VARCHAR(64) NOT NULL,
  event_level VARCHAR(8) NOT NULL,
  event_status VARCHAR(64) NOT NULL,
  current_owner_dept_id BIGINT,
  current_owner_user_id BIGINT,
  close_result VARCHAR(64),
  close_reason TEXT,
  close_check_status VARCHAR(64) NOT NULL DEFAULT 'not_checked',
  evidence_status VARCHAR(64) NOT NULL DEFAULT 'missing_soft',
  sensitivity_level VARCHAR(64) NOT NULL DEFAULT 'normal',
  upgraded_from_level VARCHAR(8),
  upgrade_reason TEXT,
  merged_into_event_id BIGINT,
  dispatched_at TIMESTAMP,
  accepted_at TIMESTAMP,
  handled_at TIMESTAMP,
  rechecked_at TIMESTAMP,
  closed_at TIMESTAMP,
  version INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_supervision_event_open_alert
ON system_supervision_event(source_system, source_alert_id)
WHERE deleted = FALSE AND event_status <> 'closed' AND source_alert_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS system_supervision_task (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  task_no VARCHAR(64) NOT NULL,
  task_type VARCHAR(64) NOT NULL,
  task_status VARCHAR(64) NOT NULL,
  assigned_dept_id BIGINT,
  assigned_role VARCHAR(64) NOT NULL,
  assigned_user_id BIGINT,
  due_at TIMESTAMP,
  accepted_at TIMESTAMP,
  arrived_at TIMESTAMP,
  submitted_at TIMESTAMP,
  result_category VARCHAR(64),
  handling_note TEXT,
  rework_count INTEGER NOT NULL DEFAULT 0,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_supervision_task_event_id
ON system_supervision_task(event_id);

CREATE TABLE IF NOT EXISTS system_supervision_action (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  task_id BIGINT,
  action_type VARCHAR(64) NOT NULL,
  channel VARCHAR(64),
  action_status VARCHAR(64) NOT NULL,
  receiver_user_id BIGINT,
  result_payload TEXT,
  failure_reason TEXT,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_supervision_action_event_id
ON system_supervision_action(event_id);

CREATE TABLE IF NOT EXISTS system_supervision_evidence_item (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  source_type VARCHAR(64) NOT NULL,
  material_type VARCHAR(64) NOT NULL,
  material_uri VARCHAR(512),
  related_record_id VARCHAR(128),
  is_required BOOLEAN NOT NULL DEFAULT FALSE,
  required_for_level VARCHAR(8),
  collect_status VARCHAR(64) NOT NULL,
  missing_reason TEXT,
  sensitivity_level VARCHAR(64) NOT NULL DEFAULT 'normal',
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_supervision_evidence_event_id
ON system_supervision_evidence_item(event_id);

CREATE TABLE IF NOT EXISTS system_supervision_close_check_result (
  id BIGSERIAL PRIMARY KEY,
  event_id BIGINT NOT NULL,
  rule_version VARCHAR(64) NOT NULL,
  check_result VARCHAR(64) NOT NULL,
  hard_block_items TEXT NOT NULL,
  soft_warning_items TEXT NOT NULL,
  exception_reason TEXT,
  checked_by BIGINT,
  checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  creator VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updater VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted BOOLEAN NOT NULL DEFAULT FALSE
);
```

- [ ] **Step 3: Add P0 rule seed constants**

Add seed rows as Java constants in a new service helper class:

`DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionRuleSeeds.java`

Use the rule codes from `docs/prd/supervision-event-closure-v1-engineering-contract.md`:

```java
RULE_DEVICE_OFFLINE_NORMAL
RULE_CAMERA_OFFLINE_KEY_AREA
RULE_FALL_DOWN
RULE_SUDDEN_ILLNESS
RULE_FIGHT
RULE_RESTRICTED_AREA
RULE_ABNORMAL_GATHERING
RULE_REHAB_WITHDRAWAL
RULE_RED_PHYSIOLOGY
```

- [ ] **Step 4: Verify compile**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-api,iot-system/iot-system-biz -am -DskipTests compile
```

Expected: compile succeeds.

- [ ] **Step 5: Non-regression check**

Run:

```powershell
rg -n "system_supervision_" DEVICE/iot-system
rg -n "queryAlarmList|handleViewImage|handleViewVideo" ../WEB/src/views/alert/index.vue
```

Expected: new supervision SQL exists; existing alert handlers still exist.

### Task 03: Add Event Model, Idempotent Alert-To-Event Service, And Query APIs

**Files:**

- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionEventDO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionEvidenceItemDO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionEventMapper.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionEvidenceItemMapper.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionEventFromAlertReqVO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionEventRespVO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionEventPageReqVO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionEventService.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionEventServiceImpl.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionEventController.java`
- Test: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionEventServiceImplTest.java`

- [ ] **Step 1: Write failing idempotency test**

Test case:

```java
@Test
void createFromAlert_reusesOpenEventForSameAlert() {
    SupervisionEventFromAlertReqVO reqVO = new SupervisionEventFromAlertReqVO();
    reqVO.setSourceSystem("video");
    reqVO.setSourceAlertId("alert-001");
    reqVO.setSourceAlertType("device_offline");
    reqVO.setEventType("device_availability");

    Long firstId = supervisionEventService.createFromAlert(reqVO).getEventId();
    Long secondId = supervisionEventService.createFromAlert(reqVO).getEventId();

    assertEquals(firstId, secondId);
}
```

Expected before implementation: fails because service does not exist.

- [ ] **Step 2: Implement DO and mapper**

Use `BaseDO`, `@TableName("system_supervision_event")`, and mapper style matching `NoticeMapper`.

Mapper must include:

```java
default SupervisionEventDO selectOpenBySourceAlert(String sourceSystem, String sourceAlertId) {
    return selectOne(new LambdaQueryWrapperX<SupervisionEventDO>()
            .eq(SupervisionEventDO::getSourceSystem, sourceSystem)
            .eq(SupervisionEventDO::getSourceAlertId, sourceAlertId)
            .ne(SupervisionEventDO::getEventStatus, "closed"));
}
```

- [ ] **Step 3: Implement service idempotency**

`createFromAlert` must:

1. Look up open event by `source_system + source_alert_id`.
2. Return the existing event if found.
3. Create a new event if none exists.
4. Create initial evidence item with `source_type = alert`.
5. Never update or delete the existing alert row.

- [ ] **Step 4: Add read APIs**

Controller endpoints:

```text
POST /system/supervision/events/from-alert
GET  /system/supervision/events/page
GET  /system/supervision/events/get?id={id}
GET  /system/supervision/events/{id}/evidence
```

Permissions to use in annotations or route metadata:

```text
supervision:event:create
supervision:event:query
supervision:evidence:query
```

- [ ] **Step 5: Run focused backend tests**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -Dtest=SupervisionEventServiceImplTest test
```

Expected: test passes.

- [ ] **Step 6: Run non-regression compile**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -am -DskipTests compile
```

Expected: compile succeeds.

### Task 04: Add Disposal Task Model, Dispatch, Accept, And Submit

**Files:**

- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionTaskDO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionActionDO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionTaskMapper.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionActionMapper.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionTaskDispatchReqVO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionTaskSubmitReqVO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionTaskRespVO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionTaskService.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionTaskServiceImpl.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionTaskController.java`
- Test: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionTaskServiceImplTest.java`

- [ ] **Step 1: Write failing lifecycle test**

Test case:

```java
@Test
void dispatchAcceptSubmit_updatesTaskAndEventTimeline() {
    Long eventId = createOpenEvent("alert-task-001");

    Long taskId = supervisionTaskService.dispatch(eventId, dispatchReq("onsite_handle", 10L, "onsite_police"));
    supervisionTaskService.accept(taskId, 1001L);
    supervisionTaskService.submit(taskId, submitReq("handled", "现场已核实并完成处置"));

    SupervisionTaskDO task = supervisionTaskMapper.selectById(taskId);
    SupervisionEventDO event = supervisionEventMapper.selectById(eventId);

    assertEquals("submitted", task.getTaskStatus());
    assertEquals("pending_close_check", event.getEventStatus());
}
```

Expected before implementation: fails because task service does not exist.

- [ ] **Step 2: Implement task service**

Rules:

1. `dispatch` creates `system_supervision_task` and `system_supervision_action`.
2. `dispatch` moves event to `dispatched`.
3. `accept` moves task to `acknowledged` and event to `accepted`.
4. `submit` requires `result_category` and `handling_note`.
5. `submit` moves task to `submitted`.
6. `submit` moves event to `pending_close_check` for L1/L2 and `pending_recheck` for L3/L4.
7. Closed events reject new dispatches.

- [ ] **Step 3: Add task APIs**

Endpoints:

```text
POST /system/supervision/events/{eventId}/tasks
POST /system/supervision/tasks/{taskId}/accept
POST /system/supervision/tasks/{taskId}/submit
GET  /system/supervision/tasks/my
```

Permissions:

```text
supervision:task:dispatch
supervision:task:handle
supervision:task:query
```

- [ ] **Step 4: Run focused backend tests**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -Dtest=SupervisionTaskServiceImplTest test
```

Expected: dispatch, accept, and submit test passes.

- [ ] **Step 5: Run backend compile**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -am -DskipTests compile
```

Expected: compile succeeds.

### Task 05: Add Frontend API, Event Center, Event Detail, And My Tasks

**Files:**

- Create: `WEB/src/api/system/supervision/event.ts`
- Create: `WEB/src/views/supervision/event/index.vue`
- Create: `WEB/src/views/supervision/event/detail.vue`
- Create: `WEB/src/views/supervision/event/event.data.ts`
- Create: `WEB/src/views/supervision/task/my.vue`
- Create: `WEB/src/views/supervision/task/task.data.ts`
- Create: `WEB/src/router/routes/modules/supervision.ts`

- [ ] **Step 1: Add API client**

Use existing `defHttp` style:

```ts
import { defHttp } from '@/utils/http/axios'

export function createEventFromAlert(data) {
  return defHttp.post({ url: '/system/supervision/events/from-alert', data })
}

export function getSupervisionEventPage(params) {
  return defHttp.get({ url: '/system/supervision/events/page', params })
}

export function getSupervisionEvent(id: number | string) {
  return defHttp.get({ url: `/system/supervision/events/get?id=${id}` })
}

export function dispatchSupervisionTask(eventId: number | string, data) {
  return defHttp.post({ url: `/system/supervision/events/${eventId}/tasks`, data })
}

export function acceptSupervisionTask(taskId: number | string) {
  return defHttp.post({ url: `/system/supervision/tasks/${taskId}/accept` })
}

export function submitSupervisionTask(taskId: number | string, data) {
  return defHttp.post({ url: `/system/supervision/tasks/${taskId}/submit`, data })
}

export function getMySupervisionTasks(params) {
  return defHttp.get({ url: '/system/supervision/tasks/my', params })
}
```

- [ ] **Step 2: Add event center table**

Use `BasicTable` like existing system pages. Columns must include:

```ts
eventNo, eventType, eventLevel, eventStatus, sourceSystem,
sourceAlertType, currentOwnerDeptId, currentOwnerUserId, createTime
```

Actions:

```text
查看详情
派发任务
```

- [ ] **Step 3: Add event detail page**

Detail page must show:

1. Event summary.
2. Source alert information.
3. Image/video URI fields as links or buttons if present.
4. Task list.
5. Evidence list.
6. Status timeline.

- [ ] **Step 4: Add my tasks page**

Page supports:

1. List assigned tasks.
2. Accept task.
3. Submit handling result.

- [ ] **Step 5: Add isolated route**

Create route module under `WEB/src/router/routes/modules/supervision.ts` with a route path under `/supervision`. Do not attach it to existing alert routes. Menu exposure should rely on backend menu permissions or route auth.

- [ ] **Step 6: Verify frontend**

Run:

```powershell
cd WEB
pnpm type:check
pnpm build
```

Expected: typecheck and build succeed.

### Task 06: Add Permission-Gated Alert Page Entry

**Files:**

- Modify: `WEB/src/views/alert/index.vue`
- Modify: `WEB/src/views/alert/components/AlertCards/index.vue`
- Use: `WEB/src/api/system/supervision/event.ts`

- [ ] **Step 1: Add table action without removing existing actions**

Add a third `TableAction` item after image and video:

```ts
{
  icon: 'ant-design:profile-outlined',
  tooltip: { title: '生成监管事件', placement: 'top' },
  auth: 'supervision:event:create',
  onClick: handleCreateSupervisionEvent.bind(null, record),
}
```

Keep existing `handleViewImage` and `handleViewVideo` actions unchanged.

- [ ] **Step 2: Add handler**

Handler:

```ts
async function handleCreateSupervisionEvent(record: Record<string, any>) {
  const result = await createEventFromAlert({
    sourceSystem: 'video',
    sourceAlertId: String(record.id ?? record.alert_id ?? record.event_id),
    sourceAlertType: String(record.task_type ?? record.event ?? 'unknown'),
    sourceAlertTime: record.created_time ?? record.datetime ?? record.createTime,
    deviceId: record.device_id,
    cameraId: record.camera_id,
    locationId: record.location_id,
    mediaRefs: {
      image: record.image_url ?? record.image,
      video: record.video_url ?? record.video,
    },
    payload: record,
  })
  createMessage.success(result?.decision === 'reused' ? '已存在监管事件' : '已生成监管事件')
  if (result?.eventId || result?.event_id) {
    await router.push({ path: '/supervision/event', query: { id: result.eventId ?? result.event_id } })
  }
}
```

Adjust only field names proven by the alert record shape in `WEB/src/views/alert/Data.tsx` and current API response.

- [ ] **Step 3: Add card action behind permission**

In `AlertCards/index.vue`, add the same action as a button or icon action. Do not alter image/video callbacks. Emit a new event:

```ts
defineEmits(['getMethod', 'viewImage', 'viewVideo', 'createSupervisionEvent'])
```

Parent `WEB/src/views/alert/index.vue` handles `@createSupervisionEvent="handleCreateSupervisionEvent"`.

- [ ] **Step 4: Verify non-regression**

Run:

```powershell
rg -n "查看告警图片|查看告警录像|handleViewImage|handleViewVideo|createSupervisionEvent" WEB/src/views/alert
cd WEB
pnpm type:check
```

Expected: image/video actions and new supervision action all exist; typecheck succeeds.

---

## Milestone B: Tasks 05-09

This milestone adds recheck, close-check, evidence chain quality, permission boundaries, and acceptance runbook after Milestone A is stable.

### Task 07: Add Recheck And Rework

**Files:**

- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionEventService.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionEventServiceImpl.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionEventController.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionRecheckReqVO.java`
- Modify: `WEB/src/views/supervision/event/detail.vue`

- [ ] **Step 1: Add failing recheck test**

Test:

```java
@Test
void recheckReject_movesEventToReworkRequired() {
    Long eventId = createPendingRecheckEvent();

    supervisionEventService.recheck(eventId, recheckReq("leader", "rejected", "处置说明不足"));

    SupervisionEventDO event = supervisionEventMapper.selectById(eventId);
    assertEquals("rework_required", event.getEventStatus());
}
```

- [ ] **Step 2: Implement recheck service**

Rules:

1. `approved` moves event to `pending_close_check`.
2. `rejected` moves event to `rework_required`.
3. `transfer_major` moves event to `transferred_major`.
4. Each recheck creates evidence and action records.

- [ ] **Step 3: Add recheck UI controls**

In event detail, show actions only when status is `pending_recheck`:

```text
复核通过
驳回补充
转重大事件
```

- [ ] **Step 4: Verify**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -Dtest=SupervisionEventServiceImplTest test
cd ..\WEB
pnpm type:check
```

### Task 08: Add Close Check And Close

**Files:**

- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionCloseCheckService.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionCloseCheckServiceImpl.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionCloseCheckResultDO.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionCloseCheckResultMapper.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionCloseReqVO.java`
- Modify: `WEB/src/views/supervision/event/detail.vue`

- [ ] **Step 1: Add failing close-check test**

Test:

```java
@Test
void closeCheck_blocksL3WithoutLeaderReview() {
    Long eventId = createEventWithLevel("L3");

    CloseCheckResult result = closeCheckService.check(eventId);

    assertEquals("failed", result.getCheckResult());
    assertTrue(result.getHardBlockItems().contains("leader_review_required"));
}
```

- [ ] **Step 2: Implement close-check rules**

Hard blocks:

```text
missing_owner
missing_handling_note
missing_recheck
missing_leader_review
missing_required_evidence
permission_denied
```

Soft warnings:

```text
missing_optional_video
timeout
duplicate_alert
```

- [ ] **Step 3: Implement close endpoint**

Rules:

1. L1/L2 require `supervision:event:close-normal`.
2. L3/L4 require `supervision:event:close-major`.
3. Failed hard blocks prevent normal close.
4. Exception close requires `exceptionReason`.
5. Close writes action, evidence, close-check result, and event `closed_at`.

- [ ] **Step 4: Add close-check panel**

Event detail shows:

```text
关闭校验
硬拦截
软提醒
例外审批说明
确认关闭
```

- [ ] **Step 5: Verify**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -Dtest=SupervisionCloseCheckServiceImplTest test
cd ..\WEB
pnpm type:check
```

### Task 09: Add Evidence Chain And Timeline Read Model

**Files:**

- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionEventServiceImpl.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionTaskServiceImpl.java`
- Create: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/vo/SupervisionTimelineItemRespVO.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/SupervisionEventController.java`
- Modify: `WEB/src/views/supervision/event/detail.vue`

- [ ] **Step 1: Add timeline API**

Endpoint:

```text
GET /system/supervision/events/{eventId}/timeline
```

Response items:

```text
created
dispatched
accepted
submitted
rechecked
close_checked
closed
```

- [ ] **Step 2: Ensure every lifecycle action writes evidence or timeline item**

Actions that must write:

```text
create_from_alert
dispatch_task
accept_task
submit_task
recheck
close_check
close
```

- [ ] **Step 3: Add UI timeline**

Event detail shows timeline above evidence list. Keep image/video entries as links to existing media behavior.

- [ ] **Step 4: Verify**

Run:

```powershell
cd DEVICE
mvn -pl iot-system/iot-system-biz -Dtest=SupervisionEventServiceImplTest,SupervisionTaskServiceImplTest test
cd ..\WEB
pnpm type:check
```

### Task 10: Add Permission Boundaries And Menu Seeds

**Files:**

- Modify: `DEVICE/iot-system/iot-system-biz/src/main/resources/sql/supervision_event_closure_v1.sql`
- Modify: `DEVICE/iot-system/iot-system-api/src/main/java/com/basiclab/iot/system/enums/ErrorCodeConstants.java`
- Modify: supervision controllers under `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision/`
- Modify: `WEB/src/router/routes/modules/supervision.ts`
- Modify: `WEB/src/views/supervision/event/detail.vue`

- [ ] **Step 1: Add permission seed codes**

Seed or document these permission keys:

```text
supervision:event:create
supervision:event:query
supervision:event:recheck-medical
supervision:event:recheck-rehab
supervision:event:recheck-leader
supervision:event:close-normal
supervision:event:close-major
supervision:evidence:query
supervision:evidence:sensitive-query
supervision:task:dispatch
supervision:task:handle
supervision:task:query
supervision:audit:query
```

- [ ] **Step 2: Guard backend endpoints**

Use existing `@PreAuthorize` style where enabled. If this codebase still comments permissions in controllers, keep the same style but ensure the permission key is present next to each endpoint.

- [ ] **Step 3: Guard frontend actions**

Use existing `v-auth` or action `auth` metadata. Sensitive evidence must not render unless the user has `supervision:evidence:sensitive-query`.

- [ ] **Step 4: Verify permission non-regression**

Run:

```powershell
rg -n "supervision:" DEVICE/iot-system WEB/src
rg -n "system:notice|system:user|system:menu" WEB/src/views/system DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin
```

Expected: new supervision permissions exist; existing system permissions still exist.

### Task 11: Add P0 Acceptance Runbook And Smoke Scripts

**Files:**

- Create: `docs/prd/tasks/supervision-event-closure-v1/p0-acceptance-runbook.md`
- Modify: `docs/prd/tasks/supervision-event-closure-v1/README.md`

- [ ] **Step 1: Add acceptance runbook**

The runbook must include these scripts:

```text
普通告警闭环
生命安全事件闭环
戒毒异常闭环
驳回补充
误报归档
关闭拦截
权限隔离
现有告警图片和录像回放非回归
```

- [ ] **Step 2: Add expected evidence for each script**

Each script records:

```text
input
steps
expected event status
expected task status
expected evidence item
expected permission result
failure condition
```

- [ ] **Step 3: Link runbook from task package**

Update `docs/prd/tasks/supervision-event-closure-v1/README.md` with the runbook path.

- [ ] **Step 4: Final milestone verification**

Run:

```powershell
rg -n "普通告警闭环|生命安全事件闭环|关闭拦截|权限隔离|图片|录像" docs/prd/tasks/supervision-event-closure-v1/p0-acceptance-runbook.md
git diff --check
```

Expected: all acceptance scripts exist; diff check reports no whitespace errors except known LF/CRLF warnings.

---

## Final Verification

After Tasks 01-11:

```powershell
git status --short
cd DEVICE
mvn -pl iot-system/iot-system-api,iot-system/iot-system-biz -am test
cd ..\WEB
pnpm type:check
pnpm build
cd ..
rg -n "handleViewImage|handleViewVideo|DialogPlayer|ImageModal|queryAlarmList" WEB/src/views/alert
rg -n "system_supervision_event|system_supervision_task|supervision:event:create|supervision:task:dispatch" DEVICE WEB docs
git diff --check
```

Expected:

1. Backend tests pass or only unrelated pre-existing environment failures are recorded.
2. Frontend typecheck and build pass.
3. Existing alert image/video/list behaviors still have their original handlers.
4. New supervision code is isolated under `system_supervision_*`, `/system/supervision/*`, and `WEB/src/views/supervision`.
5. `git diff --check` has no new whitespace errors.

## Rollback Plan

If the feature causes issues before release:

1. Remove or hide the supervision menu permissions.
2. Remove the alert page supervision action by reverting only `WEB/src/views/alert/index.vue` and `WEB/src/views/alert/components/AlertCards/index.vue`.
3. Leave additive tables in place; they do not affect existing alert reads.
4. Disable calls to `/system/supervision/*` at the gateway or menu layer.
5. Existing alert list, image modal, video modal, and device routes continue to operate because their APIs and data models were not changed.
