#!/bin/bash
# shellcheck shell=bash
# ============================================
# EL 系（CentOS / RHEL / Rocky / Alma / openEuler）安装入口公共逻辑
# ============================================
# 供 install_linux_centos.sh / install_linux_centos_arm.sh / install_linux_openeuler.sh source。
# 入口负责：头注释、帮助、发行版检查与平台独有函数；本文件负责参数解析、Docker CE
# 版本检测与安装、镜像源、firewalld 放行和转交平台统一安装脚本。
#
# 入口在 source 之前设置：
#   SCRIPT_DIR / PROJECT_ROOT
#   EL_DELEGATE_TARGET    转交的平台脚本绝对路径（install_linux.sh / install_linux_arm.sh）
#   EL_INSTALL_LABEL      转交时 EASYAIOT_INSTALL_LABEL 的默认值
#   EL_INSTALL_SCRIPT     转交时导出的 EASYAIOT_INSTALL_SCRIPT
#   EL_HELP_FN            打印入口帮助的函数名
#   EL_CHECK_FN           发行版 / 架构检查函数名
#   EL_MIRROR_FN          镜像源配置函数名（默认 configure_docker_mirror_centos）
#   EL_INSTALL_DOCKER_FN  安装 / 升级 Docker CE 的函数名（默认 install_docker_ce_el）
#   EL_OLD_DOCKER_HINT    Docker 过旧时的说明
#   EL_ARCH_LABEL         install_docker_ce_el 标题后缀（如 " · ARM"）
#   EL_PKG_ARCH_NOTE      install_docker_ce_el 安装提示后缀（如 " (aarch64)"）
#   EL_INSTALL_FAIL_HINT  Docker CE 安装失败时的附加提示（可空）
#   EL_READY_MESSAGE      --upgrade-docker-only 完成提示
# 可选钩子（入口定义即生效）：
#   el_parse_extra_option "$@"   识别入口专有选项，返回 0 并设置 EL_PARSE_CONSUMED=<消费参数个数>
#   el_after_parse               参数解析完成后的校验
#   el_replace_vendor_docker     版本检查前识别发行版自带 docker；已处理则返回 0
#   el_delegate_exports          转交前追加导出的环境变量

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

FORCE_OS_CHECK=false
SKIP_DOCKER_UPGRADE=false
UPGRADE_DOCKER_ONLY=false
SKIP_FIREWALL=false
SKIP_MIRROR=false
MIN_DOCKER_MAJOR=20
DOCKER_MIRROR="${DOCKER_MIRROR:-https://docker.m.daocloud.io/}"

EL_MIRROR_FN="${EL_MIRROR_FN:-configure_docker_mirror_centos}"
EL_INSTALL_DOCKER_FN="${EL_INSTALL_DOCKER_FN:-install_docker_ce_el}"
EL_READY_MESSAGE="${EL_READY_MESSAGE:-Docker CE 准备完成}"

# 对外常用端口（firewalld 放行；UDP 含媒体相关）
FIREWALL_TCP_PORTS=(
    8888 48080 5000 6000 6100 1984 8554 8555 9010 8002 48096 9200 9300 13338-13437
    8848 9000 9001 5432 6379 1880 1881 1883
    8080 1985 1935 9100
)
FIREWALL_UDP_PORTS=(
    8555 30000-30500 10000-20000
)

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

# ---------- 参数解析（仅消费入口专用选项，其余原样转交） ----------
el_parse_args() {
    FORWARD_ARGS=()
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help)
                "$EL_HELP_FN"
                exit 0
                ;;
            -f|--force)
                FORCE_OS_CHECK=true
                shift
                ;;
            --no-upgrade-docker)
                SKIP_DOCKER_UPGRADE=true
                shift
                ;;
            --upgrade-docker-only)
                UPGRADE_DOCKER_ONLY=true
                shift
                ;;
            --no-firewall)
                SKIP_FIREWALL=true
                shift
                ;;
            --skip-mirror)
                SKIP_MIRROR=true
                shift
                ;;
            --)
                shift
                FORWARD_ARGS+=("$@")
                break
                ;;
            *)
                EL_PARSE_CONSUMED=0
                if declare -F el_parse_extra_option >/dev/null && el_parse_extra_option "$@"; then
                    shift "$EL_PARSE_CONSUMED"
                    continue
                fi
                FORWARD_ARGS+=("$@")
                break
                ;;
        esac
    done
    if declare -F el_after_parse >/dev/null; then
        el_after_parse
    fi
}

