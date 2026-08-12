# GitHub Sync and Visible Brand Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge `origin/main@8c2af07f` into the current yFeiEye branch without losing local work, then remove newly introduced user-visible EasyAIoT branding while preserving runtime compatibility identifiers.

**Architecture:** Protect only the 12 current source/test worktree paths in a named stash and leave bulky local artifacts untouched. Perform a normal three-way merge, resolve the 28 predicted conflicts by combining local fixes with upstream functionality, then add a repository-level visible-brand contract test before the merge commit. Restore the named stash only after the merge commit is verified so pre-existing local work remains uncommitted and auditable.

**Tech Stack:** Git, PowerShell, Node.js built-in assertions, `tsx`, Vue 3/TypeScript, Python, Bash, Maven, Docker Compose, Vite/pnpm.

---

### Task 1: Capture the baseline and protect the dirty source tree

**Files:**
- Protect tracked: `VIDEO/models.py`
- Protect tracked: `VIDEO/run.py`
- Protect tracked: `WEB/src/views/dashboard/monitor/components/AlarmPanel.vue`
- Protect tracked: `WEB/src/views/dashboard/monitor/components/Header.vue`
- Protect tracked: `WEB/src/views/dashboard/monitor/components/Sidebar.vue`
- Protect tracked: `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`
- Protect tracked: `WEB/src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs`
- Protect tracked: `WEB/src/views/dashboard/monitor/index.vue`
- Protect untracked: `VIDEO/tests/test_algorithm_task_migrations.py`
- Protect untracked: `WEB/src/views/dashboard/monitor/useDashboardData.ts`
- Protect untracked: `WEB/tests/dashboardOperationalReadiness.test.ts`
- Protect untracked: `WEB/tests/monitorCommercialDashboardStyle.test.ts`
- Leave untouched: `.codex-smoke/`
- Leave untouched: `codex-smoke-screenshots-2026-06-12/`
- Leave untouched: `deploy-packages/`

- [ ] **Step 1: Refresh and record the exact merge inputs**

Run from `E:\yFeiEye`:

```powershell
git fetch --prune origin main
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git rev-list --left-right --count HEAD...origin/main
git status --short --branch
```

Expected: `origin/main` resolves to `8c2af07f60a31a55bd367cd45dca83029911c9e1`; the worktree still contains the 12 protected source/test paths and the artifact directories.

- [ ] **Step 2: Create a named, path-limited stash**

```powershell
git stash push -u -m "codex/pre-sync-20260710" -- `
  VIDEO/models.py `
  VIDEO/run.py `
  WEB/src/views/dashboard/monitor/components/AlarmPanel.vue `
  WEB/src/views/dashboard/monitor/components/Header.vue `
  WEB/src/views/dashboard/monitor/components/Sidebar.vue `
  WEB/src/views/dashboard/monitor/components/VideoMonitor.vue `
  WEB/src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs `
  WEB/src/views/dashboard/monitor/index.vue `
  VIDEO/tests/test_algorithm_task_migrations.py `
  WEB/src/views/dashboard/monitor/useDashboardData.ts `
  WEB/tests/dashboardOperationalReadiness.test.ts `
  WEB/tests/monitorCommercialDashboardStyle.test.ts
```

Expected: Git reports a saved worktree state named `codex/pre-sync-20260710`.

- [ ] **Step 3: Prove the stash contains the intended work and artifacts remain**

```powershell
git stash list --format='%gd %s' | Select-String 'codex/pre-sync-20260710'
git stash show --stat 'stash@{0}'
git status --short
Test-Path '.codex-smoke'
Test-Path 'codex-smoke-screenshots-2026-06-12'
Test-Path 'deploy-packages'
```

Expected: the named stash is present; none of the 12 protected paths remains dirty; all three artifact roots still exist and remain untracked.

### Task 2: Start the three-way merge and resolve non-frontend conflicts

