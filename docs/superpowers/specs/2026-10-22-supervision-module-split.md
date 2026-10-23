# 司法监管（supervision）从 iot-system 拆出：设计文档

日期：2026-10-22
范围：`DEVICE/iot-system` 中的 supervision / alert-review 代码；只做结构拆分，不升级框架、不改行为。

## 1. 背景与现状

`iot-system-biz` 同时承载两类东西：

- 平台通用底座：用户、部门、权限、字典、通知、租户、OAuth2、短信、邮件等（ruoyi-vue-pro 体系）。
- 司法监管业务：监管事件中心（Supervision Event）、处置任务、证据、复核工作台（Alert Review：线索归并、复核片段、案件、语义检索、AI 摘要、规则治理、证据导出、运行时巡检与外发）。

按 2026-10-22 的仓库实况，supervision 相关受版本控制文件 143 个（含 26 个 SQL 迁移与 1 个 JSON Schema）：

| 位置 | 文件数 |
|---|---|
| `iot-system-api/.../enums/supervision` | 4 |
| `controller/admin/supervision`（含 VO） | 20 |
| `dal/dataobject/supervision` | 19 |
| `dal/pgsql/supervision` | 19 |
| `job/supervision` | 9 |
| `service/supervision` | 34 |
| `src/test/.../supervision` | 36 |
| `resources/sql/migrations`、`resources/schemas` | 27 |

其中 `SupervisionAlertReviewServiceImpl` 单类 9605 行，是复核工作台全部业务的实现：60 多个对外方法、400 多个私有方法。

### 1.1 耦合点（逐一核对 import 得出）

supervision 代码对 iot-system 其它部分的依赖只有四处：

| supervision 类 | 依赖的 iot-system-biz 类 | 用途 |
|---|---|---|
| `ConfiguredReviewCameraPermissionResolver` | `PermissionService`、`RoleCodeEnum`（api） | 判断操作人是否有媒体动作权限、是否超管 |
| `NotifyReviewRuntimeOutboxPublisher` | `NotifySendService`、`AdminUserService`、`AdminUserDO` | 运行时告警外发到站内信，校验收件人租户/状态 |
| `MediaPermissionCheckControllerTest`（测试） | `AuthController` | 测试 `/system/auth/media-permission-check` |
| `ConfiguredReviewCameraPermissionResolverTest`、`NotifyReviewRuntimeOutboxPublisherTest`（测试） | 同上服务的替身 | — |

反方向，iot-system 通用代码对 supervision 的依赖只有一处：`AuthController` 的 `media-permission-check` 端点注入 `ConfiguredReviewCameraPermissionResolver`。另外 `application.yaml` 中有 `yfeieye.video.*`、`yfeieye.review.*` 配置。

其余 supervision 代码只依赖 `iot-common-*`（mybatis / tenant / job / data-permission / security / web / base）。这说明 supervision 是一个边界清楚、可以整体搬走的子域。

## 2. 拆分边界与领域语言

`CONTEXT.md` 与 ADR 0001 / 0006 定下的边界在新模块里保持不变，拆分不引入新的概念混用：

- **事件中心 ≠ 告警**（ADR 0001、0006）：`SupervisionEvent*`（事件、任务、证据、关闭校验、工作流）是权威生命周期；`Alert` 与复核工作台的 `ReviewItem` 只是输入与证据来源。新模块内部仍分成两个子域：
  - `event`：事件中心与处置闭环（`SupervisionEventService`、`SupervisionWorkflowApplicationService`、`SupervisionTask*Service`、`SupervisionEventCloseCheckService`、`SupervisionEvidenceQueryService`）。
  - `alertreview`：告警复核工作台（线索→复核片段→案件→转事件）。复核只能经 `SupervisionEventService.createFromAlert` 进入事件中心（`convertToEvent`），不能直接写事件表；这条既有规则在拆分后的 `AlertReviewEventBridge` 中保持唯一入口。
- **通知成功 ≠ 医务复测**：运行时外发（outbox → 站内信）只是动作信号。拆分后外发适配器留在 iot-system（它依赖平台通知服务），复核模块只通过 `ReviewRuntimeOutboxPublisher` 端口拿到投递结果，不把投递结果当作处置或复测完成。
- **身份候选 ≠ 可信 person_id**、**低可信窗口**、**人工替代闭环 ≠ 真实接口成功**：本次不改这些规则所在的代码路径，只搬移。特征测试锁住现有行为。

## 3. 拆分形态的取舍

### 方案 A：独立 Maven 模块，仍跑在 iot-system 进程里（推荐，本次实施）

