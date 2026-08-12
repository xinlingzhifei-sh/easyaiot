# 任务 04：可信 person_id 绑定与身份不可信降级

类型：AFK
状态：ready-for-agent
Blocked by：任务 03
User stories covered：13, 14, 15

## What to build

实现从身份候选到可信 `person_id` 的最小绑定规则。该切片完成后，系统能区分人脸匹配、床位绑定、点位绑定、`correlation_id` 等身份候选和监管业务确认后的可信身份；只有可信身份才能进入个人生理事件创建流程。

## Acceptance criteria

- [ ] 身份候选可以被记录为事件或样本证据，但不会直接生成个人生理事件。
- [ ] 接入或模拟监管台账/业务确认后的可信 `person_id`。
- [ ] 可信 `person_id` 存在且样本质量合格时，样本允许进入事件创建流程。
- [ ] 无可信 `person_id` 时，样本进入匿名质量统计或人工复核队列。
- [ ] 身份候选、确认过程和降级原因可审计。

## Implementation notes

- `FacePerson.id`、床位绑定、点位绑定、`correlation_id` 都不是天然可信身份。
- 该任务只做最小可信身份通路，不需要完整人员台账重构。
- 不要把身份候选写入个人基线。

## Suggested tests

- 只有人脸候选时不生成个人事件。
- 可信 `person_id` 存在时允许继续创建事件。
- 无可信身份时进入降级记录。
- 审计能追踪身份确认来源。
