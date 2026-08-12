# 司法监管事件处置闭环 V1 工程落地契约

状态：研发前置契约
来源：`docs/requirements/supervision-event-closure-v1-requirements.md`、`docs/prd/supervision-event-closure-v1-prd.md`
目标项目：yFeiEye
适用范围：V1-P0

## 1. 契约目标

本文补齐研发开工前最容易卡住的三个落地件：

1. 字段字典和数据模型契约。
2. 接口、模块 owner、幂等和权限边界契约。
3. P0 初始规则种子。

本文不新增业务范围，不替代 PRD，也不要求 P0 一次性完成复杂规则配置后台。P0 可以先通过数据库种子、常量配置或轻量管理入口落地规则，但必须保证事件、任务、证据、关闭校验和审计语义稳定。

## 2. 关键假设

1. `Alert` 继续作为告警线索和证据来源，不承载处置生命周期。
2. `Supervision Event` 是监管事件生命周期的权威对象。
3. `Disposal Task` 是处置责任落地对象，可以有多条任务。
4. `Action` 是任务中的动作或外部触达记录，不等同于事件关闭。
5. `Evidence Chain` 是事件证据索引和审计包，P0 不要求复制原始图片、录像或日志文件。
6. 若没有独立事件服务，P0 建议先在 `DEVICE/iot-system` 承载事件域后端能力，复用用户、部门、角色、权限和审计底座；`WEB` 承载操作页面；`VIDEO` 和 `DEVICE/iot-message` 作为告警、设备和媒体来源。

## 3. 模块职责

| 模块 | P0 职责 | 不负责 |
| --- | --- | --- |
| `VIDEO` | 产生视频算法告警、图片、录像、摄像头、点位和回放入口 | 不负责监管事件状态机、关闭校验和业务处置 |
| `DEVICE/iot-message` | 接收设备侧告警、设备离线、设备恢复、消息回执 | 不负责业务复核和领导关闭 |
| `DEVICE/iot-system` | 建议承载监管事件、处置任务、规则、权限、审计、字典和关闭校验 | 不伪造现场处置、医务复核或领导意见 |
| `WEB` | 告警入口、事件中心、事件详情、我的待办、关闭校验面板、证据链展示 | 不在前端绕过权限和状态机 |
| 文件/对象存储 | 保存图片、录像、附件、导出文件 | 不保存业务关闭语义 |
| 生理/戒毒专项 | P1 之后作为事件输入和专项复核来源 | 不另起一套事件闭环 |

## 4. 核心对象字段字典

### 4.1 `supervision_event`

