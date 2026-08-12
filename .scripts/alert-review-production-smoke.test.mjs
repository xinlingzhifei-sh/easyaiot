import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';

import {
  buildStepEnvironment,
  buildSmokeSteps,
  formatStepCommand,
  parseArgs,
  prepareStepInvocation,
  requiredOptionErrors,
  runProductionSmoke,
  sanitizeChildOutputForDisplay,
  sanitizeOutputValue,
} from './alert-review-production-smoke.mjs';
import {
  evaluateStatus,
  releaseEntriesForTrackedPaths,
} from './verify-alert-review-release-package.mjs';

const STANDARD_STORAGE_DRIFT_REASON_KEYS = [
  'file_missing',
  'retention_expired',
  'disk_full',
  'cache_flush_failed',
];

const parsed = parseArgs([
  '--device-base-url=https://device.release.example/api',
  '--operator-user-id=9001',
  '--device-alert-time=2026-07-05T10:00:00',
  '--device-profile=device-video-web',
  '--device-playback-allowed-camera-ids=camera-01',
  '--device-playback-denied-camera-ids=camera-02',
  '--device-playback-review-item-id=1001',
  '--device-playback-review-case-id=2001',
  '--video-alert-record-query-url=https://video.release.example/video/record/availability',
  '--video-record-coverage-query-url=https://video.release.example/video/record/availability',
  '--video-record-base-url=https://video.release.example/video/record',
  '--video-record-export-url=https://video.release.example/video/record/export',
  '--video-device-id=device-01',
  '--video-camera-id=camera-01',
  '--video-alert-time=2026-07-05 10:00:00',
  '--video-record-drift-retention-hours=24',
  '--video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs',
  '--tenant-id=42',
  '--player-review-row-text=RV-20260705-001',
  '--player-action-testid=alert-review-detail-seek',
  '--player-expected-seek-time=2026-07-05T10:00:30',
  '--player-expected-record-path-contains=device-01',
  '--player-expected-offset-seconds=30',
  '--player-coverage-expected-seek-time=2026-07-05T10:00:00',
  '--player-coverage-expected-record-path-contains=device-01',
  '--player-coverage-expected-offset-seconds=0',
  '--player-case-timeline-expected-seek-time=2026-07-05T10:00:00',
  '--player-case-timeline-expected-record-path-contains=device-01',
  '--player-case-timeline-expected-offset-seconds=0',
  '--evidence-output-file=artifacts/review-production-smoke.json',
  '--player-wait-text=线索复核工作台',
], {
  YFEIEYE_DEVICE_AUTH_TOKEN: 'token-1',
  YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: 'playback-url.mp4?token=device-playback-secret#device-playback-fragment',
  YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: 'playback-url.mp4?token=video-playback-secret#video-playback-fragment',
  YFEIEYE_REVIEW_PLAYER_SMOKE_URL: 'https://web.release.example/yfeieye/alert/review?token=workbench-secret&signature=workbench-signature#fragment',
  YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: 'session=local-storage-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: 'session=cookie-secret',
});

assert.equal(parsed.deviceBaseUrl, 'https://device.release.example/api');
assert.equal(parsed.token, 'token-1');
assert.equal(parsed.tokenSource, 'environment');
assert.equal(parsed.operatorUserId, 9001);
assert.equal(parsed.deviceAlertTime, '2026-07-05T10:00:00');
assert.equal(parsed.deviceId, 'device-01');
assert.equal(parsed.deviceCameraId, 'camera-01');
assert.equal(parsed.deviceZoneCode, 'production-smoke');
assert.deepEqual(parsed.deviceAllowedCameraIds, ['camera-01']);
assert.deepEqual(parsed.devicePlaybackAllowedCameraIds, ['camera-01']);
assert.deepEqual(parsed.devicePlaybackDeniedCameraIds, ['camera-02']);
assert.equal(parsed.devicePlaybackReviewItemId, 1001);
assert.equal(parsed.devicePlaybackReviewCaseId, 2001);
assert.equal(parsed.devicePlaybackMaterialUri, 'playback-url.mp4?token=device-playback-secret#device-playback-fragment');
assert.equal(parsed.videoPlaybackMaterialUri, 'playback-url.mp4?token=video-playback-secret#video-playback-fragment');
assert.equal(parsed.videoDeviceId, 'device-01');
assert.equal(parsed.videoCameraId, 'camera-01');
assert.equal(parsed.videoRecordDriftRetentionHours, 24);
assert.equal(parsed.videoManifestVerifierScript, '.scripts/record-export-manifest-verifier.mjs');
assert.equal(parsed.playerWorkbenchUrl, 'https://web.release.example/yfeieye/alert/review?token=workbench-secret&signature=workbench-signature#fragment');
assert.equal(parsed.playerLocalStorage, 'session=local-storage-secret');
assert.equal(parsed.playerCookies, 'session=cookie-secret');
assert.equal(parsed.tenantId, 42);
assert.equal(parsed.playerExpectedOffsetSeconds, 30);
assert.equal(parsed.playerCoverageExpectedOffsetSeconds, 0);
assert.equal(parsed.playerCaseTimelineExpectedOffsetSeconds, 0);
assert.equal(parsed.evidenceOutputFile, 'artifacts/review-production-smoke.json');
assert.equal(parsed.allowLocalEndpoints, false);
assert.equal(parsed.stepTimeoutMs, 900000);

const timeoutParsed = parseArgs(['--step-timeout-ms=12345'], {});
assert.equal(timeoutParsed.stepTimeoutMs, 12345);

assert.throws(
  () => parseArgs(['--device-playback-material-uri=record.mp4?token=argv-secret'], {}),
  /device playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI/,
);
assert.throws(
  () => parseArgs(['--video-playback-material-uri=record.mp4?token=argv-secret'], {}),
  /VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI/,
);
assert.throws(
  () => parseArgs(['--player-workbench-url=https://example.test/review?token=argv-secret'], {}),
  /signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL/,
);

assert.deepEqual(requiredOptionErrors({
  ...parsed,
  videoManifestVerifierScript: '',
}), [
  'missing --video-manifest-verifier-script or YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT',
]);

const evidenceOutputParsed = parseArgs(['--evidence-output-file=artifacts/review-smoke.json'], {});
assert.equal(evidenceOutputParsed.evidenceOutputFile, 'artifacts/review-smoke.json');

const localEndpointsAllowed = parseArgs([
  '--device-base-url=http://127.0.0.1:48080/admin-api',
  '--token=token-1',
  '--operator-user-id=9001',
  '--device-alert-time=2026-07-05T10:00:00',
  '--device-playback-allowed-camera-ids=camera-01',
  '--device-playback-denied-camera-ids=camera-02',
  '--video-alert-record-query-url=http://127.0.0.1:6000/video/record/availability',
  '--video-record-coverage-query-url=http://127.0.0.1:6000/video/record/availability',
  '--video-record-base-url=http://127.0.0.1:6000/video/record',
  '--video-record-export-url=http://127.0.0.1:6000/video/record/export',
  '--video-device-id=device-01',
  '--video-alert-time=2026-07-05 10:00:00',
  '--video-record-drift-retention-hours=24',
  '--player-workbench-url=http://127.0.0.1:5173/yfeieye/alert/review',
  '--tenant-id=42',
  '--player-review-row-text=RV-20260705-001',
  '--player-expected-seek-time=2026-07-05T10:00:30',
  '--player-expected-record-path-contains=device-01',
  '--player-expected-offset-seconds=30',
  '--player-coverage-expected-seek-time=2026-07-05T10:00:00',
  '--player-coverage-expected-record-path-contains=device-01',
  '--player-coverage-expected-offset-seconds=0',
  '--player-case-timeline-expected-seek-time=2026-07-05T10:00:00',
  '--player-case-timeline-expected-record-path-contains=device-01',
  '--player-case-timeline-expected-offset-seconds=0',
  '--evidence-output-file=artifacts/local-review-production-smoke.json',
  '--allow-local-endpoints',
], {});
assert.equal(localEndpointsAllowed.allowLocalEndpoints, true);
assert.equal(localEndpointsAllowed.tokenSource, 'cli');
assert.deepEqual(requiredOptionErrors(localEndpointsAllowed), []);

for (const unsafeEndpoint of [
  'https://api.public.example/mock/device',
  'https://api.public.example/device?redirect=/mock',
  'https://mock-api.public.example/device',
]) {
  assert.ok(requiredOptionErrors({
    ...localEndpointsAllowed,
    deviceBaseUrl: unsafeEndpoint,
  }).includes(
    'production smoke release token must come from YFEIEYE_DEVICE_AUTH_TOKEN; --token requires --allow-local-endpoints and local/mock endpoints only',
  ), unsafeEndpoint);
}
assert.deepEqual(requiredOptionErrors({
  ...localEndpointsAllowed,
  deviceBaseUrl: 'https://device.mock.test/api',
  videoRecordBaseUrl: 'https://video.mock.invalid/record',
}), []);

