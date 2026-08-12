# MQTT 上下行联调演示 — 详细设计文档

> 目录：`.scripts/mqtt-demo/`  
> 目的：用可运行的 Python 脚本模拟真实设备，验证 yFeiEye「设备 ↔ EMQX ↔ iot-sink ↔ Kafka ↔ PG/TDengine ↔ Web」链路已打通，并在管理端页面看到可观察的数据变化。

---

## 1. 背景与目标

### 1.1 背景

平台数据采集默认走 **EMQX 外部 Broker**，而不是 Web 直连 MQTT：

- **上行**：设备 Publish → EMQX → `iot-sink` 订阅 `/iot/#` → 解码 → Kafka → 存储（PG 影子/在线状态 + TDengine 时序）→ Web 查询展示  
- **下行**：Web/API 发令 → `iot-device` → `IotDownstreamMessageApi` → Kafka 网关 Topic → `iot-sink` EMQX 下行订阅器 → Publish 到设备 Topic → 设备 Subscribe 收到

群友反馈曾出现：消息总线循环依赖、订阅未注册、TDengine 入库/建表、无脚本配置页等问题；已在工程侧对齐参考实现。本目录脚本用于**端到端验收**，避免只看代码无法证伪。

### 1.2 设计目标

| 编号 | 目标 | 成功标准 |
|------|------|----------|
| G1 | 证明上行打通 | 脚本周期上报后，设备影子 / 运行状态 / 历史出现变化，设备 ONLINE |
| G2 | 证明下行打通 | Web「下发服务」或 HTTP `invokeService` 后，脚本终端打印下行 Topic/Payload |
| G3 | 证明下行闭环（可选） | 设备自动上行 service response，链路完整 |
| G4 | 可复现、可配置 | 全部关键参数走 CLI，不写死环境 |

### 1.3 非目标

- 不替代产线设备 SDK / 合规认证压测  
- 不覆盖 Alink `/alink/...` 专用 Topic（本演示固定走标准 `/iot/...`）  
- `01/02/03` 不强制配置协议 JS（标准 JSON 即可）；私有协议请用 `04/05` + 产品协议脚本

### 1.4 网关 / 子设备（已支持）

仅网关有公网、子设备无直连时：

| 能力 | Topic（路径中为**网关** product/device） | 说明 |
|------|------------------------------------------|------|
| 拓扑添加 | `/iot/{gwP}/{gwD}/topo/upstream/add` | 自动创建 SUBSET 并写 `parent_identification` |
| 拓扑删除 | `/iot/{gwP}/{gwD}/topo/upstream/delete` | 清空绑定，不删设备档案 |
| 子设备状态 | `/iot/{gwP}/{gwD}/topo/upstream/status` | 更新子设备 ONLINE/OFFLINE |
| 代报属性 | `/iot/{gwP}/{gwD}/sub/property/upstream/report` | payload 带 `productIdentification`/`deviceIdentification`/`properties`；不存在则自动创建 |
| 代报事件 | `/iot/{gwP}/{gwD}/sub/event/upstream/report/{id}` | 同上 |
| 代控服务 | `/iot/{gwP}/{gwD}/sub/service/downstream/invoke/{id}` | Web 对子设备下发时平台改写到此 Topic |
| 代设属性 | `/iot/{gwP}/{gwD}/sub/property/downstream/desired/set` | 同上 |
| 服务回执 | `/iot/{gwP}/{gwD}/sub/service/upstream/invoke/{id}/response` | 网关代回 |
| 属性 ACK | `/iot/{gwP}/{gwD}/sub/property/upstream/desired/set/ack` | 网关代回 |

演示脚本：`06_gateway_uplink_subdevice.py` / `07_gateway_downlink_subdevice.py`。

---

## 2. 总体架构

