# AGENTS.md — yFeiEye 项目指南（供 AI 编码代理阅读）

> 本文件面向对该项目一无所知的 AI 编码代理。请通读后再动手改代码。
> 最近一次按仓库实况核对：2026-10-20。判断一个目录是否仍然有效，以 `git ls-files <目录>` 是否有输出为准——磁盘上可能残留被 `.gitignore` 忽略的旧目录，它们不是源码。

## 1. 项目概览

yFeiEye 是一个**云-边-端一体化的 AIoT（AI + IoT）算法应用平台**（前身/同源项目名为 easyaiot，部分路径、镜像名、环境变量前缀 `EASYAIOT_*` 仍沿用该名）。平台打通从设备接入、数据采集、实时视频分析、智能研判到告警处置的完整链路。

**仓库本质：多个相互独立、可单独抽取部署的项目组成的 monorepo**。仓库根下共 20 个受版本控制的一级目录（不含点目录）：17 个模块目录 + `docs/`、`testdata/`、`tools/`；另有 5 个周边模块已拆为独立仓库，按需检出（见下文「外部模块」）。主力语言为 Java（平台控制底座）、Python（AI、视频编排、节点 Agent）、C++（高性能帧执行器）、TypeScript/Vue（前端），另有 Go（POST，以及 RTC 集成的 go2rtc）与 C#（EDGE 采集器）。

> 历史名称对照：原节点 Agent 模块已迁到 `SENTINEL/`，原 C++ 推理模块已演进为 `RUNTIME/`。旧文档、旧计划里出现的 NODE / TASK 目录名一律按此对照理解，不要在任何同名残留目录里改代码。

### 部署规格（单一事实源：`.scripts/docker/deploy-profiles.json`）

由 `EASYAIOT_DEPLOY_PROFILE=edge|mini|standard|full`（默认 `full`）选择；每个规格包含哪些模块、中间件、DEVICE 微服务和 WEB 能力，全部定义在 `deploy-profiles.json` 中，`generate-deploy-profiles.mjs` 据此生成 `deploy-profiles.generated.sh`（供 `deploy_profile.sh` 读取）和 `WEB/src/generated/deploymentProfileManifest.ts`。**改规格只改 JSON，然后重新生成，不要手改生成物。**

| 规格 | 推荐内存 | 业务模块 | DEVICE 微服务 |
|---|---|---|---|
| `edge` | ≥2 GB | VIDEO、WEB | 无 |
| `mini` | ≥4 GB | DEVICE、AI、VIDEO、WEB | iot-gateway、iot-system、iot-infra、iot-sink |
| `standard` | ≥16 GB | DEVICE、AI、RTC、POST、VIDEO、WEB | 除 iot-device / iot-visualize / iot-tdengine 外的 10 个 |
| `full`（默认） | ≥20 GB | 以上全部 + APP、VISUALIZE、TRANSFORM | 全部 13 个 |

`edge` 规格还有两种子形态，由 `EASYAIOT_EDGE_MORPHOLOGY` 选择（`.scripts/docker/edge_deploy_common.sh`）：

- `standalone`（纯边缘，默认）：汇聚面（VIDEO + WEB）与边缘算力同机，本地存储，不部署 EMQX，告警走 HTTP。
- `integrated`（云边一体）：本机只装 RUNTIME 算力节点，接入远端中心的 VIDEO / Gateway / EMQX；安装流程委托给 `RUNTIME/install_linux.sh integrated`，需提供 `VIDEO_BASE_URL`。

### 模块一：核心平台模块（出现在 `deploy-profiles.json` 的规格模块列表中）