const releaseCliToken = parseArgs(['--token=release-cli-secret'], {});
assert.equal(releaseCliToken.tokenSource, 'cli');
assert.ok(requiredOptionErrors(releaseCliToken).includes(
  'production smoke release token must come from YFEIEYE_DEVICE_AUTH_TOKEN; --token requires --allow-local-endpoints and local/mock endpoints only',
));
assert.ok(requiredOptionErrors({
  ...parsed,
  tokenSource: 'cli',
  allowLocalEndpoints: true,
}).includes(
  'production smoke release token must come from YFEIEYE_DEVICE_AUTH_TOKEN; --token requires --allow-local-endpoints and local/mock endpoints only',
));
assert.ok(
  buildSmokeSteps(localEndpointsAllowed, { nodePath: 'node', scriptDir: '.scripts' })[3].args.includes('--allow-local-endpoints'),
);
assert.ok(
  buildSmokeSteps(localEndpointsAllowed, { nodePath: 'node', scriptDir: '.scripts' })[4].args.includes('--allow-local-endpoints'),
);
assert.ok(
  buildSmokeSteps(localEndpointsAllowed, { nodePath: 'node', scriptDir: '.scripts' })[5].args.includes('--allow-local-endpoints'),
);
assert.ok(
  buildSmokeSteps(localEndpointsAllowed, { nodePath: 'node', scriptDir: '.scripts' })[6].args.includes('--allow-local-endpoints'),
);

const fromEnv = parseArgs([], {
  YFEIEYE_DEVICE_BASE_URL: 'https://device.env/admin-api',
  YFEIEYE_DEVICE_AUTH_TOKEN: 'env-token',
  YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID: '9200',
  YFEIEYE_DEVICE_SMOKE_ALERT_TIME: '2026-07-05T11:00:00',
  YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS: 'env-camera-allow',
  YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS: 'env-camera-deny',
  YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: 'env-device-record.mp4?token=env-device-secret',
  YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: 'env-video-record.mp4?token=env-video-secret',
  YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL: 'https://video.env/video/record/availability',
  YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL: 'https://video.env/video/record/availability',
  YFEIEYE_VIDEO_RECORD_BASE_URL: 'https://video.env/video/record',
  YFEIEYE_VIDEO_RECORD_EXPORT_URL: 'https://video.env/video/record/export',
  YFEIEYE_VIDEO_SMOKE_DEVICE_ID: 'env-device',
  YFEIEYE_VIDEO_SMOKE_ALERT_TIME: '2026-07-05 11:00:00',
  YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS: '72',
  YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT: '.scripts/record-export-manifest-verifier.mjs',
  YFEIEYE_REVIEW_PLAYER_SMOKE_URL: 'https://web.env/review',
  YFEIEYE_DEVICE_TENANT_ID: '84',
  YFEIEYE_REVIEW_PLAYER_SMOKE_ROW_TEXT: 'RV-ENV',
  YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_SEEK_TIME: '2026-07-05T11:00:10',
  YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_RECORD_PATH_CONTAINS: 'env-device',
  YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_OFFSET_SECONDS: '10',
  YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_SEEK_TIME: '2026-07-05T11:00:00',
  YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_RECORD_PATH_CONTAINS: 'env-device',
  YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_OFFSET_SECONDS: '0',
  YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_SEEK_TIME: '2026-07-05T11:00:00',
  YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_RECORD_PATH_CONTAINS: 'env-device',
  YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_OFFSET_SECONDS: '0',
  YFEIEYE_PRODUCTION_SMOKE_ALLOW_LOCAL_ENDPOINTS: 'true',
  YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE: 'artifacts/env-smoke.json',
});
assert.equal(fromEnv.deviceBaseUrl, 'https://device.env/admin-api');
assert.deepEqual(fromEnv.devicePlaybackAllowedCameraIds, ['env-camera-allow']);
assert.deepEqual(fromEnv.devicePlaybackDeniedCameraIds, ['env-camera-deny']);
assert.equal(fromEnv.devicePlaybackMaterialUri, 'env-device-record.mp4?token=env-device-secret');
assert.equal(fromEnv.videoPlaybackMaterialUri, 'env-video-record.mp4?token=env-video-secret');
assert.equal(fromEnv.videoDeviceId, 'env-device');
assert.equal(fromEnv.videoRecordDriftRetentionHours, 72);
assert.equal(fromEnv.videoManifestVerifierScript, '.scripts/record-export-manifest-verifier.mjs');
assert.equal(fromEnv.playerExpectedOffsetSeconds, 10);
assert.equal(fromEnv.tenantId, 84);
assert.equal(fromEnv.playerCoverageExpectedOffsetSeconds, 0);
assert.equal(fromEnv.playerCaseTimelineExpectedOffsetSeconds, 0);
assert.equal(fromEnv.evidenceOutputFile, 'artifacts/env-smoke.json');

assert.deepEqual(requiredOptionErrors(parseArgs([], {})), [
  'missing --device-base-url or YFEIEYE_DEVICE_BASE_URL',
  'missing --token or YFEIEYE_DEVICE_AUTH_TOKEN',
  'missing --tenant-id or YFEIEYE_DEVICE_TENANT_ID',
  'missing --operator-user-id or YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID',
  'missing --device-alert-time or YFEIEYE_DEVICE_SMOKE_ALERT_TIME',
  'missing --device-playback-allowed-camera-ids or YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS',
  'missing --device-playback-denied-camera-ids or YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS',
  'missing --video-alert-record-query-url or YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL',
  'missing --video-record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL',
  'missing --video-record-base-url or YFEIEYE_VIDEO_RECORD_BASE_URL',
  'missing --video-record-export-url or YFEIEYE_VIDEO_RECORD_EXPORT_URL',
  'missing --video-device-id or YFEIEYE_VIDEO_SMOKE_DEVICE_ID',
  'missing --video-alert-time or YFEIEYE_VIDEO_SMOKE_ALERT_TIME',
  'missing --video-record-drift-retention-hours or YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS',
  'missing --video-manifest-verifier-script or YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT',
  'missing --player-workbench-url or YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'missing --player-review-row-text or YFEIEYE_REVIEW_PLAYER_SMOKE_ROW_TEXT',
  'missing --player-expected-seek-time or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_SEEK_TIME',
  'missing --player-expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_RECORD_PATH_CONTAINS',
  'missing --player-expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_OFFSET_SECONDS',
  'missing --player-coverage-expected-seek-time or YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_SEEK_TIME',
  'missing --player-coverage-expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_RECORD_PATH_CONTAINS',
  'missing --player-coverage-expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_COVERAGE_EXPECTED_OFFSET_SECONDS',
  'missing --player-case-timeline-expected-seek-time or YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_SEEK_TIME',
  'missing --player-case-timeline-expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_RECORD_PATH_CONTAINS',
  'missing --player-case-timeline-expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_CASE_TIMELINE_EXPECTED_OFFSET_SECONDS',
  'missing --evidence-output-file or YFEIEYE_PRODUCTION_SMOKE_EVIDENCE_FILE',
]);

assert.deepEqual(requiredOptionErrors(parseArgs([
  '--device-base-url=http://localhost:48080/admin-api',
  '--operator-user-id=9001',
  '--device-alert-time=2026-07-05T10:00:00',
  '--device-playback-allowed-camera-ids=camera-01',
  '--device-playback-denied-camera-ids=camera-02',
  '--video-alert-record-query-url=http://127.0.0.1:6000/video/record/availability',
  '--video-record-coverage-query-url=http://video.mock/video/record/availability',
  '--video-record-base-url=file:///tmp/video/record',
  '--video-record-export-url=http://localhost:6000/video/record/export',
  '--video-device-id=device-01',
  '--video-alert-time=2026-07-05 10:00:00',
  '--video-record-drift-retention-hours=24',
  '--video-manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs',
  '--player-workbench-url=http://localhost:5173/mock-workbench',
  '--tenant-id=42',
  '--player-review-row-text=RV-20260705-001',
  '--player-expected-seek-time=2026-07-05T10:00:30',
  '--player-expected-record-path-contains=device-01',
  '--player-expected-offset-seconds=30',
  '--player-coverage-expected-seek-time=2026-07-05T10:00:00',
  '--player-coverage-expected-record-path-contains=device-01',
  '--player-coverage-expected-offset-seconds=0',
  '--player-case-timeline-expected-seek-time=2026-07-05T10:00:00',
  '--player-case-timeline-expected-record-path-contains=device-01',
  '--player-case-timeline-expected-offset-seconds=0',
  '--evidence-output-file=artifacts/local-endpoint-rejection.json',
], { YFEIEYE_DEVICE_AUTH_TOKEN: 'token-1' })), [
  'production smoke endpoint --device-base-url must not use a local/mock URL without --allow-local-endpoints',
  'production smoke endpoint --video-alert-record-query-url must not use a local/mock URL without --allow-local-endpoints',
  'production smoke endpoint --video-record-coverage-query-url must not use a local/mock URL without --allow-local-endpoints',
  'production smoke endpoint --video-record-base-url must not use a local/mock URL without --allow-local-endpoints',
  'production smoke endpoint --video-record-export-url must not use a local/mock URL without --allow-local-endpoints',
  'production smoke endpoint --player-workbench-url must not use a local/mock URL without --allow-local-endpoints',
]);