```text
┌─────────────┐  MQTT Publish (/iot/.../property/upstream/report)
│ Python 模拟  │ ──────────────────────────────────────────────┐
│ 设备脚本     │ ◄─────────────────────────────────────────────┤
└─────────────┘  MQTT Subscribe (/iot/.../service/downstream/#) │
                                                               ▼
                                                         ┌──────────┐
                                                         │  EMQX    │
                                                         │ :1883    │
                                                         └────┬─────┘
                                sink 客户端订阅 /iot/#          │
                                sink 客户端下行 Publish          │
                                                               ▼
┌──────────┐   HTTP/Feign    ┌────────────┐   Kafka    ┌────────────┐
│ Web 管理端 │ ─────────────► │ iot-device  │ ─────────► │  iot-sink  │
│ 影子/服务  │ ◄───────────── │ 命令/查询   │ ◄───────── │ 编解码/入库 │
└──────────┘                 └────────────┘            └─────┬──────┘
       ▲                                                     │
       │              查询 API                               │
       └────────────── iot-tdengine / PG ◄───────────────────┘
                      (st_property_upstream_report / shadow)
```

### 2.1 关键进程依赖

| 组件 | 作用 | 本地关键配置参考 |
|------|------|------------------|
| EMQX | MQTT Broker | `1883`；可选 HTTP Auth → sink `:8090` |
| Kafka | 设备消息总线 | sink `IotKafkaMessageBus` |
| iot-sink | 上下行协议网关 + 入库 | `iot.gateway.protocol.emqx.enabled=true` |
| iot-device | 元数据、下发命令、影子查询 | `/deviceCommand`、`/device/{id}/invokeService` |
| iot-tdengine | 历史/运行状态查询 | 库 `iot_device`，超级表 `st_property_upstream_report`（按 TAG `device_identification` 查） |
| iot-gateway | 管理端 API 入口 | 如 `48080`，前缀 `/admin-api` |
| PostgreSQL | 设备、影子、日志 | `device` / `product` 表；**脚本中的产品/设备标识必须在此存在** |
| Web | 人工观察 | 设备详情各 Tab |

> **硬约束**：`--product` / `--device` 必须是「产品管理 / 设备管理」里真实存在的 `productIdentification` / `deviceIdentification`。  
> 若库中无此设备，sink 会尝试按产品自动建档（GATEWAY/COMMON）；产品不存在或类型为 SUBSET 时仍会忽略并打 warn。  
> 本文 **§6.4** 与 `env.example` 已按本地库 `iot-device20` 现有设备填好一套可直接复制的参数；换环境请先 SQL 自检再改。

---

## 3. 协议设计

### 3.1 Topic 规范（标准 `/iot`）

统一前缀：

```text
/iot/{productIdentification}/{deviceIdentification}/...
```

本演示用到的 Topic：

| 方向 | Topic | Method | 说明 |
|------|-------|--------|------|
| 上行 | `/iot/{p}/{d}/property/upstream/report` | `thing.property.post` | 属性上报（主验证） |
| 上行 | `/iot/{p}/{d}/event/upstream/report/{identifier}` | `thing.event.post` | 可选事件 |
| 上行 | `/iot/{p}/{d}/log/upstream/report` | `thing.log.post` | 可选日志 |
| 下行 | `/iot/{p}/{d}/service/downstream/invoke/{identifier}` | `thing.service.invoke` | 云端调服务 |
| 上行回执 | `/iot/{p}/{d}/service/upstream/invoke/{identifier}/response` | `thing.service.invoke` | 设备应答 |
| 下行 | `/iot/{p}/{d}/property/downstream/desired/set` | `thing.property.set` | 属性期望设置（页面/API 可能发） |

> 源码枚举：`DEVICE/iot-sink/.../IotDeviceTopicEnum.java`  
> 下行 Topic 组装：`IotMqttTopicUtils` / `DeviceServiceImpl.invokeService`

### 3.2 Payload（Topic Codec / JSON）

`IotTopicDeviceMessageCodec` 对 `/iot/**` 直接 JSON ↔ `IotDeviceMessage`。

**最低必填字段：**

```json
{
  "tenantId": 1,
  "requestId": "a1b2c3d4e5f60718",
  "method": "thing.property.post",
  "params": {
    "temperature": 23.5,
    "humidity": 56.2,
    "counter": 1
  }
}
```

