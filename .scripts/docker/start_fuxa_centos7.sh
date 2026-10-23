#!/bin/bash

# ============================================
# CentOS 7.9 单独部署 FUXA 容器脚本
# ============================================
# 仅启动 docker-compose.yml 中的 FUXA 服务（不启动其他中间件）
#
# 使用方法：
#   cd .scripts/docker
#   chmod +x start_fuxa_centos7.sh
#   sudo ./start_fuxa_centos7.sh
#
# 选项：
#   -h, --help          显示帮助
#   -f, --force         跳过 CentOS 7 系统检查
#   --stop              停止 FUXA 容器
#   --restart           重启 FUXA 容器
#   --status            查看容器与健康状态
#   --logs              跟踪容器日志（Ctrl+C 退出）
#   --no-wait           启动后不等待健康检查
#   --skip-mirror       跳过配置 Docker 国内镜像源
#   --skip-pull         跳过拉取镜像
#   --no-upgrade-docker 检测到过旧 Docker 时不自动升级
#   --upgrade-docker    强制升级 Docker CE（需 root）
#   --seed              启动成功后导入/恢复演示组态工程（覆盖当前工程）
#   --seed-only         仅导入/恢复演示工程（容器需已运行，不重启）
#
# 默认访问信息（与 docker-compose.yml 一致）：
#   运行态:  http://127.0.0.1:1881/home
#   编辑器:  http://127.0.0.1:1881/editor   # 宿主机直连可改图；公网应由 WEB nginx 禁 /editor
#   SSO:     http://127.0.0.1:1881/easyaiot-sso.html  # 已含演示画面只读（拒绝进 editor）
#   账号:    admin / 123456（请在生产环境修改）
#
# 演示只读说明：
#   - 平台/SSO 对 4 套演示画面强制预览；公网 nginx.prod-server.conf 禁 /editor 与工程写入
#   - 本脚本直连容器 :1881，运维可用 /editor 改图，或 --seed / --seed-only 整包恢复
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.yml"
SERVICE_FUXA="FUXA"
CONTAINER_NAME="fuxa-server"
FUXA_PORT=1881
# 记录外部环境是否已指定（优先于 .env.docker）
[ -n "${FUXA_IMAGE+x}" ] && _FUXA_IMAGE_FROM_ENV=1 || _FUXA_IMAGE_FROM_ENV=
[ -n "${FUXA_TAG+x}" ] && _FUXA_TAG_FROM_ENV=1 || _FUXA_TAG_FROM_ENV=
FUXA_TAG="${FUXA_TAG:-1.3.3}"
FUXA_IMAGE="${FUXA_IMAGE:-docker.1panel.live/frangoteam/fuxa:${FUXA_TAG}}"
FUXA_IMAGE_ALIAS="${FUXA_IMAGE_ALIAS:-frangoteam/fuxa:${FUXA_TAG}}"
SSO_HTML="${SCRIPT_DIR}/../fuxa/easyaiot-sso.html"
SEED_SCRIPT="${SCRIPT_DIR}/../fuxa/seed_fuxa_demo.sh"
# FUXA 专用：与通用中间件不同，优先 1ms（DaoCloud 对 frangoteam/fuxa 常 403）
DOCKER_MIRROR="${DOCKER_MIRROR:-https://docker.1ms.run/}"

FORCE_OS_CHECK=false
WAIT_READY=true
SKIP_MIRROR=false
SKIP_PULL=false
SKIP_DOCKER_UPGRADE=false
FORCE_DOCKER_UPGRADE=false
SEED_DEMO=false
MIN_DOCKER_MAJOR=20
ACTION="start"

# 公共逻辑见 start_centos7_common.sh（统一网络名取自 deploy-profiles.json）
C7_SERVICE_LABEL="FUXA"
C7_FIREWALL_PORTS="${FUXA_PORT}"
C7_PULL_TAG_VARS="FUXA_IMAGE FUXA_IMAGE_ALIAS"
# shellcheck source=start_centos7_common.sh
source "${SCRIPT_DIR}/start_centos7_common.sh"