| 字段 | 类型建议 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint/uuid | 是 | 事件主键 |
| `event_no` | varchar | 是 | 事件编号，建议包含日期和机构前缀 |
| `tenant_id` | bigint/string | 是 | 租户或项目隔离 |
| `org_id` | bigint/string | 是 | 所属单位、监区、大队或部门 |
| `site_type` | enum | 是 | `prison`、`detention_center`、`drug_rehab`、`other` |
| `source_system` | enum | 是 | `video`、`device_message`、`manual`、`physiology`、`rehab` |
| `source_alert_id` | string | 否 | 来源告警 ID，人工事件可为空 |
| `source_alert_type` | string | 否 | 来源告警类型 |
| `source_alert_time` | datetime | 否 | 来源告警发生时间 |
| `source_payload_hash` | varchar | 否 | 来源载荷摘要，用于幂等和追溯 |
| `device_id` | string | 否 | 设备 ID |
| `camera_id` | string | 否 | 摄像头 ID |
| `location_id` | string | 否 | 点位或区域 ID |
| `person_id` | string | 否 | 可信人员 ID，不可信时为空 |
| `person_confidence` | decimal | 否 | 身份置信度或可信等级 |
| `event_type` | enum | 是 | 事件类型，见初始规则种子 |
| `event_level` | enum | 是 | `L1`、`L2`、`L3`、`L4` |
| `event_status` | enum | 是 | 事件状态，见状态契约 |
| `current_owner_dept_id` | string | 否 | 当前责任部门 |
| `current_owner_user_id` | string | 否 | 当前责任人 |
| `close_result` | enum | 否 | 关闭结果，关闭后必填 |
| `close_reason` | text | 否 | 关闭说明或误报原因 |
| `close_check_status` | enum | 是 | `not_checked`、`passed`、`failed`、`exception_required` |
| `evidence_status` | enum | 是 | `complete`、`missing_soft`、`missing_hard` |
| `sensitivity_level` | enum | 是 | `normal`、`medical`、`psychology`、`rehab` |
| `upgraded_from_level` | enum | 否 | 升级前等级 |
| `upgrade_reason` | text | 否 | 升级原因 |
| `merged_into_event_id` | id | 否 | 重复告警归并到的事件 |
| `created_by` | string | 是 | 创建人或系统账号 |
| `created_at` | datetime | 是 | 创建时间 |
| `dispatched_at` | datetime | 否 | 首次派发时间 |
| `accepted_at` | datetime | 否 | 首次接收时间 |
| `handled_at` | datetime | 否 | 首次处置提交时间 |
| `rechecked_at` | datetime | 否 | 最近复核时间 |
| `closed_at` | datetime | 否 | 关闭时间 |
| `version` | int | 是 | 乐观锁版本 |

约束：

1. 同一 `source_system + source_alert_id` 默认只能关联一个未关闭事件。
2. `event_level` 可以按规则升级，普通处理人不能直接降级。
3. `event_status = closed` 后不允许修改处置过程，只允许审计查看、导出审批和复盘标注。
4. `close_result = false_alarm` 时必须保留来源告警、证据和误报原因。

### 4.2 `disposal_task`

| 字段 | 类型建议 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint/uuid | 是 | 任务主键 |
| `event_id` | id | 是 | 所属监管事件 |
| `task_no` | varchar | 是 | 任务编号 |
| `task_type` | enum | 是 | `dispatch`、`onsite_handle`、`medical_recheck`、`rehab_recheck`、`leader_review`、`evidence_supplement` |
| `task_status` | enum | 是 | 任务状态，见状态契约 |
| `assigned_dept_id` | string | 是 | 责任部门 |
| `assigned_role` | string | 是 | 责任角色 |
| `assigned_user_id` | string | 否 | 责任人 |
| `due_at` | datetime | 否 | 截止时间 |
| `accepted_at` | datetime | 否 | 接收时间 |
| `arrived_at` | datetime | 否 | 到场时间 |
| `submitted_at` | datetime | 否 | 提交时间 |
| `result_category` | enum | 否 | `confirmed`、`false_alarm`、`unable_to_confirm`、`transferred_major`、`handled` |
| `handling_note` | text | 否 | 处置说明 |
| `rework_count` | int | 是 | 返工次数 |
| `created_by` | string | 是 | 创建人 |
| `created_at` | datetime | 是 | 创建时间 |

约束：

1. 高等级事件可以有多条任务，例如现场处置、医务复核、领导关闭。
2. 任务提交不等于事件关闭。
3. 被驳回的任务必须保留原提交记录和驳回原因。

### 4.3 `event_action`

| 字段 | 类型建议 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint/uuid | 是 | 动作主键 |
| `event_id` | id | 是 | 所属事件 |
| `task_id` | id | 否 | 所属任务 |
| `action_type` | enum | 是 | `notify`、`call`、`broadcast`、`arrive`、`handle`、`recheck`、`close_check`、`close` |
| `channel` | enum | 否 | `web`、`sms`、`voice`、`device`、`manual` |
| `action_status` | enum | 是 | `pending`、`sent`、`acknowledged`、`submitted`、`failed`、`timeout` |
| `receiver_user_id` | string | 否 | 接收人 |
| `result_payload` | json | 否 | 回执或结果 |
| `failure_reason` | text | 否 | 失败原因 |
| `created_at` | datetime | 是 | 创建时间 |

