# yFeiEye 流媒体集群独立部署 — 完整改造方案（总览）

> **版本**：v2.0  
> **日期**：2026-06-04  
> **目标规模**：20,000 路摄像头  
> **状态**：方案设计（待评审实施）

---

## 文档体系

本方案由 **1 份总览 + 8 份专题设计 + 1 套部署模板** 组成：

| 文档 | 路径 | 内容 |
|------|------|------|
| **总览（本文）** | [STREAMING_CLUSTER_REFACTOR_PLAN_zh.md](./STREAMING_CLUSTER_REFACTOR_PLAN_zh.md) | 决策摘要、架构、里程碑 |
| 架构与网络 | [架构与网络设计.md](./架构与网络设计.md) | 拓扑、VLAN、带宽、时序图 |
| MEDIA 控制面 | [MEDIA控制面与API规范.md](./MEDIA控制面与API规范.md) | REST API、DB、Redis、Kafka |
| 存储流水线 | [存储与上传流水线.md](./存储与上传流水线.md) | GlusterFS、MinIO、Upload Worker |
| SRS/ZLM 配置 | [SRS与ZLM集群配置规范.md](./SRS与ZLM集群配置规范.md) | 节点池、Hook、转推 |
| 代码改造 | [业务层代码改造清单.md](./业务层代码改造清单.md) | VIDEO/DEVICE/AI/WEB 逐文件 |
| 部署运维 | [部署与运维手册.md](./部署与运维手册.md) | 装机、扩容、Runbook |
| 压测验收 | [压测与验收标准.md](./压测与验收标准.md) | 场景、指标、Checklist |
| 安全容灾 | [安全与容灾.md](./安全与容灾.md) | 鉴权、HA、DR |
| 索引导读 | [README.md](./README.md) | 阅读顺序 |
| **部署模板** | [.scripts/media-cluster/](./.scripts/media-cluster/) | SRS/ZLM/Nginx/GlusterFS/Kafka |

---

## 1. 执行摘要

### 1.1 背景

现网 yFeiEye 在 `.scripts/docker/docker-compose.yml` 中与业务同栈部署 **SRS**（host 网络、DVR 写 `/data/playbacks`）和 **ZLMediaKit**（GB28181 RTP）。流地址由 `VIDEO/app/services/camera_service.py` 硬编码宿主机 IP；录像由 SRS `on_dvr` 同步回调 `VIDEO/app/blueprints/camera.py` 上传 MinIO。

**无法支撑 2 万路**：单 SRS `max_connections 1000`、Hook 同步处理、本地盘绑定单机。

### 1.2 目标

1. **SRS + ZLM 独立集群**，与 VIDEO / AI / DEVICE 解耦部署、独立扩容  
2. **GlusterFS** 挂载到所有媒体节点与 Upload Worker，作为录像/抓拍热缓冲  
3. **异步上传 MinIO** 后删除 GlusterFS 副本（延续 `playback_disk_guard_service.py` 策略）  
4. 新建 **MEDIA 控制面** 统一节点调度、URL 分配、Hook 入队  
5. 支撑 **20,000 路注册、6,000 路峰值并发推流**

### 1.3 五项关键决策

| # | 决策 | 理由 |
|---|------|------|
| 1 | SRS 负责 RTMP/FLV/WebRTC 主播放面；ZLM 负责 GB28181 | 复用现网双栈，国标可转推 SRS 统一前端 |
| 2 | GlusterFS 仅缓冲，MinIO 为权威存储 | 小文件海量写入不适合 Gluster 长期归档 |
| 3 | Hook → Kafka → Upload Worker | 133 段/秒级 DVR 事件，同步 HTTP 不可行 |
| 4 | 设备 Sticky 绑定 SRS 节点 | DVR 路径稳定、播放会话一致 |
| 5 | 推流直连节点 IP，播放走 Media Edge 域名 | RTMP 四层 LB 限制多，现网已验证 host 模式 SRS |

---

## 2. 目标架构一览

