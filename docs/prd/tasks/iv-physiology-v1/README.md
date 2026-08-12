# 司法监管生理监测 V1 开发任务包

来源：`docs/prd/iv-prison-physiology-v1-prd.md`
开发列表：`docs/prd/iv-prison-physiology-v1-development-list.md`
上游底座：`docs/prd/tasks/supervision-event-closure-v1/README.md`
使用方式：每个文件是一张可领取开发任务。AFK 任务可以直接实现；HITL 任务先完成决策产物，再解锁后续开发。

生理监测任务包默认复用监管事件处置闭环 V1 的事件中心、处置任务、关闭校验、证据链、职责隔离和 P0 验收口径。这里的任务只补齐生理专项能力。

## 任务索引

1. `01-event-center-boundary.md` - 确认生理专项接入监管事件闭环的数据边界 - HITL
2. `02-physiology-point-readiness.md` - 生理点位台账与 readiness 基线 - AFK
3. `03-physiology-sample-ingestion.md` - 上游生理输入归一化为 Physiology Sample - AFK
4. `04-trusted-person-id-binding.md` - 可信 person_id 绑定与身份不可信降级 - AFK
5. `05-low-confidence-window.md` - 低可信窗口保护与匿名质量统计 - AFK
6. `06-red-physiology-event.md` - 红色生理急症创建统一 Event - AFK
7. `07-event-center-ui.md` - 事件中心列表、详情和状态时间线 - AFK
8. `08-action-queue-manual-fallback.md` - 医务、干警、领导动作队列与人工替代闭环 - AFK
9. `09-medical-recheck.md` - 医务复测任务和复测回填 - AFK
10. `10-close-check-evidence-chain.md` - 关闭校验与证据链拦截 - AFK
11. `11-timeout-escalation.md` - 医务和干警超时升级 - AFK
12. `12-behavior-physiology-fusion.md` - 行为事件与生理事件融合关系 - AFK
13. `13-p0-reporting.md` - P0 报表和统计口径 - AFK
14. `14-sensitive-evidence-permission-audit.md` - 敏感证据权限、导出审批和审计 - AFK
15. `15-p0-acceptance-scripts.md` - P0 验收脚本和演练数据 - AFK
16. `16-minimal-governance-entry.md` - 最小治理入口 - AFK
17. `17-physiology-model-governance-reservation.md` - 生理模型治理预留 - AFK
18. `18-p2-operations-reservation.md` - 群体趋势、健康画像和心理辅助预留 - HITL

## 推荐执行批次

第零批：完成 `docs/prd/tasks/supervision-event-closure-v1/README.md` 的 P0 闭环任务。
第一批：1 -> 2 -> 3。
第二批：4、5 可并行，完成后进入 6。
第三批：7、8、9、10 形成主闭环。
第四批：11、12、13、14、15 补齐验收。
第五批：16、17、18 做治理与辅助运营预留。
