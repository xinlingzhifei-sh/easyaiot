# 周边模块拆分为独立仓库（TERMINAL / HARNESS / IDEA / SITE / TWIN）

- 日期：2026-10-22
- 分支：`claude/split-peripheral-modules-50aec0`
- 状态：已实施（主仓库分支 + 本地裸仓库），GitHub 侧仓库待用户创建

## 1. 背景与目标

仓库根下有一批与核心平台（DEVICE / AI / VIDEO / WEB / RUNTIME …）关系很弱的周边模块。它们不在任何部署规格里，只能用 `EASYAIOT_ENABLE_*` 显式开启，却和核心代码共用一份历史、一套 PR 流程、一份 `.gitignore`。本次把其中耦合最松的五个拆成独立仓库：

| 模块 | 内容 | 主仓库内文件数 | 体积 |
|---|---|---|---|
| `TERMINAL/` | 多协议终端（Go + Wails，基于开源 uniTerm） | 687 | 12 MB |
| `HARNESS/` | 平台 AI Agent 助手 Sidecar（DeepSeek Harness） | 24 | 0.4 MB |
| `IDEA/` | 社区贡献在线 IDE（code-server + Python 门户） | 61 | 2.5 MB |
| `SITE/` | 官方网站（Vue 3 + Vite 6，纯静态） | 45 | 2.8 MB |
| `TWIN/` | 工业园区数字孪生 MVP（Three.js） | 15 | 0.1 MB |

目标：

1. 五个模块各自成为保留历史的独立仓库。
2. 主仓库删除这些目录，但**已有的部署入口继续可用**：`EASYAIOT_ENABLE_HARNESS=1 install_linux.sh install`、`install_linux.sh site install`、`build-runtime TWIN` 等命令行为不变，只是模块代码从外部仓库取。
3. 部署规格仍以 `deploy-profiles.json` 为单一事实源，外部仓库地址也写在这里。

非目标：不改各模块自身代码和镜像名；不改 WEB 里调用这些服务的前端界面（它们只经 HTTP 访问 3080 / 9300 / 9245 端口，与源码目录无关）；不在 GitHub 上建仓库或推送。

## 2. 耦合点清单（拆分前）

### 2.1 主仓库 → 模块

| 耦合点 | 位置 | 涉及模块 | 拆分后处理 |
|---|---|---|---|
| 规格清单 | `deploy-profiles.json` 的 `moduleOrder`、`moduleEnvironmentOverrides`（`EASYAIOT_ENABLE_*`）、`containerNames.modules` | HARNESS / IDEA / TERMINAL / TWIN | 保留（模块仍是可选模块），新增 `externalModules` 段写仓库地址与分支 |
| 统一安装入口 | `install_linux.sh` / `install_linux_arm.sh` / `install_linux_kylin.sh` / `install_mac.sh` 的 `execute_module_command`，`install_business_linux.sh` 的 `execute_module`，`install_desktop_common.sh` 的 `execute_module_command` | 四个可选模块 | 每个入口开头加一行 `ensure_external_module_checkout`：目录缺失时先拉取 |
| 官网入口 | 三个 `install_linux*.sh` 的 `run_site_module`（`site` 子命令） | SITE | 同上 |
| 运行时镜像 | `runtime_image.sh` 的 `build_module_with_install_script`、`runtime_image_common.sh` 的模块↔镜像映射 | 五个 | 构建前拉取；映射表不变（镜像名不变） |
| 清理 / 校验 | `cleanup_build_runtime.sh`（按镜像名清理）、`verify_update_chain.sh`（检查各模块 install 脚本） | 四个可选模块 | 清理不依赖目录，不改；校验对"外部模块且未检出"改为跳过 |
| WEB nginx | `nginx.conf` / `nginx.mini.conf` / `nginx.edge.conf` / `nginx.prod-server.conf` 的 8090 server 托管 `html/SITE`；`nginx.site.conf` 供 SITE 容器使用 | SITE | 不改：nginx 只认产物目录，不认源码目录。`nginx.site.conf` 仍由 SITE 的 Dockerfile 通过 `--build-context webconf=../WEB/conf` 取用 |
| WEB compose | `WEB/docker-compose.yaml` 注释掉的 `${SITE_HTML:-../SITE/dist}` 挂载 | SITE | 只改注释，说明 SITE 来自外部检出或 release 产物 |
| WEB 前端 | `HarnessFloatChat`、`IdeaFloatBall`、`views/harness`、`views/idea`、`utils/{harness,idea,terminal}.ts`、`.env.*` 的 `VITE_IDEA_URL` / `VITE_HARNESS_URL` | HARNESS / IDEA / TERMINAL | 不改：纯 HTTP 集成 |
| COMPILE | `pack_desktop_runtime.sh`、`pack_deb.sh` 的模块循环含 `TWIN` | TWIN | 不改：`runtime_copy_glob` 对不存在的目录是空操作；打包机上若已检出 TWIN 仍会带上 |
| 仓库元数据 | `.gitignore`（SITE / TWIN / IDEA / HARNESS / TERMINAL 的产物与运行时数据）、`.gitattributes`（IDEA / HARNESS env 文件 LF、SITE / IDEA / HARNESS 的 `-diff`）、`.dockerignore`（TWIN Blender 产物） | 五个 | 主仓库改为整体忽略 `/TERMINAL/` 等五个目录；原有细分规则搬进各独立仓库 |
| 测试 | `.scripts/docker/deploy-profile-manifest.test.mjs` 检查 `TWIN/docker-compose.yml` 的网络名；`WEB/tests/visibleBrandingResidue.test.ts` 读取 SITE / TWIN / TERMINAL / HARNESS / IDEA 下的文件检查品牌 | 五个 | 删去对已移出文件的断言（品牌检查应随模块迁入各自仓库，见 §7） |
| CI | `.github/workflows/pr-tests.yml` 没有这五个模块的 job | — | 不改 |
| 文档 | `AGENTS.md`、根目录 6 份 README、`.scripts/docker/Readme.md`、PR 模板的模块勾选项 | 五个 | 改为说明"独立仓库 + 如何取用" |

