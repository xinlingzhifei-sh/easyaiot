import assert from 'node:assert/strict'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = resolve(fileURLToPath(new URL('../..', import.meta.url)))
const generatedPath = resolve(rootDir, 'WEB/src/generated/deploymentProfileManifest.ts')
const profileSource = readFileSync(resolve(rootDir, 'WEB/src/utils/deployProfile.ts'), 'utf8')
const permissionSource = readFileSync(resolve(rootDir, 'WEB/src/store/modules/permission.ts'), 'utf8')
const menuSource = readFileSync(resolve(rootDir, 'WEB/src/router/menus/index.ts'), 'utf8')
const defaultLayoutSource = readFileSync(resolve(rootDir, 'WEB/src/layouts/default/index.vue'), 'utf8')
const cameraSource = readFileSync(resolve(rootDir, 'WEB/src/views/camera/index.vue'), 'utf8')
const alertSource = readFileSync(resolve(rootDir, 'WEB/src/views/alert/index.vue'), 'utf8')
const packageSource = readFileSync(resolve(rootDir, 'WEB/package.json'), 'utf8')

assert.ok(existsSync(generatedPath), 'WEB deployment profile manifest must be generated')
const generatedSource = readFileSync(generatedPath, 'utf8')

assert.match(profileSource, /deploymentProfileManifest/)
assert.match(profileSource, /isDeployCapabilityEnabled/)
assert.match(profileSource, /resolveMenuCapability/)
assert.doesNotMatch(profileSource, /MINI_HIDDEN_MENU_NAMES|STANDARD_HIDDEN_MENU_NAMES/)
assert.doesNotMatch(profileSource, /可视化管理|设备管理|产品管理|数据标注/)

for (const capability of [
  'video.gb28181',
  'video.rtc',
  'video.dji',
  'alert.map',
  'ai.training',
  'workflow.flow',
  'node.cluster',
  'visualize',
  'transform',
  'assistant.harness',
])
  assert.ok(generatedSource.includes(`'${capability}'`), `${capability} must be generated`)

assert.match(permissionSource, /isMenuHiddenByDeployProfile\(route\)/)
assert.match(menuSource, /isMenuHiddenByDeployProfile\(item\)/)
assert.doesNotMatch(permissionSource, /isMenuHiddenByDeployProfile\(route\.name\)/)
assert.doesNotMatch(menuSource, /isMenuHiddenByDeployProfile\(item\.name\)/)

assert.match(defaultLayoutSource, /isHarnessEnabled/)
assert.match(defaultLayoutSource, /showIdeaBall\s*=\s*computed\([\s\S]*isHarnessEnabled\(\)/)
assert.doesNotMatch(defaultLayoutSource, /isEdgeStandaloneDeployProfile/)

assert.match(cameraSource, /isDeployCapabilityEnabled/)
assert.match(cameraSource, /showDjiDeviceCreate\s*=\s*isDeployCapabilityEnabled\('video\.dji'\)/)

assert.match(alertSource, /alertMapEnabled\s*=\s*isDeployCapabilityEnabled\('alert\.map'\)/)
assert.match(alertSource, /<TabPane\s+v-if="alertMapEnabled"/)
assert.match(
  alertSource,
  /defaultAlertTab\s*=\s*alertMapEnabled\s*\?\s*ALERT_TAB_KEYS\.MAP\s*:\s*ALERT_TAB_KEYS\.EVENTS/,
)
assert.match(alertSource, /tabStr\s*!==\s*ALERT_TAB_KEYS\.MAP\s*\|\|\s*alertMapEnabled/)
assert.equal((alertSource.match(/defaultAlertTab/g) ?? []).length, 4)

const packageJson = JSON.parse(packageSource)
assert.equal(packageJson.scripts['profile:check'], 'node ../.scripts/docker/generate-deploy-profiles.mjs --check')
assert.equal(packageJson.scripts.prebuild, packageJson.scripts['profile:check'])

console.log('Deployment profile WEB contract passed')
