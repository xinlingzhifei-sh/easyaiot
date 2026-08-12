# Alert Review FR-01 to FR-38 Hardening Review

Date: 2026-07-15

## Frigate-Inspired Boundary

yFeiEye should keep Frigate's useful business ideas, not copy its NVR stack:

- one review item groups related detections, alerts, snapshots, and recordings;
- object lifecycle rows can jump to the relevant recording time;
- zones, labels, object ids, confidence, bbox, and correlation ids must stay with the review item;
- review, export, and rule changes need replayable evidence and audit trails.

yFeiEye's source of truth is still the supervision closure model: alerts are clues, review cases are investigation workspaces, and converted events own closure state.

## Current Verdict

FR-01 to FR-38 are implemented, tracked, migrated, deployed, and production-accepted across DEVICE, VIDEO, and WEB. The final 2026-07-15 production smoke used the real GB28181 camera, real persisted FLV files, real ffmpeg, release MinIO, release PostgreSQL, the deployed workbench, and the native browser player; all seven parent gates passed. The formal matrix and traceability register below each contain exactly one FR-01 through FR-38 entry and bind the capability to interfaces, persisted state, tests, and release commands.

The acceptance result is component-proven rather than inferred from a label:

1. ReviewSegment PostgreSQL constraints and mapper transactions rejected an intentionally overlapping production replay against item 17, then accepted the next half-open time window; the final item 20 was explicitly transitioned to `ended`, leaving no smoke-created active segment.
2. DEVICE created item 20, case 14, event 13, and export job `REJ-42f3bf58-9984-4a27-806a-6f2ca402566a`; the downloaded export audit linked those exact four identities and the allow/deny playback decisions were persisted.
3. VIDEO returned continuous coverage, 10 drift-inspected recordings with zero issues, a real ffmpeg export, retained MinIO lifecycle metadata, manifest v2 HMAC metadata, and a successful remote offline verifier result.
4. Detail stream, coverage, and case timeline each opened the same real 32-second FLV at `seek_time=1784081599271`, observed native playback, and reported no media error. W4 and the full `vue-tsc --noEmit --skipLibCheck` W2 gate passed in the same parent run.

## 2026-07-15 Final Production Evidence

| Area | Final evidence |
| --- | --- |
| Sanitized production report | `.artifacts/production-smoke-fc972b4e.json`; SHA-256 `d5c683c5339b72e67dbe8338142064def86722e410f51958b5f65dcaa314e634`; `ok=true`; seven of seven steps passed; token/signature-query scan count `0`. |
| Parent gate timing | `2026-07-15T12:44:08.443Z` through `2026-07-15T13:06:39.229Z`, 1,350,786 ms. W4, full W2, LiveDevice, LiveVideo, detail, coverage, and case timeline all have independent exit-code/duration evidence. |
| DEVICE deployment | Complete JAR SHA-256 `bc817054bcf6e528292fa23b09a6a5273affd4382e119cdb96d6b45fabe9b7db`; image `sha256:ed72df631e3a7551761149f425433af6c7c71dd8c7d93c6f0b57e8dd78c3dfcc`, tagged `iot-module-system-biz:fr-r18-fc972b4e-full` and `latest`; `iot-system` healthy with restart count 0. |
| VIDEO deployment | `/opt/yfeieye-source/current` and the `/app` bind mount resolve to `/opt/yfeieye-source/releases/20260715-940c0155-fr`; final `record_export_service.py` SHA-256 `dbbf3493f763f5e4ec469911676ffde2e168562bdafd6ec93ef9d377b0c13e17`; `video-service` healthy with restart count 0. |
| Real recording replay | Record rows 37420-37422 reference three real camera FLVs covering 19:48:51.610-19:50:27.210. Each source file was uploaded through the real DVR/MinIO path, upload/source SHA-256 matched, and the local cache copy was removed before coverage/export acceptance. |
| Real export and verifier | VIDEO export `review-case-item-942aa64df8e3814bb88a1220` reached `ready` with `ffmpeg clipped and stitched evidence`; storage type `minio`, lifecycle `retained`, HMAC `hmac-sha256`, key id `2026-07-13`, signature version `v2`, signature valid, key available, and zero verifier violations. |
| Persistent retry recovery | Cross-process audit locking prevents mutable audit/manifest races during initial object-store sync; queue due times use the configured Shanghai clock even when the container runs UTC; retry clears stale error/due fields; replay prefers the server-managed original record URI after local source cleanup. A failed VIDEO job and DEVICE job 10 were requeued and then consumed by their persistent workers without manual ffmpeg execution. |
| Runtime scheduler | Natural 20:40 production ticks completed the runtime outbox, event reconcile, semantic index, evidence export worker, and runtime patrol handlers under the local-scheduler ownership model; shared Quartz ownership remains paused by `V20260713_4`. |
| Regression gates | DEVICE focused unified regression: 267 tests across 22 modules; Maven package: success. PostgreSQL release migration smoke: 25 migrations plus duplicate/concurrency races. VIDEO final discovery: 351 tests, all passing, with two explicit external-service skips. WEB: full type baseline, production build, contract/dev-api-mock/dev-api-real-drawer/playback E2E, and W4 copy scan all passed. |
| Cleanup and capacity | Smoke item 20 was ended. The ephemeral verifier SSH key, remote helper, token-refresh helper, and token files were removed. Two intermediate VIDEO release directories, one partial DEVICE image, two dangling fc972b4e images, and 2.674 GB of unused build cache were removed. Root disk improved from 97% to 95% while the current release and one DEVICE rollback image were retained. |

Production acceptance found and closed four defects that mock-only checks had not exposed:

- `test_initial_object_storage_sync_serializes_concurrent_access_audit` proves initial MinIO sync and concurrent allow/deny audit cannot publish different mutable metadata sizes; every metadata sync now holds the cross-process audit lock.
- `test_pending_retry_time_uses_shanghai_clock_in_utc_container` and `test_expiration_scheduler_uses_shanghai_clock_in_utc_container` prove queue/backoff/expiry due times remain correct when the container clock is UTC; manual retry also clears stale `last_error` and `next_attempt_at`.
- `test_retry_source_specs_restore_server_managed_original_record_uri` proves a persistent-worker replay uses `originalRecordUri` after ephemeral local `source-*` files have been removed.
- The production-smoke retained-MinIO regression proves the parent accepts VIDEO's canonical `retained` state while still rejecting missing, expired, deleted, or incomplete lifecycle evidence.

The matrix's final column is also the recurring release/operations runbook. Phrases such as "run" or "retain" there describe checks that must be repeated for future releases; they are not open blockers for this 2026-07-15 accepted artifact.

## FR Status Matrix

| FR | Capability | Current evidence in this branch | Release acceptance / ongoing operation |
| --- | --- | --- | --- |
| FR-01 | Alert review item aggregation | DEVICE tests cover clue ingest, merge windows, and tenant-scoped ingest identity; all 18 mapped supervision/review entities declare `@TableId` plus their matching PostgreSQL `@KeySequence`, required because the release PostgreSQL profile resolves the global MyBatis ID mode to `INPUT`; release PostgreSQL `PG1` covered historical backfill and duplicate races. VIDEO production `/video/alert/hook` requires an exact-byte service-HMAC `alert_ingest` context with tenant/camera identity, and realtime/snapshot/patrol algorithm senders use the signed request builder; unsigned ingest is limited to an explicit development/test switch. | Execute the current deployed ingest to prove IDs come from the 18 declared sequences rather than `NULL`, then prove real algorithm-to-VIDEO signed ingest, nonce/replay rejection, camera scope, and key rotation. |
| FR-02 | Unified workbench entry for alerts, snapshots, recordings | `AlertReviewWorkbench.vue` renders evidence timeline, emits image/video actions, and now exposes a list-level record playback action that opens the first playable evidence through the same playback-url guard before emitting `viewVideo`. | Run the same list playback path against the release API/player, not only dev-api-mock. |
| FR-03 | Review status actions | Tests cover reviewed, ignored, converted, repeated same-status clicks as idempotent, conflicting reviewer actions rejected, MapperStore `review_status`/`version` conditional updates for stale concurrent writes, and release `PG1` SQL passed stale-version rejection plus concurrent reviewer status races. | Retain the PG race evidence and verify the current deployed review actions under the final operator-role matrix. |
| FR-04 | Rule context and zone matching | Tests cover zone/object/stay rule match. | Keep region geometry in device-region source and prove drawer save writes back rule parameters. |
| FR-05 | Convert clue to supervision event | Tests cover idempotent event conversion, event projection, scheduled reverse event reconciliation, and event rollback/rework conflict policy; both event-conversion HTTP entrances require `supervision:event:create`, seeded idempotently by `V20260713_3__supervision_event_create_permission.sql`; normal conversions still dispatch responsibility tasks, while integration smoke persists a traceable `CREATED` event with task dispatch explicitly disabled. `V20260709__alert_review_scheduler_activation.sql` is the historical activation migration; final ownership is established by `V20260713_4__alert_review_local_scheduler_ownership.sql`, which pauses the shared Quartz source while the explicitly enabled local scheduler invokes event reconcile under a distributed lock. | Keep shared Quartz paused, explicitly grant `supervision:event:create` to intended non-superuser roles, then prove normal task dispatch plus a current smoke event with zero responsibility tasks and checkpoint `review_event_bound_without_task_dispatch`. |
| FR-06 | Automatic record evidence backfill | Tests cover resolver found/missing/retry. | Run against configured `yfeieye.video.alert-record-query-url` with a real VIDEO service. |
| FR-07 | Recording coverage window | DEVICE and VIDEO tests cover available/missing/motion/export segments; VIDEO coverage now classifies standard missing gap reasons for missing space, missing file, failed probe, permission denial, retention expiry, disk full, and cache flush failure; DEVICE coverage resolver now preserves retain mode, coverage source classification, exportability, non-exportable reason, and retain-until metadata from VIDEO; authorized coverage reads now attach returned record URIs to review evidence before auditing each returned URI; `LiveVideo` now includes VIDEO `non_exportable_reason` / gap reason details in the failure message when coverage has records but no playable/exportable segment, normalizes VIDEO `all` / `record` / `recording` aliases to `continuous`, requires live coverage retain/source classification evidence from the standard `continuous` / `motion` / `alert` / `detection` catalog, and emits a sanitized `coverageSummary` that `ProdSmoke` preserves at parent level. | Run live retain-mode/source classification and non-exportable reason smoke against real VIDEO recordings. |
| FR-08 | False-positive action and rule suggestion | Tests cover false-positive status, suggestion stats, minimum sample metadata, risk note, impact scope, and before/after hit comparison; the service refreshes current same-scope sample counts and blocks low-sample `accepted` / `applied` transitions, and the workbench hides accept/apply actions until `sampleRequirementMet` is true while still showing sample count, risk note, impact scope, and before/after hit estimates. | Run release smoke with real operator roles and real false-positive samples. |
| FR-09 | Cross-camera review case | Tests cover case timeline, clue dedupe, owner handoff, close-state guard, merge/split backend flow, case audit, candidate matching by correlation/object/time/adjacency, configured camera topology (`yfeieye.review.camera-topology.cameras`) for regulatory-area/adjacent-camera matches, `reviewData.caseCandidateMatch` payload explanations, and workbench topology candidate cards showing area/adjacent-camera/shared-object reasons with add-to-case action. | Add operator topology management UI for maintaining adjacency and regulatory-area source data instead of editing config by hand. |
| FR-10 | Semantic search | Tests cover local and external semantic provider fallback; workbench ops panel now exposes semantic backlog alarm level, rebuild progress, stale item count, and failed index count from the semantic index evaluation API. | Run production semantic worker schedule and real backlog smoke. |
| FR-11 | AI summary | Tests cover case timeline/evidence-aware summaries, external provider context, `aiProvenance` response metadata, persisted `ai_summary_generated` case audit traces, human confirmation/rejection audit transitions, reviewData plus timeline/action-note/material-URI prompt redaction for sensitive fields / phone / ID values, and configurable redaction policy version tracing. | Keep the same sanitizer on future provider payload fields and sign off the production catalog before release. |
| FR-12 | Evidence export | `GET /system/supervision/alert-review/evidence-export-jobs/{jobNo}/download` verifies the signed manifest, downloads each VIDEO artifact to a bounded temporary file, checks size and SHA-256, streams a single-camera MP4 directly or builds a deterministic multi-camera ZIP with artifact metadata/provenance, records the actual downloaded-byte hash, and deletes temporary files after streaming. Controller/service/provider regressions cover the byte, hash, content-type, ZIP, and cleanup contracts. | Run the endpoint against real VIDEO `downloadUrl` responses, large files, release camera grants, and expiry; verify response headers and hashes without counting module fixtures as production evidence. |
| FR-13 | Review Detail Stream | Tests cover object lifecycle rows, `seekTime`, bbox, path, camera, zone; workbench dev/API mock browser mode now verifies detail stream, coverage, and case timeline seek payloads reach the player event with exact `seek_time` and `record_path`; playback contract now preserves `seek_time`, computes `playback_offset_seconds`, lets native mp4 VOD consume that offset, `LivePlayer` can assert those fields against a deployed workbench without starting mocks, rejects local/mock workbench URLs and media evidence unless local endpoint mode is explicit, requires real native `video.currentTime` evidence when `--assert-native-current-time` is enabled, and `ProdSmoke` now runs separate `LivePlayer:detail`, `LivePlayer:coverage`, and `LivePlayer:case-timeline` steps with native-current-time assertions, parent-level `nativeCurrentTime` evidence requirements, seek summaries, and entry/action/expected seek context preserved without raw stdout while rejecting local/mock player media evidence in release mode. | Run `LivePlayer`/`ProdSmoke` against a real release player and real recording URL. |
| FR-14 | Async evidence export worker | VIDEO tests cover pending/running/ready/failed/retry/download audit/persistence and manifest storage lifecycle metadata for persisted artifacts; DEVICE has `processEvidenceExportQueue` plus `supervisionAlertReviewEvidenceExportWorkerJob`; `V20260710__alert_review_export_queue.sql` adds tenant-scoped `request_key` idempotency plus `claim_token` / owner / retry fields and a claim index. `V20260713_4` makes the explicitly enabled local scheduler the single execution source and leaves the shared Quartz export row paused. Focused mapper/store tests cover conflict-safe creation, `FOR UPDATE SKIP LOCKED`, versioned claim ownership, and claim-owned state transitions. | Keep the V4 ownership invariant and run current live multi-worker recovery, clipping, persistence, and expiry against the release database and object store. |
| FR-15 | Rule safe apply | Tests cover accepted-before-applied, shadow evaluation, rollback, controller permission annotations, and workbench UI permission gating for accept/apply/revert/replay rule actions. | Run release smoke for real operator roles and menu-permission assignments. |
| FR-16 | Semantic trigger and shift/daily report | Semantic triggers expose persisted preview-only `evaluate`, `get`, and idempotent `confirmation` contracts bound to the login operator. Normalized `inputHash`, immutable `inputVersion`, latest index version, and `indexGenerationId` survive evaluation, hit explanations, persisted audit, restore, confirmation, and duplicate confirmation. `V20260712` adds permission/terminal indexes; `V20260713` adds persistent semantic-worker claim, lease, retry, and generation fields. | Configure recipients and run real semantic/report scheduler and backlog recovery smoke. |
| FR-17 | Evidence-chain audit | Tests cover manifest verification, media access allow/deny audit, bound events, downloads, hash chaining, and reverse lookup metadata. A unified tenant-scoped `GET /system/supervision/alert-review/evidence-audit` intersects supplied `eventId`, `reviewCaseId`, `reviewItemId`, and `exportJobNo` across persisted item/audit/export relations with parameterized bounded SQL; malformed legacy metadata is not JSON-cast. `LiveDevice` performs that four-key lookup after the real export download and emits `evidence_audit_chain_verified`; `ProdSmoke` requires a positive `eventId`, non-empty singleton `eventIds`, exact item/case/event/job identity between the top-level DEVICE result and `summary.auditChain`, and `review_event_bound_without_task_dispatch`. Explicit empty arrays, mixed object/scalar arrays, or any key mismatch fail the release gate. Download audit distinguishes the logical package hash from the hash of the actual streamed MP4/ZIP bytes. | After a real deployed download, query all four keys together. Acceptance requires `eventIds=[eventId]`, `auditChain.reviewItemIds=[reviewItemId]`, matching `reviewCaseId`, `auditChain.eventIds=[eventId]`, matching `exportJobNo`, checkpoints `evidence_audit_chain_verified` and `review_event_bound_without_task_dispatch`, and zero responsibility tasks for the synthetic smoke event; verify returned hashes against downloaded bytes and prove each empty/mixed/mismatched-key negative case is rejected. |
| FR-18 | Rule replay validation | Tests cover replay before applying rule changes and rule suggestion approval now persists rule version, sample window, hit comparison, false-negative estimate, and replay report evidence; the workbench replay panel now explains rule version, sample window, hit comparison, and false-negative risk from the replay report. | Run release smoke against production historical samples. |
| FR-19 | ReviewData JSON | Tests cover labels, zones, object ids, confidence, bbox, correlation, schema version, runtime schema drift alerts, compatibility repair for older rows missing `reviewDataVersion` / `reviewSegment`, a standalone `alert-review-review-data-v1.schema.json` artifact, and production migration `V20260705` backfill of historical `review_data` rows; release `PG1` applied and verified the backfill path. | Retain release PG evidence and run the current deployed ingest/repair consistency probe. |
| FR-20 | ReviewSegment lifecycle | Tests cover active/detection/alert/ended, ended split behavior, ended truncation before late detection, ended extension rejected when it would overlap a later active same-camera segment, half-open camera/time overlap boundaries, migration drop/recreate for the exclusion constraint, mapper-store rejection before overlapping segment insert, mapper-store ignoring deleted overlapping segments before insert to match the PostgreSQL partial exclusion constraint, mapper default review-item/open-segment/camera-overlap lookups binding SMALLINT `deleted=0` after `V20260708_10`, mapper-store fail-fast validation for missing cameraId and invalid segment status before insert, mapper-store persistence of non-ended segments as DB open intervals (`end_time IS NULL`), alert status no-downgrade after later detection heartbeats or merged detection clues, service-level concurrent same-camera ingest, symmetric merge windows, event-time ordered `sourceAlertIds`, same-camera merge index shape without zone/rule filtering, DB-level status/severity checks, `V20260708` DB trigger rejection of alert-to-detection downgrade and ended reopen, `V20260708_7` ended segment `end_time` guard, `V20260708_8` alert segment severity guard, `V20260708_9` same-camera merge index rebuild, and a release PostgreSQL 16 `btree_gist` smoke for tenant-scoped `V20260702`/`V20260704` migration including open active same-camera overlap rejection, adjacent half-open same-camera boundary allowance for split detection segments, duplicate active `review_item_id` rejection, deleted duplicate `review_item_id` allowance, ended-without-end-time rejection, alert-with-detection-severity rejection, plus concurrent ReviewSegment overlap racing with exactly one successful insert. | Retain the release `PG1` evidence and execute the current deployed ingest/lifecycle smoke so every ReviewSegment lookup and split identity is proven against the final artifact. |
| FR-21 | Real VIDEO integration configuration | DEVICE resolver/export/download calls now use `VideoMediaServiceRequestSigner` with exact method/path/query/body coverage and tenant/user/camera/action headers; browser playback receives a short-lived signed media ticket. VIDEO validates service HMAC, nonce/time window/action/camera allowlists and delegates user tokens to `POST /system/auth/media-permission-check`; `alert_ingest` uses the same canonical contract. Existing LiveVideo gates still require distinct real alert, coverage, record, export, drift, manifest, and download paths. | Configure matching release secrets and explicit camera lists, then run real DEVICE↔VIDEO user-token, service-HMAC, playback-ticket, and `alert_ingest` interoperability plus key-rotation smoke. |
| FR-22 | Recording DB/disk sync | VIDEO keeps the standard gap/drift catalog and `GET /video/record/space/{spaceId}/videos/drift`; DEVICE now consumes it through the signed `HttpVideoRecordStorageDriftResolver`. Record/export reads resolve persisted `RecordSpace`/`RecordFile` camera ownership before authorization, reject camera mismatches, unsafe local paths/symlinks/`file://`, and audit allow/deny. DVR MinIO buckets are forced private and new objects use `tenants/{tenantId}/{deviceId}/{date}/{file}` with missing tenant rejected; legacy unprefixed metadata remains read-only compatible. | Run real drift repair plus historical bucket-policy removal and tenant-scoped upload/playback against production MinIO; decide repair/delete policy from observed data. |
| FR-23 | Permission and audit enforcement | `ConfiguredReviewCameraPermissionResolver` now intersects the authenticated user grant, tenant-owned camera records, requested scope, and action permission; same-tenant review history, tenant/default fallbacks, missing mapper/tenant/camera evidence, and configured fail-open cannot authorize a user. `POST /system/auth/media-permission-check` is authoritative for VIDEO user-token access. VIDEO record-management routes require a resolved camera and `record_manage`; `V20260711__alert_review_media_manage_permission.sql` seeds `system:supervision-alert-review:media:manage`, while playback/snapshot/coverage/export/download/manifest remain action-scoped and audited. | Assign release grants and menu permissions, then run deployed allow/deny tests for two users, two tenants, owned/unowned cameras, every media action, and forged request scopes. |
| FR-24 | Runtime health and reconciliation | Tests cover health metrics, runtime reconcile, storage/review drift, outbox claim/retry/delivery, and stale processing recovery. `V20260709` is the historical activation migration; final production ownership is `V20260713_4`, which pauses the shared Quartz rows and relies on the explicitly enabled `yfeieye.review.local-scheduler` source under distributed non-reentrant locks. `HttpVideoRecordStorageDriftResolver` consumes the authenticated VIDEO drift report without breaking the scheduler when VIDEO is missing or unavailable. | Keep all shared Quartz rows paused, configure the final notify sink and signed VIDEO access, and retain recurring run/retry evidence; the 2026-07-15 natural semantic-index tick passed. |
| FR-25 | Runtime locks and patrol profile | Tests cover runtime patrol profile, locks, gap reasons, `configure_video_record_query_url` action, stale runtime lock recovery metadata, active-lock blocking, and conditional DB stale-lock takeover. | Run clustered deployment lock smoke across real scheduler nodes and verify stale-lock recovery under production clock skew. |
| FR-26 | Missing config degradation | Resolver failure now degrades to missing with standardized `video_url_not_configured`; runtime health exposes a standard missing-record reason catalog for unconfigured query, missing record space, missing file, probe failure, permission denial, and retention expiry; runtime patrol and outbox metadata carry `recordGapReasons` plus `recordGapReasonDetails` for `video_url_not_configured`; workbench displays item and health reason summaries, and the browser regression now proves the unconfigured VIDEO URL path renders as `缺录像/待手动补证` with `VIDEO URL 未配置`; compose defaults reduce local missing-config drift while env overrides can still intentionally degrade. | In a disposable release profile, intentionally unset the VIDEO URLs, call runtime patrol, and load the workbench to prove the standard reason in API/UI/report evidence; then restore configuration and run the real smoke. |
| FR-27 | Reproducible video export | VIDEO export keeps manifest v2 hashes, clip/segment provenance, HMAC keyring metadata, atomic audit/commit state, and readback verification. For MinIO/S3, all content/job/audit/manifest/commit/source, staging, cleanup, and readback keys use `tenants/{tenantId}/exports/{exportId}/...`; object-storage exports without a tenant fail closed. The opt-in real ffmpeg+MinIO smoke passed through an SSH tunnel to the release MinIO with five persisted artifacts, real input/output hashes, one-second media duration, current HMAC key, and verifier success. | Repeat against the deployed VIDEO endpoint and archive tenant-isolation, cleanup, expiry, and download evidence. |
| FR-28 | Manifest v2 and verifier | Tests cover `manifestVersion=2`, `yfeieye.record-export.manifest.v2`, canonical source/output SHA-256 digest checks, canonical HMAC signature value, HMAC keyring rotation by `keyId`, offline verifier wrapper, verifier non-zero exit rejection, verifier timeout rejection, tampering, LiveVideo rejection of unsigned or placeholder-signed live export manifests, LiveVideo and ProdSmoke requiring a release manifest verifier script, ProdSmoke evidence capture of manifest signature key/version metadata, ProdSmoke parent-level rejection when LiveVideo omits valid `manifestVerification`, ProdSmoke requiring verifier `signatureValid=true` and `signatureKeyAvailable=true` matching the manifest key/version, and release-package scanning that blocks removal of LiveVideo manifest evidence, verifier evidence enforcement, verifier timeout enforcement, verifier signature/key evidence enforcement, plus returned media evidence gates. | Run verifier against production key custody/escrow and real exported evidence packages. |
| FR-29 | Rule semantic consistency | Existing tests cover bottom-center geometry, inertia, loitering, real drawer save, and replay. VIDEO now has a fresh-install `bootstrap_schema.py`, checksum/history/advisory-lock `apply_migrations.py`, and `migrations/V20260711__device_detection_region_rule_fields.sql`; `docker-compose.yaml` runs bootstrap then migrations before `run.py`, and the release-database migration/`--verify-only` path passed through an SSH tunnel. | Retain VIDEO schema-history/checksum evidence, then execute the current real drawer-save/replay/`ProdSmoke`; migration proof alone does not prove the operator flow. |
| FR-30 | Event reverse status linkage | Review rows persist `reviewData.eventProjection`; `SupervisionAlertReviewEventReconcileJob` reconciles converted clues outside list query time; converted items keep evidence actions while late false-positive rollback is blocked; `V20260708_2` seeds the job, `V20260709` historically activates it, and final `V20260713_4` pauses the shared Quartz row so only the explicitly enabled local scheduler owns execution. | Retain local-scheduler recurring-run evidence and prove the current final package against a real rollback/rework event before release closure. |
| FR-31 | Review case lifecycle | Case grouping, timeline, clue dedupe, owner handoff, close state, closed-case add rejection, merge-to-target, split-to-new-case, source case `merged` status, `case_audit` entries, owner/close/merge/split controller command mapping, and workbench lifecycle controls are covered at service/store/controller/browser-contract level. | Run release smoke against the real backend and operator workflow. |
| FR-32 | Production smoke | `ProdSmoke` gates W4 visible copy, full W2 `vue-tsc`, deployed DEVICE integration, real VIDEO alert/coverage/drift/export/manifest/download reachability, and three deployed native-player seek entrances, while rejecting local/mock/file evidence and writing a sanitized evidence report. The DEVICE step must emit `eventId`, singleton `eventIds`, `evidence_audit_chain_verified`, and `review_event_bound_without_task_dispatch`; parent validation rejects any mismatch across `reviewItemId`, `reviewCaseId`, `eventId`, and `exportJobNo` versus the persisted `export_downloaded` audit chain, and `Pkg` anchors the real four-parameter `/evidence-audit` call plus that contract. The orchestrator does not itself prove the separate DEVICE streaming-download endpoint, real `alert_ingest` HMAC caller, or private-MinIO tenant isolation. | Run the documented full command against real release services and retain the sanitized report showing the exact four-key audit chain and both checkpoints; query the release DB to prove the smoke event has zero tasks. Then execute the separate deployed single-MP4 and multi-camera-ZIP download probe plus real service-HMAC and private-MinIO evidence. No parameterless/local invocation, empty/mixed `eventIds`, mismatched audit key, or task-dispatching smoke event counts as production smoke. |
| FR-33 | Semantic index operations | Queue/evaluation/reindex contracts include a schedulable worker, persistent claim/lease ownership, failed-item retry/backoff, backlog alarm level, latest index version, generation id, rebuild progress, and workbench-visible status; `V20260713` persists claim state and `V20260713_4` leaves shared Quartz paused so the explicitly enabled local scheduler is the single worker source. | Confirm the current deployed semantic fix on a natural scheduled tick, then run real backlog and expired-lease recovery evidence; no pre-fix failure counts as a pass. |
| FR-34 | AI provenance | AI summary `structuredData.aiProvenance` now returns provider, model, providerVersion, promptVersion, promptHash, redaction policy version, redaction status, redacted fields, human confirmation status, requester, and generated metadata; generation writes `case_audit.metadata` with provenance/hash counts and policy version; confirmation/rejection writes idempotent `ai_summary_confirmed` / `ai_summary_rejected` audit entries bound to prompt and summary hashes; provider requests redact reviewData sensitive keys plus timeline action notes/material URIs containing phone / ID values before prompt construction through `yfeieye.review.ai-summary.redaction.*` policy. | Sign off the production sensitive-key/value catalog and keep provider payload expansion behind the same sanitizer. |
| FR-35 | Operational reports | Shift/daily reports cover delivery plan, persisted/idempotent acknowledgement, per-recipient delivery dedupe, outbox claim/reclaim, backlog/error rates, and responsibility/area/camera/rule dimensions; `V20260708_2`-`V20260708_6` persist scheduler/outbox/report state, while final `V20260713_4` pauses shared Quartz and adds atomic report-outbox idempotency for the local scheduler owner. | Configure release recipients/templates and the final dashboard sink, then run scheduler delivery and acknowledgement smoke against real operators while proving no second Quartz execution source is active. |
| FR-36 | Frontend E2E | Workbench contract/playback harnesses remain release gates, `Pkg` guards the `vue-tsc --noEmit` script, and `ProdSmoke` runs W4 then W2 before live services. On 2026-07-13, the pinned full `vue-tsc --noEmit --skipLibCheck`, production build, and all workbench E2E modes exited 0. | Rerun the same gates from the packaged commit and keep real API/player smoke as a separate acceptance step. |
| FR-37 | Chinese encoding quality | Workbench contract now rejects replacement characters and common mojibake fragments, asserts the required UTF-8 Chinese review/record/event labels, `alert-review-visible-copy-scan.mjs` covers workbench, player, patrol, playback utility, real drawer E2E fixture, and VIDEO record/export visible-copy files, and the release package verifier now includes `WEB/src/api/device/patrol.ts` plus `WEB/src/utils/alertRecord*.ts` in the same text-quality gate while reusing the W4 mojibake pattern catalog; the catalog also blocks common wrong-decoded missing-record fallback fragments such as `缺录像/待手动补证` and `VIDEO URL 未配置` turning into `\u7f02\u54c4...` / `\u93c8...` patterns. | Keep expanding W4 targets as new release-visible review/player/report files are added. |
| FR-38 | Traceability | This document records the DEVICE/VIDEO/WEB hardening delta through 2026-07-13, DEVICE migrations through `V20260713_4`, VIDEO migrations through `V20260713`, current local/real-dependency verification, explicit live-release gaps, and an API/artifact/test/command row for every FR. | Rerun the FR-number, unresolved-placeholder, mojibake, V4 documentation-anchor, package self-test, and clean-tree packaging scans after every release delta. |

