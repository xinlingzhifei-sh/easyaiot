# AGENTS.md — yFeiEye 项目指南（供 AI 编码代理阅读）

> 本文件面向对该项目一无所知的 AI 编码代理。请通读后再动手改代码。

## 1. 项目概览

yFeiEye 是一个**云-边-端一体化的 AIoT（AI + IoT）算法应用平台**（前身/同源项目名为 easyaiot，部分路径与标识仍沿用该名）。平台打通从设备接入、数据采集、实时视频分析、智能研判到告警处置的完整链路，同一套软件可部署为三种规格：

- **mini 边缘盒子**（≥4GB 内存，仅 iot-system + 少量服务）
- **standard AI 一体机**（≥16GB）
- **full 全栈一体机**（≥20GB，默认，含 APP 与全部中间件）

部署规格由 `EASYAIOT_DEPLOY_PROFILE=mini|standard|full` 控制，定义在 `.scripts/docker/deploy_profile.sh`。

**仓库本质：8 个相互独立、可单独抽取部署的项目组成的 monorepo**，三语混合编程——Java 做平台控制底座，Python 做 AI 与网络编程，C++ 做高性能计算任务。

### 八大模块（均为仓库根下的一级目录）

| 模块 | 技术栈 | 职责 |
|---|---|---|
| `WEB/` | Vue 3 + Vite 4 + TS 5 + ant-design-vue + Pinia（pnpm 11.3） | PC 管理控制台，dev 端口 8888 |
| `APP/` | uni-app（unibest 4.1 模板）+ Vue 3 + Vite 5 + Wot Design Uni（pnpm 10.10，Node ≥20） | 移动端 H5/小程序/App，dev 端口 9000 |
| `DEVICE/` | Java 21 + Spring Boot 2.7.18 + Spring Cloud 2021.0.5 + Nacos（Maven 多模块，groupId `com.basiclab.iot`，源于 ruoyi-vue-pro/yudao 体系） | 设备/产品/物模型管理、API 网关、GB28181、消息通知、数据汇流等微服务群 |
| `AI/` | Python + Flask 2.3 + PyTorch ≥2.9 + ultralytics/SAM/PaddleOCR | 模型训练/推理/部署服务（model-server，端口 5000） |
| `VIDEO/` | Python + Flask 2.3 + insightface/pymilvus/kafka-python | 摄像头接入、视频流、告警、录像回放、算法任务编排（端口 6000） |
| `TASK/` | C++17 + CMake + OpenCV/ONNX Runtime/FFmpeg | 单相机算法推理运行时（TASK 可执行文件）+ TaskManager HTTP 进程管理服务（端口 7000） |
| `NODE/` | Python 3（兼容 3.9）+ Flask | 边缘/远程节点 Agent（HTTP 9100），向 iot-node 控制面注册心跳、拉起工作负载 |
| `EDGE/` | Python 3 + paho-mqtt | 无界面边缘算法运行时（~512MB），全 MQTT 总线，告警写 Ceph，`python -m edge` CLI |

中间件依赖：Nacos、PostgreSQL（多库，如 `ruoyi-vue-pro20`、`iot-ai20`）、Redis、Kafka、MinIO、Milvus、EMQX、SRS、ZLMediaKit、TDengine（可选）、Node-RED。

### 领域语言（重要）

根目录 `CONTEXT.md` 定义了司法监管生理监测业务的领域术语表：`生理事件` ≠ `告警`（告警只是输入信号）、`可信 person_id` ≠ `身份候选`、`通知成功` ≠ `医务复测`、`人工替代闭环` ≠ `真实接口成功` 等。改动事件中心/告警/复测相关代码前**必读** `CONTEXT.md` 与 `docs/adr/`（如 `0001` 事件中心与 Alert 分离）。

## 2. 目录结构与文档