约束：通知成功只能证明动作触达，不能替代处置、复核或关闭。

### 4.4 `event_evidence_item`

| 字段 | 类型建议 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint/uuid | 是 | 证据项主键 |
| `event_id` | id | 是 | 所属事件 |
| `source_type` | enum | 是 | `alert`、`image`、`video`、`device`、`location`、`person`、`task`、`recheck`、`close`、`audit`、`attachment` |
| `material_type` | enum | 是 | `snapshot`、`recording`、`form`、`log`、`note`、`approval` |
| `material_uri` | varchar | 否 | 文件、对象存储或业务记录 URI |
| `related_record_id` | string | 否 | 关联业务记录 ID |
| `is_required` | boolean | 是 | 是否关闭必需 |
| `required_for_level` | enum | 否 | 适用等级 |
| `collect_status` | enum | 是 | `collected`、`missing`、`not_applicable` |
| `missing_reason` | text | 否 | 缺失原因 |
| `sensitivity_level` | enum | 是 | `normal`、`medical`、`psychology`、`rehab` |
| `created_by` | string | 是 | 创建人 |
| `created_at` | datetime | 是 | 创建时间 |

约束：

1. 证据链是索引，不要求复制原文件。
2. 缺失关键证据会影响关闭校验。
3. 敏感证据查看和导出必须单独鉴权并写审计。

### 4.5 `event_close_check_result`

| 字段 | 类型建议 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | bigint/uuid | 是 | 校验记录主键 |
| `event_id` | id | 是 | 所属事件 |
| `rule_version` | varchar | 是 | 关闭规则版本 |
| `check_result` | enum | 是 | `passed`、`failed`、`exception_required` |
| `hard_block_items` | json | 是 | 硬拦截项 |
| `soft_warning_items` | json | 是 | 软提醒项 |
| `exception_reason` | text | 否 | 例外审批原因 |
| `checked_by` | string | 是 | 校验触发人或系统账号 |
| `checked_at` | datetime | 是 | 校验时间 |

## 5. 状态契约

### 5.1 事件状态

| 状态 | 含义 | 允许进入条件 | 允许下一步 |
| --- | --- | --- | --- |
| `event_candidate` | 候选事件 | 告警命中人工确认规则 | `created`、`closed` |
| `created` | 事件已创建 | 自动或人工创建事件 | `dispatched` |
| `dispatched` | 已派发 | 有至少一条处置任务 | `accepted`、`rework_required` |
| `accepted` | 已接收 | 责任人接收任务 | `handling` |
| `handling` | 处置中 | 责任人开始处置 | `pending_recheck`、`pending_close_check` |
| `pending_recheck` | 等待复核 | 等级或 SOP 要求复核 | `rework_required`、`pending_close_check` |
| `rework_required` | 退回补充 | 复核或关闭校验不通过 | `handling`、`pending_recheck` |
| `pending_close_check` | 等待关闭校验 | 处置和复核提交完成 | `exception_review`、`transferred_major`、`closed`、`rework_required` |
| `exception_review` | 例外审批 | 无法确认或材料缺失但允许审批 | `closed`、`transferred_major`、`rework_required` |
| `transferred_major` | 转重大事件 | 风险升级或现场确认重大 | 只允许复盘或后续重大事件流程接管 |
| `closed` | 已关闭归档 | 关闭校验通过或例外审批通过 | 只允许审计、导出和复盘 |

### 5.2 任务状态

`pending -> sent -> acknowledged -> handling -> submitted -> approved -> closed`

异常状态：

1. `rejected`：复核驳回。
2. `timeout`：任务超时。
3. `cancelled`：任务被改派或合并。

## 6. 接口契约