约束说明：

1. **`tenantId` 必填**：`IotEmqxUpstreamHandler` 在解码前校验，缺失直接丢弃。  
2. **`method` 建议带**：与 Topic 标准映射一致；存储层也会按 Topic 标准化 method。  
3. **`params`**：属性上报的业务数据；影子与 TDengine `params` 列写入该对象的 JSON。  
4. 无需 `version: 1.0`（那是 Alink Codec 要求；本演示不走 `/alink/`）。

### 3.3 MQTT 连接鉴权

| 模式 | CLI | Username | Password | 适用场景 |
|------|-----|----------|----------|----------|
| `device`（默认） | `--auth-mode device` | `{deviceIdentification}&{productIdentification}` | 产品/设备密码 | EMQX 已配 HTTP Auth → sink `/mqtt/auth` |
| `broker` | `--auth-mode broker` | 默认 `emqx`（`--broker-user`） | 默认 `123456` | 本地未配设备 HTTP 鉴权，仅验证 Publish/Subscribe 通路 |

解析约定（sink）：`IotDeviceAuthUtils.parseUsername` →  
`usernameParts[0]=deviceIdentification`，`usernameParts[1]=productIdentification`。

**ClientId**（按脚本角色隔离，可并行）：

| 脚本 | 默认 ClientId |
|------|----------------|
| `01_uplink_property.py` | `demo-up-{device}` |
| `02_downlink_listen.py` | `demo-down-{device}` |
| `03_full_loop.py` | `demo-full-{device}` |
| `04_codec_uplink.py` | `demo-codec-up-{device}` |
| `05_codec_downlink.py` | `demo-codec-down-{device}` |

可用 `--client-id` 覆盖。HTTP 设备鉴权按 username（`device&product`）+ 密码定位设备，**不强制**与库表 `client_id` 相等（库中该字段常为空；也便于多连接演示）。

> 说明：即使设备用 `broker` 模式入连，只要消息发到 `/iot/...` 且 sink 已订阅，**上行业务仍可被 sink 消费**。`device` 模式才是接近生产的设备认证。

### 3.4 编解码与脚本

处理顺序（`IotDeviceMessageServiceImpl`）：

**上行 decode**

1. 从 Topic 解析 `productIdentification`  
2. 若产品已热加载 JS → `rawDataToProtocol(topic, bytes)` → 得到**平台标准 JSON 字节**  
3. 无脚本或返回空 → 原样交给 Codec  
4. Topic Codec 反序列化为 `IotDeviceMessage`

**下行 encode**

1. 将 `IotDeviceMessage` 转为 Map  
2. 若有脚本 → `protocolToRawData(topic, message)` → **直接作为 MQTT payload**（不再走 Codec）  
3. 无脚本 → Codec.encode 产出标准 JSON

**租户解析（EMQX 上行）**：标准 JSON 取 `tenantId`；私有协议可从 `EA|UP|<tenantId>|…` 或按 product/device 查库。

**结论**：

- `01/02/03` 走标准 JSON，**可不配脚本**  
- `04/05` 走紧凑文本私有协议 `EA|…`，**必须**在产品管理启用脚本（模板 `compact_text` 或文件 `product_script_compact_text.js`）

#### 3.4.1 私有协议 EA|…（04 / 05）

| 方向 | 帧格式 | 脚本函数 |
|------|--------|----------|
| 上行 | `EA\|UP\|<tenantId>\|<requestId>\|<TYPE>\|<k=v;k=v>` | `rawDataToProtocol` |
| 下行 | `EA\|DN\|<requestId>\|<TYPE>\|<identifier>\|<payload>` | `protocolToRawData` |

TYPE 上行：`PROP` / `EVENT` / `LOG` / `SVC_ACK` / `DESIRED_ACK`  
TYPE 下行：`SVC` / `SET`

验收步骤：

