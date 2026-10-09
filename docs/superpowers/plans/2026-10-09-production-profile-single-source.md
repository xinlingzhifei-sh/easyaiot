# Production Profile Single Source Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用一份声明式 manifest 统一 edge、mini、standard、full 的模块、中间件、DEVICE 服务、资源预算和 WEB capability，并在验证后安全回收第一批已失效实现。

**Architecture:** `.scripts/docker/deploy-profiles.json` 是唯一手工维护的事实源；零依赖 Node 生成器产生提交到仓库的 Shell 与 TypeScript 只读产物。部署脚本保留现有函数 Interface，但 Implementation 改为查询生成文件；WEB 通过稳定 capability ID 消费同一份数据。生成器 `--check`、Node 契约测试和 WEB 类型检查共同阻止漂移。

**Tech Stack:** JSON、Node.js `node:test`、Bash、TypeScript/Vue 3、PowerShell（Windows 回收站 API）

---

### Task 1: 固定当前基线并修复陈旧部署断言

**Files:**
- Modify: `.scripts/docker/maven-mirror.test.mjs:44-53`
- Test: `.scripts/docker/maven-mirror.test.mjs`

- [ ] **Step 1: 更新 Compose 契约断言**

断言 `device_compose()` 同时传入两个 env 文件，并通过 `files` 数组传递 base/desktop compose，而不再要求旧的单一 `-f "$COMPOSE_FILE"` 字符串：

```js
assert.match(
  installer,
  /\$DOCKER_COMPOSE --env-file "\$MIDDLEWARE_ENV_FILE" --env-file "\$DEVICE_COMPOSE_ENV_FILE"/,
);
assert.match(installer, /local -a files=\(-f "\$COMPOSE_FILE"\)/);
assert.match(installer, /"\$\{files\[@\]\}"/);
```

- [ ] **Step 2: 运行基线测试**

Run:

```powershell
$tests = Get-ChildItem .scripts/docker -Filter *.test.mjs | ForEach-Object FullName
node --test $tests
```

Expected: `tests 10`, `pass 10`, `fail 0`。

### Task 2: 先写统一 manifest 的失败契约

**Files:**
- Create: `.scripts/docker/deploy-profile-manifest.test.mjs`
- Create: `WEB/tests/deploymentProfileManifest.test.ts`

- [ ] **Step 1: 写 Shell/生成物契约测试**

测试读取规范 JSON，要求：

```js
assert.deepEqual(Object.keys(manifest.profiles), ['edge', 'mini', 'standard', 'full']);
assert.equal(manifest.profiles.edge.memoryMiB, 2048);
assert.deepEqual(manifest.profiles.mini.deviceServices, [
  'iot-gateway', 'iot-system', 'iot-infra', 'iot-sink',
]);
for (const profile of Object.values(manifest.profiles)) {
  assert.equal(profile.modules.includes('HARNESS'), false);
  assert.equal(profile.modules.includes('IDEA'), false);
  assert.equal(profile.modules.includes('TERMINAL'), false);
  assert.equal(profile.modules.includes('PANEL'), false);
  assert.equal(profile.middlewareServices.includes('GPUStack'), false);
}
```

测试还应执行：

```js
const result = spawnSync(process.execPath, [generatorPath, '--check'], {
  cwd: rootDir,
  encoding: 'utf8',
});
assert.equal(result.status, 0, result.stderr || result.stdout);
```

- [ ] **Step 2: 写 WEB capability 契约测试**

通过源码检查确保 `deployProfile.ts` 导入生成 manifest，并禁止重新出现 profile 专用硬编码集合：

```ts
assert.match(source, /deploymentProfileManifest/)
assert.doesNotMatch(source, /MINI_HIDDEN_MENU_NAMES|STANDARD_HIDDEN_MENU_NAMES/)
assert.match(generated, /flowTicket|visualize|transform|harness/)
```

- [ ] **Step 3: 验证 RED**

Run:

```powershell
node --test .scripts/docker/deploy-profile-manifest.test.mjs
pnpm --dir WEB exec tsx tests/deploymentProfileManifest.test.ts
```

Expected: 两项均因 manifest/生成物不存在而失败。

### Task 3: 实现规范源与确定性生成器

**Files:**
- Create: `.scripts/docker/deploy-profiles.json`
- Create: `.scripts/docker/generate-deploy-profiles.mjs`
- Generate: `.scripts/docker/deploy-profiles.generated.sh`
- Generate: `WEB/src/generated/deploymentProfileManifest.ts`

- [ ] **Step 1: 添加规范 JSON**

Schema 必须包含：

