# yFeiEye V2.0 系统改造设计方案

| 项目 | 内容 |
|---|---|
| 文档编号 | YFEIEYE-ARCH-2026-002 |
| 文档版本 | V2.0 |
| 文档状态 | 评审稿 |
| 编制日期 | 2026-07-28 |
| 适用仓库 | E:\yFeiEye |
| 密级 | 内部 |
| 目标读者 | 产品负责人、架构师、开发、测试、运维、实施和项目管理人员 |

## 1. 执行摘要

yFeiEye 当前已经形成由 WEB、APP、DEVICE、AI、VIDEO、TASK、NODE、EDGE 和 VISUALIZE 等模块组成的 AIoT 平台，覆盖设备接入、流媒体、算法训练与推理、告警、监管事件、移动端和边缘节点。现有系统具备较完整的业务能力，但实时推理仍主要分散在 Python VIDEO、AI 和 C++ TASK 中，消息链路同时使用 Kafka 与 MQTT，节点调度、媒体调度、数据迁移、集群运维和商业交付能力尚未形成统一架构。

本方案采用“整体重构、分阶段建设、中心平台一次切换”的落地策略。目标架构不是继续增加微服务，而是收敛为四个稳定层次：

1. 统一控制面：负责租户、设备、摄像机、模型、任务、策略、资源调度和期望状态。
2. 站点本地数据面：负责媒体接入、解码、推理、跟踪和结果生成，原始帧不跨消息总线。
3. 可靠事件数据面：负责命令、持久事件、Inbox/Outbox、业务幂等、数据库和对象存储。
4. 可插拔领域应用：通用告警与司法监管事件解耦，通过领域适配器连接。

首个生产版本正式支持 x86 CPU、ONNX Runtime 和 NVIDIA TensorRT。RK3588/RKNN 不作为首次中心切换门禁，现有 RK3588 设备暂时保留旧 EDGE 推理链路，通过兼容网关接入新平台，后续再迁移到统一运行时契约。

本方案要求完整迁移设备、用户、租户、模型、任务、告警、监管事件、审计记录和媒体对象。Redis 缓存、在线流会话和临时心跳等可重建状态不迁移。

预计首个商业化 CPU/NVIDIA 版本需要 360 至 525 人日，建议配置 6 至 8 人核心团队，建设周期 8 至 12 个月，另安排至少 30 天生产试运行。RKNN 阶段另计 50 至 80 人日。

## 2. 文档范围

### 2.1 本方案覆盖

- 现有模块的目标职责和退役边界。
- VideoCore C++ 推理执行层。
- AI 模型资产和模型供应链。
- SRS、ZLM、MEDIA Scheduler 和 VideoCore 的媒体数据流。
- DEVICE、iot-node、NODE Agent 的调度与状态调和。
- NATS JetStream 参考消息架构及 Kafka 回退条件。
- MQTT 边缘兼容和两小时离线补偿。
- 告警证据和监管事件领域边界。
- 数据库、对象存储、读模型和数据生命周期。
- 单机、站点高可用和区域多站点部署。
- 安全、可观测性、License、升级和支持诊断。
- 全量数据迁移、一次性切换和回滚承诺点。
- 验收、风险、人员、投入和交付物。

### 2.2 本方案不覆盖

- 首次版本同时支持昇腾、寒武纪等所有 NPU 后端。
- 首次版本建设第三方插件市场。
- 跨地域同步写入同一个媒体对象存储集群。
- 全面 Event Sourcing、分布式 SQL 或 Lakehouse。
- 所有 Kubernetes 发行版和所有操作系统组合。
- UI 视觉改版和非架构相关功能重做。
- 将心理、生理或行为模型输出作为医学诊断、惩戒或监管定性的独立依据。

## 3. 现状基线

### 3.1 当前模块

| 模块 | 当前职责 | 主要架构问题 |
|---|---|---|
| WEB | PC 管理控制台 | 对后端接口和媒体路径存在历史兼容依赖 |
| APP | 移动端 | 缺少自动化测试和统一 API 兼容策略 |
| DEVICE | Java 微服务控制底座 | 模块多、数据库多，服务发现和部署依赖复杂 |
| AI | 训练、导出、推理和部署 | 模型资产与生产推理职责混合 |
| VIDEO | 摄像头、流媒体、推理、告警和录像 | 职责过重，是本次拆分重点 |
| TASK | C++ 单相机推理和 TaskManager | 执行模型单一，接口和跨平台构建尚不成熟 |
| NODE | 远程节点 Agent | 已有控制能力，但缺少完整期望状态调和 |
| EDGE | MQTT 边缘运行时 | 当前无本地业务盘，与两小时离线补偿目标冲突 |
| VISUALIZE | 可视化能力 | 不属于实时推理核心路径 |

### 3.2 当前可复用能力

- SRS、ZLMediaKit、GB28181、RTSP、RTMP、HTTP-FLV 和 WebRTC 媒体链路。
- MEDIA Scheduler 规划、设备 Sticky 绑定和 URL 生成方向。
- iot-node 计算节点、心跳、容量和任务分配能力。
- AI 训练、数据集、模型导出和自动标注能力。
- VIDEO 摄像机、算法任务、录像、告警和媒体对象服务。
- DEVICE 监管事件、证据链、复测、责任和关闭门禁。
- 局部 Outbox、幂等、重试、陈旧 Claim 恢复和发布包校验经验。
- mini、standard、full 部署规格和现有安装脚本。

### 3.3 当前主要问题

1. 生产推理分散，CPU、GPU、模型和任务的资源管理不统一。
2. 视频接入、解码、模型执行、告警和业务处置职责耦合。
3. Kafka、MQTT 和数据库写入之间缺少统一端到端可靠性语义。
4. 节点分配已有基础，但没有 Desired State、Observed State、Lease 和 Fencing 闭环。
5. 多个数据库和对象存储缺少统一所有权、生命周期和恢复目标。
6. 当前仓库缺少覆盖整个 monorepo 的 CI。
7. 备份、恢复、升级、诊断、License 和支持矩阵尚未产品化。
8. 一次性切换要求放大数据迁移和回滚风险。

## 4. 目标与硬约束

### 4.1 架构目标

- 形成稳定的控制面、站点数据面、事件数据面和领域应用边界。
- VIDEO 不再承担生产模型执行。
- AI 成为模型资产和模型供应链中心。
- TASK 的有效 C++ 能力进入 VideoCore，旧 TaskManager 退役。
- 同一编码规格的视频在同一推理节点只解码一次。
- 消息总线只承载控制命令、状态和结构化结果，不传输原始帧和 Tensor。
- 通过不可变 TaskSpec、Lease 和 Reconciler 实现可恢复的任务调度。
- 支持单机、站点高可用和区域多站点商业部署。
- 未来新增 RKNN 后端时不修改控制面、领域模型和消息契约。

### 4.2 首发硬门禁

- 16 路 1080p H.264、25fps 稳定接入。
- 固定任务组合下，采样帧从 Media 入口到结果持久化 P95 小于 200ms。
- 相同硬件、模型、码流和推理频率下，节点 CPU 使用率较当前链路降低至少 50%。
- 72 小时稳定性测试无持续内存增长、队列失控或不可恢复积压。
- 故障注入后无业务记录丢失，无重复业务副作用。
- 边缘断网两小时后能够完整、按序、幂等补发。
- 完整历史业务数据和媒体对象迁移校验通过。
- 安全、审计、租户隔离、备份恢复和回滚演练通过。

### 4.3 已确认约束

