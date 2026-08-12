# 任务 03：上游生理输入归一化为 Physiology Sample

类型：AFK
状态：ready-for-agent
Blocked by：任务 02
User stories covered：15, 16, 25

## What to build

实现上游生理输入的最小接入契约，并归一化为 `Physiology Sample`。该切片完成后，系统可以接收一条来自红外、近红外、热成像设备或厂商 SDK 的生理结果，形成带来源、点位、时间窗、指标、质量标签、置信度、模型或设备版本的样本记录。

## Acceptance criteria

- [ ] 可注入或接收一条上游生理输入。
- [ ] 系统生成统一 `Physiology Sample`。
- [ ] 样本包含来源、点位、时间窗、呼吸、心率、温度、质量标签、置信度、模型或设备版本。
- [ ] 无效样本返回可解释错误，不创建个人事件。
- [ ] 提供样本查询或调试入口，便于后续任务复用。

## Implementation notes

- V1-P0 优先接上游设备或厂商 SDK，不要求先完成自研 worker。
- `Physiology Sample` 只是样本窗口，不等于个人生理事件。
- 样本必须能引用任务 02 的点位。

## Suggested tests

- 合法输入生成样本。
- 缺少点位或指标时拒绝输入。
- 样本版本信息可追溯。
- 样本查询返回质量标签和置信度。
