# 平台重复实现的现状地图与收敛方案

- 日期：2026-10-22
- 分支：`claude/converge-duplicate-impls-df1efa`
- 范围：推理执行器、告警入站通道、告警与设备数据所在库、认证。只实施低风险、可单测验证的收敛，其余写入「待决策」。
- 约束来源：`AGENTS.md`（EDGE 去留未定，不删 EDGE，也不把新功能只做在 EDGE；RUNTIME 只做帧执行，VIDEO 负责编排）、`CONTEXT.md`、`docs/adr/0001`（事件中心与 Alert 分离）、`docs/adr/0006`。

> 本文的现状结论来自 2026-10-22 对代码的阅读（`git grep` + 逐文件核对），没有在运行环境里抓包验证。行号会随代码漂移，以函数名为准。

## 1. 现状地图

### 1.1 推理执行器（3 套）

| | VIDEO Python 执行器 | RUNTIME C++ 帧执行器 | EDGE Python 运行时 |
|---|---|---|---|
| 代码 | `VIDEO/services/{realtime,snapshot,patrol}_algorithm_service/run_deploy.py`（5579 / 2774 / 795 行） | `RUNTIME/src/`（`Detech.cpp`、`AlgoMqttBus.cpp`、`AlarmCallback.cpp`、`AlertClassFilter.cpp`、`YoloEngine.cpp` 等） | `EDGE/runtime/services/{realtime,snapshot,patrol}_algorithm_service/run_deploy.py`（4684 / 2663 / 694 行）+ `EDGE/runtime/lib/algo_mqtt_bus.py` |
| 由谁拉起 | VIDEO `algorithm_task_launcher_service`，任务 `executor=python` 时 | 同一个 launcher，`executor=cpp`（**默认值**，`runtime_config_service.normalize_executor` 对空值和未知值都返回 `cpp`）；`generate_runtime_inis` 生成 `task_{id}.ini` | `EDGE/edge/workload_runner.py`，收到 `mqtt/iot-algo-task-cmd` 指令后直接 `python run_deploy.py` |
| 与 VIDEO 的关系 | VIDEO 源码树内 | 独立二进制，VIDEO 编排 | **VIDEO 代码的分叉副本**：`EDGE/scripts/sync_runtime_from_video.sh` 从 VIDEO 拷贝后再打 `EDGE/runtime/overlays`。当前与 VIDEO 的差异：realtime 1345 行、snapshot 267 行、patrol 109 行 |
| 告警出口 | `app/utils/algo_mqtt_bus.publish_alert` → `mqtt/iot-alert-notification` / `mqtt/iot-snapshot-alert`（信封 `{version,msgId,msgType,tenant,ts,payload}`，payload 已归一成嵌套 `alert`） | `AlgoMqttBus::publishAlert`（同样的信封与嵌套归一）；总线关闭时 `AlarmCallback` 走 HTTP `POST /video/alert/hook`（edge standalone 无 EMQX） | `ALGO_BUS_TRANSPORT=mqtt` 时发**扁平** payload 到同一 topic（由 iot-sink 归一）；否则 HTTP `/video/alert/hook` |
| POST 定制后处理 | `build_infer_event` / `publish_infer_event` → `mqtt/iot-infer-event`（`infer_event.v1`）；POST 不可用时按 `POST_FAIL_STRATEGY` bypass 或 drop | `Detech.cpp` 生成同一契约；`AlgoMqttBus` 有对应的 post 探活 | 无 |
| correlation_id 生成 | 每次告警 `uuid4()` | `"<taskId>_<UTC 秒>"`（`Detech.cpp`、`AlarmCallback.cpp`） | 每次告警 `uuid4()` |
| 独有能力（代码中观察到的） | patrol、SAM 补全、姿态/意图分析、非 Linux 可运行；编译失败时的兜底 | 高性能解码/推理、硬解、`ai/{device}` 带框推流、多设备单进程、TaskManager | 脱离 VIDEO 进程在节点上自运行（enroll、MQTT 任务指令、`EDGE_SRS_*` 推流 overlay） |

同一逻辑在三处各写了一遍：

