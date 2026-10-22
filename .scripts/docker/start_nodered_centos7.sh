#!/bin/bash

# ============================================
# CentOS 7.9 单独部署 Node-RED 容器脚本
# ============================================
# 仅启动 docker-compose.yml 中的 NodeRED 服务（不启动其他中间件）
#
# 使用方法：
#   cd .scripts/docker
#   chmod +x start_nodered_centos7.sh
#   sudo ./start_nodered_centos7.sh
#
# 选项：
#   -h, --help          显示帮助
#   -f, --force         跳过 CentOS 7 系统检查
#   --stop              停止 Node-RED 容器
#   --restart           重启 Node-RED 容器
#   --status            查看容器与健康状态
#   --logs              跟踪容器日志（Ctrl+C 退出）
#   --no-wait           启动后不等待健康检查
#   --skip-mirror       跳过配置 Docker 国内镜像源
#   --skip-pull         跳过拉取镜像
#   --no-upgrade-docker 检测到过旧 Docker 时不自动升级
#   --upgrade-docker    强制升级 Docker CE（需 root）
#   --seed              启动成功后导入/恢复演示规则链（覆盖演示页签）
#   --seed-only         仅导入/恢复演示规则链（容器需已运行，不重启）
#
# 默认访问信息（与 docker-compose.yml 一致）：
#   编辑器:  http://127.0.0.1:1880  （页面标题 yFeiEye）
#   数据目录: .scripts/docker/nodered_data/data
#   演示工程: .scripts/node-red/easyaiot_flows_demo.json
#
# 演示只读说明：
#   - 平台前端禁演示项编辑/删除；settings.js 中间件禁 PUT/DELETE 并 Deploy 回填锁定副本
#   - 公网 nginx 对 /flow/easyaiot_demo_* 仅允许 GET
#   - 本脚本直连容器 :1880，可用 --seed / --seed-only 整包恢复演示链路
# ============================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

COMPOSE_FILE="docker-compose.yml"
SERVICE_NODERED="NodeRED"
CONTAINER_NAME="nodered-server"
NODERED_PORT=1880
NODERED_SETTINGS="${SCRIPT_DIR}/../node-red/settings.js"
NODERED_FLOWS_DEMO="${SCRIPT_DIR}/../node-red/easyaiot_flows_demo.json"
NODERED_PUBLIC_DIR="${SCRIPT_DIR}/../node-red/public"
SEED_SCRIPT="${SCRIPT_DIR}/../node-red/seed_nodered_demo.sh"
# 记录外部环境是否已指定（优先于 compose / 默认值）
[ -n "${NODERED_IMAGE+x}" ] && _NODERED_IMAGE_FROM_ENV=1 || _NODERED_IMAGE_FROM_ENV=
NODERED_IMAGE="${NODERED_IMAGE:-nodered/node-red:5.0.9}"
NODERED_IMAGE_ALIAS="${NODERED_IMAGE_ALIAS:-nodered/node-red:5.0.9}"
DOCKER_MIRROR="${DOCKER_MIRROR:-https://docker.1panel.live/}"

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
C7_SERVICE_LABEL="Node-RED"
C7_FIREWALL_PORTS="${NODERED_PORT}"
C7_PULL_TAG_VARS="NODERED_IMAGE NODERED_IMAGE_ALIAS"
# shellcheck source=start_centos7_common.sh
source "${SCRIPT_DIR}/start_centos7_common.sh"