```
DEVICE/iot-supervision/
├── iot-supervision-api   # supervision 枚举（原 iot-system-api/enums/supervision）
└── iot-supervision-biz   # controller / dal / job / service / SQL 迁移 / JSON Schema / 测试
```

`iot-system-biz` 依赖 `iot-supervision-biz`，Spring Boot 可执行 jar 仍是 `iot-system-biz.jar`，部署形态、端口、Nacos 服务名、镜像、compose 全部不变。

- 内存：不增加 JVM，`deploy-profiles.json` 不变。
- 对外兼容：HTTP 路径（`/admin-api/system/supervision/**`、`/admin-api/system/auth/media-permission-check`）、权限标识（`system:supervision-*`）、数据库表与迁移版本号、配置键（`yfeieye.video.*`、`yfeieye.review.*`）全部不变，WEB / VIDEO 不用改。
- 依赖方向：`iot-system-biz → iot-supervision-biz → iot-supervision-api + iot-system-api + iot-common-*`。supervision 不再依赖 iot-system-biz，没有环。

### 方案 B：独立部署的 `iot-supervision` 微服务

需要：新 Spring Boot 启动类与端口、Nacos 注册、Gateway 路由（`/admin-api/system/supervision/**` 和 `media-permission-check` 改路由）、权限/用户/通知改走 Feign（`PermissionApi`、`AdminUserApi`、`NotifyMessageSendApi`）、Dockerfile、compose、`deploy-profiles.json` 的 `deviceServices`。

- 内存：每个 DEVICE 微服务 JVM 实测约 900 MB（`-Xms512m -Xmx1024m`）。`mini` 规格推荐 4 GB 内存，已跑 4 个 JVM，再加一个风险很高；`standard` / `full` 可以承受。
- 收益：监管业务可独立发版、独立扩容、独立的故障域；iot-system 回归纯平台底座。
- 代价：跨进程权限校验增加一次 RPC；`media-permission-check` 在 VIDEO 的媒体鉴权热路径上，多一跳会放大播放延迟；分布式事务边界需要重新确认（复核转事件目前在同一个本地事务里）。

### 结论

先做方案 A。方案 A 把方案 B 需要的代码边界一次做完：模块依赖无环、对平台服务的依赖都收敛成端口接口（见 §4.2）。以后要独立部署时，只需新增启动类与基于 Feign 的端口实现，再在部署规格里打开（建议做成 `deploy-profiles.json` 中 `standard` / `full` 的可选微服务），业务代码不用再动。**是否、何时独立部署留给用户决定**（见 §8）。

## 4. 模块设计

### 4.1 包名：本阶段保持不变

搬移后的类保持原包名（`com.basiclab.iot.system.{controller.admin,dal.dataobject,dal.pgsql,job,service}.supervision`、`com.basiclab.iot.system.enums.supervision`），理由：

1. MyBatis Mapper 扫描用的是 `@MapperScan("${iot.info.base-package}")`，即 `com.basiclab.iot.system`，`type-aliases-package` 也在这个前缀下；保持包名就不用改扫描配置，也不会漏扫。
2. Java 服务合并 / Boot 3 会话正在对 iot-system 做 javax→jakarta 改写。纯 `git mv`（内容不变）能被 git 的重命名检测跟上，对方的逐行改动可以自动合到新路径；同时改包名则每个文件的 package 行和 import 块都会冲突。
3. 对外行为与 Spring Bean 名称不变（Bean 名取自类名）。

改名到 `com.basiclab.iot.supervision.*` 作为 Boot 3 合入之后的独立后续项（需要同时加 `@MapperScan`）。

### 4.2 端口与适配器

supervision 对平台服务的两处依赖改成 supervision 自己定义的端口，由 iot-system-biz 提供实现：

| 端口（iot-supervision-biz） | 实现（iot-system-biz） | 行为 |
|---|---|---|
| `ReviewOperatorPermissionChecker`：`hasAnyRoles(userId, roles...)`、`hasAnyPermissions(userId, permissions...)` | `PermissionServiceReviewOperatorPermissionChecker`，原样委托 `PermissionService` | `ConfiguredReviewCameraPermissionResolver` 原先在 `permissionService == null` 时拒绝，现在在端口缺失时同样拒绝 |
| `ReviewRuntimeOutboxPublisher`（已有接口） | `NotifyReviewRuntimeOutboxPublisher`（整类留在 iot-system-biz，移到 `com.basiclab.iot.system.framework.supervision` 包） | 不变 |

