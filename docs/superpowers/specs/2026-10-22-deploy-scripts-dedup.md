# 部署脚本去重设计（`.scripts/docker/`）

> 状态：实施中（分支 `claude/deploy-scripts-dedup-aece42`）
> 关联计划：`docs/superpowers/plans/2026-10-22-deploy-scripts-dedup.md`

## 1. 背景与现状

`.scripts/docker/` 下各平台安装入口大量复制粘贴，按函数逐个比对（md5）的结果：

| 脚本族 | 文件 | 现状 |
|---|---|---|
| Linux 平台入口 | `install_linux.sh`（2533 行）、`install_linux_arm.sh`（1903）、`install_linux_kylin.sh`（1932） | arm / kylin 是 `install_linux.sh` 的**陈旧分叉**：86 个函数里 40 个逐字相同，其余多为文案漂移；`install_linux.sh` 此后陆续修过的问题（`/dev/null` 预检、gateway 就绪等待、Created 容器修复、`docker info` 超时、MQTT/工业演示、`verify-alert` 等子命令）在 arm / kylin 上都没有 |
| EL 系入口 | `install_linux_centos.sh`（558）、`install_linux_centos_arm.sh`（578）、`install_linux_openeuler.sh`（698） | 打印、参数解析、`pkg_mgr`、Docker 版本检测、firewalld 放行、转交逻辑三份重复；centos 与 centos_arm 仅差文案 |
| CentOS 7 单服务启动器 | `start_{minio,rustfs,nodered,fuxa,postgresql}_centos7.sh`（635–813） | 打印、Docker 版本检测与升级、compose 探测、网络、端口检查五份重复，各份只有提示文案不同 |
| 模块元数据 | `install_linux*.sh`、`install_business_linux.sh` | `MODULE_NAMES` / `MODULE_PORTS` / `MODULE_HEALTH_ENDPOINTS` 四份硬编码清单，与 `deploy-profiles.json` 的 `moduleOrder` 平行维护 |
| 镜像版本 | `docker-compose.yml` 等 | `zlmediakit/zlmediakit:master`、`nodered/node-red:latest` 为浮动标签 |

真正的平台差异很少：

- **ARM**：`detect_architecture`（非 ARM 直接退出）；AI / VIDEO 用模块内 `install_linux_arm.sh`；标题带「(ARM架构)」；日志文件名前缀。
- **麒麟**：同 ARM，另加麒麟系统识别、`install` 时先修宿主机 DNS（`ensure_host_dns_for_docker`）与安装 docker-buildx、Docker 镜像源配置改用 `docker_mirror_common.sh`（带 DNS）。
- **EL 系**：发行版识别、Docker CE 安装源（openEuler 需要修 `$releasever`、替换自带 docker-engine）、CentOS 7 平台 Agent 兼容脚本。

## 2. 目标与非目标

目标：

1. 平台入口只保留平台差异，重复逻辑进公共库。
2. 复用现有公共件：`deploy_profile.sh`、`docker_mirror_common.sh`、`docker_restart_guard.sh`、`host_firewall_common.sh`；模块元数据并入 `deploy-profiles.json`。
3. 浮动镜像标签锁定到具体版本或 digest。
4. 为公共函数补 `node:test` 契约/单元测试（shell 函数替身，不调用真实 docker / systemctl / yum）。

非目标（本轮不动）：

- **macOS / Windows 桌面端**：`install_mac.sh` + `install_middleware_mac.sh` 与 `install_desktop_common.sh` + `install_middleware_desktop.sh` 是两套并行实现，属于「收敛多套实现」会话的范围，这里只记录不改。
- **`install_middleware_linux.sh`（7480 行）**：被 6 个测试与发版校验器按文本断言引用，且自带一份更完整的 `configure_docker_mirror`；本轮不拆，避免与发版校验契约冲突。
- AI / VIDEO 等模块目录下的 `install_linux_{arm,kylin}.sh`（不在 `.scripts/docker/`）。

## 3. 方案

### 3.1 Linux 平台入口：`install_linux.sh` 作为实现，arm / kylin 变成薄封装

候选方案：