- **告警 payload 扁平→嵌套归一**：`VIDEO/app/utils/algo_mqtt_bus._normalize_alert_payload`、`RUNTIME/src/AlgoMqttBus.cpp::normalizeAlertPayload`、`DEVICE/iot-sink/.../IotAlgoBusMqttHandler.normalizeAlertPayload`。三处都**丢掉 `event_id`**，只认 `correlation_id`/`correlationId`。
- **告警类别过滤**：`VIDEO/app/utils/alert_class_filter.py`、`RUNTIME/src/AlertClassFilter.cpp`、`iot-sink/.../util/AlertClassFilter.java`。
- **事件分组（模型:类别）**：`VIDEO/app/utils/algorithm_task_identity.build_alert_event_groups`、`iot-sink/.../util/AlertEventGrouper.java`。
- **MQTT 发布客户端与 broker 解析**：VIDEO、EDGE、RUNTIME 各一份。
- **executor 归一**：`runtime_config_service.normalize_executor` 与 `algorithm_task_launcher_service` 中一段内联判断（未知值时两者结论不同：前者当 `cpp`，后者走 Python 路径）。

### 1.2 告警入站通道（3 条）

最终都写同一张表：**VIDEO 库（`iot-video20`）的 `alert` 表**。iot-sink 通过 `@DS("video")` 写它，VIDEO 直连落库也写它。该表有部分唯一索引 `uq_alert_correlation_id ON alert(correlation_id) WHERE correlation_id IS NOT NULL`（`.scripts/postgresql/iot-video10.sql`，`VIDEO/run.py` 启动时补建），所以 **`correlation_id` 就是事实上的跨通道幂等键**。

```
                ┌───────────── MQTT 算法总线（主路径，standard/full）──────────────┐
VIDEO py / RUNTIME / EDGE ──► mqtt/iot-alert-notification|iot-snapshot-alert ──► iot-sink IotAlgoBusMqttHandler
                                                                                  │  补通知配置（读 VIDEO 库）、类别过滤
POST（InferEvent→legacy alert，同 correlation_id）──► 同上 topic ─────────────────┤
                                                                                  ▼
                ┌──── HTTP hook（edge/mini，RUNTIME/EDGE 总线关闭时）────┐   AlertServiceImpl.processAlert
RUNTIME AlarmCallback / EDGE ──► POST /video/alert/hook ──► process_alert_hook   │  （correlationId||eventId 幂等）
                                  │ mini 或 ALERT_USE_DIRECT_PERSIST：直连落库 ──► alert 表 ◄─┘
                                  │ 否则：Kafka iot-alert-notification / iot-snapshot-alert
                                  │        └──► iot-sink AlertNotificationConsumer / SnapshotAlertConsumer ──► alert 表
                                  └ Kafka 失败或超时：_fallback_persist_on_kafka_failure 直连落库 ──► alert 表
iot-sink ──► Kafka iot-alert-notification-send ──► iot-message 发通知
iot-device 设备阈值告警 ──► 直接发 Kafka iot-alert-notification-send（不进 alert 表）
```

各通道的消息契约：

| 通道 | Topic / 端点 | 载荷 | 幂等键来源 |
|---|---|---|---|
| MQTT 算法总线 | `mqtt/iot-alert-notification`、`mqtt/iot-snapshot-alert`（`$share/algo-sink/...`） | 信封 + payload（嵌套 `alert` 或扁平 hook 字段，snake_case 为主） | payload `correlation_id`；`event_id` 在三处归一中被丢弃 |
| InferEvent → POST | `mqtt/iot-infer-event` | `infer_event.v1`（`POST/schemas/infer_event.v1.json`、`testdata/contracts/infer_*.json`） | `correlation_id` 必填（`POST/internal/contract/types.go` 校验），POST 映射回 legacy alert 时原样带上 |
| HTTP hook | `POST /video/alert/hook`（服务签名鉴权 `_authorize_alert_ingest`） | 扁平 hook 字段 | 生产者给什么用什么；**缺失时不补**，Kafka 超时后的直连兜底可能和迟到的 Kafka 消息各写一条 |
| Kafka | `iot-alert-notification`、`iot-snapshot-alert` | `AlertNotificationMessage`（camelCase + 嵌套 `alert`，`_build_minimal_alert_kafka_message` / `_build_notification_message_for_kafka` 两处各拼一遍身份字段） | `correlationId` = `correlation_id`；`eventId` = `event_id` 或回落 `correlation_id` |

