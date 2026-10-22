import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const listed = spawnSync('git', ['ls-files', '-z'], {
  cwd: repoRoot,
  encoding: 'utf8',
  maxBuffer: 16 * 1024 * 1024,
})

assert.equal(listed.status, 0, listed.stderr || 'git ls-files failed')

const protectedLiterals = [
  'EasyAIoT2025',
  'EasyAIoT.Edge',
  'urn:EasyAIoT:Edge:OpcUa',
  'EasyAIoT-*',
]
const protectedPathPrefixes = [
  'EDGE/EasyAIoT.Edge.sln',
  'EDGE/src/EasyAIoT.Edge.',
]
const oldBrand = /EasyAIoT|Easy AI Internet of Things/
const violations: string[] = []
const trackedPaths = listed.stdout.split('\0').filter(Boolean)
const matched = spawnSync(
  'git',
  ['grep', '-I', '-l', '-z', '-E', 'EasyAIoT|Easy AI Internet of Things', '--', '.'],
  {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  },
)

assert.ok(matched.status === 0 || matched.status === 1, matched.stderr || 'git grep failed')

for (const relativePath of trackedPaths) {
  if (
    relativePath.startsWith('docs/superpowers/') ||
    relativePath === 'WEB/tests/visibleBrandingResidue.test.ts'
  ) continue

  if (
    oldBrand.test(relativePath) &&
    !protectedPathPrefixes.some((prefix) => relativePath.startsWith(prefix))
  ) {
    violations.push(`${relativePath}: path contains the old visible brand`)
  }
}

for (const relativePath of matched.stdout.split('\0').filter(Boolean)) {
  if (
    relativePath.startsWith('docs/superpowers/') ||
    relativePath === 'WEB/tests/visibleBrandingResidue.test.ts'
  ) continue

  const bytes = readFileSync(resolve(repoRoot, relativePath))
  let source = bytes.toString('utf8')
  for (const literal of protectedLiterals) {
    source = source.replaceAll(literal, '')
  }
  if (relativePath === 'TERMINAL/backend/store/middleware_presets.go') {
    source = source.replace('const legacyMiddlewareBrand = "EasyAIoT"', '')
  }

  source.split(/\r?\n/).forEach((line, index) => {
    if (oldBrand.test(line)) {
      violations.push(`${relativePath}:${index + 1}: ${line.trim()}`)
    }
  })
}

const appMenu = readFileSync(resolve(repoRoot, 'APP/src/pages/index/index.ts'), 'utf8')
assert.match(appMenu, /key:\s*'easyaiot'/, 'The compatibility menu key must remain easyaiot.')
assert.match(appMenu, /name:\s*'yFeiEye'/, 'The visible APP menu name must be yFeiEye.')

assert.match(
  readFileSync(resolve(repoRoot, 'WEB/src/views/node/utils/constants.ts'), 'utf8'),
  /export ZLM_SECRET/,
  'The deployed ZLM compatibility environment key must not be renamed.',
)
assert.match(
  readFileSync(resolve(repoRoot, 'AI/app/utils/sam_model_download.py'), 'utf8'),
  /yFeiEye-AI\/1\.0/,
  'The existing AI download User-Agent must remain stable.',
)
assert.match(
  readFileSync(resolve(repoRoot, 'VIDEO/app/utils/face_model_download.py'), 'utf8'),
  /yFeiEye-VIDEO\/1\.0/,
  'The existing VIDEO download User-Agent must remain stable.',
)
assert.match(
  readFileSync(resolve(repoRoot, 'VIDEO/app/utils/plate_model_download.py'), 'utf8'),
  /yFeiEye-VIDEO\/1\.0/,
  'The existing VIDEO plate download User-Agent must remain stable.',
)

assert.equal(
  JSON.parse(readFileSync(resolve(repoRoot, 'SITE/package.json'), 'utf8')).name,
  'yfeieye-site',
  'The SITE package metadata must use the yFeiEye brand.',
)
assert.equal(
  JSON.parse(readFileSync(resolve(repoRoot, 'PANEL/ui/package.json'), 'utf8')).name,
  'yfeieye-panel-ui',
  'The PANEL package metadata must use the yFeiEye brand.',
)
assert.equal(
  JSON.parse(readFileSync(resolve(repoRoot, 'TWIN/package.json'), 'utf8')).name,
  'yfeieye-digital-twin',
  'The TWIN package metadata must use the yFeiEye brand.',
)
assert.match(
  readFileSync(resolve(repoRoot, 'TERMINAL/frontend/index.html'), 'utf8'),
  /<title>yFeiEye 终端<\/title>/,
  'The terminal browser title must use the yFeiEye brand.',
)

assert.match(
  readFileSync(resolve(repoRoot, 'HARNESS/plugins/easyaiot-llm-gateway.ts'), 'utf8'),
  /owned_by:\s*'yFeiEye'/,
  'The model listing must display the yFeiEye owner without renaming gateway identifiers.',
)

for (const name of ['README.md', 'README_zh.md', 'README_zh_tw.md', 'README_fr.md', 'README_ko.md', 'README_ru.md']) {
  assert.doesNotMatch(
    readFileSync(resolve(repoRoot, name), 'utf8'),
    /banner124[012]\.png|terminal_100[0-8]\.png/,
    `${name} must not display assistant screenshots bearing the old brand.`,
  )
}

// Node-RED 演示规则链的节点名、主题与 URL 在编辑器中可见；流 id 被 WEB 与 settings.js 引用，必须保留
const noderedDemoFlows = JSON.parse(
  readFileSync(resolve(repoRoot, '.scripts/node-red/easyaiot_flows_demo.json'), 'utf8'),
) as Array<Record<string, unknown>>
for (const node of noderedDemoFlows) {
  for (const [key, value] of Object.entries(node)) {
    if (key === 'id' || key === 'z') continue
    if (typeof value === 'string' && /easyaiot/i.test(value)) {
      violations.push(`.scripts/node-red/easyaiot_flows_demo.json: node ${String(node.id)} field ${key} shows the old brand`)
    }
  }
}
assert.ok(
  noderedDemoFlows.some((node) => node.id === 'easyaiot_demo_telemetry'),
  'The Node-RED demo flow ids must remain easyaiot_demo_* for WEB and settings.js compatibility.',
)

assert.match(
  readFileSync(resolve(repoRoot, 'IDEA/webui/index.html'), 'utf8'),
  /placeholder="https:\/\/gitee\.com\/你的账号\/yFeiEye\.git"/,
  'The IDEA portal repository placeholder must use the yFeiEye brand.',
)

assert.deepEqual(violations, [], `Old visible brand remains:\n${violations.join('\n')}`)