- 中心平台采用一次性生产切换，不采用逐设备生产灰度。
- V2 环境可以提前建设、回放和迁移演练。
- 完整迁移业务数据，临时运行状态重建。
- CPU/NVIDIA 首发，RK3588/RKNN 后续。
- 原有 EDGE 在 RKNN 完成前继续运行，但必须通过兼容网关满足新平台消息和离线补偿契约。

## 5. 架构原则

1. 控制面与数据面分离。
2. 热路径本地化，原始帧不跨服务消息总线。
3. 数据库是业务真源，消息总线不是数据库。
4. 所有跨服务投递采用至少一次语义和业务幂等。
5. 所有调度分配使用 Lease、Epoch 和 Fencing。
6. 接口、消息、数据库和模型包均版本化。
7. 首先复用现有能力，再进行边界拆分。
8. 不为单一场景创建不必要的通用平台。
9. 商业版本必须可安装、可升级、可恢复、可诊断。
10. 低可信身份不得创建或更新个人监管事件。
11. 敏感模型输出必须保留辅助和非独立定性标识。
12. 架构决策以可验证门禁为准，不以技术偏好为准。

## 6. 关键架构决策

| 编号 | 决策 | 结论 |
|---|---|---|
| ADR-V2-01 | 总体路线 | 分层内核加事件驱动，不采用大型单体或全微服务拆分 |
| ADR-V2-02 | 生产切换 | 分阶段建设，中心平台一次切换 |
| ADR-V2-03 | 推理执行 | 新建 VideoCore，首发 ORT CPU 和 TensorRT |
| ADR-V2-04 | 媒体边界 | 复用 SRS/ZLM/MEDIA Scheduler，VIDEO 提取为 MEDIA |
| ADR-V2-05 | 模型边界 | AI 负责模型供应链，不负责生产流式推理 |
| ADR-V2-06 | 控制闭环 | 扩展 iot-node，建立 Scheduler/Reconciler |
| ADR-V2-07 | 消息系统 | NATS Core 加 JetStream 为参考目标，必须通过 M0 门禁 |
| ADR-V2-08 | 消息回退 | NATS 未通过时，Kafka 保留持久事件，契约不变 |
| ADR-V2-09 | 边缘协议 | 中心使用 NATS，设备和旧 EDGE 继续 MQTT，通过网关桥接 |
| ADR-V2-10 | 数据一致性 | PostgreSQL 真源，Inbox/Outbox 和业务幂等 |
| ADR-V2-11 | 全局标识 | 新增 canonical UID 和旧 ID 映射，不进行破坏性一次替换 |
| ADR-V2-12 | 集群部署 | 单机 Compose，生产集群采用一套认证 Kubernetes 方案 |
| ADR-V2-13 | 多站点 | 每站点独立数据面，禁止跨 WAN 拉伸单个 Raft 集群 |
| ADR-V2-14 | 监管领域 | Alert 是输入证据，Supervision Event 是权威生命周期对象 |
| ADR-V2-15 | 首发范围 | 不在首发同时建设 RKNN 和其他 NPU |

## 7. 目标逻辑架构

```text
WEB / APP / Third-party
          |
Northbound API Facade
          |
+-------------------- Control Plane --------------------+
| DEVICE | iot-node Scheduler | Reconciler | Registry  |
| Tenant | Device | Camera | TaskSpec | Quota | Policy |
+-------------------------------------------------------+
          | Desired State / Command / Status
          v
+---------------- Site-local Data Plane ----------------+
| SRS / ZLM / MEDIA Scheduler -> compressed stream     |
|                 -> VideoCore Node                    |
| Decode -> FrameHub -> ORT/TRT -> Track -> Outbox     |
+-------------------------------------------------------+
          | structured result only
          v
+---------------- Event and Data Plane -----------------+
| NATS Core | JetStream | Inbox/Outbox | PostgreSQL    |
| Object Store | Read Model | Audit                   |
+-------------------------------------------------------+
          |
+---------------- Domain Applications ------------------+
| Observation -> Alert Evidence -> Generic Alert       |
|                        -> Supervision Domain Adapter  |
|                        -> Supervision Event Workflow  |
+-------------------------------------------------------+
```

横切能力包括服务身份、密钥、License、OpenTelemetry、审计、升级控制器、诊断包和容量报告。

## 8. 模块改造方案

### 8.1 WEB 和 APP

- 通过 Northbound API Facade 访问 V2 API。
- 首次切换保留旧 URL 和主要响应结构，内部转换为 V2 契约。
- 新 API 使用明确版本，例如 `/api/v2/`。
- WebSocket、播放 URL 和下载 URL 由 Facade 统一生成。
- APP 增加基础接口契约测试和关键流程冒烟。
- UI 不直接读取多个业务数据库或拼接内部服务地址。

### 8.2 DEVICE 和 iot-node

DEVICE 继续作为业务控制底座。iot-node 从简单分配器升级为运行时控制中心，新增：

- TaskSpec Registry。
- Model Package Registry 索引。
- Runtime Scheduler。
- Desired State Store。
- Observed State Store。
- Lease Manager。
- Reconciler。
- Tenant Quota Manager。
- Placement Policy。
- Upgrade Wave Controller。

Nacos 在首个 V2 版本中仅保留为 DEVICE 微服务发现兼容能力，不作为任务、配置或业务状态真源。

### 8.3 VIDEO 到 MEDIA

从 VIDEO 提取并形成 MEDIA 组件：

- 摄像机接入配置。
- SRS、ZLM 和 GB28181 媒体节点注册。
- RTSP、RTMP、GB28181 流会话。
- Sticky Lease 和媒体节点亲和。
- 流地址、播放地址和录像索引。
- DVR Hook、Upload Worker 和对象归档。
- 媒体健康、码流探测和故障切换。

以下职责从 VIDEO 移除：

- 生产模型加载。
- Python 实时检测主链。
- GPU 推理会话管理。
- 跟踪器主实现。
- 推理任务进程管理。

旧 VIDEO 对外 API 在切换时由 Facade 兼容，旧 Python VIDEO 运行服务完成切换后退役。

### 8.4 TASK 到 VideoCore

TASK 中可复用的 FFmpeg、OpenCV、ONNX Runtime、跟踪和 HTTP 管理能力进入 VideoCore。旧 TaskManager 进程在 V2 切换后退役。

### 8.5 AI Model Factory

AI 保留：

- 数据集和标注。
- 模型训练。
- 自动标注和离线推理。
- 模型导出和转换。
- 评测和 Golden Vector 生成。
- 模型签名和兼容性认证。

AI 不再承担生产摄像机实时推理。

### 8.6 NODE

NODE Agent 负责：

- 节点登记和证书。
- CPU、内存、磁盘、GPU、驱动和网络容量上报。
- 模型包和运行包预取。
- VideoCore 进程启动、停止和健康。
- Desired State 拉取和本地 Reconcile。
- 任务启动结果和 Observed State 回报。
- 升级波次、排空、激活和回滚。

NODE Agent 默认端口继续使用 9100。VideoCore 不占用 9100。

### 8.7 EDGE

首发阶段保留现有 EDGE 和 MQTT。新增 Edge Compatibility Gateway：

- MQTT 与平台事件信封转换。
- 两小时加密 SQLite/WAL Store-and-Forward。
- 严格磁盘配额和 TTL。
- 断网恢复后按序和幂等补发。
- 设备证书和密钥轮换。
- 不默认缓存完整视频，只缓存结构化结果和策略允许的短片。