**Files:**
- Modify: `.doc/部署文档/平台部署文档.md`
- Modify: `.doc/部署文档/平台部署文档_fr.md`
- Modify: `.doc/部署文档/平台部署文档_ko.md`
- Modify: `.doc/部署文档/平台部署文档_ru.md`
- Modify: `.doc/部署文档/平台部署文档_zh.md`
- Modify: `.doc/部署文档/平台部署文档_zh_tw.md`
- Modify: `.doc/部署文档/部署最佳实践.md`
- Modify: `.doc/部署文档/部署最佳实践_en.md`
- Modify: `.doc/部署文档/部署最佳实践_fr.md`
- Modify: `.doc/部署文档/部署最佳实践_ko.md`
- Modify: `.doc/部署文档/部署最佳实践_ru.md`
- Modify: `.doc/部署文档/部署最佳实践_zh_tw.md`
- Modify: `.scripts/docker/cache_python_resources_arm.sh`
- Modify: `.scripts/docker/install_middleware_linux.sh`
- Delete or retain after reference audit: `DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/framework/rpc/config/RpcConfiguration.java`
- Modify: `VIDEO/.env.docker`
- Modify: `VIDEO/app/utils/algo_model_detect.py`
- Modify: `VIDEO/services/realtime_algorithm_service/run_deploy.py`

- [ ] **Step 1: Begin a non-fast-forward merge without committing**

```powershell
git merge --no-ff --no-commit origin/main
```

Expected: exit code 1 with the 28 predicted conflicts; `MERGE_HEAD` exists and no merge commit has been created.

- [ ] **Step 2: Confirm the actual conflict inventory before editing**

```powershell
git diff --name-only --diff-filter=U
git ls-files -u
```

Expected: every unmerged path is represented in the design conflict list. If an additional path appears, inspect `git diff --cc -- <path>` before changing it and add it to the working checklist.

- [ ] **Step 3: Resolve the 12 multilingual document conflicts**

For each deployment document, inspect its combined diff:

```powershell
git diff --cc -- '.doc/部署文档/平台部署文档.md'
git diff --cc -- '.doc/部署文档/部署最佳实践.md'
```

Apply the same semantic resolution to every language variant:

```text
Keep upstream's new installation commands, deployment-profile details, and troubleshooting.
Keep local yFeiEye deployment paths and compatibility guidance where upstream did not supersede them.
Remove conflict markers.
Leave visible product naming for Task 4's tested EasyAIoT -> yFeiEye replacement.
Do not rename EASYAIOT_* variables, /opt/easyaiot paths, easyaiot-network, registry paths, or service names.
```

Stage only the 12 resolved documents after comparing the combined diff:

```powershell
git add -- '.doc/部署文档/平台部署文档.md' `
  '.doc/部署文档/平台部署文档_fr.md' `
  '.doc/部署文档/平台部署文档_ko.md' `
  '.doc/部署文档/平台部署文档_ru.md' `
  '.doc/部署文档/平台部署文档_zh.md' `
  '.doc/部署文档/平台部署文档_zh_tw.md' `
  '.doc/部署文档/部署最佳实践.md' `
  '.doc/部署文档/部署最佳实践_en.md' `
  '.doc/部署文档/部署最佳实践_fr.md' `
  '.doc/部署文档/部署最佳实践_ko.md' `
  '.doc/部署文档/部署最佳实践_ru.md' `
  '.doc/部署文档/部署最佳实践_zh_tw.md'
```

- [ ] **Step 4: Resolve script and VIDEO conflicts by preserving both behaviors**

Use `git diff --cc -- <path>` and apply these exact rules:

```text
.scripts/docker/cache_python_resources_arm.sh
  Keep local cache-root compatibility and upstream's current ARM resource list/cache flow.

.scripts/docker/install_middleware_linux.sh
  Keep local persistent-data path safeguards and upstream's current mirror/bootstrap changes.

VIDEO/.env.docker
  Keep local deployment-compatible values and add upstream's new algorithm/notification settings without duplicating keys.

VIDEO/app/utils/algo_model_detect.py
  Keep local model-path and availability fallbacks and upstream confidence/model inference controls.

VIDEO/services/realtime_algorithm_service/run_deploy.py
  Keep local realtime stream lifecycle fixes and upstream inference/drawing pipeline integration.
```

Then run syntax checks before staging:

```powershell
bash -n '.scripts/docker/cache_python_resources_arm.sh'
bash -n '.scripts/docker/install_middleware_linux.sh'
python -m py_compile 'VIDEO/app/utils/algo_model_detect.py' 'VIDEO/services/realtime_algorithm_service/run_deploy.py'
```

Expected: all commands exit 0.

Stage the resolved files:

```powershell
git add -- '.scripts/docker/cache_python_resources_arm.sh' `
  '.scripts/docker/install_middleware_linux.sh' `
  'VIDEO/.env.docker' `
  'VIDEO/app/utils/algo_model_detect.py' `
  'VIDEO/services/realtime_algorithm_service/run_deploy.py'
