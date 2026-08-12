import { existsSync, readFileSync } from 'node:fs'
import { mkdtemp, rm } from 'node:fs/promises'
import net from 'node:net'
import os from 'node:os'
import { resolve } from 'node:path'
import { spawn } from 'node:child_process'
import { parse } from '@vue/compiler-sfc'

const root = process.cwd()
const harnessRoot = resolve(root, 'scripts/fixtures/alert-review-workbench-e2e')
const workbenchPath = resolve(root, 'src/views/alert/components/AlertReviewWorkbench.vue')
const apiPath = resolve(root, 'src/api/supervision/alertReview.ts')
const mockApiPath = resolve(harnessRoot, 'mockAlertReviewApi.ts')
const mode = parseMode(process.argv.slice(2))
const browserHarnessFiles = [
  'scripts/fixtures/alert-review-workbench-e2e/index.html',
  'scripts/fixtures/alert-review-workbench-e2e/main.ts',
  'scripts/fixtures/alert-review-workbench-e2e/BasicModalProbeStub.ts',
  'scripts/fixtures/alert-review-workbench-e2e/modalHarness.ts',
  'scripts/fixtures/alert-review-workbench-e2e/playerDependencyStubs.ts',
  'scripts/fixtures/alert-review-workbench-e2e/PlayerLeafStub.ts',
  'scripts/fixtures/alert-review-workbench-e2e/antDesignVuePlayerStub.ts',
]

const workbenchSource = readFileSync(workbenchPath, 'utf8')
const apiSource = readFileSync(apiPath, 'utf8')
const mockApiSource = readFileSync(mockApiPath, 'utf8')
const parsed = parse(workbenchSource, { filename: workbenchPath })

const failures = []

function parseMode(args) {
  const supportedModes = ['all', 'contract', 'dev-api-mock', 'dev-api-real-drawer']
  let selectedMode = 'all'
  for (const arg of args) {
    if (arg === '--help' || arg === '-h') {
      console.log(`Usage: node scripts/alert-review-workbench-e2e-check.mjs [--mode=${supportedModes.join('|')}]`)
      process.exit(0)
    }
    if (arg.startsWith('--mode=')) {
      selectedMode = arg.slice('--mode='.length)
      continue
    }
    console.error(`unsupported alert review E2E argument ${arg}`)
    process.exit(1)
  }
  if (!supportedModes.includes(selectedMode)) {
    console.error(`unsupported alert review E2E mode ${selectedMode}. Supported modes: ${supportedModes.join(', ')}`)
    process.exit(1)
  }
  return selectedMode
}

const mojibakeFragments = [
  '\uFFFD',
  '\u5bf0\u546d',
  '\u5bb8\u63d2',
  '\u9423',
  '\u7487',
  '\u8930\u66de',
  '\u7f02\u54c4',
  '\u6fb6\u52ed',
  '\u6d93',
  '\u20ac',
  '\u6769\u65bf',
  '\u95b2\u5d85',
  '\u93c8\u590a',
  '\u7e4d\u9354',
]
const requiredChineseCopy = [
  '待复核',
  '已复核',
  '已忽略',
  '误报',
  '已转事件',
  '无需录像',
  '待补证',
  '录像已补',
  '缺录像',
  '补证失败',
  '已创建',
  '已派发',
  '已接收',
  '处置中',
  '待复核',
  '需返工',
  '待审核',
  '异常复核',
  '重大转办',
  '已闭环',
  '有录像',
  '有运动',
]

for (const file of browserHarnessFiles) {
  if (!existsSync(resolve(root, file))) {
    failures.push(`missing browser E2E harness file ${file}`)
  }
}

if (parsed.errors.length) {
  failures.push(`SFC parse errors: ${parsed.errors.map(error => String(error)).join('; ')}`)
}

if (!parsed.descriptor.template || !parsed.descriptor.scriptSetup) {
  failures.push('AlertReviewWorkbench must contain both template and script setup blocks')
}

for (const fragment of mojibakeFragments) {
  if (workbenchSource.includes(fragment)) {
    failures.push(`workbench contains mojibake fragment ${fragment}`)
  }
}

for (const copy of requiredChineseCopy) {
  if (!workbenchSource.includes(copy)) {
    failures.push(`missing required Chinese copy ${copy}`)
  }
}

