# Alert Review FR-01~FR-38 Release Hardening Design

## Goal

把现有 alert-review 工作台从“代码和本地 mock 大体可用”收口到可发布的 P0/P1/P2 质量门：ReviewSegment 与 ReviewData 在数据库和服务层保持一致，真实 VIDEO/ffmpeg/对象存储链路可复现，媒体权限与审计可反查，运行巡检与报表可调度，发布包只包含正式迁移和 FR 核心文件；任何缺少真实环境证据的门必须明确为上线阻塞，不能用 mock 结果替代。

## Boundary and assumptions

- 以当前 `codex/frigate-review-workbench` 分支已有实现为基线，只补 open risks、发布收口和缺口测试；不重做已通过的业务模型。
- yFeiEye 的监督事件闭环仍是事实源；ReviewItem/ReviewSegment 负责线索、证据和复核状态，VIDEO 负责录像查询与导出。
- 本机当前没有可用的真实 VIDEO、发布 PostgreSQL、对象存储或已部署播放器参数，因此代码、迁移、smoke 和验收脚本必须完整，但真实联调只能在参数可用时执行；缺参时命令应失败并输出明确阻塞原因。
- 不修改运行时标识、环境变量名、存储 key 或服务名；只补其契约、校验和可见文案。

## Design

### 1. P0 data and release foundation

1. ReviewSegment lifecycle is event-time ordered and half-open: one active same-camera segment may exist for a time window; detection heartbeats extend/merge without downgrading alert severity; a late detection truncates or starts a new segment when overlap would occur; ended segments cannot reopen. Service pre-checks provide deterministic errors and PostgreSQL constraints/triggers remain the final guard.
2. Production migrations are split and tracked individually. The schema keeps a tenant-scoped partial unique index on active `review_item_id`, a partial camera/time exclusion constraint for non-deleted segments, status/severity/end-time checks, and the same-camera merge index. PG1 must verify the migration chain, half-open boundary behavior, soft-delete allowance, and concurrent insert races against PostgreSQL 16.
3. ReviewData is versioned by the checked-in JSON schema. Legacy rows are backfilled compatibly, and a runtime patrol reports/fixes ReviewSegment/ReviewData double-write drift without silently deleting evidence.
4. VIDEO integration uses four independent configuration values: alert-record query, record-coverage query, record base, and export URL. Empty configuration degrades to `missing / manual evidence` with canonical reason `video_url_not_configured` in API, UI, and patrol output. No URL aliasing or local/mock/file media is accepted in release smoke.
5. Real export evidence contains ffmpeg command hash, source segment hashes, clip parameters, concat order, output hashes, manifest v2 signature/version/key id, storage object key/type/expiry, and download audit. The offline verifier is shipped with the release package. The worker queue persists pending/running/failed/ready/expired state, retries with backoff, cleans expired jobs, and never makes a non-ready/expired URL downloadable.
6. Recording drift patrol compares DB metadata with the filesystem/object store and emits standard reasons: `video_url_not_configured`, `record_space_not_found`, `file_missing`, `probe_failed`, `permission_denied`, `retention_expired`, `disk_full`, `cache_flush_failed`.
7. Media actions (timeline, detail stream, coverage, snapshot, export, manifest verify, package verify, download, playback URL) resolve user/tenant/camera scope, fail closed, and write allow/deny audit rows. Audit reverse lookup carries `eventId`, `caseId`, `reviewItemId`, and `exportJobNo`.
8. Release packaging rejects loose FR core files and untracked production migrations. The full frontend `vue-tsc` baseline is a release gate, including patrol, Form, Player, and train modules.

### 2. P1 workflow hardening

1. Region rule editing is sourced from the real `DeviceRegionDrawer`; `inertiaFrames` and `loiteringSeconds` are persisted and shown in replay explanations.
2. Rule state machine is `suggested -> shadow evaluated -> accepted -> applied -> reverted`; direct application, low-sample acceptance, and unsafe repeated clicks are rejected. Suggestions persist minimum sample count, false-kill risk, impact scope, historical hit comparison, rule version, sample window, and estimated false negatives.
3. Event reverse projection runs from a scheduled reconcile job and records rollback/rework conflicts. Review cases support dedupe, merge, split, owner, close, optimistic locking, idempotent repeated actions, and audit. Converted items keep evidence/export actions while false-positive rollback is blocked by policy.
4. Cross-camera candidates use configured adjacency/regulatory-area topology plus time/object/correlation signals. Topology is explicit configuration and is surfaced in the workbench reason cards.
5. Evidence export worker, runtime outbox publisher, semantic index worker, and operations report jobs use persistent state, retry/backoff, stale-claim recovery, backlog/rebuild progress, delivery idempotency, and scheduler seeds. Station notify remains configurable and retryable when unconfigured.
6. Manifest HMAC supports key rotation (`keyId`, signature version, active/retired keyring) and ships an offline verifier bundle. AI summaries persist prompt/model/version, redacted provider context, provenance, human confirmation/rejection, and traceable audit entries.
7. Shift/daily reports aggregate missing-record rate, export failure rate, semantic backlog, false-positive rate, unreviewed backlog, responsibility unit, regulatory area, camera, and rule dimensions.

### 3. P2 operations and acceptance

1. API and migration artifacts expose the ReviewData schema version and compatibility repair path; patrol can detect and repair segment/review-data double writes with an auditable action.
2. Missing-video reason catalog is shared by VIDEO, DEVICE, UI, and smoke output. Semantic triggers expose hit explanations, action previews, and pending human confirmation.
3. Browser checks support both contract mock and real dev-server/API mode. Production smoke is ordered `visible-copy -> typecheck -> ingest -> coverage -> case -> export -> verify -> download -> audit -> detail seek -> coverage seek -> case-timeline seek`; each child summary is sanitized and traceable.
4. The FR-01~FR-38 table binds each capability to API, table/artifact, primary test, and acceptance command. Release documentation records executed commands, environment parameters, evidence path, and unresolved blockers.

## Verification matrix

| Domain | Required evidence |
| --- | --- |
| Java/DB | focused ReviewSegment, permissions, runtime, case, schema tests; PG1 on PostgreSQL 16; migration release verifier |
| VIDEO | pytest for coverage/export/drift; real ffmpeg and storage smoke; manifest offline verifier; download HEAD/content-type/content-length check |
| Frontend | workbench contract/dev-api-mock plus real dev-server mode; `vue-tsc` full baseline; visible-copy/UTF-8 scan |
| Production | `ProdSmoke` with four explicit VIDEO URLs, real camera/time, user/tenant/camera allow-deny scopes, real player seek assertions, audit-chain summary |
| Packaging | release-package verifier with `--require-clean`; no loose FR core files; all SQL in production migrations |

## Non-goals

- Do not import Frigate's NVR/Home Assistant stack.
- Do not replace the existing supervision event source of truth.
- Do not claim real integration, release DB safety, or player seek completion without fresh evidence from the required environment.
- Do not expand this work into unrelated dashboard/video refactors.
