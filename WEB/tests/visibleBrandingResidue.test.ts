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

  source.split(/\r?\n/).forEach((line, index) => {
    if (oldBrand.test(line)) {
      violations.push(`${relativePath}:${index + 1}: ${line.trim()}`)
    }
  })
}

// SITE / TWIN / TERMINAL / HARNESS / IDEA 已拆为独立仓库（deploy-profiles.json externalModules），其品牌检查随模块迁出
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
  JSON.parse(readFileSync(resolve(repoRoot, 'PANEL/ui/package.json'), 'utf8')).name,
  'yfeieye-panel-ui',
  'The PANEL package metadata must use the yFeiEye brand.',
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

assert.deepEqual(violations, [], `Old visible brand remains:\n${violations.join('\n')}`)