```json
{
  "schemaVersion": 1,
  "networkName": "yfeieye-network",
  "profileOrder": ["edge", "mini", "standard", "full"],
  "moduleOrder": ["HARNESS", "IDEA", "TERMINAL", ".scripts/docker", "DEVICE", "AI", "RTC", "POST", "VIDEO", "WEB", "APP", "VISUALIZE", "TWIN", "TRANSFORM", "PANEL"],
  "moduleOptInEnvironment": {
    "HARNESS": "EASYAIOT_ENABLE_HARNESS",
    "IDEA": "EASYAIOT_ENABLE_IDEA",
    "TERMINAL": "EASYAIOT_ENABLE_TERMINAL",
    "TWIN": "EASYAIOT_ENABLE_TWIN",
    "PANEL": "EASYAIOT_ENABLE_PANEL"
  },
  "middlewareOptInEnvironment": {
    "GPUStack": "EASYAIOT_ENABLE_GPUSTACK"
  },
  "profiles": {}
}
```

每个 profile 明确列出 `aliases`、`label`、`memoryMiB`、`frontendProfile`、`localStorage`、`modules`、`middlewareServices`、`deviceServices` 与 `capabilities`。禁止通过“未出现在 skip 列表”隐式启用新增服务。

- [ ] **Step 2: 添加生成器**

生成器只使用 Node 标准库，先验证所有引用属于 catalog，再生成稳定 LF 文本：

```js
const checkOnly = process.argv.includes('--check');
const outputs = new Map([
  [shellOutputPath, renderShell(manifest)],
  [webOutputPath, renderTypeScript(manifest)],
]);

for (const [path, expected] of outputs) {
  if (checkOnly) {
    assert.equal(readFileSync(path, 'utf8'), expected, `${relative(rootDir, path)} is stale`);
  } else {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, expected, 'utf8');
  }
}
```

- [ ] **Step 3: 生成并验证 GREEN**

Run:

```powershell
node .scripts/docker/generate-deploy-profiles.mjs
node --test .scripts/docker/deploy-profile-manifest.test.mjs
```

Expected: generator exit `0`; manifest tests pass。

### Task 4: 将 Shell 查询 Interface 接到生成数据

**Files:**
- Modify: `.scripts/docker/deploy_profile.sh`
- Modify: `.scripts/docker/install_linux.sh`
- Modify: `.scripts/docker/install_linux_arm.sh`
- Modify: `.scripts/docker/install_linux_kylin.sh`
- Modify: `.scripts/docker/install_desktop_common.sh`
- Modify: `.scripts/docker/install_business_linux.sh`
- Modify: `.scripts/docker/install_windows.ps1`
- Modify: `.scripts/docker/runtime_image_common.sh`
- Modify: `.scripts/docker/analyze_deploy_memory.sh`
- Test: `.scripts/docker/deploy-profile-manifest.test.mjs`

- [ ] **Step 1: source 生成的 Shell 数据**

在 `deploy_profile.sh` 顶部 fail-closed 加载：

```bash
_DEPLOY_PROFILE_MANIFEST_SH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/deploy-profiles.generated.sh"
if [ ! -f "$_DEPLOY_PROFILE_MANIFEST_SH" ]; then
    echo "[ERROR] 缺少生成的部署规格数据: $_DEPLOY_PROFILE_MANIFEST_SH" >&2
    return 1 2>/dev/null || exit 1
fi
source "$_DEPLOY_PROFILE_MANIFEST_SH"
unset _DEPLOY_PROFILE_MANIFEST_SH
```

- [ ] **Step 2: 保留 API、替换 Implementation**

`_resolve_deploy_profile_raw`、`compose_profile_flags`、`middleware_skipped_services`、`device_skipped_services`、`device_enabled_services`、`frontend_deploy_profile`、`module_enabled_for_deploy_profile`、`deploy_profile_budget_mib` 和 `deploy_profile_budget_label` 只调用生成 helper。

模块显式开关优先于 manifest 默认值：

```bash
module_enabled_for_deploy_profile() {
    local module="$1" profile="${EASYAIOT_DEPLOY_PROFILE:-full}" override
    override="$(deploy_profile_manifest_module_override_env "$module")"
    if [ -n "$override" ]; then
        case "${!override:-}" in
            1|true|TRUE|yes|YES|on|ON) return 0 ;;
            0|false|FALSE|no|NO|off|OFF) return 1 ;;
        esac
    fi
    deploy_profile_manifest_module_enabled "$profile" "$module"
}
```

- [ ] **Step 3: 模块顺序从 manifest 读取**

Linux/ARM/Kylin/desktop/business 安装器统一使用：

```bash
read -r -a MODULES <<< "$(deploy_profile_manifest_module_order)"
```

平台不支持的模块继续通过平台过滤器处理，不再复制 profile 归属。

`install_windows.ps1` 直接用 `ConvertFrom-Json` 读取同一 manifest 中的 Docker Desktop 资源目标；`runtime_image_common.sh` 的 profile 列表和 full-only 判定改用生成 helper。

- [ ] **Step 4: 资源分析改为纯查询**

`analyze_deploy_memory.sh` 使用生成的 container catalog 和 `networkName`；解析 profile 时不得调用会写模块 `.env` 的 `apply_deploy_profile`。删除重复的 edge 输出块。

- [ ] **Step 5: 运行部署契约**

Run:

