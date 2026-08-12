# GitHub Sync and yFeiEye Visible Brand Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the latest `xinlingzhifei/easyaiot` `origin/main` into the current local-first branch, replace all user-visible legacy branding with `yFeiEye`, and prove the merged application still builds and passes focused regressions.

**Architecture:** Perform the divergent merge on an isolated worktree with a recovery branch pointing at the pre-merge commit. Resolve every conflict by comparing merge-base, local, and upstream versions, retaining local behavior while integrating upstream additions. Land the merge first, then use the existing branding contract as a RED/GREEN gate for a separate visible-brand commit before fast-forwarding the user's current branch.

**Tech Stack:** Git worktrees and three-way merge, PowerShell, Bash syntax checks, Python/pytest, Java/Maven, Vue 3/TypeScript/Vite/pnpm, Docker Compose validation.

---

## File map

The merge is expected to conflict in these responsibility groups:

- Build and runtime orchestration: `.scripts/docker/init-build-cache-dirs.sh`, `.scripts/docker/runtime_image.sh`, `.scripts/node/ensure_platform_agent.sh`, `.scripts/postgresql/iot-node10.sql`, `AI/docker-compose.yaml`, `AI/install_linux.sh`, `AI/run.py`, `DEVICE/docker-compose.yml`, `DEVICE/install_linux.sh`, `VIDEO/env.example`.
- Video data model: `VIDEO/models.py`.
- DEVICE MQTT and product scripts: the seven Java files under `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/` listed in Task 3.
- Public documentation: `README.md`, `README_fr.md`, `README_ko.md`, `README_ru.md`, `README_zh.md`, `README_zh_tw.md`.
- WEB API and compatibility infrastructure: `WEB/src/api/device/algorithm_task.ts`, `WEB/src/utils/http/axios/index.ts`, `WEB/src/utils/platformBrandingStorage.ts`, `WEB/src/views/camera/utils/devicePlay.ts`.
- WEB visible pages: the thirteen Vue/TSX files listed in Task 4.
- Visible-brand contract: `WEB/tests/visibleBrandingResidue.test.ts`.
- Brand-bearing tracked text and paths: exact files reported by the RED branding contract after the merge.

### Task 1: Create the recovery point and isolated merge worktree

**Files:**
- Verify: repository metadata and all existing untracked files
- Create branch: `codex/backup-login-page-restoration-pre-sync-20260717`
- Create worktree: `E:\yFeiEye\.worktrees\github-sync-yfeieye-20260717`
- Create branch: `codex/github-sync-yfeieye-20260717`

- [ ] **Step 1: Re-fetch and freeze the upstream commit**

Run from `E:\yFeiEye`:

```powershell
git fetch --prune origin main
git rev-parse HEAD
git rev-parse origin/main
git rev-list --left-right --count HEAD...origin/main
git merge-base HEAD origin/main
```

Expected: each command exits 0. Record the returned upstream hash; if it moved beyond `6a2dfd10d`, use the newer hash throughout the remaining steps and rerun the merge preflight.

- [ ] **Step 2: Prove the tracked tree is clean and the worktree root is ignored**

```powershell
git diff --quiet
git diff --cached --quiet
git check-ignore -q '.worktrees'
git ls-files -u
```

Expected: every command exits 0 and `git ls-files -u` prints nothing. Existing untracked deployment and smoke-test artifacts remain in place.

- [ ] **Step 3: Create the recovery branch**

```powershell
git branch 'codex/backup-login-page-restoration-pre-sync-20260717' HEAD
git show-ref --verify 'refs/heads/codex/backup-login-page-restoration-pre-sync-20260717'
```

Expected: the backup branch resolves to the same commit as the current branch.

- [ ] **Step 4: Create the isolated implementation worktree**

```powershell
git worktree add '.worktrees/github-sync-yfeieye-20260717' -b 'codex/github-sync-yfeieye-20260717' HEAD
git -C '.worktrees/github-sync-yfeieye-20260717' status --short --branch
```

Expected: the new worktree is on `codex/github-sync-yfeieye-20260717` with no tracked or untracked changes.

- [ ] **Step 5: Install isolated WEB dependencies and verify the pre-merge baseline**

Run from `E:\yFeiEye\.worktrees\github-sync-yfeieye-20260717\WEB`:

```powershell
$env:CI = 'true'
pnpm install --frozen-lockfile
pnpm exec tsx tests/visibleBrandingResidue.test.ts
pnpm exec tsx tests/loginPageRestoration.test.ts
pnpm exec tsx tests/streamUrlRewrite.test.ts
```