```

- [ ] **Step 5: Resolve the Java modify/delete conflict from current references**

```powershell
rg -n "RpcConfiguration" DEVICE -g '*.java' -g '*.xml' -g '*.yml' -g '*.yaml'
git log --oneline --all -- 'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/framework/rpc/config/RpcConfiguration.java'
```

Resolution rule:

```text
If no production reference remains and upstream moved its beans/configuration elsewhere, accept the upstream deletion with git rm.
If a live import, bean, or package scan still requires the class, keep the local file and update it only enough to compile against upstream APIs.
```

Verify the selected result from `E:\yFeiEye\DEVICE`:

```powershell
mvn -pl iot-system/iot-system-biz -am -DskipTests compile
```

Expected: exit 0. Then stage the deletion or retained file with:

```powershell
git add -A -- 'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/framework/rpc/config/RpcConfiguration.java'
```

### Task 3: Resolve frontend conflicts while retaining local playback and dashboard fixes

**Files:**
- Modify: `WEB/src/api/device/calculate.ts`
- Modify: `WEB/src/components/Application/src/AppLogo.vue`
- Modify: `WEB/src/components/Player/module/jessibuca.vue`
- Modify: `WEB/src/components/VideoPlayer/DialogPlayer.vue`
- Modify: `WEB/src/views/camera/components/SplitScreenMonitor/MonitorPanel.vue`
- Modify: `WEB/src/views/camera/utils/devicePlay.ts`
- Modify: `WEB/src/views/camera/utils/monitorDeviceTree.ts`
- Modify: `WEB/src/views/dashboard/monitor/components/Header.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/Sidebar.vue`
- Modify: `WEB/src/views/dashboard/monitor/components/VideoMonitor.vue`

- [ ] **Step 1: Resolve API and application-branding conflicts**

Apply these combined outcomes:

```text
WEB/src/api/device/calculate.ts
  Keep all existing dashboard statistics APIs and add upstream endpoint/type changes; do not remove exports used by dashboard tests.

WEB/src/components/Application/src/AppLogo.vue
  Keep upstream usePlatformBranding(), login/admin logo selection, and configurable title.
  Keep local route/navigation behavior and existing yFeiEye fallback title.
```

Run:

```powershell
rg -n "getDashboardStatistics|usePlatformBranding|displayTitle|displayLogo" `
  'WEB/src/api/device/calculate.ts' `
  'WEB/src/components/Application/src/AppLogo.vue'
```

Expected: all four contracts are present. Remove all conflict markers and stage both files.

- [ ] **Step 2: Resolve shared-player and monitor conflicts**

Apply these combined outcomes:

```text
jessibuca.vue and DialogPlayer.vue
  Preserve local H264/H265 engine selection, FLV/WebRTC fallback, tokenized URLs, and stale-stream protection.
  Add upstream audio-talk, reconnect, ticket, and player-performance changes without restoring old infinite retry behavior.

SplitScreenMonitor/MonitorPanel.vue, devicePlay.ts, monitorDeviceTree.ts
  Preserve local public URL rewriting, GB28181 fallback, codec metadata, and tree deduplication.
  Add upstream mini-mode, NVR/channel, audio-talk, stream-ticket, and playback-source changes.
```

Run focused contract tests from `E:\yFeiEye\WEB`:

```powershell
pnpm exec tsx tests/livePlayerCodecStrategy.test.ts
pnpm exec tsx tests/gb28181MonitorSync.test.ts
pnpm exec tsx tests/gb28181PlayTimeout.test.ts
pnpm exec tsx tests/streamUrlRewrite.test.ts
```

Expected: every command exits 0 before the six player/monitor files are staged.

- [ ] **Step 3: Resolve committed dashboard conflicts**

Before restoring the stash, resolve only the committed branch changes:

```text
Header.vue
  Keep local admin-entry navigation/test selectors and upstream platform-branding/dashboard-title support.

Sidebar.vue
  Keep local statistics/guard/device behavior and upstream current device/tree presentation changes.

VideoMonitor.vue
  Keep local route-return video state, AI fallback status, H265/FLV playback behavior, and upstream current player/audio changes.
