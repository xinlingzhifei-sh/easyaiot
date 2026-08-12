import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_TIMEOUT_MS = 10_000;

export const REQUIRED_CHECKPOINTS = [
  'video_record_query_checked',
  'real_record_coverage_checked',
  'ingest_review_item',
  'review_event_bound_without_task_dispatch',
  'review_rule_saved',
  'record_coverage_synced',
  'review_case_created',
  'evidence_export_ready',
  'video_export_confirmed',
  'manifest_verified',
  'evidence_download_bytes_verified',
  'evidence_download_audited',
];

export function parseArgs(args, env = process.env) {
  const parsed = {
    deviceBaseUrl: env.YFEIEYE_DEVICE_BASE_URL || '',
    token: env.YFEIEYE_DEVICE_AUTH_TOKEN || '',
    tenantId: numberOrNaN(env.YFEIEYE_DEVICE_TENANT_ID),
    operatorUserId: numberOrNaN(env.YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID),
    alertTime: env.YFEIEYE_DEVICE_SMOKE_ALERT_TIME || '',
    profile: env.YFEIEYE_DEVICE_SMOKE_PROFILE || 'release',
    includeVideoExport: parseBoolean(env.YFEIEYE_DEVICE_SMOKE_INCLUDE_VIDEO_EXPORT, true),
    deviceId: env.YFEIEYE_DEVICE_SMOKE_DEVICE_ID || '',
    cameraId: env.YFEIEYE_DEVICE_SMOKE_CAMERA_ID || '',
    zoneCode: env.YFEIEYE_DEVICE_SMOKE_ZONE_CODE || '',
    sourceAlertId: env.YFEIEYE_DEVICE_SMOKE_SOURCE_ALERT_ID || '',
    allowedCameraIds: parseCsvList(env.YFEIEYE_DEVICE_SMOKE_ALLOWED_CAMERA_IDS),
    timeoutMs: Number(env.YFEIEYE_DEVICE_SMOKE_TIMEOUT_MS || DEFAULT_TIMEOUT_MS),
    playbackReviewItemId: numberOrNaN(env.YFEIEYE_DEVICE_PLAYBACK_REVIEW_ITEM_ID),
    playbackReviewCaseId: numberOrNaN(env.YFEIEYE_DEVICE_PLAYBACK_REVIEW_CASE_ID),
    playbackMaterialUri: env.YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI || '',
    playbackAllowedCameraIds: parseCsvList(env.YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS),
    playbackDeniedCameraIds: parseCsvList(env.YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS),
    playbackReason: env.YFEIEYE_DEVICE_PLAYBACK_REASON || 'release-smoke-playback',
    help: false,
  };

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg.startsWith('--device-base-url=')) {
      parsed.deviceBaseUrl = arg.slice('--device-base-url='.length);
    } else if (arg.startsWith('--token=')) {
      throw new Error('DEVICE smoke token must be provided through YFEIEYE_DEVICE_AUTH_TOKEN');
    } else if (arg.startsWith('--tenant-id=')) {
      parsed.tenantId = numberOrNaN(arg.slice('--tenant-id='.length));
    } else if (arg.startsWith('--operator-user-id=')) {
      parsed.operatorUserId = numberOrNaN(arg.slice('--operator-user-id='.length));
    } else if (arg.startsWith('--alert-time=')) {
      parsed.alertTime = arg.slice('--alert-time='.length);
    } else if (arg.startsWith('--profile=')) {
      parsed.profile = arg.slice('--profile='.length);
    } else if (arg.startsWith('--include-video-export=')) {
      parsed.includeVideoExport = parseBoolean(arg.slice('--include-video-export='.length), true);
    } else if (arg.startsWith('--device-id=')) {
      parsed.deviceId = arg.slice('--device-id='.length);
    } else if (arg.startsWith('--camera-id=')) {
      parsed.cameraId = arg.slice('--camera-id='.length);
    } else if (arg.startsWith('--zone-code=')) {
      parsed.zoneCode = arg.slice('--zone-code='.length);
    } else if (arg.startsWith('--source-alert-id=')) {
      parsed.sourceAlertId = arg.slice('--source-alert-id='.length);
    } else if (arg.startsWith('--allowed-camera-ids=')) {
      parsed.allowedCameraIds = parseCsvList(arg.slice('--allowed-camera-ids='.length));
    } else if (arg.startsWith('--timeout-ms=')) {
      parsed.timeoutMs = Number(arg.slice('--timeout-ms='.length));
    } else if (arg.startsWith('--playback-review-item-id=')) {
      parsed.playbackReviewItemId = numberOrNaN(arg.slice('--playback-review-item-id='.length));
    } else if (arg.startsWith('--playback-review-case-id=')) {
      parsed.playbackReviewCaseId = numberOrNaN(arg.slice('--playback-review-case-id='.length));
    } else if (arg.startsWith('--playback-material-uri=')) {
      throw new Error('DEVICE playback material URI must be provided through YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI');
    } else if (arg.startsWith('--playback-allowed-camera-ids=')) {
      parsed.playbackAllowedCameraIds = parseCsvList(arg.slice('--playback-allowed-camera-ids='.length));
    } else if (arg.startsWith('--playback-denied-camera-ids=')) {
      parsed.playbackDeniedCameraIds = parseCsvList(arg.slice('--playback-denied-camera-ids='.length));
    } else if (arg.startsWith('--playback-reason=')) {
      parsed.playbackReason = arg.slice('--playback-reason='.length);
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (!Number.isFinite(parsed.timeoutMs) || parsed.timeoutMs <= 0) {
    parsed.timeoutMs = DEFAULT_TIMEOUT_MS;
  }
  return parsed;
}