- `.doc/` — 项目文档：`架构设计/项目架构设计分析.md`（模块技术栈、微服务拆解、中间件拓扑、数据流的深度分析）、`部署文档/`（平台部署文档、部署最佳实践，多语言）、`开发规范/Git 操作规范指南.md`、`项目介绍/`
- `docs/adr/` — 轻量架构决策记录（英文，6 篇）
- `docs/superpowers/{specs,plans}/` — 本仓库的事实工作流：先写设计文档（spec）再写实施计划（plan），按 `YYYY-MM-DD-主题.md` 命名
- `.scripts/docker/` — 全部安装/诊断/修复脚本与 docker-compose；`.scripts/` 下还有 mqtt/modbus/opc-ua 协议联调 demo（各带 README）
- `.scripts/postgresql/` — 6 个数据库的初始化 SQL；`schema-sync/` 的差异 SQL **严禁入库**（`.gitignore` 明确）
- `deploy-packages/`、`output/`、`tmp/`、`.artifacts/` — 部署产物与临时输出，不要当源码改
- 根目录 `yfeieye-backend-*.tar.gz` — 历史后端打包产物

## 3. 构建与运行命令

### 整体部署（推荐入口）

```bash
sudo .scripts/docker/install_linux.sh           # 交互菜单（部署/分析两级）
sudo .scripts/docker/install_linux.sh install   # 非交互：安装 + verify 健康检查
.scripts/docker/install_linux.sh pull|build|update|status|logs|clean
```

其他平台：`.scripts/docker/install_linux_arm.sh` / `install_linux_kylin.sh` / `install_mac.sh` / `install_win.ps1`。分步部署：先 `.scripts/docker/install_middleware_linux.sh install`，再到各模块目录 `./install_linux.sh install`（仅业务模块可用 `install_business_linux.sh`）。

常用端口：WEB 8888、Gateway 48080、Nacos 8848、AI 5000、VIDEO 6000、APP 9010、MinIO 9000/9001、NODE Agent 9100。

### DEVICE（Java）

```bash
# 宿主机构建（需 JDK 21 + Maven 3.9+）
cd DEVICE && mvn install -DskipTests -Drevision=1.0.0
mvn install -pl iot-system -amd        # 选择性构建
mvn test                                # 跑测试（部署构建脚本均 -DskipTests）
# 官方两阶段 Docker 构建（增量、卷挂载编译）
cd DEVICE && ./install_linux.sh install|build|start|stop|logs <svc>|clean
```

13 个 Maven 模块：`iot-parent`（BOM）、`iot-gateway`（48080）、`iot-common`（19 个共享子模块：base/web/security/redis/mybatis/mq/tenant/test 等）、`iot-system`（48099，mini 规格唯一保留服务）、`iot-infra`、`iot-device`、`iot-dataset`、`iot-node`（节点控制面，挂载 `../NODE`、`../VIDEO` 源码做远程部署）、`iot-tdengine`、`iot-file`、`iot-message`、`iot-sink`（协议接入/数据汇流）、`iot-gb28181`。业务模块统一拆 `*-api`（Feign 接口/DTO）+ `*-biz`（实现）。Spring profile：`local/dev/prod`，另有 `mini/standard` 形态专用 yaml（mini 用 Simple Discovery + 静态 Feign URL 替代 Nacos）。

### WEB / APP（前端，均用 pnpm）

```bash
cd WEB
pnpm bootstrap          # 安装（Node ^18||>=20，pnpm 11.3）
pnpm dev                # vite，端口 8888
pnpm build              # 生产构建（另有 build:test / build:no-cache）
pnpm type:check         # vue-tsc 类型检查
pnpm lint / lint:fix / lint:stylelint

cd APP
pnpm i                  # 安装（Node >=20，pnpm 10.10，强制 pnpm）
pnpm dev                # H5 开发，http://localhost:9000（另有 dev:mp-weixin / dev:app 等）
pnpm build              # = build:h5（另有 build:mp-weixin / build:app）
pnpm type-check / lint / lint:fix
```

注意：`APP/src/manifest.json` 与 `APP/src/pages.json` 由 `manifest.config.ts` / `pages.config.ts` 自动生成（predev/prepare 钩子），**不要手改**。

### AI / VIDEO（Python）

```bash
cd AI（或 VIDEO）
pip install -r requirements.txt        # 本地直装；Docker 构建用 requirements-docker.txt，勿混用
python run.py                          # 本地开发（默认 .env）；python run.py --env=prod 加载 .env.prod
./install_linux.sh install|start|stop|logs|build   # Docker 部署（host 网络，源码卷挂载，git pull 后重启即生效）
```

