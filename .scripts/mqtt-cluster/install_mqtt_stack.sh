#!/usr/bin/env bash
# yFeiEye MQTT 网关节点 — EMQX 一键部署
# 若服务已在运行且健康检查通过则自动跳过。
#
# 用法（在目标 MQTT 网关节点上）:
#   export MQTT_NODE_HOST=10.0.0.31 MQTT_AUTH_HOST=10.0.0.1 MQTT_AUTH_PORT=8090
#   bash install_mqtt_stack.sh
set -euo pipefail

MQTT_CLUSTER_ROOT="${MQTT_CLUSTER_ROOT:-/opt/easyaiot/mqtt-cluster}"
MQTT_NODE_NAME="${MQTT_NODE_NAME:-mqtt-node}"
MQTT_NODE_HOST="${MQTT_NODE_HOST:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
MQTT_AUTH_HOST="${MQTT_AUTH_HOST:-127.0.0.1}"
MQTT_AUTH_PORT="${MQTT_AUTH_PORT:-8090}"
MQTT_AUTH_PATH="${MQTT_AUTH_PATH:-/mqtt/auth}"
MQTT_TCP_PORT="${MQTT_TCP_PORT:-1883}"
MQTT_SSL_PORT="${MQTT_SSL_PORT:-8883}"
MQTT_WS_PORT="${MQTT_WS_PORT:-8083}"
MQTT_WSS_PORT="${MQTT_WSS_PORT:-8084}"
EMQX_DASHBOARD_PORT="${EMQX_DASHBOARD_PORT:-18083}"
EMQX_NODE_COOKIE="${EMQX_NODE_COOKIE:-}"
EMQX_CLUSTER_SEEDS="${EMQX_CLUSTER_SEEDS:-}"
EMQX_DASHBOARD_USER="${EMQX_DASHBOARD_USER:-admin}"
EMQX_DASHBOARD_PASSWORD="${EMQX_DASHBOARD_PASSWORD:-}"
EMQX_IMAGE="${EMQX_IMAGE:-emqx/emqx:5.8.7}"
EMQX_IMAGE_TAR="${EMQX_IMAGE_TAR:-emqx-emqx-5.8.7.tar}"

print_step() { echo ">>> $*"; }
print_ok() { echo "[OK] $*"; }
print_skip() { echo "[SKIP] $*"; }
print_err() { echo "[ERROR] $*" >&2; }

