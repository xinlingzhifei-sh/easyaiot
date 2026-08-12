# 司法监管生理监测 V1 PRD

状态：草案
输入依据：`docs/requirements/supervision-event-closure-v1-requirements.md`、`docs/requirements/iv-physiology-v1-requirements-draft.md`、`docs/requirements/iv-prison-physiology-v1-p0-acceptance-plan.md`、`CONTEXT.md`、`docs/adr/`
目标项目：yFeiEye
优先级：V1-P0 必须先交付，V1-P1/P2 预留和分阶段扩展

## Problem Statement

司法监管场景需要一条可运行、可解释、可审计的生理急症闭环，而不是在现有告警列表里增加心率、呼吸率等字段。当前 yFeiEye 已具备设备接入、视频流、算法任务、告警、通知、图片/录像证据、权限和审计底座；`docs/prd/supervision-event-closure-v1-prd.md` 已将这些能力收敛为公共监管事件处置闭环。生理监测 V1 应作为 L4 生命安全专项接入该公共闭环。

在监狱/看守所单所试点中，红色生理急症属于高风险监管事件。系统必须保证高危生理信号能够从上游输入进入统一事件中心，触达医务、监区干警和值班领导，并通过复测、处置、证据和审计完成闭环。系统还必须避免把身份不可信、低质量窗口、模拟接口、人工替代或客户阻断误记为真实成功。

## Solution

V1 以“司法监管生理监测闭环”为产品边界，优先交付 V1-P0 红色生理急症闭环。系统通过监管事件处置闭环接收行为告警、生理样本和融合线索，将 `Alert` 保持为算法或设备告警输入，把 `Supervision Event` 作为监管事件的权威载体。

生理监测 V1 不重复实现事件中心、处置任务、关闭校验、职责隔离和证据链框架；这些能力由 `docs/prd/supervision-event-closure-v1-prd.md` 定义。生理专项负责补齐生理点位、样本、质量、可信身份、红色规则、医务复测和生理治理口径。

V1-P0 的核心链路为：

`上游生理输入 -> Physiology Sample -> 质量/readiness 检查 -> 可信 person_id -> Physiology Event -> 事件中心 -> 医务动作/干警动作/领导关注 -> 医务复测 -> 关闭校验 -> 证据链归档 -> 报表区分`

系统优先复用上游红外、近红外、热成像设备或厂商 SDK 输出，yFeiEye 负责统一 `Physiology Sample` 契约、质量标签、可信身份规则、事件状态机、动作队列、医务复测、证据链和审计。真实医务适配器优先；客户现场暂不具备接口时，允许人工替代闭环，但统计口径必须与真实接口成功区分。

## User Stories