RKNN 阶段复用相同 TaskSpec、ModelPackage、Frame/Tensor 和结果契约。

## 9. 控制面详细设计

### 9.1 TaskSpec

TaskSpec 是不可变、带版本和校验值的任务定义。

```yaml
apiVersion: yfeieye.io/v2
kind: VisionTask
metadata:
  taskUid: 018f0000-0000-7000-8000-000000000001
  tenantId: "1001"
  revision: 12
spec:
  source:
    cameraUid: 018f0000-0000-7000-8000-000000000010
    streamProfile: substream-h264-1080p
  sampling:
    inferenceFps: 5
    overloadPolicy: reduce_sampling
  graph:
    - id: detector
      type: model
      modelPackage: sha256:example
    - id: tracker
      type: tracker
      dependsOn: [detector]
  runtime:
    preferredBackend: tensorrt
    allowCpuFallback: false
  resources:
    gpuMemoryMiB: 2048
    cpuMillicores: 1000
  outputs:
    - observation
    - alert_evidence
```

正式 Schema 采用 JSON Schema 或 Protobuf 定义，不以 YAML 文本本身作为接口。

### 9.2 TaskSpec 编译

控制面在下发前完成：

- Schema 校验。
- DAG 无环校验。
- 模型输入输出匹配。
- 后端兼容性检查。
- 资源预算计算。
- 媒体规格匹配。
- 租户配额检查。
- 安全和敏感能力授权。

输出 Execution Plan，VideoCore 不承担复杂业务配置推断。

### 9.3 Scheduler

调度评分至少考虑：

- 节点在线状态。
- CPU、内存和磁盘水位。
- GPU 型号、空闲显存和健康状态。
- 模型包是否已缓存。
- camera_uid 对应媒体节点位置。
- 节点最大任务数。
- 租户和站点配额。
- 维护、排空和升级状态。
- 故障域和反亲和策略。

### 9.4 Lease 和 Fencing

每次分配生成：

- `allocation_id`
- `lease_id`
- `lease_epoch`
- `task_revision`
- `expires_at`

VideoCore 结果必须携带这些字段。Inbox 拒绝过期 epoch、错误 revision 和重复业务键，避免节点故障迁移后双实例产生重复副作用。

### 9.5 Desired 和 Observed State

Desired State 存储期望任务、模型、版本和节点约束。Observed State 存储节点实际运行、模型加载、吞吐、错误和最后心跳。

Reconciler 的职责是：

- 创建缺失运行实例。
- 停止不应存在的实例。
- 修复错误版本。
- 迁移失效节点任务。
- 排空维护节点。
- 控制升级波次。
- 记录所有调和决策和原因。

## 10. VideoCore 详细设计

### 10.1 组件结构

```text
VideoCore Supervisor
  |-- Control Endpoint
  |-- Task Runtime Manager
  |-- Source Manager
  |-- Decoder Pool
  |-- FrameHub
  |-- Graph Executor
  |-- Backend Registry
  |-- Model Engine Cache
  |-- Tracker Registry
  |-- Result Outbox
  |-- Metrics and Health
```

### 10.2 进程模型

- 每个计算节点运行一个 Supervisor。
- 根据 GPU、隔离级别和故障域运行一个或多个 Worker 进程。
- 内置受信任后端可以进程内加载。
- 第三方或高风险插件运行在独立 Worker。
- 一个 Worker 崩溃不得终止整个节点上的全部任务。
- Supervisor 使用退避和熔断策略重启 Worker。

### 10.3 FrameBuffer

FrameBuffer 至少包含：

- frame_id
- camera_uid
- stream_profile
- width 和 height
- pixel_format
- plane_count
- 每个 plane 的地址、stride、offset 和 size
- memory_type
- device_id
- capture_timestamp
- ingest_timestamp
- monotonic_timestamp
- synchronization_fence
- reference_handle

禁止通过一个裸 `void*` 和单 pitch 表达所有格式。支持 HOST、CUDA DEVICE 和未来 DMA Buffer/RKNN 内存类型。

### 10.4 TensorBuffer

TensorBuffer 至少包含：

- shape
- dtype
- layout
- memory_type
- device_id
- byte_size
- owner
- synchronization primitive

后端输出可驻留设备内存。后处理不应被迫使用 `vector<float>` 将全部数据拷回主机。

### 10.5 插件 ABI

- 内置插件使用版本化 C ABI 或随版本静态编译。
- 不向第三方承诺跨编译器稳定的 C++ ABI。
- 插件声明 ABI 版本、能力、资源需求和线程模型。
- 不兼容插件在加载前失败，不允许运行时静默降级。
- 插件不得直接访问业务数据库。
- 插件不得绕过 Result Outbox 发布业务消息。

### 10.6 解码和 FrameHub

共享解码键为：

`camera_uid + stream_profile + decoder_profile`

只有相同编码、分辨率、时间基准和节点位置的任务共享解码。主码流、子码流和不同编码规格不能强行共享。

FrameHub 使用有界环形缓冲，只保存任务需要的最近帧。慢消费者依据 TaskSpec 选择跳帧、降采样或失败，不允许无限积压。

### 10.7 调度和批处理

- 实时任务采用 deadline-aware 调度。
- 动态批处理具有最大等待时间。
- 不同租户共享 GPU 时执行配额和公平调度。
- 实时任务和训练任务使用独立资源池。
- 过载策略包括降低采样、拒绝新任务、停用非关键级联和任务失败。
- 高优先级不等于永不丢弃，高优先级仍受容量和故障保护约束。

### 10.8 模型引擎缓存

引擎缓存键至少包括：

`model_digest + backend + precision + device_capability + runtime_version`

节点支持预取、预热、引用计数和 LRU 回收。切换模型时先验证和预热新引擎，再原子替换任务引用。

### 10.9 结果 Outbox

推理热路径只将结构化结果写入本地持久化 Outbox。独立 Publisher 负责：

- 发送到 JetStream 或回退消息系统。
- 重试和指数退避。
- 保存幂等键。
- 记录失败原因。
- 进入死信和人工补偿。
- 不因总线短时不可用阻塞解码和推理线程。

### 10.10 首发后端

| 后端 | 首发状态 | 说明 |
|---|---|---|
| ONNX Runtime CPU | 必须支持 | x86 CPU 基线 |
| TensorRT | 必须支持 | NVIDIA 生产主后端 |
| ONNX Runtime CUDA | 可选兼容 | 不作为主要性能承诺 |
| RKNN | 后续阶段 | RK3588 |
| CANN | 不在首发 | 保留接口扩展能力 |
| 其他 NPU | 不在首发 | 通过独立认证进入 |

## 11. 模型供应链

### 11.1 ModelPackage

每个生产模型以内容寻址包交付：

```text
model-package/
  manifest.json
  model.onnx or model.engine
  labels.json
  preprocess.json
  postprocess.json
  golden/
    inputs/
    expected-results.json
  licenses/
  sbom.json
  signature.sig
```

Manifest 包含：

- model_uid 和版本。
- 文件 SHA-256。
- 输入输出 Tensor 契约。
- 图像颜色空间和归一化规则。
- 类别、阈值和后处理版本。
- 适用运行时、后端和硬件。
- 资源预算。
- 精度容差。
- 许可证和再分发限制。
- 安全扫描结果。

### 11.2 模型发布流程

1. AI 导出候选模型。
2. 执行静态检查和许可证检查。
3. 在 CPU 和 TensorRT 上运行 Golden Vector。
4. 运行准确率和性能基线。
5. 生成 SBOM 和签名。
6. 登记兼容矩阵。
7. 发布到对象存储。
8. 控制面创建可部署版本。
9. 节点预取和预热。
10. 原子激活并保留前一版本回退。