const steps = buildSmokeSteps(parsed, {
  nodePath: 'node',
  pnpmPath: 'pnpm',
  scriptDir: '.scripts',
});
assert.deepEqual(prepareStepInvocation({ ...steps[1], command: 'pnpm.cmd' }, 'win32', 'C:\\Windows\\System32\\cmd.exe'), {
  command: 'C:\\Windows\\System32\\cmd.exe',
  args: ['/d', '/s', '/c', 'pnpm.cmd --dir WEB run type:check'],
});
assert.deepEqual(prepareStepInvocation(steps[1], 'linux', '/bin/sh'), {
  command: 'pnpm',
  args: ['--dir', 'WEB', 'run', 'type:check'],
});
assert.deepEqual(prepareStepInvocation(steps[0], 'win32', 'C:\\Windows\\System32\\cmd.exe'), {
  command: 'node',
  args: ['.scripts/alert-review-visible-copy-scan.mjs'],
});
assert.deepEqual(steps.map((step) => step.name), [
  'W4:visible-copy',
  'W2:typecheck',
  'LiveDevice',
  'LiveVideo',
  'LivePlayer:detail',
  'LivePlayer:coverage',
  'LivePlayer:case-timeline',
]);
assert.deepEqual(steps[0].args, ['.scripts/alert-review-visible-copy-scan.mjs']);
assert.deepEqual(steps[1].args, ['--dir', 'WEB', 'run', 'type:check']);
assert.deepEqual(steps.map((step) => step.timeoutMs), [900000, 900000, 900000, 900000, 900000, 900000, 900000]);
assert.deepEqual(steps[2].args.slice(0, 5), [
  '.scripts/alert-review-device-integration-smoke.mjs',
  '--device-base-url=https://device.release.example/api',
  '--tenant-id=42',
  '--operator-user-id=9001',
  '--alert-time=2026-07-05T10:00:00',
]);
assert.equal(steps[2].env.YFEIEYE_DEVICE_AUTH_TOKEN, 'token-1');
assert.ok(steps[2].args.includes('--playback-allowed-camera-ids=camera-01'));
assert.ok(steps[2].args.includes('--device-id=device-01'));
assert.ok(steps[2].args.includes('--camera-id=camera-01'));
assert.ok(steps[2].args.includes('--zone-code=production-smoke'));
assert.ok(steps[2].args.includes('--allowed-camera-ids=camera-01'));
assert.ok(steps[2].args.includes('--playback-denied-camera-ids=camera-02'));
assert.ok(steps[2].args.includes('--playback-review-item-id=1001'));
assert.ok(steps[2].args.includes('--playback-review-case-id=2001'));
assert.equal(steps[2].args.some((arg) => String(arg).startsWith('--playback-material-uri=')), false);
assert.equal(steps[2].env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI, parsed.devicePlaybackMaterialUri);
assert.ok(steps[2].args.includes('--timeout-ms=900000'));
assert.ok(steps[3].args.includes('--record-export-url=https://video.release.example/video/record/export'));
assert.equal(steps[3].env.YFEIEYE_VIDEO_SMOKE_TOKEN, 'token-1');
assert.equal(steps[3].env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI, parsed.videoPlaybackMaterialUri);
assert.equal(steps[3].args.some((arg) => String(arg).startsWith('--playback-material-uri=')), false);
assert.ok(steps[3].args.includes('--camera-id=camera-01'));
assert.ok(steps[3].args.includes('--record-drift-retention-hours=24'));
assert.ok(steps[3].args.includes('--manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs'));
assert.ok(steps[3].args.includes('--timeout-ms=900000'));
assert.ok(steps[4].args.includes('--action-testid=alert-review-detail-seek'));
assert.ok(steps[4].args.includes('--expected-offset-seconds=30'));
assert.ok(steps[4].args.includes('--timeout-ms=900000'));
assert.ok(steps[4].args.includes('--assert-native-current-time'));
assert.equal(steps[4].env.YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN, 'token-1');
assert.equal(steps[4].env.YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID, '42');
assert.equal(steps[4].env.YFEIEYE_REVIEW_PLAYER_SMOKE_URL, parsed.playerWorkbenchUrl);
assert.equal(steps[4].env.YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE, 'session=local-storage-secret');
assert.equal(steps[4].env.YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES, 'session=cookie-secret');
assert.equal(steps[4].args.some((arg) => String(arg).startsWith('--workbench-url=')), false);
assert.ok(steps[5].args.includes('--action-testid=alert-review-coverage-seek'));
assert.ok(steps[5].args.includes('--expected-offset-seconds=0'));
assert.ok(steps[5].args.includes('--timeout-ms=900000'));
assert.ok(steps[5].args.includes('--assert-native-current-time'));
assert.equal(steps[5].env.YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN, 'token-1');
assert.ok(steps[6].args.includes('--action-testid=alert-review-case-timeline-seek'));
assert.ok(steps[6].args.includes('--expected-offset-seconds=0'));
assert.ok(steps[6].args.includes('--timeout-ms=900000'));
assert.ok(steps[6].args.includes('--assert-native-current-time'));
assert.equal(steps[6].env.YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID, '42');
assert.equal(
  steps.some((step) => step.args.some((arg) => String(arg).includes('token-1'))),
  false,
);
assert.equal(
  formatStepCommand(steps[2]),
  'node .scripts/alert-review-device-integration-smoke.mjs --device-base-url=https://device.release.example/api --tenant-id=42 --operator-user-id=9001 --alert-time=2026-07-05T10:00:00 --profile=device-video-web --device-id=device-01 --camera-id=camera-01 --zone-code=production-smoke --allowed-camera-ids=camera-01 --playback-review-item-id=1001 --playback-review-case-id=2001 --playback-allowed-camera-ids=camera-01 --playback-denied-camera-ids=camera-02 --timeout-ms=900000',
);
assert.equal(formatStepCommand(steps[2]).includes('token-1'), false);
assert.equal(formatStepCommand(steps[3]).includes('token-1'), false);
assert.equal(
  formatStepCommand({
    command: 'node',
    args: [
      'child.mjs',
      '--playback-material-uri=/video/record/device-01.mp4?token=relative-arg-secret#relative-arg-fragment',
    ],
  }),
  'node child.mjs --playback-material-uri=/video/record/device-01.mp4',
);

const parentEnvironment = {
  KEEP_ME: 'kept',
  YFEIEYE_DEVICE_AUTH_TOKEN: 'parent-device-secret',
  YFEIEYE_VIDEO_SMOKE_TOKEN: 'parent-video-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN: 'parent-player-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_URL: 'https://parent.example.test/review?token=parent-url-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: 'parent-local-storage-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: 'parent-cookie-secret',
  YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: 'parent-device-record.mp4?token=parent-device-uri-secret',
  YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: 'parent-video-record.mp4?token=parent-video-uri-secret',
};
assert.deepEqual(buildStepEnvironment(steps[0], parentEnvironment), {
  KEEP_ME: 'kept',
});
assert.deepEqual(buildStepEnvironment(steps[1], parentEnvironment), {
  KEEP_ME: 'kept',
});
assert.deepEqual(buildStepEnvironment(steps[2], parentEnvironment), {
  KEEP_ME: 'kept',
  YFEIEYE_DEVICE_AUTH_TOKEN: 'token-1',
  YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: parsed.devicePlaybackMaterialUri,
});
assert.deepEqual(buildStepEnvironment(steps[3], parentEnvironment), {
  KEEP_ME: 'kept',
  YFEIEYE_VIDEO_SMOKE_TOKEN: 'token-1',
  YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: parsed.videoPlaybackMaterialUri,
});
assert.deepEqual(buildStepEnvironment(steps[4], parentEnvironment), {
  KEEP_ME: 'kept',
  YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN: 'token-1',
  YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID: '42',
  YFEIEYE_REVIEW_PLAYER_SMOKE_URL: parsed.playerWorkbenchUrl,
  YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: 'session=local-storage-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: 'session=cookie-secret',
});

