#!/usr/bin/env bash
# VIDEO 生产环境启动：强制使用 .env.prod，清除 shell 中残留的旧中间件地址
set -euo pipefail
cd "$(dirname "$0")"

unset DATABASE_URL NACOS_SERVER KAFKA_BOOTSTRAP_SERVERS MINIO_ENDPOINT MILVUS_URI 2>/dev/null || true

PYTHON="${PYTHON:-python}"
if command -v conda >/dev/null 2>&1; then
  PYTHON="conda run -n VIDEO --no-capture-output python"
fi

# --server=waitress：生产 HTTP 服务器，不受 .env.prod 里 VIDEO_HTTP_SERVER 影响
exec $PYTHON run.py --env=prod --server=waitress "$@"