export function requiredOptionErrors(options) {
  const errors = [];
  if (!hasText(options.deviceBaseUrl)) {
    errors.push('missing --device-base-url or YFEIEYE_DEVICE_BASE_URL');
  }
  if (!hasText(options.token)) {
    errors.push('missing YFEIEYE_DEVICE_AUTH_TOKEN');
  }
  if (!Number.isFinite(options.tenantId) || options.tenantId <= 0) {
    errors.push('missing --tenant-id or YFEIEYE_DEVICE_TENANT_ID');
  }
  if (!Number.isFinite(options.operatorUserId) || options.operatorUserId <= 0) {
    errors.push('missing --operator-user-id or YFEIEYE_DEVICE_SMOKE_OPERATOR_USER_ID');
  }
  if (!hasText(options.alertTime)) {
    errors.push('missing --alert-time or YFEIEYE_DEVICE_SMOKE_ALERT_TIME');
  }
  if (isRealProfile(options.profile)) {
    if (!hasText(options.cameraId)) {
      errors.push('missing --camera-id or YFEIEYE_DEVICE_SMOKE_CAMERA_ID for real profile');
    }
    if (!hasText(options.deviceId)) {
      errors.push('missing --device-id or YFEIEYE_DEVICE_SMOKE_DEVICE_ID for real profile');
    }
    if (hasText(options.deviceId) && hasText(options.cameraId)
      && options.deviceId !== options.cameraId) {
      errors.push('real profile requires deviceId and cameraId to identify the same VIDEO camera');
    }
    if (!hasText(options.zoneCode)) {
      errors.push('missing --zone-code or YFEIEYE_DEVICE_SMOKE_ZONE_CODE for real profile');
    }
    if (!Array.isArray(options.allowedCameraIds) || options.allowedCameraIds.length === 0) {
      errors.push('missing --allowed-camera-ids or YFEIEYE_DEVICE_SMOKE_ALLOWED_CAMERA_IDS for real profile');
    }
    if (options.includeVideoExport !== true) {
      errors.push('real profile requires --include-video-export=true');
    }
  }
  return errors;
}

export function buildSmokeUrl(options) {
  return `${stripTrailingSlash(options.deviceBaseUrl)}/system/supervision/alert-review/integration-smoke`;
}