Then run from the isolated worktree root:

```powershell
python -m pytest 'VIDEO/tests/test_algorithm_task_migrations.py' -q
```

Expected: dependency installation exits 0 and all four focused baseline tests pass before `origin/main` is merged. A genuine baseline failure must be recorded and diagnosed before merge work starts.

### Task 2: Start the three-way merge and resolve runtime conflicts

**Files:**
- Modify: `.scripts/docker/init-build-cache-dirs.sh`
- Modify: `.scripts/docker/runtime_image.sh`
- Modify: `.scripts/node/ensure_platform_agent.sh`
- Modify: `.scripts/postgresql/iot-node10.sql`
- Modify: `AI/docker-compose.yaml`
- Modify: `AI/install_linux.sh`
- Modify: `AI/run.py`
- Modify: `DEVICE/docker-compose.yml`
- Modify: `DEVICE/install_linux.sh`
- Modify: `VIDEO/env.example`
- Modify: `VIDEO/models.py`

- [ ] **Step 1: Start a non-fast-forward merge without committing**

Run from the isolated worktree:

```powershell
git merge --no-ff --no-commit origin/main
git diff --name-only --diff-filter=U
```

Expected: merge exits non-zero because of the 41 preflight conflicts; the second command lists only real unmerged paths. Do not abort the merge.

- [ ] **Step 2: Resolve build-cache, runtime-image, agent, and database-script conflicts**

For each of the four `.scripts` paths, inspect all three stages before editing:

```powershell
git diff --cc -- '.scripts/docker/init-build-cache-dirs.sh' '.scripts/docker/runtime_image.sh' '.scripts/node/ensure_platform_agent.sh' '.scripts/postgresql/iot-node10.sql'
git show ':2:.scripts/docker/init-build-cache-dirs.sh'
git show ':3:.scripts/docker/init-build-cache-dirs.sh'
```

Use `apply_patch` to produce these combined outcomes:

- `init-build-cache-dirs.sh`: keep local serialized/cache-safe directory handling and integrate upstream cache additions without creating duplicate functions.
- `runtime_image.sh`: retain local reachable-mirror, cache-helper, and serialized-build fixes; add upstream architecture or deployment options once.
- `ensure_platform_agent.sh`: retain local platform-agent lifecycle and metrics fixes; add upstream agent parameters without duplicating process-start paths.
- `iot-node10.sql`: preserve local schema/data additions and append upstream IoT/MQTT schema changes without duplicate columns, keys, or seed rows.

Then run:

```powershell
git add -- '.scripts/docker/init-build-cache-dirs.sh' '.scripts/docker/runtime_image.sh' '.scripts/node/ensure_platform_agent.sh' '.scripts/postgresql/iot-node10.sql'
bash -n '.scripts/docker/init-build-cache-dirs.sh'
bash -n '.scripts/docker/runtime_image.sh'
bash -n '.scripts/node/ensure_platform_agent.sh'
```

Expected: all three syntax checks exit 0.

- [ ] **Step 3: Resolve AI, DEVICE compose/install, and VIDEO conflicts**

Inspect `:1:`, `:2:`, and `:3:` for each remaining Task 2 path, then use `apply_patch` with these exact rules:

- Keep local bind mounts, shared-data roots, credentials, health checks, build serialization, and local service registration behavior.
- Integrate upstream model-management, multi-GPU, resume-training, Kylin installer, MQTT, and mount additions when they do not replace the local safeguards.
- In `VIDEO/models.py`, preserve every local algorithm-task/motion-gate column and add every upstream model field; each SQLAlchemy column appears once.
- Keep local `yFeiEye` technical values where the upstream side reintroduces older values.

Stage only the resolved Task 2 paths:

```powershell
git add -- 'AI/docker-compose.yaml' 'AI/install_linux.sh' 'AI/run.py' 'DEVICE/docker-compose.yml' 'DEVICE/install_linux.sh' 'VIDEO/env.example' 'VIDEO/models.py'
python -m py_compile 'AI/run.py' 'VIDEO/models.py'
bash -n 'AI/install_linux.sh'
bash -n 'DEVICE/install_linux.sh'
```

Expected: Python and Bash syntax checks exit 0.

### Task 3: Resolve DEVICE MQTT and product-script conflicts