show_help() {
    cat <<'EOF'
CentOS 7.9 单独部署 Node-RED（流程编排 / 低代码 · yFeiEye）

用法:
  ./start_nodered_centos7.sh [选项]

选项:
  -h, --help          显示此帮助
  -f, --force         跳过 CentOS 7 系统检查
  --stop              停止 Node-RED 容器
  --restart           重启 Node-RED 容器
  --status            查看容器状态
  --logs              跟踪容器日志
  --no-wait           启动后不等待健康检查
  --skip-mirror       跳过配置 Docker 国内镜像源
  --skip-pull         跳过拉取镜像
  --no-upgrade-docker 不自动升级过旧 Docker
  --upgrade-docker    强制升级 Docker CE（需 root）
  --seed              启动成功后导入/恢复演示规则链（覆盖演示页签）
  --seed-only         仅恢复演示规则链（容器已运行时用；数据被改删后推荐）

环境变量:
  NODERED_IMAGE       覆盖镜像（默认与 docker-compose.yml 一致：nodered/node-red:5.0.9）
  DOCKER_MIRROR       镜像加速器地址
  NODERED_URL         --seed/--seed-only 时覆盖导入地址（默认 http://127.0.0.1:1880）

示例:
  sudo ./start_nodered_centos7.sh              # 首次部署推荐 root
  sudo ./start_nodered_centos7.sh --seed       # 启动并导入演示规则链
  sudo ./start_nodered_centos7.sh --seed-only  # 仅恢复被改坏的演示规则链
  ./start_nodered_centos7.sh --status
  ./start_nodered_centos7.sh --restart
  ./start_nodered_centos7.sh --logs

演示只读:
  平台禁演示项改删；settings.js 禁 PUT/DELETE 并 Deploy 回填锁定副本；公网 nginx 禁写 easyaiot_demo_*。
  宿主机直连本容器可运维改流；恢复演示请用 --seed / --seed-only。
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

resolve_nodered_image_from_compose() {
    # 外部环境已指定 NODERED_IMAGE 时不覆盖
    if [ -n "${_NODERED_IMAGE_FROM_ENV:-}" ]; then
        print_info "Node-RED 镜像（环境变量）: ${NODERED_IMAGE}"
        return 0
    fi

    local img=""

    img=$($COMPOSE_CMD -f "$COMPOSE_FILE" config 2>/dev/null | awk '
        $1 == "NodeRED:" { svc=1; next }
        svc && $1 == "image:" { print $2; exit }
        svc && $1 ~ /^[A-Za-z]/ && $1 != "image:" { svc=0 }
    ')

    if [ -z "$img" ]; then
        img=$(grep -A20 '^  NodeRED:' "$COMPOSE_FILE" | grep 'image:' | head -1 | \
            sed 's/.*image:[[:space:]]*//;s/["'\'']//g')
        case "$img" in
            \$\{*) img="" ;;
        esac
    fi

    if [ -n "$img" ]; then
        NODERED_IMAGE="$img"
        NODERED_IMAGE_ALIAS="$img"
    fi
    print_info "Node-RED 镜像: ${NODERED_IMAGE}"
}

ensure_nodered_image() {
    if [ "$SKIP_PULL" = true ]; then
        docker image inspect "$NODERED_IMAGE" >/dev/null 2>&1 || \
            docker image inspect "$NODERED_IMAGE_ALIAS" >/dev/null 2>&1 || {
            print_error "本地不存在镜像 ${NODERED_IMAGE}"
            exit 1
        }
        print_info "已跳过镜像拉取 (--skip-pull)"
        return 0
    fi

    print_section "拉取 Node-RED 镜像 (${NODERED_IMAGE})"

    if docker image inspect "$NODERED_IMAGE" >/dev/null 2>&1; then
        print_success "镜像已存在: ${NODERED_IMAGE}"
        docker tag "$NODERED_IMAGE" "$NODERED_IMAGE_ALIAS" 2>/dev/null || true
        return 0
    fi

    if is_docker_too_old "$(get_docker_server_version)"; then
        print_error "Docker 版本过旧，请先升级: sudo $0 --upgrade-docker"
        exit 1
    fi

    export DOCKER_CONTENT_TRUST=0

    # 官方路径（去掉已有 registry 前缀），便于走国内代理
    local official_path="nodered/node-red:latest"
    case "$NODERED_IMAGE" in
        nodered/node-red|nodered/node-red:*)
            official_path="$NODERED_IMAGE"
            [[ "$official_path" != *:* ]] && official_path="nodered/node-red:latest"
            ;;
        */nodered/node-red|*/nodered/node-red:*)
            official_path="nodered/node-red:${NODERED_IMAGE##*:}"
            [[ "$NODERED_IMAGE" != *:* ]] && official_path="nodered/node-red:latest"
            ;;
    esac

    local candidates=(
        "${NODERED_IMAGE}"
        "docker.1panel.live/${official_path}"
        "docker.1ms.run/${official_path}"
        "docker.m.daocloud.io/${official_path}"
    )

    # 若已 source 公共脚本，优先走回退链
    if declare -f docker_pull_with_mirror_fallback >/dev/null 2>&1; then
        set +e
        docker_pull_with_mirror_fallback "$NODERED_IMAGE"
        local mirror_rc=$?
        set -e
        if [ "$mirror_rc" -eq 0 ] && docker image inspect "$NODERED_IMAGE" >/dev/null 2>&1; then
            docker tag "$NODERED_IMAGE" "$NODERED_IMAGE_ALIAS" 2>/dev/null || true
            print_success "Node-RED 镜像就绪: ${NODERED_IMAGE}"
            return 0
        fi
    fi

    local pulled=false src
    for src in "${candidates[@]}"; do
        if _pull_from_registry "$src"; then
            pulled=true
            break
        fi
    done

    if [ "$pulled" = true ] && docker image inspect "$NODERED_IMAGE" >/dev/null 2>&1; then
        print_success "Node-RED 镜像就绪: ${NODERED_IMAGE}"
        return 0
    fi

    print_error "无法拉取 Node-RED 镜像"
    print_info "可手动: docker pull docker.1panel.live/${official_path}"
    print_info "或: docker pull docker.m.daocloud.io/${official_path}"
    exit 1
}