const signedPlayerStdout = [
  'alert review player live smoke passed',
  JSON.stringify({
    recordPath: '/video/record/device-01.mp4?token=record-secret#record-fragment',
    currentUrl: 'https://media.example.test/video/device-01.mp4?token=current-secret&signature=abc#current-fragment',
    nativeCurrentSrc: 'https://media.example.test/video/device-01.mp4?token=native-secret#native-fragment',
    nativeError: {
      message: 'record.mp4?token=native-error-secret#native-error-fragment',
    },
    exportResult: {
      message: 'exported record.mp4?token=export-message-secret#export-message-fragment',
    },
    ruleEvidence: [{
      message: 'rule record.mp4?token=rule-secret#rule-fragment',
    }],
  }, null, 2),
  'loaded https://media.example.test/video/device-01.mp4?token=log-secret#log-fragment',
  'relative /video/record/device-02.mp4?token=relative-output-secret#relative-output-fragment',
  'bare record.mp4?token=bare-output-secret#bare-output-fragment',
].join('\n');
const sanitizedPlayerStdout = sanitizeChildOutputForDisplay(signedPlayerStdout);
assert.equal(sanitizedPlayerStdout.includes('record-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('current-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('native-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('log-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('relative-output-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('bare-output-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('native-error-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('export-message-secret'), false);
assert.equal(sanitizedPlayerStdout.includes('rule-secret'), false);
assert.match(sanitizedPlayerStdout, /"recordPath": "\/video\/record\/device-01\.mp4"/);
assert.match(sanitizedPlayerStdout, /"currentUrl": "https:\/\/media\.example\.test\/video\/device-01\.mp4"/);
assert.match(sanitizedPlayerStdout, /loaded https:\/\/media\.example\.test\/video\/device-01\.mp4$/m);
assert.match(sanitizedPlayerStdout, /relative \/video\/record\/device-02\.mp4$/m);
assert.match(sanitizedPlayerStdout, /bare record\.mp4$/);
assert.deepEqual(sanitizeOutputValue({
  exportResult: {
    message: 'exported record.mp4?token=recursive-export-secret#fragment',
  },
  nativeError: {
    message: 'record.mp4?token=recursive-native-secret#fragment',
  },
  ruleEvidence: [{
    message: 'rule record.mp4?token=recursive-rule-secret#fragment',
  }],
}), {
  exportResult: { message: 'exported record.mp4' },
  nativeError: { message: 'record.mp4' },
  ruleEvidence: [{ message: 'rule record.mp4' }],
});

const help = spawnSync(process.execPath, ['.scripts/alert-review-production-smoke.mjs', '--help'], {
  encoding: 'utf8',
});
assert.equal(help.status, 0);
assert.match(help.stdout, /W4:visible-copy -> W2:typecheck -> LiveDevice -> LiveVideo -> LivePlayer:detail/);
assert.match(help.stdout, /visible-copy files for replacement characters/);
assert.match(help.stdout, /full frontend typecheck/);
assert.match(help.stdout, /--pm-on-fail=ignore/);
assert.match(help.stdout, /YFEIEYE_DEVICE_AUTH_TOKEN/);
assert.match(help.stdout, /YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI/);
assert.match(help.stdout, /YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI/);
assert.match(help.stdout, /YFEIEYE_REVIEW_PLAYER_SMOKE_URL/);
assert.doesNotMatch(help.stdout, /--token=JWT_TOKEN/);
assert.doesNotMatch(help.stdout, /--device-playback-material-uri=/);
assert.doesNotMatch(help.stdout, /--video-playback-material-uri=/);
const productionPlaybackMaterialArgvFailure = spawnSync(process.execPath, [
  '.scripts/alert-review-production-smoke.mjs',
  '--device-playback-material-uri=record.mp4?token=production-playback-argv-secret#fragment',
], { encoding: 'utf8' });
assert.equal(productionPlaybackMaterialArgvFailure.status, 1);
assert.equal(productionPlaybackMaterialArgvFailure.stderr.includes('production-playback-argv-secret'), false);
assert.match(productionPlaybackMaterialArgvFailure.stderr, /YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI/);

const signedCliFailure = spawnSync(process.execPath, [
  '.scripts/alert-review-production-smoke.mjs',
  '--bogus=record.mp4?token=production-failure-secret#production-failure-fragment',
], { encoding: 'utf8' });
assert.equal(signedCliFailure.status, 1);
assert.equal(signedCliFailure.stderr.includes('production-failure-secret'), false);
assert.match(signedCliFailure.stderr, /--bogus=record\.mp4/);

const calls = [];
const smoke = await runProductionSmoke(parsed, {
  nodePath: 'node',
  scriptDir: '.scripts',
  writeFile: () => {},
  runCommand: async (step) => {
    calls.push(step.name);
    return { status: 0, stdout: summaryStdoutForStep(step.name) };
  },
});
assert.equal(smoke.ok, true);
assert.deepEqual(calls, ['W4:visible-copy', 'W2:typecheck', 'LiveDevice', 'LiveVideo', 'LivePlayer:detail', 'LivePlayer:coverage', 'LivePlayer:case-timeline']);
assert.deepEqual(smoke.steps.map((step) => step.status), ['passed', 'passed', 'passed', 'passed', 'passed', 'passed', 'passed']);

const pnpmGuardRetryCalls = [];
const pnpmGuardRetryWrites = [];
const pnpmGuardRetrySmoke = await runProductionSmoke({
  ...parsed,
  evidenceOutputFile: 'artifacts/pnpm-guard-retry.json',
}, {
  nodePath: 'node',
  scriptDir: '.scripts',
  writeFile: (file, content) => {
    pnpmGuardRetryWrites.push({ file, content });
  },
  runCommand: async (step) => {
    pnpmGuardRetryCalls.push({ name: step.name, args: step.args });
    if (step.name === 'W2:typecheck' && !step.args.includes('--pm-on-fail=ignore')) {
      return {
        status: 1,
        stderr: [
          '[ERROR] This project is configured to use 11.3.0 of pnpm. Your current pnpm is v11.5.2',
          'Corepack invoked pnpm with this version, and pnpm does not switch versions when running under corepack.',
          'If you want to bypass this version check, you can set the "pmOnFail" configuration to "warn" or "ignore"',
        ].join('\n'),
      };
    }
    return { status: 0, stdout: summaryStdoutForStep(step.name) };
  },
});
assert.equal(pnpmGuardRetrySmoke.ok, true);
assert.deepEqual(pnpmGuardRetryCalls.slice(0, 2), [
  { name: 'W4:visible-copy', args: ['.scripts/alert-review-visible-copy-scan.mjs'] },
  { name: 'W2:typecheck', args: ['--dir', 'WEB', 'run', 'type:check'] },
]);
assert.deepEqual(pnpmGuardRetryCalls.slice(1, 3), [
  { name: 'W2:typecheck', args: ['--dir', 'WEB', 'run', 'type:check'] },
  { name: 'W2:typecheck', args: ['--dir', 'WEB', '--pm-on-fail=ignore', 'run', 'type:check'] },
]);
assert.deepEqual(pnpmGuardRetryCalls.slice(3).map((call) => call.name), ['LiveDevice', 'LiveVideo', 'LivePlayer:detail', 'LivePlayer:coverage', 'LivePlayer:case-timeline']);
assert.equal(pnpmGuardRetryWrites.length, 1);
const pnpmGuardRetryReport = JSON.parse(pnpmGuardRetryWrites[0].content);
assert.equal(pnpmGuardRetryReport.steps[1].name, 'W2:typecheck');
assert.equal(pnpmGuardRetryReport.steps[1].summary.typecheckRetry.reason, 'pnpm_version_guard');
assert.match(pnpmGuardRetryReport.steps[1].summary.typecheckRetry.originalCommand, /^pnpm(\.cmd)? --dir WEB run type:check$/);
assert.match(pnpmGuardRetryReport.steps[1].summary.typecheckRetry.retryCommand, /^pnpm(\.cmd)? --dir WEB --pm-on-fail=ignore run type:check$/);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async () => ({ status: 0 }),
  }),
  /production smoke step LiveDevice did not emit required evidence summary/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => ({
      status: 0,
      stdout: summaryStdoutForStep(step.name, {
        playerRecordPath: 'mock://record/device-01/20260705-100000.mp4',
      }),
    }),
  }),
  /production smoke step LivePlayer:detail used local\/mock player media evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => ({
      status: 0,
      stdout: step.name === 'LivePlayer:detail'
        ? JSON.stringify({
            clickedRow: true,
            clickedAction: true,
            seekTime: '2026-07-05T10:00:30',
            recordPath: 'https://media.example.test/records/device-01/20260705-100000.mp4',
            playbackOffsetSeconds: 30,
          })
        : summaryStdoutForStep(step.name),
    }),
  }),
  /production smoke step LivePlayer:detail missing native currentTime evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => {
      const stdout = summaryStdoutForStep(step.name);
      if (step.name !== 'LivePlayer:detail') return { status: 0, stdout };
      const player = JSON.parse(stdout);
      player.nativePlayingObserved = false;
      player.nativePaused = true;
      return { status: 0, stdout: JSON.stringify(player) };
    },
  }),
  /production smoke step LivePlayer:detail native video did not enter playing state/,
);

