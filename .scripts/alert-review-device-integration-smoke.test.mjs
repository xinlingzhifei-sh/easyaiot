import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';

import {
  REQUIRED_CHECKPOINTS,
  buildSmokeBody,
  buildSmokeUrl,
  parseArgs,
  requiredOptionErrors,
  runSmoke,
  sanitizeCliOutputText,
  sanitizeCliOutputValue,
  validateSmokeResult,
} from './alert-review-device-integration-smoke.mjs';
import {
  evaluateStatus,
  releaseEntriesForTrackedPaths,
} from './verify-alert-review-release-package.mjs';

assert.ok(REQUIRED_CHECKPOINTS.includes('review_rule_saved'));
assert.ok(REQUIRED_CHECKPOINTS.includes('review_event_bound_without_task_dispatch'));
assert.throws(
  () => parseArgs(['--token=standalone-device-secret'], {}),
  /DEVICE smoke token must be provided through YFEIEYE_DEVICE_AUTH_TOKEN/,
);

const signedCliFailure = spawnSync(process.execPath, [
  '.scripts/alert-review-device-integration-smoke.mjs',
  '--bogus=/video/record/device-01.mp4?token=device-leading-secret#device-leading-fragment record.mp4?token=device-relative-secret#device-relative-fragment',
], { encoding: 'utf8' });
assert.equal(signedCliFailure.status, 1);
assert.equal(signedCliFailure.stderr.includes('device-leading-secret'), false);
assert.equal(signedCliFailure.stderr.includes('device-relative-secret'), false);
assert.match(signedCliFailure.stderr, /--bogus=\/video\/record\/device-01\.mp4 record\.mp4/);
assert.equal(
  sanitizeCliOutputText('/video/record/device-01.mp4?token=leading record.mp4?token=relative'),
  '/video/record/device-01.mp4 record.mp4',
);
assert.deepEqual(
  sanitizeCliOutputValue({
    ruleEvidence: {
      message: 'record.mp4?token=device-rule-secret#device-rule-fragment',
    },
  }),
  { ruleEvidence: { message: 'record.mp4' } },
);

const playbackMaterialCliFailure = spawnSync(process.execPath, [
  '.scripts/alert-review-device-integration-smoke.mjs',
  '--playback-material-uri=record.mp4?token=device-playback-argv-secret',
], { encoding: 'utf8' });
assert.equal(playbackMaterialCliFailure.status, 1);
assert.equal(playbackMaterialCliFailure.stderr.includes('device-playback-argv-secret'), false);
assert.match(playbackMaterialCliFailure.stderr, /YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI/);

const parsed = parseArgs([
  '--device-base-url=http://device.local/api',
  '--tenant-id=42',
  '--operator-user-id=9001',
  '--alert-time=2026-07-05T10:00:00',
  '--profile=device-video-web',
  '--device-id=camera-real-01',
  '--camera-id=camera-real-01',
  '--zone-code=zone-real-01',
  '--source-alert-id=alert-real-01',
  '--allowed-camera-ids=camera-real-01',
  '--timeout-ms=5000',
  '--playback-review-item-id=1001',
  '--playback-review-case-id=2001',
  '--playback-allowed-camera-ids=camera-01',
  '--playback-denied-camera-ids=camera-02',
  '--playback-reason=release-smoke-playback',
], {
  YFEIEYE_DEVICE_AUTH_TOKEN: 'token-1',
  YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI: 'playback-url.mp4',
});
assert.equal(parsed.deviceBaseUrl, 'http://device.local/api');
assert.equal(parsed.token, 'token-1');
assert.equal(parsed.tenantId, 42);
assert.equal(parsed.operatorUserId, 9001);
assert.equal(parsed.alertTime, '2026-07-05T10:00:00');
assert.equal(parsed.profile, 'device-video-web');
assert.equal(parsed.deviceId, 'camera-real-01');
assert.equal(parsed.cameraId, 'camera-real-01');
assert.equal(parsed.zoneCode, 'zone-real-01');
assert.equal(parsed.sourceAlertId, 'alert-real-01');
assert.deepEqual(parsed.allowedCameraIds, ['camera-real-01']);
assert.equal(parsed.includeVideoExport, true);
assert.equal(parsed.timeoutMs, 5000);
assert.equal(parsed.playbackReviewItemId, 1001);
assert.equal(parsed.playbackReviewCaseId, 2001);
assert.equal(parsed.playbackMaterialUri, 'playback-url.mp4');
assert.deepEqual(parsed.playbackAllowedCameraIds, ['camera-01']);
assert.deepEqual(parsed.playbackDeniedCameraIds, ['camera-02']);
assert.equal(parsed.playbackReason, 'release-smoke-playback');