## FR Traceability Register

Command aliases used below:

- `J1`: `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest,SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,HttpVideoResolverTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
- `J2`: `mvn -pl iot-system/iot-system-biz -am "-Dtest=ConfiguredReviewCameraPermissionResolverTest,MediaPermissionCheckControllerTest,VideoMediaServiceRequestSignerTest,HttpVideoResolverStorageDriftTest,ReviewEvidenceManifestSignerTest,SupervisionAlertReviewAuditLookupMapperTest,SupervisionAlertReviewExportQueuePersistenceTest,SupervisionAlertReviewRuntimeSchedulingTest" -DfailIfNoTests=false test`; release acceptance requires the reactor command, even when a local target-module-only run is used to isolate unrelated reactor compilation drift.
- `V1`: from `VIDEO/`, run `python -m unittest -q test_record_export test_record_availability`.
- `V2`: from `VIDEO/`, run `python -m unittest discover -q`; the 2026-07-13 release-candidate run executed 333 tests with 0 failures/errors and 2 explicit external-service skips.
- `VMig`: from `VIDEO/`, run `python bootstrap_schema.py --database-url=postgresql://...`, then `python apply_migrations.py --database-url=postgresql://...`, restart the service, and run `python apply_migrations.py --database-url=postgresql://... --verify-only`; verification must show checksum/history coverage for `V20260711` through `V20260713`, and a unit/dry-run plan is not a substitute for this release-database gate.
- `MinIO`: configure a private release-like endpoint plus `YFEIEYE_RECORD_EXPORT_STORAGE_TYPE=minio`, `YFEIEYE_RECORD_EXPORT_STORAGE_URI=s3://...`, HMAC keyring variables, and `YFEIEYE_RECORD_EXPORT_REAL_MINIO_SMOKE=true`, then run `python -m pytest test_record_export_minio_smoke.py -q`; a skip is not a pass.
- `W1`: from `WEB/`, run `pnpm test:alert-review-workbench`; from the repository root use `corepack pnpm@11.3.0 --dir WEB run test:alert-review-workbench`. Split modes are `test:alert-review-workbench:contract`, `test:alert-review-workbench:dev-api-mock`, `test:alert-review-workbench:dev-api-real-drawer`, and runner validation `test:alert-review-workbench:runner`.
- `W2`: from `WEB/`, run `pnpm run type:check`; from the repository root use `corepack pnpm@11.3.0 --dir WEB run type:check`. Full Vue SFC type checking must exit 0 from the release tree.
- `W3`: from `WEB/`, run `pnpm test:alert-review-playback`; from the repository root use `corepack pnpm@11.3.0 --dir WEB run test:alert-review-playback`. It validates workbench-to-player `seek_time`, `record_start_time`, and `playback_offset_seconds` handoff.
- `W4`: `node .scripts/alert-review-visible-copy-scan.mjs`; scans workbench, player, patrol, playback utility, real drawer E2E fixture, and VIDEO record/export visible-copy files for UTF-8 replacement characters and common mojibake fragments; `Pkg --require-clean` reuses the same mojibake pattern catalog for release-package text quality.
- `PG1`: `node .scripts/alert-review-postgres-migration-smoke.mjs --container=<postgres-container>` or `node .scripts/alert-review-postgres-migration-smoke.mjs --database-url=postgresql://.../postgres`; direct URL mode keeps the password out of `psql` argv. The current plan applies `V20260701` through `V20260713_4` in `MIGRATION_FILES` order, including `V20260708_10` SMALLINT deletion alignment, the historical `V20260709` activation migration, `V20260710` persistent export queue claims/idempotency, `V20260711` `media:manage`, `V20260712` semantic-trigger permission/terminal indexes, `V20260713` semantic-index generation/claim/lease/retry fields, `V20260713_2` evidence record start, `V20260713_3` `supervision:event:create`, and final `V20260713_4` local-scheduler ownership, paused shared Quartz rows, and atomic operations-report outbox idempotency, in addition to the earlier ingest, ReviewData, ReviewSegment, media-audit, report, and outbox assertions.
- `Smoke`: `POST /system/supervision/alert-review/integration-smoke` against release DEVICE + real VIDEO recordings.
- `LiveDevice`: set `YFEIEYE_DEVICE_AUTH_TOKEN` in the process environment, then run `node .scripts/alert-review-device-integration-smoke.mjs --device-base-url=... --tenant-id=... --operator-user-id=... --alert-time="YYYY-MM-DDTHH:mm:ss" [--playback-allowed-camera-ids=... --playback-denied-camera-ids=...]`; the CLI rejects token arguments so credentials never enter process argv. It requires a deployed DEVICE service and fails unless the endpoint returns `passed`, `manifestValid=true`, `videoExportRequested=true`, and all ingest / coverage / case / export / verify / download-audit checkpoints; when playback camera params are supplied, it also requires playback URL allow/deny decisions to produce `playback_url_granted` and `playback_url_denied`.
- `LiveVideo`: from the packaged repository/release root, set `YFEIEYE_VIDEO_SMOKE_TOKEN` in the process environment and run `node .scripts/alert-review-video-live-smoke.mjs --alert-record-query-url=... --record-coverage-query-url=... --record-base-url=... --record-export-url=... --device-id=... --alert-time="YYYY-MM-DD HH:mm:ss" --record-drift-retention-hours=24 --manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs`; the CLI rejects token arguments so credentials never enter process argv. Running a copied wrapper outside that tree is invalid because the wrapper resolves `VIDEO/app/services/record_export_manifest_verifier.py` relative to itself. It requires a real VIDEO service and real recording metadata, requires `--record-coverage-query-url` / `YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL` explicitly instead of reusing the alert-record URL, requires the offline manifest verifier in release mode, rejects inline or opaque `data:` / `blob:` media evidence, rejects local/mock/file endpoints and returned `record_uri` / `download_url` / `manifest_url` / playable `file_path` / absolute local filesystem / protocol-relative local or mock / relative `mock/...` media evidence unless `--allow-local-endpoints` is explicitly supplied for co-located real-service smoke, and fails unless the recording storage drift patrol is healthy, export status exposes a reachable `download_url` whose HEAD response is video/octet-stream with non-empty `content-length` when present, and `manifest_url` manifest v2 includes canonical ffmpeg command SHA-256 hash, canonical source segment SHA-256 hashes, valid clip windows, complete non-duplicated root/segment concat order, canonical output SHA-256 hashes, and canonical HMAC signature metadata (`algorithm`, `keyId`, signature version, hex or base64 value). Non-zero or timed-out verifier execution also fails the smoke. Its CLI JSON prints `storageDriftSummary` plus `manifestSignature` key/version metadata for release evidence; it does not accept `--evidence-output-file`.
- `LivePlayer`: `node .scripts/alert-review-player-live-smoke.mjs --workbench-url=... --review-row-text=... --action-testid=alert-review-detail-seek --expected-seek-time=... --expected-record-path-contains=... --expected-offset-seconds=... --assert-native-current-time`; requires a deployed workbench, auth state, and real recording-backed review row; local/mock workbench URLs and media evidence are rejected unless `--allow-local-endpoints` is supplied for co-located real-service smoke; native video `currentTime` evidence is required when the native assertion flag is set, and `ProdSmoke` invokes it three times for detail stream, coverage, and case timeline.
- Release playback routing keeps internal service discovery and browser routing separate: DEVICE uses the four `YFEIEYE_VIDEO_*_URL` values with the private `http://host.docker.internal:6000/video/...` endpoints, while `YFEIEYE_VIDEO_PUBLIC_PLAY_HOST=https://eye.yfeiai.com/yfeieye/dev-api` rewrites only browser-visible VIDEO URLs through nginx. VIDEO keeps `MEDIA_HTTP_PLAY_HOST=https://eye.yfeiai.com` without the `/yfeieye/dev-api` prefix because `/live`, `/ai`, and `/rtp` are public stream routes. The playback ticket is HMAC-signed against the canonical `/video/...` path seen by VIDEO after nginx removes the public prefix; publishing `host.docker.internal` or signing the prefixed nginx path fails release acceptance.
- `ProdSmoke`: set `YFEIEYE_DEVICE_AUTH_TOKEN` in the parent environment, then run `node .scripts/alert-review-production-smoke.mjs --device-base-url=... --tenant-id=... --operator-user-id=... --device-alert-time=... --device-playback-allowed-camera-ids=... --device-playback-denied-camera-ids=... --video-alert-record-query-url=... --video-record-coverage-query-url=... --video-record-base-url=... --video-record-export-url=... --video-device-id=... --video-alert-time=... --video-record-drift-retention-hours=24 --video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs --step-timeout-ms=900000 --player-workbench-url=... --player-review-row-text=... --player-expected-seek-time=... --player-expected-record-path-contains=... --player-expected-offset-seconds=... --player-coverage-expected-seek-time=... --player-coverage-expected-record-path-contains=... --player-coverage-expected-offset-seconds=0 --player-case-timeline-expected-seek-time=... --player-case-timeline-expected-record-path-contains=... --player-case-timeline-expected-offset-seconds=0 --evidence-output-file=artifacts/production-smoke.json`; credentials are passed only through per-step child environments and removed from unrelated W4/W2 steps. It runs `W4:visible-copy`, `W2:typecheck`, `LiveDevice`, `LiveVideo`, and three `LivePlayer:*` steps sequentially and fails the release on the first failed visible-copy scan, failed type check, failed real-service smoke, missing child evidence summary, missing release manifest verifier, missing evidence report path, or local/mock player media evidence; localhost/mock/file endpoints are rejected unless `--allow-local-endpoints` is supplied for co-located real-service smoke and is propagated to VIDEO and player child smokes.
- `Pkg`: `node .scripts/verify-alert-review-release-package.mjs`; use `--require-clean` for a release artifact that must come only from HEAD.

