# 司法监管事件处置闭环 V1 PRD

状态：草案
输入依据：`docs/requirements/supervision-event-closure-v1-requirements.md`、`docs/requirements/iv-physiology-v1-requirements-draft.md`、`docs/prd/iv-prison-physiology-v1-prd.md`
目标项目：yFeiEye
优先级：V1-P0 必须先交付，作为生理急症、行为融合、戒毒康复异常和后续治理报表的公共业务底座。

## Problem Statement

yFeiEye 已经具备设备接入、视频流、算法任务、告警、通知、地图分布、图片/录像证据、权限和审计底座。现有告警页可以展示告警、图片、录像、设备位置和录像回放入口，但告警之后仍缺少司法监管业务必须具备的处置闭环：监管事件、责任链、处置任务、复核、关闭校验、证据归档和督察复盘。

如果继续把业务扩展写进 `Alert`，系统会把“发现异常”“通知成功”“处置完成”“医务复核”和“事件关闭”混在一起，导致无法证明谁处理、谁复核、为什么关闭、是否可以追责。

## Solution

V1 将 yFeiEye 的业务主线升级为“司法监管事件处置闭环”：

`告警线索 -> 监管事件 -> 处置任务 -> 责任动作 -> 复核确认 -> 关闭校验 -> 证据归档 -> 督察复盘`

系统保留 `Alert` 作为算法、设备或人工上报的输入和证据来源；新增 `Supervision Event` 作为监管事件权威载体；新增 `Disposal Task` 和 `Action` 承载派发、接收、处理、复核和关闭；使用规则表决定告警是否转事件、事件等级、SOP、证据模板、关闭校验和权限边界。

## Product Principles

- 告警是线索，事件才是业务闭环。
- 通知成功不等于处置完成。
- 图片/录像是证据素材，不等于完整证据链。
- 误报不能删除，只能带原因关闭。
- 无法确认不能滥用，L3/L4 必须审批。
- 技术管理员不能伪造业务处置记录。
- 生理急症、戒毒异常和行为融合都必须接入同一套事件闭环。

## User Stories

1. As a 指挥中心值班员, I want alerts to become supervision events by rule or confirmation, so that I can supervise handling instead of only browsing alert rows.
2. As a 指挥中心值班员, I want to dispatch events to the responsible unit and person, so that every risk has an accountable owner.
3. As a 监区/大队民警, I want to receive and submit on-site handling records, so that the system records what happened and what was done.
4. As a 医务人员, I want to recheck health and injury related events, so that life-safety incidents are not closed by notification alone.
5. As a 康复/心理人员, I want to recheck drug-rehabilitation and psychology-related abnormalities, so that intervention records are available but not misused as final punishment evidence.
6. As a 值班领导, I want to approve major, life-safety, unable-to-confirm, and exception closures, so that high-risk cases have leadership oversight.
7. As a 督察/审计人员, I want to review the evidence chain and timeline, so that I can reconstruct who saw, handled, rechecked, closed, and exported the event.
8. As a 系统管理员, I want to configure event rules, evidence templates, time limits, and permissions, so that different sites can adapt workflows without changing business records.
9. As a 产品负责人, I want physiology and behavior events to share the same event closure foundation, so that future features do not create parallel closure semantics.

## Core Scope

### P0

1. 告警转监管事件规则。
2. 监管事件模型和状态机。
3. 处置任务派发、接收、处理、复核、关闭。
4. 责任动作和时间线。
5. 证据链基础索引。
6. 关闭校验规则。
7. 职责隔离和数据权限。
8. P0 验收脚本。

### P1

1. 超时升级和催办。
2. 告警合并和重复告警归并。
3. 生理红色急症接入。
4. 戒毒康复异常专项接入。
5. 处置统计和闭环质量统计。

### P2

1. 治理样本沉淀。
2. 模型优化和误报治理。
3. 导出审批和督察复盘。
4. 多场所 SOP 差异化配置。

## Core Rule Tables

### 告警转监管事件规则表

| 字段 | 说明 |
| --- | --- |
| 告警来源 | 实时算法、抓拍算法、设备告警、人工上报、生理监测 |
| 告警类型 | 跌倒、打架、越界、设备离线、生理异常等 |
| 是否生成事件 | 自动生成、人工确认后生成、只归档、不生成 |
| 事件类型 | 生命健康、监管秩序、区域安全、戒毒康复、设备保障 |
| 默认等级 | L1-L4 初始等级 |
| 升级因子 | 重点人员、重点区域、夜间、多人、伤情、重复、超时 |
| 责任链模板 | 指挥中心、监区/大队、医务、康复心理、运维、领导 |
| SOP 模板 | 到场、核实、处置、复核、关闭的必做动作 |
| 证据模板 | 图片、录像、人员、点位、处置说明、复核记录 |
| 合并规则 | 同设备、同区域、同人员、同时间窗内是否合并 |