1. Web「产品管理 → 协议脚本」→ 应用「紧凑文本协议」→ 启用 → 保存并热加载  
   或：`python3 upload_product_script.py --product ... --product-id ...`  
2. `python3 04_codec_uplink.py ...` → 影子出现属性；sink 日志含 `JS 上行解码生效`  
3. `python3 05_codec_downlink.py ...` + Web 下发服务 → 终端打印 `EA|DN|...`（非 JSON），并自动 ACK

辅助模块：`protocol_ea.py`（设备侧编解码）、页面「模拟调试」可在不发 MQTT 时试跑脚本。

---

## 4. 数据落库与页面映射

### 4.1 上行属性路径

```text
Publish property/upstream/report
  → sink：Topic 解析 product/device + payload.tenantId
  → DeviceService.getDevice(product, device)   ← 库中必须存在，否则直接忽略
  → decode + Kafka iot_device_message
  → UpstreamSubscriber / Handler
  → DeviceDataStorageService
       ├─ PG：设备 ONLINE；属性上报同步写 shadow（extension）
       └─ TD：INSERT INTO iot_device.st_property_upstream_report_{safeDeviceId}
              USING st_property_upstream_report
              TAGS (device_identification=原始标识, tenant_id, product_identification)
```

**写入侧子表名**（仅表名清洗，TAG 仍用原始设备标识）：

- 基础名：`st_property_upstream_report_{deviceIdentification}`  
- 非法字符替换为 `_`；若首字符非字母/`_`，前缀 `d_`（如 `9720...` → `..._d_9720...`）

**查询侧**（运行状态 / 历史）：

- 查超级表 `st_property_upstream_report`，条件 `WHERE device_identification = '{原始设备标识}'`  
- **不要**把查询条件做成带 `d_` 前缀的子表后缀当 TAG  
- iot-tdengine Mapper 需忽略租户插件误拼 `tenant_id`（`@InterceptorIgnore(tenantLine=true)`）

**超级表初始化**：

- sink 启动 `TdSuperTableInitializer` 必须用 `DynamicRoutingDataSource` 取 `tdengine` 数据源建表（默认 `JdbcTemplate` 往往连的是 PG，会导致「库在、超级表为空」）  
- 也可手工执行 `.scripts/tdengine/tdengine_super_tables.sql`

### 4.2 Web 应对应看到的变化

| 页面 Tab | 数据来源 | 演示预期 |
|----------|----------|----------|
| 影子 Shadow | PG `device.extension.shadow` | `params` 随上报刷新（第一证据） |
| 运行状态 | TD 超级表最近一条 `params` JSON，按物模型 `propertyCode` 拆分 | **code 与 params key 一致**时才有数值；否则 `dataValue=null` |
| 历史 | 同上超级表按 TAG 过滤 | 多条 `report_time` |
| 事件 | 事件 Topic + 存储 | `--with-event` 时增加 |
| 日志 | 日志 Topic / append log | `--with-log` 时增加 |
| 服务 | 下行命令 + 可选 response | `02` 脚本打印下行；回执可闭环 |
| 设备列表在线状态 | PG connect status | ONLINE |

> `01_uplink_property.py` 已按本地产品物模型上报：`deviceId` / `serviceId` / `Vbatt` / `eventTime` / `PVAngle_X|Y|Z` / `RSSI`。  
> **运行状态页只认物模型 `propertyCode`**，params key 对不上就会全是 `null`（影子里仍能看到原始 JSON）。  
> 另：sink 写入 TDengine 时 NCHAR 必须加引号（Mapper 用 `isQuoted()`），否则 params 双引号会被吃掉变成非法 JSON。

### 4.3 下行路径

```text
Web「下发服务」或 POST /device/{deviceId}/invokeService
  → DeviceServiceImpl 组装 IotDeviceMessage
       method=thing.service.invoke
       topic=/iot/{p}/{d}/service/downstream/invoke/{serviceIdentifier}
       tenantId / deviceId(数字主键)
  → IotDownstreamMessageApi → Kafka 网关 Topic
  → IotEmqxDownstreamSubscriber（延迟 register）
  → IotEmqxDownstreamHandler → EMQX Publish
  → 02 脚本 on_message 打印
  → （可选）自动 Publish .../service/upstream/invoke/{id}/response
```