### 11.3 模型运营

- 模型版本独立于平台版本。
- 监控输入分布、置信度和关键类别变化。
- 监控模型性能和显存回归。
- 模型升级支持独立回滚。
- 首次 V1 到 V2 平台切换不采用生产流量灰度，但 V2 后续模型升级允许按任务或站点灰度。

## 12. MEDIA 详细设计

### 12.1 接入路径

| 协议 | 目标路径 |
|---|---|
| GB28181 | 设备主动注册到 iot-gb28181/ZLM |
| RTMP | 设备主动推送到 SRS |
| RTSP 公网可达 | MEDIA 或站点节点主动拉流 |
| RTSP 私网/NAT | Edge/Agent 主动出站桥接 |
| HTTP-FLV/WebRTC | 播放输出，不作为主要设备接入协议 |

### 12.2 Sticky Lease

同一 camera_uid 和 stream_profile 绑定媒体节点租约。租约包含 epoch 和过期时间。播放、录像和推理都通过 MEDIA Scheduler 获取当前节点和流地址。

媒体节点失效后：

1. 租约过期。
2. Scheduler 选择新节点。
3. 设备或 Agent 重新建立流。
4. VideoCore 任务获得新媒体位置。
5. 旧 epoch 结果被拒绝。

### 12.3 媒体与推理

- 优先同站点部署媒体和推理节点。
- 跨节点传输压缩码流，不传输原始帧。
- 低延迟部署可将 Media Node 与 VideoCore 放在同一物理节点。
- 大规模部署可分离两个节点池，通过专用媒体网络连接。
- 录像和推理使用不同 stream_profile 时分别管理。

### 12.4 录像和对象归档

- Hook 只校验和入队，不执行大文件上传。
- Upload Worker 执行分片校验、上传和索引。
- MinIO/S3 是权威对象存储。
- 本地磁盘或共享文件系统仅作为有期限缓冲。
- 数据不得放在 `current` 或 `releases` 目录。

## 13. 消息和事件数据面

### 13.1 消息类型

| 类型 | 传输 | 持久化 |
|---|---|---|
| 节点心跳 | NATS Core | 仅保留最新状态 |
| 控制命令 | NATS Core 或短保留 Stream | 记录命令审计 |
| TaskSpec 变更 | JetStream | 持久化和可重放 |
| 媒体状态 | JetStream | 按策略保留 |
| 推理结果 | JetStream | 持久化到业务数据库 |
| 告警证据 | JetStream | 持久化和对象关联 |
| 监管事件命令 | JetStream | 持久化和审计 |
| 原始帧/Tensor | 禁止进入总线 | 不适用 |

### 13.2 NATS 选型门禁

M0 必须验证：

- 目标事件吞吐和消息大小。
- 三节点副本和单节点故障。
- Publisher/Consumer 异常退出。
- 网络分区和恢复。
- 两小时积压后的追赶时间。
- 磁盘接近阈值时的保护。
- 重放、死信和运维工具。
- 多租户权限和审计。

通过标准：

- 不丢失已确认持久事件。
- 业务幂等测试通过。
- 恢复时间满足 SLO。
- 运维复杂度不高于当前 Kafka。

如果未通过，持久事件继续使用 Kafka，控制命令可以使用 NATS Core 或现有机制。Event Envelope、Inbox/Outbox 和业务代码不得因回退而修改。

### 13.3 Event Envelope

```json
{
  "event_id": "018f0000-0000-7000-8000-000000000001",
  "event_type": "inference.result.v1",
  "schema_version": "1.0",
  "tenant_id": "1001",
  "organization_id": "2001",
  "site_id": "site-a",
  "camera_uid": "018f0000-0000-7000-8000-000000000010",
  "task_uid": "018f0000-0000-7000-8000-000000000020",
  "task_revision": 12,
  "allocation_epoch": 8,
  "occurred_at": "2026-07-28T10:00:00.000Z",
  "ingested_at": "2026-07-28T10:00:00.050Z",
  "trace_id": "example",
  "idempotency_key": "example",
  "sensitivity": "internal",
  "payload": {}
}
```

### 13.4 主题和顺序

参考主题：

- `cfg.task.v1.<partition>`
- `runtime.command.v1.<node>`
- `runtime.status.v1.<node>`
- `media.stream.v1.<partition>`
- `inference.result.v1.<partition>`
- `alert.evidence.v1.<partition>`
- `supervision.command.v1.<partition>`

需要同一 camera_uid 或 task_uid 有序的事件，通过确定性分区进入同一消费分区。消费者仍需依据 revision 和 epoch 处理乱序。

### 13.5 Inbox/Outbox

- 数据库事务同时写业务状态和 Outbox。
- Publisher Claim 使用行锁或 `FOR UPDATE SKIP LOCKED`。
- 处理中的陈旧 Claim 可以按阈值回收。
- Consumer 先写 Inbox 幂等记录，再执行业务副作用。
- 通知、对象上传和外部接口分别记录投递状态。
- 原始消息允许重复，业务副作用不得重复。

## 14. 领域事件设计

### 14.1 通用视觉事件

```text
InferenceResult
  -> Observation
  -> Detection
  -> AlertEvidence
  -> Generic Alert
  -> Notification / Archive / Domain Adapter
```

通用 Alert 不自动成为监管事件。

### 14.2 司法监管事件

只有满足领域条件的证据通过适配器进入：

```text
AlertEvidence
  -> identity readiness
  -> trusted person_id
  -> confidence and low-trust check
  -> Supervision Event
  -> responsibility
  -> handling
  -> medical recheck
  -> close gate
  -> evidence archive
```

约束：

- 身份候选不等于可信 person_id。
- 低可信窗口不得创建或更新个人监管事件。
- 通知成功不等于医务复测成功。
- 人工替代闭环不等于真实接口成功。
- 心理和健康画像带辅助不定性标识。

## 15. 标识、时间和契约

### 15.1 Canonical UID

新增以下不可变 UID：

- tenant_uid
- organization_uid
- site_uid
- device_uid
- camera_uid
- model_uid
- task_uid
- event_uid

推荐使用 UUIDv7。旧 Long ID、deviceIdentification、SIP Device ID、Channel ID 和复合 stream_id 保留在映射表中。

### 15.2 摄像机映射

`camera_uid` 表示平台唯一摄像机通道实体，不等同于物理设备 ID。GB28181 的 SIP Device ID 和 Channel ID、RTSP 地址、ONVIF 标识均作为外部标识映射。

切换前完成：

- 重复标识扫描。
- 一对多和多对一冲突清单。
- UID 回填。
- 外键和对象路径迁移。
- 旧 API 到 UID 的兼容解析。

### 15.3 时间语义

所有关键事件记录：

- occurred_at
- captured_at
- ingested_at
- persisted_at
- monotonic_duration
- clock_quality

所有站点监控 NTP/PTP 偏差。时间不同步时，结果保留但标记低时钟质量，不能用于严格延迟和证据顺序判定。

### 15.4 契约版本

- 外部 API 支持当前版本和前一版本。
- Event Schema 只允许向后兼容增加字段。
- 删除字段需要至少一个 LTS 周期。
- 数据库采用 expand、migrate、contract。
- ModelPackage 声明最低和最高兼容运行时。
- TaskSpec 由控制面升级转换，不由节点猜测。

## 16. 数据和存储架构

