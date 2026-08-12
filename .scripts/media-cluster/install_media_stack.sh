#!/usr/bin/env bash
# yFeiEye 媒体节点 — SRS + ZLMediaKit 一键部署
# 参考 docker-compose.media-node.yml；若服务已在运行且健康检查通过则自动跳过。
#
# 用法（在目标媒体节点上）:
#   export MEDIA_NODE_HOST=10.0.0.11 MEDIA_HOOK_HOST=10.0.0.1 MEDIA_HOOK_PORT=6000 YFEIEYE_SRS_HOOK_TOKEN=<32+-url-safe>
#   bash install_media_stack.sh
#
# 或一行执行（控制台「添加节点」会生成带变量的完整脚本）:
#   curl -fsSL ... | bash
set -euo pipefail

MEDIA_CLUSTER_ROOT="${MEDIA_CLUSTER_ROOT:-/opt/easyaiot/media-cluster}"
MEDIA_NODE_NAME="${MEDIA_NODE_NAME:-media-node}"
MEDIA_NODE_HOST="${MEDIA_NODE_HOST:-$(hostname -I 2>/dev/null | awk '{print $1}')}"
MEDIA_HOOK_HOST="${MEDIA_HOOK_HOST:-127.0.0.1}"
MEDIA_HOOK_PORT="${MEDIA_HOOK_PORT:-6000}"
# Internal hooks connect directly to VIDEO; do not route them through public nginx/Gateway.
MEDIA_HOOK_PATH_PREFIX="${MEDIA_HOOK_PATH_PREFIX:-}"
YFEIEYE_SRS_HOOK_TOKEN="${YFEIEYE_SRS_HOOK_TOKEN:-}"
SRS_CANDIDATE_IP="${SRS_CANDIDATE_IP:-${MEDIA_NODE_HOST}}"
SRS_RTMP_PORT="${SRS_RTMP_PORT:-1935}"
SRS_HTTP_PORT="${SRS_HTTP_PORT:-8080}"
SRS_API_PORT="${SRS_API_PORT:-1985}"
SRS_RTC_PORT="${SRS_RTC_PORT:-8000}"
ZLM_HTTP_PORT="${ZLM_HTTP_PORT:-6080}"
ZLM_RTMP_PORT="${ZLM_RTMP_PORT:-10935}"
ZLM_RTSP_PORT="${ZLM_RTSP_PORT:-8554}"
ZLM_RTP_PORT_MIN="${ZLM_RTP_PORT_MIN:-30000}"
ZLM_RTP_PORT_MAX="${ZLM_RTP_PORT_MAX:-30500}"
ZLM_RTC_PORT="${ZLM_RTC_PORT:-8800}"
ZLM_RTC_EXTERN_IP="${ZLM_RTC_EXTERN_IP:-${SRS_CANDIDATE_IP:-${MEDIA_NODE_HOST}}}"
ZLM_SECRET="${ZLM_SECRET:-}"
SRS_IMAGE="${SRS_IMAGE:-ossrs/srs:5}"
ZLM_IMAGE="${ZLM_IMAGE:-zlmediakit/zlmediakit:master}"
SRS_IMAGE_TAR="${SRS_IMAGE_TAR:-ossrs-srs-5.tar}"
ZLM_IMAGE_TAR="${ZLM_IMAGE_TAR:-zlmediakit-master.tar}"

print_step() { echo ">>> $*"; }
print_ok() { echo "[OK] $*"; }
print_skip() { echo "[SKIP] $*"; }
print_err() { echo "[ERROR] $*" >&2; }