接口路径为建议命名，最终可按现有后端路由风格调整，但输入、输出、幂等和权限语义应保持一致。

### 6.1 告警生成或关联监管事件

`POST /supervision/events/from-alert`

调用方：`VIDEO`、`DEVICE/iot-message`、`WEB`

权限：系统来源可用服务凭证；人工确认需要 `events:create`

幂等键：`source_system + source_alert_id`

请求字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `source_system` | 是 | `video`、`device_message`、`manual`、`physiology`、`rehab` |
| `source_alert_id` | 是 | 来源告警 ID |
| `source_alert_type` | 是 | 来源告警类型 |
| `source_alert_time` | 是 | 告警发生时间 |
| `device_id` | 否 | 设备 ID |
| `camera_id` | 否 | 摄像头 ID |
| `location_id` | 否 | 点位或区域 |
| `person_id` | 否 | 可信人员 ID |
| `media_refs` | 否 | 图片、录像、回放入口 |
| `confidence` | 否 | 算法置信度 |
| `payload` | 否 | 原始业务载荷 |

响应字段：

| 字段 | 说明 |
| --- | --- |
| `event_id` | 已创建或已存在事件 ID |
| `event_no` | 事件编号 |
| `decision` | `created`、`reused`、`candidate`、`archived_only`、`ignored` |
| `event_status` | 当前事件状态 |
| `rule_code` | 命中的规则编码 |

### 6.2 手工创建监管事件

`POST /supervision/events`

权限：`events:create`

适用：人工上报、演练、无法从告警自动生成的事件。

必填：`event_type`、`event_level`、`location_id`、`description`、`current_owner_dept_id`。

### 6.3 派发处置任务

`POST /supervision/events/{eventId}/tasks`

权限：`tasks:dispatch`

必填：`task_type`、`assigned_dept_id`、`assigned_role`、`due_at`。

规则：

1. `closed` 事件不能派发新任务。
2. L3/L4 必须至少包含现场处置和领导确认任务。
3. L4 生命安全事件必须包含医务或康复复核任务，除非进入例外审批。

### 6.4 接收和提交处置任务

`POST /supervision/tasks/{taskId}/accept`

权限：任务被分派人或同岗位授权人员。

`POST /supervision/tasks/{taskId}/submit`

权限：`tasks:handle`

必填：`result_category`、`handling_note`；到场类任务还应填写 `arrived_at`。

规则：

1. 提交后写入事件时间线。
2. 提交后按事件等级进入复核或关闭校验。
3. 附件、图片和录像引用进入证据链。

### 6.5 复核和退回补充

`POST /supervision/events/{eventId}/recheck`

权限：`events:recheck:medical`、`events:recheck:rehab`、`events:recheck:leader` 中至少一个。

必填：`recheck_type`、`decision`、`recheck_note`。

`decision`：

1. `approved`：进入关闭校验。
2. `rejected`：进入 `rework_required`。
3. `transfer_major`：进入 `transferred_major`。

### 6.6 关闭校验和关闭

`POST /supervision/events/{eventId}/close-check`

权限：`events:close:check`

响应：`check_result`、`hard_block_items`、`soft_warning_items`、`exception_required`。

`POST /supervision/events/{eventId}/close`

权限：L1/L2 需要 `events:close:normal`；L3/L4 需要 `events:close:major`。

规则：

1. 硬拦截存在时不能普通关闭。
2. 例外审批必须写入 `exception_reason` 和审批人。
3. 关闭后事件状态进入 `closed`，同时写入时间线、证据链和审计。

### 6.7 查询接口

| 接口 | 权限 | 用途 |
| --- | --- | --- |
| `GET /supervision/events` | `events:view` | 事件中心列表 |
| `GET /supervision/events/{eventId}` | `events:view` | 事件详情 |
| `GET /supervision/events/{eventId}/timeline` | `events:view` | 时间线 |
| `GET /supervision/events/{eventId}/evidence` | `events:evidence:view` | 证据链 |
| `GET /supervision/tasks/my` | `tasks:view:own` | 我的待办 |
| `GET /supervision/rules/seeds` | `rules:view` | 初始规则查看或调试 |

