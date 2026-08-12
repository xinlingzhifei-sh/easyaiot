# Modbus RTU Demo

验证 yFeiEye `MODBUS_RTU`（RS-485）上行采集与下行写入。

## 前置

1. Sink 开启：`basiclab.iot.sink.protocol.modbus-rtu.enabled=true`
2. 产品协议选 `MODBUS_RTU`，点位用 `propertyCode` 绑定物模型属性
3. Sink 进程能打开串口（Docker 需 `devices` 映射）
4. 依赖：`pip install pyserial`

Windows 虚拟串口联调建议：

```text
从站模拟器 COM3  <->  Sink / 本脚本 COM4
```

## 上行（读）

```bash
python3 01_uplink_read.py --port COM4 --baud 9600 --unit 1 --address 0 --quantity 3
```

Linux：

```bash
python3 01_uplink_read.py --port /dev/ttyUSB0 --baud 9600 --unit 1 --address 0 --quantity 3
```

## 下行（写）

现场侧：

```bash
python3 02_downlink_write.py --port COM4 --address 1 --value 30
```

平台全链路：

```bash
python3 03_platform_set_properties.py \
  --token '<JWT>' \
  --device-id <设备ID> \
  --props '{"setpoint":30}'
```

## 建议点位

与 Modbus TCP Demo 相同：绑定 `temperature@0`、`setpoint@1`（可写）、`running` 线圈 `@0`（可写）。
下行键为物模型属性：`{"setpoint":30}`。