```
客户端 ──► Media Edge (443/FLV/WebRTC)
              │
    ┌─────────┼─────────┬──────────────┐
    ▼         ▼         ▼              ▼
 SRS Live  SRS AI   ZLM GB      Business Cluster
  集群      集群      集群       VIDEO/AI/DEVICE/WEB
    │         │         │              │
    └─────────┴────┬────┘              │
                   ▼                    │
              GlusterFS                 │
           playbacks/snaps              │
                   │                    │
                   ▼                    │
            Upload Worker ◄── Kafka ◄── MEDIA Scheduler
                   │                    (Hook/绑定/URL)
                   ▼
                 MinIO ◄─────────────── 录像/抓拍 API 读
```

详细时序图、VLAN、端口见 [架构与网络设计.md](./架构与网络设计.md)。

---

## 3. 容量规划（20,000 路）

### 3.1 流量假设

| 维度 | 数值 |
|------|------|
| 注册设备 | 20,000 |
| 峰值同时推流 | 6,000（30%） |
| 峰值同时播放 | 2,000 |
| 常录 DVR | 4,000 路（20%） |
| 单路 live + ai 码率 | ~10 Mbps |
| 媒体层峰值带宽 | ~48 Gbps |
| DVR 分段写入 | ~130 段/秒（60s 分段） |

### 3.2 推荐集群规模

| 角色 | 数量 | 规格 |
|------|------|------|
| Media Edge | 2~4 | 8C16G，10G |
| SRS Live | 12~16 | 16C32G，10G |
| SRS AI | 8~12 | 16C32G，10G |
| ZLM GB28181 | 10~15 | 16C32G，RTP 端口池 |
| MEDIA Scheduler | 3 | 4C8G |
| Upload Worker | 6~10 | 8C16G + Gluster 客户端 |
| GlusterFS Brick | 6~9 | NVMe 缓冲 + 3 副本 |
| MinIO | 4~8 | EC，大容量 HDD |
| Kafka | 3 | 标准集群 |

完整装机表见 [部署与运维手册.md](./部署与运维手册.md)。

---

## 4. 核心数据流

### 4.1 录像（DVR）

```
SRS DVR 写 GlusterFS
  → on_dvr → MEDIA Scheduler (<50ms)
  → Kafka topic: media.dvr.completed
  → Upload Worker: stable 检测 → MinIO → Playback DB → 删除 GlusterFS
```

逻辑自现网 `on_dvr_callback` 迁移，详见 [存储与上传流水线.md](./存储与上传流水线.md)。

### 4.2 推流/播放 URL

```
设备创建 → MEDIA POST /bindings/allocate
  → 写入 Device.rtmp_stream / http_stream / ai_*
  → 推流: rtmp://{srs_node}:1935/live|ai/{device_id}
  → 播放: https://stream-play.example.com/live/{device_id}.flv
```

API 规范见 [MEDIA控制面与API规范.md](./MEDIA控制面与API规范.md)。

### 4.3 国标（GB28181）

```
WVP → MEDIA 选 zlm 节点 → SIP/RTP → ZLM
  → 可选转推 SRS live/{device_id} → WEB 统一 FLV 播放
```

---

## 5. 新建 MEDIA 模块

建议仓库路径 `MEDIA/`（Python FastAPI），职责：

- 节点注册 / 心跳 / 负载
- 设备-节点 Sticky 绑定
- 生成推流/播放 URL
- SRS/ZLM Hook 接收 → Kafka 生产
- 与 WVP `MediaServer` Redis 同步

**不负责**：MinIO 上传、转码、Playback 写库（Upload Worker + VIDEO）。

目录与 API 见 [MEDIA控制面与API规范.md](./MEDIA控制面与API规范.md)。

---

## 6. 业务改造要点

| 模块 | 核心改动 |
|------|----------|
| **VIDEO** | `camera_service` 调 MEDIA API；`on_dvr` 废弃；新建 Upload Worker |
| **DEVICE** | ZLM 动态节点；WVP Redis 与 MEDIA 同步 |
| **AI/TASK** | RTMP URL 来自 DB（MEDIA 写入），C++ 无改 |
| **WEB** | 播放 URL 仅来自 API；Nginx 改 Media Edge |
| **.scripts** | Compose 拆分；media-cluster 模板 |