requirements 分层：`requirements-base.txt`（AI/VIDEO 共用业务依赖）、`requirements.txt`（本地，含 torch）、`requirements-docker.txt`（镜像已含 torch）、`requirements-node-*.txt`（各计算节点离线 bundle，由 `export_node_pip_wheels.sh` 打包）。VIDEO 启动串：`prepare_database.py && apply_migrations.py --verify-only && enforce_private_media_buckets.py && run.py`；数据库迁移为 Flyway 风格 `migrations/V<版本>__<名称>.sql`（带 checksum 校验，强制单外层 BEGIN/COMMIT 事务）。

### TASK（C++）

```powershell
# Windows（需 vcpkg 装 opencv4/onnxruntime/ffmpeg/glog/jsoncpp/curl）
cd TASK
.\build.bat                       # cmake -G "Visual Studio 17 2022" + Release 构建 + 拷 DLL
.\TASK.exe ..\..\config\test.ini  # 运行
```

```bash
# Linux / Docker
cmake -S . -B build -DBUILD_TASK_MANAGER=ON -DCMAKE_BUILD_TYPE=Release
cmake --build build --target TaskManager
docker compose up                  # TaskManager HTTP 服务，端口 7000
```

CMake 选项：`BUILD_TASK_RUNTIME`（单相机推理运行时）、`BUILD_TASK_MANAGER`（HTTP 进程管理服务），默认均 ON。注意 `TASK/CMakeLists.txt` 中硬编码了原作者开发机路径（`G:/anaconda/...`、`F:/EASYLOT/vcpkg-master/...`），跨机构建需改。

### NODE / EDGE（边缘）

```bash
# NODE：目标机一键安装为 systemd 服务（/opt/easyaiot/node-agent）
cd NODE && ./install.sh install    # 另支持 update|start|stop|status|logs|clean
./export_pip_wheels.sh             # 控制面预下载离线 pip 依赖（AGENT_TARGET_PYTHON=3.9 可指定）
python3 run_agent.py               # 直接运行（需先在 agent.env 填 NODE_ID/AGENT_TOKEN）

# EDGE：现场唯一必配 EDGE_NODE_URL（iot-node 控制面地址）
cd EDGE
pip install -r requirements.txt
python -m edge config set-node http://<控制面>:48080
python -m edge enroll              # 登记并自动拉取 MQTT 凭据/Ceph 路径/Topic 契约
python -m edge run                 # 常驻，订阅 mqtt/iot-algo-task-cmd 任务指令
python -m edge status / pull-config / stop
```

## 4. 测试

**仓库没有任何 CI 配置**（无 `.github/`、`.gitlab-ci.yml`、Jenkinsfile），测试靠手动运行：

- **DEVICE**：JUnit 5 + spring-boot-starter-test + mockito-inline，基座模块 `iot-common-test`（H2 + jedis-mock），测试配置 `src/test/resources/application-unit-test.yaml`。命令：`cd DEVICE && mvn test`。
- **AI / VIDEO**：`AI/tests/`、`VIDEO/tests/` 为可自动化单测（pytest 与 unittest 混用），运行 `python -m pytest AI/tests/` 或 `python -m unittest discover VIDEO/tests`。**模块根目录散落的 `test_*.py` 多为需要真实模型/服务的联调脚本，不要批量当单测跑**。
- **WEB**：无 vitest。`WEB/tests/` 下 33 个 `*.test.ts` 是纯 Node 断言脚本，用 `pnpm exec tsx tests/xxx.test.ts` 逐个跑；另有脚本式契约检查 `pnpm test:alert-review-workbench` / `test:alert-review-playback`（`WEB/scripts/*.mjs`）。
- **APP**：无任何测试。
- **TASK**：`TASK/tests/test_taskmanager_api.py`（unittest，需先设 `TASK_MANAGER_BIN` 指向编译产物）；手工冒烟见 `TASK/TEST_GUIDE.md`。
- **NODE / EDGE**：无测试。
- **`.scripts/` 工具**：每个 `*.mjs` 配一个 `*.test.mjs`（node:test，`node --test`）。