show_help() {
    cat <<'EOF'
CentOS 7.9 单独部署 FUXA（Web SCADA / HMI 组态）

用法:
  ./start_fuxa_centos7.sh [选项]

选项:
  -h, --help          显示此帮助
  -f, --force         跳过 CentOS 7 系统检查
  --stop              停止 FUXA 容器
  --restart           重启 FUXA 容器
  --status            查看容器状态
  --logs              跟踪容器日志
  --no-wait           启动后不等待健康检查
  --skip-mirror       跳过配置 Docker 国内镜像源
  --skip-pull         跳过拉取镜像
  --no-upgrade-docker 不自动升级过旧 Docker
  --upgrade-docker    强制升级 Docker CE（需 root）
  --seed              启动成功后导入/恢复演示组态工程（覆盖当前工程）
  --seed-only         仅恢复演示工程（容器已运行时用；数据被改删后推荐）

环境变量:
  FUXA_IMAGE / FUXA_TAG   覆盖镜像（默认 docker.1panel.live/frangoteam/fuxa:1.3.3；拉取优先 1ms）
  DOCKER_MIRROR           镜像加速器地址
  FUXA_URL                --seed/--seed-only 时覆盖导入地址（默认 http://127.0.0.1:1881）

示例:
  sudo ./start_fuxa_centos7.sh              # 首次部署推荐 root
  ./start_fuxa_centos7.sh --status
  ./start_fuxa_centos7.sh --restart
  sudo ./start_fuxa_centos7.sh --seed       # 启动并导入演示画面
  sudo ./start_fuxa_centos7.sh --seed-only  # 仅恢复被改坏的演示工艺图
  ./start_fuxa_centos7.sh --logs

演示只读:
  SSO 桥接页已拒绝演示画面进编辑器；公网请用 WEB nginx 禁 /editor。
  宿主机直连本容器仍可改图（运维用途）；恢复演示请用 --seed / --seed-only。
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
            --logs) ACTION="logs"; shift ;;
            --no-wait) WAIT_READY=false; shift ;;
            --skip-mirror) SKIP_MIRROR=true; shift ;;
            --skip-pull) SKIP_PULL=true; shift ;;
            --no-upgrade-docker) SKIP_DOCKER_UPGRADE=true; shift ;;
            --upgrade-docker) FORCE_DOCKER_UPGRADE=true; shift ;;
            --seed) SEED_DEMO=true; shift ;;
            --seed-only) ACTION="seed-only"; SEED_DEMO=true; shift ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
}

load_env_file() {
    local env_file="${SCRIPT_DIR}/.env.docker"
    [ -f "$env_file" ] || return 0

    # 仅读取 FUXA_IMAGE / FUXA_TAG；外部环境已 export 的变量优先
    local line key val
    while IFS= read -r line || [ -n "$line" ]; do
        [[ "$line" =~ ^[[:space:]]*# ]] && continue
        [[ "$line" =~ ^[[:space:]]*$ ]] && continue
        key="${line%%=*}"
        val="${line#*=}"
        key="$(echo "$key" | tr -d '[:space:]')"
        val="${val%\"}"
        val="${val#\"}"
        val="${val%\'}"
        val="${val#\'}"
        case "$key" in
            FUXA_IMAGE)
                # 仅当未通过外部环境覆盖时采用文件值
                if [ -z "${_FUXA_IMAGE_FROM_ENV:-}" ]; then
                    FUXA_IMAGE="$val"
                fi
                ;;
            FUXA_TAG)
                if [ -z "${_FUXA_TAG_FROM_ENV:-}" ]; then
                    FUXA_TAG="$val"
                    FUXA_IMAGE_ALIAS="frangoteam/fuxa:${FUXA_TAG}"
                fi
                ;;
        esac
    done < "$env_file"
}