export function buildSmokeBody(options) {
  return {
    operatorUserId: options.operatorUserId,
    includeVideoExport: options.includeVideoExport,
    alertTime: deviceEpochMillis(options.alertTime),
    profile: options.profile,
    deviceId: options.deviceId,
    cameraId: options.cameraId,
    zoneCode: options.zoneCode,
    sourceAlertId: options.sourceAlertId,
    allowedCameraIds: options.allowedCameraIds,
  };
}

function deviceEpochMillis(value) {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  const text = String(value || '').trim();
  if (/^\d+$/.test(text)) {
    const numeric = Number(text);
    if (Number.isFinite(numeric)) {
      return numeric;
    }
  }
  const iso = text.replace(' ', 'T');
  const zoned = /(?:Z|[+-]\d{2}:\d{2})$/i.test(iso) ? iso : `${iso}+08:00`;
  const epochMillis = Date.parse(zoned);
  if (!Number.isFinite(epochMillis)) {
    throw new Error('DEVICE integration smoke requires a valid alert time');
  }
  return epochMillis;
}

export async function runSmoke(options, dependencies = {}) {
  const errors = [
    ...requiredOptionErrors(options),
    ...playbackOptionErrors(options),
  ];
  if (errors.length) {
    throw new Error(errors.join('\n'));
  }
  const fetchImpl = dependencies.fetchImpl || globalThis.fetch;
  if (typeof fetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+ or provide fetchImpl');
  }

  const payload = await fetchJson(fetchImpl, buildSmokeUrl(options), {
    timeoutMs: options.timeoutMs,
    token: options.token,
    tenantId: options.tenantId,
    body: buildSmokeBody(options),
  });
  const result = validateSmokeResult(responseData(payload));
  const auditChain = await runEvidenceAuditSmoke(options, result, fetchImpl);
  const playback = playbackProbeEnabled(options)
    ? await runPlaybackUrlSmoke(options, result, fetchImpl)
    : null;
  const checkpoints = [
    ...result.checkpoints,
    'evidence_audit_chain_verified',
    ...(playback ? playback.checkpoints : []),
  ];
  return {
    ok: true,
    result,
    auditChain,
    checkpoints,
    ...(playback ? { playback } : {}),
  };
}

export function buildPlaybackUrl(options, result, cameraIds, reasonSuffix) {
  const reviewItemId = firstPositiveNumber(options.playbackReviewItemId, result.reviewItemId);
  if (!Number.isFinite(reviewItemId) || reviewItemId <= 0) {
    throw new Error('playback URL smoke missing reviewItemId');
  }
  const url = new URL(`${stripTrailingSlash(options.deviceBaseUrl)}/system/supervision/alert-review/items/${reviewItemId}/playback-url`);
  const reviewCaseId = firstPositiveNumber(options.playbackReviewCaseId, result.reviewCaseId);
  if (Number.isFinite(reviewCaseId) && reviewCaseId > 0) {
    url.searchParams.set('reviewCaseId', String(reviewCaseId));
  }
  if (hasText(options.playbackMaterialUri)) {
    url.searchParams.set('materialUri', options.playbackMaterialUri);
  }
  for (const cameraId of cameraIds) {
    url.searchParams.append('allowedCameraIds', cameraId);
  }
  const reason = hasText(options.playbackReason) ? options.playbackReason : 'release-smoke-playback';
  url.searchParams.set('reason', `${reason} ${reasonSuffix}`);
  return url.toString();
}