- A. 把 `install_linux.sh` 的全部函数搬进新文件 `install_linux_common.sh`，三个入口都变薄。
- B. **`install_linux.sh` 原地保留为实现**，增加少量平台钩子；arm / kylin 设好平台变量后 `source` 它，覆盖钩子，再调 `install_linux_main`。

选 B。理由：其它并行会话（拆周边模块、Boot 3 等）也在改 `install_linux.sh`，整文件搬家会让它们的改动全部冲突；B 对 `install_linux.sh` 只是局部插入，且现有测试按文本断言 `install_linux.sh` 的内容，B 不破坏这些断言。

平台契约（`install_linux.sh` 顶部集中定义，直接运行时强制取 amd64 默认值，避免环境变量串味）：

| 变量 / 钩子 | amd64 默认 | ARM | 麒麟 |
|---|---|---|---|
| `INSTALL_ENTRY_NAME` | `install_linux.sh` | `install_linux_arm.sh` | `install_linux_kylin.sh` |
| `INSTALL_PLATFORM_TAG` | 空 | ` (ARM架构)` | ` (麒麟系统)` |
| `INSTALL_AI_VIDEO_SCRIPT` | `install_linux.sh` | `install_linux_arm.sh` | `install_linux_kylin.sh` |
| `EASYAIOT_DOCKER_DNS_AUTO` | `0` | `0` | `1` |
| `install_platform_preflight` | 空操作 | `detect_architecture` | `detect_architecture`（含麒麟识别） |
| `install_platform_prepare_docker` | `configure_docker_mirror` | 同左 | DNS 修复 → 镜像源 → buildx |
| `install_platform_describe` | 空操作 | 空操作 | 打印「系统类型: 麒麟系统」 |
| `install_platform_help_notes` | 空操作 | ARM 注意事项 | 麒麟注意事项 |

`INSTALL_ENTRY_NAME` 同时决定日志文件名前缀、`EASYAIOT_INSTALL_SCRIPT` 默认值、帮助与提示里的脚本名。arm / kylin 原先在 `start` / `restart` / `build` / `pull` / `build-runtime` / `update` / `check` 前调用 `detect_architecture`，现在这些位置统一调 `install_platform_preflight`。

`install_linux.sh` 内联的 `configure_docker_mirror` / `restart_docker_if_active`（70 行，与 arm 逐字相同）删除，改为 `source docker_mirror_common.sh`。为不改变 amd64/ARM 的行为，给 `docker_mirror_common.sh` 的 `_should_configure_docker_dns` 加开关 `EASYAIOT_DOCKER_DNS_AUTO`（默认 1，保持其它调用方原样）；`install_linux.sh` 置 0，即**不自动**往 daemon.json 写 DNS；麒麟入口置 1，与原麒麟脚本一致。

### 3.2 模块元数据并入 `deploy-profiles.json`

新增顶层键 `moduleRuntime`：`{ "<模块>": { "label", "port", "healthPath" } }`，生成器产出 `deploy_profile_manifest_module_label|port|health_path <模块>`，并新增 `deploy_profile_load_module_tables`（在 `deploy_profile.sh`）把它们灌进 `MODULE_NAMES` / `MODULE_PORTS` / `MODULE_HEALTH_ENDPOINTS` 关联数组。`install_linux.sh`、`install_business_linux.sh` 删掉硬编码表。标签沿用 `install_linux.sh` 写法（`install_failed` 诊断分支按标签匹配，见 `install_linux()`）。

### 3.3 EL 系：`install_el_common.sh`

公共部分：打印、专用选项解析（`-f/--force`、`--no-upgrade-docker`、`--upgrade-docker-only`、`--no-firewall`、`--skip-mirror`、`--`）、`detect_el_family` / `is_el_family`、`pkg_mgr`、`get_docker_server_version`、`is_docker_too_old`、`install_docker_ce_el`、`ensure_modern_docker`、`configure_docker_mirror_centos`、`configure_firewalld_ports`、`el_delegate`、`el_main`。

