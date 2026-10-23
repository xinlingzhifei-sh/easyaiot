#!/bin/bash

# ============================================
# yFeiEye 统一安装脚本 (CentOS / RHEL 系 · ARM)
# ============================================
# 针对 CentOS 7/8/Stream、Rocky、Alma、RHEL 等 aarch64/arm64 的一键部署入口：
#   1) 检测发行版 / ARM 架构 / SELinux / firewalld
#   2) 自动安装或升级 Docker CE（解决 CentOS 7 自带 docker 1.13 无法拉新镜像）
#   3) 配置国内镜像源、放行常用业务端口
#   4) 转交 install_linux_arm.sh 完成 ARM 平台部署（命令与交互菜单完全一致）
#
# 使用方法：
#   sudo ./install_linux_centos_arm.sh              # 交互引导
#   sudo ./install_linux_centos_arm.sh install      # 首次安装
#   sudo ./install_linux_centos_arm.sh start|stop|restart|status|verify|update
#   ./install_linux_centos_arm.sh check|profile|help
#
# CentOS ARM 专用选项（须写在子命令之前）：
#   -f, --force              跳过 CentOS/RHEL 与 ARM 架构检查
#   --no-upgrade-docker      不自动安装/升级 Docker CE
#   --upgrade-docker-only    仅安装/升级 Docker CE 后退出
#   --no-firewall            不自动放行 firewalld 端口
#   --skip-mirror            跳过 Docker 国内镜像源配置
#
# 示例：
#   sudo ./install_linux_centos_arm.sh --upgrade-docker-only
#   sudo ./install_linux_centos_arm.sh --no-firewall install
#   sudo EASYAIOT_DEPLOY_PROFILE=full ./install_linux_centos_arm.sh install
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 入口配置，含义见 install_el_common.sh 头注释
EL_DELEGATE_TARGET="${SCRIPT_DIR}/install_linux_arm.sh"
EL_INSTALL_LABEL="yFeiEye 统一安装脚本 (CentOS / RHEL · ARM)"
EL_INSTALL_SCRIPT=".scripts/docker/install_linux_centos_arm.sh"
EL_HELP_FN=show_centos_arm_help
EL_CHECK_FN=check_centos_arm
EL_OLD_DOCKER_HINT="CentOS 7 常见为 1.13），无法拉取 postgres:18 等新镜像"
EL_ARCH_LABEL=" · ARM"
EL_PKG_ARCH_NOTE=" (aarch64)"
EL_INSTALL_FAIL_HINT="请确认当前仓库提供 aarch64 包，或手动安装后加 --no-upgrade-docker"
EL_READY_MESSAGE="Docker CE 准备完成 (CentOS ARM)"

# shellcheck source=install_el_common.sh
source "${SCRIPT_DIR}/install_el_common.sh"

show_centos_arm_help() {
    cat <<'EOF'
yFeiEye 统一安装脚本 (CentOS / RHEL 系 · ARM)

用法:
  sudo ./install_linux_centos_arm.sh [CentOS ARM选项...] [命令] [参数...]

CentOS ARM 专用选项（须写在子命令之前）:
  -h, --help              显示此帮助
  -f, --force             跳过 CentOS/RHEL 与 ARM 架构检查
  --no-upgrade-docker     不自动安装/升级 Docker CE
  --upgrade-docker-only   仅安装/升级 Docker CE 后退出
  --no-firewall           不自动放行 firewalld 端口
  --skip-mirror           跳过 Docker 国内镜像源配置

子命令（与 install_linux_arm.sh 完全一致）:
  install / start / stop / restart / status / logs / build
  build-runtime / pull / clean / clean-build-runtime / update
  verify / check / profile / menu / diagnose / analyze-logs / analyze-disk / help

示例:
  sudo ./install_linux_centos_arm.sh
  sudo ./install_linux_centos_arm.sh install
  sudo ./install_linux_centos_arm.sh --upgrade-docker-only
  sudo EASYAIOT_DEPLOY_PROFILE=edge ./install_linux_centos_arm.sh install
  sudo EASYAIOT_DEPLOY_PROFILE=mini ./install_linux_centos_arm.sh install

说明:
  - 仅面向 aarch64/arm64 上的 CentOS/RHEL 系发行版
  - CentOS 7 会自动卸载系统自带 docker 1.13，并安装 docker-ce 20+
  - CentOS 8+ / Rocky / Alma / RHEL 使用 yum 或 dnf 安装 docker-ce
  - 平台业务部署逻辑委托给 install_linux_arm.sh（ARM 镜像与 Dockerfile）
  - x86_64 CentOS 请使用: install_linux_centos.sh
  - 通用 ARM（非 EL 系）请使用: install_linux_arm.sh
EOF
}

is_arm_arch() {
    local arch
    arch=$(uname -m)
    case "$arch" in
        aarch64|arm64|armv7l|armv6l|armhf|armel)
            return 0
            ;;
    esac
    return 1
}

check_centos_arm() {
    detect_el_family

    if [ "$FORCE_OS_CHECK" = true ]; then
        print_warning "已跳过 CentOS/RHEL 与 ARM 架构检查 (--force)"
        return 0
    fi

    print_section "系统环境检查 (CentOS / RHEL · ARM)"

    if ! is_el_family; then
        print_error "当前系统不是 CentOS/RHEL 系 (ID=${OS_ID:-未知})"
        print_info "通用 ARM Linux 请使用: sudo .scripts/docker/install_linux_arm.sh"
        print_info "x86 CentOS/RHEL 请使用: sudo .scripts/docker/install_linux_centos.sh"
        print_info "或加 --force 强制继续: sudo ./install_linux_centos_arm.sh --force $*"
        exit 1
    fi

    print_success "检测到: ${OS_PRETTY:-$OS_ID $OS_VERSION}"

    local arch
    arch=$(uname -m)
    case "$arch" in
        aarch64|arm64)
            print_success "架构: $arch (ARM64)"
            ;;
        armv7l|armv6l|armhf|armel)
            print_warning "架构: $arch（32 位 ARM，可能不完全支持，建议 aarch64）"
            ;;
        x86_64|amd64)
            print_error "当前为 x86_64 架构，本脚本仅用于 CentOS ARM"
            print_info "请改用: sudo .scripts/docker/install_linux_centos.sh"
            exit 1
            ;;
        *)
            print_error "未识别或不支持的架构: $arch（需要 aarch64/arm64）"
            print_info "或加 --force 强制继续"
            exit 1
            ;;
    esac

    el_report_selinux_firewalld
}

el_main "$@"