validate_hook_token() {
  if [[ ${#YFEIEYE_SRS_HOOK_TOKEN} -lt 32 ]]; then
    print_err "YFEIEYE_SRS_HOOK_TOKEN must contain at least 32 characters"
    exit 1
  fi
  if [[ ! "${YFEIEYE_SRS_HOOK_TOKEN}" =~ ^[A-Za-z0-9._~-]+$ ]]; then
    print_err "YFEIEYE_SRS_HOOK_TOKEN must contain only URL-safe characters"
    exit 1
  fi
}

validate_zlm_secret() {
  if [[ ${#ZLM_SECRET} -lt 32 ]]; then
    print_err "ZLM_SECRET must contain at least 32 characters"
    exit 1
  fi
  if [[ ! "${ZLM_SECRET}" =~ ^[A-Za-z0-9._~-]+$ ]]; then
    print_err "ZLM_SECRET must contain only URL-safe characters"
    exit 1
  fi
}

load_offline_image() {
  local canonical="$1"
  local tar_path="$2"

  if [[ ! -f "${tar_path}" ]]; then
    print_err "未找到离线镜像包: ${tar_path}"
    echo "请确认：① 本机已执行 export_media_images.sh 导出 ② iot-node 已更新并重新部署 ③ 同步步骤已上传 images/*.tar"
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

ensure_media_images() {
  local images_dir="${MEDIA_CLUSTER_ROOT}/images"
  local srs_tar="${images_dir}/${SRS_IMAGE_TAR}"
  local zlm_tar="${images_dir}/${ZLM_IMAGE_TAR}"

  print_step "导入离线 Docker 镜像（目标机不联网拉取，须已同步 images/*.tar）"
  local missing=0
  for tar in "${srs_tar}" "${zlm_tar}"; do
    if [[ ! -f "${tar}" ]]; then
      print_err "缺少离线镜像包: ${tar}"
      missing=1
    fi
  done
  if [[ "${missing}" -eq 1 ]]; then
    echo "请在本机导出镜像并确认 iot-node 服务已更新（需含「同步离线镜像」步骤），再重新部署"
    exit 1
  fi
  load_offline_image "${SRS_IMAGE}" "${srs_tar}" || exit 1
  load_offline_image "${ZLM_IMAGE}" "${zlm_tar}" || exit 1
  print_ok "SRS / ZLM 镜像均已就绪（离线导入）"
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

ensure_ceph_mount() {
  local mount_root="${MOUNT_ROOT:-/mnt/easyaiot-media}"
  if [[ "${SKIP_CEPH_CHECK:-0}" == "1" ]]; then
    print_skip "跳过 CephFS 检查（SKIP_CEPH_CHECK=1）"
    return 0
  fi
  if mountpoint -q "${mount_root}" 2>/dev/null; then
    print_ok "CephFS 已挂载: ${mount_root}"
    return 0
  fi
  if [[ -d "${mount_root}/playbacks" ]] && [[ -w "${mount_root}/playbacks" ]]; then
    print_ok "媒体缓冲目录可写: ${mount_root}/playbacks"
    return 0
  fi
  print_err "CephFS 未挂载到 ${mount_root}，请先: bash .scripts/media-cluster/ceph/mount-all.sh"
  exit 1
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
    echo "请安装 Docker Compose 插件或独立包，例如:"
    echo "  yum install -y docker-compose-plugin   # 或 docker-compose"
    echo "  apt install -y docker-compose-plugin"
    exit 1
  fi
}

assert_not_running() {
  local service="$1"
  local healthy_fn="$2"
  if [[ -z "${MEDIA_FAIL_IF_RUNNING:-}" ]]; then
    return 0
  fi
  if "${healthy_fn}"; then
    print_err "目标机 ${service} 已在运行，自动部署已中止（请先手动停止现有服务）"
    exit 1
  fi
}

compose_up() {
  local service="$1"
  (
    cd "${MEDIA_CLUSTER_ROOT}"
    # shellcheck disable=SC2086
    ${COMPOSE_CMD} -f docker-compose.media-node.yml up -d "${service}"
  )
}

compose_force_recreate() {
  local service="$1"
  (
    cd "${MEDIA_CLUSTER_ROOT}"
    # shellcheck disable=SC2086
    ${COMPOSE_CMD} -f docker-compose.media-node.yml up -d --force-recreate "${service}"
  )
}

ensure_dirs() {
  print_step "创建媒体数据目录 /mnt/easyaiot-media"
  sudo mkdir -p /mnt/easyaiot-media/playbacks /mnt/easyaiot-media/logs
  sudo chmod -R 755 /mnt/easyaiot-media
}

ensure_media_cluster() {
  if [[ ! -f "${MEDIA_CLUSTER_ROOT}/docker-compose.media-node.yml" ]]; then
    print_err "未找到 ${MEDIA_CLUSTER_ROOT}/docker-compose.media-node.yml"
    echo "请先将仓库 .scripts/media-cluster 同步到目标机，例如:"
    echo "  rsync -avz .scripts/media-cluster/ root@${MEDIA_NODE_HOST:-<目标IP>}:/opt/easyaiot/media-cluster/"
    exit 1
  fi
  if ! command -v envsubst >/dev/null 2>&1; then
    print_err "未找到 envsubst，请安装 gettext 包（如 apt install gettext-base）"
    exit 1
  fi
}

srs_healthy() {
  local body
  body=$(curl -sf --connect-timeout 5 --max-time 10 "http://127.0.0.1:${SRS_API_PORT}/api/v1/versions" 2>/dev/null || true)
  [[ -n "${body}" ]] && echo "${body}" | grep -qE '"code"[[:space:]]*:[[:space:]]*0' \
    && echo "${body}" | grep -q '"version"'
}

zlm_healthy() {
  local url="http://127.0.0.1:${ZLM_HTTP_PORT}/index/api/getServerConfig"
  local body
  if [[ -n "${ZLM_SECRET}" ]]; then
    url="${url}?secret=${ZLM_SECRET}"
  fi
  body=$(curl -sf --connect-timeout 5 --max-time 10 "${url}" 2>/dev/null || true)
  [[ -n "${body}" ]] && echo "${body}" | grep -qE '"code"[[:space:]]*:[[:space:]]*0'
}

render_srs_config() {
  local out="${MEDIA_CLUSTER_ROOT}/srs/docker.conf"
  local tmp
  tmp=$(mktemp "${out}.tmp.XXXXXX")
  SRS_CONFIG_CHANGED=0
  export MEDIA_NODE_ID="${MEDIA_NODE_NAME}-srs"
  export MEDIA_HOOK_HOST MEDIA_HOOK_PORT MEDIA_HOOK_PATH_PREFIX YFEIEYE_SRS_HOOK_TOKEN
  export SRS_CANDIDATE_IP SRS_RTC_PORT
  print_step "渲染 SRS 配置 -> ${out}"
  if ! envsubst '${MEDIA_NODE_ID} ${MEDIA_HOOK_HOST} ${MEDIA_HOOK_PORT} ${MEDIA_HOOK_PATH_PREFIX} ${YFEIEYE_SRS_HOOK_TOKEN} ${SRS_CANDIDATE_IP} ${SRS_RTC_PORT}' \
      < "${MEDIA_CLUSTER_ROOT}/srs/cluster.conf.template" \
      | sed -E \
        -e "s/^listen[[:space:]]+[0-9]+;/listen              ${SRS_RTMP_PORT};/" \
        -e "/http_server/,/}/ s/listen[[:space:]]+[0-9]+;/listen          ${SRS_HTTP_PORT};/" \
        -e "/http_api/,/}/ s/listen[[:space:]]+[0-9]+;/listen          ${SRS_API_PORT};/" \
      > "${tmp}"; then
    rm -f "${tmp}"
    return 1
  fi
  chmod 600 "${tmp}"
  if [[ -f "${out}" ]] && cmp -s "${tmp}" "${out}"; then
    rm -f "${tmp}"
  else
    mv -f "${tmp}" "${out}"
    SRS_CONFIG_CHANGED=1
  fi
  chmod 600 "${out}"
  print_ok "SRS 配置已生成"
}

render_zlm_config() {
  local out="${MEDIA_CLUSTER_ROOT}/zlm/config.ini"
  export MEDIA_NODE_ID="${MEDIA_NODE_NAME}-zlm"
  export MEDIA_HOOK_HOST MEDIA_HOOK_PORT MEDIA_HOOK_PATH_PREFIX YFEIEYE_SRS_HOOK_TOKEN ZLM_SECRET
  export ZLM_HTTP_PORT ZLM_RTMP_PORT ZLM_RTSP_PORT ZLM_RTP_PORT_MIN ZLM_RTP_PORT_MAX
  export ZLM_RTC_PORT ZLM_RTC_EXTERN_IP
  print_step "渲染 ZLM 配置 -> ${out}"
  envsubst '${MEDIA_NODE_ID} ${MEDIA_HOOK_HOST} ${MEDIA_HOOK_PORT} ${MEDIA_HOOK_PATH_PREFIX} ${YFEIEYE_SRS_HOOK_TOKEN} ${ZLM_SECRET} ${ZLM_HTTP_PORT} ${ZLM_RTMP_PORT} ${ZLM_RTSP_PORT} ${ZLM_RTP_PORT_MIN} ${ZLM_RTP_PORT_MAX} ${ZLM_RTC_PORT} ${ZLM_RTC_EXTERN_IP}' \
    < "${MEDIA_CLUSTER_ROOT}/zlm/config.ini.template" \
    > "${out}"
  chmod 600 "${out}"
  print_ok "ZLM 配置已生成"
}

deploy_srs() {
  assert_not_running "SRS" srs_healthy
  render_srs_config
  if srs_healthy && [[ "${SRS_CONFIG_CHANGED}" -eq 0 ]]; then
    print_skip "SRS 已在运行（API ${SRS_API_PORT} 可访问），跳过部署"
    return 0
  fi
  export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"
  export ZLM_HTTP_PORT
  if srs_healthy; then
    print_step "SRS 配置已更新，重建容器以使新配置生效"
    compose_force_recreate srs
  else
    print_step "启动 SRS 容器"
    compose_up srs
  fi
  local i=0
  while [[ $i -lt 30 ]]; do
    if srs_healthy; then
      print_ok "SRS 已就绪 (RTMP ${SRS_RTMP_PORT}, HTTP ${SRS_HTTP_PORT}, API ${SRS_API_PORT})"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  print_err "SRS 启动超时，请检查: docker logs ${MEDIA_NODE_NAME}-srs"
  exit 1
}

deploy_zlm() {
  assert_not_running "ZLMediaKit" zlm_healthy
  if zlm_healthy; then
    print_skip "ZLMediaKit 已在运行（HTTP ${ZLM_HTTP_PORT} 可访问），跳过部署"
    return 0
  fi
  render_zlm_config
  print_step "启动 ZLMediaKit 容器"
  export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"
  export ZLM_HTTP_PORT
  compose_up zlm
  local i=0
  while [[ $i -lt 30 ]]; do
    if zlm_healthy; then
      print_ok "ZLMediaKit 已就绪 (HTTP ${ZLM_HTTP_PORT}, RTMP ${ZLM_RTMP_PORT}, RTSP ${ZLM_RTSP_PORT})"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  print_err "ZLMediaKit 启动超时，请检查: docker logs ${MEDIA_NODE_NAME}-zlm"
  exit 1
}

main() {
  echo "========================================"
  validate_hook_token
  validate_zlm_secret
  echo " yFeiEye 媒体栈部署 — ${MEDIA_NODE_NAME} @ ${MEDIA_NODE_HOST}"
  echo "========================================"
  if [[ -n "${MEDIA_RENDER_CONFIGS_ONLY:-}" ]]; then
    render_srs_config
    render_zlm_config
    print_ok "媒体栈配置已渲染（MEDIA_RENDER_CONFIGS_ONLY）"
    exit 0
  fi
  require_docker
  ensure_ceph_mount
  resolve_compose_cmd
  print_ok "Compose 命令: ${COMPOSE_CMD}"
  ensure_dirs
  ensure_media_cluster
  if [[ -z "${MEDIA_DEPLOY_SERVICES_ONLY:-}" ]]; then
    ensure_media_images
  fi
  if [[ -n "${MEDIA_PREPARE_IMAGES_ONLY:-}" ]]; then
    exit 0
  fi
  deploy_srs
  deploy_zlm
  echo ""
  print_ok "媒体栈部署完成。请在本平台完成 Agent 纳管后，可在节点列表「更多」中管理 SRS/ZLM。"
  echo "  SRS API:  http://${MEDIA_NODE_HOST}:${SRS_API_PORT}/api/v1/versions"
  echo "  ZLM API:  http://${MEDIA_NODE_HOST}:${ZLM_HTTP_PORT}/index/api/getServerConfig"
}

main "$@"
