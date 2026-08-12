import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import * as releasePackageVerifier from './verify-alert-review-release-package.mjs';

const {
  evaluateStatus,
  releaseEntriesForTrackedPaths,
  scanLiveVideoEvidenceGate,
  scanMediaPermissionGate,
  scanRawMinioProxyGate,
  scanReleaseTraceabilityGate,
  scanStatefulReleaseMountGate,
  scanVideoIntegrationConfigGate,
  scanWebTypecheckGate,
  scanTextQuality,
} = releasePackageVerifier;

const releaseBoundStateScan = scanStatefulReleaseMountGate([
  {
    path: '.scripts/docker/docker-compose.yml',
    content: [
      './standalone-logs:/home/nacos/logs',
      './db_data/data:/var/lib/postgresql/data',
      './taos_data/data:/var/lib/taos',
      './milvus_data:/var/lib/milvus',
      './nodered_data/data:/data',
      '../zlmediakit/conf:/conf',
    ].join('\n'),
  },
  {
    path: 'AI/docker-compose.yaml',
    content: [
      './data:/app/data',
      './static:/app/static',
      './temp_uploads:/app/temp_uploads',
      './model:/app/model',
    ].join('\n'),
  },
  {
    path: '.scripts/docker/install_middleware_linux.sh',
    content: [
      'MIDDLEWARE_ENV_FILE=',
      '$COMPOSE_CMD --env-file "$MIDDLEWARE_ENV_FILE" -f "$COMPOSE_FILE" "$@"',
      '$COMPOSE_CMD -f "$COMPOSE_FILE" up -d',
    ].join('\n'),
  },
  {
    path: 'AI/install_linux.sh',
    content: 'AI_COMPOSE_ENV_FILE=\n$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"\nai_compose up\n$COMPOSE_CMD up -d',
  },
  {
    path: 'AI/install_linux_arm.sh',
    content: 'AI_COMPOSE_ENV_FILE=\n$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"\nai_compose up\n$COMPOSE_CMD up -d',
  },
  {
    path: 'AI/install_linux_kylin.sh',
    content: 'AI_COMPOSE_ENV_FILE=\n$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"\nai_compose up\n$COMPOSE_CMD up -d',
  },
  {
    path: 'VIDEO/install_linux.sh',
    content: 'VIDEO_COMPOSE_ENV_FILE=\n$COMPOSE_CMD --env-file "$VIDEO_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"\nvideo_compose up\n$COMPOSE_CMD up -d',
  },
  {
    path: 'VIDEO/install_linux_arm.sh',
    content: 'VIDEO_COMPOSE_ENV_FILE=\n$COMPOSE_CMD --env-file "$VIDEO_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"\nvideo_compose up\n$COMPOSE_CMD up -d',
  },
  {
    path: 'VIDEO/install_linux_kylin.sh',
    content: 'VIDEO_COMPOSE_ENV_FILE=\n$COMPOSE_CMD --env-file "$VIDEO_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"\nvideo_compose up\n$COMPOSE_CMD up -d',
  },
]);
assert.equal(releaseBoundStateScan.ok, false);
assert.deepEqual(releaseBoundStateScan.blockers.map((blocker) => blocker.reason), [
  'middleware_state_mounts_release_bound',
  'ai_state_mounts_release_bound',
  'middleware_compose_env_file_not_explicit',
  'middleware_prepare_srs_config_command_missing',
  'middleware_nacos_empty_data_preflight_missing',
  'ai_compose_env_file_not_explicit',
  'ai_log_state_directories_missing',
  'ai_compose_env_file_not_explicit',
  'ai_log_state_directories_missing',
  'ai_compose_env_file_not_explicit',
  'ai_log_state_directories_missing',
  'video_compose_env_file_not_explicit',
  'video_compose_env_file_not_explicit',
  'video_compose_env_file_not_explicit',
]);

const releaseBoundDeviceStateScan = scanStatefulReleaseMountGate([
  {
    path: 'DEVICE/docker-compose.yml',
    content: [
      '../.build-cache/device/logs:/root/logs/',
      '../.build-cache/device/node-logs:/root/logs/',
      '../VIDEO/alert_images:/app/alert_images',
    ].join('\n'),
  },
  {
    path: 'DEVICE/install_linux.sh',
    content: [
      'device_compose() {',
      '  $DOCKER_COMPOSE -f "$COMPOSE_FILE" "$@"',
      '}',
      'start_services() {',
      '  compose_up_detached | grep Started || true',
      '}',
      'restart_services() {',
      '  $DOCKER_COMPOSE restart',
      '}',
    ].join('\n'),
  },
]);
assert.equal(releaseBoundDeviceStateScan.ok, false);
assert.deepEqual(releaseBoundDeviceStateScan.blockers.map((blocker) => blocker.reason), [
  'device_state_mounts_release_bound',
  'device_state_directory_preflight_missing',
  'device_compose_lifecycle_failure_swallowed',
  'device_restart_does_not_recreate_state_mounts',
]);

const missingNacosDataMountScan = scanStatefulReleaseMountGate([{
  path: '.scripts/docker/docker-compose.yml',
  content: [
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/nacos_data/logs:/home/nacos/logs',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/db_data/data:/var/lib/postgresql/data',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/db_data/log:/var/log/postgresql',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/taos_data/data:/var/lib/taos',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/redis_data/data:/data',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/mq_data/data:/var/lib/kafka/data',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/minio_data/data:/data',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/milvus_data:/var/lib/milvus',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/srs_data/conf:/usr/local/srs/conf',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/nodered_data/data:/data',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/zlmediakit/conf:/conf',
    '${YFEIEYE_DOCKER_DATA_ROOT:-/opt/yfeieye-source/shared/docker}/gpustack_data:/var/lib/gpustack',
  ].join('\n'),
}]);
assert.equal(missingNacosDataMountScan.ok, false);
assert.deepEqual(missingNacosDataMountScan.blockers.map((blocker) => blocker.reason), [
  'middleware_state_mounts_release_bound',
]);

const missingNacosEmptyDataPreflightScan = scanStatefulReleaseMountGate([{
  path: '.scripts/docker/install_middleware_linux.sh',
  content: [
    'MIDDLEWARE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$MIDDLEWARE_ENV_FILE" -f "$COMPOSE_FILE" "$@"',
    'prepare-srs-config)',
    'prepare_srs_config --config-only',
  ].join('\n'),
}]);
assert.equal(missingNacosEmptyDataPreflightScan.ok, false);
assert.deepEqual(missingNacosEmptyDataPreflightScan.blockers.map((blocker) => blocker.reason), [
  'middleware_nacos_empty_data_preflight_missing',
]);

const missingAiLogMountScan = scanStatefulReleaseMountGate([{
  path: 'AI/docker-compose.yaml',
  content: [
    '${YFEIEYE_AI_STATE_ROOT:-/opt/yfeieye-source/shared/ai}/data:/app/data',
    '${YFEIEYE_AI_STATE_ROOT:-/opt/yfeieye-source/shared/ai}/static:/app/static',
    '${YFEIEYE_AI_STATE_ROOT:-/opt/yfeieye-source/shared/ai}/temp_uploads:/app/temp_uploads',
    '${YFEIEYE_AI_STATE_ROOT:-/opt/yfeieye-source/shared/ai}/model:/app/model',
  ].join('\n'),
}]);
assert.equal(missingAiLogMountScan.ok, false);
assert.deepEqual(missingAiLogMountScan.blockers.map((blocker) => blocker.reason), [
  'ai_state_mounts_release_bound',
]);

const missingAiLogDirectoryScan = scanStatefulReleaseMountGate([{
  path: 'AI/install_linux.sh',
  content: [
    'AI_COMPOSE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"',
    'ai_compose up',
  ].join('\n'),
}]);
assert.equal(missingAiLogDirectoryScan.ok, false);
assert.deepEqual(missingAiLogDirectoryScan.blockers.map((blocker) => blocker.reason), [
  'ai_log_state_directories_missing',
]);

const staleAiRestartMountScan = scanStatefulReleaseMountGate([{
  path: 'AI/install_linux_kylin.sh',
  content: [
    'AI_COMPOSE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"',
    'ai_compose up',
    'mkdir -p "${state_root}/logs/app"',
    'mkdir -p "${state_root}/logs/services"',
    'restart_service() {',
    '  ai_compose restart',
    '}',
  ].join('\n'),
}]);
assert.equal(staleAiRestartMountScan.ok, false);
assert.deepEqual(staleAiRestartMountScan.blockers.map((blocker) => blocker.reason), [
  'ai_restart_does_not_recreate_state_mounts',
]);

const staleMiddlewareRestartMountScan = scanStatefulReleaseMountGate([{
  path: '.scripts/docker/install_middleware_linux.sh',
  content: [
    'MIDDLEWARE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$MIDDLEWARE_ENV_FILE" -f "$COMPOSE_FILE" "$@"',
    'prepare-srs-config)',
    'prepare_srs_config --config-only',
    'ensure_nacos_data_ready()',
    'YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT',
    'find "$nacos_data_dir" -mindepth 1 -print -quit',
    'ensure_nacos_data_ready || exit 1',
    'restart_middleware() {',
    '  mw_compose restart',
    '}',
  ].join('\n'),
}]);
assert.equal(staleMiddlewareRestartMountScan.ok, false);
assert.deepEqual(staleMiddlewareRestartMountScan.blockers.map((blocker) => blocker.reason), [
  'middleware_restart_does_not_recreate_state_mounts',
]);

const swallowedAiLifecycleFailureScan = scanStatefulReleaseMountGate([{
  path: 'AI/install_linux.sh',
  content: [
    'AI_COMPOSE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"',
    'ai_compose up',
    'mkdir -p "${state_root}/logs/app"',
    'mkdir -p "${state_root}/logs/services"',
    'install_service() {',
    '  ai_compose up -d || true',
    '}',
  ].join('\n'),
}]);
assert.equal(swallowedAiLifecycleFailureScan.ok, false);
assert.deepEqual(swallowedAiLifecycleFailureScan.blockers.map((blocker) => blocker.reason), [
  'ai_compose_lifecycle_failure_swallowed',
]);

const swallowedMiddlewareStopFailureScan = scanStatefulReleaseMountGate([{
  path: '.scripts/docker/install_middleware_linux.sh',
  content: [
    'MIDDLEWARE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$MIDDLEWARE_ENV_FILE" -f "$COMPOSE_FILE" "$@"',
    'prepare-srs-config)',
    'prepare_srs_config --config-only',
    'ensure_nacos_data_ready()',
    'YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT',
    'find "$nacos_data_dir" -mindepth 1 -print -quit',
    'ensure_nacos_data_ready || exit 1',
    'stop_middleware() {',
    '  mw_compose down 2>&1 | tee -a "$LOG_FILE"',
    '  print_success "stopped"',
    '}',
  ].join('\n'),
}]);
assert.equal(swallowedMiddlewareStopFailureScan.ok, false);
assert.deepEqual(swallowedMiddlewareStopFailureScan.blockers.map((blocker) => blocker.reason), [
  'middleware_compose_lifecycle_failure_swallowed',
]);

const swallowedAiStopFailureScan = scanStatefulReleaseMountGate([{
  path: 'AI/install_linux.sh',
  content: [
    'AI_COMPOSE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$AI_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"',
    'ai_compose up',
    'mkdir -p "${state_root}/logs/app"',
    'mkdir -p "${state_root}/logs/services"',
    'stop_service() {',
    '  ai_compose down --remove-orphans 2>&1 | grep -v noisy || true',
    '  print_success "stopped"',
    '}',
  ].join('\n'),
}]);
assert.equal(swallowedAiStopFailureScan.ok, false);
assert.deepEqual(swallowedAiStopFailureScan.blockers.map((blocker) => blocker.reason), [
  'ai_compose_lifecycle_failure_swallowed',
]);

const repairedMiddlewareComposeFailureScan = scanStatefulReleaseMountGate([{
  path: '.scripts/docker/install_middleware_linux.sh',
  content: [
    'MIDDLEWARE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$MIDDLEWARE_ENV_FILE" -f "$COMPOSE_FILE" "$@"',
    'prepare-srs-config)',
    'prepare_srs_config --config-only',
    'ensure_nacos_data_ready()',
    'YFEIEYE_NACOS_ALLOW_EMPTY_DATA_INIT',
    'find "$nacos_data_dir" -mindepth 1 -print -quit',
    'ensure_nacos_data_ready || exit 1',
    'compose_up_middleware() {',
    '  mw_compose up -d',
    '  _up_rc=$?',
    '  _repair_created_middleware_containers',
    '  _repair_rc=$?',
    '  if [ "$_repair_rc" -eq 0 ] && [ "$_up_rc" -ne 0 ]; then',
    '    _up_rc=0',
    '  fi',
    '  return "$_up_rc"',
    '}',
    'install_middleware() {',
    '  if compose_up_middleware; then _up_rc=0; else _up_rc=$?; fi',
    '  run_best_effort_diagnostics || true',
    '}',
    'start_middleware() {',
    '  if compose_up_middleware; then _up_rc=0; else _up_rc=$?; fi',
    '  run_best_effort_diagnostics || true',
    '}',
    'restart_middleware() {',
    '  mw_compose up -d --force-recreate',
    '}',
  ].join('\n'),
}]);
assert.equal(repairedMiddlewareComposeFailureScan.ok, false);
assert.deepEqual(repairedMiddlewareComposeFailureScan.blockers.map((blocker) => blocker.reason), [
  'middleware_compose_lifecycle_failure_swallowed',
]);

const staleVideoRestartMountScan = scanStatefulReleaseMountGate([{
  path: 'VIDEO/install_linux_kylin.sh',
  content: [
    'VIDEO_COMPOSE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$VIDEO_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"',
    'video_compose up',
    'restart_service() {',
    '  video_compose restart',
    '}',
  ].join('\n'),
}]);
assert.equal(staleVideoRestartMountScan.ok, false);
assert.deepEqual(staleVideoRestartMountScan.blockers.map((blocker) => blocker.reason), [
  'video_restart_does_not_recreate_state_mounts',
]);

const swallowedVideoLifecycleFailureScan = scanStatefulReleaseMountGate([{
  path: 'VIDEO/install_linux_kylin.sh',
  content: [
    'VIDEO_COMPOSE_ENV_FILE=',
    '$COMPOSE_CMD --env-file "$VIDEO_COMPOSE_ENV_FILE" -f "${SCRIPT_DIR}/docker-compose.yaml" "$@"',
    'video_compose up',
    'install_service() {',
    '  video_compose up -d || true',
    '}',
  ].join('\n'),
}]);
assert.equal(swallowedVideoLifecycleFailureScan.ok, false);
assert.deepEqual(swallowedVideoLifecycleFailureScan.blockers.map((blocker) => blocker.reason), [
  'video_compose_lifecycle_failure_swallowed',
]);

const currentStatefulReleaseFiles = [
  'DEVICE/docker-compose.yml',
  'DEVICE/install_linux.sh',
  '.scripts/docker/docker-compose.yml',
  '.scripts/docker/env.example',
  '.scripts/docker/install_middleware_linux.sh',
  'AI/docker-compose.yaml',
  'AI/env.example',
  'AI/install_linux.sh',
  'AI/install_linux_arm.sh',
  'AI/install_linux_kylin.sh',
  'VIDEO/install_linux.sh',
  'VIDEO/install_linux_arm.sh',
  'VIDEO/install_linux_kylin.sh',
].map((path) => ({ path, content: readFileSync(path, 'utf8') }));
const currentStatefulReleaseScan = scanStatefulReleaseMountGate(currentStatefulReleaseFiles);
assert.equal(
  currentStatefulReleaseScan.ok,
  true,
  `current stateful release contract failed: ${JSON.stringify(currentStatefulReleaseScan.blockers)}`,
);

const clean = evaluateStatus(`
 M README.md
?? scratch/local-note.txt
`);
assert.equal(clean.ok, true);
assert.deepEqual(clean.blockers, []);

const untrackedBackend = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewServiceImpl.java
`);
assert.equal(untrackedBackend.ok, false);
assert.equal(untrackedBackend.blockers[0].reason, 'untracked');

const unstagedWorkbench = evaluateStatus(`
 M WEB/src/views/alert/components/AlertReviewWorkbench.vue
 M WEB/src/components/VideoPlayer/DialogPlayer.vue
