# EDGE runtime overlays

本目录保存 **不得写入 VIDEO** 的边缘专用补丁。

| 文件 | 作用 |
|------|------|
| `resolve_ai_rtmp_push_url.py.snippet` | 替换 realtime `run_deploy._resolve_ai_rtmp_push_url`，支持 `EDGE_SRS_HOST` |
| `alert_mqtt_bus.py.snippet` | `send_alert_event_async` 优先走 MQTT 告警总线 |
| `../lib/algo_mqtt_bus.py` | MQTT 发布实现 |
| `apply_overlays.py` | 对 `services/` 重新打补丁 |

种子同步后必须执行：

```bash
python3 EDGE/runtime/overlays/apply_overlays.py
# 或一并：
bash EDGE/scripts/sync_runtime_from_video.sh
```