| 模块 | 技术栈 | 职责 | 端口 | 规格 |
|---|---|---|---|---|
| `DEVICE/` | Java 21 + Spring Boot 2.7.18 + Spring Cloud 2021.0.5 + Nacos（Maven 多模块，groupId `com.basiclab.iot`，源于 ruoyi-vue-pro/yudao 体系） | 设备/产品/物模型、API 网关、节点控制面、GB28181、消息通知、数据汇流等微服务群 | Gateway 48080 | mini / standard / full |
| `AI/` | Python + Flask 2.3 + PyTorch ≥2.9 + ultralytics/SAM/PaddleOCR | 模型训练/推理/导出/部署服务（model-server） | 5000 | mini / standard / full |
| `VIDEO/` | Python + Flask 2.3 + insightface/pymilvus/kafka-python | 摄像头接入、视频流、告警、录像回放、算法任务编排；拉起 RUNTIME 执行器 | 6000 | 全部四档 |
| `WEB/` | Vue 3 + Vite 4 + TS 5 + ant-design-vue + Pinia（pnpm 11.3） | PC 管理控制台（容器内 nginx 同时托管 SITE 静态站） | 8888（SITE 8090） | 全部四档 |
| `RTC/` | go2rtc（Go，源码由 `RTC/install_linux.sh vendor` 拉取，不入库）+ Python Flask 管理服务 | 消费级摄像头 P2P/私有协议桥接（Tapo、Tuya、Ring、小米等） | 6100（go2rtc 1984/8554/8555） | standard / full |
| `POST/` | Go | 推理事件的定制后处理服务 | 8089 | standard / full |
| `APP/` | uni-app（unibest）+ Vue 3 + Vite 5 + Wot Design Uni（pnpm 10.10，Node ≥20） | 移动端 H5/小程序/App | dev 9000 / 部署 9010 | full |
| `VISUALIZE/` | Vue 3 + Vite 4（pnpm 11.3） | 拖拽式低代码可视化大屏编辑器；后台为 DEVICE `iot-visualize` | 8002 | full |
| `TRANSFORM/` | Java 21 + Spring Boot（Maven 多模块：`transform-core`、`transform-runtime`、consume/map/deliver 能力与通道插件） | 系统对接 / 数据转发 | 48096 | full |

### 模块二：边缘执行组件（不在规格模块列表中，由安装流程或控制面拉起）

| 模块 | 技术栈 | 职责 | 端口 | 如何部署 |
|---|---|---|---|---|
| `RUNTIME/` | C++17 + CMake + OpenCV 5 / ONNX Runtime / FFmpeg / glog / jsoncpp / libcurl | C++ 帧执行器（由原 TASK 演进）：拉流→解码→推理→MQTT 告警 + HTTP 心跳，`realtime` 默认推 `ai/{device}` 带框流；**不替代 VIDEO**。另含兼容用 TaskManager HTTP 服务 | 任务控制口 8000–9000（示例 8123）；TaskManager `127.0.0.1:7000` | VIDEO 安装时经 `VIDEO/scripts/ensure_runtime_cpp.sh` 编译挂载；`edge/integrated` 独立安装；WEB「业务运行时分发」经 iot-node SSH 批量分发到 `/opt/easyaiot/RUNTIME` |
| `SENTINEL/` | Python 3（兼容 3.9）+ Flask | 集群节点观察者 Agent：按 `NODE_FUNCTIONS` 探测组件、经网关向 iot-node 上报心跳与可调度能力、接收部署指令启动工作负载、缺组件时请求自愈 | 9100（`agent_server.py`） | 节点纳管后由 iot-node SSH 同步并装为 systemd 服务（`/opt/easyaiot/sentinel-agent`） |
| `EDGE/` | Python（`python -m edge` 算法运行时）+ C# .NET 8（`EasyAIoT.Edge.sln`：Modbus / OPC UA 采集器） | **保留还是废弃待决策**：`EDGE/README.md` 自称"第八核心模块"，而 `RUNTIME/README.md` 写"原 EDGE 模块已移除，边缘算力请用云边一体"；DEVICE iot-node 的 EDGE 登记接口、WEB 的 EDGE 接入引导、`COMPILE/pack_edge.sh` 仍在引用它 | 纯 MQTT，无 HTTP 管理面 | 不在任何部署规格中；手工安装 |

在 EDGE 的去留明确之前：新的边缘算力需求优先按 RUNTIME 云边一体形态实现；不要删除 EDGE，也不要把新功能只做在 EDGE 里。

### 模块三：周边 / 实验性模块