## 7. 错误码契约

| 错误码 | 含义 | 前端处理 |
| --- | --- | --- |
| `EVENT_ALREADY_EXISTS` | 告警已关联未关闭事件 | 展示“查看监管事件” |
| `EVENT_STATUS_INVALID` | 当前状态不允许操作 | 展示当前状态和可执行动作 |
| `TASK_ASSIGNEE_INVALID` | 当前用户无权处理任务 | 提示联系指挥中心改派 |
| `CLOSE_CHECK_FAILED` | 关闭校验不通过 | 展示硬拦截和补充入口 |
| `MAJOR_CLOSE_PERMISSION_REQUIRED` | L3/L4 关闭权限不足 | 提示领导确认 |
| `SENSITIVE_EVIDENCE_PERMISSION_REQUIRED` | 敏感证据权限不足 | 隐藏敏感字段并提示无权查看 |
| `EVIDENCE_REQUIRED_MISSING` | 缺少关键证据 | 引导补充证据或例外审批 |
| `EXCEPTION_APPROVAL_REQUIRED` | 需要例外审批 | 进入例外审批表单 |

## 8. P0 初始规则种子

### 8.1 告警转事件规则种子

| 规则编码 | 告警类型 | 生成策略 | 事件类型 | 默认等级 | 默认责任链 |
| --- | --- | --- | --- | --- | --- |
| `RULE_DEVICE_OFFLINE_NORMAL` | 普通设备离线 | 人工确认 | 设备保障 | L1 | 运维 + 指挥中心 |
| `RULE_CAMERA_OFFLINE_KEY_AREA` | 重点区域摄像头离线 | 自动生成 | 设备保障 | L2 | 运维 + 指挥中心 |
| `RULE_FALL_DOWN` | 倒地 | 自动生成 | 生命健康 | L4 | 监区民警 + 医务 + 值班领导 |
| `RULE_SUDDEN_ILLNESS` | 突发疾病 | 自动生成 | 生命健康 | L4 | 监区民警 + 医务 + 值班领导 |
| `RULE_FIGHT` | 打架斗殴 | 自动生成 | 监管秩序 | L3 | 监区民警 + 值班领导 |
| `RULE_RESTRICTED_AREA` | 越界或重点区域入侵 | 自动生成 | 区域安全 | L2 | 指挥中心 + 现场民警 |
| `RULE_ABNORMAL_GATHERING` | 聚集或异常接触 | 人工确认 | 监管秩序 | L2 | 监区民警 |
| `RULE_REHAB_WITHDRAWAL` | 戒断或康复异常 | 自动生成 | 戒毒康复 | L3 | 大队民警 + 医务/康复 |
| `RULE_RED_PHYSIOLOGY` | 生理红色急症 | 自动生成 | 生命健康 | L4 | 医务 + 监区民警 + 值班领导 |

### 8.2 关闭校验规则种子

| 等级 | 必需材料 | 硬拦截 | 可例外项 | 默认关闭权限 |
| --- | --- | --- | --- | --- |
| L1 | 处理说明、结果分类、操作日志 | 无处理说明、无结果分类 | 缺截图但说明充分 | `events:close:normal` |
| L2 | 责任人、到场或核实记录、现场结果、基础证据 | 无责任人、无现场结果 | 录像缺失但有替代说明 | `events:close:normal` |
| L3 | 现场处置、复核意见、领导意见、完整证据链 | 无复核、无领导意见、无关键证据 | 附件不全但领导说明 | `events:close:major` |
| L4 | 现场处置、医务/康复复核、领导关闭意见、生命安全证据链 | 无现场处置、无医务/康复复核、无领导意见 | 无法复核审批 | `events:close:major` |

