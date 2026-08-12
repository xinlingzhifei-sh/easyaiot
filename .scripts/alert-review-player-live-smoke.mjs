import { existsSync } from 'node:fs';
import { mkdtemp, rm } from 'node:fs/promises';
import { createCipheriv } from 'node:crypto';
import net from 'node:net';
import os from 'node:os';
import { resolve } from 'node:path';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const DEFAULT_TIMEOUT_MS = 30_000;
const MAX_NAVIGATION_TIMEOUT_MS = 300_000;
const DEFAULT_AUTH_STORAGE_PREFIX = 'IOT_ADMIN__PRODUCTION__2.1.0-SNAPSHOT__';
const AUTH_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000;
const AUTH_CACHE_KEY = '_11111000001111@';
const AUTH_CACHE_IV = '@11111000001111_';
const BROWSER_SENSITIVE_ENV_KEYS = new Set([
  'YFEIEYE_REVIEW_PLAYER_SMOKE_URL',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE',
  'YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES',
  'YFEIEYE_DEVICE_PLAYBACK_MATERIAL_URI',
  'YFEIEYE_VIDEO_SMOKE_PLAYBACK_MATERIAL_URI',
]);

export function parseArgs(args, env = process.env) {
  const parsed = {
    workbenchUrl: env.YFEIEYE_REVIEW_PLAYER_SMOKE_URL || '',
    reviewRowText: env.YFEIEYE_REVIEW_PLAYER_SMOKE_ROW_TEXT || '',
    actionTestId: env.YFEIEYE_REVIEW_PLAYER_SMOKE_ACTION_TESTID || 'alert-review-detail-seek',
    expectedSeekTime: env.YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_SEEK_TIME || '',
    expectedRecordPathContains: env.YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_RECORD_PATH_CONTAINS || '',
    expectedOffsetSeconds: numberOrNull(env.YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_OFFSET_SECONDS),
    waitText: env.YFEIEYE_REVIEW_PLAYER_SMOKE_WAIT_TEXT || '',
    timeoutMs: Number(env.YFEIEYE_REVIEW_PLAYER_SMOKE_TIMEOUT_MS || DEFAULT_TIMEOUT_MS),
    localStoragePairs: parsePairs(env.YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE || '', '='),
    cookiePairs: parseCookiePairs(env.YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES || ''),
    accessToken: env.YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN || '',
    tenantId: numberOrNull(env.YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID),
    authStoragePrefix: env.YFEIEYE_REVIEW_PLAYER_SMOKE_STORAGE_PREFIX || DEFAULT_AUTH_STORAGE_PREFIX,
    chromePath: env.CHROME_PATH || '',
    assertNativeCurrentTime: env.YFEIEYE_REVIEW_PLAYER_SMOKE_ASSERT_NATIVE_CURRENT_TIME === 'true',
    allowLocalEndpoints: parseBoolean(env.YFEIEYE_REVIEW_PLAYER_SMOKE_ALLOW_LOCAL_ENDPOINTS, false),
    help: false,
  };

  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      parsed.help = true;
    } else if (arg.startsWith('--workbench-url=')) {
      const workbenchUrl = arg.slice('--workbench-url='.length);
      if (hasSensitiveUrlQuery(workbenchUrl)) {
        throw new Error('signed player workbench URL must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_URL');
      }
      parsed.workbenchUrl = workbenchUrl;
    } else if (arg.startsWith('--review-row-text=')) {
      parsed.reviewRowText = arg.slice('--review-row-text='.length);
    } else if (arg.startsWith('--action-testid=')) {
      parsed.actionTestId = arg.slice('--action-testid='.length);
    } else if (arg.startsWith('--expected-seek-time=')) {
      parsed.expectedSeekTime = arg.slice('--expected-seek-time='.length);
    } else if (arg.startsWith('--expected-record-path-contains=')) {
      parsed.expectedRecordPathContains = arg.slice('--expected-record-path-contains='.length);
    } else if (arg.startsWith('--expected-offset-seconds=')) {
      parsed.expectedOffsetSeconds = numberOrNull(arg.slice('--expected-offset-seconds='.length));
    } else if (arg.startsWith('--wait-text=')) {
      parsed.waitText = arg.slice('--wait-text='.length);
    } else if (arg.startsWith('--timeout-ms=')) {
      parsed.timeoutMs = Number(arg.slice('--timeout-ms='.length));
    } else if (arg.startsWith('--local-storage=')) {
      throw new Error('player local storage must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE');
    } else if (arg.startsWith('--cookie=')) {
      throw new Error('player cookies must be provided through YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES');
    } else if (arg.startsWith('--chrome-path=')) {
      parsed.chromePath = arg.slice('--chrome-path='.length);
    } else if (arg === '--assert-native-current-time') {
      parsed.assertNativeCurrentTime = true;
    } else if (arg === '--allow-local-endpoints') {
      parsed.allowLocalEndpoints = true;
    } else {
      throw new Error(`Unknown argument: ${arg}`);
    }
  }

  parsed.localStoragePairs = parsed.localStoragePairs.filter(Boolean);
  parsed.cookiePairs = parsed.cookiePairs.filter(Boolean);
  if (!Number.isFinite(parsed.timeoutMs) || parsed.timeoutMs <= 0) {
    parsed.timeoutMs = DEFAULT_TIMEOUT_MS;
  }
  return parsed;
}