| 模块 | 技术栈 | 职责 | 端口 | 启用方式 |
|---|---|---|---|---|
| `PANEL/` | Python 后端 + Vue UI（`PANEL/ui`） | 独立于 WEB 的运维控制台（容器、部署、诊断，参考 1Panel） | 9200 | `EASYAIOT_ENABLE_PANEL` |
| `COMPILE/` | Shell + Docker + PyInstaller | 把 PANEL（及 EDGE 等）打成 deb/rpm/exe/dmg 安装包 | — | 手工或 GitHub 手动打包 workflow |
| `ANDROID/` / `IOS/` / `HARMONYOS/` | Gradle / Xcode / DevEco 壳工程 | 把 APP 的构建产物打成 APK / IPA / HAP，不含业务代码 | — | 各目录 `make-apk.sh` / `make-ipa.sh` / `make-hap.sh` |

PANEL 与下列外部模块 HARNESS、IDEA、TERMINAL、TWIN 虽列在 `deploy-profiles.json` 的 `moduleOrder` 中，但不属于任何规格，只能用对应的 `EASYAIOT_ENABLE_*` 环境变量显式开启。

### 外部模块（已拆为独立仓库，2026-10-22）

以下模块不再在本仓库中，仓库地址与 ref 记录在 `deploy-profiles.json` 的 `externalModules`（设计见 `docs/superpowers/specs/2026-10-22-split-peripheral-modules.md`）：

| 模块 | 职责 | 端口 | 启用方式 |
|---|---|---|---|
| `HARNESS` | 平台 AI Agent 助手（Sidecar，DeepSeek Harness） | 3080（LLM 网关 3082） | `EASYAIOT_ENABLE_HARNESS` |
| `IDEA` | 社区贡献在线 IDE（code-server + 门户） | 门户 9300，工作区 13338–13437 | `EASYAIOT_ENABLE_IDEA` |
| `TERMINAL` | 多协议终端与远程运维客户端（Go + Wails，基于 uniTerm） | 9245 | `EASYAIOT_ENABLE_TERMINAL` |
| `TWIN` | 工业园区数字孪生 MVP（Three.js） | 8003 | `EASYAIOT_ENABLE_TWIN` |
| `SITE` | 官方网站（纯前端） | 8090 | `.scripts/docker/install_linux.sh site install`，或产物挂进 WEB 容器 |

- 安装 / 构建入口（`install_linux*.sh`、`install_business_linux.sh`、`install_desktop_common.sh`、`install_mac.sh`、`runtime_image.sh`）执行 `install|update|build|start|restart` 前调用 `ensure_external_module_checkout`（`.scripts/docker/external_modules.sh`），目录缺失时把外部仓库检出到 `<平台根>/<模块>`。模块脚本仍引用 `../.scripts/docker/`（SITE 构建还引用 `../WEB/conf`），所以必须检出在平台根目录下。
- 覆盖：`YFEIEYE_<模块>_REPO` / `YFEIEYE_<模块>_REF`；离线用 `YFEIEYE_<模块>_ARCHIVE`（release `.tar.gz`）或手工放目录；`YFEIEYE_EXTERNAL_MODULES_FETCH=0` 禁止联网。
- 这些目录被本仓库 `.gitignore` 忽略，是各自独立的 git 仓库：**不要在本仓库里给它们提交代码**，`git clean -fdx` 也会删掉它们。
- WEB 中调用这些服务的前端（`HarnessFloatChat`、`IdeaFloatBall`、`views/harness`、`views/idea`、`utils/terminal.ts`）只经 HTTP 访问上述端口，仍留在 WEB。

### 中间件

`deploy-profiles.json` 的 `middlewareServiceOrder` 为准：Nacos、PostgreSQL（含 init）、TDengine（含 init，仅 full）、Redis、Kafka、对象存储、Milvus、SRS、Node-RED、FUXA、EMQX、ZLMediaKit、GPUStack（`EASYAIOT_ENABLE_GPUSTACK` 显式开启）。

- **对象存储**：README 与部署文档已改为 **RustFS**（S3 兼容，见 `.scripts/docker/RUSTFS_MIGRATION.md`），应用仍通过 MinIO SDK 与 `MINIO_*` 环境变量访问；但中间件 compose 的服务名/容器名仍是 `MinIO` / `minio-server`，镜像仍为 `minio/minio`。文档里写"RustFS/MinIO"即指这一个 S3 服务。
- **PostgreSQL 多库**：如 `ruoyi-vue-pro20`、`iot-ai20`、`iot-node20` 等；初始化 SQL 在 `.scripts/postgresql/`（`*10.sql`，含 iot-ai / iot-device / iot-flow / iot-gb28181 / iot-message / iot-node / iot-transform / iot-video / iot-visualize / ruoyi-vue-pro）。