const fromEnv = parseArgs([], {
  YFEIEYE_DEVICE_BASE_URL: 'https://release-device.local/admin-api',
  YFEIEYE_DEVICE_AUTH_TOKEN: 'env-token',
  YFEIEYE_DEVICE_TENANT_ID: '84',
  YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID: '9200',
  YFEIEYE_DEVICE_SMOKE_ALERT_TIME: '2026-07-05T11:00:00',
  YFEIEYE_DEVICE_SMOKE_DEVICE_ID: 'camera-env-01',
  YFEIEYE_DEVICE_SMOKE_CAMERA_ID: 'camera-env-01',
  YFEIEYE_DEVICE_SMOKE_ZONE_CODE: 'zone-env-01',
  YFEIEYE_DEVICE_SMOKE_SOURCE_ALERT_ID: 'alert-env-01',
  YFEIEYE_DEVICE_SMOKE_ALLOWED_CAMERA_IDS: 'camera-env-01',
  YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS: 'camera-env-allow',
  YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS: 'camera-env-deny',
});
assert.equal(fromEnv.deviceBaseUrl, 'https://release-device.local/admin-api');
assert.equal(fromEnv.token, 'env-token');
assert.equal(fromEnv.tenantId, 84);
assert.equal(fromEnv.operatorUserId, 9200);
assert.equal(fromEnv.profile, 'release');
assert.equal(fromEnv.includeVideoExport, true);
assert.equal(fromEnv.deviceId, 'camera-env-01');
assert.equal(fromEnv.cameraId, 'camera-env-01');
assert.equal(fromEnv.zoneCode, 'zone-env-01');
assert.equal(fromEnv.sourceAlertId, 'alert-env-01');
assert.deepEqual(fromEnv.allowedCameraIds, ['camera-env-01']);
assert.deepEqual(fromEnv.playbackAllowedCameraIds, ['camera-env-allow']);
assert.deepEqual(fromEnv.playbackDeniedCameraIds, ['camera-env-deny']);

assert.deepEqual(requiredOptionErrors(parseArgs([], {
  YFEIEYE_DEVICE_SMOKE_PROFILE: 'service-synthetic',
})), [
  'missing --device-base-url or YFEIEYE_DEVICE_BASE_URL',
  'missing YFEIEYE_DEVICE_AUTH_TOKEN',
  'missing --tenant-id or YFEIEYE_DEVICE_TENANT_ID',
  'missing --operator-user-id or YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID',
  'missing --alert-time or YFEIEYE_DEVICE_SMOKE_ALERT_TIME',
]);

assert.equal(
  buildSmokeUrl(parsed),
  'http://device.local/api/system/supervision/alert-review/integration-smoke',
);

assert.deepEqual(buildSmokeBody(parsed), {
  operatorUserId: 9001,
  includeVideoExport: true,
  alertTime: 1783216800000,
  profile: 'device-video-web',
  deviceId: 'camera-real-01',
  cameraId: 'camera-real-01',
  zoneCode: 'zone-real-01',
  sourceAlertId: 'alert-real-01',
  allowedCameraIds: ['camera-real-01'],
});