| FR | API / entry point | Tables / artifacts | Primary tests | Acceptance command |
| --- | --- | --- | --- | --- |
| FR-01 | DEVICE `POST /system/supervision/alert-review/clues/ingest` plus VIDEO `POST /video/alert/hook` | ingest item/identity/evidence tables and their PostgreSQL ID sequences; VIDEO `media_authorization_service.py` signed `alert_ingest` request | DEVICE ingest/idempotency tests, `SupervisionPersistenceIdContractTest`; `test_alert_ingest_request_builder_round_trips_through_service_hmac`, `test_alert_ingest_sender_posts_the_signed_bytes_not_a_reserialized_json_body`, unsigned-production rejection tests | `J1`, `V2`, `PG1`, `LiveDevice`; deployed sequence-backed ingest and signed caller gates passed; repeat HMAC rotation smoke for future key changes |
| FR-02 | `GET /items/{reviewItemId}/timeline`, `GET /items/{reviewItemId}/playback-url`, `AlertReviewWorkbench.vue` | `system_supervision_alert_review_evidence`, workbench SFC, playback-url audit guard | `workbenchQueryAndSummarySupportEvidenceEventCaseAndReviewerPerspective`, workbench contract selectors, dev-api-mock `alert-review-list-playback` list playback event and playback preparation assertion | `J1`, `W1`, `LivePlayer` |
| FR-03 | `POST /items/{id}/review`, `/ignore`, `/false-positive`, `/user-status` | `system_supervision_alert_review_item`, `system_supervision_alert_review_user_status` | `reviewStatusCanBeConfirmedOrIgnoredBeforeConversion`, `reviewStatusActionsAreIdempotentAndRejectConflictingReviewerActions`, `updateReviewStatusRejectsConcurrentStatusConflict`, `userReviewStatusTracksMultipleReviewersIndependently`, `alert-review-postgres-migration-smoke.mjs` review status/version race smoke | `J1`, `PG1` |
| FR-04 | `POST /rules`, `POST /rules/geometry-evaluate` | `system_supervision_alert_review_rule` | `regionRuleSuppliesRuleCodeOnlyWhenZoneObjectAndStayTimeMatch`, `ruleGeometryAndReplayUseZoneInertiaAndLoiteringSemanticsFromSavedRule` | `J1` |
| FR-05 | `POST /items/{id}/to-event`, `POST /system/supervision/events/from-alert`, `supervisionAlertReviewEventReconcileJob` | `system_supervision_event`, `system_supervision_alert_review_item.review_data`, `V20260709__alert_review_scheduler_activation.sql`, `V20260713_3__supervision_event_create_permission.sql`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | `convertReviewItemToSupervisionEventUsesReviewItemAsIdempotentSource`, `createFromAlertCanPersistSyntheticEventWithoutDispatchingTasks`, controller permission tests, `eventReconcileJobPersistsReverseEventProjectionOutsideListQuery`, `localSchedulerUsesDistributedNonReentrantLockAndPausesQuartzDoubleSource` | `J1`, `J2`, `PG1`, `Smoke`; keep Quartz paused, grant intended roles, prove normal dispatch and smoke zero-task event |
| FR-06 | `POST /items/{id}/record-evidence/retry`, `yfeieye.video.alert-record-query-url` | `system_supervision_alert_review_evidence`, VIDEO record metadata | `ingestWithoutRecordUriBackfillsRecordEvidenceWhenResolverFindsRecord`, `alertRecordResolverParsesVideoPayloadAndRewritesRelativePlaybackUrl` | `J1`, `Smoke` |
| FR-07 | `GET /items/{id}/record-coverage`, `GET /video/record/availability` | VIDEO record metadata, `system_supervision_alert_review_evidence` | `recordCoverageReturnsAvailableOrMissingWindowSegments`, `allowedRecordCoverageReadAuditsReturnedRecordUrisInEvidenceTrail`, `coverageResolverPreservesRetainModeSourceAndNonExportableReason`, `test_build_recording_availability_returns_available_missing_motion_and_export` | `J1`, `V1`, `Smoke` |
| FR-08 | `POST /items/{id}/false-positive`, `/rule-suggestion/*` | `system_supervision_alert_review_item.rule_suggestion`, `system_supervision_alert_review_rule`, workbench safety summary | `falsePositiveActionMarksStatusAndCreatesRuleSuggestion`, `lowSampleRuleSuggestionCannotBeAcceptedBeforeMoreReviewSamples`, `ruleSuggestionStatsAggregateFalsePositiveRateByCameraZoneLabelAndWindow`, workbench dev-api-mock rule suggestion safety summary and low-sample action hiding checks | `J1`, `W1` |
| FR-09 | `POST /cases`, `/cases/{id}/items/{itemId}`, `/case-candidates` | `system_supervision_alert_review_case`, `system_supervision_alert_review_case_item`, `system_supervision_alert_review_case_audit`, `reviewData.regulatoryArea` / `adjacentCameras` / `objectIds`, `reviewData.caseCandidateMatch`, `ConfiguredReviewCameraTopologyResolver` | `reviewCaseCollectsMultipleCameraCluesIntoOneTimeline`, `reviewCaseCandidatesUseAdjacentCameraZoneAndRegulatoryArea`, `reviewCaseCandidatesUseConfiguredCameraTopologyWhenReviewDataHasNoTopology`, workbench dev-api-mock topology candidate reason and add-to-case flow check | `J1`, `W1` |
| FR-10 | `GET /semantic-search`, `POST /semantic-index/reindex` | `system_supervision_alert_review_semantic_index` | `semanticSearchRanksReviewItemsByDetectionEvidenceContext`, `semanticSearchCanUseExternalProviderBeforeLocalKeywordFallback` | `J1` |
| FR-11 | `GET /cases/{id}/ai-summary`, `POST /cases/{id}/ai-summary/confirmation` | AI summary response payload with `structuredData.aiProvenance`, `system_supervision_alert_review_case_audit.metadata`, `ai_summary_confirmed` / `ai_summary_rejected` case audit entries, redacted provider prompt context, `ReviewAiSummaryRedactionPolicy` / `yfeieye.review.ai-summary.redaction.*` | `aiSummaryAndEvidenceExportUseCaseTimelineEvidenceCoverageAndActions`, `aiSummaryCanUseExternalProviderWithCaseTimelineContext`, `aiSummaryRedactsSensitiveReviewDataBeforeProviderAndAuditProvenance`, `aiSummaryRedactsTimelineNotesAndMaterialUrisBeforeProvider`, `aiSummaryRedactionPolicyCanBeOverriddenAndRecordedInProvenance`, `aiSummaryConfirmationKeepsGeneratedProvenanceAndIsIdempotentForSameStatus`, `aiSummaryConfirmationRequiresGeneratedSummaryAudit` | `J1` |
| FR-12 | evidence-export create/job APIs, `GET /evidence-export-jobs/{jobNo}/download`, VIDEO export/download APIs | `system_supervision_alert_review_export_job`, verified temporary MP4 or deterministic multi-camera ZIP, download audit metadata | `evidenceDownloadReturnsVerifiedVideoBytesAndPersistsRealByteHash`, `evidenceDownloadPackagesEveryCameraArtifactAndProvenanceIntoVerifiedZip`, `evidenceDownloadProxiesVerifiedBytesAndUsesLoginUser`, `HttpVideoResolverTest` byte/hash/cleanup cases | `J1`, `J2`; deployed large-file MP4/ZIP download required |
| FR-13 | `GET /items/{id}/detail-stream`, workbench seek action | `reviewData.reviewSegment`, `system_supervision_alert_review_segment`, `DialogPlayer` playback payload, `.scripts/alert-review-player-live-smoke.mjs` | `reviewDetailStreamExpandsObjectsDetectionsAndSeekTimes`, workbench `alert-review-detail-seek`, `alert-review-coverage-seek`, and `alert-review-case-timeline-seek` payload assertions, `alert-review-playback-contract.test.mjs`, `alert-review-player-live-smoke.test.mjs` | `J1`, `W1`, `W3`, `LivePlayer` |
| FR-14 | `GET/POST /video/record/export/{export_id}`, retry/audit/download routes, DEVICE `processEvidenceExportQueue`, `supervisionAlertReviewEvidenceExportWorkerJob` | VIDEO export persistence, DEVICE `system_supervision_alert_review_export_job`, `V20260710__alert_review_export_queue.sql`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | VIDEO async worker tests, `evidenceExportWorkerRebuildsFailedJobsAndLeavesReplayableManifest`, `SupervisionAlertReviewExportQueuePersistenceTest`, `localSchedulerOwnershipMigrationPausesQuartzAndMakesReportEnqueueAtomic`, `alert-review-postgres-migration-smoke.test.mjs` | `V1`, `J1`, `J2`, `PG1`; keep Quartz paused and run live multi-worker/object-store smoke |
| FR-15 | `POST /items/{id}/rule-suggestion/status`, `/revert`, `/rules/replay`, workbench rule action buttons | `system_supervision_alert_review_rule`, rule suggestion payload, controller permission annotations, `AlertReviewWorkbench.vue` permission gating | `ruleSuggestionGovernanceEndpointsDeclareApprovalPermissions`, `ruleSuggestionSafeApplyRequiresApprovalAndStoresShadowEvaluation`, `falsePositiveRuleSuggestionAppliesRuleConfigOnlyAfterApprovalAndCanRollback`, workbench rule governance permission contract | `J1`, `W1` |
| FR-16 | `POST /semantic-triggers/evaluate`, `GET /semantic-triggers/{evaluationId}`, `POST /semantic-triggers/{evaluationId}/confirmation`, operations-report APIs | `system_supervision_alert_review_case_audit`, semantic trigger evaluation/terminal metadata, semantic index generation/claim fields, report/outbox tables, `V20260712__alert_review_semantic_trigger_confirmation.sql`, `V20260713__alert_review_semantic_index_claim.sql`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | semantic trigger immutable input/version/generation round-trip tests, `semanticTriggerConfirmationRestoresPersistedPreviewAndIsIdempotentWithoutExecutingActions`, semantic worker claim/lease/backoff tests, `localSchedulerOwnershipMigrationPausesQuartzAndMakesReportEnqueueAtomic`, controller contract, migration/report/outbox tests | `J1`, `J2`, `PG1`; keep Quartz paused and run the current real scheduler/backlog smoke |
| FR-17 | `GET /evidence-audit?eventId=&reviewCaseId=&reviewItemId=&exportJobNo=`, case/item audit routes, download audit | `system_supervision_alert_review_case_audit`, `system_supervision_alert_review_item`, `system_supervision_alert_review_export_job`, `summary.eventId`, singleton `summary.eventIds`, exact `summary.auditChain`, `evidence_audit_chain_verified`, `review_event_bound_without_task_dispatch` | `unifiedEvidenceAuditLookupMapsFourIntersectingKeys`, `unifiedEvidenceAuditLookupFindsPersistedMediaExportAndDownloadByEventOrJobAndIntersectsKeys`, `SupervisionAlertReviewAuditLookupMapperTest`, `evidenceAuditLookupPassesAllKeysAndCurrentTenantToBoundedMapperQueries`, DEVICE four-key audit lookup smoke, production-smoke empty/mixed-array and item/case/event/job mismatch rejection tests | `J1`, `J2`, `LiveDevice`, `ProdSmoke`, `Pkg`; deployed exact four-key reverse lookup, zero-task smoke event, and negative mismatch proof required |
| FR-18 | `POST /rules/replay` | `system_supervision_alert_review_rule`, rule suggestion replay report payload, workbench replay report explanation | `ruleSuggestionSafeApplyRequiresApprovalAndStoresShadowEvaluation`, `ruleReplayEvaluatesHistoricalItemsBeforeApplyingRuleChange`, `ruleGeometryUsesBottomCenterSemanticsAndReplayRequiresSafeVersionLifecycle`, workbench dev-api-mock replay explanation check | `J1`, `W1` |
| FR-19 | ReviewData JSON in ingest/update paths | `system_supervision_alert_review_item.review_data`, `alert-review-review-data-v1.schema.json`, `V20260705__alert_review_review_data_backfill.sql` | `reviewItemKeepsFrigateLikeReviewDataFromDetectionContext`, `reviewDataJsonSchemaArtifactDefinesVersionedFrigateReviewFields`, `alertReviewReviewDataBackfillMigrationNormalizesLegacyRows`, `alert-review-postgres-migration-smoke.mjs` | `J1`, `PG1` |
| FR-20 | `GET /items/{id}/review-segment`, `POST /items/{id}/lifecycle` | `system_supervision_alert_review_segment`, `V20260702__alert_review_frigate_hardening.sql`, `V20260704__alert_review_segment_tenant_scope.sql`, `V20260708__alert_review_segment_status_transition.sql`, `V20260708_7__alert_review_segment_end_time_guard.sql`, `V20260708_8__alert_review_segment_alert_severity_guard.sql`, `V20260708_9__alert_review_merge_index_same_camera.sql` | `concurrentIngestKeepsSingleActiveReviewSegmentForSameCameraWindow`, `reviewSegmentLifecycleRejectsEndedExtensionOverlappingLaterActiveSegment`, `reviewSegmentOverlapUsesHalfOpenIntervalsSoAdjacentSegmentsCanSplitCleanly`, `selectByReviewItemIdIgnoresSoftDeletedRowsToMatchPartialUniqueIndex`, `selectOverlappingIgnoresSoftDeletedRowsToMatchExclusionConstraint`, `createRejectsOverlappingReviewSegmentBeforeSegmentInsert`, `createIgnoresDeletedOverlappingReviewSegmentBeforeSegmentInsert`, `createPersistsNonEndedReviewSegmentAsOpenInterval`, `createRejectsReviewSegmentWithoutCameraBeforeSegmentInsert`, `createRejectsInvalidReviewSegmentStatusBeforeSegmentInsert`, `alertReviewSegmentTenantScopeMigrationKeepsStatusAndSeverityConstraints`, `alertReviewSegmentEndTimeGuardMigrationRequiresEndedSegmentsToHaveEndTime`, `alertReviewSegmentAlertSeverityGuardMigrationRequiresAlertSegmentsToKeepAlertSeverity`, `alertReviewMergeIndexMigrationUsesSameCameraWindowSemantics`, local PostgreSQL tenant-scope migration smoke with open active overlap, adjacent half-open split boundary allowance, `review_item_id` uniqueness/soft-delete allowance, alert-to-detection downgrade rejection, ended reopen rejection, ended-without-end-time rejection, alert-with-detection-severity rejection, same-camera merge index shape, and concurrent ReviewSegment race cases | `J1`, `PG1`, `LiveDevice`; deployed overlap rejection, adjacent non-overlap ingest, and final `ended` transition passed |
| FR-21 | DEVICE VIDEO resolvers/providers, `POST /system/auth/media-permission-check`, VIDEO record/alert routes | `VideoMediaServiceRequestSigner`, `media_authorization_service.py`, signed playback ticket, application/compose/env configuration | `VideoMediaServiceRequestSignerTest`, `MediaPermissionCheckControllerTest`, canonical Python HMAC vectors, signed coverage/export/download/alert-ingest authorization tests, LiveVideo self-test | `J2`, `V2`, `LiveDevice`, `LiveVideo`; real user/service interoperability and playback allow/deny passed |
| FR-22 | VIDEO drift and protected record routes; DEVICE runtime patrol | `RecordSpace`/`RecordFile`, `HttpVideoRecordStorageDriftResolver`, private MinIO policy, DVR `tenants/{tenantId}/...` keys | `HttpVideoResolverStorageDriftTest`, record drift/authorization/local-path tests, `test_dvr_tenant_resolution_fails_closed_when_unconfigured`, `test_dvr_object_names_are_tenant_scoped_and_urls_are_protected`, `test_minio_bucket_policy.py` | `J2`, `V2`, `LiveVideo`; real private-MinIO drift/policy/upload path passed with 10 records and zero issues |
| FR-23 | DEVICE media guards and `POST /system/auth/media-permission-check`; VIDEO record-management authorization | case/item access audits, persisted tenant camera evidence, `application.yaml` action mappings, `V20260706`, `V20260707`, `V20260711__alert_review_media_manage_permission.sql` | `ConfiguredReviewCameraPermissionResolverTest`, `MediaPermissionCheckControllerTest`, media-manage migration/schema tests, VIDEO record management wrong-camera/missing-scope/audit tests | `J2`, `V2`, `PG1`, `LiveDevice`; deployed cross-tenant/action matrix required |
| FR-24 | `GET /runtime-health`, `POST /runtime-reconcile`, `POST /runtime-patrol`, `GET /video/record/space/{spaceId}/videos/drift` | runtime/outbox tables, `HttpVideoRecordStorageDriftResolver`, `V20260709__alert_review_scheduler_activation.sql`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | runtime reconcile/outbox tests, `resolvesSpaceThenConsumesAuthenticatedVideoStorageDriftReport`, `localSchedulerUsesDistributedNonReentrantLockAndPausesQuartzDoubleSource`, `localSchedulerOwnershipMigrationPausesQuartzAndMakesReportEnqueueAtomic`, `alert-review-postgres-migration-smoke.test.mjs` | `J1`, `J2`, `V2`, `PG1`; prove shared Quartz remains paused and retain current local-scheduler runtime runs, including a successful semantic natural tick |
| FR-25 | runtime lock/run/outbox service path | `system_supervision_alert_review_runtime_lock`, `runtime_run`, `runtime_outbox`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | `runtimePatrolProfileTraceAndGapReasonsHardenReviewOperations`, `runtimePatrolAndOutboxJobsCloseScheduledAlertDispatchLoop`, `runtimePatrolRecoversExpiredClusterLockAndReportsPreviousOwner`, `localSchedulerUsesDistributedNonReentrantLockAndPausesQuartzDoubleSource` | `J1`, `PG1`; release clustered lock smoke must also prove no second Quartz execution source |
| FR-26 | missing VIDEO URL resolver branch, UI record reason display | `application.yaml`, workbench record reason labels, runtime gap reason catalog, runtime patrol/outbox `recordGapReasonDetails` | `alertRecordResolverReportsVideoUrlNotConfiguredWhenUrlIsEmpty`, `runtimeHealthReportsVideoUrlNotConfiguredReasonWhenResolverIsUnconfigured`, `runtimeHealthExposesStandardRecordGapReasonCatalogAndNormalizesAliases`, workbench dev-api-mock missing-config UI fallback label check | `J1`, `W1` |
| FR-27 | VIDEO export/manifest/download/object-store path | manifest v2, atomic audit/commit files, private MinIO/S3 `tenants/{tenantId}/exports/{exportId}/...` artifact/staging keys | record-export HMAC/tamper/readback/tenant-key tests, object-storage hash mismatch tests, `test_record_export_minio_smoke.py` | `V1`, `V2`, `MinIO`, `LiveVideo`; real release MinIO persistence, readback, lifecycle, and download passed |
| FR-28 | `/video/record/export/{id}/manifest`, offline verifier | `record_export_manifest_verifier.py`, `.scripts/record-export-manifest-verifier.mjs`, manifest v2 JSON | `test_manifest_verifier_cli_validates_canonical_hash_signature_and_tampering`, `test_manifest_hmac_signature_verifier_checks_files_source_segments_and_clip_params`, `test_manifest_hmac_keyring_verifier_uses_manifest_key_id_after_rotation` | `V1`, `Pkg`; production key escrow smoke required |
| FR-29 | geometry/rule drawer/integration-smoke paths; VIDEO schema bootstrap/migration entry | rule geometry, `bootstrap_schema.py`, `apply_migrations.py`, `yfeieye_video_schema_history`, `V20260711__device_detection_region_rule_fields.sql` | rule geometry/drawer tests, `test_apply_migrations.py`, `test_device_detection_region_persistence.py` | `J1`, `W1`, `V2`, `VMig`, `ProdSmoke`; VMig passed, current real drawer/replay/ProdSmoke remains |
| FR-30 | event projection reconciliation path | `system_supervision_alert_review_item.event_id`, `review_data.eventProjection`, `V20260708_2__alert_review_scheduler_jobs.sql`, `V20260709__alert_review_scheduler_activation.sql`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | `convertedReviewItemCarriesLinkedEventProjection`, `eventReconcileKeepsConvertedItemWhenEventRollbackRequiresRework`, `localSchedulerUsesDistributedNonReentrantLockAndPausesQuartzDoubleSource`, PG scheduler assertions | `J1`, `J2`, `W1`, `PG1`; keep Quartz paused and run current real rollback/rework smoke |
| FR-31 | `/cases/{id}/owner`, `/close`, `/merge`, `/split` | `system_supervision_alert_review_case*` | `reviewCaseLifecycleKeepsOwnerDedupCloseAndAuditTrail`, `reviewCaseMergeAndSplitMoveCluesWithAuditTrail`, `caseLifecycleEndpointsMapHttpRequestsToServiceCommands` | `J1`, `W1` |
| FR-32 | `POST /integration-smoke`, LiveDevice/LiveVideo/LivePlayer scripts, `alert-review-production-smoke.mjs`; streaming download endpoint is a separate deployed probe | sanitized smoke evidence with positive `eventId`, singleton `eventIds`, exact four-key `auditChain`, `evidence_audit_chain_verified`, `review_event_bound_without_task_dispatch`, VIDEO drift/export/manifest evidence, W4/W2 preflights, three native-player seek summaries | integration/live-smoke self-tests, production-smoke empty/mixed/mismatch rejection tests, release-package real-audit-call and identity anchor tests; streaming controller/service tests are listed under FR-12 | `W4`, `W2`, `ProdSmoke`, `Pkg`; final seven-step real release run and exact four-key audit passed |
| FR-33 | `POST /semantic-index/queue`, `GET /semantic-index/evaluation`, `supervisionAlertReviewSemanticIndexJob`, workbench semantic ops panel | `system_supervision_alert_review_semantic_index`, `AlertReviewWorkbench.vue`, `V20260708_2__alert_review_scheduler_jobs.sql`, `V20260713__alert_review_semantic_index_claim.sql`, `V20260713_4__alert_review_local_scheduler_ownership.sql` | semantic queue/reindex/worker backlog tests, workbench semantic ops check, `localSchedulerUsesDistributedNonReentrantLockAndPausesQuartzDoubleSource`, PG scheduler assertions | `J1`, `J2`, `W1`, `PG1`; natural production tick and retry/backlog regression passed; retain recurring backlog alerts |
| FR-34 | AI summary provider path and confirmation endpoint | AI summary `structuredData.aiProvenance`, `ai_summary_generated` case audit metadata, redaction policy version, redacted reviewData/timeline provider context, `ai_summary_confirmed` / `ai_summary_rejected` audit transitions | `aiSummaryCanUseExternalProviderWithCaseTimelineContext`, `aiSummaryRedactsSensitiveReviewDataBeforeProviderAndAuditProvenance`, `aiSummaryRedactsTimelineNotesAndMaterialUrisBeforeProvider`, `aiSummaryRedactionPolicyCanBeOverriddenAndRecordedInProvenance`, `aiSummaryConfirmationKeepsGeneratedProvenanceAndIsIdempotentForSameStatus`, `aiSummaryConfirmationRequiresGeneratedSummaryAudit`, `reviewIntelligenceProviderKeepsStructuredSummaryData`, `alertReviewTablesKeepClueEvidenceAndRegionRuleFields` | `J1`; production catalog signoff before release |
| FR-35 | operations-report APIs, acknowledgement action, `SupervisionAlertReviewOperationsReportJob` | report acknowledgement/outbox/delivery/claim tables, `V20260708_2`-`V20260708_6`, `V20260713_4__alert_review_local_scheduler_ownership.sql`, `uk_supervision_alert_review_runtime_outbox_report` | operations report/delivery/acknowledgement tests, runtime outbox claim/reclaim tests, `localSchedulerOwnershipMigrationPausesQuartzAndMakesReportEnqueueAtomic`, PG scheduler assertions | `J1`, `J2`, `W1`, `PG1`; real recipients/dashboard smoke must retain single-source scheduler and atomic report enqueue evidence |
| FR-36 | workbench browser contract harness and release player smoke | `WEB/scripts/fixtures/alert-review-workbench-e2e/*`, `WEB/scripts/alert-review-workbench-e2e-check.mjs`, `WEB/scripts/alert-review-workbench-e2e-check.test.mjs`, `WEB/scripts/alert-review-playback-contract.test.mjs`, `.scripts/alert-review-player-live-smoke.mjs`, `.scripts/alert-review-production-smoke.mjs` W2 preflight | `alert-review-workbench-e2e-check.mjs --mode=contract`, `--mode=dev-api-mock`, `--mode=dev-api-real-drawer`, invalid-mode runner test, playback offset contract test, player live smoke self-test, production smoke W2 step test | `W1`, `W2`, `W3`, `LivePlayer`, `ProdSmoke`; real release API and all three native-player smokes passed |
| FR-37 | UTF-8 copy guard in workbench contract and release visible-copy scan | workbench SFC, player components, patrol API, VIDEO record/export services, contract script | required Chinese copy guard, shared W4/release-package mojibake catalog including missing-record fallback fragments, `alert-review-visible-copy-scan.mjs`, 2026-07-04 UTF-8 mojibake scan | `W1`, `W2`, `W4`, `Pkg` |
| FR-38 | this traceability register and release package gate | this document, `.scripts/verify-alert-review-release-package.mjs`, `V20260713_4__alert_review_local_scheduler_ownership.sql`, migration/package self-tests | exact FR-01..FR-38 matrix/register count, common unresolved-marker scan, replacement-character/known-mojibake scan, V4 documentation anchor, `verify-alert-review-release-package.test.mjs` | `node .scripts/verify-alert-review-release-package.test.mjs`; then `Pkg --require-clean` only after the final intentional commit |