**Files:**
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsEngine.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsScriptManager.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsUtilFunction.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/messagebus/subscriber/IotDownstreamMessageSubscriber.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/messagebus/subscriber/IotUpstreamMessageSubscriber.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/emqx/IotEmqxDownstreamSubscriber.java`
- Modify: `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/util/IotMqttTopicUtils.java`

- [ ] **Step 1: Compare each Java conflict against the base**

```powershell
git diff --cc -- 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink'
git diff --name-only --diff-filter=U -- 'DEVICE'
```

Expected: the seven paths above are the remaining DEVICE conflicts.

- [ ] **Step 2: Reconcile Java behavior with one source of truth per responsibility**

Use `apply_patch` so the final Java code has these concrete contracts:

- Product scripts compile and execute through one engine/manager path; upstream helper functions are exposed once.
- Downstream messages retain local authentication, correlation, and compatibility behavior while accepting upstream property/service command forms.
- Upstream messages retain local event/alert handling while integrating upstream property-report and gateway/sub-device routing.
- EMQX routing uses the final shared topic parser; topic construction and extraction logic is not duplicated across subscribers.
- Null/invalid messages follow the existing local rejection behavior and cannot trigger a second publish.

Stage the seven files and compile the affected module:

```powershell
git add -- 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsEngine.java' 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsScriptManager.java' 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/javascript/JsUtilFunction.java' 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/messagebus/subscriber/IotDownstreamMessageSubscriber.java' 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/messagebus/subscriber/IotUpstreamMessageSubscriber.java' 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/emqx/IotEmqxDownstreamSubscriber.java' 'DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/util/IotMqttTopicUtils.java'
cmd /c mvn -pl iot-sink/iot-sink-biz -am -DskipTests compile
```

Run the Maven command from `E:\yFeiEye\.worktrees\github-sync-yfeieye-20260717\DEVICE`.

Expected: Maven reports `BUILD SUCCESS`.

### Task 4: Resolve README and WEB conflicts

**Files:**
- Modify: `README.md`, `README_fr.md`, `README_ko.md`, `README_ru.md`, `README_zh.md`, `README_zh_tw.md`
- Modify: `WEB/src/api/device/algorithm_task.ts`
- Modify: `WEB/src/utils/http/axios/index.ts`
- Modify: `WEB/src/utils/platformBrandingStorage.ts`
- Modify: `WEB/src/views/base/login/LoginForm.vue`
- Modify: `WEB/src/views/camera/utils/devicePlay.ts`
- Modify: `WEB/src/views/devices/components/DeviceLog/index.vue`
- Modify: `WEB/src/views/devices/components/DeviceModalForm/DeviceModal.vue`
- Modify: `WEB/src/views/devices/components/Event/index.vue`
- Modify: `WEB/src/views/devices/components/Model/components/CardList/TingModelCardList.vue`
- Modify: `WEB/src/views/devices/components/Service/index.vue`
- Modify: `WEB/src/views/model/SamInference/index.vue`
- Modify: `WEB/src/views/node/Data.tsx`
- Modify: `WEB/src/views/product/components/PhysicalModal.vue`
- Modify: `WEB/src/views/product/components/ProductModal.vue`
- Modify: `WEB/src/views/product/data/ProductData.tsx`
- Modify: `WEB/src/views/train/components/AiModelTool/index.vue`
- Modify: `WEB/src/views/train/components/ModelList/index.vue`

- [ ] **Step 1: Resolve multilingual README conflicts**

Keep all locally documented yFeiEye capabilities and add upstream IoT, contributor, deployment, and screenshot sections. Replace `EasyAIoT` only in conflict hunks with `yFeiEye`; leave lowercase repository URLs unchanged. Stage all six README files and run:

```powershell
git add -- 'README.md' 'README_fr.md' 'README_ko.md' 'README_ru.md' 'README_zh.md' 'README_zh_tw.md'
git diff --cached --check -- 'README.md' 'README_fr.md' 'README_ko.md' 'README_ru.md' 'README_zh.md' 'README_zh_tw.md'
```

Expected: no whitespace errors.

- [ ] **Step 2: Resolve WEB infrastructure conflicts**

Use `apply_patch` to retain these local contracts and add upstream changes once:

- `algorithm_task.ts`: keep the local `/video/algorithm` API prefix and existing task types; add upstream request/response fields.
- Axios: keep local public-base URL, token, and error behavior; add upstream timeout/session handling without a second interceptor.
- Branding storage: keep local `yFeiEye` defaults and current storage keys; add upstream fields without changing persisted-key compatibility.
- Login: keep local camera-themed restoration, fallback assets, submit flow, and test selectors; integrate upstream login behavior without restoring old brand text.
- Device playback: keep local public URL rewriting, codec metadata, GB28181 fallback, and stale-stream guards; integrate upstream direct-stream fixes.

Stage these five files.

- [ ] **Step 3: Resolve the twelve visible-page conflicts**

Use `apply_patch` to retain local page behavior and add upstream IoT/model fields. The final templates must not contain conflict markers, duplicated form fields, duplicate request calls, or a visible `EasyAIoT` string. Stage all twelve page files.

- [ ] **Step 4: Prove the merge has no unresolved paths or markers**

```powershell
git diff --name-only --diff-filter=U
git ls-files -u
rg -n "^(<<<<<<< .+|=======|>>>>>>> .+)$" . -g '!docs/superpowers/**'
git diff --cached --check
```

Expected: the first three commands produce no conflict output and `git diff --cached --check` exits 0.

- [ ] **Step 5: Run focused WEB contracts before the merge commit**

Run from the isolated `WEB` directory:

```powershell
pnpm exec tsx tests/loginSubmit.test.ts
pnpm exec tsx tests/loginPageRestoration.test.ts
pnpm exec tsx tests/streamUrlRewrite.test.ts
pnpm exec tsx tests/livePlayerCodecStrategy.test.ts
pnpm exec tsx tests/algorithmTaskStartFailure.test.ts
```

Expected: all focused contracts exit 0.

- [ ] **Step 6: Create the merge commit**

```powershell
git commit -m "Merge origin/main and preserve local yFeiEye behavior"
git merge-base --is-ancestor origin/main HEAD
git show --stat --oneline -1
```

Expected: the commit succeeds and the ancestry command exits 0.

### Task 5: Use the branding contract as a RED/GREEN gate

**Files:**
- Modify: `WEB/tests/visibleBrandingResidue.test.ts`
- Modify: exact tracked text files reported by the test
- Rename: tracked display assets whose path contains `EasyAIoT`

- [ ] **Step 1: Run the existing contract and verify RED for merged upstream text**

Run from the isolated `WEB` directory:

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
```

