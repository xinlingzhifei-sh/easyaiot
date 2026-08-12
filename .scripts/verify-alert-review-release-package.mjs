import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { VISIBLE_COPY_MOJIBAKE_PATTERNS } from './alert-review-visible-copy-scan.mjs';

export const FR_RELEASE_PATH_RULES = [
  {
    group: 'FR release gate tooling',
    match: /^\.scripts\/(verify-alert-review-release-package|record-export-manifest-verifier|configure-nginx-stream-secret|apply-alert-review-migrations|alert-review-postgres-migration-smoke|alert-review-video-live-smoke|alert-review-device-integration-smoke|alert-review-production-smoke|alert-review-visible-copy-scan|alert-review-player-live-smoke|alert-review-segment-data-reconcile)(\.test)?\.mjs$/,
  },
  {
    group: 'FR production media deployment',
    match: /^\.scripts\/docker\/(docker-compose\.yml|env\.example|install_middleware_linux\.sh|upload_minio_data\.sh)$/,
  },
  {
    group: 'FR production media deployment',
    match: /^\.scripts\/media-cluster\/(docker-compose\.media-node\.yml|enable_cluster_mode\.sh|install_media_stack\.sh|srs\/cluster\.conf\.template|zlm\/config\.ini\.template)$/,
  },
  {
    group: 'DEVICE review backend',
    match:
      /^DEVICE\/iot-system\/iot-system-biz\/src\/main\/java\/com\/basiclab\/iot\/system\/(controller\/admin\/supervision\/|controller\/admin\/auth\/(AuthController\.java|vo\/MediaPermissionCheck.*\.java)$|dal\/dataobject\/supervision\/|dal\/pgsql\/supervision\/|job\/supervision\/|service\/supervision\/)/,
  },
  {
    group: 'DEVICE tenant job runtime',
    match:
      /^DEVICE\/iot-common\/iot-common-tenant\/src\/(main\/java\/com\/basiclab\/iot\/common\/(config\/YudaoTenantAutoConfiguration|core\/job\/TenantJobAspect)|test\/java\/com\/basiclab\/iot\/common\/core\/job\/TenantJobAspectTest)\.java$/,
  },
  {
    group: 'DEVICE schema and migration',
    match:
      /^DEVICE\/iot-system\/iot-system-biz\/src\/main\/resources\/(schemas\/alert-review-|sql\/migrations\/(V20260701__supervision_event_closure_baseline|V2026070[245678]__alert_review_(frigate_hardening|segment_tenant_scope|review_data_backfill|media_permissions|item_media_audit|segment_status_transition)|V20260708_2__alert_review_scheduler_jobs|V20260708_3__alert_review_report_ack|V20260708_4__alert_review_runtime_outbox_notify_templates|V20260708_5__alert_review_runtime_outbox_delivery|V20260708_6__alert_review_runtime_outbox_claim|V20260708_7__alert_review_segment_end_time_guard|V20260708_8__alert_review_segment_alert_severity_guard|V20260708_9__alert_review_merge_index_same_camera|V20260708_10__alert_review_deleted_smallint|V20260709__alert_review_scheduler_activation|V20260710__alert_review_export_queue|V20260711__alert_review_media_manage_permission|V20260712__alert_review_semantic_trigger_confirmation|V20260713__alert_review_semantic_index_claim|V20260713_2__alert_review_evidence_record_start|V20260713_3__supervision_event_create_permission|V20260713_4__alert_review_local_scheduler_ownership|V20260713_5__alert_review_notify_message_params_text)\.sql)/,
  },
  {
    group: 'DEVICE review regression tests',
    match:
      /^DEVICE\/iot-system\/iot-system-biz\/src\/test\/java\/com\/basiclab\/iot\/system\/supervision\/(AlertReviewDataSchemaValidatorTest|ConfiguredReviewCameraPermissionResolver|HttpVideoResolver|MediaPermissionCheckControllerTest|NotifyReviewRuntimeOutboxPublisherTest|ReviewEvidenceManifestSignerTest|SupervisionAlertReview|SupervisionPersistenceIdContractTest|SupervisionSchemaSqlTest|VideoMediaServiceRequestSignerTest)/,
  },
  {
    group: 'DEVICE video integration config',
    match: /^DEVICE\/(docker-compose\.yml|install_linux\.sh|iot-system\/iot-system-biz\/src\/main\/resources\/application\.yaml)$/,
  },
  {
    group: 'VIDEO record evidence package',
    match:
      /^VIDEO\/(\.gitignore|apply_migrations\.py|bootstrap_schema\.py|enforce_private_media_buckets\.py|prepare_database\.py|schema_lock\.py|run\.py|models\.py|migrations\/(V20260711__device_detection_region_rule_fields|V20260712__record_snapshot_tenant_scope|V20260713__alert_image_playback_tenant_scope)\.sql|app\/blueprints\/(alert|camera|device_detection_region|media_hook|playback|record|snap)\.py|app\/services\/(alert_service|algorithm_task_daemon|algorithm_task_launcher_service|auto_frame_extraction_service|device_detection_region_service|dvr_upload_service|library_matching_service|local_media_path_service|media_authorization_service|media_janitor_service|media_kafka_service|media_resource_guard|playback_disk_guard_service|post_process_launcher_service|record_cache_flush_event_service|seekable_playback_service|snap_(image|space|task|upload)_service|space_(file_metadata|folder_tree|group_save_time)_service|storage_service|stream_forward_launcher_service|record_(export|export_manifest_verifier|space|video).*)\.py|app\/utils\/(face_model_paths|minio_bucket_policy|patrol_snap_upload|plate_model_paths|video_env)\.py|services\/(patrol_algorithm_service|realtime_algorithm_service|snapshot_algorithm_service)\/run_deploy\.py|test_(alert_hook_direct_persist|alert_media_serialization|alert_notification|alert_tenant_scope|apply_migrations|archive_atomicity|device_detection_region_persistence|local_media_path_security|media_authorization|minio_bucket_policy|playback_media_authorization|record_(availability|export)|record_export_minio_smoke|record_space_tenant_listing|seekable_playback|snap_media_authorization|stream_forward|subprocess_environment|tenant_media_(maintenance|persistence)|tenant_migration_postgres)\.py|tests\/(test_gb28181_sync_service|test_realtime_algorithm_context|test_stream_url_sync_service)\.py|install_linux(?:_arm|_kylin)?\.sh|docker-compose\.yaml|env\.example)$/,
  },
  {
    group: 'Protected media raw proxy package',
    match: /^(AI\/(app\/blueprints\/minio_proxy|tests\/test_minio_proxy)\.py|AI\/(docker-compose\.yaml|env\.example|install_linux(?:_arm|_kylin)?\.sh)|APP\/(conf\/nginx\.conf|docker-compose\.yaml)|WEB\/(conf\/nginx(?:\.mini)?\.conf|docker-compose\.yaml|install_linux\.sh))$/,
  },
  {
    group: 'WEB alert review workbench package',
    match:
      /^WEB\/(package\.json|scripts\/(alert-review-workbench-e2e-check(\.test)?|alert-review-playback-contract\.test)\.mjs|scripts\/fixtures\/alert-review-workbench-e2e\/|src\/api\/(supervision\/alertReview|device\/(patrol|device_detection_region|snap))\.ts|src\/components\/(VideoPlayer\/DialogPlayer\.vue|Player\/module\/jessibuca\.vue)|src\/utils\/(alertRecord|alertRecordPlayback|withInstall)\.ts|src\/views\/(alert\/(index\.vue|components\/AlertReviewWorkbench\.vue)|camera\/components\/(DeviceRegionDrawer\/index\.vue|SnapSpace\/SnapSpaceImageGallery\.vue)))/,
  },
  {
    group: 'FR documentation package',
    match: /^docs\/(requirements\/alert-review-frigate-fr01-fr38-hardening-review\.md|superpowers\/(plans|specs)\/2026-0[67]-.*alert-review.*\.md)$/,
  },
];

const MOJIBAKE_PATTERNS = [
  ...VISIBLE_COPY_MOJIBAKE_PATTERNS,
  { pattern: '\u9352\u6d98\u7f13', reason: 'encoding_mojibake' },
  { pattern: '\u93c8\u5d85\u59df', reason: 'encoding_mojibake' },
  { pattern: '\u9363\u3125\u5534\u95ae\u3129\u654a\u7487', reason: 'encoding_mojibake' },
];

export const TRACKED_RELEASE_PATHS = [
  '.scripts/verify-alert-review-release-package.mjs',
  '.scripts/verify-alert-review-release-package.test.mjs',
  '.scripts/record-export-manifest-verifier.mjs',
  '.scripts/configure-nginx-stream-secret.mjs',
  '.scripts/configure-nginx-stream-secret.test.mjs',
  '.scripts/apply-alert-review-migrations.mjs',
  '.scripts/apply-alert-review-migrations.test.mjs',
  '.scripts/alert-review-postgres-migration-smoke.mjs',
  '.scripts/alert-review-postgres-migration-smoke.test.mjs',
  '.scripts/alert-review-video-live-smoke.mjs',
  '.scripts/alert-review-video-live-smoke.test.mjs',
  '.scripts/alert-review-device-integration-smoke.mjs',
  '.scripts/alert-review-device-integration-smoke.test.mjs',
  '.scripts/alert-review-production-smoke.mjs',
  '.scripts/alert-review-production-smoke.test.mjs',
  '.scripts/alert-review-visible-copy-scan.mjs',
  '.scripts/alert-review-visible-copy-scan.test.mjs',
  '.scripts/alert-review-player-live-smoke.mjs',
  '.scripts/alert-review-player-live-smoke.test.mjs',
  '.scripts/alert-review-segment-data-reconcile.mjs',
  '.scripts/alert-review-segment-data-reconcile.test.mjs',
  '.scripts/docker/docker-compose.yml',
  '.scripts/docker/env.example',
  '.scripts/docker/install_middleware_linux.sh',
  '.scripts/docker/upload_minio_data.sh',
  '.scripts/media-cluster/enable_cluster_mode.sh',
  '.scripts/media-cluster/docker-compose.media-node.yml',
  '.scripts/media-cluster/install_media_stack.sh',
  '.scripts/media-cluster/srs/cluster.conf.template',
  '.scripts/media-cluster/zlm/config.ini.template',
  'AI/app/blueprints/minio_proxy.py',
  'AI/tests/test_minio_proxy.py',
  'AI/docker-compose.yaml',
  'AI/env.example',
  'AI/install_linux.sh',
  'AI/install_linux_arm.sh',
  'AI/install_linux_kylin.sh',
  'APP/conf/nginx.conf',
  'APP/docker-compose.yaml',
  'DEVICE/docker-compose.yml',
  'DEVICE/install_linux.sh',
  'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/config/YudaoTenantAutoConfiguration.java',
  'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/core/job/TenantJobAspect.java',
  'DEVICE/iot-common/iot-common-tenant/src/test/java/com/basiclab/iot/common/core/job/TenantJobAspectTest.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/supervision',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/AuthController.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/vo/MediaPermissionCheckReqVO.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/vo/MediaPermissionCheckRespVO.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/job/supervision',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/schemas',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260702__alert_review_frigate_hardening.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260704__alert_review_segment_tenant_scope.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260705__alert_review_review_data_backfill.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260707__alert_review_item_media_audit.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708__alert_review_segment_status_transition.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_2__alert_review_scheduler_jobs.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_3__alert_review_report_ack.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_4__alert_review_runtime_outbox_notify_templates.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_5__alert_review_runtime_outbox_delivery.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_6__alert_review_runtime_outbox_claim.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_7__alert_review_segment_end_time_guard.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_8__alert_review_segment_alert_severity_guard.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_9__alert_review_merge_index_same_camera.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_10__alert_review_deleted_smallint.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260709__alert_review_scheduler_activation.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260710__alert_review_export_queue.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260712__alert_review_semantic_trigger_confirmation.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713__alert_review_semantic_index_claim.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_2__alert_review_evidence_record_start.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_3__supervision_event_create_permission.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_5__alert_review_notify_message_params_text.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql',
  'DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision',
  'VIDEO/.gitignore',
  'VIDEO/apply_migrations.py',
  'VIDEO/bootstrap_schema.py',
  'VIDEO/enforce_private_media_buckets.py',
  'VIDEO/prepare_database.py',
  'VIDEO/schema_lock.py',
  'VIDEO/run.py',
  'VIDEO/models.py',
  'VIDEO/app/blueprints/alert.py',
  'VIDEO/app/blueprints/playback.py',
  'VIDEO/app/blueprints/record.py',
  'VIDEO/app/blueprints/snap.py',
  'VIDEO/app/blueprints/camera.py',
  'VIDEO/app/blueprints/media_hook.py',
  'VIDEO/app/blueprints/device_detection_region.py',
  'VIDEO/app/services/device_detection_region_service.py',
  'VIDEO/app/services/alert_service.py',
  'VIDEO/app/services/algorithm_task_daemon.py',
  'VIDEO/app/services/algorithm_task_launcher_service.py',
  'VIDEO/app/services/auto_frame_extraction_service.py',
  'VIDEO/app/services/dvr_upload_service.py',
  'VIDEO/app/services/library_matching_service.py',
  'VIDEO/app/services/local_media_path_service.py',
  'VIDEO/app/services/media_authorization_service.py',
  'VIDEO/app/services/media_janitor_service.py',
  'VIDEO/app/services/media_kafka_service.py',
  'VIDEO/app/services/media_resource_guard.py',
  'VIDEO/app/services/playback_disk_guard_service.py',
  'VIDEO/app/services/post_process_launcher_service.py',
  'VIDEO/app/services/record_cache_flush_event_service.py',
  'VIDEO/app/services/record_export_manifest_verifier.py',
  'VIDEO/app/services/record_export_service.py',
  'VIDEO/app/services/record_space_service.py',
  'VIDEO/app/services/record_video_service.py',
  'VIDEO/app/services/seekable_playback_service.py',
  'VIDEO/app/services/snap_image_service.py',
  'VIDEO/app/services/snap_space_service.py',
  'VIDEO/app/services/snap_task_service.py',
  'VIDEO/app/services/snap_upload_service.py',
  'VIDEO/app/services/space_file_metadata_service.py',
  'VIDEO/app/services/space_folder_tree_service.py',
  'VIDEO/app/services/space_group_save_time_service.py',
  'VIDEO/app/services/stream_forward_launcher_service.py',
  'VIDEO/app/services/storage_service.py',
  'VIDEO/app/utils/face_model_paths.py',
  'VIDEO/app/utils/minio_bucket_policy.py',
  'VIDEO/app/utils/patrol_snap_upload.py',
  'VIDEO/app/utils/plate_model_paths.py',
  'VIDEO/app/utils/video_env.py',
  'VIDEO/services/patrol_algorithm_service/run_deploy.py',
  'VIDEO/services/realtime_algorithm_service/run_deploy.py',
  'VIDEO/services/snapshot_algorithm_service/run_deploy.py',
  'VIDEO/migrations/V20260711__device_detection_region_rule_fields.sql',
  'VIDEO/migrations/V20260712__record_snapshot_tenant_scope.sql',
  'VIDEO/migrations/V20260713__alert_image_playback_tenant_scope.sql',
  'VIDEO/docker-compose.yaml',
  'VIDEO/test_apply_migrations.py',
  'VIDEO/test_archive_atomicity.py',
  'VIDEO/test_alert_hook_direct_persist.py',
  'VIDEO/test_alert_media_serialization.py',
  'VIDEO/test_alert_notification.py',
  'VIDEO/test_alert_tenant_scope.py',
  'VIDEO/test_device_detection_region_persistence.py',
  'VIDEO/test_local_media_path_security.py',
  'VIDEO/test_record_availability.py',
  'VIDEO/test_record_export.py',
  'VIDEO/test_record_export_minio_smoke.py',
  'VIDEO/test_record_space_tenant_listing.py',
  'VIDEO/test_media_authorization.py',
  'VIDEO/test_minio_bucket_policy.py',
  'VIDEO/test_playback_media_authorization.py',
  'VIDEO/test_seekable_playback.py',
  'VIDEO/test_snap_media_authorization.py',
  'VIDEO/test_stream_forward.py',
  'VIDEO/test_subprocess_environment.py',
  'VIDEO/test_tenant_media_maintenance.py',
  'VIDEO/test_tenant_media_persistence.py',
  'VIDEO/test_tenant_migration_postgres.py',
  'VIDEO/tests/test_gb28181_sync_service.py',
  'VIDEO/tests/test_realtime_algorithm_context.py',
  'VIDEO/tests/test_stream_url_sync_service.py',
  'VIDEO/env.example',
  'VIDEO/install_linux.sh',
  'VIDEO/install_linux_arm.sh',
  'VIDEO/install_linux_kylin.sh',
  'WEB/package.json',
  'WEB/conf/nginx.conf',
  'WEB/conf/nginx.mini.conf',
  'WEB/docker-compose.yaml',
  'WEB/install_linux.sh',
  'WEB/scripts/alert-review-workbench-e2e-check.mjs',
  'WEB/scripts/alert-review-workbench-e2e-check.test.mjs',
  'WEB/scripts/alert-review-playback-contract.test.mjs',
  'WEB/scripts/fixtures/alert-review-workbench-e2e',
  'WEB/src/api/device/patrol.ts',
  'WEB/src/api/device/device_detection_region.ts',
  'WEB/src/api/device/snap.ts',
  'WEB/src/api/supervision/alertReview.ts',
  'WEB/src/components/VideoPlayer/DialogPlayer.vue',
  'WEB/src/components/Player/module/jessibuca.vue',
  'WEB/src/utils/alertRecord.ts',
  'WEB/src/utils/alertRecordPlayback.ts',
  'WEB/src/utils/withInstall.ts',
  'WEB/src/views/alert/components/AlertReviewWorkbench.vue',
  'WEB/src/views/alert/index.vue',
  'WEB/src/views/camera/components/DeviceRegionDrawer/index.vue',
  'WEB/src/views/camera/components/SnapSpace/SnapSpaceImageGallery.vue',
  'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
  'docs/superpowers/plans/2026-06-30-alert-review-phase-2.md',
  'docs/superpowers/plans/2026-07-01-alert-review-fr13-fr16.md',
  'docs/superpowers/specs/2026-06-30-alert-review-phase-2-design.md',
];