const requiredTestIds = [
  'alert-review-workbench',
  'alert-review-unified-timeline',
  'alert-review-detail-stream',
  'alert-review-review-segment',
  'alert-review-record-coverage',
  'alert-review-case-panel',
  'alert-review-case-candidate',
  'alert-review-ai-summary',
  'alert-review-evidence-export',
  'alert-review-evidence-audit',
  'alert-review-ops-panel',
  'alert-review-ops-health',
  'alert-review-ops-reconcile',
  'alert-review-ops-semantic',
  'alert-review-ops-smoke',
  'alert-review-ops-manifest-verify',
  'alert-review-ops-rule-geometry',
  'alert-review-unified-action',
  'alert-review-detail-seek',
  'alert-review-list-playback',
  'alert-review-list-coverage',
  'alert-review-create-case',
  'alert-review-candidate-add',
  'alert-review-case-owner',
  'alert-review-case-close',
  'alert-review-case-merge-source',
  'alert-review-case-merge',
  'alert-review-case-split',
  'alert-review-ai-summary-action',
  'alert-review-export-action',
  'alert-review-semantic-trigger-panel',
  'alert-review-semantic-trigger-evaluate',
  'alert-review-semantic-trigger-confirm',
  'alert-review-semantic-trigger-reject',
]

for (const testId of requiredTestIds) {
  if (!workbenchSource.includes(`data-testid="${testId}"`)) {
    failures.push(`missing stable selector ${testId}`)
  }
}

const requiredApiFunctions = [
  'updateAlertReviewLifecycle',
  'syncAlertReviewRecordStorage',
  'queueAlertReviewSemanticReindex',
  'evaluateAlertReviewSemanticIndex',
  'getAlertReviewRuntimeHealth',
  'getAlertReviewSegment',
  'reconcileAlertReviewRuntime',
  'runAlertReviewRuntimePatrol',
  'verifyAlertReviewManifest',
  'verifyAlertReviewEvidencePackage',
  'prepareAlertReviewPlaybackUrl',
  'auditAlertReviewMediaAccess',
  'auditAlertReviewItemMediaAccess',
  'evaluateAlertReviewRuleGeometry',
  'runAlertReviewIntegrationSmoke',
  'generateAlertReviewOperationsReport',
  'acknowledgeAlertReviewOperationsReport',
  'assignAlertReviewCaseOwner',
  'closeAlertReviewCase',
  'mergeAlertReviewCases',
  'splitAlertReviewCase',
  'queryAlertReviewEvidenceAudit',
  'evaluateAlertReviewSemanticTrigger',
  'confirmAlertReviewSemanticTrigger',
  'getAlertReviewSemanticTrigger',
]

for (const fn of requiredApiFunctions) {
  if (!apiSource.includes(`function ${fn}`)) {
    failures.push(`missing API function ${fn}`)
  }
}

const requiredApiRoutes = [
  '/lifecycle',
  '/review-segment',
  '/record-storage/sync',
  '/semantic-index/queue',
  '/semantic-index/evaluation',
  '/runtime-health',
  '/runtime-reconcile',
  '/runtime-patrol',
  '/manifest/verify',
  '/evidence-export-jobs/${jobNo}/verify',
  '/playback-url',
  '/media-access/audit',
  '/rules/geometry-evaluate',
  '/integration-smoke',
  '/operations-report',
  '/operations-report/acknowledgement',
  '/owner',
  '/close',
  '/merge',
  '/split',
  '/semantic-triggers/evaluate',
  '/semantic-triggers/${evaluationId}/confirmation',
  '/semantic-triggers/${evaluationId}',
]

for (const route of requiredApiRoutes) {
  if (!apiSource.includes(route)) {
    failures.push(`missing API route ${route}`)
  }
}

const requiredAuditLookupApiSnippets = [
  'export interface AlertReviewEvidenceAuditQuery',
  'eventId?: number',
  'reviewCaseId?: number',
  'reviewItemId?: number',
  'exportJobNo?: string',
  'function queryAlertReviewEvidenceAudit',
  "url: '/system/supervision/alert-review/evidence-audit'",
  'params: query',
]