入口提供：`EL_HELP_FN`、`EL_CHECK_FN`、`EL_DELEGATE_TARGET`、`EL_INSTALL_LABEL`、`EL_INSTALL_SCRIPT`、`EL_ARCH_LABEL`、`EL_OLD_DOCKER_HINT`、`EL_INSTALL_DOCKER_FN`、`EL_MIRROR_FN`，以及可选钩子 `el_before_version_check`（openEuler 用来识别自带 docker-engine）与 `el_delegate_exports`（CentOS 7 平台 Agent、openEuler 的 `DOCKER_DNS`）。

### 3.4 CentOS 7 单服务启动器：`start_centos7_common.sh`

公共部分：打印、`resolve_compose_cmd`、`check_docker`、`get_docker_server_version`、`is_docker_too_old`、`upgrade_docker_ce_centos7`、`ensure_modern_docker`、`check_compose_file`、`ensure_network`、`check_port_available`、`_pull_from_registry`、`configure_docker_mirror`（minio / rustfs / postgresql 共用版本）、`configure_docker_mirror_local` + `ensure_docker_mirror`（nodered / fuxa 共用版本）。

入口提供：`C7_SERVICE_LABEL`（提示里的服务名）、`C7_PULL_IMAGE_VARS`（拉取成功后要打的本地标签，如 `NODERED_IMAGE NODERED_IMAGE_ALIAS`）、`C7_FIREWALL_HINT_PORTS`。各函数取信息最全的那份文案（多为 postgresql 版）。

### 3.5 镜像锁定

| 镜像 | 原引用 | 锁定为 | 依据 |
|---|---|---|---|
| ZLMediaKit | `zlmediakit/zlmediakit:master` | `zlmediakit/zlmediakit@sha256:ddc5127d4fab7be622b34148ac8869ca6310298acd33440f1236e771a3c5ceee` | 上游 Docker Hub 只有 `master` / `feature-*` 分支标签、没有发布标签，只能锁 digest；该 digest 是 2026-10-12 的 `master` 多架构索引（amd64 + arm64），也是测试服务器上已部署镜像的 RepoDigest |
| Node-RED | `nodered/node-red:latest` | `nodered/node-red:5.0.9` | 2026-10-22 时 `latest` 即 5.0.9（Node 24），测试服务器上的镜像同为 v5.0.9；锁到当天值，新装行为不变 |

涉及位置：`.scripts/docker/docker-compose.yml`、`start_nodered_centos7.sh` 默认值、`.scripts/media-cluster/export_media_images.sh`。媒体节点离线流程（`export_media_images.sh` → `docker save/load` → `install_media_stack.sh`，以及 iot-node `NodeMediaServiceImpl` 的 `docker image inspect zlmediakit/zlmediakit:master` 检查）依赖本地标签名，而 `docker save` 不保留 digest 引用，因此这一路保留本地名 `:master`，只把导出时的拉取改为按同一 digest 拉取后打本地标签；`NodeMediaServiceImpl.java` 与 `docker-compose.media-node.yml` 不改。

国内源可用性：digest 拉取走 registry-mirrors 与 `docker.m.daocloud.io/<repo>@sha256:` 直连均可用（DaoCloud 按 digest 代理）。`docker_pull_with_mirror_fallback` 的「直连后 `docker tag` 回原名」对 digest 引用不成立（不能给 digest 打标签），因此对含 `@sha256:` 的引用改为：直连拉取成功即视为成功，并提示 compose 将按 digest 复用本地镜像。

列出但**不锁**的浮动引用（理由）：

- `postgres:18`、`ossrs/srs:5`：主版本标签，次版本内兼容；`postgres:18` 被 CentOS 7 启动器、部署文档多处按字面引用，本轮不动。
- `minio/aistor/mc:latest`（`install_middleware_desktop.sh`、`migrate_minio_to_rustfs.sh`、`upload_rustfs_data.sh`、`verify_rustfs_s3.sh`）：工具镜像不是中间件；且该名字只在 quay.io 存在、Docker Hub 上不存在，默认值本身就拉不到，需要单独修，记为后续事项。
- `alpine:latest`（`install_linux.sh` 容器创建探针）、`hello-world:latest`（`runtime_image_common.sh` 推送探针）：一次性探针，不进入运行面。
- `docker.cnb.cool/.../aiot-*:latest`：自家预构建业务镜像，版本由 `EASYAIOT_RUNTIME_TAG` 控制，不属于中间件。

## 4. 行为变化清单