`RoleCodeEnum.SUPER_ADMIN` 在 `iot-system-api`，supervision 依赖 api 模块即可。`AuthController` 不改，仍注入 `ConfiguredReviewCameraPermissionResolver`（现在来自依赖 jar）。

### 4.3 资源归属

- `sql/migrations/*.sql`（26 个，全部是 supervision / alert-review 表）与 `schemas/alert-review-review-data-v1.schema.json` 搬到 `iot-supervision-biz/src/main/resources`。迁移文件内容不变，所以 `apply-alert-review-migrations.mjs` 按版本号 + 内容校验和比对历史表，已部署库不受影响；`script_path` 只是记录字段。
- 同步更新引用这些路径的 `.scripts/alert-review-postgres-migration-smoke.mjs`、`apply-alert-review-migrations.test.mjs`、`verify-alert-review-release-package.mjs` 及其测试。
- `application.yaml` 中的 `yfeieye.*` 配置留在 iot-system-biz（它是宿主应用的配置）；读取它的配置契约测试改为按相对路径读 `../../iot-system/iot-system-biz/...`。

### 4.4 测试归属

- 33 个 supervision 测试随代码搬到 `iot-supervision-biz/src/test`（依赖 `iot-common-test`）。
- `MediaPermissionCheckControllerTest`（测 `AuthController`）、`NotifyReviewRuntimeOutboxPublisherTest` 留在 iot-system-biz。
- `ConfiguredReviewCameraPermissionResolverTest` 随解析器搬走，把权限替身从 `PermissionService` 换成端口。

## 5. 拆分 `SupervisionAlertReviewServiceImpl`

### 5.1 约束

- 对外契约不变：`SupervisionAlertReviewService` 接口、`SupervisionAlertReviewServiceImpl` 类名、5 个公开构造器、3 个 `@Autowired(required = false)` setter、各方法上的 `@Transactional` 注解都保留（已有测试会反射检查 `ingestClue`、`updateReviewLifecycle` 的事务注解）。
- 事务语义不变：仍然只有门面类是 Spring Bean，事务代理只包门面；拆出的组件是门面在构造器里创建的普通对象。原来类内的 `this.xxx()` 调用本来就不经过代理，组件间直接调用与之等价。
- 代码是搬移，不是重写：方法体逐字保留，只做三类机械改写：
  1. 依赖字段 `xxx` → `deps.xxx`（集中在 `AlertReviewDependencies`）；
  2. 跨组件调用 `m(...)` → `owner.m(...)`；
  3. 静态工具方法与常量挪到工具类，组件用 `import static` 引用，调用处不改。

### 5.2 目标结构（`service/supervision/alertreview` 子包）

| 类 | 职责 | 对应原方法（举例） |
|---|---|---|
| `SupervisionAlertReviewServiceImpl`（原位置，门面） | 构造、依赖注入、事务边界、逐个委托 | 全部 `@Override` 方法 |
| `AlertReviewDependencies` | 12 个外部依赖 + 3 个可替换依赖 + 录像漂移保留时长 | 原字段 |
| `AlertClueIngestion` | 线索归并、复核片段创建/合并/切分、并发锁、证据收集 | `ingestClue`、`buildReviewData`、`mergeReviewSegment` |
| `ReviewWorkbench` | 工作台查询、时间线、详情流、生命周期与人工复核状态 | `listWorkbench`、`markReviewed`、`updateReviewLifecycle` |
| `ReviewRecordCoverage` | 录像覆盖、缺口原因目录、录像存储同步与漂移 | `getRecordCoverage`、`syncRecordStorage` |
| `ReviewCaseManagement` | 案件创建/合并/拆分/关闭、案件时间线、关联候选 | `createReviewCase`、`suggestReviewCaseCandidates` |
| `ReviewRuleGovernance` | 规则保存、回放、几何评估、规则建议生命周期 | `replayRule`、`updateRuleSuggestionStatus` |
| `ReviewSemanticIntelligence` | 语义检索、索引队列、语义触发、AI 摘要与脱敏 | `semanticSearch`、`summarizeReviewCase` |
| `ReviewEvidenceExport` | 证据导出任务、工作队列、视频导出、清单签名与校验、下载打包 | `processEvidenceExportQueue`、`verifyEvidencePackage` |
| `ReviewMediaAccess` | 媒体访问范围校验与审计、回放签名 | `auditMediaAccess`、`prepareReviewPlayback` |
| `ReviewEvidenceAudit` | 审计链查询与哈希链 | `getEvidenceAuditTrail`、`queryEvidenceAuditTrail` |
| `ReviewRuntimeOperations` | 运行时健康、巡检、外发、对账、集成冒烟 | `runRuntimePatrol`、`publishRuntimeOutbox` |
| `ReviewOperationsReporting` | 运营报表、投递计划、回执 | `generateReviewReport` |
| `AlertReviewEventBridge` | 复核→事件中心的唯一入口与事件投影 | `convertToEvent`、`withEventProjection` |
| `AlertReviewValues` / `AlertReviewConstants` | 纯静态类型转换、哈希、常量 | `toText`、`sha256Hex` |