for (const snippet of requiredAuditLookupApiSnippets) {
  if (!apiSource.includes(snippet)) {
    failures.push(`missing evidence audit lookup API contract ${snippet}`)
  }
}

const requiredSemanticTriggerSnippets = [
  'export interface AlertReviewSemanticTriggerResult',
  "humanConfirmationStatus: 'pending' | 'confirmed' | 'rejected' | string",
  'evaluationId: string',
  'inputVersion: string',
  'latestIndexVersion: number',
  'hitExplanations: Record<string, any>[]',
  'actionPreviews: Record<string, any>[]',
  'confirmedBy?: number',
  'confirmedAt?: string',
  "SEMANTIC_TRIGGER_EVALUATE_PERMISSION = 'system:supervision-alert-review:semantic-trigger:evaluate'",
  "SEMANTIC_TRIGGER_CONFIRM_PERMISSION = 'system:supervision-alert-review:semantic-trigger:confirm'",
  'canEvaluateSemanticTrigger',
  'canConfirmSemanticTrigger',
  'semanticTriggerResult',
  'semanticTriggerResult.inputVersion',
  'semanticTriggerResult.latestIndexVersion',
  'explanation.indexVersion',
  'SEMANTIC_TRIGGER_EVALUATION_STORAGE_KEY',
  'restoreSemanticTriggerPreview',
  'evaluateSemanticTriggerPreview',
  "confirmSemanticTriggerPreview('confirmed')",
  "confirmSemanticTriggerPreview('rejected')",
  '命中解释',
  '动作预览',
  '确认只记录审批状态，不会执行动作',
]

for (const snippet of requiredSemanticTriggerSnippets) {
  if (!apiSource.includes(snippet) && !workbenchSource.includes(snippet)) {
    failures.push(`missing semantic trigger confirmation contract ${snippet}`)
  }
}

const requiredRuleGovernancePermissionSnippets = [
  "import { usePermission } from '@/hooks/web/usePermission'",
  'const { hasPermission } = usePermission()',
  "RULE_SUGGESTION_UPDATE_PERMISSION = 'system:supervision-alert-review:rule-suggestion:update'",
  "RULE_SUGGESTION_REVERT_PERMISSION = 'system:supervision-alert-review:rule-suggestion:revert'",
  "RULE_REPLAY_PERMISSION = 'system:supervision-alert-review:rules:replay'",
  'canUpdateRuleSuggestion',
  'canRevertRuleSuggestion',
  'canReplayRule',
  'ruleSuggestionSampleReady',
  'ruleSuggestionHasShadowEvidence',
  'canShadowEvaluateRuleSuggestion',
  'canRejectRuleSuggestion',
  'canAcceptRuleSuggestion',
  'canApplyRuleSuggestion',
  'v-if="canShadowEvaluateRuleSuggestion(item)"',
  "@click=\"updateRuleSuggestion(item, 'shadow_evaluated')\"",
  "@click=\"updateRuleSuggestion(item, 'rejected')\"",
  'v-if="canAcceptRuleSuggestion(item)"',
  'v-if="canApplyRuleSuggestion(item)"',
  "v-if=\"item.ruleSuggestionStatus === 'applied' && canRevertRuleSuggestion\"",
  'v-if="item.ruleSuggestionStatus && canReplayRule"',
  '影子评估',
  '拒绝建议',
  '审批通过',
  '应用规则',
  '回放分析',
]

for (const snippet of requiredRuleGovernancePermissionSnippets) {
  if (!workbenchSource.includes(snippet)) {
    failures.push(`missing rule governance permission contract ${snippet}`)
  }
}

const requiredRuleGovernanceApiSnippets = [
  "status: 'pending' | 'shadow_evaluated' | 'accepted' | 'rejected' | 'applied' | 'reverted'",
]

for (const snippet of requiredRuleGovernanceApiSnippets) {
  if (!apiSource.includes(snippet)) {
    failures.push(`missing rule governance API contract ${snippet}`)
  }
}

const requiredPlaybackGuardSnippets = [
  'prepareAlertReviewPlaybackUrl',
  'prepareWorkbenchPlayback',
  'prepared.recordPath',
  'target.materialUri = prepared.recordPath',
]