function normalizePath(rawPath) {
  const renameTarget = rawPath.includes(' -> ') ? rawPath.split(' -> ').pop() : rawPath;
  return renameTarget.replace(/^"\s*/, '').replace(/\s*"$/, '').replace(/\\/g, '/').replace(/^\.\//, '');
}

function parseStatusLine(line) {
  if (!line.trim()) {
    return null;
  }
  if (line.length < 4) {
    return null;
  }
  return {
    status: line.slice(0, 2),
    path: normalizePath(line.slice(3)),
  };
}

function releaseGroupFor(path) {
  return FR_RELEASE_PATH_RULES.find((rule) => rule.match.test(path))?.group ?? null;
}

function blockerReason(entry, requireClean) {
  const [indexStatus, worktreeStatus] = entry.status;
  if (entry.status === '??') {
    return 'untracked';
  }
  if (indexStatus === 'D' || worktreeStatus === 'D') {
    return 'deleted';
  }
  if (entry.status.includes('U')) {
    return 'unmerged';
  }
  if (requireClean && entry.status.trim()) {
    return 'dirty';
  }
  if (worktreeStatus !== ' ') {
    return 'unstaged';
  }
  return null;
}

export function evaluateStatus(statusText, options = {}) {
  const requireClean = options.requireClean === true;
  const entries = [];
  const blockers = [];

  for (const rawLine of statusText.split(/\r?\n/)) {
    const entry = parseStatusLine(rawLine);
    if (!entry) {
      continue;
    }
    const group = releaseGroupFor(entry.path);
    if (!group) {
      continue;
    }

    const checkedEntry = { ...entry, group };
    entries.push(checkedEntry);
    const reason = blockerReason(checkedEntry, requireClean);
    if (reason) {
      blockers.push({ ...checkedEntry, reason });
    }
  }

  return {
    ok: blockers.length === 0,
    entries,
    blockers,
  };
}

export function releaseEntriesForTrackedPaths(paths) {
  const entries = [];
  for (const rawPath of paths) {
    const path = normalizePath(rawPath || '');
    const group = releaseGroupFor(path);
    if (!group) {
      continue;
    }
    entries.push({
      status: '  ',
      path,
      group,
    });
  }
  return entries;
}

export function scanTextQuality(files) {
  const blockers = [];
  for (const file of files) {
    const path = normalizePath(file.path || '');
    const group = releaseGroupFor(path);
    if (!group || _looksBinaryPath(path)) {
      continue;
    }
    const content = String(file.content ?? '');
    for (const { pattern, reason } of MOJIBAKE_PATTERNS) {
      const index = content.indexOf(pattern);
      if (index === -1) {
        continue;
      }
      blockers.push({
        path,
        group,
        reason,
        pattern,
        line: _lineNumberAt(content, index),
      });
      break;
    }
  }
  return {
    ok: blockers.length === 0,
    blockers,
  };
}

export function scanWebTypecheckGate(files) {
  const packageFile = files.find((file) => normalizePath(file.path || '') === 'WEB/package.json');
  if (!packageFile) {
    return { ok: true, blockers: [] };
  }
  const path = 'WEB/package.json';
  const group = releaseGroupFor(path);
  const blockers = [];
  let parsed;
  try {
    parsed = JSON.parse(String(packageFile.content ?? ''));
  } catch {
    blockers.push({ path, group, reason: 'web_typecheck_gate_invalid_package_json' });
    return { ok: false, blockers };
  }
  const typecheck = parsed?.scripts?.['type:check'];
  const typecheckCommand = String(typecheck || '');
  if (!hasText(typecheck)) {
    blockers.push({ path, group, reason: 'web_typecheck_gate_missing' });
  } else if (!typecheckCommand.includes('vue-tsc') || !typecheckCommand.includes('--noEmit')) {
    blockers.push({ path, group, reason: 'web_typecheck_gate_weakened' });
  }
  return {
    ok: blockers.length === 0,
    blockers,
  };
}

export function scanMediaPermissionGate(files) {
  const migrationPath = 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql';
  const migration = files.find((file) => normalizePath(file.path || '') === migrationPath);
  const managementMigrationPath = 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql';
  const managementMigration = files.find((file) => normalizePath(file.path || '') === managementMigrationPath);
  const appConfigPath = 'DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml';
  const appConfig = files.find((file) => normalizePath(file.path || '') === appConfigPath);
  const blockers = [];
  if (migration) {
    const permissionReasons = [
      ['system:supervision-alert-review:media:playback', 'media_permission_playback_seed_missing'],
      ['system:supervision-alert-review:media:snapshot', 'media_permission_snapshot_seed_missing'],
      ['system:supervision-alert-review:media:export', 'media_permission_export_seed_missing'],
      ['system:supervision-alert-review:media:download', 'media_permission_download_seed_missing'],
      ['system:supervision-alert-review:media:manifest', 'media_permission_manifest_seed_missing'],
    ];
    const migrationContent = String(migration.content ?? '');
    for (const [permission, reason] of permissionReasons) {
      if (!migrationContent.includes(permission)) {
        blockers.push({
          path: migrationPath,
          group: releaseGroupFor(migrationPath),
          reason,
        });
      }
    }
  }
  if (managementMigration
      && !String(managementMigration.content ?? '').includes('system:supervision-alert-review:media:manage')) {
    blockers.push({
      path: managementMigrationPath,
      group: releaseGroupFor(managementMigrationPath),
      reason: 'media_permission_manage_seed_missing',
    });
  }
  if (appConfig) {
    const configContent = String(appConfig.content ?? '');
    const actionPermissionReasons = [
      [['action-permissions:', 'playback:', 'system:supervision-alert-review:media:playback'], 'media_permission_playback_action_config_missing'],
      [['action-permissions:', 'snapshot:', 'system:supervision-alert-review:media:snapshot'], 'media_permission_snapshot_action_config_missing'],
      [['action-permissions:', 'coverage:', 'system:supervision-alert-review:media:playback'], 'media_permission_coverage_action_config_missing'],
      [['action-permissions:', 'export:', 'system:supervision-alert-review:media:export'], 'media_permission_export_action_config_missing'],
      [['action-permissions:', 'download:', 'system:supervision-alert-review:media:download'], 'media_permission_download_action_config_missing'],
      [['action-permissions:', 'manifest_verify:', 'system:supervision-alert-review:media:manifest'], 'media_permission_manifest_action_config_missing'],
    ];
    for (const [fragments, reason] of actionPermissionReasons) {
      if (!containsAll(configContent, fragments)) {
        blockers.push({
          path: appConfigPath,
          group: releaseGroupFor(appConfigPath),
          reason,
        });
      }
    }
  }
  return {
    ok: blockers.length === 0,
    blockers,
  };
}

const RAW_MINIO_PROTECTED_BUCKETS = [
  'record-space',
  'snap-space',
  'camera-screenshots',
  'alert-images',
  'record-archive',
  'snap-archive',
  'review-evidence',
];

const RAW_MINIO_NGINX_PATHS = [
  'APP/conf/nginx.conf',
  'WEB/conf/nginx.conf',
  'WEB/conf/nginx.mini.conf',
];

const SRS_PUBLIC_CALLBACK_PATHS = {
  'APP/conf/nginx.conf': [
    '/dev-api/video/camera/callback/on_publish',
    '/dev-api/video/camera/callback/on_dvr',
  ],
  'WEB/conf/nginx.conf': [
    '/yfeieye/dev-api/video/camera/callback/on_publish',
    '/yfeieye/dev-api/video/camera/callback/on_dvr',
    '/dev-api/video/camera/callback/on_publish',
    '/dev-api/video/camera/callback/on_dvr',
  ],
  'WEB/conf/nginx.mini.conf': [
    '/dev-api/video/camera/callback/on_publish',
    '/dev-api/video/camera/callback/on_dvr',
  ],
};

const INTERNAL_MEDIA_HOOK_PUBLIC_PREFIXES = {
  'APP/conf/nginx.conf': [
    '/dev-api/video/media/hook/',
  ],
  'WEB/conf/nginx.conf': [
    '/yfeieye/dev-api/video/media/hook/',
    '/dev-api/video/media/hook/',
  ],
  'WEB/conf/nginx.mini.conf': [
    '/dev-api/video/media/hook/',
  ],
};

const STREAM_TICKET_COMPOSE_PATHS = [
  'APP/docker-compose.yaml',
  'WEB/docker-compose.yaml',
];

const STREAM_TICKET_INSTALL_PATHS = [
  'WEB/install_linux.sh',
];

export function scanRawMinioProxyGate(files) {
  const blockers = [];
  const proxyPath = 'AI/app/blueprints/minio_proxy.py';
  const proxy = files.find((file) => normalizePath(file.path || '') === proxyPath);
  if (proxy) {
    const content = String(proxy.content ?? '');
    const guard = 'normalized_bucket in _PROTECTED_MEDIA_BUCKETS';
    const guardIndex = content.indexOf(guard);
    const downloadIndex = guardIndex === -1
      ? -1
      : content.indexOf('_download_from_minio(', guardIndex + guard.length);
    const completeDenylist = content.includes('_PROTECTED_MEDIA_BUCKETS')
      && RAW_MINIO_PROTECTED_BUCKETS.every(
        (bucket) => content.includes(`'${bucket}'`) || content.includes(`"${bucket}"`),
      )
      && guardIndex !== -1
      && downloadIndex > guardIndex
      && content.includes('protected_media_bucket')
      && /\b403\b/.test(content);
    if (!completeDenylist) {
      blockers.push({
        path: proxyPath,
        group: releaseGroupFor(proxyPath),
        reason: 'raw_minio_protected_bucket_denylist_missing',
      });
    }
  }

  for (const path of RAW_MINIO_NGINX_PATHS) {
    const nginx = files.find((file) => normalizePath(file.path || '') === path);
    if (!nginx) {
      continue;
    }
    const content = String(nginx.content ?? '');
    const bucketLocation = extractNginxBucketLocation(content);
    if (!bucketLocation) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'raw_minio_nginx_bucket_proxy_missing',
      });
      continue;
    }
    const directives = bucketLocation.replace(/#.*$/gm, '');
    if (/\ballow\s+all\s*;/.test(directives)) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'raw_minio_nginx_bucket_allow_all',
      });
    }

    const nginxDirectives = content.replace(/#.*$/gm, '');
    if (/\bset\s+\$stream_secret\s+"[^"]+"\s*;/.test(nginxDirectives)) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'stream_ticket_hardcoded_secret',
      });
    }
    if (/\bset\s+\$stream_secret\s+""\s*;/.test(nginxDirectives)) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'stream_ticket_empty_fallback',
      });
    }
    if (!/\binclude\s+\/etc\/nginx\/yfeieye-secrets\/yfeieye-stream-secret\.runtime\.conf\s*;/.test(
      nginxDirectives,
    )) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'stream_ticket_external_secret_missing',
      });
    }
    const missingCallbackDeny = SRS_PUBLIC_CALLBACK_PATHS[path].some((callbackPath) => {
      const escapedPath = callbackPath.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      return !new RegExp(
        `location\\s*=\\s*${escapedPath}\\s*\\{\\s*return\\s+403\\s*;\\s*\\}`,
      ).test(nginxDirectives);
    }) || INTERNAL_MEDIA_HOOK_PUBLIC_PREFIXES[path].some((hookPrefix) => {
      const escapedPrefix = hookPrefix.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      return !new RegExp(
        `location\\s+\\^~\\s+${escapedPrefix}\\s*\\{\\s*return\\s+403\\s*;\\s*\\}`,
      ).test(nginxDirectives);
    });
    if (missingCallbackDeny) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'srs_hook_public_callback_deny_missing',
      });
    }
    const secureLinkCount = (nginxDirectives.match(
      /\bsecure_link\s+\$arg_st\s*,\s*\$arg_e\s*;/g,
    ) || []).length;
    const secureLinkMd5Count = (nginxDirectives.match(
      /\bsecure_link_md5\s+"\$arg_e\$uri\s+\$stream_secret"\s*;/g,
    ) || []).length;
    const deniedCount = (nginxDirectives.match(
      /if\s*\(\s*\$secure_link\s*=\s*""\s*\)\s*\{\s*return\s+403\s*;\s*\}/g,
    ) || []).length;
    const expiredCount = (nginxDirectives.match(
      /if\s*\(\s*\$secure_link\s*=\s*"0"\s*\)\s*\{\s*return\s+410\s*;\s*\}/g,
    ) || []).length;
    if (secureLinkCount < 2 || secureLinkMd5Count < 2 || deniedCount < 2 || expiredCount < 2) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'stream_ticket_enforcement_missing',
      });
    }
  }

  for (const path of STREAM_TICKET_COMPOSE_PATHS) {
    const compose = files.find((file) => normalizePath(file.path || '') === path);
    if (!compose) continue;
    const content = String(compose.content ?? '').replace(/#.*$/gm, '');
    if (!/YFEIEYE_NGINX_SECRET_DIR[^\n]*:\/etc\/nginx\/yfeieye-secrets:ro/.test(content)) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'stream_ticket_secret_mount_missing',
      });
    }
  }

  for (const path of STREAM_TICKET_INSTALL_PATHS) {
    const installer = files.find((file) => normalizePath(file.path || '') === path);
    if (!installer) continue;
    const content = String(installer.content ?? '').replace(/#.*$/gm, '');
    if (!containsAll(content, [
      'configure-nginx-stream-secret.mjs',
      '--env-file=',
      '--skip-nginx-check',
    ])) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'stream_ticket_secret_install_hook_missing',
      });
    }
  }

  return { ok: blockers.length === 0, blockers };
}