---

## 5. 目录与模块设计

```text
.scripts/mqtt-demo/
├── DESIGN.md                      # 本文档
├── env.example / .env             # 参数（start_mqtt_demo.sh 会加载）
├── common.py                      # 连接、Topic、Payload、发布工具
├── 01_uplink_property.py          # 直连上行
├── 02_downlink_listen.py          # 直连下行
├── 03_full_loop.py                # 直连上下行同进程
├── 04_codec_uplink.py / 05_...    # 私有协议编解码
├── 06_gateway_uplink_subdevice.py # 网关代报子设备（topo + 属性）
├── 07_gateway_downlink_subdevice.py # 网关代收子设备下行并 ACK
├── start_mqtt_demo.sh / stop_...
└── run/
```

### 5.1 `common.py`

职责：

- 统一 CLI：`--host/--port/--product/--device/--tenant-id/--password/--auth-mode/...`  
- 按 `client_id_role` 生成互不冲突的默认 ClientId（`up` / `down` / `full`）  
- MQTT 连接（兼容 paho-mqtt 1.x / 2.x Callback API）  
- Topic 拼装与标准 Payload 构造（强制 `tenantId`）  
- `build_demo_property_params`：对齐本地物模型 propertyCode  
- `publish_json` 同步等待 publish 完成，终端打印便于对照日志  

### 5.2 `01_uplink_property.py`

- 按 `--interval`（默认 3s）上报物模型对齐字段（Vbatt / PVAngle_* / RSSI 等）  
- 可选 `--with-event` / `--with-log`  
- `--rounds 0` 表示常驻，直到 Ctrl+C  

**验收**：盯 Web 影子与 ONLINE，不依赖下行。

### 5.3 `02_downlink_listen.py`

- 订阅 `/iot/{p}/{d}/#` 等下行相关 Topic  
- 默认 `--auto-reply`：收到 service invoke 后回 response  
- `--invoke-api`：用 JWT 调网关 `invokeService`，无需手点页面  

**验收**：终端出现 `[DOWN #n]`；Web 侧命令已发出。

### 5.4 `03_full_loop.py`

- 同进程既 Publish 属性又 Subscribe 下行  
- 可与 01 / 02 **并行**（独立 `demo-full-*` clientId）  
- 部署常驻默认三者一起起；单窗口冒烟也可只跑本脚本  

### 5.5 `start_mqtt_demo.sh` / `stop_mqtt_demo.sh`

- 并行常驻拉起 up / down / full（含 `--with-event --with-log`）  
- 部署钩子：`install_linux.sh` / `install_business_linux.sh` 成功后调用  
- 关闭自动启动：`EASYAIOT_ENABLE_MQTT_DEMO=0`  
- 只启部分：`MQTT_DEMO_SCRIPTS=up,down`  

---

## 6. 环境准备与运行手册

### 6.1 依赖

```bash
pip install paho-mqtt
cd /projects/new/easyaiot/.scripts/mqtt-demo
```

### 6.2 服务 checklist

- [ ] EMQX `1883` 可达  
- [ ] Kafka 可用，sink 消息总线为 kafka  
- [ ] `iot-sink` 已启动且 `emqx.enabled=true`，订阅含 `/iot/#`  
- [ ] `iot-device` / `iot-tdengine` / `iot-gateway` / Web 可用  
- [ ] TDengine 库 `iot_device` 下已有超级表（`SHOW iot_device.STABLES;` 非空）  
- [ ] 确认使用下方本地联调设备（或换环境后 SQL 自检通过）  
- [ ] 设备状态 ENABLE，`tenant_id` 与 `--tenant-id` 一致  

若曾用错误 schema 建过超级表：`CREATE IF NOT EXISTS` **不会改结构**，需手工 DROP 后再启 sink。

### 6.3 本地联调参数（iot-device20 现网数据）