for (const snippet of requiredPlaybackGuardSnippets) {
  if (!workbenchSource.includes(snippet)) {
    failures.push(`missing playback preparation guard contract ${snippet}`)
  }
}

const requiredSnapshotGuardSnippets = [
  "actionType?: 'snapshot' | 'playback'",
  "actionType: target.actionType || 'playback'",
  "actionType: 'snapshot'",
]

for (const snippet of requiredSnapshotGuardSnippets) {
  if (!workbenchSource.includes(snippet)) {
    failures.push(`missing snapshot media access guard contract ${snippet}`)
  }
}

const requiredReasonSnippets = [
  'recordGapReasons?: Record<string, number>',
  'recordGapReasonSummary',
  'recordReasonText(item.recordEvidenceMessage)',
  'recordReasonText(selectedItem.recordEvidenceMessage)',
]

for (const snippet of requiredReasonSnippets) {
  const source = snippet.startsWith('recordGapReasons') ? apiSource : workbenchSource
  if (!source.includes(snippet)) {
    failures.push(`missing record gap reason contract ${snippet}`)
  }
}

if (!mockApiSource.includes('evidence_download_audited')) {
  failures.push('missing integration smoke download audit checkpoint')
}

const requiredOperationsReportSnippets = [
  'AlertReviewOperationsReport',
  'AlertReviewReportAcknowledgement',
  'opsReport',
  'opsReportAcknowledgement',
  'runOpsReportAcknowledgement',
  'data-testid="alert-review-ops-report-ack"',
  'data-testid="alert-review-ops-report-ack-action"',
  'acknowledgeAlertReviewOperationsReport',
  'generateAlertReviewOperationsReport',
]

for (const snippet of requiredOperationsReportSnippets) {
  const source = snippet.includes('AlertReview') || snippet.includes('OperationsReport')
    ? `${apiSource}\n${workbenchSource}\n${mockApiSource}`
    : workbenchSource
  if (!source.includes(snippet)) {
    failures.push(`missing operations report acknowledgement contract ${snippet}`)
  }
}

