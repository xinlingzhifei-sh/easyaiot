import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import {
  buildAvailabilityUrl,
  buildExportBody,
  buildManifestVerifierEnvironment,
  parseArgs,
  requiredOptionErrors,
  resolveDownloadUrl,
  runSmoke,
  selectPlayableSegment,
  summarizeCliResult,
} from './alert-review-video-live-smoke.mjs';
import {
  evaluateStatus,
  releaseEntriesForTrackedPaths,
} from './verify-alert-review-release-package.mjs';

const SOURCE_SEGMENT_HASH = 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
const OUTPUT_FILE_HASH = 'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb';
const FFMPEG_COMMAND_HASH = 'sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc';
const MANIFEST_SIGNATURE_VALUE = 'hmac-sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd';
const STANDARD_STORAGE_DRIFT_REASON_KEYS = [
  'file_missing',
  'retention_expired',
  'disk_full',
  'cache_flush_failed',
];
assert.throws(
  () => parseArgs(['--token=standalone-video-secret'], {}),
  /VIDEO smoke token must be provided through YFEIEYE_VIDEO_SMOKE_TOKEN/,
);
assert.throws(
  () => parseArgs(['--playback-material-uri=record.mp4?token=standalone-uri-secret'], {}),
  /VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI/,
);

function urlPathEndsWith(url, suffix) {
  return new URL(String(url)).pathname.endsWith(suffix);
}

const parsed = parseArgs([
  '--alert-record-query-url=http://video.local/video/record/availability',
  '--record-coverage-query-url=http://video.local/video/record/availability',
  '--record-base-url=http://video.local/video/record',
  '--record-export-url=http://video.local/video/record/export',
  '--device-id=device-01',
  '--camera-id=camera-01',
  '--alert-time=2026-07-05 10:00:00',
  '--time-range=120',
  '--source-alert-id=alert-001',
  '--record-drift-retention-hours=24',
  '--allow-local-endpoints',
], { YFEIEYE_VIDEO_SMOKE_TOKEN: 'token-1' });

const releaseParsed = parseArgs([
  '--alert-record-query-url=https://video.release.example/video/record/availability',
  '--record-coverage-query-url=https://video.release.example/video/record/coverage',
  '--record-base-url=https://video.release.example/video/record',
  '--record-export-url=https://video.release.example/video/record/export',
  '--device-id=device-01',
  '--camera-id=camera-01',
  '--alert-time=2026-07-05 10:00:00',
  '--time-range=120',
  '--source-alert-id=alert-001',
  '--record-drift-retention-hours=24',
  '--manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs',
], { YFEIEYE_VIDEO_SMOKE_TOKEN: 'token-1' });
assert.equal(parsed.alertRecordQueryUrl, 'http://video.local/video/record/availability');
assert.equal(parsed.token, 'token-1');
assert.equal(parsed.recordCoverageQueryUrl, 'http://video.local/video/record/availability');
assert.equal(parsed.recordBaseUrl, 'http://video.local/video/record');
assert.equal(parsed.recordExportUrl, 'http://video.local/video/record/export');
assert.equal(parsed.deviceId, 'device-01');
assert.equal(parsed.cameraId, 'camera-01');
assert.equal(parsed.alertTime, '2026-07-05 10:00:00');
assert.equal(parsed.timeRangeSeconds, 120);
assert.equal(parsed.sourceAlertId, 'alert-001');
assert.equal(parsed.recordDriftRetentionHours, 24);
assert.equal(parsed.allowLocalEndpoints, true);
assert.equal(
  resolveDownloadUrl(
    '/video/record/export/review-export-1/download',
    'https://video.release.example/yfeieye/dev-api/video/record/export',
  ),
  'https://video.release.example/yfeieye/dev-api/video/record/export/review-export-1/download',
);

const parsedWithVerifier = parseArgs([
  '--alert-record-query-url=http://video.local/video/record/availability',
  '--record-coverage-query-url=http://video.local/video/record/availability',
  '--record-base-url=http://video.local/video/record',
  '--record-export-url=http://video.local/video/record/export',
  '--device-id=device-01',
  '--camera-id=camera-01',
  '--alert-time=2026-07-05 10:00:00',
  '--time-range=120',
  '--source-alert-id=alert-001',
  '--record-drift-retention-hours=24',
  '--manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs',
  '--allow-local-endpoints',
]);
assert.equal(parsedWithVerifier.manifestVerifierScript, '.scripts/record-export-manifest-verifier.mjs');

const fromEnv = parseArgs([], {
  YFEIEYE_VIDEO_SMOKE_TOKEN: 'env-token',
  YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL: 'http://env/video/record/availability',
  YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL: 'http://env/video/record/availability',
  YFEIEYE_VIDEO_RECORD_BASE_URL: 'http://env/video/record',
  YFEIEYE_VIDEO_RECORD_EXPORT_URL: 'http://env/video/record/export',
  YFEIEYE_VIDEO_SMOKE_DEVICE_ID: 'env-device',
  YFEIEYE_VIDEO_SMOKE_ALERT_TIME: '2026-07-05 11:00:00',
  YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS: '72',
  YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT: '.scripts/record-export-manifest-verifier.mjs',
  YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI: 'env-record.mp4?token=env-playback-secret#env-playback-fragment',
});
assert.equal(fromEnv.deviceId, 'env-device');
assert.equal(fromEnv.token, 'env-token');
assert.equal(fromEnv.cameraId, 'env-device');
assert.equal(fromEnv.timeRangeSeconds, 300);
assert.equal(fromEnv.recordDriftRetentionHours, 72);
assert.equal(fromEnv.manifestVerifierScript, '.scripts/record-export-manifest-verifier.mjs');
assert.equal(fromEnv.playbackMaterialUri, 'env-record.mp4?token=env-playback-secret#env-playback-fragment');
assert.equal(fromEnv.allowLocalEndpoints, false);