幂等键的解析顺序在各处不一致：

| 位置 | 顺序 |
|---|---|
| VIDEO 直连落库 `alert_service.create_alert` | `correlation_id` → `correlationId` → `event_id` → `eventId` |
| Kafka 消息 | `correlationId` 只取 `correlation_id`/`correlationId`；`eventId` 取 `event_id`/`eventId`/`correlation_id` |
| iot-sink `AlertServiceImpl` | `correlationId` → `eventId` |
| 三处 MQTT 扁平归一 | 只取 `correlation_id`/`correlationId`，`event_id` 丢失 |
| 通知状态 `_mark_task_notification_sent` | `event_id` → `eventId` → `correlation_id` → `correlationId`（与上面相反） |

其它观察：

- `VIDEO/app/services/alert_consumer_service.py` 是一个消费 `iot-alert-notification` 的 Kafka consumer，但 `start_alert_consumer` 在仓库里没有任何调用方，是失效的重复消费者（其中的 `upload_image_to_minio` 仍被直连落库复用）。
- RUNTIME 的 `correlation_id = <taskId>_<UTC 秒>`：同一任务带多路摄像头时，不同摄像头在同一秒内的告警会得到相同的键，第二条会被当作「幂等命中」丢弃。修复需要改 C++ 并重新编译 RUNTIME，见 §4。
- `alert.correlation_id` 是 `varchar(36)`；超长的键在 Python 直连落库和 Java 落库时都会因数据库报错而丢失，目前入口不校验。

### 1.3 告警与设备数据所在的库

| 库（init SQL） | 与告警/事件相关的表 | 写入方 |
|---|---|---|
| `iot-video20`（`iot-video10.sql`） | `alert`、`face_match_record`、`plate_match_record`、`algorithm_task_notification_state` 等 | VIDEO、iot-sink（`@DS("video")`） |
| `iot-device20`（`iot-device10.sql`） | `device_event`、`device_threshold_alarm`、`device_alarm_strategy`、`product_event` | iot-device |
| `iot-flow20` | `flow_alert_record`、`flow_alert_route_rule` | iot-flow |
| `iot-gb2818120` | `wvp_device_alarm` | iot-gb28181 |
| `ruoyi-vue-pro20` | `system_notify_message`；`supervision_event*`（`iot-system/.../V20260701__supervision_event_closure_baseline.sql`） | iot-system |
| TDengine | 设备时序与属性 | iot-sink（`@DS("tdengine")`） |

告警记录分布在 4 个库、5 类表里，没有统一的告警模型；监管事件中心在 `ruoyi-vue-pro20`。按 ADR-0001/0006，`Alert` 只是事件中心的输入或证据，**不应**把这些表合并成一张「告警即事件」的表。可以收敛的是**告警输入契约**，存储层要不要合并需要拍板（§4）。

### 1.4 认证（2 套）

| | DEVICE 平台认证 | VIDEO 本地认证 |
|---|---|---|
| 代码 | ruoyi/yudao OAuth2：令牌表在 `ruoyi-vue-pro20`；网关 `iot-gateway/.../TokenAuthenticationFilter`；nginx `auth_request` → `/admin-api/system/auth/check-session` | `VIDEO/app/auth/`：`auth_manager.py`（SQLite + pbkdf2 用户库，空库时写入默认管理员种子）、`token_service.py`（HMAC 签名 JSON 令牌，进程内吊销表）、`auth_api.py`（`/video/auth/*` 与 WEB 兼容的 `/video/system/auth/*`） |
| 启用条件 | mini / standard / full | `is_video_auth_enabled()`：`EASYAIOT_DEPLOY_PROFILE=edge` 或 `VIDEO_AUTH_ENABLED=1` |
| 服务间 | `X-Iot-Rpc-Token`（`IOT_RPC_INTERNAL_TOKEN`） | `MEDIA_INTERNAL_TOKEN`、告警入站服务签名（`media_authorization_service`） |