### 关闭校验规则表

| 等级 | 关闭路径 | 硬拦截 | 软提醒 | 例外路径 |
| --- | --- | --- | --- | --- |
| L1 一般事件 | 普通关闭 | 无处理说明、无结果分类 | 缺截图、重复告警 | 指挥中心说明后关闭 |
| L2 重要事件 | 复核后关闭 | 无责任人、无到场记录、无现场结果 | 证据不完整、处理超时 | 指挥中心确认 |
| L3 重大事件 | 领导确认关闭 | 无复核、无领导意见、无证据链 | 附件不全、时限超标 | 领导审批或转重大 |
| L4 生命安全事件 | 医务/康复复核 + 领导关闭 | 无医务/康复复核、无现场处置、无关闭意见 | 录像缺失但有替代说明 | 无法复核审批或转重大 |

### 职责隔离与数据权限表

| 角色 | 可以做 | 不可以做 |
| --- | --- | --- |
| 指挥中心值班员 | 确认告警、创建事件、派发、催办、关闭 L1/L2 | 替代现场处置、填写医务结论 |
| 监区/大队民警 | 接收、到场、处置、补充证据、申请升级 | 关闭 L3/L4 |
| 医务人员 | 医务复核、伤情/体征记录、无法复核说明 | 修改原始告警和现场记录 |
| 康复/心理人员 | 戒毒康复、心理异常、行为复核 | 单独作为惩戒或最终监管定性 |
| 值班领导 | 重大事件确认、改派、审批关闭、审批无法确认 | 删除证据链 |
| 督察/审计人员 | 查看全链路、审批导出、复盘违规关闭 | 修改处置过程 |
| 系统管理员 | 配置菜单、字典、规则、权限 | 伪造处置、复核、关闭记录 |

## Event Types

| 事件类型 | 是否直接生成事件 | 默认等级 | 关键升级因子 | 默认责任链 |
| --- | --- | --- | --- | --- |
| 倒地/突发疾病 | 是 | L4 | 无响应、伤情、夜间、重点人员 | 监区民警 + 医务 + 领导 |
| 生理红色急症 | 是 | L4 | 持续异常、无法复测、重点人员 | 医务 + 监区民警 + 领导 |
| 打架斗殴 | 是 | L3 | 多人、伤情、重复发生 | 监区民警 + 领导 |
| 越界/重点区域入侵 | 是 | L2 | 重点区域、身份不明、夜间 | 指挥中心 + 现场民警 |
| 聚集/异常接触 | 人工确认 | L2 | 涉重点人员、拒不配合 | 监区民警 |
| 戒断/康复异常 | 是 | L3 | 反复异常、身体风险、心理风险 | 大队民警 + 医务/康复 |
| 摄像头离线 | 视区域 | L1 | 重点区域、长时间未恢复 | 运维 + 指挥中心 |

## Status Model

事件状态：

`alert_received -> event_candidate -> created -> dispatched -> accepted -> handling -> pending_recheck -> pending_close_check -> closed`

异常状态：

`rework_required`、`exception_review`、`transferred_major`

任务动作状态：

`pending`、`sent`、`acknowledged`、`handling`、`submitted`、`approved`、`rejected`、`timeout`、`closed`

## Acceptance

P0 验收必须证明：

1. 一条告警可以生成监管事件。
2. 监管事件可以派发为处置任务。
3. 责任人可以接收并提交处置结果。
4. 需要复核的事件不能绕过复核。
5. 缺少关键材料时不能关闭。
6. 误报关闭保留原始告警和原因。
7. L3/L4 必须由有权限角色确认关闭。
8. 技术管理员不能伪造业务闭环。

## Engineering Contract

P0 开发前必须先冻结工程落地契约：`docs/prd/supervision-event-closure-v1-engineering-contract.md`。

该契约补齐字段字典、状态契约、接口边界、错误码、权限种子、关闭校验规则种子和演练规则种子。PRD 负责定义业务目标和范围，工程契约负责约束建表、接口和首轮实现，避免研发阶段把 `Alert`、`Supervision Event`、`Disposal Task`、`Action`、`Evidence Chain` 的边界重新打散。

## Out of Scope

- 不在 P0 中完成完整生理监测算法或生理 worker。
- 不在 P0 中完成全量报表和治理平台。
- 不在 P0 中替代客户现场既有规章制度。
- 不把心理/康复输出作为惩戒、医学诊断或最终监管定性。
- 不删除现有告警列表；告警列表继续作为线索和证据入口。