最终行数以实施结果为准，目标是没有超过 1500 行的类。组件依赖必须无环（实施时用脚本检查），只能经构造器注入。

### 5.2.1 实施结果（与上表的差异）

按调用图检查后做了以下调整，最终结构见 §10：

- `listWorkbench` 被案件、规则、语义、报表、运维五个组件调用，放在 `ReviewWorkbench` 会与规则治理成环，因此和事件投影、查询匹配一起下沉到低层的 `ReviewItemQuery`。
- 媒体访问拆成两层：`ReviewMediaScopeGuard`（按摄像头授权范围校验并审计，被工作台、录像覆盖、案件、导出共用）和 `ReviewMediaAccess`（对外的审计入口与回放签发）。
- 证据导出 1694 行，再拆出 `ReviewEvidenceManifests`（清单生成/签名/校验）与 `ReviewVideoExportPlanner`（视频导出分段规划与溯源校验）。
- AI 摘要从语义检索中分出为 `ReviewAiSummaryService`。
- 原实现里有两处真实的相互递归，不是拆分引入的：案件时间线 → 录像覆盖 → 媒体范围校验 → 媒体访问审计 → 案件时间线；录像覆盖 → 案件时间线 → 录像覆盖。为保持行为不变，不改算法，由装配器 `AlertReviewComponents` 在全部组件创建后绑定这两条回边（`ReviewMediaScopeGuard → ReviewMediaAccess`、`ReviewRecordCoverage → ReviewCaseManagement`），其余依赖都经构造器注入。
- 常量不单独成类：只被一个组件用的常量随组件走，多个组件共用的进 `AlertReviewValues`。

### 5.3 为什么用"门面 + 组件"而不是拆成多个 Spring Bean

拆成多个 `@Service` 会改变：事务代理的位置（组件间调用会开始经过代理）、Bean 数量与注入关系、测试的构造方式（12 260 行的现有测试直接 `new SupervisionAlertReviewServiceImpl(...)`）。这些都是行为风险。门面 + 普通组件在行为上与原单类完全等价，后续若要把某个组件升格为 Bean（例如证据导出单独部署 worker），可以在新边界上逐个做。

## 6. 特征测试

现有 `SupervisionAlertReviewServiceTest`（12 260 行）覆盖了绝大多数对外方法。按"测试中是否调用"统计，以下对外方法没有任何服务级测试，拆分前补上：

- `findReviewCaseByItem`
- `listRules`
- `reconcileEventProjections`
- `scheduleReviewReportDelivery`
- `recordEvidenceDownload`（只有控制器层的替身测试）

特征测试断言的是**现有**行为（包括看起来可疑的行为），不做修正；发现的可疑点记到报告里。

## 7. 与其它并行会话的冲突面

- **Java 服务合并 / Boot 3（javax→jakarta）**：冲突集中在
  - 搬移的 136 个 Java 文件：本次是纯重命名，git 合并时能跟随；如果对方的改写在这些文件的旧路径上，合并后需要确认改动落到了新路径。
  - `SupervisionAlertReviewServiceImpl`：本次把它拆成十几个文件，对方若改了它的 import（如 `javax.*`），会在门面类上冲突；处理方式是保留本次结构，对新文件重新执行同样的机械替换（`grep -l 'javax\.' -r iot-supervision` 后批量替换）。
  - `pom.xml`：`DEVICE/pom.xml` 新增 `<module>iot-supervision</module>`；`iot-system-biz/pom.xml` 新增对 `iot-supervision-biz` 的依赖。对方若合并 iot-system 与其它服务，需要把这条依赖带到合并后的模块。
  - 本次不修改 `iot-parent` BOM，不改任何框架版本。
- **精简部署脚本**：本次不改部署脚本；`DEVICE/install_linux.sh` 的选择性构建按目录自动发现 Maven 模块，新模块无需登记。
- **仓库瘦身 / 改写历史**：只要对方保留 git 的重命名关系即可。

## 8. 需要用户决定的事项