## Release Packaging Audit

The FR core implementation, production migrations through DEVICE `V20260713_4` and VIDEO `V20260713`, WEB workbench, VIDEO retry hardening, release-gate tooling, and sanitized final smoke report are all part of the final closure package. DEVICE runtime provenance remains the complete fc972b4e JAR/image because no later Java source changed; VIDEO runtime provenance is the final source SHA recorded above. `Pkg --require-clean` is run after the final closure commit so the repository package, rather than a dirty working tree, is the release source of truth.

Historical packaging chronology: the 2026-07-04 audit first reported 75 FR release blockers (67 untracked files and 8 unstaged files), then passed the pre-commit package gate after targeted staging. Subsequent passes added `V20260704` through `V20260713_4`, PG1, workbench/player/visible-copy checks, manifest verification, real VIDEO evidence guards, scheduler ownership, and the current release paths. These historical dirty/staged states are superseded and must not be interpreted as the current worktree state; only a fresh `Pkg --require-clean` from the final commit can describe the next release artifact.

| Package group | Current examples | Current state | Release action |
| --- | --- | --- | --- |
| DEVICE review backend | `SupervisionAlertReviewController.java`, `SupervisionAlertReviewServiceImpl.java`, review DOs, mapper store, resolver/provider classes | Tracked and deployed in the complete fc972b4e JAR/image | Keep backend and its migrations in every later release package |
| DEVICE schema and migration | Existing `V20260701`-`V20260708_10`, plus `V20260709`-`V20260713_4` and schema/PG smoke tests | Tracked, deployed, and applied through V4 | Preserve migration order and the V4 paused-Quartz/local-scheduler ownership invariant |
| DEVICE regression tests | `SupervisionAlertReviewServiceTest.java`, `SupervisionAlertReviewControllerTest.java`, `NotifyReviewRuntimeOutboxPublisherTest.java`, `HttpVideoResolverTest.java`, mapper/schema/permission tests | Tracked and committed | Keep tests with each final production fix so FR regressions remain executable |
| VIDEO evidence package | export/record/authorization/path/drift services, `bootstrap_schema.py`, `apply_migrations.py`, `migrations/V20260711__device_detection_region_rule_fields.sql`, private MinIO/DVR upload changes, and security/storage tests | Tracked; deployed `/app` mount points at the final replayable release tree | Preserve source hash and rerun real export/retry smoke after later changes |
| WEB workbench package | `AlertReviewWorkbench.vue`, `WEB/src/api/supervision/alertReview.ts`, workbench E2E script and fixtures | Tracked, built, deployed, and accepted by all three native-player entrances | Preserve the full type/workbench/player gates |
| Documentation | This FR-01 to FR-38 hardening review document, sanitized production report, and the superseded execution plan | Tracked in the final closure package | Update evidence hashes and artifact provenance for every later release |
| Release gate tooling | `.scripts/verify-alert-review-release-package.mjs`, `.scripts/verify-alert-review-release-package.test.mjs`, `.scripts/record-export-manifest-verifier.mjs`, `.scripts/alert-review-postgres-migration-smoke.mjs`, `.scripts/alert-review-postgres-migration-smoke.test.mjs`, `.scripts/alert-review-device-integration-smoke.mjs`, `.scripts/alert-review-device-integration-smoke.test.mjs`, `.scripts/alert-review-production-smoke.mjs`, `.scripts/alert-review-production-smoke.test.mjs` | Tracked and exercised by the final production run | Run from the packaged root; copied wrappers outside the tree are not valid manifest-verifier evidence |

Release packaging gates:

- `git status --short --untracked-files=all` must show no FR core implementation file as `??` after intentional staging or release packaging.
- `node .scripts/verify-alert-review-release-package.test.mjs` and `node .scripts/alert-review-postgres-migration-smoke.test.mjs` must pass, then `node .scripts/verify-alert-review-release-package.mjs` must pass before commit packaging; run it again with `--require-clean` after commit and before building a HEAD-only release artifact.
- `J1`, `J2`, `V1`, `V2`, `W1`, `W2`, `PG1`, `VMig`, and the opt-in `MinIO` smoke must be rerun from the packaged tree; skipped real-service tests do not satisfy release acceptance.
- Full `pnpm type:check` must exit cleanly before release; the lightweight `tsc --allowJs false` check only proves ordinary TypeScript files.
- The production migration `DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260702__alert_review_frigate_hardening.sql` has local PostgreSQL 16 `btree_gist` smoke coverage for tenant-scoped historical segment overlap; rerun it against the release database shape before deploy.
- The release smoke must use real DEVICE and VIDEO services, with configured alert record, record coverage, export, manifest, download audit, detail-stream seek, and case/event reverse-link paths.
- Do not ship while the worktree-only FR files remain outside the release package, while `pnpm run type:check` fails, or before real VIDEO recording smoke proves playable/exportable evidence.

Manifest verifier and HMAC key rotation:

- Sign new exports with `YFEIEYE_RECORD_EXPORT_HMAC_KEYS='{"2026-q3":"<active-secret>","2026-q2":"<previous-secret>"}'` and `YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID=2026-q3`; the legacy `YFEIEYE_RECORD_EXPORT_HMAC_SECRET` / `YFEIEYE_RECORD_EXPORT_KEY_ID` pair remains supported for non-keyring deployments.
- Keep retired keys in the verifier keyring until every manifest signed by that `keyId` has expired or been archived with an escrowed verifier bundle; the verifier reads the manifest `signature.keyId` rather than the current active key.
- Verify release evidence with `node .scripts/record-export-manifest-verifier.mjs --manifest <manifest.json>` from the packaged repository/release root, not from a directory containing only a copied wrapper, then archive the verifier output with the evidence package download audit.

## Required Release Gates

### P0 gates

- Java regression:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest,SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,HttpVideoResolverTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  and `J2`; a target-module-only pass does not waive a failed reactor build.
- VIDEO regression:
  `V1` and `V2`.
- PostgreSQL migration smoke:
  `node .scripts/alert-review-postgres-migration-smoke.mjs --container=<postgres-container>`
  or `node .scripts/alert-review-postgres-migration-smoke.mjs --database-url=postgresql://.../postgres`
- VIDEO production schema migration:
  run `VMig` against a disposable copy of the release database, then against the release rollout using the normal backup/rollback procedure; verify `yfeieye_video_schema_history` and the `V20260711` checksum.
- Private object storage:
  run `MinIO` without a skip and prove bucket policy is private, DVR keys use `tenants/{tenantId}/{deviceId}/...`, export artifacts use `tenants/{tenantId}/exports/{exportId}/...`, cross-tenant reads fail, and cleanup removes only the owning tenant's objects.
- Workbench contract:
  `corepack pnpm@11.3.0 --dir WEB run test:alert-review-workbench`
- Full frontend type baseline:
  `corepack pnpm@11.3.0 --dir WEB run type:check`; the release gate is the full `vue-tsc --noEmit --skipLibCheck` exit, not a lightweight TypeScript-only check or a Corepack version-guard failure.
- Real evidence download:
  call `GET /system/supervision/alert-review/evidence-export-jobs/{jobNo}/download` with a release token and allowed camera scope for both one-camera and multi-camera jobs; verify `Content-Type`, `Content-Length`, `X-Content-SHA256`, MP4 decodability or ZIP members/provenance, actual file hash, audit reverse lookup, expiry, and temporary-file cleanup.
- Production smoke with real VIDEO URLs:
  `YFEIEYE_DEVICE_AUTH_TOKEN=... YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI=... YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI=... YFEIEYE_REVIEW_PLAYER_SMOKE_URL=... node .scripts/alert-review-production-smoke.mjs --device-base-url=... --tenant-id=... --operator-user-id=... --device-alert-time=... --device-playback-allowed-camera-ids=... --device-playback-denied-camera-ids=... --video-alert-record-query-url=... --video-record-coverage-query-url=... --video-record-base-url=... --video-record-export-url=... --video-device-id=... --video-alert-time=... --video-record-drift-retention-hours=24 --video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs --step-timeout-ms=900000 --player-review-row-text=... --player-expected-seek-time=... --player-expected-record-path-contains=... --player-expected-offset-seconds=... --player-coverage-expected-seek-time=... --player-coverage-expected-record-path-contains=... --player-coverage-expected-offset-seconds=0 --player-case-timeline-expected-seek-time=... --player-case-timeline-expected-record-path-contains=... --player-case-timeline-expected-offset-seconds=0 --evidence-output-file=artifacts/production-smoke.json`
  This must also be accompanied by real user-token/service-HMAC `coverage`/`export`/`download`/`alert_ingest` allow-deny evidence; no local/mock result is accepted.

### P1 gates

- Region drawer saves `inertiaFrames` and `loiteringSeconds` and replay explains them.
- False-positive suggestions cannot apply live rules without accepted approval.
- Case lifecycle service/store/controller/browser contract supports dedupe, merge, split, owner, close, audit, and HTTP command mapping; the earlier real-backend/operator gap is superseded by the final deployed DEVICE/workbench smoke.
- `V20260709` is the historical activation migration; final `V20260713_4` pauses all shared Quartz rows and assigns runtime patrol, outbox, event reconciliation, export, semantic indexing, and operations reports to the explicitly enabled local scheduler under distributed locks. Release still must preserve this single-owner invariant, tune triggers, configure recipients/templates or the final sink, and prove current retry/lock plus rollback/rework/backlog/report execution.
- Semantic-trigger evaluation/confirmation now persists immutable input/version/index-generation evidence end to end; release still must prove the active worker, expired-lease reclaim, and trigger confirmation against a real backlog.

### P2 gates

- ReviewData production backfill is now represented by `V20260705`; release still must rerun `PG1` against the release database shape.
- Production HMAC key custody/escrow and rotation smoke for manifest signing plus DEVICE↔VIDEO service requests.
- Semantic worker production schedule and real backlog smoke.
- Shift/daily report with responsibility, area, camera, and rule dimensions.

## Latest Local Verification

2026-07-03 to 2026-07-09 local checks:
- FR-21/FR-32 VIDEO live-smoke endpoint safety passed after the RED failure showed standalone `LiveVideo` accepted local/mock endpoints and `ProdSmoke --allow-local-endpoints` did not pass that intent to the child VIDEO smoke:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed with `Unknown argument: --allow-local-endpoints` and a missing child-step flag assertion; GREEN reruns passed after `LiveVideo` rejected local/mock/file endpoints by default, allowed explicit co-located local smoke, and `ProdSmoke` propagated `--allow-local-endpoints` to `LiveVideo`.

- FR-21/FR-32 LiveVideo explicit coverage URL gate passed after the RED failure showed missing `record-coverage-query-url` could be hidden by reusing the alert-record URL:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed because `parseArgs()` aliased `recordCoverageQueryUrl` to `alertRecordQueryUrl`; GREEN rerun passed after `LiveVideo` required the dedicated coverage URL or environment variable explicitly, preserving the four-real-VIDEO-URL release gate.

- FR-21/FR-32 release-package coverage URL alias gate passed after the RED failure showed `Pkg` would not block reintroducing `recordCoverageQueryUrl = parsed.alertRecordQueryUrl`:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `true !== false` because the package scanner still accepted an aliased LiveVideo smoke; GREEN rerun passed after `Pkg` rejects that alias with `live_video_coverage_url_alias_present`.

- FR-21/FR-27/FR-32 LiveVideo returned media evidence safety passed after the RED failure showed a real-looking release endpoint could still return mock/local `record_uri`, `download_url`, or `manifest_url` and satisfy the smoke:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed with `Missing expected rejection`; GREEN rerun passed after `LiveVideo` rejected local/mock/file returned record, download, and manifest media evidence in release mode while keeping explicit local-endpoint smoke allowed.

- FR-21/FR-32 LiveVideo local/mock media evidence safety passed after the RED failure showed VIDEO could return only `file_path=/var/lib/...`, `record_uri=C:\...`, `record_uri=//localhost/...`, `record_uri=mock/...`, or `record_uri=data:video/mp4...` and satisfy the release smoke as playable evidence:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing the file-path / absolute-path / protocol-relative / relative-mock / inline-scheme guard; GREEN reruns passed after `LiveVideo` preserved the record URI source, rejected `file_path`, absolute local filesystem evidence, protocol-relative local/mock URLs, relative `mock/...` paths, and inline/opaque `data:` / `blob:` media evidence in release mode, and `Pkg` scanned for those guards.

- FR-21/FR-27/FR-32 LiveVideo download probe media-type safety passed after the RED failure showed a 200 HEAD response with `content-type: application/json` could satisfy the export download probe:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing the download response header guard; GREEN reruns passed after `LiveVideo` required video/octet-stream `content-type`, rejected zero `content-length` when reported, and `Pkg` scanned for the download probe header guard.

- FR-27/FR-28/FR-32 LiveVideo manifest hash format safety passed after the RED failure showed manifest source/output hash fields could be arbitrary non-empty placeholders:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing canonical hash validation; GREEN reruns passed after `LiveVideo` accepted only `sha256:<64 hex>` or bare 64-hex source/output hashes and rejected placeholder source/output hashes.

- FR-27/FR-32 LiveVideo ffmpeg command hash format safety passed after the RED failure showed manifest `ffmpegCommandHash` could be an arbitrary non-empty placeholder:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing the ffmpeg command hash guard; GREEN reruns passed after `LiveVideo` accepted only `sha256:<64 hex>` or bare 64-hex ffmpeg command hashes while preserving root-level and segment-level command hash compatibility.

- FR-27/FR-32 LiveVideo clip window safety passed after the RED failure showed manifest clip windows with `clipEndTime <= clipStartTime` could satisfy reproducibility checks:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing the clip window guard; GREEN reruns passed after `LiveVideo` rejected unparseable or non-increasing segment clip windows while preserving the existing missing-clip-params failure.

- FR-27/FR-32 LiveVideo concat order safety passed after the RED failure showed duplicated segment-level concat indexes could satisfy reproducibility checks:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing the concat-order guard; GREEN reruns passed after `LiveVideo` rejected duplicate or non-integer segment concat order indexes while preserving root-level `concatOrder` compatibility.

- FR-27/FR-32 LiveVideo root concat order safety passed after the RED failure showed duplicated root-level `concatOrder` entries could satisfy reproducibility checks:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing root-level concat order normalization; GREEN reruns passed after `LiveVideo` validated primitive and object root `concatOrder` entries with the same duplicate/non-integer guard used for segment order.

- FR-27/FR-32 LiveVideo root concat order coverage passed after the RED failure showed root-level `concatOrder` could reference a missing segment or omit a real segment and still satisfy reproducibility checks:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing root concat coverage checks; GREEN reruns passed after `LiveVideo` required root `concatOrder` to match the indexed record segment set, with a count fallback when segment indexes are unavailable.

- FR-27/FR-28 LiveVideo manifest signature gate passed after the RED failure showed unsigned live export manifests still satisfied the VIDEO smoke:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed with `Missing expected rejection`; GREEN rerun passed after `LiveVideo` required `signature.algorithm=hmac-sha256`, `keyId`, signature version, and an `hmac-sha256:` signature value before accepting a manifest v2 export.

- FR-27/FR-28 LiveVideo manifest signature value safety passed after the RED failure showed placeholder `hmac-sha256:*` values could satisfy the VIDEO smoke:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing canonical signature value checks; GREEN reruns passed after `LiveVideo` accepted only canonical HMAC-SHA256 hex/base64 values and `Pkg` scanned for that guard.

- FR-28/FR-32 production smoke manifest verifier gate passed after the RED failure showed release `ProdSmoke` could omit the offline verifier script and still pass required option validation:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with an empty `requiredOptionErrors` result, then the package fixture showed `Pkg` would not block removing the verifier-required gate; GREEN reruns passed after `ProdSmoke` required `--video-manifest-verifier-script` in release mode while preserving explicit local-endpoint smoke flexibility.

- FR-28/FR-32/FR-38 production smoke documentation gate passed after the RED failure showed Required Release Gates could omit the manifest verifier while another doc section still mentioned it:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `scanReleaseTraceabilityGate` did not exist, then failed again because the scan searched the whole document instead of the Required Release Gates command block; GREEN rerun passed after `Pkg` checks the ProdSmoke command block itself for the manifest verifier and other real-smoke arguments.

- FR-28/FR-32 standalone LiveVideo manifest verifier gate passed after the RED failure showed release `LiveVideo` could omit the offline verifier script and still pass required option validation:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `requiredOptionErrors()` omitted the missing verifier script, then `Pkg` accepted a LiveVideo smoke without the verifier-required gate; GREEN reruns passed after `LiveVideo` requires `--manifest-verifier-script` / `YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT` in release mode and `Pkg` blocks removing that gate.

- FR-28/FR-32 LiveVideo manifest verifier exit-code safety passed after the RED failure showed an offline verifier could exit non-zero while printing `{"valid":true}` and still satisfy the smoke:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection`, then the package fixture showed `Pkg` would not block removing verifier exit-status checks; GREEN reruns passed after `LiveVideo` rejects any non-zero verifier exit before parsing stdout success JSON.

- FR-28/FR-32 LiveVideo manifest verifier timeout safety passed after the RED failure showed a slow offline verifier could outlive the live-smoke timeout and still satisfy the smoke:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `Missing expected rejection` because `runManifestVerifierScript` did not pass `timeoutMs` to `spawnSync`; a package RED then showed `Pkg` would not block removing that timeout path. GREEN reruns passed after the verifier process uses the same LiveVideo timeout, converts `ETIMEDOUT` into a clear verifier timeout failure, and `Pkg` scans for timeout / `ETIMEDOUT` / timed-out evidence.

- FR-17/FR-32 production smoke player recordPath sanitizer passed after the RED failure showed a signed player `recordPath` could leak query/hash secrets into the sanitized evidence report:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `recordPath` still contained `token=record-path-secret`; a package RED then showed `Pkg` would not block removing the sanitizer. GREEN reruns passed after `ProdSmoke` applies the same URL-secret stripping to `player.recordPath` that it already applied to `currentUrl`, and `Pkg` scans for that sanitizer.

- FR-17/FR-27/FR-32 production smoke LiveVideo exportResult whitelist passed after the RED failure showed export result details could persist signed `downloadUrl` / `manifestUrl`, local output path, temporary storage URL, and debug token values:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.exportResult` copied `localOutputPath`, `temporaryStorageUrl`, and `debugToken`; a package RED then showed `Pkg` would not block raw export-result spreading. GREEN reruns passed after `ProdSmoke` whitelists export evidence to `exportId` plus stripped `downloadUrl` / `manifestUrl`, and `Pkg` scans for that whitelist and blocks raw spread.

