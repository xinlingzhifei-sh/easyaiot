#!/bin/bash
# shellcheck shell=bash
# ============================================
# CentOS 7.9 单服务启动器公共逻辑
# ============================================
# 供 start_{minio,rustfs,nodered,fuxa,postgresql}_centos7.sh source（在 cd "$SCRIPT_DIR" 之后）。
# 负责：打印、系统检查、Docker 检测 / 升级（CentOS 7 自带 1.13 无法拉新镜像）、
# compose 命令探测、统一网络、端口检查、镜像源配置与国内镜像站直连拉取。
#
# 入口在 source 之前设置：
#   SCRIPT_DIR / COMPOSE_FILE / CONTAINER_NAME / DOCKER_MIRROR
#   FORCE_OS_CHECK / SKIP_MIRROR / SKIP_DOCKER_UPGRADE / FORCE_DOCKER_UPGRADE / MIN_DOCKER_MAJOR
#   C7_SERVICE_LABEL     提示里的服务名（如 MinIO / Node-RED）
#   C7_FIREWALL_PORTS    firewalld 放行提示的 TCP 端口（空格分隔）
#   C7_PULL_TAG_VARS     直连拉取成功后要打的本地标签所在变量名（空格分隔，如 "NODERED_IMAGE NODERED_IMAGE_ALIAS"）
# 统一网络名取自 deploy-profiles.json，入口不再自定义 NETWORK_NAME。

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

print_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

