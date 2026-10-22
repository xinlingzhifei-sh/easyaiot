# 部署脚本去重实施计划

**Goal:** 按 `docs/superpowers/specs/2026-10-22-deploy-scripts-dedup.md` 去掉 `.scripts/docker/` 平台安装入口的重复代码，锁定浮动镜像标签，并补公共函数测试。

**Architecture:** `install_linux.sh` 原地作为 Linux 实现并暴露平台钩子；arm / kylin 入口 `source` 它后只覆盖钩子。EL 系与 CentOS 7 启动器各抽一份公共库。模块元数据并入 `deploy-profiles.json`，经生成器产出查询函数。

**Tech Stack:** Bash、Node.js `node:test`、JSON 生成器。

每个阶段一个提交，提交前：`bash -n` 改动脚本 + 相关 `node --test` + `node .scripts/verify-repository-secret-hygiene.mjs`。全量测试在测试服务器上跑（临时目录 `/opt/yfeieye-unittest-deploy-dedup/`，跑完删除）。

---

### Task 1：设计与计划
- [x] 写 spec、plan，提交。

### Task 2：模块元数据进 manifest
- [x] `deploy-profiles.json` 新增 `moduleRuntime`（label / port / healthPath，取 `install_linux.sh` 现值）。
- [x] `generate-deploy-profiles.mjs` 产出 `deploy_profile_manifest_module_label|port|health_path`；`--check` 覆盖。
- [x] `deploy_profile.sh` 新增 `deploy_profile_load_module_tables`。
- [x] `install_linux.sh`、`install_business_linux.sh` 删除硬编码表，改调 `deploy_profile_load_module_tables`。
- [x] 测试：`deploy-profile-shell.test.mjs` 增加元数据断言；manifest 测试断言安装脚本不再硬编码端口表。

### Task 3：Linux 平台入口薄封装
- [x] `docker_mirror_common.sh`：`_should_configure_docker_dns` 支持 `EASYAIOT_DOCKER_DNS_AUTO=0`；`docker_pull_with_mirror_fallback` 支持 digest 引用。
- [x] `install_linux.sh`：顶部平台契约（直接运行时强制 amd64 默认）；删内联 `configure_docker_mirror` / `restart_docker_if_active`，改 `source docker_mirror_common.sh`；各命令入口调用 `install_platform_preflight`；`install_linux()` 用 `install_platform_prepare_docker`；文案/日志名/帮助用 `INSTALL_ENTRY_NAME` 与 `INSTALL_PLATFORM_TAG`；`module_install_script` 用 `INSTALL_AI_VIDEO_SCRIPT`；末尾改为 `install_linux_main`，仅直接运行时执行。
- [x] `install_linux_arm.sh`、`install_linux_kylin.sh` 改写为薄封装（头注释 + 平台变量 + `detect_architecture` 等钩子 + `install_linux_main "$@"`）。
- [x] 更新引用 arm / kylin 源码文本的旧断言（`deploy-profile-manifest.test.mjs`）。
- [x] 新增 `install-linux-platform.test.mjs`、`docker-mirror-common.test.mjs`。

### Task 4：EL 系公共库
- [x] 新增 `install_el_common.sh`；`install_linux_centos.sh`、`install_linux_centos_arm.sh`、`install_linux_openeuler.sh` 改为配置 + 平台函数 + `el_main "$@"`。
- [x] 新增 `install-el-common.test.mjs`（参数解析、版本判定、firewalld 调用、升级决策、转交参数）。

### Task 5：CentOS 7 单服务启动器公共库
- [x] 新增 `start_centos7_common.sh`；五个 `start_*_centos7.sh` 删除公共函数。
- [x] 新增 `start-centos7-common.test.mjs`。

### Task 6：镜像锁定
- [x] `docker-compose.yml`：ZLMediaKit digest、Node-RED 5.0.9（附查询方法注释）。
- [x] `export_media_images.sh` 按 digest 拉取、`start_nodered_centos7.sh` 默认值同步（`NodeMediaServiceImpl.java` 依赖本地 `:master` 名，不改，见 spec 3.5）。
- [x] 新增 `image-pinning.test.mjs`。

### Task 7：验证与报告
- [x] 本地 `bash -n`；服务器全量 `node --test`；secret hygiene。
- [x] 统计删除行数，在共享笔记目录留交接说明，最终报告。