- FR-17/FR-23/FR-32 production smoke LiveDevice playback summary whitelist passed after the RED failure showed playback allow/deny evidence could also persist a granted playback URL and debug token:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.playback` copied `grantedPlaybackUrl` and `debugToken`; a package RED then showed `Pkg` would not block raw playback summary copying. GREEN reruns passed after `ProdSmoke` whitelists playback evidence to `grantedDecision`, `deniedDecision`, and `deniedReasons`, and `Pkg` scans for that whitelist.

- FR-17/FR-28/FR-32 production smoke manifestVerification summary whitelist passed after the RED failure showed offline verifier diagnostics could persist a signed manifest URL and debug token:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.manifestVerification` copied `manifestUrl` and `debugToken`; a package RED then showed `Pkg` would not block raw verifier summary copying. GREEN reruns passed after `ProdSmoke` whitelists verifier evidence to `valid`, signature/key booleans, `keyId`, `signatureVersion`, and `violations`, and `Pkg` scans for that whitelist.

- FR-17/FR-28/FR-32 production smoke manifestSignature summary whitelist passed after the RED failure showed signer metadata could persist the signature value, signer URL, and debug token:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.manifestSignature` copied `signatureValue`, `signerUrl`, and `debugToken`; a package RED then showed `Pkg` would not block raw signature summary copying. GREEN reruns passed after `ProdSmoke` whitelists signature evidence to `algorithm`, `keyId`, and `signatureVersion`, and `Pkg` scans for that whitelist.

- FR-17/FR-22/FR-32 production smoke storageDriftSummary whitelist passed after the RED failure showed DB/disk patrol evidence could persist a repair URL, local recording path, signed storage URL, and debug token:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.storageDriftSummary` copied `repairUrl`, `issues[].filePath`, `issues[].storageUrl`, and `debugToken`; a package RED then showed `Pkg` would not block raw drift-summary copying. GREEN reruns passed after `ProdSmoke` whitelists drift evidence to health/count/reason-summary fields, and `Pkg` scans for that whitelist.

- FR-14/FR-17/FR-32 production smoke manifestStorageLifecycle summary whitelist passed after the RED failure showed persisted storage lifecycle evidence could copy a signed storage URL and debug token:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.manifestStorageLifecycle` copied `storageUrl` and `debugToken`; a package RED then showed `Pkg` would not block raw lifecycle summary copying. GREEN reruns passed after `ProdSmoke` whitelists lifecycle evidence to `storageType`, `status`, `expiresAt`, and `exportPackageObjectKey`, and `Pkg` scans for that whitelist.

- FR-27/FR-28/FR-32 LiveVideo manifest signature evidence passed after the RED failure showed accepted manifest signature metadata was not preserved in CLI or production smoke evidence:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed with `manifestSignature` undefined; GREEN reruns passed after `LiveVideo` returned and summarized signer `algorithm`, `keyId`, and signature version, and `ProdSmoke` whitelisted that summary into its sanitized evidence report.

- FR-28 release-package LiveVideo signature evidence gate passed after the RED failure showed `Pkg` did not prevent deleting manifest signature evidence propagation:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `scanLiveVideoEvidenceGate` was missing; GREEN rerun passed after `Pkg` began scanning `LiveVideo` for `validateManifestSignature` / `manifestSignature` / `hmac-sha256` / `keyId` / signature version and `ProdSmoke` for manifest signature summary forwarding.

- FR-21/FR-27/FR-28/FR-32 release-package LiveVideo returned media evidence gate passed after the RED failure showed `Pkg` would not block removal of the new record/download/manifest media evidence rejection:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `live_video_media_evidence_gate_missing` was absent; GREEN rerun passed after `Pkg` began scanning `LiveVideo` for the release media evidence guard and the record/download/manifest labels.

- FR-03 PostgreSQL reviewer status race smoke passed after the RED failure showed `PG1` had no review status/version concurrent race export:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed because `alert-review-postgres-migration-smoke.mjs` did not export `buildConcurrentReviewStatusBootstrapSql`; GREEN rerun passed after `PG1` added stale `version=0` rejection, repeated same-status idempotency, and concurrent `review_status='reviewed'` race summarization with exactly one winning update.

- FR-29 backend rule-save release gate passed after RED showed integration smoke did not prove the release backend rule save path:
  `node .scripts/alert-review-device-integration-smoke.test.mjs`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#integrationSmokeCoversReviewRecordCaseExportAndManifestVerification" -DfailIfNoTests=false test`
  Result: RED first failed because `REQUIRED_CHECKPOINTS` lacked `review_rule_saved` and the service smoke did not save a rule. GREEN reruns passed after `runIntegrationSmoke` saved a `camera-smoke/zone-smoke/person` rule with `inertiaFrames=3` and `loiteringSeconds=20`, and LiveDevice/ProdSmoke began treating `review_rule_saved` as release evidence.

- FR-29/FR-32 parent-level rule semantics evidence passed after RED showed checkpoint-only rule evidence could still satisfy the smoke:
  `node .scripts/alert-review-device-integration-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#integrationSmokeCoversReviewRecordCaseExportAndManifestVerification" -DfailIfNoTests=false test`
  Result: RED first failed because LiveDevice/ProdSmoke accepted `review_rule_saved` without carrying the saved rule parameters. GREEN reruns passed after the backend smoke result exposes `smokeRule`, the admin VO/DEVICE CLI exposes `ruleEvidence`, ProdSmoke preserves it in child summaries, and Pkg blocks removal of `inertiaFrames=3` / `loiteringSeconds=20` parent-level evidence.

- FR-29/FR-32 production smoke ruleEvidence type whitelist passed after RED showed rule semantics evidence could persist object-valued camera metadata and debug tokens:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because `summary.ruleEvidence.cameraId` copied an object containing `rule-evidence-debug-secret`; a package RED then showed `Pkg` would not block wide ruleEvidence copying. GREEN reruns passed after `ProdSmoke` whitelists rule text fields and numeric inertia/loitering fields, and `Pkg` scans for that whitelist.

- FR-29 real `DeviceRegionDrawer` save smoke passed after RED showed the runner did not support a real drawer mode:
  `node scripts/alert-review-workbench-e2e-check.test.mjs`
  `pnpm test:alert-review-workbench:dev-api-real-drawer`
  Result: RED first failed with unsupported `--mode=dev-api-real-drawer`; GREEN reruns passed after the E2E harness stopped aliasing `DeviceRegionDrawer`, mocked only its device-region/model APIs, verified the real drawer called `updateDeviceRegion`, and asserted `saveAlertReviewRule` carried `inertiaFrames=3` and `loiteringSeconds=20`.

- Frontend full type baseline rechecked from the latest packaged HEAD tree:
  `pnpm --pm-on-fail=ignore --dir WEB type:check`
  `pnpm --dir WEB --pm-on-fail=ignore run type:check`
  `pnpm run type:check`
  Result: the earlier `corepack pnpm --dir WEB type:check` attempt was blocked by local pnpm shim version `11.5.2` vs project `packageManager` `11.3.0`; rerunning with the documented pnpm `--pm-on-fail=ignore` escape executed `cross-env NODE_OPTIONS=--max-old-space-size=8192 vue-tsc --noEmit --skipLibCheck` and exited 0 after a long silent run. The latest 2026-07-10 HEAD rerun used `pnpm run type:check`, waited for the silent `vue-tsc` process to finish, and exited 0.

- FR-21/FR-32 LiveVideo runtime coverage URL alias guard passed:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `requiredOptionErrors` accepted identical release `alertRecordQueryUrl` / `recordCoverageQueryUrl`, and `Pkg` did not require the runtime alias guard. GREEN reruns passed after release mode rejects the identical URL pair while `--allow-local-endpoints` still permits local co-located smoke.
- FR-21 DEVICE runtime coverage URL alias guard passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=HttpVideoResolverTest#dockerComposeWiresReviewVideoUrlsToRealVideoRecordEndpointsByDefault" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=HttpVideoResolverTest" -DfailIfNoTests=false test`
  Result: RED first failed because `HttpVideoRecordCoverageResolver` still injected `yfeieye.video.alert-record-query-url` as the default for a missing `record-coverage-query-url`; GREEN reruns passed after the resolver requires its own coverage URL property and only falls back to the configured record-base discovery path when coverage is intentionally empty.
- FR-07/FR-32 LiveVideo non-exportable coverage reason passed:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because coverage with `exportable=false` and `non_exportable_reason=retention_expired` still raised only the generic `record coverage query returned no playable/exportable record segment`; GREEN reruns passed after `LiveVideo` summarizes non-playable coverage reasons and `Pkg` blocks removing that evidence anchor.
- FR-07/FR-32 LiveVideo coverage retain/source classification passed:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `LiveVideo` did not expose `retainMode` / `coverageSource` from a playable coverage segment and `ProdSmoke` did not preserve parent-level coverage classification evidence; GREEN reruns passed after live coverage requires retain/source evidence, emits sanitized `coverageSummary`, and `Pkg` blocks removing the LiveVideo or ProdSmoke coverage classification gates. A later RED showed `LiveVideo` still accepted non-standard `retainMode=temporary` / `coverageSource=custom_ai`; GREEN reruns passed after `LiveVideo` restricted both fields to the standard `continuous` / `motion` / `alert` / `detection` catalog and `Pkg` blocks removing that catalog gate. Another RED showed `retain_mode=Recording` / `coverage_source=all` were rejected instead of being treated like continuous recording; GREEN reruns passed after `LiveVideo` normalized `all` / `record` / `recording` aliases to `continuous` before validation and summary output.
- FR-14/FR-27/FR-32 LiveVideo manifest storage-reference guard passed:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first reached the offline verifier with `file:///tmp/...` export-package storage evidence instead of rejecting the manifest storage reference, and `Pkg` did not require the guard. GREEN reruns passed after release mode validates manifest `export_package` storage references with the same local/mock/inline media evidence rules used for record, download, and manifest URLs.

- FR-32 production smoke orchestrator passed after the RED failure showed no one-command release gate existed for `LiveDevice -> LiveVideo -> LivePlayer`, and now runs `LivePlayer:detail`, `LivePlayer:coverage`, and `LivePlayer:case-timeline` as separate release steps:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `ERR_MODULE_NOT_FOUND` for `alert-review-production-smoke.mjs`; the next failure showed the release package gate did not recognize the new script. GREEN reruns passed and now assert required real-service options, step order, fail-fast behavior, and release package tracking.

- FR-32 deployed DEVICE smoke CLI passed after the RED failure showed no executable root script existed for the release endpoint:
  `node .scripts/alert-review-device-integration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with `ERR_MODULE_NOT_FOUND` for `alert-review-device-integration-smoke.mjs`; the next failure showed the release package gate did not recognize the new smoke script. GREEN reruns passed and now assert auth header, request body, required checkpoints, `manifestValid=true`, `videoExportRequested=true`, and release package tracking.

- FR-17 media access evidence-chain audit passed after the RED failure showed `media_access_granted` / `media_access_denied` case audits did not appear in `getEvidenceAuditTrail`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#evidenceAuditTrailIncludesMediaAccessReadsWithOperatorAndReverseLookup" -DfailIfNoTests=false test`
  Result: RED first failed with `NoSuchElementException` while looking for `media_access_granted`; GREEN rerun passed 1 test, 0 failures, 0 errors, with operator, media action, camera, material URI, denied reasons, eventId, reviewItemId, and hash-chain metadata preserved in the evidence audit trail.

- FR-07/FR-17/FR-23 record coverage read audit passed after RED showed authorized coverage reads only wrote a generic media access audit without returned record URIs:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#allowedRecordCoverageReadAuditsReturnedRecordUrisInEvidenceTrail" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#recordCoverageMergesVideoIndexIntervalsAndMissingGaps+allowedRecordCoverageReadAuditsReturnedRecordUrisInEvidenceTrail+recordCoverageRejectsUnauthorizedCameraMediaAndAuditsDenial+evidenceAuditTrailIncludesMediaAccessReadsWithOperatorAndReverseLookup" -DfailIfNoTests=false test`
  Result: RED first failed because expected coverage `recordUri` evidence lists were `[[null]]`; the first fix exposed `media_not_in_case`, so GREEN now attaches returned coverage record URIs to review evidence after case/camera scope precheck and before auditing. Focused rerun passed 1 test, 0 failures, 0 errors; related regression rerun passed 4 tests, 0 failures, 0 errors.

- FR-16/FR-35 operational reports now include unreviewed backlog count/rate after RED showed the daily report only covered missing-record, export, semantic, and false-positive metrics:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#dailyReportAggregatesOperationalMetricsByUnitAreaCameraAndRule" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#shiftReportSummarizesReviewItemsAndEvidenceGaps+dailyReportAggregatesOperationalMetricsByUnitAreaCameraAndRule" -DfailIfNoTests=false test`
  Result: RED first failed with expected `unreviewedBacklogCount=1` but actual `null`; GREEN rerun passed the focused daily report test and the shift+daily regression pair, with top-level and dimension-level unreviewed backlog metrics.

- FR-16 semantic trigger review context now includes hit explanations, action previews, and pending human confirmation after RED showed the trigger result only exposed action payloads:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#semanticTriggerMatchesIndexedItemsAndReturnsActions" -DfailIfNoTests=false test`
  Result: RED first failed at test compile because `ReviewSemanticTriggerResult` had no `humanConfirmationStatus()`, `hitExplanations()`, or `actionPreviews()` accessors; GREEN rerun passed 1 test, 0 failures, 0 errors.

- FR-16/FR-35 operations reports now return a delivery plan and pending acknowledgement contract after RED showed `ReviewOperationsReport` had no `deliveryPlan()` or `acknowledgement()` accessors:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#shiftReportSummarizesReviewItemsAndEvidenceGaps" -DfailIfNoTests=false test`
  Result: RED first failed at test compile because the report record had no delivery/acknowledgement accessors; GREEN rerun passed 1 test, 0 failures, 0 errors.

- FR-16/FR-35 operations report acknowledgement now persists by report key and is idempotent:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#operationsReportAcknowledgementPersistsForSameReportScopeAndIsIdempotent,SupervisionSchemaSqlTest#alertReviewReportAckMigrationPersistsOperatorAcknowledgement+schemaCreatesOnlySupervisionTables+schemaDefinesIdempotencyAndLookupIndexes" -DfailIfNoTests=false test`
  Result: RED first failed at test compile because `ReviewReportAcknowledgement` and `ReviewReportAcknowledgementCommand` did not exist; GREEN rerun passed 4 tests, 0 failures, 0 errors after the service generated a stable `reportKey`, stored `acknowledgedBy/At/note` through `system_supervision_alert_review_report_ack`, and duplicate acknowledgement returned the original operator/note.

- FR-16/FR-35 operations report acknowledgement is now exposed through release API and the workbench ops panel:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest#operationsReportEndpointsExposeAcknowledgementContractAndUseLoginUser" -DfailIfNoTests=false test`
  `node scripts/alert-review-workbench-e2e-check.mjs --mode=contract`
  Result: RED first failed with `POST /operations-report` returning 404 and the workbench contract missing the typed API, routes, and acknowledgement cell; GREEN rerun passed after the controller mapped report generation/acknowledgement requests through the login user and the workbench showed pending acknowledgement, executed the acknowledgement action, and updated the state to acknowledged.

- FR-16/FR-35 scheduled operations report delivery now enters the runtime outbox:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#operationsReportJobGeneratesScheduledShiftAndDailyReports" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#operationsReportJobGeneratesScheduledShiftAndDailyReports+runtimePatrolAndOutboxJobsCloseScheduledAlertDispatchLoop" -DfailIfNoTests=false test`
  Result: RED first failed because `SupervisionAlertReviewOperationsReportJob` returned no `deliveryOutbox` and queued no `deliver_operations_report` message; GREEN reruns passed 1 test and then the 2-test adjacent runtime outbox suite after scheduled shift/daily reports reused `system_supervision_alert_review_runtime_outbox` with a `review_operations_report` payload, same-reportKey retries returned `deliveryOutbox=0`, and the existing outbox job published it.

- FR-24/FR-35 runtime outbox publishing now requires publisher confirmation before marking messages as published:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeOutboxPublisherFailureMarksMessageFailedForRetry" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeOutboxPublisherFailureMarksMessageFailedForRetry+runtimePatrolAndOutboxJobsCloseScheduledAlertDispatchLoop+operationsReportJobGeneratesScheduledShiftAndDailyReports" -DfailIfNoTests=false test`
  Result: RED first failed at test compile because `ReviewRuntimeOutboxDeliveryResult` did not exist; GREEN reruns passed 1 focused test and then the 3-test adjacent patrol/report/outbox suite after `ReviewRuntimeOutboxPublisher` became an optional service dependency, defaulted to no-op delivery for existing deployments, and failed publisher results now leave the outbox row in `failed` with incremented `retryCount` and `lastError`.

- FR-16/FR-24/FR-35 runtime outbox can now deliver to configured station-notify recipients without fake success:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=NotifyReviewRuntimeOutboxPublisherTest" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=NotifyReviewRuntimeOutboxPublisherTest,SupervisionSchemaSqlTest#alertReviewRuntimeOutboxNotifyMigrationSeedsTemplates" -DfailIfNoTests=false test`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed at test compile because `NotifyReviewRuntimeOutboxPublisher` and `ReviewRuntimeOutboxNotifyProperties` did not exist; GREEN reruns passed after `yfeieye.review.runtime-outbox.notify.enabled` conditionally binds a `NotifySendService` publisher, configured admin recipients receive runtime alert/report templates, missing notify routing returns `runtime_outbox_notify_recipients_not_configured` so the outbox remains retryable, `V20260708_4` seeds the default templates, and PG1/release-package tests track the new migration and regression test.

- FR-16/FR-24/FR-35 runtime outbox station-notify delivery now dedupes per recipient after partial failure:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=NotifyReviewRuntimeOutboxPublisherTest" -DfailIfNoTests=false test`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed at test compile because `ReviewRuntimeOutboxNotifyDeliveryStore` did not exist; GREEN reruns passed after `system_supervision_alert_review_runtime_outbox_delivery` and `V20260708_5` added per-recipient delivery records, `NotifyReviewRuntimeOutboxPublisher` skipped already delivered recipients on retry, and the release/PG smoke gates began tracking the delivery table and recipient idempotency index.

- FR-16/FR-24/FR-35 runtime outbox publishing now claims pending messages before delivery so concurrent workers do not publish the same row twice:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeOutboxPublishingClaimsPendingMessagesBeforeDelivery" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimePatrolAndOutboxJobsCloseScheduledAlertDispatchLoop+runtimeOutboxPublisherFailureMarksMessageFailedForRetry+runtimeOutboxPublishingClaimsPendingMessagesBeforeDelivery,SupervisionSchemaSqlTest#schemaDefinesIdempotencyAndLookupIndexes+alertReviewTablesKeepClueEvidenceAndRegionRuleFields+alertReviewRuntimeOutboxDeliveryMigrationTracksRecipientIdempotency+alertReviewRuntimeOutboxClaimMigrationTracksProcessingOwnership" -DfailIfNoTests=false test`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because a nested publish call scanned the same pending outbox row (`expected 0 but was 1`); GREEN reruns passed after `claimPendingRuntimeOutbox` moved rows to `processing` with a per-run `claimToken`, PostgreSQL claim SQL uses `FOR UPDATE SKIP LOCKED`, `V20260708_6` added `claim_token/claimed_by/claimed_at` plus claim indexes, and PG/release-package gates track the new migration.

- FR-16/FR-24/FR-35 runtime outbox stale claim recovery now reclaims crashed-worker `processing` rows without stealing fresh claims:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeOutboxPublishingReclaimsOnlyStaleProcessingMessages" -DfailIfNoTests=false test`
  Result: RED first failed with `expected: <1> but was: <0>` because `processing` rows were never claimable; GREEN rerun passed after runtime outbox publish supplied a 10-minute reclaim threshold, in-memory and PostgreSQL claim logic accepted stale `processing` rows, fresh `processing` rows remained untouched, and mapper SQL kept `FOR UPDATE SKIP LOCKED`.

- FR-23 evidence package verification operator binding and media scope guard passed after RED failures showed request `operatorUserId` could override the logged-in user and package verification did not carry `allowedCameraIds` into manifest media enforcement:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest#evidencePackageVerificationUsesLoginUserInsteadOfRequestOperator" -DfailIfNoTests=false test`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest,SupervisionAlertReviewControllerTest" -DfailIfNoTests=false test`
  Result: RED first failed with JSON path `$.data.operatorUserId` expected `777` but was `9999`; this slice's RED failed at test compile because `ReviewEvidenceVerificationCommand` lacked `allowedCameraIds`; GREEN rerun passed 92 tests, 0 failures, 0 errors. Verification now resolves the operator through `currentOperatorUserId` and reuses manifest verification's camera scope enforcement for package verification.

- FR-23 configured camera scopes now require real action permission when `yfeieye.review.camera-permission.action-permissions` is configured:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=ConfiguredReviewCameraPermissionResolverTest#configuredScopesRequireRealActionPermissionWhenPermissionServiceIsPresent" -DfailIfNoTests=false test`
  Result: RED first failed at test compile because `ConfiguredReviewCameraPermissionResolver` had no action permission or `PermissionService` gate; GREEN rerun passed 1 test, 0 failures, 0 errors.

- FR-23 action-permission config keys now normalize before gate lookup so casing/whitespace cannot silently bypass the `PermissionService` gate:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=ConfiguredReviewCameraPermissionResolverTest#actionPermissionKeysAreNormalizedBeforeGateLookup" -DfailIfNoTests=false test`
  Result: RED first failed with expected `[]` but returned `[camera-01]`; GREEN rerun passed 1 test, 0 failures, 0 errors.