assert.deepEqual(requiredOptionErrors(parseArgs([], {})), [
  'missing --alert-record-query-url or YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL',
  'missing --record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL',
  'missing --record-base-url or YFEIEYE_VIDEO_RECORD_BASE_URL',
  'missing --record-export-url or YFEIEYE_VIDEO_RECORD_EXPORT_URL',
  'missing YFEIEYE_VIDEO_SMOKE_TOKEN',
  'missing --device-id or YFEIEYE_VIDEO_SMOKE_DEVICE_ID',
  'missing --alert-time or YFEIEYE_VIDEO_SMOKE_ALERT_TIME',
  'missing --record-drift-retention-hours or YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS',
  'missing --manifest-verifier-script or YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT',
]);

assert.deepEqual(requiredOptionErrors(parseArgs([
  '--alert-record-query-url=https://video.release.example/video/record/availability',
  '--record-base-url=https://video.release.example/video/record',
  '--record-export-url=https://video.release.example/video/record/export',
  '--device-id=device-01',
  '--alert-time=2026-07-05 10:00:00',
  '--record-drift-retention-hours=24',
], { YFEIEYE_VIDEO_SMOKE_TOKEN: 'token-1' })), [
  'missing --record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL',
  'missing --manifest-verifier-script or YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT',
]);

assert.deepEqual(requiredOptionErrors(parseArgs([
  '--alert-record-query-url=https://video.release.example/video/record/availability',
  '--record-coverage-query-url=https://video.release.example/video/record/availability',
  '--record-base-url=https://video.release.example/video/record',
  '--record-export-url=https://video.release.example/video/record/export',
  '--device-id=device-01',
  '--alert-time=2026-07-05 10:00:00',
  '--record-drift-retention-hours=24',
  '--manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs',
], { YFEIEYE_VIDEO_SMOKE_TOKEN: 'token-1' })), [
  'record coverage URL must not equal alert record query URL; configure dedicated --record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL',
]);

assert.deepEqual(requiredOptionErrors(parseArgs([
  '--alert-record-query-url=http://127.0.0.1:6000/video/record/availability',
  '--record-coverage-query-url=http://video.mock/video/record/availability',
  '--record-base-url=file:///tmp/video/record',
  '--record-export-url=http://localhost:6000/video/record/export',
  '--device-id=device-01',
  '--alert-time=2026-07-05 10:00:00',
  '--record-drift-retention-hours=24',
  '--manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs',
], { YFEIEYE_VIDEO_SMOKE_TOKEN: 'token-1' })), [
  'VIDEO live smoke endpoint --alert-record-query-url must not use a local/mock URL without --allow-local-endpoints',
  'VIDEO live smoke endpoint --record-coverage-query-url must not use a local/mock URL without --allow-local-endpoints',
  'VIDEO live smoke endpoint --record-base-url must not use a local/mock URL without --allow-local-endpoints',
  'VIDEO live smoke endpoint --record-export-url must not use a local/mock URL without --allow-local-endpoints',
]);

assert.deepEqual(requiredOptionErrors(parseArgs([
  '--alert-record-query-url=http://127.0.0.1:6000/video/record/availability',
  '--record-coverage-query-url=http://video.mock/video/record/availability',
  '--record-base-url=file:///tmp/video/record',
  '--record-export-url=http://localhost:6000/video/record/export',
  '--device-id=device-01',
  '--alert-time=2026-07-05 10:00:00',
  '--record-drift-retention-hours=24',
  '--allow-local-endpoints',
], {})), []);

const availabilityUrl = buildAvailabilityUrl('http://video.local/video/record/availability', parsed);
assert.match(availabilityUrl, /^http:\/\/video\.local\/video\/record\/availability\?/);
assert.match(availabilityUrl, /device_id=device-01/);
assert.match(availabilityUrl, /camera_id=camera-01/);
assert.match(availabilityUrl, /alert_time=2026-07-05\+10%3A00%3A00/);
assert.match(availabilityUrl, /time_range=120/);
assert.match(availabilityUrl, /alert_id=alert-001/);

const playable = selectPlayableSegment({
  data: {
    segments: [
      { status: 'missing', start_time: '2026-07-05T09:59:00', end_time: '2026-07-05T09:59:30' },
      {
        status: 'motion',
        start_time: '2026-07-05T10:00:00',
        end_time: '2026-07-05T10:01:00',
        segment_start_time: '2026-07-05T09:59:55',
        segment_end_time: '2026-07-05T10:01:05',
        record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
        space_id: 7,
        object_name: 'live/device-01/clip.mp4',
        exportable: true,
      },
    ],
  },
});
assert.equal(playable.recordUri, '/video/record/space/7/video/live/device-01/clip.mp4');
assert.equal(playable.startTime, '2026-07-05T10:00:00');

assert.equal(selectPlayableSegment({ data: { segments: [{ status: 'missing' }] } }), null);

assert.deepEqual(
  buildExportBody(parsed, playable),
  {
    review_case_id: 'live-smoke-case',
    review_item_id: 'live-smoke-item',
    device_id: 'device-01',
    camera_id: 'camera-01',
    source_alert_id: 'alert-001',
    start_time: '2026-07-05T10:00:00',
    end_time: '2026-07-05T10:01:00',
    record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
    space_id: 7,
    object_name: 'live/device-01/clip.mp4',
    segment_start_time: '2026-07-05T09:59:55',
    segment_end_time: '2026-07-05T10:01:05',
    record_segments: [{
      index: 0,
      record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
      space_id: 7,
      object_name: 'live/device-01/clip.mp4',
      segment_start_time: '2026-07-05T09:59:55',
      segment_end_time: '2026-07-05T10:01:05',
      clip_start_time: '2026-07-05T10:00:00',
      clip_end_time: '2026-07-05T10:01:00',
    }],
    format: 'mp4',
    async_worker: true,
  },
);

