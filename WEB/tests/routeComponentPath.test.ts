import * as assert from 'node:assert/strict'
import { normalizeViewComponentPath, resolveViewComponentPath } from '../src/router/helper/routeComponentPath'

assert.equal(
  normalizeViewComponentPath('views/system/oauth2/client/index.vue'),
  'system/oauth2/client/index',
  'Backend menu components may include a views/ prefix and .vue suffix; dynamic route loading should normalize them.',
)

assert.equal(
  normalizeViewComponentPath('/system/user/index.tsx'),
  'system/user/index',
  'Leading slashes and .tsx suffixes should not prevent matching files under src/views.',
)

assert.equal(
  normalizeViewComponentPath('src/views/system/role/index'),
  'system/role/index',
  'Full src/views prefixes should be treated as view-relative paths.',
)

const legacyBackendComponents = new Map([
  ['views/system/social/client/index.vue', 'system/oauth2/client/index'],
  ['system/social/user/index.vue', 'system/user/index'],
  ['infra/testDemo/index', 'infra/codegen/index'],
  ['infra/demo/demo01/index', 'infra/codegen/index'],
  ['video/components/Channel/index', 'gb28181/components/Channel/index'],
  ['device/device_group/index', 'devices/index'],
  ['device/device_log/index', 'devices/index'],
  ['device/device_topic/index', 'devices/index'],
  ['device/product/index', 'product/index'],
  ['device/product_template/index', 'product/index'],
  ['device/product_type/index', 'product/index'],
  ['device/protocol/index', 'product/index'],
])

for (const [component, expected] of legacyBackendComponents) {
  assert.equal(
    resolveViewComponentPath(component),
    expected,
    `Legacy backend component ${component} should resolve to an existing view component.`,
  )
}
