# Command Center, AI Startup, and Alert API Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a single-focus post-login command center that keeps the admin entry and makes scoped alert polling plus AI startup work from an empty task database.

**Architecture:** The selected camera becomes the shared scope for video, dashboard statistics, alerts, and the dashboard-owned algorithm task. DEVICE remains the authority for tenant, RBAC, and camera scope; VIDEO keeps fail-closed collection queries while accepting the documented empty alert-class contract; WEB performs idempotent task bootstrap when no template task exists.

**Tech Stack:** Vue 3 + TypeScript + Ant Design Vue + Less, Spring Boot + JUnit 5, Flask/SQLAlchemy + pytest, pnpm 11, Maven.

---

## File map

- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/AuthController.java`: accept the `alert_read` media action.
- `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ConfiguredReviewCameraPermissionResolver.java`: provide fail-closed default action-to-RBAC mappings.
- `DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml`: bind `alert_read` explicitly so Spring configuration does not replace the Java default without it.
- `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/MediaPermissionCheckControllerTest.java`: prove alert reads require tenant, camera grant, and permission.
- `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ConfiguredReviewCameraPermissionResolverTest.java`: prove runtime configuration retains the alert-read mapping.
- `VIDEO/app/services/algorithm_task_service.py`: align task create/update validation with the documented empty alert-class behavior.
- `VIDEO/tests/test_algorithm_task_alert_class_contract.py`: protect empty and non-empty alert-class semantics.
- `WEB/src/api/device/calculate.ts`: use scoped, retry-free polling without the legacy `jwt_token` header side effect.
- `WEB/src/views/dashboard/monitor/useDashboardData.ts`: make the selected camera the alert/statistics scope and refresh on device changes.
- `WEB/src/views/dashboard/monitor/dashboardGuardTask.ts`: bootstrap a dashboard task from available models and serialize concurrent starts.
- `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`: supply the model loader and expose useful AI lifecycle state in the single-focus header.
- `WEB/src/views/dashboard/monitor/index.vue`: own the command-center grid and pass the selected camera to the data composable.
- `WEB/src/views/dashboard/monitor/components/Header.vue`: preserve the admin entry and present command-center health.
- `WEB/src/views/dashboard/monitor/components/Sidebar.vue`: keep the device tree and remove duplicate overview cards.
- `WEB/src/views/dashboard/monitor/components/AlarmPanel.vue`: render scoped empty/error/retry states.
- `WEB/tests/dashboardCommandCenterRepair.test.ts`: contract-test the complete frontend repair.

### Task 1: Authorize scoped alert reads in DEVICE

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/MediaPermissionCheckControllerTest.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/AuthController.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ConfiguredReviewCameraPermissionResolver.java`
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml`
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ConfiguredReviewCameraPermissionResolverTest.java`

- [ ] **Step 1: Write the failing authorization tests**

Add a test that configures user `42`, tenant `7`, camera `camera-01`, and the playback permission, then checks:

```java
MediaPermissionCheckRespVO granted = controller.checkMediaPermission(
        new MediaPermissionCheckReqVO("alert_read", "camera-01", "/video/alert/page", null)
).getData();
assertTrue(granted.getAllowed());
assertEquals("alert_read", granted.getAction());

MediaPermissionCheckRespVO missingCamera = controller.checkMediaPermission(
        new MediaPermissionCheckReqVO("alert_read", null, "/video/alert/statistics", null)
).getData();
assertFalse(missingCamera.getAllowed());
assertEquals("camera_scope_required", missingCamera.getReason());
```

Also add a resolver test proving its default `alert_read` mapping still calls `PermissionService.hasAnyPermissions` and never grants a camera absent from the explicit user scope.
Extend the Spring binding test to require `alert_read -> system:supervision-alert-review:media:playback` from `application.yaml`.

- [ ] **Step 2: Run the test and verify RED**

Run from `DEVICE`:

```powershell
mvn -pl iot-system/iot-system-biz -am -Dtest=MediaPermissionCheckControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because `alert_read` is not in `MEDIA_ACTIONS` and the resolver has no default mapping.

- [ ] **Step 3: Add the minimal secure implementation**

Add `"alert_read"` to `MEDIA_ACTIONS`. Initialize the resolver mapping with known RBAC permissions:

```java
private Map<String, List<String>> actionPermissions = defaultActionPermissions();