const calls = [];
const fakeFetch = async (url, init = {}) => {
  calls.push({ url: String(url), init });
  if (String(url).includes('/space/device/device-01')) {
    return jsonResponse({ code: 0, data: { id: 7, device_id: 'device-01' } });
  }
  if (String(url).includes('/space/7/videos/drift')) {
    const requestUrl = new URL(String(url));
    assert.equal(requestUrl.searchParams.get('device_id'), 'device-01');
    assert.equal(requestUrl.searchParams.get('retention_hours'), '24');
    return jsonResponse({
      code: 0,
      data: {
        space_id: 7,
        device_id: 'device-01',
        summary: {
          record_count: 3,
          issue_count: 0,
          issue_reasons: {},
          standard_reason_keys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
          healthy: true,
        },
      },
    });
  }
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
          storage: {
            storageType: 'object_storage',
            artifactRole: 'export_package',
            objectKey: 'review-export-1/content.bin',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'object_storage',
        storeRoot: 's3://evidence-exports',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
        artifactKeys: {
          exportPackage: 'review-export-1/content.bin',
        },
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  if (urlPathEndsWith(url, '/video/record/export/review-export-1')) {
    return jsonResponse({
      code: 0,
      data: {
        export_id: 'review-export-1',
        status: 'ready',
        download_url: '/downloads/review-export-1.mp4',
        manifest_url: '/manifests/review-export-1.json',
      },
    });
  }
  if (urlPathEndsWith(url, '/downloads/review-export-1.mp4')) {
    assert.equal(init.method, 'HEAD');
    return jsonResponse({}, 200, {
      'content-length': '1048576',
      'content-type': 'video/mp4',
    });
  }
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, '/video/record/space/7/video/live/device-01/clip.mp4');
    assert.equal(body.async_worker, true);
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
          exportable: true,
          retain_mode: 'motion',
          coverage_source: 'detection',
        },
      ],
    },
  });
};

const smoke = await runSmoke(parsed, { fetchImpl: fakeFetch });
assert.deepEqual(smoke.checkpoints, [
  'alert_record_query_ok',
  'record_coverage_query_ok',
  'record_base_space_resolved',
  'record_storage_drift_patrol_ok',
  'record_export_posted',
  'record_export_download_ready',
  'record_export_download_probed',
  'record_export_manifest_verified',
]);
assert.equal(smoke.exportResult.exportId, 'review-export-1');
assert.equal(smoke.exportResult.downloadUrl, '/downloads/review-export-1.mp4');
assert.equal(smoke.exportResult.manifestUrl, '/manifests/review-export-1.json');
assert.equal(smoke.storageDrift.summary.record_count, 3);
assert.equal(smoke.coverage.segment.retainMode, 'motion');
assert.equal(smoke.coverage.segment.coverageSource, 'detection');
assert.equal(calls.length, 8);
assert.equal(calls.every(({ init }) => new Headers(init.headers).get('authorization') === 'Bearer token-1'), true);
for (const pathname of [
  '/video/record/export/review-export-1',
  '/downloads/review-export-1.mp4',
  '/manifests/review-export-1.json',
]) {
  const scopedCall = calls.find(({ url }) => new URL(url).pathname === pathname);
  assert.equal(new URL(scopedCall.url).searchParams.get('camera_id'), 'camera-01');
}
const playbackOverrideUri = '/video/record/device-01/override.mp4?token=playback-override-secret#playback-override-fragment';
let exportedPlaybackMaterialUri;
const playbackOverrideFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    exportedPlaybackMaterialUri = body.record_uri;
    const originalRecordUri = '/video/record/space/7/video/live/device-01/clip.mp4';
    return fakeFetch(url, {
      ...init,
      body: JSON.stringify({
        ...body,
        record_uri: originalRecordUri,
        record_segments: Array.isArray(body.record_segments)
          ? body.record_segments.map(segment => ({ ...segment, record_uri: originalRecordUri }))
          : body.record_segments,
      }),
    });
  }
  return fakeFetch(url, init);
};
await runSmoke({
  ...parsed,
  playbackMaterialUri: playbackOverrideUri,
}, { fetchImpl: playbackOverrideFetch });
assert.equal(exportedPlaybackMaterialUri, playbackOverrideUri);
const cliSummary = summarizeCliResult(smoke);
const signedExportResult = {
  ...smoke.exportResult,
  downloadUrl: 'record.mp4?token=download-secret#download-fragment',
  manifestUrl: '/manifests/review-export-1.json?token=manifest-secret#manifest-fragment',
  message: 'exported record.mp4?token=message-secret#message-fragment',
  ruleEvidence: [{
    source: 'record.mp4?token=rule-secret#rule-fragment',
  }],
};
const signedExportCliSummary = summarizeCliResult({
  ...smoke,
  exportResult: signedExportResult,
  manifestVerification: {
    valid: true,
    violations: ['checked record.mp4?token=violation-secret#violation-fragment'],
  },
});
assert.deepEqual(signedExportCliSummary.exportResult, {
  ...signedExportResult,
  downloadUrl: 'record.mp4',
  manifestUrl: '/manifests/review-export-1.json',
  message: 'exported record.mp4',
  ruleEvidence: [{ source: 'record.mp4' }],
});
assert.deepEqual(signedExportCliSummary.manifestVerification, {
  valid: true,
  violations: ['checked record.mp4'],
});
assert.match(signedExportResult.downloadUrl, /download-secret/);
assert.match(signedExportResult.manifestUrl, /manifest-secret/);
const signedCliFailure = spawnSync(process.execPath, [
  '.scripts/alert-review-video-live-smoke.mjs',
  '--bogus={"url":"record.mp4?token=video-failure-secret#video-failure-fragment"}',
], { encoding: 'utf8' });
assert.equal(signedCliFailure.status, 1);
assert.equal(signedCliFailure.stderr.includes('video-failure-secret'), false);
assert.match(signedCliFailure.stderr, /--bogus=\{"url":"record\.mp4"\}/);
const videoHelp = spawnSync(process.execPath, [
  '.scripts/alert-review-video-live-smoke.mjs',
  '--help',
], { encoding: 'utf8' });
assert.equal(videoHelp.status, 0);
assert.match(videoHelp.stdout, /YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI/);
assert.doesNotMatch(videoHelp.stdout, /--playback-material-uri=/);
const playbackMaterialArgvFailure = spawnSync(process.execPath, [
  '.scripts/alert-review-video-live-smoke.mjs',
  '--playback-material-uri=record.mp4?token=video-playback-argv-secret#fragment',
], { encoding: 'utf8' });
assert.equal(playbackMaterialArgvFailure.status, 1);
assert.equal(playbackMaterialArgvFailure.stderr.includes('video-playback-argv-secret'), false);
assert.match(playbackMaterialArgvFailure.stderr, /YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI/);
assert.deepEqual(cliSummary.coverageSummary, {
  status: 'available',
  retainMode: 'motion',
  coverageSource: 'detection',
});
assert.deepEqual(cliSummary.storageDriftSummary, {
  healthy: true,
  recordCount: 3,
  issueCount: 0,
  issueReasons: {},
  standardReasonKeys: STANDARD_STORAGE_DRIFT_REASON_KEYS,
});
assert.deepEqual(cliSummary.manifestSignature, {
  algorithm: 'hmac-sha256',
  keyId: '2026-q2',
  signatureVersion: 'v2',
});
assert.deepEqual(cliSummary.manifestStorageLifecycle, {
  storageType: 'object_storage',
  status: 'retained',
  expiresAt: '2026-07-20T00:00:00Z',
  exportPackageObjectKey: 'review-export-1/content.bin',
});