export function requiredOptionErrors(options) {
  const errors = [];
  if (!hasText(options.workbenchUrl)) {
    errors.push('missing --workbench-url or YFEIEYE_REVIEW_PLAYER_SMOKE_URL');
  }
  if (!hasText(options.reviewRowText)) {
    errors.push('missing --review-row-text or YFEIEYE_REVIEW_PLAYER_SMOKE_ROW_TEXT');
  }
  if (!hasText(options.actionTestId)) {
    errors.push('missing --action-testid or YFEIEYE_REVIEW_PLAYER_SMOKE_ACTION_TESTID');
  }
  if (!hasText(options.expectedSeekTime)) {
    errors.push('missing --expected-seek-time or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_SEEK_TIME');
  }
  if (!hasText(options.expectedRecordPathContains)) {
    errors.push('missing --expected-record-path-contains or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_RECORD_PATH_CONTAINS');
  }
  if (!Number.isFinite(options.expectedOffsetSeconds)) {
    errors.push('missing --expected-offset-seconds or YFEIEYE_REVIEW_PLAYER_SMOKE_EXPECTED_OFFSET_SECONDS');
  }
  if (!options.allowLocalEndpoints && looksLocalOrMockEndpoint(options.workbenchUrl)) {
    errors.push('player live smoke workbench URL must not use a local/mock URL without --allow-local-endpoints');
  }
  if (hasText(options.accessToken) && (!Number.isInteger(options.tenantId) || options.tenantId <= 0)) {
    errors.push('player live smoke access token requires a positive YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID');
  }
  if (!hasText(options.accessToken) && Number.isInteger(options.tenantId) && options.tenantId > 0) {
    errors.push('player live smoke tenant id requires YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN');
  }
  return errors;
}

export function buildProductionAuthStoragePairs(options, nowMs = Date.now()) {
  if (!hasText(options?.accessToken)) {
    return [];
  }
  if (!Number.isInteger(options.tenantId) || options.tenantId <= 0) {
    throw new Error('player live smoke access token requires a positive tenant id');
  }
  const prefix = hasText(options.authStoragePrefix)
    ? String(options.authStoragePrefix).trim().toUpperCase()
    : DEFAULT_AUTH_STORAGE_PREFIX;
  const expiresAt = nowMs + AUTH_CACHE_TTL_MS;
  const memoryCache = {
    ACCESS_TOKEN__: { value: options.accessToken, alive: AUTH_CACHE_TTL_MS, time: expiresAt },
    TENANT_ID__: { value: options.tenantId, alive: AUTH_CACHE_TTL_MS, time: expiresAt },
  };
  const persisted = JSON.stringify({ value: memoryCache, time: nowMs, expire: expiresAt });
  return [
    { key: 'jwt_token', value: options.accessToken },
    {
      key: `${prefix}COMMON__LOCAL__KEY__`,
      value: encryptAuthCache(persisted),
    },
  ];
}

function encryptAuthCache(plainText) {
  const input = Buffer.from(plainText, 'utf8');
  const paddingLength = 16 - (input.length % 16);
  const padded = Buffer.concat([input, Buffer.alloc(paddingLength, paddingLength)]);
  const cipher = createCipheriv(
    'aes-128-ctr',
    Buffer.from(AUTH_CACHE_KEY, 'utf8'),
    Buffer.from(AUTH_CACHE_IV, 'utf8'),
  );
  return Buffer.concat([cipher.update(padded), cipher.final()]).toString('base64');
}