# ---------- 发行版检测 ----------
detect_el_family() {
    OS_ID=""
    OS_VERSION=""
    OS_LIKE=""
    OS_PRETTY=""
    OS_MAJOR=""

    if [ -f /etc/os-release ]; then
        # shellcheck source=/dev/null
        . /etc/os-release
        OS_ID="${ID:-}"
        OS_VERSION="${VERSION_ID:-}"
        OS_LIKE="${ID_LIKE:-}"
        OS_PRETTY="${PRETTY_NAME:-$OS_ID $OS_VERSION}"
    elif [ -f /etc/redhat-release ]; then
        OS_PRETTY=$(cat /etc/redhat-release)
        if grep -qi "centos" /etc/redhat-release; then
            OS_ID="centos"
        elif grep -qi "red hat\|rhel" /etc/redhat-release; then
            OS_ID="rhel"
        elif grep -qi "rocky" /etc/redhat-release; then
            OS_ID="rocky"
        elif grep -qi "alma" /etc/redhat-release; then
            OS_ID="almalinux"
        else
            OS_ID="rhel"
        fi
        OS_VERSION=$(grep -oE '[0-9]+(\.[0-9]+)?' /etc/redhat-release | head -1 || true)
    fi

    OS_MAJOR="${OS_VERSION%%.*}"
}

is_el_family() {
    case "$OS_ID" in
        centos|rhel|rocky|almalinux|ol|anolis|opencloudos|tencentos|kylin|uos)
            return 0
            ;;
    esac
    echo " ${OS_LIKE} " | grep -qiE '[[:space:]](rhel|centos|fedora)[[:space:]]' && return 0
    return 1
}

# 发行版检查末尾的 SELinux / firewalld 提示
el_report_selinux_firewalld() {
    if command -v getenforce >/dev/null 2>&1; then
        local selinux_status
        selinux_status=$(getenforce 2>/dev/null || echo "未知")
        print_info "SELinux: $selinux_status"
        if [ "$selinux_status" = "Enforcing" ]; then
            print_warning "SELinux Enforcing 时，若容器挂载目录权限异常，可临时: setenforce 0"
            print_info "或给数据目录打标签: chcon -Rt svirt_sandbox_file_t <data_dir>"
        fi
    fi

    if systemctl is-active firewalld >/dev/null 2>&1; then
        print_info "firewalld 正在运行（安装时将自动放行常用端口，可用 --no-firewall 跳过）"
    fi
}

# EUID 只读，单独成函数便于测试替身
_el_is_root() {
    [ "$EUID" -eq 0 ]
}

pkg_mgr() {
    if command -v dnf >/dev/null 2>&1; then
        echo "dnf"
    else
        echo "yum"
    fi
}

# ---------- Docker 版本检测 / 安装 ----------
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
    local ver="${1:-}"
    if [ -z "$ver" ]; then
        return 0
    fi
    local major minor
    major=$(echo "$ver" | cut -d. -f1)
    minor=$(echo "$ver" | cut -d. -f2)
    major=${major:-0}
    minor=${minor:-0}
    # 1.x 且小于 1.20（CentOS 7 自带 1.13），或 major < MIN_DOCKER_MAJOR（openEuler 自带常见 18.09）
    if [ "$major" -le 1 ] 2>/dev/null && [ "$minor" -lt 20 ] 2>/dev/null; then
        return 0
    fi
    if [ "$major" -lt "$MIN_DOCKER_MAJOR" ] 2>/dev/null; then
        return 0
    fi
    return 1
}