assert.throws(
  () => buildSmokeBody({ ...parsed, alertTime: 'not-a-real-alert-time' }),
  /valid alert time/i,
);

assert.deepEqual(
  requiredOptionErrors({ ...parsed, deviceId: 'different-video-device' }),
  ['real profile requires deviceId and cameraId to identify the same VIDEO camera'],
);

const validPayload = {
  status: 'passed',
  reviewItemId: 1001,
  reviewCaseId: 2001,
  eventId: 7001,
  exportJobNo: 'REJ-1',
  manifestValid: true,
  videoExportRequested: true,
  videoExportConfirmed: true,
  ruleEvidence: {
    ruleCode: 'restricted_area',
    cameraId: 'camera-smoke',
    zoneCode: 'zone-smoke',
    objectLabel: 'person',
    inertiaFrames: 3,
    loiteringSeconds: 20,
  },
  checkpoints: [
    'device_api_reachable',
    'video_record_query_checked',
    'real_record_coverage_checked',
    'video_export_confirmed',
    ...REQUIRED_CHECKPOINTS,
    'sample_web_contract_renderable',
  ],
};
const validated = validateSmokeResult(validPayload);
assert.equal(validated.ok, true);
assert.deepEqual(validated.checkpoints, validPayload.checkpoints);
assert.deepEqual(validated.ruleEvidence, validPayload.ruleEvidence);
assert.equal(validated.eventId, 7001);

assert.throws(
  () => validateSmokeResult({ ...validPayload, eventId: undefined }),
  /integration smoke response missing positive eventId/,
);

assert.throws(
  () => validateSmokeResult({ ...validPayload, eventId: 0 }),
  /integration smoke response missing positive eventId/,
);

assert.throws(
  () => validateSmokeResult({ ...validPayload, ruleEvidence: undefined }),
  /integration smoke response missing rule evidence/,
);

assert.throws(
  () => validateSmokeResult({ ...validPayload, videoExportConfirmed: false }),
  /integration smoke videoExportConfirmed was not true/,
);

assert.deepEqual(requiredOptionErrors(parseArgs([
  '--device-base-url=http://device.local/api',
  '--tenant-id=42',
  '--operator-user-id=9001',
  '--alert-time=2026-07-05T10:00:00',
  '--profile=device-video-web',
], { YFEIEYE_DEVICE_AUTH_TOKEN: 'token-1' })), [
  'missing --camera-id or YFEIEYE_DEVICE_SMOKE_CAMERA_ID for real profile',
  'missing --device-id or YFEIEYE_DEVICE_SMOKE_DEVICE_ID for real profile',
  'missing --zone-code or YFEIEYE_DEVICE_SMOKE_ZONE_CODE for real profile',
  'missing --allowed-camera-ids or YFEIEYE_DEVICE_SMOKE_ALLOWED_CAMERA_IDS for real profile',
]);

assert.throws(
  () => validateSmokeResult({
    ...validPayload,
    ruleEvidence: { ...validPayload.ruleEvidence, loiteringSeconds: 0 },
  }),
  /integration smoke rule evidence missing loiteringSeconds=20/,
);

const validAuditEntry = {
  reviewCaseId: 2001,
  reviewItemId: null,
  actionType: 'export_downloaded',
  jobNo: 'REJ-1',
  fileHash: 'sha256:must-not-be-emitted',
  evidenceUris: ['https://storage.example/package.zip?secret=must-not-be-emitted'],
  boundEventIds: [7001],
  metadata: {
    reviewCaseId: 2001,
    reviewItemIds: [1001],
    eventIds: [7001],
    exportJobNo: 'REJ-1',
  },
};