### 领域语言（重要）

根目录 `CONTEXT.md` 定义了司法监管生理监测业务的领域术语表：`生理事件` ≠ `告警`（告警只是输入信号）、`可信 person_id` ≠ `身份候选`、`通知成功` ≠ `医务复测`、`人工替代闭环` ≠ `真实接口成功` 等。改动事件中心/告警/复测相关代码前**必读** `CONTEXT.md` 与 `docs/adr/`（如 `0001` 事件中心与 Alert 分离）。

## 2. 目录结构与文档

- `.doc/` — 项目文档：`架构设计/整体架构详解.md`（运行时拓扑与数据流）、`架构设计/项目架构设计分析.md`（技术栈与完成度评估）、`部署文档/`（平台部署文档、部署最佳实践，多语言）、`操作手册/`、`开发规范/Git 操作规范指南.md`、`项目介绍/`
- `docs/adr/` — 轻量架构决策记录（英文，6 篇）；`docs/prd/`、`docs/requirements/`、`docs/specs/`、`docs/security/`、`docs/code-review/` 等为需求与评审材料
- `docs/superpowers/{specs,plans}/` — 本仓库的事实工作流：先写设计文档（spec）再写实施计划（plan），按 `YYYY-MM-DD-主题.md` 命名
- `.scripts/docker/` — 全部安装/诊断/修复脚本、中间件 docker-compose、部署规格清单（`deploy-profiles.json`）
- `.scripts/` 其他子目录 — mqtt / modbus / opc-ua / industrial / flow 等协议联调 demo（各带 README），以及告警复核、发版校验、密钥卫生检查等 `*.mjs` 工具
- `.scripts/postgresql/` — 数据库初始化 SQL 与备份脚本；`schema-sync/` 下的备份与差异 SQL **严禁入库**（`.gitignore` 明确）
- `testdata/contracts/` — 推理契约测试数据；`tools/bp_v6/` — 文档/PPT 生成脚本
- `.github/workflows/` — `pr-tests.yml`（PR/push 自动测试）与 `compile-packaging.yml`（手动打包），见第 4 节
- `.gitignore` 中列出的构建产物、部署包与临时输出目录不受版本控制，不要当源码改

## 3. 构建与运行命令

### 整体部署（推荐入口）

```bash
sudo .scripts/docker/install_linux.sh           # 交互菜单（选规格 → edge 时再选 standalone / integrated）
sudo .scripts/docker/install_linux.sh install   # 非交互：安装 + verify 健康检查
.scripts/docker/install_linux.sh start|stop|restart|status|logs|pull|build|build-runtime|update|verify|check|profile|clean
.scripts/docker/install_linux.sh install POST   # 只装单个模块
EASYAIOT_DEPLOY_PROFILE=edge EASYAIOT_EDGE_MORPHOLOGY=integrated VIDEO_BASE_URL=http://<中心>:6000 \
  .scripts/docker/install_linux.sh install      # 云边一体（runtime-integrated 为兼容别名）
```

其他平台入口（均在 `.scripts/docker/`）：`install_linux_arm.sh`、`install_linux_kylin.sh`、`install_linux_centos.sh`、`install_linux_centos_arm.sh`、`install_linux_openeuler.sh`、`install_mac.sh`、`install_windows.ps1`（另有 `.cmd` / `.sh` 包装）。分步部署：先 `install_middleware_linux.sh install` 起中间件，再 `install_business_linux.sh` 或各模块目录 `./install_linux.sh install`。

常用端口：WEB 8888、Gateway 48080、Nacos 8848、AI 5000、VIDEO 6000、RTC 6100、POST 8089、TRANSFORM 48096、VISUALIZE 8002、APP 9010、RustFS/MinIO 9000/9001（仅绑 127.0.0.1）、SENTINEL Agent 9100。

### 部署规格清单

```bash
node .scripts/docker/generate-deploy-profiles.mjs           # 改完 deploy-profiles.json 后重新生成 Shell 与 WEB 清单
node .scripts/docker/generate-deploy-profiles.mjs --check   # 只校验生成物是否与 JSON 一致
node --test .scripts/docker/deploy-profile-manifest.test.mjs .scripts/docker/deploy-profile-shell.test.mjs
```

