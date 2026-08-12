# 任务 08：医务、干警、领导动作队列与人工替代闭环

类型：AFK
状态：ready-for-agent
Blocked by：任务 06
User stories covered：1, 2, 3, 5, 8, 9, 18, 19, 20

## What to build

实现红色生理事件的动作队列。事件创建后，系统应生成医务动作、监区干警动作和值班领导关注动作，并根据 readiness 选择真实适配器、人工替代、模拟或不 ready 状态。

## Acceptance criteria

- [ ] 红色事件创建后生成医务、干警、领导三个方向的动作。
- [ ] 动作状态至少支持 `pending`、`sent`、`acknowledged`、`real_success`、`manual_completed`、`manual_signed`、`timeout`、`blocked_by_customer`、`skipped_not_ready`、`simulated`。
- [ ] 动作状态独立于事件状态。
- [ ] 通知发送成功不等于医务复测完成。
- [ ] 人工替代可完成业务处理，但报表不计入真实接口成功。

## Implementation notes

- 真实医务适配器优先，但 P0 必须允许人工替代闭环。
- 客户阻断、模拟和未 ready 必须成为一等状态。
- 动作记录应进入证据链。

## Suggested tests

- 创建红色事件后生成动作。
- readiness 为 manual_required 时走人工替代。
- 模拟动作不计入 real_success。
- 动作状态变更可审计。