export function validateSmokeResult(result) {
  if (!result || typeof result !== 'object') {
    throw new Error('integration smoke response did not include a result object');
  }
  if (String(result.status || '').toLowerCase() !== 'passed') {
    throw new Error(`integration smoke status was not passed: ${String(result.status || '')}`);
  }
  if (!hasValue(result.reviewItemId)) {
    throw new Error('integration smoke response missing reviewItemId');
  }
  if (!hasValue(result.reviewCaseId)) {
    throw new Error('integration smoke response missing reviewCaseId');
  }
  if (!isPositiveId(result.eventId)) {
    throw new Error('integration smoke response missing positive eventId');
  }
  if (!hasText(result.exportJobNo)) {
    throw new Error('integration smoke response missing exportJobNo');
  }
  if (result.manifestValid !== true) {
    throw new Error('integration smoke manifestValid was not true');
  }
  if (result.videoExportRequested !== true) {
    throw new Error('integration smoke videoExportRequested was not true');
  }
  if (result.videoExportConfirmed !== true) {
    throw new Error('integration smoke videoExportConfirmed was not true');
  }
  const checkpoints = Array.isArray(result.checkpoints) ? result.checkpoints.map(String) : [];
  for (const checkpoint of REQUIRED_CHECKPOINTS) {
    if (!checkpoints.includes(checkpoint)) {
      throw new Error(`missing smoke checkpoint: ${checkpoint}`);
    }
  }
  const ruleEvidence = smokeRuleEvidence(result);
  validateRuleEvidence(ruleEvidence);
  return {
    ...result,
    ok: true,
    checkpoints,
    ruleEvidence,
  };
}

function buildEvidenceAuditUrl(options, result) {
  const url = new URL(`${stripTrailingSlash(options.deviceBaseUrl)}/system/supervision/alert-review/evidence-audit`);
  url.searchParams.set('eventId', String(result.eventId));
  url.searchParams.set('reviewCaseId', String(result.reviewCaseId));
  url.searchParams.set('reviewItemId', String(result.reviewItemId));
  url.searchParams.set('exportJobNo', result.exportJobNo);
  return url.toString();
}

async function runEvidenceAuditSmoke(options, result, fetchImpl) {
  const payload = await fetchJson(fetchImpl, buildEvidenceAuditUrl(options, result), {
    method: 'GET',
    timeoutMs: options.timeoutMs,
    token: options.token,
    tenantId: options.tenantId,
    label: 'DEVICE evidence audit chain smoke',
  });
  const entries = responseData(payload);
  const matchingEntry = Array.isArray(entries)
    ? entries.find((entry) => matchesEvidenceAuditEntry(entry, result))
    : null;
  if (!matchingEntry) {
    throw new Error('evidence audit missing matching export_downloaded entry');
  }
  return {
    action: 'export_downloaded',
    reviewCaseId: result.reviewCaseId,
    reviewItemIds: [result.reviewItemId],
    eventIds: [result.eventId],
    exportJobNo: result.exportJobNo,
  };
}

function matchesEvidenceAuditEntry(entry, result) {
  if (!entry || typeof entry !== 'object' || String(entry.actionType || '') !== 'export_downloaded') {
    return false;
  }
  const metadata = entry.metadata && typeof entry.metadata === 'object' && !Array.isArray(entry.metadata)
    ? entry.metadata
    : {};
  return idsEqual(entry.reviewCaseId, result.reviewCaseId)
    && String(entry.jobNo || '') === result.exportJobNo
    && idListIncludes(entry.boundEventIds, result.eventId)
    && idListIncludes(metadata.reviewItemIds, result.reviewItemId)
    && idListIncludes(metadata.eventIds, result.eventId)
    && String(metadata.exportJobNo || '') === result.exportJobNo;
}

function smokeRuleEvidence(result) {
  for (const key of ['ruleEvidence', 'smokeRule', 'reviewRule']) {
    if (result[key] && typeof result[key] === 'object') {
      return result[key];
    }
  }
  return null;
}

function validateRuleEvidence(ruleEvidence) {
  if (!ruleEvidence || typeof ruleEvidence !== 'object') {
    throw new Error('integration smoke response missing rule evidence');
  }
  if (Number(ruleEvidence.inertiaFrames) !== 3) {
    throw new Error('integration smoke rule evidence missing inertiaFrames=3');
  }
  if (Number(ruleEvidence.loiteringSeconds) !== 20) {
    throw new Error('integration smoke rule evidence missing loiteringSeconds=20');
  }
}

