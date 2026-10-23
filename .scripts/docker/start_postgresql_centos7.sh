#!/bin/bash

# ============================================
# CentOS 7.9 单独启动 PostgreSQL 容器脚本
# ============================================
# 仅启动 docker-compose.yml 中的 PostgresSQL 服务（不启动其他中间件）
#
# 使用方法：
#   cd .scripts/docker
#   chmod +x start_postgresql_centos7.sh
#   ./start_postgresql_centos7.sh
#
# 选项：
#   -h, --help      显示帮助
#   -f, --force     跳过 CentOS 7 系统检查
#   --stop          停止 PostgreSQL 容器
#   --restart       重启 PostgreSQL 容器
#   --status        查看容器与健康状态
#   --no-init       跳过 PostgresSQL-init 权限初始化容器
#   --no-wait       启动后不等待 pg_isready
#   --skip-mirror   跳过配置 Docker 国内镜像源（daemon.json）
#   --skip-pull     跳过拉取镜像（本地已有 postgres:18 时使用）
#
# 国内镜像：与 install_middleware_linux.sh 一致，使用 docker.m.daocloud.io
# 拉取失败时会依次尝试国内镜像站直接拉取并 tag 为 postgres:18
#
# 默认连接信息（与 docker-compose.yml 一致）：
#   主机: 127.0.0.1  端口: 5432
#   用户: postgres    密码通过 POSTGRES_PASSWORD 安全注入
#   数据库: postgres
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.yml"
SERVICE_INIT="PostgresSQL-init"
SERVICE_PG="PostgresSQL"
CONTAINER_NAME="postgres-server"
INIT_CONTAINER="postgres-init"
PG_PORT=5432
PG_IMAGE="postgres:18"
DOCKER_MIRROR="https://docker.m.daocloud.io/"

FORCE_OS_CHECK=false
RUN_INIT=true
WAIT_READY=true
SKIP_MIRROR=false
SKIP_PULL=false
SKIP_DOCKER_UPGRADE=false
FORCE_DOCKER_UPGRADE=false
MIN_DOCKER_MAJOR=20
ACTION="start"

# 公共逻辑见 start_centos7_common.sh（统一网络名取自 deploy-profiles.json）
C7_SERVICE_LABEL="PostgreSQL"
C7_FIREWALL_PORTS="${PG_PORT}"
C7_PULL_TAG_VARS="PG_IMAGE"
# shellcheck source=start_centos7_common.sh
source "${SCRIPT_DIR}/start_centos7_common.sh"

show_help() {
    cat <<'EOF'
CentOS 7.9 单独启动 PostgreSQL 容器

用法:
  ./start_postgresql_centos7.sh [选项]

选项:
  -h, --help      显示此帮助
  -f, --force     跳过 CentOS 7 系统检查
  --stop          停止 PostgreSQL 容器
  --restart       重启 PostgreSQL 容器
  --status        查看容器状态
  --no-init       跳过权限初始化容器 PostgresSQL-init
  --no-wait       启动后不等待数据库就绪
  --skip-mirror       跳过配置 Docker 国内镜像源
  --skip-pull         跳过拉取镜像
  --no-upgrade-docker 检测到过旧 Docker 时不自动升级
  --upgrade-docker    强制升级 Docker CE（需 root）

示例:
  sudo ./start_postgresql_centos7.sh   # 推荐 root，可自动配置镜像源
  ./start_postgresql_centos7.sh
  ./start_postgresql_centos7.sh --restart
  sudo ./start_postgresql_centos7.sh   # 首次部署建议用 root/sudo 设置数据目录权限
EOF
}

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            -h|--help)
                show_help
                exit 0
                ;;
            -f|--force)
                FORCE_OS_CHECK=true
                shift
                ;;
            --stop)
                ACTION="stop"
                shift
                ;;
            --restart)
                ACTION="restart"
                shift
                ;;
            --status)
                ACTION="status"
                shift
                ;;
            --no-init)
                RUN_INIT=false
                shift
                ;;
            --no-wait)
                WAIT_READY=false
                shift
                ;;
            --skip-mirror)
                SKIP_MIRROR=true
                shift
                ;;
            --skip-pull)
                SKIP_PULL=true
                shift
                ;;
            --no-upgrade-docker)
                SKIP_DOCKER_UPGRADE=true
                shift
                ;;
            --upgrade-docker)
                FORCE_DOCKER_UPGRADE=true
                shift
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