### 16.1 数据所有权

| 数据域 | 写入所有者 | 主要存储 |
|---|---|---|
| 租户、用户、权限 | DEVICE System | PostgreSQL control_db |
| 设备、摄像机、节点 | DEVICE/iot-node | PostgreSQL control_db |
| 模型元数据 | AI Model Factory | PostgreSQL model_db |
| 模型文件 | AI Model Factory | MinIO/S3 |
| 媒体会话和录像索引 | MEDIA | PostgreSQL media_db |
| 录像和抓拍 | MEDIA Upload Worker | MinIO/S3 |
| 推理观察和告警证据 | Alert Domain | PostgreSQL event_db |
| 监管事件和证据链 | Supervision Domain | PostgreSQL event_db |
| 审计 | Audit Service | PostgreSQL audit_db 或专用审计存储 |
| Dashboard/报表 | Read Model Builder | PostgreSQL read_db |

服务只能通过 API 或事件访问其他域，不得直接写入其他服务数据库。

### 16.2 PostgreSQL 部署

- Appliance 可以使用单实例 PostgreSQL 和多个逻辑数据库。
- Site HA 使用主库、两个副本和 PgBouncer。
- Regional 可按领域拆分物理集群。
- 大表优先按时间和租户分区。
- 不在首发引入分布式 SQL。
- 跨域 Dashboard 使用 Read Model，不使用实时跨库 Join。

### 16.3 对象存储

- Site HA 使用四节点 MinIO EC 或客户认证的外部 S3。
- 对象通过 bucket、tenant、site、date 和 object_uid 组织。
- 业务表保存 object_key、size、hash、content_type 和 retention_class。
- 临时签名 URL 不保存为长期业务字段。
- 支持加密、对象锁、生命周期、异地复制和孤儿扫描。

### 16.4 数据生命周期

| 数据类型 | 默认策略 |
|---|---|
| 模型包 | 当前和历史发布版本长期保留，按授权清理 |
| 告警图片 | 按租户策略保留，支持 Legal Hold |
| 告警短片 | 按事件级别和合同保留 |
| 普通录像 | 按存储计划生命周期 |
| 监管证据 | 按合规和案件策略，支持对象锁 |
| 推理原始结果 | 在线保留后归档或聚合 |
| 审计记录 | 不短于合同和合规周期 |
| 临时缓存 | TTL 和磁盘配额双重限制 |

具体保留天数由产品套餐和客户策略配置，但不得绕过 Legal Hold。

## 17. Northbound API 和兼容层

Northbound API Facade 负责：

- `/api/v2` 版本化 API。
- 旧 `/video`、`/algorithm` 等接口兼容。
- 用户 JWT、RBAC 和租户上下文。
- WebSocket 和状态订阅。
- 播放地址和对象签名地址。
- 限流、审计和错误码统一。
- 旧 ID 到 canonical UID 映射。

兼容层具有明确退役策略：

- V2 首个 LTS 保留主要旧接口。
- 记录旧接口调用量。
- 后续版本发布弃用告警。
- 前一 LTS 支持期结束后移除。

内部控制接口优先使用 Protobuf/gRPC 或版本化 REST，不通过前端 API 反向调用。

## 18. 安全和合规

### 18.1 身份

- 用户使用统一身份和 JWT。
- 内部服务使用 mTLS 服务身份。
- NODE/EDGE 使用设备证书和短期凭据。
- 不新增长期静态共享 Token。
- License 签名验证和服务身份分离。

### 18.2 密钥

- 密钥不进入 Git、镜像和前端静态资源。
- Appliance 使用加密本地 Secret Store。
- Kubernetes 使用 Secret 加密和可选外部 KMS。
- 支持轮换、吊销和审计。
- 对象存储访问使用最小权限服务账号。

### 18.3 租户隔离

- 所有业务表包含 tenant_id。
- 所有消息包含 tenant_id。
- 所有对象路径和访问策略包含租户范围。
- 调度器执行租户资源配额。
- 审计记录跨租户操作和管理员越权操作。

### 18.4 供应链

- 镜像生成 SBOM。
- 模型包生成 SBOM 和许可证清单。
- 镜像、安装包和模型包签名。
- CI 执行依赖和镜像漏洞门禁。
- 高危漏洞阻断商业发布，例外必须审批并记录失效日期。

### 18.5 隐私和监管

- 人脸、生理、心理和行为数据标记敏感级别。
- 指标标签不得包含 person_id、姓名、身份证号等敏感值。
- 日志和诊断包默认脱敏。
- 证据导出记录授权、操作者、目的和完整性校验。
- 低可信结果不能直接驱动惩戒、医学诊断或监管定性。

## 19. License 和商业授权

- 支持离线签名 License。
- License 包含租户、站点、节点、摄像机、GPU、功能和期限授权。
- License 校验结果在控制面缓存并具有宽限期。
- License 服务故障不停止正在运行的推理热路径。
- 超出授权时禁止创建新资源，现有关键任务进入明确宽限状态。
- 所有授权变化进入审计。
- 不在边缘节点保存可伪造的明文授权状态。

## 20. 可观测性

### 20.1 指标

控制面：

- reconcile backlog
- scheduling latency
- lease expiry
- node capacity
- failed placements

媒体面：

- active streams
- ingest bitrate
- reconnect count
- decode errors
- recording backlog

推理面：

- sampled fps
- inference latency
- queue depth
- dropped or skipped frames
- GPU utilization
- GPU memory
- model load time
- outbox backlog

事件面：

- publish latency
- consumer lag
- retry count
- dead-letter count
- inbox duplicate count
- database transaction latency

### 20.2 Trace

跨异步链路传播 trace_id：

`TaskSpec -> Runtime Allocation -> Media -> Inference -> AlertEvidence -> SupervisionEvent`

原始帧不进入 Trace。Trace 记录 frame_id、task_uid 和 object_uid 等非敏感技术标识。

### 20.3 日志

- 使用结构化 JSON。
- 包含 service、version、node、tenant、trace 和 error_code。
- 敏感字段脱敏。
- 设定日志轮转和保留上限。
- 支持一键生成脱敏诊断包。

### 20.4 SLO

Site HA 设计目标：

- 控制 API 月可用性不低于 99.9%。
- 已确认持久事件 RPO 为 0。
- 单计算节点故障后关键任务两分钟内恢复。
- 单消息节点故障不丢失已确认事件。
- PostgreSQL 计划内故障切换 RTO 不超过 5 分钟。

这些指标必须通过认证后才能写入销售 SLA。

## 21. 商业交付形态

### 21.1 Appliance

适用小型项目和单机一体机：

- Docker Compose 加 systemd。
- 本地 PostgreSQL、NATS、MinIO 或兼容对象存储。
- 单个 CPU 或 NVIDIA GPU 节点。
- 不承诺物理节点高可用。
- 支持离线安装、备份和恢复。

### 21.2 Site HA

推荐生产基线：

| 角色 | 基线数量 |
|---|---:|
| API Gateway/Media Edge | 2 |
| DEVICE/iot-node/API Facade | 3 副本 |
| NATS JetStream | 3 节点 |
| PostgreSQL | 1 主 2 副本 |
| MinIO | 4 节点或外部 S3 |
| SRS/ZLM | 按码流扩容 |
| VideoCore | 按 GPU 和模型吞吐扩容 |
| OTel/Metrics/Logs | 2 至 3 副本 |

每个商业 LTS 只认证一套 Kubernetes 发行版和版本，不承诺所有发行版。