```

Run the tracked dashboard contract:

```powershell
node --test 'src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs'
```

Expected: 5 tests pass. Stage the three resolved dashboard component files.

- [ ] **Step 4: Confirm all merge conflicts are resolved**

```powershell
git ls-files -u
rg -n "^(<<<<<<< .+|=======|>>>>>>> .+)$" . `
  -g '!deploy-packages/**' `
  -g '!.codex-smoke/**' `
  -g '!codex-smoke-screenshots-2026-06-12/**'
```

Expected: both commands produce no conflict entries.

### Task 4: Add a failing visible-brand contract before changing branding

**Files:**
- Create: `WEB/tests/visibleBrandingResidue.test.ts`
- Test: `WEB/tests/visibleBrandingResidue.test.ts`

- [ ] **Step 1: Create the repository-level contract test**

Add this complete file:

```typescript
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const listed = spawnSync('git', ['ls-files', '-z'], {
  cwd: repoRoot,
  encoding: 'utf8',
})

assert.equal(listed.status, 0, listed.stderr || 'git ls-files failed')

const protectedLiterals = [
  'EasyAIoT2025',
  'EasyAIoT_Media_Secret',
  'EasyAIoT-AI/1.0',
  'EasyAIoT-VIDEO/1.0',
]
const oldBrand = /EasyAIoT|Easy AI Internet of Things/
const violations: string[] = []

for (const relativePath of listed.stdout.split('\0').filter(Boolean)) {
  if (relativePath.startsWith('docs/superpowers/')) continue

  if (oldBrand.test(relativePath)) {
    violations.push(`${relativePath}: path contains the old visible brand`)
  }

  const bytes = readFileSync(resolve(repoRoot, relativePath))
  if (bytes.includes(0)) continue

  let source = bytes.toString('utf8')
  for (const literal of protectedLiterals) {
    source = source.replaceAll(literal, '')
  }

  source.split(/\r?\n/).forEach((line, index) => {
    if (oldBrand.test(line)) {
      violations.push(`${relativePath}:${index + 1}: ${line.trim()}`)
    }
  })
}

const appMenu = readFileSync(resolve(repoRoot, 'APP/src/pages/index/index.ts'), 'utf8')
assert.match(appMenu, /key:\s*'easyaiot'/, 'The compatibility menu key must remain easyaiot.')
assert.match(appMenu, /name:\s*'yFeiEye'/, 'The visible APP menu name must be yFeiEye.')

assert.match(
  readFileSync(resolve(repoRoot, 'WEB/src/views/node/utils/constants.ts'), 'utf8'),
  /EasyAIoT_Media_Secret/,
  'The deployed ZLM compatibility secret must not be renamed.',
)
assert.match(
  readFileSync(resolve(repoRoot, 'AI/app/utils/sam_model_download.py'), 'utf8'),
  /EasyAIoT-AI\/1\.0/,
  'The existing AI download User-Agent must remain stable.',
)
assert.match(
  readFileSync(resolve(repoRoot, 'VIDEO/app/utils/face_model_download.py'), 'utf8'),
  /EasyAIoT-VIDEO\/1\.0/,
  'The existing VIDEO download User-Agent must remain stable.',
)

assert.deepEqual(violations, [], `Old visible brand remains:\n${violations.join('\n')}`)
```

- [ ] **Step 2: Run the new test and verify the RED state**