### DEVICE（Java）

```bash
# 宿主机构建（需 JDK 21 + Maven 3.9+）
cd DEVICE && mvn install -DskipTests -Drevision=1.0.0
mvn install -pl iot-system -amd        # 选择性构建
mvn test                                # 跑测试（部署构建脚本均 -DskipTests）
# 官方两阶段 Docker 构建（增量、卷挂载编译）
cd DEVICE && ./install_linux.sh install|build|start|stop|logs <svc>|clean
```

15 个 Maven 模块：`iot-parent`（BOM）、`iot-common`（共享子模块：base/web/security/redis/mybatis/mq/tenant/test 等）和 13 个微服务——`iot-gateway`（48080）、`iot-system`（48099）、`iot-infra`、`iot-device`（48083）、`iot-dataset`（48087）、`iot-node`（48094，节点控制面：管理 SENTINEL Agent、分发 RUNTIME、EDGE 登记）、`iot-visualize`（48095）、`iot-flow`（48097）、`iot-tdengine`、`iot-file`、`iot-message`（48093）、`iot-sink`（48092，协议接入/数据汇流，订阅 MQTT 算法总线落库告警）、`iot-gb28181`（48088）。另有 `iot-supervision`（司法监管：事件中心、处置任务、告警复核工作台），是普通 jar，由 `iot-system-biz` 引入并在同一进程运行，不单独部署（见 `docs/superpowers/specs/2026-10-22-supervision-module-split.md`）。各规格启用哪些微服务见 `deploy-profiles.json` 的 `deviceServices`。业务模块统一拆 `*-api`（Feign 接口/DTO）+ `*-biz`（实现）。Spring profile：`local/dev/prod`，另有 `application-mini.yaml`（iot-system、iot-sink）与 `application-standard.yaml`；mini 形态同样使用 Nacos 服务发现。

### WEB / APP / VISUALIZE（前端，均用 pnpm）

```bash
cd WEB
pnpm bootstrap          # = pnpm install（Node ^18||>=20，pnpm 11.3）
pnpm dev                # vite，端口 8888
pnpm build              # 生产构建（另有 build:test / build:static / build:no-cache）
pnpm type:check         # vue-tsc 类型检查
pnpm lint / lint:fix / lint:stylelint

cd APP
pnpm i                  # 安装（Node >=20，pnpm 10.10，强制 pnpm）
pnpm dev                # = dev:h5，http://localhost:9000（另有 dev:mp-weixin / dev:app 等）
pnpm build              # = build:h5（另有 build:mp-weixin / build:app）
pnpm type-check / lint / lint:fix

cd VISUALIZE
pnpm install && pnpm run dev   # http://localhost:8002，/admin-api 代理到 Gateway 48080
```

注意：`APP/src/manifest.json` 与 `APP/src/pages.json` 由 `manifest.config.ts` / `pages.config.ts` 自动生成（predev/prepare 钩子），**不要手改**。

### AI / VIDEO（Python）

```bash
cd AI（或 VIDEO）
pip install -r requirements.txt        # 本地直装；Docker 构建用 requirements-docker.txt，勿混用
python run.py                          # 本地开发（默认 .env）；python run.py --env=prod 加载 .env.prod
./install_linux.sh install|start|stop|logs|build   # Docker 部署（host 网络，源码卷挂载，git pull 后重启即生效）
```

requirements 分层：`requirements-base.txt`（AI/VIDEO 共用业务依赖）、`requirements.txt`（本地，含 torch）、`requirements-docker.txt`（镜像已含 torch）、`requirements-node-*.txt`（各计算节点离线 bundle）。VIDEO 启动串：`prepare_database.py && apply_migrations.py --verify-only && enforce_private_media_buckets.py && run.py`；数据库迁移为 Flyway 风格 `migrations/V<版本>__<名称>.sql`（带 checksum 校验，强制单外层 BEGIN/COMMIT 事务）。

### RTC / POST / TRANSFORM

```bash
bash RTC/install_linux.sh vendor        # 拉取 go2rtc 源码（不入库）
bash RTC/install_linux.sh install|start|status|logs   # 管理 API :6100；另有 dev（本地开发）

cd POST && cp -n env.example .env && go test ./... && go run ./cmd/post
bash POST/install_linux.sh install      # 需 EASYAIOT_DEPLOY_PROFILE=standard|full

bash TRANSFORM/install_linux.sh install|start|stop|status|logs|build   # 仅 full 形态启用；联调见 TRANSFORM/docs/操作手册.md
```

