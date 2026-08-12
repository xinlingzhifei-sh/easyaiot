# 司法监管事件处置闭环 V1 开发任务包

来源：`docs/prd/supervision-event-closure-v1-prd.md`
开发列表：`docs/prd/supervision-event-closure-v1-development-list.md`
验收运行手册：`docs/prd/tasks/supervision-event-closure-v1/p0-acceptance-runbook.md`
使用方式：每个文件是一张可领取开发任务。AFK 任务可以直接实现；HITL 任务先完成决策产物，再解锁后续开发。

## 任务索引

1. `01-event-center-boundary.md` - 监管事件中心边界与对象契约 - HITL
2. `02-alert-to-event-rules.md` - 告警转监管事件规则 - AFK
3. `03-supervision-event-model.md` - 监管事件模型与状态机 - AFK
4. `04-disposal-task-flow.md` - 处置任务派发、接收和处理提交 - AFK
5. `05-recheck-and-rework.md` - 复核与退回补充 - AFK
6. `06-close-check-rules.md` - 关闭校验规则 - AFK
7. `07-evidence-chain-timeline.md` - 证据链和事件时间线 - AFK
8. `08-duty-permission-boundary.md` - 职责隔离和数据权限 - AFK
9. `09-p0-acceptance-scripts.md` - P0 验收脚本和演练数据 - AFK

## 推荐执行批次

第一批：1 -> 2 -> 3。
第二批：4 -> 5 -> 6。
第三批：7、8 并行补齐闭环。
第四批：9 做验收脚本和演练数据。
