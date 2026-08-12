import { spawnSync } from 'node:child_process';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_TIME_RANGE_SECONDS = 300;
const DEFAULT_TIMEOUT_MS = 10_000;
const DEFAULT_EXPORT_POLL_ATTEMPTS = 5;
const DEFAULT_EXPORT_POLL_INTERVAL_MS = 1_000;
const REQUIRED_STORAGE_DRIFT_REASON_KEYS = [
  'file_missing',
  'retention_expired',
  'disk_full',
  'cache_flush_failed',
];
const STANDARD_COVERAGE_CLASSIFICATIONS = ['continuous', 'motion', 'alert', 'detection'];
const MANIFEST_VERIFIER_ALLOWED_SENSITIVE_ENV_KEYS = new Set([
  'YFEIEYE_RECORD_EXPORT_HMAC_SECRET',
  'YFEIEYE_RECORD_EXPORT_HMAC_KEYS',
  'YFEIEYE_RECORD_EXPORT_KEY_ID',
]);
const MANIFEST_VERIFIER_UNRELATED_SENSITIVE_ENV_KEYS = new Set([
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
  'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
]);

export function parseArgs(args, env = process.env) {
  const parsed = {
    token: env.YFEIEYE_VIDEO_SMOKE_TOKEN || '',
    alertRecordQueryUrl: env.YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL || '',
    recordCoverageQueryUrl: env.YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL || '',
    recordBaseUrl: env.YFEIEYE_VIDEO_RECORD_BASE_URL || '',
    recordExportUrl: env.YFEIEYE_VIDEO_RECORD_EXPORT_URL || '',
    playbackMaterialUri: env.YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI || '',
    deviceId: env.YFEIEYE_VIDEO_SMOKE_DEVICE_ID || '',
    cameraId: env.YFEIEYE_VIDEO_SMOKE_CAMERA_ID || '',
    alertTime: env.YFEIEYE_VIDEO_SMOKE_ALERT_TIME || '',
    timeRangeSeconds: Number(env.YFEIEYE_VIDEO_SMOKE_TIME_RANGE || DEFAULT_TIME_RANGE_SECONDS),
    sourceAlertId: env.YFEIEYE_VIDEO_SMOKE_ALERT_ID || 'live-smoke-alert',
    reviewCaseId: env.YFEIEYE_VIDEO_SMOKE_REVIEW_CASE_ID || 'live-smoke-case',
    reviewItemId: env.YFEIEYE_VIDEO_SMOKE_REVIEW_ITEM_ID || 'live-smoke-item',
    format: env.YFEIEYE_VIDEO_SMOKE_EXPORT_FORMAT || 'mp4',
    timeoutMs: Number(env.YFEIEYE_VIDEO_SMOKE_TIMEOUT_MS || DEFAULT_TIMEOUT_MS),
    exportPollAttempts: Number(env.YFEIEYE_VIDEO_SMOKE_EXPORT_POLL_ATTEMPTS || DEFAULT_EXPORT_POLL_ATTEMPTS),
    exportPollIntervalMs: Number(env.YFEIEYE_VIDEO_SMOKE_EXPORT_POLL_INTERVAL_MS || DEFAULT_EXPORT_POLL_INTERVAL_MS),
    recordDriftRetentionHours: Number(env.YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS),
    manifestVerifierScript: env.YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT || '',
    allowLocalEndpoints: parseBoolean(env.YFEIEYE_VIDEO_SMOKE_ALLOW_LOCAL_ENDPOINTS, false),
    help: false,
  };

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg === '--allow-local-endpoints') {
      parsed.allowLocalEndpoints = true;
    } else if (arg.startsWith('--token=')) {
      throw new Error('VIDEO smoke token must be provided through YFEIEYE_VIDEO_SMOKE_TOKEN');
    } else if (arg.startsWith('--alert-record-query-url=')) {
      parsed.alertRecordQueryUrl = arg.slice('--alert-record-query-url='.length);
    } else if (arg.startsWith('--record-coverage-query-url=')) {
      parsed.recordCoverageQueryUrl = arg.slice('--record-coverage-query-url='.length);
    } else if (arg.startsWith('--record-base-url=')) {
      parsed.recordBaseUrl = arg.slice('--record-base-url='.length);
    } else if (arg.startsWith('--record-export-url=')) {
      parsed.recordExportUrl = arg.slice('--record-export-url='.length);
    } else if (arg.startsWith('--playback-material-uri=')) {
      throw new Error('VIDEO playback material URI must be provided through YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI');
    } else if (arg.startsWith('--device-id=')) {
      parsed.deviceId = arg.slice('--device-id='.length);
    } else if (arg.startsWith('--camera-id=')) {
      parsed.cameraId = arg.slice('--camera-id='.length);
    } else if (arg.startsWith('--alert-time=')) {
      parsed.alertTime = arg.slice('--alert-time='.length);
    } else if (arg.startsWith('--time-range=')) {
      parsed.timeRangeSeconds = Number(arg.slice('--time-range='.length));
    } else if (arg.startsWith('--source-alert-id=')) {
      parsed.sourceAlertId = arg.slice('--source-alert-id='.length);
    } else if (arg.startsWith('--review-case-id=')) {
      parsed.reviewCaseId = arg.slice('--review-case-id='.length);
    } else if (arg.startsWith('--review-item-id=')) {
      parsed.reviewItemId = arg.slice('--review-item-id='.length);
    } else if (arg.startsWith('--format=')) {
      parsed.format = arg.slice('--format='.length);
    } else if (arg.startsWith('--timeout-ms=')) {
      parsed.timeoutMs = Number(arg.slice('--timeout-ms='.length));
    } else if (arg.startsWith('--export-poll-attempts=')) {
      parsed.exportPollAttempts = Number(arg.slice('--export-poll-attempts='.length));
    } else if (arg.startsWith('--export-poll-interval-ms=')) {
      parsed.exportPollIntervalMs = Number(arg.slice('--export-poll-interval-ms='.length));
    } else if (arg.startsWith('--record-drift-retention-hours=')) {
      parsed.recordDriftRetentionHours = Number(arg.slice('--record-drift-retention-hours='.length));
    } else if (arg.startsWith('--manifest-verifier-script=')) {
      parsed.manifestVerifierScript = arg.slice('--manifest-verifier-script='.length);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (!parsed.cameraId && parsed.deviceId) {
    parsed.cameraId = parsed.deviceId;
  }
  if (!Number.isFinite(parsed.timeRangeSeconds) || parsed.timeRangeSeconds <= 0) {
    parsed.timeRangeSeconds = DEFAULT_TIME_RANGE_SECONDS;
  }
  if (!Number.isFinite(parsed.timeoutMs) || parsed.timeoutMs <= 0) {
    parsed.timeoutMs = DEFAULT_TIMEOUT_MS;
  }
  if (!Number.isFinite(parsed.exportPollAttempts) || parsed.exportPollAttempts <= 0) {
    parsed.exportPollAttempts = DEFAULT_EXPORT_POLL_ATTEMPTS;
  }
  if (!Number.isFinite(parsed.exportPollIntervalMs) || parsed.exportPollIntervalMs < 0) {
    parsed.exportPollIntervalMs = DEFAULT_EXPORT_POLL_INTERVAL_MS;
  }
  if (!Number.isFinite(parsed.recordDriftRetentionHours) || parsed.recordDriftRetentionHours <= 0) {
    parsed.recordDriftRetentionHours = Number.NaN;
  }

  return parsed;
}