Run from `E:\yFeiEye\WEB`:

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
```

Expected: FAIL with `Old visible brand remains` and a non-empty list from newly merged README, APP, WEB, VIDEO, DEVICE, scripts, or documentation files. The failure must not be caused by a missing file or syntax error.

### Task 5: Replace visible branding without changing protected contracts

**Files:**
- Modify: tracked text files reported by `WEB/tests/visibleBrandingResidue.test.ts`
- Rename if present: `.doc/项目介绍/EasyAIoT项目介绍 V2.0.pptx`
- Preserve: `AI/app/utils/sam_model_download.py` User-Agent
- Preserve: `VIDEO/app/utils/face_model_download.py` User-Agent
- Preserve: `VIDEO/app/utils/plate_model_download.py` User-Agent
- Preserve: `WEB/src/views/node/utils/constants.ts` ZLM secret
- Inspect: `.image/logo.png`
- Inspect: `.image/banner/banner1143.jpg`
- Inspect: `.image/banner/banner1144.jpg`
- Inspect: `.image/banner/banner1145.jpg`
- Inspect: `.image/banner/banner1146.jpg`
- Test: `WEB/tests/visibleBrandingResidue.test.ts`

- [ ] **Step 1: Perform the literal text replacement with exact protected sentinels**

Run this PowerShell block from `E:\yFeiEye`. It is a bulk mechanical rewrite; it writes only files whose non-protected visible brand text changes and preserves UTF-8 BOM state:

```powershell
$protected = [ordered]@{
  'EasyAIoT2025' = '__YFEIEYE_PROTECTED_WECHAT__'
  'EasyAIoT_Media_Secret' = '__YFEIEYE_PROTECTED_ZLM_SECRET__'
  'EasyAIoT-AI/1.0' = '__YFEIEYE_PROTECTED_AI_USER_AGENT__'
  'EasyAIoT-VIDEO/1.0' = '__YFEIEYE_PROTECTED_VIDEO_USER_AGENT__'
}
$files = @(git grep -l -I -E 'EasyAIoT|Easy AI Internet of Things' -- .)
foreach ($path in $files) {
  if ($path -like 'docs/superpowers/*') { continue }
  $bytes = [IO.File]::ReadAllBytes($path)
  $hasBom = $bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF
  $offset = if ($hasBom) { 3 } else { 0 }
  $source = [Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset)
  $updated = $source
  foreach ($entry in $protected.GetEnumerator()) {
    $updated = $updated.Replace($entry.Key, $entry.Value)
  }
  $updated = $updated.Replace('Easy AI Internet of Things', 'yFeiEye')
  $updated = $updated.Replace('EasyAIoT', 'yFeiEye')
  foreach ($entry in $protected.GetEnumerator()) {
    $updated = $updated.Replace($entry.Value, $entry.Key)
  }
  if ($updated -ne $source) {
    [IO.File]::WriteAllText($path, $updated, [Text.UTF8Encoding]::new($hasBom))
  }
}
```

Expected: exact visible `EasyAIoT` branding changes to `yFeiEye`; the four protected literals remain byte-for-byte present.

- [ ] **Step 2: Remove any old-brand path name without losing the asset**

```powershell
$oldPpt = '.doc/项目介绍/EasyAIoT项目介绍 V2.0.pptx'
$newPpt = '.doc/项目介绍/yFeiEye项目介绍 V2.0.pptx'
if (Test-Path -LiteralPath $oldPpt) {
  if (-not (Test-Path -LiteralPath $newPpt)) {
    git mv -- $oldPpt $newPpt
  } elseif ((Get-FileHash -LiteralPath $oldPpt).Hash -eq (Get-FileHash -LiteralPath $newPpt).Hash) {
    git rm -- $oldPpt
  } else {
    git mv -- $oldPpt '.doc/项目介绍/yFeiEye项目介绍 V2.0-upstream.pptx'
  }
}
git ls-files | rg 'EasyAIoT|Easy AI Internet of Things'
```

Expected: the final command has no output.

- [ ] **Step 3: Audit page-visible raster assets**

Open the five listed logo/banner files with the local image viewer. If an image visibly contains `EasyAIoT`, invoke the `imagegen` image-editing skill with this exact instruction:

```text
Replace only the visible EasyAIoT brand text with yFeiEye. Preserve the original dimensions, layout, colors, imagery, legibility, and all non-brand text. Do not add new logos or decorative elements.
```

Write the edited result back to the same tracked asset path, then reopen it for visual verification. If an asset has no old brand, leave it byte-for-byte unchanged.

- [ ] **Step 4: Run the GREEN brand test and a lowercase visible-surface audit**

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
rg -n -i "easyaiot" APP/src WEB/src `
  -g '*.vue' -g '*.ts' -g '*.tsx' -g '*.json' -g '*.html'
