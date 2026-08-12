# 任务 13：P0 报表和统计口径

类型：AFK
状态：ready-for-agent
Blocked by：任务 08、任务 09、任务 10、任务 11、任务 12
User stories covered：18, 19, 20, 28, 29

## What to build

实现 P0 验收所需报表和统计口径。该切片完成后，报表能真实区分红色生理事件闭环结果，而不是输出一个含混的成功率。

## Acceptance criteria

- [ ] 报表展示红色事件总数、已关闭、待复测、超时升级。
- [ ] 报表区分 `real_success`、`manual_completed`、`manual_signed`、`simulated`、`blocked_by_customer`、`skipped_not_ready`。
- [ ] 报表区分复测真异常、复测误报、无法复测审批、身份不可信降级、低质量窗口降级。
- [ ] 成功率拆分为真实接口成功率、人工闭环完成率、复测完成率和关闭校验通过率。
- [ ] 报表数据能关联到事件和证据链。

## Implementation notes

- 人工替代、模拟、客户阻断、未 ready 不得计入真实接口成功。
- 报表应能支撑 P0 验收脚本 A-H。
- 先做 P0 验收口径，不做完整运营 BI。

## Suggested tests

- 各类动作状态统计正确。
- 误报和真异常统计分离。
- 成功率拆分正确。
- 报表可追溯事件明细。