export function requiredOptionErrors(options) {
  const errors = [];
  if (!hasText(options.alertRecordQueryUrl)) {
    errors.push('missing --alert-record-query-url or YFEIEYE_VIDEO_ALERT_RECORD_QUERY_URL');
  }
  if (!hasText(options.recordCoverageQueryUrl)) {
    errors.push('missing --record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL');
  }
  if (!hasText(options.recordBaseUrl)) {
    errors.push('missing --record-base-url or YFEIEYE_VIDEO_RECORD_BASE_URL');
  }
  if (!hasText(options.recordExportUrl)) {
    errors.push('missing --record-export-url or YFEIEYE_VIDEO_RECORD_EXPORT_URL');
  }
  if (!options.allowLocalEndpoints && !hasText(options.token)) {
    errors.push('missing YFEIEYE_VIDEO_SMOKE_TOKEN');
  }
  if (!hasText(options.deviceId)) {
    errors.push('missing --device-id or YFEIEYE_VIDEO_SMOKE_DEVICE_ID');
  }
  if (!hasText(options.alertTime)) {
    errors.push('missing --alert-time or YFEIEYE_VIDEO_SMOKE_ALERT_TIME');
  }
  if (!Number.isFinite(options.recordDriftRetentionHours) || options.recordDriftRetentionHours <= 0) {
    errors.push('missing --record-drift-retention-hours or YFEIEYE_VIDEO_RECORD_DRIFT_RETENTION_HOURS');
  }
  if (!options.allowLocalEndpoints && !hasText(options.manifestVerifierScript)) {
    errors.push('missing --manifest-verifier-script or YFEIEYE_VIDEO_MANIFEST_VERIFIER_SCRIPT');
  }
  if (!options.allowLocalEndpoints
      && hasText(options.alertRecordQueryUrl)
      && hasText(options.recordCoverageQueryUrl)
      && sameReleaseEndpoint(options.alertRecordQueryUrl, options.recordCoverageQueryUrl)) {
    errors.push('record coverage URL must not equal alert record query URL; configure dedicated --record-coverage-query-url or YFEIEYE_VIDEO_RECORD_COVERAGE_QUERY_URL');
  }
  if (!options.allowLocalEndpoints) {
    requireReleaseEndpoint(errors, '--alert-record-query-url', options.alertRecordQueryUrl);
    requireReleaseEndpoint(errors, '--record-coverage-query-url', options.recordCoverageQueryUrl);
    requireReleaseEndpoint(errors, '--record-base-url', options.recordBaseUrl);
    requireReleaseEndpoint(errors, '--record-export-url', options.recordExportUrl);
  }
  return errors;
}

export function buildAvailabilityUrl(baseUrl, options, extraParams = {}) {
  const url = new URL(baseUrl);
  url.searchParams.set('device_id', options.deviceId);
  if (hasText(options.cameraId)) {
    url.searchParams.set('camera_id', options.cameraId);
  }
  url.searchParams.set('alert_time', options.alertTime);
  url.searchParams.set('time_range', String(options.timeRangeSeconds));
  if (hasText(options.sourceAlertId)) {
    url.searchParams.set('alert_id', options.sourceAlertId);
  }
  for (const [key, value] of Object.entries(extraParams)) {
    if (value !== undefined && value !== null && String(value) !== '') {
      url.searchParams.set(key, String(value));
    }
  }
  return url.toString();
}

export function selectPlayableSegment(payload) {
  const data = responseData(payload);
  const rows = firstList(data, 'segments', 'records', 'items', 'recordings', 'timeline_merged', 'timelineMerged', 'timeline');
  const candidates = rows.length ? rows : [data];
  for (const row of candidates) {
    if (!row || typeof row !== 'object') {
      continue;
    }
    const status = firstText(row.status, row.coverage_status, row.coverageStatus);
    const recordEvidence = firstTextEntry([
      ['record_uri', row.record_uri],
      ['recordUri', row.recordUri],
      ['play_url', row.play_url],
      ['playUrl', row.playUrl],
      ['video_url', row.video_url],
      ['videoUrl', row.videoUrl],
      ['url', row.url],
      ['file_path', row.file_path],
    ]);
    const recordUri = recordEvidence.value;
    const exportable = firstPresent(row.exportable, row.can_export, row.canExport);
    if (!hasText(recordUri) || String(status || '').toLowerCase() === 'missing' || exportable === false) {
      continue;
    }
    return {
      raw: row,
      status,
      recordUri,
      recordUriSource: recordEvidence.key,
      startTime: firstText(row.start_time, row.startTime, row.begin_time, row.beginTime, row.event_time, row.eventTime),
      endTime: firstText(row.end_time, row.endTime, row.stop_time, row.stopTime),
      retainMode: normalizeCoverageClassification(firstText(row.retain_mode, row.retainMode, row.retain)),
      coverageSource: normalizeCoverageClassification(firstText(
        row.coverage_source,
        row.coverageSource,
        row.source,
        row.record_source,
        row.recordSource,
      )),
    };
  }
  return null;
}

function describeNonPlayableSegments(payload) {
  const data = responseData(payload);
  const rows = firstList(data, 'segments', 'records', 'items', 'recordings', 'timeline_merged', 'timelineMerged', 'timeline');
  const candidates = rows.length ? rows : [data];
  const reasons = [];
  for (const row of candidates) {
    if (!row || typeof row !== 'object') {
      continue;
    }
    const recordEvidence = firstTextEntry([
      ['record_uri', row.record_uri],
      ['recordUri', row.recordUri],
      ['play_url', row.play_url],
      ['playUrl', row.playUrl],
      ['video_url', row.video_url],
      ['videoUrl', row.videoUrl],
      ['url', row.url],
      ['file_path', row.file_path],
    ]);
    const status = firstText(row.status, row.coverage_status, row.coverageStatus);
    const nonExportableReason = firstText(
      row.non_exportable_reason,
      row.nonExportableReason,
      row.unexportable_reason,
      row.unexportableReason,
      row.gap_reason,
      row.gapReason,
      row.reason,
    );
    if (hasText(nonExportableReason)) {
      reasons.push(nonExportableReason);
    } else if (firstPresent(row.exportable, row.can_export, row.canExport) === false) {
      reasons.push('exportable=false');
    } else if (hasText(status) && String(status).toLowerCase() === 'missing') {
      reasons.push('status=missing');
    } else if (!hasText(recordEvidence.value)) {
      reasons.push('record_uri_missing');
    }
  }
  return [...new Set(reasons)].slice(0, 4).join(', ');
}