async function runBrowserE2E(failures) {
  const browserPath = findBrowserPath()
  if (!browserPath) {
    failures.push('missing local Chrome or Edge executable for browser E2E')
    return
  }

  const { createServer } = await import('vite')
  const vue = (await import('@vitejs/plugin-vue')).default
  const port = await findFreePort()
  const server = await createServer({
    configFile: false,
    root,
    logLevel: 'silent',
    plugins: [vue()],
    resolve: {
      alias: [
        {
          find: '@/api/supervision/alertReview',
          replacement: toVitePath(resolve(harnessRoot, 'mockAlertReviewApi.ts')),
        },
        {
          find: '@/components/Button',
          replacement: toVitePath(resolve(harnessRoot, 'ButtonStub.ts')),
        },
        {
          find: '@/components/Icon',
          replacement: toVitePath(resolve(harnessRoot, 'IconStub.ts')),
        },
        {
          find: '@/hooks/web/useMessage',
          replacement: toVitePath(resolve(harnessRoot, 'messageStub.ts')),
        },
        {
          find: '@/hooks/web/usePermission',
          replacement: toVitePath(resolve(harnessRoot, 'permissionStub.ts')),
        },
        {
          find: '@/components/Modal/src/BasicModal.vue',
          replacement: toVitePath(resolve(harnessRoot, 'BasicModalProbeStub.ts')),
        },
        {
          find: /^@\/components\/Modal$/,
          replacement: toVitePath(resolve(harnessRoot, 'modalHarness.ts')),
        },
        {
          find: /^@\/(?:api\/device\/(?:camera|gb28181|calculate)|views\/camera\/utils\/(?:streamTicket|devicePlay|deviceLabel|livePlayer)|utils\/copyTextToClipboard)$/,
          replacement: toVitePath(resolve(harnessRoot, 'playerDependencyStubs.ts')),
        },
        {
          find: /^@\/components\/(?:VideoPlayer\/(?:rtcPlayer|EasyPlayer)|Player\/module\/ptz)\.vue$/,
          replacement: toVitePath(resolve(harnessRoot, 'PlayerLeafStub.ts')),
        },
        {
          find: /^@ant-design\/icons-vue$/,
          replacement: toVitePath(resolve(harnessRoot, 'antDesignIconsStub.ts')),
        },
        {
          find: /^ant-design-vue$/,
          replacement: toVitePath(resolve(harnessRoot, 'antDesignVuePlayerStub.ts')),
        },
        {
          find: /^lodash\/(.+)$/,
          replacement: 'lodash-es/$1.js',
        },
        {
          find: /^@ant-design\/icons-vue\/es\/icons\/.+$/,
          replacement: toVitePath(resolve(harnessRoot, 'antDesignIconDefaultStub.ts')),
        },
        {
          find: '@/api/device/device_detection_region',
          replacement: toVitePath(resolve(harnessRoot, 'mockDeviceDetectionRegionApi.ts')),
        },
        {
          find: '@/api/device/model',
          replacement: toVitePath(resolve(harnessRoot, 'mockDeviceModelApi.ts')),
        },
        ...(mode === 'dev-api-real-drawer'
          ? []
          : [{
              find: '@/views/camera/components/DeviceRegionDrawer/index.vue',
              replacement: toVitePath(resolve(harnessRoot, 'DeviceRegionDrawerStub.ts')),
            }]),
        {
          find: /^@\//,
          replacement: `${toVitePath(resolve(root, 'src'))}/`,
        },
      ],
    },
    server: {
      host: '127.0.0.1',
      port,
      strictPort: true,
      hmr: false,
      watch: null,
      fs: {
        allow: [root],
      },
    },
    optimizeDeps: {
      include: [
        'crypto-js',
        'crypto-js/aes',
        'crypto-js/enc-base64',
        'crypto-js/enc-utf8',
        'crypto-js/md5',
        'crypto-js/mode-ctr',
        'crypto-js/pad-pkcs7',
        'crypto-js/sha256',
        'crypto-js/sha512',
      ],
      entries: ['scripts/fixtures/alert-review-workbench-e2e/index.html'],
    },
    define: {
      __APP_INFO__: JSON.stringify({ name: 'alert-review-workbench-e2e' }),
    },
  })

  let userDataDir
  try {
    await server.listen()
    userDataDir = await mkdtemp(resolve(os.tmpdir(), 'alert-review-e2e-'))
    const url = `http://127.0.0.1:${port}/scripts/fixtures/alert-review-workbench-e2e/index.html?mode=${encodeURIComponent(mode)}`
    const result = await runBrowserPage(browserPath, userDataDir, url)
    if (result.status !== 'passed') {
      failures.push(`browser E2E did not pass: ${formatBrowserResult(result)}`)
    }
  }
  catch (error) {
    failures.push(`browser E2E failed: ${error instanceof Error ? error.message : String(error)}`)
  }
  finally {
    await server.close()
    if (userDataDir) {
      await wait(250)
      await rm(userDataDir, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 }).catch(() => undefined)
    }
  }
}

function findBrowserPath() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  ].filter(Boolean)
  return candidates.find(candidate => existsSync(candidate))
}

function findFreePort() {
  return new Promise((resolvePort, reject) => {
    const server = net.createServer()
    server.unref()
    server.on('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (!address || typeof address === 'string') {
        server.close(() => reject(new Error('unable to allocate browser E2E port')))
        return
      }
      const { port } = address
      server.close(() => resolvePort(port))
    })
  })
}

async function runBrowserPage(browserPath, userDataDir, url) {
  const debugPort = await findFreePort()
  const args = [
    '--headless=new',
    '--disable-gpu',
    '--in-process-gpu',
    '--disable-gpu-sandbox',
    '--disable-vulkan',
    '--no-sandbox',
    '--disable-features=Vulkan,SkiaGraphite,DefaultANGLEVulkan,CanvasOopRasterization',
    '--disable-dev-shm-usage',
    '--no-first-run',
    '--no-default-browser-check',
    `--remote-debugging-port=${debugPort}`,
    `--user-data-dir=${userDataDir}`,
    'about:blank',
  ]
  const child = spawn(browserPath, args, { windowsHide: true })
  let stderr = ''
  child.stderr.on('data', chunk => {
    stderr += chunk
  })

  let cdp
  try {
    const target = await createBrowserTarget(debugPort, url)
    cdp = await CdpClient.connect(target.webSocketDebuggerUrl)
    await cdp.send('Runtime.enable')
    await cdp.send('Log.enable')
    return await pollBrowserResult(cdp)
  }
  catch (error) {
    const browserEvents = cdp?.events?.slice(-20) || []
    throw new Error(`${error instanceof Error ? error.message : String(error)}${browserEvents.length ? `\n${JSON.stringify(browserEvents)}` : ''}${stderr ? `\n${stderr}` : ''}`)
  }
  finally {
    await withTimeout(cdp?.send('Browser.close').catch(() => undefined) || Promise.resolve(), 1000)
    cdp?.close()
    if (child.pid)
      await withTimeout(terminateProcessTree(child.pid), 3000)
  }
}

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    wait(ms).then(() => undefined),
  ])
}