### 2.2 模块 → 主仓库（反向耦合）

拆分时必须保住的依赖：

- HARNESS / TERMINAL / IDEA 的 `install.sh`、TWIN 与 SITE 的 `install_linux.sh` 都 `source ../.scripts/docker/deploy_profile.sh`（SITE 还用 `init-build-cache-dirs.sh`、`runtime_image_common.sh`），大多带 `|| true` 或 `[ -f ]` 保护。
- SITE 的 Dockerfile 需要 `--build-context webconf=../WEB/conf`。
- IDEA 的用途就是编辑主仓库：`IDEA_HOST_PROJECT_ROOT` 默认取 `${IDEA}/..`，即平台根目录。
- TERMINAL 的 compose 与 `terminal.env.example` 读中间件 `.scripts/docker/.env.docker` 的口令。

结论：这些模块**是"挂在平台根目录下运行"的插件**。所以拆分后最稳妥的集成方式是把外部仓库检出回原来的位置 `<平台根>/<模块>`，反向依赖的相对路径全部原样成立，各模块脚本一行都不用改。

## 3. 拆分后的集成方式

### 3.1 方案比较

| 方案 | 说明 | 结论 |
|---|---|---|
| A. 外部检出到原目录（采用） | 规格清单记录仓库地址与 ref；安装/构建入口发现目录缺失时 `git clone --depth 1` 到 `<平台根>/<模块>`，主仓库 `.gitignore` 忽略该目录 | 反向依赖零改动；离线可用 release 压缩包或手工放目录；不碍主仓库瘦身 |
| B. git submodule | 主仓库记录子模块提交 | 部署流程大量用 `git pull` 更新主仓库，子模块需要额外 `submodule update`；本地是部分克隆，子模块与改写历史（仓库瘦身）叠加后易出错。否决 |
| C. 只拉镜像 | 不要源码，只 `docker pull` | 这些模块的 compose、env 模板、install 脚本都在模块目录里，光有镜像跑不起来；且 IDEA / HARNESS 镜像目前在本机构建。可作为后续演进（模块仓库自带发布 compose 包） |

### 3.2 机制（方案 A）

`deploy-profiles.json` 新增：

```json
"externalModules": {
  "HARNESS":  { "repository": "https://github.com/xinlingzhifei-sh/yfeieye-harness.git",  "ref": "main" },
  "IDEA":     { "repository": "https://github.com/xinlingzhifei-sh/yfeieye-idea.git",     "ref": "main" },
  "TERMINAL": { "repository": "https://github.com/xinlingzhifei-sh/yfeieye-terminal.git", "ref": "main" },
  "TWIN":     { "repository": "https://github.com/xinlingzhifei-sh/yfeieye-twin.git",     "ref": "main" },
  "SITE":     { "repository": "https://github.com/xinlingzhifei-sh/yfeieye-site.git",     "ref": "main" }
}
```

`generate-deploy-profiles.mjs` 据此生成 `deploy_profile_manifest_external_modules`、`deploy_profile_manifest_external_module_repository`、`deploy_profile_manifest_external_module_ref`。SITE 不在 `moduleOrder` 里（它走 `site` 子命令），所以校验只要求 `externalModules` 的键是大写模块名、带 `repository` 与 `ref`。

