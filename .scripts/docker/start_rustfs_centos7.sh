#!/bin/bash

# ============================================
# CentOS 7.9 单独部署 RustFS 容器脚本
# ============================================
# 仅适用于 docker-compose.yml 已配置 RustFS 服务的环境。
# 本地默认 MinIO 部署请使用 start_minio_centos7.sh。
#
# 使用方法：
#   cd .scripts/docker
#   chmod +x start_rustfs_centos7.sh
#   sudo ./start_rustfs_centos7.sh
#
# 选项：
#   -h, --help          显示帮助
#   -f, --force         跳过 CentOS 7 系统检查
#   --stop              停止 RustFS 容器
#   --restart           重启 RustFS 容器
#   --status            查看容器与健康状态
#   --no-wait           启动后不等待健康检查
#   --skip-mirror       跳过配置 Docker 国内镜像源
#   --skip-pull         跳过拉取镜像
#   --no-upgrade-docker 检测到过旧 Docker 时不自动升级
#   --upgrade-docker    强制升级 Docker CE（需 root）
#   --skip-upload       跳过自动上传 RustFS 数据
#
# 默认连接信息（与 docker-compose.yml 一致）：
#   API:     http://127.0.0.1:9000
#   控制台:  http://127.0.0.1:9001
#   凭据:    通过 MINIO_ROOT_USER / MINIO_ROOT_PASSWORD 安全注入
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.yml"
SERVICE_MINIO="RustFS"
CONTAINER_NAME="rustfs-server"
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001
MINIO_ROOT_USER="${MINIO_ROOT_USER:-}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:-}"
DOCKER_MIRROR="https://docker.m.daocloud.io/"

# 与 docker-compose.yml 保持一致，启动时从 compose 解析
MINIO_IMAGE="rustfs/rustfs:1.0.0"

FORCE_OS_CHECK=false
WAIT_READY=true
SKIP_MIRROR=false
SKIP_PULL=false
SKIP_DOCKER_UPGRADE=false
FORCE_DOCKER_UPGRADE=false
SKIP_UPLOAD=false
MIN_DOCKER_MAJOR=20
ACTION="start"

# 公共逻辑见 start_centos7_common.sh（统一网络名取自 deploy-profiles.json）
C7_SERVICE_LABEL="RustFS"
C7_FIREWALL_PORTS="${MINIO_API_PORT} ${MINIO_CONSOLE_PORT}"
C7_PULL_TAG_VARS="MINIO_IMAGE"
# shellcheck source=start_centos7_common.sh
source "${SCRIPT_DIR}/start_centos7_common.sh"

show_help() {
    cat <<'EOF'
CentOS 7.9 单独部署 RustFS 容器

用法:
  ./start_rustfs_centos7.sh [选项]

选项:
  -h, --help          显示此帮助
  -f, --force         跳过 CentOS 7 系统检查
  --stop              停止 RustFS 容器
  --restart           重启 RustFS 容器
  --status            查看容器状态
  --no-wait           启动后不等待健康检查
  --skip-mirror       跳过配置 Docker 国内镜像源
  --skip-pull         跳过拉取镜像
  --no-upgrade-docker 不自动升级过旧 Docker
  --upgrade-docker    强制升级 Docker CE（需 root）
  --skip-upload       跳过自动上传 RustFS 数据

示例:
  sudo ./start_rustfs_centos7.sh        # 启动后自动上传 ../minio 数据（CentOS7 用 mc）
  ./start_rustfs_centos7.sh --status
  ./upload_rustfs_data.sh --prefer-mc --non-interactive
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help) show_help; exit 0 ;;
            -f|--force) FORCE_OS_CHECK=true; shift ;;
            --stop) ACTION="stop"; shift ;;
            --restart) ACTION="restart"; shift ;;
            --status) ACTION="status"; shift ;;
            --no-wait) WAIT_READY=false; shift ;;
            --skip-mirror) SKIP_MIRROR=true; shift ;;
            --skip-pull) SKIP_PULL=true; shift ;;
            --no-upgrade-docker) SKIP_DOCKER_UPGRADE=true; shift ;;
            --upgrade-docker) FORCE_DOCKER_UPGRADE=true; shift ;;
            --skip-upload) SKIP_UPLOAD=true; shift ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

