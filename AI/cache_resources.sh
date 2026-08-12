#!/bin/bash
# 预下载 AI 模块离线 wheel → .build-cache/ai/pip-wheels
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YFEIEYE_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
exec "${YFEIEYE_ROOT}/.scripts/docker/cache_python_resources.sh" ai "$@"