export function assertSmokeResult(result, options) {
  if (!result?.clickedRow) {
    throw new Error(`review row not clicked: ${options.reviewRowText}`);
  }
  if (!result?.clickedAction) {
    const availableActionTestIds = Array.isArray(result?.availableActionTestIds)
      ? result.availableActionTestIds.slice(0, 20).join(',')
      : '-';
    throw new Error(
      `seek action not clicked: ${options.actionTestId}; selectedRows=${Number(result?.selectedRowCount || 0)}; detailEntries=${Number(result?.detailStreamEntryCount || 0)}; availableActions=${availableActionTestIds || '-'}`,
    );
  }
  if (result.seekTime !== options.expectedSeekTime) {
    throw new Error(`expected seek_time ${options.expectedSeekTime}, got ${String(result.seekTime)}`);
  }
  const pathText = `${result.recordPath || ''} ${result.currentUrl || ''}`;
  if (!pathText.includes(options.expectedRecordPathContains)) {
    throw new Error(`expected record path/url to include ${options.expectedRecordPathContains}, got ${sanitizeOutputText(pathText).trim() || '-'}`);
  }
  if (!options.allowLocalEndpoints && hasLocalOrMockMediaEvidence(result.recordPath, result.currentUrl)) {
    throw new Error('player live smoke result used local/mock media evidence');
  }
  if (Number(result.playbackOffsetSeconds) !== Number(options.expectedOffsetSeconds)) {
    throw new Error(`expected playback_offset_seconds ${options.expectedOffsetSeconds}, got ${String(result.playbackOffsetSeconds)}`);
  }
  if (options.assertNativeCurrentTime) {
    if (result.nativeCurrentTime === undefined || result.nativeCurrentTime === null || String(result.nativeCurrentTime).trim() === '') {
      throw new Error(`expected native video currentTime evidence near ${options.expectedOffsetSeconds}, got ${String(result.nativeCurrentTime)}`);
    }
    const nativeCurrentTime = Number(result.nativeCurrentTime);
    if (!Number.isFinite(nativeCurrentTime)) {
      throw new Error(`expected native video currentTime evidence near ${options.expectedOffsetSeconds}, got ${String(result.nativeCurrentTime)}`);
    }
    if (Math.abs(nativeCurrentTime - Number(options.expectedOffsetSeconds)) > 1.5) {
      throw new Error(`expected native video currentTime near ${options.expectedOffsetSeconds}, got ${String(result.nativeCurrentTime)}`);
    }
    if (result.nativeError) {
      throw new Error(`native video failed to load: ${JSON.stringify(result.nativeError)}`);
    }
    if (!hasText(result.nativeCurrentSrc)) {
      throw new Error('native video currentSrc is empty');
    }
    if (Number(result.nativeReadyState) < 1 || !Number.isFinite(Number(result.nativeDuration)) || Number(result.nativeDuration) <= 0) {
      throw new Error(`native video metadata was not decoded: readyState=${String(result.nativeReadyState)}, duration=${String(result.nativeDuration)}`);
    }
    if (!result.nativePlayingObserved || result.nativePaused !== false) {
      throw new Error(`native video did not enter playing state: observed=${String(result.nativePlayingObserved)}, paused=${String(result.nativePaused)}`);
    }
  }
}

export function sanitizeSmokeResultForOutput(result) {
  return sanitizeOutputValue(result);
}

export async function runSmoke(options, dependencies = {}) {
  const errors = requiredOptionErrors(options);
  if (errors.length) {
    throw new Error(errors.join('\n'));
  }
  const browserPath = dependencies.browserPath || findBrowserPath(options.chromePath);
  if (!browserPath) {
    throw new Error('missing local Chrome or Edge executable; pass --chrome-path or CHROME_PATH');
  }

  const userDataDir = await mkdtemp(resolve(os.tmpdir(), 'alert-review-player-live-smoke-'));
  let cdp;
  let child;
  try {
    const debugPort = await findFreePort();
    child = spawn(browserPath, [
      '--headless=new',
      '--disable-gpu',
      '--no-sandbox',
      '--disable-dev-shm-usage',
      '--no-first-run',
      '--no-default-browser-check',
      `--remote-debugging-port=${debugPort}`,
      `--user-data-dir=${userDataDir}`,
      'about:blank',
    ], {
      windowsHide: true,
      env: buildBrowserEnvironment(),
    });

    const target = await createBrowserTarget(debugPort, 'about:blank');
    cdp = await CdpClient.connect(target.webSocketDebuggerUrl);
    await cdp.send('Runtime.enable');
    await cdp.send('Page.enable');
    await cdp.send('Network.enable');

    await seedCookies(cdp, options);
    await navigate(cdp, new URL(options.workbenchUrl).origin, options.timeoutMs);
    await seedStorage(cdp, options);
    await navigate(cdp, options.workbenchUrl, options.timeoutMs);
    const result = await runPageAssertions(cdp, options);
    assertSmokeResult(result, options);
    return result;
  } finally {
    await withTimeout(cdp?.send('Browser.close').catch(() => undefined) || Promise.resolve(), 1000);
    cdp?.close();
    if (child?.pid) {
      await withTimeout(terminateProcessTree(child.pid), 3000);
    }
    await rm(userDataDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => undefined);
  }
}