1. As a 监区值班民警, I want to receive a red physiology emergency with person, point, time, and risk context, so that I can quickly perform on-site confirmation.
2. As a 监区值班民警, I want to record arrival time, handling notes, and现场结果, so that the emergency handling chain has accountable evidence.
3. As a 监区值班长, I want to see whether each required action is accepted, delayed, or completed, so that I can coordinate site response before risk escalates.
4. As a 指挥中心值班员, I want red physiology emergencies to enter a unified event center, so that I can supervise lifecycle status instead of searching through alert rows.
5. As a 指挥中心值班员, I want timeout actions to escalate automatically, so that unhandled medical or site duties are not silently missed.
6. As a 医务人员, I want to receive a medical recheck task for each red physiology emergency, so that I can perform a real recheck rather than only receive a notification.
7. As a 医务人员, I want to enter rechecked heart rate, respiration, temperature, medical judgement, SOP result, and attachments, so that the event can be closed with medical evidence.
8. As a 医务人员, I want to record unable-to-recheck reasons, so that legitimate site constraints can be reviewed and approved.
9. As a 带班领导, I want to follow major red emergencies and approve critical closure exceptions, so that high-risk cases receive leadership oversight.
10. As an 审计/督察人员, I want to review the evidence chain after closure, so that I can reconstruct who saw, handled, rechecked, closed, and exported the event.
11. As an 审计/督察人员, I want export operations to require permission and audit records, so that sensitive physiology and evidence materials are protected.
12. As a 系统管理员, I want physiology evidence, medical judgement, governance, and export permissions separated, so that sensitive capabilities are not exposed through generic alert permissions.
13. As a 监管业务负责人, I want only trusted `person_id` to create personal physiology events, so that algorithmic identity candidates do not create non-compliant personal records.
14. As a 监管业务负责人, I want identity candidates such as face match, bed binding, point binding, and `correlation_id` to remain candidates until confirmed, so that the system distinguishes evidence from trusted identity.
15. As a 算法/设备运维人员, I want low-confidence physiology windows to enter anonymous quality statistics, so that point or device issues can be remediated without polluting personal baselines.
16. As a 算法/设备运维人员, I want ROI loss, occlusion, motion artifact, thermal interference, calibration status, and effective sampling ratio recorded, so that sample quality is explainable.
17. As a 生理点位配置人员, I want to configure high-risk physiology points, device/source type, calibration, privacy policy, SLA, and active analysis window, so that the emergency loop starts from known operating conditions.
18. As a 业务验收人员, I want the system to distinguish `ready`, `degraded`, `manual_required`, `manual_signed`, `blocked_by_customer`, `skipped_not_ready`, and `simulated`, so that delivery reports do not overstate production readiness.
19. As a 业务验收人员, I want manual fallback to complete the business loop without counting as real adapter success, so that customer-site constraints remain visible.
20. As a 业务验收人员, I want simulated adapter success isolated from production success, so that demos and tests do not become false acceptance evidence.
21. As a 事件处理人, I want red physiology emergencies to remain independent when behavior events occur at the same time, so that a fall event cannot swallow or downgrade a life-safety physiology event.
22. As a 事件处理人, I want behavior and physiology events to have fusion links or conflict reasons, so that复核人员 can understand why events were related or separated.
23. As a 医务负责人, I want false-abnormal recheck conclusions and error reasons to flow into governance, so that future point, device, or model issues can be improved.
24. As a 数据治理人员, I want low-confidence reasons, recheck conclusions, hard cases, and sample evidence collected through a minimal governance entry, so that governance data starts accumulating from V1-P0.
25. As a 模型治理人员, I want physiology model or device version captured with each sample, so that later shadow mode, gray release, champion-challenger, and rollback can be based on evidence.
26. As a 产品负责人, I want V1-P1/P2 capabilities reserved without blocking V1-P0, so that the first delivery proves the emergency loop while avoiding later architecture rewrites.
27. As a 合规负责人, I want psychology or health-profile outputs marked as `not_standalone_determination`, so that they cannot be used alone for punishment, diagnosis, or final supervision judgement.
28. As a 报表查看人, I want reports to separate total red physiology events, closed events, pending recheck, timeout escalation, real success, manual completion, simulation, customer blocking, and not-ready skips, so that success rates are honest.
29. As a 报表查看人, I want true abnormal, false abnormal, unable-to-recheck, identity downgrade, and low-quality downgrade separated, so that acceptance and operations can see actual risk handling quality.
30. As a 开发负责人, I want clear module boundaries between alert input, event center, physiology samples, medical recheck, evidence, readiness, and governance, so that implementation stays testable and maintainable.

## Implementation Decisions

- Reuse the supervision event closure foundation as the authoritative lifecycle for behavior, physiology, fusion, and manual supervision events.
- Keep existing algorithm/device alerts as inputs and evidence sources; do not expand alert delivery into event closure semantics.
- Model `Physiology Sample` as a normalized measurement window with source, point, time, metrics, quality labels, confidence, and model or device version.
- Create personal `Physiology Event` only when physiology quality and trusted `person_id` conditions pass.
- Treat face matching, bed binding, point binding, manual observation, track association, and frame correlation as identity candidates until business rules produce trusted supervision identity.
- Route identity-uncertain or low-confidence physiology windows to anonymous quality statistics, point remediation, or manual review; do not write them into personal events or personal baselines.
- Preserve red physiology emergencies as independent high-priority events even when behavior events are triggered in the same frame or time window.
- Represent behavior/physiology relationship through fusion links and conflict reasons, rather than overwriting either original event.
- Create an action queue for medical duty, site police duty, command center supervision, and leadership attention.
- Define action states separately from event states so notification delivery cannot be confused with medical recheck or business completion.
- Prefer real medical adapter integration, but support manual fallback when customer site readiness is incomplete.
- Distinguish `real_success`, `manual_completed`, `manual_signed`, `timeout`, `blocked_by_customer`, `skipped_not_ready`, and `simulated` in both storage and reporting.
- Require medical recheck records or approved unable-to-recheck records before normal closure of red physiology events.
- Add closure checks for required actions, recheck state, evidence chain, responsibility chain, readiness state, and close permission.
- Build evidence chain as a reviewable package that links physiology metrics, quality, trusted identity evidence, rules, visual evidence, actions, recheck, readiness, and audit records.
- Add readiness checks for medical adapter, medical accounts, shifts, callback/receipt, manual fallback, migrations, tables, indexes, and required permissions.
- Use existing user, role, menu, operation-log, notification, video, algorithm-task, MinIO, and audit foundations where suitable, but keep supervision event semantics separate.
- Keep sensitive physiology evidence, medical judgement, psychology/profile data, export operations, and governance write actions behind explicit permissions.
- Deliver V1-P0 as a single-site, one-high-risk-point red emergency loop before scaling to full-site trends, full model governance, psychology assistance, or long-term health profile operations.
- Add a minimal governance entry in V1-P1 so recheck conclusions, error reasons, low-confidence reasons, sample evidence, and model/version review material can flow.
- Reserve V1-P2 structures and permissions for group trends, local health profile, and psychology assistance, with the non-determinative marker present from V1-P0.

