import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const DEFAULT_TIMEOUT_MS = 10_000;
const RECONCILE_PATH = '/system/supervision/alert-review/runtime-reconcile';
const REVIEW_DATA_DRIFT_ALERT = 'review_data_schema_drift';
const REVIEW_SEGMENT_DRIFT_ALERT = 'review_segment_double_write_drift';
const REVIEW_DATA_REPAIRED_PREFIX = 'review_data_repaired:';
const REVIEW_SEGMENT_REPAIRED_PREFIX = 'review_segment_repaired:';

export const REPORT_SCHEMA_VERSION = 'yfeieye.alert-review.segment-data-reconcile.v1';

export function parseArgs(args, env = process.env) {
  const parsed = {
    deviceBaseUrl: env.YFEIEYE_DEVICE_BASE_URL || '',
    token: env.YFEIEYE_DEVICE_AUTH_TOKEN || '',
    tenantId: numberOrNaN(env.YFEIEYE_DEVICE_TENANT_ID),
    operatorUserId: numberOrNaN(env.YFEIEYE_REVIEW_RECONCILE_OPERATOR_USER_ID),
    timeoutMs: Number(env.YFEIEYE_REVIEW_RECONCILE_TIMEOUT_MS || DEFAULT_TIMEOUT_MS),
    mode: 'dry-run',
    repair: false,
    allowLocalEndpoints: false,
    help: false,
  };
  let dryRunRequested = false;
  let repairRequested = false;

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg === '--dry-run') {
      dryRunRequested = true;
    } else if (arg === '--repair') {
      repairRequested = true;
    } else if (arg === '--allow-local-endpoints') {
      parsed.allowLocalEndpoints = true;
    } else if (arg.startsWith('--device-base-url=')) {
      parsed.deviceBaseUrl = arg.slice('--device-base-url='.length);
    } else if (arg.startsWith('--token=')) {
      parsed.token = arg.slice('--token='.length);
    } else if (arg.startsWith('--tenant-id=')) {
      parsed.tenantId = numberOrNaN(arg.slice('--tenant-id='.length));
    } else if (arg.startsWith('--operator-user-id=')) {
      parsed.operatorUserId = numberOrNaN(arg.slice('--operator-user-id='.length));
    } else if (arg.startsWith('--timeout-ms=')) {
      parsed.timeoutMs = Number(arg.slice('--timeout-ms='.length));
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  if (dryRunRequested && repairRequested) {
    throw new Error('cannot combine --dry-run and --repair');
  }
  if (repairRequested) {
    parsed.mode = 'repair';
    parsed.repair = true;
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
  } else {
    const endpoint = parseEndpoint(options.deviceBaseUrl);
    if (!endpoint) {
      errors.push('segment/data reconcile endpoint must be an absolute URL');
    } else if (!['http:', 'https:'].includes(endpoint.protocol)) {
      errors.push('segment/data reconcile endpoint must use http or https');
    } else if (!options.allowLocalEndpoints && looksLocalOrMockEndpoint(options.deviceBaseUrl)) {
      errors.push('segment/data reconcile endpoint must not use a local/mock URL without --allow-local-endpoints');
    }
  }
  if (!hasText(options.token)) {
    errors.push('missing --token or YFEIEYE_DEVICE_AUTH_TOKEN');
  }
  if (!isPositiveInteger(options.tenantId)) {
    errors.push('missing --tenant-id or YFEIEYE_DEVICE_TENANT_ID');
  }
  if (!isPositiveInteger(options.operatorUserId)) {
    errors.push('missing --operator-user-id or YFEIEYE_REVIEW_RECONCILE_OPERATOR_USER_ID');
  }
  return errors;
}

export function buildReconcileUrl(options) {
  const url = new URL(`${stripTrailingSlash(options.deviceBaseUrl)}${RECONCILE_PATH}`);
  url.searchParams.set('operatorUserId', String(options.operatorUserId));
  url.searchParams.set('repair', String(options.repair === true));
  return url.toString();
}

export function buildReconcileHeaders(options) {
  return {
    authorization: `Bearer ${options.token}`,
    'tenant-id': String(options.tenantId),
  };
}

export async function runReconcile(options, dependencies = {}) {
  const errors = requiredOptionErrors(options);
  if (errors.length > 0) {
    throw new Error(errors.join('\n'));
  }
  const fetchImpl = dependencies.fetchImpl || globalThis.fetch;
  if (typeof fetchImpl !== 'function') {
    throw new Error('global fetch is unavailable; use Node.js 18+ or provide fetchImpl');
  }

  const url = buildReconcileUrl(options);
  const payload = await fetchJson(fetchImpl, url, {
    headers: buildReconcileHeaders(options),
    timeoutMs: options.timeoutMs,
  });
  const result = validateReconcileResult(responseData(payload));
  return buildReport(options, result, url, dependencies);
}