另外还有 AI 的 `X-Internal-Token`（`AI_INTERNAL_TOKEN`，未配置时放行）。`token_service._secret()` 在没有设置 `VIDEO_AUTH_SECRET`/`SECRET_KEY` 时会回落到源码里的固定字符串，这是安全问题，列入 §4 的后续项（本分支不改认证）。

## 2. 目标形态

1. **执行器**：VIDEO 负责编排，RUNTIME 负责帧执行（默认且唯一需要新增能力的执行器）。VIDEO Python 执行器冻结为兜底（非 Linux、RUNTIME 编译失败、RUNTIME 尚未覆盖的能力），不再加新功能；它独有的能力逐项评估要不要补到 RUNTIME。EDGE 运行时在去留确定前保持现状，不再从 VIDEO 同步新功能。
2. **告警入站契约**：所有生产者（三套执行器、POST、HTTP hook）发出的告警遵循同一份「告警入站契约 v1」：
   - 幂等键 = `correlation_id`，按 `correlation_id` → `correlationId` → `event_id` → `eventId` 的顺序取第一个非空值（去首尾空白），长度 ≤ 36；
   - 进入系统的第一个边界（总线发布、HTTP hook）负责在缺失时补一个 UUID，之后的每一跳（Kafka、直连兜底、MQTT 归一、iot-sink）只透传、不另造；
   - 契约样例放在 `testdata/contracts/alert_*.json`，Python 与 Java 两端的单测读同一批样例。
3. **告警通道**：MQTT 算法总线 + iot-sink 是唯一主路径；HTTP hook 只服务没有 EMQX 的 edge standalone 和 mini 直连落库；VIDEO→Kafka 这一跳是否下线待决策。
4. **库**：`alert` 表继续作为算法/设备告警输入的唯一落点；设备阈值告警等其它告警类表要不要汇入、怎样进入事件中心，按 ADR-0001/0006 另行设计。
5. **认证**：统一到哪一套待决策（倾向 DEVICE OAuth2 为唯一身份源，edge 形态通过精简 iot-system 或离线令牌验证解决，见 §4）。

## 3. 本分支实施的收敛（第一阶段，低风险）

| # | 内容 | 涉及文件 | 行为变化 |
|---|---|---|---|
| A | 新增 Python 告警入站契约模块：幂等键解析、缺失时补 UUID、入站校验、Kafka 消息身份字段统一写入 | 新文件 `VIDEO/app/utils/alert_ingress_contract.py` | 无（新增） |
| B | HTTP hook 入口：feed_only 分支之后先校验（缺 `device_id`、幂等键超过 36 字符时返回 `rejected`），再补幂等键，使 Kafka 消息与 Kafka 失败后的直连兜底共用同一个 `correlation_id` | `VIDEO/app/services/alert_hook_service.py` | 以前缺 `correlation_id` 的 hook 告警在 Kafka 超时兜底时可能写两条，现在由唯一索引去重。被拒绝的两类输入原本也会在落库时失败 |
| C | 两个 Kafka 消息构建函数的身份字段改用同一个函数；`correlationId` 始终是统一幂等键（只带 `event_id` 时也会填上）；通知状态记录的事件 ID 改用统一幂等键 | `alert_hook_service.py` | `correlationId` 原来为空、只有 `eventId` 的消息，iot-sink 原本就回落到 `eventId`，落库结果不变 |
| D | MQTT 总线归一：`correlation_id` 取统一幂等键（可回落到 `event_id`），保留 `event_id`；`publish_alert` 在缺键时补 UUID 并写回调用方字典，重试时复用 | `VIDEO/app/utils/algo_mqtt_bus.py` | VIDEO 自身的生产者本来就带 `uuid4`，实际不受影响 |
| E | VIDEO 直连落库改用统一解析函数（顺序不变） | `VIDEO/app/services/alert_service.py` | 无 |
| F | iot-sink 扁平 payload 归一抽成纯函数 `AlertIdempotencyKeys.copyInto`：`correlation_id` 回落 `event_id`，并保留 `event_id` | 新文件 `DEVICE/iot-sink/.../util/AlertIdempotencyKeys.java`；`IotAlgoBusMqttHandler.java` 改 1 处调用 | 只影响「扁平且只带 `event_id`」的消息，原来会丢掉幂等键 |
| G | 共享契约样例与两端单测 | `testdata/contracts/alert_*.json`、`VIDEO/tests/test_alert_ingress_contract.py`、`DEVICE/iot-sink/.../util/AlertIdempotencyKeysTest.java` | 无 |