function terminateProcessTree(pid) {
  return new Promise(resolveKill => {
    const taskkill = spawn('taskkill', ['/PID', String(pid), '/T', '/F'], { windowsHide: true })
    taskkill.on('close', () => resolveKill())
    taskkill.on('error', () => resolveKill())
  })
}

async function createBrowserTarget(debugPort, url) {
  const endpoint = `http://127.0.0.1:${debugPort}`
  const startedAt = Date.now()
  while (Date.now() - startedAt < 10000) {
    try {
      const response = await fetch(`${endpoint}/json/new?${encodeURIComponent(url)}`, { method: 'PUT' })
      if (response.ok)
        return await response.json()
    }
    catch {
      await wait(100)
    }
  }
  throw new Error('timed out waiting for browser debug endpoint')
}

async function pollBrowserResult(cdp) {
  const startedAt = Date.now()
  let lastResult
  while (Date.now() - startedAt < 90000) {
    const response = await cdp.send('Runtime.evaluate', {
      expression: `(() => {
        const el = document.querySelector('#alert-review-e2e-result');
        if (!el) {
          return {
            status: 'missing',
            text: document.body?.textContent || '',
            html: document.documentElement?.outerHTML?.slice(0, 4000) || ''
          };
        }
        return {
          status: el.dataset.status || 'unknown',
          text: el.textContent || '',
          html: el.outerHTML || ''
        };
      })()`,
      returnByValue: true,
    })
    const result = response.result?.value
    lastResult = result
    if (result?.status === 'passed' || result?.status === 'failed')
      return result
    await wait(100)
  }
  throw new Error(`timed out waiting for browser E2E result: ${JSON.stringify(lastResult)}`)
}

function formatBrowserResult(result) {
  if (!result)
    return 'empty result'
  return JSON.stringify(result, null, 2).slice(0, 4000)
}

class CdpClient {
  constructor(ws) {
    this.ws = ws
    this.id = 0
    this.pending = new Map()
    this.events = []
    this.ws.addEventListener('message', event => {
      const message = JSON.parse(event.data)
      if (!message.id) {
        if (message.method === 'Runtime.exceptionThrown' || message.method === 'Log.entryAdded')
          this.events.push(message)
        return
      }
      const pending = this.pending.get(message.id)
      if (!pending)
        return
      this.pending.delete(message.id)
      if (message.error) {
        pending.reject(new Error(message.error.message || JSON.stringify(message.error)))
        return
      }
      pending.resolve(message.result || {})
    })
  }

  static connect(url) {
    return new Promise((resolveConnect, reject) => {
      const ws = new WebSocket(url)
      ws.addEventListener('open', () => resolveConnect(new CdpClient(ws)))
      ws.addEventListener('error', () => reject(new Error('failed to connect browser websocket')))
    })
  }

  send(method, params = {}) {
    const id = ++this.id
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolveSend, reject) => {
      this.pending.set(id, { resolve: resolveSend, reject })
    })
  }

  close() {
    this.ws.close()
  }
}

function toVitePath(path) {
  return path.replace(/\\/g, '/')
}

if (!failures.length && mode !== 'contract') {
  await runBrowserE2E(failures)
}

if (failures.length) {
  console.error('Alert review workbench E2E contract failed:')
  for (const failure of failures) {
    console.error(`- ${failure}`)
  }
  process.exit(1)
}

console.log(`Alert review workbench E2E ${mode} OK`)
