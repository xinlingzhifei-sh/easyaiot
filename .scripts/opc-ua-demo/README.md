# OPC UA Demo

验证 yFeiEye `OPCUA` 上行采集与下行写入。

## 前置

1. Sink 开启：`basiclab.iot.sink.protocol.opcua.enabled=true`
2. 产品协议选 `OPCUA`，点位用 `propertyCode` 绑定物模型属性（不必与 NodeId 同名）
3. 依赖：`pip install asyncua`

## 本地服务器

```bash
python3 00_server_simulator.py --endpoint opc.tcp://0.0.0.0:4840/freeopcua/server/
```

注意：

- 启动日志里的证书 / encrypting policy 告警可忽略（SecurityPolicy=None + Anonymous）。
- 模拟器已固定为字符串 NodeId（`ns=2;s=...`）。若用旧版脚本，asyncua 可能生成 `ns=2;i=2` 等数字 ID，会导致平台读点失败、设备离线。
- 创建设备时填写：
  - Endpoint：`opc.tcp://127.0.0.1:4840/freeopcua/server/`（Sink 与模拟器同机）
  - 绑定 `temperature` → NodeId `ns=2;s=Temperature`
  - 绑定 `setpoint` → NodeId `ns=2;s=Setpoint`（可写）
  - 绑定 `running` → NodeId `ns=2;s=Running`（可写）
  - `protocolConfig.enabled` 必须为 `true`

## 上行（读）

```bash
python3 01_uplink_read.py --endpoint opc.tcp://127.0.0.1:4840/freeopcua/server/
```

平台侧等待采集周期后查看点位影子 / 运行状态，设备 ONLINE。

## 下行（写）

现场侧：

```bash
python3 02_downlink_write.py --node 'ns=2;s=Setpoint' --value 30
```

平台全链路（Web「寄存器操作」或 API）：

```bash
python3 03_platform_set_properties.py \
  --token '<JWT>' \
  --device-id <设备ID> \
  --props '{"setpoint":30}'
```

## requirements

```bash
pip install -r requirements.txt
```
