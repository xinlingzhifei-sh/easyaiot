import assert from 'node:assert/strict';

import {
  REPORT_SCHEMA_VERSION,
  buildReconcileHeaders,
  buildReconcileUrl,
  parseArgs,
  requiredOptionErrors,
  runReconcile,
} from './alert-review-segment-data-reconcile.mjs';

const releaseOptions = parseArgs([
  '--device-base-url=https://device.release.example/admin-api',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
  '--timeout-ms=5000',
], {});

assert.equal(releaseOptions.deviceBaseUrl, 'https://device.release.example/admin-api');
assert.equal(releaseOptions.token, 'jwt-token-1');
assert.equal(releaseOptions.tenantId, 7);
assert.equal(releaseOptions.operatorUserId, 9001);
assert.equal(releaseOptions.mode, 'dry-run');
assert.equal(releaseOptions.repair, false);
assert.equal(releaseOptions.allowLocalEndpoints, false);
assert.equal(releaseOptions.timeoutMs, 5000);

const envOptions = parseArgs([], {
  YFEIEYE_DEVICE_BASE_URL: 'https://device.env.example/api',
  YFEIEYE_DEVICE_AUTH_TOKEN: 'env-token',
  YFEIEYE_DEVICE_TENANT_ID: '8',
  YFEIEYE_REVIEW_RECONCILE_OPERATOR_USER_ID: '9100',
  YFEIEYE_REVIEW_RECONCILE_TIMEOUT_MS: '6000',
});
assert.equal(envOptions.deviceBaseUrl, 'https://device.env.example/api');
assert.equal(envOptions.token, 'env-token');
assert.equal(envOptions.tenantId, 8);
assert.equal(envOptions.operatorUserId, 9100);
assert.equal(envOptions.timeoutMs, 6000);
assert.equal(envOptions.repair, false);

const repairOptions = parseArgs([
  '--device-base-url=https://device.release.example/admin-api/',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
  '--repair',
], {});
assert.equal(repairOptions.mode, 'repair');
assert.equal(repairOptions.repair, true);

const explicitDryRunOptions = parseArgs([
  '--device-base-url=https://device.release.example/admin-api',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
  '--dry-run',
], {});
assert.equal(explicitDryRunOptions.mode, 'dry-run');
assert.equal(explicitDryRunOptions.repair, false);

assert.throws(
  () => parseArgs(['--dry-run', '--repair'], {}),
  /cannot combine --dry-run and --repair/,
);
assert.throws(
  () => parseArgs(['--unexpected'], {}),
  /Unknown argument: --unexpected/,
);

assert.deepEqual(requiredOptionErrors(parseArgs([], {})), [
  'missing --device-base-url or YFEIEYE_DEVICE_BASE_URL',
  'missing --token or YFEIEYE_DEVICE_AUTH_TOKEN',
  'missing --tenant-id or YFEIEYE_DEVICE_TENANT_ID',
  'missing --operator-user-id or YFEIEYE_REVIEW_RECONCILE_OPERATOR_USER_ID',
]);

const localOptions = parseArgs([
  '--device-base-url=http://127.0.0.1:8080/admin-api',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
], {});
assert.deepEqual(requiredOptionErrors(localOptions), [
  'segment/data reconcile endpoint must not use a local/mock URL without --allow-local-endpoints',
]);
assert.deepEqual(requiredOptionErrors(parseArgs([
  '--device-base-url=https://device.mock.example/mock/api',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
], {})), [
  'segment/data reconcile endpoint must not use a local/mock URL without --allow-local-endpoints',
]);
assert.deepEqual(requiredOptionErrors(parseArgs([
  '--device-base-url=http://localhost:8080/admin-api',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
  '--allow-local-endpoints',
], {})), []);
assert.deepEqual(requiredOptionErrors(parseArgs([
  '--device-base-url=ftp://device.release.example/admin-api',
  '--token=jwt-token-1',
  '--tenant-id=7',
  '--operator-user-id=9001',
], {})), [
  'segment/data reconcile endpoint must use http or https',
]);

assert.equal(
  buildReconcileUrl(releaseOptions),
  'https://device.release.example/admin-api/system/supervision/alert-review/runtime-reconcile?operatorUserId=9001&repair=false',
);
assert.equal(
  buildReconcileUrl(repairOptions),
  'https://device.release.example/admin-api/system/supervision/alert-review/runtime-reconcile?operatorUserId=9001&repair=true',
);
assert.deepEqual(buildReconcileHeaders(releaseOptions), {
  authorization: 'Bearer jwt-token-1',
  'tenant-id': '7',
});

const dryRunCalls = [];
const dryRunReport = await runReconcile(releaseOptions, {
  now: () => new Date('2026-07-11T08:00:00.000Z'),
  fetchImpl: async (url, init) => {
    dryRunCalls.push({ url: String(url), init });
    return jsonResponse({
      code: 0,
      data: {
        scannedCount: 12,
        repairedRecordCount: 0,
        repairedSemanticIndexCount: 0,
        failedExportJobCount: 1,
        findings: [],
        healthReport: {
          totalCount: 12,
          repairableCount: 3,
          alerts: [
            'review_data_schema_drift',
            'review_segment_double_write_drift',
            'evidence_export_failed',
          ],
          measuredAt: '2026-07-11T15:59:59',
          operatorUserId: 9001,
        },
        reconciledAt: '2026-07-11T16:00:00',
        operatorUserId: 9001,
      },
    });
  },
});

