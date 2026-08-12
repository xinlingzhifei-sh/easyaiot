import { mkdirSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const REQUIRED_STORAGE_DRIFT_REASON_KEYS = [
  'file_missing',
  'retention_expired',
  'disk_full',
  'cache_flush_failed',
];
const DEFAULT_STEP_TIMEOUT_MS = 900000;
const CHILD_SENSITIVE_ENV_KEYS = [
  'YFEIEYE_DEVICE_AUTH_TOKEN',
  'YFEIEYE_VIDEO_SMOKE_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
  'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
];

export function parseArgs(args, env = process.env) {
  const parsed = {
    deviceBaseUrl: env.YFEIEYE_DEVICE_BASE_URL || '',
    token: env.YFEIEYE_DEVICE_AUTH_TOKEN || '',
    tokenSource: hasText(env.YFEIEYE_DEVICE_AUTH_TOKEN) ? 'environment' : '',
    tenantId: numberOrNaN(env.YFEIEYE_DEVICE_TENANT_ID || env.YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID),
    operatorUserId: numberOrNaN(env.YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID),
    deviceAlertTime: env.YFEIEYE_DEVICE_SMOKE_ALERT_TIME || '',
    deviceProfile: env.YFEIEYE_DEVICE_SMOKE_PROFILE || 'release',
    deviceId: env.YFEIEYE_DEVICE_SMOKE_DEVICE_ID || '',
    deviceCameraId: env.YFEIEYE_DEVICE_SMOKE_CAMERA_ID || '',
    deviceZoneCode: env.YFEIEYE_DEVICE_SMOKE_ZONE_CODE || 'production-smoke',
    deviceSourceAlertId: env.YFEIEYE_DEVICE_SMOKE_SOURCE_ALERT_ID || '',
    deviceAllowedCameraIds: parseCsvList(env.YFEIEYE_DEVICE_SMOKE_ALLOWED_CAMERA_IDS),
    devicePlaybackReviewItemId: numberOrNaN(env.YFEIEYE_DEVICE_PLAYBACK_REVIEW_ITEM_ID),
    devicePlaybackReviewCaseId: numberOrNaN(env.YFEIEYE_DEVICE_PLAYBACK_REVIEW_CASE_ID),
    devicePlaybackMaterialUri: env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI || '',
    videoPlaybackMaterialUri: env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI || '',
    devicePlaybackAllowedCameraIds: parseCsvList(env.YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS),
    devicePlaybackDeniedCameraIds: parseCsvList(env.YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS),
    devicePlaybackReason: env.YFEIEYE_DEVICE_PLAYBACK_REASON || '',
    videoAlertRecordQueryUrl: env.YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL || '',
    videoRecordCoverageQueryUrl: env.YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL || '',
    videoRecordBaseUrl: env.YFEIEYE_VIDEO_RECORD_BASE_URL || '',
    videoRecordExportUrl: env.YFEIEYE_VIDEO_RECORD_EXPORT_URL || '',
    videoDeviceId: env.YFEIEYE_VIDEO_SMOKE_DEVICE_ID || '',
    videoCameraId: env.YFEIEYE_VIDEO_SMOKE_CAMERA_ID || '',
    videoAlertTime: env.YFEIEYE_VIDEO_SMOKE_ALERT_TIME || '',
    videoRecordDriftRetentionHours: numberOrNaN(env.YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS),
    videoManifestVerifierScript: env.YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT || '',
    playerWorkbenchUrl: env.YFEIEYE_REVIEW_PLAYER_SMOKE_URL || '',
    playerLocalStorage: env.YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE || '',
    playerCookies: env.YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES || '',
    playerReviewRowText: env.YFEIEYE_REVIEW_PLAYER_SMOKE_ROW_TEXT || '',
    playerActionTestId: env.YFEIEYE_REVIEW_PLAYER_SMOKE_ACTION_TESTID || 'alert-review-detail-seek',
    playerExpectedSeekTime: env.YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_SEEK_TIME || '',
    playerExpectedRecordPathContains: env.YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_RECORD_PATH_CONTAINS || '',
    playerExpectedOffsetSeconds: numberOrNaN(env.YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_OFFSET_SECONDS),
    playerCoverageActionTestId: env.YFEIEYE_REVIEW_PLAYER_COVERAGE_ACTION_TESTID || 'alert-review-coverage-seek',
    playerCoverageExpectedSeekTime: env.YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_SEEK_TIME || '',
    playerCoverageExpectedRecordPathContains: env.YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_RECORD_PATH_CONTAINS || '',
    playerCoverageExpectedOffsetSeconds: numberOrNaN(env.YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_OFFSET_SECONDS),
    playerCaseTimelineActionTestId: env.YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_ACTION_TESTID || 'alert-review-case-timeline-seek',
    playerCaseTimelineExpectedSeekTime: env.YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_SEEK_TIME || '',
    playerCaseTimelineExpectedRecordPathContains: env.YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_RECORD_PATH_CONTAINS || '',
    playerCaseTimelineExpectedOffsetSeconds: numberOrNaN(env.YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_OFFSET_SECONDS),
    playerWaitText: env.YFEIEYE_REVIEW_PLAYER_SMOKE_WAIT_TEXT || '',
    allowLocalEndpoints: parseBoolean(env.YFEIEYE_PRODUCTION_SMOKE_ALLOW_LOCAL_ENDPOINTS, false),
    evidenceOutputFile: env.YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE || '',
    stepTimeoutMs: numberOrNaN(env.YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS),
    help: false,
  };

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg === '--allow-local-endpoints') {
      parsed.allowLocalEndpoints = true;
    } else if (arg.startsWith('--evidence-output-file=')) {
      parsed.evidenceOutputFile = arg.slice('--evidence-output-file='.length);
    } else if (arg.startsWith('--step-timeout-ms=')) {
      parsed.stepTimeoutMs = numberOrNaN(arg.slice('--step-timeout-ms='.length));
    } else if (arg.startsWith('--device-base-url=')) {
      parsed.deviceBaseUrl = arg.slice('--device-base-url='.length);
    } else if (arg.startsWith('--token=')) {
      parsed.token = arg.slice('--token='.length);
      parsed.tokenSource = 'cli';
    } else if (arg.startsWith('--tenant-id=')) {
      parsed.tenantId = numberOrNaN(arg.slice('--tenant-id='.length));
    } else if (arg.startsWith('--operator-user-id=')) {
      parsed.operatorUserId = numberOrNaN(arg.slice('--operator-user-id='.length));
    } else if (arg.startsWith('--device-alert-time=')) {
      parsed.deviceAlertTime = arg.slice('--device-alert-time='.length);
    } else if (arg.startsWith('--device-profile=')) {
      parsed.deviceProfile = arg.slice('--device-profile='.length);
    } else if (arg.startsWith('--device-id=')) {
      parsed.deviceId = arg.slice('--device-id='.length);
    } else if (arg.startsWith('--device-camera-id=')) {
      parsed.deviceCameraId = arg.slice('--device-camera-id='.length);
    } else if (arg.startsWith('--device-zone-code=')) {
      parsed.deviceZoneCode = arg.slice('--device-zone-code='.length);
    } else if (arg.startsWith('--device-source-alert-id=')) {
      parsed.deviceSourceAlertId = arg.slice('--device-source-alert-id='.length);
    } else if (arg.startsWith('--device-allowed-camera-ids=')) {
      parsed.deviceAllowedCameraIds = parseCsvList(arg.slice('--device-allowed-camera-ids='.length));
    } else if (arg.startsWith('--device-playback-review-item-id=')) {
      parsed.devicePlaybackReviewItemId = numberOrNaN(arg.slice('--device-playback-review-item-id='.length));
    } else if (arg.startsWith('--device-playback-review-case-id=')) {
      parsed.devicePlaybackReviewCaseId = numberOrNaN(arg.slice('--device-playback-review-case-id='.length));
    } else if (arg.startsWith('--device-playback-material-uri=')) {
      throw new Error('device playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI');
    } else if (arg.startsWith('--video-playback-material-uri=')) {
      throw new Error('VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI');
    } else if (arg.startsWith('--device-playback-allowed-camera-ids=')) {
      parsed.devicePlaybackAllowedCameraIds = parseCsvList(arg.slice('--device-playback-allowed-camera-ids='.length));
    } else if (arg.startsWith('--device-playback-denied-camera-ids=')) {
      parsed.devicePlaybackDeniedCameraIds = parseCsvList(arg.slice('--device-playback-denied-camera-ids='.length));
    } else if (arg.startsWith('--device-playback-reason=')) {
      parsed.devicePlaybackReason = arg.slice('--device-playback-reason='.length);
    } else if (arg.startsWith('--video-alert-record-query-url=')) {
      parsed.videoAlertRecordQueryUrl = arg.slice('--video-alert-record-query-url='.length);
    } else if (arg.startsWith('--video-record-coverage-query-url=')) {
      parsed.videoRecordCoverageQueryUrl = arg.slice('--video-record-coverage-query-url='.length);
    } else if (arg.startsWith('--video-record-base-url=')) {
      parsed.videoRecordBaseUrl = arg.slice('--video-record-base-url='.length);
    } else if (arg.startsWith('--video-record-export-url=')) {
      parsed.videoRecordExportUrl = arg.slice('--video-record-export-url='.length);
    } else if (arg.startsWith('--video-device-id=')) {
      parsed.videoDeviceId = arg.slice('--video-device-id='.length);
    } else if (arg.startsWith('--video-camera-id=')) {
      parsed.videoCameraId = arg.slice('--video-camera-id='.length);
    } else if (arg.startsWith('--video-alert-time=')) {
      parsed.videoAlertTime = arg.slice('--video-alert-time='.length);
    } else if (arg.startsWith('--video-record-drift-retention-hours=')) {
      parsed.videoRecordDriftRetentionHours = numberOrNaN(arg.slice('--video-record-drift-retention-hours='.length));
    } else if (arg.startsWith('--video-manifest-verifier-script=')) {
      parsed.videoManifestVerifierScript = arg.slice('--video-manifest-verifier-script='.length);
    } else if (arg.startsWith('--player-workbench-url=')) {
      const playerWorkbenchUrl = arg.slice('--player-workbench-url='.length);
      if (hasSensitiveUrlQuery(playerWorkbenchUrl)) {
        throw new Error('signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL');
      }
      parsed.playerWorkbenchUrl = playerWorkbenchUrl;
    } else if (arg.startsWith('--player-review-row-text=')) {
      parsed.playerReviewRowText = arg.slice('--player-review-row-text='.length);
    } else if (arg.startsWith('--player-action-testid=')) {
      parsed.playerActionTestId = arg.slice('--player-action-testid='.length);
    } else if (arg.startsWith('--player-expected-seek-time=')) {
      parsed.playerExpectedSeekTime = arg.slice('--player-expected-seek-time='.length);
    } else if (arg.startsWith('--player-expected-record-path-contains=')) {
      parsed.playerExpectedRecordPathContains = arg.slice('--player-expected-record-path-contains='.length);
    } else if (arg.startsWith('--player-expected-offset-seconds=')) {
      parsed.playerExpectedOffsetSeconds = numberOrNaN(arg.slice('--player-expected-offset-seconds='.length));
    } else if (arg.startsWith('--player-coverage-action-testid=')) {
      parsed.playerCoverageActionTestId = arg.slice('--player-coverage-action-testid='.length);
    } else if (arg.startsWith('--player-coverage-expected-seek-time=')) {
      parsed.playerCoverageExpectedSeekTime = arg.slice('--player-coverage-expected-seek-time='.length);
    } else if (arg.startsWith('--player-coverage-expected-record-path-contains=')) {
      parsed.playerCoverageExpectedRecordPathContains = arg.slice('--player-coverage-expected-record-path-contains='.length);
    } else if (arg.startsWith('--player-coverage-expected-offset-seconds=')) {
      parsed.playerCoverageExpectedOffsetSeconds = numberOrNaN(arg.slice('--player-coverage-expected-offset-seconds='.length));
    } else if (arg.startsWith('--player-case-timeline-action-testid=')) {
      parsed.playerCaseTimelineActionTestId = arg.slice('--player-case-timeline-action-testid='.length);
    } else if (arg.startsWith('--player-case-timeline-expected-seek-time=')) {
      parsed.playerCaseTimelineExpectedSeekTime = arg.slice('--player-case-timeline-expected-seek-time='.length);
    } else if (arg.startsWith('--player-case-timeline-expected-record-path-contains=')) {
      parsed.playerCaseTimelineExpectedRecordPathContains = arg.slice('--player-case-timeline-expected-record-path-contains='.length);
    } else if (arg.startsWith('--player-case-timeline-expected-offset-seconds=')) {
      parsed.playerCaseTimelineExpectedOffsetSeconds = numberOrNaN(arg.slice('--player-case-timeline-expected-offset-seconds='.length));
    } else if (arg.startsWith('--player-wait-text=')) {
      parsed.playerWaitText = arg.slice('--player-wait-text='.length);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (!Number.isFinite(parsed.stepTimeoutMs) || parsed.stepTimeoutMs <= 0) {
    parsed.stepTimeoutMs = DEFAULT_STEP_TIMEOUT_MS;
  }
  parsed.deviceId = firstText(parsed.deviceId, parsed.videoDeviceId);
  parsed.deviceCameraId = firstText(parsed.deviceCameraId, parsed.videoCameraId, parsed.videoDeviceId);
  if (!Array.isArray(parsed.deviceAllowedCameraIds) || parsed.deviceAllowedCameraIds.length === 0) {
    parsed.deviceAllowedCameraIds = parsed.devicePlaybackAllowedCameraIds;
  }
  return parsed;
}

export function requiredOptionErrors(options) {
  const errors = [];
  requireText(errors, options.deviceBaseUrl, 'missing --device-base-url or YFEIEYE_DEVICE_BASE_URL');
  requireText(errors, options.token, 'missing --token or YFEIEYE_DEVICE_AUTH_TOKEN');
  if (options.tokenSource === 'cli' && !cliTokenAllowedForLocalEndpoints(options)) {
    errors.push('production smoke release token must come from YFEIEYE_DEVICE_AUTH_TOKEN; --token requires --allow-local-endpoints and local/mock endpoints only');
  }
  requirePositiveNumber(errors, options.tenantId, 'missing --tenant-id or YFEIEYE_DEVICE_TENANT_ID');
  requirePositiveNumber(errors, options.operatorUserId, 'missing --operator-user-id or YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID');
  requireText(errors, options.deviceAlertTime, 'missing --device-alert-time or YFEIEYE_DEVICE_SMOKE_ALERT_TIME');
  requireList(errors, options.devicePlaybackAllowedCameraIds, 'missing --device-playback-allowed-camera-ids or YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS');
  requireList(errors, options.devicePlaybackDeniedCameraIds, 'missing --device-playback-denied-camera-ids or YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS');
  requireText(errors, options.videoAlertRecordQueryUrl, 'missing --video-alert-record-query-url or YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL');
  requireText(errors, options.videoRecordCoverageQueryUrl, 'missing --video-record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL');
  requireText(errors, options.videoRecordBaseUrl, 'missing --video-record-base-url or YFEIEYE_VIDEO_RECORD_BASE_URL');
  requireText(errors, options.videoRecordExportUrl, 'missing --video-record-export-url or YFEIEYE_VIDEO_RECORD_EXPORT_URL');
  requireText(errors, options.videoDeviceId, 'missing --video-device-id or YFEIEYE_VIDEO_SMOKE_DEVICE_ID');
  requireText(errors, options.videoAlertTime, 'missing --video-alert-time or YFEIEYE_VIDEO_SMOKE_ALERT_TIME');
  requirePositiveNumber(errors, options.videoRecordDriftRetentionHours, 'missing --video-record-drift-retention-hours or YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS');
  if (!options.allowLocalEndpoints) {
    requireText(errors, options.videoManifestVerifierScript, 'missing --video-manifest-verifier-script or YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT');
  }
  requireText(errors, options.playerWorkbenchUrl, 'missing --player-workbench-url or YFEIEYE_REVIEW_PLAYER_SMOKE_URL');
  requireText(errors, options.playerReviewRowText, 'missing --player-review-row-text or YFEIEYE_REVIEW_PLAYER_SMOKE_ROW_TEXT');
  requireText(errors, options.playerExpectedSeekTime, 'missing --player-expected-seek-time or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_SEEK_TIME');
  requireText(errors, options.playerExpectedRecordPathContains, 'missing --player-expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_RECORD_PATH_CONTAINS');
  requireNonNegativeNumber(errors, options.playerExpectedOffsetSeconds, 'missing --player-expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_OFFSET_SECONDS');
  requireText(errors, options.playerCoverageExpectedSeekTime, 'missing --player-coverage-expected-seek-time or YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_SEEK_TIME');
  requireText(errors, options.playerCoverageExpectedRecordPathContains, 'missing --player-coverage-expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_RECORD_PATH_CONTAINS');
  requireNonNegativeNumber(errors, options.playerCoverageExpectedOffsetSeconds, 'missing --player-coverage-expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_OFFSET_SECONDS');
  requireText(errors, options.playerCaseTimelineExpectedSeekTime, 'missing --player-case-timeline-expected-seek-time or YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_SEEK_TIME');
  requireText(errors, options.playerCaseTimelineExpectedRecordPathContains, 'missing --player-case-timeline-expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_RECORD_PATH_CONTAINS');
  requireNonNegativeNumber(errors, options.playerCaseTimelineExpectedOffsetSeconds, 'missing --player-case-timeline-expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_OFFSET_SECONDS');
  requireText(errors, options.evidenceOutputFile, 'missing --evidence-output-file or YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE');
  if (!options.allowLocalEndpoints) {
    requireReleaseEndpoint(errors, '--device-base-url', options.deviceBaseUrl);
    requireReleaseEndpoint(errors, '--video-alert-record-query-url', options.videoAlertRecordQueryUrl);
    requireReleaseEndpoint(errors, '--video-record-coverage-query-url', options.videoRecordCoverageQueryUrl);
    requireReleaseEndpoint(errors, '--video-record-base-url', options.videoRecordBaseUrl);
    requireReleaseEndpoint(errors, '--video-record-export-url', options.videoRecordExportUrl);
    requireReleaseEndpoint(errors, '--player-workbench-url', options.playerWorkbenchUrl);
  }
  return errors;
}

export function buildSmokeSteps(options, runtime = {}) {
  const nodePath = runtime.nodePath || process.execPath;
  const pnpmPath = runtime.pnpmPath || defaultPnpmPath();
  const scriptDir = runtime.scriptDir || '.scripts';
  return [
    {
      name: 'W4:visible-copy',
      command: nodePath,
      args: [`${scriptDir}/alert-review-visible-copy-scan.mjs`],
      timeoutMs: options.stepTimeoutMs,
    },
    {
      name: 'W2:typecheck',
      command: pnpmPath,
      args: ['--dir', 'WEB', 'run', 'type:check'],
      timeoutMs: options.stepTimeoutMs,
    },
    {
      name: 'LiveDevice',
      command: nodePath,
      env: {
        YFEIEYE_DEVICE_AUTH_TOKEN: options.token,
        ...(hasText(options.devicePlaybackMaterialUri) ? {
          YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: options.devicePlaybackMaterialUri,
        } : {}),
      },
      timeoutMs: options.stepTimeoutMs,
      args: compact([
        `${scriptDir}/alert-review-device-integration-smoke.mjs`,
        `--device-base-url=${options.deviceBaseUrl}`,
        `--tenant-id=${options.tenantId}`,
        `--operator-user-id=${options.operatorUserId}`,
        `--alert-time=${options.deviceAlertTime}`,
        `--profile=${options.deviceProfile}`,
        `--device-id=${options.deviceId}`,
        `--camera-id=${options.deviceCameraId}`,
        `--zone-code=${options.deviceZoneCode}`,
        hasText(options.deviceSourceAlertId) ? `--source-alert-id=${options.deviceSourceAlertId}` : '',
        cameraListArg('--allowed-camera-ids', options.deviceAllowedCameraIds),
        positiveNumberArg('--playback-review-item-id', options.devicePlaybackReviewItemId),
        positiveNumberArg('--playback-review-case-id', options.devicePlaybackReviewCaseId),
        cameraListArg('--playback-allowed-camera-ids', options.devicePlaybackAllowedCameraIds),
        cameraListArg('--playback-denied-camera-ids', options.devicePlaybackDeniedCameraIds),
        hasText(options.devicePlaybackReason) ? `--playback-reason=${options.devicePlaybackReason}` : '',
        `--timeout-ms=${options.stepTimeoutMs}`,
      ]),
    },
    {
      name: 'LiveVideo',
      command: nodePath,
      env: {
        YFEIEYE_VIDEO_SMOKE_TOKEN: options.token,
        ...(hasText(options.videoPlaybackMaterialUri) ? {
          YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: options.videoPlaybackMaterialUri,
        } : {}),
      },
      timeoutMs: options.stepTimeoutMs,
      args: compact([
        `${scriptDir}/alert-review-video-live-smoke.mjs`,
        `--alert-record-query-url=${options.videoAlertRecordQueryUrl}`,
        `--record-coverage-query-url=${options.videoRecordCoverageQueryUrl}`,
        `--record-base-url=${options.videoRecordBaseUrl}`,
        `--record-export-url=${options.videoRecordExportUrl}`,
        `--device-id=${options.videoDeviceId}`,
        hasText(options.videoCameraId) ? `--camera-id=${options.videoCameraId}` : '',
        `--alert-time=${options.videoAlertTime}`,
        `--record-drift-retention-hours=${options.videoRecordDriftRetentionHours}`,
        hasText(options.videoManifestVerifierScript) ? `--manifest-verifier-script=${options.videoManifestVerifierScript}` : '',
        `--timeout-ms=${options.stepTimeoutMs}`,
        options.allowLocalEndpoints ? '--allow-local-endpoints' : '',
      ]),
    },
    ...buildPlayerSmokeSteps(options, nodePath, scriptDir),
  ];
}

function buildPlayerSmokeSteps(options, nodePath, scriptDir) {
  return [
    playerSmokeStep('LivePlayer:detail', options.playerActionTestId, options.playerExpectedSeekTime, options.playerExpectedRecordPathContains, options.playerExpectedOffsetSeconds, options, nodePath, scriptDir),
    playerSmokeStep('LivePlayer:coverage', options.playerCoverageActionTestId, options.playerCoverageExpectedSeekTime, options.playerCoverageExpectedRecordPathContains, options.playerCoverageExpectedOffsetSeconds, options, nodePath, scriptDir),
    playerSmokeStep('LivePlayer:case-timeline', options.playerCaseTimelineActionTestId, options.playerCaseTimelineExpectedSeekTime, options.playerCaseTimelineExpectedRecordPathContains, options.playerCaseTimelineExpectedOffsetSeconds, options, nodePath, scriptDir),
  ];
}

function playerSmokeStep(name, actionTestId, expectedSeekTime, expectedRecordPathContains, expectedOffsetSeconds, options, nodePath, scriptDir) {
  return {
    name,
    command: nodePath,
    env: {
      YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN: options.token,
      YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID: String(options.tenantId),
      YFEIEYE_REVIEW_PLAYER_SMOKE_URL: options.playerWorkbenchUrl,
      ...(hasText(options.playerLocalStorage) ? {
        YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: options.playerLocalStorage,
      } : {}),
      ...(hasText(options.playerCookies) ? {
        YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: options.playerCookies,
      } : {}),
    },
    allowLocalEndpoints: options.allowLocalEndpoints === true,
    timeoutMs: options.stepTimeoutMs,
    args: compact([
      `${scriptDir}/alert-review-player-live-smoke.mjs`,
      `--review-row-text=${options.playerReviewRowText}`,
      `--action-testid=${actionTestId}`,
      `--expected-seek-time=${expectedSeekTime}`,
      `--expected-record-path-contains=${expectedRecordPathContains}`,
      `--expected-offset-seconds=${expectedOffsetSeconds}`,
      `--timeout-ms=${options.stepTimeoutMs}`,
      '--assert-native-current-time',
      hasText(options.playerWaitText) ? `--wait-text=${options.playerWaitText}` : '',
      options.allowLocalEndpoints ? '--allow-local-endpoints' : '',
    ]),
    evidenceContext: {
      player: compactObject({
        entry: name.replace('LivePlayer:', ''),
        actionTestId,
        reviewRowText: options.playerReviewRowText,
        reviewItemId: Number.isFinite(options.devicePlaybackReviewItemId) ? options.devicePlaybackReviewItemId : undefined,
        reviewCaseId: Number.isFinite(options.devicePlaybackReviewCaseId) ? options.devicePlaybackReviewCaseId : undefined,
        expectedSeekTime,
        expectedRecordPathContains,
        expectedOffsetSeconds,
      }),
    },
  };
}

export async function runProductionSmoke(options, dependencies = {}) {
  const errors = requiredOptionErrors(options);
  if (errors.length) {
    throw new Error(errors.join('\n'));
  }
  const reportStartedAt = currentInstant(dependencies);
  const evidenceReport = {
    ok: false,
    status: 'running',
    startedAt: reportStartedAt.iso,
    finishedAt: null,
    durationMs: null,
    allowLocalEndpoints: options.allowLocalEndpoints === true,
    steps: [],
  };
  const steps = buildSmokeSteps(options, dependencies);
  const runCommand = dependencies.runCommand || defaultRunCommand;
  const results = [];
  for (const step of steps) {
    const stepStartedAt = currentInstant(dependencies);
    let result;
    try {
      result = await runCommand(step);
      result = await retryTypecheckAfterPnpmVersionGuard(step, result, runCommand);
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const stepFinishedAt = currentInstant(dependencies);
      evidenceReport.steps.push(buildEvidenceStep(step, 'failed', stepStartedAt, stepFinishedAt, null, message));
      finishEvidenceReport(evidenceReport, false, reportStartedAt, currentInstant(dependencies));
      writeEvidenceReport(options, evidenceReport, dependencies);
      throw error;
    }
    const status = Number(result?.status ?? 1);
    const stepFinishedAt = currentInstant(dependencies);
    if (status !== 0) {
      const message = `${step.name} failed with exit code ${status}`;
      evidenceReport.steps.push(buildEvidenceStep(step, 'failed', stepStartedAt, stepFinishedAt, status, message, result));
      finishEvidenceReport(evidenceReport, false, reportStartedAt, currentInstant(dependencies));
      writeEvidenceReport(options, evidenceReport, dependencies);
      throw new Error(message);
    }
    const evidenceStep = buildEvidenceStep(step, 'passed', stepStartedAt, stepFinishedAt, status, null, result);
    const evidenceError = passedStepEvidenceError(step, evidenceStep.summary);
    if (evidenceError) {
      evidenceStep.status = 'failed';
      evidenceStep.error = evidenceError;
      evidenceReport.steps.push(evidenceStep);
      finishEvidenceReport(evidenceReport, false, reportStartedAt, currentInstant(dependencies));
      writeEvidenceReport(options, evidenceReport, dependencies);
      throw new Error(evidenceError);
    }
    results.push({
      name: step.name,
      status: 'passed',
    });
    evidenceReport.steps.push(evidenceStep);
  }
  finishEvidenceReport(evidenceReport, true, reportStartedAt, currentInstant(dependencies));
  writeEvidenceReport(options, evidenceReport, dependencies);
  return {
    ok: true,
    steps: results,
  };
}

async function retryTypecheckAfterPnpmVersionGuard(step, result, runCommand) {
  const status = Number(result?.status ?? 1);
  if (step.name !== 'W2:typecheck' || status === 0 || !isPnpmVersionGuardFailure(result)) {
    return result;
  }
  const retryStep = {
    ...step,
    args: ['--dir', 'WEB', '--pm-on-fail=ignore', 'run', 'type:check'],
  };
  const retryResult = await runCommand(retryStep);
  return {
    ...retryResult,
    typecheckRetry: {
      reason: 'pnpm_version_guard',
      originalExitCode: status,
      originalCommand: formatStepCommand(step),
      retryCommand: formatStepCommand(retryStep),
    },
  };
}

function isPnpmVersionGuardFailure(result) {
  const output = [result?.stdout, result?.stderr].filter(hasText).join('\n');
  return output.includes('This project is configured to use')
    && output.includes('Corepack invoked pnpm')
    && output.includes('pmOnFail');
}

export function formatStepCommand(step) {
  return `${step.command} ${step.args.map(maskSensitiveArg).join(' ')}`;
}

function passedStepEvidenceError(step, summary) {
  if (step.name === 'W4:visible-copy' || step.name === 'W2:typecheck') {
    return null;
  }
  if (!summary || typeof summary !== 'object') {
    return `production smoke step ${step.name} did not emit required evidence summary`;
  }
  if (step.name === 'LiveDevice') {
    if (!Array.isArray(summary.checkpoints)) {
      return `production smoke step ${step.name} did not emit required evidence summary`;
    }
    return liveDeviceEvidenceError(step.name, summary);
  }
  if (step.name === 'LiveVideo') {
    if (!Array.isArray(summary.checkpoints)) {
      return `production smoke step ${step.name} did not emit required evidence summary`;
    }
    return liveVideoEvidenceError(step.name, summary);
  }
  if (step.name.startsWith('LivePlayer:')) {
    return livePlayerEvidenceError(step.name, summary.player, step.allowLocalEndpoints === true);
  }
  return null;
}

function liveDeviceEvidenceError(stepName, summary) {
  const missing = missingCheckpoints(summary.checkpoints, [
    'ingest_review_item',
    'review_event_bound_without_task_dispatch',
    'review_rule_saved',
    'record_coverage_synced',
    'review_case_created',
    'evidence_export_ready',
    'manifest_verified',
    'evidence_download_bytes_verified',
    'evidence_download_audited',
    'evidence_audit_chain_verified',
    'playback_url_granted',
    'playback_url_denied',
  ]);
  if (missing.length) {
    return `production smoke step ${stepName} missing evidence checkpoint: ${missing[0]}`;
  }
  if (summary.manifestValid !== true) {
    return `production smoke step ${stepName} did not verify manifestValid=true`;
  }
  if (summary.videoExportRequested !== true) {
    return `production smoke step ${stepName} did not verify videoExportRequested=true`;
  }
  if (!hasText(String(summary.reviewItemId ?? ''))) {
    return `production smoke step ${stepName} missing reviewItemId evidence`;
  }
  if (!hasText(String(summary.reviewCaseId ?? ''))) {
    return `production smoke step ${stepName} missing reviewCaseId evidence`;
  }
  if (!isPositiveId(summary.eventId)) {
    return `production smoke step ${stepName} missing eventId evidence`;
  }
  if (!Array.isArray(summary.eventIds) || summary.eventIds.length === 0) {
    return `production smoke step ${stepName} missing eventIds evidence`;
  }
  if (!isExactAuditIdList(summary.eventIds, summary.eventId)) {
    return `production smoke step ${stepName} eventIds do not exactly match eventId`;
  }
  if (!summary.auditChain || typeof summary.auditChain !== 'object') {
    return `production smoke step ${stepName} missing auditChain evidence`;
  }
  if (summary.auditChain.action !== 'export_downloaded') {
    return `production smoke step ${stepName} missing auditChain export_downloaded evidence`;
  }
  if (!hasText(String(summary.auditChain.reviewCaseId ?? ''))) {
    return `production smoke step ${stepName} missing auditChain reviewCaseId evidence`;
  }
  if (!samePositiveId(summary.auditChain.reviewCaseId, summary.reviewCaseId)) {
    return `production smoke step ${stepName} auditChain reviewCaseId does not match reviewCaseId`;
  }
  if (!Array.isArray(summary.auditChain.reviewItemIds) || summary.auditChain.reviewItemIds.length === 0) {
    return `production smoke step ${stepName} missing auditChain reviewItemIds evidence`;
  }
  if (!isExactAuditIdList(summary.auditChain.reviewItemIds, summary.reviewItemId)) {
    return `production smoke step ${stepName} auditChain reviewItemIds do not exactly match reviewItemId`;
  }
  if (!Array.isArray(summary.auditChain.eventIds) || summary.auditChain.eventIds.length === 0) {
    return `production smoke step ${stepName} missing auditChain eventIds evidence`;
  }
  if (!isExactAuditIdList(summary.auditChain.eventIds, summary.eventId)) {
    return `production smoke step ${stepName} auditChain eventIds do not exactly match eventId`;
  }
  if (!hasText(summary.auditChain.exportJobNo)) {
    return `production smoke step ${stepName} missing auditChain exportJobNo evidence`;
  }
  if (String(summary.auditChain.exportJobNo) !== String(summary.exportJobNo ?? '')) {
    return `production smoke step ${stepName} auditChain exportJobNo does not match exportJobNo`;
  }
  const playbackEvidenceError = liveDevicePlaybackEvidenceError(stepName, summary.playback);
  if (playbackEvidenceError) {
    return playbackEvidenceError;
  }
  const ruleEvidenceError = liveDeviceRuleEvidenceError(stepName, summary.ruleEvidence);
  if (ruleEvidenceError) {
    return ruleEvidenceError;
  }
  return null;
}

function liveDeviceRuleEvidenceError(stepName, ruleEvidence) {
  if (!ruleEvidence || typeof ruleEvidence !== 'object') {
    return `production smoke step ${stepName} missing rule inertia/loitering evidence`;
  }
  if (Number(ruleEvidence.inertiaFrames) !== 3) {
    return `production smoke step ${stepName} missing rule inertiaFrames=3 evidence`;
  }
  if (Number(ruleEvidence.loiteringSeconds) !== 20) {
    return `production smoke step ${stepName} missing rule loiteringSeconds=20 evidence`;
  }
  return null;
}

function liveDevicePlaybackEvidenceError(stepName, playback) {
  if (!playback || typeof playback !== 'object') {
    return `production smoke step ${stepName} missing playback URL allow/deny decision evidence`;
  }
  const grantedDecision = String(playback.grantedDecision || '').toLowerCase();
  const deniedDecision = String(playback.deniedDecision || '').toLowerCase();
  if (grantedDecision !== 'granted' || deniedDecision !== 'denied') {
    return `production smoke step ${stepName} missing playback URL allow/deny decision evidence`;
  }
  const deniedReasons = Array.isArray(playback.deniedReasons)
    ? playback.deniedReasons.map(String)
    : [];
  if (!deniedReasons.includes('camera_not_allowed')) {
    return `production smoke step ${stepName} missing playback URL deny reason evidence`;
  }
  return null;
}

function liveVideoEvidenceError(stepName, summary) {
  const missing = missingCheckpoints(summary.checkpoints, [
    'alert_record_query_ok',
    'record_coverage_query_ok',
    'record_base_space_resolved',
    'record_storage_drift_patrol_ok',
    'record_export_posted',
    'record_export_download_ready',
    'record_export_download_probed',
    'record_export_manifest_verified',
  ]);
  if (missing.length) {
    return `production smoke step ${stepName} missing evidence checkpoint: ${missing[0]}`;
  }
  if (summary.storageDriftSummary?.healthy !== true) {
    return `production smoke step ${stepName} did not prove healthy storage drift patrol`;
  }
  if (!hasText(summary.coverageSummary?.retainMode) || !hasText(summary.coverageSummary?.coverageSource)) {
    return `production smoke step ${stepName} missing coverage retain/source evidence`;
  }
  const missingStorageReason = missingStorageDriftReason(summary.storageDriftSummary?.standardReasonKeys);
  if (missingStorageReason) {
    return `production smoke step ${stepName} missing standard storage drift reason evidence: ${missingStorageReason}`;
  }
  if (!hasText(summary.exportResult?.downloadUrl)) {
    return `production smoke step ${stepName} missing export downloadUrl evidence`;
  }
  if (!hasText(summary.exportResult?.manifestUrl)) {
    return `production smoke step ${stepName} missing export manifestUrl evidence`;
  }
  if (summary.manifestSignature?.algorithm !== 'hmac-sha256'
      || !hasText(summary.manifestSignature?.keyId)
      || !hasText(summary.manifestSignature?.signatureVersion)) {
    return `production smoke step ${stepName} missing HMAC manifest signature evidence`;
  }
  const storageLifecycleStatus = String(summary.manifestStorageLifecycle?.status || '').trim().toLowerCase();
  if (!['persisted', 'retained'].includes(storageLifecycleStatus)
      || !hasText(summary.manifestStorageLifecycle?.storageType)
      || !hasText(summary.manifestStorageLifecycle?.expiresAt)
      || !hasText(summary.manifestStorageLifecycle?.exportPackageObjectKey)) {
    return `production smoke step ${stepName} missing persisted manifest storage lifecycle evidence`;
  }
  if (summary.manifestVerification?.valid !== true) {
    return `production smoke step ${stepName} missing valid manifest verifier evidence`;
  }
  if (summary.manifestVerification.signatureValid !== true
      || summary.manifestVerification.signatureKeyAvailable !== true
      || summary.manifestVerification.keyId !== summary.manifestSignature.keyId
      || summary.manifestVerification.signatureVersion !== summary.manifestSignature.signatureVersion) {
    return `production smoke step ${stepName} missing HMAC manifest verifier signature evidence`;
  }
  return null;
}

function missingStorageDriftReason(reasonKeys) {
  const keys = Array.isArray(reasonKeys)
    ? reasonKeys.map((value) => String(value).trim()).filter(Boolean)
    : [];
  return REQUIRED_STORAGE_DRIFT_REASON_KEYS.find((reason) => !keys.includes(reason));
}

function livePlayerEvidenceError(stepName, player, allowLocalEndpoints) {
  if (!player || typeof player !== 'object') {
    return `production smoke step ${stepName} did not emit required player evidence summary`;
  }
  if (player.clickedRow !== true) {
    return `production smoke step ${stepName} did not prove review row click`;
  }
  if (player.clickedAction !== true) {
    return `production smoke step ${stepName} did not prove seek action click`;
  }
  if (!hasText(player.expectedSeekTime) || player.seekTime !== player.expectedSeekTime) {
    return `production smoke step ${stepName} did not prove expected seek_time`;
  }
  const pathText = `${player.recordPath || ''} ${player.currentUrl || ''}`;
  if (!hasText(player.expectedRecordPathContains) || !pathText.includes(player.expectedRecordPathContains)) {
    return `production smoke step ${stepName} did not prove expected record path`;
  }
  if (!allowLocalEndpoints && hasLocalOrMockMediaEvidence(
    player.recordPath,
    player.currentUrl,
    player.nativeCurrentSrc,
  )) {
    return `production smoke step ${stepName} used local/mock player media evidence`;
  }
  if (Number(player.playbackOffsetSeconds) !== Number(player.expectedOffsetSeconds)) {
    return `production smoke step ${stepName} did not prove playback_offset_seconds`;
  }
  if (!Number.isFinite(player.nativeCurrentTime)) {
    return `production smoke step ${stepName} missing native currentTime evidence`;
  }
  if (Math.abs(Number(player.nativeCurrentTime) - Number(player.expectedOffsetSeconds)) > 1.5) {
    return `production smoke step ${stepName} native currentTime missed expected offset`;
  }
  if (player.nativeErrorPresent !== false) {
    return `production smoke step ${stepName} native video reported a media error`;
  }
  if (!hasText(player.nativeCurrentSrc)
      || !player.nativeCurrentSrc.includes(player.expectedRecordPathContains)) {
    return `production smoke step ${stepName} missing native currentSrc evidence`;
  }
  if (!Number.isFinite(player.nativeReadyState) || player.nativeReadyState < 1
      || !Number.isFinite(player.nativeDuration) || player.nativeDuration <= 0) {
    return `production smoke step ${stepName} native video did not decode metadata`;
  }
  if (player.nativePlayingObserved !== true || player.nativePaused !== false) {
    return `production smoke step ${stepName} native video did not enter playing state`;
  }
  return null;
}

function hasLocalOrMockMediaEvidence(...values) {
  return values.some((value) => hasText(value) && looksLocalOrMockMediaEvidence(value));
}

function looksLocalOrMockMediaEvidence(value) {
  const raw = String(value || '').trim();
  const lowered = raw.toLowerCase();
  if (!raw) {
    return false;
  }
  if (lowered.includes('/mock') || lowered.includes('mock://')) {
    return true;
  }
  let url;
  try {
    url = new URL(raw);
  } catch {
    return lowered.includes('mock');
  }
  const hostname = String(url.hostname || '').toLowerCase();
  return url.protocol === 'file:'
    || url.protocol === 'mock:'
    || hostname === 'localhost'
    || hostname === '127.0.0.1'
    || hostname === '::1'
    || hostname === '[::1]'
    || hostname === '0.0.0.0'
    || hostname.endsWith('.local')
    || hostname.includes('mock');
}

function missingCheckpoints(actual, required) {
  const values = Array.isArray(actual) ? actual.map(String) : [];
  return required.filter((checkpoint) => !values.includes(checkpoint));
}

function defaultRunCommand(step) {
  console.log(`running ${step.name}: ${formatStepCommand(step)}`);
  const invocation = prepareStepInvocation(step);
  const result = spawnSync(invocation.command, invocation.args, {
    cwd: process.cwd(),
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
    timeout: step.timeoutMs,
    env: buildStepEnvironment(step),
  });
  const timedOut = result.error?.code === 'ETIMEDOUT';
  const normalized = timedOut
    ? {
        ...result,
        status: 124,
        timedOut: true,
        timeoutMs: step.timeoutMs,
        stderr: `${result.stderr || ''}${result.stderr ? '\n' : ''}production smoke step ${step.name} timed out after ${step.timeoutMs}ms`,
      }
    : result;
  if (normalized.stdout) {
    process.stdout.write(sanitizeChildOutputForDisplay(normalized.stdout));
  }
  if (normalized.stderr) {
    process.stderr.write(sanitizeChildOutputForDisplay(normalized.stderr));
  }
  return normalized;
}

export function prepareStepInvocation(step, platform = process.platform, commandShell = process.env.ComSpec) {
  const command = String(step?.command || '');
  const args = Array.isArray(step?.args) ? step.args.map(String) : [];
  if (platform !== 'win32' || !command.toLowerCase().endsWith('.cmd')) {
    return { command, args };
  }
  const tokens = [command, ...args];
  if (!tokens.every(token => /^[A-Za-z0-9_./:\\=@+-]+$/.test(token))) {
    throw new Error('production smoke Windows batch invocation contains an unsafe token');
  }
  return {
    command: hasText(commandShell) ? commandShell : 'cmd.exe',
    args: ['/d', '/s', '/c', tokens.join(' ')],
  };
}

export function buildStepEnvironment(step, parentEnv = process.env) {
  const env = { ...parentEnv };
  const sensitiveKeys = new Set(CHILD_SENSITIVE_ENV_KEYS.map(key => key.toLowerCase()));
  for (const key of Object.keys(env)) {
    if (sensitiveKeys.has(key.toLowerCase())) {
      delete env[key];
    }
  }
  return { ...env, ...(step?.env || {}) };
}

function maskSensitiveArg(arg) {
  const value = String(arg);
  if (value.startsWith('--token=')) {
    return '--token=***';
  }
  const match = value.match(/^(--[^=]+=)(.+)$/);
  if (!match || !looksLikeUriValue(match[2]) || !/[?#]/.test(match[2])) {
    return arg;
  }
  return `${match[1]}${stripUrlSecrets(match[2])}`;
}

function looksLikeUriValue(value) {
  return String(value).includes('/') || /^[a-z][a-z0-9+.-]*:/i.test(String(value));
}

function stripUrlSecrets(value) {
  return String(value).replace(/[?#].*$/, '');
}

function hasSensitiveUrlQuery(value) {
  try {
    const url = new URL(String(value));
    for (const key of url.searchParams.keys()) {
      if (/(?:token|signature|credential|secret|api[-_]?key|authorization|auth)/i.test(key)) {
        return true;
      }
    }
    return /(?:token|signature|credential|secret|api[-_]?key|authorization|auth)\s*=/i.test(url.hash);
  } catch {
    return /[?&#][^\s=]*(?:token|signature|credential|secret|api[-_]?key|authorization|auth)[^\s=]*=/i.test(String(value));
  }
}

export function sanitizeChildOutputForDisplay(output) {
  return String(output ?? '')
    .replace(
      /("(?:recordPath|currentUrl|nativeCurrentSrc)"\s*:\s*")([^"]*)(")/g,
      (_match, prefix, value, suffix) => `${prefix}${stripUrlSecrets(value)}${suffix}`,
    )
    .replace(/https?:\/\/[^\s"'<>]+/g, value => stripUrlSecrets(value))
    .replace(/\/[^\s"'<>?#[\]{}]+[?#][^\s"'<>]*/g, value => stripUrlSecrets(value))
    .replace(/[^\s"'<>()[\]{},:?#]+[?#][^\s"'<>()[\]{},:]*/g, value => stripUrlSecrets(value));
}

export function sanitizeOutputValue(value) {
  if (typeof value === 'string') {
    return sanitizeChildOutputForDisplay(value);
  }
  if (Array.isArray(value)) {
    return value.map(sanitizeOutputValue);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [key, sanitizeOutputValue(entry)]),
    );
  }
  return value;
}

function buildEvidenceStep(step, status, startedAt, finishedAt, exitCode, error, result) {
  const entry = {
    name: step.name,
    status,
    command: formatStepCommand(step),
    exitCode,
    startedAt: startedAt.iso,
    finishedAt: finishedAt.iso,
    durationMs: durationMs(startedAt, finishedAt),
  };
  if (hasText(error)) {
    entry.error = error;
  }
  const evidenceContext = { ...(step.evidenceContext || {}) };
  if (Number.isFinite(step.timeoutMs)) {
    evidenceContext.timeout = { timeoutMs: step.timeoutMs };
  }
  const summary = mergeEvidenceSummary(evidenceContext, childSmokeSummary(result));
  if (summary) {
    entry.summary = summary;
  }
  return entry;
}

function mergeEvidenceSummary(context, childSummary) {
  if (!context && !childSummary) {
    return null;
  }
  const summary = { ...(context || {}) };
  if (context?.player || childSummary?.player) {
    summary.player = {
      ...(context?.player || {}),
      ...(childSummary?.player || {}),
    };
  }
  for (const [key, value] of Object.entries(childSummary || {})) {
    if (key !== 'player') {
      summary[key] = value;
    }
  }
  return Object.keys(summary).length ? summary : null;
}

function childSmokeSummary(result) {
  const summary = {};
  if (Number.isFinite(result?.timeoutMs)) {
    summary.timeout = { timeoutMs: result.timeoutMs };
  }
  if (result?.timedOut === true) {
    summary.timeout = { ...(summary.timeout || {}), timedOut: true };
  }
  if (result?.typecheckRetry && typeof result.typecheckRetry === 'object') {
    summary.typecheckRetry = compactObject({
      reason: result.typecheckRetry.reason,
      originalExitCode: result.typecheckRetry.originalExitCode,
      originalCommand: result.typecheckRetry.originalCommand,
      retryCommand: result.typecheckRetry.retryCommand,
    });
  }
  const payload = parseLastJsonObject(result?.stdout);
  if (!payload || typeof payload !== 'object') {
    return Object.keys(summary).length ? summary : null;
  }
  if (Array.isArray(payload.checkpoints)) {
    summary.checkpoints = payload.checkpoints;
  }
  if (payload.storageDriftSummary && typeof payload.storageDriftSummary === 'object') {
    summary.storageDriftSummary = buildStorageDriftSummary(payload.storageDriftSummary);
  }
  if (payload.coverageSummary && typeof payload.coverageSummary === 'object') {
    summary.coverageSummary = buildCoverageSummary(payload.coverageSummary);
  }
  if (payload.exportResult && typeof payload.exportResult === 'object') {
    summary.exportResult = buildExportResultSummary(payload.exportResult);
  }
  if (payload.manifestSignature && typeof payload.manifestSignature === 'object') {
    summary.manifestSignature = buildManifestSignatureSummary(payload.manifestSignature);
  }
  if (payload.manifestStorageLifecycle && typeof payload.manifestStorageLifecycle === 'object') {
    summary.manifestStorageLifecycle = buildManifestStorageLifecycleSummary(payload.manifestStorageLifecycle);
  }
  if (payload.manifestVerification && typeof payload.manifestVerification === 'object') {
    summary.manifestVerification = buildManifestVerificationSummary(payload.manifestVerification);
  }
  if (payload.playback && typeof payload.playback === 'object') {
    summary.playback = buildPlaybackAccessSummary(payload.playback);
  }
  const ruleEvidence = buildRuleEvidenceSummary(payload);
  if (ruleEvidence) {
    summary.ruleEvidence = ruleEvidence;
  }
  if (payload.player && typeof payload.player === 'object') {
    summary.player = buildPlayerSmokeSummary(payload.player);
  }
  const playerSummary = buildPlayerSmokeSummary(payload);
  if (playerSummary) {
    summary.player = playerSummary;
  }
  if (hasText(payload.status)) {
    summary.status = payload.status;
  }
  if (hasText(payload.profile)) {
    summary.profile = payload.profile;
  }
  copyPositiveIdIfPresent(summary, payload, 'reviewItemId');
  copyPositiveIdIfPresent(summary, payload, 'reviewCaseId');
  copyPositiveIdIfPresent(summary, payload, 'eventId');
  copyPositiveIdListIfPresent(summary, payload, 'reviewItemIds');
  copyPositiveIdListIfPresent(summary, payload, 'eventIds');
  if (!Object.hasOwn(payload, 'eventIds') && isPositiveId(summary.eventId)) {
    summary.eventIds = [summary.eventId];
  }
  copyTextIfPresent(summary, payload, 'exportJobNo');
  const auditChain = buildAuditChainSummary(payload, summary);
  if (auditChain) {
    summary.auditChain = auditChain;
  }
  copyIfPresent(summary, payload, 'manifestValid');
  copyIfPresent(summary, payload, 'videoExportRequested');
  return Object.keys(summary).length ? summary : null;
}

function buildRuleEvidenceSummary(payload) {
  const source = firstObject(payload.ruleEvidence, payload.smokeRule, payload.reviewRule);
  if (!source) {
    return null;
  }
  const ruleEvidence = {};
  copyTextIfPresent(ruleEvidence, source, 'ruleCode');
  copyTextIfPresent(ruleEvidence, source, 'cameraId');
  copyTextIfPresent(ruleEvidence, source, 'zoneCode');
  copyTextIfPresent(ruleEvidence, source, 'objectLabel');
  copyNumberIfPresent(ruleEvidence, source, 'inertiaFrames');
  copyNumberIfPresent(ruleEvidence, source, 'loiteringSeconds');
  return Object.keys(ruleEvidence).length ? ruleEvidence : null;
}

function buildExportResultSummary(source) {
  const exportResult = {};
  copyTextIfPresent(exportResult, source, 'exportId');
  copySanitizedUrlIfPresent(exportResult, source, 'downloadUrl');
  copySanitizedUrlIfPresent(exportResult, source, 'manifestUrl');
  return exportResult;
}

function buildCoverageSummary(source) {
  const coverage = {};
  copyTextIfPresent(coverage, source, 'status');
  copyTextIfPresent(coverage, source, 'retainMode');
  copyTextIfPresent(coverage, source, 'coverageSource');
  return coverage;
}

function buildStorageDriftSummary(source) {
  const storageDriftSummary = {};
  copyBooleanIfPresent(storageDriftSummary, source, 'healthy');
  copyNumberIfPresent(storageDriftSummary, source, 'recordCount');
  copyNumberIfPresent(storageDriftSummary, source, 'issueCount');
  if (source.issueReasons && typeof source.issueReasons === 'object' && !Array.isArray(source.issueReasons)) {
    const issueReasons = {};
    for (const [reason, count] of Object.entries(source.issueReasons)) {
      if (Number.isFinite(count)) {
        issueReasons[String(reason)] = count;
      }
    }
    storageDriftSummary.issueReasons = issueReasons;
  }
  if (Array.isArray(source.standardReasonKeys)) {
    storageDriftSummary.standardReasonKeys = source.standardReasonKeys.map(String);
  }
  return storageDriftSummary;
}

function buildManifestVerificationSummary(source) {
  const manifestVerification = {};
  copyBooleanIfPresent(manifestVerification, source, 'valid');
  copyBooleanIfPresent(manifestVerification, source, 'signatureValid');
  copyBooleanIfPresent(manifestVerification, source, 'signatureKeyAvailable');
  copyTextIfPresent(manifestVerification, source, 'keyId');
  copyTextIfPresent(manifestVerification, source, 'signatureVersion');
  if (Array.isArray(source.violations)) {
    manifestVerification.violations = source.violations.map(String);
  }
  return manifestVerification;
}

function buildManifestSignatureSummary(source) {
  const manifestSignature = {};
  copyTextIfPresent(manifestSignature, source, 'algorithm');
  copyTextIfPresent(manifestSignature, source, 'keyId');
  copyTextIfPresent(manifestSignature, source, 'signatureVersion');
  return manifestSignature;
}

function buildManifestStorageLifecycleSummary(source) {
  const manifestStorageLifecycle = {};
  copyTextIfPresent(manifestStorageLifecycle, source, 'storageType');
  copyTextIfPresent(manifestStorageLifecycle, source, 'status');
  copyTextIfPresent(manifestStorageLifecycle, source, 'expiresAt');
  copyTextIfPresent(manifestStorageLifecycle, source, 'exportPackageObjectKey');
  return manifestStorageLifecycle;
}

function buildPlaybackAccessSummary(source) {
  const playback = {};
  copyTextIfPresent(playback, source, 'grantedDecision');
  copyTextIfPresent(playback, source, 'deniedDecision');
  if (Array.isArray(source.deniedReasons)) {
    playback.deniedReasons = source.deniedReasons.map(String);
  }
  return playback;
}

function buildAuditChainSummary(payload, summary) {
  const source = payload.auditChain && typeof payload.auditChain === 'object' ? payload.auditChain : {};
  const action = firstText(source.action, hasCheckpoint(payload.checkpoints, 'evidence_download_audited') ? 'export_downloaded' : '');
  const reviewCaseId = firstAuditScalar(source.reviewCaseId, payload.reviewCaseId, summary.reviewCaseId);
  const reviewItemIds = resolveExactAuditIdList(source, payload, 'reviewItemIds', 'reviewItemId');
  const explicitEmptyEventIds = (Object.hasOwn(source, 'eventIds')
      && Array.isArray(source.eventIds)
      && source.eventIds.length === 0)
    || (!Object.hasOwn(source, 'eventIds')
      && Object.hasOwn(payload, 'eventIds')
      && Array.isArray(payload.eventIds)
      && payload.eventIds.length === 0);
  const eventIds = explicitEmptyEventIds
    ? []
    : resolveExactAuditIdList(source, payload, 'eventIds', 'eventId');
  const exportJobNo = firstText(source.exportJobNo, payload.exportJobNo, summary.exportJobNo);
  if (!hasText(action) && reviewCaseId == null && reviewItemIds.length === 0 && eventIds.length === 0 && !hasText(exportJobNo)) {
    return null;
  }
  return {
    action,
    reviewCaseId,
    reviewItemIds,
    eventIds,
    exportJobNo,
  };
}

function firstAuditScalar(...values) {
  for (const value of values) {
    const normalized = normalizeAuditScalar(value);
    if (normalized !== undefined) {
      return normalized;
    }
  }
  return undefined;
}

function resolveExactAuditIdList(source, payload, listKey, scalarKey) {
  if (Object.hasOwn(source, listKey)) {
    return normalizeAuditIdList(source[listKey]);
  }
  if (Object.hasOwn(payload, listKey)) {
    return normalizeAuditIdList(payload[listKey]);
  }
  return normalizeAuditIdList(payload[scalarKey] == null ? [] : [payload[scalarKey]]);
}

function normalizeAuditIdList(value) {
  if (!Array.isArray(value) || value.length !== 1) {
    return [];
  }
  const normalized = normalizePositiveId(value[0]);
  return normalized === undefined ? [] : [normalized];
}

function normalizeAuditScalar(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (hasText(value)) {
    return value;
  }
  return undefined;
}

function isPositiveId(value) {
  return normalizePositiveId(value) !== undefined;
}

function normalizePositiveId(value) {
  if (typeof value === 'number') {
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  }
  if (typeof value !== 'string' || !/^\d+$/.test(value.trim())) {
    return undefined;
  }
  const canonical = value.trim().replace(/^0+/, '');
  return canonical === '' ? undefined : canonical;
}

function samePositiveId(actual, expected) {
  const normalizedActual = normalizePositiveId(actual);
  const normalizedExpected = normalizePositiveId(expected);
  return normalizedActual !== undefined
    && normalizedExpected !== undefined
    && String(normalizedActual) === String(normalizedExpected);
}

function isExactAuditIdList(values, expected) {
  return Array.isArray(values)
    && values.length === 1
    && samePositiveId(values[0], expected);
}

function firstObject(...values) {
  return values.find((value) => value && typeof value === 'object') || null;
}

function hasCheckpoint(checkpoints, expected) {
  return Array.isArray(checkpoints) && checkpoints.map(String).includes(expected);
}

function firstText(...values) {
  for (const value of values) {
    if (hasText(value)) {
      return value;
    }
  }
  return '';
}

function buildPlayerSmokeSummary(payload) {
  const player = {};
  copyBooleanIfPresent(player, payload, 'clickedRow');
  copyBooleanIfPresent(player, payload, 'clickedAction');
  copyTextIfPresent(player, payload, 'seekTime');
  copySanitizedUrlIfPresent(player, payload, 'recordPath');
  copySanitizedUrlIfPresent(player, payload, 'currentUrl');
  copyNumberIfPresent(player, payload, 'playbackOffsetSeconds');
  copyNumberIfPresent(player, payload, 'nativeCurrentTime');
  copySanitizedUrlIfPresent(player, payload, 'nativeCurrentSrc');
  copyNumberIfPresent(player, payload, 'nativeReadyState');
  copyBooleanIfPresent(player, payload, 'nativePaused');
  copyNumberIfPresent(player, payload, 'nativeDuration');
  copyBooleanIfPresent(player, payload, 'nativePlayingObserved');
  if (Object.prototype.hasOwnProperty.call(payload, 'nativeError')) {
    player.nativeErrorPresent = payload.nativeError !== null && payload.nativeError !== undefined;
    if (Number.isFinite(Number(payload.nativeError?.code))) {
      player.nativeErrorCode = Number(payload.nativeError.code);
    }
  }
  return Object.keys(player).length ? player : null;
}

function copyBooleanIfPresent(target, source, key) {
  if (typeof source[key] === 'boolean') {
    target[key] = source[key];
  }
}

function copyTextIfPresent(target, source, key) {
  if (hasText(source[key])) {
    target[key] = source[key];
  }
}

function copySanitizedUrlIfPresent(target, source, key) {
  if (hasText(source[key])) {
    target[key] = stripUrlSecrets(source[key]);
  }
}

function copyNumberIfPresent(target, source, key) {
  if (Number.isFinite(source[key])) {
    target[key] = source[key];
  }
}

function copyPositiveIdIfPresent(target, source, key) {
  const normalized = normalizePositiveId(source[key]);
  if (normalized !== undefined) {
    target[key] = normalized;
  }
}

function copyPositiveIdListIfPresent(target, source, key) {
  if (!Object.hasOwn(source, key)) {
    return;
  }
  const values = source[key];
  if (!Array.isArray(values) || values.length !== 1) {
    target[key] = [];
    return;
  }
  const normalized = normalizePositiveId(values[0]);
  target[key] = normalized === undefined ? [] : [normalized];
}

function copyIfPresent(target, source, key) {
  if (source[key] !== undefined && source[key] !== null) {
    target[key] = source[key];
  }
}

function parseLastJsonObject(value) {
  if (!hasText(value)) {
    return null;
  }
  const text = String(value);
  for (let index = text.lastIndexOf('{'); index >= 0; index = text.lastIndexOf('{', index - 1)) {
    const candidate = text.slice(index).trim();
    try {
      const parsed = JSON.parse(candidate);
      return parsed && typeof parsed === 'object' ? parsed : null;
    } catch {
      // Keep scanning earlier braces; child smoke logs may contain text before JSON.
    }
  }
  return null;
}

function finishEvidenceReport(report, ok, startedAt, finishedAt) {
  report.ok = ok;
  report.status = ok ? 'passed' : 'failed';
  report.finishedAt = finishedAt.iso;
  report.durationMs = durationMs(startedAt, finishedAt);
}

function writeEvidenceReport(options, report, dependencies) {
  if (!hasText(options.evidenceOutputFile)) {
    return;
  }
  const content = JSON.stringify(sanitizeOutputValue(report), null, 2);
  if (dependencies.writeFile) {
    dependencies.writeFile(options.evidenceOutputFile, content);
    return;
  }
  mkdirSync(dirname(options.evidenceOutputFile), { recursive: true });
  writeFileSync(options.evidenceOutputFile, content, 'utf8');
}

function currentInstant(dependencies) {
  const value = dependencies.now ? dependencies.now() : new Date();
  const date = value instanceof Date ? value : new Date(value);
  return {
    iso: date.toISOString(),
    time: date.getTime(),
  };
}

function durationMs(startedAt, finishedAt) {
  return Math.max(0, finishedAt.time - startedAt.time);
}

function requireText(errors, value, message) {
  if (!hasText(value)) {
    errors.push(message);
  }
}

function requirePositiveNumber(errors, value, message) {
  if (!Number.isFinite(value) || value <= 0) {
    errors.push(message);
  }
}

function requireNonNegativeNumber(errors, value, message) {
  if (!Number.isFinite(value) || value < 0) {
    errors.push(message);
  }
}

function requireList(errors, values, message) {
  if (!Array.isArray(values) || values.length === 0) {
    errors.push(message);
  }
}

function requireReleaseEndpoint(errors, optionName, value) {
  if (!hasText(value) || !looksLocalOrMockEndpoint(value)) {
    return;
  }
  errors.push(`production smoke endpoint ${optionName} must not use a local/mock URL without --allow-local-endpoints`);
}

function positiveNumberArg(name, value) {
  return Number.isFinite(value) && value > 0 ? `${name}=${value}` : '';
}

function cameraListArg(name, values) {
  return Array.isArray(values) && values.length > 0 ? `${name}=${values.join(',')}` : '';
}

function compact(values) {
  return values.filter((value) => value !== undefined && value !== null && String(value) !== '');
}

function compactObject(value) {
  return Object.fromEntries(
    Object.entries(value).filter(([, entry]) => entry !== undefined && entry !== null && String(entry) !== ''),
  );
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== '';
}

function numberOrNaN(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return Number.NaN;
  }
  return Number(value);
}

function parseCsvList(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return [];
  }
  return String(value)
    .split(',')
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function parseBoolean(value, fallback) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return fallback;
  }
  return !['0', 'false', 'no'].includes(String(value).trim().toLowerCase());
}

function defaultPnpmPath() {
  return process.platform === 'win32' ? 'pnpm.cmd' : 'pnpm';
}

function looksLocalOrMockEndpoint(value) {
  const raw = String(value || '').trim();
  if (!raw) {
    return false;
  }
  let url;
  try {
    url = new URL(raw);
  } catch {
    return true;
  }
  const hostname = String(url.hostname || '').toLowerCase();
  return url.protocol === 'file:'
    || url.protocol === 'mock:'
    || hostname === 'localhost'
    || hostname === '127.0.0.1'
    || hostname === '::1'
    || hostname === '[::1]'
    || hostname === '0.0.0.0'
    || hostname.endsWith('.local')
    || hostname.includes('mock')
    || raw.toLowerCase().includes('/mock');
}

function cliTokenAllowedForLocalEndpoints(options) {
  if (options.allowLocalEndpoints !== true) {
    return false;
  }
  return [
    options.deviceBaseUrl,
    options.videoAlertRecordQueryUrl,
    options.videoRecordCoverageQueryUrl,
    options.videoRecordBaseUrl,
    options.videoRecordExportUrl,
    options.playerWorkbenchUrl,
  ].every(isExplicitLocalOrMockEndpoint);
}

function isExplicitLocalOrMockEndpoint(value) {
  if (!hasText(value)) {
    return false;
  }
  let url;
  try {
    url = new URL(value);
  } catch {
    return false;
  }
  const hostname = String(url.hostname || '').toLowerCase();
  return url.protocol === 'file:'
    || url.protocol === 'mock:'
    || hostname === 'localhost'
    || hostname.endsWith('.localhost')
    || /^127(?:\.\d{1,3}){3}$/.test(hostname)
    || hostname === '::1'
    || hostname === '[::1]'
    || hostname === '0.0.0.0'
    || hostname.endsWith('.test')
    || hostname.endsWith('.invalid');
}

function printHelp() {
  console.log(`Usage: node .scripts/alert-review-production-smoke.mjs \\
  --device-base-url=http://DEVICE/admin-api --tenant-id=TENANT_ID \\
  --operator-user-id=9200 --device-alert-time="2026-07-05T10:00:00" \\
  --device-playback-allowed-camera-ids=camera-01 --device-playback-denied-camera-ids=camera-02 \\
  --video-alert-record-query-url=http://VIDEO/video/alert/record/query \\
  --video-record-coverage-query-url=http://VIDEO/video/record/availability \\
  --video-record-base-url=http://VIDEO/video/record \\
  --video-record-export-url=http://VIDEO/video/record/export \\
  --video-device-id=DEVICE_ID --video-alert-time="2026-07-05 10:00:00" \\
  --video-record-drift-retention-hours=24 \\
  --video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs \\
  --player-review-row-text=RV-... \\
  --player-expected-seek-time="2026-07-05T10:00:30" \\
  --player-expected-record-path-contains=DEVICE_ID \\
  --player-expected-offset-seconds=30 \\
  --player-coverage-expected-seek-time="2026-07-05T10:00:00" \\
  --player-coverage-expected-record-path-contains=DEVICE_ID \\
  --player-coverage-expected-offset-seconds=0 \\
  --player-case-timeline-expected-seek-time="2026-07-05T10:00:00" \\
  --player-case-timeline-expected-record-path-contains=DEVICE_ID \\
  --player-case-timeline-expected-offset-seconds=0 \\
  --evidence-output-file=artifacts/production-smoke.json \\
  [--step-timeout-ms=900000] [--allow-local-endpoints]

Runs the release FR-32 production smoke in order:
W4:visible-copy -> W2:typecheck -> LiveDevice -> LiveVideo -> LivePlayer:detail ->
LivePlayer:coverage -> LivePlayer:case-timeline. The first step scans review/player/VIDEO
visible-copy files for replacement characters and known mojibake fragments, then W2 runs the
full frontend typecheck before each smoke step uses real deployed services, real recording
metadata, export verification, download audit, playback-url allow/deny authorization,
recording DB/disk drift patrol, and player seek assertions from the dedicated smoke scripts.
If Corepack/pnpm stops before vue-tsc because the local
pnpm version differs, W2 retries once with --pm-on-fail=ignore and records typecheckRetry evidence.
Each step has a timeout, defaulting to 900000ms, configurable through
--step-timeout-ms or YFEIEYE_PRODUCTION_SMOKE_STEP_TIMEOUT_MS; timeout exits are
reported as exit code 124 and written to the evidence report. The same value is
passed to LiveDevice, LiveVideo, and LivePlayer child smokes as --timeout-ms so
their internal HTTP/browser waits align with the parent step boundary.
Release smoke requires a video manifest verifier script so the fetched
manifest is verified against reachable manifest-referenced evidence files, and
requires an evidence output path so every release run leaves a sanitized JSON
report with token-free child step commands. Localhost/mock/file endpoints are
rejected unless --allow-local-endpoints is supplied for co-located real-service
smoke. Set YFEIEYE_DEVICE_AUTH_TOKEN in the parent environment for release runs;
set signed media/workbench values through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI,
YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI, and YFEIEYE_REVIEW_PLAYER_SMOKE_URL.
Their argv forms are rejected so child commands and process listings remain secret-free.
--token remains available only when --allow-local-endpoints is set and every configured
endpoint is local/mock.`);
}

async function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const result = await runProductionSmoke(options);
  console.log('alert review production smoke passed');
  console.log(JSON.stringify(sanitizeOutputValue(result), null, 2));
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli().catch((error) => {
    console.error(sanitizeChildOutputForDisplay(error instanceof Error ? error.message : String(error)));
    process.exitCode = 1;
  });
}
