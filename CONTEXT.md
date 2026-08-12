# IV Physiology Monitoring Context

This context defines the domain language for yFeiEye physiology monitoring in a judicial supervision setting. It exists to keep product, engineering, testing, and delivery aligned on what counts as a supervision-grade physiology monitoring loop.

## Language

**司法监管生理监测闭环**:
A single-site supervision workflow that turns physiology signals into accountable risk handling, including identity trust, event creation, medical recheck, evidence, action follow-up, and audit closure.
_Avoid_: 通用 AIoT 健康监测, 体征看板, 普通健康告警

**生理事件**:
A supervision event created from trusted physiology signals for a specific person or supervision context. A **生理事件** requires closure through evidence, actions, and medical or compliant recheck handling.
_Avoid_: 指标异常, 普通告警, 设备告警

**生理样本**:
A normalized physiology measurement window with source, time, point, metrics, quality label, confidence, and model or device version. A **生理样本** can come from upstream devices, vendor SDKs, or a yFeiEye worker, but it is not a personal physiology event by itself.
_Avoid_: 原始帧, 设备告警, 生理事件

**上游生理输入**:
Physiology output produced by an infrared, near-infrared, thermal-imaging device, or vendor SDK before yFeiEye normalizes it into a **生理样本**. It is the preferred V1-P0 source path when it helps prove the emergency loop sooner, while a yFeiEye worker may run as fallback, parallel capability, or later replacement.
_Avoid_: 自研 worker 前置阻塞, 原始算法真值

**事件中心**:
The authoritative place where supervision events are tracked through status, responsibility, evidence, actions, recheck, and closure. A **事件中心** may use alerts as inputs, but it is not the same thing as an alert list.
_Avoid_: 告警列表, Alert 表, 通知中心

**告警**:
An input signal or notification-worthy record produced by an algorithm, device, or matching process. A **告警** can become evidence for an event, but it is not proof that the event exists, is handled, or is closed.
_Avoid_: 生理事件, 处置闭环, 医务复测

**可信 person_id**:
The trusted supervision identity that allows a physiology signal to become a personal physiology event. It comes from the supervision person registry or a business-confirmed detained-person identifier, not directly from face matching, bed binding, point binding, or frame correlation.
_Avoid_: FacePerson.id, matched_face_entry_id, camera track id

**身份候选**:
Evidence that may help bind a physiology signal to a person, such as face matching, bed or point binding, manual observation, or `correlation_id`. A **身份候选** is not enough to create a personal physiology event until business rules confirm it as a **可信 person_id**.
_Avoid_: 可信身份, 个人生理事件身份

**低可信窗口**:
A signal window whose identity, ROI, device calibration, environment, or motion quality is insufficient for personal physiology judgment. A **低可信窗口** must not create or update a personal physiology event or personal baseline.
_Avoid_: 误报, 弱告警

**匿名质量统计**:
Non-personal aggregation used to understand point, device, or signal quality when trusted identity or signal confidence is insufficient. **匿名质量统计** is for remediation and operations, not personal risk handling.
_Avoid_: 个人健康档案, 生理事件

**医务复测**:
The medical role's recheck record for a physiology event, including actual measured signs, medical judgment, true/false abnormal conclusion, error reason, and SOP result. Notification delivery is not **医务复测**.
_Avoid_: 通知成功, 已推送, 告警已读

**真实医务适配器**:
A real production integration or workflow that can reach medical-duty users, receive a callback or receipt, and support recheck handling. A **真实医务适配器** is preferred for V1-P0, but lack of it may fall back to compliant manual handling.
_Avoid_: 模拟适配器, 通知配置, 演示接口

**生理证据链**:
The reviewable evidence package for a physiology event, connecting physiology metrics, signal quality, visual evidence, rules, actions, recheck records, and audit records. It exists so a closed event can be reconstructed later.
_Avoid_: 截图, 录像路径, 附件

**readiness 检查**:
A production-readiness judgment for dependencies such as event allowlists, tables, indexes, migration versions, medical accounts, shifts, adapters, callbacks, and manual fallback. **readiness 检查** separates real success, degraded operation, manual handling, simulation, and customer blocking.
_Avoid_: 服务在线, 健康检查, 通知配置完成

**人工替代闭环**:
A compliant fallback path where people complete and sign the required handling when a real external adapter is unavailable. It can prove business handling, but must not be counted as real adapter success.
_Avoid_: 真实接口成功, 模拟成功

**真实接口成功**:
An action outcome where a real production adapter or real medical workflow succeeds and returns usable receipt evidence. **真实接口成功** excludes manual completion, manual signature, simulation, customer blocking, and skipped-not-ready outcomes.
_Avoid_: manual_completed, manual_signed, simulated, blocked_by_customer, skipped_not_ready

**最小治理入口**:
A V1-P1 governance entry point that can receive recheck conclusions, error reasons, low-confidence reasons, samples, and model or version review material for later governance. It proves governance data can flow without requiring the full long-term operations suite.
_Avoid_: 完整治理平台, 纯表结构预留

**辅助不定性标识**:
A compliance marker that makes psychology or health-profile outputs non-determinative for punishment, medical diagnosis, or supervision final judgment.
_Avoid_: 心理定性, 医学诊断, 惩戒依据

## Flagged Ambiguities

**告警 vs 生理事件**:
An alert is an input signal or notification-worthy record; a physiology event is a supervision record that requires state, action, evidence, recheck, and closure. Do not use alert delivery as proof that a physiology event has been handled.

**事件中心 vs Alert**:
The event center owns supervision-event lifecycle language. `Alert` is an implementation-facing alert record and should be treated as input or evidence unless a later confirmed decision changes the boundary.

**生理样本 vs 生理事件**:
A sample is a normalized signal window; an event is a supervision record that requires trusted identity or compliant context, evidence, actions, recheck, and closure.

**身份候选 vs 可信 person_id**:
Face, bed, point, manual records, and frame correlation can provide identity candidates. A physiology event needs a trusted `person_id` from the supervision person registry or a business-confirmed detained-person identifier before personal event creation.

**通知成功 vs 医务复测**:
Notification success means a channel delivered or attempted a message. Medical recheck means a medical role produced a recheck record and conclusion.

**人工替代闭环 vs 真实接口成功**:
Manual handling can close the business loop when the real adapter is unavailable, but it must be reported separately from real adapter success.

**入口预留 vs 完整上线**:
Reserved entry means schema, permission, audit, and navigation paths exist for later operation. It is not the same as a fully operated trend, profile, psychology-assistance, or governance product.

## Example Dialogue

Product: "This red physiology risk has no trusted person yet. Is it a personal physiology event?"

Domain expert: "No. Put it into anonymous quality statistics or manual review until we have a trusted person_id."

Engineer: "The alert notification succeeded. Can we close the event?"

Domain expert: "No. Notification is only an action signal. Closure needs evidence, action status, and medical recheck or a compliant unable-to-recheck record."

Tester: "The medical adapter is simulated in staging. Can we report real success?"

Domain expert: "No. Simulation and manual fallback must be counted separately from real adapter success."
