# Dashboard Alert Read Permission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the production command center authenticated when it reads alerts for an explicitly granted camera, without weakening any sensitive media action.

**Architecture:** Preserve the existing action-permission gate and exact configured user-camera intersection. Make only `alert_read` independent of alert-review history; all other actions keep the persisted tenant-camera evidence gate.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Maven, PostgreSQL-backed MyBatis mapper, Vue production browser smoke.

---

### Task 1: Lock the regression with a failing resolver test

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ConfiguredReviewCameraPermissionResolverTest.java`

- [ ] **Step 1: Add a mapper helper that throws if review history is queried**

Create a proxy whose `selectExistingCameraIds` invocation throws `AssertionError("alert_read must not query review history")`.

- [ ] **Step 2: Add `alertReadUsesExplicitCameraGrantWithoutQueryingReviewHistory`**

Configure user `7` for `camera-01`, permit the default `alert_read` action, attach the rejecting mapper, resolve `camera-01`, and assert the exact result `List.of("camera-01")`.

- [ ] **Step 3: Run the single test and verify RED**

Run from `DEVICE/iot-system`:

```powershell
mvn -pl iot-system-biz -am -DskipTests=false -Dtest=ConfiguredReviewCameraPermissionResolverTest#alertReadUsesExplicitCameraGrantWithoutQueryingReviewHistory -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: FAIL because the current resolver calls `selectExistingCameraIds` for `alert_read`.

### Task 2: Implement the narrow authorization rule

**Files:**
- Modify: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ConfiguredReviewCameraPermissionResolver.java`

- [ ] **Step 1: Move the mapper-availability gate below configured-scope intersection**

The resolver must first normalize the explicit user grant and requested camera IDs.

- [ ] **Step 2: Return the exact candidate intersection for `alert_read` only**

After rejecting an empty intersection, add:

```java
if ("alert_read".equals(normalizeActionType(request.actionType()))) {
    return candidateCameraIds;
}
```

Then retain the existing `reviewItemMapper == null` fail-closed check and persisted tenant intersection for every other action.

- [ ] **Step 3: Re-run the single test and verify GREEN**

Run the Task 1 Maven command. Expected: PASS.

### Task 3: Prove no permission regression and build the artifact

**Files:**
- Verify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ConfiguredReviewCameraPermissionResolverTest.java`
- Verify: `DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/MediaPermissionCheckControllerTest.java`

- [ ] **Step 1: Run both focused suites**

```powershell
mvn -pl iot-system-biz -am -DskipTests=false -Dtest=ConfiguredReviewCameraPermissionResolverTest,MediaPermissionCheckControllerTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all focused tests PASS, including existing fail-closed and camera-scope cases.

- [ ] **Step 2: Package DEVICE**

```powershell
mvn -pl iot-system-biz -am -DskipTests package
```

Expected: BUILD SUCCESS and an updated `iot-system-biz` JAR.

- [ ] **Step 3: Review and commit only the resolver, test, spec, and plan**

```powershell
git diff --check
git status --short
git add docs/superpowers/specs/2026-07-17-dashboard-alert-read-permission-design.md docs/superpowers/plans/2026-07-17-dashboard-alert-read-permission.md DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ConfiguredReviewCameraPermissionResolver.java DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ConfiguredReviewCameraPermissionResolverTest.java
git commit -m "fix(auth): keep dashboard alert reads scoped"
```

### Task 4: Deploy and verify production behavior

**Files:**
- Deploy: packaged `iot-system-biz` JAR

- [ ] **Step 1: Create a timestamped rollback copy and deploy the new JAR**

Use the established PuTTY deployment workflow, verify absolute remote paths before moving artifacts, restart only `iot-system`, and wait for its health endpoint.

- [ ] **Step 2: Verify the DEVICE authorization endpoint through the real browser flow**

Log in normally, solve the slider challenge through the visible UI, select `[GB28181] test`, and confirm the dashboard remains open while `/video/alert/page` and `/video/alert/statistics` return HTTP 200.

- [ ] **Step 3: Verify video and AI**

Confirm the play request is no longer cancelled by authentication redirect. Enable AI, confirm the model list succeeds, and verify exactly one `[Dashboard Guard]` task exists with non-empty `model_ids`, `alert_event_enabled=true`, and a running status without an exception.

- [ ] **Step 4: Verify navigation and rollback evidence**

Open the retained `管理后台` entry, return to the dashboard, and record the deployed commit, service health, production request results, and rollback artifact.
