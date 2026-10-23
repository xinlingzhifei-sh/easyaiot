#!/usr/bin/env bash
# 在本机（平台服务器）拉取并导出 SRS / ZLM 镜像为离线 tar，再同步至目标机 docker load。
# 控制台自动部署会在本机缺失 tar 时自动执行本脚本。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGES_DIR="${ROOT}/images"
SRS_IMAGE="${SRS_IMAGE:-ossrs/srs:5}"
ZLM_IMAGE="${ZLM_IMAGE:-zlmediakit/zlmediakit:master}"
# 本地仍以 :master 命名（install_media_stack.sh / iot-node 按此名检查），内容按 digest 锁定，
# 与 .scripts/docker/docker-compose.yml 的 ZLMediaKit 一致
ZLM_DIGEST="${ZLM_DIGEST:-sha256:ddc5127d4fab7be622b34148ac8869ca6310298acd33440f1236e771a3c5ceee}"
SRS_TAR="${IMAGES_DIR}/ossrs-srs-5.tar"
ZLM_TAR="${IMAGES_DIR}/zlmediakit-master.tar"

print_step() { echo ">>> $*"; }
print_ok() { echo "[OK] $*"; }
print_err() { echo "[ERROR] $*" >&2; }

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    print_err "未安装 Docker"
    exit 1
  fi
  if ! docker info >/dev/null 2>&1; then
    print_err "Docker 未运行或无权限"
    exit 1
  fi
}

ensure_image() {
  local canonical="$1"
  shift
  local mirrors=("$@")

  if docker image inspect "${canonical}" >/dev/null 2>&1; then
    print_ok "本地已有镜像: ${canonical}"
    return 0
  fi

  export DOCKER_CONTENT_TRUST=0
  local img
  for img in "${mirrors[@]}"; do
    print_step "拉取镜像: ${img}"
    if docker pull "${img}"; then
      docker tag "${img}" "${canonical}" 2>/dev/null || true
      print_ok "镜像就绪: ${canonical}"
      return 0
    fi
  done

  print_step "尝试拉取: ${canonical}"
  docker pull "${canonical}"
}

save_image() {
  local canonical="$1"
  local tar_path="$2"

  mkdir -p "${IMAGES_DIR}"
  if [[ -f "${tar_path}" && -s "${tar_path}" ]]; then
    print_ok "离线包已存在，跳过导出: ${tar_path} ($(du -h "${tar_path}" | awk '{print $1}'))"
    return 0
  fi
  print_step "导出 ${canonical} -> ${tar_path}"
  docker save -o "${tar_path}" "${canonical}"
  print_ok "已导出 $(du -h "${tar_path}" | awk '{print $1}') ${tar_path}"
}

main() {
  require_docker
  print_step "准备离线镜像包 -> ${IMAGES_DIR}"

  ensure_image "${SRS_IMAGE}" \
    "docker.m.daocloud.io/ossrs/srs:5" \
    "registry.cn-hangzhou.aliyuncs.com/ossrs/srs:5"

  ensure_image "${ZLM_IMAGE}" \
    "docker.m.daocloud.io/zlmediakit/zlmediakit@${ZLM_DIGEST}" \
    "zlmediakit/zlmediakit@${ZLM_DIGEST}"

  save_image "${SRS_IMAGE}" "${SRS_TAR}"
  save_image "${ZLM_IMAGE}" "${ZLM_TAR}"

  echo ""
  print_ok "离线镜像包已就绪，请通过文件同步部署至目标机"
}

main "$@"