const MIDDLEWARE_SHARED_STATE_MOUNTS = [
  '/nacos_data/data:/home/nacos/data',
  '/nacos_data/logs:/home/nacos/logs',
  '/db_data/data:/var/lib/postgresql/data',
  '/db_data/log:/var/log/postgresql',
  '/taos_data/data:/var/lib/taos',
  '/redis_data/data:/data',
  '/mq_data/data:/var/lib/kafka/data',
  '/minio_data/data:/data',
  '/milvus_data:/var/lib/milvus',
  '/srs_data/conf:/usr/local/srs/conf',
  '/nodered_data/data:/data',
  '/zlmediakit/conf:/conf',
  '/gpustack_data:/var/lib/gpustack',
];

const AI_SHARED_STATE_MOUNTS = [
  '/data:/app/data',
  '/static:/app/static',
  '/temp_uploads:/app/temp_uploads',
  '/model:/app/model',
  '/logs/app:/app/logs',
  '/logs/services:/app/services/logs',
];

const DEVICE_SHARED_STATE_MOUNTS = [
  '${YFEIEYE_DEVICE_STATE_ROOT:-/opt/yfeieye-source/shared/device}/logs:/root/logs/',
  '${YFEIEYE_DEVICE_STATE_ROOT:-/opt/yfeieye-source/shared/device}/node-logs:/root/logs/',
  '${YFEIEYE_VIDEO_STATE_ROOT:-/data/yfeieye-video}/alert_images:/app/alert_images',
];

export function scanStatefulReleaseMountGate(files) {
  const blockers = [];
  const byPath = new Map(
    files.map((file) => [normalizePath(file.path || ''), String(file.content ?? '')]),
  );
  const shellFunctionBody = (source, name) => source.match(
    new RegExp(`(?:^|\\n)${name}\\(\\) \\{([\\s\\S]*?)\\n\\}`),
  )?.[1] || '';
  const lifecycleFailureIsSwallowed = (source, functionNames, composeWrapper) => functionNames
    .map((name) => shellFunctionBody(source, name))
    .filter(Boolean)
    .some((body) => body.split(/\r?\n/).some(
      (line) => line.includes(composeWrapper)
        && (/\|\|\s*true/.test(line) || /\|\s*grep\b/.test(line)),
    ));
  const lifecycleDoesNotForceRecreate = (source, functionName, composeWrapper) => {
    const body = shellFunctionBody(source, functionName);
    if (!body) return false;
    const invocations = body.split(/\r?\n/)
      .map((line) => line.trim())
      .filter((line) => line.startsWith(`${composeWrapper} up `)
        || line.startsWith(`${composeWrapper} restart`));
    return invocations.length === 0 || invocations.some(
      (line) => line.startsWith(`${composeWrapper} restart`)
        || !line.includes('--force-recreate'),
    );
  };
  const pipedComposeStopFailureIsSwallowed = (source, functionName, composeWrapper) => {
    const body = shellFunctionBody(source, functionName);
    if (!body) return false;
    const hasPipedStop = body.split(/\r?\n/).some(
      (line) => line.includes(`${composeWrapper} down`) && line.includes('|'),
    );
    if (!hasPipedStop) return false;
    const hasPipefail = /(?:^|\n)\s*set\s+-o\s+pipefail\b/.test(source)
      || /(?:^|\n)\s*set\s+-[^\n]*\bpipefail\b/.test(source);
    const propagatesComposeStatus = body.includes('PIPESTATUS[0]')
      && /return\s+["']?\$\{?_[A-Za-z0-9_]*rc\}?["']?/.test(body);
    return !hasPipefail && !propagatesComposeStatus;
  };

  const deviceComposePath = 'DEVICE/docker-compose.yml';
  const deviceCompose = byPath.get(deviceComposePath);
  if (deviceCompose !== undefined) {
    const usesSharedRoot = DEVICE_SHARED_STATE_MOUNTS.every(
      (mount) => deviceCompose.includes(mount),
    );
    const hasReleaseBoundState = /(?:^|\n)\s*-\s*["']?\.\.\/(?:\.build-cache\/device|VIDEO\/alert_images)\//m.test(
      deviceCompose,
    );
    if (!usesSharedRoot || hasReleaseBoundState) {
      blockers.push({
        path: deviceComposePath,
        group: releaseGroupFor(deviceComposePath),
        reason: 'device_state_mounts_release_bound',
      });
    }
  }

  const deviceInstallerPath = 'DEVICE/install_linux.sh';
  const deviceInstaller = byPath.get(deviceInstallerPath);
  if (deviceInstaller !== undefined) {
    if (!containsAll(deviceInstaller, [
      'prepare_device_state_dirs()',
      'YFEIEYE_DEVICE_STATE_ROOT',
      'mkdir -p "$state_root/logs" "$state_root/node-logs"',
      'chmod 0777 "$state_root/logs" "$state_root/node-logs"',
      'prepare_device_state_dirs',
    ])) {
      blockers.push({
        path: deviceInstallerPath,
        group: releaseGroupFor(deviceInstallerPath),
        reason: 'device_state_directory_preflight_missing',
      });
    }
    if (lifecycleFailureIsSwallowed(deviceInstaller, [
      'start_services',
      'restart_services',
      'update_services',
    ], 'compose_up_detached')) {
      blockers.push({
        path: deviceInstallerPath,
        group: releaseGroupFor(deviceInstallerPath),
        reason: 'device_compose_lifecycle_failure_swallowed',
      });
    }
    const restartBodies = ['restart_services', 'restart_service']
      .map((name) => shellFunctionBody(deviceInstaller, name))
      .filter(Boolean);
    if (restartBodies.some((body) => (
      !body.includes('compose_up_detached --force-recreate')
      || body.includes('$DOCKER_COMPOSE restart')
      || body.includes('device_compose restart')
    ))) {
      blockers.push({
        path: deviceInstallerPath,
        group: releaseGroupFor(deviceInstallerPath),
        reason: 'device_restart_does_not_recreate_state_mounts',
      });
    }
  }

  const middlewareComposePath = '.scripts/docker/docker-compose.yml';
  const middlewareCompose = byPath.get(middlewareComposePath);
  if (middlewareCompose !== undefined) {
    const usesSharedRoot = MIDDLEWARE_SHARED_STATE_MOUNTS.every(
      (mount) => middlewareCompose.includes(`\${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}${mount}`),
    );
    const hasReleaseBoundState = /(?:^|\n)\s*-\s*["']?(?:\.\.?\/)?(?:standalone-logs|db_data|taos_data|redis_data|mq_data|minio_data|milvus_data|srs_data|nodered_data|gpustack_data|\.\.\/zlmediakit)\//m.test(
      middlewareCompose,
    );
    if (!usesSharedRoot || hasReleaseBoundState) {
      blockers.push({
        path: middlewareComposePath,
        group: releaseGroupFor(middlewareComposePath),
        reason: 'middleware_state_mounts_release_bound',
      });
    }
  }

  const middlewareEnvPath = '.scripts/docker/env.example';
  const middlewareEnv = byPath.get(middlewareEnvPath);
  if (middlewareEnv !== undefined
      && !containsAll(middlewareEnv, [
        'YFEIEYE_DOCKER_DATA_ROOT=/opt/yfeieye-source/shared/docker',
        'YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT=false',
      ])) {
    blockers.push({
      path: middlewareEnvPath,
      group: releaseGroupFor(middlewareEnvPath),
      reason: 'middleware_state_root_env_missing',
    });
  }

  const aiComposePath = 'AI/docker-compose.yaml';
  const aiCompose = byPath.get(aiComposePath);
  if (aiCompose !== undefined) {
    const usesSharedRoot = AI_SHARED_STATE_MOUNTS.every(
      (mount) => aiCompose.includes(`\${YFEIEYE_AI_STATE_ROOT:-/opt/yfeieye-source/shared/ai}${mount}`),
    );
    const hasReleaseBoundState = /(?:^|\n)\s*-\s*["']?\.\/(?:data|static|temp_uploads|model):\/app\//m.test(aiCompose);
    if (!usesSharedRoot || hasReleaseBoundState) {
      blockers.push({
        path: aiComposePath,
        group: releaseGroupFor(aiComposePath),
        reason: 'ai_state_mounts_release_bound',
      });
    }
  }

  const aiEnvPath = 'AI/env.example';
  const aiEnv = byPath.get(aiEnvPath);
  if (aiEnv !== undefined
      && !aiEnv.includes('YFEIEYE_AI_STATE_ROOT=/opt/yfeieye-source/shared/ai')) {
    blockers.push({
      path: aiEnvPath,
      group: releaseGroupFor(aiEnvPath),
      reason: 'ai_state_root_env_missing',
    });
  }

  const middlewareInstallerPath = '.scripts/docker/install_middleware_linux.sh';
  const middlewareInstaller = byPath.get(middlewareInstallerPath);
  if (middlewareInstaller !== undefined) {
    const hasRawComposeInvocation = middlewareInstaller.split(/\r?\n/).some(
      (line) => /^\s*\$COMPOSE_CMD\b/.test(line)
        && line.includes('-f "$COMPOSE_FILE"')
        && !line.includes('--env-file "$MIDDLEWARE_ENV_FILE"'),
    );
    if (hasRawComposeInvocation || !containsAll(middlewareInstaller, [
      'MIDDLEWARE_ENV_FILE=',
      '$COMPOSE_CMD --env-file "$MIDDLEWARE_ENV_FILE" -f "$COMPOSE_FILE"',
    ])) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'middleware_compose_env_file_not_explicit',
      });
    }
    if (!containsAll(middlewareInstaller, [
      'prepare-srs-config)',
      'prepare_srs_config --config-only',
    ])) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'middleware_prepare_srs_config_command_missing',
      });
    }
    if (!containsAll(middlewareInstaller, [
      'ensure_nacos_data_ready()',
      'YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT',
      'find "$nacos_data_dir" -mindepth 1 -print -quit',
      'ensure_nacos_data_ready || exit 1',
    ])) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'middleware_nacos_empty_data_preflight_missing',
      });
    }
    const middlewareRestartBody = shellFunctionBody(middlewareInstaller, 'restart_middleware');
    if (middlewareRestartBody && (
      !middlewareRestartBody.includes('mw_compose up -d --force-recreate')
      || middlewareRestartBody.includes('mw_compose restart')
      || middlewareRestartBody.includes('mw_compose down')
      || middlewareRestartBody.includes('--remove-orphans')
    )) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'middleware_restart_does_not_recreate_state_mounts',
      });
    }
    const middlewareUpdateBody = shellFunctionBody(middlewareInstaller, 'update_middleware');
    if (middlewareUpdateBody && (
      !middlewareUpdateBody.includes('compose_up_middleware --force-recreate')
      || !middlewareUpdateBody.includes('return "${_up_rc}"')
    )) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'middleware_update_does_not_recreate_state_mounts',
      });
    }
    const middlewareComposeUpBody = shellFunctionBody(
      middlewareInstaller,
      'compose_up_middleware',
    );
    const repairCanReclassifyComposeFailure = /if\s+\[[^\n]*_repair_rc[^\n]*\][^\n]*_up_rc[^\n]*;?\s*then[\s\S]*?_up_rc\s*=\s*0/.test(
      middlewareComposeUpBody,
    );
    const composeFailureCapturedWithoutReturn = ['install_middleware', 'start_middleware']
      .map((name) => shellFunctionBody(middlewareInstaller, name))
      .filter(Boolean)
      .some((body) => body.includes('compose_up_middleware')
        && body.includes('_up_rc')
        && !/return\s+["']?\$\{?_up_rc\}?["']?/.test(body));
    if (repairCanReclassifyComposeFailure
        || composeFailureCapturedWithoutReturn
        || pipedComposeStopFailureIsSwallowed(
          middlewareInstaller, 'stop_middleware', 'mw_compose')
        || lifecycleFailureIsSwallowed(middlewareInstaller, [
      'install_middleware',
      'start_middleware',
      'restart_middleware',
      'update_middleware',
    ], 'mw_compose')) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'middleware_compose_lifecycle_failure_swallowed',
      });
    }
  }

  for (const path of [
    'AI/install_linux.sh',
    'AI/install_linux_arm.sh',
    'AI/install_linux_kylin.sh',
  ]) {
    const installer = byPath.get(path);
    if (installer === undefined) continue;
    const hasRawComposeInvocation = installer.split(/\r?\n/).some(
      (line) => /^\s*\$COMPOSE_CMD\b/.test(line)
        && !line.includes('--env-file "$AI_COMPOSE_ENV_FILE"'),
    );
    if (hasRawComposeInvocation || !containsAll(installer, [
      'AI_COMPOSE_ENV_FILE=',
      '$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml"',
      'ai_compose up',
    ])) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'ai_compose_env_file_not_explicit',
      });
    }
    if (!containsAll(installer, [
      'mkdir -p "${state_root}/logs/app"',
      'mkdir -p "${state_root}/logs/services"',
    ])) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'ai_log_state_directories_missing',
      });
    }
    const restartBody = shellFunctionBody(installer, 'restart_service');
    if (restartBody && !restartBody.includes('ai_compose up -d --force-recreate')) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'ai_restart_does_not_recreate_state_mounts',
      });
    }
    if (lifecycleDoesNotForceRecreate(installer, 'update_service', 'ai_compose')) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'ai_update_does_not_recreate_state_mounts',
      });
    }
    if (lifecycleFailureIsSwallowed(installer, [
      'install_service',
      'start_service',
      'stop_service',
      'restart_service',
      'update_service',
    ], 'ai_compose')) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'ai_compose_lifecycle_failure_swallowed',
      });
    }
  }

  for (const path of [
    'VIDEO/install_linux.sh',
    'VIDEO/install_linux_arm.sh',
    'VIDEO/install_linux_kylin.sh',
  ]) {
    const installer = byPath.get(path);
    if (installer === undefined) continue;
    const hasRawComposeInvocation = installer.split(/\r?\n/).some(
      (line) => /^\s*\$COMPOSE_CMD\b/.test(line)
        && !line.includes('--env-file "$VIDEO_COMPOSE_ENV_FILE"'),
    );
    if (hasRawComposeInvocation || !containsAll(installer, [
      'VIDEO_COMPOSE_ENV_FILE=',
      '$COMPOSE_CMD --env-file "$VIDEO_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml"',
      'video_compose up',
    ])) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'video_compose_env_file_not_explicit',
      });
    }
    const restartBody = shellFunctionBody(installer, 'restart_service');
    if (restartBody && (
      !restartBody.includes('video_compose up -d --force-recreate')
      || restartBody.includes('video_compose restart')
    )) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'video_restart_does_not_recreate_state_mounts',
      });
    }
    if (lifecycleDoesNotForceRecreate(installer, 'update_service', 'video_compose')) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'video_update_does_not_recreate_state_mounts',
      });
    }
    if (lifecycleFailureIsSwallowed(installer, [
      'install_service',
      'start_service',
      'restart_service',
      'update_service',
    ], 'video_compose')) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason: 'video_compose_lifecycle_failure_swallowed',
      });
    }
  }

  return { ok: blockers.length === 0, blockers };
}