async function fetchJson(fetchImpl, url, options) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), options.timeoutMs) : null;
  try {
    const headers = {
      authorization: `Bearer ${options.token}`,
      'tenant-id': String(options.tenantId),
    };
    const init = {
      method: options.method || 'POST',
      headers,
      signal: controller?.signal,
    };
    if (options.body !== undefined) {
      headers['content-type'] = 'application/json';
      init.body = JSON.stringify(options.body);
    }
    const response = await fetchImpl(url, init);
    const text = typeof response.text === 'function' ? await response.text() : '';
    let payload;
    try {
      payload = text ? JSON.parse(text) : {};
    } catch {
      payload = text;
    }
    if (!response.ok) {
      const label = options.label || 'DEVICE integration smoke';
      throw new Error(`${label} failed with HTTP ${response.status} ${response.statusText || ''}: ${summarizePayload(payload)}`);
    }
    assertCommonResult(payload);
    return payload;
  } finally {
    if (timer) {
      clearTimeout(timer);
    }
  }
}

function assertCommonResult(payload) {
  if (!payload || typeof payload !== 'object' || payload.code === undefined || payload.code === null) {
    return;
  }
  if (Number(payload.code) === 0 || Number(payload.code) === 200) {
    return;
  }
  const message = payload.msg || payload.message || summarizePayload(payload);
  throw new Error(`DEVICE integration smoke returned code ${payload.code}: ${message}`);
}

function playbackProbeEnabled(options) {
  return Number.isFinite(options.playbackReviewItemId)
    || Number.isFinite(options.playbackReviewCaseId)
    || hasText(options.playbackMaterialUri)
    || hasValues(options.playbackAllowedCameraIds)
    || hasValues(options.playbackDeniedCameraIds);
}

function playbackOptionErrors(options) {
  if (!playbackProbeEnabled(options)) {
    return [];
  }
  const errors = [];
  if (!hasValues(options.playbackAllowedCameraIds)) {
    errors.push('missing --playback-allowed-camera-ids or YFEIEYE_DEVICE_PLAYBACK_ALLOWED_CAMERA_IDS');
  }
  if (!hasValues(options.playbackDeniedCameraIds)) {
    errors.push('missing --playback-denied-camera-ids or YFEIEYE_DEVICE_PLAYBACK_DENIED_CAMERA_IDS');
  }
  return errors;
}

async function runPlaybackUrlSmoke(options, result, fetchImpl) {
  const grantedPayload = await fetchJson(fetchImpl, buildPlaybackUrl(
    options,
    result,
    options.playbackAllowedCameraIds,
    'allow',
  ), {
    method: 'GET',
    timeoutMs: options.timeoutMs,
    token: options.token,
    tenantId: options.tenantId,
    label: 'DEVICE playback URL allow smoke',
  });
  const granted = validatePlaybackAccess(responseData(grantedPayload), 'granted');

  const deniedPayload = await fetchJson(fetchImpl, buildPlaybackUrl(
    options,
    result,
    options.playbackDeniedCameraIds,
    'deny',
  ), {
    method: 'GET',
    timeoutMs: options.timeoutMs,
    token: options.token,
    tenantId: options.tenantId,
    label: 'DEVICE playback URL deny smoke',
  });
  const denied = validatePlaybackAccess(responseData(deniedPayload), 'denied');
  return {
    ok: true,
    granted,
    denied,
    checkpoints: ['playback_url_granted', 'playback_url_denied'],
  };
}

function validatePlaybackAccess(access, expectedDecision) {
  if (!access || typeof access !== 'object') {
    throw new Error('playback URL smoke response did not include an access object');
  }
  const decision = String(access.decision || '').toLowerCase();
  if (decision !== expectedDecision) {
    throw new Error(`playback URL smoke expected ${expectedDecision} but got ${String(access.decision || '')}`);
  }
  if (expectedDecision === 'granted' && !hasText(access.playbackUrl)) {
    throw new Error('playback URL smoke granted response missing playbackUrl');
  }
  const deniedReasons = Array.isArray(access.deniedReasons) ? access.deniedReasons.map(String) : [];
  if (expectedDecision === 'denied') {
    if (hasText(access.playbackUrl)) {
      throw new Error('playback URL smoke denied response included playbackUrl');
    }
    if (!deniedReasons.includes('camera_not_allowed')) {
      throw new Error('playback URL smoke denied response missing camera_not_allowed');
    }
  }
  return {
    ...access,
    decision,
    deniedReasons,
  };
}