### RUNTIME（C++，仅 Linux 一键流程）

```bash
./RUNTIME/install_linux.sh                         # TTY 下交互菜单
./RUNTIME/install_linux.sh build                   # 编译，默认 Docker 同源容器（EASYAIOT_RUNTIME_BUILD_MODE=host 改用本机 conda）
./RUNTIME/install_linux.sh compile|status          # 本机 conda 编译 / 查看产物
./RUNTIME/install_linux.sh integrated http://<VIDEO>:6000   # 云边一体算力节点（atomic 为兼容别名）
bash RUNTIME/build_runtime_matrix.sh               # 多 OS 离线包矩阵（产物 RUNTIME/.bundle-runtime/）
source RUNTIME/scripts/env.sh && $RUNTIME_BIN RUNTIME/config/config.example.ini   # 源码树手工跑
```

产物：`RUNTIME/build/RUNTIME`、`RUNTIME/deploy.env`（供 VIDEO 写 compose 挂载）。生产中算法任务 `executor=cpp` 时由 VIDEO 生成 `config/task_{id}.ini` 并拉起本二进制。CMake 选项 `BUILD_TASK_MANAGER`（默认 ON）额外构建兼容用 TaskManager HTTP 服务；`RUNTIME/docker-compose.yml` 以 `127.0.0.1:7000` 运行它，必须设置 `TASK_MANAGER_TOKEN`。依赖与 GPU/硬解策略详见 `RUNTIME/README.md`。

### SENTINEL / EDGE（边缘）

```bash
# SENTINEL：通常由 iot-node 在节点纳管后自动 SSH 安装；手工安装：
cd SENTINEL
cp agent.env.example agent.env     # 填 NODE_ID / AGENT_TOKEN / CONTROL_PLANE_URL（http://<gateway>:48080/admin-api/node/agent）
./install.sh install               # systemd 服务 easyaiot-sentinel-agent，目录 /opt/easyaiot/sentinel-agent；另支持 update|start|stop|status|logs|clean
./export_pip_wheels.sh             # 控制面预下载离线 pip 依赖（AGENT_TARGET_PYTHON 指定目标 Python）
python3 run_sentinel.py            # 开发态直接运行（run_agent.py 为兼容入口）

# EDGE（去留待决策，见第 1 节）：Python 运行时
cd EDGE && pip install -r requirements.txt
python -m edge config set-node http://<iot-node 控制面>:48080
python -m edge config set-join-token <与控制面 easyaiot.edge.join-token 一致>
python -m edge enroll              # 登记并自动拉取 MQTT 凭据/Ceph 路径/Topic 契约
python -m edge run                 # 常驻，订阅 mqtt/iot-algo-task-cmd 任务指令
# EDGE C# 采集器：EDGE/EasyAIoT.Edge.sln（.NET 8）；打包 EDGE/pack_linux.sh（COMPILE/pack_edge.sh 转调）
```

## 4. 测试与 CI

**CI 现状**：根目录有两个 GitHub workflow。

- `.github/workflows/pr-tests.yml`：在 `pull_request` 及推送 `main`/`dev` 时触发，按改动路径只跑对应模块的 job——DEVICE `mvn test`、VIDEO/AI `pytest tests`、WEB `pnpm type:check` + `tests/*.test.ts`、`.scripts` 下 `node --test`（各模块 `install_linux*.sh` 与 `WEB/conf/nginx.conf` 改动也会触发）、SENTINEL `unittest`（Python 3.9 与 3.11）、POST `go vet` + `go test`。目前不跳过任何已知失败测试；确需临时跳过时在对应步骤显式列出并注明原因。
- `.github/workflows/compile-packaging.yml`：仅 `workflow_dispatch` 手动触发，用于调用 `COMPILE/build.sh` 打 Ubuntu deb、Windows exe（可选 NSIS 安装器、macOS dmg、CentOS el7/8/9 与 ARM、openEuler rpm）。APP、RUNTIME 等模块尚未纳入 `pr-tests.yml`；外部模块的测试与 CI 在各自仓库。各模块本地测试方式：

