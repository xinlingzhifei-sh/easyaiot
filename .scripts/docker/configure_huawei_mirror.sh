#!/bin/bash
# ---------------------------------------------------------------------------
# 将 Docker registry-mirrors 切换为华为云加速器（保留 nvidia runtime 等原配置）
# 用法:
#   DOCKER_MIRROR=https://<id>.mirror.swr.myhuaweicloud.com bash .scripts/docker/configure_huawei_mirror.sh
#
# 获取加速器地址:
#   华为云控制台 → 容器镜像服务 SWR → 镜像中心 → 镜像加速器
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=docker_mirror_common.sh
source "${SCRIPT_DIR}/docker_mirror_common.sh"

print_info() { echo -e "\033[0;34m[INFO]\033[0m $1"; }
print_success() { echo -e "\033[0;32m[OK]\033[0m $1"; }
print_warning() { echo -e "\033[0;33m[WARN]\033[0m $1"; }
print_error() { echo -e "\033[0;31m[ERR]\033[0m $1" >&2; }

if [ "${EUID:-$(id -u)}" -ne 0 ]; then
    print_error "请使用 root 执行"
    exit 1
fi

if [ -z "${DOCKER_MIRROR:-}" ] || [[ "${DOCKER_MIRROR}" == *"docker.m.daocloud.io"* ]]; then
    if [ -z "${DOCKER_MIRROR:-}" ] || [ "${DOCKER_MIRROR}" = "https://docker.m.daocloud.io/" ] || [ "${DOCKER_MIRROR}" = "https://docker.m.daocloud.io" ]; then
        print_warning "未指定华为云加速器地址。"
        echo "请先到华为云 SWR 控制台复制「镜像加速器」地址，例如："
        echo "  export DOCKER_MIRROR=https://xxxx.mirror.swr.myhuaweicloud.com"
        echo "  bash $0"
        echo ""
        echo "若暂无华为云账号，可改用公共代理："
        echo "  export DOCKER_MIRROR=https://docker.1ms.run"
        echo "  bash $0"
        exit 1
    fi
fi

# 确保末尾无多余斜杠差异不影响写入
export DOCKER_MIRROR="${DOCKER_MIRROR%/}"
# 与 1ms.run 等组成多源，避免单一加速器缺镜像
export DOCKER_MIRROR_FALLBACKS="${DOCKER_MIRROR_FALLBACKS:-docker.1ms.run,docker.xuanyuan.me,hub.rat.dev}"

print_info "写入 Docker 镜像源: ${DOCKER_MIRROR}"
configure_docker_mirror

# 再合并写入多源（configure 只写单一 DOCKER_MIRROR；此处追加公共回退）
config_file="/etc/docker/daemon.json"
if command -v python3 >/dev/null 2>&1 && [ -f "$config_file" ]; then
    python3 - "$config_file" "$DOCKER_MIRROR" "$DOCKER_MIRROR_FALLBACKS" <<'PY'
import json, sys
path, primary, fallbacks = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
mirrors = [primary]
for h in fallbacks.split(","):
    h = h.strip().rstrip("/")
    if not h:
        continue
    if not h.startswith("http"):
        h = "https://" + h
    if h.rstrip("/") not in [m.rstrip("/") for m in mirrors]:
        mirrors.append(h)
cfg = json.load(open(path))
cfg["registry-mirrors"] = mirrors
json.dump(cfg, open(path, "w"), indent=2, ensure_ascii=False)
print("registry-mirrors =>", mirrors)
PY
    print_success "已合并多镜像源（华为云优先 + 公共回退）"
    apply_docker_daemon_config "华为云镜像源" || true
fi

echo ""
print_info "验证: docker info | grep -A10 'Registry Mirrors'"
print_info "拉取 FUXA: bash ${SCRIPT_DIR}/pull_fuxa.sh"