`);
assert.equal(unstagedWorkbench.ok, false);
assert.equal(unstagedWorkbench.blockers[0].reason, 'unstaged');
assert.equal(unstagedWorkbench.blockers[1].group, 'WEB alert review workbench package');

const stagedWorkbench = evaluateStatus(`
A  WEB/src/views/alert/components/AlertReviewWorkbench.vue
M  DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql
`);
assert.equal(stagedWorkbench.ok, true);
assert.deepEqual(stagedWorkbench.blockers, []);

const untrackedGate = evaluateStatus(`
?? .scripts/verify-alert-review-release-package.mjs
?? .scripts/alert-review-visible-copy-scan.mjs
?? .scripts/configure-nginx-stream-secret.mjs
`);
assert.equal(untrackedGate.ok, false);
assert.equal(untrackedGate.blockers[0].group, 'FR release gate tooling');
assert.equal(untrackedGate.blockers[1].group, 'FR release gate tooling');
assert.equal(untrackedGate.blockers[2].group, 'FR release gate tooling');
assert.equal(
  releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(
    '.scripts/configure-nginx-stream-secret.test.mjs',
  ),
  true,
);

const productionMediaDeploymentArtifacts = [
  '.scripts/docker/docker-compose.yml',
  '.scripts/docker/env.example',
  '.scripts/docker/install_middleware_linux.sh',
  '.scripts/docker/upload_minio_data.sh',
  'AI/docker-compose.yaml',
  'VIDEO/test_minio_bucket_policy.py',
];
const untrackedProductionMediaDeployment = evaluateStatus(
  productionMediaDeploymentArtifacts.map((path) => `?? ${path}`).join('\n'),
);
assert.equal(untrackedProductionMediaDeployment.ok, false);
assert.deepEqual(
  untrackedProductionMediaDeployment.blockers.map((entry) => entry.group),
  [
    'FR production media deployment',
    'FR production media deployment',
    'FR production media deployment',
    'FR production media deployment',
    'Protected media raw proxy package',
    'VIDEO record evidence package',
  ],
);
for (const path of productionMediaDeploymentArtifacts) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}
assert.equal(
  evaluateStatus('?? WEB/install_linux.sh').blockers[0]?.group,
  'Protected media raw proxy package',
);
assert.equal(
  releasePackageVerifier.TRACKED_RELEASE_PATHS.includes('WEB/install_linux.sh'),
  true,
);
assert.equal(
  evaluateStatus('?? DEVICE/install_linux.sh').blockers[0]?.group,
  'DEVICE video integration config',
);
assert.equal(
  releasePackageVerifier.TRACKED_RELEASE_PATHS.includes('DEVICE/install_linux.sh'),
  true,
);

const untrackedSegmentDataReconcile = evaluateStatus(`
?? .scripts/alert-review-segment-data-reconcile.mjs
`);
assert.equal(untrackedSegmentDataReconcile.ok, false);
assert.equal(untrackedSegmentDataReconcile.blockers[0].reason, 'untracked');
assert.equal(untrackedSegmentDataReconcile.blockers[0].group, 'FR release gate tooling');

const unstagedSegmentDataReconcileTest = evaluateStatus(`
 M .scripts/alert-review-segment-data-reconcile.test.mjs
`);
assert.equal(unstagedSegmentDataReconcileTest.ok, false);
assert.equal(unstagedSegmentDataReconcileTest.blockers[0].reason, 'unstaged');
assert.equal(unstagedSegmentDataReconcileTest.blockers[0].group, 'FR release gate tooling');

assert.deepEqual(
  releasePackageVerifier.TRACKED_RELEASE_PATHS?.filter((path) => path.includes('alert-review-segment-data-reconcile')),
  [
    '.scripts/alert-review-segment-data-reconcile.mjs',
    '.scripts/alert-review-segment-data-reconcile.test.mjs',
  ],
);

const untrackedBaselineMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql
`);
assert.equal(untrackedBaselineMigration.ok, false);
assert.equal(untrackedBaselineMigration.blockers[0].group, 'DEVICE schema and migration');

const supervisionEventCreatePermissionMigrationPath = 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_3__supervision_event_create_permission.sql';
const untrackedSupervisionEventCreatePermissionMigration = evaluateStatus(`
?? ${supervisionEventCreatePermissionMigrationPath}
`);
assert.equal(untrackedSupervisionEventCreatePermissionMigration.ok, false);
assert.equal(untrackedSupervisionEventCreatePermissionMigration.blockers[0].group, 'DEVICE schema and migration');
assert.equal(
  releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(supervisionEventCreatePermissionMigrationPath),
  true,
);

const localSchedulerOwnershipMigrationPath =
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql';
const untrackedLocalSchedulerOwnershipMigration = evaluateStatus(`
?? ${localSchedulerOwnershipMigrationPath}
`);
assert.equal(untrackedLocalSchedulerOwnershipMigration.ok, false);
assert.equal(
  untrackedLocalSchedulerOwnershipMigration.blockers[0].group,
  'DEVICE schema and migration',
);

const notifyMessageParamsMigrationPath =
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_5__alert_review_notify_message_params_text.sql';
const untrackedNotifyMessageParamsMigration = evaluateStatus(`
?? ${notifyMessageParamsMigrationPath}
`);
assert.equal(untrackedNotifyMessageParamsMigration.ok, false);
assert.equal(
  untrackedNotifyMessageParamsMigration.blockers[0].group,
  'DEVICE schema and migration',
);

const tenantJobRuntimePaths = [
  'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/config/YudaoTenantAutoConfiguration.java',
  'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/core/job/TenantJobAspect.java',
  'DEVICE/iot-common/iot-common-tenant/src/test/java/com/basiclab/iot/common/core/job/TenantJobAspectTest.java',
];
for (const path of tenantJobRuntimePaths) {
  const untrackedTenantJobRuntime = evaluateStatus(`?? ${path}`);
  assert.equal(untrackedTenantJobRuntime.ok, false);
  assert.equal(untrackedTenantJobRuntime.blockers[0].group, 'DEVICE tenant job runtime');
}
for (const path of [
  localSchedulerOwnershipMigrationPath,
  notifyMessageParamsMigrationPath,
  ...tenantJobRuntimePaths,
]) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}

const untrackedVideoTenantMigration = evaluateStatus(`
?? VIDEO/migrations/V20260712__record_snapshot_tenant_scope.sql
`);
assert.equal(untrackedVideoTenantMigration.ok, false);
assert.equal(untrackedVideoTenantMigration.blockers[0].group, 'VIDEO record evidence package');
assert.ok(
  releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(
    'VIDEO/migrations/V20260712__record_snapshot_tenant_scope.sql',
  ),
);

const videoP0TenantArtifacts = [
  'VIDEO/migrations/V20260713__alert_image_playback_tenant_scope.sql',
  'VIDEO/app/blueprints/playback.py',
  'VIDEO/app/services/alert_service.py',
  'VIDEO/app/services/library_matching_service.py',
  'VIDEO/test_alert_media_serialization.py',
  'VIDEO/test_alert_tenant_scope.py',
  'VIDEO/test_playback_media_authorization.py',
];
const untrackedVideoP0TenantArtifacts = evaluateStatus(
  videoP0TenantArtifacts.map((path) => `?? ${path}`).join('\n'),
);
assert.equal(untrackedVideoP0TenantArtifacts.ok, false);
assert.deepEqual(
  untrackedVideoP0TenantArtifacts.blockers.map((entry) => entry.group),
  videoP0TenantArtifacts.map(() => 'VIDEO record evidence package'),
);
for (const path of videoP0TenantArtifacts) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}

const trackedReleaseEntries = releaseEntriesForTrackedPaths([
  'README.md',
  'WEB/src/api/supervision/alertReview.ts',
  'WEB/scripts/alert-review-workbench-e2e-check.test.mjs',
  'WEB/scripts/alert-review-playback-contract.test.mjs',
  'WEB/src/components/VideoPlayer/DialogPlayer.vue',
  'WEB/src/components/Player/module/jessibuca.vue',
  'WEB/src/utils/alertRecord.ts',
  'WEB/src/utils/alertRecordPlayback.ts',
  'WEB/src/utils/withInstall.ts',
  'WEB/src/api/device/patrol.ts',
  '.scripts/verify-alert-review-release-package.mjs',
  '.scripts/record-export-manifest-verifier.mjs',
  '.scripts/alert-review-device-integration-smoke.mjs',
  '.scripts/alert-review-device-integration-smoke.test.mjs',
  '.scripts/alert-review-production-smoke.mjs',
  '.scripts/alert-review-production-smoke.test.mjs',
  '.scripts/alert-review-visible-copy-scan.mjs',
  '.scripts/alert-review-visible-copy-scan.test.mjs',
  '.scripts/alert-review-player-live-smoke.mjs',
  '.scripts/alert-review-player-live-smoke.test.mjs',
  'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/config/YudaoTenantAutoConfiguration.java',
  'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/core/job/TenantJobAspect.java',
  'DEVICE/iot-common/iot-common-tenant/src/test/java/com/basiclab/iot/common/core/job/TenantJobAspectTest.java',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql',
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
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_5__alert_review_notify_message_params_text.sql',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionAlertReviewRuntimeOutboxDeliveryDO.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionAlertReviewRuntimeOutboxDeliveryMapper.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ReviewRuntimeOutboxNotifyDeliveryStore.java',
  'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ReviewRuntimeOutboxNotifyDeliveryMapperStore.java',
  'DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/NotifyReviewRuntimeOutboxPublisherTest.java',
]);
assert.equal(trackedReleaseEntries.length, 47);
assert.deepEqual(
  trackedReleaseEntries.map((entry) => [entry.status, entry.path, entry.group]),
  [
    ['  ', 'WEB/src/api/supervision/alertReview.ts', 'WEB alert review workbench package'],
    ['  ', 'WEB/scripts/alert-review-workbench-e2e-check.test.mjs', 'WEB alert review workbench package'],
    ['  ', 'WEB/scripts/alert-review-playback-contract.test.mjs', 'WEB alert review workbench package'],
    ['  ', 'WEB/src/components/VideoPlayer/DialogPlayer.vue', 'WEB alert review workbench package'],
    ['  ', 'WEB/src/components/Player/module/jessibuca.vue', 'WEB alert review workbench package'],
    ['  ', 'WEB/src/utils/alertRecord.ts', 'WEB alert review workbench package'],
    ['  ', 'WEB/src/utils/alertRecordPlayback.ts', 'WEB alert review workbench package'],
    ['  ', 'WEB/src/utils/withInstall.ts', 'WEB alert review workbench package'],
    ['  ', 'WEB/src/api/device/patrol.ts', 'WEB alert review workbench package'],
    ['  ', '.scripts/verify-alert-review-release-package.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/record-export-manifest-verifier.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-device-integration-smoke.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-device-integration-smoke.test.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-production-smoke.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-production-smoke.test.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-visible-copy-scan.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-visible-copy-scan.test.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-player-live-smoke.mjs', 'FR release gate tooling'],
    ['  ', '.scripts/alert-review-player-live-smoke.test.mjs', 'FR release gate tooling'],
    [
      '  ',
      'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/config/YudaoTenantAutoConfiguration.java',
      'DEVICE tenant job runtime',
    ],
    [
      '  ',
      'DEVICE/iot-common/iot-common-tenant/src/main/java/com/basiclab/iot/common/core/job/TenantJobAspect.java',
      'DEVICE tenant job runtime',
    ],
    [
      '  ',
      'DEVICE/iot-common/iot-common-tenant/src/test/java/com/basiclab/iot/common/core/job/TenantJobAspectTest.java',
      'DEVICE tenant job runtime',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260701__supervision_event_closure_baseline.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260704__alert_review_segment_tenant_scope.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260705__alert_review_review_data_backfill.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260707__alert_review_item_media_audit.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708__alert_review_segment_status_transition.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_2__alert_review_scheduler_jobs.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_3__alert_review_report_ack.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_4__alert_review_runtime_outbox_notify_templates.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_5__alert_review_runtime_outbox_delivery.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_6__alert_review_runtime_outbox_claim.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_7__alert_review_segment_end_time_guard.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_8__alert_review_segment_alert_severity_guard.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_9__alert_review_merge_index_same_camera.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_10__alert_review_deleted_smallint.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260709__alert_review_scheduler_activation.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260710__alert_review_export_queue.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_4__alert_review_local_scheduler_ownership.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713_5__alert_review_notify_message_params_text.sql',
      'DEVICE schema and migration',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/dataobject/supervision/SupervisionAlertReviewRuntimeOutboxDeliveryDO.java',
      'DEVICE review backend',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/dal/pgsql/supervision/SupervisionAlertReviewRuntimeOutboxDeliveryMapper.java',
      'DEVICE review backend',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ReviewRuntimeOutboxNotifyDeliveryStore.java',
      'DEVICE review backend',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/ReviewRuntimeOutboxNotifyDeliveryMapperStore.java',
      'DEVICE review backend',
    ],
    [
      '  ',
      'DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/NotifyReviewRuntimeOutboxPublisherTest.java',
      'DEVICE review regression tests',
    ],
  ],
);

const unstagedTenantMigration = evaluateStatus(`
 M DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260704__alert_review_segment_tenant_scope.sql
`);
assert.equal(unstagedTenantMigration.ok, false);
assert.equal(unstagedTenantMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedReviewDataMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260705__alert_review_review_data_backfill.sql
`);
assert.equal(untrackedReviewDataMigration.ok, false);
assert.equal(untrackedReviewDataMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedMediaPermissionMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql
`);
assert.equal(untrackedMediaPermissionMigration.ok, false);
assert.equal(untrackedMediaPermissionMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedItemMediaAuditMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260707__alert_review_item_media_audit.sql
`);
assert.equal(untrackedItemMediaAuditMigration.ok, false);
assert.equal(untrackedItemMediaAuditMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedSegmentTransitionMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708__alert_review_segment_status_transition.sql
`);
assert.equal(untrackedSegmentTransitionMigration.ok, false);
assert.equal(untrackedSegmentTransitionMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedSchedulerJobsMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_2__alert_review_scheduler_jobs.sql
`);
assert.equal(untrackedSchedulerJobsMigration.ok, false);
assert.equal(untrackedSchedulerJobsMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedReportAckMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_3__alert_review_report_ack.sql
`);
assert.equal(untrackedReportAckMigration.ok, false);
assert.equal(untrackedReportAckMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedRuntimeOutboxNotifyTemplateMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_4__alert_review_runtime_outbox_notify_templates.sql
`);
assert.equal(untrackedRuntimeOutboxNotifyTemplateMigration.ok, false);
assert.equal(untrackedRuntimeOutboxNotifyTemplateMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedRuntimeOutboxDeliveryMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_5__alert_review_runtime_outbox_delivery.sql
`);
assert.equal(untrackedRuntimeOutboxDeliveryMigration.ok, false);
assert.equal(untrackedRuntimeOutboxDeliveryMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedRuntimeOutboxClaimMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_6__alert_review_runtime_outbox_claim.sql
`);
assert.equal(untrackedRuntimeOutboxClaimMigration.ok, false);
assert.equal(untrackedRuntimeOutboxClaimMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedSegmentEndTimeGuardMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_7__alert_review_segment_end_time_guard.sql
`);
assert.equal(untrackedSegmentEndTimeGuardMigration.ok, false);
assert.equal(untrackedSegmentEndTimeGuardMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedSegmentAlertSeverityGuardMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_8__alert_review_segment_alert_severity_guard.sql
`);
assert.equal(untrackedSegmentAlertSeverityGuardMigration.ok, false);
assert.equal(untrackedSegmentAlertSeverityGuardMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedMergeIndexSameCameraMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_9__alert_review_merge_index_same_camera.sql
`);
assert.equal(untrackedMergeIndexSameCameraMigration.ok, false);
assert.equal(untrackedMergeIndexSameCameraMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedDeletedSmallintMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260708_10__alert_review_deleted_smallint.sql
`);
assert.equal(untrackedDeletedSmallintMigration.ok, false);
assert.equal(untrackedDeletedSmallintMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedSchedulerActivationMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260709__alert_review_scheduler_activation.sql
`);
assert.equal(untrackedSchedulerActivationMigration.ok, false);
assert.equal(untrackedSchedulerActivationMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedExportQueueMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260710__alert_review_export_queue.sql
`);
assert.equal(untrackedExportQueueMigration.ok, false);
assert.equal(untrackedExportQueueMigration.blockers[0].group, 'DEVICE schema and migration');

const untrackedMediaManageMigration = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql
`);
assert.equal(untrackedMediaManageMigration.ok, false);
assert.equal(untrackedMediaManageMigration.blockers[0].group, 'DEVICE schema and migration');