| 参数 | 值 | 说明 |
|------|-----|------|
| productIdentification | `9820630576939008` | 产品「智能网关」 |
| deviceIdentification | `9720084293632004` | 设备标识 |
| tenantId | `1` | 与 `device.tenant_id` 一致 |
| password（device 鉴权） | `32423` | `product.password` |
| device 主键 ID | `57038` | 仅 `--invoke-api` / 页面设备详情 URL |
| api-base | `http://localhost:48080/admin-api` | 网关；前端反代多为 `/dev-api` |
| JWT | 浏览器登录后自行拷贝 | 仅 API 下行需要 |

自检 SQL：

```sql
SELECT id, product_identification, device_identification, tenant_id, device_status
FROM device
WHERE product_identification = '9820630576939008'
  AND device_identification = '9720084293632004'
  AND deleted = 0;
-- 期望一行：id=57038
```

换库/换设备时改参数前先跑同类查询；查不到就不要跑脚本。

### 6.4 推荐操作序列（验收上下行）

**步骤 0 — 一键常驻（推荐，部署后也会自动跑）**

```bash
cd /projects/new/easyaiot/.scripts/mqtt-demo
bash start_mqtt_demo.sh          # 并行 01+02+03
# bash stop_mqtt_demo.sh         # 停止
```

打开 Web → 设备 `57038`：影子 / 运行状态 / 历史 / ONLINE 应变；事件与日志会随 01 的 `--with-event --with-log` 出现。  
「服务 → 下发服务」时看 `run/logs/down.log`（或 full.log）应有 `[DOWN]` / `[ACK]`。

**步骤 A — 仅手动上行**

```bash
python3 01_uplink_property.py \
  --product 9820630576939008 \
  --device 9720084293632004 \
  --tenant-id 1 \
  --password 32423
```

本地无设备 HTTP Auth 时：

```bash
python3 01_uplink_property.py \
  --product 9820630576939008 \
  --device 9720084293632004 \
  --tenant-id 1 \
  --auth-mode broker \
  --password 123456
```

**步骤 B — 下行（可与 01/03 同时跑）**

```bash
python3 02_downlink_listen.py \
  --product 9820630576939008 \
  --device 9720084293632004 \
  --tenant-id 1 \
  --password 32423
```

Web → **服务 → 下发服务**。终端应打印：

```text
[DOWN #1] topic=/iot/9820630576939008/9720084293632004/service/downstream/invoke/xxx
payload=...
[ACK] 已回执服务 xxx
```

**步骤 C — API 下行（可选）**

```bash
python3 02_downlink_listen.py \
  --product 9820630576939008 \
  --device 9720084293632004 \
  --tenant-id 1 \
  --password 32423 \
  --invoke-api \
  --api-base http://localhost:48080/admin-api \
  --token 'JWT' \
  --device-id 57038 \
  --service-id <物模型服务标识>
```

**步骤 D — 单进程上下行**

```bash
python3 03_full_loop.py \
  --product 9820630576939008 \
  --device 9720084293632004 \
  --tenant-id 1 \
  --password 32423 \
  --rounds 0
```

---

## 7. 观测与排障

### 7.1 日志关键词

| 阶段 | 服务 | 关键词 |
|------|------|--------|
| 收到 MQTT | sink | `IotEmqxUpstreamHandler` / `收到 MQTT 消息` |
| 缺 tenantId | sink | `message is missing tenantId` |
| 总线注册 | sink | `上行消息订阅成功` / `EMQX 下行订阅器注册成功` |
| TD 写入 | sink | `TDEngine数据插入成功` / `超级表初始化` |
| 下行发布 | sink | `IotEmqxDownstreamHandler` |
| 命令组装 | device | `invokeService` / `服务调用消息发送成功` |

### 7.2 常见失败矩阵

