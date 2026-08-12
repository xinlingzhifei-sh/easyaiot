#!/usr/bin/env bash
# 将 yFeiEye 高质量 Node-RED 演示规则链导入运行中的 Node-RED
# 用法：bash .scripts/node-red/seed_nodered_demo.sh
#
# 依赖：Node-RED 已启动（默认 http://127.0.0.1:1880）
# 工程文件：.scripts/node-red/easyaiot_flows_demo.json（含 4 条中文演示链路）
#
# 生产恢复请直连容器内网（绕过公网只读策略），例如：
#   NODERED_URL=http://10.0.0.87:1880 bash .scripts/node-red/seed_nodered_demo.sh

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FLOWS_FILE="${ROOT_DIR}/.scripts/node-red/easyaiot_flows_demo.json"
SETTINGS_FILE="${ROOT_DIR}/.scripts/node-red/settings.js"
NODERED_URL="${NODERED_URL:-http://127.0.0.1:1880}"
DATA_DIR="${NODERED_DATA_DIR:-${ROOT_DIR}/.scripts/docker/nodered_data/data}"

DEMO_IDS=(
  easyaiot_demo_telemetry
  easyaiot_demo_alert
  easyaiot_demo_bridge
  easyaiot_demo_vision
)

if [[ ! -f "${FLOWS_FILE}" ]]; then
  echo "[ERROR] 找不到演示工程: ${FLOWS_FILE}"
  exit 1
fi

echo "[INFO] Node-RED: ${NODERED_URL}"
echo "[INFO] 工程: ${FLOWS_FILE}"

if ! curl -sf -o /dev/null "${NODERED_URL}/"; then
  echo "[ERROR] Node-RED 不可达，请先启动中间件 Node-RED 服务（端口 1880）"
  exit 1
fi

# 同步 settings（标题 yFeiEye + 演示只读中间件）；compose 已挂载时仍可覆盖本地 data 副本
if [[ -f "${SETTINGS_FILE}" && -d "${DATA_DIR}" ]]; then
  cp -f "${SETTINGS_FILE}" "${DATA_DIR}/settings.js"
  cp -f "${FLOWS_FILE}" "${DATA_DIR}/easyaiot_flows_demo.json"
  mkdir -p "${DATA_DIR}/public"
  cp -f "${ROOT_DIR}/.scripts/node-red/public/easyaiot-demo-guard.js" "${DATA_DIR}/public/"
  echo "[INFO] 已同步 settings.js / flows / public → ${DATA_DIR}"
fi

MERGED_FILE="$(mktemp /tmp/nodered_seed_XXXXXX.json)"
trap 'rm -f "${MERGED_FILE}"' EXIT

python3 - "${FLOWS_FILE}" "${NODERED_URL}" "${MERGED_FILE}" <<'PY'
import json, sys, urllib.request

flows_file, base, out_file = sys.argv[1], sys.argv[2].rstrip("/"), sys.argv[3]
demo_ids = {
    "easyaiot_demo_telemetry",
    "easyaiot_demo_alert",
    "easyaiot_demo_bridge",
    "easyaiot_demo_vision",
}
demo_labels = {
    "设备遥测采集链路",
    "告警分级推送链路",
    "工控协议桥接链路",
    "视觉质检联动链路",
}

with open(flows_file, "r", encoding="utf-8") as f:
    locked = json.load(f)

req = urllib.request.Request(base + "/flows", method="GET")
try:
    with urllib.request.urlopen(req, timeout=15) as resp:
        current = json.load(resp)
except Exception:
    current = []

if not isinstance(current, list):
    # API v2: {flows:[], rev:""}
    current = current.get("flows") if isinstance(current, dict) else []
    if not isinstance(current, list):
        current = []

def is_demo(n):
    if not isinstance(n, dict):
        return False
    if n.get("type") == "tab":
        return n.get("id") in demo_ids or n.get("label") in demo_labels
    if not n.get("z") and str(n.get("id", "")).startswith("demo_"):
        return True
    return n.get("z") in demo_ids

kept = [n for n in current if not is_demo(n)]
merged = kept + locked
with open(out_file, "w", encoding="utf-8") as f:
    json.dump(merged, f, ensure_ascii=False)
print(f"[INFO] merged flows: kept={len(kept)} locked={len(locked)} total={len(merged)}")
PY

HTTP_CODE="$(curl -s -o /tmp/nodered_seed_resp.txt -w '%{http_code}' -X POST "${NODERED_URL}/flows" \
  -H 'Content-Type: application/json' \
  -H 'Node-RED-Deployment-Type: full' \
  --data-binary @"${MERGED_FILE}")"

if [[ "${HTTP_CODE}" != "200" && "${HTTP_CODE}" != "204" ]]; then
  echo "[ERROR] 导入失败 HTTP ${HTTP_CODE}"
  cat /tmp/nodered_seed_resp.txt 2>/dev/null || true
  exit 1
fi

echo "[OK] Node-RED 演示规则链已导入"
echo "     链路：设备遥测采集链路 / 告警分级推送链路 / 工控协议桥接链路 / 视觉质检联动链路"
echo "     固定 ID：${DEMO_IDS[*]}"
echo "     编辑器：${NODERED_URL}/ （标题 yFeiEye；演示链路只读）"
echo "     若刚更新 settings.js，请重启容器使标题/中间件生效："
echo "       docker restart nodered-server"