- **DEVICE**：JUnit 5 + spring-boot-starter-test + mockito-inline，基座模块 `iot-common-test`（H2 + jedis-mock），测试配置 `src/test/resources/application-unit-test.yaml`。命令：`cd DEVICE && mvn test`。
- **AI / VIDEO**：`AI/tests/`、`VIDEO/tests/` 为可自动化单测（pytest 与 unittest 混用），运行 `python -m pytest AI/tests/` 或 `python -m unittest discover VIDEO/tests`。**模块根目录散落的 `test_*.py` 多为需要真实模型/服务的联调脚本，不要批量当单测跑**。
- **WEB**：无 vitest。`WEB/tests/` 下约 50 个 `*.test.ts` 是纯 Node 断言脚本，用 `pnpm exec tsx tests/xxx.test.ts` 逐个跑；另有脚本式契约检查 `pnpm test:alert-review-workbench`、`test:alert-review-playback`、`test:region-editor`、`test:region-hit-mode`、`test:algorithm-task-card-layout`（`WEB/scripts/*.mjs`）。
- **APP**：无任何测试。
- **RUNTIME**：`RUNTIME/tests/test_taskmanager_api.py`（unittest，需先设 `TASK_MANAGER_BIN` 指向编译产物）与 `test_container_packaging.py`；手工冒烟见 `RUNTIME/TEST_GUIDE.md`。
- **SENTINEL**：`SENTINEL/tests/`（unittest，测试直接 `import agent_commands` 等顶层模块），在 `SENTINEL/` 目录下运行 `python -m unittest discover -s tests -t .`。
- **EDGE**：仅 `EDGE/tests/test_edge_security.py`。
- **POST**：Go 单测，`go test ./...`。
- **`.scripts/` 工具**：每个 `*.mjs` 配一个 `*.test.mjs`（node:test，`node --test`），`.scripts/docker/` 下的部署契约测试同理。

## 5. 开发约定

- **Git 分支模型**（`.doc/开发规范/Git 操作规范指南.md`）：`master` 发布正式版；`dev` 日常集成；功能分支 `feature-{功能}-{姓名缩写}`（从 dev 拉、合回 dev）；`hotfix-{功能}-{缩写}` 从 master 拉，须同时合回 master 和 dev。
- **行尾与编码**（`.gitattributes` 强制）：`*.sh`、Dockerfile、`*.yaml`、`.env`、`*.py`、`*.ts`、`*.vue`、`*.js` 一律 **LF**；Windows 上编辑须用 **UTF-8 无 BOM**（CRLF/BOM 会破坏 shell 执行与 env 验签）。
- **C++ 源文件（RUNTIME）禁止中文字符与 emoji**（避免 MSVC C4819）。
- **Lombok**（`DEVICE/lombok.config`）：`toString/equalsAndHashCode callSuper=CALL`、`accessors.chain=true`。
- **WEB lint**：`@antfu/eslint-config` + stylelint；commit 用 `pnpm commit`（cz-git，conventional commits，中文交互）；lint-staged 对 js/ts 跑 eslint --fix、vue 跑 eslint+stylelint、md/json 跑 prettier。
- **APP 代码风格**（`APP/.trae/rules/project_rules.md` 与 `APP/.cursor/rules/`）：Composition API + `<script setup>`；组件文件 PascalCase；严格 TS 避免 `any`，对象类型用 `interface`、联合类型用 `type`，类型导入用 `import type`；状态用 Pinia `defineStore`；优先 UnoCSS 原子类；eslint 强制 vue block 顺序 `[script, template], style`。
- **Python 模块无 lint/format 配置**；注释与文档全中文。requirements 文件头注释即为使用规则（Docker 构建只用 `requirements-docker.txt`）。
- **部署规格**：新增/裁剪模块、中间件或 WEB 菜单能力时，改 `deploy-profiles.json` 并重新生成，不要在各安装脚本里另写一份模块清单或网络名常量。
- **RUNTIME 与 VIDEO 的分工**：VIDEO 负责编排、预览、任务生命周期与 HTTP 心跳接收；RUNTIME 只做帧执行。原画预览走 SRS `live/{device_id}`，AI 带框流走 `ai/{device_id}`，两路互不占用。
- **文档/注释语言：简体中文为主**（README 与部署文档有英/繁/俄/法/韩多语言副本；改 README 时注意根目录 6 份多语言变体的同步工具 `.scripts/_sync_readme_updates.py`）。新代码注释沿用所在文件的既有语言风格。