const semanticTriggerConfirmationMigrationPath =
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260712__alert_review_semantic_trigger_confirmation.sql';
const untrackedSemanticTriggerConfirmationMigration = evaluateStatus(`
?? ${semanticTriggerConfirmationMigrationPath}
`);
assert.equal(untrackedSemanticTriggerConfirmationMigration.ok, false);
assert.equal(
  untrackedSemanticTriggerConfirmationMigration.blockers[0].group,
  'DEVICE schema and migration',
);
assert.ok(
  releasePackageVerifier.TRACKED_RELEASE_PATHS?.includes(semanticTriggerConfirmationMigrationPath),
  'semantic trigger confirmation migration must be included in the release package',
);

const semanticIndexClaimMigrationPath =
  'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260713__alert_review_semantic_index_claim.sql';
const untrackedSemanticIndexClaimMigration = evaluateStatus(`
?? ${semanticIndexClaimMigrationPath}
`);
assert.equal(untrackedSemanticIndexClaimMigration.ok, false);
assert.equal(
  untrackedSemanticIndexClaimMigration.blockers[0].group,
  'DEVICE schema and migration',
);
assert.ok(
  releasePackageVerifier.TRACKED_RELEASE_PATHS?.includes(semanticIndexClaimMigrationPath),
  'semantic index claim migration must be included in the release package',
);

const untrackedVideoRuntimeArtifacts = evaluateStatus(`
 M VIDEO/.gitignore
?? VIDEO/apply_migrations.py
?? VIDEO/bootstrap_schema.py
?? VIDEO/prepare_database.py
?? VIDEO/schema_lock.py
?? VIDEO/run.py
?? VIDEO/models.py
?? VIDEO/app/blueprints/alert.py
?? VIDEO/app/blueprints/device_detection_region.py
?? VIDEO/app/services/device_detection_region_service.py
?? VIDEO/app/services/dvr_upload_service.py
?? VIDEO/app/services/media_kafka_service.py
?? VIDEO/app/services/record_cache_flush_event_service.py
?? VIDEO/app/services/seekable_playback_service.py
?? VIDEO/migrations/V20260711__device_detection_region_rule_fields.sql
?? VIDEO/test_apply_migrations.py
?? VIDEO/test_device_detection_region_persistence.py
?? VIDEO/test_record_export_minio_smoke.py
?? VIDEO/test_seekable_playback.py
`);
assert.equal(untrackedVideoRuntimeArtifacts.ok, false);
assert.deepEqual(
  untrackedVideoRuntimeArtifacts.blockers.map((entry) => entry.group),
  Array(19).fill('VIDEO record evidence package'),
);

const untrackedManifestSignerTest = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/ReviewEvidenceManifestSignerTest.java
`);
assert.equal(untrackedManifestSignerTest.ok, false);
assert.equal(untrackedManifestSignerTest.blockers[0].group, 'DEVICE review regression tests');

const untrackedSupervisionPersistenceIdContractTest = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/SupervisionPersistenceIdContractTest.java
`);
assert.equal(untrackedSupervisionPersistenceIdContractTest.ok, false);
assert.equal(untrackedSupervisionPersistenceIdContractTest.blockers[0].group, 'DEVICE review regression tests');

const untrackedWorkbenchRunner = evaluateStatus(`
?? WEB/scripts/alert-review-workbench-e2e-check.test.mjs
?? WEB/scripts/alert-review-playback-contract.test.mjs
?? WEB/src/utils/withInstall.ts
`);
assert.equal(untrackedWorkbenchRunner.ok, false);
assert.equal(untrackedWorkbenchRunner.blockers[0].group, 'WEB alert review workbench package');
assert.equal(untrackedWorkbenchRunner.blockers[1].group, 'WEB alert review workbench package');
assert.equal(untrackedWorkbenchRunner.blockers[2].group, 'WEB alert review workbench package');

const untrackedSecurityAndRealUiArtifacts = evaluateStatus(`
?? DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/controller/admin/auth/vo/MediaPermissionCheckReqVO.java
?? DEVICE/iot-system/iot-system-biz/src/test/java/com/basiclab/iot/system/supervision/AlertReviewDataSchemaValidatorTest.java
?? VIDEO/app/services/media_authorization_service.py
?? VIDEO/test_media_authorization.py
?? WEB/src/views/alert/index.vue
?? WEB/src/views/camera/components/DeviceRegionDrawer/index.vue
`);
assert.equal(untrackedSecurityAndRealUiArtifacts.ok, false);
assert.deepEqual(
  untrackedSecurityAndRealUiArtifacts.blockers.map((entry) => entry.group),
  [
    'DEVICE review backend',
    'DEVICE review regression tests',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'WEB alert review workbench package',
    'WEB alert review workbench package',
  ],
);

const newlyHardenedMediaArtifacts = [
  'VIDEO/app/services/local_media_path_service.py',
  'VIDEO/enforce_private_media_buckets.py',
  'VIDEO/app/services/media_janitor_service.py',
  'VIDEO/app/services/media_resource_guard.py',
  'VIDEO/app/services/playback_disk_guard_service.py',
  'VIDEO/app/blueprints/media_hook.py',
  'VIDEO/app/utils/minio_bucket_policy.py',
  'VIDEO/test_local_media_path_security.py',
  'VIDEO/test_minio_bucket_policy.py',
  'VIDEO/test_tenant_media_maintenance.py',
  'WEB/src/api/device/snap.ts',
  'WEB/src/views/camera/components/SnapSpace/SnapSpaceImageGallery.vue',
];
const untrackedHardenedMediaArtifacts = evaluateStatus(
  newlyHardenedMediaArtifacts.map((path) => `?? ${path}`).join('\n'),
);
assert.equal(untrackedHardenedMediaArtifacts.ok, false);
assert.deepEqual(
  untrackedHardenedMediaArtifacts.blockers.map((entry) => entry.group),
  [
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'WEB alert review workbench package',
    'WEB alert review workbench package',
  ],
);
for (const path of newlyHardenedMediaArtifacts) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}

const clusterHookHardeningArtifacts = [
  '.scripts/media-cluster/docker-compose.media-node.yml',
  '.scripts/media-cluster/enable_cluster_mode.sh',
  '.scripts/media-cluster/install_media_stack.sh',
  '.scripts/media-cluster/srs/cluster.conf.template',
  '.scripts/media-cluster/zlm/config.ini.template',
];
const untrackedClusterHookHardening = evaluateStatus(
  clusterHookHardeningArtifacts.map((path) => `?? ${path}`).join('\n'),
);
assert.equal(untrackedClusterHookHardening.ok, false);
assert.deepEqual(
  untrackedClusterHookHardening.blockers.map((entry) => entry.group),
  clusterHookHardeningArtifacts.map(() => 'FR production media deployment'),
);
for (const path of clusterHookHardeningArtifacts) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}

const videoRuntimeHardeningArtifacts = [
  'VIDEO/app/services/algorithm_task_daemon.py',
  'VIDEO/app/services/post_process_launcher_service.py',
  'VIDEO/app/services/stream_forward_launcher_service.py',
  'VIDEO/app/utils/face_model_paths.py',
  'VIDEO/app/utils/plate_model_paths.py',
  'VIDEO/app/utils/video_env.py',
  'VIDEO/test_subprocess_environment.py',
  'VIDEO/tests/test_realtime_algorithm_context.py',
];
const untrackedVideoRuntimeHardening = evaluateStatus(
  videoRuntimeHardeningArtifacts.map((path) => `?? ${path}`).join('\n'),
);
assert.equal(untrackedVideoRuntimeHardening.ok, false);
assert.deepEqual(
  untrackedVideoRuntimeHardening.blockers.map((entry) => entry.group),
  videoRuntimeHardeningArtifacts.map(() => 'VIDEO record evidence package'),
);
for (const path of videoRuntimeHardeningArtifacts) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}

const protectedMediaProxyArtifacts = [
  'AI/app/blueprints/minio_proxy.py',
  'AI/tests/test_minio_proxy.py',
  'AI/env.example',
  'APP/conf/nginx.conf',
  'WEB/conf/nginx.conf',
  'WEB/conf/nginx.mini.conf',
  'VIDEO/app/services/space_folder_tree_service.py',
  'VIDEO/app/services/space_group_save_time_service.py',
  'VIDEO/test_archive_atomicity.py',
  'VIDEO/test_record_space_tenant_listing.py',
  'VIDEO/test_snap_media_authorization.py',
];
const untrackedProtectedMediaProxyArtifacts = evaluateStatus(
  protectedMediaProxyArtifacts.map((path) => `?? ${path}`).join('\n'),
);
assert.equal(untrackedProtectedMediaProxyArtifacts.ok, false);
assert.deepEqual(
  untrackedProtectedMediaProxyArtifacts.blockers.map((entry) => entry.group),
  [
    'Protected media raw proxy package',
    'Protected media raw proxy package',
    'Protected media raw proxy package',
    'Protected media raw proxy package',
    'Protected media raw proxy package',
    'Protected media raw proxy package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
    'VIDEO record evidence package',
  ],
);
for (const path of protectedMediaProxyArtifacts) {
  assert.equal(
    releasePackageVerifier.TRACKED_RELEASE_PATHS.includes(path),
    true,
    `${path} must remain part of the formal FR release package`,
  );
}

const protectedMediaBuckets = [
  'record-space',
  'snap-space',
  'camera-screenshots',
  'alert-images',
  'record-archive',
  'snap-archive',
  'review-evidence',
];
const completeRawMinioProxyContent = `
_PROTECTED_MEDIA_BUCKETS = frozenset({
${protectedMediaBuckets.map((bucket) => `    '${bucket}',`).join('\n')}
})
def download_bucket_object(bucket_name):
    normalized_bucket = bucket_name.strip().lower()
    if normalized_bucket in _PROTECTED_MEDIA_BUCKETS:
        return jsonify({'reason': 'protected_media_bucket'}), 403
    return _download_from_minio(normalized_bucket, object_name, temp_path)
`;
const nginxBucketProxyContent = `
include /etc/nginx/yfeieye-secrets/yfeieye-stream-secret.runtime.conf;
location = /dev-api/video/camera/callback/on_publish { return 403; }
location = /dev-api/video/camera/callback/on_dvr { return 403; }
location = /yfeieye/dev-api/video/camera/callback/on_publish { return 403; }
location = /yfeieye/dev-api/video/camera/callback/on_dvr { return 403; }
location ^~ /dev-api/video/media/hook/ { return 403; }
location ^~ /yfeieye/dev-api/video/media/hook/ { return 403; }
location ^~ /api/v1/buckets {
    proxy_pass http://ai-host:5000;
}
location ~ ^/(ai|live)/ {
    secure_link $arg_st,$arg_e;
    secure_link_md5 "$arg_e$uri $stream_secret";
    if ($secure_link = "") { return 403; }
    if ($secure_link = "0") { return 410; }
}
location ^~ /rtp/ {
    secure_link $arg_st,$arg_e;
    secure_link_md5 "$arg_e$uri $stream_secret";
    if ($secure_link = "") { return 403; }
    if ($secure_link = "0") { return 410; }
}
`;
const completeRawMinioProxyGateScan = scanRawMinioProxyGate([
  { path: 'AI/app/blueprints/minio_proxy.py', content: completeRawMinioProxyContent },
  { path: 'APP/conf/nginx.conf', content: nginxBucketProxyContent },
  { path: 'WEB/conf/nginx.conf', content: nginxBucketProxyContent },
  { path: 'WEB/conf/nginx.mini.conf', content: nginxBucketProxyContent },
  {
    path: 'WEB/install_linux.sh',
    content: 'node ../.scripts/configure-nginx-stream-secret.mjs --env-file=../VIDEO/.env.docker --skip-nginx-check',
  },
]);
assert.equal(completeRawMinioProxyGateScan.ok, true);

const missingStreamSecretInstallHookScan = scanRawMinioProxyGate([{
  path: 'WEB/install_linux.sh',
  content: 'docker compose up -d',
}]);
assert.equal(missingStreamSecretInstallHookScan.ok, false);
assert.deepEqual(
  missingStreamSecretInstallHookScan.blockers.map((blocker) => blocker.reason),
  ['stream_ticket_secret_install_hook_missing'],
);

const webInstallerSource = readFileSync('WEB/install_linux.sh', 'utf8');
const webUpdateFunction = webInstallerSource.match(/update_service\(\) \{[\s\S]*?\n\}/)?.[0] || '';
assert.match(
  webUpdateFunction,
  /ensure_nginx_conf_for_profile/,
  'WEB update must generate and validate the stream-ticket secret before Compose starts',
);

const incompleteRawMinioDenylistScan = scanRawMinioProxyGate([{
  path: 'AI/app/blueprints/minio_proxy.py',
  content: completeRawMinioProxyContent.replace("    'review-evidence',\n", ''),
}]);
assert.equal(incompleteRawMinioDenylistScan.ok, false);
assert.deepEqual(incompleteRawMinioDenylistScan.blockers.map((blocker) => blocker.reason), [
  'raw_minio_protected_bucket_denylist_missing',
]);

const publicNginxBucketProxyScan = scanRawMinioProxyGate([
  { path: 'APP/conf/nginx.conf', content: nginxBucketProxyContent.replace('location ^~ /api/v1/buckets {', 'location ^~ /api/v1/buckets {\n    allow all;') },
  { path: 'WEB/conf/nginx.conf', content: nginxBucketProxyContent.replace('location ^~ /api/v1/buckets {', 'location ^~ /api/v1/buckets {\n    allow all;') },
  { path: 'WEB/conf/nginx.mini.conf', content: nginxBucketProxyContent.replace('location ^~ /api/v1/buckets {', 'location ^~ /api/v1/buckets {\n    allow all;') },
]);
assert.equal(publicNginxBucketProxyScan.ok, false);
assert.deepEqual(publicNginxBucketProxyScan.blockers.map((blocker) => blocker.reason), [
  'raw_minio_nginx_bucket_allow_all',
  'raw_minio_nginx_bucket_allow_all',
  'raw_minio_nginx_bucket_allow_all',
]);

const insecureStreamTicketNginxContent = `
location ^~ /api/v1/buckets {
    proxy_pass http://ai-host:5000;
}
set $stream_secret "hardcoded-secret";
location ~ ^/(ai|live)/ {
    secure_link $arg_st,$arg_e;
    secure_link_md5 "$arg_e$uri $stream_secret";
    # if ($secure_link = "") { return 403; }
    # if ($secure_link = "0") { return 410; }
}
location ^~ /rtp/ {
    secure_link $arg_st,$arg_e;
    secure_link_md5 "$arg_e$uri $stream_secret";
}
`;
const insecureStreamTicketScan = scanRawMinioProxyGate([
  { path: 'APP/conf/nginx.conf', content: insecureStreamTicketNginxContent },
]);
assert.equal(insecureStreamTicketScan.ok, false);
assert.deepEqual(insecureStreamTicketScan.blockers.map((blocker) => blocker.reason), [
  'stream_ticket_hardcoded_secret',
  'stream_ticket_external_secret_missing',
  'srs_hook_public_callback_deny_missing',
  'stream_ticket_enforcement_missing',
]);

const optionalEmptyStreamSecretFallbackScan = scanRawMinioProxyGate([{
  path: 'APP/conf/nginx.conf',
  content: nginxBucketProxyContent
    .replace(
      'include /etc/nginx/yfeieye-secrets/yfeieye-stream-secret.runtime.conf;',
      'set $stream_secret "";\ninclude /etc/nginx/yfeieye-secrets/yfeieye-stream-secret*.conf;',
    ),
}]);
assert.equal(optionalEmptyStreamSecretFallbackScan.ok, false);
assert.deepEqual(
  optionalEmptyStreamSecretFallbackScan.blockers.map((blocker) => blocker.reason),
  ['stream_ticket_empty_fallback', 'stream_ticket_external_secret_missing'],
);

const missingStreamSecretMountScan = scanRawMinioProxyGate([
  { path: 'APP/conf/nginx.conf', content: nginxBucketProxyContent },
  { path: 'WEB/conf/nginx.conf', content: nginxBucketProxyContent },
  { path: 'WEB/conf/nginx.mini.conf', content: nginxBucketProxyContent },
  { path: 'APP/docker-compose.yaml', content: 'volumes:\n  - ./conf/nginx.conf:/etc/nginx/nginx.conf:ro\n' },
  { path: 'WEB/docker-compose.yaml', content: 'volumes:\n  - ./conf/nginx.conf:/etc/nginx/nginx.conf:ro\n' },
]);
assert.equal(missingStreamSecretMountScan.ok, false);
assert.deepEqual(missingStreamSecretMountScan.blockers.map((blocker) => blocker.reason), [
  'stream_ticket_secret_mount_missing',
  'stream_ticket_secret_mount_missing',
]);

