# 任务 02：生理点位台账与 readiness 基线

类型：AFK
状态：ready-for-agent
Blocked by：任务 01
User stories covered：17, 18

## What to build

实现一个可配置、可查询、可审计的高危生理点位台账，并为该点位提供最小 readiness 基线检查。该切片完成后，业务人员可以配置一个红外、近红外、热成像或可见光 rPPG 生理点位，并看到该点位是否具备进入 P0 红色急症闭环的基础条件。

## Acceptance criteria

- [ ] 可以创建、查看、编辑一个高危生理点位。
- [ ] 点位记录包含设备或 SDK 来源、点位名称、监区/位置、标定状态、在线状态、隐私策略、分析时段和 SLA。
- [ ] readiness 至少区分 `ready`、`manual_required`、`blocked_by_customer`、`simulated`、`skipped_not_ready`。
- [ ] 点位缺少标定、设备离线或依赖缺失时，readiness 返回可解释原因。
- [ ] WEB 有基础页面或可操作入口，接口有权限控制，关键操作进入审计。

## Implementation notes

- 先支持一个高危点位，不追求全监区规模化。
- readiness 是业务交付状态，不是简单健康检查。
- 字段命名应保留后续接入多种生理输入来源的余地。

## Suggested tests

- 创建点位成功。
- 缺少标定时 readiness 不是 `ready`。
- 设备或外部依赖缺失时 readiness 能给出原因。
- 无权限用户不能修改点位。