check_required_files() {
    local missing=0

    if [ ! -f "$NODERED_SETTINGS" ]; then
        print_warning "未找到 yFeiEye settings: ${NODERED_SETTINGS}"
        print_info "compose 挂载 ../node-red/settings.js（标题 yFeiEye + 演示只读中间件）"
        missing=1
    else
        print_success "settings.js 存在（页面标题 yFeiEye）"
    fi

    if [ ! -f "$NODERED_FLOWS_DEMO" ]; then
        print_warning "未找到演示工程: ${NODERED_FLOWS_DEMO}"
        print_info "compose 挂载 ../node-red/easyaiot_flows_demo.json；可用 --seed 导入"
        missing=1
    else
        print_success "演示工程存在: easyaiot_flows_demo.json"
    fi

    if [ ! -d "$NODERED_PUBLIC_DIR" ] || [ ! -f "${NODERED_PUBLIC_DIR}/easyaiot-demo-guard.js" ]; then
        print_warning "未找到编辑器只读脚本: ${NODERED_PUBLIC_DIR}/easyaiot-demo-guard.js"
        missing=1
    else
        print_success "演示只读脚本存在: easyaiot-demo-guard.js"
    fi

    if [ "$missing" -ne 0 ]; then
        print_warning "部分 yFeiEye Node-RED 资源缺失，容器仍可启动；请从仓库恢复 .scripts/node-red/"
    fi
}

create_data_dirs() {
    local nodered_data_dir="${SCRIPT_DIR}/nodered_data/data"

    print_info "准备数据目录 nodered_data/data..."
    mkdir -p "$nodered_data_dir"

    local ok=true
    if [ "$EUID" -eq 0 ]; then
        chown -R 1000:1000 "$nodered_data_dir" 2>/dev/null || true
        chmod -R 777 "$nodered_data_dir" 2>/dev/null || true
    elif command -v sudo >/dev/null 2>&1; then
        if ! sudo chown -R 1000:1000 "$nodered_data_dir" 2>/dev/null; then
            ok=false
        fi
        sudo chmod -R 777 "$nodered_data_dir" 2>/dev/null || true
    else
        chmod -R 777 "$nodered_data_dir" 2>/dev/null || ok=false
    fi

    if [ "$ok" = true ]; then
        print_success "Node-RED 数据目录权限已设置 (UID 1000:1000, 777)"
    else
        print_warning "目录权限设置失败，请手动: sudo chown -R 1000:1000 ${nodered_data_dir} && sudo chmod -R 777 ${nodered_data_dir}"
    fi
}

check_ports() {
    print_info "检查端口 ${NODERED_PORT}..."
    check_port_available "$NODERED_PORT" || return 1
}

start_nodered() {
    print_section "启动 Node-RED (${SERVICE_NODERED})"
    $COMPOSE_CMD -f "$COMPOSE_FILE" up -d --no-deps "$SERVICE_NODERED"
    print_success "已执行: $COMPOSE_CMD up -d --no-deps ${SERVICE_NODERED}"
}

wait_for_nodered() {
    if [ "$WAIT_READY" = false ]; then
        return 0
    fi

    print_info "等待 Node-RED 就绪（最多 90 秒，start_period=30s）..."
    local attempt=0
    while [ "$attempt" -lt 30 ]; do
        if curl -sf "http://127.0.0.1:${NODERED_PORT}/" >/dev/null 2>&1; then
            print_success "Node-RED 健康检查通过"
            return 0
        fi
        if docker exec "$CONTAINER_NAME" curl -sf "http://localhost:1880/" >/dev/null 2>&1; then
            print_success "Node-RED 容器内健康检查通过"
            return 0
        fi
        attempt=$((attempt + 1))
        sleep 3
    done

    print_warning "健康检查超时，容器可能仍在启动"
    print_info "查看日志: docker logs ${CONTAINER_NAME}"
    return 1
}