install_docker_ce_el() {
    local pm
    pm=$(pkg_mgr)
    print_section "安装 / 升级 Docker CE (${OS_ID:-el}${OS_MAJOR}${EL_ARCH_LABEL:-})"

    if ! _el_is_root; then
        print_error "安装 Docker CE 需要 root 权限"
        print_info "请执行: sudo $0 --upgrade-docker-only"
        return 1
    fi

    print_info "卸载可能存在的旧版 docker..."
    $pm remove -y docker \
        docker-client docker-client-latest docker-common \
        docker-latest docker-latest-logrotate docker-logrotate \
        docker-selinux docker-engine-selinux docker-engine \
        2>/dev/null || true

    print_info "安装依赖..."
    $pm install -y yum-utils device-mapper-persistent-data lvm2 2>/dev/null \
        || $pm install -y yum-utils 2>/dev/null \
        || true

    print_info "添加 Docker CE 仓库（华为云镜像优先）..."
    local repo_ok=false
    if yum-config-manager --add-repo https://mirrors.huaweicloud.com/docker-ce/linux/centos/docker-ce.repo 2>/dev/null; then
        repo_ok=true
    elif command -v dnf >/dev/null 2>&1 && dnf config-manager --add-repo https://mirrors.huaweicloud.com/docker-ce/linux/centos/docker-ce.repo 2>/dev/null; then
        repo_ok=true
    fi
    if [ "$repo_ok" != true ]; then
        print_warning "华为云仓库添加失败，尝试官方源..."
        yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo 2>/dev/null \
            || dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo 2>/dev/null \
            || true
    fi

    print_info "安装 docker-ce / cli / containerd / compose-plugin${EL_PKG_ARCH_NOTE:-}..."
    set +e
    $pm install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    local yum_rc=$?
    set -e
    if [ "$yum_rc" -ne 0 ]; then
        print_warning "带 compose-plugin 安装失败，尝试仅安装 docker-ce..."
        $pm install -y docker-ce docker-ce-cli containerd.io || {
            print_error "Docker CE 安装失败"
            [ -n "${EL_INSTALL_FAIL_HINT:-}" ] && print_info "$EL_INSTALL_FAIL_HINT"
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
    print_success "Docker 已就绪: ${new_ver:-$(docker -v)}"

    if is_docker_too_old "$new_ver"; then
        print_error "升级后版本仍过旧: ${new_ver}（需要 ${MIN_DOCKER_MAJOR}+）"
        return 1
    fi
    return 0
}

ensure_modern_docker() {
    if [ "$SKIP_DOCKER_UPGRADE" = true ]; then
        print_warning "已跳过 Docker 自动安装/升级 (--no-upgrade-docker)"
        if ! command -v docker >/dev/null 2>&1; then
            print_error "Docker 未安装"
            exit 1
        fi
        return 0
    fi

    if ! command -v docker >/dev/null 2>&1; then
        print_warning "未检测到 Docker，将自动安装 Docker CE"
        "$EL_INSTALL_DOCKER_FN" || exit 1
        return 0
    fi

    # 尝试启动
    if ! docker info >/dev/null 2>&1; then
        if _el_is_root; then
            systemctl start docker 2>/dev/null || true
        fi
    fi

    local ver
    ver=$(get_docker_server_version)
    print_info "当前 Docker 版本: ${ver:-未知}"

    if declare -F el_replace_vendor_docker >/dev/null && el_replace_vendor_docker; then
        return 0
    fi

    if ! is_docker_too_old "$ver"; then
        print_success "Docker 版本满足要求 (>= ${MIN_DOCKER_MAJOR})"
        return 0
    fi

    print_warning "Docker ${ver:-未知} 过旧（${EL_OLD_DOCKER_HINT}）"
    print_info "将自动升级为 Docker CE ${MIN_DOCKER_MAJOR}+..."
    "$EL_INSTALL_DOCKER_FN" || exit 1
}

# ---------- 镜像源 / 防火墙 ----------
configure_docker_mirror_centos() {
    if [ "$SKIP_MIRROR" = true ]; then
        print_info "已跳过 Docker 镜像源配置 (--skip-mirror)"
        return 0
    fi
    if ! _el_is_root; then
        print_warning "配置镜像源需要 root，已跳过"
        return 0
    fi

    print_info "配置 Docker 国内镜像源: $DOCKER_MIRROR"
    mkdir -p /etc/docker
    local config_file="/etc/docker/daemon.json"
    local need_restart=false

    if [ ! -f "$config_file" ]; then
        printf '{\n  "registry-mirrors": ["%s"]\n}\n' "$DOCKER_MIRROR" > "$config_file"
        need_restart=true
        print_success "已写入 $config_file"
    elif grep -q 'docker\.m\.daocloud\.io' "$config_file" 2>/dev/null; then
        print_success "Docker 镜像源已配置"
        return 0
    elif command -v python3 >/dev/null 2>&1; then
        local rc=0
        python3 - "$config_file" "$DOCKER_MIRROR" <<'PYEOF' || rc=$?
import json, sys
path, mirror = sys.argv[1], sys.argv[2]
try:
    cfg = json.load(open(path))
except Exception:
    sys.exit(1)
mirrors = cfg.get("registry-mirrors", [])
if not isinstance(mirrors, list):
    mirrors = []
normalized = [m.rstrip("/") for m in mirrors if isinstance(m, str)]
if mirror.rstrip("/") in normalized:
    sys.exit(0)
mirrors.append(mirror)
cfg["registry-mirrors"] = mirrors
json.dump(cfg, open(path, "w"), indent=2, ensure_ascii=False)
sys.exit(3)
PYEOF
        case $rc in
            0) print_success "Docker 镜像源已就绪" ;;
            3) need_restart=true; print_success "Docker 镜像源已更新" ;;
            *) print_warning "无法自动合并 $config_file，请手动添加 registry-mirrors" ;;
        esac
    else
        print_warning "已有 $config_file 且无 python3，跳过自动合并"
        return 0
    fi

    if [ "$need_restart" = true ]; then
        # shellcheck source=docker_restart_guard.sh
        source "${SCRIPT_DIR}/docker_restart_guard.sh"
        apply_docker_daemon_config "Docker 镜像源" || true
    fi
}