| 现象 | 可能原因 | 处理 |
|------|----------|------|
| MQTT 连接失败 | Broker 不通 / 账号错 | 检查 1883；先试 `--auth-mode broker` |
| `not currently connected` / 频繁「已重连」 | 手动指定了相同 `--client-id`，或旧进程仍占 `demo-{device}` | `bash stop_mqtt_demo.sh`；勿给 01/02/03 指定同一 clientId；踢掉旧连接 |
| 部署后页面无实时数据 | mqtt-demo 未起 / EMQX 关 | 查 `EASYAIOT_ENABLE_MQTT_DEMO`；手动 `bash start_mqtt_demo.sh`；看 `run/logs/*.log` |
| sink：`设备(xxx/yyy) 不能为空` 或 warn「设备不存在」 | Topic 产品/设备标识在 PG 中不存在（常用了 `demo_product` 占位） | 改用平台真实标识；SQL 自检见 6.3 |
| 上行无页面变化 | payload 无 `tenantId` | 必传 `--tenant-id`，且与设备租户一致 |
| 上行无变化但 MQTT 成功 | sink 未订 `/iot/#` 或 Kafka 挂 | 查 sink `application-*.yaml` 与 Kafka |
| 影子不变、TD 有数 | storeToDevice 条件/deviceId | 确认 device 数字主键与消息关联成功 |
| TD：`Table does not exist`（子表） | 从未成功上行，或查询仍拼子表名且表未创建 | 先保证上行入库；查询应走超级表 + TAG |
| TD 超级表为空（`SHOW STABLES` 0 行） | `TdSuperTableInitializer` 连错数据源（打到 PG） | 升级 sink 后重启；或手工跑 `tdengine_super_tables.sql` |
| TD 插入失败 | 超级表不存在/schema 不一致 | 跑 DDL 或重启 initializer；必要时 DROP 旧表 |
| 运行状态全 null、`ts=0` | ① 无上报数据 ② 物模型 code ≠ params key ③ TD 查询失败被吞 | 先看影子；再对 params key；查 tdengine 日志 |
| 下行页面点了脚本无打印 | 设备未订阅 / 下行 Kafka 或 DownstreamSubscriber 未注册 | 确认 02 在跑；查 sink 启动日志 register |
| invokeService 401 | token 无效 | 重新登录取 JWT |
| invokeService 成功但无 MQTT | `IotDownstreamMessageApi` 未注入 / method-topic 错 | 查 device 与 sink 服务发现、Feign |

### 7.3 验收记录模板（建议手填）

```text
日期:
环境: local / dev
产品/设备/租户:
上行:  [ ] MQTT 成功  [ ] 影子变化  [ ] ONLINE  [ ] 历史有点
下行:  [ ] 终端收到 DOWN  [ ] Topic 含 service/downstream/invoke
回执:  [ ] ACK 已发送（可选）
备注:
```

---

## 8. 安全与注意项

1. **不要把真实 JWT/密码提交进仓库**；`env.example` 仅作占位。  
2. `broker` 模式仅用于开发联调，生产必须走设备 HTTP Auth + ACL。  
3. Payload 禁止伪造其它租户 `tenantId` 做越权测试于共享环境。  
4. QoS 默认 1，与 sink 本地配置一致；压测时可调，但不改变业务语义。  
5. 勿把演示脚本的 `--client-id` 设成与真实在线设备相同，否则会互踢。  
6. 关闭部署自动演示：`EASYAIOT_ENABLE_MQTT_DEMO=0`。

---

## 9. 与平台代码的对应关系（便于二次维护）