const evidenceWrites = [];
const smokeWithEvidence = await runProductionSmoke({
  ...parsed,
  evidenceOutputFile: 'artifacts/review-smoke.json',
  stepTimeoutMs: 12345,
}, {
  nodePath: 'node',
  scriptDir: '.scripts',
  now: sequencedNow([
    '2026-07-07T00:00:00.000Z',
    '2026-07-07T00:00:00.025Z',
    '2026-07-07T00:00:00.040Z',
    '2026-07-07T00:00:00.050Z',
    '2026-07-07T00:00:00.100Z',
    '2026-07-07T00:00:00.200Z',
    '2026-07-07T00:00:00.400Z',
    '2026-07-07T00:00:00.500Z',
    '2026-07-07T00:00:01.000Z',
    '2026-07-07T00:00:01.100Z',
    '2026-07-07T00:00:01.800Z',
    '2026-07-07T00:00:01.900Z',
    '2026-07-07T00:00:02.200Z',
    '2026-07-07T00:00:02.300Z',
    '2026-07-07T00:00:02.800Z',
    '2026-07-07T00:00:03.000Z',
  ]),
  writeFile: (file, content) => {
    evidenceWrites.push({ file, content });
  },
  runCommand: async (step) => ({
    status: 0,
    stdout: step.name === 'LiveDevice'
      ? `alert review DEVICE integration smoke passed
{
  "status": "passed",
  "profile": "device-video-web",
  "reviewItemId": 1001,
  "reviewCaseId": 2001,
  "eventId": 7500,
  "eventIds": [7500],
  "exportJobNo": "EXP-20260707-001",
  "auditChain": {
    "action": "export_downloaded",
    "reviewCaseId": {
      "value": 2001,
      "debugToken": "audit-chain-review-case-secret"
    },
    "reviewItemIds": [1001],
    "reviewItemIdsDebugToken": "audit-chain-review-item-secret",
    "eventIds": [7500],
    "eventIdsDebugToken": "audit-chain-event-secret",
    "exportJobNo": {
      "value": "EXP-20260707-001",
      "debugToken": "audit-chain-export-job-secret"
    }
  },
  "manifestValid": true,
  "videoExportRequested": true,
  "ruleEvidence": {
    "ruleCode": "restricted_area",
    "cameraId": {
      "value": "camera-smoke",
      "debugToken": "rule-evidence-debug-secret"
    },
    "zoneCode": "zone-smoke",
    "objectLabel": "person",
    "inertiaFrames": 3,
    "loiteringSeconds": 20
  },
  "playback": {
    "grantedDecision": "granted",
    "deniedDecision": "denied",
    "deniedReasons": ["camera_not_allowed"],
    "grantedPlaybackUrl": "https://media.example.test/playback/camera-01.mp4?token=playback-url-secret&signature=playback-sig#stream",
    "debugToken": "playback-debug-secret"
  },
  "checkpoints": [
    "ingest_review_item",
    "review_event_bound_without_task_dispatch",
    "review_rule_saved",
    "record_coverage_synced",
    "review_case_created",
    "evidence_export_ready",
    "manifest_verified",
    "evidence_download_bytes_verified",
    "evidence_download_audited",
    "evidence_audit_chain_verified",
    "playback_url_granted",
    "playback_url_denied"
  ]
}
`
      : step.name === 'LiveVideo'
        ? `alert review VIDEO live smoke passed
{
  "checkpoints": [
    "alert_record_query_ok",
    "record_coverage_query_ok",
    "record_base_space_resolved",
    "record_storage_drift_patrol_ok",
    "record_export_posted",
    "record_export_download_ready",
    "record_export_download_probed",
    "record_export_manifest_verified"
  ],
  "coverageSummary": {
    "status": "available",
    "retainMode": "motion",
    "coverageSource": "detection",
    "debugToken": "coverage-classification-secret"
  },
  "storageDriftSummary": {
    "healthy": true,
    "recordCount": 3,
    "issueCount": 0,
    "issueReasons": {},
    "standardReasonKeys": ["file_missing", "retention_expired", "disk_full", "cache_flush_failed"],
    "repairUrl": "https://storage.example.test/drift/repair?token=storage-drift-repair-secret#repair",
    "debugToken": "storage-drift-debug-secret",
    "issues": [
      {
        "reason": "file_missing",
        "filePath": "C:\\\\recordings\\\\private-camera\\\\missing.mp4",
        "storageUrl": "https://storage.example.test/records/private-camera/missing.mp4?token=storage-drift-issue-secret"
      }
    ]
  },
  "exportResult": {
    "exportId": "review-export-1",
    "downloadUrl": "https://media.example.test/downloads/review-export-1.mp4?token=export-download-secret&signature=export-download-sig#download",
    "manifestUrl": "https://media.example.test/manifests/review-export-1.json?token=export-manifest-secret&signature=export-manifest-sig#manifest",
    "localOutputPath": "C:\\\\exports\\\\private-case\\\\review-export-1.mp4",
    "temporaryStorageUrl": "https://storage.example.test/tmp/review-export-1.mp4?token=export-temp-storage-secret#tmp",
    "debugToken": "export-result-debug-secret"
  },
  "manifestSignature": {
    "algorithm": "hmac-sha256",
    "keyId": "2026-q2",
    "signatureVersion": "v2",
    "signatureValue": "hmac-sha256:manifest-signature-secret",
    "signerUrl": "https://signer.example.test/key/2026-q2?token=manifest-signature-url-secret#sign",
    "debugToken": "manifest-signature-debug-secret"
  },
  "manifestStorageLifecycle": {
    "storageType": "object_storage",
    "status": "persisted",
    "expiresAt": "2026-07-20T00:00:00Z",
    "exportPackageObjectKey": "review-export-1/content.bin",
    "storageUrl": "https://storage.example.test/review-export-1/content.bin?token=storage-lifecycle-secret&signature=storage-sig#object",
    "debugToken": "storage-lifecycle-debug-secret"
  },
  "manifestVerification": {
    "valid": true,
    "signatureValid": true,
    "signatureKeyAvailable": true,
    "keyId": "2026-q2",
    "signatureVersion": "v2",
    "violations": [],
    "manifestUrl": "https://media.example.test/manifests/review-export-1.json?token=manifest-verifier-secret&signature=verifier-sig#manifest",
    "debugToken": "manifest-verifier-debug-secret"
  }
}
`
        : step.name === 'LivePlayer:coverage'
          ? `alert review player live smoke passed
{
  "clickedRow": true,
  "clickedAction": true,
  "seekTime": "2026-07-05T10:00:00",
  "recordPath": "https://media.example.test/records/device-01/20260705-100000.mp4",
  "currentUrl": "https://media.example.test/records/device-01/20260705-100000.mp4?token=coverage-secret",
  "playbackOffsetSeconds": 0,
  "nativeCurrentTime": 0.15,
  "nativeCurrentSrc": "https://media.example.test/records/device-01/20260705-100000.mp4?token=coverage-native-secret",
  "nativeReadyState": 4,
  "nativePaused": false,
  "nativeDuration": 120,
  "nativeError": null,
  "nativePlayingObserved": true
}
`
          : step.name === 'LivePlayer:case-timeline'
            ? `alert review player live smoke passed
{
  "clickedRow": true,
  "clickedAction": true,
  "seekTime": "2026-07-05T10:00:00",
  "recordPath": "https://media.example.test/records/device-01/20260705-100000.mp4",
  "currentUrl": "https://media.example.test/records/device-01/20260705-100000.mp4?token=case-secret",
  "playbackOffsetSeconds": 0,
  "nativeCurrentTime": 0,
  "nativeCurrentSrc": "https://media.example.test/records/device-01/20260705-100000.mp4?token=case-native-secret",
  "nativeReadyState": 4,
  "nativePaused": false,
  "nativeDuration": 120,
  "nativeError": null,
  "nativePlayingObserved": true
}
`
            : `alert review player live smoke passed
{
  "player": {
    "clickedRow": true,
    "clickedAction": true,
    "seekTime": "2026-07-05T10:00:30",
    "recordPath": "https://media.example.test/records/device-01/20260705-100000.mp4?token=record-path-secret&signature=record-path-sig#media",
    "currentUrl": "https://media.example.test/records/device-01/20260705-100000.mp4?token=wrapped-media-secret&signature=abc#playback",
    "playbackOffsetSeconds": 30,
    "nativeCurrentTime": 30.25,
    "nativeCurrentSrc": "https://media.example.test/records/device-01/20260705-100000.mp4?token=detail-native-secret",
    "nativeReadyState": 4,
    "nativePaused": false,
    "nativeDuration": 120,
    "nativeError": null,
    "nativePlayingObserved": true,
    "debugToken": "wrapped-debug-secret"
  }
}
`,
  }),
});
assert.equal(smokeWithEvidence.ok, true);
assert.equal(evidenceWrites.length, 1);
assert.equal(evidenceWrites[0].file, 'artifacts/review-smoke.json');
const evidenceReport = JSON.parse(evidenceWrites[0].content);
assert.equal(evidenceReport.ok, true);
assert.equal(evidenceReport.status, 'passed');
assert.equal(evidenceReport.startedAt, '2026-07-07T00:00:00.000Z');
assert.equal(evidenceReport.finishedAt, '2026-07-07T00:00:03.000Z');
assert.equal(evidenceReport.durationMs, 3000);
assert.equal(evidenceReport.allowLocalEndpoints, false);
assert.deepEqual(evidenceReport.steps.map((step) => step.status), ['passed', 'passed', 'passed', 'passed', 'passed', 'passed', 'passed']);
assert.deepEqual(evidenceReport.steps.map((step) => step.summary?.timeout?.timeoutMs), [
  12345,
  12345,
  12345,
  12345,
  12345,
  12345,
  12345,
]);
assert.equal(evidenceReport.steps[0].name, 'W4:visible-copy');
assert.match(evidenceReport.steps[0].command, /^node \.scripts\/alert-review-visible-copy-scan\.mjs$/);
assert.equal(evidenceReport.steps[1].name, 'W2:typecheck');
assert.match(evidenceReport.steps[1].command, /^pnpm(\.cmd)? --dir WEB run type:check$/);
assert.equal(evidenceReport.steps[2].command.includes('--token='), false);
assert.equal(evidenceReport.steps[3].command.includes('--token='), false);
assert.equal(evidenceReport.steps[4].command.includes('--workbench-url='), false);
assert.equal(evidenceReport.steps[4].command.includes('command-secret'), false);
assert.deepEqual(evidenceReport.steps[2].summary, {
  timeout: { timeoutMs: 12345 },
  checkpoints: [
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
  ],
  playback: {
    grantedDecision: 'granted',
    deniedDecision: 'denied',
    deniedReasons: ['camera_not_allowed'],
  },
  ruleEvidence: {
    ruleCode: 'restricted_area',
    zoneCode: 'zone-smoke',
    objectLabel: 'person',
    inertiaFrames: 3,
    loiteringSeconds: 20,
  },
  status: 'passed',
  profile: 'device-video-web',
  reviewItemId: 1001,
  reviewCaseId: 2001,
  eventId: 7500,
  eventIds: [7500],
  exportJobNo: 'EXP-20260707-001',
  auditChain: {
    action: 'export_downloaded',
    reviewCaseId: 2001,
    reviewItemIds: [1001],
    eventIds: [7500],
    exportJobNo: 'EXP-20260707-001',
  },
  manifestValid: true,
  videoExportRequested: true,
});
assert.equal(JSON.stringify(evidenceReport).includes('rule-evidence-debug-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('audit-chain-review-case-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('audit-chain-review-item-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('audit-chain-event-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('audit-chain-export-job-secret'), false);
assert.deepEqual(evidenceReport.steps[3].summary.storageDriftSummary, {
  healthy: true,
  recordCount: 3,
  issueCount: 0,
  issueReasons: {},
  standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
});
assert.equal(JSON.stringify(evidenceReport).includes('storage-drift-repair-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('storage-drift-debug-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('storage-drift-issue-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('private-camera'), false);
assert.deepEqual(evidenceReport.steps[3].summary.checkpoints, [
  'alert_record_query_ok',
  'record_coverage_query_ok',
  'record_base_space_resolved',
  'record_storage_drift_patrol_ok',
  'record_export_posted',
  'record_export_download_ready',
  'record_export_download_probed',
  'record_export_manifest_verified',
]);
assert.deepEqual(evidenceReport.steps[3].summary.coverageSummary, {
  status: 'available',
  retainMode: 'motion',
  coverageSource: 'detection',
});
assert.equal(JSON.stringify(evidenceReport).includes('coverage-classification-secret'), false);
assert.deepEqual(evidenceReport.steps[3].summary.exportResult, {
  exportId: 'review-export-1',
  downloadUrl: 'https://media.example.test/downloads/review-export-1.mp4',
  manifestUrl: 'https://media.example.test/manifests/review-export-1.json',
});
assert.equal(JSON.stringify(evidenceReport).includes('export-temp-storage-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('export-result-debug-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('private-case'), false);
assert.deepEqual(evidenceReport.steps[3].summary.manifestSignature, {
  algorithm: 'hmac-sha256',
  keyId: '2026-q2',
  signatureVersion: 'v2',
});
assert.equal(JSON.stringify(evidenceReport).includes('manifest-signature-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('manifest-signature-url-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('manifest-signature-debug-secret'), false);
assert.deepEqual(evidenceReport.steps[3].summary.manifestStorageLifecycle, {
  storageType: 'object_storage',
  status: 'persisted',
  expiresAt: '2026-07-20T00:00:00Z',
  exportPackageObjectKey: 'review-export-1/content.bin',
});
assert.deepEqual(evidenceReport.steps[3].summary.manifestVerification, {
  valid: true,
  signatureValid: true,
  signatureKeyAvailable: true,
  keyId: '2026-q2',
  signatureVersion: 'v2',
  violations: [],
});
assert.deepEqual(evidenceReport.steps[4].summary.player, {
  entry: 'detail',
  actionTestId: 'alert-review-detail-seek',
  reviewRowText: 'RV-20260705-001',
  reviewItemId: 1001,
  reviewCaseId: 2001,
  expectedSeekTime: '2026-07-05T10:00:30',
  expectedRecordPathContains: 'device-01',
  expectedOffsetSeconds: 30,
  clickedRow: true,
  clickedAction: true,
  seekTime: '2026-07-05T10:00:30',
  recordPath: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  currentUrl: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  playbackOffsetSeconds: 30,
  nativeCurrentTime: 30.25,
  nativeCurrentSrc: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  nativeReadyState: 4,
  nativePaused: false,
  nativeDuration: 120,
  nativePlayingObserved: true,
  nativeErrorPresent: false,
});
assert.deepEqual(evidenceReport.steps[5].summary.player, {
  entry: 'coverage',
  actionTestId: 'alert-review-coverage-seek',
  reviewRowText: 'RV-20260705-001',
  reviewItemId: 1001,
  reviewCaseId: 2001,
  expectedSeekTime: '2026-07-05T10:00:00',
  expectedRecordPathContains: 'device-01',
  expectedOffsetSeconds: 0,
  clickedRow: true,
  clickedAction: true,
  seekTime: '2026-07-05T10:00:00',
  recordPath: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  currentUrl: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  playbackOffsetSeconds: 0,
  nativeCurrentTime: 0.15,
  nativeCurrentSrc: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  nativeReadyState: 4,
  nativePaused: false,
  nativeDuration: 120,
  nativePlayingObserved: true,
  nativeErrorPresent: false,
});
assert.deepEqual(evidenceReport.steps[6].summary.player, {
  entry: 'case-timeline',
  actionTestId: 'alert-review-case-timeline-seek',
  reviewRowText: 'RV-20260705-001',
  reviewItemId: 1001,
  reviewCaseId: 2001,
  expectedSeekTime: '2026-07-05T10:00:00',
  expectedRecordPathContains: 'device-01',
  expectedOffsetSeconds: 0,
  clickedRow: true,
  clickedAction: true,
  seekTime: '2026-07-05T10:00:00',
  recordPath: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  currentUrl: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  playbackOffsetSeconds: 0,
  nativeCurrentTime: 0,
  nativeCurrentSrc: 'https://media.example.test/records/device-01/20260705-100000.mp4',
  nativeReadyState: 4,
  nativePaused: false,
  nativeDuration: 120,
  nativePlayingObserved: true,
  nativeErrorPresent: false,
});
assert.equal(evidenceReport.steps[3].stdout, undefined);
assert.equal(evidenceReport.steps[4].stdout, undefined);
assert.equal(evidenceReport.steps[5].stdout, undefined);
assert.equal(evidenceReport.steps[6].stdout, undefined);
assert.equal(JSON.stringify(evidenceReport).includes('token-1'), false);
assert.equal(JSON.stringify(evidenceReport).includes('command-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('media-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('wrapped-media-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('record-path-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('wrapped-debug-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('coverage-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('case-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('export-download-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('export-manifest-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('playback-url-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('playback-debug-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('manifest-verifier-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('manifest-verifier-debug-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('storage-lifecycle-secret'), false);
assert.equal(JSON.stringify(evidenceReport).includes('storage-lifecycle-debug-secret'), false);

const unsafeIdEvidenceWrites = [];
await assert.rejects(
  () => runProductionSmoke({
    ...parsed,
    evidenceOutputFile: 'artifacts/review-smoke-unsafe-ids.json',
  }, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: (file, content) => unsafeIdEvidenceWrites.push({ file, content }),
    runCommand: async (step) => {
      if (step.name !== 'LiveDevice') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      const summary = JSON.parse(summaryStdoutForStep(step.name));
      summary.reviewItemId = { value: 1001, debugToken: 'review-item-id-debug-secret' };
      summary.reviewCaseId = { value: 2001, debugToken: 'review-case-id-debug-secret' };
      summary.eventId = { value: 7500, debugToken: 'event-id-debug-secret' };
      summary.reviewItemIds = [{ value: 1001, debugToken: 'review-item-ids-debug-secret' }];
      summary.eventIds = [{ value: 7500, debugToken: 'event-ids-debug-secret' }];
      summary.exportJobNo = { value: 'EXP-20260707-001', debugToken: 'export-job-debug-secret' };
      return { status: 0, stdout: JSON.stringify(summary) };
    },
  }),
  /production smoke step LiveDevice missing reviewItemId evidence/,
);
assert.equal(unsafeIdEvidenceWrites.length, 1);
const unsafeIdEvidenceReport = unsafeIdEvidenceWrites[0].content;
for (const leakedSecret of [
  'review-item-id-debug-secret',
  'review-case-id-debug-secret',
  'event-id-debug-secret',
  'review-item-ids-debug-secret',
  'event-ids-debug-secret',
  'export-job-debug-secret',
]) {
  assert.equal(unsafeIdEvidenceReport.includes(leakedSecret), false, leakedSecret);
}

for (const mixedIdScenario of [
  {
    name: 'top-event-ids',
    secret: 'mixed-top-event-ids-debug-secret',
    expectedError: /production smoke step LiveDevice missing eventIds evidence/,
    mutate(summary) {
      summary.eventIds = [summary.eventId, { debugToken: this.secret }];
      summary.auditChain = matchingAuditChain(summary, { eventIds: [summary.eventId] });
    },
  },
  {
    name: 'audit-event-ids',
    secret: 'mixed-audit-event-ids-debug-secret',
    expectedError: /production smoke step LiveDevice missing auditChain eventIds evidence/,
    mutate(summary) {
      summary.auditChain = matchingAuditChain(summary, {
        eventIds: [summary.eventId, { debugToken: this.secret }],
      });
    },
  },
  {
    name: 'audit-review-item-ids',
    secret: 'mixed-audit-review-item-ids-debug-secret',
    expectedError: /production smoke step LiveDevice missing auditChain reviewItemIds evidence/,
    mutate(summary) {
      summary.auditChain = matchingAuditChain(summary, {
        reviewItemIds: [summary.reviewItemId, { debugToken: this.secret }],
      });
    },
  },
]) {
  const evidenceWrites = [];
  await assert.rejects(
    () => runProductionSmokeWithLiveDeviceMutation(
      (summary) => mixedIdScenario.mutate(summary),
      {
        evidenceOutputFile: `artifacts/review-smoke-${mixedIdScenario.name}.json`,
        writeFile: (file, content) => evidenceWrites.push({ file, content }),
      },
    ),
    mixedIdScenario.expectedError,
  );
  assert.equal(evidenceWrites.length, 1);
  assert.equal(evidenceWrites[0].content.includes(mixedIdScenario.secret), false, mixedIdScenario.name);
}

const failedEvidenceWrites = [];
await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => ({ status: step.name === 'LiveVideo' ? 1 : 0, stdout: summaryStdoutForStep(step.name) }),
  }),
  /LiveVideo failed with exit code 1/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => ({
      status: 0,
      stdout: step.name === 'LiveDevice'
        ? JSON.stringify({
            status: 'passed',
            profile: 'device-video-web',
            reviewItemId: 1001,
            reviewCaseId: 2001,
            eventId: 7500,
            eventIds: [7500],
            manifestValid: true,
            videoExportRequested: true,
            checkpoints: [
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
            ],
          })
        : summaryStdoutForStep(step.name),
    }),
  }),
  /production smoke step LiveDevice missing auditChain exportJobNo evidence/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.auditChain = matchingAuditChain(summary, { eventIds: [] });
  }),
  /production smoke step LiveDevice missing auditChain eventIds evidence/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.eventIds = [];
    summary.auditChain = matchingAuditChain(summary);
  }),
  /production smoke step LiveDevice missing eventIds evidence/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.eventIds = [summary.eventId + 1];
    summary.auditChain = matchingAuditChain(summary);
  }),
  /production smoke step LiveDevice eventIds do not exactly match eventId/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.checkpoints = summary.checkpoints.filter((checkpoint) => checkpoint !== 'evidence_audit_chain_verified');
  }),
  /production smoke step LiveDevice missing evidence checkpoint: evidence_audit_chain_verified/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.checkpoints = summary.checkpoints.filter((checkpoint) => checkpoint !== 'review_event_bound_without_task_dispatch');
  }),
  /production smoke step LiveDevice missing evidence checkpoint: review_event_bound_without_task_dispatch/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    delete summary.eventId;
  }),
  /production smoke step LiveDevice missing eventId evidence/,
);