print_section() {
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

# shellcheck source=deploy-profiles.generated.sh
source "${SCRIPT_DIR}/deploy-profiles.generated.sh"
NETWORK_NAME="$(deploy_profile_manifest_network_name)"

# EUID 只读，单独成函数便于测试替身
_c7_is_root() {
    [ "$EUID" -eq 0 ]
}

check_centos7() {
    if [ "$FORCE_OS_CHECK" = true ]; then
        print_warning "已跳过 CentOS 7 系统检查 (--force)"
        return 0
    fi

    print_section "系统环境检查"

    local os_id="" os_version=""
    if [ -f /etc/os-release ]; then
        # shellcheck source=/dev/null
        source /etc/os-release
        os_id="${ID:-}"
        os_version="${VERSION_ID:-}"
    elif [ -f /etc/redhat-release ]; then
        grep -qi "centos" /etc/redhat-release 2>/dev/null && os_id="centos"
        os_version=$(grep -oE '[0-9]+\.[0-9]+' /etc/redhat-release | head -1)
    fi

    if [ "$os_id" = "centos" ]; then
        local major="${os_version%%.*}"
        if [ "$major" = "7" ]; then
            print_success "CentOS 7.x (${os_version})"
        else
            print_warning "检测到 CentOS ${os_version}，本脚本针对 CentOS 7.9 优化"
        fi
    else
        print_warning "当前系统 ID=${os_id:-未知}，脚本针对 CentOS 7 优化"
        print_info "非 CentOS 环境请使用: ./$(basename "$0") --force"
    fi

    if command -v getenforce >/dev/null 2>&1; then
        local selinux_status
        selinux_status=$(getenforce 2>/dev/null || echo "未知")
        print_info "SELinux 状态: $selinux_status"
        if [ "$selinux_status" = "Enforcing" ]; then
            print_warning "SELinux 为 Enforcing 时，若挂载目录无法访问，可临时: setenforce 0"
        fi
    fi

    if systemctl is-active firewalld >/dev/null 2>&1 && [ -n "${C7_FIREWALL_PORTS:-}" ]; then
        local p add_ports=""
        for p in $C7_FIREWALL_PORTS; do
            add_ports="${add_ports} --add-port=${p}/tcp"
        done
        print_info "firewalld 运行中，若无法访问请放行:"
        print_info "  sudo firewall-cmd --permanent${add_ports} && sudo firewall-cmd --reload"
    fi
}

resolve_compose_cmd() {
    if docker compose version >/dev/null 2>&1; then
        COMPOSE_CMD="docker compose"
    elif command -v docker-compose >/dev/null 2>&1; then
        COMPOSE_CMD="docker-compose"
    else
        print_error "未找到 docker compose / docker-compose"
        print_info "CentOS 7 安装示例:"
        print_info "  sudo yum install -y yum-utils"
        print_info "  sudo yum-config-manager --add-repo https://mirrors.huaweicloud.com/docker-ce/linux/centos/docker-ce.repo"
        print_info "  sudo yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin"
        print_info "  sudo systemctl enable --now docker"
        exit 1
    fi
    print_info "使用 Compose 命令: $COMPOSE_CMD"
}

check_docker() {
    print_info "检查 Docker 服务..."
    if docker info >/dev/null 2>&1; then
        print_success "Docker 可用"
        return 0
    fi

    print_warning "Docker 未运行或当前用户无权限，尝试启动..."
    if command -v systemctl >/dev/null 2>&1; then
        if _c7_is_root; then
            systemctl start docker || true
        elif command -v sudo >/dev/null 2>&1; then
            sudo systemctl start docker || true
        fi
    fi

    if ! docker info >/dev/null 2>&1; then
        print_error "无法连接 Docker"
        print_info "请执行: sudo systemctl start docker"
        print_info "并将用户加入 docker 组: sudo usermod -aG docker \$USER && newgrp docker"
        exit 1
    fi
    print_success "Docker 已启动"
}

get_docker_server_version() {
    local ver
    ver=$(docker version 2>/dev/null | awk '
        /^Server:/ { in_server=1; next }
        in_server && /^Version:/ { print $2; exit }
        in_server && /^[A-Z]/ && $1 !~ /^Version:/ { in_server=0 }
    ')
    if [ -n "$ver" ]; then
        echo "$ver"
        return 0
    fi
    docker -v 2>/dev/null | sed -n 's/.*[Vv]ersion \([^, ]*\).*/\1/p' | head -1
}

is_docker_too_old() {
    local ver="${1:-$(get_docker_server_version)}"
    if [ -z "$ver" ]; then
        return 0
    fi
    local major minor
    major=$(echo "$ver" | cut -d. -f1)
    minor=$(echo "$ver" | cut -d. -f2)
    major=${major:-0}
    minor=${minor:-0}
    # 1.x 且小于 1.20，或 major < MIN_DOCKER_MAJOR
    if [ "$major" -le 1 ] 2>/dev/null && [ "$minor" -lt 20 ] 2>/dev/null; then
        return 0
    fi
    if [ "$major" -lt "$MIN_DOCKER_MAJOR" ] 2>/dev/null; then
        return 0
    fi
    return 1
}

upgrade_docker_ce_centos7() {
    print_section "升级 Docker CE（CentOS 7）"

    if ! _c7_is_root; then
        print_error "升级 Docker 需要 root 权限"
        print_info "请执行: sudo $0"
        return 1
    fi

    print_info "卸载系统自带的 docker 1.13..."
    yum remove -y docker \
        docker-client docker-client-latest docker-common \
        docker-latest docker-latest-logrotate docker-logrotate \
        docker-selinux docker-engine-selinux docker-engine \
        2>/dev/null || true

    print_info "安装依赖..."
    yum install -y yum-utils device-mapper-persistent-data lvm2

    print_info "添加 Docker CE 仓库（华为云镜像）..."
    if ! yum-config-manager --add-repo https://mirrors.huaweicloud.com/docker-ce/linux/centos/docker-ce.repo 2>/dev/null; then
        print_warning "华为云仓库添加失败，尝试官方源..."
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
    fi

    print_info "安装 docker-ce（el7 最新稳定版）..."
    set +e
    yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    local yum_rc=$?
    set -e
    if [ "$yum_rc" -ne 0 ]; then
        print_warning "带 compose-plugin 安装失败，尝试仅安装 docker-ce..."
        yum install -y docker-ce docker-ce-cli containerd.io || {
            print_error "Docker CE 安装失败"
            return 1
        }
    fi

    systemctl daemon-reload
    systemctl enable docker
    systemctl start docker
    sleep 2

    if ! docker info >/dev/null 2>&1; then
        print_error "Docker CE 启动失败，请检查: journalctl -u docker -n 50"
        return 1
    fi

    local new_ver
    new_ver=$(get_docker_server_version)
    print_success "Docker 已升级: ${new_ver:-$(docker -v)}"

    if is_docker_too_old "$new_ver"; then
        print_error "升级后版本仍过旧: ${new_ver}"
        return 1
    fi
    return 0
}

ensure_modern_docker() {
    local ver
    ver=$(get_docker_server_version)
    print_info "Docker 版本: ${ver:-未知}"

    if [ "$FORCE_DOCKER_UPGRADE" = true ]; then
        if ! _c7_is_root; then
            print_error "--upgrade-docker 需要 root"
            exit 1
        fi
        upgrade_docker_ce_centos7 || exit 1
        return 0
    fi

    if ! is_docker_too_old "$ver"; then
        print_success "Docker 版本可拉取 ${C7_SERVICE_LABEL} 镜像"
        return 0
    fi

    print_warning "Docker ${ver} 过旧，拉取 ${C7_SERVICE_LABEL} 镜像会报 missing signature key"
    print_info "这是 CentOS7 自带 docker 1.13 的已知限制，需升级到 docker-ce ${MIN_DOCKER_MAJOR}+"

    if [ "$SKIP_DOCKER_UPGRADE" = true ]; then
        print_error "已指定 --no-upgrade-docker，无法继续"
        print_info "请手动升级: sudo $0 --upgrade-docker"
        exit 1
    fi

    if ! _c7_is_root; then
        print_error "自动升级需要 root"
        print_info "请执行: sudo $0"
        print_info "或仅升级 Docker: sudo $0 --upgrade-docker"
        exit 1
    fi

    print_info "将以 root 自动升级 Docker CE（取消请用 --no-upgrade-docker）..."
    upgrade_docker_ce_centos7 || exit 1
}

# MinIO / RustFS 使用的镜像源配置（PostgreSQL 入口有自己的版本；Node-RED / FUXA 用 ensure_docker_mirror）
configure_docker_mirror() {
    if [ "$SKIP_MIRROR" = true ]; then
        print_info "已跳过 Docker 镜像源配置 (--skip-mirror)"
        return 0
    fi

    print_section "配置 Docker 国内镜像源"

    local docker_config_file="/etc/docker/daemon.json"
    _c7_is_root || {
        print_warning "非 root，跳过 daemon.json 配置，将尝试国内镜像站直连拉取"
        return 0
    }

    mkdir -p /etc/docker

    if [ -f "$docker_config_file" ] && grep -q 'docker\.m\.daocloud\.io' "$docker_config_file" 2>/dev/null; then
        print_success "Docker 镜像源已配置（${DOCKER_MIRROR}）"
        return 0
    fi

    local config_updated=false
    if [ ! -f "$docker_config_file" ]; then
        cat > "$docker_config_file" <<EOF
{
  "registry-mirrors": ["${DOCKER_MIRROR}"]
}
EOF
        config_updated=true
    elif command -v python3 >/dev/null 2>&1; then
        set +e
        python3 - "$docker_config_file" "$DOCKER_MIRROR" <<'PYEOF'
import json, sys
config_file, mirror = sys.argv[1], sys.argv[2].rstrip("/") + "/"
with open(config_file, "r") as f:
    config = json.load(f)
mirrors = config.get("registry-mirrors", [])
if not isinstance(mirrors, list):
    mirrors = []
if not any(m.rstrip("/") == mirror.rstrip("/") for m in mirrors):
    mirrors.append(mirror)
    config["registry-mirrors"] = mirrors
    with open(config_file, "w") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
PYEOF
        [ $? -eq 0 ] && config_updated=true
        set -e
    else
        print_warning "请手动在 ${docker_config_file} 添加: ${DOCKER_MIRROR}"
        return 0
    fi

    if [ "$config_updated" = true ]; then
        print_success "Docker 镜像源已更新为 ${DOCKER_MIRROR}"
        # shellcheck source=docker_restart_guard.sh
        source "${SCRIPT_DIR}/docker_restart_guard.sh"
        apply_docker_daemon_config "Docker 镜像源" ||
            print_warning "镜像源暂未生效，将继续尝试直连拉取"
    fi
}

# Node-RED / FUXA：公共脚本不可用或失败时的本地回退
configure_docker_mirror_local() {
    local docker_config_file="/etc/docker/daemon.json"
    _c7_is_root || {
        print_warning "非 root，跳过 daemon.json 配置，将尝试国内镜像站直连拉取"
        return 0
    }

    mkdir -p /etc/docker

    if [ -f "$docker_config_file" ] && grep -qE 'docker\.m\.daocloud\.io|1panel\.live|1ms\.run' "$docker_config_file" 2>/dev/null; then
        print_success "Docker 镜像源已配置"
        return 0
    fi

    local config_updated=false
    if [ ! -f "$docker_config_file" ]; then
        cat > "$docker_config_file" <<EOF
{
  "registry-mirrors": ["${DOCKER_MIRROR}"]
}
EOF
        config_updated=true
    elif command -v python3 >/dev/null 2>&1; then
        set +e
        python3 - "$docker_config_file" "$DOCKER_MIRROR" <<'PYEOF'
import json, sys
config_file, mirror = sys.argv[1], sys.argv[2].rstrip("/") + "/"
with open(config_file, "r") as f:
    config = json.load(f)
mirrors = config.get("registry-mirrors", [])
if not isinstance(mirrors, list):
    mirrors = []
if not any(m.rstrip("/") == mirror.rstrip("/") for m in mirrors):
    mirrors.append(mirror)
    config["registry-mirrors"] = mirrors
    with open(config_file, "w") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
PYEOF
        [ $? -eq 0 ] && config_updated=true
        set -e
    else
        print_warning "请手动在 ${docker_config_file} 添加: ${DOCKER_MIRROR}"
        return 0
    fi

    if [ "$config_updated" = true ]; then
        print_success "Docker 镜像源已更新为 ${DOCKER_MIRROR}"
        # shellcheck source=docker_restart_guard.sh
        source "${SCRIPT_DIR}/docker_restart_guard.sh"
        apply_docker_daemon_config "Docker 镜像源" ||
            print_warning "镜像源暂未生效，将继续尝试直连拉取"
    fi
}

# Node-RED / FUXA：优先复用 docker_mirror_common.sh（含 DNS / 回退链），失败再走本地回退
ensure_docker_mirror() {
    if [ "$SKIP_MIRROR" = true ]; then
        print_info "已跳过 Docker 镜像源配置 (--skip-mirror)"
        return 0
    fi

    print_section "配置 Docker 国内镜像源"

    if [ -f "${SCRIPT_DIR}/docker_mirror_common.sh" ]; then
        # shellcheck source=docker_mirror_common.sh
        source "${SCRIPT_DIR}/docker_mirror_common.sh"
        if configure_docker_mirror; then
            return 0
        fi
        print_warning "公共镜像源配置未完成，尝试本地回退逻辑"
    fi

    configure_docker_mirror_local
}

# 从国内镜像站直连拉取，成功后按 C7_PULL_TAG_VARS 打本地标签
_pull_from_registry() {
    local source_image="$1"
    print_info "从国内镜像站直连拉取: ${source_image}"
    set +e
    DOCKER_CONTENT_TRUST=0 docker pull "$source_image"
    local pull_rc=$?
    set -e
    if [ "$pull_rc" -eq 0 ]; then
        local var first=""
        for var in $C7_PULL_TAG_VARS; do
            docker tag "$source_image" "${!var}" 2>/dev/null || true
            [ -n "$first" ] || first="${!var}"
        done
        print_success "已拉取并标记为 ${first}"
        return 0
    fi
    return 1
}

check_compose_file() {
    if [ ! -f "$COMPOSE_FILE" ]; then
        print_error "未找到 $COMPOSE_FILE，请在 .scripts/docker 目录下运行"
        exit 1
    fi
}

ensure_network() {
    if docker network ls --format '{{.Name}}' | grep -q "^${NETWORK_NAME}$"; then
        print_success "Docker 网络 ${NETWORK_NAME} 已存在"
    else
        print_info "创建 Docker 网络 ${NETWORK_NAME}..."
        docker network create "$NETWORK_NAME" >/dev/null
        print_success "网络 ${NETWORK_NAME} 已创建"
    fi
}

check_port_available() {
    local port="$1"
    local pid=""
    if command -v ss >/dev/null 2>&1; then
        pid=$(ss -lptn "sport = :${port}" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -1 || true)
    elif command -v netstat >/dev/null 2>&1; then
        pid=$(netstat -tlnp 2>/dev/null | grep ":${port} " | awk '{print $7}' | cut -d'/' -f1 | head -1 || true)
    fi

    if [ -n "$pid" ] && [ "$pid" != "-" ]; then
        if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            print_info "端口 ${port} 已由 ${CONTAINER_NAME} 使用"
            return 0
        fi
        print_warning "端口 ${port} 被进程 PID=${pid} 占用"
        return 1
    fi
    print_success "端口 ${port} 可用"
}