async function seedCookies(cdp, options) {
  const url = new URL(options.workbenchUrl);
  for (const cookie of options.cookiePairs) {
    await cdp.send('Network.setCookie', {
      name: cookie.name,
      value: cookie.value,
      domain: url.hostname,
      path: '/',
      secure: url.protocol === 'https:',
    });
  }
}

async function seedStorage(cdp, options) {
  const storagePairs = [
    ...options.localStoragePairs,
    ...buildProductionAuthStoragePairs(options),
  ];
  if (!storagePairs.length) {
    return;
  }
  const expression = storagePairs
    .map(pair => `localStorage.setItem(${JSON.stringify(pair.key)}, ${JSON.stringify(pair.value)});`)
    .join('\n');
  await cdp.send('Runtime.evaluate', { expression, returnByValue: true });
}

export async function navigate(cdp, url, timeoutMs, dependencies = {}) {
  const waitForNavigationExpression = dependencies.waitForExpression || waitForExpression;
  await cdp.send('Page.navigate', { url });
  await waitForNavigationExpression(
    cdp,
    'document.readyState === "complete" || document.readyState === "interactive"',
    resolveNavigationTimeoutMs(timeoutMs),
  );
}

export function resolveNavigationTimeoutMs(value) {
  const timeoutMs = Number(value);
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    return DEFAULT_TIMEOUT_MS;
  }
  return Math.min(timeoutMs, MAX_NAVIGATION_TIMEOUT_MS);
}

async function runPageAssertions(cdp, options) {
  if (options.waitText) {
    await waitForExpression(cdp, `document.body?.textContent?.includes(${JSON.stringify(options.waitText)})`, options.timeoutMs);
  }
  await waitForExpression(cdp, `document.body?.textContent?.includes(${JSON.stringify(options.reviewRowText)})`, options.timeoutMs);
  const clickResult = await cdp.send('Runtime.evaluate', {
    expression: buildClickAndInspectExpression(options),
    awaitPromise: true,
    returnByValue: true,
  });
  return clickResult.result?.value;
}