# 配置 Docker 国内镜像源（与 install_middleware_linux.sh 一致，使用 docker.m.daocloud.io）
# 注意：镜像源配置失败不应中断脚本，后续仍会尝试国内镜像站直连拉取
configure_docker_mirror() {
    if [ "$SKIP_MIRROR" = true ]; then
        print_info "已跳过 Docker 镜像源配置 (--skip-mirror)"
        return 0
    fi

    print_section "配置 Docker 国内镜像源"

    local docker_config_file="/etc/docker/daemon.json"
    local docker_config_dir="/etc/docker"
    local need_restart=false
    local config_updated=false

    if [ "$EUID" -ne 0 ]; then
        print_warning "配置镜像源需要 root，当前非 root，跳过 daemon.json 写入"
        print_info "可改用 root 运行，或手动添加 registry-mirrors: ${DOCKER_MIRROR}"
        print_info "后续将尝试从 docker.m.daocloud.io 等国内地址直连拉取镜像"
        return 0
    fi

    mkdir -p "$docker_config_dir"

    # 已包含国内镜像源
    if [ -f "$docker_config_file" ] && grep -q 'docker\.m\.daocloud\.io' "$docker_config_file" 2>/dev/null; then
        print_success "Docker 镜像源已配置（${DOCKER_MIRROR}）"
        return 0
    fi

    # 无配置文件：直接创建（与 install_linux.sh 一致）
    if [ ! -f "$docker_config_file" ]; then
        cat > "$docker_config_file" <<EOF
{
  "registry-mirrors": ["${DOCKER_MIRROR}"]
}
EOF
        config_updated=true
        print_info "已创建 ${docker_config_file}"
    elif command -v python3 >/dev/null 2>&1; then
        # 有配置文件时仅用 python3 合并 JSON（避免 CentOS7 python2 语法问题）
        local py_file
        set +e
        py_file=$(mktemp /tmp/docker-mirror-config.XXXXXX.py 2>/dev/null)
        set -e
        [ -z "$py_file" ] && py_file="/tmp/docker-mirror-config.$$.py"
        cat > "$py_file" <<'PYEOF'
import json
import os
import sys

config_file = os.environ["DOCKER_CONFIG_FILE"]
mirror = os.environ["DOCKER_MIRROR"].rstrip("/") + "/"

try:
    with open(config_file, "r") as f:
        config = json.load(f)
except Exception as e:
    sys.stderr.write("读取失败: %s\n" % e)
    sys.exit(1)

mirrors = config.get("registry-mirrors", [])
if not isinstance(mirrors, list):
    mirrors = []

if not any(m.rstrip("/") == mirror.rstrip("/") for m in mirrors):
    mirrors.append(mirror)
    config["registry-mirrors"] = mirrors
    with open(config_file, "w") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
    print("UPDATED")
else:
    print("OK")
PYEOF
        set +e
        local py_out
        py_out=$(DOCKER_CONFIG_FILE="$docker_config_file" DOCKER_MIRROR="$DOCKER_MIRROR" \
            python3 "$py_file" 2>&1)
        local py_rc=$?
        set -e
        rm -f "$py_file"

        if [ "$py_rc" -eq 0 ]; then
            config_updated=true
            if [ "$py_out" = "UPDATED" ]; then
                print_info "已向已有 daemon.json 合并镜像源"
            else
                print_info "daemon.json 镜像源检查完成"
            fi
        else
            print_warning "无法自动合并已有 daemon.json（格式可能无效）"
            print_info "请手动在 registry-mirrors 中添加: ${DOCKER_MIRROR}"
            print_info "或备份后删除 ${docker_config_file} 再重新运行本脚本"
            return 0
        fi
    else
        print_warning "已有 ${docker_config_file} 但未安装 python3，无法自动合并"
        print_info "请手动添加 registry-mirrors: ${DOCKER_MIRROR}"
        return 0
    fi

    if [ "$config_updated" = true ]; then
        print_success "Docker 镜像源已更新为 ${DOCKER_MIRROR}"
        need_restart=true
    fi

    if [ "$need_restart" = true ]; then
        # shellcheck source=docker_restart_guard.sh
        source "${SCRIPT_DIR}/docker_restart_guard.sh"
        if ! apply_docker_daemon_config "Docker 镜像源" || ! docker info >/dev/null 2>&1; then
            print_info "将继续尝试从国内镜像站直连拉取 postgres 镜像"
            return 0
        fi
    fi

    show_docker_registry_mirrors

    return 0
}

# 显示 daemon.json 中的 registry-mirrors 是否已生效
show_docker_registry_mirrors() {
    local mirror_lines
    mirror_lines=$(docker info 2>/dev/null | grep -iE 'Registry Mirrors|registry-mirrors' -A 6 || true)
    if [ -n "$mirror_lines" ]; then
        print_info "当前 Docker registry-mirrors:"
        echo "$mirror_lines" | sed 's/^/  /'
    else
        print_warning "docker info 未显示 Registry Mirrors（CentOS7 旧版 Docker 可能不支持或需重启）"
        print_info "本脚本将优先从 docker.m.daocloud.io 直连拉取，不依赖 registry-mirror"
    fi
}

