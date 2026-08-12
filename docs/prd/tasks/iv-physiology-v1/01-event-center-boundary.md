# 任务 01：确认生理专项接入监管事件闭环的数据边界

类型：HITL
状态：ready-for-human-decision
Blocked by：None - can start immediately
User stories covered：30

## What to build

完成生理专项接入监管事件处置闭环 V1 的数据边界和最小契约决策。该任务不要求实现业务功能，但必须产出后续开发可以直接使用的决策文档和契约草案，防止生理事件、医务复测、证据链和关闭校验另起一套与公共监管事件闭环冲突的语义。

## Acceptance criteria

- [ ] 明确生理红色急症复用 `Supervision Event` 和 `Disposal Task`。
- [ ] 明确 `Alert` 只作为算法或设备告警输入和证据来源，不承担监管事件关闭语义。
- [ ] 明确 `Physiology Sample`、可信 `person_id`、红色规则、质量标签如何进入事件证据链。
- [ ] 明确 VIDEO、DEVICE/iot-sink、WEB、iot-system 之间的样本、动作、证据、readiness 契约。
- [ ] 输出生理专项迁移、接口、权限、审计的实现边界，供任务 02-15 直接引用。

## Implementation notes

- 不要在本任务里实现大量代码。
- 决策应遵守 ADR：监管事件闭环为产品核心、统一事件中心、可信 person_id、真实医务适配器优先、上游样本归一化、最小治理入口。
- 重点交付“后续任务不会互相踩边界”的清晰契约。

## Suggested verification

- 人工评审事件模型决策文档。
- 检查后续任务是否都能引用同一套对象和状态语义。