const mojibakeScan = scanTextQuality([
  {
    path: 'VIDEO/app/blueprints/record.py',
    content: "logger.error(f'\u9352\u6d98\u7f13\u590d\u6838\u8bc1\u636e\u5f55\u50cf\u5bfc\u51fa\u4efb\u52a1\u5931\u8d25: {str(e)}')",
  },
]);
assert.equal(mojibakeScan.ok, false);
assert.equal(mojibakeScan.blockers[0].reason, 'encoding_mojibake');

const patrolMojibakeScan = scanTextQuality([
  {
    path: 'WEB/src/api/device/patrol.ts',
    content: "/** \u93bd\u52eb\u511a\u6f8e\u6751\u8d30\u59ab\u20ac\u6d7c\u6c33\u7629 API */",
  },
]);
assert.equal(patrolMojibakeScan.ok, false);
assert.equal(patrolMojibakeScan.blockers[0].reason, 'encoding_mojibake');

const visibleCopyMojibakeScan = scanTextQuality([
  {
    path: 'VIDEO/app/blueprints/record.py',
    content: 'logger.error("\u935b\u5a45警录像导出失败")',
  },
]);
assert.equal(visibleCopyMojibakeScan.ok, false);
assert.equal(visibleCopyMojibakeScan.blockers[0].reason, 'encoding_mojibake');

const missingRecordFallbackMojibakeScan = scanTextQuality([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: 'renders as `\u7f02\u54c4\u7d8d\u50cf / \u5bf0\u546e\u589c\u52a8` with `VIDEO URL \u93c8\ue048\u53a4`',
  },
]);
assert.equal(missingRecordFallbackMojibakeScan.ok, false);
assert.equal(missingRecordFallbackMojibakeScan.blockers[0].reason, 'encoding_mojibake');

const cleanTextScan = scanTextQuality([
  {
    path: 'VIDEO/app/blueprints/record.py',
    content: "logger.error(f'\u521b\u5efa\u590d\u6838\u8bc1\u636e\u5f55\u50cf\u5bfc\u51fa\u4efb\u52a1\u5931\u8d25: {str(e)}')",
  },
]);
assert.equal(cleanTextScan.ok, true);

const typecheckGateScan = scanWebTypecheckGate([
  {
    path: 'WEB/package.json',
    content: JSON.stringify({
      scripts: {
        'type:check': 'cross-env NODE_OPTIONS=--max-old-space-size=8192 vue-tsc --noEmit --skipLibCheck',
      },
    }),
  },
]);
assert.equal(typecheckGateScan.ok, true);

const missingTypecheckGateScan = scanWebTypecheckGate([
  {
    path: 'WEB/package.json',
    content: JSON.stringify({ scripts: { build: 'vite build' } }),
  },
]);
assert.equal(missingTypecheckGateScan.ok, false);
assert.equal(missingTypecheckGateScan.blockers[0].reason, 'web_typecheck_gate_missing');

const weakenedTypecheckGateScan = scanWebTypecheckGate([
  {
    path: 'WEB/package.json',
    content: JSON.stringify({ scripts: { 'type:check': 'tsc --noEmit' } }),
  },
]);
assert.equal(weakenedTypecheckGateScan.ok, false);
assert.equal(weakenedTypecheckGateScan.blockers[0].reason, 'web_typecheck_gate_weakened');

const completeMediaPermissionSeedContent = [
  'system:supervision-alert-review:media:playback',
  'system:supervision-alert-review:media:snapshot',
  'system:supervision-alert-review:media:export',
  'system:supervision-alert-review:media:download',
  'system:supervision-alert-review:media:manifest',
].join('; ');
const completeMediaManagePermissionSeedContent = 'system:supervision-alert-review:media:manage';
const completeMediaPermissionConfigContent = [
  'camera-permission:',
  'action-permissions:',
  'playback:',
  'system:supervision-alert-review:media:playback',
  'snapshot:',
  'system:supervision-alert-review:media:snapshot',
  'coverage:',
  'export:',
  'system:supervision-alert-review:media:export',
  'download:',
  'system:supervision-alert-review:media:download',
  'manifest_verify:',
  'system:supervision-alert-review:media:manifest',
].join('\n');

const mediaPermissionGateScan = scanMediaPermissionGate([
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
    content: completeMediaPermissionSeedContent,
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
    content: completeMediaManagePermissionSeedContent,
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml',
    content: completeMediaPermissionConfigContent,
  },
]);
assert.equal(mediaPermissionGateScan.ok, true);

const missingSnapshotMediaPermissionGateScan = scanMediaPermissionGate([
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
    content: completeMediaPermissionSeedContent.replace('system:supervision-alert-review:media:snapshot; ', ''),
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
    content: completeMediaManagePermissionSeedContent,
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml',
    content: completeMediaPermissionConfigContent,
  },
]);
assert.equal(missingSnapshotMediaPermissionGateScan.ok, false);
assert.deepEqual(missingSnapshotMediaPermissionGateScan.blockers.map((blocker) => blocker.reason), [
  'media_permission_snapshot_seed_missing',
]);

const missingManageMediaPermissionGateScan = scanMediaPermissionGate([
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
    content: completeMediaPermissionSeedContent,
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
    content: '',
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml',
    content: completeMediaPermissionConfigContent,
  },
]);
assert.equal(missingManageMediaPermissionGateScan.ok, false);
assert.deepEqual(missingManageMediaPermissionGateScan.blockers.map((blocker) => blocker.reason), [
  'media_permission_manage_seed_missing',
]);

const missingSnapshotMediaPermissionConfigGateScan = scanMediaPermissionGate([
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260706__alert_review_media_permissions.sql',
    content: completeMediaPermissionSeedContent,
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/sql/migrations/V20260711__alert_review_media_manage_permission.sql',
    content: completeMediaManagePermissionSeedContent,
  },
  {
    path: 'DEVICE/iot-system/iot-system-biz/src/main/resources/application.yaml',
    content: completeMediaPermissionConfigContent
      .replace('snapshot:\n', '')
      .replace('system:supervision-alert-review:media:snapshot\n', ''),
  },
]);
assert.equal(missingSnapshotMediaPermissionConfigGateScan.ok, false);
assert.deepEqual(missingSnapshotMediaPermissionConfigGateScan.blockers.map((blocker) => blocker.reason), [
  'media_permission_snapshot_action_config_missing',
]);

const completeVideoIntegrationConfig = `
YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL=\${YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL:-http://video:6000/video/alert/record/query}
YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL=\${YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL:-http://video:6000/video/record/availability}
YFEIEYE_VIDEO_RECORD_BASE_URL=\${YFEIEYE_VIDEO_RECORD_BASE_URL:-http://video:6000/video/record}
YFEIEYE_VIDEO_RECORD_EXPORT_URL=\${YFEIEYE_VIDEO_RECORD_EXPORT_URL:-http://video:6000/video/record/export}
YFEIEYE_VIDEO_PUBLIC_PLAY_HOST=\${YFEIEYE_VIDEO_PUBLIC_PLAY_HOST:-}
YFEIEYE_MEDIA_SERVICE_HMAC_SECRET=\${YFEIEYE_MEDIA_SERVICE_HMAC_SECRET:-}
YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ENABLED=\${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ENABLED:-false}
YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES=\${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES:-}
YFEIEYE_REVIEW_RUNTIME_ALERT_TEMPLATE_CODE=\${YFEIEYE_REVIEW_RUNTIME_ALERT_TEMPLATE_CODE:-YFEIEYE_REVIEW_RUNTIME_ALERT}
YFEIEYE_REVIEW_OPERATIONS_REPORT_TEMPLATE_CODE=\${YFEIEYE_REVIEW_OPERATIONS_REPORT_TEMPLATE_CODE:-YFEIEYE_REVIEW_OPERATIONS_REPORT}
\${YFEIEYE_VIDEO_STATE_ROOT:-/data/yfeieye-video}/alert_images:/app/alert_images
`;
const videoIntegrationConfigScan = scanVideoIntegrationConfigGate([{
  path: 'DEVICE/docker-compose.yml',
  content: completeVideoIntegrationConfig,
}]);
assert.equal(videoIntegrationConfigScan.ok, true);

const legacyGlobalRuntimeOutboxRecipientsScan = scanVideoIntegrationConfigGate([{
  path: 'DEVICE/docker-compose.yml',
  content: completeVideoIntegrationConfig.replace(
    'YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES=\${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_TENANT_ADMIN_USER_ROUTES:-}',
    'YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ADMIN_USER_IDS=\${YFEIEYE_REVIEW_RUNTIME_OUTBOX_NOTIFY_ADMIN_USER_IDS:-}',
  ),
}]);
assert.equal(legacyGlobalRuntimeOutboxRecipientsScan.ok, false);
assert.deepEqual(legacyGlobalRuntimeOutboxRecipientsScan.blockers.map((blocker) => blocker.reason), [
  'runtime_outbox_notify_global_admin_fallback_forbidden',
  'runtime_outbox_notify_compose_wiring_missing',
]);

const missingPublicVideoPlayHostScan = scanVideoIntegrationConfigGate([{
  path: 'DEVICE/docker-compose.yml',
  content: completeVideoIntegrationConfig.replace(
    'YFEIEYE_VIDEO_PUBLIC_PLAY_HOST=\${YFEIEYE_VIDEO_PUBLIC_PLAY_HOST:-}\n',
    '',
  ),
}]);
assert.equal(missingPublicVideoPlayHostScan.ok, false);
assert.deepEqual(missingPublicVideoPlayHostScan.blockers.map((blocker) => blocker.reason), [
  'video_integration_public_play_host_wiring_missing',
]);

const divergentAlertImageMountScan = scanVideoIntegrationConfigGate([{
  path: 'DEVICE/docker-compose.yml',
  content: completeVideoIntegrationConfig.replace(
    '${YFEIEYE_VIDEO_STATE_ROOT:-/data/yfeieye-video}/alert_images:/app/alert_images',
    '../VIDEO/alert_images:/app/alert_images',
  ),
}]);
assert.equal(divergentAlertImageMountScan.ok, false);
assert.deepEqual(divergentAlertImageMountScan.blockers.map((blocker) => blocker.reason), [
  'alert_image_shared_state_mount_missing',
]);

const completeVideoResourceControlConfig = [
  'FLASK_RUN_HOST=${FLASK_RUN_HOST:?FLASK_RUN_HOST must be set to the Docker bridge gateway}',
  'ALLOWED_HOSTS=${ALLOWED_HOSTS:?ALLOWED_HOSTS must be set}',
  'http://$${FLASK_RUN_HOST}:$${FLASK_RUN_PORT:-6000}/actuator/health',
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
].join('; ');

const missingVideoResourceControlScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/docker-compose.yaml',
  content: [
    'python /app/prepare_database.py',
    'python /app/apply_migrations.py --verify-only',
    'exec python /app/run.py',
    'YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS=${YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS:-300}',
  ].join('; '),
}]);
assert.equal(missingVideoResourceControlScan.ok, false);
assert.deepEqual(missingVideoResourceControlScan.blockers.map((blocker) => blocker.reason), [
  'video_bridge_bind_contract_missing',
  'video_resource_control_compose_wiring_missing',
]);

const publicVideoBindScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/docker-compose.yaml',
  content: [
    'python /app/prepare_database.py',
    'python /app/apply_migrations.py --verify-only',
    'exec python /app/run.py',
    'YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS=${YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS:-300}',
    completeVideoResourceControlConfig.replace(
      'FLASK_RUN_HOST=${FLASK_RUN_HOST:?FLASK_RUN_HOST must be set to the Docker bridge gateway}',
      'FLASK_RUN_HOST=${FLASK_RUN_HOST:-0.0.0.0}',
    ),
  ].join('; '),
}]);
assert.equal(publicVideoBindScan.ok, false);
assert.deepEqual(publicVideoBindScan.blockers.map((blocker) => blocker.reason), [
  'video_bridge_bind_contract_missing',
]);

const missingPlaybackTicketWindowScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/docker-compose.yaml',
  content: [
    'python /app/prepare_database.py',
    'python /app/apply_migrations.py --verify-only',
    'exec python /app/run.py',
    'YFEIEYE_MEDIA_SERVICE_HMAC_SECRET=${YFEIEYE_MEDIA_SERVICE_HMAC_SECRET:-}',
    completeVideoResourceControlConfig,
  ].join('; '),
}]);
assert.equal(missingPlaybackTicketWindowScan.ok, false);
assert.deepEqual(missingPlaybackTicketWindowScan.blockers.map((blocker) => blocker.reason), [
  'video_integration_playback_ticket_window_missing',
]);

const missingVideoMigrationEntrypointScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/docker-compose.yaml',
  content: [
    'YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS=${YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS:-300}',
    completeVideoResourceControlConfig,
  ].join('; '),
}]);
assert.equal(missingVideoMigrationEntrypointScan.ok, false);
assert.deepEqual(missingVideoMigrationEntrypointScan.blockers.map((blocker) => blocker.reason), [
  'video_production_migration_entrypoint_missing',
]);

const incompleteVideoMigrationRunnerScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/apply_migrations.py',
  content: 'MIGRATION_FILES = []',
}]);
assert.equal(incompleteVideoMigrationRunnerScan.ok, false);
assert.deepEqual(incompleteVideoMigrationRunnerScan.blockers.map((blocker) => blocker.reason), [
  'video_production_migration_runner_incomplete',
]);

const unboundedVideoSchemaLockScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/schema_lock.py',
  content: 'SELECT pg_advisory_lock(1)',
}]);
assert.equal(unboundedVideoSchemaLockScan.ok, false);
assert.deepEqual(unboundedVideoSchemaLockScan.blockers.map((blocker) => blocker.reason), [
  'video_schema_lock_not_bounded',
]);

const insecureVideoSecretOverrideScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/docker-compose.yaml',
  content: [
    'python /app/prepare_database.py',
    'python /app/apply_migrations.py --verify-only',
    'exec python /app/run.py',
    'YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS=${YFEIEYE_MEDIA_SERVICE_MAX_SKEW_SECONDS:-300}',
    'YFEIEYE_RECORD_EXPORT_HMAC_KEYS=${YFEIEYE_RECORD_EXPORT_HMAC_KEYS:-}',
    completeVideoResourceControlConfig,
  ].join('; '),
}]);
assert.equal(insecureVideoSecretOverrideScan.ok, false);
assert.deepEqual(insecureVideoSecretOverrideScan.blockers.map((blocker) => blocker.reason), [
  'video_production_secret_env_file_overridden',
]);

const incompleteVideoSecurityEnvContractScan = scanVideoIntegrationConfigGate([{
  path: 'VIDEO/env.example',
  content: 'YFEIEYE_MEDIA_AUTHORIZATION_URL=http://device',
}]);
assert.equal(incompleteVideoSecurityEnvContractScan.ok, false);
assert.deepEqual(incompleteVideoSecurityEnvContractScan.blockers.map((blocker) => blocker.reason), [
  'video_production_security_env_contract_missing',
]);

const incompleteSrsHookAuthorizationScan = scanVideoIntegrationConfigGate([
  { path: 'VIDEO/app/utils/video_env.py', content: 'def validate_production_runtime_secrets(): pass' },
  { path: 'VIDEO/app/blueprints/camera.py', content: 'def on_publish_callback(): pass\ndef on_dvr_callback(): pass' },
  { path: '.scripts/docker/install_middleware_linux.sh', content: 'on_publish http://gateway/callback' },
  { path: '.scripts/docker/env.example', content: 'VIDEO_CALLBACK_HOST=localhost' },
]);
assert.equal(incompleteSrsHookAuthorizationScan.ok, false);
assert.deepEqual(incompleteSrsHookAuthorizationScan.blockers.map((blocker) => blocker.reason), [
  'srs_hook_runtime_authorization_missing',
  'srs_hook_route_authorization_missing',
  'srs_hook_installer_contract_missing',
  'srs_hook_middleware_env_contract_missing',
]);