### 21.3 Regional

- 每个站点独立运行 MEDIA、VideoCore 和本地事件缓冲。
- 站点通过 NATS Leaf 或等价桥接汇聚结构化事件。
- 禁止跨 WAN 拉伸单一 JetStream Raft。
- 对象存储异步复制。
- 中心负责租户、模型、策略、审计和汇总查询。
- WAN 断开不影响站点本地关键任务。

## 22. 集群物理架构

```text
External Network
       |
API Gateway / Media Edge x2
       |
+---------------- Control Node Pool ----------------+
| DEVICE x3 | iot-node x3 | API Facade x3          |
+---------------------------------------------------+
       |
+---------------- Media Node Pool ------------------+
| SRS Live xN | ZLM GB xM | Upload Worker xK       |
+---------------------------------------------------+
       | compressed stream / dedicated media VLAN
+---------------- Compute Node Pool ----------------+
| VideoCore CPU xN | VideoCore GPU xM              |
| Node Agent on every node                         |
+---------------------------------------------------+
       |
+---------------- Data Node Pool -------------------+
| NATS x3 | PostgreSQL HA | MinIO x4 | Observability|
+---------------------------------------------------+
```

### 22.1 故障域

- 数据副本不得位于同一物理机。
- 关键节点跨机架、交换机或电源域。
- GPU 任务使用 Lease Epoch 防止故障后双运行。
- Media Sticky Lease 失效后重新绑定。
- Kubernetes 调度使用反亲和和节点标签。
- 数据服务配置 PodDisruptionBudget。

### 22.2 网络

- 管理、媒体、存储和业务网络逻辑隔离。
- 媒体层按实际容量配置 10G 或 25G。
- NATS 和业务 API 不承载原始视频。
- SRS/ZLM 使用 hostNetwork 或等价高性能网络。
- 对外 RTP 端口范围最小化并纳入防火墙和容量管理。

### 22.3 存储

- PostgreSQL 使用独立可靠存储。
- JetStream 使用本地高性能持久盘和副本。
- MinIO 使用独立数据盘和 EC。
- 日志、缓存、模型和业务数据使用不同目录和配额。
- 持久数据不得绑定发布目录。

## 23. 性能和容量

### 23.1 首发固定基线

| 项目 | 基线 |
|---|---|
| CPU | 16 个物理核心 |
| 内存 | 64GB |
| GPU | NVIDIA L4 24GB |
| 磁盘 | NVMe |
| 视频 | 16 路 H.264 1080p 25fps |
| 任务 A | 8 路通用检测加跟踪，5fps |
| 任务 B | 4 路人脸检测识别，5fps |
| 任务 C | 4 路车牌检测 OCR，2fps |

如果正式采购硬件不同，发布 BOM 必须重新固定型号并在相同测试集上认证，不能直接沿用结论。

### 23.2 核心指标

- 16 路持续接入。
- 采样帧端到端 P95 小于 200ms。
- CPU 使用率降低至少 50%。
- GPU 显存无持续增长。
- 任务重启恢复不超过 60 秒。
- 单模型预热时间纳入容量报告。
- 两小时总线或网络中断后，积压在规定时间内追平。
- 72 小时稳定测试后无不可恢复积压。

### 23.3 容量模型

容量报告至少计算：

- 注册摄像机数。
- 峰值在线码流数。
- 单路码率。
- 推理采样率。
- 模型单帧耗时。
- GPU 显存。
- DVR 比例和分片大小。
- 对象数量和日增长。
- 消息事件数。
- 数据库日增长。
- 日志和指标保留。

20,000 路场景沿用现有约 48Gbps 媒体层假设作为起点，但必须依据实际码率、在线率和录像策略重新测算。

## 24. 高可用、备份和灾难恢复

### 24.1 Appliance 目标

- 默认每日业务备份。
- RPO 目标 24 小时。
- RTO 目标 4 小时。
- 支持完整导出和新机恢复。

### 24.2 Site HA 目标

- PostgreSQL 持续复制。
- JetStream 三副本。
- MinIO EC。
- 已提交业务事件 RPO 0。
- 数据库故障 RTO 5 分钟。
- 单节点任务恢复 2 分钟。
- 每月至少一次自动恢复验证。
- 每季度一次完整灾难恢复演练。

### 24.3 恢复验证

备份成功不等于恢复成功。恢复演练必须验证：

- 用户和权限。
- 设备和摄像机。
- 模型列表、详情、图片和下载。
- 算法任务。
- 告警和监管事件。
- 证据对象和签名。
- 播放和媒体路由。
- 审计链。
- 版本和 Schema。

## 25. 安装、升级和发布

### 25.1 发布物

- 签名容器镜像。
- Appliance 离线安装包。
- Kubernetes Helm Chart 或等价模板。
- 数据库迁移。
- Model Runtime 包。
- SBOM 和许可证清单。
- 兼容矩阵。
- 发布说明。
- 升级和回滚说明。
- 验证脚本。

### 25.2 CI 门禁

1. Java、Python、C++、WEB 和 APP 基础构建。
2. OpenAPI、Protobuf 和 JSON Schema 兼容。
3. PostgreSQL 真实迁移。
4. Golden Vector 跨后端一致性。
5. CPU/TensorRT 性能回归。
6. 镜像和依赖漏洞。
7. SBOM 和签名。
8. Appliance 安装冒烟。
9. Site HA 安装冒烟。
10. N/N-1 混合版本测试。
11. 备份恢复。
12. 发布包内容和校验值。

### 25.3 V2 后续滚动升级

- API 和事件支持 N/N-1。
- 数据库先扩展再迁移，最后删除旧字段。
- 控制服务滚动升级。
- Media 节点先释放 Sticky Lease。
- VideoCore 节点先排空任务。
- 模型包独立升级。
- 达到错误门限时停止升级波次。

## 26. 数据迁移

### 26.1 迁移范围

迁移：

- 租户、组织、用户、角色和权限。
- 设备、产品、摄像机和协议标识。
- 节点和部署配置。
- 数据集、模型元数据和模型文件。
- 算法任务和规则。
- 告警、抓拍、短片和录像索引。
- 监管事件、证据、时间线、复测和审计。
- MinIO 和其他权威媒体对象。

不迁移：

- Redis 缓存。
- 在线播放会话。
- 临时 Token。
- 运行中线程和进程状态。
- 已过期临时下载 URL。
- 可从业务真源重建的 Read Model。

### 26.2 迁移方法

1. 建立数据源清单和数据字典。
2. 生成 canonical UID 映射。
3. 清理重复、孤儿和无效关联。
4. 搭建 V2 独立数据库和对象存储。
5. 执行全量预复制。
6. 在旧系统运行期间持续同步增量数据库和对象。
7. 至少执行三次完整演练。
8. 正式窗口停止业务写入。
9. 排空消息和任务。
10. 执行最终增量。
11. 校验计数、外键、哈希和业务抽样。
12. 启动 V2、预热模型并执行冒烟。

### 26.3 校验

- 每张关键表源目标计数。
- 主键和 UID 唯一性。
- 外键完整性。
- 业务状态分布。
- MinIO 对象数量、大小和 SHA-256 抽样或全量校验。
- 用户、设备、模型、任务、告警、事件和播放端到端抽样。
- 审计记录和证据签名验证。

## 27. 一次性切换和回滚

### 27.1 切换前

- V2 所有硬门禁通过。
- 数据迁移演练三次通过。
- 业务、运维和实施人员完成演练。
- 旧系统完整快照可恢复。
- 明确 Go/No-Go 负责人。
- 客户维护窗口和通知完成。