完整文件级清单见 [业务层代码改造清单.md](./业务层代码改造清单.md)。

---

## 7. 部署模板（已提供）

路径 [.scripts/media-cluster/](./.scripts/media-cluster/)：

```
media-cluster/
├── docker-compose.media-node.yml   # SRS 单节点
├── srs/cluster.conf.template       # SRS 集群配置
├── srs/register.sh                 # 节点注册
├── zlm/config.ini.template         # ZLM 配置
├── zlm/register.sh
├── nginx/media-edge.conf.template  # 播放入口
├── glusterfs/volume-create.sh      # 建卷
└── glusterfs/mount-all.sh          # 客户端挂载
```

流媒体 Kafka Topic（`media.dvr.completed` 等）在控制面 Kafka 上创建，见 [存储与上传流水线.md](./存储与上传流水线.md#32-创建-topic)。

配置规范见 [SRS与ZLM集群配置规范.md](./SRS与ZLM集群配置规范.md)。

---

## 8. 分阶段实施路线

| 阶段 | 周期 | 交付物 | 验收 |
|------|------|--------|------|
| **Phase 0 准备** | 2~3 周 | GlusterFS + Kafka + MEDIA MVP | 节点注册 API 可用 |
| **Phase 1 存储解耦** | 3~4 周 | DVR 写 GlusterFS + Upload Worker hybrid | 100 路双写一致 |
| **Phase 2 集群化** | 4~6 周 | 多 SRS + Edge + Hook→Kafka | 1000 路压测通过 |
| **Phase 3 压测优化** | 4~8 周 | 6000 推流 + 2000 播放 | 指标达 [压测与验收标准](./压测与验收标准.md) |
| **Phase 4 割接** | 2~4 周 | 灰度 5%→100%，下线单机 SRS | 生产稳定 7 天 |

---

## 9. 监控告警（摘要）

| 指标 | 阈值 |
|------|------|
| SRS 单节点 active_streams | > 450 |
| GlusterFS 使用率 | > 80% |
| Kafka upload-worker lag | > 10k 或 > 5min |
| Upload P99 延迟 | > 120s |
| MEDIA 节点心跳 | 丢失 > 30s |

---

## 10. 风险与对策（摘要）

| 风险 | 对策 |
|------|------|
| GlusterFS 小文件压力 | 增大 dvr_duration；快速 Upload 删除 |
| Hook 风暴 | 仅入 Kafka，Worker 异步 |
| 热点节点 | Sticky + 负载再平衡 + 拒绝超配新绑定 |
| 国标/RTSP 双栈复杂 | ZLM 转推 SRS 统一播放 |

完整见 [安全与容灾.md](./安全与容灾.md)。

---

## 11. 现网代码索引

| 能力 | 路径 |
|------|------|
| SRS DVR / Hook | `.scripts/srs/conf/docker.conf` |
| on_dvr 上传 | `VIDEO/app/blueprints/camera.py` |
| on_publish | `VIDEO/app/blueprints/camera.py` |
| 流 URL 生成 | `VIDEO/app/services/camera_service.py` |
| 磁盘守护 | `VIDEO/app/services/playback_disk_guard_service.py` |
| 录像空间 | `VIDEO/app/services/record_space_service.py` |
| ZLM 负载均衡 | `DEVICE/.../MediaServerServiceImpl.java` |
| Compose | `.scripts/docker/docker-compose.yml` |
| Nginx SRS | `WEB/conf/nginx.conf` |

---

## 12. 下一步行动

1. **评审** 2 万路中 GB28181 / RTSP / RTMP 占比，修正 §3 节点数  
2. **PoC**：按 [部署与运维手册.md](./部署与运维手册.md) §4 部署 3 SRS + GlusterFS + Worker  
3. **立项 MEDIA 模块**（Sprint 1）  
4. **编写** `VIDEO/scripts/migrate_stream_bindings.py` 批量迁移设备 URL  
5. **压测** 按 [压测与验收标准.md](./压测与验收标准.md) 执行并填基线表  

---

*维护：架构组 / 流媒体小组 | v2.0 起完整方案见 `docs/streaming-cluster/` 与 `.scripts/media-cluster/`*
