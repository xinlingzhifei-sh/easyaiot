# yFeiEye 流媒体集群独立部署 — 完整方案文档索引

> **版本**：v2.0 | **日期**：2026-06-04 | **目标**：20,000 路摄像头

本目录为 [STREAMING_CLUSTER_REFACTOR_PLAN_zh.md](./STREAMING_CLUSTER_REFACTOR_PLAN_zh.md) 的完整展开，涵盖架构、API、存储、部署、改造、压测、运维全部细节。

## 文档清单

| 文档 | 内容 |
|------|------|
| [总览与决策](./STREAMING_CLUSTER_REFACTOR_PLAN_zh.md) | 执行摘要、现状、目标架构、里程碑 |
| [架构与网络设计](./架构与网络设计.md) | 物理/逻辑拓扑、VLAN、DNS、端口、时序图 |
| [MEDIA 控制面与 API 规范](./MEDIA控制面与API规范.md) | 服务设计、REST API、DB/Redis/Kafka、与 WVP 集成 |
| [存储与上传流水线](./存储与上传流水线.md) | GlusterFS、MinIO、Upload Worker、Janitor |
| [SRS 与 ZLM 集群配置规范](./SRS与ZLM集群配置规范.md) | 节点池、完整配置模板、Hook、转推 |
| [业务层代码改造清单](./业务层代码改造清单.md) | VIDEO/DEVICE/AI/WEB 逐文件改造说明 |
| [部署与运维手册](./部署与运维手册.md) | 装机清单、Compose/Ansible、日常运维、故障处理 |
| [压测与验收标准](./压测与验收标准.md) | 压测场景、指标、验收 checklist |
| [安全与容灾](./安全与容灾.md) | 鉴权、网络隔离、备份、故障切换 |

## 配套脚本与模板

路径：`.scripts/media-cluster/`

```
media-cluster/
├── docker-compose.media-node.yml    # 单媒体节点 Compose 模板
├── srs/
│   ├── cluster.conf.template
│   └── register.sh
├── zlm/
│   ├── config.ini.template
│   └── register.sh
├── nginx/
│   └── media-edge.conf.template
└── glusterfs/
    ├── volume-create.sh
    └── mount-all.sh
```

## 阅读顺序建议

1. 架构师 / 决策层：总览 → 架构与网络设计 → 压测与验收标准 → 安全与容灾  
2. 流媒体运维：SRS 与 ZLM 集群配置规范 → 部署与运维手册 → 存储与上传流水线  
3. 后端开发：MEDIA 控制面与 API 规范 → 业务层代码改造清单 → 存储与上传流水线  
4. 实施项目经理：总览 Phase 路线 → 部署与运维手册 → 压测与验收标准