1. 是否把 supervision 独立部署成微服务（方案 B），以及放进哪些部署规格（建议仅 `standard` / `full` 可选，`mini` 不放）。
2. 包名是否在 Boot 3 合入后改为 `com.basiclab.iot.supervision.*`。
3. `NotifyReviewRuntimeOutboxPublisher` 作为平台适配器留在 iot-system，还是在独立部署时改成走 `NotifyMessageSendApi` 的 Feign 版本。

## 9. 不做的事

- 不改任何 HTTP 路径、权限标识、表结构、迁移内容、配置键。
- 不升级 Spring Boot / 不做 javax→jakarta。
- 不改 WEB、VIDEO。
- 不修特征测试中发现的可疑行为。

## 10. 实施结果（2026-10-22）

### 10.1 模块图

```
DEVICE/
├── iot-system/
│   ├── iot-system-api            （平台 API；不再含 supervision 枚举）
│   └── iot-system-biz            Spring Boot 可执行 jar（iot-system-biz.jar，端口 48099，部署不变）
│       ├── 依赖 iot-supervision-biz
│       ├── controller/admin/auth/AuthController            media-permission-check 仍在此，注入 ConfiguredReviewCameraPermissionResolver
│       └── framework/supervision/                          宿主适配器
│           ├── PermissionServiceReviewOperatorPermissionChecker   实现 ReviewOperatorPermissionChecker
│           └── NotifyReviewRuntimeOutboxPublisher                 实现 ReviewRuntimeOutboxPublisher
└── iot-supervision/
    ├── iot-supervision-api       supervision 枚举（事件级别/状态、任务状态、关闭结果）
    └── iot-supervision-biz       普通 jar；依赖 iot-supervision-api、iot-system-api、iot-common-*
        ├── controller/admin/supervision、dal/{dataobject,pgsql}/supervision、job/supervision
        ├── service/supervision/                    事件中心、任务、证据、复核门面与端口
        │   └── alertreview/                        复核工作台组件（见 10.2）
        └── resources/sql/migrations、schemas       26 个迁移 + 复核数据 JSON Schema
```

依赖方向：`iot-system-biz → iot-supervision-biz → {iot-supervision-api, iot-system-api, iot-common-*}`，无环。

### 10.2 `SupervisionAlertReviewServiceImpl` 拆分后的行数

| 类 | 行数 |
|---|---|
| `SupervisionAlertReviewServiceImpl`（门面，拆前 9605） | 496 |
| `ReviewRuleGovernance` | 1132 |
| `ReviewSemanticIntelligence` | 1083 |
| `ReviewRuntimeOperations` | 1074 |
| `AlertClueIngestion` | 880 |
| `ReviewEvidenceExport` | 857 |
| `ReviewWorkbench` | 700 |
| `ReviewAiSummaryService` | 694 |
| `ReviewVideoExportPlanner` | 542 |
| `ReviewCaseManagement` | 521 |
| `ReviewEvidenceManifests` | 457 |
| `ReviewRecordCoverage` | 450 |
| `ReviewEvidenceAudit` | 415 |
| `AlertReviewValues`（共用静态工具） | 367 |
| `ReviewOperationsReporting` | 343 |
| `ReviewMediaScopeGuard` | 226 |
| `ReviewItemQuery` | 175 |
| `AlertReviewEventBridge` | 162 |
| `ReviewMediaAccess` | 148 |
| `AlertReviewComponents`（装配） | 111 |
| `AlertReviewDependencies` | 78 |

合计约 10 900 行，比拆前多出的约 1300 行是各文件的 import、类头与门面的委托方法。

### 10.3 验证

- 拆分前基线（容器 `TZ=Asia/Shanghai`）：iot-system-biz 463 个测试全过（含 5 个新增特征测试）。
- 模块拆出后：iot-supervision-biz 412 + iot-system-biz 51 = 463，全过。
- 大类拆分后：同上 463 全过；另用脚本逐个比对 484 个搬移成员，与原实现除三类机械改写外完全一致。
- `.scripts` 中受影响的 3 个 node 测试全过。

### 10.4 已知问题（本次未改）

- `SupervisionAlertReviewRuntimeSchedulingTest.operationsReportJobUsesPreviousCompleteShanghaiShiftAndDayWindows` 依赖 JVM 默认时区为 `Asia/Shanghai`，在 UTC 环境（含 GitHub Actions 默认 runner）下必然失败；拆分前即如此。建议另行修测试或在 `pr-tests.yml` 的 DEVICE job 设 `TZ`。