### 27.2 切换步骤

1. 进入维护模式。
2. 停止创建新任务和写入业务。
3. 排空 Kafka/MQTT/Outbox。
4. 停止旧实时任务。
5. 生成最终数据库和对象快照。
6. 执行最终增量迁移。
7. 完成自动校验。
8. 启动 V2 数据、消息、控制、媒体和推理服务。
9. 预热模型。
10. 执行健康、API、媒体、推理、告警和事件冒烟。
11. Go/No-Go。
12. 开放业务流量。
13. 旧系统进入只读保留。

### 27.3 三个承诺点

1. 开放流量前：可完整回滚到旧系统。
2. 开放后短窗口：冻结 V2，导出关键新增业务增量并回放旧系统。
3. 承诺点后：不再承诺任意时刻全量反向迁移，采用修复前进和 V2 备份恢复。

短窗口默认两小时。正式项目可以缩短但不能在未验证的情况下延长。

## 28. 实施阶段和门禁

### 阶段 0：架构和基线，20 至 30 人日

交付：

- ADR。
- Contracts 目录。
- 数据所有权矩阵。
- Canonical UID 方案。
- 性能基线。
- NATS M0 测试方案。

门禁：

- 所有关键契约评审通过。
- 当前性能基线可重复。

### 阶段 1：控制面和模型供应链，45 至 65 人日

交付：

- TaskSpec。
- Scheduler/Reconciler。
- Lease/Epoch。
- ModelPackage。
- 节点预取和预热。

门禁：

- 节点故障无双任务副作用。
- 模型包可验证、可回滚。

### 阶段 2：VideoCore CPU/TensorRT，90 至 130 人日

交付：

- FrameBuffer/TensorBuffer。
- ORT CPU。
- TensorRT。
- FrameHub。
- Graph Executor。
- Result Outbox。

门禁：

- 固定模型 Golden Vector 通过。
- 16 路性能基线通过。
- 72 小时稳定性通过。

### 阶段 3：MEDIA 拆分，40 至 60 人日

交付：

- MEDIA API。
- Sticky Lease。
- Media Node 注册。
- Upload Worker。
- 播放和录像兼容。

门禁：

- GB28181、RTSP、RTMP、HTTP-FLV 和 WebRTC 端到端通过。
- 媒体节点失效恢复通过。

### 阶段 4：事件可靠性和领域适配，45 至 65 人日

交付：

- NATS/Kafka Transport Adapter。
- Inbox/Outbox。
- Alert Evidence。
- Supervision Domain Adapter。
- Dead Letter 和补偿。

门禁：

- 故障注入无业务数据丢失和重复副作用。

### 阶段 5：边缘兼容，20 至 30 人日

交付：

- Edge Compatibility Gateway。
- 加密 SQLite/WAL。
- MQTT/NATS 桥接。

门禁：

- 两小时断网补偿通过。

### 阶段 6：商业化和集群，55 至 80 人日

交付：

- Appliance 安装包。
- Site HA 部署。
- License。
- OTel。
- 备份恢复。
- 升级控制器。
- 诊断包。

门禁：

- 集群故障、升级、恢复和安全测试通过。

### 阶段 7：迁移和切换，45 至 65 人日

交付：

- 数据迁移工具。
- 校验报告。
- 三次演练。
- 正式切换手册。

门禁：

- 生产验收和 30 天试运行通过。

## 29. 测试策略

### 29.1 单元和组件测试

- TaskSpec 编译。
- Scheduler 评分和配额。
- Lease/Epoch。
- Frame/Tensor 所有权。
- 插件加载。
- Inbox/Outbox。
- ID 映射。
- 数据生命周期。

### 29.2 契约测试

- OpenAPI。
- Protobuf。
- JSON Schema。
- Event Envelope。
- ModelPackage。
- N/N-1 兼容。

### 29.3 集成测试

- MEDIA 到 VideoCore。
- VideoCore 到消息系统。
- Alert 到 Supervision。
- Model Factory 到节点。
- Edge Offline 到中心补发。
- 对象上传和数据库事务。

### 29.4 性能测试

- 单模型、单任务。
- 多模型共享。
- 16 路固定组合。
- CPU 与 TensorRT 对比。
- 动态批处理延迟。
- 两小时积压追赶。
- 72 小时稳定性。

### 29.5 故障注入

- 杀死 VideoCore Worker。
- 杀死 Supervisor。
- GPU 错误。
- Media Node 断开。
- NATS 节点故障。
- PostgreSQL 主库切换。
- MinIO 节点故障。
- 网络分区。
- 磁盘接近阈值。
- NODE/EDGE 断网两小时。

### 29.6 安全测试

- 越权和租户隔离。
- mTLS 和证书吊销。
- 密钥轮换。
- 对象签名 URL。
- 恶意模型包和签名失败。
- 镜像漏洞和 SBOM。
- 诊断包脱敏。

### 29.7 迁移测试

- 全量和增量。
- 重复数据。
- 缺失对象。
- 非法外键。
- 中断续传。
- 回滚。
- 三次完整演练。

## 30. 验收标准

### 30.1 架构验收

- 模块职责与本文一致。
- 生产推理不再由 VIDEO/AI 承担。
- TASK 旧进程退役。
- TaskSpec、ModelPackage、Event Envelope 已版本化。
- 数据所有权矩阵执行。

### 30.2 性能验收

- 固定硬件和任务组合通过。
- P95 小于 200ms。
- CPU 降低至少 50%。
- 72 小时稳定。
- 无不可恢复积压。

### 30.3 可靠性验收

- 至少一次投递。
- 业务副作用幂等。
- Lease Fencing 有效。
- 两小时离线补发。
- 备份恢复通过。

### 30.4 集群验收

- 单控制节点、消息节点、媒体节点和计算节点故障测试通过。
- 数据服务副本跨故障域。
- 滚动升级停止扩散和回滚通过。
- 多站点 WAN 断开不影响站点关键任务。

### 30.5 商业交付验收

- 安装包、升级包和离线依赖完整。
- License 和宽限策略通过。
- 支持矩阵、SBOM、发布说明齐全。
- 诊断包脱敏。
- 容量报告可生成。
- 运维手册和恢复手册完成。

### 30.6 领域合规验收

- Alert 与 Supervision Event 分离。
- 低可信身份阻止个人事件更新。
- 通知、复测和关闭状态不混淆。
- 证据完整性和审计通过。
- 辅助不定性标识存在。

## 31. 风险清单

| 风险 | 概率 | 影响 | 缓解措施 |
|---|---|---|---|
| NATS 不满足目标负载或运维 | 中 | 高 | M0 门禁，Kafka 持久事件回退 |
| VideoCore 工期低估 | 高 | 高 | 固定 CPU/TRT 范围，RKNN 后置 |
| 一次切换失败 | 中 | 极高 | 独立 V2、预复制、三次演练、承诺点 |
| 多数据库迁移质量差 | 高 | 高 | 数据字典、UID 映射、自动校验和抽样 |
| 模型结果跨后端不一致 | 中 | 高 | Golden Vector、容差和认证矩阵 |
| GPU 资源竞争 | 中 | 高 | 实时/训练资源池隔离和配额 |
| 媒体和推理网络瓶颈 | 中 | 高 | 压缩码流、站点亲和、容量测算 |
| 消息重复产生重复通知 | 中 | 高 | Inbox、按接收者投递记录和幂等键 |
| 插件崩溃影响节点 | 中 | 中 | C ABI、进程隔离和 Supervisor |
| 磁盘和日志增长 | 高 | 高 | 配额、生命周期、容量预警和恢复演练 |
| License 成为单点 | 低 | 高 | 本地签名验证、缓存和宽限期 |
| 支持矩阵过大 | 高 | 中 | 每 LTS 只认证有限组合 |
| 安全凭据泄漏 | 中 | 极高 | Secret Store、轮换、扫描和审计 |
| 现场网络长期不稳定 | 中 | 高 | Edge Store-and-Forward 和站点自治 |
| 团队并行导致契约漂移 | 高 | 高 | Contracts 仓库、兼容门禁和架构评审 |