# 确保 postgres:18 镜像存在（CentOS7 优先国内直连，避免走 docker.io）
ensure_postgresql_image() {
    if [ "$SKIP_PULL" = true ]; then
        print_info "已跳过镜像拉取 (--skip-pull)"
        if ! docker image inspect "$PG_IMAGE" >/dev/null 2>&1; then
            print_error "本地不存在镜像 ${PG_IMAGE}，请去掉 --skip-pull 或手动 docker pull"
            exit 1
        fi
        return 0
    fi

    print_section "拉取 PostgreSQL 镜像 (${PG_IMAGE})"

    if docker image inspect "$PG_IMAGE" >/dev/null 2>&1; then
        print_success "镜像已存在: ${PG_IMAGE}"
        return 0
    fi

    if is_docker_too_old "$(get_docker_server_version)"; then
        print_error "Docker 版本过旧，请先升级: sudo $0 --upgrade-docker"
        exit 1
    fi

    export DOCKER_CONTENT_TRUST=0

    # 国内直连优先（需 docker-ce 20+，1.13 会 missing signature key）
    local mirrors=(
        "docker.m.daocloud.io/library/postgres:18"
        "docker.m.daocloud.io/postgres:18"
    )

    local pulled=false
    for img in "${mirrors[@]}"; do
        if _pull_from_registry "$img"; then
            pulled=true
            break
        fi
    done

    # 最后才尝试 docker.io（部分环境 registry-mirror 仅对此生效）
    if [ "$pulled" != true ]; then
        print_warning "国内镜像站直连均失败，最后尝试 docker pull ${PG_IMAGE} ..."
        print_info "（若仍显示 docker.io/library/postgres，说明正在走 Docker Hub 或 registry-mirror 代理）"
        set +e
        if DOCKER_CONTENT_TRUST=0 docker pull "$PG_IMAGE"; then
            pulled=true
        fi
        set -e
    fi

    if [ "$pulled" = true ] && docker image inspect "$PG_IMAGE" >/dev/null 2>&1; then
        print_success "PostgreSQL 镜像就绪: ${PG_IMAGE}"
        docker images "$PG_IMAGE" --format '  {{.Repository}}:{{.Tag}}  {{.Size}}' 2>/dev/null || \
            docker images | grep -E 'postgres\s+18|postgres\s+latest' || true
        return 0
    fi

    print_error "无法拉取 PostgreSQL 镜像"
    if is_docker_too_old "$(get_docker_server_version)"; then
        print_info "若报 missing signature key，请升级 Docker: sudo $0 --upgrade-docker"
    fi
    print_info "或手动: docker pull ${PG_IMAGE}"
    exit 1
}

check_required_files() {
    local missing=0
    for f in postgresql-entrypoint.sh init-databases.sh; do
        if [ ! -f "$f" ]; then
            print_error "缺少必需文件: $SCRIPT_DIR/$f"
            missing=1
        fi
    done
    if [ ! -d "../postgresql" ]; then
        print_warning "目录 ../postgresql 不存在，将创建空目录（跳过 SQL 初始化脚本挂载内容）"
        mkdir -p "../postgresql"
    fi
    if [ "$missing" -eq 1 ]; then
        exit 1
    fi
}

create_data_dirs() {
    local data_dir="${SCRIPT_DIR}/db_data/data"
    local log_dir="${SCRIPT_DIR}/db_data/log"

    print_info "准备数据目录 db_data/{data,log}..."
    mkdir -p "$data_dir" "$log_dir"

    # 仅设挂载点顶层 777，绝不递归（与 docker-compose.yml 的 PGDATA=.../pgdata 子目录一致，
    # 递归会把 pgdata 设成 777 导致 postgres 拒绝启动；属主由 chown -R 修正即可）
    if [ "$EUID" -eq 0 ]; then
        chown -R 999:999 "$data_dir" "$log_dir"
        chmod 777 "$data_dir" "$log_dir"
        print_success "数据目录权限已设置 (999:999)"
    elif command -v sudo >/dev/null 2>&1; then
        if sudo chown -R 999:999 "$data_dir" "$log_dir" 2>/dev/null; then
            sudo chmod 777 "$data_dir" "$log_dir" 2>/dev/null || true
            print_success "数据目录权限已设置 (999:999)"
        else
            print_warning "无法设置目录属主，将依赖 PostgresSQL-init 容器修复权限"
        fi
    else
        print_warning "非 root 且无法 sudo，将依赖 PostgresSQL-init 容器修复权限"
    fi
}