不在本分支做的：RUNTIME C++ 改动（需要编译验证）、EDGE 改动（去留未定）、删除死代码、改库结构、改认证。

## 4. 待决策

| # | 问题 | 选项 | 倾向 | 影响 |
|---|---|---|---|---|
| D1 | **EDGE 去留**（`AGENTS.md` 已列为待决策） | ① 废弃 EDGE Python 运行时，边缘算力统一走 RUNTIME 云边一体（`edge/integrated`）；② 保留 EDGE 但把 `EDGE/runtime` 改成直接复用 VIDEO 代码（打包时拷贝，不再维护分叉）；③ 维持现状 | ① 或 ② | ① 需迁移 iot-node 的 EDGE 登记接口、WEB 引导页、`COMPILE/pack_edge.sh`；C# 采集器（Modbus/OPC UA）与推理运行时可以分开决定 |
| D2 | **认证统一到哪一套** | ① DEVICE OAuth2 为唯一身份源，edge 形态部署精简 iot-system 或用离线签名令牌；② edge 继续用 VIDEO 本地认证，但令牌格式与校验接口对齐 OAuth2；③ 维持两套 | ① | 影响 edge 内存预算（≥2 GB）与 nginx 配置；与「统一流量入口」设计（`docs/superpowers/specs/2026-10-21-unified-ingress.md`，在其它分支）一起定 |
| D3 | **是否下线 VIDEO→Kafka 告警入站这一跳** | ① HTTP hook 在非 mini 形态改为转发到 MQTT 总线，Kafka 只保留 iot-sink→iot-message 的通知发送；② 维持 | ① | 去掉 `get_kafka_producer` 与两个 Kafka consumer 的告警入站职责；需要确认 TRANSFORM 等是否也订阅 `iot-alert-notification`（`TRANSFORM/.../SinkKafkaTopics.java` 有引用） |
| D4 | **VIDEO Python 执行器的定位** | ① 冻结为兜底，新能力只做 RUNTIME；② 两套长期并行 | ① | 需要先列出 Python 独有能力（patrol、SAM、姿态等）在 RUNTIME 的补齐计划 |
| D5 | **其它告警类表是否汇入 `alert` / 事件中心** | `device_threshold_alarm`、`wvp_device_alarm`、`flow_alert_record` 等 | 先只统一输入契约，存储不合并 | 涉及 ADR-0001/0006 与 supervision 拆分工作 |
| D6 | **executor 未知值的语义** | 统一为 `cpp`（与 `normalize_executor` 一致）或拒绝 | 拒绝（API 层校验）+ 运行时按 `cpp` | 行为变化很小，但属于产品语义 |

需要后续落地、但不需要拍板的事项：

- **RUNTIME 多设备 `correlation_id` 冲突**（会丢告警）：把 `<taskId>_<秒>` 换成 UUID（`AlgoMqttBus.cpp` 里已有 `makeUuid`），并在 RUNTIME 归一里回落 `event_id`。需要在测试服务器上编译 RUNTIME 并跑 `RUNTIME/tests`。
- **`VIDEO/app/services/alert_consumer_service.py` 的失效 Kafka 消费者**：保留 `upload_image_to_minio` 等被复用的函数，删除消费循环，同时清理 `fix_kafka_consumer_group.*` 脚本。
- **VIDEO 认证密钥的硬编码回落值**：未配置密钥时应拒绝启动 edge 认证，而不是使用固定字符串。
- **三处类别过滤与事件分组**：用 `testdata/contracts` 增加类别归一样例，让 Python、Java、C++ 三端测试读同一批样例。