export function buildExportBody(options, segment) {
  const raw = segment?.raw && typeof segment.raw === 'object' ? segment.raw : {};
  const body = {
    review_case_id: options.reviewCaseId,
    review_item_id: options.reviewItemId,
    device_id: options.deviceId,
    camera_id: options.cameraId,
    source_alert_id: options.sourceAlertId,
    start_time: segment.startTime,
    end_time: segment.endTime,
    record_uri: segment.recordUri,
    space_id: firstPresent(raw.space_id, raw.spaceId),
    object_name: firstText(raw.object_name, raw.objectName),
    segment_start_time: firstText(raw.segment_start_time, raw.segmentStartTime, segment.startTime),
    segment_end_time: firstText(raw.segment_end_time, raw.segmentEndTime, segment.endTime),
    format: options.format || 'mp4',
    async_worker: true,
  };
  const recordSegment = {
    index: 0,
    record_uri: segment.recordUri,
    space_id: body.space_id,
    object_name: body.object_name,
    segment_start_time: body.segment_start_time,
    segment_end_time: body.segment_end_time,
    clip_start_time: segment.startTime,
    clip_end_time: segment.endTime,
  };
  for (const key of Object.keys(recordSegment)) {
    if (recordSegment[key] === undefined || recordSegment[key] === null || recordSegment[key] === '') {
      delete recordSegment[key];
    }
  }
  body.record_segments = [recordSegment];
  for (const key of Object.keys(body)) {
    if (body[key] === undefined || body[key] === null || body[key] === '') {
      delete body[key];
    }
  }
  return body;
}

export async function runSmoke(options, dependencies = {}) {
  const errors = requiredOptionErrors(options);
  if (errors.length) {
    throw new Error(errors.join('\n'));
  }
  const rawFetchImpl = dependencies.fetchImpl || globalThis.fetch;
  if (typeof rawFetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+ or provide fetchImpl');
  }
  const fetchImpl = withBearerAuthorization(rawFetchImpl, options.token);

  const checkpoints = [];
  const alertRecord = await fetchJson(fetchImpl, buildAvailabilityUrl(options.alertRecordQueryUrl, options), {
    timeoutMs: options.timeoutMs,
    label: 'alert record query',
  });
  const alertSegment = selectPlayableSegment(alertRecord);
  if (!alertSegment) {
    throw new Error('alert record query returned no playable record segment');
  }
  assertReleaseSegmentMediaEvidence(options, alertSegment);
  checkpoints.push('alert_record_query_ok');

  const coverage = await fetchJson(fetchImpl, buildAvailabilityUrl(options.recordCoverageQueryUrl, options, {
    begin_time: alertSegment.startTime,
    end_time: alertSegment.endTime,
  }), {
    timeoutMs: options.timeoutMs,
    label: 'record coverage query',
  });
  const coverageSegment = selectPlayableSegment(coverage);
  if (!coverageSegment) {
    const summary = describeNonPlayableSegments(coverage);
    throw new Error(`record coverage query returned no playable/exportable record segment${summary ? `: ${summary}` : ''}`);
  }
  assertReleaseSegmentMediaEvidence(options, coverageSegment);
  validateCoverageClassification(coverageSegment);
  checkpoints.push('record_coverage_query_ok');
  const exportSegment = hasText(options.playbackMaterialUri)
    ? {
        ...coverageSegment,
        recordUri: options.playbackMaterialUri,
        recordUriSource: 'environment',
      }
    : coverageSegment;
  assertReleaseSegmentMediaEvidence(options, exportSegment);

  const recordSpace = await fetchJson(fetchImpl, `${stripTrailingSlash(options.recordBaseUrl)}/space/device/${encodeURIComponent(options.deviceId)}`, {
    timeoutMs: options.timeoutMs,
    label: 'record base space lookup',
  });
  const spaceData = responseData(recordSpace);
  if (!hasText(firstText(spaceData.id, spaceData.space_id, spaceData.spaceId))) {
    throw new Error('record base URL did not resolve a record space for the smoke device');
  }
  checkpoints.push('record_base_space_resolved');
  const recordStorageDrift = await fetchJson(fetchImpl, buildRecordDriftUrl(options.recordBaseUrl, spaceData, options), {
    timeoutMs: options.timeoutMs,
    label: 'record storage drift patrol',
  });
  const storageDrift = validateStorageDriftReport(recordStorageDrift);
  checkpoints.push('record_storage_drift_patrol_ok');

  const exportResponse = await fetchJson(fetchImpl, options.recordExportUrl, {
    timeoutMs: options.timeoutMs,
    label: 'record export',
    method: 'POST',
    body: buildExportBody(options, exportSegment),
  });
  const exportResult = normalizeExportResult(exportResponse);
  if (!hasText(exportResult.exportId) && !hasText(exportResult.downloadUrl)) {
    throw new Error('record export response did not include export_id or download_url');
  }
  checkpoints.push('record_export_posted');
  const readyExportResult = await waitForExportDownload(fetchImpl, options, exportResult);
  assertReleaseMediaEvidence(options, 'download URL', readyExportResult.downloadUrl);
  checkpoints.push('record_export_download_ready');
  await probeDownloadUrl(fetchImpl, options, readyExportResult.downloadUrl);
  checkpoints.push('record_export_download_probed');
  const manifestEvidence = await verifyExportManifest(fetchImpl, options, readyExportResult, dependencies);
  checkpoints.push('record_export_manifest_verified');

  return {
    ok: true,
    checkpoints,
    alertRecord: { segment: alertSegment },
    coverage: { segment: coverageSegment },
    recordSpace: spaceData,
    storageDrift,
    exportResult: readyExportResult,
    manifestSignature: manifestEvidence.signature,
    manifestStorageLifecycle: manifestEvidence.storageLifecycle,
    ...(manifestEvidence.verification ? { manifestVerification: manifestEvidence.verification } : {}),
  };
}

function withBearerAuthorization(fetchImpl, token) {
  if (!hasText(token)) {
    return fetchImpl;
  }
  return (url, init = {}) => {
    const headers = new Headers(init.headers || {});
    headers.set('authorization', `Bearer ${token}`);
    return fetchImpl(url, { ...init, headers });
  };
}

export function summarizeCliResult(result) {
  const summary = result?.storageDrift?.summary && typeof result.storageDrift.summary === 'object'
    ? result.storageDrift.summary
    : {};
  return sanitizeCliOutputValue({
    checkpoints: Array.isArray(result?.checkpoints) ? result.checkpoints : [],
    ...(result?.coverage?.segment ? { coverageSummary: coverageSegmentSummary(result.coverage.segment) } : {}),
    storageDriftSummary: {
      healthy: summary.healthy === true,
      recordCount: numberValue(firstPresent(summary.record_count, summary.recordCount)),
      issueCount: numberValue(firstPresent(summary.issue_count, summary.issueCount)),
      issueReasons: summary.issue_reasons || summary.issueReasons || {},
      standardReasonKeys: storageDriftReasonKeys(result?.storageDrift, summary),
    },
    exportResult: sanitizeExportResultForOutput(result?.exportResult),
    ...(result?.manifestSignature ? { manifestSignature: result.manifestSignature } : {}),
    ...(result?.manifestStorageLifecycle ? { manifestStorageLifecycle: result.manifestStorageLifecycle } : {}),
    ...(result?.manifestVerification ? { manifestVerification: result.manifestVerification } : {}),
  });
}