configure_firewalld_ports() {
    if [ "$SKIP_FIREWALL" = true ]; then
        print_info "已跳过 firewalld 端口放行 (--no-firewall)"
        return 0
    fi
    if ! _el_is_root; then
        print_warning "放行 firewalld 端口需要 root，已跳过"
        return 0
    fi
    if ! systemctl is-active firewalld >/dev/null 2>&1; then
        print_info "firewalld 未运行，跳过端口放行"
        return 0
    fi
    if ! command -v firewall-cmd >/dev/null 2>&1; then
        return 0
    fi

    print_section "配置 firewalld 端口"
    local changed=false
    local p
    for p in "${FIREWALL_TCP_PORTS[@]}"; do
        if firewall-cmd --permanent --add-port="${p}/tcp" >/dev/null 2>&1; then
            changed=true
            print_info "已放行 TCP ${p}"
        fi
    done
    for p in "${FIREWALL_UDP_PORTS[@]}"; do
        if firewall-cmd --permanent --add-port="${p}/udp" >/dev/null 2>&1; then
            changed=true
            print_info "已放行 UDP ${p}"
        fi
    done
    if [ "$changed" = true ]; then
        firewall-cmd --reload >/dev/null 2>&1 || true
        print_success "firewalld 规则已刷新"
    else
        print_info "firewalld 端口规则无变更"
    fi
}

el_prepare_environment() {
    "$EL_CHECK_FN"
    ensure_modern_docker
    "$EL_MIRROR_FN"
    configure_firewalld_ports
}

# ---------- 转交平台统一安装脚本 ----------
el_delegate() {
    if [ ! -f "$EL_DELEGATE_TARGET" ]; then
        print_error "未找到 $EL_DELEGATE_TARGET"
        exit 1
    fi
    chmod +x "$EL_DELEGATE_TARGET" 2>/dev/null || true

    export EASYAIOT_INSTALL_LABEL="${EASYAIOT_INSTALL_LABEL:-$EL_INSTALL_LABEL}"
    export EASYAIOT_INSTALL_SCRIPT="$EL_INSTALL_SCRIPT"
    export DOCKER_MIRROR
    if declare -F el_delegate_exports >/dev/null; then
        el_delegate_exports
    fi

    print_section "转交平台统一安装脚本"
    print_info "执行: bash $EL_DELEGATE_TARGET ${FORWARD_ARGS[*]:-}"
    cd "$PROJECT_ROOT"
    exec bash "$EL_DELEGATE_TARGET" "${FORWARD_ARGS[@]}"
}

# ---------- main ----------
el_main() {
    el_parse_args "$@"

    if [ "$UPGRADE_DOCKER_ONLY" = true ]; then
        "$EL_CHECK_FN"
        SKIP_DOCKER_UPGRADE=false
        ensure_modern_docker
        "$EL_MIRROR_FN"
        print_success "$EL_READY_MESSAGE"
        exit 0
    fi

    local cmd="${FORWARD_ARGS[0]:-}"
    case "$cmd" in
        help|--help|-h)
            "$EL_HELP_FN"
            echo ""
            print_info "以下为 $(basename "$EL_DELEGATE_TARGET") 完整帮助："
            echo ""
            bash "$EL_DELEGATE_TARGET" help
            exit 0
            ;;
        profile)
            # 纯查询，无需 Docker 升级
            el_delegate
            ;;
        *)
            # 无参数（交互菜单）或其它部署命令：先做环境准备
            el_prepare_environment
            el_delegate
            ;;
    esac
}