resolve_fuxa_image_from_compose() {
    local img=""

    # compose config 会展开 ${FUXA_IMAGE:-...}
    img=$($COMPOSE_CMD -f "$COMPOSE_FILE" config 2>/dev/null | awk '
        $1 == "FUXA:" { svc=1; next }
        svc && $1 == "image:" { print $2; exit }
        svc && $1 ~ /^[A-Za-z]/ && $1 != "image:" { svc=0 }
    ')

    # 兼容 CentOS7 旧 awk：从 yml 解析默认值
    if [ -z "$img" ]; then
        img=$(grep -A25 '^  FUXA:' "$COMPOSE_FILE" | grep 'image:' | head -1 | \
            sed -n 's/.*:-\([^}]*\)}.*/\1/p')
    fi
    if [ -z "$img" ]; then
        img=$(grep -A25 '^  FUXA:' "$COMPOSE_FILE" | grep 'image:' | head -1 | \
            sed 's/.*image:[[:space:]]*//;s/["'\'']//g')
        # 去掉未展开的 ${VAR} 占位
        case "$img" in
            \$\{*) img="" ;;
        esac
    fi

    if [ -n "$img" ]; then
        FUXA_IMAGE="$img"
    fi
    print_info "FUXA 镜像: ${FUXA_IMAGE}"
}

ensure_fuxa_image() {
    if [ "$SKIP_PULL" = true ]; then
        docker image inspect "$FUXA_IMAGE" >/dev/null 2>&1 || \
            docker image inspect "$FUXA_IMAGE_ALIAS" >/dev/null 2>&1 || {
            print_error "本地不存在镜像 ${FUXA_IMAGE}"
            exit 1
        }
        print_info "已跳过镜像拉取 (--skip-pull)"
        return 0
    fi

    print_section "拉取 FUXA 镜像 (${FUXA_IMAGE})"

    if docker image inspect "$FUXA_IMAGE" >/dev/null 2>&1; then
        print_success "镜像已存在: ${FUXA_IMAGE}"
        docker tag "$FUXA_IMAGE" "$FUXA_IMAGE_ALIAS" 2>/dev/null || true
        return 0
    fi

    if is_docker_too_old "$(get_docker_server_version)"; then
        print_error "Docker 版本过旧，请先升级: sudo $0 --upgrade-docker"
        exit 1
    fi

    export DOCKER_CONTENT_TRUST=0

    # 优先复用专用拉取脚本（多源回退）
    if [ -f "${SCRIPT_DIR}/pull_fuxa.sh" ]; then
        print_info "调用 pull_fuxa.sh 拉取..."
        set +e
        FUXA_TAG="${FUXA_TAG}" FUXA_IMAGE_LOCAL="${FUXA_IMAGE}" \
            bash "${SCRIPT_DIR}/pull_fuxa.sh"
        local pull_rc=$?
        set -e
        if [ "$pull_rc" -eq 0 ] && docker image inspect "$FUXA_IMAGE" >/dev/null 2>&1; then
            docker tag "$FUXA_IMAGE" "$FUXA_IMAGE_ALIAS" 2>/dev/null || true
            print_success "FUXA 镜像就绪: ${FUXA_IMAGE}"
            return 0
        fi
    fi

    # 回退：直连常见代理（与 pull_fuxa.sh 顺序一致：1ms 优先）
    local candidates=(
        "docker.1ms.run/frangoteam/fuxa:${FUXA_TAG}"
        "docker.1panel.live/frangoteam/fuxa:${FUXA_TAG}"
        "docker.m.daocloud.io/frangoteam/fuxa:${FUXA_TAG}"
        "frangoteam/fuxa:${FUXA_TAG}"
        "${FUXA_IMAGE}"
    )

    local pulled=false src
    for src in "${candidates[@]}"; do
        print_info "尝试拉取: ${src}"
        set +e
        DOCKER_CONTENT_TRUST=0 docker pull "$src"
        local rc=$?
        set -e
        if [ "$rc" -eq 0 ]; then
            docker tag "$src" "$FUXA_IMAGE" 2>/dev/null || true
            docker tag "$src" "$FUXA_IMAGE_ALIAS" 2>/dev/null || true
            pulled=true
            break
        fi
    done

    if [ "$pulled" = true ] && docker image inspect "$FUXA_IMAGE" >/dev/null 2>&1; then
        print_success "FUXA 镜像就绪: ${FUXA_IMAGE}"
        return 0
    fi

    print_error "无法拉取 FUXA 镜像"
    print_info "可手动: bash ${SCRIPT_DIR}/pull_fuxa.sh"
    print_info "或: docker pull docker.1ms.run/frangoteam/fuxa:${FUXA_TAG} && docker tag docker.1ms.run/frangoteam/fuxa:${FUXA_TAG} ${FUXA_IMAGE}"
    exit 1
}

check_required_files() {
    if [ ! -f "$SSO_HTML" ]; then
        print_warning "未找到 SSO 桥接页: ${SSO_HTML}"
        print_info "compose 挂载 ../fuxa/easyaiot-sso.html，缺失会导致免登跳转 404"
        print_info "可从仓库恢复该文件后重启容器"
    else
        print_success "SSO 桥接页存在: easyaiot-sso.html"
    fi
}

create_data_dirs() {
    local fuxa_root="${SCRIPT_DIR}/fuxa_data"
    local dirs=(
        "${fuxa_root}/appdata"
        "${fuxa_root}/db"
        "${fuxa_root}/logs"
        "${fuxa_root}/images"
    )

    print_info "准备数据目录 fuxa_data/{appdata,db,logs,images}..."
    mkdir -p "${dirs[@]}"

    local ok=true
    local d
    for d in "${dirs[@]}"; do
        if [ "$EUID" -eq 0 ]; then
            chown -R 1000:1000 "$d" 2>/dev/null || true
            chmod -R 777 "$d" 2>/dev/null || true
        elif command -v sudo >/dev/null 2>&1; then
            if ! sudo chown -R 1000:1000 "$d" 2>/dev/null; then
                ok=false
            fi
            sudo chmod -R 777 "$d" 2>/dev/null || true
        else
            chmod -R 777 "$d" 2>/dev/null || ok=false
        fi
    done

    if [ "$ok" = true ]; then
        print_success "FUXA 数据目录权限已设置 (UID 1000:1000, 777)"
    else
        print_warning "部分目录权限设置失败，请手动: sudo chmod -R 777 ${fuxa_root}"
    fi
}

check_ports() {
    print_info "检查端口 ${FUXA_PORT}..."
    check_port_available "$FUXA_PORT" || return 1
}

start_fuxa() {
    print_section "启动 FUXA (${SERVICE_FUXA})"
    # 导出镜像变量，使 compose 使用刚拉取的标签
    export FUXA_IMAGE
    $COMPOSE_CMD -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE_FUXA"
    print_success "已执行: $COMPOSE_CMD up -d --no-deps ${SERVICE_FUXA}"
}

wait_for_fuxa() {
    if [ "$WAIT_READY" = false ]; then
        return 0
    fi

    print_info "等待 FUXA 就绪（最多 120 秒，start_period=60s）..."
    local attempt=0
    while [ "$attempt" -lt 40 ]; do
        if curl -sf "http://127.0.0.1:${FUXA_PORT}/" >/dev/null 2>&1; then
            print_success "FUXA 健康检查通过"
            return 0
        fi
        # 官方镜像无 curl，用 node 探测（与 compose healthcheck 一致）
        if docker exec "$CONTAINER_NAME" node -e \
            "require('http').get('http://127.0.0.1:1881/',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))" \
            >/dev/null 2>&1; then
            print_success "FUXA 容器内健康检查通过"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 3
    done

    print_warning "健康检查超时，容器可能仍在启动"
    print_info "查看日志: docker logs ${CONTAINER_NAME}"
    return 1
}

seed_fuxa_demo() {
    if [ "$SEED_DEMO" != true ]; then
        return 0
    fi

    if [ ! -f "$SEED_SCRIPT" ]; then
        print_warning "未找到 seed_fuxa_demo.sh，跳过演示工程导入"
        return 0
    fi

    # 直连本机容器端口（绕过公网 nginx 只读），可恢复被改删的演示工艺图
    local seed_url="${FUXA_URL:-http://127.0.0.1:${FUXA_PORT}}"
    print_section "导入/恢复 FUXA 演示组态工程"
    print_info "目标: ${seed_url}（将覆盖当前 FUXA 工程）"
    chmod +x "$SEED_SCRIPT" 2>/dev/null || true
    set +e
    FUXA_URL="${seed_url}" bash "$SEED_SCRIPT"
    local rc=$?
    set -e
    if [ "$rc" -eq 0 ]; then
        print_success "演示组态工程导入/恢复完成（4 套中文画面）"
        print_info "平台侧元数据可同步: bash ${SCRIPT_DIR}/../go-view/seed_visualize_demo.sh"
    else
        print_warning "演示工程导入失败 (exit ${rc})，可稍后: FUXA_URL=${seed_url} bash ${SEED_SCRIPT}"
    fi
}

show_connection_info() {
    print_section "FUXA 访问信息"
    echo "  容器名:   ${CONTAINER_NAME}"
    echo "  运行态:   http://127.0.0.1:${FUXA_PORT}/home"
    echo "  编辑器:   http://127.0.0.1:${FUXA_PORT}/editor  (宿主机直连/运维改图)"
    echo "  SSO 桥接: http://127.0.0.1:${FUXA_PORT}/easyaiot-sso.html  (演示画面只读)"
    echo "  账号:     admin / 123456"
    echo "  数据目录: ${SCRIPT_DIR}/fuxa_data/{appdata,db,logs,images}"
    echo ""
    print_info "演示只读: 平台/SSO 禁演示进编辑器；公网请配合 WEB nginx 禁 /editor"
    print_info "恢复演示: ./start_fuxa_centos7.sh --seed-only"
    echo ""
    print_info "常用命令:"
    echo "  docker ps | grep ${CONTAINER_NAME}"
    echo "  docker logs -f ${CONTAINER_NAME}"
    echo "  ./start_fuxa_centos7.sh --status"
    echo "  ./start_fuxa_centos7.sh --stop"
    echo "  ./start_fuxa_centos7.sh --seed-only"
}

stop_fuxa() {
    print_section "停止 FUXA"
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker stop "$CONTAINER_NAME"
        print_success "容器已停止"
    else
        $COMPOSE_CMD -f "$COMPOSE_FILE" stop "$SERVICE_FUXA" 2>/dev/null || true
        print_info "容器未在运行"
    fi
}

show_status() {
    print_section "FUXA 状态"
    docker ps -a --filter "name=${CONTAINER_NAME}" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' || true
    echo ""
    if curl -sf "http://127.0.0.1:${FUXA_PORT}/" >/dev/null 2>&1; then
        print_success "健康检查: 正常 (http://127.0.0.1:${FUXA_PORT}/)"
    else
        print_warning "健康检查: 未就绪"
    fi
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        print_info "镜像: $(docker inspect -f '{{.Config.Image}}' "$CONTAINER_NAME" 2>/dev/null || echo 未知)"
    fi
}

show_logs() {
    print_section "FUXA 日志 (Ctrl+C 退出)"
    docker logs -f --tail 200 "$CONTAINER_NAME"
}

main() {
    parse_args "$@"
    load_env_file

    case "$ACTION" in
        stop)
            check_docker
            resolve_compose_cmd
            stop_fuxa
            exit 0
            ;;
        status)
            check_docker
            show_status
            exit 0
            ;;
        logs)
            check_docker
            show_logs
            exit 0
            ;;
        seed-only)
            check_docker
            print_section "仅恢复 FUXA 演示工程"
            if ! docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
                print_error "容器 ${CONTAINER_NAME} 未运行，请先: sudo $0  或  sudo $0 --seed"
                exit 1
            fi
            wait_for_fuxa || true
            seed_fuxa_demo
            show_connection_info
            print_success "演示工程恢复流程完成"
            exit 0
            ;;
        restart)
            check_docker
            resolve_compose_cmd
            check_compose_file
            stop_fuxa
            sleep 2
            ACTION="start"
            ;;
    esac

    print_section "CentOS 7.9 FUXA 独立部署"
    check_centos7
    check_docker
    ensure_modern_docker
    ensure_docker_mirror || print_warning "镜像源配置未完成，将尝试直连拉取"
    resolve_compose_cmd
    check_compose_file
    check_required_files
    resolve_fuxa_image_from_compose
    ensure_fuxa_image
    ensure_network
    create_data_dirs
    check_ports || print_warning "端口冲突可能导致启动失败，继续尝试..."

    start_fuxa
    wait_for_fuxa || true
    seed_fuxa_demo
    show_connection_info
    print_success "FUXA 独立部署流程完成"
}

main "$@"