function buildClickAndInspectExpression(options) {
  return `(() => new Promise((resolve) => {
    const wait = (ms) => new Promise((next) => setTimeout(next, ms));
    const textIncludes = (el, text) => (el?.textContent || '').includes(text);
    const findRow = () => Array.from(document.querySelectorAll('tbody tr, [data-row-key], .ant-table-row, .ant-list-item, .review-item-row'))
      .find((el) => textIncludes(el, ${JSON.stringify(options.reviewRowText)}));
    const findAction = () => document.querySelector(${JSON.stringify(`[data-testid="${options.actionTestId}"]`)});
    let nativePlayingObserved = false;
    document.addEventListener('playing', (event) => {
      if (event.target instanceof HTMLVideoElement) nativePlayingObserved = true;
    }, true);
    const inspect = () => {
      const stage = document.querySelector('[data-testid="alert-review-dialog-player-stage"]');
      const video = stage?.querySelector('video');
      return {
        clickedRow,
        clickedAction,
        selectedRowCount: document.querySelectorAll('tbody tr.selected, .ant-table-row-selected, .review-item-row.selected').length,
        detailStreamEntryCount: document.querySelectorAll('[data-testid="alert-review-detail-stream"] .timeline-item').length,
        availableActionTestIds: Array.from(document.querySelectorAll('[data-testid^="alert-review-"]'))
          .map((element) => element.getAttribute('data-testid') || '')
          .filter((value, index, values) => value && values.indexOf(value) === index),
        seekTime: stage?.dataset.seekTime || '',
        recordPath: stage?.dataset.recordPath || '',
        currentUrl: stage?.dataset.currentUrl || '',
        playbackOffsetSeconds: Number(stage?.dataset.playbackOffsetSeconds || 'NaN'),
        nativeCurrentTime: video ? Number(video.currentTime || 0) : null,
        nativeCurrentSrc: video?.currentSrc || '',
        nativeReadyState: video ? Number(video.readyState) : null,
        nativePaused: video ? Boolean(video.paused) : null,
        nativeDuration: video && Number.isFinite(video.duration) ? Number(video.duration) : null,
        nativeError: video?.error ? { code: video.error.code, message: video.error.message || '' } : null,
        nativePlayingObserved,
      };
    };
    let clickedRow = false;
    let clickedAction = false;
    (async () => {
      const started = Date.now();
      while (Date.now() - started < ${Number(options.timeoutMs)}) {
        const row = findRow();
        if (row) {
          row.click();
          clickedRow = true;
          break;
        }
        await wait(100);
      }
      while (Date.now() - started < ${Number(options.timeoutMs)}) {
        const action = findAction();
        if (action) {
          action.click();
          clickedAction = true;
          break;
        }
        await wait(100);
      }
      while (Date.now() - started < ${Number(options.timeoutMs)}) {
        const result = inspect();
        const hasPlaybackTarget = result.seekTime || result.currentUrl || result.recordPath;
        const nativePlaybackReady = ${options.assertNativeCurrentTime ? 'Boolean(result.nativeCurrentSrc) && result.nativeReadyState >= 1 && !result.nativeError && result.nativePlayingObserved && result.nativePaused === false' : 'true'};
        if (hasPlaybackTarget && nativePlaybackReady) {
          resolve(result);
          return;
        }
        await wait(100);
      }
      resolve(inspect());
    })();
  }))()`;
}

async function waitForExpression(cdp, expression, timeoutMs) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const response = await cdp.send('Runtime.evaluate', {
      expression: `Boolean(${expression})`,
      returnByValue: true,
    });
    if (response.result?.value === true) {
      return;
    }
    await wait(100);
  }
  throw new Error(`timed out waiting for browser expression: ${expression}`);
}

function findBrowserPath(explicitPath) {
  const candidates = [
    explicitPath,
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  ].filter(Boolean);
  return candidates.find(candidate => existsSync(candidate));
}

function findFreePort() {
  return new Promise((resolvePort, reject) => {
    const server = net.createServer();
    server.unref();
    server.on('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      if (!address || typeof address === 'string') {
        server.close(() => reject(new Error('unable to allocate browser smoke port')));
        return;
      }
      const { port } = address;
      server.close(() => resolvePort(port));
    });
  });
}