private static Map<String, List<String>> defaultActionPermissions() {
    Map<String, List<String>> defaults = new LinkedHashMap<>();
    defaults.put("playback", List.of("system:supervision-alert-review:media:playback"));
    defaults.put("snapshot", List.of("system:supervision-alert-review:media:snapshot"));
    defaults.put("coverage", List.of("system:supervision-alert-review:media:playback"));
    defaults.put("alert_read", List.of("system:supervision-alert-review:media:playback"));
    defaults.put("export", List.of("system:supervision-alert-review:media:export"));
    defaults.put("download", List.of("system:supervision-alert-review:media:download"));
    defaults.put("manifest_verify", List.of("system:supervision-alert-review:media:manifest"));
    defaults.put("record_manage", List.of("system:supervision-alert-review:media:manage"));
    return defaults;
}
```

Keep `setActionPermissions()` authoritative when external configuration is present. Do not weaken tenant, permission, explicit user-camera, or persisted-camera checks.
Add the same `alert_read` mapping to `application.yaml`; this is required because bound configuration replaces the in-code map.

- [ ] **Step 4: Run the targeted DEVICE tests and verify GREEN**

Run the Maven command from Step 2.

Expected: all `MediaPermissionCheckControllerTest` tests PASS.

- [ ] **Step 5: Commit the DEVICE slice**

```powershell
git add DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/AuthController.java DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ConfiguredReviewCameraPermissionResolver.java DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/MediaPermissionCheckControllerTest.java DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ConfiguredReviewCameraPermissionResolverTest.java
git commit -m "fix(auth): authorize scoped dashboard alert reads"
```

### Task 2: Align VIDEO alert-class semantics

**Files:**
- Create: `VIDEO/tests/test_algorithm_task_alert_class_contract.py`
- Modify: `VIDEO/app/services/algorithm_task_service.py`

- [ ] **Step 1: Write a failing source-and-behavior contract test**

The test must assert the documented behavior in `filter_detections_for_alert()` and ensure task creation/update no longer contain the contradictory rejection:

```python
from pathlib import Path
from app.utils.alert_class_filter import filter_detections_for_alert


def test_empty_alert_classes_allow_any_detection():
    detections = [{"class_name": "person"}, {"class_name": "safehat"}]
    assert filter_detections_for_alert(detections, []) == detections


def test_task_service_does_not_reject_empty_alert_classes():
    source = Path("app/services/algorithm_task_service.py").read_text(encoding="utf-8")
    assert "启用告警事件时必须指定至少一个告警触发标签" not in source