const aliasCoverageClassificationFetch = async (url, init = {}) => {
  const requestUrl = String(url);
  if (requestUrl.includes('/video/record/availability') && requestUrl.includes('begin_time=')) {
    return jsonResponse({
      code: 0,
      data: {
        segments: [
          {
            status: 'available',
            start_time: '2026-07-05T10:00:00',
            end_time: '2026-07-05T10:01:00',
            record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
            exportable: true,
            retain_mode: 'Recording',
            coverage_source: 'all',
          },
        ],
      },
    });
  }
  return fakeFetch(url, init);
};
const aliasSmoke = await runSmoke(parsed, { fetchImpl: aliasCoverageClassificationFetch });
assert.equal(aliasSmoke.coverage.segment.retainMode, 'continuous');
assert.equal(aliasSmoke.coverage.segment.coverageSource, 'continuous');
assert.deepEqual(summarizeCliResult(aliasSmoke).coverageSummary, {
  status: 'available',
  retainMode: 'continuous',
  coverageSource: 'continuous',
});

const nonExportableCoverageFetch = async (url, init = {}) => {
  const requestUrl = String(url);
  if (requestUrl.includes('/video/record/availability') && requestUrl.includes('begin_time=')) {
    return jsonResponse({
      code: 0,
      data: {
        segments: [
          {
            status: 'available',
            start_time: '2026-07-05T10:00:00',
            end_time: '2026-07-05T10:01:00',
            record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
            exportable: false,
            non_exportable_reason: 'retention_expired',
          },
        ],
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: nonExportableCoverageFetch }),
  /record coverage query returned no playable\/exportable record segment.*retention_expired/s,
);

const missingCoverageClassificationFetch = async (url, init = {}) => {
  const requestUrl = String(url);
  if (requestUrl.includes('/video/record/availability') && requestUrl.includes('begin_time=')) {
    return jsonResponse({
      code: 0,
      data: {
        segments: [
          {
            status: 'available',
            start_time: '2026-07-05T10:00:00',
            end_time: '2026-07-05T10:01:00',
            record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
            exportable: true,
          },
        ],
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: missingCoverageClassificationFetch }),
  /record coverage query missing retain mode or source classification/,
);

const invalidCoverageClassificationFetch = async (url, init = {}) => {
  const requestUrl = String(url);
  if (requestUrl.includes('/video/record/availability') && requestUrl.includes('begin_time=')) {
    return jsonResponse({
      code: 0,
      data: {
        segments: [
          {
            status: 'available',
            start_time: '2026-07-05T10:00:00',
            end_time: '2026-07-05T10:01:00',
            record_uri: '/video/record/space/7/video/live/device-01/clip.mp4',
            exportable: true,
            retain_mode: 'temporary',
            coverage_source: 'custom_ai',
          },
        ],
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: invalidCoverageClassificationFetch }),
  /record coverage query returned non-standard retain mode or source classification.*temporary.*custom_ai/s,
);

const missingDriftReasonCatalogFetch = async (url, init = {}) => {
  if (String(url).includes('/space/7/videos/drift')) {
    return jsonResponse({
      code: 0,
      data: {
        space_id: 7,
        device_id: 'device-01',
        summary: {
          record_count: 3,
          issue_count: 0,
          issue_reasons: {},
          healthy: true,
        },
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: missingDriftReasonCatalogFetch }),
  /record storage drift patrol missing standard reason evidence: file_missing/,
);

const mockRecordUriFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, 'mock://record/device-01/clip.mp4');
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  if (String(url).includes('/space/device/device-01')
    || String(url).includes('/space/7/videos/drift')
    || urlPathEndsWith(url, '/video/record/export/review-export-1')
    || urlPathEndsWith(url, '/downloads/review-export-1.mp4')
    || urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return fakeFetch(url, init);
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          record_uri: 'mock://record/device-01/clip.mp4',
          exportable: true,
        },
      ],
    },
  });
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: mockRecordUriFetch }),
  /VIDEO live smoke returned local\/mock record URI/,
);

const relativeMockRecordUriFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, 'mock/device-01/clip.mp4');
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  if (String(url).includes('/space/device/device-01')
    || String(url).includes('/space/7/videos/drift')
    || urlPathEndsWith(url, '/video/record/export/review-export-1')
    || urlPathEndsWith(url, '/downloads/review-export-1.mp4')
    || urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return fakeFetch(url, init);
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          record_uri: 'mock/device-01/clip.mp4',
          exportable: true,
        },
      ],
    },
  });
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: relativeMockRecordUriFetch }),
  /VIDEO live smoke returned local\/mock record URI/,
);

const inlineRecordUriFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, 'data:video/mp4;base64,AAAA');
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  if (String(url).includes('/space/device/device-01')
    || String(url).includes('/space/7/videos/drift')
    || urlPathEndsWith(url, '/video/record/export/review-export-1')
    || urlPathEndsWith(url, '/downloads/review-export-1.mp4')
    || urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return fakeFetch(url, init);
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          record_uri: 'data:video/mp4;base64,AAAA',
          exportable: true,
        },
      ],
    },
  });
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: inlineRecordUriFetch }),
  /VIDEO live smoke returned inline\/opaque media evidence/,
);

const protocolRelativeLocalRecordUriFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, '//localhost/video/device-01/clip.mp4');
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  if (String(url).includes('/space/device/device-01')
    || String(url).includes('/space/7/videos/drift')
    || urlPathEndsWith(url, '/video/record/export/review-export-1')
    || urlPathEndsWith(url, '/downloads/review-export-1.mp4')
    || urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return fakeFetch(url, init);
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          record_uri: '//localhost/video/device-01/clip.mp4',
          exportable: true,
        },
      ],
    },
  });
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: protocolRelativeLocalRecordUriFetch }),
  /VIDEO live smoke returned local\/mock record URI/,
);

const localFilePathFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, '/var/lib/yfeieye/video/device-01/clip.mp4');
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  if (String(url).includes('/space/device/device-01')
    || String(url).includes('/space/7/videos/drift')
    || urlPathEndsWith(url, '/video/record/export/review-export-1')
    || urlPathEndsWith(url, '/downloads/review-export-1.mp4')
    || urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return fakeFetch(url, init);
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          file_path: '/var/lib/yfeieye/video/device-01/clip.mp4',
          exportable: true,
        },
      ],
    },
  });
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: localFilePathFetch }),
  /VIDEO live smoke returned local file path evidence/,
);

const absoluteRecordUriFetch = async (url, init = {}) => {
  if (init.method === 'POST') {
    const body = JSON.parse(init.body);
    assert.equal(body.record_uri, 'C:\\yfeieye\\video\\device-01\\clip.mp4');
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'pending' } });
  }
  if (String(url).includes('/space/device/device-01')
    || String(url).includes('/space/7/videos/drift')
    || urlPathEndsWith(url, '/video/record/export/review-export-1')
    || urlPathEndsWith(url, '/downloads/review-export-1.mp4')
    || urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return fakeFetch(url, init);
  }
  return jsonResponse({
    code: 0,
    data: {
      segments: [
        {
          status: 'available',
          start_time: '2026-07-05T10:00:00',
          end_time: '2026-07-05T10:01:00',
          record_uri: 'C:\\yfeieye\\video\\device-01\\clip.mp4',
          exportable: true,
        },
      ],
    },
  });
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: absoluteRecordUriFetch }),
  /VIDEO live smoke returned local file path evidence/,
);

const mockDownloadUrlFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/video/record/export/review-export-1')) {
    return jsonResponse({
      code: 0,
      data: {
        export_id: 'review-export-1',
        status: 'ready',
        download_url: 'mock://download/review-export-1.mp4',
        manifest_url: '/manifests/review-export-1.json',
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: mockDownloadUrlFetch }),
  /VIDEO live smoke returned local\/mock download URL/,
);

const localManifestUrlFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/video/record/export/review-export-1')) {
    return jsonResponse({
      code: 0,
      data: {
        export_id: 'review-export-1',
        status: 'ready',
        download_url: '/downloads/review-export-1.mp4',
        manifest_url: 'http://localhost/manifests/review-export-1.json',
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: localManifestUrlFetch }),
  /VIDEO live smoke returned local\/mock manifest URL/,
);

const verifierCalls = [];
const smokeWithVerifier = await runSmoke(parsedWithVerifier, {
  fetchImpl: fakeFetch,
  verifyManifest: async ({ manifest, manifestUrl }) => {
    verifierCalls.push({ manifest, manifestUrl });
    return {
      valid: true,
      signatureValid: true,
      signatureKeyAvailable: true,
      keyId: '2026-q2',
      signatureVersion: 'v2',
      violations: [],
    };
  },
});
assert.equal(verifierCalls.length, 1);
assert.equal(verifierCalls[0].manifest.signature.keyId, '2026-q2');
assert.equal(verifierCalls[0].manifestUrl, 'http://video.local/manifests/review-export-1.json?camera_id=camera-01');
assert.deepEqual(smokeWithVerifier.manifestVerification, {
  valid: true,
  signatureValid: true,
  signatureKeyAvailable: true,
  keyId: '2026-q2',
  signatureVersion: 'v2',
  violations: [],
});
assert.deepEqual(summarizeCliResult(smokeWithVerifier).manifestVerification, smokeWithVerifier.manifestVerification);

await assert.rejects(
  () => runSmoke(parsedWithVerifier, {
    fetchImpl: fakeFetch,
    verifyManifest: async () => ({
      valid: false,
      signatureValid: false,
      signatureKeyAvailable: false,
      keyId: '2026-q2',
      signatureVersion: 'v2',
      violations: ['missing_hmac_key'],
    }),
  }),
  /record export manifest verifier failed: missing_hmac_key/,
);