```

Expected: the contract test exits 0. Review every lowercase result; retain technical keys such as `key: 'easyaiot'`, storage keys, URLs, secrets, and compatibility mappings, while replacing any literal that is rendered to a user.

### Task 6: Verify the merged tree and create the merge commit

**Files:**
- Test: `WEB/tests/visibleBrandingResidue.test.ts`
- Verify: every staged merge/brand file

- [ ] **Step 1: Run focused merge checks**

From `E:\yFeiEye`:

```powershell
git ls-files -u
git diff --cached --check
bash -n '.scripts/docker/cache_python_resources_arm.sh'
bash -n '.scripts/docker/install_middleware_linux.sh'
python -m py_compile 'VIDEO/app/utils/algo_model_detect.py' 'VIDEO/services/realtime_algorithm_service/run_deploy.py'
docker compose -f '.scripts/docker/docker-compose.yml' config --quiet
```

Expected: every command exits 0 and `git ls-files -u` is empty.

- [ ] **Step 2: Run frontend contracts, type checking, and the production build**

From `E:\yFeiEye\WEB`:

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
pnpm exec tsx tests/livePlayerCodecStrategy.test.ts
pnpm exec tsx tests/gb28181MonitorSync.test.ts
pnpm exec tsx tests/gb28181PlayTimeout.test.ts
pnpm exec tsx tests/streamUrlRewrite.test.ts
node --test 'src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs'
pnpm run type:check
pnpm run build
```

Expected: all focused tests pass, type checking exits 0, and Vite completes a production build.

- [ ] **Step 3: Stage all resolved merge and brand paths, then inspect**

```powershell
git add -u
git add -- 'WEB/tests/visibleBrandingResidue.test.ts'
git diff --cached --name-status
git diff --cached --check
git status --short
```

Expected: no unmerged paths; artifact roots remain untracked; the 12 pre-existing source/test work paths are still absent because they remain protected in the named stash.

- [ ] **Step 4: Create the merge commit**

```powershell
git commit -m "Merge origin/main and preserve yFeiEye branding"
git show --stat --oneline -1
git merge-base --is-ancestor origin/main HEAD
```

Expected: commit succeeds; the final ancestry command exits 0.

### Task 7: Restore the user's original local work and run final verification

**Files:**
- Restore the 12 paths listed in Task 1
- Verify all repository and artifact state

- [ ] **Step 1: Reapply the named stash without dropping it**

```powershell
git stash list --format='%gd %s' | Select-String 'codex/pre-sync-20260710'
git stash apply 'stash@{0}'
```

Expected: the four untracked source/test files return. The six tracked overlap paths may conflict and must be resolved using the post-merge file, the stash version, and the stash parent as the three comparison inputs.

- [ ] **Step 2: Resolve any stash-apply conflicts while retaining both sets of work**

Expected overlap paths:

```text
VIDEO/models.py
VIDEO/run.py
WEB/src/views/dashboard/monitor/components/Header.vue
WEB/src/views/dashboard/monitor/components/Sidebar.vue
WEB/src/views/dashboard/monitor/components/VideoMonitor.vue
WEB/src/views/dashboard/monitor/index.vue
```

For each conflicted file, preserve the restored local dashboard/migration changes and the newly merged upstream behavior. Remove conflict markers, stage the resolved file only long enough to mark it resolved, then return it to an unstaged state with:

```powershell
git restore --staged -- <resolved-path>
```

Expected: `git ls-files -u` is empty and all restored source changes remain visible in `git diff` or as untracked files.

- [ ] **Step 3: Run tests that belong to the restored local work**

```powershell
python -m pytest 'VIDEO/tests/test_algorithm_task_migrations.py' -q
pnpm exec tsx tests/dashboardOperationalReadiness.test.ts
pnpm exec tsx tests/monitorCommercialDashboardStyle.test.ts
node --test 'src/views/dashboard/monitor/components/monitorResponsiveStyle.test.cjs'
```

Run the first command from `E:\yFeiEye` and the three WEB commands from `E:\yFeiEye\WEB`.

Expected: every command exits 0.

- [ ] **Step 4: Prove preservation, ancestry, branding, and build state**

```powershell
git merge-base --is-ancestor origin/main HEAD
git ls-files -u
git diff --check
git status --short --branch
git stash list --format='%gd %s' | Select-String 'codex/pre-sync-20260710'
```

From `E:\yFeiEye\WEB`:

```powershell
pnpm exec tsx tests/visibleBrandingResidue.test.ts
pnpm run build
```

Expected: upstream is an ancestor, no unmerged paths exist, diff checks pass, the original local work remains present, the named stash still exists as a recovery point, the visible-brand contract passes, and the production build exits 0.

- [ ] **Step 5: Drop the recovery stash only after all preservation checks pass**

```powershell
git stash drop 'stash@{0}'
git stash list --format='%gd %s' | Select-String 'codex/pre-sync-20260710'
```

Expected: the drop command succeeds and the final search has no output. Do not run this step if any restored path, test, conflict check, ancestry check, or build verification is incomplete.