- FR-23 media action permissions now have release `system_menu` button seeds:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest#alertReviewMediaPermissionMigrationSeedsMenuPermissions" -DfailIfNoTests=false test`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `V20260706__alert_review_media_permissions.sql` was missing and release/package tooling only tracked 14 entries; GREEN reruns passed 1 Java schema test plus both Node self-tests. `PG1` now includes `V20260706`, restores a pre-existing deleted playback permission row without duplicating it, and asserts playback/export/download/manifest permission seeds are present.

- FR-23 default media action-permission mapping now ships with the application config:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `scanMediaPermissionGate()` accepted an `application.yaml` without `snapshot` action-permission mapping; GREEN rerun passed after `application.yaml` defaulted `yfeieye.review.camera-permission.fail-closed=true` and mapped playback/snapshot/coverage/export/download/manifest_verify to the seeded media button permissions, with `Pkg` scanning both migration seeds and application action mappings.

- FR-23 workbench case timeline playback now audits media access before opening the record URI:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed with `case timeline seek expected auditAlertReviewMediaAccess before opening media, got getAlertReviewEvidenceAudit`; GREEN rerun passed and asserts audit payload contains `reviewCaseId`, `reviewItemId`, `cameraId`, `materialUri`, and `actionType=playback` before the `viewVideo` event.

- FR-23 workbench active-case direct playback now audits detail stream, unified timeline coverage, and coverage-list media opens before emitting `viewVideo`:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed with `detail stream seek with active case expected auditAlertReviewMediaAccess before opening media, got createAlertReviewCase`; GREEN rerun passed and asserts the active review case id, review item id, camera id, material URI, and `actionType=playback` before each direct playback event.

- FR-23 pre-case media playback now writes item-level media access audit before opening detail stream, unified timeline, and coverage-list record URIs:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#preCaseMediaAccessAuditRecordsAllowDenyAndCanBeListedByReviewItem,SupervisionSchemaSqlTest#alertReviewTablesKeepClueEvidenceAndRegionRuleFields+alertReviewItemMediaAuditMigrationAllowsPreCaseAuditRows" -DfailIfNoTests=false test`
  `pnpm test:alert-review-workbench:dev-api-mock`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first exposed the missing item-level audit contract and the first GREEN verification caught a missing `assertNull` import; rerun passed 3 Java tests, dev-api-mock now asserts `auditAlertReviewItemMediaAccess` before pre-case playback, and release tooling tracks `V20260707__alert_review_item_media_audit.sql`.

- FR-23 workbench playback now uses backend playback URL preparation instead of audit-only playback:
  `pnpm --pm-on-fail=ignore --dir WEB exec node scripts/alert-review-workbench-e2e-check.mjs --mode=contract`
  `pnpm --pm-on-fail=ignore --dir WEB exec node scripts/alert-review-workbench-e2e-check.mjs --mode=dev-api-mock`
  Result: RED first failed because `prepareAlertReviewPlaybackUrl`, `/playback-url`, and `prepareWorkbenchPlayback` were missing; GREEN reruns passed contract and dev-api-mock, and the fixture now asserts detail stream, unified timeline, coverage, and case timeline playback call backend playback preparation before emitting `viewVideo`.

- FR-23/FR-32 release smoke can now exercise deployed playback-url allow/deny probes:
  `node .scripts/alert-review-device-integration-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed on unknown playback smoke arguments; GREEN reruns passed after `LiveDevice` gained optional allow/deny `GET /items/{reviewItemId}/playback-url` probes and `ProdSmoke` began forwarding playback camera params into that step.

- FR-32 production smoke now requires playback allow/deny camera params:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because `requiredOptionErrors` did not include the playback allow/deny camera arguments; GREEN rerun passed after `ProdSmoke` began treating both as required release-gate inputs while keeping standalone `LiveDevice` flexible.

- FR-23/FR-32 production smoke now requires parent-level playback access evidence:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `ProdSmoke` accepted a `LiveDevice` child summary with only playback checkpoints and no granted/denied decision details, and `Pkg` did not block removing that parent check; GREEN reruns passed after `ProdSmoke` required `grantedDecision=granted`, `deniedDecision=denied`, and `camera_not_allowed` denied-reason evidence, with package scanning guarding those anchors.

- FR-13/FR-32 production smoke now preserves parent-level native player evidence:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `ProdSmoke` accepted a `LivePlayer` child summary without `nativeCurrentTime`, and `Pkg` did not block removing the parent evidence check; GREEN reruns passed after `ProdSmoke` required numeric `nativeCurrentTime` evidence and package scanning guarded the parent evidence anchors in addition to the child `--assert-native-current-time` flag.

- FR-21/FR-32 production smoke now rejects local/mock endpoints by default:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because `ProdSmoke` accepted localhost / 127.0.0.1 / mock / file endpoints; GREEN rerun passed after endpoint validation began rejecting those URLs unless `--allow-local-endpoints` is explicitly provided for co-located real-service smoke.

- FR-32 production smoke now writes a sanitized evidence report:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed on unknown `--evidence-output-file`; GREEN rerun passed after `ProdSmoke` writes success and failure JSON reports with masked token-bearing commands, step exit codes, timestamps, durations, and the final pass/fail status.

- FR-32 production smoke evidence output is now a required release input:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `requiredOptionErrors()` omitted the missing evidence report path, then `Pkg` accepted a production smoke gate without the evidence-output requirement; GREEN reruns passed after `ProdSmoke` requires `--evidence-output-file` / `YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE` and `Pkg` blocks removing that gate.

- FR-32 production smoke step timeout guard passed after a full frontend typecheck attempt stayed running without output long enough to expose a hang risk:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `ProdSmoke` had no `stepTimeoutMs`, no `--step-timeout-ms` / `YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS`, and no timeout evidence in the production smoke report. A later RED showed `stepTimeoutMs` was not passed into child smoke internal waits. GREEN reruns passed after every child step receives a configurable timeout, the default runner passes it to `spawnSync`, timeout exits normalize to code `124`, `LiveDevice` / `LiveVideo` / `LivePlayer:*` receive matching `--timeout-ms`, and `Pkg` blocks removing timeout anchors or child-timeout propagation.

- FR-32/FR-38 production smoke timeout traceability gate passed:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because the Required Release Gates `ProdSmoke` command could omit `--step-timeout-ms` while the release package scanner still returned `ok=true`. GREEN rerun passed after the traceability scanner requires the timeout argument in the production-smoke command block and the release gate command documents `--step-timeout-ms=900000`.

- FR-32/FR-38 full production-smoke command traceability gate passed:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because the Required Release Gates `ProdSmoke` command could omit real DEVICE connection/auth parameters or player offset evidence while `Pkg` still returned `ok=true`. GREEN rerun passed after `Pkg` requires DEVICE base URL/token/operator/playback allow-deny inputs, VIDEO device/time inputs, deployed player workbench/row inputs, and each detail/coverage/case-timeline record-path plus offset assertion in the documented release command.

- FR-32 production smoke evidence now aggregates child-smoke summaries:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because `LiveVideo` stdout JSON was not parsed into the evidence report; GREEN rerun passed after `ProdSmoke` captures child stdout, echoes it to the console, and stores only sanitized summary fields such as checkpoints, `storageDriftSummary`, export result, playback, player, and status.

- FR-32 production smoke now rejects child-smoke false positives without required evidence summaries:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because all child commands could return exit code 0 with no JSON evidence and `ProdSmoke` still passed; GREEN rerun passed after `ProdSmoke` began requiring `LiveDevice` ingest/rule/coverage/case/export/manifest/download/playback checkpoints, `LiveVideo` coverage/drift/export/download/manifest evidence, and each `LivePlayer:*` click/seek/record/offset proof before marking a production smoke step passed.

- FR-13/FR-32 production smoke now rejects mock player media evidence in release mode:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because `LivePlayer` could report `mock://record/...` while the release `ProdSmoke` still passed; GREEN rerun passed after player evidence validation began rejecting local/mock `recordPath` / `currentUrl` values unless local endpoint mode is explicitly enabled.

- FR-13 standalone LivePlayer smoke now rejects local/mock endpoints in release mode:
  `node .scripts/alert-review-player-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because standalone `LivePlayer` accepted localhost workbench URLs and `mock://record/...` media results, and `ProdSmoke --allow-local-endpoints` was only passed to `LiveVideo`; GREEN reruns passed after `LivePlayer` gained `--allow-local-endpoints`, default release-mode rejection for local/mock workbench/media evidence, and `ProdSmoke` forwarded the same explicit local allowance to all player child smokes.

- FR-17/FR-32 production smoke evidence now preserves DEVICE audit identifiers:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because parsed DEVICE child summaries dropped `reviewItemId`, `reviewCaseId`, `exportJobNo`, `manifestValid`, and `videoExportRequested`; GREEN rerun passed after the production evidence summary whitelist included those fields without persisting raw stdout.

- FR-17/FR-32 production smoke evidence now preserves audit-chain reverse lookup keys:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because the sanitized production evidence report had no `summary.auditChain`; GREEN reruns passed after `ProdSmoke` emits `action=export_downloaded`, `reviewCaseId`, `reviewItemIds`, `eventIds`, and `exportJobNo`, and `Pkg` blocks removing that gate.

- FR-17/FR-32 production smoke auditChain scalar whitelist passed after RED showed child audit metadata could persist object-valued reverse-lookup IDs and debug tokens:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed with a deep-equal mismatch because child `auditChain.reviewCaseId`, `reviewItemIds`, and `eventIds` copied objects carrying debug tokens; a package RED then showed `Pkg` would not block removal of scalar normalization. GREEN reruns passed after `ProdSmoke` keeps only string/number audit IDs and falls back to top-level scalar evidence when child audit IDs are object-valued.

- FR-17/FR-32 production smoke exact four-key audit acceptance passed after RED showed empty event arrays and mismatched item/case/event/job identities could satisfy the parent smoke:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first accepted explicit `eventIds=[]`, omitted top-level `eventId`, missing `evidence_audit_chain_verified`, and each mismatched audit key; a security RED also showed object-valued event IDs could leak child debug metadata into a failed evidence report. GREEN reruns passed after `ProdSmoke` preserves only positive numeric/decimal-string `eventId` and `eventIds`, requires non-empty singleton top-level/audit event arrays, enforces exact `reviewItemId`/`reviewCaseId`/`eventId`/`exportJobNo` identity, does not let fallback hide explicit empty arrays, strips object-valued event IDs before evidence persistence, and `Pkg` anchors every release gate.

- FR-13/FR-32 production smoke evidence now preserves deployed player seek proof:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because parsed `LivePlayer` stdout with `seekTime`, `recordPath`, `currentUrl`, `playbackOffsetSeconds`, and `nativeCurrentTime` produced no evidence step summary, then failed again because signed player URL query strings could leak into evidence; GREEN rerun passed after `ProdSmoke` stores those fields under `summary.player`, strips `currentUrl` query/hash secrets, and avoids persisting raw stdout.

- FR-13/FR-32 production smoke now gates all three real player seek entrances:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed on unknown `--player-coverage-*` args and then on the old single `LivePlayer` step assertion; GREEN rerun passed after `ProdSmoke` began requiring coverage and case-timeline expected seek/record/offset inputs, accepting zero-second player offsets, and running `LivePlayer:detail`, `LivePlayer:coverage`, and `LivePlayer:case-timeline` with separate sanitized evidence summaries.

- FR-13/FR-32 player native-current-time gate passed:
  `node .scripts/alert-review-player-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `LivePlayer --assert-native-current-time` accepted missing `nativeCurrentTime=null` when the expected offset was 0, `ProdSmoke` did not pass the native-current-time assertion flag to player child smokes, and `Pkg` did not block removing that flag; GREEN reruns passed after LivePlayer requires finite native video currentTime evidence, every ProdSmoke player child command includes `--assert-native-current-time`, and `Pkg` blocks deleting the flag.

- FR-13/FR-17/FR-32 production smoke player evidence now carries review/action context:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because `summary.player` only stored actual player fields and omitted entry, action id, review row, `reviewItemId`, `reviewCaseId`, and expected seek metadata; GREEN rerun passed after `ProdSmoke` merges step-level player evidence context with the child smoke result while keeping signed URLs stripped and raw stdout out of the evidence report.

- FR-17/FR-32 production smoke player evidence now sanitizes wrapped player payloads:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because a child smoke JSON shaped as `{ "player": { ... } }` copied signed `currentUrl` query strings and extra debug fields into evidence; GREEN rerun passed after wrapped player payloads began using the same field whitelist and URL stripping as bare `LivePlayer` output.

- FR-17/FR-32 production smoke step commands now strip signed URL query strings:
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed because the evidence command could preserve a signed workbench URL query/hash from `--workbench-url`; GREEN rerun passed after `formatStepCommand` kept `--token=***` and stripped query/hash from URL-valued args while leaving endpoint paths visible.

- FR-22/FR-32 live VIDEO smoke now requires a healthy recording storage drift patrol:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  Result: RED first failed on unknown drift retention args; GREEN reruns passed after `LiveVideo` began calling `/space/{spaceId}/videos/drift` with `retention_hours`, rejecting unhealthy drift reports, and `ProdSmoke` began requiring and forwarding `--video-record-drift-retention-hours`.

- FR-22 live VIDEO smoke output now includes compact drift evidence:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed because `summarizeCliResult` was not exported; GREEN rerun passed after the CLI JSON began including `storageDriftSummary` with health, checked record count, issue count, and issue reason counts.

- FR-36/W2 frontend typecheck gate is now protected by release tooling:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `pnpm --pm-on-fail=ignore --dir WEB type:check`
  Result: RED first failed because `scanWebTypecheckGate` was missing; GREEN reruns passed, `Pkg` now rejects a missing or weakened `WEB/package.json` `type:check` script, and full `vue-tsc --noEmit --skipLibCheck` exited 0 after a long silent run.

- FR-25 runtime stale-lock recovery passed after the RED failure showed no lock acquisition detail contract existed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimePatrolRecoversExpiredClusterLockAndReportsPreviousOwner" -DfailIfNoTests=false test`
  Result: RED first failed at test compile for missing `ReviewRuntimeLockAcquisition`; GREEN rerun passed 1 test, 0 failures, 0 errors, and MapperStore now uses a conditional stale-lock takeover update.

- FR-33 semantic index worker retry/progress slice passed after the RED failure showed no worker contract existed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#semanticIndexWorkerRetriesFailuresAndReportsBacklogProgress" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors; includes failed-item retry, backlog alarm, index version, rebuild progress, and `supervisionAlertReviewSemanticIndexJob` smoke.

- FR-10/FR-33 workbench semantic ops status passed after RED showed the ops panel had no stable semantic backlog/progress cell:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed with `missing stable selector alert-review-ops-semantic`; GREEN rerun passed and asserts `evaluateAlertReviewSemanticIndex`, `critical`, `50%`, and `stale 1 / failed 1` are visible in the workbench.

- FR-09 workbench topology candidate explanation and add-to-case flow passed after RED showed no stable candidate card/action existed:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed with missing `alert-review-case-candidate` and `alert-review-candidate-add`; GREEN rerun passed and asserts `RV-20260702-002`, `topology area yard-east`, `adjacent cam-east-gate -> cam-yard-east`, `shared object person-1`, and `addAlertReviewItemToCase`.

- FR-09 configured camera topology candidate matching passed after RED showed no service-side topology resolver contract existed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewCaseCandidatesUseConfiguredCameraTopologyWhenReviewDataHasNoTopology" -DfailIfNoTests=false test`
  Result: RED first failed at test compile for missing `ReviewCameraTopology` / `ReviewCameraTopologyResolver`; GREEN rerun passed 1 test and proves configured regulatory area plus adjacent camera topology can match candidates even when reviewData has no topology fields.

- FR-09 configured topology candidate explanation reached the backend payload and workbench cards after RED showed matches had no `reviewData.caseCandidateMatch` contract:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewCaseCandidatesUseConfiguredCameraTopologyWhenReviewDataHasNoTopology" -DfailIfNoTests=false test`
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed with missing backend `caseCandidateMatch` and the workbench timing out waiting for `topology area yard-east`; GREEN reruns passed, with candidate payload source `configured_camera_topology`, `regulatoryArea`, and adjacent-camera explanation rendered from nested metadata.

- FR-28 manifest HMAC keyring rotation and release verifier artifact passed after RED failures showed keyring exports still signed as plain sha256 and the release package gate did not recognize the wrapper:
  `python -m pytest test_record_export.py::TestRecordExportService::test_manifest_hmac_keyring_verifier_uses_manifest_key_id_after_rotation -q`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: 1 Python test passed; `alert review release package verifier tests OK`.

- FR-01 ingest identity DB hardening and FR-20 ReviewSegment SQL constraints passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 10 tests, 0 failures, 0 errors.
- FR-01/FR-20 PostgreSQL migration smoke script passed after the RED failure showed the reusable smoke runner was missing:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed with `ERR_MODULE_NOT_FOUND`; GREEN rerun passed and confirmed migration file list, bootstrap/assertion SQL, argument parsing, and release package tracking.
- FR-01 ingest identity PostgreSQL smoke passed against a temporary `postgres:16` container:
  `node .scripts/alert-review-postgres-migration-smoke.mjs --container=yfeieye-fr-review-pg-race-70608 --database=yfeieye_alert_review_migration_smoke`
  Result: applied `V20260702` + `V20260704`; verified tenant-scoped historical source-alert backfill, duplicate identity unique rejection, cross-process duplicate identity racing with exactly one successful insert, and same-tenant ReviewSegment overlap rejection; temporary container removed.
- FR release package verifier tests passed after adding the PostgreSQL smoke tooling to tracked release paths:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: pass.
- FR-20 PostgreSQL migration smoke passed after the RED failure showed `V20260702` rejected cross-tenant historical segment overlap before `tenant_id` existed on segment exclusion:
  RED: temporary `postgres:16` rejected current `V20260702` with `could not create exclusion constraint "ex_supervision_alert_review_segment_camera_time"` for two different tenants on the same camera/time window.
  GREEN: temporary `postgres:16` applied `V20260702` + `V20260704` and verified tenant backfill, cross-tenant overlap migration, adjacent half-open segments allowed, same-tenant overlap rejected, invalid `segment_status` rejected, and duplicate active `review_item_id` rejected.
- FR-20 schema and mapper focused regression passed after tenant-scoped production migration hardening:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest,SupervisionAlertReviewMapperStoreTest" -DfailIfNoTests=false test`
  Result: 16 tests, 0 failures, 0 errors.
- FR-20 MapperStore segment fail-fast validation passed after the RED failure showed missing cameraId and invalid segment status reached insert without application-level rejection:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewMapperStoreTest" -DfailIfNoTests=false test`
  Result: RED first failed 2 of 6 tests; GREEN rerun passed 6 tests, 0 failures, 0 errors.
- FR-01/FR-20 service and mapper regression passed after identity table wiring:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest" -DfailIfNoTests=false test`
  Result: 78 tests, 0 failures, 0 errors.
- FR-03 review status idempotency and conflict rejection passed after the RED failure showed repeated review clicks refreshed `reviewedAt`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewStatusActionsAreIdempotentAndRejectConflictingReviewerActions" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- FR-03 MapperStore DB conditional update guard passed after the RED failure showed stale 0-row updates were treated as success:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewMapperStoreTest#updateReviewStatusRejectsConcurrentStatusConflict" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- FR-08 false-positive suggestion safety metadata passed after the RED failure showed missing `minimumSampleCount`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#falsePositiveActionMarksStatusAndCreatesRuleSuggestion" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- FR-08 workbench now surfaces false-positive rule suggestion safety metadata before approval actions:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed waiting for `rule suggestion sample safety summary`; GREEN rerun passed after the workbench displayed `sample`, `risk`, `impact`, `hits`, and `possible missed` rows from the rule suggestion payload.
- FR-08 low-sample rule suggestions are now blocked before approval/application:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#lowSampleRuleSuggestionCannotBeAcceptedBeforeMoreReviewSamples+falsePositiveRuleSuggestionAppliesRuleConfigOnlyAfterApprovalAndCanRollback+ruleSuggestionSafeApplyRequiresApprovalAndStoresShadowEvaluation" -DfailIfNoTests=false test`
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first showed a 1/3 sample suggestion could be accepted and the workbench still rendered `accept`; GREEN reruns passed after the service refreshed same-scope sample counts, blocked `accepted` / `applied` when `sampleRequirementMet=false`, and the workbench hid accept/apply until the sample gate is met.
- FR-18 workbench now explains rule replay evidence before operators apply rule changes:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed waiting for `rule replay rule version`; GREEN rerun passed after the replay panel displayed rule version, sample window, hit comparison, and false-negative estimate from the replay report payload.
- FR-34 AI summary provenance passed after the RED failure showed missing `aiProvenance`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#aiSummaryCanUseExternalProviderWithCaseTimelineContext" -DfailIfNoTests=false test`
  Result: RED first failed with missing provenance; GREEN rerun passed 1 test, 0 failures, 0 errors.
- FR-34 AI summary provenance persistence passed after RED failures showed missing `case_audit.metadata` and missing `ai_summary_generated` audit trace:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest#alertReviewTablesKeepClueEvidenceAndRegionRuleFields,SupervisionAlertReviewServiceTest#aiSummaryCanUseExternalProviderWithCaseTimelineContext" -DfailIfNoTests=false test`
  Result: RED first failed 2 tests; GREEN rerun passed 2 tests, 0 failures, 0 errors.