function sanitizeExportResultForOutput(exportResult) {
  if (!exportResult || typeof exportResult !== 'object') {
    return {};
  }
  const sanitized = { ...exportResult };
  for (const key of ['downloadUrl', 'manifestUrl']) {
    if (typeof sanitized[key] === 'string') {
      sanitized[key] = stripUrlSecrets(sanitized[key]);
    }
  }
  return sanitized;
}

function validateCoverageClassification(segment) {
  if (!hasText(segment?.retainMode) || !hasText(segment?.coverageSource)) {
    throw new Error('record coverage query missing retain mode or source classification evidence');
  }
  const retainMode = String(segment.retainMode).trim().toLowerCase();
  const coverageSource = String(segment.coverageSource).trim().toLowerCase();
  if (!STANDARD_COVERAGE_CLASSIFICATIONS.includes(retainMode)
      || !STANDARD_COVERAGE_CLASSIFICATIONS.includes(coverageSource)) {
    throw new Error(`record coverage query returned non-standard retain mode or source classification: retainMode=${segment.retainMode}, coverageSource=${segment.coverageSource}`);
  }
}

function normalizeCoverageClassification(value) {
  const normalized = String(value ?? '').trim().toLowerCase();
  if (!normalized) {
    return '';
  }
  if (normalized === 'all' || normalized === 'record' || normalized === 'recording') {
    return 'continuous';
  }
  return normalized;
}

function coverageSegmentSummary(segment) {
  const summary = {};
  copyTextIfPresent(summary, segment, 'status');
  copyTextIfPresent(summary, segment, 'retainMode');
  copyTextIfPresent(summary, segment, 'coverageSource');
  return summary;
}

function copyTextIfPresent(target, source, key) {
  const value = firstText(source?.[key]);
  if (hasText(value)) {
    target[key] = value;
  }
}

async function fetchJson(fetchImpl, url, options) {
  const { payload } = await fetchJsonResponse(fetchImpl, url, options);
  return payload;
}

async function fetchJsonResponse(fetchImpl, url, options) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), options.timeoutMs) : null;
  try {
    const response = await fetchImpl(url, {
      method: options.method || 'GET',
      headers: options.body ? { 'content-type': 'application/json' } : undefined,
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: controller?.signal,
    });
    const text = typeof response.text === 'function' ? await response.text() : '';
    let payload;
    try {
      payload = text ? JSON.parse(text) : await response.json();
    } catch {
      payload = text;
    }
    if (!response.ok) {
      throw new Error(`${options.label} failed with HTTP ${response.status} ${response.statusText || ''}: ${summarizePayload(payload)}`);
    }
    return { payload, rawText: text };
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}

function normalizeExportResult(payload) {
  const data = responseData(payload);
  return {
    exportId: firstText(data.export_id, data.exportId, data.id, data.task_id, data.taskId),
    downloadUrl: firstText(data.download_url, data.downloadUrl, data.export_uri, data.exportUri, data.url),
    manifestUrl: firstText(data.manifest_url, data.manifestUrl, data.manifest_uri, data.manifestUri),
    status: firstText(data.status, data.state),
    message: firstText(data.message, data.msg),
  };
}

async function waitForExportDownload(fetchImpl, options, exportResult) {
  if (hasText(exportResult.downloadUrl)) {
    return exportResult;
  }
  if (!hasText(exportResult.exportId)) {
    throw new Error('record export response did not include a download_url and cannot be polled without export_id');
  }
  let latest = exportResult;
  for (let attempt = 0; attempt < options.exportPollAttempts; attempt += 1) {
    if (attempt > 0 && options.exportPollIntervalMs > 0) {
      await delay(options.exportPollIntervalMs);
    }
    const statusPayload = await fetchJson(fetchImpl, buildExportStatusUrl(
      options.recordExportUrl,
      exportResult.exportId,
      options.cameraId,
    ), {
      timeoutMs: options.timeoutMs,
      label: 'record export status',
    });
    latest = normalizeExportResult(statusPayload);
    if (!hasText(latest.exportId)) {
      latest.exportId = exportResult.exportId;
    }
    if (hasText(latest.downloadUrl)) {
      return latest;
    }
    if (String(latest.status || '').toLowerCase() === 'failed') {
      throw new Error(`record export failed before download was ready: ${latest.message || latest.exportId}`);
    }
  }
  throw new Error(`record export did not expose a download_url after ${options.exportPollAttempts} poll attempt(s): ${exportResult.exportId}`);
}

async function probeDownloadUrl(fetchImpl, options, downloadUrl) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), options.timeoutMs) : null;
  try {
    const response = await fetchImpl(resolveScopedExportUrl(
      downloadUrl,
      options.recordExportUrl,
      options.cameraId,
    ), {
      method: 'HEAD',
      signal: controller?.signal,
    });
    if (!response.ok) {
      throw new Error(`record export download probe failed with HTTP ${response.status} ${response.statusText || ''}`.trim());
    }
    validateDownloadProbeHeaders(response);
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}

function validateDownloadProbeHeaders(response) {
  const contentType = readHeader(response, 'content-type').toLowerCase();
  if (!contentType) {
    throw new Error('record export download probe did not return content-type');
  }
  if (!isVideoDownloadContentType(contentType)) {
    throw new Error(`record export download probe returned non-video content-type: ${contentType}`);
  }
  const contentLength = readHeader(response, 'content-length');
  if (contentLength && Number(contentLength) <= 0) {
    throw new Error(`record export download probe returned empty content-length: ${contentLength}`);
  }
}

function readHeader(response, name) {
  return String(response?.headers?.get?.(name) || '').trim();
}

function isVideoDownloadContentType(contentType) {
  return contentType.startsWith('video/')
    || contentType.includes('application/octet-stream');
}

