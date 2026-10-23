#!/bin/bash

# ============================================
# yFeiEye 统一安装脚本 (CentOS / RHEL 系)
# ============================================
# 针对 CentOS 7/8/Stream、Rocky、Alma、RHEL 的一键部署入口：
#   1) 检测发行版 / SELinux / firewalld
#   2) 自动安装或升级 Docker CE（解决 CentOS 7 自带 docker 1.13 无法拉新镜像）
#   3) 配置国内镜像源、放行常用业务端口
#   4) 转交 install_linux.sh 完成平台部署（命令与交互菜单完全一致）
#
# 使用方法：
#   sudo ./install_linux_centos.sh              # 交互引导
#   sudo ./install_linux_centos.sh install      # 首次安装
#   sudo ./install_linux_centos.sh start|stop|restart|status|verify|update
#   ./install_linux_centos.sh check|profile|help
#
# CentOS 专用选项（须写在子命令之前）：
#   -f, --force              跳过 CentOS/RHEL 发行版检查
#   --no-upgrade-docker      不自动安装/升级 Docker CE
#   --upgrade-docker-only    仅安装/升级 Docker CE 后退出
#   --no-firewall            不自动放行 firewalld 端口
#   --skip-mirror            跳过 Docker 国内镜像源配置
#
# 示例：
#   sudo ./install_linux_centos.sh --upgrade-docker-only
#   sudo ./install_linux_centos.sh --no-firewall install
#   sudo EASYAIOT_DEPLOY_PROFILE=full ./install_linux_centos.sh install
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# 入口配置，含义见 install_el_common.sh 头注释
EL_DELEGATE_TARGET="${SCRIPT_DIR}/install_linux.sh"
EL_INSTALL_LABEL="yFeiEye 统一安装脚本 (CentOS / RHEL)"
EL_INSTALL_SCRIPT=".scripts/docker/install_linux_centos.sh"
EL_HELP_FN=show_centos_help
EL_CHECK_FN=check_centos_family
EL_OLD_DOCKER_HINT="CentOS 7 常见为 1.13），无法拉取 postgres:18 等新镜像"

# shellcheck source=install_el_common.sh
source "${SCRIPT_DIR}/install_el_common.sh"

show_centos_help() {
    cat <<'EOF'
yFeiEye 统一安装脚本 (CentOS / RHEL 系)

用法:
  sudo ./install_linux_centos.sh [CentOS选项...] [命令] [参数...]

CentOS 专用选项（须写在子命令之前）:
  -h, --help              显示此帮助
  -f, --force             跳过 CentOS/RHEL 发行版检查
  --no-upgrade-docker     不自动安装/升级 Docker CE
  --upgrade-docker-only   仅安装/升级 Docker CE 后退出
  --no-firewall           不自动放行 firewalld 端口
  --skip-mirror           跳过 Docker 国内镜像源配置

子命令（与 install_linux.sh 完全一致）:
  install / start / stop / restart / status / logs / build
  build-runtime / pull / clean / clean-build-runtime / update
  verify / check / profile / menu / diagnose / analyze-logs / analyze-disk / help

示例:
  sudo ./install_linux_centos.sh
  sudo ./install_linux_centos.sh install
  sudo ./install_linux_centos.sh --upgrade-docker-only
  sudo EASYAIOT_DEPLOY_PROFILE=edge ./install_linux_centos.sh install
  sudo EASYAIOT_DEPLOY_PROFILE=mini ./install_linux_centos.sh install

说明:
  - CentOS 7 会自动卸载系统自带 docker 1.13，并安装 docker-ce 20+
  - CentOS 8+ / Rocky / Alma / RHEL 使用 yum 或 dnf 安装 docker-ce
  - 平台业务部署逻辑委托给 install_linux.sh，避免重复维护
  - CentOS 7 上控制面 Agent 将自动使用 ensure_platform_agent_centos7.sh
EOF
}

check_centos_family() {
    detect_el_family

    if [ "$FORCE_OS_CHECK" = true ]; then
        print_warning "已跳过 CentOS/RHEL 发行版检查 (--force)"
        return 0
    fi

    print_section "系统环境检查 (CentOS / RHEL)"

    if ! is_el_family; then
        print_error "当前系统不是 CentOS/RHEL 系 (ID=${OS_ID:-未知})"
        print_info "通用 Linux 请使用: sudo .scripts/docker/install_linux.sh"
        print_info "或加 --force 强制继续: sudo ./install_linux_centos.sh --force $*"
        exit 1
    fi

    print_success "检测到: ${OS_PRETTY:-$OS_ID $OS_VERSION}"

    local arch
    arch=$(uname -m)
    case "$arch" in
        x86_64|amd64)
            print_success "架构: $arch"
            ;;
        aarch64|arm64)
            print_warning "当前为 ARM 架构 ($arch)"
            print_info "CentOS ARM 推荐使用: sudo .scripts/docker/install_linux_centos_arm.sh"
            print_info "（自动装 Docker CE 后转交 install_linux_arm.sh；麒麟可用 install_linux_kylin.sh）"
            print_info "本脚本仍可继续（将转交 install_linux.sh）"
            ;;
        *)
            print_warning "未识别架构: $arch，继续尝试部署"
            ;;
    esac

    el_report_selinux_firewalld
}

# CentOS 7 优先走兼容脚本（bash 4.2 / 旧 python）
el_delegate_exports() {
    if [ "${OS_MAJOR:-}" = "7" ]; then
        export EASYAIOT_PLATFORM_AGENT_SCRIPT="${EASYAIOT_PLATFORM_AGENT_SCRIPT:-.scripts/node/ensure_platform_agent_centos7.sh}"
    fi
}

el_main "$@"