- FR-34 AI summary human confirmation passed after the RED failure showed missing confirmation API records:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#aiSummaryConfirmationKeepsGeneratedProvenanceAndIsIdempotentForSameStatus+aiSummaryConfirmationRequiresGeneratedSummaryAudit" -DfailIfNoTests=false test`
  Result: RED first failed at test compile for missing confirmation API; GREEN rerun passed 2 tests, 0 failures, 0 errors.
- FR-34 AI summary PII redaction passed after the RED failure showed provider requests still received raw `reviewData.motion.personName`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#aiSummaryRedactsSensitiveReviewDataBeforeProviderAndAuditProvenance" -DfailIfNoTests=false test`
  Result: RED first failed 1 test because provider saw `Resident Alice`; GREEN rerun passed 1 test, 0 failures, 0 errors.
- FR-34 AI summary timeline/material redaction passed after the RED failure showed provider timeline still contained raw phone/id/name values:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#aiSummaryRedactsTimelineNotesAndMaterialUrisBeforeProvider" -DfailIfNoTests=false test`
  Result: RED first failed 1 test because provider timeline still exposed raw values; GREEN rerun passed 1 test, 0 failures, 0 errors.
- FR-34 AI summary redaction policy passed after the RED failure showed the production policy type was missing:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#aiSummaryRedactionPolicyCanBeOverriddenAndRecordedInProvenance" -DfailIfNoTests=false test`
  Result: RED first failed at test compile for missing `ReviewAiSummaryRedactionPolicy`; GREEN rerun passed 1 test, 0 failures, 0 errors.
- FR-34 AI summary provider, PII redaction, timeline/material redaction, policy version, and human confirmation focused suite passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#aiSummaryCanUseExternalProviderWithCaseTimelineContext+aiSummaryRedactsSensitiveReviewDataBeforeProviderAndAuditProvenance+aiSummaryRedactsTimelineNotesAndMaterialUrisBeforeProvider+aiSummaryRedactionPolicyCanBeOverriddenAndRecordedInProvenance+aiSummaryConfirmationKeepsGeneratedProvenanceAndIsIdempotentForSameStatus+aiSummaryConfirmationRequiresGeneratedSummaryAudit" -DfailIfNoTests=false test`
  Result: 6 tests, 0 failures, 0 errors.
- FR-35 daily/shift operational report dimensions passed after the RED failure showed missing report metrics:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#shiftReportSummarizesReviewItemsAndEvidenceGaps+dailyReportAggregatesOperationalMetricsByUnitAreaCameraAndRule" -DfailIfNoTests=false test`
  Result: RED first failed because `missingRecordCount` was absent; GREEN focused suite passed 2 tests, 0 failures, 0 errors.
- FR-22/FR-24/FR-26 runtime record gap reason catalog passed after the RED failure showed missing `recordGapReasonCatalog()` on `ReviewRuntimeHealthReport`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeHealthExposesStandardRecordGapReasonCatalogAndNormalizesAliases" -DfailIfNoTests=false test`
  Result: RED first failed at test compile; GREEN rerun passed 1 test, 0 failures, 0 errors.
- FR-26 runtime patrol missing-config reason metadata passed after the RED failure showed patrol/outbox metadata lacked `recordGapReasons`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeHealthReportsVideoUrlNotConfiguredReasonWhenResolverIsUnconfigured" -DfailIfNoTests=false test`
  Result: RED first failed because `patrol.metadata().get("recordGapReasons")` was null; GREEN rerun passed after metadata included `recordGapReasons` and `recordGapReasonDetails` with code/category/label/retryable evidence.
- FR-22/FR-24/FR-26 focused runtime health gap reason regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimePatrolSurfacesRecordStorageDriftReasonsFromStorageSync+runtimeHealthReportsVideoUrlNotConfiguredReasonWhenResolverIsUnconfigured+runtimeHealthExposesStandardRecordGapReasonCatalogAndNormalizesAliases" -DfailIfNoTests=false test`
  Result: 3 tests, 0 failures, 0 errors.
- FR-07/FR-22 VIDEO availability gap reason catalog passed:
  `python -m unittest test_record_availability` from `VIDEO/`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimeHealthExposesStandardRecordGapReasonCatalogAndNormalizesAliases" -DfailIfNoTests=false test`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because VIDEO returned `gap_reason_category=unknown` for `file_missing`, `probe_failed`, `disk_full`, `cache_flush_failed`, and `video_url_not_configured`, and leaked legacy `file_expired` instead of canonical `retention_expired`; a later RED showed `file-expired`, `FILE EXPIRED`, and `VIDEO URL NOT CONFIGURED` still leaked or fell back before lookup; GREEN reruns passed after `_normalize_gap_reason` classified those standard reasons as `filesystem`, `probe`, `storage`, `cache`, and `configuration`, and VIDEO/DEVICE normalize reason tokens to lowercase snake_case before alias/catalog lookup, with `Pkg` blocking removal of the VIDEO record gap reason catalog and token normalizer anchors.
- FR-26 workbench missing-config fallback copy passed:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed waiting for `manual record evidence fallback label`; GREEN rerun passed after `video_url_not_configured` rendered as `缺录像/待手动补证` with `VIDEO URL 未配置`.
- FR-01/FR-03/FR-20 service, mapper, and schema regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 90 tests, 0 failures, 0 errors.
- DEVICE review regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest,SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,HttpVideoResolverTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 110 tests, 0 failures, 0 errors.
- WEB lightweight TypeScript gate passed after adding the runtime gap reason catalog API type:
  `pnpm exec tsc --noEmit --skipLibCheck --pretty false --allowJs false`
  Result: exit code 0, no diagnostics.
- VIDEO regression passed:
  `python -m pytest test_record_export.py test_record_availability.py -q`
  Result: 21 passed.
- Workbench E2E runner mode validation passed:
  `pnpm test:alert-review-workbench:runner`
  Result: `alert-review-workbench-e2e-check.test OK`; invalid `--mode` now fails before a false-positive browser pass.
- Workbench static contract mode passed:
  `pnpm test:alert-review-workbench:contract`
  Result: `Alert review workbench E2E contract OK`.
- Workbench dev-server/API mock browser mode passed:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: `Alert review workbench E2E dev-api-mock OK`; includes exact detail stream / coverage / case timeline `seek_time` and `record_path` player-event assertions, region drawer `saveAlertReviewRule` payload assertions for `inertiaFrames` and `loiteringSeconds`, owner, close, merge, split browser actions, UTF-8 Chinese copy guard, and the integration smoke `evidence_download_audited` checkpoint.
- Workbench playback seek contract passed after the RED failure showed the real playback bridge did not preserve offset metadata:
  `pnpm test:alert-review-playback`
  Result: `alert review playback contract tests OK`; `AlertReviewWorkbench` now emits `record_start_time`, `playAlertRecordInModal` carries `seek_time` and `playback_offset_seconds`, and `DialogPlayer` uses native mp4 VOD seeking when a positive offset is available.
- Release player live smoke self-test passed after the RED failure showed no deployed-page player seek smoke runner:
  `node .scripts/alert-review-player-live-smoke.test.mjs`
  Result: `alert review player live smoke tests OK`; the CLI refuses to pass without a deployed workbench URL, review row, expected seek time, expected record path fragment, and expected offset seconds.
- Release visible-copy scan passed after the RED failure showed no reusable release scanner for player/patrol/VIDEO copy:
  `node .scripts/alert-review-visible-copy-scan.mjs`
  Result: `Alert review visible copy scan OK: 10 file(s) checked.`
- FR-29/FR-37 real drawer fixture visible-copy coverage passed:
  `node .scripts/alert-review-visible-copy-scan.test.mjs`
  `node .scripts/alert-review-visible-copy-scan.mjs`
  Result: RED first failed because `WEB/scripts/fixtures/alert-review-workbench-e2e/main.ts` was not part of W4 target coverage; GREEN reruns passed after the real `DeviceRegionDrawer` E2E fixture joined the default scan target list while its UTF-8 Chinese assertions remained clean.
- Patrol API mojibake guard passed after RED showed the visible-copy scanner and release verifier skipped the patrol mojibake pattern:
  `node .scripts/alert-review-visible-copy-scan.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `node .scripts/alert-review-visible-copy-scan.mjs`
  Result: RED first failed because the scanner returned `ok=true` for the patrol mojibake fixture, then the release verifier returned `ok=true` because `WEB/src/api/device/patrol.ts` was not part of the FR release path rules; GREEN reruns passed after both guards recognized the pattern and the release package verifier tracked the patrol API file.

- FR-37 release package text-quality gate now reuses the W4 visible-copy mojibake catalog:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `node .scripts/alert-review-visible-copy-scan.test.mjs`
  `node .scripts/alert-review-visible-copy-scan.mjs`
  Result: RED first failed because `scanTextQuality` returned `ok=true` for the W4-visible `\u935b\u5a45` mojibake fixture; GREEN reruns passed after `Pkg` reused `VISIBLE_COPY_MOJIBAKE_PATTERNS` while preserving its extra historical release-text patterns.

- FR-37 release text-quality gate no longer carries raw mojibake fixtures in source:
  `node .scripts/verify-alert-review-release-package.mjs --require-clean`
  `node scripts/alert-review-workbench-e2e-check.test.mjs` from `WEB`
  Result: RED first failed because `Pkg --require-clean` found a raw mojibake fragment in `WEB/scripts/alert-review-workbench-e2e-check.mjs`; GREEN reruns passed after the workbench E2E mojibake fragment catalog and test fixtures used escaped codepoints while preserving runtime detection.

- FR-26/FR-37 missing-record fallback mojibake guard passed:
  `node .scripts/alert-review-visible-copy-scan.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because both W4 and `Pkg` returned `ok=true` for wrong-decoded `缺录像/待手动补证` and `VIDEO URL 未配置` fragments; GREEN reruns passed after the shared mojibake catalog recognized those codepoint patterns.

- Workbench all-mode package gate passed:
  `pnpm test:alert-review-workbench`
  Result: `Alert review workbench E2E all OK`.
- Frontend barrel-import hardening recheck passed:
  `pnpm exec tsc --noEmit --skipLibCheck --pretty false --allowJs false`
  Result: exit 0.
- Workbench all-mode package gate rechecked after frontend barrel-import hardening:
  `pnpm test:alert-review-workbench`
  Result: `Alert review workbench E2E all OK`.
- Full Vue SFC type gate passed after frontend barrel-import and non-workbench type hardening:
  `pnpm run type:check`
  Result: exit code 0, no diagnostics.
- Workbench seek/mode changes lightweight TypeScript recheck passed:
  `pnpm exec tsc --noEmit --skipLibCheck --pretty false --allowJs false`
  Result: exit code 0, no diagnostics.
- Review VIDEO docker integration config and resolver regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=HttpVideoResolverTest" -DfailIfNoTests=false test`
  Result: 12 tests, 0 failures, 0 errors.
- ReviewSegment ended-extension overlap guard passed after the RED failure showed an already ended segment could be extended over a later active same-camera segment:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewSegmentLifecycleRejectsEndedExtensionOverlappingLaterActiveSegment" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- ReviewSegment lifecycle neighbor regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#endedAlertReviewSegmentSplitsLateDetectionIntoNewSegment+endingMergedSegmentTruncatesBeforeLateDetectionSplitsNewActiveSegment+reviewSegmentLifecycleRejectsEndBeforeSegmentStart+reviewSegmentLifecycleRejectsInvalidStateAndReopenAfterEnded+reviewSegmentLifecycleRejectsEndedExtensionOverlappingLaterActiveSegment+reviewSegmentAlertStateDoesNotDowngradeWhenLaterDetectionHeartbeatArrives" -DfailIfNoTests=false test`
  Result: 6 tests, 0 failures, 0 errors.
- Alert review service full regression passed after the service-level overlap guard:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest" -DfailIfNoTests=false test`
  Result: 88 tests, 0 failures, 0 errors.
- Mapper-store open interval persistence passed after the RED failure showed non-ended ReviewSegment `end_time` was persisted as the start time instead of `NULL`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewMapperStoreTest#createPersistsNonEndedReviewSegmentAsOpenInterval" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Mapper-store deleted overlap alignment passed after the RED failure showed an already deleted ReviewSegment row returned by an overlap probe could still block a new same-camera segment, diverging from the PostgreSQL `WHERE deleted = FALSE` exclusion constraint:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewMapperStoreTest#createIgnoresDeletedOverlappingReviewSegmentBeforeSegmentInsert" -DfailIfNoTests=false test`

- ReviewSegment mapper soft-delete alignment passed after the RED failure showed default review-item lookup and camera/time overlap queries did not include `deleted=false`, diverging from the PostgreSQL partial unique/exclusion constraints:
  `mvn -pl iot-system/iot-system-biz -am -Dtest=SupervisionAlertReviewSegmentMapperTest -DfailIfNoTests=false test`
  Result: RED first failed with `overlapping review segment for camera camera-01: 205`; GREEN rerun passed after mapper-store skipped `deleted=true` overlap rows before throwing.
- ReviewSegment production SMALLINT parameter binding passed after the deployed smoke exposed `operator does not exist: smallint = boolean` in `selectLatestOpen` following `V20260708_10`:
  `mvn -pl iot-system-biz -am "-Dtest=SupervisionAlertReviewSegmentMapperTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  Result: RED failed 3 of 4 mapper tests while all explicit soft-delete filters still bound Java `false`; GREEN passed 4 of 4 after `selectByReviewItemId`, `selectOverlapping`, and `selectLatestOpen` bind numeric `0`, matching the migrated PostgreSQL column.
- Supervision/review PostgreSQL ID generation contract passed after deployed ingest exposed an explicit `id=NULL` insert despite each table owning a `BIGSERIAL` sequence:
  `mvn -pl iot-system-biz -am "-Dtest=SupervisionPersistenceIdContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  Result: RED failed on the first entity because none of the 18 mapped supervision/review DOs declared a key sequence or table ID under PostgreSQL `INPUT` mode; GREEN passed after every entity declared `@TableId` and an `@KeySequence` equal to `<table>_id_seq`.
- PostgreSQL migration smoke self-test passed after the RED failure showed the smoke SQL did not cover open active same-camera overlap rejection:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: `alert review postgres migration smoke tests OK`.
- Mapper-store full regression passed after open interval persistence:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewMapperStoreTest" -DfailIfNoTests=false test`
  Result: 7 tests, 0 failures, 0 errors.
- PostgreSQL migration smoke self-test passed after adding concurrent ReviewSegment overlap race SQL/result summarization:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: `alert review postgres migration smoke tests OK`.
- PostgreSQL migration smoke self-test passed after the RED failure showed the smoke SQL did not prove the `review_item_id` partial unique index:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed because `buildPostMigrationAssertionSql()` lacked duplicate active/deleted duplicate ReviewSegment `review_item_id` checks; GREEN rerun passed after PG1 attempts an active duplicate insert expecting `unique_violation` and then proves a `deleted=true` duplicate row is allowed by the partial index.
- PostgreSQL migration smoke self-test passed after the RED failure showed the smoke SQL only checked ended reopen and did not prove alert-to-detection downgrade rejection:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed because `buildPostMigrationAssertionSql()` lacked `expected alert ReviewSegment downgrade to detection to be rejected`; GREEN rerun passed after PG1 upgrades a segment to `alert`, attempts to downgrade it to `detection`, and expects the `V20260708` trigger to raise `check_violation`.
- PostgreSQL migration smoke self-test passed after the RED failure showed the smoke SQL rejected overlaps but did not prove adjacent half-open same-camera boundaries remain insertable:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed because `buildPostMigrationAssertionSql()` lacked `expected adjacent same-camera ReviewSegment boundary to be allowed`; GREEN rerun passed after PG1 inserts `[2026-07-05 10:05, 10:06)` for the same tenant/camera immediately after an existing `[2026-07-05 10:00, 10:05)` segment and asserts the row exists.
- PostgreSQL migration smoke and schema regression passed after the RED failure showed ended ReviewSegment rows could still be represented without an `end_time` guard:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest#alertReviewSegmentEndTimeGuardMigrationRequiresEndedSegmentsToHaveEndTime+schemaDefinesIdempotencyAndLookupIndexes+alertReviewTablesKeepClueEvidenceAndRegionRuleFields" -DfailIfNoTests=false test`
  Result: RED first failed because `V20260708_7__alert_review_segment_end_time_guard.sql` and `ck_supervision_alert_review_segment_ended_time` were missing; GREEN reruns passed after the migration backfilled ended/null rows to `start_time`, added the check constraint, and PG1 rejects a new ended segment without `end_time`.
- PostgreSQL migration smoke and schema regression passed after the RED failure showed alert ReviewSegment rows could still carry detection severity:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest#alertReviewSegmentAlertSeverityGuardMigrationRequiresAlertSegmentsToKeepAlertSeverity+schemaDefinesIdempotencyAndLookupIndexes+alertReviewTablesKeepClueEvidenceAndRegionRuleFields" -DfailIfNoTests=false test`
  Result: RED first failed because `V20260708_8__alert_review_segment_alert_severity_guard.sql` and `ck_supervision_alert_review_segment_alert_severity` were missing; GREEN reruns passed after the migration backfilled existing alert-status rows to `severity='alert'`, added the check constraint, and PG1 rejects a new alert segment with detection severity.
- PostgreSQL migration smoke and schema regression passed after the RED failure showed the review merge lookup index still encoded old zone/rule grouping instead of same-camera ReviewSegment semantics:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest#schemaDefinesIdempotencyAndLookupIndexes+alertReviewHardeningMigrationIsSplitForProductionRelease+alertReviewMergeIndexMigrationUsesSameCameraWindowSemantics" -DfailIfNoTests=false test`
  Result: RED first failed on `SupervisionSchemaSqlTest` because `idx_supervision_alert_review_merge` still expected `zone_code, rule_code`; GREEN reruns passed after the baseline and `V20260702` use `(tenant_id, source_system, camera_id, review_status, last_alert_time)`, `V20260708_9` rebuilds already migrated databases, `PG1` asserts the catalog index shape, and `Pkg` tracks the new migration.
- Local Docker daemon was unavailable, so real container `PG1` was not rerun in this workspace:
  `docker ps --format "{{.Names}}\t{{.Image}}\t{{.Status}}"`
  Historical result: failed to connect to `dockerDesktopLinuxEngine`; the later release PostgreSQL smoke passed all 25 migrations and concurrency races.
- ReviewSegment concurrent ingest and lifecycle hardening regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 83 tests, 0 failures, 0 errors.
- ReviewData standalone JSON schema artifact regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 9 tests, 0 failures, 0 errors.
- FR-19 ReviewData production migration backfill self-test passed after RED showed `PG1` did not include a ReviewData historical-row migration:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: both Node self-tests passed; `PG1` now tracks `V20260705__alert_review_review_data_backfill.sql`, seeds legacy `review_data`, and asserts `reviewDataVersion`, labels, zones, objectIds, objects, detections, reviewSegment, source alert ids, severity, and correlationId survive the production migration chain.
- Lightweight frontend type baseline passed:
  `pnpm exec tsc --noEmit --skipLibCheck --pretty false --allowJs false`
  Result: exit 0.
- Frontend patrol API type baseline rechecked:
  `pnpm exec tsc --noEmit --skipLibCheck --pretty false --allowJs false`
  Result: exit 0; `WEB/src/api/device/patrol.ts` no longer reproduces the earlier ordinary TypeScript first-error signal in the current worktree.
- Workbench/patrol UTF-8 mojibake scan passed:
  `python -X utf8 -` scan over `WEB/scripts/alert-review-workbench-e2e-check.mjs`, `WEB/src/views/alert/components/AlertReviewWorkbench.vue`, and `WEB/src/api/device/patrol.ts`
  Result: 3 files OK for replacement-character and common multi-character mojibake fragments.
- ReviewData/ReviewSegment/event reverse reconciliation/case lifecycle regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest" -DfailIfNoTests=false test`
  Result: 74 tests, 0 failures, 0 errors.
- Evidence-chain reverse lookup metadata slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#evidenceAuditTrailListsHashesExporterDownloadsAndBoundEvents" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Evidence-chain reverse lookup service regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest" -DfailIfNoTests=false test`
  Result: 74 tests, 0 failures, 0 errors.
