#!/bin/bash

# ============================================
# yFeiEye 统一安装脚本 (麒麟系统版本)
# ============================================
# 使用方法：
#   ./install_linux_kylin.sh              # 无参数：打开两层交互引导（部署 / 分析）
#   ./install_linux_kylin.sh [命令]       # 直接执行命令（适合脚本/automation）
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
#   clean-build-runtime [模块] - 清理 build-runtime 构建产物（先停业务服务，再删运行时镜像/构建缓存；保留跨架构基础镜像；不停中间件；指定模块时仅清理该模块镜像与其构建缓存）
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
#       子命令与 install_linux.sh 完全一致（含 verify-alert / mobile 等），详见 ./install_linux_kylin.sh help
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 平台契约见 install_linux.sh 顶部
INSTALL_ENTRY_NAME="install_linux_kylin.sh"
INSTALL_PLATFORM_TAG=" (麒麟系统)"
INSTALL_AI_VIDEO_SCRIPT="install_linux_kylin.sh"
# 麒麟常因 ::1/127.0.0.53 DNS 导致拉镜像失败：按需往 daemon.json 写公网 DNS
EASYAIOT_DOCKER_DNS_AUTO=1
EASYAIOT_INSTALL_LABEL="${EASYAIOT_INSTALL_LABEL:-yFeiEye 统一安装脚本 (麒麟 ARM)}"

# shellcheck source=install_linux.sh
source "${SCRIPT_DIR}/install_linux.sh"

# 检测服务器架构并验证是否为 ARM（麒麟系统）
detect_architecture() {
    print_info "检测服务器架构（麒麟系统）..."
    ARCH=$(uname -m)

    if [ -f /etc/kylin-release ] || [ -f /etc/neokylin-release ] || grep -qi "kylin" /etc/os-release 2>/dev/null; then
        print_success "检测到麒麟系统"
    else
        print_warning "未检测到麒麟系统标识，但继续执行（可能在其他国产系统上运行）"
    fi

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
            print_error "本脚本专用于 ARM 架构（麒麟系统）部署"
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

# 确保 docker-buildx 可用（Docker 26 默认 BuildKit，缺 buildx 时 docker build 直接失败）
ensure_docker_buildx_plugin() {
    if docker buildx version >/dev/null 2>&1; then
        print_success "Docker buildx 已就绪: $(docker buildx version 2>/dev/null | head -1)"
        return 0
    fi

    print_warning "未检测到 docker-buildx（BuildKit 构建需要），尝试安装..."

    local arch
    arch=$(uname -m)
    case "$arch" in
        x86_64|amd64) arch="amd64" ;;
        aarch64|arm64) arch="arm64" ;;
        *)
            print_warning "未知架构 $arch，无法自动安装 buildx"
            return 1
            ;;
    esac

    # 包管理器优先
    if check_command apt-get; then
        apt-get install -y -qq docker-buildx-plugin >/dev/null 2>&1 || true
    elif check_command yum; then
        yum install -y -q docker-buildx-plugin >/dev/null 2>&1 || true
    elif check_command dnf; then
        dnf install -y -q docker-buildx-plugin >/dev/null 2>&1 || true
    fi
    if docker buildx version >/dev/null 2>&1; then
        print_success "已通过包管理器安装 docker-buildx"
        return 0
    fi

    local ver="${DOCKER_BUILDX_VERSION:-v0.19.3}"
    local dest_dirs=(
        "/usr/local/lib/docker/cli-plugins"
        "/usr/libexec/docker/cli-plugins"
        "${HOME}/.docker/cli-plugins"
    )
    local urls=(
        "https://github.com/docker/buildx/releases/download/${ver}/buildx-${ver}.linux-${arch}"
        "https://ghproxy.net/https://github.com/docker/buildx/releases/download/${ver}/buildx-${ver}.linux-${arch}"
        "https://mirror.ghproxy.com/https://github.com/docker/buildx/releases/download/${ver}/buildx-${ver}.linux-${arch}"
        "https://ghfast.top/https://github.com/docker/buildx/releases/download/${ver}/buildx-${ver}.linux-${arch}"
    )
    local dest url tmpf
    tmpf=$(mktemp)
    for dest in "${dest_dirs[@]}"; do
        mkdir -p "$dest" 2>/dev/null || continue
        for url in "${urls[@]}"; do
            print_info "尝试下载 buildx: $url"
            if curl -fsSL --connect-timeout 15 --max-time 120 "$url" -o "$tmpf" 2>/dev/null \
                && [ -s "$tmpf" ] && head -c 4 "$tmpf" | grep -q $'\x7fELF'; then
                install -m 0755 "$tmpf" "${dest}/docker-buildx"
                rm -f "$tmpf"
                if docker buildx version >/dev/null 2>&1; then
                    print_success "已安装 docker-buildx 到 ${dest}/docker-buildx"
                    return 0
                fi
            fi
        done
    done
    rm -f "$tmpf"

    print_error "docker-buildx 安装失败。请手动安装后重试："
    print_info "  方式1: apt/yum install docker-buildx-plugin"
    print_info "  方式2: 下载 buildx 二进制到 ~/.docker/cli-plugins/docker-buildx 并 chmod +x"
    print_info "  参考: https://docs.docker.com/go/buildx/"
    return 1
}

_is_kylin_os() {
    [ -f /etc/kylin-release ] || [ -f /etc/neokylin-release ] || grep -qi "kylin" /etc/os-release 2>/dev/null
}

install_platform_preflight() {
    detect_architecture
}

# install 时：先修宿主机 /etc/resolv.conf（daemon.json 的 dns 不能修复 dockerd 拉镜像），
# 再配镜像源 + DNS，最后确保 buildx 可用
install_platform_prepare_docker() {
    ensure_host_dns_for_docker "docker.cnb.cool" || print_warning "宿主机 DNS 异常，后续拉取/构建可能失败"
    configure_docker_mirror
    ensure_docker_buildx_plugin || print_warning "buildx 未就绪，后续本地 docker build 可能失败"
}

install_platform_describe() {
    if _is_kylin_os; then
        print_info "系统类型: 麒麟系统"
    fi
}

install_platform_help_notes() {
    echo "注意："
    echo "  - 本脚本专用于麒麟系统（ARM架构）"
    echo "  - AI 和 VIDEO 模块将使用 install_linux_kylin.sh 脚本"
    echo "  - 其他模块使用标准 install_linux.sh 脚本"
    echo "  - 如需在 x86_64 架构上部署，请使用 install_linux.sh"
    echo ""
}

install_linux_main "$@"