const lexicalManifestVerifierTempDir = mkdtempSync(
  join(tmpdir(), 'yfeieye-live-video-lexical-manifest-verifier-'),
);
const lexicalManifestVerifierPath = join(
  lexicalManifestVerifierTempDir,
  'lexical-manifest-verifier.mjs',
);
writeFileSync(lexicalManifestVerifierPath, `
import { readFileSync } from 'node:fs';
for (const key of [
  'YFEIEYE_DEVICE_AUTH_TOKEN',
  'YFEIEYE_VIDEO_SMOKE_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'THIRD_PARTY_API_KEY',
]) {
  if (process.env[key]) {
    console.error('manifest verifier inherited unrelated sensitive environment');
    process.exit(7);
  }
}
if (process.env.YFEIEYE_RECORD_EXPORT_HMAC_SECRET !== 'manifest-allowed-secret') {
  console.error('manifest verifier did not receive its required HMAC environment');
  process.exit(6);
}
const manifestIndex = process.argv.indexOf('--manifest');
const rawManifest = readFileSync(process.argv[manifestIndex + 1], 'utf8');
if (JSON.parse(rawManifest).manifestVersion !== 2) {
  console.error('manifest response wrapper was not removed');
  process.exit(9);
}
if (!rawManifest.includes('"durationSeconds":30.0')) {
  console.error('durationSeconds=30.0 lexical form was not preserved');
  process.exit(8);
}
console.log(JSON.stringify({
  valid: true,
  signatureValid: true,
  signatureKeyAvailable: true,
  keyId: '2026-q2',
  signatureVersion: 'v2',
  violations: [],
}));
`, 'utf8');
const lexicalManifestFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    const manifestResponse = await fakeFetch(url, init);
    const manifestText = (await manifestResponse.text()).replace(
      /}$/,
      ',"mediaProbe":{"durationSeconds":30.0}}',
    );
    return rawJsonResponse(`{"code":0,"data":${manifestText},"msg":"success"}`);
  }
  return fakeFetch(url, init);
};
const manifestVerifierParentEnv = {
  SAFE_PROBE_VALUE: 'safe-value',
  YFEIEYE_DEVICE_AUTH_TOKEN: 'device-secret',
  YFEIEYE_VIDEO_SMOKE_TOKEN: 'video-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN: 'player-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: 'session=cookie-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: 'session=storage-secret',
  YFEIEYE_REVIEW_PLAYER_SMOKE_URL: 'https://example.test/review?token=url-secret',
  THIRD_PARTY_API_KEY: 'api-key-secret',
  YFEIEYE_RECORD_EXPORT_HMAC_SECRET: 'manifest-allowed-secret',
};
const manifestVerifierChildEnv = buildManifestVerifierEnvironment(manifestVerifierParentEnv);
assert.equal(manifestVerifierChildEnv.SAFE_PROBE_VALUE, 'safe-value');
assert.equal(manifestVerifierChildEnv.YFEIEYE_RECORD_EXPORT_HMAC_SECRET, 'manifest-allowed-secret');
for (const key of Object.keys(manifestVerifierParentEnv).filter(key => ![
  'SAFE_PROBE_VALUE',
  'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
].includes(key))) {
  assert.equal(Object.hasOwn(manifestVerifierChildEnv, key), false, `manifest verifier child env leaked ${key}`);
}
const manifestVerifierEnvKeys = Object.keys(manifestVerifierParentEnv).filter(key => key !== 'SAFE_PROBE_VALUE');
const previousManifestVerifierEnv = new Map(
  manifestVerifierEnvKeys.map(key => [key, Object.hasOwn(process.env, key) ? process.env[key] : undefined]),
);
try {
  Object.assign(process.env, manifestVerifierParentEnv);
  const lexicalManifestSmoke = await runSmoke({
    ...parsedWithVerifier,
    manifestVerifierScript: lexicalManifestVerifierPath,
  }, { fetchImpl: lexicalManifestFetch });
  assert.equal(lexicalManifestSmoke.manifestVerification.valid, true);
} finally {
  for (const [key, value] of previousManifestVerifierEnv) {
    if (value === undefined) {
      delete process.env[key];
    } else {
      process.env[key] = value;
    }
  }
  rmSync(lexicalManifestVerifierTempDir, { recursive: true, force: true });
}

const wrapperProbeDir = mkdtempSync(join(tmpdir(), 'yfeieye-manifest-wrapper-env-probe-'));
try {
  const wrapperProbeOutput = join(wrapperProbeDir, 'env.json');
  const wrapperProbeScript = join(wrapperProbeDir, 'python-probe.cjs');
  writeFileSync(wrapperProbeScript, `
const { writeFileSync } = require('node:fs');
const forbiddenKeys = [
  'YFEIEYE_DEVICE_AUTH_TOKEN',
  'YFEIEYE_VIDEO_SMOKE_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'THIRD_PARTY_API_KEY',
];
writeFileSync(process.env.ENV_PROBE_OUTPUT, JSON.stringify({
  forbidden: Object.fromEntries(forbiddenKeys.map(key => [key, process.env[key] ?? null])),
  hmacSecret: process.env.YFEIEYE_RECORD_EXPORT_HMAC_SECRET ?? null,
  safeValue: process.env.SAFE_PROBE_VALUE ?? null,
}));
process.exit(0);
`, 'utf8');
  const wrapperLauncher = join(wrapperProbeDir, 'wrapper-launcher.mjs');
  writeFileSync(wrapperLauncher, `
process.env.PYTHON = process.execPath;
process.env.NODE_OPTIONS = '--require=' + ${JSON.stringify(JSON.stringify(wrapperProbeScript))};
await import(${JSON.stringify(new URL('./record-export-manifest-verifier.mjs', import.meta.url).href)});
`, 'utf8');
  const wrapperProbe = spawnSync(process.execPath, [
    wrapperLauncher,
    '--manifest',
    join(wrapperProbeDir, 'unused-manifest.json'),
  ], {
    encoding: 'utf8',
    env: {
      ...process.env,
      ENV_PROBE_OUTPUT: wrapperProbeOutput,
      SAFE_PROBE_VALUE: 'safe-value',
      YFEIEYE_DEVICE_AUTH_TOKEN: 'device-secret',
      YFEIEYE_VIDEO_SMOKE_TOKEN: 'video-secret',
      YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN: 'player-secret',
      YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: 'session=cookie-secret',
      YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: 'session=storage-secret',
      YFEIEYE_REVIEW_PLAYER_SMOKE_URL: 'https://example.test/review?token=url-secret',
      THIRD_PARTY_API_KEY: 'api-key-secret',
      YFEIEYE_RECORD_EXPORT_HMAC_SECRET: 'manifest-allowed-secret',
    },
  });
  assert.equal(wrapperProbe.status, 0, wrapperProbe.stderr);
  const wrapperPythonEnv = JSON.parse(readFileSync(wrapperProbeOutput, 'utf8'));
  assert.deepEqual(wrapperPythonEnv.forbidden, {
    YFEIEYE_DEVICE_AUTH_TOKEN: null,
    YFEIEYE_VIDEO_SMOKE_TOKEN: null,
    YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN: null,
    YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES: null,
    YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE: null,
    YFEIEYE_REVIEW_PLAYER_SMOKE_URL: null,
    THIRD_PARTY_API_KEY: null,
  });
  assert.equal(wrapperPythonEnv.hmacSecret, 'manifest-allowed-secret');
  assert.equal(wrapperPythonEnv.safeValue, 'safe-value');
} finally {
  rmSync(wrapperProbeDir, { recursive: true, force: true });
}

const duplicateTopLevelDataManifestFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    const manifestResponse = await fakeFetch(url, init);
    const manifestText = await manifestResponse.text();
    return rawJsonResponse(
      `{"code":0,"data":${manifestText},"data":${manifestText},"msg":"success"}`,
    );
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsedWithVerifier, {
    fetchImpl: duplicateTopLevelDataManifestFetch,
    verifyManifest: async () => ({
      valid: true,
      signatureValid: true,
      signatureKeyAvailable: true,
      keyId: '2026-q2',
      signatureVersion: 'v2',
      violations: [],
    }),
  }),
  /duplicate top-level data/,
);

const verifierTempDir = mkdtempSync(join(tmpdir(), 'yfeieye-live-video-verifier-'));
const falseSuccessVerifierPath = join(verifierTempDir, 'false-success-verifier.mjs');
writeFileSync(falseSuccessVerifierPath, 'console.log(JSON.stringify({ valid: true })); process.exit(7);\n', 'utf8');
try {
  await assert.rejects(
    () => runSmoke({
      ...parsedWithVerifier,
      manifestVerifierScript: falseSuccessVerifierPath,
    }, { fetchImpl: fakeFetch }),
    /record export manifest verifier failed with exit 7/,
  );
} finally {
  rmSync(verifierTempDir, { recursive: true, force: true });
}

const slowVerifierTempDir = mkdtempSync(join(tmpdir(), 'yfeieye-live-video-slow-verifier-'));
const slowVerifierPath = join(slowVerifierTempDir, 'slow-verifier.mjs');
writeFileSync(slowVerifierPath, 'setTimeout(() => console.log(JSON.stringify({ valid: true })), 1000);\n', 'utf8');
try {
  await assert.rejects(
    () => runSmoke({
      ...parsedWithVerifier,
      timeoutMs: 50,
      manifestVerifierScript: slowVerifierPath,
    }, { fetchImpl: fakeFetch }),
    /record export manifest verifier timed out after 50ms/,
  );
} finally {
  rmSync(slowVerifierTempDir, { recursive: true, force: true });
}

const failedDriftFetch = async (url, init = {}) => {
  if (String(url).includes('/space/7/videos/drift')) {
    return jsonResponse({
      code: 0,
      data: {
        summary: {
          record_count: 3,
          issue_count: 2,
          issue_reasons: {
            file_missing: 1,
            disk_full: 1,
          },
          healthy: false,
        },
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: failedDriftFetch }),
  /record storage drift patrol reported 2 issue\(s\): file_missing=1, disk_full=1/,
);

const failedDownloadFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/downloads/review-export-1.mp4')) {
    assert.equal(init.method, 'HEAD');
    return jsonResponse({ message: 'missing export file' }, 404);
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: failedDownloadFetch }),
  /record export download probe failed with HTTP 404/,
);

const nonVideoDownloadFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/downloads/review-export-1.mp4')) {
    assert.equal(init.method, 'HEAD');
    return jsonResponse({ message: 'not a video export' }, 200, {
      'content-length': '64',
      'content-type': 'application/json',
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: nonVideoDownloadFetch }),
  /record export download probe returned non-video content-type: application\/json/,
);

const invalidManifestHashFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: 'sha256:source-segment',
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: 'sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
          storage: {
            storageType: 'object_storage',
            artifactRole: 'export_package',
            objectKey: 'review-export-1/content.bin',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'object_storage',
        storeRoot: 's3://evidence-exports',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
        artifactKeys: {
          exportPackage: 'review-export-1/content.bin',
        },
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: invalidManifestHashFetch }),
  /record export manifest invalid source segment hash: sha256:source-segment/,
);

const invalidOutputHashFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: 'output-file',
          storage: {
            storageType: 'object_storage',
            artifactRole: 'export_package',
            objectKey: 'review-export-1/content.bin',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'object_storage',
        storeRoot: 's3://evidence-exports',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
        artifactKeys: {
          exportPackage: 'review-export-1/content.bin',
        },
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: invalidOutputHashFetch }),
  /record export manifest invalid output file hash: output-file/,
);

const invalidFfmpegCommandHashFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: 'sha256:ffmpeg-command',
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
          storage: {
            storageType: 'object_storage',
            artifactRole: 'export_package',
            objectKey: 'review-export-1/content.bin',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'object_storage',
        storeRoot: 's3://evidence-exports',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
        artifactKeys: {
          exportPackage: 'review-export-1/content.bin',
        },
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: invalidFfmpegCommandHashFetch }),
  /record export manifest invalid ffmpeg command hash: sha256:ffmpeg-command/,
);

const invalidClipWindowFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:01:00',
          clipEndTime: '2026-07-05T10:00:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
          storage: {
            storageType: 'object_storage',
            artifactRole: 'export_package',
            objectKey: 'review-export-1/content.bin',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'object_storage',
        storeRoot: 's3://evidence-exports',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
        artifactKeys: {
          exportPackage: 'review-export-1/content.bin',
        },
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: invalidClipWindowFetch }),
  /record export manifest invalid clip window: 2026-07-05T10:01:00 -> 2026-07-05T10:00:00/,
);

const duplicateConcatOrderFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip-a.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:00:30',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip-b.mp4',
          sourceHash: 'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
          clipStartTime: '2026-07-05T10:00:30',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
          storage: {
            storageType: 'object_storage',
            artifactRole: 'export_package',
            objectKey: 'review-export-1/content.bin',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'object_storage',
        storeRoot: 's3://evidence-exports',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
        artifactKeys: {
          exportPackage: 'review-export-1/content.bin',
        },
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: duplicateConcatOrderFetch }),
  /record export manifest duplicate concat order index: 0/,
);