- Release package verifier self-test passed:
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: `alert review release package verifier tests OK`; the verifier now tracks `V20260704`, `V20260705`, `V20260706`, PG1 PostgreSQL smoke tooling, offline manifest verifier wrapper, LiveDevice smoke tooling, LiveVideo smoke tooling, LivePlayer smoke tooling, the workbench runner test, playback contract test, `WEB/src/utils/withInstall.ts`, and `WEB/src/api/device/patrol.ts`.
- Live VIDEO smoke script self-test passed:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: `alert review VIDEO live smoke tests OK`; the smoke now polls `record-export-url/{export_id}` when VIDEO returns an async export id and refuses to pass until `download_url` is available and reachable through a HEAD probe.
- Live VIDEO smoke reproducibility gate passed after RED showed the smoke could finish without any manifest evidence:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed because checkpoints stopped at `record_export_download_probed`, then failed again when a manifest without clip params still passed; GREEN rerun passed after the smoke required `manifest_url`, fetched manifest v2, and verified ffmpeg command hash, source segment hashes, clip params, concat order, and output hashes before adding `record_export_manifest_verified`.
- VIDEO export payloads now expose persisted manifests to LiveVideo after RED showed service responses had no `manifest_url`:
  `python -m pytest test_record_export.py::TestRecordExportService::test_create_record_export_reuses_local_record_uri_as_download_url test_record_export.py::TestRecordExportService::test_async_record_export_worker_moves_job_to_ready_with_hash_and_download -q`
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed with `KeyError: 'manifest_url'` on sync and async export payloads, then LiveVideo failed to parse VIDEO-shaped `recordSegments`; GREEN reruns passed after sync exports persisted a ready job/manifest and public export payloads exposed `/video/record/export/{export_id}/manifest`.
- Live VIDEO smoke refuses to pass without real configuration:
  `node .scripts/alert-review-video-live-smoke.mjs`
  Result: failed with missing `alert-record-query-url`, `record-coverage-query-url`, `record-base-url`, `record-export-url`, `device-id`, and `alert-time`; this is expected on an unconfigured developer machine and prevents a false positive real-VIDEO smoke.
- Release package verifier blocked the loose worktree before staging:
  `node .scripts/verify-alert-review-release-package.mjs`
  Result: failed with 29 current FR release blockers, including unstaged production migrations and untracked PG1/workbench tooling. This is an intentional P0 release stop until those files are staged/committed or otherwise included in the release package.
- Release package verifier passed after targeted staging:
  `node .scripts/verify-alert-review-release-package.mjs`
  Result: `Alert review release package verifier OK: 29 FR release path(s) checked; no loose FR core file blocked packaging.`
- HEAD-only release verifier still blocks until commit:
  `node .scripts/verify-alert-review-release-package.mjs --require-clean`
  Result: failed with staged `dirty` FR paths, as expected before the package is committed.
- Integration smoke download audit slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#integrationSmokeCoversReviewRecordCaseExportAndManifestVerification" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Converted review item post-event policy slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#convertedReviewItemAllowsEvidenceHardeningButRejectsFalsePositiveRollback" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- FR-30 workbench post-conversion action boundary passed:
  `pnpm test:alert-review-workbench:dev-api-mock`
  Result: RED first failed with `converted review item should hide false-positive action`; GREEN rerun passed after the workbench hid false-positive on converted rows and kept evidence plus record coverage actions visible.
- Review case owner/close lifecycle slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewCaseLifecycleKeepsOwnerDedupCloseAndAuditTrail" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Review case merge/split lifecycle slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#reviewCaseMergeAndSplitMoveCluesWithAuditTrail" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Review case lifecycle controller slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest#caseLifecycleEndpointsMapHttpRequestsToServiceCommands" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Event reverse reconciliation scheduler slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#eventReconcileJobPersistsReverseEventProjectionOutsideListQuery" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Event rollback/rework conflict policy slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#eventReconcileKeepsConvertedItemWhenEventRollbackRequiresRework" -DfailIfNoTests=false test`
  Result: RED first failed because the event reconcile job/report did not expose a rollback conflict; GREEN rerun passed after converted review items kept `reviewStatus=converted`, `reviewData.eventProjection` recorded `conflictPolicy=keep_converted_review_item`, and the scheduled job summary exposed `conflict=1`.
- Runtime patrol scheduler/outbox slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#runtimePatrolAndOutboxJobsCloseScheduledAlertDispatchLoop" -DfailIfNoTests=false test`
  Result: 1 test, 0 failures, 0 errors.
- Alert review scheduler seed package slice passed:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: PG1 now tracks `V20260708_2__alert_review_scheduler_jobs.sql`, bootstraps `infra_job`, and asserts paused patrol/outbox/event-reconcile/semantic-index scheduler seeds; the release verifier tracks the scheduler seed migration as a schema release path.
- Production smoke W2 typecheck preflight slice passed:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `pnpm run type:check` from `WEB/`
  Result: RED first failed because `ProdSmoke` started at `LiveDevice`; GREEN rerun passed after the production smoke step order initially became `W2:typecheck -> LiveDevice -> LiveVideo -> LivePlayer:detail -> LivePlayer:coverage -> LivePlayer:case-timeline`, with the evidence report preserving the W2 step, and the full frontend `vue-tsc --noEmit --skipLibCheck` gate exited 0 locally. This W2 baseline is now preceded by the W4 visible-copy gate.
- Production smoke W4 visible-copy preflight slice passed:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `node .scripts/alert-review-visible-copy-scan.mjs`
  Result: RED first failed because `ProdSmoke` started at `W2:typecheck`; GREEN reruns passed after the production smoke step order became `W4:visible-copy -> W2:typecheck -> LiveDevice -> LiveVideo -> LivePlayer:detail -> LivePlayer:coverage -> LivePlayer:case-timeline`, the evidence report preserved the W4 timeout/command, and `Pkg` blocks removal of the W4 visible-copy anchors.
- Production smoke W2 pnpm-version-guard retry slice passed:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  `pnpm --dir WEB --pm-on-fail=ignore run type:check`
  Result: RED first failed because a Corepack/pnpm version-guard failure stopped `ProdSmoke` before `vue-tsc`; GREEN reruns passed after `W2:typecheck` retries once with `--pm-on-fail=ignore` only for that guard, records `typecheckRetry` evidence with original/retry commands, and `Pkg` blocks removal of the retry evidence anchors. The local full frontend typecheck exited 0 after the retry command reached `vue-tsc`.
- Production smoke manifest verifier evidence gate passed:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `ProdSmoke` accepted a `LiveVideo` child summary without `manifestVerification`; GREEN reruns passed after `LiveVideo` evidence must include `manifestVerification.valid=true`, and `Pkg` blocks removal of the parent-level verifier evidence check.
- Production smoke manifest verifier signature/key evidence gate passed:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `ProdSmoke` accepted `manifestVerification.valid=true` even when `signatureValid=false` and `signatureKeyAvailable=false`; GREEN reruns passed after production smoke requires verifier signature validity, key availability, and key/version alignment with `manifestSignature`, and `Pkg` blocks removal of those parent-level anchors.
- Production smoke manifest storage lifecycle evidence gate passed:
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because `ProdSmoke` dropped `manifestStorageLifecycle` from LiveVideo child evidence and accepted summaries without persisted storage lifecycle metadata; GREEN reruns passed after production smoke requires storage type, object key, expiry, and a non-expired persisted/retained state. The final production RED exposed that VIDEO's canonical live state is `retained` while the parent accepted only the compatibility word `persisted`; a focused retained-MinIO regression failed first, then passed after the parent accepted both safe active states without weakening the remaining lifecycle fields.
- VIDEO storage drift standard reason evidence gate passed:
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  `node .scripts/alert-review-production-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed because LiveVideo summary did not emit standard drift reason keys and `ProdSmoke` accepted a LiveVideo child summary without those keys; GREEN reruns passed after LiveVideo requires and emits `file_missing`, `retention_expired`, `disk_full`, and `cache_flush_failed` reason evidence, ProdSmoke rejects missing standard reason evidence, and `Pkg` blocks removal of both checks.
- Operations report scheduler slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#operationsReportJobGeneratesScheduledShiftAndDailyReports" -DfailIfNoTests=false test`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed at test compile because `SupervisionAlertReviewOperationsReportJob` was missing; GREEN rerun passed after the job generated scheduled shift/daily report summaries and `V20260708_2` seeded paused shift/daily report jobs with handler-param-aware de-duplication.
- Evidence export worker scheduler slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#evidenceExportWorkerRebuildsFailedJobsAndLeavesReplayableManifest" -DfailIfNoTests=false test`
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: RED first failed at test compile because the DEVICE export worker API/job handler was missing; GREEN rerun passed after failed export jobs could be rebuilt to `ready`, worker attempt metadata/backoff was signed into the manifest without breaking verification, and `V20260708_2` seeded the paused export worker job.
- Evidence export worker expiry cleanup slice passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewServiceTest#evidenceExportDownloadExpiresAndWorkerCleansExpiredJobs" -DfailIfNoTests=false test`
  Result: RED first failed at test compile because `EXPORT_JOB_EXPIRED` was missing, then failed because failed/expired jobs were still downloadable or manifest verification was tied to downloadability; GREEN rerun passed after ready jobs past `expiresAt` are cleaned to `expired`, non-ready/expired downloads are rejected, and manifest verification remains replayable.
- VIDEO export storage lifecycle manifest slice passed:
  `python -m pytest test_record_export.py::TestRecordExportService::test_record_export_manifest_tracks_storage_lifecycle_for_persisted_artifacts -q`
  `node .scripts/alert-review-video-live-smoke.test.mjs`
  Result: RED first failed because VIDEO manifests had no `storageLifecycle`, LiveVideo did not reject manifests without storage lifecycle evidence, and the live export body did not force the async worker; GREEN rerun passed after persisted artifacts expose storage type, object key, expiry/status, CLI smoke summary preserves `manifestStorageLifecycle`, and LiveVideo posts `async_worker=true`.
- ReviewData backfill schema regression passed:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 11 tests, 0 failures, 0 errors.
- Local PG1 container execution was attempted again after adding `V20260706`:
  `docker ps --format "{{.Names}}\t{{.Image}}\t{{.Status}}"`
  Historical result: failed to connect to `dockerDesktopLinuxEngine`; the later release PostgreSQL migration smoke passed on the real environment.
- Local PG1 container execution was attempted again after adding `V20260705`:
  `docker ps --format "{{.Names}}\t{{.Image}}\t{{.Status}}"`
  Historical result: failed to connect to `dockerDesktopLinuxEngine`; the later release PostgreSQL migration smoke passed on the real environment.
- PG1 can now run against either Docker PostgreSQL or a direct maintenance database URL, so release migration smoke is no longer Docker Desktop-only:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first failed because `buildPsqlInvocation` / direct URL support did not exist; GREEN rerun passed after `--database-url` parsing, database URL switching, and shared sync/async psql invocation were added while preserving the existing `--container` path.
- PG1 direct URL mode now keeps PostgreSQL passwords out of `psql` argv:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  Result: RED first showed `postgresql://ci:secret@...` in the spawned args; GREEN rerun passed after direct URL execution moved host/user/password/database/sslmode into `PG*` environment variables while preserving the sanitized `psql/<database>` evidence label.
- Full frontend `pnpm run type:check` passed locally after narrowing heavy frontend barrel imports and clearing the remaining non-workbench Vue SFC type errors.

## 2026-07-10 Closure Pass

- ReviewSegment/ReviewData/permission/runtime Java regression passed from `DEVICE/`:
  `mvn -pl iot-system/iot-system-biz -am "-Dtest=SupervisionAlertReviewControllerTest,SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,HttpVideoResolverTest,SupervisionSchemaSqlTest" -DfailIfNoTests=false test`
  Result: 147 tests, 0 failures, 0 errors.
- VIDEO regression passed from `VIDEO/`:
  `python -m pytest test_record_export.py test_record_availability.py test_alert_record_query.py -q`
  Result: 31 tests passed; local ffmpeg is available at `C:\Users\86135\AppData\Local\Microsoft\WinGet\Links\ffmpeg.exe`.
- Workbench contract, dev API mock, and real `DeviceRegionDrawer` fixture passed from `WEB/`:
  `pnpm run test:alert-review-workbench:contract`
  `pnpm run test:alert-review-workbench:dev-api-mock`
  `pnpm run test:alert-review-workbench:dev-api-real-drawer`
  Result: all three modes passed. Visible-copy scan from repository root checked 10 files and passed.
- Frontend full type baseline passed from `WEB/`:
  `pnpm --pm-on-fail=ignore run type:check`
  Result: `vue-tsc --noEmit --skipLibCheck` exited 0.
- Playback contract and smoke self-tests passed:
  `node WEB/scripts/alert-review-playback-contract.test.mjs`
  `node .scripts/alert-review-player-live-smoke.test.mjs`
  `node .scripts/alert-review-device-integration-smoke.test.mjs`
  Result: all self-tests passed. The real player smoke remains blocked until a deployed workbench URL, row text, recording path, and three expected seek timestamps are supplied.
- Offline migration/package self-tests passed:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs`
  `node .scripts/verify-alert-review-release-package.test.mjs`
  Result: both self-tests passed. Real PG1 was attempted with no database URL/container and failed closed with `Provide exactly one of --container=NAME or --database-url=URL`.
- Real VIDEO and production smoke were invoked without parameters and failed closed with explicit missing URL/device/time/player/evidence arguments. No mock result is being counted as production evidence.

## 2026-07-11 Hardening Pass

- DEVICE target-module regression passed from `DEVICE/`:
  `mvn -pl iot-system/iot-system-biz "-Dtest=HttpVideoResolverTest,SupervisionAlertReviewControllerTest,SupervisionAlertReviewServiceTest,SupervisionAlertReviewMapperStoreTest,ConfiguredReviewCameraPermissionResolverTest,MediaPermissionCheckControllerTest,VideoMediaServiceRequestSignerTest" -DforkCount=0 test`
  Result: 197 tests, 0 failures, 0 errors, 0 skipped. This covers verified VIDEO byte download/hash/cleanup, single MP4 and multi-camera ZIP packaging, streaming controller cleanup, persisted real archive hash, user/tenant/camera ACL, `record_manage`, and service signing. The `-am` reactor attempt did not pass: it stopped before `iot-system-biz` on existing `iot-common-data-permission` compilation errors for missing `MyBatisUtils` / `BaseDO`, so the module result is not reported as a reactor GREEN.
- WEB playback and full type gates passed:
  `node scripts/alert-review-playback-contract.test.mjs` from `WEB/` returned `alert review playback contract tests OK`.
  `corepack pnpm@11.3.0 --dir WEB run type:check` exited 0 after running full `vue-tsc --noEmit --skipLibCheck`. The earlier unpinned Corepack command exited 1 at the pnpm 11.5.2 versus repository 11.3.0 version guard before type checking; that guard failure is not counted as a type failure or pass.
- VIDEO export and security regressions passed from `VIDEO/`:
  `python -m unittest test_record_export -v` ran 64 tests in 32.489s, all OK.
  `python -m unittest test_media_authorization test_local_media_path_security test_minio_bucket_policy test_record_availability` ran 71 tests in 7.439s, all OK.
  The private-bucket/DVR-tenant focused RED/GREEN command ran 6 tests and passed after adding private policy removal plus `tenants/{tenantId}/...` keys. Related Python compilation and `git diff --check -- VIDEO` also exited 0.
- Historical local-pass note: the earlier 179-test discovery had three collection/environment failures and one real-MinIO skip. That result is superseded by the final 2026-07-15 discovery, which ran 351 tests successfully with two explicit external-service skips.
- Migration and package self-tests passed:
  `node .scripts/alert-review-postgres-migration-smoke.test.mjs` returned `alert review postgres migration smoke tests OK`.
  `node .scripts/verify-alert-review-release-package.test.mjs` returned `alert review release package verifier tests OK`.
  Historical note: that pass did not claim real DEVICE `PG1`, VIDEO `VMig`, or a clean package gate. The later 2026-07-13/15 closure sections supersede that limitation.
- Documentation scans passed: the status matrix contains exactly one each of FR-01..FR-38, the trace register contains exactly one each of FR-01..FR-38, no common unresolved markers remain, all required 2026-07-11 hardening anchors are present, the shared mojibake catalog found no blocker, and `git diff --check` found no whitespace error. Angle-bracket values and Docker `{{.Names}}` / `{{.Image}}` / `{{.Status}}` tokens are documented command inputs/templates, not unresolved requirements.
- Historical local-pass note: real release evidence was not available at that point. It is superseded by the 2026-07-15 production closure above; the old statement must not be used as the current release verdict.

## 2026-07-13 Release-Candidate Closure

- DEVICE exact reactor gates passed: `J1` ran 225 tests and `J2` ran 40 tests with 0 failures/errors.
- VIDEO full discovery passed: `python -m unittest discover -q` ran 333 tests with 0 failures/errors and 2 explicit external-service skips.
- WEB full type baseline, production build, workbench all-mode E2E, playback contract, W4 visible-copy scan, and release-package self-test passed.
- Real DEVICE PostgreSQL migration smoke passed against the release PostgreSQL container, including duplicate ingest, concurrent reviewer status, and concurrent ReviewSegment overlap races. The production migration runner now applies through `V20260713_4__alert_review_local_scheduler_ownership.sql`; the final migration pauses shared Quartz, assigns execution to the explicitly enabled local scheduler, and protects operations-report outbox idempotency.
- Real VIDEO PostgreSQL migration smoke passed through an SSH tunnel with schema-history/checksum coverage through VIDEO `V20260713`.
- Real ffmpeg + real release MinIO smoke passed through an SSH tunnel with private object storage, five persisted artifacts, real hashes, one-second output media, current HMAC verification, and cleanup evidence.
- MinIO bootstrap no longer contains credential literals or weak fallbacks; credentials are required from the runtime environment. VIDEO child algorithm/ffmpeg processes receive least-privilege environments without the full media-service keyring.
- Production VIDEO uses host networking for ONVIF but must bind to the Docker bridge gateway, not `0.0.0.0` or loopback. On the target server the verified gateway is `172.17.0.1`; Compose must run with `--env-file .env.docker`, host nginx must proxy to that address, DEVICE/WEB container calls must succeed, and public direct port 6000 access must fail.
- WEB install/start/restart/update paths all generate and validate the stream-ticket secret include before starting or recreating nginx.

## 2026-07-15 Final Deployed Artifact Anchor

- Runtime source anchor before the final closure commit: `fc972b4e427aa760dc01467d8719a62ed7162872` (`fix: close review media and smoke boundaries`). All later runtime changes are limited to the VIDEO export recovery file whose deployed SHA-256 is recorded above; production smoke tooling/tests and this document are included by the final closure commit.
- DEVICE runs the complete `fr-r18-fc972b4e-full` image with the complete JAR hash recorded above. The earlier partial image was never kept as `latest` and was removed after acceptance.
- VIDEO `/app` is mounted from `/opt/yfeieye-source/releases/20260715-940c0155-fr/VIDEO`, not from the smoke-time replayable staging directory or older 4ac release. The final package is anchored to code/evidence commit `940c0155b570a2ed49a0e08fabe74e264321eb11` and contains audit-lock serialization, Shanghai queue-clock handling, cleared retry state, original-URI replay recovery, production tooling/tests, the formal register, and the canonical-LF smoke report.
- The final production report is retained as a tracked, sanitized artifact. It contains no bearer token or signed URL query material and can be independently matched by its SHA-256.
- Temporary verifier access was revoked after the manifest passed. Production key material remains server-side; neither the report nor the repository contains the HMAC secret.

## Historical 4ac Artifact Anchor

This section identifies the previous deployed code artifact. It must not be rewritten as evidence that a later documentation commit or current worktree fix is present in the DEVICE image.

- Code/artifact source commit: `4ac979f17e26a0605e60eac984f3336ecbfa9a9b`.
- Current release target at the time of this evidence: `/opt/yfeieye-source/releases/20260713-4ac979f1-fr`; `/opt/yfeieye-source/current` points to it. The retained rollback release is `/opt/yfeieye-source/releases/20260713-c15ea4dc-fr`.
- DEVICE image: `iot-module-system-biz:fr-r12-4ac979f1`, image ID `sha256:5b2e11b6d7a71d35345f98835096bb773435f9d8887ffacb67b119e8df7847fc`, OCI revision `4ac979f17e26a0605e60eac984f3336ecbfa9a9b`.
- DEVICE application JAR: SHA-256 `04b6cff1d95146a86b53715aa992fd297715455cb3bc21e29ec620b4634e5eca`, size `167133328` bytes.
- VIDEO keeps its existing image but mounts `/app` from `/opt/yfeieye-source/releases/20260713-4ac979f1-fr/VIDEO`; the DEVICE and VIDEO containers were healthy with zero restarts when this artifact evidence was captured.
- Deployment delta archive: `/root/fr-delta-c15ea4dc-4ac979f1.tar.gz`, SHA-256 `5118ebbdd25a053ccd143a64eaa8ba4dd8b0b26b00eba0e060aec73965948d97`.
- Any subsequent final documentation/release commit must record its own commit and release metadata. If the DEVICE image is not rebuilt, its artifact source and OCI revision remain `4ac979f1` and must not be relabeled as the later commit.

## Residual Operations, Not Release Blockers

- The host root filesystem remains at 95% after safe cleanup. The new drift/patrol reason catalog detects `disk_full` and report output exposes the condition; operations should schedule capacity expansion or retention cleanup before it crosses the configured hard threshold. Current DEVICE and VIDEO containers remain healthy and restart-free.
- The camera's live GB start call returned the upstream external code `-2` during this session. Acceptance therefore used the same real camera's already persisted, hash-verified DVR FLVs rather than a mock or generated fixture. Live-channel availability remains a camera/network operations concern, not a recording/export correctness bypass.
- `ai-service` was already unhealthy on the shared host and is outside this FR release's DEVICE/VIDEO/workbench execution path. The natural semantic-index scheduler tick inside `iot-system` passed independently.
- W4, W2, PostgreSQL migration smoke, VIDEO discovery, LiveDevice, LiveVideo, all three LivePlayer entrances, and `Pkg --require-clean` remain mandatory recurring gates for every later release even though this artifact has passed them.