## 5. 开发约定

- **Git 分支模型**（`.doc/开发规范/Git 操作规范指南.md`）：`master` 发布正式版；`dev` 日常集成；功能分支 `feature-{功能}-{姓名缩写}`（从 dev 拉、合回 dev）；`hotfix-{功能}-{缩写}` 从 master 拉，须同时合回 master 和 dev。
- **行尾与编码**（`.gitattributes` 强制）：`*.sh`、Dockerfile、`*.yaml`、`.env`、`*.py`、`*.ts`、`*.vue`、`*.js` 一律 **LF**；Windows 上编辑须用 **UTF-8 无 BOM**（CRLF/BOM 会破坏 shell 执行与 env 验签）。
- **C++ 源文件禁止中文字符与 emoji**（避免 MSVC C4819；CMake 已加 `/utf-8`）。
- **Lombok**（`DEVICE/lombok.config`）：`toString/equalsAndHashCode callSuper=CALL`、`accessors.chain=true`。
- **WEB lint**：`@antfu/eslint-config` + stylelint；commit 用 `pnpm commit`（cz-git，conventional commits，中文交互）；lint-staged 对 js/ts 跑 eslint --fix、vue 跑 eslint+stylelint、md/json 跑 prettier。
- **APP 代码风格**（`APP/.trae/rules/project_rules.md` 与 `.cursor/rules/`）：Composition API + `<script setup>`；组件文件 PascalCase；严格 TS 避免 `any`，对象类型用 `interface`、联合类型用 `type`，类型导入用 `import type`；状态用 Pinia `defineStore`；优先 UnoCSS 原子类；eslint 强制 vue block 顺序 `[script, template], style`。
- **Python 模块无 lint/format 配置**；注释与文档全中文。requirements 文件头注释即为使用规则（Docker 构建只用 `requirements-docker.txt`）。
- **EDGE 设计原则**（`EDGE/README.md`）：无界面（纯 CLI/systemd）、全 MQTT（无 Kafka、无 HTTP 管理面）、边缘零本地业务盘（告警图写 Ceph，由中心 iot-sink 归档 MinIO）、与 VIDEO 源码解耦（VIDEO 侧不得出现 `edge_node_*` 字段）。
- **文档/注释语言：简体中文为主**（README 与部署文档有英/繁/俄/法/韩多语言副本；改 README 时注意根目录 6 份多语言变体的同步工具 `.scripts/_sync_readme_updates.py`）。新代码注释沿用所在文件的既有语言风格。

## 6. 部署与发布流程

1. 选部署规格（mini/standard/full，存于 `.scripts/docker/.deploy_profile`）。
2. `.scripts/docker/install_middleware_linux.sh install` 起中间件 → 各模块目录 `./install_linux.sh install` 起业务服务。
3. DEVICE 构建有增量机制（源码/pom 哈希戳），`FORCE_REBUILD=1` 强制全量，`USE_MVND=1` 用 mvnd 加速。
4. 发版验证工具：`.scripts/verify-alert-review-release-package.mjs`、`record-export-manifest-verifier.mjs`；部署产物在 `deploy-packages/`。
5. 详见 `.doc/部署文档/平台部署文档.md` 与 `.doc/部署文档/部署最佳实践.md`。

## 7. 安全注意事项

- 数据库密码、GB28181 公网 SDP IP 等**明文硬编码**在各 `docker-compose.yml` / `application-*.yaml` 中（属现状，勿扩散；新增配置不要继续硬编码真实密钥）。
- `.env`、`agent.env` 等凭据文件严禁提交；Read/编辑工具默认也会拦截，写脚本时同样不要去 cat 它们。
- `NODE/agent_server.py` 的 HTTP 端点靠 `X-Agent-Token` 鉴权；改动时不要绕过或弱化该校验。
- 边缘节点 `agent.env` 中 `PLATFORM_AGENT=0` 不可改为 1（`agent.env.example` 明确）。
- 领域合规（`CONTEXT.md`）：心理/健康画像类输出必须带 `辅助不定性标识`，不得作为惩戒、医学诊断或监管定性依据；`低可信窗口` 不得创建/更新个人生理事件或个人基线。
