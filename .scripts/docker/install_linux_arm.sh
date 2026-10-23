#!/bin/bash

# ============================================
# yFeiEye 统一安装脚本 (ARM架构版本)
# ============================================
# 使用方法：
#   ./install_linux_arm.sh              # 无参数：打开两层交互引导（部署 / 分析）
#   ./install_linux_arm.sh [命令]       # 直接执行命令（适合脚本/automation）
#
# 可用命令：
#   install    - 安装并启动所有服务（首次运行，交互选择部署形态）
#   start      - 启动所有服务
#   stop       - 停止所有服务
#   restart    - 重启所有服务
#   status     - 查看所有服务状态
#   logs       - 查看服务日志
#   build      - 重新构建所有镜像
#   clean      - 清理所有容器和镜像
#   clean-build-runtime [模块] - 清理 build-runtime 构建产物（先停业务服务，再删运行时镜像/构建缓存；保留跨架构基础镜像；不停中间件）
#   update     - 更新镜像并重启所有服务（交互可选拉取/本地重建）
#   verify     - 验证所有服务是否启动成功
#   check      - 检查 Docker 和 Docker Compose 安装状态
#   profile    - 显示当前部署形态与服务范围
#   menu       - 打开两层交互引导（同无参数）
#   diagnose       - 问题分析定位（进入【分析】子菜单）
#   analyze-logs   - 多模块日志合并分析（各模块约 500 行，带分割线）
#   analyze-disk   - 项目关键目录磁盘占用分析
#
# 部署形态（install 交互选型）：
#   0) edge / 1) mini / 2) standard / 3) full（默认）
#   选定 edge 后再选: 1) standalone | 2) integrated
#
# 实现：本脚本只保留平台差异（架构检测等），其余全部复用 install_linux.sh，
#       子命令与 install_linux.sh 完全一致（含 verify-alert / mobile 等），详见 ./install_linux_arm.sh help
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 平台契约见 install_linux.sh 顶部
INSTALL_ENTRY_NAME="install_linux_arm.sh"
INSTALL_PLATFORM_TAG=" (ARM架构)"
INSTALL_AI_VIDEO_SCRIPT="install_linux_arm.sh"
EASYAIOT_DOCKER_DNS_AUTO=0
EASYAIOT_INSTALL_LABEL="${EASYAIOT_INSTALL_LABEL:-yFeiEye 统一安装脚本 (ARM / aarch64)}"

# shellcheck source=install_linux.sh
source "${SCRIPT_DIR}/install_linux.sh"

# 检测服务器架构并验证是否为 ARM
detect_architecture() {
    print_info "检测服务器架构..."
    ARCH=$(uname -m)

    case "$ARCH" in
        aarch64|arm64)
            ARCH="aarch64"
            print_success "检测到 ARM 架构: $ARCH (aarch64/arm64)"
            ;;
        armv7l|armv6l)
            ARCH="armv7l"
            print_warning "检测到 ARM 架构: $ARCH (armv7l/armv6l)"
            print_warning "注意：armv7l/armv6l 架构可能不完全支持，建议使用 aarch64/arm64"
            ;;
        x86_64|amd64)
            print_error "检测到 x86_64 架构"
            print_error "本脚本专用于 ARM 架构部署"
            print_info "如需在 x86_64 架构上部署，请使用 install_linux.sh"
            exit 1
            ;;
        *)
            print_error "未识别的架构: $ARCH"
            print_error "本脚本仅支持 ARM 架构（aarch64/arm64/armv7l/armv6l）"
            exit 1
            ;;
    esac
}

install_platform_preflight() {
    detect_architecture
}

install_platform_help_notes() {
    echo "注意："
    echo "  - 本脚本专用于 ARM 架构（aarch64/arm64）"
    echo "  - AI 和 VIDEO 模块使用 install_linux_arm.sh（ARM 基础镜像），DEVICE/WEB 使用 install_linux.sh"
    echo "  - 支持与 x86 版本完全相同的部署形态: edge(0) / mini(1) / standard(2) / full(3)"
    echo "  - 如需在 x86_64 架构上部署，请使用 install_linux.sh"
    echo ""
}

install_linux_main "$@"