async function createBrowserTarget(debugPort, url) {
  const endpoint = `http://127.0.0.1:${debugPort}`;
  const startedAt = Date.now();
  while (Date.now() - startedAt < 10_000) {
    try {
      const response = await fetch(`${endpoint}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' });
      if (response.ok) {
        return await response.json();
      }
    } catch {
      await wait(100);
    }
  }
  throw new Error('timed out waiting for browser debug endpoint');
}

class CdpClient {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.ws.addEventListener('message', event => {
      const message = JSON.parse(event.data);
      if (!message.id) {
        return;
      }
      const pending = this.pending.get(message.id);
      if (!pending) {
        return;
      }
      this.pending.delete(message.id);
      if (message.error) {
        pending.reject(new Error(message.error.message || JSON.stringify(message.error)));
        return;
      }
      pending.resolve(message.result || {});
    });
  }

  static connect(url) {
    return new Promise((resolveConnect, reject) => {
      const ws = new WebSocket(url);
      ws.addEventListener('open', () => resolveConnect(new CdpClient(ws)));
      ws.addEventListener('error', () => reject(new Error('failed to connect browser websocket')));
    });
  }

  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise((resolveSend, reject) => {
      this.pending.set(id, { resolve: resolveSend, reject });
    });
  }

  close() {
    this.ws.close();
  }
}

function parsePairs(value, separator) {
  return String(value || '')
    .split(';')
    .map(item => item.trim())
    .filter(Boolean)
    .map(item => parsePair(item, separator))
    .filter(Boolean);
}

function parsePair(value, separator) {
  const index = String(value).indexOf(separator);
  if (index <= 0) {
    return null;
  }
  return {
    key: value.slice(0, index),
    value: value.slice(index + separator.length),
  };
}

function parseCookiePairs(value) {
  return String(value || '')
    .split(';')
    .map(item => item.trim())
    .filter(Boolean)
    .map(parseCookiePair)
    .filter(Boolean);
}

function parseCookiePair(value) {
  const pair = parsePair(value, '=');
  return pair ? { name: pair.key, value: pair.value } : null;
}

function numberOrNull(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
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

export function buildBrowserEnvironment(parentEnv = process.env) {
  const env = {};
  for (const [key, value] of Object.entries(parentEnv || {})) {
    const normalizedKey = key.toUpperCase();
    if (BROWSER_SENSITIVE_ENV_KEYS.has(normalizedKey)
        || /(?:^|_)(?:ACCESS_TOKEN|AUTH_TOKEN|TOKEN|COOKIE|COOKIES|LOCAL_STORAGE|PASSWORD|PASSWD|SECRET|SIGNATURE|API_KEY|PRIVATE_KEY)(?:_|$)/i.test(normalizedKey)) {
      continue;
    }
    env[key] = value;
  }
  return env;
}

function sanitizeOutputText(value) {
  return String(value ?? '')
    .replace(/https?:\/\/[^\s"'<>]+/g, url => stripUrlSecrets(url))
    .replace(/\/[^\s"'<>?#[\]{}]+[?#][^\s"'<>]*/g, uri => stripUrlSecrets(uri))
    .replace(/[^\s"'<>()[\]{},:?#]+[?#][^\s"'<>()[\]{},:]*/g, uri => stripUrlSecrets(uri));
}

function sanitizeOutputValue(value) {
  if (typeof value === 'string') {
    return sanitizeOutputText(value);
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

function parseBoolean(value, fallback) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return fallback;
  }
  return !['0', 'false', 'no'].includes(String(value).trim().toLowerCase());
}

function hasText(value) {
  return typeof value === 'string' && value.trim() !== '';
}

function hasLocalOrMockMediaEvidence(...values) {
  return values.some((value) => hasText(value) && looksLocalOrMockEndpoint(value));
}

function looksLocalOrMockEndpoint(value) {
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

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    wait(ms).then(() => undefined),
  ]);
}

function terminateProcessTree(pid) {
  return new Promise(resolveKill => {
    const taskkill = spawn('taskkill', ['/PID', String(pid), '/T', '/F'], { windowsHide: true });
    taskkill.on('close', () => resolveKill());
    taskkill.on('error', () => resolveKill());
  });
}

function printHelp() {
  console.log(`Usage: node .scripts/alert-review-player-live-smoke.mjs \\
  --workbench-url=https://HOST/yfeieye/alert?tab=review \\
  --review-row-text=RV-... \\
  --action-testid=alert-review-detail-seek \\
  --expected-seek-time=2026-07-02T08:00:02 \\
  --expected-record-path-contains=east-gate-080000.mp4 \\
  --expected-offset-seconds=2 [--allow-local-endpoints]

Runs a real FR-13/FR-36 browser smoke against a deployed workbench page.
No mock API/server is started. Localhost/mock/file endpoints are rejected unless
--allow-local-endpoints is supplied for co-located real-service smoke. Signed
workbench URLs must use YFEIEYE_REVIEW_PLAYER_SMOKE_URL. Custom auth state must
use YFEIEYE_REVIEW_PLAYER_SMOKE_LOCAL_STORAGE and YFEIEYE_REVIEW_PLAYER_SMOKE_COOKIES;
--local-storage and --cookie are rejected to keep credentials out of argv. For
production authentication, set YFEIEYE_REVIEW_PLAYER_SMOKE_ACCESS_TOKEN and
YFEIEYE_REVIEW_PLAYER_SMOKE_TENANT_ID; the token is injected through the
frontend's encrypted persistent cache.`);
}

async function runCli() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printHelp();
    return;
  }
  const result = await runSmoke(options);
  console.log('alert review player live smoke passed');
  console.log(JSON.stringify(sanitizeSmokeResultForOutput(result), null, 2));
}

if (process.argv[1] && resolve(fileURLToPath(import.meta.url)) === resolve(process.argv[1])) {
  runCli().catch((error) => {
    console.error(sanitizeOutputText(error instanceof Error ? error.message : String(error)));
    process.exitCode = 1;
  });
}
