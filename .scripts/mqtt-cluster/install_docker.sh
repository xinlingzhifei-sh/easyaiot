#!/usr/bin/env bash
# yFeiEye 媒体节点 — 非交互安装 Docker Engine + Compose 插件
# 供 iot-node 远程自动部署调用；使用华为云镜像源，无需人工确认。
set -euo pipefail

print_step() { echo ">>> $*"; }
print_ok() { echo "[OK] $*"; }
print_err() { echo "[ERROR] $*" >&2; }

run_root() {
  if [[ "${EUID}" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    print_err "安装 Docker 需要 root 权限（请使用 root 账户或配置免密 sudo）"
    exit 1
  fi
}

detect_os_id() {
  if [[ ! -f /etc/os-release ]]; then
    print_err "无法检测操作系统（缺少 /etc/os-release）"
    exit 1
  fi
  # shellcheck disable=SC1091
  . /etc/os-release
  local id="${ID:-unknown}"
  if [[ "${id}" == "kylin" || "${id}" == "neokylin" || "${id}" == "openEuler" || "${id}" == "openeuler" ]]; then
    echo "centos"
    return 0
  fi
  echo "${id}"
}

install_docker_debian() {
  local os_id="$1"
  print_step "安装 Docker（Debian/Ubuntu，华为云镜像）"
  run_root apt-get remove -y docker docker-engine docker.io containerd runc 2>/dev/null || true
  run_root apt-get update -qq
  run_root apt-get install -qq -y ca-certificates curl gnupg lsb-release
  run_root install -m 0755 -d /etc/apt/keyrings
  if ! curl -fsSL --connect-timeout 15 --max-time 60 \
      "https://mirrors.huaweicloud.com/docker-ce/linux/${os_id}/gpg" \
      | run_root gpg --dearmor -o /etc/apt/keyrings/docker.gpg 2>/dev/null; then
    print_step "华为云 GPG 失败，尝试官方源"
    curl -fsSL "https://download.docker.com/linux/${os_id}/gpg" \
      | run_root gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  fi
  run_root chmod a+r /etc/apt/keyrings/docker.gpg
  run_root tee /etc/apt/sources.list.d/docker.list >/dev/null <<EOF
deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://mirrors.huaweicloud.com/docker-ce/linux/${os_id} $(lsb_release -cs) stable
EOF
  run_root apt-get update -qq
  run_root apt-get install -qq -y \
    docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
}

install_docker_rhel() {
  print_step "安装 Docker（CentOS/RHEL 系，华为云镜像）"
  run_root yum remove -y docker docker-client docker-client-latest docker-common \
    docker-latest docker-latest-logrotate docker-logrotate docker-engine \
    docker-selinux docker-engine-selinux 2>/dev/null || true
  run_root yum install -y yum-utils device-mapper-persistent-data lvm2
  if ! run_root yum-config-manager --add-repo \
      https://mirrors.huaweicloud.com/docker-ce/linux/centos/docker-ce.repo 2>/dev/null; then
    print_step "华为云仓库失败，尝试官方源"
    run_root yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
  fi
  set +e
  run_root yum install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  local rc=$?
  set -e
  if [[ "${rc}" -ne 0 ]]; then
    print_step "带 compose-plugin 安装失败，尝试仅安装 docker-ce"
    run_root yum install -y docker-ce docker-ce-cli containerd.io
  fi
}

start_docker_service() {
  print_step "启动 Docker 服务"
  if command -v systemctl >/dev/null 2>&1; then
    run_root systemctl daemon-reload || true
    run_root systemctl enable docker || true
    run_root systemctl start docker || true
    sleep 2
  fi
}

main() {
  if docker info >/dev/null 2>&1; then
    print_ok "Docker 已可用: $(docker --version 2>/dev/null || true)"
    exit 0
  fi

  if command -v docker >/dev/null 2>&1; then
    start_docker_service
    if docker info >/dev/null 2>&1; then
      print_ok "Docker 已启动: $(docker --version 2>/dev/null || true)"
      exit 0
    fi
  fi

  local os_id
  os_id="$(detect_os_id)"
  print_step "检测到操作系统: ${os_id}"

  case "${os_id}" in
    ubuntu|debian)
      install_docker_debian "${os_id}"
      ;;
    centos|rhel|fedora|rocky|almalinux)
      install_docker_rhel
      ;;
    *)
      print_err "不支持的操作系统: ${os_id}，请手动安装 Docker 后重试"
      exit 1
      ;;
  esac

  start_docker_service

  if ! docker info >/dev/null 2>&1; then
    print_err "Docker 安装完成但无法连接，请检查: systemctl status docker"
    exit 1
  fi

  print_ok "Docker 安装完成: $(docker --version 2>/dev/null || true)"
  if docker compose version >/dev/null 2>&1; then
    print_ok "Compose 插件: $(docker compose version 2>/dev/null | head -1)"
  fi
}

main "$@"
