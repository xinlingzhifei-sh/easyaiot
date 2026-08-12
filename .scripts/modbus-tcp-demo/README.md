# Modbus TCP Demo

验证 yFeiEye `MODBUS_TCP` 上行采集与下行写入。

## 前置

1. Sink 开启：`basiclab.iot.sink.protocol.modbus.enabled=true`
2. 产品协议选 `MODBUS_TCP`，并维护物模型属性（如 `temperature` / `setpoint`）
3. 设备 `protocolConfig` 填写 host/port/unitId/points；点位用 `propertyCode` 绑定物模型属性（`identifier` 可为本地名）

可选种子：

```bash
psql -d iot-device -f .scripts/postgresql/industrial_protocol_seed.sql
```

## 本地从站

```bash
python3 00_slave_simulator.py --port 5020
```

创建设备时 host 填 Sink 可达地址（容器勿用 `127.0.0.1` 指宿主机），port=`5020`，unitId=`1`。

## 上行（读）

```bash
python3 01_uplink_read.py --host 127.0.0.1 --port 5020 --unit 1 --address 0 --quantity 3
```

平台侧：保存设备后等待两个采集周期，查看点位影子 / 运行状态，设备应变为 ONLINE。

## 下行（写）

现场侧直写：

```bash
python3 02_downlink_write.py --host 127.0.0.1 --port 5020 --address 1 --value 30
```

平台全链路（Web「寄存器操作」或 API）：

```bash
python3 03_platform_set_properties.py \
  --api-base http://localhost:48080/admin-api \
  --token '<JWT>' \
  --device-id <设备ID> \
  --props '{"setpoint":30}'
```

## 建议点位

| propertyCode（绑定） | identifier（可选） | function | address | writable |
|---|---|---|---:|---|
| temperature | temperature | HOLDING_REGISTER | 0 | 否 |
| setpoint | setpoint | HOLDING_REGISTER | 1 | 是 |
| running | running | COIL | 0 | 是 |

下行示例键与 `propertyCode` 一致：`{"setpoint":30}`。
