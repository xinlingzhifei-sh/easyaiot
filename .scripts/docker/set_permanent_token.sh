#!/usr/bin/env bash
set -euo pipefail

cat >&2 <<'EOF'
该脚本已禁用：历史实现包含固定 Redis 凭据，并可直接修改登录 Token。
请先轮换已暴露凭据，再通过受鉴权、可审计的运维流程执行 Token 轮换。
EOF
exit 1