async function verifyExportManifest(fetchImpl, options, exportResult, dependencies = {}) {
  if (!hasText(exportResult.manifestUrl)) {
    throw new Error('record export response did not include manifest_url for reproducible evidence verification');
  }
  assertReleaseMediaEvidence(options, 'manifest URL', exportResult.manifestUrl);
  const manifestUrl = resolveScopedExportUrl(
    exportResult.manifestUrl,
    options.recordExportUrl,
    options.cameraId,
  );
  const manifestResponse = await fetchJsonResponse(fetchImpl, manifestUrl, {
    timeoutMs: options.timeoutMs,
    label: 'record export manifest',
  });
  const manifest = responseData(manifestResponse.payload);
  const manifestRawJson = rawResponseDataJson(
    manifestResponse.rawText,
    manifestResponse.payload,
  );
  const version = firstText(manifest.manifestVersion, manifest.manifest_version, manifest.version);
  if (Number(version) !== 2) {
    throw new Error('record export manifest is not manifestVersion 2');
  }
  const manifestSignature = validateManifestSignature(manifest);
  const recordSegments = firstList(manifest, 'recordSegments', 'record_segments');
  const sourceSegments = firstList(manifest, 'sourceSegments', 'source_segments', 'sources', 'inputs', 'recordSegments', 'record_segments');
  const ffmpegCommandHashes = [
    firstText(
      manifest.ffmpegCommandHash,
      manifest.ffmpeg_command_hash,
      manifest.commandHash,
      manifest.command_hash,
    ),
    ...sourceSegments.map((segment) => firstText(
      segment.ffmpegCommandHash,
      segment.ffmpeg_command_hash,
      segment.commandHash,
      segment.command_hash,
    )),
  ].filter(hasText);
  if (!ffmpegCommandHashes.length) {
    throw new Error('record export manifest missing ffmpeg command hash');
  }
  const invalidFfmpegCommandHash = ffmpegCommandHashes.find((hash) => !isSha256Digest(hash));
  if (invalidFfmpegCommandHash) {
    throw new Error(`record export manifest invalid ffmpeg command hash: ${invalidFfmpegCommandHash}`);
  }
  const clipParams = firstPresent(manifest.clipParams, manifest.clip_params, manifest.clip, manifest.trim);
  const hasSegmentClipParams = sourceSegments.some((segment) => hasText(firstText(
    segment.clipStartTime,
    segment.clip_start_time,
  )) && hasText(firstText(
    segment.clipEndTime,
    segment.clip_end_time,
  )));
  if ((!clipParams || typeof clipParams !== 'object') && !hasSegmentClipParams) {
    throw new Error('record export manifest missing clip params');
  }
  validateClipWindows(sourceSegments);
  const concatOrder = firstList(manifest, 'concatOrder', 'concat_order', 'stitchOrder', 'stitch_order');
  const hasSegmentConcatOrder = recordSegments.length > 0 && recordSegments.every((segment) => hasText(firstText(
    segment.recordUri,
    segment.record_uri,
    segment.uri,
  )) && firstPresent(segment.index, segment.order, segment.sequence) !== undefined);
  if (!concatOrder.length && !hasSegmentConcatOrder) {
    throw new Error('record export manifest missing concat order');
  }
  validateManifestConcatOrder(recordSegments, concatOrder);
  const sourceSegmentHashes = sourceSegments.map((segment) => firstText(
    segment.sourceHash,
    segment.source_hash,
    segment.sha256,
    segment.hash,
    segment.checksum,
  ));
  if (!sourceSegments.length || sourceSegmentHashes.some((hash) => !hasText(hash))) {
    throw new Error('record export manifest missing source segment hashes');
  }
  const invalidSourceHash = sourceSegmentHashes.find((hash) => !isSha256Digest(hash));
  if (invalidSourceHash) {
    throw new Error(`record export manifest invalid source segment hash: ${invalidSourceHash}`);
  }
  const outputs = firstList(manifest, 'outputs', 'files', 'artifacts');
  const outputHashes = outputs.map((output) => firstText(
    output.fileHash,
    output.file_hash,
    output.sha256,
    output.hash,
    output.checksum,
  ));
  if (!outputs.length || outputHashes.some((hash) => !hasText(hash))) {
    throw new Error('record export manifest missing output file hashes');
  }
  const invalidOutputHash = outputHashes.find((hash) => !isSha256Digest(hash));
  if (invalidOutputHash) {
    throw new Error(`record export manifest invalid output file hash: ${invalidOutputHash}`);
  }
  const storageLifecycle = validateManifestStorageLifecycle(manifest, outputs, options);
  const manifestVerification = await runManifestVerifierIfConfigured({
    manifest,
    manifestRawJson,
    manifestUrl,
    options,
    dependencies,
  });
  return {
    signature: manifestSignature,
    storageLifecycle,
    ...(manifestVerification ? { verification: manifestVerification } : {}),
  };
}

function isSha256Digest(value) {
  const hash = String(value || '').trim();
  return /^[a-f0-9]{64}$/i.test(hash)
    || /^sha256:[a-f0-9]{64}$/i.test(hash);
}

function validateClipWindows(sourceSegments) {
  for (const segment of sourceSegments) {
    const start = firstText(segment.clipStartTime, segment.clip_start_time);
    const end = firstText(segment.clipEndTime, segment.clip_end_time);
    if (!hasText(start) || !hasText(end)) {
      continue;
    }
    const startMs = Date.parse(start);
    const endMs = Date.parse(end);
    if (!Number.isFinite(startMs) || !Number.isFinite(endMs) || endMs <= startMs) {
      throw new Error(`record export manifest invalid clip window: ${start} -> ${end}`);
    }
  }
}

function validateManifestConcatOrder(recordSegments, concatOrder) {
  const segmentOrderEntries = recordSegments.map((segment) => firstPresent(
    segment.index,
    segment.order,
    segment.sequence,
  ));
  const orderEntries = concatOrder.length
    ? concatOrder.map(normalizeConcatOrderEntry)
    : segmentOrderEntries;
  const seen = new Set();
  for (const rawOrder of orderEntries) {
    const order = Number(rawOrder);
    if (!Number.isInteger(order) || order < 0) {
      throw new Error(`record export manifest invalid concat order index: ${rawOrder}`);
    }
    if (seen.has(order)) {
      throw new Error(`record export manifest duplicate concat order index: ${order}`);
    }
    seen.add(order);
  }
  if (concatOrder.length) {
    validateRootConcatOrderCoverage(segmentOrderEntries, orderEntries, recordSegments.length);
  }
}

function normalizeConcatOrderEntry(entry) {
  if (entry && typeof entry === 'object') {
    return firstPresent(entry.index, entry.order, entry.sequence);
  }
  return entry;
}

function validateRootConcatOrderCoverage(segmentOrderEntries, orderEntries, recordSegmentCount) {
  if (!recordSegmentCount) {
    return;
  }
  const segmentOrders = segmentOrderEntries.map((rawOrder) => Number(rawOrder));
  const canCompareSegmentIndexes = segmentOrderEntries.every((rawOrder, index) => (
    rawOrder !== undefined
    && rawOrder !== null
    && Number.isInteger(segmentOrders[index])
    && segmentOrders[index] >= 0
  ));
  if (!canCompareSegmentIndexes) {
    if (orderEntries.length !== recordSegmentCount) {
      throw new Error(`record export manifest concat order count ${orderEntries.length} does not match segment count ${recordSegmentCount}`);
    }
    return;
  }
  const segmentIndexSet = new Set(segmentOrders);
  const orderIndexSet = new Set(orderEntries.map((rawOrder) => Number(rawOrder)));
  for (const order of orderIndexSet) {
    if (!segmentIndexSet.has(order)) {
      throw new Error(`record export manifest concat order references missing segment index: ${order}`);
    }
  }
  for (const segmentIndex of segmentIndexSet) {
    if (!orderIndexSet.has(segmentIndex)) {
      throw new Error(`record export manifest concat order omits segment index: ${segmentIndex}`);
    }
  }
  if (orderEntries.length !== recordSegmentCount) {
    throw new Error(`record export manifest concat order count ${orderEntries.length} does not match segment count ${recordSegmentCount}`);
  }
}

