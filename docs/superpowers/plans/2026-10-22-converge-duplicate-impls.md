# 平台重复实现收敛：实施计划

对应设计：`docs/superpowers/specs/2026-10-22-converge-duplicate-impls.md`

## 第一阶段（本分支实施）

每步单独提交，每个提交都要能通过对应模块的单测。

### 步骤 1：文档
- 提交设计文档与本计划。

### 步骤 2：Python 告警入站契约（VIDEO）
1. 新增 `testdata/contracts/alert_flat_hook.json`、`alert_bus_nested.json`、`alert_event_id_only.json`、`alert_without_key.json`，每个样例带 `_expect` 字段，写明期望的幂等键（缺失时为 `null`）。
2. 新增 `VIDEO/app/utils/alert_ingress_contract.py`：
   - `ALERT_IDEMPOTENCY_KEY_FIELDS`、`ALERT_IDEMPOTENCY_KEY_MAX_LEN = 36`
   - `resolve_alert_idempotency_key(data) -> Optional[str]`
   - `ensure_alert_idempotency_key(data) -> str`（缺失时写入 `correlation_id = uuid4`）
   - `validate_alert_ingress(data) -> List[str]`（`device_id_missing`、`correlation_id_too_long`）
   - `apply_alert_identity_fields(message, data)`（Kafka 消息的 `correlationId`/`eventId`/`schemaVersion`）
3. `alert_hook_service.process_alert_hook`：feed_only 分支之后先校验，再补幂等键；两个 Kafka 构建函数改用 `apply_alert_identity_fields`；`_mark_task_notification_sent` 的事件 ID 改用统一键。
4. `algo_mqtt_bus._normalize_alert_payload` / `publish_alert` 改用统一解析与补键，并保留 `event_id`。
5. `alert_service.create_alert` 改用 `resolve_alert_idempotency_key`。
6. 单测 `VIDEO/tests/test_alert_ingress_contract.py`：读共享样例；验证 MQTT 归一、Kafka 两种消息、直连落库三条路径得到同一个键；验证 hook 在 Kafka 失败后的兜底与 Kafka 消息使用同一个 `correlation_id`；验证拒绝路径。
7. 在测试服务器 `python:3.11-bullseye` 容器里跑 `VIDEO/tests` 全量（按 `pr-tests.yml` 的依赖清单安装）。

### 步骤 3：iot-sink 扁平归一（DEVICE）
1. 新增 `com.basiclab.iot.sink.util.AlertIdempotencyKeys`：`resolve(JsonNode)`、`copyInto(JsonNode src, ObjectNode dst)`。
2. `IotAlgoBusMqttHandler.normalizeAlertPayload` 用 `copyInto` 替换原来只复制 `correlation_id` 的 5 行。
3. 单测 `AlertIdempotencyKeysTest` 读取 `testdata/contracts/alert_*.json`，断言与 Python 端相同的期望键。
4. 测试服务器 `maven:3.9-eclipse-temurin-21` 容器：`mvn -pl iot-sink/iot-sink-biz -am test -Dtest=AlertIdempotencyKeysTest,AlertEventGrouperTest,AlertTenantPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false`，再跑 iot-sink-biz 全部单测。

### 步骤 4：收尾
- `node .scripts/verify-repository-secret-hygiene.mjs`
- 检查 LF / 无 BOM
- 在共享笔记目录写交接说明

## 第二阶段（需编译验证，另开分支）
- RUNTIME：`correlation_id` 改 UUID，归一回落 `event_id`；`RUNTIME/tests` + 服务器编译。
- 删除 `alert_consumer_service` 中失效的 Kafka 消费循环。
- VIDEO 认证密钥：未配置时 edge 认证拒绝启动。
- 类别归一共享样例（Python / Java / C++ 三端）。

## 第三阶段（依赖待决策 D1–D6）
- D3 通过后：HTTP hook 在非 mini 形态改发 MQTT 总线，移除 VIDEO 的 Kafka 告警生产者。
- D1 通过后：按选项迁移或下线 `EDGE/runtime`。
- D2 通过后：认证统一。
- D4 通过后：Python 执行器冻结清单与 RUNTIME 补齐计划。

## 风险与回退
- 步骤 2 的拒绝路径只拦截原本必然在落库时失败的输入；若现场有生产者依赖「缺 device_id 也返回 500 以外的结果」，回退 `validate_alert_ingress` 的调用即可，其余改动不受影响。
- 步骤 3 只影响「扁平且只带 `event_id`」的 MQTT 消息。