## 6. 部署与发布流程

1. 选部署规格：`edge`（再选 `standalone` / `integrated`）、`mini`、`standard`、`full`。选择结果持久化在 `.scripts/docker/.deploy_profile`（已被 `.gitignore` 忽略），并同步到各业务模块配置。
2. `.scripts/docker/install_middleware_linux.sh install` 起中间件 → `install_business_linux.sh` 或各模块目录 `./install_linux.sh install` 起业务服务；`edge/integrated` 直接走 `RUNTIME/install_linux.sh integrated`。
3. DEVICE 构建有增量机制（源码/pom 哈希戳），`FORCE_REBUILD=1` 强制全量，`USE_MVND=1` 用 mvnd 加速。
4. 发版验证工具：`.scripts/verify-alert-review-release-package.mjs`、`.scripts/record-export-manifest-verifier.mjs`、`.scripts/verify-repository-secret-hygiene.mjs`。PANEL 等桌面/系统安装包由 `COMPILE/` 生成（也可用 GitHub 手动 workflow）。
5. 详见 `.doc/部署文档/平台部署文档.md` 与 `.doc/部署文档/部署最佳实践.md`。

## 7. 安全注意事项

- **凭据配置现状**：各模块 compose 一律 `${VAR:?...}` 必填 / `${VAR:-}` 读取密码与令牌，Spring yaml 用无默认值的 `${VAR}`（缺失即启动失败），源码与脚本不带默认口令。中间件凭据的单一来源是 `.scripts/docker/.env.docker`（可用 `YFEIEYE_MIDDLEWARE_ENV_FILE` 覆盖）：AI / VIDEO / POST / TRANSFORM 安装脚本经 `deploy_profile.sh` 的 `read_middleware_secret` / `build_middleware_postgres_url` 从中读取并写入各自 `.env.docker`，读不到即报错。模块 `.env.docker` 不入库，只跟踪 `env.example` / `.env.docker.example` 占位模板。新增配置一律走环境变量，提交前用 `.scripts/verify-repository-secret-hygiene.mjs` 自查（会检查已知历史口令、`${SECRET:-字面量}`、连接串内嵌口令、YAML 口令字面量、env 模板非占位值；确属上游出厂口令或演示种子数据时，在该行或上一行注明 `secret-hygiene: allow`）。
- `.env`、`agent.env`、`edge.env` 等凭据文件严禁提交；Read/编辑工具默认也会拦截，写脚本时同样不要去 cat 它们。
- `SENTINEL/agent_server.py` 的 HTTP 端点靠 `X-Agent-Token` 鉴权；改动时不要绕过或弱化该校验。
- 远程节点 `agent.env` 中 `PLATFORM_AGENT=0` 不可改为 1（`SENTINEL/agent.env.example` 明确；`PLATFORM_AGENT_BOOTSTRAP_TOKEN` 仅控制面宿主机使用）。
- RUNTIME TaskManager 只监听 `127.0.0.1:7000` 且强制 `TASK_MANAGER_TOKEN`；EDGE 登记依赖 `easyaiot.edge.join-token`，`easyaiot.edge.allow-open-enroll` 生产必须为 `false`。
- 领域合规（`CONTEXT.md`）：心理/健康画像类输出必须带 `辅助不定性标识`，不得作为惩戒、医学诊断或监管定性依据；`低可信窗口` 不得创建/更新个人生理事件或个人基线。

## 8. 已知的文档与配置不一致（待修，勿据此改代码）

- EDGE 与 RUNTIME 的关系未定（见第 1 节）。
- 对象存储：README、部署文档与 `.scripts/docker/RUSTFS_MIGRATION.md` 写 RustFS，但中间件 compose 仍是 `minio/minio` 镜像、服务名 `MinIO`。容器内访问对象存储一律用服务名 `MinIO`（DEVICE compose、VIDEO/AI 的 env 模板与桌面端安装脚本已按此对齐；上游换成 RustFS 镜像时也保留了 `MinIO` 网络别名），不要写 `RustFS` 主机名。