const eventIdAuditFallbackSmoke = await runProductionSmokeWithLiveDeviceMutation((summary) => {
  delete summary.eventIds;
});
assert.equal(eventIdAuditFallbackSmoke.ok, true);

const numericStringEventIdSmoke = await runProductionSmokeWithLiveDeviceMutation((summary) => {
  summary.eventId = String(summary.eventId);
  summary.eventIds = summary.eventIds.map(String);
});
assert.equal(numericStringEventIdSmoke.ok, true);

const longStringAuditIdentitySmoke = await runProductionSmokeWithLiveDeviceMutation((summary) => {
  summary.reviewItemId = '9007199254740993';
  summary.reviewCaseId = '9007199254740995';
  summary.eventId = '0009007199254740997';
  summary.eventIds = ['9007199254740997'];
  summary.exportJobNo = 'EXP-LONG-IDENTITY';
  summary.auditChain = {
    action: 'export_downloaded',
    reviewCaseId: '09007199254740995',
    reviewItemIds: ['0009007199254740993'],
    eventIds: ['09007199254740997'],
    exportJobNo: 'EXP-LONG-IDENTITY',
  };
});
assert.equal(longStringAuditIdentitySmoke.ok, true);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.auditChain = matchingAuditChain(summary, { reviewItemIds: [summary.reviewItemId + 1] });
  }),
  /production smoke step LiveDevice auditChain reviewItemIds do not exactly match reviewItemId/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.auditChain = matchingAuditChain(summary, { reviewCaseId: summary.reviewCaseId + 1 });
  }),
  /production smoke step LiveDevice auditChain reviewCaseId does not match reviewCaseId/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.auditChain = matchingAuditChain(summary, { eventIds: [summary.eventId + 1] });
  }),
  /production smoke step LiveDevice auditChain eventIds do not exactly match eventId/,
);