check_port_conflict() {
    print_info "检查端口 ${PG_PORT} 是否被占用..."
    local pid=""
    if command -v ss >/dev/null 2>&1; then
        pid=$(ss -lptn "sport = :${PG_PORT}" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -1 || true)
    elif command -v netstat >/dev/null 2>&1; then
        pid=$(netstat -tlnp 2>/dev/null | grep ":${PG_PORT} " | awk '{print $7}' | cut -d'/' -f1 | head -1 || true)
    fi

    if [ -n "$pid" ] && [ "$pid" != "-" ]; then
        if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
            print_info "端口 ${PG_PORT} 已由 ${CONTAINER_NAME} 占用"
            return 0
        fi
        print_warning "端口 ${PG_PORT} 被宿主机进程 PID=${pid} 占用"
        print_info "可执行: sudo ./restart_postgresql.sh 或停止占用进程后再试"
        return 1
    fi
    print_success "端口 ${PG_PORT} 可用"
}

run_init_container() {
    print_section "运行 PostgreSQL 权限初始化 (${SERVICE_INIT})"
    $COMPOSE_CMD -f "$COMPOSE_FILE" up --no-deps "$SERVICE_INIT"
    if docker ps -a --filter "name=${INIT_CONTAINER}" --format '{{.Status}}' | grep -q "Exited (0)"; then
        print_success "权限初始化完成"
    else
        print_warning "初始化容器未以 0 退出，请检查: docker logs ${INIT_CONTAINER}"
    fi
    docker rm -f "$INIT_CONTAINER" 2>/dev/null || true
}

start_postgresql() {
    print_section "启动 PostgreSQL (${SERVICE_PG})"

    if [ "$RUN_INIT" = true ]; then
        run_init_container
    fi

    $COMPOSE_CMD -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE_PG"
    print_success "已执行: $COMPOSE_CMD up -d --no-deps $SERVICE_PG"
}

wait_for_postgresql() {
    if [ "$WAIT_READY" = false ]; then
        return 0
    fi

    print_info "等待 PostgreSQL 就绪（最多 60 秒）..."
    local attempt=0
    local max_attempts=30
    while [ "$attempt" -lt "$max_attempts" ]; do
        if docker exec "$CONTAINER_NAME" pg_isready -U postgres >/dev/null 2>&1; then
            print_success "PostgreSQL 已就绪"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 2
    done

    print_warning "健康检查超时，容器可能仍在初始化"
    print_info "查看日志: docker logs ${CONTAINER_NAME}"
    return 1
}

show_connection_info() {
    print_section "连接信息"
    echo "  容器名:   ${CONTAINER_NAME}"
    echo "  地址:     127.0.0.1:${PG_PORT}"
    echo "  用户:     postgres"
    echo "  密码:     <由 POSTGRES_PASSWORD 安全注入，不输出>"
    echo "  数据库:   postgres"
    echo ""
    print_info "常用命令:"
    echo "  docker ps | grep ${CONTAINER_NAME}"
    echo "  docker logs -f ${CONTAINER_NAME}"
    echo "  docker exec -it ${CONTAINER_NAME} psql -U postgres -d postgres"
    echo "  ./test_postgresql_connection.sh"
}

stop_postgresql() {
    print_section "停止 PostgreSQL"
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker stop "$CONTAINER_NAME"
        print_success "容器已停止"
    else
        $COMPOSE_CMD -f "$COMPOSE_FILE" stop "$SERVICE_PG" 2>/dev/null || true
        print_info "容器未在运行"
    fi
}

show_status() {
    print_section "PostgreSQL 状态"
    docker ps -a --filter "name=${CONTAINER_NAME}" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' || true
    echo ""
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        if docker exec "$CONTAINER_NAME" pg_isready -U postgres 2>/dev/null; then
            print_success "pg_isready: 正常"
        else
            print_warning "pg_isready: 未就绪"
        fi
    fi
}

main() {
    parse_args "$@"

    case "$ACTION" in
        stop)
            check_docker
            resolve_compose_cmd
            stop_postgresql
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
            stop_postgresql
            sleep 2
            ACTION="start"
            ;;
    esac

    print_section "CentOS 7.9 PostgreSQL 独立启动"
    check_centos7
    check_docker
    ensure_modern_docker
    configure_docker_mirror || print_warning "镜像源配置未完成，将尝试直连国内镜像拉取"
    resolve_compose_cmd
    check_compose_file
    check_required_files
    ensure_postgresql_image
    ensure_network
    create_data_dirs
    check_port_conflict || print_warning "端口冲突可能导致启动失败，继续尝试..."

    start_postgresql
    wait_for_postgresql || true
    show_connection_info
    print_success "PostgreSQL 独立启动流程完成"
}

main "$@"