新文件 `.scripts/docker/external_modules.sh`（由 `deploy_profile.sh` 加载）提供：

```
ensure_external_module_checkout <模块> [命令] [平台根]
```

- 非外部模块：直接返回 0。
- 命令不是 `install|update|build|build-runtime|start|restart` 之一（如 `status` / `logs` / `stop` / `clean`）：不拉取，交给原有"目录不存在"分支处理。
- 目录已存在：返回 0；命令为 `update` 且目录是本函数克隆的（`git config yfeieye.externalModule` 标记），执行 `git pull --ff-only`，失败只告警。
- 目录不存在，按以下顺序取得：
  1. `YFEIEYE_<模块>_ARCHIVE`：本地 `.tar.gz` 路径或 http(s) URL（release 产物，顶层一层目录会被剥掉）；
  2. 否则 `git clone --depth 1 --branch <ref> <repository>`，仓库与 ref 可用 `YFEIEYE_<模块>_REPO` / `YFEIEYE_<模块>_REF` 覆盖；
  3. `YFEIEYE_EXTERNAL_MODULES_FETCH=0` 时不联网，直接报错并提示手工放置目录。
- 克隆先落到 `<模块>.fetch.<pid>` 临时目录，成功后再改名，失败不留半成品目录。

调用点（每处一行，放在原有"目录不存在"判断之前）：`install_linux.sh` / `install_linux_arm.sh` / `install_linux_kylin.sh` / `install_mac.sh` / `install_desktop_common.sh` 的 `execute_module_command`，`install_business_linux.sh` 的 `execute_module`，三个 `run_site_module`，`runtime_image.sh` 的 `build_module_with_install_script`。Windows 的 `install_windows.ps1` 最终经 Git Bash / WSL 调用 `install_desktop_common.sh`，随之生效。

SITE 产物：WEB 容器托管官网的方式不变——`pnpm --dir SITE build` 后把 `SITE/dist` 挂到 `/usr/share/nginx/html/SITE`；SITE 现在来自外部检出（`install_linux.sh site install` 会自动拉取）或 SITE 仓库 release 里的 dist 包。

### 3.3 版本钉定

首版默认 `ref: main`，便于联调。各仓库打出第一个 tag 后，建议把 `externalModules.*.ref` 改为 tag（`git clone --branch` 同样接受 tag），由主仓库提交来控制周边模块版本。

## 4. 历史保留方案

### 4.1 取历史

本地仓库是部分克隆（`blob:none`），不能直接 `filter-repo`。GitHub `xinlingzhifei-sh/yFeiEye` 的 `main` 与本地 `main`（`8aa884e3c`）一致，故以只读方式做一次 blobless 裸克隆到 `E:\yFeiEye-split\_full`（`pushurl` 置为 `DISABLED-no-push`）。`origin`（easyaiot）缺本地 590 多个提交，不用它。

### 4.2 拆分

`git subtree split` 不能在裸仓库运行，且在 Git Bash 下按提交逐个起进程，3600 多个提交要很久。改用一个等价的 Python 脚本（只调 `git cat-file` / `hash-object` / `merge-base`，与 subtree split 相同的规则）：

- 子目录树与已映射父提交相同 → 跳过该提交；
- 合并提交：若非同树一侧带来了同树一侧没有的提交，则保留合并，否则折叠；
- 新提交逐字节复制作者、提交者、时间、编码与说明，只替换 `tree` / `parent`，去掉 `gpgsig` / `mergetag`（签名对新树无效）；
- 目录在某提交中不存在时沿用第一个已映射父提交，保持血缘。

然后 `git push` 到 `E:\yFeiEye-split\<模块>.git`（裸仓库）：推送时 pack-objects 会从 GitHub 懒取所需的 blob。每个仓库都做了 `fsck --full`，并核对最终树与 `main:<模块>` 完全一致。原提交 → 新提交的对照表存为 `E:\yFeiEye-split\<模块>.map.txt`。

### 4.3 拆分后补丁提交

每个独立仓库在拆分历史之上追加一个提交：

- 把主仓库 `.gitignore` / `.gitattributes` / `.dockerignore` 里属于该模块的规则搬进模块自己的文件（例如 IDEA / HARNESS env 模板强制 LF）；
- 统一 `*.sh` 等文本 LF 规则；
- README 开头说明"本仓库从 yFeiEye 主仓库拆出，需检出到平台根目录下的 `<模块>/` 运行"。

### 4.4 与仓库瘦身（改写历史）的先后

另一个会话在用改写历史的方式给主仓库瘦身。建议顺序：