function responseData(payload) {
  if (payload && typeof payload === 'object' && payload.data && typeof payload.data === 'object') {
    return payload.data;
  }
  return payload && typeof payload === 'object' ? payload : {};
}

function hasValue(value) {
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function isPositiveId(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) && Number.isInteger(numeric) && numeric > 0;
}

function idsEqual(left, right) {
  return hasValue(left) && hasValue(right) && String(left).trim() === String(right).trim();
}

function idListIncludes(values, expected) {
  return Array.isArray(values) && values.some((value) => idsEqual(value, expected));
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== '';
}

function isRealProfile(profile) {
  return profile === 'device-video-web' || profile === 'release';
}

function hasValues(values) {
  return Array.isArray(values) && values.length > 0;
}

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function numberOrNaN(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return Number.NaN;
  }
  return Number(value);
}

function firstPositiveNumber(...values) {
  for (const value of values) {
    const numeric = Number(value);
    if (Number.isFinite(numeric) && numeric > 0) {
      return numeric;
    }
  }
  return Number.NaN;
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

function summarizePayload(payload) {
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload);
  return text.length > 500 ? `${text.slice(0, 500)}...` : text;
}

function stripUrlSecrets(value) {
  return String(value).replace(/[?#].*$/, '');
}

export function sanitizeCliOutputText(value) {
  return String(value ?? '')
    .replace(/[^\s"'<>?#[\]{}]+[?#][^\s"'<>]*/g, uri => stripUrlSecrets(uri));
}

export function sanitizeCliOutputValue(value) {
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
  console.log(`Usage: node .scripts/alert-review-device-integration-smoke.mjs \\
  --device-base-url=http://DEVICE/admin-api \\
  --tenant-id=TENANT_ID \\
  --operator-user-id=9200 \\
  --alert-time="2026-07-05T10:00:00" [--profile=release] \\
  --device-id=REAL_CAMERA_ID --camera-id=REAL_CAMERA_ID --zone-code=REAL_ZONE \\
  --allowed-camera-ids=REAL_CAMERA_ID [--source-alert-id=REAL_ALERT_ID] \\
  [--playback-allowed-camera-ids=camera-01 --playback-denied-camera-ids=camera-02]

Runs the deployed FR-32 DEVICE smoke endpoint and requires the full review loop:
ingest -> review rule save -> record coverage sync -> review case -> export ->
manifest verify -> download audit. It expects the release DEVICE service to be connected to real
VIDEO record/export configuration before this can pass. When playback camera
ids are provided, it also checks audited playback-url allow/deny decisions. Set
YFEIEYE_DEVICE_AUTH_TOKEN in the parent environment; CLI token arguments are rejected.
If playback uses a signed recording URI, set YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI;
--playback-material-uri is rejected so the URI cannot enter argv.`);
}

async function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const smoke = await runSmoke(options);
  console.log('alert review DEVICE integration smoke passed');
  console.log(JSON.stringify(sanitizeCliOutputValue({
    status: smoke.result.status,
    profile: smoke.result.profile,
    reviewItemId: smoke.result.reviewItemId,
    reviewCaseId: smoke.result.reviewCaseId,
    eventId: smoke.result.eventId,
    eventIds: smoke.auditChain.eventIds,
    exportJobNo: smoke.result.exportJobNo,
    auditChain: smoke.auditChain,
    manifestValid: smoke.result.manifestValid,
    videoExportRequested: smoke.result.videoExportRequested,
    videoExportConfirmed: smoke.result.videoExportConfirmed,
    ruleEvidence: smoke.result.ruleEvidence,
    playback: smoke.playback ? {
      grantedDecision: smoke.playback.granted.decision,
      deniedDecision: smoke.playback.denied.decision,
      deniedReasons: smoke.playback.denied.deniedReasons,
    } : undefined,
    checkpoints: smoke.checkpoints,
  }), null, 2));
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli().catch((error) => {
    console.error(sanitizeCliOutputText(error instanceof Error ? error.message : String(error)));
    process.exitCode = 1;
  });
}