按「是否用户可感知」分级：

**功能性（arm / kylin 入口获得 `install_linux.sh` 已有的修复与能力）**

1. 新增子命令：`verify-alert`、`verify-dvr`、`nfs|ceph`、`mobile`、`build-runtime-cpp` / `preflight-runtime-cpp` / `verify-runtime-cpp` / `export-runtime-cpp`，以及 `runtime-atomic` 等兼容别名。
2. `install` / `start` / `restart` / `update`：编排前跑 `/dev/null` 与容器创建实测（`_verify_dev_null`）；清理形态外容器（`cleanup_profile_excluded_containers`）；DEVICE 后等待 iot-gateway 就绪；按形态启用 MQTT / 工业演示（默认关闭，由 `deploy-profiles.json` 的 feature 开关控制）；`stop` 前停工业演示。
3. `wait_for_base_services`：先修复 Created 状态容器，PostgreSQL 等待时间由 60s 提到 180s 并增加查询就绪检查。
4. `verify` 通过后默认继续告警事件面与 DVR/NFS 验收（`EASYAIOT_VERIFY_ALERT_ON_VERIFY=0` / `EASYAIOT_VERIFY_DVR_ON_VERIFY=0` 可关）。
5. `clean-build-runtime [模块]`（ARM 原先不支持按模块清理；麒麟已支持）。
6. `install` 跳过没有安装脚本的模块，`update` 先同步宿主机脚本（`easyaiot_update_sync_project_scripts`）。
7. `EASYAIOT_INSTALL_SCRIPT` 改为「未设置时才取默认值」（原 arm / kylin 无条件覆盖）。

**配置写入**

8. `install_linux.sh` / ARM 改用 `docker_mirror_common.sh` 写 daemon.json：`DOCKER_MIRROR` 环境变量从此生效（原值硬编码）；显式 `EASYAIOT_FORCE_DOCKER_DNS=1` 时会写 DNS（原 amd64 版忽略该变量）。未设这两个变量时写入结果与原来相同。

**仅文案 / 外观**

9. arm / kylin 的部分提示文案、诊断建议改为 `install_linux.sh` 的写法；`check_docker` / `check_docker_compose` 输出更简短。
10. `install_business_linux.sh` 的模块显示名改为 `install_linux.sh` 写法（如「AI 服务」→「AI服务」）。
11. EL 系与 CentOS 7 启动器：各份文案统一为信息最全的版本。

**无变化**：所有入口的命令行参数、子命令（除上面新增）、环境变量、交互菜单结构；EL 系专用选项；CentOS 7 启动器选项。

## 5. 风险与取舍（需要主会话确认）

1. **arm / kylin 获得新流程**（第 4 节 1–7）。这是「薄封装」的必然结果，也是把陈旧分叉补齐；但无法在真实 ARM / 麒麟机器上做整体部署验证（按要求只跑单测）。如果主会话倾向零行为变化，可只合入 3.2–3.5 与 EL / CentOS 7 部分，arm / kylin 单独再议。
2. **Node-RED 5.x**：锁到 5.0.9 等于确认「今天的 latest」；若后续发现种子流程与 5.x 不兼容，可改锁 4.1.17（最后一个 4.x）。
3. **ZLMediaKit digest**：上游 `master` 是滚动构建，锁定后不再自动获得修复，升级需手工改 digest（`docker-compose.yml` 注释里写明了查询方法）。
4. 修改了其它会话可能涉及的文件：`deploy-profiles.json` + 生成物（新增 `moduleRuntime` 键，不动已有键）、`docker_mirror_common.sh`（新增开关，默认值保持原行为）。

## 6. 验证

- `bash -n` 覆盖所有改动脚本。
- `.scripts` 下全部 `node --test`（`find .scripts -name '*.test.mjs'`）在测试服务器临时容器内跑。
- 新增测试：`install-linux-platform.test.mjs`（三个入口的平台契约，在临时目录副本里 source，桩掉 docker / uname）、`install-el-common.test.mjs`、`start-centos7-common.test.mjs`、`docker-mirror-common.test.mjs`（DNS 开关、digest 回退）、`image-pinning.test.mjs`（compose 与媒体栈不再出现浮动标签）。