```

- [ ] **Step 2: Run the test and verify RED**

Run from `VIDEO`:

```powershell
python -m pytest tests/test_algorithm_task_alert_class_contract.py -q
```

Expected: one failure because the rejection text remains in the task service.

- [ ] **Step 3: Remove only the contradictory create/update checks**

Delete the two branches that raise when `alert_event_enabled` is true and `parse_alert_class_names(...)` is empty. Keep serialization, non-empty filtering, defense normalization, and all other validation unchanged.

- [ ] **Step 4: Run focused VIDEO tests and verify GREEN**

```powershell
python -m pytest tests/test_algorithm_task_alert_class_contract.py test_alert_tenant_scope.py test_alert_hook_direct_persist.py -q
```

Expected: all selected tests PASS.

- [ ] **Step 5: Commit the VIDEO slice**

```powershell
git add VIDEO/app/services/algorithm_task_service.py VIDEO/tests/test_algorithm_task_alert_class_contract.py
git commit -m "fix(video): allow default alert class coverage"
```

### Task 3: Scope dashboard polling and remove retry storms

**Files:**
- Create: `WEB/tests/dashboardCommandCenterRepair.test.ts`
- Modify: `WEB/src/api/device/calculate.ts`
- Modify: `WEB/src/views/dashboard/monitor/useDashboardData.ts`
- Modify: `WEB/src/views/dashboard/monitor/index.vue`

- [ ] **Step 1: Add failing WEB contracts for scoped polling**

Assert the following source contracts:

```ts
assert.match(dashboardData, /useDashboardData\(activeDeviceId/)
assert.match(dashboardData, /queryAlarmList\([\s\S]*device_id:[\s\S]*\{ polling: true \}/)
assert.match(dashboardData, /getDashboardStatistics\([\s\S]*device_id/)
assert.match(calculateApi, /retryRequest: \{ isOpenRetry: false, count: 0, waitTime: 0 \}/)
assert.doesNotMatch(calculateApi, /localStorage\.getItem\('jwt_token'\)/)
```

Also assert that `index.vue` passes a computed selected device ID into `useDashboardData()`.

- [ ] **Step 2: Run the WEB contract and verify RED**

Run from `WEB`:

```powershell
pnpm exec tsx tests/dashboardCommandCenterRepair.test.ts
```

Expected: FAIL because the composable is unscoped and the alert list does not use polling options.

- [ ] **Step 3: Implement scoped, retry-free refresh**

Change the API signatures to:

```ts
export const getDashboardStatistics = async (params: { device_id: string }) => { /* existing dedupe */ }
```

In the composable:

```ts
export function useDashboardData(activeDeviceId: ComputedRef<string>) {
  async function refreshDashboardData() {
    const deviceId = activeDeviceId.value.trim()
    if (!deviceId || refreshing.value) return
    const [statisticsResult, alarmResult] = await Promise.allSettled([
      getDashboardStatistics({ device_id: deviceId }),
      queryAlarmList({ pageNo: 1, pageSize: 7, device_id: deviceId }, { polling: true }),
    ])
    // retain the existing partial-success mapping
  }
  watch(activeDeviceId, () => refreshDashboardData(), { flush: "post" })
}
```

Remove the `defHttp.setHeader()` call that writes the legacy `jwt_token` header in `calculate.ts`; rely on the global Axios authorization interceptor.

- [ ] **Step 4: Run WEB scoped polling tests and existing operational tests**

```powershell
pnpm exec tsx tests/dashboardCommandCenterRepair.test.ts
pnpm exec tsx tests/dashboardOperationalReadiness.test.ts
```

Expected: both commands PASS.

- [ ] **Step 5: Commit the polling slice**

```powershell
git add WEB/src/api/device/calculate.ts WEB/src/views/dashboard/monitor/useDashboardData.ts WEB/src/views/dashboard/monitor/index.vue WEB/tests/dashboardCommandCenterRepair.test.ts
git commit -m "fix(dashboard): scope alert polling to active camera"
```

### Task 4: Bootstrap AI from an empty task table

**Files:**
- Modify: `WEB/tests/dashboardCommandCenterRepair.test.ts`
- Modify: `WEB/src/views/dashboard/monitor/dashboardGuardTask.ts`
- Modify: `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`

- [ ] **Step 1: Extend the WEB test for empty-task bootstrap and concurrency**

The source contract must require:

```ts
assert.match(guardTask, /listAvailableModels/)
assert.match(guardTask, /buildBootstrapTemplate/)
assert.match(guardTask, /alert_class_names:\s*\[\]/)
assert.match(guardTask, /startRequestsByScope/)
assert.match(videoMonitor, /getModelPage\(\{ pageNo: 1, pageSize: 1000 \}\)/)
```

Update the existing dashboard AI tests so they continue to require task list/create/start and the persisted explicit AI toggle.

- [ ] **Step 2: Run tests and verify RED**

```powershell
pnpm exec tsx tests/dashboardCommandCenterRepair.test.ts
pnpm exec tsx tests/dashboardAiDetectionStartup.test.ts
```

Expected: the new bootstrap assertions FAIL.

- [ ] **Step 3: Add the minimal bootstrap API and idempotency**

Extend `DashboardGuardTaskApi`:

```ts
listAvailableModels: () => Promise<unknown>
```

Add model normalization that accepts `response`, `response.data`, or `response.data.data`, keeps positive finite IDs, and throws `No available AI models were found for dashboard recognition.` when empty. Build an in-memory template with full defense and `alert_class_names: []`.

Serialize same-scope starts:

```ts
const startRequestsByScope = new Map<string, Promise<{ taskId: number; reusedExistingTask?: boolean }>>()
```

Return the existing Promise when a start for the same normalized scope is in progress, and delete the map entry in `finally`.

Wire `VideoMonitor.vue` to `getModelPage({ pageNo: 1, pageSize: 1000 })` with local error handling.

- [ ] **Step 4: Run all focused AI/video tests**

```powershell
pnpm exec tsx tests/dashboardCommandCenterRepair.test.ts
pnpm exec tsx tests/dashboardAiDetectionStartup.test.ts
pnpm exec tsx tests/dashboardGuardToggle.test.ts
pnpm exec tsx tests/dashboardRealtimeVideoStartup.test.ts
```

Expected: all four commands PASS.

- [ ] **Step 5: Commit the AI slice**

```powershell
git add WEB/src/views/dashboard/monitor/dashboardGuardTask.ts WEB/src/views/dashboard/monitor/components/VideoMonitor.vue WEB/tests/dashboardCommandCenterRepair.test.ts
git commit -m "fix(dashboard): bootstrap AI recognition without a template"
```

### Task 5: Redesign the post-login command center

**Files:**
- Modify: `WEB/tests/dashboardCommandCenterRepair.test.ts`
- Modify: `WEB/src/views/dashboard/monitor/index.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/Header.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/Sidebar.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/AlarmPanel.vue`

- [ ] **Step 1: Add failing structural contracts**

Require the approved structure:

```ts
assert.match(indexView, /data-testid="command-center-metrics"/)
assert.match(indexView, /command-center-grid/)
assert.match(header, /data-testid="monitor-admin-entry"/)
assert.match(header, />管理后台</)
assert.doesNotMatch(sidebar, /statistics-cards/)
assert.match(videoMonitor, /data-testid="monitor-ai-toggle"/)
assert.match(videoMonitor, /single-focus/)
```

- [ ] **Step 2: Run the contract and verify RED**

```powershell
pnpm exec tsx tests/dashboardCommandCenterRepair.test.ts
```

Expected: FAIL because the approved command-center structure is absent.

- [ ] **Step 3: Implement the approved single-focus layout**

Use this top-level grid in `index.vue`:

```vue
<section class="command-center-metrics" data-testid="command-center-metrics">
  <article v-for="metric in commandMetrics" :key="metric.key" class="command-metric">
    <span>{{ metric.label }}</span><strong>{{ metric.value }}</strong>
  </article>
</section>
<main class="command-center-grid">
  <MonitorSidebar class="command-center-devices" />
  <VideoMonitor class="command-center-video" />
  <AlarmPanel class="command-center-alerts" />
</main>
```

Use CSS Grid columns `minmax(240px, 300px) minmax(0, 1fr) minmax(280px, 340px)`, one muted gold accent, tabular metric numbers, and responsive collapse below 1100px. Remove the homepage split/preset toolbar from the rendered template while retaining its underlying code for other uses. Keep `monitor-admin-entry`, `monitor-ai-toggle`, video persistence keys, alert playback, and route overlay release unchanged.

- [ ] **Step 4: Run frontend regression tests**

```powershell
pnpm exec tsx tests/dashboardCommandCenterRepair.test.ts
pnpm exec tsx tests/dashboardAiDetectionStartup.test.ts
pnpm exec tsx tests/dashboardGuardToggle.test.ts
pnpm exec tsx tests/dashboardOperationalReadiness.test.ts
pnpm exec tsx tests/dashboardRealtimeVideoStartup.test.ts
pnpm exec tsx tests/monitorCommercialDashboardStyle.test.ts
```

Expected: all commands PASS.

- [ ] **Step 5: Commit the visual slice**

```powershell
git add WEB/src/views/dashboard/monitor/index.vue WEB/src/views/dashboard/monitor/components/Header.vue WEB/src/views/dashboard/monitor/components/Sidebar.vue WEB/src/views/dashboard/monitor/components/VideoMonitor.vue WEB/src/views/dashboard/monitor/components/AlarmPanel.vue WEB/tests/dashboardCommandCenterRepair.test.ts
git commit -m "feat(dashboard): redesign the single-focus command center"
```

### Task 6: Build and integrated verification

**Files:**
- Modify only files required by failures introduced by Tasks 1-5.

- [ ] **Step 1: Run WEB type and production checks**

```powershell
pnpm type:check
pnpm build
git diff --check
```

Expected: exit 0 for all commands.

- [ ] **Step 2: Run the complete focused regression set**

Run the WEB commands from Task 5, the DEVICE Maven command from Task 1, and the VIDEO pytest command from Task 2.

Expected: all selected tests PASS.

- [ ] **Step 3: Review the final diff for scope**

```powershell
git status --short
git diff --stat HEAD~4..HEAD
git diff --check HEAD~4..HEAD
```

Expected: only the planned WEB, DEVICE, VIDEO, test, and plan files appear.

- [ ] **Step 4: Commit any verification-only corrections**

If verification required a correction, stage only that correction and commit with a narrow `fix(...)` message. If no correction was needed, do not create an empty commit.

### Task 7: Deploy and verify the public site

**Files:**
- No new product files; create release artifacts outside tracked source as needed.

- [ ] **Step 1: Build immutable artifacts**

- Build `WEB/dist` from the verified commit.
- Build the `iot-system` runnable JAR from the verified commit.
- Package the changed VIDEO Python files with their relative paths.
- Record commit SHA and SHA-256 values without including credentials.

- [ ] **Step 2: Deploy with rollback points**

- Create timestamped release directories on `1.95.118.210`.
- Preserve the current WEB symlink target, iot-system JAR, and VIDEO files for rollback.
- Promote WEB atomically, replace the iot-system artifact, apply VIDEO files, and restart only affected services.

- [ ] **Step 3: Verify service health**

Check:

```text
nginx active
iot-system healthy
video-service healthy
public login HTTP 200
post-login dashboard assets contain command-center-metrics and monitor-admin-entry
```

- [ ] **Step 4: Verify functional behavior**

- Confirm scoped `/video/alert/statistics` and `/video/alert/page` return HTTP 200 in an authenticated session.
- Confirm VIDEO audit records `allowed` for `alert_read`, with tenant and camera present.
- Starting AI with an empty task table creates exactly one `[Dashboard Guard]` task and transitions it to running.
- Confirm the task has models, `alert_event_enabled=true`, and empty alert classes are accepted.
- Confirm the right-top admin entry navigates back to the existing backend route.
- Confirm Nginx logs show one statistics and one alert-list request per refresh interval, with no retry burst.

- [ ] **Step 5: Record deployment evidence**

Record release path, deployed commit, service status, public HTTP status, audit decision, task ID/status, and rollback targets. Do not print or store passwords, tokens, cookies, or private headers.