require_secret() {
  local name="$1" value="$2" min_length="$3"
  if [[ ${#value} -lt ${min_length} ]]; then
    print_err "${name} must contain at least ${min_length} characters"
    exit 1
  fi
  if [[ ! "${value}" =~ ^[A-Za-z0-9._~-]+$ ]]; then
    print_err "${name} must contain only URL-safe characters"
    exit 1
  fi
}

require_secret "EMQX_NODE_COOKIE" "${EMQX_NODE_COOKIE}" 24
require_secret "EMQX_DASHBOARD_PASSWORD" "${EMQX_DASHBOARD_PASSWORD}" 16

load_offline_image() {
  local canonical="$1"
  local tar_path="$2"

  if [[ ! -f "${tar_path}" ]]; then
    print_err "未找到离线镜像包: ${tar_path}"
    echo "请确认：① 本机已执行 export_mqtt_images.sh 导出 ② iot-node 已更新并重新部署 ③ 同步步骤已上传 images/*.tar"
    return 1
  fi

  if docker image inspect "${canonical}" >/dev/null 2>&1; then
    print_ok "镜像已存在: ${canonical}（离线包 ${tar_path} 已就绪）"
    return 0
  fi

  print_step "从离线包导入: ${tar_path}"
  local load_out load_rc
  set +e
  load_out=$(docker load -i "${tar_path}" 2>&1)
  load_rc=$?
  set -e
  if [[ "${load_rc}" -ne 0 ]]; then
    print_err "离线导入失败: ${tar_path}"
    [[ -n "${load_out}" ]] && echo "${load_out}"
    return 1
  fi
  [[ -n "${load_out}" ]] && echo "${load_out}"

  if docker image inspect "${canonical}" >/dev/null 2>&1; then
    print_ok "离线镜像就绪: ${canonical}"
    return 0
  fi

  local loaded=""
  loaded=$(echo "${load_out}" | sed -n 's/^Loaded image: //p' | tail -1)
  if [[ -n "${loaded}" ]]; then
    docker tag "${loaded}" "${canonical}" 2>/dev/null || true
    if docker image inspect "${canonical}" >/dev/null 2>&1; then
      print_ok "离线镜像已导入并标记为: ${canonical}"
      return 0
    fi
  fi

  print_err "离线包已加载但未找到目标镜像 ${canonical}"
  return 1
}

ensure_mqtt_images() {
  local images_dir="${MQTT_CLUSTER_ROOT}/images"
  local emqx_tar="${images_dir}/${EMQX_IMAGE_TAR}"

  print_step "导入离线 Docker 镜像（目标机不联网拉取，须已同步 images/*.tar）"
  if [[ ! -f "${emqx_tar}" ]]; then
    print_err "缺少离线镜像包: ${emqx_tar}"
    echo "请在本机导出镜像并确认 iot-node 服务已更新（需含「同步离线镜像」步骤），再重新部署"
    exit 1
  fi
  load_offline_image "${EMQX_IMAGE}" "${emqx_tar}" || exit 1
  print_ok "EMQX 镜像已就绪（离线导入）"
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    print_err "未安装 Docker，请先安装 Docker Engine 并加入 docker 组"
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    print_err "Docker 未运行或当前用户无权限（可尝试 sudo 或 usermod -aG docker \$USER）"
    exit 1
  fi
}

resolve_compose_cmd() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  elif [[ -x /usr/local/bin/docker-compose ]]; then
    COMPOSE_CMD="/usr/local/bin/docker-compose"
  else
    print_err "未找到 docker compose 或 docker-compose"
    echo "请安装 Docker Compose 插件或独立包"
    exit 1
  fi
}

ensure_mqtt_cluster() {
  if [[ ! -f "${MQTT_CLUSTER_ROOT}/docker-compose.mqtt-node.yml" ]]; then
    print_err "未找到 ${MQTT_CLUSTER_ROOT}/docker-compose.mqtt-node.yml"
    echo "请先将仓库 .scripts/mqtt-cluster 同步到目标机，例如:"
    echo "  rsync -avz .scripts/mqtt-cluster/ root@${MQTT_NODE_HOST:-<目标IP>}:/opt/easyaiot/mqtt-cluster/"
    exit 1
  fi
}

emqx_healthy() {
  /opt/emqx/bin/emqx ctl status >/dev/null 2>&1 && return 0
  docker exec "${MQTT_NODE_ID}" /opt/emqx/bin/emqx ctl status >/dev/null 2>&1 && return 0
  # Dashboard / API status（host 网络）
  local body
  body=$(curl -sf --connect-timeout 5 --max-time 10 "http://127.0.0.1:${EMQX_DASHBOARD_PORT}/api/v5/status" 2>/dev/null || true)
  [[ -n "${body}" ]] && echo "${body}" | grep -qiE 'running|ok|started'
}

build_cluster_seeds() {
  local self_seed="emqx@${MQTT_NODE_HOST}"
  if [[ -z "${EMQX_CLUSTER_SEEDS}" ]]; then
    echo "${self_seed}"
    return 0
  fi
  # 确保自身在 seeds 列表中
  if echo ",${EMQX_CLUSTER_SEEDS}," | grep -q ",${self_seed},"; then
    echo "${EMQX_CLUSTER_SEEDS}"
  else
    echo "${EMQX_CLUSTER_SEEDS},${self_seed}"
  fi
}

compose_up() {
  (
    cd "${MQTT_CLUSTER_ROOT}"
    # shellcheck disable=SC2086
    ${COMPOSE_CMD} -f docker-compose.mqtt-node.yml up -d emqx
  )
}

deploy_emqx() {
  if [[ -z "${MQTT_FAIL_IF_RUNNING:-}" ]] && emqx_healthy; then
    print_skip "EMQX 已在运行（Dashboard ${EMQX_DASHBOARD_PORT} / ctl status），跳过部署"
    return 0
  fi
  if [[ -n "${MQTT_FAIL_IF_RUNNING:-}" ]] && emqx_healthy; then
    print_err "目标机 EMQX 已在运行，自动部署已中止（请先手动停止现有服务）"
    exit 1
  fi

  local seeds
  seeds="$(build_cluster_seeds)"
  export MQTT_NODE_ID="${MQTT_NODE_NAME}-emqx"
  export EMQX_NAME="emqx"
  export EMQX_HOST="${MQTT_NODE_HOST}"
  export EMQX_NODE_NAME="emqx@${MQTT_NODE_HOST}"
  export EMQX_NODE_COOKIE
  export EMQX_CLUSTER_DISCOVERY="static"
  export EMQX_CLUSTER_SEEDS="${seeds}"
  export EMQX_DASHBOARD_USER EMQX_DASHBOARD_PASSWORD
  export MQTT_AUTH_URL="http://${MQTT_AUTH_HOST}:${MQTT_AUTH_PORT}${MQTT_AUTH_PATH}"

  print_step "启动 EMQX 容器（节点 ${EMQX_NODE_NAME}，集群 seeds: ${seeds}）"
  print_step "HTTP 认证: ${MQTT_AUTH_URL}"
  compose_up

  local i=0
  while [[ $i -lt 45 ]]; do
    if emqx_healthy; then
      print_ok "EMQX 已就绪 (MQTT ${MQTT_TCP_PORT}, WS ${MQTT_WS_PORT}, Dashboard ${EMQX_DASHBOARD_PORT})"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  print_err "EMQX 启动超时，请检查: docker logs ${MQTT_NODE_ID}"
  exit 1
}

main() {
  echo "========================================"
  echo " yFeiEye MQTT 网关部署 — ${MQTT_NODE_NAME} @ ${MQTT_NODE_HOST}"
  echo "========================================"
  require_docker
  resolve_compose_cmd
  print_ok "Compose 命令: ${COMPOSE_CMD}"
  ensure_mqtt_cluster
  if [[ -z "${MQTT_DEPLOY_SERVICES_ONLY:-}" ]]; then
    ensure_mqtt_images
  fi
  if [[ -n "${MQTT_PREPARE_IMAGES_ONLY:-}" ]]; then
    exit 0
  fi
  deploy_emqx
  echo ""
  print_ok "MQTT 网关部署完成。可在平台「MQTT 网关」页管理 EMQX 集群。"
  echo "  MQTT TCP:  mqtt://${MQTT_NODE_HOST}:${MQTT_TCP_PORT}"
  echo "  Dashboard: http://${MQTT_NODE_HOST}:${EMQX_DASHBOARD_PORT}"
  echo "  Auth URL:  http://${MQTT_AUTH_HOST}:${MQTT_AUTH_PORT}${MQTT_AUTH_PATH}"
}

main "$@"