const incompleteSrsHookHardeningScan = scanVideoIntegrationConfigGate([
  {
    path: 'VIDEO/app/utils/video_env.py',
    content: 'authorize_srs_hook_token; YFEIEYE_SRS_HOOK_TOKEN; hmac.compare_digest; at least 32 bytes in production',
  },
  {
    path: '.scripts/docker/install_middleware_linux.sh',
    content: 'resolve_srs_hook_token(); resolve_video_callback_host; ?hook_token=${srs_hook_token}',
  },
]);
assert.equal(incompleteSrsHookHardeningScan.ok, false);
assert.deepEqual(incompleteSrsHookHardeningScan.blockers.map((blocker) => blocker.reason), [
  'srs_hook_runtime_authorization_missing',
  'srs_hook_installer_contract_missing',
]);

const incompleteAlternateMediaHookScan = scanVideoIntegrationConfigGate([
  {
    path: 'VIDEO/app/blueprints/media_hook.py',
    content: 'def srs_on_dvr(): pass\ndef srs_on_publish(): pass\ndef srs_on_unpublish(): pass\ndef snap_completed(): pass\ndef zlm_on_record(): pass',
  },
  {
    path: '.scripts/media-cluster/srs/cluster.conf.template',
    content: '/video/media/hook/srs/on_dvr',
  },
  {
    path: '.scripts/media-cluster/zlm/config.ini.template',
    content: '/video/media/hook/zlm/on_record_mp4',
  },
  {
    path: '.scripts/media-cluster/install_media_stack.sh',
    content: 'envsubst MEDIA_HOOK_HOST',
  },
  {
    path: '.scripts/media-cluster/enable_cluster_mode.sh',
    content: 'CLUSTER_MODE=true',
  },
  {
    path: '.scripts/media-cluster/docker-compose.media-node.yml',
    content: 'services: {}',
  },
]);
assert.equal(incompleteAlternateMediaHookScan.ok, false);
assert.deepEqual(incompleteAlternateMediaHookScan.blockers.map((blocker) => blocker.reason), [
  'alternate_media_hook_authorization_missing',
  'cluster_srs_hook_token_missing',
  'cluster_zlm_hook_token_missing',
  'cluster_hook_installer_token_missing',
  'cluster_zlm_secret_contract_missing',
  'cluster_media_compose_base_id_missing',
  'cluster_srs_config_refresh_missing',
  'cluster_hook_env_template_token_missing',
  'cluster_hook_env_token_validation_missing',
  'cluster_hook_compose_usage_token_missing',
  'cluster_media_container_names_not_distinct',
]);

const weakClusterZlmSecretScan = scanVideoIntegrationConfigGate([{
  path: '.scripts/media-cluster/install_media_stack.sh',
  content: [
    'validate_hook_token()',
    'YFEIEYE_SRS_HOOK_TOKEN',
    '${YFEIEYE_SRS_HOOK_TOKEN}',
    'ZLM_SECRET="${ZLM_SECRET:-}"',
    'validate_zlm_secret()',
    'ZLM_SECRET must contain at least 32 characters',
    'ZLM_SECRET must contain only URL-safe characters',
    'MEDIA_HOOK_PORT="${MEDIA_HOOK_PORT:-6000}"',
    'MEDIA_HOOK_PATH_PREFIX="${MEDIA_HOOK_PATH_PREFIX:-}"',
    'chmod 600 "${out}"',
    'chmod 600 "${out}"',
    'export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"',
    'export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"',
    'ZLM_SECRET="${ZLM_SECRET:-yFeiEye_Media_Secret}"',
  ].join('\n'),
}]);
assert.equal(weakClusterZlmSecretScan.ok, false);
assert.deepEqual(weakClusterZlmSecretScan.blockers.map((blocker) => blocker.reason), [
  'cluster_zlm_secret_contract_missing',
  'cluster_srs_config_refresh_missing',
]);

const staleHealthySrsConfigScan = scanVideoIntegrationConfigGate([{
  path: '.scripts/media-cluster/install_media_stack.sh',
  content: [
    'validate_hook_token()',
    'YFEIEYE_SRS_HOOK_TOKEN',
    '${YFEIEYE_SRS_HOOK_TOKEN}',
    'ZLM_SECRET="${ZLM_SECRET:-}"',
    'validate_zlm_secret()',
    'ZLM_SECRET must contain at least 32 characters',
    'ZLM_SECRET must contain only URL-safe characters',
    'MEDIA_HOOK_PORT="${MEDIA_HOOK_PORT:-6000}"',
    'MEDIA_HOOK_PATH_PREFIX="${MEDIA_HOOK_PATH_PREFIX:-}"',
    'chmod 600 "${out}"',
    'chmod 600 "${out}"',
    'export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"',
    'export MEDIA_NODE_ID="${MEDIA_NODE_NAME}"',
    'deploy_srs() {',
    '  if srs_healthy; then',
    '    return 0',
    '  fi',
    '  render_srs_config',
    '}',
  ].join('\n'),
}]);
assert.equal(staleHealthySrsConfigScan.ok, false);
assert.deepEqual(staleHealthySrsConfigScan.blockers.map((blocker) => blocker.reason), [
  'cluster_srs_config_refresh_missing',
]);

const unvalidatedClusterEnvHookTokenScan = scanVideoIntegrationConfigGate([{
  path: '.scripts/media-cluster/enable_cluster_mode.sh',
  content: [
    'YFEIEYE_SRS_HOOK_TOKEN=',
    'chmod 600 "${ENV_SNIPPET}"',
  ].join('\n'),
}]);
assert.equal(unvalidatedClusterEnvHookTokenScan.ok, false);
assert.deepEqual(unvalidatedClusterEnvHookTokenScan.blockers.map((blocker) => blocker.reason), [
  'cluster_hook_env_token_validation_missing',
]);

const duplicateClusterContainerNameScan = scanVideoIntegrationConfigGate([{
  path: '.scripts/media-cluster/docker-compose.media-node.yml',
  content: [
    'MEDIA_HOOK_PORT=6000',
    'YFEIEYE_SRS_HOOK_TOKEN=',
    'container_name: "${MEDIA_NODE_ID}"',
    'container_name: "${MEDIA_NODE_ID}"',
  ].join('\n'),
}]);
assert.equal(duplicateClusterContainerNameScan.ok, false);
assert.deepEqual(duplicateClusterContainerNameScan.blockers.map((blocker) => blocker.reason), [
  'cluster_media_container_names_not_distinct',
]);

const suffixedClusterComposeIdScan = scanVideoIntegrationConfigGate([{
  path: '.scripts/media-cluster/install_media_stack.sh',
  content: [
    'validate_hook_token()',
    'YFEIEYE_SRS_HOOK_TOKEN',
    '${YFEIEYE_SRS_HOOK_TOKEN}',
    'ZLM_SECRET="${ZLM_SECRET:-}"',
    'validate_zlm_secret()',
    'ZLM_SECRET must contain at least 32 characters',
    'ZLM_SECRET must contain only URL-safe characters',
    'MEDIA_HOOK_PORT="${MEDIA_HOOK_PORT:-6000}"',
    'MEDIA_HOOK_PATH_PREFIX="${MEDIA_HOOK_PATH_PREFIX:-}"',
    'chmod 600 "${out}"',
    'chmod 600 "${out}"',
    'export MEDIA_NODE_ID="${MEDIA_NODE_NAME}-srs"',
    'export MEDIA_NODE_ID="${MEDIA_NODE_NAME}-zlm"',
  ].join('\n'),
}]);
assert.equal(suffixedClusterComposeIdScan.ok, false);
assert.deepEqual(suffixedClusterComposeIdScan.blockers.map((blocker) => blocker.reason), [
  'cluster_media_compose_base_id_missing',
  'cluster_srs_config_refresh_missing',
]);

const aliasedVideoIntegrationConfigScan = scanVideoIntegrationConfigGate([{
  path: 'DEVICE/docker-compose.yml',
  content: completeVideoIntegrationConfig.replace(
    '/video/alert/record/query',
    '/video/record/availability',
  ),
}]);
assert.equal(aliasedVideoIntegrationConfigScan.ok, false);
assert.deepEqual(aliasedVideoIntegrationConfigScan.blockers.map((blocker) => blocker.reason), [
  'video_integration_alert_query_default_invalid',
  'video_integration_alert_coverage_defaults_aliased',
]);

const completeLiveVideoSmokeContent = 'requiredOptionErrors; sameReleaseEndpoint; record coverage URL must not equal alert record query URL; selectPlayableSegment; describeNonPlayableSegments; non_exportable_reason; nonExportableReason; exportable=false; record coverage query returned no playable/exportable record segment; validateCoverageClassification; STANDARD_COVERAGE_CLASSIFICATIONS; continuous; motion; alert; detection; normalizeCoverageClassification; normalized === \'all\'; normalized === \'record\'; normalized === \'recording\'; return \'continuous\'; coverageSummary; retainMode; coverageSource; record coverage query missing retain mode or source classification evidence; record coverage query returned non-standard retain mode or source classification; validateStorageDriftReport; REQUIRED_STORAGE_DRIFT_REASON_KEYS; storageDriftReasonKeys; standardReasonKeys; missing standard reason evidence; file_missing; retention_expired; disk_full; cache_flush_failed; validateManifestStorageLifecycle; validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); spawnSync(process.execPath; timeout: timeoutMs; ETIMEDOUT; timed out after; result.status !== 0; verifier failed with exit; missing --manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); assertReleaseMediaEvidence(options, "export package storage reference", objectKey); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; validateClipWindows(sourceSegments); invalid clip window; validateManifestConcatOrder(recordSegments, concatOrder); normalizeConcatOrderEntry(entry); validateRootConcatOrderCoverage(segmentOrderEntries, orderEntries, recordSegments.length); concatOrder.map; entry.index; duplicate concat order index; invalid concat order index; references missing segment index; omits segment index; does not match segment count; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;';
const completeRecordVideoServiceContent = '_normalize_gap_reason; _normalize_gap_reason_token; isalnum; normalized.strip(\'_\'); \'file_expired\': \'retention_expired\'; \'retention_expired\': \'retention\'; \'video_url_not_configured\': \'configuration\'; \'record_space_not_found\': \'configuration\'; \'file_missing\': \'filesystem\'; \'probe_failed\': \'probe\'; \'permission_denied\': \'permission\'; \'disk_full\': \'storage\'; \'cache_flush_failed\': \'cache\';';
const requiredDeviceEvidenceAuditAnchors = [
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
];
const completeDeviceEvidenceAuditContent = requiredDeviceEvidenceAuditAnchors.join('; ');
const completeDeviceTokenContent = `${completeDeviceEvidenceAuditContent}; parseArgs; token: env.YFEIEYE_DEVICE_AUTH_TOKEN; DEVICE smoke token must be provided through YFEIEYE_DEVICE_AUTH_TOKEN; missing YFEIEYE_DEVICE_AUTH_TOKEN; function sanitizeCliOutputText; stripUrlSecrets; console.error(sanitizeCliOutputText; playbackMaterialUri: env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI; DEVICE playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI; sanitizeCliOutputValue; value.map(sanitizeCliOutputValue); Object.entries(value).map; console.log(JSON.stringify(sanitizeCliOutputValue;`;
const completeProductionSmokeContent = 'requiredOptionErrors; liveVideoEvidenceError; liveDeviceEvidenceError; livePlayerEvidenceError; buildSmokeSteps; W4:visible-copy; alert-review-visible-copy-scan.mjs; visible-copy files for replacement characters; playerSmokeStep; --assert-native-current-time; Number.isFinite(player.nativeCurrentTime); missing native currentTime evidence; W2:typecheck; --pm-on-fail=ignore; pnpm_version_guard; typecheckRetry; REQUIRED_STORAGE_DRIFT_REASON_KEYS; payload.coverageSummary; summary.coverageSummary = buildCoverageSummary(payload.coverageSummary); buildCoverageSummary(payload.coverageSummary); copyTextIfPresent(coverage, source, \'retainMode\'); copyTextIfPresent(coverage, source, \'coverageSource\'); summary.coverageSummary?.retainMode; summary.coverageSummary?.coverageSource; missing coverage retain/source evidence; payload.storageDriftSummary; summary.storageDriftSummary = buildStorageDriftSummary(payload.storageDriftSummary); copyBooleanIfPresent(storageDriftSummary, source, \'healthy\'); copyNumberIfPresent(storageDriftSummary, source, \'recordCount\'); copyNumberIfPresent(storageDriftSummary, source, \'issueCount\'); issueReasons; standardReasonKeys; summary.storageDriftSummary?.standardReasonKeys; missing standard storage drift reason evidence; file_missing; retention_expired; disk_full; cache_flush_failed; payload.exportResult; buildExportResultSummary(payload.exportResult); copyTextIfPresent(exportResult, source, \'exportId\'); copySanitizedUrlIfPresent(exportResult, source, \'downloadUrl\'); copySanitizedUrlIfPresent(exportResult, source, \'manifestUrl\'); payload.manifestSignature; summary.manifestSignature = buildManifestSignatureSummary(payload.manifestSignature); copyTextIfPresent(manifestSignature, source, \'algorithm\'); copyTextIfPresent(manifestSignature, source, \'keyId\'); copyTextIfPresent(manifestSignature, source, \'signatureVersion\'); payload.manifestStorageLifecycle; buildManifestStorageLifecycleSummary(payload.manifestStorageLifecycle); copyTextIfPresent(manifestStorageLifecycle, source, \'storageType\'); copyTextIfPresent(manifestStorageLifecycle, source, \'status\'); copyTextIfPresent(manifestStorageLifecycle, source, \'expiresAt\'); copyTextIfPresent(manifestStorageLifecycle, source, \'exportPackageObjectKey\'); summary.manifestStorageLifecycle?.status; missing persisted manifest storage lifecycle evidence; payload.manifestVerification; buildManifestVerificationSummary(payload.manifestVerification); copyBooleanIfPresent(manifestVerification, source, \'valid\'); copyBooleanIfPresent(manifestVerification, source, \'signatureValid\'); copyBooleanIfPresent(manifestVerification, source, \'signatureKeyAvailable\'); copyTextIfPresent(manifestVerification, source, \'keyId\'); copyTextIfPresent(manifestVerification, source, \'signatureVersion\'); violations; summary.manifestVerification?.valid; summary.manifestVerification.signatureValid; summary.manifestVerification.signatureKeyAvailable; missing valid manifest verifier evidence; missing HMAC manifest verifier signature evidence; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; evidenceOutputFile; missing --evidence-output-file; YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE; stepTimeoutMs; --step-timeout-ms; YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS; timeout: step.timeoutMs; summary.timeout; timed out after; --timeout-ms=${options.stepTimeoutMs}; childSmokeSummary; buildPlaybackAccessSummary(payload.playback); copyTextIfPresent(playback, source, \'grantedDecision\'); copyTextIfPresent(playback, source, \'deniedDecision\'); deniedReasons; copySanitizedUrlIfPresent(player, payload, \'recordPath\'); stripUrlSecrets; evidence_download_audited; auditChain; firstAuditScalar; resolveExactAuditIdList; normalizeAuditIdList; normalizeAuditScalar; eventIds; reviewItemIds; export_downloaded; missing auditChain exportJobNo evidence; liveDevicePlaybackEvidenceError; summary.playback; grantedDecision; deniedDecision; camera_not_allowed; missing playback URL allow/deny decision evidence; missing playback URL deny reason evidence; liveDeviceRuleEvidenceError; summary.ruleEvidence; buildRuleEvidenceSummary(payload); copyTextIfPresent(ruleEvidence, source, \'ruleCode\'); copyTextIfPresent(ruleEvidence, source, \'cameraId\'); copyTextIfPresent(ruleEvidence, source, \'zoneCode\'); copyTextIfPresent(ruleEvidence, source, \'objectLabel\'); copyNumberIfPresent(ruleEvidence, source, \'inertiaFrames\'); copyNumberIfPresent(ruleEvidence, source, \'loiteringSeconds\'); inertiaFrames; loiteringSeconds; missing rule inertia/loitering evidence; missing rule inertiaFrames=3 evidence; missing rule loiteringSeconds=20 evidence; evidence_audit_chain_verified; copyPositiveIdIfPresent(summary, payload, \'eventId\'); copyPositiveIdListIfPresent(summary, payload, \'eventIds\'); normalizePositiveId; Object.hasOwn(payload, \'eventIds\'); explicitEmptyEventIds; missing eventId evidence; missing eventIds evidence; eventIds do not exactly match eventId; auditChain reviewCaseId does not match reviewCaseId; auditChain reviewItemIds do not exactly match reviewItemId; auditChain eventIds do not exactly match eventId; auditChain exportJobNo does not match exportJobNo;';
const completeLiveVideoTokenContent = `${completeLiveVideoSmokeContent} parseArgs; token: env.YFEIEYE_VIDEO_SMOKE_TOKEN; VIDEO smoke token must be provided through YFEIEYE_VIDEO_SMOKE_TOKEN; !options.allowLocalEndpoints && !hasText(options.token); missing YFEIEYE_VIDEO_SMOKE_TOKEN; runSmoke; withBearerAuthorization; const fetchImpl = withBearerAuthorization(rawFetchImpl, options.token); function sanitizeCliOutputText; stripUrlSecrets; console.error(sanitizeCliOutputText; playbackMaterialUri: env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI; VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI; recordUri: options.playbackMaterialUri; sanitizeCliOutputValue; value.map(sanitizeCliOutputValue); Object.entries(value).map; buildManifestVerifierEnvironment; env: buildManifestVerifierEnvironment(); YFEIEYE_RECORD_EXPORT_HMAC_SECRET; YFEIEYE_RECORD_EXPORT_HMAC_KEYS;`;
const completeProductionSmokeTokenContent = `${completeProductionSmokeContent} formatStepCommand; env: {; YFEIEYE_DEVICE_AUTH_TOKEN: options.token; YFEIEYE_VIDEO_SMOKE_TOKEN: options.token; buildStepEnvironment; tokenSource; parsed.tokenSource = 'cli'; options.tokenSource === 'cli'; cliTokenAllowedForLocalEndpoints; isExplicitLocalOrMockEndpoint; hostname.endsWith('.localhost'); hostname.endsWith('.test'); hostname.endsWith('.invalid'); --token requires --allow-local-endpoints and local/mock endpoints only; maskSensitiveArg; value.startsWith('--token='); return '--token=***'; 'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',; YFEIEYE_REVIEW_PLAYER_SMOKE_URL: options.playerWorkbenchUrl; CHILD_SENSITIVE_ENV_KEYS; playerLocalStorage: env.YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE; playerCookies: env.YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES; YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: options.playerLocalStorage; YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: options.playerCookies; function sanitizeChildOutputForDisplay; stripUrlSecrets; console.error(sanitizeChildOutputForDisplay; devicePlaybackMaterialUri: env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI; videoPlaybackMaterialUri: env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI; YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: options.devicePlaybackMaterialUri; YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: options.videoPlaybackMaterialUri; 'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',; 'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',; device playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI; VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI; signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL; sanitizeOutputValue; value.map(sanitizeOutputValue); Object.entries(value).map;`;
const completePlayerSmokeSecurityContent = 'parseArgs; function sanitizeOutputText; stripUrlSecrets; console.error(sanitizeOutputText; sanitizeSmokeResultForOutput; sanitizeOutputValue; value.map(sanitizeOutputValue); Object.entries(value).map; buildBrowserEnvironment; env: buildBrowserEnvironment(); BROWSER_SENSITIVE_ENV_KEYS; player local storage must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE; player cookies must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES; signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL;';
const completeManifestVerifierWrapperSecurityContent = 'buildManifestVerifierEnvironment; env: buildManifestVerifierEnvironment(); ALLOWED_SENSITIVE_ENV_KEYS; UNRELATED_SENSITIVE_ENV_KEYS; YFEIEYE_RECORD_EXPORT_HMAC_SECRET; YFEIEYE_RECORD_EXPORT_HMAC_KEYS; YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN; YFEIEYE_VIDEO_SMOKE_TOKEN;';
const completeSignedRelativeCliRegressionTestContent = 'spawnSync; record.mp4?token=; signedCliFailure.stderr.includes; native-error-secret; export-message-secret; message-secret; rule-secret; device-rule-secret; device-playback-argv-secret; browserProbe; player-argv-storage-secret; manifestVerifierChildEnv; wrapperProbe; playbackOverrideUri; YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI; YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI; sanitizeOutputValue; sanitizeCliOutputValue;';
const productionSmokeTimeoutEvidenceContent = 'stepTimeoutMs; --step-timeout-ms; YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS; timeout: step.timeoutMs; summary.timeout; timed out after; --timeout-ms=${options.stepTimeoutMs};';
const productionSmokeAuditIdentityContent = "auditChain; firstAuditScalar; resolveExactAuditIdList; normalizeAuditIdList; normalizeAuditScalar; eventIds; reviewItemIds; export_downloaded; evidence_audit_chain_verified; copyPositiveIdIfPresent(summary, payload, 'eventId'); copyPositiveIdListIfPresent(summary, payload, 'eventIds'); normalizePositiveId; Object.hasOwn(payload, 'eventIds'); explicitEmptyEventIds; missing eventId evidence; missing eventIds evidence; eventIds do not exactly match eventId; auditChain reviewCaseId does not match reviewCaseId; auditChain reviewItemIds do not exactly match reviewItemId; auditChain eventIds do not exactly match eventId; auditChain exportJobNo does not match exportJobNo; missing auditChain exportJobNo evidence;";

const liveVideoEvidenceGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-device-integration-smoke.mjs',
    content: completeDeviceEvidenceAuditContent,
  },
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
  {
    path: 'VIDEO/app/services/record_video_service.py',
    content: completeRecordVideoServiceContent,
  },
]);
assert.equal(liveVideoEvidenceGateScan.ok, true);

for (const requiredDeviceEvidenceAuditAnchor of requiredDeviceEvidenceAuditAnchors) {
  const missingDeviceEvidenceAuditGateScan = scanLiveVideoEvidenceGate([
    {
      path: '.scripts/alert-review-device-integration-smoke.mjs',
      content: completeDeviceEvidenceAuditContent.replace(requiredDeviceEvidenceAuditAnchor, ''),
    },
  ]);
  assert.equal(missingDeviceEvidenceAuditGateScan.ok, false, requiredDeviceEvidenceAuditAnchor);
  assert.deepEqual(missingDeviceEvidenceAuditGateScan.blockers.map((blocker) => blocker.reason), [
    'device_smoke_evidence_audit_chain_missing',
  ]);
}

const liveVideoTokenGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-device-integration-smoke.mjs',
    content: completeDeviceTokenContent,
  },
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent,
  },
  {
    path: '.scripts/alert-review-player-live-smoke.mjs',
    content: completePlayerSmokeSecurityContent,
  },
  {
    path: '.scripts/record-export-manifest-verifier.mjs',
    content: completeManifestVerifierWrapperSecurityContent,
  },
  ...[
    '.scripts/alert-review-device-integration-smoke.test.mjs',
    '.scripts/alert-review-video-live-smoke.test.mjs',
    '.scripts/alert-review-player-live-smoke.test.mjs',
    '.scripts/alert-review-production-smoke.test.mjs',
  ].map((path) => ({ path, content: completeSignedRelativeCliRegressionTestContent })),
]);
assert.equal(liveVideoTokenGateScan.ok, true);

for (const requiredSafeEndpointAnchor of [
  "hostname.endsWith('.localhost')",
  "hostname.endsWith('.test')",
  "hostname.endsWith('.invalid')",
]) {
  const missingSafeEndpointClassificationScan = scanLiveVideoEvidenceGate([{
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent.replace(`${requiredSafeEndpointAnchor}; `, ''),
  }]);
  assert.equal(missingSafeEndpointClassificationScan.ok, false, requiredSafeEndpointAnchor);
  assert.ok(missingSafeEndpointClassificationScan.blockers.some(
    (blocker) => blocker.reason === 'production_smoke_release_token_endpoint_classification_unsafe',
  ));
}

for (const unsafeEndpointAnchor of ["hostname.includes('mock')", "includes('/mock')"]) {
  const unsafeEndpointClassificationScan = scanLiveVideoEvidenceGate([{
    path: '.scripts/alert-review-production-smoke.mjs',
    content: `${completeProductionSmokeTokenContent} ${unsafeEndpointAnchor};`,
  }]);
  assert.equal(unsafeEndpointClassificationScan.ok, false, unsafeEndpointAnchor);
  assert.ok(unsafeEndpointClassificationScan.blockers.some(
    (blocker) => blocker.reason === 'production_smoke_release_token_endpoint_classification_unsafe',
  ));
}

const playerUrlArgvGateScan = scanLiveVideoEvidenceGate([{
  path: '.scripts/alert-review-production-smoke.mjs',
  content: `${completeProductionSmokeTokenContent} --workbench-url=\${options.playerWorkbenchUrl};`,
}]);
assert.equal(playerUrlArgvGateScan.ok, false);
assert.ok(playerUrlArgvGateScan.blockers.some(
  (blocker) => blocker.reason === 'production_smoke_player_url_argv_wiring_forbidden',
));

for (const playerEnvironmentAnchor of [
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL: options.playerWorkbenchUrl',
  "'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',",
  'CHILD_SENSITIVE_ENV_KEYS',
  'playerLocalStorage: env.YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'playerCookies: env.YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: options.playerLocalStorage',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: options.playerCookies',
]) {
  const missingPlayerEnvironmentIsolationScan = scanLiveVideoEvidenceGate([{
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent.replace(`${playerEnvironmentAnchor}; `, ''),
  }]);
  assert.equal(missingPlayerEnvironmentIsolationScan.ok, false, playerEnvironmentAnchor);
  assert.ok(missingPlayerEnvironmentIsolationScan.blockers.some(
    (blocker) => blocker.reason === 'production_smoke_player_auth_environment_isolation_missing',
  ));
}

for (const [path, content, anchor, reason] of [
  [
    '.scripts/alert-review-device-integration-smoke.mjs',
    completeDeviceTokenContent,
    'console.log(JSON.stringify(sanitizeCliOutputValue;',
    'device_smoke_recursive_output_and_playback_uri_env_guard_missing',
  ],
  [
    '.scripts/alert-review-player-live-smoke.mjs',
    completePlayerSmokeSecurityContent,
    'env: buildBrowserEnvironment(); ',
    'player_smoke_recursive_output_and_child_env_guard_missing',
  ],
  [
    '.scripts/alert-review-video-live-smoke.mjs',
    completeLiveVideoTokenContent,
    'env: buildManifestVerifierEnvironment(); ',
    'live_video_recursive_output_and_verifier_env_guard_missing',
  ],
  [
    '.scripts/alert-review-production-smoke.mjs',
    completeProductionSmokeTokenContent,
    'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: options.videoPlaybackMaterialUri; ',
    'production_smoke_signed_uri_env_and_recursive_output_guard_missing',
  ],
  [
    '.scripts/record-export-manifest-verifier.mjs',
    completeManifestVerifierWrapperSecurityContent,
    'env: buildManifestVerifierEnvironment(); ',
    'manifest_verifier_python_child_env_guard_missing',
  ],
]) {
  const missingHardenedCredentialBoundaryScan = scanLiveVideoEvidenceGate([{
    path,
    content: content.replace(anchor, ''),
  }]);
  assert.equal(missingHardenedCredentialBoundaryScan.ok, false, path);
  assert.ok(
    missingHardenedCredentialBoundaryScan.blockers.some((blocker) => blocker.reason === reason),
    `${path}:${reason}`,
  );
}

const forbiddenPlaybackMaterialArgvWiringScan = scanLiveVideoEvidenceGate([{
  path: '.scripts/alert-review-production-smoke.mjs',
  content: `${completeProductionSmokeTokenContent} --playback-material-uri=\${options.devicePlaybackMaterialUri};`,
}]);
assert.ok(forbiddenPlaybackMaterialArgvWiringScan.blockers.some(
  (blocker) => blocker.reason === 'production_smoke_playback_material_uri_argv_wiring_forbidden',
));

const forbiddenStandaloneDevicePlaybackMaterialArgvScan = scanLiveVideoEvidenceGate([{
  path: '.scripts/alert-review-device-integration-smoke.mjs',
  content: `${completeDeviceTokenContent} parsed.playbackMaterialUri = arg.slice('--playback-material-uri='.length);`,
}]);
assert.ok(forbiddenStandaloneDevicePlaybackMaterialArgvScan.blockers.some(
  (blocker) => blocker.reason === 'device_smoke_playback_material_uri_argv_acceptance_forbidden',
));

for (const [path, content, anchor, reason] of [
  ['.scripts/alert-review-device-integration-smoke.mjs', completeDeviceTokenContent, 'function sanitizeCliOutputText; ', 'device_smoke_cli_output_sanitizer_missing'],
  ['.scripts/alert-review-video-live-smoke.mjs', completeLiveVideoTokenContent, 'function sanitizeCliOutputText; ', 'live_video_cli_output_sanitizer_missing'],
  ['.scripts/alert-review-player-live-smoke.mjs', completePlayerSmokeSecurityContent, 'function sanitizeOutputText; ', 'player_smoke_cli_output_sanitizer_missing'],
  ['.scripts/alert-review-production-smoke.mjs', completeProductionSmokeTokenContent, 'function sanitizeChildOutputForDisplay; ', 'production_smoke_cli_output_sanitizer_missing'],
]) {
  const missingCliSanitizerScan = scanLiveVideoEvidenceGate([{
    path,
    content: content.replace(anchor, ''),
  }]);
  assert.equal(missingCliSanitizerScan.ok, false, path);
  assert.ok(missingCliSanitizerScan.blockers.some((blocker) => blocker.reason === reason), path);
}

for (const [path, reason] of [
  ['.scripts/alert-review-device-integration-smoke.test.mjs', 'device_smoke_signed_relative_cli_regression_test_missing'],
  ['.scripts/alert-review-video-live-smoke.test.mjs', 'live_video_signed_relative_cli_regression_test_missing'],
  ['.scripts/alert-review-player-live-smoke.test.mjs', 'player_smoke_signed_relative_cli_regression_test_missing'],
  ['.scripts/alert-review-production-smoke.test.mjs', 'production_smoke_signed_relative_cli_regression_test_missing'],
]) {
  const missingSignedRelativeCliRegressionScan = scanLiveVideoEvidenceGate([{
    path,
    content: completeSignedRelativeCliRegressionTestContent.replace('record.mp4?token=; ', ''),
  }]);
  assert.equal(missingSignedRelativeCliRegressionScan.ok, false, path);
  assert.ok(missingSignedRelativeCliRegressionScan.blockers.some((blocker) => blocker.reason === reason), path);
}

const missingLiveVideoReleaseTokenGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent
      .replace('!options.allowLocalEndpoints && !hasText(options.token); ', '')
      .replace('missing YFEIEYE_VIDEO_SMOKE_TOKEN; ', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent,
  },
]);
assert.equal(missingLiveVideoReleaseTokenGateScan.ok, false);
assert.deepEqual(missingLiveVideoReleaseTokenGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_release_token_required_missing',
]);

const missingDeviceEnvironmentTokenGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-device-integration-smoke.mjs',
    content: completeDeviceTokenContent.replace('missing YFEIEYE_DEVICE_AUTH_TOKEN;', ''),
  },
]);
assert.equal(missingDeviceEnvironmentTokenGateScan.ok, false);
assert.deepEqual(missingDeviceEnvironmentTokenGateScan.blockers.map((blocker) => blocker.reason), [
  'device_smoke_environment_token_required_missing',
]);

for (const [path, content, reason] of [
  [
    '.scripts/alert-review-device-integration-smoke.mjs',
    `${completeDeviceTokenContent} parsed.token = arg.slice('--token='.length);`,
    'device_smoke_cli_token_acceptance_forbidden',
  ],
  [
    '.scripts/alert-review-video-live-smoke.mjs',
    `${completeLiveVideoTokenContent} parsed.token = arg.slice('--token='.length);`,
    'live_video_cli_token_acceptance_forbidden',
  ],
]) {
  const forbiddenStandaloneCliTokenGateScan = scanLiveVideoEvidenceGate([{ path, content }]);
  assert.equal(forbiddenStandaloneCliTokenGateScan.ok, false, path);
  assert.deepEqual(forbiddenStandaloneCliTokenGateScan.blockers.map((blocker) => blocker.reason), [reason]);
}

const missingLiveVideoBearerWrapperGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent.replace(
      'const fetchImpl = withBearerAuthorization(rawFetchImpl, options.token);',
      '',
    ),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent,
  },
]);
assert.equal(missingLiveVideoBearerWrapperGateScan.ok, false);
assert.deepEqual(missingLiveVideoBearerWrapperGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_bearer_wrapper_missing',
]);

const missingProductionSmokeLiveVideoTokenGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent.replace('YFEIEYE_VIDEO_SMOKE_TOKEN: options.token; ', ''),
  },
]);
assert.equal(missingProductionSmokeLiveVideoTokenGateScan.ok, false);
assert.deepEqual(missingProductionSmokeLiveVideoTokenGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_live_video_token_wiring_missing',
]);

const productionSmokeTokenArgvGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: `${completeProductionSmokeTokenContent} --token=\${options.token};`,
  },
]);
assert.equal(productionSmokeTokenArgvGateScan.ok, false);
assert.deepEqual(productionSmokeTokenArgvGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_token_argv_wiring_forbidden',
]);

const missingProductionSmokeReleaseTokenSourceGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent.replace("options.tokenSource === 'cli'; ", ''),
  },
]);
assert.equal(missingProductionSmokeReleaseTokenSourceGateScan.ok, false);
assert.deepEqual(missingProductionSmokeReleaseTokenSourceGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_release_token_source_gate_missing',
]);

const missingProductionSmokeTokenMaskGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoTokenContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeTokenContent
      .replace('maskSensitiveArg; ', '')
      .replace("value.startsWith('--token='); ", '')
      .replace("return '--token=***'; ", ''),
  },
]);
assert.equal(missingProductionSmokeTokenMaskGateScan.ok, false);
assert.deepEqual(missingProductionSmokeTokenMaskGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_token_log_mask_missing',
]);

const missingVideoRecordGapReasonCatalogScan = scanLiveVideoEvidenceGate([
  {
    path: 'VIDEO/app/services/record_video_service.py',
    content: completeRecordVideoServiceContent
      .replace("'file_expired': 'retention_expired'; ", '')
      .replace("'video_url_not_configured': 'configuration'; ", '')
      .replace("'file_missing': 'filesystem'; ", '')
      .replace("'probe_failed': 'probe'; ", '')
      .replace("'disk_full': 'storage'; ", '')
      .replace("'cache_flush_failed': 'cache'; ", ''),
  },
]);
assert.equal(missingVideoRecordGapReasonCatalogScan.ok, false);
assert.deepEqual(missingVideoRecordGapReasonCatalogScan.blockers.map((blocker) => blocker.reason), [
  'video_record_gap_reason_catalog_missing',
]);

const missingVideoRecordGapReasonTokenNormalizerScan = scanLiveVideoEvidenceGate([
  {
    path: 'VIDEO/app/services/record_video_service.py',
    content: completeRecordVideoServiceContent
      .replace('_normalize_gap_reason_token; ', '')
      .replace('isalnum; ', '')
      .replace("normalized.strip('_'); ", ''),
  },
]);
assert.equal(missingVideoRecordGapReasonTokenNormalizerScan.ok, false);
assert.deepEqual(missingVideoRecordGapReasonTokenNormalizerScan.blockers.map((blocker) => blocker.reason), [
  'video_record_gap_reason_catalog_missing',
]);

const missingLiveVideoManifestVerifierTimeoutGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replace('timeout: timeoutMs; ', '')
      .replace('ETIMEDOUT; ', '')
      .replace('timed out after; ', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoManifestVerifierTimeoutGateScan.ok, false);
assert.deepEqual(missingLiveVideoManifestVerifierTimeoutGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_manifest_verifier_timeout_missing',
]);

const missingLiveVideoStorageDriftReasonGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replace('REQUIRED_STORAGE_DRIFT_REASON_KEYS; ', '')
      .replace('storageDriftReasonKeys; ', '')
      .replace('standardReasonKeys; ', '')
      .replace('missing standard reason evidence; ', '')
      .replace('file_missing; ', '')
      .replace('retention_expired; ', '')
      .replace('disk_full; ', '')
      .replace('cache_flush_failed; ', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoStorageDriftReasonGateScan.ok, false);
assert.deepEqual(missingLiveVideoStorageDriftReasonGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_storage_drift_reason_evidence_missing',
]);

const missingProductionSmokeStorageDriftReasonGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('REQUIRED_STORAGE_DRIFT_REASON_KEYS; ', '')
      .replace('summary.storageDriftSummary?.standardReasonKeys; ', '')
      .replace('missing standard storage drift reason evidence; ', '')
      .replace('file_missing; ', '')
      .replace('retention_expired; ', '')
      .replace('disk_full; ', '')
      .replace('cache_flush_failed; ', ''),
  },
]);
assert.equal(missingProductionSmokeStorageDriftReasonGateScan.ok, false);
assert.deepEqual(missingProductionSmokeStorageDriftReasonGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_storage_drift_reason_evidence_missing',
]);

const missingProductionSmokeStorageDriftSummaryWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildStorageDriftSummary(payload.storageDriftSummary); ', '')
      .replace('copyBooleanIfPresent(storageDriftSummary, source, \'healthy\'); ', '')
      .replace('copyNumberIfPresent(storageDriftSummary, source, \'recordCount\'); ', '')
      .replace('copyNumberIfPresent(storageDriftSummary, source, \'issueCount\'); ', '')
      .replace('issueReasons; ', '')
      .replace('standardReasonKeys; ', ''),
  },
]);
assert.equal(missingProductionSmokeStorageDriftSummaryWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeStorageDriftSummaryWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_storage_drift_summary_whitelist_missing',
]);

const missingProductionSmokePlayerNativeTimeGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('--assert-native-current-time; ', ''),
  },
]);
assert.equal(missingProductionSmokePlayerNativeTimeGateScan.ok, false);
assert.deepEqual(missingProductionSmokePlayerNativeTimeGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_player_native_time_assert_missing',
]);

const missingProductionSmokePlayerNativeTimeEvidenceGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('Number.isFinite(player.nativeCurrentTime); ', '')
      .replace('missing native currentTime evidence; ', ''),
  },
]);
assert.equal(missingProductionSmokePlayerNativeTimeEvidenceGateScan.ok, false);
assert.deepEqual(missingProductionSmokePlayerNativeTimeEvidenceGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_player_native_time_evidence_missing',
]);

const missingProductionSmokePlayerRecordPathSanitizerGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('copySanitizedUrlIfPresent(player, payload, \'recordPath\'); ', ''),
  },
]);
assert.equal(missingProductionSmokePlayerRecordPathSanitizerGateScan.ok, false);
assert.deepEqual(missingProductionSmokePlayerRecordPathSanitizerGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_player_record_path_sanitizer_missing',
]);

const missingProductionSmokeExportResultSanitizerGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildExportResultSummary(payload.exportResult); ', '')
      .replace('copySanitizedUrlIfPresent(exportResult, source, \'downloadUrl\'); ', '')
      .replace('copySanitizedUrlIfPresent(exportResult, source, \'manifestUrl\'); ', ''),
  },
]);
assert.equal(missingProductionSmokeExportResultSanitizerGateScan.ok, false);
assert.deepEqual(missingProductionSmokeExportResultSanitizerGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_export_result_sanitizer_missing',
]);

const missingProductionSmokeExportResultWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('copyTextIfPresent(exportResult, source, \'exportId\'); ', ''),
  },
]);
assert.equal(missingProductionSmokeExportResultWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeExportResultWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_export_result_whitelist_missing',
]);

const rawSpreadProductionSmokeExportResultGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: `${completeProductionSmokeContent} const exportResult = { ...source };`,
  },
]);
assert.equal(rawSpreadProductionSmokeExportResultGateScan.ok, false);
assert.deepEqual(rawSpreadProductionSmokeExportResultGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_export_result_raw_spread_present',
]);

const missingProductionSmokeManifestVerificationWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildManifestVerificationSummary(payload.manifestVerification); ', '')
      .replace('copyBooleanIfPresent(manifestVerification, source, \'valid\'); ', '')
      .replace('copyBooleanIfPresent(manifestVerification, source, \'signatureValid\'); ', '')
      .replace('copyBooleanIfPresent(manifestVerification, source, \'signatureKeyAvailable\'); ', '')
      .replace('copyTextIfPresent(manifestVerification, source, \'keyId\'); ', '')
      .replace('copyTextIfPresent(manifestVerification, source, \'signatureVersion\'); ', '')
      .replace('violations; ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestVerificationWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestVerificationWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_verification_whitelist_missing',
]);

const missingProductionSmokeManifestSignatureWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildManifestSignatureSummary(payload.manifestSignature); ', '')
      .replace('copyTextIfPresent(manifestSignature, source, \'algorithm\'); ', '')
      .replace('copyTextIfPresent(manifestSignature, source, \'keyId\'); ', '')
      .replace('copyTextIfPresent(manifestSignature, source, \'signatureVersion\'); ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestSignatureWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestSignatureWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_signature_whitelist_missing',
]);

const missingProductionSmokeManifestStorageLifecycleWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildManifestStorageLifecycleSummary(payload.manifestStorageLifecycle); ', '')
      .replace('copyTextIfPresent(manifestStorageLifecycle, source, \'storageType\'); ', '')
      .replace('copyTextIfPresent(manifestStorageLifecycle, source, \'status\'); ', '')
      .replace('copyTextIfPresent(manifestStorageLifecycle, source, \'expiresAt\'); ', '')
      .replace('copyTextIfPresent(manifestStorageLifecycle, source, \'exportPackageObjectKey\'); ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestStorageLifecycleWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestStorageLifecycleWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_storage_lifecycle_whitelist_missing',
]);

const missingProductionSmokePlaybackSummaryWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildPlaybackAccessSummary(payload.playback); ', '')
      .replace('copyTextIfPresent(playback, source, \'grantedDecision\'); ', '')
      .replace('copyTextIfPresent(playback, source, \'deniedDecision\'); ', '')
      .replace('deniedReasons; ', ''),
  },
]);
assert.equal(missingProductionSmokePlaybackSummaryWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokePlaybackSummaryWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_playback_summary_whitelist_missing',
]);

const missingProductionSmokeTypecheckRetryGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('--pm-on-fail=ignore; ', '')
      .replace('pnpm_version_guard; ', '')
      .replace('typecheckRetry; ', ''),
  },
]);
assert.equal(missingProductionSmokeTypecheckRetryGateScan.ok, false);
assert.deepEqual(missingProductionSmokeTypecheckRetryGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_typecheck_pnpm_guard_retry_missing',
]);

const missingProductionSmokeVisibleCopyGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('W4:visible-copy; ', '')
      .replace('alert-review-visible-copy-scan.mjs; ', '')
      .replace('visible-copy files for replacement characters; ', ''),
  },
]);
assert.equal(missingProductionSmokeVisibleCopyGateScan.ok, false);
assert.deepEqual(missingProductionSmokeVisibleCopyGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_visible_copy_preflight_missing',
]);

const liveVideoCoverageUrlAliasGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: `${completeLiveVideoSmokeContent} recordCoverageQueryUrl = parsed.alertRecordQueryUrl;`,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(liveVideoCoverageUrlAliasGateScan.ok, false);
assert.deepEqual(liveVideoCoverageUrlAliasGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_coverage_url_alias_present',
]);

const missingLiveVideoCoverageUrlRuntimeAliasGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replace('sameReleaseEndpoint; ', '')
      .replace('record coverage URL must not equal alert record query URL; ', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoCoverageUrlRuntimeAliasGateScan.ok, false);
assert.deepEqual(missingLiveVideoCoverageUrlRuntimeAliasGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_coverage_url_runtime_alias_guard_missing',
]);

const missingLiveVideoNonExportableReasonSummaryScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replaceAll('describeNonPlayableSegments', '')
      .replaceAll('non_exportable_reason', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoNonExportableReasonSummaryScan.ok, false);
assert.deepEqual(missingLiveVideoNonExportableReasonSummaryScan.blockers.map((blocker) => blocker.reason), [
  'live_video_non_exportable_reason_summary_missing',
]);

const missingLiveVideoCoverageClassificationScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replaceAll('validateCoverageClassification', '')
      .replaceAll('coverageSummary', '')
      .replaceAll('retainMode', '')
      .replaceAll('coverageSource', '')
      .replaceAll('record coverage query missing retain mode or source classification evidence', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoCoverageClassificationScan.ok, false);
assert.deepEqual(missingLiveVideoCoverageClassificationScan.blockers.map((blocker) => blocker.reason), [
  'live_video_coverage_classification_evidence_missing',
]);

const missingLiveVideoCoverageClassificationCatalogScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replaceAll('STANDARD_COVERAGE_CLASSIFICATIONS', '')
      .replaceAll('continuous; motion; alert; detection; ', '')
      .replaceAll('record coverage query returned non-standard retain mode or source classification', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoCoverageClassificationCatalogScan.ok, false);
assert.deepEqual(missingLiveVideoCoverageClassificationCatalogScan.blockers.map((blocker) => blocker.reason), [
  'live_video_coverage_classification_catalog_missing',
]);

const missingLiveVideoCoverageClassificationNormalizerScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replaceAll('normalizeCoverageClassification', '')
      .replaceAll("normalized === 'all'; normalized === 'record'; normalized === 'recording'; return 'continuous'; ", ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoCoverageClassificationNormalizerScan.ok, false);
assert.deepEqual(missingLiveVideoCoverageClassificationNormalizerScan.blockers.map((blocker) => blocker.reason), [
  'live_video_coverage_classification_normalizer_missing',
]);

const missingProductionSmokeCoverageClassificationScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replaceAll('payload.coverageSummary', '')
      .replaceAll('summary.coverageSummary', '')
      .replaceAll('buildCoverageSummary', '')
      .replaceAll('retainMode', '')
      .replaceAll('coverageSource', '')
      .replaceAll('missing coverage retain/source evidence', ''),
  },
]);
assert.equal(missingProductionSmokeCoverageClassificationScan.ok, false);
assert.deepEqual(missingProductionSmokeCoverageClassificationScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_coverage_classification_summary_missing',
]);

const missingLiveVideoManifestVerifierRequiredGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replace('missing --manifest-verifier-script; ', '')
      .replace('YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; ', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoManifestVerifierRequiredGateScan.ok, false);
assert.deepEqual(missingLiveVideoManifestVerifierRequiredGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_manifest_verifier_required_missing',
]);

const missingLiveVideoManifestStorageReferenceGuardScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent
      .replace('assertReleaseMediaEvidence(options, "export package storage reference", objectKey); ', ''),
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent,
  },
]);
assert.equal(missingLiveVideoManifestStorageReferenceGuardScan.ok, false);
assert.deepEqual(missingLiveVideoManifestStorageReferenceGuardScan.blockers.map((blocker) => blocker.reason), [
  'live_video_manifest_storage_reference_guard_missing',
]);

const missingProductionSmokeEvidenceFileRequiredGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('evidenceOutputFile; ', '')
      .replace('missing --evidence-output-file; ', '')
      .replace('YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE; ', ''),
  },
]);
assert.equal(missingProductionSmokeEvidenceFileRequiredGateScan.ok, false);
assert.deepEqual(missingProductionSmokeEvidenceFileRequiredGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_evidence_output_required_missing',
]);

const missingProductionSmokeStepTimeoutGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('stepTimeoutMs; ', '')
      .replace('--step-timeout-ms; ', '')
      .replace('YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS; ', '')
      .replace('timeout: step.timeoutMs; ', '')
      .replace('summary.timeout; ', '')
      .replace('timed out after; ', ''),
  },
]);
assert.equal(missingProductionSmokeStepTimeoutGateScan.ok, false);
assert.deepEqual(missingProductionSmokeStepTimeoutGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_step_timeout_missing',
]);

const missingProductionSmokeChildTimeoutGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent.replace('--timeout-ms=${options.stepTimeoutMs}; ', ''),
  },
]);
assert.equal(missingProductionSmokeChildTimeoutGateScan.ok, false);
assert.deepEqual(missingProductionSmokeChildTimeoutGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_child_timeout_missing',
]);

const missingProductionSmokeManifestVerifierRequiredGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('videoManifestVerifierScript; ', '')
      .replace('missing --video-manifest-verifier-script; ', '')
      .replace('YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestVerifierRequiredGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestVerifierRequiredGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_verifier_required_missing',
]);

const missingProductionSmokeManifestVerifierEvidenceGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('summary.manifestVerification?.valid; ', '')
      .replace('missing valid manifest verifier evidence; ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestVerifierEvidenceGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestVerifierEvidenceGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_verifier_evidence_required_missing',
]);

const missingProductionSmokeManifestVerifierSignatureGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('summary.manifestVerification.signatureValid; ', '')
      .replace('summary.manifestVerification.signatureKeyAvailable; ', '')
      .replace('missing HMAC manifest verifier signature evidence; ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestVerifierSignatureGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestVerifierSignatureGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_verifier_signature_evidence_missing',
]);

const missingProductionSmokeManifestStorageLifecycleGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('payload.manifestStorageLifecycle; ', '')
      .replace('summary.manifestStorageLifecycle = payload.manifestStorageLifecycle; ', '')
      .replace('summary.manifestStorageLifecycle?.status; ', '')
      .replace('missing persisted manifest storage lifecycle evidence; ', ''),
  },
]);
assert.equal(missingProductionSmokeManifestStorageLifecycleGateScan.ok, false);
assert.deepEqual(missingProductionSmokeManifestStorageLifecycleGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_manifest_storage_lifecycle_missing',
]);

const missingLiveVideoEvidenceGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); hmac-sha256;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.exportResult;',
  },
]);
assert.equal(missingLiveVideoEvidenceGateScan.ok, false);
assert.deepEqual(missingLiveVideoEvidenceGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_manifest_signature_summary_missing',
  'live_video_manifest_verifier_summary_missing',
  'live_video_media_evidence_gate_missing',
  'production_smoke_manifest_signature_summary_missing',
  'production_smoke_manifest_verifier_summary_missing',
  'production_smoke_manifest_verifier_required_missing',
]);

const missingLiveVideoSignatureValueGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; validateClipWindows(sourceSegments); invalid clip window; validateManifestConcatOrder(recordSegments, concatOrder); normalizeConcatOrderEntry(entry); validateRootConcatOrderCoverage(segmentOrderEntries, orderEntries, recordSegments.length); concatOrder.map; entry.index; duplicate concat order index; invalid concat order index; references missing segment index; omits segment index; does not match segment count; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoSignatureValueGateScan.ok, false);
assert.deepEqual(missingLiveVideoSignatureValueGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_manifest_signature_summary_missing',
]);

const missingLiveVideoVerifierExitGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); spawnSync(process.execPath); hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; validateClipWindows(sourceSegments); invalid clip window; validateManifestConcatOrder(recordSegments, concatOrder); normalizeConcatOrderEntry(entry); validateRootConcatOrderCoverage(segmentOrderEntries, orderEntries, recordSegments.length); concatOrder.map; entry.index; duplicate concat order index; invalid concat order index; references missing segment index; omits segment index; does not match segment count; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoVerifierExitGateScan.ok, false);
assert.deepEqual(missingLiveVideoVerifierExitGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_manifest_verifier_summary_missing',
]);

const missingProductionSmokeAuditChainGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: `payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; requiredOptionErrors; evidenceOutputFile; missing --evidence-output-file; YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE; ${productionSmokeTimeoutEvidenceContent} liveDeviceEvidenceError; childSmokeSummary; evidence_download_audited;`,
  },
]);
assert.equal(missingProductionSmokeAuditChainGateScan.ok, false);
assert.deepEqual(missingProductionSmokeAuditChainGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_audit_chain_summary_missing',
]);

for (const requiredAuditIdentityAnchor of [
  'evidence_audit_chain_verified',
  "copyPositiveIdIfPresent(summary, payload, 'eventId')",
  "copyPositiveIdListIfPresent(summary, payload, 'eventIds')",
  'missing eventIds evidence',
  'auditChain reviewCaseId does not match reviewCaseId',
  'auditChain reviewItemIds do not exactly match reviewItemId',
  'auditChain eventIds do not exactly match eventId',
  'auditChain exportJobNo does not match exportJobNo',
]) {
  const missingProductionSmokeAuditIdentityScan = scanLiveVideoEvidenceGate([
    {
      path: '.scripts/alert-review-production-smoke.mjs',
      content: completeProductionSmokeContent.replace(requiredAuditIdentityAnchor, ''),
    },
  ]);
  assert.equal(missingProductionSmokeAuditIdentityScan.ok, false, requiredAuditIdentityAnchor);
  assert.deepEqual(missingProductionSmokeAuditIdentityScan.blockers.map((blocker) => blocker.reason), [
    'production_smoke_audit_chain_summary_missing',
  ]);
}

const missingProductionSmokeAuditChainScalarWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('firstAuditScalar; ', '')
      .replace('resolveExactAuditIdList; ', '')
      .replace('normalizeAuditIdList; ', '')
      .replace('normalizeAuditScalar; ', ''),
  },
]);
assert.equal(missingProductionSmokeAuditChainScalarWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeAuditChainScalarWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_audit_chain_scalar_whitelist_missing',
]);

const missingProductionSmokePlaybackAccessGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: `payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; requiredOptionErrors; evidenceOutputFile; missing --evidence-output-file; YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE; ${productionSmokeTimeoutEvidenceContent} liveDeviceEvidenceError; childSmokeSummary; evidence_download_audited; ${productionSmokeAuditIdentityContent}`,
  },
]);
assert.equal(missingProductionSmokePlaybackAccessGateScan.ok, false);
assert.deepEqual(missingProductionSmokePlaybackAccessGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_playback_access_evidence_missing',
]);

const missingProductionSmokeRuleSemanticsEvidenceGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: `payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT; requiredOptionErrors; evidenceOutputFile; missing --evidence-output-file; YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE; ${productionSmokeTimeoutEvidenceContent} liveDeviceEvidenceError; childSmokeSummary; evidence_download_audited; ${productionSmokeAuditIdentityContent} liveDevicePlaybackEvidenceError; summary.playback; grantedDecision; deniedDecision; camera_not_allowed; missing playback URL allow/deny decision evidence; missing playback URL deny reason evidence;`,
  },
]);
assert.equal(missingProductionSmokeRuleSemanticsEvidenceGateScan.ok, false);
assert.deepEqual(missingProductionSmokeRuleSemanticsEvidenceGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_rule_semantics_evidence_missing',
]);

const missingProductionSmokeRuleEvidenceWhitelistGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: completeLiveVideoSmokeContent,
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: completeProductionSmokeContent
      .replace('buildRuleEvidenceSummary(payload); ', '')
      .replace('copyTextIfPresent(ruleEvidence, source, \'ruleCode\'); ', '')
      .replace('copyTextIfPresent(ruleEvidence, source, \'cameraId\'); ', '')
      .replace('copyTextIfPresent(ruleEvidence, source, \'zoneCode\'); ', '')
      .replace('copyTextIfPresent(ruleEvidence, source, \'objectLabel\'); ', '')
      .replace('copyNumberIfPresent(ruleEvidence, source, \'inertiaFrames\'); ', '')
      .replace('copyNumberIfPresent(ruleEvidence, source, \'loiteringSeconds\'); ', ''),
  },
]);
assert.equal(missingProductionSmokeRuleEvidenceWhitelistGateScan.ok, false);
assert.deepEqual(missingProductionSmokeRuleEvidenceWhitelistGateScan.blockers.map((blocker) => blocker.reason), [
  'production_smoke_rule_evidence_whitelist_missing',
]);

const missingLiveVideoFilePathGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value);',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoFilePathGateScan.ok, false);
assert.deepEqual(missingLiveVideoFilePathGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoAbsolutePathGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoAbsolutePathGateScan.ok, false);
assert.deepEqual(missingLiveVideoAbsolutePathGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoProtocolRelativeGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoProtocolRelativeGateScan.ok, false);
assert.deepEqual(missingLiveVideoProtocolRelativeGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoRelativeMockPathGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); raw.startsWith; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoRelativeMockPathGateScan.ok, false);
assert.deepEqual(missingLiveVideoRelativeMockPathGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoInlineMediaGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoInlineMediaGateScan.ok, false);
assert.deepEqual(missingLiveVideoInlineMediaGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoDownloadProbeHeaderGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoDownloadProbeHeaderGateScan.ok, false);
assert.deepEqual(missingLiveVideoDownloadProbeHeaderGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoManifestHashGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoManifestHashGateScan.ok, false);
assert.deepEqual(missingLiveVideoManifestHashGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoFfmpegCommandHashGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoFfmpegCommandHashGateScan.ok, false);
assert.deepEqual(missingLiveVideoFfmpegCommandHashGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoClipWindowGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoClipWindowGateScan.ok, false);
assert.deepEqual(missingLiveVideoClipWindowGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoConcatOrderGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; validateClipWindows(sourceSegments); invalid clip window; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoConcatOrderGateScan.ok, false);
assert.deepEqual(missingLiveVideoConcatOrderGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoRootConcatOrderGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; validateClipWindows(sourceSegments); invalid clip window; validateManifestConcatOrder(recordSegments, concatOrder); duplicate concat order index; invalid concat order index; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoRootConcatOrderGateScan.ok, false);
assert.deepEqual(missingLiveVideoRootConcatOrderGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const missingLiveVideoRootConcatOrderCoverageGateScan = scanLiveVideoEvidenceGate([
  {
    path: '.scripts/alert-review-video-live-smoke.mjs',
    content: 'validateManifestSignature(manifest); isHmacSha256SignatureValue(value); signature value is not canonical hmac-sha256; runManifestVerifierIfConfigured(); manifestSignature; manifestVerification; manifestVerifierScript; runManifestVerifierScript(scriptPath, manifest); result.status !== 0; verifier failed with exit; hmac-sha256; signatureVersion; keyId; assertReleaseMediaEvidence(options, "record URI", value); assertReleaseSegmentMediaEvidence(options, segment); assertReleaseMediaEvidence(options, "download URL", value); assertReleaseMediaEvidence(options, "manifest URL", value); looksLocalOrMockMediaEvidence(value); looksInlineOrOpaqueMediaEvidence(value); looksAbsoluteLocalPathEvidence(value); data:; blob:; about:; validateDownloadProbeHeaders(response); isVideoDownloadContentType(contentType); content-type; content-length; video/; application/octet-stream; isSha256Digest(value); [a-f0-9]{64}; invalid source segment hash; invalid output file hash; ffmpegCommandHashes; invalid ffmpeg command hash; validateClipWindows(sourceSegments); invalid clip window; validateManifestConcatOrder(recordSegments, concatOrder); normalizeConcatOrderEntry(entry); concatOrder.map; entry.index; duplicate concat order index; invalid concat order index; raw.startsWith; mock/; mock\\; https:${raw}; recordUriSource; file_path;',
  },
  {
    path: '.scripts/alert-review-production-smoke.mjs',
    content: 'payload.manifestSignature; summary.manifestSignature = payload.manifestSignature; payload.manifestVerification; summary.manifestVerification = payload.manifestVerification; videoManifestVerifierScript; missing --video-manifest-verifier-script; YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT;',
  },
]);
assert.equal(missingLiveVideoRootConcatOrderCoverageGateScan.ok, false);
assert.deepEqual(missingLiveVideoRootConcatOrderCoverageGateScan.blockers.map((blocker) => blocker.reason), [
  'live_video_media_evidence_gate_missing',
]);

const completeProdSmokeTraceabilityCommand = '- Production smoke with real VIDEO URLs:\n  `YFEIEYE_DEVICE_AUTH_TOKEN=... YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI=... YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI=... YFEIEYE_REVIEW_PLAYER_SMOKE_URL=... node .scripts/alert-review-production-smoke.mjs --device-base-url=... --tenant-id=... --operator-user-id=... --device-alert-time=... --device-playback-allowed-camera-ids=... --device-playback-denied-camera-ids=... --video-alert-record-query-url=... --video-record-coverage-query-url=... --video-record-base-url=... --video-record-export-url=... --video-device-id=... --video-alert-time=... --video-record-drift-retention-hours=24 --video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs --step-timeout-ms=900000 --player-review-row-text=... --player-expected-seek-time=... --player-expected-record-path-contains=... --player-expected-offset-seconds=... --player-coverage-expected-seek-time=... --player-coverage-expected-record-path-contains=... --player-coverage-expected-offset-seconds=0 --player-case-timeline-expected-seek-time=... --player-case-timeline-expected-record-path-contains=... --player-case-timeline-expected-offset-seconds=0 --evidence-output-file=artifacts/production-smoke.json`';

const missingProdSmokeTokenEnvTraceabilityGateScan = scanReleaseTraceabilityGate([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: completeProdSmokeTraceabilityCommand.replace('YFEIEYE_DEVICE_AUTH_TOKEN=... ', ''),
  },
]);
assert.equal(missingProdSmokeTokenEnvTraceabilityGateScan.ok, false);
assert.deepEqual(missingProdSmokeTokenEnvTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_token_env_missing',
]);

const missingProdSmokeSignedUriEnvTraceabilityGateScan = scanReleaseTraceabilityGate([{
  path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
  content: completeProdSmokeTraceabilityCommand
    .replace('YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI=... ', '')
    .replace('YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI=... ', '')
    .replace('YFEIEYE_REVIEW_PLAYER_SMOKE_URL=... ', ''),
}]);
assert.deepEqual(missingProdSmokeSignedUriEnvTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_device_playback_material_env_missing',
  'fr38_prod_smoke_video_playback_material_env_missing',
  'fr38_prod_smoke_player_workbench_env_missing',
]);

const forbiddenProdSmokeSignedUriArgvTraceabilityGateScan = scanReleaseTraceabilityGate([{
  path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
  content: `${completeProdSmokeTraceabilityCommand} --device-playback-material-uri=... --video-playback-material-uri=...`,
}]);
assert.deepEqual(forbiddenProdSmokeSignedUriArgvTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_device_playback_material_argv_forbidden',
  'fr38_prod_smoke_video_playback_material_argv_forbidden',
]);

const missingProdSmokeTraceabilityGateScan = scanReleaseTraceabilityGate([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: [
      '- `ProdSmoke`: `node .scripts/alert-review-production-smoke.mjs --video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs`',
      '- Production smoke with real VIDEO URLs:',
      completeProdSmokeTraceabilityCommand
        .split('\n')[1]
        .replace('--video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs ', ''),
    ].join('\n'),
  },
]);
assert.equal(missingProdSmokeTraceabilityGateScan.ok, false);
assert.deepEqual(missingProdSmokeTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_manifest_verifier_command_missing',
]);

const missingProdSmokeStepTimeoutTraceabilityGateScan = scanReleaseTraceabilityGate([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: completeProdSmokeTraceabilityCommand.replace('--step-timeout-ms=900000 ', ''),
  },
]);
assert.equal(missingProdSmokeStepTimeoutTraceabilityGateScan.ok, false);
assert.deepEqual(missingProdSmokeStepTimeoutTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_step_timeout_command_missing',
]);

const missingProdSmokeRealDevicePlayerTraceabilityGateScan = scanReleaseTraceabilityGate([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: completeProdSmokeTraceabilityCommand
      .replace('--device-base-url=... ', '')
      .replace('--player-expected-offset-seconds=... ', ''),
  },
]);
assert.equal(missingProdSmokeRealDevicePlayerTraceabilityGateScan.ok, false);
assert.deepEqual(missingProdSmokeRealDevicePlayerTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_device_base_command_missing',
  'fr38_prod_smoke_detail_player_offset_command_missing',
]);

const missingProdSmokeTenantTraceabilityGateScan = scanReleaseTraceabilityGate([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: completeProdSmokeTraceabilityCommand.replace('--tenant-id=... ', ''),
  },
]);
assert.equal(missingProdSmokeTenantTraceabilityGateScan.ok, false);
assert.deepEqual(missingProdSmokeTenantTraceabilityGateScan.blockers.map((blocker) => blocker.reason), [
  'fr38_prod_smoke_tenant_command_missing',
]);

const completeProdSmokeTraceabilityGateScan = scanReleaseTraceabilityGate([
  {
    path: 'docs/requirements/alert-review-frigate-fr01-fr38-hardening-review.md',
    content: completeProdSmokeTraceabilityCommand,
  },
]);
assert.equal(completeProdSmokeTraceabilityGateScan.ok, true);

const requireClean = evaluateStatus(`
A  WEB/src/views/alert/components/AlertReviewWorkbench.vue
`, { requireClean: true });
assert.equal(requireClean.ok, false);
assert.equal(requireClean.blockers[0].reason, 'dirty');

const tempDir = mkdtempSync(join(tmpdir(), 'alert-review-release-package-'));
try {
  const statusPath = join(tempDir, 'status.txt');
  writeFileSync(
    statusPath,
    '?? DEVICE/iot-system/iot-system-biz/src/main/java/com/basiclab/iot/system/service/supervision/SupervisionAlertReviewServiceImpl.java\n',
  );
  const scriptPath = join(dirname(fileURLToPath(import.meta.url)), 'verify-alert-review-release-package.mjs');
  const cli = spawnSync(process.execPath, [scriptPath, `--status-file=${statusPath}`], {
    encoding: 'utf8',
  });

  assert.equal(cli.status, 1);
  assert.match(cli.stderr, /untracked/);
  assert.match(cli.stderr, /SupervisionAlertReviewServiceImpl\.java/);
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}

console.log('alert review release package verifier tests OK');