const calls = [];
const smoke = await runSmoke(parsed, {
  fetchImpl: async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (calls.length === 1) {
      assert.equal(String(url), 'http://device.local/api/system/supervision/alert-review/integration-smoke');
      assert.equal(init.method, 'POST');
      assert.equal(init.headers.authorization, 'Bearer token-1');
      assert.equal(init.headers['tenant-id'], '42');
      assert.equal(init.headers['content-type'], 'application/json');
      assert.deepEqual(JSON.parse(init.body), buildSmokeBody(parsed));
      return jsonResponse({ code: 0, data: validPayload });
    }
    const requestUrl = new URL(String(url));
    assert.equal(init.method, 'GET');
    assert.equal(init.headers.authorization, 'Bearer token-1');
    assert.equal(init.headers['tenant-id'], '42');
    if (calls.length === 2) {
      assert.equal(requestUrl.pathname, '/api/system/supervision/alert-review/evidence-audit');
      assert.equal(requestUrl.searchParams.get('eventId'), '7001');
      assert.equal(requestUrl.searchParams.get('reviewCaseId'), '2001');
      assert.equal(requestUrl.searchParams.get('reviewItemId'), '1001');
      assert.equal(requestUrl.searchParams.get('exportJobNo'), 'REJ-1');
      return jsonResponse({ code: 0, data: [validAuditEntry] });
    }
    assert.equal(requestUrl.pathname, '/api/system/supervision/alert-review/items/1001/playback-url');
    assert.equal(requestUrl.searchParams.get('reviewCaseId'), '2001');
    assert.equal(requestUrl.searchParams.get('materialUri'), 'playback-url.mp4');
    if (calls.length === 3) {
      assert.equal(requestUrl.searchParams.get('allowedCameraIds'), 'camera-01');
      assert.equal(requestUrl.searchParams.get('reason'), 'release-smoke-playback allow');
      return jsonResponse({
        code: 0,
        data: {
          reviewItemId: 1001,
          reviewCaseId: 2001,
          materialUri: 'playback-url.mp4',
          playbackUrl: 'playback-url.mp4',
          decision: 'granted',
          deniedReasons: [],
        },
      });
    }
    assert.equal(requestUrl.searchParams.get('allowedCameraIds'), 'camera-02');
    assert.equal(requestUrl.searchParams.get('reason'), 'release-smoke-playback deny');
    return jsonResponse({
      code: 0,
      data: {
        reviewItemId: 1001,
        reviewCaseId: 2001,
        materialUri: 'playback-url.mp4',
        playbackUrl: null,
        decision: 'denied',
        deniedReasons: ['camera_not_allowed'],
      },
    });
  },
});
assert.equal(smoke.ok, true);
assert.equal(smoke.result.reviewItemId, 1001);
assert.deepEqual(smoke.auditChain, {
  action: 'export_downloaded',
  reviewCaseId: 2001,
  reviewItemIds: [1001],
  eventIds: [7001],
  exportJobNo: 'REJ-1',
});
assert.deepEqual(smoke.playback.checkpoints, ['playback_url_granted', 'playback_url_denied']);
assert.ok(smoke.checkpoints.includes('evidence_audit_chain_verified'));
assert.ok(smoke.checkpoints.includes('playback_url_granted'));
assert.ok(smoke.checkpoints.includes('playback_url_denied'));
assert.equal(calls.length, 4);

const parsedWithoutPlayback = {
  ...parsed,
  playbackReviewItemId: Number.NaN,
  playbackReviewCaseId: Number.NaN,
  playbackMaterialUri: '',
  playbackAllowedCameraIds: [],
  playbackDeniedCameraIds: [],
};

async function runWithAuditEntries(entries) {
  let callCount = 0;
  return runSmoke(parsedWithoutPlayback, {
    fetchImpl: async () => {
      callCount += 1;
      return callCount === 1
        ? jsonResponse({ code: 0, data: validPayload })
        : jsonResponse({ code: 0, data: entries });
    },
  });
}

await assert.rejects(
  () => runWithAuditEntries([]),
  /evidence audit missing matching export_downloaded entry/,
);