### 8.3 证据模板种子

| 事件类型 | 必需证据 | 可选证据 | 敏感级别 |
| --- | --- | --- | --- |
| 设备保障 | 设备告警、设备点位、处理说明、恢复记录 | 截图、巡检附件 | `normal` |
| 区域安全 | 原始告警、截图、录像、点位、现场核实 | 人员身份、门禁记录 | `normal` |
| 监管秩序 | 原始告警、事发录像、现场处置、复核意见、领导意见 | 伤情照片、谈话记录 | `normal` |
| 生命健康 | 原始告警、现场处置、医务复核、领导意见、关键时间点 | 体征记录、病情说明 | `medical` |
| 戒毒康复 | 原始告警、现场处置、康复/心理复核、领导意见 | 评估记录、训练记录 | `rehab` 或 `psychology` |

### 8.4 角色权限种子

| 角色 | 关键权限 | 禁止操作 |
| --- | --- | --- |
| 指挥中心值班员 | `events:create`、`tasks:dispatch`、`events:close:normal`、`events:view` | 填写医务结论、关闭 L3/L4 |
| 监区/大队民警 | `tasks:view:own`、`tasks:handle`、`events:evidence:add` | 关闭 L3/L4、修改医务复核 |
| 医务人员 | `events:recheck:medical`、`events:evidence:medical:view` | 修改原始告警、删除现场记录 |
| 康复/心理人员 | `events:recheck:rehab`、`events:evidence:rehab:view` | 单独形成惩戒或最终监管定性 |
| 值班领导 | `events:close:major`、`events:exception:approve`、`tasks:redispatch` | 删除证据链 |
| 督察/审计人员 | `events:audit:view`、`events:evidence:export:approve` | 修改处置过程 |
| 系统管理员 | `rules:manage`、`permissions:manage`、`dictionary:manage` | 伪造处置、复核、关闭记录 |

### 8.5 任务时限种子

| 等级 | 接收时限 | 到场/核实时限 | 处置提交时限 | 关闭建议时限 |
| --- | --- | --- | --- | --- |
| L1 | 30 分钟 | 4 小时内核实 | 24 小时 | 48 小时 |
| L2 | 10 分钟 | 30 分钟 | 4 小时 | 24 小时 |
| L3 | 5 分钟 | 15 分钟 | 2 小时 | 24 小时内领导确认 |
| L4 | 1 分钟 | 5 分钟 | 30 分钟内形成初步处置 | 医务/康复复核后领导确认 |

说明：时限种子用于 P0 演练和默认配置，实际项目可按监狱、看守所、戒毒所制度调整。

## 9. P0 页面入口契约

| 页面 | 必须支持 | 不做 |
| --- | --- | --- |
| 告警列表/详情 | 生成监管事件、查看已关联监管事件 | 不承载完整处置表单 |
| 监管事件中心 | 按等级、类型、状态、责任单位、超时筛选 | 不做复杂大屏报表 |
| 监管事件详情 | 时间线、处置任务、证据链、关闭校验、审计入口 | 不允许前端绕过状态机 |
| 我的待办 | 接收任务、提交处置、补充材料 | 不显示无权任务 |
| 复核页面 | 通过、驳回、转重大、例外说明 | 不替代领导关闭 |
| 关闭校验面板 | 展示硬拦截、软提醒、例外审批入口 | 不允许隐藏失败原因 |

## 10. 研发验收口径

进入编码前必须确认：

1. 本文对象字段能支持任务 02-09。
2. `Alert -> Event` 幂等规则明确。
3. 关闭校验能返回可读失败原因。
4. 权限种子能阻止系统管理员伪造业务闭环。
5. 敏感医务、心理、康复证据有单独查看和导出权限。
6. 初始规则种子能跑通普通告警、生命安全、戒毒异常、驳回补充、误报归档、关闭拦截和权限隔离演练。