resolve_minio_image_from_compose() {
    local img
    img=$($COMPOSE_CMD -f "$COMPOSE_FILE" config 2>/dev/null | awk '
        $1 == "RustFS:" { svc=1; next }
        svc && $1 == "image:" { print $2; exit }
        svc && $1 ~ /^[A-Za-z]/ && $1 != "image:" { svc=0 }
    ')
    if [ -z "$img" ]; then
        img=$(awk '
            /^  RustFS:/ { p=1; next }
            p && /image:/ { gsub(/.*image:[[:space:]]*/, ""); gsub(/["'\'']/, ""); print; exit }
        ' "$COMPOSE_FILE" 2>/dev/null)
    fi
    if [ -n "$img" ]; then
        MINIO_IMAGE="$img"
        print_info "RustFS 镜像: ${MINIO_IMAGE}"
    else
        print_warning "未能从 compose 解析镜像，使用默认: ${MINIO_IMAGE}"
    fi
}

ensure_minio_image() {
    if [ "$SKIP_PULL" = true ]; then
        docker image inspect "$MINIO_IMAGE" >/dev/null 2>&1 || {
            print_error "本地不存在镜像 ${MINIO_IMAGE}"
            exit 1
        }
        return 0
    fi

    print_section "拉取 RustFS 镜像 (${MINIO_IMAGE})"

    if docker image inspect "$MINIO_IMAGE" >/dev/null 2>&1; then
        print_success "镜像已存在: ${MINIO_IMAGE}"
        return 0
    fi

    if is_docker_too_old "$(get_docker_server_version)"; then
        print_error "Docker 版本过旧，请先升级: sudo $0 --upgrade-docker"
        exit 1
    fi

    export DOCKER_CONTENT_TRUST=0
    local mirrors=(
        "docker.m.daocloud.io/${MINIO_IMAGE}"
    )

    local pulled=false img
    for img in "${mirrors[@]}"; do
        if _pull_from_registry "$img"; then
            pulled=true
            break
        fi
    done

    if [ "$pulled" != true ]; then
        print_warning "国内镜像站直连失败，尝试 docker pull ${MINIO_IMAGE} ..."
        set +e
        DOCKER_CONTENT_TRUST=0 docker pull "$MINIO_IMAGE" && pulled=true
        set -e
    fi

    if [ "$pulled" = true ] && docker image inspect "$MINIO_IMAGE" >/dev/null 2>&1; then
        print_success "RustFS 镜像就绪: ${MINIO_IMAGE}"
        return 0
    fi

    print_error "无法拉取 RustFS 镜像"
    print_info "可尝试: docker pull ${MINIO_IMAGE}"
    exit 1
}

create_data_dirs() {
    local data_dir="${SCRIPT_DIR}/rustfs_data/data"
    local logs_dir="${SCRIPT_DIR}/rustfs_data/logs"

    print_info "准备数据目录 rustfs_data/{data,logs}..."
    mkdir -p "$data_dir" "$logs_dir"
    chown -R 10001:10001 "$data_dir" "$logs_dir" 2>/dev/null || \
        sudo chown -R 10001:10001 "$data_dir" "$logs_dir" 2>/dev/null || true
    print_success "RustFS 数据目录已就绪"
}

check_ports() {
    print_info "检查端口 ${MINIO_API_PORT} / ${MINIO_CONSOLE_PORT}..."
    check_port_available "$MINIO_API_PORT" || return 1
    check_port_available "$MINIO_CONSOLE_PORT" || return 1
}

start_minio() {
    print_section "启动 RustFS (${SERVICE_MINIO})"
    $COMPOSE_CMD -f "$COMPOSE_FILE" up -d "$SERVICE_MINIO"
    print_success "已执行: $COMPOSE_CMD up -d ${SERVICE_MINIO}"
}

wait_for_minio() {
    if [ "$WAIT_READY" = false ]; then
        return 0
    fi

    print_info "等待 RustFS 就绪（最多 90 秒）..."
    local attempt=0
    while [ "$attempt" -lt 30 ]; do
        if curl -sf "http://127.0.0.1:${MINIO_API_PORT}/health/ready" >/dev/null 2>&1; then
            print_success "RustFS 健康检查通过"
            return 0
        fi
        if docker exec "$CONTAINER_NAME" curl -sf "http://localhost:9000/health/ready" >/dev/null 2>&1; then
            print_success "RustFS 容器内健康检查通过"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 3
    done

    print_warning "健康检查超时，容器可能仍在启动"
    print_info "查看日志: docker logs ${CONTAINER_NAME}"
    return 1
}

show_connection_info() {
    print_section "RustFS 连接信息"
    echo "  容器名:   ${CONTAINER_NAME}"
    echo "  API:      http://127.0.0.1:${MINIO_API_PORT}"
    echo "  控制台:   http://127.0.0.1:${MINIO_CONSOLE_PORT}"
    echo "  用户名:   <由 MINIO_ROOT_USER 安全注入，不输出>"
    echo "  密码:     <已安全注入，不输出>"
    echo ""
    print_info "常用命令:"
    echo "  docker ps | grep ${CONTAINER_NAME}"
    echo "  docker logs -f ${CONTAINER_NAME}"
    echo "  curl http://127.0.0.1:${MINIO_API_PORT}/health/ready"
    echo "  ./upload_rustfs_data.sh --prefer-mc --non-interactive"
}

upload_rustfs_data_auto() {
    if [ "$SKIP_UPLOAD" = true ]; then
        print_info "已跳过 RustFS 数据上传 (--skip-upload)"
        return 0
    fi

    local upload_script="${SCRIPT_DIR}/upload_rustfs_data.sh"
    if [ ! -f "$upload_script" ]; then
        print_warning "未找到 upload_rustfs_data.sh，跳过数据上传"
        return 0
    fi

    print_section "自动上传 RustFS 数据"
    chmod +x "$upload_script" 2>/dev/null || true
    set +e
    "$upload_script" --prefer-mc --non-interactive
    local rc=$?
    set -e
    if [ "$rc" -eq 0 ]; then
        print_success "RustFS 数据上传完成"
        return 0
    fi
    print_warning "RustFS 数据上传未完全成功 (exit ${rc})，可手动: ./upload_rustfs_data.sh --prefer-mc"
    return 0
}

stop_minio() {
    print_section "停止 RustFS"
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker stop "$CONTAINER_NAME"
        print_success "容器已停止"
    else
        $COMPOSE_CMD -f "$COMPOSE_FILE" stop "$SERVICE_MINIO" 2>/dev/null || true
        print_info "容器未在运行"
    fi
}

show_status() {
    print_section "RustFS 状态"
    docker ps -a --filter "name=${CONTAINER_NAME}" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' || true
    echo ""
    if curl -sf "http://127.0.0.1:${MINIO_API_PORT}/health/ready" >/dev/null 2>&1; then
        print_success "健康检查: 正常"
    else
        print_warning "健康检查: 未就绪"
    fi
}

main() {
    parse_args "$@"

    case "$ACTION" in
        stop)
            check_docker
            resolve_compose_cmd
            stop_minio
            exit 0
            ;;
        status)
            check_docker
            show_status
            exit 0
            ;;
        restart)
            check_docker
            resolve_compose_cmd
            check_compose_file
            stop_minio
            sleep 2
            ACTION="start"
            ;;
    esac

    print_section "CentOS 7.9 RustFS 独立部署"
    check_centos7
    check_docker
    ensure_modern_docker
    configure_docker_mirror || print_warning "镜像源配置未完成，将尝试直连拉取"
    resolve_compose_cmd
    check_compose_file
    resolve_minio_image_from_compose
    ensure_minio_image
    ensure_network
    create_data_dirs
    check_ports || print_warning "端口冲突可能导致启动失败，继续尝试..."

    start_minio
    wait_for_minio || true
    upload_rustfs_data_auto
    show_connection_info
    print_success "RustFS 独立部署流程完成"
}

main "$@"
