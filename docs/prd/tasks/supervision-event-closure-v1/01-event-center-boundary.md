# 任务 01：监管事件中心边界与对象契约

类型：HITL
状态：ready-for-human-decision
Blocked by：None - can start immediately

## What to build

确认监管事件中心的技术归属、对象边界和跨模块契约。本任务不实现业务功能，但必须产出后续任务可以直接引用的对象和状态语义。

## Acceptance criteria

- [ ] 明确 `Alert` 只作为告警输入和证据来源。
- [ ] 明确 `Supervision Event` 是监管事件生命周期的权威载体。
- [ ] 明确 `Disposal Task`、`Action`、`Evidence Chain` 的边界。
- [ ] 明确 VIDEO、DEVICE/iot-message、WEB、iot-system 的接口和数据职责。
- [ ] 明确权限、审计和迁移边界。
- [ ] 冻结 `docs/prd/supervision-event-closure-v1-engineering-contract.md`，作为任务 02-09 的字段、接口、权限和规则种子依据。

## Implementation notes

- 不要把处置状态、复核记录、关闭校验塞进现有 `Alert`。
- 后续生理监测 V1 依赖此任务确定的事件语义。
- 工程契约不新增业务范围，只把字段、接口、幂等、权限和初始规则种子落到研发可执行粒度。

## Suggested verification

- 人工评审对象契约。
- 检查任务 02-09 是否能引用同一套事件和状态语义。