function validateManifestStorageLifecycle(manifest, outputs, options) {
  const lifecycle = firstPresent(manifest.storageLifecycle, manifest.storage_lifecycle);
  if (!lifecycle || typeof lifecycle !== 'object') {
    throw new Error('record export manifest missing storage lifecycle');
  }
  const storageType = firstText(lifecycle.storageType, lifecycle.storage_type, lifecycle.type);
  if (!hasText(storageType)) {
    throw new Error('record export manifest storage lifecycle missing storage type');
  }
  const status = firstText(lifecycle.status, lifecycle.lifecycleStatus, lifecycle.lifecycle_status);
  if (!hasText(status)) {
    throw new Error('record export manifest storage lifecycle missing status');
  }
  if (['expired', 'deleted', 'purged'].includes(status.toLowerCase())) {
    throw new Error(`record export manifest storage lifecycle is not retained: ${status}`);
  }
  const expiresAt = firstText(lifecycle.expiresAt, lifecycle.expires_at, lifecycle.deleteAfter, lifecycle.delete_after);
  if (!hasText(expiresAt)) {
    throw new Error('record export manifest storage lifecycle missing expiresAt');
  }
  const exportOutput = outputs.find((output) => firstText(output.role, output.artifactRole, output.artifact_role) === 'export_package')
    || outputs[0];
  const storage = exportOutput && typeof exportOutput.storage === 'object' ? exportOutput.storage : null;
  if (!storage) {
    throw new Error('record export manifest missing export package storage reference');
  }
  const objectKey = firstText(storage.objectKey, storage.object_key, storage.key, storage.uri, storage.path);
  if (!hasText(objectKey)) {
    throw new Error('record export manifest export package storage reference missing object key');
  }
  assertReleaseMediaEvidence(options, 'export package storage reference', objectKey);
  const artifactStatus = firstText(storage.lifecycleStatus, storage.lifecycle_status, storage.status, status);
  if (['expired', 'deleted', 'purged'].includes(artifactStatus.toLowerCase())) {
    throw new Error(`record export manifest export package storage is not retained: ${artifactStatus}`);
  }
  return {
    storageType,
    status,
    expiresAt,
    exportPackageObjectKey: objectKey,
  };
}

async function runManifestVerifierIfConfigured({
  manifest,
  manifestRawJson,
  manifestUrl,
  options,
  dependencies,
}) {
  const injectedVerifier = dependencies && typeof dependencies.verifyManifest === 'function'
    ? dependencies.verifyManifest
    : null;
  if (!injectedVerifier && !hasText(options.manifestVerifierScript)) {
    return null;
  }
  const report = injectedVerifier
    ? await injectedVerifier({
      manifest,
      manifestRawJson,
      manifestUrl,
      options,
    })
    : runManifestVerifierScript(
      options.manifestVerifierScript,
      manifestRawJson,
      options.timeoutMs,
    );
  const normalized = normalizeManifestVerificationReport(report);
  if (normalized.valid !== true) {
    const detail = normalized.violations.length ? normalized.violations.join(', ') : 'invalid_manifest';
    throw new Error(`record export manifest verifier failed: ${detail}`);
  }
  return normalized;
}

function runManifestVerifierScript(scriptPath, manifestRawJson, timeoutMs) {
  const tempDir = mkdtempSync(join(tmpdir(), 'yfeieye-manifest-'));
  const manifestPath = join(tempDir, 'manifest.json');
  try {
    writeFileSync(manifestPath, manifestRawJson, 'utf8');
    const result = spawnSync(process.execPath, [resolve(scriptPath), '--manifest', manifestPath], {
      encoding: 'utf8',
      env: buildManifestVerifierEnvironment(),
      timeout: timeoutMs,
    });
    if (result.error?.code === 'ETIMEDOUT') {
      throw new Error(`record export manifest verifier timed out after ${timeoutMs}ms`);
    }
    if (result.error) {
      throw new Error(`record export manifest verifier failed to start: ${result.error.message}`);
    }
    const output = String(result.stdout || '').trim();
    if (result.status !== 0) {
      const detail = String(result.stderr || output || `exit ${result.status ?? 1}`).trim();
      throw new Error(`record export manifest verifier failed with exit ${result.status}: ${detail}`);
    }
    try {
      return output ? JSON.parse(output) : {};
    } catch {
      const detail = String(result.stderr || output || `exit ${result.status ?? 1}`).trim();
      throw new Error(`record export manifest verifier returned invalid JSON: ${detail}`);
    }
  } finally {
    rmSync(tempDir, { recursive: true, force: true });
  }
}

function rawResponseDataJson(rawText, payload) {
  if (!hasText(rawText)) {
    throw new Error('record export manifest response did not include raw JSON text');
  }
  if (payload && typeof payload === 'object'
      && payload.data && typeof payload.data === 'object') {
    return extractTopLevelJsonObject(rawText, 'data');
  }
  return rawText;
}

function extractTopLevelJsonObject(rawJson, propertyName) {
  let objectDepth = 0;
  let sawTopLevelObject = false;
  let propertyJson;
  let index = 0;
  while (index < rawJson.length) {
    if (rawJson[index] === '{') {
      objectDepth += 1;
      sawTopLevelObject = true;
    } else if (rawJson[index] === '}') {
      objectDepth -= 1;
    } else if (rawJson[index] === '"') {
      const keyEnd = jsonStringEnd(rawJson, index);
      const separator = skipJsonWhitespace(rawJson, keyEnd);
      if (objectDepth === 1
          && rawJson[separator] === ':'
          && JSON.parse(rawJson.slice(index, keyEnd)) === propertyName) {
        const valueStart = skipJsonWhitespace(rawJson, separator + 1);
        const valueEnd = jsonObjectEnd(rawJson, valueStart);
        if (propertyJson !== undefined) {
          throw new Error(`record export manifest response contains duplicate top-level ${propertyName} payloads`);
        }
        propertyJson = rawJson.slice(valueStart, valueEnd);
      }
      index = keyEnd;
      continue;
    }
    index += 1;
  }
  if (!sawTopLevelObject) {
    throw new Error('record export manifest response is not a JSON object');
  }
  if (propertyJson !== undefined) {
    return propertyJson;
  }
  throw new Error(`record export manifest response missing raw ${propertyName} payload`);
}

function jsonStringEnd(rawJson, start) {
  for (let index = start + 1; index < rawJson.length; index += 1) {
    if (rawJson[index] === '\\') {
      index += 1;
    } else if (rawJson[index] === '"') {
      return index + 1;
    }
  }
  throw new Error('record export manifest response contains an unterminated JSON string');
}

function jsonObjectEnd(rawJson, start) {
  if (rawJson[start] !== '{') {
    throw new Error('record export manifest data is not a JSON object');
  }
  let depth = 0;
  for (let index = start; index < rawJson.length; index += 1) {
    if (rawJson[index] === '"') {
      index = jsonStringEnd(rawJson, index) - 1;
    } else if (rawJson[index] === '{') {
      depth += 1;
    } else if (rawJson[index] === '}') {
      depth -= 1;
      if (depth === 0) {
        return index + 1;
      }
    }
  }
  throw new Error('record export manifest response contains an unterminated data object');
}

function skipJsonWhitespace(rawJson, start) {
  let index = start;
  while (index < rawJson.length && /\s/.test(rawJson[index])) {
    index += 1;
  }
  return index;
}