## 32. 人员和投入

### 32.1 人员建议

| 角色 | 建议人数 |
|---|---:|
| 总体架构/技术负责人 | 1 |
| C++/VideoCore | 2 |
| Java 控制面和领域服务 | 2 |
| Python/AI/MEDIA | 1 至 2 |
| DevOps/SRE/安全 | 1 |
| 测试和性能 | 1 至 2 |
| 数据迁移 | 可由后端和测试兼任，指定 1 名负责人 |

核心团队 6 至 8 人，测试和实施在关键阶段增加。

### 32.2 工作量

| 工作包 | 人日 |
|---|---:|
| 架构、契约和控制面 | 70 至 100 |
| VideoCore CPU/TensorRT | 90 至 130 |
| MEDIA 和事件可靠性 | 60 至 90 |
| 数据迁移和领域适配 | 45 至 65 |
| 集群、升级、备份和恢复 | 55 至 80 |
| CI、安全、SBOM、诊断和支持矩阵 | 40 至 60 |
| 合计 | 360 至 525 |

日历周期建议 8 至 12 个月，生产试运行不少于 30 天。

## 33. 商业产品治理

### 33.1 版本

- 每年规划 LTS。
- LTS 明确支持周期。
- 安全修复和功能版本分离。
- API/Event/ModelPackage 支持 N/N-1。
- 发布 BOM 记录所有组件、驱动和依赖版本。

### 33.2 支持矩阵

每个版本明确：

- CPU 架构。
- NVIDIA GPU。
- 驱动和 CUDA。
- 操作系统。
- Kubernetes。
- PostgreSQL。
- MinIO/S3。
- 浏览器。
- 摄像机协议。
- 模型运行时。

未认证组合允许客户验证，但不承诺完整 SLA。

### 33.3 诊断和运维

一键诊断包包含：

- 版本和镜像摘要。
- 节点和硬件。
- 服务健康。
- 调度和任务。
- 消息积压。
- 数据库和对象存储状态。
- 磁盘和日志。
- 关键配置摘要。
- 脱敏日志和最近错误。

诊断包不得包含明文密码、Token、私钥和个人敏感数据。

### 33.4 成本

容量报价必须包括：

- 计算节点。
- GPU。
- 网络。
- 对象存储。
- 数据库和消息节点。
- 备份空间。
- 日志和指标空间。
- 软件支持和实施。
- 模型和编解码许可证。
- 安全测评。

不以“开源软件零许可证”代表整体软件成本为零。

## 34. 交付物

1. 架构设计规范。
2. ADR 集合。
3. Contracts 规范和兼容政策。
4. TaskSpec 和 ModelPackage Schema。
5. Event Envelope 和主题规范。
6. 数据所有权和 UID 映射。
7. VideoCore 设计和 SDK。
8. MEDIA API 规范。
9. 集群部署和容量手册。
10. 安全和 License 设计。
11. 可观测性和 SLO。
12. 数据迁移和切换手册。
13. 备份恢复手册。
14. 测试和验收报告。
15. SBOM、签名和发布 BOM。
16. Appliance 和 Site HA 安装包。
17. 诊断工具和支持手册。

## 35. 实施前置条件

启动实施前必须满足：

- 负责人确认本方案。
- 建立独立 V2 开发和测试环境。
- 固定首发硬件 BOM。
- 建立基础 CI。
- 明确生产数据规模。
- 完成安全和数据分级。
- 完成三类代表模型授权确认。
- 指定数据迁移负责人。
- 指定 Go/No-Go 负责人。
- 预留性能实验和切换演练环境。

## 36. 最终建议

本方案的核心价值不是把 yFeiEye 改成更多微服务，而是把现有能力收敛为可验证的产品边界：

- DEVICE/iot-node 负责期望状态和调度。
- MEDIA 负责媒体接入和流生命周期。
- VideoCore 负责节点内高性能推理。
- AI 负责模型供应链。
- 事件数据面负责可靠交付和业务幂等。
- 通用告警和监管事件通过领域适配器连接。
- EDGE 保持站点自治并逐步迁移 RKNN。
- 商业交付具备安装、升级、恢复、诊断、License 和支持矩阵。

架构拓扑在本版本冻结。首要工作不是继续增加组件，而是完成 NATS M0、TaskSpec、Lease Fencing、数据所有权、VideoCore CPU/TensorRT、CI 和恢复演练。只有这些 P0 门禁通过，项目才进入正式生产切换阶段。

## 附录 A：推荐内部端口

| 服务 | 推荐端口 | 说明 |
|---|---:|---|
| NODE Agent | 9100 | 保留现有 |
| VideoCore Control gRPC | 17100 | 内部，不对公网 |
| VideoCore Metrics | 17101 | 内部监控 |
| MEDIA API | 17200 | 内部服务 |
| NATS Client | 4222 | 内部 |
| NATS Cluster | 6222 | 集群内部 |
| NATS Monitoring | 8222 | 运维网络 |

端口可以由发布 BOM 调整，但不得与 NODE Agent 和现有公共端口冲突。

## 附录 B：术语

| 术语 | 定义 |
|---|---|
| Control Plane | 保存期望状态并负责调度、策略和治理的控制层 |
| Data Plane | 实际处理媒体、解码和推理的站点本地执行层 |
| TaskSpec | 不可变、版本化的视觉任务定义 |
| Execution Plan | TaskSpec 校验和编译后的节点执行计划 |
| Lease | 有过期时间的任务或媒体节点分配 |
| Epoch | 每次重新分配递增的防重复代数 |
| Fencing | 拒绝旧实例继续产生有效副作用的机制 |
| ModelPackage | 带 Manifest、校验、签名和 Golden Vector 的模型发布包 |
| Inbox | 消费侧业务幂等记录 |
| Outbox | 事务内持久化的待发布消息 |
| AlertEvidence | 由推理结果形成的告警证据 |
| Supervision Event | 具有责任、复测和关闭门禁的监管事件 |
| Read Model | 由事件构建、可重建的查询视图 |
| Appliance | 单机商业交付形态 |
| Site HA | 站点高可用商业交付形态 |
| Regional | 多站点区域管理形态 |

## 附录 C：评审结论模板

| 评审项 | 结论 |
|---|---|
| 架构合理性 | 控制面、数据面、事件面和领域边界清晰 |
| 性能 | 以固定硬件、模型和推理频率验证 |
| 可扩展性 | 媒体、推理、控制和存储可独立扩容 |
| 高效性 | 单次解码、引擎共享、资源亲和和有界队列 |
| 可落地性 | 复用现有能力，分阶段建设，一次生产切换 |
| 商业化 | 安装、升级、恢复、License、诊断和支持矩阵纳入范围 |
| 集群部署 | Appliance、Site HA、Regional 三种形态 |
| 主要残余风险 | 一次性切换、VideoCore 工期、数据质量和团队契约治理 |