function validateReconcileResult(result) {
  if (!result || typeof result !== 'object' || Array.isArray(result)) {
    throw new Error('segment/data reconcile response missing result object');
  }
  if (!Number.isFinite(Number(result.scannedCount)) || Number(result.scannedCount) < 0) {
    throw new Error('segment/data reconcile response missing scannedCount');
  }
  if (!result.healthReport || typeof result.healthReport !== 'object' || Array.isArray(result.healthReport)) {
    throw new Error('segment/data reconcile response missing healthReport');
  }
  if (result.findings !== undefined && result.findings !== null && !Array.isArray(result.findings)) {
    throw new Error('segment/data reconcile response findings must be an array');
  }
  if (result.healthReport.alerts !== undefined
      && result.healthReport.alerts !== null
      && !Array.isArray(result.healthReport.alerts)) {
    throw new Error('segment/data reconcile response healthReport.alerts must be an array');
  }
  return result;
}

function buildReport(options, result, endpoint, dependencies) {
  const findings = stringList(result.findings);
  const alerts = stringList(result.healthReport.alerts);
  const reviewDataReviewItemIds = findingIds(findings, REVIEW_DATA_REPAIRED_PREFIX);
  const reviewSegmentReviewItemIds = findingIds(findings, REVIEW_SEGMENT_REPAIRED_PREFIX);
  const targetFindings = findings.filter((finding) => (
    finding.startsWith(REVIEW_DATA_REPAIRED_PREFIX)
      || finding.startsWith(REVIEW_SEGMENT_REPAIRED_PREFIX)
  ));
  const otherFindings = findings.filter((finding) => !targetFindings.includes(finding));
  const remainingReviewDataDrift = alerts.includes(REVIEW_DATA_DRIFT_ALERT);
  const remainingReviewSegmentDrift = alerts.includes(REVIEW_SEGMENT_DRIFT_ALERT);
  const detectedReviewDataDrift = remainingReviewDataDrift || reviewDataReviewItemIds.length > 0;
  const detectedReviewSegmentDrift = remainingReviewSegmentDrift || reviewSegmentReviewItemIds.length > 0;
  const hasRemainingTargetDrift = remainingReviewDataDrift || remainingReviewSegmentDrift;
  const hasTargetRepairs = reviewDataReviewItemIds.length > 0 || reviewSegmentReviewItemIds.length > 0;

  let status = 'clean';
  if (options.repair === true && hasRemainingTargetDrift) {
    status = 'repair_incomplete';
  } else if (options.repair === true && hasTargetRepairs) {
    status = 'repaired';
  } else if (hasRemainingTargetDrift) {
    status = 'drift_detected';
  }

  return {
    schemaVersion: REPORT_SCHEMA_VERSION,
    ok: true,
    status,
    mode: options.repair === true ? 'repair' : 'dry-run',
    generatedAt: currentIsoInstant(dependencies.now),
    endpoint,
    tenantId: options.tenantId,
    operatorUserId: options.operatorUserId,
    serverOperatorUserId: result.operatorUserId ?? null,
    scannedCount: Number(result.scannedCount),
    reconciledAt: result.reconciledAt ?? null,
    drift: {
      detected: {
        reviewDataSchema: detectedReviewDataDrift,
        reviewSegmentDoubleWrite: detectedReviewSegmentDrift,
      },
      remaining: {
        reviewDataSchema: remainingReviewDataDrift,
        reviewSegmentDoubleWrite: remainingReviewSegmentDrift,
      },
    },
    repairs: {
      requested: options.repair === true,
      reviewData: {
        count: reviewDataReviewItemIds.length,
        reviewItemIds: reviewDataReviewItemIds,
      },
      reviewSegment: {
        count: reviewSegmentReviewItemIds.length,
        reviewItemIds: reviewSegmentReviewItemIds,
      },
    },
    targetFindings,
    otherFindings,
    alerts,
    runtimeSummary: {
      repairedRecordCount: nonNegativeNumber(result.repairedRecordCount),
      repairedSemanticIndexCount: nonNegativeNumber(result.repairedSemanticIndexCount),
      failedExportJobCount: nonNegativeNumber(result.failedExportJobCount),
      repairableCount: nonNegativeNumber(result.healthReport.repairableCount),
    },
    healthReport: result.healthReport,
  };
}