for (const [label, entry] of [
  ['reviewCaseId', { ...validAuditEntry, reviewCaseId: 2999 }],
  ['jobNo', { ...validAuditEntry, jobNo: 'REJ-other' }],
  ['boundEventIds', { ...validAuditEntry, boundEventIds: [7999] }],
  ['metadata.reviewItemIds', {
    ...validAuditEntry,
    metadata: { ...validAuditEntry.metadata, reviewItemIds: [1999] },
  }],
  ['metadata.eventIds', {
    ...validAuditEntry,
    metadata: { ...validAuditEntry.metadata, eventIds: [7999] },
  }],
  ['metadata.exportJobNo', {
    ...validAuditEntry,
    metadata: { ...validAuditEntry.metadata, exportJobNo: 'REJ-other' },
  }],
]) {
  await assert.rejects(
    () => runWithAuditEntries([entry]),
    /evidence audit missing matching export_downloaded entry/,
    label,
  );
}

await assert.rejects(
  () => runSmoke(parseArgs([
    '--device-base-url=http://device.local/api',
    '--tenant-id=42',
    '--operator-user-id=9001',
    '--alert-time=2026-07-05T10:00:00',
    '--playback-allowed-camera-ids=camera-01',
  ], { YFEIEYE_DEVICE_AUTH_TOKEN: 'token-1' }), { fetchImpl: async () => jsonResponse({}) }),
  /missing --playback-denied-camera-ids or YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS/,
);

await assert.rejects(
  () => runSmoke(parseArgs([], {}), { fetchImpl: async () => jsonResponse({}) }),
  /missing --device-base-url/,
);

await assert.rejects(
  () => runSmoke(parsed, {
    fetchImpl: async () => jsonResponse({
      code: 0,
      data: {
        ...validPayload,
        checkpoints: REQUIRED_CHECKPOINTS.filter((checkpoint) => checkpoint !== 'evidence_download_bytes_verified'),
      },
    }),
  }),
  /missing smoke checkpoint: evidence_download_bytes_verified/,
);

await assert.rejects(
  () => runSmoke(parsed, {
    fetchImpl: async () => jsonResponse({
      code: 0,
      data: {
        ...validPayload,
        checkpoints: REQUIRED_CHECKPOINTS.filter((checkpoint) => checkpoint !== 'evidence_download_audited'),
      },
    }),
  }),
  /missing smoke checkpoint: evidence_download_audited/,
);

await assert.rejects(
  () => runSmoke(parsed, {
    fetchImpl: async () => jsonResponse({
      code: 0,
      data: {
        ...validPayload,
        manifestValid: false,
      },
    }),
  }),
  /integration smoke manifestValid was not true/,
);

await assert.rejects(
  () => runSmoke(parsed, {
    fetchImpl: async () => jsonResponse({
      code: 0,
      data: {
        ...validPayload,
        videoExportRequested: false,
      },
    }),
  }),
  /integration smoke videoExportRequested was not true/,
);

const untrackedDeviceSmoke = evaluateStatus(`
?? .scripts/alert-review-device-integration-smoke.mjs
?? .scripts/alert-review-device-integration-smoke.test.mjs
`);
assert.equal(untrackedDeviceSmoke.ok, false);
assert.equal(untrackedDeviceSmoke.blockers[0].group, 'FR release gate tooling');
assert.equal(untrackedDeviceSmoke.blockers[1].group, 'FR release gate tooling');

const trackedDeviceSmokeEntries = releaseEntriesForTrackedPaths([
  '.scripts/alert-review-device-integration-smoke.mjs',
  '.scripts/alert-review-device-integration-smoke.test.mjs',
]);
assert.equal(trackedDeviceSmokeEntries.length, 2);

function jsonResponse(body, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 200 ? 'OK' : 'ERROR',
    async text() {
      return JSON.stringify(body);
    },
  };
}

console.log('alert review DEVICE integration smoke tests OK');