function extractNginxBucketLocation(content) {
  const match = /location\s+(?:\^~\s+)?\/api\/v1\/buckets\/?\s*\{/.exec(content);
  if (!match) {
    return null;
  }
  const start = match.index + match[0].lastIndexOf('{');
  let depth = 0;
  for (let index = start; index < content.length; index += 1) {
    if (content[index] === '{') {
      depth += 1;
    } else if (content[index] === '}') {
      depth -= 1;
      if (depth === 0) {
        return content.slice(start, index + 1);
      }
    }
  }
  return null;
}

export function scanVideoIntegrationConfigGate(files) {
  const path = 'DEVICE/docker-compose.yml';
  const compose = files.find((file) => normalizePath(file.path || '') === path);
  const blockers = [];
  const videoComposePath = 'VIDEO/docker-compose.yaml';
  const videoCompose = files.find((file) => normalizePath(file.path || '') === videoComposePath);
  const videoMigrationRunnerPath = 'VIDEO/apply_migrations.py';
  const videoMigrationRunner = files.find((file) => normalizePath(file.path || '') === videoMigrationRunnerPath);
  const videoSchemaBootstrapPath = 'VIDEO/bootstrap_schema.py';
  const videoSchemaBootstrap = files.find((file) => normalizePath(file.path || '') === videoSchemaBootstrapPath);
  const videoSchemaCoordinatorPath = 'VIDEO/prepare_database.py';
  const videoSchemaCoordinator = files.find((file) => normalizePath(file.path || '') === videoSchemaCoordinatorPath);
  const videoSchemaLockPath = 'VIDEO/schema_lock.py';
  const videoSchemaLock = files.find((file) => normalizePath(file.path || '') === videoSchemaLockPath);
  const videoEnvLoaderPath = 'VIDEO/app/utils/video_env.py';
  const videoEnvLoader = files.find((file) => normalizePath(file.path || '') === videoEnvLoaderPath);
  const videoCameraPath = 'VIDEO/app/blueprints/camera.py';
  const videoCamera = files.find((file) => normalizePath(file.path || '') === videoCameraPath);
  const middlewareInstallerPath = '.scripts/docker/install_middleware_linux.sh';
  const middlewareInstaller = files.find(
    (file) => normalizePath(file.path || '') === middlewareInstallerPath,
  );
  const middlewareEnvPath = '.scripts/docker/env.example';
  const middlewareEnv = files.find((file) => normalizePath(file.path || '') === middlewareEnvPath);
  const alternateHookPath = 'VIDEO/app/blueprints/media_hook.py';
  const alternateHook = files.find((file) => normalizePath(file.path || '') === alternateHookPath);
  const clusterSrsTemplatePath = '.scripts/media-cluster/srs/cluster.conf.template';
  const clusterSrsTemplate = files.find(
    (file) => normalizePath(file.path || '') === clusterSrsTemplatePath,
  );
  const clusterZlmTemplatePath = '.scripts/media-cluster/zlm/config.ini.template';
  const clusterZlmTemplate = files.find(
    (file) => normalizePath(file.path || '') === clusterZlmTemplatePath,
  );
  const clusterInstallerPath = '.scripts/media-cluster/install_media_stack.sh';
  const clusterInstaller = files.find(
    (file) => normalizePath(file.path || '') === clusterInstallerPath,
  );
  const clusterEnablePath = '.scripts/media-cluster/enable_cluster_mode.sh';
  const clusterEnable = files.find(
    (file) => normalizePath(file.path || '') === clusterEnablePath,
  );
  const clusterComposePath = '.scripts/media-cluster/docker-compose.media-node.yml';
  const clusterCompose = files.find(
    (file) => normalizePath(file.path || '') === clusterComposePath,
  );
  if (videoSchemaBootstrap && !containsAll(String(videoSchemaBootstrap.content ?? ''), [
    'fresh-install baseline',
    'db.create_all()',
    'SQLALCHEMY_DATABASE_URI',
    'DATABASE_URL is required',
  ])) {
    blockers.push({
      path: videoSchemaBootstrapPath,
      group: releaseGroupFor(videoSchemaBootstrapPath),
      reason: 'video_fresh_install_schema_bootstrap_incomplete',
    });
  }
  if (videoSchemaCoordinator && !containsAll(String(videoSchemaCoordinator.content ?? ''), [
    'production_schema_lock',
    'bootstrap_schema(database_url)',
    'apply_migrations(database_url, plan, acquire_lock=False)',
  ])) {
    blockers.push({
      path: videoSchemaCoordinatorPath,
      group: releaseGroupFor(videoSchemaCoordinatorPath),
      reason: 'video_schema_coordinator_incomplete',
    });
  }
  if (videoSchemaLock && !containsAll(String(videoSchemaLock.content ?? ''), [
    'pg_try_advisory_lock',
    'SET statement_timeout',
    'SET lock_timeout',
    'TimeoutError',
    'pg_advisory_unlock',
  ])) {
    blockers.push({
      path: videoSchemaLockPath,
      group: releaseGroupFor(videoSchemaLockPath),
      reason: 'video_schema_lock_not_bounded',
    });
  }
  const videoEnvExamplePath = 'VIDEO/env.example';
  const videoEnvExample = files.find((file) => normalizePath(file.path || '') === videoEnvExamplePath);
  if (videoEnvExample && !containsAll(String(videoEnvExample.content ?? ''), [
    'YFEIEYE_MEDIA_AUTHORIZATION_URL',
    'YFEIEYE_MEDIA_SERVICE_HMAC_KEYS',
    'YFEIEYE_MEDIA_SERVICE_POLICIES',
    'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET',
    'YFEIEYE_MEDIA_SERVICE_IDS',
    'YFEIEYE_MEDIA_SERVICE_ALLOWED_ACTIONS',
    'YFEIEYE_MEDIA_SERVICE_ALLOWED_CAMERA_IDS',
    'YFEIEYE_MEDIA_ACCESS_AUDIT_DIR',
    'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
    'YFEIEYE_RECORD_EXPORT_ACTIVE_KEY_ID',
    'YFEIEYE_RECORD_EXPORT_STORAGE_TYPE=minio',
    'YFEIEYE_RECORD_EXPORT_S3_ENDPOINT',
    'YFEIEYE_RECORD_EXPORT_S3_ACCESS_KEY',
    'YFEIEYE_RECORD_EXPORT_S3_SECRET_KEY',
    'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_DIR',
    'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_TTL_SECONDS',
    'YFEIEYE_SEEKABLE_PLAYBACK_CACHE_MAX_BYTES',
    'YFEIEYE_SEEKABLE_PLAYBACK_LOCK_STALE_SECONDS',
    'YFEIEYE_SEEKABLE_PLAYBACK_LOCK_HEARTBEAT_SECONDS',
    'YFEIEYE_SEEKABLE_PLAYBACK_READ_LEASE_STALE_SECONDS',
    'YFEIEYE_SEEKABLE_PLAYBACK_MAX_OUTPUT_BYTES',
    'YFEIEYE_RECORD_CACHE_EVENT_RETENTION_HOURS',
    'YFEIEYE_RECORD_CACHE_EVENT_MAX_FILES',
    'YFEIEYE_RECORD_DRIFT_LOOKBACK_HOURS',
    'YFEIEYE_FFMPEG_MAX_CONCURRENT',
    'YFEIEYE_FFMPEG_SLOT_WAIT_SECONDS',
    'YFEIEYE_FFMPEG_THREADS',
    'YFEIEYE_FFMPEG_FILTER_THREADS',
    'YFEIEYE_FFMPEG_TIMEOUT_BASE_SECONDS',
    'YFEIEYE_FFMPEG_TIMEOUT_PER_MEDIA_SECOND',
    'YFEIEYE_FFMPEG_TIMEOUT_MAX_SECONDS',
    'YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES',
    'YFEIEYE_RECORD_EXPORT_STORE_MAX_BYTES',
    'YFEIEYE_RECORD_EXPORT_TEMP_DIR',
    'YFEIEYE_RECORD_EXPORT_TEMP_MAX_BYTES',
    'YFEIEYE_RECORD_EXPORT_ORPHAN_TTL_SECONDS',
    'YFEIEYE_VIDEO_SCHEMA_LOCK_WAIT_SECONDS',
    'YFEIEYE_VIDEO_SCHEMA_STATEMENT_TIMEOUT_MS',
    'YFEIEYE_VIDEO_SCHEMA_DB_LOCK_TIMEOUT_MS',
    'YFEIEYE_SRS_HOOK_TOKEN',
    'FLASK_RUN_HOST=172.17.0.1',
  ])) {
    blockers.push({
      path: videoEnvExamplePath,
      group: releaseGroupFor(videoEnvExamplePath),
      reason: 'video_production_security_env_contract_missing',
    });
  }
  if (videoEnvLoader && !containsAll(String(videoEnvLoader.content ?? ''), [
    'authorize_srs_hook_token',
    'YFEIEYE_SRS_HOOK_TOKEN',
    'hmac.compare_digest',
    '_SRS_HOOK_TOKEN_RE',
    'fullmatch',
    'at least 32 bytes in production',
  ])) {
    blockers.push({
      path: videoEnvLoaderPath,
      group: releaseGroupFor(videoEnvLoaderPath),
      reason: 'srs_hook_runtime_authorization_missing',
    });
  }
  if (videoCamera) {
    const cameraContent = String(videoCamera.content ?? '');
    const authorizationCallCount = (cameraContent.match(/authorize_srs_hook_token\(/g) || []).length;
    if (authorizationCallCount < 2 || !cameraContent.includes("request.args.get('hook_token')")) {
      blockers.push({
        path: videoCameraPath,
        group: releaseGroupFor(videoCameraPath),
        reason: 'srs_hook_route_authorization_missing',
      });
    }
  }
  if (middlewareInstaller) {
    const installerContent = String(middlewareInstaller.content ?? '');
    if (!containsAll(installerContent, [
      'resolve_srs_hook_token()',
      '?hook_token=${srs_hook_token}',
      'resolve_video_callback_host',
      'chmod 600 "$srs_config_file"',
    ]) || installerContent.includes('/admin-api/video/camera/callback/')) {
      blockers.push({
        path: middlewareInstallerPath,
        group: releaseGroupFor(middlewareInstallerPath),
        reason: 'srs_hook_installer_contract_missing',
      });
    }
  }
  if (middlewareEnv && !containsAll(String(middlewareEnv.content ?? ''), [
    'VIDEO_CALLBACK_HOST=172.17.0.1',
    'YFEIEYE_SRS_HOOK_TOKEN=',
  ])) {
    blockers.push({
      path: middlewareEnvPath,
      group: releaseGroupFor(middlewareEnvPath),
      reason: 'srs_hook_middleware_env_contract_missing',
    });
  }
  if (alternateHook) {
    const hookContent = String(alternateHook.content ?? '');
    const authorizationCallCount = (hookContent.match(/_require_internal_hook_token\(/g) || []).length;
    if (authorizationCallCount < 6 || !containsAll(hookContent, [
      'authorize_srs_hook_token',
      "request.headers.get('X-YFeiEye-Hook-Token')",
      "@media_hook_bp.route('/hook/srs/on_dvr'",
      "@media_hook_bp.route('/hook/srs/on_publish'",
      "@media_hook_bp.route('/hook/srs/on_unpublish'",
      "@media_hook_bp.route('/hook/snap/completed'",
      "@media_hook_bp.route('/hook/zlm/on_record_mp4'",
      "@media_hook_bp.route('/hook/zlm/on_record_ts'",
    ])) {
      blockers.push({
        path: alternateHookPath,
        group: releaseGroupFor(alternateHookPath),
        reason: 'alternate_media_hook_authorization_missing',
      });
    }
  }
  if (clusterSrsTemplate) {
    const content = String(clusterSrsTemplate.content ?? '');
    if ((content.match(/\?hook_token=\$\{YFEIEYE_SRS_HOOK_TOKEN\}/g) || []).length < 3) {
      blockers.push({
        path: clusterSrsTemplatePath,
        group: releaseGroupFor(clusterSrsTemplatePath),
        reason: 'cluster_srs_hook_token_missing',
      });
    }
  }
  if (clusterZlmTemplate) {
    const content = String(clusterZlmTemplate.content ?? '');
    if ((content.match(/\?hook_token=\$\{YFEIEYE_SRS_HOOK_TOKEN\}/g) || []).length < 3) {
      blockers.push({
        path: clusterZlmTemplatePath,
        group: releaseGroupFor(clusterZlmTemplatePath),
        reason: 'cluster_zlm_hook_token_missing',
      });
    }
  }
  if (clusterInstaller) {
    const content = String(clusterInstaller.content ?? '');
    const secureConfigCount = (content.match(/chmod 600 "\$\{out\}"/g) || []).length;
    if (secureConfigCount < 2 || !containsAll(content, [
      'validate_hook_token()',
      'YFEIEYE_SRS_HOOK_TOKEN',
      '${YFEIEYE_SRS_HOOK_TOKEN}',
      'MEDIA_HOOK_PORT="${MEDIA_HOOK_PORT:-6000}"',
      'MEDIA_HOOK_PATH_PREFIX="${MEDIA_HOOK_PATH_PREFIX:-}"',
    ]) || content.includes('MEDIA_HOOK_PATH_PREFIX="${MEDIA_HOOK_PATH_PREFIX:-/admin-api}"')) {
      blockers.push({
        path: clusterInstallerPath,
        group: releaseGroupFor(clusterInstallerPath),
        reason: 'cluster_hook_installer_token_missing',
      });
    }
    if (!containsAll(content, [
      'ZLM_SECRET="${ZLM_SECRET:-}"',
      'validate_zlm_secret()',
      'ZLM_SECRET must contain at least 32 characters',
      'ZLM_SECRET must contain only URL-safe characters',
    ]) || content.includes('ZLM_SECRET="${ZLM_SECRET:-yFeiEye_Media_Secret}"')) {
      blockers.push({
        path: clusterInstallerPath,
        group: releaseGroupFor(clusterInstallerPath),
        reason: 'cluster_zlm_secret_contract_missing',
      });
    }
    if ((content.match(/export MEDIA_NODE_ID="\$\{MEDIA_NODE_NAME\}"/g) || []).length < 2) {
      blockers.push({
        path: clusterInstallerPath,
        group: releaseGroupFor(clusterInstallerPath),
        reason: 'cluster_media_compose_base_id_missing',
      });
    }
    const deploySrsStart = content.indexOf('deploy_srs()');
    const deploySrsEnd = content.indexOf('deploy_zlm()', deploySrsStart + 1);
    const deploySrsContent = deploySrsStart >= 0
      ? content.slice(deploySrsStart, deploySrsEnd >= 0 ? deploySrsEnd : undefined)
      : '';
    const renderIndex = deploySrsContent.indexOf('render_srs_config');
    const healthySkipIndex = deploySrsContent.indexOf('if srs_healthy');
    if (!containsAll(content, [
      'SRS_CONFIG_CHANGED=',
      'cmp -s',
      'compose_force_recreate srs',
      '--force-recreate',
    ]) || renderIndex < 0 || healthySkipIndex < 0 || renderIndex > healthySkipIndex) {
      blockers.push({
        path: clusterInstallerPath,
        group: releaseGroupFor(clusterInstallerPath),
        reason: 'cluster_srs_config_refresh_missing',
      });
    }
  }
  if (clusterEnable) {
    const content = String(clusterEnable.content ?? '');
    if (!containsAll(content, [
      'YFEIEYE_SRS_HOOK_TOKEN=',
      'chmod 600 "${ENV_SNIPPET}"',
    ])) {
      blockers.push({
        path: clusterEnablePath,
        group: releaseGroupFor(clusterEnablePath),
        reason: 'cluster_hook_env_template_token_missing',
      });
    }
    if (!containsAll(content, [
      'validate_hook_token()',
      'YFEIEYE_SRS_HOOK_TOKEN must contain at least 32 characters',
      'YFEIEYE_SRS_HOOK_TOKEN must contain only URL-safe characters',
    ])) {
      blockers.push({
        path: clusterEnablePath,
        group: releaseGroupFor(clusterEnablePath),
        reason: 'cluster_hook_env_token_validation_missing',
      });
    }
  }
  if (clusterCompose) {
    const content = String(clusterCompose.content ?? '');
    if (!containsAll(content, [
      'MEDIA_HOOK_PORT=6000',
      'YFEIEYE_SRS_HOOK_TOKEN=',
    ])) {
      blockers.push({
        path: clusterComposePath,
        group: releaseGroupFor(clusterComposePath),
        reason: 'cluster_hook_compose_usage_token_missing',
      });
    }
    if (!containsAll(content, [
      'container_name: "${MEDIA_NODE_ID}-srs"',
      'container_name: "${MEDIA_NODE_ID}-zlm"',
    ])) {
      blockers.push({
        path: clusterComposePath,
        group: releaseGroupFor(clusterComposePath),
        reason: 'cluster_media_container_names_not_distinct',
      });
    }
  }
  if (videoMigrationRunner && !containsAll(String(videoMigrationRunner.content ?? ''), [
    'MIGRATION_FILES',
    'V20260711__device_detection_region_rule_fields.sql',
    'V20260712__record_snapshot_tenant_scope.sql',
    'yfeieye_video_schema_history',
    'acquire_schema_lock',
    'checksum mismatch for migration',
    'strip_outer_transaction',
    'verify_only',
    'YFEIEYE_VIDEO_LEGACY_TENANT_ID',
  ])) {
    blockers.push({
      path: videoMigrationRunnerPath,
      group: releaseGroupFor(videoMigrationRunnerPath),
      reason: 'video_production_migration_runner_incomplete',
    });
  }
  if (videoCompose) {
    const videoComposeContent = String(videoCompose.content ?? '');
    if (!containsAll(videoComposeContent, [
      'FLASK_RUN_HOST=${FLASK_RUN_HOST:?',
      'ALLOWED_HOSTS=${ALLOWED_HOSTS:?',
      'http://$${FLASK_RUN_HOST}:$${FLASK_RUN_PORT:-6000}/actuator/health',
    ]) || /FLASK_RUN_HOST=\$\{FLASK_RUN_HOST:-(?:127\.0\.0\.1|0\.0\.0\.0)\}/.test(
      videoComposeContent,
    )) {
      blockers.push({
        path: videoComposePath,
        group: releaseGroupFor(videoComposePath),
        reason: 'video_bridge_bind_contract_missing',
      });
    }
    if (!videoComposeContent.includes(
      'YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS=${YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS:-300}',
    )) {
      blockers.push({
        path: videoComposePath,
        group: releaseGroupFor(videoComposePath),
        reason: 'video_integration_playback_ticket_window_missing',
      });
    }
    if (!containsAll(videoComposeContent, [
      'python /app/prepare_database.py',
      'python /app/apply_migrations.py --verify-only',
      'exec python /app/run.py',
    ])) {
      blockers.push({
        path: videoComposePath,
        group: releaseGroupFor(videoComposePath),
        reason: 'video_production_migration_entrypoint_missing',
      });
    }
    if (!containsAll(videoComposeContent, [
      'YFEIEYE_FFMPEG_MAX_CONCURRENT=${YFEIEYE_FFMPEG_MAX_CONCURRENT:-1}',
      'YFEIEYE_FFMPEG_SLOT_WAIT_SECONDS=${YFEIEYE_FFMPEG_SLOT_WAIT_SECONDS:-30}',
      'YFEIEYE_FFMPEG_THREADS=${YFEIEYE_FFMPEG_THREADS:-1}',
      'YFEIEYE_FFMPEG_FILTER_THREADS=${YFEIEYE_FFMPEG_FILTER_THREADS:-1}',
      'YFEIEYE_FFMPEG_TIMEOUT_BASE_SECONDS=${YFEIEYE_FFMPEG_TIMEOUT_BASE_SECONDS:-30}',
      'YFEIEYE_FFMPEG_TIMEOUT_PER_MEDIA_SECOND=${YFEIEYE_FFMPEG_TIMEOUT_PER_MEDIA_SECOND:-4}',
      'YFEIEYE_FFMPEG_TIMEOUT_MAX_SECONDS=${YFEIEYE_FFMPEG_TIMEOUT_MAX_SECONDS:-600}',
      'YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES=${YFEIEYE_MEDIA_DISK_MIN_FREE_BYTES:-2147483648}',
      'YFEIEYE_RECORD_EXPORT_STORE_MAX_BYTES=${YFEIEYE_RECORD_EXPORT_STORE_MAX_BYTES:-2147483648}',
      'YFEIEYE_RECORD_EXPORT_TEMP_DIR=/data/yfeieye-record-exports/tmp',
      'YFEIEYE_RECORD_EXPORT_TEMP_MAX_BYTES=${YFEIEYE_RECORD_EXPORT_TEMP_MAX_BYTES:-1073741824}',
      'YFEIEYE_RECORD_EXPORT_ORPHAN_TTL_SECONDS=${YFEIEYE_RECORD_EXPORT_ORPHAN_TTL_SECONDS:-3600}',
      'YFEIEYE_SEEKABLE_PLAYBACK_MAX_OUTPUT_BYTES=${YFEIEYE_SEEKABLE_PLAYBACK_MAX_OUTPUT_BYTES:-1073741824}',
    ])) {
      blockers.push({
        path: videoComposePath,
        group: releaseGroupFor(videoComposePath),
        reason: 'video_resource_control_compose_wiring_missing',
      });
    }
    if (/YFEIEYE_RECORD_EXPORT_(?:HMAC_KEYS|ACTIVE_KEY_ID)=\$\{[^}]*:-\}/.test(videoComposeContent)
        || /YFEIEYE_MEDIA_SERVICE_(?:HMAC_KEYS|POLICIES)=\$\{[^}]*:-\}/.test(videoComposeContent)) {
      blockers.push({
        path: videoComposePath,
        group: releaseGroupFor(videoComposePath),
        reason: 'video_production_secret_env_file_overridden',
      });
    }
  }
  if (!compose) return { ok: blockers.length === 0, blockers };
  const content = String(compose.content ?? '');
  if (!content.includes(
    '${YFEIEYE_VIDEO_STATE_ROOT:-/data/yfeieye-video}/alert_images:/app/alert_images',
  ) || content.includes('../VIDEO/alert_images:/app/alert_images')) {
    blockers.push({
      path,
      group: releaseGroupFor(path),
      reason: 'alert_image_shared_state_mount_missing',
    });
  }
  if (content.includes('YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ADMIN_USER_IDS=')) {
    blockers.push({
      path,
      group: releaseGroupFor(path),
      reason: 'runtime_outbox_notify_global_admin_fallback_forbidden',
    });
  }
  if (!containsAll(content, [
    'YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ENABLED=${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ENABLED:-false}',
    'YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES=${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES:-}',
    'YFEIEYE_REVIEW_RUNTIME_ALERT_TEMPLATE_CODE=${YFEIEYE_REVIEW_RUNTIME_ALERT_TEMPLATE_CODE:-YFEIEYE_REVIEW_RUNTIME_ALERT}',
    'YFEIEYE_REVIEW_OPERATIONS_REPORT_TEMPLATE_CODE=${YFEIEYE_REVIEW_OPERATIONS_REPORT_TEMPLATE_CODE:-YFEIEYE_REVIEW_OPERATIONS_REPORT}',
  ])) {
    blockers.push({
      path,
      group: releaseGroupFor(path),
      reason: 'runtime_outbox_notify_compose_wiring_missing',
    });
  }
  const defaults = new Map();
  for (const name of [
    'YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL',
    'YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL',
    'YFEIEYE_VIDEO_RECORD_BASE_URL',
    'YFEIEYE_VIDEO_RECORD_EXPORT_URL',
  ]) {
    const match = content.match(new RegExp(`${name}=\\$\\{${name}:-([^}\\s]+)\\}`));
    defaults.set(name, match?.[1] || '');
    if (!match?.[1]) {
      blockers.push({ path, group: releaseGroupFor(path), reason: `video_integration_${name.toLowerCase()}_missing` });
    }
  }
  const alertUrl = defaults.get('YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL');
  const coverageUrl = defaults.get('YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL');
  if (alertUrl && !alertUrl.includes('/video/alert/record/query')) {
    blockers.push({ path, group: releaseGroupFor(path), reason: 'video_integration_alert_query_default_invalid' });
  }
  if (coverageUrl && !coverageUrl.includes('/video/record/availability')) {
    blockers.push({ path, group: releaseGroupFor(path), reason: 'video_integration_coverage_default_invalid' });
  }
  if (alertUrl && coverageUrl && alertUrl === coverageUrl) {
    blockers.push({ path, group: releaseGroupFor(path), reason: 'video_integration_alert_coverage_defaults_aliased' });
  }
  if (!content.includes('YFEIEYE_MEDIA_SERVICE_HMAC_SECRET=${YFEIEYE_MEDIA_SERVICE_HMAC_SECRET:-}')) {
    blockers.push({ path, group: releaseGroupFor(path), reason: 'video_integration_hmac_secret_wiring_missing' });
  }
  if (!content.includes('YFEIEYE_VIDEO_PUBLIC_PLAY_HOST=${YFEIEYE_VIDEO_PUBLIC_PLAY_HOST:-}')) {
    blockers.push({ path, group: releaseGroupFor(path), reason: 'video_integration_public_play_host_wiring_missing' });
  }
  return { ok: blockers.length === 0, blockers };
}

export function scanLiveVideoEvidenceGate(files) {
  const blockers = [];
  const liveVideo = files.find((file) => normalizePath(file.path || '') === '.scripts/alert-review-video-live-smoke.mjs');
  const deviceSmoke = files.find((file) => normalizePath(file.path || '') === '.scripts/alert-review-device-integration-smoke.mjs');
  const playerSmoke = files.find((file) => normalizePath(file.path || '') === '.scripts/alert-review-player-live-smoke.mjs');
  const manifestVerifierWrapper = files.find((file) => normalizePath(file.path || '') === '.scripts/record-export-manifest-verifier.mjs');
  const recordVideoService = files.find((file) => normalizePath(file.path || '') === 'VIDEO/app/services/record_video_service.py');
  if (deviceSmoke && !containsAll(deviceSmoke.content, [
    'const auditChain = await runEvidenceAuditSmoke(options, result, fetchImpl)',
    '/system/supervision/alert-review/evidence-audit',
    "url.searchParams.set('eventId'",
    "url.searchParams.set('reviewCaseId'",
    "url.searchParams.set('reviewItemId'",
    "url.searchParams.set('exportJobNo'",
    "fetchJson(fetchImpl, buildEvidenceAuditUrl(options, result), {\n    method: 'GET'",
    'entries.find((entry) => matchesEvidenceAuditEntry(entry, result))',
    "String(entry.actionType || '') !== 'export_downloaded'",
    'idsEqual(entry.reviewCaseId, result.reviewCaseId)',
    "String(entry.jobNo || '') === result.exportJobNo",
    'idListIncludes(entry.boundEventIds, result.eventId)',
    'idListIncludes(metadata.reviewItemIds, result.reviewItemId)',
    'idListIncludes(metadata.eventIds, result.eventId)',
    "String(metadata.exportJobNo || '') === result.exportJobNo",
    'evidence_audit_chain_verified',
    'review_event_bound_without_task_dispatch',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-device-integration-smoke.mjs'),
      reason: 'device_smoke_evidence_audit_chain_missing',
    });
  }
  if (recordVideoService && recordVideoService.content.includes('_normalize_gap_reason') && !containsAll(recordVideoService.content, [
    '_normalize_gap_reason_token',
    'isalnum',
    "normalized.strip('_')",
    "'file_expired': 'retention_expired'",
    "'retention_expired': 'retention'",
    "'video_url_not_configured': 'configuration'",
    "'record_space_not_found': 'configuration'",
    "'file_missing': 'filesystem'",
    "'probe_failed': 'probe'",
    "'permission_denied': 'permission'",
    "'disk_full': 'storage'",
    "'cache_flush_failed': 'cache'",
  ])) {
    blockers.push({
      path: 'VIDEO/app/services/record_video_service.py',
      group: releaseGroupFor('VIDEO/app/services/record_video_service.py'),
      reason: 'video_record_gap_reason_catalog_missing',
    });
  }
  if (liveVideo && /\brecordCoverageQueryUrl\s*=\s*parsed\.alertRecordQueryUrl\b/.test(liveVideo.content)) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_coverage_url_alias_present',
    });
  }
  if (liveVideo && liveVideo.content.includes('requiredOptionErrors') && !containsAll(liveVideo.content, [
    'sameReleaseEndpoint',
    'record coverage URL must not equal alert record query URL',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_coverage_url_runtime_alias_guard_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('parseArgs') && !containsAll(liveVideo.content, [
    'token: env.YFEIEYE_VIDEO_SMOKE_TOKEN',
    'VIDEO smoke token must be provided through YFEIEYE_VIDEO_SMOKE_TOKEN',
    '!options.allowLocalEndpoints && !hasText(options.token)',
    'missing YFEIEYE_VIDEO_SMOKE_TOKEN',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_release_token_required_missing',
    });
  }
  if (liveVideo?.content.includes("parsed.token = arg.slice('--token='.length)")) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_cli_token_acceptance_forbidden',
    });
  }
  if (deviceSmoke && deviceSmoke.content.includes('parseArgs') && !containsAll(deviceSmoke.content, [
    'token: env.YFEIEYE_DEVICE_AUTH_TOKEN',
    'DEVICE smoke token must be provided through YFEIEYE_DEVICE_AUTH_TOKEN',
    'missing YFEIEYE_DEVICE_AUTH_TOKEN',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-device-integration-smoke.mjs'),
      reason: 'device_smoke_environment_token_required_missing',
    });
  }
  if (deviceSmoke?.content.includes("parsed.token = arg.slice('--token='.length)")) {
    blockers.push({
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-device-integration-smoke.mjs'),
      reason: 'device_smoke_cli_token_acceptance_forbidden',
    });
  }
  if (deviceSmoke && deviceSmoke.content.includes('parseArgs') && !containsAll(deviceSmoke.content, [
    'function sanitizeCliOutputText',
    'stripUrlSecrets',
    'console.error(sanitizeCliOutputText',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-device-integration-smoke.mjs'),
      reason: 'device_smoke_cli_output_sanitizer_missing',
    });
  }
  if (deviceSmoke && deviceSmoke.content.includes('parseArgs') && !containsAll(deviceSmoke.content, [
    'playbackMaterialUri: env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
    'DEVICE playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
    'sanitizeCliOutputValue',
    'value.map(sanitizeCliOutputValue)',
    'Object.entries(value).map',
    'console.log(JSON.stringify(sanitizeCliOutputValue',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-device-integration-smoke.mjs'),
      reason: 'device_smoke_recursive_output_and_playback_uri_env_guard_missing',
    });
  }
  if (deviceSmoke?.content.includes("parsed.playbackMaterialUri = arg.slice('--playback-material-uri='.length)")) {
    blockers.push({
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-device-integration-smoke.mjs'),
      reason: 'device_smoke_playback_material_uri_argv_acceptance_forbidden',
    });
  }
  if (liveVideo && liveVideo.content.includes('parseArgs') && !containsAll(liveVideo.content, [
    'function sanitizeCliOutputText',
    'stripUrlSecrets',
    'console.error(sanitizeCliOutputText',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_cli_output_sanitizer_missing',
    });
  }
  if (playerSmoke && playerSmoke.content.includes('parseArgs') && !containsAll(playerSmoke.content, [
    'function sanitizeOutputText',
    'stripUrlSecrets',
    'console.error(sanitizeOutputText',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-player-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-player-live-smoke.mjs'),
      reason: 'player_smoke_cli_output_sanitizer_missing',
    });
  }
  if (playerSmoke && playerSmoke.content.includes('parseArgs') && !containsAll(playerSmoke.content, [
    'sanitizeSmokeResultForOutput',
    'sanitizeOutputValue',
    'value.map(sanitizeOutputValue)',
    'Object.entries(value).map',
    'buildBrowserEnvironment',
    'env: buildBrowserEnvironment()',
    'BROWSER_SENSITIVE_ENV_KEYS',
    'player local storage must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
    'player cookies must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
    'signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-player-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-player-live-smoke.mjs'),
      reason: 'player_smoke_recursive_output_and_child_env_guard_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('parseArgs') && !containsAll(liveVideo.content, [
    'playbackMaterialUri: env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
    'VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
    'recordUri: options.playbackMaterialUri',
    'sanitizeCliOutputValue',
    'value.map(sanitizeCliOutputValue)',
    'Object.entries(value).map',
    'buildManifestVerifierEnvironment',
    'env: buildManifestVerifierEnvironment()',
    'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
    'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_recursive_output_and_verifier_env_guard_missing',
    });
  }
  if (manifestVerifierWrapper && !containsAll(manifestVerifierWrapper.content, [
    'buildManifestVerifierEnvironment',
    'env: buildManifestVerifierEnvironment()',
    'ALLOWED_SENSITIVE_ENV_KEYS',
    'UNRELATED_SENSITIVE_ENV_KEYS',
    'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
    'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
    'YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN',
    'YFEIEYE_VIDEO_SMOKE_TOKEN',
  ])) {
    blockers.push({
      path: '.scripts/record-export-manifest-verifier.mjs',
      group: releaseGroupFor('.scripts/record-export-manifest-verifier.mjs'),
      reason: 'manifest_verifier_python_child_env_guard_missing',
    });
  }
  for (const [path, reason] of [
    ['.scripts/alert-review-device-integration-smoke.test.mjs', 'device_smoke_signed_relative_cli_regression_test_missing'],
    ['.scripts/alert-review-video-live-smoke.test.mjs', 'live_video_signed_relative_cli_regression_test_missing'],
    ['.scripts/alert-review-player-live-smoke.test.mjs', 'player_smoke_signed_relative_cli_regression_test_missing'],
    ['.scripts/alert-review-production-smoke.test.mjs', 'production_smoke_signed_relative_cli_regression_test_missing'],
  ]) {
    const smokeTest = files.find((file) => normalizePath(file.path || '') === path);
    if (smokeTest && !containsAll(smokeTest.content, [
      'spawnSync',
      'record.mp4?token=',
      'signedCliFailure.stderr.includes',
    ])) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason,
      });
    }
  }
  for (const [path, anchors, reason] of [
    [
      '.scripts/alert-review-device-integration-smoke.test.mjs',
      ['device-rule-secret', 'device-playback-argv-secret', 'sanitizeCliOutputValue'],
      'device_smoke_recursive_output_and_playback_uri_env_regression_missing',
    ],
    [
      '.scripts/alert-review-player-live-smoke.test.mjs',
      ['native-error-secret', 'export-message-secret', 'rule-secret', 'browserProbe', 'player-argv-storage-secret'],
      'player_smoke_recursive_output_and_real_child_env_regression_missing',
    ],
    [
      '.scripts/alert-review-video-live-smoke.test.mjs',
      ['message-secret', 'rule-secret', 'manifestVerifierChildEnv', 'wrapperProbe', 'playbackOverrideUri'],
      'live_video_recursive_output_and_real_verifier_env_regression_missing',
    ],
    [
      '.scripts/alert-review-production-smoke.test.mjs',
      ['YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI', 'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI', 'sanitizeOutputValue', 'native-error-secret', 'rule-secret'],
      'production_smoke_signed_uri_env_and_recursive_output_regression_missing',
    ],
  ]) {
    const smokeTest = files.find((file) => normalizePath(file.path || '') === path);
    if (smokeTest && !containsAll(smokeTest.content, anchors)) {
      blockers.push({
        path,
        group: releaseGroupFor(path),
        reason,
      });
    }
  }
  if (liveVideo && liveVideo.content.includes('runSmoke') && !containsAll(liveVideo.content, [
    'withBearerAuthorization',
    'const fetchImpl = withBearerAuthorization(rawFetchImpl, options.token)',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_bearer_wrapper_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('selectPlayableSegment') && !containsAll(liveVideo.content, [
    'describeNonPlayableSegments',
    'non_exportable_reason',
    'nonExportableReason',
    'exportable=false',
    'record coverage query returned no playable/exportable record segment',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_non_exportable_reason_summary_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('selectPlayableSegment') && !containsAll(liveVideo.content, [
    'validateCoverageClassification',
    'coverageSummary',
    'retainMode',
    'coverageSource',
    'record coverage query missing retain mode or source classification evidence',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_coverage_classification_evidence_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('validateCoverageClassification') && !containsAll(liveVideo.content, [
    'STANDARD_COVERAGE_CLASSIFICATIONS',
    'continuous',
    'motion',
    'alert',
    'detection',
    'record coverage query returned non-standard retain mode or source classification',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_coverage_classification_catalog_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('validateCoverageClassification') && !containsAll(liveVideo.content, [
    'normalizeCoverageClassification',
    "normalized === 'all'",
    "normalized === 'record'",
    "normalized === 'recording'",
    "return 'continuous'",
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_coverage_classification_normalizer_missing',
    });
  }
  if (liveVideo && !containsAll(liveVideo.content, [
    'validateManifestSignature',
    'isHmacSha256SignatureValue',
    'signature value is not canonical hmac-sha256',
    'manifestSignature',
    'hmac-sha256',
    'signatureVersion',
    'keyId',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_manifest_signature_summary_missing',
    });
  }
  if (liveVideo && !containsAll(liveVideo.content, [
    'runManifestVerifierIfConfigured',
    'manifestVerification',
    'manifestVerifierScript',
    'runManifestVerifierScript',
    'result.status !== 0',
    'verifier failed with exit',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_manifest_verifier_summary_missing',
    });
  }
  if (liveVideo
      && liveVideo.content.includes('spawnSync(process.execPath')
      && liveVideo.content.includes('result.status !== 0')
      && !containsAll(liveVideo.content, [
        'timeout: timeoutMs',
        'ETIMEDOUT',
        'timed out after',
      ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_manifest_verifier_timeout_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('requiredOptionErrors') && !containsAll(liveVideo.content, [
    'manifestVerifierScript',
    'missing --manifest-verifier-script',
    'YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_manifest_verifier_required_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('validateStorageDriftReport') && !containsAll(liveVideo.content, [
    'REQUIRED_STORAGE_DRIFT_REASON_KEYS',
    'storageDriftReasonKeys',
    'standardReasonKeys',
    'missing standard reason evidence',
    'file_missing',
    'retention_expired',
    'disk_full',
    'cache_flush_failed',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_storage_drift_reason_evidence_missing',
    });
  }
  if (liveVideo && liveVideo.content.includes('validateManifestStorageLifecycle') && !containsAll(liveVideo.content, [
    'assertReleaseMediaEvidence(options,',
    'export package storage reference',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_manifest_storage_reference_guard_missing',
    });
  }
  if (liveVideo && !containsAll(liveVideo.content, [
    'assertReleaseMediaEvidence',
    'assertReleaseSegmentMediaEvidence',
    'looksLocalOrMockMediaEvidence',
    'looksInlineOrOpaqueMediaEvidence',
    'looksAbsoluteLocalPathEvidence',
    'data:',
    'blob:',
    'about:',
    'validateDownloadProbeHeaders',
    'isVideoDownloadContentType',
    'content-type',
    'content-length',
    'video/',
    'application/octet-stream',
    'isSha256Digest',
    '[a-f0-9]{64}',
    'invalid source segment hash',
    'invalid output file hash',
    'ffmpegCommandHashes',
    'invalid ffmpeg command hash',
    'validateClipWindows',
    'invalid clip window',
    'validateManifestConcatOrder',
    'normalizeConcatOrderEntry',
    'validateRootConcatOrderCoverage',
    'concatOrder.map',
    'entry.index',
    'duplicate concat order index',
    'invalid concat order index',
    'references missing segment index',
    'omits segment index',
    'does not match segment count',
    'raw.startsWith',
    'https:${raw}',
    'mock/',
    'mock\\',
    'recordUriSource',
    'file_path',
    'record URI',
    'download URL',
    'manifest URL',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-video-live-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-video-live-smoke.mjs'),
      reason: 'live_video_media_evidence_gate_missing',
    });
  }
  const productionSmoke = files.find((file) => normalizePath(file.path || '') === '.scripts/alert-review-production-smoke.mjs');
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand')
      && !containsAll(productionSmoke.content, [
        'YFEIEYE_DEVICE_AUTH_TOKEN: options.token',
        'YFEIEYE_VIDEO_SMOKE_TOKEN: options.token',
        'buildStepEnvironment',
      ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_live_video_token_wiring_missing',
    });
  }
  if (productionSmoke?.content.includes('--token=${options.token}')) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_token_argv_wiring_forbidden',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand') && !containsAll(productionSmoke.content, [
    'tokenSource',
    "parsed.tokenSource = 'cli'",
    "options.tokenSource === 'cli'",
    'cliTokenAllowedForLocalEndpoints',
    'isExplicitLocalOrMockEndpoint',
    '--token requires --allow-local-endpoints and local/mock endpoints only',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_release_token_source_gate_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand')) {
    const explicitEndpointClassifier = productionSmoke.content.match(
      /function isExplicitLocalOrMockEndpoint\([^)]*\)\s*\{[\s\S]*?\r?\n\}/,
    )?.[0] || productionSmoke.content;
    if (!containsAll(explicitEndpointClassifier, [
      "hostname.endsWith('.localhost')",
      "hostname.endsWith('.test')",
      "hostname.endsWith('.invalid')",
    ]) || [
      "hostname.includes('mock')",
      "includes('/mock')",
    ].some((anchor) => explicitEndpointClassifier.includes(anchor))) {
      blockers.push({
        path: '.scripts/alert-review-production-smoke.mjs',
        group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
        reason: 'production_smoke_release_token_endpoint_classification_unsafe',
      });
    }
  }
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand') && !containsAll(productionSmoke.content, [
    'YFEIEYE_REVIEW_PLAYER_SMOKE_URL: options.playerWorkbenchUrl',
    "'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',",
    'CHILD_SENSITIVE_ENV_KEYS',
    'playerLocalStorage: env.YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
    'playerCookies: env.YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
    'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: options.playerLocalStorage',
    'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: options.playerCookies',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_player_auth_environment_isolation_missing',
    });
  }
  if (productionSmoke?.content.includes('--workbench-url=${options.playerWorkbenchUrl}')) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_player_url_argv_wiring_forbidden',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand') && !containsAll(productionSmoke.content, [
    'devicePlaybackMaterialUri: env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
    'videoPlaybackMaterialUri: env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
    'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: options.devicePlaybackMaterialUri',
    'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: options.videoPlaybackMaterialUri',
    "'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',",
    "'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',",
    'device playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
    'VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
    'signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
    'sanitizeOutputValue',
    'value.map(sanitizeOutputValue)',
    'Object.entries(value).map',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_signed_uri_env_and_recursive_output_guard_missing',
    });
  }
  if (productionSmoke?.content.includes('--playback-material-uri=${options.')) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_playback_material_uri_argv_wiring_forbidden',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand') && !containsAll(productionSmoke.content, [
    'function sanitizeChildOutputForDisplay',
    'stripUrlSecrets',
    'console.error(sanitizeChildOutputForDisplay',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_cli_output_sanitizer_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('formatStepCommand') && !containsAll(productionSmoke.content, [
    'maskSensitiveArg',
    "value.startsWith('--token=')",
    "return '--token=***'",
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_token_log_mask_missing',
    });
  }
  if (productionSmoke && !containsAll(productionSmoke.content, [
    'payload.manifestSignature',
    'summary.manifestSignature',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_signature_summary_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'buildManifestSignatureSummary(payload.manifestSignature)',
    "copyTextIfPresent(manifestSignature, source, 'algorithm')",
    "copyTextIfPresent(manifestSignature, source, 'keyId')",
    "copyTextIfPresent(manifestSignature, source, 'signatureVersion')",
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_signature_whitelist_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'payload.manifestStorageLifecycle',
    'summary.manifestStorageLifecycle',
    'summary.manifestStorageLifecycle?.status',
    'missing persisted manifest storage lifecycle evidence',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_storage_lifecycle_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'buildManifestStorageLifecycleSummary(payload.manifestStorageLifecycle)',
    "copyTextIfPresent(manifestStorageLifecycle, source, 'storageType')",
    "copyTextIfPresent(manifestStorageLifecycle, source, 'status')",
    "copyTextIfPresent(manifestStorageLifecycle, source, 'expiresAt')",
    "copyTextIfPresent(manifestStorageLifecycle, source, 'exportPackageObjectKey')",
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_storage_lifecycle_whitelist_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'payload.coverageSummary',
    'summary.coverageSummary',
    'buildCoverageSummary(payload.coverageSummary)',
    "copyTextIfPresent(coverage, source, 'retainMode')",
    "copyTextIfPresent(coverage, source, 'coverageSource')",
    'missing coverage retain/source evidence',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_coverage_classification_summary_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'REQUIRED_STORAGE_DRIFT_REASON_KEYS',
    'summary.storageDriftSummary?.standardReasonKeys',
    'missing standard storage drift reason evidence',
    'file_missing',
    'retention_expired',
    'disk_full',
    'cache_flush_failed',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_storage_drift_reason_evidence_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'buildStorageDriftSummary(payload.storageDriftSummary)',
    "copyBooleanIfPresent(storageDriftSummary, source, 'healthy')",
    "copyNumberIfPresent(storageDriftSummary, source, 'recordCount')",
    "copyNumberIfPresent(storageDriftSummary, source, 'issueCount')",
    'issueReasons',
    'standardReasonKeys',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_storage_drift_summary_whitelist_missing',
    });
  }
  if (productionSmoke && !containsAll(productionSmoke.content, [
    'payload.manifestVerification',
    'summary.manifestVerification',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_verifier_summary_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'buildManifestVerificationSummary(payload.manifestVerification)',
    "copyBooleanIfPresent(manifestVerification, source, 'valid')",
    "copyBooleanIfPresent(manifestVerification, source, 'signatureValid')",
    "copyBooleanIfPresent(manifestVerification, source, 'signatureKeyAvailable')",
    "copyTextIfPresent(manifestVerification, source, 'keyId')",
    "copyTextIfPresent(manifestVerification, source, 'signatureVersion')",
    'violations',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_verification_whitelist_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'buildExportResultSummary(payload.exportResult)',
    "copySanitizedUrlIfPresent(exportResult, source, 'downloadUrl')",
    "copySanitizedUrlIfPresent(exportResult, source, 'manifestUrl')",
    'stripUrlSecrets',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_export_result_sanitizer_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    "copyTextIfPresent(exportResult, source, 'exportId')",
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_export_result_whitelist_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('const exportResult = { ...source }')) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_export_result_raw_spread_present',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'summary.manifestVerification?.valid',
    'missing valid manifest verifier evidence',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_verifier_evidence_required_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('liveVideoEvidenceError') && !containsAll(productionSmoke.content, [
    'summary.manifestVerification.signatureValid',
    'summary.manifestVerification.signatureKeyAvailable',
    'missing HMAC manifest verifier signature evidence',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_verifier_signature_evidence_missing',
    });
  }
  if (productionSmoke && !containsAll(productionSmoke.content, [
    'videoManifestVerifierScript',
    'missing --video-manifest-verifier-script',
    'YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_manifest_verifier_required_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('requiredOptionErrors') && !containsAll(productionSmoke.content, [
    'evidenceOutputFile',
    'missing --evidence-output-file',
    'YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_evidence_output_required_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('requiredOptionErrors') && !containsAll(productionSmoke.content, [
    'stepTimeoutMs',
    '--step-timeout-ms',
    'YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS',
    'timeout: step.timeoutMs',
    'summary.timeout',
    'timed out after',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_step_timeout_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('requiredOptionErrors') && !productionSmoke.content.includes('--timeout-ms=${options.stepTimeoutMs}')) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_child_timeout_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('W2:typecheck') && !containsAll(productionSmoke.content, [
    '--pm-on-fail=ignore',
    'pnpm_version_guard',
    'typecheckRetry',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_typecheck_pnpm_guard_retry_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('buildSmokeSteps') && !containsAll(productionSmoke.content, [
    'W4:visible-copy',
    'alert-review-visible-copy-scan.mjs',
    'visible-copy files for replacement characters',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_visible_copy_preflight_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('playerSmokeStep') && !containsAll(productionSmoke.content, [
    '--assert-native-current-time',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_player_native_time_assert_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('livePlayerEvidenceError') && !containsAll(productionSmoke.content, [
    'Number.isFinite(player.nativeCurrentTime)',
    'missing native currentTime evidence',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_player_native_time_evidence_missing',
    });
  }
  if (productionSmoke && productionSmoke.content.includes('livePlayerEvidenceError') && !containsAll(productionSmoke.content, [
    "copySanitizedUrlIfPresent(player, payload, 'recordPath')",
    'stripUrlSecrets',
  ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_player_record_path_sanitizer_missing',
    });
  }
  const productionSmokeHasLiveDeviceEvidenceGate = productionSmoke?.content.includes('liveDeviceEvidenceError') === true;
  const productionSmokeMissingAuditChainGate = productionSmokeHasLiveDeviceEvidenceGate && !containsAll(productionSmoke.content, [
    'auditChain',
    'eventIds',
    'reviewItemIds',
    'evidence_download_audited',
    'evidence_audit_chain_verified',
    'export_downloaded',
    "copyPositiveIdIfPresent(summary, payload, 'eventId')",
    "copyPositiveIdListIfPresent(summary, payload, 'eventIds')",
    'normalizePositiveId',
    "Object.hasOwn(payload, 'eventIds')",
    'explicitEmptyEventIds',
    'missing eventId evidence',
    'missing eventIds evidence',
    'eventIds do not exactly match eventId',
    'auditChain reviewCaseId does not match reviewCaseId',
    'auditChain reviewItemIds do not exactly match reviewItemId',
    'auditChain eventIds do not exactly match eventId',
    'auditChain exportJobNo does not match exportJobNo',
    'missing auditChain exportJobNo evidence',
  ]);
  if (productionSmokeMissingAuditChainGate) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_audit_chain_summary_missing',
    });
  }
  if (productionSmokeHasLiveDeviceEvidenceGate
      && !productionSmokeMissingAuditChainGate
      && !containsAll(productionSmoke.content, [
        'firstAuditScalar',
        'resolveExactAuditIdList',
        'normalizeAuditIdList',
        'normalizeAuditScalar',
      ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_audit_chain_scalar_whitelist_missing',
    });
  }
  const productionSmokeMissingPlaybackAccessGate = productionSmokeHasLiveDeviceEvidenceGate
      && !productionSmokeMissingAuditChainGate
      && !containsAll(productionSmoke.content, [
    'liveDevicePlaybackEvidenceError',
    'summary.playback',
    'grantedDecision',
    'deniedDecision',
    'camera_not_allowed',
    'missing playback URL allow/deny decision evidence',
    'missing playback URL deny reason evidence',
  ]);
  if (productionSmokeMissingPlaybackAccessGate) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_playback_access_evidence_missing',
    });
  }
  const productionSmokeMissingRuleSemanticsGate = productionSmokeHasLiveDeviceEvidenceGate
      && !productionSmokeMissingAuditChainGate
      && !productionSmokeMissingPlaybackAccessGate
      && !containsAll(productionSmoke.content, [
        'liveDeviceRuleEvidenceError',
        'summary.ruleEvidence',
        'inertiaFrames',
        'loiteringSeconds',
        'missing rule inertia/loitering evidence',
        'missing rule inertiaFrames=3 evidence',
        'missing rule loiteringSeconds=20 evidence',
      ]);
  if (productionSmokeHasLiveDeviceEvidenceGate
      && !productionSmokeMissingAuditChainGate
      && !productionSmokeMissingPlaybackAccessGate
      && !productionSmokeMissingRuleSemanticsGate
      && !containsAll(productionSmoke.content, [
        'buildPlaybackAccessSummary(payload.playback)',
        "copyTextIfPresent(playback, source, 'grantedDecision')",
        "copyTextIfPresent(playback, source, 'deniedDecision')",
        'deniedReasons',
      ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_playback_summary_whitelist_missing',
    });
  }
  if (productionSmokeHasLiveDeviceEvidenceGate
      && !productionSmokeMissingAuditChainGate
      && !productionSmokeMissingPlaybackAccessGate
      && !productionSmokeMissingRuleSemanticsGate
      && !containsAll(productionSmoke.content, [
        'buildRuleEvidenceSummary(payload)',
        "copyTextIfPresent(ruleEvidence, source, 'ruleCode')",
        "copyTextIfPresent(ruleEvidence, source, 'cameraId')",
        "copyTextIfPresent(ruleEvidence, source, 'zoneCode')",
        "copyTextIfPresent(ruleEvidence, source, 'objectLabel')",
        "copyNumberIfPresent(ruleEvidence, source, 'inertiaFrames')",
        "copyNumberIfPresent(ruleEvidence, source, 'loiteringSeconds')",
      ])) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_rule_evidence_whitelist_missing',
    });
  }
  if (productionSmokeMissingRuleSemanticsGate) {
    blockers.push({
      path: '.scripts/alert-review-production-smoke.mjs',
      group: releaseGroupFor('.scripts/alert-review-production-smoke.mjs'),
      reason: 'production_smoke_rule_semantics_evidence_missing',
    });
  }
  return {
    ok: blockers.length === 0,
    blockers,
  };
}

export function scanReleaseTraceabilityGate(files) {
  const docPath = 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md';
  const doc = files.find((file) => normalizePath(file.path || '') === docPath);
  if (!doc) {
    return { ok: true, blockers: [] };
  }
  const content = String(doc.content ?? '');
  const prodSmokeIndex = content.indexOf('Production smoke with real VIDEO URLs');
  if (prodSmokeIndex === -1) {
    return { ok: true, blockers: [] };
  }
  const prodSmokeBlock = releaseGateBlock(content, prodSmokeIndex);
  const blockers = [];
  const checks = [
    ['--device-base-url=', 'fr38_prod_smoke_device_base_command_missing'],
    ['YFEIEYE_DEVICE_AUTH_TOKEN', 'fr38_prod_smoke_token_env_missing'],
    ['--tenant-id=', 'fr38_prod_smoke_tenant_command_missing'],
    ['--operator-user-id=', 'fr38_prod_smoke_operator_command_missing'],
    ['--device-alert-time=', 'fr38_prod_smoke_device_alert_time_command_missing'],
    ['--device-playback-allowed-camera-ids=', 'fr38_prod_smoke_playback_allow_command_missing'],
    ['--device-playback-denied-camera-ids=', 'fr38_prod_smoke_playback_deny_command_missing'],
    ['YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI', 'fr38_prod_smoke_device_playback_material_env_missing'],
    ['--video-alert-record-query-url=', 'fr38_prod_smoke_alert_record_command_missing'],
    ['--video-record-coverage-query-url=', 'fr38_prod_smoke_coverage_command_missing'],
    ['--video-record-base-url=', 'fr38_prod_smoke_record_base_command_missing'],
    ['--video-record-export-url=', 'fr38_prod_smoke_export_command_missing'],
    ['--video-device-id=', 'fr38_prod_smoke_video_device_command_missing'],
    ['--video-alert-time=', 'fr38_prod_smoke_video_alert_time_command_missing'],
    ['--video-record-drift-retention-hours=', 'fr38_prod_smoke_drift_retention_command_missing'],
    ['--video-manifest-verifier-script=', 'fr38_prod_smoke_manifest_verifier_command_missing'],
    ['YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI', 'fr38_prod_smoke_video_playback_material_env_missing'],
    ['--step-timeout-ms=', 'fr38_prod_smoke_step_timeout_command_missing'],
    ['YFEIEYE_REVIEW_PLAYER_SMOKE_URL', 'fr38_prod_smoke_player_workbench_env_missing'],
    ['--player-review-row-text=', 'fr38_prod_smoke_player_review_row_command_missing'],
    ['--player-expected-seek-time=', 'fr38_prod_smoke_detail_player_command_missing'],
    ['--player-expected-record-path-contains=', 'fr38_prod_smoke_detail_player_record_command_missing'],
    ['--player-expected-offset-seconds=', 'fr38_prod_smoke_detail_player_offset_command_missing'],
    ['--player-coverage-expected-seek-time=', 'fr38_prod_smoke_coverage_player_command_missing'],
    ['--player-coverage-expected-record-path-contains=', 'fr38_prod_smoke_coverage_player_record_command_missing'],
    ['--player-coverage-expected-offset-seconds=', 'fr38_prod_smoke_coverage_player_offset_command_missing'],
    ['--player-case-timeline-expected-seek-time=', 'fr38_prod_smoke_case_timeline_player_command_missing'],
    ['--player-case-timeline-expected-record-path-contains=', 'fr38_prod_smoke_case_timeline_player_record_command_missing'],
    ['--player-case-timeline-expected-offset-seconds=', 'fr38_prod_smoke_case_timeline_player_offset_command_missing'],
    ['--evidence-output-file=', 'fr38_prod_smoke_evidence_file_command_missing'],
  ];
  for (const [fragment, reason] of checks) {
    if (!prodSmokeBlock.includes(fragment)) {
      blockers.push({
        path: docPath,
        group: releaseGroupFor(docPath),
        reason,
        line: _lineNumberAt(content, prodSmokeIndex),
      });
    }
  }
  for (const [fragment, reason] of [
    ['--device-playback-material-uri=', 'fr38_prod_smoke_device_playback_material_argv_forbidden'],
    ['--video-playback-material-uri=', 'fr38_prod_smoke_video_playback_material_argv_forbidden'],
  ]) {
    if (prodSmokeBlock.includes(fragment)) {
      blockers.push({
        path: docPath,
        group: releaseGroupFor(docPath),
        reason,
        line: _lineNumberAt(content, prodSmokeIndex),
      });
    }
  }
  return {
    ok: blockers.length === 0,
    blockers,
  };
}

function releaseGateBlock(content, startIndex) {
  const rest = content.slice(startIndex);
  const nextHeading = rest.slice(1).search(/\n#{2,3} /);
  if (nextHeading === -1) {
    return rest;
  }
  return rest.slice(0, nextHeading + 1);
}

function _lineNumberAt(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

function _looksBinaryPath(path) {
  return /\.(png|jpe?g|gif|webp|ico|mp4|mov|avi|zip|gz|tar|7z|pdf)$/i.test(path);
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== '';
}

function containsAll(content, fragments) {
  const text = String(content ?? '');
  return fragments.every((fragment) => text.includes(fragment));
}

function readStatusText(args) {
  const statusFileArg = args.find((arg) => arg.startsWith('--status-file='));
  if (statusFileArg) {
    return readFileSync(statusFileArg.slice('--status-file='.length), 'utf8');
  }
  return execFileSync('git', ['status', '--short', '--untracked-files=all'], {
    encoding: 'utf8',
  });
}

function readTrackedReleaseEntries() {
  const output = execFileSync('git', ['ls-files', '--', ...TRACKED_RELEASE_PATHS], {
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  });
  return releaseEntriesForTrackedPaths(output.split(/\r?\n/));
}

function mergeEntries(primary, secondary) {
  const seen = new Set(primary.map((entry) => entry.path));
  const merged = [...primary];
  for (const entry of secondary) {
    if (seen.has(entry.path)) {
      continue;
    }
    seen.add(entry.path);
    merged.push(entry);
  }
  return merged;
}

function readReleaseTextFiles(entries) {
  const uniquePaths = [...new Set(entries.map((entry) => entry.path))];
  const files = [];
  for (const path of uniquePaths) {
    if (_looksBinaryPath(path) || !existsSync(path)) {
      continue;
    }
    try {
      if (statSync(path).isFile()) {
        files.push({ path, content: readFileSync(path, 'utf8') });
      }
    } catch {
      // Status blockers will catch deleted or inaccessible files.
    }
  }
  return files;
}

function printHelp() {
  console.log(`Usage: node .scripts/verify-alert-review-release-package.mjs [--require-clean] [--status-file=PATH]

Checks that FR-01..FR-38 alert-review core files are not loose in the worktree.
Default mode allows staged FR changes for pre-commit packaging review.
Use --require-clean for a release artifact that must come only from HEAD.`);
}

function runCli() {
  const args = process.argv.slice(2);
  if (args.includes('--help') || args.includes('-h')) {
    printHelp();
    return;
  }

  const result = evaluateStatus(readStatusText(args), {
    requireClean: args.includes('--require-clean'),
  });
  const entriesForTextScan = args.includes('--require-clean')
    ? mergeEntries(result.entries, readTrackedReleaseEntries())
    : result.entries;
  const releaseTextFiles = readReleaseTextFiles(entriesForTextScan);
  const textResult = scanTextQuality(releaseTextFiles);
  const webTypecheckResult = scanWebTypecheckGate(releaseTextFiles);
  const mediaPermissionResult = scanMediaPermissionGate(releaseTextFiles);
  const rawMinioProxyResult = scanRawMinioProxyGate(releaseTextFiles);
  const statefulReleaseMountResult = scanStatefulReleaseMountGate(releaseTextFiles);
  const videoIntegrationConfigResult = scanVideoIntegrationConfigGate(releaseTextFiles);
  const liveVideoEvidenceResult = scanLiveVideoEvidenceGate(releaseTextFiles);
  const traceabilityResult = scanReleaseTraceabilityGate(releaseTextFiles);
  result.blockers.push(...textResult.blockers);
  result.blockers.push(...webTypecheckResult.blockers);
  result.blockers.push(...mediaPermissionResult.blockers);
  result.blockers.push(...rawMinioProxyResult.blockers);
  result.blockers.push(...statefulReleaseMountResult.blockers);
  result.blockers.push(...videoIntegrationConfigResult.blockers);
  result.blockers.push(...liveVideoEvidenceResult.blockers);
  result.blockers.push(...traceabilityResult.blockers);
  result.ok = result.blockers.length === 0;

  if (!result.ok) {
    console.error('Alert review release package verifier failed:');
    for (const blocker of result.blockers) {
      const status = blocker.status ? `${blocker.status} ` : '';
      const line = blocker.line ? `:${blocker.line}` : '';
      console.error(`- ${blocker.reason}: ${status}${blocker.path}${line} (${blocker.group})`);
    }
    process.exitCode = 1;
    return;
  }

  console.log(
    `Alert review release package verifier OK: ${entriesForTextScan.length} FR release path(s) checked; no loose FR core file blocked packaging.`,
  );
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli();
}