await assert.rejects(
  () => runProductionSmokeWithLiveDeviceMutation((summary) => {
    summary.auditChain = matchingAuditChain(summary, { exportJobNo: `${summary.exportJobNo}-mismatch` });
  }),
  /production smoke step LiveDevice auditChain exportJobNo does not match exportJobNo/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => ({
      status: 0,
      stdout: step.name === 'LiveDevice'
        ? JSON.stringify({
            status: 'passed',
            profile: 'device-video-web',
            reviewItemId: 1001,
            reviewCaseId: 2001,
            eventId: 7500,
            eventIds: [7500],
            exportJobNo: 'EXP-20260707-001',
            manifestValid: true,
            videoExportRequested: true,
            checkpoints: [
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
            ],
          })
        : summaryStdoutForStep(step.name),
    }),
  }),
  /production smoke step LiveDevice missing playback URL allow\/deny decision evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => ({
      status: 0,
      stdout: step.name === 'LiveDevice'
        ? JSON.stringify({
            status: 'passed',
            profile: 'device-video-web',
            reviewItemId: 1001,
            reviewCaseId: 2001,
            eventId: 7500,
            eventIds: [7500],
            exportJobNo: 'EXP-20260707-001',
            manifestValid: true,
            videoExportRequested: true,
            playback: {
              grantedDecision: 'granted',
              deniedDecision: 'denied',
              deniedReasons: ['camera_not_allowed'],
            },
            checkpoints: [
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
            ],
          })
        : summaryStdoutForStep(step.name),
    }),
  }),
  /production smoke step LiveDevice missing rule inertia\/loitering evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => {
      if (step.name !== 'LiveVideo') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      return {
        status: 0,
        stdout: JSON.stringify({
          checkpoints: [
            'alert_record_query_ok',
            'record_coverage_query_ok',
            'record_base_space_resolved',
            'record_storage_drift_patrol_ok',
            'record_export_posted',
            'record_export_download_ready',
            'record_export_download_probed',
            'record_export_manifest_verified',
          ],
          coverageSummary: {
            status: 'available',
            retainMode: 'motion',
            coverageSource: 'detection',
          },
          storageDriftSummary: {
            healthy: true,
            recordCount: 3,
            issueCount: 0,
            issueReasons: {},
          },
          exportResult: {
            exportId: 'review-export-1',
            downloadUrl: '/downloads/review-export-1.mp4',
            manifestUrl: '/manifests/review-export-1.json',
          },
          manifestSignature: {
            algorithm: 'hmac-sha256',
            keyId: '2026-q2',
            signatureVersion: 'v2',
          },
          manifestStorageLifecycle: {
            storageType: 'object_storage',
            status: 'persisted',
            expiresAt: '2026-07-20T00:00:00Z',
            exportPackageObjectKey: 'review-export-1/content.bin',
          },
          manifestVerification: {
            valid: true,
            signatureValid: true,
            signatureKeyAvailable: true,
            keyId: '2026-q2',
            signatureVersion: 'v2',
            violations: [],
          },
        }),
      };
    },
  }),
  /production smoke step LiveVideo missing standard storage drift reason evidence: file_missing/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => {
      if (step.name !== 'LiveVideo') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      return {
        status: 0,
        stdout: JSON.stringify({
          checkpoints: [
            'alert_record_query_ok',
            'record_coverage_query_ok',
            'record_base_space_resolved',
            'record_storage_drift_patrol_ok',
            'record_export_posted',
            'record_export_download_ready',
            'record_export_download_probed',
            'record_export_manifest_verified',
          ],
          coverageSummary: {
            status: 'available',
          },
          storageDriftSummary: {
            healthy: true,
            recordCount: 3,
            issueCount: 0,
            issueReasons: {},
            standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
          },
          exportResult: {
            exportId: 'review-export-1',
            downloadUrl: '/downloads/review-export-1.mp4',
            manifestUrl: '/manifests/review-export-1.json',
          },
          manifestSignature: {
            algorithm: 'hmac-sha256',
            keyId: '2026-q2',
            signatureVersion: 'v2',
          },
          manifestStorageLifecycle: {
            storageType: 'object_storage',
            status: 'persisted',
            expiresAt: '2026-07-20T00:00:00Z',
            exportPackageObjectKey: 'review-export-1/content.bin',
          },
          manifestVerification: {
            valid: true,
            signatureValid: true,
            signatureKeyAvailable: true,
            keyId: '2026-q2',
            signatureVersion: 'v2',
            violations: [],
          },
        }),
      };
    },
  }),
  /production smoke step LiveVideo missing coverage retain\/source evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => {
      if (step.name !== 'LiveVideo') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      return {
        status: 0,
        stdout: JSON.stringify({
          checkpoints: [
            'alert_record_query_ok',
            'record_coverage_query_ok',
            'record_base_space_resolved',
            'record_storage_drift_patrol_ok',
            'record_export_posted',
            'record_export_download_ready',
            'record_export_download_probed',
            'record_export_manifest_verified',
          ],
          coverageSummary: {
            status: 'available',
            retainMode: 'motion',
            coverageSource: 'detection',
          },
          storageDriftSummary: {
            healthy: true,
            recordCount: 3,
            issueCount: 0,
            issueReasons: {},
            standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
          },
          exportResult: {
            exportId: 'review-export-1',
            downloadUrl: '/downloads/review-export-1.mp4',
            manifestUrl: '/manifests/review-export-1.json',
          },
          manifestSignature: {
            algorithm: 'hmac-sha256',
            keyId: '2026-q2',
            signatureVersion: 'v2',
          },
          manifestStorageLifecycle: {
            storageType: 'object_storage',
            status: 'persisted',
            expiresAt: '2026-07-20T00:00:00Z',
            exportPackageObjectKey: 'review-export-1/content.bin',
          },
        }),
      };
    },
  }),
  /production smoke step LiveVideo missing valid manifest verifier evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => {
      if (step.name !== 'LiveVideo') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      return {
        status: 0,
        stdout: JSON.stringify({
          checkpoints: [
            'alert_record_query_ok',
            'record_coverage_query_ok',
            'record_base_space_resolved',
            'record_storage_drift_patrol_ok',
            'record_export_posted',
            'record_export_download_ready',
            'record_export_download_probed',
            'record_export_manifest_verified',
          ],
          coverageSummary: {
            status: 'available',
            retainMode: 'motion',
            coverageSource: 'detection',
          },
          storageDriftSummary: {
            healthy: true,
            recordCount: 3,
            issueCount: 0,
            issueReasons: {},
            standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
          },
          exportResult: {
            exportId: 'review-export-1',
            downloadUrl: '/downloads/review-export-1.mp4',
            manifestUrl: '/manifests/review-export-1.json',
          },
          manifestSignature: {
            algorithm: 'hmac-sha256',
            keyId: '2026-q2',
            signatureVersion: 'v2',
          },
          manifestStorageLifecycle: {
            storageType: 'object_storage',
            status: 'persisted',
            expiresAt: '2026-07-20T00:00:00Z',
            exportPackageObjectKey: 'review-export-1/content.bin',
          },
          manifestVerification: {
            valid: true,
            signatureValid: false,
            signatureKeyAvailable: false,
            keyId: '2026-q2',
            signatureVersion: 'v2',
            violations: [],
          },
        }),
      };
    },
  }),
  /production smoke step LiveVideo missing HMAC manifest verifier signature evidence/,
);