1. **先合入本分支**（拆分）。独立仓库的历史取自改写前的 `8aa884e3c`，提交 ID 本来就是新生成的，与主仓库后续是否改写无关，不需要重做。
2. **再做瘦身**，并把 `TERMINAL/ HARNESS/ IDEA/ SITE/ TWIN/` 加进瘦身的清除路径（`git filter-repo --path TERMINAL --invert-paths` 等）：这些历史已经在独立仓库里保住了，从主仓库历史中剔除可以再省约 18 MB 工作树体积及其历史版本。
3. 若瘦身先做：拆分结果仍然有效（内容相同），只是 `<模块>.map.txt` 里的"原提交"会指向改写前的 ID，追溯时要用改写前的备份引用。

## 5. 其他候选模块评估

| 模块 | 与主仓库的耦合 | 建议 |
|---|---|---|
| `PANEL/` | 被安装脚本深度引用（`panel_skip_deploy_reason`、`install_desktop_common.sh` 专门分支、`docker_mirror_common.sh`、COMPILE 把它打成 deb/rpm/exe/dmg 安装包作为"无源码 runtime"的入口）；容器内还要调用平台安装脚本 | **暂不拆**。它是交付入口而非周边功能；若要拆应与 COMPILE 一起拆成"安装器"仓库，并先把 runtime 包格式稳定下来 |
| `COMPILE/` | 打包 PANEL、EDGE、RUNTIME 和主仓库 `.scripts`；`compile-packaging.yml` 在主仓库；脚本按主仓库目录布局取文件 | **暂不拆**。它天然依赖主仓库布局；单独拆只会把一份目录清单复制到两处。与 PANEL 一起评估 |
| `ANDROID/` `IOS/` `HARMONYOS/` | 只消费 `APP/dist/build/{app,h5}`，版本号与 `APP/manifest.config.ts` 交叉校验；历史很短（3 / 7 / 3 个提交）；ANDROID 含约 30 MB 的 `.aar` 二进制 | **建议下一轮合并拆成一个 `yfeieye-mobile-shells` 仓库**，`.aar` 改为 release 附件或首次构建时下载；同时让瘦身会话从主仓库历史中清掉这些 `.aar`。本轮不动，因为版本号交叉校验脚本需要改为读取 APP 发布产物，属于另一项设计 |

## 6. 主仓库改动摘要

- 删除 `TERMINAL/ HARNESS/ IDEA/ SITE/ TWIN/`。
- `deploy-profiles.json` 增 `externalModules`；生成器、生成物、契约测试同步。
- 新增 `.scripts/docker/external_modules.sh` 与 `.scripts/docker/external-modules.test.mjs`（用本地裸仓库验证克隆、跳过、归档、禁用拉取等分支）。
- 安装 / 构建入口各加一行调用；`verify_update_chain.sh` 对未检出的外部模块跳过。
- `.gitignore` 整体忽略五个目录并删去其内部细则；`.gitattributes`、`.dockerignore` 删去对应规则。
- `WEB/docker-compose.yaml` 注释、两个 PR 模板、`AGENTS.md`、6 份 README、`.scripts/docker/Readme.md` 同步。

## 7. 待决与风险

1. **仓库名与地址**：清单里写的是 `xinlingzhifei-sh/yfeieye-{harness,idea,terminal,twin,site}`，需用户在 GitHub 上按此建仓库（或改清单）。建库前，启用这些模块的安装会因克隆失败而报错（与拆分前"目录不存在"的报错行为一致），可先用 `YFEIEYE_<模块>_REPO=E:/yFeiEye-split/<模块>.git` 指向本地裸仓库。
2. **TERMINAL 自带上游 workflow**：`TERMINAL/.github/workflows/` 在主仓库里嵌在子目录、不会执行；成为独立仓库后位于根目录，**推到 GitHub 后会开始运行**（含发布 Homebrew / Scoop、部署文档）。推送前需用户决定保留、禁用或删除。
3. **品牌检查测试**：`visibleBrandingResidue.test.ts` 里对五个模块文件的断言已删除，没有搬进各模块仓库（各仓库目前没有测试框架）。如需保留，应在各仓库补 CI。
4. **`git clean -fdx`** 会删掉被忽略的外部检出目录（含本地改动）。文档已提示。
5. **IDEA 发布功能**：IDEA 的"发布本机栈"会在 `IDEA_HOST_PROJECT_ROOT` 下找各模块的 install 脚本；外部检出在原位置，不受影响。
6. **离线/内网部署**：无外网时需预先放好目录，或提供 `YFEIEYE_<模块>_ARCHIVE`。COMPILE 生成的离线包目前不包含这五个模块（拆分前也只带 TWIN 的部署文件；PANEL 安装包场景本就不部署它们）。