## Testing Decisions

- Tests should verify external behavior and acceptance outcomes, not internal table names or implementation details.
- P0 acceptance requires scenario tests for the full red physiology emergency loop from sample ingestion to event closure.
- The normal-loop test must prove that a trusted identity and quality-passing red sample creates a personal physiology event, dispatches actions, accepts medical recheck, passes closure checks, and writes evidence/reporting data.
- The identity-downgrade test must prove that a sample with only identity candidates does not create a personal event or personal baseline.
- The low-quality-window test must prove that ROI loss, occlusion, motion artifact, thermal interference, or missing calibration prevents personal event creation and personal baseline updates.
- The manual-fallback test must prove that business handling can close through manual records while reporting excludes it from `real_success`.
- The medical-timeout test must prove that timeout creates escalation records and blocks direct closure.
- The false-abnormal-recheck test must prove that the event remains auditable, error reasons are recorded, and governance receives the reason without deleting the original event.
- The customer-blocked-readiness test must prove that customer-side missing interface, account, shift, network, or workflow is reported as customer blocking or not-ready, not system success.
- The simultaneous-behavior-and-physiology test must prove that behavior events and red physiology events both remain visible, with fusion relation or conflict explanation.
- Permission tests must prove that medical recheck, evidence viewing, governance writing, closing, and export actions are isolated by role.
- Reporting tests must prove that counts and success rates split real success, manual completion, manual signature, simulation, customer blocking, not-ready skip, timeout, false abnormal, unable-to-recheck, identity downgrade, and low-quality downgrade.
- Migration/readiness tests must prove that existing deployments can upgrade with required schema, indexes, permissions, and readiness probes without relying on a fresh database only.
- Audit tests must prove that viewing sensitive evidence, medical recheck, closure, approval, export, and governance writes leave reviewable operation records.

## Out of Scope

- Full-monitoring-site rollout across all监区 and all physiology points.
- Long-term health profile operations as a completed product.
- Full psychology-assistance review workstation.
- Complete model governance platform with fully operated shadow mode, champion-challenger, gray release, and rollback workflows.
- Full self-developed physiology worker replacing upstream devices or vendor SDKs.
- Using psychology, health profile, or group trend outputs as punishment, medical diagnosis, or final supervision judgement.
- Counting manual fallback, simulation, customer blocking, or not-ready skips as real production adapter success.
- Replacing the existing alert list entirely; alert remains useful as input and evidence, but not as the completed event center.

## Further Notes

- V1-P0 success is measured by whether the red physiology emergency loop can be rehearsed and audited end to end, not by whether all future physiology operations are finished.
- Evidence-chain gaps should not delay emergency dispatch, but they must prevent normal closure until evidence is completed or a compliant gap explanation is recorded.
- The PRD intentionally avoids final table names and API paths. Those should be designed during implementation planning after module boundaries and ownership are confirmed.
- The strongest implementation boundary is semantic: alert input, supervision event, physiology sample, medical recheck, readiness, evidence, governance, and report statistics must remain distinct concepts.
- The first implementation slice should use one high-risk point and one red emergency path to prove the loop before broader rollout.