seed_nodered_demo() {
    if [ "$SEED_DEMO" != true ]; then
        return 0
    fi

    if [ ! -f "$SEED_SCRIPT" ]; then
        print_warning "未找到 seed_nodered_demo.sh，跳过演示规则链导入"
        return 0
    fi

    # 直连本机容器端口（绕过公网 nginx 只读），可恢复被改删的演示规则链
    local seed_url="${NODERED_URL:-http://127.0.0.1:${NODERED_PORT}}"
    print_section "导入/恢复 yFeiEye Node-RED 演示规则链"
    print_info "目标: ${seed_url}（将覆盖 4 条演示页签，保留其它自定义流）"
    chmod +x "$SEED_SCRIPT" 2>/dev/null || true
    set +e
    NODERED_URL="${seed_url}" \
      NODERED_DATA_DIR="${SCRIPT_DIR}/nodered_data/data" \
      bash "$SEED_SCRIPT"
    local rc=$?
    set -e
    if [ "$rc" -eq 0 ]; then
        print_success "演示规则链导入/恢复完成（4 条只读）"
        print_info "页面标题/只读中间件依赖 settings.js 挂载；若未生效可: docker restart ${CONTAINER_NAME}"
    else
        print_warning "演示规则链导入失败 (exit ${rc})，可稍后: NODERED_URL=${seed_url} bash ${SEED_SCRIPT}"
    fi
}

show_connection_info() {
    print_section "Node-RED 访问信息"
    echo "  容器名:   ${CONTAINER_NAME}"
    echo "  编辑器:   http://127.0.0.1:${NODERED_PORT}  （标题 yFeiEye）"
    echo "  镜像:     ${NODERED_IMAGE}"
    echo "  数据目录: ${SCRIPT_DIR}/nodered_data/data"
    echo "  演示工程: ${SCRIPT_DIR}/../node-red/easyaiot_flows_demo.json"
    echo ""
    print_info "演示只读: 平台禁改删；settings.js 禁写演示页签；公网 nginx 禁写 easyaiot_demo_*"
    print_info "恢复演示: ./start_nodered_centos7.sh --seed-only"
    echo ""
    print_info "常用命令:"
    echo "  docker ps | grep ${CONTAINER_NAME}"
    echo "  docker logs -f ${CONTAINER_NAME}"
    echo "  ./start_nodered_centos7.sh --status"
    echo "  ./start_nodered_centos7.sh --stop"
    echo "  ./start_nodered_centos7.sh --seed-only"
}

stop_nodered() {
    print_section "停止 Node-RED"
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        docker stop "$CONTAINER_NAME"
        print_success "容器已停止"
    else
        $COMPOSE_CMD -f "$COMPOSE_FILE" stop "$SERVICE_NODERED" 2>/dev/null || true
        print_info "容器未在运行"
    fi
}

show_status() {
    print_section "Node-RED 状态"
    docker ps -a --filter "name=${CONTAINER_NAME}" --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' || true
    echo ""
    if curl -sf "http://127.0.0.1:${NODERED_PORT}/" >/dev/null 2>&1; then
        print_success "健康检查: 正常 (http://127.0.0.1:${NODERED_PORT}/)"
    else
        print_warning "健康检查: 未就绪"
    fi
    if docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
        print_info "镜像: $(docker inspect -f '{{.Config.Image}}' "$CONTAINER_NAME" 2>/dev/null || echo 未知)"
    fi
}

show_logs() {
    print_section "Node-RED 日志 (Ctrl+C 退出)"
    docker logs -f --tail 200 "$CONTAINER_NAME"
}

main() {
    parse_args "$@"

    case "$ACTION" in
        stop)
            check_docker
            resolve_compose_cmd
            stop_nodered
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
            print_section "仅恢复 Node-RED 演示规则链"
            if ! docker ps --filter "name=${CONTAINER_NAME}" --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
                print_error "容器 ${CONTAINER_NAME} 未运行，请先: sudo $0  或  sudo $0 --seed"
                exit 1
            fi
            wait_for_nodered || true
            seed_nodered_demo
            show_connection_info
            print_success "演示规则链恢复流程完成"
            exit 0
            ;;
        restart)
            check_docker
            resolve_compose_cmd
            check_compose_file
            stop_nodered
            sleep 2
            ACTION="start"
            ;;
    esac

    print_section "CentOS 7.9 Node-RED 独立部署"
    check_centos7
    check_docker
    ensure_modern_docker
    ensure_docker_mirror || print_warning "镜像源配置未完成，将尝试直连拉取"
    resolve_compose_cmd
    check_compose_file
    check_required_files
    resolve_nodered_image_from_compose
    ensure_nodered_image
    ensure_network
    create_data_dirs
    check_ports || print_warning "端口冲突可能导致启动失败，继续尝试..."

    start_nodered
    wait_for_nodered || true
    seed_nodered_demo
    show_connection_info
    print_success "Node-RED 独立部署流程完成"
}

main "$@"