```powershell
$tests = Get-ChildItem .scripts/docker -Filter *.test.mjs | ForEach-Object FullName
node --test $tests
```

Expected: all pass, exit `0`。

### Task 5: WEB 改为 capability ID 消费者

**Files:**
- Modify: `WEB/src/utils/deployProfile.ts`
- Modify: `WEB/package.json`
- Test: `WEB/tests/deploymentProfileManifest.test.ts`
- Do not modify: `WEB/tests/dashboardAiDetectionStartup.test.ts`
- Do not modify: `WEB/tests/dashboardRealtimeVideoStartup.test.ts`

- [ ] **Step 1: 删除 profile 专用菜单集合**

使用生成数据实现统一查询：

```ts
import { deploymentProfileManifest } from '@/generated/deploymentProfileManifest'

export type DeployCapability = keyof typeof deploymentProfileManifest.capabilities

export function isDeployCapabilityEnabled(capability: DeployCapability): boolean {
  return deploymentProfileManifest.profiles[getDeployProfile()].capabilities.includes(capability)
}

export function isMenuHiddenByDeployProfile(menuName: string | undefined | null): boolean {
  const capability = deploymentProfileManifest.menuCapabilityAliases[String(menuName ?? '').trim()]
  return capability ? !isDeployCapabilityEnabled(capability) : false
}
```

现有 `isGb28181Enabled`、`isRtcEnabled`、`isTrainAdvancedEnabled` 等导出保持不变，只改为 capability wrapper，避免扩大调用方 diff。

- [ ] **Step 2: 添加生成检查命令**

`WEB/package.json` 增加：

```json
"profile:check": "node ../.scripts/docker/generate-deploy-profiles.mjs --check"
```

- [ ] **Step 3: 验证 WEB**

Run:

```powershell
pnpm --dir WEB exec tsx tests/deploymentProfileManifest.test.ts
pnpm --dir WEB type:check
```

Expected: both exit `0`。

### Task 6: 第一批可恢复物理删除

**Files:**
- Delete after reference proof: `PANEL/ui/dist-old-1785385383/`
- Delete after reference proof: `PANEL/ui/dist-oldx/`
- Delete after reference proof: `PANEL/ui/dist-prev-tmp/`
- Delete after reference proof: `PANEL/ui/dist-root-stuck/`
- Delete after import-graph proof: `WEB/src/api/bpm/`
- Delete after import-graph proof: `WEB/src/api/member/`
- Delete after import-graph proof: `WEB/src/api/mp/`
- Delete after import-graph proof: `WEB/src/api/pay/`
- Delete after import-graph proof: eight individually listed zero-inbound WEB API files
- Preserve: `TASK/`, `NODE/`, `EDGE/edge/`, `EDGE/runtime/`, RUNTIME TaskManager

- [ ] **Step 1: 生成删除前证据清单**

Run:

```powershell
git grep -n -I -E 'dist-old-1785385383|dist-oldx|dist-prev-tmp|dist-root-stuck' -- .
git grep -n -I -E '/?@/api/(bpm|member|mp|pay)/' -- WEB/src
git status --short -- PANEL/ui/dist-old-1785385383 PANEL/ui/dist-oldx PANEL/ui/dist-prev-tmp PANEL/ui/dist-root-stuck WEB/src/api
```

Expected: 每个目标都能分类为“无引用”“需迁移引用”或“不可删”；任何不确定项停止删除。

- [ ] **Step 2: 保护本地配置**

只枚举文件名与 Git 状态，不读取 `NODE/agent.env`、`.env` 或其他凭据内容。`TASK` 本地 INI、`NODE`、旧 EDGE 和 RUNTIME TaskManager 不进入本批删除。

- [ ] **Step 3: 使用 Windows 回收站 API**

仅对已证明安全的完整绝对路径调用 `Microsoft.VisualBasic.FileIO.FileSystem.DeleteDirectory(..., RecycleOption.SendToRecycleBin)`；禁止 `Remove-Item`、`del`、`rm`、`git clean`。删除后记录回收路径和恢复方式。

- [ ] **Step 4: 验证删除没有破坏生产契约**

Run:

```powershell
node .scripts/docker/generate-deploy-profiles.mjs --check
$tests = Get-ChildItem .scripts/docker -Filter *.test.mjs | ForEach-Object FullName
node --test $tests
pnpm --dir WEB exec tsx tests/deploymentProfileManifest.test.ts
pnpm --dir WEB type:check
git diff --check
```

Expected: all commands exit `0`；删除只包含审计批准的目标。

---

## Self-review

- Spec coverage: manifest、Shell、WEB、资源预算、测试和回收站删除均有独立任务。
- Deletion boundary: 不读取凭据；有引用或本地配置的目标停在审计阶段。
- Type consistency: canonical 名称统一使用 `deploymentProfileManifest`、`DeployCapability`、`deploy_profile_manifest_*`。
- Scope control: 不改告警/监管事件业务语义，不触碰用户已有两个 dashboard 测试改动，不进行全仓格式化。