assert.equal(dryRunCalls.length, 1);
assert.equal(dryRunCalls[0].url, buildReconcileUrl(releaseOptions));
assert.equal(dryRunCalls[0].init.method, 'POST');
assert.deepEqual(dryRunCalls[0].init.headers, buildReconcileHeaders(releaseOptions));
assert.equal(dryRunReport.schemaVersion, REPORT_SCHEMA_VERSION);
assert.equal(dryRunReport.ok, true);
assert.equal(dryRunReport.status, 'drift_detected');
assert.equal(dryRunReport.mode, 'dry-run');
assert.equal(dryRunReport.generatedAt, '2026-07-11T08:00:00.000Z');
assert.equal(dryRunReport.tenantId, 7);
assert.equal(dryRunReport.operatorUserId, 9001);
assert.equal(dryRunReport.scannedCount, 12);
assert.deepEqual(dryRunReport.drift, {
  detected: {
    reviewDataSchema: true,
    reviewSegmentDoubleWrite: true,
  },
  remaining: {
    reviewDataSchema: true,
    reviewSegmentDoubleWrite: true,
  },
});
assert.deepEqual(dryRunReport.repairs, {
  requested: false,
  reviewData: { count: 0, reviewItemIds: [] },
  reviewSegment: { count: 0, reviewItemIds: [] },
});
assert.deepEqual(dryRunReport.targetFindings, []);
assert.deepEqual(dryRunReport.otherFindings, []);
assert.deepEqual(dryRunReport.alerts, [
  'review_data_schema_drift',
  'review_segment_double_write_drift',
  'evidence_export_failed',
]);
assert.deepEqual(dryRunReport.runtimeSummary, {
  repairedRecordCount: 0,
  repairedSemanticIndexCount: 0,
  failedExportJobCount: 1,
  repairableCount: 3,
});

const repairReport = await runReconcile(repairOptions, {
  now: () => new Date('2026-07-11T08:01:00.000Z'),
  fetchImpl: async () => jsonResponse({
    code: 0,
    data: {
      scannedCount: 12,
      repairedRecordCount: 1,
      repairedSemanticIndexCount: 1,
      failedExportJobCount: 0,
      findings: [
        'review_data_repaired:101',
        'review_segment_repaired:101',
        'review_segment_repaired:102',
        'review_segment_repaired:9007199254740993',
        'record_repaired:103',
      ],
      healthReport: {
        totalCount: 12,
        repairableCount: 0,
        alerts: [],
        measuredAt: '2026-07-11T16:00:59',
        operatorUserId: 9001,
      },
      reconciledAt: '2026-07-11T16:01:00',
      operatorUserId: 9001,
    },
  }),
});
assert.equal(repairReport.status, 'repaired');
assert.equal(repairReport.mode, 'repair');
assert.deepEqual(repairReport.drift, {
  detected: {
    reviewDataSchema: true,
    reviewSegmentDoubleWrite: true,
  },
  remaining: {
    reviewDataSchema: false,
    reviewSegmentDoubleWrite: false,
  },
});
assert.deepEqual(repairReport.repairs, {
  requested: true,
  reviewData: { count: 1, reviewItemIds: [101] },
  reviewSegment: { count: 3, reviewItemIds: [101, 102, '9007199254740993'] },
});
assert.deepEqual(repairReport.targetFindings, [
  'review_data_repaired:101',
  'review_segment_repaired:101',
  'review_segment_repaired:102',
  'review_segment_repaired:9007199254740993',
]);
assert.deepEqual(repairReport.otherFindings, ['record_repaired:103']);

const incompleteRepairReport = await runReconcile(repairOptions, {
  fetchImpl: async () => jsonResponse({
    code: 0,
    data: {
      scannedCount: 2,
      repairedRecordCount: 0,
      repairedSemanticIndexCount: 0,
      failedExportJobCount: 0,
      findings: ['review_segment_repaired:item-external'],
      healthReport: {
        totalCount: 2,
        repairableCount: 1,
        alerts: ['review_segment_double_write_drift'],
      },
      operatorUserId: 9001,
    },
  }),
});
assert.equal(incompleteRepairReport.status, 'repair_incomplete');
assert.deepEqual(incompleteRepairReport.repairs.reviewSegment.reviewItemIds, ['item-external']);

const cleanReport = await runReconcile(explicitDryRunOptions, {
  fetchImpl: async () => jsonResponse({
    code: 0,
    data: {
      scannedCount: 0,
      repairedRecordCount: 0,
      repairedSemanticIndexCount: 0,
      failedExportJobCount: 0,
      findings: [],
      healthReport: { totalCount: 0, repairableCount: 0, alerts: [] },
      operatorUserId: 9001,
    },
  }),
});
assert.equal(cleanReport.status, 'clean');

let localFetchCalled = false;
await assert.rejects(
  () => runReconcile(localOptions, {
    fetchImpl: async () => {
      localFetchCalled = true;
      return jsonResponse({});
    },
  }),
  /must not use a local\/mock URL/,
);
assert.equal(localFetchCalled, false);

await assert.rejects(
  () => runReconcile(releaseOptions, {
    fetchImpl: async () => jsonResponse({ code: 401, msg: 'unauthorized' }),
  }),
  /returned code 401: unauthorized/,
);

await assert.rejects(
  () => runReconcile(releaseOptions, {
    fetchImpl: async () => jsonResponse({ message: 'gateway unavailable' }, 503),
  }),
  /failed with HTTP 503/,
);

await assert.rejects(
  () => runReconcile(releaseOptions, {
    fetchImpl: async () => jsonResponse({ code: 0, data: { scannedCount: 1 } }),
  }),
  /response missing healthReport/,
);

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

console.log('alert review segment/data reconcile tests OK');