Expected: FAIL with `Old visible brand remains` and real tracked paths, not a missing dependency or syntax error.

- [ ] **Step 2: Align the test protection list with local contracts**

Use `apply_patch` on `WEB/tests/visibleBrandingResidue.test.ts` so it:

- continues excluding `docs/superpowers/**` and itself;
- allows only the known non-visible credential literal `EasyAIoT2025`;
- requires `APP/src/pages/index/index.ts` to keep internal key `easyaiot` and visible name `yFeiEye`;
- requires the locally established media secret and AI/VIDEO user-agent values to remain `yFeiEye` values;
- reports every other title-case legacy brand occurrence and old-brand path.

Run the test again. Expected: it remains RED and now reports only actionable visible-brand residue.

- [ ] **Step 3: Apply the mechanical title-case replacement**

Use a UTF-8/BOM-preserving bulk mechanical rewrite over tracked text files reported by `git grep`, excluding `docs/superpowers/**`, the branding test itself, and the exact credential literal `EasyAIoT2025`. Replace:

```text
Easy AI Internet of Things -> yFeiEye
EasyAIoT -> yFeiEye
```

Do not replace lowercase `easyaiot` globally. Review every modified executable/config file with `git diff` before staging.

- [ ] **Step 4: Audit lowercase page-visible residue**

```powershell
rg -n -i "easyaiot" APP/src WEB/src -g '*.vue' -g '*.ts' -g '*.tsx' -g '*.json' -g '*.html'
```

Expected: review every result. Retain only internal keys, URLs, paths, env names, service names, storage keys, and compatibility values. Use `apply_patch` to change any lowercase string rendered to a user.

- [ ] **Step 5: Rename old-brand display paths without deleting assets**

```powershell
git ls-files | rg 'EasyAIoT|Easy AI Internet of Things'
```

For each result, use `git mv` to the same path with `yFeiEye`. If both names already exist, compare hashes and preserve both contents under distinct `yFeiEye` names; do not permanently delete either file.