function normalizeManifestVerificationReport(report) {
  if (!report || typeof report !== 'object') {
    throw new Error('record export manifest verifier returned no JSON object');
  }
  return {
    valid: report.valid === true,
    signatureValid: report.signatureValid === true,
    signatureKeyAvailable: report.signatureKeyAvailable === true,
    keyId: firstText(report.keyId, report.key_id),
    signatureVersion: firstText(
      report.signatureVersion,
      report.signature_version,
      report.algorithmVersion,
      report.algorithm_version,
    ),
    violations: Array.isArray(report.violations) ? report.violations.map((violation) => String(violation)) : [],
  };
}

function validateManifestSignature(manifest) {
  const signature = manifest.signature && typeof manifest.signature === 'object' ? manifest.signature : null;
  if (!signature) {
    throw new Error('record export manifest missing HMAC signature metadata');
  }
  if (firstText(signature.algorithm, signature.alg) !== 'hmac-sha256') {
    throw new Error('record export manifest signature algorithm is not hmac-sha256');
  }
  if (!hasText(firstText(signature.keyId, signature.key_id))) {
    throw new Error('record export manifest signature missing keyId');
  }
  if (!hasText(firstText(signature.signatureVersion, signature.signature_version, signature.algorithmVersion, signature.algorithm_version))) {
    throw new Error('record export manifest signature missing version');
  }
  const value = firstText(signature.value, signature.signature, signature.digest);
  if (!hasText(value) || !value.startsWith('hmac-sha256:')) {
    throw new Error('record export manifest signature missing hmac-sha256 value');
  }
  if (!isHmacSha256SignatureValue(value)) {
    throw new Error('record export manifest signature value is not canonical hmac-sha256');
  }
  return {
    algorithm: 'hmac-sha256',
    keyId: firstText(signature.keyId, signature.key_id),
    signatureVersion: firstText(signature.signatureVersion, signature.signature_version, signature.algorithmVersion, signature.algorithm_version),
  };
}

function isHmacSha256SignatureValue(value) {
  const raw = String(value || '').trim();
  if (!raw.startsWith('hmac-sha256:')) {
    return false;
  }
  const digest = raw.slice('hmac-sha256:'.length);
  return /^[a-f0-9]{64}$/i.test(digest)
    || /^[A-Za-z0-9+/]{43}=$/.test(digest)
    || /^[A-Za-z0-9_-]{43}=?$/.test(digest);
}

export function resolveDownloadUrl(downloadUrl, baseUrl) {
  const internalExportPrefix = '/video/record/export/';
  if (String(downloadUrl || '').startsWith(internalExportPrefix)) {
    const resolved = new URL(baseUrl);
    resolved.search = '';
    resolved.hash = '';
    resolved.pathname = `${resolved.pathname.replace(/\/+$/, '')}/${String(downloadUrl).slice(internalExportPrefix.length)}`;
    return resolved.toString();
  }
  return new URL(downloadUrl, baseUrl).toString();
}

function buildExportStatusUrl(recordExportUrl, exportId, cameraId) {
  return appendCameraScope(
    `${stripTrailingSlash(recordExportUrl)}/${encodeURIComponent(exportId)}`,
    cameraId,
  );
}

function resolveScopedExportUrl(resourceUrl, recordExportUrl, cameraId) {
  return appendCameraScope(resolveDownloadUrl(resourceUrl, recordExportUrl), cameraId);
}

function appendCameraScope(resourceUrl, cameraId) {
  const url = new URL(resourceUrl);
  if (hasText(cameraId)) {
    url.searchParams.set('camera_id', cameraId);
  }
  return url.toString();
}

function buildRecordDriftUrl(recordBaseUrl, spaceData, options) {
  const spaceId = firstText(spaceData.id, spaceData.space_id, spaceData.spaceId);
  const url = new URL(`${stripTrailingSlash(recordBaseUrl)}/space/${encodeURIComponent(spaceId)}/videos/drift`);
  url.searchParams.set('device_id', options.deviceId);
  url.searchParams.set('retention_hours', String(options.recordDriftRetentionHours));
  return url.toString();
}

function validateStorageDriftReport(payload) {
  const data = responseData(payload);
  const summary = data.summary && typeof data.summary === 'object' ? data.summary : {};
  const recordCount = numberValue(firstPresent(summary.record_count, summary.recordCount));
  const issueCount = numberValue(firstPresent(summary.issue_count, summary.issueCount));
  if (!Number.isFinite(recordCount) || recordCount <= 0) {
    throw new Error('record storage drift patrol returned no checked recording metadata');
  }
  if (!Number.isFinite(issueCount)) {
    throw new Error('record storage drift patrol response missing issue_count');
  }
  if (issueCount > 0 || summary.healthy === false) {
    throw new Error(`record storage drift patrol reported ${issueCount} issue(s): ${formatIssueReasons(summary.issue_reasons || summary.issueReasons)}`);
  }
  const reasonKeys = storageDriftReasonKeys(data, summary);
  const missingReason = REQUIRED_STORAGE_DRIFT_REASON_KEYS.find((reason) => !reasonKeys.includes(reason));
  if (missingReason) {
    throw new Error(`record storage drift patrol missing standard reason evidence: ${missingReason}`);
  }
  return {
    ...data,
    summary: {
      ...summary,
      standardReasonKeys: reasonKeys,
    },
  };
}

function storageDriftReasonKeys(data, summary) {
  const keys = firstList(
    summary,
    'standard_reason_keys',
    'standardReasonKeys',
    'reason_keys',
    'reasonKeys',
  );
  if (keys.length > 0) {
    return uniqueTextValues(keys);
  }
  const dataKeys = firstList(
    data,
    'standard_reason_keys',
    'standardReasonKeys',
    'reason_keys',
    'reasonKeys',
  );
  if (dataKeys.length > 0) {
    return uniqueTextValues(dataKeys);
  }
  const catalog = firstPresent(
    summary?.reason_catalog,
    summary?.reasonCatalog,
    data?.reason_catalog,
    data?.reasonCatalog,
  );
  if (catalog && typeof catalog === 'object' && !Array.isArray(catalog)) {
    return uniqueTextValues(Object.keys(catalog));
  }
  return [];
}

function uniqueTextValues(values) {
  return [...new Set(values.map((value) => String(value).trim()).filter(Boolean))];
}

function formatIssueReasons(issueReasons) {
  if (!issueReasons || typeof issueReasons !== 'object') {
    return 'unknown';
  }
  const entries = Object.entries(issueReasons)
    .filter(([, count]) => Number(count) > 0)
    .map(([reason, count]) => `${reason}=${count}`);
  return entries.length ? entries.join(', ') : 'unknown';
}

function numberValue(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : Number.NaN;
}

function delay(ms) {
  return new Promise((resolveDelay) => setTimeout(resolveDelay, ms));
}

function responseData(payload) {
  if (payload && typeof payload === 'object' && payload.data && typeof payload.data === 'object') {
    return payload.data;
  }
  return payload && typeof payload === 'object' ? payload : {};
}

function firstList(object, ...keys) {
  for (const key of keys) {
    const value = object?.[key];
    if (Array.isArray(value)) {
      return value;
    }
  }
  return [];
}

function firstPresent(...values) {
  return values.find((value) => value !== undefined && value !== null);
}

function firstText(...values) {
  const value = firstPresent(...values);
  return value === undefined || value === null ? '' : String(value);
}