function rootConcatOrderManifest(concatOrder) {
  return {
    manifestVersion: 2,
    concatOrder,
    recordSegments: [
      {
        index: 0,
        recordUri: '/video/record/space/7/video/live/device-01/clip-a.mp4',
        sourceHash: SOURCE_SEGMENT_HASH,
        clipStartTime: '2026-07-05T10:00:00',
        clipEndTime: '2026-07-05T10:00:30',
        ffmpegCommandHash: FFMPEG_COMMAND_HASH,
      },
      {
        index: 1,
        recordUri: '/video/record/space/7/video/live/device-01/clip-b.mp4',
        sourceHash: 'sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
        clipStartTime: '2026-07-05T10:00:30',
        clipEndTime: '2026-07-05T10:01:00',
        ffmpegCommandHash: FFMPEG_COMMAND_HASH,
      },
    ],
    files: [
      {
        path: 'review-export-1.mp4',
        role: 'export_package',
        hash: OUTPUT_FILE_HASH,
        storage: {
          storageType: 'object_storage',
          artifactRole: 'export_package',
          objectKey: 'review-export-1/content.bin',
          expiresAt: '2026-07-20T00:00:00Z',
          lifecycleStatus: 'retained',
        },
      },
    ],
    storageLifecycle: {
      storageType: 'object_storage',
      storeRoot: 's3://evidence-exports',
      status: 'retained',
      expiresAt: '2026-07-20T00:00:00Z',
      artifactKeys: {
        exportPackage: 'review-export-1/content.bin',
      },
    },
    signature: {
      algorithm: 'hmac-sha256',
      keyId: '2026-q2',
      signatureVersion: 'v2',
      value: MANIFEST_SIGNATURE_VALUE,
    },
  };
}

const duplicateRootConcatOrderFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse(rootConcatOrderManifest([0, 0]));
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: duplicateRootConcatOrderFetch }),
  /record export manifest duplicate concat order index: 0/,
);

const missingRootConcatOrderReferenceFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse(rootConcatOrderManifest([0, 2]));
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: missingRootConcatOrderReferenceFetch }),
  /record export manifest concat order references missing segment index: 2/,
);

const omittedRootConcatOrderSegmentFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse(rootConcatOrderManifest([0]));
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: omittedRootConcatOrderSegmentFetch }),
  /record export manifest concat order omits segment index: 1/,
);

const missingManifestFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/video/record/export/review-export-1')) {
    return jsonResponse({ code: 0, data: { export_id: 'review-export-1', status: 'ready', download_url: '/downloads/review-export-1.mp4' } });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: missingManifestFetch }),
  /record export response did not include manifest_url/,
);

const missingStorageLifecycleFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
        },
      ],
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: missingStorageLifecycleFetch }),
  /record export manifest missing storage lifecycle/,
);

const localStorageReferenceFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
          storage: {
            storageType: 'local_disk',
            artifactRole: 'export_package',
            objectKey: 'file:///tmp/review-export-1.mp4',
            expiresAt: '2026-07-20T00:00:00Z',
            lifecycleStatus: 'retained',
          },
        },
      ],
      storageLifecycle: {
        storageType: 'local_disk',
        status: 'retained',
        expiresAt: '2026-07-20T00:00:00Z',
      },
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(releaseParsed, { fetchImpl: localStorageReferenceFetch }),
  /VIDEO live smoke returned local\/mock export package storage reference/,
);

const unsignedManifestFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      recordSegments: [
        {
          index: 0,
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
          clipStartTime: '2026-07-05T10:00:00',
          clipEndTime: '2026-07-05T10:01:00',
          ffmpegCommandHash: FFMPEG_COMMAND_HASH,
        },
      ],
      files: [
        {
          path: 'review-export-1.mp4',
          role: 'export_package',
          hash: OUTPUT_FILE_HASH,
        },
      ],
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: unsignedManifestFetch }),
  /record export manifest missing HMAC signature metadata/,
);

const malformedSignatureValueFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      ...rootConcatOrderManifest([0, 1]),
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: 'hmac-sha256:not-a-real-signature',
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: malformedSignatureValueFetch }),
  /record export manifest signature value is not canonical hmac-sha256/,
);

const incompleteManifestFetch = async (url, init = {}) => {
  if (urlPathEndsWith(url, '/manifests/review-export-1.json')) {
    return jsonResponse({
      manifestVersion: 2,
      ffmpegCommandHash: FFMPEG_COMMAND_HASH,
      sourceSegments: [
        {
          recordUri: '/video/record/space/7/video/live/device-01/clip.mp4',
          sourceHash: SOURCE_SEGMENT_HASH,
        },
      ],
      outputs: [
        {
          path: 'review-export-1.mp4',
          sha256: OUTPUT_FILE_HASH,
        },
      ],
      signature: {
        algorithm: 'hmac-sha256',
        keyId: '2026-q2',
        signatureVersion: 'v2',
        value: MANIFEST_SIGNATURE_VALUE,
      },
    });
  }
  return fakeFetch(url, init);
};
await assert.rejects(
  () => runSmoke(parsed, { fetchImpl: incompleteManifestFetch }),
  /record export manifest missing clip params/,
);

await assert.rejects(
  () => runSmoke(parseArgs([], {}), { fetchImpl: fakeFetch }),
  /missing --alert-record-query-url/,
);

const untrackedLiveSmoke = evaluateStatus(`
?? .scripts/alert-review-video-live-smoke.mjs
?? .scripts/alert-review-video-live-smoke.test.mjs
`);
assert.equal(untrackedLiveSmoke.ok, false);
assert.equal(untrackedLiveSmoke.blockers[0].group, 'FR release gate tooling');
assert.equal(untrackedLiveSmoke.blockers[1].group, 'FR release gate tooling');

const trackedSmokeEntries = releaseEntriesForTrackedPaths([
  '.scripts/alert-review-video-live-smoke.mjs',
  '.scripts/alert-review-video-live-smoke.test.mjs',
]);
assert.equal(trackedSmokeEntries.length, 2);

function jsonResponse(body, status = 200, headers = {}) {
  const headerMap = new Map(Object.entries(headers).map(([key, value]) => [key.toLowerCase(), String(value)]));
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 200 ? 'OK' : 'ERROR',
    headers: {
      get(name) {
        return headerMap.get(String(name).toLowerCase()) || null;
      },
    },
    async json() {
      return body;
    },
    async text() {
      return JSON.stringify(body);
    },
  };
}

function rawJsonResponse(rawBody, status = 200, headers = {}) {
  const headerMap = new Map(Object.entries(headers).map(([key, value]) => [key.toLowerCase(), String(value)]));
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 200 ? 'OK' : 'ERROR',
    headers: {
      get(name) {
        return headerMap.get(String(name).toLowerCase()) || null;
      },
    },
    async json() {
      return JSON.parse(rawBody);
    },
    async text() {
      return rawBody;
    },
  };
}

console.log('alert review VIDEO live smoke tests OK');