- [ ] **Step 6: Run GREEN branding checks**

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
git diff --check
```

Expected: both commands exit 0.

- [ ] **Step 7: Commit the visible-brand cleanup**

```powershell
git add -u
git add -- 'WEB/tests/visibleBrandingResidue.test.ts'
git diff --cached --check
git commit -m "chore(brand): refresh merged visible copy to yFeiEye"
```

Expected: commit succeeds and contains no untracked smoke/deployment artifacts.

### Task 6: Audit page-referenced raster branding

**Files:**
- Inspect: raster assets referenced by `WEB/src`, `APP/src`, and top-level `README*`
- Modify only if required: an asset with visible `EasyAIoT` text

- [ ] **Step 1: Enumerate referenced brand/logo images**

```powershell
rg -n -i "logo|brand|banner|login" WEB/src APP/src README*.md -g '*.vue' -g '*.ts' -g '*.tsx' -g '*.md'
```

Expected: produce the exact referenced image paths; ignore unreferenced artifact and deployment-package images.

- [ ] **Step 2: Visually inspect each referenced candidate**

Open candidates with the local image viewer. If an image visibly contains the old brand, use the `imagegen` skill with this exact edit instruction:

```text
Replace only the visible EasyAIoT brand text with yFeiEye. Preserve the original dimensions, layout, colors, imagery, legibility, and all non-brand text. Do not add new logos or decorative elements.
```

Reopen every edited asset and confirm the old text is gone. Assets without old text remain byte-for-byte unchanged.

- [ ] **Step 3: Commit only actual image edits**

If no image changed, skip this commit. Otherwise:

```powershell
$editedImages = @(git diff --name-only -- '*.png' '*.jpg' '*.jpeg' '*.webp' '*.gif' '*.svg')
if ($editedImages.Count -gt 0) {
  git add -- $editedImages
  git commit -m "chore(brand): refresh visible image branding"
}
```

Expected: only visually verified page-referenced images are committed.

### Task 7: Run full verification and fast-forward the user's branch

**Files:**
- Verify: entire merged tree
- Update branch: `codex/login-page-restoration`

- [ ] **Step 1: Run Git and syntax verification**

Run from the isolated worktree:

```powershell
git merge-base --is-ancestor origin/main HEAD
git ls-files -u
git diff --check
python -m py_compile 'AI/run.py' 'VIDEO/models.py'
bash -n '.scripts/docker/init-build-cache-dirs.sh'
bash -n '.scripts/docker/runtime_image.sh'
bash -n '.scripts/node/ensure_platform_agent.sh'
bash -n 'AI/install_linux.sh'
bash -n 'DEVICE/install_linux.sh'
docker compose -f '.scripts/docker/docker-compose.yml' config --quiet
```

Expected: all commands exit 0 and no unmerged files are printed.

- [ ] **Step 2: Run VIDEO regressions**

```powershell
python -m pytest 'VIDEO/tests/test_algorithm_task_migrations.py' -q
python -m pytest 'VIDEO/tests/test_algo_model_detect.py' -q
```

Expected: both targeted test files pass.

- [ ] **Step 3: Run the WEB regression suite and production build**

Run from the isolated `WEB` directory:

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
pnpm exec tsx tests/loginSubmit.test.ts
pnpm exec tsx tests/loginPageRestoration.test.ts
pnpm exec tsx tests/dashboardOperationalReadiness.test.ts
pnpm exec tsx tests/dashboardRealtimeVideoStartup.test.ts
pnpm exec tsx tests/monitorCommercialDashboardStyle.test.ts
pnpm exec tsx tests/streamUrlRewrite.test.ts
pnpm exec tsx tests/livePlayerCodecStrategy.test.ts
pnpm exec tsx tests/gb28181PlayTimeout.test.ts
pnpm exec tsx tests/algorithmTaskStartFailure.test.ts
node --test 'src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs'
pnpm run build
```

Expected: every contract passes and Vite reports a successful production build.

- [ ] **Step 4: Compile affected DEVICE modules**

Run from the isolated `DEVICE` directory:

```powershell
cmd /c mvn -pl iot-sink/iot-sink-biz,iot-system/iot-system-biz -am -DskipTests compile
```

Expected: Maven reports `BUILD SUCCESS` for all required modules.

- [ ] **Step 5: Prove the original worktree remained untouched**

Run from `E:\yFeiEye` before updating its branch:

```powershell
git status --porcelain=v2 --untracked-files=no
git rev-parse 'codex/backup-login-page-restoration-pre-sync-20260717'
git rev-parse HEAD
```

Expected: no tracked working-tree changes; the backup branch and current branch still point to the pre-merge plan commit.

- [ ] **Step 6: Fast-forward the user's current branch**

```powershell
git merge --ff-only 'codex/github-sync-yfeieye-20260717'
git merge-base --is-ancestor origin/main HEAD
git status --short --branch
```

Expected: fast-forward succeeds, upstream ancestry exits 0, and the previously existing untracked artifacts remain untracked.

- [ ] **Step 7: Record final evidence without destructive cleanup**

```powershell
git log --oneline --decorate -5
git diff 'codex/backup-login-page-restoration-pre-sync-20260717'..HEAD --stat
git worktree list
```

Expected: history shows the merge and brand commits. Keep the backup branch and isolated worktree until the final report; do not permanently delete any files or directories.