function firstTextEntry(entries) {
  for (const [key, value] of entries) {
    if (value !== undefined && value !== null) {
      return { key, value: String(value) };
    }
  }
  return { key: '', value: '' };
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== '';
}

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function sameReleaseEndpoint(left, right) {
  return normalizeEndpointForComparison(left) === normalizeEndpointForComparison(right);
}

function normalizeEndpointForComparison(value) {
  const raw = stripTrailingSlash(value).trim();
  try {
    const url = new URL(raw);
    url.hash = '';
    url.pathname = stripTrailingSlash(url.pathname);
    return url.toString();
  } catch {
    return raw;
  }
}

function requireReleaseEndpoint(errors, optionName, value) {
  if (!hasText(value) || !looksLocalOrMockEndpoint(value)) {
    return;
  }
  errors.push(`VIDEO live smoke endpoint ${optionName} must not use a local/mock URL without --allow-local-endpoints`);
}

function assertReleaseMediaEvidence(options, label, value) {
  if (!hasText(value)) {
    return;
  }
  if (looksInlineOrOpaqueMediaEvidence(value)) {
    throw new Error(`VIDEO live smoke returned inline/opaque media evidence: ${value}`);
  }
  if (options.allowLocalEndpoints) {
    return;
  }
  if (looksAbsoluteLocalPathEvidence(value)) {
    throw new Error(`VIDEO live smoke returned local file path evidence: ${value}`);
  }
  if (!looksLocalOrMockMediaEvidence(value)) {
    return;
  }
  throw new Error(`VIDEO live smoke returned local/mock ${label}: ${value}`);
}

function assertReleaseSegmentMediaEvidence(options, segment) {
  assertReleaseMediaEvidence(options, 'record URI', segment?.recordUri);
  if (options.allowLocalEndpoints || segment?.recordUriSource !== 'file_path') {
    return;
  }
  throw new Error(`VIDEO live smoke returned local file path evidence: ${segment.recordUri}`);
}

function parseBoolean(value, fallback) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return fallback;
  }
  return !['0', 'false', 'no'].includes(String(value).trim().toLowerCase());
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

function looksLocalOrMockMediaEvidence(value) {
  const raw = String(value || '').trim();
  if (!raw) {
    return false;
  }
  const lowered = raw.toLowerCase();
  if (lowered.startsWith('mock:')
    || lowered.startsWith('mock/')
    || lowered.startsWith('mock\\')
    || lowered.startsWith('file:')
    || lowered.includes('/mock')
    || lowered.includes('\\mock')) {
    return true;
  }
  let url;
  try {
    url = new URL(raw.startsWith('//') ? `https:${raw}` : raw);
  } catch {
    return false;
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

function looksInlineOrOpaqueMediaEvidence(value) {
  const lowered = String(value || '').trim().toLowerCase();
  return lowered.startsWith('data:')
    || lowered.startsWith('blob:')
    || lowered.startsWith('about:');
}

function looksAbsoluteLocalPathEvidence(value) {
  const raw = String(value || '').trim();
  if (!raw) {
    return false;
  }
  const lowered = raw.toLowerCase();
  return /^[a-z]:[\\/]/i.test(raw)
    || raw.startsWith('\\\\')
    || /^\/(var|opt|mnt|media|srv|data|home|tmp)\//i.test(raw);
}

function summarizePayload(payload) {
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload);
  return text.length > 500 ? `${text.slice(0, 500)}...` : text;
}

function stripUrlSecrets(value) {
  return String(value).replace(/[?#].*$/, '');
}

export function buildManifestVerifierEnvironment(parentEnv = process.env) {
  const env = {};
  for (const [key, value] of Object.entries(parentEnv || {})) {
    const normalizedKey = key.toUpperCase();
    if (MANIFEST_VERIFIER_ALLOWED_SENSITIVE_ENV_KEYS.has(normalizedKey)) {
      env[key] = value;
      continue;
    }
    if (MANIFEST_VERIFIER_UNRELATED_SENSITIVE_ENV_KEYS.has(normalizedKey)
        || /(?:^|_)(?:ACCESS_TOKEN|AUTH_TOKEN|SESSION_TOKEN|TOKEN|ACCESS_KEY(?:_ID)?|COOKIE|COOKIES|LOCAL_STORAGE|PASSWORD|PASSWD|SECRET|SIGNATURE|API_KEY|PRIVATE_KEY)(?:_|$)/i.test(normalizedKey)) {
      continue;
    }
    env[key] = value;
  }
  return env;
}

function sanitizeCliOutputText(value) {
  return String(value ?? '')
    .replace(/https?:\/\/[^\s"'<>]+/g, url => stripUrlSecrets(url))
    .replace(/\/[^\s"'<>?#[\]{}]+[?#][^\s"'<>]*/g, uri => stripUrlSecrets(uri))
    .replace(/[^\s"'<>()[\]{},:?#]+[?#][^\s"'<>()[\]{},:]*/g, uri => stripUrlSecrets(uri));
}

function sanitizeCliOutputValue(value) {
  if (typeof value === 'string') {
    return sanitizeCliOutputText(value);
  }
  if (Array.isArray(value)) {
    return value.map(sanitizeCliOutputValue);
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [key, sanitizeCliOutputValue(entry)]),
    );
  }
  return value;
}

function printHelp() {
  console.log(`Usage: node .scripts/alert-review-video-live-smoke.mjs \\
  --alert-record-query-url=http://VIDEO/video/alert/record/query \\
  --record-coverage-query-url=http://VIDEO/video/record/availability \\
  --record-base-url=http://VIDEO/video/record \\
  --record-export-url=http://VIDEO/video/record/export \\
  --device-id=DEVICE_ID --alert-time="YYYY-MM-DD HH:mm:ss" [--camera-id=CAMERA_ID] \\
  --record-drift-retention-hours=24 [--export-poll-attempts=5] [--export-poll-interval-ms=1000] \\
  --manifest-verifier-script=.scripts/record-export-manifest-verifier.mjs \\
  [--allow-local-endpoints]

Runs a real FR-21/FR-32 VIDEO smoke: alert record lookup, coverage lookup,
record-base space lookup, recording DB/disk drift patrol, export POST, export
download readiness, a HEAD probe against the resolved download URL, and manifest v2 reproducibility fields
(ffmpeg command hash, source hashes, clip params, concat order, output hashes).
Release smoke requires --manifest-verifier-script so the fetched manifest is also
checked by the record export verifier where manifest-referenced files are accessible.
The smoke must use a real device with real recording metadata; no mock server is
started. Localhost/mock/file endpoints are rejected unless --allow-local-endpoints
is supplied for co-located real-service smoke. Set YFEIEYE_VIDEO_SMOKE_TOKEN in
the parent environment instead of placing release credentials in argv. If the
export must use an explicit signed recording URI, set
YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI; --playback-material-uri is rejected.`);
}

async function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const result = await runSmoke(options);
  console.log('alert review VIDEO live smoke passed');
  console.log(JSON.stringify(summarizeCliResult(result), null, 2));
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli().catch((error) => {
    console.error(sanitizeCliOutputText(error instanceof Error ? error.message : String(error)));
    process.exitCode = 1;
  });
}