| 能力 | 代码位置 |
|------|----------|
| 上行 MQTT 入口 | `IotEmqxUpstreamHandler` |
| 下行 MQTT 出口 | `IotEmqxDownstreamHandler` |
| 下行总线订阅（防循环依赖） | `IotEmqxDownstreamSubscriber` + `SmartInitializingSingleton` |
| 上行总线订阅 | `IotUpstreamMessageSubscriber` |
| Topic / Method | `IotDeviceTopicEnum` / `IotDeviceMessageMethodEnum` |
| 存储 | `DeviceDataStorageService` |
| TD 超级表初始化 | `TdSuperTableInitializer`（必须走 tdengine 数据源，勿用 primary JdbcTemplate） |
| TD 运行状态/历史查询 | `TdEngineMapper.getLastRowsListByIdentifier`（超级表 + TAG） |
| 设备缓存查找 | `DeviceServiceImpl.getDevice`（查无返回 null，不抛缓存异常） |
| 服务下发 API | `DeviceController.invokeService` / `DeviceCommandController` |
| 网关路由 | `iot-gateway` → `/admin-api/device/**`、`/admin-api/sink/**`、`/admin-api/tdengine/**` |
| 前端服务下发 | `WEB/.../devices/components/Service/index.vue` |
| 产品脚本配置 | `WEB/.../product/components/ProductScript.vue` + sink `ProductScriptController` |

---

## 10. 网关代报 / 代控验收（06 / 07）

前置：库中已有 **GATEWAY** 设备（可用 §6.3 智能网关）+ **SUBSET** 产品（`productType=SUBSET`）。子设备标识可不预先建档。

```bash
# .env 示例
# PRODUCT/DEVICE = 网关
# SUB_PRODUCT = 已有 SUBSET 产品标识
python3 06_gateway_uplink_subdevice.py \
  --product 9820630576939008 --device 9720084293632004 --tenant-id 1 --password 32423 \
  --sub-product <SUBSET产品标识> --sub-device demo-sub-001 --rounds 5

# 另一终端：网关监听子设备下行
python3 07_gateway_downlink_subdevice.py \
  --product 9820630576939008 --device 9720084293632004 --tenant-id 1 --password 32423 \
  --sub-product <SUBSET产品标识> --sub-device demo-sub-001
```

验收清单：

- [ ] 网关详情 →「子设备」出现 `demo-sub-001`（或属性上报自动创建）
- [ ] 打开子设备 → 影子随 06 上报变化
- [ ] 子设备「设备控制」下发 → 07 打印 `[DOWN]`，Topic 含 `/sub/`
- [ ] 07 自动 `[ACK]` 后，指令日志 PENDING → SUCCESS

常驻：`MQTT_DEMO_SCRIPTS=gw-up,gw-down` + 配置 `SUB_PRODUCT`。

## 11. 演进建议

1. 从 Web 登录接口自动取 token，减少手工拷贝 JWT。  
2. 增加对 TDengine REST 的只读校验脚本，做到「无页面也能断言入库」。  
3. 接入 CI：对 local compose 做 smoke（需测试账号与隔离租户）。  
4. EMQX ACL：限制网关仅能 publish/subscribe 已绑定子设备代理 Topic。

---

## 附录 A — Payload 示例

**属性上报**

```json
{
  "tenantId": 1,
  "requestId": "demo000000000001",
  "method": "thing.property.post",
  "params": {
    "temperature": 24.1,
    "humidity": 51.0,
    "counter": 7,
    "demoSource": "mqtt-demo/01_uplink_property.py"
  }
}
```

**服务回执**

```json
{
  "tenantId": 1,
  "requestId": "来自下行的requestId",
  "method": "thing.service.invoke",
  "params": { "result": "ok" },
  "data": { "success": true },
  "code": 0,
  "msg": "demo ok"
}
```

## 附录 B — 一键冒烟（可选）

```bash
cd /projects/new/easyaiot/.scripts/mqtt-demo

python3 03_full_loop.py \
  --product 9820630576939008 \
  --device 9720084293632004 \
  --tenant-id 1 \
  --password 32423 \
  --rounds 10

# 浏览器打开设备 57038 影子；服务页点一次下发，看终端 [DOWN]
```

---

**文档版本**：1.3  
**变更摘要（1.3）**：演示脚本 params 对齐物模型；说明运行状态空值与 TDengine NCHAR 引号问题。  
**适用范围**：yFeiEye 当前 `/iot` + EMQX + sink 消息总线架构
**维护**：换联调设备/物模型时同步更新 §4.2、6.3、6.4、附录 B、`01_uplink_property.py` 与 `env.example`