async function fetchJson(fetchImpl, url, options) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), options.timeoutMs) : null;
  try {
    const response = await fetchImpl(url, {
      method: 'POST',
      headers: options.headers,
      signal: controller?.signal,
    });
    const text = typeof response.text === 'function' ? await response.text() : '';
    let payload;
    try {
      payload = text ? JSON.parse(text) : {};
    } catch {
      payload = text;
    }
    if (!response.ok) {
      throw new Error(`segment/data reconcile failed with HTTP ${response.status} ${response.statusText || ''}: ${summarizePayload(payload)}`);
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
  if ([0, 200].includes(Number(payload.code))) {
    return;
  }
  const message = payload.msg || payload.message || summarizePayload(payload);
  throw new Error(`segment/data reconcile returned code ${payload.code}: ${message}`);
}

function responseData(payload) {
  if (payload && typeof payload === 'object' && payload.data && typeof payload.data === 'object') {
    return payload.data;
  }
  return payload && typeof payload === 'object' ? payload : {};
}

function findingIds(findings, prefix) {
  const identifiers = [];
  const seen = new Set();
  for (const finding of findings) {
    if (!finding.startsWith(prefix)) {
      continue;
    }
    const raw = finding.slice(prefix.length).trim();
    if (!raw) {
      continue;
    }
    const numeric = /^\d+$/.test(raw) ? Number(raw) : Number.NaN;
    const identifier = Number.isSafeInteger(numeric) ? numeric : raw;
    const key = String(identifier);
    if (!seen.has(key)) {
      seen.add(key);
      identifiers.push(identifier);
    }
  }
  return identifiers;
}

function currentIsoInstant(now) {
  const value = typeof now === 'function' ? now() : new Date();
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new Error('segment/data reconcile report clock returned an invalid instant');
  }
  return date.toISOString();
}

function parseEndpoint(value) {
  try {
    return new URL(String(value || '').trim());
  } catch {
    return null;
  }
}

function looksLocalOrMockEndpoint(value) {
  const raw = String(value || '').trim();
  const lowered = raw.toLowerCase();
  const url = parseEndpoint(raw);
  if (!url) {
    return true;
  }
  const hostname = String(url.hostname || '').toLowerCase();
  return url.protocol === 'file:'
    || url.protocol === 'mock:'
    || hostname === 'localhost'
    || hostname.startsWith('127.')
    || hostname === '::1'
    || hostname === '[::1]'
    || hostname === '0.0.0.0'
    || hostname === 'host.docker.internal'
    || hostname.endsWith('.local')
    || hostname.includes('mock')
    || lowered.includes('/mock');
}

function nonNegativeNumber(value) {
  const numeric = Number(value);
  return Number.isFinite(numeric) && numeric >= 0 ? numeric : 0;
}

function numberOrNaN(value) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return Number.NaN;
  }
  return Number(value);
}

function isPositiveInteger(value) {
  return Number.isInteger(value) && value > 0;
}

function stripTrailingSlash(value) {
  return String(value || '').replace(/\/+$/, '');
}

function stringList(value) {
  return Array.isArray(value) ? value.map(String) : [];
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== '';
}

function summarizePayload(payload) {
  const text = typeof payload === 'string' ? payload : JSON.stringify(payload);
  return text.length > 500 ? `${text.slice(0, 500)}...` : text;
}

function printHelp() {
  console.log(`Usage: node .scripts/alert-review-segment-data-reconcile.mjs \\
  --device-base-url=https://DEVICE/admin-api \\
  --token=JWT_TOKEN --tenant-id=1 --operator-user-id=9001 \\
  [--dry-run | --repair] [--timeout-ms=10000] [--allow-local-endpoints]

Checks ReviewSegment/reviewData double-write consistency through the deployed
DEVICE runtime-reconcile API. Dry-run is the default and always sends
repair=false. Mutating reconciliation requires the explicit --repair flag.
Successful execution writes one JSON report to stdout. Local and mock endpoints
are rejected unless --allow-local-endpoints is explicitly supplied.`);
}

async function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const report = await runReconcile(options);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli().catch((error) => {
    process.stderr.write(`${JSON.stringify({
      schemaVersion: REPORT_SCHEMA_VERSION,
      ok: false,
      status: 'failed',
      error: error instanceof Error ? error.message : String(error),
    })}\n`);
    process.exitCode = 1;
  });
}
