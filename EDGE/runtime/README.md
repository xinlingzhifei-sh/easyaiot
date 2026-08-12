# EDGE/runtime — 边缘算法执行包（EDGE 自有）

本目录是边缘侧 **唯一** 推理入口，由 `edge.workload_runner` 拉起。  
**与 VIDEO 源码树运行时解耦**：VIDEO 不包含 `EDGE_*` 推流逻辑；边缘推流/解析只维护在此处。

## 目录

```
runtime/
  services/
    realtime_algorithm_service/run_deploy.py   # 含 EDGE_SRS_HOST 推流解析（EDGE overlay）
    snapshot_algorithm_service/
    patrol_algorithm_service/
  overlays/
    resolve_ai_rtmp_push_url.py.snippet         # EDGE 自有补丁片段
    apply_overlays.py                          # 同步后重新打补丁
```

## 可选：从 VIDEO 种子刷新

仅在需要对齐算法主体时执行（会覆盖 `services/` 后再自动应用 overlays）：

```bash
# 仓库根目录
bash EDGE/scripts/sync_runtime_from_video.sh
```

脚本流程：拷贝 VIDEO/services → `EDGE/runtime/services` → 运行 `overlays/apply_overlays.py`。  
**不会修改 VIDEO 源码。** 日常演进以本目录为准；VIDEO 只是可选种子源，不是运行时依赖。

## 推流目标（set-srs）

边缘现场：

```bash
python -m edge config set-srs --host <SRS主机> --rtmp-port 1935 --http-port 8080 --api-port 1985
```

写入 `EDGE_SRS_*` 后，由 MQTT runtime 注入任务进程；`realtime` 的 `_resolve_ai_rtmp_push_url`（EDGE overlay）拼出：

`rtmp://{EDGE_SRS_HOST}:{port}/ai/{deviceId}`

## 约束

- `ALGO_MEDIA_REF_MODE=shared_fs`：图写 Ceph，不 MinIO 同步上传  
- `ALGO_BUS_TRANSPORT=mqtt`：事件走 EMQX  
- 无 Flask 管理面、无本地业务库职责  
