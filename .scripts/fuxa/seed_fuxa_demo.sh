#!/usr/bin/env bash
# 将 yFeiEye 高质量组态演示工程导入运行中的 FUXA
# 用法：bash .scripts/fuxa/seed_fuxa_demo.sh
#
# 依赖：FUXA 已启动（默认 http://127.0.0.1:1881），账号 admin/123456
# 工程文件：.scripts/fuxa/easyaiot_scada_demo.fuxap（含 4 个中文画面）

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FUXAP="${ROOT_DIR}/.scripts/fuxa/easyaiot_scada_demo.fuxap"
FUXA_URL="${FUXA_URL:-http://127.0.0.1:1881}"
FUXA_USER="${FUXA_USER:-admin}"
FUXA_PASS="${FUXA_PASS:-123456}"

if [[ ! -f "${FUXAP}" ]]; then
  echo "[ERROR] 找不到组态工程: ${FUXAP}"
  exit 1
fi

echo "[INFO] FUXA: ${FUXA_URL}"
echo "[INFO] 工程: ${FUXAP}"

if ! curl -sf -o /dev/null "${FUXA_URL}/"; then
  echo "[ERROR] FUXA 不可达，请先启动中间件 FUXA 服务（端口 1881）"
  exit 1
fi

TOKEN="$(curl -sf -X POST "${FUXA_URL}/api/signin" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${FUXA_USER}\",\"password\":\"${FUXA_PASS}\"}" \
  | python3 -c 'import sys,json; print(json.load(sys.stdin)["data"]["token"])')"

if [[ -z "${TOKEN}" ]]; then
  echo "[ERROR] FUXA 登录失败（默认 admin/123456）"
  exit 1
fi

HTTP_CODE="$(curl -s -o /tmp/fuxa_seed_resp.txt -w '%{http_code}' -X POST "${FUXA_URL}/api/project" \
  -H "Content-Type: application/json" \
  -H "x-access-token: ${TOKEN}" \
  --data-binary @"${FUXAP}")"

if [[ "${HTTP_CODE}" != "200" && "${HTTP_CODE}" != "204" ]]; then
  echo "[ERROR] 导入失败 HTTP ${HTTP_CODE}"
  cat /tmp/fuxa_seed_resp.txt 2>/dev/null || true
  exit 1
fi

echo "[OK] 组态演示工程已导入 FUXA"
echo "     画面：水厂工艺总貌 / 产线运行看板 / 厂区管网组态 / 配电室电力监视"
echo "     运行态：${FUXA_URL}/home"
echo "     编辑器：${FUXA_URL}/editor（生产公网 nginx 已禁 /editor，请直连容器内网改图）"
echo "     建议同步导入平台种子：bash .scripts/go-view/seed_visualize_demo.sh"