await assert.rejects(
  () => runProductionSmoke(parsed, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: () => {},
    runCommand: async (step) => {
      if (step.name !== 'LiveVideo') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      return {
        status: 0,
        stdout: JSON.stringify({
          checkpoints: [
            'alert_record_query_ok',
            'record_coverage_query_ok',
            'record_base_space_resolved',
            'record_storage_drift_patrol_ok',
            'record_export_posted',
            'record_export_download_ready',
            'record_export_download_probed',
            'record_export_manifest_verified',
          ],
          coverageSummary: {
            status: 'available',
            retainMode: 'motion',
            coverageSource: 'detection',
          },
          storageDriftSummary: {
            healthy: true,
            recordCount: 3,
            issueCount: 0,
            issueReasons: {},
            standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
          },
          exportResult: {
            exportId: 'review-export-1',
            downloadUrl: '/downloads/review-export-1.mp4',
            manifestUrl: '/manifests/review-export-1.json',
          },
          manifestSignature: {
            algorithm: 'hmac-sha256',
            keyId: '2026-q2',
            signatureVersion: 'v2',
          },
          manifestVerification: {
            valid: true,
            signatureValid: true,
            signatureKeyAvailable: true,
            keyId: '2026-q2',
            signatureVersion: 'v2',
            violations: [],
          },
        }),
      };
    },
  }),
  /production smoke step LiveVideo missing persisted manifest storage lifecycle evidence/,
);

await assert.rejects(
  () => runProductionSmoke({
    ...parsed,
    evidenceOutputFile: 'artifacts/review-smoke-failed.json',
  }, {
    nodePath: 'node',
    scriptDir: '.scripts',
    now: sequencedNow([
      '2026-07-07T00:00:00.000Z',
      '2026-07-07T00:00:00.025Z',
      '2026-07-07T00:00:00.040Z',
      '2026-07-07T00:00:00.050Z',
      '2026-07-07T00:00:00.100Z',
      '2026-07-07T00:00:00.200Z',
      '2026-07-07T00:00:00.400Z',
      '2026-07-07T00:00:00.500Z',
      '2026-07-07T00:00:00.800Z',
      '2026-07-07T00:00:00.900Z',
    ]),
    writeFile: (file, content) => {
      failedEvidenceWrites.push({ file, content });
    },
    runCommand: async (step) => ({ status: step.name === 'LiveVideo' ? 2 : 0, stdout: summaryStdoutForStep(step.name) }),
  }),
  /LiveVideo failed with exit code 2/,
);
assert.equal(failedEvidenceWrites.length, 1);
assert.equal(failedEvidenceWrites[0].file, 'artifacts/review-smoke-failed.json');
const failedEvidenceReport = JSON.parse(failedEvidenceWrites[0].content);
assert.equal(failedEvidenceReport.ok, false);
assert.equal(failedEvidenceReport.status, 'failed');
assert.equal(failedEvidenceReport.finishedAt, '2026-07-07T00:00:00.900Z');
assert.deepEqual(failedEvidenceReport.steps.map((step) => step.status), ['passed', 'passed', 'passed', 'failed']);
assert.equal(failedEvidenceReport.steps[3].name, 'LiveVideo');
assert.equal(failedEvidenceReport.steps[3].exitCode, 2);
assert.equal(failedEvidenceReport.steps[3].error, 'LiveVideo failed with exit code 2');
assert.equal(JSON.stringify(failedEvidenceReport).includes('token-1'), false);

const untrackedProductionSmoke = evaluateStatus(`
?? .scripts/alert-review-production-smoke.mjs
?? .scripts/alert-review-production-smoke.test.mjs
`);
assert.equal(untrackedProductionSmoke.ok, false);
assert.equal(untrackedProductionSmoke.blockers[0].group, 'FR release gate tooling');
assert.equal(untrackedProductionSmoke.blockers[1].group, 'FR release gate tooling');

const trackedProductionSmokeEntries = releaseEntriesForTrackedPaths([
  '.scripts/alert-review-production-smoke.mjs',
  '.scripts/alert-review-production-smoke.test.mjs',
]);
assert.equal(trackedProductionSmokeEntries.length, 2);

const retainedStorageSmoke = await runProductionSmoke(parsed, {
  nodePath: 'node',
  scriptDir: '.scripts',
  writeFile: () => {},
  runCommand: async (step) => {
    if (step.name !== 'LiveVideo') {
      return { status: 0, stdout: summaryStdoutForStep(step.name) };
    }
    const summary = JSON.parse(summaryStdoutForStep(step.name));
    summary.manifestStorageLifecycle.status = 'retained';
    summary.manifestStorageLifecycle.storageType = 'minio';
    return { status: 0, stdout: JSON.stringify(summary) };
  },
});
assert.equal(retainedStorageSmoke.ok, true);
assert.equal(retainedStorageSmoke.steps.find((step) => step.name === 'LiveVideo')?.status, 'passed');

console.log('alert review production smoke tests OK');

function runProductionSmokeWithLiveDeviceMutation(mutate, options = {}) {
  return runProductionSmoke({
    ...parsed,
    evidenceOutputFile: options.evidenceOutputFile || parsed.evidenceOutputFile,
  }, {
    nodePath: 'node',
    scriptDir: '.scripts',
    writeFile: options.writeFile || (() => {}),
    runCommand: async (step) => {
      if (step.name !== 'LiveDevice') {
        return { status: 0, stdout: summaryStdoutForStep(step.name) };
      }
      const summary = JSON.parse(summaryStdoutForStep(step.name));
      mutate(summary);
      return { status: 0, stdout: JSON.stringify(summary) };
    },
  });
}

function matchingAuditChain(summary, overrides = {}) {
  return {
    action: 'export_downloaded',
    reviewCaseId: summary.reviewCaseId,
    reviewItemIds: [summary.reviewItemId],
    eventIds: [summary.eventId],
    exportJobNo: summary.exportJobNo,
    ...overrides,
  };
}

function summaryStdoutForStep(name, options = {}) {
  const playerRecordPath = options.playerRecordPath || 'https://media.example.test/records/device-01/20260705-100000.mp4';
  if (name === 'LiveDevice') {
    return JSON.stringify({
      status: 'passed',
      profile: 'device-video-web',
      reviewItemId: 1001,
      reviewCaseId: 2001,
      eventId: 7500,
      eventIds: [7500],
      exportJobNo: 'EXP-20260707-001',
      manifestValid: true,
      videoExportRequested: true,
      ruleEvidence: {
        ruleCode: 'restricted_area',
        cameraId: 'camera-smoke',
        zoneCode: 'zone-smoke',
        objectLabel: 'person',
        inertiaFrames: 3,
        loiteringSeconds: 20,
      },
      playback: {
        grantedDecision: 'granted',
        deniedDecision: 'denied',
        deniedReasons: ['camera_not_allowed'],
      },
      checkpoints: [
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
      ],
    });
  }
  if (name === 'LiveVideo') {
    return JSON.stringify({
      checkpoints: [
        'alert_record_query_ok',
        'record_coverage_query_ok',
        'record_base_space_resolved',
        'record_storage_drift_patrol_ok',
        'record_export_posted',
        'record_export_download_ready',
        'record_export_download_probed',
        'record_export_manifest_verified',
      ],
      coverageSummary: {
        status: 'available',
        retainMode: 'motion',
        coverageSource: 'detection',
      },
      storageDriftSummary: {
        healthy: true,
        recordCount: 3,
        issueCount: 0,
        issueReasons: {},
        standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
      },
      exportResult: {
        exportId: 'review-export-1',
        downloadUrl: '/downloads/review-export-1.mp4',
        manifestUrl: '/manifests/review-export-1.json',
      },
      manifestSignature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
      },
      manifestStorageLifecycle: {
        storageType: 'object_storage',
        status: 'persisted',
        expiresAt: '2026-07-20T00:00:00Z',
        exportPackageObjectKey: 'review-export-1/content.bin',
      },
      manifestVerification: {
        valid: true,
        signatureValid: true,
        signatureKeyAvailable: true,
        keyId: '2026-q2',
        signatureVersion: 'v2',
        verifier: 'record-export-manifest-verifier',
        canonicalHash: 'sha256:manifest',
      },
    });
  }
  if (name === 'LivePlayer:coverage' || name === 'LivePlayer:case-timeline') {
    return JSON.stringify({
      clickedRow: true,
      clickedAction: true,
      seekTime: '2026-07-05T10:00:00',
      recordPath: playerRecordPath,
      playbackOffsetSeconds: 0,
      nativeCurrentTime: 0.15,
      nativeCurrentSrc: playerRecordPath,
      nativeReadyState: 4,
      nativePaused: false,
      nativeDuration: 120,
      nativeError: null,
      nativePlayingObserved: true,
    });
  }
  if (name === 'LivePlayer:detail') {
    return JSON.stringify({
      clickedRow: true,
      clickedAction: true,
      seekTime: '2026-07-05T10:00:30',
      recordPath: playerRecordPath,
      playbackOffsetSeconds: 30,
      nativeCurrentTime: 30.25,
      nativeCurrentSrc: playerRecordPath,
      nativeReadyState: 4,
      nativePaused: false,
      nativeDuration: 120,
      nativeError: null,
      nativePlayingObserved: true,
    });
  }
  return '';
}

function sequencedNow(values) {
  let index = 0;
  return () => new Date(values[Math.min(index++, values.length - 1)]);
}
