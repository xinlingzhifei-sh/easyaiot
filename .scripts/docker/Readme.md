# 使用说明

## 0 一键部署入口

| 系统 | 脚本 |
|------|------|
| Linux x86（通用） | `./install_linux.sh` |
| **CentOS / RHEL / Rocky / Alma（x86）** | `./install_linux_centos.sh` |
| **CentOS / RHEL 系 · ARM** | `./install_linux_centos_arm.sh` |
| **麒麟(Kylin)** | `./install_linux_kylin.sh` |
| **欧拉(openEuler)** | `./install_linux_openeuler.sh` |
| Linux ARM（通用） | `./install_linux_arm.sh` |
| **macOS（仅预构建镜像）** | `./install_mac.sh` |
| **Windows（仅预构建镜像）** | `./install_windows.ps1` / `./install_windows.sh` |

```bash
# CentOS x86 推荐（自动安装/升级 Docker CE、配置镜像源与 firewalld）
sudo .scripts/docker/install_linux_centos.sh install

# CentOS ARM 推荐（同上环境准备，再转交 install_linux_arm.sh）
sudo .scripts/docker/install_linux_centos_arm.sh install

# openEuler 推荐（卸载自带 docker-engine、修复仓库 releasever、装 Docker CE）
sudo .scripts/docker/install_linux_openeuler.sh install

# 仅准备 Docker CE
sudo .scripts/docker/install_linux_centos.sh --upgrade-docker-only
sudo .scripts/docker/install_linux_centos_arm.sh --upgrade-docker-only
sudo .scripts/docker/install_linux_openeuler.sh --upgrade-docker-only
```

**RUNTIME C++ 离线包（iot-node 集群分发，与 COMPILE 矩阵对齐）**

```bash
# 分发前预检（缺包时提示 export 命令，不必 SSH 上去才发现）
bash .scripts/docker/install_linux.sh preflight-runtime-cpp --node 5
bash .scripts/docker/install_linux.sh preflight-runtime-cpp openeuler22 x86_64

# 单 OS / 批量矩阵构建（openEuler / CentOS / 麒麟入口均转交 install_linux.sh）
bash .scripts/docker/install_linux.sh build-runtime-cpp openeuler22
bash .scripts/docker/install_linux.sh build-runtime-cpp --compile-target openeuler
bash .scripts/docker/install_linux_openeuler.sh build-runtime-cpp openeuler22
bash .scripts/docker/install_linux_kylin.sh build-runtime-cpp kylin10   # 需 RUNTIME_KYLIN10_ARM64_IMAGE
```

一键安装顺序：中间件 → DEVICE/AI/RTC/VIDEO/WEB… → PANEL；HARNESS / IDEA / TERMINAL / TWIN 默认关闭，用 `EASYAIOT_ENABLE_<模块>=1` 显式开启。

外部模块：HARNESS、IDEA、TERMINAL、TWIN、SITE 已拆为独立仓库（`deploy-profiles.json` 的 `externalModules`）。
开启后执行 install / update / build / start 时，`external_modules.sh` 会把仓库检出到平台根目录下的同名目录：

```bash
EASYAIOT_ENABLE_HARNESS=1 bash .scripts/docker/install_linux.sh install HARNESS   # 自动 git clone
YFEIEYE_HARNESS_REPO=/srv/git/yfeieye-harness.git YFEIEYE_HARNESS_REF=v1.0.0 ...   # 改仓库 / 钉版本
YFEIEYE_SITE_ARCHIVE=/data/yfeieye-site-1.0.0.tar.gz bash .scripts/docker/install_linux.sh site install  # 离线 release 包
YFEIEYE_EXTERNAL_MODULES_FETCH=0 ...                                               # 禁止联网，只用已放好的目录
```

单独中间件（CentOS 7.9）：`start_postgresql_centos7.sh` / `start_rustfs_centos7.sh` / `start_nodered_centos7.sh` / `start_fuxa_centos7.sh`

## 1 建立基础环境目录
> mkdir -p db_data/{data,log} taos_data/{data,log} mq_data/{data,log} redis_data/{data,log} nacos_data/{data,log}

## 2 上传docker-compose.yml 文件

## 3 常见问题修复

### PostgreSQL 权限问题

**✅ 已自动修复**：从 2025-11-28 开始，PostgreSQL 容器会在启动时自动修复权限问题，无需手动干预。

如果仍然遇到 `could not open file "global/pg_filenode.map": Permission denied` 错误：

**快速修复**：
```bash
cd .scripts/docker
./fix_postgresql_permissions.sh
```

详细说明请参考：
- [POSTGRESQL_AUTO_FIX.md](./POSTGRESQL_AUTO_FIX.md) - 自动修复机制说明
- [POSTGRESQL_PERMISSION_FIX.md](./POSTGRESQL_PERMISSION_FIX.md) - 手动修复指南

### PostgreSQL 密码问题

如果遇到密码认证失败问题：

**快速修复**：
```bash
cd .scripts/docker
./fix_postgresql.sh
```

详细说明请参考：[POSTGRESQL_FIX.md](./POSTGRESQL_FIX.md)

